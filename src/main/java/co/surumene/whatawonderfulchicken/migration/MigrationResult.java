package co.surumene.whatawonderfulchicken.migration;

import co.surumene.whatawonderfulchicken.data.PhenotypeSnapshot;
import co.surumene.wgl.api.DiploidGenome;

import java.util.Objects;

public record MigrationResult(
        DiploidGenome genome,
        PhenotypeSnapshot phenotypeSnapshot,
        long adultBiologicalTime) {

    public MigrationResult {
        Objects.requireNonNull(genome, "genome");
        Objects.requireNonNull(phenotypeSnapshot, "phenotypeSnapshot");
        if (adultBiologicalTime < 0L) {
            throw new IllegalArgumentException("adultBiologicalTime must be >= 0");
        }
    }
}
