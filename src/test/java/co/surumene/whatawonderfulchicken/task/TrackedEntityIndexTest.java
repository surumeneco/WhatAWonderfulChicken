package co.surumene.whatawonderfulchicken.task;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TrackedEntityIndexTest {
    @Test
    void tracksEntitiesIncrementallyAndPreservesIdentityOnReplacement() {
        TrackedEntityIndex<Object> index = new TrackedEntityIndex<>();
        UUID id = UUID.randomUUID();
        Object oldEntity = new Object();
        Object newEntity = new Object();
        index.track(id, oldEntity);
        assertSame(oldEntity, index.snapshot().getFirst());
        index.track(id, newEntity);
        index.untrack(id, oldEntity);
        assertEquals(1, index.size());
        assertSame(newEntity, index.snapshot().getFirst());
        index.untrack(id, newEntity);
        assertTrue(index.snapshot().isEmpty());
    }

    @Test
    void returnsDefensiveSnapshotsAndStableKeyViewForAgeCacheMaintenance() {
        TrackedEntityIndex<Object> index = new TrackedEntityIndex<>();
        UUID id = UUID.randomUUID();
        Object entity = new Object();
        index.track(id, entity);
        var copy = index.snapshot();
        index.untrack(id, entity);
        assertEquals(1, copy.size());
        assertTrue(index.ids().isEmpty());
    }
}
