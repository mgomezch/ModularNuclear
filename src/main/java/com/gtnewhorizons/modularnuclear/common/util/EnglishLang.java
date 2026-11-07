package com.gtnewhorizons.modularnuclear.common.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.util.StatCollector;

/**
 * The mod's English lang file, read from the jar: what logs, tests and computer APIs show a {@link LocalizedText} as.
 * Players see their own language instead ({@code ClientText}).
 */
public final class EnglishLang {

    private static final String PATH = "/assets/modularnuclear/lang/en_US.lang";
    private static Map<String, String> patterns;

    private EnglishLang() {}

    /**
     * The English pattern of a key, or null if there is none. Keys of other mods (GregTech's shutdown reasons) come
     * from the game's own lang, which on a server is English.
     */
    public static String pattern(String key) {
        if (patterns == null) patterns = load();
        String p = patterns.get(key);
        if (p == null && StatCollector.canTranslate(key)) p = StatCollector.translateToLocal(key);
        return p;
    }

    private static Map<String, String> load() {
        Map<String, String> map = new HashMap<>();
        try (InputStream is = EnglishLang.class.getResourceAsStream(PATH)) {
            if (is == null) return map;
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq > 0) map.put(line.substring(0, eq), line.substring(eq + 1));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return map;
    }
}
