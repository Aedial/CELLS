package com.cells.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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


/**
 * Startup-loaded disassembly entries for cells and upgrade cards.
 */
public final class DisassemblyConfig {

    public static final String CONFIG_NAME = "disassembly.cfg";

    private static final String CONFIG_SUBDIR = Tags.MODID;
    private static final String CONFIG_SOURCE = "/assets/" + Tags.MODID + "/" + CONFIG_NAME;
    public static final String HOUSING_TOKEN = "<housing>";
    public static final String COMPONENT_TOKEN = "<component>";
    private static final int DEFAULT_METADATA = 0;

    private static final Map<String, List<OutputSpec>> ENTRIES = new HashMap<>();
    private static final Set<String> MISSING_OUTPUTS = new HashSet<>();

    private DisassemblyConfig() {}

    /**
     * Loads the disassembly entries and creates the default file when needed.
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
        List<OutputSpec> outputSpecs = ENTRIES.get(itemName + "@" + source.getMetadata());
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

    private static void parseConfig(File configFile) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(configFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();

                // Skip empty lines and full-line comments
                if (line.isEmpty() || line.startsWith("#")) continue;

                // Remove comments on non-empty lines
                int commentIndex = line.indexOf('#');
                if (commentIndex >= 0) line = line.substring(0, commentIndex).trim();

                int separator = line.indexOf('=');
                if (separator < 1) {
                    Cells.LOGGER.warn("Malformed disassembly entry at line {}: {}", lineNumber, line);
                    continue;
                }

                EntryKey entryKey = EntryKey.parse(unquote(line.substring(0, separator).trim()));
                String outputValue = line.substring(separator + 1).trim();
                if (!outputValue.startsWith("[")) {
                    EntryKey aliasKey = EntryKey.parse(unquote(outputValue));
                    if (entryKey == null || aliasKey == null) {
                        Cells.LOGGER.warn("Malformed disassembly alias at line {}: {}", lineNumber, line);
                        continue;
                    }

                    List<OutputSpec> aliasedOutputs = ENTRIES.get(aliasKey.toString());
                    if (aliasedOutputs == null) {
                        Cells.LOGGER.warn("Disassembly alias at line {} must refer to an earlier entry", lineNumber);
                        continue;
                    }

                    ENTRIES.put(entryKey.toString(), new ArrayList<>(aliasedOutputs));
                    continue;
                }

                while (!outputValue.endsWith("]")) {
                    String continuation = reader.readLine();
                    if (continuation == null) {
                        Cells.LOGGER.warn("Unterminated disassembly outputs at line {}", lineNumber);
                        outputValue = null;
                        break;
                    }

                    lineNumber++;
                    continuation = continuation.trim();
                    if (continuation.isEmpty() || continuation.startsWith("#")) continue;

                    outputValue += continuation;
                }

                if (outputValue == null) continue;

                List<OutputSpec> outputs = parseOutputs(outputValue, lineNumber);
                if (entryKey == null || outputs == null) continue;

                ENTRIES.put(entryKey.toString(), outputs);
            }
        }
    }

    @Nullable
    private static List<OutputSpec> parseOutputs(String value, int lineNumber) {
        if (!value.startsWith("[") || !value.endsWith("]")) {
            Cells.LOGGER.warn("Malformed disassembly outputs at line {}: {}", lineNumber, value);
            return null;
        }

        String content = value.substring(1, value.length() - 1).trim();
        if (content.isEmpty()) return Collections.emptyList();

        List<OutputSpec> outputs = new ArrayList<>();
        for (String valuePart : content.split(",")) {
            OutputSpec output = OutputSpec.parse(unquote(valuePart.trim()));
            if (output == null) {
                Cells.LOGGER.warn("Malformed disassembly output at line {}: {}", lineNumber, valuePart.trim());
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
