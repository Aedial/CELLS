package com.cells.blocks.fluidimportinterface;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
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
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.fluids.util.IAEFluidInventory;
import appeng.fluids.util.IAEFluidTank;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.MachineSource;
import appeng.tile.grid.AENetworkInvTile;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.SettingsFrom;
import appeng.util.inv.InvOperation;

import com.cells.blocks.interfacebase.FluidInterfaceLogic;
import com.cells.blocks.interfacebase.IFluidInterfaceHost;
import com.cells.gui.CellsGuiHandler;


/**
 * Tile entity for the Fluid Import Interface block.
 * Provides filter slots (fluid-based filters) and internal fluid tanks.
 * Only accepts fluids that match the filter in the corresponding slot.
 * Automatically imports stored fluids into the ME network.
 * <p>
 * Business logic is delegated to {@link FluidInterfaceLogic} to avoid code
 * duplication with part and export variants.
 */
public class TileFluidImportInterface extends AENetworkInvTile implements IGridTickable, IAEFluidInventory, IFluidInterfaceHost, FluidInterfaceLogic.Host {

    private final FluidInterfaceLogic logic;
    private final IActionSource actionSource;

    // Dummy inventory for AENetworkInvTile contract (fluid storage is not item-based)
    private final AppEngInternalInventory dummyInventory = new AppEngInternalInventory(this, 0, 0);

    public TileFluidImportInterface() {
        this.getProxy().setFlags(GridFlags.REQUIRE_CHANNEL);
        this.getProxy().setIdlePowerUsage(1.0);
        this.actionSource = new MachineSource(this);
        this.logic = new FluidInterfaceLogic(this);
    }

    // ============================== Host callbacks ==============================

    @Override
    public AENetworkProxy getGridProxy() {
        return this.getProxy();
    }

    @Override
    public IActionSource getActionSource() {
        return this.actionSource;
    }

    @Override
    public boolean isExport() {
        return false;
    }

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

    // ============================== IFluidInterfaceHost delegation ==============================

    @Override
    public IAEFluidTank getFilterInventory() {
        return this.logic.getFilterInventory();
    }

    @Override
    public AppEngInternalInventory getUpgradeInventory() {
        return this.logic.getUpgradeInventory();
    }

    @Override
    public void refreshFilterMap() {
        this.logic.refreshFilterMap();
    }

    @Override
    public void refreshUpgrades() {
        this.logic.refreshUpgrades();
    }

    @Override
    public boolean isValidUpgrade(ItemStack stack) {
        return this.logic.isValidUpgrade(stack);
    }

    @Override
    public void clearFilters() {
        this.logic.clearFilters();
    }

    @Override
    public boolean isTankEmpty(int slot) {
        return this.logic.isTankEmpty(slot);
    }

    @Nullable
    @Override
    public IAEFluidStack getFilterFluid(int slot) {
        return this.logic.getFilterFluid(slot);
    }

    @Override
    public void setFilterFluid(int slot, @Nullable IAEFluidStack fluid) {
        this.logic.setFilterFluid(slot, fluid);
    }

    @Nullable
    @Override
    public FluidStack getFluidInTank(int slot) {
        return this.logic.getFluidInTank(slot);
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

    @Override
    public int getInstalledCapacityUpgrades() {
        return this.logic.getInstalledCapacityUpgrades();
    }

    @Override
    public int getTotalPages() {
        return this.logic.getTotalPages();
    }

    @Override
    public int getCurrentPage() {
        return this.logic.getCurrentPage();
    }

    @Override
    public void setCurrentPage(int page) {
        this.logic.setCurrentPage(page);
    }

    @Override
    public int getCurrentPageStartSlot() {
        return this.logic.getCurrentPageStartSlot();
    }

    @Override
    public boolean hasOverflowUpgrade() {
        return this.logic.hasOverflowUpgrade();
    }

    @Override
    public boolean hasTrashUnselectedUpgrade() {
        return this.logic.hasTrashUnselectedUpgrade();
    }

    @Override
    public int insertFluidIntoTank(int slot, FluidStack fluid) {
        return this.logic.insertFluidIntoTank(slot, fluid);
    }

    // ============================== IInterfaceHost ==============================

    @Override
    public int getMainGuiId() {
        return CellsGuiHandler.GUI_FLUID_IMPORT_INTERFACE;
    }

    @Override
    public String getGuiTitleLangKey() {
        return "gui.cells.import_fluid_interface.title";
    }

    @Override
    public ItemStack getBackButtonStack() {
        return new ItemStack(this.getBlockType());
    }

    // ============================== AE2 tile contract ==============================

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
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
        return super.readFromStream(data) | this.logic.readTanksFromStream(data);
    }

    @Override
    protected void writeToStream(final ByteBuf data) throws IOException {
        super.writeToStream(data);
        this.logic.writeTanksToStream(data);
    }

    @Nonnull
    @Override
    public IItemHandler getInternalInventory() {
        return this.dummyInventory;
    }

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removed, ItemStack added) {
        if (inv == this.logic.getUpgradeInventory()) this.logic.onUpgradeChanged();
        this.markDirty();
    }

    /**
     * IAEFluidInventory callback - called when the fluid filter inventory changes.
     */
    @Override
    public void onFluidInventoryChanged(IAEFluidTank inv, int slot, InvOperation operation, FluidStack added, FluidStack removed) {
        if (inv == this.logic.getFilterInventory()) this.logic.onFluidFilterChanged(slot);
    }

    @Override
    public void onFluidInventoryChanged(IAEFluidTank inv, int slot) {
        if (inv == this.logic.getFilterInventory()) this.logic.onFluidFilterChanged(slot);
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

    // ============================== Capability handling ==============================

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) return true;
        return super.hasCapability(capability, facing);
    }

    @Override
    @Nullable
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(this.logic.getExternalHandler());
        }
        return super.getCapability(capability, facing);
    }
}
