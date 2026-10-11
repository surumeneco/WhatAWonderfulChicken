package co.surumene.whatawonderfulchicken.service;

import co.surumene.whatawonderfulchicken.WhatAWonderfulChickenPlugin;
import co.surumene.whatawonderfulchicken.display.DisplayService;
import co.surumene.whatawonderfulchicken.founder.FounderGenomeSynthesis;
import co.surumene.whatawonderfulchicken.founder.FounderOrigin;
import co.surumene.whatawonderfulchicken.founder.WonderfulChickenFounderSynthesizer;
import co.surumene.whatawonderfulchicken.genome.WonderfulChickenGenomeProfile;
import co.surumene.wgl.api.GenomeEngine;
import org.bukkit.Bukkit;
import org.bukkit.entity.Chicken;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Generation is detached from Bukkit. Selected animals remain vanilla until
 * their complete genome is available; the probability draw is not repeated.
 */
public final class NaturalChickenGenerationCoordinator {
    private final WhatAWonderfulChickenPlugin plugin;
    private final WonderfulChickenService chickens;
    private final WonderfulChickenStore store;
    private final DisplayService displays;
    private final ThreadPoolExecutor worker;
    private final Map<UUID, Job> pending = new HashMap<>();
    private final ArrayDeque<Job> waiting = new ArrayDeque<>();
    private volatile boolean closed;

    public NaturalChickenGenerationCoordinator(
            WhatAWonderfulChickenPlugin plugin, WonderfulChickenService chickens,
            WonderfulChickenStore store, DisplayService displays) {
        this.plugin = plugin;
        this.chickens = chickens;
        this.store = store;
        this.displays = displays;
        worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(16),
                task -> {
                    Thread thread = new Thread(task, "wwc-natural-founder");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    public boolean request(Chicken chicken, long seed) {
        if (closed || store.isWonderful(chicken)) return false;
        UUID uuid = chicken.getUniqueId();
        if (pending.containsKey(uuid)) return false;
        if (pending.size() >= 128) {
            plugin.getLogger().warning("Natural genome generation backlog full");
            return false;
        }
        Job job = new Job(chicken, uuid, seed,
                plugin.genomeLib().engine(), plugin.genomeProfile());
        pending.put(uuid, job);
        waiting.addLast(job);
        return true;
    }

    /**
     * Administrative founder requests do not create a placeholder entity.
     * Completion (or failure) is reported on the Bukkit thread.
     */
    public boolean submitFounder(
            FounderOrigin origin, long seed,
            java.util.function.Consumer<co.surumene.whatawonderfulchicken.data.WonderfulChickenData> onComplete,
            java.util.function.Consumer<Exception> onFailure) {
        if (closed) return false;
        GenomeEngine engine = plugin.genomeLib().engine();
        WonderfulChickenGenomeProfile profile = plugin.genomeProfile();
        try {
            worker.execute(() -> {
                FounderGenomeSynthesis result = null;
                Exception failure = null;
                try {
                    result = new WonderfulChickenFounderSynthesizer(engine, profile)
                            .synthesize(origin, seed);
                } catch (RuntimeException error) {
                    failure = error;
                }
                FounderGenomeSynthesis completed = result;
                Exception error = failure;
                if (closed) return;
                try {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (closed || !plugin.isEnabled()) return;
                        if (error != null) {
                            onFailure.accept(error);
                        } else if (engine != plugin.genomeLib().engine()
                                || profile != plugin.genomeProfile()) {
                            onFailure.accept(new IllegalStateException("WGL configuration changed during calculation"));
                        } else {
                            try {
                                onComplete.accept(chickens.dataFromFounderSynthesis(completed, origin));
                            } catch (RuntimeException conversionError) {
                                onFailure.accept(conversionError);
                            }
                        }
                    });
                } catch (IllegalStateException ignored) {}
            });
            return true;
        } catch (RejectedExecutionException full) {
            return false;
        }
    }

    /** Worker-only calculation with Bukkit-thread completion. */
    public <T> boolean submitCalculation(
            java.util.concurrent.Callable<T> calculate,
            java.util.function.Consumer<T> onComplete,
            java.util.function.Consumer<Exception> onFailure) {
        if (closed) return false;
        try {
            worker.execute(() -> {
                T outcome = null;
                Exception error = null;
                try {
                    outcome = calculate.call();
                } catch (Exception failure) {
                    error = failure;
                }
                T finished = outcome;
                Exception failed = error;
                if (closed || !plugin.isEnabled()) return;
                try {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (closed || !plugin.isEnabled()) return;
                        if (failed != null) onFailure.accept(failed);
                        else onComplete.accept(finished);
                    });
                } catch (IllegalStateException ignored) {
                    // Plugin shutdown invalidated the callback.
                }
            });
            return true;
        } catch (RejectedExecutionException full) {
            return false;
        }
    }

    /** Called once per server tick; never waits for genome synthesis. */
    public void tick() {
        if (closed || waiting.isEmpty() || worker.getQueue().remainingCapacity() == 0) return;
        Job job = waiting.removeFirst();
        if (pending.get(job.id()) != job) return;
        try {
            worker.execute(() -> calculate(job));
        } catch (RejectedExecutionException rejected) {
            if (!closed) waiting.addFirst(job);
        }
    }

    public void invalidate(Chicken chicken) {
        Job job = pending.get(chicken.getUniqueId());
        if (job != null && job.chicken() == chicken) {
            pending.remove(job.id());
            waiting.remove(job);
        }
    }

    public void shutdown() {
        closed = true;
        pending.clear();
        waiting.clear();
        worker.shutdownNow();
    }

    private void calculate(Job job) {
        FounderGenomeSynthesis synthesis = null;
        RuntimeException failure = null;
        try {
            synthesis = new WonderfulChickenFounderSynthesizer(job.engine(), job.profile())
                    .synthesize(FounderOrigin.NATURAL, job.seed());
        } catch (RuntimeException ex) {
            failure = ex;
        }
        FounderGenomeSynthesis completed = synthesis;
        RuntimeException error = failure;
        if (closed || !plugin.isEnabled()) return;
        try {
            Bukkit.getScheduler().runTask(plugin, () -> complete(job, completed, error));
        } catch (IllegalStateException ignored) {
            // Plugin disabled before completion could be scheduled.
        }
    }

    private void complete(Job job, FounderGenomeSynthesis synthesis, RuntimeException error) {
        if (pending.remove(job.id()) != job) return;
        Chicken chicken = job.chicken();
        if (closed || !plugin.isEnabled()
                || !chicken.isValid() || chicken.isDead()
                || !chicken.getUniqueId().equals(job.id())
                || store.isWonderful(chicken)
                || job.engine() != plugin.genomeLib().engine()
                || job.profile() != plugin.genomeProfile()) return;
        if (error != null) {
            plugin.getLogger().warning("Natural WWC generation failed for " + job.id()
                    + ": " + error.getMessage());
            return;
        }
        try {
            chickens.initialize(chicken,
                    chickens.dataFromFounderSynthesis(synthesis, FounderOrigin.NATURAL));
            displays.rebuild(chicken);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Could not apply natural WWC genome for "
                    + job.id() + ": " + ex.getMessage());
        }
    }

    private record Job(Chicken chicken, UUID id, long seed,
                       GenomeEngine engine, WonderfulChickenGenomeProfile profile) {}
}
