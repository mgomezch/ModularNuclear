package com.gtnewhorizons.modularnuclear.common.nuclear;

import com.gtnewhorizons.coolantloops.common.config.CoolantLoopConfigData;
import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;
import com.gtnewhorizons.coolantloops.engine.LoopSegment;
import com.gtnewhorizons.modularnuclear.common.config.ReactorConfigData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData;

/**
 * A reactor coolant passage (high-pressure hatch pair) as a grid cell and as a coolant loop device, shared by the
 * game's high-pressure hatches and the standalone simulator.
 */
public final class CoolantPassage {

    /** Joules per heat unit (HU) when exchanging heat with a coolant loop. */
    public static final double JOULES_PER_HU = 128.0;
    /** Minor loss coefficient of a pressure-tube passage with fuel bundle spacers. */
    public static final double MINOR_LOSS_K = 2.5;
    /** Conduction coefficient of the passage cell (metal pressure tube). */
    public static final double HEAT_TRANSFER_COEFF = 0.90;
    public static final double SCATTERING = 0.80;
    /** Moderation when the passage is not connected to a loop, or the loop fluid has no coolant entry. */
    public static final double DEFAULT_MODERATION = 0.05;

    private CoolantPassage() {}

    public static double absorption(NeutronType type) {
        return type == NeutronType.THERMAL ? 0.05 : 0.02;
    }

    /** Moderation of a passage carrying the given loop fluid (its components.yaml coolant fluid moderation). */
    public static double moderation(String loopFluidName) {
        if (loopFluidName == null) return DEFAULT_MODERATION;
        ComponentData.CoolantFluidData cfd = ReactorConfigData.findCoolantFluid(loopFluidName);
        return cfd != null ? cfd.moderation : DEFAULT_MODERATION;
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
     * turbulent flow, Nu = 4.36 in laminar flow), limited so the two cannot cross over in temperature. Updates the
     * segment's temperature and returns the heat moved from the cell into the coolant in HU (negative if the coolant
     * heated the cell).
     */
    public static double exchange(double cellTempC, double flowM3s, double dtSeconds, CoolantFluidProperty fluid,
        LoopSegment segment) {
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
        double h = (nu * kThermal) / diameter;
        double heatJoules = h * area * deltaT * dtSeconds;

        double cellCapacity = NuclearSimulationEngine.HU_PER_DEGREE * JOULES_PER_HU;
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
