package com.cells.items;

import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;

import com.cells.Tags;
import com.cells.core.CellsCreativeTab;


/**
 * Ore Dictionary Card - an upgrade card for Compacting Storage Cells.
 * <p>
 * When installed in a compacting cell's upgrade slot, allows ore dictionary
 * equivalent items to be inserted and converted to the partitioned item type.
 * This mirrors the behavior of Storage Drawers' Conversion Upgrade.
 * </p>
 * <p>
 * For example, if a compacting cell is partitioned with "Iron Ingot" from one mod,
 * this card will allow "Iron Ingot" from other mods (with matching ore dictionary
 * entries like "ingotIron") to be inserted and stored as the original iron ingot.
 * </p>
 * <p>
 * The card also applies ore dictionary matching to compressed and decompressed
 * tiers in the compression chain, allowing equivalent blocks/nuggets to be
 * inserted as well.
 * </p>
 * <p>
 * <b>Compatibility:</b> Only works with Compacting and Hyper-Density Compacting 
 * cells from this mod.
 * </p>
 * <p>
 * Implements {@link IUpgradeModule} to integrate with AE2's upgrade system,
 * but returns {@link Upgrades#QUANTUM_LINK} as a placeholder type since it's 
 * a custom upgrade not in the standard {@link Upgrades} enum.
 * </p>
 */
public class ItemOreDictCard extends Item implements IUpgradeModule {

    public ItemOreDictCard() {
        setRegistryName(Tags.MODID, "oredict_card");
        setTranslationKey(Tags.MODID + ".oredict_card");
        setMaxStackSize(64);
        setCreativeTab(CellsCreativeTab.instance);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, World world, List<String> tooltip,
                               @Nonnull ITooltipFlag flag) {
        tooltip.add("§7" + I18n.format("tooltip.cells.oredict_card.desc"));
        tooltip.add("");

        String compatibleTypes = String.join(
            I18n.format("tooltip.cells.card.separator"),
            I18n.format("tooltip.cells.type.compact"),
            I18n.format("tooltip.cells.type.hyperdensity_compact")
        );
        tooltip.add("§8" + I18n.format("tooltip.cells.card.compatible", compatibleTypes));
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
}
