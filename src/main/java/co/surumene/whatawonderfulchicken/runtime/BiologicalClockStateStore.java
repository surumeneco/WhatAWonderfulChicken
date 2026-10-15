package co.surumene.whatawonderfulchicken.runtime;

public interface BiologicalClockStateStore {
    BiologicalClockState load();
    void save(BiologicalClockState state);
}
