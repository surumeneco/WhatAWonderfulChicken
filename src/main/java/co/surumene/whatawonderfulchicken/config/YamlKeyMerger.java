package co.surumene.whatawonderfulchicken.config;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Adds only absent leaf keys from a bundled YAML file to a user's configuration.
 * Never overwrites a present key, including invalid values and custom translations.
 */
public final class YamlKeyMerger {
    private YamlKeyMerger() {}

    public static int copyMissing(ConfigurationSection target, ConfigurationSection bundled) {
        int added = 0;
        for (String path : bundled.getKeys(true)) {
            if (bundled.isConfigurationSection(path) || hasNonSectionParent(target, path)) continue;
            // isSet checks for an explicit entry, rather than the attached defaults.
            if (target.isSet(path)) continue;
            target.set(path, bundled.get(path));
            added++;
        }
        return added;
    }

    static boolean hasNonSectionParent(ConfigurationSection target, String path) {
        int separator = path.indexOf('.');
        while (separator >= 0) {
            String parent = path.substring(0, separator);
            if (target.isSet(parent) && !target.isConfigurationSection(parent)) return true;
            separator = path.indexOf('.', separator + 1);
        }
        return false;
    }
}
