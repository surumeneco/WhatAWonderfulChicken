package co.surumene.whatawonderfulchicken.genome;

public record DivineLineagePhenotype(
        double haplotypeAScore,
        double haplotypeBScore,
        boolean expressed) {

    public DivineLineagePhenotype {
        requireScore(haplotypeAScore, "haplotypeAScore");
        requireScore(haplotypeBScore, "haplotypeBScore");
    }

    public double totalScore() {
        return haplotypeAScore + haplotypeBScore;
    }

    private static void requireScore(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
