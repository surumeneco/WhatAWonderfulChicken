package co.surumene.whatawonderfulchicken.chickentrap;

import java.util.Objects;

public final class ChickenTrapPolicy {
    public static final long DAY_TICKS = 24_000L;
    public static final int CHECK_TIME = 18_000;
    public static final int DAWN_TIME = 23_000;
    public static final double SPAWN_RADIUS = 32.0;
    public static final long GRACE_MILLIS = 3_000L;

    private ChickenTrapPolicy() {}

    /** A command/bed skip never counts as natural arrival, even when it lands exactly at 18000. */
    public static boolean naturalCheck(long previous, long current) {
        return current == previous + 1L && Math.floorMod(current, DAY_TICKS) == CHECK_TIME;
    }

    public static boolean dawnCrossed(long previous, long current) {
        return current > previous &&
                Math.floorDiv(current - DAWN_TIME, DAY_TICKS)
                > Math.floorDiv(previous - DAWN_TIME, DAY_TICKS);
    }

    public static boolean extendedBowRange(double distanceSquared) {
        return Double.isFinite(distanceSquared)
                && distanceSquared > 225.0 && distanceSquared <= 400.0;
    }

    /** A mounted encounter contains a rider and its Chicken; it expires with one burst. */
    public static boolean linkedMountPair(String role, String partnerRole, boolean reciprocal) {
        return reciprocal && (
                ChickenTrapStateStore.RIDER.equals(role)
                        && ChickenTrapStateStore.MOUNT.equals(partnerRole)
                || ChickenTrapStateStore.MOUNT.equals(role)
                        && ChickenTrapStateStore.RIDER.equals(partnerRole));
    }

    /** A client-facing ItemDisplay is not a second explosion source. */
    public static boolean burstOnExpiry(String role) {
        return role != null && !ChickenTrapStateStore.VISUAL.equals(role);
    }

    public static boolean isNewMoon(long fullTime) {
        return Math.floorMod(Math.floorDiv(fullTime, DAY_TICKS), 8L) == 4L;
    }

    public static boolean roll(double draw, double chance) {
        if (!Double.isFinite(draw) || draw < 0.0 || draw >= 1.0)
            throw new IllegalArgumentException("draw must be [0,1)");
        if (!Double.isFinite(chance) || chance < 0.0 || chance > 1.0)
            throw new IllegalArgumentException("chance must be [0,1]");
        return draw < chance;
    }

    public static Offset offset(double radialDraw, double angleDraw) {
        if (!Double.isFinite(radialDraw) || radialDraw < 0.0 || radialDraw > 1.0
                || !Double.isFinite(angleDraw) || angleDraw < 0.0 || angleDraw >= 1.0)
            throw new IllegalArgumentException("invalid random draw");
        double radius = StrictMath.sqrt(radialDraw) * SPAWN_RADIUS;
        double angle = angleDraw * StrictMath.PI * 2.0;
        return new Offset(radius * StrictMath.cos(angle), radius * StrictMath.sin(angle));
    }

    public record Offset(double x, double z) {}
}
