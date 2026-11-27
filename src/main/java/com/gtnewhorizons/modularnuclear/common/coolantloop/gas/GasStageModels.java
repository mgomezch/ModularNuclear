package com.gtnewhorizons.modularnuclear.common.coolantloop.gas;

/**
 * The physics of the three pressure stages, apart from their machines: the game's multiblocks and the tests share
 * them. Each fills a GasPressureStage.Context's answers.
 */
public final class GasStageModels {

    private GasStageModels() {}

    /** Below this pressure difference an orifice's flow is taken as linear in it, so equalization settles. */
    static final double LINEAR_BELOW_PA = 5_000.0;
    static final double EQUAL_BELOW_PA = 100.0;

    /**
     * A compressor: a displacement (m^3/s of suction gas at full speed), a clearance that lowers its volumetric
     * efficiency as the pressure ratio rises, and an isentropic efficiency.
     * <p>
     * A compressor with a built-in pressure ratio above 1 (the compact ones: Note [Compact loop machines]) compresses
     * its gas at least that far before letting it out, as a screw compressor with a fixed volume ratio does: on a
     * loop whose own ratio is lower, the excess work goes into the gas as heat.
     */
    public static final class Compressor {

        public final double displacement;
        public final double isentropicEfficiency;
        public final double clearance;
        public final double builtInRatio;

        public Compressor(double displacementM3s, double isentropicEfficiency, double clearance) {
            this(displacementM3s, isentropicEfficiency, clearance, 1.0);
        }

        public Compressor(double displacementM3s, double isentropicEfficiency, double clearance, double builtInRatio) {
            this.displacement = displacementM3s;
            this.isentropicEfficiency = isentropicEfficiency;
            this.clearance = clearance;
            this.builtInRatio = Math.max(1.0, builtInRatio);
        }

        /** Volumetric efficiency at a pressure ratio: 1 - c (r^(1/gamma) - 1), never below zero. */
        public double volumetricEfficiency(GasProperty gas, double ratio) {
            if (ratio <= 1.0) return 1.0;
            return Math.max(0.0, 1.0 - clearance * (Math.pow(ratio, 1.0 / gas.getGamma()) - 1.0));
        }

        /**
         * Density of what the compressor draws: the vapour's. A compressor's cylinders fill with vapour, and at a wet
         * suction it draws the vapour's density's worth of the mix (and wears: slugging). Drawing the mix at its own
         * density would pump a flooded evaporator at nearly the liquid's density, thousands of kilograms a second.
         */
        public static double suctionDensity(GasPressureStage.Context c) {
            if (c.in.phase != GasProperty.Phase.TWO_PHASE && c.in.phase != GasProperty.Phase.LIQUID)
                return c.in.density;
            double rhoG = c.pressureIn / (c.gas.getGasConstant() * c.in.saturationTemperatureK);
            return Math.min(c.in.density, rhoG);
        }

        /** Specific shaft work (J/kg) from the inlet to the outlet pressure. */
        public double specificWork(GasPressureStage.Context c) {
            double pressureOut = Math.max(c.pressureOut, c.pressureIn * builtInRatio);
            if (pressureOut <= c.pressureIn) return 0.0;
            double hs = c.gas.isentropicEnthalpy(c.pressureIn, c.enthalpyIn, pressureOut);
            return Math.max(0.0, hs - c.enthalpyIn) / isentropicEfficiency;
        }

        /** How far past its built-in ratio a compressor with one lets its loop's ratio go before it has stopped. */
        public static final double UNLOADING_BAND = 0.5;

        /**
         * The share of its speed a compressor with a built-in ratio runs at: all of it up to that ratio, then less
         * as the loop's ratio passes it, down to none at (1 + UNLOADING_BAND) times it. So it doesn't pump a lightly
         * loaded evaporator down to a vacuum at full power: it holds the loop's ratio in that band and idles.
         */
        public double unloading(GasPressureStage.Context c) {
            if (builtInRatio <= 1.0 || c.pressureIn <= 0.0) return 1.0;
            double over = c.pressureOut / c.pressureIn / builtInRatio - 1.0;
            return Math.max(0.0, Math.min(1.0, 1.0 - over / UNLOADING_BAND));
        }

        /** Runs at a speed between 0 and 1. */
        public void compute(GasPressureStage.Context c, double speed) {
            c.clearAnswers();
            if (!c.loopRunning || speed <= 0.0) return;
            double ratio = c.pressureOut / c.pressureIn;
            double volume = Math.min(displacement * speed * volumetricEfficiency(c.gas, ratio), c.maxVolumetricFlow);
            double flow = Math.min(volume * suctionDensity(c), c.maxMassFlow);
            double w = specificWork(c);
            c.massFlow = flow;
            c.enthalpyOut = c.enthalpyIn + w;
            c.shaftPowerWatts = flow * w;
        }

        /**
         * The speed at which the compressor's shaft takes no more than maxWatts: its power at full speed without the
         * engine's limit on its flow (c.maxMassFlow), which compute applies afterwards. Sized from fullSpeedPower,
         * which has that limit in it, a compressor the engine was holding back ran over its power when the limit
         * rose.
         */
        public double speedForPower(GasPressureStage.Context c, double maxWatts) {
            if (!c.loopRunning) return 1.0;
            double ratio = c.pressureOut / c.pressureIn;
            double volume = Math.min(displacement * volumetricEfficiency(c.gas, ratio), c.maxVolumetricFlow);
            double watts = volume * suctionDensity(c) * specificWork(c);
            return watts > maxWatts ? Math.max(0.0, maxWatts / watts) : 1.0;
        }

        /** Shaft power (W) at full speed in this context. */
        public double fullSpeedPower(GasPressureStage.Context c) {
            if (!c.loopRunning) return 0.0;
            double ratio = c.pressureOut / c.pressureIn;
            double volume = Math.min(displacement * volumetricEfficiency(c.gas, ratio), c.maxVolumetricFlow);
            return Math.min(volume * suctionDensity(c), c.maxMassFlow) * specificWork(c);
        }
    }

    /** Mass flow (kg/s) through an orifice of effective area (m^2) from the inlet state to the outlet pressure. */
    public static double orificeFlow(GasPressureStage.Context c, double area) {
        double dp = c.pressureIn - c.pressureOut;
        // Below this the zones count as equal: the flow stops, and a stopped loop comes to rest
        if (dp <= EQUAL_BELOW_PA || area <= 0.0) return 0.0;
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
