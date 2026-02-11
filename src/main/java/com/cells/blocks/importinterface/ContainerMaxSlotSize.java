package com.cells.blocks.importinterface;

import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;

import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.util.Platform;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Container for the Max Slot Size configuration GUI.
 * Similar to AE2's ContainerPriority but for slot size configuration.
 */
public class ContainerMaxSlotSize extends AEBaseContainer {

    private final TileImportInterface tile;

    @SideOnly(Side.CLIENT)
    private GuiTextField textField;

    @GuiSync(0)
    public int maxSlotSize = TileImportInterface.DEFAULT_MAX_SLOT_SIZE;

    public ContainerMaxSlotSize(final InventoryPlayer ip, final TileImportInterface tile) {
        super(ip, tile, null);
        this.tile = tile;
    }

    @SideOnly(Side.CLIENT)
    public void setTextField(final GuiTextField field) {
        this.textField = field;
        this.textField.setText(String.valueOf(this.maxSlotSize));
    }

    public void setMaxSlotSize(final int newValue) {
        int clamped = Math.max(TileImportInterface.MIN_MAX_SLOT_SIZE, newValue);
        this.tile.setMaxSlotSize(clamped);
        this.maxSlotSize = clamped;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        if (Platform.isServer()) this.maxSlotSize = this.tile.getMaxSlotSize();
    }

    @Override
    public void onUpdate(final String field, final Object oldValue, final Object newValue) {
        if (field.equals("maxSlotSize") && this.textField != null) {
            this.textField.setText(String.valueOf(this.maxSlotSize));
        }

        super.onUpdate(field, oldValue, newValue);
    }

    public TileImportInterface getTile() {
        return this.tile;
    }
}
