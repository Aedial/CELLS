package com.cells.blocks.compactingpatternexposer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import appeng.api.util.AECableType;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.EmptyHandler;
import net.minecraftforge.common.util.Constants;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.ITickManager;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.container.ContainerNull;
import appeng.helpers.PatternHelper;
import appeng.me.helpers.MachineSource;
import appeng.tile.grid.AENetworkInvTile;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.inv.InvOperation;
import appeng.util.item.AEItemStack;

import com.cells.cells.compacting.CompactingHelper;
import com.cells.util.ItemStackKey;


/**
 * Tile entity that exposes compacting conversions as processing patterns.
 * <p>
 * Patterns are generated from ghost filters. When AE2 pushes one of those patterns,
 * the tile immediately queues the resulting output back into ME storage on the next
 * network tick so the crafting CPU can consume the produced items.
 */
public class TileCompactingPatternExposer extends AENetworkInvTile implements ICraftingProvider, IGridTickable {

    public static final int FILTER_SLOTS = 18;

    private static final String NBT_FILTERS = "filters";
    private static final String NBT_MULTIPLIERS = "multipliers";
    private static final String NBT_PENDING_OUTPUTS = "pendingOutputs";
    private static final int MAX_PENDING_PATTERNS = 64;
    private static final int MAX_VALIDATED_PATTERN_CACHE_SIZE = 128;
    private static final long DEFAULT_PATTERN_MULTIPLIER = 1L;
    public static final long MAX_PATTERN_MULTIPLIER = Integer.MAX_VALUE;
    private static final long MAX_ENCODED_PATTERN_STACK_SIZE = Integer.MAX_VALUE;

    private final AppEngInternalInventory filterInventory = new AppEngInternalInventory(this, FILTER_SLOTS, 1);
    private final IActionSource actionSource = new MachineSource(this);
    private final List<IAEItemStack> pendingOutputs = new ArrayList<>();
    private final IAEItemStack[] cachedFilterStacks = new IAEItemStack[FILTER_SLOTS];
    private final long[] patternMultipliers = new long[FILTER_SLOTS];
    private final long[] cachedPatternMultiplierLimits = new long[FILTER_SLOTS];
    private final SlotPatterns[] slotPatterns = new SlotPatterns[FILTER_SLOTS];
    private final PatternPreview[] upwardPreviews = new PatternPreview[FILTER_SLOTS];
    private final PatternPreview[] downwardPreviews = new PatternPreview[FILTER_SLOTS];
    private final Map<PatternSignature, IAEItemStack> validatedPatternOutputs =
        new LinkedHashMap<PatternSignature, IAEItemStack>(MAX_VALIDATED_PATTERN_CACHE_SIZE, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<PatternSignature, IAEItemStack> eldest) {
                return this.size() > MAX_VALIDATED_PATTERN_CACHE_SIZE;
            }
        };

    private boolean patternsDirty = true;
    private boolean previewsDirty = true;
    private boolean multiplierLimitsDirty = true;
    private boolean loadingFilters = false;

    public TileCompactingPatternExposer() {
        Arrays.fill(this.patternMultipliers, DEFAULT_PATTERN_MULTIPLIER);
        Arrays.fill(this.cachedPatternMultiplierLimits, MAX_PATTERN_MULTIPLIER);

        // The GUI reads ghost filters and previews from the tile caches on both sides,
        // so client sync must fire the same inventory-change path that refreshes them.
        this.filterInventory.setEnableClientEvents(true);
        this.getProxy().setFlags(GridFlags.REQUIRE_CHANNEL);
        this.getProxy().setIdlePowerUsage(1.0);
    }

    @Override
    public void onReady() {
        super.onReady();

        this.multiplierLimitsDirty = true;
        boolean multipliersChanged = this.validateAllPatternMultipliers();
        this.patternsDirty = true;
        this.previewsDirty = true;

        if (multipliersChanged && !this.world.isRemote) this.markDirty();

        this.notifyPatternsChanged();
        this.updateTickState();
    }

    @Override
    public void gridChanged() {
        super.gridChanged();
        this.updateTickState();
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    @Override
    @Nonnull
    public AECableType getCableConnectionType(@Nonnull final AEPartLocation dir) {
        return AECableType.SMART;
    }

    @MENetworkEventSubscribe
    public void onPowerStatusChange(MENetworkPowerStatusChange ignored) {
        this.updateTickState();
    }

    @MENetworkEventSubscribe
    public void onChannelChange(MENetworkChannelsChanged ignored) {
        this.updateTickState();
        this.notifyPatternsChanged();
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);

        this.loadingFilters = true;
        NBTTagCompound filters = data.getCompoundTag(NBT_FILTERS);
        for (int slot = 0; slot < FILTER_SLOTS; slot++) {
            this.filterInventory.setStackInSlot(slot, new ItemStack(filters.getCompoundTag("item" + slot)));
        }
        this.loadingFilters = false;

        Arrays.fill(this.patternMultipliers, DEFAULT_PATTERN_MULTIPLIER);
        NBTTagCompound multipliers = data.getCompoundTag(NBT_MULTIPLIERS);
        for (int slot = 0; slot < FILTER_SLOTS; slot++) {
            this.patternMultipliers[slot] = this.sanitizePatternMultiplier(multipliers.getLong("slot" + slot));
        }

        this.pendingOutputs.clear();
        NBTTagList pending = data.getTagList(NBT_PENDING_OUTPUTS, Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < pending.tagCount(); index++) {
            IAEItemStack stack = AEItemStack.fromNBT(pending.getCompoundTagAt(index));
            if (stack != null && stack.getStackSize() > 0) this.pendingOutputs.add(stack);
        }

        this.refreshAllFilterCaches();
        this.multiplierLimitsDirty = true;
        this.validateAllPatternMultipliers();
        this.validatedPatternOutputs.clear();
        this.patternsDirty = true;
        this.previewsDirty = true;
    }

    @Override
    public NBTTagCompound writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);

        NBTTagCompound filters = new NBTTagCompound();
        for (int slot = 0; slot < FILTER_SLOTS; slot++) {
            NBTTagCompound itemData = new NBTTagCompound();
            ItemStack stack = this.filterInventory.getStackInSlot(slot);
            if (!stack.isEmpty()) stack.writeToNBT(itemData);
            filters.setTag("item" + slot, itemData);
        }
        data.setTag(NBT_FILTERS, filters);

        NBTTagCompound multipliers = new NBTTagCompound();
        for (int slot = 0; slot < FILTER_SLOTS; slot++) {
            multipliers.setLong("slot" + slot, this.patternMultipliers[slot]);
        }
        data.setTag(NBT_MULTIPLIERS, multipliers);

        NBTTagList pending = new NBTTagList();
        for (IAEItemStack stack : this.pendingOutputs) {
            NBTTagCompound stackData = new NBTTagCompound();
            stack.writeToNBT(stackData);
            pending.appendTag(stackData);
        }
        data.setTag(NBT_PENDING_OUTPUTS, pending);

        return data;
    }

    @Override
    public @Nonnull IItemHandler getInternalInventory() {
        return EmptyHandler.INSTANCE;
    }

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation operation, ItemStack removed, ItemStack added) {
        if (inv != this.filterInventory) return;

        this.refreshFilterCache(slot);
        this.multiplierLimitsDirty = true;
        this.clampStoredPatternMultiplier(slot);
        this.patternsDirty = true;
        this.previewsDirty = true;

        if (this.loadingFilters || this.world == null || this.world.isRemote) return;

        this.markDirty();
        this.notifyPatternsChanged();
    }

    @Override
    public void provideCrafting(ICraftingProviderHelper craftingHelper) {
        this.rebuildPatternsIfNeeded();

        for (SlotPatterns slotPatterns : this.slotPatterns) {
            if (slotPatterns == null) continue;

            if (slotPatterns.upward != null && slotPatterns.upward.details != null) {
                craftingHelper.addCraftingOption(this, slotPatterns.upward.details);
            }

            if (slotPatterns.downward != null && slotPatterns.downward.details != null) {
                craftingHelper.addCraftingOption(this, slotPatterns.downward.details);
            }
        }
    }

    @Override
    public boolean pushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table) {
        PatternRequest patternRequest = this.createPatternRequest(patternDetails);
        if (patternRequest == null) return false;

        IAEItemStack queuedOutput = this.resolvePatternOutput(patternRequest);
        if (queuedOutput == null) return false;

        if (this.pendingOutputs.size() >= MAX_PENDING_PATTERNS) return false;

        this.pendingOutputs.add(queuedOutput.copy());

        this.markDirty();
        this.updateTickState();
        return true;
    }

    @Override
    public boolean isBusy() {
        return this.pendingOutputs.size() >= MAX_PENDING_PATTERNS;
    }

    @Override
    public @Nonnull TickingRequest getTickingRequest(@Nonnull IGridNode node) {
        return new TickingRequest(1, 20, this.pendingOutputs.isEmpty(), true);
    }

    @Override
    public @Nonnull TickRateModulation tickingRequest(@Nonnull IGridNode node, int ticksSinceLastCall) {
        if (this.pendingOutputs.isEmpty()) return TickRateModulation.SLEEP;

        IMEMonitor<IAEItemStack> itemMonitor = this.getItemMonitor();
        if (itemMonitor == null) return TickRateModulation.SLOWER;

        boolean insertedAnything = false;

        for (int index = 0; index < this.pendingOutputs.size(); index++) {
            IAEItemStack pending = this.pendingOutputs.get(index);
            IAEItemStack remainder = itemMonitor.injectItems(pending.copy(), Actionable.MODULATE, this.actionSource);

            if (remainder == null || remainder.getStackSize() <= 0) {
                this.pendingOutputs.remove(index);
                index--;
                insertedAnything = true;
                continue;
            }

            if (remainder.getStackSize() < pending.getStackSize()) {
                this.pendingOutputs.set(index, remainder);
                insertedAnything = true;
            }

            break;
        }

        if (!insertedAnything) return TickRateModulation.SLOWER;

        this.markDirty();
        this.updateTickState();
        return this.pendingOutputs.isEmpty() ? TickRateModulation.SLEEP : TickRateModulation.URGENT;
    }

    @Nullable
    public IAEItemStack getFilter(int slot) {
        if (slot < 0 || slot >= FILTER_SLOTS) return null;

        IAEItemStack filter = this.cachedFilterStacks[slot];
        if (filter == null) return null;

        return filter.copy();
    }

    public long getPatternMultiplier(int slot) {
        if (slot < 0 || slot >= FILTER_SLOTS) return DEFAULT_PATTERN_MULTIPLIER;

        return this.getValidatedPatternMultiplier(slot);
    }

    public long getPatternMultiplierLimit(int slot) {
        if (slot < 0 || slot >= FILTER_SLOTS) return MAX_PATTERN_MULTIPLIER;

        this.rebuildPatternMultiplierLimitsIfNeeded();
        return this.cachedPatternMultiplierLimits[slot];
    }

    public void setFilterStack(int slot, ItemStack stack) {
        if (slot < 0 || slot >= FILTER_SLOTS) return;

        ItemStack normalized = this.normalizeFilterStack(stack);
        if (this.sameFilterStack(this.cachedFilterStacks[slot], normalized)) return;

        this.filterInventory.setStackInSlot(slot, normalized);
    }

    public void setPatternMultiplier(int slot, long multiplier) {
        if (slot < 0 || slot >= FILTER_SLOTS) return;

        long validated = Math.min(this.getPatternMultiplierLimit(slot), this.sanitizePatternMultiplier(multiplier));
        if (this.patternMultipliers[slot] == validated) return;

        this.patternMultipliers[slot] = validated;
        this.patternsDirty = true;
        this.previewsDirty = true;

        if (this.world == null || this.world.isRemote) return;

        this.markDirty();
        this.notifyPatternsChanged();
    }

    public boolean containsFilterStack(ItemStack stack, int ignoredSlot) {
        ItemStack normalized = this.normalizeFilterStack(stack);
        if (normalized.isEmpty()) return false;

        for (int slot = 0; slot < FILTER_SLOTS; slot++) {
            if (slot == ignoredSlot) continue;

            if (this.sameFilterStack(this.cachedFilterStacks[slot], normalized)) return true;
        }

        return false;
    }

    public int findFirstEmptyFilterSlot() {
        for (int slot = 0; slot < FILTER_SLOTS; slot++) {
            if (this.cachedFilterStacks[slot] == null) return slot;
        }

        return -1;
    }

    @Nullable
    public PatternPreview getUpPreview(int slot) {
        return this.getPreview(slot, true);
    }

    @Nullable
    public PatternPreview getDownPreview(int slot) {
        return this.getPreview(slot, false);
    }

    // Adjacent tiers may coexist as long as they do not expose two different
    // recipes for the same output item. That allows ingot <-> block pairs while
    // still rejecting ambiguous middle tiers such as nugget -> ingot and block -> ingot.
    @Nonnull
    public ItemStack findConflictingPatternOutput(int slot, ItemStack stack) {
        ItemStack normalized = this.normalizeFilterStack(stack);
        if (normalized.isEmpty() || this.world == null) return ItemStack.EMPTY;

        CompactingHelper helper = new CompactingHelper(this.world);
        Map<ItemStackKey, ItemStack> existingOutputs = new LinkedHashMap<>();

        for (int otherSlot = 0; otherSlot < FILTER_SLOTS; otherSlot++) {
            if (otherSlot == slot) continue;

            IAEItemStack filter = this.cachedFilterStacks[otherSlot];
            if (filter == null) continue;

            this.collectPatternOutputs(existingOutputs, helper, filter.getDefinition().copy());
        }

        Map<ItemStackKey, ItemStack> candidateOutputs = new LinkedHashMap<>();
        ItemStack selfConflict = this.collectPatternOutputs(candidateOutputs, helper, normalized.copy());
        if (!selfConflict.isEmpty()) return selfConflict;

        for (Map.Entry<ItemStackKey, ItemStack> entry : candidateOutputs.entrySet()) {
            if (existingOutputs.containsKey(entry.getKey())) return entry.getValue().copy();
        }

        return ItemStack.EMPTY;
    }

    @Nullable
    private PatternPreview getPreview(int slot, boolean upward) {
        if (slot < 0 || slot >= FILTER_SLOTS) return null;

        this.rebuildPreviewsIfNeeded();
        return upward ? this.upwardPreviews[slot] : this.downwardPreviews[slot];
    }

    private void rebuildPatternsIfNeeded() {
        if (!this.patternsDirty || this.world == null) return;

        this.patternsDirty = false;
        this.validatedPatternOutputs.clear();

        for (int slot = 0; slot < FILTER_SLOTS; slot++) this.slotPatterns[slot] = null;

        CompactingHelper helper = new CompactingHelper(this.world);

        for (int slot = 0; slot < FILTER_SLOTS; slot++) {
            IAEItemStack filter = this.cachedFilterStacks[slot];
            if (filter == null) continue;

            this.slotPatterns[slot] = new SlotPatterns(
                this.buildUpwardPattern(helper, slot, filter),
                this.buildDownwardPattern(helper, slot, filter)
            );

            this.cacheCurrentPattern(this.slotPatterns[slot].upward);
            this.cacheCurrentPattern(this.slotPatterns[slot].downward);
        }
    }

    private void rebuildPreviewsIfNeeded() {
        if (!this.previewsDirty || this.world == null) return;

        this.previewsDirty = false;
        Arrays.fill(this.upwardPreviews, null);
        Arrays.fill(this.downwardPreviews, null);

        CompactingHelper helper = new CompactingHelper(this.world);

        for (int slot = 0; slot < FILTER_SLOTS; slot++) {
            IAEItemStack filter = this.cachedFilterStacks[slot];
            if (filter == null) continue;

            this.upwardPreviews[slot] = this.buildUpwardPreview(helper, slot, filter);
            this.downwardPreviews[slot] = this.buildDownwardPreview(helper, slot, filter);
        }
    }

    @Nullable
    private PatternPreview buildUpwardPreview(CompactingHelper helper, int slot, IAEItemStack filter) {
        CompactingHelper.Result result = helper.findHigherTier(filter.getDefinition().copy());
        if (result.getStack().isEmpty()) return null;

        long multiplier = this.getValidatedPatternMultiplier(slot);
        long inputCount = (long) result.getConversionRate() * multiplier;
        long outputCount = (long) result.getStack().getCount() * multiplier;

        return this.createPatternPreview(true, result.getStack(), inputCount, outputCount, multiplier);
    }

    @Nullable
    private PatternPreview buildDownwardPreview(CompactingHelper helper, int slot, IAEItemStack filter) {
        ItemStack normalizedFilter = filter.getDefinition().copy();
        CompactingHelper.Result result = helper.findLowerTier(normalizedFilter);
        if (result.getStack().isEmpty()) return null;

        ItemStack output = this.resolveDownwardPatternOutput(normalizedFilter, result);
        if (output.isEmpty()) return null;

        long multiplier = this.getValidatedPatternMultiplier(slot);
        long inputCount = DEFAULT_PATTERN_MULTIPLIER * multiplier;
        long outputCount = (long) output.getCount() * multiplier;

        return this.createPatternPreview(false, output, inputCount, outputCount, multiplier);
    }

    @Nullable
    private ExposedPattern buildUpwardPattern(CompactingHelper helper, int slot, IAEItemStack filter) {
        CompactingHelper.Result result = helper.findHigherTier(filter.getDefinition().copy());
        if (result.getStack().isEmpty()) return null;

        long multiplier = this.getValidatedPatternMultiplier(slot);
        ItemStack output = result.getStack().copy();
        long inputCount = (long) result.getConversionRate() * multiplier;
        long outputCount = (long) output.getCount() * multiplier;

        return this.createExposedPattern(true, filter, inputCount, output, outputCount, multiplier);
    }

    @Nullable
    private ExposedPattern buildDownwardPattern(CompactingHelper helper, int slot, IAEItemStack filter) {
        ItemStack normalizedFilter = filter.getDefinition().copy();
        CompactingHelper.Result result = helper.findLowerTier(normalizedFilter);
        if (result.getStack().isEmpty()) return null;

        ItemStack output = this.resolveDownwardPatternOutput(normalizedFilter, result);
        if (output.isEmpty()) return null;

        long multiplier = this.getValidatedPatternMultiplier(slot);
        long inputCount = DEFAULT_PATTERN_MULTIPLIER * multiplier;
        long outputCount = (long) output.getCount() * multiplier;

        return this.createExposedPattern(false, filter, inputCount, output, outputCount, multiplier);
    }

    @Nonnull
    private ItemStack collectPatternOutputs(Map<ItemStackKey, ItemStack> outputs, CompactingHelper helper,
                                            ItemStack filter) {
        ItemStack conflict = this.registerPatternOutput(outputs, this.findUpwardPatternOutput(helper, filter));
        if (!conflict.isEmpty()) return conflict;

        return this.registerPatternOutput(outputs, this.findDownwardPatternOutput(helper, filter));
    }

    @Nonnull
    private ItemStack findUpwardPatternOutput(CompactingHelper helper, ItemStack filter) {
        CompactingHelper.Result result = helper.findHigherTier(filter.copy());
        if (result.getStack().isEmpty()) return ItemStack.EMPTY;

        return result.getStack().copy();
    }

    @Nonnull
    private ItemStack findDownwardPatternOutput(CompactingHelper helper, ItemStack filter) {
        CompactingHelper.Result result = helper.findLowerTier(filter.copy());
        if (result.getStack().isEmpty()) return ItemStack.EMPTY;

        return this.resolveDownwardPatternOutput(filter, result);
    }

    @Nonnull
    private ItemStack registerPatternOutput(Map<ItemStackKey, ItemStack> outputs, ItemStack output) {
        if (output.isEmpty()) return ItemStack.EMPTY;

        ItemStackKey key = ItemStackKey.of(output);
        if (key == null) return ItemStack.EMPTY;
        if (outputs.containsKey(key)) return output.copy();

        outputs.put(key, output.copy());
        return ItemStack.EMPTY;
    }

    @Nonnull
    private ExposedPattern createExposedPattern(boolean upward, IAEItemStack inputTemplate, long inputCount,
                                                ItemStack outputTemplate, long outputCount, long multiplier) {
        ItemStack input = inputTemplate.getDefinition().copy();
        input.setCount(1);

        ItemStack output = outputTemplate.copy();
        output.setCount(1);

        IAEItemStack cachedOutput = AEItemStack.fromItemStack(output.copy());
        if (cachedOutput != null) cachedOutput.setStackSize(outputCount);

        return new ExposedPattern(
            this.createPatternDetails(input, inputCount, output, outputCount),
            this.createCachedPatternSignature(inputTemplate, inputCount, cachedOutput, outputCount),
            cachedOutput
        );
    }

    @Nonnull
    private PatternPreview createPatternPreview(boolean upward, ItemStack outputTemplate, long inputCount,
                                                long outputCount, long multiplier) {
        ItemStack output = outputTemplate.copy();
        output.setCount(1);

        return new PatternPreview(output, inputCount, outputCount, upward, multiplier);
    }

    @Nullable
    private ICraftingPatternDetails createPatternDetails(ItemStack input, long inputCount, ItemStack output, long outputCount) {
        if (this.world == null) return null;

        Optional<ItemStack> maybePattern = AEApi.instance().definitions().items().encodedPattern().maybeStack(1);
        if (!maybePattern.isPresent()) return null;

        ItemStack patternStack = maybePattern.get();
        NBTTagCompound encodedValue = new NBTTagCompound();
        NBTTagList tagIn = new NBTTagList();
        NBTTagList tagOut = new NBTTagList();

        if (!this.appendEncodedStacks(tagIn, input, inputCount, PatternHelper.PROCESSING_INPUT_LIMIT)) return null;
        if (!this.appendEncodedStacks(tagOut, output, outputCount, PatternHelper.PROCESSING_OUTPUT_LIMIT)) return null;

        encodedValue.setTag("in", tagIn);
        encodedValue.setTag("out", tagOut);
        encodedValue.setBoolean("crafting", false);
        patternStack.setTagCompound(encodedValue);

        if (!(patternStack.getItem() instanceof ICraftingPatternItem)) return null;

        return ((ICraftingPatternItem) patternStack.getItem()).getPatternForItem(patternStack, this.world);
    }

    @Nonnull
    private ItemStack resolveDownwardPatternOutput(ItemStack normalizedFilter, CompactingHelper.Result lowerResult) {
        if (lowerResult.getStack().isEmpty()) return ItemStack.EMPTY;

        ItemStack output = this.findDirectDecompressionOutput(normalizedFilter, lowerResult);
        if (!output.isEmpty()) return output;

        output = lowerResult.getStack().copy();
        if (output.getCount() == 1 && lowerResult.getConversionRate() > 1) {
            output.setCount(lowerResult.getConversionRate());
        }

        return output;
    }

    @Nonnull
    private ItemStack findDirectDecompressionOutput(ItemStack filter, CompactingHelper.Result lowerResult) {
        if (this.world == null) return ItemStack.EMPTY;

        InventoryCrafting lookup = new InventoryCrafting(new ContainerNull(), 1, 1);
        lookup.setInventorySlotContents(0, filter.copy());
        CompactingHelper helper = new CompactingHelper(this.world);

        for (ItemStack recipeOutput : helper.findAllMatchingRecipes(lookup)) {
            if (recipeOutput.isEmpty()) continue;
            if (!this.matchesCompactingType(recipeOutput, lowerResult.getStack())) continue;

            if (this.canCompressBack(helper, recipeOutput, filter, lowerResult.getConversionRate())) {
                return recipeOutput.copy();
            }
        }

        return ItemStack.EMPTY;
    }

    private boolean canCompressBack(CompactingHelper helper, ItemStack candidate, ItemStack expectedResult, int conversionRate) {
        if (this.world == null) return false;
        if (conversionRate != 4 && conversionRate != 9) return false;

        int size = conversionRate == 9 ? 3 : 2;
        InventoryCrafting lookup = new InventoryCrafting(new ContainerNull(), size, size);
        ItemStack single = candidate.copy();
        single.setCount(1);

        for (int slot = 0; slot < size * size; slot++) {
            lookup.setInventorySlotContents(slot, single.copy());
        }

        for (ItemStack recipeOutput : helper.findAllMatchingRecipes(lookup)) {
            if (this.matchesCompactingType(recipeOutput, expectedResult)) return true;
        }

        return false;
    }

    @Nullable
    private IAEItemStack resolvePatternOutput(@Nonnull PatternRequest patternRequest) {
        IAEItemStack cachedOutput = this.validatedPatternOutputs.get(patternRequest.signature);
        if (cachedOutput != null) return cachedOutput;

        return this.validateAndCachePattern(patternRequest);
    }

    private boolean matchesCompactingType(ItemStack left, ItemStack right) {
        if (left.isEmpty() || right.isEmpty()) return left.isEmpty() && right.isEmpty();

        return left.getItem() == right.getItem()
            && left.getItemDamage() == right.getItemDamage()
            && ItemStack.areItemStackTagsEqual(left, right);
    }

    private boolean sameFilterStack(@Nullable IAEItemStack cachedFilter, ItemStack stack) {
        if (stack.isEmpty()) return cachedFilter == null;
        if (cachedFilter == null) return false;

        return cachedFilter.equals(stack);
    }

    private boolean samePatternStack(IAEItemStack left, ItemStack right, long expectedCount) {
        if (left.getStackSize() != expectedCount) return false;

        return this.matchesCompactingType(left.getDefinition(), right);
    }

    private boolean appendEncodedStacks(NBTTagList target, ItemStack template, long totalCount, int maxEntries) {
        if (template.isEmpty() || totalCount <= 0) return false;

        long remaining = totalCount;
        int entryCount = 0;

        while (remaining > 0) {
            if (entryCount >= maxEntries) return false;

            ItemStack stack = template.copy();
            stack.setCount((int) Math.min(MAX_ENCODED_PATTERN_STACK_SIZE, remaining));
            target.appendTag(stack.writeToNBT(new NBTTagCompound()));
            remaining -= stack.getCount();
            entryCount++;
        }

        return true;
    }

    private long sanitizePatternMultiplier(long multiplier) {
        return Math.max(DEFAULT_PATTERN_MULTIPLIER, Math.min(multiplier, MAX_PATTERN_MULTIPLIER));
    }

    private long getValidatedPatternMultiplier(int slot) {
        this.rebuildPatternMultiplierLimitsIfNeeded();
        return this.getValidatedPatternMultiplierFromCache(slot);
    }

    private long getValidatedPatternMultiplierFromCache(int slot) {
        if (slot < 0 || slot >= FILTER_SLOTS) return DEFAULT_PATTERN_MULTIPLIER;

        return Math.min(this.cachedPatternMultiplierLimits[slot], this.sanitizePatternMultiplier(this.patternMultipliers[slot]));
    }

    // AE2 processing patterns can only encode 16 input stacks and 6 output stacks,
    // each limited to a vanilla ItemStack count. The effective multiplier must stay
    // within those bounds so the encoded pattern remains representable.
    private long getPatternEncodingLimit(long inputPerOperation, long outputPerOperation) {
        long limit = MAX_PATTERN_MULTIPLIER;

        if (inputPerOperation > 0) {
            long inputCapacity = PatternHelper.PROCESSING_INPUT_LIMIT * MAX_ENCODED_PATTERN_STACK_SIZE;
            limit = Math.min(limit, inputCapacity / inputPerOperation);
        }

        if (outputPerOperation > 0) {
            long outputCapacity = PatternHelper.PROCESSING_OUTPUT_LIMIT * MAX_ENCODED_PATTERN_STACK_SIZE;
            limit = Math.min(limit, outputCapacity / outputPerOperation);
        }

        return Math.max(DEFAULT_PATTERN_MULTIPLIER, limit);
    }

    private boolean clampStoredPatternMultiplier(int slot) {
        this.rebuildPatternMultiplierLimitsIfNeeded();
        return this.clampStoredPatternMultiplierFromCache(slot);
    }

    private boolean clampStoredPatternMultiplierFromCache(int slot) {
        if (slot < 0 || slot >= FILTER_SLOTS) return false;

        long validated = this.getValidatedPatternMultiplierFromCache(slot);
        if (this.patternMultipliers[slot] == validated) return false;

        this.patternMultipliers[slot] = validated;
        this.patternsDirty = true;
        this.previewsDirty = true;
        return true;
    }

    private boolean validateAllPatternMultipliers() {
        this.rebuildPatternMultiplierLimitsIfNeeded();
        return this.validateAllPatternMultipliersFromCache();
    }

    private boolean validateAllPatternMultipliersFromCache() {
        boolean changed = false;

        for (int slot = 0; slot < FILTER_SLOTS; slot++) {
            changed |= this.clampStoredPatternMultiplierFromCache(slot);
        }

        return changed;
    }

    private void rebuildPatternMultiplierLimitsIfNeeded() {
        if (!this.multiplierLimitsDirty) return;

        this.multiplierLimitsDirty = false;
        Arrays.fill(this.cachedPatternMultiplierLimits, MAX_PATTERN_MULTIPLIER);

        if (this.world == null) return;

        CompactingHelper helper = new CompactingHelper(this.world);
        for (int slot = 0; slot < FILTER_SLOTS; slot++) {
            IAEItemStack filter = this.cachedFilterStacks[slot];
            if (filter == null) continue;

            this.cachedPatternMultiplierLimits[slot] = this.computePatternMultiplierLimit(helper, filter.getDefinition().copy());
        }

        boolean multipliersChanged = this.validateAllPatternMultipliersFromCache();
        if (!multipliersChanged || this.world.isRemote) return;

        this.markDirty();
    }

    private long computePatternMultiplierLimit(CompactingHelper helper, ItemStack normalizedFilter) {
        long limit = MAX_PATTERN_MULTIPLIER;
        normalizedFilter.setCount(1);

        CompactingHelper.Result higher = helper.findHigherTier(normalizedFilter.copy());
        if (!higher.getStack().isEmpty()) {
            limit = Math.min(limit, this.getPatternEncodingLimit(higher.getConversionRate(), higher.getStack().getCount()));
        }

        CompactingHelper.Result lower = helper.findLowerTier(normalizedFilter.copy());
        if (!lower.getStack().isEmpty()) {
            ItemStack output = this.resolveDownwardPatternOutput(normalizedFilter.copy(), lower);
            if (!output.isEmpty()) {
                limit = Math.min(limit, this.getPatternEncodingLimit(DEFAULT_PATTERN_MULTIPLIER, output.getCount()));
            }
        }

        return Math.max(DEFAULT_PATTERN_MULTIPLIER, limit);
    }

    private void refreshAllFilterCaches() {
        for (int slot = 0; slot < FILTER_SLOTS; slot++) this.refreshFilterCache(slot);
    }

    private void refreshFilterCache(int slot) {
        if (slot < 0 || slot >= FILTER_SLOTS) return;

        ItemStack normalized = this.normalizeFilterStack(this.filterInventory.getStackInSlot(slot));
        if (normalized.isEmpty()) {
            this.cachedFilterStacks[slot] = null;
            return;
        }

        this.cachedFilterStacks[slot] = AEItemStack.fromItemStack(normalized);
    }

    private void cacheCurrentPattern(@Nullable ExposedPattern pattern) {
        if (pattern == null || pattern.signature == null || pattern.cachedOutput == null) return;

        this.cacheValidatedPatternOutput(pattern.signature, pattern.cachedOutput);
    }

    private void cacheValidatedPatternOutput(@Nonnull PatternSignature signature, @Nonnull IAEItemStack output) {
        this.validatedPatternOutputs.put(signature, output.copy());
    }

    @Nullable
    private PatternRequest createPatternRequest(@Nullable ICraftingPatternDetails patternDetails) {
        if (patternDetails == null || patternDetails.isCraftable()) return null;

        IAEItemStack[] inputs = patternDetails.getCondensedInputs();
        IAEItemStack[] outputs = patternDetails.getCondensedOutputs();

        if (inputs.length != 1 || outputs.length != 1) return null;
        if (inputs[0] == null || outputs[0] == null) return null;

        PatternSignature signature = this.createLookupPatternSignature(inputs[0], outputs[0]);
        if (signature == null) return null;

        return new PatternRequest(signature, inputs[0], outputs[0]);
    }

    @Nullable
    private IAEItemStack validateAndCachePattern(@Nonnull PatternRequest patternRequest) {
        if (this.world == null) return null;

        ItemStack normalizedInput = patternRequest.input.getDefinition().copy();
        normalizedInput.setCount(1);

        CompactingHelper helper = new CompactingHelper(this.world);
        CompactingHelper.Result higher = helper.findHigherTier(normalizedInput.copy());
        CompactingHelper.Result lower = helper.findLowerTier(normalizedInput.copy());
        ItemStack downwardOutput = lower.getStack().isEmpty()
            ? ItemStack.EMPTY
            : this.resolveDownwardPatternOutput(normalizedInput.copy(), lower);

        for (int slot = 0; slot < FILTER_SLOTS; slot++) {
            IAEItemStack filter = this.cachedFilterStacks[slot];
            if (filter == null) continue;
            if (!this.matchesCompactingType(filter.getDefinition(), normalizedInput)) continue;

            long multiplier = this.getValidatedPatternMultiplier(slot);

            if (!higher.getStack().isEmpty()) {
                ItemStack expectedOutput = higher.getStack().copy();
                long expectedInputCount = (long) higher.getConversionRate() * multiplier;
                long expectedOutputCount = (long) expectedOutput.getCount() * multiplier;

                if (patternRequest.input.getStackSize() == expectedInputCount
                    && this.samePatternStack(patternRequest.output, expectedOutput, expectedOutputCount)) {
                    return this.cacheValidatedPatternOutput(patternRequest.input, patternRequest.output);
                }
            }

            if (downwardOutput.isEmpty()) continue;

            long expectedInputCount = multiplier;
            long expectedOutputCount = (long) downwardOutput.getCount() * multiplier;
            if (patternRequest.input.getStackSize() != expectedInputCount) continue;
            if (!this.samePatternStack(patternRequest.output, downwardOutput, expectedOutputCount)) continue;

            return this.cacheValidatedPatternOutput(patternRequest.input, patternRequest.output);
        }

        return null;
    }

    @Nullable
    private IAEItemStack cacheValidatedPatternOutput(@Nonnull IAEItemStack input, @Nonnull IAEItemStack output) {
        PatternSignature signature = this.createCachedPatternSignature(input, input.getStackSize(), output,
            output.getStackSize());
        IAEItemStack cachedOutput = output.copy();
        if (signature == null || cachedOutput == null) return null;

        this.cacheValidatedPatternOutput(signature, cachedOutput);
        return cachedOutput;
    }

    @Nullable
    private PatternSignature createCachedPatternSignature(@Nullable IAEItemStack input, long inputCount,
                                                          @Nullable IAEItemStack output, long outputCount) {
        return this.createPatternSignature(input, inputCount, output, outputCount);
    }

    @Nullable
    private PatternSignature createLookupPatternSignature(@Nullable IAEItemStack input, @Nullable IAEItemStack output) {
        if (input == null || output == null) return null;

        return this.createPatternSignature(input, input.getStackSize(), output, output.getStackSize());
    }

    @Nullable
    private PatternSignature createPatternSignature(@Nullable IAEItemStack input, long inputCount,
                                                    @Nullable IAEItemStack output, long outputCount) {
        if (input == null || output == null) return null;
        if (inputCount <= 0 || outputCount <= 0) return null;

        return new PatternSignature(input, inputCount, output, outputCount);
    }

    @Nullable
    private IMEMonitor<IAEItemStack> getItemMonitor() {
        IGridNode node = this.getProxy().getNode();
        if (node == null) return null;

        IGrid grid = node.getGrid();
        IStorageGrid storage = grid.getCache(IStorageGrid.class);

        return storage.getInventory(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
    }

    private void notifyPatternsChanged() {
        if (this.world == null || this.world.isRemote) return;

        IGridNode node = this.getProxy().getNode();
        if (node == null) return;

        IGrid grid = node.getGrid();
        grid.postEvent(new MENetworkCraftingPatternChange(this, node));
    }

    private void updateTickState() {
        IGridNode node = this.getProxy().getNode();
        if (node == null) return;

        IGrid grid = node.getGrid();
        ITickManager tickManager = grid.getCache(ITickManager.class);

        if (this.pendingOutputs.isEmpty()) {
            tickManager.sleepDevice(node);
            return;
        }

        tickManager.wakeDevice(node);
    }

    @Nonnull
    private ItemStack normalizeFilterStack(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;

        ItemStack normalized = stack.copy();
        normalized.setCount(1);
        return normalized;
    }

    public static final class PatternPreview {

        private final ItemStack output;
        private final long inputCount;
        private final long outputCount;
        private final boolean upward;
        private final long multiplier;

        private PatternPreview(ItemStack output, long inputCount, long outputCount, boolean upward, long multiplier) {
            this.output = output;
            this.inputCount = inputCount;
            this.outputCount = outputCount;
            this.upward = upward;
            this.multiplier = multiplier;
        }

        public ItemStack getOutput() {
            return this.output;
        }

        public PatternPreview copy() {
            return create(this.output.copy(), this.inputCount, this.outputCount, this.upward, this.multiplier);
        }

        public static PatternPreview create(ItemStack output, long inputCount, long outputCount,
                                            boolean upward, long multiplier) {
            return new PatternPreview(output, inputCount, outputCount, upward, multiplier);
        }

        public long getInputCount() {
            return this.inputCount;
        }

        public long getOutputCount() {
            return this.outputCount;
        }

        public boolean isUpward() {
            return this.upward;
        }

        public long getMultiplier() {
            return this.multiplier;
        }
    }

    private static final class ExposedPattern {

        @Nullable
        private final ICraftingPatternDetails details;
        @Nullable
        private final PatternSignature signature;
        @Nullable
        private final IAEItemStack cachedOutput;

        private ExposedPattern(@Nullable ICraftingPatternDetails details, @Nullable PatternSignature signature,
                               @Nullable IAEItemStack cachedOutput) {
            this.details = details;
            this.signature = signature;
            this.cachedOutput = cachedOutput;
        }
    }

    private static final class SlotPatterns {

        @Nullable
        private final ExposedPattern upward;
        @Nullable
        private final ExposedPattern downward;

        private SlotPatterns(@Nullable ExposedPattern upward, @Nullable ExposedPattern downward) {
            this.upward = upward;
            this.downward = downward;
        }
    }

    private static final class PatternRequest {

        private final PatternSignature signature;
        private final IAEItemStack input;
        private final IAEItemStack output;

        private PatternRequest(PatternSignature signature, IAEItemStack input, IAEItemStack output) {
            this.signature = signature;
            this.input = input;
            this.output = output;
        }
    }

    private static final class PatternSignature {

        private final IAEItemStack input;
        private final long inputCount;
        private final IAEItemStack output;
        private final long outputCount;
        private final int hashCode;

        private PatternSignature(IAEItemStack input, long inputCount, IAEItemStack output, long outputCount) {
            this.input = input;
            this.inputCount = inputCount;
            this.output = output;
            this.outputCount = outputCount;
            this.hashCode = Objects.hash(this.input, this.inputCount, this.output, this.outputCount);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof PatternSignature)) return false;

            PatternSignature other = (PatternSignature) obj;
            return this.inputCount == other.inputCount
                && this.outputCount == other.outputCount
                && this.input.equals(other.input)
                && this.output.equals(other.output);
        }

        @Override
        public int hashCode() {
            return this.hashCode;
        }
    }
}