package com.cells.blocks.interfacebase.item;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import com.cells.blocks.interfacebase.AbstractResourceInterfaceGui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import appeng.api.parts.IPart;
import appeng.container.slot.SlotFake;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.helpers.InventoryAction;
import appeng.util.item.AEItemStack;

import mezz.jei.api.gui.IGhostIngredientHandler.Target;

import com.cells.gui.CellsGuiHandler;
import com.cells.gui.QuickAddHelper;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.packets.PacketQuickAddItemFilter;

import java.io.IOException;


/**
 * GUI for both Item Import Interface and Item Export Interface.
 * Extends the abstract base to provide item-specific slot creation and JEI handling.
 * <p>
 * Unlike fluid/gas interfaces, item interfaces use standard container slots
 * (SlotFake for filters, SlotNormal for storage), not custom GuiCustomSlot widgets.
 */
public class GuiItemInterface extends AbstractResourceInterfaceGui<IItemInterfaceHost, ContainerItemInterface> {

    /**
     * Constructor for tile entity.
     */
    public GuiItemInterface(final InventoryPlayer inventoryPlayer, final TileEntity tile) {
        super(new ContainerItemInterface(inventoryPlayer, tile), (IItemInterfaceHost) tile);
    }

    /**
     * Constructor for part.
     */
    public GuiItemInterface(final InventoryPlayer inventoryPlayer, final IPart part) {
        super(new ContainerItemInterface(inventoryPlayer, part), (IItemInterfaceHost) part);
    }

    // ============================== Abstract method implementations ==============================

    @Override
    protected int getCurrentPage() {
        return this.container.currentPage;
    }

    @Override
    protected int getTotalPages() {
        return this.container.totalPages;
    }

    @Override
    protected long getMaxSlotSize() {
        return this.container.maxSlotSize;
    }

    @Override
    protected long getPollingRate() {
        return this.container.pollingRate;
    }

    @Override
    protected void nextPage() {
        this.container.nextPage();
    }

    @Override
    protected void prevPage() {
        this.container.prevPage();
    }

    @Override
    protected int getMaxSlotSizeGuiId() {
        return this.host.isPart() ? CellsGuiHandler.GUI_PART_MAX_SLOT_SIZE : CellsGuiHandler.GUI_MAX_SLOT_SIZE;
    }

    @Override
    protected int getPollingRateGuiId() {
        return this.host.isPart() ? CellsGuiHandler.GUI_PART_POLLING_RATE : CellsGuiHandler.GUI_POLLING_RATE;
    }

    @Override
    protected void createResourceSlots() {
        // Item interfaces use standard container slots (SlotFake/SlotNormal),
        // which are already added by the container. No custom slots needed here.
    }

    @Override
    protected List<Target<?>> createJEITargets(Object ingredient) {
        if (!(ingredient instanceof ItemStack)) return Collections.emptyList();

        List<Target<?>> targets = new ArrayList<>();
        ItemStack itemStack = (ItemStack) ingredient;

        for (Slot slot : this.inventorySlots.inventorySlots) {
            if (!(slot instanceof ContainerItemInterface.SlotFilter)) continue;

            Target<Object> target = new Target<Object>() {
                @Override
                @Nonnull
                public Rectangle getArea() {
                    return new Rectangle(getGuiLeft() + slot.xPos, getGuiTop() + slot.yPos, 16, 16);
                }

                @Override
                public void accept(@Nonnull Object ingredient) {
                    try {
                        PacketInventoryAction p = new PacketInventoryAction(
                            InventoryAction.PLACE_JEI_GHOST_ITEM,
                            (SlotFake) slot,
                            AEItemStack.fromItemStack(itemStack)
                        );
                        NetworkHandler.instance().sendToServer(p);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            };
            targets.add(target);
            mapTargetSlot.putIfAbsent(target, slot);
        }

        return targets;
    }

    @Override
    protected boolean handleQuickAdd(Slot hoveredSlot) {
        ItemStack item = QuickAddHelper.getItemUnderCursor(hoveredSlot);

        if (!item.isEmpty()) {
            CellsNetworkHandler.INSTANCE.sendToServer(new PacketQuickAddItemFilter(item));
            return true;
        }

        return false;
    }

    // ============================== Custom rendering ==============================

    /**
     * Override drawSlot to render filter (fake) slots without item count.
     * This makes them appear as "ghost" items, which is the standard UX for filters.
     */
    @Override
    public void drawSlot(Slot slot) {
        if (slot instanceof SlotFake) {
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty()) {
                this.zLevel = 100.0F;
                this.itemRender.zLevel = 100.0F;

                // TODO: ghost overlay?
                RenderHelper.enableGUIStandardItemLighting();
                GlStateManager.enableDepth();
                this.itemRender.renderItemIntoGUI(stack, slot.xPos, slot.yPos);
                GlStateManager.disableDepth();

                this.itemRender.zLevel = 0.0F;
                this.zLevel = 0.0F;
            }

            return;
        }

        super.drawSlot(slot);
    }
}
