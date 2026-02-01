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

    private static final InventoryLookup lookup1 = new InventoryLookup(1, 1);
    private static final InventoryLookup lookup2 = new InventoryLookup(2, 2);
    private static final InventoryLookup lookup3 = new InventoryLookup(3, 3);

    private final World world;

    public CompactingHelper(World world) {
        this.world = world;
    }

    /**
     * Represents a compression chain with up to 3 tiers.
     */
    public static class CompressionChain {

        private final ItemStack[] stacks = new ItemStack[3];
        private final int[] rates = new int[3];

        public CompressionChain() {
            for (int i = 0; i < 3; i++) {
                stacks[i] = ItemStack.EMPTY;
                rates[i] = 0;
            }
        }

        public CompressionChain(ItemStack[] chain, int[] convRates) {
            for (int i = 0; i < 3; i++) {
                stacks[i] = (chain != null && chain[i] != null) ? chain[i] : ItemStack.EMPTY;
                rates[i] = (convRates != null) ? convRates[i] : 0;
            }
        }

        /**
         * Get the item stack at the given tier.
         * @param tier 0=highest, 1=middle, 2=lowest
         */
        @Nonnull
        public ItemStack getStack(int tier) {
            return tier >= 0 && tier < 3 ? stacks[tier] : ItemStack.EMPTY;
        }

        /**
         * Get the conversion rate at the given tier.
         * The rate is relative to the lowest tier (base = 1).
         * @param tier 0=highest, 1=middle, 2=lowest
         */
        public int getRate(int tier) {
            return tier >= 0 && tier < 3 ? rates[tier] : 0;
        }

        /**
         * Get the number of tiers in this chain (1-3).
         */
        public int getTierCount() {
            int count = 0;
            for (int i = 0; i < 3; i++) {
                if (!stacks[i].isEmpty()) count++;
            }

            return count;
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
            if (outputCount != 4 && outputCount != 9) continue;

            // Create a single-item version for compression check
            ItemStack singleCandidate = candidate.copy();
            singleCandidate.setCount(1);

            // Verify: N of the candidate should compress back to the original
            InventoryLookup lookup = (outputCount == 9) ? lookup3 : lookup2;
            setupLookup(lookup, singleCandidate);
            List<ItemStack> compResults = findAllMatchingRecipes(lookup);

            for (ItemStack comp : compResults) {
                if (areItemsEqual(comp, stack)) {
                    return new Result(singleCandidate, outputCount);
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
     */
    @Nonnull
    public CompressionChain getCompressionChain(@Nonnull ItemStack inputItem) {
        if (inputItem.isEmpty()) return new CompressionChain();

        ItemStack normalized = inputItem.copy();
        normalized.setCount(1);

        Result higher = findHigherTier(normalized);
        Result lower = findLowerTier(normalized);

        ItemStack[] chain = new ItemStack[3];
        int[] rates = new int[3];
        for (int i = 0; i < 3; i++) {
            chain[i] = ItemStack.EMPTY;
            rates[i] = 0;
        }

        // Build chain based on what we found
        if (!higher.getStack().isEmpty() && !lower.getStack().isEmpty()) {
            // Full 3-tier chain: higher -> middle -> lower
            chain[0] = higher.getStack();
            chain[1] = normalized;
            chain[2] = lower.getStack();

            // Rates relative to lowest tier (base = 1)
            rates[0] = higher.getConversionRate() * lower.getConversionRate();
            rates[1] = lower.getConversionRate();
            rates[2] = 1;
        } else if (!higher.getStack().isEmpty()) {
            // 2-tier: higher -> middle (no lower)
            chain[0] = higher.getStack();
            chain[1] = normalized;

            rates[0] = higher.getConversionRate();
            rates[1] = 1;
        } else if (!lower.getStack().isEmpty()) {
            // 2-tier: middle -> lower (no higher)
            chain[0] = normalized;
            chain[1] = lower.getStack();

            rates[0] = lower.getConversionRate();
            rates[1] = 1;
        } else {
            // Single item, no compression
            chain[0] = normalized;
            rates[0] = 1;
        }

        return new CompressionChain(chain, rates);
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
