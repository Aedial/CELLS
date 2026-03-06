package com.cells.util;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import appeng.tile.inventory.AppEngInternalInventory;


/**
 * Helper for migrating inventories when loading old saves.
 * Prevents inventory shrinking when NBT has a smaller "Size" value than expected.
 */
public final class InventoryMigrationHelper {

    private InventoryMigrationHelper() {
        // Utility class
    }

    /**
     * Read inventory from NBT without allowing the inventory to shrink.
     * This is used to migrate old saves where the inventory had fewer slots.
     * Items from the old save are loaded into their original slots.
     *
     * @param inventory The inventory to load into (already at correct size)
     * @param data The NBT compound containing the inventory data
     * @param name The key name for the inventory in NBT
     */
    public static void readFromNBTWithoutShrinking(AppEngInternalInventory inventory, NBTTagCompound data, String name) {
        NBTTagCompound invData = data.getCompoundTag(name);
        if (invData == null || invData.isEmpty()) return;

        // Read items without calling setSize - preserve our current inventory size
        NBTTagList tagList = invData.getTagList("Items", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < tagList.tagCount(); i++) {
            NBTTagCompound itemTags = tagList.getCompoundTagAt(i);
            int slot = itemTags.getInteger("Slot");

            // Only load into valid slots within our current inventory size
            if (slot >= 0 && slot < inventory.getSlots()) {
                ItemStack stack = new ItemStack(itemTags);
                inventory.setStackInSlot(slot, stack);
            }
        }
    }
}
