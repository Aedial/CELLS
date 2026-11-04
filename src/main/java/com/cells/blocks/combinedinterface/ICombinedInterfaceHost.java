package com.cells.blocks.combinedinterface;

import java.util.List;

import javax.annotation.Nullable;

import com.cells.blocks.interfacebase.IInterfaceHost;
import com.cells.blocks.interfacebase.IInterfaceLogic;
import com.cells.blocks.interfacebase.item.ItemInterfaceLogic;
import com.cells.blocks.interfacebase.fluid.FluidInterfaceLogic;
import com.cells.blocks.interfacebase.managers.InterfaceRatioHandler;
import com.cells.network.sync.ResourceType;


/**
 * Interface for combined interface hosts (both tile and part).
 * <p>
 * A combined interface wraps multiple resource-specific logics (item, fluid, gas, essentia)
 * behind a single block/part. It shares one upgrade inventory across all logics and
 * provides tab-based GUI access to each resource type.
 * <p>
 * Due to Java's type erasure, the combined host CANNOT implement both IItemInterfaceHost
 * and IFluidInterfaceHost (both extend IFilterableInterfaceHost with different type parameters).
 * Instead, typed access is provided through dedicated logic accessors.
 */
public interface ICombinedInterfaceHost extends IInterfaceHost {

    public InterfaceRatioHandler getRatioHandler();

    /**
     * @return The item interface logic (always present).
     */
    ItemInterfaceLogic getItemLogic();

    /**
     * @return The fluid interface logic (always present).
     */
    FluidInterfaceLogic getFluidLogic();

    /**
     * Get the gas interface logic.
     * Only available when MekanismEnergistics is loaded.
     *
     * @return The gas logic, or null if MekanismEnergistics is not loaded
     */
    @Nullable
    IInterfaceLogic getGasLogic();

    /**
     * Get the essentia interface logic.
     * Only available when ThaumicEnergistics is loaded.
     *
     * @return The essentia logic, or null if ThaumicEnergistics is not loaded
     */
    @Nullable
    IInterfaceLogic getEssentiaLogic();

    /**
     * Get all loaded logic instances.
     * Always contains at least item and fluid logics. May also include gas and/or essentia.
     */
    List<IInterfaceLogic> getAllLogics();

    /**
     * Get the logic for a specific resource type.
     *
     * @return The logic, or null if the type's mod is not loaded
     */
    @Nullable
    IInterfaceLogic getLogicForType(ResourceType type);

    /**
     * Get the currently active tab resource type.
     * This state is transient (not saved to NBT) and used by the container/GUI for display.
     */
    ResourceType getActiveTab();

    /**
     * Set the active tab resource type.
     * Will be clamped to only available tabs (loaded mods).
     */
    void setActiveTab(ResourceType tab);

    /**
     * Get the list of available tabs (only types whose mods are loaded).
     * Always contains ITEM and FLUID. May also contain GAS and/or ESSENTIA.
     */
    List<ResourceType> getAvailableTabs();
}
