package co.surumene.whatawonderfulchicken.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GenomePayloadReusePolicyTest {
    private final Object genome = new Object();
    private final Object phenotype = new Object();
    private final Object backbone = new Object();
    private final UUID reference = UUID.randomUUID();

    @Test void reusesOnlyExactVerifiedImmutableObjectIdentitiesAndPdcReference() {
        assertTrue(GenomePayloadReusePolicy.canReuse(genome, phenotype, backbone, reference,
                genome, phenotype, backbone, reference, 4));
        assertFalse(GenomePayloadReusePolicy.canReuse(genome, phenotype, backbone, reference,
                new Object(), phenotype, backbone, reference, 4));
        assertFalse(GenomePayloadReusePolicy.canReuse(genome, phenotype, backbone, reference,
                genome, new Object(), backbone, reference, 4));
        assertFalse(GenomePayloadReusePolicy.canReuse(genome, phenotype, backbone, reference,
                genome, phenotype, new Object(), reference, 4));
        assertFalse(GenomePayloadReusePolicy.canReuse(genome, phenotype, backbone, reference,
                genome, phenotype, backbone, UUID.randomUUID(), 4));
    }

    @Test void refusesMissingReferenceOrUnmigratedSchema() {
        assertFalse(GenomePayloadReusePolicy.canReuse(genome, phenotype, backbone, null,
                genome, phenotype, backbone, null, 4));
        assertFalse(GenomePayloadReusePolicy.canReuse(genome, phenotype, backbone, reference,
                genome, phenotype, backbone, reference, 3));
        assertFalse(GenomePayloadReusePolicy.canReuse(genome, phenotype, backbone, reference,
                genome, phenotype, backbone, reference, null));
    }
}
