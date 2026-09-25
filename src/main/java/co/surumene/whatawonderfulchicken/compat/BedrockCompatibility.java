package co.surumene.whatawonderfulchicken.compat;

import org.bukkit.entity.Chicken;
import org.bukkit.entity.Player;

import java.util.UUID;

public interface BedrockCompatibility {
    boolean isBedrockPlayer(UUID playerId);

    void registerJavaOnlyDisplay(UUID entityId);

    void unregisterJavaOnlyDisplay(UUID entityId);

    void registerBedrockDisplay(UUID entityId, float scale);

    void updateBedrockDisplayScale(UUID entityId, float scale);

    void unregisterBedrockDisplay(UUID entityId);

    void applySeatOffset(Player player, Chicken chicken, double chickenScale);

    void clearSeatOffset(UUID playerId);

    void shutdown();

    static BedrockCompatibility disabled() {
        return Disabled.INSTANCE;
    }

    final class Disabled implements BedrockCompatibility {
        private static final Disabled INSTANCE = new Disabled();

        private Disabled() {
        }

        @Override
        public boolean isBedrockPlayer(UUID playerId) {
            return false;
        }

        @Override
        public void registerJavaOnlyDisplay(UUID entityId) {
        }

        @Override
        public void unregisterJavaOnlyDisplay(UUID entityId) {
        }

        @Override
        public void registerBedrockDisplay(UUID entityId, float scale) {
        }

        @Override
        public void updateBedrockDisplayScale(UUID entityId, float scale) {
        }

        @Override
        public void unregisterBedrockDisplay(UUID entityId) {
        }

        @Override
        public void applySeatOffset(Player player, Chicken chicken, double chickenScale) {
        }

        @Override
        public void clearSeatOffset(UUID playerId) {
        }

        @Override
        public void shutdown() {
        }
    }
}
