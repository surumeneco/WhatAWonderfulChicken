package co.surumene.whatawonderfulchicken.task;

import co.surumene.whatawonderfulchicken.display.DisplayService;
import co.surumene.whatawonderfulchicken.listener.WorldListener;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenService;
import org.bukkit.entity.Chicken;

public final class IntegrityController implements Runnable {
    private final WonderfulChickenService chickens;
    private final DisplayService displays;
    private final WorldListener worlds;
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
        if (tick % 20 == 0) {
            worlds.retryPendingRestoration();
            // Full-world fallback handles rare late PDC restoration with no add event.
            if (tick % 100 == 0) {
                for (Chicken chicken : chickens.reconcileLoadedWorlds()) {
                    displays.rebuild(chicken);
                }
            }
            for (Chicken chicken : chickens.loadedChickens()) {
                chickens.captureHeadEquipment(chicken);
                chickens.projectAttributes(chicken);
                displays.rebuild(chicken);
            }
        }
    }
}