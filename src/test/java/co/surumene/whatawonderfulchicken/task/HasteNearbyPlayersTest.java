package co.surumene.whatawonderfulchicken.task;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class HasteNearbyPlayersTest {
    @Test void hasteUsesSpatialCandidateQuery() {
        AtomicInteger spatialCalls = new AtomicInteger();
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getPlayers")) {
                        throw new AssertionError("Do not scan every player");
                    }
                    if (method.getName().equals("getNearbyPlayers")) {
                        assertEquals(5.0, (Double) args[1], 0.0);
                        spatialCalls.incrementAndGet();
                        return List.<Player>of();
                    }
                    return method.getReturnType() == boolean.class ? false : null;
                });
        assertTrue(HasteNearbyPlayers.find(world, new Location(null, 0, 0, 0), 5).isEmpty());
        assertEquals(1, spatialCalls.get());
    }

    @Test void cubeDiagonalOutsideSphereIsExcluded() {
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class}, (proxy, method, args) -> null);
        var origin = new Location(world, 0, 0, 0);
        assertTrue(HasteNearbyPlayers.contains(origin, new Location(world, 4, 0, 0), 25));
        assertFalse(HasteNearbyPlayers.contains(origin, new Location(world, 4, 4, 0), 25));
    }
}
