package co.surumene.whatawonderfulchicken.breeding;

public record WonderfulChickenBreedingSettings(
        DirectInheritance directInheritance,
        HatenkoTrait hatenkoTrait) {

    public WonderfulChickenBreedingSettings {
        if (directInheritance == null || hatenkoTrait == null) {
            throw new NullPointerException("breeding settings must not contain null components");
        }
    }

    public static WonderfulChickenBreedingSettings defaults() {
        return new WonderfulChickenBreedingSettings(
                new DirectInheritance(0.75, 0.25),
                new HatenkoTrait(2.0, 4.0));
    }

    public record DirectInheritance(
            double weakPreferProbability,
            double crossoverWeightInsideBlock) {
        public DirectInheritance {
            if (!Double.isFinite(weakPreferProbability)
                    || weakPreferProbability < 0.0
                    || weakPreferProbability > 1.0) {
                throw new IllegalArgumentException("weakPreferProbability must be in [0,1]");
            }
            if (!Double.isFinite(crossoverWeightInsideBlock)
                    || crossoverWeightInsideBlock < 0.0) {
                throw new IllegalArgumentException(
                        "crossoverWeightInsideBlock must be finite and >= 0");
            }
        }
    }

    public record HatenkoTrait(
            double weakParentMultiplier,
            double strongParentMultiplier) {
        public HatenkoTrait {
            if (!Double.isFinite(weakParentMultiplier)
                    || weakParentMultiplier < 0.0
                    || !Double.isFinite(strongParentMultiplier)
                    || strongParentMultiplier < weakParentMultiplier) {
                throw new IllegalArgumentException("invalid HATENKO mutation multipliers");
            }
        }
    }
}
