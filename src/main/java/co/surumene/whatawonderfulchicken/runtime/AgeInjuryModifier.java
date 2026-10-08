package co.surumene.whatawonderfulchicken.runtime;

import co.surumene.whatawonderfulchicken.data.InjuryPhenotype;
import co.surumene.whatawonderfulchicken.data.StatType;
import java.util.List;
import java.util.Objects;

/** Pure normalized-stat adjustment; personality and canonical scaling are later stages. */
public final class AgeInjuryModifier {
    private static final double NORMAL_RANK_COUNT = 9.0;

    private AgeInjuryModifier() {}

    public static AdjustedStat adjust(
            StatType stat,
            double baseNormalized,
            double adultAgeDays,
            AgeCurve curve,
            List<InjuryPhenotype> injuries) {
        return adjust(stat, baseNormalized, adultAgeDays, curve, injuries,
                ageSensitivity(stat));
    }

    public static AdjustedStat adjust(
            StatType stat,
            double baseNormalized,
            double adultAgeDays,
            AgeCurve curve,
            List<InjuryPhenotype> injuries,
            double sensitivity) {
        Objects.requireNonNull(stat, "stat");
        Objects.requireNonNull(curve, "curve");
        Objects.requireNonNull(injuries, "injuries");
        if (!Double.isFinite(baseNormalized) || baseNormalized < 0.0
                || !Double.isFinite(adultAgeDays) || adultAgeDays < 0.0) {
            throw new IllegalArgumentException(
                    "base normalized and adult age must be finite and nonnegative");
        }
        if (!Double.isFinite(sensitivity) || sensitivity < 0.0) {
            throw new IllegalArgumentException("age sensitivity must be finite and nonnegative");
        }
        double ageReduction = curve.rankReductionAt(adultAgeDays)
                * sensitivity / NORMAL_RANK_COUNT;
        InjuryPhenotype matching = injuries.stream()
                .filter(injury -> injury.stat() == stat)
                .findFirst().orElse(null);
        boolean active = matching != null
                && adultAgeDays >= matching.onsetGameDay();
        double injuryReduction = active
                ? matching.severityRank() / NORMAL_RANK_COUNT : 0.0;
        return new AdjustedStat(baseNormalized,
                Math.max(0.0, baseNormalized - ageReduction - injuryReduction),
                active);
    }

    public static double ageSensitivity(StatType stat) {
        return switch (Objects.requireNonNull(stat, "stat")) {
            case GROUND_SPEED, ASCENT_SPEED, JUMP_STRENGTH -> 1.0;
            case AIR_SPEED -> 0.8;
            case STAMINA -> 0.7;
            case MAX_HEALTH -> 0.7;
            case STAMINA_RECOVERY -> 0.5;
            case STEP_HEIGHT -> 0.4;
            case SIZE -> 0.0;
        };
    }

    public record AdjustedStat(
            double baseNormalized,
            double effectiveNormalized,
            boolean injuryActive) {
        public AdjustedStat {
            if (!Double.isFinite(baseNormalized) || baseNormalized < 0.0
                    || !Double.isFinite(effectiveNormalized)
                    || effectiveNormalized < 0.0) {
                throw new IllegalArgumentException("normalized stats must be finite and nonnegative");
            }
        }
    }
}
