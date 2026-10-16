package co.surumene.whatawonderfulchicken.task;

import co.surumene.whatawonderfulchicken.runtime.AgeCurve;
import java.util.List;

/** Earliest biological-time boundary that may change an effective stat. */
final class ChickenAgeProjectionDeadline {
    private static final double TICKS_PER_DAY = 24_000.0;
    private static final long QUANTUM = 200L;

    private ChickenAgeProjectionDeadline() {}

    static long next(long now, long adultStart, AgeCurve curve, List<Double> onsets) {
        double days = Math.max(0, now - adultStart) / TICKS_PER_DAY;
        long due;
        if (days < curve.growthEndGameDay() ||
                (days >= curve.agingStartGameDay() && days < curve.elderGameDay())) {
            due = (now / QUANTUM + 1) * QUANTUM;
        } else if (days < curve.agingStartGameDay()) {
            due = boundary(adultStart, curve.agingStartGameDay());
        } else {
            due = Long.MAX_VALUE;
        }
        for (Double onset : onsets) {
            if (onset != null && Double.isFinite(onset) && onset > days) {
                due = Math.min(due, boundary(adultStart, onset));
            }
        }
        return due;
    }

    private static long boundary(long start, double days) {
        if (!Double.isFinite(days) || days > (Long.MAX_VALUE - start) / TICKS_PER_DAY) {
            return Long.MAX_VALUE;
        }
        return start + (long) Math.ceil(days * TICKS_PER_DAY);
    }
}
