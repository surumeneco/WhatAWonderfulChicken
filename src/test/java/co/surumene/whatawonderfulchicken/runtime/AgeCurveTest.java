package co.surumene.whatawonderfulchicken.runtime;

import co.surumene.whatawonderfulchicken.data.DevelopmentFactor;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class AgeCurveTest {
    private static Map<DevelopmentFactor, Double> factors(double score) {
        EnumMap<DevelopmentFactor, Double> result =
                new EnumMap<>(DevelopmentFactor.class);
        for (DevelopmentFactor factor : DevelopmentFactor.values()) {
            result.put(factor, score);
        }
        return result;
    }

    @Test void midrangeFactorsMatchWwwAgeDefaultsAndSmoothTransitions() {
        AgeCurve age = AgeCurve.from(factors(0.5), 672.0, 2880.0, 4320.0);
        assertEquals(672.0, age.growthEndGameDay());
        assertEquals(3552.0, age.agingStartGameDay());
        assertEquals(7872.0, age.elderGameDay());
        assertEquals(0.5, age.rankReductionAt(0.0));
        assertEquals(0.25, age.rankReductionAt(336.0));
        assertEquals(0.0, age.rankReductionAt(672.0));
        assertEquals(0.0, age.rankReductionAt(3552.0));
        assertEquals(0.75, age.rankReductionAt(5712.0));
        assertEquals(1.5, age.rankReductionAt(7872.0));
        assertEquals(1.5, age.rankReductionAt(1_000_000.0));
    }

    @Test void resistantFactorsVaryCurveWithoutChangingGenomeScores() {
        var source = factors(0.0);
        var fast = AgeCurve.from(source, 672.0, 2880.0, 4320.0);
        var slow = AgeCurve.from(factors(1.0), 672.0, 2880.0, 4320.0);
        assertTrue(fast.growthEndGameDay() > slow.growthEndGameDay());
        assertTrue(fast.maximumAgingReductionRank() > slow.maximumAgingReductionRank());
        assertEquals(0.0, source.get(DevelopmentFactor.MATURITY));
    }

    @Test void rejectsInvalidPhenotypeFactorsAndAgeInputs() {
        var incomplete = factors(0.5);
        incomplete.remove(DevelopmentFactor.AGING_SPEED);
        assertThrows(IllegalArgumentException.class,
                () -> AgeCurve.from(incomplete, 672, 2880, 4320));
        assertThrows(IllegalArgumentException.class,
                () -> AgeCurve.from(factors(0.5), -1, 2880, 4320));
        assertThrows(IllegalArgumentException.class,
                () -> AgeCurve.from(factors(0.5), 672, Double.NaN, 4320));
        var age = AgeCurve.from(factors(0.5), 672, 2880, 4320);
        assertThrows(IllegalArgumentException.class,
                () -> age.rankReductionAt(Double.POSITIVE_INFINITY));
    }
}
