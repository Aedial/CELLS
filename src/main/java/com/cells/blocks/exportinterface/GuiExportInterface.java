package com.cells.blocks.exportinterface;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

import appeng.api.parts.IPart;
import appeng.client.gui.AEBaseGui;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.container.slot.SlotFake;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.helpers.InventoryAction;
import appeng.util.item.AEItemStack;

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
import com.cells.network.packets.PacketQuickAddItemFilter;


/**
 * GUI for the Export Interface.
 * Shows 4 rows of 9 paired slots (filter on top, storage below).
 * <p>
 * Works with both TileExportInterface (block) and PartExportInterface (part).
 */
public class GuiExportInterface extends AEBaseGui implements IJEIGhostIngredients {

    private final ContainerExportInterface container;
    private final IExportInterfaceInventoryHost host;
    private DynamicTooltipTabButton configButton;
    private DynamicTooltipTabButton pollingRateButton;
    private GuiClearFiltersButton clearFiltersButton;
    private GuiPageNavigation pageNavigation;
    private final Map<Object, Object> mapTargetSlot = new HashMap<>();

    /**
     * Constructor for tile entity.
     */
    public GuiExportInterface(final InventoryPlayer inventoryPlayer, final TileExportInterface tile) {
        super(new ContainerExportInterface(inventoryPlayer, tile));
        this.container = (ContainerExportInterface) this.inventorySlots;
        this.host = tile;
        this.ySize = 256;
        this.xSize = 210;
    }

    /**
     * Constructor for part.
     */
    public GuiExportInterface(final InventoryPlayer inventoryPlayer, final IPart part) {
        super(new ContainerExportInterface(inventoryPlayer, part));
        this.container = (ContainerExportInterface) this.inventorySlots;
        this.host = (IExportInterfaceInventoryHost) part;
        this.ySize = 256;
        this.xSize = 210;
    }

    @Override
    public void initGui() {
        super.initGui();

        // Config button to open max slot size configuration screen
        this.configButton = new DynamicTooltipTabButton(
            this.guiLeft + 154,
            this.guiTop,
            2 + 4 * 16,
            () -> I18n.format("gui.cells.export_interface.max_slot_size") + "\n\n"
                + I18n.format("gui.cells.export_interface.max_slot_size.items.tooltip", (int) this.container.maxSlotSize) + "\n"
                + I18n.format("gui.cells.export_interface.max_slot_size.tooltip"),
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
        // For Export interface, clears all filters and sends items back to network
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
        this.fontRenderer.drawString(I18n.format("gui.cells.export_interface.title"), 8, 6, 0x404040);

        // Draw controls help widget on the left side
        ImportInterfaceControlsHelper.drawControlsHelpWidget(
            this.fontRenderer,
            this.guiLeft,
            this.guiTop,
            this.ySize,
            false,
            false
        );
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        // Use the same texture as import interface
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
    public void drawSlot(Slot slot) {
        if (slot instanceof SlotFake) {
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty()) {
                this.zLevel = 100.0F;
                this.itemRender.zLevel = 100.0F;

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

    @Override
    public List<Target<?>> getPhantomTargets(Object ingredient) {
        if (!(ingredient instanceof ItemStack)) return Collections.emptyList();

        List<Target<?>> targets = new ArrayList<>();
        ItemStack itemStack = (ItemStack) ingredient;

        for (Slot slot : this.inventorySlots.inventorySlots) {
            if (!(slot instanceof SlotFake)) continue;

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

    @SuppressWarnings("unchecked")
    @Override
    public Map<Target<?>, Object> getFakeSlotTargetMap() {
        return (Map<Target<?>, Object>) (Map<?, ?>) mapTargetSlot;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (KeyBindings.QUICK_ADD_TO_FILTER.isActiveAndMatches(keyCode)) {
            Slot hoveredSlot = this.getSlotUnderMouse();
            ItemStack item = QuickAddHelper.getItemUnderCursor(hoveredSlot);

            if (!item.isEmpty()) {
                CellsNetworkHandler.INSTANCE.sendToServer(new PacketQuickAddItemFilter(item));
                return;
            }
        }

        super.keyTyped(typedChar, keyCode);
    }
}
