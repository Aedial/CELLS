package com.cells.integration.mekanismenergistics;

import java.util.Map;

import com.mekeng.github.common.me.data.IAEGasStack;


/**
 * Interface for containers that can receive gas slot updates.
 * Similar to AE2's IFluidSyncContainer but for gases.
 */
public interface IGasSyncContainer {

    /**
     * Called when gas slot updates are received from the server (on client)
     * or from the client (on server for filter changes).
     *
     * @param gases Map of slot index to gas stack (null to clear)
     */
    void receiveGasSlots(Map<Integer, IAEGasStack> gases);
}
