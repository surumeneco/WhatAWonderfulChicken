package co.surumene.whatawonderfulchicken.compat;

import org.bukkit.entity.Chicken;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Optional, Bedrock-only rider seat adjustment. Item displays are not changed. */
public interface BedrockCompatibility {
    void applySeatOffset(Player player, Chicken chicken);

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
        public void applySeatOffset(Player player, Chicken chicken) {
        }

        @Override
        public void clearSeatOffset(UUID playerId) {
        }

        @Override
        public void shutdown() {
        }
    }
}