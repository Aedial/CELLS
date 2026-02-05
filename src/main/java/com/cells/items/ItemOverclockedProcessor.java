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
 * Overclocked Processors - used to craft Compacting storage components.
 *
 * Created in the Inscriber with:
 * - Top: Compressed Processor Print (1x compression)
 * - Middle: Matter Ball
 * - Bottom: Compressed Silicon Print (1x compression)
 *
 * Types:
 * - 0: Overclocked Calculation Processor
 * - 1: Overclocked Engineering Processor
 * - 2: Overclocked Logic Processor
 */
public class ItemOverclockedProcessor extends Item {

    public static final int CALCULATION = 0;
    public static final int ENGINEERING = 1;
    public static final int LOGIC = 2;

    private static final String[] TYPE_NAMES = {"calculation", "engineering", "logic"};

    public ItemOverclockedProcessor() {
        setMaxStackSize(64);
        setHasSubtypes(true);
        setMaxDamage(0);
        setCreativeTab(CellsCreativeTab.instance);
        setRegistryName(Tags.MODID, "overclocked_processor");
        setTranslationKey(Tags.MODID + ".overclocked_processor");
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

        for (int i = 0; i < TYPE_NAMES.length; i++) items.add(new ItemStack(this, 1, i));
    }

    /**
     * Create an overclocked processor ItemStack.
     * @param type CALCULATION, ENGINEERING, or LOGIC
     */
    public static ItemStack create(int type) {
        if (ItemRegistry.OVERCLOCKED_PROCESSOR == null) return ItemStack.EMPTY;
        if (type < 0 || type >= TYPE_NAMES.length) type = 0;

        return new ItemStack(ItemRegistry.OVERCLOCKED_PROCESSOR, 1, type);
    }

    /**
     * Get the type names for model registration.
     */
    public static String[] getTypeNames() {
        return TYPE_NAMES;
    }
}
