package co.surumene.whatawonderfulchicken.service;

import co.surumene.whatawonderfulchicken.WhatAWonderfulChickenPlugin;
import co.surumene.whatawonderfulchicken.config.ConfigService;
import co.surumene.whatawonderfulchicken.data.AncestorSnapshot;
import co.surumene.whatawonderfulchicken.data.Genetics;
import co.surumene.whatawonderfulchicken.data.Trait;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class WonderfulChickenService {
    private static final double MOVEMENT_ATTRIBUTE_BLOCKS_PER_SECOND = 42.157;
    private final WhatAWonderfulChickenPlugin plugin;
    private final ConfigService config;
    private final WonderfulChickenStore store;
    private final Map<UUID, Chicken> loaded = new HashMap<>();

    public WonderfulChickenService(WhatAWonderfulChickenPlugin plugin, ConfigService config, WonderfulChickenStore store) {
        this.plugin = plugin;
        this.config = config;
        this.store = store;
    }

    public boolean isWonderful(Entity entity) {
        return entity instanceof Chicken chicken && store.isWonderful(chicken);
    }

    public boolean registerLoaded(Chicken chicken) {
        if (!store.isWonderful(chicken)) return false;
        Chicken previous = loaded.put(chicken.getUniqueId(), chicken);
        if (previous == chicken) return false;
        if (previous != null) store.invalidate(previous);
        store.invalidate(chicken);
        ensureGenetics(chicken, new HashSet<>());
        synchronizeRangePolicy(chicken);
        WonderfulChickenData data = store.load(chicken);
        if (!config.persistCurrentStamina()) {
            data.currentStamina(data.value(StatType.STAMINA));
            store.save(chicken, data);
        }
        synchronizeBehaviorState(chicken, data);
        return true;
    }

    /**
     * Also discovers chickens reconstructed by third-party plugins after spawn events.
     * Existing chickens are not reinitialized and their equipment remains in PDC.
     */
    public Collection<Chicken> reconcileLoadedWorlds() {
        List<Chicken> newlyRegistered = new ArrayList<>();
        Bukkit.getWorlds().forEach(world -> world.getEntitiesByClass(Chicken.class).forEach(chicken -> {
            if (registerLoaded(chicken)) newlyRegistered.add(chicken);
        }));
        return newlyRegistered;
    }

    public void unregisterLoaded(Chicken chicken) {
        loaded.remove(chicken.getUniqueId(), chicken);
        store.invalidate(chicken);
    }

    public boolean isSuperseded(Chicken chicken) {
        Chicken current = loaded.get(chicken.getUniqueId());
        return current != null && current != chicken;
    }

    public Collection<Chicken> loadedChickens() {
        List<Chicken> result = new ArrayList<>();
        Iterator<Map.Entry<UUID, Chicken>> iterator = loaded.entrySet().iterator();
        while (iterator.hasNext()) {
            Chicken chicken = iterator.next().getValue();
            if (!chicken.isValid() || !store.isWonderful(chicken)) {
                iterator.remove();
                store.invalidate(chicken);
                continue;
            }
            result.add(chicken);
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
        data.genetics(Genetics.random());
        data.currentStamina(data.effective(StatType.STAMINA, config.natureAdjustment()));
        return data;
    }

    public WonderfulChickenData createBredData(Chicken parentA, Chicken parentB) {
        ensureGenetics(parentA, new HashSet<>());
        ensureGenetics(parentB, new HashSet<>());
        WonderfulChickenData a = store.load(parentA);
        WonderfulChickenData b = store.load(parentB);
        var random = ThreadLocalRandom.current();
        double mutationMultiplier = (a.trait() == Trait.HATENKO ? 2.0 : 1.0)
                * (b.trait() == Trait.HATENKO ? 2.0 : 1.0);
        WonderfulChickenData child = new WonderfulChickenData();
        child.genetics(Genetics.breed(a.genetics(), b.genetics(),
                Math.min(1.0, config.geneticMutationRate() * mutationMultiplier), random));

        // Guaranteed direct slots have priority over the single independent stat mutation.
        Map<StatType, Double> guaranteed = new EnumMap<>(StatType.class);
        List<StatType> eligible = new ArrayList<>(List.of(StatType.values()));
        if (a.trait() == Trait.JIKIDEN) {
            StatType chosen = eligible.remove(random.nextInt(eligible.size()));
            guaranteed.put(chosen, a.normalized(chosen));
        }
        if (b.trait() == Trait.JIKIDEN) {
            StatType chosen = eligible.remove(random.nextInt(eligible.size()));
            guaranteed.put(chosen, b.normalized(chosen));
        }
        StatType mutated = random.nextDouble() < Math.min(1.0, config.statMutationRate() * mutationMultiplier)
                ? eligible.get(random.nextInt(eligible.size())) : null;

        for (StatType stat : StatType.values()) {
            double normalized;
            if (guaranteed.containsKey(stat)) {
                // This is the only breeding path which can retain wild-only normalized values > 1.
                normalized = guaranteed.get(stat);
            } else if (stat == mutated) {
                normalized = random.nextDouble();
            } else {
                double av = Math.min(1.0, Math.max(0.0, a.normalized(stat)));
                double bv = Math.min(1.0, Math.max(0.0, b.normalized(stat)));
                if (random.nextDouble() < config.directInheritanceRate()) {
                    normalized = random.nextBoolean() ? av : bv;
                } else {
                    double mean = (av + bv) / 2.0;
                    double sigma = Math.max(config.breedingMinStdDev(), Math.abs(av - bv) * config.breedingSpreadFactor());
                    normalized = truncatedGaussian(mean, sigma, 0.0, 1.0);
                }
            }
            double value = store.toValue(stat, normalized);
            child.value(stat, value);
            child.normalized(stat, stat == StatType.MAX_HEALTH ? store.toNormalized(stat, value) : normalized);
        }
        child.currentStamina(child.effective(StatType.STAMINA, config.natureAdjustment()));
        child.bloodlineId(UUID.randomUUID().toString());
        child.generation(Math.max(a.generation(), b.generation()) + 1);
        child.pedigree(new PedigreeData(
                selfSnapshot(parentA, a), selfSnapshot(parentB, b),
                a.pedigree().parentA(), a.pedigree().parentB(),
                b.pedigree().parentA(), b.pedigree().parentB()));
        return child;
    }

    /** One-time, persistent upgrade of a loaded legacy chicken. Never guesses ancestor genes from an ID. */
    private Genetics ensureGenetics(Chicken chicken, Set<UUID> visiting) {
        WonderfulChickenData data = store.load(chicken);
        if (data.genetics() != null) return data.genetics();
        if (!visiting.add(chicken.getUniqueId())) return null;
        try {
            PedigreeData p = data.pedigree();
            AncestorSnapshot a = recoverAncestor(chicken, p.parentA(), visiting);
            AncestorSnapshot b = recoverAncestor(chicken, p.parentB(), visiting);
            Genetics genes = a != null && b != null && a.genetics() != null && b.genetics() != null
                    ? Genetics.breed(a.genetics(), b.genetics(),
                        Math.min(1.0, config.geneticMutationRate()
                            * (a.genetics().trait() == Trait.HATENKO ? 2.0 : 1.0)
                            * (b.genetics().trait() == Trait.HATENKO ? 2.0 : 1.0)),
                        ThreadLocalRandom.current())
                    : Genetics.random();
            data.genetics(genes);
            if (a != p.parentA() || b != p.parentB()) {
                data.pedigree(new PedigreeData(a, b, p.grandparentAA(), p.grandparentAB(),
                        p.grandparentBA(), p.grandparentBB()));
            }
            store.save(chicken, data);
            return genes;
        } finally {
            visiting.remove(chicken.getUniqueId());
        }
    }

    private AncestorSnapshot recoverAncestor(Chicken child, AncestorSnapshot snapshot, Set<UUID> visiting) {
        if (snapshot == null || snapshot.genetics() != null) return snapshot;
        if (snapshot.bloodlineId() == null || snapshot.bloodlineId().isBlank()) return snapshot;
        // The old snapshot stores only name, generation and bloodline ID. Locate a real loaded parent.
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Chicken possible : world.getEntitiesByClass(Chicken.class)) {
                if (possible == child || !store.isWonderful(possible)) continue;
                if (!snapshot.bloodlineId().equals(store.read(possible).bloodlineId())) continue;
                Genetics genes = ensureGenetics(possible, visiting);
                if (genes != null) return snapshot.withGenetics(genes);
            }
        }
        return snapshot;
    }

    public double effective(WonderfulChickenData data, StatType stat) {
        return data.effective(stat, config.natureAdjustment());
    }

    public void initialize(Chicken chicken, WonderfulChickenData data) {
        if (data.genetics() == null) data.genetics(Genetics.random());
        store.save(chicken, data);
        loaded.put(chicken.getUniqueId(), chicken);
        projectAttributes(chicken);
        chicken.setHealth(effective(data, StatType.MAX_HEALTH));
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
        data.currentStamina(Math.min(data.currentStamina(), effective(data, StatType.STAMINA)));
        store.save(chicken, data);
        projectAttributes(chicken);
    }

    public void resyncAllLoaded() {
        for (Chicken chicken : loadedChickens()) synchronizeRangePolicy(chicken);
    }

    public void projectAttributes(Chicken chicken) {
        if (!store.isWonderful(chicken)) return;
        WonderfulChickenData data = store.read(chicken);
        double effectiveHealth = effective(data, StatType.MAX_HEALTH);
        setAttribute(chicken, Attribute.MAX_HEALTH, effectiveHealth);
        setAttribute(chicken, Attribute.SCALE, effective(data, StatType.SIZE));
        setAttribute(chicken, Attribute.STEP_HEIGHT, data.value(StatType.STEP_HEIGHT));
        setAttribute(chicken, Attribute.JUMP_STRENGTH, jumpVelocityForHeight(effective(data, StatType.JUMP_STRENGTH)));
        setAttribute(chicken, Attribute.MOVEMENT_SPEED, Math.max(0.001, effective(data, StatType.GROUND_SPEED) / MOVEMENT_ATTRIBUTE_BLOCKS_PER_SECOND));
        if (chicken.getHealth() > effectiveHealth) chicken.setHealth(effectiveHealth);
        ItemStack head = data.headItem();
        if (chicken.getEquipment() != null) {
            if (!Objects.equals(chicken.getEquipment().getHelmet(), head)) chicken.getEquipment().setHelmet(head);
            if (chicken.getEquipment().getHelmetDropChance() != 0.0f) chicken.getEquipment().setHelmetDropChance(0.0f);
        }
    }

    public void captureHeadEquipment(Chicken chicken) {
        if (!store.isWonderful(chicken) || chicken.getEquipment() == null) return;
        ItemStack actual = chicken.getEquipment().getHelmet();
        if (Objects.equals(store.read(chicken).headItem(), actual)) return;
        WonderfulChickenData data = store.load(chicken);
        data.headItem(actual);
        store.save(chicken, data);
    }

    public void synchronizeBehaviorState(Chicken chicken, WonderfulChickenData data) {
        chicken.setAI(true);
        chicken.getPathfinder().stopPathfinding();
        boolean wander = data.behaviorMode() == co.surumene.whatawonderfulchicken.data.BehaviorMode.WANDER;
        chicken.setAware(wander);
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
        return new AncestorSnapshot(name, data.generation(), data.bloodlineId(), data.genetics());
    }

    public WonderfulChickenStore store() { return store; }

    private void setAttribute(Chicken chicken, Attribute attribute, double value) {
        AttributeInstance instance = chicken.getAttribute(attribute);
        if (instance == null) {
            plugin.getLogger().fine("Attribute unavailable on chicken: " + attribute.key().asString());
            return;
        }
        double clamped = Math.max(instance.getAttribute().getDefaultValue() == 0 ? 0.00001 : 0.0, value);
        if (Double.compare(instance.getBaseValue(), clamped) == 0) return;
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