package com.cells.integration.thaumicenergistics;

import javax.annotation.Nullable;

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
