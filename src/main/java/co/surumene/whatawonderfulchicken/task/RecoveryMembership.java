package co.surumene.whatawonderfulchicken.task;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Chicken;

/** Tracks only chickens whose non-riding stamina can actually recover. */
final class RecoveryMembership {
    private final Map<UUID, Chicken> candidates = new HashMap<>();

    void update(Chicken chicken, boolean needsRecovery) {
        if (needsRecovery) candidates.put(chicken.getUniqueId(), chicken);
        else remove(chicken);
    }

    void remove(Chicken chicken) {
        candidates.remove(chicken.getUniqueId(), chicken);
    }

    List<Chicken> snapshot() {
        return List.copyOf(candidates.values());
    }

    int size() {
        return candidates.size();
    }

    void clear() {
        candidates.clear();
    }
}
