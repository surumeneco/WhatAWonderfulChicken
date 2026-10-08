package co.surumene.whatawonderfulchicken.chickentrap;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ChickenTrapPolicyTest {
    @Test void checksOnlyNaturalMidnightArrival() {
        assertTrue(ChickenTrapPolicy.naturalCheck(17_999L,18_000L));
        assertFalse(ChickenTrapPolicy.naturalCheck(17_998L,18_000L));
        assertFalse(ChickenTrapPolicy.naturalCheck(18_000L,18_000L));
        assertFalse(ChickenTrapPolicy.naturalCheck(17_999L,18_001L));
        assertTrue(ChickenTrapPolicy.naturalCheck(41_999L,42_000L));
    }
    @Test void moonCycleIsEightDaysAndNewMoonIsFourthDay() {
        assertFalse(ChickenTrapPolicy.isNewMoon(18_000L));
        assertTrue(ChickenTrapPolicy.isNewMoon(4L*24_000L + 18_000L));
        assertTrue(ChickenTrapPolicy.isNewMoon(12L*24_000L + 18_000L));
        assertFalse(ChickenTrapPolicy.isNewMoon(5L*24_000L + 18_000L));
    }
    @Test void dawnExpiresEvenWhenTimeIsSkipped() {
        assertTrue(ChickenTrapPolicy.dawnCrossed(22_999L,23_000L));
        assertTrue(ChickenTrapPolicy.dawnCrossed(22_000L,24_000L));
        assertTrue(ChickenTrapPolicy.dawnCrossed(22_500L,25_000L));
        assertFalse(ChickenTrapPolicy.dawnCrossed(23_000L,23_001L));
        assertFalse(ChickenTrapPolicy.dawnCrossed(23_000L,22_999L));
    }
    @Test void chanceBoundariesAndUniformDiskSampling() {
        assertFalse(ChickenTrapPolicy.roll(0.01,0.01));
        assertTrue(ChickenTrapPolicy.roll(0.009,0.01));
        assertFalse(ChickenTrapPolicy.roll(0,0));
        assertTrue(ChickenTrapPolicy.roll(0,1));
        assertThrows(IllegalArgumentException.class,()->ChickenTrapPolicy.roll(0.1,1.5));
        var center=ChickenTrapPolicy.offset(0,0.5);
        assertEquals(0,center.x(),1e-9);
        assertEquals(0,center.z(),1e-9);
        var edge=ChickenTrapPolicy.offset(1,0);
        assertEquals(32,edge.x(),1e-9);
        assertEquals(0,edge.z(),1e-9);
    }
}
