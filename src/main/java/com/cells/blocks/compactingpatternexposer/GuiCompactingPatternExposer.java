package com.cells.blocks.compactingpatternexposer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import net.minecraftforge.fml.common.Optional;

import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiNumberBox;
import appeng.client.gui.widgets.GuiCustomSlot;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.util.ReadableNumberConverter;
import appeng.util.item.AEItemStack;

import mezz.jei.api.gui.IGhostIngredientHandler.Target;

import com.cells.Tags;
import com.cells.client.KeyBindings;
import com.cells.gui.QuickAddHelper;
import com.cells.gui.slots.AbstractResourceFilterSlot;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.sync.PacketCompactingPatternMultiplier;
import com.cells.network.sync.PacketQuickAddFilter;
import com.cells.network.sync.ResourceType;


/**
 * GUI for the Compacting Pattern Exposer.
 */
@Optional.Interface(iface = "appeng.container.interfaces.IJEIGhostIngredients", modid = "jei")
public class GuiCompactingPatternExposer extends AEBaseGui implements IJEIGhostIngredients {

    private static final ResourceLocation TEXTURE =
        new ResourceLocation(Tags.MODID, "textures/guis/compacting_pattern_exposer.png");
    private static final ResourceLocation MULTIPLIER_TEXTURE =
        new ResourceLocation("appliedenergistics2", "textures/guis/priority.png");

    private static final int SLOT_START_X = 8;
    private static final int SLOT_START_Y = 25;
    private static final int SLOT_SPACING = 18;
    private static final int MULTIPLIER_MODAL_WIDTH = 176;
    private static final int MULTIPLIER_MODAL_HEIGHT = 107;

    private final ContainerCompactingPatternExposer container;
    private final Map<Target<?>, Object> mapTargetSlot = new HashMap<>();
    private final List<GuiButton> multiplierButtons = new ArrayList<>();

    private GuiNumberBox multiplierField;
    private boolean multiplierModalOpen;
    private int multiplierModalSlot = -1;

    private GuiButton plus1;
    private GuiButton plus10;
    private GuiButton plus100;
    private GuiButton plus1000;
    private GuiButton minus1;
    private GuiButton minus10;
    private GuiButton minus100;
    private GuiButton minus1000;

    public GuiCompactingPatternExposer(InventoryPlayer playerInv, TileEntity tile) {
        super(new ContainerCompactingPatternExposer(playerInv, (TileCompactingPatternExposer) tile));
        this.container = (ContainerCompactingPatternExposer) this.inventorySlots;
        this.xSize = 176;
        this.ySize = 240;
    }

    @Override
    public void initGui() {
        super.initGui();

        this.guiSlots.clear();

        // Row 1: filter slots 0-8, then their previews
        this.addFilterRow(0, SLOT_START_Y);
        this.addResultRow(this.container::getUpPreview, 0, SLOT_START_Y + SLOT_SPACING);
        this.addResultRow(this.container::getDownPreview, 0, SLOT_START_Y + SLOT_SPACING * 2);

        // Row 2: filter slots 9-17, then their previews
        this.addFilterRow(9, SLOT_START_Y + SLOT_SPACING * 4);
        this.addResultRow(this.container::getUpPreview, 9, SLOT_START_Y + SLOT_SPACING * 5);
        this.addResultRow(this.container::getDownPreview, 9, SLOT_START_Y + SLOT_SPACING * 6);

        this.initMultiplierModalWidgets();
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRenderer.drawString(I18n.format("cells.compacting_pattern_exposer.title"), 8, 6, 0x404040);
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.mc.getTextureManager().bindTexture(TEXTURE);
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);

        if (!this.multiplierModalOpen) return;
        if (this.container.getFilter(this.multiplierModalSlot) == null) {
            this.closeMultiplierModal();
            return;
        }

        this.updateMultiplierModalWidgetPositions();
        this.drawMultiplierModal(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (this.multiplierModalOpen) {
            this.handleMultiplierModalKeyTyped(typedChar, keyCode);
            return;
        }

        if (this.checkHotbarKeys(keyCode)) return;

        if (KeyBindings.QUICK_ADD_TO_FILTER.isActiveAndMatches(keyCode)) {
            Slot hoveredSlot = this.getSlotUnderMouse();
            ItemStack stack = QuickAddHelper.getItemUnderCursor(hoveredSlot);
            if (!stack.isEmpty()) {
                CellsNetworkHandler.INSTANCE.sendToServer(new PacketQuickAddFilter(
                    ResourceType.ITEM, AEItemStack.fromItemStack(stack)
                ));
                return;
            }
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (this.multiplierModalOpen) {
            this.handleMultiplierModalClick(mouseX, mouseY, mouseButton);
            return;
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (this.multiplierModalOpen) return;

        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (this.multiplierModalOpen) return;

        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void actionPerformed(@Nonnull GuiButton btn) throws IOException {
        super.actionPerformed(btn);
    }

    @Override
    @Optional.Method(modid = "jei")
    public List<Target<?>> getPhantomTargets(Object ingredient) {
        this.mapTargetSlot.clear();
        List<Target<?>> targets = new ArrayList<>();

        for (GuiCustomSlot slot : this.guiSlots) {
            if (!(slot instanceof AbstractResourceFilterSlot)) continue;

            AbstractResourceFilterSlot<?> filterSlot = (AbstractResourceFilterSlot<?>) slot;
            if (filterSlot.convertToResource(ingredient) == null) continue;

            Target<Object> target = filterSlot.createJEITarget(this::getGuiLeft, this::getGuiTop);
            targets.add(target);
            this.mapTargetSlot.putIfAbsent(target, slot);
        }

        return targets;
    }

    @Override
    @Optional.Method(modid = "jei")
    public Map<Target<?>, Object> getFakeSlotTargetMap() {
        return this.mapTargetSlot;
    }

    private void addFilterRow(int startSlot, int y) {
        for (int column = 0; column < 9; column++) {
            int slot = startSlot + column;
            CompactingFilterSlot filterSlot = new CompactingFilterSlot(
                this.container::getFilter,
                slot,
                SLOT_START_X + column * SLOT_SPACING,
                y,
                () -> this.container.getPatternMultiplier(slot)
            );
            filterSlot.setRightClickHandler(() -> this.openMultiplierModal(slot));
            this.guiSlots.add(filterSlot);
        }
    }

    private void addResultRow(CompactingResultSlot.PreviewProvider provider, int startSlot, int y) {
        for (int column = 0; column < 9; column++) {
            int slot = startSlot + column;
            this.guiSlots.add(new CompactingResultSlot(provider, slot, SLOT_START_X + column * SLOT_SPACING, y));
        }
    }

    private void initMultiplierModalWidgets() {
        this.multiplierButtons.clear();

        this.plus1 = new GuiButton(0, 0, 0, 22, 20, "+1");
        this.plus10 = new GuiButton(1, 0, 0, 28, 20, "+10");
        this.plus100 = new GuiButton(2, 0, 0, 32, 20, "+100");
        this.plus1000 = new GuiButton(3, 0, 0, 38, 20, "+1000");

        this.minus1 = new GuiButton(4, 0, 0, 22, 20, "-1");
        this.minus10 = new GuiButton(5, 0, 0, 28, 20, "-10");
        this.minus100 = new GuiButton(6, 0, 0, 32, 20, "-100");
        this.minus1000 = new GuiButton(7, 0, 0, 38, 20, "-1000");

        Collections.addAll(
            this.multiplierButtons,
            this.plus1,
            this.plus10,
            this.plus100,
            this.plus1000,
            this.minus1,
            this.minus10,
            this.minus100,
            this.minus1000
        );

        this.multiplierField = new GuiNumberBox(this.fontRenderer, 0, 0, 59, this.fontRenderer.FONT_HEIGHT, Integer.class);
        this.multiplierField.setEnableBackgroundDrawing(false);
        this.multiplierField.setMaxStringLength(10);
        this.multiplierField.setTextColor(0xFFFFFF);
        this.multiplierField.setVisible(true);

        this.updateMultiplierModalWidgetPositions();

        if (this.multiplierModalOpen) {
            this.syncMultiplierFieldFromContainer();
            this.multiplierField.setFocused(true);
        } else {
            this.multiplierField.setFocused(false);
        }
    }

    private void updateMultiplierModalWidgetPositions() {
        int left = this.getMultiplierModalLeft();
        int top = this.getMultiplierModalTop();

        this.plus1.x = left + 20;
        this.plus1.y = top + 34;
        this.plus10.x = left + 48;
        this.plus10.y = top + 34;
        this.plus100.x = left + 82;
        this.plus100.y = top + 34;
        this.plus1000.x = left + 120;
        this.plus1000.y = top + 34;

        this.minus1.x = left + 20;
        this.minus1.y = top + 69;
        this.minus10.x = left + 48;
        this.minus10.y = top + 69;
        this.minus100.x = left + 82;
        this.minus100.y = top + 69;
        this.minus1000.x = left + 120;
        this.minus1000.y = top + 69;

        this.multiplierField.x = left + 62;
        this.multiplierField.y = top + 57;
        this.multiplierField.width = 59;
    }

    private void drawMultiplierModal(int mouseX, int mouseY, float partialTicks) {
        int left = this.getMultiplierModalLeft();
        int top = this.getMultiplierModalTop();
        long limit = this.getMultiplierModalLimit();

        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0F, 0.0F, 300.0F);
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(MULTIPLIER_TEXTURE);
        this.drawTexturedModalRect(left, top, 0, 0, MULTIPLIER_MODAL_WIDTH, MULTIPLIER_MODAL_HEIGHT);

        String footer = I18n.format("cells.compacting_pattern_exposer.multiplier.applies");

        this.fontRenderer.drawString(
            I18n.format("cells.compacting_pattern_exposer.multiplier.title", this.multiplierModalSlot + 1),
            left + 8,
            top + 6,
            0x404040
        );
        this.fontRenderer.drawString(
            footer,
            left + (MULTIPLIER_MODAL_WIDTH - this.fontRenderer.getStringWidth(footer)) / 2,
            top + MULTIPLIER_MODAL_HEIGHT - this.fontRenderer.FONT_HEIGHT - 4,
            0x404040
        );
        this.fontRenderer.drawString(
            I18n.format("cells.compacting_pattern_exposer.multiplier.max", this.formatWide(limit)),
            left + 8,
            top + 57,
            0x404040
        );

        for (GuiButton button : this.multiplierButtons) {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            button.drawButton(this.mc, mouseX, mouseY, partialTicks);
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.multiplierField.drawTextBox();

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private void openMultiplierModal(int slot) {
        if (this.container.getFilter(slot) == null) return;

        this.multiplierModalOpen = true;
        this.multiplierModalSlot = slot;
        this.updateMultiplierModalWidgetPositions();
        this.syncMultiplierFieldFromContainer();
        this.multiplierField.setFocused(true);
    }

    private void closeMultiplierModal() {
        this.multiplierModalOpen = false;
        this.multiplierModalSlot = -1;
        this.multiplierField.setFocused(false);
    }

    private void handleMultiplierModalClick(int mouseX, int mouseY, int mouseButton) {
        if (!this.isPointInMultiplierModal(mouseX, mouseY)) {
            this.commitMultiplierField();
            this.closeMultiplierModal();
            return;
        }

        if (mouseButton == 0) {
            for (GuiButton button : this.multiplierButtons) {
                if (!button.mousePressed(this.mc, mouseX, mouseY)) continue;

                button.playPressSound(this.mc.getSoundHandler());
                this.adjustMultiplier(this.getMultiplierButtonDelta(button));
                return;
            }
        }

        this.multiplierField.mouseClicked(mouseX, mouseY, mouseButton);
        if (!this.isPointInMultiplierField(mouseX, mouseY)) {
            this.multiplierField.setFocused(false);
        }
    }

    private void handleMultiplierModalKeyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.closeMultiplierModal();
            return;
        }

        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            this.commitMultiplierField();
            this.closeMultiplierModal();
            return;
        }

        if (!this.multiplierField.isFocused()) return;

        boolean isValidKey = keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_RIGHT
            || keyCode == Keyboard.KEY_LEFT || keyCode == Keyboard.KEY_BACK
            || Character.isDigit(typedChar);

        if (!isValidKey || !this.multiplierField.textboxKeyTyped(typedChar, keyCode)) return;

        String out = this.multiplierField.getText();
        while (out.startsWith("0") && out.length() > 1) out = out.substring(1);

        if (!out.equals(this.multiplierField.getText())) {
            this.multiplierField.setText(out);
            this.multiplierField.setCursorPositionEnd();
        }

        if (!out.isEmpty()) {
            try {
                this.commitMultiplierValue(Long.parseLong(out));
            } catch (NumberFormatException ignored) {
                this.commitMultiplierValue(this.getMultiplierModalLimit());
            }
        }
    }

    private void commitMultiplierField() {
        String value = this.multiplierField.getText();
        if (value.isEmpty()) {
            this.syncMultiplierFieldFromContainer();
            return;
        }

        try {
            this.commitMultiplierValue(Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            this.commitMultiplierValue(this.getMultiplierModalLimit());
        }
    }

    private void adjustMultiplier(long delta) {
        long base = this.getCurrentMultiplierFieldValue();
        long limit = this.getMultiplierModalLimit();

        long newValue;
        if (delta > 0) {
            long headroom = limit - base;
            newValue = base + Math.min(delta, headroom);
        } else {
            newValue = base + delta;
        }

        this.commitMultiplierValue(newValue);
    }

    private void commitMultiplierValue(long value) {
        if (this.multiplierModalSlot < 0) return;

        long clamped = this.clampMultiplierValue(value);
        long current = this.container.getPatternMultiplier(this.multiplierModalSlot);

        this.onMultiplierValueChanged(clamped);
        if (clamped == current) return;

        this.container.applyClientPatternMultiplier(this.multiplierModalSlot, clamped);
        CellsNetworkHandler.INSTANCE.sendToServer(new PacketCompactingPatternMultiplier(this.multiplierModalSlot, clamped));
    }

    private void onMultiplierValueChanged(long value) {
        long clamped = this.clampMultiplierValue(value);
        String displayValue = Long.toString(clamped);
        String currentText = this.multiplierField.getText();

        if (!currentText.equals(displayValue)) {
            int cursorFromEnd = currentText.length() - this.multiplierField.getCursorPosition();
            this.multiplierField.setText(displayValue);
            int newCursor = Math.max(0, displayValue.length() - cursorFromEnd);
            this.multiplierField.setCursorPosition(newCursor);
        }
    }

    private long clampMultiplierValue(long value) {
        return Math.max(1L, Math.min(value, this.getMultiplierModalLimit()));
    }

    private void syncMultiplierFieldFromContainer() {
        long multiplier = this.container.getPatternMultiplier(this.multiplierModalSlot);
        this.onMultiplierValueChanged(multiplier);
        this.multiplierField.setCursorPositionEnd();
    }

    private long getCurrentMultiplierFieldValue() {
        String value = this.multiplierField.getText();
        if (value.isEmpty()) {
            return this.container.getPatternMultiplier(this.multiplierModalSlot);
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return this.container.getPatternMultiplier(this.multiplierModalSlot);
        }
    }

    private long getMultiplierModalLimit() {
        return this.container.getPatternMultiplierLimit(this.multiplierModalSlot);
    }

    private int getMultiplierButtonDelta(GuiButton button) {
        if (button == this.plus1) return 1;
        if (button == this.plus10) return 10;
        if (button == this.plus100) return 100;
        if (button == this.plus1000) return 1000;

        if (button == this.minus1) return -1;
        if (button == this.minus10) return -10;
        if (button == this.minus100) return -100;
        if (button == this.minus1000) return -1000;

        return 0;
    }

    private boolean isPointInMultiplierModal(int mouseX, int mouseY) {
        int left = this.getMultiplierModalLeft();
        int top = this.getMultiplierModalTop();
        return mouseX >= left && mouseX < left + MULTIPLIER_MODAL_WIDTH
            && mouseY >= top && mouseY < top + MULTIPLIER_MODAL_HEIGHT;
    }

    private boolean isPointInMultiplierField(int mouseX, int mouseY) {
        return mouseX >= this.multiplierField.x && mouseX < this.multiplierField.x + this.multiplierField.width
            && mouseY >= this.multiplierField.y && mouseY < this.multiplierField.y + this.fontRenderer.FONT_HEIGHT + 2;
    }

    private int getMultiplierModalLeft() {
        return this.guiLeft + (this.xSize - MULTIPLIER_MODAL_WIDTH) / 2;
    }

    private int getMultiplierModalTop() {
        return this.guiTop + (this.ySize - MULTIPLIER_MODAL_HEIGHT) / 2;
    }

    private String formatWide(long amount) {
        return ReadableNumberConverter.INSTANCE.toSlimReadableForm(amount);
    }
}