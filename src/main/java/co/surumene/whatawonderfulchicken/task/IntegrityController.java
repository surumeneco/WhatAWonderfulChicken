package co.surumene.whatawonderfulchicken.task;

import co.surumene.whatawonderfulchicken.display.DisplayService;
import co.surumene.whatawonderfulchicken.listener.WorldListener;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenService;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Chicken;

import java.util.ArrayDeque;
import java.util.Collection;

/**
 * Distributes maintenance over successive ticks instead of performing all
 * registered-chicken projection and world reconciliation at one-second edges.
 * All entity and Paper operations stay on the main server thread.
 */
public final class IntegrityController implements Runnable {
    private static final int MAINTENANCE_SPAN_TICKS = 20;
    private static final int CHUNK_SCAN_INTERVAL_TICKS = 100;
    private static final int CHUNKS_PER_TICK = 2;

    private final WonderfulChickenService chickens;
    private final DisplayService displays;
    private final WorldListener worlds;
    private final ArrayDeque<Chicken> maintenance = new ArrayDeque<>();
    private final ArrayDeque<Chunk> pendingChunks = new ArrayDeque<>();
    private int maintenanceBudget;
    private int tick;

    public IntegrityController(WonderfulChickenService chickens, DisplayService displays, WorldListener worlds) {
        this.chickens = chickens;
        this.displays = displays;
        this.worlds = worlds;
    }

    @Override
    public void run() {
        tick++;
        displays.tick();

        if (tick % MAINTENANCE_SPAN_TICKS == 1) {
            Collection<Chicken> registered = chickens.loadedChickens();
            maintenance.clear();
            maintenance.addAll(registered);
            maintenanceBudget = perTickBudget(registered.size());
        }
        // Keep the delayed PDC restoration check, but move expensive
        // projection/rebuild work into a bounded per-tick maintenance wheel.
        if (tick % MAINTENANCE_SPAN_TICKS == 0) {
            worlds.retryPendingRestoration();
        }

        for (int processed = 0; processed < maintenanceBudget && !maintenance.isEmpty(); processed++) {
            Chicken chicken = maintenance.removeFirst();
            if (!chicken.isValid() || !chickens.isRegistered(chicken)) continue;
            chickens.retryLegacyMigration(chicken);
            chickens.captureHeadEquipment(chicken);
            chickens.projectAttributes(chicken);
            displays.rebuild(chicken);
        }

        // A third-party plugin can restore PDC after an add event.
        // Reconcile loaded CHUNKS incrementally instead of scanning every
        // chicken in every world during one tick.
        if (tick % CHUNK_SCAN_INTERVAL_TICKS == 0 && pendingChunks.isEmpty()) {
            for (World world : Bukkit.getWorlds()) {
                for (Chunk chunk : world.getLoadedChunks()) {
                    pendingChunks.addLast(chunk);
                }
            }
        }
        for (int i = 0; i < CHUNKS_PER_TICK && !pendingChunks.isEmpty(); i++) {
            Chunk chunk = pendingChunks.removeFirst();
            if (!chunk.isLoaded()) continue;
            for (Chicken chicken : chickens.reconcileLoadedChunk(chunk)) {
                displays.rebuild(chicken);
            }
        }
    }

    static int perTickBudget(int count) {
        if (count < 0) throw new IllegalArgumentException("count must be >= 0");
        return Math.max(1, (count / MAINTENANCE_SPAN_TICKS)
                + (count % MAINTENANCE_SPAN_TICKS == 0 ? 0 : 1));
    }
}
