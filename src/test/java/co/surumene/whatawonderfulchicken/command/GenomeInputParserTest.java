package co.surumene.whatawonderfulchicken.command;

import co.surumene.wgl.api.BitSequence;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class GenomeInputParserTest {
    @Test
    void splitsWholeHaplotypeByDefaultRatiosWithoutBitLoss() {
        List<BitSequence> chromosomes = GenomeInputParser.parseHaplotype(
                GenomeInputParser.Format.BITS,
                "111111111111111111111111111111111111111",
                null);
        assertEquals(List.of(9, 8, 7, 6, 5, 4),
                chromosomes.stream().map(BitSequence::bitLength).toList());
        assertEquals(39, chromosomes.stream().mapToInt(BitSequence::bitLength).sum());
    }

    @Test
    void explicitLeadingChromosomesLeaveRemainderForRemainingRatio() {
        List<BitSequence> chromosomes = GenomeInputParser.parseHaplotype(
                GenomeInputParser.Format.BITS,
                "1,00,1111111111111111111111",
                null);
        assertEquals(1, chromosomes.get(0).bitLength());
        assertEquals(2, chromosomes.get(1).bitLength());
        assertEquals(22, chromosomes.subList(2, 6).stream()
                .mapToInt(BitSequence::bitLength).sum());
    }

    @Test
    void textRestoresEscapedCommaAfterBoundaryParsing() {
        List<BitSequence> chromosomes = GenomeInputParser.parseHaplotype(
                GenomeInputParser.Format.TEXT,
                "a\\,b,c,d,e,f,g",
                null);
        assertEquals(
                BitSequence.ofPacked("a,b".getBytes(StandardCharsets.UTF_8), 24),
                chromosomes.get(0));
        assertEquals(6, chromosomes.size());
    }

    @Test
    void ignoresSeventhAndLaterChromosomeSections() {
        List<BitSequence> chromosomes = GenomeInputParser.parseHaplotype(
                GenomeInputParser.Format.TEXT,
                "a,b,c,d,e,f,ignored,also-ignored",
                null);
        assertEquals(6, chromosomes.size());
        assertEquals("f", new String(chromosomes.get(5).packedBits(), StandardCharsets.UTF_8));
    }

    @Test
    void hexAndDnaUseWglCodecAndBitsAllowsOddLength() {
        var codec = GenomeInputParser.defaultSequenceCodecForTest();
        var oddBits = GenomeInputParser.parseHaplotype(
                GenomeInputParser.Format.BITS, "101", codec);
        assertEquals(6, oddBits.size());
        assertEquals(3, oddBits.stream()
                .mapToInt(BitSequence::bitLength)
                .sum());
        assertEquals("1111", GenomeInputParser.parseHaplotype(
                GenomeInputParser.Format.HEX, "f,,,,,", codec).get(0).toBitString());
        assertEquals("00011110", GenomeInputParser.parseHaplotype(
                GenomeInputParser.Format.DNA, "TCAG,,,,,", codec).get(0).toBitString());
    }

    @Test
    void quotedCommandArgumentsPreserveSpacesAndEscapes() {
        assertEquals(
                List.of("hello world", "other value"),
                GenomeCommandArguments.parse("\"hello world\" \"other value\""));
        assertEquals(
                List.of("plain"),
                GenomeCommandArguments.parse("plain"));
        assertEquals(
                List.of("a\\,b"),
                GenomeCommandArguments.parse("\"a\\,b\""));
    }

    @Test
    void omittedHaplotypeBProducesCompleteHomozygousGenome() {
        var genome = GenomeAdminCodec.parseGenome(
                GenomeInputParser.Format.BITS,
                "101010101010101010101010101010101010101",
                null,
                GenomeInputParser.defaultSequenceCodecForTest());

        assertEquals(6, genome.chromosomePairCount());
        assertTrue(genome.chromosomePairs().stream()
                .allMatch(pair -> pair.haplotypeA().equals(pair.haplotypeB())));
    }

    @Test
    void rawBitsOutputCanRecreateTheSamePhysicalGenome() {
        var original = GenomeAdminCodec.parseGenome(
                GenomeInputParser.Format.BITS,
                "1,01,101,0101,11111,000000",
                "0,10,010,1010,00000,111111",
                GenomeInputParser.defaultSequenceCodecForTest());
        var raw = GenomeAdminCodec.rawBits(original);
        var recreated = GenomeAdminCodec.parseGenome(
                GenomeInputParser.Format.BITS,
                raw.haplotypeA(),
                raw.haplotypeB(),
                GenomeInputParser.defaultSequenceCodecForTest());

        assertEquals(original, recreated);
    }

    @Test
    void rejectsInvalidFormatAndMoreThanTwoHaplotypes() {
        assertThrows(IllegalArgumentException.class,
                () -> GenomeInputParser.Format.parse("raw"));
        assertThrows(IllegalArgumentException.class,
                () -> GenomeCommandArguments.parse("a b c"));
    }
}
