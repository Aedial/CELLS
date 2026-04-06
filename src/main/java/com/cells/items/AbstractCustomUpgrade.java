package com.cells.items;

import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;

import com.cells.Tags;
import com.cells.core.CellsCreativeTab;


/**
 * Abstract base class for custom upgrade cards that integrate with AE2's upgrade system.
 * <p>
 * Implements {@link IUpgradeModule} to integrate with AE2's upgrade system,
 * but returns null for the upgrade type since it's a custom upgrade not in
 * the standard {@link Upgrades} enum.
 * </p>
 */
public class AbstractCustomUpgrade extends Item implements IUpgradeModule {

    public final int[] tierValues;
    public final String[] tierNames;

    public AbstractCustomUpgrade(String name) {
        setRegistryName(Tags.MODID, name);
        setTranslationKey(Tags.MODID + "." + name);
        setMaxStackSize(64);
        setCreativeTab(CellsCreativeTab.instance);

        tierValues = new int[0];
        tierNames = new String[0];
    }

    public AbstractCustomUpgrade(String name, int[] tierValues, String[] tierNames) {
        setRegistryName(Tags.MODID, name);
        setTranslationKey(Tags.MODID + "." + name);
        setMaxStackSize(64);
        setHasSubtypes(true);
        setMaxDamage(0);
        setCreativeTab(CellsCreativeTab.instance);

        if (tierValues.length != tierNames.length) {
            throw new IllegalArgumentException("Tier values and names arrays must have the same length");
        }

        this.tierValues = tierValues;
        this.tierNames = tierNames;
    }

    public static void setIntKey(ItemStack stack, String key, int value, int min) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }

        tag.setInteger(key, Math.max(min, value));
    }

    public static int getIntKey(ItemStack stack, String key, int defaultValue) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(key)) return defaultValue;

        return tag.getInteger(key);
    }

    /**
     * Helper method to add compatible types to the tooltip.
     *
     * @param tooltip         The tooltip list to add to
     * @param compatibleTypes The compatible type keys (without "tooltip.cells.type." prefix)
     */
    public void addCompatibilityTooltip(@Nonnull List<String> tooltip, String... compatibleTypes) {
        if (compatibleTypes.length == 0) {
            throw new IllegalArgumentException("At least one compatible type must be provided");
        }

        for (int i = 0; i < compatibleTypes.length; i++) {
            String typeKey = compatibleTypes[i];
            if (typeKey == null || typeKey.isEmpty() || typeKey.startsWith("tooltip")) {
                throw new IllegalArgumentException("Invalid compatible type key: " + typeKey);
            }
            compatibleTypes[i] = I18n.format("tooltip.cells.type." + compatibleTypes[i]);
        }

        String compatibleTypesStr = String.join(
            I18n.format("tooltip.cells.card.separator"), compatibleTypes);

        tooltip.add("");
        tooltip.add("§8" + I18n.format("tooltip.cells.card.compatible", compatibleTypesStr));
    }

    @Override
    @Nonnull
    public String getTranslationKey(@Nonnull ItemStack stack) {
        if (tierNames.length == 0) return getTranslationKey();

        int meta = stack.getMetadata();
        if (meta >= 0 && meta < tierNames.length) return getTranslationKey() + "." + tierNames[meta];

        return getTranslationKey();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(@Nonnull CreativeTabs tab, @Nonnull NonNullList<ItemStack> items) {
        if (!isInCreativeTab(tab)) return;

        // Items with no tiers add a single stack
        if (tierValues.length == 0) {
            items.add(new ItemStack(this, 1, 0));
            return;
        }

        // Items with tiers add one stack per tier
        for (int i = 0; i < tierValues.length; i++) items.add(new ItemStack(this, 1, i));
    }

    /**
     * Gets the type limit value for this card variant.
     *
     * @param stack The card ItemStack
     * @return The type limit (1, 2, 4, 8, 16, 32, or 63)
     */
    public int getTierValue(ItemStack stack) {
        if (tierValues.length == 0) return 0;

        int meta = stack.getMetadata();
        if (meta >= 0 && meta < tierValues.length) return tierValues[meta];

        return tierValues[0];
    }

    /**
     * Get the tier names for model registration.
     */
    public String[] getTierNames() {
        return tierNames;
    }

    /**
     * Returns the AE2 upgrade type for this card.
     * <p>
     * Returns {@link Upgrades#QUANTUM_LINK} as a placeholder to pass slot validation
     * in GUI systems. The actual filtering is handled by the Import Interface's
     * upgrade inventory filter which checks for this specific item class.
     * </p>
     *
     * @param itemstack The upgrade item stack
     * @return {@link Upgrades#QUANTUM_LINK} as a placeholder for slot validation
     */
    @Override
    public Upgrades getType(ItemStack itemstack) {
        return Upgrades.QUANTUM_LINK;
    }
}
