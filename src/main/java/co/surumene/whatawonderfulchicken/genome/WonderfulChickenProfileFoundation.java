package co.surumene.whatawonderfulchicken.genome;

import co.surumene.wgl.api.AnchorSeed;
import co.surumene.wgl.api.BackboneDefinition;
import co.surumene.wgl.api.BitSequence;
import co.surumene.wgl.api.ChromosomeTemplate;
import co.surumene.wgl.api.FounderScaffoldTolerance;
import co.surumene.wgl.api.MarkerLocus;
import co.surumene.wgl.api.ProfileDescriptor;
import co.surumene.wgl.api.StandardGenomeSafetyPolicyV1;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class WonderfulChickenProfileFoundation {
    public static final String PROFILE_ID = "wonderful-chicken";
    public static final int PROFILE_VERSION = 1;
    public static final int GENOME_FORMAT_VERSION = 1;

    private static final List<Integer> BASELINE_LENGTHS =
            List.of(9216, 8192, 7168, 6144, 5120, 4096);
    private static final int ANCHOR_INTERVAL = 512;
    private static final int ANCHOR_START = 256;

    // Independent from Wonderful Wolf so normally generated Chicken/Wolf genomes
    // do not accidentally satisfy the physical homology policy.
    private static final long TEMPLATE_SEED = 0x434849434B454E31L;

    private WonderfulChickenProfileFoundation() {}

    public static ProfileDescriptor descriptor(WonderfulChickenGenomeSettings settings) {
        Objects.requireNonNull(settings, "settings");
        return new ProfileDescriptor(PROFILE_ID, PROFILE_VERSION, semanticFingerprint(settings));
    }

    public static BackboneDefinition backbone(WonderfulChickenGenomeSettings settings) {
        Objects.requireNonNull(settings, "settings");
        FounderScaffoldTolerance tolerance = new FounderScaffoldTolerance(
                settings.chromosomeLengthStandardDeviationRatio(),
                settings.chromosomeLengthMinRatio(),
                settings.chromosomeLengthMaxRatio());

        List<ChromosomeTemplate> chromosomes = new ArrayList<>(BASELINE_LENGTHS.size());
        for (int index = 0; index < BASELINE_LENGTHS.size(); index++) {
            int bitLength = BASELINE_LENGTHS.get(index);
            BitSequence template = template(index, bitLength);
            List<AnchorSeed> anchors = anchors(template);
            MarkerLocus markerLocus = markerLocus(anchors, bitLength);
            chromosomes.add(new ChromosomeTemplate(template, anchors, markerLocus, tolerance));
        }

        return new BackboneDefinition(
                PROFILE_ID,
                GENOME_FORMAT_VERSION,
                chromosomes,
                new StandardGenomeSafetyPolicyV1());
    }

    private static byte[] semanticFingerprint(WonderfulChickenGenomeSettings settings) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
            double[] values = {
                    settings.seriousMaxScore(),
                    settings.seriousSpread(),
                    settings.dominantGap(),
                    settings.traitExpressionThreshold(),
                    settings.traitStrongGap(),
                    settings.injuryExpressionThreshold(),
                    settings.injuryOnsetMaxGameDays(),
                    settings.injurySeverityRankMin(),
                    settings.injurySeverityRankMax(),
                    settings.divineMinHaplotypeScore(),
                    settings.divineTotalScore(),
                    settings.chromosomeLengthStandardDeviationRatio(),
                    settings.chromosomeLengthMinRatio(),
                    settings.chromosomeLengthMaxRatio()
            };
            for (double value : values) {
                buffer.clear();
                buffer.putLong(Double.doubleToLongBits(value));
                digest.update(buffer.array());
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static BitSequence template(int chromosomeIndex, int bitLength) {
        byte[] packed = new byte[BitSequence.packedLength(bitLength)];
        long state = TEMPLATE_SEED ^ ((long) chromosomeIndex * 0x9E3779B97F4A7C15L);
        for (int offset = 0; offset < packed.length; offset += Long.BYTES) {
            state += 0x9E3779B97F4A7C15L;
            long value = mix64(state);
            for (int byteIndex = 0; byteIndex < Long.BYTES && offset + byteIndex < packed.length; byteIndex++) {
                packed[offset + byteIndex] =
                        (byte) (value >>> ((Long.BYTES - 1 - byteIndex) * Byte.SIZE));
            }
        }
        return BitSequence.ofPacked(packed, bitLength);
    }

    private static List<AnchorSeed> anchors(BitSequence template) {
        List<AnchorSeed> anchors = new ArrayList<>();
        for (int position = ANCHOR_START;
             position + 48 <= template.bitLength();
             position += ANCHOR_INTERVAL) {
            anchors.add(new AnchorSeed(position, template.slice(position, position + 48)));
        }
        if (anchors.size() < 2) {
            throw new IllegalStateException("backbone chromosome requires at least two anchors");
        }
        return List.copyOf(anchors);
    }

    private static MarkerLocus markerLocus(List<AnchorSeed> anchors, int chromosomeLength) {
        int bestIndex = 0;
        double bestDistance = Double.POSITIVE_INFINITY;
        double chromosomeCenter = chromosomeLength / 2.0;
        for (int index = 0; index < anchors.size() - 1; index++) {
            double firstCenter = anchors.get(index).position() + 24.0;
            double secondCenter = anchors.get(index + 1).position() + 24.0;
            double pairCenter = (firstCenter + secondCenter) / 2.0;
            double distance = Math.abs(pairCenter - chromosomeCenter);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = index;
            }
        }
        return new MarkerLocus(anchors.get(bestIndex), anchors.get(bestIndex + 1));
    }

    private static long mix64(long value) {
        long z = value;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
