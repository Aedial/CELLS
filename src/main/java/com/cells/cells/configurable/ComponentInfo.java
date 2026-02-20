package com.cells.cells.configurable;

import com.cells.config.CellsConfig;


/**
 * Immutable data class holding the properties of a recognized ME Storage Component.
 * Determines the base capacity and storage channel of a Configurable Storage Cell.
 */
public final class ComponentInfo {

    private final long bytes;
    private final boolean isFluid;
    private final String tierName;

    public ComponentInfo(long bytes, boolean isFluid, String tierName) {
        this.bytes = bytes;
        this.isFluid = isFluid;
        this.tierName = tierName;
    }

    /** Total byte capacity of the component. */
    public long getBytes() {
        return bytes;
    }

    /** Bytes consumed per stored type (overhead). */
    public long getBytesPerType() {
        return bytes / 2 / CellsConfig.configurableCellMaxTypes;
    }

    /** Whether this component stores fluids (true) or items (false). */
    public boolean isFluid() {
        return isFluid;
    }

    /**
     * Tier name for texture/model selection (e.g., "1k", "64k", "1g").
     * Also used in the tooltip display.
     */
    public String getTierName() {
        return tierName;
    }
}
