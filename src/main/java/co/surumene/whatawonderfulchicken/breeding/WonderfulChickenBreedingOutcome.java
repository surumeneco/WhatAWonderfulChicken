package co.surumene.whatawonderfulchicken.breeding;

import co.surumene.whatawonderfulchicken.data.PhenotypeSnapshot;
import co.surumene.wgl.api.DiploidGenome;

public sealed interface WonderfulChickenBreedingOutcome
        permits WonderfulChickenBreedingOutcome.Success,
                WonderfulChickenBreedingOutcome.Fallback {

    record Success(
            DiploidGenome genome,
            PhenotypeSnapshot phenotypeSnapshot,
            String marker) implements WonderfulChickenBreedingOutcome {}

    record Fallback(String detail) implements WonderfulChickenBreedingOutcome {}
}
