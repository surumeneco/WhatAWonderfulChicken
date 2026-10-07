package co.surumene.whatawonderfulchicken.genome;

import co.surumene.whatawonderfulchicken.data.DevelopmentFactor;
import co.surumene.whatawonderfulchicken.data.ExpressedTrait;
import co.surumene.whatawonderfulchicken.data.InjuryPhenotype;
import co.surumene.whatawonderfulchicken.data.Nature;
import co.surumene.whatawonderfulchicken.data.PersonalityFactor;
import co.surumene.whatawonderfulchicken.data.StatType;
import co.surumene.whatawonderfulchicken.data.Trait;
import co.surumene.whatawonderfulchicken.data.TraitStrength;
import co.surumene.whatawonderfulchicken.founder.WonderfulChickenSynthesisTarget;
import co.surumene.wgl.api.AddressAggregate;
import co.surumene.wgl.api.BackboneDefinition;
import co.surumene.wgl.api.DecodedGene;
import co.surumene.wgl.api.DecodedGenome;
import co.surumene.wgl.api.DecodedHomologyBlock;
import co.surumene.wgl.api.DirectContributionModel;
import co.surumene.wgl.api.EffectiveContribution;
import co.surumene.wgl.api.GenomeAddress;
import co.surumene.wgl.api.GenomeProfile;
import co.surumene.wgl.api.GenomeRandom;
import co.surumene.wgl.api.GeneSequenceCodec;
import co.surumene.wgl.api.ProfileDescriptor;
import co.surumene.wgl.api.StandardDirectContributionModel;
import co.surumene.wgl.api.SynthesisAddressPlan;
import co.surumene.wgl.api.SynthesisBlock;
import co.surumene.wgl.api.SynthesisContext;
import co.surumene.wgl.api.SynthesisSafetyPolicy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

public final class WonderfulChickenGenomeProfile implements GenomeProfile<WonderfulChickenDecodedPhenotype> {
    static final double EPS = 1.0e-12;

    private static final DirectContributionModel STANDARD_CONTRIBUTION =
            StandardDirectContributionModel.defaultModel();
    private static final DirectContributionModel EXTRAORDINARY_CONTRIBUTION =
            new StandardDirectContributionModel(
                    StandardDirectContributionModel.DEFAULT_ALPHA,
                    2.0,
                    address -> 2.0);

    private final WonderfulChickenGenomeSettings settings;
    private final WonderfulChickenSynthesisSettings synthesisSettings;
    private final ProfileDescriptor descriptor;
    private final BackboneDefinition backbone;
    private final GeneSequenceCodec geneSequenceCodec;

    public WonderfulChickenGenomeProfile(WonderfulChickenGenomeSettings settings) {
        this(settings, WonderfulChickenSynthesisSettings.defaults(), null);
    }

    public WonderfulChickenGenomeProfile(
            WonderfulChickenGenomeSettings settings,
            GeneSequenceCodec geneSequenceCodec) {
        this(settings, WonderfulChickenSynthesisSettings.defaults(), geneSequenceCodec);
    }

    public WonderfulChickenGenomeProfile(
            WonderfulChickenGenomeSettings settings,
            WonderfulChickenSynthesisSettings synthesisSettings,
            GeneSequenceCodec geneSequenceCodec) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.synthesisSettings = Objects.requireNonNull(synthesisSettings, "synthesisSettings");
        this.descriptor = WonderfulChickenProfileFoundation.descriptor(settings, synthesisSettings);
        this.backbone = WonderfulChickenProfileFoundation.backbone(settings);
        this.geneSequenceCodec = geneSequenceCodec;
    }

    public WonderfulChickenGenomeSettings settings() {
        return settings;
    }

    public WonderfulChickenSynthesisSettings synthesisSettings() {
        return synthesisSettings;
    }

    public BackboneDefinition backbone() {
        return backbone;
    }

    @Override
    public ProfileDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public boolean isDefinedAddress(GenomeAddress address) {
        Objects.requireNonNull(address, "address");
        return switch (address.type()) {
            case 0x00, 0x02, 0x07 -> address.target() < StatType.values().length;
            case 0x01 -> address.target() < DevelopmentFactor.values().length;
            case 0x03 -> address.target() < PersonalityFactor.values().length;
            case 0x04 -> address.target() < Trait.values().length;
            case 0x05 -> address.target() == 0x00;
            default -> false;
        };
    }

    @Override
    public int minimumExtensionBits(GenomeAddress address) {
        Objects.requireNonNull(address, "address");
        return address.type() == 0x02 && address.target() < StatType.values().length ? 16 : 0;
    }

    @Override
    public boolean requiresHomologyContext() {
        return true;
    }

    @Override
    public DirectContributionModel contributionModel(GenomeAddress address) {
        Objects.requireNonNull(address, "address");
        if (!isDefinedAddress(address)) {
            throw new IllegalArgumentException("undefined Wonderful Chicken address: " + address);
        }
        return address.type() == 0x07 ? EXTRAORDINARY_CONTRIBUTION : STANDARD_CONTRIBUTION;
    }

    @Override
    public SynthesisAddressPlan synthesisPlan(
            GenomeAddress address,
            double target,
            SynthesisContext context,
            GenomeRandom random) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(random, "random");
        if (!isDefinedAddress(address)) {
            throw new IllegalArgumentException("undefined Wonderful Chicken address: " + address);
        }

        WonderfulChickenSynthesisSettings.Range range;
        return switch (address.type()) {
            case 0x00 -> boundedPlan(target, synthesisSettings.abilityGenes(), random);
            case 0x01 -> centeredPlan(target, synthesisSettings.developmentGenes(), random);
            case 0x03 -> mixedCenteredPlan(target, synthesisSettings.personalityGenes(), random);
            case 0x04 -> boundedPlan(target, synthesisSettings.traitGenes(), random);
            case 0x07 -> {
                var e = synthesisSettings.extraordinary();
                range = new WonderfulChickenSynthesisSettings.Range(
                        e.genesPerTargetMin(),
                        (e.genesPerTargetMin() + e.genesPerTargetMax()) / 2,
                        e.genesPerTargetMax());
                yield boundedPlan(target, range, random);
            }
            default -> GenomeProfile.super.synthesisPlan(address, target, context, random);
        };
    }

    @Override
    public List<SynthesisBlock> synthesisBlocks(
            co.surumene.wgl.api.SynthesisTarget target,
            SynthesisContext context,
            GenomeRandom random) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(random, "random");
        if (!(target instanceof WonderfulChickenSynthesisTarget chickenTarget)) {
            return List.of();
        }
        if (geneSequenceCodec == null) {
            throw new IllegalStateException(
                    "Wonderful Chicken synthesis requires the WGL GeneSequenceCodec");
        }
        return new WonderfulChickenSynthesisMaterial(
                synthesisSettings,
                backbone,
                geneSequenceCodec,
                this::contributionModel)
                .blocks(chickenTarget, random);
    }

    @Override
    public SynthesisSafetyPolicy synthesisSafetyPolicy() {
        return (metrics, decodedGenome) -> {
            for (int haplotype = 0; haplotype <= 1; haplotype++) {
                int lane = haplotype;
                int candidateCount = metrics.haplotypes().stream()
                        .filter(metric -> metric.haplotypeIndex() == lane)
                        .mapToInt(metric -> metric.geneCandidateCount())
                        .sum();
                long recognizableBits = metrics.haplotypes().stream()
                        .filter(metric -> metric.haplotypeIndex() == lane)
                        .mapToLong(metric -> metric.recognizableBits())
                        .sum();
                long totalBits = metrics.haplotypes().stream()
                        .filter(metric -> metric.haplotypeIndex() == lane)
                        .mapToLong(metric -> metric.bitLength())
                        .sum();
                double recognizableRatio =
                        totalBits == 0L ? 0.0 : recognizableBits / (double) totalBits;

                long direct = decodedGenome.physicalGenes().stream()
                        .filter(DecodedGene::addressValid)
                        .filter(gene -> gene.haplotypeIndex() == lane)
                        .filter(gene -> !gene.regulation())
                        .count();
                long regulation = decodedGenome.physicalGenes().stream()
                        .filter(DecodedGene::addressValid)
                        .filter(gene -> gene.haplotypeIndex() == lane)
                        .filter(DecodedGene::regulation)
                        .count();

                if (candidateCount > synthesisSettings.recognizableGenesHardMax()
                        || direct > synthesisSettings.directGenesHardMax()
                        || regulation > synthesisSettings.regulationGenesHardMax()
                        || recognizableRatio > synthesisSettings.recognizableRegionMaxRatio() + EPS
                        || 1.0 - recognizableRatio < synthesisSettings.noncodingRegionMinRatio() - EPS) {
                    return false;
                }
            }
            return true;
        };
    }

    private SynthesisAddressPlan boundedPlan(
            double target,
            WonderfulChickenSynthesisSettings.Range range,
            GenomeRandom random) {
        if (!Double.isFinite(target) || target < 0.0 || target > 1.0) {
            throw new IllegalArgumentException("target must be finite and in [0,1]");
        }
        if (target == 0.0) return new SynthesisAddressPlan(0.0, 0.0, 0, 0, 0, 0);

        double draw = synthesisSettings.cancellationMin()
                + (synthesisSettings.cancellationMax() - synthesisSettings.cancellationMin())
                * random.nextDouble();
        double cancellation = Math.min(
                draw,
                Math.max(0.0, 1.0 - synthesisSettings.highTargetHeadroom() - target));
        double positive = target / (1.0 - cancellation);

        int totalGenes = 2 * triangularInt(range.min(), range.center(), range.max(), random);
        if (cancellation == 0.0) {
            return new SynthesisAddressPlan(positive, 0.0, totalGenes, totalGenes, 0, 0);
        }
        int negativeGenes = Math.max(1, Math.min(
                totalGenes - 1,
                (int) StrictMath.round(totalGenes * cancellation / (positive + cancellation))));
        int positiveGenes = totalGenes - negativeGenes;
        return new SynthesisAddressPlan(
                positive, cancellation,
                positiveGenes, positiveGenes,
                negativeGenes, negativeGenes);
    }

    private static SynthesisAddressPlan centeredPlan(
            double target,
            WonderfulChickenSynthesisSettings.Range range,
            GenomeRandom random) {
        int totalGenes = 2 * triangularInt(range.min(), range.center(), range.max(), random);
        double delta = 2.0 * target - 1.0;
        if (delta > 0.0) {
            return new SynthesisAddressPlan(delta, 0.0, totalGenes, totalGenes, 0, 0);
        }
        if (delta < 0.0) {
            return new SynthesisAddressPlan(0.0, -delta, 0, 0, totalGenes, totalGenes);
        }
        return new SynthesisAddressPlan(0.0, 0.0, 0, 0, 0, 0);
    }

    private SynthesisAddressPlan mixedCenteredPlan(
            double target,
            WonderfulChickenSynthesisSettings.Range range,
            GenomeRandom random) {
        if (!Double.isFinite(target) || target < 0.0 || target > 1.0) {
            throw new IllegalArgumentException("target must be finite and in [0,1]");
        }
        double delta = 2.0 * target - 1.0;
        double draw = synthesisSettings.personalityCancellationMin()
                + (synthesisSettings.personalityCancellationMax()
                - synthesisSettings.personalityCancellationMin()) * random.nextDouble();
        double cancellation = Math.min(draw, Math.max(0.0, 1.0 - StrictMath.abs(delta)));
        double positive = Math.max(0.0, delta) + cancellation;
        double negative = Math.max(0.0, -delta) + cancellation;

        int totalGenes = 2 * triangularInt(range.min(), range.center(), range.max(), random);
        if (positive == 0.0) {
            return new SynthesisAddressPlan(0.0, negative, 0, 0, totalGenes, totalGenes);
        }
        if (negative == 0.0) {
            return new SynthesisAddressPlan(positive, 0.0, totalGenes, totalGenes, 0, 0);
        }
        int positiveGenes = Math.max(1, Math.min(
                totalGenes - 1,
                (int) StrictMath.round(totalGenes * positive / (positive + negative))));
        int negativeGenes = totalGenes - positiveGenes;
        return new SynthesisAddressPlan(
                positive, negative,
                positiveGenes, positiveGenes,
                negativeGenes, negativeGenes);
    }

    private static int triangularInt(
            int min, int mode, int max, GenomeRandom random) {
        if (min == max) return min;
        double u = random.nextDouble();
        double split = (mode - min) / (double) (max - min);
        double value = u < split
                ? min + StrictMath.sqrt(u * (max - min) * (mode - min))
                : max - StrictMath.sqrt((1.0 - u) * (max - min) * (max - mode));
        return Math.max(min, Math.min(max, (int) StrictMath.round(value)));
    }

    @Override
    public WonderfulChickenDecodedPhenotype mapPhenotype(DecodedGenome decodedGenome) {
        Objects.requireNonNull(decodedGenome, "decodedGenome");

        EnumMap<StatType, Double> baseAbilities = new EnumMap<>(StatType.class);
        EnumMap<StatType, Double> extraordinary = new EnumMap<>(StatType.class);
        for (StatType stat : StatType.values()) {
            int targetId = stat.targetId();
            baseAbilities.put(
                    stat,
                    decodedGenome.aggregate(new GenomeAddress(0x00, targetId)).score());
            extraordinary.put(
                    stat,
                    0.5 * decodedGenome.aggregate(new GenomeAddress(0x07, targetId)).score());
        }

        EnumMap<PersonalityFactor, Double> personalityFactors = new EnumMap<>(PersonalityFactor.class);
        for (PersonalityFactor factor : PersonalityFactor.values()) {
            personalityFactors.put(
                    factor,
                    centeredScore(decodedGenome.aggregate(new GenomeAddress(0x03, factor.targetId()))));
        }

        EnumMap<DevelopmentFactor, Double> developmentFactors = new EnumMap<>(DevelopmentFactor.class);
        for (DevelopmentFactor factor : DevelopmentFactor.values()) {
            developmentFactors.put(
                    factor,
                    centeredScore(decodedGenome.aggregate(new GenomeAddress(0x01, factor.targetId()))));
        }

        DivineLineagePhenotype divine =
                decodeDivine(decodedGenome.aggregate(new GenomeAddress(0x05, 0x00)));

        return new WonderfulChickenDecodedPhenotype(
                baseAbilities,
                extraordinary,
                personalityFactors,
                decodePersonality(personalityFactors),
                decodeTraits(decodedGenome),
                developmentFactors,
                decodeInjuries(decodedGenome),
                divine);
    }

    private Nature decodePersonality(EnumMap<PersonalityFactor, Double> scores) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double score : scores.values()) {
            min = Math.min(min, score);
            max = Math.max(max, score);
        }

        if (max < settings.seriousMaxScore() - EPS
                || max - min <= settings.seriousSpread() + EPS) {
            return Nature.MAJIME;
        }

        List<PersonalityFactor> ranked = new ArrayList<>(List.of(PersonalityFactor.values()));
        ranked.sort(Comparator
                .comparingDouble((PersonalityFactor factor) -> scores.get(factor))
                .reversed()
                .thenComparingInt(PersonalityFactor::targetId));

        PersonalityFactor first = ranked.get(0);
        PersonalityFactor second = ranked.get(1);
        if (scores.get(first) - scores.get(second) >= settings.dominantGap() - EPS) {
            second = first;
        }
        return personalityForPair(first, second);
    }

    private static Nature personalityForPair(PersonalityFactor first, PersonalityFactor second) {
        int a = Math.min(first.targetId(), second.targetId());
        int b = Math.max(first.targetId(), second.targetId());
        return switch ((a << 8) | b) {
            case 0x0000, 0x0005 -> Nature.ISOGINBO;
            case 0x0001 -> Nature.SEKASEKA;
            case 0x0002 -> Nature.HANEKKAERI;
            case 0x0003 -> Nature.GANBARIYA;
            case 0x0004 -> Nature.DOSSHIRI;
            case 0x0101, 0x0105 -> Nature.UWA_NO_SORA;
            case 0x0102 -> Nature.TOBASHIYA;
            case 0x0103 -> Nature.JIKKURI;
            case 0x0104 -> Nature.NOBINOBI;
            case 0x0202, 0x0205 -> Nature.AWATENBO;
            case 0x0203 -> Nature.NEBARIZUYOI;
            case 0x0204 -> Nature.GENKIMONO;
            case 0x0303, 0x0305 -> Nature.TAMEKOMIYA;
            case 0x0304 -> Nature.KOMAME;
            case 0x0404, 0x0405 -> Nature.KUISHINBO;
            case 0x0505 -> Nature.CHAKKARI;
            default -> throw new IllegalStateException(
                    "unsupported personality factor pair: " + first + "/" + second);
        };
    }

    private List<ExpressedTrait> decodeTraits(DecodedGenome decodedGenome) {
        List<TraitScore> candidates = new ArrayList<>();
        for (Trait trait : Trait.values()) {
            double score = decodedGenome.aggregate(new GenomeAddress(0x04, trait.targetId())).score();
            if (score >= settings.traitExpressionThreshold() - EPS) {
                candidates.add(new TraitScore(trait, score));
            }
        }
        candidates.sort(Comparator
                .comparingDouble(TraitScore::score)
                .reversed()
                .thenComparingInt(entry -> entry.trait().targetId()));

        if (candidates.isEmpty()) {
            return List.of();
        }
        if (candidates.size() == 1) {
            return List.of(new ExpressedTrait(candidates.getFirst().trait(), TraitStrength.WEAK));
        }

        TraitScore first = candidates.get(0);
        TraitScore second = candidates.get(1);
        if (first.score() - second.score() >= settings.traitStrongGap() - EPS) {
            return List.of(new ExpressedTrait(first.trait(), TraitStrength.STRONG));
        }
        return List.of(
                new ExpressedTrait(first.trait(), TraitStrength.WEAK),
                new ExpressedTrait(second.trait(), TraitStrength.WEAK));
    }

    private List<InjuryPhenotype> decodeInjuries(DecodedGenome decodedGenome) {
        List<InjuryPhenotype> injuries = new ArrayList<>();

        for (StatType stat : StatType.values()) {
            GenomeAddress address = new GenomeAddress(0x02, stat.targetId());
            List<EffectiveContribution> contributions = decodedGenome.aggregate(address).contributions();
            if (contributions.isEmpty()) {
                continue;
            }

            List<InjuryPair> pairs = new ArrayList<>();
            double injurySurvival = 1.0;
            for (DecodedHomologyBlock block : decodedGenome.homologyBlocks()) {
                List<EffectiveContribution> sideA = injuryContributions(contributions, block, 0);
                List<EffectiveContribution> sideB = injuryContributions(contributions, block, 1);
                double loadA = boundedScore(sideA);
                double loadB = boundedScore(sideB);
                if (loadA <= 0.0 || loadB <= 0.0) {
                    continue;
                }

                List<DecodedGene> genes = injuryGenes(decodedGenome.physicalGenes(), address, block);
                if (genes.isEmpty()) {
                    continue;
                }

                double pairLoad = StrictMath.sqrt(loadA * loadB);
                injurySurvival *= 1.0 - pairLoad;
                double onset = genes.stream()
                        .mapToDouble(gene -> gene.extension().toLong(0, 8) / 255.0)
                        .average()
                        .orElseThrow();
                double severity = genes.stream()
                        .mapToDouble(gene -> gene.extension().toLong(8, 8) / 255.0)
                        .average()
                        .orElseThrow();
                pairs.add(new InjuryPair(pairLoad, onset, severity));
            }

            double score = 1.0 - injurySurvival;
            if (score < settings.injuryExpressionThreshold() - EPS || pairs.isEmpty()) {
                continue;
            }

            double totalWeight = pairs.stream().mapToDouble(InjuryPair::weight).sum();
            double onsetNormalized = pairs.stream()
                    .mapToDouble(pair -> pair.weight() * pair.onsetNormalized())
                    .sum() / totalWeight;
            double severityNormalized = pairs.stream()
                    .mapToDouble(pair -> pair.weight() * pair.severityNormalized())
                    .sum() / totalWeight;

            double onsetGameDay = Math.round(settings.injuryOnsetMaxGameDays() * onsetNormalized);
            double severityRank = settings.injurySeverityRankMin()
                    + (settings.injurySeverityRankMax() - settings.injurySeverityRankMin())
                    * severityNormalized;
            injuries.add(new InjuryPhenotype(stat, onsetGameDay, severityRank));
        }

        return List.copyOf(injuries);
    }

    private DivineLineagePhenotype decodeDivine(AddressAggregate aggregate) {
        double a = haplotypeScore(aggregate.contributions(), 0);
        double b = haplotypeScore(aggregate.contributions(), 1);
        boolean expressed =
                Math.min(a, b) >= settings.divineMinHaplotypeScore() - EPS
                        && a + b >= settings.divineTotalScore() - EPS;
        return new DivineLineagePhenotype(a, b, expressed);
    }

    private static List<EffectiveContribution> injuryContributions(
            List<EffectiveContribution> contributions,
            DecodedHomologyBlock block,
            int haplotype) {
        int start = haplotype == 0 ? block.startA() : block.startB();
        int end = haplotype == 0 ? block.endAExclusive() : block.endBExclusive();
        return contributions.stream()
                .filter(contribution -> !contribution.secondary())
                .filter(contribution -> contribution.chromosomeIndex() == block.chromosomeIndex())
                .filter(contribution -> contribution.haplotypeIndex() == haplotype)
                .filter(contribution -> contribution.startBit() >= start && contribution.startBit() < end)
                .toList();
    }

    private static List<DecodedGene> injuryGenes(
            List<DecodedGene> genes,
            GenomeAddress address,
            DecodedHomologyBlock block) {
        return genes.stream()
                .filter(DecodedGene::addressValid)
                .filter(gene -> address.equals(gene.address()))
                .filter(gene -> gene.extension().bitLength() >= 16)
                .filter(gene -> gene.chromosomeIndex() == block.chromosomeIndex())
                .filter(gene -> gene.haplotypeIndex() == 0 || gene.haplotypeIndex() == 1)
                .filter(gene -> {
                    int start = gene.haplotypeIndex() == 0 ? block.startA() : block.startB();
                    int end = gene.haplotypeIndex() == 0 ? block.endAExclusive() : block.endBExclusive();
                    return gene.startBit() >= start && gene.startBit() < end;
                })
                .toList();
    }

    private static double boundedScore(List<EffectiveContribution> contributions) {
        double positiveSurvival = 1.0;
        double negativeSurvival = 1.0;
        for (EffectiveContribution contribution : contributions) {
            if (contribution.effect() >= 0.0) {
                positiveSurvival *= 1.0 - contribution.saturation();
            } else {
                negativeSurvival *= 1.0 - contribution.saturation();
            }
        }
        return (1.0 - positiveSurvival) * negativeSurvival;
    }

    static double centeredScore(AddressAggregate aggregate) {
        double negative = 1.0 - aggregate.negativeSurvival();
        return clamp01(0.5 + 0.5 * (aggregate.positiveSaturation() - negative));
    }

    static double haplotypeScore(List<EffectiveContribution> contributions, int haplotype) {
        double positiveSurvival = 1.0;
        double negativeSurvival = 1.0;
        for (EffectiveContribution contribution : contributions) {
            if (contribution.haplotypeIndex() != haplotype) {
                continue;
            }
            if (contribution.effect() >= 0.0) {
                positiveSurvival *= 1.0 - contribution.saturation();
            } else {
                negativeSurvival *= 1.0 - contribution.saturation();
            }
        }
        return (1.0 - positiveSurvival) * negativeSurvival;
    }

    static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private record TraitScore(Trait trait, double score) {}

    private record InjuryPair(double weight, double onsetNormalized, double severityNormalized) {}
}
