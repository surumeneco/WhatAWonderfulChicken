package co.surumene.whatawonderfulchicken.data;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class PedigreeDataTest {
    @Test
    void roundTripsRealParentNatureAndTraitList() {
        var first = new AncestorSnapshot("parent", 2, "lineage", null,
                Nature.values()[0], java.util.List.of(Trait.values()[0]));
        var saved = new PedigreeData(first, null, null, null, null, null);
        var restored = PedigreeData.deserialize(saved.serialize()).parentA();
        assertEquals(first.personality(), restored.personality());
        assertEquals(first.expressedTraits(), restored.expressedTraits());
    }

    @Test
    void roundTripsDivineFounderParentMetadata() {
        var a = new AncestorSnapshot("parent", 1, "2A-56-1B-AC-8F-00", null,
                Nature.values()[0], java.util.List.of(Trait.values()[0]), true);
        var p = new PedigreeData(a, null, null, null, null, null);
        var actual = PedigreeData.deserialize(p.serialize()).parentA();
        assertTrue(actual.divineLineageExpressed());
        assertEquals(a.bloodlineId(), actual.bloodlineId());
        assertEquals(a.expressedTraits(), actual.expressedTraits());
    }

    @Test
    void readsVersionThreeWithoutInventingDivineExpression() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(3);
            out.writeBoolean(true);
            out.writeUTF("legacy");
            out.writeInt(1);
            out.writeUTF("old-ref");
            out.writeBoolean(false);
            out.writeBoolean(true);
            out.writeUTF(Nature.values()[0].name());
            out.writeByte(0);
            for (int i = 0; i < 5; i++) out.writeBoolean(false);
        }
        var restored = PedigreeData.deserialize(bytes.toByteArray());
        assertNotNull(restored.parentA());
        assertEquals(Nature.values()[0], restored.parentA().personality());
        assertFalse(restored.parentA().divineLineageExpressed());
        assertNull(restored.parentB());
    }

    @Test
    void roundTripGenotypesAndUnknownAncestors() {
        Genetics genes = new Genetics(3, 4, 1, 2);
        AncestorSnapshot a = new AncestorSnapshot("parent", 2, "bloodline-a", genes);
        PedigreeData pedigree = new PedigreeData(a, null, a, null, null, null);
        PedigreeData restored = PedigreeData.deserialize(pedigree.serialize());
        assertEquals(genes, restored.parentA().genetics());
        assertEquals(genes, restored.grandparentAA().genetics());
        assertNull(restored.parentB());
    }

    @Test
    void restoresLegacyVersionOneWithoutInventingParentGenes() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeInt(1);
            out.writeBoolean(true);
            out.writeUTF("ancestor");
            out.writeInt(4);
            out.writeUTF("old-id");
            for (int i = 0; i < 5; i++) out.writeBoolean(false);
        }
        PedigreeData restored = PedigreeData.deserialize(buffer.toByteArray());
        assertEquals("ancestor", restored.parentA().name());
        assertEquals(4, restored.parentA().generation());
        assertNull(restored.parentA().genetics());
        assertEquals("old-id", restored.parentA().bloodlineId());
        assertNull(restored.parentB());
        assertNull(PedigreeData.deserialize(new byte[]{0, 0, 0, 99}).parentA());
    }
}
