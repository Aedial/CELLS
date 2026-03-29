package com.cells.gui.slots;

import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;

import appeng.helpers.InventoryAction;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;


/**
 * Unified item storage slot implementation.
 * <p>
 * Extends AbstractResourceTankSlot for consistent slot behavior across all resource types.
 * Displays item contents in import/export interfaces.
 * <ul>
 *   <li><b>Import mode</b>: Click with item to insert (validates against filter), click to extract</li>
 *   <li><b>Export mode</b>: Display-only (items come from adjacent inventories to ME)</li>
 * </ul>
 *
 * @param <H> The host interface type (must provide item access methods)
 */
public class ItemStorageSlot<H extends ItemStorageSlot.IItemStorageHost> extends AbstractResourceTankSlot<ItemStack, H> {

    /**
     * Interface that hosts must implement to provide item storage access.
     */
    public interface IItemStorageHost {
        /**
         * Get the item in the specified storage slot.
         * The ItemStack's count is capped at Integer.MAX_VALUE for API compatibility.
         * Use {@link #getItemAmount(int)} for the true long amount.
         */
        @Nullable
        ItemStack getItemInStorage(int slotIndex);

        /**
         * Get the actual amount stored in the specified slot as a long.
         * This bypasses the int limitation of ItemStack.getCount().
         */
        long getItemAmount(int slotIndex);

        /**
         * Get the type name for localization (e.g., "item").
         */
        String getTypeName();

        /**
         * Check if this is an export interface.
         */
        boolean isExport();
    }

    /**
     * Create an item storage slot with pagination support.
     *
     * @param host The item interface host
     * @param displaySlotIndex The display slot index (0-35 for one page)
     * @param id The slot ID for the GUI
     * @param x X position in GUI
     * @param y Y position in GUI
     * @param pageOffsetSupplier Supplier that returns the current page's starting slot index
     * @param maxSlotSizeSupplier Supplier for the synced max slot size (from container)
     */
    public ItemStorageSlot(
            H host,
            int displaySlotIndex,
            int id,
            int x, int y,
            IntSupplier pageOffsetSupplier,
            LongSupplier maxSlotSizeSupplier) {
        super(host, displaySlotIndex, id, x, y, pageOffsetSupplier, maxSlotSizeSupplier, host.isExport());
    }

    @Override
    @Nullable
    public ItemStack getResource() {
        return this.host.getItemInStorage(getTankIndex());
    }

    @Override
    protected void drawResourceContent(Minecraft mc, int mouseX, int mouseY, float partialTicks, ItemStack resource) {
        if (resource.isEmpty()) return;

        RenderHelper.enableGUIStandardItemLighting();
        mc.getRenderItem().renderItemAndEffectIntoGUI(resource, this.xPos(), this.yPos());
        RenderHelper.disableStandardItemLighting();
    }

    @Override
    protected String getResourceDisplayName(ItemStack resource) {
        return resource.getDisplayName();
    }

    @Override
    protected long getResourceAmount(ItemStack resource) {
        // Use the host's getItemAmount for true long precision
        return this.host.getItemAmount(getTankIndex());
    }

    @Override
    protected String getTypeName() {
        return this.host.getTypeName();
    }

    @Override
    protected boolean handlePouring(ItemStack clickStack, int mouseButton) {
        // Export mode is read-only - items come from adjacent inventories
        if (this.isExport) return false;

        // Send action based on mouse button:
        // - Left-click (0): PICKUP_OR_SET_DOWN - insert full stack
        // - Right-click (1): SPLIT_OR_PLACE_SINGLE - insert single item
        InventoryAction action = (mouseButton == 1)
            ? InventoryAction.SPLIT_OR_PLACE_SINGLE
            : InventoryAction.PICKUP_OR_SET_DOWN;

        NetworkHandler.instance().sendToServer(new PacketInventoryAction(action, getTankIndex(), 0));
        return true;
    }

    @Override
    protected boolean handleFilling(ItemStack clickStack, int mouseButton) {
        // Export mode allows extraction with empty cursor
        // Send action based on mouse button:
        // - Left-click (0): PICKUP_OR_SET_DOWN - extract full stack (capped at 64)
        // - Right-click (1): SPLIT_OR_PLACE_SINGLE - extract half stack
        InventoryAction action = (mouseButton == 1)
            ? InventoryAction.SPLIT_OR_PLACE_SINGLE
            : InventoryAction.PICKUP_OR_SET_DOWN;

        NetworkHandler.instance().sendToServer(new PacketInventoryAction(action, getTankIndex(), 0));
        return true;
    }

    /**
     * Handle shift-click for quick item transfer.
     * <p>
     * Export mode: Send items to player inventory.
     * Import mode: No action (extraction not allowed).
     *
     * @return true if handled
     */
    @Override
    protected boolean handleShiftClick(ItemStack clickStack, int mouseButton) {
        // Import mode: can't extract
        if (!this.isExport) return false;

        ItemStack resource = getResource();
        if (resource == null || resource.isEmpty()) return false;

        // Shift-click to extract whole stack to player inventory
        NetworkHandler.instance().sendToServer(new PacketInventoryAction(
            InventoryAction.SHIFT_CLICK,
            getTankIndex(),
            0
        ));
        return true;
    }
}
