package com.cells.cells.compacting;

import net.minecraft.item.ItemStack;

import appeng.api.config.FuzzyMode;
import appeng.api.config.IncludeExclude;
import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.me.storage.MEInventoryHandler;
import appeng.me.storage.MEPassThrough;
import appeng.util.prioritylist.FuzzyPriorityList;
import appeng.util.prioritylist.PrecisePriorityList;

import net.minecraftforge.items.IItemHandler;


/**
 * Inventory handler wrapper for compacting cells.
 * Handles partition filtering and upgrade processing.
 */
public class CompactingCellInventoryHandler extends MEInventoryHandler<IAEItemStack> implements ICellInventoryHandler<IAEItemStack> {

    private IncludeExclude myWhitelist = IncludeExclude.WHITELIST;

    public CompactingCellInventoryHandler(IMEInventory<IAEItemStack> inventory, IStorageChannel<IAEItemStack> channel) {
        super(inventory, channel);

        ICellInventory<IAEItemStack> ci = getCellInv();
        if (ci == null) return;

        IItemList<IAEItemStack> priorityList = channel.createList();

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
                IAEItemStack configItem = channel.createStack(is);
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
    public ICellInventory<IAEItemStack> getCellInv() {
        Object o = this.getInternal();
        if (o instanceof MEPassThrough) o = ((MEPassThrough<?>) o).getInternal();

        return (ICellInventory<IAEItemStack>) (o instanceof ICellInventory ? o : null);
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

    /**
     * Override to allow compression chain items through the filter.
     * The underlying CompactingCellInventory handles the actual validation.
     */
    @Override
    public boolean passesBlackOrWhitelist(IAEItemStack input) {
        // First check normal partition list
        if (super.passesBlackOrWhitelist(input)) return true;

        // For compacting cells with whitelist mode, also check if item is in compression chain
        if (myWhitelist != IncludeExclude.WHITELIST) return false;

        ICellInventory<IAEItemStack> ci = getCellInv();
        if (!(ci instanceof CompactingCellInventory)) return false;

        CompactingCellInventory compacting = (CompactingCellInventory) ci;

        return compacting.isInCompressionChain(input);
    }
}
