package co.surumene.whatawonderfulchicken.breeding;

import co.surumene.whatawonderfulchicken.data.*;
import co.surumene.wgl.api.DecoderIdentity;
import co.surumene.wgl.api.GenomeAddress;
import co.surumene.wgl.api.ProfileDescriptor;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class WonderfulChickenBreedingPolicyTest {
    @Test
    void directInheritanceStrengthMapsToSpecifiedRetention() {
        assertEquals(0.75,
                WonderfulChickenBreedingPolicy.directRetentionProbability(TraitStrength.WEAK),
                0.0);
        assertEquals(1.0,
                WonderfulChickenBreedingPolicy.directRetentionProbability(TraitStrength.STRONG),
                0.0);
    }

    @Test
    void hatenkoMultipliesAcrossBothParents() {
        PhenotypeSnapshot weak = snapshot(List.of(
                new ExpressedTrait(Trait.HATENKO, TraitStrength.WEAK)));
        PhenotypeSnapshot strong = snapshot(List.of(
                new ExpressedTrait(Trait.HATENKO, TraitStrength.STRONG)));
        PhenotypeSnapshot none = snapshot(List.of());

        assertEquals(2.0, WonderfulChickenBreedingPolicy.mutationMultiplier(weak, none), 0.0);
        assertEquals(4.0, WonderfulChickenBreedingPolicy.mutationMultiplier(strong, none), 0.0);
        assertEquals(8.0, WonderfulChickenBreedingPolicy.mutationMultiplier(weak, strong), 0.0);
    }

    @Test
    void divineAddressCannotAppearDeNovo() {
        assertEquals(
                java.util.Set.of(new GenomeAddress(0x05, 0x00)),
                WonderfulChickenBreedingPolicy.deNovoForbiddenAddresses());
    }

    private static PhenotypeSnapshot snapshot(List<ExpressedTrait> traits) {
        EnumMap<StatType, Double> abilities = new EnumMap<>(StatType.class);
        for (StatType stat : StatType.values()) abilities.put(stat, 0.5);
        EnumMap<PersonalityFactor, Double> personality = new EnumMap<>(PersonalityFactor.class);
        for (PersonalityFactor factor : PersonalityFactor.values()) personality.put(factor, 0.5);
        EnumMap<DevelopmentFactor, Double> development = new EnumMap<>(DevelopmentFactor.class);
        for (DevelopmentFactor factor : DevelopmentFactor.values()) development.put(factor, 0.5);
        return new PhenotypeSnapshot(
                new DecoderIdentity(1, new byte[32],
                        new ProfileDescriptor("wonderful-chicken", 1, new byte[32])),
                abilities, personality, Nature.MAJIME, traits, development,
                List.of(), 0.0, false);
    }
}
