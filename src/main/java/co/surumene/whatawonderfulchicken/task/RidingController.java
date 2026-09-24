package co.surumene.whatawonderfulchicken.task;

import co.surumene.whatawonderfulchicken.config.ConfigService;
import co.surumene.whatawonderfulchicken.data.StatType;
import co.surumene.whatawonderfulchicken.data.WonderfulChickenData;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenService;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenStore;
import org.bukkit.Bukkit;
import org.bukkit.Input;
import org.bukkit.Material;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

public final class RidingController implements Runnable {
    private final WonderfulChickenService chickens;
    private final WonderfulChickenStore store;
    private final ConfigService config;
    private final Map<UUID, Long> lastAirborneTick = new HashMap<>();
    private final Map<UUID, Boolean> roadCache = new HashMap<>();
    private final Set<UUID> mountedPlayers = new HashSet<>();
    private long tick;

    public RidingController(WonderfulChickenService chickens, WonderfulChickenStore store, ConfigService config) {
        this.chickens = chickens;
        this.store = store;
        this.config = config;
    }

    @Override
    public void run() {
        tick++;
        Set<UUID> nowMounted = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!(player.getVehicle() instanceof Chicken chicken) || !store.isWonderful(chicken)) continue;
            nowMounted.add(player.getUniqueId());
            tickMounted(player, chicken);
        }
        for (UUID uuid : new HashSet<>(mountedPlayers)) {
            if (!nowMounted.contains(uuid)) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) restoreHud(player);
            }
        }
        mountedPlayers.clear();
        mountedPlayers.addAll(nowMounted);
    }

    public void restoreHud(Player player) {
        player.sendExperienceChange(player.getExp(), player.getLevel());
    }

    private void tickMounted(Player player, Chicken chicken) {
        WonderfulChickenData data = store.load(chicken);
        if (chicken.hasAI()) chicken.setAI(false);
        Input input = player.getCurrentInput();
        boolean onGround = chicken.isOnGround();
        boolean inWater = chicken.isInWater();
        chicken.setRotation(player.getLocation().getYaw(), chicken.getLocation().getPitch());

        Vector velocity = chicken.getVelocity();
        Vector horizontal = horizontalInput(player, input);
        double speed = (onGround ? data.value(StatType.GROUND_SPEED) : data.value(StatType.AIR_SPEED)) / 20.0;
        if (onGround && isRoad(chicken)) speed *= config.roadMultiplier();
        if (horizontal.lengthSquared() > 0.0) horizontal.normalize().multiply(speed);
        velocity.setX(horizontal.getX()).setZ(horizontal.getZ());

        if (inWater) {
            lastAirborneTick.put(chicken.getUniqueId(), tick);
        } else if (onGround) {
            if (input.isJump()) velocity.setY(chickens.jumpVelocityForHeight(data.value(StatType.JUMP_STRENGTH)));
            long delay = Math.round(config.recoveryDelaySeconds() * 20.0);
            long lastAir = lastAirborneTick.getOrDefault(chicken.getUniqueId(), Long.MIN_VALUE / 4);
            if (tick - lastAir >= delay) {
                data.currentStamina(Math.min(data.value(StatType.STAMINA), data.currentStamina() + data.value(StatType.STAMINA_RECOVERY) / 20.0));
            }
        } else {
            lastAirborneTick.put(chicken.getUniqueId(), tick);
            if (input.isJump() && data.currentStamina() > 0.0) {
                velocity.setY(data.value(StatType.ASCENT_SPEED) / 20.0);
                data.currentStamina(Math.max(0.0, data.currentStamina() - config.staminaConsumptionPerSecond() / 20.0));
            } else if (velocity.getY() < -0.12) {
                velocity.setY(-0.12);
            }
        }

        chicken.setVelocity(velocity);
        player.setExhaustion(0.0f);
        float progress = (float) Math.max(0.0, Math.min(1.0, data.currentStamina() / Math.max(0.0001, data.value(StatType.STAMINA))));
        player.sendExperienceChange(progress, player.getLevel());
        store.save(chicken, data);
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