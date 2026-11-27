package com.cells.parts.subnetproxy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.config.FuzzyMode;
import appeng.api.config.Upgrades;
import appeng.api.implementations.IPowerChannelState;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartModel;
import appeng.api.parts.PartItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.ICellContainer;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IStorageChannel;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.core.sync.GuiBridge;
import appeng.helpers.IPriorityHost;
import appeng.items.parts.PartModels;
import appeng.parts.AEBasePart;
import appeng.parts.automation.UpgradeInventory;
import appeng.parts.PartModel;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;
import appeng.util.SettingsFrom;

import io.netty.buffer.ByteBuf;

import com.cells.api.FilterHostUtil;
import com.cells.api.ISubnetProxy;
import com.cells.config.CellsConfig;
import com.cells.gui.CellsGuiHandler;
import com.cells.gui.overlay.ServerMessageHelper;
import com.cells.helpers.MemoryCardUpgradeHelper;
import com.cells.helpers.SubnetProxyMemoryCardHelper;
import com.cells.helpers.UpgradeCardInteractionHelper;
import com.cells.items.upgrades.ItemInsertionCard;
import com.cells.network.sync.ResourceType;
import com.cells.parts.CellsPartType;
import com.cells.parts.ItemCellsPart;
import com.cells.Tags;
import com.cells.util.PowerStateHelper;


/**
 * Front half of the Subnet Proxy block.
 * <p>
 * Alongside the back part, it forms the complete Subnet Proxy block,
 * forwarding changes from the back part to the front-facing grid.
 * and 
 * <p>
 * The filter configuration, upgrades, page state, and filter mode are all
 * managed here and persisted in NBT.
 */
public class PartSubnetProxyFront extends AEBasePart
    implements IPowerChannelState, ICellContainer, IAEAppEngInventory, IPriorityHost, ISubnetProxy {

    // LED state flags
    protected static final int POWERED_FLAG = 1;
    protected static final int CHANNEL_FLAG = 2;
    protected static final int BOTH_PARTS_FLAG = 4;

    @PartModels
    public static final ResourceLocation MODEL_BASE = new ResourceLocation(Tags.MODID, "part/subnet_proxy_front/base");
    @PartModels
    public static final ResourceLocation MODEL_STATUS_OFF = new ResourceLocation(Tags.MODID, "part/subnet_proxy_front/status_off");
    @PartModels
    public static final ResourceLocation MODEL_STATUS_ON = new ResourceLocation(Tags.MODID, "part/subnet_proxy_front/status_on");
    @PartModels
    public static final ResourceLocation MODEL_STATUS_HAS_CHANNEL = new ResourceLocation(Tags.MODID, "part/subnet_proxy_front/status_active");

    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_STATUS_OFF);
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_STATUS_ON);
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_STATUS_HAS_CHANNEL);

    /** Slots per page of filter config: 9 columns x 7 rows */
    public static final int SLOTS_PER_PAGE = 63;

    /**
     * Filter config inventory. Total size = SLOTS_PER_PAGE * maxPages.
     * Items represent filters: plain ItemStacks for item filters,
     * FluidDummyItem stacks for fluid filters.
     */
    private final AppEngInternalInventory config;

    /**
     * Upgrade inventory. Slot count comes from config.
     * Supports: Capacity (adds pages), Fuzzy (fuzzy matching), Inverter (blacklist).
     */
    private final UpgradeInventory upgrades;

    /** Current page index (0-based). Persisted in NBT, synced to GUI. */
    private int currentPage = 0;

    /**
     * Filter mode: determines how items dragged into filter slots are converted.
     * ITEM encodes an ItemStack; FLUID converts it to a FluidDummyItem, etc.
     * Persisted in NBT, synced to GUI.
     */
    private ResourceType filterMode = ResourceType.ITEM;

    /**
     * Fuzzy mode for item matching when a fuzzy card is installed.
     * Persisted in NBT, synced to GUI.
     */
    private FuzzyMode fuzzyMode = FuzzyMode.IGNORE_ALL;

    /**
     * Set of storage channels currently exposed by this proxy. Disabled channels
     * are entirely hidden from the front-grid: {@link #getCellArray} returns an
     * empty list for them, and the grid adapters drops the previous deltas
     * before forwarding. The default is empty (everything off) so newly placed
     * proxies don't accidentally expose anything until the user opts in.
     * <p>
     * Persisted in NBT as a bitmask over {@link ResourceType#ordinal()}, synced
     * to the GUI via the container's {@code @GuiSync} field.
     */
    private EnumSet<ResourceType> enabledChannels = EnumSet.noneOf(ResourceType.class);

    /** Client-side LED state */
    private int clientFlags = 0;

    /** Extraction adapters for the front-facing grid */
    private final Map<ResourceType, SubnetProxyInventoryHandler<?>> readers = new EnumMap<>(ResourceType.class);

    /** Direct-insertion adapters for the front-facing grid when the Insertion Card is installed */
    private final Map<ResourceType, SubnetProxyInsertionHandler<?>> writers = new EnumMap<>(ResourceType.class);

    /** Current captured access shared by the adapters, endpoint owners, and graph */
    private List<SubnetProxySnapshot<?>> snapshots = Collections.emptyList();

    /** Priority for this extraction/insertion on the front-facing grid */
    private int priority;

    /** Suppress configuration callbacks until the complete saved NBT has been read */
    private boolean loadingFromNbt;

    /** Whether the part is still loaded in the world */
    private boolean loaded;

    /** Pairing flag used by LED synchronization and inspection */
    private boolean cachedHasBack;

    /** Last world tick whose settings change dirtied the owning chunk */
    private long lastSaveTick = -1;

    /** Placement tick used to suppress off-hand activation during the same back-part placement */
    private long placedTick = -1;

    public PartSubnetProxyFront(final ItemStack is) {
        super(is);

        // Inner proxy connects to Grid B (the cable bus this part sits on).
        // Grid B discovers this ICellContainer via the inner proxy node.
        this.getProxy().setFlags(GridFlags.REQUIRE_CHANNEL);
        this.getProxy().setIdlePowerUsage(1.0);

        // Upgrade inventory with configurable slot count
        int upgradeSlots = CellsConfig.general.subnetProxyUpgradeSlots;
        this.upgrades = new UpgradeInventory(this, upgradeSlots) {
            @Override
            public int getMaxInstalled(Upgrades u) {
                // We only limit capacity cards by the number of slots
                if (u == Upgrades.CAPACITY) return Integer.MAX_VALUE;
                if (u == Upgrades.FUZZY) return 1;
                if (u == Upgrades.INVERTER) return 1;

                // TODO: Add Redstone Card support when baseline support
                //       is solid. It would just involve withdrawing the
                //       proxy when there is no Redstone signal present.

                // QUANTUM_LINK is the placeholder type for custom CELLS upgrades.
                // We allow 1 for the Insertion Card. The filter below restricts
                // which specific item classes are accepted.
                if (u == Upgrades.QUANTUM_LINK) return 1;
                return 0;
            }
        };

        // Restrict the QUANTUM_LINK upgrade slot to only accept InsertionCard,
        // preventing other custom CELLS upgrades from being placed here.
        this.upgrades.setFilter(new IAEItemFilter() {
            @Override
            public boolean allowExtract(IItemHandler inv, int slot, int amount) {
                return true;
            }

            @Override
            public boolean allowInsert(IItemHandler inv, int slot, ItemStack itemstack) {
                if (itemstack.isEmpty()) return false;
                if (!(itemstack.getItem() instanceof IUpgradeModule)) return false;

                Upgrades u = ((IUpgradeModule) itemstack.getItem()).getType(itemstack);
                if (u == null) return false;

                // Only allow InsertionCard for the custom upgrade slot
                if (u == Upgrades.QUANTUM_LINK && !(itemstack.getItem() instanceof ItemInsertionCard)) {
                    return false;
                }

                return upgrades.getInstalledUpgrades(u) < upgrades.getMaxInstalled(u);
            }
        });

        // Allocate config inventory for all pages. At max upgrades this can be 63 * (1 + upgradeSlots)
        // slots in memory, but NBT serialization is sparse (only non-empty slots written).
        int totalSlots = SLOTS_PER_PAGE * getMaxPages();
        this.config = new AppEngInternalInventory(this, totalSlots, 1);

        for (SubnetProxyChannel<?> channel : SubnetProxyManager.channels()) {
            this.createAdapters(channel);
        }
    }

    /**
     * SubnetProxyChannel resolves the shared storage channel singletons when
     * runtime adapters are created, after AEApi has registered its channels.
     */
    private <T extends IAEStack<T>> void createAdapters(SubnetProxyChannel<T> channel) {
        this.readers.put(channel.getType(), new SubnetProxyInventoryHandler<>(channel.getChannel(), this.priority));
        this.writers.put(channel.getType(), new SubnetProxyInsertionHandler<>(channel.getChannel(), this.priority));
    }

    public int getMaxPages() {
        return 1 + CellsConfig.general.subnetProxyUpgradeSlots;
    }

    public int getTotalPages() {
        return 1 + this.upgrades.getInstalledUpgrades(Upgrades.CAPACITY);
    }

    public int getCurrentPage() {
        return this.currentPage;
    }

    public void setCurrentPage(int page) {
        this.currentPage = Math.max(0, Math.min(page, getTotalPages() - 1));
    }

    public ResourceType getFilterMode() {
        return this.filterMode;
    }

    public void setFilterMode(ResourceType mode) {
        this.filterMode = mode;
        this.markHostDirty();
    }

    public void cycleFilterMode() {
        ResourceType[] available = ResourceType.getAvailableTypes();
        int idx = 0;
        for (int i = 0; i < available.length; i++) {
            if (available[i] == this.filterMode) {
                idx = i;
                break;
            }
        }

        idx = (idx + 1) % available.length;
        setFilterMode(available[idx]);
    }

    public FuzzyMode getFuzzyMode() {
        return this.fuzzyMode;
    }

    public void setFuzzyMode(FuzzyMode mode) {
        this.fuzzyMode = mode;
        this.makeDirty(false);
        this.markHostDirty();
    }

    public boolean isChannelEnabled(ResourceType type) {
        return this.enabledChannels.contains(type);
    }

    public int getEnabledChannelsBitmask() {
        int mask = 0;
        for (ResourceType t : this.enabledChannels) mask |= (1 << t.ordinal());
        return mask;
    }

    public void setChannelEnabled(ResourceType type, boolean enabled) {
        if (enabled == this.enabledChannels.contains(type)) return;

        if (enabled) this.enabledChannels.add(type);
        else this.enabledChannels.remove(type);
        this.makeDirty(true);
        this.markHostDirty();
    }

    public AppEngInternalInventory getConfigInventory() {
        return this.config;
    }

    public UpgradeInventory getUpgradeInventory() {
        return this.upgrades;
    }

    @Override
    public int getFilterSlots() {
        return Math.min(this.config.getSlots(), getTotalPages() * SLOTS_PER_PAGE);
    }

    @Override
    @Nonnull
    public ItemStack getFilter(int slot) {
        if (slot < 0 || slot >= getFilterSlots()) return ItemStack.EMPTY;

        return FilterHostUtil.normalizeFilter(this.config.getStackInSlot(slot));
    }

    @Override
    public void setFilter(int slot, @Nonnull ItemStack stack) {
        if (slot < 0 || slot >= getFilterSlots()) return;

        this.config.setStackInSlot(slot, FilterHostUtil.normalizeFilter(stack));
    }

    @Override
    public void clearFilters() {
        int slotCount = getFilterSlots();
        for (int slot = 0; slot < slotCount; slot++) {
            this.config.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    @Override
    @Nonnull
    public EnumFacing getPrimaryFacing() {
        AEPartLocation side = this.getSide();
        return side != null ? side.getFacing() : EnumFacing.NORTH;
    }

    public int getInstalledUpgrades(Upgrades u) {
        return this.upgrades.getInstalledUpgrades(u);
    }

    public boolean hasInsertionCard() {
        for (int i = 0; i < this.upgrades.getSlots(); i++) {
            ItemStack stack = this.upgrades.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof ItemInsertionCard) return true;
        }

        return false;
    }

    @Override
    public void setPriority(int newValue) {
        this.priority = newValue;
        this.updateAdapterPriorities();
        this.makeDirty(true);
        this.markHostDirty();
    }

    private void updateAdapterPriorities() {
        for (SubnetProxyAdapter<?> adapter : this.readers.values()) adapter.setPriority(this.priority);
        for (SubnetProxyAdapter<?> adapter : this.writers.values()) adapter.setPriority(this.priority);
    }

    @Override
    public ItemStack getItemStackRepresentation() {
        return AEApi.instance().definitions().parts().cableAnchor().maybeStack(1).orElse(ItemStack.EMPTY);
    }

    @Override
    public GuiBridge getGuiBridge() {
        // Returning null means the priority GUI won't show a "back" button.
        // The player closes the priority GUI with Escape and re-opens the proxy.
        // This is required because the proxy GUI uses CELLS' own GUI system,
        // not AE2's GuiBridge enum.
        return null;
    }

    @Override
    public IItemHandler getInventoryByName(final String name) {
        if ("config".equals(name)) return this.config;
        if ("upgrades".equals(name)) return this.upgrades;
        return null;
    }

    @Override
    public void addToWorld() {
        super.addToWorld();
        this.loaded = true;
        this.cachedHasBack = this.findBackPart() != null;
        this.makeDirty(true);
    }

    @Override
    public void removeFromWorld() {
        this.loaded = false;
        this.makeDirty(false);

        super.removeFromWorld();
        this.cachedHasBack = false;
    }

    @Override
    public void onPlacement(EntityPlayer player, EnumHand hand, ItemStack held, AEPartLocation side) {
        super.onPlacement(player, hand, held, side);

        if (held.hasTagCompound()) this.uploadSettings(SettingsFrom.DISMANTLE_ITEM, held.getTagCompound(), player);

        TileEntity tile = this.getHost() != null ? this.getHost().getTile() : null;
        if (tile != null) this.placedTick = tile.getWorld().getTotalWorldTime();
    }

    @Override
    public ItemStack getItemStack(PartItemStack type) {
        ItemStack stack = super.getItemStack(type).copy();

        if (type == PartItemStack.WRENCH || type == PartItemStack.BREAK) {
            NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound().copy() : new NBTTagCompound();
            this.downloadSettings(SettingsFrom.DISMANTLE_ITEM, tag);

            // Wrench keeps the upgrades, only normal break removes them
            if (type == PartItemStack.BREAK) tag.removeTag("upgrades");

            stack.setTagCompound(tag.isEmpty() ? null : tag);
        }

        return stack;
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);

        this.loadingFromNbt = true;
        try {
            // Sparse config deserialization: only non-empty slots were written
            if (data.hasKey("config")) readSparseConfig(data.getCompoundTag("config"));

            this.upgrades.readFromNBT(data, "upgrades");
            this.currentPage = data.getInteger("currentPage");
            this.filterMode = ResourceType.fromOrdinal(data.getInteger("filterMode"));
            this.fuzzyMode = FuzzyMode.values()[Math.max(0, Math.min(
                data.getInteger("fuzzyMode"), FuzzyMode.values().length - 1))];
            this.priority = data.getInteger("priority");
            this.updateAdapterPriorities();

            // Channel exposure bitmask. Missing key = empty set (default-off), which
            // matches the field's default for fresh parts placed before this NBT key existed.
            this.enabledChannels = EnumSet.noneOf(ResourceType.class);
            if (data.hasKey("enabledChannels")) {
                int mask = data.getInteger("enabledChannels");
                for (ResourceType t : ResourceType.values()) {
                    if ((mask & (1 << t.ordinal())) != 0) this.enabledChannels.add(t);
                }
            }
        } finally {
            this.loadingFromNbt = false;
        }

        this.makeDirty(false);
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);

        // Sparse config serialization: only write non-empty slots to avoid
        // 1.6k empty slot entries in NBT. Each entry is keyed by slot index.
        writeSparseConfig(data);

        this.upgrades.writeToNBT(data, "upgrades");
        // FIXME: Should we persist the current page? It's only client-side state for the GUI.
        //        What if multiple players use the GUI simultaneously?
        data.setInteger("currentPage", this.currentPage);
        data.setInteger("filterMode", this.filterMode.ordinal());
        data.setInteger("fuzzyMode", this.fuzzyMode.ordinal());
        data.setInteger("priority", this.priority);
        data.setInteger("enabledChannels", getEnabledChannelsBitmask());
    }

    private void writeSparseConfig(final NBTTagCompound data) {
        NBTTagCompound sparse = new NBTTagCompound();

        for (int i = 0; i < this.config.getSlots(); i++) {
            ItemStack stack = this.config.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            NBTTagCompound slotTag = new NBTTagCompound();
            stack.writeToNBT(slotTag);
            sparse.setTag(Integer.toString(i), slotTag);
        }

        data.setTag("config", sparse);
    }

    protected NBTTagCompound downloadSettings(final SettingsFrom from, final NBTTagCompound output) {
        output.setInteger("priority", this.priority);
        output.setInteger("filterMode", this.filterMode.ordinal());
        output.setInteger("fuzzyMode", this.fuzzyMode.ordinal());

        if (from != SettingsFrom.MEMORY_CARD) {
            output.setInteger("enabledChannels", getEnabledChannelsBitmask());
        }

        // Memory cards should only capture the pages that are currently usable,
        // not hidden overflow filters from removed capacity upgrades.
        int filterSlots = this.getFilterSlots();
        AppEngInternalInventory exportedConfig = new AppEngInternalInventory(null, filterSlots, 1);
        for (int slot = 0; slot < filterSlots; slot++) {
            ItemStack stack = this.config.getStackInSlot(slot);
            if (stack.isEmpty()) continue;

            exportedConfig.setStackInSlot(slot, stack.copy());
        }

        output.setTag("config", exportedConfig.serializeNBT());
        this.upgrades.writeToNBT(output, "upgrades");
        return output;
    }

    @Override
    public void uploadSettings(final SettingsFrom from, final NBTTagCompound compound, final EntityPlayer player) {
        if (compound == null) return;

        int previousChannels = this.getEnabledChannelsBitmask();

        if (compound.hasKey("priority")) this.setPriority(compound.getInteger("priority"));

        if (compound.hasKey("filterMode")) {
            this.filterMode = ResourceType.fromOrdinal(compound.getInteger("filterMode"));
        }

        if (compound.hasKey("fuzzyMode")) {
            int fuzzyOrdinal = Math.max(0, Math.min(compound.getInteger("fuzzyMode"), FuzzyMode.values().length - 1));
            this.fuzzyMode = FuzzyMode.values()[fuzzyOrdinal];
        }

        if (from != SettingsFrom.MEMORY_CARD && compound.hasKey("enabledChannels")) {
            int mask = compound.getInteger("enabledChannels");
            EnumSet<ResourceType> importedChannels = EnumSet.noneOf(ResourceType.class);
            for (ResourceType type : ResourceType.values()) {
                if ((mask & (1 << type.ordinal())) != 0) importedChannels.add(type);
            }

            this.enabledChannels = importedChannels;
        }

        if (from == SettingsFrom.MEMORY_CARD && player != null) {
            MemoryCardUpgradeHelper.restoreFromMemoryCard(
                compound,
                "upgrades",
                player,
                this.upgrades,
                this.upgrades::setStackInSlot);
        } else {
            this.upgrades.readFromNBT(compound, "upgrades");
        }

        AppEngInternalInventory importedConfig = new AppEngInternalInventory(null, this.config.getSlots(), 1);
        importedConfig.readFromNBT(compound, "config");

        IAEAppEngInventory configOwner = this.config.getTileEntity();
        this.config.setTileEntity(null);

        List<ItemStack> skippedFilters = new ArrayList<>();

        if (from == SettingsFrom.MEMORY_CARD) {
            // Add new filters into the visible empty slots without clearing the existing config.
            int visibleSlots = this.getFilterSlots();
            for (int slot = 0; slot < importedConfig.getSlots(); slot++) {
                ItemStack stack = importedConfig.getStackInSlot(slot);
                if (stack.isEmpty()) continue;

                ItemStack normalized = FilterHostUtil.normalizeFilter(stack);
                int emptySlot = -1;
                boolean duplicate = false;

                for (int targetSlot = 0; targetSlot < visibleSlots; targetSlot++) {
                    ItemStack existing = this.config.getStackInSlot(targetSlot);
                    if (FilterHostUtil.matchesFilter(existing, normalized)) {
                        duplicate = true;
                        break;
                    }

                    if (emptySlot < 0 && existing.isEmpty()) emptySlot = targetSlot;
                }

                if (duplicate) continue;

                if (emptySlot < 0) {
                    skippedFilters.add(normalized);
                    continue;
                }

                this.config.setStackInSlot(emptySlot, normalized);
            }
        } else {
            for (int slot = 0; slot < this.config.getSlots(); slot++) {
                ItemStack stack = slot < importedConfig.getSlots() ? importedConfig.getStackInSlot(slot) : ItemStack.EMPTY;
                this.config.setStackInSlot(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
            }
        }

        this.config.setTileEntity(configOwner);

        this.setCurrentPage(this.currentPage);
        this.makeDirty(previousChannels != this.getEnabledChannelsBitmask());
        this.markHostDirty();

        this.markHostForUpdate();

        if (player instanceof EntityPlayerMP && !skippedFilters.isEmpty()) {
            String filters = skippedFilters.stream()
                .map(ItemStack::getDisplayName)
                .reduce((a, b) -> a + "\n- " + b)
                .orElse("");
            ServerMessageHelper.warning(
                (EntityPlayerMP) player,
                "message.cells.filters_not_added",
                String.valueOf(skippedFilters.size()),
                filters);
        }
    }

    private void readSparseConfig(final NBTTagCompound sparse) {
        // Clear all slots
        for (int i = 0; i < this.config.getSlots(); i++) {
            this.config.setStackInSlot(i, ItemStack.EMPTY);
        }

        // Read non-empty slots
        for (String key : sparse.getKeySet()) {
            int slot;
            try {
                slot = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                continue;
            }

            if (slot < 0 || slot >= this.config.getSlots()) continue;

            ItemStack stack = new ItemStack(sparse.getCompoundTag(key));
            if (!stack.isEmpty()) this.config.setStackInSlot(slot, stack);
        }
    }

    @MENetworkEventSubscribe
    public void stateChange(final MENetworkChannelsChanged event) {
        this.makeDirty(false);
        this.markHostForUpdate();
    }

    @MENetworkEventSubscribe
    public void stateChange(final MENetworkPowerStatusChange event) {
        this.makeDirty(false);
        this.markHostForUpdate();
    }

    @Override
    public void onNeighborChanged(IBlockAccess world, BlockPos pos, BlockPos neighbor) {
        // TODO: could exit early if neighbor != adjacentPos
        this.cachedHasBack = this.findBackPart() != null;
        this.makeDirty(false);
        this.markHostForUpdate();
    }

    private int computeStateFlags() {
        int flags = 0;
        if (PowerStateHelper.isPowered(this.getProxy())) flags |= POWERED_FLAG;
        if (PowerStateHelper.hasChannel(this.getProxy())) flags |= CHANNEL_FLAG;
        if (this.cachedHasBack) flags |= BOTH_PARTS_FLAG;

        return flags;
    }

    private int getStateFlags() {
        TileEntity hostTile = this.getHost() != null ? this.getHost().getTile() : null;
        World hostWorld = hostTile != null ? hostTile.getWorld() : null;
        if (hostWorld != null && !hostWorld.isRemote) return this.computeStateFlags();

        return this.clientFlags;
    }

    @Override
    public boolean useStandardMemoryCard() {
        return false;
    }

    protected boolean useMemoryCard(final EntityPlayer player) {
        final ItemStack memCardIS = player.inventory.getCurrentItem();
        if (memCardIS.isEmpty()) return false;
        if (!(memCardIS.getItem() instanceof IMemoryCard)) return false;

        final IMemoryCard memoryCard = (IMemoryCard) memCardIS.getItem();
        final String name = SubnetProxyMemoryCardHelper.getMemoryCardName();

        if (player.isSneaking()) {
            final NBTTagCompound data = this.downloadSettings(SettingsFrom.MEMORY_CARD, new NBTTagCompound());
            if (!data.isEmpty()) {
                memoryCard.setMemoryCardContents(memCardIS, name, data);
                memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_SAVED);
            }
        } else {
            final NBTTagCompound data = SubnetProxyMemoryCardHelper.prepareUploadData(
                memoryCard.getSettingsName(memCardIS),
                memoryCard.getData(memCardIS)
            );

            if (data != null) {
                this.uploadSettings(SettingsFrom.MEMORY_CARD, data, player);
                memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
            } else {
                memoryCard.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            }
        }

        return true;
    }

    @Override
    public void writeToStream(final ByteBuf data) {
        int flags = this.computeStateFlags();
        this.clientFlags = flags;

        data.writeByte((byte) flags);
    }

    @Override
    public boolean readFromStream(final ByteBuf data) {
        final int old = this.clientFlags;
        this.clientFlags = data.readByte();
        return old != this.clientFlags;
    }

    @Override
    public boolean isPowered() {
        return (this.getStateFlags() & POWERED_FLAG) == POWERED_FLAG;
    }

    @Override
    public boolean isActive() {
        int flags = this.getStateFlags();
        return (flags & CHANNEL_FLAG) == CHANNEL_FLAG
            && (flags & BOTH_PARTS_FLAG) == BOTH_PARTS_FLAG;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List<IMEInventoryHandler> getCellArray(final IStorageChannel<?> channel) {
        for (SubnetProxyInventoryHandler<?> reader : this.readers.values()) {
            if (reader.getChannel() != channel) continue;

            ResourceType type = this.resourceType(channel);
            if (!this.enabledChannels.contains(type)) return Collections.emptyList();

            SubnetProxyInsertionHandler<?> writer = this.writers.get(type);
            if (this.hasInsertionCard()) return Arrays.asList(reader, writer);

            return Collections.singletonList(reader);
        }

        return Collections.emptyList();
    }

    private ResourceType resourceType(IStorageChannel<?> channel) {
        for (SubnetProxyChannel<?> supported : SubnetProxyManager.channels()) {
            if (supported.getChannel() == channel) return supported.getType();
        }

        return null;
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    @Override
    public void blinkCell(int slot) {
        // No visual blinking for passthrough
    }

    @Override
    public void saveChanges(@Nullable ICellInventory<?> cellInventory) {
        this.markHostDirty();
    }

    private void markHostDirty() {
        TileEntity te = this.getHost() != null ? this.getHost().getTile() : null;
        if (te == null) return;

        World w = te.getWorld();

        //noinspection ConstantValue
        if (w == null || w.isRemote) return;

        // Debounce to once per tick
        long currentTick = w.getTotalWorldTime();
        if (this.lastSaveTick == currentTick) return;

        this.lastSaveTick = currentTick;

        w.markChunkDirty(te.getPos(), te);
    }

    private void markHostForUpdate() {
        IPartHost host = this.getHost();
        if (host == null) return;

        host.markForUpdate();
    }

    @Override
    public void onChangeInventory(IItemHandler inventory, int slot, InvOperation operation,
            ItemStack removed, ItemStack added) {
        if (this.loadingFromNbt) return;

        this.setCurrentPage(this.currentPage);
        this.makeDirty(inventory == this.upgrades);
        this.markHostDirty();
    }

    private void makeDirty(boolean shouldGridRefresh) {
        World world = this.getHostWorld();
        if (world == null || world.isRemote || this.loadingFromNbt) return;

        this.invalidateSnapshots();
        SubnetProxyManager.makeDirty(this);
        IGridNode node = this.getProxy().getNode();
        if (shouldGridRefresh && node != null) {
            SubnetProxyNode owner = node.getGrid().getCache(ISubnetProxyNode.class);
            owner.requestGridReset();
        }
    }

    public void markSourcesDirty() {
        this.makeDirty(false);
    }

    public void invalidateSnapshots() {
        SubnetProxyManager.topology().invalidate(this.snapshots);
        this.snapshots = Collections.emptyList();
        for (SubnetProxyInventoryHandler<?> adapter : this.readers.values()) adapter.setSnapshot(null);
        for (SubnetProxyInsertionHandler<?> adapter : this.writers.values()) adapter.setSnapshot(null);
    }

    public List<SubnetProxySnapshot<?>> captureConnection() {
        boolean current = !this.snapshots.isEmpty();
        for (SubnetProxySnapshot<?> snapshot : this.snapshots) current &= snapshot.available();
        if (current) return this.snapshots;

        this.invalidateSnapshots();

        List<SubnetProxySnapshot<?>> newSnapshots = new ArrayList<>();
        PartSubnetProxyBack back = this.loaded ? this.findBackPart() : null;
        IGridNode frontNode = this.loaded ? this.getProxy().getNode() : null;
        IGridNode backNode = back != null ? back.getProxy().getNode() : null;
        this.cachedHasBack = back != null;

        if (frontNode == null || backNode == null) return newSnapshots;

        SubnetProxyNode frontOwner = frontNode.getGrid().getCache(ISubnetProxyNode.class);
        SubnetProxyNode backOwner = backNode.getGrid().getCache(ISubnetProxyNode.class);
        frontOwner.register();
        backOwner.register();
        if (frontOwner == backOwner || !frontNode.isActive() || !backNode.isActive()) return newSnapshots;

        this.snapshots = newSnapshots;
        frontOwner.replaceSnapshots(frontNode, newSnapshots);
        backOwner.replaceSnapshots(backNode, newSnapshots);

        for (SubnetProxyChannel<?> channel : SubnetProxyManager.channels()) {
            if (this.enabledChannels.contains(channel.getType())) {
                newSnapshots.add(this.captureAccess(backOwner, frontOwner, channel));
            }
        }

        return newSnapshots;
    }

    @SuppressWarnings("unchecked")
    private <T extends IAEStack<T>> SubnetProxySnapshot<T> captureAccess(SubnetProxyNode back,
            SubnetProxyNode front, SubnetProxyChannel<T> channel) {
        SubnetProxySnapshot<T> snapshot = new SubnetProxySnapshot<>(back, front, channel,
            channel.captureFilter(this), this.priority, this.hasInsertionCard());

        ((SubnetProxyInventoryHandler<T>) this.readers.get(channel.getType())).setSnapshot(snapshot);
        ((SubnetProxyInsertionHandler<T>) this.writers.get(channel.getType())).setSnapshot(snapshot);

        return snapshot;
    }

    public PartSubnetProxyBack findBackPart() {
        TileEntity selfTile = this.getHost() != null ? this.getHost().getTile() : null;
        if (selfTile == null) return null;

        AEPartLocation side = this.getSide();
        if (side == null) return null;

        // The back part is in the adjacent block in our facing direction,
        // on the opposite side (facing back toward us).
        EnumFacing facing = side.getFacing();
        BlockPos adjacentPos = selfTile.getPos().offset(facing);
        if (!selfTile.getWorld().isBlockLoaded(adjacentPos)) return null;

        TileEntity adjacentTile = selfTile.getWorld().getTileEntity(adjacentPos);
        if (!(adjacentTile instanceof IPartHost)) return null;

        IPart candidate = ((IPartHost) adjacentTile).getPart(AEPartLocation.fromFacing(facing.getOpposite()));
        if (candidate instanceof PartSubnetProxyBack) return (PartSubnetProxyBack) candidate;

        return null;
    }

    public IGrid getBackGrid() {
        PartSubnetProxyBack back = this.findBackPart();
        IGridNode node = back != null ? back.getProxy().getNode() : null;
        return node != null ? node.getGrid() : null;
    }

    @Override
    public boolean isOutboundConnection() {
        return true;
    }

    @Override
    public IGrid getTargetGrid() {
        return this.getBackGrid();
    }

    @Override
    @Nonnull
    public ItemStack getRemoteDisplayStack() {
        PartSubnetProxyBack back = findBackPart();
        return back != null ? back.getItemStack(PartItemStack.PICK) : ItemStack.EMPTY;
    }

    public List<String> describeRuntime() {
        List<String> lines = new ArrayList<>();
        IGridNode endpoint = this.getProxy().getNode();
        lines.add("Proxy priority=" + this.priority + " channels=" + this.enabledChannels
            + " paired=" + this.cachedHasBack + " insertion=" + this.hasInsertionCard());

        if (endpoint == null) return lines;

        SubnetProxyNode node = endpoint.getGrid().getCache(ISubnetProxyNode.class);
        lines.add("Grid generation=" + node.getGeneration()
            + " membershipRevision=" + node.getMembershipRevision()
            + " topologyRevision=" + SubnetProxyManager.topology().revision());
        lines.add(node.diagnostics().describe());

        for (SubnetProxyView<?> view : node.views()) {
            lines.add(view.channel().getType() + " stable=" + view.isStable()
                + " localRevision=" + view.localRevision() + " resetRevision=" + view.resetRevision()
                + " origins=" + view.accountedOrigins().size() + " refresh=" + view.reason());
        }

        return lines;
    }

    @Override
    public void getBoxes(final IPartCollisionHelper bch) {
        // 5 units deep from the face (z=11..16 in part-local coords)
        bch.addBox(0, 0, 11, 16, 16, 16);
    }

    @Override
    @Nonnull
    public AECableType getCableConnectionType(final AEPartLocation dir) {
        return AECableType.SMART;
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 2;
    }

    @Override
    public boolean onPartActivate(final EntityPlayer player, final EnumHand hand, final Vec3d pos) {
        // If the player is holding the complementary (back) part, attempt to place it
        // on the adjacent block's opposite face instead of activating this part.
        if (isHoldingComplementaryPart(player, hand, CellsPartType.SUBNET_PROXY_BACK)) {
            return tryPlaceComplementaryPart(player, hand);
        }

        // Suppress activation on the same tick the part was placed.
        // AE2's client-side PartPlacement returns PASS, causing Minecraft to
        // try the off-hand, which triggers onPartActivate on the just-placed part.
        TileEntity te = this.getHost() != null ? this.getHost().getTile() : null;
        if (te != null && te.getWorld().getTotalWorldTime() == this.placedTick) return false;

        final ItemStack heldItem = player.getHeldItem(hand);
        if (!player.isSneaking() && UpgradeCardInteractionHelper.isUpgradeCard(heldItem)) {
            if (player.world.isRemote) return true;

            final ItemStack remainder = UpgradeCardInteractionHelper.tryInsertOne(heldItem, this.upgrades);
            if (remainder != null) {
                player.setHeldItem(hand, remainder);
                return true;
            }
        }

        if (!player.isSneaking() && this.useMemoryCard(player)) return true;
        if (player.isSneaking()) return false;

        if (player.world.isRemote) return true;

        // Check that the back part is present
        if (findBackPart() == null) {
            player.sendMessage(new TextComponentTranslation("chat.cells.subnet_proxy.need_back"));
            return true;
        }

        IPartHost host = this.getHost();
        AEPartLocation side = this.getSide();
        if (host == null || side == null) return true;

        // Open GUI
        TileEntity guiTe = host.getTile();
        if (guiTe == null) return true;

        CellsGuiHandler.openPartGui(player, guiTe, side, CellsGuiHandler.GUI_PART_SUBNET_PROXY);

        return true;
    }

    @Override
    public boolean onPartShiftActivate(final EntityPlayer player, final EnumHand hand, final Vec3d pos) {
        return this.useMemoryCard(player);
    }

    private boolean isHoldingComplementaryPart(EntityPlayer player, EnumHand hand, CellsPartType expectedType) {
        ItemStack held = player.getHeldItem(hand);
        if (held.isEmpty()) return false;
        if (!(held.getItem() instanceof ItemCellsPart)) return false;

        CellsPartType type = CellsPartType.getById(held.getItemDamage());
        return type == expectedType;
    }

    private boolean tryPlaceComplementaryPart(EntityPlayer player, EnumHand hand) {
        if (player.world.isRemote) return true;

        IPartHost host = this.getHost();
        AEPartLocation side = this.getSide();
        if (host == null || side == null) return true;

        TileEntity selfTile = host.getTile();
        if (selfTile == null) return true;

        EnumFacing facing = side.getFacing();
        BlockPos adjacentPos = selfTile.getPos().offset(facing);

        // Delegate to AE2's part placement helper
        AEApi.instance().partHelper().placeBus(
            player.getHeldItem(hand), adjacentPos,
            facing.getOpposite(), player, hand, player.world);

        return true;
    }

    @Override
    public void getDrops(final List<ItemStack> drops, final boolean wrenched) {
        if (wrenched) return;

        for (int i = 0; i < this.upgrades.getSlots(); i++) {
            ItemStack stack = this.upgrades.getStackInSlot(i);
            if (!stack.isEmpty()) drops.add(stack.copy());
        }
    }

    public World getHostWorld() {
        TileEntity te = this.getHost() != null ? this.getHost().getTile() : null;
        return te != null ? te.getWorld() : null;
    }

    public BlockPos getHostPos() {
        return this.getHost() != null ? this.getHost().getLocation().getPos() : null;
    }

    @Nonnull
    @Override
    public IPartModel getStaticModels() {
        if (this.isActive() && this.isPowered()) return MODELS_HAS_CHANNEL;
        if (this.isPowered()) return MODELS_ON;
        return MODELS_OFF;
    }
}
