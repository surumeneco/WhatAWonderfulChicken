package co.surumene.whatawonderfulchicken.runtime;

import co.surumene.whatawonderfulchicken.data.DevelopmentFactor;
import co.surumene.whatawonderfulchicken.data.PhenotypeSnapshot;
import java.util.Map;
import java.util.Objects;

/** Continuous adulthood curve in normalized-rank units, matching WWW. */
public record AgeCurve(
        double growthEndGameDay,
        double juvenileSuppressionRank,
        double agingStartGameDay,
        double elderGameDay,
        double maximumAgingReductionRank) {

    public static final double DEFAULT_GROWTH_DAYS = 672.0;
    public static final double DEFAULT_PEAK_DAYS = 2_880.0;
    public static final double DEFAULT_AGING_DAYS = 4_320.0;

    public AgeCurve {
        if (!valid(growthEndGameDay) || !valid(juvenileSuppressionRank)
                || !valid(agingStartGameDay) || !valid(elderGameDay)
                || !valid(maximumAgingReductionRank)
                || agingStartGameDay < growthEndGameDay
                || elderGameDay < agingStartGameDay) {
            throw new IllegalArgumentException("invalid age curve");
        }
    }

    public static AgeCurve from(PhenotypeSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return from(snapshot.developmentFactors(),
                DEFAULT_GROWTH_DAYS, DEFAULT_PEAK_DAYS, DEFAULT_AGING_DAYS);
    }

    /** Configurable inputs make future runtime configuration independent of genome storage. */
    public static AgeCurve from(
            Map<DevelopmentFactor, Double> factors,
            double baseGrowthGameDays,
            double basePeakDurationGameDays,
            double baseAgingDurationGameDays) {
        Objects.requireNonNull(factors, "factors");
        if (!valid(baseGrowthGameDays) || !valid(basePeakDurationGameDays)
                || !valid(baseAgingDurationGameDays)) {
            throw new IllegalArgumentException("age durations must be finite and nonnegative");
        }
        for (DevelopmentFactor factor : DevelopmentFactor.values()) {
            Double score = factors.get(factor);
            if (score == null || !Double.isFinite(score)
                    || score < 0.0 || score > 1.0) {
                throw new IllegalArgumentException("invalid development factor: " + factor);
            }
        }
        double growth = baseGrowthGameDays
                * (1.25 - 0.50 * factors.get(DevelopmentFactor.MATURITY));
        double juvenile = 0.25
                + 0.50 * factors.get(DevelopmentFactor.JUVENILE_SUPPRESSION);
        double agingStart = growth + basePeakDurationGameDays
                * (0.80 + 0.40 * factors.get(DevelopmentFactor.AGING_START));
        double agingDuration = baseAgingDurationGameDays
                * (1.25 - 0.50 * factors.get(DevelopmentFactor.AGING_SPEED));
        double elder = agingStart + agingDuration;
        double reduction = 2.00 - factors.get(DevelopmentFactor.AGING_RESISTANCE);
        return new AgeCurve(growth, juvenile, agingStart, elder, reduction);
    }

    public double rankReductionAt(double ageGameDays) {
        if (!Double.isFinite(ageGameDays)) {
            throw new IllegalArgumentException("ageGameDays must be finite");
        }
        double age = Math.max(0.0, ageGameDays);
        if (age < growthEndGameDay) {
            return growthEndGameDay == 0.0 ? 0.0
                    : juvenileSuppressionRank
                    * (1.0 - smoothstep(age / growthEndGameDay));
        }
        if (age < agingStartGameDay) return 0.0;
        if (age < elderGameDay) {
            double duration = elderGameDay - agingStartGameDay;
            return duration == 0.0 ? maximumAgingReductionRank
                    : maximumAgingReductionRank
                    * smoothstep((age - agingStartGameDay) / duration);
        }
        return maximumAgingReductionRank;
    }

    public static double smoothstep(double value) {
        double x = Math.max(0.0, Math.min(1.0, value));
        return 3.0 * x * x - 2.0 * x * x * x;
    }

    private static boolean valid(double value) {
        return Double.isFinite(value) && value >= 0.0;
    }
}
