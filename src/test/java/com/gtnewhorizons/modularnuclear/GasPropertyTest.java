package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasProperty.Phase;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasProperty.State;

/** The gas model (Note [The gas model]) against reference data and its own invariants. */
public class GasPropertyTest {

    private static final double C = 273.15;

    private static void assertRel(double expected, double actual, double rel, String what) {
        assertEquals(expected, actual, Math.abs(expected) * rel, what);
    }

    @Test
    public void saturationCurvesMatchReferenceData() {
        GasProperty r = GasProperty.R134A;
        assertRel(10.17e5, r.saturationPressure(40 + C), 0.03, "R-134a at 40 °C");
        assertRel(2.006e5, r.saturationPressure(-10 + C), 0.03, "R-134a at -10 °C");
        assertRel(1.01325e5, r.saturationPressure(247.08), 1e-9, "R-134a boils at 1 atm");
        assertRel(13.5e5, GasProperty.AMMONIA.saturationPressure(35 + C), 0.04, "ammonia at 35 °C");
        assertRel(13.7e5, GasProperty.PROPANE.saturationPressure(40 + C), 0.03, "propane at 40 °C");
        assertRel(64.3e5, GasProperty.CARBON_DIOXIDE.saturationPressure(25 + C), 0.02, "CO2 at 25 °C");
        assertRel(40.59e5, r.saturationPressure(r.getCriticalTemperatureK() - 1e-9), 1e-6, "critical point");
        assertEquals(40 + C, r.saturationTemperature(r.saturationPressure(40 + C)), 1e-9);
    }

    @Test
    public void latentHeatFallsWithTemperature() {
        GasProperty r = GasProperty.R134A;
        assertEquals(217e3, r.latentHeat(247.08), 1e-6);
        // Real R-134a: 206 kJ/kg at -10 °C, 163 at 40 °C
        assertRel(206e3, r.latentHeat(-10 + C), 0.05, "R-134a at -10 °C");
        assertRel(163e3, r.latentHeat(40 + C), 0.12, "R-134a at 40 °C");
        assertTrue(r.latentHeat(-20 + C) > r.latentHeat(20 + C));
    }

    @Test
    public void phasesAndRoundTrips() {
        GasProperty r = GasProperty.R134A;
        State st = new State();
        double p = 5e5;
        double ts = r.saturationTemperature(p);
        // Subcooled liquid, superheated vapour, and a two-phase mix
        r.state(p, r.enthalpy(p, ts - 20), st);
        assertEquals(Phase.LIQUID, st.phase);
        assertEquals(ts - 20, st.temperatureK, 1e-9);
        assertRel(1206.0, st.density, 0.01, "liquid density");
        r.state(p, r.enthalpy(p, ts + 30), st);
        assertEquals(Phase.VAPOR, st.phase);
        assertEquals(ts + 30, st.temperatureK, 1e-9);
        assertEquals(p / (r.getGasConstant() * (ts + 30)), st.density, 1e-9);
        double hl = r.saturatedVaporEnthalpy(ts) - r.latentHeat(ts);
        r.state(p, hl + 0.25 * r.latentHeat(ts), st);
        assertEquals(Phase.TWO_PHASE, st.phase);
        assertEquals(0.25, st.quality, 1e-12);
        assertEquals(ts, st.temperatureK, 0.0);
    }

    @Test
    public void densityRisesWithPressureAtFixedEnthalpy() {
        // GasLoopEngine's pressure solve relies on it, in every phase and across the critical pressure
        for (GasProperty g : GasProperty.defaults()) {
            State st = new State();
            for (double h : new double[] { -50e3, 0.0, 100e3, 250e3, 400e3, 800e3 }) {
                double last = 0.0;
                for (double p = 0.2e5; p < 200e5; p *= 1.07) {
                    double rho = g.state(p, h, st).density;
                    assertTrue(rho >= last * (1 - 1e-12), g + " at h=" + h + ", p=" + p);
                    last = rho;
                }
            }
        }
    }

    @Test
    public void entropyRisesWithEnthalpy() {
        for (GasProperty g : GasProperty.defaults()) {
            double frozen = Double.isNaN(g.getFreezingPointK()) ? 2.0 : g.getFreezingPointK();
            for (double p : new double[] { 0.5e5, 3e5, 20e5, 150e5 }) {
                double last = Double.NEGATIVE_INFINITY;
                for (double h = g.liquidEnthalpy(frozen); h < 2000e3; h += 5e3) {
                    double s = g.entropy(p, h);
                    assertTrue(s > last, g + " at p=" + p + ", h=" + h);
                    last = s;
                }
            }
        }
    }

    @Test
    public void vaporCompressesAsAnIdealGas() {
        GasProperty he = GasProperty.HELIUM;
        double p1 = 5e5, t1 = 300, p2 = 15e5;
        double h2 = he.isentropicEnthalpy(p1, he.vaporEnthalpy(t1), p2);
        double t2 = t1 * Math.pow(p2 / p1, (he.getGamma() - 1) / he.getGamma());
        assertEquals(he.vaporEnthalpy(t2), h2, 1e-6);
        assertEquals(5.0 / 3.0, he.getGamma(), 1e-3);
        // Superheated R-134a from 2 bar, 0 °C to 10 bar: about 50 °C in a real ideal compressor
        GasProperty r = GasProperty.R134A;
        double h = r.isentropicEnthalpy(2e5, r.enthalpy(2e5, C), 10e5);
        assertEquals(50 + C, r.state(10e5, h, new State()).temperatureK, 8.0);
    }

    @Test
    public void throttledLiquidFlashes() {
        // R-134a liquid at 10 bar, 35 °C through a valve to 2 bar: about 30% vapour at -10 °C (real 0.28)
        GasProperty r = GasProperty.R134A;
        double h = r.enthalpy(10e5, 35 + C);
        State st = r.state(2e5, h, new State());
        assertEquals(Phase.TWO_PHASE, st.phase);
        assertEquals(r.saturationTemperature(2e5), st.temperatureK, 0.0);
        assertEquals(0.29, st.quality, 0.03);
        // Throttled vapour keeps its temperature
        double hv = r.vaporEnthalpy(60 + C);
        assertEquals(60 + C, r.state(2e5, hv, new State()).temperatureK, 1e-9);
    }

    @Test
    public void isentropicEnthalpyIntoTheDome() {
        // Wet expansion: saturated vapour expanded hard ends two-phase, with the entropy it started with
        GasProperty n = GasProperty.NITROGEN;
        double p1 = 30e5, p2 = 1.2e5;
        double h1 = n.saturatedVaporEnthalpy(n.saturationTemperature(p1)) + 2000.0;
        double h2 = n.isentropicEnthalpy(p1, h1, p2);
        assertEquals(n.entropy(p1, h1), n.entropy(p2, h2), 1e-6);
        assertEquals(Phase.TWO_PHASE, n.state(p2, h2, new State()).phase);
    }

    @Test
    public void standardLiters() {
        GasProperty r = GasProperty.R134A;
        assertRel(4.55, r.getStandardDensity(), 0.01, "R-134a at STP");
        assertEquals(1000.0, r.litersOfMass(r.massOfLiters(1000.0)), 1e-9);
        assertSame(r, GasProperty.get("r134a"));
        assertNull(GasProperty.get("water"));
    }
}
