package com.cells.blocks.fluidexportinterface;

import java.util.function.IntSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.storage.data.IAEFluidStack;
import appeng.client.gui.widgets.GuiCustomSlot;
import appeng.client.gui.widgets.ITooltip;
import appeng.fluids.client.render.FluidStackSizeRenderer;
import appeng.fluids.util.AEFluidStack;


/**
 * Custom GUI slot for rendering fluid tanks in the Fluid Export Interface.
 * <p>
 * Displays fluids extracted from the network. Read-only display unlike the import interface.
 * <p>
 * Supports pagination via a page offset supplier, allowing the displayed tank to map
 * to a different actual tank index based on the current page.
 */
public class GuiFluidExportTankSlot extends GuiCustomSlot implements ITooltip {

    private static final FluidStackSizeRenderer FLUID_STACK_SIZE_RENDERER = new FluidStackSizeRenderer();

    private final IFluidExportInterfaceInventoryHost host;
    private final ContainerFluidExportInterface container;
    private final int displayTankIndex;  // The tank index displayed in the GUI (0-35)
    private final IntSupplier pageOffsetSupplier;  // Supplies the current page offset
    private FontRenderer fontRenderer;

    /**
     * Create a tank slot with pagination support.
     *
     * @param host The fluid interface host
     * @param container The container for synced config values
     * @param displayTankIndex The display tank index (0-35 for one page)
     * @param id The slot ID for the GUI
     * @param x X position in GUI
     * @param y Y position in GUI
     * @param pageOffsetSupplier Supplier that returns the current page's starting slot index
     */
    public GuiFluidExportTankSlot(IFluidExportInterfaceInventoryHost host, ContainerFluidExportInterface container,
                                   int displayTankIndex, int id, int x, int y, IntSupplier pageOffsetSupplier) {
        super(id, x, y);
        this.host = host;
        this.container = container;
        this.displayTankIndex = displayTankIndex;
        this.pageOffsetSupplier = pageOffsetSupplier;
    }

    /**
     * Create a tank slot without pagination (legacy support, page 0 only).
     */
    public GuiFluidExportTankSlot(IFluidExportInterfaceInventoryHost host, ContainerFluidExportInterface container,
                                   int tankIndex, int id, int x, int y) {
        this(host, container, tankIndex, id, x, y, () -> 0);
    }

    /**
     * Get the actual tank index in the storage (display tank + page offset).
     */
    public int getTankIndex() {
        return this.displayTankIndex + this.pageOffsetSupplier.getAsInt();
    }

    public void setFontRenderer(FontRenderer fontRenderer) {
        this.fontRenderer = fontRenderer;
    }

    @Override
    public void drawContent(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        FluidStack fluid = this.host.getFluidInTank(this.getTankIndex());
        if (fluid == null || fluid.amount <= 0) return;

        Fluid fluidType = fluid.getFluid();
        if (fluidType == null) return;
        if (fluidType.getStill() == null) return;

        GlStateManager.disableLighting();
        GlStateManager.disableBlend();

        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        TextureAtlasSprite sprite = mc.getTextureMapBlocks().getAtlasSprite(fluidType.getStill().toString());

        int color = fluidType.getColor(fluid);
        float red = (color >> 16 & 255) / 255.0F;
        float green = (color >> 8 & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;
        GlStateManager.color(red, green, blue);

        this.drawTexturedModalRect(this.xPos(), this.yPos(), sprite, getWidth(), getHeight());

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        if (this.fontRenderer != null) {
            IAEFluidStack aeFluid = AEFluidStack.fromFluidStack(fluid);
            FLUID_STACK_SIZE_RENDERER.renderStackSize(this.fontRenderer, aeFluid, this.xPos(), this.yPos());
        }
    }

    @Override
    public String getMessage() {
        FluidStack fluid = this.host.getFluidInTank(this.getTankIndex());
        if (fluid == null || fluid.amount <= 0) return null;

        long capacity = this.container.maxSlotSize;
        return fluid.getLocalizedName() + "\n" + I18n.format("tooltip.cells.fluid_export_interface.amount", fluid.amount, capacity);
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
        // TODO: allow filling tanks in hand from the slot?
        // Export interface tanks don't accept fluid pouring - they're filled from network
    }

    public FluidStack getFluidStack() {
        return this.host.getFluidInTank(this.getTankIndex());
    }
}
