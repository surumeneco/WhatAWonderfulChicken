package co.surumene.whatawonderfulchicken.runtime;

import java.util.Objects;

public record BiologicalClockState(
        String clockWorld,
        long ignoredSkipOffset,
        long lastBiologicalTime) {

    public BiologicalClockState {
        Objects.requireNonNull(clockWorld, "clockWorld");
        if (clockWorld.isBlank()) {
            throw new IllegalArgumentException("clockWorld must not be blank");
        }
        if (lastBiologicalTime < 0L) {
            throw new IllegalArgumentException("lastBiologicalTime must be >= 0");
        }
    }
}
