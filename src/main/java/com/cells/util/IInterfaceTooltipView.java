package com.cells.util;

import java.util.Collection;

import net.minecraft.util.EnumFacing;


/**
 * Internal tooltip bridge for interface API views.
 */
public interface IInterfaceTooltipView {

    /**
     * Get the polling rate for the interface, in ticks. Used for tooltip display purposes only.
     */
    int getTooltipPollingRate();

    /**
     * Get the default transfer quantity for each slot of the interface. Used for tooltip display purposes only.
     */
    long getTooltipTransferQuantity();

    /**
     * Get the adjacent facings that currently resolve to valid auto-transfer targets.
     * Used for tooltip display purposes only.
     */
    Collection<EnumFacing> getTooltipAutoTransferFacings();
}