package co.surumene.whatawonderfulchicken.command;

import co.surumene.wgl.api.BitSequence;
import co.surumene.wgl.api.GenomeSequenceCodec;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class GenomeInputParser {
    private static final int[] CHROMOSOME_RATIOS = {9, 8, 7, 6, 5, 4};

    private GenomeInputParser() {}

    public enum Format {
        TEXT,
        BITS,
        HEX,
        DNA;

        public static Format parse(String raw) {
            Objects.requireNonNull(raw, "raw");
            try {
                return valueOf(raw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException(
                        "format must be one of text, bits, hex, dna");
            }
        }
    }

    public static List<BitSequence> parseHaplotype(
            Format format,
            String raw,
            GenomeSequenceCodec codec) {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(raw, "raw");

        List<String> sections = splitSections(raw);
        int explicit = Math.max(0, Math.min(5, sections.size() - 1));
        List<BitSequence> result = new ArrayList<>(6);

        for (int i = 0; i < explicit; i++) {
            result.add(decode(format, sections.get(i), codec));
        }

        String remainderText = sections.get(explicit);
        BitSequence remainder = decode(format, remainderText, codec);
        int remainingCount = 6 - explicit;
        if (remainingCount == 1) {
            result.add(remainder);
        } else {
            result.addAll(splitByRatio(
                    remainder,
                    explicit,
                    remainingCount));
        }

        return List.copyOf(result);
    }

    private static List<String> splitSections(String raw) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (escaped) {
                if (c == ',') current.append(',');
                else current.append('\\').append(c);
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == ',') {
                result.add(current.toString());
                current.setLength(0);
                if (result.size() == 6) {
                    throw new IllegalArgumentException(
                            "a haplotype must contain exactly six chromosome sections");
                }
                continue;
            }
            current.append(c);
        }
        if (escaped) current.append('\\');
        result.add(current.toString());
        return List.copyOf(result);
    }

    private static BitSequence decode(
            Format format,
            String value,
            GenomeSequenceCodec codec) {
        return switch (format) {
            case TEXT -> {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                yield BitSequence.ofPacked(bytes, bytes.length * 8);
            }
            case BITS -> BitSequence.fromBits(value);
            case HEX -> requireCodec(codec).decodeHex(value);
            case DNA -> requireCodec(codec).decodeDna(value);
        };
    }

    private static GenomeSequenceCodec requireCodec(
            GenomeSequenceCodec codec) {
        return Objects.requireNonNull(
                codec,
                "WGL GenomeSequenceCodec is required for this format");
    }

    private static List<BitSequence> splitByRatio(
            BitSequence bits,
            int ratioStart,
            int count) {
        int totalWeight = 0;
        for (int i = 0; i < count; i++) {
            totalWeight += CHROMOSOME_RATIOS[ratioStart + i];
        }

        int[] lengths = new int[count];
        int allocated = 0;
        for (int i = 0; i < count; i++) {
            lengths[i] = (int) (((long) bits.bitLength()
                    * CHROMOSOME_RATIOS[ratioStart + i])
                    / totalWeight);
            allocated += lengths[i];
        }
        int leftover = bits.bitLength() - allocated;
        for (int i = 0; i < leftover; i++) {
            lengths[i % count]++;
        }

        List<BitSequence> result = new ArrayList<>(count);
        int offset = 0;
        for (int length : lengths) {
            result.add(bits.slice(offset, offset + length));
            offset += length;
        }
        return result;
    }

    static GenomeSequenceCodec defaultSequenceCodecForTest() {
        return new GenomeSequenceCodec() {
            @Override
            public BitSequence decodeBits(String bits) {
                return BitSequence.fromBits(bits);
            }

            @Override
            public BitSequence decodeHex(String hex) {
                if (hex == null || !hex.matches("(?i)[0-9a-f]*")) {
                    throw new IllegalArgumentException(
                            "hex must contain only 0-9/A-F");
                }
                StringBuilder bits = new StringBuilder(hex.length() * 4);
                for (int i = 0; i < hex.length(); i++) {
                    int value = Character.digit(hex.charAt(i), 16);
                    bits.append(String.format(
                            Locale.ROOT,
                            "%4s",
                            Integer.toBinaryString(value))
                            .replace(' ', '0'));
                }
                return BitSequence.fromBits(bits.toString());
            }

            @Override
            public BitSequence decodeDna(String dna) {
                StringBuilder bits = new StringBuilder(dna.length() * 2);
                for (int i = 0; i < dna.length(); i++) {
                    bits.append(switch (Character.toUpperCase(dna.charAt(i))) {
                        case 'T' -> "00";
                        case 'C' -> "01";
                        case 'G' -> "10";
                        case 'A' -> "11";
                        default -> throw new IllegalArgumentException(
                                "dna must contain only T/C/G/A");
                    });
                }
                return BitSequence.fromBits(bits.toString());
            }

            @Override
            public String encodeBits(BitSequence bits) {
                return bits.toBitString();
            }

            @Override
            public TextView encodeHex(BitSequence bits) {
                throw new UnsupportedOperationException();
            }

            @Override
            public TextView encodeDna(BitSequence bits) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
