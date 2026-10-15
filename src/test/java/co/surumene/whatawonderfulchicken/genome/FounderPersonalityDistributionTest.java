package co.surumene.whatawonderfulchicken.genome;

import co.surumene.whatawonderfulchicken.data.Nature;
import co.surumene.whatawonderfulchicken.data.PersonalityFactor;
import co.surumene.whatawonderfulchicken.founder.FounderOrigin;
import co.surumene.whatawonderfulchicken.founder.WonderfulChickenFounderTargetGenerator;
import co.surumene.wgl.api.GenomeRandom;
import org.junit.jupiter.api.Test;
import java.util.EnumMap;
import java.util.SplittableRandom;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deterministic statistical regression for the *Founder Target* distribution, not WGL meiosis. */
final class FounderPersonalityDistributionTest {
    private static final int SAMPLES = 22_000;

    @Test
    void bothFounderOriginsGiveApproximatelyUniform22FactorClassifications() {
        WonderfulChickenFounderTargetGenerator generator =
                new WonderfulChickenFounderTargetGenerator(WonderfulChickenSynthesisSettings.defaults());
        WonderfulChickenGenomeProfile profile =
                new WonderfulChickenGenomeProfile(WonderfulChickenGenomeSettings.defaults());

        for (FounderOrigin origin : FounderOrigin.values()) {
            EnumMap<Nature, Integer> counts = new EnumMap<>(Nature.class);
            for (Nature nature : Nature.values()) counts.put(nature, 0);
            GenomeRandom random = new FixedRandom(20261009L + origin.ordinal());

            for (int sample = 0; sample < SAMPLES; sample++) {
                var target = generator.generate(origin, random);
                Nature nature = profile.decodePersonality(new EnumMap<PersonalityFactor, Double>(
                        target.personalityFactors()));
                counts.merge(nature, 1, Integer::sum);
            }

            assertEquals(SAMPLES, counts.values().stream().mapToInt(Integer::intValue).sum());
            for (Nature nature : Nature.values()) {
                // Pi/P6 and Pi/Pi have one shared name for P1..P5, so five
                // names get 2/22 of the factor-pair population rather than 1/22.
                boolean combined = nature == Nature.ISOGINBO || nature == Nature.UWA_NO_SORA
                        || nature == Nature.AWATENBO || nature == Nature.TAMEKOMIYA
                        || nature == Nature.KUISHINBO;
                double expected = (combined ? 2.0 : 1.0) / 22.0;
                double observed = counts.get(nature) / (double) SAMPLES;
                assertTrue(Math.abs(observed - expected) < 0.015,
                        () -> origin + " " + nature + ": expected " + expected
                                + " but observed " + observed);
            }
        }
    }

    private static final class FixedRandom implements GenomeRandom {
        private final SplittableRandom delegate;
        FixedRandom(long seed) { delegate = new SplittableRandom(seed); }
        @Override public long nextLong() { return delegate.nextLong(); }
        @Override public int nextInt(int bound) { return delegate.nextInt(bound); }
        @Override public double nextDouble() { return delegate.nextDouble(); }
        @Override public boolean nextBoolean() { return delegate.nextBoolean(); }
    }
}
