package com.cells.integration.thaumicenergistics;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumcraft.api.items.ItemsTC;

import thaumicenergistics.api.EssentiaStack;
import thaumicenergistics.api.ThEApi;
import thaumicenergistics.api.storage.IAEEssentiaStack;

import com.cells.blocks.interfacebase.IInterfaceLogic;
import com.cells.blocks.iointerface.IIOInterfaceHost;
import com.cells.gui.QuickAddHelper;
import com.cells.items.ItemRecoveryContainer;


/**
 * Container helper for essentia empty/fill item actions in the I/O Interface.
 * <p>
 * Isolates all ThaumicEnergistics type references so that
 * {@link com.cells.blocks.iointerface.ContainerIOInterface} never directly references
 * essentia classes, preventing ClassNotFoundException when ThaumicEnergistics is not installed.
 * <p>
 * Only call methods on this class after verifying ThaumicEnergistics is loaded.
 */
public final class IOContainerEssentiaHelper {

    private IOContainerEssentiaHelper() {}

    /**
     * Handle pouring essentia from held item into storage slot (import only).
     * Only supports IEssentiaContainerItem (phials, jars).
     *
     * @return true if the action was handled
     */
    public static boolean handleEssentiaEmptyItem(
            EntityPlayerMP player, int slotIndex, IIOInterfaceHost host,
            Runnable updateHeld, Runnable detectChanges) {

        IInterfaceLogic logic = host.getActiveLogic();
        if (!(logic instanceof EssentiaInterfaceLogic)) return false;

        EssentiaInterfaceLogic essentiaLogic = (EssentiaInterfaceLogic) logic;
        if (slotIndex < 0 || slotIndex >= EssentiaInterfaceLogic.STORAGE_SLOTS) return false;

        ItemStack held = player.inventory.getItemStack();
        if (held.isEmpty()) return false;

        if (ItemRecoveryContainer.isType(held, ItemRecoveryContainer.TYPE_ESSENTIA)) {
            return handleRecoveryContainerPouring(player, slotIndex, held, essentiaLogic, updateHeld, detectChanges);
        }

        // Block vis crystals - they require burning in a smeltery
        if (held.getItem() == ItemsTC.crystalEssence) return false;

        if (!(held.getItem() instanceof IEssentiaContainerItem)) return false;

        return handleEssentiaContainerPouring(player, slotIndex, held, essentiaLogic, updateHeld, detectChanges);
    }

    /**
     * Handle filling held item from essentia slot (export only).
     *
     * @return true if the action was handled
     */
    public static boolean handleEssentiaFillItem(
            EntityPlayerMP player, int slotIndex, IIOInterfaceHost host,
            Runnable updateHeld, Runnable detectChanges) {

        IInterfaceLogic logic = host.getActiveLogic();
        if (!(logic instanceof EssentiaInterfaceLogic)) return false;

        EssentiaInterfaceLogic essentiaLogic = (EssentiaInterfaceLogic) logic;
        if (slotIndex < 0 || slotIndex >= EssentiaInterfaceLogic.STORAGE_SLOTS) return false;

        ItemStack held = player.inventory.getItemStack();
        if (held.isEmpty()) return false;

        // Block vis crystals
        if (held.getItem() == ItemsTC.crystalEssence) return false;

        if (!(held.getItem() instanceof IEssentiaContainerItem)) return false;

        IEssentiaContainerItem containerItem = (IEssentiaContainerItem) held.getItem();

        // Container must be empty
        AspectList existingAspects = containerItem.getAspects(held);
        if (existingAspects != null && existingAspects.size() > 0) return false;

        // Get container capacity
        ResourceLocation registryName = held.getItem().getRegistryName();
        if (registryName == null) return false;

        int containerCapacity = ThEApi.instance().config().essentiaContainerCapacity()
            .getOrDefault(registryName + ":" + held.getMetadata(), 0);
        if (containerCapacity <= 0) {
            containerCapacity = ThEApi.instance().config().essentiaContainerCapacity()
                .getOrDefault(registryName.toString(), 0);
        }
        if (containerCapacity <= 0) return false;

        // Check slot content
        EssentiaStack slotEssentia = essentiaLogic.getEssentiaInSlot(slotIndex);
        if (slotEssentia == null || slotEssentia.getAmount() <= 0) return false;

        Aspect aspect = slotEssentia.getAspect();
        if (aspect == null) return false;

        boolean isPhial = registryName.getPath().contains("phial");
        int toTransfer;

        if (isPhial) {
            if (slotEssentia.getAmount() < containerCapacity) return false;
            toTransfer = containerCapacity;
        } else {
            toTransfer = Math.min(slotEssentia.getAmount(), containerCapacity);
        }

        // Drain from slot
        EssentiaStack drained = essentiaLogic.drainEssentiaFromSlot(slotIndex, toTransfer, true);
        if (drained == null || drained.getAmount() <= 0) return false;

        // Handle stack splitting
        if (held.getCount() > 1) {
            ItemStack filledContainer = new ItemStack(held.getItem(), 1);
            IEssentiaContainerItem filledItem = (IEssentiaContainerItem) filledContainer.getItem();
            filledItem.setAspects(filledContainer, new AspectList().add(aspect, drained.getAmount()));
            if (isPhial) filledContainer.setItemDamage(1);

            if (!player.inventory.addItemStackToInventory(filledContainer)) {
                player.dropItem(filledContainer, false);
            }

            held.shrink(1);
            player.inventory.setItemStack(held);
        } else {
            containerItem.setAspects(held, new AspectList().add(aspect, drained.getAmount()));
            if (isPhial) held.setItemDamage(1);
        }

        updateHeld.run();
        detectChanges.run();
        return true;
    }

    private static boolean handleRecoveryContainerPouring(
            EntityPlayerMP player, int slotIndex, ItemStack held,
            EssentiaInterfaceLogic essentiaLogic,
            Runnable updateHeld, Runnable detectChanges) {

        EssentiaStack contained = QuickAddHelper.getEssentiaFromItemStack(held);
        if (contained == null || contained.getAmount() <= 0 || contained.getAspect() == null) return false;

        IAEEssentiaStack filterEssentia = essentiaLogic.getFilter(slotIndex);
        if (filterEssentia != null && filterEssentia.getStack().getAspect() != contained.getAspect()) return false;

        EssentiaStack currentSlotEssentia = essentiaLogic.getEssentiaInSlot(slotIndex);
        if (currentSlotEssentia != null && currentSlotEssentia.getAspect() != contained.getAspect()) return false;

        long toTransfer = Math.min(
            ItemRecoveryContainer.getAmount(held),
            essentiaLogic.getEffectiveMaxSlotSize(slotIndex) - essentiaLogic.getSlotAmount(slotIndex)
        );
        if (toTransfer <= 0) return false;

        long transferred = essentiaLogic.insertEssentiaIntoSlotLong(slotIndex, contained, toTransfer);

        if (transferred <= 0) return false;

        ItemRecoveryContainer.consumeHeldTransfer(player, held, transferred);

        updateHeld.run();
        detectChanges.run();
        return true;
    }

    // ================================= Internal =================================

    private static boolean handleEssentiaContainerPouring(
            EntityPlayerMP player, int slotIndex, ItemStack held,
            EssentiaInterfaceLogic essentiaLogic,
            Runnable updateHeld, Runnable detectChanges) {

        IEssentiaContainerItem containerItem = (IEssentiaContainerItem) held.getItem();

        AspectList aspects = containerItem.getAspects(held);
        if (aspects == null || aspects.size() == 0) return false;

        Aspect[] aspectArray = aspects.getAspects();
        if (aspectArray == null || aspectArray.length == 0) return false;

        Aspect aspect = aspectArray[0];
        int amount = aspects.getAmount(aspect);
        if (aspect == null || amount <= 0) return false;

        // Check filter
        IAEEssentiaStack filterEssentia = essentiaLogic.getFilter(slotIndex);
        if (filterEssentia != null && filterEssentia.getStack().getAspect() != aspect) return false;

        // Calculate space
        int capacity = (int) Math.min(essentiaLogic.getEffectiveMaxSlotSize(slotIndex), Integer.MAX_VALUE);
        EssentiaStack currentSlotEssentia = essentiaLogic.getEssentiaInSlot(slotIndex);
        if (currentSlotEssentia != null && currentSlotEssentia.getAspect() != aspect) return false;

        int currentAmount = currentSlotEssentia != null ? currentSlotEssentia.getAmount() : 0;
        int spaceAvailable = capacity - currentAmount;
        if (spaceAvailable <= 0) return false;

        // Phial detection
        boolean isPhial = held.getItem().getRegistryName() != null &&
            held.getItem().getRegistryName().getPath().contains("phial");

        int toTransfer;
        if (isPhial) {
            if (spaceAvailable < amount) return false;
            toTransfer = amount;
        } else {
            toTransfer = Math.min(amount, spaceAvailable);
        }

        // Handle stack splitting
        if (held.getCount() > 1) {
            ItemStack emptyContainer = new ItemStack(held.getItem(), 1);
            if (!player.inventory.addItemStackToInventory(emptyContainer)) {
                player.dropItem(emptyContainer, false);
            }
            held.shrink(1);
            player.inventory.setItemStack(held);
        } else {
            held.setTagCompound(null);
            held.setItemDamage(0);
        }

        // Insert into slot
        int newAmount = Math.min(currentAmount + toTransfer, capacity);
        EssentiaStack newEssentia = new EssentiaStack(aspect, newAmount);
        essentiaLogic.setEssentiaInSlot(slotIndex, newEssentia);

        updateHeld.run();
        detectChanges.run();
        return true;
    }
}
