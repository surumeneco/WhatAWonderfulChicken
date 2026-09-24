package co.surumene.whatawonderfulchicken.task;

import co.surumene.whatawonderfulchicken.config.ConfigService;
import co.surumene.whatawonderfulchicken.data.BehaviorMode;
import co.surumene.whatawonderfulchicken.data.WonderfulChickenData;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenService;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

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
            if (!chicken.getPassengers().isEmpty()) {
                chicken.getPathfinder().stopPathfinding();
                continue;
            }
            WonderfulChickenData data = store.load(chicken);
            if (!chicken.hasAI()) chicken.setAI(true);

            if (data.behaviorMode() == BehaviorMode.WANDER) {
                if (!chicken.isAware()) chicken.setAware(true);
                continue;
            }

            if (data.behaviorMode() == BehaviorMode.WAIT || data.followTarget() == null) {
                waitPassively(chicken);
                continue;
            }

            Player target = Bukkit.getPlayer(data.followTarget());
            if (target == null || !target.isOnline() || target.getWorld() != chicken.getWorld()) {
                waitPassively(chicken);
                continue;
            }

            double distance = chicken.getLocation().distance(target.getLocation());
            if (distance >= config.followTeleportDistance()) {
                Location safe = findSafeNear(target, chicken);
                if (safe != null) chicken.teleport(safe);
                waitPassively(chicken);
                continue;
            }

            if (distance <= 2.5) {
                waitPassively(chicken);
                continue;
            }

            if (!chicken.isAware()) chicken.setAware(true);
            if (!chicken.getPathfinder().moveTo(target, 1.0)) {
                waitPassively(chicken);
            }
        }
    }

    private void waitPassively(Chicken chicken) {
        if (chicken.isAware()) chicken.setAware(false);
        chicken.getPathfinder().stopPathfinding();
        if (ThreadLocalRandom.current().nextDouble() < 0.08) {
            float yaw = chicken.getYaw() + (float) ThreadLocalRandom.current().nextDouble(-60.0, 60.0);
            chicken.setRotation(yaw, chicken.getPitch());
        }
    }

    private Location findSafeNear(Player player, Chicken chicken) {
        Location base = player.getLocation();
        Location current = chicken.getLocation();
        BoundingBox currentBox = chicken.getBoundingBox();
        int[][] offsets = {{2,0},{-2,0},{0,2},{0,-2},{2,2},{-2,2},{2,-2},{-2,-2},{3,0},{0,3}};
        for (int[] offset : offsets) {
            Location candidate = base.clone().add(offset[0], 0, offset[1]);
            for (int dy = 2; dy >= -2; dy--) {
                Location destination = candidate.clone().add(0.5, dy, 0.5);
                Material ground = destination.clone().subtract(0, 1, 0).getBlock().getType();
                if (!ground.isSolid()) continue;
                Vector shift = destination.toVector().subtract(current.toVector());
                BoundingBox destinationBox = currentBox.clone().shift(shift);
                if (!destination.getWorld().hasCollisionsIn(destinationBox)) return destination;
            }
        }
        return null;
    }
}
