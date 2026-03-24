package com.cells.gui.slots;

import java.util.function.IntSupplier;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Optional;

import mekanism.api.gas.GasStack;

import com.mekeng.github.common.me.data.IAEGasStack;
import com.mekeng.github.common.me.data.impl.AEGasStack;

import com.cells.gui.QuickAddHelper;
import com.cells.gui.ResourceRenderer;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.sync.PacketResourceSlot;
import com.cells.network.sync.ResourceType;


/**
 * Unified gas filter slot implementation.
 * <p>
 * Uses unified {@link PacketResourceSlot} for sync.
 * Supports pagination via page offset supplier.
 */
@Optional.Interface(iface = "appeng.container.slot.IJEITargetSlot", modid = "mekeng")
public class GasFilterSlot extends AbstractResourceFilterSlot<IAEGasStack> {

    /**
     * Provider interface for getting gas in a slot.
     */
    @FunctionalInterface
    public interface GasProvider {
        @Nullable IAEGasStack getGas(int slot);
    }

    private final GasProvider provider;
    private final IntSupplier pageOffsetSupplier;

    /**
     * Create a gas filter slot with pagination support.
     */
    public GasFilterSlot(GasProvider provider, int displaySlot, int x, int y, IntSupplier pageOffsetSupplier) {
        super(displaySlot, x, y);
        this.provider = provider;
        this.pageOffsetSupplier = pageOffsetSupplier;
    }

    /**
     * Create a gas filter slot without pagination.
     */
    public GasFilterSlot(GasProvider provider, int slot, int x, int y) {
        this(provider, slot, x, y, () -> 0);
    }

    @Override
    public int getSlot() {
        return this.slot + this.pageOffsetSupplier.getAsInt();
    }

    @Override
    @Nullable
    protected IAEGasStack extractResourceFromStack(ItemStack stack) {
        GasStack gas = QuickAddHelper.getGasFromItemStack(stack);
        return gas != null ? AEGasStack.of(gas) : null;
    }

    @Override
    protected boolean canExtractResourceFrom(ItemStack stack) {
        return QuickAddHelper.getGasFromItemStack(stack) != null;
    }

    @Override
    @Nullable
    public IAEGasStack getResource() {
        return this.provider.getGas(getSlot());
    }

    @Override
    public void setResource(@Nullable IAEGasStack resource) {
        // Send packet to server using unified resource sync
        CellsNetworkHandler.INSTANCE.sendToServer(
            new PacketResourceSlot(ResourceType.GAS, getSlot(), resource)
        );
    }

    @Override
    protected void drawResourceContent(Minecraft mc, int mouseX, int mouseY, float partialTicks, IAEGasStack resource) {
        ResourceRenderer.renderGas(resource, this.xPos(), this.yPos(), this.getWidth(), this.getHeight());
    }

    @Override
    protected String getResourceDisplayName(IAEGasStack resource) {
        GasStack stack = resource.getGasStack();
        if (stack != null && stack.getGas() != null) {
            return stack.getGas().getLocalizedName();
        }

        return null;
    }

    @Override
    protected boolean resourcesEqual(@Nullable IAEGasStack a, @Nullable IAEGasStack b) {
        if (a == null || b == null) return a == b;

        GasStack gasA = a.getGasStack();
        GasStack gasB = b.getGasStack();
        if (gasA == null || gasB == null) return gasA == gasB;

        return gasA.getGas() == gasB.getGas();
    }

    @Override
    @Nullable
    public IAEGasStack convertToResource(Object ingredient) {
        // Direct GasStack
        if (ingredient instanceof GasStack) {
            return AEGasStack.of((GasStack) ingredient);
        }

        // IAEGasStack
        if (ingredient instanceof IAEGasStack) return (IAEGasStack) ingredient;

        // ItemStack with gas
        if (ingredient instanceof ItemStack) {
            return extractResourceFromStack((ItemStack) ingredient);
        }

        return null;
    }

    /**
     * Get the gas ingredient for JEI integration.
     */
    public Object getIngredient() {
        IAEGasStack gs = getResource();
        return gs == null ? null : gs.getGasStack();
    }
}
