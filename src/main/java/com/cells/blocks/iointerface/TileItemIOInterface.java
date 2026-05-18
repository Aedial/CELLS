package com.cells.blocks.iointerface;

import javax.annotation.Nullable;

import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;

import appeng.capabilities.Capabilities;

import com.cells.blocks.interfacebase.item.ItemInterfaceLogic;
import com.cells.gui.CellsGuiHandler;
import com.cells.network.sync.ResourceType;
import com.cells.util.DirectionalCompositeItemHandler;


/**
 * Tile entity for the Item I/O Interface block.
 * Combines an Item Import Interface and an Item Export Interface in a single block
 * with direction-switching tabs.
 */
public class TileItemIOInterface extends AbstractIOInterfaceTile<ItemInterfaceLogic> {

    /**
     * Typed host wrapper for ItemInterfaceLogic (marker interface, no extra methods).
     */
    private class ItemDirectionHost extends DirectionHost implements ItemInterfaceLogic.Host {
        ItemDirectionHost(boolean export) { super(export); }
    }

    public TileItemIOInterface() {
        ItemDirectionHost importHost = new ItemDirectionHost(false);
        ItemDirectionHost exportHost = new ItemDirectionHost(true);

        ItemInterfaceLogic importLogic = new ItemInterfaceLogic(importHost);
        ItemInterfaceLogic exportLogic = new ItemInterfaceLogic(exportHost);

        this.initLogics(importLogic, exportLogic);
    }

    @Override
    public ResourceType getResourceType() {
        return ResourceType.ITEM;
    }

    @Override
    public int getMainGuiId() {
        return CellsGuiHandler.GUI_ITEM_IO_INTERFACE;
    }

    // ============================== Capabilities ==============================

    // The composite capability routes insertItem to the import logic
    // and extractItem to the export logic, so that adjacent machines
    // can both push items in (import) and pull items out (export).

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return true;
        if (capability == Capabilities.ITEM_REPOSITORY_CAPABILITY) return true;
        return super.hasCapability(capability, facing);
    }

    @Override
    @Nullable
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(
                new DirectionalCompositeItemHandler(
                    this.importLogic.getExternalHandler(),
                    this.exportLogic.getExternalHandler()
                )
            );
        }
        if (capability == Capabilities.ITEM_REPOSITORY_CAPABILITY) {
            // Use the import logic's repository for item lookup (both are equivalent for reading)
            return Capabilities.ITEM_REPOSITORY_CAPABILITY.cast(this.importLogic.getItemRepository());
        }
        return super.getCapability(capability, facing);
    }
}
