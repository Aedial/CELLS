package com.cells.blocks.fluidimportinterface;

import java.util.Collections;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;

import appeng.api.storage.data.IAEFluidStack;
import appeng.client.gui.widgets.GuiCustomSlot;
import appeng.container.slot.IJEITargetSlot;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketFluidSlot;
import appeng.fluids.util.AEFluidStack;


/**
 * GUI widget for rendering fluid filter slots in the Fluid Import Interface.
 * <p>
 * Similar to AE2's GuiFluidSlot but reads from the tile's filter inventory directly.
 * When clicked, extracts the fluid from the held container item and sends a PacketFluidSlot
 * to update the filter on the server.
 * </p>
 */
public class GuiFluidFilterSlot extends GuiCustomSlot implements IJEITargetSlot {

    private final TileFluidImportInterface tile;
    private final int slot;

    public GuiFluidFilterSlot(final TileFluidImportInterface tile, final int slot, final int x, final int y) {
        super(slot, x, y);
        this.tile = tile;
        this.slot = slot;
    }

    public int getSlot() {
        return this.slot;
    }

    @Override
    public void drawContent(final Minecraft mc, final int mouseX, final int mouseY, final float partialTicks) {
        final IAEFluidStack fs = this.getFluidStack();
        if (fs == null) return;

        GlStateManager.disableLighting();
        GlStateManager.disableBlend();

        final Fluid fluid = fs.getFluid();
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        final TextureAtlasSprite sprite = mc.getTextureMapBlocks().getAtlasSprite(fluid.getStill().toString());

        // Set color for dynamic fluids
        final float red = (fluid.getColor() >> 16 & 255) / 255.0F;
        final float green = (fluid.getColor() >> 8 & 255) / 255.0F;
        final float blue = (fluid.getColor() & 255) / 255.0F;
        GlStateManager.color(red, green, blue);

        this.drawTexturedModalRect(this.xPos(), this.yPos(), sprite, this.getWidth(), this.getHeight());

        // Reset color
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableLighting();
        GlStateManager.enableBlend();
    }

    @Override
    public boolean canClick(final EntityPlayer player) {
        final ItemStack mouseStack = player.inventory.getItemStack();
        // Allow clicking with empty hand (to clear) or with fluid container (to set)
        return mouseStack.isEmpty() || mouseStack.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
    }

    @Override
    public void slotClicked(final ItemStack clickStack, int mouseButton) {
        // Right-click or empty hand clears the filter
        if (clickStack.isEmpty() || mouseButton == 1) {
            this.setFluidStack(null);
            return;
        }

        // Left-click with fluid container sets the filter
        if (mouseButton == 0) {
            final FluidStack fluid = FluidUtil.getFluidContained(clickStack);
            if (fluid != null) this.setFluidStack(AEFluidStack.fromFluidStack(fluid));
        }
    }

    @Override
    public String getMessage() {
        final IAEFluidStack fluid = this.getFluidStack();
        if (fluid != null) {
            return fluid.getFluidStack().getLocalizedName();
        }
        return null;
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    public IAEFluidStack getFluidStack() {
        return this.tile.getFilterFluid(this.slot);
    }

    public void setFluidStack(final IAEFluidStack stack) {
        // Send the fluid slot update to the server via PacketFluidSlot
        // The server will validate and apply the change
        NetworkHandler.instance().sendToServer(new PacketFluidSlot(Collections.singletonMap(this.slot, stack)));
    }

    @Override
    public boolean needAccept() {
        return this.getFluidStack() == null;
    }

    /**
     * Returns the fluid ingredient for JEI integration.
     */
    public Object getIngredient() {
        final IAEFluidStack fs = this.getFluidStack();
        return fs == null ? null : fs.getFluidStack();
    }
}
