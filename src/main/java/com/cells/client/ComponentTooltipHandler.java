package com.cells.client;

import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cells.cells.configurable.ComponentHelper;


/**
 * Client-side event handler that adds a tooltip to items recognized as valid
 * components for the Configurable Cell.
 */
@SideOnly(Side.CLIENT)
public class ComponentTooltipHandler {

    private static final String TOOLTIP_KEY = "tooltip.cells.component.configurable_compatible";

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        // Check if this item is a recognized component
        if (ComponentHelper.getComponentInfo(stack) == null) return;

        List<String> tooltips = event.getToolTip();
        tooltips.add("");
        tooltips.add(I18n.format(TOOLTIP_KEY));
    }
}
