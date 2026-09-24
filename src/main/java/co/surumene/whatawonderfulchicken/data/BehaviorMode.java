package co.surumene.whatawonderfulchicken.data;

public enum BehaviorMode {
    WANDER,
    WAIT,
    FOLLOW;

    public BehaviorMode next() {
        return switch (this) {
            case WANDER -> WAIT;
            case WAIT -> FOLLOW;
            case FOLLOW -> WANDER;
        };
    }

    public static BehaviorMode parse(String value) {
        return switch (value.toLowerCase()) {
            case "wander", "roam", "roaming" -> WANDER;
            case "wait", "waiting", "stay" -> WAIT;
            case "follow", "following" -> FOLLOW;
            default -> throw new IllegalArgumentException("Unknown behavior mode: " + value);
        };
    }
}