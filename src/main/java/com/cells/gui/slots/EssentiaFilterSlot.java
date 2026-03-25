package com.cells.gui.slots;

import java.util.function.IntSupplier;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.api.EssentiaStack;
import thaumicenergistics.api.storage.IAEEssentiaStack;
import thaumicenergistics.integration.appeng.AEEssentiaStack;

import com.cells.gui.QuickAddHelper;
import com.cells.gui.ResourceRenderer;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.sync.PacketResourceSlot;
import com.cells.network.sync.ResourceType;


/**
 * Unified essentia filter slot implementation.
 * <p>
 * Uses unified {@link PacketResourceSlot} for sync.
 * Works with IAEEssentiaStack for unified handling across all resource types.
 */
@SideOnly(Side.CLIENT)
@Optional.Interface(iface = "appeng.container.slot.IJEITargetSlot", modid = "thaumicenergistics")
public class EssentiaFilterSlot extends AbstractResourceFilterSlot<IAEEssentiaStack> {

    /**
     * Provider interface for getting essentia in a slot.
     */
    @FunctionalInterface
    public interface EssentiaProvider {
        @Nullable IAEEssentiaStack getEssentia(int slot);
    }

    private final EssentiaProvider provider;
    private final IntSupplier pageOffsetSupplier;

    /**
     * Create an essentia filter slot with pagination support.
     *
     * @param provider The provider for getting essentia data
     * @param displaySlot The display slot index (0-35 for one page)
     * @param x X position in GUI
     * @param y Y position in GUI
     * @param pageOffsetSupplier Supplier that returns the current page's starting slot index
     */
    public EssentiaFilterSlot(EssentiaProvider provider, int displaySlot, int x, int y, IntSupplier pageOffsetSupplier) {
        super(displaySlot, x, y);
        this.provider = provider;
        this.pageOffsetSupplier = pageOffsetSupplier;
    }

    /**
     * Create an essentia filter slot without pagination.
     */
    public EssentiaFilterSlot(EssentiaProvider provider, int slot, int x, int y) {
        this(provider, slot, x, y, () -> 0);
    }

    @Override
    public int getSlot() {
        return this.slot + this.pageOffsetSupplier.getAsInt();
    }

    @Override
    @Nullable
    protected IAEEssentiaStack extractResourceFromStack(ItemStack stack) {
        EssentiaStack raw = QuickAddHelper.getEssentiaFromItemStack(stack);
        return raw != null ? AEEssentiaStack.fromEssentiaStack(raw) : null;
    }

    @Override
    protected boolean canExtractResourceFrom(ItemStack stack) {
        return QuickAddHelper.getEssentiaFromItemStack(stack) != null;
    }

    @Override
    @Nullable
    public IAEEssentiaStack getResource() {
        return this.provider.getEssentia(getSlot());
    }

    @Override
    public void setResource(@Nullable IAEEssentiaStack resource) {
        CellsNetworkHandler.INSTANCE.sendToServer(
            new PacketResourceSlot(ResourceType.ESSENTIA, getSlot(), resource));
    }

    @Override
    protected void drawResourceContent(Minecraft mc, int mouseX, int mouseY, float partialTicks, IAEEssentiaStack resource) {
        EssentiaStack raw = resource.getStack();
        ResourceRenderer.renderEssentia(raw, this.xPos(), this.yPos(), this.getWidth(), this.getHeight());
    }

    @Override
    protected String getResourceDisplayName(IAEEssentiaStack resource) {
        EssentiaStack raw = resource.getStack();
        Aspect aspect = raw != null ? raw.getAspect() : null;
        return aspect != null ? aspect.getName() : null;
    }

    @Override
    protected boolean resourcesEqual(@Nullable IAEEssentiaStack a, @Nullable IAEEssentiaStack b) {
        if (a == null || b == null) return a == b;

        return a.equals(b);
    }

    @Override
    @Nullable
    public IAEEssentiaStack convertToResource(Object ingredient) {
        // Direct IAEEssentiaStack
        if (ingredient instanceof IAEEssentiaStack) return (IAEEssentiaStack) ingredient;

        // Direct EssentiaStack
        if (ingredient instanceof EssentiaStack) {
            return AEEssentiaStack.fromEssentiaStack((EssentiaStack) ingredient);
        }

        // Direct Aspect
        if (ingredient instanceof Aspect) {
            return AEEssentiaStack.fromEssentiaStack(new EssentiaStack((Aspect) ingredient, 1));
        }

        // ItemStack with essentia
        if (ingredient instanceof ItemStack) {
            return extractResourceFromStack((ItemStack) ingredient);
        }

        return null;
    }

    /**
     * Get the essentia ingredient for JEI integration.
     */
    public Object getIngredient() {
        IAEEssentiaStack resource = getResource();
        if (resource == null) return null;

        EssentiaStack raw = resource.getStack();
        return raw != null ? raw.getAspect() : null;
    }
}
