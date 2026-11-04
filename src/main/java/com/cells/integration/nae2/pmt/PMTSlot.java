package com.cells.integration.nae2.pmt;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.container.slot.SlotRestrictedInput;
import appeng.util.Platform;


/**
 * Slot for Pattern Multi-Tool patterns.
 * <p>
 * Features:
 * - Only accepts patterns (blank or encoded)
 * - Slots are enabled/disabled based on capacity upgrades in the PMT
 * - getDisplayStack() returns the crafting output instead of the pattern item
 * - groupNum determines which column the slot belongs to (0 = always enabled, 1-3 require upgrades)
 */
public class PMTSlot extends SlotRestrictedInput {

    private final PMTManager pmtManager;
    private final int groupNum;  // Column index (0-3), determines if slot is enabled based on upgrades

    /**
     * Creates a PMT slot.
     *
     * @param itemHandler The PMT's pattern inventory
     * @param pmtManager The PMT manager for this container
     * @param slotIndex Index within the PMT inventory (0-35)
     * @param x X position in GUI
     * @param y Y position in GUI
     * @param groupNum Column index (0=always enabled, 1-3 require capacity upgrades)
     * @param playerInv Player inventory for slot restrictions
     */
    public PMTSlot(IItemHandler itemHandler, PMTManager pmtManager, int slotIndex, int x, int y,
            int groupNum, InventoryPlayer playerInv) {
        super(PlacableItemType.PATTERN, itemHandler, slotIndex, x, y, playerInv);
        this.pmtManager = pmtManager;
        this.groupNum = groupNum;
        this.setReturnAsSingleStack(false);
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        if (!isSlotEnabled()) return false;
        if (stack.isEmpty()) return false;

        // Accept blank patterns
        if (AEApi.instance().definitions().materials().blankPattern().isSameAs(stack)) {
            return true;
        }

        // Accept encoded patterns
        return stack.getItem() instanceof ICraftingPatternItem;
    }

    /**
     * Gets the display stack - shows the crafting output instead of the encoded pattern.
     * This makes it easier to see what each pattern does at a glance.
     * <p>
     * Called by AE2's rendering system when isDisplay() returns true.
     */
    @Override
    public ItemStack getDisplayStack() {
        if (!Platform.isClient()) return super.getStack();
        if (pmtManager == null) return super.getStack();

        return pmtManager.getPatternRenderData(this.getSlotIndex()).getDisplayStack();
    }

    /**
     * Gets the count for the output display.
     * Used to render the small number in the corner showing how many items the pattern produces.
     */
    public long getOutputCount() {
        if (pmtManager == null) return 0;

        return pmtManager.getPatternRenderData(this.getSlotIndex()).getOutputCount();
    }

    @Override
    public boolean isSlotEnabled() {
        if (pmtManager == null) return false;

        return pmtManager.isColumnEnabled(groupNum);
    }

    @Override
    public boolean canTakeStack(EntityPlayer player) {
        return isSlotEnabled() && super.canTakeStack(player);
    }

    /**
     * Override to prevent AE2's red "invalid" overlay from appearing.
     * PMT slots handle their own rendering - we don't want AE2's validation overlay.
     */
    @Override
    public hasCalculatedValidness getIsValid() {
        // Always return Valid to prevent red overlay
        return hasCalculatedValidness.Valid;
    }

    /**
     * Gets the column number (0-3) for this slot.
     */
    public int getGroupNum() {
        return groupNum;
    }

    /**
     * Gets the PMT manager for this slot.
     */
    public PMTManager getPMTManager() {
        return pmtManager;
    }
}
