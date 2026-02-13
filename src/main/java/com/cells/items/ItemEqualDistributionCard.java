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
public class ItemEqualDistributionCard extends Item implements IUpgradeModule {

    /**
     * Tier values representing the type limits.
     * Metadata 0-6 maps to 1, 2, 4, 8, 16, 32, 63.
     */
    public static final int[] TIER_VALUES = {1, 2, 4, 8, 16, 32, 63, Integer.MAX_VALUE};

    private static final String[] TIER_NAMES = {"1x", "2x", "4x", "8x", "16x", "32x", "63x", "infinite"};

    public ItemEqualDistributionCard() {
        setRegistryName(Tags.MODID, "equal_distribution_card");
        setTranslationKey(Tags.MODID + ".equal_distribution_card");
        setMaxStackSize(64);
        setHasSubtypes(true);
        setMaxDamage(0);
        setCreativeTab(CellsCreativeTab.instance);
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        int meta = stack.getMetadata();
        if (meta >= 0 && meta < TIER_NAMES.length) {
            return getTranslationKey() + "." + TIER_NAMES[meta];
        }

        return getTranslationKey();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (!isInCreativeTab(tab)) return;

        for (int i = 0; i < TIER_NAMES.length; i++) {
            items.add(new ItemStack(this, 1, i));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        int tier = getTierValue(stack);

        if (tier == Integer.MAX_VALUE) {
            tooltip.add("\u00a77" + I18n.format("tooltip.cells.equal_distribution_card.desc.infinite"));
        } else {
            tooltip.add("\u00a77" + I18n.format("tooltip.cells.equal_distribution_card.desc", tier));
        }
        tooltip.add("");

        String compatibleTypes = I18n.format("tooltip.cells.type.hyperdensity");
        tooltip.add("\u00a78" + I18n.format("tooltip.cells.card.compatible", compatibleTypes));
    }

    /**
     * Gets the type limit value for this card variant.
     *
     * @param stack The card ItemStack
     * @return The type limit (1, 2, 4, 8, 16, 32, or 63)
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
     * <p>
     * Note: We use QUANTUM_LINK because it's not supposed to go there and won't
     * conflict with common cell upgrades. The cells' upgrade inventory filter
     * correctly identifies and limits our custom cards regardless of this type.
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
     * Creates an Equal Distribution Card for the given tier.
     *
     * @param tier 0=1x, 1=2x, 2=4x, 3=8x, 4=16x, 5=32x, 6=63x, 7=unbounded
     * @return The card ItemStack
     */
    public static ItemStack create(int tier) {
        if (tier < 0 || tier >= TIER_NAMES.length) tier = 0;

        return new ItemStack(ItemRegistry.EQUAL_DISTRIBUTION_CARD, 1, tier);
    }

    /**
     * Get the tier names for model registration.
     */
    public static String[] getTierNames() {
        return TIER_NAMES;
    }
}
