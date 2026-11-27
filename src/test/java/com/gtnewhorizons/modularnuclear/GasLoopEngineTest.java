package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopSegment;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasLoopEngine;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasPressureStage;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasStageModels;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.SegmentHeat;

/** Whole gas loops on GasLoopEngine (Note [Gas loop pressure zones]), run to steady state. */
public class GasLoopEngineTest {

    static final double C = 273.15;
    static final double DT = 0.05;

    /** A compressor at full speed, counting its work. */
    static final class TestCompressor implements GasPressureStage {

        final GasStageModels.Compressor model;
        double speed = 1.0;
        double workJ = 0.0;

        TestCompressor(double displacement) {
            model = new GasStageModels.Compressor(displacement, 0.75, 0.04);
        }

        @Override
        public void computeStage(Context c) {
            model.compute(c, speed);
            workJ += c.shaftPowerWatts * c.dt;
        }

        @Override
        public boolean isPassive() {
            return false;
        }
    }

    static final class TestValve implements GasPressureStage {

        final GasStageModels.Valve model;
        double opening = 0.3;
        boolean thermostatic = true;
        final GasStageModels.Thermostat control = new GasStageModels.Thermostat();

        TestValve(double area) {
            model = new GasStageModels.Valve(area);
        }

        @Override
        public void computeStage(Context c) {
            if (thermostatic && c.loopRunning) opening = control.opening(c.downstreamExitSuperheatK, c.dt);
            model.compute(c, opening);
        }
    }

    static final class TestExpander implements GasPressureStage {

        final GasStageModels.Expander model;
        double workJ = 0.0;

        TestExpander(double area) {
            model = new GasStageModels.Expander(area, 0.85);
        }

        @Override
        public void computeStage(Context c) {
            model.compute(c, true);
            workJ -= c.shaftPowerWatts * c.dt;
        }
    }

    static LoopSegment pipe(String id) {
        return new LoopSegment(id, 1.0, 0.2, 0.000045, 0.05, 100.0, 1000.0, 20.0);
    }

    /** Segments in a ring, each fed by the one before. */
    static List<LoopSegment> ring(String... ids) {
        List<LoopSegment> l = new ArrayList<>();
        for (String id : ids) l.add(pipe(id));
        for (int i = 0; i < l.size(); i++) l.get(i)
            .addUpstreamSegment(l.get((i + l.size() - 1) % l.size()));
        return l;
    }

    static double totalEnthalpy(GasLoopEngine e) {
        double h = 0;
        for (LoopSegment s : e.getSegments()) h += s.getGasMassKg() * s.getGasEnthalpy() + s.getPendingGasHeatJoules();
        return h;
    }

    static LoopSegment find(GasLoopEngine e, String id) {
        for (LoopSegment s : e.getSegments()) if (s.getId()
            .equals(id)) return s;
        throw new AssertionError(id);
    }

    /** Totals of a vapour-compression loop's heat over the run's last stretch. */
    static final class Run {

        double evaporatorJ, condenserJ, workJ, startEnthalpyJ, endEnthalpyJ;
    }

    /**
     * An R-134a loop: compressor, four condenser segments to air at 30 °C, a thermostatic valve, four evaporator
     * segments taking heat from a load at 15 °C. Returns the engine after the run, and fills the totals of its last
     * 20 s.
     */
    static GasLoopEngine vapourCompression(Run run, TestCompressor comp, TestValve valve, double seconds) {
        GasProperty gas = GasProperty.R134A;
        GasLoopEngine e = new GasLoopEngine(gas);
        List<LoopSegment> l = ring("comp", "c1", "c2", "c3", "c4", "valve", "e1", "e2", "e3", "e4");
        for (LoopSegment s : l) e.addSegment(s);
        e.bindStage(find(e, "comp").getKey(), comp);
        e.bindStage(find(e, "valve").getKey(), valve);
        double ambient = 30 + C;
        double charge = GasLoopEngine.requiredChargeKg(gas, e.getTotalVolumeM3(), ambient, 0.3, 10e5);
        e.equilibrate(charge, ambient);
        e.setPumpPowered(true);
        double condUA = 40_000.0, evapUA = 40_000.0;
        int steps = (int) (seconds / DT);
        int tally = steps - (int) (20.0 / DT);
        for (int t = 0; t < steps; t++) {
            double w0 = comp.workJ;
            e.step(DT);
            assertFalse(e.isRuptured(), () -> e.getFailureReason());
            double q = 0, qc = 0;
            for (String id : new String[] { "c1", "c2", "c3", "c4" }) {
                qc += SegmentHeat.exchangeThrough(gas, find(e, id), ambient, condUA, DT);
            }
            for (String id : new String[] { "e1", "e2", "e3", "e4" }) {
                q += SegmentHeat.exchangeThrough(gas, find(e, id), 15 + C, evapUA, DT);
            }
            if (t == tally) run.startEnthalpyJ = totalEnthalpy(e);
            if (t >= tally) {
                run.evaporatorJ += q;
                run.condenserJ -= qc;
                run.workJ += comp.workJ - w0;
            }
            assertEquals(charge, e.getTotalGasMassKg(), charge * 1e-9, "mass at step " + t);
        }
        run.endEnthalpyJ = totalEnthalpy(e);
        return e;
    }

    @Test
    public void vapourCompressionLoopReachesARealisticSteadyState() {
        Run run = new Run();
        TestCompressor comp = new TestCompressor(0.05);
        TestValve valve = new TestValve(2e-4);
        GasLoopEngine e = vapourCompression(run, comp, valve, 120.0);
        double[] zones = e.getZonePressures();
        assertEquals(2, zones.length);
        double low = Math.min(zones[0], zones[1]), high = Math.max(zones[0], zones[1]);
        GasProperty gas = GasProperty.R134A;
        double tEvap = gas.saturationTemperature(low) - C, tCond = gas.saturationTemperature(high) - C;
        String report = String.format(
            "low %.2f bar (%.1f °C), high %.2f bar (%.1f °C), Qe %.1f kW, Qc %.1f kW, W %.1f kW, valve %.3f",
            low / 1e5,
            tEvap,
            high / 1e5,
            tCond,
            run.evaporatorJ / 20e3,
            run.condenserJ / 20e3,
            run.workJ / 20e3,
            valve.opening);
        System.out.println(report);
        assertTrue(tEvap < 15.0 && tEvap > -30.0, report);
        assertTrue(tCond > 30.0 && tCond < 70.0, report);
        double cop = run.evaporatorJ / run.workJ;
        double carnot = (tEvap + C) / (tCond - tEvap);
        assertTrue(cop > 1.5 && cop < carnot, report + ", COP " + cop + ", Carnot " + carnot);
        // Energy: what the evaporator took plus the work is what the condenser rejected plus what the loop stored
        double stored = run.endEnthalpyJ - run.startEnthalpyJ;
        assertEquals(run.evaporatorJ + run.workJ, run.condenserJ + stored, 0.005 * run.condenserJ, report);
        // The evaporator's outlet is superheated near the valve's set point
        assertEquals(1.0, find(e, "e4").getGasQuality(), 1e-12, report);
    }

    @Test
    public void stoppedLoopEqualizesAndComesToRest() {
        Run run = new Run();
        TestCompressor comp = new TestCompressor(0.05);
        TestValve valve = new TestValve(2e-4);
        GasLoopEngine e = vapourCompression(run, comp, valve, 30.0);
        e.setPumpPowered(false);
        for (int t = 0; t < 6000; t++) e.step(DT);
        double[] zones = e.getZonePressures();
        assertEquals(zones[0], zones[1], 0.05e5);
        assertEquals(0.0, e.getVolumetricFlowRate(), 1e-6);
    }

    @Test
    public void heliumBraytonLoopWithRecuperatorGoesCryogenic() {
        // compressor -> aftercooler (to 30 °C) -> recuperator hot side -> expander -> cold load -> recuperator cold
        // side -> back. The recuperator is two segments exchanging with each other.
        GasProperty gas = GasProperty.HELIUM;
        GasLoopEngine e = new GasLoopEngine(gas);
        List<LoopSegment> l = ring("comp", "a1", "a2", "rh", "exp", "load", "rc", "r2");
        for (LoopSegment s : l) e.addSegment(s);
        TestCompressor comp = new TestCompressor(0.4);
        TestExpander exp = new TestExpander(1.5e-4);
        e.bindStage(find(e, "comp").getKey(), comp);
        e.bindStage(find(e, "exp").getKey(), exp);
        double ambient = 30 + C;
        e.equilibrate(GasLoopEngine.requiredChargeKg(gas, e.getTotalVolumeM3(), ambient, 0.3, 15e5), ambient);
        e.setPumpPowered(true);
        double loadK = 70.0;
        double loadJ = 0.0;
        for (int t = 0; t < 6000; t++) {
            e.step(DT);
            assertFalse(e.isRuptured(), () -> e.getFailureReason());
            SegmentHeat.exchangeThrough(gas, find(e, "a1"), ambient, 50_000, DT);
            SegmentHeat.exchangeThrough(gas, find(e, "a2"), ambient, 50_000, DT);
            LoopSegment hot = find(e, "rh"), cold = find(e, "rc");
            SegmentHeat.counterflow(gas, hot, gas, cold, 30_000, DT);
            double ql = SegmentHeat.exchangeThrough(gas, find(e, "load"), loadK, 2_000, DT);
            if (t >= 6000 - 400) loadJ += ql;
        }
        LoopSegment expOut = find(e, "exp");
        String report = String.format(
            "zones %.1f / %.1f bar, expander out %.1f K, load seg %.1f K, load %.2f kW",
            e.getZonePressures()[0] / 1e5,
            e.getZonePressures()[1] / 1e5,
            expOut.getCurrentTemperatureCelsius() + C,
            find(e, "load").getCurrentTemperatureCelsius() + C,
            loadJ / 20e3);
        System.out.println(report);
        assertTrue(expOut.getCurrentTemperatureCelsius() + C < 100.0, report);
        assertTrue(loadJ > 0.0, report);
        assertTrue(exp.workJ > 0.0, report);
    }

    @Test
    public void equilibriumChargeSitsOnTheSaturationCurve() {
        GasProperty gas = GasProperty.R134A;
        GasLoopEngine e = new GasLoopEngine(gas);
        for (LoopSegment s : ring("a", "b", "c")) e.addSegment(s);
        double t = 25 + C;
        e.equilibrate(GasLoopEngine.requiredChargeKg(gas, e.getTotalVolumeM3(), t, 0.3, 0), t);
        e.step(DT);
        assertEquals(gas.saturationPressure(t), e.getZonePressures()[0], 1.0);
        assertEquals(25.0, find(e, "b").getCurrentTemperatureCelsius(), 1e-6);
    }
}
