package co.surumene.whatawonderfulchicken.genome;

import co.surumene.wgl.api.BackboneDefinition;
import co.surumene.wgl.api.ChromosomeTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WonderfulChickenProfileFoundationTest {
    @Test
    void buildsChickenSpecificSixChromosomeBackbone() {
        WonderfulChickenGenomeSettings settings = WonderfulChickenGenomeSettings.defaults();
        BackboneDefinition backbone = WonderfulChickenProfileFoundation.backbone(settings);

        assertEquals("wonderful-chicken", backbone.backboneId());
        assertEquals(1, backbone.genomeFormatVersion());
        assertEquals(List.of(9216, 8192, 7168, 6144, 5120, 4096),
                backbone.baselineChromosomeLengths());

        for (ChromosomeTemplate chromosome : backbone.chromosomes()) {
            assertTrue(chromosome.anchors().size() >= 2);
            assertEquals(48, chromosome.anchors().getFirst().canonicalBits().bitLength());
        }
    }

    @Test
    void descriptorIsStableForSameSettingsAndChangesWithSemantics() {
        WonderfulChickenGenomeSettings defaults = WonderfulChickenGenomeSettings.defaults();
        var first = WonderfulChickenProfileFoundation.descriptor(defaults);
        var second = WonderfulChickenProfileFoundation.descriptor(defaults);

        assertEquals("wonderful-chicken", first.profileId());
        assertEquals(1, first.profileVersion());
        assertArrayEquals(first.semanticFingerprint(), second.semanticFingerprint());

        var changed = new WonderfulChickenGenomeSettings(
                defaults.seriousMaxScore(),
                defaults.seriousSpread(),
                0.09,
                defaults.traitExpressionThreshold(),
                defaults.traitStrongGap(),
                defaults.injuryExpressionThreshold(),
                defaults.injuryOnsetMaxGameDays(),
                defaults.injurySeverityRankMin(),
                defaults.injurySeverityRankMax(),
                defaults.divineMinHaplotypeScore(),
                defaults.divineTotalScore(),
                defaults.chromosomeLengthStandardDeviationRatio(),
                defaults.chromosomeLengthMinRatio(),
                defaults.chromosomeLengthMaxRatio());

        assertNotEquals(first, WonderfulChickenProfileFoundation.descriptor(changed));
    }
}
