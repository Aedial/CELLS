package com.cells.api;

import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.util.EnumFacing;

import net.minecraftforge.items.IItemHandler;


/**
 * API contract for CELLS interface views.
 */
public interface IInterfaceHost extends IFilterHost {

    /**
     * Resource channel represented by this interface view.
     */
    @Nonnull
    ResourceType getResourceType();

    /**
     * Whether this view exports from the ME network into adjacent targets.
     */
    boolean isExport();

    /**
     * Whether this host is part of a paired import/export I/O view and needs
     * direction-based disambiguation when displayed.
     */
    boolean isDirectionalView();

    /**
     * Stable facing used when a single side must be selected.
     */
    @Nonnull
    EnumFacing getPrimaryFacing();

    /**
     * All facings currently exposed by this interface view.
     */
    @Nonnull
    Collection<EnumFacing> getTargetFacings();

    /**
     * Preview entries visible through the primary facing.
     */
    @Nonnull
    List<ResourcePreviewEntry> getPreviewEntries(int limit);

    /**
     * Preview entries visible through a specific facing.
     */
    @Nonnull
    List<ResourcePreviewEntry> getPreviewEntries(@Nonnull EnumFacing facing, int limit);

    /**
     * Whether this host represents one resource type within a multi-type combined interface
     * and needs a resource-type prefix label when displayed.
     * Returns {@code false} by default (single-type and IO interfaces are not type-labeled).
     */
    default boolean isTypeLabeled() {
        return false;
    }

    /**
     * Upgrade inventory for this specific interface direction, or {@code null} if not
     * applicable. Used by IO interfaces so each direction's upgrades are accessible
     * independently via the terminal.
     * Returns {@code null} by default; single-direction machines expose upgrades via
     * {@link IUpgradeable} on the machine object instead.
     */
    @Nullable
    default IItemHandler getUpgradeInventory() {
        return null;
    }
}