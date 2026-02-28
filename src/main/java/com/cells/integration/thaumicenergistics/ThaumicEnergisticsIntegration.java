package com.cells.integration.thaumicenergistics;

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
 * Integration handler for Thaumic Energistics.
 * <p>
 * This class acts as a bridge to the actual essentia cell implementation,
 * checking if the mod is loaded before attempting to use its classes.
 */
public final class ThaumicEnergisticsIntegration {

    private static final String MOD_ID = "thaumicenergistics";
    private static Boolean modLoaded = null;

    private ThaumicEnergisticsIntegration() {}

    /**
     * Check if Thaumic Energistics is loaded.
     */
    public static boolean isModLoaded() {
        if (modLoaded == null) {
            modLoaded = Loader.isModLoaded(MOD_ID);

            if (modLoaded) Cells.LOGGER.info("Thaumic Energistics detected, enabling essentia cell support");
        }

        return modLoaded;
    }

    /**
     * Get the cell inventory handler for an essentia cell.
     * Returns null if Thaumic Energistics is not loaded.
     */
    @SuppressWarnings("unchecked")
    public static <T extends IAEStack<T>> ICellInventoryHandler<T> getCellInventory(
            ItemStack is, ISaveProvider container, ComponentInfo info, IStorageChannel<T> channel) {

        if (!isModLoaded()) {
            Cells.LOGGER.warn("Essentia channel component used but Thaumic Energistics is not loaded");
            return null;
        }

        return EssentiaIntegrationImpl.getCellInventory(is, container, info, channel);
    }

    /**
     * Inner class that contains the actual implementation.
     * This class is only loaded if Thaumic Energistics is present,
     * preventing ClassNotFoundException when the mod is absent.
     */
    static final class EssentiaIntegrationImpl {

        @SuppressWarnings("unchecked")
        static <T extends IAEStack<T>> ICellInventoryHandler<T> getCellInventory(
                ItemStack is, ISaveProvider container, ComponentInfo info, IStorageChannel<T> channel) {

            try {
                IStorageChannel<thaumicenergistics.api.storage.IAEEssentiaStack> essentiaChannel =
                    AEApi.instance().storage().getStorageChannel(
                        thaumicenergistics.api.storage.IEssentiaStorageChannel.class);

                if (channel != essentiaChannel) return null;

                ConfigurableCellEssentiaInventory inventory = new ConfigurableCellEssentiaInventory(is, container, info);
                return (ICellInventoryHandler<T>) new ConfigurableCellInventoryHandler<>(inventory, essentiaChannel);
            } catch (Exception e) {
                Cells.LOGGER.error("Failed to create essentia cell inventory", e);
                return null;
            }
        }

        static void addCellInformation(ItemStack cellStack, List<String> tooltip) {
            try {
                IStorageChannel<thaumicenergistics.api.storage.IAEEssentiaStack> essentiaChannel =
                    AEApi.instance().storage().getStorageChannel(
                        thaumicenergistics.api.storage.IEssentiaStorageChannel.class);

                ICellInventoryHandler<thaumicenergistics.api.storage.IAEEssentiaStack> handler =
                    AEApi.instance().registries().cell().getCellInventory(cellStack, null, essentiaChannel);

                AEApi.instance().client().addCellInformation(handler, tooltip);
            } catch (Exception e) {
                Cells.LOGGER.error("Failed to add essentia cell info to tooltip", e);
            }
        }
    }

    /**
     * Add AE2 cell information to a tooltip for an essentia cell.
     * Does nothing if Thaumic Energistics is not loaded.
     */
    public static void addCellInformation(ItemStack cellStack, List<String> tooltip) {
        if (!isModLoaded()) return;

        EssentiaIntegrationImpl.addCellInformation(cellStack, tooltip);
    }
}
