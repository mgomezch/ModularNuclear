package com.gtnewhorizons.modularnuclear.common.nuclear;

import java.util.Random;

/**
 * The reactor simulation's random source: SplitMix64 behind {@link Random}'s interface. Every {@code java.util.Random}
 * draw updates an {@code AtomicLong} with a compare-and-set (two per {@code nextDouble}), which neutron walks pay many
 * times per tile per cycle; this one is a plain field, an add and two multiply-xorshifts per 64 bits, with a 2^64
 * period and good statistical quality. It is not a fixed table of values replayed: a short replayed sequence would
 * give every cycle the same neutron histories.
 * <p>
 * Not thread safe: threads sharing one get a racy sequence (still random-looking, not reproducible). Seeding is
 * reproducible, but sequences differ from {@code java.util.Random}'s for the same seed.
 */
public final class FastRandom extends Random {

    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;
    private static long seedSequence = System.nanoTime();

    private long state;

    public FastRandom() {
        this(nextSeed());
    }

    public FastRandom(long seed) {
        super(0L);
        state = seed;
    }

    private static synchronized long nextSeed() {
        seedSequence += GOLDEN_GAMMA;
        return seedSequence ^ System.nanoTime();
    }

    @Override
    public void setSeed(long seed) {
        state = seed;
    }

    @Override
    public long nextLong() {
        long z = (state += GOLDEN_GAMMA);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    @Override
    protected int next(int bits) {
        return (int) (nextLong() >>> (64 - bits));
    }

    @Override
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    @Override
    public float nextFloat() {
        return (nextLong() >>> 40) * 0x1.0p-24f;
    }

    @Override
    public int nextInt() {
        return (int) nextLong();
    }

    /**
     * Uniform in [0, bound) by multiply-shift (Lemire, without the rejection step): the bias is at most bound / 2^32,
     * far below anything the simulation could notice.
     */
    @Override
    public int nextInt(int bound) {
        if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
        return (int) (((nextLong() >>> 32) * bound) >>> 32);
    }

    @Override
    public boolean nextBoolean() {
        return nextLong() < 0L;
    }
}
