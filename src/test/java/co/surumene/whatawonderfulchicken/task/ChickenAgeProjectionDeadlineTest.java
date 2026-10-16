package co.surumene.whatawonderfulchicken.task;

import co.surumene.whatawonderfulchicken.runtime.AgeCurve;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ChickenAgeProjectionDeadlineTest {
    static final AgeCurve curve = new AgeCurve(3, .5, 20, 30, 1);
    @Test void skipsPeakButNotInjuryOrGrowth() {
        long a = 1000L;
        assertEquals(a + 20 * 24000, ChickenAgeProjectionDeadline.next(a+10*24000, a, curve, List.of()));
        assertEquals(a + 15 * 24000, ChickenAgeProjectionDeadline.next(a+10*24000, a, curve, List.of(15.0)));
        assertEquals(2200L, ChickenAgeProjectionDeadline.next(2041L, a, curve, List.of()));
    }
}
