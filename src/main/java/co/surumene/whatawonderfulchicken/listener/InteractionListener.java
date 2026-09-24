package co.surumene.whatawonderfulchicken.listener;

import co.surumene.whatawonderfulchicken.WhatAWonderfulChickenPlugin;
import co.surumene.whatawonderfulchicken.config.ConfigService;
import co.surumene.whatawonderfulchicken.config.MessageService;
import co.surumene.whatawonderfulchicken.data.WonderfulChickenData;
import co.surumene.whatawonderfulchicken.display.DisplayService;
import co.surumene.whatawonderfulchicken.gui.InventoryService;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenService;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenStore;
import co.surumene.whatawonderfulchicken.task.RidingController;
import co.surumene.whatawonderfulchicken.util.ItemUtil;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.Set;

public final class InteractionListener implements Listener {
    private static final Set<Material> SEEDS = Set.of(
            Material.WHEAT_SEEDS, Material.MELON_SEEDS, Material.PUMPKIN_SEEDS,
            Material.BEETROOT_SEEDS, Material.TORCHFLOWER_SEEDS, Material.PITCHER_POD);

    private final WhatAWonderfulChickenPlugin plugin;
    private final WonderfulChickenService chickens;
    private final WonderfulChickenStore store;
    private final ConfigService config;
    private final MessageService messages;
    private final InventoryService inventories;
    private final DisplayService displays;
    private final RidingController riding;

    public InteractionListener(WhatAWonderfulChickenPlugin plugin, WonderfulChickenService chickens, WonderfulChickenStore store,
                               ConfigService config, MessageService messages, InventoryService inventories, DisplayService displays,
                               RidingController riding) {
        this.plugin = plugin;
        this.chickens = chickens;
        this.store = store;
        this.config = config;
        this.messages = messages;
        this.inventories = inventories;
        this.displays = displays;
        this.riding = riding;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();

        Chicken chicken;
        boolean mountedProxy;
        if (event.getRightClicked() instanceof Chicken direct && store.isWonderful(direct)) {
            chicken = direct;
            mountedProxy = false;
        } else {
            chicken = riding.interactionOwner(event.getRightClicked());
            if (chicken == null) return;
            mountedProxy = true;
        }

        if (player.isSneaking()) return;
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (mountedProxy || player.getVehicle() == chicken) {
            if (player.getVehicle() != chicken) return;
            event.setCancelled(true);
            if (ItemUtil.isCarpet(hand)) equipCarpet(player, chicken, hand);
            else inventories.open(player, chicken);
            return;
        }

        if (ItemUtil.isCarpet(hand)) {
            event.setCancelled(true);
            equipCarpet(player, chicken, hand);
            return;
        }

        if (SEEDS.contains(hand.getType())) {
            if (chicken.getHealth() < chicken.getMaxHealth()) {
                event.setCancelled(true);
                chicken.setHealth(Math.min(chicken.getMaxHealth(), chicken.getHealth() + config.healPerSeed()));
                consumeOne(player, hand);
            }
            return;
        }

        if (hand.getType() == Material.NAME_TAG) return;
        if (!chicken.isAdult() || store.load(chicken).carpet() == null) return;
        if (!chicken.getPassengers().isEmpty()) return;
        event.setCancelled(true);
        chicken.getPathfinder().stopPathfinding();
        chicken.setAware(false);
        chicken.addPassenger(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMountedInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (player.isSneaking()) return;
        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof Chicken chicken) || !store.isWonderful(chicken)) return;
        if (!isLookingAtMount(player, chicken)) return;
        ItemStack hand = player.getInventory().getItemInMainHand();
        event.setCancelled(true);
        if (ItemUtil.isCarpet(hand)) equipCarpet(player, chicken, hand);
        else inventories.open(player, chicken);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player
                && player.getVehicle() instanceof Chicken chicken
                && store.isWonderful(chicken)
                && event.getFoodLevel() < player.getFoodLevel()) {
            event.setCancelled(true);
        }
    }

    private void equipCarpet(Player player, Chicken chicken, ItemStack hand) {
        if (!chicken.isAdult()) {
            player.sendMessage(messages.text(player, "error.adult_only"));
            return;
        }
        WonderfulChickenData data = store.load(chicken);
        ItemStack old = data.carpet();
        data.carpet(hand.asOne());
        store.save(chicken, data);
        displays.rebuild(chicken);
        consumeOne(player, hand);
        if (old != null) {
            var leftover = player.getInventory().addItem(old);
            leftover.values().forEach(item -> chicken.getWorld().dropItemNaturally(chicken.getLocation(), item));
        }
    }

    private void consumeOne(Player player, ItemStack hand) {
        if (player.getGameMode() == GameMode.CREATIVE) return;
        hand.subtract(1);
        player.getInventory().setItemInMainHand(hand.isEmpty() ? null : hand);
    }

    private boolean isLookingAtMount(Player player, Chicken chicken) {
        Vector start = player.getEyeLocation().toVector();
        Vector direction = player.getEyeLocation().getDirection().normalize();
        BoundingBox target = chicken.getBoundingBox().clone().expand(0.35, 0.25, 0.35);
        return target.rayTrace(start, direction, 6.0) != null;
    }
}