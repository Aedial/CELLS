package com.cells.gui.subnetproxy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Slot;

import appeng.api.config.FuzzyMode;
import appeng.api.config.SecurityPermissions;
import appeng.api.implementations.guiobjects.IGuiItem;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEFluidStack;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.OptionalSlotRestrictedInput;
import appeng.container.slot.SlotRestrictedInput;
import appeng.container.slot.IOptionalSlotHost;
import appeng.fluids.items.FluidDummyItem;
import appeng.fluids.util.AEFluidStack;
import appeng.helpers.InventoryAction;
import appeng.items.contents.NetworkToolViewer;
import appeng.items.tools.ToolNetworkTool;
import appeng.parts.automation.UpgradeInventory;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;

import com.cells.gui.overlay.ServerMessageHelper;

import com.cells.config.CellsConfig;
import com.cells.gui.IToolboxContainer;
import com.cells.integration.mekanismenergistics.MekanismEnergisticsIntegration;
import com.cells.integration.thaumicenergistics.ThaumicEnergisticsIntegration;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.sync.IQuickAddFilterContainer;
import com.cells.network.sync.IResourceSyncContainer;
import com.cells.network.sync.PacketResourceSlot;
import com.cells.network.sync.ResourceType;
import com.cells.parts.subnetproxy.PartSubnetProxyFront;


/**
 * Container for the Subnet Proxy GUI.
 * <p>
 * Layout:
 * <ul>
 *   <li>63 filter slots (9×7) using paged view, all visible at once</li>
 *   <li>Dynamic upgrade slot columns (Cell Workbench style, from config)</li>
 *   <li>Page navigation synced via @GuiSync</li>
 *   <li>Filter mode synced via @GuiSync</li>
 * </ul>
 */
public class ContainerSubnetProxy extends AEBaseContainer
    implements IOptionalSlotHost, IQuickAddFilterContainer, IResourceSyncContainer, IToolboxContainer {

    private final PartSubnetProxyFront part;

    // Server-side cache for filter change detection (slot index → IAEItemStack)
    private final Map<Integer, IAEItemStack> serverFilterCache = new HashMap<>();

    // Client-side cache populated by PacketResourceSlot from server
    private final Map<Integer, IAEItemStack> clientFilterCache = new HashMap<>();

    // Network tool ("toolbox") support
    private int toolboxSlot = -1;
    private NetworkToolViewer toolboxInventory;

    @GuiSync(0)
    public int currentPage = 0;

    @GuiSync(1)
    public int totalPages = 1;

    @GuiSync(2)
    public int filterMode = 0;

    @GuiSync(3)
    public int availableUpgrades = 0;

    @GuiSync(4)
    public int fuzzyMode = 0;

    @GuiSync(5)
    public int priority = 0;

    @GuiSync(6)
    public int hasInsertionCard = 0;

    /**
     * Bitmask of enabled channels (one bit per {@link ResourceType#ordinal()}).
     * Synced from the part. Used by the GUI to render channel-toggle button
     * states and to decide whether to show the "no channels enabled" warning.
     */
    @GuiSync(7)
    public int enabledChannels = 0;

    public ContainerSubnetProxy(final InventoryPlayer ip, final PartSubnetProxyFront part) {
        super(ip, null, part);
        this.part = part;

        // Initialize synced fields from part state
        this.currentPage = part.getCurrentPage();
        this.totalPages = part.getTotalPages();
        this.filterMode = part.getFilterMode().ordinal();
        this.fuzzyMode = part.getFuzzyMode().ordinal();
        this.availableUpgrades = CellsConfig.general.subnetProxyUpgradeSlots;
        this.priority = part.getPriority();
        this.hasInsertionCard = part.hasInsertionCard() ? 1 : 0;
        this.enabledChannels = part.getEnabledChannelsBitmask();

        // Filter slots are NOT container slots. They are GUI-only widgets
        // (SubnetProxyFilterWidget) synced via PacketResourceSlot, completely
        // bypassing vanilla's container slot sync which caused desync bugs.

        // Add upgrade slots (Cell Workbench dynamic column style)
        // Up to 3 columns of 8 rows each, starting at (187, 26)
        UpgradeInventory upgradeInv = part.getUpgradeInventory();
        int maxUpgrades = CellsConfig.general.subnetProxyUpgradeSlots;

        // Column x-offsets matching Cell Workbench: first at 187, second at 187+27=214, third at 214+18=232
        final int[] colX = { 187, 214, 232 };

        for (int col = 0; col < 3; col++) {
            for (int row = 0; row < 8; row++) {
                int slotIdx = col * 8 + row;
                if (slotIdx >= maxUpgrades) break;

                this.addSlotToContainer(new OptionalSlotRestrictedInput(
                    SlotRestrictedInput.PlacableItemType.UPGRADES,
                    upgradeInv, this,
                    slotIdx,
                    colX[col],
                    25 + row * 18,
                    slotIdx,
                    ip
                ).setNotDraggable());
            }
        }

        // Set up toolbox (network tool)
        this.setupToolbox(ip);
        // Bind player inventory at the standard low position
        this.bindPlayerInventory(ip, 0, 251 - 82);
    }

    /**
     * Set up the network tool ("toolbox") if the player has one.
     * Toolbox is rendered at y=183 in the GUI.
     */
    private void setupToolbox(InventoryPlayer ip) {
        World w = this.part.getHostWorld();
        BlockPos pos = this.part.getHostPos();
        if (w == null || pos == null) return;

        final IInventory pi = ip;
        for (int x = 0; x < pi.getSizeInventory(); x++) {
            final ItemStack pii = pi.getStackInSlot(x);
            if (!pii.isEmpty() && pii.getItem() instanceof ToolNetworkTool) {
                this.lockPlayerInventorySlot(x);
                this.toolboxSlot = x;
                this.toolboxInventory = (NetworkToolViewer) ((IGuiItem) pii.getItem())
                    .getGuiObject(pii, w, pos);
                break;
            }
        }

        if (this.hasToolbox()) {
            for (int v = 0; v < 3; v++) {
                for (int u = 0; u < 3; u++) {
                    SlotRestrictedInput slot = new SlotRestrictedInput(
                        SlotRestrictedInput.PlacableItemType.UPGRADES,
                        this.toolboxInventory.getInternalInventory(),
                        u + v * 3, 186 + u * 18, 191 + v * 18,
                        ip);
                    slot.setPlayerSide();
                    this.addSlotToContainer(slot);
                }
            }
        }
    }

    public boolean hasToolbox() {
        return this.toolboxInventory != null;
    }

    // ========================= Listener Registration (page sync fix) =========================

    /**
     * Override addListener to send the full filter state to new listeners
     * via PacketResourceSlot instead of vanilla's sendAllContents.
     * <p>
     * Vanilla's {@code Container.addListener()} sends all slot contents BEFORE
     * AE2's {@code detectAndSendChanges()} sends the {@code @GuiSync} fields.
     */
    @Override
    public void addListener(@Nonnull IContainerListener listener) {
        if (this.listeners.contains(listener)) {
            throw new IllegalArgumentException("Listener already listening");
        }

        this.listeners.add(listener);

        // Send @GuiSync fields first so the client knows the current page
        this.detectAndSendChanges();

        // Send full filter state to the new listener
        if (Platform.isServer() && listener instanceof EntityPlayerMP) {
            int totalSlots = this.part.getTotalPages() * PartSubnetProxyFront.SLOTS_PER_PAGE;
            Map<Integer, Object> fullFilterMap = new HashMap<>();

            for (int i = 0; i < totalSlots; i++) {
                ItemStack stack = this.part.getConfigInventory().getStackInSlot(i);
                IAEItemStack ae = stack.isEmpty() ? null : AEItemStack.fromItemStack(stack);
                fullFilterMap.put(i, ae);
                this.serverFilterCache.put(i, ae == null ? null : ae.copy());
            }

            CellsNetworkHandler.INSTANCE.sendTo(
                new PacketResourceSlot(ResourceType.ITEM, fullFilterMap),
                (EntityPlayerMP) listener
            );
        }
    }

    // ========================= IOptionalSlotHost (for upgrade slots) =========================

    @Override
    public boolean isSlotEnabled(int idx) {
        // Each upgrade slot has its index as the group number.
        // Slot is enabled if its index is < configured upgrade count.
        return idx < CellsConfig.general.subnetProxyUpgradeSlots;
    }

    // ========================= Page Navigation =========================

    public void setCurrentPage(int page) {
        this.currentPage = Math.max(0, Math.min(page, this.totalPages - 1));

        if (Platform.isServer()) this.part.setCurrentPage(this.currentPage);
    }

    public void nextPage() {
        if (this.currentPage < this.totalPages - 1) setCurrentPage(this.currentPage + 1);
    }

    public void prevPage() {
        if (this.currentPage > 0) setCurrentPage(this.currentPage - 1);
    }

    // ========================= Filter Mode =========================

    public ResourceType getFilterMode() {
        return ResourceType.fromOrdinal(this.filterMode);
    }

    public void setFilterMode(ResourceType mode) {
        this.filterMode = mode.ordinal();
        if (Platform.isServer()) this.part.setFilterMode(mode);
    }

    // ========================= Fuzzy Mode =========================

    public FuzzyMode getFuzzyMode() {
        int ordinal = this.fuzzyMode;
        FuzzyMode[] values = FuzzyMode.values();
        if (ordinal >= 0 && ordinal < values.length) return values[ordinal];

        return FuzzyMode.IGNORE_ALL;
    }

    public void setFuzzyMode(FuzzyMode mode) {
        this.fuzzyMode = mode.ordinal();
        if (Platform.isServer()) this.part.setFuzzyMode(mode);
    }

    // ========================= Priority =========================

    public void setPriority(int newValue) {
        this.priority = newValue;
        if (Platform.isServer()) this.part.setPriority(newValue);
    }

    // ========================= Sync =========================

    @Override
    public void detectAndSendChanges() {
        this.verifyPermissions(SecurityPermissions.BUILD, false);

        if (Platform.isServer()) {
            // Don't read currentPage from part here, it's set by setCurrentPage()
            // and reading it back would cause race conditions with the page change
            // packet (the packet's scheduled task may not have run yet, causing
            // the server to sync the old page back to the client).
            this.totalPages = this.part.getTotalPages();
            // Clamp currentPage if totalPages decreased (capacity card removed)
            if (this.currentPage >= this.totalPages) {
                this.currentPage = Math.max(0, this.totalPages - 1);
                this.part.setCurrentPage(this.currentPage);
            }
            this.filterMode = this.part.getFilterMode().ordinal();
            this.fuzzyMode = this.part.getFuzzyMode().ordinal();
            this.availableUpgrades = CellsConfig.general.subnetProxyUpgradeSlots;
            this.priority = this.part.getPriority();
            this.hasInsertionCard = this.part.hasInsertionCard() ? 1 : 0;
            this.enabledChannels = this.part.getEnabledChannelsBitmask();

            // Validate toolbox
            if (this.hasToolbox()) {
                ItemStack currentItem = this.getPlayerInv().getStackInSlot(this.toolboxSlot);
                if (currentItem.isEmpty() || currentItem != this.toolboxInventory.getItemStack()) {
                    if (!ItemStack.areItemsEqual(this.toolboxInventory.getItemStack(), currentItem)) {
                        this.setValidContainer(false);
                    }
                }
            }
        }

        this.standardDetectAndSendChanges();

        // Server-side filter sync: diff each config slot against what was
        // last sent and send PacketResourceSlot for any changes
        if (Platform.isServer()) {
            int totalSlots = this.part.getTotalPages() * PartSubnetProxyFront.SLOTS_PER_PAGE;

            for (int i = 0; i < totalSlots; i++) {
                ItemStack current = this.part.getConfigInventory().getStackInSlot(i);
                IAEItemStack aeCurrent = current.isEmpty() ? null : AEItemStack.fromItemStack(current);
                IAEItemStack cached = this.serverFilterCache.get(i);

                boolean changed;
                if (aeCurrent == null && cached == null) {
                    changed = false;
                } else if (aeCurrent == null || cached == null) {
                    changed = true;
                } else {
                    changed = !aeCurrent.equals(cached);
                }

                if (changed) {
                    this.serverFilterCache.put(i, aeCurrent == null ? null : aeCurrent.copy());

                    for (IContainerListener listener : this.listeners) {
                        if (listener instanceof EntityPlayerMP) {
                            CellsNetworkHandler.INSTANCE.sendTo(
                                new PacketResourceSlot(ResourceType.ITEM, i, aeCurrent),
                                (EntityPlayerMP) listener
                            );
                        }
                    }
                }
            }
        }
    }

    /**
     * Calls the AEBaseContainer's detectAndSendChanges which handles
     * @GuiSync field synchronization and slot change detection.
     */
    protected void standardDetectAndSendChanges() {
        super.detectAndSendChanges();
    }

    // ========================= Filter Updates via PacketResourceSlot =========================

    /**
     * Get the client-side filter for a given absolute slot index.
     * Used by SubnetProxyFilterWidget for rendering.
     */
    @Nullable
    public IAEItemStack getClientFilter(int slot) {
        return this.clientFilterCache.get(slot);
    }

    /**
     * Get the total number of filter slots across all pages.
     * Used by SubnetProxyFilterWidget for client-side duplicate detection.
     */
    public int getTotalFilterSlots() {
        return this.totalPages * PartSubnetProxyFront.SLOTS_PER_PAGE;
    }

    /**
     * Receive resource slot updates from PacketResourceSlot.
     * <p>
     * On client: updates the client filter cache for GUI rendering.
     * On server: validates, converts, and stores in the config inventory.
     */
    @Override
    public void receiveResourceSlots(ResourceType type, Map<Integer, Object> resources) {
        if (type != ResourceType.ITEM) return;

        for (Map.Entry<Integer, Object> entry : resources.entrySet()) {
            int slot = entry.getKey();
            IAEItemStack stack = (IAEItemStack) entry.getValue();

            if (Platform.isClient()) {
                // Client: update render cache
                if (stack == null) {
                    this.clientFilterCache.remove(slot);
                } else {
                    this.clientFilterCache.put(slot, stack);
                }
            } else {
                // Server: validate slot range and store in config inventory
                int totalSlots = this.part.getTotalPages() * PartSubnetProxyFront.SLOTS_PER_PAGE;
                if (slot < 0 || slot >= totalSlots) continue;

                if (stack == null) {
                    this.part.getConfigInventory().setStackInSlot(slot, ItemStack.EMPTY);
                } else {
                    ItemStack filterStack = stack.createItemStack();
                    filterStack.setCount(1);

                    // Duplicate filter protection: reject if the same item
                    // already exists in any other filter slot
                    boolean duplicate = false;
                    for (int j = 0; j < totalSlots; j++) {
                        if (j == slot) continue;

                        ItemStack existing = this.part.getConfigInventory().getStackInSlot(j);
                        if (!existing.isEmpty() && Platform.itemComparisons().isSameItem(existing, filterStack)) {
                            duplicate = true;
                            break;
                        }
                    }

                    if (duplicate) continue;

                    this.part.getConfigInventory().setStackInSlot(slot, filterStack);
                }
            }
        }
    }

    // ========================= Error Reporting for Slot Interactions =========================

    /**
     * Intercepts server-side slot actions for non-filter slots.
     * <p>
     * Filter slots are now GUI widgets (SubnetProxyFilterWidget) synced via
     * PacketResourceSlot, so they don't appear in inventorySlots.
     * This override only handles upgrade slots and other container slots.
     */
    @Override
    public void doAction(final EntityPlayerMP player, final InventoryAction action, final int slot, final long id) {
        super.doAction(player, action, slot, id);
    }

    /**
     * Intercepts shift-click to add filter-conversion with error reporting.
     * <p>
     * Upgrade cards (capacity, fuzzy, inverter) are allowed through to
     * the base handler without filter validation, so they can be
     * shift-clicked into/out of upgrade slots normally.
     */
    @Override
    public ItemStack transferStackInSlot(final EntityPlayer player, final int idx) {
        if (idx < 0 || idx >= this.inventorySlots.size()) return super.transferStackInSlot(player, idx);

        final Slot clickSlot = this.getSlot(idx);

        // Only intercept when shift-clicking FROM player inventory
        if (!(clickSlot instanceof AppEngSlot) || !((AppEngSlot) clickSlot).isPlayerSide()) {
            return super.transferStackInSlot(player, idx);
        }

        ItemStack tis = clickSlot.getStack();
        if (tis.isEmpty()) return super.transferStackInSlot(player, idx);

        // Skip filter validation for items that can go into upgrade slots.
        // The base handler will route them to the correct non-fake slot.
        if (isUpgradeCard(tis)) return super.transferStackInSlot(player, idx);

        // Try to convert the item for the current filter mode
        ItemStack converted = SlotFakeConvertingFilter.testConvertForMode(tis, getFilterMode());
        if (converted == null) {
            if (player instanceof EntityPlayerMP) {
                ServerMessageHelper.error((EntityPlayerMP) player,
                    "message.cells.not_valid_content",
                    new TextComponentTranslation(getTypeLocalizationKey()));
            }

            return ItemStack.EMPTY;
        }

        // Check for duplicates across ALL filter slots in the config inventory
        int totalSlots = Math.min(this.part.getTotalPages() * PartSubnetProxyFront.SLOTS_PER_PAGE,
            this.part.getConfigInventory().getSlots());

        for (int i = 0; i < totalSlots; i++) {
            final ItemStack destination = this.part.getConfigInventory().getStackInSlot(i);
            if (!destination.isEmpty() && Platform.itemComparisons().isSameItem(destination, converted)) {
                if (player instanceof EntityPlayerMP) {
                    ServerMessageHelper.warning((EntityPlayerMP) player,
                        "message.cells.filter_duplicate");
                }

                return ItemStack.EMPTY;
            }
        }

        // Place the converted item in the first empty filter slot
        for (int i = 0; i < totalSlots; i++) {
            if (this.part.getConfigInventory().getStackInSlot(i).isEmpty()) {
                this.part.getConfigInventory().setStackInSlot(i, converted.copy());
                return ItemStack.EMPTY;
            }
        }

        // No empty slot found
        return ItemStack.EMPTY;
    }

    /**
     * Check if the given stack is a valid upgrade card for this container's
     * upgrade slots (capacity, fuzzy, inverter cards).
     */
    private boolean isUpgradeCard(ItemStack stack) {
        for (final Object inventorySlot : this.inventorySlots) {
            if (inventorySlot instanceof OptionalSlotRestrictedInput) {
                OptionalSlotRestrictedInput rs = (OptionalSlotRestrictedInput) inventorySlot;
                if (rs.isItemValid(stack)) return true;
            }
        }
        return false;
    }

    public PartSubnetProxyFront getPart() {
        return this.part;
    }

    /** True if the synced bitmask has the bit for {@code type} set. */
    public boolean isChannelEnabled(ResourceType type) {
        return (this.enabledChannels & (1 << type.ordinal())) != 0;
    }

    /** True if no channel is currently enabled (used to render the warning overlay). */
    public boolean hasNoChannelsEnabled() {
        return this.enabledChannels == 0;
    }

    // ========================= IQuickAddFilterContainer =========================

    @Override
    public ResourceType getQuickAddResourceType() {
        return this.getFilterMode();
    }

    @Override
    public boolean isResourceInFilter(@Nonnull Object resource) {
        int totalSlots = Math.min(this.part.getTotalPages() * PartSubnetProxyFront.SLOTS_PER_PAGE,
            this.part.getConfigInventory().getSlots());

        for (int i = 0; i < totalSlots; i++) {
            ItemStack existing = this.part.getConfigInventory().getStackInSlot(i);
            if (existing.isEmpty()) continue;

            // Compare based on resource type
            if (resource instanceof IAEItemStack) {
                IAEItemStack aeExisting = AEItemStack.fromItemStack(existing);
                if (aeExisting != null && aeExisting.equals(resource)) return true;
            } else if (resource instanceof IAEFluidStack) {
                if (existing.getItem() instanceof FluidDummyItem) {
                    IAEFluidStack aeExisting = AEFluidStack.fromFluidStack(
                        ((FluidDummyItem) existing.getItem()).getFluidStack(existing));
                    if (aeExisting != null && aeExisting.equals(resource)) return true;
                }
            }

            // Gas: delegate to helper
            if (MekanismEnergisticsIntegration.isModLoaded()
                    && SubnetProxyGasHelper.isGasResourceMatch(existing, resource)) {
                return true;
            }

            // Essentia: delegate to helper
            if (ThaumicEnergisticsIntegration.isModLoaded()
                    && SubnetProxyEssentiaHelper.isEssentiaResourceMatch(existing, resource)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean quickAddToFilter(@Nonnull Object resource, @Nullable EntityPlayer player) {
        int totalSlots = Math.min(this.part.getTotalPages() * PartSubnetProxyFront.SLOTS_PER_PAGE,
            this.part.getConfigInventory().getSlots());

        // Find the first empty slot
        for (int i = 0; i < totalSlots; i++) {
            ItemStack existing = this.part.getConfigInventory().getStackInSlot(i);
            if (!existing.isEmpty()) continue;

            // Convert the resource to an ItemStack for the config inventory
            ItemStack filterStack = resourceToFilterStack(resource);
            if (filterStack.isEmpty()) return false;

            this.part.getConfigInventory().setStackInSlot(i, filterStack);
            return true;
        }

        return false;
    }

    @Override
    public String getTypeLocalizationKey() {
        return "cells.type." + this.getFilterMode().name().toLowerCase();
    }

    /**
     * Convert a resource object (IAEItemStack, IAEFluidStack, etc.) to an ItemStack
     * suitable for storage in the config inventory.
     */
    private static ItemStack resourceToFilterStack(Object resource) {
        if (resource instanceof IAEItemStack) {
            ItemStack stack = ((IAEItemStack) resource).createItemStack();
            stack.setCount(1);
            return stack;
        }

        if (resource instanceof IAEFluidStack) {
            return SubnetProxyContainerHelper.fluidToFilterStack((IAEFluidStack) resource);
        }

        // Gas and Essentia are handled by their respective helpers in the part
        // via reflection-free @Optional.Method helpers
        return SubnetProxyContainerHelper.modResourceToFilterStack(resource);
    }

    // ========================= JEI Recipe Transfer =========================

    /**
     * Add multiple filter ItemStacks to the config inventory, silently skipping
     * duplicates and ignoring space shortages.
     * <p>
     * Used by the JEI recipe transfer handler to batch-add recipe ingredients.
     * Unlike quick-add, this does NOT report errors, duplicates and full
     * inventory are silently ignored.
     *
     * @param filterStacks The pre-converted filter ItemStacks to add
     */
    public void addFilterStacksSilently(@Nonnull List<ItemStack> filterStacks) {
        int totalSlots = Math.min(this.part.getTotalPages() * PartSubnetProxyFront.SLOTS_PER_PAGE,
            this.part.getConfigInventory().getSlots());

        for (ItemStack filterStack : filterStacks) {
            if (filterStack.isEmpty()) continue;

            // Check for duplicates across ALL existing filter slots
            boolean duplicate = false;
            for (int i = 0; i < totalSlots; i++) {
                ItemStack existing = this.part.getConfigInventory().getStackInSlot(i);
                if (!existing.isEmpty() && Platform.itemComparisons().isSameItem(existing, filterStack)) {
                    duplicate = true;
                    break;
                }
            }
            if (duplicate) continue;

            // Find the first empty slot
            boolean placed = false;
            for (int i = 0; i < totalSlots; i++) {
                if (this.part.getConfigInventory().getStackInSlot(i).isEmpty()) {
                    this.part.getConfigInventory().setStackInSlot(i, filterStack.copy());
                    placed = true;
                    break;
                }
            }

            // If no space, silently stop (remaining items won't fit either)
            if (!placed) break;
        }
    }
}
