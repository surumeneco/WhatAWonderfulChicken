package co.surumene.whatawonderfulchicken.genome;

/**
 * Immutable Profile-owned settings. Phase 9 will source these values from typed config;
 * keeping the Profile constructor independent from Bukkit configuration preserves WGL testability.
 */
public record WonderfulChickenGenomeSettings(
        double seriousMaxScore,
        double seriousSpread,
        double dominantGap,
        double traitExpressionThreshold,
        double traitStrongGap,
        double injuryExpressionThreshold,
        double injuryOnsetMaxGameDays,
        double injurySeverityRankMin,
        double injurySeverityRankMax,
        double divineMinHaplotypeScore,
        double divineTotalScore,
        double chromosomeLengthStandardDeviationRatio,
        double chromosomeLengthMinRatio,
        double chromosomeLengthMaxRatio) {

    public WonderfulChickenGenomeSettings {
        requireUnit(seriousMaxScore, "seriousMaxScore");
        requireUnit(seriousSpread, "seriousSpread");
        requireUnit(dominantGap, "dominantGap");
        requireUnit(traitExpressionThreshold, "traitExpressionThreshold");
        requireUnit(traitStrongGap, "traitStrongGap");
        requireUnit(injuryExpressionThreshold, "injuryExpressionThreshold");
        if (!Double.isFinite(injuryOnsetMaxGameDays) || injuryOnsetMaxGameDays <= 0.0) {
            throw new IllegalArgumentException("injuryOnsetMaxGameDays must be finite and > 0");
        }
        if (!Double.isFinite(injurySeverityRankMin)
                || !Double.isFinite(injurySeverityRankMax)
                || injurySeverityRankMin < 0.0
                || injurySeverityRankMax < injurySeverityRankMin) {
            throw new IllegalArgumentException("invalid injury severity range");
        }
        requireUnit(divineMinHaplotypeScore, "divineMinHaplotypeScore");
        if (!Double.isFinite(divineTotalScore) || divineTotalScore < 0.0 || divineTotalScore > 2.0) {
            throw new IllegalArgumentException("divineTotalScore must be finite and in [0,2]");
        }
        if (!Double.isFinite(chromosomeLengthStandardDeviationRatio)
                || chromosomeLengthStandardDeviationRatio <= 0.0
                || !Double.isFinite(chromosomeLengthMinRatio)
                || !Double.isFinite(chromosomeLengthMaxRatio)
                || chromosomeLengthMinRatio <= 0.0
                || chromosomeLengthMinRatio > 1.0
                || chromosomeLengthMaxRatio < 1.0
                || chromosomeLengthMaxRatio < chromosomeLengthMinRatio) {
            throw new IllegalArgumentException("invalid chromosome length tolerance");
        }
    }

    public static WonderfulChickenGenomeSettings defaults() {
        return new WonderfulChickenGenomeSettings(
                0.500,
                0.115,
                0.085,
                0.50,
                0.125,
                0.45,
                7872.0,
                3.0,
                6.0,
                0.18,
                0.65,
                0.05,
                0.90,
                1.10);
    }

    private static void requireUnit(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0,1]");
        }
    }
}
