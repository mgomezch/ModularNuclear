package com.gtnewhorizons.modularnuclear.common.coolantloop.pump;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.TestCoolantPump;
import com.gtnewhorizons.modularnuclear.TestEnvironmentHelper;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTECoolantPump;

class CoolantPumpFillRateTest {

    @BeforeAll
    static void init() {
        TestEnvironmentHelper.init();
    }

    private static MTECoolantPump pump(long configuredRate, long... fillVolumes) {
        MTECoolantPump pump = new TestCoolantPump("fill_rate_test_pump");
        pump.getPumpCore().mFillRateLitersPerTick = configuredRate;
        pump.getPumpCore().mFillOrderCumulative = new long[fillVolumes.length];
        long total = 0L;
        for (int i = 0; i < fillVolumes.length; i++) {
            total += fillVolumes[i];
            pump.getPumpCore().mFillOrderCumulative[i] = total;
        }
        pump.getPumpCore().mRequiredFillLiters = total;
        return pump;
    }

    @Test
    void smallPipesFillAtTheConfiguredRate() {
        MTECoolantPump pump = pump(1000L, 2400L, 2400L, 1000L);
        assertEquals(
            1000L,
            pump.getPumpCore()
                .currentFillRateLitersPerTick());
    }

    @Test
    void largePipesFillAtLeastFourPerSecond() {
        // Infinity huge pipes hold 240 000 L each: 48 000 L/t fills four a second
        MTECoolantPump pump = pump(1000L, 240_000L, 240_000L, 1000L);
        assertEquals(
            48_000L,
            pump.getPumpCore()
                .currentFillRateLitersPerTick());
    }

    @Test
    void rateFollowsThePartBeingFilledInCrawlOrder() {
        MTECoolantPump pump = pump(1000L, 2400L, 240_000L, 1000L);
        pump.getPumpCore().mCurrentFillLiters = 0L;
        assertEquals(
            1000L,
            pump.getPumpCore()
                .currentFillRateLitersPerTick(),
            "First part: a small pipe");
        pump.getPumpCore().mCurrentFillLiters = 2400L;
        assertEquals(
            48_000L,
            pump.getPumpCore()
                .currentFillRateLitersPerTick(),
            "Second part: a huge pipe");
        pump.getPumpCore().mCurrentFillLiters = 242_400L;
        assertEquals(
            1000L,
            pump.getPumpCore()
                .currentFillRateLitersPerTick(),
            "Last part: a device");
    }

    @Test
    void uncrawledLoopFillsAtTheConfiguredRate() {
        MTECoolantPump pump = new TestCoolantPump("fill_rate_test_pump");
        pump.getPumpCore().mFillRateLitersPerTick = 1000L;
        assertEquals(
            1000L,
            pump.getPumpCore()
                .currentFillRateLitersPerTick());
    }
}
