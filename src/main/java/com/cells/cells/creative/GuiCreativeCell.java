package com.cells.cells.creative;

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

import appeng.client.gui.AEBaseGui;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.container.slot.IJEITargetSlot;
import appeng.container.slot.SlotFake;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import appeng.helpers.InventoryAction;

import mezz.jei.api.gui.IGhostIngredientHandler.Target;

import com.cells.Tags;
import com.cells.gui.GuiClearFiltersButton;


/**
 * GUI screen for the Creative ME Cell.
 * <p>
 * Layout (210x241):
 * - Title: "Creative Cell Filters" at top
 * - 9x7 grid of filter slots starting at (8, 19)
 * - Clear filters button (right of hotbar)
 * - Player inventory at y=159
 * <p>
 * Implements IJEIGhostIngredients for JEI drag-drop support.
 */
public class GuiCreativeCell extends AEBaseGui implements IJEIGhostIngredients {

    private static final ResourceLocation TEXTURE = new ResourceLocation(Tags.MODID, "textures/guis/creative_cell.png");

    private final ContainerCreativeCell container;
    private GuiClearFiltersButton clearButton;
    private final Map<Target<?>, Object> mapTargetSlot = new HashMap<>();

    public GuiCreativeCell(InventoryPlayer playerInv, EnumHand hand) {
        super(new ContainerCreativeCell(playerInv, hand));
        this.container = (ContainerCreativeCell) this.inventorySlots;
        this.xSize = 210;
        this.ySize = 241;
    }

    @Override
    public void initGui() {
        super.initGui();

        // Clear filters button - positioned right of the hotbar, similar to Import/Export Interface
        // Player inventory starts at y=159, hotbar is 58px below top of player inventory section
        // Position: right side of GUI, aligned with hotbar
        this.clearButton = new GuiClearFiltersButton(0, this.guiLeft + 176 + 2, this.guiTop + 159 + 58,
            () -> I18n.format("gui.cells.creative_cell.clear") + "\n\n"
                + I18n.format("gui.cells.creative_cell.clear_tooltip"));
        this.buttonList.add(this.clearButton);
    }

    private void clearFilters() {
        container.clearAllFilters();
        // No need to send a packet - filter handler writes directly to cell NBT
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        // Title
        String title = I18n.format("gui.cells.creative_cell.title");
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

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void actionPerformed(@Nonnull final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        if (btn == this.clearButton) clearFilters();
    }

    // =====================
    // IJEIGhostIngredients implementation for JEI drag-drop support
    // =====================

    @Override
    public List<Target<?>> getPhantomTargets(Object ingredient) {
        mapTargetSlot.clear();

        if (!(ingredient instanceof ItemStack)) return Collections.emptyList();

        ItemStack itemStack = (ItemStack) ingredient;
        if (itemStack.isEmpty()) return Collections.emptyList();

        List<Target<?>> targets = new ArrayList<>();

        // Add all filter slots (SlotFake) as valid targets
        for (Slot slot : this.inventorySlots.inventorySlots) {
            if (!(slot instanceof SlotFake)) continue;

            SlotFake fakeSlot = (SlotFake) slot;
            if (!fakeSlot.isSlotEnabled()) continue;

            Target<Object> target = new Target<Object>() {
                @Nonnull
                @Override
                public Rectangle getArea() {
                    return new Rectangle(getGuiLeft() + fakeSlot.xPos, getGuiTop() + fakeSlot.yPos, 16, 16);
                }

                @Override
                public void accept(@Nonnull Object ingredient) {
                    if (!(ingredient instanceof ItemStack)) return;

                    ItemStack stack = (ItemStack) ingredient;
                    if (stack.isEmpty()) return;

                    try {
                        IAEItemStack aeStack = AEItemStack.fromItemStack(stack);
                        PacketInventoryAction packet = new PacketInventoryAction(
                            InventoryAction.PLACE_JEI_GHOST_ITEM,
                            (IJEITargetSlot) fakeSlot,
                            aeStack
                        );
                        NetworkHandler.instance().sendToServer(packet);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
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
