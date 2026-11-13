package co.surumene.whatawonderfulchicken.chickentrap;

import co.surumene.whatawonderfulchicken.WhatAWonderfulChickenPlugin;
import co.surumene.whatawonderfulchicken.config.ConfigService;
import co.surumene.whatawonderfulchicken.display.DisplayService;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenService;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenStore;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.WindCharge;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Display;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Stateful Chicken Trap runtime. Game-world access occurs on the primary server thread. */
public final class ChickenTrapRuntime implements Runnable {
    private final WhatAWonderfulChickenPlugin plugin;
    private final ConfigService config;
    private final WonderfulChickenService chickens;
    private final DisplayService displays;
    private final ChickenTrapFounderFactory founders;
    private final java.util.concurrent.ThreadPoolExecutor founderWorker =
            new java.util.concurrent.ThreadPoolExecutor(1, 1, 0L,
                    java.util.concurrent.TimeUnit.MILLISECONDS,
                    new java.util.concurrent.ArrayBlockingQueue<>(8),
                    task -> {
                        Thread thread = new Thread(task, "wwc-trap-founder");
                        thread.setDaemon(true);
                        return thread;
                    }, new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
    private volatile boolean shuttingDown;
    private final ChickenTrapStateStore states;
    private final Map<UUID, Long> lastFullTimes = new HashMap<>();
    private final Set<UUID> skipped = new HashSet<>();
    private final Set<UUID> tracked = new HashSet<>();
    private final Set<UUID> activating = new HashSet<>();
    // Prepared founders are intentionally in-memory: restored waiters prepare again.
    private final Map<UUID, PreparedFounders> prepared = new HashMap<>();
    private final Map<UUID, UUID> preparing = new HashMap<>();
    private final Map<UUID, Long> preparationStarted = new HashMap<>();
    private final Map<UUID, Long> retryAfter = new HashMap<>();
    private static final long PREPARATION_RETRY_MILLIS = 3_000L;
    private static final long PREPARATION_TIMEOUT_MILLIS = 60_000L;

    private record PreparedFounders(
            co.surumene.wgl.api.GenomeEngine engine,
            co.surumene.whatawonderfulchicken.genome.WonderfulChickenGenomeProfile profile,
            co.surumene.whatawonderfulchicken.data.WonderfulChickenData first,
            co.surumene.whatawonderfulchicken.data.WonderfulChickenData second) {}
    private final Map<UUID, Location> projectileDestinations = new HashMap<>();

    public ChickenTrapRuntime(WhatAWonderfulChickenPlugin plugin, ConfigService config,
                              WonderfulChickenService chickens, WonderfulChickenStore store,
                              DisplayService displays) {
        this.plugin = plugin;
        this.config = config;
        this.chickens = chickens;
        this.displays = displays;
        this.states = new ChickenTrapStateStore(plugin);
        this.founders = new ChickenTrapFounderFactory(plugin, store, config);
    }

    public void shutdown() {
        shuttingDown = true;
        founderWorker.shutdownNow();
        activating.clear();
        prepared.clear();
        preparing.clear();
        preparationStarted.clear();
        retryAfter.clear();
    }

    public void restoreLoaded() {
        for (World world : Bukkit.getWorlds()) {
            lastFullTimes.put(world.getUID(), world.getFullTime());
            for (Entity entity : world.getEntities()) register(entity);
        }
    }

    public void register(Entity entity) {
        if (states.role(entity) == null) return;
        if (entity.getWorld().getFullTime() >= states.expiry(entity)) {
            // Late chunk loads are cleanup only. The encounter's dawn burst was
            // a world-time event, not an effect to replay when someone returns.
            expire(entity, false);
            return;
        }
        tracked.add(entity.getUniqueId());
        if (entity instanceof Skeleton skeleton
                && states.hasRole(skeleton, ChickenTrapStateStore.ARMED)) {
            requestPreparation(skeleton);
        }
    }

    public boolean isTrapMount(Chicken chicken) {
        return chicken != null && states.hasRole(chicken, ChickenTrapStateStore.MOUNT);
    }

    public void markTimeSkip(World world) {
        skipped.add(world.getUID());
    }

    @Override public void run() {
        for (World world : Bukkit.getWorlds()) {
            long now = world.getFullTime();
            Long prev = lastFullTimes.put(world.getUID(), now);
            if (prev == null) continue;
            if (ChickenTrapPolicy.dawnCrossed(prev, now)) expireWorld(world);
            if (skipped.remove(world.getUID())
                    || !ChickenTrapPolicy.naturalCheck(prev, now)
                    || !ChickenTrapPolicy.isNewMoon(now)
                    || world.hasStorm() || world.isThundering()
                    || world.getPlayers().isEmpty()
                    || !ChickenTrapPolicy.roll(draw(), config.chickenTrapChance())) continue;
            List<Player> players = world.getPlayers();
            Player chosen = players.get((int) (draw() * players.size()));
            Location target = randomSkyLocation(chosen);
            if (target != null) launch(target);
        }
        for (UUID id : List.copyOf(tracked)) {
            Entity entity = Bukkit.getEntity(id);
            if (entity == null) {
                // Unloaded entities keep their PDC and are rediscovered when their chunk loads.
                tracked.remove(id);
                forgetPreparation(id);
                continue;
            }
            String role = states.role(entity);
            if (role == null || !entity.isValid() || entity.isDead()) {
                tracked.remove(id);
                forgetPreparation(id);
                continue;
            }
            if (entity.getWorld().getFullTime() >= states.expiry(entity)) {
                expire(entity, true);
                continue;
            }
            if (entity instanceof Skeleton skeleton) {
                if (ChickenTrapStateStore.ARMED.equals(role)) tickArmed(skeleton);
                else if (ChickenTrapStateStore.RIDER.equals(role)) tickRider(skeleton);
            } else if (entity instanceof ItemDisplay display
                    && ChickenTrapStateStore.VISUAL.equals(role)) {
                Entity charge = Bukkit.getEntity(states.partner(display));
                if (charge instanceof WindCharge active && active.isValid()) {
                    display.teleport(active.getLocation());
                } else {
                    states.clear(display);
                    tracked.remove(display.getUniqueId());
                    display.remove();
                }
            }
        }
    }

    private Location randomSkyLocation(Player player) {
        World world = player.getWorld();
        for (int attempt = 0; attempt < 20; attempt++) {
            var offset = ChickenTrapPolicy.offset(draw(), draw());
            int x = (int) Math.floor(player.getX() + offset.x());
            int z = (int) Math.floor(player.getZ() + offset.z());
            int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING) + 1;
            if (y >= world.getMaxHeight() - 18 || y <= world.getMinHeight()) continue;
            Location location = new Location(world, x + .5, y, z + .5);
            if (world.getWorldBorder().isInside(location)
                    && location.getBlock().getLightFromSky() == 15
                    && location.getBlock().isPassable()
                    && location.clone().add(0, 1, 0).getBlock().isPassable())
                return location;
        }
        return null;
    }

    private void launch(Location destination) {
        World world = destination.getWorld();
        long expiry = world.getFullTime() / ChickenTrapPolicy.DAY_TICKS
                * ChickenTrapPolicy.DAY_TICKS + ChickenTrapPolicy.DAWN_TIME;
        WindCharge charge = world.spawn(destination.clone().add(0, 15, 0),
                WindCharge.class, CreatureSpawnEvent.SpawnReason.CUSTOM);
        states.mark(charge, ChickenTrapStateStore.CHARGE, expiry);
        projectileDestinations.put(charge.getUniqueId(), destination.clone());
        tracked.add(charge.getUniqueId());
        charge.setDirection(new Vector(0, -1, 0));
        charge.setVelocity(new Vector(0, -1.25, 0));
        charge.setPersistent(true);
        ItemDisplay visual = world.spawn(charge.getLocation(), ItemDisplay.class,
                CreatureSpawnEvent.SpawnReason.CUSTOM);
        visual.setItemStack(ItemStack.of(Material.WIND_CHARGE));
        visual.setBillboard(Display.Billboard.CENTER);
        visual.setTransformation(new Transformation(new Vector3f(),
                new Quaternionf(), new Vector3f(3.0f, 3.0f, 3.0f), new Quaternionf()));
        states.mark(visual, ChickenTrapStateStore.VISUAL, expiry);
        states.pair(visual, charge.getUniqueId());
        tracked.add(visual.getUniqueId());
    }

    public void hitCharge(WindCharge charge) {
        if (!states.hasRole(charge, ChickenTrapStateStore.CHARGE)) return;
        Location target = projectileDestinations.remove(charge.getUniqueId());
        long expiry = states.expiry(charge);
        states.clear(charge);
        tracked.remove(charge.getUniqueId());
        if (target == null) {
            World world = charge.getWorld();
            int x = charge.getLocation().getBlockX();
            int z = charge.getLocation().getBlockZ();
            int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING) + 1;
            target = new Location(world, x + .5, y, z + .5);
        }
        Location finalTarget = target;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (finalTarget.getWorld().getFullTime() < expiry) spawnArmed(finalTarget, expiry);
        });
    }

    private void spawnArmed(Location location, long expiry) {
        Skeleton skeleton = location.getWorld().spawn(location, Skeleton.class,
                CreatureSpawnEvent.SpawnReason.CUSTOM);
        configureSkeleton(skeleton);
        skeleton.setAI(false);
        states.mark(skeleton, ChickenTrapStateStore.ARMED, expiry);
        states.graceUntil(skeleton, System.currentTimeMillis() + ChickenTrapPolicy.GRACE_MILLIS);
        tracked.add(skeleton.getUniqueId());
        requestPreparation(skeleton);
    }

    private static void configureSkeleton(Skeleton skeleton) {
        skeleton.setPersistent(true);
        var health = skeleton.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) health.setBaseValue(50.0);
        skeleton.setHealth(50.0);
        var reach = skeleton.getAttribute(Attribute.FOLLOW_RANGE);
        if (reach != null && reach.getBaseValue() < 20.0) reach.setBaseValue(20.0);
    }


    /** Unprepared trap bait cannot be damaged. Entity attacks give totem feedback. */
    public boolean protectDamage(Entity victim, boolean entityAttack) {
        if (!(victim instanceof Skeleton skeleton)
                || !states.hasRole(skeleton, ChickenTrapStateStore.ARMED)
                || isPrepared(skeleton)) {
            return false;
        }
        if (entityAttack) {
            Location effect = skeleton.getLocation().add(0.0, 1.0, 0.0);
            World world = skeleton.getWorld();
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, effect, 30,
                    0.45, 0.65, 0.45, 0.2);
            world.playSound(effect, Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
        }
        return true;
    }

    public boolean attackSkeleton(Skeleton skeleton) {
        return skeleton != null && activate(skeleton);
    }

    private boolean isPrepared(Skeleton skeleton) {
        UUID id = skeleton.getUniqueId();
        PreparedFounders ready = prepared.get(id);
        if (ready == null) return false;
        // A profile reload invalidates old, computed genomes before any damage is accepted.
        if (ready.engine() != plugin.genomeLib().engine()
                || ready.profile() != plugin.genomeProfile()) {
            prepared.remove(id);
            requestPreparation(skeleton);
            return false;
        }
        return true;
    }

    private void requestPreparation(Skeleton initial) {
        UUID id = initial.getUniqueId();
        if (shuttingDown || !plugin.isEnabled() || !initial.isValid() || initial.isDead()
                || !states.hasRole(initial, ChickenTrapStateStore.ARMED)
                || prepared.containsKey(id) || preparing.containsKey(id)
                || System.currentTimeMillis() < retryAfter.getOrDefault(id, 0L)) return;

        var engine = plugin.genomeLib().engine();
        var profile = plugin.genomeProfile();
        long seedA = ThreadLocalRandom.current().nextLong();
        long seedB = ThreadLocalRandom.current().nextLong();
        UUID generation = UUID.randomUUID();
        preparing.put(id, generation);
        preparationStarted.put(id, System.currentTimeMillis());
        try {
            founderWorker.execute(() -> {
                co.surumene.whatawonderfulchicken.founder.FounderGenomeSynthesis first = null;
                co.surumene.whatawonderfulchicken.founder.FounderGenomeSynthesis second = null;
                RuntimeException failure = null;
                try {
                    var synthesizer =
                            new co.surumene.whatawonderfulchicken.founder.WonderfulChickenFounderSynthesizer(engine, profile);
                    first = synthesizer.synthesize(
                            co.surumene.whatawonderfulchicken.founder.FounderOrigin.CHICKEN_TRAP, seedA);
                    second = synthesizer.synthesize(
                            co.surumene.whatawonderfulchicken.founder.FounderOrigin.CHICKEN_TRAP, seedB);
                } catch (RuntimeException error) {
                    failure = error;
                }
                var firstResult = first;
                var secondResult = second;
                var error = failure;
                if (shuttingDown) return;
                try {
                    Bukkit.getScheduler().runTask(plugin,
                            () -> finishPreparation(initial, generation, engine, profile,
                                    firstResult, secondResult, error));
                } catch (IllegalStateException ignored) {
                    // Plugin is being disabled.
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException rejected) {
            preparing.remove(id, generation);
            preparationStarted.remove(id);
            retryAfter.put(id, System.currentTimeMillis() + PREPARATION_RETRY_MILLIS);
            plugin.getLogger().warning("Chicken Trap founder preparation queue full: " + id);
        }
    }

    private void finishPreparation(
            Skeleton initial, UUID generation,
            co.surumene.wgl.api.GenomeEngine engine,
            co.surumene.whatawonderfulchicken.genome.WonderfulChickenGenomeProfile profile,
            co.surumene.whatawonderfulchicken.founder.FounderGenomeSynthesis first,
            co.surumene.whatawonderfulchicken.founder.FounderGenomeSynthesis second,
            RuntimeException failure) {
        UUID id = initial.getUniqueId();
        if (!generation.equals(preparing.get(id))) return; // stale or timed-out job
        preparing.remove(id);
        preparationStarted.remove(id);
        if (shuttingDown || !plugin.isEnabled() || !initial.isValid() || initial.isDead()
                || Bukkit.getEntity(id) != initial
                || !states.hasRole(initial, ChickenTrapStateStore.ARMED)
                || initial.getWorld().getFullTime() >= states.expiry(initial)) {
            forgetPreparation(id);
            return;
        }
        if (engine != plugin.genomeLib().engine() || profile != plugin.genomeProfile()) {
            retryAfter.put(id, 0L); // new configuration requires fresh synthesis
            return;
        }
        try {
            if (failure != null) throw failure;
            var firstData = founders.fromSynthesis(first);
            var secondData = founders.fromSynthesis(second);
            prepared.put(id, new PreparedFounders(engine, profile, firstData, secondData));
            retryAfter.remove(id);
        } catch (RuntimeException error) {
            plugin.getLogger().warning("Chicken Trap founder preparation failed for " + id + ": " + error);
            retryAfter.put(id, System.currentTimeMillis() + PREPARATION_RETRY_MILLIS);
        }
    }

    private void forgetPreparation(UUID id) {
        preparing.remove(id);
        preparationStarted.remove(id);
        retryAfter.remove(id);
        prepared.remove(id);
        activating.remove(id);
    }

    private void tickArmed(Skeleton skeleton) {
        UUID id = skeleton.getUniqueId();
        if (!isPrepared(skeleton)) {
            Long started = preparationStarted.get(id);
            if (started != null && System.currentTimeMillis() - started > PREPARATION_TIMEOUT_MILLIS) {
                plugin.getLogger().warning("Chicken Trap founder preparation timed out for " + id);
                preparing.remove(id);
                preparationStarted.remove(id);
                retryAfter.put(id, System.currentTimeMillis() + PREPARATION_RETRY_MILLIS);
            }
            requestPreparation(skeleton);
            return; // no proximity activation until both genomes exist
        }
        if (System.currentTimeMillis() < states.graceUntil(skeleton)) return;
        double radiusSquared = config.chickenTrapActivationRadius() * config.chickenTrapActivationRadius();
        Location location = skeleton.getLocation();
        for (Player player : skeleton.getWorld().getPlayers()) {
            if (player.isValid() && !player.isDead()
                    && location.distanceSquared(player.getLocation()) <= radiusSquared) {
                activate(skeleton);
                return;
            }
        }
    }

    /** The prepared pair is applied synchronously; a triggering hit is consumed. */
    private boolean activate(Skeleton initial) {
        if (!initial.isValid() || initial.isDead()
                || !states.hasRole(initial, ChickenTrapStateStore.ARMED)
                || !isPrepared(initial)) return false;
        UUID id = initial.getUniqueId();
        if (!activating.add(id)) return false;
        try {
            finishActivation(initial, prepared.get(id));
        } finally {
            activating.remove(id);
        }
        return true;
    }

    private void finishActivation(Skeleton initial, PreparedFounders ready) {
        if (ready == null || shuttingDown || !plugin.isEnabled()
                || !initial.isValid() || initial.isDead()
                || !states.hasRole(initial, ChickenTrapStateStore.ARMED)
                || ready.engine() != plugin.genomeLib().engine()
                || ready.profile() != plugin.genomeProfile()) return;

        Location location = initial.getLocation();
        World world = initial.getWorld();
        long expiry = states.expiry(initial);
        if (world.getFullTime() >= expiry) return;

        Chicken first = null;
        Chicken second = null;
        Skeleton secondRider = null;
        try {
            first = world.spawn(location.clone().add(-1, 0, 0), Chicken.class,
                    CreatureSpawnEvent.SpawnReason.CUSTOM);
            second = world.spawn(location.clone().add(1, 0, 0), Chicken.class,
                    CreatureSpawnEvent.SpawnReason.CUSTOM);
            first.setAdult();
            second.setAdult();
            chickens.initialize(first, ready.first());
            chickens.initialize(second, ready.second());
            displays.rebuild(first);
            displays.rebuild(second);
            secondRider = world.spawn(location.clone().add(1, 0, 0), Skeleton.class,
                    CreatureSpawnEvent.SpawnReason.CUSTOM);
            configureSkeleton(secondRider);
            if (!first.addPassenger(initial) || !second.addPassenger(secondRider))
                throw new IllegalStateException("Could not mount trap skeletons");

            states.mark(initial, ChickenTrapStateStore.RIDER, expiry);
            states.mark(secondRider, ChickenTrapStateStore.RIDER, expiry);
            states.mark(first, ChickenTrapStateStore.MOUNT, expiry);
            states.mark(second, ChickenTrapStateStore.MOUNT, expiry);
            states.pair(initial, first.getUniqueId());
            states.pair(secondRider, second.getUniqueId());
            states.pair(first, initial.getUniqueId());
            states.pair(second, secondRider.getUniqueId());
            tracked.addAll(List.of(initial.getUniqueId(), secondRider.getUniqueId(),
                    first.getUniqueId(), second.getUniqueId()));
            initial.setAI(true);
            prepared.remove(initial.getUniqueId());
        } catch (RuntimeException error) {
            if (secondRider != null) secondRider.remove();
            if (first != null) first.remove();
            if (second != null) second.remove();
            if (initial.isValid()) {
                states.clear(initial);
                states.mark(initial, ChickenTrapStateStore.ARMED, expiry);
                states.graceUntil(initial, System.currentTimeMillis() + ChickenTrapPolicy.GRACE_MILLIS);
                initial.setAI(false);
                tracked.add(initial.getUniqueId());
            } else {
                forgetPreparation(initial.getUniqueId());
            }
            plugin.getLogger().warning("Chicken Trap activation failed: " + error);
            return;
        }

        // Play the encounter effect only after the pair is successfully committed.
        try {
            windExplosion(location);
        } catch (RuntimeException error) {
            plugin.getLogger().warning("Chicken Trap activation effect failed: " + error);
        }
    }

    private void tickRider(Skeleton skeleton) {
        UUID mountId = states.partner(skeleton);
        Entity paired = mountId == null ? null : Bukkit.getEntity(mountId);
        if (!(paired instanceof Chicken chicken) || !states.hasRole(chicken, ChickenTrapStateStore.MOUNT)) {
            return;
        }
        if (skeleton.getVehicle() != chicken && !chicken.addPassenger(skeleton)) return;
        LivingEntity target = skeleton.getTarget();
        if (!(target instanceof Player player) || !player.isValid() || player.isDead()) {
            target = nearestPlayer(skeleton, 20.0);
            if (target instanceof Player player) skeleton.setTarget(player);
        }
        if (target != null) {
            double distanceSquared = skeleton.getLocation().distanceSquared(target.getLocation());
            // Preserve vanilla bow AI within its standard range; only supplement 15-20 blocks.
            int interval = skeleton.getWorld().getDifficulty() == org.bukkit.Difficulty.HARD ? 20 : 40;
            if (ChickenTrapPolicy.extendedBowRange(distanceSquared)
                    && skeleton.hasLineOfSight(target)
                    && skeleton.getTicksLived() % interval == 0) {
                skeleton.rangedAttack(target, 1.0F);
            }
            // Vanilla 26.2 delegates the Skeleton's navigation / movement
            // control to the mount. Do not overwrite that with a separate
            // Chicken path; the ranged bow AI owns pursuit and strafing.
        }
    }

    private static Player nearestPlayer(Skeleton skeleton, double radius) {
        Player nearest = null;
        double distance = radius * radius;
        for (Player player : skeleton.getWorld().getPlayers()) {
            if (!player.isValid() || player.isDead()) continue;
            double current = skeleton.getLocation().distanceSquared(player.getLocation());
            if (current < distance) { distance = current; nearest = player; }
        }
        return nearest;
    }

    public void died(LivingEntity entity) {
        String role = states.role(entity);
        if (role == null) return;
        UUID partner = states.partner(entity);
        if (ChickenTrapStateStore.RIDER.equals(role) && partner != null) {
            Entity paired = Bukkit.getEntity(partner);
            if (paired instanceof Chicken chicken && states.hasRole(chicken, ChickenTrapStateStore.MOUNT)) {
                states.clear(chicken);
                tracked.remove(chicken.getUniqueId());
                chicken.eject();
            }
        }
        // The rider remains a marked trap participant when its mount dies,
        // so it is still removed at dawn.
        states.clear(entity);
        tracked.remove(entity.getUniqueId());
        forgetPreparation(entity.getUniqueId());
    }

    private void expireWorld(World world) {
        for (Entity entity : new ArrayList<>(world.getEntities())) {
            if (states.role(entity) != null && world.getFullTime() >= states.expiry(entity))
                expire(entity, true);
        }
    }

    /**
     * A rider and its marked Chicken form one encounter unit at expiry.
     * Clear both roles before removing either entity so death/load callbacks
     * cannot release the Chicken or produce a second wind burst.
     */
    private void expire(Entity entity, boolean burst) {
        String role = states.role(entity);
        if (role == null) return;
        Location location = entity.getLocation();

        UUID partnerId = states.partner(entity);
        Entity partner = partnerId == null ? null : Bukkit.getEntity(partnerId);
        if (partner != null && ChickenTrapPolicy.linkedMountPair(
                role, states.role(partner),
                entity.getUniqueId().equals(states.partner(partner)))) {
            states.clear(partner);
            tracked.remove(partner.getUniqueId());
            partner.remove();
        }

        states.clear(entity);
        tracked.remove(entity.getUniqueId());
        forgetPreparation(entity.getUniqueId());
        projectileDestinations.remove(entity.getUniqueId());
        entity.remove();
        if (burst && ChickenTrapPolicy.burstOnExpiry(role)) windExplosion(location);
    }

    private static void windExplosion(Location location) {
        WindCharge charge = location.getWorld().spawn(location.clone().add(0, .5, 0),
                WindCharge.class, CreatureSpawnEvent.SpawnReason.CUSTOM);
        charge.explode();
    }

    private static double draw() {
        return ThreadLocalRandom.current().nextDouble();
    }
}
