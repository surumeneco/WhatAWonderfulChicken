package co.surumene.whatawonderfulchicken.task;

import co.surumene.whatawonderfulchicken.data.BehaviorMode;
import java.lang.reflect.Proxy;
import java.util.UUID;
import org.bukkit.entity.Chicken;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class FollowMembershipTest {
    private static Chicken chicken(UUID id) {
        return (Chicken) Proxy.newProxyInstance(Chicken.class.getClassLoader(),
            new Class<?>[]{Chicken.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "isValid" -> true;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> method.getReturnType() == boolean.class ? false : null;
            });
    }

    @Test void onlyActiveModesNeedPeriodicFollowWork() {
        FollowMembership candidates = new FollowMembership();
        Chicken wander = chicken(UUID.randomUUID());
        Chicken wait = chicken(UUID.randomUUID());
        Chicken follow = chicken(UUID.randomUUID());
        candidates.update(wander, BehaviorMode.WANDER);
        candidates.update(wait, BehaviorMode.WAIT);
        candidates.update(follow, BehaviorMode.FOLLOW);
        assertEquals(2, candidates.snapshot().size());
        assertFalse(candidates.snapshot().contains(wander));
        assertTrue(candidates.snapshot().contains(wait));
        assertTrue(candidates.snapshot().contains(follow));
    }

    @Test void modeChangeAndInstanceReplacementRemoveOnlyCurrentInstance() {
        FollowMembership candidates = new FollowMembership();
        UUID id = UUID.randomUUID();
        Chicken old = chicken(id), replacement = chicken(id);
        candidates.update(old, BehaviorMode.FOLLOW);
        candidates.update(replacement, BehaviorMode.FOLLOW);
        candidates.remove(old);
        assertSame(replacement, candidates.snapshot().getFirst());
        candidates.update(replacement, BehaviorMode.WANDER);
        assertEquals(0, candidates.snapshot().size());
    }
}
