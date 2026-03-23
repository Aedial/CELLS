package com.cells.integration.mekanismenergistics;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import appeng.api.parts.IPart;
import appeng.client.gui.widgets.GuiCustomSlot;

import com.mekeng.github.common.me.data.IAEGasStack;
import com.mekeng.github.common.me.data.impl.AEGasStack;

import mekanism.api.gas.GasStack;

import mezz.jei.api.gui.IGhostIngredientHandler.Target;

import com.cells.blocks.interfacebase.AbstractResourceInterfaceGui;
import com.cells.gui.QuickAddHelper;
import com.cells.network.CellsNetworkHandler;


/**
 * GUI for both Gas Import Interface and Gas Export Interface.
 * Extends the abstract base to provide gas-specific slot creation and JEI handling.
 */
public class GuiGasInterface extends AbstractResourceInterfaceGui<IGasInterfaceHost, ContainerGasInterface> {

    /**
     * Constructor for tile entity.
     */
    public GuiGasInterface(final InventoryPlayer inventoryPlayer, final TileEntity tile) {
        super(new ContainerGasInterface(inventoryPlayer, tile), (IGasInterfaceHost) tile);
    }

    /**
     * Constructor for part.
     */
    public GuiGasInterface(final InventoryPlayer inventoryPlayer, final IPart part) {
        super(new ContainerGasInterface(inventoryPlayer, part), (IGasInterfaceHost) part);
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
        return this.host.isPart()
            ? GasInterfaceGuiHandler.GUI_PART_GAS_MAX_SLOT_SIZE
            : GasInterfaceGuiHandler.GUI_GAS_MAX_SLOT_SIZE;
    }

    @Override
    protected int getPollingRateGuiId() {
        return this.host.isPart()
            ? GasInterfaceGuiHandler.GUI_PART_GAS_POLLING_RATE
            : GasInterfaceGuiHandler.GUI_GAS_POLLING_RATE;
    }

    @Override
    protected void createResourceSlots() {
        // Add gas filter slots (4 rows x 9 cols)
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                int displaySlot = row * 9 + col;
                if (displaySlot >= SLOTS_PER_PAGE) break;

                int xPos = 8 + col * 18;
                int filterY = 25 + row * 36;

                GuiGasFilterSlot filterSlot = new GuiGasFilterSlot(
                    this.container, displaySlot, xPos, filterY,
                    () -> this.container.currentPage * SLOTS_PER_PAGE
                );
                this.guiSlots.add(filterSlot);
            }
        }

        // Add gas tank status slots below each filter
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                int displayTank = row * 9 + col;
                if (displayTank >= SLOTS_PER_PAGE) break;

                int xPos = 8 + col * 18;
                int yPos = 25 + row * 36 + 18; // 18px below filter slot

                GuiGasTankSlot tankSlot = new GuiGasTankSlot(
                    this.host, displayTank, displayTank, xPos, yPos,
                    () -> this.container.currentPage * SLOTS_PER_PAGE,
                    () -> this.container.maxSlotSize
                );
                tankSlot.setFontRenderer(this.fontRenderer);
                this.guiSlots.add(tankSlot);
            }
        }
    }

    @Override
    protected List<Target<?>> createJEITargets(Object ingredient) {
        GasStack gasStack = null;

        // Handle direct GasStack
        if (ingredient instanceof GasStack) gasStack = (GasStack) ingredient;

        // Handle IAEGasStack from MekanismEnergistics
        else if (ingredient instanceof IAEGasStack) {
            gasStack = ((IAEGasStack) ingredient).getGasStack();
        }
        // Handle ItemStack containing gas (gas tanks, canisters, etc.)
        else if (ingredient instanceof ItemStack) {
            gasStack = QuickAddHelper.getGasFromItemStack((ItemStack) ingredient);
        }

        if (gasStack == null) return new ArrayList<>();

        List<Target<?>> targets = new ArrayList<>();

        for (GuiCustomSlot slot : this.guiSlots) {
            if (!(slot instanceof GuiGasFilterSlot)) continue;

            final GuiGasFilterSlot filterSlot = (GuiGasFilterSlot) slot;

            Target<Object> target = new Target<Object>() {
                @Override
                @Nonnull
                public Rectangle getArea() {
                    return new Rectangle(getGuiLeft() + filterSlot.xPos(), getGuiTop() + filterSlot.yPos(), 16, 16);
                }

                @Override
                public void accept(@Nonnull Object ingredient) {
                    // Re-extract gas from the accepted ingredient
                    GasStack gas = null;
                    if (ingredient instanceof GasStack) {
                        gas = (GasStack) ingredient;
                    } else if (ingredient instanceof IAEGasStack) {
                        gas = ((IAEGasStack) ingredient).getGasStack();
                    } else if (ingredient instanceof ItemStack) {
                        gas = QuickAddHelper.getGasFromItemStack((ItemStack) ingredient);
                    }

                    if (gas == null) return;

                    IAEGasStack aeGas = AEGasStack.of(gas);
                    Map<Integer, IAEGasStack> map = new HashMap<>();
                    map.put(filterSlot.getSlot(), aeGas);
                    CellsNetworkHandler.INSTANCE.sendToServer(new PacketGasSlot(map));
                }
            };
            targets.add(target);
            mapTargetSlot.putIfAbsent(target, slot);
        }

        return targets;
    }

    @Override
    protected boolean handleQuickAdd(Slot hoveredSlot) {
        GasStack gas = QuickAddHelper.getGasUnderCursor(hoveredSlot);

        if (gas == null) {
            QuickAddHelper.sendNoValidError("gas");
            return false;
        }

        // Send the gas to the first available slot
        IAEGasStack aeGas = AEGasStack.of(gas);
        Map<Integer, IAEGasStack> map = new HashMap<>();

        // Find first empty slot on current page
        for (GuiCustomSlot slot : this.guiSlots) {
            if (!(slot instanceof GuiGasFilterSlot)) continue;

            GuiGasFilterSlot filterSlot = (GuiGasFilterSlot) slot;
            int absoluteSlot = filterSlot.getSlot();

            // Check if this slot is empty via container's client cache
            if (this.container.getClientFilterGas(absoluteSlot) == null) {
                map.put(absoluteSlot, aeGas);
                CellsNetworkHandler.INSTANCE.sendToServer(new PacketGasSlot(map));
                return true;
            }
        }

        QuickAddHelper.sendNoSpaceError();
        return false;
    }
}
