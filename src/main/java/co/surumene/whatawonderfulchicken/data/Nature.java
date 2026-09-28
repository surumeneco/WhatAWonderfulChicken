package co.surumene.whatawonderfulchicken.data;

public enum Nature {
    MAJIME("majime", null, null),
    SEKASEKA("sekaseka", StatType.GROUND_SPEED, StatType.AIR_SPEED),
    UWA_NO_SORA("uwa_no_sora", StatType.AIR_SPEED, StatType.GROUND_SPEED),
    AWATENBO("awatenbo", StatType.ASCENT_SPEED, StatType.STAMINA),
    NEBARIZUYOI("nebarizuyoi", StatType.STAMINA, StatType.ASCENT_SPEED),
    KOMAME("komame", StatType.STAMINA_RECOVERY, StatType.STAMINA),
    TAMEKOMIYA("tamekomiya", StatType.STAMINA, StatType.STAMINA_RECOVERY),
    HANEKKAERI("hanekkaeri", StatType.JUMP_STRENGTH, StatType.GROUND_SPEED),
    ISOGINBO("isoginbo", StatType.GROUND_SPEED, StatType.JUMP_STRENGTH),
    KUISHINBO("kuishinbo", StatType.MAX_HEALTH, null),
    TOBASHIYA("tobashiya", StatType.AIR_SPEED, StatType.STAMINA),
    JIKKURI("jikkuri", StatType.STAMINA, StatType.AIR_SPEED);

    private final String key;
    private final StatType positive;
    private final StatType negative;

    Nature(String key, StatType positive, StatType negative) {
        this.key = key;
        this.positive = positive;
        this.negative = negative;
    }

    public String key() { return key; }

    public double multiplier(StatType stat, double adjustment) {
        if (stat == positive || (this == KUISHINBO && stat == StatType.SIZE)) return 1.0 + adjustment;
        if (stat == negative) return 1.0 - adjustment;
        return 1.0;
    }

    public static Nature fromGenes(int first, int second) {
        int a = Math.min(first, second);
        int b = Math.max(first, second);
        if (a < 0 || b > 4) throw new IllegalArgumentException("Invalid nature gene");
        return switch (a * 5 + b) {
            case 0 -> ISOGINBO;
            case 1 -> SEKASEKA;
            case 2 -> HANEKKAERI;
            case 3 -> MAJIME;
            case 4 -> ISOGINBO;
            case 6 -> UWA_NO_SORA;
            case 7 -> TOBASHIYA;
            case 8 -> JIKKURI;
            case 9 -> TOBASHIYA;
            case 12 -> AWATENBO;
            case 13, 14 -> NEBARIZUYOI;
            case 18 -> TAMEKOMIYA;
            case 19 -> KOMAME;
            case 24 -> KUISHINBO;
            default -> throw new IllegalArgumentException("Invalid nature gene pair");
        };
    }
}
