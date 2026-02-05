package com.cells.items;

import javax.annotation.Nonnull;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cells.core.CellsCreativeTab;

import com.cells.ItemRegistry;
import com.cells.Tags;


/**
 * Compressed Silicon Prints.
 *
 * Compression levels (metadata):
 * - 0: Compressed (8x base print + redstone)
 * - 1: Double Compressed (8x compressed + redstone)
 * - 2: Triple Compressed (8x double compressed + redstone)
 * - 3: Quadruple Compressed (8x triple compressed = 4096x base print + 585x redstone)
 *
 * Higher compression levels can be added in future versions.
 */
public class ItemCompressedSiliconPrint extends Item {

    public static final int COMPRESSED = 0;
    public static final int DOUBLE_COMPRESSED = 1;
    public static final int TRIPLE_COMPRESSED = 2;
    public static final int QUADRUPLE_COMPRESSED = 3;

    private static final String[] LEVEL_NAMES = {"compressed", "double_compressed", "triple_compressed", "quadruple_compressed"};

    public ItemCompressedSiliconPrint() {
        setMaxStackSize(64);
        setHasSubtypes(true);
        setMaxDamage(0);
        setCreativeTab(CellsCreativeTab.instance);
        setRegistryName(Tags.MODID, "compressed_silicon_print");
        setTranslationKey(Tags.MODID + ".compressed_silicon_print");
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        int meta = stack.getMetadata();

        if (meta >= 0 && meta < LEVEL_NAMES.length) {
            return getTranslationKey() + "." + LEVEL_NAMES[meta];
        }

        return getTranslationKey();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(@Nonnull CreativeTabs tab, @Nonnull NonNullList<ItemStack> items) {
        if (!isInCreativeTab(tab)) return;

        for (int level = 0; level < LEVEL_NAMES.length; level++) items.add(new ItemStack(this, 1, level));
    }

    /**
     * Create a compressed silicon print ItemStack.
     * @param level COMPRESSED, DOUBLE_COMPRESSED, TRIPLE_COMPRESSED, or QUADRUPLE_COMPRESSED
     */
    public static ItemStack create(int level) {
        if (ItemRegistry.COMPRESSED_SILICON_PRINT == null) return ItemStack.EMPTY;
        if (level < 0 || level >= LEVEL_NAMES.length) level = 0;

        return new ItemStack(ItemRegistry.COMPRESSED_SILICON_PRINT, 1, level);
    }

    /**
     * Get the level names for model registration.
     */
    public static String[] getLevelNames() {
        return LEVEL_NAMES;
    }
}
