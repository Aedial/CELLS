package com.cells.items.pullpush;

import java.io.IOException;
import java.util.Collections;

import javax.annotation.Nonnull;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;

import appeng.client.gui.AEBaseGui;

import com.cells.Tags;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.packets.PacketSetPullPushKeepQuantity;
import com.cells.network.packets.PacketSetPullPushQuantity;
import com.cells.network.packets.PacketSetPullPushRate;
import com.cells.util.PollingRateUtils;


/**
 * GUI for configuring the interval and quantity of a Pull/Push Card.
 * Has +/- buttons for 1s, 1m, 1h, 1d. Holding shift multiplies by 10.
 * Time is displayed in format "1d 2h 3m 4s" (skipping zero parts) or "0".
 *
 * Also has text fields for quantity and keep quantity (comma-formatted,
 * capped at Integer.MAX_VALUE) with "/ max" display after each field.
 * Hovering over a label shows a tooltip explaining the field.
 */
public class GuiPullPushCard extends AEBaseGui implements ContainerPullPushCard.IValuesListener {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
        Tags.MODID, "textures/guis/push_pull_card.png");

    /** Formatted max int display shown after each text field */
    private static final String MAX_DISPLAY = "/ " + String.format("%,d", Integer.MAX_VALUE);

    private GuiButton plusTick;
    private GuiButton plusSec;
    private GuiButton plusMin;
    private GuiButton plusHour;
    private GuiButton plusDay;
    private GuiButton minusTick;
    private GuiButton minusSec;
    private GuiButton minusMin;
    private GuiButton minusHour;
    private GuiButton minusDay;

    private GuiTextField quantityField;
    private GuiTextField keepsField;

    final private boolean isExport;

    private int currentInterval = ContainerPullPushCard.DEFAULT_INTERVAL;
    private int currentQuantity = ContainerPullPushCard.MINIMUM_QUANTITY;
    private int currentKeepsQuantity = ContainerPullPushCard.MINIMUM_KEEP_QUANTITY;

    // Tracks whether keyTyped pre-skipped a comma so onFieldValueChanged can
    // decide whether to nudge the cursor past the comma after reformatting.
    private boolean commaSkipped;

    public GuiPullPushCard(final InventoryPlayer inventoryPlayer, final EnumHand hand) {
        super(new ContainerPullPushCard(inventoryPlayer, hand));
        this.xSize = 176;
        this.ySize = 156;
        this.isExport = ((ContainerPullPushCard) this.inventorySlots).isPullCard();
    }

    @Override
    public void initGui() {
        super.initGui();

        // Register as listener for interval changes
        ((ContainerPullPushCard) this.inventorySlots).setListener(this);

        // Plus buttons (top row) - positioned similar to priority GUI
        this.buttonList.add(this.plusTick = new GuiButton(0, this.guiLeft + 10, this.guiTop + 32, 28, 20, "+1t"));
        this.buttonList.add(this.plusSec = new GuiButton(1, this.guiLeft + 40, this.guiTop + 32, 28, 20, "+1s"));
        this.buttonList.add(this.plusMin = new GuiButton(2, this.guiLeft + 70, this.guiTop + 32, 28, 20, "+1m"));
        this.buttonList.add(this.plusHour = new GuiButton(3, this.guiLeft + 100, this.guiTop + 32, 28, 20, "+1h"));
        this.buttonList.add(this.plusDay = new GuiButton(4, this.guiLeft + 130, this.guiTop + 32, 28, 20, "+1d"));

        // Minus buttons (bottom row)
        this.buttonList.add(this.minusTick = new GuiButton(5, this.guiLeft + 10, this.guiTop + 69, 28, 20, "-1t"));
        this.buttonList.add(this.minusSec = new GuiButton(6, this.guiLeft + 40, this.guiTop + 69, 28, 20, "-1s"));
        this.buttonList.add(this.minusMin = new GuiButton(7, this.guiLeft + 70, this.guiTop + 69, 28, 20, "-1m"));
        this.buttonList.add(this.minusHour = new GuiButton(8, this.guiLeft + 100, this.guiTop + 69, 28, 20, "-1h"));
        this.buttonList.add(this.minusDay = new GuiButton(9, this.guiLeft + 130, this.guiTop + 69, 28, 20, "-1d"));

        // Quantity text field
        this.quantityField = new GuiTextField(
            10, this.fontRenderer,
            this.guiLeft + 9, this.guiTop + 110,
            80, this.fontRenderer.FONT_HEIGHT);
        this.quantityField.setEnableBackgroundDrawing(false);
        this.quantityField.setMaxStringLength(15); // "2,147,483,647" = 13 chars + margin
        this.quantityField.setTextColor(0xFFFFFF);
        this.quantityField.setVisible(true);
        this.quantityField.setFocused(false);
        this.quantityField.setText(String.format("%,d", this.currentQuantity));

        // Keeps quantity text field
        this.keepsField = new GuiTextField(
            11, this.fontRenderer,
            this.guiLeft + 9, this.guiTop + 136,
            80, this.fontRenderer.FONT_HEIGHT);
        this.keepsField.setEnableBackgroundDrawing(false);
        this.keepsField.setMaxStringLength(15);
        this.keepsField.setTextColor(0xFFFFFF);
        this.keepsField.setVisible(true);
        this.keepsField.setFocused(false);
        this.keepsField.setText(String.format("%,d", this.currentKeepsQuantity));
    }

    @Override
    public void onIntervalChanged(int interval) {
        this.currentInterval = interval;
    }

    @Override
    public void onQuantityChanged(int quantity) {
        this.currentQuantity = quantity;

        // Only update the field when it's not focused, to avoid fighting with user input
        if (this.quantityField != null && !this.quantityField.isFocused()) {
            this.quantityField.setText(String.format("%,d", quantity));
        }
    }

    @Override
    public void onKeepsQuantityChanged(int keepsQuantity) {
        this.currentKeepsQuantity = keepsQuantity;

        if (this.keepsField != null && !this.keepsField.isFocused()) {
            this.keepsField.setText(String.format("%,d", keepsQuantity));
        }
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        String type = this.isExport ? "push" : "pull";
        String title = I18n.format("item.cells." + type + "_card.name");
        this.fontRenderer.drawString(title, 8, 6, 0x404040);

        // Interval section
        String intervalLabel = I18n.format("cells.push_pull_card.interval.title");
        this.fontRenderer.drawString(intervalLabel, 8, 22, 0x404040);

        // Draw the current interval centered in the display area (12-157)
        String timeStr = PollingRateUtils.format(this.currentInterval);
        int textWidth = this.fontRenderer.getStringWidth(timeStr);
        int centerX = 12 + (155 - textWidth) / 2;
        this.fontRenderer.drawString(timeStr, centerX, 57, 0xFFFFFF);

        // Quantity section
        String quantityLabel = I18n.format("cells.push_pull_card.quantity.title");
        this.fontRenderer.drawString(quantityLabel, 8, 98, 0x404040);
        // "/ 2,147,483,647" after the text field (field ends at x=89, 3px gap)
        this.fontRenderer.drawString(MAX_DISPLAY, 92, 110, 0x808080);

        // Keeps quantity section
        String keepsLabel = I18n.format("cells.push_pull_card.limit.title");
        this.fontRenderer.drawString(keepsLabel, 8, 124, 0x404040);
        this.fontRenderer.drawString(MAX_DISPLAY, 92, 136, 0x808080);
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.mc.getTextureManager().bindTexture(TEXTURE);
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);

        this.quantityField.drawTextBox();
        this.keepsField.drawTextBox();
    }

    @Override
    protected void actionPerformed(@Nonnull final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        int delta = getButtonDelta(btn);
        if (delta != 0) addInterval(delta);
    }

    private int getButtonDelta(final GuiButton btn) {
        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        int multiplier = shift ? 10 : 1;

        if (btn == this.plusTick) return 1 * multiplier;
        if (btn == this.plusSec) return PollingRateUtils.TICKS_PER_SECOND * multiplier;
        if (btn == this.plusMin) return PollingRateUtils.TICKS_PER_MINUTE * multiplier;
        if (btn == this.plusHour) return PollingRateUtils.TICKS_PER_HOUR * multiplier;
        if (btn == this.plusDay) return PollingRateUtils.TICKS_PER_DAY * multiplier;

        if (btn == this.minusTick) return -1 * multiplier;
        if (btn == this.minusSec) return -PollingRateUtils.TICKS_PER_SECOND * multiplier;
        if (btn == this.minusMin) return -PollingRateUtils.TICKS_PER_MINUTE * multiplier;
        if (btn == this.minusHour) return -PollingRateUtils.TICKS_PER_HOUR * multiplier;
        if (btn == this.minusDay) return -PollingRateUtils.TICKS_PER_DAY * multiplier;

        return 0;
    }

    private void addInterval(final int delta) {
        long result = this.currentInterval + delta;
        result = Math.max(1, Math.min(Integer.MAX_VALUE, result));
        this.currentInterval = (int) result;
        CellsNetworkHandler.INSTANCE.sendToServer(new PacketSetPullPushRate(this.currentInterval));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);

        // Update button labels based on shift state
        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);

        this.plusTick.displayString = shift ? "+10t" : "+1t";
        this.plusSec.displayString = shift ? "+10s" : "+1s";
        this.plusMin.displayString = shift ? "+10m" : "+1m";
        this.plusHour.displayString = shift ? "+10h" : "+1h";
        this.plusDay.displayString = shift ? "+10d" : "+1d";

        this.minusTick.displayString = shift ? "-10t" : "-1t";
        this.minusSec.displayString = shift ? "-10s" : "-1s";
        this.minusMin.displayString = shift ? "-10m" : "-1m";
        this.minusHour.displayString = shift ? "-10h" : "-1h";
        this.minusDay.displayString = shift ? "-10d" : "-1d";

        // --- Label tooltips on hover ---
        int labelX = this.guiLeft + 8;
        int fontH = this.fontRenderer.FONT_HEIGHT;

        // Interval label
        String intervalLabel = I18n.format("cells.push_pull_card.interval.title");
        int intervalLabelY = this.guiTop + 22;
        if (mouseX >= labelX && mouseX <= labelX + this.fontRenderer.getStringWidth(intervalLabel)
            && mouseY >= intervalLabelY && mouseY <= intervalLabelY + fontH) {
            this.drawHoveringText(
                Collections.singletonList(I18n.format("cells.push_pull_card.interval.tooltip")),
                mouseX, mouseY);
        }

        // Quantity label
        String quantityLabel = I18n.format("cells.push_pull_card.quantity.title");
        int quantityLabelY = this.guiTop + 98;
        if (mouseX >= labelX && mouseX <= labelX + this.fontRenderer.getStringWidth(quantityLabel)
            && mouseY >= quantityLabelY && mouseY <= quantityLabelY + fontH) {
            this.drawHoveringText(
                Collections.singletonList(I18n.format("cells.push_pull_card.quantity.tooltip")),
                mouseX, mouseY);
        }

        // Keeps label
        String keepsLabel = I18n.format("cells.push_pull_card.limit.title");
        int keepsLabelY = this.guiTop + 124;
        if (mouseX >= labelX && mouseX <= labelX + this.fontRenderer.getStringWidth(keepsLabel)
            && mouseY >= keepsLabelY && mouseY <= keepsLabelY + fontH) {
            this.drawHoveringText(
                Collections.singletonList(I18n.format("cells.push_pull_card.limit.tooltip")),
                mouseX, mouseY);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        this.quantityField.mouseClicked(mouseX, mouseY, mouseButton);
        this.keepsField.mouseClicked(mouseX, mouseY, mouseButton);

        // Only one text field may be focused at a time
        if (this.quantityField.isFocused()) {
            this.keepsField.setFocused(false);
        } else if (this.keepsField.isFocused()) {
            this.quantityField.setFocused(false);
        }
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (this.checkHotbarKeys(key)) return;

        // Determine which text field is focused (if any)
        GuiTextField activeField = null;
        if (this.quantityField.isFocused()) {
            activeField = this.quantityField;
        } else if (this.keepsField.isFocused()) {
            activeField = this.keepsField;
        }

        // No field focused: delegate to parent (handles ESC, etc.)
        if (activeField == null) {
            super.keyTyped(character, key);
            return;
        }

        // Tab switches focus between the two fields
        if (key == Keyboard.KEY_TAB) {
            if (this.quantityField.isFocused()) {
                this.quantityField.setFocused(false);
                this.keepsField.setFocused(true);
            } else {
                this.keepsField.setFocused(false);
                this.quantityField.setFocused(true);
            }

            return;
        }

        // Commas are virtual (auto-formatted), so we need to skip over them when
        // pressing backspace or delete, otherwise the comma just gets re-added and
        // the user's keypress appears to do nothing.
        this.commaSkipped = false;

        if (key == Keyboard.KEY_BACK) {
            String text = activeField.getText();
            int cursor = activeField.getCursorPosition();

            if (cursor > 0 && text.charAt(cursor - 1) == ',') {
                activeField.setCursorPosition(cursor - 1);
                this.commaSkipped = true;
            }
        } else if (key == Keyboard.KEY_DELETE) {
            String text = activeField.getText();
            int cursor = activeField.getCursorPosition();

            if (cursor < text.length() && text.charAt(cursor) == ',') {
                activeField.setCursorPosition(cursor + 1);
                this.commaSkipped = true;
            }
        }

        boolean isValidKey = key == Keyboard.KEY_DELETE || key == Keyboard.KEY_RIGHT
            || key == Keyboard.KEY_LEFT || key == Keyboard.KEY_BACK
            || Character.isDigit(character);

        if (isValidKey && activeField.textboxKeyTyped(character, key)) {
            String out = activeField.getText().replaceAll(",", "");

            // Remove leading zeros
            while (out.startsWith("0") && out.length() > 1) {
                out = out.substring(1);
            }

            // Skip when empty (unsaved), let user type freely
            if (!out.isEmpty()) {
                try {
                    long parsed = Long.parseLong(out);
                    this.onFieldValueChanged(activeField, parsed);
                } catch (final NumberFormatException e) {
                    // Exceeded Long.MAX_VALUE, clamp to Integer.MAX_VALUE
                    this.onFieldValueChanged(activeField, Integer.MAX_VALUE);
                }
            }

            // After all processing (including potential reformat), if we
            // pre-skipped a comma for backspace/delete, nudge the cursor past
            // the comma so it lands on the correct side.
            if (this.commaSkipped) {
                String finalText = activeField.getText();
                int cur = activeField.getCursorPosition();

                if (cur < finalText.length() && finalText.charAt(cur) == ',') {
                    activeField.setCursorPosition(cur + 1);
                }

                this.commaSkipped = false;
            }
        } else {
            super.keyTyped(character, key);
        }
    }

    /**
     * Handles value change for either text field, clamping and reformatting with commas.
     * Sends the appropriate packet to the server.
     */
    private void onFieldValueChanged(GuiTextField field, long newValue) {
        int min = (field == this.quantityField)
            ? ContainerPullPushCard.MINIMUM_QUANTITY
            : ContainerPullPushCard.MINIMUM_KEEP_QUANTITY;

        long clamped = Math.max(min, Math.min(newValue, Integer.MAX_VALUE));
        String displayValue = String.format("%,d", clamped);
        String currentText = field.getText();

        if (!currentText.equals(displayValue)) {
            // Preserve cursor position relative to the end (commas shift positions)
            int cursorFromEnd = currentText.length() - field.getCursorPosition();
            field.setText(displayValue);
            int newCursor = Math.max(0, displayValue.length() - cursorFromEnd);
            field.setCursorPosition(newCursor);
        }

        // Update local state and send packet
        if (field == this.quantityField) {
            this.currentQuantity = (int) clamped;
            CellsNetworkHandler.INSTANCE.sendToServer(new PacketSetPullPushQuantity(this.currentQuantity));
        } else {
            this.currentKeepsQuantity = (int) clamped;
            CellsNetworkHandler.INSTANCE.sendToServer(new PacketSetPullPushKeepQuantity(this.currentKeepsQuantity));
        }
    }
}
