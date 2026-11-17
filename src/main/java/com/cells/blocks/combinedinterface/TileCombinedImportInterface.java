package com.cells.blocks.combinedinterface;

import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.common.Optional;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;
import thaumcraft.api.aspects.IEssentiaTransport;

import com.cells.gui.CellsGuiHandler;
import com.cells.integration.thaumicenergistics.CombinedInterfaceEssentiaHelper;
import com.cells.integration.thaumicenergistics.EssentiaInterfaceLogic;


/**
 * Tile entity for the Combined Import Interface block.
 * Combines item, fluid, gas, and essentia import interfaces into a single block.
 * All resource types share the same upgrade slots and ME network connection.
 * <p>
 * Implements {@link IAspectContainer} and {@link IEssentiaTransport} (via @Optional)
 * so that ThaumicEnergistics and Thaumcraft recognize this block as an essentia sink.
 * All Thaumcraft interface methods delegate to the essentia logic through the
 * {@link CombinedInterfaceEssentiaHelper} to safely isolate Thaumcraft type references.
 */
@Optional.InterfaceList({
    @Optional.Interface(iface = "thaumcraft.api.aspects.IAspectContainer", modid = "thaumcraft"),
    @Optional.Interface(iface = "thaumcraft.api.aspects.IEssentiaTransport", modid = "thaumcraft")
})
public class TileCombinedImportInterface extends AbstractCombinedInterfaceTile
        implements IAspectContainer, IEssentiaTransport {

    @Override
    public boolean isExport() {
        return false;
    }

    @Override
    public int getMainGuiId() {
        return CellsGuiHandler.GUI_COMBINED_IMPORT_INTERFACE;
    }

    // ============================== IAspectContainer ==============================

    @Optional.Method(modid = "thaumcraft")
    @Override
    public AspectList getAspects() {
        return CombinedInterfaceEssentiaHelper.getAspects(this.essentiaLogic);
    }

    @Optional.Method(modid = "thaumcraft")
    @Override
    public void setAspects(AspectList aspects) {
        // Not implemented - we manage our own storage
    }

    @Optional.Method(modid = "thaumcraft")
    @Override
    public boolean doesContainerContain(AspectList aspects) {
        return CombinedInterfaceEssentiaHelper.doesContainerContain(this.essentiaLogic, aspects);
    }

    @Optional.Method(modid = "thaumcraft")
    @Override
    public boolean takeFromContainer(AspectList aspects) {
        // Import interface: we are a SINK, not a source
        return false;
    }

    @Optional.Method(modid = "thaumcraft")
    @Override
    public boolean takeFromContainer(Aspect aspect, int amount) {
        // Import interface: we are a SINK, not a source
        return false;
    }

    @Optional.Method(modid = "thaumcraft")
    @Override
    public int containerContains(Aspect aspect) {
        return CombinedInterfaceEssentiaHelper.containerContains(this.essentiaLogic, aspect);
    }

    @Optional.Method(modid = "thaumcraft")
    @Override
    public boolean doesContainerAccept(Aspect aspect) {
        return CombinedInterfaceEssentiaHelper.doesContainerAccept(this.essentiaLogic, aspect);
    }

    @Optional.Method(modid = "thaumcraft")
    @Override
    public int addToContainer(Aspect aspect, int amount) {
        return CombinedInterfaceEssentiaHelper.addToContainer(this.essentiaLogic, aspect, amount);
    }

    @Optional.Method(modid = "thaumcraft")
    @Override
    public boolean doesContainerContainAmount(Aspect aspect, int amount) {
        return CombinedInterfaceEssentiaHelper.doesContainerContainAmount(this.essentiaLogic, aspect, amount);
    }

    // ============================== IEssentiaTransport ==============================

    /**
     * Import interface has HIGH suction minimum - we are an essentia sink.
     */
    @Optional.Method(modid = "thaumcraft")
    @Override
    public int getMinimumSuction() {
        return EssentiaInterfaceLogic.IMPORT_SUCTION;
    }

    /**
     * Import interface has HIGH suction, we are a sink that wants essentia.
     */
    @Optional.Method(modid = "thaumcraft")
    @Override
    public int getSuctionAmount(EnumFacing facing) {
        return EssentiaInterfaceLogic.IMPORT_SUCTION;
    }

    /**
     * Return the aspect type we want to receive (first filter, or null for any).
     */
    @Optional.Method(modid = "thaumcraft")
    @Override
    public Aspect getSuctionType(EnumFacing facing) {
        return CombinedInterfaceEssentiaHelper.getSuctionType(this.essentiaLogic);
    }

    /**
     * Import interface: tubes can push essentia to us, we are a sink.
     */
    @Optional.Method(modid = "thaumcraft")
    @Override
    public boolean canInputFrom(EnumFacing facing) {
        return true;
    }

    /**
     * Import interface: tubes cannot pull from us - we are a sink.
     */
    @Optional.Method(modid = "thaumcraft")
    @Override
    public boolean canOutputTo(EnumFacing facing) {
        return false;
    }

    /**
     * We are connectable from all sides.
     */
    @Optional.Method(modid = "thaumcraft")
    @Override
    public boolean isConnectable(EnumFacing facing) {
        return true;
    }

    /**
     * Set the suction type (not used - we manage our own suction).
     */
    @Optional.Method(modid = "thaumcraft")
    @Override
    public void setSuction(Aspect aspect, int amount) {
        // We manage our own suction based on filters
    }

    /**
     * Return amount of stored essentia for tube transport queries.
     */
    @Optional.Method(modid = "thaumcraft")
    @Override
    public int getEssentiaAmount(EnumFacing facing) {
        return CombinedInterfaceEssentiaHelper.getStoredEssentiaAmount(this.essentiaLogic);
    }

    /**
     * Return the stored essentia type for tube transport queries.
     */
    @Optional.Method(modid = "thaumcraft")
    @Override
    public Aspect getEssentiaType(EnumFacing facing) {
        return CombinedInterfaceEssentiaHelper.getStoredEssentiaType(this.essentiaLogic);
    }

    /**
     * Add essentia from tube transport. Import interface: we are a sink, tubes push to us.
     */
    @Optional.Method(modid = "thaumcraft")
    @Override
    public int addEssentia(Aspect aspect, int amount, EnumFacing facing) {
        return CombinedInterfaceEssentiaHelper.addEssentia(this.essentiaLogic, aspect, amount);
    }

    /**
     * Take essentia for tube transport. Import interface: we are a sink, tubes cannot pull from us.
     */
    @Optional.Method(modid = "thaumcraft")
    @Override
    public int takeEssentia(Aspect aspect, int amount, EnumFacing facing) {
        return 0;
    }
}
