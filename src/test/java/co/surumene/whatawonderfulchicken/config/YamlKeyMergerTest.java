package co.surumene.whatawonderfulchicken.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class YamlKeyMergerTest {
    @TempDir Path directory;

    private YamlConfiguration resource(String path) {
        try (var reader = new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream(path)), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
    }

    @Test
    void upgradesOriginalConfigToDiskWithoutReplacingOverridesOrUnknownKeys() throws Exception {
        YamlConfiguration old = resource("/legacy-config-1.0.0.yml");
        YamlConfiguration defaults = resource("/config.yml");
        old.set("natural-spawn.chance", 0.25);
        old.set("custom.setting", "keep me");
        File file = directory.resolve("config.yml").toFile();
        old.save(file);

        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        loaded.setDefaults(defaults);
        assertTrue(ConfigService.validateConfiguration(loaded, defaults).valid());
        assertTrue(YamlKeyMerger.mergeAndSave(loaded, defaults, file) > 0);

        YamlConfiguration persisted = YamlConfiguration.loadConfiguration(file);
        assertTrue(persisted.isSet("nature.adjustment"));
        assertTrue(persisted.isSet("breeding.stat-mutation-rate"));
        assertTrue(persisted.isSet("breeding.genetic-mutation-rate"));
        assertTrue(persisted.isSet("traits.haste-radius"));
        assertTrue(persisted.isSet("traits.egg-gold-chance"));
        assertEquals(0.10, persisted.getDouble("nature.adjustment"), 1e-9);
        assertEquals(0.25, persisted.getDouble("natural-spawn.chance"), 1e-9);
        assertEquals("keep me", persisted.getString("custom.setting"));

        String firstSave = Files.readString(file.toPath());
        assertEquals(0, YamlKeyMerger.mergeAndSave(persisted, defaults, file));
        assertEquals(firstSave, Files.readString(file.toPath()), "Already-upgraded config must not be rewritten");
    }

    @Test
    void upgradesOriginalJapaneseAndEnglishLanguageFilesWithoutOverwritingTranslations() throws Exception {
        for (String name : new String[]{"ja_jp.yml", "en_us.yml"}) {
            YamlConfiguration old = resource("/legacy-lang/" + name);
            YamlConfiguration defaults = resource("/lang/" + name);
            old.set("gui.title", "Custom title");
            old.set("custom.message", "Preserve this");
            File file = directory.resolve(name).toFile();
            old.save(file);

            assertTrue(YamlKeyMerger.mergeAndSave(old, defaults, file) > 0);
            YamlConfiguration persisted = YamlConfiguration.loadConfiguration(file);
            assertEquals("Custom title", persisted.getString("gui.title"));
            assertEquals("Preserve this", persisted.getString("custom.message"));
            assertFalse(defaults.contains("nature.majime.description"),
                    "Personality descriptions are obsolete in distributed language files");
            assertEquals(defaults.getString("trait.kin_no_tamago.description"),
                    persisted.getString("trait.kin_no_tamago.description"));
            assertEquals(defaults.getString("gui.ancestor_genetics"),
                    persisted.getString("gui.ancestor_genetics"));

            String firstSave = Files.readString(file.toPath());
            assertEquals(0, YamlKeyMerger.mergeAndSave(persisted, defaults, file));
            assertEquals(firstSave, Files.readString(file.toPath()),
                    "Already-upgraded language file must not be rewritten");
        }
    }

    @Test
    void distributedLanguagesCoverEveryCurrentNatureAndTrait() {
        for (String name : new String[]{"ja_jp.yml", "en_us.yml"}) {
            YamlConfiguration language = resource("/lang/" + name);
            for (var nature : co.surumene.whatawonderfulchicken.data.Nature.values()) {
                String base = "nature." + nature.key();
                assertNotNull(language.getString(base + ".name"), name + ": " + base);
                assertFalse(language.contains(base + ".description"),
                        "Personality flavor descriptions are no longer published");
            }
            for (var trait : co.surumene.whatawonderfulchicken.data.Trait.values()) {
                String base = "trait." + trait.key();
                assertNotNull(language.getString(base + ".name"), name + ": " + base);
                assertNotNull(language.getString(base + ".description"), name + ": " + base);
            }
        }
    }

    @Test
    void partialFileOnlyAddsMissingLeavesAndPreservesComments() throws Exception {
        Path file = directory.resolve("comments.yml");
        Files.writeString(file, "# operator note\nnature:\n  adjustment: 0.2 # custom adjustment\n");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file.toFile());
        YamlConfiguration defaults = resource("/config.yml");
        assertTrue(YamlKeyMerger.mergeAndSave(config, defaults, file.toFile()) > 0);
        YamlConfiguration persisted = YamlConfiguration.loadConfiguration(file.toFile());
        assertEquals(0.2, persisted.getDouble("nature.adjustment"), 1e-9);
        assertTrue(Files.readString(file).contains("operator note"));
        assertTrue(Files.readString(file).contains("custom adjustment"));
        assertEquals(defaults.getComments("runtime.age.clock-world"),
                persisted.getComments("runtime.age.clock-world"),
                "New runtime clock key must retain explanatory comments");
        assertEquals(defaults.getComments("runtime.age"),
                persisted.getComments("runtime.age"),
                "New runtime age section must retain its bundled comments");
    }

    @Test
    void explicitScalarParentDoesNotGetOverwrittenByNestedDefaults() {
        YamlConfiguration config = resource("/legacy-config-1.0.0.yml");
        config.set("nature", "incorrect scalar");
        int added = YamlKeyMerger.copyMissing(config, resource("/config.yml"));
        assertTrue(added > 0, "Other missing keys should still be available for migration");
        assertEquals("incorrect scalar", config.get("nature"));
        assertFalse(config.isSet("nature.adjustment"));
        assertFalse(ConfigService.validateConfiguration(config, resource("/config.yml")).valid());
    }
}
