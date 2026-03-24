package com.cells.parts;

import java.util.EnumSet;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import appeng.tile.inventory.AppEngInternalInventory;

import com.cells.Tags;
import com.cells.blocks.interfacebase.item.IItemInterfaceHost;
import com.cells.blocks.interfacebase.item.ItemInterfaceLogic;
import com.cells.gui.CellsGuiHandler;
import com.cells.util.ItemStackKey;


/**
 * Part version of the Export Interface for items.
 * Can be placed on cables and behaves identically to the block version.
 * Requests items from the network and exposes them for extraction.
 * <p>
 * Business logic is delegated to {@link ItemInterfaceLogic} to avoid code
 * duplication with tile and import variants.
 */
public class PartExportInterface extends AbstractInterfacePart<ItemInterfaceLogic>
        implements IItemInterfaceHost, ItemInterfaceLogic.Host {

    private static final String prefix = "part/export_interface/item/";

    public static final ResourceLocation MODEL_BASE = new ResourceLocation(Tags.MODID, prefix + "base");

    @PartModels
    public static final PartModel MODELS_OFF = new PartModel(MODEL_BASE, new ResourceLocation(Tags.MODID, prefix + "off"));

    @PartModels
    public static final PartModel MODELS_ON = new PartModel(MODEL_BASE, new ResourceLocation(Tags.MODID, prefix + "on"));

    @PartModels
    public static final PartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, new ResourceLocation(Tags.MODID, prefix + "has_channel"));

    public PartExportInterface(final ItemStack is) {
        super(is);
        setLogic(new ItemInterfaceLogic(this));
    }

    // ============================== AbstractInterfacePart implementation ==============================

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
        return "tile.cells.export_interface";
    }

    @Override
    public boolean isExport() {
        return true;
    }

    @Override
    public int getMainGuiId() {
        return CellsGuiHandler.GUI_PART_EXPORT_INTERFACE;
    }

    // ============================== IItemInterfaceHost delegation ==============================

    @Override
    public IItemHandlerModifiable getFilterInventory() {
        return this.logic.getFilterInventory();
    }

    @Override
    public IItemHandlerModifiable getStorageInventory() {
        return this.logic.getStorageInventory();
    }

    @Override
    public AppEngInternalInventory getUpgradeInventory() {
        return this.logic.getUpgradeInventory();
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return this.logic.isItemValidForSlot(slot, stack);
    }

    @Override
    public void refreshFilterMap() {
        this.logic.refreshFilterMap();
    }

    @Override
    public void refreshUpgrades() {
        this.logic.refreshUpgrades();
    }

    @Override
    public boolean isValidUpgrade(ItemStack stack) {
        return this.logic.isValidUpgrade(stack);
    }

    @Override
    public int getMaxSlotSize() {
        return this.logic.getMaxSlotSize();
    }

    @Override
    public void setMaxSlotSize(int size) {
        this.logic.setMaxSlotSize(size);
    }

    @Override
    public int getPollingRate() {
        return this.logic.getPollingRate();
    }

    @Override
    public void setPollingRate(int ticks) {
        this.logic.setPollingRate(ticks);
    }

    public void setPollingRate(int ticks, EntityPlayer player) {
        this.logic.setPollingRate(ticks, player);
    }

    @Override
    public int getInstalledCapacityUpgrades() {
        return this.logic.getInstalledCapacityUpgrades();
    }

    @Override
    public int getTotalPages() {
        return this.logic.getTotalPages();
    }

    @Override
    public int getCurrentPage() {
        return this.logic.getCurrentPage();
    }

    @Override
    public void setCurrentPage(int page) {
        this.logic.setCurrentPage(page);
    }

    @Override
    public int getCurrentPageStartSlot() {
        return this.logic.getCurrentPageStartSlot();
    }

    @Override
    public boolean hasOverflowUpgrade() {
        return this.logic.hasOverflowUpgrade();
    }

    @Override
    public boolean hasTrashUnselectedUpgrade() {
        return this.logic.hasTrashUnselectedUpgrade();
    }

    // ============================== IFilterableInterfaceHost delegation ==============================

    @Override
    public boolean isInFilter(@Nonnull ItemStackKey key) {
        return this.logic.isInFilter(key);
    }

    @Override
    public int findSlotByKey(@Nonnull ItemStackKey key) {
        return this.logic.findSlotByKey(key);
    }

    @Override
    public int addToFirstAvailableSlot(@Nonnull ItemStack stack) {
        return this.logic.addToFirstAvailableSlot(stack);
    }

    // ============================== Capability handling ==============================

    @Override
    public boolean hasCapability(Capability<?> capability) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return true;
        return super.hasCapability(capability);
    }

    @Override
    public <T> T getCapability(Capability<T> capability) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(this.logic.getExternalHandler());
        }
        return super.getCapability(capability);
    }

    // ============================== Utility methods ==============================

    public EnumSet<EnumFacing> getTargets() {
        return EnumSet.of(this.getSide().getFacing());
    }
}
