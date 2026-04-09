package com.cells.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.cells.Cells;
import com.cells.Tags;


/**
 * Validates ore dictionary entries for equivalence matching.
 * <p>
 * Mirrors Storage Drawers' {@code OreDictRegistry} logic to prevent exploits
 * where items sharing overly broad ore tags (like "dye") would incorrectly match.
 * </p>
 * <p>
 * Entries are checked against:
 * <ul>
 *   <li>A blacklist of ore names (exact match)</li>
 *   <li>A prefix blacklist (e.g., "dye" blocks "dyeBlue", "dyeBlack")</li>
 *   <li>A whitelist of known safe entries</li>
 *   <li>Dynamic validation (no wildcards, no duplicate mods, consistent ore tags)</li>
 * </ul>
 * </p>
 * <p>
 * Whitelist and blacklist can be customized via config files:
 * <ul>
 *   <li>{@code config/cells/oredict_whitelist.txt}</li>
 *   <li>{@code config/cells/oredict_blacklist.txt}</li>
 * </ul>
 * </p>
 */
public final class OreDictValidator {

    private static final String WHITELIST_RESOURCE = "/assets/" + Tags.MODID + "/oredict_whitelist.txt";
    private static final String BLACKLIST_RESOURCE = "/assets/" + Tags.MODID + "/oredict_blacklist.txt";
    private static final String CONFIG_SUBDIR = Tags.MODID;
    private static final String WHITELIST_CONFIG_NAME = "oredict_whitelist.txt";
    private static final String BLACKLIST_CONFIG_NAME = "oredict_blacklist.txt";

    // Explicit whitelist of ore names that are safe for equivalence matching
    private static final Set<String> whitelist = new HashSet<>();

    // Explicit blacklist of ore names that are too broad for equivalence matching
    private static final Set<String> blacklist = new HashSet<>();

    // Prefix-based blacklist: ore names starting with these prefixes are blacklisted
    private static final List<String> blacklistPrefixes = new ArrayList<>();

    // Cache of ore IDs confirmed as valid (pre-validated at postInit)
    // Using Integer IDs for O(1) lookup during item matching
    private static final Set<Integer> validOreIds = new HashSet<>();

    // Cache of ore IDs confirmed as invalid
    private static final Set<Integer> invalidOreIds = new HashSet<>();

    // Whether preValidateAllEntries() has been called
    private static boolean fullyValidated = false;

    private OreDictValidator() {
        // Utility class
    }

    // =====================
    // Initialization
    // =====================

    /**
     * Load whitelist and blacklist from config files or bundled defaults.
     * <p>
     * Called from {@link com.cells.Cells#preInit} after the config directory
     * is available.
     *
     * @param configDir The Forge config directory
     */
    public static void loadConfig(@Nullable File configDir) {
        whitelist.clear();
        blacklist.clear();
        blacklistPrefixes.clear();
        validOreIds.clear();
        invalidOreIds.clear();
        fullyValidated = false;

        File cellsConfigDir = null;
        if (configDir != null) {
            cellsConfigDir = new File(configDir, CONFIG_SUBDIR);
            if (!cellsConfigDir.exists()) cellsConfigDir.mkdirs();
        }

        loadWhitelist(cellsConfigDir);
        loadBlacklist(cellsConfigDir);

        Cells.LOGGER.info("OreDictValidator: loaded {} whitelist entries, {} blacklist entries, {} blacklist prefixes",
            whitelist.size(), blacklist.size(), blacklistPrefixes.size());
    }

    private static void loadWhitelist(@Nullable File cellsConfigDir) {
        // Try config directory override first (config/cells/)
        if (cellsConfigDir != null) {
            File override = new File(cellsConfigDir, WHITELIST_CONFIG_NAME);
            if (override.isFile()) {
                Cells.LOGGER.info("Loading ore dict whitelist override from {}", override.getAbsolutePath());
                try (InputStream is = Files.newInputStream(override.toPath())) {
                    parseWhitelist(is);
                    return;
                } catch (Exception e) {
                    Cells.LOGGER.error("Failed to load whitelist override, falling back to bundled", e);
                }
            }
        }

        // Fall back to bundled resource
        try (InputStream is = OreDictValidator.class.getResourceAsStream(WHITELIST_RESOURCE)) {
            if (is == null) {
                Cells.LOGGER.warn("Bundled ore dict whitelist not found, using hardcoded defaults");
                loadHardcodedWhitelist();
                return;
            }

            parseWhitelist(is);
        } catch (Exception e) {
            Cells.LOGGER.error("Failed to load bundled whitelist, using hardcoded defaults", e);
            loadHardcodedWhitelist();
        }
    }

    private static void loadBlacklist(@Nullable File cellsConfigDir) {
        // Try config directory override first (config/cells/)
        if (cellsConfigDir != null) {
            File override = new File(cellsConfigDir, BLACKLIST_CONFIG_NAME);
            if (override.isFile()) {
                Cells.LOGGER.info("Loading ore dict blacklist override from {}", override.getAbsolutePath());
                try (InputStream is = Files.newInputStream(override.toPath())) {
                    parseBlacklist(is);
                    return;
                } catch (Exception e) {
                    Cells.LOGGER.error("Failed to load blacklist override, falling back to bundled", e);
                }
            }
        }

        // Fall back to bundled resource
        try (InputStream is = OreDictValidator.class.getResourceAsStream(BLACKLIST_RESOURCE)) {
            if (is == null) {
                Cells.LOGGER.warn("Bundled ore dict blacklist not found, using hardcoded defaults");
                loadHardcodedBlacklist();
                return;
            }

            parseBlacklist(is);
        } catch (Exception e) {
            Cells.LOGGER.error("Failed to load bundled blacklist, using hardcoded defaults", e);
            loadHardcodedBlacklist();
        }
    }

    private static void parseWhitelist(InputStream is) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                whitelist.add(line);
            }
        } catch (Exception e) {
            Cells.LOGGER.error("Failed to parse whitelist", e);
        }
    }

    private static void parseBlacklist(InputStream is) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                // Lines starting with "*" are prefix blacklist entries
                if (line.startsWith("*")) {
                    blacklistPrefixes.add(line.substring(1));
                } else {
                    blacklist.add(line);
                }
            }
        } catch (Exception e) {
            Cells.LOGGER.error("Failed to parse blacklist", e);
        }
    }

    // =====================
    // Hardcoded defaults (fallback, should never be used if resources are correct)
    // =====================

    private static void loadHardcodedWhitelist() {
        // Common metal forms - expanded list
        String[] types = { "ore", "block", "ingot", "nugget", "dust", "gear", "plate", "rod" };
        String[] materials = {
            // Vanilla
            "Iron", "Gold", "Diamond", "Emerald", "Lapis", "Redstone", "Quartz", "Coal",
            // Common modded metals
            "Aluminum", "Aluminium", "Tin", "Copper", "Lead", "Silver", "Platinum", "Nickel",
            "Osmium", "Zinc", "Uranium", "Titanium", "Tungsten", "Cobalt", "Ardite",
            // Alloys
            "Invar", "Bronze", "Electrum", "Enderium", "Lumium", "Signalum", "Steel",
            "Manyullyn", "Mithril", "Constantan", "Brass",
            // Tech/magic metals
            "Thaumium", "Void", "Manasteel", "Terrasteel", "Elementium", "Draconic",
            "Yellorium", "Cyanite", "Blutonium", "Ludicrite",
            // Gems (blocks/ores)
            "Ruby", "Sapphire", "Peridot", "Topaz", "Tanzanite", "Malachite", "Amber",
            "Certus", "CertusQuartz", "ChargedCertusQuartz", "Fluix"
        };

        for (String material : materials) {
            for (String type : types) whitelist.add(type + material);
        }

        // Additional common entries
        Collections.addAll(whitelist,
            "gemDiamond", "gemEmerald", "gemLapis", "gemQuartz", "gemRuby", "gemSapphire",
            "cropWheat", "cropPotato", "cropCarrot", "cropBeetroot",
            "dustWood", "pulpWood"
        );
    }

    private static void loadHardcodedBlacklist() {
        // Mirror Storage Drawers' default blacklist
        Collections.addAll(blacklist,
            "logWood", "plankWood", "slabWood", "stairWood", "stickWood",
            "treeSapling", "treeLeaves", "leavesTree",
            "blockGlass", "paneGlass", "record",
            "stone", "cobblestone", "glowstone", "glass", "obsidian", "sand", "sandstone",
            "accioMaterial", "crucioMaterial", "imperioMaterial", "zivicioMaterial",
            "resourceTaint", "slimeball",
            "blockMetal", "ingotMetal", "nuggetMetal"
        );

        // Prefix blacklist
        Collections.addAll(blacklistPrefixes, "list", "dye", "paneGlass");
    }

    // =====================
    // Pre-validation (postInit)
    // =====================

    /**
     * Pre-validate all registered ore dictionary entries.
     * <p>
     * Called from {@link com.cells.Cells#postInit} after all mods have registered
     * their ore dictionary entries. This caches validation results for O(1) lookup
     * during gameplay.
     * </p>
     */
    public static void preValidateAllEntries() {
        String[] allOreNames = OreDictionary.getOreNames();
        int valid = 0;
        int invalid = 0;

        for (String oreName : allOreNames) {
            int oreId = OreDictionary.getOreID(oreName);
            if (isEntryValidInternal(oreName)) {
                validOreIds.add(oreId);
                valid++;
            } else {
                invalidOreIds.add(oreId);
                invalid++;
            }
        }

        fullyValidated = true;
        Cells.LOGGER.info("OreDictValidator: pre-validated {} ore entries ({} valid, {} invalid)",
            allOreNames.length, valid, invalid);
    }

    // =====================
    // Validation API
    // =====================

    /**
     * Checks if an ore dictionary ID is valid for equivalence matching.
     * <p>
     * After {@link #preValidateAllEntries()} is called, this is O(1).
     * Before that, it performs full validation with caching.
     * </p>
     *
     * @param oreId The ore dictionary ID
     * @return true if the entry is safe to use for equivalence matching
     */
    public static boolean isOreIdValid(int oreId) {
        if (fullyValidated) return validOreIds.contains(oreId);

        // Fall back to name-based validation with caching
        // Should never happen, but who knows
        if (validOreIds.contains(oreId)) return true;
        if (invalidOreIds.contains(oreId)) return false;

        String oreName = OreDictionary.getOreName(oreId);
        if (isEntryValidInternal(oreName)) {
            validOreIds.add(oreId);
            return true;
        } else {
            invalidOreIds.add(oreId);
            return false;
        }
    }

    /**
     * Build a set of valid ore IDs for the given ItemStack.
     * <p>
     * This is meant to be called once per protoStack during chain initialization,
     * then cached by the cell inventory for O(1) matching during insertions.
     * </p>
     *
     * @param stack The ItemStack to get valid ore IDs for
     * @return Set of valid ore IDs that can be used for equivalence matching
     */
    public static Set<Integer> getValidOreIds(ItemStack stack) {
        if (stack.isEmpty()) return Collections.emptySet();

        int[] oreIds = OreDictionary.getOreIDs(stack);
        if (oreIds.length == 0) return Collections.emptySet();

        Set<Integer> result = new HashSet<>();
        for (int id : oreIds) {
            if (isOreIdValid(id)) result.add(id);
        }

        return result;
    }

    /**
     * Build a combined set of valid ore IDs for multiple ItemStacks.
     * <p>
     * Used by compacting cells to cache all valid ore IDs across the entire
     * compression chain (protoStack array).
     * </p>
     *
     * @param stacks Array of ItemStacks (may contain empty stacks)
     * @return Combined set of valid ore IDs for all non-empty stacks
     */
    public static Set<Integer> getValidOreIdsForChain(ItemStack[] stacks) {
        Set<Integer> result = new HashSet<>();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) result.addAll(getValidOreIds(stack));
        }

        return result;
    }

    /**
     * Build a mapping from ore ID to slot indice for the given protoStack array.
     * <p>
     * This enables O(1) lookup of candidate slot when matching an input item by ore ID.
     * The cell caches this map during chain initialization for efficient ore dict matching.
     * </p>
     *
     * @param protoStack Array of ItemStacks representing the compression chain
     * @return Map from valid ore ID to slot indice that has the ore ID
     */
    public static Map<Integer, Integer> getOreIdToSlotMapping(ItemStack[] protoStack) {
        Map<Integer, Integer> result = new HashMap<>();

        for (int slot = 0; slot < protoStack.length; slot++) {
            ItemStack stack = protoStack[slot];
            if (stack.isEmpty()) continue;

            for (int oreId : OreDictionary.getOreIDs(stack)) {
                if (!isOreIdValid(oreId)) continue;

                result.putIfAbsent(oreId, slot);
            }
        }

        return result;
    }

    /**
     * Get the set of slots that might match the input item via ore dictionary.
     * <p>
     * Returns all slots whose proto items share at least one valid ore ID with
     * the input item. Caller must still verify NBT equality on candidate slots.
     * <p>
     * Note: This method allocates a HashSet on every call. For hot paths, prefer
     * {@link #findFirstMatchingSlot} which is zero-allocation.
     *
     * @param input The input item to match
     * @param oreIdToSlot Map from ore ID to slot (from {@link #getOreIdToSlotMapping})
     * @return Set of candidate slot indices, or empty set if no matches
     */
    public static Set<Integer> getMatchingSlots(ItemStack input, Map<Integer, Integer> oreIdToSlot) {
        if (input.isEmpty() || oreIdToSlot.isEmpty()) return Collections.emptySet();
        if (input.getMetadata() == OreDictionary.WILDCARD_VALUE) return Collections.emptySet();

        Set<Integer> result = new HashSet<>();
        for (int oreId : OreDictionary.getOreIDs(input)) {
            Integer slot = oreIdToSlot.get(oreId);
            if (slot != null) result.add(slot);
        }

        return result;
    }

    /**
     * Find the first slot that matches the input item via ore dictionary AND NBT equality.
     * <p>
     * Zero-allocation alternative to {@link #getMatchingSlots} for use on hot paths
     * (injectItems, extractItems, isInCompressionChain). Instead of building a Set of
     * candidate slots and then iterating, this method inlines both the ore ID lookup
     * and the NBT comparison into a single pass.
     * <p>
     * The cost is: one {@code OreDictionary.getOreIDs(input)} call (which Forge may
     * intern), then for each ore ID, a HashMap lookup + one NBT compare per candidate.
     * No HashSet or any other collection is allocated.
     *
     * @param input The input item to match
     * @param oreIdToSlot Map from ore ID to slot (from {@link #getOreIdToSlotMapping})
     * @param protoStack The proto items array to compare NBT against
     * @return The matching slot index, or -1 if no match
     */
    public static int findFirstMatchingSlot(ItemStack input, Map<Integer, Integer> oreIdToSlot, ItemStack[] protoStack) {
        if (input.isEmpty() || oreIdToSlot.isEmpty()) return -1;
        if (input.getMetadata() == OreDictionary.WILDCARD_VALUE) return -1;

        for (int oreId : OreDictionary.getOreIDs(input)) {
            Integer slot = oreIdToSlot.get(oreId);
            if (slot == null) continue;

            // Verify NBT equality between the input and the proto item at this slot
            if (ItemStack.areItemStackTagsEqual(protoStack[slot], input)) return slot;
        }

        return -1;
    }

    /**
     * Check if any slot matches the input item via ore dictionary AND NBT equality.
     * <p>
     * Zero-allocation boolean variant of {@link #findFirstMatchingSlot} for use
     * in {@code isInCompressionChain} and {@code isAllowedByPartition}.
     *
     * @param input The input item to match
     * @param oreIdToSlot Map from ore ID to slot (from {@link #getOreIdToSlotMapping})
     * @param protoStack The proto items array to compare NBT against
     * @return true if any slot matches via ore dict + NBT
     */
    public static boolean hasMatchingSlot(ItemStack input, Map<Integer, Integer> oreIdToSlot, ItemStack[] protoStack) {
        return findFirstMatchingSlot(input, oreIdToSlot, protoStack) >= 0;
    }

    /**
     * Checks if an ItemStack matches any entry in a pre-computed set of valid ore IDs.
     * <p>
     * This is a simple boolean check for use cases that only need to know
     * if there's any match (e.g., {@code isAllowedByPartition}, {@code isInCompressionChain}).
     *
     * @param input The input stack to check
     * @param cachedValidOreIds Pre-computed set of valid ore IDs
     * @return true if input shares a valid ore entry with the cached set
     */
    public static boolean matchesOreDictCache(ItemStack input, Set<Integer> cachedValidOreIds) {
        if (input.isEmpty() || cachedValidOreIds.isEmpty()) return false;
        if (input.getMetadata() == OreDictionary.WILDCARD_VALUE) return false;

        for (int id : OreDictionary.getOreIDs(input)) {
            if (cachedValidOreIds.contains(id)) return true;
        }

        return false;
    }

    /**
     * Checks if two ItemStacks are equivalent via the Ore Dictionary.
     * <p>
     * Two stacks are ore dictionary equivalent if they share at least one
     * valid ore dictionary entry AND have matching NBT tags.
     * <p>
     * This does NOT check if the items are directly equal. Callers should
     * check direct equality first before calling this method.
     * <p>
     * Note: this is an O(n*m) operation where n = len(idsA) and m = len(idsB), so callers
     * should use {@link #matchesOreDictCache} when possible for O(1) checks.
     *
     * @param a First stack
     * @param b Second stack
     * @return true if stacks share a valid ore dictionary entry and have equal NBT
     */
    public static boolean areOreDictEquivalent(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.getMetadata() == OreDictionary.WILDCARD_VALUE || b.getMetadata() == OreDictionary.WILDCARD_VALUE) {
            return false;
        }

        int[] idsA = OreDictionary.getOreIDs(a);
        int[] idsB = OreDictionary.getOreIDs(b);
        if (idsA.length == 0 || idsB.length == 0) return false;

        // Build set of valid ore IDs from A for O(n) lookup
        Set<Integer> validIdsA = new HashSet<>();
        for (int id : idsA) {
            if (isOreIdValid(id)) validIdsA.add(id);
        }

        if (validIdsA.isEmpty()) return false;

        // Check if B shares any valid ore entry with A
        for (int id : idsB) {
            if (validIdsA.contains(id)) return ItemStack.areItemStackTagsEqual(a, b);
        }

        return false;
    }

    // =====================
    // Internal validation
    // =====================

    private static boolean isEntryValidInternal(String oreName) {
        // Whitelist entries are always valid
        if (whitelist.contains(oreName)) return true;

        // Check blacklist (exact match)
        if (blacklist.contains(oreName)) return false;

        // Check prefix blacklist
        for (String prefix : blacklistPrefixes) {
            if (oreName.startsWith(prefix)) return false;
        }

        // Dynamic validation
        return isValidForEquiv(oreName);
    }

    /**
     * Dynamic validation mirroring Storage Drawers' {@code isValidForEquiv}.
     * <p>
     * An ore entry is valid for equivalence if:
     * <ul>
     *   <li>No registered items have wildcard damage values</li>
     *   <li>No two registered items come from the same mod</li>
     *   <li>The ore IDs across all registered items form a consistent set</li>
     * </ul>
     * </p>
     */
    private static boolean isValidForEquiv(String oreName) {
        List<ItemStack> oreList = OreDictionary.getOres(oreName);
        if (oreList.isEmpty()) return false;

        // Fail entries that have any wildcard items registered
        Set<String> modIds = new HashSet<>();
        for (ItemStack stack : oreList) {
            if (stack.getItemDamage() == OreDictionary.WILDCARD_VALUE) return false;

            Item item = stack.getItem();
            if (item.getRegistryName() != null) {
                modIds.add(item.getRegistryName().getNamespace());
            }
        }

        // Fail entries that have multiple instances from the same mod
        if (modIds.size() < oreList.size()) return false;

        // Fail entries where the ore IDs across items are inconsistent
        Set<Integer> mergedIds = new HashSet<>();
        int maxKeyCount = 0;

        for (ItemStack stack : oreList) {
            int[] ids = OreDictionary.getOreIDs(stack);
            maxKeyCount = Math.max(maxKeyCount, ids.length);

            for (int id : ids) mergedIds.add(id);
        }

        return maxKeyCount >= mergedIds.size();
    }
}
