package co.surumene.whatawonderfulchicken.runtime;

import java.io.File;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Backward-compatible owner-side adapter: WCL owns the legacy YAML algorithm,
 * while WWC/WWW retain their existing state contract and file location.
 */
public final class YamlBiologicalClockStateStore implements BiologicalClockStateStore {
    private final co.surumene.wcl.plugin.time.YamlBiologicalClockStateStore delegate;

    public YamlBiologicalClockStateStore(File file, Logger logger) {
        delegate = new co.surumene.wcl.plugin.time.YamlBiologicalClockStateStore(
                Objects.requireNonNull(file, "file"), Objects.requireNonNull(logger, "logger"));
    }

    @Override public BiologicalClockState load() {
        var state = delegate.load();
        return state == null ? null : new BiologicalClockState(
                state.clockWorld(), state.ignoredSkipOffset(), state.lastBiologicalTime());
    }

    @Override public void save(BiologicalClockState state) {
        Objects.requireNonNull(state, "state");
        delegate.save(new co.surumene.wcl.api.time.BiologicalClockState(
                state.clockWorld(), state.ignoredSkipOffset(), state.lastBiologicalTime()));
    }
}
