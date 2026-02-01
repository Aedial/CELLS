package com.cells.items;

import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.core.CreativeTab;

import com.cells.Tags;


/**
 * Overflow Card - an upgrade card for Compacting and Hyper-Density Storage Cells.
 * <p>
 * When installed in a cell's upgrade slot, excess items that cannot fit in the
 * cell are voided instead of being rejected back to the network.
 * </p>
 * <p>
 * This is useful for automated systems where you want to store as much as possible
 * and destroy the overflow, rather than having items back up in the network.
 * </p>
 * <p>
 * <b>Compatibility:</b> Only works with Compacting and Hyper-Density cells from
 * this mod. Standard AE2 cells do not support this upgrade.
 * </p>
 * <p>
 * Implements {@link IUpgradeModule} to integrate with AE2's upgrade system,
 * but returns null for the upgrade type since it's a custom upgrade not in
 * the standard {@link Upgrades} enum.
 * </p>
 */
public class ItemOverflowCard extends Item implements IUpgradeModule {

    public ItemOverflowCard() {
        setRegistryName(Tags.MODID, "overflow_card");
        setTranslationKey(Tags.MODID + ".overflow_card");
        setMaxStackSize(64);
        setCreativeTab(CreativeTab.instance);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add("\u00a77" + I18n.format("tooltip.cells.overflow_card.desc"));

        String compatibleTypes = String.join(
            I18n.format("tooltip.cells.card.separator"),
            I18n.format("tooltip.cells.type.compact"),
            I18n.format("tooltip.cells.type.hyperdensity"),
            I18n.format("tooltip.cells.type.hyperdensity_compact")
        );
        tooltip.add("\u00a78" + I18n.format("tooltip.cells.card.compatible", compatibleTypes));
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
}
