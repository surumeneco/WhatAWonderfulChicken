package co.surumene.whatawonderfulchicken.display;

import co.surumene.whatawonderfulchicken.WhatAWonderfulChickenPlugin;
import co.surumene.whatawonderfulchicken.data.StatType;
import co.surumene.whatawonderfulchicken.data.WonderfulChickenData;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenService;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenStore;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class DisplayService {
    private static final double CARPET_SCALE_FACTOR = 0.72;
    private static final double SHULKER_SCALE_FACTOR = 0.63;
    private static final double CARPET_TARGET_Y_FACTOR = 0.85;
    private static final double SHULKER_TARGET_Y_FACTOR = 0.65;

    private final WhatAWonderfulChickenPlugin plugin;
    private final WonderfulChickenService chickens;
    private final WonderfulChickenStore store;
    private final NamespacedKey ownerKey;
    private final NamespacedKey roleKey;
    private final Map<DisplayKey, UUID> displays = new HashMap<>();
    private final DisplayRemovalQueue removals = new DisplayRemovalQueue();

    public DisplayService(WhatAWonderfulChickenPlugin plugin, WonderfulChickenService chickens, WonderfulChickenStore store) {
        this.plugin = plugin;
        this.chickens = chickens;
        this.store = store;
        this.ownerKey = new NamespacedKey(plugin, "display_owner");
        this.roleKey = new NamespacedKey(plugin, "display_role");
    }

    public void rebuildAllLoaded() {
        cleanupAndIndexExisting();
        for (Chicken chicken : chickens.loadedChickens()) rebuild(chicken);
    }

    public void reconcileChunk(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (!(entity instanceof ArmorStand stand)) continue;
            if (removals.contains(stand.getUniqueId())) continue;
            String ownerRaw = stand.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
            String roleRaw = stand.getPersistentDataContainer().get(roleKey, PersistentDataType.STRING);
            if (ownerRaw == null || roleRaw == null) continue;
            try {
                DisplayKey key = new DisplayKey(UUID.fromString(ownerRaw), DisplayRole.valueOf(roleRaw));
                Entity owner = Bukkit.getEntity(key.owner());
                if (!(owner instanceof Chicken chicken) || !store.isWonderful(chicken)) {
                    removals.queue(stand.getUniqueId());
                    continue;
                }
                UUID current = displays.get(key);
                Entity currentEntity = current == null ? null : Bukkit.getEntity(current);
                if (currentEntity == null || !currentEntity.isValid()) {
                    stand.setPersistent(false);
                    displays.put(key, stand.getUniqueId());
                } else if (!current.equals(stand.getUniqueId())) {
                    removals.queue(stand.getUniqueId());
                }
            } catch (IllegalArgumentException ex) {
                removals.queue(stand.getUniqueId());
            }
        }
    }

    public void rebuild(Chicken chicken) {
        if (!store.isWonderful(chicken)) return;
        WonderfulChickenData data = store.read(chicken);
        syncRole(chicken, DisplayRole.CARPET, data.carpet());
        syncRole(chicken, DisplayRole.SHULKER_BOX, data.shulkerBox());
    }

    public void removeFor(Chicken chicken) {
        for (DisplayRole role : DisplayRole.values()) remove(chicken.getUniqueId(), role);
    }

    public void removeAll() {
        displays.values().forEach(removals::queue);
        displays.clear();
        // The periodic tick task no longer runs after plugin disable.
        flushPendingRemovals();
    }

    public void tick() {
        flushPendingRemovals();
        Iterator<Map.Entry<DisplayKey, UUID>> iterator = displays.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<DisplayKey, UUID> entry = iterator.next();
            Entity owner = Bukkit.getEntity(entry.getKey().owner());
            Entity display = Bukkit.getEntity(entry.getValue());
            if (!(owner instanceof Chicken chicken) || !store.isWonderful(chicken) || !(display instanceof ArmorStand stand) || !stand.isValid()) {
                if (display != null) removals.queue(display.getUniqueId());
                iterator.remove();
                continue;
            }
            Location destination = targetLocation(chicken, entry.getKey().role());
            Location current = stand.getLocation();
            if (current.getX() != destination.getX() || current.getY() != destination.getY()
                    || current.getZ() != destination.getZ() || current.getYaw() != destination.getYaw()) {
                stand.teleport(destination);
            }
            if (Float.compare(stand.getBodyYaw(), chicken.getBodyYaw()) != 0) {
                stand.setBodyYaw(chicken.getBodyYaw());
            }
            AttributeInstance scale = stand.getAttribute(Attribute.SCALE);
            if (scale != null) {
                double chickenScale = store.read(chicken).value(StatType.SIZE);
                double intended = Math.max(0.2, chickenScale * displayScaleFactor(entry.getKey().role()));
                if (Double.compare(scale.getBaseValue(), intended) != 0) scale.setBaseValue(intended);
            }
        }
    }

    private void syncRole(Chicken chicken, DisplayRole role, ItemStack item) {
        if (item == null || item.isEmpty()) {
            remove(chicken.getUniqueId(), role);
            return;
        }
        DisplayKey key = new DisplayKey(chicken.getUniqueId(), role);
        ArmorStand stand = null;
        UUID existing = displays.get(key);
        if (existing != null && Bukkit.getEntity(existing) instanceof ArmorStand candidate && candidate.isValid()) stand = candidate;
        if (stand == null) {
            stand = chicken.getWorld().spawn(targetLocation(chicken, role), ArmorStand.class, spawned -> {
                spawned.setInvisible(true);
                spawned.setMarker(true);
                spawned.setSmall(true);
                spawned.setGravity(false);
                spawned.setPersistent(false);
                spawned.setInvulnerable(true);
                spawned.setSilent(true);
                spawned.setBasePlate(false);
                spawned.setArms(false);
                spawned.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, chicken.getUniqueId().toString());
                spawned.getPersistentDataContainer().set(roleKey, PersistentDataType.STRING, role.name());
            });
            displays.put(key, stand.getUniqueId());
        }
        if (stand.getEquipment() != null) {
            ItemStack shown = item.asOne();
            if (!shown.equals(stand.getEquipment().getHelmet())) stand.getEquipment().setHelmet(shown);
        }
    }

    private Location targetLocation(Chicken chicken, DisplayRole role) {
        WonderfulChickenData data = store.read(chicken);
        double scale = data.value(StatType.SIZE);
        Location base = chicken.getLocation().clone();
        float bodyYaw = chicken.getBodyYaw();
        double yaw = Math.toRadians(bodyYaw);
        Vector backward = new Vector(Math.sin(yaw), 0, -Math.cos(yaw));
        if (role == DisplayRole.SHULKER_BOX) base.add(backward.multiply(0.22 * scale));

        double standScale = Math.max(0.2, scale * displayScaleFactor(role));
        double helmetAnchorHeight = 0.90 * standScale;
        double desiredY = scale * (role == DisplayRole.CARPET
                ? CARPET_TARGET_Y_FACTOR
                : SHULKER_TARGET_Y_FACTOR);
        base.add(0, desiredY - helmetAnchorHeight, 0);
        base.setYaw(bodyYaw);
        base.setPitch(0.0f);
        return base;
    }

    private double displayScaleFactor(DisplayRole role) {
        return role == DisplayRole.CARPET ? CARPET_SCALE_FACTOR : SHULKER_SCALE_FACTOR;
    }

    private void remove(UUID owner, DisplayRole role) {
        UUID display = displays.remove(new DisplayKey(owner, role));
        if (display != null) removals.queue(display);
    }

    private void flushPendingRemovals() {
        removals.flush(displayId -> {
            Entity entity = Bukkit.getEntity(displayId);
            if (entity == null || !entity.isValid()) return true;
            entity.remove();
            return !entity.isValid();
        });
    }

    private void cleanupAndIndexExisting() {
        displays.clear();
        Map<DisplayKey, ArmorStand> first = new HashMap<>();
        Bukkit.getWorlds().forEach(world -> world.getEntitiesByClass(ArmorStand.class).forEach(stand -> {
            if (removals.contains(stand.getUniqueId())) return;
            String ownerRaw = stand.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
            String roleRaw = stand.getPersistentDataContainer().get(roleKey, PersistentDataType.STRING);
            if (ownerRaw == null || roleRaw == null) return;
            try {
                DisplayKey key = new DisplayKey(UUID.fromString(ownerRaw), DisplayRole.valueOf(roleRaw));
                Entity owner = Bukkit.getEntity(key.owner());
                if (!(owner instanceof Chicken chicken) || !store.isWonderful(chicken)) {
                    removals.queue(stand.getUniqueId());
                    return;
                }
                ArmorStand prior = first.putIfAbsent(key, stand);
                if (prior != null) removals.queue(stand.getUniqueId());
                else stand.setPersistent(false);
            } catch (IllegalArgumentException ex) {
                removals.queue(stand.getUniqueId());
            }
        }));
        first.forEach((key, stand) -> displays.put(key, stand.getUniqueId()));
    }

    private record DisplayKey(UUID owner, DisplayRole role) {}
}