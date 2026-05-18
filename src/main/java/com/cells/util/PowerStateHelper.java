package com.cells.util;

import appeng.api.networking.IGridNode;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;


/**
 * Shared helpers for querying AE2 proxy power and channel state.
 */
public final class PowerStateHelper {

    private PowerStateHelper() {
    }

    public static boolean isPowered(AENetworkProxy proxy) {
        try {
            return proxy.getEnergy().isNetworkPowered();
        } catch (GridAccessException e) {
            return false;
        }
    }

    public static boolean hasChannel(AENetworkProxy proxy) {
        IGridNode node = proxy.getNode();
        return node != null && node.meetsChannelRequirements();
    }
}