package com.cells.util;

import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;


/**
 * Lightweight, immutable key wrapper for hashing/comparing FluidStacks by
 * fluid type and NBT. Useful for maps/sets where full FluidStack semantics
 * are desired but amounts are irrelevant.
 */
public final class FluidStackKey {

    private final Fluid fluid;
    @Nullable
    private final NBTTagCompound nbt;

    // Cached hash code for performance (FluidStackKey is immutable)
    private Integer cachedHashCode = null;

    private FluidStackKey(final Fluid fluid, @Nullable final NBTTagCompound nbt) {
        this.fluid = Objects.requireNonNull(fluid, "fluid");
        this.nbt = (nbt == null) ? null : nbt.copy();
    }

    /**
     * Create a key from a FluidStack. Returns null for null stacks.
     */
    @Nullable
    public static FluidStackKey of(@Nullable final FluidStack stack) {
        if (stack == null || stack.getFluid() == null) return null;

        return new FluidStackKey(stack.getFluid(), stack.tag);
    }

    public Fluid getFluid() {
        return this.fluid;
    }

    @Nullable
    public NBTTagCompound getNbt() {
        return this.nbt;
    }

    /**
     * Returns true if the provided stack is equivalent to this key
     * (fluid and NBT match). Null stacks return false.
     */
    public boolean matches(@Nullable final FluidStack stack) {
        if (stack == null || stack.getFluid() == null) return false;

        if (stack.getFluid() != this.fluid) return false;

        return Objects.equals(this.nbt, stack.tag);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof FluidStackKey)) return false;

        final FluidStackKey that = (FluidStackKey) o;
        return this.fluid == that.fluid && Objects.equals(this.nbt, that.nbt);
    }

    @Override
    public int hashCode() {
        if (this.cachedHashCode != null) return this.cachedHashCode;

        int hash = this.fluid.hashCode();
        if (this.nbt != null) hash = hash * 31 + this.nbt.hashCode();
        this.cachedHashCode = hash;

        return this.cachedHashCode;
    }

    @Override
    public String toString() {
        return "FluidStackKey{fluid=" + this.fluid.getName() +
               (this.nbt == null ? "" : "|" + this.nbt) + "}";
    }
}
