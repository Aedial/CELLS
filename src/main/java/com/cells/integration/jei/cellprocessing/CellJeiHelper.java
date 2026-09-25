package com.cells.integration.jei.cellprocessing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.IItemHandler;

import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.ICellWorkbenchItem;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IItemList;

import com.cells.ItemRegistry;
import com.cells.cells.configurable.ComponentHelper;
import com.cells.cells.configurable.ComponentInfo;
import com.cells.cells.hyperdensity.compacting.ItemHyperDensityCompactingCell;
import com.cells.cells.hyperdensity.compacting.ItemHyperDensityCompactingComponent;
import com.cells.cells.hyperdensity.fluid.ItemFluidHyperDensityCell;
import com.cells.cells.hyperdensity.fluid.ItemFluidHyperDensityComponent;
import com.cells.cells.hyperdensity.item.ItemHyperDensityCell;
import com.cells.cells.hyperdensity.item.ItemHyperDensityComponent;
import com.cells.cells.normal.compacting.ItemCompactingCell;
import com.cells.cells.normal.compacting.ItemCompactingComponent;
import com.cells.integration.jei.cellview.CellViewHelper;
import com.cells.items.upgrades.AbstractCustomUpgrade;
import com.cells.util.DisassemblyConfig;


/**
 * Collects CELLS cell stacks and the items returned by their JEI actions.
 */
final class CellJeiHelper {

    private CellJeiHelper() {}

    static List<ItemStack> getAllDisassemblyCells() {
        List<ItemStack> cells = new ArrayList<>();

        addTieredItems(cells, ItemRegistry.COMPACTING_CELL,
            ItemCompactingCell.getTierNames().length);
        addTieredItems(cells, ItemRegistry.HYPER_DENSITY_CELL,
            ItemHyperDensityCell.getTierNames().length);
        addTieredItems(cells, ItemRegistry.FLUID_HYPER_DENSITY_CELL,
            ItemFluidHyperDensityCell.getTierNames().length);
        addTieredItems(cells, ItemRegistry.HYPER_DENSITY_COMPACTING_CELL,
            ItemHyperDensityCompactingCell.getTierNames().length);

        if (ItemRegistry.CONFIGURABLE_CELL != null) {
            for (ItemStack component : ComponentHelper.getValidComponents()) {
                ItemStack cell = new ItemStack(ItemRegistry.CONFIGURABLE_CELL);
                ComponentHelper.setInstalledComponent(cell, component);
                cells.add(cell);
            }
        }

        return cells;
    }

    static List<ItemStack> getAllDisassemblyItems() {
        List<ItemStack> items = new ArrayList<>();

        // Cells
        items.addAll(getAllDisassemblyCells());

        // Upgrades
        addTieredItems(items, ItemRegistry.OVERFLOW_CARD, 1);
        addTieredItems(items, ItemRegistry.OREDICT_CARD, 1);
        addTieredItems(items, ItemRegistry.TRASH_UNSELECTED_CARD, 1);
        addTieredItems(items, ItemRegistry.INSERTION_CARD, 1);
        addTieredItems(items, ItemRegistry.PULL_CARD, 1);
        addTieredItems(items, ItemRegistry.PUSH_CARD, 1);
        addTieredItems(items, ItemRegistry.EQUAL_DISTRIBUTION_CARD,
            ItemRegistry.EQUAL_DISTRIBUTION_CARD.getTierNames().length);
        addTieredItems(items, ItemRegistry.COMPRESSION_TIER_CARD,
            ItemRegistry.COMPRESSION_TIER_CARD.getTierNames().length);
        addTieredItems(items, ItemRegistry.DECOMPRESSION_TIER_CARD,
            ItemRegistry.DECOMPRESSION_TIER_CARD.getTierNames().length);

        if (ItemRegistry.EMC_CAPACITY_CARD != null) {
            addTieredItems(items, ItemRegistry.EMC_CAPACITY_CARD,
                ItemRegistry.EMC_CAPACITY_CARD.getTierNames().length);
        }

        return items;
    }

    static boolean canDisassemble(ItemStack stack) {
        if (!isDisassemblableCell(stack) && !isDisassemblableUpgrade(stack)) return false;
        if (getConfiguredOutputs(stack).isEmpty()) return false;

        if (isDisassemblableUpgrade(stack)) return true;

        if (stack.getItem() == ItemRegistry.CONFIGURABLE_CELL) {
            if (ComponentHelper.hasContent(stack)) return false;
            if (CellViewHelper.getCellInfo(stack) != null && !hasNoStoredContent(stack)) return false;

            return !ComponentHelper.getInstalledComponent(stack).isEmpty() || hasUpgrades(stack);
        }

        return hasNoStoredContent(stack);
    }

    static boolean canShowDisassembly(ItemStack stack) {
        if (!isDisassemblableCell(stack) && !isDisassemblableUpgrade(stack)) return false;
        if (getConfiguredOutputs(stack).isEmpty()) return false;

        if (isDisassemblableUpgrade(stack)) return true;

        if (stack.getItem() != ItemRegistry.CONFIGURABLE_CELL) {
            return CellViewHelper.getCellInfo(stack) != null;
        }

        return !ComponentHelper.getInstalledComponent(stack).isEmpty() || hasUpgrades(stack);
    }

    static List<ItemStack> getDisassemblyOutputs(ItemStack stack) {
        List<ItemStack> outputs = new ArrayList<>();
        if (isDisassemblableCell(stack)) outputs.addAll(getUpgrades(stack));
        outputs.addAll(getConfiguredOutputs(stack));
        return outputs;
    }

    private static List<ItemStack> getConfiguredOutputs(ItemStack stack) {
        @Nullable ItemStack housing = null;
        ItemStack component;
        if (stack.getItem() == ItemRegistry.CONFIGURABLE_CELL) {
            housing = createConfigurableHousing(stack);
            component = ComponentHelper.getInstalledComponent(stack);
        } else {
            component = getCellComponent(stack);
        }

        return DisassemblyConfig.getOutputs(stack, housing, component);
    }

    static List<ItemStack> getSwapComponents(ItemStack stack) {
        if (!isUpgradeableCell(stack)) return Collections.emptyList();

        List<ItemStack> components = new ArrayList<>();
        if (stack.getItem() == ItemRegistry.CONFIGURABLE_CELL) {
            for (ItemStack component : ComponentHelper.getValidComponents()) addIfPresent(components, component);
            components.sort(CellJeiHelper::compareConfigurableComponents);
            return components;
        }

        addTieredItems(components, ItemRegistry.COMPACTING_COMPONENT,
            ItemCompactingComponent.getTierNames().length);
        addTieredItems(components, ItemRegistry.HYPER_DENSITY_COMPONENT,
            ItemHyperDensityComponent.getTierNames().length);
        addTieredItems(components, ItemRegistry.FLUID_HYPER_DENSITY_COMPONENT,
            ItemFluidHyperDensityComponent.getTierNames().length);
        addTieredItems(components, ItemRegistry.HYPER_DENSITY_COMPACTING_COMPONENT,
            ItemHyperDensityCompactingComponent.getTierNames().length);
        return components;
    }

    private static void addTieredItems(List<ItemStack> cells, @Nullable Item item, int tiers) {
        if (item == null) return;

        for (int tier = 0; tier < tiers; tier++) cells.add(new ItemStack(item, 1, tier));
    }

    private static boolean isDisassemblableCell(ItemStack stack) {
        if (stack.isEmpty()) return false;

        return stack.getItem() == ItemRegistry.COMPACTING_CELL
            || stack.getItem() == ItemRegistry.HYPER_DENSITY_CELL
            || stack.getItem() == ItemRegistry.FLUID_HYPER_DENSITY_CELL
            || stack.getItem() == ItemRegistry.HYPER_DENSITY_COMPACTING_CELL
            || stack.getItem() == ItemRegistry.CONFIGURABLE_CELL;
        // TODO: add support for EMC Cell?
    }

    private static boolean isDisassemblableUpgrade(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof AbstractCustomUpgrade;
    }

    private static boolean isUpgradeableCell(ItemStack stack) {
        if (!isDisassemblableCell(stack)) return false;

        if (stack.getItem() != ItemRegistry.CONFIGURABLE_CELL) return true;

        return ComponentHelper.getComponentInfo(ComponentHelper.getInstalledComponent(stack)) != null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean hasNoStoredContent(ItemStack stack) {
        CellViewHelper.CellInfo cellInfo = CellViewHelper.getCellInfo(stack);
        if (cellInfo == null) return false;

        ICellInventoryHandler handler = cellInfo.getHandler();
        IStorageChannel channel = cellInfo.getChannel();
        IItemList contents = handler.getAvailableItems(channel.createList());
        return contents.isEmpty();
    }

    private static boolean hasUpgrades(ItemStack stack) {
        if (!(stack.getItem() instanceof ICellWorkbenchItem)) return false;

        ICellWorkbenchItem cell = (ICellWorkbenchItem) stack.getItem();
        IItemHandler upgrades = cell.getUpgradesInventory(stack);
        for (int slot = 0; slot < upgrades.getSlots(); slot++) {
            if (!upgrades.getStackInSlot(slot).isEmpty()) return true;
        }

        return false;
    }

    private static List<ItemStack> getUpgrades(ItemStack stack) {
        if (!(stack.getItem() instanceof ICellWorkbenchItem)) return Collections.emptyList();

        List<ItemStack> upgrades = new ArrayList<>();
        ICellWorkbenchItem cell = (ICellWorkbenchItem) stack.getItem();
        IItemHandler inventory = cell.getUpgradesInventory(stack);
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            addIfPresent(upgrades, inventory.getStackInSlot(slot));
        }

        return upgrades;
    }

    private static ItemStack createConfigurableHousing(ItemStack stack) {
        ItemStack housing = stack.copy();
        housing.setCount(1);
        ComponentHelper.setInstalledComponent(housing, ItemStack.EMPTY);

        ICellWorkbenchItem cell = (ICellWorkbenchItem) housing.getItem();
        IItemHandler upgrades = cell.getUpgradesInventory(housing);
        for (int slot = 0; slot < upgrades.getSlots(); slot++) {
            upgrades.extractItem(slot, Integer.MAX_VALUE, false);
        }

        NBTTagCompound data = housing.getTagCompound();
        if (data != null) {
            data.removeTag("itemType");
            data.removeTag("fluidType");
            data.removeTag("gasType");
            data.removeTag("essentiaType");
        }

        return housing;
    }

    private static ItemStack getCellComponent(ItemStack stack) {
        if (stack.getItem() == ItemRegistry.COMPACTING_CELL) {
            return ItemCompactingComponent.create(stack.getMetadata());
        }

        if (stack.getItem() == ItemRegistry.HYPER_DENSITY_CELL) {
            return ItemHyperDensityComponent.create(stack.getMetadata());
        }

        if (stack.getItem() == ItemRegistry.FLUID_HYPER_DENSITY_CELL) {
            return ItemFluidHyperDensityComponent.create(stack.getMetadata());
        }

        if (stack.getItem() == ItemRegistry.HYPER_DENSITY_COMPACTING_CELL) {
            return ItemHyperDensityCompactingComponent.create(stack.getMetadata());
        }

        return ItemStack.EMPTY;
    }

    private static int compareConfigurableComponents(ItemStack left, ItemStack right) {
        ComponentInfo leftInfo = ComponentHelper.getComponentInfo(left);
        ComponentInfo rightInfo = ComponentHelper.getComponentInfo(right);
        if (leftInfo == null || rightInfo == null) return 0;

        int channel = leftInfo.getChannelType().compareTo(rightInfo.getChannelType());
        if (channel != 0) return channel;

        int bytes = Long.compare(leftInfo.getBytes(), rightInfo.getBytes());
        if (bytes != 0) return bytes;

        String leftName = left.getItem().getRegistryName().toString();
        String rightName = right.getItem().getRegistryName().toString();
        int name = leftName.compareTo(rightName);
        if (name != 0) return name;

        return Integer.compare(left.getMetadata(), right.getMetadata());
    }

    private static void addIfPresent(List<ItemStack> stacks, @Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;

        stacks.add(stack.copy());
    }
}
