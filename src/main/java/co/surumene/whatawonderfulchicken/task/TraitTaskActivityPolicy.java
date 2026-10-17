package co.surumene.whatawonderfulchicken.task;

/** Pure experiment gate for scheduler lifecycle. */
public final class TraitTaskActivityPolicy {
    private TraitTaskActivityPolicy() {}
    public static boolean shouldRun(int activeSources) { return activeSources > 0; }
}
