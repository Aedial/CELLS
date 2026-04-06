package com.cells.client;

import javax.annotation.Nullable;

import net.minecraft.client.renderer.color.IItemColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.cells.cells.configurable.ChannelType;
import com.cells.cells.configurable.ComponentHelper;
import com.cells.cells.configurable.ComponentInfo;
import com.cells.cells.configurable.ItemConfigurableCell;
import com.cells.cells.hyperdensity.compacting.ItemHyperDensityCompactingCell;
import com.cells.cells.hyperdensity.fluid.ItemFluidHyperDensityCell;
import com.cells.cells.hyperdensity.item.ItemHyperDensityCell;
import com.cells.cells.normal.compacting.ItemCompactingCell;

import static com.cells.client.CellTextureColors.*;


/**
 * IItemColor implementation for storage cells with the modular layered texture system.
 * <p>
 * This handler provides colors for the 5 tint layers:
 * <ul>
 *   <li>Layer 0: Frame (no tint)</li>
 *   <li>Layer 1: Outer highlights (tier color with outer brightness)</li>
 *   <li>Layer 2: Inner highlights (tier color with inner brightness)</li>
 *   <li>Layer 3: Inner shape (cell type color with lower brightness)</li>
 *   <li>Layer 4: Outer shape (cell type color with higher brightness)</li>
 * </ul>
 * <p>
 * The colors are determined by:
 * - Cell type: Determines the shape base color (e.g., cyan for configurable item, blue for compacting)
 * - Tier index: Determines the highlight base color and brightness values (0=1k, 1=4k, ..., 11=2g)
 */
public class CellItemColors implements IItemColor {

    /**
     * Singleton instance for reuse across all cell item registrations.
     */
    public static final CellItemColors INSTANCE = new CellItemColors();

    private CellItemColors() {}

    @Override
    public int colorMultiplier(ItemStack stack, int tintIndex) {
        if (stack.isEmpty()) return NO_TINT;

        Item item = stack.getItem();

        // Determine the cell type and tier index
        CellType cellType = getCellType(item, stack);
        if (cellType == null) return NO_TINT;

        int tier = getTierIndex(item, stack);

        return getColorForLayer(cellType, tier, tintIndex);
    }

    /**
     * Determine the CellType for a given item.
     *
     * @param item The cell item
     * @param stack The cell ItemStack (needed for configurable cell channel detection)
     * @return The CellType, or null if unknown
     */
    @Nullable
    private CellType getCellType(Item item, ItemStack stack) {
        // Configurable cells - determine channel type from installed component
        if (item instanceof ItemConfigurableCell) {
            ItemStack component = ComponentHelper.getInstalledComponent(stack);
            ComponentInfo info = ComponentHelper.getComponentInfo(component);
            if (info == null) return CellType.CONFIGURABLE_ITEM;

            ChannelType channel = info.getChannelType();
            switch (channel) {
                case FLUID:    return CellType.CONFIGURABLE_FLUID;
                case GAS:      return CellType.CONFIGURABLE_GAS;
                case ESSENTIA: return CellType.CONFIGURABLE_ESSENTIA;
                case ITEM:
                default:       return CellType.CONFIGURABLE_ITEM;
            }
        }

        if (item instanceof ItemCompactingCell) return CellType.COMPACTING;
        if (item instanceof ItemHyperDensityCompactingCell) return CellType.COMPACTING;
        if (item instanceof ItemHyperDensityCell) return CellType.HYPER_DENSITY_ITEM;
        if (item instanceof ItemFluidHyperDensityCell) return CellType.HYPER_DENSITY_FLUID;

        return null;
    }

    /**
     * Determine the tier index for a given item.
     * Tier indices: 0=1k, 1=4k, 2=16k, 3=64k, 4=256k, 5=1m, 6=4m, 7=16m, 8=64m, 9=256m, 10=1g, 11=2g
     *
     * @param item The cell item
     * @param stack The cell ItemStack
     * @return The tier index (0-11)
     */
    private int getTierIndex(Item item, ItemStack stack) {
        // Configurable cells - get tier from installed component
        if (item instanceof ItemConfigurableCell) {
            ItemStack component = ComponentHelper.getInstalledComponent(stack);
            ComponentInfo info = ComponentHelper.getComponentInfo(component);
            if (info == null) return 0; // Default to tier 0 (1k)

            // Convert tier name to tier index
            return CellTextureColors.getTierIndex(info.getTierName());
        }

        // Standard tiered cells use metadata directly as tier index
        return stack.getMetadata();
    }
}
