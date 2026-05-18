package com.cells.blocks.iointerface;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.FluidActionResult;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.fluids.capability.FluidTankProperties;
import net.minecraftforge.fml.common.Optional;

import appeng.api.AEApi;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.implementations.guiobjects.IGuiItem;
import appeng.api.parts.IPart;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.SlotNormal;
import appeng.container.slot.SlotRestrictedInput;
import appeng.fluids.util.AEFluidStack;
import appeng.helpers.InventoryAction;
import appeng.items.contents.NetworkToolViewer;
import appeng.items.tools.ToolNetworkTool;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.Platform;

import com.cells.blocks.interfacebase.AbstractResourceInterfaceLogic;
import com.cells.blocks.interfacebase.IInterfaceLogic;
import com.cells.blocks.interfacebase.IResourceInterfaceLogic;
import com.cells.blocks.interfacebase.ISizeOverrideContainer;
import com.cells.blocks.interfacebase.item.ItemInterfaceLogic;
import com.cells.blocks.interfacebase.fluid.FluidInterfaceLogic;
import com.cells.gui.IToolboxContainer;
import com.cells.gui.QuickAddHelper;
import com.cells.gui.overlay.ServerMessageHelper;
import com.cells.items.ItemRecoveryContainer;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.packets.PacketSyncSlotSizeOverride;
import com.cells.network.sync.IQuickAddFilterContainer;
import com.cells.network.sync.IResourceSyncContainer;
import com.cells.network.sync.IStorageSyncContainer;
import com.cells.network.sync.PacketResourceSlot;
import com.cells.network.sync.PacketStorageSync;
import com.cells.network.sync.ResourceType;


/**
 * Container for I/O Interface GUIs (Item I/O, Fluid I/O, etc.).
 * <p>
 * Manages two logics of the same resource type (import + export) behind a single container.
 * Switching between Import and Export tabs invalidates caches and triggers a full re-sync.
 * Upgrade slots dynamically switch to the active tab's upgrade inventory.
 * <p>
 * Polling rate is shared between both directions. All other settings (filters, storage,
 * max slot size, upgrades, per-slot overrides) are per-direction.
 */
public class ContainerIOInterface extends AEBaseContainer
    implements IResourceSyncContainer, IQuickAddFilterContainer, IStorageSyncContainer, ISizeOverrideContainer,
    IToolboxContainer {

    private final IIOInterfaceHost host;

    /** Server-side cache for filter sync. */
    private final Map<Integer, Object> serverFilterCache = new HashMap<>();

    /** Server-side cache for storage sync. */
    private final Map<Integer, Object> serverStorageCache = new HashMap<>();

    /** Client-side per-slot size overrides. */
    private final Map<Integer, Long> maxSlotSizeOverrides = new HashMap<>();

    /** Server-side cache for size overrides. */
    private final Map<Integer, Long> serverSizeOverrideCache = new HashMap<>();

    /**
     * Switchable upgrade inventory that delegates to the active tab's logic.
     * When the direction tab changes, this wrapper's delegate is updated.
     */
    private final SwitchableUpgradeInventory switchableUpgradeInv;

    // Network tool ("toolbox") support
    private int toolboxSlot;
    private NetworkToolViewer toolboxInventory;

    // ================================= @GuiSync fields =================================

    /** Direction tab: 0=import, 1=export. */
    @GuiSync(0)
    public int activeDirectionTab = IIOInterfaceHost.TAB_IMPORT;

    @GuiSync(1)
    public long maxSlotSize;

    /** Polling rate (shared between both directions). */
    @GuiSync(2)
    public long pollingRate = 0;

    @GuiSync(3)
    public int currentPage = 0;

    @GuiSync(4)
    public int totalPages = 1;

    // ================================= Constructors =================================

    /** Constructor for tile entity hosts. */
    public ContainerIOInterface(final InventoryPlayer ip, final TileEntity tile) {
        this(ip, (IIOInterfaceHost) tile, tile);
    }

    /** Constructor for part hosts. */
    public ContainerIOInterface(final InventoryPlayer ip, final IPart part) {
        this(ip, (IIOInterfaceHost) part, part);
    }

    private ContainerIOInterface(
            final InventoryPlayer ip,
            final IIOInterfaceHost host,
            final Object anchor
    ) {
        super(
            ip,
            anchor instanceof TileEntity ? (TileEntity) anchor : null,
            anchor instanceof IPart ? (IPart) anchor : null
        );
        this.host = host;

        // Initialize @GuiSync fields from the active tab's state
        IInterfaceLogic activeLogic = host.getActiveLogic();
        this.activeDirectionTab = host.getActiveDirectionTab();
        this.maxSlotSize = activeLogic.getMaxSlotSize();
        this.pollingRate = activeLogic.getPollingRate();
        this.currentPage = activeLogic.getCurrentPage();
        this.totalPages = activeLogic.getTotalPages();

        // Create switchable upgrade inventory
        this.switchableUpgradeInv = new SwitchableUpgradeInventory(getActiveLogicUpgradeInv());

        // Set up toolbox
        this.setupToolbox(anchor);

        // Add upgrade slots (backed by the switchable inventory)
        int upgradeSlots = this.switchableUpgradeInv.getSlots();
        for (int i = 0; i < upgradeSlots; i++) {
            this.addSlotToContainer(new SlotUpgrade(this.switchableUpgradeInv, i, 186, 25 + i * 18));
        }

        // Bind player inventory
        this.bindPlayerInventory(ip, 0, 174);
    }

    // ================================= Toolbox Support =================================

    private void setupToolbox(Object anchor) {
        if (this.host.getHostWorld() == null || this.host.getHostPos() == null) return;

        for (int x = 0; x < this.getPlayerInv().getSizeInventory(); x++) {
            final ItemStack pii = this.getPlayerInv().getStackInSlot(x);
            if (!pii.isEmpty() && pii.getItem() instanceof ToolNetworkTool) {
                this.lockPlayerInventorySlot(x);
                this.toolboxSlot = x;
                this.toolboxInventory = (NetworkToolViewer) ((IGuiItem) pii.getItem())
                    .getGuiObject(pii, this.host.getHostWorld(), this.host.getHostPos());
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
     * Switch to a different direction tab (server-side).
     * Called by {@link com.cells.network.packets.PacketSwitchTab} on the server.
     * <p>
     * The early-return check uses the container's @GuiSync field (which is
     * per-side, independent between client and server) rather than the host's
     * field, so two separate container instances on the same logical side cannot
     * race each other.
     */
    public void switchTab(int newTab) {
        if (newTab != IIOInterfaceHost.TAB_IMPORT && newTab != IIOInterfaceHost.TAB_EXPORT) return;
        if (this.activeDirectionTab == newTab) return;

        this.host.setActiveDirectionTab(newTab);

        // Update @GuiSync fields from the new tab's logic
        IInterfaceLogic logic = this.host.getActiveLogic();
        this.activeDirectionTab = newTab;
        this.maxSlotSize = logic.getMaxSlotSize();
        // Polling rate is shared, no change needed
        this.currentPage = logic.getCurrentPage();
        this.totalPages = logic.getTotalPages();

        // Switch the upgrade inventory delegate
        this.switchableUpgradeInv.switchTo(getActiveLogicUpgradeInv());

        // Clear caches to force full re-sync
        this.serverFilterCache.clear();
        this.serverStorageCache.clear();
        this.serverSizeOverrideCache.clear();
        this.maxSlotSizeOverrides.clear();
    }

    /**
     * Client-side optimistic tab switch.
     * <p>
     * Updates the container's tab field, switches the upgrade inventory delegate,
     * and ALSO updates the client-side host's {@code activeDirectionTab}. The host
     * mutation is essential: many GUI/container paths dispatch via
     * {@code host.getActiveLogic()} / {@code host.isExport()} (slot rendering,
     * filter/storage sync receivers, title, controls help, polling-rate strings,
     * etc.).
     * <p>
     * This is safe even in singleplayer: the integrated server's TileEntity/IPart
     * is a distinct instance from the client's (each side has its own world), so
     * mutating the client host does not affect the server. The server still
     * processes its own {@link #switchTab(int)} when {@code PacketSwitchTab} arrives.
     */
    public void onClientTabSwitch(int newTab) {
        if (newTab != IIOInterfaceHost.TAB_IMPORT && newTab != IIOInterfaceHost.TAB_EXPORT) return;
        if (this.activeDirectionTab == newTab) return;

        this.activeDirectionTab = newTab;
        // Update host first so getActiveLogicUpgradeInv()/getActiveLogic() return the new tab.
        this.host.setActiveDirectionTab(newTab);
        this.switchableUpgradeInv.switchTo(getActiveLogicUpgradeInv());
    }

    /**
     * @return The IItemHandler backing the upgrade slots. Reflects the
     *         currently-active tab's upgrade inventory contents (since the
     *         delegate is switched on tab change).
     */
    @Nonnull
    public IItemHandler getUpgradeInventoryView() {
        return this.switchableUpgradeInv;
    }

    // ================================= Accessors =================================

    public IIOInterfaceHost getHost() {
        return this.host;
    }

    @Nonnull
    private IInterfaceLogic getActiveLogic() {
        return this.host.getActiveLogic();
    }

    private AppEngInternalInventory getActiveLogicUpgradeInv() {
        IInterfaceLogic logic = getActiveLogic();
        if (logic instanceof AbstractResourceInterfaceLogic) {
            return ((AbstractResourceInterfaceLogic<?, ?, ?>) logic).getUpgradeInventory();
        }
        // Fallback: import logic's upgrade inventory
        IInterfaceLogic importLogic = this.host.getImportLogic();
        if (importLogic instanceof AbstractResourceInterfaceLogic) {
            return ((AbstractResourceInterfaceLogic<?, ?, ?>) importLogic).getUpgradeInventory();
        }
        return new AppEngInternalInventory(null, 0);
    }

    public void setMaxSlotSize(long size) {
        getActiveLogic().setMaxSlotSize(size);
    }

    public void setPollingRate(int ticks) {
        // Polling rate is shared - set on both logics
        this.host.setPollingRate(ticks);
    }

    public void clearFilters() {
        getActiveLogic().clearFilters();
    }

    // ================================= Per-Slot Size Overrides =================================

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
        IInterfaceLogic activeLogic = getActiveLogic();
        int tab = this.host.getActiveDirectionTab();

        if (this.activeDirectionTab != tab) this.activeDirectionTab = tab;
        if (this.maxSlotSize != activeLogic.getMaxSlotSize()) this.maxSlotSize = activeLogic.getMaxSlotSize();
        if (this.pollingRate != activeLogic.getPollingRate()) this.pollingRate = activeLogic.getPollingRate();
        if (this.currentPage != activeLogic.getCurrentPage()) this.currentPage = activeLogic.getCurrentPage();
        if (this.totalPages != activeLogic.getTotalPages()) this.totalPages = activeLogic.getTotalPages();

        super.detectAndSendChanges();
        this.checkToolbox();

        if (!Platform.isServer()) return;

        ResourceType resType = this.host.getResourceType();
        syncActiveTabFilters(resType);
        syncActiveTabStorage(resType);
        syncMaxSlotSizeOverrides();
    }

    @SuppressWarnings("rawtypes")
    private void syncActiveTabFilters(ResourceType resType) {
        IInterfaceLogic logic = getActiveLogic();
        if (!(logic instanceof IResourceInterfaceLogic)) return;

        IResourceInterfaceLogic rawLogic = (IResourceInterfaceLogic) logic;
        final int filterSlots = AbstractResourceInterfaceLogic.FILTER_SLOTS;

        for (int i = 0; i < filterSlots; i++) {
            Object current = rawLogic.getFilter(i);
            Object cached = this.serverFilterCache.get(i);

            if (!filtersEqual(current, cached)) {
                this.serverFilterCache.put(i, ResourceType.copyStack(current));

                for (IContainerListener listener : this.listeners) {
                    if (listener instanceof EntityPlayerMP) {
                        CellsNetworkHandler.INSTANCE.sendTo(
                            new PacketResourceSlot(resType, i, current),
                            (EntityPlayerMP) listener
                        );
                    }
                }
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private void syncActiveTabStorage(ResourceType resType) {
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
                            new PacketStorageSync(resType, i, current),
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
        sendFullSync((EntityPlayerMP) listener);
    }

    @SuppressWarnings("rawtypes")
    private void sendFullSync(EntityPlayerMP listener) {
        ResourceType resType = this.host.getResourceType();
        IInterfaceLogic logic = getActiveLogic();
        if (!(logic instanceof IResourceInterfaceLogic)) return;

        IResourceInterfaceLogic rawLogic = (IResourceInterfaceLogic) logic;
        final int filterSlots = AbstractResourceInterfaceLogic.FILTER_SLOTS;
        Map<Integer, Object> fullFilterMap = new HashMap<>();
        Map<Integer, Object> fullStorageMap = new HashMap<>();

        for (int i = 0; i < filterSlots; i++) {
            Object filter = rawLogic.getFilter(i);
            fullFilterMap.put(i, filter);
            this.serverFilterCache.put(i, ResourceType.copyStack(filter));

            Object storage = rawLogic.getStorageAsAEStack(i);
            fullStorageMap.put(i, storage);
            this.serverStorageCache.put(i, ResourceType.copyStack(storage));
        }

        CellsNetworkHandler.INSTANCE.sendTo(new PacketResourceSlot(resType, fullFilterMap), listener);
        CellsNetworkHandler.INSTANCE.sendTo(new PacketStorageSync(resType, fullStorageMap), listener);

        // Send full size overrides
        Map<Integer, Long> hostOverrides = logic.getmaxSlotSizeOverrides();
        for (Map.Entry<Integer, Long> entry : hostOverrides.entrySet()) {
            CellsNetworkHandler.INSTANCE.sendTo(
                new PacketSyncSlotSizeOverride(entry.getKey(), entry.getValue()), listener
            );
            this.serverSizeOverrideCache.put(entry.getKey(), entry.getValue());
        }
    }

    private void syncMaxSlotSizeOverrides() {
        IInterfaceLogic activeLogic = getActiveLogic();
        Map<Integer, Long> hostOverrides = activeLogic.getmaxSlotSizeOverrides();

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
        ResourceType resType = this.host.getResourceType();
        if (type != resType) return;

        IInterfaceLogic logic = getActiveLogic();
        if (!(logic instanceof IResourceInterfaceLogic)) return;

        IResourceInterfaceLogic rawLogic = (IResourceInterfaceLogic) logic;

        if (this.host.getHostWorld() != null && this.host.getHostWorld().isRemote) {
            for (Map.Entry<Integer, Object> entry : resources.entrySet()) {
                rawLogic.setFilter(entry.getKey(), entry.getValue());
            }
            return;
        }

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
        ResourceType resType = this.host.getResourceType();
        if (type != resType) return;
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
        return this.host.getResourceType();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public boolean isResourceInFilter(@Nonnull Object resource) {
        IInterfaceLogic logic = getActiveLogic();
        if (!(logic instanceof IResourceInterfaceLogic)) return false;

        IResourceInterfaceLogic rawLogic = (IResourceInterfaceLogic) logic;
        final int filterSlots = AbstractResourceInterfaceLogic.FILTER_SLOTS;

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

        if (isResourceInFilter(resource)) {
            if (player instanceof EntityPlayerMP) {
                ServerMessageHelper.warning((EntityPlayerMP) player, "message.cells.filter_duplicate");
            }
            return false;
        }

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
        return "cells.type." + this.host.getResourceType().name().toLowerCase();
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

    @Override
    public void doAction(EntityPlayerMP player, InventoryAction action, int slot, long id) {
        final int slotsPerPage = AbstractResourceInterfaceLogic.SLOTS_PER_PAGE;

        if (slot >= 0 && slot < slotsPerPage) {
            int actualSlot = slot + (this.currentPage * slotsPerPage);
            ResourceType resType = this.host.getResourceType();

            if (action == InventoryAction.PICKUP_OR_SET_DOWN) {
                if (resType == ResourceType.ITEM && handleItemStorageInteraction(player, actualSlot, false)) return;
            }

            if (action == InventoryAction.SPLIT_OR_PLACE_SINGLE) {
                if (resType == ResourceType.ITEM && handleItemStorageInteraction(player, actualSlot, true)) return;
            }

            if (action == InventoryAction.SHIFT_CLICK) {
                if (resType == ResourceType.ITEM && handleItemStorageShiftClick(player, actualSlot)) return;
            }

            if (action == InventoryAction.EMPTY_ITEM && !this.host.isExport()) {
                if (resType == ResourceType.FLUID && handleFluidEmptyItem(player, actualSlot)) return;
                if (resType == ResourceType.GAS && handleGasEmptyItem(player, actualSlot)) return;
                if (resType == ResourceType.ESSENTIA && handleEssentiaEmptyItem(player, actualSlot)) return;
            }

            if (action == InventoryAction.FILL_ITEM && this.host.isExport()) {
                if (resType == ResourceType.FLUID && handleFluidFillItem(player, actualSlot)) return;
                if (resType == ResourceType.GAS && handleGasFillItem(player, actualSlot)) return;
                if (resType == ResourceType.ESSENTIA && handleEssentiaFillItem(player, actualSlot)) return;
            }
        }

        super.doAction(player, action, slot, id);
    }

    /**
     * Handle direct item storage interaction (pickup/setdown).
     * Only works when active tab is item type.
     */
    private boolean handleItemStorageInteraction(EntityPlayerMP player, int storageSlot, boolean halfStack) {
        IInterfaceLogic logic = getActiveLogic();
        if (!(logic instanceof ItemInterfaceLogic)) return false;

        ItemInterfaceLogic itemLogic = (ItemInterfaceLogic) logic;
        IItemHandlerModifiable storage = itemLogic.getStorageInventory();
        if (storageSlot < 0 || storageSlot >= storage.getSlots()) return false;

        ItemStack held = player.inventory.getItemStack();
        ItemStack stored = storage.getStackInSlot(storageSlot);
        boolean isExport = this.host.isExport();

        if (held.isEmpty()) {
            if (isExport && !stored.isEmpty()) {
                long storedAmount = itemLogic.getSlotAmount(storageSlot);
                int extractAmount;
                if (halfStack) {
                    extractAmount = (int) Math.max(1, Math.min(storedAmount / 2, 32));
                } else {
                    extractAmount = (int) Math.min(storedAmount, 64);
                }

                ItemStack toExtract = stored.copy();
                toExtract.setCount(extractAmount);

                itemLogic.adjustSlotAmount(storageSlot, -extractAmount);
                player.inventory.setItemStack(toExtract);
                this.updateHeld(player);
                itemLogic.refreshFilterMap();
            }
            return true;
        }

        if (isExport) return true;

        ItemStack transferStack = ItemRecoveryContainer.getHeldItemTransferStack(held);
        if (transferStack == null || transferStack.isEmpty()) return true;

        if (!itemLogic.isItemValidForSlot(storageSlot, transferStack)) return true;

        long requestedAmount = ItemRecoveryContainer.getHeldItemTransferAmount(held, halfStack);
        if (requestedAmount <= 0) return true;

        if (stored.isEmpty()) {
            long toTransfer = Math.min(requestedAmount, itemLogic.getEffectiveMaxSlotSize(storageSlot));
            if (toTransfer <= 0) return true;

            int initialAmount = (int) Math.min(toTransfer, Integer.MAX_VALUE);
            ItemStack toInsert = transferStack.copy();
            toInsert.setCount(initialAmount);
            storage.setStackInSlot(storageSlot, toInsert);

            long remainingToInsert = toTransfer - initialAmount;
            if (remainingToInsert > 0) itemLogic.adjustSlotAmount(storageSlot, remainingToInsert);

            ItemRecoveryContainer.consumeHeldTransfer(player, held, toTransfer);
            this.updateHeld(player);
            itemLogic.refreshFilterMap();
            return true;
        }

        if (!ItemStack.areItemsEqual(transferStack, stored) || !ItemStack.areItemStackTagsEqual(transferStack, stored)) {
            return true;
        }

        long currentAmount = itemLogic.getSlotAmount(storageSlot);
        long space = itemLogic.getEffectiveMaxSlotSize(storageSlot) - currentAmount;
        long toTransfer = Math.min(requestedAmount, space);
        if (toTransfer > 0) {
            itemLogic.adjustSlotAmount(storageSlot, toTransfer);
            ItemRecoveryContainer.consumeHeldTransfer(player, held, toTransfer);
            this.updateHeld(player);
            itemLogic.refreshFilterMap();
        }

        return true;
    }

    private boolean handleItemStorageShiftClick(EntityPlayerMP player, int storageSlot) {
        if (!this.host.isExport()) return true;

        IInterfaceLogic logic = getActiveLogic();
        if (!(logic instanceof ItemInterfaceLogic)) return false;

        ItemInterfaceLogic itemLogic = (ItemInterfaceLogic) logic;
        IItemHandlerModifiable storage = itemLogic.getStorageInventory();
        if (storageSlot < 0 || storageSlot >= storage.getSlots()) return false;

        ItemStack stored = storage.getStackInSlot(storageSlot);
        if (stored.isEmpty()) return true;

        long remainingAmount = itemLogic.getSlotAmount(storageSlot);
        ItemStack template = stored.copy();
        int vanillaMax = template.getMaxStackSize();
        long totalTransferred = 0;

        for (int i = 0; i < player.inventory.mainInventory.size() && remainingAmount > 0; i++) {
            ItemStack invStack = player.inventory.mainInventory.get(i);

            if (invStack.isEmpty()) {
                int toInsert = (int) Math.min(remainingAmount, vanillaMax);
                ItemStack newStack = template.copy();
                newStack.setCount(toInsert);
                player.inventory.mainInventory.set(i, newStack);
                remainingAmount -= toInsert;
                totalTransferred += toInsert;
            } else if (ItemStack.areItemsEqual(invStack, template)
                    && ItemStack.areItemStackTagsEqual(invStack, template)) {
                int space = vanillaMax - invStack.getCount();
                int toTransfer = (int) Math.min(remainingAmount, space);
                if (toTransfer > 0) {
                    invStack.grow(toTransfer);
                    remainingAmount -= toTransfer;
                    totalTransferred += toTransfer;
                }
            }
        }

        if (totalTransferred > 0) {
            itemLogic.adjustSlotAmount(storageSlot, -totalTransferred);
        }

        itemLogic.refreshFilterMap();
        this.detectAndSendChanges();
        return true;
    }

    /**
     * Handle pouring fluid from held item into tank (import only).
     * Mirrors ContainerFluidInterface.handleEmptyItemAction.
     */
    private boolean handleFluidEmptyItem(EntityPlayerMP player, int slot) {
        IInterfaceLogic logic = getActiveLogic();
        if (!(logic instanceof FluidInterfaceLogic)) return false;

        FluidInterfaceLogic fluidLogic = (FluidInterfaceLogic) logic;

        if (slot < 0 || slot >= FluidInterfaceLogic.STORAGE_SLOTS) return false;

        final ItemStack held = player.inventory.getItemStack();
        if (held.isEmpty()) return false;

        if (ItemRecoveryContainer.isType(held, ItemRecoveryContainer.TYPE_FLUID)) {
            return handleRecoveryContainerFluidPouring(player, slot, held, fluidLogic);
        }

        ItemStack heldCopy = held.copy();
        heldCopy.setCount(1);
        IFluidHandlerItem fh = FluidUtil.getFluidHandler(heldCopy);
        if (fh == null) return false;

        // Check filter
        IAEFluidStack filterFluid = fluidLogic.getFilter(slot);

        FluidStack drainable = fh.drain(Integer.MAX_VALUE, false);
        if (drainable == null || drainable.amount <= 0) return false;
        if (filterFluid != null && !filterFluid.getFluidStack().isFluidEqual(drainable)) return false;

        int capacity = (int) Math.min(fluidLogic.getEffectiveMaxSlotSize(slot), Integer.MAX_VALUE);
        FluidStack currentTankFluid = fluidLogic.getFluidInTank(slot);
        if (currentTankFluid != null && !currentTankFluid.isFluidEqual(drainable)) return false;

        int currentAmount = currentTankFluid != null ? currentTankFluid.amount : 0;
        int spaceAvailable = capacity - currentAmount;
        if (spaceAvailable <= 0) return false;

        int heldAmount = held.getCount();
        for (int i = 0; i < heldAmount; i++) {
            currentTankFluid = fluidLogic.getFluidInTank(slot);
            currentAmount = currentTankFluid != null ? currentTankFluid.amount : 0;
            spaceAvailable = capacity - currentAmount;
            if (spaceAvailable <= 0) break;

            ItemStack copiedContainer = held.copy();
            copiedContainer.setCount(1);
            fh = FluidUtil.getFluidHandler(copiedContainer);
            if (fh == null) break;

            drainable = fh.drain(spaceAvailable, false);
            if (drainable == null || drainable.amount <= 0) break;

            int toInsert = Math.min(drainable.amount, spaceAvailable);
            FluidStack drained = fh.drain(toInsert, true);
            if (drained == null || drained.amount <= 0) break;

            fluidLogic.insertFluidIntoTank(slot, drained);

            if (held.getCount() == 1) {
                player.inventory.setItemStack(fh.getContainer());
            } else {
                player.inventory.getItemStack().shrink(1);
                if (!player.inventory.addItemStackToInventory(fh.getContainer())) {
                    player.dropItem(fh.getContainer(), false);
                }
            }
        }

        this.updateHeld(player);
        return true;
    }

    private boolean handleRecoveryContainerFluidPouring(EntityPlayerMP player, int slot, ItemStack held,
                                                        FluidInterfaceLogic fluidLogic) {
        FluidStack recoveryFluid = ItemRecoveryContainer.getFluidStack(held);
        if (recoveryFluid == null || recoveryFluid.getFluid() == null) return false;

        IAEFluidStack filterFluid = fluidLogic.getFilter(slot);
        if (filterFluid != null && !filterFluid.getFluidStack().isFluidEqual(recoveryFluid)) return false;

        FluidStack currentTankFluid = fluidLogic.getFluidInTank(slot);
        if (currentTankFluid != null && !currentTankFluid.isFluidEqual(recoveryFluid)) return false;

        long remainingToInsert = Math.min(
            ItemRecoveryContainer.getAmount(held),
            fluidLogic.getEffectiveMaxSlotSize(slot) - fluidLogic.getSlotAmount(slot)
        );
        if (remainingToInsert <= 0) return false;

        long transferred = fluidLogic.insertFluidIntoTankLong(slot, recoveryFluid, remainingToInsert);

        if (transferred <= 0) return false;

        ItemRecoveryContainer.consumeHeldTransfer(player, held, transferred);
        this.updateHeld(player);
        return true;
    }

    /**
     * Handle filling held item from tank (export only).
     * Mirrors ContainerFluidInterface.handleFillItemAction.
     */
    private boolean handleFluidFillItem(EntityPlayerMP player, int slot) {
        IInterfaceLogic logic = getActiveLogic();
        if (!(logic instanceof FluidInterfaceLogic)) return false;

        FluidInterfaceLogic fluidLogic = (FluidInterfaceLogic) logic;

        if (slot < 0 || slot >= FluidInterfaceLogic.STORAGE_SLOTS) return false;

        final ItemStack held = player.inventory.getItemStack();
        if (held.isEmpty()) return false;

        FluidStack tankFluid = fluidLogic.getFluidInTank(slot);
        if (tankFluid == null || tankFluid.amount <= 0) return false;

        int heldAmount = held.getCount();
        for (int i = 0; i < heldAmount; i++) {
            tankFluid = fluidLogic.getFluidInTank(slot);
            if (tankFluid == null || tankFluid.amount <= 0) break;

            ItemStack singleContainer = held.copy();
            singleContainer.setCount(1);

            FluidActionResult result = FluidUtil.tryFillContainer(
                singleContainer,
                new FluidTankWrapper(fluidLogic, slot, tankFluid),
                Integer.MAX_VALUE,
                player,
                true
            );

            if (!result.isSuccess()) break;

            if (held.getCount() == 1) {
                player.inventory.setItemStack(result.getResult());
            } else {
                player.inventory.getItemStack().shrink(1);
                if (!player.inventory.addItemStackToInventory(result.getResult())) {
                    player.dropItem(result.getResult(), false);
                }
            }
        }

        this.updateHeld(player);
        return true;
    }

    /**
     * Wrapper around a single fluid tank slot to work with FluidUtil.tryFillContainer.
     * Mirrors the TankWrapper inner class in ContainerFluidInterface.
     */
    private static class FluidTankWrapper implements IFluidHandler {
        private final FluidInterfaceLogic logic;
        private final int slot;
        private FluidStack fluid;

        FluidTankWrapper(FluidInterfaceLogic logic, int slot, FluidStack fluid) {
            this.logic = logic;
            this.slot = slot;
            this.fluid = fluid;
        }

        @Override
        public IFluidTankProperties[] getTankProperties() {
            int maxTankSize = (int) Math.min(logic.getEffectiveMaxSlotSize(slot), Integer.MAX_VALUE);
            return new IFluidTankProperties[] {
                new FluidTankProperties(fluid, maxTankSize)
            };
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            return 0; // Drain-only wrapper
        }

        @Nullable
        @Override
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            if (resource == null || !resource.isFluidEqual(fluid)) return null;
            return drain(resource.amount, doDrain);
        }

        @Nullable
        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            FluidStack drained = logic.drainFluidFromTank(slot, maxDrain, doDrain);
            if (doDrain && drained != null) {
                fluid = logic.getFluidInTank(slot);
            }
            return drained;
        }
    }

    // ================================= Gas Storage Interaction =================================

    /**
     * Handle pouring gas from held item into tank (import only).
     * Delegates to IOContainerGasHelper to isolate MekanismEnergistics type references.
     */
    @Optional.Method(modid = "mekeng")
    private boolean handleGasEmptyItem(EntityPlayerMP player, int slot) {
        return com.cells.integration.mekanismenergistics.IOContainerGasHelper.handleGasEmptyItem(
            player, slot, this.host,
            () -> this.updateHeld(player),
            () -> this.detectAndSendChanges()
        );
    }

    /**
     * Handle filling held item from gas tank (export only).
     * Delegates to IOContainerGasHelper to isolate MekanismEnergistics type references.
     */
    @Optional.Method(modid = "mekeng")
    private boolean handleGasFillItem(EntityPlayerMP player, int slot) {
        return com.cells.integration.mekanismenergistics.IOContainerGasHelper.handleGasFillItem(
            player, slot, this.host,
            () -> this.updateHeld(player),
            () -> this.detectAndSendChanges()
        );
    }

    // ================================= Essentia Storage Interaction =================================

    /**
     * Handle pouring essentia from held item into storage slot (import only).
     * Delegates to IOContainerEssentiaHelper to isolate ThaumicEnergistics type references.
     */
    @Optional.Method(modid = "thaumicenergistics")
    private boolean handleEssentiaEmptyItem(EntityPlayerMP player, int slot) {
        return com.cells.integration.thaumicenergistics.IOContainerEssentiaHelper.handleEssentiaEmptyItem(
            player, slot, this.host,
            () -> this.updateHeld(player),
            () -> this.detectAndSendChanges()
        );
    }

    /**
     * Handle filling held item from essentia slot (export only).
     * Delegates to IOContainerEssentiaHelper to isolate ThaumicEnergistics type references.
     */
    @Optional.Method(modid = "thaumicenergistics")
    private boolean handleEssentiaFillItem(EntityPlayerMP player, int slot) {
        return com.cells.integration.thaumicenergistics.IOContainerEssentiaHelper.handleEssentiaFillItem(
            player, slot, this.host,
            () -> this.updateHeld(player),
            () -> this.detectAndSendChanges()
        );
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

        boolean isFromPlayerInventory = (slot instanceof appeng.container.slot.AppEngSlot)
            && ((appeng.container.slot.AppEngSlot) slot).isPlayerSide();

        if (!isFromPlayerInventory) return super.transferStackInSlot(player, slotIndex);

        // From player inventory: try upgrade slots first
        AppEngInternalInventory upgradeInv = getActiveLogicUpgradeInv();
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

        // Try to add as filter for the active tab
        ResourceType resType = this.host.getResourceType();
        if (resType == ResourceType.ITEM) {
            ItemStack filterCopy = QuickAddHelper.getItemFromItemStack(clickedStack);
            if (filterCopy.isEmpty()) return ItemStack.EMPTY;

            filterCopy.setCount(1);
            IAEItemStack aeStack = AEApi.instance().storage()
                .getStorageChannel(IItemStorageChannel.class).createStack(filterCopy);
            if (aeStack != null) this.quickAddToFilter(aeStack, player);
        } else if (resType == ResourceType.FLUID) {
            // Extract fluid from item and add as filter
            FluidStack fluid = QuickAddHelper.getFluidFromItemStack(clickedStack);
            if (fluid != null) {
                AEFluidStack aeFluid = AEFluidStack.fromFluidStack(fluid);
                if (aeFluid != null) this.quickAddToFilter(aeFluid, player);
            }
        }

        return ItemStack.EMPTY;
    }

    // ================================= Upgrade slot change =================================

    @Override
    public void onSlotChange(final Slot s) {
        super.onSlotChange(s);

        if (s instanceof SlotUpgrade) {
            // Refresh upgrades for the ACTIVE logic only (upgrades are per-direction)
            getActiveLogic().refreshUpgrades();
        }
    }

    // ================================= Helpers =================================

    private static boolean filtersEqual(@Nullable Object a, @Nullable Object b) {
        if (a == null) return b == null;
        if (b == null) return false;
        return a.equals(b);
    }

    @SuppressWarnings("rawtypes")
    private static boolean storageEqual(@Nullable Object a, @Nullable Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (!filtersEqual(a, b)) return false;
        return ((IAEStack) a).getStackSize() == ((IAEStack) b).getStackSize();
    }

    @Nullable
    private EntityPlayer getPlayerFromListeners() {
        for (IContainerListener listener : this.listeners) {
            if (listener instanceof EntityPlayer) return (EntityPlayer) listener;
        }
        return null;
    }

    /**
     * Package-visible wrapper for AEBaseContainer.updateHeld.
     */
    void sendHeldItemUpdate(EntityPlayerMP player) {
        this.updateHeld(player);
    }

    // ================================= Switchable Upgrade Inventory =================================

    /**
     * IItemHandler wrapper that delegates to a switchable AppEngInternalInventory.
     * When the direction tab changes, call {@link #switchTo} to update the delegate.
     */
    static class SwitchableUpgradeInventory implements IItemHandler {

        private AppEngInternalInventory delegate;

        SwitchableUpgradeInventory(AppEngInternalInventory initial) {
            this.delegate = initial;
        }

        void switchTo(AppEngInternalInventory newInv) {
            this.delegate = newInv;
        }

        @Override
        public int getSlots() {
            return this.delegate.getSlots();
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            return this.delegate.getStackInSlot(slot);
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            return this.delegate.insertItem(slot, stack, simulate);
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return this.delegate.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return this.delegate.getSlotLimit(slot);
        }
    }

    // ================================= Upgrade Slot =================================

    static class SlotUpgrade extends SlotNormal {
        SlotUpgrade(IItemHandler inv, int idx, int x, int y) {
            super(inv, idx, x, y);
            this.setIIcon(13 * 16 + 15); // UPGRADES icon
        }

        @Override
        public int getSlotStackLimit() {
            return 1;
        }
    }
}
