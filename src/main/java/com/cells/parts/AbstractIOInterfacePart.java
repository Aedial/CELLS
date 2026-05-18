package com.cells.parts;

import java.util.Arrays;
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
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.MachineSource;
import appeng.parts.PartBasicState;
import appeng.parts.PartModel;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.SettingsFrom;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;

import com.cells.api.IInterfaceProvider;
import com.cells.blocks.interfacebase.AbstractResourceInterfaceLogic;
import com.cells.blocks.interfacebase.IInterfaceLogic;
import com.cells.blocks.iointerface.IIOInterfaceHost;
import com.cells.gui.CellsGuiHandler;
import com.cells.helpers.InterfaceMemoryCardHelper;
import com.cells.helpers.InterfaceApiHelper;


/**
 * Abstract base class for IO (Import+Export) interface cable parts.
 * <p>
 * Manages two logic instances of the same resource type (one import, one export)
 * in a single cable bus part. Parallels
 * {@link com.cells.blocks.iointerface.AbstractIOInterfaceTile} for the block version.
 * <p>
 * Each direction has its own upgrade inventory, filters, storage, and max slot size.
 * Only the polling rate is shared between directions.
 * <p>
 * Subclasses must:
 * <ul>
 *   <li>Create both logic instances in constructor and call {@link #initLogics}</li>
 *   <li>Implement {@link #getResourceType()}</li>
 *   <li>Implement {@link #getMainGuiId()}</li>
 *   <li>Provide model and capability implementations</li>
 * </ul>
 *
 * @param <L> The logic class type (ItemInterfaceLogic, FluidInterfaceLogic, etc.)
 */
public abstract class AbstractIOInterfacePart<L extends IInterfaceLogic> extends PartBasicState
    implements IGridTickable, IIOInterfaceHost, IAEAppEngInventory, IInterfaceProvider {

    protected final IActionSource actionSource;

    /** Import logic (direction = import, isExport = false). */
    protected L importLogic;

    /** Export logic (direction = export, isExport = true). */
    protected L exportLogic;

    /** Both logics for iteration. Immutable after {@link #initLogics}. */
    private List<IInterfaceLogic> allLogicsList;

    /** Active direction tab. Not saved to NBT (client-side transient). */
    private int activeDirectionTab = IIOInterfaceHost.TAB_IMPORT;

    // Debounce: markChunkDirty at most once per tick
    private long lastSaveTick = -1;

    protected AbstractIOInterfacePart(final ItemStack is) {
        super(is);
        this.actionSource = new MachineSource(this);
    }

    /**
     * Initialize both logics. Must be called by subclass constructor.
     */
    protected void initLogics(L importLogic, L exportLogic) {
        this.importLogic = importLogic;
        this.exportLogic = exportLogic;
        this.allLogicsList = Collections.unmodifiableList(
            Arrays.asList((IInterfaceLogic) importLogic, (IInterfaceLogic) exportLogic)
        );
    }

    // ============================== Abstract methods ==============================

    protected abstract PartModel getModelOff();
    protected abstract PartModel getModelOn();
    protected abstract PartModel getModelHasChannel();

    // ============================== Direction Host Wrapper ==============================

    /**
     * Wrapper that delegates all Host methods to the part, except {@link #isExport()}
     * which returns a fixed direction. Parallels the inner DirectionHost class in
     * {@link com.cells.blocks.iointerface.AbstractIOInterfaceTile}.
     */
    protected class DirectionHost implements AbstractResourceInterfaceLogic.Host {

        private final boolean export;

        public DirectionHost(boolean export) {
            this.export = export;
        }

        @Override
        public AENetworkProxy getGridProxy() {
            return AbstractIOInterfacePart.this.getProxy();
        }

        @Override
        public IActionSource getActionSource() {
            return AbstractIOInterfacePart.this.actionSource;
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
            AbstractIOInterfacePart.this.doMarkDirtyAndSave();
        }

        @Override
        @Nullable
        public World getHostWorld() {
            TileEntity te = AbstractIOInterfacePart.this.getHost().getTile();
            return te != null ? te.getWorld() : null;
        }

        @Override
        public BlockPos getHostPos() {
            return AbstractIOInterfacePart.this.getHost().getLocation().getPos();
        }

        @Override
        public IGridTickable getTickable() {
            return AbstractIOInterfacePart.this;
        }

        @Override
        public EnumSet<EnumFacing> getTargetFacings() {
            return EnumSet.of(AbstractIOInterfacePart.this.getSide().getFacing());
        }

        // IAEAppEngInventory

        @Override
        public void saveChanges() {
            AbstractIOInterfacePart.this.saveChanges();
        }

        @Override
        public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc,
                                      ItemStack removed, ItemStack added) {
            AbstractIOInterfacePart.this.onChangeInventory(inv, slot, mc, removed, added);
        }
    }

    // ============================== IInterfaceProvider ==============================

    /**
     * Expose this IO part's two logical directions (import + export) to the CELLS
     * public API so the Disk Terminal can scan and display them.
     * The part's single cable-bus facing is used as the target facing for both hosts.
     */
    @Nonnull
    @Override
    public List<com.cells.api.IInterfaceHost> getInterfaceHosts() {
        return InterfaceApiHelper.createInterfaceHosts(this, EnumSet.of(this.getSide().getFacing()));
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
        TileEntity te = this.getHost().getTile();
        return te != null ? te.getWorld() : null;
    }

    @Override
    public BlockPos getHostPos() {
        return this.getHost().getLocation().getPos();
    }

    public IGridTickable getTickable() {
        return this;
    }

    @Override
    public String getTypeName() {
        return getResourceType().name().toLowerCase();
    }

    @Override
    public String getGuiTitleLangKey() {
        return String.format("cells.%s_interface.%s.title",
            this.getDirectionString(), this.getTypeName());
    }

    @Override
    public ItemStack getBackButtonStack() {
        return this.getItemStack();
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

    @Override
    public AEPartLocation getPartSide() {
        return this.getSide();
    }

    // ============================== Part model and rendering ==============================

    @Override
    @Nonnull
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
    public float getCableConnectionLength(AECableType cable) {
        return 4;
    }

    // ============================== Grid lifecycle ==============================

    @Override
    public void addToWorld() {
        super.addToWorld();
        for (IInterfaceLogic logic : this.allLogicsList) logic.onGridReady();
    }

    // ============================== Network events ==============================

    @MENetworkEventSubscribe
    public void chanRender(final MENetworkChannelsChanged c) {
        this.getHost().markForUpdate();
        for (IInterfaceLogic logic : this.allLogicsList) logic.wakeUpIfAdaptive();
    }

    @MENetworkEventSubscribe
    public void powerRender(final MENetworkPowerStatusChange c) {
        this.getHost().markForUpdate();
        for (IInterfaceLogic logic : this.allLogicsList) logic.wakeUpIfAdaptive();
    }

    @Override
    public void gridChanged() {
        for (IInterfaceLogic logic : this.allLogicsList) logic.wakeUpIfAdaptive();
    }

    // ============================== NBT serialization ==============================

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        // Each logic uses direction-prefixed keys to avoid collisions.
        // Parts always use isTile=false for legacy compat.
        this.importLogic.readFromNBT(data, false);
        this.exportLogic.readFromNBT(data, false);
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        this.importLogic.writeToNBT(data);
        this.exportLogic.writeToNBT(data);
    }

    @Nonnull
    @Override
    public NBTTagCompound downloadSettings(SettingsFrom from) {
        NBTTagCompound output = super.downloadSettings(from);
        if (output == null) output = new NBTTagCompound();

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
    public void onPlacement(final EntityPlayer player, final EnumHand hand,
                            final ItemStack held, final AEPartLocation side) {
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
        final String name = "tile.cells.io_interface." + this.getTypeName();

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
                InterfaceMemoryCardHelper.io(this.getTypeName())
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
            for (IInterfaceLogic logic : this.allLogicsList) logic.getStorageDrops(drops);
        } else {
            for (IInterfaceLogic logic : this.allLogicsList) logic.getDrops(drops);
        }
    }

    // ============================== Neighbor ==============================

    @Override
    public void onNeighborChanged(IBlockAccess w, BlockPos pos, BlockPos neighbor) {
        for (IInterfaceLogic logic : this.allLogicsList) {
            if (logic instanceof AbstractResourceInterfaceLogic) {
                ((AbstractResourceInterfaceLogic<?, ?, ?>) logic).onNeighborChanged(neighbor);
            }
        }
    }

    // ============================== IAEAppEngInventory ==============================

    @Override
    public void saveChanges() { this.doMarkDirtyAndSave(); }

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc,
                                  ItemStack removed, ItemStack added) {
        for (IInterfaceLogic logic : this.allLogicsList) {
            logic.onChangeInventory(inv, slot, removed, added);
        }
        this.getHost().markForUpdate();
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

    // ============================== Helper ==============================

    private void doMarkDirtyAndSave() {
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

    public TileEntity getTileEntity() {
        return this.getHost().getTile();
    }

    /**
     * Parts only interact with the block on their attached side.
     */
    public EnumSet<EnumFacing> getTargetFacings() {
        return EnumSet.of(this.getSide().getFacing());
    }
}
