package com.cells.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import com.cells.Cells;
import com.cells.Tags;
import com.cells.items.upgrades.AbstractCustomUpgrade;


/**
 * Startup-loaded disassembly entries for cells and upgrade cards.
 */
public final class DisassemblyConfig {

    public static final String CONFIG_NAME = "disassembly.cfg";

    private static final String CONFIG_SUBDIR = Tags.MODID;
    private static final String CONFIG_SOURCE = "/assets/" + Tags.MODID + "/" + CONFIG_NAME;
    private static final String PROCESSED_ENTRIES_KEY = "processed_bundled_entries";
    public static final String HOUSING_TOKEN = "<housing>";
    public static final String COMPONENT_TOKEN = "<component>";
    private static final int DEFAULT_METADATA = 0;

    private static final Map<String, List<OutputSpec>> ENTRIES = new HashMap<>();
    private static final Set<String> MISSING_OUTPUTS = new HashSet<>();

    private DisassemblyConfig() {}

    /**
     * Loads the disassembly entries, creates the default file when needed, and appends new bundled entries.
     */
    public static void load(@Nullable File configDir) {
        ENTRIES.clear();
        MISSING_OUTPUTS.clear();

        if (configDir == null) {
            Cells.LOGGER.error("Cannot load disassembly config without a config directory");
            return;
        }

        File cellsConfigDir = new File(configDir, CONFIG_SUBDIR);
        File configFile = new File(cellsConfigDir, CONFIG_NAME);
        try {
            if (!configFile.isFile()) createDefaultConfig(cellsConfigDir, configFile);

            try {
                updateConfig(configFile);
            } catch (IOException e) {
                Cells.LOGGER.error("Failed to update disassembly config at {}", configFile.getAbsolutePath(), e);
            }

            parseConfig(configFile);
            Cells.LOGGER.info("Loaded {} disassembly entries from {}", ENTRIES.size(),
                configFile.getAbsolutePath());
        } catch (Exception e) {
            ENTRIES.clear();
            Cells.LOGGER.error("Failed to load disassembly config from {}", configFile.getAbsolutePath(), e);
        }
    }

    /**
     * Resolves the configured outputs for an item stack.
     * Returns an empty list when no entry exists or an output item is unavailable.
     */
    @Nonnull
    public static List<ItemStack> getOutputs(@Nonnull ItemStack source) {
        return getOutputs(source, null, null);
    }

    /**
     * Resolves configured outputs with optional housing and component outputs.
     */
    @Nonnull
    public static List<ItemStack> getOutputs(@Nonnull ItemStack source,
                                             @Nullable ItemStack housing,
                                             @Nullable ItemStack component) throws IllegalArgumentException {
        if (source.isEmpty() || source.getItem().getRegistryName() == null) return Collections.emptyList();

        String itemName = source.getItem().getRegistryName().toString();
        int metadata = source.getItem() instanceof AbstractCustomUpgrade
            ? ((AbstractCustomUpgrade) source.getItem()).getDisassemblyMetadata(source) : source.getMetadata();
        List<OutputSpec> outputSpecs = ENTRIES.get(itemName + "@" + metadata);
        if (outputSpecs == null) return Collections.emptyList();

        List<ItemStack> outputs = new ArrayList<>();
        for (OutputSpec outputSpec : outputSpecs) {
            if (outputSpec.isSpecial()) {
                ItemStack specialOutput;
                if (HOUSING_TOKEN.equals(outputSpec.itemName)) {
                    specialOutput = housing;
                } else if (COMPONENT_TOKEN.equals(outputSpec.itemName)) {
                    specialOutput = component;
                } else {
                    throw new IllegalArgumentException("Unknown special token: " + outputSpec.itemName);
                }
    
                if (specialOutput != null && !specialOutput.isEmpty()) {
                    specialOutput = specialOutput.copy();
                    specialOutput.setCount(outputSpec.count);
                    outputs.add(specialOutput);
                }
                continue;
            }

            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(outputSpec.itemName));
            if (item == null) {
                logMissingOutput(source, outputSpec.itemName);
                return Collections.emptyList();
            }

            outputs.add(new ItemStack(item, outputSpec.count, outputSpec.metadata));
        }

        return outputs;
    }

    public static boolean isSpecialToken(@Nonnull String value) {
        return HOUSING_TOKEN.equals(value) || COMPONENT_TOKEN.equals(value);
    }

    private static void createDefaultConfig(File cellsConfigDir, File configFile) throws IOException {
        Files.createDirectories(cellsConfigDir.toPath());

        try (InputStream source = DisassemblyConfig.class.getResourceAsStream(CONFIG_SOURCE)) {
            if (source == null) {
                throw new IOException("Bundled disassembly config is missing at " + CONFIG_SOURCE);
            }

            Files.copy(source, configFile.toPath());
        }

        Cells.LOGGER.info("Created default disassembly config at {}", configFile.getAbsolutePath());
    }

    private static void updateConfig(File configFile) throws IOException {
        String content = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
        String newline = content.contains("\r\n") ? "\r\n" : content.contains("\r") ? "\r" : "\n";
        content = normalizeNewlines(content);
        Set<String> processedKeys = new LinkedHashSet<>();
        Set<String> existingKeys = new LinkedHashSet<>();
        ConfigEntry cacheEntry = null;
        for (ConfigEntry entry : readConfigEntries(content)) {
            if (entry.value == null) throw new IOException("Unterminated disassembly list for " + entry.key);

            if (PROCESSED_ENTRIES_KEY.equals(entry.key)) {
                if (cacheEntry != null) throw new IOException("Duplicate " + PROCESSED_ENTRIES_KEY + " key");

                cacheEntry = entry;
                readProcessedKeys(entry.value, processedKeys);
                continue;
            }

            EntryKey entryKey = EntryKey.parse(entry.key);
            if (entryKey != null) existingKeys.add(entryKey.toString());
        }

        Set<String> cachedKeys = new HashSet<>(processedKeys);
        processedKeys.addAll(existingKeys);

        StringBuilder bundledContent = new StringBuilder();
        try (InputStream source = DisassemblyConfig.class.getResourceAsStream(CONFIG_SOURCE)) {
            if (source == null) {
                throw new IOException("Bundled disassembly config is missing at " + CONFIG_SOURCE);
            }

            InputStreamReader reader = new InputStreamReader(source, StandardCharsets.UTF_8);
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) >= 0) bundledContent.append(buffer, 0, count);
        }

        String bundledText = normalizeNewlines(bundledContent.toString());
        StringBuilder additions = new StringBuilder();
        int addedEntries = 0;
        for (ConfigEntry entry : readConfigEntries(bundledText)) {
            if (PROCESSED_ENTRIES_KEY.equals(entry.key) || entry.value == null) continue;

            EntryKey entryKey = EntryKey.parse(entry.key);
            if (entryKey == null || !processedKeys.add(entryKey.toString())) continue;

            additions.append(bundledText, entry.start, entry.end);
            addedEntries++;
        }

        if (cacheEntry != null && processedKeys.equals(cachedKeys)) return;

        Set<String> newKeys = new LinkedHashSet<>(processedKeys);
        newKeys.removeAll(cachedKeys);
        StringBuilder cache = new StringBuilder();
        if (cacheEntry == null) cache.append(PROCESSED_ENTRIES_KEY).append(" = [");

        int keyIndex = 0;
        for (String key : newKeys) {
            cache.append('\n').append("    \"").append(key).append('"');
            if (++keyIndex < newKeys.size() || !cachedKeys.isEmpty()) cache.append(',');
        }

        StringBuilder updated = new StringBuilder(content);
        if (cacheEntry != null) {
            int insertion = content.indexOf('[', cacheEntry.start) + 1;
            char following = content.charAt(insertion);
            if (following != '\n') cache.append('\n');

            // Keep cached values and comments intact when inserting newly processed keys
            updated.insert(insertion, cache);
        }

        if (additions.length() > 0) appendConfigSection(updated, additions.toString());

        if (cacheEntry == null) {
            cache.append('\n').append(']').append('\n');
            String explanation = "# DO NOT EDIT OR REMOVE this key or its contents\n"
                + "# It is used to keep track of keys that should not be re-added automatically\n"
                + "# Any key removed from this list will be restored to default\n";
            appendConfigSection(updated, explanation + cache);
        }

        Path temporary = Files.createTempFile(configFile.getParentFile().toPath(), CONFIG_NAME, ".tmp");
        try {
            Files.write(temporary, updated.toString().replace("\n", newline).getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temporary, configFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }

        Cells.LOGGER.info("Updated disassembly config at {} with {} new bundled entries",
            configFile.getAbsolutePath(), addedEntries);
    }

    private static void readProcessedKeys(@Nullable String value, Set<String> processedKeys) throws IOException {
        if (value == null || !value.startsWith("[") || !value.endsWith("]")) {
            throw new IOException("Malformed " + PROCESSED_ENTRIES_KEY + " list");
        }

        String content = value.substring(1, value.length() - 1).trim();
        if (content.isEmpty()) return;

        for (String part : content.split(",", -1)) {
            EntryKey key = EntryKey.parse(unquote(part.trim()));
            if (key == null) throw new IOException("Malformed processed disassembly key: " + part.trim());

            processedKeys.add(key.toString());
        }
    }

    private static void appendConfigSection(StringBuilder content, String section) {
        if (content.length() > 0) {
            char last = content.charAt(content.length() - 1);
            if (last != '\n') content.append('\n');

            content.append('\n');
        }

        content.append(section);
    }

    private static List<ConfigEntry> readConfigEntries(String content) throws IOException {
        List<ConfigEntry> entries = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String line;
            int lineNumber = 0;
            int offset = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                int start = offset;
                offset = Math.min(content.length(), offset + line.length() + 1);
                line = line.trim();

                // Skip empty lines and full-line comments
                if (line.isEmpty() || line.startsWith("#")) continue;

                // Remove comments on non-empty lines
                int commentIndex = line.indexOf('#');
                if (commentIndex >= 0) line = line.substring(0, commentIndex).trim();

                int separator = line.indexOf('=');
                if (separator < 1) {
                    Cells.LOGGER.warn("Malformed disassembly entry at line {}: '{}'", lineNumber, line);
                    continue;
                }

                String key = unquote(line.substring(0, separator).trim());
                String outputValue = line.substring(separator + 1).trim();
                StringBuilder builder = new StringBuilder(outputValue);
                if (!outputValue.startsWith("[")) {
                    entries.add(new ConfigEntry(key, outputValue, start, offset, lineNumber));
                    continue;
                }

                int endBracketIndex = outputValue.indexOf(']');
                while (endBracketIndex < 0) {
                    String continuation = reader.readLine();
                    if (continuation == null) {
                        Cells.LOGGER.warn("Unterminated disassembly outputs at line {}", lineNumber);
                        outputValue = null;
                        break;
                    }

                    lineNumber++;
                    offset = Math.min(content.length(), offset + continuation.length() + 1);
                    continuation = continuation.trim();
                    if (continuation.isEmpty() || continuation.startsWith("#")) continue;

                    commentIndex = continuation.indexOf('#');
                    if (commentIndex >= 0) continuation = continuation.substring(0, commentIndex).trim();

                    builder.append(continuation);
                    endBracketIndex = continuation.indexOf(']');
                }

                String value = outputValue == null ? null : builder.toString();
                entries.add(new ConfigEntry(key, value, start, offset, lineNumber));
            }
        }

        return entries;
    }

    private static String normalizeNewlines(String content) {
        return content.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static void parseConfig(File configFile) throws IOException {
        String content = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
        for (ConfigEntry entry : readConfigEntries(normalizeNewlines(content))) {
            if (PROCESSED_ENTRIES_KEY.equals(entry.key) || entry.value == null) continue;

            EntryKey entryKey = EntryKey.parse(entry.key);
            if (!entry.value.startsWith("[")) {
                EntryKey aliasKey = EntryKey.parse(unquote(entry.value));
                if (entryKey == null || aliasKey == null) {
                    Cells.LOGGER.warn("Malformed disassembly alias at line {}: '{} = {}'",
                        entry.lineNumber, entry.key, entry.value);
                    continue;
                }

                List<OutputSpec> aliasedOutputs = ENTRIES.get(aliasKey.toString());
                if (aliasedOutputs == null) {
                    Cells.LOGGER.warn("Disassembly alias at line {} must refer to an earlier entry", entry.lineNumber);
                    continue;
                }

                putEntry(entryKey, new ArrayList<>(aliasedOutputs));
                continue;
            }

            List<OutputSpec> outputs = parseOutputs(entry.value, entry.lineNumber);
            if (entryKey == null || outputs == null) continue;

            putEntry(entryKey, outputs);
        }
    }

    private static void putEntry(EntryKey entryKey, List<OutputSpec> outputs) {
        String sourceKey = entryKey.toString();
        if (ENTRIES.containsKey(sourceKey)) {
            Cells.LOGGER.warn(
                "Duplicate disassembly entry for {}. Overwriting previous entry.",
                sourceKey);
        }

        ENTRIES.put(sourceKey, outputs);
    }

    @Nullable
    private static List<OutputSpec> parseOutputs(String value, int lineNumber) {
        if (!value.startsWith("[") || !value.endsWith("]")) {
            Cells.LOGGER.warn("Malformed disassembly outputs at line {}: '{}'", lineNumber, value);
            return null;
        }

        String content = value.substring(1, value.length() - 1).trim();
        if (content.isEmpty()) return Collections.emptyList();

        List<OutputSpec> outputs = new ArrayList<>();
        for (String valuePart : content.split(",")) {
            OutputSpec output = OutputSpec.parse(unquote(valuePart.trim()));
            if (output == null || output.itemName.isEmpty()) {
                Cells.LOGGER.warn("Malformed disassembly output at line {}: '{}'", lineNumber, valuePart.trim());
                return null;
            }

            outputs.add(output);
        }

        return outputs;
    }

    private static void logMissingOutput(ItemStack source, String outputName) {
        String warningKey = source.getItem().getRegistryName() + "=" + outputName;
        if (!MISSING_OUTPUTS.add(warningKey)) return;

        Cells.LOGGER.warn("Disassembly output {} for {} is not registered", outputName,
            source.getItem().getRegistryName());
    }

    private static String unquote(String value) {
        if (value.length() < 2 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
            return value;
        }

        return value.substring(1, value.length() - 1);
    }

    private static final class ConfigEntry {

        private final String key;
        @Nullable
        private final String value;
        private final int start;
        private final int end;
        private final int lineNumber;

        private ConfigEntry(String key, @Nullable String value, int start, int end, int lineNumber) {
            this.key = key;
            this.value = value;
            this.start = start;
            this.end = end;
            this.lineNumber = lineNumber;
        }
    }

    private static final class EntryKey {

        private final String itemName;
        private final int metadata;

        private EntryKey(String itemName, int metadata) {
            this.itemName = itemName;
            this.metadata = metadata;
        }

        @Nullable
        private static EntryKey parse(String value) {
            ParsedItem parsed = ParsedItem.parse(value);
            return parsed == null ? null : new EntryKey(parsed.itemName, parsed.metadata);
        }

        @Override
        public String toString() {
            return itemName + "@" + metadata;
        }
    }

    private static final class OutputSpec {

        private final String itemName;
        private final int metadata;
        private final int count;

        private OutputSpec(String itemName, int metadata, int count) {
            this.itemName = itemName;
            this.metadata = metadata;
            this.count = count;
        }

        @Nullable
        private static OutputSpec parse(String value) {
            int countSeparator = value.lastIndexOf('*');
            String itemValue = countSeparator < 0 ? value : value.substring(0, countSeparator).trim();
            String countValue = countSeparator < 0 ? null : value.substring(countSeparator + 1).trim();
            int count = 1;
            if (countValue != null) {
                try {
                    count = Integer.parseInt(countValue);
                } catch (NumberFormatException e) {
                    return null;
                }

                if (count < 1) return null;
            }

            if (isSpecialToken(itemValue)) return new OutputSpec(itemValue, DEFAULT_METADATA, count);

            ParsedItem parsed = ParsedItem.parse(itemValue);
            return parsed == null ? null : new OutputSpec(parsed.itemName, parsed.metadata, count);
        }

        private boolean isSpecial() {
            return isSpecialToken(itemName);
        }
    }

    private static final class ParsedItem {

        private final String itemName;
        private final int metadata;

        private ParsedItem(String itemName, int metadata) {
            this.itemName = itemName;
            this.metadata = metadata;
        }

        @Nullable
        private static ParsedItem parse(String value) {
            int separator = value.lastIndexOf('@');
            String itemName = separator < 0 ? value : value.substring(0, separator);
            String metadataValue = separator < 0 ? null : value.substring(separator + 1);
            if (itemName.isEmpty()) return null;

            try {
                new ResourceLocation(itemName);
            } catch (Exception e) {
                return null;
            }

            if (metadataValue == null) return new ParsedItem(itemName, DEFAULT_METADATA);
            if (metadataValue.isEmpty()) return null;

            try {
                int metadata = Integer.parseInt(metadataValue);
                return metadata < 0 ? null : new ParsedItem(itemName, metadata);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
