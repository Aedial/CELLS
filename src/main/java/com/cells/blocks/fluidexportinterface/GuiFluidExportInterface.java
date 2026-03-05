package com.cells.blocks.fluidexportinterface;

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
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import appeng.api.parts.IPart;
import appeng.api.storage.data.IAEFluidStack;
import appeng.client.gui.AEBaseGui;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketFluidSlot;
import appeng.fluids.util.AEFluidStack;

import mezz.jei.api.gui.IGhostIngredientHandler.Target;

import com.cells.Tags;
import com.cells.blocks.importinterface.TileImportInterface;
import com.cells.client.KeyBindings;
import com.cells.gui.CellsGuiHandler;
import com.cells.gui.DynamicTooltipTabButton;
import com.cells.gui.GuiClearFiltersButton;
import com.cells.gui.GuiPageNavigation;
import com.cells.gui.ImportInterfaceControlsHelper;
import com.cells.gui.QuickAddHelper;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.packets.PacketChangePage;
import com.cells.network.packets.PacketClearFilters;
import com.cells.network.packets.PacketOpenGui;
import com.cells.network.packets.PacketQuickAddFluidFilter;


/**
 * GUI for the Fluid Export Interface.
 * <p>
 * Shows 4 rows of 9 filter slots (fluid-based filters using GuiFluidFilterSlot).
 * Storage tanks are rendered as visual indicators below each filter slot using GuiFluidExportTankSlot.
 * </p>
 */
public class GuiFluidExportInterface extends AEBaseGui implements IJEIGhostIngredients {

    private final ContainerFluidExportInterface container;
    private final IFluidExportInterfaceInventoryHost host;
    private DynamicTooltipTabButton configButton;
    private DynamicTooltipTabButton pollingRateButton;
    private GuiClearFiltersButton clearFiltersButton;
    private GuiPageNavigation pageNavigation;
    private final Map<Object, Object> mapTargetSlot = new HashMap<>();

    /**
     * Constructor for tile entity.
     */
    public GuiFluidExportInterface(final InventoryPlayer inventoryPlayer, final TileFluidExportInterface tile) {
        super(new ContainerFluidExportInterface(inventoryPlayer, tile));
        this.container = (ContainerFluidExportInterface) this.inventorySlots;
        this.host = tile;
        this.ySize = 256;
        this.xSize = 210;
    }

    /**
     * Constructor for part.
     */
    public GuiFluidExportInterface(final InventoryPlayer inventoryPlayer, final IPart part) {
        super(new ContainerFluidExportInterface(inventoryPlayer, part));
        this.container = (ContainerFluidExportInterface) this.inventorySlots;
        this.host = (IFluidExportInterfaceInventoryHost) part;
        this.ySize = 256;
        this.xSize = 210;
    }

    @Override
    public void initGui() {
        super.initGui();

        // Slots per page for pagination (4 rows x 9 cols)
        final int SLOTS_PER_PAGE = 36;

        // Add fluid filter slots with pagination support
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

        // Add fluid tank slots with pagination support
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                int displayTank = row * 9 + col;
                if (displayTank >= SLOTS_PER_PAGE) break;

                int xPos = 8 + col * 18;
                int yPos = 25 + row * 36 + 18;

                GuiFluidExportTankSlot tankSlot = new GuiFluidExportTankSlot(
                    this.host, this.container, displayTank, displayTank, xPos, yPos,
                    () -> this.container.currentPage * SLOTS_PER_PAGE
                );
                tankSlot.setFontRenderer(this.fontRenderer);
                this.guiSlots.add(tankSlot);
            }
        }

        this.configButton = new DynamicTooltipTabButton(
            this.guiLeft + 154,
            this.guiTop,
            2 + 4 * 16,
            () -> I18n.format("gui.cells.export_interface.max_slot_size") + "\n\n"
                + I18n.format("gui.cells.export_interface.max_slot_size.fluids.tooltip", (int) this.container.maxSlotSize) + "\n"
                + I18n.format("gui.cells.export_interface.max_slot_size.tooltip"),
            this.itemRender
        );
        this.buttonList.add(this.configButton);

        this.pollingRateButton = new DynamicTooltipTabButton(
            this.guiLeft + 154 - 22,
            this.guiTop,
            2 + 5 * 16,
            () -> {
                int rate = (int) this.container.pollingRate;
                String value = rate <= 0
                    ? I18n.format("gui.cells.export_interface.polling_rate.adaptive.tooltip")
                    : I18n.format("gui.cells.export_interface.polling_rate.custom.tooltip", TileImportInterface.formatPollingRate(rate));
                return I18n.format("gui.cells.export_interface.polling_rate") + "\n\n"
                    + value + "\n"
                    + I18n.format("gui.cells.export_interface.polling_rate.tooltip");
            },
            this.itemRender
        );
        this.buttonList.add(this.pollingRateButton);

        // Clear filters button (right of the hotbar)
        // For Export interface, clears all filters and sends fluids back to network
        this.clearFiltersButton = new GuiClearFiltersButton(
            2,  // Button ID
            this.guiLeft + 186,
            this.guiTop + 232,
            () -> I18n.format("gui.cells.export_interface.clear_filters") + "\n\n"
                + I18n.format("gui.cells.export_interface.clear_filters.tooltip")
        );
        this.buttonList.add(this.clearFiltersButton);

        // Page navigation (only visible when capacity cards are installed)
        // Position: 26x10 at (181, 3) relative to GUI
        this.pageNavigation = new GuiPageNavigation(
            3,  // Button ID
            this.guiLeft + 181,
            this.guiTop + 3,
            () -> this.container.currentPage,
            () -> this.container.totalPages,
            () -> {
                this.container.prevPage();
                CellsNetworkHandler.INSTANCE.sendToServer(new PacketChangePage(this.container.currentPage));
            },
            () -> {
                this.container.nextPage();
                CellsNetworkHandler.INSTANCE.sendToServer(new PacketChangePage(this.container.currentPage));
            }
        );
        this.buttonList.add(this.pageNavigation);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRenderer.drawString(I18n.format("gui.cells.export_fluid_interface.title"), 8, 6, 0x404040);

        ImportInterfaceControlsHelper.drawControlsHelpWidget(
            this.fontRenderer,
            this.guiLeft,
            this.guiTop,
            this.ySize,
            true,
            false
        );
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.bindCellsTexture("guis/import_interface.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
    }

    @Override
    protected void actionPerformed(@Nonnull final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        BlockPos pos = this.host.getHostPos();

        if (btn == this.configButton) {
            if (this.host.isPart()) {
                CellsNetworkHandler.INSTANCE.sendToServer(new PacketOpenGui(
                    pos,
                    CellsGuiHandler.GUI_PART_MAX_SLOT_SIZE,
                    this.host.getPartSide()
                ));
            } else {
                CellsNetworkHandler.INSTANCE.sendToServer(new PacketOpenGui(
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    CellsGuiHandler.GUI_MAX_SLOT_SIZE
                ));
            }
            return;
        }

        if (btn == this.pollingRateButton) {
            if (this.host.isPart()) {
                CellsNetworkHandler.INSTANCE.sendToServer(new PacketOpenGui(
                    pos,
                    CellsGuiHandler.GUI_PART_POLLING_RATE,
                    this.host.getPartSide()
                ));
            } else {
                CellsNetworkHandler.INSTANCE.sendToServer(new PacketOpenGui(
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    CellsGuiHandler.GUI_POLLING_RATE
                ));
            }

            return;
        }

        if (btn == this.clearFiltersButton) {
            // Clear all filters (server handles orphan return to network)
            CellsNetworkHandler.INSTANCE.sendToServer(new PacketClearFilters());
        }
    }

    public void bindCellsTexture(final String path) {
        this.mc.getTextureManager().bindTexture(new ResourceLocation(Tags.MODID, "textures/" + path));
    }

    @Override
    public List<Target<?>> getPhantomTargets(Object ingredient) {
        // Support JEI fluid dragging for filter slots
        if (!(ingredient instanceof FluidStack)) return Collections.emptyList();

        List<Target<?>> targets = new ArrayList<>();
        FluidStack fluidStack = (FluidStack) ingredient;

        for (Object slot : this.guiSlots) {
            if (!(slot instanceof GuiFluidFilterSlot)) continue;

            GuiFluidFilterSlot filterSlot = (GuiFluidFilterSlot) slot;

            Target<Object> target = new Target<Object>() {
                @Override
                @Nonnull
                public Rectangle getArea() {
                    return new Rectangle(getGuiLeft() + filterSlot.xPos(), getGuiTop() + filterSlot.yPos(), 16, 16);
                }

                @Override
                public void accept(@Nonnull Object ingredient) {
                    if (ingredient instanceof FluidStack) {
                        IAEFluidStack aeFluid = AEFluidStack.fromFluidStack((FluidStack) ingredient);
                        NetworkHandler.instance().sendToServer(new PacketFluidSlot(Collections.singletonMap(filterSlot.getSlot(), aeFluid)));
                    }
                }
            };
            targets.add(target);
            mapTargetSlot.putIfAbsent(target, filterSlot);
        }

        return targets;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<Target<?>, Object> getFakeSlotTargetMap() {
        return (Map<Target<?>, Object>) (Map<?, ?>) mapTargetSlot;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (KeyBindings.QUICK_ADD_TO_FILTER.isActiveAndMatches(keyCode)) {
            ItemStack hoveredItem = QuickAddHelper.getItemUnderCursor(this.getSlotUnderMouse());
            if (!hoveredItem.isEmpty()) {
                FluidStack fluid = FluidUtil.getFluidContained(hoveredItem);
                if (fluid != null) {
                    CellsNetworkHandler.INSTANCE.sendToServer(new PacketQuickAddFluidFilter(fluid));
                    return;
                }
            }
        }

        super.keyTyped(typedChar, keyCode);
    }
}
