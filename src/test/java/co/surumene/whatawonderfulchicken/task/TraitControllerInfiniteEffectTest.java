package co.surumene.whatawonderfulchicken.task;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Integration guard for WWC's call into WCL's potion refresh decision. */
class TraitControllerInfiniteEffectTest {
    @Test
    void riderKeepsExistingInfiniteNightVisionWithoutThrowing() throws Exception {
        Player player = mock(Player.class);
        PotionEffect existing = mock(PotionEffect.class);
        when(existing.getAmplifier()).thenReturn(0);
        when(existing.getDuration()).thenReturn(-1);
        when(player.getPotionEffect(PotionEffectType.NIGHT_VISION)).thenReturn(existing);

        TraitController controller = new TraitController(null, null, null, null);
        Method apply = TraitController.class.getDeclaredMethod(
                "apply", LivingEntity.class, PotionEffectType.class, int.class);
        apply.setAccessible(true);

        apply.invoke(controller, player, PotionEffectType.NIGHT_VISION, 0);

        verify(player, never()).addPotionEffect(any(PotionEffect.class));
    }
}
