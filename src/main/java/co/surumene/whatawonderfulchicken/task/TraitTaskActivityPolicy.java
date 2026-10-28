package co.surumene.whatawonderfulchicken.task;

/** Pure experiment gate for scheduler lifecycle. */
public final class TraitTaskActivityPolicy {
    private TraitTaskActivityPolicy() {}
    public static boolean shouldRun(int activeSources) { return co.surumene.wcl.api.runtime.TraitActivityGate.shouldRun(activeSources, false); }
    public static boolean shouldClearAlerts(int activeSources) { return co.surumene.wcl.api.runtime.TraitActivityGate.shouldClearAlerts(activeSources); }
}
