package co.surumene.whatawonderfulchicken.task;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Pure, immutable scan footprint; world membership is part of chunk identity. */
public final class PlayerNearbyChunkAuditPolicy {
    private PlayerNearbyChunkAuditPolicy() {}

    public record PlayerChunk(UUID worldId, int chunkX, int chunkZ) {
        public PlayerChunk { Objects.requireNonNull(worldId); }
    }

    public record ChunkKey(UUID worldId, int chunkX, int chunkZ) {
        public ChunkKey { Objects.requireNonNull(worldId); }
    }

    /** Distinct 3-by-3 areas around each online player's current chunk. */
    public static Set<ChunkKey> nearby(Collection<PlayerChunk> players) {
        Set<ChunkKey> result = new LinkedHashSet<>();
        for (PlayerChunk player : players) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    result.add(new ChunkKey(player.worldId(),
                            player.chunkX() + dx, player.chunkZ() + dz));
                }
            }
        }
        return result;
    }
}
