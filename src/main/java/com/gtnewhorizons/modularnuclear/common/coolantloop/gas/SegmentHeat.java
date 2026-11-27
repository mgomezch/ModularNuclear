package com.gtnewhorizons.modularnuclear.common.coolantloop.gas;

import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopSegment;

/**
 * Heat exchange between a device and the gas in its loop segment. The heat goes into the gas flowing through, applied
 * by the loop's next step (wrinkle (W1) of Note [Gas loop pressure zones]); these helpers cap it so the gas doesn't
 * cross the temperature it exchanges with.
 */
public final class SegmentHeat {

    private SegmentHeat() {}

    /**
     * How a gas's phase scales a convection coefficient taken from a liquid correlation: vapour conducts heat poorly,
     * and condensing or boiling gas far better than either phase alone.
     */
    public static final double VAPOR_FACTOR = 0.3;
    public static final double TWO_PHASE_FACTOR = 2.0;

    /** The phase factor of the segment's gas, or 1 for liquid coolant. */
    public static double phaseFactor(LoopSegment s) {
        if (s == null || !s.holdsGas()) return 1.0;
        double x = s.getGasQuality();
        if (x >= 1.0) return VAPOR_FACTOR;
        if (x <= 0.0) return 1.0;
        return TWO_PHASE_FACTOR;
    }

    /** How far past its saturation (or inlet) temperature a coil heats the gas flowing through it, K. */
    public static final double COIL_SUPERHEAT_K = 50.0;

    /**
     * The hottest a coil of gas in a hot body (a coolant bath's items) takes the gas: COIL_SUPERHEAT_K past its
     * saturation temperature or its inlet temperature, whichever is higher. Gas flowing through a coil in a body at
     * 1500 °C leaves far below it, and a starved coil that let its vapour reach the body's temperature would melt
     * its loop within a second; past the bound the body cools only as fast as the flow takes its heat.
     */
    public static double coilBoundK(GasProperty gas, LoopSegment s) {
        double ts = gas.saturationTemperature(pressurePa(s));
        double in = s.getIncomingTemperatureCelsius() + 273.15;
        return Math.max(ts, in) + COIL_SUPERHEAT_K;
    }

    /** Pressure (Pa) of the segment's gas. */
    public static double pressurePa(LoopSegment s) {
        return s.getInletPressureBar() * 1e5;
    }

    /**
     * Most heat (J) the gas in and through the segment can take in this step before it reaches otherK, or give up
     * (for otherK below it) as a negative number: what is in the segment plus what flows through it.
     */
    public static double limit(GasProperty gas, LoopSegment s, double otherK, double dt) {
        double target = gas.enthalpy(pressurePa(s), otherK);
        double m = s.getGasMassKg();
        double through = Math.max(0.0, s.getGasMassFlow()) * dt;
        double h = s.getGasEnthalpy();
        double hIn = s.getGasIncomingEnthalpy();
        if (target >= h) return m * Math.max(0.0, target - h) + through * Math.max(0.0, target - hIn);
        return -(m * Math.max(0.0, h - target) + through * Math.max(0.0, hIn - target));
    }

    /**
     * Moves heat into the segment's gas (negative to take it out), as much of proposedJoules as the gas can take
     * before reaching otherK. Returns the heat moved.
     */
    public static double exchange(GasProperty gas, LoopSegment s, double otherK, double proposedJoules, double dt) {
        if (gas == null || s == null || !s.holdsGas() || proposedJoules == 0.0) return 0.0;
        double cap = limit(gas, s, otherK, dt);
        double j;
        if (proposedJoules > 0.0) j = Math.max(0.0, Math.min(proposedJoules, cap));
        else j = Math.min(0.0, Math.max(proposedJoules, cap));
        if (j != 0.0) {
            s.addPendingGasHeatJoules(j);
            s.boundGasHeat(j, gas.enthalpy(pressurePa(s), otherK));
        }
        return j;
    }

    /**
     * Exchange with a body at otherK (one that doesn't change temperature over the step: air, a heater's casing, an
     * item) through a conductance UA (W/K) for dt seconds. The gas flowing through sees the body along its whole
     * path, so the heat is the effectiveness-NTU heat from the gas's inlet state, eps = 1 - exp(-UA dt / C), not
     * UA times the segment's own temperature difference, which at a high conductance would swing from tick to tick.
     * Returns the heat moved into the gas (J).
     */
    public static double exchangeThrough(GasProperty gas, LoopSegment s, double otherK, double conductanceWPerK,
        double dt) {
        if (s == null || !s.holdsGas()) return 0.0;
        double through = Math.max(0.0, s.getGasMassFlow()) * dt;
        if (through <= 0.0 || conductanceWPerK <= 0.0) return 0.0;
        double tIn = s.getIncomingTemperatureCelsius() + 273.15;
        double dT = otherK - tIn;
        if (Math.abs(dT) < 1e-9) return 0.0;
        double qMax = through * (gas.enthalpy(pressurePa(s), otherK) - s.getGasIncomingEnthalpy());
        if (qMax * dT <= 0.0) return 0.0;
        double capacity = qMax / dT;
        double q = (1.0 - Math.exp(-conductanceWPerK * dt / capacity)) * qMax;
        return exchange(gas, s, otherK, q, dt);
    }

    /** The gas flowing through a segment, as a counterflow exchanger's stream. */
    public static Counterflow.Stream stream(GasProperty gas, LoopSegment s, double dt) {
        return new Counterflow.Stream() {

            @Override
            public double inletK() {
                return s.getIncomingTemperatureCelsius() + 273.15;
            }

            @Override
            public double massPerStep() {
                return Math.max(0.0, s.getGasMassFlow()) * dt;
            }

            @Override
            public double inletEnthalpy() {
                return s.getGasIncomingEnthalpy();
            }

            @Override
            public double enthalpyAt(double t) {
                return gas.enthalpy(pressurePa(s), t);
            }
        };
    }

    /**
     * Moves heat between two gas segments as a counterflow exchanger of conductance UA (W/K) (Note [Counterflow
     * exchange between two streams]). Returns the heat moved from a into b, J.
     */
    public static double counterflow(GasProperty gasA, LoopSegment a, GasProperty gasB, LoopSegment b,
        double conductanceWPerK, double dt) {
        double q = Counterflow.exchange(stream(gasA, a, dt), stream(gasB, b, dt), conductanceWPerK, dt);
        if (q == 0.0) return 0.0;
        double ta = a.getIncomingTemperatureCelsius() + 273.15, tb = b.getIncomingTemperatureCelsius() + 273.15;
        // Both sides take the same heat: the smaller of what each can
        double fromA = q > 0 ? Math.min(q, -limit(gasA, a, tb, dt)) : Math.max(q, -limit(gasA, a, tb, dt));
        double intoB = q > 0 ? Math.min(fromA, limit(gasB, b, ta, dt)) : Math.max(fromA, limit(gasB, b, ta, dt));
        exchange(gasA, a, tb, -intoB, dt);
        exchange(gasB, b, ta, intoB, dt);
        return intoB;
    }
}
