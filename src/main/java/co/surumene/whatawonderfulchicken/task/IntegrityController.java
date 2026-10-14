package co.surumene.whatawonderfulchicken.task;

import co.surumene.whatawonderfulchicken.display.DisplayService;
import co.surumene.whatawonderfulchicken.listener.WorldListener;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenService;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;

/**
 * Keep moving displays up to date every tick, but never process all loaded
 * chickens or every world entity in the same server tick.
 */
public final class IntegrityController implements Runnable {
    private static final int MAX_CHICKENS_PER_TICK = 8;
    private static final int MAX_CHUNKS_PER_TICK = 2;
    private static final long AGE_REFRESH_QUANTUM = 200L;

    private final WonderfulChickenService chickens;
    private final DisplayService displays;
    private final WorldListener worlds;
    private final LongSupplier biologicalTime;
    private final ArrayDeque<Chicken> pendingChickens = new ArrayDeque<>();
    private final ArrayDeque<Chunk> pendingChunks = new ArrayDeque<>();
    private final Map<UUID, Long> lastAgeProjection = new HashMap<>();
    private long tick;
    private long nextChickenSweep;
    private long nextWorldSweep;

    public IntegrityController(
            WonderfulChickenService chickens,
            DisplayService displays,
            WorldListener worlds,
            LongSupplier biologicalTime) {
        this.chickens = chickens;
        this.displays = displays;
        this.worlds = worlds;
        this.biologicalTime = biologicalTime;
    }

    @Override
    public void run() {
        tick++;
        displays.tick();

        // These two queues are already bounded by pending event/migration IDs.
        if (tick % 20 == 0) {
            worlds.retryPendingRestoration();
            chickens.retryLegacyMigrations();
        }

        if (pendingChickens.isEmpty() && tick >= nextChickenSweep) {
            nextChickenSweep = tick + 200L;
            var snapshot = chickens.loadedChickens();
            Set<UUID> registered = new HashSet<>();
            for (Chicken chicken : snapshot) {
                registered.add(chicken.getUniqueId());
                pendingChickens.addLast(chicken);
            }
            lastAgeProjection.keySet().retainAll(registered);
        }

        long ageBucket = biologicalTime.getAsLong() / AGE_REFRESH_QUANTUM;
        for (int i = 0; i < MAX_CHICKENS_PER_TICK && !pendingChickens.isEmpty(); i++) {
            Chicken chicken = pendingChickens.removeFirst();
            if (!chicken.isValid() || chickens.isSuperseded(chicken)) continue;

            UUID id = chicken.getUniqueId();
            boolean headChanged = chickens.captureHeadEquipment(chicken);
            if (lastAgeProjection.getOrDefault(id, Long.MIN_VALUE) != ageBucket) {
                chickens.projectAttributes(chicken);
                displays.rebuild(chicken);
                lastAgeProjection.put(id, ageBucket);
            } else if (headChanged) {
                displays.rebuild(chicken);
            }
        }

        // Rare late-PDC/third-party restorations still have a fallback, but
        // chunk entity queries are spread over many ticks instead of a
        // global world scan every five seconds.
        if (pendingChunks.isEmpty() && tick >= nextWorldSweep) {
            nextWorldSweep = tick + 200L;
            for (World world : Bukkit.getWorlds()) {
                for (Chunk chunk : world.getLoadedChunks()) {
                    pendingChunks.addLast(chunk);
                }
            }
        }
        for (int i = 0; i < MAX_CHUNKS_PER_TICK && !pendingChunks.isEmpty(); i++) {
            Chunk chunk = pendingChunks.removeFirst();
            if (!chunk.isLoaded()) continue;
            for (Entity entity : chunk.getEntities()) {
                if (entity instanceof Chicken chicken
                        && chickens.registerLoaded(chicken)) {
                    displays.rebuild(chicken);
                }
            }
        }
    }
}
