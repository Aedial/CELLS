package com.cells.blocks.interfacebase.fluid;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import com.cells.blocks.interfacebase.AbstractResourceInterfaceGui;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import appeng.api.parts.IPart;
import appeng.api.storage.data.IAEFluidStack;
import appeng.client.gui.widgets.GuiCustomSlot;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketFluidSlot;
import appeng.fluids.util.AEFluidStack;

import mezz.jei.api.gui.IGhostIngredientHandler.Target;

import com.cells.gui.CellsGuiHandler;
import com.cells.gui.QuickAddHelper;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.packets.PacketQuickAddFluidFilter;


/**
 * GUI for both Fluid Import Interface and Fluid Export Interface.
 * Extends the abstract base to provide fluid-specific slot creation and JEI handling.
 */
public class GuiFluidInterface extends AbstractResourceInterfaceGui<IFluidInterfaceHost, ContainerFluidInterface> {

    /**
     * Constructor for tile entity.
     */
    public GuiFluidInterface(final InventoryPlayer inventoryPlayer, final TileEntity tile) {
        super(new ContainerFluidInterface(inventoryPlayer, tile), (IFluidInterfaceHost) tile);
    }

    /**
     * Constructor for part.
     */
    public GuiFluidInterface(final InventoryPlayer inventoryPlayer, final IPart part) {
        super(new ContainerFluidInterface(inventoryPlayer, part), (IFluidInterfaceHost) part);
    }

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
        // Add fluid filter slots (4 rows x 9 cols)
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                int displaySlot = row * 9 + col;
                if (displaySlot >= SLOTS_PER_PAGE) break;

                int xPos = 8 + col * 18;
                int filterY = 25 + row * 36;

                GuiFluidFilterSlot filterSlot = new GuiFluidFilterSlot(
                    this.host, displaySlot, xPos, filterY,
                    () -> this.container.currentPage * SLOTS_PER_PAGE
                );
                this.guiSlots.add(filterSlot);
            }
        }

        // Add fluid tank status slots below each filter
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                int displayTank = row * 9 + col;
                if (displayTank >= SLOTS_PER_PAGE) break;

                int xPos = 8 + col * 18;
                int yPos = 25 + row * 36 + 18; // 18px below filter slot

                GuiFluidTankSlot tankSlot = new GuiFluidTankSlot(
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
        FluidStack fluidStack = null;

        if (ingredient instanceof FluidStack) {
            fluidStack = (FluidStack) ingredient;
        } else if (ingredient instanceof ItemStack) {
            fluidStack = FluidUtil.getFluidContained((ItemStack) ingredient);
        }

        if (fluidStack == null) return new ArrayList<>();

        final FluidStack finalFluid = fluidStack;
        List<Target<?>> targets = new ArrayList<>();

        for (GuiCustomSlot slot : this.guiSlots) {
            if (!(slot instanceof GuiFluidFilterSlot)) continue;

            final GuiFluidFilterSlot filterSlot = (GuiFluidFilterSlot) slot;

            Target<Object> target = new Target<Object>() {
                @Override
                @Nonnull
                public Rectangle getArea() {
                    return new Rectangle(getGuiLeft() + filterSlot.xPos(), getGuiTop() + filterSlot.yPos(), 16, 16);
                }

                @Override
                public void accept(@Nonnull Object ingredient) {
                    IAEFluidStack aeFluid = AEFluidStack.fromFluidStack(finalFluid);
                    Map<Integer, IAEFluidStack> map = new HashMap<>();
                    map.put(filterSlot.getSlot(), aeFluid);
                    NetworkHandler.instance().sendToServer(new PacketFluidSlot(map));
                }
            };
            targets.add(target);
            mapTargetSlot.putIfAbsent(target, slot);
        }

        return targets;
    }

    @Override
    protected boolean handleQuickAdd(Slot hoveredSlot) {
        FluidStack fluid = QuickAddHelper.getFluidUnderCursor(hoveredSlot);

        if (fluid != null) {
            CellsNetworkHandler.INSTANCE.sendToServer(new PacketQuickAddFluidFilter(fluid));
            return true;
        }

        QuickAddHelper.sendNoValidError("fluid");
        return true;
    }
}
