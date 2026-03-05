package com.cells.blocks.fluidexportinterface;

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

import com.cells.blocks.importinterface.TileImportInterface;
import com.cells.gui.CellsGuiHandler;
import com.cells.items.ItemOverflowCard;
import com.cells.util.FluidStackKey;
import com.cells.util.TickManagerHelper;


/**
 * Tile entity for the Fluid Export Interface block.
 * Provides filter slots (fluid-based filters) and internal fluid tanks.
 * Requests fluids from the ME network that match the filter configuration.
 * Exposes stored fluids for external extraction.
 * Supports Capacity Cards to add additional pages of 36 tanks each.
 */
public class TileFluidExportInterface extends AENetworkInvTile implements IGridTickable, IAEAppEngInventory, IAEFluidInventory, IFluidExportInterfaceInventoryHost {

    public static final int SLOTS_PER_PAGE = 36;
    public static final int MAX_CAPACITY_CARDS = 4;
    public static final int MAX_PAGES = 1 + MAX_CAPACITY_CARDS;
    public static final int FILTER_SLOTS = SLOTS_PER_PAGE * MAX_PAGES;
    public static final int TANK_SLOTS = SLOTS_PER_PAGE * MAX_PAGES;
    public static final int TOTAL_SLOTS = Math.min(FILTER_SLOTS, TANK_SLOTS);
    public static final int UPGRADE_SLOTS = 4;
    public static final int DEFAULT_MAX_SLOT_SIZE = 16000; // Default tank capacity in mB (16 buckets)

    // Filter inventory - stores fluid types directly
    private final AEFluidInventory filterInventory = new AEFluidInventory(this, FILTER_SLOTS, 1);

    // Internal fluid storage - one tank per filter slot
    private final FluidStack[] fluidTanks = new FluidStack[TANK_SLOTS];

    // Upgrade inventory - accepts only specific upgrade cards
    private final AppEngInternalInventory upgradeInventory;

    // Dummy inventory for AENetworkInvTile contract
    private final AppEngInternalInventory dummyInventory = new AppEngInternalInventory(this, 0, 0);

    // Wrapper that exposes fluid tanks for extraction
    private final ExportFluidHandler exportFluidHandler;

    // Max tank capacity in mB for each tank
    private int maxSlotSize = DEFAULT_MAX_SLOT_SIZE;

    // Polling rate in ticks (0 = adaptive AE2 default, nonzero = fixed interval)
    private int pollingRate = TileImportInterface.DEFAULT_POLLING_RATE;

    // Number of installed capacity upgrades (adds pages)
    private int installedCapacityUpgrades = 0;

    // Current GUI page index (0-based)
    private int currentPage = 0;

    // Mapping of filter fluids to their corresponding tank index
    final Map<FluidStackKey, Integer> filterToSlotMap = new HashMap<>();

    // Reverse mapping: slot index to filter key
    final Map<Integer, FluidStackKey> slotToFilterMap = new HashMap<>();

    // List of slot indices that have filters
    List<Integer> filterSlotList = new ArrayList<>();

    // Action source for network operations
    private final IActionSource actionSource;

    public TileFluidExportInterface() {
        this.getProxy().setFlags(GridFlags.REQUIRE_CHANNEL);
        this.getProxy().setIdlePowerUsage(1.0);
        this.actionSource = new MachineSource(this);

        // Create upgrade inventory with filtering for specific upgrade cards
        this.upgradeInventory = new AppEngInternalInventory(this, UPGRADE_SLOTS, 1) {
            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return TileFluidExportInterface.this.isValidUpgrade(stack);
            }
        };

        this.exportFluidHandler = new ExportFluidHandler(this);

        refreshUpgrades();
        refreshFilterMap();
    }

    /**
     * Refresh the filter to slot mapping.
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

    @Override
    public void onFluidInventoryChanged(IAEFluidTank inv, int slot, InvOperation operation, FluidStack added, FluidStack removed) {
        if (inv == this.filterInventory) {
            // If filter was removed or changed, handle orphaned fluids
            if (removed != null) {
                this.onFilterChanged(slot);
            } else {
                this.refreshFilterMap();
                this.wakeUpIfAdaptive();
                this.markDirty();
            }
        }
    }

    @Override
    public void onFluidInventoryChanged(IAEFluidTank inv, int slot) {
        if (inv == this.filterInventory) {
            // For the simple callback, handle as a filter change
            this.onFilterChanged(slot);
        }
    }

    public boolean isValidUpgrade(ItemStack stack) {
        if (stack.isEmpty()) return false;

        if (stack.getItem() instanceof ItemOverflowCard) {
            return countUpgrade(ItemOverflowCard.class) < 1;
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

    private int countUpgrade(Class<?> itemClass) {
        int count = 0;
        for (int i = 0; i < this.upgradeInventory.getSlots(); i++) {
            ItemStack existing = this.upgradeInventory.getStackInSlot(i);
            if (!existing.isEmpty() && itemClass.isInstance(existing.getItem())) count++;
        }

        return count;
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
     * Refresh the status of installed upgrades. Should be called whenever upgrade slots change.
     */
    public void refreshUpgrades() {
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
     * Handle reduction in capacity cards - clear filters and return fluids from deleted pages.
     */
    private void handleCapacityReduction(int newCapacityCount) {
        int newTotalPages = 1 + newCapacityCount;
        int newMaxSlot = newTotalPages * SLOTS_PER_PAGE;

        // Process slots that are being removed (from the end)
        for (int slot = TANK_SLOTS - 1; slot >= newMaxSlot; slot--) {
            // Clear the filter
            this.filterInventory.setFluidInSlot(slot, null);

            // Return fluid to network
            FluidStack fluid = this.fluidTanks[slot];
            if (fluid != null && fluid.amount > 0) {
                returnFluidToNetwork(fluid, true);
                this.fluidTanks[slot] = null;
            }
        }

        refreshFilterMap();
        this.markDirty();
    }

    /**
     * Return fluid to the ME network.
     * @return Amount that could NOT be inserted (remainder).
     */
    private int returnFluidToNetwork(FluidStack fluid, boolean force) {
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

    public boolean isTankEmpty(int slot) {
        if (slot < 0 || slot >= TANK_SLOTS) return true;

        return fluidTanks[slot] == null || fluidTanks[slot].amount <= 0;
    }

    @Nullable
    public FluidStack getFluidInTank(int slot) {
        if (slot < 0 || slot >= TANK_SLOTS) return null;

        return fluidTanks[slot];
    }

    /**
     * Drain fluid from a specific tank slot.
     *
     * @param slot The tank slot index
     * @param maxDrain Maximum amount to drain
     * @param doDrain Whether to actually drain or just simulate
     * @return The fluid extracted, or null if nothing extracted
     */
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

            this.markDirty();
            this.markForUpdate();

            // Wake up the tile to request more fluids
            this.wakeUpIfAdaptive();
        }

        return drained;
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

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.filterInventory.readFromNBT(data, "fluidFilters");
        this.upgradeInventory.readFromNBT(data, "upgrades");
        this.maxSlotSize = data.getInteger("maxSlotSize");
        this.pollingRate = data.getInteger("pollingRate");

        if (this.maxSlotSize < TileImportInterface.MIN_MAX_SLOT_SIZE) {
            this.maxSlotSize = TileImportInterface.MIN_MAX_SLOT_SIZE;
        }

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

        output.setInteger("maxSlotSize", this.maxSlotSize);
        output.setInteger("pollingRate", this.pollingRate);

        if (from == SettingsFrom.DISMANTLE_ITEM) {
            this.filterInventory.writeToNBT(output, "fluidFilters");
        }

        return output;
    }

    @Override
    public void uploadSettings(SettingsFrom from, NBTTagCompound compound, EntityPlayer player) {
        super.uploadSettings(from, compound, player);

        if (compound == null) return;

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
                "message.cells.export_fluid_interface.filters_not_added",
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

        for (int i = 0; i < TANK_SLOTS; i++) {
            boolean hasFluid = data.readBoolean();
            if (hasFluid) {
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

        for (int i = 0; i < TANK_SLOTS; i++) {
            FluidStack fluid = this.fluidTanks[i];
            if (fluid != null && fluid.amount > 0) {
                data.writeBoolean(true);

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
        int oldSize = this.maxSlotSize;

        this.maxSlotSize = Math.max(TileImportInterface.MIN_MAX_SLOT_SIZE, size);
        this.markDirty();

        // If slot size was reduced, return overflow fluids to the network
        if (oldSize > this.maxSlotSize) returnOverflowToNetwork();

        // Tanks may now have room for more fluid after increasing the limit
        if (oldSize < this.maxSlotSize) this.wakeUpIfAdaptive();
    }

    @Override
    public int getPollingRate() {
        return this.pollingRate;
    }

    @Override
    public void setPollingRate(int ticks) {
        this.setPollingRate(ticks, null);
    }

    public void setPollingRate(int ticks, EntityPlayer player) {
        this.pollingRate = Math.max(0, ticks);
        this.markDirty();

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

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removed, ItemStack added) {
        if (inv == this.upgradeInventory) this.refreshUpgrades();

        this.markDirty();
    }

    /**
     * Called when a fluid filter changes - check for orphaned fluids and return to network.
     *
     * @param slot The slot that changed
     */
    public void onFilterChanged(int slot) {
        // If there was a filter and it changed, return orphaned fluids
        FluidStack tankFluid = this.fluidTanks[slot];
        if (tankFluid != null && tankFluid.amount > 0) {
            IAEFluidStack filterFluid = this.filterInventory.getFluidInSlot(slot);

            // Check if tank fluid doesn't match new filter (orphaned)
            boolean isOrphaned = filterFluid == null ||
                !filterFluid.getFluidStack().isFluidEqual(tankFluid);

            if (isOrphaned) returnTankToNetwork(slot);
        }

        this.refreshFilterMap();
        this.wakeUpIfAdaptive();
        this.markDirty();
    }

    /**
     * Try to return all fluids in a specific tank slot back to the ME network.
     * Fluids that cannot be returned stay in the tank.
     *
     * @param slot The tank slot index to return fluids from
     * @return true if all fluids were returned, false if some remain
     */
    public boolean returnTankToNetwork(int slot) {
        if (slot < 0 || slot >= TANK_SLOTS) return true;

        FluidStack fluid = this.fluidTanks[slot];
        if (fluid == null || fluid.amount <= 0) return true;

        int notInserted = insertFluidsIntoNetwork(fluid);

        // Update the tank with whatever couldn't be returned
        if (notInserted <= 0) {
            this.fluidTanks[slot] = null;
        } else {
            fluid.amount = notInserted;
        }

        this.markDirty();
        this.markForUpdate();

        return notInserted <= 0;
    }

    /**
     * Return all orphaned fluids (fluids that don't match their filter) to the ME network.
     */
    private void returnOrphanedFluidsToNetwork() {
        for (int i = 0; i < TANK_SLOTS; i++) {
            FluidStack tankFluid = this.fluidTanks[i];
            if (tankFluid == null || tankFluid.amount <= 0) continue;

            IAEFluidStack filterFluid = this.filterInventory.getFluidInSlot(i);

            // If no filter or fluids don't match filter, try to return them
            boolean isOrphaned = filterFluid == null ||
                !filterFluid.getFluidStack().isFluidEqual(tankFluid);

            if (isOrphaned) {
                returnTankToNetwork(i);
            }
        }
    }

    /**
     * Return overflow fluids (fluids exceeding maxSlotSize) back to the ME network.
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

            // Reduce the fluid in the tank by amount successfully returned
            fluid.amount -= (overflow - notInserted);
            if (fluid.amount <= 0) {
                this.fluidTanks[i] = null;
            }
        }

        this.markDirty();
        this.markForUpdate();
    }

    /**
     * Clear all filters. Only clears filters where the corresponding tank
     * is empty - orphaned fluids will be returned to network on the next tick.
     */
    public void clearFilters() {
        for (int i = 0; i < FILTER_SLOTS; i++) {
            // Only clear filter if the corresponding tank is empty
            if (i >= TANK_SLOTS || this.fluidTanks[i] == null || this.fluidTanks[i].amount <= 0) {
                this.filterInventory.setFluidInSlot(i, null);
            }
        }

        this.refreshFilterMap();
        this.markDirty();
        this.markForUpdate();
    }

    /**
     * Try to insert fluids into the ME network.
     *
     * @param fluid The fluid to insert
     * @return Amount of fluid that couldn't be inserted
     */
    private int insertFluidsIntoNetwork(FluidStack fluid) {
        if (fluid == null || fluid.amount <= 0) return 0;

        try {
            IStorageGrid storage = this.getProxy().getStorage();
            IMEInventory<IAEFluidStack> fluidStorage = storage.getInventory(
                AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class)
            );

            IAEFluidStack toInsert = AEFluidStack.fromFluidStack(fluid);
            if (toInsert == null) return fluid.amount;

            IAEFluidStack notInserted = fluidStorage.injectItems(toInsert, Actionable.MODULATE, this.actionSource);

            if (notInserted == null || notInserted.getStackSize() == 0) {
                return 0;
            }

            return (int) notInserted.getStackSize();
        } catch (GridAccessException e) {
            // Not connected to grid, return all
            return fluid.amount;
        }
    }

    @Override
    public void getDrops(final World w, final BlockPos pos, final List<ItemStack> drops) {
        // Drop all upgrades
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

    // IFluidExportInterfaceHost implementation

    @Override
    public int getMainGuiId() {
        return CellsGuiHandler.GUI_FLUID_EXPORT_INTERFACE;
    }

    @Override
    public String getGuiTitleLangKey() {
        return "gui.cells.export_fluid_interface.title";
    }

    @Override
    public ItemStack getBackButtonStack() {
        return new ItemStack(this.getBlockType());
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

        boolean didWork = exportFluids();

        if (this.pollingRate > 0) return TickRateModulation.SAME;

        return didWork ? TickRateModulation.FASTER : (hasWorkToDo() ? TickRateModulation.SLOWER : TickRateModulation.SLEEP);
    }

    private boolean hasWorkToDo() {
        for (int i : this.filterSlotList) {
            FluidStack current = this.fluidTanks[i];
            int currentAmount = (current == null) ? 0 : current.amount;
            if (currentAmount < this.maxSlotSize) return true;
        }

        return false;
    }

    /**
     * Wake up the tick manager if using adaptive polling (rate=0).
     * Called when filters or settings change to ensure the device starts ticking
     * to pull fluids that now match the updated configuration.
     */
    private void wakeUpIfAdaptive() {
        if (this.pollingRate > 0) return;

        try {
            this.getProxy().getTick().alertDevice(this.getProxy().getNode());
        } catch (GridAccessException e) {
            // Not connected to grid
        }
    }

    /**
     * Export fluids from the ME network into tanks.
     * First returns any orphaned or overflow fluids to the network,
     * then requests fluids from the network for tanks that need them.
     */
    private boolean exportFluids() {
        boolean didWork = false;

        try {
            IStorageGrid storage = this.getProxy().getStorage();
            IMEInventory<IAEFluidStack> fluidStorage = storage.getInventory(
                AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class)
            );

            // First, return any orphaned or overflow fluids to the network
            // This must happen before requesting new fluids
            returnOrphanedFluidsToNetwork();
            returnOverflowToNetwork();

            for (int i : this.filterSlotList) {
                IAEFluidStack filterFluid = this.filterInventory.getFluidInSlot(i);
                if (filterFluid == null) continue;

                FluidStack current = this.fluidTanks[i];

                // Skip slots where current fluids don't match filter (orphaned fluids)
                // This prevents requesting more fluids when the tank has mismatched content
                if (current != null && current.amount > 0) {
                    if (!filterFluid.getFluidStack().isFluidEqual(current)) continue;
                }

                int currentAmount = (current == null) ? 0 : current.amount;
                int space = this.maxSlotSize - currentAmount;
                if (space <= 0) continue;

                // Request fluids from network
                IAEFluidStack request = filterFluid.copy();
                request.setStackSize(space);

                // Try to extract from network
                IAEFluidStack extracted = fluidStorage.extractItems(request, Actionable.MODULATE, this.actionSource);
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
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(this.exportFluidHandler);
        }

        return super.getCapability(capability, facing);
    }

    /**
     * Wrapper handler that exposes tanks for extraction only.
     */
    private static class ExportFluidHandler implements IFluidHandler {
        private final TileFluidExportInterface tile;

        public ExportFluidHandler(TileFluidExportInterface tile) {
            this.tile = tile;
        }

        @Override
        public IFluidTankProperties[] getTankProperties() {
            List<IFluidTankProperties> props = new ArrayList<>();

            for (int slot : tile.filterSlotList) {
                FluidStack contents = tile.fluidTanks[slot];
                int capacity = tile.maxSlotSize;

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
                        return false; // Export interface doesn't accept input
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

            Integer slot = tile.filterToSlotMap.get(key);
            if (slot == null) return null;

            return tile.drainFluidFromTank(slot, resource.amount, doDrain);
        }

        @Nullable
        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            // Drain from any available tank (first non-empty)
            for (int slot : tile.filterSlotList) {
                FluidStack fluid = tile.fluidTanks[slot];
                if (fluid != null && fluid.amount > 0) {
                    return tile.drainFluidFromTank(slot, maxDrain, doDrain);
                }
            }

            return null;
        }
    }
}
