package co.surumene.whatawonderfulchicken.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyRetryWakeDelayTest {
    @Test
    void schedulesImmediateCapacityRetriesOnNextTickWithoutPollingIdleQueue() {
        assertEquals(1L, LegacyRetryWakeDelay.ticksUntil(10_000L, 10_000L));
    }

    @Test
    void honorsSixtySecondWallClockBackoffBeforeRetry() {
        assertEquals(1200L, LegacyRetryWakeDelay.ticksUntil(60_000L, 0L));
        assertEquals(400L, LegacyRetryWakeDelay.ticksUntil(60_000L, 40_000L));
        assertEquals(1L, LegacyRetryWakeDelay.ticksUntil(60_000L, 59_999L));
    }

    @Test
    void boundsLongDelaysToAllowClockOrConfigChanges() {
        assertEquals(1200L, LegacyRetryWakeDelay.ticksUntil(1_000_000L, 0L));
    }
}
