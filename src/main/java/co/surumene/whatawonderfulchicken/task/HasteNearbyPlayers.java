package co.surumene.whatawonderfulchicken.task;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import java.util.Collection;

/** Preserves the consumer-facing query helper while delegating Paper details to WCL. */
final class HasteNearbyPlayers {
    private HasteNearbyPlayers() {}

    static Collection<Player> find(World world, Location source, double radius) {
        return co.surumene.wcl.plugin.spatial.NearbyPlayerQuery.find(world,
                source, radius);
    }

    static boolean contains(Location source, Location player, double radiusSquared) {
        return co.surumene.wcl.plugin.spatial.NearbyPlayerQuery.insideSphere(
                source, player, radiusSquared);
    }
}
