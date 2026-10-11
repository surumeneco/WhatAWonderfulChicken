package co.surumene.whatawonderfulchicken.genome;

import co.surumene.whatawonderfulchicken.founder.FounderOrigin;
import co.surumene.whatawonderfulchicken.founder.WonderfulChickenFounderSynthesizer;
import co.surumene.wgl.api.AnchorSeed;
import co.surumene.wgl.api.BitSequence;
import co.surumene.wgl.api.ChromosomeTemplate;
import co.surumene.wgl.api.GenomeRandom;
import co.surumene.wgl.api.SynthesisResult;
import co.surumene.wgl.core.EngineConfig;
import co.surumene.wgl.core.WonderfulGenomeEngine;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

final class WonderfulChickenFounderMarkerVariationTest {
    private final WonderfulChickenGenomeProfile profile =
            new WonderfulChickenGenomeProfile(WonderfulChickenGenomeSettings.defaults());

    @Test
    void founderMarkerAnchorsVaryIndependentlyWithoutLosingHomology() {
        for (int chromosome = 0; chromosome < profile.backbone().chromosomes().size(); chromosome++) {
            ChromosomeTemplate template = profile.backbone().chromosomes().get(chromosome);
            AnchorSeed first = template.markerLocus().first();
            AnchorSeed second = template.markerLocus().second();

            BitSequence a = profile.founderTemplateBits(
                    chromosome, 0, template, new ChoiceRandom(1, 0));
            BitSequence b = profile.founderTemplateBits(
                    chromosome, 1, template, new ChoiceRandom(48, 13));

            assertEquals(1, window(a, first).hammingDistance(first.canonicalBits()));
            assertEquals(0, window(a, second).hammingDistance(second.canonicalBits()));
            assertEquals(1, window(b, first).hammingDistance(first.canonicalBits()));
            assertEquals(1, window(b, second).hammingDistance(second.canonicalBits()));
            assertTrue(window(a, first).hammingDistance(window(b, first)) <= 2);
            assertTrue(window(a, second).hammingDistance(window(b, second)) <= 2);

            List<AnchorSeed> otherAnchors = template.anchors().stream()
                    .filter(anchor -> !anchor.equals(first) && !anchor.equals(second))
                    .toList();
            for (AnchorSeed anchor : otherAnchors) {
                assertEquals(anchor.canonicalBits(), window(a, anchor));
                assertEquals(anchor.canonicalBits(), window(b, anchor));
            }

            assertEquals(template.templateBits(), profile.founderTemplateBits(
                    chromosome, 0, template, new ChoiceRandom(0, 0)));
        }
    }

    @Test
    void independentFounderSynthesisDoesNotKeepTheCanonicalMarker() {
        WonderfulGenomeEngine engine = WonderfulGenomeEngine.create(EngineConfig.defaults());
        WonderfulChickenGenomeProfile synthesisProfile = new WonderfulChickenGenomeProfile(
                WonderfulChickenGenomeSettings.defaults(),
                WonderfulChickenSynthesisSettings.defaults(),
                engine.geneSequenceCodec());
        WonderfulChickenFounderSynthesizer synthesizer =
                new WonderfulChickenFounderSynthesizer(engine, synthesisProfile);

        SynthesisResult.Success a = assertInstanceOf(SynthesisResult.Success.class,
                synthesizer.synthesize(FounderOrigin.NATURAL, 2026101201L).result());
        SynthesisResult.Success b = assertInstanceOf(SynthesisResult.Success.class,
                synthesizer.synthesize(FounderOrigin.NATURAL, 2026101202L).result());

        String first = engine.marker(synthesisProfile.backbone(), a.genome()).formatted();
        String second = engine.marker(synthesisProfile.backbone(), b.genome()).formatted();
        assertNotEquals(first, second, "independent natural founders must not share a fixed marker");
        assertEquals(6, first.split("-").length);
        assertEquals(6, second.split("-").length);
    }

    private static BitSequence window(BitSequence genome, AnchorSeed anchor) {
        return genome.slice(anchor.position(), anchor.position() + 48);
    }

    private static final class ChoiceRandom implements GenomeRandom {
        private final Queue<Integer> choices = new ArrayDeque<>();

        private ChoiceRandom(int... values) {
            for (int value : values) choices.add(value);
        }

        @Override public long nextLong() { return 0L; }
        @Override public double nextDouble() { return 0.5; }
        @Override public boolean nextBoolean() { return false; }

        @Override
        public int nextInt(int bound) {
            assertEquals(49, bound);
            return choices.remove();
        }
    }
}
