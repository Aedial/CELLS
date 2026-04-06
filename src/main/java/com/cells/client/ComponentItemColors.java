package com.cells.client;

import net.minecraft.client.renderer.color.IItemColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.cells.cells.hyperdensity.compacting.ItemHyperDensityCompactingComponent;
import com.cells.cells.hyperdensity.fluid.ItemFluidHyperDensityComponent;
import com.cells.cells.hyperdensity.item.ItemHyperDensityComponent;
import com.cells.cells.normal.compacting.ItemCompactingComponent;
import com.cells.client.CellTextureColors.CellType;


/**
 * IItemColor implementation for storage cell components with the layered texture system.
 * <p>
 * Component textures use 2 layers:
 * <ul>
 *   <li>Layer 0: Wave (tinted by component type color)</li>
 *   <li>Layer 1: Frame (no tint)</li>
 * </ul>
 * <p>
 * The wave color is determined by the component type (compacting, hyper_density, etc.).
 */
public class ComponentItemColors implements IItemColor {

    /**
     * Singleton instance for reuse across all component item registrations.
     */
    public static final ComponentItemColors INSTANCE = new ComponentItemColors();

    /**
     * Return value indicating no tint should be applied.
     */
    private static final int NO_TINT = 0xFFFFFFFF;

    private ComponentItemColors() {}

    @Override
    public int colorMultiplier(ItemStack stack, int tintIndex) {
        if (stack.isEmpty()) return NO_TINT;

        // Only tint layer 0 (wave layer)
        if (tintIndex != 0) return NO_TINT;

        Item item = stack.getItem();
        CellType componentType = getComponentType(item);
        if (componentType == null) return NO_TINT;

        return CellTextureColors.getComponentWaveColor(componentType);
    }

    /**
     * Determine the ComponentType for a given item.
     *
     * @param item The component item
     * @return The CellType, or null if not a recognized component
     */
    private CellType getComponentType(Item item) {
        if (item instanceof ItemCompactingComponent) return CellType.COMPACTING;
        if (item instanceof ItemHyperDensityComponent) return CellType.HYPER_DENSITY_ITEM;
        if (item instanceof ItemFluidHyperDensityComponent) return CellType.HYPER_DENSITY_FLUID;
        if (item instanceof ItemHyperDensityCompactingComponent) return CellType.HYPER_DENSITY_COMPACTING;

        return null;
    }
}
