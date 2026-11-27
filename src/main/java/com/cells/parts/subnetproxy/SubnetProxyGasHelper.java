package com.cells.parts.subnetproxy;

import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.config.Upgrades;
import appeng.api.storage.data.IItemList;

import com.mekeng.github.common.item.ItemDummyGas;
import com.mekeng.github.common.me.data.IAEGasStack;
import com.mekeng.github.common.me.storage.IGasStorageChannel;
import mekanism.api.gas.GasStack;

import com.cells.network.sync.ResourceType;


/**
 * Optional gas-channel implementation loaded after Mekanism Energistics is available. It captures
 * gas filter entries and supplies the gas storage channel to the shared proxy flows.
 */
public final class SubnetProxyGasHelper extends SubnetProxyChannel<IAEGasStack> {

    private SubnetProxyGasHelper() {
        super(ResourceType.GAS, AEApi.instance().storage().getStorageChannel(IGasStorageChannel.class));
    }

    public static SubnetProxyChannel<IAEGasStack> channel() {
        return new SubnetProxyGasHelper();
    }

    @Override
    public Filter<IAEGasStack> captureFilter(PartSubnetProxyFront front) {
        IItemList<IAEGasStack> gases = this.getChannel().createList();

        for (int slot = 0; slot < front.getFilterSlots(); slot++) {
            ItemStack stack = front.getFilter(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof ItemDummyGas)) continue;

            GasStack gas = ((ItemDummyGas) stack.getItem()).getGasStack(stack);
            if (gas != null) gases.add(this.getChannel().createStack(new GasStack(gas.getGas(), 1)));
        }

        boolean invert = front.getInstalledUpgrades(Upgrades.INVERTER) > 0;
        return new Filter<>(gases, invert);
    }

    public static boolean isDummy(ItemStack stack) {
        return stack.getItem() instanceof ItemDummyGas;
    }
}
