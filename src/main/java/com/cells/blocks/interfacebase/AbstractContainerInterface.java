package com.cells.blocks.interfacebase;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import appeng.api.implementations.guiobjects.IGuiItem;
import appeng.api.parts.IPart;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.SlotNormal;
import appeng.container.slot.SlotRestrictedInput;
import appeng.helpers.InventoryAction;
import appeng.items.contents.NetworkToolViewer;
import appeng.items.tools.ToolNetworkTool;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.Platform;

import com.cells.network.CellsNetworkHandler;
import com.cells.network.sync.IQuickAddFilterContainer;
import com.cells.network.sync.IResourceSyncContainer;
import com.cells.network.sync.PacketResourceSlot;
import com.cells.network.sync.ResourceType;


/**
 * Abstract base container for all interface types (Item, Fluid, Gas, Essentia).
 * Provides unified filter management with O(1) key-based lookups via the host's HashMap cache.
 * Implements unified resource sync via {@link IResourceSyncContainer} and {@link PacketResourceSlot}.
 * <p>
 * Subclasses must implement type-specific methods delegating to their host interface.
 *
 * @param <T> The stored stack type (ItemStack, IAEFluidStack, IAEGasStack)
 * @param <K> The key type for hashable lookups (ItemStackKey, FluidStackKey, GasStackKey)
 * @param <H> The host interface type (IItemInterfaceHost, IFluidInterfaceHost, etc.)
 */
public abstract class AbstractContainerInterface<T, K, H extends IFilterableInterfaceHost<T, K>>
    extends AEBaseContainer
    implements IResourceSyncContainer, IQuickAddFilterContainer {

    protected final H host;

    /** Server-side cache for filter sync (tracks what was sent to clients). */
    protected final Map<Integer, T> serverFilterCache = new HashMap<>();

    // Network tool ("toolbox") support
    private int toolboxSlot;
    private NetworkToolViewer toolboxInventory;

    @GuiSync(0)
    public long maxSlotSize;

    @GuiSync(1)
    public long pollingRate = 0;

    @GuiSync(2)
    public int currentPage = 0;

    @GuiSync(3)
    public int totalPages = 1;

    /**
     * Common constructor for both tile and part hosts.
     */
    protected AbstractContainerInterface(
        final InventoryPlayer ip,
        final H host,
        final Object anchor,
        final long defaultMaxSlotSize
    ) {
        super(
            ip,
            anchor instanceof TileEntity ? (TileEntity) anchor : null,
            anchor instanceof IPart ? (IPart) anchor : null
        );
        this.host = host;
        this.maxSlotSize = defaultMaxSlotSize;
        this.setupToolbox(anchor);

        // Add upgrade slots
        for (int i = 0; i < AbstractResourceInterfaceLogic.UPGRADE_SLOTS; i++) {
            this.addSlotToContainer(new SlotUpgrade<>(
                this.host.getUpgradeInventory(), i, 186, 25 + i * 18, this.host
            ));
        }

        // Bind player inventory
        this.bindPlayerInventory(ip, 0, 174);
    }

    // ================================= Toolbox Support =================================

    /**
     * Set up the network tool ("toolbox") if the player has one in their inventory.
     * Adds a 3x3 grid of upgrade slots at position (186, 156).
     */
    protected void setupToolbox(Object anchor) {
        // Get world and position from the host
        World w = this.host.getHostWorld();
        BlockPos pos = this.host.getHostPos();

        if (w == null || pos == null) return;

        final IInventory pi = this.getPlayerInv();
        for (int x = 0; x < pi.getSizeInventory(); x++) {
            final ItemStack pii = pi.getStackInSlot(x);
            if (!pii.isEmpty() && pii.getItem() instanceof ToolNetworkTool) {
                this.lockPlayerInventorySlot(x);
                this.toolboxSlot = x;
                this.toolboxInventory = (NetworkToolViewer) ((IGuiItem) pii.getItem())
                    .getGuiObject(pii, w, pos);
                break;
            }
        }

        if (this.hasToolbox()) {
            for (int v = 0; v < 3; v++) {
                for (int u = 0; u < 3; u++) {
                    SlotRestrictedInput slot = new SlotRestrictedInput(
                        SlotRestrictedInput.PlacableItemType.UPGRADES,
                        this.toolboxInventory.getInternalInventory(),
                        u + v * 3, 186 + u * 18, 156 + v * 18,
                        this.getInventoryPlayer());
                    slot.setPlayerSide();
                    this.addSlotToContainer(slot);
                }
            }
        }
    }

    /**
     * Check if the player has a network tool ("toolbox") in their inventory.
     */
    public boolean hasToolbox() {
        return this.toolboxInventory != null;
    }

    /**
     * Validate that the toolbox is still in the expected slot.
     * Called from detectAndSendChanges to ensure the container remains valid.
     */
    protected void checkToolbox() {
        if (!hasToolbox()) return;

        final ItemStack currentItem = this.getPlayerInv().getStackInSlot(this.toolboxSlot);

        if (currentItem == this.toolboxInventory.getItemStack()) return;

        if (currentItem.isEmpty()) {
            this.setValidContainer(false);
            return;
        }

        if (ItemStack.areItemsEqual(this.toolboxInventory.getItemStack(), currentItem)) {
            this.getPlayerInv().setInventorySlotContents(
                this.toolboxSlot,
                this.toolboxInventory.getItemStack()
            );
        } else {
            this.setValidContainer(false);
        }
    }

    // ================================= Abstract Methods =================================

    /**
     * @return The resource type for network sync.
     */
    protected abstract ResourceType getResourceType();

    /**
     * Create a key from a stack.
     */
    @Nullable
    protected abstract K createKey(@Nullable T stack);

    /**
     * Get the filter at a specific slot from the host.
     */
    @Nullable
    protected abstract T getFilter(int slot);

    /**
     * Set the filter at a specific slot on the host.
     */
    protected abstract void setFilter(int slot, @Nullable T stack);

    /**
     * Check if storage at a slot is empty.
     */
    protected abstract boolean isStorageEmpty(int slot);

    /**
     * Check if two keys are equal.
     */
    protected abstract boolean keysEqual(@Nonnull K a, @Nonnull K b);

    /**
     * Extract a filter stack from an ItemStack container (for shift-click).
     * Returns null if the item doesn't contain a valid stack of this type.
     */
    @Nullable
    protected abstract T extractFilterFromContainer(ItemStack container);

    /**
     * Convert a stack to an AE-wrapped version suitable for setFilter.
     * For fluids/gases, this wraps in IAEFluidStack/IAEGasStack.
     * For items, this may just return the stack as-is or create a single-count copy.
     */
    @Nonnull
    protected abstract T createFilterStack(@Nonnull T raw);

    /**
     * Copy a filter stack (for caching).
     * Must return a deep copy that won't be affected by original changes.
     */
    @Nullable
    protected abstract T copyFilter(@Nullable T filter);

    /**
     * Check if two filter stacks are equal (for sync comparison).
     */
    protected abstract boolean filtersEqual(@Nullable T a, @Nullable T b);

    // ================================= Host Access =================================

    public H getHost() {
        return this.host;
    }

    public void setMaxSlotSize(int size) {
        this.host.setMaxSlotSize(size);
    }

    public void setPollingRate(int ticks) {
        this.host.setPollingRate(ticks);
    }

    /**
     * Clear all filters. Delegates to host.
     */
    public void clearFilters() {
        this.host.clearFilters();
    }

    // ================================= Pagination =================================

    /**
     * Sets the current page for viewing, clamped to valid range.
     */
    public void setCurrentPage(int page) {
        int newPage = Math.max(0, Math.min(page, this.totalPages - 1));
        this.currentPage = newPage;
        this.host.setCurrentPage(newPage);
    }

    public void nextPage() {
        if (this.currentPage < this.totalPages - 1) setCurrentPage(this.currentPage + 1);
    }

    public void prevPage() {
        if (this.currentPage > 0) setCurrentPage(this.currentPage - 1);
    }

    // ================================= Common Sync =================================

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        // Validate toolbox is still in expected slot
        this.checkToolbox();

        // Sync GuiSync values from host
        if (this.maxSlotSize != this.host.getMaxSlotSize()) this.maxSlotSize = this.host.getMaxSlotSize();
        if (this.pollingRate != this.host.getPollingRate()) this.pollingRate = this.host.getPollingRate();
        if (this.currentPage != this.host.getCurrentPage()) this.currentPage = this.host.getCurrentPage();
        if (this.totalPages != this.host.getTotalPages()) this.totalPages = this.host.getTotalPages();

        // Server-side filter sync
        if (!Platform.isServer()) return;

        final int filterSlots = AbstractResourceInterfaceLogic.FILTER_SLOTS;
        final ResourceType type = getResourceType();

        for (int i = 0; i < filterSlots; i++) {
            T current = getFilter(i);
            T cached = this.serverFilterCache.get(i);

            if (!filtersEqual(current, cached)) {
                this.serverFilterCache.put(i, copyFilter(current));

                // Send diff to all listeners
                for (IContainerListener listener : this.listeners) {
                    if (listener instanceof EntityPlayerMP) {
                        CellsNetworkHandler.INSTANCE.sendTo(
                            new PacketResourceSlot(type, i, current),
                            (EntityPlayerMP) listener
                        );
                    }
                }
            }
        }
    }

    @Override
    public void addListener(@Nonnull IContainerListener listener) {
        super.addListener(listener);

        // Send full filter inventory to new listener
        if (!Platform.isServer() || !(listener instanceof EntityPlayerMP)) return;

        final int filterSlots = AbstractResourceInterfaceLogic.FILTER_SLOTS;
        final ResourceType type = getResourceType();
        Map<Integer, Object> fullMap = new HashMap<>();

        for (int i = 0; i < filterSlots; i++) {
            T filter = getFilter(i);
            fullMap.put(i, filter);
            this.serverFilterCache.put(i, copyFilter(filter));
        }

        CellsNetworkHandler.INSTANCE.sendTo(
            new PacketResourceSlot(type, fullMap),
            (EntityPlayerMP) listener
        );
    }

    /**
     * Receive resource slot updates from unified sync packet.
     * Implements {@link IResourceSyncContainer}.
     */
    @Override
    @SuppressWarnings("unchecked")
    public void receiveResourceSlots(ResourceType type, Map<Integer, Object> resources) {
        // Only handle our resource type
        if (type != getResourceType()) return;

        // On client, update the host directly (GUI reads from host via slots)
        if (this.host.getHostWorld() != null && this.host.getHostWorld().isRemote) {
            for (Map.Entry<Integer, Object> entry : resources.entrySet()) {
                setFilter(entry.getKey(), (T) entry.getValue());
            }
            return;
        }

        // On server, validate and apply updates
        Map<Integer, T> typedFilters = new HashMap<>();
        for (Map.Entry<Integer, Object> entry : resources.entrySet()) {
            typedFilters.put(entry.getKey(), (T) entry.getValue());
        }

        applyFilterUpdates(typedFilters, getPlayerFromListeners());
    }

    /**
     * Get a filter from the host (works on both client and server).
     * On client, returns the synced value.
     * On server, returns the live value.
     */
    public T getClientFilter(int slot) {
        return getFilter(slot);
    }

    // ================================= Unified Filter Operations =================================

    /**
     * Check if a filter with the given key already exists in any slot.
     * Uses the host's HashMap cache for O(1) lookup.
     *
     * @param key The key to check
     * @return true if a duplicate exists
     */
    public boolean isInFilter(@Nonnull K key) {
        return this.host.isInFilter(key);
    }

    /**
     * Get the effective number of filter slots based on installed capacity upgrades.
     */
    public int getEffectiveFilterSlots() {
        return this.host.getEffectiveFilterSlots();
    }

    /**
     * Find the first available filter slot that can accept a new filter.
     * For import interfaces, the corresponding storage slot must also be empty.
     *
     * @return The slot index, or -1 if no slot is available
     */
    public int findFirstAvailableSlot() {
        final boolean isExport = this.host.isExport();
        final int effectiveSlots = getEffectiveFilterSlots();

        for (int i = 0; i < effectiveSlots; i++) {
            T existingFilter = getFilter(i);
            if (existingFilter != null) continue;

            // Import mode: only use slot if storage is also empty
            if (!isExport && !isStorageEmpty(i)) continue;

            return i;
        }

        return -1;
    }

    /**
     * Attempt to add a stack to the first available filter slot.
     * Checks for duplicates using the key-based lookup.
     * Provides feedback messages to the player on failure.
     *
     * @param stack  The stack to add as a filter
     * @param player The player to send feedback messages to (can be null)
     * @return true if the filter was added successfully
     */
    public boolean addToFilter(@Nonnull T stack, @Nullable EntityPlayer player) {
        K key = createKey(stack);
        if (key == null) {
            if (player != null) {
                player.sendMessage(new TextComponentTranslation(
                    "message.cells.not_valid_content",
                    new TextComponentTranslation(this.host.getTypeLocalizationKey())
                ));
            }
            return false;
        }

        // Check for duplicates using O(1) lookup
        if (isInFilter(key)) {
            if (player != null) {
                player.sendMessage(new TextComponentTranslation("message.cells.filter_duplicate"));
            }
            return false;
        }

        // Find first available slot
        int slot = findFirstAvailableSlot();
        if (slot < 0) {
            if (player != null) {
                player.sendMessage(new TextComponentTranslation("message.cells.no_filter_space"));
            }
            return false;
        }

        // Set the filter
        T filterStack = createFilterStack(stack);
        setFilter(slot, filterStack);
        this.host.refreshFilterMap();
        return true;
    }

    /**
     * Validate and apply filter updates received from sync packets.
     * Common logic for fluid/gas slot sync handlers.
     *
     * @param filters Map of slot -> stack updates
     * @param player  Player for feedback messages (null on client)
     */
    protected void applyFilterUpdates(Map<Integer, T> filters, @Nullable EntityPlayer player) {
        final boolean isExport = this.host.isExport();

        for (Map.Entry<Integer, T> entry : filters.entrySet()) {
            int slot = entry.getKey();
            T stack = entry.getValue();

            // Validate slot index
            if (slot < 0 || slot >= AbstractResourceInterfaceLogic.FILTER_SLOTS) continue;

            // Null stack means clearing the filter
            if (stack == null) {
                // Import: only clear if storage is empty (prevent orphans)
                if (!isExport && !isStorageEmpty(slot)) {
                    if (player != null) {
                        player.sendMessage(new TextComponentTranslation("message.cells.storage_not_empty"));
                    }
                    continue;
                }

                setFilter(slot, null);
                continue;
            }

            // Import: prevent filter changes if storage has content
            if (!isExport && !isStorageEmpty(slot)) {
                if (player != null) {
                    player.sendMessage(new TextComponentTranslation("message.cells.storage_not_empty"));
                }
                continue;
            }

            // Check for duplicates
            K newKey = createKey(stack);
            if (newKey == null) continue;

            // Check against existing filters (except current slot)
            boolean isDuplicate = false;
            for (int i = 0; i < AbstractResourceInterfaceLogic.FILTER_SLOTS; i++) {
                if (i == slot) continue;

                T other = getFilter(i);
                if (other == null) continue;

                K otherKey = createKey(other);
                if (otherKey != null && keysEqual(newKey, otherKey)) {
                    isDuplicate = true;
                    break;
                }
            }

            if (isDuplicate) {
                if (player != null) {
                    player.sendMessage(new TextComponentTranslation("message.cells.filter_duplicate"));
                }
            } else {
                setFilter(slot, stack);
            }
        }

        // Refresh the filter map after batch updates
        this.host.refreshFilterMap();
    }

    /**
     * Get the player from container listeners for sending feedback messages.
     */
    @Nullable
    protected EntityPlayer getPlayerFromListeners() {
        for (IContainerListener listener : this.listeners) {
            if (listener instanceof EntityPlayer) return (EntityPlayer) listener;
        }
        return null;
    }

    // ================================= Shift-Click Handler =================================

    /**
     * Common shift-click handler that handles upgrade insertion and filter extraction.
     * <p>
     * Priority order for shift-click from player inventory:
     * <ol>
     *   <li>Try to insert into upgrade slots (if item is valid upgrade and space available)</li>
     *   <li>Try to extract filter from container and add to filter slots</li>
     * </ol>
     *
     * @param player    The player performing the action
     * @param slotIndex The clicked slot index
     * @return ItemStack.EMPTY (we never move items out of this GUI, only set filters)
     */
    @Override
    @Nonnull
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        // Only process on server side
        if (player.world.isRemote) return ItemStack.EMPTY;

        if (slotIndex < 0 || slotIndex >= inventorySlots.size()) return ItemStack.EMPTY;

        Slot slot = inventorySlots.get(slotIndex);
        if (slot == null || !slot.getHasStack()) return ItemStack.EMPTY;

        ItemStack clickedStack = slot.getStack();
        if (clickedStack.isEmpty()) return ItemStack.EMPTY;

        // Determine if this is a player-side slot (player inventory or hotbar)
        boolean isFromPlayerInventory = (slot instanceof AppEngSlot)
            && ((AppEngSlot) slot).isPlayerSide();

        // Shift-clicking from container-side slots (upgrade slots) - move to player inventory
        // Delegate to parent for container-to-player transfers
        if (!isFromPlayerInventory) return super.transferStackInSlot(player, slotIndex);

        // === Shift-clicking from player inventory ===

        // Priority 1: Try to insert into upgrade slots if item is a valid upgrade
        if (this.host.isValidUpgrade(clickedStack)) {
            boolean insertedAny = false;

            // Insert as many as possible into empty upgrade slots
            // Iterate by slot type (SlotUpgrade), not by index, to avoid toolbox slots
            for (Slot containerSlot : this.inventorySlots) {
                if (clickedStack.isEmpty()) break;
                if (!(containerSlot instanceof SlotUpgrade)) continue;
                if (containerSlot.getHasStack()) continue;
                if (!containerSlot.isItemValid(clickedStack)) continue;

                // Found empty upgrade slot - insert one item
                ItemStack toInsert = clickedStack.splitStack(1);
                containerSlot.putStack(toInsert);
                containerSlot.onSlotChanged();
                insertedAny = true;
            }

            if (insertedAny) {
                if (clickedStack.isEmpty()) slot.putStack(ItemStack.EMPTY);
                slot.onSlotChanged();
                this.detectAndSendChanges();
            }

            // Don't try filter extraction for upgrade items
            return ItemStack.EMPTY;
        }

        // Priority 2: Try to extract filter from the container and add to filter slots
        T filterStack = extractFilterFromContainer(clickedStack);
        if (filterStack != null) addToFilter(filterStack, player);

        return ItemStack.EMPTY;
    }

    // ================================= IQuickAddFilterContainer =================================

    @Override
    public ResourceType getQuickAddResourceType() {
        return getResourceType();
    }

    /**
     * Check if a resource is already in the filter.
     * Subclasses must implement createKeyFromResource to convert the Object to type K.
     */
    @Override
    @SuppressWarnings("unchecked")
    public boolean isResourceInFilter(@Nonnull Object resource) {
        K key = createKey((T) resource);
        return key != null && isInFilter(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean quickAddToFilter(@Nonnull Object resource, @Nullable EntityPlayer player) {
        return addToFilter((T) resource, player);
    }

    @Override
    public String getTypeLocalizationKey() {
        return this.host.getTypeLocalizationKey();
    }

    // ================================= Inventory Action Handler =================================

    /**
     * Handle inventory actions for storage slots.
     * <p>
     * Direction restrictions (no swaps allowed):
     * <ul>
     *   <li>Export: pickup only (can extract, cannot insert)</li>
     *   <li>Import: setdown only (can insert, cannot extract)</li>
     * </ul>
     *
     * @param player The player performing the action
     * @param action The action type
     * @param slot The slot index (display slot, not absolute)
     * @param id Additional action ID
     */
    @Override
    public void doAction(EntityPlayerMP player, InventoryAction action, int slot, long id) {
        final int slotsPerPage = AbstractResourceInterfaceLogic.SLOTS_PER_PAGE;

        // Check if this is a storage slot action
        if (slot >= 0 && slot < slotsPerPage) {
            int actualSlot = slot + (this.currentPage * slotsPerPage);

            // PICKUP_OR_SET_DOWN: direct cursor/storage interaction (full stack)
            // SPLIT_OR_PLACE_SINGLE: same but half/single amount
            // - Export: extract to cursor only (no insert, no swap)
            // - Import: insert from cursor only (no extract, no swap)
            if (action == InventoryAction.PICKUP_OR_SET_DOWN) {
                if (handleStorageInteraction(player, actualSlot, false)) return;
            }
            if (action == InventoryAction.SPLIT_OR_PLACE_SINGLE) {
                if (handleStorageInteraction(player, actualSlot, true)) return;
            }

            // SHIFT_CLICK: transfer to player inventory
            // - Export only: move from storage to player
            // - Import: no action (extraction not allowed)
            if (action == InventoryAction.SHIFT_CLICK) {
                if (handleStorageShiftClick(player, actualSlot)) return;
            }

            // EMPTY_ITEM: pour from held container into storage (fluids/gases)
            // - Import only: pour into tank
            // - Export: no action (insertion not allowed)
            if (action == InventoryAction.EMPTY_ITEM && !this.host.isExport()) {
                if (handleEmptyItemAction(player, actualSlot)) return;
            }

            // FILL_ITEM: extract from storage into held container (fluids/gases)
            // - Export only: fill container from tank
            // - Import: no action (extraction not allowed)
            if (action == InventoryAction.FILL_ITEM && this.host.isExport()) {
                if (handleFillItemAction(player, actualSlot)) return;
            }
        }

        super.doAction(player, action, slot, id);
    }

    /**
     * Handle direct storage interaction (pickup/setdown).
     * <p>
     * This method enforces directional restrictions:
     * <ul>
     *   <li>Export: extract only (empty cursor → fill from storage)</li>
     *   <li>Import: insert only (item in cursor → insert to storage)</li>
     *   <li>Neither mode allows swap (different item in cursor + non-empty storage = no action)</li>
     * </ul>
     *
     * @param player The player performing the action
     * @param slot The absolute storage slot index
     * @param halfStack If true, only transfer half/single (right-click behavior)
     * @return true if handled (even if no-op due to restrictions), false to delegate
     */
    protected boolean handleStorageInteraction(EntityPlayerMP player, int slot, boolean halfStack) {
        // Default: no direct storage interaction (fluids/gases use EMPTY_ITEM instead)
        return false;
    }

    /**
     * Handle shift-click on storage slots.
     * <p>
     * Export mode: transfers entire stack from storage to player inventory.
     * Import mode: no action (extraction not allowed).
     *
     * @param player The player performing the action
     * @param slot The absolute storage slot index
     * @return true if handled, false to delegate
     */
    protected boolean handleStorageShiftClick(EntityPlayerMP player, int slot) {
        // Default: no shift-click support (only items support this)
        return false;
    }

    /**
     * Handle pouring from held item into a tank slot.
     * <p>
     * Override in subclasses that support tank operations (fluid, gas).
     * Default implementation returns false (not handled).
     *
     * @param player The player performing the action
     * @param tankSlot The tank slot index
     * @return true if handled, false to delegate to parent
     */
    protected boolean handleEmptyItemAction(EntityPlayerMP player, int tankSlot) {
        return false;
    }

    /**
     * Handle filling held item from a tank slot.
     * <p>
     * Override in subclasses that support tank operations (fluid, gas).
     * Default implementation returns false (not handled).
     *
     * @param player The player performing the action
     * @param tankSlot The tank slot index
     * @return true if handled, false to delegate to parent
     */
    protected boolean handleFillItemAction(EntityPlayerMP player, int tankSlot) {
        return false;
    }

    // ================================= Upgrade Slot Class =================================

    /**
     * Common upgrade slot implementation.
     */
    protected static class SlotUpgrade<H extends IInterfaceHost> extends SlotNormal {
        private final H host;

        public SlotUpgrade(AppEngInternalInventory inv, int idx, int x, int y, H host) {
            super(inv, idx, x, y);
            this.host = host;
            this.setIIcon(13 * 16 + 15); // UPGRADES icon
        }

        @Override
        public boolean isItemValid(@Nonnull ItemStack stack) {
            return host.isValidUpgrade(stack);
        }

        @Override
        public int getSlotStackLimit() {
            return 1;
        }

        @Override
        public hasCalculatedValidness getIsValid() {
            return hasCalculatedValidness.Valid;
        }
    }
}
