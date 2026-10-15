package co.surumene.whatawonderfulchicken.command;

import co.surumene.wgl.api.BitSequence;
import co.surumene.wgl.api.ChromosomePair;
import co.surumene.wgl.api.DiploidGenome;
import co.surumene.wgl.api.GenomeSequenceCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class GenomeAdminCodec {
    public static final int GENOME_FORMAT_VERSION = 1;

    private GenomeAdminCodec() {}

    public static DiploidGenome parseGenome(
            GenomeInputParser.Format format,
            String haplotypeA,
            String haplotypeB,
            GenomeSequenceCodec codec) {
        List<BitSequence> a = GenomeInputParser.parseHaplotype(
                format,
                Objects.requireNonNull(haplotypeA, "haplotypeA"),
                codec);
        List<BitSequence> b = haplotypeB == null
                ? a
                : GenomeInputParser.parseHaplotype(
                        format,
                        haplotypeB,
                        codec);
        if (a.size() != 6 || b.size() != 6) {
            throw new IllegalArgumentException(
                    "Wonderful Chicken genome requires exactly six chromosome pairs");
        }

        List<ChromosomePair> pairs = new ArrayList<>(6);
        for (int i = 0; i < 6; i++) {
            pairs.add(new ChromosomePair(a.get(i), b.get(i)));
        }
        return new DiploidGenome(GENOME_FORMAT_VERSION, pairs);
    }

    public static RawBitsView rawBits(DiploidGenome genome) {
        Objects.requireNonNull(genome, "genome");
        String a = genome.chromosomePairs().stream()
                .map(ChromosomePair::haplotypeA)
                .map(BitSequence::toBitString)
                .collect(Collectors.joining(","));
        String b = genome.chromosomePairs().stream()
                .map(ChromosomePair::haplotypeB)
                .map(BitSequence::toBitString)
                .collect(Collectors.joining(","));
        String lengths = genome.chromosomePairs().stream()
                .map(pair -> pair.haplotypeA().bitLength()
                        + "/"
                        + pair.haplotypeB().bitLength())
                .collect(Collectors.joining(","));
        return new RawBitsView(a, b, lengths);
    }

    public record RawBitsView(
            String haplotypeA,
            String haplotypeB,
            String chromosomeLengths) {}
}
