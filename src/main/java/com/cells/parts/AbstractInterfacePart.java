package com.cells.parts;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

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
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.MachineSource;
import appeng.parts.PartBasicState;
import appeng.parts.PartModel;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.SettingsFrom;
import appeng.util.inv.InvOperation;

import net.minecraftforge.items.IItemHandler;

import com.cells.blocks.interfacebase.AbstractResourceInterfaceLogic;
import com.cells.blocks.interfacebase.IInterfaceHost;
import com.cells.blocks.interfacebase.IInterfaceLogic;
import com.cells.gui.CellsGuiHandler;


/**
 * Abstract base class for all interface parts (import/export, item/fluid/gas).
 * <p>
 * This class extracts common functionality from all Part*Interface classes:
 * <ul>
 *   <li>Host callbacks (markDirty, getHostWorld, etc.)</li>
 *   <li>Part model rendering (3-state model: off/on/has_channel)</li>
 *   <li>Network event handling</li>
 *   <li>NBT serialization framework</li>
 *   <li>Memory card compatibility</li>
 *   <li>Grid ticking delegation</li>
 * </ul>
 * <p>
 * Subclasses only need to:
 * <ul>
 *   <li>Define their logic class and model paths</li>
 *   <li>Implement host interface methods (filter access, etc.)</li>
 *   <li>Provide capability handlers</li>
 *   <li>Define GUI IDs and memory card names</li>
 * </ul>
 *
 * @param <L> The logic class type (ItemInterfaceLogic, FluidInterfaceLogic, etc.)
 */
public abstract class AbstractInterfacePart<L extends IInterfaceLogic> extends PartBasicState
        implements IGridTickable, IInterfaceHost, AbstractResourceInterfaceLogic.Host {

    protected final IActionSource actionSource;
    protected L logic;

    // Debounce: getTotalWorldTime is two field reads + a long compare,
    // markChunkDirty is two chunk map lookups + a boolean write.
    private long lastSaveTick = -1;

    protected AbstractInterfacePart(final ItemStack is) {
        super(is);
        this.actionSource = new MachineSource(this);
        // Logic is created by subclass after super() call via createLogic()
    }

    /**
     * Create and set the logic instance.
     * Must be called by subclass constructor after super() call.
     *
     * @param logic The logic instance (created with 'this' as host)
     */
    protected void setLogic(L logic) {
        this.logic = logic;
    }

    // ============================== Abstract methods ==============================

    /**
     * Get the off-state model for this part.
     */
    protected abstract PartModel getModelOff();

    /**
     * Get the on-state model for this part.
     */
    protected abstract PartModel getModelOn();

    /**
     * Get the has-channel-state model for this part.
     */
    protected abstract PartModel getModelHasChannel();

    /**
     * Get the memory card translation key for this part type.
     * E.g., "tile.cells.import_interface" or "tile.cells.export_interface"
     */
    protected abstract String getMemoryCardName();

    // ============================== IInterfaceHost.Host callbacks ==============================

    /**
     * Get the network proxy. Subclasses delegate to their logic's host interface.
     */
    public AENetworkProxy getGridProxy() {
        return this.getProxy();
    }

    /**
     * Get the action source for ME network operations.
     */
    public IActionSource getActionSource() {
        return this.actionSource;
    }

    @Override
    public void markDirtyAndSave() {
        TileEntity te = this.getHost().getTile();
        if (te == null) return;

        World w = te.getWorld();
        if (w == null || w.isRemote) return;

        // Debounce to once per tick
        long currentTick = w.getTotalWorldTime();
        if (this.lastSaveTick == currentTick) return;
        this.lastSaveTick = currentTick;

        // Call markChunkDirty directly on the host tile to flag the chunk for saving.
        // We intentionally skip markForSave() / saveChanges() to bypass the bloat.
        w.markChunkDirty(te.getPos(), te);
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

    /**
     * Get this part as an IGridTickable for scheduling.
     */
    @Override
    public IGridTickable getTickable() {
        return this;
    }

    @Override
    public abstract boolean isExport();

    // ============================== IAEAppEngInventory implementation ==============================

    @Override
    public void saveChanges() {
        this.markDirtyAndSave();
    }

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removed, ItemStack added) {
        if (inv == this.logic.getUpgradeInventory()) {
            this.logic.onUpgradeChanged();
        }
        this.getHost().markForUpdate();
    }

    // ============================== IInterfaceHost implementation ==============================

    @Nonnull
    public L getInterfaceLogic() {
        return this.logic;
    }

    @Override
    public String getTypeName() {
        return this.logic.getTypeName();
    }

    @Override
    public AEPartLocation getPartSide() {
        return this.getSide();
    }

    // ============================== Common delegation methods ==============================
    // These methods are shared by all interface parts (Item, Fluid, Gas).
    // They satisfy the interface contracts of IItemInterfaceHost, IFluidInterfaceHost, IGasInterfaceHost.

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
    public long getMaxSlotSize() {
        return this.logic.getMaxSlotSize();
    }

    @Override
    public void setMaxSlotSize(long size) {
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

    @Override
    public ItemStack getBackButtonStack() {
        return this.getItemStack();
    }

    // ============================== Part model and rendering ==============================

    @Override
    @Nonnull
    public IPartModel getStaticModels() {
        if (this.isActive() && this.isPowered()) {
            return getModelHasChannel();
        } else if (this.isPowered()) {
            return getModelOn();
        }
        return getModelOff();
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

    @MENetworkEventSubscribe
    public void chanRender(final MENetworkChannelsChanged c) {
        this.getHost().markForUpdate();
        this.logic.wakeUpIfAdaptive();
    }

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
        // Use isTile=false for parts; ItemInterfaceLogic overrides for legacy support
        this.logic.readFromNBT(data, false);
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

    @Override
    public void uploadSettings(SettingsFrom from, NBTTagCompound compound, EntityPlayer player) {
        super.uploadSettings(from, compound, player);
        this.logic.uploadSettings(compound, player);
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

    // ============================== Stream sync ==============================

    @Override
    public boolean readFromStream(final ByteBuf data) throws IOException {
        boolean result = super.readFromStream(data);
        result |= this.logic.readStorageFromStream(data);
        result |= this.logic.readFiltersFromStream(data);
        return result;
    }

    @Override
    public void writeToStream(final ByteBuf data) throws IOException {
        super.writeToStream(data);
        this.logic.writeStorageToStream(data);
        this.logic.writeFiltersToStream(data);
    }

    @Override
    public void onPlacement(final EntityPlayer player, final EnumHand hand, final ItemStack held, final AEPartLocation side) {
        super.onPlacement(player, hand, held, side);
        if (held.hasTagCompound()) {
            this.uploadSettings(SettingsFrom.DISMANTLE_ITEM, held.getTagCompound(), player);
        }
    }

    // ============================== Memory card handling ==============================

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
     *
     * @return true if memory card was used
     */
    protected boolean useMemoryCard(final EntityPlayer player) {
        final ItemStack memCardIS = player.inventory.getCurrentItem();
        if (memCardIS.isEmpty()) return false;
        if (!(memCardIS.getItem() instanceof IMemoryCard)) return false;

        final IMemoryCard memoryCard = (IMemoryCard) memCardIS.getItem();
        final String name = getMemoryCardName();

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

    // ============================== GUI handling ==============================

    @Override
    public boolean onPartActivate(final EntityPlayer p, final EnumHand hand, final Vec3d pos) {
        // Handle memory card (right-click to load settings)
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
            // Upgrades are saved to NBT via downloadSettings(DISMANTLE_ITEM),
            // but stored items must still drop
            this.logic.getStorageDrops(drops);
        } else {
            this.logic.getDrops(drops);
        }
    }

    // ============================== Neighbor change delegation ==============================

    @Override
    public void onNeighborChanged(IBlockAccess w, BlockPos pos, BlockPos neighbor) {
        if (this.logic instanceof AbstractResourceInterfaceLogic) {
            ((AbstractResourceInterfaceLogic<?, ?, ?>) this.logic).onNeighborChanged(neighbor);
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
        return this.logic.onTick(ticksSinceLastCall);
    }

    // ============================== Utility methods ==============================

    public TileEntity getTileEntity() {
        return this.getHost().getTile();
    }

    public EnumSet<EnumFacing> getTargets() {
        return EnumSet.of(this.getSide().getFacing());
    }
}
