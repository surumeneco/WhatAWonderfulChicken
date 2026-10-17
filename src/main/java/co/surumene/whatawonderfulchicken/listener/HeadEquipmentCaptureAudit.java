package co.surumene.whatawonderfulchicken.listener;

/**
 * Distinguishes Paper equipment change notifications from corrections found
 * only by the existing periodic scan. Does not change fallback coverage.
 * All callers run on the main server thread.
 */
final class HeadEquipmentCaptureAudit {
    private long eventNotifications;
    private long eventCorrections;
    private long periodicCorrections;

    void recordEvent(boolean corrected) {
        eventNotifications++;
        if (corrected) eventCorrections++;
    }

    void recordPeriodicCorrection() {
        periodicCorrections++;
    }

    Sample drain() {
        Sample snapshot = new Sample(eventNotifications, eventCorrections, periodicCorrections);
        eventNotifications = 0;
        eventCorrections = 0;
        periodicCorrections = 0;
        return snapshot;
    }

    record Sample(long eventNotifications, long eventCorrections, long periodicCorrections) {
        boolean hasActivity() {
            return eventNotifications > 0 || periodicCorrections > 0;
        }
    }
}
