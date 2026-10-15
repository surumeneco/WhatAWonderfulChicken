package co.surumene.whatawonderfulchicken.runtime;

import co.surumene.whatawonderfulchicken.data.InjuryPhenotype;
import co.surumene.whatawonderfulchicken.data.StatType;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class AgeInjuryModifierTest {
    private static final AgeCurve CURVE =
            new AgeCurve(100.0, 0.5, 500.0, 900.0, 1.5);

    @Test void fullMaturityHasNoAgePenalty() {
        var result = AgeInjuryModifier.adjust(
                StatType.GROUND_SPEED, 0.8, 200, CURVE, List.of());
        assertEquals(0.8, result.baseNormalized());
        assertEquals(0.8, result.effectiveNormalized());
        assertFalse(result.injuryActive());
    }

    @Test void agePenaltyDependsOnStatAndKeepsSizeUnchanged() {
        var speed = AgeInjuryModifier.adjust(
                StatType.GROUND_SPEED, 1.0, 900, CURVE, List.of());
        var size = AgeInjuryModifier.adjust(
                StatType.SIZE, 1.0, 900, CURVE, List.of());
        assertEquals(1.0 - 1.5 / 9.0, speed.effectiveNormalized(), 1e-12);
        assertEquals(1.0, size.effectiveNormalized());
    }

    @Test void latentInjuryActivatesAtOnsetIncludingSize() {
        var injury = new InjuryPhenotype(StatType.SIZE, 60.0, 1.8);
        var before = AgeInjuryModifier.adjust(
                StatType.SIZE, 0.75, 59.0, CURVE, List.of(injury));
        var at = AgeInjuryModifier.adjust(
                StatType.SIZE, 0.75, 60.0, CURVE, List.of(injury));
        assertFalse(before.injuryActive());
        assertEquals(0.75, before.effectiveNormalized());
        assertTrue(at.injuryActive());
        assertEquals(0.55, at.effectiveNormalized(), 1e-12);
    }

    @Test void ageAndInjuryNeverMakeEffectiveStatNegative() {
        var injury = new InjuryPhenotype(StatType.GROUND_SPEED, 0.0, 7.0);
        var result = AgeInjuryModifier.adjust(
                StatType.GROUND_SPEED, 0.1, 900, CURVE, List.of(injury));
        assertEquals(0.0, result.effectiveNormalized());
        assertEquals(0.1, result.baseNormalized());
    }

    @Test void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class,
                () -> AgeInjuryModifier.adjust(
                        StatType.SIZE, 0.5, -1, CURVE, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> AgeInjuryModifier.adjust(
                        StatType.SIZE, Double.NaN, 3, CURVE, List.of()));
    }
}
