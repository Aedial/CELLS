package com.cells.integration.jei.cellview;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEStack;

import com.cells.cells.configurable.ItemConfigurableCell;
import com.cells.cells.hyperdensity.compacting.IItemHyperDensityCompactingCell;
import com.cells.cells.hyperdensity.fluid.IItemFluidHyperDensityCell;
import com.cells.cells.hyperdensity.item.IItemHyperDensityCell;
import com.cells.cells.normal.compacting.IInternalCompactingCell;
import com.cells.integration.thaumicenergistics.ThaumicEnergisticsIntegration;
import com.cells.integration.mekanismenergistics.MekanismEnergisticsIntegration;


/**
 * Helper class for detecting and querying AE2 storage cells.
 * <p>
 * Unlike IStorageCell interface checks, this works with all cells
 * registered through the AE2 cell handler system, including CELLS mod
 * cells that don't directly implement IStorageCell.
 */
public final class CellViewHelper {

    private CellViewHelper() {}

    /**
     * Cell types supported by CELLS mod.
     * UNKNOWN is for non-CELLS cells (like vanilla AE2 or other addons).
     */
    public enum CellType {
        /** Normal compacting cell (single item type with compression chain) */
        COMPACTING,
        /** Hyper-Density item cell (massive storage multiplier) */
        HYPER_DENSITY_ITEM,
        /** Hyper-Density fluid cell (massive storage multiplier) */
        HYPER_DENSITY_FLUID,
        /** Hyper-Density compacting cell (HD multiplier + compression) */
        HYPER_DENSITY_COMPACTING,
        /** Configurable cell (component-based, equal distribution) */
        CONFIGURABLE,
        /** Unknown/external cell (vanilla AE2 or other addon) */
        UNKNOWN
    }

    /**
     * Detect the CELLS mod cell type for an ItemStack.
     *
     * @param stack The cell item
     * @return The detected cell type, or UNKNOWN if not a CELLS cell
     */
    @Nonnull
    public static CellType getCellType(@Nonnull ItemStack stack) {
        if (stack.isEmpty()) return CellType.UNKNOWN;

        // Check most specific types first
        if (stack.getItem() instanceof IItemHyperDensityCompactingCell) {
            return CellType.HYPER_DENSITY_COMPACTING;
        }

        if (stack.getItem() instanceof IItemHyperDensityCell) {
            return CellType.HYPER_DENSITY_ITEM;
        }

        if (stack.getItem() instanceof IItemFluidHyperDensityCell) {
            return CellType.HYPER_DENSITY_FLUID;
        }

        if (stack.getItem() instanceof IInternalCompactingCell) {
            return CellType.COMPACTING;
        }

        if (stack.getItem() instanceof ItemConfigurableCell) {
            return CellType.CONFIGURABLE;
        }

        return CellType.UNKNOWN;
    }

    /**
     * Check if a cell is a CELLS mod cell (not external/unknown).
     */
    public static boolean isCellsModCell(@Nonnull ItemStack stack) {
        return getCellType(stack) != CellType.UNKNOWN;
    }

    /**
     * Result from cell detection containing the storage channel and inventory handler.
     * Uses raw types internally to avoid Java 8 generics inference issues.
     */
    public static class CellInfo {
        private final IStorageChannel<?> channel;
        private final ICellInventoryHandler<?> handler;

        CellInfo(IStorageChannel<?> channel, ICellInventoryHandler<?> handler) {
            this.channel = channel;
            this.handler = handler;
        }

        @SuppressWarnings("unchecked")
        public <T extends IAEStack<T>> IStorageChannel<T> getChannel() {
            return (IStorageChannel<T>) channel;
        }

        @SuppressWarnings("unchecked")
        public <T extends IAEStack<T>> ICellInventoryHandler<T> getHandler() {
            return (ICellInventoryHandler<T>) handler;
        }
    }

    /**
     * Check if an ItemStack is a valid AE2 storage cell.
     * <p>
     * Tries all known storage channels (Item, Fluid, Essentia, Gas) to detect
     * if the stack can be used as a cell in any of them.
     *
     * @param stack The item to check
     * @return true if the stack is a valid cell for any storage channel
     */
    public static boolean isCell(@Nonnull ItemStack stack) {
        if (stack.isEmpty()) return false;

        return getCellInfo(stack) != null;
    }

    /**
     * Get the cell information for an ItemStack, including its storage channel
     * and inventory handler.
     * <p>
     * Tries channels in order: Item, Fluid, Essentia (if available), Gas (if available).
     *
     * @param stack The cell item
     * @return CellInfo containing channel and handler, or null if not a valid cell
     */
    @Nullable
    public static CellInfo getCellInfo(@Nonnull ItemStack stack) {
        if (stack.isEmpty()) return null;

        // Try Item channel
        IItemStorageChannel itemChannel = AEApi.instance().storage()
            .getStorageChannel(IItemStorageChannel.class);
        ICellInventoryHandler<?> handler = AEApi.instance().registries().cell()
            .getCellInventory(stack, null, itemChannel);
        if (handler != null) return new CellInfo(itemChannel, handler);

        // Try Fluid channel
        IFluidStorageChannel fluidChannel = AEApi.instance().storage()
            .getStorageChannel(IFluidStorageChannel.class);
        handler = AEApi.instance().registries().cell()
            .getCellInventory(stack, null, fluidChannel);
        if (handler != null) return new CellInfo(fluidChannel, handler);

        // Try Essentia channel (Thaumic Energistics)
        if (ThaumicEnergisticsIntegration.isModLoaded()) {
            IStorageChannel<?> essentiaChannel = ThaumicEnergisticsIntegration.getStorageChannel();
            if (essentiaChannel != null) {
                handler = AEApi.instance().registries().cell()
                    .getCellInventory(stack, null, essentiaChannel);
                if (handler != null) return new CellInfo(essentiaChannel, handler);
            }
        }

        // Try Gas channel (Mekanism Energistics)
        if (MekanismEnergisticsIntegration.isModLoaded()) {
            IStorageChannel<?> gasChannel = MekanismEnergisticsIntegration.getStorageChannel();
            if (gasChannel != null) {
                handler = AEApi.instance().registries().cell()
                    .getCellInventory(stack, null, gasChannel);
                if (handler != null) return new CellInfo(gasChannel, handler);
            }
        }

        return null;
    }
}
