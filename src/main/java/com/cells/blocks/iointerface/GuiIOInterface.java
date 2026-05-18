package com.cells.blocks.iointerface;

import java.io.IOException;
import java.util.Collections;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;

import appeng.api.AEApi;
import appeng.api.parts.IPart;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.widgets.GuiCustomSlot;
import appeng.fluids.util.AEFluidStack;

import com.cells.Tags;
import com.cells.blocks.interfacebase.AbstractResourceInterfaceGui;
import com.cells.blocks.interfacebase.fluid.FluidInterfaceLogic;
import com.cells.blocks.interfacebase.item.ItemInterfaceLogic;
import com.cells.gui.CellsGuiHandler;
import com.cells.gui.QuickAddHelper;
import com.cells.gui.slots.FluidFilterSlot;
import com.cells.gui.slots.FluidTankSlot;
import com.cells.gui.slots.ItemFilterSlot;
import com.cells.gui.slots.ItemStorageSlot;
import com.cells.integration.mekanismenergistics.IOGuiGasHelper;
import com.cells.integration.mekanismenergistics.MekanismEnergisticsIntegration;
import com.cells.integration.thaumicenergistics.IOGuiEssentiaHelper;
import com.cells.integration.thaumicenergistics.ThaumicEnergisticsIntegration;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.packets.PacketSwitchTab;
import com.cells.network.sync.PacketQuickAddFilter;
import com.cells.network.sync.ResourceType;


/**
 * GUI for the I/O Interface (Item I/O, Fluid I/O, etc.).
 * <p>
 * Displays two direction tabs (Import, Export) above the main GUI area using the same
 * DiskTerminal-style tab rendering as the combined interface. Each tab shows the resource
 * type's icon overlaid with an IO arrows texture (left half = import, right half = export).
 * <p>
 * Switching tabs rebuilds the filter and storage slots to reflect the new direction.
 */
public class GuiIOInterface
        extends AbstractResourceInterfaceGui<IIOInterfaceHost, ContainerIOInterface> {

    // ================================= Tab Constants (DiskTerminal style) =================================

    private static final int TAB_SIZE = 22;
    private static final int TAB_Y_OFFSET = -22;
    private static final int TAB_SPACING = 2;
    private static final int TAB_LEFT_OFFSET = 4;
    private static final int TAB_ICON_INSET = 3;

    private static final int COLOR_TAB_NORMAL   = 0xFF8B8B8B;
    private static final int COLOR_TAB_HOVER    = 0xFFA0A0A0;
    private static final int COLOR_TAB_SELECTED = 0xFFC6C6C6;

    // IO arrows overlay texture (64x32, left=import, right=export)
    private static final ResourceLocation IO_ARROWS_TEXTURE =
        new ResourceLocation(Tags.MODID, "textures/guis/io_arrows.png");

    // Arrow overlay UV coordinates within the 64x32 texture
    private static final int ARROW_U_IMPORT = 0;
    private static final int ARROW_U_EXPORT = 32;
    private static final int ARROW_V = 0;
    /** Each arrow occupies a 32x32 region in the texture; rendered at 16x16 on screen via drawScaledCustomSizeModalRect. */
    private static final int ARROW_TEX_SIZE = 32;

    /** Track the last known activeDirectionTab to detect @GuiSync changes. */
    private int lastActiveDirectionTab = -1;

    /** Index of the currently hovered tab, or -1 if none. */
    private int hoveredTabIndex = -1;

    /** Cached icon ItemStack for the resource type. */
    private ItemStack resourceIcon;

    // ================================= Constructors =================================

    public GuiIOInterface(final InventoryPlayer inventoryPlayer, final TileEntity tile) {
        super(
            new ContainerIOInterface(inventoryPlayer, tile),
            (IIOInterfaceHost) tile
        );
    }

    public GuiIOInterface(final InventoryPlayer inventoryPlayer, final IPart part) {
        super(
            new ContainerIOInterface(inventoryPlayer, part),
            (IIOInterfaceHost) part
        );
    }

    // ================================= Abstract method implementations =================================

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

    /**
     * Return the resource type's name for tooltip units (e.g. "item", "fluid").
     */
    @Override
    protected String getUnitTypeName() {
        return this.host.getResourceType().name().toLowerCase();
    }

    // ================================= Slot creation =================================

    @Override
    protected GuiCustomSlot createFilterSlotForIndex(int displaySlot, int x, int y) {
        ResourceType resType = this.host.getResourceType();

        switch (resType) {
            case FLUID:
                return new FluidFilterSlot(
                    slot -> getActiveFluidLogic().getFilter(slot),
                    displaySlot, x, y,
                    () -> this.container.currentPage * SLOTS_PER_PAGE
                );

            case GAS:
                if (MekanismEnergisticsIntegration.isModLoaded()) {
                    GuiCustomSlot gasSlot = IOGuiGasHelper.createGasFilterSlot(
                        this.host, displaySlot, x, y,
                        () -> this.container.currentPage * SLOTS_PER_PAGE);
                    if (gasSlot != null) return gasSlot;
                }
                break;

            case ESSENTIA:
                if (ThaumicEnergisticsIntegration.isModLoaded()) {
                    GuiCustomSlot essentiaSlot = IOGuiEssentiaHelper.createEssentiaFilterSlot(
                        this.host, displaySlot, x, y,
                        () -> this.container.currentPage * SLOTS_PER_PAGE);
                    if (essentiaSlot != null) return essentiaSlot;
                }
                break;

            case ITEM:
            default:
                return new ItemFilterSlot(
                    slot -> getActiveItemLogic().getFilter(slot),
                    displaySlot, x, y,
                    () -> this.container.currentPage * SLOTS_PER_PAGE
                );
        }

        // Fallback for unloaded integration mods
        return new ItemFilterSlot(
            slot -> getActiveItemLogic().getFilter(slot),
            displaySlot, x, y,
            () -> this.container.currentPage * SLOTS_PER_PAGE
        );
    }

    @Override
    protected GuiCustomSlot createTankSlotForIndex(int displaySlot, int x, int y) {
        ResourceType resType = this.host.getResourceType();

        switch (resType) {
            case FLUID: {
                FluidTankHostAdapter fluidAdapter = new FluidTankHostAdapter(this.host);
                return new FluidTankSlot<>(
                    fluidAdapter, displaySlot, displaySlot, x, y,
                    () -> this.container.currentPage * SLOTS_PER_PAGE,
                    () -> getEffectiveMaxSlotSizeForDisplay(displaySlot)
                );
            }

            case GAS: {
                if (MekanismEnergisticsIntegration.isModLoaded()) {
                    return IOGuiGasHelper.createGasTankSlot(
                        this.host, displaySlot, x, y,
                        () -> this.container.currentPage * SLOTS_PER_PAGE,
                        () -> getEffectiveMaxSlotSizeForDisplay(displaySlot)
                    );
                }
                break;
            }

            case ESSENTIA: {
                if (ThaumicEnergisticsIntegration.isModLoaded()) {
                    return IOGuiEssentiaHelper.createEssentiaTankSlot(
                        this.host, displaySlot, x, y,
                        () -> this.container.currentPage * SLOTS_PER_PAGE,
                        () -> getEffectiveMaxSlotSizeForDisplay(displaySlot)
                    );
                }
                break;
            }

            case ITEM:
            default: {
                ItemStorageHostAdapter itemAdapter = new ItemStorageHostAdapter(this.host);
                return new ItemStorageSlot<>(
                    itemAdapter, displaySlot, displaySlot, x, y,
                    () -> this.container.currentPage * SLOTS_PER_PAGE,
                    () -> getEffectiveMaxSlotSizeForDisplay(displaySlot)
                );
            }
        }

        // Fallback for unloaded integration mods
        ItemStorageHostAdapter fallbackAdapter = new ItemStorageHostAdapter(this.host);
        return new ItemStorageSlot<>(
            fallbackAdapter, displaySlot, displaySlot, x, y,
            () -> this.container.currentPage * SLOTS_PER_PAGE,
            () -> getEffectiveMaxSlotSizeForDisplay(displaySlot)
        );
    }

    // ================================= Init =================================

    @Override
    public void initGui() {
        super.initGui();
        this.lastActiveDirectionTab = this.container.activeDirectionTab;
        initResourceIcon();
    }

    // ================================= Draw =================================

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Detect @GuiSync tab changes and rebuild slots
        if (this.container.activeDirectionTab != this.lastActiveDirectionTab) {
            this.lastActiveDirectionTab = this.container.activeDirectionTab;
            this.guiSlots.clear();
            this.createResourceSlots();
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

        // Draw tab tooltip
        if (this.hoveredTabIndex >= 0) {
            String tabName;
            if (this.hoveredTabIndex == IIOInterfaceHost.TAB_IMPORT) {
                tabName = I18n.format("cells.direction.import");
            } else {
                tabName = I18n.format("cells.direction.export");
            }
            this.drawHoveringText(Collections.singletonList(tabName), mouseX, mouseY);
        }
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawBG(offsetX, offsetY, mouseX, mouseY);
        drawTabs(offsetX, offsetY, mouseX, mouseY);
    }

    // ================================= Tab click handling =================================

    @Override
    protected void mouseClicked(int xCoord, int yCoord, int btn) throws IOException {
        if (btn == 0 && handleTabClick(xCoord, yCoord)) return;
        super.mouseClicked(xCoord, yCoord, btn);
    }

    // ================================= Quick-add =================================

    @Override
    protected boolean handleQuickAdd(Slot hoveredSlot) {
        ResourceType resType = this.host.getResourceType();

        switch (resType) {
            case ITEM: {
                ItemStack item = QuickAddHelper.getItemUnderCursor(hoveredSlot);
                if (!item.isEmpty()) {
                    IAEItemStack iaeItem = AEApi.instance().storage()
                        .getStorageChannel(IItemStorageChannel.class).createStack(item);
                    CellsNetworkHandler.INSTANCE.sendToServer(
                        new PacketQuickAddFilter(ResourceType.ITEM, iaeItem));
                    return true;
                }
                break;
            }

            case FLUID: {
                FluidStack fluid = QuickAddHelper.getFluidUnderCursor(hoveredSlot);
                if (fluid != null) {
                    CellsNetworkHandler.INSTANCE.sendToServer(
                        new PacketQuickAddFilter(ResourceType.FLUID, AEFluidStack.fromFluidStack(fluid)));
                    return true;
                }
                if (QuickAddHelper.hasAnythingUnderCursor(hoveredSlot)) {
                    QuickAddHelper.sendNoValidError("fluid");
                }
                return true;
            }

            case GAS: {
                if (MekanismEnergisticsIntegration.isModLoaded()) {
                    return IOGuiGasHelper.handleGasQuickAdd(hoveredSlot);
                }
                break;
            }

            case ESSENTIA: {
                if (ThaumicEnergisticsIntegration.isModLoaded()) {
                    return IOGuiEssentiaHelper.handleEssentiaQuickAdd(hoveredSlot);
                }
                break;
            }

            default:
                break;
        }

        return false;
    }

    // ================================= Tab Drawing =================================

    /**
     * Draw direction tabs (Import, Export) above the GUI.
     * Each tab shows the resource type's icon with an IO arrow overlay on top.
     */
    private void drawTabs(int offsetX, int offsetY, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();

        int tabY = offsetY + TAB_Y_OFFSET;
        this.hoveredTabIndex = -1;

        for (int i = 0; i < 2; i++) {
            int tabX = offsetX + TAB_LEFT_OFFSET + (i * (TAB_SIZE + TAB_SPACING));
            boolean isSelected = (i == this.container.activeDirectionTab);
            boolean isHovered = mouseX >= tabX && mouseX < tabX + TAB_SIZE
                && mouseY >= tabY && mouseY < tabY + TAB_SIZE;

            if (isHovered) this.hoveredTabIndex = i;

            // Tab background
            int bgColor;
            if (isSelected) {
                bgColor = COLOR_TAB_SELECTED;
            } else if (isHovered) {
                bgColor = COLOR_TAB_HOVER;
            } else {
                bgColor = COLOR_TAB_NORMAL;
            }
            drawRect(tabX, tabY, tabX + TAB_SIZE, tabY + TAB_SIZE, bgColor);

            // 3D border
            drawRect(tabX, tabY, tabX + TAB_SIZE, tabY + 1, 0xFFFFFFFF);                       // top
            drawRect(tabX, tabY, tabX + 1, tabY + TAB_SIZE, 0xFFFFFFFF);                        // left
            drawRect(tabX + TAB_SIZE - 1, tabY, tabX + TAB_SIZE, tabY + TAB_SIZE, 0xFF555555);  // right

            if (isSelected) {
                drawRect(tabX + 1, tabY + TAB_SIZE - 1, tabX + TAB_SIZE - 1, tabY + TAB_SIZE, COLOR_TAB_SELECTED);
            } else {
                drawRect(tabX, tabY + TAB_SIZE - 1, tabX + TAB_SIZE, tabY + TAB_SIZE, 0xFF555555);
            }

            // Render the resource type icon (16x16) centered within the 22x22 tab
            int iconX = tabX + TAB_ICON_INSET;
            int iconY = tabY + TAB_ICON_INSET;

            if (this.resourceIcon != null && !this.resourceIcon.isEmpty()) {
                RenderHelper.enableGUIStandardItemLighting();
                this.itemRender.renderItemIntoGUI(this.resourceIcon, iconX, iconY);
                RenderHelper.disableStandardItemLighting();
                GlStateManager.disableLighting();
            }

            // Overlay the IO arrow on top of the icon
            GlStateManager.enableBlend();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            this.mc.getTextureManager().bindTexture(IO_ARROWS_TEXTURE);

            // Select left (import) or right (export) half of the 64x32 texture
            // The texture is 64x32, each arrow is 16x16, centered in its 32x16 half
            int arrowU = (i == IIOInterfaceHost.TAB_IMPORT) ? ARROW_U_IMPORT : ARROW_U_EXPORT;

            // Draw the 32x32 source arrow region scaled down to 16x16 on screen.
            GlStateManager.pushMatrix();
            GlStateManager.translate(0, 0, 400);
            drawScaledCustomSizeModalRect(
                iconX, iconY,
                arrowU, ARROW_V,
                ARROW_TEX_SIZE, ARROW_TEX_SIZE,
                ARROW_TEX_SIZE / 2, ARROW_TEX_SIZE / 2,
                64, 32
            );
            GlStateManager.popMatrix();
        }

        // Restore GL state
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
    }

    /**
     * Handle a click in the tab area.
     */
    private boolean handleTabClick(int mouseX, int mouseY) {
        int tabY = this.guiTop + TAB_Y_OFFSET;
        if (mouseY < tabY || mouseY >= tabY + TAB_SIZE) return false;

        for (int i = 0; i < 2; i++) {
            int tabX = this.guiLeft + TAB_LEFT_OFFSET + (i * (TAB_SIZE + TAB_SPACING));

            if (mouseX >= tabX && mouseX < tabX + TAB_SIZE) {
                if (i == this.container.activeDirectionTab) return true;

                // Send tab switch to server (reuse PacketSwitchTab with direction ordinal)
                // The ordinal maps to: 0=import, 1=export
                // We use ResourceType ordinals 0 and 1 which happen to be ITEM and FLUID,
                // but the handler will check if the container is ContainerIOInterface
                // and treat the ordinal as a direction tab index.
                CellsNetworkHandler.INSTANCE.sendToServer(new PacketSwitchTab(i));

                // Optimistic client-side update
                this.container.onClientTabSwitch(i);
                this.lastActiveDirectionTab = i;

                // Rebuild resource slots for the new tab
                this.guiSlots.clear();
                this.createResourceSlots();
                return true;
            }
        }

        return false;
    }

    // ================================= Tab Icons =================================

    /**
     * Initialize the resource type icon based on host's resource type.
     */
    private void initResourceIcon() {
        ResourceType resType = this.host.getResourceType();

        switch (resType) {
            case ITEM:
                this.resourceIcon = new ItemStack(Blocks.CHEST);
                break;
            case FLUID:
                this.resourceIcon = new ItemStack(Items.WATER_BUCKET);
                break;
            case GAS:
                if (Loader.isModLoaded("mekanism")) {
                    this.resourceIcon = getGasIcon();
                } else {
                    this.resourceIcon = new ItemStack(Items.GLASS_BOTTLE);
                }
                break;
            case ESSENTIA:
                if (Loader.isModLoaded("thaumcraft")) {
                    this.resourceIcon = getEssentiaIcon();
                } else {
                    this.resourceIcon = new ItemStack(Items.GLASS_BOTTLE);
                }
                break;
            default:
                this.resourceIcon = new ItemStack(Blocks.BARRIER);
                break;
        }
    }

    @Optional.Method(modid = "mekanism")
    private static ItemStack getGasIcon() {
        return new ItemStack(mekanism.common.MekanismBlocks.GasTank);
    }

    @Optional.Method(modid = "thaumcraft")
    private static ItemStack getEssentiaIcon() {
        Block jar = thaumcraft.api.blocks.BlocksTC.jarNormal;
        if (jar != null) return new ItemStack(jar);
        return new ItemStack(Items.GLASS_BOTTLE);
    }

    // ================================= Logic Accessors =================================

    /**
     * Get the active tab's logic as ItemInterfaceLogic (for filter slot access).
     */
    private ItemInterfaceLogic getActiveItemLogic() {
        return (ItemInterfaceLogic) this.host.getActiveLogic();
    }

    /**
     * Get the active tab's logic as FluidInterfaceLogic (for filter slot access).
     */
    private FluidInterfaceLogic getActiveFluidLogic() {
        return (FluidInterfaceLogic) this.host.getActiveLogic();
    }

    // ================================= Host Adapters =================================

    /**
     * Adapter for FluidTankSlot that delegates to the active tab's fluid logic.
     */
    private static class FluidTankHostAdapter implements FluidTankSlot.IFluidTankHost {
        private final IIOInterfaceHost host;

        FluidTankHostAdapter(IIOInterfaceHost host) {
            this.host = host;
        }

        @Override
        public FluidStack getFluidInTank(int tankIndex) {
            return ((FluidInterfaceLogic) this.host.getActiveLogic()).getFluidInTank(tankIndex);
        }

        @Override
        public long getFluidAmount(int tankIndex) {
            return ((FluidInterfaceLogic) this.host.getActiveLogic()).getSlotAmount(tankIndex);
        }

        @Override
        public String getTypeName() {
            return "fluid";
        }

        @Override
        public boolean isExport() {
            return this.host.isExport();
        }
    }

    /**
     * Adapter for ItemStorageSlot that delegates to the active tab's item logic.
     */
    private static class ItemStorageHostAdapter implements ItemStorageSlot.IItemStorageHost {
        private final IIOInterfaceHost host;

        ItemStorageHostAdapter(IIOInterfaceHost host) {
            this.host = host;
        }

        @Override
        public ItemStack getItemInStorage(int slotIndex) {
            return ((ItemInterfaceLogic) this.host.getActiveLogic()).getItemInSlot(slotIndex);
        }

        @Override
        public long getItemAmount(int slotIndex) {
            return ((ItemInterfaceLogic) this.host.getActiveLogic()).getSlotAmount(slotIndex);
        }

        @Override
        public String getTypeName() {
            return "item";
        }

        @Override
        public boolean isExport() {
            return this.host.isExport();
        }
    }
}
