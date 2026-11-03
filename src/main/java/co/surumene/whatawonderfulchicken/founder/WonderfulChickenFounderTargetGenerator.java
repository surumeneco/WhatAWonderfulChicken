package co.surumene.whatawonderfulchicken.founder;

import co.surumene.whatawonderfulchicken.data.DevelopmentFactor;
import co.surumene.whatawonderfulchicken.data.ExpressedTrait;
import co.surumene.whatawonderfulchicken.data.PersonalityFactor;
import co.surumene.whatawonderfulchicken.data.StatType;
import co.surumene.whatawonderfulchicken.data.Trait;
import co.surumene.whatawonderfulchicken.data.TraitStrength;
import co.surumene.whatawonderfulchicken.genome.WonderfulChickenSynthesisSettings;
import co.surumene.wgl.api.GenomeRandom;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

public final class WonderfulChickenFounderTargetGenerator {
    private static final int TRAIT_CANDIDATE_COUNT = Trait.values().length + 1;
    private final WonderfulChickenSynthesisSettings settings;
    private final WonderfulChickenSynthesisSettings.Distribution trapDistribution;

    public WonderfulChickenFounderTargetGenerator(WonderfulChickenSynthesisSettings settings) {
        this(settings, settings.abilityDistribution());
    }
    public WonderfulChickenFounderTargetGenerator(
            WonderfulChickenSynthesisSettings settings,
            WonderfulChickenSynthesisSettings.Distribution trapDistribution) {
        this.settings = Objects.requireNonNull(settings);
        this.trapDistribution = Objects.requireNonNull(trapDistribution);
    }

    public FounderTarget generate(FounderOrigin origin, GenomeRandom random) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(random, "random");

        EnumMap<StatType, Double> abilities = new EnumMap<>(StatType.class);
        double abilityMin = origin == FounderOrigin.NATURAL ? 0.0 : 0.5;
        double abilityMax = origin == FounderOrigin.NATURAL ? 0.75 : 1.5;
        for (StatType stat : StatType.values()) {
            abilities.put(stat, truncatedNormal(
                    (origin == FounderOrigin.NATURAL ? settings.abilityDistribution() : trapDistribution), abilityMin, abilityMax, random));
        }

        EnumMap<PersonalityFactor, Double> personality = new EnumMap<>(PersonalityFactor.class);
        for (PersonalityFactor factor : PersonalityFactor.values()) {
            personality.put(factor, truncatedNormal(
                    settings.personalityDistribution(), 0.0, 1.0, random));
        }

        EnumMap<DevelopmentFactor, Double> development = new EnumMap<>(DevelopmentFactor.class);
        for (DevelopmentFactor factor : DevelopmentFactor.values()) {
            development.put(factor, truncatedNormal(
                    settings.developmentDistribution(), 0.0, 1.0, random));
        }

        return new FounderTarget(origin, abilities, personality, drawTraits(random), development);
    }

    private static List<ExpressedTrait> drawTraits(GenomeRandom random) {
        return co.surumene.wcl.api.genetics.FounderSampling.drawTraits(
                        List.of(Trait.values()), Trait::targetId, random::nextInt)
                .stream()
                .map(choice -> new ExpressedTrait(choice.trait(),
                        choice.strong() ? TraitStrength.STRONG : TraitStrength.WEAK))
                .toList();
    }

    static double truncatedNormal(
            WonderfulChickenSynthesisSettings.Distribution distribution,
            double min,
            double max,
            GenomeRandom random) {
        return co.surumene.wcl.api.genetics.FounderSampling.truncatedNormal(
                distribution.mean(), distribution.standardDeviation(),
                min, max, random::nextDouble);
    }
}
