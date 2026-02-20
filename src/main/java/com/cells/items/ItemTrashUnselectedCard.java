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

import com.cells.Tags;
import com.cells.core.CellsCreativeTab;


/**
 * Trash Unselected Card - an upgrade card for the Import Interface.
 * <p>
 * When installed in the Import Interface's upgrade slot, items that do not
 * match any filter slot are voided instead of being rejected.
 * </p>
 * <p>
 * This is useful for "dumb" trash filtering, where you want to keep specific
 * items and destroy everything else. For example, filtering cobblestone out
 * of a quarry output.
 * </p>
 * <p>
 * <b>Compatibility:</b> Only works with the Import Interface from this mod.
 * </p>
 * <p>
 * Implements {@link IUpgradeModule} to integrate with AE2's upgrade system,
 * but returns null for the upgrade type since it's a custom upgrade not in
 * the standard {@link Upgrades} enum.
 * </p>
 */
public class ItemTrashUnselectedCard extends Item implements IUpgradeModule {

    public ItemTrashUnselectedCard() {
        setRegistryName(Tags.MODID, "trash_unselected_card");
        setTranslationKey(Tags.MODID + ".trash_unselected_card");
        setMaxStackSize(64);
        setCreativeTab(CellsCreativeTab.instance);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add("§7" + I18n.format("tooltip.cells.trash_unselected_card.desc"));
        tooltip.add("§e" + I18n.format("tooltip.cells.trash_unselected_card.warning"));
        tooltip.add("");

        String compatibleTypes = I18n.format("tooltip.cells.type.import_interface");
        tooltip.add("§8" + I18n.format("tooltip.cells.card.compatible", compatibleTypes));
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
