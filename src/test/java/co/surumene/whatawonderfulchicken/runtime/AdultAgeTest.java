package co.surumene.whatawonderfulchicken.runtime;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class AdultAgeTest {
    @Test void babiesDoNotStartTheAdultClock() {
        assertEquals(0L, AdultAge.start(false, 0L, 240_000L));
        assertEquals(0.0, AdultAge.days(0L, 250_000L));
    }

    @Test void adulthoodStartsAtObservationAndDoesNotRestart() {
        long first = AdultAge.start(true, 0L, 240_000L);
        assertEquals(240_000L, first);
        assertEquals(first, AdultAge.start(true, first, 480_000L));
        assertEquals(10.0, AdultAge.days(first, 480_000L));
    }

    @Test void timeZeroIsRepresentedWithoutLosingUnknownSentinel() {
        assertEquals(1L, AdultAge.start(true, 0L, 0L));
        assertEquals(0.0, AdultAge.days(1L, 0L));
    }

    @Test void nonMonotoneObservedTimeDoesNotReverseAge() {
        assertEquals(0.0, AdultAge.days(500_000L, 490_000L));
        assertThrows(IllegalArgumentException.class, () -> AdultAge.days(-1, 0));
    }
}
