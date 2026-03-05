package com.cells.blocks.fluidimportinterface;

import java.util.Collections;
import java.util.function.IntSupplier;

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
 * Similar to AE2's GuiFluidSlot but reads from the host's filter inventory directly.
 * When clicked, extracts the fluid from the held container item and sends a PacketFluidSlot
 * to update the filter on the server.
 * <p>
 * Supports pagination via a page offset supplier, allowing the displayed slot to map
 * to a different actual slot index based on the current page.
 */
public class GuiFluidFilterSlot extends GuiCustomSlot implements IJEITargetSlot {

    private final IFluidImportInterfaceInventoryHost host;
    private final int displaySlot;  // The slot index displayed in the GUI (0-35)
    private final IntSupplier pageOffsetSupplier;  // Supplies the current page offset (page * slotsPerPage)

    /**
     * Create a filter slot with pagination support.
     *
     * @param host The fluid interface host
     * @param displaySlot The display slot index (0-35 for one page)
     * @param x X position in GUI
     * @param y Y position in GUI
     * @param pageOffsetSupplier Supplier that returns the current page's starting slot index
     */
    public GuiFluidFilterSlot(final IFluidImportInterfaceInventoryHost host, final int displaySlot,
                              final int x, final int y, final IntSupplier pageOffsetSupplier) {
        super(displaySlot, x, y);
        this.host = host;
        this.displaySlot = displaySlot;
        this.pageOffsetSupplier = pageOffsetSupplier;
    }

    /**
     * Create a filter slot without pagination (legacy support, page 0 only).
     */
    public GuiFluidFilterSlot(final IFluidImportInterfaceInventoryHost host, final int slot, final int x, final int y) {
        this(host, slot, x, y, () -> 0);
    }

    /**
     * Get the actual slot index in the filter inventory (display slot + page offset).
     */
    public int getSlot() {
        return this.displaySlot + this.pageOffsetSupplier.getAsInt();
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
        return this.host.getFilterFluid(this.getSlot());
    }

    public void setFluidStack(final IAEFluidStack stack) {
        // Send the fluid slot update to the server via PacketFluidSlot
        // The server will validate and apply the change
        NetworkHandler.instance().sendToServer(new PacketFluidSlot(Collections.singletonMap(this.getSlot(), stack)));
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
