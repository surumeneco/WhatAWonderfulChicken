package co.surumene.whatawonderfulchicken.data;

import java.util.Objects;

public record InjuryPhenotype(StatType stat, double onsetGameDay, double severityRank) {
    public InjuryPhenotype {
        Objects.requireNonNull(stat, "stat");
        if (!Double.isFinite(onsetGameDay) || onsetGameDay < 0.0) {
            throw new IllegalArgumentException("onsetGameDay must be finite and >= 0");
        }
        if (!Double.isFinite(severityRank) || severityRank < 0.0) {
            throw new IllegalArgumentException("severityRank must be finite and >= 0");
        }
    }
}
