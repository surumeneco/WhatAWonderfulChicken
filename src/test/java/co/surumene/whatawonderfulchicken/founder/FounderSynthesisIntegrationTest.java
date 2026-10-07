package co.surumene.whatawonderfulchicken.founder;

import co.surumene.whatawonderfulchicken.data.DevelopmentFactor;
import co.surumene.whatawonderfulchicken.data.PersonalityFactor;
import co.surumene.whatawonderfulchicken.data.StatType;
import co.surumene.whatawonderfulchicken.genome.WonderfulChickenGenomeProfile;
import co.surumene.whatawonderfulchicken.genome.WonderfulChickenGenomeSettings;
import co.surumene.whatawonderfulchicken.genome.WonderfulChickenSynthesisSettings;
import co.surumene.wgl.api.DecodedGene;
import co.surumene.wgl.api.GenomeAddress;
import co.surumene.wgl.api.GenomeRandom;
import co.surumene.wgl.api.SynthesisAddressPlan;
import co.surumene.wgl.api.SynthesisContext;
import co.surumene.wgl.api.SynthesisResult;
import co.surumene.wgl.core.EngineConfig;
import co.surumene.wgl.core.WonderfulGenomeEngine;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

final class FounderSynthesisIntegrationTest {
    private final WonderfulGenomeEngine engine = WonderfulGenomeEngine.create(EngineConfig.defaults());
    private final WonderfulChickenGenomeSettings genomeSettings =
            WonderfulChickenGenomeSettings.defaults();
    private final WonderfulChickenSynthesisSettings synthesisSettings =
            WonderfulChickenSynthesisSettings.defaults();
    private final WonderfulChickenGenomeProfile profile =
            new WonderfulChickenGenomeProfile(
                    genomeSettings,
                    synthesisSettings,
                    engine.geneSequenceCodec());

    @Test
    void personalitySynthesisPlanKeepsBothSignsWhilePreservingScore() {
        SynthesisAddressPlan plan = profile.synthesisPlan(
                new GenomeAddress(0x03, 0x00),
                0.62,
                SynthesisContext.defaults(),
                engine.standardRandom(42L));

        assertTrue(plan.positiveSaturation() > 0.0);
        assertTrue(plan.negativeSaturation() > 0.0);
        assertEquals(0.24,
                plan.positiveSaturation() - plan.negativeSaturation(),
                1.0e-12);
    }

    @Test
    void naturalFounderDoesNotCreateExtraordinaryContribution() {
        WonderfulChickenSynthesisTarget target = WonderfulChickenSynthesisTarget.from(
                fixedFounder(FounderOrigin.NATURAL, 0.70),
                genomeSettings,
                synthesisSettings,
                engine.standardRandom(1001L));

        for (StatType stat : StatType.values()) {
            assertEquals(0.70, target.baseAbilityTargets().get(stat), 1.0e-12);
            assertEquals(0.0, target.extraordinaryTargets().get(stat), 1.0e-12);
        }
    }

    @Test
    void chickenTrapTargetAboveOneIsSeparatedIntoBaseAndExtraordinaryLayers() {
        WonderfulChickenSynthesisTarget target = WonderfulChickenSynthesisTarget.from(
                fixedFounder(FounderOrigin.CHICKEN_TRAP, 1.20),
                genomeSettings,
                synthesisSettings,
                engine.standardRandom(1002L));

        for (StatType stat : StatType.values()) {
            double base = target.baseAbilityTargets().get(stat);
            double extraordinary = target.extraordinaryTargets().get(stat);
            assertTrue(base <= 1.0);
            assertTrue(extraordinary > 0.0);
            assertEquals(1.20, base + extraordinary, 1.0e-12);
        }
    }

    @Test
    void naturalFounderGenomeSuppliesNeitherDivineNorExtraordinaryGenes() {
        WonderfulChickenFounderSynthesizer synthesizer =
                new WonderfulChickenFounderSynthesizer(engine, profile);
        SynthesisResult.Success success = requireSuccess(
                synthesizer.synthesize(FounderOrigin.NATURAL, 2026100713L).result());

        assertFalse(success.decoded().decodedGenome().physicalGenes().stream()
                .filter(DecodedGene::addressValid)
                .anyMatch(gene -> gene.address().equals(new GenomeAddress(0x05, 0x00))));
        assertFalse(success.decoded().decodedGenome().physicalGenes().stream()
                .filter(DecodedGene::addressValid)
                .anyMatch(gene -> gene.address().type() == 0x07));
    }

    @Test
    void founderPipelineRoundTripsAndIsReproducible() {
        WonderfulChickenFounderSynthesizer synthesizer =
                new WonderfulChickenFounderSynthesizer(engine, profile);

        for (FounderOrigin origin : FounderOrigin.values()) {
            FounderGenomeSynthesis first =
                    synthesizer.synthesize(origin, 2026100710L + origin.ordinal());
            FounderGenomeSynthesis second =
                    synthesizer.synthesize(origin, 2026100710L + origin.ordinal());

            SynthesisResult.Success a = requireSuccess(first.result());
            SynthesisResult.Success b = requireSuccess(second.result());

            assertEquals(6, a.genome().chromosomePairs().size());
            assertArrayEquals(engine.encode(a.genome()), engine.encode(b.genome()));
            assertEquals(first.founderTarget(), second.founderTarget());
            assertTrue(first.synthesisTarget().isSatisfied(
                    a.decoded().decodedGenome(),
                    EngineConfig.defaults().synthesizer().convergenceTolerance()));
        }
    }

    private static FounderTarget fixedFounder(FounderOrigin origin, double ability) {
        EnumMap<StatType, Double> abilities = new EnumMap<>(StatType.class);
        for (StatType stat : StatType.values()) abilities.put(stat, ability);

        EnumMap<PersonalityFactor, Double> personality = new EnumMap<>(PersonalityFactor.class);
        for (PersonalityFactor factor : PersonalityFactor.values()) personality.put(factor, 0.5);

        EnumMap<DevelopmentFactor, Double> development = new EnumMap<>(DevelopmentFactor.class);
        for (DevelopmentFactor factor : DevelopmentFactor.values()) development.put(factor, 0.5);

        return new FounderTarget(origin, abilities, personality, List.of(), development);
    }

    private static SynthesisResult.Success requireSuccess(SynthesisResult result) {
        if (result instanceof SynthesisResult.Success success) return success;
        SynthesisResult.Failure failure = (SynthesisResult.Failure) result;
        fail("synthesis failed: " + failure.reason() + " / " + failure.detail());
        throw new AssertionError("unreachable");
    }
}
