package com.gtnewhorizons.modularnuclear.common.util;

import java.util.Arrays;
import java.util.List;

import net.minecraft.util.StatCollector;

/**
 * Localizes in the running game's language. Only for text the client shows (GUIs, tooltips, WAILA bodies); what the
 * server builds for players is a {@link LocalizedText}, which the client localizes with {@link #of(LocalizedText)}.
 */
public final class ClientText {

    private ClientText() {}

    public static String of(LocalizedText text) {
        return text.format(ClientText::pattern);
    }

    public static String get(String key) {
        return of(LocalizedText.of(key));
    }

    public static String format(String key, Object... args) {
        return of(LocalizedText.of(key, args));
    }

    /** A key's text split into lines at its "\n"s. */
    public static List<String> lines(String key) {
        return Arrays.asList(get(key).split("\n"));
    }

    /** The game's own lang falls back to English for keys a language lacks; so does this, outside the game. */
    private static String pattern(String key) {
        return StatCollector.canTranslate(key) ? StatCollector.translateToLocal(key) : EnglishLang.pattern(key);
    }
}
