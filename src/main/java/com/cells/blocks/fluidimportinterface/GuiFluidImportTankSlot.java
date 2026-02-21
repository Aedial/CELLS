package com.cells.blocks.fluidimportinterface;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import appeng.api.storage.data.IAEFluidStack;
import appeng.client.gui.widgets.GuiCustomSlot;
import appeng.client.gui.widgets.ITooltip;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.fluids.client.render.FluidStackSizeRenderer;
import appeng.fluids.util.AEFluidStack;
import appeng.helpers.InventoryAction;


/**
 * Custom GUI slot for rendering fluid tanks in the Fluid Import Interface.
 * <p>
 * This class integrates with AE2's guiSlots system for automatic rendering,
 * tooltip handling, and hover highlighting - no custom rendering code needed in the GUI.
 * </p>
 */
public class GuiFluidImportTankSlot extends GuiCustomSlot implements ITooltip {

    private static final FluidStackSizeRenderer FLUID_STACK_SIZE_RENDERER = new FluidStackSizeRenderer();

    private final TileFluidImportInterface tile;
    private final ContainerFluidImportInterface container;
    private final int tankIndex;
    private FontRenderer fontRenderer;

    public GuiFluidImportTankSlot(TileFluidImportInterface tile, ContainerFluidImportInterface container, int tankIndex, int id, int x, int y) {
        super(id, x, y);
        this.tile = tile;
        this.container = container;
        this.tankIndex = tankIndex;
    }

    /**
     * Set the font renderer for stack size rendering.
     * Must be called after construction.
     */
    public void setFontRenderer(FontRenderer fontRenderer) {
        this.fontRenderer = fontRenderer;
    }

    @Override
    public void drawContent(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        FluidStack fluid = this.tile.getFluidInTank(this.tankIndex);
        if (fluid == null || fluid.amount <= 0) return;

        Fluid fluidType = fluid.getFluid();
        if (fluidType == null) return;
        if (fluidType.getStill() == null) return;

        GlStateManager.disableLighting();
        GlStateManager.disableBlend();

        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        TextureAtlasSprite sprite = mc.getTextureMapBlocks().getAtlasSprite(fluidType.getStill().toString());

        // Set fluid color
        int color = fluidType.getColor(fluid);
        float red = (color >> 16 & 255) / 255.0F;
        float green = (color >> 8 & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;
        GlStateManager.color(red, green, blue);

        this.drawTexturedModalRect(this.xPos(), this.yPos(), sprite, getWidth(), getHeight());

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        // Render stack size in corner (like AE2 fluid slots)
        if (this.fontRenderer != null) {
            IAEFluidStack aeFluid = AEFluidStack.fromFluidStack(fluid);
            FLUID_STACK_SIZE_RENDERER.renderStackSize(this.fontRenderer, aeFluid, this.xPos(), this.yPos());
        }
    }

    @Override
    public String getMessage() {
        FluidStack fluid = this.tile.getFluidInTank(this.tankIndex);
        if (fluid == null || fluid.amount <= 0) return null;

        // Format: "Fluid Name\n1,234 / 16,000 mB"
        // Use the container's synced maxSlotSize for correct capacity display
        long capacity = this.container.maxSlotSize;
        return fluid.getLocalizedName() + "\n" + I18n.format("tooltip.cells.fluid_import_interface.amount", fluid.amount, capacity);
    }

    @Override
    public int xPos() {
        return this.x;
    }

    @Override
    public int yPos() {
        return this.y;
    }

    @Override
    public int getWidth() {
        return 16;
    }

    @Override
    public int getHeight() {
        return 16;
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    @Override
    public void slotClicked(ItemStack clickStack, int mouseButton) {
        // Handle fluid container clicks: empty the container into the tank
        if (clickStack.isEmpty()) return;

        // Only process items that are fluid containers
        FluidStack fluidInContainer = FluidUtil.getFluidContained(clickStack);
        if (fluidInContainer == null) return;

        // Send EMPTY_ITEM action to the server to empty the container into this tank slot
        NetworkHandler.instance().sendToServer(
            new PacketInventoryAction(InventoryAction.EMPTY_ITEM, this.tankIndex, this.getId())
        );
    }

    public FluidStack getFluidStack() {
        return this.tile.getFluidInTank(this.tankIndex);
    }
}
