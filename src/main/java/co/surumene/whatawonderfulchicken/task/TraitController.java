package co.surumene.whatawonderfulchicken.task;

import co.surumene.whatawonderfulchicken.config.ConfigService;
import co.surumene.whatawonderfulchicken.data.Trait;
import co.surumene.whatawonderfulchicken.runtime.TraitRuntimeModifiers;
import java.util.List;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenService;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenStore;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Chicken;
import org.bukkit.scheduler.BukkitTask;
import co.surumene.whatawonderfulchicken.WhatAWonderfulChickenPlugin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Ocelot;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Apply short-lived vanilla effects. Never erase potion effects owned by other mechanics. */
public final class TraitController implements Runnable {
    private static final int EFFECT_TICKS = 80;
    private static final int EFFECT_REFRESH_THRESHOLD = 40;
    private final WhatAWonderfulChickenPlugin plugin;
    private final WonderfulChickenService chickens;
    private final WonderfulChickenStore store;
    private final ConfigService config;
    private final Map<UUID, Long> lastAlert = new HashMap<>();
    private final RuntimeTraitMembership membership = new RuntimeTraitMembership();
    private long ticks;
    private BukkitTask task;
    private boolean schedulerStarted;

    public TraitController(WhatAWonderfulChickenPlugin plugin, WonderfulChickenService chickens, WonderfulChickenStore store, ConfigService config) {
        this.plugin = plugin;
        this.chickens = chickens;
        this.store = store;
        this.config = config;
    }

    /** Recompute membership only when an entity is restored or its traits change. */
    public void updateCandidate(Chicken chicken) {
        if (!chicken.isValid() || !store.isWonderful(chicken)) {
            membership.remove(chicken);
            updateTaskState();
            return;
        }
        var data = store.read(chicken);
        membership.update(chicken, RuntimeTraitMembership.needsTick(data::hasTrait));
        updateTaskState();
    }

    @Override
    public void run() {
        ticks += 10L;
        Set<UUID> active = new HashSet<>();
        for (Chicken chicken : membership.snapshot()) {
            if (!chicken.isValid() || !store.isWonderful(chicken)) {
                membership.remove(chicken);
                continue;
            }
            active.add(chicken.getUniqueId());
            var data = store.read(chicken);
            var expressed = data.phenotypeSnapshot() == null
                    ? List.<co.surumene.whatawonderfulchicken.data.ExpressedTrait>of()
                    : data.phenotypeSnapshot().expressedTraits();
            for (Trait trait : Trait.values()) {
                if (!data.hasTrait(trait)) continue;
                int amplifier = TraitRuntimeModifiers.potionAmplifier(expressed, trait);
                switch (trait) {
                    case HINOTORI -> apply(chicken, PotionEffectType.FIRE_RESISTANCE, 0);
                    case WATAGE -> apply(chicken, PotionEffectType.SLOW_FALLING, 0);
                    case FUKUTSU -> apply(chicken, PotionEffectType.REGENERATION, amplifier);
                    case SAIKUTSU_OUEN -> applyHaste(chicken, amplifier);
                    case YOME -> applyRider(chicken, PotionEffectType.NIGHT_VISION, 0);
                    case CHIKARAKOBU -> applyRider(chicken, PotionEffectType.STRENGTH, amplifier);
                    case MIHARIBAN -> alert(chicken);
                    default -> { /* Egg laying and genetics are handled at their respective events. */ }
                }
            }
        }
        lastAlert.keySet().retainAll(active);
        updateTaskState();
    }

    private void applyHaste(Chicken chicken, int amplifier) {
        double radius = config.hasteRadius();
        double squared = radius * radius;
        var source = chicken.getLocation();
        for (Player player : HasteNearbyPlayers.find(chicken.getWorld(), source, radius)) {
            if (HasteNearbyPlayers.contains(source, player.getLocation(), squared)) {
                apply(player, PotionEffectType.HASTE, amplifier);
            }
        }
    }

    private void applyRider(Chicken chicken, PotionEffectType type, int amplifier) {
        for (Entity passenger : chicken.getPassengers()) {
            if (passenger instanceof Player player) apply(player, type, amplifier);
        }
    }

    private void apply(org.bukkit.entity.LivingEntity entity, PotionEffectType type, int amplifier) {
        PotionEffect current = entity.getPotionEffect(type);
        // Vanilla night vision flashes below 10 seconds; refresh before entering that window.
        // On dismount the effect expires naturally, without removing another source's potion.
        int duration = type == PotionEffectType.NIGHT_VISION ? 240 : EFFECT_TICKS;
        int threshold = type == PotionEffectType.NIGHT_VISION ? 220 : EFFECT_REFRESH_THRESHOLD;
        // Respect other mechanics' stronger effects; stronger WWC expression may upgrade a weaker one.
        if (current != null && !co.surumene.wcl.api.traits.PotionRefreshDecision.shouldApply(
                current.getAmplifier(), current.getDuration(), amplifier, threshold)) return;
        entity.addPotionEffect(new PotionEffect(type, duration, amplifier, true, false, false));
    }

    private void alert(Chicken chicken) {
        double radius = config.alertRadius();
        double squared = radius * radius;
        double nearest = Double.POSITIVE_INFINITY;
        for (Entity entity : chicken.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof Monster || entity instanceof Fox || entity instanceof Ocelot)) continue;
            double distance = chicken.getLocation().distanceSquared(entity.getLocation());
            if (distance <= squared) nearest = Math.min(nearest, distance);
        }
        if (nearest == Double.POSITIVE_INFINITY) return;
        double proportion = Math.sqrt(nearest) / radius;
        long interval = Math.round(config.alertMinInterval() + proportion
                * (config.alertMaxInterval() - config.alertMinInterval()));
        long last = lastAlert.getOrDefault(chicken.getUniqueId(), Long.MIN_VALUE / 2);
        if (ticks - last < interval) return;
        chicken.getWorld().playSound(chicken.getLocation(), Sound.ENTITY_CHICKEN_HURT, 1.0f, 1.1f);
        lastAlert.put(chicken.getUniqueId(), ticks);
    }

    /** Enable scheduler management after initial entity restoration finishes. */
    public void start() {
        schedulerStarted = true;
        updateTaskState();
    }

    private void updateTaskState() {
        if (!schedulerStarted) return;
        boolean shouldRun = TraitTaskActivityPolicy.shouldRun(membership.size());
        if (shouldRun && task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this, 10L, 10L);
        } else if (!shouldRun && task != null) {
            task.cancel();
            task = null;
        }
        // Pausing the logical 10-tick clock must not preserve a Watchman
        // cooldown across a period with no eligible trait sources.
        if (TraitTaskActivityPolicy.shouldClearAlerts(membership.size())) {
            lastAlert.clear();
        }
    }

    public void shutdown() {
        schedulerStarted = false;
        if (task != null) task.cancel();
        task = null;
        lastAlert.clear();
        membership.clear();
    }
}
