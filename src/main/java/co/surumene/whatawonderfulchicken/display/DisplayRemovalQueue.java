package co.surumene.whatawonderfulchicken.display;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/** Deduplicates display removals and executes them outside entity lifecycle callbacks. */
final class DisplayRemovalQueue {
    private final Set<UUID> pending = new LinkedHashSet<>();

    void queue(UUID entityId) {
        pending.add(entityId);
    }

    boolean contains(UUID entityId) {
        return pending.contains(entityId);
    }

    /** Removals queued during a flush, or not yet completed, remain for the next tick. */
    void flush(Predicate<UUID> tryRemove) {
        if (pending.isEmpty()) return;
        Set<UUID> batch = new LinkedHashSet<>(pending);
        pending.clear();
        for (UUID entityId : batch) {
            if (!tryRemove.test(entityId)) pending.add(entityId);
        }
    }
}
