package co.surumene.whatawonderfulchicken.listener;

import java.lang.reflect.Proxy;
import java.util.UUID;
import org.bukkit.entity.Chicken;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class LatePdcCandidateIndexTest {
    private static Chicken chicken(UUID id) {
        return (Chicken) Proxy.newProxyInstance(Chicken.class.getClassLoader(),
                new Class<?>[]{Chicken.class}, (p, m, a) -> switch (m.getName()) {
                    case "getUniqueId" -> id;
                    case "hashCode" -> System.identityHashCode(p);
                    case "equals" -> p == a[0];
                    default -> m.getReturnType() == boolean.class ? false : null;
                });
    }

    @Test void indexesOnlyTrackedChickenIdsAndHandlesReplacement() {
        LatePdcCandidateIndex candidates = new LatePdcCandidateIndex();
        UUID id = UUID.randomUUID();
        Chicken old = chicken(id);
        Chicken replacement = chicken(id);
        candidates.track(old);
        candidates.track(replacement);
        candidates.untrack(old);
        assertEquals(java.util.List.of(id), candidates.ids());
        candidates.untrack(replacement);
        assertTrue(candidates.ids().isEmpty());
    }

    @Test void neverAddsDuplicateAuditsForTheSameChicken() {
        LatePdcCandidateIndex candidates = new LatePdcCandidateIndex();
        Chicken c = chicken(UUID.randomUUID());
        candidates.track(c);
        candidates.track(c);
        assertEquals(1, candidates.ids().size());
    }
}
