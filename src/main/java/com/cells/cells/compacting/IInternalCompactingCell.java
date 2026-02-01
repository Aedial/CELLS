package com.cells.cells.compacting;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

import appeng.api.storage.ICellWorkbenchItem;
import appeng.api.storage.data.IAEItemStack;

import com.cells.api.IItemCompactingCell;


/**
 * Internal interface for compacting storage cell items.
 * Extends the public API interface with implementation-specific methods.
 */
public interface IInternalCompactingCell extends IItemCompactingCell, ICellWorkbenchItem {

    /**
     * Get the total byte capacity of this cell.
     */
    long getBytes(@Nonnull ItemStack cellItem);

    /**
     * Get the bytes used per stored type.
     */
    long getBytesPerType(@Nonnull ItemStack cellItem);

    /**
     * Get the idle power drain of this cell.
     */
    double getIdleDrain();

    /**
     * Check if the given item is blacklisted from this cell.
     */
    boolean isBlackListed(@Nonnull ItemStack cellItem, @Nonnull IAEItemStack requestedAddition);

    /**
     * Check if this cell can be stored inside other storage cells.
     */
    boolean storableInStorageCell();
}
