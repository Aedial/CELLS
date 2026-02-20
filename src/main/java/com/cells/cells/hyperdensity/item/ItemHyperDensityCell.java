package com.cells.cells.hyperdensity.item;

import net.minecraft.item.ItemStack;

import com.cells.ItemRegistry;
import com.cells.Tags;


/**
 * Hyper-Density storage cell item.
 * <p>
 * These cells display standard capacity values (1k, 4k, etc.) but internally
 * multiply storage by ~2 billion, allowing massive item storage.
 * <p>
 * Uses sub-items for different capacity tiers (max 1G due to 8 items/byte * multiplier overflow):
 * - 0: 1k HD (displays 1k, actually 1k * 2.1B = ~2.1T bytes)
 * - 1: 4k HD (displays 4k, actually ~8.6T bytes)
 * - 2: 16k HD (displays 16k, actually ~34T bytes)
 * - 3: 64k HD (displays 64k, actually ~137T bytes)
 * - 4: 256k HD (displays 256k, actually ~549T bytes)
 * - 5: 1M HD (displays 1M, actually ~2.1Q bytes)
 * - 6: 4M HD (displays 4M, actually ~8.6Q bytes)
 * - 7: 16M HD (displays 16M, actually ~34Q bytes)
 * - 8: 64M HD (displays 64M, actually ~137Q bytes)
 * - 9: 256M HD (displays 256M, actually ~549Q bytes)
 * - 10: 1G HD (displays 1G, actually ~2.1QQ bytes - near Long.MAX_VALUE / 8)
 */
public class ItemHyperDensityCell extends ItemHyperDensityCellBase {

    private static final String[] TIER_NAMES = {
        "1k", "4k", "16k", "64k",
        "256k", "1m", "4m", "16m",
        "64m", "256m", "1g"
    };

    // Display bytes - what the user sees
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

    public ItemHyperDensityCell() {
        super(TIER_NAMES, DISPLAY_BYTES);
        setRegistryName(Tags.MODID, "hyper_density_cell");
        setTranslationKey(Tags.MODID + ".hyper_density_cell");
    }

    @Override
    protected ItemStack getCellComponent(int tier) {
        // Hyper-density cells use hyper-density components
        return ItemHyperDensityComponent.create(tier);
    }

    /**
     * Create a cell ItemStack for the given tier.
     * @param tier 0=1k, 1=4k, 2=16k, 3=64k, 4=256k, 5=1M, 6=4M, 7=16M, 8=64M, 9=256M, 10=1G
     */
    public static ItemStack create(int tier) {
        if (tier < 0 || tier >= TIER_NAMES.length) tier = 0;

        return new ItemStack(ItemRegistry.HYPER_DENSITY_CELL, 1, tier);
    }

    /**
     * Get the tier names for model registration.
     */
    public static String[] getTierNames() {
        return TIER_NAMES;
    }

    /**
     * Get the number of tiers available.
     */
    public static int getTierCount() {
        return TIER_NAMES.length;
    }
}
