package co.surumene.whatawonderfulchicken.data;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GeneticsTest {
    @Test
    void everyUnorderedPairIsDefinedAndSymmetric() {
        Set<Nature> natures = new HashSet<>();
        Set<Trait> traits = new HashSet<>();
        for (int a = 0; a < 5; a++) {
            for (int b = a; b < 5; b++) {
                Nature nature = Nature.fromGenes(a, b);
                Trait trait = Trait.fromGenes(a, b);
                assertEquals(nature, Nature.fromGenes(b, a));
                assertEquals(trait, Trait.fromGenes(b, a));
                natures.add(nature);
                traits.add(trait);
            }
        }
        assertEquals(12, natures.size());
        assertEquals(11, traits.size());
    }

    @Test
    void exclusivePairsAndUnorderedStorage() {
        assertEquals(Nature.KUISHINBO, Nature.fromGenes(4, 4));
        assertEquals(Trait.KIN_NO_TAMAGO, Trait.fromGenes(4, 4));
        for (int i = 0; i < 4; i++) {
            assertNotEquals(Nature.KUISHINBO, Nature.fromGenes(i, 4));
            assertNotEquals(Trait.KIN_NO_TAMAGO, Trait.fromGenes(i, 4));
        }
        Genetics normalized = new Genetics(4, 1, 3, 0);
        assertEquals(new Genetics(1, 4, 0, 3), normalized);
        assertEquals(normalized, Genetics.fromBytes(normalized.toBytes()));
        assertNull(Genetics.fromBytes(new byte[]{1, 5, 1, 1}));
        assertNull(Genetics.fromBytes(new byte[]{1, 1}));
    }

    @Test
    void randomSourceCanReachEveryAllele() {
        Set<Integer> natureFactors = new HashSet<>();
        Set<Integer> traitFactors = new HashSet<>();
        Random random = new Random(1357L);
        for (int i = 0; i < 1000; i++) {
            Genetics genes = Genetics.random(random);
            natureFactors.add(genes.natureA());
            natureFactors.add(genes.natureB());
            traitFactors.add(genes.traitA());
            traitFactors.add(genes.traitB());
        }
        assertEquals(Set.of(0, 1, 2, 3, 4), natureFactors);
        assertEquals(Set.of(0, 1, 2, 3, 4), traitFactors);
    }

    @Test
    void bothParentsContributeExactlyOneAllelePerPairWithoutMutation() {
        Genetics parentA = new Genetics(0, 0, 1, 1);
        Genetics parentB = new Genetics(4, 4, 3, 3);
        Genetics child = Genetics.breed(parentA, parentB, 0, new Random(1L));
        assertEquals(new Genetics(0, 4, 1, 3), child);
    }

    @Test
    void mutationChangesAtMostOneOfFourAlleles() {
        Genetics parents = new Genetics(0, 0, 0, 0);
        Random random = new Random(32L);
        for (int i = 0; i < 250; i++) {
            Genetics child = Genetics.breed(parents, parents, 1.0, random);
            long changes = java.util.stream.IntStream.of(child.natureA(), child.natureB(),
                    child.traitA(), child.traitB()).filter(value -> value != 0).count();
            assertTrue(changes <= 1);
        }
    }

    @Test
    void effectiveMultipliersDoNotMutateBaseStats() {
        WonderfulChickenData chicken = new WonderfulChickenData();
        chicken.genetics(new Genetics(4, 4, 3, 3)); // Kuishinbo + Kuse Mashi
        chicken.value(StatType.SIZE, 2.0);
        chicken.value(StatType.MAX_HEALTH, 21.0);
        assertEquals(2.3, chicken.effective(StatType.SIZE, 0.10), 1e-9);
        assertEquals(24.0, chicken.effective(StatType.MAX_HEALTH, 0.10), 1e-9);
        assertEquals(2.0, chicken.value(StatType.SIZE), 1e-9);
        assertEquals(21.0, chicken.value(StatType.MAX_HEALTH), 1e-9);
    }
}
