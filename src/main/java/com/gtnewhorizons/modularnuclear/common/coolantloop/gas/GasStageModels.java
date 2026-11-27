package com.gtnewhorizons.modularnuclear.common.coolantloop.gas;

/**
 * The physics of the three pressure stages, apart from their machines: the game's multiblocks and the tests share
 * them. Each fills a GasPressureStage.Context's answers.
 */
public final class GasStageModels {

    private GasStageModels() {}

    /** Below this pressure difference an orifice's flow is taken as linear in it, so equalization settles. */
    static final double LINEAR_BELOW_PA = 5_000.0;

    /**
     * A compressor: a displacement (m^3/s of suction gas at full speed), a clearance that lowers its volumetric
     * efficiency as the pressure ratio rises, and an isentropic efficiency.
     */
    public static final class Compressor {

        public final double displacement;
        public final double isentropicEfficiency;
        public final double clearance;

        public Compressor(double displacementM3s, double isentropicEfficiency, double clearance) {
            this.displacement = displacementM3s;
            this.isentropicEfficiency = isentropicEfficiency;
            this.clearance = clearance;
        }

        /** Volumetric efficiency at a pressure ratio: 1 - c (r^(1/gamma) - 1), never below zero. */
        public double volumetricEfficiency(GasProperty gas, double ratio) {
            if (ratio <= 1.0) return 1.0;
            return Math.max(0.0, 1.0 - clearance * (Math.pow(ratio, 1.0 / gas.getGamma()) - 1.0));
        }

        /** Specific shaft work (J/kg) from the inlet to the outlet pressure. */
        public double specificWork(GasPressureStage.Context c) {
            if (c.pressureOut <= c.pressureIn) return 0.0;
            double hs = c.gas.isentropicEnthalpy(c.pressureIn, c.enthalpyIn, c.pressureOut);
            return Math.max(0.0, hs - c.enthalpyIn) / isentropicEfficiency;
        }

        /** Runs at a speed between 0 and 1. */
        public void compute(GasPressureStage.Context c, double speed) {
            c.clearAnswers();
            if (!c.loopRunning || speed <= 0.0) return;
            double ratio = c.pressureOut / c.pressureIn;
            double volume = Math.min(displacement * speed * volumetricEfficiency(c.gas, ratio), c.maxVolumetricFlow);
            double flow = Math.min(volume * c.in.density, c.maxMassFlow);
            double w = specificWork(c);
            c.massFlow = flow;
            c.enthalpyOut = c.enthalpyIn + w;
            c.shaftPowerWatts = flow * w;
        }

        /** Shaft power (W) at full speed in this context. */
        public double fullSpeedPower(GasPressureStage.Context c) {
            if (!c.loopRunning) return 0.0;
            double ratio = c.pressureOut / c.pressureIn;
            double volume = Math.min(displacement * volumetricEfficiency(c.gas, ratio), c.maxVolumetricFlow);
            return volume * c.in.density * specificWork(c);
        }
    }

    /** Mass flow (kg/s) through an orifice of effective area (m^2) from the inlet state to the outlet pressure. */
    public static double orificeFlow(GasPressureStage.Context c, double area) {
        double dp = c.pressureIn - c.pressureOut;
        if (dp <= 0.0 || area <= 0.0) return 0.0;
        GasProperty.State in = c.in;
        double flow;
        if (in.phase == GasProperty.Phase.VAPOR) {
            // Compressible flow through a nozzle, choked below the critical pressure ratio
            double g = c.gas.getGamma();
            double r = Math.max(c.pressureOut / c.pressureIn, 0.0);
            double rc = Math.pow(2.0 / (g + 1.0), g / (g - 1.0));
            double rr = Math.max(r, rc);
            double psi = Math.sqrt(2.0 * g / (g - 1.0) * (Math.pow(rr, 2.0 / g) - Math.pow(rr, (g + 1.0) / g)));
            flow = area * psi * Math.sqrt(c.pressureIn * in.density);
        } else {
            // Liquid or a two-phase mix, as a homogeneous fluid
            flow = area * Math.sqrt(2.0 * in.density * Math.max(dp, LINEAR_BELOW_PA));
        }
        if (dp < LINEAR_BELOW_PA) flow *= dp / LINEAR_BELOW_PA;
        return Math.min(Math.min(flow, c.maxVolumetricFlow * in.density), c.maxMassFlow);
    }

    /** An expansion valve: isenthalpic, through an orifice of up to maxArea (m^2) times its opening. */
    public static final class Valve {

        public final double maxArea;

        public Valve(double maxAreaM2) {
            this.maxArea = maxAreaM2;
        }

        public void compute(GasPressureStage.Context c, double opening) {
            c.clearAnswers();
            c.massFlow = orificeFlow(c, maxArea * Math.max(0.0, Math.min(1.0, opening)));
            c.enthalpyOut = c.enthalpyIn;
        }

    }

    /**
     * A thermostatic expansion valve's control: it opens as the gas leaving its evaporator gets more superheated than
     * the set point and closes as it gets less (or wet). Proportional with a slow integral, as a real TXV's spring
     * and bulb act on the error at once and its bias drifts; a purely integral valve hunts, flooding and starving
     * the evaporator in turn.
     */
    public static final class Thermostat {

        public double setPointK = 5.0;
        public double proportionalPerK = 0.01;
        public double integralPerKSecond = 0.002;
        public double minOpening = 0.005;
        /** The integral part: the opening at zero error. */
        public double bias = 0.2;

        /** The opening for this step, given the superheat the valve's evaporator leaves its gas at. */
        public double opening(double superheatK, double dt) {
            double error = superheatK - setPointK;
            double p = proportionalPerK * Math.max(-20.0, Math.min(20.0, error));
            bias = Math.max(minOpening, Math.min(1.0, bias + integralPerKSecond * error * dt));
            return Math.max(minOpening, Math.min(1.0, bias + p));
        }
    }

    /** A turbo-expander: a nozzle of area (m^2) and an isentropic efficiency. */
    public static final class Expander {

        public final double nozzleArea;
        public final double isentropicEfficiency;

        public Expander(double nozzleAreaM2, double isentropicEfficiency) {
            this.nozzleArea = nozzleAreaM2;
            this.isentropicEfficiency = isentropicEfficiency;
        }

        /**
         * Passes the nozzle's flow. Loaded (something takes its work), it expands the gas towards the isentropic
         * outlet and gives out the work as negative shaft power; unloaded, it free-wheels and the gas passes as
         * through a valve.
         */
        public void compute(GasPressureStage.Context c, boolean loaded) {
            c.clearAnswers();
            c.massFlow = orificeFlow(c, nozzleArea);
            if (!loaded || c.massFlow <= 0.0 || c.pressureOut >= c.pressureIn) {
                c.enthalpyOut = c.enthalpyIn;
                return;
            }
            double hs = c.gas.isentropicEnthalpy(c.pressureIn, c.enthalpyIn, c.pressureOut);
            double w = Math.max(0.0, c.enthalpyIn - hs) * isentropicEfficiency;
            c.enthalpyOut = c.enthalpyIn - w;
            c.shaftPowerWatts = -c.massFlow * w;
        }
    }
}
