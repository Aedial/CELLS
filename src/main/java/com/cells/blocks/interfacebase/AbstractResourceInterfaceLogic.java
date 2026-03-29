package com.cells.blocks.interfacebase;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.Actionable;
import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEStack;
import appeng.core.settings.TickRates;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.inv.IAEAppEngInventory;

import com.cells.config.CellsConfig;
import com.cells.items.ItemOverflowCard;
import com.cells.items.ItemTrashUnselectedCard;
import com.cells.items.ItemAutoPullCard;
import com.cells.items.ItemAutoPushCard;
import com.cells.util.TickManagerHelper;


/**
 * Abstract base class for resource interface logic (fluid, gas, item, essentia).
 * Contains all shared business logic for import/export interfaces.
 * <p>
 * Type parameters:
 * <ul>
 *   <li>{@code R} - The native resource stack type (FluidStack, GasStack, etc.)</li>
 *   <li>{@code AE} - The AE2 wrapped stack type (IAEFluidStack, IAEGasStack, etc.)</li>
 *   <li>{@code K} - The hashable key type for the resource (FluidStackKey, GasStackKey, etc.)</li>
 * </ul>
 * <p>
 * Subclasses must implement the type-specific operations for resource handling,
 * NBT serialization, and ME network interactions.
 */
public abstract class AbstractResourceInterfaceLogic<R, AE extends IAEStack<AE>, K>
        implements IResourceInterfaceLogic<AE, K> {

    /**
     * Callback interface that the host (tile or part) implements to provide
     * platform-specific operations to the logic delegate.
     */
    public interface Host extends IAEAppEngInventory {

        /** Get the AE2 grid proxy for network access. */
        AENetworkProxy getGridProxy();

        /** Get the action source for ME network operations. */
        IActionSource getActionSource();

        /** Whether this is an export interface (true) or import interface (false). */
        boolean isExport();

        /**
         * Mark this host as dirty and save its state.
         * Tile: markDirty(). Part: getHost().markForSave().
         */
        void markDirtyAndSave();

        /**
         * Mark this host for client update.
         * Tile: markForUpdate(). Part: getHost().markForUpdate().
         */
        void markForNetworkUpdate();

        /** Get the world this host is in (may be null during loading). */
        @Nullable
        World getHostWorld();

        /** Get the position of this host in the world. */
        BlockPos getHostPos();

        /** Get the IGridTickable to re-register with tick manager. */
        IGridTickable getTickable();
    }

    public static final int SLOTS_PER_PAGE = 36;
    public static final int MAX_CAPACITY_CARDS = 4;
    public static final int MAX_PAGES = 1 + MAX_CAPACITY_CARDS;
    public static final int FILTER_SLOTS = SLOTS_PER_PAGE * MAX_PAGES;
    public static final int STORAGE_SLOTS = SLOTS_PER_PAGE * MAX_PAGES;
    public static final int TOTAL_SLOTS = Math.min(FILTER_SLOTS, STORAGE_SLOTS);
    public static final int UPGRADE_SLOTS = 4;
    public static final int DEFAULT_MAX_SLOT_SIZE = 16000; // mB (16 buckets)
    public static final int MIN_MAX_SLOT_SIZE = 1;
    public static final int DEFAULT_POLLING_RATE = 0; // 0 = adaptive (AE2-managed tick rates)

    /**
     * Get the maximum allowed slot size from config.
     * @return The configured max slot size limit (defaults to Long.MAX_VALUE)
     */
    public static long getMaxMaxSlotSize() {
        return CellsConfig.interfaceMaxSlotSizeLimit;
    }

    /**
     * Get the minimum allowed polling rate from config.
     * @return The configured minimum polling rate (defaults to 0 for adaptive)
     */
    public static int getMinPollingRate() {
        return CellsConfig.interfaceMinPollingRate;
    }

    public static int getDefaultPollingRate() {
        int minRate = getMinPollingRate();
        return Math.max(minRate, DEFAULT_POLLING_RATE);
    }

    public long getDefaultMaxSlotSize() {
        return Math.min(DEFAULT_MAX_SLOT_SIZE, getMaxMaxSlotSize());
    }

    protected final Host host;

    /** Filter array - stores the filter resource for each slot. */
    protected final R[] filters;

    /**
     * Storage array - stores resource IDENTITY only (type + NBT, not amount).
     * The actual amounts are stored in the parallel {@link #amounts} array.
     * This separation allows amounts to exceed Integer.MAX_VALUE while still
     * using native resource types that have int-based amount fields.
     */
    protected final R[] storage;

    /**
     * Parallel amounts array - stores the actual amount for each storage slot.
     * This is separate from storage[] because native resource types (FluidStack,
     * GasStack, ItemStack) have int-based amount fields that cannot exceed ~2.1B.
     * By storing amounts separately as long, we can store up to ~9.2 quintillion.
     */
    protected final long[] amounts;

    /** Upgrade inventory (accepts only specific upgrade cards). */
    protected final AppEngInternalInventory upgradeInventory;

    /** Maximum amount per slot in whatever units this resource uses (e.g., mB for fluids). */
    protected long maxSlotSize;

    /** Polling rate in ticks (0 = adaptive). */
    protected int pollingRate = 0;

    /** Whether we are currently sleeping (not being ticked by AE2). */
    protected boolean isSleeping = false;

    /** Whether overflow upgrade is installed (import only). */
    protected boolean installedOverflowUpgrade = false;

    /** Whether trash unselected upgrade is installed (import only). */
    protected boolean installedTrashUnselectedUpgrade = false;

    /** Whether auto-pull/push upgrade is installed. */
    protected boolean installedAutoPushPullUpgrade = false;

    /** Tick interval for auto-pull/push operations, stored in the respective card's NBT. */
    protected int autoPullPushInterval = -1;

    /** Quantity for auto-pull/push operations, stored in the respective card's NBT. */
    protected int autoPushPullQuantity = 0;

    /** Number of installed capacity cards. */
    protected int installedCapacityUpgrades = 0;

    /** Current GUI page index (0-based). */
    protected int currentPage = 0;

    /** Mapping of filter resource keys to their corresponding slot index. */
    protected final Map<K, Integer> filterToSlotMap = new HashMap<>();

    /** Reverse mapping: slot index to filter key. */
    protected final Map<Integer, K> slotToFilterMap = new HashMap<>();

    /** List of slot indices that have filters, in slot order. */
    protected List<Integer> filterSlotList = new ArrayList<>();

    @SuppressWarnings("unchecked")
    protected AbstractResourceInterfaceLogic(Host host, Class<R> resourceClass) {
        this.host = host;
        this.maxSlotSize = getDefaultMaxSlotSize();

        // Create arrays for filters and storage
        this.filters = (R[]) Array.newInstance(resourceClass, FILTER_SLOTS);
        this.storage = (R[]) Array.newInstance(resourceClass, STORAGE_SLOTS);
        this.amounts = new long[STORAGE_SLOTS];

        // Create upgrade inventory with filtering for specific upgrade cards
        this.upgradeInventory = new AppEngInternalInventory(host, UPGRADE_SLOTS, 1) {
            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return AbstractResourceInterfaceLogic.this.isValidUpgrade(stack);
            }
        };

        refreshUpgrades();
        refreshFilterMap();
    }

    // TODO: Add capabilities caching for Auto-Pull/Push cards. This involves :
    // - Caching the adjacent IItemHandler/IFluidHandler/etc. gotten from the tile.
    // - Weak map of Facing -> Cached Tile Entity for the isInvalid checks.
    // - Weak map of Facing -> Cached Capability of the TE.
    // - Invalidate the cache when the adjacent tile changes (neighbor change event) or when the card is removed.
    //     -> If a neighbor changes, we need to check if it has a capability we can use and update the cache accordingly.
    // - null and tile.isInvalid checks before using the cached capability.
    // - update() only runs if we have a card installed and any cached capability is present.
    // - When the card is detected, we check all adjacent tiles.
    // - If we have no card, we can skip neighbor checks and capability caching entirely.
    //
    // What do we use for update()? AE2's tick manager with a custom tick rate based on the card's interval
    // seems decently in-line with what we already have (fixed polling rate).
    // But then, how do we differentiate between polling rate and push/pull rate?
    // It's still the same tickingRequest() method, and we could poll too often if the card interval is very low.
    // Could we make a fake grid node and compare it when we get the ticked to dispatch to the right logic?
    // State management is gonna be a hassle, I feel it.
    //
    // What we can do :
    // - Keep a timeSinceStart via tickingRequest's ticksSinceLastCall
    // - If timeSinceStart >= lastCardRun + cardInterval, perform push/pull operation
    // - If timeSinceStart >= lastNetworkIO + Polling Rate, perform network I/O operation
    // - Then you take the minimum of the two intervals to determine the ticking rate
    // PROBLEM: We can have BOTH Adaptive Polling Rate AND Fixed CardInterterval
    //          And that's a hassle because it becomes a game of Hot/Cold
    //          (we can still do it by relying on ticksSinceLastCall to guess the intervals)

    // ============================== Abstract methods ==============================

    /**
     * Create a hashable key for the given resource.
     * @return The key, or null if resource is invalid
     */
    @Nullable
    protected abstract K createKey(R resource);

    /**
     * Check if two keys match. Uses Object.equals which benefits from cached hashes.
     * Override if you need different comparison semantics.
     */
    protected boolean keysMatch(@Nullable K a, @Nullable K b) {
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    /**
     * Check if two resources match (same type, ignoring amount).
     * Default implementation uses key comparison for efficiency.
     * Override only if you need different comparison semantics.
     */
    protected boolean resourcesMatch(R a, R b) {
        if (a == null || b == null) return false;
        return keysMatch(createKey(a), createKey(b));
    }

    /**
     * Get the amount from an EXTERNAL resource stack.
     * Used when receiving resources from external APIs (IFluidHandler, etc.).
     * For internal storage amounts, use {@link #getSlotAmount(int)} instead.
     */
    protected abstract int getAmount(R resource);

    /**
     * Set the amount on a resource stack (for external API returns).
     * NOTE: This modifies the resource object directly.
     */
    protected abstract void setAmount(R resource, int amount);

    /**
     * Create a copy of a resource with the given amount.
     * Used for creating stacks to return via external APIs (int-based).
     */
    protected abstract R copyWithAmount(R resource, int amount);

    /**
     * Create an identity-only copy of a resource (amount=1).
     * Used for storing resource type/NBT in the storage array without amount.
     * Default implementation delegates to copyWithAmount(resource, 1).
     */
    protected R copyAsIdentity(R resource) {
        return copyWithAmount(resource, 1);
    }

    /**
     * Create a copy of a resource.
     */
    protected abstract R copy(R resource);

    /**
     * Get the display name of a resource for chat messages.
     */
    protected abstract String getLocalizedName(R resource);

    /**
     * Convert a native resource to an AE stack.
     */
    protected abstract AE toAEStack(R resource);

    /**
     * Convert an AE stack to a native resource.
     */
    protected abstract R fromAEStack(AE aeStack);

    /**
     * Get the AE stack size.
     */
    protected abstract long getAEStackSize(AE aeStack);

    /**
     * Write a resource to NBT.
     */
    protected abstract void writeResourceToNBT(R resource, NBTTagCompound tag);

    /**
     * Read a resource from NBT.
     */
    @Nullable
    protected abstract R readResourceFromNBT(NBTTagCompound tag);

    /**
     * Get the resource name string for stream serialization (e.g., fluid registry name).
     */
    protected abstract String getResourceName(R resource);

    /**
     * Look up a resource by name string.
     */
    @Nullable
    protected abstract R getResourceByName(String name, int amount);

    /**
     * Get the ME inventory for this resource type.
     */
    protected abstract IMEInventory<AE> getMEInventory(IStorageGrid storage);

    /**
     * Get the type name of the resource interface (e.g., "fluid", "item").
     */
    public abstract String getTypeName();

    /**
     * Create a recovery item for remainder resources that couldn't be stored.
     * Return ItemStack.EMPTY if no recovery item is available for this type.
     *
     * @param identity The resource identity (type + NBT, not amount)
     * @param amount The amount to store in the recovery item (supports long)
     * @return A recovery ItemStack, or ItemStack.EMPTY if not available
     */
    protected abstract ItemStack createRecoveryItem(R identity, long amount);

    // ============================== Slot amount helpers ==============================

    /**
     * Get the amount stored in a specific slot.
     * Uses the parallel amounts array for long precision.
     */
    public long getSlotAmount(int slot) {
        if (slot < 0 || slot >= STORAGE_SLOTS) return 0;
        return this.amounts[slot];
    }

    /**
     * Set the amount stored in a specific slot.
     * Uses the parallel amounts array for long precision.
     */
    protected void setSlotAmount(int slot, long amount) {
        if (slot < 0 || slot >= STORAGE_SLOTS) return;
        this.amounts[slot] = amount;
    }

    /**
     * Adjust the amount stored in a specific slot by a delta value.
     * Used by GUI containers for long-safe insertion/extraction operations.
     * <p>
     * If the resulting amount is <= 0, the slot is cleared (both identity and amount).
     * If the slot was empty (identity null) and delta > 0, this does nothing.
     * Use setResourceInSlot() to place items in empty slots.
     *
     * @param slot  The storage slot index
     * @param delta The amount to add (positive) or subtract (negative)
     * @return The actual amount added/removed (may be clamped to available space/contents)
     */
    public long adjustSlotAmount(int slot, long delta) {
        if (slot < 0 || slot >= STORAGE_SLOTS) return 0;

        R identity = this.storage[slot];

        // Can't adjust an empty slot (use setResourceInSlot instead)
        if (identity == null) return 0;

        long currentAmount = this.amounts[slot];
        long newAmount = currentAmount + delta;

        if (newAmount <= 0) {
            // Slot depleted - clear both identity and amount
            long removed = currentAmount;
            this.storage[slot] = null;
            this.amounts[slot] = 0;
            host.markDirtyAndSave();
            return -removed; // Return negative to indicate removal
        }

        // Clamp to max slot size
        long maxSize = getMaxSlotSize();
        if (newAmount > maxSize) {
            newAmount = maxSize;
        }

        long actualDelta = newAmount - currentAmount;
        this.amounts[slot] = newAmount;
        host.markDirtyAndSave();
        return actualDelta;
    }

    /**
     * Create a resource stack from the storage identity with the given amount.
     * Clamps the amount to int for external API compatibility.
     *
     * @param slot The storage slot containing the identity
     * @param amount The amount to apply (will be clamped to Integer.MAX_VALUE)
     * @return The resource with the clamped amount, or null if slot is empty
     */
    @Nullable
    protected R createStackFromSlot(int slot, long amount) {
        if (slot < 0 || slot >= STORAGE_SLOTS) return null;

        R identity = this.storage[slot];
        if (identity == null) return null;

        int clampedAmount = (int) Math.min(amount, Integer.MAX_VALUE);
        return copyWithAmount(identity, clampedAmount);
    }

    // ============================== Slot access ==============================

    public AppEngInternalInventory getUpgradeInventory() {
        return this.upgradeInventory;
    }

    public boolean isSlotEmpty(int slot) {
        if (slot < 0 || slot >= STORAGE_SLOTS) return true;
        return this.storage[slot] == null || this.amounts[slot] <= 0;
    }

    @Nullable
    public R getResourceInSlot(int slot) {
        if (slot < 0 || slot >= STORAGE_SLOTS) return null;

        R identity = this.storage[slot];
        if (identity == null || this.amounts[slot] <= 0) return null;

        // Create a stack with the current amount (clamped to int for external APIs)
        return createStackFromSlot(slot, this.amounts[slot]);
    }

    /**
     * Set the resource in a specific storage slot.
     * Used for GUI-based resource pouring.
     *
     * @param slot The storage slot index
     * @param resource The resource to set (with amount), or null to clear
     */
    public void setResourceInSlot(int slot, @Nullable R resource) {
        if (slot < 0 || slot >= STORAGE_SLOTS) return;

        if (resource == null) {
            this.storage[slot] = null;
            this.amounts[slot] = 0;
        } else {
            // Store identity and amount separately
            this.storage[slot] = copyAsIdentity(resource);
            this.amounts[slot] = getAmount(resource);
        }

        // Rebuild filter map since storage changed
        refreshFilterMap();

        // Trigger network update to sync storage to clients
        this.host.markForNetworkUpdate();

        // Wake up to process the new content (import will push to network)
        this.wakeUpIfAdaptive();
    }

    @Override
    @Nullable
    public AE getFilter(int slot) {
        if (slot < 0 || slot >= FILTER_SLOTS) return null;

        R filter = this.filters[slot];
        return filter != null ? toAEStack(filter) : null;
    }

    /**
     * Set the filter for a slot. Subclasses may override to add callbacks.
     */
    @Override
    public void setFilter(int slot, @Nullable AE aeResource) {
        if (slot < 0 || slot >= FILTER_SLOTS) return;

        this.filters[slot] = aeResource != null ? copyWithAmount(fromAEStack(aeResource), 1) : null;
        onFilterChanged(slot);
    }

    /**
     * Insert resource into a specific storage slot (import interface operation).
     * Uses the parallel amounts array for long precision internally.
     *
     * @return The amount actually inserted (clamped to int for external API compat)
     */
    public int insertIntoSlot(int slot, R resource) {
        if (slot < 0 || slot >= STORAGE_SLOTS) return 0;
        if (resource == null || getAmount(resource) <= 0) return 0;

        R identity = this.storage[slot];

        // TODO: Use keys with keysMatch instead of resourcesMatch for efficiency once we have cached keys in the filter map
        //       We have cached filters map, but not cached storage keys yet
        // If slot has resource, it must match
        if (identity != null && !resourcesMatch(identity, resource)) return 0;

        long currentAmount = this.amounts[slot];
        long spaceAvailable = this.maxSlotSize - currentAmount;
        if (spaceAvailable <= 0) return 0;

        // Input amount is int (from external APIs), but we store as long
        int inputAmount = getAmount(resource);
        long toInsert = Math.min(inputAmount, spaceAvailable);

        // Store identity only (amount=1), actual amount in parallel array
        if (identity == null) this.storage[slot] = copyAsIdentity(resource);
        this.amounts[slot] += toInsert;

        this.host.markDirtyAndSave();
        this.host.markForNetworkUpdate();
        this.wakeUpIfAdaptive();

        // Return int for external API compatibility (always fits since input was int)
        return (int) toInsert;
    }

    /**
     * Internal insert with long amount support.
     * Used for ME network operations that use long amounts.
     *
     * @return The amount actually inserted (as long)
     */
    protected long insertIntoSlotLong(int slot, R resource, long amount) {
        if (slot < 0 || slot >= STORAGE_SLOTS) return 0;
        if (resource == null || amount <= 0) return 0;

        R identity = this.storage[slot];

        // If slot has resource, it must match
        if (identity != null && !resourcesMatch(identity, resource)) return 0;

        long currentAmount = this.amounts[slot];
        long spaceAvailable = this.maxSlotSize - currentAmount;
        if (spaceAvailable <= 0) return 0;

        long toInsert = Math.min(amount, spaceAvailable);

        if (identity == null) this.storage[slot] = copyAsIdentity(resource);
        this.amounts[slot] += toInsert;

        this.host.markDirtyAndSave();
        this.host.markForNetworkUpdate();
        this.wakeUpIfAdaptive();

        return toInsert;
    }

    /**
     * Drain resource from a specific storage slot (export interface operation).
     * Uses the parallel amounts array for long precision internally.
     *
     * @param maxDrain Maximum amount to drain (int for external API compat)
     * @return The resource extracted (clamped to int), or null if nothing extracted
     */
    @Nullable
    public R drainFromSlot(int slot, int maxDrain, boolean doDrain) {
        return drainFromSlotLong(slot, maxDrain, doDrain);
    }

    /**
     * Internal drain with long amount support.
     * Used for ME network operations that use long amounts.
     *
     * @param maxDrain Maximum amount to drain (as long)
     * @return The resource extracted (clamped to int for external APIs), or null if nothing extracted
     */
    @Nullable
    protected R drainFromSlotLong(int slot, long maxDrain, boolean doDrain) {
        if (slot < 0 || slot >= STORAGE_SLOTS) return null;

        R identity = this.storage[slot];
        long currentAmount = this.amounts[slot];
        if (identity == null || currentAmount <= 0) return null;

        long toDrain = Math.min(maxDrain, currentAmount);

        // Clamp to int for external API return (create stack with int amount)
        int clampedDrain = (int) Math.min(toDrain, Integer.MAX_VALUE);
        R drained = copyWithAmount(identity, clampedDrain);

        if (doDrain) {
            this.amounts[slot] -= toDrain;
            if (this.amounts[slot] <= 0) {
                this.storage[slot] = null;
                this.amounts[slot] = 0;
            }

            this.host.markDirtyAndSave();
            this.host.markForNetworkUpdate();

            // Wake up to request more resources (export replenishes)
            this.wakeUpIfAdaptive();
        }

        return drained;
    }

    /**
     * Receive a resource into this interface based on filter configuration.
     * Used by import interface external handlers.
     * Uses the parallel amounts array for long precision internally.
     *
     * @param resource The resource to receive
     * @param doTransfer If true, actually perform the transfer; if false, simulate
     * @return The amount accepted (may include voided amounts if upgrades are installed)
     */
    public int receiveFiltered(R resource, boolean doTransfer) {
        if (resource == null || getAmount(resource) <= 0) return 0;

        K key = createKey(resource);
        if (key == null) return 0;

        int inputAmount = getAmount(resource);

        // No filter matches - void if trash unselected upgrade installed, reject otherwise
        Integer slot = this.filterToSlotMap.get(key);
        if (slot == null) return this.installedTrashUnselectedUpgrade ? inputAmount : 0;

        // Insert into matching slot using parallel amounts array
        long currentAmount = this.amounts[slot];
        long space = this.maxSlotSize - currentAmount;

        // Slot full - void overflow if upgrade installed, reject otherwise
        if (space <= 0) return this.installedOverflowUpgrade ? inputAmount : 0;

        // Input is int, but we can accept up to maxSlotSize (long)
        long toInsert = Math.min(inputAmount, space);

        if (doTransfer) {
            // Store identity only
            if (this.storage[slot] == null) this.storage[slot] = copyAsIdentity(resource);
            this.amounts[slot] += toInsert;

            this.host.markDirtyAndSave();
            this.host.markForNetworkUpdate();

            // Wake up to import resources
            this.wakeUpIfAdaptive();
        }

        // If overflow upgrade installed, accept all resource (void the excess)
        int excess = inputAmount - (int) toInsert;
        if (excess > 0 && this.installedOverflowUpgrade) return inputAmount;

        return (int) toInsert;
    }

    /**
     * Drain any available resource from this interface.
     * Used by export interface external handlers for untyped drain requests.
     *
     * @param maxDrain Maximum amount to drain
     * @param doDrain If true, actually drain; if false, simulate
     * @return The drained resource, or null if nothing available
     */
    @Nullable
    public R drainAny(int maxDrain, boolean doDrain) {
        // Drain from first non-empty slot
        for (int slot : this.filterSlotList) {
            if (this.storage[slot] != null && this.amounts[slot] > 0) {
                return drainFromSlot(slot, maxDrain, doDrain);
            }
        }

        return null;
    }

    /**
     * Drain a specific resource from this interface.
     * Used by export interface external handlers for typed drain requests.
     *
     * @param request The resource to drain (type and max amount)
     * @param doDrain If true, actually drain; if false, simulate
     * @return The drained resource, or null if not available
     */
    @Nullable
    public R drainSpecific(R request, boolean doDrain) {
        if (request == null || getAmount(request) <= 0) return null;

        K key = createKey(request);
        if (key == null) return null;

        Integer slot = this.filterToSlotMap.get(key);
        if (slot == null) return null;

        return drainFromSlot(slot, getAmount(request), doDrain);
    }

    /**
     * Check if this interface can receive the given resource type.
     * Used by import interface external handlers for capability queries.
     *
     * @param resource The resource to check (only type matters, not amount)
     * @return true if a filter exists for this resource type
     */
    public boolean canReceive(R resource) {
        if (resource == null) return false;

        K key = createKey(resource);
        return key != null && this.filterToSlotMap.containsKey(key);
    }

    /**
     * Check if this interface can drain the given resource type.
     * Used by export interface external handlers for capability queries.
     *
     * @param resource The resource to check (only type matters, not amount)
     * @return true if a filter exists for this resource type
     */
    public boolean canDrain(R resource) {
        // Same logic as canReceive - we can drain what we have filters for
        return canReceive(resource);
    }

    @Override
    public long getMaxSlotSize() {
        return this.maxSlotSize;
    }

    /**
     * Set the maximum tank capacity.
     * If export and reduced, returns overflow to the network.
     */
    @Override
    public void setMaxSlotSize(long size) {
        long oldSize = this.maxSlotSize;
        this.maxSlotSize = Math.max(MIN_MAX_SLOT_SIZE, Math.min(size, getMaxMaxSlotSize()));

        this.host.markDirtyAndSave();

        if (this.host.isExport()) {
            if (oldSize > this.maxSlotSize) returnOverflowToNetwork();
            if (oldSize < this.maxSlotSize) this.wakeUpIfAdaptive();
        }
    }

    @Override
    public int getPollingRate() {
        return this.pollingRate;
    }

    @Override
    public void setPollingRate(int ticks) {
        this.setPollingRate(ticks, null);
    }

    /**
     * Set the polling rate with optional player notification on failure.
     * @param ticks Polling rate in ticks (0 = adaptive, but clamped to config minimum)
     * @param player Player to notify if re-registration fails, or null to skip notification
     */
    public void setPollingRate(int ticks, EntityPlayer player) {
        // Enforce config minimum (0 means adaptive, but if config requires higher, enforce it)
        int minRate = getMinPollingRate();
        this.pollingRate = Math.max(minRate, ticks);
        this.host.markDirtyAndSave();

        // Re-register with the tick manager to apply the new TickingRequest bounds.
        AENetworkProxy proxy = this.host.getGridProxy();
        if (proxy.isReady()) {
            if (!TickManagerHelper.reRegisterTickable(proxy.getNode(), this.host.getTickable())) {
                if (player != null) {
                    player.sendMessage(new TextComponentTranslation("message.cells.polling_rate_delayed"));
                }
            }

            // When switching to adaptive mode, wake up the interface immediately
            // to prevent it from sleeping indefinitely waiting for external triggers.
            this.wakeUpIfAdaptive();
        }
    }

    /**
     * Refresh the status of installed upgrades.
     */
    @Override
    public void refreshUpgrades() {
        if (!this.host.isExport()) {
            this.installedOverflowUpgrade = countUpgrade(ItemOverflowCard.class) > 0;
            this.installedTrashUnselectedUpgrade = countUpgrade(ItemTrashUnselectedCard.class) > 0;
        }

        this.installedAutoPushPullUpgrade = (countUpgrade(ItemAutoPullCard.class) > 0 ||
                countUpgrade(ItemAutoPushCard.class) > 0);

        if (this.installedAutoPushPullUpgrade) {
            // Read the values from the first found auto-pull/push card (there should only be 1)
            initAutoPullPushFromUpgrades();
        }

        int oldCapacityCount = this.installedCapacityUpgrades;
        this.installedCapacityUpgrades = countCapacityUpgrades();

        // Handle capacity card removal
        if (this.installedCapacityUpgrades < oldCapacityCount) {
            handleCapacityReduction(oldCapacityCount, this.installedCapacityUpgrades);
        }

        // Clamp current page to valid range
        int maxPage = this.installedCapacityUpgrades;
        if (this.currentPage > maxPage) this.currentPage = maxPage;
    }

    protected int countUpgrade(Class<?> itemClass) {
        int count = 0;
        for (int i = 0; i < this.upgradeInventory.getSlots(); i++) {
            ItemStack existing = this.upgradeInventory.getStackInSlot(i);
            if (!existing.isEmpty() && itemClass.isInstance(existing.getItem())) count++;
        }

        return count;
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

    protected int initAutoPullPushFromUpgrades() {
        for (int i = 0; i < this.upgradeInventory.getSlots(); i++) {
            ItemStack stack = this.upgradeInventory.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            if (this.installedAutoPushPullUpgrade && ((stack.getItem() instanceof ItemAutoPullCard) ||
                (stack.getItem() instanceof ItemAutoPushCard))) {
                NBTTagCompound tag = stack.getTagCompound();
                if (tag != null && tag.hasKey("Interval", Constants.NBT.TAG_INT)) {
                    this.autoPullPushInterval = tag.getInteger("Interval");
                }
                if (tag != null && tag.hasKey("Quantity", Constants.NBT.TAG_INT)) {
                    this.autoPushPullQuantity = tag.getInteger("Quantity");
                }

                break;
            }
        }

        return -1; // Default interval if not found
    }

    protected int countCapacityUpgrades() {
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

    /**
     * Check if an item is a valid upgrade for this interface.
     */
    public boolean isValidUpgrade(ItemStack stack) {
        if (stack.isEmpty()) return false;

        // Overflow and Trash unselected are import-only (make no sense for export)
        if (!this.host.isExport()) {
            if (stack.getItem() instanceof ItemOverflowCard) {
                return countUpgrade(ItemOverflowCard.class) < 1;
            }

            if (stack.getItem() instanceof ItemTrashUnselectedCard) {
                return countUpgrade(ItemTrashUnselectedCard.class) < 1;
            }
        }

        if (stack.getItem() instanceof ItemAutoPullCard && !this.host.isExport()) {
            return countUpgrade(ItemAutoPullCard.class) < 1;
        }

        if (stack.getItem() instanceof ItemAutoPushCard && this.host.isExport()) {
            return countUpgrade(ItemAutoPushCard.class) < 1;
        }

        // Capacity card (both import and export)
        if (stack.getItem() instanceof IUpgradeModule) {
            IUpgradeModule module = (IUpgradeModule) stack.getItem();
            if (module.getType(stack) == Upgrades.CAPACITY) return true;
        }

        return false;
    }

    @Override
    public int getInstalledCapacityUpgrades() {
        return this.installedCapacityUpgrades;
    }

    @Override
    public int getTotalPages() {
        return 1 + this.installedCapacityUpgrades;
    }

    @Override
    public int getCurrentPage() {
        return this.currentPage;
    }

    @Override
    public void setCurrentPage(int page) {
        this.currentPage = Math.max(0, Math.min(page, this.installedCapacityUpgrades));
    }

    @Override
    public int getCurrentPageStartSlot() {
        return this.currentPage * SLOTS_PER_PAGE;
    }

    @Override
    public int getSlotsPerPage() {
        return SLOTS_PER_PAGE;
    }

    @Override
    public int getFilterSlots() {
        return FILTER_SLOTS;
    }

    /**
     * Handle capacity reduction by clearing filters and returning resources from removed pages.
     */
    protected void handleCapacityReduction(int oldCount, int newCount) {
        int newMaxSlot = (1 + newCount) * SLOTS_PER_PAGE;
        int oldMaxSlot = (1 + oldCount) * SLOTS_PER_PAGE;

        World world = this.host.getHostWorld();
        BlockPos pos = this.host.getHostPos();

        // Process slots that are being removed
        for (int slot = newMaxSlot; slot < oldMaxSlot && slot < STORAGE_SLOTS; slot++) {
            // Clear the filter
            this.filters[slot] = null;

            // Return resource to network
            R identity = this.storage[slot];
            long amount = this.amounts[slot];
            if (identity != null && amount > 0) {
                long notInserted = insertIntoNetworkLong(identity, amount);

                // Create a single recovery item for remainder (supports long amounts)
                if (notInserted > 0) {
                    ItemStack drop = createRecoveryItem(identity, notInserted);
                    if (!drop.isEmpty() && world != null && pos != null) {
                        Block.spawnAsEntity(world, pos, drop);
                    }
                    // If no recovery item available, remainder is voided
                    // This should never happen as we handle every type
                }

                this.storage[slot] = null;
                this.amounts[slot] = 0;
            }
        }

        this.refreshFilterMap();
        this.host.markDirtyAndSave();
    }

    /**
     * Refresh the filter to slot mapping.
     */
    @Override
    public void refreshFilterMap() {
        this.filterToSlotMap.clear();
        this.slotToFilterMap.clear();

        List<Integer> validSlots = new ArrayList<>();

        for (int i = 0; i < TOTAL_SLOTS; i++) {
            R filter = this.filters[i];
            if (filter == null) continue;

            K key = createKey(filter);
            if (key != null) {
                this.filterToSlotMap.put(key, i);
                this.slotToFilterMap.put(i, key);
                validSlots.add(i);
            }
        }

        this.filterSlotList = validSlots;
    }

    /**
     * Clear filter slots.
     * Import: only clears filters where the corresponding slot is empty.
     * Export: clears all filter slots.
     */
    @Override
    public void clearFilters() {
        if (this.host.isExport()) {
            for (int i = 0; i < FILTER_SLOTS; i++) this.filters[i] = null;
        } else {
            for (int i = 0; i < FILTER_SLOTS; i++) {
                // Only clear filter if the corresponding storage slot is empty
                if (i >= STORAGE_SLOTS || this.storage[i] == null || this.amounts[i] <= 0) {
                    this.filters[i] = null;
                }
            }
        }

        this.refreshFilterMap();
        this.host.markDirtyAndSave();
        this.host.markForNetworkUpdate();
    }

    /**
     * Find the first empty filter slot.
     * @return The slot index, or -1 if no empty slots available
     */
    public int findEmptyFilterSlot() {
        for (int i = 0; i < FILTER_SLOTS; i++) {
            if (this.filters[i] == null) return i;
        }

        return -1;
    }

    /**
     * Find the storage slot that matches the given resource.
     * @return The slot index, or -1 if no matching filter found
     */
    public int findSlotForResource(R resource) {
        if (resource == null) return -1;

        K key = createKey(resource);
        if (key == null) return -1;

        Integer slot = this.filterToSlotMap.get(key);
        return slot != null ? slot : -1;
    }

    // ============================== IFilterableInterfaceHost support ==============================

    /**
     * Check if a filter with the given key exists.
     * O(1) operation using the internal HashMap.
     *
     * @param key The key to check
     * @return true if a filter exists for this key
     */
    @Override
    public boolean isInFilter(@Nonnull K key) {
        return this.filterToSlotMap.containsKey(key);
    }

    /**
     * Check if a resource is in the filter.
     * Creates a key and delegates to isInFilter(K).
     *
     * @param resource The resource to check
     * @return true if a filter exists for this resource
     */
    public boolean isResourceInFilter(@Nullable R resource) {
        if (resource == null) return false;

        K key = createKey(resource);
        return key != null && isInFilter(key);
    }

    /**
     * Find the slot index for a given key.
     *
     * @param key The key to find
     * @return The slot index, or -1 if not found
     */
    @Override
    public int findSlotByKey(@Nonnull K key) {
        Integer slot = this.filterToSlotMap.get(key);
        return slot != null ? slot : -1;
    }

    /**
     * Get the effective number of filter slots based on installed capacity upgrades.
     *
     * @return Number of effective slots (SLOTS_PER_PAGE * totalPages)
     */
    public int getEffectiveFilterSlots() {
        return SLOTS_PER_PAGE * getTotalPages();
    }

    /**
     * Check if the storage at a specific slot is empty.
     *
     * @param slot The slot index
     * @return true if the storage slot is empty
     */
    @Override
    public boolean isStorageEmpty(int slot) {
        if (slot < 0 || slot >= STORAGE_SLOTS) return true;
        return this.storage[slot] == null || this.amounts[slot] <= 0;
    }

    /**
     * Add a resource to the first available filter slot.
     * Respects import/export rules (import won't add to slots with non-empty storage).
     * Does NOT check for duplicates - caller should check isInFilter first.
     *
     * @param resource The resource to add as a filter
     * @return The slot index where the filter was added, or -1 if no space available
     */
    public int addToFirstAvailableSlot(@Nonnull R resource) {
        final boolean isExport = this.host.isExport();
        final int effectiveSlots = getEffectiveFilterSlots();

        for (int i = 0; i < effectiveSlots; i++) {
            if (this.filters[i] != null) continue;

            // Import mode: only use slot if storage is also empty
            if (!isExport && !isStorageEmpty(i)) continue;

            // Set the filter
            this.filters[i] = copyWithAmount(resource, 1);
            onFilterChanged(i);
            return i;
        }

        return -1;
    }

    /**
     * Add an AE-wrapped resource to the first available filter slot.
     * Convenience wrapper that converts from AE type to native type.
     *
     * @param aeResource The AE-wrapped resource to add as a filter
     * @return The slot index where the filter was added, or -1 if no space available
     */
    @Override
    public int addToFirstAvailableSlotAE(@Nonnull AE aeResource) {
        R resource = fromAEStack(aeResource);
        return addToFirstAvailableSlot(resource);
    }

    /**
     * Collect stored resources that should be dropped (not upgrades).
     * Attempts to return stored resources to the ME network first.
     * Creates ItemRecoveryContainer items for any resources that couldn't be returned.
     */
    public void getStorageDrops(List<ItemStack> drops) {
        // Try to return all storage contents to the network
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            R identity = this.storage[i];
            long amount = this.amounts[i];
            if (identity == null || amount <= 0) continue;

            // Try to insert into network
            long notInserted = insertIntoNetworkLong(identity, amount);

            // Create a single recovery item for the remainder (supports long amounts)
            if (notInserted > 0) {
                ItemStack drop = createRecoveryItem(identity, notInserted);
                if (!drop.isEmpty()) drops.add(drop);
            }

            // Clear the slot regardless
            this.storage[i] = null;
            this.amounts[i] = 0;
        }
    }

    /**
     * Collect all items that should be dropped when this interface is broken normally.
     * Includes both stored resources and upgrades.
     */
    public void getDrops(List<ItemStack> drops) {
        getStorageDrops(drops);

        // Drop upgrades
        for (int i = 0; i < this.upgradeInventory.getSlots(); i++) {
            ItemStack stack = this.upgradeInventory.getStackInSlot(i);
            if (!stack.isEmpty()) drops.add(stack);
        }
    }

    /**
     * Get the NBT key for filters (e.g., "fluidFilters", "gasFilters").
     * Default: getTypeName() + "Filters".
     */
    protected String getFiltersNBTKey() {
        return getTypeName() + "Filters";
    }

    /**
     * Get the NBT key for storage (e.g., "fluidStorage", "gasStorage", "itemStorage").
     * Default: getTypeName() + "Storage".
     */
    protected String getStorageNBTKey() {
        return getTypeName() + "Storage";
    }

    protected void writeFiltersToNBT(NBTTagCompound data, String name) {
        NBTTagCompound filtersMap = new NBTTagCompound();
        for (int i = 0; i < FILTER_SLOTS; i++) {
            R filter = this.filters[i];
            if (filter == null) continue;

            NBTTagCompound filterTag = new NBTTagCompound();
            writeResourceToNBT(filter, filterTag);
            filtersMap.setTag("#" + i, filterTag);
        }
        data.setTag(name, filtersMap);
    }

    protected void readFiltersFromNBT(NBTTagCompound data, String name) {
        if (!data.hasKey(name, Constants.NBT.TAG_COMPOUND)) return;

        NBTTagCompound filtersMap = data.getCompoundTag(name);
        for (String key : filtersMap.getKeySet()) {
            if (!key.startsWith("#")) continue;

            try {
                int slot = Integer.parseInt(key.substring(1));
                if (slot < 0 || slot >= FILTER_SLOTS) continue;

                NBTTagCompound filterTag = filtersMap.getCompoundTag(key);
                if (filterTag.isEmpty()) continue;

                R filter = readResourceFromNBT(filterTag);
                if (filter != null) this.filters[slot] = filter;
            } catch (NumberFormatException ignored) {
            }
        }
    }

    /**
     * Read logic state from NBT. Call from host's readFromNBT.
     */
    public void readFromNBT(NBTTagCompound data) {
        readFiltersFromNBT(data, getFiltersNBTKey());
        readStorageFromNBT(data);
        this.upgradeInventory.readFromNBT(data, "upgrades");

        this.maxSlotSize = data.getLong("maxSlotSize");
        this.pollingRate = data.getInteger("pollingRate");

        this.maxSlotSize = Math.max(MIN_MAX_SLOT_SIZE, Math.min(this.maxSlotSize, getMaxMaxSlotSize()));
        // Enforce config minimum polling rate (0 = adaptive allowed only if config allows)
        int minRate = getMinPollingRate();
        this.pollingRate = Math.max(minRate, this.pollingRate);


        this.refreshFilterMap();
        this.refreshUpgrades();
    }

    /**
     * Read storage data from NBT. Override in subclass for type-specific migration.
     * Reads both identity and amounts from NBT, supporting long amounts.
     */
    protected void readStorageFromNBT(NBTTagCompound data) {
        String storageKey = getStorageNBTKey();
        if (!data.hasKey(storageKey, Constants.NBT.TAG_COMPOUND)) return;

        NBTTagCompound storageMap = data.getCompoundTag(storageKey);
        for (String key : storageMap.getKeySet()) {
            try {
                int slot = Integer.parseInt(key);
                if (slot < 0 || slot >= STORAGE_SLOTS) continue;

                NBTTagCompound slotTag = storageMap.getCompoundTag(key);
                R resource = readResourceFromNBT(slotTag);
                if (resource == null) continue;

                // Store identity only
                this.storage[slot] = copyAsIdentity(resource);

                // Read amount: prefer "Amount" (long), fall back to resource's native int amount
                if (slotTag.hasKey("Amount")) {
                    this.amounts[slot] = slotTag.getLong("Amount");
                } else {
                    // Legacy migration: use the resource's native int amount
                    this.amounts[slot] = getAmount(resource);
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    /**
     * Write logic state to NBT. Call from host's writeToNBT.
     */
    public void writeToNBT(NBTTagCompound data) {
        writeFiltersToNBT(data, getFiltersNBTKey());
        this.upgradeInventory.writeToNBT(data, "upgrades");
        data.setLong("maxSlotSize", this.maxSlotSize);
        data.setInteger("pollingRate", this.pollingRate);

        // Write storage - compound map format with parallel amounts
        NBTTagCompound storageMap = new NBTTagCompound();
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            if (this.storage[i] == null || this.amounts[i] <= 0) continue;

            NBTTagCompound slotTag = new NBTTagCompound();
            writeResourceToNBT(this.storage[i], slotTag);
            // Write amount as long for full long precision
            slotTag.setLong("Amount", this.amounts[i]);
            storageMap.setTag(String.valueOf(i), slotTag);
        }
        data.setTag(getStorageNBTKey(), storageMap);
    }

    // ============================== Stream serialization ==============================

    /**
     * Read storage data from a ByteBuf stream for client sync.
     * @return true if any data changed
     */
    public boolean readStorageFromStream(ByteBuf data) {
        boolean changed = false;

        // Clear all storage first
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            if (this.storage[i] != null || this.amounts[i] != 0) {
                this.storage[i] = null;
                this.amounts[i] = 0;
                changed = true;
            }
        }

        int count = data.readShort();
        for (int idx = 0; idx < count; idx++) {
            int slot = data.readShort();
            long amount = data.readLong();
            NBTTagCompound tag = ByteBufUtils.readTag(data);

            if (slot < 0 || slot >= STORAGE_SLOTS) continue;
            if (tag == null) continue;

            R resource = readResourceFromNBT(tag);
            if (resource != null) {
                this.storage[slot] = copyAsIdentity(resource);
                this.amounts[slot] = amount;
                changed = true;
            }
        }

        return changed;
    }

    /**
     * Write storage data to a ByteBuf stream for client sync.
     */
    public void writeStorageToStream(ByteBuf data) {
        // Count non-empty storage slots first
        int count = 0;
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            if (this.storage[i] != null && this.amounts[i] > 0) count++;
        }

        data.writeShort(count);

        for (int i = 0; i < STORAGE_SLOTS; i++) {
            R identity = this.storage[i];
            long amount = this.amounts[i];
            if (identity == null || amount <= 0) continue;

            data.writeShort(i);
            data.writeLong(amount);

            NBTTagCompound tag = new NBTTagCompound();
            writeResourceToNBT(identity, tag);
            ByteBufUtils.writeTag(data, tag);
        }
    }

    /**
     * Read filter data from a ByteBuf stream for client sync.
     * @return true if any data changed
     */
    public boolean readFiltersFromStream(ByteBuf data) {
        boolean changed = false;

        // Clear all filters first
        for (int i = 0; i < FILTER_SLOTS; i++) {
            if (this.filters[i] != null) {
                this.filters[i] = null;
                changed = true;
            }
        }

        int count = data.readShort();
        for (int idx = 0; idx < count; idx++) {
            int slot = data.readShort();
            NBTTagCompound tag = ByteBufUtils.readTag(data);

            if (slot < 0 || slot >= FILTER_SLOTS) continue;
            if (tag == null) continue;

            R resource = readResourceFromNBT(tag);
            if (resource != null) {
                this.filters[slot] = resource;
                changed = true;
            }
        }

        this.refreshFilterMap();
        return changed;
    }

    /**
     * Write filter data to a ByteBuf stream for client sync.
     */
    public void writeFiltersToStream(ByteBuf data) {
        // Count non-empty filters first
        int count = 0;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            if (this.filters[i] != null) count++;
        }

        data.writeShort(count);

        for (int i = 0; i < FILTER_SLOTS; i++) {
            R filter = this.filters[i];
            if (filter == null) continue;

            data.writeShort(i);

            NBTTagCompound tag = new NBTTagCompound();
            writeResourceToNBT(filter, tag);
            ByteBufUtils.writeTag(data, tag);
        }
    }

    /**
     * Download settings to NBT for memory cards.
     */
    public NBTTagCompound downloadSettings() {
        NBTTagCompound output = new NBTTagCompound();

        output.setLong("maxSlotSize", this.maxSlotSize);
        output.setInteger("pollingRate", this.pollingRate);

        return output;
    }

    /**
     * Download settings with filters for memory card + keybind.
     */
    public NBTTagCompound downloadSettingsWithFilter() {
        NBTTagCompound output = downloadSettings();
        writeFiltersToNBT(output, getFiltersNBTKey());

        return output;
    }

    /**
     * Download settings with filters AND upgrades for disassembly.
     */
    public NBTTagCompound downloadSettingsForDismantle() {
        NBTTagCompound output = downloadSettingsWithFilter();
        this.upgradeInventory.writeToNBT(output, "upgrades");

        return output;
    }

    /**
     * Upload settings from NBT (memory card or dismantle).
     */
    public void uploadSettings(NBTTagCompound compound, EntityPlayer player) {
        if (compound == null) return;

        if (compound.hasKey("maxSlotSize")) {
            this.setMaxSlotSize(compound.getLong("maxSlotSize"));
        }
        if (compound.hasKey("pollingRate")) {
            this.setPollingRate(compound.getInteger("pollingRate"), player);
        }

        // Merge upgrades FIRST (capacity cards enable extra pages for filters)
        if (compound.hasKey("upgrades")) {
            mergeUpgradesFromNBT(compound, "upgrades");
        }

        // Merge filter inventory from memory card instead of replacing
        if (compound.hasKey(getFiltersNBTKey())) {
            mergeFiltersFromNBT(compound, getFiltersNBTKey(), player);
        }
    }

    protected void mergeUpgradesFromNBT(NBTTagCompound data, String name) {
        if (!data.hasKey(name)) return;

        AppEngInternalInventory sourceUpgrades = new AppEngInternalInventory(null, UPGRADE_SLOTS, 1);
        sourceUpgrades.readFromNBT(data, name);

        for (int i = 0; i < sourceUpgrades.getSlots(); i++) {
            ItemStack sourceUpgrade = sourceUpgrades.getStackInSlot(i);
            if (sourceUpgrade.isEmpty()) continue;

            int targetSlot = -1;
            for (int j = 0; j < this.upgradeInventory.getSlots(); j++) {
                if (this.upgradeInventory.getStackInSlot(j).isEmpty()) {
                    targetSlot = j;
                    break;
                }
            }

            if (targetSlot >= 0) {
                this.upgradeInventory.setStackInSlot(targetSlot, sourceUpgrade.copy());
            }
        }

        this.refreshUpgrades();
    }

    protected void mergeFiltersFromNBT(NBTTagCompound data, String name, @Nullable EntityPlayer player) {
        if (!data.hasKey(name)) return;

        // Read source filters into temporary array
        R[] sourceFilters = createFilterArray();
        NBTTagCompound filtersMap = data.getCompoundTag(name);
        for (String key : filtersMap.getKeySet()) {
            if (!key.startsWith("#")) continue;

            try {
                int slot = Integer.parseInt(key.substring(1));
                if (slot < 0 || slot >= FILTER_SLOTS) continue;

                NBTTagCompound filterTag = filtersMap.getCompoundTag(key);
                if (filterTag.isEmpty()) continue;

                sourceFilters[slot] = readResourceFromNBT(filterTag);
            } catch (NumberFormatException ignored) {
            }
        }

        List<R> skippedFilters = new ArrayList<>();

        for (int i = 0; i < FILTER_SLOTS; i++) {
            R sourceFilter = sourceFilters[i];
            if (sourceFilter == null) continue;

            K sourceKey = createKey(sourceFilter);
            if (sourceKey == null) continue;

            // Skip if filter already exists in target
            if (this.filterToSlotMap.containsKey(sourceKey)) continue;

            // Find an empty slot
            int targetSlot = findEmptyFilterSlot();
            if (targetSlot < 0) {
                skippedFilters.add(copy(sourceFilter));
                continue;
            }

            // Add the filter
            this.filters[targetSlot] = copy(sourceFilter);
            this.filterToSlotMap.put(sourceKey, targetSlot);
            this.slotToFilterMap.put(targetSlot, sourceKey);
        }

        this.refreshFilterMap();

        // Notify player about skipped filters
        if (player != null && !skippedFilters.isEmpty()) {
            String filters = skippedFilters.stream()
                .map(this::getLocalizedName)
                .reduce((a, b) -> a + "\n- " + b)
                .orElse("");
            player.sendMessage(new TextComponentTranslation("message.cells.filters_not_added", skippedFilters.size(), filters));
        }
    }

    @SuppressWarnings("unchecked")
    protected R[] createFilterArray() {
        return (R[]) Array.newInstance(filters.getClass().getComponentType(), FILTER_SLOTS);
    }

    // ============================== Inventory change handling ==============================

    /**
     * Handle filter changes. Call from host or subclass.
     */
    public void onFilterChanged(int slot) {
        if (this.host.isExport()) {
            // Export: if filter changed, return orphaned resources in that slot
            R identity = this.storage[slot];
            if (identity != null && this.amounts[slot] > 0) {
                // Use cached filter key from slotToFilterMap for efficiency
                K cachedFilterKey = this.slotToFilterMap.get(slot);
                boolean isOrphaned = cachedFilterKey == null || !keysMatch(cachedFilterKey, createKey(identity));

                if (isOrphaned) returnSlotToNetwork(slot);
            }
        }

        this.refreshFilterMap();
        this.wakeUpIfAdaptive();
        this.host.markDirtyAndSave();
    }

    /**
     * Handle inventory changes. Call from host's onChangeInventory.
     * Only handles upgrade inventory changes (filter/storage use onFilterChanged).
     *
     * @param inv The changed inventory
     * @param slot The changed slot
     * @param removed The removed stack (unused but kept for signature compatibility)
     * @param added The added stack (unused but kept for signature compatibility)
     */
    public void onChangeInventory(IItemHandler inv, int slot, ItemStack removed, ItemStack added) {
        if (inv == this.upgradeInventory) onUpgradeChanged();
    }

    /**
     * Handle upgrade inventory changes.
     */
    public void onUpgradeChanged() {
        this.refreshUpgrades();
        this.host.markDirtyAndSave();
    }

    // ============================== Tick handling ==============================

    /**
     * Create a TickingRequest based on current configuration.
     * Also initializes the isSleeping state to match the request.
     */
    public TickingRequest getTickingRequest() {
        if (this.pollingRate > 0) {
            this.isSleeping = false;

            return new TickingRequest(
                this.pollingRate,
                this.pollingRate,
                false,
                true
            );
        }

        this.isSleeping = !hasWorkToDo();

        return new TickingRequest(
            TickRates.Interface.getMin(),
            TickRates.Interface.getMax(),
            this.isSleeping,
            true
        );
    }

    /**
     * Handle a tick. Returns the appropriate rate modulation.
     */
    public TickRateModulation onTick() {
        if (!this.host.getGridProxy().isActive()) {
            this.isSleeping = true;
            return TickRateModulation.SLEEP;
        }

        boolean didWork = this.host.isExport() ? exportResources() : importResources();

        if (this.pollingRate > 0) return TickRateModulation.SAME;
        if (didWork) return TickRateModulation.FASTER;

        boolean shouldSleep = !hasWorkToDo();
        this.isSleeping = shouldSleep;

        return shouldSleep ? TickRateModulation.SLEEP : TickRateModulation.SLOWER;
    }

    /**
     * Check if there's work to do based on direction.
     */
    public boolean hasWorkToDo() {
        if (this.host.isExport()) {
            // Check if any configured slot needs resources
            for (int i : this.filterSlotList) {
                if (this.amounts[i] < this.maxSlotSize) return true;
            }
        } else {
            // Check if any filtered slot has resources to import
            for (int i : this.filterToSlotMap.values()) {
                if (this.storage[i] != null && this.amounts[i] > 0) return true;
            }
        }

        return false;
    }

    /**
     * Wake up the tick manager if sleeping and using adaptive polling.
     * Only calls alertDevice() when actually sleeping - tick modulation handles the rest.
     */
    public void wakeUpIfAdaptive() {
        if (this.pollingRate > 0) return;
        if (!this.isSleeping) return;

        try {
            this.host.getGridProxy().getTick().alertDevice(this.host.getGridProxy().getNode());
            this.isSleeping = false;
        } catch (GridAccessException e) {
            // Not connected to grid
        }
    }

    /**
     * Get the maximum request size for the AE network.
     * This is used to clamp requests to avoid issues with some mods
     * that do not handle large request sizes correctly.
     * @return The maximum request size
     */
    protected long getMaxAENetworkRequestSize() {
        return Long.MAX_VALUE;
    }

    /**
     * Import resources from internal storage into the ME network.
     * Uses the parallel amounts array for long precision.
     */
    protected boolean importResources() {
        boolean didWork = false;

        try {
            IStorageGrid storageGrid = this.host.getGridProxy().getStorage();
            IMEInventory<AE> inventory = getMEInventory(storageGrid);

            for (int slot : this.filterToSlotMap.values()) {
                R identity = this.storage[slot];
                long amount = this.amounts[slot];
                if (identity == null || amount <= 0) continue;

                // Create AE stack with identity only - setStackSize will set the actual amount
                AE aeStack = toAEStack(copyWithAmount(identity, 1));
                aeStack.setStackSize(Math.min(amount, getMaxAENetworkRequestSize()));

                AE remaining = inventory.injectItems(aeStack, Actionable.MODULATE, this.host.getActionSource());

                if (remaining == null) {
                    this.storage[slot] = null;
                    this.amounts[slot] = 0;
                    didWork = true;
                } else if (getAEStackSize(remaining) < amount) {
                    this.amounts[slot] = getAEStackSize(remaining);
                    didWork = true;
                }
            }
        } catch (GridAccessException e) {
            // Not connected to grid
        }

        if (didWork) this.host.markForNetworkUpdate();

        return didWork;
    }

    /**
     * Export resources from the ME network into storage slots.
     * Uses the parallel amounts array for long precision.
     */
    protected boolean exportResources() {
        boolean didWork = false;

        try {
            IStorageGrid storageGrid = this.host.getGridProxy().getStorage();
            IMEInventory<AE> inventory = getMEInventory(storageGrid);

            // First, return any orphaned or overflow resources to the network
            returnOrphanedToNetwork();
            returnOverflowToNetwork();

            for (int slot : this.filterSlotList) {
                R filter = this.filters[slot];
                if (filter == null) continue;

                R identity = this.storage[slot];
                long currentAmount = this.amounts[slot];

                // Skip slots where current resources don't match filter (still orphaned)
                // Use cached filter key from slotToFilterMap for efficiency
                if (identity != null && currentAmount > 0) {
                    K cachedFilterKey = this.slotToFilterMap.get(slot);
                    if (cachedFilterKey == null || !keysMatch(cachedFilterKey, createKey(identity))) continue;
                }

                long space = this.maxSlotSize - currentAmount;
                if (space <= 0) continue;

                // Request resources from network (AE2 uses long natively)
                AE request = toAEStack(copyWithAmount(filter, 1));
                request.setStackSize(Math.min(space, getMaxAENetworkRequestSize()));

                AE extracted = inventory.extractItems(request, Actionable.MODULATE, this.host.getActionSource());
                if (extracted == null || getAEStackSize(extracted) <= 0) continue;

                long extractedAmount = getAEStackSize(extracted);

                // Add to storage
                if (identity == null) {
                    this.storage[slot] = copyAsIdentity(fromAEStack(extracted));
                }
                this.amounts[slot] += extractedAmount;

                didWork = true;
            }
        } catch (GridAccessException e) {
            // Not connected to grid
        }

        if (didWork) this.host.markForNetworkUpdate();

        return didWork;
    }

    // ============================== Network operations ==============================

    /**
     * Insert resources into the ME network.
     * @return Amount that could not be inserted (int for external API compat)
     */
    protected int insertIntoNetwork(R resource) {
        if (resource == null || getAmount(resource) <= 0) return 0;
        return (int) insertIntoNetworkLong(resource, getAmount(resource));
    }

    /**
     * Insert resources into the ME network with long amount support.
     *
     * @param identity The resource identity (type/NBT)
     * @param amount The amount to insert (long)
     * @return Amount that could not be inserted (long)
     */
    protected long insertIntoNetworkLong(R identity, long amount) {
        if (identity == null || amount <= 0) return 0;

        try {
            IStorageGrid storage = this.host.getGridProxy().getStorage();
            IMEInventory<AE> inventory = getMEInventory(storage);

            // Create AE stack with full long amount
            AE aeStack = toAEStack(copyWithAmount(identity, 1));
            aeStack.setStackSize(amount);

            AE remaining = inventory.injectItems(aeStack, Actionable.MODULATE, this.host.getActionSource());

            if (remaining == null) return 0;

            return getAEStackSize(remaining);
        } catch (GridAccessException e) {
            return amount;
        }
    }

    /**
     * Return a slot's contents to the network.
     */
    protected void returnSlotToNetwork(int slot) {
        if (slot < 0 || slot >= STORAGE_SLOTS) return;

        R identity = this.storage[slot];
        long amount = this.amounts[slot];
        if (identity == null || amount <= 0) return;

        long notInserted = insertIntoNetworkLong(identity, amount);

        if (notInserted <= 0) {
            this.storage[slot] = null;
            this.amounts[slot] = 0;
        } else {
            this.amounts[slot] = notInserted;
        }

        this.host.markForNetworkUpdate();
    }

    /**
     * Return orphaned resources (no matching filter) to the network.
     */
    protected void returnOrphanedToNetwork() {
        for (int slot = 0; slot < STORAGE_SLOTS; slot++) {
            R identity = this.storage[slot];
            if (identity == null || this.amounts[slot] <= 0) continue;

            // Use cached filter key from slotToFilterMap for efficiency
            K cachedFilterKey = this.slotToFilterMap.get(slot);
            if (cachedFilterKey != null && keysMatch(cachedFilterKey, createKey(identity))) continue;

            // Orphaned - return to network
            returnSlotToNetwork(slot);
        }
    }

    /**
     * Return overflow resources (above maxSlotSize) to the network.
     */
    protected void returnOverflowToNetwork() {
        for (int slot = 0; slot < STORAGE_SLOTS; slot++) {
            R identity = this.storage[slot];
            if (identity == null) continue;

            long amount = this.amounts[slot];
            if (amount <= this.maxSlotSize) continue;

            // Has overflow - return excess to network
            long overflow = amount - this.maxSlotSize;
            long notInserted = insertIntoNetworkLong(identity, overflow);

            // Reduce slot by what was successfully returned
            long returned = overflow - notInserted;
            if (returned > 0) {
                this.amounts[slot] -= returned;
                this.host.markForNetworkUpdate();
            }
        }
    }
}
