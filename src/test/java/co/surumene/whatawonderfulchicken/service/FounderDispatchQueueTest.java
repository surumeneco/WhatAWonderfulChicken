package co.surumene.whatawonderfulchicken.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FounderDispatchQueueTest {
    @Test
    void drainsOnlyWhenWorkExistsAndWorkerHasCapacity() {
        FounderDispatchQueue<String> queue = new FounderDispatchQueue<>();
        List<String> submitted = new ArrayList<>();
        int[] capacity = {2};

        assertEquals(0, queue.drain(job -> true, () -> capacity[0], job -> submitted.add(job)));
        queue.offer("first");
        queue.offer("second");
        queue.offer("third");

        assertEquals(2, queue.drain(job -> true, () -> capacity[0],
                job -> { submitted.add(job); capacity[0]--; }));
        assertEquals(List.of("first", "second"), submitted);
        assertEquals(1, queue.size());

        capacity[0] = 1;
        assertEquals(1, queue.drain(job -> true, () -> capacity[0],
                job -> { submitted.add(job); capacity[0]--; }));
        assertEquals(List.of("first", "second", "third"), submitted);
        assertTrue(queue.isEmpty());
    }

    @Test
    void keepsRejectedJobUntilWorkerCompletionWakesQueueAgain() {
        FounderDispatchQueue<String> queue = new FounderDispatchQueue<>();
        List<String> submitted = new ArrayList<>();
        queue.offer("first");
        queue.offer("second");

        assertEquals(0, queue.drain(job -> true, () -> 1, job -> {
            throw new RejectedExecutionException("executor full");
        }));
        assertEquals(2, queue.size());

        assertEquals(2, queue.drain(job -> true, () -> 1, submitted::add));
        assertEquals(List.of("first", "second"), submitted);
    }

    @Test
    void discardsInvalidatedWorkWithoutBlockingFollowingJobs() {
        FounderDispatchQueue<String> queue = new FounderDispatchQueue<>();
        List<String> submitted = new ArrayList<>();
        queue.offer("stale");
        queue.offer("valid");

        assertEquals(1, queue.drain("valid"::equals, () -> 1, submitted::add));
        assertEquals(List.of("valid"), submitted);
        assertTrue(queue.isEmpty());
    }

    @Test
    void allowsCancellationAndNoIdlePolling() {
        FounderDispatchQueue<String> queue = new FounderDispatchQueue<>();
        queue.offer("invalid");
        assertTrue(queue.remove("invalid"));
        assertFalse(queue.remove("invalid"));
        assertTrue(queue.isEmpty());

        int[] calls = {0};
        assertEquals(0, queue.drain(job -> true, () -> { calls[0]++; return 1; },
                job -> { throw new AssertionError("idle queue unexpectedly executed"); }));
        assertEquals(0, calls[0]);
    }
}
