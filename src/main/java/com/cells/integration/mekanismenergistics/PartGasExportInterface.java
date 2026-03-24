package com.cells.integration.mekanismenergistics;

import java.util.EnumSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;

import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import appeng.tile.inventory.AppEngInternalInventory;

import com.mekeng.github.common.me.data.IAEGasStack;

import mekanism.api.gas.GasStack;
import mekanism.common.capabilities.Capabilities;

import com.cells.Tags;
import com.cells.parts.AbstractInterfacePart;


/**
 * Part version of the Gas Export Interface.
 * Can be placed on cables and behaves identically to the block version.
 * <p>
 * Business logic is delegated to {@link GasInterfaceLogic} to avoid code
 * duplication with tile and import variants.
 */
public class PartGasExportInterface extends AbstractInterfacePart<GasInterfaceLogic>
        implements IGasInterfaceHost, GasInterfaceLogic.Host {

    private static final String prefix = "part/export_interface/gas/";

    public static final ResourceLocation MODEL_BASE = new ResourceLocation(Tags.MODID, prefix + "base");

    @PartModels
    public static final PartModel MODELS_OFF = new PartModel(MODEL_BASE, new ResourceLocation(Tags.MODID, prefix + "off"));

    @PartModels
    public static final PartModel MODELS_ON = new PartModel(MODEL_BASE, new ResourceLocation(Tags.MODID, prefix + "on"));

    @PartModels
    public static final PartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, new ResourceLocation(Tags.MODID, prefix + "has_channel"));

    public PartGasExportInterface(final ItemStack is) {
        super(is);
        setLogic(new GasInterfaceLogic(this));
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
        return "tile.cells.export_interface.gas";
    }

    @Override
    public boolean isExport() {
        return true;
    }

    @Override
    public int getMainGuiId() {
        return GasInterfaceGuiHandler.GUI_PART_GAS_EXPORT_INTERFACE;
    }

    // ============================== IGasInterfaceHost delegation ==============================

    @Override
    public AppEngInternalInventory getUpgradeInventory() {
        return this.logic.getUpgradeInventory();
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
    public boolean isTankEmpty(int slot) {
        return this.logic.isTankEmpty(slot);
    }

    @Nullable
    @Override
    public IAEGasStack getFilterGas(int slot) {
        return this.logic.getFilterGas(slot);
    }

    @Override
    public void setFilterGas(int slot, @Nullable IAEGasStack gas) {
        this.logic.setFilterGas(slot, gas);
    }

    @Nullable
    @Override
    public GasStack getGasInTank(int slot) {
        return this.logic.getGasInTank(slot);
    }

    @Override
    public void setGasInTank(int slot, @Nullable GasStack gas) {
        this.logic.setGasInTank(slot, gas);
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

    @Override
    public GasStack drainGasFromTank(int slot, int maxDrain, boolean doDrain) {
        return this.logic.drainGasFromTank(slot, maxDrain, doDrain);
    }

    // ============================== IFilterableInterfaceHost delegation ==============================

    @Override
    public boolean isInFilter(@Nonnull GasStackKey key) {
        return this.logic.isInFilter(key);
    }

    @Override
    public int findSlotByKey(@Nonnull GasStackKey key) {
        return this.logic.findSlotByKey(key);
    }

    @Override
    public int addToFirstAvailableSlot(@Nonnull IAEGasStack stack) {
        return this.logic.addToFirstAvailableSlotAE(stack);
    }

    // ============================== Capability handling ==============================

    @Override
    public boolean hasCapability(Capability<?> capability) {
        if (capability == Capabilities.GAS_HANDLER_CAPABILITY) return true;
        return super.hasCapability(capability);
    }

    @Override
    public <T> T getCapability(Capability<T> capability) {
        if (capability == Capabilities.GAS_HANDLER_CAPABILITY) {
            return Capabilities.GAS_HANDLER_CAPABILITY.cast(this.logic.getExternalHandler());
        }
        return super.getCapability(capability);
    }

    // ============================== Utility methods ==============================

    public EnumSet<EnumFacing> getTargets() {
        return EnumSet.of(this.getSide().getFacing());
    }

    public TileEntity getTileEntity() {
        return this.getHost().getTile();
    }
}
