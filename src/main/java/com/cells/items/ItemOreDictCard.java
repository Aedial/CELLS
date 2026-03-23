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
 * Ore Dictionary Card - an upgrade card for Compacting Storage Cells.
 * <p>
 * When installed in a compacting cell's upgrade slot, allows ore dictionary
 * equivalent items to be inserted and converted to the partitioned item type.
 * This mirrors the behavior of Storage Drawers' Conversion Upgrade.
 * <p>
 * For example, if a compacting cell is partitioned with "Iron Ingot" from one mod,
 * this card will allow "Iron Ingot" from other mods (with matching ore dictionary
 * entries like "ingotIron") to be inserted and stored as the original iron ingot.
 * <p>
 * The card also applies ore dictionary matching to compressed and decompressed
 * tiers in the compression chain, allowing equivalent blocks/nuggets to be
 * inserted as well.
 * <p>
 * <b>Compatibility:</b> Only works with Compacting and Hyper-Density Compacting 
 * cells from this mod.
 * </p>
 */
public class ItemOreDictCard extends AbstractCustomUpgrade {

    public ItemOreDictCard() {
        super("oredict_card");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, World world, @Nonnull List<String> tooltip,
                               @Nonnull ITooltipFlag flag) {
        tooltip.add("§7" + I18n.format("tooltip.cells.oredict_card.desc"));
        addCompatibilityTooltip(tooltip, "compact", "hyperdensity_compact");
    }
}
