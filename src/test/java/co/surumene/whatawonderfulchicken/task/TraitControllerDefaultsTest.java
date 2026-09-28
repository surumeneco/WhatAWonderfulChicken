package co.surumene.whatawonderfulchicken.task;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TraitControllerDefaultsTest {
    @Test
    void bundledRangeDefaultsMatchApprovedSpecification() throws Exception {
        try (var stream = Objects.requireNonNull(getClass().getResourceAsStream("/config.yml"));
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(reader);
            assertEquals(7.5, config.getDouble("traits.haste-radius"), 1e-9);
            assertEquals(20.0, config.getDouble("traits.alert-radius"), 1e-9);
            assertEquals(10, config.getInt("traits.alert-min-interval-ticks"));
            assertEquals(80, config.getInt("traits.alert-max-interval-ticks"));
        }
    }

}
