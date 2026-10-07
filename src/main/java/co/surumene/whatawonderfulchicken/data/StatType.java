package co.surumene.whatawonderfulchicken.data;

import java.util.Arrays;
import java.util.Optional;

public enum StatType {
    MAX_HEALTH(0x00, "max_health", "max-health", "max-health"),
    SIZE(0x01, "size", "size", "size"),
    GROUND_SPEED(0x02, "ground_speed", "ground-speed", "ground-speed"),
    AIR_SPEED(0x03, "air_speed", "air-speed", "air-speed"),
    ASCENT_SPEED(0x04, "ascent_speed", "ascent-speed", "ascent-speed"),
    JUMP_STRENGTH(0x05, "jump_strength", "jump-strength", "jump-strength"),
    STEP_HEIGHT(0x06, "step_height", "step-height", "step-height"),
    STAMINA(0x07, "stamina", "stamina", "stamina"),
    STAMINA_RECOVERY(0x08, "stamina_recovery", "stamina-recovery", "stamina-recovery");

    private final int targetId;
    private final String key;
    private final String commandName;
    private final String configName;

    StatType(int targetId, String key, String commandName, String configName) {
        this.targetId = targetId;
        this.key = key;
        this.commandName = commandName;
        this.configName = configName;
    }

    public int targetId() { return targetId; }
    public String key() { return key; }
    public String commandName() { return commandName; }
    public String configName() { return configName; }

    public double canonicalizeValue(double value) {
        return this == MAX_HEALTH ? Math.round(value) : value;
    }

    public static Optional<StatType> fromCommandName(String name) {
        return Arrays.stream(values()).filter(stat -> stat.commandName.equalsIgnoreCase(name)).findFirst();
    }

    public static Optional<StatType> fromDataKey(String name) {
        return Arrays.stream(values()).filter(stat -> stat.key.equalsIgnoreCase(name)).findFirst();
    }
}
