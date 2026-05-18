package com.cells.parts;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;

import appeng.capabilities.Capabilities;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;

import com.cells.blocks.interfacebase.item.ItemInterfaceLogic;
import com.cells.gui.CellsGuiHandler;
import com.cells.network.sync.ResourceType;
import com.cells.util.DirectionalCompositeItemHandler;


/**
 * Part version of the Item I/O Interface.
 * Combines item import and export capabilities in a single cable part
 * with direction-switching tabs.
 */
public class PartItemIOInterface extends AbstractIOInterfacePart<ItemInterfaceLogic> {

    private static final String prefix = "part/io_interface/item/";
    private static final Object[] MODELS = PartModelsHelper.createInterfaceModels(prefix);

    @PartModels
    public static final PartModel MODELS_OFF = (PartModel) MODELS[1];

    @PartModels
    public static final PartModel MODELS_ON = (PartModel) MODELS[2];

    @PartModels
    public static final PartModel MODELS_HAS_CHANNEL = (PartModel) MODELS[3];

    /**
     * Typed host wrapper for ItemInterfaceLogic (marker interface, no extra methods).
     */
    private class ItemDirectionHost extends DirectionHost implements ItemInterfaceLogic.Host {
        ItemDirectionHost(boolean export) { super(export); }
    }

    public PartItemIOInterface(final ItemStack is) {
        super(is);

        ItemDirectionHost importHost = new ItemDirectionHost(false);
        ItemDirectionHost exportHost = new ItemDirectionHost(true);

        this.initLogics(
            new ItemInterfaceLogic(importHost),
            new ItemInterfaceLogic(exportHost)
        );
    }

    @Override
    protected PartModel getModelOff() { return MODELS_OFF; }

    @Override
    protected PartModel getModelOn() { return MODELS_ON; }

    @Override
    protected PartModel getModelHasChannel() { return MODELS_HAS_CHANNEL; }

    @Override
    public ResourceType getResourceType() {
        return ResourceType.ITEM;
    }

    @Override
    public int getMainGuiId() {
        return CellsGuiHandler.GUI_PART_ITEM_IO_INTERFACE;
    }

    // ============================== Capabilities ==============================

    @Override
    public boolean hasCapability(Capability<?> capability) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return true;
        if (Capabilities.ITEM_REPOSITORY_CAPABILITY != null
                && capability == Capabilities.ITEM_REPOSITORY_CAPABILITY) return true;
        return super.hasCapability(capability);
    }

    @Override
    public <T> T getCapability(Capability<T> capability) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(
                new DirectionalCompositeItemHandler(
                    this.importLogic.getExternalHandler(),
                    this.exportLogic.getExternalHandler()
                )
            );
        }
        if (Capabilities.ITEM_REPOSITORY_CAPABILITY != null
                && capability == Capabilities.ITEM_REPOSITORY_CAPABILITY) {
            return Capabilities.ITEM_REPOSITORY_CAPABILITY.cast(this.importLogic.getItemRepository());
        }
        return super.getCapability(capability);
    }
}
