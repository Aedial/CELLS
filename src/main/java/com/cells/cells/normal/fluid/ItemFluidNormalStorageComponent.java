package com.cells.cells.normal.fluid;

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
 * Normal fluid storage component item for large capacity tiers.
 * Provides 64M, 256M, 1G, 2G fluid storage components.
 * 
 * These components are used to craft normal fluid storage cells.
 */
public class ItemFluidNormalStorageComponent extends Item {

    private static final String[] TIER_NAMES = {"65536k", "262144k", "1048576k", "2097152k"};

    public ItemFluidNormalStorageComponent() {
        setMaxStackSize(64);
        setHasSubtypes(true);
        setMaxDamage(0);
        setCreativeTab(CreativeTab.instance);
        setRegistryName(Tags.MODID, "fluid_normal_storage_component");
        setTranslationKey(Tags.MODID + ".fluid_normal_storage_component");
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

        return new ItemStack(ItemRegistry.FLUID_NORMAL_STORAGE_COMPONENT, 1, tier);
    }

    /**
     * Get the tier names for model registration.
     */
    public static String[] getTierNames() {
        return TIER_NAMES;
    }
}
