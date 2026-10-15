package co.surumene.whatawonderfulchicken.breeding;

import co.surumene.whatawonderfulchicken.data.WonderfulChickenData;

import java.util.Objects;

public record BreedingParent(
        String displayName,
        WonderfulChickenData data) {

    public BreedingParent {
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(data, "data");
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (!data.hasGenomeModel()) {
            throw new IllegalArgumentException("breeding parent requires Genome and Phenotype Snapshot");
        }
    }
}
