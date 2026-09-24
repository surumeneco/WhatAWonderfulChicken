package co.surumene.whatawonderfulchicken.util;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class ItemUtil {
    private ItemUtil() {}

    public static boolean isCarpet(ItemStack item) {
        return item != null && !item.isEmpty() && item.getType().name().endsWith("_CARPET");
    }

    public static boolean isShulkerBox(ItemStack item) {
        if (item == null || item.isEmpty()) return false;
        String name = item.getType().name();
        return name.equals("SHULKER_BOX") || name.endsWith("_SHULKER_BOX");
    }

    public static boolean isHeadEquippable(ItemStack item) {
        if (item == null || item.isEmpty()) return false;
        Equippable equippable = item.getData(DataComponentTypes.EQUIPPABLE);
        return equippable != null && equippable.slot() == EquipmentSlot.HEAD;
    }

    public static ItemStack one(ItemStack source) {
        if (source == null || source.isEmpty()) return null;
        return source.asOne();
    }

    public static ItemStack fromMaterial(String raw) {
        Material material = Material.matchMaterial(raw);
        if (material == null || !material.isItem()) throw new IllegalArgumentException("Unknown item: " + raw);
        return ItemStack.of(material);
    }
}