package co.surumene.whatawonderfulchicken.runtime;

/** Adult time starts only once, upon first observed adult state. */
public final class AdultAge {
    private AdultAge() {}

    public static long start(boolean adult, long savedStart, long currentTime) {
        if (savedStart < 0 || currentTime < 0) {
            throw new IllegalArgumentException("biological timestamps must be nonnegative");
        }
        if (!adult || savedStart != 0) return savedStart;
        // Zero is reserved for an unknown start / currently immature entity.
        return Math.max(1L, currentTime);
    }

    public static double days(long savedStart, long currentTime) {
        if (savedStart < 0 || currentTime < 0) {
            throw new IllegalArgumentException("biological timestamps must be nonnegative");
        }
        if (savedStart == 0) return 0.0;
        return Math.max(0.0, (double)currentTime - savedStart) / 24_000.0;
    }
}
