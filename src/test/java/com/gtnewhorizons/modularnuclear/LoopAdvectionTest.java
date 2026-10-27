package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantFluidProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantLoopEngine;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopAdvection;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopSegment;

/**
 * The implicit one-pass advection (LoopAdvection) against an explicit reference that splits each tick into upwind
 * sub-steps sweeping at most a fraction of a segment's volume (the scheme the engine used before, and with small
 * enough sub-steps a close approximation of the exact solution).
 */
public class LoopAdvectionTest {

    private static final double TICK = 0.05;

    /** Explicit upwind advection, sub-stepped so no segment has more than maxCourant of its volume swept per step. */
    static void subStepped(List<LoopSegment> segments, double flowM3s, double dt, double maxCourant) {
        double courant = 0.0;
        for (LoopSegment s : segments) {
            if (s.getVolume() > 0.0) courant = Math.max(courant, flowM3s * s.getFlowFraction() * dt / s.getVolume());
        }
        int steps = Math.max(1, (int) Math.ceil(courant / maxCourant));
        double sub = dt / steps;
        double[] next = new double[segments.size()];
        for (int k = 0; k < steps; k++) {
            for (int i = 0; i < segments.size(); i++) {
                LoopSegment s = segments.get(i);
                double r = Math.min(1.0, flowM3s * s.getFlowFraction() * sub / s.getVolume());
                List<LoopSegment> ups = s.getUpstreamSegments();
                double in;
                if (ups.size() == 1) {
                    in = ups.get(0)
                        .getCurrentTemperatureCelsius();
                } else {
                    double t = 0.0, w = 0.0;
                    for (LoopSegment u : ups) {
                        t += u.getFlowFraction() * u.getCurrentTemperatureCelsius();
                        w += u.getFlowFraction();
                    }
                    in = t / w;
                }
                next[i] = s.getCurrentTemperatureCelsius() * (1.0 - r) + in * r;
            }
            for (int i = 0; i < segments.size(); i++) segments.get(i)
                .setCurrentTemperatureCelsius(next[i]);
        }
    }

    private static double[] temps(CoolantLoopEngine e) {
        List<LoopSegment> segs = e.getSegments();
        double[] t = new double[segs.size()];
        for (int i = 0; i < t.length; i++) t[i] = segs.get(i)
            .getCurrentTemperatureCelsius();
        return t;
    }

    private static double maxDifference(double[] x, double[] y) {
        double d = 0.0;
        for (int i = 0; i < x.length; i++) d = Math.max(d, Math.abs(x[i] - y[i]));
        return d;
    }

    private static double spread(double[] t) {
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (double v : t) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        return max - min;
    }

    @Test
    void testSolvesTheSameEquationsAsTheReference() {
        // With steps far shorter than the time the coolant takes to cross a segment, both schemes approach the exact
        // solution, so they must agree closely: the implicit solve is solving the right equations.
        CoolantLoopEngine implicit = CoolantLoopEnergyConservationTest.loop(38, 8, 3);
        CoolantLoopEngine reference = CoolantLoopEnergyConservationTest.loop(38, 8, 3);
        double flow = 2.834;
        double initialSpread = spread(temps(implicit));
        for (int tick = 0; tick < 20; tick++) {
            for (int k = 0; k < 400; k++) LoopAdvection.step(implicit.getSegments(), flow, TICK / 400);
            subStepped(reference.getSegments(), flow, TICK, 0.01);
        }
        double diff = maxDifference(temps(implicit), temps(reference));
        System.out.printf("fine steps: max difference %.4f C of %.0f C initial spread%n", diff, initialSpread);
        assertTrue(diff < 0.005 * initialSpread, "implicit and reference should agree at fine time steps: " + diff);
    }

    @Test
    void testOneSolvePerTickTracksTheSubSteppedScheme() {
        // At the game's tick, against the previous engine's scheme (sub-steps sweeping at most one segment volume)
        CoolantLoopEngine implicit = CoolantLoopEnergyConservationTest.loop(38, 8, 5);
        CoolantLoopEngine reference = CoolantLoopEnergyConservationTest.loop(38, 8, 5);
        double initialSpread = spread(temps(implicit));
        for (double flow : new double[] { 0.1, 2.834 }) {
            double worst = 0.0;
            for (int tick = 0; tick < 200; tick++) {
                LoopAdvection.step(implicit.getSegments(), flow, TICK);
                subStepped(reference.getSegments(), flow, TICK, 1.0);
                worst = Math.max(worst, maxDifference(temps(implicit), temps(reference)));
            }
            System.out.printf(
                "per tick at %.0f L/s: worst difference %.2f C of %.0f C initial spread%n",
                flow * 1000,
                worst,
                initialSpread);
            assertTrue(worst < 0.25 * initialSpread, flow * 1000 + " L/s: " + worst);
        }
        assertEquals(
            CoolantLoopEnergyConservationTest.heat(reference),
            CoolantLoopEnergyConservationTest.heat(implicit),
            1e-9 * Math.abs(CoolantLoopEnergyConservationTest.heat(reference)));
    }

    @Test
    void testSteadyStateWithHeatInAndOutMatchesTheReference() {
        // Passages heated every tick and PHEs cooling toward 100 C, as in a running loop. The truth for heat that
        // enters and leaves once per tick is advection resolved finely within each tick; one implicit solve per tick
        // must settle close to it.
        CoolantLoopEngine implicit = CoolantLoopEnergyConservationTest.loop(38, 8, 9);
        CoolantLoopEngine subStepped = CoolantLoopEnergyConservationTest.loop(38, 8, 9);
        CoolantLoopEngine fine = CoolantLoopEnergyConservationTest.loop(38, 8, 9);
        double flow = 2.834;
        for (int tick = 0; tick < 6000; tick++) {
            LoopAdvection.step(implicit.getSegments(), flow, TICK);
            subStepped(subStepped.getSegments(), flow, TICK, 1.0);
            subStepped(fine.getSegments(), flow, TICK, 0.02);
            exchange(implicit.getSegments());
            exchange(subStepped.getSegments());
            exchange(fine.getSegments());
        }
        double[] truth = temps(fine);
        double rise = max(truth) - 100.0;
        double implicitError = maxDifference(temps(implicit), truth);
        double subSteppedError = maxDifference(temps(subStepped), truth);
        System.out.printf(
            "steady state: rise %.2f C; implicit off by %.3f C, one-volume sub-steps off by %.3f C%n",
            rise,
            implicitError,
            subSteppedError);
        assertTrue(
            implicitError < 0.06 * rise,
            "implicit steady state too far from the fine solution: " + implicitError);
    }

    private static double max(double[] t) {
        double m = -Double.MAX_VALUE;
        for (double v : t) m = Math.max(m, v);
        return m;
    }

    /** 1 MW into each passage per tick's worth of heat, and each PHE taking 20% of its excess over 100 C. */
    private static void exchange(List<LoopSegment> segments) {
        double rhoCp = 1000.0 * 4184.0;
        for (LoopSegment s : segments) {
            if (s.getId()
                .startsWith("passage_")) {
                s.setCurrentTemperatureCelsius(
                    s.getCurrentTemperatureCelsius() + 1.0e6 * TICK / (s.getVolume() * rhoCp));
            } else if (s.getId()
                .startsWith("phe_") && !s.isManifold()) {
                    s.setCurrentTemperatureCelsius(
                        s.getCurrentTemperatureCelsius() - 0.2 * (s.getCurrentTemperatureCelsius() - 100.0));
                }
        }
    }

    @Test
    void testNoTemperatureLeavesTheRangeAlreadyPresent() {
        // Mixing can only produce temperatures between those the loop already holds, at any flow
        for (double flow : new double[] { 0.01, 0.5, 2.834, 50.0, 1000.0 }) {
            CoolantLoopEngine e = CoolantLoopEnergyConservationTest.loop(38, 8, 17);
            double[] t0 = temps(e);
            double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
            for (double v : t0) {
                lo = Math.min(lo, v);
                hi = Math.max(hi, v);
            }
            for (int tick = 0; tick < 50; tick++) {
                LoopAdvection.step(e.getSegments(), flow, TICK);
                for (double v : temps(e)) {
                    assertTrue(
                        v >= lo - 1e-9 && v <= hi + 1e-9,
                        flow * 1000 + " L/s: " + v + " outside " + lo + ".." + hi);
                }
            }
        }
    }

    @Test
    void testExtremeFlowConservesHeatInOneSolve() {
        CoolantLoopEngine e = CoolantLoopEnergyConservationTest.loop(38, 8, 11);
        double before = CoolantLoopEnergyConservationTest.heat(e);
        for (int tick = 0; tick < 100; tick++) LoopAdvection.step(e.getSegments(), 1000.0, TICK); // 1,000,000 L/s
        assertEquals(before, CoolantLoopEnergyConservationTest.heat(e), 1e-9 * Math.abs(before));
        assertTrue(spread(temps(e)) < 1e-3, "at a million L/s the loop should be all but uniform");
    }

    @Test
    void testBypassCycleUsesTheDirectSolveAndConservesHeat() {
        // A ring a -> b -> c -> a whose segment b also feeds a bypass d back into itself (b -> d -> b): the cycle
        // b-d avoids the first segment, so the cut solve cannot order it.
        List<LoopSegment> segs = new ArrayList<>();
        LoopSegment a = seg("a", 300), bb = seg("b", 20), c = seg("c", 600), d = seg("d", 900);
        bb.addUpstreamSegment(a);
        bb.addUpstreamSegment(d);
        d.setFlowFraction(0.5);
        c.setFlowFraction(1.0);
        d.addUpstreamSegment(bb);
        c.addUpstreamSegment(bb);
        a.addUpstreamSegment(c);
        bb.setFlowFraction(1.5);
        segs.add(a);
        segs.add(bb);
        segs.add(c);
        segs.add(d);
        double before = 0.0;
        for (LoopSegment s : segs) before += s.getVolume() * s.getCurrentTemperatureCelsius();
        for (int tick = 0; tick < 50; tick++) LoopAdvection.step(segs, 5.0, TICK);
        double after = 0.0;
        for (LoopSegment s : segs) after += s.getVolume() * s.getCurrentTemperatureCelsius();
        assertEquals(before, after, 1e-9 * before);
    }

    private static LoopSegment seg(String id, double temp) {
        return new LoopSegment(id, 1.0, 0.2, 0.00005, 0.5, 3000.0, 10000.0, temp);
    }

    @Test
    void testEngineUsesTheImplicitSolve() {
        CoolantLoopEngine e = new CoolantLoopEngine(CoolantFluidProperty.WATER);
        CoolantLoopEngine copy = CoolantLoopEnergyConservationTest.loop(4, 1, 13);
        for (LoopSegment s : copy.getSegments()) e.addSegment(s);
        double before = CoolantLoopEnergyConservationTest.heat(e);
        e.setMaxVolumetricFlowRate(100.0);
        e.setVolumetricFlowRate(50.0);
        e.step(TICK);
        assertEquals(before, CoolantLoopEnergyConservationTest.heat(e), 1e-9 * Math.abs(before));
    }
}
