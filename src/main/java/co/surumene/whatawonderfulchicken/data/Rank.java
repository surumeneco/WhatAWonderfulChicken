package co.surumene.whatawonderfulchicken.data;

public enum Rank {
    MISERABLE("miserable"),
    VERY_LOW("very_low"),
    LOW("low"),
    SLIGHTLY_LOW("slightly_low"),
    COMMON("common"),
    SLIGHTLY_HIGH("slightly_high"),
    HIGH("high"),
    VERY_HIGH("very_high"),
    LEGENDARY("legendary"),
    MYTHICAL("mythical"),
    IMPOSSIBLE("impossible");

    private final String key;
    Rank(String key) { this.key = key; }
    public String key() { return key; }

    public static Rank fromNormalized(double normalized) {
        if (normalized > 1.25) return IMPOSSIBLE;
        if (normalized > 1.0) return MYTHICAL;
        double clamped = Math.max(0.0, Math.min(1.0, normalized));
        int index = Math.min(8, (int) Math.floor(clamped * 9.0));
        return values()[index];
    }
}