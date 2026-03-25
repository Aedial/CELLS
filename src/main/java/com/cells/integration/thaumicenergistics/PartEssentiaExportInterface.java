package com.cells.integration.thaumicenergistics;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import thaumcraft.api.aspects.Aspect;

import thaumicenergistics.api.EssentiaStack;

import appeng.items.parts.PartModels;
import appeng.parts.PartModel;

import com.cells.Tags;
import com.cells.parts.AbstractInterfacePart;


/**
 * Part version of the Essentia Export Interface.
 * Can be placed on cables and behaves identically to the block version.
 * <p>
 * <b>Important:</b> Unlike the block version, parts do NOT participate in the
 * Thaumcraft tube network. This is because {@code IEssentiaTransport} requires
 * a tile entity, and parts are attached to cables instead. Use the block version
 * if tube connectivity is required.
 * <p>
 * This part still works with:
 * <ul>
 *   <li>Storage buses pointing at the interface</li>
 *   <li>Direct adjacent machine interactions (if they check part capabilities)</li>
 *   <li>Manual essentia extraction via phials</li>
 * </ul>
 * <p>
 * Business logic is delegated to {@link EssentiaInterfaceLogic} to avoid code
 * duplication with tile and import variants.
 */
public class PartEssentiaExportInterface extends AbstractInterfacePart<EssentiaInterfaceLogic>
        implements IEssentiaInterfaceHost, EssentiaInterfaceLogic.Host {

    private static final String prefix = "part/export_interface/essentia/";

    public static final ResourceLocation MODEL_BASE = new ResourceLocation(Tags.MODID, prefix + "base");

    @PartModels
    public static final PartModel MODELS_OFF = new PartModel(MODEL_BASE, new ResourceLocation(Tags.MODID, prefix + "off"));

    @PartModels
    public static final PartModel MODELS_ON = new PartModel(MODEL_BASE, new ResourceLocation(Tags.MODID, prefix + "on"));

    @PartModels
    public static final PartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, new ResourceLocation(Tags.MODID, prefix + "has_channel"));

    public PartEssentiaExportInterface(final ItemStack is) {
        super(is);
        setLogic(new EssentiaInterfaceLogic(this));
    }

    @Override
    protected PartModel getModelOff() {
        return MODELS_OFF;
    }

    @Override
    protected PartModel getModelOn() {
        return MODELS_ON;
    }

    @Override
    protected PartModel getModelHasChannel() {
        return MODELS_HAS_CHANNEL;
    }

    @Override
    protected String getMemoryCardName() {
        return "tile.cells.export_interface.essentia";
    }

    @Override
    public boolean isExport() {
        return true;
    }

    @Override
    public int getMainGuiId() {
        return EssentiaInterfaceGuiHandler.GUI_PART_ESSENTIA_EXPORT_INTERFACE;
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
        // Export interface does not accept essentia (logic handles direction check)
        return this.logic.addToContainer(aspect, amount);
    }

    @Override
    public int takeEssentiaAmount(Aspect aspect, int amount) {
        // Export interface provides essentia to external sources (logic handles direction check)
        return this.logic.takeEssentiaAmount(aspect, amount);
    }

    @Override
    public int getEssentiaCount(Aspect aspect) {
        return this.logic.getEssentiaCount(aspect);
    }
}
