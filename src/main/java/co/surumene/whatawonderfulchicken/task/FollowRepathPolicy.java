package co.surumene.whatawonderfulchicken.task;

import org.bukkit.util.Vector;

/** A still-valid path to an unchanged approach position does not need rebuilding. */
final class FollowRepathPolicy {
    private static final double MOVE_THRESHOLD_SQUARED = 0.25;

    private FollowRepathPolicy() {}

    static boolean needsRepath(boolean hasPath, Vector previous, Vector destination) {
        double movedSquared = hasPath && previous != null
                ? previous.distanceSquared(destination) : 0.0;
        return co.surumene.wcl.api.behavior.RepathDecision.needsRepath(
                hasPath, previous == null, movedSquared, MOVE_THRESHOLD_SQUARED);
    }
}
