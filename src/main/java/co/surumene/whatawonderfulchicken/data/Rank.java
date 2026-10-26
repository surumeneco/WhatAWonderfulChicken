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
        return values()[co.surumene.wcl.api.ability.NormalizedRankIndex.of(normalized, false)];
    }}