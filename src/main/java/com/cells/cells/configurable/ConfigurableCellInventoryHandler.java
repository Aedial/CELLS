package com.cells.cells.configurable;

import appeng.api.storage.IMEInventory;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;

import com.cells.cells.common.AbstractCellInventoryHandler;


/**
 * Inventory handler wrapper for configurable cells.
 * <p>
 * Uses the generic base class since configurable cells support multiple channel types.
 */
public class ConfigurableCellInventoryHandler<T extends IAEStack<T>> extends AbstractCellInventoryHandler<T> {

    public ConfigurableCellInventoryHandler(IMEInventory<T> inventory, IStorageChannel<T> channel) {
        super(inventory, channel);
    }
}
