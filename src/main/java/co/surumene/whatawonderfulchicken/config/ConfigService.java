package co.surumene.whatawonderfulchicken.config;

import co.surumene.whatawonderfulchicken.WhatAWonderfulChickenPlugin;
import co.surumene.whatawonderfulchicken.data.StatType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ConfigService {
    private final WhatAWonderfulChickenPlugin plugin;
    private final YamlConfiguration defaults;
    private Set<Material> cachedRoadBlocks;

    public ConfigService(WhatAWonderfulChickenPlugin plugin) {
        this.plugin = plugin;
        try (var stream = plugin.getResource("config.yml")) {
            if (stream == null) throw new IllegalStateException("Bundled config.yml is missing");
            this.defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load bundled config.yml", ex);
        }
    }

    public FileConfiguration config() { return plugin.getConfig(); }

    public ValidationResult validate(ConfigurationSection config) {
        ValidationResult result = validateConfiguration(config, defaults);
        if (!result.valid()) return result;
        String name = config.getString("runtime.age.clock-world",
                defaults.getString("runtime.age.clock-world", "world"));
        if (org.bukkit.Bukkit.getWorld(name) == null) {
            return ValidationResult.error("runtime.age.clock-world does not exist: " + name);
        }
        return result;
    }

    static ValidationResult validateConfiguration(ConfigurationSection config, ConfigurationSection defaults) {
        List<String> errors = new ArrayList<>();
        // A scalar in place of a section is an explicit invalid value, not a missing key.
        Set<String> nonSections = new LinkedHashSet<>();
        for (String path : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(path)) continue;
            int separator = path.indexOf('.');
            while (separator >= 0) {
                String parent = path.substring(0, separator);
                if (config.isSet(parent) && !config.isConfigurationSection(parent)) nonSections.add(parent);
                separator = path.indexOf('.', separator + 1);
            }
        }
        for (String path : nonSections) errors.add(path + " must be a configuration section");
        for (StatType stat : StatType.values()) {
            double min = numberSetting(config, defaults, "stats." + stat.configName() + ".min");
            double max = numberSetting(config, defaults, "stats." + stat.configName() + ".max");
            if (!Double.isFinite(min) || !Double.isFinite(max) || min >= max) {
                errors.add("stats." + stat.configName() + ": min must be finite and < max");
            }
            String display = config.getString("stats." + stat.configName() + ".display", "");
            if (!Set.of("none", "value", "rank", "both").contains(display.toLowerCase(Locale.ROOT))) {
                errors.add("stats." + stat.configName() + ".display must be none/value/rank/both");
            }
        }
        String policy = config.getString("stats.range-change-policy", "");
        if (!Set.of("preserve-value", "preserve-normalized").contains(policy)) {
            errors.add("stats.range-change-policy must be preserve-value or preserve-normalized");
        }
        validateProbability(config, defaults, "natural-spawn.chance", errors);
        validateProbability(config, defaults, "breeding.direct-inheritance-rate", errors);
        validateProbability(config, defaults, "breeding.stat-mutation-rate", errors);
        validateProbability(config, defaults, "breeding.genetic-mutation-rate", errors);
        validateProbability(config, defaults, "traits.egg-gold-chance", errors);
        validateProbability(config, defaults, "traits.egg-netherite-conditional-chance", errors);
        double adjustment = numberSetting(config, defaults, "nature.adjustment");
        if (!Double.isFinite(adjustment) || adjustment < 0.0 || adjustment >= 2.0 / 3.0)
            errors.add("nature.adjustment must be >= 0 and < 2/3 (including Kuse Mashi)");
        double maxNormalized = numberSetting(config, defaults, "natural-spawn.max-normalized");
        if (maxNormalized < 1.0 || !Double.isFinite(maxNormalized)) errors.add("natural-spawn.max-normalized must be >= 1.0");
        double naturalMean = numberSetting(config, defaults, "natural-spawn.distribution.mean");
        if (!Double.isFinite(naturalMean) || naturalMean < 0.0 || naturalMean > maxNormalized) {
            errors.add("natural-spawn.distribution.mean must be finite and between 0.0 and natural-spawn.max-normalized");
        }
        positive(config, defaults, "natural-spawn.distribution.standard-deviation", errors);
        positive(config, defaults, "breeding.gaussian.spread-factor", errors);
        positive(config, defaults, "breeding.gaussian.minimum-standard-deviation", errors);
        positive(config, defaults, "flight.stamina-consumption-per-second", errors);
        nonNegative(config, defaults, "flight.recovery-delay-seconds", errors);
        nonNegative(config, defaults, "feeding.heal-per-seed", errors);
        positive(config, defaults, "road.speed-multiplier", errors);
        positive(config, defaults, "traits.haste-radius", errors);
        positive(config, defaults, "traits.alert-radius", errors);
        int minAlert = integerSetting(config, defaults, "traits.alert-min-interval-ticks");
        int maxAlert = integerSetting(config, defaults, "traits.alert-max-interval-ticks");
        if (minAlert < 10 || maxAlert < minAlert) errors.add("traits.alert interval must satisfy 10 <= min <= max");
        if (integerSetting(config, defaults, "road.check-interval-ticks") < 1) errors.add("road.check-interval-ticks must be >= 1");
        positive(config, defaults, "follow.teleport-distance", errors);
        Object clockWorld = config.isSet("runtime.age.clock-world")
                ? config.get("runtime.age.clock-world")
                : defaults.get("runtime.age.clock-world");
        if (!(clockWorld instanceof String name) || name.isBlank()) {
            errors.add("runtime.age.clock-world must be a nonempty world name");
        }
        nonNegative(config, defaults, "runtime.age.base-growth-game-days", errors);
        nonNegative(config, defaults, "runtime.age.base-peak-duration-game-days", errors);
        nonNegative(config, defaults, "runtime.age.base-aging-duration-game-days", errors);
        for (StatType stat : StatType.values()) {
            nonNegative(config, defaults,
                    "runtime.age.sensitivity." + stat.configName(), errors);
        }
        if (integerSetting(config, defaults, "commands.info-max-results") < 1) errors.add("commands.info-max-results must be >= 1");
        for (String block : config.getStringList("road.blocks")) {
            if (Material.matchMaterial(block) == null) errors.add("Unknown road block: " + block);
        }
        return new ValidationResult(errors.isEmpty(), List.copyOf(errors));
    }

    /**
     * Persist newly introduced settings after successful validation.
     * Existing user overrides and unrelated keys are never modified.
     * On subsequent loads this is a no-op, avoiding unnecessary disk writes.
     */
    public int persistMissingDefaults() {
        try {
            int added = YamlKeyMerger.mergeAndSave(plugin.getConfig(), defaults,
                    new File(plugin.getDataFolder(), "config.yml"));
            if (added > 0) {
                plugin.getLogger().info("Added " + added + " missing configuration entries to config.yml");
            }
            return added;
        } catch (java.io.IOException ex) {
            plugin.getLogger().warning("Failed to update config.yml: " + ex.getMessage());
            // The in-memory defaults still allow the plugin to operate.
            return 0;
        }
    }

    public ValidationResult reloadFromDisk() {
        File file = new File(plugin.getDataFolder(), "config.yml");
        YamlConfiguration candidate = YamlConfiguration.loadConfiguration(file);
        candidate.setDefaults(defaults);
        ValidationResult validation = validate(candidate);
        if (!validation.valid()) return validation;
        plugin.reloadConfig();
        persistMissingDefaults();
        cachedRoadBlocks = null;
        return validation;
    }

    public ValidationResult set(String path, Object value) {
        if (!defaults.contains(path)) return ValidationResult.error("Unknown config path: " + path);
        FileConfiguration config = plugin.getConfig();
        Object old = config.isSet(path) ? config.get(path) : null;
        config.set(path, value);
        ValidationResult validation = validate(config);
        if (!validation.valid()) {
            config.set(path, old);
            return validation;
        }
        plugin.saveConfig();
        cachedRoadBlocks = null;
        return validation;
    }

    public ValidationResult reset(String path) {
        if (!defaults.contains(path)) return ValidationResult.error("Unknown config path: " + path);
        return set(path, defaults.get(path));
    }

    public ValidationResult resetCategory(String kind, String rootPath) {
        FileConfiguration config = plugin.getConfig();
        String root = rootPath == null || rootPath.isBlank() ? "" : rootPath;
        ConfigurationSection source = root.isEmpty() ? defaults : defaults.getConfigurationSection(root);
        if (source == null) return ValidationResult.error("Unknown config path: " + root);
        for (String key : source.getKeys(true)) {
            String full = root.isEmpty() ? key : root + "." + key;
            if (defaults.isConfigurationSection(full)) continue;
            Object value = defaults.get(full);
            boolean numeric = value instanceof Number;
            if ((kind.equals("numeric") && numeric) || (kind.equals("other") && !numeric)) {
                config.set(full, value);
            }
        }
        ValidationResult validation = validate(config);
        if (!validation.valid()) {
            plugin.reloadConfig();
            return validation;
        }
        plugin.saveConfig();
        cachedRoadBlocks = null;
        return validation;
    }

    public double statMin(StatType stat) { return config().getDouble("stats." + stat.configName() + ".min"); }
    public double statMax(StatType stat) { return config().getDouble("stats." + stat.configName() + ".max"); }
    public String statDisplay(StatType stat) { return config().getString("stats." + stat.configName() + ".display", "rank").toLowerCase(Locale.ROOT); }
    public String rangeChangePolicy() { return config().getString("stats.range-change-policy", "preserve-value"); }
    public double naturalChance() { return config().getDouble("natural-spawn.chance", 0.05); }
    public double naturalMaxNormalized() { return config().getDouble("natural-spawn.max-normalized", 1.5); }
    public double naturalMean() { return config().getDouble("natural-spawn.distribution.mean", 0.42); }
    public double naturalStdDev() { return config().getDouble("natural-spawn.distribution.standard-deviation", 0.22); }
    public double directInheritanceRate() { return config().getDouble("breeding.direct-inheritance-rate", 0.40); }
    public double breedingSpreadFactor() { return config().getDouble("breeding.gaussian.spread-factor", 0.25); }
    public double breedingMinStdDev() { return config().getDouble("breeding.gaussian.minimum-standard-deviation", 0.03); }
    public double natureAdjustment() { return config().getDouble("nature.adjustment", 0.10); }
    public double statMutationRate() { return config().getDouble("breeding.stat-mutation-rate", 0.01); }
    public double geneticMutationRate() { return config().getDouble("breeding.genetic-mutation-rate", 0.01); }
    public double hasteRadius() { return config().getDouble("traits.haste-radius", 7.5); }
    public double alertRadius() { return config().getDouble("traits.alert-radius", 20.0); }
    public int alertMinInterval() { return config().getInt("traits.alert-min-interval-ticks", 10); }
    public int alertMaxInterval() { return config().getInt("traits.alert-max-interval-ticks", 80); }
    public double eggGoldChance() { return config().getDouble("traits.egg-gold-chance", 0.05); }
    public double eggNetheriteChance() { return config().getDouble("traits.egg-netherite-conditional-chance", 0.01); }
    public double staminaConsumptionPerSecond() { return config().getDouble("flight.stamina-consumption-per-second", 1.0); }
    public double recoveryDelaySeconds() { return config().getDouble("flight.recovery-delay-seconds", 1.0); }
    public double healPerSeed() { return config().getDouble("feeding.heal-per-seed", 2.0); }
    public double roadMultiplier() { return config().getDouble("road.speed-multiplier", 1.25); }
    public int roadCheckIntervalTicks() { return config().getInt("road.check-interval-ticks", 5); }
    public double followTeleportDistance() { return config().getDouble("follow.teleport-distance", 16.0); }
    public boolean persistCurrentStamina() { return config().getBoolean("storage.persist-current-stamina", true); }
    public int infoMaxResults() { return config().getInt("commands.info-max-results", 10); }
    public String ageClockWorld() { return config().getString("runtime.age.clock-world", "world"); }
    public double ageGrowthDays() { return config().getDouble("runtime.age.base-growth-game-days", 672.0); }
    public double agePeakDays() { return config().getDouble("runtime.age.base-peak-duration-game-days", 2880.0); }
    public double ageAgingDays() { return config().getDouble("runtime.age.base-aging-duration-game-days", 4320.0); }
    public double ageSensitivity(StatType stat) {
        return config().getDouble("runtime.age.sensitivity." + stat.configName(),
                co.surumene.whatawonderfulchicken.runtime.AgeInjuryModifier.ageSensitivity(stat));
    }

    public Set<Material> roadBlocks() {
        if (cachedRoadBlocks != null) return cachedRoadBlocks;
        Set<Material> blocks = new LinkedHashSet<>();
        for (String raw : config().getStringList("road.blocks")) {
            Material material = Material.matchMaterial(raw);
            if (material != null) blocks.add(material);
        }
        cachedRoadBlocks = Set.copyOf(blocks);
        return cachedRoadBlocks;
    }

    public YamlConfiguration defaults() { return defaults; }

    /** Resolve omitted old-config keys from bundled defaults, never masking explicitly invalid values. */
    private static double numberSetting(ConfigurationSection config, ConfigurationSection defaults, String path) {
        Object raw = config.isSet(path) ? config.get(path) : defaults.get(path);
        return raw instanceof Number number ? number.doubleValue() : Double.NaN;
    }

    private static int integerSetting(ConfigurationSection config, ConfigurationSection defaults, String path) {
        double value = numberSetting(config, defaults, path);
        if (!Double.isFinite(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE
                || value != Math.rint(value)) return Integer.MIN_VALUE;
        return (int) value;
    }

    private static void validateProbability(ConfigurationSection config, ConfigurationSection defaults,
                                            String path, List<String> errors) {
        double value = numberSetting(config, defaults, path);
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0)
            errors.add(path + " must be between 0.0 and 1.0");
    }

    private static void positive(ConfigurationSection config, ConfigurationSection defaults,
                                 String path, List<String> errors) {
        double value = numberSetting(config, defaults, path);
        if (!Double.isFinite(value) || value <= 0.0) errors.add(path + " must be > 0");
    }

    private static void nonNegative(ConfigurationSection config, ConfigurationSection defaults,
                                    String path, List<String> errors) {
        double value = numberSetting(config, defaults, path);
        if (!Double.isFinite(value) || value < 0.0) errors.add(path + " must be >= 0");
    }

    public record ValidationResult(boolean valid, List<String> errors) {
        public static ValidationResult error(String error) { return new ValidationResult(false, List.of(error)); }
    }
}