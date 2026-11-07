package com.gtnewhorizons.modularnuclear.common.nuclear.standalone.cli;

import java.util.Locale;

/** Compact JSON output: objects, arrays and fields, with the commas placed for the caller. */
public final class JsonWriter {

    private final StringBuilder sb = new StringBuilder();
    private boolean needComma = false;

    private JsonWriter separate() {
        if (needComma) sb.append(',');
        needComma = true;
        return this;
    }

    public JsonWriter beginObject() {
        separate().sb.append('{');
        needComma = false;
        return this;
    }

    public JsonWriter endObject() {
        sb.append('}');
        needComma = true;
        return this;
    }

    public JsonWriter beginArray() {
        separate().sb.append('[');
        needComma = false;
        return this;
    }

    public JsonWriter endArray() {
        sb.append(']');
        needComma = true;
        return this;
    }

    /** A field name; the next value is its value. */
    public JsonWriter key(String name) {
        separate().sb.append('"')
            .append(name)
            .append("\":");
        needComma = false;
        return this;
    }

    public JsonWriter value(String s) {
        separate().sb.append('"')
            .append(escape(s))
            .append('"');
        return this;
    }

    public JsonWriter value(boolean b) {
        separate().sb.append(b);
        return this;
    }

    public JsonWriter value(long n) {
        separate().sb.append(n);
        return this;
    }

    /** A double as Java prints it (shortest round-trip form). */
    public JsonWriter value(double d) {
        separate().sb.append(d);
        return this;
    }

    /** A double with a fixed number of decimals; infinite values are written as the string "Infinity". */
    public JsonWriter fixed(double d, int decimals) {
        if (Double.isInfinite(d)) return value("Infinity");
        separate().sb.append(String.format(Locale.US, "%." + decimals + "f", d));
        return this;
    }

    public JsonWriter field(String name, String s) {
        return key(name).value(s);
    }

    public JsonWriter field(String name, boolean b) {
        return key(name).value(b);
    }

    public JsonWriter field(String name, long n) {
        return key(name).value(n);
    }

    public JsonWriter field(String name, double d) {
        return key(name).value(d);
    }

    public JsonWriter fixed(String name, double d, int decimals) {
        return key(name).fixed(d, decimals);
    }

    /** A field holding an array of doubles with a fixed number of decimals. */
    public JsonWriter fixedArray(String name, double[] values, int count, int decimals) {
        key(name).beginArray();
        for (int i = 0; i < count; i++) fixed(values[i], decimals);
        return endArray();
    }

    public JsonWriter object(String name) {
        return key(name).beginObject();
    }

    public JsonWriter array(String name) {
        return key(name).beginArray();
    }

    @Override
    public String toString() {
        return sb.toString();
    }

    /** Escapes quotes, backslashes and line breaks; null is written as the empty string. */
    public static String escape(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
