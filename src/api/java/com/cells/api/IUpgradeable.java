package com.cells.api;

import javax.annotation.Nonnull;

import net.minecraftforge.items.IItemHandler;


/**
 * API contract for CELLS devices with a mutable upgrade inventory.
 */
public interface IUpgradeable {

    /**
     * Upgrade inventory exposed by this device.
     */
    @Nonnull
    IItemHandler getUpgradeInventory();
}