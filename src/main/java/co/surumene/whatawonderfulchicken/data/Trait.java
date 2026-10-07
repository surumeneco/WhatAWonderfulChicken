package co.surumene.whatawonderfulchicken.data;

public enum Trait {
    JIKIDEN(0x00, "jikiden"),
    HATENKO(0x01, "hatenko"),
    YOME(0x02, "yome"),
    HINOTORI(0x03, "hinotori"),
    WATAGE(0x04, "watage"),
    FUKUTSU(0x05, "fukutsu"),
    SAIKUTSU_OUEN(0x06, "saikutsu_ouen"),
    CHIKARAKOBU(0x07, "chikarakobu"),
    KIN_NO_TAMAGO(0x08, "kin_no_tamago"),
    MIHARIBAN(0x09, "mihariban"),
    KUSE_MASHI(0x0A, "kuse_mashi"),
    OYOGI_JOUZU(0x0B, "oyogi_jouzu"),
    YUKIGUNI_UMARE(0x0C, "yukiguni_umare");

    private final int targetId;
    private final String key;

    Trait(int targetId, String key) {
        this.targetId = targetId;
        this.key = key;
    }

    public int targetId() { return targetId; }
    public String key() { return key; }

    public static Trait fromTargetId(int targetId) {
        for (Trait trait : values()) {
            if (trait.targetId == targetId) return trait;
        }
        throw new IllegalArgumentException("Unknown trait target id: " + targetId);
    }

    public static Trait fromGenes(int first, int second) {
        int a = Math.min(first, second);
        int b = Math.max(first, second);
        if (a < 0 || b > 4) throw new IllegalArgumentException("Invalid trait gene");
        return switch (a * 5 + b) {
            case 0, 2 -> YOME;
            case 1, 4 -> MIHARIBAN;
            case 3 -> SAIKUTSU_OUEN;
            case 6, 9 -> HINOTORI;
            case 7 -> WATAGE;
            case 8 -> CHIKARAKOBU;
            case 12, 13 -> FUKUTSU;
            case 14 -> JIKIDEN;
            case 18 -> KUSE_MASHI;
            case 19 -> HATENKO;
            case 24 -> KIN_NO_TAMAGO;
            default -> throw new IllegalArgumentException("Invalid trait gene pair");
        };
    }
}
