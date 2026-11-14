package co.surumene.whatawonderfulchicken.chickentrap;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProtect(EntityDamageEvent event) {
        if (runtime.protectDamage(event.getEntity())) {
            // The protection invariant must survive a failed particle/sound call.
            event.setCancelled(true);
            if (event instanceof EntityDamageByEntityEvent) {
                runtime.playProtectedHitEffect(event.getEntity());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDirectHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Skeleton skeleton)
                || !playerAttack(event.getDamager())) return;
        if (runtime.attackSkeleton(skeleton)) {
            // Consume the initiating hit so an otherwise lethal blow cannot
            // remove the bait after the prepared riders have been mounted.
            event.setCancelled(true);
        }
    }

    private static boolean playerAttack(Entity damager) {
        if (damager instanceof Player) return true;
        return damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Player;
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
