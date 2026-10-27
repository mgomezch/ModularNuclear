package com.gtnewhorizons.modularnuclear.common.nuclear;

import com.gtnewhorizons.modularnuclear.common.config.CoolantLoopConfigData;
import com.gtnewhorizons.modularnuclear.common.config.ReactorConfigData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData;
import com.gtnewhorizons.modularnuclear.common.config.data.PhysicsData;
import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantFluidProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopSegment;

/**
 * A reactor coolant passage (high-pressure hatch pair) as a grid cell and as a coolant loop device, shared by the
 * game's high-pressure hatches and the standalone simulator.
 */
public final class CoolantPassage {

    /** Joules per heat unit (HU) when exchanging heat with a coolant loop. */
    public static final double JOULES_PER_HU = 128.0;

    private CoolantPassage() {}

    /** The passage's cell and loop properties (physics.yaml, coolant_passage_*). */
    private static PhysicsData config() {
        return ReactorConfigData.getPhysics();
    }

    /** Minor loss coefficient of the passage in its loop. */
    public static double minorLossK() {
        return config().coolantPassageMinorLossK;
    }

    /** Conduction coefficient of the passage cell. */
    public static double conduction() {
        return config().coolantPassageConduction;
    }

    public static double scattering() {
        return config().coolantPassageScattering;
    }

    public static double absorption(NeutronType type) {
        return type == NeutronType.THERMAL ? config().coolantPassageThermalAbsorption
            : config().coolantPassageFastAbsorption;
    }

    /** Moderation when the passage is on no loop, or its loop fluid has no coolant entry. */
    public static double defaultModeration() {
        return config().coolantPassageDefaultModeration;
    }

    /** Moderation of a passage carrying the given loop fluid (its components.yaml coolant fluid moderation). */
    public static double moderation(String loopFluidName) {
        if (loopFluidName == null) return defaultModeration();
        ComponentData.CoolantFluidData cfd = ReactorConfigData.findCoolantFluid(loopFluidName);
        return cfd != null ? cfd.moderation : defaultModeration();
    }

    /** Neutrons a passage absorbs per radiolysis event. */
    public static int neutronsPerRadiolysis() {
        return config().coolantPassageNeutronsPerRadiolysis;
    }

    /**
     * Gas that radiolysis of the loop fluid produces ("Deuterium" or "Tritium"), or null if the fluid has none. Each
     * event turns 2 L of coolant into 2 L of that gas and 1 L of oxygen.
     */
    public static String radiolysisGas(String loopFluidName) {
        ComponentData.CoolantFluidData cfd = ReactorConfigData
            .findCoolantFluid(loopFluidName != null ? loopFluidName : "ic2distilledwater");
        if (cfd == null || cfd.transmutationProduct == null) return null;
        if ("tritium".equalsIgnoreCase(cfd.transmutationProduct)) return "Tritium";
        if ("deuterium".equalsIgnoreCase(cfd.transmutationProduct)) return "Deuterium";
        return null;
    }

    public static double length() {
        return CoolantLoopConfigData.getPhysicsData()
            .getStandardPassageLength();
    }

    public static double diameter() {
        return CoolantLoopConfigData.getPhysicsData()
            .getStandardPassageDiameter();
    }

    /**
     * One tick of convective exchange between a passage cell and the coolant in its loop segment (Dittus-Boelter in
     * turbulent flow, Nu = 4.36 in laminar flow, scaled by the configured passage heat transfer multiplier), limited so
     * the two cannot cross over in temperature. Updates the
     * segment's temperature and returns the heat moved from the cell into the coolant in HU (negative if the coolant
     * heated the cell).
     */
    public static double exchange(double cellTempC, double flowM3s, double dtSeconds, CoolantFluidProperty fluid,
        LoopSegment segment) {
        return exchange(cellTempC, NuclearSimulationEngine.HU_PER_DEGREE, flowM3s, dtSeconds, fluid, segment);
    }

    /**
     * As {@link #exchange(double, double, double, CoolantFluidProperty, LoopSegment)}, for a cell of the given heat
     * capacity (HU/°C).
     */
    public static double exchange(double cellTempC, double cellCapacityHu, double flowM3s, double dtSeconds,
        CoolantFluidProperty fluid, LoopSegment segment) {
        if (flowM3s <= 1e-6 || fluid == null || segment == null) return 0.0;
        double coolantTemp = segment.getCurrentTemperatureCelsius();
        double deltaT = cellTempC - coolantTemp;
        if (Math.abs(deltaT) < 1e-4) return 0.0;

        double diameter = diameter();
        double area = Math.PI * diameter * length();
        double flowArea = Math.PI * Math.pow(diameter / 2.0, 2);
        double velocity = flowM3s / flowArea;

        double rho = fluid.getDensity();
        double mu = fluid.getDynamicViscosity();
        double kThermal = fluid.getThermalConductivity();
        double cp = fluid.getSpecificHeat();
        double re = (rho * velocity * diameter) / Math.max(1e-6, mu);
        double pr = (mu * cp) / Math.max(1e-4, kThermal);
        double nu = re < 2300.0 ? 4.36 : 0.023 * Math.pow(re, 0.8) * Math.pow(Math.max(0.6, pr), 0.4);
        double h = (nu * kThermal) / diameter * NuclearSimulationEngine.coolantPassageHeatTransferMultiplier;
        double heatJoules = h * area * deltaT * dtSeconds;

        double cellCapacity = cellCapacityHu * JOULES_PER_HU;
        double segmentCapacity = segment.getVolume() * rho * cp;
        double maxJoules = segmentCapacity > 1e-4
            ? (cellCapacity * segmentCapacity) / (cellCapacity + segmentCapacity) * Math.abs(deltaT)
            : cellCapacity * Math.abs(deltaT);
        if (Math.abs(heatJoules) > maxJoules) heatJoules = Math.signum(heatJoules) * maxJoules;

        if (segmentCapacity > 1e-4) {
            segment.setCurrentTemperatureCelsius(coolantTemp + heatJoules / segmentCapacity);
        }
        return heatJoules / JOULES_PER_HU;
    }
}
