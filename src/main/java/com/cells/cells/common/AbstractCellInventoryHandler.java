package com.cells.cells.common;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.FuzzyMode;
import appeng.api.config.IncludeExclude;
import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.storage.MEInventoryHandler;
import appeng.me.storage.MEPassThrough;
import appeng.util.prioritylist.FuzzyPriorityList;
import appeng.util.prioritylist.PrecisePriorityList;


/**
 * Generic cell inventory handler for all custom cell types.
 * <p>
 * Wraps an ICellInventory and provides partition filtering and upgrade processing.
 * Handles standard AE2 upgrade cards: Fuzzy, Inverter, Sticky.
 * <p>
 * Extend this class for cell-specific behavior (e.g., CompactingCellInventoryHandler
 * overrides passesBlackOrWhitelist for compression chain items).
 *
 * @param <T> The AE stack type for this channel
 */
public class AbstractCellInventoryHandler<T extends IAEStack<T>> extends MEInventoryHandler<T> implements ICellInventoryHandler<T> {

    protected IncludeExclude myWhitelist = IncludeExclude.WHITELIST;

    public AbstractCellInventoryHandler(IMEInventory<T> inventory, IStorageChannel<T> channel) {
        super(inventory, channel);

        ICellInventory<T> ci = getCellInv();
        if (ci == null) return;

        IItemList<T> priorityList = channel.createList();

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

        for (int x = 0; x < config.getSlots(); x++) {
            ItemStack is = config.getStackInSlot(x);
            if (!is.isEmpty()) {
                T configItem = channel.createStack(is);
                if (configItem != null) priorityList.add(configItem);
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
    public ICellInventory<T> getCellInv() {
        Object o = this.getInternal();
        if (o instanceof MEPassThrough) o = ((MEPassThrough<?>) o).getInternal();

        return (ICellInventory<T>) (o instanceof ICellInventory ? o : null);
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
