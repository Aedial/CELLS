package com.cells.util;

import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.storage.data.IAEItemStack;


/**
 * Lightweight, immutable key wrapper for hashing/comparing ItemStacks by
 * item, metadata (damage), and NBT. Useful for maps/sets where full
 * ItemStack semantics are desired but stack counts are irrelevant.
 */
public final class ItemStackKey {

    private final Item item;
    private final int meta;
    @Nullable
    private final NBTTagCompound nbt;

    // Cached hash code for performance (ItemStackKey is immutable)
    private Integer cachedHashCode = null;

    private ItemStackKey(final Item item, final int meta, @Nullable final NBTTagCompound nbt, final boolean copyNbt) {
        this.item = Objects.requireNonNull(item, "item");
        this.meta = meta;
        this.nbt = (nbt == null) ? null : (copyNbt ? nbt.copy() : nbt);
    }

    /**
     * Create a key from an ItemStack. Returns null for null/empty stacks.
     */
    @Nullable
    public static ItemStackKey of(@Nullable final ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        final Item it = stack.getItem();
        final int m = stack.getItemDamage();
        final NBTTagCompound tag = (stack.hasTagCompound()) ? stack.getTagCompound() : null;

        return new ItemStackKey(it, m, tag, true);
    }

    /**
     * Create a key from a transient ItemStack. Returns null for null stacks.
     * @param stack The stack to create a key for. Must not be modified as long as the key is in use.
     */
    @Nullable
    public static ItemStackKey ofTransient(@Nullable final ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        final Item it = stack.getItem();
        final int m = stack.getItemDamage();
        final NBTTagCompound tag = (stack.hasTagCompound()) ? stack.getTagCompound() : null;

        return new ItemStackKey(it, m, tag, false);
    }

    /**
     * Create a transient lookup key from an IAEItemStack <b>without copying NBT</b>.
     * <p>
     * The returned key borrows a reference to the definition stack's NBT tag.
     * It is intended for short-lived {@code Set.contains()} lookups only,
     * do NOT store it longer than the calling scope.
     */
    @Nullable
    public static ItemStackKey ofLookup(@Nullable final IAEItemStack aeStack) {
        if (aeStack == null) return null;

        // getDefinition() returns a shared, unmodifiable stack: its NBT is safe
        // to reference for the duration of a single contains() call.
        final ItemStack def = aeStack.getDefinition();
        return ofTransient(def);
    }

    /**
     * Create an ItemStack from this key with the specified count.
     * Useful when an API requires an ItemStack but we only have a key
     * (e.g. IItemRepository.getStoredItemCount needs a stack parameter).
     */
    public ItemStack toStack(int count) {
        ItemStack stack = new ItemStack(this.item, count, this.meta);
        if (this.nbt != null) stack.setTagCompound(this.nbt.copy());
        return stack;
    }

    /**
     * Returns true if the provided stack is equivalent to this key
     * (item, meta, and NBT all match). Null/empty stacks return false.
     */
    public boolean matches(@Nullable final ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        if (stack.getItem() != this.item) return false;
        if (stack.getItemDamage() != this.meta) return false;

        final NBTTagCompound other = stack.getTagCompound();
        return Objects.equals(this.nbt, other);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemStackKey)) return false;

        final ItemStackKey that = (ItemStackKey) o;
        return this.meta == that.meta && this.item == that.item && Objects.equals(this.nbt, that.nbt);
    }

    @Override
    public int hashCode() {
        if (this.cachedHashCode != null) return this.cachedHashCode;

        int hash = this.item.hashCode() * 31 + this.meta;
        if (this.nbt != null) hash = hash * 31 + this.nbt.hashCode();
        this.cachedHashCode = hash;

        return this.cachedHashCode;
    }

    @Override
    public String toString() {
        return "ItemStackKey{item=" + this.item.getRegistryName() + "@" + this.meta +
               (this.nbt == null ? "null" : "|" + this.nbt) + "}";
    }
}
