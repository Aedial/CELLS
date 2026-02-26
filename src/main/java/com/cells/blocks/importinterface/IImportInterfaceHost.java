package com.cells.blocks.importinterface;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import appeng.api.util.AEPartLocation;


/**
 * Shared interface for Import Interface host tile entities (item and fluid variants).
 * Provides the common contract needed by sub-GUIs (MaxSlotSize, PollingRate)
 * and network packets to operate without knowing the concrete tile type.
 * <p>
 * Implementations can be either {@link net.minecraft.tileentity.TileEntity}
 * subclasses or {@link appeng.api.parts.IPart} implementations.
 */
public interface IImportInterfaceHost {

    int getMaxSlotSize();

    void setMaxSlotSize(int size);

    int getPollingRate();

    void setPollingRate(int ticks);

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
}
