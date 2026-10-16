package co.surumene.whatawonderfulchicken.service;

import co.surumene.whatawonderfulchicken.WhatAWonderfulChickenPlugin;
import co.surumene.whatawonderfulchicken.config.ConfigService;
import co.surumene.whatawonderfulchicken.breeding.BreedingParent;
import co.surumene.whatawonderfulchicken.breeding.WonderfulChickenBreedingOutcome;
import co.surumene.whatawonderfulchicken.breeding.WonderfulChickenBreedingService;
import co.surumene.whatawonderfulchicken.data.AncestorSnapshot;
import co.surumene.whatawonderfulchicken.data.Genetics;
import co.surumene.whatawonderfulchicken.data.Trait;
import co.surumene.whatawonderfulchicken.data.PedigreeData;
import co.surumene.whatawonderfulchicken.data.StatType;
import co.surumene.whatawonderfulchicken.data.WonderfulChickenData;
import co.surumene.whatawonderfulchicken.founder.FounderGenomeSynthesis;
import co.surumene.whatawonderfulchicken.founder.FounderOrigin;
import co.surumene.whatawonderfulchicken.founder.WonderfulChickenFounderSynthesizer;
import co.surumene.whatawonderfulchicken.genome.PhenotypeOrigin;
import co.surumene.whatawonderfulchicken.genome.WonderfulChickenDecodedPhenotype;
import co.surumene.whatawonderfulchicken.runtime.AdultAge;
import co.surumene.whatawonderfulchicken.runtime.AgeCurve;
import co.surumene.whatawonderfulchicken.runtime.AgeInjuryModifier;
import co.surumene.wgl.api.DecodeResult;
import co.surumene.wgl.api.SynthesisResult;
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
    private final LegacyGenomeMigrationCoordinator migrations;

    public WonderfulChickenService(WhatAWonderfulChickenPlugin plugin, ConfigService config, WonderfulChickenStore store) {
        this.plugin = plugin;
        this.config = config;
        this.store = store;
        this.migrations = new LegacyGenomeMigrationCoordinator(plugin, this, store);
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
        WonderfulChickenData data = store.load(chicken);
        if (data.hasGenomeModel()) {
            synchronizeRangePolicy(chicken);
        } else {
            // Keep the original normalized values intact until the legacy
            // snapshot has been synthesized and atomically committed.
            migrations.request(chicken);
            projectAttributes(chicken);
        }
        data = store.load(chicken);
        if (!config.persistCurrentStamina()) {
            data.currentStamina(data.value(StatType.STAMINA));
            store.save(chicken, data);
        }
        synchronizeBehaviorState(chicken, data);
        store.notifyStaminaState(chicken);
        store.notifyTraitState(chicken);
        store.notifyFollowState(chicken);
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
        store.notifyFollowUnload(chicken);
        migrations.invalidate(chicken);
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
        var engine = plugin.genomeLib().engine();
        WonderfulChickenFounderSynthesizer synthesizer =
                new WonderfulChickenFounderSynthesizer(engine, plugin.genomeProfile());
        FounderGenomeSynthesis synthesis =
                synthesizer.synthesize(FounderOrigin.NATURAL, ThreadLocalRandom.current().nextLong());
        return dataFromFounderSynthesis(synthesis, FounderOrigin.NATURAL);
    }

    /** Apply only after the WGL CPU-heavy work has completed off-thread. */
    public WonderfulChickenData dataFromFounderSynthesis(FounderGenomeSynthesis synthesis, FounderOrigin origin) {
        if (!(synthesis.result() instanceof SynthesisResult.Success success)) {
            SynthesisResult.Failure failure = (SynthesisResult.Failure) synthesis.result();
            throw new IllegalStateException(
                    "Natural Wonderful Chicken genome synthesis failed: "
                            + failure.reason() + " / " + failure.detail());
        }

        DecodeResult<?> decoded = success.decoded();
        WonderfulChickenDecodedPhenotype phenotype = (WonderfulChickenDecodedPhenotype) decoded.phenotype();
        var snapshot = phenotype.toSnapshot(
                decoded.identity(), origin == FounderOrigin.NATURAL
                        ? PhenotypeOrigin.NATURAL_FOUNDER : PhenotypeOrigin.CHICKEN_TRAP_FOUNDER);

        WonderfulChickenData data = new WonderfulChickenData();
        for (StatType stat : StatType.values()) {
            double normalized = snapshot.normalizedAbilities().get(stat);
            data.normalized(stat, normalized);
            data.value(stat, store.toValue(stat, normalized));
        }
        data.genome(success.genome());
        data.phenotypeSnapshot(snapshot);
        data.adultBiologicalTime(0L);
        data.currentStamina(data.value(StatType.STAMINA));
        data.bloodlineId(store.genomeMarker(data));
        data.generation(0);
        data.pedigree(PedigreeData.EMPTY);
        // Legacy genetics remain only as transition-era compatibility data.
        data.genetics(Genetics.random());
        data.currentStamina(data.effective(StatType.STAMINA, config.natureAdjustment()));
        return data;
    }

    public java.util.Optional<WonderfulChickenData> createBredData(
            Chicken parentA,
            Chicken parentB) {
        migrations.request(parentA);
        migrations.request(parentB);

        WonderfulChickenData a = store.load(parentA);
        WonderfulChickenData b = store.load(parentB);
        if (!a.hasGenomeModel() || !b.hasGenomeModel()) {
            return java.util.Optional.empty();
        }

        WonderfulChickenBreedingService breeding =
                new WonderfulChickenBreedingService(
                        () -> plugin.genomeLib().engine(),
                        plugin::genomeProfile);

        WonderfulChickenBreedingOutcome outcome = breeding.breed(
                new BreedingParent(breedingDisplayName(parentA), a),
                new BreedingParent(breedingDisplayName(parentB), b),
                ThreadLocalRandom.current().nextLong());
        return dataFromBreedingOutcome(selfSnapshot(parentA, a), selfSnapshot(parentB, b), a, b, outcome);
    }

    /**
     * Breed the immutable parent snapshots off-thread, then construct the
     * child's Bukkit-specific pedigree/state on the main server thread.
     */
    public boolean requestBredData(
            Chicken parentA, Chicken parentB,
            java.util.function.Consumer<java.util.Optional<WonderfulChickenData>> onComplete,
            java.util.function.Consumer<Exception> onFailure) {
        WonderfulChickenData a = store.load(parentA);
        WonderfulChickenData b = store.load(parentB);
        if (!a.hasGenomeModel() || !b.hasGenomeModel()) return false;
        var engine = plugin.genomeLib().engine();
        var profile = plugin.genomeProfile();
        String nameA = breedingDisplayName(parentA);
        String nameB = breedingDisplayName(parentB);
        AncestorSnapshot ancestryA = selfSnapshot(parentA, a);
        AncestorSnapshot ancestryB = selfSnapshot(parentB, b);
        WonderfulChickenBreedingService calculator =
                new WonderfulChickenBreedingService(() -> engine, () -> profile);
        long seed = ThreadLocalRandom.current().nextLong();
        return plugin.naturalFounders().submitCalculation(
                () -> calculator.breed(
                        new BreedingParent(nameA, a),
                        new BreedingParent(nameB, b), seed),
                outcome -> {
                    if (engine != plugin.genomeLib().engine()
                            || profile != plugin.genomeProfile()) {
                        onFailure.accept(new IllegalStateException(
                                "WGL configuration changed during breeding"));
                        return;
                    }
                    try {
                        onComplete.accept(dataFromBreedingOutcome(ancestryA, ancestryB, a, b, outcome));
                    } catch (RuntimeException error) {
                        onFailure.accept(error);
                    }
                },
                onFailure);
    }

    private java.util.Optional<WonderfulChickenData> dataFromBreedingOutcome(
            AncestorSnapshot ancestryA, AncestorSnapshot ancestryB,
            WonderfulChickenData a, WonderfulChickenData b,
            WonderfulChickenBreedingOutcome outcome) {
        if (outcome instanceof WonderfulChickenBreedingOutcome.Fallback) {
            return java.util.Optional.empty();
        }

        WonderfulChickenBreedingOutcome.Success success =
                (WonderfulChickenBreedingOutcome.Success) outcome;

        WonderfulChickenData child = new WonderfulChickenData();
        child.genome(success.genome());
        child.phenotypeSnapshot(success.phenotypeSnapshot());
        child.adultBiologicalTime(0L);

        for (StatType stat : StatType.values()) {
            double normalized =
                    success.phenotypeSnapshot().normalizedAbilities().get(stat);
            child.normalized(stat, normalized);
            child.value(stat, store.toValue(stat, normalized));
        }

        child.currentStamina(
                child.effective(StatType.STAMINA, config.natureAdjustment()));
        child.bloodlineId(success.marker());
        child.generation(Math.max(a.generation(), b.generation()) + 1);
        child.pedigree(new PedigreeData(
                ancestryA, ancestryB,
                a.pedigree().parentA(), a.pedigree().parentB(),
                b.pedigree().parentA(), b.pedigree().parentB()));

        // Retain a legacy shadow genotype only for backward/rollback compatibility.
        // It is not consulted when producing the Genome or Phenotype Snapshot.
        if (a.genetics() != null && b.genetics() != null) {
            child.genetics(Genetics.breed(
                    a.genetics(),
                    b.genetics(),
                    Math.min(1.0, config.geneticMutationRate()),
                    ThreadLocalRandom.current()));
        } else {
            child.genetics(Genetics.random());
        }

        return java.util.Optional.of(child);
    }

    private static String breedingDisplayName(Chicken chicken) {
        if (chicken.customName() != null) {
            String custom = PlainTextComponentSerializer.plainText()
                    .serialize(chicken.customName());
            if (!custom.isBlank()) return custom;
        }
        String name = chicken.getName();
        return name == null || name.isBlank() ? "Chicken" : name;
    }

    /**
     * Fill previously unknown parent snapshots when a real parent becomes available later.
     * The child's own genotype is never re-rolled after its one-time migration.
     * Called on demand, not by the periodic full-world reconciliation.
     */
    public void refreshPedigree(Chicken child) {
        if (!store.isWonderful(child)) return;
        Set<UUID> visiting = new HashSet<>();
        visiting.add(child.getUniqueId());
        WonderfulChickenData data = store.load(child);
        if (data.genetics() == null) {
            ensureGenetics(child, new HashSet<>());
            data = store.load(child);
        }
        PedigreeData original = data.pedigree();
        AncestorSnapshot parentA = recoverAncestor(child, original.parentA(), visiting);
        AncestorSnapshot parentB = recoverAncestor(child, original.parentB(), visiting);
        AncestorSnapshot grandparentAA = recoverAncestor(child, original.grandparentAA(), visiting);
        AncestorSnapshot grandparentAB = recoverAncestor(child, original.grandparentAB(), visiting);
        AncestorSnapshot grandparentBA = recoverAncestor(child, original.grandparentBA(), visiting);
        AncestorSnapshot grandparentBB = recoverAncestor(child, original.grandparentBB(), visiting);
        if (parentA == original.parentA() && parentB == original.parentB()
                && grandparentAA == original.grandparentAA()
                && grandparentAB == original.grandparentAB()
                && grandparentBA == original.grandparentBA()
                && grandparentBB == original.grandparentBB()) return;
        data.pedigree(new PedigreeData(parentA, parentB,
                grandparentAA, grandparentAB, grandparentBA, grandparentBB));
        store.save(child, data);
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
        if (snapshot == null || !WonderfulChickenStore.isLegacyLineageId(snapshot.bloodlineId())) {
            return snapshot;
        }
        // Only backfill when a real loaded ancestor's persisted ID matches.
        // Never infer a missing historical phenotype from shadow Genetics.
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Chicken possible : world.getEntitiesByClass(Chicken.class)) {
                if (possible == child || !store.isWonderful(possible)) continue;
                if (!snapshot.bloodlineId().equals(store.legacyBloodlineId(possible))) continue;
                WonderfulChickenData parentData = store.read(possible);
                AncestorSnapshot verified = snapshot.withBloodlineId(parentData.bloodlineId());
                if (verified.genetics() == null) {
                    Genetics genes = ensureGenetics(possible, visiting);
                    if (genes != null) verified = verified.withGenetics(genes);
                }
                if (verified.personality() == null && parentData.phenotypeSnapshot() != null) {
                    var phenotype = parentData.phenotypeSnapshot();
                    verified = verified.withPhenotype(phenotype.personality(),
                            phenotype.expressedTraits().stream().map(e -> e.trait()).toList(),
                            phenotype.divineLineageExpressed());
                }
                return verified;
            }
        }
        return snapshot;
    }

    public boolean isRegistered(Chicken chicken) {
        return loaded.get(chicken.getUniqueId()) == chicken;
    }

    public void retryLegacyMigrations() {
        migrations.retryLoaded();
    }

    public void shutdown() {
        migrations.shutdown();
    }

    public double effective(WonderfulChickenData data, StatType stat) {
        if (!data.hasGenomeModel() || data.adultBiologicalTime() == 0L) {
            // Immature chickens do not progress through the adult curve.
            return data.effective(stat, config.natureAdjustment());
        }
        var curve = AgeCurve.from(
                data.phenotypeSnapshot().developmentFactors(),
                config.ageGrowthDays(), config.agePeakDays(), config.ageAgingDays());
        double age = AdultAge.days(
                data.adultBiologicalTime(), plugin.biologicalClock().currentTime());
        double normalized = AgeInjuryModifier.adjust(
                stat, data.normalized(stat), age, curve,
                data.phenotypeSnapshot().injuries(),
                config.ageSensitivity(stat)).effectiveNormalized();
        double value = store.toValue(stat, normalized);
        double adjustment = config.natureAdjustment()
                * (data.hasTrait(Trait.KUSE_MASHI) ? 1.5 : 1.0);
        double effective = value * data.nature().multiplier(stat, adjustment);
        return stat == StatType.MAX_HEALTH
                ? Math.max(1.0, Math.round(effective)) : effective;
    }

    public double adultAgeDays(WonderfulChickenData data) {
        return AdultAge.days(
                data.adultBiologicalTime(), plugin.biologicalClock().currentTime());
    }

    private void initializeAdultAge(Chicken chicken, WonderfulChickenData data) {
        long start = AdultAge.start(
                chicken.isAdult(), data.adultBiologicalTime(),
                plugin.biologicalClock().currentTime());
        if (start != data.adultBiologicalTime()) data.adultBiologicalTime(start);
    }

    public void initialize(Chicken chicken, WonderfulChickenData data) {
        if (data.genetics() == null) data.genetics(Genetics.random());
        initializeAdultAge(chicken, data);
        store.save(chicken, data);
        loaded.put(chicken.getUniqueId(), chicken);
        migrations.request(chicken);
        projectAttributes(chicken);
        chicken.setHealth(effective(data, StatType.MAX_HEALTH));
        synchronizeBehaviorState(chicken, data);
    }

    public void synchronizeRangePolicy(Chicken chicken) {
        if (!store.isWonderful(chicken)) return;
        WonderfulChickenData data = store.load(chicken);
        if (data.hasGenomeModel()) {
            // Snapshot is immutable historical phenotype. Config range changes may change
            // its projected gameplay value, but never rewrite the stored normalized phenotype.
            for (StatType stat : StatType.values()) {
                double normalized = data.phenotypeSnapshot().normalizedAbilities().get(stat);
                data.normalized(stat, normalized);
                data.value(stat, store.toValue(stat, normalized));
            }
        } else if (config.rangeChangePolicy().equals("preserve-normalized")) {
            for (StatType stat : StatType.values()) {
                data.value(stat, store.toValue(stat, data.normalized(stat)));
            }
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
        if (chicken.isAdult() && data.adultBiologicalTime() == 0L) {
            // One-time transition from baby (or legacy v2.0 snapshot) to adult.
            data = store.load(chicken);
            initializeAdultAge(chicken, data);
            store.save(chicken, data);
        }
        double effectiveHealth = effective(data, StatType.MAX_HEALTH);
        setAttribute(chicken, Attribute.MAX_HEALTH, effectiveHealth);
        setAttribute(chicken, Attribute.SCALE, effective(data, StatType.SIZE));
        setAttribute(chicken, Attribute.STEP_HEIGHT, effective(data, StatType.STEP_HEIGHT));
        setAttribute(chicken, Attribute.JUMP_STRENGTH, jumpVelocityForHeight(effective(data, StatType.JUMP_STRENGTH)));
        setAttribute(chicken, Attribute.MOVEMENT_SPEED, Math.max(0.001, effective(data, StatType.GROUND_SPEED) / MOVEMENT_ATTRIBUTE_BLOCKS_PER_SECOND));
        if (chicken.getHealth() > effectiveHealth) chicken.setHealth(effectiveHealth);
        double effectiveStamina = effective(data, StatType.STAMINA);
        if (data.currentStamina() > effectiveStamina) {
            // Keep the persisted current pool at or below the aging-adjusted capacity.
            store.setCurrentStamina(chicken, effectiveStamina);
        }
        ItemStack head = data.headItem();
        if (chicken.getEquipment() != null) {
            if (!Objects.equals(chicken.getEquipment().getHelmet(), head)) chicken.getEquipment().setHelmet(head);
            if (chicken.getEquipment().getHelmetDropChance() != 0.0f) chicken.getEquipment().setHelmetDropChance(0.0f);
        }
        // Aging can change maximum stamina even when the stored pool is unchanged.
        store.notifyStaminaState(chicken);
    }

    /** @return true only when an externally changed helmet was persisted. */
    public boolean captureHeadEquipment(Chicken chicken) {
        if (!store.isWonderful(chicken) || chicken.getEquipment() == null) return false;
        ItemStack actual = chicken.getEquipment().getHelmet();
        if (Objects.equals(store.read(chicken).headItem(), actual)) return false;
        WonderfulChickenData data = store.load(chicken);
        data.headItem(actual);
        store.save(chicken, data);
        return true;
    }

    public void synchronizeBehaviorState(Chicken chicken, WonderfulChickenData data) {
        chicken.setAI(true);
        chicken.getPathfinder().stopPathfinding();
        boolean wander = data.behaviorMode() == co.surumene.whatawonderfulchicken.data.BehaviorMode.WANDER;
        chicken.setAware(wander);
    }

    public String displayBloodlineId(String bloodlineId) {
        if (bloodlineId == null || bloodlineId.isBlank()) return "不明";
        if (WonderfulChickenStore.isLegacyLineageId(bloodlineId)) {
            return "旧ID:" + bloodlineId.substring(0, 8);
        }
        return bloodlineId;
    }

    public double jumpVelocityForHeight(double height) {
        return 0.42 * Math.sqrt(Math.max(0.0, height) / 1.25);
    }

    public AncestorSnapshot selfSnapshot(Chicken chicken, WonderfulChickenData data) {
        String name = "";
        if (chicken.customName() != null) name = PlainTextComponentSerializer.plainText().serialize(chicken.customName());
        var phenotype = data.phenotypeSnapshot();
        return new AncestorSnapshot(name, data.generation(), data.bloodlineId(),
                data.genetics(),
                phenotype == null ? null : phenotype.personality(),
                phenotype == null ? java.util.List.of() :
                    phenotype.expressedTraits().stream().map(entry -> entry.trait()).toList(),
                phenotype != null && phenotype.divineLineageExpressed());
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