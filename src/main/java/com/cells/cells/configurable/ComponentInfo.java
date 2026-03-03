package com.cells.cells.configurable;


/**
 * Immutable data class holding the properties of a recognized ME Storage Component.
 * Determines the base capacity and storage channel of a Configurable Storage Cell.
 */
public final class ComponentInfo {

    private final long bytes;
    private final ChannelType channelType;
    private final String tierName;

    public ComponentInfo(long bytes, ChannelType channelType, String tierName) {
        this.bytes = bytes;
        this.channelType = channelType;
        this.tierName = tierName;
    }

    /** Total byte capacity of the component. */
    public long getBytes() {
        return bytes;
    }

    /** Bytes consumed per stored type (overhead). */
    public long getBytesPerType() {
        return bytes / 2 / channelType.getMaxTypes();
    }

    /**
     * The storage channel type of this component.
     */
    public ChannelType getChannelType() {
        return channelType;
    }

    /**
     * Tier name for texture/model selection (e.g., "1k", "64k", "1g").
     * Also used in the tooltip display.
     */
    public String getTierName() {
        return tierName;
    }
}
