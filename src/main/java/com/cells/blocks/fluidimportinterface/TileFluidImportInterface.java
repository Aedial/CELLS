package com.cells.blocks.fluidimportinterface;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
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

import java.nio.charset.StandardCharsets;

import com.cells.blocks.importinterface.IImportInterfaceHost;
import com.cells.blocks.importinterface.TileImportInterface;
import com.cells.gui.CellsGuiHandler;
import com.cells.items.ItemOverflowCard;
import com.cells.items.ItemTrashUnselectedCard;
import com.cells.util.FluidStackKey;
import com.cells.util.TickManagerHelper;


/**
 * Tile entity for the Fluid Import Interface block.
 * Provides 36 filter slots (fluid-based filters) and 36 internal fluid tanks.
 * Only accepts fluids that match the filter in the corresponding slot.
 * Automatically imports stored fluids into the ME network.
 */
public class TileFluidImportInterface extends AENetworkInvTile implements IGridTickable, IAEAppEngInventory, IAEFluidInventory, IImportInterfaceHost {

    public static final int FILTER_SLOTS = 36;
    public static final int TANK_SLOTS = 36;
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

    // Mapping of filter fluids to their corresponding tank index for quick lookup
    final Map<FluidStackKey, Integer> filterToSlotMap = new HashMap<>();

    // List of filter fluids for quick access
    List<FluidStackKey> filterFluidList = new ArrayList<>();

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
    }

    /**
     * Refresh the filter to slot mapping. Should be called whenever filter slots change.
     * Reads fluids directly from the AEFluidInventory.
     */
    public void refreshFilterMap() {
        this.filterToSlotMap.clear();

        for (int i = 0; i < FILTER_SLOTS && i < TANK_SLOTS; i++) {
            IAEFluidStack filterFluid = this.filterInventory.getFluidInSlot(i);
            if (filterFluid == null) continue;

            FluidStack fluid = filterFluid.getFluidStack();
            if (fluid == null) continue;

            FluidStackKey key = FluidStackKey.of(fluid);
            if (key != null) this.filterToSlotMap.put(key, i);
        }

        this.filterFluidList = new ArrayList<>(filterToSlotMap.keySet());
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
     * Only accepts Overflow Card and Trash Unselected Card, max 1 of each.
     */
    public boolean isValidUpgrade(ItemStack stack) {
        if (stack.isEmpty()) return false;

        if (stack.getItem() instanceof ItemOverflowCard) {
            return countUpgrade(ItemOverflowCard.class) < 1;
        }

        if (stack.getItem() instanceof ItemTrashUnselectedCard) {
            return countUpgrade(ItemTrashUnselectedCard.class) < 1;
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

    public boolean hasOverflowUpgrade() {
        return countUpgrade(ItemOverflowCard.class) > 0;
    }

    public boolean hasTrashUnselectedUpgrade() {
        return countUpgrade(ItemTrashUnselectedCard.class) > 0;
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
        this.pollingRate = Math.max(0, ticks);
        this.markDirty();

        // Re-register with the tick manager to apply the new TickingRequest bounds.
        // Uses TickManagerHelper to purge stale TickTrackers from AE2's internal
        // PriorityQueue before re-registering (see TickManagerHelper for details).
        TickManagerHelper.reRegisterTickable(this.getProxy().getNode(), this);
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
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removed, ItemStack added) {
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
    public AECableType getCableConnectionType(final AEPartLocation dir) {
        return AECableType.SMART;
    }

    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    // IGridTickable implementation

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
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
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
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

                IAEFluidStack aeStack = AEFluidStack.fromFluidStack(fluid);
                if (aeStack == null) continue;

                // Try to insert into network
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

            for (Map.Entry<FluidStackKey, Integer> entry : tile.filterToSlotMap.entrySet()) {
                int slot = entry.getValue();
                FluidStack contents = tile.fluidTanks[slot];
                int capacity = tile.maxSlotSize;

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
                        return entry.getKey().matches(fluidStack);
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
