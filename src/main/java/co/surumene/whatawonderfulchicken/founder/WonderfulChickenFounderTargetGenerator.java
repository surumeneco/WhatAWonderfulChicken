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

    public WonderfulChickenFounderTargetGenerator(WonderfulChickenSynthesisSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public FounderTarget generate(FounderOrigin origin, GenomeRandom random) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(random, "random");

        EnumMap<StatType, Double> abilities = new EnumMap<>(StatType.class);
        double abilityMin = origin == FounderOrigin.NATURAL ? 0.0 : 0.5;
        double abilityMax = origin == FounderOrigin.NATURAL ? 0.75 : 1.5;
        for (StatType stat : StatType.values()) {
            abilities.put(stat, truncatedNormal(
                    settings.abilityDistribution(), abilityMin, abilityMax, random));
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
        int none = Trait.values().length;
        int first = random.nextInt(TRAIT_CANDIDATE_COUNT);
        int second = random.nextInt(TRAIT_CANDIDATE_COUNT);
        if (first == none && second == none) return List.of();
        if (first == second) return List.of(new ExpressedTrait(Trait.values()[first], TraitStrength.STRONG));

        List<ExpressedTrait> out = new ArrayList<>(2);
        if (first != none) out.add(new ExpressedTrait(Trait.values()[first], TraitStrength.WEAK));
        if (second != none) out.add(new ExpressedTrait(Trait.values()[second], TraitStrength.WEAK));
        out.sort(Comparator.comparingInt(entry -> entry.trait().targetId()));
        return List.copyOf(out);
    }

    static double truncatedNormal(
            WonderfulChickenSynthesisSettings.Distribution distribution,
            double min,
            double max,
            GenomeRandom random) {
        for (;;) {
            double u1 = random.nextDouble();
            double u2 = random.nextDouble();
            if (!(u1 > 0.0)) continue;
            double z = StrictMath.sqrt(-2.0 * StrictMath.log(u1))
                    * StrictMath.cos(2.0 * StrictMath.PI * u2);
            double value = distribution.mean() + distribution.standardDeviation() * z;
            if (value >= min && value <= max) return value;
        }
    }
}
