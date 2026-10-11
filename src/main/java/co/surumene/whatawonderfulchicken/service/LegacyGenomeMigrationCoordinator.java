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
import java.util.Map;
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
    private boolean closed;

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
            return false;
        }
        LegacyInput input = LegacyInput.capture(current);
        // Obtain both references on the server thread. A WGL config reload may
        // replace the engine instance while an existing calculation is running.
        GenomeEngine engine = plugin.genomeLib().engine();
        WonderfulChickenGenomeProfile profile = plugin.genomeProfile();
        Request job = new Request(chicken, input);
        pending.put(id, job);
        try {
            worker.execute(() -> calculate(job, engine, profile));
            return true;
        } catch (RejectedExecutionException rejected) {
            pending.remove(id, job);
            // Queue capacity is intentionally bounded; retry during the next sweep.
            return false;
        }
    }

    void retryLoaded() {
        if (closed) return;
        int submitted = 0;
        for (Chicken chicken : chickens.loadedChickens()) {
            if (submitted >= 4) break;
            if (request(chicken)) submitted++;
        }
    }

    void invalidate(Chicken chicken) {
        UUID id = chicken.getUniqueId();
        Request job = pending.get(id);
        if (job != null && job.chicken() == chicken) pending.remove(id);
        retryAfter.remove(id);
    }

    void shutdown() {
        closed = true;
        pending.clear();
        retryAfter.clear();
        worker.shutdownNow();
    }

    private void calculate(Request job, GenomeEngine engine, WonderfulChickenGenomeProfile profile) {
        MigrationResult result = null;
        RuntimeException failure = null;
        try {
            long seed = migrationSeed(job.chicken().getUniqueId());
            result = new LegacyChickenGenomeMigrator(engine, profile)
                    .migrate(job.input().toLegacyData(), 0L, seed);
        } catch (RuntimeException error) {
            failure = error;
        }
        MigrationResult completed = result;
        RuntimeException error = failure;
        if (closed || !plugin.isEnabled()) return;
        try {
            Bukkit.getScheduler().runTask(plugin, () -> complete(job, completed, error));
        } catch (IllegalStateException ignored) {
            // The plugin was disabled while the worker was finishing.
        }
    }

    private void complete(Request job, MigrationResult result, RuntimeException error) {
        UUID id = job.chicken().getUniqueId();
        if (pending.get(id) != job) return;
        pending.remove(id);
        if (closed || !plugin.isEnabled() || !job.chicken().isValid()
                || !chickens.isRegistered(job.chicken()) || !store.isWonderful(job.chicken())) return;

        if (error != null) {
            retryAfter.put(id, System.currentTimeMillis() + FAILURE_RETRY_DELAY_MILLIS);
            plugin.getLogger().warning("Legacy genome migration failed for " + id
                    + " (retry after 60 seconds): " + error.getClass().getSimpleName()
                    + ": " + error.getMessage());
            return;
        }

        WonderfulChickenData latest = store.load(job.chicken());
        if (latest.hasGenomeModel()) return;
        if (!job.input().equals(LegacyInput.capture(latest))) {
            // Never overwrite changed game data with an outdated calculation.
            return;
        }
        latest.genome(Objects.requireNonNull(result).genome());
        latest.phenotypeSnapshot(result.phenotypeSnapshot());
        latest.adultBiologicalTime(result.adultBiologicalTime());
        store.save(job.chicken(), latest);
        retryAfter.remove(id);
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

    private record Request(Chicken chicken, LegacyInput input) {}
}
