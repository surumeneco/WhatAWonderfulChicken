package co.surumene.whatawonderfulchicken.task;

/** Live A/B experiment gate, not a user-facing gameplay setting. */
public final class HeadAuditStrategy {
    public enum Mode { EVENT_WITH_PERIODIC_FALLBACK, EVENT_ONLY }
    private HeadAuditStrategy() {}
    public static boolean shouldPoll(Mode mode) {
        return mode == Mode.EVENT_WITH_PERIODIC_FALLBACK;
    }
}
