package com.gtnewhorizons.modularnuclear.common.nuclear;

import java.util.Random;

/**
 * The physics parameters a {@link NuclearSimulationEngine} runs with, the values derived from them, and its random
 * number source. An engine holds its set; the tiles of a grid read their owner's (the reactor's or the simulator
 * grid's). {@link NuclearSimulationEngine#params()} is the set the game installs from its configuration, and the set
 * new engines are built from.
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
    public double ic2CoolantHeatPerLiter = NuclearSimulationEngine.DEFAULT_IC2_COOLANT_HEAT_PER_LITER;
    public double fuelBurnupMultiplier = NuclearSimulationEngine.DEFAULT_FUEL_BURNUP_MULTIPLIER;
    /** Scales every fuel's base neutron emission, on top of its archetype's multiplier. */
    public double neutronYieldMultiplier = NuclearSimulationEngine.DEFAULT_NEUTRON_YIELD_MULT;
    public double coolantPassageHeatTransferMultiplier = NuclearSimulationEngine.DEFAULT_COOLANT_PASSAGE_HEAT_TRANSFER_MULT;
    /** Factor on passage heat transfer per voltage tier above MV. */
    public double coolantPassageHeatTransferTierRatio = NuclearSimulationEngine.DEFAULT_COOLANT_PASSAGE_HEAT_TRANSFER_TIER_RATIO;
    /** Share of the fission heat per neutron a fuel's spontaneous emission deposits (its chain emission: all). */
    public double spontaneousHeatFraction = NuclearSimulationEngine.DEFAULT_SPONTANEOUS_HEAT_FRACTION;
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
    /** Conductance (HU/(s·°C)) of an MV (tier 2) nuclear core hatch; see {@link #hatchConductance(int)}. */
    public double baseHatchConductance = NuclearSimulationEngine.DEFAULT_BASE_HATCH_CONDUCTANCE;
    /**
     * Factor by which a nuclear core hatch's conductance grows per tier. Its heat capacity doubles per tier, so above 2
     * a higher tier also removes a larger share of its excess heat (better materials, not only a bigger hatch).
     */
    public double hatchConductanceTierRatio = NuclearSimulationEngine.DEFAULT_HATCH_CONDUCTANCE_TIER_RATIO;
    /**
     * Heat capacity (HU/°C) of a tier-0 nuclear core hatch's structure; a hatch of tier t holds this times 2^t, as its
     * tank holds 8000 L times 2^t. Every other core tile holds {@link #huPerDegree}.
     */
    public double coreHatchHeatCapacity = NuclearSimulationEngine.DEFAULT_CORE_HATCH_HEAT_CAPACITY;

    /** The engine's random number source (neutron directions, stochastic interactions). */
    public Random random = new FastRandom();

    /** Hatch conductance per tier (base × ratio^(tier − MV)), see hatchConductance. */
    public final TierScale hatchConductanceByTier = new TierScale(2);
    /** Passage heat transfer factor per tier, see passageHeatTransferFactor. */
    public final TierScale passageHeatTransferByTier = new TierScale(2);

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
        c.coolantPassageHeatTransferTierRatio = coolantPassageHeatTransferTierRatio;
        c.spontaneousHeatFraction = spontaneousHeatFraction;
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
        c.ic2CoolantHeatPerLiter = ic2CoolantHeatPerLiter;
        c.random = new FastRandom();
        return c;
    }

    /** Rounds down and adds one with probability equal to the fractional part. */
    public int stochasticRound(double value) {
        if (value <= 0.0) return 0;
        int whole = (int) value;
        return whole + (random.nextDouble() < value - whole ? 1 : 0);
    }

    /** Base neutron multiplier of a fuel archetype, including the global neutron yield multiplier. */
    public double nbaseMultiplier(String archetype) {
        double mult = switch (NuclearSimulationEngine.archetypeOf(archetype)) {
            case NuclearSimulationEngine.ARCHETYPE_DENSE -> denseNbaseMultiplier;
            case NuclearSimulationEngine.ARCHETYPE_EXCITED -> excitedNbaseMultiplier;
            case NuclearSimulationEngine.ARCHETYPE_BREEDER -> breederNbaseMultiplier;
            default -> standardNbaseMultiplier;
        };
        return mult * neutronYieldMultiplier;
    }

    /** Fission multiplier of a fuel archetype. */
    public double fissionMultiplier(String archetype) {
        return switch (NuclearSimulationEngine.archetypeOf(archetype)) {
            case NuclearSimulationEngine.ARCHETYPE_DENSE -> denseFissionMultiplier;
            case NuclearSimulationEngine.ARCHETYPE_EXCITED -> excitedFissionMultiplier;
            case NuclearSimulationEngine.ARCHETYPE_BREEDER -> breederFissionMultiplier;
            default -> standardFissionMultiplier;
        };
    }

    /** Heat capacity (HU/°C) of a nuclear core hatch of the given tier (LV=1 ... UHV=9), whatever it holds. */
    public double hatchHeatCapacity(int tier) {
        return coreHatchHeatCapacity * TierScale.pow2(tier);
    }

    /**
     * Thermal conductance U in HU/(s·°C) of a nuclear core hatch of the given tier: the base (MV) conductance times the
     * tier ratio per tier above MV. With the defaults an EV hatch removes 20% of its excess heat per second and a UHV
     * hatch 60%.
     */
    public double hatchConductance(int tier) {
        return hatchConductanceByTier.get(baseHatchConductance, hatchConductanceTierRatio, tier);
    }

    /**
     * Factor on a coolant passage's convective heat transfer for its voltage tier: the configured multiplier at MV
     * (tier 2), times the tier ratio per tier above.
     */
    public double passageHeatTransferFactor(int tier) {
        return passageHeatTransferByTier
            .get(coolantPassageHeatTransferMultiplier, coolantPassageHeatTransferTierRatio, tier);
    }

    /**
     * Heat (HU) a core hatch at {@code temp} conducts into a coolant sink at {@code sinkTemp} over {@code ticks}:
     * exact exponential cooling of the hatch's heat capacity at its conductance, scaled by efficiency.
     */
    public double conductiveHeatTransfer(double temp, double sinkTemp, int tier, double efficiency, int ticks) {
        if (temp <= sinkTemp || efficiency <= 0.0) return 0.0;
        double deltaT = temp - sinkTemp;
        double effFactor = Math.max(0.0, Math.min(1.0, efficiency));
        double conductance = hatchConductance(tier) * effFactor;
        double ch = hatchHeatCapacity(tier);
        double dt = (double) Math.max(1, ticks) / 20.0;
        return ch * deltaT * (1.0 - Math.exp(-conductance * dt / ch));
    }

    // Setters that keep each value in its sensible range

    public void setHuPerDegree(double v) {
        huPerDegree = Math.max(0.1, v);
    }

    public void setGlobalThermalFissionMultiplier(double mult) {
        globalThermalFissionMultiplier = Math.max(0.0, mult);
        thermalFissionMultiplier = globalThermalFissionMultiplier;
    }

    public void setGlobalFastFissionMultiplier(double mult) {
        globalFastFissionMultiplier = Math.max(0.0, mult);
    }

    public void setFastAbsorptionDamageMultiplier(double mult) {
        fastAbsorptionDamageMultiplier = Math.max(0.0, mult);
    }

    public void setThermalAbsorptionDamageMultiplier(double mult) {
        thermalAbsorptionDamageMultiplier = Math.max(0.0, mult);
    }

    public void setBeamEnergyToFastNeutronMultiplier(double mult) {
        beamEnergyToFastNeutronMultiplier = Math.max(0.0, mult);
    }

    public void setFuelBurnupMultiplier(double mult) {
        fuelBurnupMultiplier = Math.max(0.0, mult);
    }

    public void setAmbientTemperature(double temp) {
        ambientTemp = temp;
    }

    public void setTicksPerCycle(int ticks) {
        ticksPerCycle = Math.max(1, ticks);
    }

    public void setGridConductionMultiplier(double mult) {
        gridConductionMultiplier = Math.max(0.0, mult);
    }

    public void setBaseHatchConductance(double val) {
        baseHatchConductance = Math.max(0.01, val);
    }

    public void setHatchConductanceTierRatio(double val) {
        hatchConductanceTierRatio = Math.max(1.0, val);
    }

    public void setCoreHatchHeatCapacity(double val) {
        coreHatchHeatCapacity = Math.max(0.1, val);
    }

    /** Seeds the random source, so a run can be repeated exactly. */
    public void setRandomSeed(long seed) {
        random.setSeed(seed);
    }

    public void setSimulationParameters(double low, double high, double power, double fissionMult,
        double heatPerNeutron, double hpBoil) {
        tempThresholdLow = low;
        tempThresholdHigh = high;
        reactivityPower = power;
        setGlobalThermalFissionMultiplier(fissionMult);
        fissionHeatPerNeutron = heatPerNeutron;
        hpWaterBoilingPoint = hpBoil;
    }
}
