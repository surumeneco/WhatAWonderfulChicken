package co.surumene.whatawonderfulchicken.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class WonderfulChickenInventoryHolder implements InventoryHolder {
    private final UUID chickenId;
    private Inventory inventory;

    public WonderfulChickenInventoryHolder(UUID chickenId) {
        this.chickenId = chickenId;
    }

    public UUID chickenId() { return chickenId; }
    public void inventory(Inventory inventory) { this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }
}