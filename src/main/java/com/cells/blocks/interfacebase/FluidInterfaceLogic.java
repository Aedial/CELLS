package com.cells.blocks.interfacebase;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.core.settings.TickRates;
import appeng.fluids.util.AEFluidInventory;
import appeng.fluids.util.AEFluidStack;
import appeng.fluids.util.IAEFluidInventory;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.inv.IAEAppEngInventory;

import com.cells.items.ItemOverflowCard;
import com.cells.items.ItemTrashUnselectedCard;
import com.cells.util.FluidStackKey;
import com.cells.util.TickManagerHelper;


/**
 * Shared logic delegate for Fluid Interface implementations (both import and export,
 * both tile and part). Contains all business logic that is identical across all
 * four fluid interface variants.
 * <p>
 * The host provides platform-specific operations (grid proxy, marking dirty, etc.)
 * via the {@link Host} callback interface. The {@link Host#isExport()} flag
 * parameterizes import vs export behavioral differences.
 * <p>
 * Both TileFluidImportInterface/TileFluidExportInterface and
 * PartFluidImportInterface/PartFluidExportInterface instantiate a
 * FluidInterfaceLogic in their constructor and delegate business logic to it.
 */
// FIXME: could probably unify some logic with ItemInterfaceLogic, with abstract helpers.
//        Don't need to unify the whole class, just the shared logic around filter management, pagination, etc.
public class FluidInterfaceLogic {

    // ============================== Host callback interface ==============================

    /**
     * Callback interface that the host (tile or part) implements to provide
     * platform-specific operations to the logic delegate.
     * <p>
     * Extends IAEAppEngInventory (for upgrade inventory change callbacks)
     * and IAEFluidInventory (for fluid filter inventory change callbacks).
     */
    public interface Host extends IAEAppEngInventory, IAEFluidInventory {

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

    // ============================== Constants ==============================

    public static final int SLOTS_PER_PAGE = 36;
    public static final int MAX_CAPACITY_CARDS = 4;
    public static final int MAX_PAGES = 1 + MAX_CAPACITY_CARDS;
    public static final int FILTER_SLOTS = SLOTS_PER_PAGE * MAX_PAGES;
    public static final int TANK_SLOTS = SLOTS_PER_PAGE * MAX_PAGES;
    public static final int TOTAL_SLOTS = Math.min(FILTER_SLOTS, TANK_SLOTS);
    public static final int UPGRADE_SLOTS = 4;
    public static final int DEFAULT_MAX_SLOT_SIZE = 16000; // mB (16 buckets)
    public static final int MIN_MAX_SLOT_SIZE = 1;

    // ============================== Fields ==============================

    private final Host host;

    // Filter inventory (fluid filters using AE2 fluid inventory)
    private final AEFluidInventory filterInventory;

    // Internal fluid storage - one tank per filter slot
    private final FluidStack[] fluidTanks = new FluidStack[TANK_SLOTS];

    // Upgrade inventory (accepts only specific upgrade cards)
    private final AppEngInternalInventory upgradeInventory;

    // External handler exposed via capabilities
    private final IFluidHandler externalHandler;

    // Config
    private int maxSlotSize = DEFAULT_MAX_SLOT_SIZE;
    private int pollingRate = 0; // 0 = adaptive (AE2 default)

    // Upgrade cache (import-only upgrades are always false for export)
    private boolean installedOverflowUpgrade = false;
    private boolean installedTrashUnselectedUpgrade = false;
    private int installedCapacityUpgrades = 0;

    // Current GUI page index (0-based)
    private int currentPage = 0;

    // Mapping of filter fluids to their corresponding tank index for quick lookup
    final Map<FluidStackKey, Integer> filterToSlotMap = new HashMap<>();

    // Reverse mapping: slot index to filter key (used by handlers for tank properties)
    final Map<Integer, FluidStackKey> slotToFilterMap = new HashMap<>();

    // List of slot indices that have filters, in slot order
    List<Integer> filterSlotList = new ArrayList<>();

    // ============================== Constructor ==============================

    public FluidInterfaceLogic(Host host) {
        this.host = host;

        // Create fluid filter inventory - the host gets IAEFluidInventory callbacks
        this.filterInventory = new AEFluidInventory(host, FILTER_SLOTS, 1);

        // Create upgrade inventory with filtering for specific upgrade cards
        this.upgradeInventory = new AppEngInternalInventory(host, UPGRADE_SLOTS, 1) {
            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return FluidInterfaceLogic.this.isValidUpgrade(stack);
            }
        };

        // Create appropriate external handler based on direction
        if (host.isExport()) {
            this.externalHandler = new ExportFluidHandler(this);
        } else {
            this.externalHandler = new FilteredFluidHandler(this);
        }

        refreshUpgrades();
        refreshFilterMap();
    }

    // ============================== Inventory accessors ==============================

    public AEFluidInventory getFilterInventory() {
        return this.filterInventory;
    }

    public AppEngInternalInventory getUpgradeInventory() {
        return this.upgradeInventory;
    }

    /**
     * @return The external handler to expose via capabilities (filtered for import, extraction-only for export).
     */
    public IFluidHandler getExternalHandler() {
        return this.externalHandler;
    }

    // ============================== Tank access ==============================

    public boolean isTankEmpty(int slot) {
        if (slot < 0 || slot >= TANK_SLOTS) return true;

        return fluidTanks[slot] == null || fluidTanks[slot].amount <= 0;
    }

    @Nullable
    public FluidStack getFluidInTank(int slot) {
        if (slot < 0 || slot >= TANK_SLOTS) return null;

        return fluidTanks[slot];
    }

    @Nullable
    public IAEFluidStack getFilterFluid(int slot) {
        if (slot < 0 || slot >= FILTER_SLOTS) return null;

        return this.filterInventory.getFluidInSlot(slot);
    }

    public void setFilterFluid(int slot, @Nullable IAEFluidStack fluid) {
        if (slot < 0 || slot >= FILTER_SLOTS) return;

        this.filterInventory.setFluidInSlot(slot, fluid);
    }

    /**
     * Insert fluid into a specific tank slot (import interface operation).
     * @return The amount of fluid actually inserted
     */
    public int insertFluidIntoTank(int slot, FluidStack fluid) {
        if (slot < 0 || slot >= TANK_SLOTS) return 0;
        if (fluid == null || fluid.amount <= 0) return 0;

        FluidStack current = this.fluidTanks[slot];

        // If tank has fluid, it must match
        if (current != null && !current.isFluidEqual(fluid)) return 0;

        int capacity = this.maxSlotSize;
        int currentAmount = current != null ? current.amount : 0;
        int spaceAvailable = capacity - currentAmount;
        if (spaceAvailable <= 0) return 0;

        int toInsert = Math.min(fluid.amount, spaceAvailable);

        if (current == null) {
            this.fluidTanks[slot] = new FluidStack(fluid, toInsert);
        } else {
            current.amount += toInsert;
        }

        this.host.markDirtyAndSave();
        this.host.markForNetworkUpdate();

        return toInsert;
    }

    /**
     * Drain fluid from a specific tank slot (export interface operation).
     * @return The fluid extracted, or null if nothing extracted
     */
    @Nullable
    public FluidStack drainFluidFromTank(int slot, int maxDrain, boolean doDrain) {
        if (slot < 0 || slot >= TANK_SLOTS) return null;

        FluidStack current = this.fluidTanks[slot];
        if (current == null || current.amount <= 0) return null;

        int toDrain = Math.min(maxDrain, current.amount);
        FluidStack drained = current.copy();
        drained.amount = toDrain;

        if (doDrain) {
            current.amount -= toDrain;
            if (current.amount <= 0) this.fluidTanks[slot] = null;

            this.host.markDirtyAndSave();
            this.host.markForNetworkUpdate();

            // Wake up to request more fluids (export replenishes)
            this.wakeUpIfAdaptive();
        }

        return drained;
    }

    // ============================== Config ==============================

    public int getMaxSlotSize() {
        return this.maxSlotSize;
    }

    /**
     * Set the maximum tank capacity in mB.
     * If export and reduced, returns overflow fluids to the network.
     */
    public void setMaxSlotSize(int size) {
        int oldSize = this.maxSlotSize;
        this.maxSlotSize = Math.max(MIN_MAX_SLOT_SIZE, size);

        this.host.markDirtyAndSave();

        if (this.host.isExport()) {
            if (oldSize > this.maxSlotSize) returnOverflowToNetwork();
            if (oldSize < this.maxSlotSize) this.wakeUpIfAdaptive();
        }
    }

    public int getPollingRate() {
        return this.pollingRate;
    }

    public void setPollingRate(int ticks) {
        this.setPollingRate(ticks, null);
    }

    /**
     * Set the polling rate with optional player notification on failure.
     * @param ticks Polling rate in ticks (0 = adaptive)
     * @param player Player to notify if re-registration fails, or null to skip notification
     */
    public void setPollingRate(int ticks, EntityPlayer player) {
        this.pollingRate = Math.max(0, ticks);
        this.host.markDirtyAndSave();

        // Re-register with the tick manager to apply the new TickingRequest bounds.
        // Only attempt on server side when the proxy is ready - if not ready yet
        // (e.g., during onPlacement/uploadSettings), the tick manager will pick up
        // the correct rate when the node is first added to the grid.
        AENetworkProxy proxy = this.host.getGridProxy();
        if (proxy.isReady()) {
            // Uses TickManagerHelper to purge stale TickTrackers from AE2's internal
            // PriorityQueue before re-registering (see TickManagerHelper for details).
            if (!TickManagerHelper.reRegisterTickable(proxy.getNode(), this.host.getTickable())) {
                if (player != null) {
                    player.sendMessage(new TextComponentTranslation("chat.cells.polling_rate_delayed"));
                }
            }
        }
    }

    // ============================== Upgrade management ==============================

    /**
     * Refresh the status of installed upgrades. Should be called whenever upgrade slots change.
     */
    public void refreshUpgrades() {
        if (!this.host.isExport()) {
            this.installedOverflowUpgrade = countUpgrade(ItemOverflowCard.class) > 0;
            this.installedTrashUnselectedUpgrade = countUpgrade(ItemTrashUnselectedCard.class) > 0;
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

    private int countUpgrade(Class<?> itemClass) {
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

    /**
     * Check if an item is a valid upgrade for this interface.
     * Import: Accepts Overflow Card, Trash Unselected Card (max 1 each), and Capacity Card (max 4).
     * Export: Accepts Overflow Card (max 1) and Capacity Card (max 4) only.
     */
    public boolean isValidUpgrade(ItemStack stack) {
        if (stack.isEmpty()) return false;

        if (stack.getItem() instanceof ItemOverflowCard) {
            // Overflow card is valid for both import and export fluid interfaces
            return countUpgrade(ItemOverflowCard.class) < 1;
        }

        // Trash unselected is import-only
        if (!this.host.isExport()) {
            if (stack.getItem() instanceof ItemTrashUnselectedCard) {
                return countUpgrade(ItemTrashUnselectedCard.class) < 1;
            }
        }

        // Capacity card (both import and export) - limited by the number of upgrade slots
        if (stack.getItem() instanceof IUpgradeModule) {
            IUpgradeModule module = (IUpgradeModule) stack.getItem();
            if (module.getType(stack) == Upgrades.CAPACITY) return true;
        }

        return false;
    }

    // ============================== Pagination ==============================

    public int getInstalledCapacityUpgrades() {
        return this.installedCapacityUpgrades;
    }

    public int getTotalPages() {
        return 1 + this.installedCapacityUpgrades;
    }

    public int getCurrentPage() {
        return this.currentPage;
    }

    public void setCurrentPage(int page) {
        this.currentPage = Math.max(0, Math.min(page, this.installedCapacityUpgrades));
    }

    public int getCurrentPageStartSlot() {
        return this.currentPage * SLOTS_PER_PAGE;
    }

    /**
     * Handle capacity reduction by clearing filters and returning fluids from removed pages.
     */
    private void handleCapacityReduction(int oldCount, int newCount) {
        int newMaxSlot = (1 + newCount) * SLOTS_PER_PAGE;
        int oldMaxSlot = (1 + oldCount) * SLOTS_PER_PAGE;

        // Process slots that are being removed
        for (int slot = newMaxSlot; slot < oldMaxSlot && slot < TANK_SLOTS; slot++) {
            // Clear the filter
            this.filterInventory.setFluidInSlot(slot, null);

            // Return fluid to network
            FluidStack fluid = this.fluidTanks[slot];
            if (fluid != null && fluid.amount > 0) {
                int notInserted = insertFluidsIntoNetwork(fluid);
                // TODO: handle notInserted, maybe fluid drops? Should I create a dummy item container?
                // Void remainder (no good way to drop fluids without containers)
                this.fluidTanks[slot] = null;
            }
        }

        this.refreshFilterMap();
        this.host.markDirtyAndSave();
    }

    // ============================== Filter management ==============================

    /**
     * Refresh the filter to slot mapping. Should be called whenever filter slots change.
     */
    public void refreshFilterMap() {
        this.filterToSlotMap.clear();
        this.slotToFilterMap.clear();

        List<Integer> validSlots = new ArrayList<>();

        for (int i = 0; i < TOTAL_SLOTS; i++) {
            IAEFluidStack filterFluid = this.filterInventory.getFluidInSlot(i);
            if (filterFluid == null) continue;

            FluidStack fluid = filterFluid.getFluidStack();
            if (fluid == null) continue;

            FluidStackKey key = FluidStackKey.of(fluid);
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
     * Import: only clears filters where the corresponding tank is empty.
     * Export: clears all filter slots, orphaned fluids returned on next tick.
     */
    public void clearFilters() {
        if (this.host.isExport()) {
            for (int i = 0; i < FILTER_SLOTS; i++) {
                this.filterInventory.setFluidInSlot(i, null);
            }
        } else {
            for (int i = 0; i < FILTER_SLOTS; i++) {
                // Only clear filter if the corresponding tank is empty
                if (i >= TANK_SLOTS || this.fluidTanks[i] == null || this.fluidTanks[i].amount <= 0) {
                    this.filterInventory.setFluidInSlot(i, null);
                }
            }
        }

        this.refreshFilterMap();
        this.host.markDirtyAndSave();
        this.host.markForNetworkUpdate();
    }

    /**
     * Find the first empty filter slot.
     * @return The slot index, or -1 if no empty slots are available
     */
    public int findEmptyFilterSlot() {
        for (int i = 0; i < this.filterInventory.getSlots(); i++) {
            if (this.filterInventory.getFluidInSlot(i) == null) return i;
        }

        return -1;
    }

    // ============================== NBT serialization ==============================

    /**
     * Write fluid filters to NBT in sparse format (only non-empty slots).
     * Format: compound map with slot index as string key -> fluid filter data.
     * Compatible with AEFluidInventory's dense format for reading.
     */
    private void writeFiltersToNBT(NBTTagCompound data, String name) {
        NBTTagCompound filtersMap = new NBTTagCompound();
        for (int i = 0; i < this.filterInventory.getSlots(); i++) {
            IAEFluidStack filter = this.filterInventory.getFluidInSlot(i);
            if (filter == null) continue;

            NBTTagCompound filterTag = new NBTTagCompound();
            filter.writeToNBT(filterTag);
            filtersMap.setTag("#" + i, filterTag);
        }
        data.setTag(name, filtersMap);
    }

    /**
     * Read fluid filters from NBT. Supports both sparse format (only non-empty slots)
     * and AEFluidInventory's dense format (all slots with empty compounds for nulls).
     */
    private void readFiltersFromNBT(NBTTagCompound data, String name) {
        if (!data.hasKey(name, Constants.NBT.TAG_COMPOUND)) return;

        NBTTagCompound filtersMap = data.getCompoundTag(name);
        for (String key : filtersMap.getKeySet()) {
            if (!key.startsWith("#")) continue;

            try {
                int slot = Integer.parseInt(key.substring(1));
                if (slot < 0 || slot >= FILTER_SLOTS) continue;

                NBTTagCompound filterTag = filtersMap.getCompoundTag(key);
                // Skip empty compounds (from dense format or sparse empties)
                if (filterTag.isEmpty()) continue;

                IAEFluidStack filter = AEFluidStack.fromNBT(filterTag);
                if (filter != null) this.filterInventory.setFluidInSlot(slot, filter);
            } catch (NumberFormatException e) {
                continue;
            }
        }
    }

    /**
     * Read logic state from NBT. Call from host's readFromNBT.
     */
    public void readFromNBT(NBTTagCompound data) {
        readFiltersFromNBT(data, "fluidFilters");
        this.upgradeInventory.readFromNBT(data, "upgrades");
        this.maxSlotSize = data.getInteger("maxSlotSize");
        this.pollingRate = data.getInteger("pollingRate");

        if (this.maxSlotSize < MIN_MAX_SLOT_SIZE) this.maxSlotSize = MIN_MAX_SLOT_SIZE;
        if (this.pollingRate < 0) this.pollingRate = 0;

        // Read fluid tanks - supports two formats:
        // 1. Compound map format (current): "fluidTanks" is a TAG_COMPOUND with slot indices as string keys
        // 2. Sequential list format (legacy): TAG_LIST with one entry per slot, empties marked "Empty"
        if (data.hasKey("fluidTanks", Constants.NBT.TAG_COMPOUND)) {
            NBTTagCompound tanksMap = data.getCompoundTag("fluidTanks");
            for (String key : tanksMap.getKeySet()) {
                try {
                    int slot = Integer.parseInt(key);
                    if (slot >= 0 && slot < TANK_SLOTS) {
                        this.fluidTanks[slot] = FluidStack.loadFluidStackFromNBT(tanksMap.getCompoundTag(key));
                    }
                } catch (NumberFormatException e) {
                    continue;
                }
            }
        } else if (data.hasKey("fluidTanks", Constants.NBT.TAG_LIST)) {
            NBTTagList tankList = data.getTagList("fluidTanks", Constants.NBT.TAG_COMPOUND);

            for (int i = 0; i < tankList.tagCount() && i < TANK_SLOTS; i++) {
                NBTTagCompound tankTag = tankList.getCompoundTagAt(i);
                if (tankTag.hasKey("Empty")) {
                    this.fluidTanks[i] = null;
                } else {
                    this.fluidTanks[i] = FluidStack.loadFluidStackFromNBT(tankTag);
                }
            }
        }

        this.refreshFilterMap();
        this.refreshUpgrades();
    }

    /**
     * Write logic state to NBT. Call from host's writeToNBT.
     */
    public void writeToNBT(NBTTagCompound data) {
        writeFiltersToNBT(data, "fluidFilters");
        this.upgradeInventory.writeToNBT(data, "upgrades");
        data.setInteger("maxSlotSize", this.maxSlotSize);
        data.setInteger("pollingRate", this.pollingRate);

        // Write fluid tanks - compound map format: slot index as string key -> fluid data.
        // Only non-empty slots are stored.
        NBTTagCompound tanksMap = new NBTTagCompound();
        for (int i = 0; i < TANK_SLOTS; i++) {
            if (this.fluidTanks[i] == null) continue;

            NBTTagCompound tankTag = new NBTTagCompound();
            this.fluidTanks[i].writeToNBT(tankTag);
            tanksMap.setTag(String.valueOf(i), tankTag);
        }
        data.setTag("fluidTanks", tanksMap);
    }

    // ============================== Stream serialization (for tiles) ==============================

    /**
     * Read tank data from a ByteBuf stream for client sync.
     * Call from tile's readFromStream.
     * Sparse format: count of non-empty entries, then {slot, fluidName, amount} per entry.
     * @return true if any data changed (needs re-render)
     */
    public boolean readTanksFromStream(ByteBuf data) {
        boolean changed = false;

        // Clear all tanks first, then populate from the stream
        for (int i = 0; i < TANK_SLOTS; i++) {
            if (this.fluidTanks[i] != null) {
                this.fluidTanks[i] = null;
                changed = true;
            }
        }

        int count = data.readShort();
        for (int idx = 0; idx < count; idx++) {
            int slot = data.readShort();
            int nameLen = data.readShort();
            byte[] nameBytes = new byte[nameLen];
            data.readBytes(nameBytes);
            String fluidName = new String(nameBytes, StandardCharsets.UTF_8);
            int amount = data.readInt();

            if (slot < 0 || slot >= TANK_SLOTS) continue;

            Fluid fluid = FluidRegistry.getFluid(fluidName);
            if (fluid != null) {
                this.fluidTanks[slot] = new FluidStack(fluid, amount);
                changed = true;
            }
        }

        return changed;
    }

    /**
     * Write tank data to a ByteBuf stream for client sync.
     * Call from tile's writeToStream.
     * Sparse format: sends count of non-empty entries, then {slot, fluidName, amount} per entry.
     */
    public void writeTanksToStream(ByteBuf data) {
        // Count non-empty tanks first
        int count = 0;
        for (int i = 0; i < TANK_SLOTS; i++) {
            if (this.fluidTanks[i] != null && this.fluidTanks[i].amount > 0) count++;
        }

        data.writeShort(count);

        for (int i = 0; i < TANK_SLOTS; i++) {
            FluidStack fluid = this.fluidTanks[i];
            if (fluid == null || fluid.amount <= 0) continue;

            data.writeShort(i);

            byte[] nameBytes = fluid.getFluid().getName().getBytes(StandardCharsets.UTF_8);
            data.writeShort(nameBytes.length);
            data.writeBytes(nameBytes);

            data.writeInt(fluid.amount);
        }
    }

    // ============================== Settings (memory card / dismantle) ==============================

    /**
     * Download settings to NBT for memory cards.
     */
    public NBTTagCompound downloadSettings() {
        NBTTagCompound output = new NBTTagCompound();

        output.setInteger("maxSlotSize", this.maxSlotSize);
        output.setInteger("pollingRate", this.pollingRate);

        return output;
    }

    /**
     * Download settings with filters for memory card + keybind.
     * Does NOT save upgrades to avoid duplication (upgrades stay in the interface).
     */
    public NBTTagCompound downloadSettingsWithFilter() {
        NBTTagCompound output = downloadSettings();
        writeFiltersToNBT(output, "fluidFilters");

        return output;
    }

    /**
     * Download settings with filters AND upgrades for disassembly.
     * Upgrades are saved because the interface is being broken and dropped as an item.
     */
    public NBTTagCompound downloadSettingsForDismantle() {
        NBTTagCompound output = downloadSettingsWithFilter();
        this.upgradeInventory.writeToNBT(output, "upgrades");

        return output;
    }

    /**
     * Upload settings from NBT (memory card or dismantle).
     * Upgrades are restored BEFORE filters to ensure capacity cards are in place,
     * enabling extra filter pages before filter restoration.
     */
    public void uploadSettings(NBTTagCompound compound, EntityPlayer player) {
        if (compound == null) return;

        if (compound.hasKey("maxSlotSize")) {
            this.setMaxSlotSize(compound.getInteger("maxSlotSize"));
        }
        if (compound.hasKey("pollingRate")) {
            this.setPollingRate(compound.getInteger("pollingRate"), player);
        }

        // Merge upgrades FIRST (capacity cards enable extra pages for filters)
        if (compound.hasKey("upgrades")) {
            mergeUpgradesFromNBT(compound, "upgrades");
        }

        // Merge filter inventory from memory card instead of replacing
        if (compound.hasKey("fluidFilters")) {
            mergeFiltersFromNBT(compound, "fluidFilters", player);
        }
    }

    /**
     * Merge upgrades from NBT into the current upgrade inventory.
     * Only adds upgrades to empty slots to avoid duplication.
     */
    private void mergeUpgradesFromNBT(NBTTagCompound data, String name) {
        if (!data.hasKey(name)) return;

        AppEngInternalInventory sourceUpgrades = new AppEngInternalInventory(null, UPGRADE_SLOTS, 1);
        sourceUpgrades.readFromNBT(data, name);

        for (int i = 0; i < sourceUpgrades.getSlots(); i++) {
            ItemStack sourceUpgrade = sourceUpgrades.getStackInSlot(i);
            if (sourceUpgrade.isEmpty()) continue;

            // Find an empty slot for this upgrade
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
            // If no empty slots, silently skip (upgrades are full)
        }

        this.refreshUpgrades();
    }

    /**
     * Merge filters from NBT into the current filter inventory.
     * Only adds filters to empty slots; skips filters that already exist.
     */
    private void mergeFiltersFromNBT(NBTTagCompound data, String name, @Nullable EntityPlayer player) {
        if (!data.hasKey(name)) return;

        // Create a temporary inventory to load the source filters
        AEFluidInventory sourceFilters = new AEFluidInventory(null, FILTER_SLOTS, 1);
        sourceFilters.readFromNBT(data, name);

        List<FluidStack> skippedFilters = new ArrayList<>();

        for (int i = 0; i < sourceFilters.getSlots(); i++) {
            IAEFluidStack sourceFilter = sourceFilters.getFluidInSlot(i);
            if (sourceFilter == null) continue;

            FluidStack fluidStack = sourceFilter.getFluidStack();
            if (fluidStack == null) continue;

            FluidStackKey sourceKey = FluidStackKey.of(fluidStack);
            if (sourceKey == null) continue;

            // Skip if this filter already exists in the target
            if (this.filterToSlotMap.containsKey(sourceKey)) continue;

            // Find an empty slot to add this filter
            int targetSlot = findEmptyFilterSlot();
            if (targetSlot < 0) {
                skippedFilters.add(fluidStack.copy());
                continue;
            }

            // Add the filter to the empty slot
            this.filterInventory.setFluidInSlot(targetSlot, sourceFilter.copy());
            this.filterToSlotMap.put(sourceKey, targetSlot);
            this.slotToFilterMap.put(targetSlot, sourceKey);
        }

        this.refreshFilterMap();

        // Notify the player about skipped filters
        if (player != null && !skippedFilters.isEmpty()) {
            String langKey = this.host.isExport()
                ? "message.cells.export_fluid_interface.filters_not_added"
                : "message.cells.import_fluid_interface.filters_not_added";

            String filters = skippedFilters.stream()
                .map(FluidStack::getLocalizedName)
                .reduce((a, b) -> a + "\n- " + b)
                .orElse("");
            player.sendMessage(new TextComponentTranslation(langKey, skippedFilters.size(), filters));
        }
    }

    // ============================== Inventory change handling ==============================

    /**
     * Handle fluid filter changes. Call from host's onFluidInventoryChanged.
     */
    public void onFluidFilterChanged(int slot) {
        if (this.host.isExport()) {
            // Export: if filter changed, return orphaned fluids in that slot
            FluidStack tankFluid = this.fluidTanks[slot];
            if (tankFluid != null && tankFluid.amount > 0) {
                IAEFluidStack filterFluid = this.filterInventory.getFluidInSlot(slot);

                boolean isOrphaned = filterFluid == null ||
                    !filterFluid.getFluidStack().isFluidEqual(tankFluid);

                if (isOrphaned) returnTankToNetwork(slot);
            }
        }

        this.refreshFilterMap();
        this.wakeUpIfAdaptive();
        this.host.markDirtyAndSave();
    }

    /**
     * Handle upgrade inventory changes. Call from host's onChangeInventory.
     */
    public void onUpgradeChanged() {
        this.refreshUpgrades();
        this.host.markDirtyAndSave();
    }

    // ============================== Tick handling ==============================

    /**
     * Create a TickingRequest based on current configuration.
     */
    public TickingRequest getTickingRequest() {
        if (this.pollingRate > 0) {
            return new TickingRequest(
                this.pollingRate,
                this.pollingRate,
                false,
                true
            );
        }

        return new TickingRequest(
            TickRates.Interface.getMin(),
            TickRates.Interface.getMax(),
            !hasWorkToDo(),
            true
        );
    }

    /**
     * Handle a tick. Returns the appropriate rate modulation.
     */
    public TickRateModulation onTick() {
        if (!this.host.getGridProxy().isActive()) return TickRateModulation.SLEEP;

        boolean didWork = this.host.isExport() ? exportFluids() : importFluids();

        if (this.pollingRate > 0) return TickRateModulation.SAME;
        if (didWork) return TickRateModulation.FASTER;

        return hasWorkToDo() ? TickRateModulation.SLOWER : TickRateModulation.SLEEP;
    }

    /**
     * Check if there's work to do based on direction.
     */
    public boolean hasWorkToDo() {
        if (this.host.isExport()) {
            // Check if any configured tank needs fluids
            for (int i : this.filterSlotList) {
                FluidStack current = this.fluidTanks[i];
                int currentAmount = (current == null) ? 0 : current.amount;
                if (currentAmount < this.maxSlotSize) return true;
            }
        } else {
            // Check if any filtered tank has fluids to import
            for (int i : this.filterToSlotMap.values()) {
                if (this.fluidTanks[i] != null && this.fluidTanks[i].amount > 0) return true;
            }
        }

        return false;
    }

    /**
     * Wake up the tick manager if using adaptive polling (rate=0).
     */
    public void wakeUpIfAdaptive() {
        if (this.pollingRate > 0) return;

        try {
            this.host.getGridProxy().getTick().alertDevice(this.host.getGridProxy().getNode());
        } catch (GridAccessException e) {
            // Not connected to grid
        }
    }

    // ============================== Import logic ==============================

    /**
     * Import fluids from internal tanks into the ME network.
     */
    private boolean importFluids() {
        boolean didWork = false;

        try {
            IStorageGrid storage = this.host.getGridProxy().getStorage();
            IMEInventory<IAEFluidStack> fluidStorage = storage.getInventory(
                AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class)
            );

            for (int i : this.filterToSlotMap.values()) {
                FluidStack fluid = this.fluidTanks[i];
                if (fluid == null || fluid.amount <= 0) continue;

                IAEFluidStack aeStack = AEFluidStack.fromFluidStack(fluid);
                IAEFluidStack remaining = fluidStorage.injectItems(aeStack, Actionable.MODULATE, this.host.getActionSource());

                if (remaining == null) {
                    this.fluidTanks[i] = null;
                    didWork = true;
                } else if (remaining.getStackSize() < fluid.amount) {
                    fluid.amount = (int) remaining.getStackSize();
                    didWork = true;
                }
            }
        } catch (GridAccessException e) {
            // Not connected to grid
        }

        if (didWork) this.host.markForNetworkUpdate();

        return didWork;
    }

    // ============================== Export logic ==============================

    /**
     * Export fluids from the ME network into tanks.
     */
    private boolean exportFluids() {
        boolean didWork = false;

        try {
            IStorageGrid storage = this.host.getGridProxy().getStorage();
            IMEInventory<IAEFluidStack> fluidStorage = storage.getInventory(
                AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class)
            );

            // First, return any orphaned or overflow fluids to the network
            returnOrphanedFluidsToNetwork();
            returnOverflowToNetwork();

            for (int i : this.filterSlotList) {
                IAEFluidStack filterFluid = this.filterInventory.getFluidInSlot(i);
                if (filterFluid == null) continue;

                FluidStack current = this.fluidTanks[i];

                // Skip slots where current fluids don't match filter (orphaned)
                if (current != null && current.amount > 0) {
                    if (!filterFluid.getFluidStack().isFluidEqual(current)) continue;
                }

                int currentAmount = (current == null) ? 0 : current.amount;
                int space = this.maxSlotSize - currentAmount;
                if (space <= 0) continue;

                // Request fluids from network
                IAEFluidStack request = filterFluid.copy();
                request.setStackSize(space);

                IAEFluidStack extracted = fluidStorage.extractItems(request, Actionable.MODULATE, this.host.getActionSource());
                if (extracted == null || extracted.getStackSize() <= 0) continue;

                // Add to tank
                int amount = (int) extracted.getStackSize();
                if (current == null) {
                    this.fluidTanks[i] = extracted.getFluidStack();
                } else {
                    current.amount += amount;
                }

                didWork = true;
            }
        } catch (GridAccessException e) {
            // Not connected to grid
        }

        if (didWork) this.host.markForNetworkUpdate();

        return didWork;
    }

    // ============================== Network transfer helpers ==============================

    /**
     * Try to insert fluids into the ME network.
     * @return Amount of fluid that couldn't be inserted
     */
    public int insertFluidsIntoNetwork(FluidStack fluid) {
        if (fluid == null || fluid.amount <= 0) return 0;

        try {
            IStorageGrid storage = this.host.getGridProxy().getStorage();
            IMEInventory<IAEFluidStack> fluidStorage = storage.getInventory(
                AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class)
            );

            IAEFluidStack toInsert = AEFluidStack.fromFluidStack(fluid);
            if (toInsert == null) return fluid.amount;

            IAEFluidStack notInserted = fluidStorage.injectItems(toInsert, Actionable.MODULATE, this.host.getActionSource());

            if (notInserted == null || notInserted.getStackSize() == 0) return 0;
            return (int) notInserted.getStackSize();
        } catch (GridAccessException e) {
            return fluid.amount;
        }
    }

    /**
     * Return all fluids in a specific tank back to the ME network.
     */
    public void returnTankToNetwork(int slot) {
        if (slot < 0 || slot >= TANK_SLOTS) return;

        FluidStack fluid = this.fluidTanks[slot];
        if (fluid == null || fluid.amount <= 0) return;

        int notInserted = insertFluidsIntoNetwork(fluid);
        if (notInserted <= 0) {
            this.fluidTanks[slot] = null;
        } else {
            fluid.amount = notInserted;
        }

        this.host.markDirtyAndSave();
        this.host.markForNetworkUpdate();
    }

    /**
     * Return overflow fluids (exceeding maxSlotSize) back to the ME network.
     */
    private void returnOverflowToNetwork() {
        for (int i = 0; i < TANK_SLOTS; i++) {
            FluidStack fluid = this.fluidTanks[i];
            if (fluid == null) continue;

            int overflow = fluid.amount - this.maxSlotSize;
            if (overflow <= 0) continue;

            FluidStack overflowFluid = fluid.copy();
            overflowFluid.amount = overflow;

            int notInserted = insertFluidsIntoNetwork(overflowFluid);

            fluid.amount -= (overflow - notInserted);
            if (fluid.amount <= 0) this.fluidTanks[i] = null;
        }

        this.host.markDirtyAndSave();
        this.host.markForNetworkUpdate();
    }

    /**
     * Return all orphaned fluids (fluids that don't match their filter) to the ME network.
     */
    public void returnOrphanedFluidsToNetwork() {
        for (int i = 0; i < TANK_SLOTS; i++) {
            FluidStack tankFluid = this.fluidTanks[i];
            if (tankFluid == null || tankFluid.amount <= 0) continue;

            IAEFluidStack filterFluid = this.filterInventory.getFluidInSlot(i);

            boolean isOrphaned = filterFluid == null ||
                !filterFluid.getFluidStack().isFluidEqual(tankFluid);

            if (isOrphaned) returnTankToNetwork(i);
        }
    }

    // ============================== Drops ==============================

    /**
     * Collect stored items that should be dropped (not upgrades).
     * Used during wrench dismantling where upgrades are saved to NBT.
     * Note: Fluids cannot be dropped as items - this is a no-op for fluid interfaces.
     */
    public void getStorageDrops(List<ItemStack> drops) {
        // TODO: do something about fluids. Maybe custom fluid drops?
        //       Like an item that can be right-clicked on a tank to retrieve the fluid as an item, or something?
    }

    /**
     * Collect all items that should be dropped when this interface is broken normally.
     * This method is NOT called during wrench dismantling (tiles use disableDrops(),
     * parts check the wrenched flag) - in that case, upgrades are saved to NBT instead.
     * Fluids cannot be dropped as items.
     */
    public void getDrops(List<ItemStack> drops) {
        // Drop stored items (no-op for fluids)
        getStorageDrops(drops);

        // Drop upgrades (only during normal breaking - wrench path saves them to NBT)
        for (int i = 0; i < this.upgradeInventory.getSlots(); i++) {
            ItemStack stack = this.upgradeInventory.getStackInSlot(i);
            if (!stack.isEmpty()) drops.add(stack);
        }
    }

    // ============================== External handlers ==============================

    /**
     * Filtered fluid insertion handler for import interfaces.
     * Routes fluids to appropriate tank based on filters.
     * Does not allow extraction.
     */
    private static class FilteredFluidHandler implements IFluidHandler {
        private final FluidInterfaceLogic logic;

        public FilteredFluidHandler(FluidInterfaceLogic logic) {
            this.logic = logic;
        }

        @Override
        public IFluidTankProperties[] getTankProperties() {
            List<IFluidTankProperties> props = new ArrayList<>();

            for (int slot : logic.filterSlotList) {
                FluidStack contents = logic.fluidTanks[slot];
                int capacity = logic.maxSlotSize;

                FluidStackKey filterKey = logic.slotToFilterMap.get(slot);

                props.add(new IFluidTankProperties() {
                    @Nullable
                    @Override
                    public FluidStack getContents() {
                        return contents != null ? contents.copy() : null;
                    }

                    @Override
                    public int getCapacity() {
                        return capacity;
                    }

                    @Override
                    public boolean canFill() {
                        return true;
                    }

                    @Override
                    public boolean canDrain() {
                        return false;
                    }

                    @Override
                    public boolean canFillFluidType(FluidStack fluidStack) {
                        return filterKey != null && filterKey.matches(fluidStack);
                    }

                    @Override
                    public boolean canDrainFluidType(FluidStack fluidStack) {
                        return false;
                    }
                });
            }

            return props.toArray(new IFluidTankProperties[0]);
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            if (resource == null || resource.amount <= 0) return 0;

            FluidStackKey key = FluidStackKey.of(resource);
            if (key == null) return 0;

            // No filter matches - void if trash unselected upgrade installed, reject otherwise
            Integer slot = logic.filterToSlotMap.get(key);
            if (slot == null) return logic.installedTrashUnselectedUpgrade ? resource.amount : 0;

            // Insert into matching tank
            FluidStack existing = logic.fluidTanks[slot];
            int capacity = logic.maxSlotSize;
            int currentAmount = (existing == null) ? 0 : existing.amount;
            int space = capacity - currentAmount;

            // Tank full - void overflow if upgrade installed, reject otherwise
            if (space <= 0) return logic.installedOverflowUpgrade ? resource.amount : 0;

            int toInsert = Math.min(resource.amount, space);
            if (doFill) {
                if (existing == null) {
                    logic.fluidTanks[slot] = new FluidStack(resource, toInsert);
                } else {
                    existing.amount += toInsert;
                }

                logic.host.markDirtyAndSave();
                logic.host.markForNetworkUpdate();

                // Wake up to import fluids
                logic.wakeUpIfAdaptive();
            }

            // If overflow upgrade installed, accept all fluid (void the excess)
            int excess = resource.amount - toInsert;
            if (excess > 0 && logic.installedOverflowUpgrade) return resource.amount;

            return toInsert;
        }

        @Nullable
        @Override
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            // Import interface does not allow external extraction
            return null;
        }

        @Nullable
        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            // Import interface does not allow external extraction
            return null;
        }
    }

    /**
     * Export fluid handler that exposes tanks for extraction only.
     */
    private static class ExportFluidHandler implements IFluidHandler {
        private final FluidInterfaceLogic logic;

        public ExportFluidHandler(FluidInterfaceLogic logic) {
            this.logic = logic;
        }

        @Override
        public IFluidTankProperties[] getTankProperties() {
            List<IFluidTankProperties> props = new ArrayList<>();

            for (int slot : logic.filterSlotList) {
                FluidStack contents = logic.fluidTanks[slot];
                int capacity = logic.maxSlotSize;

                FluidStackKey filterKey = logic.slotToFilterMap.get(slot);

                props.add(new IFluidTankProperties() {
                    @Nullable
                    @Override
                    public FluidStack getContents() {
                        return contents != null ? contents.copy() : null;
                    }

                    @Override
                    public int getCapacity() {
                        return capacity;
                    }

                    @Override
                    public boolean canFill() {
                        return false;
                    }

                    @Override
                    public boolean canDrain() {
                        return true;
                    }

                    @Override
                    public boolean canFillFluidType(FluidStack fluidStack) {
                        return false;
                    }

                    @Override
                    public boolean canDrainFluidType(FluidStack fluidStack) {
                        return filterKey != null && filterKey.matches(fluidStack);
                    }
                });
            }

            return props.toArray(new IFluidTankProperties[0]);
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            // Export interface does not allow external insertion
            return 0;
        }

        @Nullable
        @Override
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            if (resource == null || resource.amount <= 0) return null;

            FluidStackKey key = FluidStackKey.of(resource);
            if (key == null) return null;

            Integer slot = logic.filterToSlotMap.get(key);
            if (slot == null) return null;

            return logic.drainFluidFromTank(slot, resource.amount, doDrain);
        }

        @Nullable
        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            // Drain from any available tank (first non-empty)
            for (int slot : logic.filterSlotList) {
                FluidStack fluid = logic.fluidTanks[slot];
                if (fluid != null && fluid.amount > 0) {
                    return logic.drainFluidFromTank(slot, maxDrain, doDrain);
                }
            }

            return null;
        }
    }
}
