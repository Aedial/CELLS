package com.cells.gui.slots;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.container.slot.IJEITargetSlot;

import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.api.EssentiaStack;

import com.cells.gui.QuickAddHelper;
import com.cells.gui.ResourceRenderer;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.packets.PacketSetCreativeEssentiaFilter;


/**
 * Unified essentia filter slot implementation.
 * <p>
 * Works with both creative cells and interfaces via the provider pattern.
 */
@SideOnly(Side.CLIENT)
@Optional.Interface(iface = "appeng.container.slot.IJEITargetSlot", modid = "thaumicenergistics")
public class EssentiaFilterSlot extends AbstractResourceFilterSlot<EssentiaStack> implements IJEITargetSlot {

    /**
     * Provider interface for getting/setting essentia in a slot.
     */
    public interface EssentiaProvider {
        @Nullable EssentiaStack getEssentia(int slot);
        void setEssentia(int slot, @Nullable EssentiaStack essentia);
    }

    private final EssentiaProvider provider;

    /**
     * Create an essentia filter slot.
     */
    public EssentiaFilterSlot(EssentiaProvider provider, int slot, int x, int y) {
        super(slot, x, y);
        this.provider = provider;
    }

    @Override
    @Nullable
    protected EssentiaStack extractResourceFromStack(ItemStack stack) {
        return QuickAddHelper.getEssentiaFromItemStack(stack);
    }

    @Override
    protected boolean canExtractResourceFrom(ItemStack stack) {
        return QuickAddHelper.getEssentiaFromItemStack(stack) != null;
    }

    @Override
    @Nullable
    public EssentiaStack getResource() {
        return this.provider.getEssentia(this.slot);
    }

    @Override
    public void setResource(@Nullable EssentiaStack resource) {
        // Provider handles the local state update
        this.provider.setEssentia(this.slot, resource);

        // Send packet to server to sync the change (for both setting and clearing)
        CellsNetworkHandler.INSTANCE.sendToServer(
            new PacketSetCreativeEssentiaFilter(this.slot, resource));
    }

    @Override
    protected void drawResourceContent(Minecraft mc, int mouseX, int mouseY, float partialTicks, EssentiaStack resource) {
        ResourceRenderer.renderEssentia(resource, this.xPos(), this.yPos(), this.getWidth(), this.getHeight());
    }

    @Override
    protected String getResourceDisplayName(EssentiaStack resource) {
        Aspect aspect = resource.getAspect();
        return aspect != null ? aspect.getName() : null;
    }

    @Override
    protected boolean resourcesEqual(@Nullable EssentiaStack a, @Nullable EssentiaStack b) {
        if (a == null || b == null) return a == b;

        return a.getAspect() == b.getAspect();
    }

    @Override
    @Nullable
    protected EssentiaStack convertToResource(Object ingredient) {
        // Direct EssentiaStack
        if (ingredient instanceof EssentiaStack) return (EssentiaStack) ingredient;

        // Direct Aspect
        if (ingredient instanceof Aspect) {
            return new EssentiaStack((Aspect) ingredient, 1);
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
        EssentiaStack es = getResource();
        return es == null ? null : es.getAspect();
    }
}
