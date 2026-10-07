package co.surumene.whatawonderfulchicken.founder;

import co.surumene.whatawonderfulchicken.genome.WonderfulChickenGenomeProfile;
import co.surumene.whatawonderfulchicken.genome.WonderfulChickenGenomeSettings;
import co.surumene.whatawonderfulchicken.genome.WonderfulChickenSynthesisSettings;
import co.surumene.wgl.api.GenomeAddress;
import co.surumene.wgl.api.SynthesisAddressPlan;
import co.surumene.wgl.api.SynthesisContext;
import co.surumene.wgl.api.SynthesisResult;
import co.surumene.wgl.core.EngineConfig;
import co.surumene.wgl.core.WonderfulGenomeEngine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

final class FounderSynthesisIntegrationTest {
    private final WonderfulGenomeEngine engine = WonderfulGenomeEngine.create(EngineConfig.defaults());
    private final WonderfulChickenGenomeProfile profile =
            new WonderfulChickenGenomeProfile(
                    WonderfulChickenGenomeSettings.defaults(),
                    WonderfulChickenSynthesisSettings.defaults(),
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
    void founderPipelineRoundTripsAndIsReproducible() {
        WonderfulChickenFounderSynthesizer synthesizer =
                new WonderfulChickenFounderSynthesizer(engine, profile);

        for (FounderOrigin origin : FounderOrigin.values()) {
            FounderGenomeSynthesis first = synthesizer.synthesize(origin, 2026100710L + origin.ordinal());
            FounderGenomeSynthesis second = synthesizer.synthesize(origin, 2026100710L + origin.ordinal());

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

    private static SynthesisResult.Success requireSuccess(SynthesisResult result) {
        if (result instanceof SynthesisResult.Success success) return success;
        SynthesisResult.Failure failure = (SynthesisResult.Failure) result;
        fail("synthesis failed: " + failure.reason() + " / " + failure.detail());
        throw new AssertionError("unreachable");
    }
}
