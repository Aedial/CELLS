package com.cells.cells.compacting;

import net.minecraft.item.ItemStack;

import com.cells.ItemRegistry;
import com.cells.Tags;


/**
 * Compacting storage cell item.
 * Stores a single item type and exposes compressed/decompressed forms to the network.
 * 
 * Uses sub-items for different capacity tiers:
 * - 0: 1k (1,024 bytes)
 * - 1: 4k (4,096 bytes)
 * - 2: 16k (16,384 bytes)
 * - 3: 64k (65,536 bytes)
 * - 4: 256k (262,144 bytes)
 * - 5: 1M (1,048,576 bytes)
 * - 6: 4M (4,194,304 bytes)
 * - 7: 16M (16,777,216 bytes)
 * - 8: 64M (67,108,864 bytes)
 * - 9: 256M (268,435,456 bytes)
 * - 10: 1G (1,073,741,824 bytes)
 * - 11: 2G (2,147,483,648 bytes)
 */
public class ItemCompactingCell extends ItemCompactingCellBase {

    private static final String[] TIER_NAMES = {
        "1k", "4k", "16k", "64k",           // Standard AE2 tiers
        "256k", "1m", "4m", "16m",          // Dense tiers (NAE2-equivalent)
        "64m", "256m", "1g", "2g"           // Ultra-dense tiers
    };

    private static final long[] TIER_BYTES = {
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
        1073741824L,    // 1G
        2147483648L     // 2G
    };

    private static final long[] BYTES_PER_TYPE = {
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
        8388608L,       // 1G
        16777216L       // 2G
    };

    public ItemCompactingCell() {
        super(TIER_NAMES, TIER_BYTES, BYTES_PER_TYPE);
        setRegistryName(Tags.MODID, "compacting_cell");
        setTranslationKey(Tags.MODID + ".compacting_cell");
    }

    @Override
    protected ItemStack getCellComponent(int tier) {
        // All tiers now have components (1k-2G)
        return ItemCompactingComponent.create(tier);
    }

    /**
     * Create a cell ItemStack for the given tier.
     * @param tier 0=1k, 1=4k, 2=16k, 3=64k, 4=256k, 5=1M, 6=4M, 7=16M, 8=64M, 9=256M, 10=1G, 11=2G
     */
    public static ItemStack create(int tier) {
        if (tier < 0 || tier >= TIER_NAMES.length) tier = 0;

        return new ItemStack(ItemRegistry.COMPACTING_CELL, 1, tier);
    }

    /**
     * Get the tier names for model registration.
     */
    public static String[] getTierNames() {
        return TIER_NAMES;
    }
}
