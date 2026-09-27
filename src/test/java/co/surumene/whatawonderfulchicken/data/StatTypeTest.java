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

    @Test
    void runtimeCopyDoesNotShareMutableStatsOrStamina() {
        WonderfulChickenData source = new WonderfulChickenData();
        source.value(StatType.STAMINA, 20.0);
        source.normalized(StatType.STAMINA, 0.7);
        source.currentStamina(9.0);
        source.behaviorMode(BehaviorMode.FOLLOW);
        WonderfulChickenData working = source.copy();

        working.value(StatType.STAMINA, 2.0);
        working.normalized(StatType.STAMINA, 0.0);
        working.currentStamina(0.0);
        working.behaviorMode(BehaviorMode.WAIT);

        assertEquals(20.0, source.value(StatType.STAMINA));
        assertEquals(0.7, source.normalized(StatType.STAMINA));
        assertEquals(9.0, source.currentStamina());
        assertEquals(BehaviorMode.FOLLOW, source.behaviorMode());
    }
}
