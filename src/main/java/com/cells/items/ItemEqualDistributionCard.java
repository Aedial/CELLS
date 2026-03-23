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
 * Equal Distribution Card - an upgrade for storage cells that limits capacity per type.
 * <p>
 * When installed, the cell's capacity is divided equally among the allowed types,
 * preventing any single item from dominating the storage.
 * </p>
 * <p>
 * Available in variants: 1x, 2x, 4x, 8x, 16x, 32x, 63x, unbounded
 * <ul>
 *   <li>1x: 1 type, full capacity (effectively a partition)</li>
 *   <li>2x: 2 types, capacity/2 per type</li>
 *   <li>4x: 4 types, capacity/4 per type</li>
 *   <li>... and so on</li>
 *   <li>unbounded: no limit, inherits the max types from the cell itself</li>
 * </ul>
 * The actual type limit is min(card value, config slots used).
 * </p>
 * <p>
 * <b>Compatibility:</b> NOT compatible with Compacting Cells, as they only
 * store a single item type. Works with standard and Hyper-Density cells.
 * </p>
 */
public class ItemEqualDistributionCard extends AbstractCustomUpgrade {

    /**
     * Tier values representing the type limits.
     * Metadata 0-6 maps to 1, 2, 4, 8, 16, 32, 63.
     */
    public static final int[] TIER_VALUES = {1, 2, 4, 8, 16, 32, 63, Integer.MAX_VALUE};

    private static final String[] TIER_NAMES = {"1x", "2x", "4x", "8x", "16x", "32x", "63x", "infinite"};

    public ItemEqualDistributionCard() {
        super("equal_distribution_card", TIER_VALUES, TIER_NAMES);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, World world, @Nonnull List<String> tooltip,
                               @Nonnull ITooltipFlag flag) {
        int tier = getTierValue(stack);
        if (tier == Integer.MAX_VALUE) {
            tooltip.add("§7" + I18n.format("tooltip.cells.equal_distribution_card.desc.infinite"));
        } else {
            tooltip.add("§7" + I18n.format("tooltip.cells.equal_distribution_card.desc", tier));
        }
        addCompatibilityTooltip(tooltip, "hyperdensity");
    }
}
