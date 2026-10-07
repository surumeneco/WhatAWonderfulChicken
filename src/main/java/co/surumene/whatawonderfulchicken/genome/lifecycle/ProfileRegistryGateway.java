package co.surumene.whatawonderfulchicken.genome.lifecycle;

import co.surumene.wgl.api.GenomeProfile;

public interface ProfileRegistryGateway {
    void register(GenomeProfile<?> profile);
    void replace(GenomeProfile<?> profile);
    void unregisterOwner();
}
