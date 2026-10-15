package co.surumene.whatawonderfulchicken.gui;

import co.surumene.whatawonderfulchicken.data.ExpressedTrait;
import co.surumene.whatawonderfulchicken.data.InjuryPhenotype;
import co.surumene.whatawonderfulchicken.data.Nature;
import co.surumene.whatawonderfulchicken.data.StatType;
import co.surumene.whatawonderfulchicken.data.Trait;
import co.surumene.whatawonderfulchicken.data.TraitStrength;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ChickenGuiPresentationTest {
    @Test void neutralNatureHasNoArrow() {
        for (StatType stat : StatType.values()) {
            assertEquals("", ChickenGuiPresentation.natureArrow(Nature.MAJIME, stat));
        }
    }
    @Test void arrowsOnlyIndicatePersonalityNotOtherEffects() {
        assertEquals("↑", ChickenGuiPresentation.natureArrow(Nature.SEKASEKA, StatType.GROUND_SPEED));
        assertEquals("↓", ChickenGuiPresentation.natureArrow(Nature.SEKASEKA, StatType.AIR_SPEED));
        assertEquals("", ChickenGuiPresentation.natureArrow(Nature.SEKASEKA, StatType.SIZE));
        assertEquals("↑", ChickenGuiPresentation.natureArrow(Nature.KUISHINBO, StatType.SIZE));
        assertEquals("↑", ChickenGuiPresentation.natureArrow(Nature.CHAKKARI, StatType.STEP_HEIGHT));
    }
    @Test void absenceAndTwoDifferentExpressedTraitsRemainRepresentable() {
        assertTrue(ChickenGuiPresentation.traits(List.of()).isEmpty());
        var two = List.of(new ExpressedTrait(Trait.FUKUTSU, TraitStrength.WEAK),
                new ExpressedTrait(Trait.WATAGE, TraitStrength.WEAK));
        assertEquals(two, ChickenGuiPresentation.traits(two));
        assertEquals(List.of(new ExpressedTrait(Trait.CHIKARAKOBU, TraitStrength.STRONG)),
                ChickenGuiPresentation.traits(
                        List.of(new ExpressedTrait(Trait.CHIKARAKOBU, TraitStrength.STRONG))));
    }
    @Test void legacyTraitIsRetainedOnlyWithoutGenomeSnapshot() {
        assertEquals(List.of(new ExpressedTrait(Trait.FUKUTSU, TraitStrength.WEAK)),
                ChickenGuiPresentation.legacyTraits(Trait.FUKUTSU));
        assertTrue(ChickenGuiPresentation.legacyTraits(null).isEmpty());
    }
    @Test void injuryOnlyVisibleAfterAdultOnset() {
        var injuries = List.of(new InjuryPhenotype(StatType.STAMINA, 3, 1));
        assertFalse(ChickenGuiPresentation.activeInjury(injuries, StatType.STAMINA, 0, 24000 * 10L));
        assertFalse(ChickenGuiPresentation.activeInjury(injuries, StatType.STAMINA, 24000, 24000 * 3L));
        assertTrue(ChickenGuiPresentation.activeInjury(injuries, StatType.STAMINA, 24000, 24000 * 4L));
        assertFalse(ChickenGuiPresentation.activeInjury(injuries, StatType.SIZE, 24000, 24000 * 4L));
    }
}
