package com.cells.blocks.importinterface;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.tileentity.TileEntity;

import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.util.Platform;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Container for the Polling Rate configuration GUI.
 * Similar to ContainerMaxSlotSize but for polling rate configuration.
 * Works with any tile entity implementing {@link IImportInterfaceHost}.
 */
public class ContainerPollingRate extends AEBaseContainer {

    private final IImportInterfaceHost host;

    @SideOnly(Side.CLIENT)
    private IPollingRateListener listener;

    @GuiSync(0)
    public long pollingRate = TileImportInterface.DEFAULT_POLLING_RATE;

    public ContainerPollingRate(final InventoryPlayer ip, final IImportInterfaceHost host) {
        super(ip, (TileEntity) host, null);
        this.host = host;
    }

    @SideOnly(Side.CLIENT)
    public void setListener(final IPollingRateListener listener) {
        this.listener = listener;
        this.listener.onPollingRateChanged(this.pollingRate);
    }

    public void setPollingRate(final int newValue) {
        int clamped = Math.max(0, newValue);
        this.host.setPollingRate(clamped);
        this.pollingRate = clamped;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        if (Platform.isServer()) this.pollingRate = this.host.getPollingRate();
    }

    @Override
    public void onUpdate(final String field, final Object oldValue, final Object newValue) {
        if (field.equals("pollingRate") && this.listener != null) {
            this.listener.onPollingRateChanged(this.pollingRate);
        }

        super.onUpdate(field, oldValue, newValue);
    }

    public IImportInterfaceHost getHost() {
        return this.host;
    }

    /**
     * Interface for listening to polling rate changes (used by GUI to update display).
     */
    @SideOnly(Side.CLIENT)
    public interface IPollingRateListener {
        void onPollingRateChanged(long pollingRate);
    }
}
