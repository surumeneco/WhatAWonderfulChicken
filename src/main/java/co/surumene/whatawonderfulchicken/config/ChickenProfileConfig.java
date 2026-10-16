package co.surumene.whatawonderfulchicken.config;
import co.surumene.whatawonderfulchicken.genome.*;
import co.surumene.whatawonderfulchicken.breeding.*;
import org.bukkit.configuration.ConfigurationSection;

public final class ChickenProfileConfig {
    private ChickenProfileConfig() {}
    public record Values(WonderfulChickenGenomeSettings decoder,
            WonderfulChickenSynthesisSettings synthesizer,
            WonderfulChickenBreedingSettings breeding,
            WonderfulChickenSynthesisSettings.Distribution trapAbilities) {}
    public static Values read(ConfigurationSection config) {
        var genome = RecordConfigurationParser.load(config,
                WonderfulChickenGenomeSettings.defaults(), "");
        var synthesis = RecordConfigurationParser.load(config,
                WonderfulChickenSynthesisSettings.defaults(), "");
        var breeding = RecordConfigurationParser.load(config,
                WonderfulChickenBreedingSettings.defaults(), "genome-profile.breeding-policy");
        var trap = RecordConfigurationParser.load(config,
                new WonderfulChickenSynthesisSettings.Distribution(0.42, 0.22),
                "founder-target.chicken-trap.abilities");
        if (synthesis.recognizableRegionMaxRatio() + synthesis.noncodingRegionMinRatio() > 1.0)
            throw new IllegalArgumentException("Genome profile region ratios must sum to <= 1");
        return new Values(genome, synthesis, breeding, trap);
    }
}
