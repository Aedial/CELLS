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
 * Tile entity for the Essentia Export Interface block.
 * Provides filter slots (aspect-based filters) and internal essentia storage.
 * Pulls essentia from the ME network to fill storage slots matching filters.
 * External machines can extract essentia from the storage.
 * <p>
 * Implements {@link IAspectContainer} for adjacent machines to detect essentia storage.
 * Implements {@link IEssentiaTransport} to participate in the Thaumcraft tube network.
 * <p>
 * As an <b>Export Interface</b> (SOURCE), this block:
 * <ul>
 *   <li>Has LOW suction - tubes will PULL essentia FROM this block</li>
 *   <li>Provides essentia via takeFromContainer() to tubes</li>
 *   <li>Does NOT accept essentia via addToContainer() from tubes</li>
 * </ul>
 * <p>
 * Business logic is delegated to {@link EssentiaInterfaceLogic} to avoid code
 * duplication with part and import variants.
 */
public class TileEssentiaExportInterface extends AbstractInterfaceTile<EssentiaInterfaceLogic>
        implements IEssentiaInterfaceHost, EssentiaInterfaceLogic.Host, IAspectContainer, IEssentiaTransport {

    public TileEssentiaExportInterface() {
        this.initLogic(new EssentiaInterfaceLogic(this));
    }

    @Override
    public boolean isExport() {
        return true;
    }

    @Override
    public int getMainGuiId() {
        return EssentiaInterfaceGuiHandler.GUI_ESSENTIA_EXPORT_INTERFACE;
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
    public EssentiaStack drainEssentiaFromSlot(int slot, int maxDrain, boolean doDrain) {
        return this.logic.drainEssentiaFromSlot(slot, maxDrain, doDrain);
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

    @Override
    public int takeEssentiaAmount(Aspect aspect, int amount) {
        return this.logic.takeEssentiaAmount(aspect, amount);
    }

    @Override
    public int getEssentiaCount(Aspect aspect) {
        return this.logic.getEssentiaCount(aspect);
    }

    // ============================== IAspectContainer Implementation ==============================

    @Override
    public AspectList getAspects() {
        return this.logic.getAspects();
    }

    @Override
    public void setAspects(AspectList aspects) {
        // Not implemented, we manage our own storage
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
        // Export interface: we are a source, tubes can pull from us
        // First check if we have all aspects
        for (Aspect aspect : aspects.getAspects()) {
            int needed = aspects.getAmount(aspect);
            if (!this.logic.doesContainerContainAmount(aspect, needed)) {
                return false;
            }
        }

        // Take all aspects
        for (Aspect aspect : aspects.getAspects()) {
            int needed = aspects.getAmount(aspect);
            this.logic.takeEssentiaAmount(aspect, needed);
        }
        return true;
    }

    @Override
    public boolean takeFromContainer(Aspect aspect, int amount) {
        // Export interface: we are a source, tubes can pull from us
        int taken = this.logic.takeEssentiaAmount(aspect, amount);
        return taken >= amount;
    }

    @Override
    public int containerContains(Aspect aspect) {
        return this.logic.getEssentiaCount(aspect);
    }

    // ============================== IEssentiaTransport Implementation ==============================

    /**
     * Export interface has LOW suction - we are an essentia source.
     */
    @Override
    public int getMinimumSuction() {
        return EssentiaInterfaceLogic.EXPORT_SUCTION;
    }

    /**
     * Export interface has LOW suction - we are a source that provides essentia.
     */
    @Override
    public int getSuctionAmount(EnumFacing facing) {
        return EssentiaInterfaceLogic.EXPORT_SUCTION;
    }

    /**
     * Return the aspect type we have to offer (first stored type, or null for any).
     */
    @Override
    public Aspect getSuctionType(EnumFacing facing) {
        // As a source, we advertise what we have
        return this.logic.getSuctionType();
    }

    /**
     * Export interface: tubes CANNOT push essentia to us, we are a source.
     */
    @Override
    public boolean canInputFrom(EnumFacing facing) {
        return false;
    }

    /**
     * Export interface: tubes CAN pull essentia from us, we are a source.
     */
    @Override
    public boolean canOutputTo(EnumFacing facing) {
        return true;
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
     * Return total essentia stored.
     */
    @Override
    public int getEssentiaAmount(EnumFacing facing) {
        AspectList aspects = this.logic.getAspects();
        long total = 0;
        for (Aspect aspect : aspects.getAspects()) {
            total += aspects.getAmount(aspect);
        }
        // Clamp to avoid overflow
        return (int) Math.min(total, Integer.MAX_VALUE);
    }

    /**
     * Return the essentia type we have available.
     * Export interfaces report what they have to offer (stored essentia).
     */
    @Override
    public Aspect getEssentiaType(EnumFacing facing) {
        return this.logic.getStoredEssentiaType();
    }

    /**
     * Add essentia from tube transport.
     * Export interface: we are a source, tubes cannot push to us.
     */
    @Override
    public int addEssentia(Aspect aspect, int amount, EnumFacing facing) {
        return 0;
    }

    /**
     * Take essentia for tube transport.
     * Export interface: we are a source, tubes pull from us.
     */
    @Override
    public int takeEssentia(Aspect aspect, int amount, EnumFacing facing) {
        return this.logic.takeEssentiaAmount(aspect, amount);
    }
}
