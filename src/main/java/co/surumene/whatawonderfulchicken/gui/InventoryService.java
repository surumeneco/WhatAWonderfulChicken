package co.surumene.whatawonderfulchicken.gui;

import co.surumene.whatawonderfulchicken.WhatAWonderfulChickenPlugin;
import co.surumene.whatawonderfulchicken.config.ConfigService;
import co.surumene.whatawonderfulchicken.config.MessageService;
import co.surumene.whatawonderfulchicken.data.AncestorSnapshot;
import co.surumene.whatawonderfulchicken.data.BehaviorMode;
import co.surumene.whatawonderfulchicken.data.PedigreeData;
import co.surumene.whatawonderfulchicken.data.Rank;
import co.surumene.whatawonderfulchicken.data.StatType;
import co.surumene.whatawonderfulchicken.data.WonderfulChickenData;
import co.surumene.whatawonderfulchicken.display.DisplayService;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenService;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenStore;
import co.surumene.whatawonderfulchicken.util.ItemUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class InventoryService {
    public static final int SLOT_CARPET = 0;
    public static final int SLOT_SHULKER = 1;
    public static final int SLOT_HEAD = 2;
    public static final int SLOT_BEHAVIOR = 6;
    public static final int SLOT_INFO = 7;
    public static final int SLOT_PEDIGREE = 8;

    private final WhatAWonderfulChickenPlugin plugin;
    private final WonderfulChickenService chickens;
    private final WonderfulChickenStore store;
    private final ConfigService config;
    private final MessageService messages;
    private final DisplayService displays;
    private final Map<UUID, UUID> locks = new HashMap<>();
    private final Set<UUID> skipCargoSyncOnClose = new HashSet<>();

    public InventoryService(
            WhatAWonderfulChickenPlugin plugin,
            WonderfulChickenService chickens,
            WonderfulChickenStore store,
            ConfigService config,
            MessageService messages,
            DisplayService displays) {
        this.plugin = plugin;
        this.chickens = chickens;
        this.store = store;
        this.config = config;
        this.messages = messages;
        this.displays = displays;
    }

    public void open(Player player, Chicken chicken) {
        UUID holder = locks.get(chicken.getUniqueId());
        if (holder != null && !holder.equals(player.getUniqueId())) {
            player.sendMessage(messages.text(player, "error.inventory_busy"));
            return;
        }
        WonderfulChickenData data = store.load(chicken);
        boolean cargo = data.shulkerBox() != null;
        int size = cargo ? 36 : 9;
        WonderfulChickenInventoryHolder customHolder = new WonderfulChickenInventoryHolder(chicken.getUniqueId());
        Inventory inventory = Bukkit.createInventory(customHolder, size, Component.text(messages.text(player.locale(), "gui.title")));
        customHolder.inventory(inventory);
        populateMeta(player, inventory, data);
        if (cargo) loadCargo(inventory, data.shulkerBox());
        locks.put(chicken.getUniqueId(), player.getUniqueId());
        player.openInventory(inventory);
    }

    public void handleClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof WonderfulChickenInventoryHolder holder)) return;
        Entity raw = Bukkit.getEntity(holder.chickenId());
        if (!(raw instanceof Chicken chicken) || !store.isWonderful(chicken)) {
            event.setCancelled(true);
            event.getWhoClicked().closeInventory();
            return;
        }
        int rawSlot = event.getRawSlot();
        int topSize = top.getSize();
        boolean clickedTop = rawSlot >= 0 && rawSlot < topSize;

        if (event.isShiftClick()) {
            if (!clickedTop) {
                event.setCancelled(true);
                if (topSize == 36) {
                    ItemStack moving = event.getCurrentItem();
                    if (moving != null && !moving.isEmpty() && !ItemUtil.isShulkerBox(moving)) {
                        moveToCargo(top, moving, event);
                        scheduleCargoSync(chicken, top);
                    }
                }
                return;
            }
            if (rawSlot < 9) event.setCancelled(true);
            else scheduleCargoSync(chicken, top);
            return;
        }

        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }

        if (clickedTop && rawSlot < 9) {
            event.setCancelled(true);
            if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
            if (rawSlot <= SLOT_HEAD) handleEquipmentClick(event, chicken, rawSlot);
            else if (rawSlot == SLOT_BEHAVIOR) handleBehaviorClick(event, chicken);
            else if (rawSlot == SLOT_INFO || rawSlot == SLOT_PEDIGREE) {
                if (event.getWhoClicked() instanceof Player player) sendDetailedInfo(player, chicken, rawSlot == SLOT_PEDIGREE);
            }
            return;
        }

        if (clickedTop && rawSlot >= 9 && incomingShulkerBox(event)) {
            event.setCancelled(true);
            return;
        }
        if (clickedTop && rawSlot >= 9) scheduleCargoSync(chicken, top);
    }

    public void handleDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof WonderfulChickenInventoryHolder holder)) return;
        for (int slot : event.getRawSlots()) {
            if (slot < 9) {
                event.setCancelled(true);
                return;
            }
            if (slot < top.getSize() && ItemUtil.isShulkerBox(event.getOldCursor())) {
                event.setCancelled(true);
                return;
            }
        }
        Entity entity = Bukkit.getEntity(holder.chickenId());
        if (entity instanceof Chicken chicken) scheduleCargoSync(chicken, top);
    }

    public void handleClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof WonderfulChickenInventoryHolder holder)) return;
        Entity entity = Bukkit.getEntity(holder.chickenId());
        boolean skipCargoSync = skipCargoSyncOnClose.remove(event.getPlayer().getUniqueId());
        if (!skipCargoSync && entity instanceof Chicken chicken && store.isWonderful(chicken) && top.getSize() == 36) {
            syncCargo(chicken, top);
        }
        locks.remove(holder.chickenId(), event.getPlayer().getUniqueId());
    }

    public void handlePlayerExit(Player player) {
        Inventory top = player.getOpenInventory().getTopInventory();
        if (!(top.getHolder() instanceof WonderfulChickenInventoryHolder holder)) return;
        Entity entity = Bukkit.getEntity(holder.chickenId());
        boolean skipCargoSync = skipCargoSyncOnClose.remove(player.getUniqueId());
        if (!skipCargoSync && entity instanceof Chicken chicken && store.isWonderful(chicken) && top.getSize() == 36) {
            syncCargo(chicken, top);
        }
        locks.remove(holder.chickenId(), player.getUniqueId());
    }

    public void closeFor(Chicken chicken) {
        UUID viewerId = locks.remove(chicken.getUniqueId());
        if (viewerId == null) return;
        Player viewer = Bukkit.getPlayer(viewerId);
        if (viewer != null && viewer.getOpenInventory().getTopInventory().getHolder() instanceof WonderfulChickenInventoryHolder) viewer.closeInventory();
    }

    public void closeAll() {
        for (UUID viewerId : List.copyOf(locks.values())) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null && viewer.getOpenInventory().getTopInventory().getHolder() instanceof WonderfulChickenInventoryHolder) {
                viewer.closeInventory();
            }
        }
        locks.clear();
        skipCargoSyncOnClose.clear();
    }

    private boolean incomingShulkerBox(InventoryClickEvent event) {
        if (ItemUtil.isShulkerBox(event.getCursor())) return true;
        if (event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0
                && event.getWhoClicked() instanceof Player player) {
            return ItemUtil.isShulkerBox(player.getInventory().getItem(event.getHotbarButton()));
        }
        if (event.getClick() == ClickType.SWAP_OFFHAND && event.getWhoClicked() instanceof Player player) {
            return ItemUtil.isShulkerBox(player.getInventory().getItemInOffHand());
        }
        return false;
    }

    private void handleEquipmentClick(InventoryClickEvent event, Chicken chicken, int slot) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        WonderfulChickenData data = store.load(chicken);
        if (slot == SLOT_SHULKER && event.getView().getTopInventory().getSize() == 36) {
            syncCargo(chicken, event.getView().getTopInventory());
            data = store.load(chicken);
        }
        ItemStack cursor = player.getItemOnCursor();
        ItemStack current = switch (slot) {
            case SLOT_CARPET -> data.carpet();
            case SLOT_SHULKER -> data.shulkerBox();
            case SLOT_HEAD -> data.headItem();
            default -> null;
        };
        boolean cursorEmpty = cursor == null || cursor.isEmpty();
        if (!cursorEmpty && !validForSlot(slot, cursor)) {
            player.sendMessage(messages.text(player, "error.invalid_equipment"));
            return;
        }
        if (slot == SLOT_CARPET && !chicken.isAdult() && !cursorEmpty) {
            player.sendMessage(messages.text(player, "error.adult_only"));
            return;
        }
        ItemStack replacement = cursorEmpty ? null : cursor.asOne();
        switch (slot) {
            case SLOT_CARPET -> data.carpet(replacement);
            case SLOT_SHULKER -> data.shulkerBox(replacement);
            case SLOT_HEAD -> data.headItem(replacement);
            default -> { return; }
        }
        store.save(chicken, data);
        chickens.projectAttributes(chicken);
        displays.rebuild(chicken);
        ItemStack nextCursor = completeDirectEquipmentSwap(player, chicken, cursor, cursorEmpty, current);
        if (slot == SLOT_CARPET && replacement == null) chicken.eject();
        if (slot == SLOT_SHULKER) {
            player.setItemOnCursor(ItemStack.empty());
            skipCargoSyncOnClose.add(player.getUniqueId());
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> {
                open(player, chicken);
                player.setItemOnCursor(nextCursor);
            });
        } else {
            player.setItemOnCursor(nextCursor);
            populateMeta(player, event.getView().getTopInventory(), data);
        }
    }


    private ItemStack completeDirectEquipmentSwap(Player player, Chicken chicken, ItemStack cursor, boolean cursorEmpty, ItemStack previous) {
        if (cursorEmpty || cursor.getAmount() == 1) {
            return previous == null ? ItemStack.empty() : previous;
        }

        ItemStack remainder = cursor.clone();
        remainder.setAmount(cursor.getAmount() - 1);
        if (previous != null && !previous.isEmpty()) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(previous);
            leftovers.values().forEach(item -> chicken.getWorld().dropItemNaturally(chicken.getLocation(), item));
        }
        return remainder;
    }

    private void handleBehaviorClick(InventoryClickEvent event, Chicken chicken) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        WonderfulChickenData data = store.load(chicken);
        BehaviorMode next = data.behaviorMode().next();
        data.behaviorMode(next);
        if (next == BehaviorMode.FOLLOW) data.followTarget(player.getUniqueId());
        else if (next != BehaviorMode.FOLLOW) data.followTarget(null);
        store.save(chicken, data);
        chickens.synchronizeBehaviorState(chicken, data);
        populateMeta(player, event.getView().getTopInventory(), data);
    }

    private void populateMeta(Player viewer, Inventory inventory, WonderfulChickenData data) {
        inventory.setItem(SLOT_CARPET, data.carpet() == null ? placeholder(Material.WHITE_STAINED_GLASS_PANE, messages.text(viewer.locale(), "gui.slot_carpet")) : data.carpet());
        inventory.setItem(SLOT_SHULKER, data.shulkerBox() == null ? placeholder(Material.WHITE_STAINED_GLASS_PANE, messages.text(viewer.locale(), "gui.slot_shulker")) : data.shulkerBox());
        inventory.setItem(SLOT_HEAD, data.headItem() == null ? placeholder(Material.WHITE_STAINED_GLASS_PANE, messages.text(viewer.locale(), "gui.slot_head")) : data.headItem());
        for (int slot = 3; slot <= 5; slot++) inventory.setItem(slot, placeholder(Material.BLACK_STAINED_GLASS_PANE, messages.text(viewer.locale(), "gui.reserved")));
        inventory.setItem(SLOT_BEHAVIOR, behaviorItem(viewer, data));
        inventory.setItem(SLOT_INFO, infoItem(viewer, data));
        inventory.setItem(SLOT_PEDIGREE, pedigreeItem(viewer, data));
    }

    private ItemStack behaviorItem(Player viewer, WonderfulChickenData data) {
        Material material = switch (data.behaviorMode()) {
            case WANDER -> Material.FEATHER;
            case WAIT -> Material.OAK_FENCE;
            case FOLLOW -> Material.LEAD;
        };
        String mode = messages.text(viewer.locale(), "behavior." + data.behaviorMode().name().toLowerCase(Locale.ROOT));
        ItemStack item = placeholder(material, messages.text(viewer.locale(), "gui.behavior", mode));
        if (data.behaviorMode() == BehaviorMode.FOLLOW && data.followTarget() != null) {
            ItemMeta meta = item.getItemMeta();
            String targetName = Bukkit.getOfflinePlayer(data.followTarget()).getName();
            meta.lore(List.of(Component.text(messages.text(viewer.locale(), "gui.follow_target", targetName == null ? data.followTarget() : targetName))));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack infoItem(Player viewer, WonderfulChickenData data) {
        ItemStack item = placeholder(Material.PAPER, messages.text(viewer.locale(), "gui.info"));
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>();
        for (StatType stat : StatType.values()) {
            String display = config.statDisplay(stat);
            if (display.equals("none")) continue;
            String label = messages.text(viewer.locale(), "stat." + stat.key());
            String value = formatValue(stat, data.value(stat));
            String rank = messages.rank(viewer.locale(), Rank.fromNormalized(data.normalized(stat)).key());
            String line = switch (display) {
                case "value" -> label + ": " + value;
                case "rank" -> label + ": " + rank;
                default -> label + ": " + value + " (" + rank + ")";
            };
            lore.add(Component.text(line));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack pedigreeItem(Player viewer, WonderfulChickenData data) {
        ItemStack item = placeholder(Material.WRITABLE_BOOK, messages.text(viewer.locale(), "gui.pedigree"));
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(messages.text(viewer.locale(), "gui.pedigree_id", chickens.displayBloodlineId(data.bloodlineId()))));
        lore.add(Component.text(messages.text(viewer.locale(), "gui.generation", data.generation())));
        PedigreeData p = data.pedigree();
        addAncestor(lore, viewer, "gui.parent_a", p.parentA());
        addAncestor(lore, viewer, "gui.parent_b", p.parentB());
        addAncestor(lore, viewer, "gui.grandparent_aa", p.grandparentAA());
        addAncestor(lore, viewer, "gui.grandparent_ab", p.grandparentAB());
        addAncestor(lore, viewer, "gui.grandparent_ba", p.grandparentBA());
        addAncestor(lore, viewer, "gui.grandparent_bb", p.grandparentBB());
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void sendDetailedInfo(Player player, Chicken chicken, boolean pedigreeOnly) {
        WonderfulChickenData data = store.load(chicken);
        player.sendMessage(messages.text(player, "command.header"));
        player.sendMessage("UUID: " + chicken.getUniqueId());
        player.sendMessage("Bloodline: " + chickens.displayBloodlineId(data.bloodlineId()) + " / Generation: " + data.generation());
        if (!pedigreeOnly) {
            for (StatType stat : StatType.values()) player.sendMessage(stat.commandName() + "=" + formatValue(stat, data.value(stat)) + " normalized=" + String.format(Locale.ROOT, "%.4f", data.normalized(stat)));
        } else {
            PedigreeData p = data.pedigree();
            player.sendMessage(messages.text(player.locale(), "gui.parent_a") + ": " + ancestorText(player, p.parentA()));
            player.sendMessage(messages.text(player.locale(), "gui.parent_b") + ": " + ancestorText(player, p.parentB()));
            player.sendMessage(messages.text(player.locale(), "gui.grandparent_aa") + ": " + ancestorText(player, p.grandparentAA()));
            player.sendMessage(messages.text(player.locale(), "gui.grandparent_ab") + ": " + ancestorText(player, p.grandparentAB()));
            player.sendMessage(messages.text(player.locale(), "gui.grandparent_ba") + ": " + ancestorText(player, p.grandparentBA()));
            player.sendMessage(messages.text(player.locale(), "gui.grandparent_bb") + ": " + ancestorText(player, p.grandparentBB()));
        }
    }

    private void loadCargo(Inventory inventory, ItemStack shulker) {
        ItemStack[] contents = getShulkerContents(shulker);
        for (int i = 0; i < 27; i++) inventory.setItem(9 + i, contents[i]);
    }

    private void syncCargo(Chicken chicken, Inventory inventory) {
        WonderfulChickenData data = store.load(chicken);
        ItemStack shulker = data.shulkerBox();
        if (shulker == null || inventory.getSize() != 36) return;
        ItemStack[] contents = new ItemStack[27];
        for (int i = 0; i < 27; i++) {
            ItemStack item = inventory.getItem(9 + i);
            contents[i] = item == null || item.isEmpty() ? null : item.clone();
        }
        data.shulkerBox(withShulkerContents(shulker, contents));
        store.save(chicken, data);
        displays.rebuild(chicken);
    }

    private void scheduleCargoSync(Chicken chicken, Inventory inventory) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (chicken.isValid() && store.isWonderful(chicken) && inventory.getSize() == 36) syncCargo(chicken, inventory);
        });
    }

    private void moveToCargo(Inventory top, ItemStack moving, InventoryClickEvent event) {
        int remaining = moving.getAmount();
        for (int slot = 9; slot < 36 && remaining > 0; slot++) {
            ItemStack current = top.getItem(slot);
            if (current != null && current.isSimilar(moving) && current.getAmount() < current.getMaxStackSize()) {
                int add = Math.min(remaining, current.getMaxStackSize() - current.getAmount());
                current.setAmount(current.getAmount() + add);
                remaining -= add;
            }
        }
        for (int slot = 9; slot < 36 && remaining > 0; slot++) {
            ItemStack current = top.getItem(slot);
            if (current == null || current.isEmpty()) {
                int add = Math.min(remaining, moving.getMaxStackSize());
                ItemStack placed = moving.clone();
                placed.setAmount(add);
                top.setItem(slot, placed);
                remaining -= add;
            }
        }
        moving.setAmount(remaining);
        if (remaining <= 0) event.setCurrentItem(null);
    }

    private ItemStack[] getShulkerContents(ItemStack item) {
        if (item.getItemMeta() instanceof BlockStateMeta meta && meta.getBlockState() instanceof ShulkerBox shulker) return shulker.getInventory().getContents();
        return new ItemStack[27];
    }

    private ItemStack withShulkerContents(ItemStack item, ItemStack[] contents) {
        ItemStack copy = item.clone();
        if (copy.getItemMeta() instanceof BlockStateMeta meta && meta.getBlockState() instanceof ShulkerBox shulker) {
            shulker.getInventory().setContents(Arrays.copyOf(contents, 27));
            meta.setBlockState(shulker);
            copy.setItemMeta(meta);
            return copy;
        }
        return copy;
    }

    private boolean validForSlot(int slot, ItemStack item) {
        return switch (slot) {
            case SLOT_CARPET -> ItemUtil.isCarpet(item);
            case SLOT_SHULKER -> ItemUtil.isShulkerBox(item);
            case SLOT_HEAD -> ItemUtil.isHeadEquippable(item);
            default -> false;
        };
    }

    private ItemStack placeholder(Material material, String name) {
        ItemStack item = ItemStack.of(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        item.setItemMeta(meta);
        return item;
    }

    private String formatValue(StatType stat, double value) {
        return switch (stat) {
            case MAX_HEALTH -> String.format(Locale.ROOT, "%.0f HP", value);
            case SIZE -> String.format(Locale.ROOT, "%.2f m", value * 0.7);
            case GROUND_SPEED, AIR_SPEED, ASCENT_SPEED -> String.format(Locale.ROOT, "%.2f blocks/s", value);
            case JUMP_STRENGTH, STEP_HEIGHT -> String.format(Locale.ROOT, "%.2f blocks", value);
            case STAMINA_RECOVERY -> String.format(Locale.ROOT, "%.2f/s", value);
            default -> String.format(Locale.ROOT, "%.2f", value);
        };
    }

    private void addAncestor(List<Component> lore, Player viewer, String labelKey, AncestorSnapshot snapshot) {
        String label = messages.text(viewer.locale(), labelKey);
        lore.add(Component.text(label + ": " + ancestorText(viewer, snapshot)));
    }

    private String ancestorText(Player viewer, AncestorSnapshot snapshot) {
        if (snapshot == null) return messages.text(viewer.locale(), "gui.ancestor_none");
        String name = snapshot.name();
        if (name == null || name.isBlank() || name.equals("無名の鶏")) {
            name = messages.text(viewer.locale(), "gui.ancestor_unnamed");
        }
        return messages.text(viewer.locale(), "gui.ancestor_value",
                name, snapshot.generation(), chickens.displayBloodlineId(snapshot.bloodlineId()));
    }
}