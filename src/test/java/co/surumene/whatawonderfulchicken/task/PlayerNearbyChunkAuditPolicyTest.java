package co.surumene.whatawonderfulchicken.task;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PlayerNearbyChunkAuditPolicyTest {
    private static final UUID WORLD_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID WORLD_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Test void emptyPlayersYieldNoChunkScanning() {
        assertTrue(PlayerNearbyChunkAuditPolicy.nearby(List.of()).isEmpty());
    }

    @Test void includesCenterAndExactlyEightNeighbors() {
        var selected = PlayerNearbyChunkAuditPolicy.nearby(List.of(new PlayerNearbyChunkAuditPolicy.PlayerChunk(WORLD_A, 0, 0)));
        assertEquals(9, selected.size());
        assertTrue(selected.contains(new PlayerNearbyChunkAuditPolicy.ChunkKey(WORLD_A, -1, -1)));
        assertTrue(selected.contains(new PlayerNearbyChunkAuditPolicy.ChunkKey(WORLD_A, 0, 0)));
        assertTrue(selected.contains(new PlayerNearbyChunkAuditPolicy.ChunkKey(WORLD_A, 1, 1)));
    }

    @Test void overlapsDeduplicateButDifferentWorldsStayDistinct() {
        var selected = PlayerNearbyChunkAuditPolicy.nearby(List.of(
            new PlayerNearbyChunkAuditPolicy.PlayerChunk(WORLD_A, 0, 0),
            new PlayerNearbyChunkAuditPolicy.PlayerChunk(WORLD_A, 1, 0),
            new PlayerNearbyChunkAuditPolicy.PlayerChunk(WORLD_B, 0, 0)));
        assertEquals(21, selected.size());
        assertTrue(selected.contains(new PlayerNearbyChunkAuditPolicy.ChunkKey(WORLD_B, 0, 0)));
        assertTrue(selected.contains(new PlayerNearbyChunkAuditPolicy.ChunkKey(WORLD_A, 2, 0)));
    }

    @Test void handlesNegativeChunkCoordinatesWithoutClamping() {
        var selected = PlayerNearbyChunkAuditPolicy.nearby(List.of(new PlayerNearbyChunkAuditPolicy.PlayerChunk(WORLD_A, -10, -8)));
        assertTrue(selected.contains(new PlayerNearbyChunkAuditPolicy.ChunkKey(WORLD_A, -11, -9)));
        assertTrue(selected.contains(new PlayerNearbyChunkAuditPolicy.ChunkKey(WORLD_A, -9, -7)));
    }
}
