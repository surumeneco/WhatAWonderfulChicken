package co.surumene.whatawonderfulchicken.task;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TraitTaskActivityPolicyTest {
    @Test void startsOnlyWithActiveEffectSources() {
        assertFalse(TraitTaskActivityPolicy.shouldRun(0));
        assertTrue(TraitTaskActivityPolicy.shouldRun(1));
        assertTrue(TraitTaskActivityPolicy.shouldRun(200));
    }
}
