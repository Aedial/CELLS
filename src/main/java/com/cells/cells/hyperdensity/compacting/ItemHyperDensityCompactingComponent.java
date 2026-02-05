package com.cells.cells.hyperdensity.compacting;

import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cells.core.CellsCreativeTab;

import com.cells.Tags;


/**
 * Hyper-Density Compacting storage component item.
 * Used to craft hyper-density compacting storage cells.
 * 
 * Limited to 16M tier maximum due to overflow concerns.
 * 
 * Uses sub-items for different capacity tiers:
 * - 0: 1k HD Compacting Component
 * - 1: 4k HD Compacting Component
 * - 2: 16k HD Compacting Component
 * - 3: 64k HD Compacting Component
 * - 4: 256k HD Compacting Component
 * - 5: 1M HD Compacting Component
 * - 6: 4M HD Compacting Component
 * - 7: 16M HD Compacting Component
 */
public class ItemHyperDensityCompactingComponent extends Item {

    private static final String[] TIER_NAMES = {
        "1k", "4k", "16k", "64k",
        "256k", "1m", "4m", "16m",
        "64m", "256m", "1g"
    };

    public ItemHyperDensityCompactingComponent() {
        setMaxStackSize(64);
        setHasSubtypes(true);
        setMaxDamage(0);
        setCreativeTab(CellsCreativeTab.instance);
        setRegistryName(Tags.MODID, "hyper_density_compacting_component");
        setTranslationKey(Tags.MODID + ".hyper_density_compacting_component");
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

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add("\u00a7d" + I18n.format("tooltip.cells.hyper_density_component.info"));
    }

    /**
     * Create a component ItemStack for the given tier.
     * @param tier 0=1k, 1=4k, ... 7=16M
     */
    public static ItemStack create(int tier) {
        if (tier < 0 || tier >= TIER_NAMES.length) tier = 0;

        return new ItemStack(com.cells.ItemRegistry.HYPER_DENSITY_COMPACTING_COMPONENT, 1, tier);
    }

    /**
     * Get the tier names for model registration.
     */
    public static String[] getTierNames() {
        return TIER_NAMES;
    }
}
