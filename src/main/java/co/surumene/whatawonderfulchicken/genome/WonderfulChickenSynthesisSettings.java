package co.surumene.whatawonderfulchicken.genome;

public record WonderfulChickenSynthesisSettings(
        Distribution abilityDistribution,
        Distribution personalityDistribution,
        Distribution developmentDistribution,
        Range abilityGenes,
        Range personalityGenes,
        Range developmentGenes,
        Range traitGenes,
        Injury injury,
        Divine divine,
        Extraordinary extraordinary,
        double cancellationMin,
        double cancellationMax,
        double personalityCancellationMin,
        double personalityCancellationMax,
        double highTargetHeadroom,
        int directGenesSoftMin,
        int directGenesSoftMax,
        int directGenesHardMax,
        int regulationGenesMin,
        int regulationGenesCenter,
        int regulationGenesMax,
        int regulationGenesHardMax,
        int recognizableGenesHardMax,
        double recognizableRegionMaxRatio,
        double noncodingRegionMinRatio,
        Relay relay) {

    public WonderfulChickenSynthesisSettings {
        if (abilityDistribution == null || personalityDistribution == null
                || developmentDistribution == null || abilityGenes == null
                || personalityGenes == null || developmentGenes == null
                || traitGenes == null || injury == null || divine == null
                || extraordinary == null || relay == null) {
            throw new NullPointerException("synthesis settings must not contain null components");
        }
        requireRange01(cancellationMin, cancellationMax, "cancellation");
        requireRange01(personalityCancellationMin, personalityCancellationMax, "personalityCancellation");
        if (!Double.isFinite(highTargetHeadroom) || highTargetHeadroom < 0.0 || highTargetHeadroom >= 1.0) {
            throw new IllegalArgumentException("highTargetHeadroom must be finite and in [0,1)");
        }
        if (directGenesSoftMin < 0 || directGenesSoftMax < directGenesSoftMin
                || directGenesHardMax < directGenesSoftMax) {
            throw new IllegalArgumentException("invalid direct gene limits");
        }
        if (regulationGenesMin < 0 || regulationGenesCenter < regulationGenesMin
                || regulationGenesMax < regulationGenesCenter
                || regulationGenesHardMax < regulationGenesMax) {
            throw new IllegalArgumentException("invalid regulation gene limits");
        }
        if (recognizableGenesHardMax < 1) {
            throw new IllegalArgumentException("recognizableGenesHardMax must be >= 1");
        }
        requireUnit(recognizableRegionMaxRatio, "recognizableRegionMaxRatio");
        requireUnit(noncodingRegionMinRatio, "noncodingRegionMinRatio");
    }

    public static WonderfulChickenSynthesisSettings defaults() {
        return new WonderfulChickenSynthesisSettings(
                new Distribution(0.42, 0.22),
                new Distribution(0.50, 0.10),
                new Distribution(0.50, 0.20),
                new Range(8, 9, 12),
                new Range(4, 6, 8),
                new Range(3, 5, 7),
                new Range(1, 2, 4),
                new Injury(1.5, 6, 3, 0.43, 0.035, 0.30, 0.55, 0.002, 0.50, 0.22, 0.50, 0.18),
                new Divine(0.30, 6, 8, 10, 0.93, 0.06, 0.16),
                new Extraordinary(4, 6, 2, 3, 0.15),
                0.20, 0.35,
                0.30, 0.50,
                0.02,
                180, 220, 256,
                35, 40, 50, 64,
                320, 0.65, 0.35,
                new Relay(0.05, 0.08, 0.15, 0.45, 0.60, 0.75, 0.75));
    }

    private static void requireRange01(double min, double max, String name) {
        requireUnit(min, name + "Min");
        requireUnit(max, name + "Max");
        if (max < min) throw new IllegalArgumentException(name + " max must be >= min");
    }

    private static void requireUnit(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0,1]");
        }
    }

    public record Distribution(double mean, double standardDeviation) {
        public Distribution {
            if (!Double.isFinite(mean) || !Double.isFinite(standardDeviation)
                    || standardDeviation <= 0.0) {
                throw new IllegalArgumentException("invalid distribution");
            }
        }
    }

    public record Range(int min, int center, int max) {
        public Range {
            if (min < 1 || center < min || max < center) {
                throw new IllegalArgumentException("invalid range");
            }
        }
    }

    public record Injury(
            double blocksLambda, int blocksMax, int genesPerBlock,
            double loadMean, double loadStandardDeviation, double loadMin, double loadMax,
            double bilateralProbability,
            double onsetMean, double onsetStandardDeviation,
            double severityMean, double severityStandardDeviation) {
        public Injury {
            if (!(blocksLambda >= 0.0) || blocksMax < 0 || genesPerBlock < 1) {
                throw new IllegalArgumentException("invalid injury block settings");
            }
            requireUnit(bilateralProbability, "bilateralProbability");
        }
    }

    public record Divine(
            double supplyProbability, int genesMin, int genesCenter, int genesMax,
            double majorHaplotypeProbability, double geneDMin, double geneDMax) {
        public Divine {
            requireUnit(supplyProbability, "supplyProbability");
            requireUnit(majorHaplotypeProbability, "majorHaplotypeProbability");
            if (genesMin < 1 || genesCenter < genesMin || genesMax < genesCenter) {
                throw new IllegalArgumentException("invalid divine gene range");
            }
            if (!Double.isFinite(geneDMin) || !Double.isFinite(geneDMax)
                    || geneDMin < 0.0 || geneDMax < geneDMin) {
                throw new IllegalArgumentException("invalid divine gene contribution range");
            }
        }
    }

    public record Extraordinary(
            int genesPerTargetMin, int genesPerTargetMax,
            int blocksPerTargetMin, int blocksPerTargetMax,
            double transferMax) {
        public Extraordinary {
            if (genesPerTargetMin < 1 || genesPerTargetMax < genesPerTargetMin
                    || blocksPerTargetMin < 1 || blocksPerTargetMax < blocksPerTargetMin) {
                throw new IllegalArgumentException("invalid extraordinary ranges");
            }
            requireUnit(transferMax, "transferMax");
        }
    }

    public record Relay(
            double attachmentMinRatio, double attachmentMaxRatio,
            double normalSecondaryMinRatio, double normalSecondaryMaxRatio,
            double strongSecondaryMinRatio, double strongSecondaryMaxRatio,
            double positiveRatio) {
        public Relay {
            requireRange01(attachmentMinRatio, attachmentMaxRatio, "attachment");
            requireRange01(normalSecondaryMinRatio, normalSecondaryMaxRatio, "normalSecondary");
            requireRange01(strongSecondaryMinRatio, strongSecondaryMaxRatio, "strongSecondary");
            requireUnit(positiveRatio, "positiveRatio");
        }
    }
}
