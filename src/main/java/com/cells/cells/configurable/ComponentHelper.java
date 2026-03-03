package com.cells.cells.configurable;

import javax.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import com.cells.Cells;
import com.cells.Tags;


/**
 * Utility class for recognizing ME Storage Components and mapping them to their
 * byte capacities and storage channels.
 * <p>
 * Recognized components are loaded from a whitelist file
 * ({@code assets/cells/configurable_components.cfg}) at startup. Each entry maps
 * a registry name + metadata to a byte capacity, storage channel, and tier name.
 * <p>
 * Tier names are collected dynamically from the whitelist entries, allowing
 * modpack developers to add custom sizes beyond the default set. Note that
 * textures are only provided for: 1k, 4k, 16k, 64k, 256k, 1m, 4m, 16m, 64m, 256m, 1g, 2g.
 * Custom tier names will show no texture (black and purple checkerboard) but can still be used for storage.
 * <p>
 * <b>Warning:</b> No overflow protection is provided for custom components.
 * The underlying storage uses long values, so modpack developers should be
 * mindful of values approaching Long.MAX_VALUE (9.2 quintillion).
 * <p>
 * Supported components (via default whitelist):
 * - AE2 item components (1k, 4k, 16k, 64k)
 * - AE2 fluid components (1k, 4k, 16k, 64k)
 * - NAE2 item components (256k, 1m, 4m, 16m)
 * - NAE2 fluid components (256k, 1m, 4m, 16m)
 * - CrazyAE item components (256k, 1m, 4m, 16m, 64m, 256m, 1g, 2g)
 * - CrazyAE fluid components (256k, 1m, 4m, 16m, 64m, 256m, 1g, 2g)
 * - Thaumic Energistics essentia components (1k, 4k, 16k, 64k)
 * - Mekanism Energistics gas components (1k, 4k, 16k, 64k)
 */
public final class ComponentHelper {

    private static final String WHITELIST_RESOURCE = "/assets/" + Tags.MODID + "/configurable_components.cfg";
    public static final String WHITELIST_CONFIG_NAME = "configurable_components.cfg";

    /**
     * Whitelist map: "registryName@meta" -> ComponentInfo.
     * Populated once from the whitelist config file.
     */
    private static final Map<String, ComponentInfo> WHITELIST = new HashMap<>();

    /**
     * Tier names collected from whitelist entries, separated by channel type.
     * Each channel type has its own set of tier names in order of first appearance.
     */
    private static final Map<ChannelType, Set<String>> REGISTERED_TIER_NAMES_BY_CHANNEL = new EnumMap<>(ChannelType.class);

    static {
        // Initialize empty sets for each channel type
        for (ChannelType type : ChannelType.values()) {
            REGISTERED_TIER_NAMES_BY_CHANNEL.put(type, new LinkedHashSet<>());
        }
    }

    private ComponentHelper() {}

    // =====================
    // Whitelist loading
    // =====================

    /**
     * Load the component whitelist. Checks the Forge config directory first
     * for a user override ({@code config/configurable_components.cfg}), then
     * falls back to the bundled resource inside the JAR.
     * <p>
     * Called from {@link com.cells.Cells#preInit} after the config directory
     * is available.
     *
     * @param configDir The Forge config directory (may be null for fallback-only)
     */
    public static void loadWhitelist(@Nullable File configDir) {
        WHITELIST.clear();
        for (ChannelType type : ChannelType.values()) REGISTERED_TIER_NAMES_BY_CHANNEL.get(type).clear();

        // Try config directory override first
        if (configDir != null) {
            File override = new File(configDir, WHITELIST_CONFIG_NAME);
            if (override.isFile()) {
                Cells.LOGGER.info("Loading component whitelist override from {}", override.getAbsolutePath());
                try (InputStream is = Files.newInputStream(override.toPath())) {
                    parseWhitelist(is, override.getAbsolutePath());
                    return;
                } catch (Exception e) {
                    Cells.LOGGER.error("Failed to load whitelist override, falling back to bundled", e);
                }
            }
        }

        // Fall back to bundled resource inside the JAR
        try (InputStream is = ComponentHelper.class.getResourceAsStream(WHITELIST_RESOURCE)) {
            if (is == null) {
                Cells.LOGGER.error("Component whitelist not found at {}", WHITELIST_RESOURCE);
                return;
            }

            parseWhitelist(is, "(bundled) " + WHITELIST_RESOURCE);
        } catch (Exception e) {
            Cells.LOGGER.error("Failed to load component whitelist", e);
        }
    }

    /**
     * Parse whitelist entries from an InputStream.
     * Each non-comment, non-blank line has the format:
     * {@code registry_name@metadata = bytes,channel,tier_name}
     */
    private static void parseWhitelist(InputStream is, String sourceName) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            int lineNum = 0;

            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                // Format: registryName@meta = bytes,channel,tierName
                int eqIdx = line.indexOf('=');
                if (eqIdx < 0) {
                    Cells.LOGGER.warn("Malformed whitelist entry at line {}: {}", lineNum, line);
                    continue;
                }

                String key = line.substring(0, eqIdx).trim();
                String value = line.substring(eqIdx + 1).trim();
                String[] parts = value.split(",");
                if (parts.length != 3) {
                    Cells.LOGGER.warn("Malformed whitelist value at line {}: {}", lineNum, value);
                    continue;
                }

                try {
                    long bytes = Long.parseLong(parts[0].trim());
                    String channelName = parts[1].trim().toLowerCase();
                    String tierName = parts[2].trim().toLowerCase();
                    ChannelType channelType = ChannelType.fromConfigName(channelName);

                    if (channelType == null) {
                        Cells.LOGGER.warn("Unknown channel type at line {}: {}", lineNum, channelName);
                        continue;
                    }

                    WHITELIST.put(key, new ComponentInfo(bytes, channelType, tierName));
                    REGISTERED_TIER_NAMES_BY_CHANNEL.get(channelType).add(tierName);
                } catch (NumberFormatException e) {
                    Cells.LOGGER.warn("Invalid byte value at line {}: {}", lineNum, parts[0].trim());
                }
            }

            Cells.LOGGER.info("Loaded {} component whitelist entries from {}", WHITELIST.size(), sourceName);
        } catch (Exception e) {
            Cells.LOGGER.error("Failed to parse whitelist from {}", sourceName, e);
        }
    }

    // =====================
    // Tier name access
    // =====================

    /**
     * Get tier names registered for a specific channel type.
     *
     * @param channelType The channel type to get tiers for
     * @return Array of tier names for that channel (e.g., ["1k", "4k", "16k", ...])
     */
    public static String[] getRegisteredTierNames(ChannelType channelType) {
        Set<String> tiers = REGISTERED_TIER_NAMES_BY_CHANNEL.get(channelType);
        return tiers != null ? tiers.toArray(new String[0]) : new String[0];
    }

    /**
     * Get the map of all registered tier names by channel type.
     * This is useful for model registration where we need to iterate all channels.
     *
     * @return Map of channel type to tier names
     */
    public static Map<ChannelType, Set<String>> getRegisteredTierNamesByChannel() {
        return REGISTERED_TIER_NAMES_BY_CHANNEL;
    }

    /**
     * Get all valid component ItemStacks that currently exist in the game.
     * <p>
     * This method iterates through the whitelist and checks if each component's
     * item is registered in Forge. Only components whose items exist are returned.
     * This is useful for dynamically registering recipes only for available components.
     *
     * @return List of valid component ItemStacks (only for loaded mods/items)
     */
    public static List<ItemStack> getValidComponents() {
        List<ItemStack> validComponents = new ArrayList<>();

        for (String key : WHITELIST.keySet()) {
            // Parse "registryName@meta" format
            int atIdx = key.indexOf('@');
            if (atIdx < 0) continue;

            String registryName = key.substring(0, atIdx);
            int meta;
            try {
                meta = Integer.parseInt(key.substring(atIdx + 1));
            } catch (NumberFormatException e) {
                continue;
            }

            // Check if the item exists in the registry
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(registryName));
            if (item == null) continue;

            validComponents.add(new ItemStack(item, 1, meta));
        }

        return validComponents;
    }

    /**
     * Determine the component info for the given ItemStack.
     * Looks up the item's registry name and metadata in the whitelist.
     *
     * @param stack The ItemStack to identify
     * @return ComponentInfo if recognized, null otherwise
     */
    @Nullable
    public static ComponentInfo getComponentInfo(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        Item item = stack.getItem();
        ResourceLocation regName = item.getRegistryName();
        if (regName == null) return null;

        String key = regName + "@" + stack.getMetadata();

        return WHITELIST.get(key);
    }

    /**
     * Get the component stored in a cell's NBT.
     *
     * @param cellStack The cell ItemStack
     * @return The component ItemStack, or empty if none
     */
    public static ItemStack getInstalledComponent(ItemStack cellStack) {
        NBTTagCompound tag = cellStack.getTagCompound();
        if (tag == null || !tag.hasKey("component")) return ItemStack.EMPTY;

        return new ItemStack(tag.getCompoundTag("component"));
    }

    /**
     * Store a component in a cell's NBT.
     *
     * @param cellStack The cell ItemStack
     * @param component The component to store, or empty to remove
     */
    public static void setInstalledComponent(ItemStack cellStack, ItemStack component) {
        NBTTagCompound tag = cellStack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            cellStack.setTagCompound(tag);
        }

        if (component == null || component.isEmpty()) {
            tag.removeTag("component");
        } else {
            NBTTagCompound componentTag = new NBTTagCompound();
            component.writeToNBT(componentTag);
            tag.setTag("component", componentTag);
        }
    }


    /**
     * Get the user-configured max per type from the cell's NBT.
     */
    public static long getMaxPerType(ItemStack cellStack) {
        NBTTagCompound tag = cellStack.getTagCompound();
        if (tag == null || !tag.hasKey("maxPerType")) return Long.MAX_VALUE;

        return tag.getLong("maxPerType");
    }

    /**
     * Set the user-configured max per type in the cell's NBT.
     * If the value is Long.MAX_VALUE (unlimited), the key is removed instead.
     */
    public static void setMaxPerType(ItemStack cellStack, long value) {
        NBTTagCompound tag = cellStack.getTagCompound();

        // Remove the key if the value is unlimited (default behavior)
        if (value == Long.MAX_VALUE) {
            if (tag != null) tag.removeTag("maxPerType");
            return;
        }

        if (tag == null) {
            tag = new NBTTagCompound();
            cellStack.setTagCompound(tag);
        }

        tag.setLong("maxPerType", value);
    }

    /**
     * Get the user-configured max types from the cell's NBT.
     * Returns Integer.MAX_VALUE if not set (unlimited by user, uses config value).
     */
    public static int getUserMaxTypes(ItemStack cellStack) {
        NBTTagCompound tag = cellStack.getTagCompound();
        if (tag == null || !tag.hasKey("userMaxTypes")) return Integer.MAX_VALUE;

        return tag.getInteger("userMaxTypes");
    }

    /**
     * Set the user-configured max types in the cell's NBT.
     * If the value is Integer.MAX_VALUE (unlimited), the key is removed instead.
     */
    public static void setUserMaxTypes(ItemStack cellStack, int value) {
        NBTTagCompound tag = cellStack.getTagCompound();

        // Remove the key if the value is unlimited (default behavior)
        if (value == Integer.MAX_VALUE) {
            if (tag != null) tag.removeTag("userMaxTypes");
            return;
        }

        if (tag == null) {
            tag = new NBTTagCompound();
            cellStack.setTagCompound(tag);
        }

        tag.setInteger("userMaxTypes", value);
    }

    /**
     * Get the effective max types for a cell, considering both user limit and config limit.
     * Returns the minimum of user limit, config limit, capped at 1 minimum.
     */
    public static int getEffectiveMaxTypes(ItemStack cellStack, ChannelType channelType) {
        int configMax = channelType.getMaxTypes();
        int userMax = getUserMaxTypes(cellStack);

        return Math.max(1, Math.min(configMax, userMax));
    }

    /**
     * Validate a proposed new max types value for a cell.
     * Checks:
     * 1. newMaxTypes >= currently stored types count
     * 2. Per-type capacity with new limit can hold existing content
     *
     * @param cellStack   The cell ItemStack
     * @param newMaxTypes The proposed new max types value
     * @return null if valid, or an error message key if invalid
     */
    public static String validateNewMaxTypes(ItemStack cellStack, int newMaxTypes) {
        if (newMaxTypes < 1) return "message.cells.configurable_cell.types_min";

        ComponentInfo info = getComponentInfo(getInstalledComponent(cellStack));
        if (info == null) return null; // No component, no validation needed

        int configMax = info.getChannelType().getMaxTypes();
        if (newMaxTypes > configMax) return "message.cells.configurable_cell.types_over_config";

        long[] summary = getStoredContentSummary(cellStack);
        long storedTypes = summary[0];
        long maxCountPerType = summary[1];

        // Check 1: Must have enough types for stored content
        if (newMaxTypes < storedTypes) return "message.cells.configurable_cell.types_below_stored";

        // Check 2: Per-type capacity must accommodate existing content
        long newPhysicalPerType = calculatePhysicalPerTypeCapacity(info, newMaxTypes);
        long userMaxPerType = getMaxPerType(cellStack);
        long effectivePerType = Math.min(userMaxPerType, newPhysicalPerType);

        if (maxCountPerType > effectivePerType) return "message.cells.configurable_cell.types_capacity_exceeded";

        return null; // Valid
    }

    /**
     * Determine if the cell has items or fluids in its inventory by checking the NBT for stored types.
     * @return True if the cell has stored types (indicating it has content), false otherwise.
     */
    public static boolean hasContent(ItemStack cellStack) {
        NBTTagCompound tag = cellStack.getTagCompound();
        if (tag == null) return false;

        // Check item storage (ConfigurableCellItemInventory uses "itemType")
        if (tag.hasKey("itemType") && !tag.getCompoundTag("itemType").isEmpty()) return true;

        // Check fluid storage (ConfigurableCellFluidInventory uses "fluidType")
        return tag.hasKey("fluidType") && !tag.getCompoundTag("fluidType").isEmpty();
    }

    /**
     * Calculate the physical per-type capacity for a given component and max types.
     * This is the maximum items/mB per type, based on component bytes and equal distribution.
     *<p>
     * Formula: totalBytes * unitsPerByte / 2 / maxTypes.
     * The bytes overhead is simplifed to be 50% of total bytes.
     *<p>
     * @param info     The component info
     * @param maxTypes The number of types to divide among
     * @return The maximum items or buckets per type
     */
    public static long calculatePhysicalPerTypeCapacity(ComponentInfo info, int maxTypes) {
        if (info == null || maxTypes <= 0) return 0;

        long totalBytes = info.getBytes();
        long availableBytes = totalBytes / 2;

        if (availableBytes <= 0) return 0;

        // 8 bits per byte, each bit holds countPerBit units
        // Items/essentia: 1 per bit (8 per byte)
        // Fluids: 1000 per bit (8000 per byte)
        // Gas: 4000 per bit (32000 per byte)
        long totalUnits = availableBytes * 8L * info.getChannelType().getCountPerBit();

        return totalUnits / maxTypes;
    }

    // =====================
    // Content inspection (for swap validation)
    // =====================

    /**
     * Read the stored types count and maximum per-type count from cell NBT,
     * without constructing a full inventory.
     *
     * @return long[2]: [0] = storedTypes, [1] = maxCountPerType (the largest count among all stored types)
     */
    public static long[] getStoredContentSummary(ItemStack cellStack) {
        NBTTagCompound tag = cellStack.getTagCompound();
        if (tag == null) return new long[]{0, 0};

        long storedTypes = 0;
        long maxCountPerType = 0;

        // Check all channel type storage tags
        for (ChannelType channelType : ChannelType.values()) {
            String nbtKey = channelType.getNbtTagKey();
            if (!tag.hasKey(nbtKey)) continue;

            NBTTagCompound channelTag = tag.getCompoundTag(nbtKey);
            for (String key : channelTag.getKeySet()) {
                long count = channelTag.getCompoundTag(key).getLong("StoredCount");
                if (count > 0) {
                    storedTypes++;
                    if (count > maxCountPerType) maxCountPerType = count;
                }
            }
        }

        return new long[]{storedTypes, maxCountPerType};
    }

    /**
     * Check whether the current component can be swapped for a new one without
     * data loss. Conditions:
     * <ul>
     *   <li>Both components must use the same storage channel (item ↔ item, fluid ↔ fluid)</li>
     *   <li>The new component's per-type capacity must be ≥ the highest stored count</li>
     * </ul>
     *
     * @param cellStack    The cell ItemStack (carries the stored inventory NBT)
     * @param newComponent The candidate replacement component
     * @return true if the swap is safe
     */
    public static boolean canSwapComponent(ItemStack cellStack, ItemStack newComponent) {
        ComponentInfo currentInfo = getComponentInfo(getInstalledComponent(cellStack));
        ComponentInfo newInfo = getComponentInfo(newComponent);
        if (currentInfo == null || newInfo == null) return false;

        // Must be the same storage channel type
        if (currentInfo.getChannelType() != newInfo.getChannelType()) return false;

        // New component must have enough per-type capacity for the existing content
        int maxTypes = newInfo.getChannelType().getMaxTypes();
        long newPhysicalPerType = calculatePhysicalPerTypeCapacity(newInfo, maxTypes);
        long userMaxPerType = getMaxPerType(cellStack);
        long newEffectivePerType = Math.min(userMaxPerType, newPhysicalPerType);

        long[] summary = getStoredContentSummary(cellStack);
        long storedTypes = summary[0];
        long maxCountPerType = summary[1];

        // The new component must hold at least as many types as currently stored
        if (storedTypes > maxTypes) return false;

        // The new component's per-type capacity must accommodate the largest stored count
        return maxCountPerType <= newEffectivePerType;
    }
}
