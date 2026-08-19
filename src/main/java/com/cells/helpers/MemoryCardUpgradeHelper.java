package com.cells.helpers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.IItemHandler;

import appeng.tile.inventory.AppEngInternalInventory;

import com.cells.gui.overlay.ServerMessageHelper;


/**
 * Restores upgrade inventories from memory-card payloads without consuming cards
 * that are already installed.
 * <p>
 * Matching ignores NBT so cards with per-card settings can still match existing
 * installs, while the saved NBT is copied onto the matched or newly inserted
 * card afterward.
 */
public final class MemoryCardUpgradeHelper {

    private MemoryCardUpgradeHelper() {
    }

    /**
     * Restore the upgrades stored under the given NBT key.
     * <p>
     * Existing installed cards satisfy the saved requirement one-for-one when
     * they match by item identity. Only missing cards are sourced from the
     * player's main inventory.
     */
    public static void restoreFromMemoryCard(@Nonnull NBTTagCompound data,
                                             @Nonnull String key,
                                             @Nonnull EntityPlayer player,
                                             @Nonnull IItemHandler upgrades,
                                             @Nonnull BiConsumer<Integer, ItemStack> slotSetter) {
        if (!data.hasKey(key)) return;

        List<ItemStack> savedUpgrades = readSavedUpgrades(data, key, upgrades.getSlots());
        if (savedUpgrades.isEmpty()) return;

        boolean[] claimedSlots = new boolean[upgrades.getSlots()];
        List<ItemStack> missingUpgrades = new ArrayList<>();
        List<ItemStack> blockedUpgrades = new ArrayList<>();

        for (ItemStack savedUpgrade : savedUpgrades) {
            int installedSlot = findMatchingInstalledSlot(upgrades, claimedSlots, savedUpgrade);
            if (installedSlot >= 0) {
                claimedSlots[installedSlot] = true;

                ItemStack existing = upgrades.getStackInSlot(installedSlot);
                ItemStack updated = copySavedSettings(existing, savedUpgrade);
                if (!sameStackWithTags(existing, updated)) {
                    slotSetter.accept(installedSlot, updated);
                }

                continue;
            }

            int playerSlot = findMatchingPlayerSlot(player, savedUpgrade);
            if (playerSlot < 0) {
                missingUpgrades.add(savedUpgrade.copy());
                continue;
            }

            ItemStack playerStack = player.inventory.mainInventory.get(playerSlot);
            ItemStack preparedUpgrade = copySavedSettings(playerStack, savedUpgrade);
            int insertedSlot = insertOne(preparedUpgrade, upgrades);
            if (insertedSlot < 0) {
                blockedUpgrades.add(savedUpgrade.copy());
                continue;
            }

            claimedSlots[insertedSlot] = true;
            consumeOneFromPlayer(player, playerSlot);
        }

        notifyWarnings(player, missingUpgrades, blockedUpgrades);
    }

    private static List<ItemStack> readSavedUpgrades(NBTTagCompound data, String key, int slotCount) {
        AppEngInternalInventory temp = new AppEngInternalInventory(null, slotCount, 1);
        temp.readFromNBT(data, key);

        List<ItemStack> savedUpgrades = new ArrayList<>();
        for (int slot = 0; slot < temp.getSlots(); slot++) {
            ItemStack sourceStack = temp.getStackInSlot(slot);
            if (sourceStack.isEmpty()) continue;

            int copies = Math.max(1, sourceStack.getCount());
            for (int i = 0; i < copies; i++) {
                ItemStack normalized = sourceStack.copy();
                normalized.setCount(1);
                savedUpgrades.add(normalized);
            }
        }

        return savedUpgrades;
    }

    private static int findMatchingInstalledSlot(IItemHandler upgrades, boolean[] claimedSlots, ItemStack savedUpgrade) {
        for (int slot = 0; slot < upgrades.getSlots(); slot++) {
            if (claimedSlots[slot]) continue;

            ItemStack installed = upgrades.getStackInSlot(slot);
            if (sameUpgradeItem(installed, savedUpgrade)) return slot;
        }

        return -1;
    }

    private static int findMatchingPlayerSlot(EntityPlayer player, ItemStack savedUpgrade) {
        for (int slot = 0; slot < player.inventory.mainInventory.size(); slot++) {
            ItemStack candidate = player.inventory.mainInventory.get(slot);
            if (sameUpgradeItem(candidate, savedUpgrade)) return slot;
        }

        return -1;
    }

    private static int insertOne(ItemStack stack, IItemHandler upgrades) {
        if (stack.isEmpty()) return -1;

        int originalCount = stack.getCount();
        for (int slot = 0; slot < upgrades.getSlots(); slot++) {
            if (!upgrades.isItemValid(slot, stack)) continue;

            ItemStack remainder = upgrades.insertItem(slot, stack.copy(), false);
            if (remainder.isEmpty() || remainder.getCount() < originalCount) return slot;
        }

        return -1;
    }

    private static void consumeOneFromPlayer(EntityPlayer player, int slot) {
        ItemStack stack = player.inventory.mainInventory.get(slot);
        if (stack.isEmpty()) return;

        stack.shrink(1);
        if (stack.getCount() <= 0) player.inventory.mainInventory.set(slot, ItemStack.EMPTY);

        player.inventory.markDirty();
    }

    private static ItemStack copySavedSettings(ItemStack targetBase, ItemStack savedUpgrade) {
        ItemStack updated = targetBase.copy();
        updated.setCount(1);
        updated.setTagCompound(savedUpgrade.hasTagCompound() ? savedUpgrade.getTagCompound().copy() : null);
        return updated;
    }

    private static boolean sameUpgradeItem(ItemStack left, ItemStack right) {
        return !left.isEmpty() && !right.isEmpty() && ItemStack.areItemsEqual(left, right);
    }

    private static boolean sameStackWithTags(ItemStack left, ItemStack right) {
        if (left.isEmpty() || right.isEmpty()) return left.isEmpty() && right.isEmpty();

        return left.getCount() == right.getCount()
            && ItemStack.areItemsEqual(left, right)
            && ItemStack.areItemStackTagsEqual(left, right);
    }

    private static void notifyWarnings(EntityPlayer player, List<ItemStack> missingUpgrades, List<ItemStack> blockedUpgrades) {
        if (!(player instanceof EntityPlayerMP)) return;

        if (!missingUpgrades.isEmpty()) {
            ServerMessageHelper.warning(
                (EntityPlayerMP) player,
                "message.cells.memory_card.upgrades_not_found",
                String.valueOf(missingUpgrades.size()),
                summarizeUpgrades(missingUpgrades));
        }

        if (!blockedUpgrades.isEmpty()) {
            ServerMessageHelper.warning(
                (EntityPlayerMP) player,
                "message.cells.memory_card.upgrades_not_inserted",
                String.valueOf(blockedUpgrades.size()),
                summarizeUpgrades(blockedUpgrades));
        }
    }

    private static String summarizeUpgrades(List<ItemStack> upgrades) {
        Map<String, Integer> counts = new LinkedHashMap<>();

        for (ItemStack stack : upgrades) {
            String name = stack.getDisplayName();
            counts.put(name, counts.getOrDefault(name, 0) + 1);
        }

        StringBuilder summary = new StringBuilder();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (summary.length() > 0) summary.append("\n- ");

            summary.append(entry.getKey());
            if (entry.getValue() > 1) {
                summary.append(" (x").append(entry.getValue()).append(')');
            }
        }

        return summary.toString();
    }
}