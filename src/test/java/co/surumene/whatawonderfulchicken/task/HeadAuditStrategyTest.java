package co.surumene.whatawonderfulchicken.task;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeadAuditStrategyTest {
    @Test
    void periodicFallbackRemainsAvailableButEventOnlySkipsIt() {
        assertTrue(HeadAuditStrategy.shouldPoll(HeadAuditStrategy.Mode.EVENT_WITH_PERIODIC_FALLBACK));
        assertFalse(HeadAuditStrategy.shouldPoll(HeadAuditStrategy.Mode.EVENT_ONLY));
    }
}
