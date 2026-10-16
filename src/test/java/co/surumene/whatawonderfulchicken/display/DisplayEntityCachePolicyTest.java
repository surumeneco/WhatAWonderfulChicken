package co.surumene.whatawonderfulchicken.display;

import java.lang.reflect.Proxy;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class DisplayEntityCachePolicyTest {
    private static Entity entity(UUID id, boolean valid) {
        return (Entity) Proxy.newProxyInstance(Entity.class.getClassLoader(),
                new Class<?>[]{Entity.class}, (p,m,a)->switch(m.getName()) {
                    case "getUniqueId" -> id;
                    case "isValid" -> valid;
                    default -> m.getReturnType()==boolean.class ? false : null;
                });
    }
    @Test void acceptsCurrentEntityButRejectsReplacementAndUnload() {
        UUID expected = UUID.randomUUID();
        assertTrue(DisplayEntityCachePolicy.reusable(entity(expected,true),expected,false));
        assertFalse(DisplayEntityCachePolicy.reusable(entity(expected,false),expected,false));
        assertFalse(DisplayEntityCachePolicy.reusable(entity(expected,true),expected,true));
        assertFalse(DisplayEntityCachePolicy.reusable(entity(UUID.randomUUID(),true),expected,false));
        assertFalse(DisplayEntityCachePolicy.reusable(null,expected,false));
    }
}
