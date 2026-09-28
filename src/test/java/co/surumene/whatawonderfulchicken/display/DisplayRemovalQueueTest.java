package co.surumene.whatawonderfulchicken.display;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisplayRemovalQueueTest {
    @Test
    void deduplicatesRemovalsAndRetriesOnlyUnfinishedEntities() {
        DisplayRemovalQueue queue = new DisplayRemovalQueue();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        queue.queue(first);
        queue.queue(first);
        queue.queue(second);

        List<UUID> attempted = new ArrayList<>();
        queue.flush(id -> {
            attempted.add(id);
            return id.equals(second);
        });

        assertEquals(List.of(first, second), attempted);
        assertTrue(queue.contains(first));
        assertFalse(queue.contains(second));

        attempted.clear();
        queue.flush(id -> {
            attempted.add(id);
            return true;
        });

        assertEquals(List.of(first), attempted);
        assertFalse(queue.contains(first));
    }

    @Test
    void queuesAddedDuringRemovalAreNotFlushedUntilNextTick() {
        DisplayRemovalQueue queue = new DisplayRemovalQueue();
        UUID oldDisplay = UUID.randomUUID();
        UUID replacement = UUID.randomUUID();
        queue.queue(oldDisplay);

        List<UUID> attempted = new ArrayList<>();
        queue.flush(id -> {
            attempted.add(id);
            queue.queue(replacement);
            return true;
        });

        assertEquals(List.of(oldDisplay), attempted);
        assertFalse(queue.contains(oldDisplay));
        assertTrue(queue.contains(replacement));

        queue.flush(id -> {
            attempted.add(id);
            return true;
        });

        assertEquals(List.of(oldDisplay, replacement), attempted);
        assertFalse(queue.contains(replacement));
    }

    @Test
    void repeatedCleanupRequestsDoNotExtendTheQueue() {
        DisplayRemovalQueue queue = new DisplayRemovalQueue();
        UUID id = UUID.randomUUID();
        queue.queue(id);
        queue.queue(id);
        queue.flush(ignored -> true);

        int[] calls = {0};
        queue.flush(ignored -> {
            calls[0]++;
            return true;
        });
        assertEquals(0, calls[0]);
    }
}
