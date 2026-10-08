package co.surumene.whatawonderfulchicken.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class ConfigCompatibilityTest {
    private YamlConfiguration resource(String path) {
        try (var reader = new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream(path)), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (java.io.IOException error) {
            throw new AssertionError(error);
        }
    }

    private YamlConfiguration legacyConfig() {
        return resource("/legacy-config-1.0.0.yml");
    }

    private YamlConfiguration defaults() {
        return resource("/config.yml");
    }

    @Test
    void originalVersionOneConfigStartsWithoutNewNatureAndTraitEntries() {
        YamlConfiguration old = legacyConfig();
        assertFalse(old.isSet("nature.adjustment"));
        assertFalse(old.isSet("breeding.stat-mutation-rate"));
        assertFalse(old.isSet("breeding.genetic-mutation-rate"));
        assertFalse(old.isSet("traits.haste-radius"));
        assertFalse(old.isSet("traits.alert-min-interval-ticks"));
        ConfigService.ValidationResult result = ConfigService.validateConfiguration(old, defaults());
        assertTrue(result.valid(), () -> String.join("; ", result.errors()));

        // Validation must neither initialize nor overwrite the administrator's config file.
        assertFalse(old.isSet("nature.adjustment"));
        assertEquals(0.4, old.getDouble("breeding.direct-inheritance-rate"), 1e-9);
    }

    @Test
    void reloadingOriginalConfigWithBundledDefaultsAlsoWorks() {
        YamlConfiguration old = legacyConfig();
        YamlConfiguration bundled = defaults();
        old.setDefaults(bundled);
        ConfigService.ValidationResult result = ConfigService.validateConfiguration(old, bundled);
        assertTrue(result.valid(), () -> String.join("; ", result.errors()));
    }

    @Test
    void retainsValidAdministratorOverrides() {
        YamlConfiguration old = legacyConfig();
        old.set("road.speed-multiplier", 1.4);
        old.set("nature.adjustment", 0.15);
        old.set("traits.haste-radius", 9.0);
        assertTrue(ConfigService.validateConfiguration(old, defaults()).valid());
        assertEquals(1.4, old.getDouble("road.speed-multiplier"), 1e-9);
        assertEquals(0.15, old.getDouble("nature.adjustment"), 1e-9);
        assertEquals(9.0, old.getDouble("traits.haste-radius"), 1e-9);
    }

    @Test
    void chickenTrapConfigurationAcceptsLegacyDefaultsAndRejectsInvalidValues() {
        YamlConfiguration existing = legacyConfig();
        YamlConfiguration bundled = defaults();
        assertTrue(ConfigService.validateConfiguration(existing, bundled).valid());

        existing.set("chicken-trap.spawn-chance", 1.1);
        assertTrue(ConfigService.validateConfiguration(existing, bundled).errors().stream()
                .anyMatch(error -> error.contains("chicken-trap.spawn-chance")));
        existing.set("chicken-trap.spawn-chance", 0.01);

        existing.set("chicken-trap.activation-radius-blocks", 0.0);
        assertTrue(ConfigService.validateConfiguration(existing, bundled).errors().stream()
                .anyMatch(error -> error.contains("chicken-trap.activation-radius-blocks")));
        existing.set("chicken-trap.activation-radius-blocks", 10.0);
        assertTrue(ConfigService.validateConfiguration(existing, bundled).valid());
    }

    @Test
    void explicitlyInvalidValuesNeverFallBackSilently() {
        YamlConfiguration old = legacyConfig();
        YamlConfiguration bundled = defaults();
        old.setDefaults(bundled);

        old.set("breeding.stat-mutation-rate", "invalid");
        assertTrue(ConfigService.validateConfiguration(old, bundled).errors()
                .stream().anyMatch(message -> message.contains("breeding.stat-mutation-rate")));

        old.set("breeding.stat-mutation-rate", 0.01);
        old.set("nature.adjustment", 0.9);
        assertTrue(ConfigService.validateConfiguration(old, bundled).errors()
                .stream().anyMatch(message -> message.contains("nature.adjustment")));

        old.set("nature.adjustment", 0.1);
        old.set("traits.haste-radius", "invalid");
        assertTrue(ConfigService.validateConfiguration(old, bundled).errors()
                .stream().anyMatch(message -> message.contains("traits.haste-radius")));

        old.set("traits.haste-radius", 5.0);
        old.set("traits.alert-min-interval-ticks", 9);
        assertTrue(ConfigService.validateConfiguration(old, bundled).errors()
                .stream().anyMatch(message -> message.contains("traits.alert interval")));

        old.set("traits.alert-min-interval-ticks", 10.5);
        assertTrue(ConfigService.validateConfiguration(old, bundled).errors()
                .stream().anyMatch(message -> message.contains("traits.alert interval")));
    }
}
