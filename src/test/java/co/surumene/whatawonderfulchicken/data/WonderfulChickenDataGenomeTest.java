package co.surumene.whatawonderfulchicken.data;

import co.surumene.wgl.api.BitSequence;
import co.surumene.wgl.api.ChromosomePair;
import co.surumene.wgl.api.DecoderIdentity;
import co.surumene.wgl.api.DiploidGenome;
import co.surumene.wgl.api.ProfileDescriptor;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class WonderfulChickenDataGenomeTest {
    @Test
    void snapshotBecomesPhenotypeAuthorityWithoutDeletingLegacyGenetics() {
        WonderfulChickenData data = new WonderfulChickenData();
        data.genetics(new Genetics(0, 0, 0, 0));
        data.genome(genome());
        data.phenotypeSnapshot(snapshot());
        data.adultBiologicalTime(1234L);

        assertEquals(Nature.GANBARIYA, data.nature());
        assertEquals(Trait.FUKUTSU, data.trait());
        assertTrue(data.hasTrait(Trait.FUKUTSU));
        assertFalse(data.hasTrait(Trait.YOME));

        WonderfulChickenData copy = data.copy();
        assertTrue(copy.hasGenomeModel());
        assertSame(data.genome(), copy.genome());
        assertSame(data.phenotypeSnapshot(), copy.phenotypeSnapshot());
        assertEquals(1234L, copy.adultBiologicalTime());
        assertEquals(new Genetics(0,0,0,0), copy.genetics());
    }

    @Test
    void decodedNoTraitDoesNotResurrectLegacyTrait() {
        WonderfulChickenData data = new WonderfulChickenData();
        data.genetics(new Genetics(0, 0, 0, 0));
        PhenotypeSnapshot original = snapshot();
        data.phenotypeSnapshot(new PhenotypeSnapshot(
                original.decoderIdentity(),
                original.normalizedAbilities(),
                original.personalityFactors(),
                original.personality(),
                List.of(),
                original.developmentFactors(),
                original.injuries(),
                original.divineLineageTotalScore(),
                original.divineLineageExpressed()));

        assertNull(data.trait());
        assertFalse(data.hasTrait(Trait.YOME));
    }

    @Test
    void rejectsNegativeAdultBiologicalTime() {
        WonderfulChickenData data = new WonderfulChickenData();
        assertThrows(IllegalArgumentException.class, () -> data.adultBiologicalTime(-1L));
    }

    private static DiploidGenome genome() {
        return new DiploidGenome(1, List.of(new ChromosomePair(
                BitSequence.fromBits("1010"),
                BitSequence.fromBits("0101"))));
    }

    private static PhenotypeSnapshot snapshot() {
        EnumMap<StatType, Double> abilities = new EnumMap<>(StatType.class);
        for (StatType stat : StatType.values()) abilities.put(stat, 0.5);
        EnumMap<PersonalityFactor, Double> personality = new EnumMap<>(PersonalityFactor.class);
        for (PersonalityFactor factor : PersonalityFactor.values()) personality.put(factor, 0.5);
        EnumMap<DevelopmentFactor, Double> development = new EnumMap<>(DevelopmentFactor.class);
        for (DevelopmentFactor factor : DevelopmentFactor.values()) development.put(factor, 0.5);

        return new PhenotypeSnapshot(
                new DecoderIdentity(1, new byte[32],
                        new ProfileDescriptor("wonderful-chicken", 1, new byte[32])),
                abilities,
                personality,
                Nature.GANBARIYA,
                List.of(new ExpressedTrait(Trait.FUKUTSU, TraitStrength.WEAK)),
                development,
                List.of(),
                0.0,
                false);
    }
}
