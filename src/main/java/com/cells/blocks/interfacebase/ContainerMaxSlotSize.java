package com.cells.blocks.interfacebase;

import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.tileentity.TileEntity;

import appeng.api.parts.IPart;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.util.Platform;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Container for the Max Slot Size configuration GUI.
 * Similar to AE2's ContainerPriority but for slot size configuration.
 * Works with any host implementing {@link IInterfaceHost} (both TileEntity and IPart).
 */
public class ContainerMaxSlotSize extends AEBaseContainer {

    private final IInterfaceHost host;

    @SideOnly(Side.CLIENT)
    private GuiTextField textField;

    @GuiSync(0)
    public long maxSlotSize = -1;

    public ContainerMaxSlotSize(final InventoryPlayer ip, final IInterfaceHost host) {
        super(ip, host instanceof TileEntity ? (TileEntity) host : null, host instanceof IPart ? (IPart) host : null);
        this.host = host;
    }

    @SideOnly(Side.CLIENT)
    public void setTextField(final GuiTextField field) {
        this.textField = field;
        this.textField.setText(String.format("%,d", this.maxSlotSize));
    }

    public void setMaxSlotSize(final long newValue) {
        this.maxSlotSize = this.host.setMaxSlotSize(newValue);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        if (Platform.isServer()) this.maxSlotSize = this.host.getMaxSlotSize();
    }

    @Override
    public void onUpdate(final String field, final Object oldValue, final Object newValue) {
        if (field.equals("maxSlotSize") && this.textField != null) {
            // Format with commas for readability, preserve cursor position
            String formatted = String.format("%,d", this.maxSlotSize);
            String currentText = this.textField.getText();

            if (!currentText.equals(formatted)) {
                int cursorFromEnd = currentText.length() - this.textField.getCursorPosition();
                this.textField.setText(formatted);
                int newCursor = Math.max(0, formatted.length() - cursorFromEnd);
                this.textField.setCursorPosition(newCursor);
            }
        }

        super.onUpdate(field, oldValue, newValue);
    }
}
