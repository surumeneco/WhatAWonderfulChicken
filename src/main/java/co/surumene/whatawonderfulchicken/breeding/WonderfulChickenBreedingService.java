package co.surumene.whatawonderfulchicken.breeding;

import co.surumene.whatawonderfulchicken.data.PhenotypeSnapshot;
import co.surumene.whatawonderfulchicken.genome.PhenotypeOrigin;
import co.surumene.whatawonderfulchicken.genome.WonderfulChickenDecodedPhenotype;
import co.surumene.whatawonderfulchicken.genome.WonderfulChickenGenomeProfile;
import co.surumene.wgl.api.*;

import java.util.Objects;
import java.util.function.Supplier;

public final class WonderfulChickenBreedingService {
    private final Supplier<GenomeEngine> engineSupplier;
    private final Supplier<WonderfulChickenGenomeProfile> profileSupplier;
    private final WonderfulChickenBreedingSettings settings;

    public WonderfulChickenBreedingService(
            Supplier<GenomeEngine> engineSupplier,
            Supplier<WonderfulChickenGenomeProfile> profileSupplier) {
        this(engineSupplier, profileSupplier, WonderfulChickenBreedingSettings.defaults());
    }

    public WonderfulChickenBreedingService(
            Supplier<GenomeEngine> engineSupplier,
            Supplier<WonderfulChickenGenomeProfile> profileSupplier,
            WonderfulChickenBreedingSettings settings) {
        this.engineSupplier = Objects.requireNonNull(engineSupplier, "engineSupplier");
        this.profileSupplier = Objects.requireNonNull(profileSupplier, "profileSupplier");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public WonderfulChickenBreedingOutcome breed(
            BreedingParent parentA,
            BreedingParent parentB,
            long seed) {
        GenomeEngine engine = Objects.requireNonNull(engineSupplier.get(), "current engine");
        return breed(parentA, parentB, engine.standardRandom(seed), engine);
    }

    public WonderfulChickenBreedingOutcome breed(
            BreedingParent parentA,
            BreedingParent parentB,
            GenomeRandom random) {
        GenomeEngine engine = Objects.requireNonNull(engineSupplier.get(), "current engine");
        return breed(parentA, parentB, random, engine);
    }

    private WonderfulChickenBreedingOutcome breed(
            BreedingParent parentA,
            BreedingParent parentB,
            GenomeRandom random,
            GenomeEngine engine) {
        Objects.requireNonNull(parentA, "parentA");
        Objects.requireNonNull(parentB, "parentB");
        Objects.requireNonNull(random, "random");

        WonderfulChickenGenomeProfile profile =
                Objects.requireNonNull(profileSupplier.get(), "current profile");

        BackboneCompatibilityReport backboneA =
                engine.assessBackboneCompatibility(
                        profile.backbone(), parentA.data().genome());
        if (!backboneA.compatible()) {
            return new WonderfulChickenBreedingOutcome.Fallback(
                    "parent A is incompatible with Wonderful Chicken Backbone: "
                            + backboneA.reason());
        }

        BackboneCompatibilityReport backboneB =
                engine.assessBackboneCompatibility(
                        profile.backbone(), parentB.data().genome());
        if (!backboneB.compatible()) {
            return new WonderfulChickenBreedingOutcome.Fallback(
                    "parent B is incompatible with Wonderful Chicken Backbone: "
                            + backboneB.reason());
        }

        BreedingContext context =
                new WonderfulChickenBreedingContextFactory(
                        engine, profile, settings)
                        .create(parentA.data(), parentB.data(), random);

        BreedingResult result = engine.breed(
                profile,
                new BreedingParentSource.DiploidParent(parentA.data().genome()),
                new BreedingParentSource.DiploidParent(parentB.data().genome()),
                context,
                random);

        if (result instanceof BreedingResult.NoViableOffspring failure) {
            return new WonderfulChickenBreedingOutcome.Fallback(
                    failure.reason() + ": " + failure.detail());
        }

        BreedingResult.Success success = (BreedingResult.Success) result;
        DecodeResult<?> decoded = success.decoded();
        if (!(decoded.phenotype() instanceof WonderfulChickenDecodedPhenotype phenotype)) {
            throw new IllegalStateException(
                    "WGL breeding returned a non-WWC phenotype");
        }

        PhenotypeSnapshot snapshot = phenotype.toSnapshot(
                decoded.identity(),
                PhenotypeOrigin.BREEDING);

        String marker =
                engine.marker(profile.backbone(), success.genome()).formatted();

        return new WonderfulChickenBreedingOutcome.Success(
                success.genome(),
                snapshot,
                marker);
    }
}
