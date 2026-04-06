package com.cells.blocks.importinterface;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.capabilities.Capabilities;

import com.cells.blocks.interfacebase.AbstractInterfaceTile;
import com.cells.blocks.interfacebase.item.IItemInterfaceHost;
import com.cells.blocks.interfacebase.item.ItemInterfaceLogic;
import com.cells.gui.CellsGuiHandler;


/**
 * Tile entity for the Import Interface block.
 * Provides filter slots (ghost items) and storage slots (expandable with Capacity Cards).
 * Only accepts items that match the filter in the corresponding slot.
 * Automatically imports stored items into the ME network.
 * <p>
 * Business logic is delegated to {@link ItemInterfaceLogic} to avoid code
 * duplication with part and export variants.
 */
public class TileImportInterface extends AbstractInterfaceTile<ItemInterfaceLogic>
        implements IItemInterfaceHost, ItemInterfaceLogic.Host {

    public TileImportInterface() {
        this.initLogic(new ItemInterfaceLogic(this));
    }

    @Override
    public boolean isExport() {
        return false;
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
        return CellsGuiHandler.GUI_IMPORT_INTERFACE;
    }

    @Override
    protected void readLogicFromNBT(final NBTTagCompound data) {
        // Use isTile=true for legacy format support
        this.logic.readFromNBT(data, true);
    }

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
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(this.logic.getExternalHandler());
        }
        if (capability == Capabilities.ITEM_REPOSITORY_CAPABILITY) {
            return Capabilities.ITEM_REPOSITORY_CAPABILITY.cast(this.logic.getItemRepository());
        }
        return super.getCapability(capability, facing);
    }
}
