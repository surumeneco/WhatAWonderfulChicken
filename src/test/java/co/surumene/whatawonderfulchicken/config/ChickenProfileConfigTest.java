package co.surumene.whatawonderfulchicken.config;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import static org.junit.jupiter.api.Assertions.*;

class ChickenProfileConfigTest {
    private static YamlConfiguration defaults() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                ChickenProfileConfigTest.class.getClassLoader().getResourceAsStream("config.yml")));
    }
    @Test void bundledKeysReproduceExistingProfileSettings() {
        ChickenProfileConfig.Values settings = ChickenProfileConfig.read(defaults());
        assertEquals(co.surumene.whatawonderfulchicken.genome.WonderfulChickenGenomeSettings.defaults(),
                settings.decoder());
        assertEquals(co.surumene.whatawonderfulchicken.genome.WonderfulChickenSynthesisSettings.defaults(),
                settings.synthesizer());
        assertEquals(co.surumene.whatawonderfulchicken.breeding.WonderfulChickenBreedingSettings.defaults(),
                settings.breeding());
    }
    @Test void changedFounderBreedingAndDecoderSettingsAreLoaded() {
        var yaml = defaults();
        yaml.set("founder-target.chicken-trap.abilities.mean", 0.9);
        yaml.set("genome-profile.decoder.personality.dominant-gap", 0.12);
        yaml.set("genome-profile.breeding-policy.wild-trait.weak-parent-multiplier", 3.0);
        ChickenProfileConfig.Values values = ChickenProfileConfig.read(yaml);
        assertEquals(0.9, values.trapAbilities().mean());
        assertEquals(0.12, values.decoder().dominantGap());
        assertEquals(3.0, values.breeding().hatenkoTrait().weakParentMultiplier());
    }
    @Test void malformedGenomeProfileFailsWithoutFallback() {
        var yaml = defaults();
        yaml.set("genome-profile.decoder.trait.strong-gap", "broken");
        assertThrows(IllegalArgumentException.class, () -> ChickenProfileConfig.read(yaml));
    }
}
