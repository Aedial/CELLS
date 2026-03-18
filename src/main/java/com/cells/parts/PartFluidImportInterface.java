package com.cells.parts;

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
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
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
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.fluids.util.IAEFluidInventory;
import appeng.fluids.util.IAEFluidTank;
import appeng.items.parts.PartModels;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.MachineSource;
import appeng.parts.PartBasicState;
import appeng.parts.PartModel;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.SettingsFrom;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;

import com.cells.Tags;
import com.cells.blocks.interfacebase.FluidInterfaceLogic;
import com.cells.blocks.interfacebase.IFluidInterfaceHost;
import com.cells.gui.CellsGuiHandler;


/**
 * Part version of the Fluid Import Interface.
 * Can be placed on cables and behaves identically to the block version.
 * <p>
 * Business logic is delegated to {@link FluidInterfaceLogic} to avoid code
 * duplication with tile and export variants.
 */
public class PartFluidImportInterface extends PartBasicState implements IGridTickable, IAEAppEngInventory, IAEFluidInventory, IFluidInterfaceHost, FluidInterfaceLogic.Host {

    public static final ResourceLocation MODEL_BASE = new ResourceLocation(Tags.MODID, "part/import_fluid_interface_base");

    @PartModels
    public static final PartModel MODELS_OFF = new PartModel(MODEL_BASE, new ResourceLocation(Tags.MODID, "part/import_fluid_interface_off"));

    @PartModels
    public static final PartModel MODELS_ON = new PartModel(MODEL_BASE, new ResourceLocation(Tags.MODID, "part/import_fluid_interface_on"));

    @PartModels
    public static final PartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, new ResourceLocation(Tags.MODID, "part/import_fluid_interface_has_channel"));

    private final FluidInterfaceLogic logic;
    private final IActionSource actionSource;

    public PartFluidImportInterface(final ItemStack is) {
        super(is);
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
        this.getHost().markForSave();
    }

    @Override
    public void markForNetworkUpdate() {
        this.getHost().markForUpdate();
    }

    @Override
    @Nullable
    public World getHostWorld() {
        TileEntity te = this.getHost().getTile();
        return te != null ? te.getWorld() : null;
    }

    @Override
    public BlockPos getHostPos() {
        return this.getHost().getLocation().getPos();
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

    // ============================== Part model and rendering ==============================

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

    // ============================== Network events ==============================

    @Override
    @MENetworkEventSubscribe
    public void chanRender(final MENetworkChannelsChanged c) {
        this.getHost().markForUpdate();
        this.logic.wakeUpIfAdaptive();
    }

    @Override
    @MENetworkEventSubscribe
    public void powerRender(final MENetworkPowerStatusChange c) {
        this.getHost().markForUpdate();
        this.logic.wakeUpIfAdaptive();
    }

    @Override
    public void gridChanged() {
        this.logic.wakeUpIfAdaptive();
    }

    // ============================== NBT serialization ==============================

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.logic.readFromNBT(data);
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        this.logic.writeToNBT(data);
    }

    @Nonnull
    @Override
    public NBTTagCompound downloadSettings(SettingsFrom from) {
        NBTTagCompound output = super.downloadSettings(from);
        if (output == null) output = new NBTTagCompound();

        NBTTagCompound logicSettings = (from == SettingsFrom.DISMANTLE_ITEM)
            ? this.logic.downloadSettingsForDismantle()
            : this.logic.downloadSettings();

        output.merge(logicSettings);
        return output;
    }

    @Override
    public NBTTagCompound downloadSettingsWithFilter() {
        return this.logic.downloadSettingsWithFilter();
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
        this.logic.uploadSettings(compound, player);
    }

    @Override
    public void onPlacement(final EntityPlayer player, final EnumHand hand, final ItemStack held, final AEPartLocation side) {
        super.onPlacement(player, hand, held, side);
        if (held.hasTagCompound()) {
            this.uploadSettings(SettingsFrom.DISMANTLE_ITEM, held.getTagCompound(), player);
        }
    }

    // ============================== GUI handling ==============================

    @Override
    public boolean onPartActivate(final EntityPlayer p, final EnumHand hand, final Vec3d pos) {
        if (!p.isSneaking() && this.useMemoryCard(p)) return true;
        if (p.isSneaking()) return false;

        if (!p.world.isRemote) {
            CellsGuiHandler.openPartGui(p, this.getHost().getTile(), this.getSide(), CellsGuiHandler.GUI_PART_FLUID_IMPORT_INTERFACE);
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
            // Upgrades are saved to NBT via downloadSettings(DISMANTLE_ITEM),
            // but stored items must still drop
            this.logic.getStorageDrops(drops);
        } else {
            this.logic.getDrops(drops);
        }
    }

    public EnumSet<EnumFacing> getTargets() {
        return EnumSet.of(this.getSide().getFacing());
    }

    public TileEntity getTileEntity() {
        return this.getHost().getTile();
    }

    // ============================== IAEAppEngInventory ==============================

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removed, ItemStack added) {
        if (inv == this.logic.getUpgradeInventory()) {
            this.logic.onUpgradeChanged();
        }
        this.getHost().markForUpdate();
    }

    // ============================== IAEFluidInventory ==============================

    @Override
    public void onFluidInventoryChanged(IAEFluidTank inv, int slot, InvOperation operation, FluidStack added, FluidStack removed) {
        if (inv == this.logic.getFilterInventory()) {
            this.logic.onFluidFilterChanged(slot);
        }
    }

    @Override
    public void onFluidInventoryChanged(IAEFluidTank inv, int slot) {
        if (inv == this.logic.getFilterInventory()) {
            this.logic.onFluidFilterChanged(slot);
        }
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
    public boolean hasCapability(Capability<?> capability) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) return true;
        return super.hasCapability(capability);
    }

    @Override
    public <T> T getCapability(Capability<T> capability) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(this.logic.getExternalHandler());
        }
        return super.getCapability(capability);
    }
}
