package com.cells.integration.thaumicenergistics;

import thaumcraft.api.aspects.Aspect;


/**
 * Interface for receiving essentia storage change notifications.
 * <p>
 * This allows essentia containers (like our Essentia Import/Export Interfaces) to
 * notify connected Essentia Storage Buses about content changes without triggering
 * a full network refresh (MENetworkCellArrayUpdate).
 * <p>
 * The storage bus can then use IStorageGrid.postAlterationOfStoredItems() to
 * selectively notify the network about specific changes.
 */
public interface IEssentiaStorageChangeListener {

    /**
     * Called when an essentia container's content changes.
     * <p>
     * The listener should use this to post delta changes to the ME network
     * via IStorageGrid.postAlterationOfStoredItems() instead of triggering
     * a full cell array update.
     *
     * @param aspect The aspect type that changed
     * @param delta  The change amount (positive for addition, negative for removal)
     */
    void onEssentiaChanged(Aspect aspect, int delta);

    /**
     * Called when the essentia container's max capacity changes.
     * <p>
     * This may require a handler refresh to update available space calculations.
     */
    void onCapacityChanged();
}
