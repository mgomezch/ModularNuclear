package com.gtnewhorizons.modularnuclear.configgen;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

/**
 * Build-time generator: converts the default YAML configuration into JSON string constants in a Java class, so that
 * code without a YAML parser (the TeaVM/WebAssembly simulator) loads exactly the same defaults as the game.
 *
 * <p>
 * Usage: {@code ReactorDefaultsGenerator <default_config dir> <output source root>}
 */
public final class ReactorDefaultsGenerator {

    private static final String PACKAGE = "com.gtnewhorizons.modularnuclear.common.config";
    private static final String CLASS = "ReactorDefaultsJson";
    private static final String[] FILES = { "physics", "tiers", "fuels", "components" };
    /** Keeps each string constant well below the class-file limit of 65535 bytes. */
    private static final int CHUNK = 8000;

    public static void main(String[] args) throws IOException {
        Path configDir = Paths.get(args[0]);
        Path outRoot = Paths.get(args[1]);
        Path outFile = outRoot.resolve(PACKAGE.replace('.', '/'))
            .resolve(CLASS + ".java");
        Files.createDirectories(outFile.getParent());

        StringBuilder src = new StringBuilder();
        src.append("package ")
            .append(PACKAGE)
            .append(";\n\n");
        src.append("/** Generated from assets/modularnuclear/default_config/*.yaml by ReactorDefaultsGenerator. */\n");
        src.append("public final class ")
            .append(CLASS)
            .append(" {\n\n    private ")
            .append(CLASS)
            .append("() {}\n");
        for (String name : FILES) {
            Object data;
            try (Reader r = Files.newBufferedReader(configDir.resolve(name + ".yaml"), StandardCharsets.UTF_8)) {
                data = new Yaml().load(r);
            }
            StringBuilder json = new StringBuilder();
            writeJson(json, data);
            src.append("\n    public static String ")
                .append(name)
                .append("() {\n        return new StringBuilder()");
            for (int i = 0; i < json.length(); i += CHUNK) {
                src.append("\n            .append(\"");
                escapeJava(src, json.substring(i, Math.min(json.length(), i + CHUNK)));
                src.append("\")");
            }
            src.append("\n            .toString();\n    }\n");
        }
        src.append("}\n");
        try (Writer w = Files.newBufferedWriter(outFile, StandardCharsets.UTF_8)) {
            w.write(src.toString());
        }
    }

    private static void writeJson(StringBuilder out, Object v) {
        if (v == null) {
            out.append("null");
        } else if (v instanceof Map<?, ?>map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) out.append(',');
                first = false;
                writeString(out, String.valueOf(e.getKey()));
                out.append(':');
                writeJson(out, e.getValue());
            }
            out.append('}');
        } else if (v instanceof List<?>list) {
            out.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) out.append(',');
                writeJson(out, list.get(i));
            }
            out.append(']');
        } else if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (Double.isNaN(d)) throw new IllegalArgumentException("NaN in config");
            // Always keep a decimal point or exponent so the value parses back as a double; infinities use the
            // (non-standard) Infinity / -Infinity literals that MiniJson accepts.
            out.append(Double.toString(d));
        } else if (v instanceof Number || v instanceof Boolean) {
            out.append(v);
        } else if (v instanceof String s) {
            writeString(out, s);
        } else {
            throw new IllegalArgumentException("Unsupported YAML value type: " + v.getClass());
        }
    }

    private static void writeString(StringBuilder out, String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20 || c > 0x7e) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    private static void escapeJava(StringBuilder out, String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') out.append('\\');
            out.append(c);
        }
    }
}
