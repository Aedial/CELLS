package com.cells.blocks.combinedinterface;

import java.util.ArrayList;
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
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
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
import appeng.capabilities.Capabilities;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.MachineSource;
import appeng.tile.grid.AENetworkInvTile;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.SettingsFrom;
import appeng.util.inv.InvOperation;

import com.cells.api.IInterfaceHost;
import com.cells.api.IInterfaceProvider;
import com.cells.api.IUpgradeable;
import com.cells.blocks.interfacebase.AbstractResourceInterfaceLogic;
import com.cells.blocks.interfacebase.IInterfaceLogic;
import com.cells.blocks.interfacebase.fluid.FluidInterfaceLogic;
import com.cells.blocks.interfacebase.item.ItemInterfaceLogic;
import com.cells.helpers.InterfaceApiHelper;
import com.cells.integration.mekanismenergistics.CombinedInterfaceGasHelper;
import com.cells.integration.mekanismenergistics.MekanismEnergisticsIntegration;
import com.cells.integration.thaumicenergistics.CombinedInterfaceEssentiaHelper;
import com.cells.integration.thaumicenergistics.ThaumicEnergisticsIntegration;
import com.cells.network.sync.ResourceType;
import com.cells.util.PowerStateHelper;


/**
 * Abstract base class for combined interface tile entities.
 * <p>
 * A combined interface wraps multiple resource-specific logics (item, fluid, gas, essentia)
 * into a single tile entity. All logics share one upgrade inventory, one tick handler,
 * and one grid node. The GUI provides tabs to switch between resource types.
 * <p>
 * Subclasses only need to implement {@link #isExport()} and {@link #getMainGuiId()}.
 */
public abstract class AbstractCombinedInterfaceTile extends AENetworkInvTile
        implements IGridTickable, ICombinedInterfaceHost,
           ItemInterfaceLogic.Host, FluidInterfaceLogic.Host,
       IInterfaceProvider, IUpgradeable, IPowerChannelState {

    protected final IActionSource actionSource;

    // ============================== Logics ==============================
    // Item and fluid are always present. Gas and essentia are conditional on mod availability.

    protected final ItemInterfaceLogic itemLogic;
    protected final FluidInterfaceLogic fluidLogic;

    /** Gas logic, null if MekanismEnergistics is not loaded. */
    @Nullable
    protected final IInterfaceLogic gasLogic;

    /** Essentia logic, null if ThaumicEnergistics is not loaded. */
    @Nullable
    protected final IInterfaceLogic essentiaLogic;

    /** All loaded logics for iteration. Immutable after construction. */
    private final List<IInterfaceLogic> allLogics;

    /** Available resource type tabs. Immutable after construction. */
    private final List<ResourceType> availableTabs;

    // ============================== State ==============================

    /** Active tab for the GUI. Client-side only, not saved to NBT. */
    private ResourceType activeTab = ResourceType.ITEM;

    // Dummy inventory for AENetworkInvTile contract.
    private final AppEngInternalInventory dummyInventory = new AppEngInternalInventory(this, 0, 0);

    // Debounce: markChunkDirty to once per tick
    private long lastSaveTick = -1;

    protected AbstractCombinedInterfaceTile() {
        this.getProxy().setFlags(GridFlags.REQUIRE_CHANNEL);
        this.getProxy().setIdlePowerUsage(1.0);
        this.actionSource = new MachineSource(this);

        // Create the primary (item) logic with its own upgrade inventory
        this.itemLogic = new ItemInterfaceLogic(this);

        // All secondary logics share the primary's upgrade inventory
        AppEngInternalInventory sharedUpgradeInv = this.itemLogic.getUpgradeInventory();

        this.fluidLogic = new FluidInterfaceLogic(this, sharedUpgradeInv);

        // Conditionally create gas logic
        IInterfaceLogic gas = null;
        if (MekanismEnergisticsIntegration.isModLoaded()) {
            gas = CombinedInterfaceGasHelper.createGasLogic(this, sharedUpgradeInv);
        }
        this.gasLogic = gas;

        // Conditionally create essentia logic
        IInterfaceLogic essentia = null;
        if (ThaumicEnergisticsIntegration.isModLoaded()) {
            essentia = CombinedInterfaceEssentiaHelper.createEssentiaLogic(this, sharedUpgradeInv);
        }
        this.essentiaLogic = essentia;

        // Build the logic list and tab list (immutable after construction)
        List<IInterfaceLogic> logics = new ArrayList<>(4);
        List<ResourceType> tabs = new ArrayList<>(4);

        logics.add(this.itemLogic);
        tabs.add(ResourceType.ITEM);

        logics.add(this.fluidLogic);
        tabs.add(ResourceType.FLUID);

        if (this.gasLogic != null) {
            logics.add(this.gasLogic);
            tabs.add(ResourceType.GAS);
        }

        if (this.essentiaLogic != null) {
            logics.add(this.essentiaLogic);
            tabs.add(ResourceType.ESSENTIA);
        }

        this.allLogics = Collections.unmodifiableList(logics);
        this.availableTabs = Collections.unmodifiableList(tabs);
    }

    @Override
    public boolean isPowered() {
        return PowerStateHelper.isPowered(this.getProxy());
    }

    @Override
    public boolean isActive() {
        return PowerStateHelper.hasChannel(this.getProxy());
    }

    // ============================== ICombinedInterfaceHost ==============================

    @Override
    public ItemInterfaceLogic getItemLogic() {
        return this.itemLogic;
    }

    @Override
    public FluidInterfaceLogic getFluidLogic() {
        return this.fluidLogic;
    }

    @Override
    @Nullable
    public IInterfaceLogic getGasLogic() {
        return this.gasLogic;
    }

    @Override
    @Nullable
    public IInterfaceLogic getEssentiaLogic() {
        return this.essentiaLogic;
    }

    @Override
    public List<IInterfaceLogic> getAllLogics() {
        return this.allLogics;
    }

    @Override
    @Nonnull
    public List<IInterfaceHost> getInterfaceHosts() {
        return InterfaceApiHelper.createInterfaceHosts(this, EnumSet.allOf(EnumFacing.class));
    }

    @Override
    @Nonnull
    public AppEngInternalInventory getUpgradeInventory() {
        return this.itemLogic.getUpgradeInventory();
    }

    @Override
    @Nullable
    public IInterfaceLogic getLogicForType(ResourceType type) {
        switch (type) {
            case ITEM: return this.itemLogic;
            case FLUID: return this.fluidLogic;
            case GAS: return this.gasLogic;
            case ESSENTIA: return this.essentiaLogic;
            default: return null;
        }
    }

    @Override
    public ResourceType getActiveTab() {
        return this.activeTab;
    }

    @Override
    public void setActiveTab(ResourceType tab) {
        if (this.availableTabs.contains(tab)) {
            this.activeTab = tab;
        }
    }

    @Override
    public List<ResourceType> getAvailableTabs() {
        return this.availableTabs;
    }

    // ============================== IInterfaceHost ==============================

    @Override
    public String getTypeName() {
        return "combined";
    }

    @Override
    public String getGuiTitleLangKey() {
        return String.format("cells.%s_interface.combined.title", this.getDirectionString());
    }

    @Override
    public ItemStack getBackButtonStack() {
        return new ItemStack(this.getBlockType());
    }

    // The active tab's logic is used for delegation of IInterfaceHost methods
    // that depend on the current resource type context.

    @Override
    public long validateMaxSlotSize(long size) {
        IInterfaceLogic logic = getLogicForType(this.activeTab);
        return logic != null ? logic.validateMaxSlotSize(size) : this.itemLogic.validateMaxSlotSize(size);
    }

    @Override
    public long getMaxSlotSize() {
        IInterfaceLogic logic = getLogicForType(this.activeTab);
        return logic != null ? logic.getMaxSlotSize() : this.itemLogic.getMaxSlotSize();
    }

    @Override
    public long setMaxSlotSize(long size) {
        IInterfaceLogic logic = getLogicForType(this.activeTab);
        return logic != null ? logic.setMaxSlotSize(size) : this.itemLogic.setMaxSlotSize(size);
    }

    @Override
    public long getEffectiveMaxSlotSize(int slot) {
        IInterfaceLogic logic = getLogicForType(this.activeTab);
        return logic != null ? logic.getEffectiveMaxSlotSize(slot) : this.itemLogic.getEffectiveMaxSlotSize(slot);
    }

    @Override
    public long setMaxSlotSizeOverride(int slot, long size) {
        IInterfaceLogic logic = getLogicForType(this.activeTab);
        return logic != null ? logic.setMaxSlotSizeOverride(slot, size) : this.itemLogic.setMaxSlotSizeOverride(slot, size);
    }

    @Override
    public long getMaxSlotSizeOverride(int slot) {
        IInterfaceLogic logic = getLogicForType(this.activeTab);
        return logic != null ? logic.getMaxSlotSizeOverride(slot) : this.itemLogic.getMaxSlotSizeOverride(slot);
    }

    @Override
    public void clearMaxSlotSizeOverride(int slot) {
        IInterfaceLogic logic = getLogicForType(this.activeTab);
        if (logic != null) {
            logic.clearMaxSlotSizeOverride(slot);
        } else {
            this.itemLogic.clearMaxSlotSizeOverride(slot);
        }
    }

    @Override
    public int getPollingRate() {
        IInterfaceLogic logic = getLogicForType(this.activeTab);
        return logic != null ? logic.getPollingRate() : this.itemLogic.getPollingRate();
    }

    @Override
    public int setPollingRate(int ticks) {
        IInterfaceLogic logic = getLogicForType(this.activeTab);
        return logic != null ? logic.setPollingRate(ticks) : this.itemLogic.setPollingRate(ticks);
    }

    // ============================== AbstractResourceInterfaceLogic.Host ==============================

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

        long currentTick = this.world.getTotalWorldTime();
        if (this.lastSaveTick == currentTick) return;
        this.lastSaveTick = currentTick;

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

    // ============================== NBT serialization ==============================

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);

        // Read all logics from the same NBT compound.
        // Each logic uses type-prefixed keys (e.g., "itemFilters", "fluidMaxSlotSize")
        // to avoid collisions when sharing the same compound.
        this.itemLogic.readFromNBT(data, true);
        this.fluidLogic.readFromNBT(data, true);
        if (this.gasLogic != null) this.gasLogic.readFromNBT(data, true);
        if (this.essentiaLogic != null) this.essentiaLogic.readFromNBT(data, true);
    }

    @Override
    @Nonnull
    public NBTTagCompound writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);

        this.itemLogic.writeToNBT(data);
        this.fluidLogic.writeToNBT(data);
        if (this.gasLogic != null) this.gasLogic.writeToNBT(data);
        if (this.essentiaLogic != null) this.essentiaLogic.writeToNBT(data);

        return data;
    }

    // ============================== Memory card / settings ==============================

    @Nonnull
    @Override
    public NBTTagCompound downloadSettings(SettingsFrom from) {
        NBTTagCompound output = super.downloadSettings(from);
        if (output == null) output = new NBTTagCompound();

        if (from == SettingsFrom.DISMANTLE_ITEM) {
            this.disableDrops();
        }

        // Save all logics' settings into the same compound
        for (IInterfaceLogic logic : this.allLogics) {
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
        for (IInterfaceLogic logic : this.allLogics) {
            output.merge(logic.downloadSettingsWithFilter());
        }
        return output;
    }

    @Override
    public void uploadSettings(SettingsFrom from, NBTTagCompound compound, EntityPlayer player) {
        super.uploadSettings(from, compound, player);

        // Upload to all logics - each logic reads only its own prefixed keys
        for (IInterfaceLogic logic : this.allLogics) {
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
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removed, ItemStack added) {
        // Forward to ALL logics so each detects its own inventory changes.
        // Each logic checks if 'inv' matches its own inventories and acts accordingly.
        for (IInterfaceLogic logic : this.allLogics) {
            logic.onChangeInventory(inv, slot, removed, added);
        }
        this.markDirtyAndSave();
    }

    @Override
    public void getDrops(final World w, final BlockPos pos, final List<ItemStack> drops) {
        for (IInterfaceLogic logic : this.allLogics) {
            logic.getDrops(drops);
        }
    }

    @Override
    public void getNoDrops(final World w, final BlockPos pos, final List<ItemStack> drops) {
        // During wrench dismantling, upgrades are saved to NBT but stored items must still drop
        for (IInterfaceLogic logic : this.allLogics) {
            logic.getStorageDrops(drops);
        }
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

        // Re-scan the capability cache for all logics now that all TEs are in the
        // world and the grid proxy is ready. During readFromNBT, adjacent TEs may
        // not have been loaded yet, leaving the push/pull card's cache empty.
        for (IInterfaceLogic logic : this.allLogics) {
            logic.onGridReady();
        }
    }

    // ============================== Grid events ==============================

    @Override
    public void gridChanged() {
        for (IInterfaceLogic logic : this.allLogics) {
            logic.wakeUpIfAdaptive();
        }
    }

    @MENetworkEventSubscribe
    public void powerStatusChanged(final MENetworkPowerStatusChange event) {
        for (IInterfaceLogic logic : this.allLogics) {
            logic.wakeUpIfAdaptive();
        }
    }

    @MENetworkEventSubscribe
    public void channelsChanged(final MENetworkChannelsChanged event) {
        for (IInterfaceLogic logic : this.allLogics) {
            logic.wakeUpIfAdaptive();
        }
    }

    // ============================== IGridTickable ==============================

    @Override
    @Nonnull
    public TickingRequest getTickingRequest(@Nonnull final IGridNode node) {
        // Use the most aggressive ticking request from all logics.
        // Since they share the same upgrade inventory, they should produce the same request,
        // but we take the minimum of each parameter to be safe.
        TickingRequest primary = this.itemLogic.getTickingRequest();
        int minTickRate = primary.minTickRate;
        int maxTickRate = primary.maxTickRate;

        for (int i = 1; i < this.allLogics.size(); i++) {
            TickingRequest req = this.allLogics.get(i).getTickingRequest();
            minTickRate = Math.min(minTickRate, req.minTickRate);
            maxTickRate = Math.min(maxTickRate, req.maxTickRate);
        }

        return new TickingRequest(minTickRate, maxTickRate, primary.isSleeping, true);
    }

    @Override
    @Nonnull
    public TickRateModulation tickingRequest(@Nonnull final IGridNode node, final int ticksSinceLastCall) {
        // Tick all logics and return the most aggressive modulation
        TickRateModulation result = TickRateModulation.IDLE;

        for (IInterfaceLogic logic : this.allLogics) {
            TickRateModulation mod = logic.onTick(ticksSinceLastCall);
            if (mod.ordinal() > result.ordinal()) result = mod;
        }

        return result;
    }

    // ============================== Capabilities ==============================

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        // Item capabilities (always available)
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return true;
        if (capability == Capabilities.ITEM_REPOSITORY_CAPABILITY) return true;

        // Fluid capability (always available)
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) return true;

        // Gas capability (only when MekanismEnergistics is loaded)
        if (MekanismEnergisticsIntegration.isModLoaded() && this.gasLogic != null
                && CombinedInterfaceGasHelper.hasGasCapability(capability)) {
            return true;
        }

        return super.hasCapability(capability, facing);
    }

    @Override
    @Nullable
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        // Item capabilities
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(this.itemLogic.getExternalHandler());
        }
        if (capability == Capabilities.ITEM_REPOSITORY_CAPABILITY) {
            return Capabilities.ITEM_REPOSITORY_CAPABILITY.cast(this.itemLogic.getItemRepository());
        }

        // Fluid capability
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(this.fluidLogic.getExternalHandler());
        }

        // Gas capability
        if (MekanismEnergisticsIntegration.isModLoaded() && this.gasLogic != null) {
            T gasResult = CombinedInterfaceGasHelper.getGasCapability(
                (com.cells.integration.mekanismenergistics.GasInterfaceLogic) this.gasLogic, capability);
            if (gasResult != null) return gasResult;
        }

        return super.getCapability(capability, facing);
    }

    // ============================== Neighbor change ==============================

    /**
     * Handle neighbor block changes for all logics' auto-pull/push capability caches.
     */
    public void onNeighborChanged(BlockPos fromPos) {
        for (IInterfaceLogic logic : this.allLogics) {
            if (logic instanceof AbstractResourceInterfaceLogic) {
                ((AbstractResourceInterfaceLogic<?, ?, ?>) logic).onNeighborChanged(fromPos);
            }
        }
    }

    // ============================== Utility ==============================

    @Nonnull
    public IInterfaceLogic getInterfaceLogic() {
        // Return the primary (item) logic as the default
        return this.itemLogic;
    }

    /**
     * Get the logic for the active tab. Returns the appropriate typed logic
     * for container/GUI operations that depend on the current tab.
     */
    public IInterfaceLogic getActiveLogic() {
        IInterfaceLogic logic = getLogicForType(this.activeTab);
        return logic != null ? logic : this.itemLogic;
    }
}
