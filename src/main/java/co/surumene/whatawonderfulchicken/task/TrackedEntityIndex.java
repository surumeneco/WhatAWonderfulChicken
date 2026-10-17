package co.surumene.whatawonderfulchicken.task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Main-thread index that never retains an unloaded instance after its unload notification. */
final class TrackedEntityIndex<T> {
    private final Map<UUID, T> entities = new HashMap<>();

    void track(UUID id, T entity) {
        entities.put(id, entity);
    }

    void untrack(UUID id, T entity) {
        entities.remove(id, entity);
    }

    List<T> snapshot() {
        return new ArrayList<>(entities.values());
    }

    Set<UUID> ids() {
        return entities.keySet();
    }

    int size() {
        return entities.size();
    }
}
