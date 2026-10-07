package co.surumene.whatawonderfulchicken.migration;

import co.surumene.whatawonderfulchicken.data.DevelopmentFactor;
import co.surumene.whatawonderfulchicken.data.ExpressedTrait;
import co.surumene.whatawonderfulchicken.data.Nature;
import co.surumene.whatawonderfulchicken.data.PersonalityFactor;
import co.surumene.whatawonderfulchicken.data.PhenotypeSnapshot;
import co.surumene.whatawonderfulchicken.data.StatType;
import co.surumene.whatawonderfulchicken.data.Trait;
import co.surumene.whatawonderfulchicken.data.TraitStrength;
import co.surumene.whatawonderfulchicken.data.WonderfulChickenData;
import co.surumene.whatawonderfulchicken.founder.FounderOrigin;
import co.surumene.whatawonderfulchicken.founder.FounderTarget;
import co.surumene.whatawonderfulchicken.founder.WonderfulChickenFounderTargetGenerator;
import co.surumene.whatawonderfulchicken.founder.WonderfulChickenSynthesisTarget;
import co.surumene.whatawonderfulchicken.genome.WonderfulChickenDecodedPhenotype;
import co.surumene.whatawonderfulchicken.genome.WonderfulChickenGenomeProfile;
import co.surumene.wgl.api.DecodeResult;
import co.surumene.wgl.api.GenomeEngine;
import co.surumene.wgl.api.GenomeRandom;
import co.surumene.wgl.api.SynthesisContext;
import co.surumene.wgl.api.SynthesisResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

public final class LegacyChickenGenomeMigrator {
    private final GenomeEngine engine;
    private final WonderfulChickenGenomeProfile profile;
    private final WonderfulChickenFounderTargetGenerator founderTargets;

    public LegacyChickenGenomeMigrator(
            GenomeEngine engine,
            WonderfulChickenGenomeProfile profile) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.founderTargets =
                new WonderfulChickenFounderTargetGenerator(profile.synthesisSettings());
    }

    public MigrationResult migrate(
            WonderfulChickenData legacy,
            long adultBiologicalTime,
            long seed) {
        Objects.requireNonNull(legacy, "legacy");
        if (adultBiologicalTime < 0L) {
            throw new IllegalArgumentException("adultBiologicalTime must be >= 0");
        }
        GenomeRandom random = engine.standardRandom(seed);
        MigrationPlan plan = migrationPlan(legacy, random);

        WonderfulChickenSynthesisTarget synthesisTarget =
                WonderfulChickenSynthesisTarget.forMigration(
                        plan.target(),
                        plan.extraordinaryAllowed(),
                        plan.divineSupplyAllowed(),
                        profile.settings(),
                        profile.synthesisSettings(),
                        random);

        SynthesisResult result = engine.synthesize(
                profile,
                profile.backbone(),
                synthesisTarget,
                SynthesisContext.defaults(),
                random);
        if (!(result instanceof SynthesisResult.Success success)) {
            SynthesisResult.Failure failure = (SynthesisResult.Failure) result;
            throw new IllegalStateException(
                    "legacy WWC genome migration failed: "
                            + failure.reason() + " / " + failure.detail());
        }

        DecodeResult<WonderfulChickenDecodedPhenotype> decoded =
                engine.decode(profile, success.genome());
        WonderfulChickenDecodedPhenotype phenotype = decoded.phenotype();

        EnumMap<StatType, Double> exactAbilities = new EnumMap<>(StatType.class);
        for (StatType stat : StatType.values()) {
            exactAbilities.put(stat, legacy.normalized(stat));
        }

        PhenotypeSnapshot snapshot = new PhenotypeSnapshot(
                decoded.identity(),
                exactAbilities,
                phenotype.personalityFactors(),
                legacy.nature(),
                plan.target().traits(),
                phenotype.developmentFactors(),
                phenotype.injuries(),
                phenotype.divineLineage().totalScore(),
                phenotype.divineLineage().expressed());

        return new MigrationResult(success.genome(), snapshot, adultBiologicalTime);
    }

    private MigrationPlan migrationPlan(WonderfulChickenData legacy, GenomeRandom random) {
        FounderTarget naturalPriors = founderTargets.generate(FounderOrigin.NATURAL, random);

        EnumMap<StatType, Double> abilities = new EnumMap<>(StatType.class);
        boolean extraordinary = false;
        for (StatType stat : StatType.values()) {
            double value = legacy.normalized(stat);
            if (!Double.isFinite(value) || value < 0.0 || value > 1.5) {
                throw new IllegalArgumentException(
                        "legacy normalized ability is outside [0,1.5]: " + stat + "=" + value);
            }
            abilities.put(stat, value);
            if (value > 1.0) extraordinary = true;
        }

        FounderOrigin synthesisOrigin =
                extraordinary ? FounderOrigin.CHICKEN_TRAP : FounderOrigin.NATURAL;
        FounderTarget target = new FounderTarget(
                synthesisOrigin,
                abilities,
                personalityTarget(legacy.nature()),
                migratedTraits(legacy.trait(), random),
                naturalPriors.developmentFactors());

        return new MigrationPlan(
                target,
                extraordinary,
                extraordinary && legacy.generation() == 0);
    }

    private static EnumMap<PersonalityFactor, Double> personalityTarget(Nature nature) {
        EnumMap<PersonalityFactor, Double> scores = new EnumMap<>(PersonalityFactor.class);
        for (PersonalityFactor factor : PersonalityFactor.values()) scores.put(factor, 0.45);

        switch (Objects.requireNonNull(nature, "nature")) {
            case MAJIME -> {
                for (PersonalityFactor factor : PersonalityFactor.values()) scores.put(factor, 0.50);
            }
            case ISOGINBO -> dominant(scores, PersonalityFactor.LEG_POWER);
            case SEKASEKA -> pair(scores, PersonalityFactor.LEG_POWER, PersonalityFactor.FLIGHT);
            case HANEKKAERI -> pair(scores, PersonalityFactor.LEG_POWER, PersonalityFactor.FLAPPING);
            case GANBARIYA -> pair(scores, PersonalityFactor.LEG_POWER, PersonalityFactor.ENDURANCE);
            case DOSSHIRI -> pair(scores, PersonalityFactor.LEG_POWER, PersonalityFactor.NUTRITION);
            case UWA_NO_SORA -> dominant(scores, PersonalityFactor.FLIGHT);
            case TOBASHIYA -> pair(scores, PersonalityFactor.FLIGHT, PersonalityFactor.FLAPPING);
            case JIKKURI -> pair(scores, PersonalityFactor.FLIGHT, PersonalityFactor.ENDURANCE);
            case NOBINOBI -> pair(scores, PersonalityFactor.FLIGHT, PersonalityFactor.NUTRITION);
            case AWATENBO -> dominant(scores, PersonalityFactor.FLAPPING);
            case NEBARIZUYOI -> pair(scores, PersonalityFactor.FLAPPING, PersonalityFactor.ENDURANCE);
            case GENKIMONO -> pair(scores, PersonalityFactor.FLAPPING, PersonalityFactor.NUTRITION);
            case TAMEKOMIYA -> dominant(scores, PersonalityFactor.ENDURANCE);
            case KOMAME -> pair(scores, PersonalityFactor.ENDURANCE, PersonalityFactor.NUTRITION);
            case KUISHINBO -> dominant(scores, PersonalityFactor.NUTRITION);
            case CHAKKARI -> dominant(scores, PersonalityFactor.NEUTRAL);
        }
        return scores;
    }

    private static void dominant(
            EnumMap<PersonalityFactor, Double> scores,
            PersonalityFactor factor) {
        scores.put(factor, 0.68);
    }

    private static void pair(
            EnumMap<PersonalityFactor, Double> scores,
            PersonalityFactor first,
            PersonalityFactor second) {
        scores.put(first, 0.68);
        scores.put(second, 0.64);
    }

    private static List<ExpressedTrait> migratedTraits(
            Trait existing,
            GenomeRandom random) {
        int none = Trait.values().length;
        int secondId = random.nextInt(none + 1);
        Trait second = secondId == none ? null : Trait.values()[secondId];

        if (existing == null) {
            return second == null
                    ? List.of()
                    : List.of(new ExpressedTrait(second, TraitStrength.WEAK));
        }
        if (second == null) {
            return List.of(new ExpressedTrait(existing, TraitStrength.WEAK));
        }
        if (second == existing) {
            return List.of(new ExpressedTrait(existing, TraitStrength.STRONG));
        }

        List<ExpressedTrait> traits = new ArrayList<>(2);
        traits.add(new ExpressedTrait(existing, TraitStrength.WEAK));
        traits.add(new ExpressedTrait(second, TraitStrength.WEAK));
        traits.sort(Comparator.comparingInt(value -> value.trait().targetId()));
        return List.copyOf(traits);
    }

    private record MigrationPlan(
            FounderTarget target,
            boolean extraordinaryAllowed,
            boolean divineSupplyAllowed) {}
}
