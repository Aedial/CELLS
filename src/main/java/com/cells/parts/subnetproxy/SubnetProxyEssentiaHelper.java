package com.cells.parts.subnetproxy;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import appeng.api.AEApi;
import appeng.api.config.Upgrades;
import appeng.api.networking.IGridHost;
import appeng.api.storage.data.IItemList;
import appeng.api.storage.IMEInventoryHandler;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IAspectContainer;
import thaumicenergistics.api.EssentiaStack;
import thaumicenergistics.api.storage.IAEEssentiaStack;
import thaumicenergistics.api.storage.IEssentiaStorageChannel;
import thaumicenergistics.integration.appeng.grid.EssentiaContainerAdapter;
import thaumicenergistics.item.ItemDummyAspect;
import thaumicenergistics.part.PartEssentiaStorageBus;
import thaumicenergistics.tile.TileInfusionProvider;

import com.cells.network.sync.ResourceType;


/**
 * Optional essentia-channel implementation for aspect filters and storage-bus source checks after
 * Thaumic Energistics loads. Essentia Interfaces and Infusion Providers count as passthroughs.
 */
public final class SubnetProxyEssentiaHelper extends SubnetProxyChannel<IAEEssentiaStack> {

    private SubnetProxyEssentiaHelper() {
        super(ResourceType.ESSENTIA, AEApi.instance().storage().getStorageChannel(IEssentiaStorageChannel.class));
    }

    public static SubnetProxyChannel<IAEEssentiaStack> channel() {
        return new SubnetProxyEssentiaHelper();
    }

    @Override
    public SubnetProxyProvider.Handler<IAEEssentiaStack> captureHandler(SubnetProxyProvider provider,
            IGridHost host, IMEInventoryHandler<IAEEssentiaStack> inventory) {
        if (!(host instanceof PartEssentiaStorageBus)
            || inventory.getClass() != EssentiaContainerAdapter.class) {
            return super.captureHandler(provider, host, inventory);
        }

        // Original Thaumic Energistics recreates the bus wrapper on each lookup
        TileEntity container = target(host);
        if (!(container instanceof IAspectContainer) || container.isInvalid()) return null;

        return new SubnetProxyProvider.Handler<>(provider, inventory, host, container);
    }

    @Override
    public boolean isPassthrough(IGridHost host) {
        if (super.isPassthrough(host)) return true;
        if (!(host instanceof PartEssentiaStorageBus)) return false;

        TileEntity target = target(host);
        if (target == null) return false;

        return target instanceof TileInfusionProvider
            || target.getClass().getName().equals("thaumicenergistics.tile.TileEssentiaInterface");
    }

    @Override
    public Filter<IAEEssentiaStack> captureFilter(PartSubnetProxyFront front) {
        IItemList<IAEEssentiaStack> aspects = this.getChannel().createList();

        for (int slot = 0; slot < front.getFilterSlots(); slot++) {
            ItemStack stack = front.getFilter(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof ItemDummyAspect)) continue;

            Aspect aspect = ((ItemDummyAspect) stack.getItem()).getAspect(stack);
            if (aspect != null) aspects.add(this.getChannel().createStack(new EssentiaStack(aspect, 1)));
        }

        boolean invert = front.getInstalledUpgrades(Upgrades.INVERTER) > 0;
        return new Filter<>(aspects, invert);
    }

    public static boolean isDummy(ItemStack stack) {
        return stack.getItem() instanceof ItemDummyAspect;
    }
}
