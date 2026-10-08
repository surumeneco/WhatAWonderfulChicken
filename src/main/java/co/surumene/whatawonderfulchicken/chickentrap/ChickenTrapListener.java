package co.surumene.whatawonderfulchicken.chickentrap;

import org.bukkit.entity.Entity;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.TimeSkipEvent;

public final class ChickenTrapListener implements Listener {
    private final ChickenTrapRuntime runtime;
    public ChickenTrapListener(ChickenTrapRuntime runtime) { this.runtime = runtime; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof WindCharge charge) runtime.hitCharge(charge);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTimeSkip(TimeSkipEvent event) { runtime.markTimeSkip(event.getWorld()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDirectHit(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof org.bukkit.entity.Skeleton skeleton)) return;
        org.bukkit.entity.Entity source = e.getDamager();
        if (source instanceof org.bukkit.entity.Player) {
            runtime.attackSkeleton(skeleton);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(org.bukkit.event.entity.EntityDeathEvent e) {
        runtime.died(e.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) runtime.register(entity);
    }
}
