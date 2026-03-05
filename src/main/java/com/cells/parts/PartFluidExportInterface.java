package com.cells.parts;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
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
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
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
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartModel;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.core.settings.TickRates;
import appeng.fluids.util.AEFluidInventory;
import appeng.fluids.util.AEFluidStack;
import appeng.fluids.util.IAEFluidInventory;
import appeng.fluids.util.IAEFluidTank;
import appeng.items.parts.PartModels;
import appeng.me.GridAccessException;
import appeng.me.helpers.MachineSource;
import appeng.parts.PartBasicState;
import appeng.parts.PartModel;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.SettingsFrom;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;
import appeng.util.item.AEItemStack;

import com.cells.Tags;
import com.cells.blocks.fluidexportinterface.IFluidExportInterfaceInventoryHost;
import com.cells.blocks.importinterface.TileImportInterface;
import com.cells.gui.CellsGuiHandler;
import com.cells.util.FluidStackKey;
import com.cells.util.TickManagerHelper;


/**
 * Part version of the Fluid Export Interface.
 * Can be placed on cables and behaves identically to the block version.
 * Requests fluids from the network and exposes them for extraction.
 */
public class PartFluidExportInterface extends PartBasicState implements IGridTickable, IAEAppEngInventory, IAEFluidInventory, IFluidExportInterfaceInventoryHost {

    public static final ResourceLocation MODEL_BASE = new ResourceLocation(Tags.MODID, "part/export_fluid_interface_base");

    @PartModels
    public static final PartModel MODELS_OFF = new PartModel(MODEL_BASE, new ResourceLocation(Tags.MODID, "part/export_fluid_interface_off"));

    @PartModels
    public static final PartModel MODELS_ON = new PartModel(MODEL_BASE, new ResourceLocation(Tags.MODID, "part/export_fluid_interface_on"));

    @PartModels
    public static final PartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, new ResourceLocation(Tags.MODID, "part/export_fluid_interface_has_channel"));

    // Pagination constants
    public static final int SLOTS_PER_PAGE = 36;
    public static final int MAX_CAPACITY_CARDS = 4;
    public static final int MAX_PAGES = MAX_CAPACITY_CARDS + 1;  // Base page + 4 capacity card pages

    // Total slots/tanks = base page (36) + 4 capacity pages (36 each) = 180
    public static final int FILTER_SLOTS = SLOTS_PER_PAGE * MAX_PAGES;
    public static final int TANK_SLOTS = SLOTS_PER_PAGE * MAX_PAGES;
    public static final int TOTAL_SLOTS = Math.min(FILTER_SLOTS, TANK_SLOTS);
    public static final int UPGRADE_SLOTS = 4;
    public static final int DEFAULT_MAX_SLOT_SIZE = 16000; // mB (16 buckets)

    // Filter inventory - fluid filters (sized for max capacity)
    private final AEFluidInventory filterInventory = new AEFluidInventory(this, FILTER_SLOTS, 1);

    // Internal fluid storage (sized for max capacity)
    private final FluidStack[] fluidTanks = new FluidStack[TANK_SLOTS];

    // Upgrade inventory
    private final AppEngInternalInventory upgradeInventory;

    // External access wrapper
    private final ExportFluidHandler exportFluidHandler;

    // Config
    private int maxSlotSize = DEFAULT_MAX_SLOT_SIZE;
    private int pollingRate = TileImportInterface.DEFAULT_POLLING_RATE;

    // Pagination state
    private int installedCapacityUpgrades = 0;
    private int currentPage = 0;

    // Filter mapping
    final Map<FluidStackKey, Integer> filterToSlotMap = new HashMap<>();
    List<FluidStackKey> filterFluidList = new ArrayList<>();

    // Action source
    private final IActionSource actionSource;

    public PartFluidExportInterface(final ItemStack is) {
        super(is);
        this.actionSource = new MachineSource(this);

        this.upgradeInventory = new AppEngInternalInventory(this, UPGRADE_SLOTS, 1) {
            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return PartFluidExportInterface.this.isValidUpgrade(stack);
            }
        };

        this.exportFluidHandler = new ExportFluidHandler(this);

        refreshUpgrades();
        refreshFilterMap();
    }

    // IFluidExportInterfaceInventoryHost implementation

    @Override
    public int getMaxSlotSize() {
        return this.maxSlotSize;
    }

    @Override
    public void setMaxSlotSize(int size) {
        int oldSize = this.maxSlotSize;

        this.maxSlotSize = Math.max(TileImportInterface.MIN_MAX_SLOT_SIZE, size);
        this.getHost().markForSave();

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
        this.getHost().markForSave();

        if (!TickManagerHelper.reRegisterTickable(this.getProxy().getNode(), this)) {
            if (player != null) {
                player.sendMessage(new TextComponentTranslation("chat.cells.polling_rate_delayed"));
            }
        }
    }

    @Override
    public BlockPos getHostPos() {
        return this.getHost().getLocation().getPos();
    }

    @Override
    public World getHostWorld() {
        return this.getHost().getLocation().getWorld();
    }

    @Override
    public boolean isTankEmpty(int slot) {
        if (slot < 0 || slot >= TANK_SLOTS) return true;

        return fluidTanks[slot] == null || fluidTanks[slot].amount <= 0;
    }

    @Override
    @Nullable
    public FluidStack drainFluidFromTank(int slot, int maxDrain, boolean doDrain) {
        if (slot < 0 || slot >= TANK_SLOTS) return null;

        FluidStack existing = this.fluidTanks[slot];
        if (existing == null || existing.amount <= 0) return null;

        int toDrain = Math.min(maxDrain, existing.amount);
        FluidStack result = new FluidStack(existing, toDrain);

        if (doDrain) {
            existing.amount -= toDrain;
            if (existing.amount <= 0) this.fluidTanks[slot] = null;

            this.getHost().markForSave();
            this.getHost().markForUpdate();

            // Wake up to request more from network
            this.wakeUpIfAdaptive();
        }

        return result;
    }

    @Override
    public int getMainGuiId() {
        return CellsGuiHandler.GUI_PART_FLUID_EXPORT_INTERFACE;
    }

    @Override
    public String getGuiTitleLangKey() {
        return "gui.cells.fluid_export_interface.title";
    }

    @Override
    public AEPartLocation getPartSide() {
        return this.getSide();
    }

    @Override
    public ItemStack getBackButtonStack() {
        return this.getItemStack();
    }

    // Part model and rendering

    @Override
    @Nonnull
    public IPartModel getStaticModels() {
        if (this.isActive() && this.isPowered()) {
            return MODELS_HAS_CHANNEL;
        } else if (this.isPowered()) {
            return MODELS_ON;
        } else {
            return MODELS_OFF;
        }
    }

    @Override
    public void getBoxes(final IPartCollisionHelper bch) {
        bch.addBox(2, 2, 14, 14, 14, 16);
        bch.addBox(5, 5, 12, 11, 11, 14);
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 4;
    }

    // Network events

    @Override
    @MENetworkEventSubscribe
    public void chanRender(final MENetworkChannelsChanged c) {
        this.getHost().markForUpdate();
    }

    @Override
    @MENetworkEventSubscribe
    public void powerRender(final MENetworkPowerStatusChange c) {
        this.getHost().markForUpdate();
    }

    // NBT serialization

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.filterInventory.readFromNBT(data, "fluidFilters");
        this.upgradeInventory.readFromNBT(data, "upgrades");
        this.maxSlotSize = data.getInteger("maxSlotSize");
        this.pollingRate = data.getInteger("pollingRate");

        if (this.maxSlotSize < TileImportInterface.MIN_MAX_SLOT_SIZE) {
            this.maxSlotSize = DEFAULT_MAX_SLOT_SIZE;
        }

        if (this.pollingRate < 0) this.pollingRate = TileImportInterface.DEFAULT_POLLING_RATE;

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
    public void writeToNBT(final NBTTagCompound data) {
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
    }

    @Override
    public void writeToStream(final ByteBuf data) throws IOException {
        super.writeToStream(data);

        // Write fluid tanks to stream for client sync
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
    public boolean useStandardMemoryCard() {
        return false;
    }

    private boolean useMemoryCard(final EntityPlayer player) {
        final ItemStack memCardIS = player.inventory.getCurrentItem();
        if (memCardIS.isEmpty()) return false;
        if (!(memCardIS.getItem() instanceof IMemoryCard)) return false;

        final IMemoryCard memoryCard = (IMemoryCard) memCardIS.getItem();

        final String name = "tile.cells.fluid_export_interface";

        if (player.isSneaking()) {
            final NBTTagCompound data = this.downloadSettings(SettingsFrom.MEMORY_CARD);
            if (data != null && !data.isEmpty()) {
                memoryCard.setMemoryCardContents(memCardIS, name, data);
                memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_SAVED);
            }
        } else {
            final String storedName = memoryCard.getSettingsName(memCardIS);
            final NBTTagCompound data = memoryCard.getData(memCardIS);
            if (name.equals(storedName)) {
                this.uploadSettings(SettingsFrom.MEMORY_CARD, data, player);
                memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
            } else {
                memoryCard.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            }
        }

        return true;
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

        if (compound.hasKey("fluidFilters")) {
            this.filterInventory.readFromNBT(compound, "fluidFilters");
            this.refreshFilterMap();
        }
    }

    @Override
    public boolean readFromStream(final ByteBuf data) throws IOException {
        boolean changed = super.readFromStream(data);

        // Read fluid tanks from stream
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

    // GUI handling

    @Override
    public boolean onPartActivate(final EntityPlayer p, final EnumHand hand, final Vec3d pos) {
        if (!p.isSneaking() && this.useMemoryCard(p)) return true;
        if (p.isSneaking()) return false;

        if (!p.world.isRemote) {
            CellsGuiHandler.openPartGui(p, this.getHost().getTile(), this.getSide(), CellsGuiHandler.GUI_PART_FLUID_EXPORT_INTERFACE);
        }

        return true;
    }

    @Override
    public boolean onPartShiftActivate(final EntityPlayer p, final EnumHand hand, final Vec3d pos) {
        return this.useMemoryCard(p);
    }

    // Drops

    @Override
    public void getDrops(final List<ItemStack> drops, final boolean wrenched) {
        for (int i = 0; i < this.upgradeInventory.getSlots(); i++) {
            ItemStack stack = this.upgradeInventory.getStackInSlot(i);
            if (!stack.isEmpty()) drops.add(stack);
        }
        // Fluids cannot be dropped as items
    }

    public EnumSet<EnumFacing> getTargets() {
        return EnumSet.of(this.getSide().getFacing());
    }

    public TileEntity getTileEntity() {
        return this.getHost().getTile();
    }

    // Inventory access

    public IAEFluidTank getFilterInventory() {
        return this.filterInventory;
    }

    public AppEngInternalInventory getUpgradeInventory() {
        return this.upgradeInventory;
    }

    @Nullable
    public FluidStack getFluidInTank(int slot) {
        if (slot < 0 || slot >= TANK_SLOTS) return null;

        return this.fluidTanks[slot];
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

    // Pagination methods (IFluidExportInterfaceInventoryHost)

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
        int maxPage = getTotalPages() - 1;
        this.currentPage = Math.max(0, Math.min(page, maxPage));
    }

    @Override
    public int getCurrentPageStartSlot() {
        return this.currentPage * SLOTS_PER_PAGE;
    }

    // IAEAppEngInventory

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removed, ItemStack added) {
        if (inv == this.upgradeInventory) {
            this.refreshUpgrades();
            this.getHost().markForSave();
        }
    }

    // IAEFluidInventory

    @Override
    public void onFluidInventoryChanged(IAEFluidTank inv, int slot, InvOperation operation, FluidStack added, FluidStack removed) {
        if (inv == this.filterInventory) {
            refreshFilterMap();
            this.wakeUpIfAdaptive();
            this.getHost().markForSave();
        }
    }

    @Override
    public void onFluidInventoryChanged(IAEFluidTank inv, int slot) {
        if (inv == this.filterInventory) {
            refreshFilterMap();
            this.wakeUpIfAdaptive();
            this.getHost().markForSave();
        }
    }

    // IGridTickable

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

    // Capability handling

    @Override
    public boolean hasCapability(Capability<?> capability) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) return true;

        return super.hasCapability(capability);
    }

    @Override
    public <T> T getCapability(Capability<T> capability) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(this.exportFluidHandler);
        }

        return super.getCapability(capability);
    }

    // Internal methods

    public void refreshUpgrades() {
        int oldCapacity = this.installedCapacityUpgrades;
        this.installedCapacityUpgrades = countCapacityUpgrades();

        // Handle capacity reduction: return fluids and clear filters from removed pages
        if (this.installedCapacityUpgrades < oldCapacity) {
            handleCapacityReduction(oldCapacity, this.installedCapacityUpgrades);
        }
    }

    /**
     * Counts the number of AE2 capacity cards installed.
     */
    private int countCapacityUpgrades() {
        int count = 0;

        for (int i = 0; i < this.upgradeInventory.getSlots(); i++) {
            ItemStack stack = this.upgradeInventory.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            if (stack.getItem() instanceof IUpgradeModule) {
                IUpgradeModule module = (IUpgradeModule) stack.getItem();
                if (module.getType(stack) == appeng.api.config.Upgrades.CAPACITY) {
                    count += stack.getCount();
                }
            }
        }

        return Math.min(count, MAX_CAPACITY_CARDS);
    }

    /**
     * Handles capacity reduction when capacity cards are removed.
     * Returns fluids to network, clears filters on removed pages.
     */
    private void handleCapacityReduction(int oldCapacity, int newCapacity) {
        int oldMaxSlot = (oldCapacity + 1) * SLOTS_PER_PAGE;
        int newMaxSlot = (newCapacity + 1) * SLOTS_PER_PAGE;

        // Process slots that are being removed
        for (int i = newMaxSlot; i < oldMaxSlot; i++) {
            // Return fluid to network
            FluidStack fluid = this.fluidTanks[i];
            if (fluid != null && fluid.amount > 0) {
                returnFluidToNetwork(fluid);
                this.fluidTanks[i] = null;
            }

            // Clear the filter
            this.filterInventory.setFluidInSlot(i, null);
        }

        // Clamp current page to valid range
        int maxPage = newCapacity;
        if (this.currentPage > maxPage) this.currentPage = maxPage;

        this.refreshFilterMap();
    }

    /**
     * Attempts to return fluid to the AE2 network.
     * If unable to return, fluid is voided.
     */
    private void returnFluidToNetwork(FluidStack fluid) {
        if (fluid == null || fluid.amount <= 0) return;

        try {
            IStorageGrid storage = this.getProxy().getStorage();
            IMEInventory<IAEFluidStack> fluidStorage = storage.getInventory(
                AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class)
            );

            IAEFluidStack toInsert = AEFluidStack.fromFluidStack(fluid);
            IAEFluidStack remaining = fluidStorage.injectItems(toInsert, Actionable.MODULATE, this.actionSource);

            // Any remainder is voided (no good way to drop fluid items)
        } catch (GridAccessException e) {
            // Network unavailable, fluid is voided
        }
    }

    public void refreshFilterMap() {
        this.filterToSlotMap.clear();

        for (int i = 0; i < TOTAL_SLOTS; i++) {
            IAEFluidStack filterFluid = this.filterInventory.getFluidInSlot(i);
            if (filterFluid == null) continue;

            FluidStack fluid = filterFluid.getFluidStack();
            if (fluid == null) continue;

            FluidStackKey key = FluidStackKey.of(fluid);
            if (key != null) this.filterToSlotMap.put(key, i);
        }

        this.filterFluidList = new ArrayList<>(filterToSlotMap.keySet());
    }

    private int countUpgrade(Class<?> itemClass) {
        int count = 0;
        for (int i = 0; i < this.upgradeInventory.getSlots(); i++) {
            ItemStack existing = this.upgradeInventory.getStackInSlot(i);
            if (!existing.isEmpty() && itemClass.isInstance(existing.getItem())) {
                count++;
            }
        }

        return count;
    }

    public boolean isValidUpgrade(ItemStack stack) {
        if (stack.isEmpty()) return false;

        // Accept AE2 capacity cards (max 4)
        if (stack.getItem() instanceof IUpgradeModule) {
            IUpgradeModule module = (IUpgradeModule) stack.getItem();
            if (module.getType(stack) == appeng.api.config.Upgrades.CAPACITY) {
                return countCapacityUpgrades() < MAX_CAPACITY_CARDS;
            }
        }

        return false;
    }

    /**
     * Clear all filters.
     * Orphaned fluids will be returned to network on the next tick.
     */
    @Override
    public void clearFilters() {
        for (int i = 0; i < FILTER_SLOTS; i++) {
            this.filterInventory.setFluidInSlot(i, null);
        }

        this.refreshFilterMap();
        this.getHost().markForSave();
        this.getHost().markForUpdate();
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

        this.getHost().markForSave();
        this.getHost().markForUpdate();

        return notInserted <= 0;
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
            if (fluid.amount <= 0) this.fluidTanks[i] = null;
        }

        this.getHost().markForSave();
        this.getHost().markForUpdate();
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

            if (isOrphaned) returnTankToNetwork(i);
        }
    }

    /**
     * Try to insert fluids into the ME network.
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

            if (notInserted == null || notInserted.getStackSize() == 0) return 0;

            return (int) notInserted.getStackSize();
        } catch (GridAccessException e) {
            // Not connected to grid, return all
            return fluid.amount;
        }
    }

    private boolean hasWorkToDo() {
        for (int i : this.filterToSlotMap.values()) {
            FluidStack existing = this.fluidTanks[i];
            if (existing == null || existing.amount < this.maxSlotSize) return true;
        }

        return false;
    }

    /**
     * Wake up the tick manager if using adaptive polling (rate=0).
     * Called when filters or settings change to ensure the device starts ticking
     * to push fluids that now match the updated configuration.
     */
    private void wakeUpIfAdaptive() {
        if (this.pollingRate > 0) return;

        try {
            this.getProxy().getTick().alertDevice(this.getProxy().getNode());
        } catch (GridAccessException e) {
            // Not connected to grid
        }
    }

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

            for (Map.Entry<FluidStackKey, Integer> entry : this.filterToSlotMap.entrySet()) {
                int slot = entry.getValue();
                FluidStackKey filterKey = entry.getKey();
                Fluid filterFluid = filterKey.getFluid();
                if (filterFluid == null) continue;

                FluidStack existing = this.fluidTanks[slot];

                // Skip slots where current fluids don't match filter (orphaned fluids)
                // This prevents requesting more fluids when the tank has mismatched content
                if (existing != null && existing.amount > 0) {
                    if (!existing.getFluid().equals(filterFluid)) continue;
                }

                int currentAmount = existing == null ? 0 : existing.amount;
                int space = this.maxSlotSize - currentAmount;
                if (space <= 0) continue;

                IAEFluidStack request = AEFluidStack.fromFluidStack(new FluidStack(filterFluid, space));
                if (request == null) continue;

                IAEFluidStack extracted = fluidStorage.extractItems(request, Actionable.MODULATE, this.actionSource);
                if (extracted == null || extracted.getStackSize() <= 0) continue;

                if (existing == null) {
                    this.fluidTanks[slot] = extracted.getFluidStack();
                } else {
                    existing.amount += (int) extracted.getStackSize();
                }

                didWork = true;
            }
        } catch (GridAccessException e) {
            // Not connected to grid
        }

        if (didWork) this.getHost().markForUpdate();

        return didWork;
    }

    /**
     * Wrapper handler for external fluid extraction access.
     * Only allows draining fluids, not filling.
     */
    private static class ExportFluidHandler implements IFluidHandler {
        private final PartFluidExportInterface part;

        public ExportFluidHandler(PartFluidExportInterface part) {
            this.part = part;
        }

        @Override
        public IFluidTankProperties[] getTankProperties() {
            List<IFluidTankProperties> props = new ArrayList<>();

            for (Map.Entry<FluidStackKey, Integer> entry : part.filterToSlotMap.entrySet()) {
                int slot = entry.getValue();
                FluidStack contents = part.fluidTanks[slot];
                int capacity = part.maxSlotSize;

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
                        return entry.getKey().matches(fluidStack);
                    }
                });
            }

            return props.toArray(new IFluidTankProperties[0]);
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            // Export interface does not allow external filling
            return 0;
        }

        @Nullable
        @Override
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            if (resource == null || resource.amount <= 0) return null;

            FluidStackKey key = FluidStackKey.of(resource);
            if (key == null) return null;

            Integer slot = part.filterToSlotMap.get(key);
            if (slot == null) return null;

            return part.drainFluidFromTank(slot, resource.amount, doDrain);
        }

        @Nullable
        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            if (maxDrain <= 0) return null;

            // Drain from the first non-empty tank
            for (int slot : part.filterToSlotMap.values()) {
                FluidStack existing = part.fluidTanks[slot];
                if (existing != null && existing.amount > 0) {
                    return part.drainFluidFromTank(slot, maxDrain, doDrain);
                }
            }

            return null;
        }
    }
}
