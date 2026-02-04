package com.cells.cells.hyperdensity.fluid;

import net.minecraft.item.ItemStack;

import appeng.api.config.FuzzyMode;
import appeng.api.config.IncludeExclude;
import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IItemList;
import appeng.me.storage.MEInventoryHandler;
import appeng.me.storage.MEPassThrough;
import appeng.util.prioritylist.FuzzyPriorityList;
import appeng.util.prioritylist.PrecisePriorityList;

import net.minecraftforge.items.IItemHandler;


/**
 * Inventory handler wrapper for hyper-density fluid cells.
 * Handles partition filtering and upgrade processing.
 */
public class FluidHyperDensityCellInventoryHandler extends MEInventoryHandler<IAEFluidStack> implements ICellInventoryHandler<IAEFluidStack> {

    private IncludeExclude myWhitelist = IncludeExclude.WHITELIST;

    public FluidHyperDensityCellInventoryHandler(IMEInventory<IAEFluidStack> inventory, IStorageChannel<IAEFluidStack> channel) {
        super(inventory, channel);

        ICellInventory<IAEFluidStack> ci = getCellInv();
        if (ci == null) return;

        IItemList<IAEFluidStack> priorityList = channel.createList();

        IItemHandler upgrades = ci.getUpgradesInventory();
        IItemHandler config = ci.getConfigInventory();
        FuzzyMode fzMode = ci.getFuzzyMode();

        boolean hasInverter = false;
        boolean hasFuzzy = false;
        boolean hasSticky = false;

        for (int x = 0; x < upgrades.getSlots(); x++) {
            ItemStack is = upgrades.getStackInSlot(x);
            if (!is.isEmpty() && is.getItem() instanceof IUpgradeModule) {
                Upgrades u = ((IUpgradeModule) is.getItem()).getType(is);
                if (u != null) {
                    switch (u) {
                        case FUZZY:
                            hasFuzzy = true;
                            break;
                        case INVERTER:
                            hasInverter = true;
                            break;
                        case STICKY:
                            hasSticky = true;
                            break;
                        default:
                    }
                }
            }
        }

        IFluidStorageChannel fluidChannel = (IFluidStorageChannel) channel;
        for (int x = 0; x < config.getSlots(); x++) {
            ItemStack is = config.getStackInSlot(x);
            if (!is.isEmpty()) {
                IAEFluidStack configFluid = fluidChannel.createStack(is);
                if (configFluid != null) priorityList.add(configFluid);
            }
        }

        this.myWhitelist = hasInverter ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST;
        this.setWhitelist(this.myWhitelist);

        if (hasSticky) setSticky(true);

        if (!priorityList.isEmpty()) {
            if (hasFuzzy) {
                this.setPartitionList(new FuzzyPriorityList<>(priorityList, fzMode));
            } else {
                this.setPartitionList(new PrecisePriorityList<>(priorityList));
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ICellInventory<IAEFluidStack> getCellInv() {
        Object o = this.getInternal();
        if (o instanceof MEPassThrough) o = ((MEPassThrough<?>) o).getInternal();

        return (ICellInventory<IAEFluidStack>) (o instanceof ICellInventory ? o : null);
    }

    @Override
    public boolean isPreformatted() {
        return !this.getPartitionList().isEmpty();
    }

    @Override
    public boolean isFuzzy() {
        return this.getPartitionList() instanceof FuzzyPriorityList;
    }

    @Override
    public IncludeExclude getIncludeExcludeMode() {
        return this.myWhitelist;
    }
}
