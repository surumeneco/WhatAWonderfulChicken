package co.surumene.whatawonderfulchicken.breeding;

import co.surumene.whatawonderfulchicken.data.StatType;
import co.surumene.whatawonderfulchicken.genome.WonderfulChickenDecodedPhenotype;
import co.surumene.whatawonderfulchicken.genome.WonderfulChickenGenomeProfile;
import co.surumene.whatawonderfulchicken.data.WonderfulChickenData;
import co.surumene.wgl.api.*;

import java.util.*;

final class WonderfulChickenInheritanceAnalyzer {
    private static final double EPS = 1.0e-12;
    private static final int START_MOTIF_BITS = 16;

    private final GenomeEngine engine;
    private final WonderfulChickenGenomeProfile profile;

    WonderfulChickenInheritanceAnalyzer(
            GenomeEngine engine,
            WonderfulChickenGenomeProfile profile) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    ParentAnalysis analyze(WonderfulChickenData individual) {
        Objects.requireNonNull(individual, "individual");
        DecodeResult<WonderfulChickenDecodedPhenotype> decoded =
                engine.decode(profile, individual.genome());
        return new ParentAnalysis(individual.genome(), decoded);
    }

    final class ParentAnalysis {
        private final DiploidGenome genome;
        private final DecodeResult<WonderfulChickenDecodedPhenotype> decoded;
        private final EnumMap<StatType, List<InheritanceBlock>> normal =
                new EnumMap<>(StatType.class);
        private final EnumMap<StatType, List<InheritanceBlock>> extraordinary =
                new EnumMap<>(StatType.class);

        private ParentAnalysis(
                DiploidGenome genome,
                DecodeResult<WonderfulChickenDecodedPhenotype> decoded) {
            this.genome = genome;
            this.decoded = decoded;
        }

        double baseStatType(StatType stat) {
            return decoded.phenotype().baseAbilities().get(stat);
        }

        List<InheritanceBlock> normalBlocks(StatType stat) {
            return normal.computeIfAbsent(
                    stat,
                    key -> blocksFor(key, 0x00, false));
        }

        List<InheritanceBlock> extraordinaryBlocks(StatType stat) {
            return extraordinary.computeIfAbsent(
                    stat,
                    key -> blocksFor(key, 0x07, true));
        }

        private List<InheritanceBlock> blocksFor(
                StatType stat,
                int addressType,
                boolean extraordinaryContribution) {
            GenomeAddress targetAddress =
                    new GenomeAddress(addressType, stat.targetId());

            List<DecodedGene> genes = decoded.decodedGenome().physicalGenes().stream()
                    .filter(DecodedGene::addressValid)
                    .filter(gene -> gene.chromosomeIndex() >= 0 && gene.haplotypeIndex() >= 0)
                    .sorted(Comparator
                            .comparingInt(DecodedGene::chromosomeIndex)
                            .thenComparingInt(DecodedGene::haplotypeIndex)
                            .thenComparingInt(DecodedGene::startBit))
                    .toList();

            List<BlockRange> candidates = new ArrayList<>();
            for (DecodedGene direct : genes) {
                if (!targetAddress.equals(direct.address())) {
                    continue;
                }
                candidates.add(expandLocalBlock(direct, genes));
            }

            List<BlockRange> merged = merge(candidates);
            double fullValue = extraordinaryContribution
                    ? decoded.phenotype().extraordinaryContributions().get(stat)
                    : decoded.phenotype().baseAbilities().get(stat);

            List<InheritanceBlock> result = new ArrayList<>();
            for (BlockRange range : merged) {
                DiploidGenome masked = mask(genome, range);
                WonderfulChickenDecodedPhenotype without =
                        engine.decode(profile, masked).phenotype();
                double maskedValue = extraordinaryContribution
                        ? without.extraordinaryContributions().get(stat)
                        : without.baseAbilities().get(stat);
                double delta = fullValue - maskedValue;
                if (delta > EPS) {
                    result.add(new InheritanceBlock(
                            stat,
                            range.chromosomeIndex(),
                            range.haplotypeIndex(),
                            range.startBit(),
                            range.endBitExclusive(),
                            delta));
                }
            }

            result.sort(Comparator
                    .comparingInt(InheritanceBlock::chromosomeIndex)
                    .thenComparingInt(InheritanceBlock::haplotypeIndex)
                    .thenComparingInt(InheritanceBlock::startBit));
            return List.copyOf(result);
        }
    }

    private static BlockRange expandLocalBlock(
            DecodedGene direct,
            List<DecodedGene> allGenes) {
        int start = direct.startBit();
        int end = direct.endBitExclusive();

        List<DecodedGene> lane = allGenes.stream()
                .filter(gene -> gene.chromosomeIndex() == direct.chromosomeIndex())
                .filter(gene -> gene.haplotypeIndex() == direct.haplotypeIndex())
                .toList();

        for (DecodedGene gene : lane) {
            if (!isCis(gene)) {
                continue;
            }
            int radius = 32 * (((gene.rawEffectByte() >>> 4) & 0x0F) + 1);
            if (Math.abs(startMotifPosition(gene) - startMotifPosition(direct)) <= radius) {
                start = Math.min(start, gene.startBit());
                end = Math.max(end, gene.endBitExclusive());
            }
        }

        int index = lane.indexOf(direct);
        if (index >= 0) {
            if (index + 1 < lane.size()) {
                DecodedGene after = lane.get(index + 1);
                if (isRelay(after)
                        && after.orientation() == GeneOrientation.FORWARD) {
                    start = Math.min(start, after.startBit());
                    end = Math.max(end, after.endBitExclusive());
                }
            }
            if (index > 0) {
                DecodedGene before = lane.get(index - 1);
                if (isRelay(before)
                        && before.orientation() == GeneOrientation.REVERSE) {
                    start = Math.min(start, before.startBit());
                    end = Math.max(end, before.endBitExclusive());
                }
            }
        }

        return new BlockRange(
                direct.chromosomeIndex(),
                direct.haplotypeIndex(),
                start,
                end);
    }

    private static int startMotifPosition(DecodedGene gene) {
        return gene.orientation() == GeneOrientation.FORWARD
                ? gene.startBit()
                : gene.endBitExclusive() - START_MOTIF_BITS;
    }

    private static boolean isRelay(DecodedGene gene) {
        return gene.addressValid()
                && gene.address() != null
                && gene.address().type() == 0x08
                && gene.address().target() == 0x06;
    }

    private static boolean isCis(DecodedGene gene) {
        return gene.addressValid()
                && gene.address() != null
                && gene.address().type() == 0x08
                && (gene.address().target() == 0x00 || gene.address().target() == 0x01);
    }

    private static List<BlockRange> merge(List<BlockRange> ranges) {
        if (ranges.isEmpty()) {
            return List.of();
        }
        List<BlockRange> sorted = ranges.stream()
                .sorted(Comparator
                        .comparingInt(BlockRange::chromosomeIndex)
                        .thenComparingInt(BlockRange::haplotypeIndex)
                        .thenComparingInt(BlockRange::startBit))
                .toList();

        List<BlockRange> merged = new ArrayList<>();
        BlockRange current = sorted.getFirst();
        for (int i = 1; i < sorted.size(); i++) {
            BlockRange next = sorted.get(i);
            if (current.chromosomeIndex() == next.chromosomeIndex()
                    && current.haplotypeIndex() == next.haplotypeIndex()
                    && next.startBit() <= current.endBitExclusive()) {
                current = new BlockRange(
                        current.chromosomeIndex(),
                        current.haplotypeIndex(),
                        current.startBit(),
                        Math.max(current.endBitExclusive(), next.endBitExclusive()));
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return List.copyOf(merged);
    }

    private static DiploidGenome mask(
            DiploidGenome genome,
            BlockRange range) {
        List<ChromosomePair> pairs = new ArrayList<>(genome.chromosomePairs());
        ChromosomePair pair = pairs.get(range.chromosomeIndex());
        int length = range.endBitExclusive() - range.startBit();
        BitSequence replacement = BitSequence.ofPacked(
                new byte[BitSequence.packedLength(length)],
                length);

        BitSequence a = pair.haplotypeA();
        BitSequence b = pair.haplotypeB();
        if (range.haplotypeIndex() == 0) {
            a = a.replace(range.startBit(), range.endBitExclusive(), replacement);
        } else {
            b = b.replace(range.startBit(), range.endBitExclusive(), replacement);
        }
        pairs.set(range.chromosomeIndex(), new ChromosomePair(a, b));
        return new DiploidGenome(genome.genomeFormatVersion(), pairs);
    }

    record InheritanceBlock(
            StatType stat,
            int chromosomeIndex,
            int haplotypeIndex,
            int startBit,
            int endBitExclusive,
            double delta) {

        InheritanceBlock {
            Objects.requireNonNull(stat, "stat");
            if (chromosomeIndex < 0) throw new IllegalArgumentException("chromosomeIndex must be >= 0");
            if (haplotypeIndex < 0 || haplotypeIndex > 1) {
                throw new IllegalArgumentException("haplotypeIndex must be 0 or 1");
            }
            if (startBit < 0 || endBitExclusive <= startBit) {
                throw new IllegalArgumentException("invalid block range");
            }
            if (!Double.isFinite(delta) || delta <= 0.0) {
                throw new IllegalArgumentException("delta must be finite and > 0");
            }
        }

        InheritanceConstraint hardConstraint() {
            return InheritanceConstraint.hard(
                    chromosomeIndex, haplotypeIndex, startBit, endBitExclusive);
        }

        InheritanceConstraint softConstraint(
                double retentionProbstat,
                double crossoverWeightMultiplier) {
            return InheritanceConstraint.soft(
                    chromosomeIndex,
                    haplotypeIndex,
                    startBit,
                    endBitExclusive,
                    retentionProbstat,
                    crossoverWeightMultiplier);
        }
    }

    private record BlockRange(
            int chromosomeIndex,
            int haplotypeIndex,
            int startBit,
            int endBitExclusive) {}
}
