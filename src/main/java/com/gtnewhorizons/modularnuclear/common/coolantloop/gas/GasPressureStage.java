package com.gtnewhorizons.modularnuclear.common.coolantloop.gas;

/**
 * A device that changes a gas loop's pressure: a compressor, an expansion valve or a turbo-expander. GasLoopEngine
 * calls it once per step with its inlet state and outlet pressure, and it answers with the mass flow it passes and
 * the enthalpy the gas leaves at. See Note [Gas loop pressure zones]
 */
public interface GasPressureStage {

    /** What a stage sees and answers in one step. */
    final class Context {

        public GasProperty gas;
        /** The inlet: the upstream zone's pressure and the enthalpy flowing in. */
        public double pressureIn;
        public double enthalpyIn;
        /** The inlet's derived state. */
        public final GasProperty.State in = new GasProperty.State();
        /** The downstream zone's pressure. */
        public double pressureOut;
        /** Whether the loop runs: its pump allows it and the loop is charged. */
        public boolean loopRunning;
        public double dt;
        /**
         * Superheat (K) of the gas leaving the downstream zone towards its next stage: what a thermostatic valve
         * holds. See GasProperty.State.superheatK.
         */
        public double downstreamExitSuperheatK;
        /** The most volumetric flow (m^3/s) the stage's pipes carry; infinite when unknown. */
        public double maxVolumetricFlow = Double.POSITIVE_INFINITY;
        /** The downstream zone's weakest pressure rating, Pa; infinite for none. */
        public double downstreamMaxPressure = Double.POSITIVE_INFINITY;
        /** The most mass flow (kg/s) the engine lets the stage pass this step. See Note [Gas loop pressure zones] */
        public double maxMassFlow = Double.POSITIVE_INFINITY;

        // Answers
        /** Mass flow, kg/s, from inlet to outlet; never negative. */
        public double massFlow;
        /** Enthalpy the gas leaves at, J/kg. */
        public double enthalpyOut;
        /** Shaft power in W: positive when the stage spends it (a compressor), negative when it gives it out. */
        public double shaftPowerWatts;

        public void clearAnswers() {
            massFlow = 0.0;
            enthalpyOut = enthalpyIn;
            shaftPowerWatts = 0.0;
        }
    }

    /** Computes this step's flow and outlet enthalpy, and does what the device does with its power. */
    void computeStage(Context c);

    /**
     * Whether the stage's flow only follows the pressure difference (a valve, an expander), so it may be held back
     * from overshooting equalization within a step. A compressor drives its flow.
     */
    default boolean isPassive() {
        return true;
    }
}
