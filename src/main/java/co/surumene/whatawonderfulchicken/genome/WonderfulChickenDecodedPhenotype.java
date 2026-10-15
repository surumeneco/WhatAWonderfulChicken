package co.surumene.whatawonderfulchicken.genome;

import co.surumene.whatawonderfulchicken.data.DevelopmentFactor;
import co.surumene.whatawonderfulchicken.data.ExpressedTrait;
import co.surumene.whatawonderfulchicken.data.InjuryPhenotype;
import co.surumene.whatawonderfulchicken.data.Nature;
import co.surumene.whatawonderfulchicken.data.PersonalityFactor;
import co.surumene.whatawonderfulchicken.data.PhenotypeSnapshot;
import co.surumene.whatawonderfulchicken.data.StatType;
import co.surumene.wgl.api.DecoderIdentity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record WonderfulChickenDecodedPhenotype(
        Map<StatType, Double> baseAbilities,
        Map<StatType, Double> extraordinaryContributions,
        Map<PersonalityFactor, Double> personalityFactors,
        Nature personality,
        List<ExpressedTrait> expressedTraits,
        Map<DevelopmentFactor, Double> developmentFactors,
        List<InjuryPhenotype> injuries,
        DivineLineagePhenotype divineLineage) {

    public WonderfulChickenDecodedPhenotype {
        baseAbilities = immutableEnumMap(StatType.class, baseAbilities);
        extraordinaryContributions = immutableEnumMap(StatType.class, extraordinaryContributions);
        personalityFactors = immutableEnumMap(PersonalityFactor.class, personalityFactors);
        developmentFactors = immutableEnumMap(DevelopmentFactor.class, developmentFactors);
        personality = Objects.requireNonNull(personality, "personality");
        expressedTraits = List.copyOf(Objects.requireNonNull(expressedTraits, "expressedTraits"));
        injuries = List.copyOf(Objects.requireNonNull(injuries, "injuries"));
        divineLineage = Objects.requireNonNull(divineLineage, "divineLineage");
    }

    public PhenotypeSnapshot toSnapshot(DecoderIdentity identity, PhenotypeOrigin origin) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(origin, "origin");

        boolean extraordinaryActive =
                origin == PhenotypeOrigin.CHICKEN_TRAP_FOUNDER || divineLineage.expressed();

        EnumMap<StatType, Double> abilities = new EnumMap<>(StatType.class);
        for (StatType stat : StatType.values()) {
            double base = baseAbilities.get(stat);
            double extraordinary = extraordinaryActive ? extraordinaryContributions.get(stat) : 0.0;
            abilities.put(stat, Math.min(1.5, base + extraordinary));
        }

        return new PhenotypeSnapshot(
                identity,
                abilities,
                personalityFactors,
                personality,
                expressedTraits,
                developmentFactors,
                injuries,
                divineLineage.totalScore(),
                divineLineage.expressed());
    }

    private static <K extends Enum<K>> Map<K, Double> immutableEnumMap(
            Class<K> type,
            Map<K, Double> source) {
        Objects.requireNonNull(source, "source");
        EnumMap<K, Double> copy = new EnumMap<>(type);
        copy.putAll(source);
        for (K key : type.getEnumConstants()) {
            Double value = copy.get(key);
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException("missing or non-finite value for " + key);
            }
        }
        if (copy.size() != type.getEnumConstants().length) {
            throw new IllegalArgumentException("unexpected enum-map entries");
        }
        return Collections.unmodifiableMap(copy);
    }
}
