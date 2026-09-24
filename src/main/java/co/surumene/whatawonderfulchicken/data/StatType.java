package co.surumene.whatawonderfulchicken.data;

import java.util.Arrays;
import java.util.Optional;

public enum StatType {
    MAX_HEALTH("max_health", "max-health", "max-health"),
    SIZE("size", "size", "size"),
    GROUND_SPEED("ground_speed", "ground-speed", "ground-speed"),
    AIR_SPEED("air_speed", "air-speed", "air-speed"),
    ASCENT_SPEED("ascent_speed", "ascent-speed", "ascent-speed"),
    JUMP_STRENGTH("jump_strength", "jump-strength", "jump-strength"),
    STEP_HEIGHT("step_height", "step-height", "step-height"),
    STAMINA("stamina", "stamina", "stamina"),
    STAMINA_RECOVERY("stamina_recovery", "stamina-recovery", "stamina-recovery");

    private final String key;
    private final String commandName;
    private final String configName;

    StatType(String key, String commandName, String configName) {
        this.key = key;
        this.commandName = commandName;
        this.configName = configName;
    }

    public String key() { return key; }
    public String commandName() { return commandName; }
    public String configName() { return configName; }

    public static Optional<StatType> fromCommandName(String name) {
        return Arrays.stream(values()).filter(stat -> stat.commandName.equalsIgnoreCase(name)).findFirst();
    }

    public static Optional<StatType> fromDataKey(String name) {
        return Arrays.stream(values()).filter(stat -> stat.key.equalsIgnoreCase(name)).findFirst();
    }
}