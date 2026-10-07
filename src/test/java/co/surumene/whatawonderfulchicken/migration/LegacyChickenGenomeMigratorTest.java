package co.surumene.whatawonderfulchicken.migration;

import co.surumene.whatawonderfulchicken.data.*;
import co.surumene.whatawonderfulchicken.genome.*;
import co.surumene.wgl.api.DecodeResult;
import co.surumene.wgl.api.DecodedGene;
import co.surumene.wgl.api.GenomeAddress;
import co.surumene.wgl.core.EngineConfig;
import co.surumene.wgl.core.WonderfulGenomeEngine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class LegacyChickenGenomeMigratorTest {
    private final WonderfulGenomeEngine engine =
            WonderfulGenomeEngine.create(EngineConfig.defaults());
    private final WonderfulChickenGenomeProfile profile =
            new WonderfulChickenGenomeProfile(
                    WonderfulChickenGenomeSettings.defaults(),
                    WonderfulChickenSynthesisSettings.defaults(),
                    engine.geneSequenceCodec());
    private final LegacyChickenGenomeMigrator migrator =
            new LegacyChickenGenomeMigrator(engine, profile);

    @Test
    void preservesLegacyAbilitiesAndExpressedNatureWhileAddingGenomeData() {
        WonderfulChickenData legacy = legacy(0.70, 0, new Genetics(0, 1, 4, 4));
        MigrationResult migrated = migrator.migrate(legacy, 0L, 2026100701L);

        assertEquals(0L, migrated.adultBiologicalTime());
        assertEquals(legacy.nature(), migrated.phenotypeSnapshot().personality());
        for (StatType stat : StatType.values()) {
            assertEquals(legacy.normalized(stat),
                    migrated.phenotypeSnapshot().normalizedAbilities().get(stat), 0.0);
        }
        assertTrue(migrated.phenotypeSnapshot().expressedTraits().stream()
                .anyMatch(t -> t.trait() == legacy.trait()));
        assertArrayEquals(engine.encode(migrated.genome()),
                engine.encode(engine.decodeBinary(engine.encode(migrated.genome()))));
    }

    @Test
    void mythicalLegacyIndividualUsesExtraordinaryGenomeLayer() {
        WonderfulChickenData legacy = legacy(1.20, 0, new Genetics(0, 0, 2, 2));
        MigrationResult migrated = migrator.migrate(legacy, 1234L, 2026100702L);

        DecodeResult<WonderfulChickenDecodedPhenotype> decoded =
                engine.decode(profile, migrated.genome());
        assertTrue(decoded.phenotype().extraordinaryContributions().values().stream()
                .anyMatch(v -> v > 0.0));
        for (StatType stat : StatType.values()) {
            assertEquals(1.20,
                    migrated.phenotypeSnapshot().normalizedAbilities().get(stat), 0.0);
        }
    }

    @Test
    void laterGenerationMythicalMigrationDoesNotInjectDivineGenes() {
        WonderfulChickenData legacy = legacy(1.20, 4, new Genetics(2, 2, 1, 1));
        MigrationResult migrated = migrator.migrate(legacy, 0L, 2026100704L);

        DecodeResult<WonderfulChickenDecodedPhenotype> decoded =
                engine.decode(profile, migrated.genome());
        assertFalse(decoded.decodedGenome().physicalGenes().stream()
                .filter(DecodedGene::addressValid)
                .anyMatch(gene -> new GenomeAddress(0x05, 0x00).equals(gene.address())));
        assertTrue(decoded.phenotype().extraordinaryContributions().values().stream()
                .anyMatch(v -> v > 0.0));
    }

    @Test
    void migrationDoesNotAlterPedigreeOrLegacyGenetics() {
        WonderfulChickenData legacy = legacy(0.60, 3, new Genetics(1, 3, 0, 4));
        PedigreeData pedigree = new PedigreeData(
                new AncestorSnapshot("A", 2, "a", new Genetics(0,0,0,0)),
                new AncestorSnapshot("B", 2, "b", new Genetics(1,1,1,1)),
                null, null, null, null);
        legacy.pedigree(pedigree);

        MigrationResult migrated = migrator.migrate(legacy, 0L, 2026100703L);

        assertEquals(pedigree, legacy.pedigree());
        assertEquals(new Genetics(1,3,0,4), legacy.genetics());
        assertNotNull(migrated.genome());
    }

    private static WonderfulChickenData legacy(double normalized, int generation, Genetics genetics) {
        WonderfulChickenData data = new WonderfulChickenData();
        for (StatType stat : StatType.values()) {
            data.normalized(stat, normalized);
            data.value(stat, normalized);
        }
        data.generation(generation);
        data.genetics(genetics);
        data.bloodlineId("legacy");
        return data;
    }
}
