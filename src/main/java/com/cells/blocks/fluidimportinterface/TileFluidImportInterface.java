package com.cells.blocks.fluidimportinterface;

import java.io.IOException;
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
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.core.settings.TickRates;
import appeng.fluids.util.AEFluidInventory;
import appeng.fluids.util.AEFluidStack;
import appeng.fluids.util.IAEFluidInventory;
import appeng.fluids.util.IAEFluidTank;
import appeng.me.GridAccessException;
import appeng.me.helpers.MachineSource;
import appeng.tile.grid.AENetworkInvTile;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;
import appeng.util.SettingsFrom;

import com.cells.blocks.importinterface.IImportInterfaceHost;
import com.cells.blocks.importinterface.TileImportInterface;
import com.cells.gui.CellsGuiHandler;
import com.cells.items.ItemOverflowCard;
import com.cells.items.ItemTrashUnselectedCard;
import com.cells.util.FluidStackKey;
import com.cells.util.TickManagerHelper;


/**
 * Tile entity for the Fluid Import Interface block.
 * Provides filter slots (fluid-based filters) and internal fluid tanks.
 * Only accepts fluids that match the filter in the corresponding slot.
 * Automatically imports stored fluids into the ME network.
 * Supports Capacity Cards to add additional pages of 36 tanks each.
 */
public class TileFluidImportInterface extends AENetworkInvTile implements IGridTickable, IAEAppEngInventory, IAEFluidInventory, IImportInterfaceHost, IFluidImportInterfaceInventoryHost {

    public static final int SLOTS_PER_PAGE = 36;
    public static final int MAX_CAPACITY_CARDS = 4;
    public static final int MAX_PAGES = 1 + MAX_CAPACITY_CARDS;
    public static final int FILTER_SLOTS = SLOTS_PER_PAGE * MAX_PAGES;
    public static final int TANK_SLOTS = SLOTS_PER_PAGE * MAX_PAGES;
    public static final int TOTAL_SLOTS = Math.min(FILTER_SLOTS, TANK_SLOTS);
    public static final int UPGRADE_SLOTS = 4;
    public static final int DEFAULT_MAX_SLOT_SIZE = 16000; // Default tank capacity in mB (16 buckets)

    // Filter inventory - stores fluid types directly (not containers) to avoid storing items
    private final AEFluidInventory filterInventory = new AEFluidInventory(this, FILTER_SLOTS, 1);

    // Internal fluid storage - one tank per filter slot
    private final FluidStack[] fluidTanks = new FluidStack[TANK_SLOTS];

    // Upgrade inventory - accepts only specific upgrade cards
    private final AppEngInternalInventory upgradeInventory;

    // Dummy inventory for AENetworkInvTile contract (fluid storage is not item-based)
    private final AppEngInternalInventory dummyInventory = new AppEngInternalInventory(this, 0, 0);

    // Wrapper that exposes fluid tanks with filter checking
    private final FilteredFluidHandler filteredFluidHandler;

    // Max tank capacity in mB for each tank
    private int maxSlotSize = DEFAULT_MAX_SLOT_SIZE;

    // Polling rate in ticks (0 = adaptive AE2 default, nonzero = fixed interval)
    private int pollingRate = TileImportInterface.DEFAULT_POLLING_RATE;

    // Has Void Overflow Upgrade installed
    private boolean installedOverflowUpgrade = false;

    // Has Trash Unselected Upgrade installed
    private boolean installedTrashUnselectedUpgrade = false;

    // Number of installed capacity upgrades (adds pages)
    private int installedCapacityUpgrades = 0;

    // Current GUI page index (0-based)
    private int currentPage = 0;

    // Mapping of filter fluids to their corresponding tank index for quick lookup
    final Map<FluidStackKey, Integer> filterToSlotMap = new HashMap<>();

    // Reverse mapping: slot index to filter key
    final Map<Integer, FluidStackKey> slotToFilterMap = new HashMap<>();

    // List of slot indices that have filters, in slot order (0, 1, 3, 5 if slots 2,4 have no filter)
    // This ensures external systems only see tanks that matter
    List<Integer> filterSlotList = new ArrayList<>();

    // Action source for network operations
    private final IActionSource actionSource;

    public TileFluidImportInterface() {
        this.getProxy().setFlags(GridFlags.REQUIRE_CHANNEL);
        this.getProxy().setIdlePowerUsage(1.0);
        this.actionSource = new MachineSource(this);

        // Create upgrade inventory with filtering for specific upgrade cards
        this.upgradeInventory = new AppEngInternalInventory(this, UPGRADE_SLOTS, 1) {
            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return TileFluidImportInterface.this.isValidUpgrade(stack);
            }
        };

        this.filteredFluidHandler = new FilteredFluidHandler(this);

        refreshUpgrades();
        refreshFilterMap();
    }

    /**
     * Refresh the status of installed upgrades. Should be called whenever upgrade slots change.
     */
    public void refreshUpgrades() {
        this.installedOverflowUpgrade = hasOverflowUpgrade();
        this.installedTrashUnselectedUpgrade = hasTrashUnselectedUpgrade();

        // Count and handle capacity upgrades
        int newCapacityCount = countCapacityUpgrades();
        if (newCapacityCount < this.installedCapacityUpgrades) {
            // Capacity cards were removed - handle reduction
            handleCapacityReduction(newCapacityCount);
        }
        this.installedCapacityUpgrades = newCapacityCount;

        // Clamp current page to valid range
        int maxPage = getTotalPages() - 1;
        if (this.currentPage > maxPage) this.currentPage = maxPage;
    }

    /**
     * Refresh the filter to slot mapping. Should be called whenever filter slots change.
     * Reads fluids directly from the AEFluidInventory.
     */
    public void refreshFilterMap() {
        this.filterToSlotMap.clear();
        this.slotToFilterMap.clear();

        // Build list of valid (internal) slot indices for quick access (because AE2 expects slots matching)
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
     * IAEFluidInventory callback - called when the fluid filter inventory changes.
     * <p>
     * IMPORTANT: AEFluidInventory.onContentChanged() calls the 5-parameter version,
     * not the 2-parameter version. We must override this signature for the callback
     * to actually be invoked.
     */
    @Override
    public void onFluidInventoryChanged(IAEFluidTank inv, int slot, InvOperation operation, FluidStack added, FluidStack removed) {
        if (inv == this.filterInventory) {
            refreshFilterMap();
            this.markDirty();
        }
    }

    /**
     * Also override the 2-parameter version for completeness.
     */
    @Override
    public void onFluidInventoryChanged(IAEFluidTank inv, int slot) {
        if (inv == this.filterInventory) {
            refreshFilterMap();
            this.markDirty();
        }
    }

    /**
     * Check if an item is a valid upgrade for this interface.
     * Accepts Overflow Card, Trash Unselected Card (max 1 each), and Capacity Cards (max 4).
     */
    public boolean isValidUpgrade(ItemStack stack) {
        if (stack.isEmpty()) return false;

        if (stack.getItem() instanceof ItemOverflowCard) {
            return countUpgrade(ItemOverflowCard.class) < 1;
        }

        if (stack.getItem() instanceof ItemTrashUnselectedCard) {
            return countUpgrade(ItemTrashUnselectedCard.class) < 1;
        }

        // Check for AE2 capacity card
        if (stack.getItem() instanceof IUpgradeModule) {
            IUpgradeModule module = (IUpgradeModule) stack.getItem();
            if (module.getType(stack) == Upgrades.CAPACITY) {
                return countCapacityUpgrades() < MAX_CAPACITY_CARDS;
            }
        }

        return false;
    }

    /**
     * Count the number of capacity upgrade cards installed.
     */
    private int countCapacityUpgrades() {
        int count = 0;
        for (int i = 0; i < this.upgradeInventory.getSlots(); i++) {
            ItemStack stack = this.upgradeInventory.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof IUpgradeModule) {
                IUpgradeModule module = (IUpgradeModule) stack.getItem();
                if (module.getType(stack) == Upgrades.CAPACITY) count++;
            }
        }

        return count;
    }

    /**
     * @return Number of capacity upgrades currently installed.
     */
    public int getInstalledCapacityUpgrades() {
        return this.installedCapacityUpgrades;
    }

    /**
     * @return Total number of pages (1 base + 1 per capacity card).
     */
    public int getTotalPages() {
        return 1 + this.installedCapacityUpgrades;
    }

    /**
     * @return Current page index (0-based).
     */
    public int getCurrentPage() {
        return this.currentPage;
    }

    /**
     * Set the current page index, clamped to valid range.
     */
    public void setCurrentPage(int page) {
        int maxPage = getTotalPages() - 1;
        if (page < 0) page = 0;
        if (page > maxPage) page = maxPage;
        this.currentPage = page;
    }

    /**
     * @return The starting slot index for the current page.
     */
    public int getCurrentPageStartSlot() {
        return this.currentPage * SLOTS_PER_PAGE;
    }

    /**
     * Handle reduction in capacity cards - clear filters and return fluids from deleted pages.
     */
    private void handleCapacityReduction(int newCapacityCount) {
        int newTotalPages = 1 + newCapacityCount;
        int newMaxSlot = newTotalPages * SLOTS_PER_PAGE;

        // Process slots that are being removed (from the end)
        for (int slot = TANK_SLOTS - 1; slot >= newMaxSlot; slot--) {
            // Clear the filter
            this.filterInventory.setFluidInSlot(slot, null);

            // Return fluid to network, drop remainder
            FluidStack fluid = this.fluidTanks[slot];
            if (fluid != null && fluid.amount > 0) {
                int remaining = returnFluidToNetwork(fluid);
                if (remaining > 0) dropFluidOnGround(new FluidStack(fluid.getFluid(), remaining));
                this.fluidTanks[slot] = null;
            }
        }

        refreshFilterMap();
        this.markDirty();
    }

    /**
     * Attempt to return a fluid to the ME network.
     * @return Amount that could NOT be inserted (remainder).
     */
    private int returnFluidToNetwork(FluidStack fluid) {
        if (fluid == null || fluid.amount <= 0) return 0;

        try {
            IStorageGrid storage = this.getProxy().getStorage();
            IMEInventory<IAEFluidStack> fluidStorage = storage.getInventory(
                AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class)
            );

            IAEFluidStack toInsert = AEFluidStack.fromFluidStack(fluid);
            IAEFluidStack notInserted = fluidStorage.injectItems(toInsert, Actionable.MODULATE, this.actionSource);

            if (notInserted == null) return 0;
            return (int) notInserted.getStackSize();
        } catch (GridAccessException e) {
            return fluid.amount;
        }
    }

    /**
     * Drop fluid on ground. For fluids, we just void them since there's no good way
     * to drop fluid without a container. A message could be logged.
     */
    private void dropFluidOnGround(FluidStack fluid) {
        // Fluids cannot be dropped as entities without containers
        // Log a warning or just void them
        if (fluid != null && fluid.amount > 0 && this.world != null && !this.world.isRemote) {
            // TODO: how do we handle that
            // Could spawn bucket items here if we wanted, but that's complex
            // For now, fluids that can't go back to network are voided
        }
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
        return countUpgrade(ItemOverflowCard.class) > 0;
    }

    public boolean hasTrashUnselectedUpgrade() {
        return countUpgrade(ItemTrashUnselectedCard.class) > 0;
    }

    /**
     * Clear filter slots only where the corresponding tank is empty.
     * This prevents orphaning fluids in the import interface.
     */
    @Override
    public void clearFilters() {
        int maxSlot = getTotalPages() * SLOTS_PER_PAGE;
        for (int i = 0; i < maxSlot; i++) {
            // Only clear filter if the corresponding tank is empty
            if (i >= TANK_SLOTS || this.fluidTanks[i] == null || this.fluidTanks[i].amount <= 0) {
                this.filterInventory.setFluidInSlot(i, null);
            }
        }

        this.refreshFilterMap();
        this.markDirty();
    }

    /**
     * Check if a specific tank has any fluid stored.
     */
    public boolean isTankEmpty(int slot) {
        if (slot < 0 || slot >= TANK_SLOTS) return true;

        return fluidTanks[slot] == null || fluidTanks[slot].amount <= 0;
    }

    /**
     * Get the fluid stored in a specific tank (may be null).
     */
    @Nullable
    public FluidStack getFluidInTank(int slot) {
        if (slot < 0 || slot >= TANK_SLOTS) return null;

        return fluidTanks[slot];
    }

    /**
     * Insert fluid into a specific tank slot.
     * Returns the amount actually inserted.
     *
     * @param slot The tank slot index
     * @param fluid The fluid to insert (not modified)
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

        this.markDirty();
        this.markForUpdate();

        return toInsert;
    }

    /**
     * Get the fluid filter for a specific slot.
     * Returns null if no filter is set.
     */
    @Nullable
    public IAEFluidStack getFilterFluid(int slot) {
        if (slot < 0 || slot >= FILTER_SLOTS) return null;

        return this.filterInventory.getFluidInSlot(slot);
    }

    /**
     * Set the fluid filter for a specific slot.
     * Pass null to clear the filter.
     */
    public void setFilterFluid(int slot, @Nullable IAEFluidStack fluid) {
        if (slot < 0 || slot >= FILTER_SLOTS) return;

        this.filterInventory.setFluidInSlot(slot, fluid);
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.filterInventory.readFromNBT(data, "fluidFilters");
        this.upgradeInventory.readFromNBT(data, "upgrades");
        this.maxSlotSize = data.getInteger("maxSlotSize");
        this.pollingRate = data.getInteger("pollingRate");

        if (this.maxSlotSize < TileImportInterface.MIN_MAX_SLOT_SIZE) this.maxSlotSize = TileImportInterface.MIN_MAX_SLOT_SIZE;
        if (this.pollingRate < 0) this.pollingRate = 0;

        // Read fluid tanks
        if (data.hasKey("fluidTanks", Constants.NBT.TAG_LIST)) {
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

    @Nonnull
    @Override
    public NBTTagCompound writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        this.filterInventory.writeToNBT(data, "fluidFilters");
        this.upgradeInventory.writeToNBT(data, "upgrades");
        data.setInteger("maxSlotSize", this.maxSlotSize);
        data.setInteger("pollingRate", this.pollingRate);

        // Write fluid tanks
        NBTTagList tankList = new NBTTagList();
        for (int i = 0; i < TANK_SLOTS; i++) {
            NBTTagCompound tankTag = new NBTTagCompound();
            if (this.fluidTanks[i] != null) {
                this.fluidTanks[i].writeToNBT(tankTag);
            } else {
                tankTag.setBoolean("Empty", true);
            }
            tankList.appendTag(tankTag);
        }
        data.setTag("fluidTanks", tankList);

        return data;
    }

    @Nonnull
    @Override
    public NBTTagCompound downloadSettings(SettingsFrom from) {
        NBTTagCompound output = super.downloadSettings(from);
        if (output == null) output = new NBTTagCompound();

        // Save slot size and polling rate for both memory card and dismantle
        output.setInteger("maxSlotSize", this.maxSlotSize);
        output.setInteger("pollingRate", this.pollingRate);

        // Save filter inventory only when dismantling (not for memory card)
        if (from == SettingsFrom.DISMANTLE_ITEM) {
            this.filterInventory.writeToNBT(output, "fluidFilters");
        }

        return output;
    }

    @Override
    public void uploadSettings(SettingsFrom from, NBTTagCompound compound, EntityPlayer player) {
        super.uploadSettings(from, compound, player);

        if (compound == null) return;

        // Load slot size and polling rate for both memory card and dismantle
        if (compound.hasKey("maxSlotSize")) {
            this.setMaxSlotSize(compound.getInteger("maxSlotSize"));
        }
        if (compound.hasKey("pollingRate")) {
            this.setPollingRate(compound.getInteger("pollingRate"), player);
        }

        // Merge filter inventory from memory card instead of replacing
        if (compound.hasKey("fluidFilters")) {
            mergeFiltersFromNBT(compound, "fluidFilters", player);
        }
    }

    /**
     * Merge fluid filters from NBT into the current filter inventory.
     * Only adds filters to empty slots; skips filters that already exist.
     * Reports to the player which filters couldn't be added if slots were full.
     *
     * @param data The NBT compound containing the filter data
     * @param name The key name for the filter tag list
     * @param player The player to notify about results, or null to skip notification
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
                // No empty slots - track this filter as skipped
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
            String filters = skippedFilters.stream()
                .map(FluidStack::getLocalizedName)
                .reduce((a, b) -> a + "\n- " + b)
                .orElse("");
            player.sendMessage(new TextComponentTranslation(
                "message.cells.import_fluid_interface.filters_not_added",
                skippedFilters.size(),
                filters
            ));
        }
    }

    /**
     * Find the first empty filter slot.
     * @return The slot index, or -1 if no empty slots are available
     */
    private int findEmptyFilterSlot() {
        for (int i = 0; i < this.filterInventory.getSlots(); i++) {
            if (this.filterInventory.getFluidInSlot(i) == null) return i;
        }

        return -1;
    }

    @Override
    protected boolean readFromStream(final ByteBuf data) throws IOException {
        boolean changed = super.readFromStream(data);

        // Read fluid tanks from stream for client sync
        for (int i = 0; i < TANK_SLOTS; i++) {
            boolean hasFluid = data.readBoolean();
            if (hasFluid) {
                // Read fluid registry name length and bytes
                int nameLen = data.readShort();
                byte[] nameBytes = new byte[nameLen];
                data.readBytes(nameBytes);
                String fluidName = new String(nameBytes, StandardCharsets.UTF_8);

                int amount = data.readInt();

                Fluid fluid = FluidRegistry.getFluid(fluidName);
                if (fluid != null) {
                    FluidStack oldFluid = this.fluidTanks[i];
                    this.fluidTanks[i] = new FluidStack(fluid, amount);
                    if (oldFluid == null || !oldFluid.isFluidStackIdentical(this.fluidTanks[i])) {
                        changed = true;
                    }
                } else {
                    if (this.fluidTanks[i] != null) changed = true;
                    this.fluidTanks[i] = null;
                }
            } else {
                if (this.fluidTanks[i] != null) changed = true;
                this.fluidTanks[i] = null;
            }
        }

        return changed;
    }

    @Override
    protected void writeToStream(final ByteBuf data) throws IOException {
        super.writeToStream(data);

        // Write fluid tanks to stream for client sync
        for (int i = 0; i < TANK_SLOTS; i++) {
            FluidStack fluid = this.fluidTanks[i];
            if (fluid != null && fluid.amount > 0) {
                data.writeBoolean(true);

                // Write fluid registry name as length-prefixed UTF-8 bytes
                byte[] nameBytes = fluid.getFluid().getName().getBytes(StandardCharsets.UTF_8);
                data.writeShort(nameBytes.length);
                data.writeBytes(nameBytes);

                data.writeInt(fluid.amount);
            } else {
                data.writeBoolean(false);
            }
        }
    }

    @Nonnull
    @Override
    public IItemHandler getInternalInventory() {
        // Fluid tile doesn't use item-based storage; return a dummy inventory
        // to satisfy AENetworkInvTile's contract without interfering with fluid tanks
        return this.dummyInventory;
    }

    public IAEFluidTank getFilterInventory() {
        return this.filterInventory;
    }

    public AppEngInternalInventory getUpgradeInventory() {
        return this.upgradeInventory;
    }

    @Override
    public int getMaxSlotSize() {
        return this.maxSlotSize;
    }

    @Override
    public void setMaxSlotSize(int size) {
        this.maxSlotSize = Math.max(TileImportInterface.MIN_MAX_SLOT_SIZE, size);
        this.markDirty();
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
     * @param ticks Polling rate in ticks (0 = adaptive)
     * @param player Player to notify if re-registration fails, or null to skip notification
     */
    public void setPollingRate(int ticks, EntityPlayer player) {
        this.pollingRate = Math.max(0, ticks);
        this.markDirty();

        // Re-register with the tick manager to apply the new TickingRequest bounds.
        // Uses TickManagerHelper to purge stale TickTrackers from AE2's internal
        // PriorityQueue before re-registering (see TickManagerHelper for details).
        if (!TickManagerHelper.reRegisterTickable(this.getProxy().getNode(), this)) {
            if (player != null) {
                player.sendMessage(new TextComponentTranslation("chat.cells.polling_rate_delayed"));
            }
        }
    }

    @Override
    public BlockPos getHostPos() {
        return this.getPos();
    }

    @Override
    public World getHostWorld() {
        return this.getWorld();
    }

    // IImportInterfaceHost implementation

    @Override
    public int getMainGuiId() {
        return CellsGuiHandler.GUI_FLUID_IMPORT_INTERFACE;
    }

    @Override
    public String getGuiTitleLangKey() {
        return "gui.cells.import_fluid_interface.title";
    }

    @Override
    public ItemStack getBackButtonStack() {
        return new ItemStack(this.getBlockType());
    }

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removed, ItemStack added) {
        // Upgrade changed - refresh cached upgrade flags
        if (inv == this.upgradeInventory) this.refreshUpgrades();

        this.markDirty();
    }

    @Override
    public void getDrops(final World w, final BlockPos pos, final List<ItemStack> drops) {
        // Only drop upgrade cards; filter items are ghosts and fluids can't be dropped as items
        for (int i = 0; i < this.upgradeInventory.getSlots(); i++) {
            ItemStack stack = this.upgradeInventory.getStackInSlot(i);
            if (!stack.isEmpty()) drops.add(stack);
        }
    }

    @Override
    @Nonnull
    public AECableType getCableConnectionType(@Nonnull final AEPartLocation dir) {
        return AECableType.SMART;
    }

    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    // IGridTickable implementation

    @Override
    @Nonnull
    public TickingRequest getTickingRequest(@Nonnull final IGridNode node) {
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

    @Override
    @Nonnull
    public TickRateModulation tickingRequest(@Nonnull final IGridNode node, final int ticksSinceLastCall) {
        if (!this.getProxy().isActive()) return TickRateModulation.SLEEP;

        boolean didWork = importFluids();

        if (this.pollingRate > 0) return TickRateModulation.SAME;

        return didWork ? TickRateModulation.FASTER : (hasWorkToDo() ? TickRateModulation.SLOWER : TickRateModulation.SLEEP);
    }

    private boolean hasWorkToDo() {
        for (int i : this.filterToSlotMap.values()) {
            if (this.fluidTanks[i] != null && this.fluidTanks[i].amount > 0) return true;
        }

        return false;
    }

    /**
     * Import fluids from internal tanks into the ME network.
     * @return true if any fluids were imported
     */
    private boolean importFluids() {
        boolean didWork = false;

        try {
            IStorageGrid storage = this.getProxy().getStorage();
            IMEInventory<IAEFluidStack> fluidStorage = storage.getInventory(
                AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class)
            );

            for (int i : this.filterToSlotMap.values()) {
                FluidStack fluid = this.fluidTanks[i];
                if (fluid == null || fluid.amount <= 0) continue;

                // Try to insert into network
                IAEFluidStack aeStack = AEFluidStack.fromFluidStack(fluid);
                IAEFluidStack remaining = fluidStorage.injectItems(aeStack, Actionable.MODULATE, this.actionSource);

                if (remaining == null) {
                    // All fluid inserted
                    this.fluidTanks[i] = null;
                    didWork = true;
                } else if (remaining.getStackSize() < fluid.amount) {
                    // Some fluid inserted
                    fluid.amount = (int) remaining.getStackSize();
                    didWork = true;
                }
                // else: nothing inserted, network full
            }
        } catch (GridAccessException e) {
            // Not connected to grid
        }

        // Sync to client if fluid state changed
        if (didWork) this.markForUpdate();

        return didWork;
    }

    // Capability handling

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) return true;

        return super.hasCapability(capability, facing);
    }

    @Override
    @Nullable
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(this.filteredFluidHandler);
        }

        return super.getCapability(capability, facing);
    }

    /**
     * Wrapper handler that provides filtered fluid insertion.
     * Fluids are automatically routed to the appropriate tank based on filters.
     * Does not allow extraction (import-only interface).
     */
    private static class FilteredFluidHandler implements IFluidHandler {
        private final TileFluidImportInterface tile;

        public FilteredFluidHandler(TileFluidImportInterface tile) {
            this.tile = tile;
        }

        @Override
        public IFluidTankProperties[] getTankProperties() {
            List<IFluidTankProperties> props = new ArrayList<>();

            for (int slot : tile.filterSlotList) {
                FluidStack contents = tile.fluidTanks[slot];
                int capacity = tile.maxSlotSize;

                // Use cached filter key for this slot
                FluidStackKey filterKey = tile.slotToFilterMap.get(slot);

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
            Integer slot = tile.filterToSlotMap.get(key);
            if (slot == null) return tile.installedTrashUnselectedUpgrade ? resource.amount : 0;

            // Insert into matching tank
            FluidStack existing = tile.fluidTanks[slot];
            int capacity = tile.maxSlotSize;
            int currentAmount = (existing == null) ? 0 : existing.amount;
            int space = capacity - currentAmount;

            // Tank full - void overflow if upgrade installed, reject otherwise
            if (space <= 0) return tile.installedOverflowUpgrade ? resource.amount : 0;

            int toInsert = Math.min(resource.amount, space);
            if (doFill) {
                if (existing == null) {
                    tile.fluidTanks[slot] = new FluidStack(resource, toInsert);
                } else {
                    existing.amount += toInsert;
                }

                tile.markDirty();
                tile.markForUpdate(); // Sync to client for rendering

                // Wake up the tile to import fluids (adaptive polling only)
                if (tile.pollingRate <= 0) {
                    try {
                        tile.getProxy().getTick().alertDevice(tile.getProxy().getNode());
                    } catch (GridAccessException e) {
                        // Not connected to grid
                    }
                }
            }

            // If overflow upgrade installed, accept all fluid (void the excess)
            int excess = resource.amount - toInsert;
            if (excess > 0 && tile.installedOverflowUpgrade) return resource.amount;

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
}
