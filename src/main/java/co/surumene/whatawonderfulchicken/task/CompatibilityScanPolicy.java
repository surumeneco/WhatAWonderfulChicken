package co.surumene.whatawonderfulchicken.task;

/** Explicit comparison of the compatibility recovery guarantee. */
public final class CompatibilityScanPolicy {
    public enum Mode { ALL_LOADED_CHUNKS, CANDIDATE_ONLY }
    private CompatibilityScanPolicy() {}
    public static boolean shouldScanKnownCandidates(Mode mode) { return true; }
    public static boolean shouldScanAllChunks(Mode mode) {
        return mode == Mode.ALL_LOADED_CHUNKS;
    }
}
