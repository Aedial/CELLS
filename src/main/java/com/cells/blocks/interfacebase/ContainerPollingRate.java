package com.cells.blocks.interfacebase;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.tileentity.TileEntity;

import appeng.api.parts.IPart;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.util.Platform;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Container for the Polling Rate configuration GUI.
 * Similar to ContainerMaxSlotSize but for polling rate configuration.
 * Works with any host implementing {@link IInterfaceHost} (both TileEntity and IPart).
 */
public class ContainerPollingRate extends AEBaseContainer {

    private final IInterfaceHost host;

    @SideOnly(Side.CLIENT)
    private IPollingRateListener listener;

    /** Polling rate in ticks. 0 = adaptive (AE2 default), unless config requires higher. */
    @GuiSync(0)
    public long pollingRate = AbstractResourceInterfaceLogic.getMinPollingRate();

    public ContainerPollingRate(final InventoryPlayer ip, final IInterfaceHost host) {
        super(ip, host instanceof TileEntity ? (TileEntity) host : null, host instanceof IPart ? (IPart) host : null);
        this.host = host;
    }

    @SideOnly(Side.CLIENT)
    public void setListener(final IPollingRateListener listener) {
        this.listener = listener;
        this.listener.onPollingRateChanged(this.pollingRate);
    }

    public void setPollingRate(final int newValue) {
        int minRate = AbstractResourceInterfaceLogic.getMinPollingRate();
        int clamped = Math.max(minRate, newValue);
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

    public IInterfaceHost getHost() {
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
