package co.surumene.whatawonderfulchicken.task;

import java.util.Collection;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Nearby candidate query, followed by the same exact radial inclusion test. */
final class HasteNearbyPlayers {
    private HasteNearbyPlayers() {}

    static Collection<Player> find(World world, Location source, double radius) {
        return Objects.requireNonNull(world, "world").getNearbyPlayers(source, radius);
    }

    static boolean contains(Location source, Location player, double radiusSquared) {
        return source.distanceSquared(player) <= radiusSquared;
    }
}
