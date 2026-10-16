package co.surumene.whatawonderfulchicken.task;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Active rider IDs, maintained by mount and dismount events. */
final class RiderMembership {
    private final Set<UUID> active = new HashSet<>();
    void mount(UUID player) { active.add(player); }
    void dismount(UUID player) { active.remove(player); }
    Set<UUID> snapshot() { return Set.copyOf(active); }
    void clear() { active.clear(); }
}
