package co.surumene.whatawonderfulchicken.founder;

import co.surumene.whatawonderfulchicken.genome.WonderfulChickenGenomeProfile;
import co.surumene.wgl.api.DecodeResult;
import co.surumene.wgl.api.GenomeEngine;
import co.surumene.wgl.api.GenomeRandom;
import co.surumene.wgl.api.SynthesisContext;
import co.surumene.wgl.api.SynthesisResult;

import java.util.Objects;

public final class WonderfulChickenFounderSynthesizer {
    private final GenomeEngine engine;
    private final WonderfulChickenGenomeProfile profile;
    private final WonderfulChickenFounderTargetGenerator targetGenerator;

    public WonderfulChickenFounderSynthesizer(
            GenomeEngine engine,
            WonderfulChickenGenomeProfile profile) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.targetGenerator = new WonderfulChickenFounderTargetGenerator(profile.synthesisSettings());
    }

    public FounderGenomeSynthesis synthesize(FounderOrigin origin, long seed) {
        return synthesize(origin, engine.standardRandom(seed));
    }

    public FounderGenomeSynthesis synthesize(FounderOrigin origin, GenomeRandom random) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(random, "random");

        FounderTarget founder = targetGenerator.generate(origin, random);
        WonderfulChickenSynthesisTarget target = WonderfulChickenSynthesisTarget.from(
                founder, profile.settings(), profile.synthesisSettings(), random);

        SynthesisResult result = engine.synthesize(
                profile, profile.backbone(), target, SynthesisContext.defaults(), random);
        if (result instanceof SynthesisResult.Success success) {
            DecodeResult<?> canonical = engine.decode(profile, success.genome());
            result = new SynthesisResult.Success(success.genome(), canonical);
        }
        return new FounderGenomeSynthesis(founder, target, result);
    }
}
