package co.surumene.whatawonderfulchicken.data;

public enum PersonalityFactor {
    LEG_POWER(0x00),
    FLIGHT(0x01),
    FLAPPING(0x02),
    ENDURANCE(0x03),
    NUTRITION(0x04),
    NEUTRAL(0x05);

    private final int targetId;

    PersonalityFactor(int targetId) {
        this.targetId = targetId;
    }

    public int targetId() {
        return targetId;
    }
}
