package com.cells.cells.creative;

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
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentTranslation;

import appeng.util.Platform;

import com.cells.network.CellsNetworkHandler;
import com.cells.network.sync.IQuickAddFilterContainer;
import com.cells.network.sync.IResourceSyncContainer;
import com.cells.network.sync.PacketResourceSlot;
import com.cells.network.sync.ResourceType;


/**
 * Abstract sync container for Creative Cell GUIs.
 * <p>
 * Handles unified resource sync via {@link PacketResourceSlot} for all resource types.
 * Subclasses must implement type-specific methods for stack equality and conversion.
 * <p>
 * The type parameter S represents the "sync" type that gets sent over the network.
 * For fluids this is IAEFluidStack, for gases IAEGasStack, for items/essentia the raw type.
 *
 * @param <H> The filter handler type
 * @param <S> The stack type used for sync (what gets sent in packets)
 */
public abstract class AbstractCreativeCellSyncContainer<H extends AbstractCreativeCellFilterHandler<?, ?>, S>
        extends AbstractCreativeCellContainer<H>
        implements IResourceSyncContainer, IQuickAddFilterContainer {

    /** Server-side cache for filter sync (tracks what was sent to clients). */
    protected final Map<Integer, S> serverFilterCache = new HashMap<>();

    protected AbstractCreativeCellSyncContainer(InventoryPlayer playerInv, EnumHand hand, H filterHandler) {
        super(playerInv, hand, filterHandler);
    }

    // ================================= Abstract Methods =================================

    /**
     * @return The resource type for network sync.
     */
    protected abstract ResourceType getResourceType();

    /**
     * Get the sync stack from the filter handler at a specific slot.
     * This should convert from raw type to sync type if needed.
     *
     * @param slot The slot index
     * @return The sync stack, or null if empty
     */
    @Nullable
    protected abstract S getSyncStack(int slot);

    /**
     * Set the stack in the filter handler at a specific slot.
     * This should convert from sync type to raw type if needed.
     *
     * @param slot  The slot index
     * @param stack The sync stack to set, or null to clear
     */
    protected abstract void setSyncStack(int slot, @Nullable S stack);

    /**
     * Copy a sync stack for caching.
     *
     * @param stack The stack to copy
     * @return A copy of the stack, or null if the input is null
     */
    @Nullable
    protected abstract S copySyncStack(@Nullable S stack);

    /**
     * Check if two sync stacks are equal for comparison.
     */
    protected abstract boolean syncStacksEqual(@Nullable S a, @Nullable S b);

    /**
     * Check if a sync stack is "empty" (null or empty equivalent).
     */
    protected abstract boolean isSyncStackEmpty(@Nullable S stack);

    /**
     * Check if the filter handler contains this stack.
     */
    protected abstract boolean filterContains(@Nonnull S stack);

    /**
     * Extract a resource from an ItemStack container (for shift-click).
     * For fluids, extracts via FluidUtil. For gases/essentia, uses QuickAddHelper.
     * For items, just returns the ItemStack itself (cast to S).
     *
     * @param container The ItemStack that might contain a resource
     * @return The extracted resource, or null if no resource found
     */
    @Nullable
    protected abstract S extractResourceFromItemStack(@Nonnull ItemStack container);

    // ================================= IQuickAddFilterContainer =================================

    @Override
    public ResourceType getQuickAddResourceType() {
        return getResourceType();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean isResourceInFilter(@Nonnull Object resource) {
        return filterContains((S) resource);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean quickAddToFilter(@Nonnull Object resource, @Nullable EntityPlayer player) {
        S stack = (S) resource;
        if (isSyncStackEmpty(stack)) return false;

        // Check for duplicates
        if (filterContains(stack)) return false;

        // Find first empty slot
        for (int i = 0; i < FILTER_SLOTS; i++) {
            if (isSyncStackEmpty(getSyncStack(i))) {
                setSyncStack(i, stack);
                return true;
            }
        }

        return false;
    }

    @Override
    public String getTypeLocalizationKey() {
        return "cells.type." + getResourceType().name().toLowerCase();
    }

    // ================================= Common Filter Operations =================================

    public void clearAllFilters() {
        filterHandler.clearAll();
    }

    /**
     * Handle shift-click: extract resource from container and add as filter.
     * The actual item stays in place (return empty), only the filter is set.
     * <p>
     * Note: This runs on BOTH client and server in singleplayer, so feedback
     * messages are only sent server-side to avoid duplicates.
     */
    @Override
    @Nonnull
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= inventorySlots.size()) return ItemStack.EMPTY;

        Slot slot = inventorySlots.get(slotIndex);
        if (slot == null || !slot.getHasStack()) return ItemStack.EMPTY;

        ItemStack clickedStack = slot.getStack();
        S resource = extractResourceFromItemStack(clickedStack);

        if (resource == null) {
            // Invalid type - send feedback (server-side only to avoid duplicates)
            if (Platform.isServer()) {
                player.sendMessage(new TextComponentTranslation(
                    "message.cells.creative_cell.not_valid_content",
                    new TextComponentTranslation(getTypeLocalizationKey())
                ));
            }
            return ItemStack.EMPTY;
        }

        // Check for duplicates before adding (server-side only to avoid duplicates)
        if (filterContains(resource)) {
            if (Platform.isServer()) {
                player.sendMessage(new TextComponentTranslation("message.cells.filter_duplicate"));
            }
            return ItemStack.EMPTY;
        }

        if (!quickAddToFilter(resource, player)) {
            // No space - send feedback (server-side only to avoid duplicates)
            if (Platform.isServer()) {
                player.sendMessage(new TextComponentTranslation("message.cells.no_filter_space"));
            }
        }

        // Return empty so the actual item stays in place
        return ItemStack.EMPTY;
    }

    // ================================= Sync Implementation =================================

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        if (!Platform.isServer()) return;

        final ResourceType type = getResourceType();

        for (int i = 0; i < FILTER_SLOTS; i++) {
            S current = getSyncStack(i);
            S cached = serverFilterCache.get(i);

            if (!syncStacksEqual(current, cached)) {
                serverFilterCache.put(i, copySyncStack(current));

                // Send diff to all listeners
                for (IContainerListener listener : this.listeners) {
                    if (listener instanceof EntityPlayerMP) {
                        CellsNetworkHandler.INSTANCE.sendTo(
                            new PacketResourceSlot(type, i, current),
                            (EntityPlayerMP) listener
                        );
                    }
                }
            }
        }
    }

    @Override
    public void addListener(@Nonnull IContainerListener listener) {
        super.addListener(listener);

        if (!Platform.isServer() || !(listener instanceof EntityPlayerMP)) return;

        final ResourceType type = getResourceType();
        Map<Integer, Object> fullMap = new HashMap<>();

        for (int i = 0; i < FILTER_SLOTS; i++) {
            S stack = getSyncStack(i);
            fullMap.put(i, stack);
            serverFilterCache.put(i, copySyncStack(stack));
        }

        CellsNetworkHandler.INSTANCE.sendTo(
            new PacketResourceSlot(type, fullMap),
            (EntityPlayerMP) listener
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public void receiveResourceSlots(ResourceType type, Map<Integer, Object> resources) {
        if (type != getResourceType()) return;

        for (Map.Entry<Integer, Object> entry : resources.entrySet()) {
            int slot = entry.getKey();
            if (slot < 0 || slot >= FILTER_SLOTS) continue;

            S newStack = (S) entry.getValue();

            // Check for duplicates when adding a new filter (server-side only)
            // Clearing a slot (null) should always be allowed
            if (newStack != null && !isSyncStackEmpty(newStack)) {
                // Check if this resource already exists in another slot
                if (filterContains(newStack)) {
                    // Send duplicate feedback to player (server-side only)
                    if (Platform.isServer()) {
                        EntityPlayer player = this.getInventoryPlayer().player;
                        player.sendMessage(new TextComponentTranslation("message.cells.filter_duplicate"));
                    }
                    continue;
                }
            }

            setSyncStack(slot, newStack);
        }
    }
}
