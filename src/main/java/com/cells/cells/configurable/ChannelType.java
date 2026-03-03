package com.cells.cells.configurable;

import com.cells.config.CellsConfig;


/**
 * Enumeration of supported AE2 storage channels for the Configurable Storage Cell.
 * <p>
 * Each channel type corresponds to a different kind of AE2 storage:
 * - ITEM: Standard item storage (AE2 core)
 * - FLUID: Fluid storage (AE2 core)
 * - ESSENTIA: Thaumcraft essentia storage (Thaumic Energistics)
 * - GAS: Mekanism gas storage (Mekanism Energistics)
 */
public enum ChannelType {

    /**
     * Standard item storage (AE2 core).
     * 1 unit per bit (8 items per byte).
     */
    ITEM("item", 1),

    /**
     * Fluid storage (AE2 core).
     * 1000 mB per bit (8000 mB per byte).
     */
    FLUID("fluid", 1000),

    /**
     * Thaumcraft essentia storage (Thaumic Energistics).
     * 1 essentia per bit (8 essentia per byte).
     */
    ESSENTIA("essentia", 1),

    /**
     * Mekanism gas storage (Mekanism Energistics).
     * 4000 mB per bit (32000 mB per byte).
     */
    GAS("gas", 4000);

    private final String configName;

    /**
     * The number of units that can be stored per bit of cell storage.
     * AE2 cells use bytes, with 8 "bits" per byte.
     * - Items: 1 (8 items per byte)
     * - Fluids: 1000 (8000 mB per byte)
     * - Essentia: 1 (8 essentia per byte)
     * - Gas: 4000 (32000 mB per byte)
     */
    private final int countPerBit;

    ChannelType(String configName, int countPerBit) {
        this.configName = configName;
        this.countPerBit = countPerBit;
    }

    /**
     * The name used in the config file to identify this channel type.
     */
    public String getConfigName() {
        return configName;
    }

    /**
     * Parse a channel type from a config string.
     *
     * @param name The channel name from config (case-insensitive)
     * @return The matching ChannelType, or null if not recognized
     */
    public static ChannelType fromConfigName(String name) {
        if (name == null) return null;

        String normalized = name.trim().toLowerCase();
        for (ChannelType type : values()) {
            if (type.configName.equals(normalized)) return type;
        }

        return null;
    }

    /**
     * Check if this channel type requires an optional mod.
     * Item and Fluid are always available (AE2 core).
     * Essentia requires Thaumic Energistics.
     * Gas requires Mekanism Energistics.
     */
    public boolean requiresOptionalMod() {
        return this == ESSENTIA || this == GAS;
    }

    /**
     * Get the number of units that can be stored per bit of cell storage.
     * AE2 cells use bytes, with 8 "bits" per byte.
     * <p>
     * For example:
     * - Items: 1 (8 items per byte)
     * - Fluids: 1000 (8000 mB per byte)
     * - Essentia: 1 (8 essentia per byte)
     * - Gas: 4000 (32000 mB per byte)
     *
     * @return units per bit
     */
    public int getCountPerBit() {
        return countPerBit;
    }

    /**
     * Get the localization suffix for this channel type.
     * Used in gui/tooltip localization keys like "capacity_title.item", "capacity_title.fluid".
     */
    public String getLocalizationSuffix() {
        return configName;
    }

    /**
     * Get the NBT tag key used to store this channel's data.
     */
    public String getNbtTagKey() {
        switch (this) {
            case ITEM:     return "itemType";
            case FLUID:    return "fluidType";
            case ESSENTIA: return "essentiaType";
            case GAS:      return "gasType";
            default:       return "itemType";
        }
    }

    /**
     * Get the model suffix used for textures/models of this channel type.
     * Item channel has no suffix, the rest have their config name as suffix.
     */
    public String getModelSuffix() {
        if (this == ITEM) return "";

        return "_" + configName;
    }

    /**
     * Get the maximum types allowed for configurable cells of this channel type.
     * Each channel type has its own config value.
     *
     * @return The configured max types for this channel type
     */
    public int getMaxTypes() {
        switch (this) {
            case ITEM:     return CellsConfig.configurableCellItemMaxTypes;
            case FLUID:    return CellsConfig.configurableCellFluidMaxTypes;
            case ESSENTIA: return CellsConfig.configurableCellEssentiaMaxTypes;
            case GAS:      return CellsConfig.configurableCellGasMaxTypes;
            default:       return 63;
        }
    }
}
