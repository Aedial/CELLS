package com.cells.util;

import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.definitions.IMaterials;
import appeng.api.storage.ICellWorkbenchItem;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;


/**
 * Shared utility for cell disassembly (shift-right-click to break down).
 * <p>
 * Consolidates the common logic from all cell base classes to avoid code duplication.
 * Supports different storage channels and optional component/housing returns.
 */
public final class CellDisassemblyHelper {

    private CellDisassemblyHelper() {}

    // =====================
    // Action handlers for Item methods
    // =====================

    /**
     * Standard onItemRightClick handler for cells that support disassembly.
     * Checks for sneaking and delegates to disassembly logic.
     *
     * @param world The world
     * @param player The player
     * @param hand The hand holding the cell
     * @param disassembler The disassembly function (takes ItemStack, returns success)
     * @return ActionResult for the right-click
     */
    @Nonnull
    public static ActionResult<ItemStack> handleRightClick(
            @Nonnull World world,
            @Nonnull EntityPlayer player,
            @Nonnull EnumHand hand,
            @Nonnull Function<ItemStack, Boolean> disassembler) {
        ItemStack stack = player.getHeldItem(hand);
        if (player.isSneaking() && disassembler.apply(stack)) {
            return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
        }

        return new ActionResult<>(EnumActionResult.PASS, stack);
    }

    /**
     * Standard onItemUseFirst handler for cells that support disassembly on block use.
     *
     * @param player The player
     * @param hand The hand holding the cell
     * @param disassembler The disassembly function (takes ItemStack, returns success)
     * @return EnumActionResult for the block use
     */
    @Nonnull
    public static EnumActionResult handleUseFirst(
            @Nonnull EntityPlayer player,
            @Nonnull EnumHand hand,
            @Nonnull Function<ItemStack, Boolean> disassembler) {
        return disassembler.apply(player.getHeldItem(hand)) ? EnumActionResult.SUCCESS : EnumActionResult.PASS;
    }

    // =====================
    // Core disassembly logic
    // =====================

    /**
     * Disassemble a storage cell, returning its components to the player.
     * <p>
     * This is the main entry point for standard cells that:
     * - Check if cell is empty via the storage channel
     * - Return upgrades from the cell
     * - Optionally return housing (AE2 empty storage cell)
     * - Optionally return a component (via supplier)
     *
     * @param <T> The AE stack type
     * @param stack The cell ItemStack to disassemble
     * @param player The player performing the action
     * @param channel The storage channel to check for contents
     * @param cellWorkbenchItem The ICellWorkbenchItem for getting upgrades
     * @param returnHousing Whether to return an empty AE2 storage cell housing
     * @param componentSupplier Optional function to get the component ItemStack (may be null)
     * @return true if disassembly was successful
     */
    public static <T extends IAEStack<T>> boolean disassembleCell(
            @Nonnull ItemStack stack,
            @Nonnull EntityPlayer player,
            @Nonnull IStorageChannel<T> channel,
            @Nonnull ICellWorkbenchItem cellWorkbenchItem,
            boolean returnHousing,
            @Nullable Function<ItemStack, ItemStack> componentSupplier) {
        if (!player.isSneaking()) return false;
        if (Platform.isClient()) return false;

        // Check if cell has content
        IMEInventoryHandler<T> inv = AEApi.instance().registries().cell()
                .getCellInventory(stack, null, channel);
        if (inv == null) return false;

        IItemList<T> list = inv.getAvailableItems(channel.createList());
        if (!list.isEmpty()) {
            player.sendStatusMessage(new TextComponentString(
                    "§c" + I18n.format("message.cells.disassemble_fail_content")), true);
            return false;
        }

        InventoryAdaptor ia = InventoryAdaptor.getAdaptor(player);
        if (ia == null) return false;

        // Remove one cell from the player's hand
        removeOneFromHand(stack, player);

        // Return upgrades
        returnUpgrades(cellWorkbenchItem.getUpgradesInventory(stack), ia, player);

        // Return housing if requested
        if (returnHousing) returnStandardHousing(ia, player);

        // Return component if supplier provided
        if (componentSupplier != null) {
            ItemStack component = componentSupplier.apply(stack);
            returnItem(component, ia, player);
        }

        if (player.inventoryContainer != null) player.inventoryContainer.detectAndSendChanges();

        return true;
    }

    /**
     * Simplified disassembly for cells without components.
     * Returns upgrades and optionally housing.
     */
    public static <T extends IAEStack<T>> boolean disassembleCell(
            @Nonnull ItemStack stack,
            @Nonnull EntityPlayer player,
            @Nonnull IStorageChannel<T> channel,
            @Nonnull ICellWorkbenchItem cellWorkbenchItem,
            boolean returnHousing) {
        return disassembleCell(stack, player, channel, cellWorkbenchItem, returnHousing, null);
    }

    // =====================
    // Helper methods
    // =====================

    /**
     * Remove one item from the player's held stack.
     * Handles both main hand and off hand, and stack counts > 1.
     */
    public static void removeOneFromHand(@Nonnull ItemStack stack, @Nonnull EntityPlayer player) {
        if (stack.getCount() > 1) {
            stack.shrink(1);
        } else {
            // Determine which hand and clear it
            if (stack == player.getHeldItemMainhand()) {
                player.inventory.setInventorySlotContents(player.inventory.currentItem, ItemStack.EMPTY);
            } else if (stack == player.getHeldItemOffhand()) {
                player.setHeldItem(EnumHand.OFF_HAND, ItemStack.EMPTY);
            }
        }
    }

    /**
     * Return all upgrades from the given inventory to the player.
     */
    public static void returnUpgrades(
            @Nonnull IItemHandler upgradesInventory,
            @Nonnull InventoryAdaptor ia,
            @Nonnull EntityPlayer player) {
        for (int i = 0; i < upgradesInventory.getSlots(); i++) {
            ItemStack upgradeStack = upgradesInventory.getStackInSlot(i);
            if (!upgradeStack.isEmpty()) returnItem(upgradeStack, ia, player);
        }
    }

    /**
     * Return the standard AE2 empty storage cell housing to the player.
     */
    public static void returnStandardHousing(
            @Nonnull InventoryAdaptor ia,
            @Nonnull EntityPlayer player) {
        IMaterials materials = AEApi.instance().definitions().materials();
        ItemStack housing = materials.emptyStorageCell().maybeStack(1).orElse(ItemStack.EMPTY);
        returnItem(housing, ia, player);
    }

    /**
     * Return an item to the player's inventory, dropping if full.
     */
    public static void returnItem(
            @Nonnull ItemStack itemStack,
            @Nonnull InventoryAdaptor ia,
            @Nonnull EntityPlayer player) {
        if (itemStack.isEmpty()) return;

        ItemStack leftStack = ia.addItems(itemStack);
        if (!leftStack.isEmpty()) player.dropItem(leftStack, false);
    }

    /**
     * Check if any slot in an item handler has items.
     */
    public static boolean hasUpgrades(@Nonnull IItemHandler upgrades) {
        for (int i = 0; i < upgrades.getSlots(); i++) {
            if (!upgrades.getStackInSlot(i).isEmpty()) return true;
        }

        return false;
    }

    /**
     * Extract and return all upgrades from an item handler, extracting them from the slots.
     * Unlike {@link #returnUpgrades}, this modifies the source inventory.
     */
    public static void extractAndReturnUpgrades(
            @Nonnull IItemHandler upgradesInventory,
            @Nonnull InventoryAdaptor ia,
            @Nonnull EntityPlayer player) {
        for (int i = 0; i < upgradesInventory.getSlots(); i++) {
            ItemStack upgradeStack = upgradesInventory.extractItem(i, Integer.MAX_VALUE, false);
            if (!upgradeStack.isEmpty()) returnItem(upgradeStack, ia, player);
        }
    }
}
