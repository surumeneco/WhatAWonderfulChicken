package co.surumene.whatawonderfulchicken.data;

import co.surumene.wgl.api.DecoderIdentity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record PhenotypeSnapshot(
        DecoderIdentity decoderIdentity,
        Map<StatType, Double> normalizedAbilities,
        Map<PersonalityFactor, Double> personalityFactors,
        Nature personality,
        List<ExpressedTrait> expressedTraits,
        Map<DevelopmentFactor, Double> developmentFactors,
        List<InjuryPhenotype> injuries,
        double divineLineageTotalScore,
        boolean divineLineageExpressed) {

    public PhenotypeSnapshot {
        Objects.requireNonNull(decoderIdentity, "decoderIdentity");
        Objects.requireNonNull(personality, "personality");
        if (!Double.isFinite(divineLineageTotalScore)
                || divineLineageTotalScore < 0.0
                || divineLineageTotalScore > 2.0) {
            throw new IllegalArgumentException("divineLineageTotalScore must be finite and in [0,2]");
        }

        normalizedAbilities = completeScores(
                StatType.class, normalizedAbilities, 0.0, 1.5, "normalizedAbilities");
        personalityFactors = completeScores(
                PersonalityFactor.class, personalityFactors, 0.0, 1.0, "personalityFactors");
        developmentFactors = completeScores(
                DevelopmentFactor.class, developmentFactors, 0.0, 1.0, "developmentFactors");

        expressedTraits = List.copyOf(Objects.requireNonNull(expressedTraits, "expressedTraits"));
        if (expressedTraits.size() > 2) {
            throw new IllegalArgumentException("expressedTraits must contain at most two traits");
        }
        HashSet<Trait> traitKinds = new HashSet<>();
        for (ExpressedTrait trait : expressedTraits) {
            Objects.requireNonNull(trait, "expressedTrait");
            if (!traitKinds.add(trait.trait())) {
                throw new IllegalArgumentException("expressedTraits must not contain duplicate traits");
            }
        }

        injuries = List.copyOf(Objects.requireNonNull(injuries, "injuries"));
        EnumSet<StatType> injuredStats = EnumSet.noneOf(StatType.class);
        for (InjuryPhenotype injury : injuries) {
            Objects.requireNonNull(injury, "injury");
            if (!injuredStats.add(injury.stat())) {
                throw new IllegalArgumentException("injuries must contain at most one entry per stat");
            }
        }
    }

    private static <K extends Enum<K>> Map<K, Double> completeScores(
            Class<K> type,
            Map<K, Double> source,
            double min,
            double max,
            String name) {
        Objects.requireNonNull(source, name);
        EnumMap<K, Double> copy = new EnumMap<>(type);
        for (K key : type.getEnumConstants()) {
            Double value = source.get(key);
            if (value == null) {
                throw new IllegalArgumentException(name + " is missing " + key);
            }
            if (!Double.isFinite(value) || value < min || value > max) {
                throw new IllegalArgumentException(name + "[" + key + "] must be finite and in [" + min + "," + max + "]");
            }
            copy.put(key, value);
        }
        if (source.size() != copy.size()) {
            throw new IllegalArgumentException(name + " contains unexpected entries");
        }
        return Collections.unmodifiableMap(copy);
    }
}
