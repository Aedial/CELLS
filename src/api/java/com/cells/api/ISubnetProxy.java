package com.cells.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;


/**
 * API contract for CELLS subnet proxies.
 */
public interface ISubnetProxy extends IFilterHost {

    /**
     * Whether this proxy lives on the main-network side of the connection.
     */
    boolean isOutboundConnection();

    /**
     * Stable facing used when a single side must be selected.
     */
    @Nonnull
    EnumFacing getPrimaryFacing();

    /**
     * The grid reached through the proxy.
     */
    @Nullable
    Object getTargetGrid();

    /**
     * Display stack representing the linked peer.
     */
    @Nonnull
    ItemStack getRemoteDisplayStack();
}