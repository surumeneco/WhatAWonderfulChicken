package co.surumene.whatawonderfulchicken.data;

public enum DevelopmentFactor {
    MATURITY(0x00),
    JUVENILE_SUPPRESSION(0x01),
    AGING_START(0x02),
    AGING_SPEED(0x03),
    AGING_RESISTANCE(0x04);

    private final int targetId;

    DevelopmentFactor(int targetId) {
        this.targetId = targetId;
    }

    public int targetId() {
        return targetId;
    }
}
