package co.surumene.whatawonderfulchicken.compat;

import co.surumene.whatawonderfulchicken.WhatAWonderfulChickenPlugin;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Player;
import org.cloudburstmc.math.vector.Vector3f;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.entity.data.GeyserEntityDataTypes;
import org.geysermc.geyser.api.entity.type.GeyserEntity;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.java.ServerSpawnEntityEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GeyserBedrockCompatibility implements BedrockCompatibility, EventRegistrar {
    private static final float VANILLA_CHICKEN_MOUNT_HEIGHT = 0.35f;

    private final WhatAWonderfulChickenPlugin plugin;
    private final GeyserApi api;
    private final Map<UUID, Boolean> javaOnlyDisplays = new ConcurrentHashMap<>();
    private final Map<UUID, Float> bedrockDisplayScales = new ConcurrentHashMap<>();
    private final Map<UUID, SeatState> seatStates = new ConcurrentHashMap<>();

    private GeyserBedrockCompatibility(WhatAWonderfulChickenPlugin plugin, GeyserApi api) {
        this.plugin = plugin;
        this.api = api;
        api.eventBus().subscribe(this, ServerSpawnEntityEvent.class, this::onServerSpawnEntity);
    }

    public static BedrockCompatibility create(WhatAWonderfulChickenPlugin plugin) {
        try {
            if (plugin.getServer().getPluginManager().getPlugin("Geyser-Spigot") == null) {
                plugin.getLogger().info("Geyser-Spigot not found; Bedrock compatibility layer is disabled.");
                return BedrockCompatibility.disabled();
            }
            GeyserApi api = GeyserApi.api();
            if (api == null) {
                plugin.getLogger().warning("Geyser API is not available; Bedrock compatibility layer is disabled.");
                return BedrockCompatibility.disabled();
            }
            plugin.getLogger().info("Geyser Bedrock compatibility layer enabled.");
            return new GeyserBedrockCompatibility(plugin, api);
        } catch (LinkageError | RuntimeException ex) {
            plugin.getLogger().warning("Could not initialize Geyser compatibility: " + ex.getMessage());
            return BedrockCompatibility.disabled();
        }
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public boolean isBedrockPlayer(UUID playerId) {
        return api.connectionByUuid(playerId) != null;
    }

    @Override
    public void registerJavaOnlyDisplay(UUID entityId) {
        javaOnlyDisplays.put(entityId, Boolean.TRUE);
    }

    @Override
    public void unregisterJavaOnlyDisplay(UUID entityId) {
        javaOnlyDisplays.remove(entityId);
    }

    @Override
    public void registerBedrockDisplay(UUID entityId, float scale) {
        bedrockDisplayScales.put(entityId, scale);
        updateBedrockDisplayScale(entityId, scale);
    }

    @Override
    public void updateBedrockDisplayScale(UUID entityId, float scale) {
        bedrockDisplayScales.put(entityId, scale);
        for (GeyserConnection connection : api.onlineConnections()) {
            GeyserEntity entity = connection.entities().byUuid(entityId);
            if (entity != null) {
                entity.override(GeyserEntityDataTypes.SCALE, scale);
            }
        }
    }

    @Override
    public void unregisterBedrockDisplay(UUID entityId) {
        bedrockDisplayScales.remove(entityId);
    }

    @Override
    public void applySeatOffset(Player player, Chicken chicken, double chickenScale) {
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
            if (base == null) {
                return;
            }
            state = new SeatState(chicken.getUniqueId(), base);
            seatStates.put(player.getUniqueId(), state);
        }

        float extraY = VANILLA_CHICKEN_MOUNT_HEIGHT * (float) Math.max(0.0, chickenScale - 1.0);
        Vector3f base = state.baseOffset();
        rider.override(GeyserEntityDataTypes.SEAT_OFFSET,
                Vector3f.from(base.getX(), base.getY() + extraY, base.getZ()));
    }

    @Override
    public void clearSeatOffset(UUID playerId) {
        SeatState removed = seatStates.remove(playerId);
        if (removed == null) {
            return;
        }
        GeyserConnection connection = api.connectionByUuid(playerId);
        if (connection != null) {
            connection.playerEntity().override(GeyserEntityDataTypes.SEAT_OFFSET, null);
        }
    }

    @Override
    public void shutdown() {
        for (UUID playerId : seatStates.keySet().toArray(UUID[]::new)) {
            clearSeatOffset(playerId);
        }
        javaOnlyDisplays.clear();
        bedrockDisplayScales.clear();
        api.eventBus().unregisterAll(this);
    }

    private void onServerSpawnEntity(ServerSpawnEntityEvent event) {
        UUID uuid = event.uuid();
        if (javaOnlyDisplays.containsKey(uuid)) {
            event.setCancelled(true);
            return;
        }
        Float scale = bedrockDisplayScales.get(uuid);
        if (scale != null) {
            event.preSpawnConsumer(entity -> entity.override(GeyserEntityDataTypes.SCALE, scale));
        }
    }

    private record SeatState(UUID vehicleId, Vector3f baseOffset) {
    }
}
