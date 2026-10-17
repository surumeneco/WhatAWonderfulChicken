package co.surumene.whatawonderfulchicken.config;
import co.surumene.whatawonderfulchicken.genome.*;
import co.surumene.whatawonderfulchicken.breeding.*;

final class ConfigProfilePaths {
    private ConfigProfilePaths() {}
    static String path(Class<?> owner, String prefix, String name) {
        String key = name.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase();
        if (owner == WonderfulChickenGenomeSettings.class) {
            if (name.startsWith("serious") || name.equals("dominantGap")) return "genome-profile.decoder.personality." + key;
            if (name.startsWith("trait")) return "genome-profile.decoder.trait." + key.substring(6);
            if (name.startsWith("injury")) return "genome-profile.decoder.injury." + key.substring(7);
            if (name.startsWith("divine")) return "genome-profile.decoder.divine." + key.substring(7);
            return "genome-profile.synthesizer." + key;
        }
        if (owner == WonderfulChickenSynthesisSettings.class) {
            if (name.equals("abilityDistribution")) return "founder-target.natural.abilities";
            if (name.equals("personalityDistribution")) return "founder-target.personality";
            if (name.equals("developmentDistribution")) return "founder-target.development";
            if (name.endsWith("Genes")) return "genome-profile.synthesizer.genes-per-target."
                    + name.substring(0, name.length() - 5).toLowerCase();
            return "genome-profile.synthesizer." + key;
        }
        if (owner == WonderfulChickenBreedingSettings.class && name.equals("hatenkoTrait"))
            return prefix + ".wild-trait";
        if (owner == WonderfulChickenSynthesisSettings.Range.class) return prefix + "-" + key;
        return prefix + "." + key;
    }
}
