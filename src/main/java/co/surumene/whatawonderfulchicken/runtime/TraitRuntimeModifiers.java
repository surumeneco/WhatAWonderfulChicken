package co.surumene.whatawonderfulchicken.runtime;

import co.surumene.whatawonderfulchicken.data.ExpressedTrait;
import co.surumene.whatawonderfulchicken.data.Trait;
import co.surumene.whatawonderfulchicken.data.TraitStrength;
import java.util.List;
import java.util.Objects;

/** Game runtime strength mapping from immutable decoded traits. */
public final class TraitRuntimeModifiers {
    private TraitRuntimeModifiers() {}

    public static int potionAmplifier(List<ExpressedTrait> traits, Trait target) {
        return isStrong(traits, target) ? 1 : 0;
    }

    public static boolean isStrong(List<ExpressedTrait> traits, Trait target) {
        Objects.requireNonNull(traits, "traits");
        Objects.requireNonNull(target, "target");
        return traits.stream().anyMatch(value ->
                value.trait() == target && value.strength() == TraitStrength.STRONG);
    }

    public static double specialEggChance(double base, List<ExpressedTrait> traits) {
        if (!Double.isFinite(base) || base < 0.0 || base > 1.0) {
            throw new IllegalArgumentException("base probability must be in [0,1]");
        }
        return Math.min(1.0, base * (isStrong(traits, Trait.KIN_NO_TAMAGO) ? 2.0 : 1.0));
    }
}
