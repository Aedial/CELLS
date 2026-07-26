package com.cells.integration.jei;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;

import com.cells.blocks.combinedinterface.ContainerCombinedInterface;
import com.cells.blocks.interfacebase.AbstractContainerInterface;
import com.cells.blocks.iointerface.ContainerIOInterface;
import com.cells.blocks.iointerface.IIOInterfaceHost;
import com.cells.config.CellsConfig;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.packets.PacketJEIInterfaceRecipeTransfer;
import com.cells.network.sync.IQuickAddFilterContainer;
import com.cells.network.sync.ResourceType;


/**
 * JEI recipe transfer handler for CELLS filter containers.
 * <p>
 * Interface containers resolve recipe inputs/outputs from the shared
 * import/export routing preference. Creative cells use the same preference as a
 * direct inputs-vs-outputs selection for their single filter grid.
 */
public class InterfaceRecipeTransferHandler<C extends Container> implements IRecipeTransferHandler<C> {

    public enum TransferMode {
        SINGLE_DIRECTION,
        COMBINED_ALL_TYPES,
        IO_SPLIT_DIRECTIONS,
        FILTER_SELECTION_ONLY
    }

    private final Class<C> containerClass;
    private final TransferMode transferMode;

    public InterfaceRecipeTransferHandler(Class<C> containerClass, TransferMode transferMode) {
        this.containerClass = containerClass;
        this.transferMode = transferMode;
    }

    @Override
    public Class<C> getContainerClass() {
        return this.containerClass;
    }

    @Nullable
    @Override
    public IRecipeTransferError transferRecipe(
            C container,
            IRecipeLayout recipeLayout,
            EntityPlayer player,
            boolean maxTransfer,
            boolean doTransfer) {
        String recipeType = recipeLayout.getRecipeCategory().getUid();

        if (recipeType.equals(VanillaRecipeCategoryUid.INFORMATION)
            || recipeType.equals(VanillaRecipeCategoryUid.FUEL)) {
            return null;
        }

        if (!doTransfer) return null;

        List<PacketJEIInterfaceRecipeTransfer.TransferEntry> entries = buildEntries(container, recipeLayout);
        if (entries.isEmpty()) return null;

        CellsNetworkHandler.INSTANCE.sendToServer(new PacketJEIInterfaceRecipeTransfer(entries));
        return null;
    }

    private List<PacketJEIInterfaceRecipeTransfer.TransferEntry> buildEntries(C container, IRecipeLayout recipeLayout) {
        switch (this.transferMode) {
            case SINGLE_DIRECTION:
                return buildSingleDirectionEntries(container, recipeLayout);
            case COMBINED_ALL_TYPES:
                return buildCombinedEntries(container, recipeLayout);
            case IO_SPLIT_DIRECTIONS:
                return buildIOEntries(container, recipeLayout);
            case FILTER_SELECTION_ONLY:
                return buildSelectionEntries(container, recipeLayout);
            default:
                return Collections.emptyList();
        }
    }

    @SuppressWarnings("rawtypes")
    private List<PacketJEIInterfaceRecipeTransfer.TransferEntry> buildSingleDirectionEntries(
            C container,
            IRecipeLayout recipeLayout) {
        if (!(container instanceof AbstractContainerInterface)) return Collections.emptyList();

        AbstractContainerInterface interfaceContainer = (AbstractContainerInterface) container;
        ResourceType type = interfaceContainer.getQuickAddResourceType();
        RecipeTransferIngredientCollector.RecipeComponentSelection selection =
            getSelectionForInterface(interfaceContainer.getHost().isExport());

        List<Object> resources = RecipeTransferIngredientCollector.collect(recipeLayout, type, selection);
        return createEntries(type, PacketJEIInterfaceRecipeTransfer.DIRECTION_UNSPECIFIED, resources);
    }

    private List<PacketJEIInterfaceRecipeTransfer.TransferEntry> buildCombinedEntries(
            C container,
            IRecipeLayout recipeLayout) {
        if (!(container instanceof ContainerCombinedInterface)) return Collections.emptyList();

        ContainerCombinedInterface combinedContainer = (ContainerCombinedInterface) container;
        RecipeTransferIngredientCollector.RecipeComponentSelection selection =
            getSelectionForInterface(combinedContainer.getHost().isExport());

        List<PacketJEIInterfaceRecipeTransfer.TransferEntry> entries = new ArrayList<>();

        for (ResourceType type : combinedContainer.getHost().getAvailableTabs()) {
            List<Object> resources = RecipeTransferIngredientCollector.collect(recipeLayout, type, selection);
            entries.addAll(createEntries(type, PacketJEIInterfaceRecipeTransfer.DIRECTION_UNSPECIFIED, resources));
        }

        return entries;
    }

    private List<PacketJEIInterfaceRecipeTransfer.TransferEntry> buildIOEntries(
            C container,
            IRecipeLayout recipeLayout) {
        if (!(container instanceof ContainerIOInterface)) return Collections.emptyList();

        ContainerIOInterface ioContainer = (ContainerIOInterface) container;
        ResourceType type = ioContainer.getHost().getResourceType();

        int inputTarget = CellsConfig.hidden.jeiTransferInputsToExport
            ? IIOInterfaceHost.TAB_EXPORT
            : IIOInterfaceHost.TAB_IMPORT;
        int outputTarget = inputTarget == IIOInterfaceHost.TAB_EXPORT
            ? IIOInterfaceHost.TAB_IMPORT
            : IIOInterfaceHost.TAB_EXPORT;

        List<PacketJEIInterfaceRecipeTransfer.TransferEntry> entries = new ArrayList<>();
        entries.addAll(createEntries(
            type,
            inputTarget,
            RecipeTransferIngredientCollector.collect(
                recipeLayout,
                type,
                RecipeTransferIngredientCollector.RecipeComponentSelection.INPUTS)));
        entries.addAll(createEntries(
            type,
            outputTarget,
            RecipeTransferIngredientCollector.collect(
                recipeLayout,
                type,
                RecipeTransferIngredientCollector.RecipeComponentSelection.OUTPUTS)));
        return entries;
    }

    private List<PacketJEIInterfaceRecipeTransfer.TransferEntry> buildSelectionEntries(
            C container,
            IRecipeLayout recipeLayout) {
        if (!(container instanceof IQuickAddFilterContainer)) return Collections.emptyList();

        IQuickAddFilterContainer quickAddContainer = (IQuickAddFilterContainer) container;
        ResourceType type = quickAddContainer.getQuickAddResourceType();
        RecipeTransferIngredientCollector.RecipeComponentSelection selection = getSelectionForCreativeCell();

        List<Object> resources = RecipeTransferIngredientCollector.collect(recipeLayout, type, selection);
        return createEntries(type, PacketJEIInterfaceRecipeTransfer.DIRECTION_UNSPECIFIED, resources);
    }

    private static RecipeTransferIngredientCollector.RecipeComponentSelection getSelectionForInterface(
            boolean isExportInterface) {
        return CellsConfig.interfaceReceivesJeiInputs(isExportInterface)
            ? RecipeTransferIngredientCollector.RecipeComponentSelection.INPUTS
            : RecipeTransferIngredientCollector.RecipeComponentSelection.OUTPUTS;
    }

    private static RecipeTransferIngredientCollector.RecipeComponentSelection getSelectionForCreativeCell() {
        return CellsConfig.creativeCellReceivesJeiOutputs()
            ? RecipeTransferIngredientCollector.RecipeComponentSelection.OUTPUTS
            : RecipeTransferIngredientCollector.RecipeComponentSelection.INPUTS;
    }

    private static List<PacketJEIInterfaceRecipeTransfer.TransferEntry> createEntries(
            ResourceType type,
            int directionTab,
            List<Object> resources) {
        List<PacketJEIInterfaceRecipeTransfer.TransferEntry> entries = new ArrayList<>(resources.size());

        for (Object resource : resources) {
            if (resource == null) continue;
            entries.add(new PacketJEIInterfaceRecipeTransfer.TransferEntry(type, directionTab, resource));
        }

        return entries;
    }
}