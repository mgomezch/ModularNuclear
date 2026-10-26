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
 * Usage: {@code ReactorDefaultsGenerator <output source root> [<default_config dir> <package> <class> <name>...]...},
 * groups separated by {@code --}. Each group turns {@code <dir>/<name>.yaml} into a {@code <name>()} method returning
 * the document as JSON.
 */
public final class ReactorDefaultsGenerator {

    /** Keeps each string constant well below the class-file limit of 65535 bytes. */
    private static final int CHUNK = 8000;

    public static void main(String[] args) throws IOException {
        Path outRoot = Paths.get(args[0]);
        int i = 1;
        while (i < args.length) {
            Path configDir = Paths.get(args[i]);
            String pkg = args[i + 1];
            String cls = args[i + 2];
            java.util.List<String> names = new java.util.ArrayList<>();
            i += 3;
            while (i < args.length && !"--".equals(args[i])) names.add(args[i++]);
            i++;
            generate(outRoot, configDir, pkg, cls, names);
        }
    }

    private static void generate(Path outRoot, Path configDir, String pkg, String cls, java.util.List<String> names)
        throws IOException {
        Path outFile = outRoot.resolve(pkg.replace('.', '/'))
            .resolve(cls + ".java");
        Files.createDirectories(outFile.getParent());

        StringBuilder src = new StringBuilder();
        src.append("package ")
            .append(pkg)
            .append(";\n\n");
        src.append("/** Generated from ")
            .append(configDir.getFileName())
            .append("/*.yaml by ReactorDefaultsGenerator. */\n");
        src.append("public final class ")
            .append(cls)
            .append(" {\n\n    private ")
            .append(cls)
            .append("() {}\n");
        for (String name : names) {
            Object data;
            try (Reader r = Files.newBufferedReader(configDir.resolve(name + ".yaml"), StandardCharsets.UTF_8)) {
                data = new Yaml().load(r);
            }
            StringBuilder json = new StringBuilder();
            writeJson(json, data);
            src.append("\n    public static String ")
                .append(name)
                .append("() {\n        return new StringBuilder()");
            for (int k = 0; k < json.length(); k += CHUNK) {
                src.append("\n            .append(\"");
                escapeJava(src, json.substring(k, Math.min(json.length(), k + CHUNK)));
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
