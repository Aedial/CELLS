package com.cells.blocks.interfacebase;

import java.io.IOException;

import javax.annotation.Nonnull;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.util.ReadableNumberConverter;

import com.cells.Tags;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.packets.PacketOpenGui;
import com.cells.network.packets.PacketSetMaxSlotSize;


/**
 * GUI for configuring the max slot size of an Interface.
 * Similar to AE2's Priority GUI with +/- buttons and a number field.
 * Works with any host implementing {@link IInterfaceHost} (both TileEntity and IPart).
 */
public class GuiMaxSlotSize extends AEBaseGui {

    private static final ResourceLocation TEXTURE = new ResourceLocation(Tags.MODID, "textures/guis/slot_size.png");

    private GuiTextField sizeField;
    private GuiTabButton originalGuiBtn;
    private long currentMaxSlotSize;

    private GuiButton plus1;
    private GuiButton plus10;
    private GuiButton plus100;
    private GuiButton plus1000;
    private GuiButton minus1;
    private GuiButton minus10;
    private GuiButton minus100;
    private GuiButton minus1000;

    private final IInterfaceHost host;

    // Tracks whether keyTyped pre-skipped a comma so onQtyChanged can decide
    // whether to nudge the cursor past the comma after reformatting.
    private boolean commaSkipped;

    public GuiMaxSlotSize(final InventoryPlayer inventoryPlayer, final IInterfaceHost host) {
        super(new ContainerMaxSlotSize(inventoryPlayer, host));
        this.host = host;
        this.xSize = 200; // Widened to accommodate long values
    }

    @Override
    public void initGui() {
        super.initGui();

        // Button increments - using same values as AE2 priority
        final int a = 1;
        final int b = 10;
        final int c = 100;
        final int d = 1000;

        // Buttons spread out for 200px wide GUI
        // Gap between buttons: 14px
        this.buttonList.add(this.plus1 = new GuiButton(0, this.guiLeft + 20, this.guiTop + 32, 22, 20, "+" + a));
        this.buttonList.add(this.plus10 = new GuiButton(0, this.guiLeft + 56, this.guiTop + 32, 28, 20, "+" + b));
        this.buttonList.add(this.plus100 = new GuiButton(0, this.guiLeft + 98, this.guiTop + 32, 32, 20, "+" + c));
        this.buttonList.add(this.plus1000 = new GuiButton(0, this.guiLeft + 144, this.guiTop + 32, 38, 20, "+" + d));

        this.buttonList.add(this.minus1 = new GuiButton(0, this.guiLeft + 20, this.guiTop + 69, 22, 20, "-" + a));
        this.buttonList.add(this.minus10 = new GuiButton(0, this.guiLeft + 56, this.guiTop + 69, 28, 20, "-" + b));
        this.buttonList.add(this.minus100 = new GuiButton(0, this.guiLeft + 98, this.guiTop + 69, 32, 20, "-" + c));
        this.buttonList.add(this.minus1000 = new GuiButton(0, this.guiLeft + 144, this.guiTop + 69, 38, 20, "-" + d));

        // Back button to return to the main Interface GUI (22px from right edge)
        this.buttonList.add(this.originalGuiBtn = new GuiTabButton(
            this.guiLeft + this.xSize - 22,
            this.guiTop,
            this.host.getBackButtonStack(),
            I18n.format(this.host.getGuiTitleLangKey()),
            this.itemRender
        ));

        // Number input field (max 9,223,372,036,854,775,807 = 25 digits x 6px each = 150px)
        // We remove 15px that are probably coming from the commas
        this.sizeField = new GuiTextField(0, this.fontRenderer, this.guiLeft + 17, this.guiTop + 57, 135, this.fontRenderer.FONT_HEIGHT);
        this.sizeField.setEnableBackgroundDrawing(false);
        this.sizeField.setMaxStringLength(26);
        this.sizeField.setTextColor(0xFFFFFF);
        this.sizeField.setVisible(true);
        this.sizeField.setFocused(true);
        ((ContainerMaxSlotSize) this.inventorySlots).setTextField(this.sizeField);
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(I18n.format("cells.max_slot_size.title"), 8, 6, 0x404040);

        // Read from the container's synced field (kept up-to-date by @GuiSync)
        long syncedSize = ((ContainerMaxSlotSize) this.inventorySlots).maxSlotSize;
        if (syncedSize >= 0) this.currentMaxSlotSize = syncedSize;

        // After the text field: 17 + 135 + 3px padding => x=155
        String shortNumber = ReadableNumberConverter.INSTANCE.toWideReadableForm(this.currentMaxSlotSize);
        String displayText = "= " + shortNumber;
        this.fontRenderer.drawString(displayText, 155, 57, 0x404040);
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.mc.getTextureManager().bindTexture(TEXTURE);
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);

        this.sizeField.drawTextBox();
    }

    @Override
    protected void actionPerformed(@Nonnull final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        if (btn == this.originalGuiBtn) {
            // Return to the main Interface GUI
            BlockPos pos = this.host.getHostPos();
            CellsNetworkHandler.INSTANCE.sendToServer(new PacketOpenGui(
                pos,
                this.host.getMainGuiId(),
                this.host.getPartSide()
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

    private void onQtyChanged(long newValue) {
        long value = this.host.validateMaxSlotSize(newValue);
        this.currentMaxSlotSize = value;

        // Text with , as thousand separator for better readability
        String displayValue = String.format("%,d", value);
        String currentText = this.sizeField.getText();

        if (!currentText.equals(displayValue)) {
            // Preserve cursor position relative to the end (since commas shift positions)
            int cursorFromEnd = currentText.length() - this.sizeField.getCursorPosition();
            this.sizeField.setText(displayValue);
            // Restore cursor position from end, accounting for new commas
            int newCursor = Math.max(0, displayValue.length() - cursorFromEnd);
            this.sizeField.setCursorPosition(newCursor);

            CellsNetworkHandler.INSTANCE.sendToServer(new PacketSetMaxSlotSize(value));
        }
    }

    private void addQty(final int delta) {
        long newValue;

        if (delta > 0) {
            // Clamp so currentMaxSlotSize + delta doesn't overflow Long.MAX_VALUE
            long headroom = Long.MAX_VALUE - this.currentMaxSlotSize;
            newValue = this.currentMaxSlotSize + Math.min(delta, headroom);
        } else {
            // Negative delta: just add directly, validateMaxSlotSize handles clamping to minimum
            newValue = this.currentMaxSlotSize + delta;
        }

        this.onQtyChanged(this.host.validateMaxSlotSize(newValue));
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (!this.checkHotbarKeys(key)) {
            // Commas are virtual (auto-formatted), so we need to skip over them when
            // pressing backspace or delete, otherwise the comma just gets re-added and
            // the user's keypress appears to do nothing.
            this.commaSkipped = false;

            if (key == Keyboard.KEY_BACK) {
                String text = this.sizeField.getText();
                int cursor = this.sizeField.getCursorPosition();

                if (cursor > 0 && text.charAt(cursor - 1) == ',') {
                    this.sizeField.setCursorPosition(cursor - 1);
                    this.commaSkipped = true;
                }
            } else if (key == Keyboard.KEY_DELETE) {
                String text = this.sizeField.getText();
                int cursor = this.sizeField.getCursorPosition();

                if (cursor < text.length() && text.charAt(cursor) == ',') {
                    this.sizeField.setCursorPosition(cursor + 1);
                    this.commaSkipped = true;
                }
            }

            if ((key == Keyboard.KEY_DELETE || key == Keyboard.KEY_RIGHT
                || key == Keyboard.KEY_LEFT || key == Keyboard.KEY_BACK
                || Character.isDigit(character)) && this.sizeField.textboxKeyTyped(character, key)) {

                String out = this.sizeField.getText();

                // Remove commas from thousand separators
                out = out.replaceAll(",", "");

                // Remove leading zeros
                while (out.startsWith("0") && out.length() > 1) {
                    out = out.substring(1);
                }

                // Skip to allow empty field (unsaved)
                if (!out.isEmpty()) {
                    try {
                        // Parse as long to handle large values
                        this.onQtyChanged(Long.parseLong(out));
                    } catch (final NumberFormatException e) {
                        // Parsing failed should mean we exceeded Long.MAX_VALUE, so clamp to max
                        this.onQtyChanged(Long.MAX_VALUE);
                    }
                }

                // After all processing (including potential reformat), if we
                // pre-skipped a comma for backspace/delete, nudge the cursor past
                // the comma so it lands on the correct side. This runs even when
                // onQtyChanged didn't reformat (text unchanged).
                if (this.commaSkipped) {
                    String finalText = this.sizeField.getText();
                    int cur = this.sizeField.getCursorPosition();

                    if (cur < finalText.length() && finalText.charAt(cur) == ',') {
                        this.sizeField.setCursorPosition(cur + 1);
                    }

                    this.commaSkipped = false;
                }
            } else {
                super.keyTyped(character, key);
            }
        }
    }
}
