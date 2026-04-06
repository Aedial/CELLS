package com.cells.integration.thaumicenergistics;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import appeng.api.parts.IPart;

import net.minecraft.util.ResourceLocation;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumcraft.api.items.ItemsTC;

import thaumicenergistics.api.EssentiaStack;
import thaumicenergistics.api.ThEApi;
import thaumicenergistics.api.storage.IAEEssentiaStack;
import thaumicenergistics.api.storage.IEssentiaStorageChannel;

import appeng.api.AEApi;

import com.cells.blocks.interfacebase.AbstractContainerInterface;
import com.cells.gui.QuickAddHelper;
import com.cells.network.sync.ResourceType;


/**
 * Container for Essentia Import/Export Interface GUIs.
 * <p>
 * Extends {@link AbstractContainerInterface} with essentia-specific implementations.
 * Most logic is inherited from the abstract base class.
 */
public class ContainerEssentiaInterface
    extends AbstractContainerInterface<IAEEssentiaStack, EssentiaStackKey, IEssentiaInterfaceHost> {

    /**
     * Constructor for tile entity hosts.
     */
    public ContainerEssentiaInterface(final InventoryPlayer ip, final TileEntity tile) {
        this(ip, (IEssentiaInterfaceHost) tile, tile);
    }

    /**
     * Constructor for part hosts.
     */
    public ContainerEssentiaInterface(final InventoryPlayer ip, final IPart part) {
        this(ip, (IEssentiaInterfaceHost) part, part);
    }

    /**
     * Common constructor.
     */
    private ContainerEssentiaInterface(final InventoryPlayer ip, final IEssentiaInterfaceHost host, final Object anchor) {
        super(ip, host, anchor, EssentiaInterfaceLogic.DEFAULT_MAX_SLOT_SIZE);
    }

    // ================================= Abstract Implementations =================================

    @Override
    protected ResourceType getResourceType() {
        return ResourceType.ESSENTIA;
    }

    @Override
    @Nullable
    protected EssentiaStackKey createKey(@Nullable IAEEssentiaStack stack) {
        if (stack == null) return null;
        return EssentiaStackKey.of(stack.getStack());
    }

    @Override
    @Nullable
    protected IAEEssentiaStack getFilter(int slot) {
        return this.host.getFilter(slot);
    }

    @Override
    protected void setFilter(int slot, @Nullable IAEEssentiaStack stack) {
        this.host.setFilter(slot, stack);
    }

    @Override
    protected boolean isStorageEmpty(int slot) {
        return this.host.isStorageEmpty(slot);
    }

    @Override
    protected boolean keysEqual(@Nonnull EssentiaStackKey a, @Nonnull EssentiaStackKey b) {
        return a.equals(b);
    }

    @Override
    @Nullable
    protected IAEEssentiaStack extractFilterFromContainer(ItemStack container) {
        EssentiaStack essentia = QuickAddHelper.getEssentiaFromItemStack(container);
        if (essentia == null || essentia.getAmount() <= 0) return null;

        // Use the API channel's createStack() instead of internal AEEssentiaStack.fromEssentiaStack()
        IEssentiaStorageChannel channel = AEApi.instance().storage().getStorageChannel(IEssentiaStorageChannel.class);
        return channel.createStack(essentia);
    }

    @Override
    @Nonnull
    protected IAEEssentiaStack createFilterStack(@Nonnull IAEEssentiaStack raw) {
        // Already an AE stack, just ensure it has count 1
        IAEEssentiaStack copy = raw.copy();
        copy.setStackSize(1);
        return copy;
    }

    @Override
    @Nullable
    protected IAEEssentiaStack copyFilter(@Nullable IAEEssentiaStack filter) {
        return filter == null ? null : filter.copy();
    }

    @Override
    protected boolean filtersEqual(@Nullable IAEEssentiaStack a, @Nullable IAEEssentiaStack b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    // ================================= Upgrade Slot Change Handler =================================

    @Override
    public void onSlotChange(final Slot s) {
        super.onSlotChange(s);
        if (s instanceof SlotUpgrade) this.host.refreshUpgrades();
    }

    // ================================= Essentia Pouring (Import Mode) =================================

    /**
     * Handle pouring essentia from a held item into a storage slot.
     * <p>
     * Only supports IEssentiaContainerItem (phials, jars). Generic essentia items like
     * vis crystals require burning and should NOT be directly poured. The QuickAddHelper
     * methods are for filter quick-add, not for pouring.
     */
    @Override
    protected boolean handleEmptyItemAction(EntityPlayerMP player, int slotIndex) {
        ItemStack held = player.inventory.getItemStack();
        if (held.isEmpty()) return false;

        // Block vis crystals - they have IEssentiaContainerItem but require burning in a smeltery,
        // they cannot be poured directly into essentia interfaces
        if (held.getItem() == ItemsTC.crystalEssence) return false;

        // Only allow IEssentiaContainerItem (phials, jars, etc.)
        // Vis crystals and other items require burning or special processing
        if (held.getItem() instanceof IEssentiaContainerItem) {
            return handleEssentiaContainerPouring(player, slotIndex, held);
        }

        return false;
    }

    /**
     * Handle pouring from an IEssentiaContainerItem (phials, jars).
     * <p>
     * Handles stacked items properly - only processes one item from the stack at a time.
     * <ul>
     *   <li>Phials: Must be emptied all-at-once (10 essentia). If slot can't hold 10, fail.</li>
     *   <li>Jars: Support partial draining via setAspects().</li>
     * </ul>
     * <p>
     * When the held stack has multiple items (stackCount > 1), we split one off, process it,
     * and return the empty container to the player's inventory (or held slot if only 1 item).
     */
    private boolean handleEssentiaContainerPouring(EntityPlayerMP player, int slotIndex, ItemStack held) {
        IEssentiaContainerItem containerItem = (IEssentiaContainerItem) held.getItem();

        // Get essentia from the container
        AspectList aspects = containerItem.getAspects(held);
        if (aspects == null || aspects.size() == 0) return false;

        // Get the first aspect (containers typically hold one type)
        Aspect[] aspectArray = aspects.getAspects();
        if (aspectArray == null || aspectArray.length == 0) return false;

        Aspect aspect = aspectArray[0];
        int amount = aspects.getAmount(aspect);
        if (aspect == null || amount <= 0) return false;

        // Check if the slot has a filter set
        IAEEssentiaStack filterEssentia = this.host.getFilter(slotIndex);
        if (filterEssentia != null && filterEssentia.getStack().getAspect() != aspect) return false;

        // Calculate how much we can insert into the slot
        int capacity = (int) Math.min(this.host.getMaxSlotSize(), Integer.MAX_VALUE);
        EssentiaStack currentSlotEssentia = this.host.getEssentiaInSlot(slotIndex);

        // If slot has essentia, it must match
        if (currentSlotEssentia != null && currentSlotEssentia.getAspect() != aspect) return false;

        int currentAmount = currentSlotEssentia != null ? currentSlotEssentia.getAmount() : 0;
        int spaceAvailable = capacity - currentAmount;
        if (spaceAvailable <= 0) return false;

        // Determine if this is a "fixed-amount" container like a phial (must transfer all or nothing)
        // vs a jar (can partially drain)
        // Phials are identified by registry name containing "phial" or fixed capacity of exactly 10
        boolean isPhial = held.getItem().getRegistryName() != null &&
            held.getItem().getRegistryName().getPath().contains("phial");

        int toTransfer;
        if (isPhial) {
            // Phials must be emptied completely - if we can't fit all, fail
            if (spaceAvailable < amount) return false;
            toTransfer = amount;
        } else {
            // Jars can be partially drained
            toTransfer = Math.min(amount, spaceAvailable);
        }

        // Handle stack splitting: if we have multiple items, process only one
        if (held.getCount() > 1) {
            // Create empty container - explicitly clear NBT and damage to ensure proper stacking
            ItemStack emptyContainer = new ItemStack(held.getItem(), 1);
            // Don't copy NBT - we want a clean empty container that will stack properly

            // Try to add empty container to player inventory (drop on the floor if full)
            if (!player.inventory.addItemStackToInventory(emptyContainer)) {
                player.dropItem(emptyContainer, false);
            }

            // Decrement the held stack
            held.shrink(1);
            player.inventory.setItemStack(held);
        } else {
            // Clear the held item's NBT completely to make it an empty container
            held.setTagCompound(null);
            held.setItemDamage(0);
        }

        // Insert into slot (clamped to avoid overflow)
        int newAmount = Math.min(currentAmount + toTransfer, capacity);
        EssentiaStack newEssentia = new EssentiaStack(aspect, newAmount);
        this.host.setEssentiaInSlot(slotIndex, newEssentia);

        // Sync the held item change to the client
        this.updateHeld(player);
        this.detectAndSendChanges();
        return true;
    }

    // ================================= Essentia Extraction (Export Only) =================================

    /**
     * Handle filling held item from slot (draining essentia from export interface).
     * <p>
     * Only supports IEssentiaContainerItem (phials, jars). The container must be empty
     * to receive essentia.
     */
    @Override
    protected boolean handleFillItemAction(EntityPlayerMP player, int slotIndex) {
        ItemStack held = player.inventory.getItemStack();
        if (held.isEmpty()) return false;

        // Block vis crystals - they cannot be filled
        if (held.getItem() == ItemsTC.crystalEssence) return false;

        // Only allow IEssentiaContainerItem (phials, jars, etc.)
        if (!(held.getItem() instanceof IEssentiaContainerItem)) return false;

        IEssentiaContainerItem containerItem = (IEssentiaContainerItem) held.getItem();

        // Check if container is empty (can receive essentia)
        AspectList existingAspects = containerItem.getAspects(held);
        if (existingAspects != null && existingAspects.size() > 0) return false;

        // Get container capacity from ThaumicEnergistics config
        ResourceLocation registryName = held.getItem().getRegistryName();
        if (registryName == null) return false;

        int containerCapacity = ThEApi.instance().config().essentiaContainerCapacity()
            .getOrDefault(registryName + ":" + held.getMetadata(), 0);
        if (containerCapacity <= 0) {
            // Try without metadata
            containerCapacity = ThEApi.instance().config().essentiaContainerCapacity()
                .getOrDefault(registryName.toString(), 0);
        }
        if (containerCapacity <= 0) return false;

        // Check if slot has essentia
        EssentiaStack slotEssentia = this.host.getEssentiaInSlot(slotIndex);
        if (slotEssentia == null || slotEssentia.getAmount() <= 0) return false;

        Aspect aspect = slotEssentia.getAspect();
        if (aspect == null) return false;

        // Determine if this is a "fixed-amount" container like a phial (must fill completely)
        boolean isPhial = registryName.getPath().contains("phial");
        int toTransfer;

        if (isPhial) {
            // Phials must be filled completely - if we don't have enough, fail
            if (slotEssentia.getAmount() < containerCapacity) return false;
            toTransfer = containerCapacity;
        } else {
            // Jars can be partially filled
            toTransfer = Math.min(slotEssentia.getAmount(), containerCapacity);
        }

        // Drain from slot
        EssentiaStack drained = this.host.drainEssentiaFromSlot(slotIndex, toTransfer, true);
        if (drained == null || drained.getAmount() <= 0) return false;

        // Handle stack splitting: if we have multiple items, process only one
        if (held.getCount() > 1) {
            // Create filled container
            ItemStack filledContainer = new ItemStack(held.getItem(), 1);
            IEssentiaContainerItem filledItem = (IEssentiaContainerItem) filledContainer.getItem();
            filledItem.setAspects(filledContainer, new AspectList().add(aspect, drained.getAmount()));

            // Set metadata: phials use damage 1 when filled, jars use damage 0
            // This is required for Thaumcraft to recognize the container as filled
            if (isPhial) filledContainer.setItemDamage(1);

            // Try to add filled container to player inventory (drop on the floor if full)
            if (!player.inventory.addItemStackToInventory(filledContainer)) {
                player.dropItem(filledContainer, false);
            }

            // Decrement the held stack
            held.shrink(1);
            player.inventory.setItemStack(held);
        } else {
            // Fill the held item directly
            containerItem.setAspects(held, new AspectList().add(aspect, drained.getAmount()));
            if (isPhial) held.setItemDamage(1);
        }

        // Sync the held item change to the client
        this.updateHeld(player);
        this.detectAndSendChanges();
        return true;
    }

    // ================================= Client Filter Access =================================

    /**
     * Get the client-side filter essentia stack for a visual slot.
     * Used by EssentiaFilterSlot for display.
     */
    @Nullable
    public IAEEssentiaStack getClientFilterEssentia(int displaySlot) {
        int actualSlot = displaySlot + (this.currentPage * EssentiaInterfaceLogic.SLOTS_PER_PAGE);
        return this.host.getFilter(actualSlot);
    }
}
