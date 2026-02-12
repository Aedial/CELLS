package com.cells.blocks.importinterface;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.container.slot.SlotFake;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.helpers.InventoryAction;
import appeng.util.item.AEItemStack;

import com.cells.Tags;
import com.cells.gui.CellsGuiHandler;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.packets.PacketOpenGui;

import mezz.jei.api.gui.IGhostIngredientHandler;


/**
 * GUI for the Import Interface.
 * Shows 4 rows of 9 paired slots (filter on top, storage below).
 * Extends AEBaseGui to get proper SlotFake handling for filter slots.
 * Implements IJEIGhostIngredients for JEI drag and drop support.
 */
public class GuiImportInterface extends AEBaseGui implements IJEIGhostIngredients {

    private final ContainerImportInterface container;
    private final TileImportInterface tile;
    private GuiTabButton configButton;
    private final Map<IGhostIngredientHandler.Target<?>, Object> mapTargetSlot = new HashMap<>();

    public GuiImportInterface(final InventoryPlayer inventoryPlayer, final TileImportInterface tile) {
        super(new ContainerImportInterface(inventoryPlayer, tile));
        this.container = (ContainerImportInterface) this.inventorySlots;
        this.tile = tile;
        this.ySize = 256;
        this.xSize = 210;  // 176 for the main area + 34 for the upgrades area
    }

    @Override
    public void initGui() {
        super.initGui();

        // Config button to open max slot size configuration screen
        // Uses icon index for capacity-like appearance (similar to storage cells)
        this.configButton = new GuiTabButton(
            this.guiLeft + 154,
            this.guiTop,
            2 + 4 * 16,
            I18n.format("gui.cells.import_interface.max_slot_size"),
            this.itemRender
        );
        this.buttonList.add(this.configButton);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRenderer.drawString(I18n.format("gui.cells.import_interface.title"), 8, 6, 0x404040);
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
            // Open the max slot size configuration GUI
            CellsNetworkHandler.INSTANCE.sendToServer(new PacketOpenGui(
                this.tile.getPos().getX(),
                this.tile.getPos().getY(),
                this.tile.getPos().getZ(),
                CellsGuiHandler.GUI_MAX_SLOT_SIZE
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
     * Override drawSlot to render filter (fake) slots without item count.
     * This makes them appear as "ghost" items, which is the standard UX for filters.
     */
    @Override
    public void drawSlot(Slot slot) {
        if (slot instanceof SlotFake) {
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty()) {
                // Render the item without the count
                this.zLevel = 100.0F;
                this.itemRender.zLevel = 100.0F;

                RenderHelper.enableGUIStandardItemLighting();
                GlStateManager.enableDepth();
                // TODO: add transparency?
                this.itemRender.renderItemIntoGUI(stack, slot.xPos, slot.yPos);
                GlStateManager.disableDepth();

                this.itemRender.zLevel = 0.0F;
                this.zLevel = 0.0F;
            }

            return;
        }

        super.drawSlot(slot);
    }

    /**
     * JEI ghost ingredient support - returns targets for dragging items from JEI.
     */
    @Override
    public List<IGhostIngredientHandler.Target<?>> getPhantomTargets(Object ingredient) {
        if (!(ingredient instanceof ItemStack)) return Collections.emptyList();

        List<IGhostIngredientHandler.Target<?>> targets = new ArrayList<>();
        ItemStack itemStack = (ItemStack) ingredient;

        for (Slot slot : this.inventorySlots.inventorySlots) {
            if (!(slot instanceof SlotFake)) continue;

            IGhostIngredientHandler.Target<Object> target = new IGhostIngredientHandler.Target<Object>() {
                @Override
                public Rectangle getArea() {
                    return new Rectangle(getGuiLeft() + slot.xPos, getGuiTop() + slot.yPos, 16, 16);
                }

                @Override
                public void accept(Object ingredient) {
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
    public Map<IGhostIngredientHandler.Target<?>, Object> getFakeSlotTargetMap() {
        return mapTargetSlot;
    }
}
