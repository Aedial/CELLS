package com.cells.integration.mekanismenergistics;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextComponentTranslation;

import appeng.api.parts.IPart;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.SlotNormal;
import appeng.helpers.InventoryAction;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.Platform;

import com.mekeng.github.common.me.data.IAEGasStack;
import com.mekeng.github.common.me.data.impl.AEGasStack;
import mekanism.api.gas.GasStack;

import com.cells.gui.QuickAddHelper;
import com.cells.network.CellsNetworkHandler;


/**
 * Container for Gas Import/Export Interface GUIs.
 * Handles gas filter synchronization and tank interactions.
 * <p>
 * Since AE2 doesn't natively support gas sync, we implement our own sync mechanism
 * using {@link PacketGasSlot} for client-server communication.
 */
public class ContainerGasInterface extends AEBaseContainer implements IGasSyncContainer {

    private final IGasInterfaceHost host;
    private final Map<Integer, IAEGasStack> clientFilterCache = new HashMap<>();
    private final Map<Integer, IAEGasStack> serverFilterCache = new HashMap<>();

    @GuiSync(0)
    public long maxSlotSize = GasInterfaceLogic.DEFAULT_MAX_SLOT_SIZE;

    @GuiSync(1)
    public long pollingRate = 0;

    @GuiSync(2)
    public int currentPage = 0;

    @GuiSync(3)
    public int totalPages = 1;

    /**
     * Constructor for tile entity hosts.
     */
    public ContainerGasInterface(final InventoryPlayer ip, final TileEntity tile) {
        this(ip, (IGasInterfaceHost) tile, tile);
    }

    /**
     * Constructor for part hosts.
     */
    public ContainerGasInterface(final InventoryPlayer ip, final IPart part) {
        this(ip, (IGasInterfaceHost) part, part);
    }

    /**
     * Common constructor that both tile and part use.
     */
    private ContainerGasInterface(final InventoryPlayer ip, final IGasInterfaceHost host, final Object anchor) {
        super(ip, anchor instanceof TileEntity ? (TileEntity) anchor : null, anchor instanceof IPart ? (IPart) anchor : null);
        this.host = host;

        // Add 4 upgrade slots at the right side of the GUI
        for (int i = 0; i < GasInterfaceLogic.UPGRADE_SLOTS; i++) {
            this.addSlotToContainer(new SlotUpgrade(
                host.getUpgradeInventory(), i, 186, 25 + i * 18, host
            ));
        }

        // Bind player inventory
        this.bindPlayerInventory(ip, 0, 174);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        if (Platform.isServer()) {
            // Sync gas filters by comparing with cache
            for (int i = 0; i < GasInterfaceLogic.FILTER_SLOTS; i++) {
                IAEGasStack current = this.host.getFilterGas(i);
                IAEGasStack cached = this.serverFilterCache.get(i);

                boolean different = (current == null && cached != null) ||
                                   (current != null && cached == null) ||
                                   (current != null && !current.equals(cached));

                if (different) {
                    this.serverFilterCache.put(i, current == null ? null : current.copy());

                    // Send diff to all listeners
                    Map<Integer, IAEGasStack> diff = Collections.singletonMap(i, current);
                    ByteBuf buf = Unpooled.buffer();
                    writeGasMap(buf, diff);
                    CellsNetworkHandler.INSTANCE.sendToAll(new PacketGasSlot(buf));
                }
            }
        }

        // Sync GuiSync values
        if (this.maxSlotSize != this.host.getMaxSlotSize()) {
            this.maxSlotSize = this.host.getMaxSlotSize();
        }
        if (this.pollingRate != this.host.getPollingRate()) {
            this.pollingRate = this.host.getPollingRate();
        }
        if (this.currentPage != this.host.getCurrentPage()) {
            this.currentPage = this.host.getCurrentPage();
        }
        if (this.totalPages != this.host.getTotalPages()) {
            this.totalPages = this.host.getTotalPages();
        }
    }

    private void writeGasMap(ByteBuf buf, Map<Integer, IAEGasStack> map) {
        buf.writeInt(map.size());
        for (Map.Entry<Integer, IAEGasStack> entry : map.entrySet()) {
            buf.writeInt(entry.getKey());
            IAEGasStack gas = entry.getValue();
            if (gas == null) {
                buf.writeBoolean(false);
            } else {
                buf.writeBoolean(true);
                try {
                    gas.writeToPacket(buf);
                } catch (IOException e) {
                    // Should not happen for ByteBuf
                }
            }
        }
    }

    /**
     * Sets the current page for viewing, clamped to valid range.
     */
    public void setCurrentPage(int page) {
        int newPage = Math.max(0, Math.min(page, this.totalPages - 1));
        this.currentPage = newPage;
        this.host.setCurrentPage(newPage);
    }

    public void nextPage() {
        if (this.currentPage < this.totalPages - 1) setCurrentPage(this.currentPage + 1);
    }

    public void prevPage() {
        if (this.currentPage > 0) setCurrentPage(this.currentPage - 1);
    }

    @Override
    public void addListener(@Nonnull IContainerListener listener) {
        super.addListener(listener);

        // Send full gas filter inventory to new listener
        if (Platform.isServer()) {
            Map<Integer, IAEGasStack> fullMap = new HashMap<>();
            for (int i = 0; i < GasInterfaceLogic.FILTER_SLOTS; i++) {
                IAEGasStack gas = this.host.getFilterGas(i);
                fullMap.put(i, gas);
                this.serverFilterCache.put(i, gas == null ? null : gas.copy());
            }

            ByteBuf buf = Unpooled.buffer();
            writeGasMap(buf, fullMap);
            CellsNetworkHandler.INSTANCE.sendToAll(new PacketGasSlot(buf));
        }
    }

    /**
     * Receive gas slot updates from server (on client) or from client (on server).
     */
    @Override
    public void receiveGasSlots(Map<Integer, IAEGasStack> gases) {
        // On client, just update the display cache
        if (this.host.getHostWorld() != null && this.host.getHostWorld().isRemote) {
            this.clientFilterCache.putAll(gases);
            return;
        }

        // Get the player for feedback messages (first listener should be the player)
        EntityPlayer player = null;
        for (IContainerListener listener : this.listeners) {
            if (listener instanceof EntityPlayer) {
                player = (EntityPlayer) listener;
                break;
            }
        }

        final boolean isExport = this.host.isExport();

        // On server, validate each change before applying
        for (Map.Entry<Integer, IAEGasStack> entry : gases.entrySet()) {
            int slot = entry.getKey();
            IAEGasStack gas = entry.getValue();

            // Validate slot index
            if (slot < 0 || slot >= GasInterfaceLogic.FILTER_SLOTS) continue;

            // Null gas means clearing the filter
            if (gas == null) {
                // Import: only clear if tank is empty (prevent orphans)
                if (!isExport && !this.host.isTankEmpty(slot)) {
                    if (player != null) {
                        player.sendMessage(new TextComponentTranslation("cells.storage_not_empty"));
                    }
                    continue;
                }

                this.host.setFilterGas(slot, null);
                continue;
            }

            // Import: prevent filter changes if the corresponding tank has fluid
            if (!isExport && !this.host.isTankEmpty(slot)) {
                if (player != null) {
                    player.sendMessage(new TextComponentTranslation("cells.storage_not_empty"));
                }
                continue;
            }

            // Prevent duplicate gas filters
            GasStackKey newKey = GasStackKey.of(gas.getGasStack());
            if (newKey == null) continue;

            boolean isDuplicate = false;

            for (int i = 0; i < GasInterfaceLogic.FILTER_SLOTS; i++) {
                if (i == slot) continue;

                IAEGasStack otherGas = this.host.getFilterGas(i);
                if (otherGas == null) continue;

                GasStackKey otherKey = GasStackKey.of(otherGas.getGasStack());
                if (otherKey != null && otherKey.equals(newKey)) {
                    isDuplicate = true;
                    break;
                }
            }

            if (isDuplicate) {
                if (player != null) {
                    player.sendMessage(new TextComponentTranslation("message.cells.filter_duplicate"));
                }
            } else {
                this.host.setFilterGas(slot, gas);
            }
        }
    }

    /**
     * Get a gas filter from the client cache (for GUI rendering).
     */
    public IAEGasStack getClientFilterGas(int slot) {
        if (Platform.isServer()) return this.host.getFilterGas(slot);
        return this.clientFilterCache.get(slot);
    }

    public IGasInterfaceHost getHost() {
        return this.host;
    }

    public void setMaxSlotSize(int size) {
        this.host.setMaxSlotSize(size);
    }

    public void setPollingRate(int ticks) {
        this.host.setPollingRate(ticks);
    }

    /**
     * Clear all filters. Import only clears where tank is empty (prevents orphans).
     * Export clears all and returns gases to network. Delegates to host.
     */
    public void clearFilters() {
        this.host.clearFilters();
    }

    @Override
    public void onSlotChange(final Slot s) {
        super.onSlotChange(s);

        if (s instanceof SlotUpgrade) {
            host.refreshUpgrades();
        }
    }

    /**
     * Handle inventory actions from the GUI.
     * Gas interfaces don't support pouring from containers (unlike fluid interfaces)
     * since there's no universal gas container item like fluid buckets.
     */
    @Override
    public void doAction(EntityPlayerMP player, InventoryAction action, int slot, long id) {
        // Gas interfaces don't support EMPTY_ITEM action since there's no universal
        // gas container item.
        super.doAction(player, action, slot, id);
    }

    /**
     * Handle shift-click: if the clicked item is a gas container, extract its gas
     * and set it as a filter in the first available slot.
     * The actual item stays in place (return empty), only the filter is set.
     * <p>
     * Note: transferStackInSlot is called on SERVER side for actual execution.
     * On client, it's just for prediction. The server handles the actual filter setting,
     * and detectAndSendChanges syncs the result back to the client.
     * We do NOT send a packet here because that would cause a duplicate message
     * (server already processed the shift-click before receiving the packet).
     */
    @Override
    @Nonnull
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        // Only process on server side - client prediction is not needed for filter slots
        if (player.world.isRemote) return ItemStack.EMPTY;

        if (slotIndex < 0 || slotIndex >= inventorySlots.size()) return ItemStack.EMPTY;

        Slot slot = inventorySlots.get(slotIndex);
        if (slot == null || !slot.getHasStack()) return ItemStack.EMPTY;

        // Only process shift-clicks from the player inventory (not from upgrade slots)
        // Our upgrade slots are added first, so player inventory starts after them
        int upgradeSlotCount = GasInterfaceLogic.UPGRADE_SLOTS;
        if (slotIndex < upgradeSlotCount) return ItemStack.EMPTY;

        ItemStack clickedStack = slot.getStack();
        if (clickedStack.isEmpty()) return ItemStack.EMPTY;

        // Try to extract gas from the item
        GasStack gas = QuickAddHelper.getGasFromItemStack(clickedStack);
        if (gas == null || gas.amount <= 0) return ItemStack.EMPTY;

        // Check for duplicates across all filter slots
        GasStackKey newKey = GasStackKey.of(gas);
        if (newKey == null) return ItemStack.EMPTY;

        for (int i = 0; i < GasInterfaceLogic.FILTER_SLOTS; i++) {
            IAEGasStack existingFilter = this.host.getFilterGas(i);
            if (existingFilter != null) {
                GasStackKey existingKey = GasStackKey.of(existingFilter.getGasStack());
                if (existingKey != null && existingKey.equals(newKey)) {
                    // Already in filter, show duplicate message
                    player.sendMessage(new TextComponentTranslation("message.cells.filter_duplicate"));
                    return ItemStack.EMPTY;
                }
            }
        }

        // Find the first empty filter slot
        final boolean isExport = this.host.isExport();
        for (int i = 0; i < GasInterfaceLogic.FILTER_SLOTS; i++) {
            IAEGasStack existingFilter = this.host.getFilterGas(i);
            if (existingFilter != null) continue;

            // Import mode: only set filter if tank is empty
            if (!isExport && !this.host.isTankEmpty(i)) continue;

            // Found an available slot, set the filter
            // Server sets directly, detectAndSendChanges will sync to client
            IAEGasStack aeGas = AEGasStack.of(gas);
            this.host.setFilterGas(i, aeGas);

            return ItemStack.EMPTY;
        }

        // No empty slot found
        return ItemStack.EMPTY;
    }

    /**
     * Custom slot for upgrades that only accepts specific upgrade cards.
     */
    private static class SlotUpgrade extends SlotNormal {
        private final IGasInterfaceHost host;

        public SlotUpgrade(AppEngInternalInventory inv, int idx, int x, int y, IGasInterfaceHost host) {
            super(inv, idx, x, y);
            this.host = host;
            this.setIIcon(13 * 16 + 15);
        }

        @Override
        public boolean isItemValid(@Nonnull ItemStack stack) {
            return host.isValidUpgrade(stack);
        }

        @Override
        public int getSlotStackLimit() {
            return 1;
        }

        @Override
        public hasCalculatedValidness getIsValid() {
            return hasCalculatedValidness.Valid;
        }
    }
}
