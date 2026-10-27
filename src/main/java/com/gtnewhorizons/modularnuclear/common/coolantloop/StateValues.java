package com.gtnewhorizons.modularnuclear.common.coolantloop;

import java.util.Map;

/** Tolerant readers for persisted state maps (values may come back with a different numeric or boolean type). */
final class StateValues {

    private StateValues() {}

    static double dbl(Map<String, Object> m, String k, double def) {
        Object v = m.get(k);
        return v instanceof Number n ? n.doubleValue() : def;
    }

    static int integer(Map<String, Object> m, String k, int def) {
        Object v = m.get(k);
        return v instanceof Number n ? n.intValue() : def;
    }

    static long lng(Map<String, Object> m, String k, long def) {
        Object v = m.get(k);
        return v instanceof Number n ? n.longValue() : def;
    }

    static boolean bool(Map<String, Object> m, String k, boolean def) {
        Object v = m.get(k);
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        return def;
    }

    static String str(Map<String, Object> m, String k, String def) {
        Object v = m.get(k);
        return v != null ? v.toString() : def;
    }
}
