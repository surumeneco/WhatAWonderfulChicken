package co.surumene.whatawonderfulchicken.task;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.UUID;
import org.bukkit.entity.Chicken;
import static org.junit.jupiter.api.Assertions.*;

final class RecoveryMembershipTest {
    private static Chicken chicken(UUID id) {
        return (Chicken) Proxy.newProxyInstance(Chicken.class.getClassLoader(),
                new Class<?>[] {Chicken.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "isValid" -> true;
                    case "toString" -> "Chicken-" + id;
                    default -> method.getReturnType().isPrimitive() ? false : null;
                });
    }

    @Test
    void fullyRecoveredChickensDoNotConsumePerTickWork() {
        RecoveryMembership candidates = new RecoveryMembership();
        Chicken full = chicken(UUID.randomUUID());
        Chicken depleted = chicken(UUID.randomUUID());
        candidates.update(full, false);
        candidates.update(depleted, true);
        assertEquals(1, candidates.size());
        assertSame(depleted, candidates.snapshot().getFirst());
        candidates.update(depleted, false);
        assertEquals(0, candidates.size());
    }

    @Test
    void newerEntitySupersedesOldEntityWithSameUuid() {
        RecoveryMembership candidates = new RecoveryMembership();
        UUID uuid = UUID.randomUUID();
        Chicken old = chicken(uuid), replacement = chicken(uuid);
        candidates.update(old, true);
        candidates.update(replacement, true);
        candidates.remove(old);
        assertSame(replacement, candidates.snapshot().getFirst());
    }
}
