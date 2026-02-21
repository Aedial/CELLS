package com.cells.blocks.importinterface;


/**
 * Shared interface for Import Interface host tile entities (item and fluid variants).
 * Provides the common contract needed by sub-GUIs (MaxSlotSize, PollingRate)
 * and network packets to operate without knowing the concrete tile type.
 * <p>
 * All implementations must also be {@link net.minecraft.tileentity.TileEntity}
 * subclasses for compatibility with AE2's container system.
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
}
