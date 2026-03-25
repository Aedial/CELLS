package com.cells.blocks.exportinterface;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import com.cells.blocks.interfacebase.AbstractInterfaceTile;
import com.cells.blocks.interfacebase.item.IItemInterfaceHost;
import com.cells.blocks.interfacebase.item.ItemInterfaceLogic;
import com.cells.gui.CellsGuiHandler;


/**
 * Tile entity for the Export Interface block.
 * Provides filter slots and storage slots (expandable with Capacity Cards).
 * Requests items from the ME network that match the filter configuration.
 * Exposes stored items for external extraction.
 * <p>
 * Business logic is delegated to {@link ItemInterfaceLogic} to avoid code
 * duplication with part and import variants.
 */
public class TileExportInterface extends AbstractInterfaceTile<ItemInterfaceLogic>
        implements IItemInterfaceHost, ItemInterfaceLogic.Host {

    public TileExportInterface() {
        this.initLogic(new ItemInterfaceLogic(this));
    }

    @Override
    public boolean isExport() {
        return true;
    }

    @Override
    public IItemHandlerModifiable getFilterInventory() {
        return this.logic.getFilterInventory();
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
    public int getMainGuiId() {
        return CellsGuiHandler.GUI_EXPORT_INTERFACE;
    }

    @Override
    protected void readLogicFromNBT(final NBTTagCompound data) {
        // Use isTile=true for legacy format support
        this.logic.readFromNBT(data, true);
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return true;
        return super.hasCapability(capability, facing);
    }

    @Override
    @Nullable
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(this.logic.getExternalHandler());
        }
        return super.getCapability(capability, facing);
    }
}
