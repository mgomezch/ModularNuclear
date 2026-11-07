package com.gtnewhorizons.modularnuclear.common.coolantloop;

/**
 * Names a part of a coolant loop: a pipe, device or manifold group at a block position (kind and position), or a
 * free-form name (the standalone simulator's segments). Equality and hashing use the fields, so the crawler and the
 * engine key their maps by these without building strings; the string form ({@code "pipe_10_64_-3"}) is made only
 * when shown or saved, and {@link #of} reads it back to the same id. Plain Java, shared with the simulator.
 */
public final class LoopId {

    private final String kind;
    private final int x, y, z;
    private final int hash;
    private String text;

    private LoopId(String kind, int x, int y, int z, String text) {
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.z = z;
        this.text = text;
        this.hash = kind != null ? ((kind.hashCode() * 31 + x) * 31 + y) * 31 + z : text.hashCode();
    }

    /** The {@code kind} part (pipe, heater, manifold_group, ...) at a block position. */
    public static LoopId at(String kind, int x, int y, int z) {
        return new LoopId(kind, x, y, z, null);
    }

    /** The id a string form names: {@code kind_x_y_z} for a positioned part, or else a free-form name. */
    public static LoopId of(String text) {
        int c3 = text.lastIndexOf('_');
        int c2 = c3 > 0 ? text.lastIndexOf('_', c3 - 1) : -1;
        int c1 = c2 > 0 ? text.lastIndexOf('_', c2 - 1) : -1;
        if (c1 > 0) {
            Integer x = parseInt(text, c1 + 1, c2), y = parseInt(text, c2 + 1, c3),
                z = parseInt(text, c3 + 1, text.length());
            if (x != null && y != null && z != null) return new LoopId(text.substring(0, c1), x, y, z, text);
        }
        return new LoopId(null, 0, 0, 0, text);
    }

    private static Integer parseInt(String s, int from, int to) {
        if (from >= to) return null;
        int i = from;
        boolean negative = s.charAt(i) == '-';
        if (negative && ++i == to) return null;
        long v = 0;
        for (; i < to; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return null;
            v = v * 10 + (c - '0');
            if (v > Integer.MAX_VALUE + 1L) return null;
        }
        v = negative ? -v : v;
        return v < Integer.MIN_VALUE || v > Integer.MAX_VALUE ? null : (int) v;
    }

    /** Whether this is a positioned part of the given kind. */
    public boolean isKind(String kind) {
        return kind.equals(this.kind);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LoopId that) || hash != that.hash) return false;
        if (kind == null) return that.kind == null && text.equals(that.text);
        return kind.equals(that.kind) && x == that.x && y == that.y && z == that.z;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        if (text == null) text = kind + "_" + x + "_" + y + "_" + z;
        return text;
    }
}
