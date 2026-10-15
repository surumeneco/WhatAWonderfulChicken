package co.surumene.whatawonderfulchicken.gui;

import co.surumene.whatawonderfulchicken.data.ExpressedTrait;
import co.surumene.whatawonderfulchicken.data.InjuryPhenotype;
import co.surumene.whatawonderfulchicken.data.Nature;
import co.surumene.whatawonderfulchicken.data.StatType;
import co.surumene.whatawonderfulchicken.data.Trait;
import co.surumene.whatawonderfulchicken.data.TraitStrength;
import co.surumene.whatawonderfulchicken.runtime.AdultAge;
import java.util.List;
import java.util.Objects;

/** Read-only projection for player-facing metadata; never exposes latent injury details. */
public final class ChickenGuiPresentation {
    private ChickenGuiPresentation() {}

    /** Unicode arrows indicate only the direction of personality's multiplicative effect. */
    public static String natureArrow(Nature nature, StatType stat) {
        Objects.requireNonNull(nature, "nature");
        Objects.requireNonNull(stat, "stat");
        double factor = nature.multiplier(stat, 0.1);
        return factor > 1.0 ? "↑" : factor < 1.0 ? "↓" : "";
    }

    /** When a decoded phenotype exists its expressed trait list is authoritative, including empty. */
    public static List<ExpressedTrait> traits(List<ExpressedTrait> decoded) {
        return List.copyOf(Objects.requireNonNull(decoded, "decoded"));
    }

    public static List<ExpressedTrait> legacyTraits(Trait trait) {
        return trait == null ? List.of()
                : List.of(new ExpressedTrait(trait, TraitStrength.WEAK));
    }

    public static boolean activeInjury(
            List<InjuryPhenotype> injuries, StatType stat, long adultStart, long now) {
        Objects.requireNonNull(injuries, "injuries");
        Objects.requireNonNull(stat, "stat");
        if (adultStart == 0L) return false;
        double days = AdultAge.days(adultStart, now);
        return injuries.stream().anyMatch(injury ->
                injury.stat() == stat && days >= injury.onsetGameDay());
    }
}
