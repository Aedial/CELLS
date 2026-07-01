package com.cells.parts.subnetproxy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.config.FuzzyMode;
import appeng.api.config.Upgrades;
import appeng.api.config.Actionable;
import appeng.api.implementations.IPowerChannelState;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartModel;
import appeng.api.parts.IPart;
import appeng.api.parts.PartItemStack;
import appeng.api.storage.ICellContainer;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellProvider;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.fluids.items.FluidDummyItem;
import appeng.fluids.util.AEFluidStack;
import appeng.core.sync.GuiBridge;
import appeng.helpers.IPriorityHost;
import appeng.items.parts.PartModels;
import appeng.capabilities.Capabilities;
import appeng.me.GridAccessException;
import appeng.me.helpers.MachineSource;
import appeng.parts.AEBasePart;
import appeng.parts.PartModel;
import appeng.parts.automation.UpgradeInventory;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.SettingsFrom;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;
import appeng.util.inv.filter.IAEItemFilter;
import appeng.util.item.AEItemStack;

import com.cells.Cells;
import com.cells.api.FilterHostUtil;
import com.cells.api.ISubnetProxy;
import com.cells.helpers.SubnetProxyMemoryCardHelper;
import com.cells.Tags;
import com.cells.config.CellsConfig;
import com.cells.gui.CellsGuiHandler;
import com.cells.integration.mekanismenergistics.MekanismEnergisticsIntegration;
import com.cells.integration.thaumicenergistics.ThaumicEnergisticsIntegration;
import com.cells.items.ItemInsertionCard;
import com.cells.network.sync.ResourceType;
import com.cells.parts.CellsPartType;
import com.cells.parts.ItemCellsPart;
import com.cells.util.PowerStateHelper;


/**
 * Front half of the Subnet Proxy block.
 * <p>
 * Connects to the "front" cable (Grid B) via an outer proxy, and exposes
 * a filtered, read-only view of Grid A's storage (via the back part) to Grid B.
 * <p>
 * Implements {@link ICellContainer} so Grid B automatically discovers
 * the passthrough storage. Since the inner proxy is orphaned (no center cable),
 * only Grid B will see this ICellContainer, no double registration.
 * <p>
 * The filter configuration, upgrades, page state, and filter mode are all
 * managed here and persisted in NBT.
 */
public class PartSubnetProxyFront extends AEBasePart
    implements IPowerChannelState, ICellContainer, IAEAppEngInventory, IGridTickable, IPriorityHost,
           ISubnetProxy {

    // LED state flags
    protected static final int POWERED_FLAG = 1;
    protected static final int CHANNEL_FLAG = 2;
    protected static final int BOTH_PARTS_FLAG = 4;

    /**
     * Cached storage channel singletons for the two always-present channels.
     * Resolved lazily on first access (AEApi may not be ready at class load).
     * Used to avoid repeated {@code AEApi.instance().storage()
     * .getStorageChannel(...)} lookups per call. Channel instances are
     * registry singletons, so caching them is safe.
     */
    private static volatile IItemStorageChannel CACHED_ITEM_CHANNEL;
    private static volatile IFluidStorageChannel CACHED_FLUID_CHANNEL;

    private static IItemStorageChannel itemChannel() {
        IItemStorageChannel ch = CACHED_ITEM_CHANNEL;
        if (ch == null) {
            ch = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
            CACHED_ITEM_CHANNEL = ch;
        }
        return ch;
    }

    private static IFluidStorageChannel fluidChannel() {
        IFluidStorageChannel ch = CACHED_FLUID_CHANNEL;
        if (ch == null) {
            ch = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
            CACHED_FLUID_CHANNEL = ch;
        }
        return ch;
    }

    @PartModels
    public static final ResourceLocation MODEL_BASE = new ResourceLocation(Tags.MODID, "part/subnet_proxy_front/base");
    @PartModels
    public static final ResourceLocation MODEL_STATUS_OFF = new ResourceLocation(Tags.MODID, "part/subnet_proxy_front/status_off");
    @PartModels
    public static final ResourceLocation MODEL_STATUS_ON = new ResourceLocation(Tags.MODID, "part/subnet_proxy_front/status_on");
    @PartModels
    public static final ResourceLocation MODEL_STATUS_HAS_CHANNEL = new ResourceLocation(Tags.MODID, "part/subnet_proxy_front/status_active");

    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_STATUS_OFF);
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_STATUS_ON);
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_STATUS_HAS_CHANNEL);

    /** Slots per page of filter config: 9 columns × 7 rows */
    public static final int SLOTS_PER_PAGE = 63;

    /**
     * Filter config inventory. Total size = SLOTS_PER_PAGE * maxPages.
     * Items represent filters: plain ItemStacks for item filters,
     * FluidDummyItem stacks for fluid filters.
     */
    private AppEngInternalInventory config;

    /**
     * Upgrade inventory. Slot count comes from config.
     * Supports: Capacity (adds pages), Fuzzy (fuzzy matching), Inverter (blacklist).
     */
    private final UpgradeInventory upgrades;

    /** Current page index (0-based). Persisted in NBT, synced to GUI. */
    private int currentPage = 0;

    /**
     * Filter mode: determines how items dragged into filter slots are converted.
     * ITEM = store as ItemStack, FLUID = convert to FluidDummyItem, etc.
     * Persisted in NBT, synced to GUI.
     */
    private ResourceType filterMode = ResourceType.ITEM;

    /**
     * Fuzzy mode for item matching when a fuzzy card is installed.
     * Persisted in NBT, synced to GUI.
     */
    private FuzzyMode fuzzyMode = FuzzyMode.IGNORE_ALL;

    /**
     * Set of storage channels currently exposed by this proxy. Disabled channels
     * are entirely hidden from the front-grid: {@link #getCellArray} returns an
     * empty list for them, and {@link GridAListener#postChange} drops their deltas
     * before forwarding. The default is empty (everything off) so newly placed
     * proxies don't accidentally expose anything until the user opts in.
     * <p>
     * Persisted in NBT as a bitmask over {@link ResourceType#ordinal()}, synced
     * to the GUI via the container's {@code @GuiSync} field.
     */
    private EnumSet<ResourceType> enabledChannels = EnumSet.noneOf(ResourceType.class);

    /** Client-side LED state */
    private int clientFlags = 0;

    /** Cached item-channel passthrough handler */
    private SubnetProxyInventoryHandler<IAEItemStack> itemHandler;

    /** Cached fluid-channel passthrough handler */
    private SubnetProxyInventoryHandler<IAEFluidStack> fluidHandler;

    // ========================= Insertion Handlers (front-grid → back-grid) =========================

    /**
     * Item insertion handler exposed on the FRONT-grid when an Insertion Card is
     * installed. Forwards matching injections to the BACK-grid's storage monitor,
     * implementing the reverse direction from the read view.
     * <p>
     * Filter is shared with {@link #itemHandler} (read-side filter): items the
     * proxy exposes from back-grid for reading are also the items it accepts for
     * insertion in the opposite direction.
     */
    private final SubnetProxyInsertionHandler<IAEItemStack> itemInsertionHandler;

    /** Fluid insertion handler. See {@link #itemInsertionHandler}. */
    private final SubnetProxyInsertionHandler<IAEFluidStack> fluidInsertionHandler;

    /**
     * Gas insertion handler (raw type to avoid loading Mekanism classes when
     * mekeng is absent). Allocated only when MekanismEnergistics is loaded.
     * See {@link #itemInsertionHandler}.
     */
    @SuppressWarnings("rawtypes")
    private SubnetProxyInsertionHandler gasInsertionHandler;

    /**
     * Essentia insertion handler (raw type to avoid loading TE classes when
     * thaumicenergistics is absent). Allocated only when ThaumicEnergistics
     * is loaded. See {@link #itemInsertionHandler}.
     */
    @SuppressWarnings("rawtypes")
    private SubnetProxyInsertionHandler essentiaInsertionHandler;

    /**
     * Cached: whether the insertion card was present at the last
     * {@link #updateInsertionHandlers()} call. Used by {@link #getCellArray}
     * to decide whether to include the insertion handlers without re-scanning
     * upgrade slots on every grid query.
     */
    private boolean insertionActive = false;

    /**
     * Cached gas-channel passthrough handler (null if MekanismEnergistics not loaded).
     * Typed as raw to avoid class loading; the actual generic type is IAEGasStack.
     */
    @SuppressWarnings("rawtypes")
    private SubnetProxyInventoryHandler gasHandler;

    /**
     * Cached essentia-channel passthrough handler (null if ThaumicEnergistics not loaded).
     * Typed as raw to avoid class loading; the actual generic type is IAEEssentiaStack.
     */
    @SuppressWarnings("rawtypes")
    private SubnetProxyInventoryHandler essentiaHandler;

    /** Priority for the passthrough storage (default 0) */
    private int priority = 0;

    /** Debounce for markDirtyAndSave */
    private long lastSaveTick = -1;

    /**
     * True while {@link #updatePassthroughSources()} is mutating handler/source state.
     * Coordinator callbacks can fire during that refresh, so Grid B refreshes must
     * be deferred until the rebuild has reached a consistent state.
     */
    private boolean rebuildingPassthroughSources = false;

    /**
     * Identity hash of the last structural surface published to Grid B.
     * Tracks topology only (sources, listeners, elections, insertion presence),
     * not item contents. Content changes continue to flow through monitor deltas.
     */
    private int lastPublishedStructureHash = 0;

    /**
     * Identity hash of the cell-handler surface Grid B can discover through
     * {@link #getCellArray}. Internal back-grid churn can change the published
     * contents without changing this outer handler set.
     */
    private int lastPublishedCellArrayHash = 0;

    /**
     * Runtime-only probe state for "listed but not extractable" mismatches.
     * Kept on the front part so the warning log and the looked-at diagnostic
     * command can both reference the same last-observed fault.
     */
    @Nullable
    private FaultRecord lastFaultRecord;

    private long lastFaultLogTick = Long.MIN_VALUE;
    private int lastFaultLogFingerprint = 0;

    private static final long FAULT_LOG_COOLDOWN_TICKS = 100L;

    /**
     * Trace switch for Subnet Proxy update flow diagnostics.
     * Flip while investigating issue #84-class force-update races.
     */
    private static final boolean TRACE_UPDATE_FLOW = Boolean.parseBoolean(System.getProperty("cells.trace.subnetproxy.updateflow", "false"));

    public static class FaultRecord {

        public final long firstObservedTick;
        public final long lastObservedTick;
        public final int occurrenceCount;
        public final String channelName;
        public final String requestDescription;
        public final long requestedAmount;
        public final long extractedAmount;
        public final long visibleAmount;
        public final String actionName;
        public final boolean ownOriginVisible;
        public final int structureHash;
        public final int localCellCount;
        public final int visiblePeerCount;

        private final int fingerprint;

        private FaultRecord(
                long firstObservedTick,
                long lastObservedTick,
                int occurrenceCount,
                String channelName,
                String requestDescription,
                long requestedAmount,
                long extractedAmount,
                long visibleAmount,
                String actionName,
                boolean ownOriginVisible,
                int structureHash,
                int localCellCount,
                int visiblePeerCount,
                int fingerprint) {
            this.firstObservedTick = firstObservedTick;
            this.lastObservedTick = lastObservedTick;
            this.occurrenceCount = occurrenceCount;
            this.channelName = channelName;
            this.requestDescription = requestDescription;
            this.requestedAmount = requestedAmount;
            this.extractedAmount = extractedAmount;
            this.visibleAmount = visibleAmount;
            this.actionName = actionName;
            this.ownOriginVisible = ownOriginVisible;
            this.structureHash = structureHash;
            this.localCellCount = localCellCount;
            this.visiblePeerCount = visiblePeerCount;
            this.fingerprint = fingerprint;
        }

        private FaultRecord(FaultRecord other) {
            this(
                other.firstObservedTick,
                other.lastObservedTick,
                other.occurrenceCount,
                other.channelName,
                other.requestDescription,
                other.requestedAmount,
                other.extractedAmount,
                other.visibleAmount,
                other.actionName,
                other.ownOriginVisible,
                other.structureHash,
                other.localCellCount,
                other.visiblePeerCount,
                other.fingerprint);
        }

        private FaultRecord withObservation(long observedTick, long extractedAmount, long visibleAmount) {
            return new FaultRecord(
                this.firstObservedTick,
                observedTick,
                this.occurrenceCount + 1,
                this.channelName,
                this.requestDescription,
                this.requestedAmount,
                extractedAmount,
                visibleAmount,
                this.actionName,
                this.ownOriginVisible,
                this.structureHash,
                this.localCellCount,
                this.visiblePeerCount,
                this.fingerprint);
        }
    }

    public static class ProxyDiagnosticSnapshot {

        public final int dimensionId;
        public final String dimensionName;
        @Nullable
        public final BlockPos pos;
        @Nullable
        public final EnumFacing side;
        public final boolean powered;
        public final boolean active;
        public final boolean paired;
        public final EnumSet<ResourceType> enabledChannels;
        public final int priority;
        public final boolean hasInsertionCard;
        public final boolean insertionActive;
        public final ResourceType filterMode;
        public final boolean fuzzyEnabled;
        public final boolean inverterEnabled;
        public final boolean ownOriginVisible;
        public final int structureHash;
        public final int frontGridHash;
        public final int backGridHash;
        public final int exposedOriginCount;
        public final int totalPeerCount;
        public final int visiblePeerCount;
        public final int itemLocalCells;
        public final int fluidLocalCells;
        public final int gasLocalCells;
        public final int essentiaLocalCells;
        @Nullable
        public final FaultRecord lastFault;

        private ProxyDiagnosticSnapshot(
                int dimensionId,
                String dimensionName,
                @Nullable BlockPos pos,
                @Nullable EnumFacing side,
                boolean powered,
                boolean active,
                boolean paired,
                EnumSet<ResourceType> enabledChannels,
                int priority,
                boolean hasInsertionCard,
                boolean insertionActive,
                ResourceType filterMode,
                boolean fuzzyEnabled,
                boolean inverterEnabled,
                boolean ownOriginVisible,
                int structureHash,
                int frontGridHash,
                int backGridHash,
                int exposedOriginCount,
                int totalPeerCount,
                int visiblePeerCount,
                int itemLocalCells,
                int fluidLocalCells,
                int gasLocalCells,
                int essentiaLocalCells,
                @Nullable FaultRecord lastFault) {
            this.dimensionId = dimensionId;
            this.dimensionName = dimensionName;
            this.pos = pos;
            this.side = side;
            this.powered = powered;
            this.active = active;
            this.paired = paired;
            this.enabledChannels = enabledChannels.clone();
            this.priority = priority;
            this.hasInsertionCard = hasInsertionCard;
            this.insertionActive = insertionActive;
            this.filterMode = filterMode;
            this.fuzzyEnabled = fuzzyEnabled;
            this.inverterEnabled = inverterEnabled;
            this.ownOriginVisible = ownOriginVisible;
            this.structureHash = structureHash;
            this.frontGridHash = frontGridHash;
            this.backGridHash = backGridHash;
            this.exposedOriginCount = exposedOriginCount;
            this.totalPeerCount = totalPeerCount;
            this.visiblePeerCount = visiblePeerCount;
            this.itemLocalCells = itemLocalCells;
            this.fluidLocalCells = fluidLocalCells;
            this.gasLocalCells = gasLocalCells;
            this.essentiaLocalCells = essentiaLocalCells;
            this.lastFault = lastFault == null ? null : new FaultRecord(lastFault);
        }
    }

    // ========================= Delta Forwarding State =========================

    /**
     * Action source identifying this proxy as the originator of delta events
     * posted to Grid B. Using a dedicated source prevents the anti-recursion
     * guard in NetworkMonitor from confusing proxy-forwarded deltas with
     * deltas from other machines.
     * <p>
     * Used only as a last-resort fallback in {@link #snapshotDiffChannel}
     * when {@code gridA} is null (no back-grid bound). All normal forwarding
     * paths wrap each event with {@link SubnetProxyEventSource} so the
     * origin-grid and event-UUID metadata survive an arbitrary chain of hops
     * (and dedup correctly across diamond topologies).
     */
    private final IActionSource proxySource = new MachineSource(this);

    // ========================= Peer aggregation / coordinator state =========================

    /**
     * Other {@link PartSubnetProxyFront} instances on our back-grid (NOT on
     * our front-grid). Each one's back-grid is a candidate "peer origin" we
     * may publish into our front-grid (subject to election in the front-grid
     * coordinator). Recomputed in {@link #updatePassthroughSources}.
     * <p>
     * Self is excluded. Peers whose back-grid equals our front-grid are kept
     * in this list but skipped at listing/forwarding time as immediate loop-backs.
     */
    private List<PartSubnetProxyFront> peerFronts = new ArrayList<>();

    /**
     * Origin-grids this front can publish on its front-grid: own back-grid +
     * each peer's back-grid. Used by the front-grid coordinator's election
     * (which origins this front competes for) and by {@link GridAListener}
     * (whether to forward an incoming delta whose origin we know).
     * <p>
     * Always includes our own back-grid (when set). Recomputed in
     * {@link #updatePassthroughSources}.
     */
    private Set<IGrid> exposedOrigins = new HashSet<>();

    /**
     * The {@link SubnetProxyGridCoordinator} currently registered for our
     * front-grid, or null if not registered (front-grid unavailable).
     * Tracked so we can unregister deterministically when the front-grid
     * changes or the part is removed from the world.
     */
    private SubnetProxyGridCoordinator currentFrontGridCoord;

    /**
     * Front-grid identity paired with {@link #currentFrontGridCoord}. This lets
     * hot-path election checks distinguish a live coordinator miss from a stale
     * cached coordinator that still belongs to an old grid.
     */
    private IGrid currentFrontGrid;

    /**
     * Listener registered on Grid A's IMEMonitors (all channels).
     * Typed as raw to handle multiple storage channels with a single instance.
     * Sets {@link #deltasDirty} and alerts Grid B's tick manager on change.
     */
    private final GridAListener gridAListener = new GridAListener();

    /**
     * Stable token for {@link IMEMonitorHandlerReceiver#isValid}.
     * Invalidated on removal from world; recreated on next listener registration.
     */
    private Object listenerToken = new Object();

    /**
     * Grid A's item monitor we are currently registered on, or null.
     * Stored for unregistration when sources change or part is removed.
     */
    @SuppressWarnings("rawtypes")
    private IMEMonitor registeredItemMonitor;

    /** Grid A's fluid monitor we are currently registered on, or null. */
    @SuppressWarnings("rawtypes")
    private IMEMonitor registeredFluidMonitor;

    /**
     * Grid A reference, stored when listeners are registered.
     * Used by {@link #isLocalSource} to distinguish local cell changes from
     * passthrough-forwarded changes (storage bus → ME Interface → remote grid).
     */
    private IGrid gridA;

    /**
     * IActionHost-backed providers that contributed directly-local storage at the
     * last source rebuild. This lets delta handling recognize later disconnect/
     * reset events from those same providers without any steady-state rescans.
     */
    private Set<IActionHost> knownLocalProviderHosts = Collections.emptySet();

    /**
     * Dirty flag for deferred monitor-reset reconciliation on Grid B.
     * Set by {@link GridAListener#onListUpdate()} so the next proxy tick
     * can reconcile Grid B's cached listing against the rebuilt back-grid
     * source set. Normal per-item deltas are forwarded immediately in
     * {@link GridAListener#postChange} and do NOT set this flag.
     */
    private boolean deltasDirty = false;

    /**
    * True while a Grid A source refresh still needs a diff against the
    * previous passthrough snapshot. Lazy source refreshes must preserve the
    * old snapshot baseline until {@link #tickingRequest} reconciles it.
    */
    private boolean pendingMonitorResetReconcile = false;

    /**
     * World tick of the last back-grid cell-array rebuild routed through
     * {@link #markSourcesDirty()}. Storage-bus partition resets emit a direct
     * self-sourced delta in the same tick after that rebuild; ordinary storage
     * bus content changes do not.
     */
    private long lastSourceDirtyTick = Long.MIN_VALUE;

    /**
     * True when the next monitor-reset reconcile should force Grid B to rebuild
     * from the proxy's current listing instead of using the cheaper snapshot
     * diff path. Reserved for storage-surface reset signatures only.
     */
    private boolean pendingForcedGridBRefresh = false;

    /**
     * Dirty flag for passthrough sources (local cells from Grid A).
     * Set by the back part when Grid A fires MENetworkCellArrayUpdate,
     * and on initial placement. Cleared after updatePassthroughSources() runs.
     */
    private boolean sourcesDirty = true;

    /**
     * Dirty flag for filter predicates.
     * Set when config inventory or upgrades change. Cleared after rebuildFilters() runs.
     */
    private boolean filtersDirty = true;

    /** Cached inverter state at time of last filter rebuild */
    private boolean cachedHasInverter = false;

    /** Cached fuzzy state at time of last filter rebuild */
    private boolean cachedHasFuzzy = false;

    /**
     * Cached server-side flag: whether the back counterpart exists in the adjacent block.
     * Updated on neighbor changes and placement, used in writeToStream to avoid
     * a tile entity lookup every tick.
     */
    private boolean cachedHasBack = false;

    /**
     * World tick when this part was placed. Used to suppress spurious
     * activation caused by off-hand fall-through on the same tick as
     * placement (AE2's client-side PartPlacement returns PASS, so
     * Minecraft tries the off-hand which triggers onPartActivate).
     */
    private long placedTick = -1;

    public PartSubnetProxyFront(final ItemStack is) {
        super(is);

        // Inner proxy connects to Grid B (the cable bus this part sits on).
        // Grid B discovers this ICellContainer via the inner proxy node.
        this.getProxy().setFlags(GridFlags.REQUIRE_CHANNEL);
        this.getProxy().setIdlePowerUsage(1.0);

        // Upgrade inventory with configurable slot count
        int upgradeSlots = CellsConfig.general.subnetProxyUpgradeSlots;
        this.upgrades = new UpgradeInventory(this, upgradeSlots) {
            @Override
            public int getMaxInstalled(Upgrades u) {
                // We only limit capacity cards by the number of slots
                if (u == Upgrades.CAPACITY) return Integer.MAX_VALUE;
                if (u == Upgrades.FUZZY) return 1;
                if (u == Upgrades.INVERTER) return 1;
                // QUANTUM_LINK is the placeholder type for custom CELLS upgrades.
                // We allow 1 for the Insertion Card. The filter below restricts
                // which specific item classes are accepted.
                if (u == Upgrades.QUANTUM_LINK) return 1;
                return 0;
            }
        };

        // Restrict the QUANTUM_LINK upgrade slot to only accept InsertionCard,
        // preventing other custom CELLS upgrades from being placed here.
        this.upgrades.setFilter(new IAEItemFilter() {
            @Override
            public boolean allowExtract(IItemHandler inv, int slot, int amount) {
                return true;
            }

            @Override
            public boolean allowInsert(IItemHandler inv, int slot, ItemStack itemstack) {
                if (itemstack.isEmpty()) return false;
                if (!(itemstack.getItem() instanceof IUpgradeModule)) return false;

                Upgrades u = ((IUpgradeModule) itemstack.getItem()).getType(itemstack);
                if (u == null) return false;

                // Only allow InsertionCard for the custom upgrade slot
                if (u == Upgrades.QUANTUM_LINK && !(itemstack.getItem() instanceof ItemInsertionCard)) {
                    return false;
                }

                return upgrades.getInstalledUpgrades(u) < upgrades.getMaxInstalled(u);
            }
        });

        // Allocate config inventory for all pages. At max upgrades this can be 63 * (1 + upgradeSlots)
        // slots in memory, but NBT serialization is sparse (only non-empty slots written).
        int totalSlots = SLOTS_PER_PAGE * getMaxPages();
        this.config = new AppEngInternalInventory(this, totalSlots, 1);

        // Create passthrough handlers
        this.itemHandler = new SubnetProxyInventoryHandler<>(
            AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class), this.priority);
        this.fluidHandler = new SubnetProxyInventoryHandler<>(
            AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class), this.priority);

        // Wire owning front part so handlers can fetch peer items via
        // appendPeerItemsForListing during getAvailableItems().
        this.itemHandler.setFrontPart(this);
        this.fluidHandler.setFrontPart(this);

        // Create insertion handlers (always allocated; activated only when card is installed)
        this.itemInsertionHandler = new SubnetProxyInsertionHandler<>(
            AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
        this.fluidInsertionHandler = new SubnetProxyInsertionHandler<>(
            AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));

        // Optional mod handlers (created only if the mod is loaded to avoid class loading errors)
        if (MekanismEnergisticsIntegration.isModLoaded()) {
            this.gasHandler = SubnetProxyGasHelper.createHandler(this.priority);
            this.gasHandler.setFrontPart(this);
            this.gasInsertionHandler = SubnetProxyGasHelper.createInsertionHandler();
        }
        if (ThaumicEnergisticsIntegration.isModLoaded()) {
            this.essentiaHandler = SubnetProxyEssentiaHelper.createHandler(this.priority);
            this.essentiaHandler.setFrontPart(this);
            this.essentiaInsertionHandler = SubnetProxyEssentiaHelper.createInsertionHandler();
        }

        this.lastPublishedStructureHash = this.computePublishedStructureHash();
        this.lastPublishedCellArrayHash = this.computePublishedCellArrayHash();
    }

    // ========================= Page Management =========================

    /** Maximum pages = 1 (base) + max capacity cards */
    public int getMaxPages() {
        return 1 + CellsConfig.general.subnetProxyUpgradeSlots;
    }

    /** Actual pages available = 1 + installed capacity cards */
    public int getTotalPages() {
        return 1 + this.upgrades.getInstalledUpgrades(Upgrades.CAPACITY);
    }

    public int getCurrentPage() {
        return this.currentPage;
    }

    public void setCurrentPage(int page) {
        this.currentPage = Math.max(0, Math.min(page, getTotalPages() - 1));
    }

    // ========================= Filter Mode =========================

    public ResourceType getFilterMode() {
        return this.filterMode;
    }

    public void setFilterMode(ResourceType mode) {
        this.filterMode = mode;
        this.markHostDirty();
    }

    /** Cycle to the next available filter mode (skipping unavailable mods) */
    public void cycleFilterMode() {
        ResourceType[] available = ResourceType.getAvailableTypes();
        int idx = 0;
        for (int i = 0; i < available.length; i++) {
            if (available[i] == this.filterMode) {
                idx = i;
                break;
            }
        }

        idx = (idx + 1) % available.length;
        setFilterMode(available[idx]);
    }

    // ========================= Fuzzy Mode =========================

    public FuzzyMode getFuzzyMode() {
        return this.fuzzyMode;
    }

    public void setFuzzyMode(FuzzyMode mode) {
        this.fuzzyMode = mode;
        this.filtersDirty = true;
        this.notifyGridOfChange();
        this.markHostDirty();
    }

    // ========================= Channel Exposure =========================

    /**
     * @return true if the given storage channel is currently exposed on the front-grid.
     *         When false, {@link #getCellArray} returns empty and incoming deltas
     *         are dropped (the proxy acts as if it didn't exist for that channel).
     */
    public boolean isChannelEnabled(ResourceType type) {
        return this.enabledChannels.contains(type);
    }

    void ensureFiltersCurrent() {
        if (this.filtersDirty) this.rebuildFilters();
    }

    void ensureSourcesCurrent() {
        if (!this.sourcesDirty) return;

        if (this.pendingMonitorResetReconcile) {
            this.updatePassthroughSources(false);
            return;
        }

        this.updatePassthroughSources();
    }

    boolean isReadChannelExposed(IStorageChannel<?> channel) {
        ResourceType type = channelToResourceType(channel, itemChannel(), fluidChannel());
        return type == null || this.isReadChannelExposed(type);
    }

    boolean isReadChannelExposed(ResourceType type) {
        if (!this.shouldExposeOwnOrigin()) return false;
        if (this.enabledChannels.contains(type)) return true;

        for (PartSubnetProxyFront sibling : this.getParallelOwnOriginFronts()) {
            if (sibling == this) continue;
            if (sibling.enabledChannels.contains(type)) return true;
        }

        return false;
    }

    private boolean hasAnyReadChannelExposed() {
        if (this.isReadChannelExposed(ResourceType.ITEM)) return true;
        if (this.isReadChannelExposed(ResourceType.FLUID)) return true;
        if (this.gasHandler != null && this.isReadChannelExposed(ResourceType.GAS)) return true;
        if (this.essentiaHandler != null && this.isReadChannelExposed(ResourceType.ESSENTIA)) return true;

        return false;
    }

    private List<PartSubnetProxyFront> getParallelOwnOriginFronts() {
        IGrid origin = this.getBackGrid();
        if (origin == null) return Collections.singletonList(this);

        IGrid frontGrid = getFrontGridLive();
        if (frontGrid != null && origin == frontGrid) return Collections.singletonList(this);

        SubnetProxyGridCoordinator coord = getFrontGridCoordinator(frontGrid);
        if (coord == null) return Collections.singletonList(this);

        List<PartSubnetProxyFront> siblings = coord.getOwnOriginFronts(origin);
        return siblings.isEmpty() ? Collections.singletonList(this) : siblings;
    }

    /**
     * Build the visible filter surface for this handler's channel.
     * <p>
     * The elected representative for an origin-grid must union direct parallel
     * fronts that bridge the same origin into this front-grid, so visibility is
     * no longer limited to whichever front won election.
     */
    @Nullable
    public <T extends IAEStack<T>> Predicate<T> buildVisibleReadFilter(
            IStorageChannel<T> channel,
            @Nullable Predicate<T> ownFilter) {
        ResourceType type = channelToResourceType(channel, itemChannel(), fluidChannel());
        if (type == null) return ownFilter;
        if (!this.shouldExposeOwnOrigin()) return stack -> false;

        boolean ownEnabled = this.enabledChannels.contains(type);
        List<Predicate<T>> siblingFilters = null;

        for (PartSubnetProxyFront sibling : this.getParallelOwnOriginFronts()) {
            if (sibling == this) continue;
            if (!sibling.enabledChannels.contains(type)) continue;

            sibling.ensureFiltersCurrent();

            Predicate<T> siblingFilter = sibling.getReadChannelFilter(type);
            if (siblingFilter == null) return null;

            if (siblingFilters == null) siblingFilters = new ArrayList<>();

            siblingFilters.add(siblingFilter);
        }

        if (ownEnabled && ownFilter == null) return null;
        if (!ownEnabled && siblingFilters == null) return stack -> false;
        if (siblingFilters == null) return ownFilter;

        List<Predicate<T>> visibleSiblingFilters = siblingFilters;

        return stack -> {
            if (ownEnabled && (ownFilter == null || ownFilter.test(stack))) return true;

            for (Predicate<T> siblingFilter : visibleSiblingFilters) {
                if (siblingFilter.test(stack)) return true;
            }

            return false;
        };
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private <T extends IAEStack<T>> Predicate<T> getReadChannelFilter(ResourceType type) {
        switch (type) {
            case ITEM:
                return (Predicate<T>) this.itemHandler.getFilter();
            case FLUID:
                return (Predicate<T>) this.fluidHandler.getFilter();
            case GAS:
                return this.gasHandler != null ? (Predicate<T>) this.gasHandler.getFilter() : null;
            case ESSENTIA:
                return this.essentiaHandler != null ? (Predicate<T>) this.essentiaHandler.getFilter() : null;
            default:
                return null;
        }
    }

    /** Pack the enabled-channels set into a bitmask over {@link ResourceType#ordinal()}. */
    public int getEnabledChannelsBitmask() {
        int mask = 0;
        for (ResourceType t : this.enabledChannels) mask |= (1 << t.ordinal());
        return mask;
    }

    /**
     * Toggle whether a channel is exposed on the front-grid. Notifies the
     * front-grid that our cell array changed so it re-discovers (or drops)
     * the channel's contents immediately.
     */
    public void setChannelEnabled(ResourceType type, boolean enabled) {
        boolean was = this.enabledChannels.contains(type);
        if (was == enabled) return;

        if (enabled) this.enabledChannels.add(type);
        else this.enabledChannels.remove(type);

        this.markHostDirty();
        // Cell array on front-grid changed: this channel just appeared/disappeared.
        this.notifyGridOfChange();
    }

    /**
     * AE2's storage cache only exposes cell providers whose node is active.
     * Mirror that here so the proxy does not publish providers that Grid A's
     * own storage monitor has already dropped.
     */
    static boolean isActiveCellProviderNode(IGridNode node, IGridHost host) {
        if (!(host instanceof IActionHost)) return true;

        return node != null && node.isActive();
    }

    /**
     * Structural hash of what Grid B can currently see through this proxy.
     * The hash intentionally ignores item counts and unstable back-grid wrapper
     * identities. It tracks only the externally visible surface on Grid B:
     * exposed read channels, own-origin election, published peer fronts,
     * insertion exposure, and the bound back-grid identity.
     */
    private int computePublishedStructureHash() {
        int hash = 1;
        boolean ownOriginVisible = this.shouldExposeOwnOrigin();

        hash = 31 * hash + (ownOriginVisible ? 1 : 0);

        int readChannelsBitmask = 0;
        if (this.isReadChannelExposed(ResourceType.ITEM)) {
            readChannelsBitmask |= 1 << ResourceType.ITEM.ordinal();
        }

        if (this.isReadChannelExposed(ResourceType.FLUID)) {
            readChannelsBitmask |= 1 << ResourceType.FLUID.ordinal();
        }

        if (this.gasHandler != null && this.isReadChannelExposed(ResourceType.GAS)) {
            readChannelsBitmask |= 1 << ResourceType.GAS.ordinal();
        }

        if (this.essentiaHandler != null && this.isReadChannelExposed(ResourceType.ESSENTIA)) {
            readChannelsBitmask |= 1 << ResourceType.ESSENTIA.ordinal();
        }

        hash = 31 * hash + readChannelsBitmask;

        if (this.hasAnyReadChannelExposed()) {
            hash = 31 * hash + sortedIdentityHash(this.getPublishedPeerFronts());
        }

        if (this.insertionActive && !this.enabledChannels.isEmpty()) {
            hash = 31 * hash + this.getEnabledChannelsBitmask();
        }

        hash = 31 * hash + identityHash(this.gridA);

        return hash;
    }

    /**
     * Hash only the outer handler set that Grid B can discover via
     * {@link #getCellArray}. This is narrower than
     * {@link #computePublishedStructureHash()}: content-surface changes can be
     * handled as deltas without forcing Grid B to rebuild its cell array.
     */
    private int computePublishedCellArrayHash() {
        int hash = 1;
        boolean ownOriginVisible = this.shouldExposeOwnOrigin();

        hash = 31 * hash + (ownOriginVisible ? 1 : 0);

        int readChannelsBitmask = 0;
        if (this.isReadChannelExposed(ResourceType.ITEM)) {
            readChannelsBitmask |= 1 << ResourceType.ITEM.ordinal();
        }

        if (this.isReadChannelExposed(ResourceType.FLUID)) {
            readChannelsBitmask |= 1 << ResourceType.FLUID.ordinal();
        }

        if (this.gasHandler != null && this.isReadChannelExposed(ResourceType.GAS)) {
            readChannelsBitmask |= 1 << ResourceType.GAS.ordinal();
        }

        if (this.essentiaHandler != null && this.isReadChannelExposed(ResourceType.ESSENTIA)) {
            readChannelsBitmask |= 1 << ResourceType.ESSENTIA.ordinal();
        }

        hash = 31 * hash + readChannelsBitmask;

        if (this.insertionActive && !this.enabledChannels.isEmpty()) {
            hash = 31 * hash + this.getEnabledChannelsBitmask();
        }

        return hash;
    }

    /**
     * Peer fronts whose items are actually visible on this front-grid right now.
     * This mirrors the same front-grid + coordinator checks used by listing.
     */
    private List<PartSubnetProxyFront> getPublishedPeerFronts() {
        if (this.peerFronts.isEmpty()) return Collections.emptyList();

        IGrid frontGrid = getFrontGridLive();
        if (frontGrid == null) return Collections.emptyList();

        SubnetProxyGridCoordinator coord = getFrontGridCoordinator(frontGrid);
        if (coord == null) return Collections.emptyList();

        List<PartSubnetProxyFront> visiblePeers = new ArrayList<>();
        for (PartSubnetProxyFront peer : this.peerFronts) {
            IGrid peerOrigin = peer.getBackGrid();
            if (peerOrigin == null) continue;
            if (peerOrigin == frontGrid) continue;
            if (!coord.isElected(this, peerOrigin)) continue;

            visiblePeers.add(peer);
        }

        return visiblePeers;
    }

    private static int identityHash(@Nullable Object value) {
        return value == null ? 0 : System.identityHashCode(value);
    }

    private static int sortedIdentityHash(Iterable<?> values) {
        List<Integer> ids = new ArrayList<>();
        for (Object value : values) {
            if (value == null) continue;

            ids.add(System.identityHashCode(value));
        }

        if (ids.isEmpty()) return 0;

        Collections.sort(ids);

        int hash = 1;
        for (int id : ids) hash = 31 * hash + id;

        return hash;
    }

    // ========================= Config & Upgrade Access =========================

    public AppEngInternalInventory getConfigInventory() {
        return this.config;
    }

    public UpgradeInventory getUpgradeInventory() {
        return this.upgrades;
    }

    @Override
    public int getFilterSlots() {
        return Math.min(this.config.getSlots(), getTotalPages() * SLOTS_PER_PAGE);
    }

    @Override
    @Nonnull
    public ItemStack getFilter(int slot) {
        if (slot < 0 || slot >= getFilterSlots()) return ItemStack.EMPTY;

        return FilterHostUtil.normalizeFilter(this.config.getStackInSlot(slot));
    }

    @Override
    public void setFilter(int slot, @Nonnull ItemStack stack) {
        if (slot < 0 || slot >= getFilterSlots()) return;

        this.config.setStackInSlot(slot, FilterHostUtil.normalizeFilter(stack));
    }

    @Override
    public void clearFilters() {
        int slotCount = getFilterSlots();
        for (int slot = 0; slot < slotCount; slot++) {
            this.config.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    @Override
    @Nonnull
    public EnumFacing getPrimaryFacing() {
        AEPartLocation side = this.getSide();
        return side != null ? side.getFacing() : EnumFacing.NORTH;
    }

    public int getInstalledUpgrades(Upgrades u) {
        return this.upgrades.getInstalledUpgrades(u);
    }

    /**
     * Check if an Insertion Card is installed in the upgrade inventory.
     * Scans upgrade slots for an {@link ItemInsertionCard} instance.
     */
    public boolean hasInsertionCard() {
        for (int i = 0; i < this.upgrades.getSlots(); i++) {
            ItemStack stack = this.upgrades.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof ItemInsertionCard) return true;
        }

        return false;
    }

    // ========================= IPriorityHost =========================

    @Override
    public void setPriority(int newValue) {
        this.priority = newValue;
        this.markHostDirty();

        // Keep every channel handler's priority in sync with the part. AE2
        // re-sorts cells at the next MENetworkCellArrayUpdate, so we post
        // that event via notifyGridOfChange() below.
        this.itemHandler.setPriority(newValue);
        this.fluidHandler.setPriority(newValue);

        if (this.gasHandler != null && MekanismEnergisticsIntegration.isModLoaded()) {
            this.gasHandler.setPriority(newValue);
        }
        if (this.essentiaHandler != null && ThaumicEnergisticsIntegration.isModLoaded()) {
            this.essentiaHandler.setPriority(newValue);
        }

        // Insertion handlers also use this priority for AE2 routing.
        if (this.insertionActive) {
            this.itemInsertionHandler.setPriority(newValue);
            this.fluidInsertionHandler.setPriority(newValue);
            if (this.gasInsertionHandler != null && MekanismEnergisticsIntegration.isModLoaded()) {
                this.gasInsertionHandler.setPriority(newValue);
            }
            if (this.essentiaInsertionHandler != null && ThaumicEnergisticsIntegration.isModLoaded()) {
                this.essentiaInsertionHandler.setPriority(newValue);
            }
        }

        // Cell array on front-grid changed (priority is part of the cell identity).
        notifyGridOfChange();
    }

    @Override
    public ItemStack getItemStackRepresentation() {
        return AEApi.instance().definitions().parts().cableAnchor().maybeStack(1).orElse(ItemStack.EMPTY);
    }

    @Override
    public GuiBridge getGuiBridge() {
        // Returning null means the priority GUI won't show a "back" button.
        // The player closes the priority GUI with Escape and re-opens the proxy.
        // This is required because the proxy GUI uses CELLS' own GUI system,
        // not AE2's GuiBridge enum.
        return null;
    }

    @Override
    public IItemHandler getInventoryByName(final String name) {
        if ("config".equals(name)) return this.config;
        if ("upgrades".equals(name)) return this.upgrades;
        return null;
    }

    // ========================= Dual Proxy Lifecycle =========================

    @Override
    public void addToWorld() {
        super.addToWorld();
        this.sourcesDirty = true;
        this.filtersDirty = true;
        this.cachedHasBack = findBackPart() != null;

        // Wire insertion handlers if the card is already installed (NBT-loaded part).
        // Safe to call even if back-grid isn't ready yet; will be a no-op and
        // re-attempted via markSourcesDirty path when back becomes available.
        updateInsertionHandlers();

        this.lastPublishedStructureHash = this.computePublishedStructureHash();
        this.lastPublishedCellArrayHash = this.computePublishedCellArrayHash();
    }

    @Override
    public void onPlacement(EntityPlayer player, EnumHand hand, ItemStack held, AEPartLocation side) {
        super.onPlacement(player, hand, held, side);
        if (held.hasTagCompound()) {
            this.uploadSettings(SettingsFrom.DISMANTLE_ITEM, held.getTagCompound(), player);
        }

        TileEntity te = this.getHost() != null ? this.getHost().getTile() : null;
        if (te != null && te.getWorld() != null) {
            this.placedTick = te.getWorld().getTotalWorldTime();
        }
    }

    @Override
    public ItemStack getItemStack(PartItemStack type) {
        ItemStack stack = super.getItemStack(type);
        if (type == PartItemStack.WRENCH) {
            NBTTagCompound tag = this.downloadSettings(SettingsFrom.DISMANTLE_ITEM, new NBTTagCompound());
            if (!tag.isEmpty()) stack.setTagCompound(tag);
        }

        return stack;
    }

    @Override
    public void removeFromWorld() {
        unregisterGridAListeners();
        // Drop back-grid monitor reference held by insertion handlers
        this.itemInsertionHandler.clearMonitor();
        this.fluidInsertionHandler.clearMonitor();
        if (this.gasInsertionHandler != null && MekanismEnergisticsIntegration.isModLoaded()) {
            this.gasInsertionHandler.clearMonitor();
        }
        if (this.essentiaInsertionHandler != null && ThaumicEnergisticsIntegration.isModLoaded()) {
            this.essentiaInsertionHandler.clearMonitor();
        }
        this.insertionActive = false;
        // Drop coordinator registration (if any) so dead fronts don't linger
        // in election tables on whichever front-grid we last belonged to.
        if (this.currentFrontGridCoord != null) {
            this.currentFrontGridCoord.unregisterFront(this);
            this.currentFrontGridCoord = null;
        }
        this.currentFrontGrid = null;
        this.peerFronts = new ArrayList<>();
        this.exposedOrigins = new HashSet<>();
        super.removeFromWorld();
        this.cachedHasBack = false;
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);

        // Sparse config deserialization: only non-empty slots were written
        if (data.hasKey("config")) readSparseConfig(data.getCompoundTag("config"));

        this.upgrades.readFromNBT(data, "upgrades");
        this.currentPage = data.getInteger("currentPage");
        this.filterMode = ResourceType.fromOrdinal(data.getInteger("filterMode"));
        this.fuzzyMode = FuzzyMode.values()[Math.max(0, Math.min(
            data.getInteger("fuzzyMode"), FuzzyMode.values().length - 1))];
        this.priority = data.getInteger("priority");

        // Channel exposure bitmask. Missing key = empty set (default-off), which
        // matches the field's default for fresh parts placed before this NBT key existed.
        this.enabledChannels = EnumSet.noneOf(ResourceType.class);
        if (data.hasKey("enabledChannels")) {
            int mask = data.getInteger("enabledChannels");
            for (ResourceType t : ResourceType.values()) {
                if ((mask & (1 << t.ordinal())) != 0) this.enabledChannels.add(t);
            }
        }
        this.filtersDirty = true;
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);

        // Sparse config serialization: only write non-empty slots to avoid
        // 1.6k empty slot entries in NBT. Each entry is keyed by slot index.
        writeSparseConfig(data);

        this.upgrades.writeToNBT(data, "upgrades");
        // FIXME: should we persist the current page? It's only client-side state for the GUI.
        data.setInteger("currentPage", this.currentPage);
        data.setInteger("filterMode", this.filterMode.ordinal());
        data.setInteger("fuzzyMode", this.fuzzyMode.ordinal());
        data.setInteger("priority", this.priority);
        data.setInteger("enabledChannels", getEnabledChannelsBitmask());
    }

    /**
     * Write only non-empty config slots to NBT in a sparse format.
     * Each slot is stored as a sub-compound keyed by its index string.
     */
    private void writeSparseConfig(final NBTTagCompound data) {
        NBTTagCompound sparse = new NBTTagCompound();

        for (int i = 0; i < this.config.getSlots(); i++) {
            ItemStack stack = this.config.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            NBTTagCompound slotTag = new NBTTagCompound();
            stack.writeToNBT(slotTag);
            sparse.setTag(Integer.toString(i), slotTag);
        }

        data.setTag("config", sparse);
    }

    protected NBTTagCompound downloadSettings(final SettingsFrom from, final NBTTagCompound output) {
        output.setInteger("priority", this.priority);
        output.setInteger("filterMode", this.filterMode.ordinal());
        output.setInteger("fuzzyMode", this.fuzzyMode.ordinal());

        if (from != SettingsFrom.MEMORY_CARD) {
            output.setInteger("enabledChannels", getEnabledChannelsBitmask());
        }

        // Memory cards should only capture the pages that are currently usable,
        // not hidden overflow filters from removed capacity upgrades.
        int filterSlots = this.getFilterSlots();
        AppEngInternalInventory exportedConfig = new AppEngInternalInventory(null, filterSlots, 1);
        for (int slot = 0; slot < filterSlots; slot++) {
            ItemStack stack = this.config.getStackInSlot(slot);
            if (stack.isEmpty()) continue;

            exportedConfig.setStackInSlot(slot, stack.copy());
        }

        output.setTag("config", exportedConfig.serializeNBT());
        return output;
    }

    @Override
    public void uploadSettings(final SettingsFrom from, final NBTTagCompound compound, final EntityPlayer player) {
        if (compound == null) return;

        if (compound.hasKey("priority")) this.setPriority(compound.getInteger("priority"));

        if (compound.hasKey("filterMode")) {
            this.filterMode = ResourceType.fromOrdinal(compound.getInteger("filterMode"));
        }

        if (compound.hasKey("fuzzyMode")) {
            int fuzzyOrdinal = Math.max(0, Math.min(compound.getInteger("fuzzyMode"), FuzzyMode.values().length - 1));
            this.fuzzyMode = FuzzyMode.values()[fuzzyOrdinal];
        }

        if (from != SettingsFrom.MEMORY_CARD && compound.hasKey("enabledChannels")) {
            int mask = compound.getInteger("enabledChannels");
            EnumSet<ResourceType> importedChannels = EnumSet.noneOf(ResourceType.class);
            for (ResourceType type : ResourceType.values()) {
                if ((mask & (1 << type.ordinal())) != 0) importedChannels.add(type);
            }

            this.enabledChannels = importedChannels;
        }

        AppEngInternalInventory importedConfig = new AppEngInternalInventory(null, this.config.getSlots(), 1);
        importedConfig.readFromNBT(compound, "config");

        IAEAppEngInventory configOwner = this.config.getTileEntity();
        this.config.setTileEntity(null);

        if (from == SettingsFrom.MEMORY_CARD) {
            // Add new filters into the empty slots without clearing the existing config
            for (int slot = 0; slot < importedConfig.getSlots(); slot++) {
                ItemStack stack = importedConfig.getStackInSlot(slot);
                if (stack.isEmpty()) continue;

                FilterHostUtil.addFilter(this, stack);
            }
        } else {
            for (int slot = 0; slot < this.config.getSlots(); slot++) {
                ItemStack stack = slot < importedConfig.getSlots() ? importedConfig.getStackInSlot(slot) : ItemStack.EMPTY;
                this.config.setStackInSlot(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
            }
        }

        this.config.setTileEntity(configOwner);

        this.setCurrentPage(this.currentPage);
        this.filtersDirty = true;
        this.notifyGridOfChange();
        this.markHostDirty();

        this.markHostForUpdate();
    }

    /**
     * Read sparse config from NBT. Clears all slots first, then fills
     * only the slots that were serialized.
     */
    private void readSparseConfig(final NBTTagCompound sparse) {
        // Clear all slots
        for (int i = 0; i < this.config.getSlots(); i++) {
            this.config.setStackInSlot(i, ItemStack.EMPTY);
        }

        // Read non-empty slots
        for (String key : sparse.getKeySet()) {
            int slot;
            try {
                slot = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                continue;
            }

            if (slot < 0 || slot >= this.config.getSlots()) continue;

            ItemStack stack = new ItemStack(sparse.getCompoundTag(key));
            if (!stack.isEmpty()) this.config.setStackInSlot(slot, stack);
        }
    }

    // ========================= Network Events =========================

    /**
     * These subscribe to events from any grid the part's nodes belong to.
     * The inner proxy is on Grid B, so these fire for Grid B events.
     */
    @MENetworkEventSubscribe
    public void stateChange(final MENetworkChannelsChanged c) {
        if (this.getHost() == null) return;

        // When Grid B first assigns a channel (e.g. after initial placement),
        // re-discover Grid A's storage so it becomes visible immediately
        // without requiring a world reload or cell array change.
        this.sourcesDirty = true;
        this.notifyGridOfChange();
        this.markHostForUpdate();
    }

    @MENetworkEventSubscribe
    public void stateChange(final MENetworkPowerStatusChange c) {
        this.markHostForUpdate();
    }

    // ========================= Neighbor Updates =========================

    @Override
    public void onNeighborChanged(IBlockAccess w, BlockPos pos, BlockPos neighbor) {
        // TODO: could exist early if neighbor != adjacentPos
        boolean hasBack = findBackPart() != null;

        if (hasBack != this.cachedHasBack) {
            this.cachedHasBack = hasBack;
            this.markHostForUpdate();
        }
    }

    // ========================= LED State from Outer Proxy =========================

    private int computeStateFlags() {
        int flags = 0;
        if (PowerStateHelper.isPowered(this.getProxy())) flags |= POWERED_FLAG;
        if (PowerStateHelper.hasChannel(this.getProxy())) flags |= CHANNEL_FLAG;
        if (this.cachedHasBack) flags |= BOTH_PARTS_FLAG;

        return flags;
    }

    /**
     * WAILA/TOP may query power state on the logical server, so the display
     * status must not rely solely on the client-side stream cache.
     */
    private int getStateFlags() {
        TileEntity hostTile = this.getHost() != null ? this.getHost().getTile() : null;
        World hostWorld = hostTile != null ? hostTile.getWorld() : null;
        if (hostWorld != null && !hostWorld.isRemote) return this.computeStateFlags();

        return this.clientFlags;
    }

    // ========================= Memory card handling =========================

    @Override
    public boolean useStandardMemoryCard() {
        return false;
    }

    protected boolean useMemoryCard(final EntityPlayer player) {
        final ItemStack memCardIS = player.inventory.getCurrentItem();
        if (memCardIS.isEmpty()) return false;
        if (!(memCardIS.getItem() instanceof IMemoryCard)) return false;

        final IMemoryCard memoryCard = (IMemoryCard) memCardIS.getItem();
        final String name = SubnetProxyMemoryCardHelper.getMemoryCardName();

        if (player.isSneaking()) {
            final NBTTagCompound data = this.downloadSettings(SettingsFrom.MEMORY_CARD, new NBTTagCompound());
            if (!data.isEmpty()) {
                memoryCard.setMemoryCardContents(memCardIS, name, data);
                memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_SAVED);
            }
        } else {
            final NBTTagCompound data = SubnetProxyMemoryCardHelper.prepareUploadData(
                memoryCard.getSettingsName(memCardIS),
                memoryCard.getData(memCardIS)
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

    @Override
    public void writeToStream(final ByteBuf data) throws IOException {
        int flags = this.computeStateFlags();
        this.clientFlags = flags;

        data.writeByte((byte) flags);
    }

    @Override
    public boolean readFromStream(final ByteBuf data) throws IOException {
        final int old = this.clientFlags;
        this.clientFlags = data.readByte();
        return old != this.clientFlags;
    }

    @Override
    public boolean isPowered() {
        return (this.getStateFlags() & POWERED_FLAG) == POWERED_FLAG;
    }

    @Override
    public boolean isActive() {
        int flags = this.getStateFlags();

        return (flags & CHANNEL_FLAG) == CHANNEL_FLAG
            && (flags & BOTH_PARTS_FLAG) == BOTH_PARTS_FLAG;
    }

    // ========================= ICellContainer =========================

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public List<IMEInventoryHandler> getCellArray(final IStorageChannel channel) {
        // Only rebuild when invalidated by grid events or config changes
        this.ensureSourcesCurrent();
        if (this.filtersDirty) this.rebuildFilters();

        IItemStorageChannel itemCh = itemChannel();
        IFluidStorageChannel fluidCh = fluidChannel();

        // Channel-exposure gate: disabled channels are entirely hidden from the
        // front-grid (no read view, no insertion). The proxy effectively does
        // not exist on that channel until the user toggles it on. Listener-side
        // gating in GridAListener.postChange suppresses delta forwarding too.
        ResourceType requestedType = channelToResourceType(channel, itemCh, fluidCh);
        if (requestedType != null
                && !this.enabledChannels.contains(requestedType)
                && !this.isReadChannelExposed(requestedType)) {
            return Collections.emptyList();
        }

        if (channel == itemCh) {
            // Combine read-side handler with insertion handler when active.
            // Both are exposed on the front-grid: the read handler shows back-grid
            // items (filtered) for extraction; the insertion handler accepts items
            // matching the same filter and forwards them to back-grid storage.
            List<IMEInventoryHandler> read = this.isReadChannelExposed(ResourceType.ITEM)
                ? this.itemHandler.asCellArray()
                : Collections.emptyList();
            if (!this.insertionActive || !this.enabledChannels.contains(ResourceType.ITEM)) return read;

            List<IMEInventoryHandler> insert = this.itemInsertionHandler.asCellArray();
            if (insert.isEmpty()) return read;
            if (read.isEmpty()) return insert;

            List<IMEInventoryHandler> combined = new ArrayList<>(read.size() + insert.size());
            combined.addAll(read);
            combined.addAll(insert);
            return combined;
        }

        if (channel == fluidCh) {
            List<IMEInventoryHandler> read = this.isReadChannelExposed(ResourceType.FLUID)
                ? this.fluidHandler.asCellArray()
                : Collections.emptyList();
            if (!this.insertionActive || !this.enabledChannels.contains(ResourceType.FLUID)) return read;

            List<IMEInventoryHandler> insert = this.fluidInsertionHandler.asCellArray();
            if (insert.isEmpty()) return read;
            if (read.isEmpty()) return insert;

            List<IMEInventoryHandler> combined = new ArrayList<>(read.size() + insert.size());
            combined.addAll(read);
            combined.addAll(insert);
            return combined;
        }

        // Gas channel (MekanismEnergistics)
        if (this.gasHandler != null && MekanismEnergisticsIntegration.isModLoaded()) {
            List<IMEInventoryHandler> result = this.isReadChannelExposed(ResourceType.GAS)
                ? SubnetProxyGasHelper.asCellArray(this.gasHandler, channel)
                : Collections.emptyList();
            if (!result.isEmpty()) {
                if (!this.insertionActive
                        || !this.enabledChannels.contains(ResourceType.GAS)
                        || this.gasInsertionHandler == null) return result;
                List<IMEInventoryHandler> insert = this.gasInsertionHandler.asCellArray();
                if (insert.isEmpty()) return result;
                List<IMEInventoryHandler> combined = new ArrayList<>(result.size() + insert.size());
                combined.addAll(result);
                combined.addAll(insert);
                return combined;
            }

            if (this.insertionActive
                    && this.enabledChannels.contains(ResourceType.GAS)
                    && this.gasInsertionHandler != null) {
                return this.gasInsertionHandler.asCellArray();
            }
        }

        // Essentia channel (ThaumicEnergistics)
        if (this.essentiaHandler != null && ThaumicEnergisticsIntegration.isModLoaded()) {
            List<IMEInventoryHandler> result = this.isReadChannelExposed(ResourceType.ESSENTIA)
                ? SubnetProxyEssentiaHelper.asCellArray(this.essentiaHandler, channel)
                : Collections.emptyList();
            if (!result.isEmpty()) {
                if (!this.insertionActive
                        || !this.enabledChannels.contains(ResourceType.ESSENTIA)
                        || this.essentiaInsertionHandler == null) return result;
                List<IMEInventoryHandler> insert = this.essentiaInsertionHandler.asCellArray();
                if (insert.isEmpty()) return result;
                List<IMEInventoryHandler> combined = new ArrayList<>(result.size() + insert.size());
                combined.addAll(result);
                combined.addAll(insert);
                return combined;
            }

            if (this.insertionActive
                    && this.enabledChannels.contains(ResourceType.ESSENTIA)
                    && this.essentiaInsertionHandler != null) {
                return this.essentiaInsertionHandler.asCellArray();
            }
        }

        return Collections.emptyList();
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    @Override
    public void blinkCell(int slot) {
        // No visual blinking for passthrough
    }

    // ISaveProvider (from ICellContainer)
    @Override
    public void saveChanges(@Nullable ICellInventory<?> cellInventory) {
        this.markHostDirty();
    }

    private void markHostDirty() {
        TileEntity te = this.getHost() != null ? this.getHost().getTile() : null;
        if (te == null) return;

        World w = te.getWorld();
        //noinspection ConstantValue
        if (w == null || w.isRemote) return;

        // Debounce to once per tick
        long currentTick = w.getTotalWorldTime();
        if (this.lastSaveTick == currentTick) return;
        this.lastSaveTick = currentTick;

        w.markChunkDirty(te.getPos(), te);
    }

    private void markHostForUpdate() {
        IPartHost host = this.getHost();
        if (host == null) return;

        host.markForUpdate();
    }

    // IAEAppEngInventory (for config/upgrades inventory change callbacks)
    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removed, ItemStack added) {
        // When upgrades change, the number of pages may change
        if (inv == this.upgrades) {
            // Clamp current page if capacity cards removed
            setCurrentPage(this.currentPage);
            this.filtersDirty = true;
            this.notifyGridOfChange();

            // If the insertion card was added or removed, rewire local insertion
            // handlers (their monitor points at back-grid). The cell array on
            // front-grid will reflect the new state via notifyGridOfChange below.
            boolean removedInsertion = !removed.isEmpty() && removed.getItem() instanceof ItemInsertionCard;
            boolean addedInsertion = !added.isEmpty() && added.getItem() instanceof ItemInsertionCard;
            if (removedInsertion || addedInsertion) {
                updateInsertionHandlers();
            }
        }

        // When config changes, filters need rebuilding
        if (inv == this.config) {
            this.filtersDirty = true;
            this.notifyGridOfChange();
        }

        this.markHostDirty();
        this.markHostForUpdate();
    }

    /** Guard against recursive markSourcesDirty → notifyGridOfChange → cellUpdate → markSourcesDirty loops */
    private boolean inMarkSourcesDirty = false;

    /**
     * Called by the back part when Grid A fires {@link MENetworkCellArrayUpdate}.
     * Refreshes the passthrough sources (local cell handlers, peer set,
     * Grid A listener subscriptions) and the insertion-handler wiring.
     * <p>
     * <b>Performance note (steady state):</b> Grid A's cell-array updates are
     * frequent (storage bus rebuilds, drive ejections, etc.). We must NOT
     * unconditionally post {@link MENetworkCellArrayUpdate} on Grid B in
     * response, because that forces AE2 to call {@code getAvailableItems()}
     * on every cell handler on Grid B, which the front-part chain then
     * cascades downward via {@code snapshotDiffAndForward()}. The cascade
     * dominates the server tick (see spark profile UyNgCh4lFT).
     * <p>
     * Content changes on Grid A reach Grid B via the {@link GridAListener}
     * postChange path directly (filtered + forwarded as deltas), so a Grid B
     * cell-array refresh would be redundant. The set of handlers we publish
    * via {@link #getCellArray} is structurally invariant w.r.t. normal Grid A
    * content changes, but back-grid topology changes can still alter the
    * published surface: local provider membership, listener bindings,
    * coordinator election, and {@link #insertionActive}. Only those
    * structural changes warrant a Grid B notify.
     * <p>
     * Sources are refreshed eagerly (not deferred via the {@code sourcesDirty}
     * flag) precisely because we no longer rely on a Grid B cell-array
     * refresh to drive a {@link #getCellArray()} call back into
     * {@link #updatePassthroughSources}; without that pull, Grid A listener
     * subscriptions would go stale.
     * <p>
     * Guarded against recursion: even though we no longer notify on every
     * call, {@link #updatePassthroughSources} touches grid caches and the
     * insertion-active flip path may still fan out.
     */
    public void markSourcesDirty() {
        if (this.inMarkSourcesDirty) return;

        this.pendingMonitorResetReconcile = false;
        this.deltasDirty = false;
        this.pendingForcedGridBRefresh = false;
        this.inMarkSourcesDirty = true;
        try {
            int previousStructureHash = this.lastPublishedStructureHash;
            int previousCellArrayHash = this.lastPublishedCellArrayHash;

            // Eagerly rebuild local cells / peers / Grid A listeners. Without
            // a Grid B notify (see javadoc), there is no later getCellArray()
            // pull that would lazily rebuild via the sourcesDirty flag.
            // Preserve the existing snapshots as the diff baseline. Storage
            // buses can emit a follow-up direct delta for the same rebuild,
            // and keeping the old baseline lets that immediate delta collapse
            // naturally before the deferred reconcile runs.
            this.lastSourceDirtyTick = this.getObservedWorldTick();
            this.sourcesDirty = true;
            updatePassthroughSources(false);
            // Back-grid topology may have changed; re-check insertion wiring
            // (handles back part appearing/disappearing or its grid changing).
            updateInsertionHandlers();

            // Only notify Grid B when the structural surface it sees actually
            // changed. Content changes still flow through monitor deltas, but
            // source, election, listener, and insertion-topology changes do not.
            int currentStructureHash = this.computePublishedStructureHash();
            int currentCellArrayHash = this.computePublishedCellArrayHash();
            this.lastPublishedStructureHash = currentStructureHash;
            this.lastPublishedCellArrayHash = currentCellArrayHash;
            if (currentCellArrayHash != previousCellArrayHash) {
                this.refreshAllSnapshots();
                this.notifyGridOfChange();
                return;
            }

            // if (currentStructureHash != previousStructureHash)
            // Published visibility changed, but Grid B's discoverable
            // handler set did not. Let the deferred snapshot reconcile
            // publish the content delta without forcing a cell-array rebuild.

            this.pendingMonitorResetReconcile = true;
            this.deltasDirty = true;
            this.alertGridBTick();
        } finally {
            this.inMarkSourcesDirty = false;
        }
    }

    // ========================= Passthrough Logic =========================

    /**
     * Find the back part in the adjacent block facing this front part.
     * <p>
     * Layout: [Grid B cable + front(EAST)] | [back(WEST) + Grid A cable]
     * The parts face each other across the block boundary, like a storage
     * bus on an interface. The front faces EAST, the back faces WEST.
     */
    public PartSubnetProxyBack findBackPart() {
        TileEntity selfTile = this.getHost() != null ? this.getHost().getTile() : null;
        if (selfTile == null || selfTile.getWorld() == null) return null;

        AEPartLocation side = this.getSide();
        if (side == null) return null;

        // The back part is in the adjacent block in our facing direction,
        // on the opposite side (facing back toward us).
        EnumFacing facing = side.getFacing();
        BlockPos adjacentPos = selfTile.getPos().offset(facing);
        TileEntity adjacentTile = selfTile.getWorld().getTileEntity(adjacentPos);
        if (!(adjacentTile instanceof IPartHost)) return null;

        IPart candidate = ((IPartHost) adjacentTile).getPart(AEPartLocation.fromFacing(facing.getOpposite()));
        if (candidate instanceof PartSubnetProxyBack) return (PartSubnetProxyBack) candidate;

        return null;
    }

    /**
     * Rebuild the source cell handlers on the passthrough handlers.
     * <p>
     * For anti-looping logic, see {@link SubnetProxyInventoryHandler}.
     * <p>
     * Only called when {@code sourcesDirty} is true (Grid A cell array update
     * forwarded by the back part, or initial placement). Cost: O(nodes) per
     * invocation, amortized to O(1) between grid events.
     */
    @SuppressWarnings("unchecked")
    private void updatePassthroughSources() {
        this.updatePassthroughSources(true);
    }

    @SuppressWarnings("unchecked")
    private void updatePassthroughSources(boolean refreshSnapshots) {
        this.sourcesDirty = false;
        this.rebuildingPassthroughSources = true;

        try {
            // Detach stale listeners first, but keep the last known back-grid
            // identity until we know whether this refresh found a replacement.
            unregisterGridAListeners(false);

            PartSubnetProxyBack back = findBackPart();
            if (back == null) {
                this.gridA = null;
                this.knownLocalProviderHosts = Collections.emptySet();

                this.itemHandler.clearSources();
                this.fluidHandler.clearSources();
                if (this.gasHandler != null) this.gasHandler.clearSources();
                if (this.essentiaHandler != null) this.essentiaHandler.clearSources();

                if (refreshSnapshots) {
                    // Reset snapshots so next connection starts clean
                    this.clearPassthroughSnapshots();
                }

                // No back: drop peers/origins/coord. Election on whichever
                // front-grid we previously belonged to must drop us so other
                // fronts can take over publication of any origins we held.
                this.peerFronts = new ArrayList<>();
                this.exposedOrigins = new HashSet<>();
                refreshCoordinatorRegistration();

                return;
            }

            try {
                IGrid gridA = back.getProxy().getGrid();
                IStorageGrid sg = gridA.getCache(IStorageGrid.class);
                this.gridA = gridA;

                IItemStorageChannel itemChannel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
                IFluidStorageChannel fluidChannel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);

                // Collect cell handlers from non-passthrough providers only.
                // Also collect peer subnet-proxy fronts on the back-grid: their
                // back-grids become candidate origins we may publish on our
                // front-grid (subject to election in the front-grid coordinator).
                List<IMEInventoryHandler<IAEItemStack>> localItemCells = new ArrayList<>();
                List<IMEInventoryHandler<IAEFluidStack>> localFluidCells = new ArrayList<>();
                List<PartSubnetProxyFront> newPeers = new ArrayList<>();
                Set<IActionHost> newKnownLocalProviders = Collections.newSetFromMap(new IdentityHashMap<>());

                for (IGridNode node : gridA.getNodes()) {
                    IGridHost host = node.getMachine();
                    if (!isActiveCellProviderNode(node, host)) continue;
                    if (!(host instanceof ICellProvider)) continue;

                    // Skip our own proxy to prevent proxy-to-proxy chains.
                    // Other PartSubnetProxyFront on this back-grid are PEERS:
                    // their back-grids become candidate origins for us to expose,
                    // but they MUST be excluded from local-cell collection (their
                    // ICellContainer would otherwise add their items here doubled).
                    if (host instanceof PartSubnetProxyFront) {
                        PartSubnetProxyFront peer = (PartSubnetProxyFront) host;
                        if (peer != this) newPeers.add(peer);
                        continue;
                    }

                    // Skip back parts: their insertion handlers are write-only wrappers
                    // for Grid B's storage, not local storage on Grid A.
                    if (host instanceof PartSubnetProxyBack) continue;

                    // For cable-bus parts (storage buses and similar), check if their
                    // target tile provides STORAGE_MONITORABLE_ACCESSOR. If so, the
                    // part is bridging to another ME network (i.e. subnet passthrough)
                    // and must be excluded to prevent loop inflation.
                    if (isPassthroughBusStatic(host)) continue;

                    if (host instanceof IActionHost) {
                        newKnownLocalProviders.add((IActionHost) host);
                    }

                    ICellProvider provider = (ICellProvider) host;

                    for (IMEInventoryHandler<?> h : provider.getCellArray(itemChannel)) {
                        localItemCells.add((IMEInventoryHandler<IAEItemStack>) h);
                    }

                    for (IMEInventoryHandler<?> h : provider.getCellArray(fluidChannel)) {
                        localFluidCells.add((IMEInventoryHandler<IAEFluidStack>) h);
                    }
                }

                // Publish updated peer/origin sets BEFORE refreshing the coordinator
                // so election sees the new exposed-origins set.
                this.peerFronts = newPeers;
                this.exposedOrigins = computeExposedOrigins(gridA, newPeers);
                this.knownLocalProviderHosts = newKnownLocalProviders;
                refreshCoordinatorRegistration();

                this.itemHandler.setLocalCells(localItemCells);
                this.fluidHandler.setLocalCells(localFluidCells);

                // Gas channel (MekanismEnergistics)
                if (this.gasHandler != null && MekanismEnergisticsIntegration.isModLoaded()) {
                    SubnetProxyGasHelper.updateSources(this.gasHandler, gridA, sg);
                }

                // Essentia channel (ThaumicEnergistics)
                if (this.essentiaHandler != null && ThaumicEnergisticsIntegration.isModLoaded()) {
                    SubnetProxyEssentiaHelper.updateSources(this.essentiaHandler, gridA, sg);
                }

                // Register on Grid A's monitors for immediate delta forwarding.
                // The listener checks source grid membership and forwards local
                // changes directly to Grid B (no deferred snapshot diff).
                registerGridAListeners(gridA, sg.getInventory(itemChannel), sg.getInventory(fluidChannel));

                // Gas/essentia listeners
                if (this.gasHandler != null && MekanismEnergisticsIntegration.isModLoaded()) {
                    SubnetProxyGasHelper.registerListener(this.gasHandler, sg, this.gridAListener, this.listenerToken);
                }
                if (this.essentiaHandler != null && ThaumicEnergisticsIntegration.isModLoaded()) {
                    SubnetProxyEssentiaHelper.registerListener(this.essentiaHandler, sg, this.gridAListener, this.listenerToken);
                }

                if (refreshSnapshots) refreshAllSnapshots();
            } catch (final GridAccessException e) {
                this.gridA = null;
                this.knownLocalProviderHosts = Collections.emptySet();

                this.itemHandler.clearSources();
                this.fluidHandler.clearSources();
                if (this.gasHandler != null) this.gasHandler.clearSources();
                if (this.essentiaHandler != null) this.essentiaHandler.clearSources();

                if (refreshSnapshots) {
                    this.clearPassthroughSnapshots();
                }

                // Back-grid unavailable: also drop peer/origin set & coord
                this.peerFronts = new ArrayList<>();
                this.exposedOrigins = new HashSet<>();
                refreshCoordinatorRegistration();
            }
        } finally {
            this.rebuildingPassthroughSources = false;

            // When this refresh happens as part of a Grid B rebuild pull,
            // there will be no later structural notify from markSourcesDirty.
            if (!this.inMarkSourcesDirty) {
                this.lastPublishedStructureHash = this.computePublishedStructureHash();
                this.lastPublishedCellArrayHash = this.computePublishedCellArrayHash();
            }
        }
    }

    /** Take a snapshot of the handler's current listing for delta comparison. */
    private <T extends IAEStack<T>> void takeSnapshot(
            SubnetProxyInventoryHandler<T> handler,
            IStorageChannel<T> channel) {
        IItemList<T> snapshot = channel.createList();
        handler.getAvailableItems(snapshot);
        handler.setLastSnapshot(snapshot);
    }

    /**
     * Raw-typed bridge to {@link #takeSnapshot} for gas/essentia handlers
     * (kept raw at the field decl to avoid loading mod-gated classes).
     * Cast safety: each handler is built with its matching channel by the
     * corresponding helper.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void takeSnapshotRaw(SubnetProxyInventoryHandler handler, IStorageChannel channel) {
        takeSnapshot(handler, channel);
    }

    private void refreshAllSnapshots() {
        takeSnapshot(this.itemHandler, itemChannel());
        takeSnapshot(this.fluidHandler, fluidChannel());

        if (this.gasHandler != null && MekanismEnergisticsIntegration.isModLoaded()) {
            takeSnapshotRaw(this.gasHandler, SubnetProxyGasHelper.getChannel());
        }

        if (this.essentiaHandler != null && ThaumicEnergisticsIntegration.isModLoaded()) {
            takeSnapshotRaw(this.essentiaHandler, SubnetProxyEssentiaHelper.getChannel());
        }
    }

    /**
     * Checks if a grid host is a cable-bus part (storage bus or similar) whose
     * target tile entity provides {@code Capabilities.STORAGE_MONITORABLE_ACCESSOR}.
     * <p>
     * This capability is the AE2 standard for exposing a grid's full storage to
     * an adjacent block. It is provided by:
     * <ul>
     *   <li>{@code DualityInterface} / {@code DualityFluidInterface}: ME Interfaces</li>
     *   <li>{@code DualityGasInterface}: MekanismEnergistics gas interfaces</li>
     *   <li>{@code TileChest}: ME Chests (but their items are already on the
     *       grid via their own ICellContainer, so excluding them is harmless)</li>
     * </ul>
     * Any storage bus whose target has this capability is a subnet passthrough
     * and should be excluded from the proxy's listing view.
     *
     * @return true if the host is a passthrough bus that should be skipped
     */
    static boolean isPassthroughBusStatic(IGridHost host) {
        if (!(host instanceof AEBasePart)) return false;

        AEBasePart part = (AEBasePart) host;
        IPartHost partHost = part.getHost();
        AEPartLocation side = part.getSide();
        if (partHost == null || side == null) return false;

        TileEntity selfTile = partHost.getTile();
        if (selfTile == null || selfTile.getWorld() == null) return false;

        EnumFacing facing = side.getFacing();
        BlockPos targetPos = selfTile.getPos().offset(facing);
        TileEntity target = selfTile.getWorld().getTileEntity(targetPos);

        return target != null
            && target.hasCapability(Capabilities.STORAGE_MONITORABLE_ACCESSOR, facing.getOpposite());
    }

    /**
     * Rebuild the filter sets and predicates on the passthrough handlers
     * based on the current config inventory content.
     * <p>
     * Only called when {@code filtersDirty} is true (config/upgrade change).
     * Uses AE2's native {@link IItemList#findPrecise} for precise matching,
     * which leverages interned {@code AESharedItemStack} reference lookups
     * (identity hash, zero NBT cost). Fuzzy matching uses a {@code Map<Item,
     * List<IAEItemStack>>} index for O(1) candidate lookup + damage-range
     * comparisons.
     */
    private void rebuildFilters() {
        this.filtersDirty = false;

        this.cachedHasInverter = this.upgrades.getInstalledUpgrades(Upgrades.INVERTER) > 0;
        this.cachedHasFuzzy = this.upgrades.getInstalledUpgrades(Upgrades.FUZZY) > 0;
        FuzzyMode fuzzyMode = this.fuzzyMode;

        final boolean hasInverter = this.cachedHasInverter;
        final boolean hasFuzzy = this.cachedHasFuzzy;

        // Collect all non-empty filter entries across all accessible pages
        int totalSlots = Math.min(getTotalPages() * SLOTS_PER_PAGE, this.config.getSlots());

        // Use AE2's native IItemList for precise matching. ItemList uses
        // Reference2ObjectOpenHashMap keyed by interned AESharedItemStack,
        // so findPrecise() is an identity-hash lookup with 0 NBT cost.
        IItemList<IAEItemStack> itemFilterList = AEApi.instance().storage()
            .getStorageChannel(IItemStorageChannel.class).createList();
        IItemList<IAEFluidStack> fluidFilterList = AEApi.instance().storage()
            .getStorageChannel(IFluidStorageChannel.class).createList();
        boolean hasItemFilters = false;
        boolean hasFluidFilters = false;

        // For fuzzy fluid matching, we index by Fluid to ignore NBT.
        // AEFluidStack.fuzzyComparison() only checks fluid type identity,
        // so a Set<Fluid> gives us O(1) lookup with the same semantics.
        Set<Fluid> fuzzyFluidIndex = hasFuzzy ? new HashSet<>() : null;

        // For fuzzy item matching, we index filters by Item to avoid O(F) per-item scans.
        // Only the filters for the matching Item need to be checked per incoming stack.
        Map<Item, List<IAEItemStack>> fuzzyItemIndex = hasFuzzy ? new HashMap<>() : null;

        for (int i = 0; i < totalSlots; i++) {
            ItemStack stack = this.config.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            if (stack.getItem() instanceof FluidDummyItem) {
                IAEFluidStack aeFluid = AEFluidStack.fromFluidStack(
                    ((FluidDummyItem) stack.getItem()).getFluidStack(stack));
                if (aeFluid != null) {
                    fluidFilterList.add(aeFluid);
                    hasFluidFilters = true;

                    // Index by Fluid type for fuzzy matching (ignores NBT)
                    if (hasFuzzy) fuzzyFluidIndex.add(aeFluid.getFluid());
                }
            } else {
                IAEItemStack aeFilter = AEItemStack.fromItemStack(stack);
                if (aeFilter != null) {
                    itemFilterList.add(aeFilter);
                    hasItemFilters = true;

                    if (hasFuzzy) {
                        // Index by Item for O(1) lookup of candidate filters per incoming stack
                        fuzzyItemIndex.computeIfAbsent(stack.getItem(), k -> new ArrayList<>()).add(aeFilter);
                    }
                }
            }
        }

        // Build item filter predicate
        if (!hasItemFilters && !hasInverter) {
            // No filters + whitelist = pass everything
            this.itemHandler.setFilter(null);
        } else if (hasFuzzy && fuzzyItemIndex != null && !fuzzyItemIndex.isEmpty()) {
            // Fuzzy matching: look up candidate filters by Item, then check damage range.
            // O(1) map lookup + O(K) fuzzy checks where K = filters for the same Item.
            // There should not be many filters per Item, except with metadata-items (Thermal Expansion materials, etc.)
            this.itemHandler.setFilter(aeStack -> {
                if (aeStack == null) return false;

                Item item = aeStack.getDefinition().getItem();
                List<IAEItemStack> candidates = fuzzyItemIndex.get(item);

                boolean matchesAny = false;
                if (candidates != null) {
                    for (IAEItemStack aeFilter : candidates) {
                        if (aeStack.fuzzyComparison(aeFilter, fuzzyMode)) {
                            matchesAny = true;
                            break;
                        }
                    }
                }

                return hasInverter != matchesAny;
            });
        } else {
            // Precise matching: O(1) per item via IItemList.findPrecise(), which uses
            // interned AESharedItemStack identity lookups (zero NBT hashing).
            this.itemHandler.setFilter(aeStack -> {
                if (aeStack == null) return false;

                boolean matchesAny = itemFilterList.findPrecise(aeStack) != null;

                return hasInverter != matchesAny;
            });
        }

        // Build fluid filter predicate.
        // Precise mode: findPrecise() checks both fluid type AND NBT (via equals+hashCode).
        // Fuzzy mode: matches by fluid type only, ignoring NBT. Aligns with AE2's
        // PartFluidStorageBus which uses FuzzyPriorityList with fuzzyComparison().
        if (!hasFluidFilters && !hasInverter) {
            this.fluidHandler.setFilter(null);
        } else if (hasFuzzy && fuzzyFluidIndex != null && !fuzzyFluidIndex.isEmpty()) {
            // Fuzzy matching: check fluid type only (ignoring NBT),
            // matching AEFluidStack.fuzzyComparison() semantics.
            this.fluidHandler.setFilter(aeStack -> {
                if (aeStack == null) return false;

                boolean matchesAny = fuzzyFluidIndex.contains(aeStack.getFluid());

                return hasInverter != matchesAny;
            });
        } else {
            // Precise matching: findPrecise() checks fluid type + NBT.
            this.fluidHandler.setFilter(aeStack -> {
                if (aeStack == null) return false;

                boolean matchesAny = fluidFilterList.findPrecise(aeStack) != null;

                return hasInverter != matchesAny;
            });
        }

        // Build gas filter predicate (MekanismEnergistics)
        if (this.gasHandler != null && MekanismEnergisticsIntegration.isModLoaded()) {
            SubnetProxyGasHelper.rebuildFilter(this.gasHandler, this.config, totalSlots, hasInverter);
        }

        // Build essentia filter predicate (ThaumicEnergistics)
        if (this.essentiaHandler != null && ThaumicEnergisticsIntegration.isModLoaded()) {
            SubnetProxyEssentiaHelper.rebuildFilter(this.essentiaHandler, this.config, totalSlots, hasInverter);
        }

        // Insertion handlers share the read-side filter; refresh after each rebuild.
        // Cheap: just re-grabs the predicate references.
        if (this.insertionActive) {
            this.itemInsertionHandler.setFilter(this.itemHandler.getFilter());
            this.fluidInsertionHandler.setFilter(this.fluidHandler.getFilter());
        }
    }

    // ========================= Insertion Card Support (front-grid → back-grid) =========================

    /**
     * (Re)wire the insertion handlers based on the current upgrade-slot state.
     * <p>
     * When the Insertion Card is installed AND a back part is present AND its
     * grid is reachable, the insertion handlers' monitors are pointed at the
     * BACK-grid's storage. Items on the front-grid that match the proxy's
     * filter and are routed to the insertion handler will be injected into
     * the back-grid's storage, the reverse direction from the read view.
     * <p>
     * If any prerequisite fails, the handlers are deactivated (cleared monitor)
     * and {@link #insertionActive} is set to false so {@link #getCellArray} stops
     * including them.
     * <p>
     * Filters are sourced from the read-side handlers ({@link #itemHandler},
     * {@link #fluidHandler}); call this method again after {@link #rebuildFilters}
     * if filters may have changed.
     */
    private void updateInsertionHandlers() {
        // Ensure read-side filters are current (they're shared with insertion)
        if (this.filtersDirty) rebuildFilters();

        boolean shouldBeActive = hasInsertionCard();

        if (!shouldBeActive) {
            if (this.insertionActive) {
                this.itemInsertionHandler.clearMonitor();
                this.fluidInsertionHandler.clearMonitor();
                if (this.gasInsertionHandler != null && MekanismEnergisticsIntegration.isModLoaded()) {
                    this.gasInsertionHandler.clearMonitor();
                }
                if (this.essentiaInsertionHandler != null && ThaumicEnergisticsIntegration.isModLoaded()) {
                    this.essentiaInsertionHandler.clearMonitor();
                }
                this.insertionActive = false;
            }
            return;
        }

        // Need the back part's grid to wire the monitors
        PartSubnetProxyBack back = findBackPart();
        if (back == null) {
            if (this.insertionActive) {
                this.itemInsertionHandler.clearMonitor();
                this.fluidInsertionHandler.clearMonitor();
                if (this.gasInsertionHandler != null && MekanismEnergisticsIntegration.isModLoaded()) {
                    this.gasInsertionHandler.clearMonitor();
                }
                if (this.essentiaInsertionHandler != null && ThaumicEnergisticsIntegration.isModLoaded()) {
                    this.essentiaInsertionHandler.clearMonitor();
                }
                this.insertionActive = false;
            }
            return;
        }

        try {
            IGrid backGrid = back.getProxy().getGrid();
            IStorageGrid backStorage = backGrid.getCache(IStorageGrid.class);

            IItemStorageChannel itemChannel = AEApi.instance().storage()
                .getStorageChannel(IItemStorageChannel.class);
            IFluidStorageChannel fluidChannel = AEApi.instance().storage()
                .getStorageChannel(IFluidStorageChannel.class);

            this.itemInsertionHandler.setMonitor(backStorage.getInventory(itemChannel));
            this.itemInsertionHandler.setFilter(this.itemHandler.getFilter());
            this.itemInsertionHandler.setPriority(this.priority);

            this.fluidInsertionHandler.setMonitor(backStorage.getInventory(fluidChannel));
            this.fluidInsertionHandler.setFilter(this.fluidHandler.getFilter());
            this.fluidInsertionHandler.setPriority(this.priority);

            // Optional channels: only wire when the integration mod is loaded
            // AND the corresponding read-side handler exists. Both helpers
            // isolate mod-specific class loads so this stays NoClassDefFoundError-safe.
            if (this.gasInsertionHandler != null && this.gasHandler != null
                    && MekanismEnergisticsIntegration.isModLoaded()) {
                wireGasInsertion(backStorage);
            }
            if (this.essentiaInsertionHandler != null && this.essentiaHandler != null
                    && ThaumicEnergisticsIntegration.isModLoaded()) {
                wireEssentiaInsertion(backStorage);
            }

            this.insertionActive = true;
        } catch (final GridAccessException e) {
            // Back grid not available
            this.itemInsertionHandler.clearMonitor();
            this.fluidInsertionHandler.clearMonitor();
            if (this.gasInsertionHandler != null && MekanismEnergisticsIntegration.isModLoaded()) {
                this.gasInsertionHandler.clearMonitor();
            }
            if (this.essentiaInsertionHandler != null && ThaumicEnergisticsIntegration.isModLoaded()) {
                this.essentiaInsertionHandler.clearMonitor();
            }
            this.insertionActive = false;
        }
    }

    /**
     * Wire the gas insertion handler. Isolated to keep Mekanism class loads
     * out of the main {@link #updateInsertionHandlers} path; only invoked
     * when the mod is loaded.
     */
    @SuppressWarnings("unchecked")
    private void wireGasInsertion(IStorageGrid backStorage) {
        SubnetProxyGasHelper.wireInsertionHandler(
            this.gasInsertionHandler, this.gasHandler, backStorage, this.priority);
    }

    /**
     * Wire the essentia insertion handler. Isolated to keep ThaumicEnergistics
     * class loads out of the main {@link #updateInsertionHandlers} path;
     * only invoked when the mod is loaded.
     */
    @SuppressWarnings("unchecked")
    private void wireEssentiaInsertion(IStorageGrid backStorage) {
        SubnetProxyEssentiaHelper.wireInsertionHandler(
            this.essentiaInsertionHandler, this.essentiaHandler, backStorage, this.priority);
    }

    /**
     * Map an AE2 storage channel to the {@link ResourceType} we use for the
     * channel-exposure gate. Returns null for channels we don't manage (then
     * the gate is bypassed and {@link #getCellArray} falls through to its
     * default empty-list behavior at the bottom).
     * <p>
     * Gas/essentia checks are guarded by their integration loaders so this is
     * safe to call when the optional mods are absent.
     */
    private static ResourceType channelToResourceType(
            IStorageChannel<?> channel,
            IItemStorageChannel itemCh,
            IFluidStorageChannel fluidCh) {
        if (channel == itemCh) return ResourceType.ITEM;
        if (channel == fluidCh) return ResourceType.FLUID;

        if (MekanismEnergisticsIntegration.isModLoaded()
                && SubnetProxyGasHelper.matchesChannel(channel)) {
            return ResourceType.GAS;
        }
        if (ThaumicEnergisticsIntegration.isModLoaded()
                && SubnetProxyEssentiaHelper.matchesChannel(channel)) {
            return ResourceType.ESSENTIA;
        }

        return null;
    }

    /** Notify Grid B that our cell array has changed */
    private void notifyGridOfChange() {
        IGridNode node = this.getProxy().getNode();
        if (node != null && node.getGrid() != null) {
            node.getGrid().postEvent(new MENetworkCellArrayUpdate());
        }
    }

    private void traceUpdate(String phase, String detail) {
        Cells.LOGGER.info(
            "[SubnetProxyTrace] phase={} pos={} side={} dim={} tick={} frontGrid={} backGrid={} state[sourcesDirty={}, filtersDirty={}, deltasDirty={}, pendingReset={}, pendingForce={}, insertionActive={}, structureHash={}, cellArrayHash={}] {}",
            phase,
            this.describeTracePos(),
            this.getSide(),
            this.getTraceDimension(),
            this.getObservedWorldTick(),
            describeGrid(this.getFrontGridLive()),
            describeGrid(this.gridA),
            this.sourcesDirty,
            this.filtersDirty,
            this.deltasDirty,
            this.pendingMonitorResetReconcile,
            this.pendingForcedGridBRefresh,
            this.insertionActive,
            this.lastPublishedStructureHash,
            this.lastPublishedCellArrayHash,
            detail);
    }

    private String describeTracePos() {
        TileEntity tile = this.getHost() != null ? this.getHost().getTile() : null;
        BlockPos pos = tile != null ? tile.getPos() : null;

        return pos != null ? pos.toString() : "<no-pos>";
    }

    private String getTraceDimension() {
        World world = this.getHostWorld();

        return world != null ? Integer.toString(world.provider.getDimension()) : "<no-dim>";
    }

    private String findTraceCaller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement frame : stack) {
            String className = frame.getClassName();
            String methodName = frame.getMethodName();

            if (className.equals(Thread.class.getName())) continue;

            if (className.equals(PartSubnetProxyFront.class.getName())
                    && (methodName.equals("findTraceCaller")
                        || methodName.equals("traceUpdate")
                        || methodName.equals("notifyGridOfChange"))) {
                continue;
            }

            if (className.equals(PartSubnetProxyFront.class.getName())) return methodName;

            if (className.startsWith(PartSubnetProxyFront.class.getName() + "$")) {
                return className.substring(className.lastIndexOf('$') + 1) + "." + methodName;
            }

            return className + "." + methodName;
        }

        return "<unknown>";
    }

    private String describeMonitor(@Nullable IBaseMonitor monitor) {
        if (monitor == null) return "null";
        if (monitor == this.registeredItemMonitor) return "item";
        if (monitor == this.registeredFluidMonitor) return "fluid";
        if (this.gasHandler != null && monitor == this.gasHandler.getRegisteredMonitor()) return "gas";
        if (this.essentiaHandler != null && monitor == this.essentiaHandler.getRegisteredMonitor()) return "essentia";

        return monitor.getClass().getSimpleName() + '@'
            + Integer.toHexString(System.identityHashCode(monitor));
    }

    private String describeActionSource(@Nullable IActionSource source) {
        if (source == null) return "null";

        StringBuilder builder = new StringBuilder();
        builder.append(source.getClass().getSimpleName())
            .append('@')
            .append(Integer.toHexString(System.identityHashCode(source)));

        IGrid origin = SubnetProxyEventSource.extractOriginGrid(source);
        if (origin != null) builder.append(", origin=").append(describeGrid(origin));

        UUID eventId = SubnetProxyEventSource.extractEventId(source);
        if (eventId != null) builder.append(", eventId=").append(eventId);

        if (!source.machine().isPresent()) {
            builder.append(", machine=<none>");
            return builder.toString();
        }

        IActionHost machine = source.machine().get();
        builder.append(", machine=")
            .append(machine.getClass().getSimpleName())
            .append('@')
            .append(Integer.toHexString(System.identityHashCode(machine)));

        IGridNode node = machine.getActionableNode();
        builder.append(", machineGrid=").append(describeGrid(node != null ? node.getGrid() : null));
        builder.append(", knownLocal=").append(this.knownLocalProviderHosts.contains(machine));

        return builder.toString();
    }

    private String describeChannel(IStorageChannel<?> channel) {
        ResourceType type = channelToResourceType(channel, itemChannel(), fluidChannel());
        if (type != null) return type.name();

        return channel.getClass().getSimpleName() + '@'
            + Integer.toHexString(System.identityHashCode(channel));
    }

    private static String describeGrid(@Nullable IGrid grid) {
        if (grid == null) return "null";

        return grid.getClass().getSimpleName() + '@'
            + Integer.toHexString(System.identityHashCode(grid));
    }

    private static String describeChangeIterable(@Nullable Iterable<?> change) {
        if (change == null) return "null";

        if (change instanceof Collection) {
            return change.getClass().getSimpleName() + "[size=" + ((Collection<?>) change).size() + "]";
        }

        return change.getClass().getSimpleName();
    }

    private static String describeActionHostIdentity(@Nullable IActionHost host) {
        if (host == null) return "null";

        return host.getClass().getSimpleName() + '@'
            + Integer.toHexString(System.identityHashCode(host));
    }

    private static String describeMonitorIdentity(@Nullable IMEMonitor<?> monitor) {
        if (monitor == null) return "null";

        return monitor.getClass().getSimpleName() + '@'
            + Integer.toHexString(System.identityHashCode(monitor));
    }

    private String summarizeActionHosts(Iterable<IActionHost> hosts, int maxEntries) {
        StringBuilder builder = new StringBuilder();
        int total = 0;

        for (IActionHost host : hosts) {
            if (total < maxEntries) {
                if (builder.length() > 0) builder.append(", ");
                builder.append(describeActionHostIdentity(host));
            }

            total++;
        }

        if (total == 0) return "[]";
        if (total > maxEntries) builder.append(", ... total=").append(total);

        return '[' + builder.toString() + ']';
    }

    private <T extends IAEStack<T>> String summarizeAeChanges(Iterable<T> changes, int maxEntries) {
        StringBuilder builder = new StringBuilder();
        int total = 0;

        for (T change : changes) {
            if (total < maxEntries) {
                if (builder.length() > 0) builder.append("; ");
                builder.append(describeAeStack(change))
                    .append(" delta=")
                    .append(change.getStackSize());
            }

            total++;
        }

        if (total == 0) return "[]";
        if (total > maxEntries) builder.append("; ... total=").append(total);

        return "[" + builder + "]";
    }

    private <T extends IAEStack<T>> String summarizeSnapshotList(@Nullable IItemList<T> list, int maxEntries) {
        if (list == null) return "null";

        StringBuilder builder = new StringBuilder();
        int total = 0;

        for (T stack : list) {
            if (total < maxEntries) {
                if (builder.length() > 0) builder.append("; ");
                builder.append(describeAeStack(stack))
                    .append(" size=")
                    .append(stack.getStackSize());
            }

            total++;
        }

        if (total == 0) return "[]";
        if (total > maxEntries) builder.append("; ... total=").append(total);

        return "[" + builder + "]";
    }

    // ========================= Grid A Listener (Delta Forwarding) =========================

    /**
     * Raw IMEMonitorHandlerReceiver registered on Grid A's monitors (all channels).
     * <p>
     * <b>Immediate forwarding with source-based anti-loop:</b> Filters incoming
     * deltas by checking the {@link IActionSource}'s grid membership and forwards
     * matching deltas to Grid B immediately. Provider-surface rebuild events also
     * queue a one-shot snapshot reconcile because the raw monitor delta can be
     * incomplete, but they still keep the immediate O(δ) forwarding path for the
     * common case (same as classic Storage Bus on Interface).
     * <p>
     * <b>Anti-loop guarantee:</b> Changes from passthrough storage buses arrive
     * with a {@link MachineSource} whose machine is on a <em>remote</em> grid
     * (the original source is preserved through {@code MEMonitorPassThrough}).
     * Our {@link #isLocalSource} check rejects these because the machine's
     * {@link IGridNode#getGrid()} differs from Grid A. Changes from other
     * {@link PartSubnetProxyFront} instances are also rejected, preventing
     * A↔B bidirectional loops. Only changes originating from machines physically
     * on Grid A (drives, chests, local storage buses) are forwarded.
     * <p>
    * <b>onListUpdate fallback:</b> Full monitor resets (power loss/restore,
    * force-update from AE2's nesting detection, storage-bus partition resets)
    * queue a one-shot rebuild and then force Grid B to refresh from the proxy's
    * current listing. Some upstream providers suppress their own removal delta
    * during resets, so relying on incremental correction alone can leave Grid B
    * with a stale cached view.
     */
    @SuppressWarnings("rawtypes")
    private class GridAListener implements IMEMonitorHandlerReceiver {

        @SuppressWarnings("unchecked")
        @Override
        public void postChange(final IBaseMonitor monitor, final Iterable change, final IActionSource actionSource) {
            boolean forceGridBRefresh = isStorageSurfaceResetDelta(actionSource);
            boolean refreshSources = forceGridBRefresh || shouldRefreshSourcesForDelta(actionSource);

            if (refreshSources) {
                if (forceGridBRefresh) pendingForcedGridBRefresh = true;

                // Storage-surface providers can emit self-sourced monitor events
                // while their visible listing is being rebuilt. Those deltas are
                // not a reliable full description of what disappeared or appeared,
                // so queue a source refresh after the immediate delta. Only the
                // storage-bus reset signature upgrades that reconcile into a full
                // Grid B rebuild; generic force-updates stay on the cheaper
                // snapshot-diff path.
                sourcesDirty = true;
                pendingMonitorResetReconcile = true;
                deltasDirty = true;
                alertGridBTick();
            }

            // ---- Determine the ORIGIN grid for this delta ----
            // 1) If the source is already a SubnetProxyEventSource, the upstream
            //    proxy chain has tagged the original origin-grid; use it verbatim
            //    (preserves the same identity through any number of hops).
            // 2) Otherwise, this is a real machine change on our back-grid;
            //    origin = back-grid (gridA) UNLESS the source machine is on a
            //    different grid (passthrough storage bus → ME Interface), in
            //    which case we'd be inflating the loop and must reject.
            IGrid origin = SubnetProxyEventSource.extractOriginGrid(actionSource);
            if (origin == null) {
                if (gridA == null) return;
                if (!isPlainLocalSource(actionSource) && !refreshSources) return;
                origin = gridA;
            }

            // ---- Loop-back rejection ----
            // Never forward a delta back into the grid where it originated.
            // (Catches A↔B bidirectional pairs cleanly: B→A's listener
            // hears A's local change with origin=A; B→A's front-grid is A;
            // origin == front-grid -> reject.)
            IGrid frontGrid = getFrontGridLive();
            if (frontGrid != null && origin == frontGrid) return;

            // ---- Reach-set check (1-hop visibility) ----
            // Forward only if our published listing actually exposes this
            // origin's items. Otherwise we'd be forwarding ghost deltas
            // for items we don't even list.
            if (!exposedOrigins.contains(origin)) return;

            // ---- Election: only one front per origin publishes on a given grid ----
            // Diamond topologies (A→B→D + A→C→D) are dedup'd here:
            // exactly one of B→D / C→D is elected to forward A's deltas.
            SubnetProxyGridCoordinator coord = getFrontGridCoordinator(frontGrid);
            if (coord != null && !coord.isElected(PartSubnetProxyFront.this, origin)) return;

            // ---- Event-UUID dedup (belt-and-suspenders) ----
            // Reuse the upstream UUID so a single logical event keeps the same
            // identity through the chain; generate a fresh one for new origins.
            UUID eventId = SubnetProxyEventSource.extractEventId(actionSource);
            if (eventId == null) eventId = UUID.randomUUID();
            if (coord != null && !coord.tryAccept(eventId)) return;

            // Ensure filters are current before testing deltas
            if (filtersDirty) rebuildFilters();

            try {
                IStorageGrid gridB = getProxy().getStorage();
                IActionSource wrapped = new SubnetProxyEventSource(
                    PartSubnetProxyFront.this, eventId, origin);

                if (monitor == registeredItemMonitor) {
                    if (!isReadChannelExposed(ResourceType.ITEM)) return;
                    IStorageChannel<IAEItemStack> ch = itemChannel();
                    forwardFilteredDeltas(change, itemHandler, ch, gridB, wrapped);
                } else if (monitor == registeredFluidMonitor) {
                    if (!isReadChannelExposed(ResourceType.FLUID)) return;
                    IStorageChannel<IAEFluidStack> ch = fluidChannel();
                    forwardFilteredDeltas(change, fluidHandler, ch, gridB, wrapped);
                } else if (gasHandler != null && MekanismEnergisticsIntegration.isModLoaded()
                           && monitor == gasHandler.getRegisteredMonitor()) {
                    if (!isReadChannelExposed(ResourceType.GAS)) return;
                    forwardFilteredDeltas(change, gasHandler, SubnetProxyGasHelper.getChannel(), gridB, wrapped);
                } else if (essentiaHandler != null && ThaumicEnergisticsIntegration.isModLoaded()
                           && monitor == essentiaHandler.getRegisteredMonitor()) {
                    if (!isReadChannelExposed(ResourceType.ESSENTIA)) return;
                    forwardFilteredDeltas(change, essentiaHandler, SubnetProxyEssentiaHelper.getChannel(), gridB, wrapped);
                }
            } catch (final GridAccessException e) {
                // Grid B not available
            }
        }

        @Override
        public void onListUpdate() {
            // Full list reset on Grid A (e.g. power loss/restore or a storage
            // bus rebuild). Preserve the previous snapshot as the diff baseline,
            // then reconcile against the rebuilt source set on the next Grid B tick.
            sourcesDirty = true;
            pendingMonitorResetReconcile = true;
            deltasDirty = true;
            alertGridBTick();
        }

        @Override
        public boolean isValid(final Object verificationToken) {
            return verificationToken == listenerToken;
        }
    }

    /**
     * Whether an unwrapped (non-{@link SubnetProxyEventSource}) source represents
     * a real machine local to our back-grid. Used to filter out deltas that
     * arrive at our back-grid via passthrough storage buses (storage bus -> ME
     * Interface) whose machine() points at the remote source on another grid.
     * <p>
     * Wrapped sources are handled separately in {@link GridAListener#postChange}
     * (their {@code originGrid} is trusted directly).
     */
    private boolean isPlainLocalSource(IActionSource source) {
        if (this.gridA == null) return false;

        if (!source.machine().isPresent()) {
            // Sources without machine info (BaseActionSource) come from
            // GridStorageCache when a non-IActionHost provider is registered.
            // These are always local to Grid A.
            return true;
        }

        IActionHost machine = source.machine().get();

        // A subnet-proxy front as the source means this came from a forwarding
        // chain that didn't wrap (legacy/gas/essentia). Reject defensively.
        if (machine instanceof PartSubnetProxyFront) return false;

        IGridNode node = machine.getActionableNode();
        return node != null && node.getGrid() == this.gridA;
    }

    /**
     * Recognize provider-surface changes that AE2 reports only as monitor deltas.
     * This covers two silent cases: a provider that used to be directly local has
     * left Grid A before its removal delta is delivered, and similar stale-host
     * transitions where the provider's actionable node no longer matches Grid A.
     * Ordinary local storage-bus content changes must stay on the cheap direct
     * delta path; their reset signature is handled separately by
     * {@link #isStorageSurfaceResetDelta(IActionSource)}.
     */
    private boolean shouldRefreshSourcesForDelta(IActionSource source) {
        if (this.gridA == null || !source.machine().isPresent()) return false;

        IActionHost machine = source.machine().get();
        if (!this.knownLocalProviderHosts.contains(machine)) return false;

        IGridNode node = machine.getActionableNode();
        return node == null || node.getGrid() != this.gridA;
    }

    /**
     * Storage-bus partition resets rebuild the back-grid cell array first, then
     * emit a self-sourced delta from the rebuilt storage bus in the same world
     * tick. That combination is the expensive correctness case that still needs
     * a full Grid B rebuild after the immediate delta is forwarded.
     */
    private boolean isStorageSurfaceResetDelta(IActionSource source) {
        if (this.gridA == null || !source.machine().isPresent()) return false;
        if (!this.pendingMonitorResetReconcile) return false;

        IActionHost machine = source.machine().get();
        if (!this.knownLocalProviderHosts.contains(machine)) return false;
        if (!isLocalStorageSurfaceProvider(machine)) return false;

        long observedTick = this.getObservedWorldTick();
        return observedTick >= 0 && observedTick == this.lastSourceDirtyTick;
    }

    private static boolean isLocalStorageSurfaceProvider(IActionHost machine) {
        String className = machine.getClass().getName();
        return className.equals("appeng.parts.misc.PartStorageBus")
            || className.equals("appeng.fluids.parts.PartFluidStorageBus")
            || className.equals("com.mekeng.github.common.part.PartGasStorageBus")
            || className.equals("thaumicenergistics.part.PartEssentiaStorageBus");
    }

    /**
     * Filter incoming deltas through the handler's predicate and forward
     * matching entries to Grid B with the supplied (already-wrapped) source.
     * Also updates the snapshot incrementally so the onListUpdate fallback
     * can diff correctly.
     *
     * @param changes  raw deltas from Grid A's monitor (already signed)
     * @param handler  the proxy handler with filter predicate
     * @param channel  the storage channel
     * @param gridB    Grid B's storage grid for posting
     * @param source   wrapped source carrying eventId + originGrid metadata
     */
    private <T extends IAEStack<T>> void forwardFilteredDeltas(
            Iterable<T> changes,
            SubnetProxyInventoryHandler<T> handler,
            IStorageChannel<T> channel,
            IStorageGrid gridB,
            IActionSource source) {
        List<T> forwarded = new ArrayList<>();

        for (T change : changes) {
            if (handler.matchesVisibleStack(change)) forwarded.add(change);
        }

        if (forwarded.isEmpty()) return;

        gridB.postAlterationOfStoredItems(channel, forwarded, source);

        // Update snapshot incrementally so onListUpdate diffs remain accurate
        updateSnapshotIncremental(handler, channel, forwarded);
    }

    /**
     * Apply forwarded deltas to the handler's snapshot. Creates the snapshot
     * if it doesn't exist yet (first-run edge case during listener setup).
     */
    private <T extends IAEStack<T>> void updateSnapshotIncremental(
            SubnetProxyInventoryHandler<T> handler,
            IStorageChannel<T> channel,
            List<T> deltas) {

        IItemList<T> snapshot = handler.getLastSnapshot();
        if (snapshot == null) {
            snapshot = channel.createList();
            handler.setLastSnapshot(snapshot);
        }

        for (T delta : deltas) snapshot.add(delta);
    }

    private <T extends IAEStack<T>> String applySnapshotDeltasWithTrace(
            IItemList<T> snapshot,
            List<T> deltas) {
        StringBuilder builder = new StringBuilder();
        int total = 0;

        for (T delta : deltas) {
            T beforeStack = snapshot.findPrecise(delta);
            long before = beforeStack != null ? beforeStack.getStackSize() : 0L;

            snapshot.add(delta);

            T afterStack = snapshot.findPrecise(delta);
            long after = afterStack != null ? afterStack.getStackSize() : 0L;

            if (total < 8) {
                if (builder.length() > 0) builder.append("; ");
                builder.append(describeAeStack(delta))
                    .append(" before=")
                    .append(before)
                    .append(", delta=")
                    .append(delta.getStackSize())
                    .append(", after=")
                    .append(after);
            }

            total++;
        }

        if (total == 0) return "[]";
        if (total > 8) builder.append("; ... total=").append(total);

        return "[" + builder + "]";
    }

    /** Wake up Grid B's tick manager so we compute the snapshot diff. */
    private void alertGridBTick() {
        try {
            this.getProxy().getTick().alertDevice(this.getProxy().getNode());
        } catch (final GridAccessException e) {
            // Grid B not available yet
        }
    }

    /**
     * Register the Grid A listener on the given monitors.
     * Previous listeners must already have been detached by the caller.
     *
     * @param gridA       Grid A's grid reference, stored for {@link #isLocalSource}
     * @param itemMonitor Grid A's item monitor
     * @param fluidMonitor Grid A's fluid monitor
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void registerGridAListeners(IGrid gridA, IMEMonitor itemMonitor, IMEMonitor fluidMonitor) {
        this.gridA = gridA;

        // Fresh token so any stale listener references auto-expire via isValid
        this.listenerToken = new Object();

        if (itemMonitor != null) {
            this.registeredItemMonitor = itemMonitor;
            itemMonitor.addListener(this.gridAListener, this.listenerToken);
        }

        if (fluidMonitor != null) {
            this.registeredFluidMonitor = fluidMonitor;
            fluidMonitor.addListener(this.gridAListener, this.listenerToken);
        }

        // Gas/essentia listeners are registered via their respective helpers
        // (see updatePassthroughSources). Their monitor reference is tracked
        // on the handler itself (getRegisteredMonitor) so unregisterGridAListeners
        // can tear them down symmetrically.
    }

    /** Unregister from all Grid A monitors we're currently listening on. */
    private void unregisterGridAListeners() {
        unregisterGridAListeners(true);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void unregisterGridAListeners(boolean clearGridReference) {
        if (this.registeredItemMonitor != null) {
            this.registeredItemMonitor.removeListener(this.gridAListener);
            this.registeredItemMonitor = null;
        }

        if (this.registeredFluidMonitor != null) {
            this.registeredFluidMonitor.removeListener(this.gridAListener);
            this.registeredFluidMonitor = null;
        }

        if (this.gasHandler != null && MekanismEnergisticsIntegration.isModLoaded()) {
            IMEMonitor gm = this.gasHandler.getRegisteredMonitor();
            if (gm != null) {
                gm.removeListener(this.gridAListener);
                this.gasHandler.setRegisteredMonitor(null);
            }
        }
        if (this.essentiaHandler != null && ThaumicEnergisticsIntegration.isModLoaded()) {
            IMEMonitor em = this.essentiaHandler.getRegisteredMonitor();
            if (em != null) {
                em.removeListener(this.gridAListener);
                this.essentiaHandler.setRegisteredMonitor(null);
            }
        }

        if (clearGridReference) this.gridA = null;

        // Invalidate the token so any lingering references auto-expire
        this.listenerToken = new Object();
    }

    // ========================= IGridTickable (on Grid B) =========================

    @Nonnull
    @Override
    public TickingRequest getTickingRequest(@Nonnull final IGridNode node) {
        // Only tick for the inner proxy node (Grid B).
        if (node != this.getProxy().getNode()) {
            return new TickingRequest(20, 20, true, false);
        }

        // Alertable so the Grid A listener's onListUpdate can wake us for
        // snapshot diff. Normal deltas are forwarded immediately in postChange,
        // so ticking is only needed for rare full-reset events.
        return new TickingRequest(
            CellsConfig.general.subnetProxyMinTickRate,
            CellsConfig.general.subnetProxyMaxTickRate,
            !this.deltasDirty, true);
    }

    @Nonnull
    @Override
    public TickRateModulation tickingRequest(@Nonnull final IGridNode node, final int ticksSinceLastCall) {
        if (node != this.getProxy().getNode()) return TickRateModulation.SLEEP;
        if (!this.deltasDirty) return TickRateModulation.SLEEP;

        this.deltasDirty = false;

        if (this.pendingMonitorResetReconcile) {
            this.reconcilePendingMonitorReset();
            return TickRateModulation.SLEEP;
        }

        snapshotDiffAndForward();

        return TickRateModulation.SLEEP;
    }

    private void reconcilePendingMonitorReset() {
        if (this.sourcesDirty) this.updatePassthroughSources(false);
        if (this.filtersDirty) this.rebuildFilters();

        this.pendingMonitorResetReconcile = false;
        boolean forceGridBRefresh = this.pendingForcedGridBRefresh;
        this.pendingForcedGridBRefresh = false;
        this.lastSourceDirtyTick = Long.MIN_VALUE;

        int previousStructureHash = this.lastPublishedStructureHash;
        int previousCellArrayHash = this.lastPublishedCellArrayHash;
        int currentStructureHash = this.computePublishedStructureHash();
        int currentCellArrayHash = this.computePublishedCellArrayHash();
        this.lastPublishedStructureHash = currentStructureHash;
        this.lastPublishedCellArrayHash = currentCellArrayHash;
        if (forceGridBRefresh || currentCellArrayHash != previousCellArrayHash) {
            this.refreshAllSnapshots();
            this.notifyGridOfChange();
            return;
        }

        // if (currentStructureHash != previousStructureHash) {
        // Visibility surface changed without altering Grid B's handler set.
        // Preserve the old baseline and let snapshotDiffAndForward emit the
        // content delta instead of forcing a cell-array rebuild.
        // }

        snapshotDiffAndForward();
    }

    /**
     * Snapshot-based delta forwarding, used only as a fallback when the proxy
     * has already marked normal content deltas dirty without a pending full
     * monitor reset.
     * <p>
     * Normal per-item deltas are forwarded immediately in
     * {@link GridAListener#postChange} and never reach this path.
     * <p>
     * Reads the handler's current listing via {@code getAvailableItems()}
     * (local cells + filter only), diffs against the incrementally maintained
     * snapshot, and forwards non-zero changes to Grid B.
     */
    private void snapshotDiffAndForward() {
        // Ensure sources and filters are up to date before snapshotting
        if (this.sourcesDirty) this.updatePassthroughSources();
        if (this.filtersDirty) this.rebuildFilters();

        IStorageGrid gridB;
        try {
            gridB = this.getProxy().getStorage();
        } catch (final GridAccessException e) {
            return;
        }

        // Item channel delta
        snapshotDiffChannel(this.itemHandler, itemChannel(), gridB);

        // Fluid channel delta
        snapshotDiffChannel(this.fluidHandler, fluidChannel(), gridB);

        // Gas channel (MekanismEnergistics)
        if (this.gasHandler != null && MekanismEnergisticsIntegration.isModLoaded()) {
            snapshotDiffChannelRaw(this.gasHandler, SubnetProxyGasHelper.getChannel(), gridB);
        }

        // Essentia channel (ThaumicEnergistics)
        if (this.essentiaHandler != null && ThaumicEnergisticsIntegration.isModLoaded()) {
            snapshotDiffChannelRaw(this.essentiaHandler, SubnetProxyEssentiaHelper.getChannel(), gridB);
        }
    }

    /**
     * Raw-typed bridge to {@link #snapshotDiffChannel} for use with handlers
     * declared as raw types (gas/essentia, where the typed channel classes
     * are gated by mod presence and we cannot mention them at field-decl time).
     * <p>
     * The cast is safe because each handler is constructed with the matching
     * channel by its corresponding helper.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void snapshotDiffChannelRaw(SubnetProxyInventoryHandler handler,
                                        IStorageChannel channel,
                                        IStorageGrid gridB) {
        snapshotDiffChannel(handler, channel, gridB);
    }

    /**
     * Compute the delta between the handler's current listing and its
     * incrementally maintained snapshot, forward non-empty diffs to Grid B,
     * and replace the snapshot with the current state.
     *
     * @param handler  the passthrough handler to query
     * @param channel  the storage channel
     * @param gridB    Grid B's storage grid for posting deltas
     */
    private <T extends IAEStack<T>> void snapshotDiffChannel(
            SubnetProxyInventoryHandler<T> handler,
            IStorageChannel<T> channel,
            IStorageGrid gridB) {

        // Take current snapshot from the handler (reads local cells + filter only)
        IItemList<T> current = channel.createList();
        handler.getAvailableItems(current);

        IItemList<T> previous = handler.getLastSnapshot();
        if (previous == null) {
            // First snapshot: no delta to forward, just establish baseline.
            // Grid B already got the full listing via getCellArray → getAvailableItems.
            handler.setLastSnapshot(current);
            return;
        }

        this.postSnapshotDelta(previous, current, channel, gridB);

        handler.setLastSnapshot(current);
    }

    private <T extends IAEStack<T>> void postSnapshotDelta(
            @Nullable IItemList<T> previous,
            @Nullable IItemList<T> current,
            IStorageChannel<T> channel,
            IStorageGrid gridB) {
        if (previous == null || current == null) return;

        // Diff via key-lookup: for each item in current, compute (now - was)
        // using IItemContainer.findPrecise; for items missing from current,
        // emit them as negative.
        //
        // This method runs every time Grid A's monitor issues a  full reset
        // (power flicker, cell array shuffle).
        List<T> changes = new ArrayList<>();

        for (T now : current) {
            T was = previous.findPrecise(now);
            long delta = (was == null) ? now.getStackSize()
                                       : now.getStackSize() - was.getStackSize();
            if (delta == 0) continue;

            T entry = now.copy();
            entry.setStackSize(delta);
            changes.add(entry);
        }

        for (T was : previous) {
            // Items present in previous but NOT in current: full removal.
            if (current.findPrecise(was) != null) continue;
            if (was.getStackSize() == 0) continue;

            T entry = was.copy();
            entry.setStackSize(-was.getStackSize());
            changes.add(entry);
        }

        if (changes.isEmpty()) return;

        // Wrap as a SubnetProxyEventSource so downstream proxies see the
        // origin as our back-grid (gridA) and apply normal loop/election
        // checks. Snapshot-diff is a fallback path (rare full resets), so
        // generating a fresh UUID per call is fine; the per-grid LRU dedup
        // is purely a safety net here.
        IActionSource src = (this.gridA != null)
            ? new SubnetProxyEventSource(this, UUID.randomUUID(), this.gridA)
            : this.proxySource;
        gridB.postAlterationOfStoredItems(channel, changes, src);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void postSnapshotDeltaRaw(
            @Nullable IItemList<?> previous,
            @Nullable IItemList<?> current,
            IStorageChannel<?> channel,
            IStorageGrid gridB) {
        this.postSnapshotDelta((IItemList) previous, (IItemList) current, (IStorageChannel) channel, gridB);
    }

    // ========================= Peer aggregation / coordinator helpers =========================

    /**
     * Live lookup of our front-grid (the grid the inner proxy node belongs to).
     * Returns null if the node has no grid yet (early lifecycle / disconnected).
     * Distinct from {@link #gridA} (which is back-grid).
     */
    private IGrid getFrontGridLive() {
        IGridNode node = this.getProxy().getNode();
        return node != null ? node.getGrid() : null;
    }

    /**
     * Resolve the coordinator for the current front-grid.
     *
     * Listing and delta forwarding should follow the live per-grid coordinator,
     * not just the cached reference that is normally refreshed during source
     * rebuilds. This lookup stays read-only so monitor callbacks never try to
     * repair coordinator state from inside a forwarding path.
     */
    @Nullable
    private SubnetProxyGridCoordinator getFrontGridCoordinator(@Nullable IGrid frontGrid) {
        if (frontGrid == null) return null;

        SubnetProxyGridCoordinator liveCoord = SubnetProxyGridCoordinator.getOrNull(frontGrid);
        if (liveCoord != null) return liveCoord;

        return frontGrid == this.currentFrontGrid ? this.currentFrontGridCoord : null;
    }

    /**
     * Build the exposed-origins set: own back-grid + each peer's back-grid.
     * Filters out nulls (peers in transient states with no grid yet).
     */
    private Set<IGrid> computeExposedOrigins(IGrid backGrid, List<PartSubnetProxyFront> peers) {
        Set<IGrid> set = new HashSet<>();
        if (backGrid != null) set.add(backGrid);

        for (PartSubnetProxyFront p : peers) {
            IGrid pBack = p.getBackGrid();
            if (pBack != null) set.add(pBack);
        }

        return set;
    }

    /**
     * (Re)synchronize our coordinator membership with our current front-grid.
     * <ul>
     *   <li>If front-grid changed, unregister from old coord and register with new.</li>
     *   <li>If front-grid is the same, just notify the existing coord that our
     *       exposed-origins set may have changed (election recompute).</li>
     * </ul>
     * Safe to call repeatedly; cheap when nothing has changed.
     */
    private void refreshCoordinatorRegistration() {
        IGrid frontGrid = getFrontGridLive();
        SubnetProxyGridCoordinator newCoord = (frontGrid != null)
            ? SubnetProxyGridCoordinator.getOrCreate(frontGrid)
            : null;

        if (newCoord != this.currentFrontGridCoord || frontGrid != this.currentFrontGrid) {
            if (this.currentFrontGridCoord != null) {
                this.currentFrontGridCoord.unregisterFront(this);
            }

            this.currentFrontGrid = frontGrid;
            this.currentFrontGridCoord = newCoord;
            if (newCoord != null) newCoord.registerFront(this);
        } else if (newCoord != null) {
            // Same coord, but our exposedOrigins likely changed; re-elect.
            newCoord.onPeersChanged();
        }
    }

    /**
     * @return our back-grid (Grid A), or null if not currently wired.
     *         Stable accessor for peers and the coordinator.
     */
    public IGrid getBackGrid() {
        return this.gridA;
    }

    @Override
    public boolean isOutboundConnection() {
        return true;
    }

    @Override
    @Nullable
    public IGrid getTargetGrid() {
        return this.getBackGrid();
    }

    @Override
    @Nonnull
    public ItemStack getRemoteDisplayStack() {
        PartSubnetProxyBack back = findBackPart();
        return back != null ? back.getItemStack(PartItemStack.PICK) : ItemStack.EMPTY;
    }

    /**
     * @return the current exposed-origins set (own back-grid + each peer's
     *         back-grid). Read-only; do not mutate. Used by the coordinator
     *         during election.
     */
    public Set<IGrid> getExposedOrigins() {
        return this.exposedOrigins;
    }

    /**
     * Whether this front is currently allowed to publish its OWN back-grid on
     * its front-grid. The election gate must apply to local listing/extract as
     * well as peer aggregation, otherwise duplicate same-origin fronts can both
     * expose the same inventory surface.
     */
    boolean shouldExposeOwnOrigin() {
        IGrid origin = this.getBackGrid();
        if (origin == null) return false;

        IGrid frontGrid = getFrontGridLive();
        if (frontGrid != null && origin == frontGrid) return false;

        SubnetProxyGridCoordinator coord = getFrontGridCoordinator(frontGrid);
        return coord == null || coord.isElected(this, origin);
    }

    /**
     * Election changes are structural visibility changes. Clear our snapshots
     * so a later back-grid onListUpdate does not replay those structural
     * changes as data deltas, then refresh Grid B if the published surface
     * actually changed.
     */
    void onCoordinatorElectionChanged() {
        clearPassthroughSnapshots();

        if (this.rebuildingPassthroughSources) return;

        int currentStructureHash = this.computePublishedStructureHash();
        if (currentStructureHash == this.lastPublishedStructureHash) return;

        this.lastPublishedStructureHash = currentStructureHash;
        this.notifyGridOfChange();
    }

    @SuppressWarnings("unchecked")
    private void clearPassthroughSnapshots() {
        this.itemHandler.setLastSnapshot(null);
        this.fluidHandler.setLastSnapshot(null);
        if (this.gasHandler != null) this.gasHandler.setLastSnapshot(null);
        if (this.essentiaHandler != null) this.essentiaHandler.setLastSnapshot(null);
    }

    // ========================= Diagnostics =========================

    public void refreshDiagnosticsState() {
        this.ensureSourcesCurrent();
        if (this.filtersDirty) this.rebuildFilters();
    }

    public ProxyDiagnosticSnapshot getDiagnosticSnapshot() {
        this.refreshDiagnosticsState();

        World world = this.getHostWorld();
        IGrid frontGrid = getFrontGridLive();
        int dimensionId = world != null && world.provider != null ? world.provider.getDimension() : Integer.MIN_VALUE;
        String dimensionName = world != null && world.provider != null && world.provider.getDimensionType() != null
                ? world.provider.getDimensionType().getName()
                : "unknown";

        return new ProxyDiagnosticSnapshot(
            dimensionId,
            dimensionName,
            this.getHostPos(),
            this.getSide() != null ? this.getSide().getFacing() : null,
            this.isPowered(),
            this.isActive(),
            this.findBackPart() != null,
            this.enabledChannels,
            this.getPriority(),
            this.hasInsertionCard(),
            this.insertionActive,
            this.getFilterMode(),
            this.cachedHasFuzzy,
            this.cachedHasInverter,
            this.shouldExposeOwnOrigin(),
            this.computePublishedStructureHash(),
            identityHash(frontGrid),
            identityHash(this.getBackGrid()),
            this.exposedOrigins.size(),
            this.peerFronts.size(),
            this.getPublishedPeerFronts().size(),
            this.itemHandler.getLocalCellCount(),
            this.fluidHandler.getLocalCellCount(),
            this.gasHandler != null ? this.gasHandler.getLocalCellCount() : 0,
            this.essentiaHandler != null ? this.essentiaHandler.getLocalCellCount() : 0,
            this.lastFaultRecord);
    }

    void recordExtractionMismatch(
            IStorageChannel<?> channel,
            IAEStack<?> request,
            @Nullable IAEStack<?> extracted,
            long visibleAmount,
            Actionable action) {
        if (request == null || visibleAmount <= 0) return;

        ResourceType type = channelToResourceType(channel, itemChannel(), fluidChannel());
        String channelName = type != null ? type.name() : channel.getClass().getSimpleName();
        int structureHash = this.computePublishedStructureHash();
        boolean ownOriginVisible = this.shouldExposeOwnOrigin();
        long observedTick = this.getObservedWorldTick();
        long extractedAmount = extracted == null ? 0 : extracted.getStackSize();
        int fingerprint = computeFaultFingerprint(channelName, request, structureHash, ownOriginVisible);

        FaultRecord record = new FaultRecord(
            observedTick,
            observedTick,
            1,
            channelName,
            describeAeStack(request),
            request.getStackSize(),
            extractedAmount,
            visibleAmount,
            action.name(),
            ownOriginVisible,
            structureHash,
            this.getLocalCellCount(type),
            this.getPublishedPeerFronts().size(),
            fingerprint);

        if (this.lastFaultRecord != null && this.lastFaultRecord.fingerprint == fingerprint) {
            record = this.lastFaultRecord.withObservation(observedTick, extractedAmount, visibleAmount);
        }

        this.lastFaultRecord = record;

        if (observedTick >= 0
                && this.lastFaultLogFingerprint == fingerprint
                && this.lastFaultLogTick >= 0
                && observedTick - this.lastFaultLogTick < FAULT_LOG_COOLDOWN_TICKS) {
            return;
        }

        this.lastFaultLogFingerprint = fingerprint;
        this.lastFaultLogTick = observedTick;

        World world = this.getHostWorld();
        Cells.LOGGER.warn(
            "Subnet Proxy visibility mismatch at dim={} ({}) pos={} side={} channel={} request={} requested={} extracted={} visible_now={} structure_hash={} own_origin_visible={} local_cells={} visible_peers={} action={} occurrences={}",
            world != null && world.provider != null ? world.provider.getDimension() : "unknown",
            world != null && world.provider != null && world.provider.getDimensionType() != null
                    ? world.provider.getDimensionType().getName()
                    : "unknown",
            formatBlockPos(this.getHostPos()),
            this.getSide() != null ? this.getSide().getFacing() : "unknown",
            record.channelName,
            record.requestDescription,
            record.requestedAmount,
            record.extractedAmount,
            record.visibleAmount,
            formatHash(record.structureHash),
            record.ownOriginVisible,
            record.localCellCount,
            record.visiblePeerCount,
            record.actionName,
            record.occurrenceCount);
    }

    private int getLocalCellCount(@Nullable ResourceType type) {
        if (type == null) return 0;

        switch (type) {
            case ITEM:
                return this.itemHandler.getLocalCellCount();
            case FLUID:
                return this.fluidHandler.getLocalCellCount();
            case GAS:
                return this.gasHandler != null ? this.gasHandler.getLocalCellCount() : 0;
            case ESSENTIA:
                return this.essentiaHandler != null ? this.essentiaHandler.getLocalCellCount() : 0;
            default:
                return 0;
        }
    }

    private long getObservedWorldTick() {
        World world = this.getHostWorld();
        return world != null ? world.getTotalWorldTime() : -1L;
    }

    private static int computeFaultFingerprint(
            String channelName,
            IAEStack<?> request,
            int structureHash,
            boolean ownOriginVisible) {
        int hash = 1;
        hash = 31 * hash + channelName.hashCode();
        hash = 31 * hash + structureHash;
        hash = 31 * hash + (ownOriginVisible ? 1 : 0);

        ItemStack displayStack = request.asItemStackRepresentation();
        if (!displayStack.isEmpty()) {
            hash = 31 * hash + Item.getIdFromItem(displayStack.getItem());
            hash = 31 * hash + displayStack.getMetadata();
            hash = 31 * hash + (displayStack.hasTagCompound() ? displayStack.getTagCompound().hashCode() : 0);
        }

        return hash;
    }

    private static String describeAeStack(IAEStack<?> stack) {
        ItemStack displayStack = stack.asItemStackRepresentation();
        if (displayStack.isEmpty()) return stack.getClass().getSimpleName();

        ResourceLocation registryName = displayStack.getItem().getRegistryName();
        StringBuilder description = new StringBuilder(displayStack.getDisplayName());

        if (registryName != null) {
            description.append(" (")
                .append(registryName)
                .append(':')
                .append(displayStack.getMetadata());

            if (displayStack.hasTagCompound()) description.append(", nbt");

            description.append(')');
        }

        return description.toString();
    }

    private static String formatBlockPos(@Nullable BlockPos pos) {
        if (pos == null) return "[unknown]";
        return "[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]";
    }

    private static String formatHash(int hash) {
        return String.format("%08X", hash);
    }

    /**
     * Append peer fronts' filtered local items into {@code out}, then re-filter
     * through {@code myFilter}. Called by {@link SubnetProxyInventoryHandler#getAvailableItems}
     * to enable 1-hop peer aggregation:
     * <ul>
     *   <li>For each peer front on our back-grid:</li>
     *   <li>Skip if peer's back-grid is null or equals our front-grid (loop-back).</li>
     *   <li>Skip if we are not the elected publisher for the peer's back-grid
     *       (handles diamond dedup).</li>
     *   <li>Otherwise, fetch the peer's <em>local-only</em> filtered items
     *       (NOT the peer's full peer-aware listing, which would expose
     *       2-hop items and break the 1-hop visibility rule).</li>
     *   <li>Re-filter with our own filter (conjunction: peer's filter AND ours)
     *       and add to {@code out}.</li>
     * </ul>
     *
     * @param out      output list to append into
     * @param channel  storage channel being queried
         * @param visibilityFilter the currently visible filter surface
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public <T extends IAEStack<T>> void appendPeerItemsForListing(
            IItemList<T> out,
            IStorageChannel<T> channel,
             Predicate<T> visibilityFilter) {

        if (this.peerFronts.isEmpty()) return;

        IGrid frontGrid = getFrontGridLive();
        if (frontGrid == null) return;

        SubnetProxyGridCoordinator coord = getFrontGridCoordinator(frontGrid);
        if (coord == null) return;

        // Resolve channel identity once per call to avoid per-peer lookups.
        // These come from a static cache so repeated calls, don't re-traverse
        // the AEApi registry.
        IItemStorageChannel itemCh = itemChannel();
        IFluidStorageChannel fluidCh = fluidChannel();

        for (PartSubnetProxyFront peer : this.peerFronts) {
            IGrid peerOrigin = peer.getBackGrid();
            if (peerOrigin == null) continue;
            if (peerOrigin == frontGrid) continue; // immediate loop-back
            if (!coord.isElected(this, peerOrigin)) continue;

            // Get peer's local (back-grid only, no recursion into its peers).
            // Peer's filter is applied here; ours is applied in the merge below.
            SubnetProxyInventoryHandler peerHandler = resolvePeerHandler(peer, channel, itemCh, fluidCh);
            if (peerHandler == null) continue;

            IItemList<T> peerLocal = channel.createList();
            peerHandler.appendLocalAvailableItems(peerLocal);

            for (T s : peerLocal) {
                if (visibilityFilter == null || visibilityFilter.test(s)) out.add(s);
            }
        }
    }

    /**
     * Resolve which of the peer's per-channel handlers to use for the given
     * AE2 storage channel. Centralized so listing and extract paths stay in
     * sync, and so optional channels (gas/essentia) are added in exactly
     * one place. Returns null when the channel is unsupported or the peer
     * lacks a handler for it (e.g. integration mod missing on this side).
     */
    @SuppressWarnings("rawtypes")
    private static SubnetProxyInventoryHandler resolvePeerHandler(
            PartSubnetProxyFront peer,
            IStorageChannel<?> channel,
            IItemStorageChannel itemCh,
            IFluidStorageChannel fluidCh) {

        if (channel == itemCh) return peer.itemHandler;
        if (channel == fluidCh) return peer.fluidHandler;

        if (peer.gasHandler != null && MekanismEnergisticsIntegration.isModLoaded()
                && SubnetProxyGasHelper.matchesChannel(channel)) {
            return peer.gasHandler;
        }
        if (peer.essentiaHandler != null && ThaumicEnergisticsIntegration.isModLoaded()
                && SubnetProxyEssentiaHelper.matchesChannel(channel)) {
            return peer.essentiaHandler;
        }
        return null;
    }

    /**
     * Symmetric counterpart to {@link #appendPeerItemsForListing} for the
     * extraction path. Iterates peer fronts on our back-grid and, when we are
     * the elected publisher for a peer's origin, extracts from the peer's
     * local cells (not the peer's full peer-aware handler, and not the back
     * grid's monitor). Preserves the 1-hop visibility rule in the extract
     * surface the same way listing does.
     *
     * @param request  remaining request (caller should pass a copy with the
     *                 outstanding amount, not the original).
     * @param visibilityFilter the currently visible filter surface; peer-local
     *                         filtering is still applied independently by
     *                         {@link SubnetProxyInventoryHandler#extractFromLocalCells}.
     * @return combined extracted stack, or null if nothing extracted.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public <T extends IAEStack<T>> T extractFromPeerLocals(
            T request,
            Actionable type,
            IActionSource src,
            IStorageChannel<T> channel,
            Predicate<T> visibilityFilter) {

        if (this.peerFronts.isEmpty()) return null;
        if (request == null || request.getStackSize() <= 0) return null;
        if (visibilityFilter != null && !visibilityFilter.test(request)) return null;

        IGrid frontGrid = getFrontGridLive();
        if (frontGrid == null) return null;

        SubnetProxyGridCoordinator coord = getFrontGridCoordinator(frontGrid);
        if (coord == null) return null;

        IItemStorageChannel itemCh = itemChannel();
        IFluidStorageChannel fluidCh = fluidChannel();

        long remaining = request.getStackSize();
        T extracted = null;

        // Sort peers by their proxy priority (desc) so extraction respects
        // user-configured priority across the diamond/chain. Each peer's
        // priority is the same value propagated to all its handlers, so the
        // proxy-level getPriority() suffices and avoids per-handler lookup.
        List<PartSubnetProxyFront> orderedPeers = sortedPeersByPriorityDesc(this.peerFronts);

        for (PartSubnetProxyFront peer : orderedPeers) {
            if (remaining <= 0) break;

            IGrid peerOrigin = peer.getBackGrid();
            if (peerOrigin == null) continue;
            if (peerOrigin == frontGrid) continue; // immediate loop-back
            if (!coord.isElected(this, peerOrigin)) continue;

            SubnetProxyInventoryHandler peerHandler = resolvePeerHandler(peer, channel, itemCh, fluidCh);
            if (peerHandler == null) continue;

            T sub = request.copy();
            sub.setStackSize(remaining);
            T got = (T) peerHandler.extractFromLocalCells(sub, type, src);
            if (got == null || got.getStackSize() <= 0) continue;

            remaining -= got.getStackSize();
            if (extracted == null) {
                extracted = got;
            } else {
                extracted.incStackSize(got.getStackSize());
            }
        }

        return extracted;
    }

    /**
     * Return {@code peers} sorted by descending priority. Avoids allocations
     * for the common 0/1-peer case (small networks). The proxy-level priority
     * is identical to its handler priority since {@link #setPriority} keeps
     * them in sync, so {@link #getPriority()} is the canonical sort key.
     */
    private static List<PartSubnetProxyFront> sortedPeersByPriorityDesc(List<PartSubnetProxyFront> peers) {
        if (peers.size() < 2) return peers;
        List<PartSubnetProxyFront> sorted = new ArrayList<>(peers);
        sorted.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        return sorted;
    }

    // ========================= Collision & Cable =========================

    @Override
    public void getBoxes(final IPartCollisionHelper bch) {
        // 5 units deep from the face (z=11..16 in part-local coords)
        bch.addBox(0, 0, 11, 16, 16, 16);
    }

    @Override
    public AECableType getCableConnectionType(final AEPartLocation dir) {
        return AECableType.SMART;
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 2;
    }

    // ========================= Right-click handling =========================

    @Override
    public boolean onPartActivate(final EntityPlayer player, final EnumHand hand, final Vec3d pos) {
        // If the player is holding the complementary (back) part, attempt to place it
        // on the adjacent block's opposite face instead of activating this part.
        if (isHoldingComplementaryPart(player, hand, CellsPartType.SUBNET_PROXY_BACK)) {
            return tryPlaceComplementaryPart(player, hand);
        }

        // Suppress activation on the same tick the part was placed.
        // AE2's client-side PartPlacement returns PASS, causing Minecraft to
        // try the off-hand, which triggers onPartActivate on the just-placed part.
        TileEntity te = this.getHost() != null ? this.getHost().getTile() : null;
        if (te != null && te.getWorld() != null && te.getWorld().getTotalWorldTime() == this.placedTick) return false;

        if (!player.isSneaking() && this.useMemoryCard(player)) return true;
        if (player.isSneaking()) return false;

        if (player.world.isRemote) return true;

        // Check that the back part is present
        if (findBackPart() == null) {
            player.sendMessage(new TextComponentTranslation("chat.cells.subnet_proxy.need_back"));
            return true;
        }

        IPartHost host = this.getHost();
        AEPartLocation side = this.getSide();
        if (host == null || side == null) return true;

        // Open GUI
        TileEntity guiTe = host.getTile();
        if (guiTe == null) return true;

        CellsGuiHandler.openPartGui(player, guiTe, side, CellsGuiHandler.GUI_PART_SUBNET_PROXY);

        return true;
    }

    @Override
    public boolean onPartShiftActivate(final EntityPlayer player, final EnumHand hand, final Vec3d pos) {
        return this.useMemoryCard(player);
    }

    /**
     * Check if the player is holding a specific CELLS part type.
     */
    private boolean isHoldingComplementaryPart(EntityPlayer player, EnumHand hand, CellsPartType expectedType) {
        ItemStack held = player.getHeldItem(hand);
        if (held.isEmpty()) return false;
        if (!(held.getItem() instanceof ItemCellsPart)) return false;

        CellsPartType type = CellsPartType.getById(held.getItemDamage());
        return type == expectedType;
    }

    /**
     * Attempt to place the complementary part on the adjacent block's opposite face.
     * Returns true to consume the click regardless of success.
     */
    private boolean tryPlaceComplementaryPart(EntityPlayer player, EnumHand hand) {
        if (player.world.isRemote) return true;

        IPartHost host = this.getHost();
        AEPartLocation side = this.getSide();
        if (host == null || side == null) return true;

        TileEntity selfTile = host.getTile();
        if (selfTile == null) return true;

        EnumFacing facing = side.getFacing();
        BlockPos adjacentPos = selfTile.getPos().offset(facing);

        // Delegate to AE2's part placement helper
        AEApi.instance().partHelper().placeBus(
            player.getHeldItem(hand), adjacentPos,
            facing.getOpposite(), player, hand, player.world);

        return true;
    }

    // ========================= Drop handling =========================

    @Override
    public void getDrops(final List<ItemStack> drops, final boolean wrenched) {
        for (int i = 0; i < this.upgrades.getSlots(); i++) {
            ItemStack stack = this.upgrades.getStackInSlot(i);
            if (!stack.isEmpty()) drops.add(stack);
        }
    }

    // ========================= Host access for container/GUI =========================

    public World getHostWorld() {
        TileEntity te = this.getHost() != null ? this.getHost().getTile() : null;
        return te != null ? te.getWorld() : null;
    }

    public BlockPos getHostPos() {
        return this.getHost() != null ? this.getHost().getLocation().getPos() : null;
    }

    // ========================= Model =========================

    @Nonnull
    @Override
    public IPartModel getStaticModels() {
        if (this.isActive() && this.isPowered()) return MODELS_HAS_CHANNEL;
        if (this.isPowered()) return MODELS_ON;
        return MODELS_OFF;
    }
}
