package co.surumene.whatawonderfulchicken.compat;

import co.surumene.whatawonderfulchicken.WhatAWonderfulChickenPlugin;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Player;
import org.cloudburstmc.math.vector.Vector3f;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.entity.data.GeyserEntityDataTypes;
import org.geysermc.geyser.api.entity.type.GeyserEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Bedrock-only rider positioning; no visual entity changes. */
public final class GeyserBedrockCompatibility implements BedrockCompatibility {
    private static final float VANILLA_CHICKEN_MOUNT_HEIGHT = 0.35f;
    private final GeyserApi api;
    private final Map<UUID, SeatState> seatStates = new ConcurrentHashMap<>();

    private GeyserBedrockCompatibility(GeyserApi api) {
        this.api = api;
    }

    public static BedrockCompatibility create(WhatAWonderfulChickenPlugin plugin) {
        try {
            GeyserApi api = GeyserApi.api();
            if (api == null) {
                plugin.getLogger().warning("Geyser API unavailable; seat adjustment disabled.");
                return BedrockCompatibility.disabled();
            }
            plugin.getLogger().info("Geyser rider seat adjustment enabled.");
            return new GeyserBedrockCompatibility(api);
        } catch (LinkageError | RuntimeException ex) {
            plugin.getLogger().warning("Geyser seat adjustment unavailable: " + ex.getMessage());
            return BedrockCompatibility.disabled();
        }
    }

    @Override
    public void applySeatOffset(Player player, Chicken chicken) {
        GeyserConnection connection = api.connectionByUuid(player.getUniqueId());
        if (connection == null) {
            seatStates.remove(player.getUniqueId());
            return;
        }

        GeyserEntity rider = connection.playerEntity();
        GeyserEntity vehicle = rider.vehicle();
        if (vehicle == null || vehicle.uuid() == null || !vehicle.uuid().equals(chicken.getUniqueId())) {
            clearSeatOffset(player.getUniqueId());
            return;
        }

        SeatState state = seatStates.get(player.getUniqueId());
        if (state == null || !state.vehicleId().equals(chicken.getUniqueId())) {
            rider.override(GeyserEntityDataTypes.SEAT_OFFSET, null);
            Vector3f base = rider.value(GeyserEntityDataTypes.SEAT_OFFSET);
            if (base == null) return;
            state = new SeatState(chicken.getUniqueId(), base);
            seatStates.put(player.getUniqueId(), state);
        }

        float extraY = (float) (Math.max(0.0,
                chicken.getBoundingBox().getHeight() - VANILLA_CHICKEN_MOUNT_HEIGHT) * (2.0 / 3.0));
        Vector3f base = state.baseOffset();
        Vector3f corrected = Vector3f.from(base.getX(), base.getY() + extraY, base.getZ());
        if (!corrected.equals(rider.override(GeyserEntityDataTypes.SEAT_OFFSET))) {
            rider.override(GeyserEntityDataTypes.SEAT_OFFSET, corrected);
        }
    }

    @Override
    public void clearSeatOffset(UUID playerId) {
        SeatState removed = seatStates.remove(playerId);
        if (removed == null) return;
        GeyserConnection connection = api.connectionByUuid(playerId);
        if (connection != null) connection.playerEntity().override(GeyserEntityDataTypes.SEAT_OFFSET, null);
    }

    @Override
    public void shutdown() {
        for (UUID playerId : seatStates.keySet().toArray(UUID[]::new)) {
            clearSeatOffset(playerId);
        }
    }

    private record SeatState(UUID vehicleId, Vector3f baseOffset) {}
}
