package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantFluidProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantLoopEngine;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopSegment;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.TurbineCalculator;

/** Only high-pressure pipes have a pressure limit; other loop segments have none, said explicitly. */
public class LoopSegmentLimitsTest {

    private static LoopSegment device(String id) {
        return new LoopSegment(id, 1.0, 0.2, 0.000045, 0.5, LoopSegment.NO_LIMIT, 2000.0, 20.0);
    }

    private static CoolantLoopEngine loop(LoopSegment... segs) {
        CoolantLoopEngine e = new CoolantLoopEngine(CoolantFluidProperty.WATER);
        for (int i = 0; i < segs.length; i++) {
            if (i > 0) segs[i].addUpstreamSegment(segs[i - 1]);
            e.addSegment(segs[i]);
        }
        segs[0].addUpstreamSegment(segs[segs.length - 1]);
        e.setMaxVolumetricFlowRate(100.0);
        e.setPumpPowered(true);
        e.setPumpMechanicalPowerWatts(5.0e8);
        return e;
    }

    @Test
    void testOnlyLimitedSegmentsBurst() {
        CoolantLoopEngine devices = loop(device("a"), device("b"), device("c"));
        double peak = 0.0;
        for (int i = 0; i < 20; i++) {
            devices.step(0.05);
            peak = Math.max(peak, devices.getPeakLoopPressureBar());
        }
        assertTrue(peak > 600.0, "the pump pushes hundreds of bar from standstill: " + peak);
        assertFalse(devices.isRuptured(), "segments without a pressure limit do not burst");

        LoopSegment pipe = new LoopSegment("pipe", 1.0, 0.2, 0.000045, 0.05, 600.0, 3000.0, 20.0);
        CoolantLoopEngine withPipe = loop(pipe, device("b"), device("c"));
        for (int i = 0; i < 20 && !withPipe.isRuptured(); i++) withPipe.step(0.05);
        assertTrue(withPipe.isRuptured(), "a pipe still bursts above its rating");
    }

    @Test
    void testMissingLimitIsExplicit() {
        LoopSegment d = device("d");
        assertFalse(d.hasPressureLimit());
        assertTrue(d.hasTemperatureLimit());
        assertThrows(IllegalStateException.class, d::getMaxPressureBar);
        assertEquals(2000.0, d.getMaxTemperatureCelsius(), 0.0);
    }

    @Test
    void testUnlimitedRoundTripsThroughSavedState() {
        Map<String, Object> state = device("d").toStateMap();
        assertEquals(LoopSegment.NO_LIMIT_STATE, state.get("pMax"));
        LoopSegment back = LoopSegment.fromStateMap(state, new ArrayList<>());
        assertFalse(back.hasPressureLimit());
        assertEquals(2000.0, back.getMaxTemperatureCelsius(), 0.0);

        state.put("pMax", 600.0); // a save from before, with a number
        assertEquals(
            600.0,
            LoopSegment.fromStateMap(state, new ArrayList<>())
                .getMaxPressureBar(),
            0.0);
    }

    @Test
    void testSupercriticalSteamBelowZpmIsReportedWasted() {
        TurbineCalculator.PowerEstimationResult luv = TurbineCalculator.calculatePower(
            NuclearSimulationEngine.PIPE_TIER_OSMIUM,
            0,
            0,
            1000.0,
            0,
            0,
            0,
            0,
            TurbineCalculator.TurbineMaterial.values()[0],
            TurbineCalculator.TurbineSize.LARGE,
            TurbineCalculator.FittingMode.TIGHT);
        assertEquals(1000.0, luv.voidedSupercriticalSteam, 1e-9);
        TurbineCalculator.PowerEstimationResult ev = TurbineCalculator.calculatePower(
            NuclearSimulationEngine.PIPE_TIER_ELECTRUM,
            0,
            500.0,
            0,
            0,
            0,
            0,
            0,
            TurbineCalculator.TurbineMaterial.values()[0],
            TurbineCalculator.TurbineSize.LARGE,
            TurbineCalculator.FittingMode.TIGHT);
        assertEquals(500.0, ev.voidedSuperheatedSteam, 1e-9);
    }
}
