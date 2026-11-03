package co.surumene.whatawonderfulchicken.listener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadEquipmentCaptureAuditTest {
    @Test
    void countsEquipmentEventsAndCorrectionsFromPeriodicFallbackSeparately() {
        HeadEquipmentCaptureAudit audit = new HeadEquipmentCaptureAudit();
        audit.recordEvent(false);
        audit.recordEvent(true);
        audit.recordEvent(true);
        audit.recordPeriodicCorrection();

        HeadEquipmentCaptureAudit.Sample sample = audit.drain();
        assertEquals(3, sample.eventNotifications());
        assertEquals(2, sample.eventCorrections());
        assertEquals(1, sample.periodicCorrections());
        assertTrue(sample.hasActivity());
    }

    @Test
    void readingAndResettingEmptyTelemetryDoesNotReportActivity() {
        HeadEquipmentCaptureAudit audit = new HeadEquipmentCaptureAudit();
        assertFalse(audit.drain().hasActivity());

        audit.recordPeriodicCorrection();
        assertTrue(audit.drain().hasActivity());
        assertFalse(audit.drain().hasActivity());
    }
}
