package com.gtnewhorizons.modularnuclear.common.util;

import java.util.Arrays;
import java.util.function.Function;

/**
 * A player-facing line as a lang key and its arguments, localized only where it is shown. The server builds these,
 * saves them and syncs them; each client formats them in its own language ({@code ClientText}), and logs, tests and
 * computer APIs in English ({@link EnglishLang}).
 * <p>
 * Arguments are strings, numbers, booleans or other {@code LocalizedText}s (localized in turn); anything else is kept
 * as its {@code toString()}. Plain Java, so the standalone simulator can use it.
 */
public final class LocalizedText {

    /** The key of a line that is not localized: its single argument is the text. */
    public static final String LITERAL_KEY = "";

    public static final LocalizedText EMPTY = literal("");

    public final String key;
    private final Object[] args;

    private LocalizedText(String key, Object[] args) {
        this.key = key;
        this.args = args;
    }

    public static LocalizedText of(String key, Object... args) {
        Object[] kept = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            kept[i] = keep(args[i]);
        }
        return new LocalizedText(key, kept);
    }

    /** Text that is shown as it is (a player's input, a name the game already gave). */
    public static LocalizedText literal(String text) {
        return new LocalizedText(LITERAL_KEY, new Object[] { text != null ? text : "" });
    }

    private static Object keep(Object arg) {
        if (arg == null) return "";
        if (arg instanceof String || arg instanceof LocalizedText || arg instanceof Boolean) return arg;
        if (arg instanceof Integer || arg instanceof Long || arg instanceof Short || arg instanceof Byte) {
            return ((Number) arg).longValue();
        }
        if (arg instanceof Number) return ((Number) arg).doubleValue();
        return arg.toString();
    }

    public boolean isLiteral() {
        return LITERAL_KEY.equals(key);
    }

    public int argCount() {
        return args.length;
    }

    /** The i-th argument: a String, Long, Double, Boolean or LocalizedText. */
    public Object arg(int i) {
        return args[i];
    }

    /**
     * Formats the line with a lang's patterns (a key to its pattern, or null if the lang has none, when the key itself
     * is shown). Literal "\n" in a pattern becomes a line break.
     */
    public String format(Function<String, String> patterns) {
        if (isLiteral()) return (String) args[0];
        Object[] shown = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object a = args[i];
            shown[i] = a instanceof LocalizedText t ? t.format(patterns) : a;
        }
        String pattern = patterns.apply(key);
        if (pattern == null) return key;
        try {
            return newlines(String.format(pattern, shown));
        } catch (RuntimeException e) {
            return newlines(pattern);
        }
    }

    /** The line in English, for logs, tests and computer APIs. */
    public String english() {
        return format(EnglishLang::pattern);
    }

    @Override
    public String toString() {
        return english();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LocalizedText that)) return false;
        return key.equals(that.key) && Arrays.equals(args, that.args);
    }

    @Override
    public int hashCode() {
        return 31 * key.hashCode() + Arrays.hashCode(args);
    }

    private static String newlines(String s) {
        int i = s.indexOf("\\n");
        if (i < 0) return s;
        StringBuilder sb = new StringBuilder(s.length());
        int from = 0;
        while (i >= 0) {
            sb.append(s, from, i)
                .append('\n');
            from = i + 2;
            i = s.indexOf("\\n", from);
        }
        return sb.append(s, from, s.length())
            .toString();
    }
}
