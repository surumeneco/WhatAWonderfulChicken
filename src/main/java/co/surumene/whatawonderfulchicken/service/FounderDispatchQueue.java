package co.surumene.whatawonderfulchicken.service;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

/**
 * Main-thread queue of worker submissions. Call drain only on an enqueue or a
 * worker-completion wakeup; no per-tick idle task is necessary.
 */
final class FounderDispatchQueue<T> {
    private final ArrayDeque<T> waiting = new ArrayDeque<>();

    void offer(T job) {
        waiting.addLast(Objects.requireNonNull(job, "job"));
    }

    boolean remove(T job) {
        return waiting.remove(job);
    }

    boolean isEmpty() {
        return waiting.isEmpty();
    }

    int size() {
        return waiting.size();
    }

    void clear() {
        waiting.clear();
    }

    /**
     * Submits work only while the bounded executor has room. Rejected work is
     * restored at the front so a later completion wakeup cannot lose it.
     * The validity predicate filters jobs invalidated by entity replacement.
     */
    int drain(Predicate<T> valid, IntSupplier remainingCapacity, Consumer<T> submit) {
        Objects.requireNonNull(valid, "valid");
        Objects.requireNonNull(remainingCapacity, "remainingCapacity");
        Objects.requireNonNull(submit, "submit");
        int submitted = 0;
        while (!waiting.isEmpty() && remainingCapacity.getAsInt() > 0) {
            T job = waiting.removeFirst();
            if (!valid.test(job)) continue;
            try {
                submit.accept(job);
                submitted++;
            } catch (RejectedExecutionException rejected) {
                waiting.addFirst(job);
                break;
            }
        }
        return submitted;
    }
}
