package com.cells.cells.creative.gas;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import mekanism.api.gas.GasStack;

import com.mekeng.github.common.me.data.IAEGasStack;
import com.mekeng.github.common.me.data.impl.AEGasStack;

import com.cells.gui.slots.GasFilterSlot;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.sync.PacketResourceSlot;
import com.cells.network.sync.ResourceType;


/**
 * Gas filter slot for Creative Gas Cell.
 * <p>
 * Thin wrapper around {@link GasFilterSlot} that converts between GasStack and IAEGasStack.
 * Uses unified {@link PacketResourceSlot} for server sync.
 */
public class GuiCreativeGasFilterSlot extends GasFilterSlot {

    private final CreativeGasCellTankAdapter tankAdapter;

    public GuiCreativeGasFilterSlot(final CreativeGasCellTankAdapter tankAdapter,
                                    final int slot, final int x, final int y) {
        // Provider returns IAEGasStack by wrapping the tank adapter's GasStack
        super(s -> {
            GasStack gas = tankAdapter.getGasStack(s);
            return gas != null ? AEGasStack.of(gas) : null;
        }, slot, x, y);
        this.tankAdapter = tankAdapter;
    }

    @Override
    public void setResource(@Nullable IAEGasStack resource) {
        // Convert IAEGasStack to GasStack for the tank adapter (client-side update)
        GasStack gasStack = resource != null ? resource.getGasStack() : null;
        this.tankAdapter.setGas(this.slot, gasStack);

        // Send to server via unified PacketResourceSlot
        CellsNetworkHandler.INSTANCE.sendToServer(
            new PacketResourceSlot(ResourceType.GAS, this.slot, resource)
        );
    }

    @Override
    public boolean isSlotEnabled() {
        return true;
    }

    public ItemStack getDisplayStack() {
        return ItemStack.EMPTY;
    }
}
