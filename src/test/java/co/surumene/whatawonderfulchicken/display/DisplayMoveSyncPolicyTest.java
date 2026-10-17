package co.surumene.whatawonderfulchicken.display;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DisplayMoveSyncPolicyTest {
    @Test
    void reactsToPositionOrOrientationButNotUnchangedEvents() {
        assertFalse(DisplayMoveSyncPolicy.shouldSync(false, false));
        assertTrue(DisplayMoveSyncPolicy.shouldSync(true, false));
        assertTrue(DisplayMoveSyncPolicy.shouldSync(false, true));
        assertTrue(DisplayMoveSyncPolicy.shouldSync(true, true));
    }
}
