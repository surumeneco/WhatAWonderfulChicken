package co.surumene.whatawonderfulchicken.listener;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldListenerEggTest {
    @Test
    void goldTraitReplacesAllNaturallyLaidEggColors() {
        assertTrue(WorldListener.isChickenEgg(Material.EGG));
        assertTrue(WorldListener.isChickenEgg(Material.BROWN_EGG));
        assertTrue(WorldListener.isChickenEgg(Material.BLUE_EGG));
        assertFalse(WorldListener.isChickenEgg(Material.CHICKEN_SPAWN_EGG));
        assertFalse(WorldListener.isChickenEgg(Material.RAW_GOLD));
    }
}
