package com.gtnewhorizons.modularnuclear.common.nuclear;

import java.util.Random;

/**
 * The physics parameters {@link NuclearSimulationEngine} runs with, and its random number source. The engine reads
 * the set installed for the current thread ({@link NuclearSimulationEngine#withParameters}), or the shared one: so
 * a simulator run, a calibration candidate or a test can use its own set without disturbing anyone else's.
 */
public final class EngineParameters {

    public double huPerDegree = NuclearSimulationEngine.DEFAULT_HU_PER_DEGREE;
    public double ambientTemp = NuclearSimulationEngine.DEFAULT_AMBIENT_TEMP;
    public int ticksPerCycle = NuclearSimulationEngine.DEFAULT_TICKS_PER_CYCLE;
    public double tempThresholdLow = NuclearSimulationEngine.DEFAULT_TEMP_THRESHOLD_LOW;
    public double tempThresholdHigh = NuclearSimulationEngine.DEFAULT_TEMP_THRESHOLD_HIGH;
    public double reactivityPower = NuclearSimulationEngine.DEFAULT_REACTIVITY_POWER;
    public double globalThermalFissionMultiplier = NuclearSimulationEngine.DEFAULT_GLOBAL_THERMAL_FISSION_MULT;
    public double thermalFissionMultiplier = NuclearSimulationEngine.DEFAULT_GLOBAL_THERMAL_FISSION_MULT;
    public double globalFastFissionMultiplier = NuclearSimulationEngine.DEFAULT_GLOBAL_FAST_FISSION_MULT;
    public double fastAbsorptionDamageMultiplier = NuclearSimulationEngine.DEFAULT_FAST_ABSORPTION_DAMAGE_MULT;
    public double thermalAbsorptionDamageMultiplier = NuclearSimulationEngine.DEFAULT_THERMAL_ABSORPTION_DAMAGE_MULT;
    public double fissionHeatPerNeutron = NuclearSimulationEngine.DEFAULT_FISSION_HEAT_PER_NEUTRON;
    public double hpWaterBoilingPoint = NuclearSimulationEngine.DEFAULT_HP_WATER_BOILING_POINT;
    public int hatchCoolantCapacity = NuclearSimulationEngine.DEFAULT_HATCH_CAPACITY;
    public NuclearSimulationEngine.TurnoverCurve turnoverCurve = NuclearSimulationEngine.DEFAULT_TURNOVER_CURVE;
    public double turnoverDeltaTMax = NuclearSimulationEngine.DEFAULT_TURNOVER_DELTA_T_MAX;
    public double turnoverExponent = NuclearSimulationEngine.DEFAULT_TURNOVER_EXPONENT;
    public int coolantFeedRate = NuclearSimulationEngine.DEFAULT_COOLANT_FEED_RATE;
    public double coolingHeatPerLiter = NuclearSimulationEngine.DEFAULT_COOLING_HEAT_PER_LITER;
    public double fuelBurnupMultiplier = NuclearSimulationEngine.DEFAULT_FUEL_BURNUP_MULTIPLIER;
    /** Scales every fuel's base neutron emission, on top of its archetype's multiplier. */
    public double neutronYieldMultiplier = NuclearSimulationEngine.DEFAULT_NEUTRON_YIELD_MULT;
    public double coolantPassageHeatTransferMultiplier = NuclearSimulationEngine.DEFAULT_COOLANT_PASSAGE_HEAT_TRANSFER_MULT;
    public double denseNbaseMultiplier = NuclearSimulationEngine.DEFAULT_DENSE_NBASE_MULT;
    public double denseFissionMultiplier = NuclearSimulationEngine.DEFAULT_DENSE_FISSION_MULT;
    public double excitedNbaseMultiplier = NuclearSimulationEngine.DEFAULT_EXCITED_NBASE_MULT;
    public double excitedFissionMultiplier = NuclearSimulationEngine.DEFAULT_EXCITED_FISSION_MULT;
    public double standardNbaseMultiplier = NuclearSimulationEngine.DEFAULT_STANDARD_NBASE_MULT;
    public double standardFissionMultiplier = NuclearSimulationEngine.DEFAULT_STANDARD_FISSION_MULT;
    public double breederNbaseMultiplier = NuclearSimulationEngine.DEFAULT_BREEDER_NBASE_MULT;
    public double breederFissionMultiplier = NuclearSimulationEngine.DEFAULT_BREEDER_FISSION_MULT;
    public double wallReflectionChance = NuclearSimulationEngine.DEFAULT_WALL_REFLECTION_CHANCE;
    public double beamEnergyToFastNeutronMultiplier = NuclearSimulationEngine.DEFAULT_BEAM_ENERGY_TO_FAST_NEUTRON_MULTIPLIER;
    /** Scales cell-to-cell heat conduction (not boundary loss or hatch-to-coolant transfer). */
    public double gridConductionMultiplier = NuclearSimulationEngine.DEFAULT_GRID_CONDUCTION_MULTIPLIER;
    /** Conductance (HU/(s·°C)) of an MV (tier 2) nuclear core hatch; see {@link #getHatchConductance(int)}. */
    public double baseHatchConductance = NuclearSimulationEngine.DEFAULT_BASE_HATCH_CONDUCTANCE;
    /**
     * Factor by which a nuclear core hatch's conductance grows per tier. Its heat capacity doubles per tier, so above 2
     * a higher tier also removes a larger share of its excess heat (better materials, not only a bigger hatch).
     */
    public double hatchConductanceTierRatio = NuclearSimulationEngine.DEFAULT_HATCH_CONDUCTANCE_TIER_RATIO;
    /**
     * Heat capacity (HU/°C) of a tier-0 nuclear core hatch's structure; a hatch of tier t holds this times 2^t, as its
     * tank holds 8000 L times 2^t. Every other core tile holds {@link #HU_PER_DEGREE}.
     */
    public double coreHatchHeatCapacity = NuclearSimulationEngine.DEFAULT_CORE_HATCH_HEAT_CAPACITY;

    /** The engine's random number source (neutron directions, stochastic interactions). */
    public Random random = new Random();

    /** An independent copy: changing it leaves this one (and its random sequence) as it was. */
    public EngineParameters copy() {
        EngineParameters c = new EngineParameters();
        c.huPerDegree = huPerDegree;
        c.ambientTemp = ambientTemp;
        c.ticksPerCycle = ticksPerCycle;
        c.tempThresholdLow = tempThresholdLow;
        c.tempThresholdHigh = tempThresholdHigh;
        c.reactivityPower = reactivityPower;
        c.globalThermalFissionMultiplier = globalThermalFissionMultiplier;
        c.thermalFissionMultiplier = thermalFissionMultiplier;
        c.globalFastFissionMultiplier = globalFastFissionMultiplier;
        c.fastAbsorptionDamageMultiplier = fastAbsorptionDamageMultiplier;
        c.thermalAbsorptionDamageMultiplier = thermalAbsorptionDamageMultiplier;
        c.fissionHeatPerNeutron = fissionHeatPerNeutron;
        c.hpWaterBoilingPoint = hpWaterBoilingPoint;
        c.hatchCoolantCapacity = hatchCoolantCapacity;
        c.turnoverCurve = turnoverCurve;
        c.turnoverDeltaTMax = turnoverDeltaTMax;
        c.turnoverExponent = turnoverExponent;
        c.coolantFeedRate = coolantFeedRate;
        c.coolingHeatPerLiter = coolingHeatPerLiter;
        c.fuelBurnupMultiplier = fuelBurnupMultiplier;
        c.neutronYieldMultiplier = neutronYieldMultiplier;
        c.coolantPassageHeatTransferMultiplier = coolantPassageHeatTransferMultiplier;
        c.denseNbaseMultiplier = denseNbaseMultiplier;
        c.denseFissionMultiplier = denseFissionMultiplier;
        c.excitedNbaseMultiplier = excitedNbaseMultiplier;
        c.excitedFissionMultiplier = excitedFissionMultiplier;
        c.standardNbaseMultiplier = standardNbaseMultiplier;
        c.standardFissionMultiplier = standardFissionMultiplier;
        c.breederNbaseMultiplier = breederNbaseMultiplier;
        c.breederFissionMultiplier = breederFissionMultiplier;
        c.wallReflectionChance = wallReflectionChance;
        c.beamEnergyToFastNeutronMultiplier = beamEnergyToFastNeutronMultiplier;
        c.gridConductionMultiplier = gridConductionMultiplier;
        c.baseHatchConductance = baseHatchConductance;
        c.hatchConductanceTierRatio = hatchConductanceTierRatio;
        c.coreHatchHeatCapacity = coreHatchHeatCapacity;
        c.random = new Random();
        return c;
    }
}
