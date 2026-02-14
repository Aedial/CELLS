package com.cells.blocks.importinterface;

import net.minecraft.entity.player.InventoryPlayer;

import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.util.Platform;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Container for the Polling Rate configuration GUI.
 * Similar to ContainerMaxSlotSize but for polling rate configuration.
 */
public class ContainerPollingRate extends AEBaseContainer {

    private final TileImportInterface tile;

    @SideOnly(Side.CLIENT)
    private IPollingRateListener listener;

    @GuiSync(0)
    public int pollingRate = TileImportInterface.DEFAULT_POLLING_RATE;

    public ContainerPollingRate(final InventoryPlayer ip, final TileImportInterface tile) {
        super(ip, tile, null);
        this.tile = tile;
    }

    @SideOnly(Side.CLIENT)
    public void setListener(final IPollingRateListener listener) {
        this.listener = listener;
        this.listener.onPollingRateChanged(this.pollingRate);
    }

    public void setPollingRate(final int newValue) {
        int clamped = Math.max(0, newValue);
        this.tile.setPollingRate(clamped);
        this.pollingRate = clamped;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        if (Platform.isServer()) this.pollingRate = this.tile.getPollingRate();
    }

    @Override
    public void onUpdate(final String field, final Object oldValue, final Object newValue) {
        if (field.equals("pollingRate") && this.listener != null) {
            this.listener.onPollingRateChanged(this.pollingRate);
        }

        super.onUpdate(field, oldValue, newValue);
    }

    public TileImportInterface getTile() {
        return this.tile;
    }

    /**
     * Interface for listening to polling rate changes (used by GUI to update display).
     */
    @SideOnly(Side.CLIENT)
    public interface IPollingRateListener {
        void onPollingRateChanged(int pollingRate);
    }
}
