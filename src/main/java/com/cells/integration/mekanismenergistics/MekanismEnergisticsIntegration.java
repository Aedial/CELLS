package com.cells.integration.mekanismenergistics;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;

import appeng.api.AEApi;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;

import com.cells.Cells;
import com.cells.cells.configurable.ComponentInfo;
import com.cells.cells.configurable.ConfigurableCellInventoryHandler;


/**
 * Integration handler for Mekanism Energistics.
 * <p>
 * This class acts as a bridge to the actual gas cell implementation,
 * checking if the mod is loaded before attempting to use its classes.
 */
public final class MekanismEnergisticsIntegration {

    private static final String MOD_ID = "mekeng";
    private static Boolean modLoaded = null;

    private MekanismEnergisticsIntegration() {}

    /**
     * Check if Mekanism Energistics is loaded.
     */
    public static boolean isModLoaded() {
        if (modLoaded == null) {
            modLoaded = Loader.isModLoaded(MOD_ID);

            if (modLoaded) Cells.LOGGER.info("Mekanism Energistics detected, enabling gas cell support");
        }

        return modLoaded;
    }

    /**
     * Get the cell inventory handler for a gas cell.
     * Returns null if Mekanism Energistics is not loaded.
     */
    @SuppressWarnings("unchecked")
    public static <T extends IAEStack<T>> ICellInventoryHandler<T> getCellInventory(
            ItemStack is, ISaveProvider container, ComponentInfo info, IStorageChannel<T> channel) {

        if (!isModLoaded()) {
            Cells.LOGGER.warn("Gas channel component used but Mekanism Energistics is not loaded");
            return null;
        }

        return GasIntegrationImpl.getCellInventory(is, container, info, channel);
    }

    /**
     * Add AE2 cell information to a tooltip for a gas cell.
     * Does nothing if Mekanism Energistics is not loaded.
     */
    public static void addCellInformation(ItemStack cellStack, List<String> tooltip) {
        if (!isModLoaded()) return;

        GasIntegrationImpl.addCellInformation(cellStack, tooltip);
    }

    /**
     * Inner class that contains the actual implementation.
     * This class is only loaded if Mekanism Energistics is present,
     * preventing ClassNotFoundException when the mod is absent.
     */
    static final class GasIntegrationImpl {

        @SuppressWarnings("unchecked")
        static <T extends IAEStack<T>> ICellInventoryHandler<T> getCellInventory(
                ItemStack is, ISaveProvider container, ComponentInfo info, IStorageChannel<T> channel) {

            try {
                IStorageChannel<com.mekeng.github.common.me.data.IAEGasStack> gasChannel =
                    AEApi.instance().storage().getStorageChannel(
                        com.mekeng.github.common.me.storage.IGasStorageChannel.class);

                if (channel != gasChannel) return null;

                ConfigurableCellGasInventory inventory = new ConfigurableCellGasInventory(is, container, info);
                return (ICellInventoryHandler<T>) new ConfigurableCellInventoryHandler<>(inventory, gasChannel);
            } catch (Exception e) {
                Cells.LOGGER.error("Failed to create gas cell inventory", e);
                return null;
            }
        }

        static void addCellInformation(ItemStack cellStack, List<String> tooltip) {
            try {
                IStorageChannel<com.mekeng.github.common.me.data.IAEGasStack> gasChannel =
                    AEApi.instance().storage().getStorageChannel(
                        com.mekeng.github.common.me.storage.IGasStorageChannel.class);

                ICellInventoryHandler<com.mekeng.github.common.me.data.IAEGasStack> handler =
                    AEApi.instance().registries().cell().getCellInventory(cellStack, null, gasChannel);

                AEApi.instance().client().addCellInformation(handler, tooltip);
            } catch (Exception e) {
                Cells.LOGGER.error("Failed to add gas cell info to tooltip", e);
            }
        }
    }
}
