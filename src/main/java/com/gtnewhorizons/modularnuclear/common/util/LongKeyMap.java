package com.gtnewhorizons.modularnuclear.common.util;

/**
 * A map from {@code long} keys to values for lookups on the server tick: a read takes no lock and allocates nothing
 * (no boxed keys, no entry objects), and a write copies the table under a lock. Meant for caches filled once per
 * distinct key (item keys, see {@code ReactorDataRegistry.getItemKey}) and read every tick, from the server and client
 * threads alike. Key 0 means "no item" and is never stored.
 */
public final class LongKeyMap<V> {

    private static final class Table {

        final long[] keys;
        final Object[] values;
        final int size;

        Table(int capacity, int size) {
            this.keys = new long[capacity];
            this.values = new Object[capacity];
            this.size = size;
        }
    }

    private volatile Table table = new Table(16, 0);

    private static int slot(long key, int mask) {
        long h = key * 0x9E3779B97F4A7C15L;
        return (int) (h ^ (h >>> 32)) & mask;
    }

    @SuppressWarnings("unchecked")
    public V get(long key) {
        if (key == 0L) return null;
        Table t = table;
        int mask = t.keys.length - 1;
        for (int i = slot(key, mask);; i = (i + 1) & mask) {
            long k = t.keys[i];
            if (k == key) return (V) t.values[i];
            if (k == 0L) return null;
        }
    }

    public synchronized void put(long key, V value) {
        if (key == 0L) return;
        Table old = table;
        int size = old.size + (contains(old, key) ? 0 : 1);
        int capacity = old.keys.length;
        // Keep the table at most half full, so probes stay short
        while (size * 2 > capacity) capacity *= 2;
        Table t = new Table(capacity, size);
        int mask = capacity - 1;
        for (int j = 0; j < old.keys.length; j++) {
            if (old.keys[j] != 0L && old.keys[j] != key) insert(t, mask, old.keys[j], old.values[j]);
        }
        insert(t, mask, key, value);
        table = t;
    }

    private static boolean contains(Table t, long key) {
        int mask = t.keys.length - 1;
        for (int i = slot(key, mask);; i = (i + 1) & mask) {
            if (t.keys[i] == key) return true;
            if (t.keys[i] == 0L) return false;
        }
    }

    private static void insert(Table t, int mask, long key, Object value) {
        int i = slot(key, mask);
        while (t.keys[i] != 0L) i = (i + 1) & mask;
        t.keys[i] = key;
        t.values[i] = value;
    }

    public synchronized void clear() {
        table = new Table(16, 0);
    }

    public int size() {
        return table.size;
    }
}
