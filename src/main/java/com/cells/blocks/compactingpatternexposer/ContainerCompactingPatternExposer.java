package com.cells.blocks.compactingpatternexposer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.container.AEBaseContainer;
import appeng.util.Platform;

import com.cells.gui.QuickAddHelper;
import com.cells.gui.overlay.ServerMessageHelper;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.sync.ICompactingPatternPreviewSyncContainer;
import com.cells.network.sync.ICompactingPatternMultiplierSyncContainer;
import com.cells.network.sync.PacketCompactingPatternPreview;
import com.cells.network.sync.PacketCompactingPatternMultiplier;
import com.cells.network.sync.IQuickAddFilterContainer;
import com.cells.network.sync.IResourceSyncContainer;
import com.cells.network.sync.PacketResourceSlot;
import com.cells.network.sync.ResourceType;


/**
 * Container for the Compacting Pattern Exposer GUI.
 * <p>
 * Filter slots are GUI-only ghost widgets synced via {@link PacketResourceSlot}.
 * The container itself only hosts the player inventory.
 */
public class ContainerCompactingPatternExposer extends AEBaseContainer
    implements ICompactingPatternMultiplierSyncContainer, ICompactingPatternPreviewSyncContainer,
    IQuickAddFilterContainer, IResourceSyncContainer {

    private static final String FILTER_CONFLICT_MESSAGE = "message.cells.compacting_pattern_exposer.filter_conflict";

    private final TileCompactingPatternExposer tile;
    private final Map<Integer, IAEItemStack> serverFilterCache = new HashMap<>();
    private final Map<Integer, Long> serverMultiplierCache = new HashMap<>();
    private final TileCompactingPatternExposer.PatternPreview[] serverUpPreviewCache =
        new TileCompactingPatternExposer.PatternPreview[TileCompactingPatternExposer.FILTER_SLOTS];
    private final TileCompactingPatternExposer.PatternPreview[] serverDownPreviewCache =
        new TileCompactingPatternExposer.PatternPreview[TileCompactingPatternExposer.FILTER_SLOTS];
    private final long[] clientMultipliers = new long[TileCompactingPatternExposer.FILTER_SLOTS];
    private final TileCompactingPatternExposer.PatternPreview[] clientUpPreviews =
        new TileCompactingPatternExposer.PatternPreview[TileCompactingPatternExposer.FILTER_SLOTS];
    private final TileCompactingPatternExposer.PatternPreview[] clientDownPreviews =
        new TileCompactingPatternExposer.PatternPreview[TileCompactingPatternExposer.FILTER_SLOTS];
    @Nullable
    private String quickAddFailureMessageKey;

    public ContainerCompactingPatternExposer(InventoryPlayer playerInv, TileCompactingPatternExposer tile) {
        super(playerInv, tile, null);
        this.tile = tile;
        Arrays.fill(this.clientMultipliers, 1L);
        this.bindPlayerInventory(playerInv, 0, 156);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        if (!Platform.isServer()) return;

        Map<Integer, TileCompactingPatternExposer.PatternPreview> changedUpPreviews = new HashMap<>();
        Map<Integer, TileCompactingPatternExposer.PatternPreview> changedDownPreviews = new HashMap<>();

        for (int slot = 0; slot < TileCompactingPatternExposer.FILTER_SLOTS; slot++) {
            IAEItemStack current = this.tile.getFilter(slot);
            IAEItemStack cached = this.serverFilterCache.get(slot);

            if (!this.sameFilter(current, cached)) {
                this.serverFilterCache.put(slot, current != null ? current.copy() : null);

                for (IContainerListener listener : this.listeners) {
                    if (!(listener instanceof EntityPlayerMP)) continue;

                    CellsNetworkHandler.INSTANCE.sendTo(
                        new PacketResourceSlot(ResourceType.ITEM, slot, current),
                        (EntityPlayerMP) listener
                    );
                }
            }

            long currentMultiplier = this.tile.getPatternMultiplier(slot);
            Long cachedMultiplier = this.serverMultiplierCache.get(slot);
            if (cachedMultiplier == null || cachedMultiplier != currentMultiplier) {
                this.serverMultiplierCache.put(slot, currentMultiplier);

                for (IContainerListener listener : this.listeners) {
                    if (!(listener instanceof EntityPlayerMP)) continue;

                    CellsNetworkHandler.INSTANCE.sendTo(
                        new PacketCompactingPatternMultiplier(slot, currentMultiplier),
                        (EntityPlayerMP) listener
                    );
                }
            }

            TileCompactingPatternExposer.PatternPreview upPreview = this.tile.getUpPreview(slot);
            if (!this.samePreview(this.serverUpPreviewCache[slot], upPreview)) {
                this.serverUpPreviewCache[slot] = this.copyPreview(upPreview);
                changedUpPreviews.put(slot, this.copyPreview(upPreview));
            }

            TileCompactingPatternExposer.PatternPreview downPreview = this.tile.getDownPreview(slot);
            if (!this.samePreview(this.serverDownPreviewCache[slot], downPreview)) {
                this.serverDownPreviewCache[slot] = this.copyPreview(downPreview);
                changedDownPreviews.put(slot, this.copyPreview(downPreview));
            }
        }

        if (changedUpPreviews.isEmpty() && changedDownPreviews.isEmpty()) return;

        for (IContainerListener listener : this.listeners) {
            if (!(listener instanceof EntityPlayerMP)) continue;

            CellsNetworkHandler.INSTANCE.sendTo(
                new PacketCompactingPatternPreview(changedUpPreviews, changedDownPreviews),
                (EntityPlayerMP) listener
            );
        }
    }

    @Override
    public void addListener(@Nonnull IContainerListener listener) {
        super.addListener(listener);

        if (!Platform.isServer() || !(listener instanceof EntityPlayerMP)) return;

        Map<Integer, Object> fullMap = new HashMap<>();
        Map<Integer, Long> multiplierMap = new HashMap<>();
        Map<Integer, TileCompactingPatternExposer.PatternPreview> upPreviewMap = new HashMap<>();
        Map<Integer, TileCompactingPatternExposer.PatternPreview> downPreviewMap = new HashMap<>();
        for (int slot = 0; slot < TileCompactingPatternExposer.FILTER_SLOTS; slot++) {
            IAEItemStack current = this.tile.getFilter(slot);
            fullMap.put(slot, current);
            this.serverFilterCache.put(slot, current != null ? current.copy() : null);

            long multiplier = this.tile.getPatternMultiplier(slot);
            multiplierMap.put(slot, multiplier);
            this.serverMultiplierCache.put(slot, multiplier);

            TileCompactingPatternExposer.PatternPreview upPreview = this.tile.getUpPreview(slot);
            this.serverUpPreviewCache[slot] = this.copyPreview(upPreview);
            upPreviewMap.put(slot, this.copyPreview(upPreview));

            TileCompactingPatternExposer.PatternPreview downPreview = this.tile.getDownPreview(slot);
            this.serverDownPreviewCache[slot] = this.copyPreview(downPreview);
            downPreviewMap.put(slot, this.copyPreview(downPreview));
        }

        CellsNetworkHandler.INSTANCE.sendTo(
            new PacketResourceSlot(ResourceType.ITEM, fullMap),
            (EntityPlayerMP) listener
        );

        CellsNetworkHandler.INSTANCE.sendTo(
            new PacketCompactingPatternMultiplier(multiplierMap),
            (EntityPlayerMP) listener
        );

        CellsNetworkHandler.INSTANCE.sendTo(
            new PacketCompactingPatternPreview(upPreviewMap, downPreviewMap),
            (EntityPlayerMP) listener
        );
    }

    @Override
    public void receiveResourceSlots(ResourceType type, Map<Integer, Object> resources) {
        if (type != ResourceType.ITEM) return;

        for (Map.Entry<Integer, Object> entry : resources.entrySet()) {
            int slot = entry.getKey();
            if (slot < 0 || slot >= TileCompactingPatternExposer.FILTER_SLOTS) continue;

            IAEItemStack resource = (IAEItemStack) entry.getValue();
            ItemStack stack = resource != null ? resource.createItemStack() : ItemStack.EMPTY;

            if (Platform.isServer()) {
                if (!stack.isEmpty() && this.tile.containsFilterStack(stack, slot)) {
                    EntityPlayer player = this.getInventoryPlayer().player;
                    if (player instanceof EntityPlayerMP) {
                        ServerMessageHelper.warning((EntityPlayerMP) player, "message.cells.filter_duplicate");
                    }
                    continue;
                }

                if (!stack.isEmpty() && this.hasFilterConflict(slot, stack)) {
                    this.reportFilterConflict(this.getInventoryPlayer().player);
                    continue;
                }
            }

            this.tile.setFilterStack(slot, stack);
        }
    }

    public void receivePatternMultipliers(Map<Integer, Long> multipliers) {
        for (Map.Entry<Integer, Long> entry : multipliers.entrySet()) {
            int slot = entry.getKey();
            if (slot < 0 || slot >= TileCompactingPatternExposer.FILTER_SLOTS) continue;

            long multiplier = entry.getValue() == null
                ? 1L
                : entry.getValue();

            if (Platform.isServer()) {
                this.tile.setPatternMultiplier(slot, multiplier);
                continue;
            }

            this.clientMultipliers[slot] = multiplier;
        }
    }

    @Override
    public void receivePatternPreviews(Map<Integer, TileCompactingPatternExposer.PatternPreview> upwardPreviews,
                                       Map<Integer, TileCompactingPatternExposer.PatternPreview> downwardPreviews) {
        for (Map.Entry<Integer, TileCompactingPatternExposer.PatternPreview> entry : upwardPreviews.entrySet()) {
            int slot = entry.getKey();
            if (slot < 0 || slot >= TileCompactingPatternExposer.FILTER_SLOTS) continue;

            this.clientUpPreviews[slot] = this.copyPreview(entry.getValue());
        }

        for (Map.Entry<Integer, TileCompactingPatternExposer.PatternPreview> entry : downwardPreviews.entrySet()) {
            int slot = entry.getKey();
            if (slot < 0 || slot >= TileCompactingPatternExposer.FILTER_SLOTS) continue;

            this.clientDownPreviews[slot] = this.copyPreview(entry.getValue());
        }
    }

    @Override
    public ResourceType getQuickAddResourceType() {
        return ResourceType.ITEM;
    }

    @Override
    public boolean isResourceInFilter(@Nonnull Object resource) {
        if (!(resource instanceof IAEItemStack)) return false;

        return this.tile.containsFilterStack(((IAEItemStack) resource).getDefinition(), -1);
    }

    @Override
    public boolean quickAddToFilter(@Nonnull Object resource, @Nullable EntityPlayer player) {
        this.quickAddFailureMessageKey = null;

        if (!(resource instanceof IAEItemStack)) return false;

        ItemStack stack = ((IAEItemStack) resource).getDefinition();
        if (stack.isEmpty()) return false;
        if (this.tile.containsFilterStack(stack, -1)) return false;

        int slot = this.tile.findFirstEmptyFilterSlot();
        if (slot < 0) return false;

        if (this.hasFilterConflict(slot, stack)) {
            this.quickAddFailureMessageKey = FILTER_CONFLICT_MESSAGE;
            return false;
        }

        this.tile.setFilterStack(slot, stack);
        return true;
    }

    @Override
    @Nullable
    public String consumeQuickAddFailureMessageKey() {
        String failureMessageKey = this.quickAddFailureMessageKey;
        this.quickAddFailureMessageKey = null;
        return failureMessageKey;
    }

    @Override
    public String getTypeLocalizationKey() {
        return "cells.type.item";
    }

    @Override
    @Nonnull
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= this.inventorySlots.size()) return ItemStack.EMPTY;

        Slot slot = this.inventorySlots.get(slotIndex);
        if (slot == null || !slot.getHasStack()) return ItemStack.EMPTY;

        ItemStack filterStack = QuickAddHelper.getItemFromItemStack(slot.getStack());
        if (filterStack.isEmpty()) {
            if (Platform.isServer() && player instanceof EntityPlayerMP) {
                ServerMessageHelper.error((EntityPlayerMP) player,
                    "message.cells.creative_cell.not_valid_content", this.getTypeLocalizationKey());
            }
            return ItemStack.EMPTY;
        }

        IAEItemStack aeStack = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class).createStack(filterStack);
        if (aeStack == null) return ItemStack.EMPTY;

        if (this.tile.containsFilterStack(filterStack, -1)) {
            if (Platform.isServer() && player instanceof EntityPlayerMP) {
                ServerMessageHelper.warning((EntityPlayerMP) player, "message.cells.filter_duplicate");
            }
            return ItemStack.EMPTY;
        }

        if (!this.quickAddToFilter(aeStack, player)) {
            if (Platform.isServer() && player instanceof EntityPlayerMP) {
                String failureMessageKey = this.consumeQuickAddFailureMessageKey();
                if (failureMessageKey != null) {
                    ServerMessageHelper.error((EntityPlayerMP) player, failureMessageKey);
                } else {
                    ServerMessageHelper.error((EntityPlayerMP) player, "message.cells.no_filter_space");
                }
            }
        }

        return ItemStack.EMPTY;
    }

    @Nullable
    public IAEItemStack getFilter(int slot) {
        return this.tile.getFilter(slot);
    }

    @Nullable
    public TileCompactingPatternExposer.PatternPreview getUpPreview(int slot) {
        if (!Platform.isServer()) return this.copyPreview(this.clientUpPreviews[slot]);

        return this.tile.getUpPreview(slot);
    }

    @Nullable
    public TileCompactingPatternExposer.PatternPreview getDownPreview(int slot) {
        if (!Platform.isServer()) return this.copyPreview(this.clientDownPreviews[slot]);

        return this.tile.getDownPreview(slot);
    }

    public long getPatternMultiplier(int slot) {
        if (!Platform.isServer()) return this.clientMultipliers[slot];

        return this.tile.getPatternMultiplier(slot);
    }

    public long getPatternMultiplierLimit(int slot) {
        return this.tile.getPatternMultiplierLimit(slot);
    }

    public void applyClientPatternMultiplier(int slot, long multiplier) {
        if (slot < 0 || slot >= TileCompactingPatternExposer.FILTER_SLOTS) return;

        this.clientMultipliers[slot] = multiplier;
    }

    private boolean sameFilter(@Nullable IAEItemStack left, @Nullable IAEItemStack right) {
        if (left == null) return right == null;
        if (right == null) return false;

        return left.equals(right);
    }

    private boolean hasFilterConflict(int slot, ItemStack stack) {
        return !this.tile.findConflictingPatternOutput(slot, stack).isEmpty();
    }

    private boolean samePreview(@Nullable TileCompactingPatternExposer.PatternPreview left,
                                @Nullable TileCompactingPatternExposer.PatternPreview right) {
        if (left == null || right == null) return left == right;
        if (left.getInputCount() != right.getInputCount()) return false;
        if (left.getOutputCount() != right.getOutputCount()) return false;
        if (left.getMultiplier() != right.getMultiplier()) return false;
        if (left.isUpward() != right.isUpward()) return false;

        return ItemStack.areItemStacksEqual(left.getOutput(), right.getOutput())
            && ItemStack.areItemStackTagsEqual(left.getOutput(), right.getOutput());
    }

    @Nullable
    private TileCompactingPatternExposer.PatternPreview copyPreview(@Nullable TileCompactingPatternExposer.PatternPreview preview) {
        return preview == null ? null : preview.copy();
    }

    private void reportFilterConflict(@Nullable EntityPlayer player) {
        if (!Platform.isServer() || !(player instanceof EntityPlayerMP)) return;

        ServerMessageHelper.error((EntityPlayerMP) player, FILTER_CONFLICT_MESSAGE);
    }
}