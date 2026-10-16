package co.surumene.whatawonderfulchicken.task;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class RiderMembershipTest {
    @Test void tracksOnlyActiveRidersAndHandlesDismount() {
        RiderMembership index = new RiderMembership();
        UUID a=UUID.randomUUID(), b=UUID.randomUUID();
        assertTrue(index.snapshot().isEmpty());
        index.mount(a);index.mount(b);index.mount(a);
        assertEquals(2,index.snapshot().size());
        index.dismount(a);
        assertEquals(java.util.Set.of(b),index.snapshot());
        index.dismount(b);
        assertTrue(index.snapshot().isEmpty());
    }
}
