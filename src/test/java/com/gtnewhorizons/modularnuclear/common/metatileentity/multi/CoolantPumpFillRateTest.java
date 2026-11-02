package com.gtnewhorizons.modularnuclear.common.metatileentity.multi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.TestEnvironmentHelper;

class CoolantPumpFillRateTest {

    @BeforeAll
    static void init() {
        TestEnvironmentHelper.init();
    }

    private static MTECoolantPump pump(long configuredRate, long... fillVolumes) {
        MTECoolantPump pump = new MTECoolantPump("fill_rate_test_pump");
        pump.mFillRateLitersPerTick = configuredRate;
        pump.mFillOrderCumulative = new long[fillVolumes.length];
        long total = 0L;
        for (int i = 0; i < fillVolumes.length; i++) {
            total += fillVolumes[i];
            pump.mFillOrderCumulative[i] = total;
        }
        pump.mRequiredFillLiters = total;
        return pump;
    }

    @Test
    void smallPipesFillAtTheConfiguredRate() {
        MTECoolantPump pump = pump(1000L, 2400L, 2400L, 1000L);
        assertEquals(1000L, pump.currentFillRateLitersPerTick());
    }

    @Test
    void largePipesFillAtLeastOnePerSecond() {
        // Infinity huge pipes hold 240 000 L each: 12 000 L/t fills one a second
        MTECoolantPump pump = pump(1000L, 240_000L, 240_000L, 1000L);
        assertEquals(12_000L, pump.currentFillRateLitersPerTick());
    }

    @Test
    void rateFollowsThePartBeingFilledInCrawlOrder() {
        MTECoolantPump pump = pump(1000L, 2400L, 240_000L, 1000L);
        pump.mCurrentFillLiters = 0L;
        assertEquals(1000L, pump.currentFillRateLitersPerTick(), "First part: a small pipe");
        pump.mCurrentFillLiters = 2400L;
        assertEquals(12_000L, pump.currentFillRateLitersPerTick(), "Second part: a huge pipe");
        pump.mCurrentFillLiters = 242_400L;
        assertEquals(1000L, pump.currentFillRateLitersPerTick(), "Last part: a device");
    }

    @Test
    void uncrawledLoopFillsAtTheConfiguredRate() {
        MTECoolantPump pump = new MTECoolantPump("fill_rate_test_pump");
        pump.mFillRateLitersPerTick = 1000L;
        assertEquals(1000L, pump.currentFillRateLitersPerTick());
    }
}
