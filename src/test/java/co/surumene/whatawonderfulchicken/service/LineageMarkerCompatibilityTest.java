package co.surumene.whatawonderfulchicken.service;

import co.surumene.whatawonderfulchicken.data.AncestorSnapshot;
import co.surumene.whatawonderfulchicken.data.PedigreeData;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class LineageMarkerCompatibilityTest {
    @Test
    void legacyUuidIsDistinguishedFromGenomeMarker() {
        assertTrue(WonderfulChickenStore.isLegacyLineageId(
                "d85f2e08-3a54-4439-a119-0bc3d49a1900"));
        assertFalse(WonderfulChickenStore.isLegacyLineageId("2A-56-1B-AC-8F-00"));
        assertFalse(WonderfulChickenStore.isLegacyLineageId(""));
        assertFalse(WonderfulChickenStore.isLegacyLineageId(null));
    }

    @Test
    void historicalAncestorCanBeReplacedOnlyWithVerifiedMarker() {
        var old = new AncestorSnapshot("older parent", 4,
                "d85f2e08-3a54-4439-a119-0bc3d49a1900");
        var updated = old.withBloodlineId("2A-56-1B-AC-8F-00");
        assertEquals(old.name(), updated.name());
        assertEquals(old.generation(), updated.generation());
        assertEquals("2A-56-1B-AC-8F-00", updated.bloodlineId());
        var persisted = new PedigreeData(updated, null, old, null, null, null);
        var restored = PedigreeData.deserialize(persisted.serialize());
        assertEquals("2A-56-1B-AC-8F-00", restored.parentA().bloodlineId());
        assertEquals(old.bloodlineId(), restored.grandparentAA().bloodlineId());
    }
}
