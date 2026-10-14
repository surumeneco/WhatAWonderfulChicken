package co.surumene.whatawonderfulchicken.command;

import co.surumene.whatawonderfulchicken.data.Trait;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class CommandInfoLocalizationTest {
    @Test
    void bothLanguageFilesContainAllNewInfoSectionsAndExistingTraitDescriptions() throws Exception {
        for (String language : new String[]{"ja_jp", "en_us"}) {
            String resource = "lang/" + language + ".yml";
            try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(stream, resource);
                var yaml = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
                for (String section : new String[]{"basic", "pedigree", "behavior",
                        "abilities", "traits", "age", "injuries"}) {
                    String key = "command.info_section_" + section;
                    assertNotNull(yaml.getString(key), resource + " " + key);
                }
                for (String key : new String[]{"info_current_age", "info_age_stage",
                        "info_growth_end", "info_aging_start", "info_elder_day",
                        "info_max_reduction", "info_active_lineage", "info_inactive_lineage"}) {
                    assertNotNull(yaml.getString("command." + key), resource + " command." + key);
                }
                for (Trait trait : Trait.values()) {
                    String key = "trait." + trait.key() + ".description";
                    assertNotNull(yaml.getString(key), resource + " " + key);
                }
            }
        }
    }
}
