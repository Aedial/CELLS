package com.cells.cells.creative.fluid;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import appeng.api.storage.data.IAEFluidStack;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiCustomSlot;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketFluidSlot;
import appeng.fluids.util.AEFluidStack;

import mezz.jei.api.gui.IGhostIngredientHandler.Target;

import com.cells.Tags;
import com.cells.client.KeyBindings;
import com.cells.gui.GuiClearFiltersButton;
import com.cells.gui.QuickAddHelper;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.packets.PacketQuickAddCreativeFluidFilter;


/**
 * GUI screen for the Creative ME Fluid Cell.
 * <p>
 * Layout (210x241):
 * - Title: "Creative Fluid Cell Filters" at top
 * - 9x7 grid of fluid filter slots starting at (8, 19)
 * - Clear filters button (right of hotbar)
 * - Player inventory at y=159
 * <p>
 * Implements IJEIGhostIngredients for JEI drag-drop support (fluids and fluid containers).
 */
public class GuiCreativeFluidCell extends AEBaseGui implements IJEIGhostIngredients {

    private static final ResourceLocation TEXTURE = new ResourceLocation(Tags.MODID, "textures/guis/creative_cell.png");

    private final ContainerCreativeFluidCell container;
    private final CreativeFluidCellTankAdapter tankAdapter;
    private GuiClearFiltersButton clearButton;
    private final Map<Target<?>, Object> mapTargetSlot = new HashMap<>();

    public GuiCreativeFluidCell(InventoryPlayer playerInv, EnumHand hand) {
        super(new ContainerCreativeFluidCell(playerInv, hand));
        this.container = (ContainerCreativeFluidCell) this.inventorySlots;
        this.tankAdapter = new CreativeFluidCellTankAdapter(this.container.getFilterHandler());
        this.xSize = 210;
        this.ySize = 241;
    }

    @Override
    public void initGui() {
        super.initGui();

        // Add 9x7 grid of fluid filter slots
        for (int row = 0; row < ContainerCreativeFluidCell.GRID_ROWS; row++) {
            for (int col = 0; col < ContainerCreativeFluidCell.GRID_COLS; col++) {
                int slotIndex = row * ContainerCreativeFluidCell.GRID_COLS + col;
                int x = ContainerCreativeFluidCell.FILTER_START_X + col * 18;
                int y = ContainerCreativeFluidCell.FILTER_START_Y + row * 18;

                GuiCreativeFluidFilterSlot filterSlot = new GuiCreativeFluidFilterSlot(
                    this.tankAdapter, slotIndex, x, y
                );
                this.guiSlots.add(filterSlot);
            }
        }

        // Clear filters button - positioned right of the hotbar, similar to Import/Export Interface
        // Player inventory starts at y=159, hotbar is 58px below top of player inventory section
        // Position: right side of GUI, aligned with hotbar
        this.clearButton = new GuiClearFiltersButton(0, this.guiLeft + 176 + 2, this.guiTop + 159 + 58,
            () -> I18n.format("gui.cells.creative_fluid_cell.clear") + "\n\n"
                + I18n.format("gui.cells.creative_fluid_cell.clear_tooltip"));
        this.buttonList.add(this.clearButton);
    }

    private void clearFilters() {
        container.clearAllFilters();
        // Send empty fluid map to clear all filters on server
        Map<Integer, IAEFluidStack> emptyMap = new HashMap<>();
        for (int i = 0; i < ContainerCreativeFluidCell.FILTER_SLOTS; i++) {
            emptyMap.put(i, null);
        }
        NetworkHandler.instance().sendToServer(new PacketFluidSlot(emptyMap));
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        // Title
        String title = I18n.format("gui.cells.creative_fluid_cell.title");
        this.fontRenderer.drawString(title, 8, 6, 0x404040);

        // "Inventory" label above player inventory slots
        this.fontRenderer.drawString(I18n.format("container.inventory"), 8, 148, 0x404040);
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.mc.getTextureManager().bindTexture(TEXTURE);
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (this.checkHotbarKeys(keyCode)) return;

        // Handle quick-add keybind (extract fluid from hovered item or fluid slot)
        if (KeyBindings.QUICK_ADD_TO_FILTER.isActiveAndMatches(keyCode)) {
            Slot hoveredSlot = this.getSlotUnderMouse();
            FluidStack fluid = QuickAddHelper.getFluidUnderCursor(hoveredSlot);

            if (fluid != null) {
                CellsNetworkHandler.INSTANCE.sendToServer(new PacketQuickAddCreativeFluidFilter(fluid));
            } else {
                QuickAddHelper.sendNoFluidError();
            }

            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void actionPerformed(@Nonnull final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        if (btn == this.clearButton) clearFilters();
    }

    // =====================
    // IJEIGhostIngredients implementation for JEI drag-drop support
    // Accepts both FluidStack (direct) and ItemStack (fluid extracted from container)
    // =====================

    @Override
    public List<Target<?>> getPhantomTargets(Object ingredient) {
        mapTargetSlot.clear();

        FluidStack fluidStack = null;

        if (ingredient instanceof FluidStack) {
            fluidStack = (FluidStack) ingredient;
        } else if (ingredient instanceof ItemStack) {
            fluidStack = FluidUtil.getFluidContained((ItemStack) ingredient);
        }

        if (fluidStack == null) return Collections.emptyList();

        List<Target<?>> targets = new ArrayList<>();

        // Add all fluid filter slots as valid targets
        for (GuiCustomSlot slot : this.guiSlots) {
            if (!(slot instanceof GuiCreativeFluidFilterSlot)) continue;

            final GuiCreativeFluidFilterSlot filterSlot = (GuiCreativeFluidFilterSlot) slot;

            Target<Object> target = new Target<Object>() {
                @Nonnull
                @Override
                public Rectangle getArea() {
                    return new Rectangle(getGuiLeft() + filterSlot.xPos(), getGuiTop() + filterSlot.yPos(), 16, 16);
                }

                @Override
                public void accept(@Nonnull Object ingredient) {
                    FluidStack fluid = null;

                    if (ingredient instanceof FluidStack) {
                        fluid = (FluidStack) ingredient;
                    } else if (ingredient instanceof ItemStack) {
                        fluid = FluidUtil.getFluidContained((ItemStack) ingredient);
                    }

                    if (fluid == null) return;

                    IAEFluidStack aeFluid = AEFluidStack.fromFluidStack(fluid);
                    Map<Integer, IAEFluidStack> map = new HashMap<>();
                    map.put(filterSlot.getSlot(), aeFluid);
                    NetworkHandler.instance().sendToServer(new PacketFluidSlot(map));
                }
            };

            targets.add(target);
            mapTargetSlot.put(target, slot);
        }

        return targets;
    }

    @Override
    public Map<Target<?>, Object> getFakeSlotTargetMap() {
        return mapTargetSlot;
    }
}
