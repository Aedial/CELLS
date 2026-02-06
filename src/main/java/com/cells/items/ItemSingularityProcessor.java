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
 * Singularity Processors - used to craft Hyper-Density storage components.
 *
 * Created in the Inscriber with:
 * - Top: Quadruple Compressed Processor Print (4x compression)
 * - Middle: Singularity
 * - Bottom: Quadruple Compressed Silicon Print (4x compression)
 *
 * Types:
 * - 0: Singularity Calculation Processor
 * - 1: Singularity Engineering Processor
 * - 2: Singularity Logic Processor
 */
public class ItemSingularityProcessor extends Item {

    public static final int CALCULATION = 0;
    public static final int ENGINEERING = 1;
    public static final int LOGIC = 2;

    private static final String[] TYPE_NAMES = {"calculation", "engineering", "logic"};

    public ItemSingularityProcessor() {
        setMaxStackSize(64);
        setHasSubtypes(true);
        setMaxDamage(0);
        setCreativeTab(CellsCreativeTab.instance);
        setRegistryName(Tags.MODID, "singularity_processor");
        setTranslationKey(Tags.MODID + ".singularity_processor");
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        int meta = stack.getMetadata();
        if (meta >= 0 && meta < TYPE_NAMES.length) {
            return getTranslationKey() + "." + TYPE_NAMES[meta];
        }

        return getTranslationKey();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(@Nonnull CreativeTabs tab, @Nonnull NonNullList<ItemStack> items) {
        if (!isInCreativeTab(tab)) return;

        for (int i = 0; i < TYPE_NAMES.length; i++) {
            items.add(new ItemStack(this, 1, i));
        }
    }

    /**
     * Create a singularity processor ItemStack.
     * @param type CALCULATION, ENGINEERING, or LOGIC
     */
    public static ItemStack create(int type) {
        if (ItemRegistry.SINGULARITY_PROCESSOR == null) return ItemStack.EMPTY;
        if (type < 0 || type >= TYPE_NAMES.length) type = 0;

        return new ItemStack(ItemRegistry.SINGULARITY_PROCESSOR, 1, type);
    }

    /**
     * Get the type names for model registration.
     */
    public static String[] getTypeNames() {
        return TYPE_NAMES;
    }
}
