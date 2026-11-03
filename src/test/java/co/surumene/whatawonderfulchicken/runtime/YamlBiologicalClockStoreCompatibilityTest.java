package co.surumene.whatawonderfulchicken.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.Path;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

final class YamlBiologicalClockStoreCompatibilityTest {
    @TempDir Path folder;

    @Test void consumerFileAndDataContractRemainExactlyTheSame() {
        Path path = folder.resolve("plugin").resolve("biological-clock.yml");
        var store = new YamlBiologicalClockStateStore(path.toFile(), Logger.getLogger("clock"));
        assertNull(store.load());
        var original = new BiologicalClockState("world", 500L, 12_345L);
        store.save(original);
        assertEquals(original, store.load());
        var yaml = YamlConfiguration.loadConfiguration(path.toFile());
        assertEquals("world", yaml.getString("clock-world"));
        assertEquals(500L, yaml.getLong("ignored-skip-offset"));
        assertEquals(12_345L, yaml.getLong("last-biological-time"));
    }
}
