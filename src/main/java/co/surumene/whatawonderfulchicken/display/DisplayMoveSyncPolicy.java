package co.surumene.whatawonderfulchicken.display;

/** Pure decision shared by Paper movement listeners. */
public final class DisplayMoveSyncPolicy {
    private DisplayMoveSyncPolicy() {}
    public static boolean shouldSync(boolean positionChanged, boolean orientationChanged) {
        return positionChanged || orientationChanged;
    }
}
