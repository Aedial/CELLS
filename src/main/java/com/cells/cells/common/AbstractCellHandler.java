package com.cells.cells.common;

import net.minecraft.item.ItemStack;

import appeng.api.storage.ICellHandler;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;


/**
 * Abstract base for cell handlers.
 * <p>
 * Cell handlers are registered with AE2's cell registry and are responsible for:
 * - Identifying whether an ItemStack is a cell of this type
 * - Creating the cell inventory and wrapping it in an inventory handler
 * <p>
 * Each cell type (hyper-density, compacting, configurable) has its own handler
 * implementation that specifies the cell interface, expected channel, and
 * how to create the inventory.
 *
 * @param <T> The AE stack type for this cell's channel
 */
public abstract class AbstractCellHandler<T extends IAEStack<T>> implements ICellHandler {

    /**
     * Check if the ItemStack is a cell of this handler's type.
     * Implementations should check for the specific cell interface.
     *
     * @param is The ItemStack to check
     * @return true if this handler can handle the cell
     */
    @Override
    public abstract boolean isCell(ItemStack is);

    /**
     * Get the storage channel this cell type supports.
     * Used to filter out requests for incompatible channels.
     *
     * @return The storage channel class
     */
    protected abstract IStorageChannel<T> getChannel();

    /**
     * Create the cell inventory for this cell.
     *
     * @param is The cell ItemStack
     * @param container The save provider (typically ME Drive)
     * @return The cell inventory, or null if creation fails
     */
    protected abstract ICellInventoryHandler<T> createInventory(ItemStack is, ISaveProvider container);

    @Override
    @SuppressWarnings("unchecked")
    public <S extends IAEStack<S>> ICellInventoryHandler<S> getCellInventory(ItemStack is, ISaveProvider container, IStorageChannel<S> channel) {
        if (!isCell(is)) return null;
        if (channel != getChannel()) return null;

        return (ICellInventoryHandler<S>) createInventory(is, container);
    }
}
