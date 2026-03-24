package com.cells.network.sync;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;


/**
 * Interface for containers that support quick-add filter operations.
 * <p>
 * Implemented by both creative cell containers and interface containers
 * to allow a unified {@link PacketQuickAddFilter} to add resources to filters.
 */
public interface IQuickAddFilterContainer {

    /**
     * Get the resource type this container handles.
     */
    ResourceType getQuickAddResourceType();

    /**
     * Check if a resource is already in the filter.
     *
     * @param resource The resource to check (type depends on ResourceType)
     * @return true if the resource is already filtered
     */
    boolean isResourceInFilter(@Nonnull Object resource);

    /**
     * Add a resource to the first available filter slot.
     *
     * @param resource The resource to add (type depends on ResourceType)
     * @param player   The player adding the filter (for messages), may be null for silent add
     * @return true if the resource was added successfully
     */
    boolean quickAddToFilter(@Nonnull Object resource, @Nullable EntityPlayer player);

    /**
     * Get the type localization key for "not valid content" messages.
     * For example: "cells.type.fluid", "cells.type.gas".
     */
    String getTypeLocalizationKey();
}
