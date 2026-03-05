package com.cells.parts;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

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
import com.cells.blocks.importinterface.IImportInterfaceInventoryHost;
import com.cells.blocks.importinterface.TileImportInterface;
import com.cells.gui.CellsGuiHandler;
import com.cells.items.ItemOverflowCard;
import com.cells.items.ItemTrashUnselectedCard;
import com.cells.util.ItemStackKey;
import com.cells.util.TickManagerHelper;


/**
 * Part version of the Import Interface for items.
 * Can be placed on cables and behaves identically to the block version.
 */
public class PartImportInterface extends PartBasicState implements IGridTickable, IAEAppEngInventory, IImportInterfaceInventoryHost {

    public static final ResourceLocation MODEL_BASE = new ResourceLocation(Tags.MODID, "part/import_interface_base");

    @PartModels
    public static final PartModel MODELS_OFF = new PartModel(MODEL_BASE, new ResourceLocation(Tags.MODID, "part/import_interface_off"));

    @PartModels
    public static final PartModel MODELS_ON = new PartModel(MODEL_BASE, new ResourceLocation(Tags.MODID, "part/import_interface_on"));

    @PartModels
    public static final PartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, new ResourceLocation(Tags.MODID, "part/import_interface_has_channel"));

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
    private final FilteredStorageHandler filteredHandler;

    // Config
    private int maxSlotSize = TileImportInterface.DEFAULT_MAX_SLOT_SIZE;
    private int pollingRate = TileImportInterface.DEFAULT_POLLING_RATE;

    // Upgrade cache
    private boolean installedOverflowUpgrade = false;
    private boolean installedTrashUnselectedUpgrade = false;
    private int installedCapacityUpgrades = 0;

    // Current GUI page index (0-based)
    private int currentPage = 0;

    // Filter mapping
    final Map<ItemStackKey, Integer> filterToSlotMap = new HashMap<>();
    List<ItemStackKey> filterItemList = new ArrayList<>();

    // Action source
    private final IActionSource actionSource;

    public PartImportInterface(final ItemStack is) {
        super(is);
        this.actionSource = new MachineSource(this);

        // Create storage inventory with filter and unlimited stack size support
        this.storageInventory = new AppEngInternalInventory(this, STORAGE_SLOTS, TileImportInterface.DEFAULT_MAX_SLOT_SIZE) {
            @Override
            public int getSlotLimit(int slot) {
                return PartImportInterface.this.maxSlotSize;
            }

            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return PartImportInterface.this.isItemValidForSlot(slot, stack);
            }

            @Override
            @Nonnull
            public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
                if (stack.isEmpty()) return ItemStack.EMPTY;

                ItemStack existing = this.getStackInSlot(slot);
                int limit = PartImportInterface.this.maxSlotSize;

                if (!existing.isEmpty()) {
                    if (!ItemStack.areItemsEqual(existing, stack) || !ItemStack.areItemStackTagsEqual(existing, stack)) {
                        return stack;
                    }

                    int space = limit - existing.getCount();
                    if (space <= 0) return stack;

                    int toInsert = Math.min(stack.getCount(), space);
                    if (!simulate) {
                        ItemStack newStack = existing.copy();
                        newStack.grow(toInsert);
                        this.setStackInSlot(slot, newStack);
                    }

                    if (toInsert >= stack.getCount()) return ItemStack.EMPTY;

                    ItemStack remainder = stack.copy();
                    remainder.shrink(toInsert);
                    return remainder;
                } else {
                    int toInsert = Math.min(stack.getCount(), limit);
                    if (!simulate) {
                        ItemStack newStack = stack.copy();
                        newStack.setCount(toInsert);
                        this.setStackInSlot(slot, newStack);
                    }

                    if (toInsert >= stack.getCount()) return ItemStack.EMPTY;

                    ItemStack remainder = stack.copy();
                    remainder.shrink(toInsert);
                    return remainder;
                }
            }
        };

        this.upgradeInventory = new AppEngInternalInventory(this, UPGRADE_SLOTS, 1) {
            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return PartImportInterface.this.isValidUpgrade(stack);
            }
        };

        this.filteredHandler = new FilteredStorageHandler(this);

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
        int oldSize = this.maxSlotSize;

        this.maxSlotSize = Math.max(TileImportInterface.MIN_MAX_SLOT_SIZE, size);
        this.getHost().markForSave();

        if (this.pollingRate <= 0 && oldSize < this.maxSlotSize) this.wakeDevice();
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
        int oldRate = this.pollingRate;
        this.pollingRate = Math.max(0, ticks);
        this.getHost().markForSave();

        if (!TickManagerHelper.reRegisterTickable(this.getProxy().getNode(), this)) {
            if (player != null) {
                player.sendMessage(new TextComponentTranslation("chat.cells.polling_rate_delayed"));
            }
        }

        // Wake up the device when switching to adaptive mode (rate = 0)
        if (this.pollingRate <= 0 && oldRate > 0) this.wakeDevice();
    }

    @Override
    public BlockPos getHostPos() {
        return this.getHost().getLocation().getPos();
    }

    @Override
    public int getMainGuiId() {
        return CellsGuiHandler.GUI_PART_IMPORT_INTERFACE;
    }

    @Override
    public String getGuiTitleLangKey() {
        return "gui.cells.import_interface.title";
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

        // Save slot size and polling rate for both memory card and dismantle
        output.setInteger("maxSlotSize", this.maxSlotSize);
        output.setInteger("pollingRate", this.pollingRate);

        // Save filter inventory only when dismantling (not for memory card)
        if (from == SettingsFrom.DISMANTLE_ITEM) {
            this.filterInventory.writeToNBT(output, "filter");
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
        final String name = "tile.cells.import_interface";

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
        if (compound.hasKey("filter")) {
            this.filterInventory.readFromNBT(compound, "filter");
            this.refreshFilterMap();
        }
    }

    // GUI handling

    @Override
    public boolean onPartActivate(final EntityPlayer p, final EnumHand hand, final Vec3d pos) {
        // Handle memory card (right-click to load settings)
        if (!p.isSneaking() && this.useMemoryCard(p)) return true;

        if (p.isSneaking()) return false;

        if (!p.world.isRemote) {
            CellsGuiHandler.openPartGui(p, this.getHost().getTile(), this.getSide(), CellsGuiHandler.GUI_PART_IMPORT_INTERFACE);
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
            this.refreshFilterMap();
            this.getHost().markForSave();

            // Wake up the device when filter changes in adaptive polling mode
            if (this.pollingRate <= 0) this.wakeDevice();
        } else if (inv == this.upgradeInventory) {
            this.refreshUpgrades();
            this.getHost().markForSave();
        } else if (inv == this.storageInventory && !added.isEmpty()) {
            // Wake up the part to import items in adaptive mode
            if (this.pollingRate <= 0) this.wakeDevice();
        }

        this.getHost().markForUpdate();
    }

    /**
     * Wake up the device to process items immediately.
     * Only effective in adaptive polling mode (pollingRate = 0).
     */
    private void wakeDevice() {
        try {
            this.getProxy().getTick().alertDevice(this.getProxy().getNode());
        } catch (GridAccessException e) {
            // Not connected to grid
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
                !hasWorkToDo(),
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

        boolean didWork = importItems();

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
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(this.filteredHandler);
        }

        return super.getCapability(capability);
    }

    // Internal methods

    public void refreshUpgrades() {
        this.installedOverflowUpgrade = hasOverflowUpgrade();
        this.installedTrashUnselectedUpgrade = hasTrashUnselectedUpgrade();

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

        for (int i = 0; i < maxSlots; i++) {
            ItemStack filterStack = this.filterInventory.getStackInSlot(i);
            if (!filterStack.isEmpty()) this.filterToSlotMap.put(ItemStackKey.of(filterStack), i);
        }

        this.filterItemList = new ArrayList<>(filterToSlotMap.keySet());
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

        // Process slots that are being removed (from newTotalSlots to oldTotalSlots-1)
        for (int slot = newTotalSlots; slot < oldTotalSlots && slot < this.storageInventory.getSlots(); slot++) {
            // Clear the filter
            if (slot < this.filterInventory.getSlots()) {
                this.filterInventory.setStackInSlot(slot, ItemStack.EMPTY);
            }

            // Return items to network or drop on floor
            ItemStack items = this.storageInventory.getStackInSlot(slot);
            if (!items.isEmpty()) {
                ItemStack remaining = returnItemsToNetwork(items);
                if (!remaining.isEmpty()) dropItemsOnGround(remaining);
                this.storageInventory.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }

        this.refreshFilterMap();
    }

    /**
     * Try to return items to the ME network.
     * @return Items that could not be inserted (empty if all inserted)
     */
    private ItemStack returnItemsToNetwork(ItemStack stack) {
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
            return stack;
        }
    }

    /**
     * Drop items on the ground at the part's host position.
     */
    private void dropItemsOnGround(ItemStack stack) {
        if (stack.isEmpty()) return;

        net.minecraft.tileentity.TileEntity te = this.getHost().getTile();
        if (te == null || te.getWorld() == null) return;

        BlockPos pos = te.getPos();
        net.minecraft.entity.item.EntityItem entity = new net.minecraft.entity.item.EntityItem(
            te.getWorld(),
            pos.getX() + 0.5,
            pos.getY() + 0.5,
            pos.getZ() + 0.5,
            stack
        );
        te.getWorld().spawnEntity(entity);
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
     * Clear filter slots only where the corresponding storage slot is empty.
     * This prevents orphaning items in the import interface.
     */
    @Override
    public void clearFilters() {
        for (int i = 0; i < FILTER_SLOTS; i++) {
            // Only clear filter if the corresponding storage slot is empty
            if (i >= STORAGE_SLOTS || this.storageInventory.getStackInSlot(i).isEmpty()) {
                this.filterInventory.setStackInSlot(i, ItemStack.EMPTY);
            }
        }

        this.refreshFilterMap();
        this.getHost().markForSave();
        this.getHost().markForUpdate();
    }

    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if (slot < 0 || slot >= this.storageInventory.getSlots()) return false;

        ItemStackKey key = ItemStackKey.of(stack);
        if (key == null) return false;

        int filterSlot = this.filterToSlotMap.getOrDefault(key, -1);
        return filterSlot == slot;
    }

    private boolean hasWorkToDo() {
        for (int i : this.filterToSlotMap.values()) {
            if (!this.storageInventory.getStackInSlot(i).isEmpty()) return true;
        }

        return false;
    }

    private boolean importItems() {
        boolean didWork = false;

        try {
            IStorageGrid storage = this.getProxy().getStorage();
            IMEInventory<IAEItemStack> itemStorage = storage.getInventory(
                AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class)
            );

            for (int i : this.filterToSlotMap.values()) {
                ItemStack stack = this.storageInventory.getStackInSlot(i);
                if (stack.isEmpty()) continue;

                IAEItemStack aeStack = AEItemStack.fromItemStack(stack);
                if (aeStack == null) continue;

                IAEItemStack remaining = itemStorage.injectItems(aeStack, Actionable.MODULATE, this.actionSource);

                if (remaining == null) {
                    this.storageInventory.setStackInSlot(i, ItemStack.EMPTY);
                    didWork = true;
                } else if (remaining.getStackSize() < stack.getCount()) {
                    ItemStack newStack = stack.copy();
                    newStack.setCount((int) remaining.getStackSize());
                    this.storageInventory.setStackInSlot(i, newStack);
                    didWork = true;
                }
            }
        } catch (GridAccessException e) {
            // Not connected to grid
        }

        return didWork;
    }

    /**
     * Wrapper handler for external access.
     */
    private static class FilteredStorageHandler implements IItemHandler {
        private final PartImportInterface part;

        public FilteredStorageHandler(PartImportInterface part) {
            this.part = part;
        }

        @Override
        public int getSlots() {
            return 1 + part.filterToSlotMap.size();
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot <= 0) return ItemStack.EMPTY;

            int filterIndex = slot - 1;
            if (filterIndex >= part.filterItemList.size()) return ItemStack.EMPTY;

            ItemStackKey key = part.filterItemList.get(filterIndex);
            Integer storageSlot = part.filterToSlotMap.get(key);
            if (storageSlot == null) return ItemStack.EMPTY;

            return part.storageInventory.getStackInSlot(storageSlot);
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) return ItemStack.EMPTY;

            return insertItemSlotless(stack, simulate);
        }

        private ItemStack insertItemSlotless(@Nonnull ItemStack stack, boolean simulate) {
            ItemStackKey key = ItemStackKey.of(stack);
            if (key == null) return stack;

            int slot = part.filterToSlotMap.getOrDefault(key, -1);
            if (slot == -1) return part.installedTrashUnselectedUpgrade ? ItemStack.EMPTY : stack;

            ItemStack remaining = part.storageInventory.insertItem(slot, stack, simulate);

            if (part.installedOverflowUpgrade) return ItemStack.EMPTY;

            return remaining.isEmpty() ? ItemStack.EMPTY : remaining;
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return part.maxSlotSize;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            ItemStackKey key = ItemStackKey.of(stack);
            if (key == null) return false;

            return part.filterToSlotMap.containsKey(key);
        }
    }
}
