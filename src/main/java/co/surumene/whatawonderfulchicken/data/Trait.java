package co.surumene.whatawonderfulchicken.data;

public enum Trait {
    JIKIDEN("jikiden"),
    HATENKO("hatenko"),
    YOME("yome"),
    HINOTORI("hinotori"),
    WATAGE("watage"),
    FUKUTSU("fukutsu"),
    SAIKUTSU_OUEN("saikutsu_ouen"),
    CHIKARAKOBU("chikarakobu"),
    KIN_NO_TAMAGO("kin_no_tamago"),
    MIHARIBAN("mihariban"),
    KUSE_MASHI("kuse_mashi");

    private final String key;
    Trait(String key) { this.key = key; }
    public String key() { return key; }

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
