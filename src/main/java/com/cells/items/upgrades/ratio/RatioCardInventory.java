package com.cells.items.upgrades.ratio;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.ItemStackHandler;

import appeng.api.implementations.ICraftingPatternItem;


public class RatioCardInventory extends ItemStackHandler {

    public static final int COLUMNS = 9;
    public static final int ROWS = 3;
    public static final int SLOTS = COLUMNS * ROWS;
    public static final int SLOT_LIMIT = 1;
    private static final String PATTERNS = "patterns";

    private final ItemStack card;
    private boolean loading = true;
    private boolean dirty;

    public RatioCardInventory(ItemStack card) {
        super(SLOTS);
        this.card = card;
        if (card.hasTagCompound() && card.getTagCompound().hasKey(PATTERNS)) {
            NBTTagCompound contents = card.getTagCompound().getCompoundTag(PATTERNS).copy();
            contents.setInteger("Size", SLOTS);
            deserializeNBT(contents);
        }

        this.loading = false;
    }

    @Override
    public int getSlotLimit(int slot) {
        return SLOT_LIMIT;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ICraftingPatternItem;
    }

    @Override
    protected void onContentsChanged(int slot) {
        if (this.loading) return;

        boolean empty = true;
        for (ItemStack pattern : this.stacks) {
            if (!pattern.isEmpty()) {
                empty = false;
                break;
            }
        }

        NBTTagCompound tag = this.card.getTagCompound();
        if (empty) {
            if (tag != null) {
                tag.removeTag(PATTERNS);
                if (tag.isEmpty()) this.card.setTagCompound(null);
            }
        } else {
            if (tag == null) {
                tag = new NBTTagCompound();
                this.card.setTagCompound(tag);
            }

            tag.setTag(PATTERNS, serializeNBT());
        }

        this.dirty = true;
    }

    public boolean consumeDirty() {
        boolean changed = this.dirty;
        this.dirty = false;
        return changed;
    }
}
