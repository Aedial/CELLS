package com.cells.blocks.importinterface;

import java.io.IOException;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiTabButton;

import com.cells.BlockRegistry;
import com.cells.gui.CellsGuiHandler;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.packets.PacketOpenGui;
import com.cells.network.packets.PacketSetPollingRate;


/**
 * GUI for configuring the polling rate of the Import Interface.
 * Has +/- buttons for 1s, 1m, 1h, 1d. Holding shift multiplies by 10.
 * Time is displayed in format "1d 2h 3m 4s" (skipping zero parts) or "0".
 */
public class GuiPollingRate extends AEBaseGui implements ContainerPollingRate.IPollingRateListener {

    private GuiTabButton originalGuiBtn;

    private GuiButton plusSec;
    private GuiButton plusMin;
    private GuiButton plusHour;
    private GuiButton plusDay;
    private GuiButton minusSec;
    private GuiButton minusMin;
    private GuiButton minusHour;
    private GuiButton minusDay;

    private final TileImportInterface tile;
    private int currentPollingRate = 0;

    public GuiPollingRate(final InventoryPlayer inventoryPlayer, final TileImportInterface tile) {
        super(new ContainerPollingRate(inventoryPlayer, tile));
        this.tile = tile;
    }

    @Override
    public void initGui() {
        super.initGui();

        // Register as listener for polling rate changes
        ((ContainerPollingRate) this.inventorySlots).setListener(this);

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

        // Back button to return to Import Interface GUI
        this.buttonList.add(this.originalGuiBtn = new GuiTabButton(
            this.guiLeft + 154,
            this.guiTop,
            new ItemStack(BlockRegistry.IMPORT_INTERFACE),
            I18n.format("gui.cells.import_interface.title"),
            this.itemRender
        ));

        // TODO: add a footer with what this does (0 = default polling rate, nonzero = fixed interval)
    }

    @Override
    public void onPollingRateChanged(int pollingRate) {
        this.currentPollingRate = pollingRate;
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(I18n.format("gui.cells.polling_rate.title"), 8, 6, 0x404040);

        // Draw the current polling rate centered in the display area
        String timeStr = TileImportInterface.formatPollingRate(this.currentPollingRate);
        int textWidth = this.fontRenderer.getStringWidth(timeStr);
        int centerX = (this.xSize - textWidth) / 2;
        this.fontRenderer.drawString(timeStr, centerX, 57, 0xFFFFFF);
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        // Use AE2's priority texture as base (same layout)
        this.bindTexture("guis/priority.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
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

        int delta = getButtonDelta(btn);
        if (delta != 0) addPollingRate(delta);
    }

    private int getButtonDelta(final GuiButton btn) {
        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        int multiplier = shift ? 10 : 1;

        if (btn == this.plusSec) return TileImportInterface.TICKS_PER_SECOND * multiplier;
        if (btn == this.plusMin) return TileImportInterface.TICKS_PER_MINUTE * multiplier;
        if (btn == this.plusHour) return TileImportInterface.TICKS_PER_HOUR * multiplier;
        if (btn == this.plusDay) return TileImportInterface.TICKS_PER_DAY * multiplier;

        if (btn == this.minusSec) return -TileImportInterface.TICKS_PER_SECOND * multiplier;
        if (btn == this.minusMin) return -TileImportInterface.TICKS_PER_MINUTE * multiplier;
        if (btn == this.minusHour) return -TileImportInterface.TICKS_PER_HOUR * multiplier;
        if (btn == this.minusDay) return -TileImportInterface.TICKS_PER_DAY * multiplier;

        return 0;
    }

    private void addPollingRate(final int delta) {
        int result = this.currentPollingRate + delta;
        result = Math.max(0, result);
        this.currentPollingRate = result;
        CellsNetworkHandler.INSTANCE.sendToServer(new PacketSetPollingRate(result));
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
