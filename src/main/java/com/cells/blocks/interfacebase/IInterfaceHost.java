package com.cells.blocks.interfacebase;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import appeng.api.util.AEPartLocation;
import appeng.util.SettingsFrom;


/**
 * Base interface for all Interface host variants (item/fluid, import/export, block/part).
 * Provides the common contract needed by sub-GUIs (MaxSlotSize, PollingRate)
 * and network packets to operate without knowing the concrete host type.
 * <p>
 * Implementations can be either {@link net.minecraft.tileentity.TileEntity}
 * subclasses or {@link appeng.api.parts.IPart} implementations.
 */
public interface IInterfaceHost {

    int getMaxSlotSize();

    void setMaxSlotSize(int size);

    int getPollingRate();

    void setPollingRate(int ticks);

    /**
     * @return true if this is an export interface, false if import.
     */
    boolean isExport();

    /**
     * @return the GUI ID of the main interface GUI, used by sub-GUIs to navigate back.
     */
    int getMainGuiId();

    /**
     * @return the lang key for the main GUI title, used by sub-GUIs for the back button tooltip.
     */
    String getGuiTitleLangKey();

    /**
     * @return the position of this host in the world.
     */
    BlockPos getHostPos();

    /**
     * @return an ItemStack representing this host, for display in the back button.
     */
    ItemStack getBackButtonStack();

    /**
     * @return the part side if this host is a part, or null if this is a block.
     */
    @Nullable
    default AEPartLocation getPartSide() {
        return null;
    }

    /**
     * @return true if this host is a part (on a cable), false if it's a block.
     */
    default boolean isPart() {
        return getPartSide() != null;
    }

    /**
     * Download settings to NBT for memory cards and dismantling.
     */
    NBTTagCompound downloadSettings(SettingsFrom from);

    /**
     * Download settings with filters to NBT for memory card + keybind.
     * Does NOT include upgrades (those stay in the source interface).
     */
    NBTTagCompound downloadSettingsWithFilter();
}
