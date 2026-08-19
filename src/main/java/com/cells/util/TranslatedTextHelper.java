package com.cells.util;

import net.minecraft.util.text.ITextComponent;


/**
 * Normalizes translated text after localization has been resolved.
 * .lang file entries keep inline escape sequences such as \n literal,
 * so callers that want multiline output must convert them explicitly.
 */
public final class TranslatedTextHelper {

    private TranslatedTextHelper() {
    }

    public static String normalizeEscapedNewlines(String text) {
        if (text == null || text.indexOf('\\') < 0) return text;

        return text.replace("\\n", "\n");
    }

    public static String resolveComponentText(ITextComponent component) {
        return normalizeEscapedNewlines(component.getUnformattedText());
    }
}