package com.cells.blocks.interfacebase;

import java.io.IOException;

import javax.annotation.Nonnull;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.util.ReadableNumberConverter;

import com.cells.Tags;
import com.cells.config.CellsConfig;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.packets.PacketOpenGui;
import com.cells.network.packets.PacketSetMaxSlotSize;


/**
 * GUI for configuring the max slot size of an Interface.
 * Similar to AE2's Priority GUI with +/- buttons and a number field.
 * Works with any host implementing {@link IInterfaceHost} (both TileEntity and IPart).
 * <p>
 * Supports two modes:
 * <ul>
 *   <li><b>Global mode</b>: Title "Max Slot Size", edits the global maxSlotSize.</li>
 *   <li><b>Per-slot mode</b>: Title "Max Slot Size (slot X)", edits a per-slot override.
 *       Clearing the field resets the override (reverts to global).</li>
 * </ul>
 */
public class GuiMaxSlotSize extends AEBaseGui {

    private static final ResourceLocation TEXTURE = new ResourceLocation(Tags.MODID, "textures/guis/slot_size.png");

    private GuiTextField sizeField;
    private GuiTabButton originalGuiBtn;
    private long currentMaxSlotSize;

    private final GuiButton[] sizeButtons = new GuiButton[8];
    private long[] buttonValues;
    private boolean useFixedButtonValues;

    private final IInterfaceHost host;

    // Tracks whether keyTyped pre-skipped a comma so onQtyChanged can decide
    // whether to nudge the cursor past the comma after reformatting.
    private boolean commaSkipped;

    /** Keyboard repeat is normally disabled outside text-entry screens. */
    private boolean previousKeyboardRepeatEnabled;
    private boolean keyboardRepeatStateCaptured;

    public GuiMaxSlotSize(final InventoryPlayer inventoryPlayer, final IInterfaceHost host) {
        super(new ContainerMaxSlotSize(inventoryPlayer, host));
        this.host = host;
        this.xSize = 200; // Widened to accommodate long values
        this.ySize = 107;
    }

    /**
     * @return The container, typed for convenience.
     */
    private ContainerMaxSlotSize getContainer() {
        return (ContainerMaxSlotSize) this.inventorySlots;
    }

    /**
     * @return true if we're editing a per-slot override rather than the global max.
     */
    private boolean isOverrideMode() {
        return getContainer().isOverrideMode();
    }

    @Override
    public void initGui() {
        super.initGui();

        if (!this.keyboardRepeatStateCaptured) {
            this.previousKeyboardRepeatEnabled = Keyboard.areRepeatEventsEnabled();
            this.keyboardRepeatStateCaptured = true;
        }
        Keyboard.enableRepeatEvents(true);

        this.useFixedButtonValues = CellsConfig.interfaces.interfaceMaxSlotSizeUseFixedValues;
        this.buttonValues = this.useFixedButtonValues
            ? CellsConfig.getInterfaceMaxSlotSizeFixedValues()
            : CellsConfig.getInterfaceMaxSlotSizeOffsets();

        // Use a fixed-width layout to stay compact even with formatted
        // Long.MAX_VALUE-sized configured values within the 28px buttons.
        for (int i = 0; i < this.sizeButtons.length; i++) {
            int column = i % 4;
            int row = i / 4;
            long value = this.buttonValues[this.useFixedButtonValues ? i : column];
            String label = this.formatButtonLabel(value, !this.useFixedButtonValues, row == 0);

            this.buttonList.add(this.sizeButtons[i] = new GuiButton(
                i,
                this.guiLeft + 15 + column * 36,
                this.guiTop + (row == 0 ? 32 : 69),
                30,
                20,
                label
            ));
        }

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
        getContainer().setTextField(this.sizeField);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();

        if (this.keyboardRepeatStateCaptured) {
            Keyboard.enableRepeatEvents(this.previousKeyboardRepeatEnabled);
            this.keyboardRepeatStateCaptured = false;
        }
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        // Title: in per-slot mode show "Max Slot Size (slot X)", otherwise "Max Slot Size"
        if (isOverrideMode()) {
            // 1-indexed slot number for user display
            String title = I18n.format("cells.slot_size_override.title", getContainer().getOverrideSlot() + 1);
            this.fontRenderer.drawString(title, 8, 6, 0x404040);
        } else {
            this.fontRenderer.drawString(I18n.format("cells.max_slot_size.title"), 8, 6, 0x404040);
        }

        // Read from the container's synced field (kept up-to-date by @GuiSync)
        long syncedSize = getContainer().maxSlotSize;
        if (syncedSize >= 0) this.currentMaxSlotSize = syncedSize;

        // After the text field: 17 + 135 + 3px padding => x=155
        if (isOverrideMode() && getContainer().maxSlotSize < 0) {
            // Override not set - show "global" hint in gray
            String globalText = I18n.format("cells.slot_size_override.using_global");
            this.fontRenderer.drawString(globalText, 155, 57, 0x808080);
        } else {
            String shortNumber = ReadableNumberConverter.INSTANCE.toWideReadableForm(this.currentMaxSlotSize);
            String displayText = "= " + shortNumber;
            this.fontRenderer.drawString(displayText, 155, 57, 0x404040);
        }

        // In per-slot mode, show hint text at the bottom of the GUI
        if (isOverrideMode()) {
            String hint = I18n.format("cells.slot_size_override.clear_hint");
            this.fontRenderer.drawString(hint, 8, this.ySize - 14, 0x808080);
        }
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.mc.getTextureManager().bindTexture(TEXTURE);
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);

        this.sizeField.drawTextBox();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        // Right-click on the text field clears it (same UX as filter slot right-click)
        boolean insideField = mouseX >= this.sizeField.x && mouseX < this.sizeField.x + this.sizeField.width
                           && mouseY >= this.sizeField.y && mouseY < this.sizeField.y + this.sizeField.height;

        if (mouseButton == 1 && insideField) {
            this.sizeField.setText("");

            if (isOverrideMode()) {
                // Clear the per-slot override (revert to global)
                this.currentMaxSlotSize = 0;
                CellsNetworkHandler.INSTANCE.sendToServer(new PacketSetMaxSlotSize(-1));
            } else {
                // In global mode, clear just resets the display (unsaved)
                this.currentMaxSlotSize = 0;
            }

            return;
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
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

        for (int i = 0; i < this.sizeButtons.length; i++) {
            if (btn != this.sizeButtons[i]) continue;

            if (this.useFixedButtonValues) {
                this.onQtyChanged(this.buttonValues[i]);
            } else {
                long offset = this.buttonValues[i % 4];
                this.addQty(i < 4 ? offset : -offset);
            }

            return;
        }
    }

    private String formatButtonLabel(long value, boolean isOffset, boolean isPositive) {
        if (!isOffset) return ReadableNumberConverter.INSTANCE.toWideReadableForm(value);

        return (isPositive ? "+" : "-") + ReadableNumberConverter.INSTANCE.toSlimReadableForm(value);
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
        }

        // Always send the packet, even if the text matches (because values under 1000 don't get commas)
        CellsNetworkHandler.INSTANCE.sendToServer(new PacketSetMaxSlotSize(value));
    }

    private void addQty(final long delta) {
        long base;

        // In per-slot mode with no current override, start from 0 so that
        // buttons act as expected.
        if (isOverrideMode() && getContainer().maxSlotSize < 0) {
            base = 0;
        } else {
            base = this.currentMaxSlotSize;
        }

        long newValue;
        if (delta > 0) {
            // Clamp so base + delta doesn't overflow Long.MAX_VALUE
            long headroom = Long.MAX_VALUE - base;
            newValue = base + Math.min(delta, headroom);
        } else {
            // Negative delta: just add directly, validateMaxSlotSize handles clamping to minimum
            newValue = base + delta;
        }

        this.onQtyChanged(this.host.validateMaxSlotSize(newValue));
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (!this.sizeField.isFocused()) {
            super.keyTyped(character, key);
            return;
        }

        if (GuiScreen.isKeyComboCtrlA(key) || GuiScreen.isKeyComboCtrlC(key)) {
            this.sizeField.textboxKeyTyped(character, key);
            return;
        }

        if (GuiScreen.isKeyComboCtrlV(key)) {
            this.pasteClipboardValue();
            return;
        }

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

            this.updateValueFromField();

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

    /**
     * Applies a number-only clipboard paste through the text field so selection
     * replacement and the configured maximum length retain their normal behavior.
     */
    private void pasteClipboardValue() {
        String clipboardValue = GuiScreen.getClipboardString().replace(",", "").trim();
        if (clipboardValue.isEmpty() || !clipboardValue.chars().allMatch(Character::isDigit)) return;

        this.sizeField.writeText(clipboardValue);
        this.updateValueFromField();
    }

    /**
     * Parses the editable text, sending valid values to the server. An empty
     * field deliberately remains unformatted, including after Ctrl+A then Backspace.
     */
    private void updateValueFromField() {
        String out = this.sizeField.getText().replace(",", "");

        // Remove leading zeros
        while (out.startsWith("0") && out.length() > 1) {
            out = out.substring(1);
        }

        if (out.isEmpty()) {
            if (isOverrideMode()) {
                // In per-slot mode, empty field clears the override
                this.currentMaxSlotSize = 0;
                CellsNetworkHandler.INSTANCE.sendToServer(new PacketSetMaxSlotSize(-1));
            }
            // In global mode, empty field is just unsaved (no packet sent)
            return;
        }

        try {
            // Parse as long to handle large values
            this.onQtyChanged(Long.parseLong(out));
        } catch (final NumberFormatException e) {
            // Parsing failed should mean we exceeded Long.MAX_VALUE, so clamp to max
            this.onQtyChanged(Long.MAX_VALUE);
        }
    }
}
