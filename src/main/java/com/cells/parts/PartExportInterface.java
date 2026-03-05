package com.cells.parts;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
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
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.core.settings.TickRates;
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
import com.cells.blocks.exportinterface.IExportInterfaceInventoryHost;
import com.cells.blocks.importinterface.TileImportInterface;
import com.cells.gui.CellsGuiHandler;
import com.cells.items.ItemOverflowCard;
import com.cells.util.ItemStackKey;
import com.cells.util.TickManagerHelper;


/**
 * Part version of the Export Interface for items.
 * Can be placed on cables and behaves identically to the block version.
 * Requests items from the network and exposes them for extraction.
 */
public class PartExportInterface extends PartBasicState implements IGridTickable, IAEAppEngInventory, IExportInterfaceInventoryHost {

    public static final ResourceLocation MODEL_BASE = new ResourceLocation(Tags.MODID, "part/export_interface_base");

    @PartModels
    public static final PartModel MODELS_OFF = new PartModel(MODEL_BASE, new ResourceLocation(Tags.MODID, "part/export_interface_off"));

    @PartModels
    public static final PartModel MODELS_ON = new PartModel(MODEL_BASE, new ResourceLocation(Tags.MODID, "part/export_interface_on"));

    @PartModels
    public static final PartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, new ResourceLocation(Tags.MODID, "part/export_interface_has_channel"));

    public static final int SLOTS_PER_PAGE = 36;
    public static final int MAX_CAPACITY_CARDS = 4;
    public static final int MAX_PAGES = 1 + MAX_CAPACITY_CARDS;
    public static final int FILTER_SLOTS = SLOTS_PER_PAGE * MAX_PAGES;
    public static final int STORAGE_SLOTS = SLOTS_PER_PAGE * MAX_PAGES;
    public static final int UPGRADE_SLOTS = 4;

    // Filter inventory - ghost items only
    private final AppEngInternalInventory filterInventory = new AppEngInternalInventory(this, FILTER_SLOTS, 1);

    // Storage inventory - actual items
    private final AppEngInternalInventory storageInventory;

    // Upgrade inventory
    private final AppEngInternalInventory upgradeInventory;

    // Wrapper for external access
    private final ExportStorageHandler exportHandler;

    // Config
    private int maxSlotSize = TileImportInterface.DEFAULT_MAX_SLOT_SIZE;
    private int pollingRate = TileImportInterface.DEFAULT_POLLING_RATE;

    // Filter mapping
    final Map<ItemStackKey, Integer> filterToSlotMap = new HashMap<>();
    List<Integer> filterSlotList = new ArrayList<>();

    // Number of installed capacity upgrades (adds pages)
    private int installedCapacityUpgrades = 0;

    // Current GUI page index (0-based)
    private int currentPage = 0;

    // Action source
    private final IActionSource actionSource;

    public PartExportInterface(final ItemStack is) {
        super(is);
        this.actionSource = new MachineSource(this);

        // Create storage inventory with large stack support
        this.storageInventory = new AppEngInternalInventory(this, STORAGE_SLOTS, TileImportInterface.DEFAULT_MAX_SLOT_SIZE) {
            @Override
            public int getSlotLimit(int slot) {
                return PartExportInterface.this.maxSlotSize;
            }

            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return PartExportInterface.this.isItemValidForSlot(slot, stack);
            }
        };

        this.upgradeInventory = new AppEngInternalInventory(this, UPGRADE_SLOTS, 1) {
            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return PartExportInterface.this.isValidUpgrade(stack);
            }
        };

        this.exportHandler = new ExportStorageHandler(this);

        refreshUpgrades();
        refreshFilterMap();
    }

    // IExportInterfaceHost implementation

    @Override
    public int getMaxSlotSize() {
        return this.maxSlotSize;
    }

    @Override
    public void setMaxSlotSize(int size) {
        int oldSize = this.maxSlotSize;

        this.maxSlotSize = Math.max(TileImportInterface.MIN_MAX_SLOT_SIZE, size);
        this.getHost().markForSave();

        // Slots may now have room for more items after increasing the limit
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
    public int getMainGuiId() {
        return CellsGuiHandler.GUI_PART_EXPORT_INTERFACE;
    }

    @Override
    public String getGuiTitleLangKey() {
        return "gui.cells.export_interface.title";
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
        this.filterInventory.readFromNBT(data, "filter");
        this.storageInventory.readFromNBT(data, "storage");
        this.upgradeInventory.readFromNBT(data, "upgrades");
        this.maxSlotSize = data.getInteger("maxSlotSize");
        this.pollingRate = data.getInteger("pollingRate");

        if (this.maxSlotSize < TileImportInterface.MIN_MAX_SLOT_SIZE) {
            this.maxSlotSize = TileImportInterface.DEFAULT_MAX_SLOT_SIZE;
        }

        if (this.pollingRate < 0) this.pollingRate = TileImportInterface.DEFAULT_POLLING_RATE;

        this.refreshFilterMap();
        this.refreshUpgrades();
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        this.filterInventory.writeToNBT(data, "filter");
        this.storageInventory.writeToNBT(data, "storage");
        this.upgradeInventory.writeToNBT(data, "upgrades");
        data.setInteger("maxSlotSize", this.maxSlotSize);
        data.setInteger("pollingRate", this.pollingRate);
    }

    @Nonnull
    @Override
    public NBTTagCompound downloadSettings(SettingsFrom from) {
        NBTTagCompound output = super.downloadSettings(from);
        if (output == null) output = new NBTTagCompound();

        output.setInteger("maxSlotSize", this.maxSlotSize);
        output.setInteger("pollingRate", this.pollingRate);

        if (from == SettingsFrom.DISMANTLE_ITEM) {
            this.filterInventory.writeToNBT(output, "filter");
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

        final String name = "tile.cells.export_interface";

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

        if (compound.hasKey("filter")) {
            this.filterInventory.readFromNBT(compound, "filter");
            this.refreshFilterMap();
        }
    }

    // GUI handling

    @Override
    public boolean onPartActivate(final EntityPlayer p, final EnumHand hand, final Vec3d pos) {
        if (!p.isSneaking() && this.useMemoryCard(p)) return true;
        if (p.isSneaking()) return false;

        if (!p.world.isRemote) {
            CellsGuiHandler.openPartGui(p, this.getHost().getTile(), this.getSide(), CellsGuiHandler.GUI_PART_EXPORT_INTERFACE);
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
        for (int i = 0; i < this.storageInventory.getSlots(); i++) {
            ItemStack stack = this.storageInventory.getStackInSlot(i);
            if (!stack.isEmpty()) drops.add(stack);
        }

        for (int i = 0; i < this.upgradeInventory.getSlots(); i++) {
            ItemStack stack = this.upgradeInventory.getStackInSlot(i);
            if (!stack.isEmpty()) drops.add(stack);
        }
    }

    public EnumSet<EnumFacing> getTargets() {
        return EnumSet.of(this.getSide().getFacing());
    }

    public TileEntity getTileEntity() {
        return this.getHost().getTile();
    }

    // Inventory access

    public AppEngInternalInventory getFilterInventory() {
        return this.filterInventory;
    }

    public AppEngInternalInventory getStorageInventory() {
        return this.storageInventory;
    }

    public AppEngInternalInventory getUpgradeInventory() {
        return this.upgradeInventory;
    }

    // IAEAppEngInventory

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removed, ItemStack added) {
        if (inv == this.filterInventory) {
            // Filter changed - rebuild mapping and wake up so the device
            // starts pushing items matching the new filter
            this.refreshFilterMap();
            this.wakeUpIfAdaptive();
            this.getHost().markForSave();
        } else if (inv == this.upgradeInventory) {
            this.refreshUpgrades();
            this.getHost().markForSave();
        } else if (inv == this.storageInventory && !removed.isEmpty()) {
            // Item extracted - wake up to request more
            this.wakeUpIfAdaptive();
        }

        this.getHost().markForUpdate();
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

        boolean didWork = exportItems();

        if (this.pollingRate > 0) return TickRateModulation.SAME;

        return didWork ? TickRateModulation.FASTER : (hasWorkToDo() ? TickRateModulation.SLOWER : TickRateModulation.SLEEP);
    }

    // Capability handling

    @Override
    public boolean hasCapability(Capability<?> capability) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return true;

        return super.hasCapability(capability);
    }

    @Override
    public <T> T getCapability(Capability<T> capability) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(this.exportHandler);
        }

        return super.getCapability(capability);
    }

    // Internal methods

    public void refreshUpgrades() {
        int oldCapacityCount = this.installedCapacityUpgrades;
        this.installedCapacityUpgrades = countCapacityUpgrades();

        // Handle capacity card removal - shrink pages
        if (this.installedCapacityUpgrades < oldCapacityCount) {
            handleCapacityReduction(oldCapacityCount, this.installedCapacityUpgrades);
        }

        // Clamp current page to valid range
        int maxPage = this.installedCapacityUpgrades;
        if (this.currentPage > maxPage) this.currentPage = maxPage;
    }

    public void refreshFilterMap() {
        this.filterToSlotMap.clear();

        final int filterSlots = this.filterInventory.getSlots();
        final int storageSlots = this.storageInventory.getSlots();
        final int maxSlots = Math.min(filterSlots, storageSlots);

        List<Integer> validSlots = new ArrayList<>();

        for (int i = 0; i < maxSlots; i++) {
            ItemStack filterStack = this.filterInventory.getStackInSlot(i);
            if (!filterStack.isEmpty()) {
                this.filterToSlotMap.put(ItemStackKey.of(filterStack), i);
                validSlots.add(i);
            }
        }

        this.filterSlotList = validSlots;
    }

    public boolean isValidUpgrade(ItemStack stack) {
        if (stack.isEmpty()) return false;

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
     * Count the number of installed capacity upgrades.
     */
    public int countCapacityUpgrades() {
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
     * Get the number of installed capacity upgrades.
     */
    public int getInstalledCapacityUpgrades() {
        return this.installedCapacityUpgrades;
    }

    /**
     * Get the total number of pages (1 base + 1 per capacity card).
     */
    public int getTotalPages() {
        return 1 + this.installedCapacityUpgrades;
    }

    /**
     * Get the current page index (0-based).
     */
    public int getCurrentPage() {
        return this.currentPage;
    }

    /**
     * Set the current page index (0-based), clamped to valid range.
     */
    public void setCurrentPage(int page) {
        this.currentPage = Math.max(0, Math.min(page, this.installedCapacityUpgrades));
    }

    /**
     * Get the starting slot index for the current page.
     */
    public int getCurrentPageStartSlot() {
        return this.currentPage * SLOTS_PER_PAGE;
    }

    /**
     * Handle capacity reduction by clearing filters and returning/dropping items from removed pages.
     */
    private void handleCapacityReduction(int oldCount, int newCount) {
        int newTotalSlots = (1 + newCount) * SLOTS_PER_PAGE;
        int oldTotalSlots = (1 + oldCount) * SLOTS_PER_PAGE;

        // Process slots that are being removed
        for (int slot = newTotalSlots; slot < oldTotalSlots && slot < this.storageInventory.getSlots(); slot++) {
            // Clear the filter
            if (slot < this.filterInventory.getSlots()) {
                this.filterInventory.setStackInSlot(slot, ItemStack.EMPTY);
            }

            // Return items to network or drop on floor
            returnSlotToNetwork(slot, true);
        }

        this.refreshFilterMap();
    }

    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if (slot < 0 || slot >= this.storageInventory.getSlots()) return false;

        ItemStack filterStack = this.filterInventory.getStackInSlot(slot);
        if (filterStack.isEmpty()) return false;

        return ItemStack.areItemsEqual(stack, filterStack) && ItemStack.areItemStackTagsEqual(stack, filterStack);
    }

    /**
     * Clear all filters.
     * Orphaned items will be returned to network on the next export.
     */
    @Override
    public void clearFilters() {
        for (int i = 0; i < this.filterInventory.getSlots(); i++) {
            this.filterInventory.setStackInSlot(i, ItemStack.EMPTY);
        }

        this.refreshFilterMap();
        this.getHost().markForSave();
    }

    /**
     * Try to return all items in a specific storage slot back to the ME network.
     * Items that cannot be returned stay in the slot.
     *
     * @param slot The slot index to return items from
     * @return true if all items were returned, false if some remain
     */
    public boolean returnSlotToNetwork(int slot) {
        return returnSlotToNetwork(slot, false);
    }

    /**
     * Try to return all items in a specific storage slot back to the ME network.
     * Items that cannot be returned stay in the slot, unless force is true.
     *
     * @param slot The slot index to return items from
     * @param force If true, drop items on the ground that cannot be returned to the network
     * @return true if all items were returned or dropped, false if some remain
     */
    public boolean returnSlotToNetwork(int slot, boolean force) {
        if (slot < 0 || slot >= this.storageInventory.getSlots()) return true;

        ItemStack stack = this.storageInventory.getStackInSlot(slot);
        if (stack.isEmpty()) return true;

        ItemStack remaining = insertItemsIntoNetwork(stack);

        // Update the slot with whatever couldn't be returned
        if (force && !remaining.isEmpty()) {
            dropItemsOnGround(remaining);
            remaining = ItemStack.EMPTY;
        }

        this.storageInventory.setStackInSlot(slot, remaining);
        this.getHost().markForSave();

        return remaining.isEmpty();
    }

    /**
     * Return overflow items (items exceeding maxSlotSize) back to the ME network.
     */
    private void returnOverflowToNetwork() {
        for (int i = 0; i < this.storageInventory.getSlots(); i++) {
            ItemStack stack = this.storageInventory.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            int overflow = stack.getCount() - this.maxSlotSize;
            if (overflow <= 0) continue;

            ItemStack overflowStack = stack.copy();
            overflowStack.setCount(overflow);

            ItemStack remaining = insertItemsIntoNetwork(overflowStack);

            // Reduce the stack in the slot by amount successfully returned
            stack.shrink(overflow - remaining.getCount());
            this.storageInventory.setStackInSlot(i, stack);
        }

        this.getHost().markForSave();
    }

    /**
     * Return all orphaned items (items that don't match their filter) to the ME network.
     */
    private void returnOrphanedItemsToNetwork() {
        for (int i = 0; i < this.storageInventory.getSlots(); i++) {
            ItemStack storage = this.storageInventory.getStackInSlot(i);
            if (storage.isEmpty()) continue;

            ItemStack filter = this.filterInventory.getStackInSlot(i);

            // If no filter or items don't match filter, try to return them
            boolean isOrphaned = filter.isEmpty() ||
                !ItemStack.areItemsEqual(storage, filter) ||
                !ItemStack.areItemStackTagsEqual(storage, filter);

            if (isOrphaned) returnSlotToNetwork(i);
        }
    }

    /**
     * Try to insert items into the ME network.
     */
    private ItemStack insertItemsIntoNetwork(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;

        try {
            IStorageGrid storage = this.getProxy().getStorage();
            IMEInventory<IAEItemStack> itemStorage = storage.getInventory(
                AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class)
            );

            IAEItemStack toInsert = AEItemStack.fromItemStack(stack);
            if (toInsert == null) return stack;

            IAEItemStack notInserted = itemStorage.injectItems(toInsert, Actionable.MODULATE, this.actionSource);
            if (notInserted == null || notInserted.getStackSize() == 0) return ItemStack.EMPTY;

            return notInserted.createItemStack();
        } catch (GridAccessException e) {
            // Not connected to grid, return all items
            return stack;
        }
    }

    /**
     * Drop items on the ground at the part's host position.
     * Used when removing capacity upgrades causes pages to be deleted.
     */
    private void dropItemsOnGround(ItemStack stack) {
        if (stack.isEmpty()) return;

        net.minecraft.tileentity.TileEntity te = this.getHost().getTile();
        if (te == null || te.getWorld() == null) return;

        BlockPos pos = te.getPos();
        EntityItem entity = new EntityItem(
            te.getWorld(),
            pos.getX() + 0.5,
            pos.getY() + 0.5,
            pos.getZ() + 0.5,
            stack
        );
        te.getWorld().spawnEntity(entity);
    }

    private boolean hasWorkToDo() {
        for (int i : this.filterSlotList) {
            ItemStack current = this.storageInventory.getStackInSlot(i);
            if (current.isEmpty() || current.getCount() < this.maxSlotSize) return true;
        }

        return false;
    }

    /**
     * Wake up the tick manager if using adaptive polling (rate=0).
     * Called when filters or settings change to ensure the device starts ticking
     * to pull items that now match the updated configuration.
     */
    private void wakeUpIfAdaptive() {
        if (this.pollingRate > 0) return;

        try {
            this.getProxy().getTick().alertDevice(this.getProxy().getNode());
        } catch (GridAccessException e) {
            // Not connected to grid
        }
    }

    private boolean exportItems() {
        boolean didWork = false;

        try {
            IStorageGrid storage = this.getProxy().getStorage();
            IMEInventory<IAEItemStack> itemStorage = storage.getInventory(
                AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class)
            );

            // First, return any orphaned or overflow items to the network
            // This must happen before requesting new items
            returnOrphanedItemsToNetwork();
            returnOverflowToNetwork();

            for (int i : this.filterSlotList) {
                ItemStack filterStack = this.filterInventory.getStackInSlot(i);
                if (filterStack.isEmpty()) continue;

                ItemStack current = this.storageInventory.getStackInSlot(i);

                // Skip slots where current items don't match filter (orphaned items)
                // This prevents requesting more items when the slot has mismatched content
                if (!current.isEmpty()) {
                    if (!ItemStack.areItemsEqual(current, filterStack) ||
                        !ItemStack.areItemStackTagsEqual(current, filterStack)) {
                        continue;
                    }
                }

                int currentCount = current.isEmpty() ? 0 : current.getCount();
                int space = this.maxSlotSize - currentCount;
                if (space <= 0) continue;

                IAEItemStack request = AEItemStack.fromItemStack(filterStack);
                if (request == null) continue;

                request.setStackSize(space);

                IAEItemStack extracted = itemStorage.extractItems(request, Actionable.MODULATE, this.actionSource);
                if (extracted == null || extracted.getStackSize() <= 0) continue;

                if (current.isEmpty()) {
                    ItemStack newStack = extracted.createItemStack();
                    this.storageInventory.setStackInSlot(i, newStack);
                } else {
                    current.grow((int) extracted.getStackSize());
                    this.storageInventory.setStackInSlot(i, current);
                }

                didWork = true;
            }
        } catch (GridAccessException e) {
            // Not connected to grid
        }

        return didWork;
    }

    /**
     * Wrapper handler for external extraction access.
     */
    private static class ExportStorageHandler implements IItemHandler {
        private final PartExportInterface part;

        public ExportStorageHandler(PartExportInterface part) {
            this.part = part;
        }

        @Override
        public int getSlots() {
            return part.filterSlotList.size();
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= part.filterSlotList.size()) return ItemStack.EMPTY;

            int storageSlot = part.filterSlotList.get(slot);
            return part.storageInventory.getStackInSlot(storageSlot);
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            // Export interface does not allow external insertion
            return stack;
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= part.filterSlotList.size()) return ItemStack.EMPTY;

            int storageSlot = part.filterSlotList.get(slot);
            return part.storageInventory.extractItem(storageSlot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return part.maxSlotSize;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            // External insertion not allowed
            return false;
        }
    }
}
