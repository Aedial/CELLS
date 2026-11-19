package com.cells.profiling;

import javax.annotation.Nullable;

import appeng.api.storage.ISaveProvider;


/**
 * Access to the container that owns a CELLS cell's measured work.
 */
public interface ICellProfilingInventory {

    @Nullable
    ISaveProvider getProfilingContainer();
}
