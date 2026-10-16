package co.surumene.whatawonderfulchicken.task;

import co.surumene.whatawonderfulchicken.data.Trait;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.entity.Chicken;

/** Incrementally tracks chickens whose traits need recurring world interaction. */
final class RuntimeTraitMembership {
    private static final Trait[] TICK_TRAITS = {
        Trait.HINOTORI, Trait.WATAGE, Trait.FUKUTSU, Trait.SAIKUTSU_OUEN,
        Trait.YOME, Trait.CHIKARAKOBU, Trait.MIHARIBAN
    };
    private final Map<UUID, Chicken> active = new HashMap<>();

    static boolean needsTick(Predicate<Trait> hasTrait) {
        for (Trait trait : TICK_TRAITS) {
            if (hasTrait.test(trait)) return true;
        }
        return false;
    }

    void update(Chicken chicken, boolean shouldTick) {
        if (shouldTick) active.put(chicken.getUniqueId(), chicken);
        else remove(chicken);
    }

    void remove(Chicken chicken) {
        active.remove(chicken.getUniqueId(), chicken);
    }

    List<Chicken> snapshot() {
        return List.copyOf(active.values());
    }

    int size() {
        return active.size();
    }

    void clear() {
        active.clear();
    }
}
