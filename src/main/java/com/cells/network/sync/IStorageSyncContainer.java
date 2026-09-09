package com.cells.network.sync;

import java.util.Map;


/**
 * Interface for containers that can receive storage slot updates from the server.
 * <p>
 * Unlike {@link IResourceSyncContainer} which handles bidirectional filter sync,
 * this interface is for server→client storage sync only. Storage data (resource identity
 * + amount per slot) is pushed to the client via {@link PacketStorageSync} whenever
 * the container's {@code detectAndSendChanges} detects a difference.
 */
public interface IStorageSyncContainer {

    /**
     * Called when storage slot updates are received from the server.
     *
     * @param type      The type of resource being synced
     * @param resources Map of slot index to resource (null values indicate clearing).
     *                  Values are IAEStack instances whose getStackSize() contains the amount.
     */
    void receiveStorageSlots(ResourceType type, Map<Integer, Object> resources);

    default void receiveStorageSlots(ResourceType type, int directionTab, Map<Integer, Object> resources) {
        receiveStorageSlots(type, resources);
    }
}
