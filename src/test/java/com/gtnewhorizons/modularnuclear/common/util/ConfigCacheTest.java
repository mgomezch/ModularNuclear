package com.gtnewhorizons.modularnuclear.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/** The configuration caches load each key once, cache misses too, and start over when a configuration loads. */
class ConfigCacheTest {

    @Test
    void longKeyCacheLoadsOnceAndForgetsOnANewGeneration() {
        LongKeyCache<String> cache = new LongKeyCache<>();
        AtomicInteger loads = new AtomicInteger();
        LongKeyCache.Loader<AtomicInteger, String> loader = (n, k) -> {
            n.incrementAndGet();
            return k == 7L ? null : "v" + k;
        };
        assertEquals("v3", cache.get(3L, loads, loader));
        assertEquals("v3", cache.get(3L, loads, loader));
        assertNull(cache.get(7L, loads, loader));
        assertNull(cache.get(7L, loads, loader));
        assertEquals(2, loads.get(), "a hit and a cached miss load nothing");

        ConfigGeneration.bump();
        assertEquals("v3", cache.get(3L, loads, loader));
        assertEquals(3, loads.get(), "a new configuration loads again");
    }

    @Test
    void keyZeroIsNeverCached() {
        LongKeyCache<String> cache = new LongKeyCache<>();
        AtomicInteger loads = new AtomicInteger();
        cache.get(0L, loads, (n, k) -> "x" + n.incrementAndGet());
        cache.get(0L, loads, (n, k) -> "x" + n.incrementAndGet());
        assertEquals(2, loads.get());
    }

    @Test
    void identityAndNameCachesBehaveTheSame() {
        IdentityCache<Object, String> byIdentity = new IdentityCache<>();
        NameCache<String> byName = new NameCache<>();
        AtomicInteger loads = new AtomicInteger();
        Object key = new Object();
        byIdentity.get(key, k -> {
            loads.incrementAndGet();
            return null;
        });
        byIdentity.get(key, k -> {
            loads.incrementAndGet();
            return null;
        });
        byName.get("water", n -> "w" + loads.incrementAndGet());
        assertEquals("w2", byName.get("water", n -> "w" + loads.incrementAndGet()));
        assertEquals(2, loads.get());

        ConfigGeneration.bump();
        assertEquals("w3", byName.get("water", n -> "w" + loads.incrementAndGet()));
    }
}
