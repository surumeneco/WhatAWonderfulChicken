package co.surumene.whatawonderfulchicken.task;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class IntegrityControllerBudgetTest {
    @Test
    void maintenanceIsSpreadOverTwentyTicksWithoutDroppingTheLastEntity() {
        for (int count : new int[] {0, 1, 19, 20, 21, 99, 100, 1_000}) {
            int budget = IntegrityController.perTickBudget(count);
            assertTrue(budget >= 1);
            assertTrue(budget * 20 >= count, "incomplete batch for " + count);
            if (count > 20) {
                assertTrue(budget < count, "work should not concentrate in one tick");
            }
        }
    }

    @Test
    void rejectsNegativeCount() {
        assertThrows(IllegalArgumentException.class,
                () -> IntegrityController.perTickBudget(-1));
    }
}
