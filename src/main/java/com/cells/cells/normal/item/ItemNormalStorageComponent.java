package com.cells.cells.normal.item;

import javax.annotation.Nonnull;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.core.CreativeTab;

import com.cells.ItemRegistry;
import com.cells.Tags;


/**
 * Normal storage component item for tiers beyond NAE2's 16M cap.
 * Provides 64M, 256M, 1G, 2G storage components.
 * 
 * These components are used to craft normal storage cells.
 */
public class ItemNormalStorageComponent extends Item {

    private static final String[] TIER_NAMES = {"65536k", "262144k", "1048576k", "2097152k"};

    // NAE2 modid for localization/texture namespace
    private static final String NAE2_MODID = "nae2";

    public ItemNormalStorageComponent() {
        setMaxStackSize(64);
        setHasSubtypes(true);
        setMaxDamage(0);
        setCreativeTab(CreativeTab.instance);
        setRegistryName(NAE2_MODID, "material_cell_part_extended");
        setTranslationKey(NAE2_MODID + ".material.cell_part");
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        int meta = stack.getMetadata();
        if (meta >= 0 && meta < TIER_NAMES.length) return getTranslationKey() + "." + TIER_NAMES[meta];

        return getTranslationKey();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(@Nonnull CreativeTabs tab, @Nonnull NonNullList<ItemStack> items) {
        if (!isInCreativeTab(tab)) return;

        for (int i = 0; i < TIER_NAMES.length; i++) items.add(new ItemStack(this, 1, i));
    }

    /**
     * Create a component ItemStack for the given tier.
     * @param tier 0=64M, 1=256M, 2=1G, 3=2G
     */
    public static ItemStack create(int tier) {
        if (tier < 0 || tier >= TIER_NAMES.length) tier = 0;

        return new ItemStack(ItemRegistry.NORMAL_STORAGE_COMPONENT, 1, tier);
    }

    /**
     * Get the tier names for model registration.
     */
    public static String[] getTierNames() {
        return TIER_NAMES;
    }
}
