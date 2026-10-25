package com.gtnewhorizons.modularnuclear.common.nuclear;

import java.util.Locale;

/**
 * String helpers that are safe in code compiled to WebAssembly by TeaVM.
 * <p>
 * TeaVM 0.10.2's WebAssembly backend does not emit {@link String#replace(CharSequence, CharSequence)} or
 * {@link String#replace(char, char)}: calls to either compile to an indirect call through an empty method-table slot
 * and trap at runtime with "table index is out of bounds". Code reachable from {@code NuclearSimWasmBridge} must use
 * these helpers instead of {@code String.replace}.
 */
public final class WasmStrings {

    private WasmStrings() {}

    /**
     * Equivalent to {@code s.replace(target, replacement)} for non-empty {@code target}.
     */
    public static String replace(String s, String target, String replacement) {
        if (s == null || target == null || target.isEmpty()) return s;
        int idx = s.indexOf(target);
        if (idx < 0) return s;
        StringBuilder sb = new StringBuilder(s.length());
        int from = 0;
        while (idx >= 0) {
            sb.append(s.substring(from, idx))
                .append(replacement);
            from = idx + target.length();
            idx = s.indexOf(target, from);
        }
        sb.append(s.substring(from));
        return sb.toString();
    }

    /**
     * Normalizes a user-supplied enum token: trims, upper-cases (locale-independent), and replaces every character in
     * {@code separators} with {@code '_'}. For example {@code normalizeToken(" cast-iron ", " -")} is
     * {@code "CAST_IRON"}.
     */
    public static String normalizeToken(String str, String separators) {
        char[] chars = str.trim()
            .toUpperCase(Locale.ROOT)
            .toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (separators.indexOf(chars[i]) >= 0) {
                chars[i] = '_';
            }
        }
        return new String(chars);
    }
}
