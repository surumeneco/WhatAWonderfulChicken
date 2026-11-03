package co.surumene.whatawonderfulchicken.task;

import co.surumene.wcl.api.traits.PotionRefreshDecision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression contract for the WCL helper used by TraitController.apply().
 * The Paper potion registry is unavailable in ordinary unit tests.
 */
class TraitControllerInfiniteEffectTest {
    @Test
    void riderNightVisionDoesNotReplaceInfiniteEqualOrStrongerEffect() {
        // TraitController uses 220 ticks as its night-vision refresh threshold.
        assertFalse(PotionRefreshDecision.shouldApply(0, -1, 0, 220));
        assertFalse(PotionRefreshDecision.shouldApply(1, -1, 0, 220));
    }

    @Test
    void riderStrengthDoesNotReplaceInfiniteSameLevelEffect() {
        // TraitController uses 40 ticks for the other trait effects.
        assertFalse(PotionRefreshDecision.shouldApply(0, -1, 0, 40));
        assertTrue(PotionRefreshDecision.shouldApply(0, 40, 0, 40));
    }
}
