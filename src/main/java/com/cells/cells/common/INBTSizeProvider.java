package com.cells.cells.common;


/**
 * Interface for cell inventories that track NBT size.
 * <p>
 * Implemented by all custom cell inventory types to provide
 * NBT size information for tooltip display and warnings.
 */
public interface INBTSizeProvider {

    /**
     * Get the total NBT size of all stored items/fluids in bytes.
     * Used for tooltip display and warning when approaching limits.
     *
     * @return Total NBT size in bytes
     */
    int getTotalNbtSize();
}
