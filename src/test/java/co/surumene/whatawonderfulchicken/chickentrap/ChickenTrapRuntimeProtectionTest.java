package co.surumene.whatawonderfulchicken.chickentrap;

import co.surumene.whatawonderfulchicken.WhatAWonderfulChickenPlugin;
import co.surumene.whatawonderfulchicken.config.ConfigService;
import co.surumene.whatawonderfulchicken.display.DisplayService;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenService;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenStore;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Skeleton;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChickenTrapRuntimeProtectionTest {
    @Test
    void unpreparedBaitIsImmuneWithoutActivating() {
        WhatAWonderfulChickenPlugin plugin = mock(WhatAWonderfulChickenPlugin.class);
        when(plugin.getName()).thenReturn("WhatAWonderfulChicken");
        io.papermc.paper.plugin.configuration.PluginMeta pluginMeta =
                mock(io.papermc.paper.plugin.configuration.PluginMeta.class);
        when(pluginMeta.getName()).thenReturn("WhatAWonderfulChicken");
        when(plugin.getPluginMeta()).thenReturn(pluginMeta);
        ChickenTrapRuntime runtime = new ChickenTrapRuntime(
                plugin, mock(ConfigService.class), mock(WonderfulChickenService.class),
                mock(WonderfulChickenStore.class), mock(DisplayService.class));
        try {
            World world = mock(World.class);
            Skeleton bait = mock(Skeleton.class);
            PersistentDataContainer pdc = mock(PersistentDataContainer.class);
            when(bait.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
            when(bait.getPersistentDataContainer()).thenReturn(pdc);
            when(pdc.get(eq(new NamespacedKey(plugin, "chicken_trap_role")),
                    eq(PersistentDataType.STRING))).thenReturn(ChickenTrapStateStore.ARMED);
            when(bait.isValid()).thenReturn(true);
            when(bait.getWorld()).thenReturn(world);
            when(bait.getLocation()).thenReturn(new Location(world, 2, 65, 3));

            assertTrue(runtime.protectDamage(bait, false));
            verifyNoInteractions(world);
                assertFalse(runtime.attackSkeleton(bait));
        } finally {
            runtime.shutdown();
        }
    }
}
