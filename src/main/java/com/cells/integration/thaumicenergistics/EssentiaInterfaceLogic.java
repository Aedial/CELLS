package com.cells.integration.thaumicenergistics;

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;

import appeng.api.AEApi;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.storage.IMEInventory;
import appeng.api.util.AEPartLocation;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;

import thaumicenergistics.api.EssentiaStack;
import thaumicenergistics.api.storage.IAEEssentiaStack;
import thaumicenergistics.api.storage.IEssentiaStorageChannel;
import thaumicenergistics.integration.appeng.AEEssentiaStack;
import thaumicenergistics.part.PartEssentiaStorageBus;

import com.cells.blocks.interfacebase.AbstractResourceInterfaceLogic;
import com.cells.items.ItemRecoveryContainer;


/**
 * Essentia-specific implementation of the resource interface logic.
 * Handles essentia import/export interfaces for both tiles and parts.
 * <p>
 * Extends {@link AbstractResourceInterfaceLogic} with EssentiaStack as the resource type,
 * IAEEssentiaStack as the AE2 stack type, and EssentiaStackKey as the key type.
 * <p>
 * Unlike fluid/gas interfaces, essentia uses {@link IAspectContainer} directly
 * on the tile entity rather than a Forge capability. The external handler is
 * exposed through the tile implementing {@link IAspectContainer}.
 */
public class EssentiaInterfaceLogic extends AbstractResourceInterfaceLogic<EssentiaStack, IAEEssentiaStack, EssentiaStackKey> {

    /**
     * Host interface for essentia interfaces.
     */
    public interface Host extends AbstractResourceInterfaceLogic.Host {
    }

    /**
     * Default max slot size for essentia (256 essentia per slot).
     * A standard jar holds 250 essentia, so 256 is a reasonable default.
     */
    public static final int DEFAULT_MAX_SLOT_SIZE = 256;

    /**
     * Default suction for import interface (sink - high suction, wants essentia).
     * The import interface is a SINK that accepts essentia FROM the tube network
     * TO store in the ME network. This does not mean, however, that tubes will
     * push essentia to us. The tubes network is pull-based, so we'd need to
     * actively pull the essentia.
     */
    public static final int IMPORT_SUCTION = 128;

    /**
     * Default suction for export interface (source - low suction, provides essentia).
     * The export interface is a SOURCE that provides essentia TO the tube network
     * FROM the ME network. Low suction means tubes will pull essentia from us.
     */
    public static final int EXPORT_SUCTION = 0;

    /**
     * Tracks pending essentia changes that need to be notified to connected storage buses.
     * Key is the Aspect, value is the cumulative delta (positive for additions, negative for removals).
     * <p>
     * Changes are accumulated during operations (import/export ticking, tube I/O) and then
     * flushed to the network at the end of each tick or operation via
     * {@link #notifyStorageBusOfChanges()}.
     */
    private final Map<Aspect, Long> pendingChanges = new HashMap<>();

    /**
     * Flag to track whether we have an adjacent essentia storage bus.
     * This is cached to avoid checking all neighbors every tick.
     * Set to true on neighbor change detection, cleared when no bus is found.
     */
    private boolean hasAdjacentStorageBus = false;

    /**
     * Flag indicating that neighbor check is needed (after a neighbor change event).
     */
    private boolean neighborCheckPending = true;

    public long getDefaultMaxSlotSize() {
        return Math.min(DEFAULT_MAX_SLOT_SIZE, getMaxMaxSlotSize());
    }

    public EssentiaInterfaceLogic(Host host) {
        super(host, EssentiaStack.class);
    }

    @Override
    public String getTypeName() {
        return "essentia";
    }

    /**
     * Get essentia in a specific slot.
     */
    @Nullable
    public EssentiaStack getEssentiaInSlot(int slot) {
        return getResourceInSlot(slot);
    }

    /**
     * Set essentia in a specific slot.
     * Used by GUI essentia pouring in import mode.
     */
    public void setEssentiaInSlot(int slot, @Nullable EssentiaStack essentia) {
        if (slot < 0 || slot >= STORAGE_SLOTS) return;

        setResourceInSlot(slot, essentia);
        this.host.markDirtyAndSave();
    }

    /**
     * Insert essentia into a specific slot.
     * @return Amount actually inserted
     */
    public int insertEssentiaIntoSlot(int slot, EssentiaStack essentia) {
        return insertIntoSlot(slot, essentia);
    }

    /**
     * Since Thaumic Energistics has a bug that downgrades requests from
     * Long.MAX_VALUE to Integer.MAX_VALUE, we clamp the request to avoid any voiding.
     * There is no telling if any voiding *is happening*, but it *does* ask
     * Long.MAX_VALUE essentia to our creative cells, while returning only Max Int.
     */
    @Override
    protected long getMaxAENetworkRequestSize() {
        return Integer.MAX_VALUE;
    }

    /**
     * Drain essentia from a specific slot.
     */
    @Nullable
    public EssentiaStack drainEssentiaFromSlot(int slot, int maxDrain, boolean doDrain) {
        return drainFromSlot(slot, maxDrain, doDrain);
    }

    /**
     * Insert essentia into the ME network.
     */
    public int insertEssentiaIntoNetwork(EssentiaStack essentia) {
        return insertIntoNetwork(essentia);
    }

    // ============================== IAspectContainer Support ==============================

    /**
     * Get the AspectList representation of current storage.
     * Used by IAspectContainer.getAspects().
     */
    public AspectList getAspects() {
        AspectList list = new AspectList();

        for (int i = 0; i < STORAGE_SLOTS; i++) {
            EssentiaStack identity = this.storage[i];
            long amount = this.amounts[i];
            if (identity == null || amount <= 0) continue;

            Aspect aspect = identity.getAspect();
            // Clamp to int for AspectList API compatibility
            if (aspect != null) list.add(aspect, (int) Math.min(amount, Integer.MAX_VALUE));
        }

        return list;
    }

    /**
     * Check if the container accepts a specific aspect.
     * Returns true if there's a filter for this aspect.
     */
    public boolean doesContainerAccept(Aspect aspect) {
        if (aspect == null) return false;

        EssentiaStackKey key = EssentiaStackKey.of(aspect);
        return isInFilter(key);
    }

    /**
     * Check if the container contains at least the specified amount of an aspect.
     */
    public boolean doesContainerContainAmount(Aspect aspect, int amount) {
        if (aspect == null || amount <= 0) return false;

        int slot = findSlotForAspect(aspect);
        if (slot < 0) return false;

        return this.storage[slot] != null && this.amounts[slot] >= amount;
    }

    /**
     * Get the total amount of a specific aspect in the container.
     * This is what IAspectContainer.containerContains(Aspect) should return.
     * We ensure uniqueness of filters, so there will be at most one slot per aspect.
     */
    public int getEssentiaCount(Aspect aspect) {
        if (aspect == null) return 0;

        for (int i = 0; i < STORAGE_SLOTS; i++) {
            EssentiaStack identity = this.storage[i];
            if (identity != null && identity.getAspect() == aspect) {
                // Clamp to int for API compatibility
                return (int) Math.min(this.amounts[i], Integer.MAX_VALUE);
            }
        }

        return 0;
    }

    /**
     * Check if the container contains any amount of an aspect.
     * Convenience method for checking presence (amount > 0).
     */
    public boolean containerContainsAny(Aspect aspect) {
        return getEssentiaCount(aspect) > 0;
    }

    /**
     * Add essentia to the container.
     * Used by IEssentiaTransport when tubes push essentia to us.
     * <p>
     * Only IMPORT interfaces accept essentia from tubes - they are SINKS
     * that accept essentia from external sources to store in the ME network.
     *
     * @param aspect The aspect to add
     * @param amount The amount to add
     * @return The amount actually added
     */
    public int addToContainer(Aspect aspect, int amount) {
        if (aspect == null || amount <= 0) return 0;

        // Only import interfaces accept essentia from tubes (they are sinks)
        // Export interfaces PROVIDE essentia, they don't accept it
        if (this.host.isExport()) return 0;

        EssentiaStack toInsert = new EssentiaStack(aspect, amount);
        int added = receiveFiltered(toInsert, true);

        // Record the change for storage bus notification
        if (added > 0) {
            recordEssentiaChange(aspect, added);
            notifyStorageBusOfChanges();
        }

        return added;
    }

    /**
     * Take essentia from the container (internal method returning amount).
     * Used by IEssentiaTransport when tubes pull essentia from us.
     * <p>
     * Only EXPORT interfaces provide essentia to tubes - they are SOURCES
     * that pull essentia from the ME network and provide it externally.
     * <p>
     * Note: For IAspectContainer.takeFromContainer() which returns boolean,
     * tiles should call this and return (result >= amount).
     *
     * @param aspect The aspect to take
     * @param amount The amount to take
     * @return The amount actually taken
     */
    public int takeEssentiaAmount(Aspect aspect, int amount) {
        if (aspect == null || amount <= 0) return 0;

        // Only export interfaces provide essentia to tubes (they are sources)
        // Import interfaces ACCEPT essentia, they don't provide it
        if (!this.host.isExport()) return 0;

        int slot = findSlotForAspect(aspect);
        if (slot < 0) return 0;

        EssentiaStack drained = drainFromSlot(slot, amount, true);
        int taken = drained != null ? drained.getAmount() : 0;

        // Record the change for storage bus notification (negative = removal)
        if (taken > 0) {
            recordEssentiaChange(aspect, -taken);
            notifyStorageBusOfChanges();
        }

        return taken;
    }

    /**
     * Find the storage slot for a given aspect.
     */
    public int findSlotForAspect(Aspect aspect) {
        if (aspect == null) return -1;

        EssentiaStackKey key = EssentiaStackKey.of(aspect);
        return findSlotByKey(key);
    }

    /**
     * Get the suction amount for tube connectivity.
     * Import = HIGH suction (sink, accepts essentia), Export = LOW suction (source, provides essentia).
     */
    public int getSuctionAmount() {
        return this.host.isExport() ? EXPORT_SUCTION : IMPORT_SUCTION;
    }

    /**
     * Get the suction type (aspect) for IEssentiaTransport.
     * <p>
     * We return null (accept any aspect) to maximize compatibility with tube routing.
     * Thaumcraft tubes seem to prefer routing to sinks that accept any type.
     * The actual filtering happens in addToContainer() -> receiveFiltered() where
     * we only accept essentia matching our filters.
     * <p>
     * This behavior matches how Thaumcraft jars work - they return null for suction
     * type but only store one aspect type per jar.
     * <p>
     * For export interfaces (sources), we return null to indicate we can provide any
     * type we have stored. The actual aspect is reported via getEssentiaType().
     */
    @Nullable
    public Aspect getSuctionType() {
        return null;
    }

    /**
     * Get the first stored essentia type.
     * Used by export interfaces to report what essentia type they have available.
     * <p>
     * Note: This is a hint for the tube network. The actual extraction via takeEssentia()
     * works for any aspect we have stored. Machines like Thaumatorium cycle through their
     * needed aspects and request each one specifically. For direct machine access (like
     * infusion altars), use getAspects() which returns ALL stored aspects.
     *
     * @return The first stored aspect, or null if empty
     */
    @Nullable
    public Aspect getStoredEssentiaType() {
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            EssentiaStack identity = this.storage[i];
            if (identity != null && this.amounts[i] > 0 && identity.getAspect() != null) {
                return identity.getAspect();
            }
        }
        return null;
    }

    // ============================== Storage Bus Notification ==============================

    /**
     * Called when a neighbor block changes.
     * Checks if the changed neighbor is an Essentia Storage Bus and updates the cache.
     * Also delegates to the base class for IAspectContainer capability cache invalidation.
     *
     * @param neighborPos The position of the neighbor that changed
     */
    @Override
    public void onNeighborChanged(BlockPos neighborPos) {
        // Delegate to base class for capability cache invalidation (IAspectContainer scanning)
        super.onNeighborChanged(neighborPos);

        World world = this.host.getHostWorld();
        BlockPos pos = this.host.getHostPos();
        if (world == null || pos == null || neighborPos == null) return;

        // Check if the changed neighbor is a storage bus facing us
        TileEntity te = world.getTileEntity(neighborPos);
        if (te instanceof IPartHost) {
            IPartHost partHost = (IPartHost) te;
            // Find which direction the neighbor is from us, then check the opposite side
            EnumFacing directionToNeighbor = null;
            for (EnumFacing facing : EnumFacing.VALUES) {
                if (pos.offset(facing).equals(neighborPos)) {
                    directionToNeighbor = facing;
                    break;
                }
            }

            if (directionToNeighbor != null) {
                // The storage bus faces towards us = opposite of our offset direction
                AEPartLocation partSide = AEPartLocation.fromFacing(directionToNeighbor.getOpposite());
                IPart part = partHost.getPart(partSide);

                if (part instanceof PartEssentiaStorageBus) {
                    this.hasAdjacentStorageBus = true;
                    return;
                }
            }
        }

        // If no storage bus found at changed position, recheck all neighbors
        // (in case a storage bus was removed and we need to update the flag)
        this.neighborCheckPending = true;
    }

    /**
     * Record an essentia change that needs to be notified to connected storage buses.
     * Changes are accumulated and flushed at the end of tick operations.
     * <p>
     * Uses saturating addition to avoid overflow when accumulating many changes.
     *
     * @param aspect The aspect that changed
     * @param delta  The change amount (positive for additions, negative for removals)
     */
    protected void recordEssentiaChange(Aspect aspect, long delta) {
        if (aspect == null || delta == 0) return;

        this.pendingChanges.merge(aspect, delta, this::saturatingAdd);
    }

    /**
     * Check all adjacent positions for Essentia Storage Buses.
     * Updates the {@link #hasAdjacentStorageBus} cache.
     *
     * @return true if at least one storage bus was found
     */
    protected boolean checkForAdjacentStorageBuses() {
        World world = this.host.getHostWorld();
        BlockPos pos = this.host.getHostPos();
        if (world == null || pos == null) return false;

        for (EnumFacing facing : EnumFacing.VALUES) {
            BlockPos neighborPos = pos.offset(facing);
            TileEntity te = world.getTileEntity(neighborPos);
            if (te == null) continue;

            // Check if neighbor is a cable bus that might have a storage bus part
            if (te instanceof IPartHost) {
                IPartHost partHost = (IPartHost) te;
                // The storage bus would be facing towards us (opposite of our offset direction)
                AEPartLocation partSide = AEPartLocation.fromFacing(facing.getOpposite());
                IPart part = partHost.getPart(partSide);

                if (part instanceof PartEssentiaStorageBus) {
                    this.hasAdjacentStorageBus = true;
                    return true;
                }
            }
        }

        this.hasAdjacentStorageBus = false;
        return false;
    }

    /**
     * Notify the ME network about pending essentia changes.
     * <p>
     * This uses {@code IStorageGrid.postAlterationOfStoredItems()} to inform the network
     * about changes without triggering a full {@code MENetworkCellArrayUpdate} event.
     * This is more efficient for content-only changes (no structural changes to cell providers).
     * <p>
     * IMPORTANT: We post to the STORAGE BUS's network, not our own network, because
     * the storage bus exposes our interface to a potentially different ME network.
     * <p>
     * Called after each AE2 tick or after external I/O operations that modify storage.
     */
    protected void notifyStorageBusOfChanges() {
        // If no pending changes, nothing to do
        if (this.pendingChanges.isEmpty()) return;

        // If no adjacent storage bus, clear changes and exit
        if (!this.hasAdjacentStorageBus) {
            this.pendingChanges.clear();
            return;
        }

        // Find the adjacent storage bus and get its grid
        PartEssentiaStorageBus storageBus = findAdjacentStorageBus();
        if (storageBus == null) {
            this.pendingChanges.clear();
            return;
        }

        IGridNode gridNode = storageBus.getGridNode();
        if (gridNode == null || gridNode.getGrid() == null) {
            this.pendingChanges.clear();
            return;
        }

        // Post changes to the STORAGE BUS's network (not our network!)
        try {
            IStorageGrid storageGrid = gridNode.getGrid().getCache(IStorageGrid.class);
            if (storageGrid == null) {
                this.pendingChanges.clear();
                return;
            }

            IActionSource source = this.host.getActionSource();
            IEssentiaStorageChannel channel = AEApi.instance().storage()
                    .getStorageChannel(IEssentiaStorageChannel.class);

            List<IAEEssentiaStack> changes = new ArrayList<>();
            for (Map.Entry<Aspect, Long> entry : this.pendingChanges.entrySet()) {
                Aspect aspect = entry.getKey();
                long delta = entry.getValue();
                if (delta == 0) continue;

                // Create an AE stack with the delta
                // Positive delta = added, negative delta = removed
                EssentiaStack stack = new EssentiaStack(aspect, 1);
                IAEEssentiaStack aeStack = AEEssentiaStack.fromEssentiaStack(stack);
                if (aeStack != null) {
                    // Set stack size to the delta (sign matters for the network)
                    aeStack.setStackSize(delta);
                    changes.add(aeStack);
                }
            }

            if (!changes.isEmpty()) {
                storageGrid.postAlterationOfStoredItems(channel, changes, source);
            }

        } catch (Exception e) {
            // Grid access failed - ignore
        }

        // Clear pending changes
        this.pendingChanges.clear();
    }

    /**
     * Find an adjacent Essentia Storage Bus.
     *
     * @return The first found storage bus, or null if none found
     */
    @Nullable
    private PartEssentiaStorageBus findAdjacentStorageBus() {
        World world = this.host.getHostWorld();
        BlockPos pos = this.host.getHostPos();
        if (world == null || pos == null) return null;

        for (EnumFacing facing : EnumFacing.VALUES) {
            BlockPos neighborPos = pos.offset(facing);
            TileEntity te = world.getTileEntity(neighborPos);
            if (te == null) continue;

            if (te instanceof IPartHost) {
                IPartHost partHost = (IPartHost) te;
                AEPartLocation partSide = AEPartLocation.fromFacing(facing.getOpposite());
                IPart part = partHost.getPart(partSide);

                if (part instanceof PartEssentiaStorageBus) {
                    return (PartEssentiaStorageBus) part;
                }
            }
        }

        return null;
    }

    /**
     * Capture the current storage state as a map of aspect to amount.
     * Used to compute deltas after operations that change storage.
     * <p>
     * Uses saturating addition to avoid overflow when multiple slots contain the
     * same aspect (which can happen with orphaned slots plus normal slots).
     */
    private Map<Aspect, Long> captureStorageState() {
        Map<Aspect, Long> state = new HashMap<>();

        for (int i = 0; i < STORAGE_SLOTS; i++) {
            EssentiaStack identity = this.storage[i];
            long amount = this.amounts[i];
            if (identity == null || amount <= 0) continue;

            // Use saturating addition to avoid overflow
            Aspect aspect = identity.getAspect();
            if (aspect != null) state.merge(aspect, amount, this::saturatingAdd);
        }

        return state;
    }

    /**
     * Saturating addition for two longs.
     * Returns Long.MAX_VALUE on overflow, Long.MIN_VALUE on underflow.
     */
    private long saturatingAdd(long a, long b) {
        long result = a + b;
        // Overflow if both operands have the same sign and the result has a different sign
        if (((a ^ result) & (b ^ result)) < 0) {
            return (a > 0) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
        return result;
    }

    /**
     * Compute storage changes by comparing before and after states.
     *
     * @param before State captured before the operation
     * @param after  State captured after the operation
     */
    private void computeAndRecordDeltas(Map<Aspect, Long> before, Map<Aspect, Long> after) {
        // Find all aspects that changed
        HashSet<Aspect> allAspects = new HashSet<>();
        allAspects.addAll(before.keySet());
        allAspects.addAll(after.keySet());

        for (Aspect aspect : allAspects) {
            long oldAmount = before.getOrDefault(aspect, 0L);
            long newAmount = after.getOrDefault(aspect, 0L);
            long delta = newAmount - oldAmount;

            if (delta != 0) recordEssentiaChange(aspect, delta);
        }
    }

    /**
     * Override onTick to capture storage deltas and notify connected storage buses.
     * <p>
     * This allows storage buses to see our changes without triggering a full
     * MENetworkCellArrayUpdate event. We capture state before ticking, let the
     * parent class do its import/export work, then compute deltas and notify.
     */
    @Override
    public TickRateModulation onTick(int ticksSinceLastCall) {
        // Check for adjacent storage buses if needed (e.g. on init or after neighbor change)
        if (this.neighborCheckPending) {
            this.neighborCheckPending = false;
            checkForAdjacentStorageBuses();
        }

        // If we have storage buses, capture state before tick operations
        Map<Aspect, Long> beforeState = null;
        if (this.hasAdjacentStorageBus) beforeState = captureStorageState();

        // Let parent handle the actual import/export operations
        TickRateModulation result = super.onTick(ticksSinceLastCall);

        // If we captured state, compute deltas and notify
        if (beforeState != null) {
            Map<Aspect, Long> afterState = captureStorageState();
            computeAndRecordDeltas(beforeState, afterState);
            notifyStorageBusOfChanges();
        }

        return result;
    }

    // ============================== Abstract method implementations ==============================

    @Override
    @Nullable
    protected EssentiaStackKey createKey(EssentiaStack resource) {
        return EssentiaStackKey.of(resource);
    }

    @Override
    protected int getAmount(EssentiaStack resource) {
        return resource.getAmount();
    }

    @Override
    protected void setAmount(EssentiaStack resource, int amount) {
        resource.setAmount(amount);
    }

    @Override
    protected EssentiaStack copyWithAmount(EssentiaStack resource, int amount) {
        Aspect aspect = resource.getAspect();
        if (aspect == null) return null;
        return new EssentiaStack(aspect, amount);
    }

    @Override
    protected EssentiaStack copy(EssentiaStack resource) {
        return resource.copy();
    }

    @Override
    protected String getLocalizedName(EssentiaStack resource) {
        Aspect aspect = resource.getAspect();
        return aspect != null ? aspect.getName() : "Unknown";
    }

    @Override
    protected IAEEssentiaStack toAEStack(EssentiaStack resource) {
        return AEEssentiaStack.fromEssentiaStack(resource);
    }

    @Override
    protected EssentiaStack fromAEStack(IAEEssentiaStack aeStack) {
        return aeStack.getStack();
    }

    @Override
    protected long getAEStackSize(IAEEssentiaStack aeStack) {
        return aeStack.getStackSize();
    }

    @Override
    protected void writeResourceToNBT(EssentiaStack resource, NBTTagCompound tag) {
        Aspect aspect = resource.getAspect();
        if (aspect != null) {
            tag.setString("Aspect", aspect.getTag());
            tag.setInteger("Amount", resource.getAmount());
        }
    }

    @Override
    @Nullable
    protected EssentiaStack readResourceFromNBT(NBTTagCompound tag) {
        if (!tag.hasKey("Aspect")) return null;

        String aspectTag = tag.getString("Aspect");
        Aspect aspect = Aspect.getAspect(aspectTag);
        if (aspect == null) return null;

        int amount = tag.hasKey("Amount") ? tag.getInteger("Amount") : 1;
        return new EssentiaStack(aspect, amount);
    }

    @Override
    protected String getResourceName(EssentiaStack resource) {
        Aspect aspect = resource.getAspect();
        return aspect != null ? aspect.getTag() : "";
    }

    @Override
    @Nullable
    protected EssentiaStack getResourceByName(String name, int amount) {
        Aspect aspect = Aspect.getAspect(name);
        if (aspect == null) return null;
        return new EssentiaStack(aspect, amount);
    }

    // ============================== Stream Serialization ==============================

    @Override
    public boolean readStorageFromStream(ByteBuf data) {
        boolean changed = false;

        // Clear all storage first
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            if (this.storage[i] != null || this.amounts[i] != 0) {
                this.storage[i] = null;
                this.amounts[i] = 0;
                changed = true;
            }
        }

        int count = data.readShort();
        for (int idx = 0; idx < count; idx++) {
            int slot = data.readShort();
            int tagLen = data.readByte() & 0xFF;
            byte[] tagBytes = new byte[tagLen];
            data.readBytes(tagBytes);
            String tag = new String(tagBytes, StandardCharsets.UTF_8);
            long amount = data.readLong();

            if (slot < 0 || slot >= STORAGE_SLOTS) continue;

            Aspect aspect = Aspect.getAspect(tag);
            if (aspect != null) {
                this.setResourceInSlotWithAmount(slot, new EssentiaStack(aspect, 1), amount);
                changed = true;
            }
        }

        return changed;
    }

    @Override
    public void writeStorageToStream(ByteBuf data) {
        // Count non-empty storage slots first
        int count = 0;
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            if (this.storage[i] != null && this.amounts[i] > 0) count++;
        }

        data.writeShort(count);

        for (int i = 0; i < STORAGE_SLOTS; i++) {
            EssentiaStack identity = this.storage[i];
            long amount = this.amounts[i];
            if (identity == null || amount <= 0) continue;

            Aspect aspect = identity.getAspect();
            if (aspect == null) continue;

            String tag = aspect.getTag();
            byte[] tagBytes = tag.getBytes(StandardCharsets.UTF_8);

            data.writeShort(i);
            data.writeByte(tagBytes.length);
            data.writeBytes(tagBytes);
            data.writeLong(amount);
        }
    }

    @Override
    public boolean readFiltersFromStream(ByteBuf data) {
        boolean changed = false;

        // Clear all filters first
        for (int i = 0; i < FILTER_SLOTS; i++) {
            if (this.filters[i] != null) {
                this.filters[i] = null;
                changed = true;
            }
        }

        int count = data.readShort();
        for (int idx = 0; idx < count; idx++) {
            int slot = data.readShort();
            int tagLen = data.readByte() & 0xFF;
            byte[] tagBytes = new byte[tagLen];
            data.readBytes(tagBytes);
            String tag = new String(tagBytes, StandardCharsets.UTF_8);

            if (slot < 0 || slot >= FILTER_SLOTS) continue;

            Aspect aspect = Aspect.getAspect(tag);
            if (aspect != null) {
                // Filters have amount 1 (type only)
                this.filters[slot] = new EssentiaStack(aspect, 1);
                changed = true;
            }
        }

        if (changed) this.refreshFilterMap();

        return changed;
    }

    @Override
    public void writeFiltersToStream(ByteBuf data) {
        // Count non-empty filters first
        int count = 0;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            if (this.filters[i] != null) count++;
        }

        data.writeShort(count);

        for (int i = 0; i < FILTER_SLOTS; i++) {
            EssentiaStack essentia = this.filters[i];
            if (essentia == null) continue;

            Aspect aspect = essentia.getAspect();
            if (aspect == null) continue;

            String tag = aspect.getTag();
            byte[] tagBytes = tag.getBytes(StandardCharsets.UTF_8);

            data.writeShort(i);
            data.writeByte(tagBytes.length);
            data.writeBytes(tagBytes);
        }
    }

    @Override
    protected IMEInventory<IAEEssentiaStack> getMEInventory(IStorageGrid storage) {
        return storage.getInventory(
            AEApi.instance().storage().getStorageChannel(IEssentiaStorageChannel.class)
        );
    }

    @Override
    protected ItemStack createRecoveryItem(EssentiaStack identity, long amount) {
        if (identity == null || identity.getAspect() == null || amount <= 0) {
            return ItemStack.EMPTY;
        }

        // Use the recovery container to store the essentia for later recovery
        return ItemRecoveryContainer.createForEssentia(
            identity.getAspect().getTag(),
            amount
        );
    }

    // ============================== Auto-Pull/Push capability methods ==============================

    /**
     * Essentia does not use Forge capabilities. Return null so the base class
     * skips the standard Forge capability scanning. We override refreshCapabilityCache()
     * instead to directly scan for IAspectContainer on adjacent tiles.
     */
    @Override
    @Nullable
    protected Capability<?> getAdjacentCapability() {
        return null;
    }

    /**
     * Override base capability cache to scan for IAspectContainer on adjacent tiles directly,
     * since essentia doesn't use Forge capabilities.
     */
    @Override
    protected void refreshCapabilityCache() {
        this.cachedAdjacentTiles.clear();
        this.cachedCapabilities.clear();
        this.capabilityCachePopulated = true;

        World world = this.host.getHostWorld();
        BlockPos pos = this.host.getHostPos();
        if (world == null || pos == null) return;

        for (EnumFacing facing : EnumFacing.VALUES) {
            BlockPos adjacentPos = pos.offset(facing);
            if (!world.isBlockLoaded(adjacentPos)) continue;

            TileEntity te = world.getTileEntity(adjacentPos);
            if (te == null || te.isInvalid()) continue;
            if (!(te instanceof IAspectContainer)) continue;

            // Don't cache ourselves (should never happen, but just in case)
            TileEntity selfTe = world.getTileEntity(pos);
            if (te == selfTe) continue;

            this.cachedAdjacentTiles.put(facing, new WeakReference<>(te));
            this.cachedCapabilities.put(facing, te);
        }
    }

    /**
     * Override invalidation to also scan for IAspectContainer directly.
     */
    @Override
    protected void invalidateCapabilityCacheForFacing(EnumFacing facing) {
        this.cachedAdjacentTiles.remove(facing);
        this.cachedCapabilities.remove(facing);

        if (!this.installedAutoPushPullUpgrade) return;

        World world = this.host.getHostWorld();
        BlockPos pos = this.host.getHostPos();
        if (world == null || pos == null) return;

        BlockPos adjacentPos = pos.offset(facing);
        if (!world.isBlockLoaded(adjacentPos)) return;

        TileEntity te = world.getTileEntity(adjacentPos);
        if (te == null || te.isInvalid()) return;
        if (!(te instanceof IAspectContainer)) return;

        this.cachedAdjacentTiles.put(facing, new WeakReference<>(te));
        this.cachedCapabilities.put(facing, te);
    }

    @Override
    protected long countResourceInHandler(Object handler, EssentiaStackKey key, EnumFacing facing) {
        if (!(handler instanceof IAspectContainer)) return 0;

        Aspect aspect = key.getAspect();
        if (aspect == null) return 0;

        return ((IAspectContainer) handler).containerContains(aspect);
    }

    /**
     * Extract essentia from an adjacent IAspectContainer.
     * <p>
     * IMPORTANT: {@code takeFromContainer(Aspect, int)} is all-or-nothing: it returns false
     * (and removes nothing) if the requested amount exceeds what's available. We MUST check
     * {@code containerContains()} first and cap the extraction amount.
     */
    @Override
    protected long extractResourceFromHandler(Object handler, EssentiaStackKey key, int maxAmount, EnumFacing facing) {
        if (!(handler instanceof IAspectContainer)) return 0;

        IAspectContainer container = (IAspectContainer) handler;
        Aspect aspect = key.getAspect();
        if (aspect == null) return 0;

        // Cap at available amount (takeFromContainer is all-or-nothing)
        int available = container.containerContains(aspect);
        if (available <= 0) return 0;

        int toExtract = Math.min(maxAmount, available);
        if (!container.takeFromContainer(aspect, toExtract)) return 0;

        return toExtract;
    }

    @Override
    protected long insertResourceIntoHandler(Object handler, EssentiaStack identity, int maxAmount, EnumFacing facing) {
        if (!(handler instanceof IAspectContainer)) return 0;

        IAspectContainer container = (IAspectContainer) handler;
        Aspect aspect = identity.getAspect();
        if (aspect == null) return 0;

        if (!container.doesContainerAccept(aspect)) return 0;

        // addToContainer returns the leftover amount that could NOT be added
        int leftover = container.addToContainer(aspect, maxAmount);
        return maxAmount - leftover;
    }
}
