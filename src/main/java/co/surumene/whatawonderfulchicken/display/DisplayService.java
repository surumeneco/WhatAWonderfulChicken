package co.surumene.whatawonderfulchicken.display;

import co.surumene.whatawonderfulchicken.WhatAWonderfulChickenPlugin;
import co.surumene.whatawonderfulchicken.compat.BedrockCompatibility;
import co.surumene.whatawonderfulchicken.data.StatType;
import co.surumene.whatawonderfulchicken.data.WonderfulChickenData;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenService;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenStore;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
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
    private final BedrockCompatibility bedrock;
    private final NamespacedKey ownerKey;
    private final NamespacedKey roleKey;
    private final Map<DisplayKey, UUID> javaDisplays = new HashMap<>();
    private final Map<DisplayKey, UUID> bedrockDisplays = new HashMap<>();

    public DisplayService(
            WhatAWonderfulChickenPlugin plugin,
            WonderfulChickenService chickens,
            WonderfulChickenStore store,
            BedrockCompatibility bedrock
    ) {
        this.plugin = plugin;
        this.chickens = chickens;
        this.store = store;
        this.bedrock = bedrock;
        this.ownerKey = new NamespacedKey(plugin, "display_owner");
        this.roleKey = new NamespacedKey(plugin, "display_role");
    }

    public void rebuildAllLoaded() {
        cleanupAndIndexExisting();
        for (Chicken chicken : chickens.loadedChickens()) rebuild(chicken);
        refreshVisibility();
    }

    public void reconcileChunk(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (!(entity instanceof ArmorStand) && !(entity instanceof FallingBlock)
                    && !(entity instanceof Shulker)) continue;
            DisplayKey key = displayKey(entity);
            if (key == null) continue;

            Entity owner = Bukkit.getEntity(key.owner());
            if (!(owner instanceof Chicken chicken) || !store.isWonderful(chicken)) {
                unregisterAndRemove(entity);
                continue;
            }

            if (entity instanceof ArmorStand stand) {
                UUID current = javaDisplays.get(key);
                Entity currentEntity = current == null ? null : Bukkit.getEntity(current);
                if (currentEntity == null || !currentEntity.isValid()) {
                    stand.setPersistent(false);
                    javaDisplays.put(key, stand.getUniqueId());
                    bedrock.registerJavaOnlyDisplay(stand.getUniqueId());
                } else if (!current.equals(stand.getUniqueId())) {
                    unregisterAndRemove(stand);
                }
            } else {
                // Also remove Shulker Box FallingBlocks left by the older Bedrock implementation.
                if (!bedrock.enabled() || !isCorrectBedrockDisplay(entity, key.role())) {
                    unregisterAndRemove(entity);
                    continue;
                }
                UUID current = bedrockDisplays.get(key);
                Entity currentEntity = current == null ? null : Bukkit.getEntity(current);
                if (currentEntity == null || !currentEntity.isValid()) {
                    configureBedrockDisplay(entity);
                    bedrockDisplays.put(key, entity.getUniqueId());
                    bedrock.registerBedrockDisplay(entity.getUniqueId(), bedrockScale(chicken, key.role()));
                    syncBedrockVisibility(entity);
                } else if (!current.equals(entity.getUniqueId())) {
                    unregisterAndRemove(entity);
                }
            }
        }
    }

    public void rebuild(Chicken chicken) {
        if (!store.isWonderful(chicken)) return;
        WonderfulChickenData data = store.load(chicken);
        syncRole(chicken, DisplayRole.CARPET, data.carpet());
        syncRole(chicken, DisplayRole.SHULKER_BOX, data.shulkerBox());
    }

    public void removeFor(Chicken chicken) {
        for (DisplayRole role : DisplayRole.values()) remove(chicken.getUniqueId(), role);
    }

    public void removeAll() {
        for (UUID displayId : javaDisplays.values()) {
            bedrock.unregisterJavaOnlyDisplay(displayId);
            Entity entity = Bukkit.getEntity(displayId);
            if (entity != null) entity.remove();
        }
        for (UUID displayId : bedrockDisplays.values()) {
            bedrock.unregisterBedrockDisplay(displayId);
            Entity entity = Bukkit.getEntity(displayId);
            if (entity != null) entity.remove();
        }
        javaDisplays.clear();
        bedrockDisplays.clear();
    }

    public void tick() {
        tickJavaDisplays();
        tickBedrockDisplays();
    }

    public void refreshVisibility() {
        if (!bedrock.enabled()) return;
        for (UUID displayId : bedrockDisplays.values()) {
            Entity entity = Bukkit.getEntity(displayId);
            if (entity != null && entity.isValid()) {
                syncBedrockVisibility(entity);
            }
        }
    }

    /** Suppress block placement by the Carpet display when it contacts a solid block. */
    public boolean isBedrockCarpetDisplay(Entity entity) {
        return entity instanceof FallingBlock
                && entity.getPersistentDataContainer().has(ownerKey, PersistentDataType.STRING)
                && DisplayRole.CARPET.name().equals(
                        entity.getPersistentDataContainer().get(roleKey, PersistentDataType.STRING));
    }

    private void tickJavaDisplays() {
        Iterator<Map.Entry<DisplayKey, UUID>> iterator = javaDisplays.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<DisplayKey, UUID> entry = iterator.next();
            Entity owner = Bukkit.getEntity(entry.getKey().owner());
            Entity display = Bukkit.getEntity(entry.getValue());
            if (!(owner instanceof Chicken chicken) || !store.isWonderful(chicken)
                    || !(display instanceof ArmorStand stand) || !stand.isValid()) {
                if (display != null) display.remove();
                bedrock.unregisterJavaOnlyDisplay(entry.getValue());
                iterator.remove();
                continue;
            }
            stand.teleport(javaTargetLocation(chicken, entry.getKey().role()));
            stand.setBodyYaw(chicken.getBodyYaw());
            AttributeInstance scale = stand.getAttribute(Attribute.SCALE);
            if (scale != null) {
                scale.setBaseValue(bedrockScale(chicken, entry.getKey().role()));
            }
        }
    }

    private void tickBedrockDisplays() {
        if (!bedrock.enabled()) {
            if (!bedrockDisplays.isEmpty()) {
                for (UUID displayId : bedrockDisplays.values()) {
                    Entity entity = Bukkit.getEntity(displayId);
                    if (entity != null) entity.remove();
                }
                bedrockDisplays.clear();
            }
            return;
        }

        Iterator<Map.Entry<DisplayKey, UUID>> iterator = bedrockDisplays.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<DisplayKey, UUID> entry = iterator.next();
            Entity owner = Bukkit.getEntity(entry.getKey().owner());
            Entity display = Bukkit.getEntity(entry.getValue());
            if (!(owner instanceof Chicken chicken) || !store.isWonderful(chicken)
                    || display == null || !display.isValid()
                    || !isCorrectBedrockDisplay(display, entry.getKey().role())) {
                if (display != null) display.remove();
                bedrock.unregisterBedrockDisplay(entry.getValue());
                iterator.remove();
                continue;
            }

            display.teleport(bedrockTargetLocation(chicken, entry.getKey().role()));
            if (display instanceof FallingBlock block) {
                block.setVelocity(new Vector());
                block.setTicksLived(1);
            }
            bedrock.updateBedrockDisplayScale(display.getUniqueId(), bedrockScale(chicken, entry.getKey().role()));
        }
    }

    private void syncRole(Chicken chicken, DisplayRole role, ItemStack item) {
        if (item == null || item.isEmpty()) {
            remove(chicken.getUniqueId(), role);
            return;
        }

        syncJavaRole(chicken, role, item);
        if (bedrock.enabled()) {
            syncBedrockRole(chicken, role, item);
        } else {
            removeBedrock(chicken.getUniqueId(), role);
        }
    }

    private void syncJavaRole(Chicken chicken, DisplayRole role, ItemStack item) {
        DisplayKey key = new DisplayKey(chicken.getUniqueId(), role);
        ArmorStand stand = null;
        UUID existing = javaDisplays.get(key);
        if (existing != null && Bukkit.getEntity(existing) instanceof ArmorStand candidate && candidate.isValid()) {
            stand = candidate;
        }
        if (stand == null) {
            stand = chicken.getWorld().spawn(javaTargetLocation(chicken, role), ArmorStand.class, spawned -> {
                spawned.setInvisible(true);
                spawned.setMarker(true);
                spawned.setSmall(true);
                spawned.setGravity(false);
                spawned.setPersistent(false);
                spawned.setInvulnerable(true);
                spawned.setSilent(true);
                spawned.setBasePlate(false);
                spawned.setArms(false);
                tag(spawned, chicken, role);
                bedrock.registerJavaOnlyDisplay(spawned.getUniqueId());
            });
            javaDisplays.put(key, stand.getUniqueId());
        }
        if (stand.getEquipment() != null) stand.getEquipment().setHelmet(item.asOne());
    }

    private void syncBedrockRole(Chicken chicken, DisplayRole role, ItemStack item) {
        DisplayKey key = new DisplayKey(chicken.getUniqueId(), role);
        Entity display = null;
        UUID existing = bedrockDisplays.get(key);
        if (existing != null) {
            Entity candidate = Bukkit.getEntity(existing);
            if (role == DisplayRole.CARPET && candidate instanceof FallingBlock block && block.isValid()
                    && block.getBlockData().getMaterial() == item.getType()) {
                display = block;
            } else if (role == DisplayRole.SHULKER_BOX && candidate instanceof Shulker shulker
                    && shulker.isValid()) {
                shulker.setColor(shulkerColor(item.getType()));
                display = shulker;
            } else {
                removeBedrock(chicken.getUniqueId(), role);
            }
        }

        if (display == null) {
            if (role == DisplayRole.CARPET) {
                FallingBlock block = chicken.getWorld().spawnFallingBlock(
                        bedrockTargetLocation(chicken, role),
                        item.getType().createBlockData());
                configureBedrockDisplay(block);
                tag(block, chicken, role);
                display = block;
            } else {
                Shulker shulker = chicken.getWorld().spawn(
                        bedrockTargetLocation(chicken, role), Shulker.class, spawned -> {
                            configureBedrockDisplay(spawned);
                            spawned.setColor(shulkerColor(item.getType()));
                            tag(spawned, chicken, role);
                        });
                display = shulker;
            }
            bedrockDisplays.put(key, display.getUniqueId());
            bedrock.registerBedrockDisplay(display.getUniqueId(), bedrockScale(chicken, role));
        }
        syncBedrockVisibility(display);
    }

    private void configureBedrockDisplay(Entity display) {
        if (display instanceof FallingBlock block) {
            // Geyser translates no-gravity FallingBlocks to Bedrock NO_AI, which freezes client movement.
            block.setGravity(true);
            block.setDropItem(false);
            block.setCancelDrop(true);
            block.setHurtEntities(false);
            block.shouldAutoExpire(false);
            block.setVelocity(new Vector());
            block.setTicksLived(1);
        } else if (display instanceof Shulker shulker) {
            shulker.setPeek(0.0f);
            shulker.setAware(false);
            shulker.setGravity(false);
            shulker.setCollidable(false);
            shulker.setRemoveWhenFarAway(false);
        }
        display.setPersistent(false);
        display.setInvulnerable(true);
        display.setSilent(true);
    }

    private boolean isCorrectBedrockDisplay(Entity display, DisplayRole role) {
        return role == DisplayRole.CARPET ? display instanceof FallingBlock : display instanceof Shulker;
    }

    private DyeColor shulkerColor(Material material) {
        if (material == Material.SHULKER_BOX) return null;
        String name = material.name();
        return DyeColor.valueOf(name.substring(0, name.length() - "_SHULKER_BOX".length()));
    }

    private void syncBedrockVisibility(Entity display) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (bedrock.isBedrockPlayer(player.getUniqueId())) {
                player.showEntity(plugin, display);
            } else {
                player.hideEntity(plugin, display);
            }
        }
    }

    private void tag(Entity entity, Chicken chicken, DisplayRole role) {
        entity.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, chicken.getUniqueId().toString());
        entity.getPersistentDataContainer().set(roleKey, PersistentDataType.STRING, role.name());
    }

    private DisplayKey displayKey(Entity entity) {
        String ownerRaw = entity.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        String roleRaw = entity.getPersistentDataContainer().get(roleKey, PersistentDataType.STRING);
        if (ownerRaw == null || roleRaw == null) return null;
        try {
            return new DisplayKey(UUID.fromString(ownerRaw), DisplayRole.valueOf(roleRaw));
        } catch (IllegalArgumentException ex) {
            unregisterAndRemove(entity);
            return null;
        }
    }

    private Location javaTargetLocation(Chicken chicken, DisplayRole role) {
        double scale = store.load(chicken).value(StatType.SIZE);
        Location base = horizontalBase(chicken, role, scale);

        double standScale = Math.max(0.2, scale * displayScaleFactor(role));
        double helmetAnchorHeight = 0.90 * standScale;
        double desiredY = scale * targetYFactor(role);
        base.add(0, desiredY - helmetAnchorHeight, 0);
        return base;
    }

    private Location bedrockTargetLocation(Chicken chicken, DisplayRole role) {
        double scale = store.load(chicken).value(StatType.SIZE);
        Location base = horizontalBase(chicken, role, scale);
        double desiredY = scale * targetYFactor(role);
        if (role == DisplayRole.SHULKER_BOX) {
            desiredY -= bedrockScale(chicken, role) * 0.5;
        }
        base.add(0, desiredY, 0);
        return base;
    }

    private Location horizontalBase(Chicken chicken, DisplayRole role, double scale) {
        Location base = chicken.getLocation().clone();
        float bodyYaw = chicken.getBodyYaw();
        double yaw = Math.toRadians(bodyYaw);
        Vector backward = new Vector(Math.sin(yaw), 0, -Math.cos(yaw));
        if (role == DisplayRole.SHULKER_BOX) base.add(backward.multiply(0.22 * scale));
        base.setYaw(bodyYaw);
        base.setPitch(0.0f);
        return base;
    }

    private double targetYFactor(DisplayRole role) {
        return role == DisplayRole.CARPET ? CARPET_TARGET_Y_FACTOR : SHULKER_TARGET_Y_FACTOR;
    }

    private float bedrockScale(Chicken chicken, DisplayRole role) {
        double chickenScale = store.load(chicken).value(StatType.SIZE);
        return (float) Math.max(0.2, chickenScale * displayScaleFactor(role));
    }

    private double displayScaleFactor(DisplayRole role) {
        return role == DisplayRole.CARPET ? CARPET_SCALE_FACTOR : SHULKER_SCALE_FACTOR;
    }

    private void remove(UUID owner, DisplayRole role) {
        removeJava(owner, role);
        removeBedrock(owner, role);
    }

    private void removeJava(UUID owner, DisplayRole role) {
        UUID display = javaDisplays.remove(new DisplayKey(owner, role));
        if (display != null) {
            bedrock.unregisterJavaOnlyDisplay(display);
            Entity entity = Bukkit.getEntity(display);
            if (entity != null) entity.remove();
        }
    }

    private void removeBedrock(UUID owner, DisplayRole role) {
        UUID display = bedrockDisplays.remove(new DisplayKey(owner, role));
        if (display != null) {
            bedrock.unregisterBedrockDisplay(display);
            Entity entity = Bukkit.getEntity(display);
            if (entity != null) entity.remove();
        }
    }

    private void unregisterAndRemove(Entity entity) {
        bedrock.unregisterJavaOnlyDisplay(entity.getUniqueId());
        bedrock.unregisterBedrockDisplay(entity.getUniqueId());
        entity.remove();
    }

    private void cleanupAndIndexExisting() {
        javaDisplays.clear();
        bedrockDisplays.clear();
        Map<DisplayKey, ArmorStand> firstJava = new HashMap<>();
        Map<DisplayKey, Entity> firstBedrock = new HashMap<>();

        Bukkit.getWorlds().forEach(world -> world.getEntities().forEach(entity -> {
            if (!(entity instanceof ArmorStand) && !(entity instanceof FallingBlock)
                    && !(entity instanceof Shulker)) return;
            DisplayKey key = displayKey(entity);
            if (key == null) return;

            Entity owner = Bukkit.getEntity(key.owner());
            if (!(owner instanceof Chicken chicken) || !store.isWonderful(chicken)) {
                unregisterAndRemove(entity);
                return;
            }

            if (entity instanceof ArmorStand stand) {
                ArmorStand prior = firstJava.putIfAbsent(key, stand);
                if (prior != null) {
                    unregisterAndRemove(stand);
                } else {
                    stand.setPersistent(false);
                    bedrock.registerJavaOnlyDisplay(stand.getUniqueId());
                }
            } else {
                if (!bedrock.enabled() || !isCorrectBedrockDisplay(entity, key.role())) {
                    unregisterAndRemove(entity);
                    return;
                }
                Entity prior = firstBedrock.putIfAbsent(key, entity);
                if (prior != null) {
                    unregisterAndRemove(entity);
                } else {
                    configureBedrockDisplay(entity);
                    bedrock.registerBedrockDisplay(entity.getUniqueId(), bedrockScale(chicken, key.role()));
                    syncBedrockVisibility(entity);
                }
            }
        }));

        firstJava.forEach((key, stand) -> javaDisplays.put(key, stand.getUniqueId()));
        firstBedrock.forEach((key, display) -> bedrockDisplays.put(key, display.getUniqueId()));
    }

    private record DisplayKey(UUID owner, DisplayRole role) {}
}
