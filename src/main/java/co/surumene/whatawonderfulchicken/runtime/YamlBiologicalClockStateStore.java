package co.surumene.whatawonderfulchicken.runtime;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.Logger;

public final class YamlBiologicalClockStateStore
        implements BiologicalClockStateStore {
    private static final String WORLD = "clock-world";
    private static final String OFFSET = "ignored-skip-offset";
    private static final String LAST_TIME = "last-biological-time";

    private final File file;
    private final Logger logger;

    public YamlBiologicalClockStateStore(File file, Logger logger) {
        this.file = Objects.requireNonNull(file, "file");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public BiologicalClockState load() {
        if (!file.isFile()) {
            return null;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String world = yaml.getString(WORLD);
        if (world == null || world.isBlank()) {
            logger.warning("Ignoring invalid biological clock state: missing clock-world");
            return null;
        }

        Object rawOffset = yaml.get(OFFSET);
        Object rawLast = yaml.get(LAST_TIME);
        if (!(rawOffset instanceof Number offset)
                || !(rawLast instanceof Number last)) {
            logger.warning("Ignoring invalid biological clock state: numeric values are missing");
            return null;
        }

        long offsetValue = offset.longValue();
        long lastValue = last.longValue();
        if (lastValue < 0L) {
            logger.warning("Ignoring invalid biological clock state: last time is negative");
            return null;
        }

        return new BiologicalClockState(world, offsetValue, lastValue);
    }

    @Override
    public void save(BiologicalClockState state) {
        Objects.requireNonNull(state, "state");
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException(
                    "failed to create biological clock state directory: " + parent);
        }

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set(WORLD, state.clockWorld());
        yaml.set(OFFSET, state.ignoredSkipOffset());
        yaml.set(LAST_TIME, state.lastBiologicalTime());
        try {
            yaml.save(file);
        } catch (IOException error) {
            throw new IllegalStateException(
                    "failed to persist biological clock state", error);
        }
    }
}
