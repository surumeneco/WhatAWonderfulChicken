package co.surumene.whatawonderfulchicken.founder;

import co.surumene.whatawonderfulchicken.data.PersonalityFactor;
import co.surumene.whatawonderfulchicken.data.StatType;
import co.surumene.whatawonderfulchicken.genome.WonderfulChickenSynthesisSettings;
import co.surumene.wgl.api.GenomeRandom;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FounderTargetGeneratorTest {
    private final WonderfulChickenFounderTargetGenerator generator =
            new WonderfulChickenFounderTargetGenerator(WonderfulChickenSynthesisSettings.defaults());

    @Test
    void founderTargetsRespectOriginRangesAndPersonalityDistribution() {
        GenomeRandom random = new TestRandom(2026100701L);
        for (int i = 0; i < 1000; i++) {
            FounderTarget natural = generator.generate(FounderOrigin.NATURAL, random);
            FounderTarget trap = generator.generate(FounderOrigin.CHICKEN_TRAP, random);
            for (StatType stat : StatType.values()) {
                assertRange(natural.abilities().get(stat), 0.0, 0.75);
                assertRange(trap.abilities().get(stat), 0.5, 1.5);
            }
            for (PersonalityFactor factor : PersonalityFactor.values()) {
                assertRange(natural.personalityFactors().get(factor), 0.0, 1.0);
                assertRange(trap.personalityFactors().get(factor), 0.0, 1.0);
            }
        }
    }

    @Test
    void sameSeedProducesSameFounderTarget() {
        assertEquals(
                generator.generate(FounderOrigin.CHICKEN_TRAP, new TestRandom(123456789L)),
                generator.generate(FounderOrigin.CHICKEN_TRAP, new TestRandom(123456789L)));
    }

    private static void assertRange(double value, double min, double max) {
        assertTrue(Double.isFinite(value) && value >= min && value <= max,
                () -> value + " outside [" + min + "," + max + "]");
    }

    private static final class TestRandom implements GenomeRandom {
        private final SplittableRandom random;
        private TestRandom(long seed) { random = new SplittableRandom(seed); }
        @Override public long nextLong() { return random.nextLong(); }
        @Override public double nextDouble() { return random.nextDouble(); }
        @Override public int nextInt(int bound) { return random.nextInt(bound); }
        @Override public boolean nextBoolean() { return random.nextBoolean(); }
    }
}
