package co.surumene.whatawonderfulchicken.service;

import co.surumene.whatawonderfulchicken.WhatAWonderfulChickenPlugin;
import co.surumene.whatawonderfulchicken.data.Genetics;
import co.surumene.whatawonderfulchicken.data.StatType;
import co.surumene.whatawonderfulchicken.data.WonderfulChickenData;
import co.surumene.whatawonderfulchicken.genome.WonderfulChickenGenomeProfile;
import co.surumene.whatawonderfulchicken.migration.LegacyChickenGenomeMigrator;
import co.surumene.whatawonderfulchicken.migration.MigrationResult;
import co.surumene.wgl.api.GenomeEngine;
import org.bukkit.Bukkit;
import org.bukkit.entity.Chicken;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Keeps Paper entity/PDC operations on the server thread and runs only detached
 * WGL calculations in a single bounded background worker.
 */
final class LegacyGenomeMigrationCoordinator {
    private static final long FAILURE_RETRY_DELAY_MILLIS = 60_000L;
    private final WhatAWonderfulChickenPlugin plugin;
    private final WonderfulChickenService chickens;
    private final WonderfulChickenStore store;
    private final ThreadPoolExecutor worker;
    private final Map<UUID, Request> pending = new HashMap<>();
    private final Map<UUID, Long> retryAfter = new HashMap<>();
    /** Only failed or queue-rejected legacy entities need periodic resubmission. */
    private final Set<UUID> retryCandidates = new LinkedHashSet<>();
    private volatile boolean closed;

    LegacyGenomeMigrationCoordinator(
            WhatAWonderfulChickenPlugin plugin,
            WonderfulChickenService chickens,
            WonderfulChickenStore store) {
        this.plugin = plugin;
        this.chickens = chickens;
        this.store = store;
        this.worker = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(16),
                task -> {
                    Thread thread = new Thread(task, "wwc-legacy-genome-migration");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    boolean request(Chicken chicken) {
        if (closed || !chicken.isValid() || !store.isWonderful(chicken)) return false;
        UUID id = chicken.getUniqueId();
        if (pending.containsKey(id)
                || System.currentTimeMillis() < retryAfter.getOrDefault(id, 0L)) return false;

        WonderfulChickenData current = store.load(chicken);
        if (current.hasGenomeModel()) {
            retryAfter.remove(id);
            retryCandidates.remove(id);
            return false;
        }
        LegacyInput input = LegacyInput.capture(current);
        // Obtain both references on the server thread. A WGL config reload may
        // replace the engine instance while an existing calculation is running.
        GenomeEngine engine = plugin.genomeLib().engine();
        WonderfulChickenGenomeProfile profile = plugin.genomeProfile();
        Request job = new Request(chicken, id, input, engine, profile);
        pending.put(id, job);
        try {
            worker.execute(() -> calculate(job));
            retryCandidates.remove(id);
            return true;
        } catch (RejectedExecutionException rejected) {
            pending.remove(id, job);
            // Queue capacity is intentionally bounded; retry this individual only.
            retryCandidates.add(id);
            return false;
        }
    }

    void retryLoaded() {
        if (closed || retryCandidates.isEmpty()) return;
        int submitted = 0;
        long now = System.currentTimeMillis();
        // Bounded retry queue: do not load the Genome/PDC of every migrated chicken.
        for (UUID id : List.copyOf(retryCandidates)) {
            if (submitted >= 4) break;
            if (pending.containsKey(id)) {
                retryCandidates.remove(id);
                continue;
            }
            if (now < retryAfter.getOrDefault(id, 0L)) continue;
            org.bukkit.entity.Entity entity = Bukkit.getEntity(id);
            if (!(entity instanceof Chicken chicken) || !chicken.isValid()
                    || !chickens.isRegistered(chicken) || !store.isWonderful(chicken)) {
                retryCandidates.remove(id);
                retryAfter.remove(id);
                continue;
            }
            // Remove before calling request(): request() also maintains this queue.
            // Retain the candidate if the bounded executor rejects the submission.
            retryCandidates.remove(id);
            if (request(chicken)) submitted++;
        }
    }

    void invalidate(Chicken chicken) {
        UUID id = chicken.getUniqueId();
        Request job = pending.get(id);
        if (job != null && job.chicken() == chicken) pending.remove(id);
        retryAfter.remove(id);
        retryCandidates.remove(id);
    }

    void shutdown() {
        closed = true;
        pending.clear();
        retryAfter.clear();
        retryCandidates.clear();
        worker.shutdownNow();
    }

    private void calculate(Request job) {
        MigrationResult result = null;
        RuntimeException failure = null;
        try {
            long seed = migrationSeed(job.id());
            result = new LegacyChickenGenomeMigrator(job.engine(), job.profile())
                    .migrate(job.input().toLegacyData(), 0L, seed);
        } catch (RuntimeException error) {
            failure = error;
        }
        MigrationResult completed = result;
        RuntimeException error = failure;
        if (closed || !plugin.isEnabled()) return;
        try {
            Bukkit.getScheduler().runTask(plugin, () -> complete(job, completed, error));
        } catch (RuntimeException dispatchFailure) {
            if (!closed && plugin.isEnabled()) {
                plugin.getLogger().warning("Could not dispatch legacy genome result: "
                        + dispatchFailure.getMessage());
            }
        }
    }

    private void complete(Request job, MigrationResult result, RuntimeException error) {
        UUID id = job.id();
        if (pending.get(id) != job) return;
        pending.remove(id);
        if (closed || !plugin.isEnabled() || !job.chicken().isValid()
                || !chickens.isRegistered(job.chicken()) || !store.isWonderful(job.chicken())) return;
        if (job.engine() != plugin.genomeLib().engine()
                || job.profile() != plugin.genomeProfile()) {
            // Reload swapped the genome implementation while the old result
            // was in flight. Resubmit from the live entity, never lose it.
            retryCandidates.add(id);
            return;
        }

        if (error != null) {
            retryAfter.put(id, System.currentTimeMillis() + FAILURE_RETRY_DELAY_MILLIS);
            retryCandidates.add(id);
            plugin.getLogger().warning("Legacy genome migration failed for " + id
                    + " (retry after 60 seconds): " + error.getClass().getSimpleName()
                    + ": " + error.getMessage());
            return;
        }

        WonderfulChickenData latest = store.load(job.chicken());
        if (latest.hasGenomeModel()) return;
        if (!job.input().equals(LegacyInput.capture(latest))) {
            // Never overwrite changed game data with an outdated calculation.
            // The latest state still needs a genome, so retry the live entity.
            retryCandidates.add(id);
            return;
        }
        latest.genome(Objects.requireNonNull(result).genome());
        latest.phenotypeSnapshot(result.phenotypeSnapshot());
        latest.adultBiologicalTime(result.adultBiologicalTime());
        store.save(job.chicken(), latest);
        retryAfter.remove(id);
        retryCandidates.remove(id);
        chickens.synchronizeRangePolicy(job.chicken());
        chickens.projectAttributes(job.chicken());
    }

    private static long migrationSeed(UUID entityId) {
        return entityId.getMostSignificantBits()
                ^ Long.rotateLeft(entityId.getLeastSignificantBits(), 17)
                ^ 0x5757435F56325F31L;
    }

    /** Detached, equality-comparable input; never shares Bukkit ItemStacks or entities. */
    record LegacyInput(Map<StatType, Double> normalized, Genetics genetics, int generation) {
        LegacyInput {
            normalized = Map.copyOf(normalized);
        }

        static LegacyInput capture(WonderfulChickenData data) {
            EnumMap<StatType, Double> abilities = new EnumMap<>(StatType.class);
            for (StatType stat : StatType.values()) abilities.put(stat, data.normalized(stat));
            return new LegacyInput(abilities, data.genetics(), data.generation());
        }

        WonderfulChickenData toLegacyData() {
            WonderfulChickenData detached = new WonderfulChickenData();
            for (StatType stat : StatType.values()) detached.normalized(stat, normalized.get(stat));
            detached.genetics(genetics);
            detached.generation(generation);
            return detached;
        }
    }

    private record Request(
            Chicken chicken, UUID id, LegacyInput input,
            GenomeEngine engine, WonderfulChickenGenomeProfile profile) {}
}
