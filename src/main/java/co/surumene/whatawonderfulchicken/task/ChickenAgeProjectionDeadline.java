package co.surumene.whatawonderfulchicken.task;
import co.surumene.whatawonderfulchicken.runtime.AgeCurve;
import co.surumene.wcl.api.time.AgeDeadlineMath;
import java.util.List;
/** Consumer contract and legacy overflow behavior remain WWC-owned. */
final class ChickenAgeProjectionDeadline {
    private ChickenAgeProjectionDeadline() {}
    static long next(long now,long adultStart,AgeCurve curve,List<Double> onsets) {
        return AgeDeadlineMath.next(now,adultStart,curve.growthEndGameDay(),
                curve.agingStartGameDay(),curve.elderGameDay(),onsets,AgeDeadlineMath.BoundaryMode.LEGACY);
    }
}
