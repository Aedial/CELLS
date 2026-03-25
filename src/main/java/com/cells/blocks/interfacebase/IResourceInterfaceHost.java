package com.cells.blocks.interfacebase;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import appeng.api.storage.data.IAEStack;


/**
 * Extended interface for resource-based interface hosts (Fluid, Gas, Item, Essentia, etc.).
 * These hosts use {@link IResourceInterfaceLogic} for filter and storage operations.
 * <p>
 * This interface provides default implementations for all filter/storage methods
 * by delegating to the typed logic instance. Concrete type-specific interfaces
 * (IFluidInterfaceHost, IGasInterfaceHost, IItemInterfaceHost) only need to provide
 * type-specific methods like tank/inventory access.
 *
 * @param <AE> The AE stack type (IAEFluidStack, IAEGasStack, IAEItemStack)
 * @param <K>  The key type for hashable lookups (FluidStackKey, GasStackKey, ItemStackKey)
 */
public interface IResourceInterfaceHost<AE extends IAEStack<AE>, K>
    extends IFilterableInterfaceHost<AE, K> {

    // ================================= Logic Access (narrowed return type) =================================

    /**
     * Get the typed logic instance.
     * This narrows the return type from IInterfaceLogic to enable type-safe defaults.
     */
    @Override
    @Nonnull
    IResourceInterfaceLogic<AE, K> getInterfaceLogic();

    // ================================= IFilterableInterfaceHost Defaults =================================

    @Override
    @Nullable
    default AE getFilter(int slot) {
        return getInterfaceLogic().getFilter(slot);
    }

    @Override
    default void setFilter(int slot, @Nullable AE stack) {
        getInterfaceLogic().setFilter(slot, stack);
    }

    @Override
    default boolean isStorageEmpty(int slot) {
        return getInterfaceLogic().isStorageEmpty(slot);
    }

    @Override
    default boolean isInFilter(@Nonnull K key) {
        return getInterfaceLogic().isInFilter(key);
    }

    @Override
    default int findSlotByKey(@Nonnull K key) {
        return getInterfaceLogic().findSlotByKey(key);
    }

    @Override
    default int addToFirstAvailableSlot(@Nonnull AE stack) {
        return getInterfaceLogic().addToFirstAvailableSlotAE(stack);
    }
}
