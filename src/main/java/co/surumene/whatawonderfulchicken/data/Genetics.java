package co.surumene.whatawonderfulchicken.data;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/** Two independent, unordered allele pairs: nature (P1-P5), trait (T1-T5). */
public record Genetics(int natureA, int natureB, int traitA, int traitB) {
    public Genetics {
        if (!valid(natureA) || !valid(natureB) || !valid(traitA) || !valid(traitB))
            throw new IllegalArgumentException("Gene index must be 0..4");
        if (natureA > natureB) { int other = natureA; natureA = natureB; natureB = other; }
        if (traitA > traitB) { int other = traitA; traitA = traitB; traitB = other; }
    }

    private static boolean valid(int gene) { return gene >= 0 && gene < 5; }

    public Nature nature() { return Nature.fromGenes(natureA, natureB); }
    public Trait trait() { return Trait.fromGenes(traitA, traitB); }

    public byte[] toBytes() { return new byte[]{(byte) natureA, (byte) natureB, (byte) traitA, (byte) traitB}; }

    public static Genetics fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length != 4) return null;
        try { return new Genetics(bytes[0], bytes[1], bytes[2], bytes[3]); }
        catch (IllegalArgumentException ex) { return null; }
    }

    public static Genetics random() { return random(ThreadLocalRandom.current()); }

    public static Genetics random(RandomGenerator random) {
        return new Genetics(random.nextInt(5), random.nextInt(5), random.nextInt(5), random.nextInt(5));
    }

    /** Exactly one allele from each parent per pair. Mutation replaces at most one of the four alleles. */
    public static Genetics breed(Genetics a, Genetics b, double mutationRate, RandomGenerator random) {
        if (a == null || b == null) throw new IllegalArgumentException("Both parent genotypes required");
        int[] factors = {
                random.nextBoolean() ? a.natureA : a.natureB,
                random.nextBoolean() ? b.natureA : b.natureB,
                random.nextBoolean() ? a.traitA : a.traitB,
                random.nextBoolean() ? b.traitA : b.traitB
        };
        if (random.nextDouble() < mutationRate) factors[random.nextInt(4)] = random.nextInt(5);
        return new Genetics(factors[0], factors[1], factors[2], factors[3]);
    }
}
