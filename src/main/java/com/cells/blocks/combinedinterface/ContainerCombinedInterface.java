package com.cells.blocks.combinedinterface;

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
import net.minecraft.world.World;

import appeng.api.implementations.guiobjects.IGuiItem;
import appeng.api.parts.IPart;
import appeng.api.storage.data.IAEStack;
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

import com.cells.blocks.interfacebase.AbstractResourceInterfaceLogic;
import com.cells.blocks.interfacebase.IInterfaceLogic;
import com.cells.blocks.interfacebase.IResourceInterfaceLogic;
import com.cells.blocks.interfacebase.ISizeOverrideContainer;
import com.cells.gui.IToolboxContainer;
import com.cells.gui.overlay.ServerMessageHelper;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.packets.PacketSyncSlotSizeOverride;
import com.cells.network.sync.IQuickAddFilterContainer;
import com.cells.network.sync.IResourceSyncContainer;
import com.cells.network.sync.IStorageSyncContainer;
import com.cells.network.sync.PacketResourceSlot;
import com.cells.network.sync.PacketStorageSync;
import com.cells.network.sync.ResourceType;


/**
 * Container for Combined Import/Export Interface GUIs.
 * <p>
 * Manages multiple resource-type logics (item, fluid, gas, essentia) behind a single container.
 * Only the active tab's filters are synced to clients; switching tabs invalidates the cache
 * and triggers a full re-sync.
 * <p>
 * Cannot extend {@link com.cells.blocks.interfacebase.AbstractContainerInterface} because that
 * class is generic over a single resource type. Instead, this container directly extends
 * {@link AEBaseContainer} and handles filter sync manually using raw-typed
 * {@link IResourceInterfaceLogic} accessors.
 */
public class ContainerCombinedInterface extends AEBaseContainer
    implements IResourceSyncContainer, IQuickAddFilterContainer, IStorageSyncContainer, ISizeOverrideContainer,
    IToolboxContainer {

    private final ICombinedInterfaceHost host;

    /**
     * Server-side cache for filter sync. Tracks what was sent to clients.
     * Keyed by absolute slot index, values are raw filter objects (IAEItemStack, IAEFluidStack, etc.)
     */
    private final Map<Integer, Object> serverFilterCache = new HashMap<>();

    /**
     * Server-side cache for storage sync. Tracks what was sent to clients.
     * Values are IAEStack instances with identity+amount, or null for empty slots.
     */
    private final Map<Integer, Object> serverStorageCache = new HashMap<>();

    /** Client-side per-slot size overrides (received via sync packets). */
    private final Map<Integer, Long> maxSlotSizeOverrides = new HashMap<>();

    /** Server-side cache to track what was last sent to clients for maxSlotSizeOverrides. */
    private final Map<Integer, Long> serverSizeOverrideCache = new HashMap<>();

    // Network tool ("toolbox") support
    private int toolboxSlot;
    private NetworkToolViewer toolboxInventory;

    // ================================= @GuiSync fields =================================
    // These are server→client synced by AE2's SyncData mechanism.

    /** Ordinal of the active ResourceType tab. Synced so client can detect tab switches. */
    @GuiSync(0)
    public int activeTabOrdinal = ResourceType.ITEM.ordinal();

    @GuiSync(1)
    public long maxSlotSize;

    @GuiSync(2)
    public long pollingRate = 0;

    @GuiSync(3)
    public int currentPage = 0;

    @GuiSync(4)
    public int totalPages = 1;

    // ================================= Constructors =================================

    /**
     * Constructor for tile entity hosts.
     */
    public ContainerCombinedInterface(final InventoryPlayer ip, final TileEntity tile) {
        this(ip, (ICombinedInterfaceHost) tile, tile);
    }

    /**
     * Constructor for part hosts.
     */
    public ContainerCombinedInterface(final InventoryPlayer ip, final IPart part) {
        this(ip, (ICombinedInterfaceHost) part, part);
    }

    /**
     * Common constructor.
     */
    private ContainerCombinedInterface(
            final InventoryPlayer ip,
            final ICombinedInterfaceHost host,
            final Object anchor
    ) {
        super(
            ip,
            anchor instanceof TileEntity ? (TileEntity) anchor : null,
            anchor instanceof IPart ? (IPart) anchor : null
        );
        this.host = host;

        // Initialize @GuiSync fields from the active tab's state
        IInterfaceLogic activeLogic = getActiveLogic();
        this.activeTabOrdinal = host.getActiveTab().ordinal();
        this.maxSlotSize = activeLogic.getMaxSlotSize();
        this.pollingRate = activeLogic.getPollingRate();
        this.currentPage = activeLogic.getCurrentPage();
        this.totalPages = activeLogic.getTotalPages();

        // Set up toolbox (network tool)
        this.setupToolbox(anchor);

        // Add upgrade slots (shared inventory across all logics)
        AppEngInternalInventory upgradeInv = host.getItemLogic().getUpgradeInventory();
        for (int i = 0; i < upgradeInv.getSlots(); i++) {
            this.addSlotToContainer(new SlotUpgrade(upgradeInv, i, 186, 25 + i * 18));
        }

        // Bind player inventory
        this.bindPlayerInventory(ip, 0, 174);
    }

    // ================================= Toolbox Support =================================

    private void setupToolbox(Object anchor) {
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

    public boolean hasToolbox() {
        return this.toolboxInventory != null;
    }

    private void checkToolbox() {
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

    // ================================= Tab Management =================================

    /**
     * Get the currently active resource type tab.
     */
    public ResourceType getActiveTab() {
        return this.host.getActiveTab();
    }

    /**
     * Get the logic for the currently active tab.
     */
    @Nonnull
    private IInterfaceLogic getActiveLogic() {
        IInterfaceLogic logic = this.host.getLogicForType(this.host.getActiveTab());
        // Fallback to item if somehow null (should never happen)
        return logic != null ? logic : this.host.getItemLogic();
    }

    /**
     * Switch to a different resource type tab.
     * Called by {@link com.cells.network.packets.PacketSwitchTab} on the server.
     * Invalidates the server-side filter cache to trigger a full re-sync.
     */
    public void switchTab(ResourceType newTab) {
        if (!this.host.getAvailableTabs().contains(newTab)) return;
        if (this.host.getActiveTab() == newTab) return;

        this.host.setActiveTab(newTab);

        // Update @GuiSync fields from the new tab's logic
        IInterfaceLogic logic = getActiveLogic();
        this.activeTabOrdinal = newTab.ordinal();
        this.maxSlotSize = logic.getMaxSlotSize();
        this.pollingRate = logic.getPollingRate();
        this.currentPage = logic.getCurrentPage();
        this.totalPages = logic.getTotalPages();

        // Clear caches to force full re-sync for the new tab
        this.serverFilterCache.clear();
        this.serverStorageCache.clear();
    }

    // ================================= Accessors =================================

    public ICombinedInterfaceHost getHost() {
        return this.host;
    }

    public void setMaxSlotSize(long size) {
        getActiveLogic().setMaxSlotSize(size);
    }

    // ================================= Per-Slot Size Overrides (ISizeOverrideContainer) =================================

    @Override
    public void receiveMaxSlotSizeOverridesync(int slot, long size) {
        if (size < 0) {
            this.maxSlotSizeOverrides.remove(slot);
        } else {
            this.maxSlotSizeOverrides.put(slot, size);
        }
    }

    @Override
    public long getEffectiveMaxSlotSize(int slot) {
        Long override = this.maxSlotSizeOverrides.get(slot);
        return override != null ? override : this.maxSlotSize;
    }

    @Override
    public long getSlotSizeOverride(int slot) {
        Long override = this.maxSlotSizeOverrides.get(slot);
        return override != null ? override : -1;
    }

    public void setPollingRate(int ticks) {
        getActiveLogic().setPollingRate(ticks);
    }

    public void clearFilters() {
        getActiveLogic().clearFilters();
    }

    // ================================= Pagination =================================

    public void setCurrentPage(int page) {
        IInterfaceLogic logic = getActiveLogic();
        int newPage = Math.max(0, Math.min(page, logic.getTotalPages() - 1));
        this.currentPage = newPage;
        logic.setCurrentPage(newPage);
    }

    public void nextPage() {
        if (this.currentPage < this.totalPages - 1) setCurrentPage(this.currentPage + 1);
    }

    public void prevPage() {
        if (this.currentPage > 0) setCurrentPage(this.currentPage - 1);
    }

    // ================================= Sync =================================

    @Override
    public void detectAndSendChanges() {
        // Sync @GuiSync fields from active tab's logic BEFORE super processes them.
        // AE2's SyncData sends on first tick (clientVersion == null), so fields must
        // be current before super.detectAndSendChanges().
        IInterfaceLogic activeLogic = getActiveLogic();
        ResourceType activeTab = this.host.getActiveTab();

        int tabOrd = activeTab.ordinal();
        if (this.activeTabOrdinal != tabOrd) this.activeTabOrdinal = tabOrd;
        if (this.maxSlotSize != activeLogic.getMaxSlotSize()) this.maxSlotSize = activeLogic.getMaxSlotSize();
        if (this.pollingRate != activeLogic.getPollingRate()) this.pollingRate = activeLogic.getPollingRate();
        if (this.currentPage != activeLogic.getCurrentPage()) this.currentPage = activeLogic.getCurrentPage();
        if (this.totalPages != activeLogic.getTotalPages()) this.totalPages = activeLogic.getTotalPages();

        super.detectAndSendChanges();

        this.checkToolbox();

        // Server-side filter sync for the ACTIVE tab only
        if (!Platform.isServer()) return;

        syncActiveTabFilters(activeTab);
        syncActiveTabStorage(activeTab);
        syncmaxSlotSizeOverrides();
    }

    /**
     * Sync the active tab's filters to all listeners.
     * Uses raw-typed access since the container handles all resource types.
     */
    @SuppressWarnings("rawtypes")
    private void syncActiveTabFilters(ResourceType activeTab) {
        IInterfaceLogic logic = getActiveLogic();
        if (!(logic instanceof IResourceInterfaceLogic)) return;

        IResourceInterfaceLogic rawLogic = (IResourceInterfaceLogic) logic;
        final int filterSlots = AbstractResourceInterfaceLogic.FILTER_SLOTS;

        for (int i = 0; i < filterSlots; i++) {
            Object current = rawLogic.getFilter(i);
            Object cached = this.serverFilterCache.get(i);

            if (!filtersEqual(current, cached)) {
                this.serverFilterCache.put(i, copyFilter(activeTab, current));

                for (IContainerListener listener : this.listeners) {
                    if (listener instanceof EntityPlayerMP) {
                        CellsNetworkHandler.INSTANCE.sendTo(
                            new PacketResourceSlot(activeTab, i, current),
                            (EntityPlayerMP) listener
                        );
                    }
                }
            }
        }
    }

    /**
     * Sync the active tab's storage to all listeners.
     * Uses raw-typed access since the container handles all resource types.
     */
    @SuppressWarnings("rawtypes")
    private void syncActiveTabStorage(ResourceType activeTab) {
        IInterfaceLogic logic = getActiveLogic();
        if (!(logic instanceof IResourceInterfaceLogic)) return;

        IResourceInterfaceLogic rawLogic = (IResourceInterfaceLogic) logic;
        final int filterSlots = AbstractResourceInterfaceLogic.FILTER_SLOTS;

        for (int i = 0; i < filterSlots; i++) {
            Object current = rawLogic.getStorageAsAEStack(i);
            Object cached = this.serverStorageCache.get(i);

            if (!storageEqual(current, cached)) {
                this.serverStorageCache.put(i, ResourceType.copyStack(current));

                for (IContainerListener listener : this.listeners) {
                    if (listener instanceof EntityPlayerMP) {
                        CellsNetworkHandler.INSTANCE.sendTo(
                            new PacketStorageSync(activeTab, i, current),
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

        if (!Platform.isServer() || !(listener instanceof EntityPlayerMP)) return;

        // Send full filter and storage state of the active tab to the new listener
        sendFullSync((EntityPlayerMP) listener);
    }

    /**
     * Send all filters and storage for the active tab to a specific listener.
     */
    @SuppressWarnings("rawtypes")
    private void sendFullSync(EntityPlayerMP listener) {
        ResourceType activeTab = this.host.getActiveTab();
        IInterfaceLogic logic = getActiveLogic();
        if (!(logic instanceof IResourceInterfaceLogic)) return;

        IResourceInterfaceLogic rawLogic = (IResourceInterfaceLogic) logic;
        final int filterSlots = AbstractResourceInterfaceLogic.FILTER_SLOTS;
        Map<Integer, Object> fullFilterMap = new HashMap<>();
        Map<Integer, Object> fullStorageMap = new HashMap<>();

        for (int i = 0; i < filterSlots; i++) {
            Object filter = rawLogic.getFilter(i);
            fullFilterMap.put(i, filter);
            this.serverFilterCache.put(i, copyFilter(activeTab, filter));

            Object storage = rawLogic.getStorageAsAEStack(i);
            fullStorageMap.put(i, storage);
            this.serverStorageCache.put(i, ResourceType.copyStack(storage));
        }

        CellsNetworkHandler.INSTANCE.sendTo(new PacketResourceSlot(activeTab, fullFilterMap), listener);
        CellsNetworkHandler.INSTANCE.sendTo(new PacketStorageSync(activeTab, fullStorageMap), listener);

        // Send full size overrides to new listener
        Map<Integer, Long> hostOverrides = logic.getmaxSlotSizeOverrides();
        for (Map.Entry<Integer, Long> entry : hostOverrides.entrySet()) {
            CellsNetworkHandler.INSTANCE.sendTo(
                new PacketSyncSlotSizeOverride(entry.getKey(), entry.getValue()), listener
            );
            this.serverSizeOverrideCache.put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Sync per-slot size overrides from the active tab's logic to clients.
     * Compares against serverSizeOverrideCache and sends diffs.
     */
    private void syncmaxSlotSizeOverrides() {
        IInterfaceLogic activeLogic = getActiveLogic();
        Map<Integer, Long> hostOverrides = activeLogic.getmaxSlotSizeOverrides();

        // Check for new or changed overrides
        for (Map.Entry<Integer, Long> entry : hostOverrides.entrySet()) {
            int slot = entry.getKey();
            long size = entry.getValue();
            Long cached = this.serverSizeOverrideCache.get(slot);

            if (cached == null || cached != size) {
                this.serverSizeOverrideCache.put(slot, size);
                for (IContainerListener listener : this.listeners) {
                    if (listener instanceof EntityPlayerMP) {
                        CellsNetworkHandler.INSTANCE.sendTo(
                            new PacketSyncSlotSizeOverride(slot, size),
                            (EntityPlayerMP) listener
                        );
                    }
                }
            }
        }

        // Check for removed overrides
        this.serverSizeOverrideCache.entrySet().removeIf(entry -> {
            if (!hostOverrides.containsKey(entry.getKey())) {
                for (IContainerListener listener : this.listeners) {
                    if (listener instanceof EntityPlayerMP) {
                        CellsNetworkHandler.INSTANCE.sendTo(
                            new PacketSyncSlotSizeOverride(entry.getKey(), -1),
                            (EntityPlayerMP) listener
                        );
                    }
                }
                return true;
            }
            return false;
        });
    }

    // ================================= IResourceSyncContainer =================================

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void receiveResourceSlots(ResourceType type, Map<Integer, Object> resources) {
        // Only handle the active tab's type
        ResourceType activeTab = this.host.getActiveTab();
        if (type != activeTab) return;

        IInterfaceLogic logic = getActiveLogic();
        if (!(logic instanceof IResourceInterfaceLogic)) return;

        IResourceInterfaceLogic rawLogic = (IResourceInterfaceLogic) logic;

        // Client-side: update filters directly
        if (this.host.getHostWorld() != null && this.host.getHostWorld().isRemote) {
            for (Map.Entry<Integer, Object> entry : resources.entrySet()) {
                rawLogic.setFilter(entry.getKey(), entry.getValue());
            }
            return;
        }

        // Server-side: validate and apply with duplicate protection
        final boolean isExport = this.host.isExport();
        EntityPlayer player = getPlayerFromListeners();

        for (Map.Entry<Integer, Object> entry : resources.entrySet()) {
            int slot = entry.getKey();
            Object stack = entry.getValue();

            if (slot < 0 || slot >= AbstractResourceInterfaceLogic.FILTER_SLOTS) continue;

            if (stack == null) {
                if (!isExport && !rawLogic.isStorageEmpty(slot)) {
                    if (player instanceof EntityPlayerMP) {
                        ServerMessageHelper.warning((EntityPlayerMP) player, "message.cells.storage_not_empty");
                    }
                    continue;
                }
                rawLogic.setFilter(slot, null);
                continue;
            }

            if (!isExport && !rawLogic.isStorageEmpty(slot)) {
                if (player instanceof EntityPlayerMP) {
                    ServerMessageHelper.warning((EntityPlayerMP) player, "message.cells.storage_not_empty");
                }
                continue;
            }

            // Duplicate check: scan all other slots for a matching filter
            boolean isDuplicate = false;
            for (int i = 0; i < AbstractResourceInterfaceLogic.FILTER_SLOTS; i++) {
                if (i == slot) continue;
                Object existing = rawLogic.getFilter(i);
                if (existing != null && existing.equals(stack)) {
                    isDuplicate = true;
                    break;
                }
            }

            if (isDuplicate) {
                if (player instanceof EntityPlayerMP) {
                    ServerMessageHelper.warning((EntityPlayerMP) player, "message.cells.filter_duplicate");
                }
                continue;
            }

            rawLogic.setFilter(slot, stack);
        }

        logic.refreshFilterMap();
    }

    // ================================= IStorageSyncContainer =================================

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void receiveStorageSlots(ResourceType type, Map<Integer, Object> resources) {
        // Only handle the active tab's type
        ResourceType activeTab = this.host.getActiveTab();
        if (type != activeTab) return;

        // Storage sync is server→client only
        if (this.host.getHostWorld() == null || !this.host.getHostWorld().isRemote) return;

        IInterfaceLogic logic = getActiveLogic();
        if (!(logic instanceof IResourceInterfaceLogic)) return;

        IResourceInterfaceLogic rawLogic = (IResourceInterfaceLogic) logic;

        for (Map.Entry<Integer, Object> entry : resources.entrySet()) {
            rawLogic.setStorageFromAEStack(entry.getKey(), entry.getValue());
        }
    }

    // ================================= IQuickAddFilterContainer =================================

    @Override
    public ResourceType getQuickAddResourceType() {
        return this.host.getActiveTab();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public boolean isResourceInFilter(@Nonnull Object resource) {
        // Delegate to the active tab's logic
        // Since we don't have the typed key, use raw logic
        IInterfaceLogic logic = getActiveLogic();
        if (!(logic instanceof IResourceInterfaceLogic)) return false;

        IResourceInterfaceLogic rawLogic = (IResourceInterfaceLogic) logic;
        final int filterSlots = AbstractResourceInterfaceLogic.FILTER_SLOTS;

        // Linear scan since we don't have a typed key for the combined container
        for (int i = 0; i < filterSlots; i++) {
            Object existing = rawLogic.getFilter(i);
            if (existing != null && existing.equals(resource)) return true;
        }

        return false;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean quickAddToFilter(@Nonnull Object resource, @Nullable EntityPlayer player) {
        IInterfaceLogic logic = getActiveLogic();
        if (!(logic instanceof IResourceInterfaceLogic)) return false;

        IResourceInterfaceLogic rawLogic = (IResourceInterfaceLogic) logic;
        final boolean isExport = this.host.isExport();

        // Check for duplicates
        if (isResourceInFilter(resource)) {
            if (player instanceof EntityPlayerMP) {
                ServerMessageHelper.warning((EntityPlayerMP) player, "message.cells.filter_duplicate");
            }
            return false;
        }

        // Find first available slot
        int slot = findFirstAvailableSlot(rawLogic, isExport);
        if (slot < 0) {
            if (player instanceof EntityPlayerMP) {
                ServerMessageHelper.error((EntityPlayerMP) player, "message.cells.no_filter_space");
            }
            return false;
        }

        rawLogic.setFilter(slot, resource);
        logic.refreshFilterMap();
        return true;
    }

    @Override
    public String getTypeLocalizationKey() {
        ResourceType tab = this.host.getActiveTab();
        return "cells.type." + tab.name().toLowerCase();
    }

    @SuppressWarnings("rawtypes")
    private int findFirstAvailableSlot(IResourceInterfaceLogic rawLogic, boolean isExport) {
        final int effectiveSlots = getActiveLogic().getEffectiveFilterSlots();

        for (int i = 0; i < effectiveSlots; i++) {
            Object existing = rawLogic.getFilter(i);
            if (existing != null) continue;

            if (!isExport && !rawLogic.isStorageEmpty(i)) continue;

            return i;
        }

        return -1;
    }

    // ================================= Storage Interaction =================================

    /**
     * Handle inventory actions for storage slots.
     * Dispatches to the active tab's resource type for storage interactions.
     */
    @Override
    public void doAction(EntityPlayerMP player, InventoryAction action, int slot, long id) {
        final int slotsPerPage = AbstractResourceInterfaceLogic.SLOTS_PER_PAGE;

        if (slot >= 0 && slot < slotsPerPage) {
            int actualSlot = slot + (this.currentPage * slotsPerPage);

            if (action == InventoryAction.PICKUP_OR_SET_DOWN) {
                if (handleStorageInteraction(player, actualSlot, false)) return;
            }

            if (action == InventoryAction.SPLIT_OR_PLACE_SINGLE) {
                if (handleStorageInteraction(player, actualSlot, true)) return;
            }

            if (action == InventoryAction.SHIFT_CLICK) {
                if (handleStorageShiftClick(player, actualSlot)) return;
            }

            if (action == InventoryAction.EMPTY_ITEM && !this.host.isExport()) {
                if (handleEmptyItemAction(player, actualSlot)) return;
            }

            if (action == InventoryAction.FILL_ITEM && this.host.isExport()) {
                if (handleFillItemAction(player, actualSlot)) return;
            }
        }

        super.doAction(player, action, slot, id);
    }

    /**
     * Dispatch storage interactions to type-specific handlers.
     * For now, only item type supports direct storage interaction.
     * Fluid/gas/essentia use EMPTY_ITEM/FILL_ITEM actions instead.
     */
    private boolean handleStorageInteraction(EntityPlayerMP player, int storageSlot, boolean halfStack) {
        ResourceType activeTab = this.host.getActiveTab();

        // Only items support direct pickup/setdown on storage slots
        if (activeTab != ResourceType.ITEM) return false;

        // Delegate to the item interface's storage interaction
        return CombinedContainerItemHelper.handleStorageInteraction(
            this, this.host, player, storageSlot, halfStack
        );
    }

    private boolean handleStorageShiftClick(EntityPlayerMP player, int storageSlot) {
        ResourceType activeTab = this.host.getActiveTab();
        if (activeTab != ResourceType.ITEM) return false;

        return CombinedContainerItemHelper.handleStorageShiftClick(
            this, this.host, player, storageSlot
        );
    }

    /**
     * Handle pouring from held item into tank. Only relevant for fluid/gas tabs.
     */
    private boolean handleEmptyItemAction(EntityPlayerMP player, int slot) {
        ResourceType activeTab = this.host.getActiveTab();

        if (activeTab == ResourceType.FLUID) {
            return CombinedContainerFluidHelper.handleEmptyItemAction(
                this, this.host, player, slot
            );
        }

        // TODO: Gas pour support when gas tab is active
        return false;
    }

    /**
     * Handle filling held item from tank. Only relevant for fluid/gas tabs.
     */
    private boolean handleFillItemAction(EntityPlayerMP player, int slot) {
        ResourceType activeTab = this.host.getActiveTab();

        if (activeTab == ResourceType.FLUID) {
            return CombinedContainerFluidHelper.handleFillItemAction(
                this, this.host, player, slot
            );
        }

        // TODO: Gas fill support when gas tab is active
        return false;
    }

    // ================================= Shift-Click =================================

    @Override
    @Nonnull
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        if (player.world.isRemote) return ItemStack.EMPTY;
        if (slotIndex < 0 || slotIndex >= inventorySlots.size()) return ItemStack.EMPTY;

        Slot slot = inventorySlots.get(slotIndex);
        if (slot == null || !slot.getHasStack()) return ItemStack.EMPTY;

        ItemStack clickedStack = slot.getStack();
        if (clickedStack.isEmpty()) return ItemStack.EMPTY;

        boolean isFromPlayerInventory = (slot instanceof AppEngSlot)
            && ((AppEngSlot) slot).isPlayerSide();

        // Shift-clicking from container-side slots - move to player inventory
        if (!isFromPlayerInventory) return super.transferStackInSlot(player, slotIndex);

        // From player inventory: try upgrade slots first
        AppEngInternalInventory upgradeInv = this.host.getItemLogic().getUpgradeInventory();
        boolean insertedAny = false;

        for (int i = 0; i < upgradeInv.getSlots(); i++) {
            if (!upgradeInv.isItemValid(i, clickedStack)) continue;

            int previousCount = clickedStack.getCount();
            ItemStack remainder = upgradeInv.insertItem(i, clickedStack.copy(), false);
            if (remainder.getCount() < previousCount) {
                clickedStack.setCount(remainder.getCount());
                insertedAny = true;
                if (clickedStack.isEmpty()) break;
            }
        }

        if (insertedAny) {
            if (clickedStack.isEmpty()) slot.putStack(ItemStack.EMPTY);
            slot.onSlotChanged();
            this.detectAndSendChanges();
            return ItemStack.EMPTY;
        }

        // Priority 2: Try to add as filter for the active tab
        // Only item tab supports extracting filters from ItemStacks directly
        ResourceType activeTab = this.host.getActiveTab();
        if (activeTab == ResourceType.ITEM) {
            CombinedContainerItemHelper.tryAddItemFilter(this, this.host, clickedStack, player);
        } else if (activeTab == ResourceType.FLUID) {
            CombinedContainerFluidHelper.tryAddFluidFilter(this, this.host, clickedStack, player);
        }
        // Gas and essentia don't support shift-click filter from items

        return ItemStack.EMPTY;
    }

    // ================================= Upgrade slot change =================================

    @Override
    public void onSlotChange(final Slot s) {
        super.onSlotChange(s);

        if (s instanceof SlotUpgrade) {
            // Refresh upgrades for ALL logics since they share the inventory
            for (IInterfaceLogic logic : this.host.getAllLogics()) {
                logic.refreshUpgrades();
            }
        }
    }

    // ================================= Filter Helpers =================================

    /**
     * Compare two filter objects for equality.
     * Handles null comparisons and delegates to the object's equals method.
     */
    private static boolean filtersEqual(@Nullable Object a, @Nullable Object b) {
        if (a == null) return b == null;
        if (b == null) return false;
        return a.equals(b);
    }

    /**
     * Compare two storage stack objects for equality (identity + amount).
     * Uses {@link #filtersEqual} for identity comparison, plus amount check.
     */
    @SuppressWarnings("rawtypes")
    private static boolean storageEqual(@Nullable Object a, @Nullable Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (!filtersEqual(a, b)) return false;

        // All concrete types extend IAEStack, compare amounts
        return ((IAEStack) a).getStackSize() == ((IAEStack) b).getStackSize();
    }

    /**
     * Copy a filter object for caching.
     * Dispatches to ResourceType.copyStack which handles all IAEStack types.
     */
    @Nullable
    private static Object copyFilter(ResourceType type, @Nullable Object filter) {
        return ResourceType.copyStack(filter);
    }

    // ================================= Helpers =================================

    /**
     * Get the player from container listeners for sending feedback messages.
     */
    @Nullable
    private EntityPlayer getPlayerFromListeners() {
        for (IContainerListener listener : this.listeners) {
            if (listener instanceof EntityPlayer) return (EntityPlayer) listener;
        }
        return null;
    }

    // ================================= Upgrade Slot Class =================================

    /**
     * Package-visible wrapper for AEBaseContainer.updateHeld, needed by helper classes.
     */
    void sendHeldItemUpdate(EntityPlayerMP player) {
        this.updateHeld(player);
    }

    /**
     * Common upgrade slot implementation (matches AbstractContainerInterface.SlotUpgrade).
     */
    static class SlotUpgrade extends SlotNormal {
        SlotUpgrade(AppEngInternalInventory inv, int idx, int x, int y) {
            super(inv, idx, x, y);
            this.setIIcon(13 * 16 + 15); // UPGRADES icon
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
