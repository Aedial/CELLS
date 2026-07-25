package com.cells.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;


public final class LocalizedTooltipText {

    private final String literal;
    private final String translationKey;
    private final List<LocalizedTooltipText> arguments;

    private LocalizedTooltipText(String literal, String translationKey, List<LocalizedTooltipText> arguments) {
        this.literal = literal;
        this.translationKey = translationKey;
        this.arguments = arguments;
    }

    @Nonnull
    public static LocalizedTooltipText literal(String text) {
        return new LocalizedTooltipText(text != null ? text : "", null, Collections.emptyList());
    }

    @Nonnull
    public static LocalizedTooltipText translated(String key, LocalizedTooltipText... arguments) {
        List<LocalizedTooltipText> args = new ArrayList<>();
        if (arguments != null) {
            for (LocalizedTooltipText argument : Arrays.asList(arguments)) {
                if (argument != null) args.add(argument);
            }
        }

        return new LocalizedTooltipText(null, key, Collections.unmodifiableList(args));
    }

    public boolean isLiteral() {
        return this.translationKey == null;
    }

    public boolean isEmpty() {
        return isLiteral() && this.literal.isEmpty();
    }

    @Nonnull
    public String getLiteral() {
        return this.literal != null ? this.literal : "";
    }

    @Nonnull
    public String getTranslationKey() {
        return this.translationKey != null ? this.translationKey : "";
    }

    @Nonnull
    public List<LocalizedTooltipText> getArguments() {
        return this.arguments;
    }
}