package co.surumene.whatawonderfulchicken.task;

import co.surumene.whatawonderfulchicken.display.DisplayService;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenService;
import org.bukkit.entity.Chicken;

public final class IntegrityController implements Runnable {
    private final WonderfulChickenService chickens;
    private final DisplayService displays;
    private int tick;

    public IntegrityController(WonderfulChickenService chickens, DisplayService displays) {
        this.chickens = chickens;
        this.displays = displays;
    }

    @Override
    public void run() {
        tick++;
        displays.tick();
        if (tick % 20 == 0) {
            displays.refreshVisibility();
            for (Chicken chicken : chickens.loadedChickens()) {
                chickens.captureHeadEquipment(chicken);
                chickens.projectAttributes(chicken);
                displays.rebuild(chicken);
            }
        }
    }
}