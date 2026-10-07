package co.surumene.whatawonderfulchicken.breeding;

import co.surumene.whatawonderfulchicken.data.ExpressedTrait;
import co.surumene.whatawonderfulchicken.data.PhenotypeSnapshot;
import co.surumene.whatawonderfulchicken.data.Trait;
import co.surumene.whatawonderfulchicken.data.TraitStrength;
import co.surumene.wgl.api.GenomeAddress;
import co.surumene.wgl.api.InheritanceConstraint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class WonderfulChickenBreedingPolicy {
    private static final GenomeAddress DIVINE_ADDRESS =
            new GenomeAddress(0x05, 0x00);
    private static final WonderfulChickenBreedingSettings DEFAULTS =
            WonderfulChickenBreedingSettings.defaults();

    private WonderfulChickenBreedingPolicy() {}

    public static Set<GenomeAddress> deNovoForbiddenAddresses() {
        return Set.of(DIVINE_ADDRESS);
    }

    public static double mutationMultiplier(
            PhenotypeSnapshot parentA,
            PhenotypeSnapshot parentB) {
        return mutationMultiplier(parentA, parentB, DEFAULTS);
    }

    public static double mutationMultiplier(
            PhenotypeSnapshot parentA,
            PhenotypeSnapshot parentB,
            WonderfulChickenBreedingSettings settings) {
        Objects.requireNonNull(parentA, "parentA");
        Objects.requireNonNull(parentB, "parentB");
        Objects.requireNonNull(settings, "settings");
        return hatenkoMultiplier(parentA, settings)
                * hatenkoMultiplier(parentB, settings);
    }

    public static double directRetentionProbability(TraitStrength strength) {
        return directRetentionProbability(strength, DEFAULTS);
    }

    public static double directRetentionProbability(
            TraitStrength strength,
            WonderfulChickenBreedingSettings settings) {
        Objects.requireNonNull(strength, "strength");
        Objects.requireNonNull(settings, "settings");
        double weak = settings.directInheritance().weakPreferProbability();
        return strength == TraitStrength.STRONG
                ? 1.0
                : weak;
    }

    public static Optional<TraitStrength> expressedTrait(
            PhenotypeSnapshot snapshot,
            Trait trait) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(trait, "trait");
        return snapshot.expressedTraits().stream()
                .filter(entry -> entry.trait() == trait)
                .map(ExpressedTrait::strength)
                .findFirst();
    }

    public static List<InheritanceConstraint> resolveSoftAgainstHard(
            List<InheritanceConstraint> hard,
            List<InheritanceConstraint> soft) {
        List<InheritanceConstraint> hardCopy =
                List.copyOf(Objects.requireNonNull(hard, "hard"));
        List<InheritanceConstraint> result = new ArrayList<>();
        for (InheritanceConstraint candidate : Objects.requireNonNull(soft, "soft")) {
            boolean conflict = hardCopy.stream().anyMatch(h -> overlaps(h, candidate));
            if (!conflict) result.add(candidate);
        }
        return List.copyOf(result);
    }

    private static double hatenkoMultiplier(
            PhenotypeSnapshot snapshot,
            WonderfulChickenBreedingSettings settings) {
        return expressedTrait(snapshot, Trait.HATENKO)
                .map(strength -> strength == TraitStrength.STRONG
                        ? settings.hatenkoTrait().strongParentMultiplier()
                        : settings.hatenkoTrait().weakParentMultiplier())
                .orElse(1.0);
    }

    private static boolean overlaps(
            InheritanceConstraint a,
            InheritanceConstraint b) {
        return a.chromosomeIndex() == b.chromosomeIndex()
                && a.startBit() < b.endBitExclusive()
                && b.startBit() < a.endBitExclusive();
    }
}
