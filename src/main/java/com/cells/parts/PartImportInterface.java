package com.cells.parts;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.capabilities.Capabilities;

import appeng.items.parts.PartModels;
import appeng.parts.PartModel;

import com.cells.Tags;
import com.cells.blocks.interfacebase.item.IItemInterfaceHost;
import com.cells.blocks.interfacebase.item.ItemInterfaceLogic;
import com.cells.gui.CellsGuiHandler;


/**
 * Part version of the Import Interface for items.
 * Can be placed on cables and behaves identically to the block version.
 * <p>
 * Business logic is delegated to {@link ItemInterfaceLogic} to avoid code
 * duplication with tile and export variants.
 */
public class PartImportInterface extends AbstractInterfacePart<ItemInterfaceLogic>
        implements IItemInterfaceHost, ItemInterfaceLogic.Host {

    private static final String prefix = "part/import_interface/item/";

    public static final ResourceLocation MODEL_BASE = new ResourceLocation(Tags.MODID, prefix + "base");

    @PartModels
    public static final PartModel MODELS_OFF = new PartModel(MODEL_BASE, new ResourceLocation(Tags.MODID, prefix + "off"));

    @PartModels
    public static final PartModel MODELS_ON = new PartModel(MODEL_BASE, new ResourceLocation(Tags.MODID, prefix + "on"));

    @PartModels
    public static final PartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, new ResourceLocation(Tags.MODID, prefix + "has_channel"));

    public PartImportInterface(final ItemStack is) {
        super(is);
        setLogic(new ItemInterfaceLogic(this));
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
        // the 'item' variant doesn't have the type suffix
        return "tile.cells.import_interface";
    }

    @Override
    public boolean isExport() {
        return false;
    }

    @Override
    public int getMainGuiId() {
        return CellsGuiHandler.GUI_PART_IMPORT_INTERFACE;
    }

    @Override
    public IItemHandlerModifiable getStorageInventory() {
        return this.logic.getStorageInventory();
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return this.logic.isItemValidForSlot(slot, stack);
    }

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
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(this.logic.getExternalHandler());
        }
        if (Capabilities.ITEM_REPOSITORY_CAPABILITY != null
                && capability == Capabilities.ITEM_REPOSITORY_CAPABILITY) {
            return Capabilities.ITEM_REPOSITORY_CAPABILITY.cast(this.logic.getItemRepository());
        }
        return super.getCapability(capability);
    }
}
