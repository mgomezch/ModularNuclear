package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantFluidProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantLoopEngine;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopSegment;

/** The loop is adiabatic: moving coolant around it never creates or destroys heat, however fast it flows. */
public class CoolantLoopEnergyConservationTest {

    private static LoopSegment segment(String id, double length, double diameter, double temp) {
        return new LoopSegment(id, length, diameter, 0.00005, 0.5, 3000.0, 10000.0, temp);
    }

    private static void chain(LoopSegment from, LoopSegment to) {
        to.addUpstreamSegment(from);
    }

    /**
     * The loop the simulator builds: cold leg, a manifold feeding parallel passages, a collecting manifold, hot leg,
     * parallel PHEs between two more manifolds, and the return run. Temperatures start uneven.
     */
    private static CoolantLoopEngine loop(int passages, int phes, long seed) {
        Random random = new Random(seed);
        CoolantLoopEngine engine = new CoolantLoopEngine(CoolantFluidProperty.WATER);
        List<LoopSegment> segs = new ArrayList<>();
        LoopSegment prev = null;
        for (int i = 0; i < 4; i++) {
            LoopSegment s = segment("cold_" + i, 1.0, 0.2, 20 + random.nextDouble() * 500);
            if (prev != null) chain(prev, s);
            segs.add(s);
            prev = s;
        }
        LoopSegment first = segs.get(0);
        LoopSegment split = segment("manifold_in", 1.0, 0.2, 20 + random.nextDouble() * 500);
        split.setManifold(true);
        chain(prev, split);
        segs.add(split);
        LoopSegment join = segment("manifold_out", 1.0, 0.2, 20 + random.nextDouble() * 500);
        join.setManifold(true);
        for (int i = 0; i < passages; i++) {
            LoopSegment p = segment("passage_" + i, 5.0, 0.1, 20 + random.nextDouble() * 2000);
            p.setFlowFraction(1.0 / passages);
            chain(split, p);
            chain(p, join);
            segs.add(p);
        }
        segs.add(join);
        prev = join;
        for (int i = 0; i < 4; i++) {
            LoopSegment s = segment("hot_" + i, 1.0, 0.2, 20 + random.nextDouble() * 500);
            chain(prev, s);
            segs.add(s);
            prev = s;
        }
        LoopSegment pheIn = segment("phe_manifold_in", 1.0, 0.2, 20 + random.nextDouble() * 500);
        pheIn.setManifold(true);
        chain(prev, pheIn);
        segs.add(pheIn);
        LoopSegment pheOut = segment("phe_manifold_out", 1.0, 0.2, 20 + random.nextDouble() * 500);
        pheOut.setManifold(true);
        for (int j = 0; j < phes; j++) {
            LoopSegment phe = segment("phe_" + j, 2.0, 0.12, 20 + random.nextDouble() * 500);
            phe.setFlowFraction(1.0 / phes);
            chain(pheIn, phe);
            chain(phe, pheOut);
            segs.add(phe);
        }
        segs.add(pheOut);
        prev = pheOut;
        for (int i = 0; i < 4; i++) {
            LoopSegment s = segment("return_" + i, 1.0, 0.2, 20 + random.nextDouble() * 500);
            chain(prev, s);
            segs.add(s);
            prev = s;
        }
        chain(prev, first);
        for (LoopSegment s : segs) engine.addSegment(s);
        return engine;
    }

    /** Heat held by the coolant, up to the fluid's constant density and specific heat: sum of volume x temperature. */
    private static double heat(CoolantLoopEngine engine) {
        double h = 0.0;
        for (LoopSegment s : engine.getSegments()) h += s.getVolume() * s.getCurrentTemperatureCelsius();
        return h;
    }

    private static void assertConserved(double flowLitersPerSecond, int passages, int phes) {
        CoolantLoopEngine engine = loop(passages, phes, 42);
        engine.setMaxVolumetricFlowRate(10.0);
        double before = heat(engine);
        for (int t = 0; t < 200; t++) {
            // Hold the flow: the pump would, and this test is about advection alone
            engine.setVolumetricFlowRate(flowLitersPerSecond / 1000.0);
            engine.step(0.05);
        }
        assertEquals(before, heat(engine), Math.abs(before) * 1e-9, flowLitersPerSecond + " L/s");
    }

    @Test
    void testSlowFlowConservesHeat() {
        assertConserved(100.0, 38, 8);
    }

    @Test
    void testFlowSweepingSeveralSegmentsPerTickConservesHeat() {
        // 2,834 L/s sweeps ~142 L per tick through 31 L pipe segments
        assertConserved(2834.0, 38, 8);
    }

    @Test
    void testVeryFastFlowConservesHeat() {
        assertConserved(50000.0, 4, 1);
    }

    @Test
    void testFastFlowStillMixesTowardsUniformTemperature() {
        CoolantLoopEngine engine = loop(38, 8, 7);
        engine.setMaxVolumetricFlowRate(10.0);
        double before = spread(engine);
        for (int t = 0; t < 400; t++) {
            engine.setVolumetricFlowRate(2.834);
            engine.step(0.05);
        }
        assertTrue(spread(engine) < before * 0.1, "temperatures should even out as the coolant circulates");
    }

    private static double spread(CoolantLoopEngine engine) {
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (LoopSegment s : engine.getSegments()) {
            min = Math.min(min, s.getCurrentTemperatureCelsius());
            max = Math.max(max, s.getCurrentTemperatureCelsius());
        }
        return max - min;
    }
}
