package com.cells.blocks.interfacebase;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;

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
        implements IGridTickable, IInterfaceHost, AbstractResourceInterfaceLogic.Host {

    protected final IActionSource actionSource;
    protected L logic;

    // Dummy inventory for AENetworkInvTile contract.
    // Storage is managed by the logic and serialized via writeToStream/readFromStream.
    private final AppEngInternalInventory dummyInventory = new AppEngInternalInventory(this, 0, 0);

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
        this.markDirty();
    }

    @Override
    public void markForNetworkUpdate() {
        this.markForUpdate();
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

    public AppEngInternalInventory getUpgradeInventory() {
        return this.logic.getUpgradeInventory();
    }

    public void refreshFilterMap() {
        this.logic.refreshFilterMap();
    }

    public void refreshUpgrades() {
        this.logic.refreshUpgrades();
    }

    public boolean isValidUpgrade(ItemStack stack) {
        return this.logic.isValidUpgrade(stack);
    }

    @Override
    public int getMaxSlotSize() {
        return this.logic.getMaxSlotSize();
    }

    @Override
    public void setMaxSlotSize(int size) {
        this.logic.setMaxSlotSize(size);
    }

    @Override
    public int getPollingRate() {
        return this.logic.getPollingRate();
    }

    @Override
    public void setPollingRate(int ticks) {
        this.logic.setPollingRate(ticks);
    }

    public void setPollingRate(int ticks, EntityPlayer player) {
        this.logic.setPollingRate(ticks, player);
    }

    public int getInstalledCapacityUpgrades() {
        return this.logic.getInstalledCapacityUpgrades();
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

    public int getCurrentPageStartSlot() {
        return this.logic.getCurrentPageStartSlot();
    }

    public boolean hasOverflowUpgrade() {
        return this.logic.hasOverflowUpgrade();
    }

    public boolean hasTrashUnselectedUpgrade() {
        return this.logic.hasTrashUnselectedUpgrade();
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

    @Override
    protected boolean readFromStream(final ByteBuf data) throws IOException {
        boolean changed = super.readFromStream(data);
        changed |= this.logic.readStorageFromStream(data);
        changed |= this.logic.readFiltersFromStream(data);
        return changed;
    }

    @Override
    protected void writeToStream(final ByteBuf data) throws IOException {
        super.writeToStream(data);
        this.logic.writeStorageToStream(data);
        this.logic.writeFiltersToStream(data);
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
        if (inv == this.logic.getUpgradeInventory()) this.logic.onUpgradeChanged();
        this.markDirty();
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
        return this.logic.onTick();
    }
}
