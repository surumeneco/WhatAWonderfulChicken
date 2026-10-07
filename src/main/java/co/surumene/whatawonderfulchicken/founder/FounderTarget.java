package co.surumene.whatawonderfulchicken.founder;

import co.surumene.whatawonderfulchicken.data.DevelopmentFactor;
import co.surumene.whatawonderfulchicken.data.ExpressedTrait;
import co.surumene.whatawonderfulchicken.data.PersonalityFactor;
import co.surumene.whatawonderfulchicken.data.StatType;
import co.surumene.whatawonderfulchicken.data.Trait;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record FounderTarget(
        FounderOrigin origin,
        Map<StatType, Double> abilities,
        Map<PersonalityFactor, Double> personalityFactors,
        List<ExpressedTrait> traits,
        Map<DevelopmentFactor, Double> developmentFactors) {

    public FounderTarget {
        Objects.requireNonNull(origin, "origin");
        abilities = completeScores(StatType.class, abilities, 0.0, 1.5, "abilities");
        personalityFactors = completeScores(PersonalityFactor.class, personalityFactors, 0.0, 1.0, "personalityFactors");
        developmentFactors = completeScores(DevelopmentFactor.class, developmentFactors, 0.0, 1.0, "developmentFactors");
        traits = List.copyOf(Objects.requireNonNull(traits, "traits"));
        if (traits.size() > 2) throw new IllegalArgumentException("traits must contain at most two entries");
        HashSet<Trait> unique = new HashSet<>();
        for (ExpressedTrait trait : traits) {
            Objects.requireNonNull(trait, "trait");
            if (!unique.add(trait.trait())) throw new IllegalArgumentException("traits must not contain duplicates");
        }
    }

    private static <K extends Enum<K>> Map<K, Double> completeScores(
            Class<K> type, Map<K, Double> source, double min, double max, String name) {
        Objects.requireNonNull(source, name);
        EnumMap<K, Double> copy = new EnumMap<>(type);
        for (K key : type.getEnumConstants()) {
            Double value = source.get(key);
            if (value == null || !Double.isFinite(value) || value < min || value > max) {
                throw new IllegalArgumentException(name + " has invalid or missing value for " + key);
            }
            copy.put(key, value);
        }
        if (copy.size() != source.size()) throw new IllegalArgumentException(name + " contains unexpected entries");
        return Collections.unmodifiableMap(copy);
    }
}
