package co.surumene.whatawonderfulchicken.task;

import co.surumene.whatawonderfulchicken.compat.BedrockCompatibility;
import co.surumene.whatawonderfulchicken.config.ConfigService;
import co.surumene.whatawonderfulchicken.data.StatType;
import co.surumene.whatawonderfulchicken.data.WonderfulChickenData;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenService;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenStore;
import org.bukkit.Bukkit;
import org.bukkit.Input;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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

public final class RidingController implements Runnable, Listener {
    private final WonderfulChickenService chickens;
    private final WonderfulChickenStore store;
    private final ConfigService config;
    private final BedrockCompatibility bedrock;
    private final Map<UUID, UUID> mountedInteractionProxies = new HashMap<>();
    private final Map<UUID, UUID> mountedInteractionOwners = new HashMap<>();
    private final Map<UUID, Long> lastAirborneTick = new HashMap<>();
    private final Map<UUID, Boolean> roadCache = new HashMap<>();
    private final Map<UUID, UUID> mountedChickens = new HashMap<>();
    private Set<UUID> mountedIds = Set.of();
    private final Map<UUID, Float> exhaustionAtMount = new HashMap<>();
    private final Set<UUID> airFlapLockedUntilJumpRelease = new HashSet<>();
    private final RecoveryMembership recoveryCandidates = new RecoveryMembership();
    private final RiderMembership riderCandidates = new RiderMembership();
    private boolean riderIndexBootstrapped;
    private long tick;

    public RidingController(WonderfulChickenService chickens, WonderfulChickenStore store, ConfigService config, BedrockCompatibility bedrock) {
        this.chickens = chickens;
        this.store = store;
        this.config = config;
        this.bedrock = bedrock;
    }

    /** Called after committed stamina/capacity changes; full chickens remain inactive. */
    public void refreshRecovery(Chicken chicken) {
        if (!chicken.isValid() || !store.isWonderful(chicken)) {
            recoveryCandidates.remove(chicken);
            return;
        }
        WonderfulChickenData data = store.read(chicken);
        recoveryCandidates.update(chicken,
                data.currentStamina() < chickens.effective(data, StatType.STAMINA));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (event.getEntity() instanceof Player player
                && event.getMount() instanceof Chicken chicken
                && store.isWonderful(chicken)) {
            riderCandidates.mount(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player
                && event.getDismounted() instanceof Chicken) {
            riderCandidates.dismount(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        riderCandidates.dismount(event.getPlayer().getUniqueId());
    }

    @Override
    public void run() {
        tick++;
        Map<UUID, UUID> nowMounted = new HashMap<>();
        Set<UUID> mountedChickenIds = new HashSet<>();
        // Compatibility resync for eventless third-party mount changes and hot reload.
        if (!riderIndexBootstrapped || tick % 200L == 0L) {
            riderIndexBootstrapped = true;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getVehicle() instanceof Chicken chicken && store.isWonderful(chicken)) {
                    riderCandidates.mount(player.getUniqueId());
                }
            }
        }
        for (UUID riderId : riderCandidates.snapshot()) {
            Player player = Bukkit.getPlayer(riderId);
            if (player == null || !(player.getVehicle() instanceof Chicken chicken)
                    || !store.isWonderful(chicken)) {
                riderCandidates.dismount(riderId);
                continue;
            }
            UUID playerId = player.getUniqueId();
            UUID chickenId = chicken.getUniqueId();
            nowMounted.put(playerId, chickenId);
            mountedChickenIds.add(chickenId);
            exhaustionAtMount.putIfAbsent(playerId, player.getExhaustion());
            tickMounted(player, chicken);
        }
        for (Chicken chicken : recoveryCandidates.snapshot()) {
            if (!chicken.isValid() || !store.isWonderful(chicken)) {
                recoveryCandidates.remove(chicken);
                continue;
            }
            if (!mountedChickenIds.contains(chicken.getUniqueId())) tickUnmounted(chicken);
        }
        for (Map.Entry<UUID, UUID> entry : new HashMap<>(mountedChickens).entrySet()) {
            if (!nowMounted.containsKey(entry.getKey())) finishMount(entry.getKey(), entry.getValue());
        }
        mountedChickens.clear();
        mountedChickens.putAll(nowMounted);
        mountedIds = Set.copyOf(mountedChickenIds);
        // Stale-UUID cleanup need not query Bukkit.getEntity for every tick.
        if (tick % 200L == 0L) {
            lastAirborneTick.keySet().removeIf(uuid -> Bukkit.getEntity(uuid) == null);
        }
    }

    public Set<UUID> mountedChickenIds() {
        return mountedIds;
    }

    public void restoreHud(Player player) {
        player.sendExperienceChange(player.getExp(), player.getLevel());
    }

    public void shutdown() {
        for (Map.Entry<UUID, UUID> entry : new HashMap<>(mountedChickens).entrySet()) {
            finishMount(entry.getKey(), entry.getValue());
        }
        mountedChickens.clear();
        mountedIds = Set.of();
        riderCandidates.clear();
        exhaustionAtMount.clear();
        airFlapLockedUntilJumpRelease.clear();
        recoveryCandidates.clear();
        lastAirborneTick.clear();
        removeAllInteractionProxies();
    }

    private void finishMount(UUID playerId, UUID chickenId) {
        roadCache.remove(chickenId);
        airFlapLockedUntilJumpRelease.remove(chickenId);
        removeInteractionProxy(chickenId);
        Player player = Bukkit.getPlayer(playerId);
        Float exhaustion = exhaustionAtMount.remove(playerId);
        bedrock.clearSeatOffset(playerId);
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
        WonderfulChickenData data = store.read(chicken);
        double maximum = chickens.effective(data, StatType.STAMINA);
        if (data.currentStamina() >= maximum) return;
        long delay = Math.round(config.recoveryDelaySeconds() * 20.0);
        long lastAir = lastAirborneTick.getOrDefault(chickenId, Long.MIN_VALUE / 4);
        if (tick - lastAir < delay) return;
        store.setCurrentStamina(chicken, Math.min(maximum,
                data.currentStamina() + chickens.effective(data, StatType.STAMINA_RECOVERY) / 20.0));
    }

    private void tickMounted(Player player, Chicken chicken) {
        bedrock.applySeatOffset(player, chicken);
        ensureInteractionProxy(chicken);
        WonderfulChickenData data = store.read(chicken);
        double stamina = data.currentStamina();
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
        double speed = (onGround ? chickens.effective(data, StatType.GROUND_SPEED) : chickens.effective(data, StatType.AIR_SPEED)) / 20.0;
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
                velocity.setY(chickens.jumpVelocityForHeight(chickens.effective(data, StatType.JUMP_STRENGTH)));
                airFlapLockedUntilJumpRelease.add(chickenId);
            }
            long delay = Math.round(config.recoveryDelaySeconds() * 20.0);
            long lastAir = lastAirborneTick.getOrDefault(chicken.getUniqueId(), Long.MIN_VALUE / 4);
            if (tick - lastAir >= delay) {
                stamina = Math.min(chickens.effective(data, StatType.STAMINA), stamina + chickens.effective(data, StatType.STAMINA_RECOVERY) / 20.0);
            }
        } else {
            lastAirborneTick.put(chicken.getUniqueId(), tick);
            if (input.isJump() && !airFlapLockedUntilJumpRelease.contains(chickenId) && stamina > 0.0) {
                velocity.setY(chickens.effective(data, StatType.ASCENT_SPEED) / 20.0);
                stamina = Math.max(0.0, stamina - config.staminaConsumptionPerSecond() / 20.0);
            } else if (velocity.getY() < -0.12) {
                velocity.setY(-0.12);
            }
        }

        chicken.setVelocity(velocity);
        Float baselineExhaustion = exhaustionAtMount.get(player.getUniqueId());
        if (baselineExhaustion != null) player.setExhaustion(baselineExhaustion);
        float progress = (float) Math.max(0.0, Math.min(1.0, stamina / Math.max(0.0001, chickens.effective(data, StatType.STAMINA))));
        player.sendExperienceChange(progress, player.getLevel());
        store.setCurrentStamina(chicken, stamina);
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
        Location destination = interactionLocation(chicken);
        Location current = proxy.getLocation();
        if (current.getX() != destination.getX() || current.getY() != destination.getY()
                || current.getZ() != destination.getZ() || current.getYaw() != destination.getYaw()) {
            proxy.teleport(destination);
        }
        float width = (float) Math.max(0.5, Math.max(box.getWidthX(), box.getWidthZ()) * 1.15);
        float height = (float) Math.max(0.5, box.getHeight() * 1.10);
        if (Float.compare(proxy.getInteractionWidth(), width) != 0) proxy.setInteractionWidth(width);
        if (Float.compare(proxy.getInteractionHeight(), height) != 0) proxy.setInteractionHeight(height);
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