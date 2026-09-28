package co.surumene.whatawonderfulchicken.data;

public record AncestorSnapshot(String name, int generation, String bloodlineId, Genetics genetics) {
    public AncestorSnapshot(String name, int generation, String bloodlineId) {
        this(name, generation, bloodlineId, null);
    }

    public AncestorSnapshot withGenetics(Genetics genes) {
        return new AncestorSnapshot(name, generation, bloodlineId, genes);
    }
}
