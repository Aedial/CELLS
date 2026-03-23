package com.cells.blocks.interfacebase.fluid;

import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

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
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.fluids.client.render.FluidStackSizeRenderer;
import appeng.fluids.util.AEFluidStack;
import appeng.helpers.InventoryAction;


/**
 * Unified GUI slot for rendering fluid tanks in both Fluid Import and Export Interfaces.
 * <p>
 * Import mode: supports clicking with fluid containers to pour fluid into the tank.
 * Export mode: read-only display (tanks are filled from the ME network).
 * <p>
 * Supports pagination via a page offset supplier, allowing the displayed tank to map
 * to a different actual tank index based on the current page.
 */
public class GuiFluidTankSlot extends GuiCustomSlot {

    private static final FluidStackSizeRenderer FLUID_STACK_SIZE_RENDERER = new FluidStackSizeRenderer();

    private final IFluidInterfaceHost host;
    private final int displayTankIndex;
    private final IntSupplier pageOffsetSupplier;
    private final LongSupplier maxSlotSizeSupplier;
    private final boolean isExport;
    private FontRenderer fontRenderer;

    /**
     * Create a tank slot with pagination support.
     *
     * @param host The fluid interface host
     * @param displayTankIndex The display tank index (0-35 for one page)
     * @param id The slot ID for the GUI
     * @param x X position in GUI
     * @param y Y position in GUI
     * @param pageOffsetSupplier Supplier that returns the current page's starting slot index
     * @param maxSlotSizeSupplier Supplier for the synced max slot size (from container)
     */
    public GuiFluidTankSlot(IFluidInterfaceHost host,
                            int displayTankIndex, int id, int x, int y,
                            IntSupplier pageOffsetSupplier,
                            LongSupplier maxSlotSizeSupplier) {
        super(id, x, y);
        this.host = host;
        this.displayTankIndex = displayTankIndex;
        this.pageOffsetSupplier = pageOffsetSupplier;
        this.maxSlotSizeSupplier = maxSlotSizeSupplier;
        this.isExport = host.isExport();
    }

    /**
     * Get the actual tank index in the storage (display tank + page offset).
     */
    public int getTankIndex() {
        return this.displayTankIndex + this.pageOffsetSupplier.getAsInt();
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
        FluidStack fluid = this.host.getFluidInTank(this.getTankIndex());
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
        FluidStack fluid = this.host.getFluidInTank(this.getTankIndex());
        if (fluid == null || fluid.amount <= 0) return null;

        // Format: "Fluid Name\n1,234 / 16,000 mB"
        long capacity = this.maxSlotSizeSupplier.getAsLong();
        String unit = I18n.format("cells.unit." + host.getTypeName());
        String amountText = I18n.format("tooltip.cells.amount", fluid.amount, capacity, unit);

        return fluid.getLocalizedName() + "\n" + amountText;
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
        // Export tanks don't accept fluid pouring - they're filled from the network
        if (this.isExport) return;

        // Import: handle fluid container clicks - empty the container into the tank
        if (clickStack.isEmpty()) return;

        // Only process items that are fluid containers
        FluidStack fluidInContainer = FluidUtil.getFluidContained(clickStack);
        if (fluidInContainer == null) return;

        // Send EMPTY_ITEM action to the server to empty the container into this tank slot
        NetworkHandler.instance().sendToServer(
            new PacketInventoryAction(InventoryAction.EMPTY_ITEM, this.getTankIndex(), this.getId())
        );
    }

    public FluidStack getFluidStack() {
        return this.host.getFluidInTank(this.getTankIndex());
    }
}
