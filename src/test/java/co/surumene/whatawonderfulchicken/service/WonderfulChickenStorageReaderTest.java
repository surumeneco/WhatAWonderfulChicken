package co.surumene.whatawonderfulchicken.service;
import co.surumene.whatawonderfulchicken.WhatAWonderfulChickenPlugin;
import co.surumene.wcl.api.inventory.StorageReadResult;
import java.util.*;
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
}
