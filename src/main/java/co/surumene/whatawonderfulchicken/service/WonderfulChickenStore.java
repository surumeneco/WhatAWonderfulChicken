package co.surumene.whatawonderfulchicken.service;

import co.surumene.whatawonderfulchicken.WhatAWonderfulChickenPlugin;
import co.surumene.whatawonderfulchicken.config.ConfigService;
import co.surumene.whatawonderfulchicken.data.BehaviorMode;
import co.surumene.whatawonderfulchicken.data.PedigreeData;
import co.surumene.whatawonderfulchicken.data.StatType;
import co.surumene.whatawonderfulchicken.data.WonderfulChickenData;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Chicken;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public final class WonderfulChickenStore {
    public static final String MARKER_VALUE = "wonderful_chicken";
    private static final int DATA_VERSION = 1;

    private final WhatAWonderfulChickenPlugin plugin;
    private final ConfigService config;
    private final NamespacedKey markerKey;
    private final NamespacedKey dataVersionKey;
    private final NamespacedKey currentStaminaKey;
    private final NamespacedKey carpetKey;
    private final NamespacedKey shulkerKey;
    private final NamespacedKey headKey;
    private final NamespacedKey behaviorKey;
    private final NamespacedKey followTargetKey;
    private final NamespacedKey bloodlineKey;
    private final NamespacedKey generationKey;
    private final NamespacedKey pedigreeKey;
    private final Map<StatType, NamespacedKey> valueKeys = new EnumMap<>(StatType.class);
    private final Map<StatType, NamespacedKey> normalizedKeys = new EnumMap<>(StatType.class);

    public WonderfulChickenStore(WhatAWonderfulChickenPlugin plugin, ConfigService config) {
        this.plugin = plugin;
        this.config = config;
        this.markerKey = key("type");
        this.dataVersionKey = key("data_version");
        this.currentStaminaKey = key("current_stamina");
        this.carpetKey = key("equipment_carpet");
        this.shulkerKey = key("equipment_shulker_box");
        this.headKey = key("equipment_head");
        this.behaviorKey = key("behavior_mode");
        this.followTargetKey = key("follow_target");
        this.bloodlineKey = key("bloodline_id");
        this.generationKey = key("generation");
        this.pedigreeKey = key("pedigree");
        for (StatType stat : StatType.values()) {
            valueKeys.put(stat, key("stat_" + stat.key()));
            normalizedKeys.put(stat, key("normalized_" + stat.key()));
        }
    }

    public boolean isWonderful(Chicken chicken) {
        return MARKER_VALUE.equals(chicken.getPersistentDataContainer().get(markerKey, PersistentDataType.STRING));
    }

    public WonderfulChickenData load(Chicken chicken) {
        if (!isWonderful(chicken)) throw new IllegalArgumentException("Chicken is not wonderful");
        PersistentDataContainer pdc = chicken.getPersistentDataContainer();
        WonderfulChickenData data = new WonderfulChickenData();
        for (StatType stat : StatType.values()) {
            Double value = pdc.get(valueKeys.get(stat), PersistentDataType.DOUBLE);
            Double normalized = pdc.get(normalizedKeys.get(stat), PersistentDataType.DOUBLE);
            if (value == null && normalized != null) value = toValue(stat, normalized);
            if (normalized == null && value != null) normalized = toNormalized(stat, value);
            data.value(stat, value == null ? config.statMin(stat) : value);
            data.normalized(stat, normalized == null ? 0.0 : normalized);
        }
        double maxStamina = data.value(StatType.STAMINA);
        Double storedStamina = pdc.get(currentStaminaKey, PersistentDataType.DOUBLE);
        data.currentStamina(storedStamina != null
                ? Math.max(0.0, Math.min(maxStamina, storedStamina))
                : maxStamina);
        data.carpet(readItem(pdc, carpetKey));
        data.shulkerBox(readItem(pdc, shulkerKey));
        data.headItem(readItem(pdc, headKey));
        String behavior = pdc.get(behaviorKey, PersistentDataType.STRING);
        if (behavior != null) {
            try { data.behaviorMode(BehaviorMode.valueOf(behavior)); } catch (IllegalArgumentException ignored) {}
        }
        String follow = pdc.get(followTargetKey, PersistentDataType.STRING);
        if (follow != null) {
            try { data.followTarget(UUID.fromString(follow)); } catch (IllegalArgumentException ignored) {}
        }
        String bloodline = pdc.get(bloodlineKey, PersistentDataType.STRING);
        data.bloodlineId(bloodline == null || bloodline.isBlank() ? UUID.randomUUID().toString() : bloodline);
        Integer generation = pdc.get(generationKey, PersistentDataType.INTEGER);
        data.generation(generation == null ? 0 : generation);
        data.pedigree(PedigreeData.deserialize(pdc.get(pedigreeKey, PersistentDataType.BYTE_ARRAY)));
        return data;
    }

    public void save(Chicken chicken, WonderfulChickenData data) {
        PersistentDataContainer pdc = chicken.getPersistentDataContainer();
        pdc.set(markerKey, PersistentDataType.STRING, MARKER_VALUE);
        pdc.set(dataVersionKey, PersistentDataType.INTEGER, DATA_VERSION);
        for (StatType stat : StatType.values()) {
            pdc.set(valueKeys.get(stat), PersistentDataType.DOUBLE, data.value(stat));
            pdc.set(normalizedKeys.get(stat), PersistentDataType.DOUBLE, data.normalized(stat));
        }
        pdc.set(currentStaminaKey, PersistentDataType.DOUBLE, data.currentStamina());
        writeItem(pdc, carpetKey, data.carpet());
        writeItem(pdc, shulkerKey, data.shulkerBox());
        writeItem(pdc, headKey, data.headItem());
        pdc.set(behaviorKey, PersistentDataType.STRING, data.behaviorMode().name());
        if (data.followTarget() == null) pdc.remove(followTargetKey);
        else pdc.set(followTargetKey, PersistentDataType.STRING, data.followTarget().toString());
        if (data.bloodlineId() == null || data.bloodlineId().isBlank()) data.bloodlineId(UUID.randomUUID().toString());
        pdc.set(bloodlineKey, PersistentDataType.STRING, data.bloodlineId());
        pdc.set(generationKey, PersistentDataType.INTEGER, data.generation());
        pdc.set(pedigreeKey, PersistentDataType.BYTE_ARRAY, data.pedigree().serialize());
    }

    public void setCurrentStamina(Chicken chicken, double stamina) {
        WonderfulChickenData data = load(chicken);
        data.currentStamina(Math.max(0.0, Math.min(data.value(StatType.STAMINA), stamina)));
        save(chicken, data);
    }

    public double toValue(StatType stat, double normalized) {
        double min = config.statMin(stat);
        double value = min + normalized * (config.statMax(stat) - min);
        return stat.canonicalizeValue(value);
    }

    public double toNormalized(StatType stat, double value) {
        double min = config.statMin(stat);
        double max = config.statMax(stat);
        return (value - min) / (max - min);
    }

    private ItemStack readItem(PersistentDataContainer pdc, NamespacedKey key) {
        byte[] bytes = pdc.get(key, PersistentDataType.BYTE_ARRAY);
        if (bytes == null || bytes.length == 0) return null;
        try { return ItemStack.deserializeBytes(bytes); } catch (RuntimeException ex) {
            plugin.getLogger().warning("Failed to deserialize equipment item from PDC key " + key + ": " + ex.getMessage());
            return null;
        }
    }

    private void writeItem(PersistentDataContainer pdc, NamespacedKey key, ItemStack item) {
        if (item == null || item.isEmpty()) pdc.remove(key);
        else pdc.set(key, PersistentDataType.BYTE_ARRAY, item.serializeAsBytes());
    }

    private NamespacedKey key(String name) { return new NamespacedKey(plugin, name); }
}