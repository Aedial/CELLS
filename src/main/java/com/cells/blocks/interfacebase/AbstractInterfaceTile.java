package com.cells.blocks.interfacebase;

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
import com.cells.api.IUpgradeable;
import com.cells.helpers.InterfaceApiHelper;
import com.cells.util.PowerStateHelper;


/**
 * Abstract base class for all interface tile entities (import/export, item/fluid/gas).
 * <p>
 * This class extracts common functionality from all Tile*Interface classes:
 * <ul>
 *   <li>Host callbacks (markDirty, getHostWorld, etc.)</li>
 *   <li>Grid event handling and ticking</li>
 *   <li>NBT serialization framework</li>
 *   <li>Memory card compatibility</li>
 *   <li>Common delegation to logic</li>
 * </ul>
 * <p>
 * Subclasses only need to:
 * <ul>
 *   <li>Create their specific logic in constructor and call {@link #initLogic(IInterfaceLogic)}</li>
 *   <li>Implement {@link #isExport()}</li>
 *   <li>Implement {@link #getMainGuiId()} and type-specific host interface methods</li>
 *   <li>Override stream sync if needed (item vs fluid/gas differ)</li>
 *   <li>Provide capability handlers</li>
 * </ul>
 *
 * @param <L> The logic class type (ItemInterfaceLogic, FluidInterfaceLogic, etc.)
 */
public abstract class AbstractInterfaceTile<L extends IInterfaceLogic> extends AENetworkInvTile
    implements IGridTickable, IInterfaceHost, AbstractResourceInterfaceLogic.Host,
           IInterfaceProvider, IUpgradeable, IPowerChannelState {

    protected final IActionSource actionSource;
    protected L logic;

    // Dummy inventory for AENetworkInvTile contract.
    // Storage is managed by the logic and serialized via writeToStream/readFromStream.
    private final AppEngInternalInventory dummyInventory = new AppEngInternalInventory(this, 0, 0);

    // Debounce for markDirtyAndSave
    // getTotalWorldTime is two field reads + a long compare,
    // markChunkDirty is two chunk map lookups + a boolean write.
    private long lastSaveTick = -1;

    protected AbstractInterfaceTile() {
        this.getProxy().setFlags(GridFlags.REQUIRE_CHANNEL);
        this.getProxy().setIdlePowerUsage(1.0);
        this.actionSource = new MachineSource(this);
        // Logic must be created by subclass and passed to initLogic()
    }

    /**
     * Initialize the logic instance. Must be called by subclass constructor after super().
     *
     * @param logic The logic instance (created with 'this' as host)
     */
    protected void initLogic(L logic) {
        this.logic = logic;
    }

    @Override
    public boolean isPowered() {
        return PowerStateHelper.isPowered(this.getProxy());
    }

    @Override
    public boolean isActive() {
        return PowerStateHelper.hasChannel(this.getProxy());
    }

    // ============================== AbstractResourceInterfaceLogic.Host callbacks ==============================

    @Override
    public AENetworkProxy getGridProxy() {
        return this.getProxy();
    }

    @Override
    public IActionSource getActionSource() {
        return this.actionSource;
    }

    @Override
    public abstract boolean isExport();

    @Override
    public void markDirtyAndSave() {
        if (this.world == null || this.world.isRemote) return;

        // Debounce to once per tick
        long currentTick = this.world.getTotalWorldTime();
        if (this.lastSaveTick == currentTick) return;
        this.lastSaveTick = currentTick;

        // Call markChunkDirty directly to flag the chunk for saving.
        // We intentionally skip markForSave() / saveChanges() to bypass the bloat.
        this.world.markChunkDirty(this.pos, this);
    }

    @Override
    @Nullable
    public World getHostWorld() {
        return this.world;
    }

    @Override
    public BlockPos getHostPos() {
        return this.pos;
    }

    @Override
    public IGridTickable getTickable() {
        return this;
    }

    // ============================== Common delegation to logic ==============================

    @Nonnull
    public L getInterfaceLogic() {
        return this.logic;
    }

    @Override
    @Nonnull
    public List<com.cells.api.IInterfaceHost> getInterfaceHosts() {
        return InterfaceApiHelper.createInterfaceHosts(this, this.logic, EnumSet.allOf(EnumFacing.class));
    }

    @Override
    @Nonnull
    public AppEngInternalInventory getUpgradeInventory() {
        return this.logic.getUpgradeInventory();
    }

    public void refreshFilterMap() {
        this.logic.refreshFilterMap();
    }

    @Override
    public long validateMaxSlotSize(long size) {
        return this.logic.validateMaxSlotSize(size);
    }

    @Override
    public long getMaxSlotSize() {
        return this.logic.getMaxSlotSize();
    }

    @Override
    public long setMaxSlotSize(long size) {
        return this.logic.setMaxSlotSize(size);
    }

    @Override
    public long getEffectiveMaxSlotSize(int slot) {
        return this.logic.getEffectiveMaxSlotSize(slot);
    }

    @Override
    public long setMaxSlotSizeOverride(int slot, long size) {
        return this.logic.setMaxSlotSizeOverride(slot, size);
    }

    @Override
    public long getMaxSlotSizeOverride(int slot) {
        return this.logic.getMaxSlotSizeOverride(slot);
    }

    @Override
    public void clearMaxSlotSizeOverride(int slot) {
        this.logic.clearMaxSlotSizeOverride(slot);
    }

    @Override
    public int getPollingRate() {
        return this.logic.getPollingRate();
    }

    @Override
    public int setPollingRate(int ticks) {
        return this.logic.setPollingRate(ticks);
    }

    public int setPollingRate(int ticks, EntityPlayer player) {
        return this.logic.setPollingRate(ticks, player);
    }

    public int getTotalPages() {
        return this.logic.getTotalPages();
    }

    public int getCurrentPage() {
        return this.logic.getCurrentPage();
    }

    public void setCurrentPage(int page) {
        this.logic.setCurrentPage(page);
    }

    // ============================== IInterfaceHost implementation ==============================

    @Override
    public abstract int getMainGuiId();

    @Override
    public String getTypeName() {
        return this.logic.getTypeName();
    }

    @Override
    public ItemStack getBackButtonStack() {
        return new ItemStack(this.getBlockType());
    }

    // ============================== AE2 tile contract ==============================

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        readLogicFromNBT(data);
    }

    /**
     * Read logic state from NBT. Called by readFromNBT.
     * Override in item tiles to use legacy format with isTile flag.
     */
    protected void readLogicFromNBT(final NBTTagCompound data) {
        this.logic.readFromNBT(data);
    }

    @Override
    @Nonnull
    public NBTTagCompound writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        this.logic.writeToNBT(data);
        return data;
    }

    @Nonnull
    @Override
    public NBTTagCompound downloadSettings(SettingsFrom from) {
        NBTTagCompound output = super.downloadSettings(from);
        if (output == null) output = new NBTTagCompound();

        NBTTagCompound logicSettings;
        if (from == SettingsFrom.DISMANTLE_ITEM) {
            // Disable drops so getDrops() isn't called - upgrades are saved in NBT instead
            this.disableDrops();
            logicSettings = this.logic.downloadSettingsForDismantle();
        } else {
            logicSettings = this.logic.downloadSettings();
        }

        output.merge(logicSettings);
        return output;
    }

    @Override
    public NBTTagCompound downloadSettingsWithFilter() {
        return this.logic.downloadSettingsWithFilter();
    }

    @Override
    public void uploadSettings(SettingsFrom from, NBTTagCompound compound, EntityPlayer player) {
        super.uploadSettings(from, compound, player);
        this.logic.uploadSettings(compound, player);
    }

    @Nonnull
    @Override
    public IItemHandler getInternalInventory() {
        // Return dummy inventory - storage is managed by the logic, not AEBaseInvTile
        return this.dummyInventory;
    }

    /**
     * Handle inventory changes. Default implementation handles upgrade changes.
     * Subclasses may override for type-specific behavior.
     */
    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removed, ItemStack added) {
        this.logic.onChangeInventory(inv, slot, removed, added);
        this.markDirtyAndSave();
    }

    @Override
    public void getDrops(final World w, final BlockPos pos, final List<ItemStack> drops) {
        this.logic.getDrops(drops);
    }

    @Override
    public void getNoDrops(final World w, final BlockPos pos, final List<ItemStack> drops) {
        // During wrench dismantling, upgrades are saved to NBT but stored items must still drop
        this.logic.getStorageDrops(drops);
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

        // Re-scan the capability cache now that all TEs are in the world and the
        // grid proxy is ready. During readFromNBT, adjacent TEs may not have been
        // loaded yet, leaving the push/pull card's cache empty.
        this.logic.onGridReady();
    }

    // ============================== Grid events ==============================

    @Override
    public void gridChanged() {
        this.logic.wakeUpIfAdaptive();
    }

    @MENetworkEventSubscribe
    public void powerStatusChanged(final MENetworkPowerStatusChange event) {
        this.logic.wakeUpIfAdaptive();
    }

    @MENetworkEventSubscribe
    public void channelsChanged(final MENetworkChannelsChanged event) {
        this.logic.wakeUpIfAdaptive();
    }

    // ============================== IGridTickable ==============================

    @Override
    @Nonnull
    public TickingRequest getTickingRequest(@Nonnull final IGridNode node) {
        return this.logic.getTickingRequest();
    }

    @Override
    @Nonnull
    public TickRateModulation tickingRequest(@Nonnull final IGridNode node, final int ticksSinceLastCall) {
        return this.logic.onTick(ticksSinceLastCall);
    }
}
