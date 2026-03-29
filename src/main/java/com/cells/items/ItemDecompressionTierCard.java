package com.cells.items;

import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Decompression Tier Card - an upgrade for Compacting Storage Cells.
 * <p>
 * When installed in a Compacting Cell's upgrade slot, increases the number
 * of compression tiers available DOWNWARD (toward less compressed forms).
 * </p>
 * <p>
 * Available in variants: 3x, 6x, 9x, 12x, 15x
 * <ul>
 *   <li>3x: Up to 3 decompression tiers (e.g., block → ingot → nugget → mini nugget)</li>
 *   <li>6x: Up to 6 decompression tiers</li>
 *   <li>9x: Up to 9 decompression tiers</li>
 *   <li>12x: Up to 12 decompression tiers</li>
 *   <li>15x: Up to 15 decompression tiers</li>
 * </ul>
 * </p>
 * <p>
 * Note: Only one compression direction card can be installed at a time.
 * This card expands compression downward; use {@link ItemCompressionTierCard}
 * for expanding upward.
 * </p>
 * <p>
 * <b>Compatibility:</b> Only works with Compacting cells (normal and Hyper-Density).
 * </p>
 */
public class ItemDecompressionTierCard extends AbstractCustomUpgrade {

    /**
     * Tier values representing the number of decompression tiers available.
     * Metadata 0-4 maps to 3, 6, 9, 12, 15.
     */
    public static final int[] TIER_VALUES = {3, 6, 9, 12, 15};

    private static final String[] TIER_NAMES = {"3x", "6x", "9x", "12x", "15x"};

    public ItemDecompressionTierCard() {
        super("decompression_tier_card", TIER_VALUES, TIER_NAMES);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, World world, @Nonnull List<String> tooltip,
                               @Nonnull ITooltipFlag flag) {
        int tier = getTierValue(stack);
        tooltip.add("§7" + I18n.format("tooltip.cells.decompression_tier_card.desc", tier));
        addCompatibilityTooltip(tooltip, "compact", "hyperdensity_compact");
    }
}
