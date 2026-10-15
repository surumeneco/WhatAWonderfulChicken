package co.surumene.whatawonderfulchicken.breeding;

import co.surumene.whatawonderfulchicken.data.StatType;
import co.surumene.whatawonderfulchicken.data.Trait;
import co.surumene.whatawonderfulchicken.data.TraitStrength;
import co.surumene.whatawonderfulchicken.genome.WonderfulChickenGenomeProfile;
import co.surumene.whatawonderfulchicken.data.WonderfulChickenData;
import co.surumene.wgl.api.*;

import java.util.*;
import java.util.function.ToDoubleFunction;

public final class WonderfulChickenBreedingContextFactory {
    private final WonderfulChickenGenomeProfile profile;
    private final WonderfulChickenInheritanceAnalyzer analyzer;
    private final WonderfulChickenBreedingSettings settings;

    public WonderfulChickenBreedingContextFactory(
            GenomeEngine engine,
            WonderfulChickenGenomeProfile profile) {
        this(engine, profile, WonderfulChickenBreedingSettings.defaults());
    }

    public WonderfulChickenBreedingContextFactory(
            GenomeEngine engine,
            WonderfulChickenGenomeProfile profile,
            WonderfulChickenBreedingSettings settings) {
        Objects.requireNonNull(engine, "engine");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.analyzer = new WonderfulChickenInheritanceAnalyzer(engine, profile);
    }

    public BreedingContext create(
            WonderfulChickenData parentA,
            WonderfulChickenData parentB,
            GenomeRandom random) {
        Objects.requireNonNull(parentA, "parentA");
        Objects.requireNonNull(parentB, "parentB");
        Objects.requireNonNull(random, "random");

        WonderfulChickenBreedingSettings policy = settings;
        Optional<TraitStrength> directA =
                WonderfulChickenBreedingPolicy.expressedTrait(
                        parentA.phenotypeSnapshot(), Trait.JIKIDEN);
        Optional<TraitStrength> directB =
                WonderfulChickenBreedingPolicy.expressedTrait(
                        parentB.phenotypeSnapshot(), Trait.JIKIDEN);

        WonderfulChickenInheritanceAnalyzer.ParentAnalysis analysisA =
                directA.isPresent() || hasHardCandidate(parentA)
                        ? analyzer.analyze(parentA)
                        : null;
        WonderfulChickenInheritanceAnalyzer.ParentAnalysis analysisB =
                directB.isPresent() || hasHardCandidate(parentB)
                        ? analyzer.analyze(parentB)
                        : null;

        HardSelection hard = selectHard(
                parentA, analysisA,
                parentB, analysisB,
                random);

        SoftSelection soft = selectSoft(
                analysisA, directA, hard.parentA(),
                analysisB, directB, hard.parentB(),
                policy, random);

        List<InheritanceConstraint> finalA =
                combine(hard.parentA(), soft.parentA());
        List<InheritanceConstraint> finalB =
                combine(hard.parentB(), soft.parentB());

        double mutationMultiplier =
                WonderfulChickenBreedingPolicy.mutationMultiplier(
                        parentA.phenotypeSnapshot(),
                        parentB.phenotypeSnapshot(),
                        policy);

        return new BreedingContext(
                profile.backbone(),
                mutationMultiplier,
                WonderfulChickenBreedingPolicy.deNovoForbiddenAddresses(),
                null,
                false,
                new ParentMeiosisPolicy(finalA),
                new ParentMeiosisPolicy(finalB));
    }

    private static boolean hasHardCandidate(
            WonderfulChickenData parent) {
        return parent.phenotypeSnapshot().divineLineageExpressed()
                && parent.phenotypeSnapshot().normalizedAbilities().values().stream()
                        .anyMatch(value -> value > 1.0);
    }

    private HardSelection selectHard(
            WonderfulChickenData parentA,
            WonderfulChickenInheritanceAnalyzer.ParentAnalysis analysisA,
            WonderfulChickenData parentB,
            WonderfulChickenInheritanceAnalyzer.ParentAnalysis analysisB,
            GenomeRandom random) {
        List<StatCandidate> candidatesA =
                new ArrayList<>(hardCandidates(parentA));
        List<StatCandidate> candidatesB =
                new ArrayList<>(hardCandidates(parentB));

        while (!candidatesA.isEmpty() || !candidatesB.isEmpty()) {
            if (candidatesA.isEmpty()) {
                Optional<InheritanceConstraint> chosen =
                        chooseSingleHard(candidatesB, analysisB, random);
                return new HardSelection(List.of(), chosen.stream().toList());
            }
            if (candidatesB.isEmpty()) {
                Optional<InheritanceConstraint> chosen =
                        chooseSingleHard(candidatesA, analysisA, random);
                return new HardSelection(chosen.stream().toList(), List.of());
            }

            List<StatPair> pairs = distinctPairs(candidatesA, candidatesB);
            if (!pairs.isEmpty()) {
                StatPair selected =
                        chooseWeighted(pairs, StatPair::weight, random);
                Optional<WonderfulChickenInheritanceAnalyzer.InheritanceBlock> blockA =
                        bestExtraordinaryBlock(analysisA, selected.a().stat());
                Optional<WonderfulChickenInheritanceAnalyzer.InheritanceBlock> blockB =
                        bestExtraordinaryBlock(analysisB, selected.b().stat());

                if (blockA.isPresent() && blockB.isPresent()) {
                    return new HardSelection(
                            List.of(blockA.orElseThrow().hardConstraint()),
                            List.of(blockB.orElseThrow().hardConstraint()));
                }
                if (blockA.isEmpty()) {
                    removeAbility(candidatesA, selected.a().stat());
                }
                if (blockB.isEmpty()) {
                    removeAbility(candidatesB, selected.b().stat());
                }
                continue;
            }

            StatCandidate onlyA = candidatesA.getFirst();
            StatCandidate onlyB = candidatesB.getFirst();
            Optional<WonderfulChickenInheritanceAnalyzer.InheritanceBlock> blockA =
                    bestExtraordinaryBlock(analysisA, onlyA.stat());
            Optional<WonderfulChickenInheritanceAnalyzer.InheritanceBlock> blockB =
                    bestExtraordinaryBlock(analysisB, onlyB.stat());

            if (blockA.isPresent() && blockB.isPresent()) {
                if (blockA.orElseThrow().delta() >= blockB.orElseThrow().delta()) {
                    return new HardSelection(
                            List.of(blockA.orElseThrow().hardConstraint()),
                            List.of());
                }
                return new HardSelection(
                        List.of(),
                        List.of(blockB.orElseThrow().hardConstraint()));
            }
            if (blockA.isEmpty()) {
                removeAbility(candidatesA, onlyA.stat());
            }
            if (blockB.isEmpty()) {
                removeAbility(candidatesB, onlyB.stat());
            }
        }

        return HardSelection.empty();
    }

    private Optional<InheritanceConstraint> chooseSingleHard(
            List<StatCandidate> candidates,
            WonderfulChickenInheritanceAnalyzer.ParentAnalysis analysis,
            GenomeRandom random) {
        while (!candidates.isEmpty()) {
            StatCandidate selected =
                    chooseWeighted(candidates, StatCandidate::weight, random);
            Optional<WonderfulChickenInheritanceAnalyzer.InheritanceBlock> block =
                    bestExtraordinaryBlock(analysis, selected.stat());
            if (block.isPresent()) {
                return Optional.of(block.orElseThrow().hardConstraint());
            }
            removeAbility(candidates, selected.stat());
        }
        return Optional.empty();
    }

    private static List<StatCandidate> hardCandidates(
            WonderfulChickenData parent) {
        if (!parent.phenotypeSnapshot().divineLineageExpressed()) {
            return List.of();
        }
        List<StatCandidate> result = new ArrayList<>();
        for (StatType stat : StatType.values()) {
            double finalAbility =
                    parent.phenotypeSnapshot().normalizedAbilities().get(stat);
            if (finalAbility > 1.0) {
                result.add(new StatCandidate(
                        stat,
                        finalAbility - 1.0));
            }
        }
        return List.copyOf(result);
    }

    private static Optional<WonderfulChickenInheritanceAnalyzer.InheritanceBlock>
    bestExtraordinaryBlock(
            WonderfulChickenInheritanceAnalyzer.ParentAnalysis analysis,
            StatType stat) {
        return analysis.extraordinaryBlocks(stat).stream()
                .max(Comparator.comparingDouble(
                        WonderfulChickenInheritanceAnalyzer.InheritanceBlock::delta));
    }

    private SoftSelection selectSoft(
            WonderfulChickenInheritanceAnalyzer.ParentAnalysis analysisA,
            Optional<TraitStrength> strengthA,
            List<InheritanceConstraint> hardA,
            WonderfulChickenInheritanceAnalyzer.ParentAnalysis analysisB,
            Optional<TraitStrength> strengthB,
            List<InheritanceConstraint> hardB,
            WonderfulChickenBreedingSettings policy,
            GenomeRandom random) {
        List<StatCandidate> candidatesA = strengthA.isPresent()
                ? new ArrayList<>(normalCandidates(analysisA))
                : new ArrayList<>();
        List<StatCandidate> candidatesB = strengthB.isPresent()
                ? new ArrayList<>(normalCandidates(analysisB))
                : new ArrayList<>();

        while (!candidatesA.isEmpty() || !candidatesB.isEmpty()) {
            if (candidatesA.isEmpty()) {
                Optional<InheritanceConstraint> chosen =
                        chooseSingleSoft(
                                candidatesB, analysisB, hardB,
                                strengthB.orElseThrow(), policy, random);
                return new SoftSelection(List.of(), chosen.stream().toList());
            }
            if (candidatesB.isEmpty()) {
                Optional<InheritanceConstraint> chosen =
                        chooseSingleSoft(
                                candidatesA, analysisA, hardA,
                                strengthA.orElseThrow(), policy, random);
                return new SoftSelection(chosen.stream().toList(), List.of());
            }

            List<StatPair> pairs = distinctPairs(candidatesA, candidatesB);
            if (!pairs.isEmpty()) {
                StatPair selected =
                        chooseWeighted(pairs, StatPair::weight, random);
                List<WonderfulChickenInheritanceAnalyzer.InheritanceBlock> blocksA =
                        eligibleNormalBlocks(
                                analysisA, selected.a().stat(), hardA);
                List<WonderfulChickenInheritanceAnalyzer.InheritanceBlock> blocksB =
                        eligibleNormalBlocks(
                                analysisB, selected.b().stat(), hardB);

                if (!blocksA.isEmpty() && !blocksB.isEmpty()) {
                    return new SoftSelection(
                            List.of(toSoftConstraint(
                                    blocksA,
                                    strengthA.orElseThrow(),
                                    policy,
                                    random)),
                            List.of(toSoftConstraint(
                                    blocksB,
                                    strengthB.orElseThrow(),
                                    policy,
                                    random)));
                }
                if (blocksA.isEmpty()) {
                    removeAbility(candidatesA, selected.a().stat());
                }
                if (blocksB.isEmpty()) {
                    removeAbility(candidatesB, selected.b().stat());
                }
                continue;
            }

            StatCandidate onlyA = candidatesA.getFirst();
            StatCandidate onlyB = candidatesB.getFirst();
            List<WonderfulChickenInheritanceAnalyzer.InheritanceBlock> blocksA =
                    eligibleNormalBlocks(
                            analysisA, onlyA.stat(), hardA);
            List<WonderfulChickenInheritanceAnalyzer.InheritanceBlock> blocksB =
                    eligibleNormalBlocks(
                            analysisB, onlyB.stat(), hardB);

            if (!blocksA.isEmpty() && !blocksB.isEmpty()) {
                StatCandidate selectedParent = chooseWeighted(
                        List.of(onlyA, onlyB),
                        StatCandidate::weight,
                        random);
                if (selectedParent == onlyA) {
                    return new SoftSelection(
                            List.of(toSoftConstraint(
                                    blocksA,
                                    strengthA.orElseThrow(),
                                    policy,
                                    random)),
                            List.of());
                }
                return new SoftSelection(
                        List.of(),
                        List.of(toSoftConstraint(
                                blocksB,
                                strengthB.orElseThrow(),
                                policy,
                                random)));
            }
            if (blocksA.isEmpty()) {
                removeAbility(candidatesA, onlyA.stat());
            }
            if (blocksB.isEmpty()) {
                removeAbility(candidatesB, onlyB.stat());
            }
        }

        return SoftSelection.empty();
    }

    private Optional<InheritanceConstraint> chooseSingleSoft(
            List<StatCandidate> candidates,
            WonderfulChickenInheritanceAnalyzer.ParentAnalysis analysis,
            List<InheritanceConstraint> hard,
            TraitStrength strength,
            WonderfulChickenBreedingSettings policy,
            GenomeRandom random) {
        while (!candidates.isEmpty()) {
            StatCandidate selected =
                    chooseWeighted(candidates, StatCandidate::weight, random);
            List<WonderfulChickenInheritanceAnalyzer.InheritanceBlock> blocks =
                    eligibleNormalBlocks(
                            analysis, selected.stat(), hard);
            if (!blocks.isEmpty()) {
                return Optional.of(toSoftConstraint(
                        blocks, strength, policy, random));
            }
            removeAbility(candidates, selected.stat());
        }
        return Optional.empty();
    }

    private static List<StatCandidate> normalCandidates(
            WonderfulChickenInheritanceAnalyzer.ParentAnalysis analysis) {
        List<StatCandidate> result = new ArrayList<>();
        boolean anyPositive = false;
        for (StatType stat : StatType.values()) {
            double base = analysis.baseAbility(stat);
            double weight = base * base;
            result.add(new StatCandidate(stat, weight));
            anyPositive |= weight > 0.0;
        }
        if (!anyPositive) {
            return List.copyOf(result);
        }
        return result.stream()
                .filter(candidate -> candidate.weight() > 0.0)
                .toList();
    }

    private static List<WonderfulChickenInheritanceAnalyzer.InheritanceBlock>
    eligibleNormalBlocks(
            WonderfulChickenInheritanceAnalyzer.ParentAnalysis analysis,
            StatType stat,
            List<InheritanceConstraint> hard) {
        return analysis.normalBlocks(stat).stream()
                .filter(block -> hard.stream().noneMatch(
                        constraint -> overlaps(constraint, block)))
                .toList();
    }

    private static InheritanceConstraint toSoftConstraint(
            List<WonderfulChickenInheritanceAnalyzer.InheritanceBlock> blocks,
            TraitStrength strength,
            WonderfulChickenBreedingSettings policy,
            GenomeRandom random) {
        WonderfulChickenInheritanceAnalyzer.InheritanceBlock block =
                chooseWeighted(
                        blocks,
                        candidate -> candidate.delta() * candidate.delta(),
                        random);
        return block.softConstraint(
                WonderfulChickenBreedingPolicy.directRetentionProbability(
                        strength, policy),
                policy.directInheritance().crossoverWeightInsideBlock());
    }

    private static List<StatPair> distinctPairs(
            List<StatCandidate> a,
            List<StatCandidate> b) {
        List<StatPair> result = new ArrayList<>();
        for (StatCandidate left : a) {
            for (StatCandidate right : b) {
                if (left.stat() != right.stat()) {
                    result.add(new StatPair(
                            left,
                            right,
                            left.weight() * right.weight()));
                }
            }
        }
        return List.copyOf(result);
    }

    private static boolean overlaps(
            InheritanceConstraint constraint,
            WonderfulChickenInheritanceAnalyzer.InheritanceBlock block) {
        return constraint.chromosomeIndex() == block.chromosomeIndex()
                && constraint.startBit() < block.endBitExclusive()
                && block.startBit() < constraint.endBitExclusive();
    }

    private static void removeAbility(
            List<StatCandidate> candidates,
            StatType stat) {
        candidates.removeIf(candidate -> candidate.stat() == stat);
    }

    private static List<InheritanceConstraint> combine(
            List<InheritanceConstraint> hard,
            List<InheritanceConstraint> soft) {
        List<InheritanceConstraint> result =
                new ArrayList<>(hard.size() + soft.size());
        result.addAll(hard);
        result.addAll(
                WonderfulChickenBreedingPolicy.resolveSoftAgainstHard(
                        hard, soft));
        return List.copyOf(result);
    }

    private static <T> T chooseWeighted(
            List<T> values,
            ToDoubleFunction<T> weight,
            GenomeRandom random) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException(
                    "cannot choose from an empty list");
        }

        double total = 0.0;
        for (T value : values) {
            double candidate = weight.applyAsDouble(value);
            if (Double.isFinite(candidate) && candidate > 0.0) {
                total += candidate;
            }
        }

        if (!(total > 0.0) || !Double.isFinite(total)) {
            return values.get(random.nextInt(values.size()));
        }

        double draw = random.nextDouble() * total;
        double cumulative = 0.0;
        for (T value : values) {
            double candidate = weight.applyAsDouble(value);
            if (Double.isFinite(candidate) && candidate > 0.0) {
                cumulative += candidate;
                if (draw < cumulative) {
                    return value;
                }
            }
        }
        return values.getLast();
    }

    private record StatCandidate(
            StatType stat,
            double weight) {}

    private record StatPair(
            StatCandidate a,
            StatCandidate b,
            double weight) {}

    private record HardSelection(
            List<InheritanceConstraint> parentA,
            List<InheritanceConstraint> parentB) {
        private HardSelection {
            parentA = List.copyOf(parentA);
            parentB = List.copyOf(parentB);
        }

        static HardSelection empty() {
            return new HardSelection(List.of(), List.of());
        }
    }

    private record SoftSelection(
            List<InheritanceConstraint> parentA,
            List<InheritanceConstraint> parentB) {
        private SoftSelection {
            parentA = List.copyOf(parentA);
            parentB = List.copyOf(parentB);
        }

        static SoftSelection empty() {
            return new SoftSelection(List.of(), List.of());
        }
    }
}
