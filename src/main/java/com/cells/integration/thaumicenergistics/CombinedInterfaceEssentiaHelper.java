package com.cells.integration.thaumicenergistics;

import javax.annotation.Nullable;

import appeng.tile.inventory.AppEngInternalInventory;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;

import com.cells.blocks.interfacebase.AbstractResourceInterfaceLogic;
import com.cells.blocks.interfacebase.IInterfaceLogic;


/**
 * Helper for creating essentia interface logics in combined interfaces and
 * for delegating Thaumcraft interface methods (IAspectContainer, IAspectSource,
 * IEssentiaTransport) from the combined interface tiles.
 * <p>
 * This class lives in the ThaumicEnergistics integration package to safely reference
 * Thaumcraft/ThaumicEnergistics classes without causing ClassNotFoundException when the mods aren't loaded.
 * The combined tile classes use @Optional.Method on their Thaumcraft interface implementations,
 * so these delegate methods are only ever called when Thaumcraft is present.
 * <p>
 * Only call methods on this class after verifying {@code ThaumicEnergisticsIntegration.isModLoaded()}.
 */
public class CombinedInterfaceEssentiaHelper {

    private CombinedInterfaceEssentiaHelper() {}

    // ============================== Logic creation ==============================

    /**
     * Create an essentia interface logic with its own upgrade inventory.
     */
    public static EssentiaInterfaceLogic createEssentiaLogic(AbstractResourceInterfaceLogic.Host host) {
        return new EssentiaInterfaceLogic(host);
    }

    /**
     * Create an essentia interface logic sharing an existing upgrade inventory.
     */
    public static EssentiaInterfaceLogic createEssentiaLogic(AbstractResourceInterfaceLogic.Host host,
                                                              AppEngInternalInventory sharedUpgradeInventory) {
        return new EssentiaInterfaceLogic(host, sharedUpgradeInventory);
    }

    // ============================== IAspectContainer delegates ==============================

    /**
     * Safely cast and return the logic's aspect list.
     *
     * @param essentiaLogic the essentiaLogic field from the combined tile (nullable)
     * @return the extractable aspect list, or an empty list if the logic is null
     */
    public static AspectList getAspects(@Nullable IInterfaceLogic essentiaLogic) {
        if (essentiaLogic == null) return new AspectList();
        return ((EssentiaInterfaceLogic) essentiaLogic).getAvailableAspects();
    }

    /**
     * Check if the container holds all the given aspects.
     */
    public static boolean doesContainerContain(@Nullable IInterfaceLogic essentiaLogic, AspectList aspects) {
        if (essentiaLogic == null) return false;
        EssentiaInterfaceLogic logic = (EssentiaInterfaceLogic) essentiaLogic;

        for (Aspect aspect : aspects.getAspects()) {
            if (aspect == null) continue;
            if (!logic.doesContainerContainAmount(aspect, aspects.getAmount(aspect))) return false;
        }

        return true;
    }

    /**
     * Return the extractable amount of a specific aspect.
     */
    public static int containerContains(@Nullable IInterfaceLogic essentiaLogic, Aspect aspect) {
        if (essentiaLogic == null) return 0;
        return ((EssentiaInterfaceLogic) essentiaLogic).getAvailableEssentiaCount(aspect);
    }

    /**
     * Check if the container accepts a specific aspect (matches a filter slot).
     */
    public static boolean doesContainerAccept(@Nullable IInterfaceLogic essentiaLogic, Aspect aspect) {
        if (essentiaLogic == null) return false;
        return ((EssentiaInterfaceLogic) essentiaLogic).doesContainerAccept(aspect);
    }

    /**
     * Check if the container holds at least {@code amount} of the given aspect.
     */
    public static boolean doesContainerContainAmount(@Nullable IInterfaceLogic essentiaLogic, Aspect aspect, int amount) {
        if (essentiaLogic == null) return false;
        return ((EssentiaInterfaceLogic) essentiaLogic).doesContainerContainAmount(aspect, amount);
    }

    /**
     * Take essentia from the container (used by export/source interfaces).
     * Import interfaces should return false since they are sinks.
     */
    public static boolean takeFromContainer(@Nullable IInterfaceLogic essentiaLogic, AspectList aspects) {
        if (essentiaLogic == null) return false;
        EssentiaInterfaceLogic logic = (EssentiaInterfaceLogic) essentiaLogic;

        // Check availability first
        for (Aspect aspect : aspects.getAspects()) {
            if (aspect == null) continue;
            int needed = aspects.getAmount(aspect);
            if (!logic.doesContainerContainAmount(aspect, needed)) return false;
        }

        // Take all
        for (Aspect aspect : aspects.getAspects()) {
            if (aspect == null) continue;
            int needed = aspects.getAmount(aspect);
            logic.takeEssentiaAmount(aspect, needed);
        }

        return true;
    }

    /**
     * Take a single aspect from the container (used by export/source interfaces).
     */
    public static boolean takeFromContainerSingle(@Nullable IInterfaceLogic essentiaLogic, Aspect aspect, int amount) {
        if (essentiaLogic == null) return false;
        return ((EssentiaInterfaceLogic) essentiaLogic).takeFromContainer(aspect, amount);
    }

    // ============================== IEssentiaTransport delegates ==============================

    /**
     * Return the suction type (which aspect we want/have).
     */
    @Nullable
    public static Aspect getSuctionType(@Nullable IInterfaceLogic essentiaLogic) {
        if (essentiaLogic == null) return null;
        return ((EssentiaInterfaceLogic) essentiaLogic).getSuctionType();
    }

    /**
     * Return the stored essentia type (for transport queries).
     */
    @Nullable
    public static Aspect getStoredEssentiaType(@Nullable IInterfaceLogic essentiaLogic) {
        if (essentiaLogic == null) return null;
        return ((EssentiaInterfaceLogic) essentiaLogic).getStoredEssentiaType();
    }

    /**
     * Return the amount of the stored essentia type.
     */
    public static int getStoredEssentiaAmount(@Nullable IInterfaceLogic essentiaLogic) {
        if (essentiaLogic == null) return 0;

        EssentiaInterfaceLogic logic = (EssentiaInterfaceLogic) essentiaLogic;
        Aspect type = logic.getStoredEssentiaType();
        if (type == null) return 0;

        return logic.getEssentiaCount(type);
    }

    /**
     * Add essentia from tube transport (used by import/sink interfaces).
     *
     * @return the leftover amount that could NOT be added
     */
    public static int addToContainer(@Nullable IInterfaceLogic essentiaLogic, Aspect aspect, int amount) {
        if (essentiaLogic == null) return amount;
        return ((EssentiaInterfaceLogic) essentiaLogic).addToContainer(aspect, amount);
    }

    public static int addEssentia(@Nullable IInterfaceLogic essentiaLogic, Aspect aspect, int amount) {
        return amount - addToContainer(essentiaLogic, aspect, amount);
    }

    /**
     * Take essentia for tube transport (used by export/source interfaces).
     *
     * @return the amount actually taken
     */
    public static int takeEssentiaAmount(@Nullable IInterfaceLogic essentiaLogic, Aspect aspect, int amount) {
        if (essentiaLogic == null) return 0;
        return ((EssentiaInterfaceLogic) essentiaLogic).takeEssentiaAmount(aspect, amount);
    }
}
