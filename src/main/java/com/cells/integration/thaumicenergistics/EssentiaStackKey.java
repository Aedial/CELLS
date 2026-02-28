package com.cells.integration.thaumicenergistics;

import java.util.Objects;

import javax.annotation.Nullable;

import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.api.EssentiaStack;


/**
 * Lightweight, immutable key wrapper for hashing/comparing EssentiaStacks by
 * aspect type. Useful for maps/sets where full EssentiaStack semantics
 * are desired but amounts are irrelevant.
 */
public final class EssentiaStackKey {

    private final String aspectTag;

    // Cached hash code for performance (EssentiaStackKey is immutable)
    private Integer cachedHashCode = null;

    private EssentiaStackKey(final String aspectTag) {
        this.aspectTag = Objects.requireNonNull(aspectTag, "aspectTag");
    }

    /**
     * Create a key from an EssentiaStack. Returns null for null stacks or null aspects.
     */
    @Nullable
    public static EssentiaStackKey of(@Nullable final EssentiaStack stack) {
        if (stack == null || stack.getAspect() == null) return null;

        return new EssentiaStackKey(stack.getAspectTag());
    }

    /**
     * Create a key from an Aspect. Returns null for null aspects.
     */
    @Nullable
    public static EssentiaStackKey of(@Nullable final Aspect aspect) {
        if (aspect == null) return null;

        return new EssentiaStackKey(aspect.getTag());
    }

    /**
     * Create a key from an aspect tag string. Returns null for null/empty tags.
     */
    @Nullable
    public static EssentiaStackKey of(@Nullable final String aspectTag) {
        if (aspectTag == null || aspectTag.isEmpty()) return null;

        return new EssentiaStackKey(aspectTag);
    }

    public String getAspectTag() {
        return this.aspectTag;
    }

    @Nullable
    public Aspect getAspect() {
        return Aspect.getAspect(this.aspectTag);
    }

    /**
     * Returns true if the provided stack is equivalent to this key
     * (same aspect). Null stacks return false.
     */
    public boolean matches(@Nullable final EssentiaStack stack) {
        if (stack == null || stack.getAspect() == null) return false;

        return this.aspectTag.equals(stack.getAspectTag());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof EssentiaStackKey)) return false;

        final EssentiaStackKey that = (EssentiaStackKey) o;
        return this.aspectTag.equals(that.aspectTag);
    }

    @Override
    public int hashCode() {
        if (this.cachedHashCode != null) return this.cachedHashCode;

        this.cachedHashCode = this.aspectTag.hashCode();
        return this.cachedHashCode;
    }

    @Override
    public String toString() {
        return "EssentiaStackKey{aspect=" + this.aspectTag + "}";
    }
}
