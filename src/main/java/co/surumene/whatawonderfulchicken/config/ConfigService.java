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
        List<String> errors = new ArrayList<>();
        for (StatType stat : StatType.values()) {
            double min = config.getDouble("stats." + stat.configName() + ".min", Double.NaN);
            double max = config.getDouble("stats." + stat.configName() + ".max", Double.NaN);
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
        validateProbability(config, "natural-spawn.chance", errors);
        validateProbability(config, "breeding.direct-inheritance-rate", errors);
        double maxNormalized = config.getDouble("natural-spawn.max-normalized", -1.0);
        if (maxNormalized < 1.0 || !Double.isFinite(maxNormalized)) errors.add("natural-spawn.max-normalized must be >= 1.0");
        double naturalMean = config.getDouble("natural-spawn.distribution.mean", Double.NaN);
        if (!Double.isFinite(naturalMean) || naturalMean < 0.0 || naturalMean > maxNormalized) {
            errors.add("natural-spawn.distribution.mean must be finite and between 0.0 and natural-spawn.max-normalized");
        }
        positive(config, "natural-spawn.distribution.standard-deviation", errors);
        positive(config, "breeding.gaussian.spread-factor", errors);
        positive(config, "breeding.gaussian.minimum-standard-deviation", errors);
        positive(config, "flight.stamina-consumption-per-second", errors);
        nonNegative(config, "flight.recovery-delay-seconds", errors);
        nonNegative(config, "feeding.heal-per-seed", errors);
        positive(config, "road.speed-multiplier", errors);
        if (config.getInt("road.check-interval-ticks", 0) < 1) errors.add("road.check-interval-ticks must be >= 1");
        positive(config, "follow.teleport-distance", errors);
        if (config.getInt("commands.info-max-results", 0) < 1) errors.add("commands.info-max-results must be >= 1");
        for (String block : config.getStringList("road.blocks")) {
            if (Material.matchMaterial(block) == null) errors.add("Unknown road block: " + block);
        }
        return new ValidationResult(errors.isEmpty(), List.copyOf(errors));
    }

    public ValidationResult reloadFromDisk() {
        File file = new File(plugin.getDataFolder(), "config.yml");
        YamlConfiguration candidate = YamlConfiguration.loadConfiguration(file);
        candidate.setDefaults(defaults);
        ValidationResult validation = validate(candidate);
        if (!validation.valid()) return validation;
        plugin.reloadConfig();
        return validation;
    }

    public ValidationResult set(String path, Object value) {
        if (!defaults.contains(path)) return ValidationResult.error("Unknown config path: " + path);
        FileConfiguration config = plugin.getConfig();
        Object old = config.get(path);
        config.set(path, value);
        ValidationResult validation = validate(config);
        if (!validation.valid()) {
            config.set(path, old);
            return validation;
        }
        plugin.saveConfig();
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
        return validation;
    }

    public double statMin(StatType stat) { return config().getDouble("stats." + stat.configName() + ".min"); }
    public double statMax(StatType stat) { return config().getDouble("stats." + stat.configName() + ".max"); }
    public String statDisplay(StatType stat) { return config().getString("stats." + stat.configName() + ".display", "both").toLowerCase(Locale.ROOT); }
    public String rangeChangePolicy() { return config().getString("stats.range-change-policy", "preserve-value"); }
    public double naturalChance() { return config().getDouble("natural-spawn.chance", 0.05); }
    public double naturalMaxNormalized() { return config().getDouble("natural-spawn.max-normalized", 1.5); }
    public double naturalMean() { return config().getDouble("natural-spawn.distribution.mean", 0.42); }
    public double naturalStdDev() { return config().getDouble("natural-spawn.distribution.standard-deviation", 0.22); }
    public double directInheritanceRate() { return config().getDouble("breeding.direct-inheritance-rate", 0.40); }
    public double breedingSpreadFactor() { return config().getDouble("breeding.gaussian.spread-factor", 0.25); }
    public double breedingMinStdDev() { return config().getDouble("breeding.gaussian.minimum-standard-deviation", 0.03); }
    public double staminaConsumptionPerSecond() { return config().getDouble("flight.stamina-consumption-per-second", 1.0); }
    public double recoveryDelaySeconds() { return config().getDouble("flight.recovery-delay-seconds", 1.0); }
    public double healPerSeed() { return config().getDouble("feeding.heal-per-seed", 2.0); }
    public double roadMultiplier() { return config().getDouble("road.speed-multiplier", 1.25); }
    public int roadCheckIntervalTicks() { return config().getInt("road.check-interval-ticks", 5); }
    public double followTeleportDistance() { return config().getDouble("follow.teleport-distance", 16.0); }
    public boolean persistCurrentStamina() { return config().getBoolean("storage.persist-current-stamina", true); }
    public int infoMaxResults() { return config().getInt("commands.info-max-results", 10); }

    public Set<Material> roadBlocks() {
        Set<Material> blocks = new LinkedHashSet<>();
        for (String raw : config().getStringList("road.blocks")) {
            Material material = Material.matchMaterial(raw);
            if (material != null) blocks.add(material);
        }
        return blocks;
    }

    public YamlConfiguration defaults() { return defaults; }

    private static void validateProbability(ConfigurationSection config, String path, List<String> errors) {
        double value = config.getDouble(path, -1.0);
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) errors.add(path + " must be between 0.0 and 1.0");
    }

    private static void positive(ConfigurationSection config, String path, List<String> errors) {
        double value = config.getDouble(path, Double.NaN);
        if (!Double.isFinite(value) || value <= 0.0) errors.add(path + " must be > 0");
    }

    private static void nonNegative(ConfigurationSection config, String path, List<String> errors) {
        double value = config.getDouble(path, Double.NaN);
        if (!Double.isFinite(value) || value < 0.0) errors.add(path + " must be >= 0");
    }

    public record ValidationResult(boolean valid, List<String> errors) {
        public static ValidationResult error(String error) { return new ValidationResult(false, List.of(error)); }
    }
}