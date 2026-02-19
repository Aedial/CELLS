package com.cells.blocks.importinterface;

import java.io.IOException;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiTabButton;

import com.cells.BlockRegistry;
import com.cells.gui.CellsGuiHandler;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.packets.PacketOpenGui;
import com.cells.network.packets.PacketSetMaxSlotSize;


/**
 * GUI for configuring the max slot size of the Import Interface.
 * Similar to AE2's Priority GUI with +/- buttons and a number field.
 */
public class GuiMaxSlotSize extends AEBaseGui {

    private GuiTextField sizeField;
    private GuiTabButton originalGuiBtn;

    private GuiButton plus1;
    private GuiButton plus10;
    private GuiButton plus100;
    private GuiButton plus1000;
    private GuiButton minus1;
    private GuiButton minus10;
    private GuiButton minus100;
    private GuiButton minus1000;

    private final TileImportInterface tile;

    public GuiMaxSlotSize(final InventoryPlayer inventoryPlayer, final TileImportInterface tile) {
        super(new ContainerMaxSlotSize(inventoryPlayer, tile));
        this.tile = tile;
    }

    @Override
    public void initGui() {
        super.initGui();

        // Button increments - using same values as AE2 priority
        final int a = 1;
        final int b = 10;
        final int c = 100;
        final int d = 1000;

        this.buttonList.add(this.plus1 = new GuiButton(0, this.guiLeft + 20, this.guiTop + 32, 22, 20, "+" + a));
        this.buttonList.add(this.plus10 = new GuiButton(0, this.guiLeft + 48, this.guiTop + 32, 28, 20, "+" + b));
        this.buttonList.add(this.plus100 = new GuiButton(0, this.guiLeft + 82, this.guiTop + 32, 32, 20, "+" + c));
        this.buttonList.add(this.plus1000 = new GuiButton(0, this.guiLeft + 120, this.guiTop + 32, 38, 20, "+" + d));

        this.buttonList.add(this.minus1 = new GuiButton(0, this.guiLeft + 20, this.guiTop + 69, 22, 20, "-" + a));
        this.buttonList.add(this.minus10 = new GuiButton(0, this.guiLeft + 48, this.guiTop + 69, 28, 20, "-" + b));
        this.buttonList.add(this.minus100 = new GuiButton(0, this.guiLeft + 82, this.guiTop + 69, 32, 20, "-" + c));
        this.buttonList.add(this.minus1000 = new GuiButton(0, this.guiLeft + 120, this.guiTop + 69, 38, 20, "-" + d));

        // Back button to return to Import Interface GUI
        this.buttonList.add(this.originalGuiBtn = new GuiTabButton(
            this.guiLeft + 154,
            this.guiTop,
            new ItemStack(BlockRegistry.IMPORT_INTERFACE),
            "Import Interface",
            this.itemRender
        ));

        // Number input field
        this.sizeField = new GuiTextField(0, this.fontRenderer, this.guiLeft + 62, this.guiTop + 57, 59, this.fontRenderer.FONT_HEIGHT);
        this.sizeField.setEnableBackgroundDrawing(false);
        this.sizeField.setMaxStringLength(16);
        this.sizeField.setTextColor(0xFFFFFF);
        this.sizeField.setVisible(true);
        this.sizeField.setFocused(true);
        ((ContainerMaxSlotSize) this.inventorySlots).setTextField(this.sizeField);
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString("Max Slot Size", 8, 6, 0x404040);
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        // Use AE2's priority texture as base (same layout)
        this.bindTexture("guis/priority.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);

        this.sizeField.drawTextBox();
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        if (btn == this.originalGuiBtn) {
            // Return to Import Interface GUI
            CellsNetworkHandler.INSTANCE.sendToServer(new PacketOpenGui(
                this.tile.getPos().getX(),
                this.tile.getPos().getY(),
                this.tile.getPos().getZ(),
                CellsGuiHandler.GUI_IMPORT_INTERFACE
            ));
            return;
        }

        final boolean isPlus = btn == this.plus1 || btn == this.plus10 || btn == this.plus100 || btn == this.plus1000;
        final boolean isMinus = btn == this.minus1 || btn == this.minus10 || btn == this.minus100 || btn == this.minus1000;

        if (isPlus || isMinus) this.addQty(this.getButtonQty(btn));
    }

    private int getButtonQty(final GuiButton btn) {
        if (btn == this.plus1) return 1;
        if (btn == this.plus10) return 10;
        if (btn == this.plus100) return 100;
        if (btn == this.plus1000) return 1000;

        if (btn == this.minus1) return -1;
        if (btn == this.minus10) return -10;
        if (btn == this.minus100) return -100;
        if (btn == this.minus1000) return -1000;

        return 0;
    }

    private void addQty(final int delta) {
        try {
            String out = this.sizeField.getText();

            // Remove leading zeros
            while (out.startsWith("0") && out.length() > 1) out = out.substring(1);

            if (out.isEmpty()) out = "1";

            // Parse as long to handle values > Integer.MAX_VALUE, then clamp
            long parsed = Long.parseLong(out);
            parsed += delta;
            int result = (int) Math.max(TileImportInterface.MIN_MAX_SLOT_SIZE, Math.min(Integer.MAX_VALUE, parsed));

            this.sizeField.setText(out = Integer.toString(result));
            CellsNetworkHandler.INSTANCE.sendToServer(new PacketSetMaxSlotSize(result));
        } catch (final NumberFormatException e) {
            this.sizeField.setText("1");
        }
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (!this.checkHotbarKeys(key)) {
            if ((key == Keyboard.KEY_DELETE || key == Keyboard.KEY_RIGHT
                || key == Keyboard.KEY_LEFT || key == Keyboard.KEY_BACK
                || Character.isDigit(character)) && this.sizeField.textboxKeyTyped(character, key)) {

                String out = this.sizeField.getText();

                // Remove leading zeros
                boolean fixed = false;
                while (out.startsWith("0") && out.length() > 1) {
                    out = out.substring(1);
                    fixed = true;
                }

                if (fixed) this.sizeField.setText(out);

                if (out.isEmpty()) out = "1";

                try {
                    // Parse as long to handle values > Integer.MAX_VALUE, then clamp
                    long parsed = Long.parseLong(out);
                    int value = (int) Math.max(TileImportInterface.MIN_MAX_SLOT_SIZE, Math.min(Integer.MAX_VALUE, parsed));
                    CellsNetworkHandler.INSTANCE.sendToServer(new PacketSetMaxSlotSize(value));
                } catch (final NumberFormatException e) {
                    // Ignore invalid input
                }
            } else {
                super.keyTyped(character, key);
            }
        }
    }
}
