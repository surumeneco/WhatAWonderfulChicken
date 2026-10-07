package co.surumene.whatawonderfulchicken.genome;

import co.surumene.whatawonderfulchicken.data.StatType;
import co.surumene.whatawonderfulchicken.founder.FounderOrigin;
import co.surumene.whatawonderfulchicken.founder.WonderfulChickenSynthesisTarget;
import co.surumene.wgl.api.BackboneDefinition;
import co.surumene.wgl.api.BitSequence;
import co.surumene.wgl.api.DirectContributionModel;
import co.surumene.wgl.api.GeneSequenceCodec;
import co.surumene.wgl.api.GenomeAddress;
import co.surumene.wgl.api.GenomeRandom;
import co.surumene.wgl.api.SynthesisBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

final class WonderfulChickenSynthesisMaterial {
    private static final GenomeAddress DIVINE = new GenomeAddress(0x05, 0x00);
    private static final GenomeAddress RELAY = new GenomeAddress(0x08, 0x06);

    private final WonderfulChickenSynthesisSettings settings;
    private final BackboneDefinition backbone;
    private final GeneSequenceCodec codec;
    private final Function<GenomeAddress, DirectContributionModel> modelFor;

    WonderfulChickenSynthesisMaterial(
            WonderfulChickenSynthesisSettings settings,
            BackboneDefinition backbone,
            GeneSequenceCodec codec,
            Function<GenomeAddress, DirectContributionModel> modelFor) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.backbone = Objects.requireNonNull(backbone, "backbone");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.modelFor = Objects.requireNonNull(modelFor, "modelFor");
    }

    List<SynthesisBlock> blocks(WonderfulChickenSynthesisTarget target, GenomeRandom random) {
        List<SynthesisBlock> blocks = new ArrayList<>();
        addInjuryBlocks(blocks, random);
        addDivineBlocks(blocks, target, random);
        addExtraordinaryBlocks(blocks, target, random);
        addRegulationBlocks(blocks, target, random);
        return List.copyOf(blocks);
    }

    private void addInjuryBlocks(List<SynthesisBlock> out, GenomeRandom random) {
        var injury = settings.injury();
        int blockCount = Math.min(injury.blocksMax(), samplePoisson(injury.blocksLambda(), random));
        for (int i = 0; i < blockCount; i++) {
            StatType stat = StatType.values()[random.nextInt(StatType.values().length)];
            GenomeAddress address = new GenomeAddress(0x02, stat.targetId());
            double load = truncatedNormal(
                    injury.loadMean(), injury.loadStandardDeviation(),
                    injury.loadMin(), injury.loadMax(), random);
            double onset = truncatedNormal(
                    injury.onsetMean(), injury.onsetStandardDeviation(), 0.0, 1.0, random);
            double severity = truncatedNormal(
                    injury.severityMean(), injury.severityStandardDeviation(), 0.0, 1.0, random);
            BitSequence extension = BitSequence.fromLong(quantizeByte(onset), 8)
                    .concat(BitSequence.fromLong(quantizeByte(severity), 8));
            int magnitude = bestMagnitudeForAggregate(address, load, injury.genesPerBlock());
            BitSequence block = repeatedDirectGenes(
                    address, magnitude, injury.genesPerBlock(), extension);
            int chromosome = weightedChromosome(random);
            if (random.nextDouble() < injury.bilateralProbability()) {
                out.add(SynthesisBlock.fixed(block, chromosome, 0));
                out.add(SynthesisBlock.fixed(block, chromosome, 1));
            } else {
                out.add(SynthesisBlock.fixed(block, chromosome, random.nextBoolean() ? 1 : 0));
            }
        }
    }

    private void addDivineBlocks(
            List<SynthesisBlock> out,
            WonderfulChickenSynthesisTarget target,
            GenomeRandom random) {
        if (target.origin() != FounderOrigin.CHICKEN_TRAP) return;
        var divine = settings.divine();
        if (random.nextDouble() >= divine.supplyProbability()) return;

        int count = triangularInt(divine.genesMin(), divine.genesCenter(), divine.genesMax(), random);
        int majorHaplotype = random.nextBoolean() ? 1 : 0;
        List<BitSequence> hap0 = new ArrayList<>();
        List<BitSequence> hap1 = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            double desiredD = divine.geneDMin()
                    + (divine.geneDMax() - divine.geneDMin()) * random.nextDouble();
            int magnitude = modelFor.apply(DIVINE).closestMagnitudeCode(DIVINE, desiredD, 15);
            BitSequence gene = codec.encodeDirectGene(
                    DIVINE, false, magnitude, 15, BitSequence.empty());
            int haplotype = random.nextDouble() < divine.majorHaplotypeProbability()
                    ? majorHaplotype : 1 - majorHaplotype;
            (haplotype == 0 ? hap0 : hap1).add(gene);
        }
        addChunkedBlocks(out, hap0, 0, random);
        addChunkedBlocks(out, hap1, 1, random);
    }

    private void addExtraordinaryBlocks(
            List<SynthesisBlock> out,
            WonderfulChickenSynthesisTarget target,
            GenomeRandom random) {
        var extraordinary = settings.extraordinary();
        for (StatType stat : StatType.values()) {
            double e = target.extraordinaryTargets().get(stat);
            if (!(e > 0.0)) continue;

            GenomeAddress address = new GenomeAddress(0x07, stat.targetId());
            double rawTarget = Math.min(1.0, 2.0 * e);
            GeneCountPlan plan = bestExtraordinaryGeneCount(address, rawTarget, extraordinary);
            int blockCount = extraordinary.blocksPerTargetMin()
                    + random.nextInt(extraordinary.blocksPerTargetMax()
                    - extraordinary.blocksPerTargetMin() + 1);

            int blocks0 = 1;
            int blocks1 = 1;
            if (blockCount > 2) {
                if (random.nextBoolean()) blocks0++; else blocks1++;
            }
            addPartitionedBlocks(out,
                    encodedGenes(address, plan.haplotype0Magnitudes()), blocks0, 0, random);
            addPartitionedBlocks(out,
                    encodedGenes(address, plan.haplotype1Magnitudes()), blocks1, 1, random);
        }
    }

    private void addRegulationBlocks(
            List<SynthesisBlock> out,
            WonderfulChickenSynthesisTarget target,
            GenomeRandom random) {
        List<GenomeAddress> targetAddresses = target.continuousTargets().keySet().stream()
                .sorted().toList();
        if (targetAddresses.isEmpty()) return;

        for (int haplotype = 0; haplotype <= 1; haplotype++) {
            int total = triangularInt(
                    settings.regulationGenesMin(),
                    settings.regulationGenesCenter(),
                    settings.regulationGenesMax(),
                    random);
            double attachmentRatio = settings.relay().attachmentMinRatio()
                    + (settings.relay().attachmentMaxRatio()
                    - settings.relay().attachmentMinRatio()) * random.nextDouble();
            int directCenter = (settings.directGenesSoftMin() + settings.directGenesSoftMax()) / 2;
            int relayCount = Math.min(total,
                    Math.max(1, (int) StrictMath.round(directCenter * attachmentRatio)));

            for (int i = 0; i < relayCount; i++) {
                GenomeAddress sourceAddress = targetAddresses.get(random.nextInt(targetAddresses.size()));
                GenomeAddress secondaryAddress = targetAddresses.get(random.nextInt(targetAddresses.size()));
                BitSequence source = codec.encodeDirectGene(
                        sourceAddress, false, 1, 15, BitSequence.empty());

                double relayRatio = settings.relay().normalSecondaryMinRatio()
                        + (settings.relay().normalSecondaryMaxRatio()
                        - settings.relay().normalSecondaryMinRatio()) * random.nextDouble();
                int ratioCode = Math.max(1, Math.min(127,
                        (int) StrictMath.round(relayRatio * 127.0)));
                boolean negative = random.nextDouble() >= settings.relay().positiveRatio();
                BitSequence relay = codec.encodeDirectGene(
                        RELAY,
                        negative,
                        ratioCode,
                        15,
                        codec.encodeAddressHeader(secondaryAddress));
                out.add(SynthesisBlock.fixed(
                        source.concat(relay), weightedChromosome(random), haplotype));
            }

            for (int i = relayCount; i < total; i++) {
                int selector = random.nextInt(11);
                int targetId = switch (selector) {
                    case 0, 1 -> 0x00;
                    case 2, 3 -> 0x01;
                    case 4 -> 0x04;
                    case 5 -> 0x05;
                    case 6 -> 0x07;
                    case 7 -> 0x08;
                    case 8 -> 0x09;
                    case 9 -> 0x0A;
                    default -> 0x0B;
                };
                GenomeAddress address = new GenomeAddress(0x08, targetId);
                int rawEffect;
                int expression;
                if (targetId == 0x00 || targetId == 0x01) {
                    int radiusCode = random.nextInt(4);
                    int strength = 1 + random.nextInt(4);
                    rawEffect = (radiusCode << 4) | strength;
                    expression = 8 + random.nextInt(8);
                } else {
                    rawEffect = 1 + random.nextInt(255);
                    expression = 1 + random.nextInt(15);
                }
                out.add(SynthesisBlock.fixed(
                        codec.encodeRawGene(address, rawEffect, expression, BitSequence.empty()),
                        weightedChromosome(random),
                        haplotype));
            }
        }
    }

    private GeneCountPlan bestExtraordinaryGeneCount(
            GenomeAddress address,
            double target,
            WonderfulChickenSynthesisSettings.Extraordinary config) {
        GeneCountPlan best = null;
        DirectContributionModel model = modelFor.apply(address);
        for (int a = config.genesPerTargetMin(); a <= config.genesPerTargetMax(); a++) {
            for (int b = config.genesPerTargetMin(); b <= config.genesPerTargetMax(); b++) {
                int total = a + b;
                for (int common = 0; common <= 127; common++) {
                    double commonU = saturation(model, address, common);
                    double commonSurvival = StrictMath.pow(1.0 - commonU, Math.max(0, total - 1));
                    for (int tail = 0; tail <= 127; tail++) {
                        double tailU = saturation(model, address, tail);
                        double achieved = 1.0 - commonSurvival * (1.0 - tailU);
                        double error = StrictMath.abs(achieved - target);
                        if (best == null || error < best.error()) {
                            List<Integer> all = new ArrayList<>(total);
                            for (int i = 0; i < total - 1; i++) all.add(common);
                            all.add(tail);
                            best = new GeneCountPlan(
                                    List.copyOf(all.subList(0, a)),
                                    List.copyOf(all.subList(a, total)),
                                    error);
                            if (error <= 1.0e-12) return best;
                        }
                    }
                }
            }
        }
        return Objects.requireNonNull(best);
    }

    private static double saturation(
            DirectContributionModel model,
            GenomeAddress address,
            int magnitude) {
        double d = model.baseEffect(address, false, magnitude, 15);
        return model.saturation(address, StrictMath.abs(d));
    }

    private int bestMagnitudeForAggregate(GenomeAddress address, double target, int count) {
        DirectContributionModel model = modelFor.apply(address);
        int best = 0;
        double bestError = StrictMath.abs(target);
        for (int magnitude = 0; magnitude <= 127; magnitude++) {
            double d = model.baseEffect(address, false, magnitude, 15);
            double u = model.saturation(address, StrictMath.abs(d));
            double achieved = 1.0 - StrictMath.pow(1.0 - u, count);
            double error = StrictMath.abs(achieved - target);
            if (error < bestError) {
                bestError = error;
                best = magnitude;
            }
        }
        return best;
    }

    private BitSequence repeatedDirectGenes(
            GenomeAddress address,
            int magnitude,
            int count,
            BitSequence extension) {
        BitSequence bits = BitSequence.empty();
        for (int i = 0; i < count; i++) {
            bits = bits.concat(codec.encodeDirectGene(address, false, magnitude, 15, extension));
        }
        return bits;
    }

    private List<BitSequence> encodedGenes(GenomeAddress address, List<Integer> magnitudes) {
        List<BitSequence> genes = new ArrayList<>(magnitudes.size());
        for (int magnitude : magnitudes) {
            genes.add(codec.encodeDirectGene(address, false, magnitude, 15, BitSequence.empty()));
        }
        return genes;
    }

    private void addChunkedBlocks(
            List<SynthesisBlock> out,
            List<BitSequence> genes,
            int haplotype,
            GenomeRandom random) {
        int cursor = 0;
        while (cursor < genes.size()) {
            int remaining = genes.size() - cursor;
            int size = Math.min(remaining, 2 + random.nextInt(2));
            if (remaining - size == 1 && size > 2) size--;
            out.add(SynthesisBlock.fixed(
                    concat(genes.subList(cursor, cursor + size)),
                    weightedChromosome(random),
                    haplotype));
            cursor += size;
        }
    }

    private void addPartitionedBlocks(
            List<SynthesisBlock> out,
            List<BitSequence> genes,
            int blockCount,
            int haplotype,
            GenomeRandom random) {
        int remainingGenes = genes.size();
        int cursor = 0;
        for (int blockIndex = 0; blockIndex < blockCount; blockIndex++) {
            int remainingBlocks = blockCount - blockIndex;
            int size = remainingGenes / remainingBlocks;
            if (remainingGenes % remainingBlocks != 0) size++;
            out.add(SynthesisBlock.fixed(
                    concat(genes.subList(cursor, cursor + size)),
                    weightedChromosome(random),
                    haplotype));
            cursor += size;
            remainingGenes -= size;
        }
    }

    private static BitSequence concat(List<BitSequence> sequences) {
        BitSequence out = BitSequence.empty();
        for (BitSequence sequence : sequences) out = out.concat(sequence);
        return out;
    }

    private int weightedChromosome(GenomeRandom random) {
        List<Integer> lengths = backbone.baselineChromosomeLengths();
        int total = lengths.stream().mapToInt(Integer::intValue).sum();
        int roll = random.nextInt(total);
        for (int index = 0; index < lengths.size(); index++) {
            int length = lengths.get(index);
            if (roll < length) return index;
            roll -= length;
        }
        return lengths.size() - 1;
    }

    private static int quantizeByte(double value) {
        return Math.max(0, Math.min(255, (int) StrictMath.round(value * 255.0)));
    }

    private static int samplePoisson(double lambda, GenomeRandom random) {
        double limit = StrictMath.exp(-lambda);
        int k = 0;
        double product = 1.0;
        do {
            k++;
            product *= random.nextDouble();
        } while (product > limit);
        return k - 1;
    }

    private static double truncatedNormal(
            double mean, double standardDeviation, double min, double max, GenomeRandom random) {
        for (;;) {
            double u1 = random.nextDouble();
            double u2 = random.nextDouble();
            if (!(u1 > 0.0)) continue;
            double z = StrictMath.sqrt(-2.0 * StrictMath.log(u1))
                    * StrictMath.cos(2.0 * StrictMath.PI * u2);
            double value = mean + standardDeviation * z;
            if (value >= min && value <= max) return value;
        }
    }

    private static int triangularInt(int min, int mode, int max, GenomeRandom random) {
        if (min == max) return min;
        double u = random.nextDouble();
        double split = (mode - min) / (double) (max - min);
        double value = u < split
                ? min + StrictMath.sqrt(u * (max - min) * (mode - min))
                : max - StrictMath.sqrt((1.0 - u) * (max - min) * (max - mode));
        return Math.max(min, Math.min(max, (int) StrictMath.round(value)));
    }

    private record GeneCountPlan(
            List<Integer> haplotype0Magnitudes,
            List<Integer> haplotype1Magnitudes,
            double error) {}
}
