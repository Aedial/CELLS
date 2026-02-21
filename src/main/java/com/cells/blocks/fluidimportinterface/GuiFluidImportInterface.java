package com.cells.blocks.fluidimportinterface;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import appeng.api.storage.data.IAEFluidStack;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiCustomSlot;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketFluidSlot;
import appeng.fluids.util.AEFluidStack;

import com.cells.Tags;
import com.cells.gui.CellsGuiHandler;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.packets.PacketOpenGui;

import mezz.jei.api.gui.IGhostIngredientHandler;


/**
 * GUI for the Fluid Import Interface.
 * <p>
 * Shows 4 rows of 9 filter slots (fluid-based filters using GuiFluidFilterSlot).
 * Storage tanks are rendered as visual indicators below each filter slot using GuiFluidImportTankSlot.
 * </p>
 * <p>
 * Extends AEBaseGui for proper fluid slot rendering.
 * Implements IJEIGhostIngredients for JEI drag and drop support.
 */
public class GuiFluidImportInterface extends AEBaseGui implements IJEIGhostIngredients {

    private final ContainerFluidImportInterface container;
    private final TileFluidImportInterface tile;
    private GuiTabButton configButton;
    private GuiTabButton pollingRateButton;
    private final Map<IGhostIngredientHandler.Target<?>, Object> mapTargetSlot = new HashMap<>();

    public GuiFluidImportInterface(final InventoryPlayer inventoryPlayer, final TileFluidImportInterface tile) {
        super(new ContainerFluidImportInterface(inventoryPlayer, tile));
        this.container = (ContainerFluidImportInterface) this.inventorySlots;
        this.tile = tile;
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

                GuiFluidFilterSlot filterSlot = new GuiFluidFilterSlot(this.tile, slotIndex, xPos, filterY);
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

                GuiFluidImportTankSlot tankSlot = new GuiFluidImportTankSlot(this.tile, this.container, tankIndex, tankIndex, xPos, yPos);
                tankSlot.setFontRenderer(this.fontRenderer);
                this.guiSlots.add(tankSlot);
            }
        }

        this.configButton = new GuiTabButton(
            this.guiLeft + 154,
            this.guiTop,
            2 + 4 * 16,
            I18n.format("gui.cells.import_interface.max_slot_size"),
            this.itemRender
        );
        this.buttonList.add(this.configButton);

        this.pollingRateButton = new GuiTabButton(
            this.guiLeft + 154 - 22,
            this.guiTop,
            2 + 5 * 16,
            I18n.format("gui.cells.polling_rate.title"),
            this.itemRender
        );
        this.buttonList.add(this.pollingRateButton);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRenderer.drawString(I18n.format("gui.cells.import_fluid_interface.title"), 8, 6, 0x404040);
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.bindCellsTexture("guis/import_interface.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        if (btn == this.configButton) {
            CellsNetworkHandler.INSTANCE.sendToServer(new PacketOpenGui(
                this.tile.getPos().getX(),
                this.tile.getPos().getY(),
                this.tile.getPos().getZ(),
                CellsGuiHandler.GUI_MAX_SLOT_SIZE
            ));
            return;
        }

        if (btn == this.pollingRateButton) {
            CellsNetworkHandler.INSTANCE.sendToServer(new PacketOpenGui(
                this.tile.getPos().getX(),
                this.tile.getPos().getY(),
                this.tile.getPos().getZ(),
                CellsGuiHandler.GUI_POLLING_RATE
            ));
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
    public List<IGhostIngredientHandler.Target<?>> getPhantomTargets(Object ingredient) {
        // Accept FluidStack directly (from JEI fluid tab)
        FluidStack fluidStack = null;
        if (ingredient instanceof FluidStack) {
            fluidStack = (FluidStack) ingredient;
        } else if (ingredient instanceof ItemStack) {
            fluidStack = FluidUtil.getFluidContained((ItemStack) ingredient);
        }

        if (fluidStack == null) return new ArrayList<>();

        final FluidStack finalFluid = fluidStack;
        List<IGhostIngredientHandler.Target<?>> targets = new ArrayList<>();

        for (GuiCustomSlot slot : this.guiSlots) {
            if (!(slot instanceof GuiFluidFilterSlot)) continue;

            final GuiFluidFilterSlot filterSlot = (GuiFluidFilterSlot) slot;

            IGhostIngredientHandler.Target<Object> target = new IGhostIngredientHandler.Target<Object>() {
                @Override
                public Rectangle getArea() {
                    return new Rectangle(getGuiLeft() + filterSlot.xPos(), getGuiTop() + filterSlot.yPos(), 16, 16);
                }

                @Override
                public void accept(Object ingredient) {
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

    @Override
    public Map<IGhostIngredientHandler.Target<?>, Object> getFakeSlotTargetMap() {
        return mapTargetSlot;
    }
}
