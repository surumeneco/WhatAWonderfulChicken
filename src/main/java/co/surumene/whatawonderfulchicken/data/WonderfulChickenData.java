package co.surumene.whatawonderfulchicken.data;

import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public final class WonderfulChickenData {
    private final Map<StatType, Double> values = new EnumMap<>(StatType.class);
    private final Map<StatType, Double> normalized = new EnumMap<>(StatType.class);
    private double currentStamina;
    private ItemStack carpet;
    private ItemStack shulkerBox;
    private ItemStack headItem;
    private BehaviorMode behaviorMode = BehaviorMode.WANDER;
    private UUID followTarget;
    private String bloodlineId;
    private int generation;
    private PedigreeData pedigree = PedigreeData.EMPTY;
    private Genetics genetics;

    public double value(StatType stat) { return values.getOrDefault(stat, 0.0); }
    public void value(StatType stat, double value) { values.put(stat, value); }
    public double normalized(StatType stat) { return normalized.getOrDefault(stat, 0.0); }
    public void normalized(StatType stat, double value) { normalized.put(stat, value); }
    public double currentStamina() { return currentStamina; }
    public void currentStamina(double value) { currentStamina = value; }
    public ItemStack carpet() { return cloneOrNull(carpet); }
    public void carpet(ItemStack item) { carpet = cloneOrNull(item); }
    public ItemStack shulkerBox() { return cloneOrNull(shulkerBox); }
    public void shulkerBox(ItemStack item) { shulkerBox = cloneOrNull(item); }
    public ItemStack headItem() { return cloneOrNull(headItem); }
    public void headItem(ItemStack item) { headItem = cloneOrNull(item); }
    public BehaviorMode behaviorMode() { return behaviorMode; }
    public void behaviorMode(BehaviorMode mode) { behaviorMode = mode; }
    public UUID followTarget() { return followTarget; }
    public void followTarget(UUID target) { followTarget = target; }
    public String bloodlineId() { return bloodlineId; }
    public void bloodlineId(String value) { bloodlineId = value; }
    public int generation() { return generation; }
    public void generation(int value) { generation = value; }
    public Genetics genetics() { return genetics; }
    public void genetics(Genetics genes) { genetics = genes; }
    public Nature nature() { return genetics == null ? Nature.MAJIME : genetics.nature(); }
    public Trait trait() { return genetics == null ? null : genetics.trait(); }

    /** Effective gameplay value; breeding and ranks continue to use the unmodified stored value. */
    public double effective(StatType stat, double configuredAdjustment) {
        double adjustment = configuredAdjustment * (trait() == Trait.KUSE_MASHI ? 1.5 : 1.0);
        double effective = value(stat) * nature().multiplier(stat, adjustment);
        return stat == StatType.MAX_HEALTH ? Math.max(1.0, Math.round(effective)) : effective;
    }

    public PedigreeData pedigree() { return pedigree; }
    public void pedigree(PedigreeData value) { pedigree = value == null ? PedigreeData.EMPTY : value; }

    public WonderfulChickenData copy() {
        WonderfulChickenData snapshot = new WonderfulChickenData();
        snapshot.values.putAll(values);
        snapshot.normalized.putAll(normalized);
        snapshot.currentStamina = currentStamina;
        snapshot.carpet = cloneOrNull(carpet);
        snapshot.shulkerBox = cloneOrNull(shulkerBox);
        snapshot.headItem = cloneOrNull(headItem);
        snapshot.behaviorMode = behaviorMode;
        snapshot.followTarget = followTarget;
        snapshot.bloodlineId = bloodlineId;
        snapshot.generation = generation;
        snapshot.pedigree = pedigree;
        snapshot.genetics = genetics;
        return snapshot;
    }

    private static ItemStack cloneOrNull(ItemStack item) {
        return item == null || item.isEmpty() ? null : item.clone();
    }
}