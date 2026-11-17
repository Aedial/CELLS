package com.cells.gui.scanner.button;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;


/**
 * Subnet button with green and red backgrounds for the scan scope.
 */
public class GuiScannerSubnetButton extends BinaryButton {

    private static final ResourceLocation AE2_STATES = new ResourceLocation(
        "appliedenergistics2", "textures/guis/states.png");
    private static final int ICON_SIZE = 16;
    private static final int ICON_U = 2 * ICON_SIZE;
    private static final int ICON_V = 8 * ICON_SIZE;

    public GuiScannerSubnetButton(int id, int x, int y, boolean includeSubnets) {
        super(id, x, y, includeSubnets);
    }

    @Override
    protected void drawForeground(Minecraft mc) {
        mc.getTextureManager().bindTexture(AE2_STATES);
        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        drawTexturedModalRect(x, y, ICON_U, ICON_V, ICON_SIZE, ICON_SIZE);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
    }

    @Override
    public List<String> getTooltip() {
        List<String> tooltip = new ArrayList<>();
        tooltip.add(I18n.format("cells.interface_scanner.subnets"));

        String state = get() ? "enabled" : "disabled";
        tooltip.add(I18n.format("cells.interface_scanner.subnets." + state));

        if (!this.enabled) tooltip.add(I18n.format("cells.interface_scanner.no_network"));

        return tooltip;
    }
}
