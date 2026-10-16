package co.surumene.whatawonderfulchicken.task;

import co.surumene.whatawonderfulchicken.data.BehaviorMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Chicken;

/** Stores only currently loaded chickens whose behavior needs periodic follow work. */
final class FollowMembership {
    private final Map<UUID, Chicken> active = new HashMap<>();

    void update(Chicken chicken, BehaviorMode mode) {
        if (mode == BehaviorMode.FOLLOW || mode == BehaviorMode.WAIT) {
            active.put(chicken.getUniqueId(), chicken);
        } else {
            remove(chicken);
        }
    }

    void remove(Chicken chicken) {
        active.remove(chicken.getUniqueId(), chicken);
    }

    List<Chicken> snapshot() {
        return active.isEmpty() ? List.of() : new ArrayList<>(active.values());
    }

    void clear() {
        active.clear();
    }
}
