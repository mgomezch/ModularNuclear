package com.gtnewhorizons.modularnuclear.common.util;

import java.util.IdentityHashMap;
import java.util.function.Function;

/**
 * A cache keyed by object identity (fluids, materials) to what the configuration makes of them. Reads take no lock
 * (the table is copied on write), a miss is cached too, and it starts over when the {@link ConfigGeneration} moves on.
 */
public final class IdentityCache<K, V> {

    private static final Object MISS = new Object();

    private volatile IdentityHashMap<K, Object> map = new IdentityHashMap<>();
    private volatile int generation = ConfigGeneration.current();

    /** The key's cached value, or what {@code loader} makes of it (null is cached as well). */
    @SuppressWarnings("unchecked")
    public V get(K key, Function<? super K, ? extends V> loader) {
        if (ConfigGeneration.current() != generation) clear();
        Object hit = map.get(key);
        if (hit == null) return add(key, loader);
        return hit == MISS ? null : (V) hit;
    }

    @SuppressWarnings("unchecked")
    private synchronized V add(K key, Function<? super K, ? extends V> loader) {
        Object hit = map.get(key);
        if (hit != null) return hit == MISS ? null : (V) hit;
        V v = loader.apply(key);
        IdentityHashMap<K, Object> next = new IdentityHashMap<>(map);
        next.put(key, v != null ? v : MISS);
        map = next;
        return v;
    }

    public synchronized void clear() {
        map = new IdentityHashMap<>();
        generation = ConfigGeneration.current();
    }
}
