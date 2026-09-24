package co.surumene.whatawonderfulchicken.task;

import co.surumene.whatawonderfulchicken.config.ConfigService;
import co.surumene.whatawonderfulchicken.data.BehaviorMode;
import co.surumene.whatawonderfulchicken.data.StatType;
import co.surumene.whatawonderfulchicken.data.WonderfulChickenData;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenService;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class FollowController implements Runnable {
    private final WonderfulChickenService chickens;
    private final WonderfulChickenStore store;
    private final ConfigService config;

    public FollowController(WonderfulChickenService chickens, WonderfulChickenStore store, ConfigService config) {
        this.chickens = chickens;
        this.store = store;
        this.config = config;
    }

    @Override
    public void run() {
        for (Chicken chicken : chickens.loadedChickens()) {
            if (!chicken.getPassengers().isEmpty()) continue;
            WonderfulChickenData data = store.load(chicken);
            if (data.behaviorMode() == BehaviorMode.WANDER) {
                if (!chicken.hasAI()) chicken.setAI(true);
                continue;
            }
            if (chicken.hasAI()) chicken.setAI(false);
            if (data.behaviorMode() == BehaviorMode.WAIT || data.followTarget() == null) {
                stopHorizontal(chicken);
                continue;
            }
            Player target = Bukkit.getPlayer(data.followTarget());
            if (target == null || !target.isOnline() || target.getWorld() != chicken.getWorld()) {
                stopHorizontal(chicken);
                continue;
            }
            double distance = chicken.getLocation().distance(target.getLocation());
            if (distance >= config.followTeleportDistance()) {
                Location safe = findSafeNear(target);
                if (safe != null) chicken.teleport(safe);
                continue;
            }
            if (distance <= 2.5) {
                stopHorizontal(chicken);
                continue;
            }
            Vector delta = target.getLocation().toVector().subtract(chicken.getLocation().toVector());
            delta.setY(0);
            if (delta.lengthSquared() == 0) continue;
            double speed = Math.min(data.value(StatType.GROUND_SPEED) / 20.0, 0.55);
            Vector velocity = chicken.getVelocity();
            Vector horizontal = delta.normalize().multiply(speed);
            velocity.setX(horizontal.getX()).setZ(horizontal.getZ());
            if (chicken.isOnGround() && target.getLocation().getY() - chicken.getLocation().getY() > 0.8) {
                velocity.setY(chickens.jumpVelocityForHeight(Math.min(data.value(StatType.JUMP_STRENGTH), 2.0)));
            }
            chicken.setVelocity(velocity);
            chicken.setRotation(target.getLocation().getYaw(), chicken.getLocation().getPitch());
        }
    }

    private void stopHorizontal(Chicken chicken) {
        Vector velocity = chicken.getVelocity();
        velocity.setX(0).setZ(0);
        chicken.setVelocity(velocity);
    }

    private Location findSafeNear(Player player) {
        Location base = player.getLocation();
        int[][] offsets = {{2,0},{-2,0},{0,2},{0,-2},{2,2},{-2,2},{2,-2},{-2,-2},{3,0},{0,3}};
        for (int[] offset : offsets) {
            Location candidate = base.clone().add(offset[0], 0, offset[1]);
            for (int dy = 2; dy >= -2; dy--) {
                Location test = candidate.clone().add(0, dy, 0);
                Material feet = test.getBlock().getType();
                Material head = test.clone().add(0, 1, 0).getBlock().getType();
                Material ground = test.clone().subtract(0, 1, 0).getBlock().getType();
                if (feet.isAir() && head.isAir() && ground.isSolid()) return test.add(0.5, 0, 0.5);
            }
        }
        return null;
    }
}