package com.cells.recipes;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.registries.IForgeRegistryEntry;

import appeng.api.AEApi;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.ICellWorkbenchItem;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import net.minecraftforge.items.IItemHandler;

import com.cells.ItemRegistry;
import com.cells.cells.hyperdensity.compacting.ItemHyperDensityCompactingComponent;
import com.cells.cells.hyperdensity.fluid.ItemFluidHyperDensityComponent;
import com.cells.cells.hyperdensity.item.ItemHyperDensityComponent;
import com.cells.cells.normal.compacting.ItemCompactingComponent;
import com.cells.cells.configurable.ChannelType;
import com.cells.cells.configurable.ComponentHelper;
import com.cells.cells.configurable.ComponentInfo;
import com.cells.integration.mekanismenergistics.MekanismEnergisticsIntegration;
import com.cells.integration.thaumicenergistics.ThaumicEnergisticsIntegration;


/**
 * Dynamic crafting recipe that swaps the component inside one CELLS storage cell.
 * <p>
 * Input: one supported CELLS cell + one supported replacement component.
 * Output: the converted cell, while the replaced component is returned as a crafting remainder.
 * <p>
 * Safety rules:
 * <ul>
 *   <li>Only CELLS cells can be upgraded or downgraded.</li>
 *   <li>Configurable cell casings cannot swap into non-configurable casings, or vice-versa.</li>
 *   <li>If the cell contains data, the target family must be content-compatible.</li>
 *   <li>Installed upgrades must remain valid for the target cell.</li>
 *   <li>The target cell must have enough capacity for the existing contents.</li>
 * </ul>
 */
public class CellComponentSwapRecipe extends IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {

    @Override
    public boolean matches(@Nonnull InventoryCrafting inv, @Nonnull World world) {
        return findMatch(inv) != null;
    }

    @Override
    @Nonnull
    public ItemStack getCraftingResult(@Nonnull InventoryCrafting inv) {
        SwapMatch match = findMatch(inv);
        return match == null ? ItemStack.EMPTY : match.result.copy();
    }

    @Override
    public boolean canFit(int width, int height) {
        return width * height >= 2;
    }

    @Override
    @Nonnull
    public ItemStack getRecipeOutput() {
        return ItemStack.EMPTY;
    }

    @Override
    @Nonnull
    public NonNullList<ItemStack> getRemainingItems(@Nonnull InventoryCrafting inv) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(inv.getSizeInventory(), ItemStack.EMPTY);
        SwapMatch match = findMatch(inv);
        if (match == null) return remaining;

        remaining.set(match.cellSlot, match.oldComponent.copy());
        return remaining;
    }

    @Override
    public boolean isDynamic() {
        return true;
    }

    @Nullable
    private SwapMatch findMatch(InventoryCrafting inv) {
        ItemStack sourceCell = ItemStack.EMPTY;
        ItemStack newComponent = ItemStack.EMPTY;
        int cellSlot = -1;

        for (int slot = 0; slot < inv.getSizeInventory(); slot++) {
            ItemStack stack = inv.getStackInSlot(slot);
            if (stack.isEmpty()) continue;

            CellDescriptor sourceDescriptor = describeSourceCell(stack);
            if (sourceDescriptor != null) {
                if (!sourceCell.isEmpty()) return null;

                sourceCell = stack;
                cellSlot = slot;
                continue;
            }

            CellDescriptor targetDescriptor = describeTargetFromComponent(stack);
            if (targetDescriptor != null) {
                if (!newComponent.isEmpty()) return null;

                newComponent = stack;
                continue;
            }

            return null;
        }

        if (sourceCell.isEmpty() || newComponent.isEmpty()) return null;

        CellDescriptor sourceDescriptor = describeSourceCell(sourceCell);
        CellDescriptor targetDescriptor = describeTargetFromComponent(newComponent);
        if (sourceDescriptor == null || targetDescriptor == null) return null;

        if (sameComponent(sourceDescriptor.component, newComponent)) return null;

        if (!hasCompatibleCasing(sourceDescriptor, targetDescriptor)) return null;

        boolean sourceEmpty = isEmpty(sourceCell, sourceDescriptor);
        if (!sourceEmpty && !canPreserveContents(sourceDescriptor, targetDescriptor)) return null;

        ItemStack result = createResultStack(sourceCell, targetDescriptor, newComponent);
        if (!supportsInstalledUpgrades(sourceCell, result)) return null;

        if (!sourceEmpty && !fitsExistingContents(result, targetDescriptor)) return null;

        return new SwapMatch(result, singleCopy(sourceDescriptor.component), cellSlot);
    }

    @Nullable
    private CellDescriptor describeSourceCell(ItemStack stack) {
        Item item = stack.getItem();

        if (ItemRegistry.CONFIGURABLE_CELL != null && item == ItemRegistry.CONFIGURABLE_CELL) {
            ItemStack installed = ComponentHelper.getInstalledComponent(stack);
            ComponentInfo info = ComponentHelper.getComponentInfo(installed);
            if (installed.isEmpty() || info == null) return null;

            return new CellDescriptor(CellFamily.CONFIGURABLE, ChannelGroup.from(info.getChannelType()),
                info.getChannelType(), ItemRegistry.CONFIGURABLE_CELL, 0, installed, info);
        }

        if (ItemRegistry.COMPACTING_CELL != null && item == ItemRegistry.COMPACTING_CELL) {
            return new CellDescriptor(CellFamily.COMPACTING, ChannelGroup.COMPACTING, ChannelType.ITEM,
                ItemRegistry.COMPACTING_CELL, stack.getMetadata(),
                ItemCompactingComponent.create(stack.getMetadata()), null);
        }

        if (ItemRegistry.HYPER_DENSITY_CELL != null && item == ItemRegistry.HYPER_DENSITY_CELL) {
            return new CellDescriptor(CellFamily.HYPER_DENSITY_ITEM, ChannelGroup.ITEM, ChannelType.ITEM,
                ItemRegistry.HYPER_DENSITY_CELL, stack.getMetadata(),
                ItemHyperDensityComponent.create(stack.getMetadata()), null);
        }

        if (ItemRegistry.FLUID_HYPER_DENSITY_CELL != null && item == ItemRegistry.FLUID_HYPER_DENSITY_CELL) {
            return new CellDescriptor(CellFamily.HYPER_DENSITY_FLUID, ChannelGroup.FLUID, ChannelType.FLUID,
                ItemRegistry.FLUID_HYPER_DENSITY_CELL, stack.getMetadata(),
                ItemFluidHyperDensityComponent.create(stack.getMetadata()), null);
        }

        if (ItemRegistry.HYPER_DENSITY_COMPACTING_CELL != null && item == ItemRegistry.HYPER_DENSITY_COMPACTING_CELL) {
            return new CellDescriptor(CellFamily.HYPER_DENSITY_COMPACTING, ChannelGroup.COMPACTING, ChannelType.ITEM,
                ItemRegistry.HYPER_DENSITY_COMPACTING_CELL, stack.getMetadata(),
                ItemHyperDensityCompactingComponent.create(stack.getMetadata()), null);
        }

        return null;
    }

    @Nullable
    private CellDescriptor describeTargetFromComponent(ItemStack stack) {
        Item item = stack.getItem();

        if (ItemRegistry.COMPACTING_COMPONENT != null && item == ItemRegistry.COMPACTING_COMPONENT && ItemRegistry.COMPACTING_CELL != null) {
            return new CellDescriptor(CellFamily.COMPACTING, ChannelGroup.COMPACTING, ChannelType.ITEM,
                ItemRegistry.COMPACTING_CELL, stack.getMetadata(), singleCopy(stack), null);
        }

        if (ItemRegistry.HYPER_DENSITY_COMPONENT != null && item == ItemRegistry.HYPER_DENSITY_COMPONENT && ItemRegistry.HYPER_DENSITY_CELL != null) {
            return new CellDescriptor(CellFamily.HYPER_DENSITY_ITEM, ChannelGroup.ITEM, ChannelType.ITEM,
                ItemRegistry.HYPER_DENSITY_CELL, stack.getMetadata(), singleCopy(stack), null);
        }

        if (ItemRegistry.FLUID_HYPER_DENSITY_COMPONENT != null && item == ItemRegistry.FLUID_HYPER_DENSITY_COMPONENT
                && ItemRegistry.FLUID_HYPER_DENSITY_CELL != null) {
            return new CellDescriptor(CellFamily.HYPER_DENSITY_FLUID, ChannelGroup.FLUID, ChannelType.FLUID,
                ItemRegistry.FLUID_HYPER_DENSITY_CELL, stack.getMetadata(), singleCopy(stack), null);
        }

        if (ItemRegistry.HYPER_DENSITY_COMPACTING_COMPONENT != null && item == ItemRegistry.HYPER_DENSITY_COMPACTING_COMPONENT
                && ItemRegistry.HYPER_DENSITY_COMPACTING_CELL != null) {
            return new CellDescriptor(CellFamily.HYPER_DENSITY_COMPACTING, ChannelGroup.COMPACTING, ChannelType.ITEM,
                ItemRegistry.HYPER_DENSITY_COMPACTING_CELL, stack.getMetadata(), singleCopy(stack), null);
        }

        if (ItemRegistry.CONFIGURABLE_CELL != null) {
            ComponentInfo info = ComponentHelper.getComponentInfo(stack);
            if (info != null) {
                return new CellDescriptor(CellFamily.CONFIGURABLE, ChannelGroup.from(info.getChannelType()),
                    info.getChannelType(), ItemRegistry.CONFIGURABLE_CELL, 0, singleCopy(stack), info);
            }
        }

        return null;
    }

    private boolean isEmpty(ItemStack cellStack, CellDescriptor descriptor) {
        ICellInventory<?> inventory = getCellInventory(cellStack, descriptor);
        if (inventory != null) return inventory.getUsedBytes() == 0;

        if (descriptor.family == CellFamily.CONFIGURABLE) return !ComponentHelper.hasContent(cellStack);

        return false;
    }

    private boolean hasCompatibleCasing(CellDescriptor source, CellDescriptor target) {
        boolean sourceConfigurable = source.family == CellFamily.CONFIGURABLE;
        boolean targetConfigurable = target.family == CellFamily.CONFIGURABLE;
        return sourceConfigurable == targetConfigurable;
    }

    private boolean canPreserveContents(CellDescriptor source, CellDescriptor target) {
        if (source.group == ChannelGroup.COMPACTING || target.group == ChannelGroup.COMPACTING) {
            return source.group == ChannelGroup.COMPACTING && target.group == ChannelGroup.COMPACTING;
        }

        if (source.family == CellFamily.CONFIGURABLE || target.family == CellFamily.CONFIGURABLE) {
            return source.channelType == target.channelType;
        }

        return source.family == target.family;
    }

    private boolean supportsInstalledUpgrades(ItemStack sourceCell, ItemStack targetCell) {
        if (!(sourceCell.getItem() instanceof ICellWorkbenchItem)) return false;
        if (!(targetCell.getItem() instanceof ICellWorkbenchItem)) return false;

        ICellWorkbenchItem sourceWorkbench = (ICellWorkbenchItem) sourceCell.getItem();
        ICellWorkbenchItem targetWorkbench = (ICellWorkbenchItem) targetCell.getItem();

        IItemHandler sourceUpgrades = sourceWorkbench.getUpgradesInventory(sourceCell);
        IItemHandler targetUpgrades = targetWorkbench.getUpgradesInventory(
            new ItemStack(targetCell.getItem(), 1, targetCell.getMetadata()));

        for (int slot = 0; slot < sourceUpgrades.getSlots(); slot++) {
            ItemStack upgrade = sourceUpgrades.getStackInSlot(slot);
            if (upgrade.isEmpty()) continue;

            ItemStack remainder = singleCopy(upgrade);
            for (int targetSlot = 0; targetSlot < targetUpgrades.getSlots() && !remainder.isEmpty(); targetSlot++) {
                remainder = targetUpgrades.insertItem(targetSlot, remainder, false);
            }

            if (!remainder.isEmpty()) return false;
        }

        return true;
    }

    private boolean fitsExistingContents(ItemStack result, CellDescriptor descriptor) {
        ICellInventory<?> inventory = getCellInventory(result, descriptor);
        if (inventory == null) return false;

        if (inventory.getStoredItemTypes() > inventory.getTotalItemTypes()) return false;

        return inventory.getUsedBytes() <= inventory.getTotalBytes();
    }

    private ItemStack createResultStack(ItemStack sourceCell, CellDescriptor target, ItemStack newComponent) {
        ItemStack result = new ItemStack(target.item, 1, target.meta);
        NBTTagCompound sourceTag = sourceCell.getTagCompound();
        if (sourceTag != null) result.setTagCompound(sourceTag.copy());

        if (target.family == CellFamily.CONFIGURABLE) {
            ComponentHelper.setInstalledComponent(result, singleCopy(newComponent));
        } else {
            NBTTagCompound resultTag = result.getTagCompound();
            if (resultTag != null) resultTag.removeTag("component");
        }

        return result;
    }

    @Nullable
    private ICellInventory<?> getCellInventory(ItemStack stack, CellDescriptor descriptor) {
        switch (descriptor.channelType) {
            case ITEM: {
                IItemStorageChannel channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
                ICellInventoryHandler<IAEItemStack> handler = AEApi.instance().registries().cell().getCellInventory(stack, null, channel);
                return handler == null ? null : handler.getCellInv();
            }

            case FLUID: {
                IFluidStorageChannel channel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
                ICellInventoryHandler<IAEFluidStack> handler = AEApi.instance().registries().cell().getCellInventory(stack, null, channel);
                return handler == null ? null : handler.getCellInv();
            }

            case GAS: {
                if (descriptor.componentInfo == null) return null;

                @SuppressWarnings("rawtypes")
                IStorageChannel rawChannel = MekanismEnergisticsIntegration.getStorageChannel();
                if (rawChannel == null) return null;

                @SuppressWarnings("unchecked")
                ICellInventoryHandler<?> handler = MekanismEnergisticsIntegration.getCellInventory(
                    stack, null, descriptor.componentInfo, rawChannel);
                return handler == null ? null : handler.getCellInv();
            }

            case ESSENTIA: {
                if (descriptor.componentInfo == null) return null;

                @SuppressWarnings("rawtypes")
                IStorageChannel rawChannel = ThaumicEnergisticsIntegration.getStorageChannel();
                if (rawChannel == null) return null;

                @SuppressWarnings("unchecked")
                ICellInventoryHandler<?> handler = ThaumicEnergisticsIntegration.getCellInventory(
                    stack, null, descriptor.componentInfo, rawChannel);
                return handler == null ? null : handler.getCellInv();
            }

            default:
                return null;
        }
    }

    private boolean sameComponent(ItemStack a, ItemStack b) {
        return a.getItem() == b.getItem() && a.getMetadata() == b.getMetadata()
            && ItemStack.areItemStackTagsEqual(a, b);
    }

    private ItemStack singleCopy(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    private enum CellFamily {
        COMPACTING,
        HYPER_DENSITY_ITEM,
        HYPER_DENSITY_FLUID,
        HYPER_DENSITY_COMPACTING,
        CONFIGURABLE
    }

    private enum ChannelGroup {
        ITEM,
        FLUID,
        COMPACTING,
        ESSENTIA,
        GAS;

        private static ChannelGroup from(ChannelType channelType) {
            switch (channelType) {
                case FLUID:
                    return FLUID;

                case ESSENTIA:
                    return ESSENTIA;

                case GAS:
                    return GAS;

                case ITEM:
                default:
                    return ITEM;
            }
        }
    }

    private static final class CellDescriptor {

        private final CellFamily family;
        private final ChannelGroup group;
        private final ChannelType channelType;
        private final Item item;
        private final int meta;
        private final ItemStack component;
        private final ComponentInfo componentInfo;

        private CellDescriptor(CellFamily family, ChannelGroup group, ChannelType channelType,
                               Item item, int meta, ItemStack component, @Nullable ComponentInfo componentInfo) {
            this.family = family;
            this.group = group;
            this.channelType = channelType;
            this.item = item;
            this.meta = meta;
            this.component = component;
            this.componentInfo = componentInfo;
        }
    }

    private static final class SwapMatch {

        private final ItemStack result;
        private final ItemStack oldComponent;
        private final int cellSlot;

        private SwapMatch(ItemStack result, ItemStack oldComponent, int cellSlot) {
            this.result = result;
            this.oldComponent = oldComponent;
            this.cellSlot = cellSlot;
        }
    }
}