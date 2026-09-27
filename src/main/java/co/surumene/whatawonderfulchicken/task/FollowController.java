package co.surumene.whatawonderfulchicken.task;

import co.surumene.whatawonderfulchicken.WhatAWonderfulChickenPlugin;
import co.surumene.whatawonderfulchicken.config.ConfigService;
import co.surumene.whatawonderfulchicken.data.BehaviorMode;
import co.surumene.whatawonderfulchicken.data.WonderfulChickenData;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenService;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.EnumSet;
import java.util.concurrent.ThreadLocalRandom;

public final class FollowController implements Runnable {
    private static final double FOLLOW_STOP_DISTANCE = 5.0;
    private final WonderfulChickenService chickens;
    private final WonderfulChickenStore store;
    private final ConfigService config;
    private final GoalKey<Chicken> followGoalKey;

    public FollowController(WhatAWonderfulChickenPlugin plugin, WonderfulChickenService chickens,
                            WonderfulChickenStore store, ConfigService config) {
        this.chickens = chickens;
        this.store = store;
        this.config = config;
        this.followGoalKey = GoalKey.of(Chicken.class, new NamespacedKey(plugin, "follow_player"));
    }

    @Override
    public void run() {
        for (Chicken chicken : chickens.loadedChickens()) {
            if (isRidden(chicken)) {
                chicken.setAware(false);
                chicken.getPathfinder().stopPathfinding();
                continue;
            }
            WonderfulChickenData data = store.read(chicken);
            if (!chicken.hasAI()) chicken.setAI(true);

            if (data.behaviorMode() == BehaviorMode.WANDER) {
                removeFollowGoal(chicken);
                if (!chicken.isAware()) chicken.setAware(true);
                continue;
            }

            if (data.behaviorMode() == BehaviorMode.WAIT || data.followTarget() == null) {
                removeFollowGoal(chicken);
                waitPassively(chicken);
                continue;
            }

            ensureFollowGoal(chicken);
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

            if (distance <= FOLLOW_STOP_DISTANCE) {
                waitPassively(chicken);
                continue;
            }

            // The dedicated MOVE goal prevents vanilla temptation/random movement
            // from replacing the path when other players are nearby.
            if (!chicken.isAware()) chicken.setAware(true);
        }
    }

    private void ensureFollowGoal(Chicken chicken) {
        if (!Bukkit.getMobGoals().hasGoal(chicken, followGoalKey)) {
            Bukkit.getMobGoals().addGoal(chicken, 0, new FollowGoal(chicken));
        }
    }

    private void removeFollowGoal(Chicken chicken) {
        if (Bukkit.getMobGoals().hasGoal(chicken, followGoalKey)) {
            Bukkit.getMobGoals().removeGoal(chicken, followGoalKey);
        }
    }

    static Vector followDestination(Vector chickenPosition, Vector targetPosition) {
        Vector direction = targetPosition.clone().subtract(chickenPosition).setY(0.0);
        if (direction.lengthSquared() < 1.0e-8) return targetPosition.clone();
        return targetPosition.clone().subtract(direction.normalize().multiply(FOLLOW_STOP_DISTANCE));
    }

    private final class FollowGoal implements Goal<Chicken> {
        private final Chicken chicken;
        private int ticks;

        private FollowGoal(Chicken chicken) {
            this.chicken = chicken;
        }

        @Override
        public boolean shouldActivate() {
            return chicken.isValid() && store.isWonderful(chicken) && chicken.isAware()
                    && !isRidden(chicken) && store.read(chicken).behaviorMode() == BehaviorMode.FOLLOW;
        }

        @Override
        public boolean shouldStayActive() {
            return shouldActivate();
        }

        @Override
        public void tick() {
            // Replan twice per second, leaving the navigation uninterrupted between updates.
            if (ticks++ % 10 != 0) return;
            WonderfulChickenData data = store.read(chicken);
            Player target = data.followTarget() == null ? null : Bukkit.getPlayer(data.followTarget());
            if (target == null || !target.isOnline() || target.getWorld() != chicken.getWorld()
                    || chicken.getLocation().distanceSquared(target.getLocation())
                    <= FOLLOW_STOP_DISTANCE * FOLLOW_STOP_DISTANCE) {
                chicken.getPathfinder().stopPathfinding();
                return;
            }

            Location destination = target.getLocation();
            Vector approach = followDestination(chicken.getLocation().toVector(), destination.toVector());
            destination.setX(approach.getX());
            destination.setZ(approach.getZ());
            var path = chicken.getPathfinder().findPath(destination);
            if (path != null) {
                chicken.getPathfinder().moveTo(path, 1.0);
            } else {
                chicken.getPathfinder().stopPathfinding();
            }
        }

        @Override
        public void stop() {
            chicken.getPathfinder().stopPathfinding();
            ticks = 0;
        }

        @Override
        public GoalKey<Chicken> getKey() {
            return followGoalKey;
        }

        @Override
        public EnumSet<GoalType> getTypes() {
            return EnumSet.of(GoalType.MOVE);
        }
    }

    private boolean isRidden(Chicken chicken) {
        if (!chicken.getPassengers().isEmpty()) return true;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getVehicle() == chicken) return true;
        }
        return false;
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
