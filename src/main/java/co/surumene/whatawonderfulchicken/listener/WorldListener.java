package co.surumene.whatawonderfulchicken.listener;

import co.surumene.whatawonderfulchicken.display.DisplayService;
import co.surumene.whatawonderfulchicken.gui.InventoryService;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenService;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenStore;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public final class WorldListener implements Listener {
    private final WonderfulChickenService chickens;
    private final WonderfulChickenStore store;
    private final DisplayService displays;
    private final InventoryService inventories;

    public WorldListener(WonderfulChickenService chickens, WonderfulChickenStore store, DisplayService displays, InventoryService inventories) {
        this.chickens = chickens;
        this.store = store;
        this.displays = displays;
        this.inventories = inventories;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNaturalSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Chicken chicken)) return;
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        if (store.isWonderful(chicken)) return;
        if (chickens.rollNaturalConversion()) {
            chickens.initialize(chicken, chickens.createNaturalData());
            displays.rebuild(chicken);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (!(event.getEntity() instanceof Chicken child)) return;
        if (!(event.getMother() instanceof Chicken mother) || !(event.getFather() instanceof Chicken father)) return;
        if (store.isWonderful(mother) && store.isWonderful(father)) {
            chickens.initialize(child, chickens.createBredData(mother, father));
            displays.rebuild(child);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        displays.reconcileChunk(event.getChunk());
        for (Entity entity : event.getChunk().getEntities()) {
            if (!(entity instanceof Chicken chicken)) continue;
            if (store.isWonderful(chicken)) {
                chickens.registerLoaded(chicken);
                displays.rebuild(chicken);
            } else if (event.isNewChunk() && chickens.rollNaturalConversion()) {
                chickens.initialize(chicken, chickens.createNaturalData());
                displays.rebuild(chicken);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof Chicken chicken && store.isWonderful(chicken)) {
                inventories.closeFor(chicken);
                displays.removeFor(chicken);
                chickens.unregisterLoaded(chicken);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        displays.refreshVisibility();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Chicken chicken) || !store.isWonderful(chicken)) return;
        inventories.closeFor(chicken);
        var data = store.load(chicken);
        if (data.carpet() != null) event.getDrops().add(data.carpet());
        if (data.shulkerBox() != null) event.getDrops().add(data.shulkerBox());
        if (chicken.getEquipment() != null) {
            var actualHead = chicken.getEquipment().getHelmet();
            if (actualHead != null && !actualHead.isEmpty()) event.getDrops().add(actualHead.clone());
            chicken.getEquipment().setHelmet(null);
        } else if (data.headItem() != null) {
            event.getDrops().add(data.headItem());
        }
        displays.removeFor(chicken);
        chickens.unregisterLoaded(chicken);
    }
}