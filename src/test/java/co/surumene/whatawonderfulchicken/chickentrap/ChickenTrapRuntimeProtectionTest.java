package co.surumene.whatawonderfulchicken.chickentrap;

import co.surumene.whatawonderfulchicken.WhatAWonderfulChickenPlugin;
import co.surumene.whatawonderfulchicken.config.ConfigService;
import co.surumene.whatawonderfulchicken.display.DisplayService;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenService;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenStore;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Skeleton;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChickenTrapRuntimeProtectionTest {
    @Test
    void unpreparedBaitIsImmuneWithoutActivating() {
        // Mock state-store construction: Paper namespaces require a real server
        // plugin, which is not installed in this headless JUnit environment.
        try (MockedConstruction<ChickenTrapStateStore> stateConstruction =
                mockConstruction(ChickenTrapStateStore.class,
                        (state, ignored) -> when(state.hasRole(
                                any(Skeleton.class), eq(ChickenTrapStateStore.ARMED)))
                                .thenReturn(true))) {
            WhatAWonderfulChickenPlugin plugin = mock(WhatAWonderfulChickenPlugin.class);
            ChickenTrapRuntime runtime = new ChickenTrapRuntime(
                    plugin, mock(ConfigService.class), mock(WonderfulChickenService.class),
                    mock(WonderfulChickenStore.class), mock(DisplayService.class));
            try {
                World world = mock(World.class);
                Skeleton bait = mock(Skeleton.class);
                when(bait.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
                when(bait.isValid()).thenReturn(true);
                when(bait.getWorld()).thenReturn(world);
                when(bait.getLocation()).thenReturn(new Location(world, 2, 65, 3));

                assertTrue(runtime.protectDamage(bait));
                verifyNoInteractions(world);
                assertFalse(runtime.attackSkeleton(bait));
            } finally {
                runtime.shutdown();
            }
        }
    }
}
