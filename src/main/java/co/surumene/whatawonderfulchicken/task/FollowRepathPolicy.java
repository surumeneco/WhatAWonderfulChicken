package co.surumene.whatawonderfulchicken.task;

import org.bukkit.util.Vector;

/** A still-valid path to an unchanged approach position does not need rebuilding. */
final class FollowRepathPolicy {
    private static final double MOVE_THRESHOLD_SQUARED = 0.25;

    private FollowRepathPolicy() {}

    static boolean needsRepath(boolean hasPath, Vector previous, Vector destination) {
        return !hasPath || previous == null
                || previous.distanceSquared(destination) >= MOVE_THRESHOLD_SQUARED;
    }
}
