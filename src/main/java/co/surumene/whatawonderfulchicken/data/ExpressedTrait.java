package co.surumene.whatawonderfulchicken.data;

import java.util.Objects;

public record ExpressedTrait(Trait trait, TraitStrength strength) {
    public ExpressedTrait {
        Objects.requireNonNull(trait, "trait");
        Objects.requireNonNull(strength, "strength");
    }
}
