package co.surumene.whatawonderfulchicken.task;

import co.surumene.whatawonderfulchicken.config.ConfigService;
import co.surumene.whatawonderfulchicken.data.StatType;
import co.surumene.whatawonderfulchicken.data.WonderfulChickenData;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenService;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenStore;
import org.bukkit.Bukkit;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RidingController implements Runnable {
    private final WonderfulChickenService chickens;
    private final WonderfulChickenStore store;
    private final ConfigService config;
    private final Map<UUID, UUID> mountedInteractionProxies = new HashMap<>();
    private final Map<UUID, UUID> mountedInteractionOwners = new HashMap<>();
    private final Map<UUID, Long> lastAirborneTick = new HashMap<>();
    private final Map<UUID, Boolean> roadCache = new HashMap<>();
    private final Map<UUID, UUID> mountedChickens = new HashMap<>();
    private final Map<UUID, Float> exhaustionAtMount = new HashMap<>();
    private final Set<UUID> airFlapLockedUntilJumpRelease = new HashSet<>();
    private long tick;

    public RidingController(WonderfulChickenService chickens, WonderfulChickenStore store, ConfigService config) {
        this.chickens = chickens;
        this.store = store;
        this.config = config;
    }

    @Override
    public void run() {
        tick++;
        Map<UUID, UUID> nowMounted = new HashMap<>();
        Set<UUID> mountedChickenIds = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!(player.getVehicle() instanceof Chicken chicken) || !store.isWonderful(chicken)) continue;
            UUID playerId = player.getUniqueId();
            UUID chickenId = chicken.getUniqueId();
            nowMounted.put(playerId, chickenId);
            mountedChickenIds.add(chickenId);
            exhaustionAtMount.putIfAbsent(playerId, player.getExhaustion());
            tickMounted(player, chicken);
        }
        for (Chicken chicken : chickens.loadedChickens()) {
            if (!mountedChickenIds.contains(chicken.getUniqueId())) tickUnmounted(chicken);
        }
        for (Map.Entry<UUID, UUID> entry : new HashMap<>(mountedChickens).entrySet()) {
            if (!nowMounted.containsKey(entry.getKey())) finishMount(entry.getKey(), entry.getValue());
        }
        mountedChickens.clear();
        mountedChickens.putAll(nowMounted);
        lastAirborneTick.keySet().removeIf(uuid -> Bukkit.getEntity(uuid) == null);
    }

    public void restoreHud(Player player) {
        player.sendExperienceChange(player.getExp(), player.getLevel());
    }

    public void shutdown() {
        for (Map.Entry<UUID, UUID> entry : new HashMap<>(mountedChickens).entrySet()) {
            finishMount(entry.getKey(), entry.getValue());
        }
        mountedChickens.clear();
        exhaustionAtMount.clear();
        airFlapLockedUntilJumpRelease.clear();
        removeAllInteractionProxies();
    }

    private void finishMount(UUID playerId, UUID chickenId) {
        roadCache.remove(chickenId);
        airFlapLockedUntilJumpRelease.remove(chickenId);
        removeInteractionProxy(chickenId);
        Player player = Bukkit.getPlayer(playerId);
        Float exhaustion = exhaustionAtMount.remove(playerId);
        if (player != null) {
            if (exhaustion != null) player.setExhaustion(exhaustion);
            restoreHud(player);
        }
        if (Bukkit.getEntity(chickenId) instanceof Chicken chicken && store.isWonderful(chicken)) {
            chickens.synchronizeBehaviorState(chicken, store.load(chicken));
        }
    }

    private void tickUnmounted(Chicken chicken) {
        UUID chickenId = chicken.getUniqueId();
        if (chicken.isInWater() || !isGrounded(chicken)) {
            lastAirborneTick.put(chickenId, tick);
            return;
        }
        WonderfulChickenData data = store.load(chicken);
        double maximum = data.value(StatType.STAMINA);
        if (data.currentStamina() >= maximum) return;
        long delay = Math.round(config.recoveryDelaySeconds() * 20.0);
        long lastAir = lastAirborneTick.getOrDefault(chickenId, Long.MIN_VALUE / 4);
        if (tick - lastAir < delay) return;
        data.currentStamina(Math.min(maximum,
                data.currentStamina() + data.value(StatType.STAMINA_RECOVERY) / 20.0));
        store.save(chicken, data);
    }

    private void tickMounted(Player player, Chicken chicken) {
        ensureInteractionProxy(chicken);
        WonderfulChickenData data = store.load(chicken);
        Input input = player.getCurrentInput();
        UUID chickenId = chicken.getUniqueId();
        boolean onGround = isGrounded(chicken);
        boolean inWater = chicken.isInWater();
        if (!input.isJump()) airFlapLockedUntilJumpRelease.remove(chickenId);
        if (!chicken.hasAI()) chicken.setAI(true);
        chicken.getPathfinder().stopPathfinding();
        chicken.setAware(inWater && data.behaviorMode() != co.surumene.whatawonderfulchicken.data.BehaviorMode.FOLLOW);
        chicken.setRotation(player.getLocation().getYaw(), chicken.getLocation().getPitch());

        Vector velocity = chicken.getVelocity();
        Vector horizontal = horizontalInput(player, input);
        double speed = (onGround ? data.value(StatType.GROUND_SPEED) : data.value(StatType.AIR_SPEED)) / 20.0;
        if (onGround && isRoad(chicken)) speed *= config.roadMultiplier();
        if (horizontal.lengthSquared() > 0.0) {
            horizontal.normalize().multiply(speed);
            velocity.setX(horizontal.getX()).setZ(horizontal.getZ());
        } else if (!inWater) {
            velocity.setX(0.0).setZ(0.0);
        }

        if (inWater) {
            lastAirborneTick.put(chicken.getUniqueId(), tick);
        } else if (onGround) {
            if (input.isJump() && !airFlapLockedUntilJumpRelease.contains(chickenId)) {
                velocity.setY(chickens.jumpVelocityForHeight(data.value(StatType.JUMP_STRENGTH)));
                airFlapLockedUntilJumpRelease.add(chickenId);
            }
            long delay = Math.round(config.recoveryDelaySeconds() * 20.0);
            long lastAir = lastAirborneTick.getOrDefault(chicken.getUniqueId(), Long.MIN_VALUE / 4);
            if (tick - lastAir >= delay) {
                data.currentStamina(Math.min(data.value(StatType.STAMINA), data.currentStamina() + data.value(StatType.STAMINA_RECOVERY) / 20.0));
            }
        } else {
            lastAirborneTick.put(chicken.getUniqueId(), tick);
            if (input.isJump() && !airFlapLockedUntilJumpRelease.contains(chickenId) && data.currentStamina() > 0.0) {
                velocity.setY(data.value(StatType.ASCENT_SPEED) / 20.0);
                data.currentStamina(Math.max(0.0, data.currentStamina() - config.staminaConsumptionPerSecond() / 20.0));
            } else if (velocity.getY() < -0.12) {
                velocity.setY(-0.12);
            }
        }

        chicken.setVelocity(velocity);
        Float baselineExhaustion = exhaustionAtMount.get(player.getUniqueId());
        if (baselineExhaustion != null) player.setExhaustion(baselineExhaustion);
        float progress = (float) Math.max(0.0, Math.min(1.0, data.currentStamina() / Math.max(0.0001, data.value(StatType.STAMINA))));
        player.sendExperienceChange(progress, player.getLevel());
        store.save(chicken, data);
    }

    private void ensureInteractionProxy(Chicken chicken) {
        UUID chickenId = chicken.getUniqueId();
        Interaction proxy = null;
        UUID proxyId = mountedInteractionProxies.get(chickenId);
        if (proxyId != null && Bukkit.getEntity(proxyId) instanceof Interaction existing && existing.isValid()) {
            proxy = existing;
        }
        if (proxy == null) {
            proxy = (Interaction) chicken.getWorld().spawnEntity(interactionLocation(chicken), EntityType.INTERACTION);
            proxy.setPersistent(false);
            mountedInteractionProxies.put(chickenId, proxy.getUniqueId());
            mountedInteractionOwners.put(proxy.getUniqueId(), chickenId);
        }
        BoundingBox box = chicken.getBoundingBox();
        proxy.teleport(interactionLocation(chicken));
        proxy.setInteractionWidth((float) Math.max(0.5, Math.max(box.getWidthX(), box.getWidthZ()) * 1.15));
        proxy.setInteractionHeight((float) Math.max(0.5, box.getHeight() * 1.10));
    }

    private Location interactionLocation(Chicken chicken) {
        BoundingBox box = chicken.getBoundingBox();
        return new Location(chicken.getWorld(), box.getCenterX(), box.getMinY(), box.getCenterZ(), chicken.getBodyYaw(), 0.0f);
    }

    public Chicken interactionOwner(Entity entity) {
        UUID chickenId = mountedInteractionOwners.get(entity.getUniqueId());
        if (chickenId == null) return null;
        Entity owner = Bukkit.getEntity(chickenId);
        return owner instanceof Chicken chicken && store.isWonderful(chicken) ? chicken : null;
    }

    private void removeInteractionProxy(UUID chickenId) {
        UUID proxyId = mountedInteractionProxies.remove(chickenId);
        if (proxyId == null) return;
        mountedInteractionOwners.remove(proxyId);
        Entity proxy = Bukkit.getEntity(proxyId);
        if (proxy != null) proxy.remove();
    }

    private void removeAllInteractionProxies() {
        UUID[] ids = mountedInteractionProxies.keySet().toArray(UUID[]::new);
        for (UUID chickenId : ids) removeInteractionProxy(chickenId);
        mountedInteractionOwners.clear();
    }

    private boolean isGrounded(Chicken chicken) {
        if (chicken.isOnGround()) return true;
        if (chicken.isInWater()) return false;
        BoundingBox probe = chicken.getBoundingBox().clone().shift(0.0, -0.08, 0.0);
        return chicken.getWorld().hasCollisionsIn(probe);
    }

    private Vector horizontalInput(Player player, Input input) {
        double yaw = Math.toRadians(player.getLocation().getYaw());
        Vector forward = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        Vector result = new Vector();
        if (input.isForward()) result.add(forward);
        if (input.isBackward()) result.subtract(forward);
        if (input.isRight()) result.add(right);
        if (input.isLeft()) result.subtract(right);
        return result;
    }

    private boolean isRoad(Chicken chicken) {
        int interval = Math.max(1, config.roadCheckIntervalTicks());
        if (tick % interval != 0 && roadCache.containsKey(chicken.getUniqueId())) return roadCache.get(chicken.getUniqueId());
        Material below = chicken.getLocation().clone().subtract(0, 2, 0).getBlock().getType();
        Set<Material> roadBlocks = config.roadBlocks();
        boolean result = roadBlocks.contains(below);
        roadCache.put(chicken.getUniqueId(), result);
        return result;
    }
}