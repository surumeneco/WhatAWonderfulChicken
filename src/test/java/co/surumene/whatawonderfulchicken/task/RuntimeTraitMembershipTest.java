package co.surumene.whatawonderfulchicken.task;

import co.surumene.whatawonderfulchicken.data.Trait;
import org.bukkit.entity.Chicken;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

final class RuntimeTraitMembershipTest {
    @Test void onlyTickBasedTraitsRequirePeriodicWork() {
        assertFalse(RuntimeTraitMembership.needsTick(trait -> false));
        assertTrue(RuntimeTraitMembership.needsTick(trait -> trait == Trait.HINOTORI));
        assertTrue(RuntimeTraitMembership.needsTick(trait -> trait == Trait.MIHARIBAN));
    }

    @Test void activeIndexRemovesUnchangedAndSupersededChickens() {
        RuntimeTraitMembership index = new RuntimeTraitMembership();
        UUID uuid = UUID.randomUUID();
        Chicken first = fakeChicken(uuid), current = fakeChicken(uuid);
        index.update(first, true);
        assertEquals(1, index.size());
        index.update(current, true);
        index.remove(first);
        assertSame(current, index.snapshot().getFirst());
        index.update(current, false);
        assertTrue(index.snapshot().isEmpty());
    }

    private static Chicken fakeChicken(UUID id) {
        return (Chicken) Proxy.newProxyInstance(Chicken.class.getClassLoader(),
                new Class<?>[]{Chicken.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "isValid" -> true;
                    default -> method.getReturnType() == boolean.class ? false : null;
                });
    }
}
