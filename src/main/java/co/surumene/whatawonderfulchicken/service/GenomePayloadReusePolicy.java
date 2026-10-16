package co.surumene.whatawonderfulchicken.service;

import java.util.Objects;
import java.util.UUID;

/** Reuse external immutable data only when the exact validated objects and reference are unchanged. */
final class GenomePayloadReusePolicy {
    private GenomePayloadReusePolicy() {}

    static boolean canReuse(
            Object savedGenome, Object savedPhenotype, Object savedBackbone, UUID savedReference,
            Object currentGenome, Object currentPhenotype, Object currentBackbone, UUID entityReference,
            Integer entitySchemaVersion) {
        return savedGenome != null && savedPhenotype != null && savedBackbone != null
                && savedGenome == currentGenome
                && savedPhenotype == currentPhenotype
                && savedBackbone == currentBackbone
                && savedReference != null
                && Objects.equals(savedReference, entityReference)
                && Integer.valueOf(4).equals(entitySchemaVersion);
    }
}
