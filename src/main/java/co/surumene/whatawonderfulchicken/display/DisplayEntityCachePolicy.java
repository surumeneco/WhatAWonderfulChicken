package co.surumene.whatawonderfulchicken.display;

import java.util.UUID;
import org.bukkit.entity.Entity;

final class DisplayEntityCachePolicy {
    private DisplayEntityCachePolicy() {}

    static boolean reusable(Entity candidate, UUID expected, boolean superseded) {
        return candidate != null && candidate.isValid() && !superseded
                && expected.equals(candidate.getUniqueId());
    }
}
