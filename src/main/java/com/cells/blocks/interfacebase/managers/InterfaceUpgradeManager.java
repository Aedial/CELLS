package com.cells.blocks.interfacebase.managers;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.me.helpers.AENetworkProxy;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.inv.IAEAppEngInventory;

import com.cells.items.ItemAutoPullCard;
import com.cells.items.ItemAutoPushCard;
import com.cells.items.ItemOverflowCard;
import com.cells.items.ItemTrashUnselectedCard;
import com.cells.util.TickManagerHelper;


/**
 * Manages upgrade cards installed in an interface: overflow, trash unselected,
 * auto-pull/push, and capacity cards. Reads card NBT for interval/quantity
 * settings and notifies the parent logic when card state changes.
 */
public class InterfaceUpgradeManager {

    public static final int UPGRADE_SLOTS = 4;

    /**
     * Callback interface for the parent logic to receive upgrade change notifications.
     * This decouples the manager from the concrete logic type.
     */
    public interface Callbacks {

        /** Get the host's grid proxy for tick re-registration. */
        AENetworkProxy getGridProxy();

        /** Get the host's tickable for tick re-registration. */
        appeng.api.networking.ticking.IGridTickable getTickable();

        /** Notify that capacity cards changed, triggering filter/storage cleanup. */
        void onCapacityReduction(int oldCount, int newCount);

        /** Notify that the adjacent capability cache should be refreshed. */
        void onAutoPullPushInstalled();

        /** Notify that the adjacent capability cache should be cleared. */
        void onAutoPullPushRemoved();

        /** Wake up the interface if in adaptive polling mode. */
        void wakeUpIfAdaptive();

        /** Clamp the current page to valid range after capacity changes. */
        void clampCurrentPage(int maxPage);
    }

    private final Callbacks callbacks;
    private final AppEngInternalInventory upgradeInventory;
    private final boolean isExport;

    // ============================== Installed state ==============================

    /** Whether overflow upgrade is installed (import only). */
    private boolean installedOverflowUpgrade = false;

    /** Whether trash unselected upgrade is installed (import only). */
    private boolean installedTrashUnselectedUpgrade = false;

    /** Whether auto-pull/push upgrade is installed. */
    private boolean installedAutoPushPullUpgrade = false;

    /** Tick interval for auto-pull/push operations, stored in the respective card's NBT. */
    private int autoPullPushInterval = -1;

    /** Quantity for auto-pull/push operations, stored in the respective card's NBT. */
    private int autoPushPullQuantity = 0;

    /** Keep quantity for auto-pull/push operations, stored in the respective card's NBT. */
    private int autoPullPushKeepQuantity = 0;

    /** Number of installed capacity cards. */
    private int installedCapacityUpgrades = 0;

    public InterfaceUpgradeManager(IAEAppEngInventory host, boolean isExport, Callbacks callbacks) {
        this.callbacks = callbacks;
        this.isExport = isExport;
        this.upgradeInventory = new AppEngInternalInventory(host, UPGRADE_SLOTS, 1) {
            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return InterfaceUpgradeManager.this.isValidUpgrade(stack);
            }
        };
    }

    // ============================== Getters ==============================

    public AppEngInternalInventory getUpgradeInventory() {
        return this.upgradeInventory;
    }

    public boolean hasOverflowUpgrade() {
        return this.installedOverflowUpgrade;
    }

    public boolean hasTrashUnselectedUpgrade() {
        return this.installedTrashUnselectedUpgrade;
    }

    public boolean hasAutoPullPushUpgrade() {
        return this.installedAutoPushPullUpgrade;
    }

    public int getAutoPullPushInterval() {
        return this.autoPullPushInterval;
    }

    public int getAutoPushPullQuantity() {
        return this.autoPushPullQuantity;
    }

    public int getAutoPullPushKeepQuantity() {
        return this.autoPullPushKeepQuantity;
    }

    public int getInstalledCapacityUpgrades() {
        return this.installedCapacityUpgrades;
    }

    // ============================== Refresh ==============================

    /**
     * Refresh the status of installed upgrades.
     * Handles card lifecycle: when auto-pull/push card is inserted or removed,
     * updates the capability cache and re-registers the tick rate.
     */
    public void refreshUpgrades() {
        if (!this.isExport) {
            this.installedOverflowUpgrade = countUpgrade(ItemOverflowCard.class) > 0;
            this.installedTrashUnselectedUpgrade = countUpgrade(ItemTrashUnselectedCard.class) > 0;
        }

        boolean wasCardInstalled = this.installedAutoPushPullUpgrade;
        int oldCardInterval = this.autoPullPushInterval;

        this.installedAutoPushPullUpgrade = (countUpgrade(ItemAutoPullCard.class) > 0 ||
                countUpgrade(ItemAutoPushCard.class) > 0);

        if (this.installedAutoPushPullUpgrade) {
            // Read the values from the first found auto-pull/push card (there should only be 1)
            initAutoPullPushFromUpgrades();

            // Card just installed or settings changed, scan adjacent tiles and re-register tick rate
            if (!wasCardInstalled) {
                this.callbacks.onAutoPullPushInstalled();
                reRegisterTickRate();
            } else if (this.autoPullPushInterval != oldCardInterval) {
                // Card interval changed: re-register tick rate to match
                reRegisterTickRate();
            }
        } else if (wasCardInstalled) {
            // Card was removed: clear cache, reset timers, revert tick rate
            this.callbacks.onAutoPullPushRemoved();
            this.autoPullPushInterval = -1;
            this.autoPushPullQuantity = 0;
            this.autoPullPushKeepQuantity = 0;
            reRegisterTickRate();
        }

        int oldCapacityCount = this.installedCapacityUpgrades;
        this.installedCapacityUpgrades = countCapacityUpgrades();

        // Handle capacity card removal
        if (this.installedCapacityUpgrades < oldCapacityCount) {
            this.callbacks.onCapacityReduction(oldCapacityCount, this.installedCapacityUpgrades);
        }

        // Clamp current page to valid range
        this.callbacks.clampCurrentPage(this.installedCapacityUpgrades);
    }

    /**
     * Re-register with the AE2 tick manager to apply changed tick rate bounds.
     * Called when the card is installed, removed, or its interval changes.
     */
    private void reRegisterTickRate() {
        AENetworkProxy proxy = this.callbacks.getGridProxy();
        if (!proxy.isReady()) return;

        TickManagerHelper.reRegisterTickable(proxy.getNode(), this.callbacks.getTickable());

        // When switching to adaptive mode (card removed), wake up to prevent sleeping forever
        this.callbacks.wakeUpIfAdaptive();
    }

    // ============================== Counting helpers ==============================

    private int countUpgrade(Class<?> itemClass) {
        int count = 0;
        for (int i = 0; i < this.upgradeInventory.getSlots(); i++) {
            ItemStack existing = this.upgradeInventory.getStackInSlot(i);
            if (!existing.isEmpty() && itemClass.isInstance(existing.getItem())) count++;
        }

        return count;
    }

    private int countCapacityUpgrades() {
        int count = 0;
        for (int i = 0; i < this.upgradeInventory.getSlots(); i++) {
            ItemStack stack = this.upgradeInventory.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof IUpgradeModule)) continue;

            IUpgradeModule module = (IUpgradeModule) stack.getItem();
            if (module.getType(stack) == Upgrades.CAPACITY) count++;
        }

        return count;
    }

    private void initAutoPullPushFromUpgrades() {
        for (int i = 0; i < this.upgradeInventory.getSlots(); i++) {
            ItemStack stack = this.upgradeInventory.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            if (stack.getItem() instanceof ItemAutoPullCard || stack.getItem() instanceof ItemAutoPushCard) {
                if (stack.getItem() instanceof ItemAutoPullCard) {
                    this.autoPullPushInterval = ItemAutoPullCard.getInterval(stack);
                    this.autoPushPullQuantity = ItemAutoPullCard.getQuantity(stack);
                    this.autoPullPushKeepQuantity = ItemAutoPullCard.getKeepQuantity(stack);
                } else {
                    this.autoPullPushInterval = ItemAutoPushCard.getInterval(stack);
                    this.autoPushPullQuantity = ItemAutoPushCard.getQuantity(stack);
                    this.autoPullPushKeepQuantity = ItemAutoPushCard.getKeepQuantity(stack);
                }

                break;
            }
        }
    }

    // ============================== Validation ==============================

    /**
     * Check if an item is a valid upgrade for this interface.
     */
    public boolean isValidUpgrade(ItemStack stack) {
        if (stack.isEmpty()) return false;

        // Overflow and Trash unselected are import-only (make no sense for export)
        if (!this.isExport) {
            if (stack.getItem() instanceof ItemOverflowCard) {
                return countUpgrade(ItemOverflowCard.class) < 1;
            }

            if (stack.getItem() instanceof ItemTrashUnselectedCard) {
                return countUpgrade(ItemTrashUnselectedCard.class) < 1;
            }
        }

        if (stack.getItem() instanceof ItemAutoPullCard && !this.isExport) {
            return countUpgrade(ItemAutoPullCard.class) < 1;
        }

        if (stack.getItem() instanceof ItemAutoPushCard && this.isExport) {
            return countUpgrade(ItemAutoPushCard.class) < 1;
        }

        // Capacity card (both import and export)
        if (stack.getItem() instanceof IUpgradeModule) {
            IUpgradeModule module = (IUpgradeModule) stack.getItem();
            if (module.getType(stack) == Upgrades.CAPACITY) return true;
        }

        return false;
    }

    // ============================== Serialization helpers ==============================

    /**
     * Read upgrade inventory from NBT.
     */
    public void readFromNBT(NBTTagCompound data) {
        this.upgradeInventory.readFromNBT(data, "upgrades");
    }

    /**
     * Write upgrade inventory to NBT.
     */
    public void writeToNBT(NBTTagCompound data) {
        this.upgradeInventory.writeToNBT(data, "upgrades");
    }

    /**
     * Reset auto-pull/push timing state (e.g., on card removal).
     * Called by the adjacent handler or tick scheduler as needed.
     */
    public void resetAutoPullPushTimers() {
        this.autoPullPushInterval = -1;
        this.autoPushPullQuantity = 0;
        this.autoPullPushKeepQuantity = 0;
    }

    /**
     * Collect upgrade inventory contents as drops.
     */
    public void getUpgradeDrops(java.util.List<ItemStack> drops) {
        for (int i = 0; i < this.upgradeInventory.getSlots(); i++) {
            ItemStack stack = this.upgradeInventory.getStackInSlot(i);
            if (!stack.isEmpty()) drops.add(stack);
        }
    }
}
