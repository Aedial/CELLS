package com.cells.integration.nae2.pmt;

import java.util.Arrays;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.Upgrades;
import appeng.api.AEApi;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.items.misc.ItemEncodedPattern;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.Platform;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;
import appeng.util.inv.filter.IAEItemFilter;


/**
 * Pattern Multi-Tool inventory access for the Pattern Card GUI.
 * <p>
 * Provides the PMT's pattern inventory and capacity upgrades when NAE2 is installed.
 * Searches Baubles first, then the player inventory.
 * <p>
 * The PMT has:
 * - 36 pattern slots (4 columns x 9 rows)
 * - Column 0 is always enabled
 * - Columns 1-3 are enabled by capacity upgrades
 * - 3 upgrade slots for capacity upgrades
 */
public class PMTManager implements IAEAppEngInventory {

    // PMT inventory sizes
    public static final int PATTERN_SLOTS = 36;  // 4 columns x 9 rows
    public static final int UPGRADE_SLOTS = 3;
    public static final int ROWS = 9;
    public static final int COLUMNS = 4;

    // Mod IDs
    private static final String NAE2_MOD_ID = "nae2";
    private static final String BAUBLES_MOD_ID = "baubles";

    // The PMT ItemStack from player inventory
    private final ItemStack pmtStack;
    private final World world;

    // Internal inventories (loaded from NBT)
    private final AppEngInternalInventory patternInventory;
    private final AppEngInternalInventory upgradeInventory;

    // The PMT has a fixed 36-slot inventory, so a bounded per-slot cache is enough to
    // avoid reparsing encoded pattern NBT on every GUI frame
    private final ItemStack[] cachedPatternKeys = new ItemStack[PATTERN_SLOTS];
    private final PatternRenderData[] cachedPatternRenderData = new PatternRenderData[PATTERN_SLOTS];

    // Cached upgrade count
    private int installedCapacityUpgrades = 0;

    private boolean loading;
    private boolean dirty;

    /**
     * Creates a PMTManager for the given PMT ItemStack.
     * Private - use findPMT() to get a manager.
     */
    private PMTManager(ItemStack pmtStack, World world) {
        this.pmtStack = pmtStack;
        this.world = world;
        this.patternInventory = new AppEngInternalInventory(this, PATTERN_SLOTS);
        this.patternInventory.setFilter(new PatternFilter());
        this.upgradeInventory = new AppEngInternalInventory(this, UPGRADE_SLOTS);

        Arrays.fill(this.cachedPatternKeys, ItemStack.EMPTY);
        Arrays.fill(this.cachedPatternRenderData, PatternRenderData.EMPTY);

        // Load data from NBT
        this.loading = true;
        loadFromNBT();
        this.loading = false;
    }

    /**
     * Checks if NAE2 is loaded.
     */
    public static boolean isNAE2Loaded() {
        return Loader.isModLoaded(NAE2_MOD_ID);
    }

    /**
     * Checks if Baubles is loaded.
     */
    public static boolean isBaublesLoaded() {
        return Loader.isModLoaded(BAUBLES_MOD_ID);
    }

    /**
     * Finds a Pattern Multi-Tool in the player's inventory.
     * Searches baubles inventory first (if available), then player inventory.
     *
     * @param player The player to search
     * @return PMTManager if found, null otherwise
     */
    @Nullable
    public static PMTManager findPMT(EntityPlayer player) {
        if (!isNAE2Loaded()) return null;

        // Search baubles inventory first (if baubles is loaded)
        if (isBaublesLoaded()) {
            ItemStack baublesStack = findPMTInBaubles(player);
            if (baublesStack != null && !baublesStack.isEmpty()) {
                return new PMTManager(baublesStack, player.world);
            }
        }

        // Search player inventory
        InventoryPlayer inv = player.inventory;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (isPMTItem(stack)) return new PMTManager(stack, player.world);
        }

        return null;
    }

    /**
     * Searches the Baubles inventory for a PMT.
     */
    @Nullable
    @Optional.Method(modid = "baubles")
    private static ItemStack findPMTInBaubles(EntityPlayer player) {
        try {
            IInventory baublesInv = getBaublesInventory(player);
            if (baublesInv == null) return null;

            for (int i = 0; i < baublesInv.getSizeInventory(); i++) {
                ItemStack stack = baublesInv.getStackInSlot(i);
                if (isPMTItem(stack)) return stack;
            }
        } catch (NoSuchMethodError ignored) {
            // Baubles API changed, ignore
        }

        return null;
    }

    /**
     * Gets the Baubles inventory for the player.
     */
    @Optional.Method(modid = "baubles")
    private static IInventory getBaublesInventory(EntityPlayer player) {
        try {
            Class<?> api = Class.forName("baubles.api.BaublesApi");
            Object inventory = api.getMethod("getBaubles", EntityPlayer.class).invoke(null, player);
            return inventory instanceof IInventory ? (IInventory) inventory : null;
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    /**
     * Checks if an ItemStack is a Pattern Multi-Tool.
     */
    public static boolean isPMTItem(ItemStack stack) {
        if (stack.isEmpty()) return false;

        // Check by class name to avoid hard dependency on NAE2
        String className = stack.getItem().getClass().getName();
        return className.equals("co.neeve.nae2.common.items.patternmultitool.ToolPatternMultiTool");
    }

    /**
     * Loads pattern and upgrade inventories from the PMT's NBT data.
     */
    private void loadFromNBT() {
        if (!pmtStack.hasTagCompound()) return;

        NBTTagCompound data = pmtStack.getTagCompound().copy();
        if (data == null) return;

        // Remove "Size" tag that can cause issues (same as ObjPatternMultiTool does)
        if (data.hasKey("inv")) {
            NBTTagCompound invTag = data.getCompoundTag("inv");
            invTag.removeTag("Size");
            patternInventory.readFromNBT(data, "inv");
        }

        if (data.hasKey("upgrades")) {
            NBTTagCompound upgradesTag = data.getCompoundTag("upgrades");
            upgradesTag.removeTag("Size");
            upgradeInventory.readFromNBT(data, "upgrades");
        }

        // Calculate installed capacity upgrades
        updateCapacityUpgradeCount();
    }

    /**
     * Saves changes back to the PMT's NBT data.
     */
    public void saveChanges() {
        if (this.loading) return;

        NBTTagCompound data = Platform.openNbtData(pmtStack);
        patternInventory.writeToNBT(data, "inv");
        upgradeInventory.writeToNBT(data, "upgrades");
        this.dirty = true;
    }

    public boolean consumeDirty() {
        boolean changed = this.dirty;
        this.dirty = false;
        return changed;
    }

    public boolean isPresent(EntityPlayer player) {
        for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
            if (player.inventory.getStackInSlot(slot) == this.pmtStack) return true;
        }

        return isBaublesLoaded() && findPMTInBaubles(player) == this.pmtStack;
    }

    /**
     * Updates the cached count of installed capacity upgrades.
     */
    private void updateCapacityUpgradeCount() {
        installedCapacityUpgrades = 0;

        for (int i = 0; i < upgradeInventory.getSlots(); i++) {
            ItemStack upgrade = upgradeInventory.getStackInSlot(i);
            if (upgrade.isEmpty()) continue;
            if (!(upgrade.getItem() instanceof IUpgradeModule)) continue;

            // Check if it's a capacity upgrade using AE2's IUpgradeModule interface
            IUpgradeModule upgradeModule = (IUpgradeModule) upgrade.getItem();
            if (upgradeModule.getType(upgrade) == Upgrades.CAPACITY) {
                installedCapacityUpgrades += upgrade.getCount();
            }
        }

        // Cap at 3 (maximum capacity upgrade count)
        installedCapacityUpgrades = Math.min(installedCapacityUpgrades, 3);
    }

    /**
     * Checks if this manager has a valid PMT.
     */
    public boolean hasPMT() {
        return !pmtStack.isEmpty() && isPMTItem(pmtStack);
    }

    /**
     * Gets the number of installed capacity upgrades (0-3).
     */
    public int getInstalledCapacityUpgrades() {
        return installedCapacityUpgrades;
    }

    /**
     * Checks if a column is enabled based on capacity upgrades.
     * Column 0 is always enabled. Columns 1-3 require upgrades.
     *
     * @param column Column index (0-3)
     * @return true if column is enabled
     */
    public boolean isColumnEnabled(int column) {
        if (column == 0) return true;
        return column <= installedCapacityUpgrades;
    }

    /**
     * Gets the pattern inventory.
     */
    public IItemHandler getPatternInventory() {
        return patternInventory;
    }

    /**
     * Gets the upgrade inventory.
     */
    public IItemHandler getUpgradeInventory() {
        return upgradeInventory;
    }

    /**
     * Gets the PMT ItemStack.
     */
    public ItemStack getPMTStack() {
        return pmtStack;
    }

    public PatternRenderData getPatternRenderData(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= PATTERN_SLOTS) return PatternRenderData.EMPTY;

        ItemStack patternStack = patternInventory.getStackInSlot(slotIndex);
        if (!isPatternRenderCacheValid(slotIndex, patternStack)) {
            cachedPatternKeys[slotIndex] = patternStack.isEmpty() ? ItemStack.EMPTY : patternStack.copy();
            cachedPatternRenderData[slotIndex] = buildPatternRenderData(patternStack);
        }

        return cachedPatternRenderData[slotIndex];
    }

    private boolean isPatternRenderCacheValid(int slotIndex, ItemStack patternStack) {
        return arePatternStacksEqual(cachedPatternKeys[slotIndex], patternStack);
    }

    private void invalidatePatternRenderCache(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= PATTERN_SLOTS) return;

        cachedPatternKeys[slotIndex] = ItemStack.EMPTY;
        cachedPatternRenderData[slotIndex] = PatternRenderData.EMPTY;
    }

    private static boolean arePatternStacksEqual(ItemStack left, ItemStack right) {
        if (left.isEmpty() || right.isEmpty()) return left.isEmpty() && right.isEmpty();

        return ItemStack.areItemsEqual(left, right)
            && ItemStack.areItemStackTagsEqual(left, right)
            && left.getCount() == right.getCount();
    }

    private PatternRenderData buildPatternRenderData(ItemStack patternStack) {
        if (patternStack.isEmpty()) return PatternRenderData.EMPTY;
        if (!(patternStack.getItem() instanceof ICraftingPatternItem)) {
            return new PatternRenderData(patternStack, 0L);
        }

        try {
            if (patternStack.getItem() instanceof ItemEncodedPattern) {
                ItemEncodedPattern encodedPattern = (ItemEncodedPattern) patternStack.getItem();
                ItemStack output = encodedPattern.getOutput(patternStack);
                if (!output.isEmpty()) return new PatternRenderData(output, output.getCount());
            }

            ICraftingPatternItem patternItem = (ICraftingPatternItem) patternStack.getItem();
            ICraftingPatternDetails details = patternItem.getPatternForItem(patternStack, this.world);
            if (details == null) return new PatternRenderData(patternStack, 0L);

            IAEItemStack[] outputs = details.getOutputs();
            if (outputs.length == 0 || outputs[0] == null) {
                return new PatternRenderData(patternStack, 0L);
            }

            ItemStack output = outputs[0].createItemStack();
            if (output.isEmpty()) return new PatternRenderData(patternStack, 0L);

            return new PatternRenderData(output, outputs[0].getStackSize());
        } catch (RuntimeException e) {
            return new PatternRenderData(patternStack, 0L);
        }
    }

    // ==================== IAEAppEngInventory ====================

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc,
                                  ItemStack removedStack, ItemStack newStack) {
        if (inv == patternInventory) invalidatePatternRenderCache(slot);

        // Update upgrade count when upgrade inventory changes
        if (inv == upgradeInventory) updateCapacityUpgradeCount();

        // Save changes back to NBT
        saveChanges();
    }

    // ==================== Pattern Filter ====================

    /**
     * Filter that only allows patterns in the pattern inventory.
     */
    private static class PatternFilter implements IAEItemFilter {
        @Override
        public boolean allowExtract(IItemHandler inv, int slot, int amount) {
            return true;
        }

        @Override
        public boolean allowInsert(IItemHandler inv, int slot, ItemStack stack) {
            return stack.getItem() instanceof ICraftingPatternItem
                || AEApi.instance().definitions().materials().blankPattern().isSameAs(stack);
        }
    }

    public static class PatternRenderData {
        public static final PatternRenderData EMPTY = new PatternRenderData(ItemStack.EMPTY, 0L);

        private final ItemStack displayStack;
        private final long outputCount;

        public PatternRenderData(ItemStack displayStack, long outputCount) {
            this.displayStack = displayStack;
            this.outputCount = outputCount;
        }

        public ItemStack getDisplayStack() {
            return displayStack;
        }

        public long getOutputCount() {
            return outputCount;
        }
    }
}
