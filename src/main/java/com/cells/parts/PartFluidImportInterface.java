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
import com.cells.blocks.fluidimportinterface.IFluidImportInterfaceInventoryHost;
import com.cells.blocks.importinterface.IImportInterfaceHost;
import com.cells.blocks.importinterface.TileImportInterface;
import com.cells.gui.CellsGuiHandler;
import com.cells.items.ItemOverflowCard;
import com.cells.items.ItemTrashUnselectedCard;
import com.cells.util.FluidStackKey;
import com.cells.util.TickManagerHelper;


/**
 * Part version of the Fluid Import Interface.
 * Can be placed on cables and behaves identically to the block version.
 */
public class PartFluidImportInterface extends PartBasicState implements IGridTickable, IAEAppEngInventory, IAEFluidInventory, IImportInterfaceHost, IFluidImportInterfaceInventoryHost {

    public static final ResourceLocation MODEL_BASE = new ResourceLocation(Tags.MODID, "part/import_fluid_interface_base");

    @PartModels
    public static final PartModel MODELS_OFF = new PartModel(MODEL_BASE, new ResourceLocation(Tags.MODID, "part/import_fluid_interface_off"));

    @PartModels
    public static final PartModel MODELS_ON = new PartModel(MODEL_BASE, new ResourceLocation(Tags.MODID, "part/import_fluid_interface_on"));

    @PartModels
    public static final PartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, new ResourceLocation(Tags.MODID, "part/import_fluid_interface_has_channel"));

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
    private final FilteredFluidHandler filteredFluidHandler;

    // Config
    private int maxSlotSize = DEFAULT_MAX_SLOT_SIZE;
    private int pollingRate = TileImportInterface.DEFAULT_POLLING_RATE;

    // Upgrade cache
    private boolean installedOverflowUpgrade = false;
    private boolean installedTrashUnselectedUpgrade = false;
    private int installedCapacityUpgrades = 0;

    // Pagination state
    private int currentPage = 0;

    // Filter mapping
    final Map<FluidStackKey, Integer> filterToSlotMap = new HashMap<>();
    List<FluidStackKey> filterFluidList = new ArrayList<>();

    // Action source
    private final IActionSource actionSource;

    public PartFluidImportInterface(final ItemStack is) {
        super(is);
        this.actionSource = new MachineSource(this);

        this.upgradeInventory = new AppEngInternalInventory(this, UPGRADE_SLOTS, 1) {
            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return PartFluidImportInterface.this.isValidUpgrade(stack);
            }
        };

        this.filteredFluidHandler = new FilteredFluidHandler(this);

        refreshUpgrades();
        refreshFilterMap();
    }

    // IImportInterfaceHost implementation

    @Override
    public int getMaxSlotSize() {
        return this.maxSlotSize;
    }

    @Override
    public void setMaxSlotSize(int size) {
        this.maxSlotSize = Math.max(TileImportInterface.MIN_MAX_SLOT_SIZE, size);
        this.getHost().markForSave();
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
    public int getMainGuiId() {
        return CellsGuiHandler.GUI_PART_FLUID_IMPORT_INTERFACE;
    }

    @Override
    public String getGuiTitleLangKey() {
        return "gui.cells.import_fluid_interface.title";
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

        // Save slot size and polling rate for both memory card and dismantle
        output.setInteger("maxSlotSize", this.maxSlotSize);
        output.setInteger("pollingRate", this.pollingRate);

        // Save filter inventory only when dismantling (not for memory card)
        if (from == SettingsFrom.DISMANTLE_ITEM) {
            this.filterInventory.writeToNBT(output, "fluidFilters");
        }

        return output;
    }

    /**
     * Use the block's translation key for memory card compatibility.
     * This allows memory cards to work between block and part versions.
     */
    @Override
    public boolean useStandardMemoryCard() {
        return false;
    }

    /**
     * Custom memory card handling that uses the block's translation key.
     */
    private boolean useMemoryCard(final EntityPlayer player) {
        final ItemStack memCardIS = player.inventory.getCurrentItem();
        if (memCardIS.isEmpty()) return false;
        if (!(memCardIS.getItem() instanceof IMemoryCard)) return false;

        final IMemoryCard memoryCard = (IMemoryCard) memCardIS.getItem();

        // Use block's translation key for compatibility between parts and blocks
        final String name = "tile.cells.import_fluid_interface";

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

        // Load slot size and polling rate for both memory card and dismantle
        if (compound.hasKey("maxSlotSize")) {
            this.setMaxSlotSize(compound.getInteger("maxSlotSize"));
        }
        if (compound.hasKey("pollingRate")) {
            this.setPollingRate(compound.getInteger("pollingRate"), player);
        }

        // Load filter inventory when memory card has filters
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
        // Handle memory card (right-click to load settings)
        if (!p.isSneaking() && this.useMemoryCard(p)) return true;

        if (p.isSneaking()) return false;

        if (!p.world.isRemote) {
            CellsGuiHandler.openPartGui(p, this.getHost().getTile(), this.getSide(), CellsGuiHandler.GUI_PART_FLUID_IMPORT_INTERFACE);
        }

        return true;
    }

    @Override
    public boolean onPartShiftActivate(final EntityPlayer p, final EnumHand hand, final Vec3d pos) {
        // Handle memory card (shift-click to save settings)
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
        // TODO: maybe fluid drops, if LazyAE2 is there?
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

    // Pagination methods (IFluidImportInterfaceInventoryHost)

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

    public int insertFluidIntoTank(int slot, FluidStack fluid) {
        if (slot < 0 || slot >= TANK_SLOTS) return 0;
        if (fluid == null || fluid.amount <= 0) return 0;

        FluidStack current = this.fluidTanks[slot];

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

        this.getHost().markForSave();
        this.getHost().markForUpdate();

        return toInsert;
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
            this.getHost().markForSave();
        }
    }

    @Override
    public void onFluidInventoryChanged(IAEFluidTank inv, int slot) {
        if (inv == this.filterInventory) {
            refreshFilterMap();
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

        boolean didWork = importFluids();

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
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(this.filteredFluidHandler);
        }

        return super.getCapability(capability);
    }

    // Internal methods

    public void refreshUpgrades() {
        this.installedOverflowUpgrade = hasOverflowUpgrade();
        this.installedTrashUnselectedUpgrade = hasTrashUnselectedUpgrade();

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
            if (remaining != null && remaining.getStackSize() > 0) {
                // TODO: how do we handle it
                // Log or silently void - fluids can't be dropped as items easily
            }
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

    public boolean hasOverflowUpgrade() {
        return countUpgrade(ItemOverflowCard.class) > 0;
    }

    public boolean hasTrashUnselectedUpgrade() {
        return countUpgrade(ItemTrashUnselectedCard.class) > 0;
    }

    public boolean isValidUpgrade(ItemStack stack) {
        if (stack.isEmpty()) return false;

        if (stack.getItem() instanceof ItemOverflowCard) return !hasOverflowUpgrade();
        if (stack.getItem() instanceof ItemTrashUnselectedCard) return !hasTrashUnselectedUpgrade();

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
     * Clear filter slots only where the corresponding tank is empty.
     * This prevents orphaning fluids in the import interface.
     */
    @Override
    public void clearFilters() {
        for (int i = 0; i < FILTER_SLOTS; i++) {
            // Only clear filter if the corresponding tank is empty
            if (i >= TANK_SLOTS || this.fluidTanks[i] == null || this.fluidTanks[i].amount <= 0) {
                this.filterInventory.setFluidInSlot(i, null);
            }
        }

        this.refreshFilterMap();
        this.getHost().markForSave();
        this.getHost().markForUpdate();
    }

    private boolean hasWorkToDo() {
        for (int i : this.filterToSlotMap.values()) {
            if (this.fluidTanks[i] != null && this.fluidTanks[i].amount > 0) return true;
        }

        return false;
    }

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
                IAEFluidStack remaining = fluidStorage.injectItems(aeStack, Actionable.MODULATE, this.actionSource);

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

        if (didWork) this.getHost().markForUpdate();

        return didWork;
    }

    /**
     * Wrapper handler for external fluid access.
     */
    private static class FilteredFluidHandler implements IFluidHandler {
        private final PartFluidImportInterface part;

        public FilteredFluidHandler(PartFluidImportInterface part) {
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

            Integer slot = part.filterToSlotMap.get(key);
            if (slot == null) return part.installedTrashUnselectedUpgrade ? resource.amount : 0;

            FluidStack existing = part.fluidTanks[slot];
            int capacity = part.maxSlotSize;
            int currentAmount = (existing == null) ? 0 : existing.amount;
            int space = capacity - currentAmount;
            if (space <= 0) return part.installedOverflowUpgrade ? resource.amount : 0;

            int toInsert = Math.min(resource.amount, space);
            if (doFill) {
                if (existing == null) {
                    part.fluidTanks[slot] = new FluidStack(resource, toInsert);
                } else {
                    existing.amount += toInsert;
                }

                part.getHost().markForSave();
                part.getHost().markForUpdate();

                if (part.pollingRate <= 0) {
                    try {
                        part.getProxy().getTick().alertDevice(part.getProxy().getNode());
                    } catch (GridAccessException e) {
                        // Not connected to grid
                    }
                }
            }

            int excess = resource.amount - toInsert;
            if (excess > 0 && part.installedOverflowUpgrade) return resource.amount;

            return toInsert;
        }

        @Nullable
        @Override
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            return null;
        }

        @Nullable
        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            return null;
        }
    }
}
