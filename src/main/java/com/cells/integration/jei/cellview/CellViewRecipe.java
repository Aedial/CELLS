package com.cells.integration.jei.cellview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.IItemHandler;

import appeng.api.storage.ICellInventory;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.util.Platform;

import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeWrapper;

import com.cells.ItemRegistry;
import com.cells.cells.configurable.AbstractConfigurableCellInventory;
import com.cells.cells.configurable.ComponentHelper;
import com.cells.cells.configurable.ComponentInfo;
import com.cells.cells.hyperdensity.compacting.HyperDensityCompactingCellInventory;
import com.cells.cells.hyperdensity.fluid.FluidHyperDensityCellInventory;
import com.cells.cells.hyperdensity.item.HyperDensityCellInventory;
import com.cells.cells.hyperdensity.item.ItemHyperDensityCellBase;
import com.cells.cells.normal.compacting.CompactingCellInventory;
import com.cells.integration.jei.cellview.CellViewHelper.CellType;
import com.cells.util.CellUpgradeHelper;
import com.cells.util.NBTSizeHelper;


/**
 * JEI recipe wrapper for displaying cell contents.
 * <p>
 * Extracts and stores all cell data for display in the JEI category.
 * Supports any cell registered with AE2's cell handler system,
 * including item, fluid, essentia, and gas channels.
 * <p>
 * Uses raw types internally to avoid Java 8 generics inference issues
 * across different storage channel types.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class CellViewRecipe implements IRecipeWrapper {

    private final ItemStack cellStack;
    private final IStorageChannel channel;
    private final ICellInventory cellInventory;
    private final CellType cellType;

    // Cached cell information
    private final List<StoredStackInfo> storedStacks;
    private final List<ItemStack> installedUpgrades;
    private final long usedBytes;
    private final long totalBytes;
    private final int usedTypes;
    private final int maxTypes;
    private final long bytesPerType;
    private final int nbtSize;

    // Compacting cell specific data
    private final boolean isCompacting;
    private final boolean hasPartition;
    private final boolean chainInitialized;
    private final ItemStack partitionedItem;
    private final List<ItemStack> compressionChain;
    private final int tiersUp;
    private final int tiersDown;
    private final boolean hasOreDictCard;

    // Hyper-Density specific data
    private final boolean isHyperDensity;
    private final long byteMultiplier;

    // Configurable cell specific data
    private final boolean isConfigurable;
    private final String componentName;

    // Equal distribution (from upgrade card or built-in for configurable cells)
    private final boolean hasEqualDistribution;
    private final int equalDistributionLimit;
    private final long perTypeLimit;

    /**
     * Data class for stored stack information.
     */
    public static class StoredStackInfo {
        public final IAEStack stack;
        public final long count;
        public final long bytesUsed;

        public StoredStackInfo(IAEStack stack, long count, long bytesUsed) {
            this.stack = stack;
            this.count = count;
            this.bytesUsed = bytesUsed;
        }
    }

    public CellViewRecipe(ItemStack cellStack) {
        this.cellStack = cellStack.copy();
        this.cellType = CellViewHelper.getCellType(cellStack);

        // Use CellViewHelper to detect channel and get handler
        CellViewHelper.CellInfo cellInfo = CellViewHelper.getCellInfo(cellStack);

        if (cellInfo != null) {
            this.channel = cellInfo.getChannel();
            this.cellInventory = cellInfo.getHandler().getCellInv();
        } else {
            this.channel = null;
            this.cellInventory = null;
        }

        if (cellInventory != null) {
            this.totalBytes = cellInventory.getTotalBytes();
            this.usedBytes = cellInventory.getUsedBytes();
            this.maxTypes = (int) cellInventory.getTotalItemTypes();
            this.usedTypes = (int) cellInventory.getStoredItemTypes();
            this.bytesPerType = cellInventory.getBytesPerType();

            // Extract stored stacks and sort by count (largest first)
            this.storedStacks = extractStoredStacks();

            // Extract installed upgrades
            this.installedUpgrades = extractUpgrades();

            // Calculate NBT size
            NBTTagCompound nbt = Platform.openNbtData(cellStack);
            this.nbtSize = NBTSizeHelper.calculateSize(nbt);

            // Extract cell-type specific data
            CellTypeData typeData = extractCellTypeData();
            this.isCompacting = typeData.isCompacting;
            this.hasPartition = typeData.hasPartition;
            this.chainInitialized = typeData.chainInitialized;
            this.partitionedItem = typeData.partitionedItem;
            this.compressionChain = typeData.compressionChain;
            this.tiersUp = typeData.tiersUp;
            this.tiersDown = typeData.tiersDown;
            this.hasOreDictCard = typeData.hasOreDictCard;
            this.isHyperDensity = typeData.isHyperDensity;
            this.byteMultiplier = typeData.byteMultiplier;
            this.isConfigurable = typeData.isConfigurable;
            this.componentName = typeData.componentName;
            this.hasEqualDistribution = typeData.hasEqualDistribution;
            this.equalDistributionLimit = typeData.equalDistributionLimit;
            this.perTypeLimit = typeData.perTypeLimit;
        } else {
            this.totalBytes = 0;
            this.usedBytes = 0;
            this.maxTypes = 0;
            this.usedTypes = 0;
            this.bytesPerType = 0;
            this.storedStacks = Collections.emptyList();
            this.installedUpgrades = Collections.emptyList();
            this.nbtSize = 0;

            // Default cell-type specific data
            this.isCompacting = false;
            this.hasPartition = false;
            this.chainInitialized = false;
            this.partitionedItem = ItemStack.EMPTY;
            this.compressionChain = Collections.emptyList();
            this.tiersUp = 0;
            this.tiersDown = 0;
            this.hasOreDictCard = false;
            this.isHyperDensity = false;
            this.byteMultiplier = 1;
            this.isConfigurable = false;
            this.componentName = "";
            this.hasEqualDistribution = false;
            this.equalDistributionLimit = 0;
            this.perTypeLimit = 0;
        }
    }

    /**
     * Internal data class for cell-type specific extraction.
     */
    private static class CellTypeData {
        boolean isCompacting = false;
        boolean hasPartition = false;
        boolean chainInitialized = false;
        ItemStack partitionedItem = ItemStack.EMPTY;
        List<ItemStack> compressionChain = Collections.emptyList();
        int tiersUp = 0;
        int tiersDown = 0;
        boolean hasOreDictCard = false;
        boolean isHyperDensity = false;
        long byteMultiplier = 1;
        boolean isConfigurable = false;
        String componentName = "";
        boolean hasEqualDistribution = false;
        int equalDistributionLimit = 0;
        long perTypeLimit = 0;
    }

    /**
     * Extract cell-type specific data based on the cell inventory type.
     */
    private CellTypeData extractCellTypeData() {
        CellTypeData data = new CellTypeData();

        // Check for compacting cell inventories (normal and HD)
        if (cellInventory instanceof CompactingCellInventory) {
            CompactingCellInventory compacting = (CompactingCellInventory) cellInventory;
            data.isCompacting = true;
            data.hasPartition = compacting.hasPartition();
            data.chainInitialized = compacting.isChainInitialized();
            data.partitionedItem = compacting.getPartitionedItem();
            data.tiersUp = compacting.getTiersUp();
            data.tiersDown = compacting.getTiersDown();
            data.compressionChain = extractCompressionChain(compacting);
            data.hasOreDictCard = hasOreDictUpgrade();
        } else if (cellInventory instanceof HyperDensityCompactingCellInventory) {
            HyperDensityCompactingCellInventory hdCompacting = (HyperDensityCompactingCellInventory) cellInventory;
            data.isCompacting = true;
            data.isHyperDensity = true;
            data.byteMultiplier = ItemHyperDensityCellBase.BYTE_MULTIPLIER;
            data.hasPartition = hdCompacting.hasPartition();
            data.chainInitialized = hdCompacting.isChainInitialized();
            data.partitionedItem = hdCompacting.getPartitionedItem();
            data.tiersUp = hdCompacting.getTiersUp();
            data.tiersDown = hdCompacting.getTiersDown();
            data.compressionChain = extractHDCompressionChain(hdCompacting);
            data.hasOreDictCard = hasOreDictUpgrade();
        }

        // Check for hyper-density cell inventories
        if (cellInventory instanceof HyperDensityCellInventory) {
            data.isHyperDensity = true;
            data.byteMultiplier = ItemHyperDensityCellBase.BYTE_MULTIPLIER;
        } else if (cellInventory instanceof FluidHyperDensityCellInventory) {
            data.isHyperDensity = true;
            data.byteMultiplier = ItemHyperDensityCellBase.BYTE_MULTIPLIER;
        }

        // Check for configurable cell inventories (have built-in equal distribution)
        if (cellInventory instanceof AbstractConfigurableCellInventory) {
            data.isConfigurable = true;
            data.hasEqualDistribution = true; // Built-in equal distribution
            ItemStack installedComponent = ComponentHelper.getInstalledComponent(cellStack);
            ComponentInfo componentInfo = ComponentHelper.getComponentInfo(installedComponent);
            if (componentInfo != null) {
                data.componentName = componentInfo.getTierName();
                int effectiveMaxTypes = ComponentHelper.getEffectiveMaxTypes(cellStack, componentInfo.getChannelType());
                data.equalDistributionLimit = effectiveMaxTypes;
                long physicalPerType = ComponentHelper.calculatePhysicalPerTypeCapacity(componentInfo, effectiveMaxTypes);
                long userMaxPerType = ComponentHelper.getMaxPerType(cellStack);
                data.perTypeLimit = Math.min(userMaxPerType, physicalPerType);
            }
        } else {
            // Check for Equal Distribution Card upgrade in non-configurable cells
            IItemHandler upgrades = cellInventory.getUpgradesInventory();
            int eqDistLimit = CellUpgradeHelper.getEqualDistributionLimit(upgrades);
            if (eqDistLimit > 0) {
                data.hasEqualDistribution = true;
                data.equalDistributionLimit = eqDistLimit;

                // Calculate per-type limit for cells with equal distribution upgrade
                // Formula: (totalBytes - eqDistLimit * bytesPerType) * unitsPerByte * multiplier / eqDistLimit
                long displayBytes = totalBytes;
                if (data.isHyperDensity) {
                    // HD cells report display bytes, which is already multiplied
                    displayBytes = totalBytes;
                }
                long overhead = (long) eqDistLimit * bytesPerType;
                long availableBytes = displayBytes - overhead;

                if (availableBytes > 0 && channel != null) {
                    int unitsPerByte = channel.getUnitsPerByte();
                    long multiplier = data.isHyperDensity ? data.byteMultiplier : 1;
                    data.perTypeLimit = (availableBytes * unitsPerByte * multiplier) / eqDistLimit;
                }
            }
        }

        return data;
    }

    /**
     * Extract compression chain items from a compacting cell.
     */
    private List<ItemStack> extractCompressionChain(CompactingCellInventory compacting) {
        List<ItemStack> chain = new ArrayList<>();
        chain.addAll(compacting.getAllHigherTierItems());
        ItemStack main = compacting.getPartitionedItem();
        if (!main.isEmpty()) chain.add(main);
        chain.addAll(compacting.getAllLowerTierItems());

        return chain;
    }

    /**
     * Extract compression chain items from a HD compacting cell.
     */
    private List<ItemStack> extractHDCompressionChain(HyperDensityCompactingCellInventory hdCompacting) {
        List<ItemStack> chain = new ArrayList<>();
        chain.addAll(hdCompacting.getAllHigherTierItems());
        ItemStack main = hdCompacting.getPartitionedItem();
        if (!main.isEmpty()) chain.add(main);
        chain.addAll(hdCompacting.getAllLowerTierItems());

        return chain;
    }

    /**
     * Check if the cell has an ore dictionary upgrade card installed.
     */
    private boolean hasOreDictUpgrade() {
        for (ItemStack upgrade : installedUpgrades) {
            // Check for oredict card by inspecting the item
            if (upgrade.getItem() == ItemRegistry.OREDICT_CARD) return true;
        }

        return false;
    }

    private List<StoredStackInfo> extractStoredStacks() {
        List<StoredStackInfo> result = new ArrayList<>();

        if (cellInventory == null || channel == null) return result;

        IItemList available = cellInventory.getAvailableItems(channel.createList());

        for (Object obj : available) {
            IAEStack stack = (IAEStack) obj;
            if (stack == null) continue;

            long count = stack.getStackSize();
            if (count <= 0) continue;

            // Calculate bytes used by this stack
            // Bytes = bytesPerType + ceil(count / unitsPerByte)
            long unitsPerByte = channel.getUnitsPerByte();
            long bytesUsed = bytesPerType + (long) Math.ceil((double) count / unitsPerByte);

            result.add(new StoredStackInfo(stack.copy(), count, bytesUsed));
        }

        // Sort by count descending
        Collections.sort(result, new Comparator<StoredStackInfo>() {
            @Override
            public int compare(StoredStackInfo a, StoredStackInfo b) {
                return Long.compare(b.count, a.count);
            }
        });

        return result;
    }

    private List<ItemStack> extractUpgrades() {
        List<ItemStack> result = new ArrayList<>();

        if (cellInventory == null) return result;

        IItemHandler upgrades = cellInventory.getUpgradesInventory();
        if (upgrades == null) return result;

        for (int i = 0; i < upgrades.getSlots(); i++) {
            ItemStack stack = upgrades.getStackInSlot(i);
            if (!stack.isEmpty()) result.add(stack.copy());
        }

        return result;
    }

    @Override
    public void getIngredients(@Nonnull IIngredients ingredients) {
        // Both input AND output are required for JEI to find this recipe
        // when pressing R (lookup recipes) or U (lookup usages) on the cell
        ingredients.setInput(VanillaTypes.ITEM, cellStack);
        ingredients.setOutput(VanillaTypes.ITEM, cellStack);
    }

    // Getters for cell information

    public ItemStack getCellStack() {
        return cellStack;
    }

    @Nullable
    public IStorageChannel getChannel() {
        return channel;
    }

    @Nullable
    public ICellInventory getCellInventory() {
        return cellInventory;
    }

    public List<StoredStackInfo> getStoredStacks() {
        return storedStacks;
    }

    public List<ItemStack> getInstalledUpgrades() {
        return installedUpgrades;
    }

    public long getUsedBytes() {
        return usedBytes;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public int getUsedTypes() {
        return usedTypes;
    }

    public int getMaxTypes() {
        return maxTypes;
    }

    public long getBytesPerType() {
        return bytesPerType;
    }

    public int getNbtSize() {
        return nbtSize;
    }

    /**
     * Get the overhead bytes due to type headers.
     * Each stored type consumes bytesPerType bytes of overhead.
     *
     * @return Total overhead bytes
     */
    public long getOverheadBytes() {
        return bytesPerType * usedTypes;
    }

    public boolean isEmpty() {
        return storedStacks.isEmpty();
    }

    public boolean hasValidInventory() {
        return cellInventory != null;
    }

    // Cell type getters

    public CellType getCellType() {
        return cellType;
    }

    /**
     * Check if this is a Creative Cell (infinite item source, no bytes consumed).
     */
    public boolean isCreative() {
        return cellType == CellType.CREATIVE;
    }

    // Compacting cell getters

    public boolean isCompacting() {
        return isCompacting;
    }

    public boolean hasPartition() {
        return hasPartition;
    }

    public boolean isChainInitialized() {
        return chainInitialized;
    }

    public ItemStack getPartitionedItem() {
        return partitionedItem;
    }

    public List<ItemStack> getCompressionChain() {
        return compressionChain;
    }

    public int getTiersUp() {
        return tiersUp;
    }

    public int getTiersDown() {
        return tiersDown;
    }

    public boolean hasOreDictCard() {
        return hasOreDictCard;
    }

    // Hyper-Density getters

    public boolean isHyperDensity() {
        return isHyperDensity;
    }

    public long getByteMultiplier() {
        return byteMultiplier;
    }

    // Configurable cell getters

    public boolean isConfigurable() {
        return isConfigurable;
    }

    public String getComponentName() {
        return componentName;
    }

    // Equal distribution getters

    /**
     * Check if this cell has equal distribution active (either built-in for configurable cells,
     * or from an Equal Distribution Card upgrade).
     */
    public boolean hasEqualDistribution() {
        return hasEqualDistribution;
    }

    /**
     * Get the equal distribution type limit.
     * This is the maximum number of types the cell can hold when equal distribution is active.
     */
    public int getEqualDistributionLimit() {
        return equalDistributionLimit;
    }

    /**
     * Get the per-type capacity limit when equal distribution is active.
     * Returns 0 if equal distribution is not active.
     */
    public long getPerTypeLimit() {
        return perTypeLimit;
    }
}
