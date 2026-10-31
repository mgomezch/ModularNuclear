package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.nuclear.FastRandom;
import com.gtnewhorizons.modularnuclear.common.nuclear.TierScale;

public class FastRandomAndTierScaleTest {

    @Test
    void testSeededSequencesRepeat() {
        FastRandom a = new FastRandom(42L);
        FastRandom b = new FastRandom(7L);
        b.setSeed(42L);
        for (int i = 0; i < 100; i++) assertEquals(a.nextLong(), b.nextLong());
    }

    @Test
    void testDrawsAreInRangeAndRoughlyUniform() {
        FastRandom r = new FastRandom(1234L);
        int n = 400_000;
        double sum = 0.0;
        int[] buckets = new int[4];
        for (int i = 0; i < n; i++) {
            double d = r.nextDouble();
            assertTrue(d >= 0.0 && d < 1.0);
            sum += d;
            int k = r.nextInt(4);
            assertTrue(k >= 0 && k < 4);
            buckets[k]++;
        }
        assertEquals(0.5, sum / n, 0.005);
        for (int c : buckets) assertEquals(n / 4.0, c, n * 0.01);
    }

    @Test
    void testTierScaleMatchesMathPow() {
        TierScale s = new TierScale(2);
        for (int tier = -1; tier < 20; tier++) {
            int t = Math.max(0, tier);
            assertEquals(1.5 * Math.pow(2.65, t - 2), s.get(1.5, 2.65, tier));
            assertEquals(Math.pow(2.0, t), TierScale.pow2(tier));
        }
        // A changed ratio rebuilds the table
        assertEquals(1.5 * Math.pow(3.0, 3), s.get(1.5, 3.0, 5));
    }
}
