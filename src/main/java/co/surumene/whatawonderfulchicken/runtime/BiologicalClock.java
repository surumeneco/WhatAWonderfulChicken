package co.surumene.whatawonderfulchicken.runtime;

import java.util.Objects;
import java.util.function.LongSupplier;

public final class BiologicalClock {
    private final BiologicalClockStateStore store;
    private String worldName;
    private LongSupplier rawFullTime;
    private long ignoredSkipOffset;
    private long lastKnownBiologicalTime;

    private BiologicalClock(
            String worldName,
            LongSupplier rawFullTime,
            BiologicalClockStateStore store,
            long ignoredSkipOffset,
            long lastKnownBiologicalTime) {
        this.worldName = requireWorldName(worldName);
        this.rawFullTime = Objects.requireNonNull(rawFullTime, "rawFullTime");
        this.store = Objects.requireNonNull(store, "store");
        this.ignoredSkipOffset = ignoredSkipOffset;
        this.lastKnownBiologicalTime = Math.max(0L, lastKnownBiologicalTime);
    }

    public static BiologicalClock start(
            String configuredWorld,
            LongSupplier rawFullTime,
            BiologicalClockStateStore store) {
        String world = requireWorldName(configuredWorld);
        Objects.requireNonNull(rawFullTime, "rawFullTime");
        Objects.requireNonNull(store, "store");

        long raw = rawFullTime.getAsLong();
        BiologicalClockState persisted = store.load();
        if (persisted == null) {
            long biological = Math.max(0L, raw);
            return new BiologicalClock(
                    world, rawFullTime, store, 0L, biological);
        }

        if (persisted.clockWorld().equals(world)) {
            long biological = Math.max(
                    0L,
                    raw - persisted.ignoredSkipOffset());
            return new BiologicalClock(
                    world,
                    rawFullTime,
                    store,
                    persisted.ignoredSkipOffset(),
                    biological);
        }

        long biological = persisted.lastBiologicalTime();
        return new BiologicalClock(
                world,
                rawFullTime,
                store,
                raw - biological,
                biological);
    }

    public synchronized long currentTime() {
        lastKnownBiologicalTime =
                Math.max(0L, rawFullTime.getAsLong() - ignoredSkipOffset);
        return lastKnownBiologicalTime;
    }

    public synchronized double currentGameDay() {
        return currentTime() / 24_000.0;
    }

    public synchronized void recordIgnoredSkip(long skipAmount) {
        long before = currentTime();
        long nextOffset = Math.addExact(ignoredSkipOffset, skipAmount);
        BiologicalClockState next = new BiologicalClockState(
                worldName,
                nextOffset,
                before);
        store.save(next);
        ignoredSkipOffset = nextOffset;
        lastKnownBiologicalTime = before;
    }

    public synchronized void reconfigure(
            String newWorldName,
            LongSupplier newRawFullTime) {
        String world = requireWorldName(newWorldName);
        Objects.requireNonNull(newRawFullTime, "newRawFullTime");

        long biological = currentTime();
        long newRaw = newRawFullTime.getAsLong();

        long nextOffset = newRaw - biological;
        BiologicalClockState next = new BiologicalClockState(
                world,
                nextOffset,
                biological);
        store.save(next);

        worldName = world;
        rawFullTime = newRawFullTime;
        ignoredSkipOffset = nextOffset;
        lastKnownBiologicalTime = biological;
    }

    public synchronized void persist() {
        currentTime();
        saveState();
    }

    public synchronized String worldName() {
        return worldName;
    }

    public synchronized long ignoredSkipOffset() {
        return ignoredSkipOffset;
    }

    private void saveState() {
        store.save(new BiologicalClockState(
                worldName,
                ignoredSkipOffset,
                lastKnownBiologicalTime));
    }

    private static String requireWorldName(String worldName) {
        Objects.requireNonNull(worldName, "worldName");
        if (worldName.isBlank()) {
            throw new IllegalArgumentException("worldName must not be blank");
        }
        return worldName;
    }
}
