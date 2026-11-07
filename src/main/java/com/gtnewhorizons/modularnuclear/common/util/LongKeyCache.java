package com.gtnewhorizons.modularnuclear.common.util;

/**
 * A cache from {@code long} keys (item kinds, see {@code ReactorDataRegistry.getItemKey}) to what the configuration
 * makes of them, read every tick on the server and client threads. Reads take no lock and allocate nothing; a miss is
 * cached too. It starts over when the {@link ConfigGeneration} moves on. Key 0 ("no item") is never cached.
 */
public final class LongKeyCache<V> {

    /** Computes a key's value from state the caller passes (so a lookup allocates no capturing lambda). */
    @FunctionalInterface
    public interface Loader<S, V> {

        V load(S state, long key);
    }

    private static final Object MISS = new Object();

    private final LongKeyMap<Object> map = new LongKeyMap<>();
    private volatile int generation = ConfigGeneration.current();

    /** The key's cached value, or what {@code loader} makes of it (null is cached as well). */
    @SuppressWarnings("unchecked")
    public <S> V get(long key, S state, Loader<S, V> loader) {
        checkGeneration();
        Object hit = map.get(key);
        if (hit == null) {
            V v = loader.load(state, key);
            if (key != 0L) map.put(key, v != null ? v : MISS);
            return v;
        }
        return hit == MISS ? null : (V) hit;
    }

    /** Fills in a key's value ahead of its first lookup. */
    public void put(long key, V value) {
        checkGeneration();
        map.put(key, value != null ? value : MISS);
    }

    public void clear() {
        map.clear();
    }

    private void checkGeneration() {
        int g = ConfigGeneration.current();
        if (g == generation) return;
        synchronized (this) {
            if (g != generation) {
                map.clear();
                generation = g;
            }
        }
    }
}
