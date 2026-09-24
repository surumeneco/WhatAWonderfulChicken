package co.surumene.whatawonderfulchicken.listener;

import co.surumene.whatawonderfulchicken.gui.InventoryService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class InventoryListener implements Listener {
    private final InventoryService inventories;
    public InventoryListener(InventoryService inventories) { this.inventories = inventories; }
    @EventHandler public void onClick(InventoryClickEvent event) { inventories.handleClick(event); }
    @EventHandler public void onDrag(InventoryDragEvent event) { inventories.handleDrag(event); }
    @EventHandler public void onClose(InventoryCloseEvent event) { inventories.handleClose(event); }
}