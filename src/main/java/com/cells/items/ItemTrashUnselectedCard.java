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
 * Trash Unselected Card - an upgrade card for the Import Interface.
 * <p>
 * When installed in the Import Interface's upgrade slot, items that do not
 * match any filter slot are voided instead of being rejected.
 * <p>
 * This is useful for "dumb" trash filtering, where you want to keep specific
 * items and destroy everything else. For example, filtering cobblestone out
 * of a quarry output.
 * <p>
 * <b>Compatibility:</b> Only works with the Import Interface from this mod.
 */
public class ItemTrashUnselectedCard extends AbstractCustomUpgrade {

    public ItemTrashUnselectedCard() {
        super("trash_unselected_card");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, World world, @Nonnull List<String> tooltip,
                               @Nonnull ITooltipFlag flag) {
        tooltip.add("§7" + I18n.format("tooltip.cells.trash_unselected_card.desc"));
        tooltip.add("§e" + I18n.format("tooltip.cells.trash_unselected_card.warning"));
        addCompatibilityTooltip(tooltip, "import_interface");
    }
}
