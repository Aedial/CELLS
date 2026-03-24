package com.cells.gui.slots;

import java.util.function.IntSupplier;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;

import appeng.api.storage.data.IAEFluidStack;
import appeng.fluids.util.AEFluidStack;

import com.cells.gui.ResourceRenderer;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.sync.PacketResourceSlot;
import com.cells.network.sync.ResourceType;


/**
 * Unified fluid filter slot implementation.
 * <p>
 * Uses unified {@link PacketResourceSlot} for sync.
 * Supports pagination via page offset supplier.
 */
public class FluidFilterSlot extends AbstractResourceFilterSlot<IAEFluidStack> {

    /**
     * Provider interface for getting/setting fluid in a slot.
     * Implement this in your container/host to provide the fluid data.
     */
    @FunctionalInterface
    public interface FluidProvider {
        @Nullable IAEFluidStack getFluid(int slot);
    }

    private final FluidProvider provider;
    private final IntSupplier pageOffsetSupplier;

    /**
     * Create a fluid filter slot with pagination support.
     *
     * @param provider The provider for getting fluid data
     * @param displaySlot The display slot index (0-35 for one page)
     * @param x X position in GUI
     * @param y Y position in GUI
     * @param pageOffsetSupplier Supplier that returns the current page's starting slot index
     */
    public FluidFilterSlot(FluidProvider provider, int displaySlot, int x, int y, IntSupplier pageOffsetSupplier) {
        super(displaySlot, x, y);
        this.provider = provider;
        this.pageOffsetSupplier = pageOffsetSupplier;
    }

    /**
     * Create a fluid filter slot without pagination.
     */
    public FluidFilterSlot(FluidProvider provider, int slot, int x, int y) {
        this(provider, slot, x, y, () -> 0);
    }

    @Override
    public int getSlot() {
        return this.slot + this.pageOffsetSupplier.getAsInt();
    }

    @Override
    @Nullable
    protected IAEFluidStack extractResourceFromStack(ItemStack stack) {
        FluidStack fluid = FluidUtil.getFluidContained(stack);
        return fluid != null ? AEFluidStack.fromFluidStack(fluid) : null;
    }

    @Override
    protected boolean canExtractResourceFrom(ItemStack stack) {
        return stack.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
    }

    @Override
    @Nullable
    public IAEFluidStack getResource() {
        return this.provider.getFluid(getSlot());
    }

    @Override
    public void setResource(@Nullable IAEFluidStack resource) {
        // Send packet to server using unified resource sync
        CellsNetworkHandler.INSTANCE.sendToServer(
            new PacketResourceSlot(ResourceType.FLUID, getSlot(), resource)
        );
    }

    @Override
    protected void drawResourceContent(Minecraft mc, int mouseX, int mouseY, float partialTicks, IAEFluidStack resource) {
        ResourceRenderer.renderFluid(resource, this.xPos(), this.yPos(), this.getWidth(), this.getHeight());
    }

    @Override
    protected String getResourceDisplayName(IAEFluidStack resource) {
        return resource.getFluidStack().getLocalizedName();
    }

    @Override
    protected boolean resourcesEqual(@Nullable IAEFluidStack a, @Nullable IAEFluidStack b) {
        if (a == null || b == null) return a == b;

        return a.getFluid() == b.getFluid();
    }

    @Override
    @Nullable
    public IAEFluidStack convertToResource(Object ingredient) {
        // Direct FluidStack
        if (ingredient instanceof FluidStack) {
            return AEFluidStack.fromFluidStack((FluidStack) ingredient);
        }

        // IAEFluidStack
        if (ingredient instanceof IAEFluidStack) return (IAEFluidStack) ingredient;

        // ItemStack with fluid
        if (ingredient instanceof ItemStack) {
            return extractResourceFromStack((ItemStack) ingredient);
        }

        return null;
    }

    /**
     * Get the fluid ingredient for JEI integration.
     */
    public Object getIngredient() {
        IAEFluidStack fs = getResource();
        return fs == null ? null : fs.getFluidStack();
    }
}
