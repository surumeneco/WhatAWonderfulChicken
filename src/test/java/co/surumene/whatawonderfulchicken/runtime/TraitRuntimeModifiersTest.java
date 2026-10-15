package co.surumene.whatawonderfulchicken.runtime;

import co.surumene.whatawonderfulchicken.data.ExpressedTrait;
import co.surumene.whatawonderfulchicken.data.Trait;
import co.surumene.whatawonderfulchicken.data.TraitStrength;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class TraitRuntimeModifiersTest {
    @Test void weakAndStrongExpressionsMapToRespectivePotionLevels() {
        var traits = List.of(
                new ExpressedTrait(Trait.FUKUTSU, TraitStrength.WEAK),
                new ExpressedTrait(Trait.SAIKUTSU_OUEN, TraitStrength.STRONG));
        assertEquals(0, TraitRuntimeModifiers.potionAmplifier(traits, Trait.FUKUTSU));
        assertEquals(1, TraitRuntimeModifiers.potionAmplifier(traits, Trait.SAIKUTSU_OUEN));
        assertEquals(0, TraitRuntimeModifiers.potionAmplifier(traits, Trait.CHIKARAKOBU));
    }

    @Test void strongEggTraitDoublesProbabilityWithoutChangingConditionalDropMix() {
        var strong = List.of(new ExpressedTrait(Trait.KIN_NO_TAMAGO, TraitStrength.STRONG));
        var weak = List.of(new ExpressedTrait(Trait.KIN_NO_TAMAGO, TraitStrength.WEAK));
        assertEquals(0.10, TraitRuntimeModifiers.specialEggChance(0.05, strong));
        assertEquals(0.05, TraitRuntimeModifiers.specialEggChance(0.05, weak));
        assertEquals(1.0, TraitRuntimeModifiers.specialEggChance(0.8, strong));
    }

    @Test void invalidProbabilityFailsRatherThanFallingBack() {
        assertThrows(IllegalArgumentException.class,
                () -> TraitRuntimeModifiers.specialEggChance(Double.NaN, List.of()));
    }
}
