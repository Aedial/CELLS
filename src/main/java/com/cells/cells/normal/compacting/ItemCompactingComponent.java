package com.cells.cells.normal.compacting;

import javax.annotation.Nonnull;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.cells.core.CellsCreativeTab;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cells.ItemRegistry;
import com.cells.Tags;


/**
 * Compacting storage component item.
 * Used to craft compacting storage cells.
 * <p>
 * These are crafted from regular AE2 storage components.
 * <p>
 * Uses sub-items for different capacity tiers:
 * - 0: 1k Compacting Component
 * - 1: 4k Compacting Component
 * - 2: 16k Compacting Component
 * - 3: 64k Compacting Component
 * - 4: 256k Compacting Component
 * - 5: 1M Compacting Component
 * - 6: 4M Compacting Component
 * - 7: 16M Compacting Component
 * - 8: 64M Compacting Component
 * - 9: 256M Compacting Component
 * - 10: 1G Compacting Component
 * - 11: 2G Compacting Component
 */
public class ItemCompactingComponent extends Item {

    private static final String[] TIER_NAMES = {
        "1k", "4k", "16k", "64k",
        "256k", "1m", "4m", "16m",
        "64m", "256m", "1g", "2g"
    };

    public ItemCompactingComponent() {
        setMaxStackSize(64);
        setHasSubtypes(true);
        setMaxDamage(0);
        setCreativeTab(CellsCreativeTab.instance);
        setRegistryName(Tags.MODID, "compacting_component");
        setTranslationKey(Tags.MODID + ".compacting_component");
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
     * @param tier 0=1k, 1=4k, ... 11=2G
     */
    public static ItemStack create(int tier) {
        if (tier < 0 || tier >= TIER_NAMES.length) tier = 0;

        return new ItemStack(ItemRegistry.COMPACTING_COMPONENT, 1, tier);
    }

    /**
     * Get the tier names for model registration.
     */
    public static String[] getTierNames() {
        return TIER_NAMES;
    }
}
