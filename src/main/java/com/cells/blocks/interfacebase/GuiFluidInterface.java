package com.cells.blocks.interfacebase;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import appeng.api.parts.IPart;
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
 * Unified GUI for both Fluid Import Interface and Fluid Export Interface.
 * Shows 4 rows of 9 paired custom slots (fluid filter on top, tank indicator below).
 * <p>
 * Uses the host's {@link IFluidInterfaceHost#isExport()} to parameterize import/export differences:
 * <ul>
 *   <li>Title lang key from {@link IFluidInterfaceHost#getGuiTitleLangKey()}</li>
 *   <li>Controls help widget shows import or export variant</li>
 *   <li>Tooltip lang key prefix: "import_interface" or "export_interface"</li>
 * </ul>
 * <p>
 * Implements IJEIGhostIngredients for JEI drag and drop support (FluidStack and ItemStack→FluidStack).
 * Works with both tile entities and parts via {@link IFluidInterfaceHost}.
 */
public class GuiFluidInterface extends AEBaseGui implements IJEIGhostIngredients {

    private final ResourceLocation BACKGROUND_TEXTURE = new ResourceLocation(Tags.MODID, "textures/guis/import_interface.png");

    private final ContainerFluidInterface container;
    private final IFluidInterfaceHost host;
    private final String langPrefix; // "import_interface" or "export_interface"
    private DynamicTooltipTabButton configButton;
    private DynamicTooltipTabButton pollingRateButton;
    private GuiClearFiltersButton clearFiltersButton;
    private GuiPageNavigation pageNavigation;
    // Use Object key type to avoid JEI class reference in field signature (JEI is optional)
    private final Map<Object, Object> mapTargetSlot = new HashMap<>();

    /**
     * Constructor for tile entity.
     */
    public GuiFluidInterface(final InventoryPlayer inventoryPlayer, final TileEntity tile) {
        super(new ContainerFluidInterface(inventoryPlayer, tile));
        this.container = (ContainerFluidInterface) this.inventorySlots;
        this.host = (IFluidInterfaceHost) tile;
        this.langPrefix = host.isExport() ? "export_interface" : "import_interface";
        this.ySize = 256;
        this.xSize = 210;
    }

    /**
     * Constructor for part.
     */
    public GuiFluidInterface(final InventoryPlayer inventoryPlayer, final IPart part) {
        super(new ContainerFluidInterface(inventoryPlayer, part));
        this.container = (ContainerFluidInterface) this.inventorySlots;
        this.host = (IFluidInterfaceHost) part;
        this.langPrefix = host.isExport() ? "export_interface" : "import_interface";
        this.ySize = 256;
        this.xSize = 210;
    }

    @Override
    public void initGui() {
        super.initGui();

        final int SLOTS_PER_PAGE = 36;

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

        // Config button to open max slot size configuration screen
        this.configButton = new DynamicTooltipTabButton(
            this.guiLeft + 154,
            this.guiTop,
            2 + 4 * 16,
            () -> I18n.format("gui.cells." + langPrefix + ".max_slot_size") + "\n\n"
                + I18n.format("gui.cells." + langPrefix + ".max_slot_size.fluids.tooltip", (int) this.container.maxSlotSize) + "\n"
                + I18n.format("gui.cells." + langPrefix + ".max_slot_size.tooltip"),
            this.itemRender
        );
        this.buttonList.add(this.configButton);

        // Polling rate button
        this.pollingRateButton = new DynamicTooltipTabButton(
            this.guiLeft + 154 - 22,
            this.guiTop,
            2 + 5 * 16,
            () -> {
                int rate = (int) this.container.pollingRate;
                String value = rate <= 0
                    ? I18n.format("gui.cells." + langPrefix + ".polling_rate.adaptive.tooltip")
                    : I18n.format("gui.cells." + langPrefix + ".polling_rate.custom.tooltip", ItemInterfaceLogic.formatPollingRate(rate));
                return I18n.format("gui.cells." + langPrefix + ".polling_rate") + "\n\n"
                    + value + "\n"
                    + I18n.format("gui.cells." + langPrefix + ".polling_rate.tooltip");
            },
            this.itemRender
        );
        this.buttonList.add(this.pollingRateButton);

        // Clear filters button
        this.clearFiltersButton = new GuiClearFiltersButton(
            2,
            this.guiLeft + 186,
            this.guiTop + 232,
            () -> I18n.format("gui.cells." + langPrefix + ".clear_filters") + "\n\n"
                + I18n.format("gui.cells." + langPrefix + ".clear_filters.tooltip")
        );
        this.buttonList.add(this.clearFiltersButton);

        // Page navigation (only visible when capacity cards are installed)
        this.pageNavigation = new GuiPageNavigation(
            3,
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
        this.fontRenderer.drawString(I18n.format(this.host.getGuiTitleLangKey()), 8, 6, 0x404040);

        // Draw controls help widget on the left side
        // isFluid=true, isImport=!isExport
        ImportInterfaceControlsHelper.drawControlsHelpWidget(
            this.fontRenderer,
            this.guiLeft,
            this.guiTop,
            this.ySize,
            true,
            !this.host.isExport()
        );
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.mc.getTextureManager().bindTexture(BACKGROUND_TEXTURE);
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
            CellsNetworkHandler.INSTANCE.sendToServer(new PacketClearFilters());
        }
    }

    /**
     * JEI ghost ingredient support.
     * Accepts both FluidStack (direct) and ItemStack (fluid extracted from container).
     */
    @Override
    public List<Target<?>> getPhantomTargets(Object ingredient) {
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

    @SuppressWarnings("unchecked")
    @Override
    public Map<Target<?>, Object> getFakeSlotTargetMap() {
        return (Map<Target<?>, Object>) (Map<?, ?>) mapTargetSlot;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        // Handle quick-add keybind (extract fluid from hovered item or fluid slot)
        if (KeyBindings.QUICK_ADD_TO_FILTER.isActiveAndMatches(keyCode)) {
            Slot hoveredSlot = this.getSlotUnderMouse();
            FluidStack fluid = QuickAddHelper.getFluidUnderCursor(hoveredSlot);

            if (fluid != null) {
                CellsNetworkHandler.INSTANCE.sendToServer(new PacketQuickAddFluidFilter(fluid));
            } else {
                QuickAddHelper.sendNoFluidError();
            }

            return;
        }

        super.keyTyped(typedChar, keyCode);
    }
}
