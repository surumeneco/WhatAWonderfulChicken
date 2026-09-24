package co.surumene.whatawonderfulchicken.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatTypeTest {
    @Test
    void maxHealthCanonicalizesToWholeHp() {
        assertEquals(20.0, StatType.MAX_HEALTH.canonicalizeValue(19.6));
        assertEquals(19.0, StatType.MAX_HEALTH.canonicalizeValue(19.4));
    }

    @Test
    void otherStatsRemainContinuous() {
        assertEquals(2.35, StatType.SIZE.canonicalizeValue(2.35));
    }
}
