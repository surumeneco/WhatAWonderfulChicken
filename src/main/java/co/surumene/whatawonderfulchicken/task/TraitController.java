package co.surumene.whatawonderfulchicken.task;

import co.surumene.whatawonderfulchicken.config.ConfigService;
import co.surumene.whatawonderfulchicken.data.Trait;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenService;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenStore;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Chicken;
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
    static final Sound ALERT_SOUND = Sound.ENTITY_CHICKEN_HURT;
    private static final int EFFECT_TICKS = 80;
    private static final int EFFECT_REFRESH_THRESHOLD = 40;
    private final WonderfulChickenService chickens;
    private final WonderfulChickenStore store;
    private final ConfigService config;
    private final Map<UUID, Long> lastAlert = new HashMap<>();
    private long ticks;

    public TraitController(WonderfulChickenService chickens, WonderfulChickenStore store, ConfigService config) {
        this.chickens = chickens;
        this.store = store;
        this.config = config;
    }

    @Override
    public void run() {
        ticks += 10L;
        Set<UUID> active = new HashSet<>();
        for (Chicken chicken : chickens.loadedChickens()) {
            active.add(chicken.getUniqueId());
            Trait trait = store.read(chicken).trait();
            if (trait == null) continue;
            switch (trait) {
                case HINOTORI -> apply(chicken, PotionEffectType.FIRE_RESISTANCE);
                case WATAGE -> apply(chicken, PotionEffectType.SLOW_FALLING);
                case FUKUTSU -> apply(chicken, PotionEffectType.REGENERATION);
                case SAIKUTSU_OUEN -> applyHaste(chicken);
                case YOME -> applyRider(chicken, PotionEffectType.NIGHT_VISION);
                case CHIKARAKOBU -> applyRider(chicken, PotionEffectType.STRENGTH);
                case MIHARIBAN -> alert(chicken);
                default -> { /* Egg laying and genetics are handled at their respective events. */ }
            }
        }
        lastAlert.keySet().retainAll(active);
    }

    private void applyHaste(Chicken chicken) {
        double radius = config.hasteRadius();
        double squared = radius * radius;
        for (Player player : chicken.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(chicken.getLocation()) <= squared) {
                apply(player, PotionEffectType.HASTE);
            }
        }
    }

    private void applyRider(Chicken chicken, PotionEffectType type) {
        for (Entity passenger : chicken.getPassengers()) {
            if (passenger instanceof Player player) apply(player, type);
        }
    }

    private void apply(org.bukkit.entity.LivingEntity entity, PotionEffectType type) {
        PotionEffect current = entity.getPotionEffect(type);
        // Vanilla night vision flashes below 10 seconds; refresh before entering that window.
        // On dismount the effect expires naturally, without removing another source's potion.
        int duration = type == PotionEffectType.NIGHT_VISION ? 240 : EFFECT_TICKS;
        int threshold = type == PotionEffectType.NIGHT_VISION ? 220 : EFFECT_REFRESH_THRESHOLD;
        // Respect external stronger or longer-lasting effects. Do not add amplifiers.
        if (current != null && (current.getAmplifier() > 0 || current.getDuration() > threshold)) return;
        entity.addPotionEffect(new PotionEffect(type, duration, 0, true, false, false));
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
        chicken.getWorld().playSound(chicken.getLocation(), ALERT_SOUND, 1.0f, 1.1f);
        lastAlert.put(chicken.getUniqueId(), ticks);
    }

    public void shutdown() {
        lastAlert.clear();
    }
}
