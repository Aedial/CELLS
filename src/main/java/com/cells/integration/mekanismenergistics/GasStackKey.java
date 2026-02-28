package com.cells.integration.mekanismenergistics;

import java.util.Objects;

import javax.annotation.Nullable;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;


/**
 * Lightweight, immutable key wrapper for hashing/comparing GasStacks by
 * gas type. Useful for maps/sets where full GasStack semantics
 * are desired but amounts are irrelevant.
 */
public final class GasStackKey {

    private final Gas gas;

    // Cached hash code for performance (GasStackKey is immutable)
    private Integer cachedHashCode = null;

    private GasStackKey(final Gas gas) {
        this.gas = Objects.requireNonNull(gas, "gas");
    }

    /**
     * Create a key from a GasStack. Returns null for null stacks or null gases.
     */
    @Nullable
    public static GasStackKey of(@Nullable final GasStack stack) {
        if (stack == null || stack.getGas() == null) return null;

        return new GasStackKey(stack.getGas());
    }

    /**
     * Create a key from a Gas. Returns null for null gases.
     */
    @Nullable
    public static GasStackKey of(@Nullable final Gas gas) {
        if (gas == null) return null;

        return new GasStackKey(gas);
    }

    public Gas getGas() {
        return this.gas;
    }

    /**
     * Returns true if the provided stack is equivalent to this key
     * (same gas). Null stacks return false.
     */
    public boolean matches(@Nullable final GasStack stack) {
        if (stack == null || stack.getGas() == null) return false;

        return this.gas == stack.getGas();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof GasStackKey)) return false;

        final GasStackKey that = (GasStackKey) o;
        return this.gas == that.gas;
    }

    @Override
    public int hashCode() {
        if (this.cachedHashCode != null) return this.cachedHashCode;

        this.cachedHashCode = this.gas.hashCode();
        return this.cachedHashCode;
    }

    @Override
    public String toString() {
        return "GasStackKey{gas=" + this.gas.getName() + "}";
    }
}
