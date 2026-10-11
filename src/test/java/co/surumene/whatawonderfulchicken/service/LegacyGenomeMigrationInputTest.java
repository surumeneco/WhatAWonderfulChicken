package co.surumene.whatawonderfulchicken.service;

import co.surumene.whatawonderfulchicken.data.Genetics;
import co.surumene.whatawonderfulchicken.data.StatType;
import co.surumene.whatawonderfulchicken.data.WonderfulChickenData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class LegacyGenomeMigrationInputTest {
    @Test
    void legacySnapshotIsDetachedAndDetectsChanges() {
        WonderfulChickenData original = new WonderfulChickenData();
        for (StatType stat : StatType.values()) original.normalized(stat, 0.65);
        original.genetics(new Genetics(0, 2, 1, 4));
        original.generation(3);

        var input = LegacyGenomeMigrationCoordinator.LegacyInput.capture(original);
        assertEquals(input, LegacyGenomeMigrationCoordinator.LegacyInput.capture(original));

        original.normalized(StatType.STAMINA, 0.85);
        assertNotEquals(input, LegacyGenomeMigrationCoordinator.LegacyInput.capture(original));

        var copy = input.toLegacyData();
        assertEquals(0.65, copy.normalized(StatType.STAMINA));
        assertEquals(3, copy.generation());
        assertEquals(input.genetics(), copy.genetics());
        assertEquals(input.genetics().nature(), copy.nature());
        assertEquals(input.genetics().trait(), copy.trait());
        assertFalse(copy.hasGenomeModel());
    }
}
