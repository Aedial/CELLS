package com.cells.cells.configurable;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import appeng.fluids.helper.FluidCellConfig;
import appeng.items.contents.CellConfig;

import com.cells.integration.mekanismenergistics.MekanismEnergisticsIntegration;
import com.cells.integration.thaumicenergistics.ThaumicEnergisticsIntegration;


/**
 * Dispatches the correct config inventory class based on channel type.
 * <p>
 * Each storage channel needs a specialized CellConfig that converts container items
 * (buckets, phials, gas tanks, etc.) into their respective dummy item representations.
 * Without this, the partition list builder (in AbstractCellInventoryHandler) cannot
 * convert raw ItemStacks to the channel's AE stack type, silently producing an empty
 * partition list and effectively disabling filtering.
 * <p>
 * Channel → Config class mapping:
 * <ul>
 *   <li>ITEM → {@link CellConfig} (items are already ItemStacks, no conversion needed)</li>
 *   <li>FLUID → {@link FluidCellConfig} (converts fluid containers to FluidDummyItem)</li>
 *   <li>GAS → {@code GasCellConfig} (converts gas containers to ItemDummyGas)</li>
 *   <li>ESSENTIA → {@code EssentiaCellConfig} (converts essentia containers to ItemDummyAspect)</li>
 * </ul>
 */
public final class ConfigInventoryHelper {

    private ConfigInventoryHelper() {}

    /**
     * Get the appropriate config inventory for the given channel type.
     * Falls back to plain CellConfig if the required mod is not loaded.
     *
     * @param cellStack The cell ItemStack
     * @param channelType The storage channel type
     * @return The channel-appropriate config inventory
     */
    public static IItemHandler getConfigInventory(ItemStack cellStack, ChannelType channelType) {
        switch (channelType) {
            case FLUID:
                return new FluidCellConfig(cellStack);

            case GAS: {
                IItemHandler gasConfig = MekanismEnergisticsIntegration.getConfigInventory(cellStack);
                // Fall back to plain CellConfig if MekEng is not loaded (shouldn't happen
                // in practice, gas components can't exist without MekEng, but defensive)
                return gasConfig != null ? gasConfig : new CellConfig(cellStack);
            }

            case ESSENTIA: {
                IItemHandler essentiaConfig = ThaumicEnergisticsIntegration.getConfigInventory(cellStack);
                // Fall back to plain CellConfig if ThE is not loaded
                return essentiaConfig != null ? essentiaConfig : new CellConfig(cellStack);
            }

            case ITEM:
            default:
                return new CellConfig(cellStack);
        }
    }
}
