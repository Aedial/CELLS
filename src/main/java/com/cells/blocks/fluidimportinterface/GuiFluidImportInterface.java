package com.cells.blocks.fluidimportinterface;

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
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import appeng.api.parts.IPart;
import appeng.api.storage.data.IAEFluidStack;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiCustomSlot;
import appeng.client.gui.widgets.GuiTabButton;
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
import com.cells.gui.ImportInterfaceControlsHelper;
import com.cells.gui.QuickAddHelper;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.packets.PacketOpenGui;
import com.cells.network.packets.PacketQuickAddFluidFilter;


/**
 * GUI for the Fluid Import Interface.
 * <p>
 * Shows 4 rows of 9 filter slots (fluid-based filters using GuiFluidFilterSlot).
 * Storage tanks are rendered as visual indicators below each filter slot using GuiFluidImportTankSlot.
 * </p>
 * <p>
 * Extends AEBaseGui for proper fluid slot rendering.
 * Implements IJEIGhostIngredients for JEI drag and drop support.
 * Works with both TileFluidImportInterface (block) and PartFluidImportInterface (part).
 */
public class GuiFluidImportInterface extends AEBaseGui implements IJEIGhostIngredients {

    private final ContainerFluidImportInterface container;
    private final IFluidImportInterfaceInventoryHost host;
    private DynamicTooltipTabButton configButton;
    private DynamicTooltipTabButton pollingRateButton;
    // Use Object key type to avoid JEI class reference in field signature (JEI is optional)
    private final Map<Object, Object> mapTargetSlot = new HashMap<>();

    /**
     * Constructor for tile entity.
     */
    public GuiFluidImportInterface(final InventoryPlayer inventoryPlayer, final TileFluidImportInterface tile) {
        super(new ContainerFluidImportInterface(inventoryPlayer, tile));
        this.container = (ContainerFluidImportInterface) this.inventorySlots;
        this.host = tile;
        this.ySize = 256;
        this.xSize = 210;
    }

    /**
     * Constructor for part.
     */
    public GuiFluidImportInterface(final InventoryPlayer inventoryPlayer, final IPart part) {
        super(new ContainerFluidImportInterface(inventoryPlayer, part));
        this.container = (ContainerFluidImportInterface) this.inventorySlots;
        this.host = (IFluidImportInterfaceInventoryHost) part;
        this.ySize = 256;
        this.xSize = 210;
    }

    @Override
    public void initGui() {
        super.initGui();

        // Add fluid filter slots (fluid-based, not item-based)
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = row * 9 + col;
                if (slotIndex >= TileFluidImportInterface.FILTER_SLOTS) break;

                int xPos = 8 + col * 18;
                int filterY = 25 + row * 36;  // Filter slot position

                GuiFluidFilterSlot filterSlot = new GuiFluidFilterSlot(this.host, slotIndex, xPos, filterY);
                this.guiSlots.add(filterSlot);
            }
        }

        // Add fluid tank slots using AE2's guiSlots infrastructure
        // This automatically handles rendering, tooltips, and hover highlighting
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                int tankIndex = row * 9 + col;
                if (tankIndex >= TileFluidImportInterface.TANK_SLOTS) break;

                int xPos = 8 + col * 18;
                int yPos = 25 + row * 36 + 18;  // 18 pixels below filter slot

                GuiFluidImportTankSlot tankSlot = new GuiFluidImportTankSlot(this.host, this.container, tankIndex, tankIndex, xPos, yPos);
                tankSlot.setFontRenderer(this.fontRenderer);
                this.guiSlots.add(tankSlot);
            }
        }

        this.configButton = new DynamicTooltipTabButton(
            this.guiLeft + 154,
            this.guiTop,
            2 + 4 * 16,
            () -> I18n.format("gui.cells.import_interface.max_slot_size") + "\n\n"
                + I18n.format("gui.cells.import_interface.max_slot_size.fluids.tooltip", (int) this.container.maxSlotSize) + "\n"
                + I18n.format("gui.cells.import_interface.max_slot_size.tooltip"),
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
                    ? I18n.format("gui.cells.import_interface.polling_rate.adaptive.tooltip")
                    : I18n.format("gui.cells.import_interface.polling_rate.custom.tooltip", TileImportInterface.formatPollingRate(rate));
                return I18n.format("gui.cells.import_interface.polling_rate") + "\n\n"
                    + value + "\n"
                    + I18n.format("gui.cells.import_interface.polling_rate.tooltip");
            },
            this.itemRender
        );
        this.buttonList.add(this.pollingRateButton);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRenderer.drawString(I18n.format("gui.cells.import_fluid_interface.title"), 8, 6, 0x404040);

        // Draw controls help widget on the left side
        ImportInterfaceControlsHelper.drawControlsHelpWidget(
            this.fontRenderer,
            this.guiLeft,
            this.guiTop,
            this.ySize,
            true
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
            // Open the max slot size configuration GUI
            // Use part-specific GUI ID with side encoding for parts
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
            // Open the polling rate configuration GUI
            // Use part-specific GUI ID with side encoding for parts
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
        }
    }

    /**
     * Bind a texture from the CELLS mod assets.
     */
    public void bindCellsTexture(final String path) {
        this.mc.getTextureManager().bindTexture(new ResourceLocation(Tags.MODID, "textures/" + path));
    }

    /**
     * JEI ghost ingredient support - returns targets for dragging items from JEI.
     * Only accepts items that contain fluids.
     */
    @Override
    public List<Target<?>> getPhantomTargets(Object ingredient) {
        // Accept FluidStack directly (from JEI fluid tab)
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
                    // Send fluid slot update to server
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
        // Handle quick-add keybind
        if (KeyBindings.QUICK_ADD_TO_FILTER.isActiveAndMatches(keyCode)) {
            Slot hoveredSlot = this.getSlotUnderMouse();
            FluidStack fluid = QuickAddHelper.getFluidUnderCursor(hoveredSlot);

            if (fluid != null) {
                CellsNetworkHandler.INSTANCE.sendToServer(new PacketQuickAddFluidFilter(fluid));
            } else {
                // Show error that item doesn't contain fluid
                QuickAddHelper.sendNoFluidError();
            }

            return;
        }

        super.keyTyped(typedChar, keyCode);
    }
}
