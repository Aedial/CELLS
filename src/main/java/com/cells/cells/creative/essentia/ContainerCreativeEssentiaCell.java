package com.cells.cells.creative.essentia;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

import thaumicenergistics.api.EssentiaStack;

import com.cells.cells.creative.AbstractCreativeCellContainer;
import com.cells.gui.QuickAddHelper;


/**
 * Container for the Creative ME Essentia Cell GUI.
 * <p>
 * Provides a 9x7 grid of essentia filter slots.
 * Only accessible in creative mode.
 * <p>
 * Note: Essentia doesn't have a sync system like fluids/gases in AE2,
 * so we read directly from NBT. Changes are saved directly to the cell ItemStack.
 */
public class ContainerCreativeEssentiaCell extends AbstractCreativeCellContainer<CreativeEssentiaCellFilterHandler> {

    public ContainerCreativeEssentiaCell(InventoryPlayer playerInv, EnumHand hand) {
        super(playerInv, hand, new CreativeEssentiaCellFilterHandler(playerInv.player.getHeldItem(hand)));

        // Bind player inventory - start at y=159 to leave room for our custom GUI area
        bindPlayerInventory(playerInv, 0, 159);
    }

    @Override
    protected Class<? extends Item> getCellItemClass() {
        return ItemCreativeEssentiaCell.class;
    }

    /**
     * Set an essentia filter at a specific slot.
     */
    public void setEssentiaFilter(int slot, EssentiaStack essentia) {
        if (slot < 0 || slot >= FILTER_SLOTS) return;

        filterHandler.setEssentiaInSlot(slot, essentia);
    }

    /**
     * Add an essentia filter at the first available slot (for quick-add).
     * Returns true if successful.
     */
    public boolean addToFilter(EssentiaStack essentia) {
        return addToFilterAndGetSlot(essentia) >= 0;
    }

    /**
     * Add an essentia filter at the first available slot (for quick-add).
     * Returns the slot index where it was added, or -1 if no space.
     */
    public int addToFilterAndGetSlot(EssentiaStack essentia) {
        if (essentia == null) return -1;

        // Check if already exists
        if (filterHandler.isInFilter(essentia)) return -1;

        // Find first empty slot
        for (int i = 0; i < FILTER_SLOTS; i++) {
            if (filterHandler.getEssentiaInSlot(i) == null) {
                filterHandler.setEssentiaInSlot(i, essentia);
                return i;
            }
        }

        return -1;
    }

    @Override
    public void clearAllFilters() {
        filterHandler.clearAll();
    }

    /**
     * Handle shift-click: extract essentia from container and add as filter.
     * The actual item stays in place (return empty), only the filter is set.
     */
    @Override
    @Nonnull
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= inventorySlots.size()) return ItemStack.EMPTY;

        Slot slot = inventorySlots.get(slotIndex);
        if (slot == null || !slot.getHasStack()) return ItemStack.EMPTY;

        // Try to extract essentia from the item using QuickAddHelper
        ItemStack clickedStack = slot.getStack();
        EssentiaStack essentia = QuickAddHelper.getEssentiaFromItemStack(clickedStack);
        if (essentia != null) addToFilter(essentia);

        // Return empty so the actual item stays in place
        return ItemStack.EMPTY;
    }
}
