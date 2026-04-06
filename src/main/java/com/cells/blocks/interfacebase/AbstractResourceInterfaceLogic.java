package com.cells.blocks.interfacebase;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.items.IItemHandler;

import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEStack;
import appeng.me.helpers.AENetworkProxy;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.inv.IAEAppEngInventory;

import com.cells.blocks.interfacebase.managers.InterfaceAdjacentHandler;
import com.cells.blocks.interfacebase.managers.InterfaceInventoryManager;
import com.cells.blocks.interfacebase.managers.InterfaceTickScheduler;
import com.cells.blocks.interfacebase.managers.InterfaceUpgradeManager;



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
         * Mark this host as dirty and save its state (markChunkDirty).
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
    public static final int DEFAULT_MAX_SLOT_SIZE = 16000; // mB (16 buckets)

    public long getDefaultMaxSlotSize() {
        return DEFAULT_MAX_SLOT_SIZE;
    }

    protected final Host host;

    /** Filter array - stores the filter resource for each slot. */
    private final R[] filters;

    /**
     * Storage array - stores resource IDENTITY only (type + NBT, not amount).
     * The actual amounts are stored in the parallel {@link #amounts} array.
     * This separation allows amounts to exceed Integer.MAX_VALUE while still
     * using native resource types that have int-based amount fields.
     */
    private final R[] storage;

    /**
     * Parallel amounts array - stores the actual amount for each storage slot.
     * This is separate from storage[] because native resource types (FluidStack,
     * GasStack, ItemStack) have int-based amount fields that cannot exceed ~2.1B.
     * By storing amounts separately as long, we can store up to ~9.2 quintillion.
     */
    private final long[] amounts;

    /** Current GUI page index (0-based). */
    protected int currentPage = 0;

    // ============================== Managers ==============================
    // These managers own the business logic and their respective private state.
    // The logic class orchestrates them and provides callback implementations.

    /** Manages upgrade cards (overflow, trash unselected, auto-pull/push, capacity). */
    protected final InterfaceUpgradeManager upgradeManager;

    /** Manages combined filter/storage operations, mappings, network I/O, and serialization. */
    protected final InterfaceInventoryManager<R, AE, K> inventoryManager;

    /** Manages tick scheduling, polling rate, and sleep/wake state. */
    protected final InterfaceTickScheduler tickScheduler;

    /** Manages capability cache and auto-pull/push with adjacent blocks. */
    protected final InterfaceAdjacentHandler<R, K> adjacentHandler;

    @SuppressWarnings("unchecked")
    protected AbstractResourceInterfaceLogic(Host host, Class<R> resourceClass) {
        this.host = host;

        // Create arrays for filters and storage
        this.filters = (R[]) Array.newInstance(resourceClass, FILTER_SLOTS);
        this.storage = (R[]) Array.newInstance(resourceClass, STORAGE_SLOTS);
        this.amounts = new long[STORAGE_SLOTS];

        // Initialize managers (order matters: inventory before adjacent, which needs it)
        this.upgradeManager = new InterfaceUpgradeManager(host, host.isExport(), createUpgradeCallbacks());

        this.inventoryManager = new InterfaceInventoryManager<>(createInventoryResourceOps(), createInventoryCallbacks(),
                this.filters, this.storage, this.amounts, this.getDefaultMaxSlotSize());

        this.tickScheduler = new InterfaceTickScheduler(createTickCallbacks());

        this.adjacentHandler = createAdjacentHandler(
                createAdjacentResourceOps(), createAdjacentCallbacks(), this.inventoryManager);

        refreshUpgrades();
        refreshFilterMap();
    }

    // ============================== Manager callback factories ==============================
    // These methods create anonymous callback implementations that bridge manager interfaces
    // to the logic's own methods and host. Defined as factory methods to keep the constructor clean.

    private InterfaceUpgradeManager.Callbacks createUpgradeCallbacks() {
        return new InterfaceUpgradeManager.Callbacks() {
            @Override public AENetworkProxy getGridProxy() { return host.getGridProxy(); }
            @Override public IGridTickable getTickable() { return host.getTickable(); }
            @Override public void onCapacityReduction(int oldCount, int newCount) { inventoryManager.handleCapacityReduction(oldCount, newCount); }
            @Override public void onAutoPullPushInstalled() { refreshCapabilityCache(); }
            @Override public void onAutoPullPushRemoved() { clearCapabilityCache(); }
            @Override public void wakeUpIfAdaptive() { AbstractResourceInterfaceLogic.this.wakeUpIfAdaptive(); }
            @Override public void clampCurrentPage(int maxPage) { if (currentPage > maxPage) currentPage = maxPage; }
        };
    }

    private InterfaceInventoryManager.ResourceOps<R, AE, K> createInventoryResourceOps() {
        return new InterfaceInventoryManager.ResourceOps<R, AE, K>() {
            @Override public K createKey(R resource) { return AbstractResourceInterfaceLogic.this.createKey(resource); }
            @Override public int getAmount(R resource) { return AbstractResourceInterfaceLogic.this.getAmount(resource); }
            @Override public R copyWithAmount(R resource, int amount) { return AbstractResourceInterfaceLogic.this.copyWithAmount(resource, amount); }
            @Override public R copyAsIdentity(R resource) { return AbstractResourceInterfaceLogic.this.copyAsIdentity(resource); }
            @Override public R copy(R resource) { return AbstractResourceInterfaceLogic.this.copy(resource); }
            @Override public String getLocalizedName(R resource) { return AbstractResourceInterfaceLogic.this.getLocalizedName(resource); }
            @Override public AE toAEStack(R resource) { return AbstractResourceInterfaceLogic.this.toAEStack(resource); }
            @Override public R fromAEStack(AE aeStack) { return AbstractResourceInterfaceLogic.this.fromAEStack(aeStack); }
            @Override public long getAEStackSize(AE aeStack) { return AbstractResourceInterfaceLogic.this.getAEStackSize(aeStack); }
            @Override public IMEInventory<AE> getMEInventory(IStorageGrid storage) { return AbstractResourceInterfaceLogic.this.getMEInventory(storage); }
            @Override public void writeResourceToNBT(R resource, NBTTagCompound tag) { AbstractResourceInterfaceLogic.this.writeResourceToNBT(resource, tag); }
            @Override public R readResourceFromNBT(NBTTagCompound tag) { return AbstractResourceInterfaceLogic.this.readResourceFromNBT(tag); }
            @Override public void writeResourceToStream(R resource, ByteBuf data) { AbstractResourceInterfaceLogic.this.writeResourceToStream(resource, data); }
            @Override public R readResourceFromStream(ByteBuf data) { return AbstractResourceInterfaceLogic.this.readResourceFromStream(data); }
            @Override public ItemStack createRecoveryItem(R identity, long amount) { return AbstractResourceInterfaceLogic.this.createRecoveryItem(identity, amount); }
        };
    }

    private InterfaceInventoryManager.Callbacks createInventoryCallbacks() {
        return new InterfaceInventoryManager.Callbacks() {
            @Override public boolean isExport() { return host.isExport(); }
            @Override public void markDirtyAndSave() { host.markDirtyAndSave(); }
            @Override public void markForNetworkUpdate() { host.markForNetworkUpdate(); }
            @Override public void wakeUpIfAdaptive() { AbstractResourceInterfaceLogic.this.wakeUpIfAdaptive(); }
            @Override public int getEffectiveFilterSlots() { return AbstractResourceInterfaceLogic.this.getEffectiveFilterSlots(); }
            @Override public AENetworkProxy getGridProxy() { return host.getGridProxy(); }
            @Override public IActionSource getActionSource() { return host.getActionSource(); }
            @Override public long getMaxAENetworkRequestSize() { return AbstractResourceInterfaceLogic.this.getMaxAENetworkRequestSize(); }
            @Override public World getHostWorld() { return host.getHostWorld(); }
            @Override public BlockPos getHostPos() { return host.getHostPos(); }
        };
    }

    private InterfaceTickScheduler.Callbacks createTickCallbacks() {
        return new InterfaceTickScheduler.Callbacks() {
            @Override public AENetworkProxy getGridProxy() { return host.getGridProxy(); }
            @Override public IGridTickable getTickable() { return host.getTickable(); }
            @Override public void markDirtyAndSave() { host.markDirtyAndSave(); }
            @Override public boolean performNetworkIO() { return host.isExport() ? inventoryManager.exportResources() : inventoryManager.importResources(); }
            @Override public boolean performAutoPullPush() { return AbstractResourceInterfaceLogic.this.performAutoPullPush(); }
            @Override public boolean hasWorkToDo() { return inventoryManager.hasWorkToDo(); }
        };
    }

    private InterfaceAdjacentHandler.ResourceOps<R, K> createAdjacentResourceOps() {
        return new InterfaceAdjacentHandler.ResourceOps<R, K>() {
            @Override public List<Capability<?>> getAdjacentCapabilities() { return AbstractResourceInterfaceLogic.this.getAdjacentCapabilities(); }
            @Override public long countResourceInHandler(Object handler, K key, EnumFacing facing) { return AbstractResourceInterfaceLogic.this.countResourceInHandler(handler, key, facing); }
            @Override public long extractResourceFromHandler(Object handler, K key, int maxAmount, EnumFacing facing) { return AbstractResourceInterfaceLogic.this.extractResourceFromHandler(handler, key, maxAmount, facing); }
            @Override public long insertResourceIntoHandler(Object handler, R identity, int maxAmount, EnumFacing facing) { return AbstractResourceInterfaceLogic.this.insertResourceIntoHandler(handler, identity, maxAmount, facing); }
            @Override public R copyAsIdentity(R resource) { return AbstractResourceInterfaceLogic.this.copyAsIdentity(resource); }
            @Override public Map<K, Long> buildResourceCountMap(Object handler, EnumFacing facing) { return AbstractResourceInterfaceLogic.this.buildResourceCountMap(handler, facing); }
            @Override public void flushOperationCaches() { AbstractResourceInterfaceLogic.this.flushOperationCaches(); }
        };
    }

    private InterfaceAdjacentHandler.Callbacks createAdjacentCallbacks() {
        return new InterfaceAdjacentHandler.Callbacks() {
            @Override public World getHostWorld() { return host.getHostWorld(); }
            @Override public BlockPos getHostPos() { return host.getHostPos(); }
            @Override public boolean isExport() { return host.isExport(); }
            @Override public void markDirtyAndSave() { host.markDirtyAndSave(); }
            @Override public void markForNetworkUpdate() { host.markForNetworkUpdate(); }
        };
    }

    /**
     * Factory method for creating the adjacent handler.
     * Subclasses can override to provide a custom handler (e.g. for essentia's IAspectContainer scanning).
     * <p>
     * Called during construction, so subclass fields are NOT yet initialized.
     * Implementations must not depend on subclass instance state.
     */
    protected InterfaceAdjacentHandler<R, K> createAdjacentHandler(
            InterfaceAdjacentHandler.ResourceOps<R, K> ops,
            InterfaceAdjacentHandler.Callbacks callbacks,
            InterfaceInventoryManager<R, ?, K> inventoryManager
    ) {
        return new InterfaceAdjacentHandler<>(ops, callbacks, inventoryManager);
    }

    // ============================== Capability cache management ==============================

    /**
     * Get the Forge Capabilities that this interface type uses for adjacent interaction,
     * in priority order. The first non-null capability that an adjacent tile supports
     * will be cached and used for pull/push operations.
     * <p>
     * For example, item interfaces return [IItemRepository, IItemHandler] to prefer
     * slotless bulk operations (efficient for Storage Drawers) over slot-by-slot iteration.
     *
     * @return Ordered list of capabilities to try, or empty if this type does not use Forge capabilities
     */
    protected abstract List<Capability<?>> getAdjacentCapabilities();

    /**
     * Count how much of a resource matching the given key exists in the adjacent handler.
     * Used for keepQuantity calculations in both pull and push operations.
     * <p>
     * Prefer implementing {@link #buildResourceCountMap} to pre-compute all counts at once,
     * rather than scanning the handler's slots per-key. This method is only called as a
     * fallback when buildResourceCountMap returns null.
     *
     * @param handler The adjacent handler (typed appropriately by each subclass)
     * @param key The resource key to count
     * @param facing The direction from us to the adjacent block
     * @return The total amount of matching resources in the adjacent handler
     */
    protected abstract long countResourceInHandler(Object handler, K key, EnumFacing facing);

    /**
     * Build a map of all resources and their total amounts in the adjacent handler.
     * Used to pre-compute counts for keepQuantity calculations, avoiding repeated
     * iteration over the handler's slots for each filter during pull/push operations.
     * <p>
     * Default returns null, falling back to per-key {@link #countResourceInHandler} calls.
     * Subclasses should override when the handler supports efficient enumeration
     * (e.g. IItemRepository.getAllItems(), IFluidHandler.getTankProperties()).
     *
     * @param handler The adjacent handler
     * @param facing The direction from us to the adjacent block
     * @return Map of resource key → total amount, or null for per-key fallback
     */
    @Nullable
    protected Map<K, Long> buildResourceCountMap(Object handler, EnumFacing facing) {
        return null;
    }

    /**
     * Extract resources matching the given key from the adjacent handler.
     *
     * @param handler The adjacent handler
     * @param key The resource key to extract
     * @param maxAmount Maximum amount to extract (already capped by caller for space/keepQuantity)
     * @param facing The direction from us to the adjacent block
     * @return The amount actually extracted (0 if nothing was extracted)
     */
    protected abstract long extractResourceFromHandler(Object handler, K key, int maxAmount, EnumFacing facing);

    /**
     * Insert resources into the adjacent handler.
     * <p>
     * The identity resource provides the type information; the actual amount to insert
     * is given by {@code maxAmount}. The implementation should construct the appropriate
     * stack/resource object and attempt insertion.
     *
     * @param handler The adjacent handler
     * @param identity The resource identity (from our storage slot)
     * @param maxAmount Maximum amount to insert
     * @param facing The direction from us to the adjacent block
     * @return The amount actually accepted by the adjacent handler
     */
    protected abstract long insertResourceIntoHandler(Object handler, R identity, int maxAmount, EnumFacing facing);

    /**
     * Pull resources from an adjacent capability handler into our internal storage buffer.
     * <p>
     * Iterates our filter slots, and for each filtered resource present in the adjacent handler,
     * extracts up to the card's configured quantity while respecting keepQuantity and available space.
     * <p>
     * The handler-specific work is delegated to {@link #countResourceInHandler} and
     * {@link #extractResourceFromHandler}, which each subclass implements.
     *
     * @param adjacentHandler The adjacent capability handler (already validated by cache)
     * @param facing The direction from us to the adjacent block
     * @param quantity Maximum amount to transfer per resource type (from card config)
     * @param keepQuantity Amount to keep in the adjacent inventory (0 = take everything)
     * @return true if any resources were transferred
     */
    protected boolean pullFromAdjacent(Object adjacentHandler, EnumFacing facing, int quantity, int keepQuantity) {
        return this.adjacentHandler.pullFromAdjacent(adjacentHandler, facing, quantity, keepQuantity);
    }

    /**
     * Push resources from our internal storage buffer to an adjacent capability handler.
     * Delegates to {@link InterfaceAdjacentHandler#pushToAdjacent}.
     */
    protected boolean pushToAdjacent(Object adjacentHandler, EnumFacing facing, int quantity, int keepQuantity) {
        return this.adjacentHandler.pushToAdjacent(adjacentHandler, facing, quantity, keepQuantity);
    }

    /**
     * Scan all 6 adjacent positions and cache any valid capabilities.
     * Called when a card is installed or when the interface first loads with a card.
     */
    protected void refreshCapabilityCache() {
        this.adjacentHandler.refreshCapabilityCache();
    }

    /**
     * Clear all cached capabilities. Called when the card is removed.
     */
    protected void clearCapabilityCache() {
        this.adjacentHandler.clearCapabilityCache();
    }

    /**
     * Called when a neighbor block changes. Determines which facing was affected
     * and invalidates the corresponding cache entry.
     *
     * @param neighborPos The position of the block that changed
     */
    public void onNeighborChanged(BlockPos neighborPos) {
        this.adjacentHandler.onNeighborChanged(neighborPos, this.upgradeManager.hasAutoPullPushUpgrade());
    }

    // ============================== Auto-Pull/Push execution ==============================

    /**
     * Perform one cycle of auto-pull or auto-push operations across all cached capabilities.
     * Called from {@link #onTick(int)} when the card interval has elapsed.
     *
     * @return true if any resource was transferred
     */
    protected boolean performAutoPullPush() {
        if (!this.upgradeManager.hasAutoPullPushUpgrade()) return false;
        if (this.inventoryManager.getFilterSlotList().isEmpty()) return false;

        // Ensure cache is populated (handles first tick after load)
        if (!this.adjacentHandler.isCachePopulated()) refreshCapabilityCache();

        return this.adjacentHandler.performAutoPullPush(
                this.upgradeManager.getAutoPushPullQuantity(),
                this.upgradeManager.getAutoPullPushKeepQuantity());
    }

    /**
     * Called at the end of each auto-pull/push cycle to flush any transient caches
     * built during handler interaction. Default no-op; overridden by subclasses
     * that cache adjacent handler state across multiple extract/insert operations
     * within a single cycle (e.g. IItemHandler slot caching for items).
     */
    protected void flushOperationCaches() { }

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
     * Write a resource to a ByteBuf stream for client sync.
     * Default implementation wraps writeResourceToNBT via ByteBufUtils.
     * Override in subclasses that need a different wire format (e.g., compressed NBT for items,
     * compact string encoding for essentia).
     */
    protected void writeResourceToStream(R resource, ByteBuf data) {
        NBTTagCompound tag = new NBTTagCompound();
        writeResourceToNBT(resource, tag);
        ByteBufUtils.writeTag(data, tag);
    }

    /**
     * Read a resource from a ByteBuf stream for client sync.
     * Must match the format written by {@link #writeResourceToStream}.
     * @return The resource, or null if data is corrupted or unreadable.
     */
    @Nullable
    protected R readResourceFromStream(ByteBuf data) {
        NBTTagCompound tag = ByteBufUtils.readTag(data);
        return tag != null ? readResourceFromNBT(tag) : null;
    }

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
    // These delegate to inventoryManager for the actual logic.

    /**
     * Get the amount stored in a specific slot.
     */
    public long getSlotAmount(int slot) {
        return this.inventoryManager.getSlotAmount(slot);
    }

    /**
     * Set the resource and amount in a specific slot.
     * Updates the storage key map and orphan tracking.
     */
    protected void setResourceInSlotWithAmount(int slot, R resource, long amount) {
        this.inventoryManager.setResourceInSlotWithAmount(slot, resource, amount);
    }

    /**
     * Same as setResourceInSlotWithAmount(int, R, long) but uses the resource's identity and amount.
     */
    protected void setResourceInSlotWithAmount(int slot, R resource) {
        this.inventoryManager.setResourceInSlotWithAmount(slot, resource);
    }

    /**
     * Clear a specific slot, zeroing its amount.
     * Identity preservation: when a slot has a matching filter (not orphaned),
     * the identity is kept even when amount reaches 0.
     */
    protected void clearSlot(int slot) {
        this.inventoryManager.clearSlot(slot);
    }

    /**
     * Adjust the amount stored in a specific slot by a delta value.
     */
    public long adjustSlotAmount(int slot, long delta) {
        return this.inventoryManager.adjustSlotAmount(slot, delta);
    }

    /**
     * Create a resource stack from the storage identity with the given amount.
     */
    @Nullable
    protected R createStackFromSlot(int slot, long amount) {
        return this.inventoryManager.createStackFromSlot(slot, amount);
    }

    // ============================== Slot access ==============================

    public AppEngInternalInventory getUpgradeInventory() {
        return this.upgradeManager.getUpgradeInventory();
    }

    @Nullable
    public R getResourceInSlot(int slot) {
        return this.inventoryManager.getResourceInSlot(slot);
    }

    /**
     * Set the resource in a specific storage slot.
     * Used for GUI-based resource pouring.
     */
    public void setResourceInSlot(int slot, @Nullable R resource) {
        this.inventoryManager.setResourceInSlot(slot, resource);
    }

    @Override
    @Nullable
    public AE getFilter(int slot) {
        return this.inventoryManager.getFilter(slot);
    }

    /**
     * Set the filter for a slot. Subclasses may override to add callbacks.
     */
    @Override
    public void setFilter(int slot, @Nullable AE aeResource) {
        this.inventoryManager.setFilter(slot, aeResource);
    }

    /**
     * Insert resource into a specific storage slot (import interface operation).
     *
     * @return The amount actually inserted (clamped to int for external API compat)
     */
    public int insertIntoSlot(int slot, R resource) {
        return this.inventoryManager.insertIntoSlot(slot, resource);
    }

    /**
     * Internal insert with long amount support.
     *
     * @return The amount actually inserted (as long)
     */
    protected long insertIntoSlotLong(int slot, R resource, long amount) {
        return this.inventoryManager.insertIntoSlotLong(slot, resource, amount);
    }

    /**
     * Drain resource from a specific storage slot (export interface operation).
     */
    @Nullable
    public R drainFromSlot(int slot, int maxDrain, boolean doDrain) {
        return this.inventoryManager.drainFromSlot(slot, maxDrain, doDrain);
    }

    /**
     * Receive a resource into this interface based on filter configuration.
     * Used by import interface external handlers.
     */
    public int receiveFiltered(R resource, boolean doTransfer) {
        return this.inventoryManager.receiveFiltered(resource, doTransfer,
                this.upgradeManager.hasOverflowUpgrade(),
                this.upgradeManager.hasTrashUnselectedUpgrade());
    }

    /**
     * Drain any available resource from this interface.
     */
    @Nullable
    public R drainAny(int maxDrain, boolean doDrain) {
        return this.inventoryManager.drainAny(maxDrain, doDrain);
    }

    /**
     * Drain a specific resource from this interface.
     */
    @Nullable
    public R drainSpecific(R request, boolean doDrain) {
        return this.inventoryManager.drainSpecific(request, doDrain);
    }

    /**
     * Check if this interface can receive the given resource type.
     */
    public boolean canReceive(R resource) {
        return this.inventoryManager.canReceive(resource);
    }

    /**
     * Check if this interface can drain the given resource type.
     */
    public boolean canDrain(R resource) {
        return this.inventoryManager.canDrain(resource);
    }

    @Override
    public long validateMaxSlotSize(long newMax) {
        return this.inventoryManager.validateMaxSlotSize(newMax);
    }

    @Override
    public long setMaxSlotSize(long newMax) {
        return this.inventoryManager.setMaxSlotSize(newMax);
    }

    @Override
    public long getMaxSlotSize() {
        return this.inventoryManager.getMaxSlotSize();
    }

    @Override
    public int getPollingRate() {
        return this.tickScheduler.getPollingRate();
    }

    @Override
    public int setPollingRate(int ticks) {
        return this.setPollingRate(ticks, null);
    }

    /**
     * Set the polling rate with optional player notification on failure.
     */
    public int setPollingRate(int ticks, EntityPlayer player) {
        return this.tickScheduler.setPollingRate(ticks, player);
    }

    /**
     * Refresh the status of installed upgrades.
     * Delegates to upgradeManager.
     */
    @Override
    public void refreshUpgrades() {
        this.upgradeManager.refreshUpgrades();
    }

    @Override
    public int getTotalPages() {
        return 1 + this.upgradeManager.getInstalledCapacityUpgrades();
    }

    @Override
    public int getCurrentPage() {
        return this.currentPage;
    }

    @Override
    public void setCurrentPage(int page) {
        this.currentPage = Math.max(0, Math.min(page, this.upgradeManager.getInstalledCapacityUpgrades()));
    }

    /**
     * Refresh the filter-to-slot mapping and recalculate orphan status for all storage slots.
     * Also rebuilds the storage key map from current storage identities.
     */
    @Override
    public void refreshFilterMap() {
        this.inventoryManager.refreshFilterMap();
    }

    /**
     * Clear filter slots.
     * Import: only clears filters where the corresponding slot is empty.
     * Export: clears all filter slots.
     */
    @Override
    public void clearFilters() {
        this.inventoryManager.clearFilters();
    }

    // ============================== IFilterableInterfaceHost support ==============================

    @Override
    public boolean isInFilter(@Nonnull K key) {
        return this.inventoryManager.isInFilter(key);
    }

    public boolean isResourceInFilter(@Nullable R resource) {
        return this.inventoryManager.isResourceInFilter(resource);
    }

    @Override
    public int findSlotByKey(@Nonnull K key) {
        return this.inventoryManager.findSlotByKey(key);
    }

    /**
     * Get the effective number of filter slots based on installed capacity upgrades.
     */
    public int getEffectiveFilterSlots() {
        return SLOTS_PER_PAGE * getTotalPages();
    }

    @Override
    public boolean isStorageEmpty(int slot) {
        return this.inventoryManager.isSlotEmpty(slot);
    }

    // ============================== Array accessor methods for subclasses ==============================
    // Subclasses should use these instead of directly accessing the storage/filter/amounts arrays.
    // These delegate to the inventoryManager which owns the canonical data.

    /**
     * Get the raw storage identity for a specific slot (without amount).
     * This is the resource type/NBT stored in that slot, or null if empty.
     */
    @Nullable
    protected R getStorageIdentity(int slot) {
        return this.inventoryManager.getStorageIdentity(slot);
    }

    /**
     * Get the raw filter resource for a specific slot.
     * @return The filter resource, or null if no filter is set
     */
    @Nullable
    protected R getRawFilter(int slot) {
        return this.inventoryManager.getRawFilter(slot);
    }

    /**
     * Get an unmodifiable view of the filter slot list (slot indices with filters, in order).
     */
    protected List<Integer> getFilterSlotList() {
        return this.inventoryManager.getFilterSlotList();
    }

    /**
     * Get the set of slot indices that have non-null storage identities.
     * Some may have amount == 0 due to identity preservation (filter match).
     */
    protected Set<Integer> getOccupiedStorageSlots() {
        return this.inventoryManager.getOccupiedStorageSlots();
    }

    /**
     * Get the filter key for a specific slot.
     * @return The key, or null if no filter in that slot
     */
    @Nullable
    protected K getFilterKey(int slot) {
        return this.inventoryManager.getFilterKey(slot);
    }

    /**
     * Check if a filter key exists.
     */
    protected boolean containsFilterKey(@Nonnull K key) {
        return this.inventoryManager.containsFilterKey(key);
    }

    /**
     * Get the filter slot index for a given key.
     * @return The slot index, or null if not found
     */
    @Nullable
    protected Integer getFilterSlotForKey(K key) {
        return this.inventoryManager.getFilterSlot(key);
    }

    /**
     * Collect stored resources that should be dropped (not upgrades).
     * Attempts to return stored resources to the ME network first.
     */
    public void getStorageDrops(List<ItemStack> drops) {
        this.inventoryManager.getStorageDrops(drops);
    }

    /**
     * Collect all items that should be dropped when this interface is broken normally.
     * Includes both stored resources and upgrades.
     */
    public void getDrops(List<ItemStack> drops) {
        getStorageDrops(drops);
        this.upgradeManager.getUpgradeDrops(drops);
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

    protected void readFiltersFromNBT(NBTTagCompound data, String name) {
        this.inventoryManager.readFiltersFromNBT(data, name);
    }

    /**
     * Read logic state from NBT. Call from host's readFromNBT.
     */
    public void readFromNBT(NBTTagCompound data) {
        readFiltersFromNBT(data, getFiltersNBTKey());
        readStorageFromNBT(data);

        this.upgradeManager.readFromNBT(data);
        this.inventoryManager.readFromNBT(data);
        this.tickScheduler.readFromNBT(data);

        this.inventoryManager.refreshFilterMap();
        this.upgradeManager.refreshUpgrades();
    }

    /**
     * Read storage data from NBT. Override in subclass for type-specific migration.
     * Reads both identity and amounts from NBT, supporting long amounts.
     */
    protected void readStorageFromNBT(NBTTagCompound data) {
        this.inventoryManager.readStorageFromNBT(data, getStorageNBTKey());
    }

    /**
     * Write logic state to NBT. Call from host's writeToNBT.
     */
    public void writeToNBT(NBTTagCompound data) {
        this.inventoryManager.writeFiltersToNBT(data, getFiltersNBTKey());
        this.inventoryManager.writeStorageToNBT(data, getStorageNBTKey());
        this.upgradeManager.writeToNBT(data);
        this.inventoryManager.writeToNBT(data);
        this.tickScheduler.writeToNBT(data);
    }

    // ============================== Stream serialization ==============================
    // These delegate to inventoryManager, which uses ResourceOps for type-specific
    // resource encoding. Subclasses customize wire format by overriding
    // writeResourceToStream() / readResourceFromStream() instead of these methods.

    /**
     * Read storage data from a ByteBuf stream for client sync.
     * @return true if any data changed
     */
    public boolean readStorageFromStream(ByteBuf data) {
        return this.inventoryManager.readStorageFromStream(data);
    }

    /**
     * Write storage data to a ByteBuf stream for client sync.
     */
    public void writeStorageToStream(ByteBuf data) {
        this.inventoryManager.writeStorageToStream(data);
    }

    /**
     * Read filter data from a ByteBuf stream for client sync.
     * @return true if any data changed
     */
    public boolean readFiltersFromStream(ByteBuf data) {
        return this.inventoryManager.readFiltersFromStream(data);
    }

    /**
     * Write filter data to a ByteBuf stream for client sync.
     */
    public void writeFiltersToStream(ByteBuf data) {
        this.inventoryManager.writeFiltersToStream(data);
    }

    /**
     * Download settings to NBT for memory cards.
     */
    public NBTTagCompound downloadSettings() {
        NBTTagCompound output = new NBTTagCompound();

        this.inventoryManager.writeToNBT(output);
        this.tickScheduler.writeToNBT(output);

        return output;
    }

    /**
     * Download settings with filters for memory card + keybind.
     */
    public NBTTagCompound downloadSettingsWithFilter() {
        NBTTagCompound output = downloadSettings();
        this.inventoryManager.writeFiltersToNBT(output, getFiltersNBTKey());

        return output;
    }

    /**
     * Download settings with filters AND upgrades for disassembly.
     */
    public NBTTagCompound downloadSettingsForDismantle() {
        NBTTagCompound output = downloadSettingsWithFilter();
        this.upgradeManager.writeToNBT(output);

        return output;
    }

    /**
     * Upload settings from NBT (memory card or dismantle).
     */
    public void uploadSettings(NBTTagCompound compound, EntityPlayer player) {
        if (compound == null) return;

        this.inventoryManager.readFromNBT(compound);
        this.tickScheduler.readFromNBT(compound, player);

        // Merge upgrades FIRST (capacity cards enable extra pages for filters)
        this.upgradeManager.readFromNBT(compound);

        // Merge filter inventory from memory card instead of replacing
        if (compound.hasKey(getFiltersNBTKey())) {
            mergeFiltersFromNBT(compound, getFiltersNBTKey(), player);
        }

        // Apply the loaded upgrade/filter state to cached fields.
        // Without this, capacity cards, auto-pull/push cards, and filter maps
        // remain at default values until manually re-triggered by the user.
        this.inventoryManager.refreshFilterMap();
        this.upgradeManager.refreshUpgrades();
    }

    protected void mergeFiltersFromNBT(NBTTagCompound data, String name, @Nullable EntityPlayer player) {
        if (!data.hasKey(name)) return;

        // Read source filters into temporary array
        List<R> sourceFilters = new ArrayList<>();
        NBTTagCompound filtersMap = data.getCompoundTag(name);
        for (String key : filtersMap.getKeySet()) {
            if (!key.startsWith("#")) continue;

            try {
                int slot = Integer.parseInt(key.substring(1));
                if (slot < 0 || slot >= FILTER_SLOTS) continue;

                NBTTagCompound filterTag = filtersMap.getCompoundTag(key);
                if (filterTag.isEmpty()) continue;

                sourceFilters.add(readResourceFromNBT(filterTag));
            } catch (NumberFormatException ignored) {
            }
        }

        this.inventoryManager.mergeFilters(sourceFilters, player);
    }

    // ============================== Inventory change handling ==============================

    /**
     * Handle filter changes. Call from host or subclass.
     */
    public void onFilterChanged(int slot) {
        this.inventoryManager.onFilterChanged(slot);
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
        if (inv == this.upgradeManager.getUpgradeInventory()) {
            this.refreshUpgrades();
            this.host.markDirtyAndSave();
        }
    }

    // ============================== Tick handling ==============================

    /**
     * Create a TickingRequest based on current configuration.
     * <p>
     * When an auto-pull/push card is installed, the tick rate must accommodate
     * both the card interval and the network I/O polling rate. We use the minimum
     * of the two as the tick rate, and time-gate each operation independently.
     * <p>
     * Special case: Adaptive polling + card installed:
     * The node must never sleep, otherwise the card timer won't advance.
     * We cap the max wait to avoid waiting longer than the card interval, but allow going faster when there's work to do.
     * <p>
     * As we never sleep with the card installed, calling wakeUpIfAdaptive() is useless for any method the card calls,
     * like performAutoPullPush(). Instead, we rely on the tick manager to call onTick at the configured rate.
     */
    public TickingRequest getTickingRequest() {
        return this.tickScheduler.getTickingRequest(
            this.upgradeManager.hasAutoPullPushUpgrade(),
            this.upgradeManager.getAutoPullPushInterval()
        );
    }

    /**
     * Handle a tick with elapsed-time tracking for dual-timer dispatch.
     * <p>
     * When a card is installed, two independent timers are maintained:
     * <ul>
     *   <li><b>Card timer:</b> Fires at {@code autoPullPushInterval} ticks, runs {@link #performAutoPullPush()}</li>
     *   <li><b>Network I/O timer:</b> Fires at the polling rate, runs import/export resources</li>
     * </ul>
     * Both timers use {@code >=} threshold checks (not {@code ==}) to tolerate AE2's imprecise tick scheduling.
     *
     * @param ticksSinceLastCall Number of ticks since this method was last called (from AE2 tick manager)
     * @return The appropriate tick rate modulation
     */
    public TickRateModulation onTick(int ticksSinceLastCall) {
        return this.tickScheduler.onTick(
            ticksSinceLastCall,
            this.upgradeManager.hasAutoPullPushUpgrade(),
            this.upgradeManager.getAutoPullPushInterval()
        );
    }

    /**
     * Wake up the tick manager if sleeping and using adaptive polling.
     * Only calls alertDevice() when actually sleeping - tick modulation handles the rest.
     */
    public void wakeUpIfAdaptive() {
        this.tickScheduler.wakeUpIfAdaptive();
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
}
