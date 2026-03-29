package com.cells.integration.thaumicenergistics;

import javax.annotation.Nullable;

import thaumcraft.api.aspects.Aspect;

import thaumicenergistics.api.EssentiaStack;
import thaumicenergistics.api.storage.IAEEssentiaStack;

import com.cells.blocks.interfacebase.IResourceInterfaceHost;
import com.cells.gui.slots.EssentiaTankSlot;


/**
 * Extended interface for Essentia Interface hosts (both import and export, both tile and part).
 * Provides access to essentia filter/storage operations.
 * <p>
 * Filter and storage methods are inherited from {@link IResourceInterfaceHost}
 * which provides default implementations delegating to the typed logic.
 * <p>
 * Unlike Fluid/Gas interfaces, essentia uses {@link thaumcraft.api.aspects.IAspectContainer}
 * directly on the tile entity rather than Forge capabilities. Parts cannot participate
 * in the tube network because Thaumcraft's tube system requires tile entities.
 */
public interface IEssentiaInterfaceHost
    extends IResourceInterfaceHost<IAEEssentiaStack, EssentiaStackKey>,
            EssentiaTankSlot.IEssentiaTankHost {

    /**
     * Get the essentia currently in a storage slot.
     * Required by {@link EssentiaTankSlot.IEssentiaTankHost} for GUI rendering.
     */
    EssentiaStack getEssentiaInSlot(int slot);

    @Override
    default long getEssentiaAmount(int slot) {
        return getInterfaceLogic().getSlotAmount(slot);
    }

    /**
     * Set the essentia in a storage slot (for import interface essentia pouring).
     *
     * @param slot The storage slot index
     * @param essentia The essentia to set, or null to clear
     */
    void setEssentiaInSlot(int slot, @Nullable EssentiaStack essentia);

    /**
     * Insert essentia into a storage slot (import interfaces only).
     *
     * @param slot The storage slot index
     * @param essentia The essentia to insert
     * @return The amount actually inserted
     */
    default int insertEssentiaIntoSlot(int slot, EssentiaStack essentia) {
        throw new UnsupportedOperationException("insertEssentiaIntoSlot is only supported on import interfaces");
    }

    /**
     * Extract essentia from a storage slot (export interfaces only).
     *
     * @param slot The storage slot index
     * @param maxDrain Maximum amount to drain
     * @param doDrain Whether to actually drain or just simulate
     * @return The essentia extracted, or null if nothing extracted
     */
    default EssentiaStack drainEssentiaFromSlot(int slot, int maxDrain, boolean doDrain) {
        throw new UnsupportedOperationException("drainEssentiaFromSlot is only supported on export interfaces");
    }

    // ============================== IAspectContainer Support ==============================

    /**
     * Check if this container accepts a specific aspect.
     * Returns true if there's a filter for this aspect.
     * This matches IAspectContainer.doesContainerAccept(Aspect).
     */
    boolean doesContainerAccept(Aspect aspect);

    /**
     * Check if this container contains at least the specified amount of an aspect.
     * This matches IAspectContainer.doesContainerContainAmount(Aspect, int).
     */
    boolean doesContainerContainAmount(Aspect aspect, int amount);

    /**
     * Add essentia to the container.
     * Used by tubes pushing essentia to IMPORT interfaces (they are sinks).
     * This matches IAspectContainer.addToContainer(Aspect, int).
     *
     * @param aspect The aspect to add
     * @param amount The amount to add
     * @return The amount actually added (0 for export interfaces)
     */
    int addToContainer(Aspect aspect, int amount);

    /**
     * Take essentia from the container (internal method).
     * Returns the amount actually taken. Use this for internal logic.
     * <p>
     * Only EXPORT interfaces provide essentia - they are sources.
     * <p>
     * Note: Tiles must also implement IAspectContainer.takeFromContainer(Aspect, int)
     * which returns boolean. That method should delegate to this one.
     *
     * @param aspect The aspect to take
     * @param amount The amount to take
     * @return The amount actually taken (0 for import interfaces)
     */
    int takeEssentiaAmount(Aspect aspect, int amount);

    /**
     * Get the amount of a specific aspect in the container.
     * Use this for internal logic when you need the actual count.
     *
     * @param aspect The aspect to check
     * @return The amount of that aspect stored
     */
    int getEssentiaCount(Aspect aspect);

    // ============================== IResourceInterfaceHost Defaults ==============================

    @Override
    @Nullable
    default EssentiaStackKey createKey(@Nullable IAEEssentiaStack stack) {
        if (stack == null) return null;
        return EssentiaStackKey.of(stack.getStack());
    }

    @Override
    default String getTypeLocalizationKey() {
        return "cells.type.essentia";
    }
}
