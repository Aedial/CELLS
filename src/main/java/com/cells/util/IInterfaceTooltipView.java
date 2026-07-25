package com.cells.util;


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
}