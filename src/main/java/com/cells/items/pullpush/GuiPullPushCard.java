package com.cells.items.pullpush;

import java.io.IOException;

import javax.annotation.Nonnull;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.EnumHand;

import appeng.client.gui.AEBaseGui;

import com.cells.network.CellsNetworkHandler;
import com.cells.network.packets.PacketSetPullPushRate;
import com.cells.util.PollingRateUtils;


/**
 * GUI for configuring the interval and quantity of a Pull/Push Card.
 * Has +/- buttons for 1s, 1m, 1h, 1d. Holding shift multiplies by 10.
 * Time is displayed in format "1d 2h 3m 4s" (skipping zero parts) or "0".
 */
public class GuiPullPushCard extends AEBaseGui implements ContainerPullPushCard.IValuesListener {

    private GuiButton plusSec;
    private GuiButton plusMin;
    private GuiButton plusHour;
    private GuiButton plusDay;
    private GuiButton minusSec;
    private GuiButton minusMin;
    private GuiButton minusHour;
    private GuiButton minusDay;

    private int currentInterval = 20;
    private int currentQuantity = 0;

    public GuiPullPushCard(final InventoryPlayer inventoryPlayer, final EnumHand hand) {
        super(new ContainerPullPushCard(inventoryPlayer, hand));
    }

    @Override
    public void initGui() {
        super.initGui();

        // Register as listener for interval changes
        ((ContainerPullPushCard) this.inventorySlots).setListener(this);

        // Plus buttons (top row) - positioned similar to priority GUI
        this.buttonList.add(this.plusSec = new GuiButton(0, this.guiLeft + 20, this.guiTop + 32, 28, 20, "+1s"));
        this.buttonList.add(this.plusMin = new GuiButton(1, this.guiLeft + 54, this.guiTop + 32, 28, 20, "+1m"));
        this.buttonList.add(this.plusHour = new GuiButton(2, this.guiLeft + 88, this.guiTop + 32, 28, 20, "+1h"));
        this.buttonList.add(this.plusDay = new GuiButton(3, this.guiLeft + 122, this.guiTop + 32, 28, 20, "+1d"));

        // Minus buttons (bottom row)
        this.buttonList.add(this.minusSec = new GuiButton(4, this.guiLeft + 20, this.guiTop + 69, 28, 20, "-1s"));
        this.buttonList.add(this.minusMin = new GuiButton(5, this.guiLeft + 54, this.guiTop + 69, 28, 20, "-1m"));
        this.buttonList.add(this.minusHour = new GuiButton(6, this.guiLeft + 88, this.guiTop + 69, 28, 20, "-1h"));
        this.buttonList.add(this.minusDay = new GuiButton(7, this.guiLeft + 122, this.guiTop + 69, 28, 20, "-1d"));
    }

    @Override
    public void onIntervalChanged(int interval) {
        this.currentInterval = interval;
    }

    @Override
    public void onQuantityChanged(int quantity) {
        this.currentQuantity = quantity;
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        String title = I18n.format("cells.push_pull_card.interval.title");
        this.fontRenderer.drawString(title, 8, 6, 0x404040);

        // Draw the current interval centered in the display area
        String timeStr = PollingRateUtils.format(this.currentInterval);
        int textWidth = this.fontRenderer.getStringWidth(timeStr);
        int centerX = (this.xSize - textWidth) / 2;
        this.fontRenderer.drawString(timeStr, centerX, 57, 0xFFFFFF);

        // TODO: add quantity display and controls, like Max Slot GUI (but capped at Integer.MAX_VALUE)
        // Text field at 54,106 (2,147,483,647 = ~66px) - size: 75x10
        // We also want the "= {}" short suffix after field
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        // Use AE2's priority texture as base (same layout)
        this.bindTexture("guis/priority.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
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

        if (btn == this.plusSec) return PollingRateUtils.TICKS_PER_SECOND * multiplier;
        if (btn == this.plusMin) return PollingRateUtils.TICKS_PER_MINUTE * multiplier;
        if (btn == this.plusHour) return PollingRateUtils.TICKS_PER_HOUR * multiplier;
        if (btn == this.plusDay) return PollingRateUtils.TICKS_PER_DAY * multiplier;

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

        this.plusSec.displayString = shift ? "+10s" : "+1s";
        this.plusMin.displayString = shift ? "+10m" : "+1m";
        this.plusHour.displayString = shift ? "+10h" : "+1h";
        this.plusDay.displayString = shift ? "+10d" : "+1d";

        this.minusSec.displayString = shift ? "-10s" : "-1s";
        this.minusMin.displayString = shift ? "-10m" : "-1m";
        this.minusHour.displayString = shift ? "-10h" : "-1h";
        this.minusDay.displayString = shift ? "-10d" : "-1d";
    }
}
