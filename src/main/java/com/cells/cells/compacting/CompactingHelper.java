package com.cells.cells.compacting;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;


/**
 * Utility class for finding compression/decompression relationships between items.
 * 
 * This implementation is optimized to avoid iterating over the entire recipe registry
 * for every lookup. Instead of scanning all recipes, it starts from what the item alone
 * crafts (decomposition) and verifies the reverse relationship.
 * 
 * Compression works by finding crafting recipes where:
 * - Higher tier: Filling a 2x2 or 3x3 grid with item X produces item Y, and item Y decompresses back to X
 * - Lower tier: Item X alone in grid produces N of item Y, and N of item Y in a grid produces item X
 */
public class CompactingHelper {

    /** Default number of tiers when no tier card is installed. */
    public static final int DEFAULT_TIERS = 3;

    /** Maximum supported tiers (for 15x cards). */
    public static final int MAX_SUPPORTED_TIERS = 15;

    private static final InventoryLookup lookup1 = new InventoryLookup(1, 1);
    private static final InventoryLookup lookup2 = new InventoryLookup(2, 2);
    private static final InventoryLookup lookup3 = new InventoryLookup(3, 3);

    private final World world;

    public CompactingHelper(World world) {
        this.world = world;
    }

    /**
     * Represents a compression chain with a dynamic number of tiers.
     * <p>
     * Tier 0 is always the highest (most compressed) form.
     * The main tier is where the partitioned item sits in the chain.
     * </p>
     */
    public static class CompressionChain {

        private final ItemStack[] stacks;
        private final int[] rates;
        private final int mainTierIndex;
        private final int maxTiers;

        public CompressionChain() {
            this(DEFAULT_TIERS);
        }

        public CompressionChain(int maxTiers) {
            this.maxTiers = maxTiers;
            this.stacks = new ItemStack[maxTiers];
            this.rates = new int[maxTiers];
            this.mainTierIndex = 0;

            for (int i = 0; i < maxTiers; i++) {
                stacks[i] = ItemStack.EMPTY;
                rates[i] = 0;
            }
        }

        public CompressionChain(ItemStack[] chain, int[] convRates, int mainTierIndex, int maxTiers) {
            this.maxTiers = maxTiers;
            this.stacks = new ItemStack[maxTiers];
            this.rates = new int[maxTiers];
            this.mainTierIndex = mainTierIndex;

            for (int i = 0; i < maxTiers; i++) {
                stacks[i] = (chain != null && i < chain.length && chain[i] != null) ? chain[i] : ItemStack.EMPTY;
                rates[i] = (convRates != null && i < convRates.length) ? convRates[i] : 0;
            }
        }

        /**
         * Get the item stack at the given tier.
         * @param tier 0=highest (most compressed), increasing=less compressed
         */
        @Nonnull
        public ItemStack getStack(int tier) {
            return tier >= 0 && tier < maxTiers ? stacks[tier] : ItemStack.EMPTY;
        }

        /**
         * Get the conversion rate at the given tier.
         * The rate is relative to the lowest tier (base = 1).
         * @param tier 0=highest, increasing=less compressed
         */
        public int getRate(int tier) {
            return tier >= 0 && tier < maxTiers ? rates[tier] : 0;
        }

        /**
         * Get the number of tiers in this chain.
         */
        public int getTierCount() {
            int count = 0;
            for (int i = 0; i < maxTiers; i++) {
                if (!stacks[i].isEmpty()) count++;
            }

            return count;
        }

        /**
         * Get the max tiers this chain supports.
         */
        public int getMaxTiers() {
            return maxTiers;
        }

        /**
         * Get the tier index where the main (partitioned) item is located.
         */
        public int getMainTierIndex() {
            return mainTierIndex;
        }
    }

    /**
     * Result of a compression lookup.
     */
    public static class Result {

        @Nonnull
        private final ItemStack stack;
        private final int conversionRate;

        public Result(@Nonnull ItemStack stack, int conversionRate) {
            this.stack = stack;
            this.conversionRate = conversionRate;
        }

        @Nonnull
        public ItemStack getStack() {
            return stack;
        }

        /**
         * The number of the original item needed to make one of this result.
         * For higher tier: e.g., 9 iron ingots = 1 iron block (rate = 9)
         * For lower tier: e.g., 1 iron ingot = 9 nuggets (rate = 9)
         */
        public int getConversionRate() {
            return conversionRate;
        }
    }

    /**
     * Find a higher tier (more compressed) form of the given item.
     * E.g., iron ingot -> iron block (rate = 9)
     * 
     * Strategy: Try 3x3 then 2x2 grids and verify that the result decompresses back.
     */
    @Nonnull
    public Result findHigherTier(@Nonnull ItemStack stack) {
        if (stack.isEmpty()) return new Result(ItemStack.EMPTY, 0);

        // Try 3x3 grid first (9 items -> 1 block)
        Result result = tryCompression(stack, lookup3, 9);
        if (!result.getStack().isEmpty()) return result;

        // Try 2x2 grid (4 items -> 1 block)
        return tryCompression(stack, lookup2, 4);
    }

    /**
     * Try to find a compression recipe using the given grid size.
     * Verifies that the result decompresses back to the original item.
     */
    @Nonnull
    private Result tryCompression(@Nonnull ItemStack stack, InventoryLookup lookup, int gridSize) {
        setupLookup(lookup, stack);
        List<ItemStack> candidates = findAllMatchingRecipes(lookup);

        for (ItemStack candidate : candidates) {
            // Skip if result is the same as input
            if (areItemsEqual(candidate, stack)) continue;

            // Verify: candidate alone should decompress back to gridSize of the original
            setupLookup(lookup1, candidate);
            List<ItemStack> decompResults = findAllMatchingRecipes(lookup1);

            for (ItemStack decomp : decompResults) {
                if (decomp.getCount() == gridSize && areItemsEqual(decomp, stack)) {
                    return new Result(candidate, gridSize);
                }
            }
        }

        return new Result(ItemStack.EMPTY, 0);
    }

    /**
     * Find a lower tier (less compressed) form of the given item.
     * E.g., iron ingot -> iron nugget (rate = 9)
     * 
     * Strategy: Check what the item alone crafts to (decomposition), then verify
     * that the result can compress back. This avoids iterating the entire recipe registry.
     * 
     * Also checks for recipes that produce 1 output (common in mods) where 9 or 4
     * of the output compress back to the original.
     */
    @Nonnull
    public Result findLowerTier(@Nonnull ItemStack stack) {
        if (stack.isEmpty()) return new Result(ItemStack.EMPTY, 0);

        // Check what this item alone crafts to (1x1 grid)
        setupLookup(lookup1, stack);
        List<ItemStack> decompCandidates = findAllMatchingRecipes(lookup1);

        for (ItemStack candidate : decompCandidates) {
            // Skip if result is the same as input
            if (areItemsEqual(candidate, stack)) continue;

            int outputCount = candidate.getCount();

            // Standard case: recipe outputs 4 or 9 items directly
            if (outputCount == 4 || outputCount == 9) {
                // Create a single-item version for compression check
                ItemStack singleCandidate = candidate.copy();
                singleCandidate.setCount(1);

                // Verify: N of the candidate should compress back to the original
                InventoryLookup lookup = (outputCount == 9) ? lookup3 : lookup2;
                setupLookup(lookup, singleCandidate);
                List<ItemStack> compResults = findAllMatchingRecipes(lookup);

                for (ItemStack comp : compResults) {
                    if (areItemsEqual(comp, stack)) return new Result(singleCandidate, outputCount);
                }
            }

            // Mod compatibility: recipe outputs 1 item, check if 9 or 4 compress back
            if (outputCount == 1) {
                ItemStack singleCandidate = candidate.copy();
                singleCandidate.setCount(1);

                // Try 3x3 first (rate = 9)
                setupLookup(lookup3, singleCandidate);
                List<ItemStack> compResults9 = findAllMatchingRecipes(lookup3);
                for (ItemStack comp : compResults9) {
                    if (areItemsEqual(comp, stack)) return new Result(singleCandidate, 9);
                }

                // Try 2x2 (rate = 4)
                setupLookup(lookup2, singleCandidate);
                List<ItemStack> compResults4 = findAllMatchingRecipes(lookup2);
                for (ItemStack comp : compResults4) {
                    if (areItemsEqual(comp, stack)) return new Result(singleCandidate, 4);
                }
            }
        }

        return new Result(ItemStack.EMPTY, 0);
    }

    /**
     * Get the compression chain for an item.
     * Returns [higher, middle, lower] where higher/lower can be empty if not found.
     * 
     * This only looks ONE tier up and ONE tier down from the input item.
     * The input item becomes the middle tier.
     * 
     * @deprecated Use {@link #getCompressionChain(ItemStack, int, int)} for dynamic tier support.
     */
    @Deprecated
    @Nonnull
    public CompressionChain getCompressionChain(@Nonnull ItemStack inputItem) {
        return getCompressionChain(inputItem, 1, 1);
    }

    /**
     * Get the compression chain for an item with configurable tier depths.
     * <p>
     * Builds a compression chain centered on the input item, extending
     * upward (compressed) and downward (decompressed) by the specified
     * number of tiers.
     * </p>
     * 
     * @param inputItem The item to build the chain around (becomes the main tier)
     * @param tiersUp Number of tiers to look upward (toward compressed forms), 0 or more
     * @param tiersDown Number of tiers to look downward (toward decompressed forms), 0 or more
     * @return A CompressionChain with the main item and any found tiers
     */
    @Nonnull
    public CompressionChain getCompressionChain(@Nonnull ItemStack inputItem, int tiersUp, int tiersDown) {
        if (inputItem.isEmpty()) return new CompressionChain();

        ItemStack normalized = inputItem.copy();
        normalized.setCount(1);

        // Collect tiers going up (more compressed) - will be reversed
        List<ItemStack> upStacks = new ArrayList<>();
        List<Integer> upRatios = new ArrayList<>();
        ItemStack current = normalized;

        for (int i = 0; i < tiersUp; i++) {
            Result higher = findHigherTier(current);
            if (higher.getStack().isEmpty()) break;

            upStacks.add(higher.getStack());
            upRatios.add(higher.getConversionRate());
            current = higher.getStack();
        }

        // Collect tiers going down (less compressed)
        List<ItemStack> downStacks = new ArrayList<>();
        List<Integer> downRatios = new ArrayList<>();
        current = normalized;

        for (int i = 0; i < tiersDown; i++) {
            Result lower = findLowerTier(current);
            if (lower.getStack().isEmpty()) break;

            downStacks.add(lower.getStack());
            downRatios.add(lower.getConversionRate());
            current = lower.getStack();
        }

        // Total chain size: up tiers + main item + down tiers
        int totalTiers = upStacks.size() + 1 + downStacks.size();
        int mainTierIndex = upStacks.size(); // Main item is after all up tiers

        ItemStack[] chain = new ItemStack[totalTiers];
        int[] rates = new int[totalTiers];

        // Fill chain: highest (most compressed) first
        // upStacks are in order [first up, second up, ...] so reverse for chain order
        int idx = 0;
        for (int i = upStacks.size() - 1; i >= 0; i--) {
            chain[idx++] = upStacks.get(i);
        }

        // Main item
        chain[idx++] = normalized;

        // Down tiers (already in order - lowest first from main)
        for (int i = 0; i < downStacks.size(); i++) {
            chain[idx++] = downStacks.get(i);
        }

        // Calculate rates relative to lowest tier (base = 1)
        // The ratio between adjacent tiers is stored in upRatios/downRatios:
        // - upRatios[k] = ratio between tier (mainTierIndex - k - 1) and tier (mainTierIndex - k)
        // - downRatios[k] = ratio between tier (mainTierIndex + k) and tier (mainTierIndex + k + 1)
        rates[totalTiers - 1] = 1; // Lowest tier is always 1

        for (int i = totalTiers - 2; i >= 0; i--) {
            int ratio;

            if (i >= mainTierIndex) {
                // At or below main tier: use downRatios
                int downIdx = i - mainTierIndex;
                ratio = (downIdx < downRatios.size()) ? downRatios.get(downIdx) : 1;
            } else {
                // Above main tier: use upRatios (indexed from main going up)
                int upIdx = mainTierIndex - i - 1;
                ratio = (upIdx < upRatios.size()) ? upRatios.get(upIdx) : 1;
            }

            rates[i] = rates[i + 1] * ratio;
        }

        return new CompressionChain(chain, rates, mainTierIndex, totalTiers);
    }

    private void setupLookup(InventoryLookup lookup, ItemStack stack) {
        int size = lookup.getWidth() * lookup.getHeight();
        ItemStack template = stack.copy();
        template.setCount(1);

        for (int i = 0; i < size; i++) lookup.setInventorySlotContents(i, template.copy());
    }

    private List<ItemStack> findAllMatchingRecipes(InventoryCrafting crafting) {
        List<ItemStack> candidates = new ArrayList<>();

        for (IRecipe recipe : CraftingManager.REGISTRY) {
            if (recipe.matches(crafting, world)) {
                ItemStack result = recipe.getCraftingResult(crafting);
                if (!result.isEmpty()) candidates.add(result);
            }
        }

        return candidates;
    }

    private static boolean areItemsEqual(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;

        return a.getItem() == b.getItem() && a.getMetadata() == b.getMetadata();
    }

    /**
     * Simple inventory implementation for recipe lookups.
     */
    private static class InventoryLookup extends InventoryCrafting {

        private final ItemStack[] stacks;
        private final int width;
        private final int height;

        public InventoryLookup(int width, int height) {
            super(null, width, height);
            this.width = width;
            this.height = height;
            this.stacks = new ItemStack[width * height];

            for (int i = 0; i < stacks.length; i++) stacks[i] = ItemStack.EMPTY;
        }

        @Override
        public int getSizeInventory() {
            return stacks.length;
        }

        @Override
        public boolean isEmpty() {
            for (ItemStack stack : stacks) {
                if (!stack.isEmpty()) return false;
            }

            return true;
        }

        @Override
        @Nonnull
        public ItemStack getStackInSlot(int index) {
            if (index < 0 || index >= stacks.length) return ItemStack.EMPTY;

            return stacks[index];
        }

        @Override
        public void setInventorySlotContents(int index, @Nonnull ItemStack stack) {
            if (index >= 0 && index < stacks.length) stacks[index] = stack;
        }

        @Override
        @Nonnull
        public ItemStack removeStackFromSlot(int index) {
            ItemStack stack = getStackInSlot(index);
            setInventorySlotContents(index, ItemStack.EMPTY);

            return stack;
        }

        @Override
        @Nonnull
        public ItemStack decrStackSize(int index, int count) {
            ItemStack stack = getStackInSlot(index);
            if (stack.isEmpty()) return ItemStack.EMPTY;

            return stack.splitStack(count);
        }

        @Override
        public int getWidth() {
            return width;
        }

        @Override
        public int getHeight() {
            return height;
        }

        @Override
        public void clear() {
            for (int i = 0; i < stacks.length; i++) stacks[i] = ItemStack.EMPTY;
        }
    }
}
