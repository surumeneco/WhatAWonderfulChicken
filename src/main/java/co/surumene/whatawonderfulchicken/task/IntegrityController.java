package co.surumene.whatawonderfulchicken.task;

import co.surumene.whatawonderfulchicken.display.DisplayService;
import co.surumene.whatawonderfulchicken.runtime.AgeCurve;
import co.surumene.whatawonderfulchicken.listener.WorldListener;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenService;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * Keep moving displays up to date every tick, but never process all loaded
 * chickens or every world entity in the same server tick.
 */
public final class IntegrityController implements Runnable {
    private static final int MAX_CHICKENS_PER_TICK = 8;
    private static final int MAX_CHUNKS_PER_TICK = 2;
    private static final int MAX_PDC_CANDIDATES_PER_TICK = 8;
    // Eventless third-party entity insertion fallback; normal late PDC checks
    // run every 200 ticks against tracked chicken UUIDs instead of all chunks.
    private static final long GLOBAL_COMPATIBILITY_AUDIT_TICKS = 6000L;
    private static final long AGE_REFRESH_QUANTUM = 200L;

    private final WonderfulChickenService chickens;
    private final DisplayService displays;
    private final WorldListener worlds;
    private final LongSupplier biologicalTime;
    private final TrackedEntityIndex<Chicken> tracked = new TrackedEntityIndex<>();
    private final ArrayDeque<Chicken> pendingChickens = new ArrayDeque<>();
    private final ArrayDeque<Chunk> pendingChunks = new ArrayDeque<>();
    private final ArrayDeque<UUID> pendingRestorationIds = new ArrayDeque<>();
    private long nextCandidateSweep;
    private final Map<UUID, Long> nextAgeProjection = new HashMap<>();
    private final Map<UUID, Chicken> projectionEntities = new HashMap<>();
    private long lastBiologicalTime = Long.MIN_VALUE;
    private long tick;
    private long nextChickenSweep;
    private long nextWorldSweep = GLOBAL_COMPATIBILITY_AUDIT_TICKS;

    public IntegrityController(
            WonderfulChickenService chickens,
            DisplayService displays,
            WorldListener worlds,
            LongSupplier biologicalTime) {
        this.chickens = chickens;
        this.displays = displays;
        this.worlds = worlds;
        this.biologicalTime = biologicalTime;
        for (Chicken chicken : chickens.loadedChickens()) track(chicken);
    }

    /** Incrementally track loaded WWC instances, retaining UUID replacement identity. */
    public void track(Chicken chicken) {
        tracked.track(chicken.getUniqueId(), chicken);
    }

    public void untrack(Chicken chicken) {
        if (tracked.untrack(chicken.getUniqueId(), chicken)) {
            nextAgeProjection.remove(chicken.getUniqueId());
            projectionEntities.remove(chicken.getUniqueId());
        }
    }

    /** Config/clock changes require a fresh curve even during a plateau. */
    public void markAllAgeDirty() {
        nextAgeProjection.clear();
    }

    @Override
    public void run() {
        tick++;
        displays.tick();

        // Legacy Genome retries wake only on failure/deadline/worker completion.
        if (tick % 20 == 0) worlds.retryPendingRestoration();

        if (pendingChickens.isEmpty() && tick >= nextChickenSweep) {
            nextChickenSweep = tick + 200L;
            // No periodic PDC-backed full snapshot of the registered set.
            pendingChickens.addAll(tracked.snapshot());
            nextAgeProjection.keySet().retainAll(tracked.ids());
            projectionEntities.keySet().retainAll(tracked.ids());
        }

        long now = biologicalTime.getAsLong();
        if (lastBiologicalTime != Long.MIN_VALUE && now < lastBiologicalTime) {
            nextAgeProjection.clear();
        }
        lastBiologicalTime = now;
        for (int i = 0; i < MAX_CHICKENS_PER_TICK && !pendingChickens.isEmpty(); i++) {
            Chicken chicken = pendingChickens.removeFirst();
            if (!chicken.isValid() || chickens.isSuperseded(chicken)
                    || !chickens.isRegistered(chicken)) {
                untrack(chicken);
                continue;
            }

            UUID id = chicken.getUniqueId();
            boolean newEntity = projectionEntities.put(id, chicken) != chicken;
            if (newEntity || now >= nextAgeProjection.getOrDefault(id, Long.MIN_VALUE)) {
                chickens.projectAttributes(chicken);
                displays.rebuild(chicken);
                var data = chickens.store().read(chicken);
                long due = now + AGE_REFRESH_QUANTUM;
                if (chicken.isAdult() && data.hasGenomeModel()
                        && data.adultBiologicalTime() > 0L
                        && data.phenotypeSnapshot() != null) {
                    AgeCurve curve = chickens.ageProjectionCurve(data);
                    long predicted = ChickenAgeProjectionDeadline.next(
                            now, data.adultBiologicalTime(), curve,
                            data.phenotypeSnapshot().injuries().stream()
                                    .map(injury -> injury.onsetGameDay()).toList());
                    // Preserve a rare external-attribute compatibility check.
                    due = Math.min(predicted, now + 6000L);
                }
                nextAgeProjection.put(id, due);
            }
        }

        if (pendingRestorationIds.isEmpty() && tick >= nextCandidateSweep) {
            nextCandidateSweep = tick + 200L;
            pendingRestorationIds.addAll(worlds.latePdcCandidateIds());
        }
        for (int i = 0; i < MAX_PDC_CANDIDATES_PER_TICK
                && !pendingRestorationIds.isEmpty(); i++) {
            worlds.auditLatePdcCandidate(pendingRestorationIds.removeFirst());
        }

        if (tick % 6000L == 0L) worlds.logHeadEquipmentAudit();

        // Every 6000 ticks only the currently online players' center chunk
        // and eight neighbors are eligible. No new chunk loads or duplicates.
        if (pendingChunks.isEmpty() && tick >= nextWorldSweep) {
            nextWorldSweep = tick + GLOBAL_COMPATIBILITY_AUDIT_TICKS;
            List<PlayerNearbyChunkAuditPolicy.PlayerChunk> positions = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                Location loc = player.getLocation();
                positions.add(new PlayerNearbyChunkAuditPolicy.PlayerChunk(
                        player.getWorld().getUID(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4));
            }
            for (PlayerNearbyChunkAuditPolicy.ChunkKey key :
                    PlayerNearbyChunkAuditPolicy.nearby(positions)) {
                World world = Bukkit.getWorld(key.worldId());
                if (world == null || !world.isChunkLoaded(key.chunkX(), key.chunkZ())) continue;
                pendingChunks.addLast(world.getChunkAt(key.chunkX(), key.chunkZ()));
            }
        }
        for (int i = 0; i < MAX_CHUNKS_PER_TICK && !pendingChunks.isEmpty(); i++) {
            Chunk chunk = pendingChunks.removeFirst();
            if (!chunk.isLoaded()) continue;
            for (Entity entity : chunk.getEntities()) {
                if (entity instanceof Chicken chicken) {
                    worlds.trackCompatibilityCandidate(chicken);
                    if (chickens.registerLoaded(chicken)) displays.rebuild(chicken);
                }
            }
        }
    }
}
