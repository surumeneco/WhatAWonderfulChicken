package co.surumene.whatawonderfulchicken.service;

/** Main-thread scheduler deadline conversion; real error backoff stays wall-clock based. */
final class LegacyRetryWakeDelay {
    private static final long MAX_TICKS = 1200L;
    private LegacyRetryWakeDelay() {}
    static long ticksUntil(long nextEligibleMillis, long nowMillis) {
        long difference = nextEligibleMillis <= nowMillis ? 0L
                : nextEligibleMillis - nowMillis;
        return Math.max(1L, Math.min(MAX_TICKS, (difference + 49L) / 50L));
    }
}
