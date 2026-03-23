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
public class ItemCompressionTierCard extends AbstractCustomUpgrade {

    /**
     * Tier values representing the number of compression tiers available.
     * Metadata 0-4 maps to 3, 6, 9, 12, 15.
     */
    public static final int[] TIER_VALUES = {3, 6, 9, 12, 15};

    private static final String[] TIER_NAMES = {"3x", "6x", "9x", "12x", "15x"};

    public ItemCompressionTierCard() {
        super("compression_tier_card", TIER_VALUES, TIER_NAMES);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, World world, @Nonnull List<String> tooltip,
                               @Nonnull ITooltipFlag flag) {
        int tier = getTierValue(stack);
        tooltip.add("§7" + I18n.format("tooltip.cells.compression_tier_card.desc", tier));
        addCompatibilityTooltip(tooltip, "compacting", "hyperdensity_compact");
    }
}
