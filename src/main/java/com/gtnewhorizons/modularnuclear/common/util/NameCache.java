package com.gtnewhorizons.modularnuclear.common.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * A cache from names (fluid registry names, mostly) to what the configuration makes of them. Concurrent, a miss is
 * cached too, and it starts over when the {@link ConfigGeneration} moves on. Plain Java: the standalone simulator
 * uses it too.
 */
public final class NameCache<V> {

    private static final Object MISS = new Object();

    private final ConcurrentHashMap<String, Object> map = new ConcurrentHashMap<>();
    private volatile int generation = ConfigGeneration.current();

    /** The name's cached value, or what {@code loader} makes of it (null is cached as well). */
    @SuppressWarnings("unchecked")
    public V get(String name, Function<String, ? extends V> loader) {
        int g = ConfigGeneration.current();
        if (g != generation) {
            map.clear();
            generation = g;
        }
        Object hit = map.get(name);
        if (hit == null) {
            V v = loader.apply(name);
            map.put(name, v != null ? v : MISS);
            return v;
        }
        return hit == MISS ? null : (V) hit;
    }

    public void clear() {
        map.clear();
    }
}
