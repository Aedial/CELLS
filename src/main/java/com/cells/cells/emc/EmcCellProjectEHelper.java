package com.cells.cells.emc;

import net.minecraft.item.ItemStack;

import com.latmod.mods.projectex.ProjectEXUtils;

import moze_intel.projecte.utils.NBTWhitelist;


final class EmcCellProjectEHelper {

    private EmcCellProjectEHelper() {
    }

    static ItemStack normalizeStack(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;

        if (isExactProjectEIdentity(stack)) return stack;

        // fixOutput() handles normalizing the stack to be like what is in knowledge,
        // e.g. by removing NBT, which could be used to trade between items that rely
        // on NBT tag to dispatch their types (e.g. Enchanted Books or Vis Crystals).
        ItemStack normalized = ProjectEXUtils.fixOutput(stack);
        if (normalized.isEmpty()) return ItemStack.EMPTY;

        return normalized;
    }

    /**
     * Cheap predicate for whether ProjectE normalization would preserve this stack's exact AE2 identity.
     * This avoids creating a fresh ItemStack for the common case where the stack is already canonical.
     */
    static boolean isExactProjectEIdentity(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!stack.getHasSubtypes() && stack.isItemStackDamageable() && stack.getItemDamage() != 0) return false;

        return !stack.hasTagCompound() || NBTWhitelist.shouldDupeWithNBT(stack);
    }
}