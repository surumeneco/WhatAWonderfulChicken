package co.surumene.whatawonderfulchicken.data;

import java.util.List;

/**
 * Birth-time ancestry metadata. Legacy Genetics is retained only for
 * backwards compatibility and is not authoritative once Genome is present.
 */
public record AncestorSnapshot(
        String name, int generation, String bloodlineId, Genetics genetics,
        Nature personality, List<Trait> expressedTraits) {

    public AncestorSnapshot {
        expressedTraits = List.copyOf(expressedTraits == null ? List.of() : expressedTraits);
    }

    public AncestorSnapshot(String name, int generation, String bloodlineId, Genetics genetics) {
        this(name, generation, bloodlineId, genetics, null, List.of());
    }

    public AncestorSnapshot(String name, int generation, String bloodlineId) {
        this(name, generation, bloodlineId, null, null, List.of());
    }

    public AncestorSnapshot withBloodlineId(String marker) {
        return new AncestorSnapshot(name, generation, marker, genetics, personality, expressedTraits);
    }

    public AncestorSnapshot withPhenotype(Nature nature, List<Trait> traits) {
        return new AncestorSnapshot(name, generation, bloodlineId, genetics, nature, traits);
    }

    public AncestorSnapshot withGenetics(Genetics genes) {
        return new AncestorSnapshot(name, generation, bloodlineId,
                genes, personality, expressedTraits);
    }
}
