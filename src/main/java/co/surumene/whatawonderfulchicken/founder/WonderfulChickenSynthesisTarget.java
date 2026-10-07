package co.surumene.whatawonderfulchicken.founder;

import co.surumene.whatawonderfulchicken.data.DevelopmentFactor;
import co.surumene.whatawonderfulchicken.data.ExpressedTrait;
import co.surumene.whatawonderfulchicken.data.PersonalityFactor;
import co.surumene.whatawonderfulchicken.data.StatType;
import co.surumene.whatawonderfulchicken.data.Trait;
import co.surumene.whatawonderfulchicken.data.TraitStrength;
import co.surumene.whatawonderfulchicken.genome.WonderfulChickenGenomeSettings;
import co.surumene.whatawonderfulchicken.genome.WonderfulChickenSynthesisSettings;
import co.surumene.wgl.api.AddressAggregate;
import co.surumene.wgl.api.DecodedGenome;
import co.surumene.wgl.api.GenomeAddress;
import co.surumene.wgl.api.GenomeRandom;
import co.surumene.wgl.api.SynthesisTarget;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class WonderfulChickenSynthesisTarget implements SynthesisTarget {
    private final FounderOrigin origin;
    private final Map<GenomeAddress, Double> continuousTargets;
    private final Map<StatType, Double> baseAbilityTargets;
    private final Map<StatType, Double> extraordinaryTargets;

    private WonderfulChickenSynthesisTarget(
            FounderOrigin origin,
            Map<GenomeAddress, Double> continuousTargets,
            Map<StatType, Double> baseAbilityTargets,
            Map<StatType, Double> extraordinaryTargets) {
        this.origin = Objects.requireNonNull(origin, "origin");
        this.continuousTargets = Map.copyOf(continuousTargets);
        this.baseAbilityTargets = immutableStatMap(baseAbilityTargets);
        this.extraordinaryTargets = immutableStatMap(extraordinaryTargets);
    }

    public static WonderfulChickenSynthesisTarget from(
            FounderTarget founder,
            WonderfulChickenGenomeSettings genomeSettings,
            WonderfulChickenSynthesisSettings synthesisSettings,
            GenomeRandom random) {
        Objects.requireNonNull(founder, "founder");
        Objects.requireNonNull(genomeSettings, "genomeSettings");
        Objects.requireNonNull(synthesisSettings, "synthesisSettings");
        Objects.requireNonNull(random, "random");

        Map<GenomeAddress, Double> continuous = new LinkedHashMap<>();
        EnumMap<StatType, Double> base = new EnumMap<>(StatType.class);
        EnumMap<StatType, Double> extraordinary = new EnumMap<>(StatType.class);

        for (StatType stat : StatType.values()) {
            double finalTarget = founder.abilities().get(stat);
            double b = finalTarget;
            double e = 0.0;
            if (founder.origin() == FounderOrigin.CHICKEN_TRAP && finalTarget > 1.0) {
                double maxTransfer = Math.min(
                        synthesisSettings.extraordinary().transferMax(),
                        1.5 - finalTarget);
                double transfer = maxTransfer <= 0.0 ? 0.0 : maxTransfer * random.nextDouble();
                b = 1.0 - transfer;
                e = finalTarget - b;
            }
            base.put(stat, b);
            extraordinary.put(stat, e);
            continuous.put(new GenomeAddress(0x00, stat.targetId()), b);
        }

        for (DevelopmentFactor factor : DevelopmentFactor.values()) {
            continuous.put(new GenomeAddress(0x01, factor.targetId()),
                    founder.developmentFactors().get(factor));
        }
        for (PersonalityFactor factor : PersonalityFactor.values()) {
            continuous.put(new GenomeAddress(0x03, factor.targetId()),
                    founder.personalityFactors().get(factor));
        }

        addTraitTargets(founder, genomeSettings, continuous);
        return new WonderfulChickenSynthesisTarget(founder.origin(), continuous, base, extraordinary);
    }

    public FounderOrigin origin() { return origin; }
    @Override public Map<GenomeAddress, Double> continuousTargets() { return continuousTargets; }
    public Map<StatType, Double> baseAbilityTargets() { return baseAbilityTargets; }
    public Map<StatType, Double> extraordinaryTargets() { return extraordinaryTargets; }

    @Override
    public boolean isSatisfied(DecodedGenome decoded, double tolerance) {
        Objects.requireNonNull(decoded, "decoded");
        if (!Double.isFinite(tolerance) || tolerance < 0.0) {
            throw new IllegalArgumentException("tolerance must be finite and >= 0");
        }
        for (var entry : continuousTargets.entrySet()) {
            AddressAggregate aggregate = decoded.aggregate(entry.getKey());
            double actual = switch (entry.getKey().type()) {
                case 0x01, 0x03 -> centeredScore(aggregate);
                default -> aggregate.score();
            };
            if (StrictMath.abs(actual - entry.getValue()) > tolerance) return false;
        }
        for (StatType stat : StatType.values()) {
            double expected = extraordinaryTargets.get(stat);
            double actual = 0.5 * decoded.aggregate(new GenomeAddress(0x07, stat.targetId())).score();
            if (StrictMath.abs(actual - expected) > tolerance) return false;
        }
        return true;
    }

    private static void addTraitTargets(
            FounderTarget founder,
            WonderfulChickenGenomeSettings settings,
            Map<GenomeAddress, Double> continuous) {
        double threshold = settings.traitExpressionThreshold();
        double gap = settings.traitStrongGap();
        double inactive = Math.max(0.0, threshold - Math.max(gap, 0.05));
        double weak = Math.min(1.0, threshold + Math.max(0.01, gap * 0.25));
        double strong = Math.min(1.0, weak + gap + Math.max(0.01, gap * 0.25));

        Map<Trait, TraitStrength> expressed = new EnumMap<>(Trait.class);
        for (ExpressedTrait trait : founder.traits()) expressed.put(trait.trait(), trait.strength());
        for (Trait trait : Trait.values()) {
            TraitStrength strength = expressed.get(trait);
            double value = strength == null ? inactive
                    : strength == TraitStrength.STRONG ? strong : weak;
            continuous.put(new GenomeAddress(0x04, trait.targetId()), value);
        }
    }

    private static double centeredScore(AddressAggregate aggregate) {
        double negative = 1.0 - aggregate.negativeSurvival();
        return Math.max(0.0, Math.min(1.0,
                0.5 + 0.5 * (aggregate.positiveSaturation() - negative)));
    }

    private static Map<StatType, Double> immutableStatMap(Map<StatType, Double> source) {
        EnumMap<StatType, Double> copy = new EnumMap<>(StatType.class);
        copy.putAll(source);
        return Collections.unmodifiableMap(copy);
    }
}
