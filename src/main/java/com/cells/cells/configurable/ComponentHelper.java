package com.cells.cells.configurable;

import javax.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import com.cells.Cells;
import com.cells.Tags;
import com.cells.config.CellsConfig;


/**
 * Utility class for recognizing ME Storage Components and mapping them to their
 * byte capacities and storage channels.
 * <p>
 * Recognized components are loaded from a whitelist file
 * ({@code assets/cells/configurable_components.cfg}) at startup. Each entry maps
 * a registry name + metadata to a byte capacity, storage channel, and tier name.
 * <p>
 * Supported components (via whitelist):
 * - AE2 item components (1k, 4k, 16k, 64k)
 * - AE2 fluid components (1k, 4k, 16k, 64k)
 * - NAE2 item components (256k, 1m, 4m, 16m)
 * - NAE2 fluid components (256k, 1m, 4m, 16m)
 * - CrazyAE item components (256k, 1m, 4m, 16m, 64m, 256m, 1g, 2g)
 * - CrazyAE fluid components (256k, 1m, 4m, 16m, 64m, 256m, 1g, 2g)
 */
public final class ComponentHelper {

    private static final String WHITELIST_RESOURCE = "/assets/" + Tags.MODID + "/configurable_components.cfg";
    public static final String WHITELIST_CONFIG_NAME = "configurable_components.cfg";

    /**
     * Whitelist map: "registryName@meta" -> ComponentInfo.
     * Populated once from the whitelist config file.
     */
    private static final Map<String, ComponentInfo> WHITELIST = new HashMap<>();

    public static final String[] TIER_NAMES = {
        "1k", "4k", "16k", "64k",
        "256k", "1m", "4m", "16m",
        "64m", "256m", "1g", "2g"
    };

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

        // Try config directory override first
        if (configDir != null) {
            File override = new File(configDir, WHITELIST_CONFIG_NAME);
            if (override.isFile()) {
                Cells.LOGGER.info("Loading component whitelist override from {}", override.getAbsolutePath());
                try (InputStream is = new FileInputStream(override)) {
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
                    String channel = parts[1].trim().toLowerCase();
                    String tierName = parts[2].trim();
                    boolean isFluid = channel.equals("fluid");

                    WHITELIST.put(key, new ComponentInfo(bytes, isFluid, tierName));
                } catch (NumberFormatException e) {
                    Cells.LOGGER.warn("Invalid byte value at line {}: {}", lineNum, parts[0].trim());
                }
            }

            Cells.LOGGER.info("Loaded {} component whitelist entries from {}", WHITELIST.size(), sourceName);
        } catch (Exception e) {
            Cells.LOGGER.error("Failed to parse whitelist from {}", sourceName, e);
        }
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

        String key = regName.toString() + "@" + stack.getMetadata();

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
     */
    public static void setMaxPerType(ItemStack cellStack, long value) {
        NBTTagCompound tag = cellStack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            cellStack.setTagCompound(tag);
        }

        tag.setLong("maxPerType", value);
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
        if (tag.hasKey("fluidType") && !tag.getCompoundTag("fluidType").isEmpty()) return true;

        return false;
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

        // 8 units per byte for both items and fluids
        long totalUnits = availableBytes * 8L;

        // For fluids, convert units to mB (1 unit = 1000 mB)
        if (info.isFluid()) totalUnits *= 1000L;

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

        // Check item storage
        if (tag.hasKey("itemType")) {
            NBTTagCompound itemsTag = tag.getCompoundTag("itemType");
            for (String key : itemsTag.getKeySet()) {
                long count = itemsTag.getCompoundTag(key).getLong("StoredCount");
                if (count > 0) {
                    storedTypes++;
                    if (count > maxCountPerType) maxCountPerType = count;
                }
            }
        }

        // Check fluid storage
        if (tag.hasKey("fluidType")) {
            NBTTagCompound fluidsTag = tag.getCompoundTag("fluidType");
            for (String key : fluidsTag.getKeySet()) {
                long count = fluidsTag.getCompoundTag(key).getLong("StoredCount");
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

        // Must be the same storage channel
        if (currentInfo.isFluid() != newInfo.isFluid()) return false;

        // New component must have enough per-type capacity for the existing content
        int maxTypes = CellsConfig.configurableCellMaxTypes;
        long newPhysicalPerType = calculatePhysicalPerTypeCapacity(newInfo, maxTypes);
        long userMaxPerType = getMaxPerType(cellStack);
        long newEffectivePerType = Math.min(userMaxPerType, newPhysicalPerType);

        long[] summary = getStoredContentSummary(cellStack);
        long storedTypes = summary[0];
        long maxCountPerType = summary[1];

        // The new component must hold at least as many types as currently stored
        if (storedTypes > maxTypes) return false;

        // The new component's per-type capacity must accommodate the largest stored count
        if (maxCountPerType > newEffectivePerType) return false;

        return true;
    }
}
