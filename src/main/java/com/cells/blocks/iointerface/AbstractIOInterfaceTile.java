package com.cells.blocks.iointerface;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;

import appeng.api.implementations.IPowerChannelState;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.MachineSource;
import appeng.tile.grid.AENetworkInvTile;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.SettingsFrom;
import appeng.util.inv.InvOperation;

import com.cells.api.IInterfaceProvider;
import com.cells.blocks.interfacebase.AbstractResourceInterfaceLogic;
import com.cells.blocks.interfacebase.IInterfaceLogic;
import com.cells.helpers.InterfaceApiHelper;
import com.cells.util.PowerStateHelper;


/**
 * Abstract base class for IO (Import+Export) interface tile entities.
 * <p>
 * An IO interface wraps two logic instances of the same resource type, one for import
 * and one for export. The GUI provides two tabs to switch between directions.
 * <p>
 * Unlike the combined interface which shares an upgrade inventory across resource types,
 * the IO interface gives each direction its own upgrade inventory but shares the polling rate.
 * <p>
 * Subclasses must:
 * <ul>
 *   <li>Create both logic instances in constructor and call {@link #initLogics(IInterfaceLogic, IInterfaceLogic)}</li>
 *   <li>Implement {@link #getResourceType()}</li>
 *   <li>Implement {@link #getMainGuiId()}</li>
 *   <li>Provide capability handlers</li>
 * </ul>
 *
 * @param <L> The logic class type (ItemInterfaceLogic, FluidInterfaceLogic, etc.)
 */
public abstract class AbstractIOInterfaceTile<L extends IInterfaceLogic>
    extends AENetworkInvTile implements IGridTickable, IIOInterfaceHost, IInterfaceProvider,
        IPowerChannelState {

    protected final IActionSource actionSource;

    /** Import logic (direction = import, isExport = false). */
    protected L importLogic;

    /** Export logic (direction = export, isExport = true). */
    protected L exportLogic;

    /** Both logics for iteration. Immutable after {@link #initLogics}. */
    private List<IInterfaceLogic> allLogicsList;

    /** Active direction tab. Not saved to NBT (client-side transient). */
    private int activeDirectionTab = IIOInterfaceHost.TAB_IMPORT;

    // Dummy inventory for AENetworkInvTile contract.
    private final AppEngInternalInventory dummyInventory = new AppEngInternalInventory(this, 0, 0);

    // Debounce: markChunkDirty at most once per tick
    private long lastSaveTick = -1;

    protected AbstractIOInterfaceTile() {
        this.getProxy().setFlags(GridFlags.REQUIRE_CHANNEL);
        this.getProxy().setIdlePowerUsage(1.0);
        this.actionSource = new MachineSource(this);
    }

    /**
     * Initialize both logics. Must be called by subclass constructor.
     *
     * @param importLogic The import logic instance
     * @param exportLogic The export logic instance
     */
    protected void initLogics(L importLogic, L exportLogic) {
        this.importLogic = importLogic;
        this.exportLogic = exportLogic;
        this.allLogicsList = Collections.unmodifiableList(
            Arrays.asList((IInterfaceLogic) importLogic, (IInterfaceLogic) exportLogic)
        );
    }

    @Override
    public boolean isPowered() {
        return PowerStateHelper.isPowered(this.getProxy());
    }

    @Override
    public boolean isActive() {
        return PowerStateHelper.hasChannel(this.getProxy());
    }

    // ============================== Direction Host Wrapper ==============================

    /**
     * Wrapper that delegates all Host methods to the tile, except {@link #isExport()}
     * which returns a fixed direction. This allows two logics of the same type to coexist
     * with different import/export behavior.
     * <p>
     * Subclasses cast this to their specific Host interface
     * (e.g., ItemInterfaceLogic.Host, FluidInterfaceLogic.Host) since those interfaces
     * don't add any methods beyond AbstractResourceInterfaceLogic.Host.
     */
    protected class DirectionHost implements AbstractResourceInterfaceLogic.Host {

        private final boolean export;

        public DirectionHost(boolean export) {
            this.export = export;
        }

        @Override
        public AENetworkProxy getGridProxy() {
            return AbstractIOInterfaceTile.this.getProxy();
        }

        @Override
        public IActionSource getActionSource() {
            return AbstractIOInterfaceTile.this.actionSource;
        }

        @Override
        public boolean isExport() {
            return this.export;
        }

        /**
         * Namespace this direction's NBT keys so import and export logics (which share
         * a single resource type and a single NBT compound) do not collide on filters,
         * storage, max slot size, polling rate, upgrades, or per-slot overrides.
         */
        @Override
        public String getNBTKeyPrefix() {
            return this.export ? "export_" : "import_";
        }

        @Override
        public void markDirtyAndSave() {
            AbstractIOInterfaceTile.this.doMarkDirtyAndSave();
        }

        @Override
        @Nullable
        public World getHostWorld() {
            return AbstractIOInterfaceTile.this.world;
        }

        @Override
        public BlockPos getHostPos() {
            return AbstractIOInterfaceTile.this.pos;
        }

        @Override
        public IGridTickable getTickable() {
            return AbstractIOInterfaceTile.this;
        }

        @Override
        public EnumSet<EnumFacing> getTargetFacings() {
            return EnumSet.allOf(EnumFacing.class);
        }

        // IAEAppEngInventory

        @Override
        public void saveChanges() {
            AbstractIOInterfaceTile.this.saveChanges();
        }

        @Override
        public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc,
                                      ItemStack removed, ItemStack added) {
            AbstractIOInterfaceTile.this.onChangeInventory(inv, slot, mc, removed, added);
        }
    }

    // ============================== IIOInterfaceHost ==============================

    @Nonnull
    @Override
    public IInterfaceLogic getImportLogic() {
        return this.importLogic;
    }

    @Nonnull
    @Override
    public IInterfaceLogic getExportLogic() {
        return this.exportLogic;
    }

    @Override
    public List<IInterfaceLogic> getAllLogics() {
        return this.allLogicsList;
    }

    @Override
    @Nonnull
    public List<com.cells.api.IInterfaceHost> getInterfaceHosts() {
        return InterfaceApiHelper.createInterfaceHosts(this, EnumSet.allOf(EnumFacing.class));
    }

    @Override
    public int getActiveDirectionTab() {
        return this.activeDirectionTab;
    }

    @Override
    public void setActiveDirectionTab(int tab) {
        if (tab == IIOInterfaceHost.TAB_IMPORT || tab == IIOInterfaceHost.TAB_EXPORT) {
            this.activeDirectionTab = tab;
        }
    }

    // ============================== IInterfaceHost ==============================

    @Override
    public abstract int getMainGuiId();

    @Override
    @Nullable
    public World getHostWorld() {
        return this.world;
    }

    @Override
    public BlockPos getHostPos() {
        return this.pos;
    }

    public IGridTickable getTickable() {
        return this;
    }

    @Override
    public String getTypeName() {
        return getResourceType().name().toLowerCase();
    }

    @Override
    public ItemStack getBackButtonStack() {
        return new ItemStack(this.getBlockType());
    }

    // Delegate to active tab's logic for direction-specific values

    @Override
    public long validateMaxSlotSize(long size) {
        return getActiveLogic().validateMaxSlotSize(size);
    }

    @Override
    public long getMaxSlotSize() {
        return getActiveLogic().getMaxSlotSize();
    }

    @Override
    public long setMaxSlotSize(long size) {
        return getActiveLogic().setMaxSlotSize(size);
    }

    @Override
    public long getEffectiveMaxSlotSize(int slot) {
        return getActiveLogic().getEffectiveMaxSlotSize(slot);
    }

    @Override
    public long setMaxSlotSizeOverride(int slot, long size) {
        return getActiveLogic().setMaxSlotSizeOverride(slot, size);
    }

    @Override
    public long getMaxSlotSizeOverride(int slot) {
        return getActiveLogic().getMaxSlotSizeOverride(slot);
    }

    @Override
    public void clearMaxSlotSizeOverride(int slot) {
        getActiveLogic().clearMaxSlotSizeOverride(slot);
    }

    // Polling rate is SHARED between both logics

    @Override
    public int getPollingRate() {
        return this.importLogic.getPollingRate();
    }

    @Override
    public int setPollingRate(int ticks) {
        int result = this.importLogic.setPollingRate(ticks);
        this.exportLogic.setPollingRate(ticks);
        return result;
    }

    // ============================== AbstractResourceInterfaceLogic.Host callbacks ==============================

    public void doMarkDirtyAndSave() {
        if (this.world == null || this.world.isRemote) return;

        long currentTick = this.world.getTotalWorldTime();
        if (this.lastSaveTick == currentTick) return;
        this.lastSaveTick = currentTick;

        this.world.markChunkDirty(this.pos, this);
    }

    // ============================== NBT serialization ==============================

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        readLogicsFromNBT(data);
    }

    /**
     * Read both logics from NBT. Override in subclasses if needed (e.g., isTile flag).
     */
    protected void readLogicsFromNBT(final NBTTagCompound data) {
        // Each logic uses direction-prefixed keys (e.g., "import_itemFilters", "export_itemMaxSlotSize",
        // "import_upgrades") via Host#getNBTKeyPrefix(), so import and export state never collide.
        this.importLogic.readFromNBT(data, true);
        this.exportLogic.readFromNBT(data, true);
    }

    @Override
    @Nonnull
    public NBTTagCompound writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);

        this.importLogic.writeToNBT(data);
        this.exportLogic.writeToNBT(data);

        return data;
    }

    // ============================== Memory card / settings ==============================

    @Nonnull
    @Override
    public NBTTagCompound downloadSettings(SettingsFrom from) {
        NBTTagCompound output = super.downloadSettings(from);
        if (output == null) output = new NBTTagCompound();

        if (from == SettingsFrom.DISMANTLE_ITEM) this.disableDrops();

        for (IInterfaceLogic logic : this.allLogicsList) {
            NBTTagCompound logicSettings = (from == SettingsFrom.DISMANTLE_ITEM)
                ? logic.downloadSettingsForDismantle()
                : logic.downloadSettings();
            output.merge(logicSettings);
        }

        return output;
    }

    @Override
    public NBTTagCompound downloadSettingsWithFilter() {
        NBTTagCompound output = new NBTTagCompound();
        for (IInterfaceLogic logic : this.allLogicsList) {
            output.merge(logic.downloadSettingsWithFilter());
        }
        return output;
    }

    @Override
    public void uploadSettings(SettingsFrom from, NBTTagCompound compound, EntityPlayer player) {
        super.uploadSettings(from, compound, player);
        for (IInterfaceLogic logic : this.allLogicsList) {
            logic.uploadSettings(compound, player);
        }
    }

    // ============================== AENetworkInvTile contract ==============================

    @Nonnull
    @Override
    public IItemHandler getInternalInventory() {
        return this.dummyInventory;
    }

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc,
                                  ItemStack removed, ItemStack added) {
        for (IInterfaceLogic logic : this.allLogicsList) {
            logic.onChangeInventory(inv, slot, removed, added);
        }
        doMarkDirtyAndSave();
    }

    @Override
    public void getDrops(final World w, final BlockPos pos, final java.util.List<ItemStack> drops) {
        for (IInterfaceLogic logic : this.allLogicsList) logic.getDrops(drops);
    }

    @Override
    public void getNoDrops(final World w, final BlockPos pos, final java.util.List<ItemStack> drops) {
        for (IInterfaceLogic logic : this.allLogicsList) logic.getStorageDrops(drops);
    }

    @Override
    @Nonnull
    public AECableType getCableConnectionType(@Nonnull final AEPartLocation dir) {
        return AECableType.SMART;
    }

    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    // ============================== Grid lifecycle ==============================

    @Override
    public void onReady() {
        super.onReady();
        for (IInterfaceLogic logic : this.allLogicsList) logic.onGridReady();
    }

    // ============================== Grid events ==============================

    @Override
    public void gridChanged() {
        for (IInterfaceLogic logic : this.allLogicsList) logic.wakeUpIfAdaptive();
    }

    @MENetworkEventSubscribe
    public void powerStatusChanged(final MENetworkPowerStatusChange event) {
        for (IInterfaceLogic logic : this.allLogicsList) logic.wakeUpIfAdaptive();
    }

    @MENetworkEventSubscribe
    public void channelsChanged(final MENetworkChannelsChanged event) {
        for (IInterfaceLogic logic : this.allLogicsList) logic.wakeUpIfAdaptive();
    }

    // ============================== IGridTickable ==============================

    @Override
    @Nonnull
    public TickingRequest getTickingRequest(@Nonnull final IGridNode node) {
        TickingRequest importReq = this.importLogic.getTickingRequest();
        TickingRequest exportReq = this.exportLogic.getTickingRequest();

        return new TickingRequest(
            Math.min(importReq.minTickRate, exportReq.minTickRate),
            Math.min(importReq.maxTickRate, exportReq.maxTickRate),
            importReq.isSleeping && exportReq.isSleeping,
            true
        );
    }

    @Override
    @Nonnull
    public TickRateModulation tickingRequest(@Nonnull final IGridNode node, final int ticksSinceLastCall) {
        TickRateModulation importMod = this.importLogic.onTick(ticksSinceLastCall);
        TickRateModulation exportMod = this.exportLogic.onTick(ticksSinceLastCall);

        // Return the more aggressive modulation
        return importMod.ordinal() > exportMod.ordinal() ? importMod : exportMod;
    }

    // ============================== Neighbor change ==============================

    /**
     * Handle neighbor block changes for both logics' auto-pull/push capability caches.
     */
    public void onNeighborChanged(BlockPos fromPos) {
        for (IInterfaceLogic logic : this.allLogicsList) {
            if (logic instanceof AbstractResourceInterfaceLogic) {
                ((AbstractResourceInterfaceLogic<?, ?, ?>) logic).onNeighborChanged(fromPos);
            }
        }
    }
}
