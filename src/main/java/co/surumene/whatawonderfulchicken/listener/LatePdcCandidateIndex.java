package co.surumene.whatawonderfulchicken.listener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Chicken;

/** Tracks loaded unmarked chickens that could receive a delayed third-party PDC. */
final class LatePdcCandidateIndex {
    private final Map<UUID, Chicken> tracked = new HashMap<>();

    void track(Chicken chicken) {
        tracked.put(chicken.getUniqueId(), chicken);
    }

    void untrack(Chicken chicken) {
        tracked.remove(chicken.getUniqueId(), chicken);
    }

    void untrack(UUID id) {
        tracked.remove(id);
    }

    List<UUID> ids() {
        return new ArrayList<>(tracked.keySet());
    }

    void clear() {
        tracked.clear();
    }
}
