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
 * Overflow Card - an upgrade card for Compacting and Hyper-Density Storage Cells.
 * <p>
 * When installed in a cell's upgrade slot, excess items that cannot fit in the
 * cell are voided instead of being rejected back to the network.
 * <p>
 * <b>Important:</b> The overflow card only voids items of types that are already
 * stored in the cell. New types that the cell cannot accept (due to type limit,
 * blacklist, or partition restrictions) will still be rejected normally.
 * This prevents accidentally voiding valuable items that were never intended
 * for the cell.
 * <p>
 * This is useful for automated systems where you want to store as much as possible
 * and destroy the overflow, rather than having items back up in the network.
 * <p>
 * <b>Compatibility:</b> Only works with Compacting and Hyper-Density cells from
 * this mod. Standard AE2 cells do not support this upgrade.
 */
public class ItemOverflowCard extends AbstractCustomUpgrade {

    public ItemOverflowCard() {
        super("overflow_card");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, World world, @Nonnull List<String> tooltip,
                               @Nonnull ITooltipFlag flag) {
        tooltip.add("§7" + I18n.format("tooltip.cells.overflow_card.desc"));
        addCompatibilityTooltip(tooltip, "compact", "hyperdensity", "hyperdensity_compact", "import_interface");
    }
}
