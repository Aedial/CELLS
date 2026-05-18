package com.cells.api;

import java.util.List;

import javax.annotation.Nonnull;


/**
 * API contract for CELLS devices that expose one or more interface views.
 */
public interface IInterfaceProvider {

    /**
     * All interface views currently exposed by this device.
     */
    @Nonnull
    List<IInterfaceHost> getInterfaceHosts();
}