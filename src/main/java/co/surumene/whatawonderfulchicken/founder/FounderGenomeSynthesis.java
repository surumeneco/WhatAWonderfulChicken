package co.surumene.whatawonderfulchicken.founder;

import co.surumene.wgl.api.SynthesisResult;
import java.util.Objects;

public record FounderGenomeSynthesis(
        FounderTarget founderTarget,
        WonderfulChickenSynthesisTarget synthesisTarget,
        SynthesisResult result) {
    public FounderGenomeSynthesis {
        Objects.requireNonNull(founderTarget, "founderTarget");
        Objects.requireNonNull(synthesisTarget, "synthesisTarget");
        Objects.requireNonNull(result, "result");
    }
}
