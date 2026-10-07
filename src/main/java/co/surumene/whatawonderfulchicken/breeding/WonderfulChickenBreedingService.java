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
        GenomeEngine engine = currentEngine();
        return breed(parentA, parentB, engine.standardRandom(seed), engine);
    }

    public WonderfulChickenBreedingOutcome breed(
            BreedingParent parentA,
            BreedingParent parentB,
            GenomeRandom random) {
        GenomeEngine engine = currentEngine();
        return breed(parentA, parentB, random, engine);
    }

    public WonderfulChickenBreedingOutcome breedSources(
            BreedingParentSource parentA,
            BreedingParentSource parentB,
            long seed) {
        GenomeEngine engine = currentEngine();
        WonderfulChickenGenomeProfile profile = currentProfile();
        BreedingContext context = new BreedingContext(
                profile.backbone(),
                1.0,
                WonderfulChickenBreedingPolicy.deNovoForbiddenAddresses(),
                null,
                false);
        return breedSources(parentA, parentB, context, engine.standardRandom(seed), engine, profile);
    }

    public WonderfulChickenBreedingOutcome breedSources(
            BreedingParentSource parentA,
            BreedingParentSource parentB,
            BreedingContext context,
            GenomeRandom random) {
        return breedSources(
                parentA,
                parentB,
                context,
                random,
                currentEngine(),
                currentProfile());
    }

    private WonderfulChickenBreedingOutcome breed(
            BreedingParent parentA,
            BreedingParent parentB,
            GenomeRandom random,
            GenomeEngine engine) {
        Objects.requireNonNull(parentA, "parentA");
        Objects.requireNonNull(parentB, "parentB");
        Objects.requireNonNull(random, "random");

        WonderfulChickenGenomeProfile profile = currentProfile();
        BreedingParentSource sourceA =
                new BreedingParentSource.DiploidParent(parentA.data().genome());
        BreedingParentSource sourceB =
                new BreedingParentSource.DiploidParent(parentB.data().genome());

        WonderfulChickenBreedingOutcome.Fallback incompatible =
                backboneFailure(engine, profile.backbone(), sourceA, sourceB);
        if (incompatible != null) return incompatible;

        BreedingContext context =
                new WonderfulChickenBreedingContextFactory(
                        engine, profile, settings)
                        .create(parentA.data(), parentB.data(), random);

        return breedCompatibleSources(
                sourceA, sourceB, context, random, engine, profile);
    }

    private WonderfulChickenBreedingOutcome breedSources(
            BreedingParentSource parentA,
            BreedingParentSource parentB,
            BreedingContext context,
            GenomeRandom random,
            GenomeEngine engine,
            WonderfulChickenGenomeProfile profile) {
        Objects.requireNonNull(parentA, "parentA");
        Objects.requireNonNull(parentB, "parentB");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(random, "random");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(profile, "profile");

        WonderfulChickenBreedingOutcome.Fallback incompatible =
                backboneFailure(engine, profile.backbone(), parentA, parentB);
        if (incompatible != null) return incompatible;

        return breedCompatibleSources(
                parentA, parentB, context, random, engine, profile);
    }

    private WonderfulChickenBreedingOutcome breedCompatibleSources(
            BreedingParentSource parentA,
            BreedingParentSource parentB,
            BreedingContext context,
            GenomeRandom random,
            GenomeEngine engine,
            WonderfulChickenGenomeProfile profile) {
        BreedingResult result = engine.breed(
                profile,
                parentA,
                parentB,
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

    private static WonderfulChickenBreedingOutcome.Fallback backboneFailure(
            GenomeEngine engine,
            BackboneDefinition backbone,
            BreedingParentSource parentA,
            BreedingParentSource parentB) {
        BackboneCompatibilityReport backboneA =
                assessBackboneCompatibility(engine, backbone, parentA);
        if (!backboneA.compatible()) {
            return new WonderfulChickenBreedingOutcome.Fallback(
                    "parent A is incompatible with Wonderful Chicken Backbone: "
                            + backboneA.reason());
        }

        BackboneCompatibilityReport backboneB =
                assessBackboneCompatibility(engine, backbone, parentB);
        if (!backboneB.compatible()) {
            return new WonderfulChickenBreedingOutcome.Fallback(
                    "parent B is incompatible with Wonderful Chicken Backbone: "
                            + backboneB.reason());
        }
        return null;
    }

    private static BackboneCompatibilityReport assessBackboneCompatibility(
            GenomeEngine engine,
            BackboneDefinition backbone,
            BreedingParentSource source) {
        return switch (source) {
            case BreedingParentSource.DiploidParent diploid ->
                    engine.assessBackboneCompatibility(backbone, diploid.genome());
            case BreedingParentSource.Gamete gamete ->
                    engine.assessBackboneCompatibility(backbone, gamete.genome());
        };
    }

    private GenomeEngine currentEngine() {
        return Objects.requireNonNull(engineSupplier.get(), "current engine");
    }

    private WonderfulChickenGenomeProfile currentProfile() {
        return Objects.requireNonNull(profileSupplier.get(), "current profile");
    }
}
