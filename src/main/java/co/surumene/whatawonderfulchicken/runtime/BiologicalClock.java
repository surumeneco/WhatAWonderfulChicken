package co.surumene.whatawonderfulchicken.runtime;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Existing consumer contract and YAML schema stay unchanged.
 * The state transitions and skip/reconfiguration semantics live in WCL.
 */
public final class BiologicalClock {
    private final co.surumene.wcl.api.time.BiologicalClock delegate;

    private BiologicalClock(co.surumene.wcl.api.time.BiologicalClock delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    public static BiologicalClock start(
            String configuredWorld, LongSupplier rawFullTime, BiologicalClockStateStore store) {
        Objects.requireNonNull(store, "store");
        var adapter = new co.surumene.wcl.api.time.BiologicalClockStateStore() {
            @Override public co.surumene.wcl.api.time.BiologicalClockState load() {
                BiologicalClockState previous = store.load();
                return previous == null ? null : new co.surumene.wcl.api.time.BiologicalClockState(
                        previous.clockWorld(), previous.ignoredSkipOffset(), previous.lastBiologicalTime());
            }

            @Override public void save(co.surumene.wcl.api.time.BiologicalClockState state) {
                store.save(new BiologicalClockState(
                        state.clockWorld(), state.ignoredSkipOffset(), state.lastBiologicalTime()));
            }
        };
        return new BiologicalClock(co.surumene.wcl.api.time.BiologicalClock.start(
                configuredWorld, rawFullTime, adapter));
    }

    public long currentTime() { return delegate.currentTime(); }
    public double currentGameDay() { return delegate.currentGameDay(); }
    public void recordIgnoredSkip(long skipAmount) { delegate.recordIgnoredSkip(skipAmount); }
    public void reconfigure(String worldName, LongSupplier fullTime) {
        delegate.reconfigure(worldName, fullTime);
    }
    public void persist() { delegate.persist(); }
    public String worldName() { return delegate.worldName(); }
    public long ignoredSkipOffset() { return delegate.ignoredSkipOffset(); }
}
