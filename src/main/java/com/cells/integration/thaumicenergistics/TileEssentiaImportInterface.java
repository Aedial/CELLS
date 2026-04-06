package com.cells.integration.thaumicenergistics;

import javax.annotation.Nullable;

import net.minecraft.util.EnumFacing;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;
import thaumcraft.api.aspects.IEssentiaTransport;

import thaumicenergistics.api.EssentiaStack;

import com.cells.blocks.interfacebase.AbstractInterfaceTile;


/**
 * Tile entity for the Essentia Import Interface block.
 * Provides filter slots (aspect-based filters) and internal essentia storage.
 * Only accepts essentia that matches the filter in the corresponding slot.
 * Automatically imports stored essentia into the ME network.
 * <p>
 * Implements {@link IAspectContainer} for adjacent machines to detect essentia storage.
 * Implements {@link IEssentiaTransport} to participate in the Thaumcraft tube network.
 * <p>
 * NOTE: To fully work with tubes, we would need to ACTIVELY pull essentia every tick.
 *       This is a fairly laggy thing to do, so it is not implemented.
 *       Use the Pull Card with an Essentia Container instead.
 * <p>
 * Business logic is delegated to {@link EssentiaInterfaceLogic} to avoid code
 * duplication with part and export variants.
 */
public class TileEssentiaImportInterface extends AbstractInterfaceTile<EssentiaInterfaceLogic>
        implements IEssentiaInterfaceHost, EssentiaInterfaceLogic.Host, IAspectContainer, IEssentiaTransport {

    public TileEssentiaImportInterface() {
        this.initLogic(new EssentiaInterfaceLogic(this));
    }

    @Override
    public boolean isExport() {
        return false;
    }

    @Override
    public int getMainGuiId() {
        return EssentiaInterfaceGuiHandler.GUI_ESSENTIA_IMPORT_INTERFACE;
    }

    // ============================== IEssentiaInterfaceHost Implementation ==============================

    @Nullable
    @Override
    public EssentiaStack getEssentiaInSlot(int slot) {
        return this.logic.getEssentiaInSlot(slot);
    }

    @Override
    public void setEssentiaInSlot(int slot, @Nullable EssentiaStack essentia) {
        this.logic.setEssentiaInSlot(slot, essentia);
    }

    @Override
    public boolean doesContainerAccept(Aspect aspect) {
        return this.logic.doesContainerAccept(aspect);
    }

    @Override
    public boolean doesContainerContainAmount(Aspect aspect, int amount) {
        return this.logic.doesContainerContainAmount(aspect, amount);
    }

    @Override
    public int addToContainer(Aspect aspect, int amount) {
        return this.logic.addToContainer(aspect, amount);
    }

    // ============================== IAspectContainer Implementation ==============================

    @Override
    public AspectList getAspects() {
        return this.logic.getAspects();
    }

    @Override
    public void setAspects(AspectList aspects) {
        // Not implemented - we manage our own storage
    }

    @Override
    public boolean doesContainerContain(AspectList aspects) {
        for (Aspect aspect : aspects.getAspects()) {
            if (!this.logic.containerContainsAny(aspect)) return false;
        }
        return true;
    }

    @Override
    public boolean takeFromContainer(AspectList aspects) {
        // Import interface: we are a SINK, not a source
        // Tubes should not pull from us - they push to us
        return false;
    }

    @Override
    public boolean takeFromContainer(Aspect aspect, int amount) {
        // Import interface: we are a SINK, not a source
        // Tubes should not pull from us - they push to us
        return false;
    }

    @Override
    public int containerContains(Aspect aspect) {
        return this.logic.getEssentiaCount(aspect);
    }

    // ============================== IEssentiaTransport Implementation ==============================

    /**
     * Import interface has HIGH suction minimum - we are an essentia sink.
     * That's the amount other blocks need to import from us.
     */
    @Override
    public int getMinimumSuction() {
        return EssentiaInterfaceLogic.IMPORT_SUCTION;
    }

    /**
     * Import interface has HIGH suction, we are a sink that wants essentia.
     */
    @Override
    public int getSuctionAmount(EnumFacing facing) {
        return EssentiaInterfaceLogic.IMPORT_SUCTION;
    }

    /**
     * Return the aspect type we want to receive (first filter, or null for any).
     */
    @Override
    public Aspect getSuctionType(EnumFacing facing) {
        // As a sink, we advertise what we want
        return this.logic.getSuctionType();
    }

    /**
     * Import interface: tubes can push essentia to us, we are a sink.
     */
    @Override
    public boolean canInputFrom(EnumFacing facing) {
        return true;
    }

    /**
     * Import interface: tubes cannot pull from us - we are a sink.
     */
    @Override
    public boolean canOutputTo(EnumFacing facing) {
        return false;
    }

    /**
     * We are connectable from all sides.
     */
    @Override
    public boolean isConnectable(EnumFacing facing) {
        return true;
    }

    /**
     * Set the suction type (not used - we manage our own suction).
     */
    @Override
    public void setSuction(Aspect aspect, int amount) {
        // We manage our own suction based on filters
    }

    /**
     * Return amount of stored essentia for tube transport queries.
     * <p>
     * For import interfaces (sinks), this reports what we have stored.
     * The tube network uses this along with getEssentiaType() to know what's available.
     * Since we're a sink, this is less important than for sources.
     */
    @Override
    public int getEssentiaAmount(EnumFacing facing) {
        // Return amount of first stored type (if any)
        Aspect type = this.logic.getStoredEssentiaType();
        if (type == null) return 0;

        return this.logic.getEssentiaCount(type);
    }

    /**
     * Return the stored essentia type for tube transport queries.
     * <p>
     * For import interfaces (sinks), this reports what we have stored.
     * Note: This is different from getSuctionType() which indicates what we WANT.
     */
    @Override
    public Aspect getEssentiaType(EnumFacing facing) {
        return this.logic.getStoredEssentiaType();
    }

    /**
     * Add essentia from tube transport.
     * Import interface: we are a sink, tubes push to us.
     */
    @Override
    public int addEssentia(Aspect aspect, int amount, EnumFacing facing) {
        return this.logic.addToContainer(aspect, amount);
    }

    /**
     * Take essentia for tube transport.
     * Import interface: we are a sink, tubes cannot pull from us.
     */
    @Override
    public int takeEssentia(Aspect aspect, int amount, EnumFacing facing) {
        return 0;
    }
}
