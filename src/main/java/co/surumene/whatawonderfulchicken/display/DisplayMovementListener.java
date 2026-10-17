package co.surumene.whatawonderfulchicken.display;

import io.papermc.paper.event.entity.EntityMoveEvent;
import co.surumene.whatawonderfulchicken.WhatAWonderfulChickenPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Chicken;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTeleportEvent;
import java.util.Objects;

/** Candidate B: targeted display sync without scanning every equipped Chicken each tick. */
public final class DisplayMovementListener implements Listener {
    private final DisplayService displays;
    private final WhatAWonderfulChickenPlugin plugin;

    public DisplayMovementListener(WhatAWonderfulChickenPlugin plugin, DisplayService displays) {
        this.plugin = Objects.requireNonNull(plugin);
        this.displays = Objects.requireNonNull(displays);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(EntityMoveEvent event) {
        if (event.getEntity() instanceof Chicken chicken
                && DisplayMoveSyncPolicy.shouldSync(
                    event.hasChangedPosition(), event.hasChangedOrientation())) {
            displays.syncMoving(chicken);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(EntityTeleportEvent event) {
        if (event.getEntity() instanceof Chicken chicken) {
            // Teleport events can fire before the destination has been committed.
            // Re-read the final entity location on the following server tick.
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (plugin.isEnabled()) displays.syncMoving(chicken);
            });
        }
    }
}
