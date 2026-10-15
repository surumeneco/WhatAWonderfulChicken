package co.surumene.whatawonderfulchicken.genome;

import co.surumene.whatawonderfulchicken.data.DevelopmentFactor;
import co.surumene.whatawonderfulchicken.data.Nature;
import co.surumene.whatawonderfulchicken.data.PersonalityFactor;
import co.surumene.whatawonderfulchicken.data.StatType;
import co.surumene.whatawonderfulchicken.data.Trait;
import co.surumene.whatawonderfulchicken.data.TraitStrength;
import co.surumene.wgl.api.AddressAggregate;
import co.surumene.wgl.api.DecodedGenome;
import co.surumene.wgl.api.DecoderIdentity;
import co.surumene.wgl.api.GenomeAddress;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WonderfulChickenGenomeProfileTest {
    private final WonderfulChickenGenomeProfile profile =
            new WonderfulChickenGenomeProfile(WonderfulChickenGenomeSettings.defaults());

    @Test
    void exposesChickenAddressSpace() {
        assertTrue(profile.isDefinedAddress(new GenomeAddress(0x00, 0x08)));
        assertFalse(profile.isDefinedAddress(new GenomeAddress(0x00, 0x09)));
        assertTrue(profile.isDefinedAddress(new GenomeAddress(0x03, 0x05)));
        assertTrue(profile.isDefinedAddress(new GenomeAddress(0x04, 0x0C)));
        assertFalse(profile.isDefinedAddress(new GenomeAddress(0x04, 0x0D)));
        assertTrue(profile.isDefinedAddress(new GenomeAddress(0x05, 0x00)));
        assertEquals(16, profile.minimumExtensionBits(new GenomeAddress(0x02, 0x00)));
    }

    @Test
    void decodesChickenPersonalityTraitsAndTrapFounderExtraordinaryAbility() {
        Map<GenomeAddress, AddressAggregate> aggregates = new HashMap<>();

        for (StatType stat : StatType.values()) {
            aggregates.put(new GenomeAddress(0x00, stat.targetId()), bounded(0.40));
            aggregates.put(new GenomeAddress(0x07, stat.targetId()), bounded(0.20));
        }
        for (DevelopmentFactor factor : DevelopmentFactor.values()) {
            aggregates.put(new GenomeAddress(0x01, factor.targetId()), centered(0.50));
        }

        putPersonality(aggregates, PersonalityFactor.LEG_POWER, 0.72);
        putPersonality(aggregates, PersonalityFactor.FLIGHT, 0.40);
        putPersonality(aggregates, PersonalityFactor.FLAPPING, 0.41);
        putPersonality(aggregates, PersonalityFactor.ENDURANCE, 0.68);
        putPersonality(aggregates, PersonalityFactor.NUTRITION, 0.42);
        putPersonality(aggregates, PersonalityFactor.NEUTRAL, 0.43);

        aggregates.put(new GenomeAddress(0x04, Trait.JIKIDEN.targetId()), bounded(0.70));
        aggregates.put(new GenomeAddress(0x04, Trait.HATENKO.targetId()), bounded(0.68));

        WonderfulChickenDecodedPhenotype decoded =
                profile.mapPhenotype(new DecodedGenome(aggregates, List.of(), List.of()));

        assertEquals(Nature.GANBARIYA, decoded.personality());
        assertEquals(2, decoded.expressedTraits().size());
        assertEquals(Trait.JIKIDEN, decoded.expressedTraits().get(0).trait());
        assertEquals(TraitStrength.WEAK, decoded.expressedTraits().get(0).strength());
        assertEquals(Trait.HATENKO, decoded.expressedTraits().get(1).trait());

        DecoderIdentity identity = new DecoderIdentity(1, new byte[32], profile.descriptor());
        assertEquals(0.40,
                decoded.toSnapshot(identity, PhenotypeOrigin.NATURAL_FOUNDER)
                        .normalizedAbilities().get(StatType.MAX_HEALTH),
                1.0e-12);
        assertEquals(0.50,
                decoded.toSnapshot(identity, PhenotypeOrigin.CHICKEN_TRAP_FOUNDER)
                        .normalizedAbilities().get(StatType.MAX_HEALTH),
                1.0e-12);
    }

    @Test
    void maxBelowHalfIsSeriousEvenWhenScoresAreSpread() {
        Map<GenomeAddress, AddressAggregate> aggregates = new HashMap<>();
        for (StatType stat : StatType.values()) {
            aggregates.put(new GenomeAddress(0x00, stat.targetId()), bounded(0.40));
            aggregates.put(new GenomeAddress(0x07, stat.targetId()), bounded(0.0));
        }
        for (DevelopmentFactor factor : DevelopmentFactor.values()) {
            aggregates.put(new GenomeAddress(0x01, factor.targetId()), centered(0.50));
        }

        putPersonality(aggregates, PersonalityFactor.LEG_POWER, 0.49);
        putPersonality(aggregates, PersonalityFactor.FLIGHT, 0.20);
        putPersonality(aggregates, PersonalityFactor.FLAPPING, 0.30);
        putPersonality(aggregates, PersonalityFactor.ENDURANCE, 0.35);
        putPersonality(aggregates, PersonalityFactor.NUTRITION, 0.40);
        putPersonality(aggregates, PersonalityFactor.NEUTRAL, 0.45);

        assertEquals(
                Nature.MAJIME,
                profile.mapPhenotype(new DecodedGenome(aggregates, List.of(), List.of())).personality());
    }

    private static void putPersonality(
            Map<GenomeAddress, AddressAggregate> aggregates,
            PersonalityFactor factor,
            double score) {
        aggregates.put(new GenomeAddress(0x03, factor.targetId()), centered(score));
    }

    private static AddressAggregate bounded(double score) {
        return new AddressAggregate(score, 1.0, score, List.of());
    }

    private static AddressAggregate centered(double score) {
        if (score >= 0.5) {
            return new AddressAggregate(2.0 * score - 1.0, 1.0, score, List.of());
        }
        return new AddressAggregate(0.0, 2.0 * score, score, List.of());
    }
}
