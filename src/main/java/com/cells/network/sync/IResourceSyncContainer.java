package com.cells.network.sync;

import java.util.Map;


/**
 * Unified interface for containers that can receive resource slot updates.
 * <p>
 * Replaces the separate IFluidSyncContainer and IGasSyncContainer interfaces
 * with a single unified interface that handles ALL resource types.
 * <p>
 * Containers implementing this interface can receive updates for any resource type
 * (items, fluids, gases, essentia) through a single method.
 */
public interface IResourceSyncContainer {

    /**
     * Called when resource slot updates are received.
     * <p>
     * On client: updates come from server (filter sync).
     * On server: updates come from client (filter changes from GUI).
     *
     * @param type      The type of resource being synced
     * @param resources Map of slot index to resource (null values indicate clearing)
     */
    void receiveResourceSlots(ResourceType type, Map<Integer, Object> resources);
}
