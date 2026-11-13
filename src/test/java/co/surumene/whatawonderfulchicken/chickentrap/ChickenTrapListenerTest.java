package co.surumene.whatawonderfulchicken.chickentrap;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class ChickenTrapListenerTest {
    private final ChickenTrapRuntime runtime = mock(ChickenTrapRuntime.class);
    private final ChickenTrapListener listener = new ChickenTrapListener(runtime);
    private final Skeleton waiting = mock(Skeleton.class);

    @Test
    void onlyPlayerOrPlayerProjectileCanTriggerTrap() {
        EntityDamageByEntityEvent mobAttack = mock(EntityDamageByEntityEvent.class);
        when(mobAttack.getEntity()).thenReturn(waiting);
        when(mobAttack.getDamager()).thenReturn(mock(Zombie.class));
        listener.onDirectHit(mobAttack);
        verify(runtime, never()).attackSkeleton(waiting);

        EntityDamageByEntityEvent playerAttack = mock(EntityDamageByEntityEvent.class);
        when(playerAttack.getEntity()).thenReturn(waiting);
        when(playerAttack.getDamager()).thenReturn(mock(Player.class));
        when(runtime.attackSkeleton(waiting)).thenReturn(true);
        listener.onDirectHit(playerAttack);
        verify(runtime).attackSkeleton(waiting);
        verify(playerAttack).setCancelled(true);

        EntityDamageByEntityEvent projectileAttack = mock(EntityDamageByEntityEvent.class);
        Projectile arrow = mock(Projectile.class);
        when(arrow.getShooter()).thenReturn(mock(Player.class));
        when(projectileAttack.getEntity()).thenReturn(waiting);
        when(projectileAttack.getDamager()).thenReturn(arrow);
        listener.onDirectHit(projectileAttack);
        verify(runtime, times(2)).attackSkeleton(waiting);
        verify(projectileAttack).setCancelled(true);
    }

    @Test
    void preparationCancelsDamageAndFeedbackIsSpecificToEntityAttack() {
        EntityDamageEvent environment = mock(EntityDamageEvent.class);
        when(environment.getEntity()).thenReturn(waiting);
        when(runtime.protectDamage(waiting, false)).thenReturn(true);
        listener.onProtect(environment);
        verify(environment).setCancelled(true);
        verify(runtime).protectDamage(waiting, false);

        EntityDamageByEntityEvent attack = mock(EntityDamageByEntityEvent.class);
        when(attack.getEntity()).thenReturn(waiting);
        when(runtime.protectDamage(waiting, true)).thenReturn(true);
        listener.onProtect(attack);
        verify(attack).setCancelled(true);
        verify(runtime).protectDamage(waiting, true);
    }
}
