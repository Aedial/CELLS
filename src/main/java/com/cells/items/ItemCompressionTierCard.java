package com.cells.items;

import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import com.cells.core.CellsCreativeTab;

import com.cells.ItemRegistry;
import com.cells.Tags;


/**
 * Compression Tier Card - an upgrade for Compacting Storage Cells.
 * <p>
 * When installed in a Compacting Cell's upgrade slot, increases the number
 * of compression tiers available UPWARD (toward more compressed forms).
 * </p>
 * <p>
 * Available in variants: 3x, 6x, 9x, 12x, 15x
 * <ul>
 *   <li>3x: Up to 3 compression tiers (e.g., nugget → ingot → block → double block)</li>
 *   <li>6x: Up to 6 compression tiers</li>
 *   <li>9x: Up to 9 compression tiers</li>
 *   <li>12x: Up to 12 compression tiers</li>
 *   <li>15x: Up to 15 compression tiers</li>
 * </ul>
 * </p>
 * <p>
 * Note: Only one compression direction card can be installed at a time.
 * This card expands compression upward; use {@link ItemDecompressionTierCard}
 * for expanding downward.
 * </p>
 * <p>
 * <b>Compatibility:</b> Only works with Compacting cells (normal and Hyper-Density).
 * </p>
 */
public class ItemCompressionTierCard extends Item implements IUpgradeModule {

    /**
     * Tier values representing the number of compression tiers available.
     * Metadata 0-4 maps to 3, 6, 9, 12, 15.
     */
    public static final int[] TIER_VALUES = {3, 6, 9, 12, 15};

    private static final String[] TIER_NAMES = {"3x", "6x", "9x", "12x", "15x"};

    public ItemCompressionTierCard() {
        setRegistryName(Tags.MODID, "compression_tier_card");
        setTranslationKey(Tags.MODID + ".compression_tier_card");
        setMaxStackSize(64);
        setHasSubtypes(true);
        setMaxDamage(0);
        setCreativeTab(CellsCreativeTab.instance);
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        int meta = stack.getMetadata();
        if (meta >= 0 && meta < TIER_NAMES.length) return getTranslationKey() + "." + TIER_NAMES[meta];

        return getTranslationKey();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (!isInCreativeTab(tab)) return;

        for (int i = 0; i < TIER_NAMES.length; i++) items.add(new ItemStack(this, 1, i));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        int tier = getTierValue(stack);
        tooltip.add("\u00a77" + I18n.format("tooltip.cells.compression_tier_card.desc", tier));
        tooltip.add("");

        String compatibleTypes = String.join(
            I18n.format("tooltip.cells.card.separator"),
            I18n.format("tooltip.cells.type.compact"),
            I18n.format("tooltip.cells.type.hyperdensity_compact")
        );
        tooltip.add("\u00a78" + I18n.format("tooltip.cells.card.compatible", compatibleTypes));
    }

    /**
     * Gets the tier count value for this card variant.
     *
     * @param stack The card ItemStack
     * @return The number of compression tiers (3, 6, 9, 12, or 15)
     */
    public static int getTierValue(ItemStack stack) {
        int meta = stack.getMetadata();
        if (meta >= 0 && meta < TIER_VALUES.length) return TIER_VALUES[meta];

        return TIER_VALUES[0];
    }

    /**
     * Returns the AE2 upgrade type for this card.
     * <p>
     * Returns {@link Upgrades#QUANTUM_LINK} as a placeholder to pass slot validation
     * in the Cell Workbench GUI. The actual filtering and limits are handled by
     * {@link com.cells.util.CustomCellUpgrades} which checks for this specific
     * item class.
     * </p>
     *
     * @param itemstack The upgrade item stack
     * @return {@link Upgrades#QUANTUM_LINK} as a placeholder for slot validation
     */
    @Override
    public Upgrades getType(ItemStack itemstack) {
        return Upgrades.QUANTUM_LINK;
    }

    /**
     * Creates a Compression Tier Card for the given tier.
     *
     * @param tier 0=3x, 1=6x, 2=9x, 3=12x, 4=15x
     * @return The card ItemStack
     */
    public static ItemStack create(int tier) {
        if (tier < 0 || tier >= TIER_NAMES.length) tier = 0;

        return new ItemStack(ItemRegistry.COMPRESSION_TIER_CARD, 1, tier);
    }

    /**
     * Get the tier names for model registration.
     */
    public static String[] getTierNames() {
        return TIER_NAMES;
    }
}
