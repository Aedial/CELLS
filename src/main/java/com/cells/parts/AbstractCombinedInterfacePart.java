package com.cells.parts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartModel;
import appeng.api.parts.PartItemStack;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.capabilities.Capabilities;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.MachineSource;
import appeng.parts.PartBasicState;
import appeng.parts.PartModel;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.SettingsFrom;
import appeng.util.inv.InvOperation;

import com.cells.api.IInterfaceHost;
import com.cells.api.IInterfaceProvider;
import com.cells.api.IUpgradeable;
import com.cells.blocks.combinedinterface.ICombinedInterfaceHost;
import com.cells.blocks.interfacebase.AbstractResourceInterfaceLogic;
import com.cells.blocks.interfacebase.IInterfaceLogic;
import com.cells.blocks.interfacebase.fluid.FluidInterfaceLogic;
import com.cells.blocks.interfacebase.item.ItemInterfaceLogic;
import com.cells.gui.CellsGuiHandler;
import com.cells.helpers.InterfaceMemoryCardHelper;
import com.cells.helpers.InterfaceApiHelper;
import com.cells.integration.mekanismenergistics.CombinedInterfaceGasHelper;
import com.cells.integration.mekanismenergistics.MekanismEnergisticsIntegration;
import com.cells.integration.thaumicenergistics.CombinedInterfaceEssentiaHelper;
import com.cells.integration.thaumicenergistics.ThaumicEnergisticsIntegration;
import com.cells.network.sync.ResourceType;


/**
 * Abstract base class for combined interface parts (import/export).
 * <p>
 * Manages multiple resource logics (item, fluid, gas, essentia) in a single part,
 * analogous to {@link com.cells.blocks.combinedinterface.AbstractCombinedInterfaceTile}
 * but for cable bus parts.
 */
public abstract class AbstractCombinedInterfacePart extends PartBasicState
        implements IGridTickable, ICombinedInterfaceHost,
           ItemInterfaceLogic.Host, FluidInterfaceLogic.Host,
           IInterfaceProvider, IUpgradeable {

    protected final IActionSource actionSource;

    protected final ItemInterfaceLogic itemLogic;
    protected final FluidInterfaceLogic fluidLogic;
    @Nullable
    protected final IInterfaceLogic gasLogic;
    @Nullable
    protected final IInterfaceLogic essentiaLogic;

    private final List<IInterfaceLogic> allLogics;
    private final List<ResourceType> availableTabs;
    private ResourceType activeTab = ResourceType.ITEM;

    private long lastSaveTick = -1;

    protected AbstractCombinedInterfacePart(final ItemStack is) {
        super(is);
        this.actionSource = new MachineSource(this);

        // Create item logic (owns the upgrade inventory)
        this.itemLogic = new ItemInterfaceLogic(this);
        AppEngInternalInventory sharedUpgradeInv = this.itemLogic.getUpgradeInventory();

        // Secondary logics share the upgrade inventory
        this.fluidLogic = new FluidInterfaceLogic(this, sharedUpgradeInv);

        IInterfaceLogic gas = null;
        if (MekanismEnergisticsIntegration.isModLoaded()) {
            gas = CombinedInterfaceGasHelper.createGasLogic(this, sharedUpgradeInv);
        }
        this.gasLogic = gas;

        IInterfaceLogic essentia = null;
        if (ThaumicEnergisticsIntegration.isModLoaded()) {
            essentia = CombinedInterfaceEssentiaHelper.createEssentiaLogic(this, sharedUpgradeInv);
        }
        this.essentiaLogic = essentia;

        List<IInterfaceLogic> logics = new ArrayList<>(4);
        List<ResourceType> tabs = new ArrayList<>(4);

        logics.add(this.itemLogic);
        tabs.add(ResourceType.ITEM);
        logics.add(this.fluidLogic);
        tabs.add(ResourceType.FLUID);
        if (this.gasLogic != null) { logics.add(this.gasLogic); tabs.add(ResourceType.GAS); }
        if (this.essentiaLogic != null) { logics.add(this.essentiaLogic); tabs.add(ResourceType.ESSENTIA); }

        this.allLogics = Collections.unmodifiableList(logics);
        this.availableTabs = Collections.unmodifiableList(tabs);
    }

    // ============================== Abstract ==============================

    protected abstract PartModel getModelOff();
    protected abstract PartModel getModelOn();
    protected abstract PartModel getModelHasChannel();

    // ============================== ICombinedInterfaceHost ==============================

    @Override public ItemInterfaceLogic getItemLogic() { return this.itemLogic; }
    @Override public FluidInterfaceLogic getFluidLogic() { return this.fluidLogic; }
    @Override @Nullable public IInterfaceLogic getGasLogic() { return this.gasLogic; }
    @Override @Nullable public IInterfaceLogic getEssentiaLogic() { return this.essentiaLogic; }
    @Override public List<IInterfaceLogic> getAllLogics() { return this.allLogics; }

    @Override
    @Nonnull
    public List<IInterfaceHost> getInterfaceHosts() {
        return InterfaceApiHelper.createInterfaceHosts(this, getTargetFacings());
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
    public ResourceType getActiveTab() { return this.activeTab; }

    @Override
    public void setActiveTab(ResourceType tab) {
        if (this.availableTabs.contains(tab)) this.activeTab = tab;
    }

    @Override
    public List<ResourceType> getAvailableTabs() { return this.availableTabs; }

    // ============================== IInterfaceHost ==============================

    @Override public String getTypeName() { return "combined"; }

    @Override
    public String getGuiTitleLangKey() {
        return String.format("cells.%s_interface.combined.title", this.getDirectionString());
    }

    @Override
    public ItemStack getBackButtonStack() { return this.getItemStack(); }

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

    @Override public AEPartLocation getPartSide() { return this.getSide(); }

    // ============================== AbstractResourceInterfaceLogic.Host ==============================

    @Override public AENetworkProxy getGridProxy() { return this.getProxy(); }
    @Override public IActionSource getActionSource() { return this.actionSource; }
    @Override public abstract boolean isExport();
    @Override public IGridTickable getTickable() { return this; }

    @Override
    public void markDirtyAndSave() {
        TileEntity te = this.getHost().getTile();
        if (te == null) return;
        World w = te.getWorld();
        //noinspection ConstantValue
        if (w == null || w.isRemote) return;

        long currentTick = w.getTotalWorldTime();
        if (this.lastSaveTick == currentTick) return;
        this.lastSaveTick = currentTick;
        w.markChunkDirty(te.getPos(), te);
    }

    @Override @Nullable
    public World getHostWorld() {
        TileEntity te = this.getHost().getTile();
        return te != null ? te.getWorld() : null;
    }

    @Override
    public BlockPos getHostPos() { return this.getHost().getLocation().getPos(); }

    // ============================== Part basics ==============================

    @Override @Nonnull
    public IPartModel getStaticModels() {
        if (this.isActive() && this.isPowered()) return getModelHasChannel();
        if (this.isPowered()) return getModelOn();
        return getModelOff();
    }

    @Override
    public void getBoxes(final IPartCollisionHelper bch) {
        bch.addBox(2, 2, 14, 14, 14, 16);
        bch.addBox(5, 5, 12, 11, 11, 14);
    }

    @Override
    public float getCableConnectionLength(AECableType cable) { return 4; }

    // ============================== Grid lifecycle ==============================

    @Override
    public void addToWorld() {
        super.addToWorld();

        // Re-scan the capability cache for all logics now that all TEs are in the
        // world and the grid proxy is ready. During readFromNBT, adjacent TEs may
        // not have been loaded yet, leaving the push/pull card's cache empty.
        for (IInterfaceLogic logic : this.allLogics) {
            logic.onGridReady();
        }
    }

    // ============================== Network events ==============================

    @MENetworkEventSubscribe
    public void chanRender(final MENetworkChannelsChanged c) {
        this.getHost().markForUpdate();
        for (IInterfaceLogic logic : this.allLogics) logic.wakeUpIfAdaptive();
    }

    @MENetworkEventSubscribe
    public void powerRender(final MENetworkPowerStatusChange c) {
        this.getHost().markForUpdate();
        for (IInterfaceLogic logic : this.allLogics) logic.wakeUpIfAdaptive();
    }

    @Override
    public void gridChanged() {
        for (IInterfaceLogic logic : this.allLogics) logic.wakeUpIfAdaptive();
    }

    // ============================== NBT / settings / stream ==============================

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        for (IInterfaceLogic logic : this.allLogics) logic.readFromNBT(data, false);
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        for (IInterfaceLogic logic : this.allLogics) logic.writeToNBT(data);
    }

    @Nonnull
    @Override
    public NBTTagCompound downloadSettings(SettingsFrom from) {
        NBTTagCompound output = super.downloadSettings(from);
        if (output == null) output = new NBTTagCompound();

        for (IInterfaceLogic logic : this.allLogics) {
            NBTTagCompound logicSettings = (from == SettingsFrom.DISMANTLE_ITEM)
                ? logic.downloadSettingsForDismantle() : logic.downloadSettings();
            output.merge(logicSettings);
        }
        return output;
    }

    @Override
    public NBTTagCompound downloadSettingsWithFilter() {
        NBTTagCompound output = new NBTTagCompound();
        for (IInterfaceLogic logic : this.allLogics) output.merge(logic.downloadSettingsWithFilter());
        return output;
    }

    @Override
    public void uploadSettings(SettingsFrom from, NBTTagCompound compound, EntityPlayer player) {
        super.uploadSettings(from, compound, player);
        for (IInterfaceLogic logic : this.allLogics) logic.uploadSettings(compound, player);
    }

    @Override
    public ItemStack getItemStack(PartItemStack type) {
        ItemStack stack = super.getItemStack(type);
        if (type == PartItemStack.WRENCH) {
            NBTTagCompound tag = this.downloadSettings(SettingsFrom.DISMANTLE_ITEM);
            if (!tag.isEmpty()) stack.setTagCompound(tag);
        }
        return stack;
    }

    @Override
    public void onPlacement(final EntityPlayer player, final EnumHand hand, final ItemStack held, final AEPartLocation side) {
        super.onPlacement(player, hand, held, side);
        if (held.hasTagCompound()) {
            this.uploadSettings(SettingsFrom.DISMANTLE_ITEM, held.getTagCompound(), player);
        }
    }

    // ============================== Memory card ==============================

    @Override
    public boolean useStandardMemoryCard() { return false; }

    protected boolean useMemoryCard(final EntityPlayer player) {
        final ItemStack memCardIS = player.inventory.getCurrentItem();
        if (memCardIS.isEmpty()) return false;
        if (!(memCardIS.getItem() instanceof IMemoryCard)) return false;

        final IMemoryCard memoryCard = (IMemoryCard) memCardIS.getItem();
        final String name = "tile.cells." + (this.isExport() ? "export" : "import") + "_interface.combined";

        if (player.isSneaking()) {
            final NBTTagCompound data = this.downloadSettings(SettingsFrom.MEMORY_CARD);
            if (!data.isEmpty()) {
                memoryCard.setMemoryCardContents(memCardIS, name, data);
                memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_SAVED);
            }
        } else {
            final NBTTagCompound data = InterfaceMemoryCardHelper.prepareUploadData(
                memoryCard.getSettingsName(memCardIS),
                memoryCard.getData(memCardIS),
                InterfaceMemoryCardHelper.combined(this.isExport())
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

    // ============================== GUI ==============================

    @Override
    public boolean onPartActivate(final EntityPlayer p, final EnumHand hand, final Vec3d pos) {
        if (!p.isSneaking() && this.useMemoryCard(p)) return true;
        if (p.isSneaking()) return false;

        if (!p.world.isRemote) {
            CellsGuiHandler.openPartGui(p, this.getHost().getTile(), this.getSide(), getMainGuiId());
        }
        return true;
    }

    @Override
    public boolean onPartShiftActivate(final EntityPlayer p, final EnumHand hand, final Vec3d pos) {
        return this.useMemoryCard(p);
    }

    // ============================== Drops ==============================

    @Override
    public void getDrops(final List<ItemStack> drops, final boolean wrenched) {
        if (wrenched) {
            for (IInterfaceLogic logic : this.allLogics) logic.getStorageDrops(drops);
        } else {
            for (IInterfaceLogic logic : this.allLogics) logic.getDrops(drops);
        }
    }

    // ============================== Neighbor ==============================

    @Override
    public void onNeighborChanged(IBlockAccess w, BlockPos pos, BlockPos neighbor) {
        for (IInterfaceLogic logic : this.allLogics) {
            if (logic instanceof AbstractResourceInterfaceLogic) {
                ((AbstractResourceInterfaceLogic<?, ?, ?>) logic).onNeighborChanged(neighbor);
            }
        }
    }

    // ============================== IAEAppEngInventory ==============================

    @Override
    public void saveChanges() { this.markDirtyAndSave(); }

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removed, ItemStack added) {
        for (IInterfaceLogic logic : this.allLogics) {
            logic.onChangeInventory(inv, slot, removed, added);
        }
        this.getHost().markForUpdate();
    }

    // ============================== IGridTickable ==============================

    @Override @Nonnull
    public TickingRequest getTickingRequest(@Nonnull final IGridNode node) {
        TickingRequest primary = this.itemLogic.getTickingRequest();
        int minTick = primary.minTickRate;
        int maxTick = primary.maxTickRate;

        for (int i = 1; i < this.allLogics.size(); i++) {
            TickingRequest req = this.allLogics.get(i).getTickingRequest();
            minTick = Math.min(minTick, req.minTickRate);
            maxTick = Math.min(maxTick, req.maxTickRate);
        }

        return new TickingRequest(minTick, maxTick, primary.isSleeping, true);
    }

    @Override @Nonnull
    public TickRateModulation tickingRequest(@Nonnull final IGridNode node, final int ticksSinceLastCall) {
        TickRateModulation result = TickRateModulation.IDLE;
        for (IInterfaceLogic logic : this.allLogics) {
            TickRateModulation mod = logic.onTick(ticksSinceLastCall);
            if (mod.ordinal() > result.ordinal()) result = mod;
        }
        return result;
    }

    // ============================== Capabilities ==============================

    @Override
    public boolean hasCapability(Capability<?> capability) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return true;
        if (Capabilities.ITEM_REPOSITORY_CAPABILITY != null
                && capability == Capabilities.ITEM_REPOSITORY_CAPABILITY) return true;
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) return true;
        if (MekanismEnergisticsIntegration.isModLoaded()
                && CombinedInterfaceGasHelper.hasGasCapability(capability)) return true;
        return super.hasCapability(capability);
    }

    @Override
    public <T> T getCapability(Capability<T> capability) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(this.itemLogic.getExternalHandler());
        }
        if (Capabilities.ITEM_REPOSITORY_CAPABILITY != null
                && capability == Capabilities.ITEM_REPOSITORY_CAPABILITY) {
            return Capabilities.ITEM_REPOSITORY_CAPABILITY.cast(this.itemLogic.getItemRepository());
        }
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(this.fluidLogic.getExternalHandler());
        }
        if (this.gasLogic != null && MekanismEnergisticsIntegration.isModLoaded()) {
            T gasCap = CombinedInterfaceGasHelper.getGasCapability(
                (com.cells.integration.mekanismenergistics.GasInterfaceLogic) this.gasLogic, capability);
            if (gasCap != null) return gasCap;
        }
        return super.getCapability(capability);
    }

    // ============================== Helper ==============================

    public TileEntity getTileEntity() {
        return this.getHost().getTile();
    }

    /**
     * Parts only interact with the block on their attached side,
     * unlike full-block tiles which interact with all 6 adjacent blocks.
     */
    public EnumSet<EnumFacing> getTargetFacings() {
        return EnumSet.of(this.getSide().getFacing());
    }
}
