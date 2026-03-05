package com.cells.blocks.fluidexportinterface;

import java.util.Collections;
import java.util.Map;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextComponentTranslation;

import appeng.api.parts.IPart;
import appeng.api.storage.data.IAEFluidStack;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.AppEngSlot.hasCalculatedValidness;
import appeng.container.slot.SlotNormal;
import appeng.fluids.container.IFluidSyncContainer;
import appeng.fluids.helper.FluidSyncHelper;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.Platform;

import com.cells.blocks.importinterface.TileImportInterface;
import com.cells.util.FluidStackKey;


/**
 * Container for the Fluid Export Interface GUI.
 * Layout: 4 rows of 9 filter slots (fluid filters, not item slots).
 * Storage is fluid-based (internal tanks), rendered as GuiFluidExportTankSlot.
 * Plus 4 upgrade slots on the right side.
 * <p>
 * Works with both TileFluidExportInterface (block) and PartFluidExportInterface (part).
 */
public class ContainerFluidExportInterface extends AEBaseContainer implements IFluidSyncContainer {

    private final IFluidExportInterfaceInventoryHost host;
    private FluidSyncHelper filterSync = null;

    @GuiSync(0)
    public long maxSlotSize = TileFluidExportInterface.DEFAULT_MAX_SLOT_SIZE;

    @GuiSync(1)
    public long pollingRate = TileImportInterface.DEFAULT_POLLING_RATE;

    @GuiSync(2)
    public int currentPage = 0;

    @GuiSync(3)
    public int totalPages = 1;

    /**
     * Constructor for tile entity.
     */
    public ContainerFluidExportInterface(final InventoryPlayer ip, final TileFluidExportInterface tile) {
        this(ip, tile, tile);
    }

    /**
     * Constructor for part.
     */
    public ContainerFluidExportInterface(final InventoryPlayer ip, final IPart part) {
        this(ip, (IFluidExportInterfaceInventoryHost) part, part instanceof TileEntity ? part : null);
    }

    /**
     * Common constructor that both tile and part use.
     */
    private ContainerFluidExportInterface(final InventoryPlayer ip, final IFluidExportInterfaceInventoryHost host, final Object anchor) {
        super(ip, anchor instanceof TileEntity ? (TileEntity) anchor : null, anchor instanceof IPart ? (IPart) anchor : null);
        this.host = host;

        // Add 4 upgrade slots at the right side of the GUI
        for (int i = 0; i < TileFluidExportInterface.UPGRADE_SLOTS; i++) {
            this.addSlotToContainer(new SlotUpgrade(
                host.getUpgradeInventory(), i, 186, 25 + i * 18, host
            ));
        }

        // Bind player inventory
        this.bindPlayerInventory(ip, 0, 174);
    }

    private FluidSyncHelper getFilterSyncHelper() {
        if (this.filterSync == null) {
            this.filterSync = new FluidSyncHelper(this.host.getFilterInventory(), 0);
        }
        return this.filterSync;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        // Sync fluid filter inventory
        if (Platform.isServer()) this.getFilterSyncHelper().sendDiff(this.listeners);

        if (this.maxSlotSize != this.host.getMaxSlotSize()) this.maxSlotSize = this.host.getMaxSlotSize();
        if (this.pollingRate != this.host.getPollingRate()) this.pollingRate = this.host.getPollingRate();

        // Sync pagination state
        if (this.currentPage != this.host.getCurrentPage()) this.currentPage = this.host.getCurrentPage();
        if (this.totalPages != this.host.getTotalPages()) this.totalPages = this.host.getTotalPages();
    }

    /**
     * Sets the current page for viewing, clamped to valid range.
     */
    public void setCurrentPage(int page) {
        int maxPage = this.totalPages - 1;
        if (page < 0) page = 0;
        if (page > maxPage) page = maxPage;

        this.currentPage = page;
        this.host.setCurrentPage(page);
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
        this.getFilterSyncHelper().sendFull(Collections.singleton(listener));
    }

    @Override
    public void receiveFluidSlots(Map<Integer, IAEFluidStack> fluids) {
        // On client, just update the display cache
        if (this.host.getHostWorld() != null && this.host.getHostWorld().isRemote) {
            this.getFilterSyncHelper().readPacket(fluids);
            return;
        }

        // Get the player for feedback messages
        EntityPlayer player = null;
        for (IContainerListener listener : this.listeners) {
            if (listener instanceof EntityPlayer) {
                player = (EntityPlayer) listener;
                break;
            }
        }

        // On server, validate each change before applying
        for (Map.Entry<Integer, IAEFluidStack> entry : fluids.entrySet()) {
            int slot = entry.getKey();
            IAEFluidStack fluid = entry.getValue();

            // Validate slot index
            if (slot < 0 || slot >= TileFluidExportInterface.FILTER_SLOTS) continue;

            // Null fluid means clearing the filter
            if (fluid == null) {
                this.host.setFilterFluid(slot, null);
                continue;
            }

            // Prevent duplicate fluid filters
            FluidStackKey newKey = FluidStackKey.of(fluid.getFluidStack());
            if (newKey == null) continue;

            boolean isDuplicate = false;
            for (int i = 0; i < TileFluidExportInterface.FILTER_SLOTS; i++) {
                if (i == slot) continue;

                IAEFluidStack otherFluid = this.host.getFilterFluid(i);
                if (otherFluid == null) continue;

                FluidStackKey otherKey = FluidStackKey.of(otherFluid.getFluidStack());
                if (otherKey != null && otherKey.equals(newKey)) {
                    isDuplicate = true;
                    break;
                }
            }

            if (isDuplicate) {
                if (player != null) {
                    player.sendMessage(new TextComponentTranslation("message.cells.export_interface.filter_duplicate"));
                }
            } else {
                this.host.setFilterFluid(slot, fluid);
            }
        }
    }

    public IFluidExportInterfaceInventoryHost getHost() {
        return this.host;
    }

    public void setMaxSlotSize(int size) {
        this.host.setMaxSlotSize(size);
    }

    public void setPollingRate(int ticks) {
        this.host.setPollingRate(ticks);
    }

    /**
     * Clear all filters and return all tank fluids to the network.
     */
    public void clearFilters() {
        this.host.clearFilters();
    }

    @Override
    public void onSlotChange(final Slot s) {
        super.onSlotChange(s);

        if (s instanceof SlotUpgrade) host.refreshUpgrades();
    }

    /**
     * Custom slot for upgrades that only accepts specific upgrade cards.
     */
    private static class SlotUpgrade extends SlotNormal {
        private final IFluidExportInterfaceInventoryHost host;

        public SlotUpgrade(AppEngInternalInventory inv, int idx, int x, int y, IFluidExportInterfaceInventoryHost host) {
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
