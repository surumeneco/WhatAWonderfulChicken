package co.surumene.whatawonderfulchicken.listener;

import co.surumene.whatawonderfulchicken.WhatAWonderfulChickenPlugin;
import co.surumene.whatawonderfulchicken.display.DisplayService;
import co.surumene.whatawonderfulchicken.config.ConfigService;
import co.surumene.whatawonderfulchicken.data.Trait;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class WorldListener implements Listener {
    private final WhatAWonderfulChickenPlugin plugin;
    private final ConfigService config;
    private final WonderfulChickenService chickens;
    private final WonderfulChickenStore store;
    private final DisplayService displays;
    private final InventoryService inventories;
    private final Map<UUID, Chicken> pendingRestoration = new HashMap<>();

    public WorldListener(WhatAWonderfulChickenPlugin plugin, WonderfulChickenService chickens, WonderfulChickenStore store,
                         DisplayService displays, InventoryService inventories, ConfigService config) {
        this.plugin = plugin;
        this.config = config;
        this.chickens = chickens;
        this.store = store;
        this.displays = displays;
        this.inventories = inventories;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityAdd(EntityAddToWorldEvent event) {
        if (!(event.getEntity() instanceof Chicken chicken)) return;
        // Other plugins can restore PDC after the spawn event. Check on the next tick.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!chicken.isValid()) return;
            if (!store.isWonderful(chicken)) {
                pendingRestoration.put(chicken.getUniqueId(), chicken);
                return;
            }
            chickens.registerLoaded(chicken);
            displays.rebuild(chicken);
        });
    }

    /** One-second delayed check for chickens whose PDC was restored after their add event. */
    public void retryPendingRestoration() {
        for (Chicken chicken : pendingRestoration.values()) {
            if (!chicken.isValid() || !store.isWonderful(chicken)) continue;
            chickens.registerLoaded(chicken);
            displays.rebuild(chicken);
        }
        pendingRestoration.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveFromWorldEvent event) {
        if (!(event.getEntity() instanceof Chicken chicken)) return;
        pendingRestoration.remove(chicken.getUniqueId(), chicken);
        if (!store.isWonderful(chicken)) return;
        if (chickens.isSuperseded(chicken)) {
            store.invalidate(chicken);
            return;
        }
        inventories.closeFor(chicken);
        displays.removeFor(chicken);
        chickens.unregisterLoaded(chicken);
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEggLay(EntityDropItemEvent event) {
        if (!(event.getEntity() instanceof Chicken chicken) || !store.isWonderful(chicken)) return;
        // All three vanilla egg colors are naturally laid by chicken variants.
        if (!isChickenEgg(event.getItemDrop().getItemStack().getType())) return;
        if (store.read(chicken).trait() != Trait.KIN_NO_TAMAGO) return;
        if (ThreadLocalRandom.current().nextDouble() >= config.eggGoldChance()) return;
        Material result = ThreadLocalRandom.current().nextDouble() < config.eggNetheriteChance()
                ? Material.NETHERITE_SCRAP : Material.RAW_GOLD;
        event.getItemDrop().setItemStack(org.bukkit.inventory.ItemStack.of(result));
    }

    static boolean isChickenEgg(Material item) {
        return item == Material.EGG || item == Material.BLUE_EGG || item == Material.BROWN_EGG;
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
                pendingRestoration.remove(chicken.getUniqueId(), chicken);
                if (chickens.isSuperseded(chicken)) {
                    store.invalidate(chicken);
                    continue;
                }
                inventories.closeFor(chicken);
                displays.removeFor(chicken);
                chickens.unregisterLoaded(chicken);
            }
        }
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
        if (!chickens.isSuperseded(chicken)) displays.removeFor(chicken);
        chickens.unregisterLoaded(chicken);
    }
}