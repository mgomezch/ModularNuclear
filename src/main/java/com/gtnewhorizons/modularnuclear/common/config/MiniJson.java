package com.gtnewhorizons.modularnuclear.common.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON parser producing the same value shapes SnakeYAML produces for the reactor config (LinkedHashMap,
 * ArrayList, String, Integer/Long, Double, Boolean, null). It avoids regular expressions and String.replace so that it
 * compiles under TeaVM for the WebAssembly simulator.
 */
public final class MiniJson {

    private final String s;
    private int pos;

    private MiniJson(String s) {
        this.s = s;
    }

    public static Object parse(String json) {
        MiniJson p = new MiniJson(json);
        p.skipWhitespace();
        Object v = p.readValue();
        p.skipWhitespace();
        if (p.pos != p.s.length()) throw p.error("Trailing characters");
        return v;
    }

    private IllegalArgumentException error(String msg) {
        return new IllegalArgumentException(msg + " at offset " + pos);
    }

    private void skipWhitespace() {
        while (pos < s.length()) {
            char c = s.charAt(pos);
            if (c != ' ' && c != '\n' && c != '\r' && c != '\t') return;
            pos++;
        }
    }

    private void expect(char c) {
        if (pos >= s.length() || s.charAt(pos) != c) throw error("Expected '" + c + "'");
        pos++;
    }

    private boolean consumeLiteral(String lit) {
        if (s.startsWith(lit, pos)) {
            pos += lit.length();
            return true;
        }
        return false;
    }

    private Object readValue() {
        if (pos >= s.length()) throw error("Unexpected end of input");
        char c = s.charAt(pos);
        switch (c) {
            case '{':
                return readObject();
            case '[':
                return readArray();
            case '"':
                return readString();
            default:
                if (consumeLiteral("true")) return Boolean.TRUE;
                if (consumeLiteral("false")) return Boolean.FALSE;
                if (consumeLiteral("null")) return null;
                if (consumeLiteral("Infinity")) return Double.POSITIVE_INFINITY;
                if (consumeLiteral("-Infinity")) return Double.NEGATIVE_INFINITY;
                return readNumber();
        }
    }

    private Map<String, Object> readObject() {
        expect('{');
        Map<String, Object> map = new LinkedHashMap<>();
        skipWhitespace();
        if (pos < s.length() && s.charAt(pos) == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            String key = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            map.put(key, readValue());
            skipWhitespace();
            if (pos < s.length() && s.charAt(pos) == ',') {
                pos++;
                continue;
            }
            expect('}');
            return map;
        }
    }

    private List<Object> readArray() {
        expect('[');
        List<Object> list = new ArrayList<>();
        skipWhitespace();
        if (pos < s.length() && s.charAt(pos) == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipWhitespace();
            list.add(readValue());
            skipWhitespace();
            if (pos < s.length() && s.charAt(pos) == ',') {
                pos++;
                continue;
            }
            expect(']');
            return list;
        }
    }

    private String readString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= s.length()) throw error("Unterminated string");
            char c = s.charAt(pos++);
            if (c == '"') return sb.toString();
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (pos >= s.length()) throw error("Unterminated escape");
            char e = s.charAt(pos++);
            switch (e) {
                case '"':
                case '\\':
                case '/':
                    sb.append(e);
                    break;
                case 'n':
                    sb.append('\n');
                    break;
                case 'r':
                    sb.append('\r');
                    break;
                case 't':
                    sb.append('\t');
                    break;
                case 'b':
                    sb.append('\b');
                    break;
                case 'f':
                    sb.append('\f');
                    break;
                case 'u':
                    if (pos + 4 > s.length()) throw error("Bad unicode escape");
                    sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                    pos += 4;
                    break;
                default:
                    throw error("Bad escape '\\" + e + "'");
            }
        }
    }

    private Object readNumber() {
        int start = pos;
        boolean isDouble = false;
        if (pos < s.length() && (s.charAt(pos) == '-' || s.charAt(pos) == '+')) pos++;
        while (pos < s.length()) {
            char c = s.charAt(pos);
            if (c >= '0' && c <= '9') {
                pos++;
            } else if (c == '.' || c == 'e' || c == 'E' || c == '-' || c == '+') {
                isDouble = true;
                pos++;
            } else {
                break;
            }
        }
        String num = s.substring(start, pos);
        if (num.isEmpty() || "-".equals(num) || "+".equals(num)) throw error("Bad value");
        if (isDouble) return Double.parseDouble(num);
        long l = Long.parseLong(num);
        if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) return (int) l;
        return l;
    }
}
