package com.cells.blocks.importinterface;

import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.tileentity.TileEntity;

import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.util.Platform;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Container for the Max Slot Size configuration GUI.
 * Similar to AE2's ContainerPriority but for slot size configuration.
 * Works with any tile entity implementing {@link IImportInterfaceHost}.
 */
public class ContainerMaxSlotSize extends AEBaseContainer {

    private final IImportInterfaceHost host;

    @SideOnly(Side.CLIENT)
    private GuiTextField textField;

    @GuiSync(0)
    public long maxSlotSize = TileImportInterface.DEFAULT_MAX_SLOT_SIZE;

    public ContainerMaxSlotSize(final InventoryPlayer ip, final IImportInterfaceHost host) {
        super(ip, (TileEntity) host, null);
        this.host = host;
    }

    @SideOnly(Side.CLIENT)
    public void setTextField(final GuiTextField field) {
        this.textField = field;
        this.textField.setText(String.valueOf(this.maxSlotSize));
    }

    public void setMaxSlotSize(final int newValue) {
        int clamped = Math.max(TileImportInterface.MIN_MAX_SLOT_SIZE, newValue);
        this.host.setMaxSlotSize(clamped);
        this.maxSlotSize = clamped;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        if (Platform.isServer()) this.maxSlotSize = this.host.getMaxSlotSize();
    }

    @Override
    public void onUpdate(final String field, final Object oldValue, final Object newValue) {
        if (field.equals("maxSlotSize") && this.textField != null) {
            this.textField.setText(String.valueOf(this.maxSlotSize));
        }

        super.onUpdate(field, oldValue, newValue);
    }

    public IImportInterfaceHost getHost() {
        return this.host;
    }
}
