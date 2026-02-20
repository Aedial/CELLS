package com.cells.cells.hyperdensity.compacting;

import net.minecraft.item.ItemStack;

import com.cells.ItemRegistry;
import com.cells.Tags;


/**
 * Hyper-Density Compacting storage cell item.
 * <p>
 * Combines compacting functionality with the HD byte multiplier.
 * Limited to 16M tier maximum due to overflow concerns with base unit calculations.
 * <p>
 * Uses sub-items for different capacity tiers:
 * - 0: 1k HD Compacting
 * - 1: 4k HD Compacting
 * - 2: 16k HD Compacting
 * - 3: 64k HD Compacting
 * - 4: 256k HD Compacting
 * - 5: 1M HD Compacting
 * - 6: 4M HD Compacting
 * - 7: 16M HD Compacting (max tier due to overflow safety)
 */
public class ItemHyperDensityCompactingCell extends ItemHyperDensityCompactingCellBase {

    private static final String[] TIER_NAMES = {
        "1k", "4k", "16k", "64k",
        "256k", "1m", "4m", "16m",
        "64m", "256m", "1g"
    };

    private static final long[] DISPLAY_BYTES = {
        1024L,          // 1k
        4096L,          // 4k
        16384L,         // 16k
        65536L,         // 64k
        262144L,        // 256k
        1048576L,       // 1M
        4194304L,       // 4M
        16777216L,      // 16M
        67108864L,      // 64M
        268435456L,     // 256M
        1073741824L     // 1G
    };

    private static final long[] DISPLAY_BYTES_PER_TYPE = {
        8L,             // 1k
        32L,            // 4k
        128L,           // 16k
        512L,           // 64k
        2048L,          // 256k
        8192L,          // 1M
        32768L,         // 4M
        131072L,        // 16M
        524288L,        // 64M
        2097152L,       // 256M
        8388608L        // 1G
    };

    public ItemHyperDensityCompactingCell() {
        super(TIER_NAMES, DISPLAY_BYTES, DISPLAY_BYTES_PER_TYPE);
        setRegistryName(Tags.MODID, "hyper_density_compacting_cell");
        setTranslationKey(Tags.MODID + ".hyper_density_compacting_cell");
    }

    @Override
    protected ItemStack getCellComponent(int tier) {
        return ItemHyperDensityCompactingComponent.create(tier);
    }

    /**
     * Create a cell ItemStack for the given tier.
     * @param tier 0=1k, 1=4k, 2=16k, 3=64k, 4=256k, 5=1M, 6=4M, 7=16M
     */
    public static ItemStack create(int tier) {
        if (tier < 0 || tier >= TIER_NAMES.length) tier = 0;

        return new ItemStack(ItemRegistry.HYPER_DENSITY_COMPACTING_CELL, 1, tier);
    }

    /**
     * Get the tier names for model registration.
     */
    public static String[] getTierNames() {
        return TIER_NAMES;
    }
}
