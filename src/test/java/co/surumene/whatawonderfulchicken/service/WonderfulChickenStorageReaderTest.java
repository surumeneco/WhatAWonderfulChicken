package co.surumene.whatawonderfulchicken.service;
import co.surumene.whatawonderfulchicken.WhatAWonderfulChickenPlugin;
import co.surumene.wcl.api.inventory.StorageReadResult;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Chicken;
import org.mockito.MockedStatic;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

final class WonderfulChickenStorageReaderTest {
    @Test void savedPdcReturnsIndependentStorageWithFollowRelationshipAndCorruptItemsFailClosed(){
        var plugin=Mockito.mock(WhatAWonderfulChickenPlugin.class);
        Mockito.when(plugin.isEnabled()).thenReturn(true);
        PersistentDataContainer pdc=Mockito.mock(PersistentDataContainer.class);
        Mockito.when(pdc.get(new NamespacedKey("whatawonderfulchicken","type"),PersistentDataType.STRING))
                .thenReturn("wonderful_chicken");
        Mockito.when(pdc.get(new NamespacedKey("whatawonderfulchicken","data_version"),PersistentDataType.INTEGER))
                .thenReturn(2);
        UUID commander=UUID.randomUUID();
        Mockito.when(pdc.get(new NamespacedKey("whatawonderfulchicken","follow_target"),PersistentDataType.STRING))
                .thenReturn(commander.toString());
        var reader=new WonderfulChickenStorageReader(plugin);
        UUID id=UUID.randomUUID(),world=UUID.randomUUID();
        var response=reader.readStored(id,world,Optional.empty(),pdc);
        assertInstanceOf(StorageReadResult.Success.class,response);
        var snapshot=((StorageReadResult.Success)response).snapshot();
        assertEquals(commander,snapshot.relationships().get("follow_target"));
        assertTrue(snapshot.items().isEmpty());
        Mockito.when(pdc.get(new NamespacedKey("whatawonderfulchicken","equipment_head"),PersistentDataType.BYTE_ARRAY))
                .thenReturn(new byte[]{1,2,3});
        var broken=reader.readStored(id,world,Optional.empty(),pdc);
        assertInstanceOf(StorageReadResult.Failure.class,broken);
        assertEquals("CORRUPT_DATA",((StorageReadResult.Failure)broken).code());
    }
    @Test void disabledPluginAndUnrecognizedMobDoNotAppearAsEmptyInventory(){
        var plugin=Mockito.mock(WhatAWonderfulChickenPlugin.class);
        var reader=new WonderfulChickenStorageReader(plugin);
        var pdc=Mockito.mock(PersistentDataContainer.class);
        assertInstanceOf(StorageReadResult.Failure.class,
                reader.readStored(UUID.randomUUID(),UUID.randomUUID(),Optional.empty(),pdc));
        Mockito.when(plugin.isEnabled()).thenReturn(true);
        assertInstanceOf(StorageReadResult.NotFound.class,
                reader.readStored(UUID.randomUUID(),UUID.randomUUID(),Optional.empty(),pdc));
    }

    @Test void loadedChickenUsesCurrentWorldAndPositionWithoutChangingPdc() {
        var plugin = Mockito.mock(WhatAWonderfulChickenPlugin.class);
        Mockito.when(plugin.isEnabled()).thenReturn(true);
        var chicken = Mockito.mock(Chicken.class);
        var world = Mockito.mock(World.class);
        var location = Mockito.mock(Location.class);
        var pdc = Mockito.mock(PersistentDataContainer.class);
        UUID entityId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();

        Mockito.when(chicken.getWorld()).thenReturn(world);
        Mockito.when(world.getUID()).thenReturn(worldId);
        Mockito.when(chicken.getLocation()).thenReturn(location);
        Mockito.when(location.getX()).thenReturn(12.5);
        Mockito.when(location.getY()).thenReturn(64.0);
        Mockito.when(location.getZ()).thenReturn(-30.25);
        Mockito.when(chicken.getPersistentDataContainer()).thenReturn(pdc);
        Mockito.when(pdc.get(new NamespacedKey("whatawonderfulchicken", "type"),
                PersistentDataType.STRING)).thenReturn("wonderful_chicken");

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getEntity(entityId)).thenReturn(chicken);
            var result = new WonderfulChickenStorageReader(plugin).readLoaded(entityId);
            var snapshot = assertInstanceOf(StorageReadResult.Success.class, result).snapshot();
            assertEquals(entityId, snapshot.entityId());
            assertEquals(worldId, snapshot.worldId());
            assertEquals("wwc", snapshot.providerId());
            assertEquals(Optional.of(new co.surumene.wcl.api.inventory.StorageSnapshot.Position(
                    12.5, 64.0, -30.25)), snapshot.position());
            assertTrue(snapshot.relationships().isEmpty());
            assertTrue(snapshot.items().isEmpty());
            bukkit.verify(() -> Bukkit.getEntity(entityId));
            Mockito.verify(chicken).getPersistentDataContainer();
            Mockito.verify(pdc, Mockito.never()).set(
                    Mockito.any(NamespacedKey.class),
                    Mockito.any(PersistentDataType.class), Mockito.any());
        }
    }
}
