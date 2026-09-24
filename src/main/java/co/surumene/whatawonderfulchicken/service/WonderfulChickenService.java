package co.surumene.whatawonderfulchicken.service;

import co.surumene.whatawonderfulchicken.WhatAWonderfulChickenPlugin;
import co.surumene.whatawonderfulchicken.config.ConfigService;
import co.surumene.whatawonderfulchicken.data.AncestorSnapshot;
import co.surumene.whatawonderfulchicken.data.PedigreeData;
import co.surumene.whatawonderfulchicken.data.StatType;
import co.surumene.whatawonderfulchicken.data.WonderfulChickenData;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class WonderfulChickenService {
    private static final double MOVEMENT_ATTRIBUTE_BLOCKS_PER_SECOND = 42.157;
    private final WhatAWonderfulChickenPlugin plugin;
    private final ConfigService config;
    private final WonderfulChickenStore store;
    private final Set<UUID> loaded = new HashSet<>();

    public WonderfulChickenService(WhatAWonderfulChickenPlugin plugin, ConfigService config, WonderfulChickenStore store) {
        this.plugin = plugin;
        this.config = config;
        this.store = store;
    }

    public boolean isWonderful(Entity entity) {
        return entity instanceof Chicken chicken && store.isWonderful(chicken);
    }

    public void registerLoaded(Chicken chicken) {
        if (!store.isWonderful(chicken)) return;
        loaded.add(chicken.getUniqueId());
        synchronizeRangePolicy(chicken);
        WonderfulChickenData data = store.load(chicken);
        if (!config.persistCurrentStamina()) {
            data.currentStamina(data.value(StatType.STAMINA));
            store.save(chicken, data);
        }
        synchronizeBehaviorState(chicken, data);
    }

    public void unregisterLoaded(Chicken chicken) {
        loaded.remove(chicken.getUniqueId());
    }

    public Collection<Chicken> loadedChickens() {
        List<Chicken> result = new ArrayList<>();
        loaded.removeIf(uuid -> Bukkit.getEntity(uuid) == null);
        for (UUID uuid : List.copyOf(loaded)) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity instanceof Chicken chicken && chicken.isValid() && store.isWonderful(chicken)) result.add(chicken);
        }
        return result;
    }

    public void scanLoadedWorlds() {
        loaded.clear();
        Bukkit.getWorlds().forEach(world -> world.getEntitiesByClass(Chicken.class).forEach(this::registerLoaded));
    }

    public boolean rollNaturalConversion() {
        return ThreadLocalRandom.current().nextDouble() < config.naturalChance();
    }

    public WonderfulChickenData createNaturalData() {
        WonderfulChickenData data = new WonderfulChickenData();
        for (StatType stat : StatType.values()) {
            double normalized = truncatedGaussian(config.naturalMean(), config.naturalStdDev(), 0.0, config.naturalMaxNormalized());
            double value = store.toValue(stat, normalized);
            data.value(stat, value);
            data.normalized(stat, stat == StatType.MAX_HEALTH ? store.toNormalized(stat, value) : normalized);
        }
        data.currentStamina(data.value(StatType.STAMINA));
        data.bloodlineId(UUID.randomUUID().toString());
        data.generation(0);
        data.pedigree(PedigreeData.EMPTY);
        return data;
    }

    public WonderfulChickenData createBredData(Chicken parentA, Chicken parentB) {
        WonderfulChickenData a = store.load(parentA);
        WonderfulChickenData b = store.load(parentB);
        WonderfulChickenData child = new WonderfulChickenData();
        for (StatType stat : StatType.values()) {
            double av = Math.min(1.0, Math.max(0.0, a.normalized(stat)));
            double bv = Math.min(1.0, Math.max(0.0, b.normalized(stat)));
            double normalized;
            if (ThreadLocalRandom.current().nextDouble() < config.directInheritanceRate()) {
                normalized = ThreadLocalRandom.current().nextBoolean() ? av : bv;
            } else {
                double mean = (av + bv) / 2.0;
                double sigma = Math.max(config.breedingMinStdDev(), Math.abs(av - bv) * config.breedingSpreadFactor());
                normalized = truncatedGaussian(mean, sigma, 0.0, 1.0);
            }
            double value = store.toValue(stat, normalized);
            child.value(stat, value);
            child.normalized(stat, stat == StatType.MAX_HEALTH ? store.toNormalized(stat, value) : normalized);
        }
        child.currentStamina(child.value(StatType.STAMINA));
        child.bloodlineId(UUID.randomUUID().toString());
        child.generation(Math.max(a.generation(), b.generation()) + 1);
        child.pedigree(new PedigreeData(
                selfSnapshot(parentA, a), selfSnapshot(parentB, b),
                a.pedigree().parentA(), a.pedigree().parentB(),
                b.pedigree().parentA(), b.pedigree().parentB()));
        return child;
    }

    public void initialize(Chicken chicken, WonderfulChickenData data) {
        store.save(chicken, data);
        loaded.add(chicken.getUniqueId());
        projectAttributes(chicken);
        chicken.setHealth(data.value(StatType.MAX_HEALTH));
        synchronizeBehaviorState(chicken, data);
    }

    public void synchronizeRangePolicy(Chicken chicken) {
        if (!store.isWonderful(chicken)) return;
        WonderfulChickenData data = store.load(chicken);
        if (config.rangeChangePolicy().equals("preserve-normalized")) {
            for (StatType stat : StatType.values()) data.value(stat, store.toValue(stat, data.normalized(stat)));
        } else {
            for (StatType stat : StatType.values()) {
                double value = stat.canonicalizeValue(data.value(stat));
                data.value(stat, value);
                data.normalized(stat, store.toNormalized(stat, value));
            }
        }
        data.currentStamina(Math.min(data.currentStamina(), data.value(StatType.STAMINA)));
        store.save(chicken, data);
        projectAttributes(chicken);
    }

    public void resyncAllLoaded() {
        for (Chicken chicken : loadedChickens()) synchronizeRangePolicy(chicken);
    }

    public void projectAttributes(Chicken chicken) {
        if (!store.isWonderful(chicken)) return;
        WonderfulChickenData data = store.load(chicken);
        setAttribute(chicken, Attribute.MAX_HEALTH, data.value(StatType.MAX_HEALTH));
        setAttribute(chicken, Attribute.SCALE, data.value(StatType.SIZE));
        setAttribute(chicken, Attribute.STEP_HEIGHT, data.value(StatType.STEP_HEIGHT));
        setAttribute(chicken, Attribute.JUMP_STRENGTH, jumpVelocityForHeight(data.value(StatType.JUMP_STRENGTH)));
        setAttribute(chicken, Attribute.MOVEMENT_SPEED, Math.max(0.001, data.value(StatType.GROUND_SPEED) / MOVEMENT_ATTRIBUTE_BLOCKS_PER_SECOND));
        if (chicken.getHealth() > data.value(StatType.MAX_HEALTH)) chicken.setHealth(data.value(StatType.MAX_HEALTH));
        ItemStack head = data.headItem();
        if (chicken.getEquipment() != null) {
            chicken.getEquipment().setHelmet(head);
            chicken.getEquipment().setHelmetDropChance(0.0f);
        }
    }

    public void captureHeadEquipment(Chicken chicken) {
        if (!store.isWonderful(chicken) || chicken.getEquipment() == null) return;
        WonderfulChickenData data = store.load(chicken);
        ItemStack actual = chicken.getEquipment().getHelmet();
        data.headItem(actual == null || actual.isEmpty() ? null : actual.clone());
        store.save(chicken, data);
    }

    public void synchronizeBehaviorState(Chicken chicken, WonderfulChickenData data) {
        chicken.setAI(true);
        boolean wander = data.behaviorMode() == co.surumene.whatawonderfulchicken.data.BehaviorMode.WANDER;
        chicken.setAware(wander);
        if (!wander) chicken.getPathfinder().stopPathfinding();
    }

    public String displayBloodlineId(String bloodlineId) {
        if (bloodlineId == null || bloodlineId.isBlank()) return "-";
        String compact = bloodlineId.replace("-", "");
        return compact.substring(0, Math.min(8, compact.length()));
    }

    public double jumpVelocityForHeight(double height) {
        return 0.42 * Math.sqrt(Math.max(0.0, height) / 1.25);
    }

    public AncestorSnapshot selfSnapshot(Chicken chicken, WonderfulChickenData data) {
        String name = "";
        if (chicken.customName() != null) name = PlainTextComponentSerializer.plainText().serialize(chicken.customName());
        return new AncestorSnapshot(name, data.generation(), data.bloodlineId());
    }

    public WonderfulChickenStore store() { return store; }

    private void setAttribute(Chicken chicken, Attribute attribute, double value) {
        AttributeInstance instance = chicken.getAttribute(attribute);
        if (instance == null) {
            plugin.getLogger().fine("Attribute unavailable on chicken: " + attribute.key().asString());
            return;
        }
        double clamped = Math.max(instance.getAttribute().getDefaultValue() == 0 ? 0.00001 : 0.0, value);
        try { instance.setBaseValue(clamped); } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Could not apply " + attribute.key().asString() + "=" + value + ": " + ex.getMessage());
        }
    }

    private static double truncatedGaussian(double mean, double stdDev, double min, double max) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        while (true) {
            double value = mean + random.nextGaussian() * stdDev;
            if (value >= min && value <= max) return value;
        }
    }
}