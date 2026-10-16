package co.surumene.whatawonderfulchicken.task;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class FollowRepathPolicyTest {
    @Test void doesNotRepathWhenValidPathTargetsSameLocation() {
        Vector target = new Vector(10,64,10);
        assertFalse(FollowRepathPolicy.needsRepath(true,target,new Vector(10.25,64,10)));
    }
    @Test void doesRepathAfterPathLostOrSignificantTargetMovement() {
        Vector target = new Vector(10,64,10);
        assertTrue(FollowRepathPolicy.needsRepath(false,target,target));
        assertTrue(FollowRepathPolicy.needsRepath(true,null,target));
        assertTrue(FollowRepathPolicy.needsRepath(true,target,new Vector(10.5,64,10)));
    }
}
