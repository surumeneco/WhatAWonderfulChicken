package co.surumene.whatawonderfulchicken.service;

import co.surumene.wcl.api.runtime.BoundedDispatchQueue;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

/**
 * Consumer-local facade preserving invalid-job filtering and existing callers.
 * WCL provides only FIFO admission; the chicken plugin owns the executor and wakeups.
 */
final class FounderDispatchQueue<T> {
    private final BoundedDispatchQueue<T> waiting = new BoundedDispatchQueue<>();

    void offer(T job) {
        waiting.offer(job);
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

    int drain(Predicate<T> valid, IntSupplier remainingCapacity, Consumer<T> submit) {
        return waiting.drain(valid, remainingCapacity, submit);
    }
}
