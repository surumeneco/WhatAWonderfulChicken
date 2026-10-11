package co.surumene.whatawonderfulchicken.data;

import java.util.List;

/**
 * Birth-time ancestry metadata. Legacy Genetics is retained only for
 * backwards compatibility and is not authoritative once Genome is present.
 */
public record AncestorSnapshot(
        String name, int generation, String bloodlineId, Genetics genetics,
        Nature personality, List<Trait> expressedTraits,
        boolean divineLineageExpressed) {

    public AncestorSnapshot {
        expressedTraits = List.copyOf(expressedTraits == null ? List.of() : expressedTraits);
    }

    public AncestorSnapshot(String name, int generation, String bloodlineId, Genetics genetics) {
        this(name, generation, bloodlineId, genetics, null, List.of(), false);
    }

    public AncestorSnapshot(String name, int generation, String bloodlineId) {
        this(name, generation, bloodlineId, null, null, List.of(), false);
    }

    public AncestorSnapshot(String name, int generation, String bloodlineId,
                            Genetics genetics, Nature personality, List<Trait> traits) {
        this(name, generation, bloodlineId, genetics, personality, traits, false);
    }

    public AncestorSnapshot withBloodlineId(String marker) {
        return new AncestorSnapshot(name, generation, marker, genetics, personality, expressedTraits, divineLineageExpressed);
    }

    public AncestorSnapshot withPhenotype(Nature nature, List<Trait> traits) {
        return withPhenotype(nature, traits, false);
    }

    public AncestorSnapshot withPhenotype(Nature nature, List<Trait> traits, boolean divine) {
        return new AncestorSnapshot(name, generation, bloodlineId, genetics, nature, traits, divine);
    }

    public AncestorSnapshot withGenetics(Genetics genes) {
        return new AncestorSnapshot(name, generation, bloodlineId,
                genes, personality, expressedTraits, divineLineageExpressed);
    }
}
