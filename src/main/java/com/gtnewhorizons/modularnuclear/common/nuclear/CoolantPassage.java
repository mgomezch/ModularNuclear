package com.gtnewhorizons.modularnuclear.common.nuclear;

import com.gtnewhorizons.modularnuclear.common.config.CoolantLoopConfigData;
import com.gtnewhorizons.modularnuclear.common.config.ReactorConfigData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData;
import com.gtnewhorizons.modularnuclear.common.config.data.PhysicsData;
import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantFluidProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopSegment;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.SegmentHeat;

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

    /** Heat capacity (HU/°C) of a passage of the given voltage tier: 2^tier times the configured base. */
    public static double heatCapacity(int tier) {
        return config().coolantPassageHeatCapacity * TierScale.pow2(tier);
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
     * One tick of convective exchange between a passage cell of the given heat capacity (HU/°C) and voltage tier and
     * the
     * coolant in its loop segment (Dittus-Boelter in turbulent flow, Nu = 4.36 in laminar flow, scaled by the tier's
     * {@link EngineParameters#passageHeatTransferFactor(int)}), limited so the two cannot cross over in temperature.
     * Updates the segment's
     * temperature and returns the heat moved from the cell into the coolant in HU (negative if the coolant heated the
     * cell).
     */
    public static double exchange(EngineParameters p, double cellTempC, double cellCapacityHu, int tier, double flowM3s,
        double dtSeconds, CoolantFluidProperty fluid, LoopSegment segment) {
        double d = diameter();
        double area = Math.PI * d * length();
        if (segment != null && segment.holdsGas()) {
            // A reactor barely cools through a gas loop, so gas loops leave the reactor's balance alone
            return exchangeGas(
                p,
                cellTempC,
                cellCapacityHu,
                tier,
                area,
                GAS_PASSAGE_HEAT_TRANSFER,
                flowM3s,
                dtSeconds,
                fluid,
                segment);
        }
        return exchange(p, cellTempC, cellCapacityHu, tier, area, 1.0, flowM3s, dtSeconds, fluid, segment);
    }

    /**
     * One tick of convective exchange between a heated element of the given heat capacity (HU/°C), voltage tier,
     * surface area (m²), and efficiency factor [0.0, 1.0] and the coolant in its loop segment (Dittus-Boelter in
     * turbulent flow, Nu = 4.36 in laminar flow, scaled by the tier's
     * {@link EngineParameters#passageHeatTransferFactor(int)} and
     * {@code efficiencyFactor}), limited so the two cannot cross over in temperature. Updates the segment's
     * temperature and returns the heat moved from the element into the coolant in HU (negative if the coolant heated
     * the element).
     */
    public static double exchange(EngineParameters p, double cellTempC, double cellCapacityHu, int tier, double areaM2,
        double efficiencyFactor, double flowM3s, double dtSeconds, CoolantFluidProperty fluid, LoopSegment segment) {
        if (flowM3s <= 1e-6 || fluid == null || segment == null || cellCapacityHu <= 0.0 || areaM2 <= 0.0) return 0.0;
        if (segment.holdsGas()) {
            return exchangeGas(
                p,
                cellTempC,
                cellCapacityHu,
                tier,
                areaM2,
                efficiencyFactor,
                flowM3s,
                dtSeconds,
                fluid,
                segment);
        }
        double eff = Math.max(0.0, Math.min(1.0, efficiencyFactor));
        if (eff <= 0.0) return 0.0;

        double coolantTemp = segment.getCurrentTemperatureCelsius();
        double deltaT = cellTempC - coolantTemp;
        if (Math.abs(deltaT) < 1e-4) return 0.0;

        double diameter = diameter();
        double flowArea = Math.PI * Math.pow(diameter / 2.0, 2);
        double velocity = flowM3s / flowArea;

        double rho = fluid.getDensity();
        double mu = fluid.getDynamicViscosity();
        double kThermal = fluid.getThermalConductivity();
        double cp = fluid.getSpecificHeat();
        double re = (rho * velocity * diameter) / Math.max(1e-6, mu);
        double pr = (mu * cp) / Math.max(1e-4, kThermal);
        double nu = re < 2300.0 ? 4.36 : 0.023 * Math.pow(re, 0.8) * Math.pow(Math.max(0.6, pr), 0.4);
        double h = (nu * kThermal) / diameter * p.passageHeatTransferFactor(tier) * eff;
        double heatJoules = h * areaM2 * deltaT * dtSeconds;

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

    /** Share of a passage's convection left on a gas loop: gas loops are for refrigeration, not for cooling a core. */
    public static final double GAS_PASSAGE_HEAT_TRANSFER = 0.02;

    /**
     * The exchange on a gas loop: Dittus-Boelter with the gas's properties in its phase (two-phase gas boils or
     * condenses on the wall, SegmentHeat.TWO_PHASE_FACTOR better than its liquid), the heat put into the gas flowing
     * through (SegmentHeat), limited by the cell's heat as on a liquid loop. Returns HU moved into the gas.
     */
    static double exchangeGas(EngineParameters p, double cellTempC, double cellCapacityHu, int tier, double areaM2,
        double factor, double flowM3s, double dtSeconds, CoolantFluidProperty fluid, LoopSegment segment) {
        GasProperty gas = GasProperty.get(fluid.getFluidName());
        if (gas == null || flowM3s <= 1e-9 || cellCapacityHu <= 0.0 || areaM2 <= 0.0) return 0.0;
        double deltaT = cellTempC - segment.getCurrentTemperatureCelsius();
        if (Math.abs(deltaT) < 1e-4) return 0.0;
        double x = segment.getGasQuality();
        boolean vapour = x >= 1.0;
        double rho = Math.max(1e-6, segment.getGasDensity());
        double mu = vapour ? gas.getVaporViscosity() : gas.getLiquidViscosity();
        double k = vapour ? gas.getVaporConductivity() : gas.getLiquidConductivity();
        double cp = vapour ? gas.getCpVapor() : gas.getCpLiquid();
        double d = diameter();
        double velocity = flowM3s / (Math.PI * d * d / 4.0);
        double re = rho * velocity * d / Math.max(1e-7, mu);
        double pr = mu * cp / Math.max(1e-5, k);
        double nu = re < 2300.0 ? 4.36 : 0.023 * Math.pow(re, 0.8) * Math.pow(Math.max(0.6, pr), 0.4);
        double h = nu * k / d * p.passageHeatTransferFactor(tier) * Math.max(0.0, Math.min(1.0, factor));
        if (x > 0.0 && x < 1.0) h *= SegmentHeat.TWO_PHASE_FACTOR;
        double heat = h * areaM2 * deltaT * dtSeconds;
        double cellCapacity = cellCapacityHu * JOULES_PER_HU;
        heat = Math.signum(heat) * Math.min(Math.abs(heat), cellCapacity * Math.abs(deltaT));
        double moved = SegmentHeat.exchange(gas, segment, cellTempC + 273.15, heat, dtSeconds);
        return moved / JOULES_PER_HU;
    }
}
