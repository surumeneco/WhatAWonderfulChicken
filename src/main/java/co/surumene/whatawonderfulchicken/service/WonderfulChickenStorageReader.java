package co.surumene.whatawonderfulchicken.service;

import co.surumene.whatawonderfulchicken.WhatAWonderfulChickenPlugin;
import co.surumene.wcl.api.inventory.StorageReadResult;
import co.surumene.wcl.api.inventory.StorageSnapshot;
import co.surumene.wcl.api.inventory.StoredItem;
import co.surumene.wcl.plugin.inventory.WonderfulStorageReader;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Read-only storage export, including data supplied by an offline PDC reader. */
public final class WonderfulChickenStorageReader implements WonderfulStorageReader {
    private final WhatAWonderfulChickenPlugin plugin;
    public WonderfulChickenStorageReader(WhatAWonderfulChickenPlugin plugin) {
        this.plugin=Objects.requireNonNull(plugin);
    }
    @Override public String providerId(){return "wwc";}
    @Override public StorageReadResult readLoaded(UUID entityId){
        Objects.requireNonNull(entityId);
        if(!plugin.isEnabled())return new StorageReadResult.Failure("DISABLED","WWC is disabled");
        Entity entity=Bukkit.getEntity(entityId);
        if(!(entity instanceof Chicken chicken))return new StorageReadResult.NotFound();
        return readSaved(entityId, chicken.getWorld().getUID(),
                Optional.of(new StorageSnapshot.Position(chicken.getX(),chicken.getY(),chicken.getZ())),
                chicken.getPersistentDataContainer());
    }
    @Override public StorageReadResult readStored(UUID entityId, UUID worldId,
            Optional<StorageSnapshot.Position> position, PersistentDataContainer pdc){
        Objects.requireNonNull(entityId);
        Objects.requireNonNull(worldId);
        Objects.requireNonNull(position);
        Objects.requireNonNull(pdc);
        if(!plugin.isEnabled())return new StorageReadResult.Failure("DISABLED","WWC is disabled");
        try {
            if(!"wonderful_chicken".equals(pdc.get(key("type"),PersistentDataType.STRING)))
                return new StorageReadResult.NotFound();
            Integer version=pdc.get(key("data_version"),PersistentDataType.INTEGER);
            if(version!=null && version!=2 && version!=3 && version!=4)
                return new StorageReadResult.Failure("UNSUPPORTED_SCHEMA","Unsupported WWC data version "+version);
            if(version!=null && version==4) {
                String ref=pdc.get(key("genome_ref"),PersistentDataType.STRING);
                if(ref==null)return new StorageReadResult.Failure("MISSING_DATA","Missing WWC genome reference");
                UUID.fromString(ref);
            }
            Map<String,UUID> relations=new HashMap<>();
            String follow=pdc.get(key("follow_target"),PersistentDataType.STRING);
            if(follow!=null)relations.put("follow_target",UUID.fromString(follow));
            List<StoredItem> items=new ArrayList<>(3);
            add(items,pdc,"carpet","equipment_carpet");
            add(items,pdc,"shulker","equipment_shulker_box");
            add(items,pdc,"head","equipment_head");
            return new StorageReadResult.Success(new StorageSnapshot(
                    entityId,providerId(),worldId,position,relations,items));
        }catch(RuntimeException problem){
            return new StorageReadResult.Failure("CORRUPT_DATA",
                    problem.getClass().getSimpleName()+": "+String.valueOf(problem.getMessage()));
        }
    }
    private void add(List<StoredItem> out,PersistentDataContainer pdc,String slot,String key){
        byte[] raw=pdc.get(key(key),PersistentDataType.BYTE_ARRAY);
        if(raw==null)return;
        if(raw.length==0)throw new IllegalArgumentException("Empty serialized item "+key);
        ItemStack item=ItemStack.deserializeBytes(raw);
        if(item==null || item.isEmpty())throw new IllegalArgumentException("Empty stored ItemStack "+key);
        // Keep the original serialized bytes, including custom PDC and nested Shulker/Bundle data.
        out.add(new StoredItem(slot,raw));
    }
    private NamespacedKey key(String name){return new NamespacedKey(plugin,name);}
}
