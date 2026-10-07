package co.surumene.whatawonderfulchicken.breeding;

import co.surumene.whatawonderfulchicken.data.*;
import co.surumene.whatawonderfulchicken.founder.*;
import co.surumene.whatawonderfulchicken.genome.*;
import co.surumene.wgl.api.*;
import co.surumene.wgl.core.EngineConfig;
import co.surumene.wgl.core.WonderfulGenomeEngine;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class WonderfulChickenBreedingServiceIntegrationTest {
    private final WonderfulGenomeEngine engine =
            WonderfulGenomeEngine.create(EngineConfig.defaults());
    private final WonderfulChickenGenomeProfile profile =
            new WonderfulChickenGenomeProfile(
                    WonderfulChickenGenomeSettings.defaults(),
                    WonderfulChickenSynthesisSettings.defaults(),
                    engine.geneSequenceCodec());
    private final WonderfulChickenBreedingService breeding =
            new WonderfulChickenBreedingService(() -> engine, () -> profile);

    @Test
    void breedsTwoBackboneCompatibleParentsThroughWgl() {
        WonderfulChickenData a = parent(FounderOrigin.NATURAL, 101L);
        WonderfulChickenData b = parent(FounderOrigin.NATURAL, 202L);

        WonderfulChickenBreedingOutcome outcome = breeding.breed(
                new BreedingParent("A", a),
                new BreedingParent("B", b),
                303L);

        WonderfulChickenBreedingOutcome.Success success =
                assertInstanceOf(WonderfulChickenBreedingOutcome.Success.class, outcome);
        assertEquals(6, success.genome().chromosomePairCount());
        assertEquals("wonderful-chicken",
                success.phenotypeSnapshot().decoderIdentity().profileDescriptor().profileId());
        assertFalse(success.marker().isBlank());
    }

    @Test
    void acceptsAlreadyResolvedGameteSources() {
        WonderfulChickenData a = parent(FounderOrigin.NATURAL, 111L);
        WonderfulChickenData b = parent(FounderOrigin.NATURAL, 222L);

        HaploidGenome gameteA = haplotypeA(a.genome());
        HaploidGenome gameteB = haplotypeA(b.genome());

        WonderfulChickenBreedingOutcome outcome = breeding.breedSources(
                new BreedingParentSource.Gamete(gameteA),
                new BreedingParentSource.Gamete(gameteB),
                333L);

        WonderfulChickenBreedingOutcome.Success success =
                assertInstanceOf(WonderfulChickenBreedingOutcome.Success.class, outcome);
        assertEquals(6, success.genome().chromosomePairCount());
    }

    @Test
    void incompatibleBackboneFallsBackWithoutCallingConsumerHomologyLogic() {
        WonderfulChickenData compatible = parent(FounderOrigin.NATURAL, 404L);
        WonderfulChickenData incompatible = parent(FounderOrigin.NATURAL, 505L);
        incompatible.genome(zeroGenome());

        WonderfulChickenBreedingOutcome outcome = breeding.breed(
                new BreedingParent("compatible", compatible),
                new BreedingParent("incompatible", incompatible),
                606L);

        WonderfulChickenBreedingOutcome.Fallback fallback =
                assertInstanceOf(WonderfulChickenBreedingOutcome.Fallback.class, outcome);
        assertTrue(fallback.detail().contains("Backbone"));
    }

    @Test
    void directInheritanceCreatesPhysicalParentConstraint() {
        WonderfulChickenData direct = withTraits(
                parent(FounderOrigin.NATURAL, 707L),
                List.of(new ExpressedTrait(Trait.JIKIDEN, TraitStrength.WEAK)));
        WonderfulChickenData other = parent(FounderOrigin.NATURAL, 808L);

        BreedingContext context =
                new WonderfulChickenBreedingContextFactory(engine, profile)
                        .create(direct, other, engine.standardRandom(909L));

        assertEquals(1, context.parentAPolicy().inheritanceConstraints().size());
        InheritanceConstraint constraint =
                context.parentAPolicy().inheritanceConstraints().getFirst();
        assertEquals(0.75, constraint.retentionProbability(), 0.0);
        assertFalse(constraint.hardProtection());
    }

    @Test
    void hatenkoFeedsWglMutationMultiplier() {
        WonderfulChickenData weak = withTraits(
                parent(FounderOrigin.NATURAL, 1001L),
                List.of(new ExpressedTrait(Trait.HATENKO, TraitStrength.WEAK)));
        WonderfulChickenData strong = withTraits(
                parent(FounderOrigin.NATURAL, 1002L),
                List.of(new ExpressedTrait(Trait.HATENKO, TraitStrength.STRONG)));

        BreedingContext context =
                new WonderfulChickenBreedingContextFactory(engine, profile)
                        .create(weak, strong, engine.standardRandom(1003L));

        assertEquals(8.0, context.mutationRateMultiplier(), 0.0);
        assertEquals(
                java.util.Set.of(new GenomeAddress(0x05, 0x00)),
                context.deNovoForbiddenAddresses());
    }

    private WonderfulChickenData parent(FounderOrigin origin, long seed) {
        WonderfulChickenFounderSynthesizer synthesizer =
                new WonderfulChickenFounderSynthesizer(engine, profile);
        FounderGenomeSynthesis synthesis = synthesizer.synthesize(origin, seed);
        SynthesisResult.Success success =
                assertInstanceOf(SynthesisResult.Success.class, synthesis.result());
        DecodeResult<WonderfulChickenDecodedPhenotype> decoded =
                engine.decode(profile, success.genome());

        WonderfulChickenData data = new WonderfulChickenData();
        data.genome(success.genome());
        data.phenotypeSnapshot(decoded.phenotype().toSnapshot(
                decoded.identity(),
                origin == FounderOrigin.NATURAL
                        ? PhenotypeOrigin.NATURAL_FOUNDER
                        : PhenotypeOrigin.CHICKEN_TRAP_FOUNDER));
        data.generation(0);
        data.bloodlineId("test-" + seed);
        data.pedigree(PedigreeData.EMPTY);
        for (StatType stat : StatType.values()) {
            double normalized = data.phenotypeSnapshot().normalizedAbilities().get(stat);
            data.normalized(stat, normalized);
            data.value(stat, normalized);
        }
        return data;
    }

    private static WonderfulChickenData withTraits(
            WonderfulChickenData source,
            List<ExpressedTrait> traits) {
        PhenotypeSnapshot p = source.phenotypeSnapshot();
        source.phenotypeSnapshot(new PhenotypeSnapshot(
                p.decoderIdentity(),
                p.normalizedAbilities(),
                p.personalityFactors(),
                p.personality(),
                traits,
                p.developmentFactors(),
                p.injuries(),
                p.divineLineageTotalScore(),
                p.divineLineageExpressed()));
        return source;
    }

    private static HaploidGenome haplotypeA(DiploidGenome genome) {
        return new HaploidGenome(
                genome.genomeFormatVersion(),
                genome.chromosomePairs().stream()
                        .map(ChromosomePair::haplotypeA)
                        .toList());
    }

    private static DiploidGenome zeroGenome() {
        int[] lengths = {9216,8192,7168,6144,5120,4096};
        List<ChromosomePair> pairs = new ArrayList<>();
        for (int length : lengths) {
            BitSequence zero = BitSequence.ofPacked(
                    new byte[BitSequence.packedLength(length)], length);
            pairs.add(new ChromosomePair(zero, zero));
        }
        return new DiploidGenome(1, pairs);
    }
}
