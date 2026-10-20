package com.gtnewhorizons.modularnuclear.common.nuclear;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class NuclearSimulationEngine {

    public static final double HU_FOR_FAST_NEUTRON = 8.0;
    public static final double DEFAULT_HU_PER_DEGREE = 32.0;
    public static double huPerDegree = DEFAULT_HU_PER_DEGREE;
    public static double HU_PER_DEGREE = DEFAULT_HU_PER_DEGREE;
    public static final double BASE_HEAT_CONDUCTION = 0.01;
    public static final double DEFAULT_AMBIENT_TEMP = 20.0;
    public static double ambientTemp = DEFAULT_AMBIENT_TEMP;
    public static double AMBIENT_TEMP = DEFAULT_AMBIENT_TEMP;
    public static final double DEFAULT_TEMP_THRESHOLD_LOW = 800.0;
    public static final double DEFAULT_TEMP_THRESHOLD_HIGH = 3200.0;
    public static final double DEFAULT_REACTIVITY_POWER = 1.2;
    public static final double DEFAULT_GLOBAL_THERMAL_FISSION_MULT = 1.0;
    public static final double DEFAULT_THERMAL_FISSION_MULT = DEFAULT_GLOBAL_THERMAL_FISSION_MULT;
    public static final double DEFAULT_GLOBAL_FAST_FISSION_MULT = 1.0;
    public static final double DEFAULT_FAST_ABSORPTION_DAMAGE_MULT = 8.0;
    public static final double DEFAULT_THERMAL_ABSORPTION_DAMAGE_MULT = 1.0;
    public static final double DEFAULT_FISSION_HEAT_PER_NEUTRON = 38.0;
    public static final double DEFAULT_HP_WATER_BOILING_POINT = 180.0;

    public enum TurnoverCurve {

        EXPONENTIAL,
        LINEAR,
        SIGMOID,
        STEP;

        public static TurnoverCurve fromString(String str) {
            if (str == null) return EXPONENTIAL;
            try {
                return valueOf(
                    str.trim()
                        .toUpperCase());
            } catch (Exception e) {
                return EXPONENTIAL;
            }
        }
    }

    public static final int DEFAULT_HATCH_CAPACITY = 8000;
    public static final TurnoverCurve DEFAULT_TURNOVER_CURVE = TurnoverCurve.SIGMOID;
    public static final double DEFAULT_TURNOVER_DELTA_T_MAX = 100.0;
    public static final double DEFAULT_TURNOVER_EXPONENT = 1.0;
    public static final int DEFAULT_COOLANT_FEED_RATE = 999999;
    public static final double DEFAULT_COOLING_HEAT_PER_LITER = 17.65625;
    public static final double DEFAULT_IC2_COOLANT_HEAT_PER_LITER = 20.0;
    public static final int DEFAULT_TICKS_PER_CYCLE = 20;

    public static int ticksPerCycle = DEFAULT_TICKS_PER_CYCLE;
    public static double tempThresholdLow = DEFAULT_TEMP_THRESHOLD_LOW;
    public static double tempThresholdHigh = DEFAULT_TEMP_THRESHOLD_HIGH;
    public static double reactivityPower = DEFAULT_REACTIVITY_POWER;
    public static double globalThermalFissionMultiplier = DEFAULT_GLOBAL_THERMAL_FISSION_MULT;
    public static double thermalFissionMultiplier = DEFAULT_GLOBAL_THERMAL_FISSION_MULT;
    public static double globalFastFissionMultiplier = DEFAULT_GLOBAL_FAST_FISSION_MULT;
    public static double fastAbsorptionDamageMultiplier = DEFAULT_FAST_ABSORPTION_DAMAGE_MULT;
    public static double thermalAbsorptionDamageMultiplier = DEFAULT_THERMAL_ABSORPTION_DAMAGE_MULT;
    public static double fissionHeatPerNeutron = DEFAULT_FISSION_HEAT_PER_NEUTRON;
    public static double hpWaterBoilingPoint = DEFAULT_HP_WATER_BOILING_POINT;

    public static int hatchCoolantCapacity = DEFAULT_HATCH_CAPACITY;
    public static TurnoverCurve turnoverCurve = DEFAULT_TURNOVER_CURVE;
    public static double turnoverDeltaTMax = DEFAULT_TURNOVER_DELTA_T_MAX;
    public static double turnoverExponent = DEFAULT_TURNOVER_EXPONENT;
    public static int coolantFeedRate = DEFAULT_COOLANT_FEED_RATE;
    public static double coolingHeatPerLiter = DEFAULT_COOLING_HEAT_PER_LITER;
    public static double ic2CoolantHeatPerLiter = DEFAULT_IC2_COOLANT_HEAT_PER_LITER;

    public static final double DEFAULT_FUEL_BURNUP_MULTIPLIER = 1.0;
    public static double fuelBurnupMultiplier = DEFAULT_FUEL_BURNUP_MULTIPLIER;

    // Fuel Archetype / Class Multipliers (Global Calibration Parameters)
    public static final double DEFAULT_DENSE_NBASE_MULT = 0.50;
    public static final double DEFAULT_DENSE_FISSION_MULT = 1.20;
    public static final double DEFAULT_EXCITED_NBASE_MULT = 1.40;
    public static final double DEFAULT_EXCITED_FISSION_MULT = 0.80;
    public static final double DEFAULT_STANDARD_NBASE_MULT = 1.00;
    public static final double DEFAULT_STANDARD_FISSION_MULT = 1.00;
    public static final double DEFAULT_BREEDER_NBASE_MULT = 1.00;
    public static final double DEFAULT_BREEDER_FISSION_MULT = 1.00;

    public static double denseNbaseMultiplier = DEFAULT_DENSE_NBASE_MULT;
    public static double denseFissionMultiplier = DEFAULT_DENSE_FISSION_MULT;
    public static double excitedNbaseMultiplier = DEFAULT_EXCITED_NBASE_MULT;
    public static double excitedFissionMultiplier = DEFAULT_EXCITED_FISSION_MULT;
    public static double standardNbaseMultiplier = DEFAULT_STANDARD_NBASE_MULT;
    public static double standardFissionMultiplier = DEFAULT_STANDARD_FISSION_MULT;
    public static double breederNbaseMultiplier = DEFAULT_BREEDER_NBASE_MULT;
    public static double breederFissionMultiplier = DEFAULT_BREEDER_FISSION_MULT;

    public static double getArchetypeNbaseMultiplier(String archetype) {
        if (archetype == null) return standardNbaseMultiplier;
        return switch (archetype.toLowerCase(java.util.Locale.ROOT)) {
            case "dense", "subcritical", "naquadah" -> denseNbaseMultiplier;
            case "excited", "driver" -> excitedNbaseMultiplier;
            case "breeder" -> breederNbaseMultiplier;
            default -> standardNbaseMultiplier;
        };
    }

    public static double getArchetypeFissionMultiplier(String archetype) {
        if (archetype == null) return standardFissionMultiplier;
        return switch (archetype.toLowerCase(java.util.Locale.ROOT)) {
            case "dense", "subcritical", "naquadah" -> denseFissionMultiplier;
            case "excited", "driver" -> excitedFissionMultiplier;
            case "breeder" -> breederFissionMultiplier;
            default -> standardFissionMultiplier;
        };
    }

    public static void setFuelBurnupMultiplier(double mult) {
        fuelBurnupMultiplier = Math.max(0.0, mult);
    }

    public static final double DEFAULT_WALL_REFLECTION_CHANCE = 0.0;
    public static double wallReflectionChance = DEFAULT_WALL_REFLECTION_CHANCE;
    public static final double DEFAULT_WALL_ABSORB_HEAT_PER_NEUTRON = 0.0;
    public static double wallAbsorbHeatPerNeutron = DEFAULT_WALL_ABSORB_HEAT_PER_NEUTRON;

    public static final double DEFAULT_BEAM_ENERGY_TO_FAST_NEUTRON_MULTIPLIER = 0.001;
    public static double beamEnergyToFastNeutronMultiplier = DEFAULT_BEAM_ENERGY_TO_FAST_NEUTRON_MULTIPLIER;

    public static void setHuPerDegree(double v) {
        huPerDegree = Math.max(0.1, v);
        HU_PER_DEGREE = huPerDegree;
    }

    public static void setGlobalThermalFissionMultiplier(double mult) {
        globalThermalFissionMultiplier = Math.max(0.0, mult);
        thermalFissionMultiplier = globalThermalFissionMultiplier;
    }

    public static void setGlobalFastFissionMultiplier(double mult) {
        globalFastFissionMultiplier = Math.max(0.0, mult);
    }

    public static void setFastAbsorptionDamageMultiplier(double mult) {
        fastAbsorptionDamageMultiplier = Math.max(0.0, mult);
    }

    public static void setThermalAbsorptionDamageMultiplier(double mult) {
        thermalAbsorptionDamageMultiplier = Math.max(0.0, mult);
    }

    public static void setBeamEnergyToFastNeutronMultiplier(double mult) {
        beamEnergyToFastNeutronMultiplier = Math.max(0.0, mult);
    }

    public static void setAmbientTemperature(double temp) {
        ambientTemp = temp;
        AMBIENT_TEMP = temp;
    }

    public static void setSimulationParameters(double low, double high, double power, double fissionMult,
        double heatPerNeutron, double hpBoil) {
        tempThresholdLow = low;
        tempThresholdHigh = high;
        reactivityPower = power;
        setGlobalThermalFissionMultiplier(fissionMult);
        fissionHeatPerNeutron = heatPerNeutron;
        hpWaterBoilingPoint = hpBoil;
    }

    public static void setExtendedParameters(int hatchCap, TurnoverCurve curve, double dtMax, double exp, int feedRate,
        double coolingHeat, double low, double high, double power, double fissionMult, double heatPerNeutron,
        double hpBoil) {
        hatchCoolantCapacity = Math.max(100, hatchCap);
        turnoverCurve = (curve != null) ? curve : DEFAULT_TURNOVER_CURVE;
        turnoverDeltaTMax = Math.max(10.0, dtMax);
        turnoverExponent = Math.max(0.1, exp);
        coolantFeedRate = Math.max(0, feedRate);
        coolingHeatPerLiter = Math.max(0.1, coolingHeat);
        setSimulationParameters(low, high, power, fissionMult, heatPerNeutron, hpBoil);
    }

    public static void setExtendedParameters(int hatchCap, TurnoverCurve curve, double dtMax, double exp, int feedRate,
        double coolingHeat, double low, double high, double power, double fissionMult, double heatPerNeutron,
        double hpBoil, double ic2Heat, double ambient) {
        setExtendedParameters(
            hatchCap,
            curve,
            dtMax,
            exp,
            feedRate,
            coolingHeat,
            low,
            high,
            power,
            fissionMult,
            heatPerNeutron,
            hpBoil);
        ic2CoolantHeatPerLiter = Math.max(0.1, ic2Heat);
        setAmbientTemperature(ambient);
    }

    public static void resetDefaultParameters() {
        setHuPerDegree(DEFAULT_HU_PER_DEGREE);
        setGlobalThermalFissionMultiplier(DEFAULT_GLOBAL_THERMAL_FISSION_MULT);
        setGlobalFastFissionMultiplier(DEFAULT_GLOBAL_FAST_FISSION_MULT);
        setFastAbsorptionDamageMultiplier(DEFAULT_FAST_ABSORPTION_DAMAGE_MULT);
        setThermalAbsorptionDamageMultiplier(DEFAULT_THERMAL_ABSORPTION_DAMAGE_MULT);
        tempThresholdLow = DEFAULT_TEMP_THRESHOLD_LOW;
        tempThresholdHigh = DEFAULT_TEMP_THRESHOLD_HIGH;
        reactivityPower = DEFAULT_REACTIVITY_POWER;
        fissionHeatPerNeutron = DEFAULT_FISSION_HEAT_PER_NEUTRON;
        hpWaterBoilingPoint = DEFAULT_HP_WATER_BOILING_POINT;
        hatchCoolantCapacity = DEFAULT_HATCH_CAPACITY;
        turnoverCurve = DEFAULT_TURNOVER_CURVE;
        turnoverDeltaTMax = DEFAULT_TURNOVER_DELTA_T_MAX;
        turnoverExponent = DEFAULT_TURNOVER_EXPONENT;
        coolantFeedRate = DEFAULT_COOLANT_FEED_RATE;
        coolingHeatPerLiter = DEFAULT_COOLING_HEAT_PER_LITER;
        fuelBurnupMultiplier = DEFAULT_FUEL_BURNUP_MULTIPLIER;
        denseNbaseMultiplier = DEFAULT_DENSE_NBASE_MULT;
        denseFissionMultiplier = DEFAULT_DENSE_FISSION_MULT;
        excitedNbaseMultiplier = DEFAULT_EXCITED_NBASE_MULT;
        excitedFissionMultiplier = DEFAULT_EXCITED_FISSION_MULT;
        standardNbaseMultiplier = DEFAULT_STANDARD_NBASE_MULT;
        standardFissionMultiplier = DEFAULT_STANDARD_FISSION_MULT;
        breederNbaseMultiplier = DEFAULT_BREEDER_NBASE_MULT;
        breederFissionMultiplier = DEFAULT_BREEDER_FISSION_MULT;
        wallReflectionChance = DEFAULT_WALL_REFLECTION_CHANCE;
        wallAbsorbHeatPerNeutron = DEFAULT_WALL_ABSORB_HEAT_PER_NEUTRON;
        ic2CoolantHeatPerLiter = DEFAULT_IC2_COOLANT_HEAT_PER_LITER;
        baseHatchConductance = DEFAULT_BASE_HATCH_CONDUCTANCE;
        setBeamEnergyToFastNeutronMultiplier(DEFAULT_BEAM_ENERGY_TO_FAST_NEUTRON_MULTIPLIER);
        setAmbientTemperature(DEFAULT_AMBIENT_TEMP);
        ticksPerCycle = DEFAULT_TICKS_PER_CYCLE;
    }

    public static void setTicksPerCycle(int ticks) {
        ticksPerCycle = Math.max(1, ticks);
    }

    public static int getTicksPerCycle() {
        return ticksPerCycle;
    }

    public static final double DEFAULT_BASE_HATCH_CONDUCTANCE = 2.0;
    public static double baseHatchConductance = DEFAULT_BASE_HATCH_CONDUCTANCE;

    public static void setBaseHatchConductance(double val) {
        baseHatchConductance = Math.max(0.01, val);
    }

    /**
     * Calculates the thermal conductance U in Hu/(t·°C) for a nuclear core hatch of the given tier.
     * Tier scaling: LV(1)=0.50x, MV(2)=1.0x, HV(3)=2.0x, EV(4)=4.0x, IV(5)=8.0x, LuV(6)=16.0x... (base-2 exponential)
     */
    public static double getHatchConductance(int tier) {
        double mult;
        if (tier <= 1) {
            mult = 0.50;
        } else {
            mult = Math.pow(2.0, Math.max(0, tier - 2));
        }
        return mult * baseHatchConductance;
    }

    /**
     * Returns the effective sink temperature (°C) for a coolant fluid.
     * IC2 coolant operates down to ambient temperature, while heavy water boils at 101.4°C and distilled water at
     * 100°C.
     */
    public static double getCoolantSinkTemperature(String fluidName, double ambientTemp) {
        if (fluidName == null) return ambientTemp;
        com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.CoolantFluidData cfd = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .findCoolantFluid(fluidName);
        if (cfd != null) {
            if (cfd.expansionRatio > 1) {
                return Math.max(ambientTemp, cfd.boilingTemperature);
            }
            return ambientTemp;
        }
        return ambientTemp;
    }

    /**
     * Analytical lumped-capacitance heat transfer model based on continuous exponential cooling.
     * Calculates the maximum heat energy (Hu) transferred from a hatch at temperature temp
     * into a coolant heat sink at sinkTemp over a 1-tick interval (dt = 1).
     *
     * @param temp       The hatch temperature in °C
     * @param sinkTemp   The coolant sink temperature in °C
     * @param tier       The tier of the nuclear core hatch (LV=1, MV=2, HV=3, EV=4, ...)
     * @param efficiency Reactor efficiency multiplier [0.0, 1.0]
     * @return Heat energy in Hu transferred this tick
     */
    public static double calculateConductiveHeatTransfer(double temp, double sinkTemp, int tier, double efficiency) {
        if (temp <= sinkTemp || efficiency <= 0.0) return 0.0;
        double deltaT = temp - sinkTemp;
        double effFactor = Math.max(0.0, Math.min(1.0, efficiency));
        double conductance = getHatchConductance(tier) * effFactor;
        double ch = HU_PER_DEGREE;
        return ch * deltaT * (1.0 - Math.exp(-conductance / ch));
    }

    public static final int PIPE_TIER_ELECTRUM = 0;
    public static final int PIPE_TIER_PLATINUM = 1;
    public static final int PIPE_TIER_OSMIUM = 2;
    public static final int PIPE_TIER_QUANTIUM = 3;
    public static final int PIPE_TIER_FLUXED_ELECTRUM = 4;
    public static final int PIPE_TIER_BLACK_PLUTONIUM = 5;

    public static String getPipeTierName(int tier) {
        return switch (tier) {
            case PIPE_TIER_ELECTRUM -> "Electrum (IC2 Coolant)";
            case PIPE_TIER_PLATINUM -> "Platinum (Distilled Water)";
            case PIPE_TIER_OSMIUM -> "Osmium (HP core hatches)";
            case PIPE_TIER_QUANTIUM -> "Quantium (Heavy Water)";
            case PIPE_TIER_FLUXED_ELECTRUM -> "Fluxed Electrum (excited fuel)";
            case PIPE_TIER_BLACK_PLUTONIUM -> "Black Plutonium (max tier / all coolants)";
            default -> "None";
        };
    }

    public static long getPipeTierVoltage(int tier) {
        return switch (tier) {
            case PIPE_TIER_ELECTRUM -> 2048L; // EV
            case PIPE_TIER_PLATINUM -> 8192L; // IV
            case PIPE_TIER_OSMIUM -> 32768L; // LuV
            case PIPE_TIER_QUANTIUM -> 131072L; // ZPM
            case PIPE_TIER_FLUXED_ELECTRUM -> 524288L; // UV
            case PIPE_TIER_BLACK_PLUTONIUM -> 2097152L; // UHV
            default -> 2048L;
        };
    }

    public static String getPipeTierVoltageName(int tier) {
        return switch (tier) {
            case PIPE_TIER_ELECTRUM -> "EV";
            case PIPE_TIER_PLATINUM -> "IV";
            case PIPE_TIER_OSMIUM -> "LuV";
            case PIPE_TIER_QUANTIUM -> "ZPM";
            case PIPE_TIER_FLUXED_ELECTRUM -> "UV";
            case PIPE_TIER_BLACK_PLUTONIUM -> "UHV";
            default -> "EV";
        };
    }

    public static double getMaxOperatingTemperature(int tier) {
        if (tier >= 0
            && tier < com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.maxOperatingTempTier.length) {
            double cfg = com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.maxOperatingTempTier[tier];
            if (cfg
                != com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.DEFAULT_MAX_OPERATING_TEMP_TIER[tier]) {
                return cfg;
            }
        }
        com.gtnewhorizons.modularnuclear.common.config.data.TierData td = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .getTier(tier);
        if (td != null) return td.maxOperatingTemp;
        if (tier >= 0
            && tier < com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.maxOperatingTempTier.length) {
            return com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.maxOperatingTempTier[tier];
        }
        return 800.0;
    }

    /**
     * Calculates the maximum average core temperature under which structural damage repair is permitted.
     * Uniformly calculated as configured percentage of the casing tier's maximum operating temperature.
     */
    public static double getRepairTemperatureThreshold(int tier) {
        if (com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.repairTempThresholdRatio != 0.10) {
            return com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.repairTempThresholdRatio
                * getMaxOperatingTemperature(tier);
        }
        com.gtnewhorizons.modularnuclear.common.config.data.TierData td = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .getTier(tier);
        if (td != null) return td.repairTempThreshold;
        return com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.repairTempThresholdRatio
            * getMaxOperatingTemperature(tier);
    }

    public static double getCoolantBoilingThreshold(String fluidName) {
        if (fluidName == null) return Double.POSITIVE_INFINITY;
        com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.CoolantFluidData cfd = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .findCoolantFluid(fluidName);
        if (cfd != null) {
            return cfd.explosionTemperature;
        }
        return Double.POSITIVE_INFINITY;
    }

    public static int getRequiredFluidTier(String fluidName) {
        if (fluidName == null) return -1;
        com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.CoolantFluidData cfd = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .findCoolantFluid(fluidName);
        if (cfd != null) {
            return cfd.requiredTier;
        }
        com.gtnewhorizons.modularnuclear.common.config.data.FuelData fd = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .findFuel(fluidName);
        if (fd != null && fd.isLiquid) {
            return fd.requiredTier;
        }
        return -1;
    }

    public static boolean isFluidAllowed(String fluidName, int pipeTier) {
        if (fluidName == null) return false;
        int reqTier = getRequiredFluidTier(fluidName);
        if (reqTier < 0) return false;
        com.gtnewhorizons.modularnuclear.common.config.data.TierData td = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .getTier(pipeTier);
        if (td != null && !td.allowedCoolants.isEmpty()) {
            if (td.allowedCoolants.contains("*") || com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
                .matchesFluid(fluidName, td.allowedCoolants)) {
                return true;
            }
        }
        return pipeTier < 0 || pipeTier >= reqTier;
    }

    public static double getCoolingOperatingThreshold(String fluidName) {
        return getCoolantSinkTemperature(fluidName, ambientTemp);
    }

    public static double getCoolingOperatingThreshold(String fluidName, double ambient) {
        return getCoolantSinkTemperature(fluidName, ambient);
    }

    private static final int[] dX = { 1, 0, -1, 0, 1, -1, 1, -1 };
    private static final int[] dY = { 0, 1, 0, -1, 1, 1, -1, -1 };
    private static final double[] DIR_WEIGHTS = { 1.0 / 6.0, 1.0 / 6.0, 1.0 / 6.0, 1.0 / 6.0, // 4 Cardinals (weight 1/6
                                                                                              // each)
        1.0 / 12.0, 1.0 / 12.0, 1.0 / 12.0, 1.0 / 12.0 // 4 Diagonals (weight 1/12 each)
    };
    private static final Random RAND = new Random();

    public static void setRandomSeed(long seed) {
        RAND.setSeed(seed);
    }

    public static class SimulationResult {

        public int totalNeutronsGenerated = 0;
        public int fastNeutronsAbsorbed = 0;
        public int thermalNeutronsAbsorbed = 0;
        public int wallNeutronsReflected = 0;
        public int wallNeutronsAbsorbed = 0;
        public double wallHeatPool = 0;
        public int neutronsEscaped = 0;
        public double maxTemperature = AMBIENT_TEMP;
        public double averageTemperature = AMBIENT_TEMP;
        public double averageReactivity = 0.0;
        public double totalHeatHU = 0;
    }

    /**
     * Formats neutron flux into realistic physical units (multiples of 10¹³ neutrons/cm²/sec).
     * Follows the US Nuclear Regulatory Commission (NRC) definition:
     * Rate of flow of neutrons per unit area per second (nv = n·v), measured in neutrons/cm²/sec.
     */
    public static String formatNeutronFlux(double neutronsProduced) {
        if (neutronsProduced <= 0) {
            return "0 neutrons/cm²/sec";
        }
        double physicalFlux = neutronsProduced * 1.0e13;
        return String.format(java.util.Locale.US, "%.2e neutrons/cm²/sec", physicalFlux)
            .replace("+0", "")
            .replace("+", "");
    }

    public static String formatNeutronFlux(int neutronsProduced) {
        return formatNeutronFlux((double) neutronsProduced);
    }

    /**
     * Formats cumulative neutron fluence into physical units (neutrons/cm²).
     * Follows the US Nuclear Regulatory Commission (NRC) definition:
     * Time-integrated neutron flux (∫ φ dt), measured in neutrons/cm².
     */
    public static String formatNeutronFluence(double cumulativeNeutrons) {
        if (cumulativeNeutrons <= 0) {
            return "0 neutrons/cm²";
        }
        double physicalFluence = cumulativeNeutrons * 1.0e13;
        return String.format(java.util.Locale.US, "%.2e neutrons/cm²", physicalFluence)
            .replace("+0", "")
            .replace("+", "");
    }

    public static String formatNeutronFluence(long cumulativeNeutrons) {
        return formatNeutronFluence((double) cumulativeNeutrons);
    }

    /**
     * Executes one reactor simulation tick over the 2D grid with full (100%) maintenance efficiency and default ambient
     * temperature.
     */
    public static SimulationResult simulate(INuclearTile[][] grid, int sizeX, int sizeY) {
        return simulate(grid, sizeX, sizeY, 1.0, ambientTemp);
    }

    /**
     * Executes one reactor simulation tick over the 2D grid with specified maintenance efficiency factor [0.0, 1.0] and
     * default ambient temperature.
     */
    public static SimulationResult simulate(INuclearTile[][] grid, int sizeX, int sizeY, double maintenanceEfficiency) {
        return simulate(grid, sizeX, sizeY, maintenanceEfficiency, ambientTemp);
    }

    /**
     * Executes one reactor simulation tick over the 2D grid with specified maintenance efficiency and biome ambient
     * temperature.
     */
    public static SimulationResult simulate(INuclearTile[][] grid, int sizeX, int sizeY, double maintenanceEfficiency,
        double ambient) {
        SimulationResult result = new SimulationResult();
        if (grid == null || sizeX <= 0 || sizeY <= 0) return result;

        // --- PASS 1: FROZEN INITIAL STATE SNAPSHOT & SYNCHRONIZED NEUTRON EMISSION ---
        double sumTemp = 0;
        int activeTileCount = 0;
        double sumFuelReactivity = 0;
        int fuelTileCount = 0;

        double[][] initialTemp = new double[sizeX][sizeY];
        int[][] emittedNeutrons = new int[sizeX][sizeY];
        double[][] efficiency = new double[sizeX][sizeY];
        double[][] pendingHeat = new double[sizeX][sizeY];

        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                INuclearTile tile = grid[x][y];
                if (tile != null) {
                    activeTileCount++;
                    double temp = tile.getTemperature();
                    initialTemp[x][y] = temp;
                    if (temp > result.maxTemperature) {
                        result.maxTemperature = temp;
                    }
                    sumTemp += temp;
                    if (tile.isFuel()) {
                        double eff = calculateEfficiency(tile, temp);
                        efficiency[x][y] = eff;
                        sumFuelReactivity += eff;
                        fuelTileCount++;

                        int produced = tile.generateNeutrons(eff);
                        if (produced > 0) {
                            emittedNeutrons[x][y] = produced;
                            result.totalNeutronsGenerated += produced;
                            // Prompt fission heat queued for atomic deposit in Pass 3
                            pendingHeat[x][y] += produced * fissionHeatPerNeutron;
                        }
                    } else if (tile.isNeutronSource()) {
                        efficiency[x][y] = Math.max(0.0, Math.min(1.0, maintenanceEfficiency));
                        int produced = tile.generateNeutrons(1.0);
                        if (produced > 0) {
                            emittedNeutrons[x][y] = produced;
                            result.totalNeutronsGenerated += produced;
                            // External neutron injection produces no prompt fission heat
                        }
                    } else {
                        efficiency[x][y] = Math.max(0.0, Math.min(1.0, maintenanceEfficiency));
                    }
                }
            }
        }
        if (activeTileCount > 0) {
            result.averageTemperature = sumTemp / activeTileCount;
        }
        result.averageReactivity = (fuelTileCount > 0) ? (sumFuelReactivity / fuelTileCount) : 0.0;

        // --- PASS 2: HYBRID NEUTRON MODEL (MI FAST RAYS + ISOTROPIC THERMAL STENCIL) ---
        // Fast neutrons emit as discrete directional rays with random directions based on fuel type:
        // single rods emit 1 ray, dual rods emit 2 rays, quad rods & fluid fuels emit 4 rays.
        // External neutron sources emit 4 discrete directional rays into the 4 cardinal directions.
        // Fast rays undergo Monte Carlo random-walk scattering and reflection, or get absorbed.
        // When fast neutrons moderate in coolant/moderators, they convert into a thermal flux pool.
        // Thermal neutrons then diffuse omnidirectionally using the 9-point isotropic stencil.

        final int MAX_FAST_STEPS = (sizeX + sizeY) * 2;
        final int MAX_THERMAL_STEPS = Math.max(12, (sizeX + sizeY) * 2);

        double[][] thermalPool = new double[sizeX][sizeY];
        double[][] tileFluxFast = new double[sizeX][sizeY];
        double[][] tileFluxThermal = new double[sizeX][sizeY];
        double[][] tileAbsThermal = new double[sizeX][sizeY];
        double[][] tileScatThermal = new double[sizeX][sizeY];

        // Part A: Fast Neutron Emission & Monte Carlo Propagation
        for (int i = 0; i < sizeX; i++) {
            for (int j = 0; j < sizeY; j++) {
                int N = emittedNeutrons[i][j];
                if (N <= 0) continue;

                INuclearTile sourceTile = grid[i][j];
                boolean isExternalSource = sourceTile != null && sourceTile.isNeutronSource();
                int rayCount = isExternalSource ? 4 : ((sourceTile != null) ? sourceTile.getNeutronEmissionCount() : 1);
                if (rayCount <= 0) continue;
                int nPerRay = N / rayCount;
                int rem = N % rayCount;

                for (int r = 0; r < rayCount; r++) {
                    int rayNeutrons = nPerRay + (r < rem ? 1 : 0);
                    if (rayNeutrons <= 0) continue;

                    int dir = isExternalSource ? r : RAND.nextInt(4);
                    int posX = i;
                    int posY = j;
                    int steps = 0;
                    boolean terminated = false;

                    while (steps < MAX_FAST_STEPS) {
                        steps++;
                        posX += dX[dir];
                        posY += dY[dir];

                        boolean isOutOfBounds = (posX < 0 || posX >= sizeX || posY < 0 || posY >= sizeY);
                        boolean isNullCell = !isOutOfBounds && (grid[posX][posY] == null);

                        if (isOutOfBounds) {
                            if (wallReflectionChance > 0 && RAND.nextDouble() < wallReflectionChance) {
                                result.wallNeutronsReflected += rayNeutrons;
                                dir = (dir + 2) % 4;
                                posX += dX[dir];
                                posY += dY[dir];
                                continue;
                            } else {
                                result.neutronsEscaped += rayNeutrons;
                                terminated = true;
                                break;
                            }
                        }

                        if (isNullCell) {
                            result.neutronsEscaped += rayNeutrons;
                            terminated = true;
                            break;
                        }

                        INuclearTile hitTile = grid[posX][posY];
                        hitTile.addNeutronFlux(NeutronType.FAST, rayNeutrons);
                        tileFluxFast[posX][posY] += rayNeutrons;

                        double pAbs = hitTile.getAbsorptionProbability(NeutronType.FAST);
                        double pScatter = hitTile.getScatteringProbability(NeutronType.FAST);
                        double pTotal = Math.min(1.0, pAbs + pScatter);

                        if (RAND.nextDouble() < pTotal) {
                            double pAbsRel = (pTotal > 0) ? (pAbs / pTotal) : 0.0;
                            if (RAND.nextDouble() < pAbsRel) {
                                hitTile.onNeutronAbsorbed(NeutronType.FAST, rayNeutrons);
                                result.fastNeutronsAbsorbed += rayNeutrons;
                                if (hitTile.getInsulationDampening() < 1.0) {
                                    pendingHeat[posX][posY] += rayNeutrons * HU_FOR_FAST_NEUTRON;
                                }
                                if (hitTile.getModeratorData() != null
                                    && hitTile.getModeratorData().wignerEnergyPerFast > 0.0) {
                                    hitTile
                                        .addWignerEnergy(rayNeutrons * hitTile.getModeratorData().wignerEnergyPerFast);
                                }
                                terminated = true;
                                break;
                            } else {
                                hitTile.onNeutronScattered(NeutronType.FAST, rayNeutrons);
                                if (pScatter >= 0.90 && pAbs <= 0.05) {
                                    dir = (dir + 2) % 4;
                                } else {
                                    dir = RAND.nextInt(4);
                                }

                                double pMod = hitTile.getModerationProbability();
                                if (RAND.nextDouble() < pMod) {
                                    if (hitTile.getInsulationDampening() < 1.0) {
                                        pendingHeat[posX][posY] += rayNeutrons * HU_FOR_FAST_NEUTRON;
                                    }
                                    if (hitTile.getModeratorData() != null
                                        && hitTile.getModeratorData().wignerEnergyPerFast > 0.0) {
                                        hitTile.addWignerEnergy(
                                            rayNeutrons * hitTile.getModeratorData().wignerEnergyPerFast);
                                    }
                                    thermalPool[posX][posY] += rayNeutrons;
                                    terminated = true;
                                    break;
                                }
                            }
                        }
                    }

                    if (!terminated) {
                        result.neutronsEscaped += rayNeutrons;
                    }
                }
            }
        }

        // Part B: Thermal Neutron 9-Point Isotropic Diffusion Stencil
        double[][] activeThermal = thermalPool;

        for (int step = 0; step < MAX_THERMAL_STEPS; step++) {
            double totalActive = 0.0;
            for (int x = 0; x < sizeX; x++) {
                for (int y = 0; y < sizeY; y++) {
                    totalActive += activeThermal[x][y];
                }
            }
            if (totalActive < 0.001) break;

            double[][] nextThermal = new double[sizeX][sizeY];

            for (int x = 0; x < sizeX; x++) {
                for (int y = 0; y < sizeY; y++) {
                    double arrivingT = activeThermal[x][y];
                    if (arrivingT <= 0.0001) continue;

                    INuclearTile hitTile = grid[x][y];
                    if (hitTile == null) {
                        result.neutronsEscaped += (int) Math.round(arrivingT);
                        continue;
                    }

                    tileFluxThermal[x][y] += arrivingT;

                    double pAbsT = hitTile.getAbsorptionProbability(NeutronType.THERMAL);
                    double absT = arrivingT * pAbsT;
                    tileAbsThermal[x][y] += absT;

                    if (hitTile.getInsulationDampening() < 1.0) {
                        if (absT > 0) {
                            if (hitTile.isFuel()) {
                                double rodFraction = hitTile.getControlRodAbsorptionFraction(NeutronType.THERMAL);
                                double absRod = Math.min(absT, Math.max(0.0, absT * rodFraction));
                                double absFuel = Math.max(0.0, absT - absRod);
                                pendingHeat[x][y] += absFuel * fissionHeatPerNeutron * 1.25
                                    + absRod * (HU_FOR_FAST_NEUTRON * 0.5);
                            } else {
                                pendingHeat[x][y] += absT * (HU_FOR_FAST_NEUTRON * 0.5);
                            }
                        }
                    }

                    double survT = Math.max(0.0, arrivingT - absT);
                    double pScatT = Math.max(0.0, Math.min(1.0, hitTile.getScatteringProbability(NeutronType.THERMAL)));
                    double scatterT = survT * pScatT;
                    double unscatteredT = Math.max(0.0, survT - scatterT);
                    result.neutronsEscaped += (int) Math.round(unscatteredT);
                    tileScatThermal[x][y] += scatterT;

                    for (int d = 0; d < 8; d++) {
                        double w = DIR_WEIGHTS[d];
                        double outT = scatterT * w;
                        int nx = x + dX[d];
                        int ny = y + dY[d];

                        boolean isOutOfBounds = (nx < 0 || nx >= sizeX || ny < 0 || ny >= sizeY);
                        boolean isNullCell = !isOutOfBounds && (grid[nx][ny] == null);

                        if (isOutOfBounds) {
                            if (wallReflectionChance > 0) {
                                double reflT = outT * wallReflectionChance;
                                nextThermal[x][y] += reflT;
                                result.wallNeutronsReflected += (int) Math.round(reflT);
                                result.neutronsEscaped += (int) Math.round(outT * (1.0 - wallReflectionChance));
                            } else {
                                result.neutronsEscaped += (int) Math.round(outT);
                            }
                        } else if (isNullCell) {
                            result.neutronsEscaped += (int) Math.round(outT);
                        } else {
                            nextThermal[nx][ny] += outT;
                        }
                    }
                }
            }

            activeThermal = nextThermal;
        }

        // Account for any remaining residual thermal flux
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                double residual = activeThermal[x][y];
                if (residual > 0.001) {
                    result.neutronsEscaped += (int) Math.round(residual);
                }
            }
        }

        // Execute thermal callbacks
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                INuclearTile tile = grid[x][y];
                if (tile == null) continue;

                int fT = (int) Math.round(tileFluxThermal[x][y]);
                if (fT > 0) tile.addNeutronFlux(NeutronType.THERMAL, fT);

                int aT = (int) Math.round(tileAbsThermal[x][y]);
                if (aT > 0) {
                    tile.onNeutronAbsorbed(NeutronType.THERMAL, aT);
                    result.thermalNeutronsAbsorbed += aT;
                }

                int sT = (int) Math.round(tileScatThermal[x][y]);
                if (sT > 0) {
                    tile.onNeutronScattered(NeutronType.THERMAL, sT);
                }
            }
        }

        // --- PASS 3: ATOMIC NUCLEAR HEAT DEPOSITION ---
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                INuclearTile tile = grid[x][y];
                if (tile != null) {
                    double wignerHeat = tile.releaseWignerEnergy();
                    if (wignerHeat > 0.0) {
                        pendingHeat[x][y] += wignerHeat;
                    }
                    if (pendingHeat[x][y] > 0.0) {
                        tile.addHeat(pendingHeat[x][y]);
                        result.totalHeatHU += pendingHeat[x][y];
                    }
                }
            }
        }

        // --- PASS 4: MULTI-SUBSTEP DISCRETE HEAT CONDUCTION & BOUNDARY LOSS ---
        final int SUBSTEPS = 5;
        double[][] deltaTemp = new double[sizeX][sizeY];

        for (int sub = 0; sub < SUBSTEPS; sub++) {
            for (int x = 0; x < sizeX; x++) {
                for (int y = 0; y < sizeY; y++) {
                    deltaTemp[x][y] = 0;
                }
            }

            for (int x = 0; x < sizeX; x++) {
                for (int y = 0; y < sizeY; y++) {
                    INuclearTile tileA = grid[x][y];
                    if (tileA == null) continue;

                    double tempA = tileA.getTemperature();
                    double coeffA = Math.max(BASE_HEAT_CONDUCTION, tileA.getHeatTransferCoeff());

                    for (int k = 0; k < 4; k++) {
                        int nx = x + dX[k];
                        int ny = y + dY[k];

                        if (nx >= 0 && nx < sizeX && ny >= 0 && ny < sizeY && grid[nx][ny] != null) {
                            INuclearTile tileB = grid[nx][ny];
                            double tempB = tileB.getTemperature();
                            double coeffB = Math.max(BASE_HEAT_CONDUCTION, tileB.getHeatTransferCoeff());
                            double transferCoeff = 0.5 * (coeffA + coeffB) / SUBSTEPS;
                            if (tempA > tempB) {
                                double dampening = Math
                                    .max(tileA.getInsulationDampening(), tileB.getInsulationDampening());
                                double flow = (tempA - tempB) * transferCoeff
                                    * (1.0 - Math.min(1.0, Math.max(0.0, dampening)));
                                deltaTemp[x][y] -= flow;
                                deltaTemp[nx][ny] += flow;
                            }
                        } else {
                            // Heat loss to empty space / outer walls at ambient temperature
                            double dampening = tileA.getInsulationDampening();
                            double loss = (tempA - ambient) * (coeffA / (2.0 * SUBSTEPS))
                                * (1.0 - Math.min(1.0, Math.max(0.0, dampening)));
                            deltaTemp[x][y] -= loss;
                        }
                    }
                }
            }

            for (int x = 0; x < sizeX; x++) {
                for (int y = 0; y < sizeY; y++) {
                    INuclearTile tile = grid[x][y];
                    if (tile != null) {
                        tile.setTemperature(Math.max(-273.15, tile.getTemperature() + deltaTemp[x][y]));
                    }
                }
            }
        }

        // --- PASS 4.5: ACTIVE HEAT VENT & HEAT EXCHANGER PASS ---
        processActiveVentsAndExchangers(grid, sizeX, sizeY, ambient);

        // --- PASS 5: TILE NUCLEAR UPDATE (BOILING, COOLING, DURABILITY, TRANSMUTATION) ---
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                INuclearTile tile = grid[x][y];
                if (tile != null) {
                    tile.nuclearTick(efficiency[x][y]);
                }
            }
        }

        // --- PASS 6: WALL HEAT DISTRIBUTION ---
        result.totalHeatHU += result.wallHeatPool;
        if (activeTileCount > 0 && result.wallHeatPool > 0) {
            double heatPerCell = result.wallHeatPool / activeTileCount;
            for (int x = 0; x < sizeX; x++) {
                for (int y = 0; y < sizeY; y++) {
                    INuclearTile tile = grid[x][y];
                    if (tile != null) {
                        tile.addHeat(heatPerCell);
                    }
                }
            }
        }

        // --- PASS 7: END-OF-TICK TELEMETRY & METRICS ---
        result.maxTemperature = ambient;
        result.averageTemperature = ambient;
        sumTemp = 0;
        sumFuelReactivity = 0;
        fuelTileCount = 0;
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                INuclearTile tile = grid[x][y];
                if (tile != null) {
                    double temp = tile.getTemperature();
                    if (temp > result.maxTemperature) {
                        result.maxTemperature = temp;
                    }
                    sumTemp += temp;
                    if (tile.isFuel()) {
                        sumFuelReactivity += calculateEfficiency(tile, temp);
                        fuelTileCount++;
                    }
                }
            }
        }
        if (activeTileCount > 0) {
            result.averageTemperature = sumTemp / activeTileCount;
        }
        result.averageReactivity = (fuelTileCount > 0) ? (sumFuelReactivity / fuelTileCount) : 0.0;

        return result;
    }

    /**
     * Checks if (x,y) in an N x N grid is a cut corner (null cell).
     * Tier 1 (5x5): depth 1 (4 corners cut)
     * Tier 2 (9x9): depth 2 (12 corners cut)
     * Tier 3 (13x13): depth 3 (24 corners cut)
     */
    public static boolean isCornerNullCell(int x, int y, int sizeX, int sizeY) {
        if (sizeX != sizeY) return false;
        int n = sizeX;
        int cutDepth = Math.max(1, (n - 5) / 4 + 1);
        int dX = Math.min(x, n - 1 - x);
        int dY = Math.min(y, n - 1 - y);
        return (dX + dY) < cutDepth;
    }

    /**
     * Reactivity efficiency curve for an INuclearTile: handles custom fuel mixes and standard fuels.
     */
    public static double calculateEfficiency(INuclearTile tile, double avgTemp) {
        if (tile != null) {
            CustomFuelMix mix = tile.getCustomFuelMix();
            if (mix != null) {
                return mix.calculateReactivity(avgTemp);
            }
            com.gtnewhorizons.modularnuclear.common.config.data.FuelData fd = tile.getFuelData();
            if (fd != null) {
                return fd.calculateReactivity(avgTemp);
            }
            return calculateEfficiency(tile.getFuelType(), avgTemp);
        }
        return calculateEfficiency(NuclearFuelType.URANIUM, avgTemp);
    }

    /**
     * Reactivity efficiency curve: fuel-specific curve ramping up from 50% at 0 °C to 100% at peak temp,
     * decaying to 5% at floor temp, and retaining 5% reactivity for all higher temperatures.
     */
    public static double calculateEfficiency(NuclearFuelType fuel, double avgTemp) {
        if (fuel != null) {
            return fuel.calculateReactivity(avgTemp);
        }
        return NuclearFuelType.URANIUM.calculateReactivity(avgTemp);
    }

    /**
     * Legacy/default reactivity curve fallback using standard Uranium baseline.
     */
    public static double calculateEfficiency(double avgTemp) {
        return calculateEfficiency(NuclearFuelType.URANIUM, avgTemp);
    }

    /**
     * Executes active heat vent cooling and active heat exchanger equalization over the 2D grid.
     * Heat vents run first, followed by heat exchangers in randomized order to prevent directional bias.
     */
    public static void processActiveVentsAndExchangers(INuclearTile[][] grid, int sizeX, int sizeY, double ambient) {
        if (grid == null || sizeX <= 0 || sizeY <= 0) return;

        // 1. Process Heat Vents FIRST
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                INuclearTile tile = grid[x][y];
                if (tile != null && tile.isHeatVent()) {
                    processHeatVent(tile, ambient);
                }
            }
        }

        // 2. Process Heat Exchangers in RANDOMIZED order each tick to prevent directional bias
        List<int[]> exchangers = new ArrayList<>();
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                INuclearTile tile = grid[x][y];
                if (tile != null && tile.isHeatExchanger()) {
                    exchangers.add(new int[] { x, y });
                }
            }
        }
        if (!exchangers.isEmpty()) {
            Collections.shuffle(exchangers);
            for (int[] pos : exchangers) {
                processHeatExchanger(grid, pos[0], pos[1], sizeX, sizeY);
            }
        }
    }

    /**
     * Processes an active heat vent on its own cell, equalizing cell temperature towards ambient.
     * Consumes 1 EU per 4 HU removed or transported.
     */
    public static void processHeatVent(INuclearTile tile, double ambient) {
        com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.HeatVentData vent = tile.getHeatVentData();
        if (vent == null) return;
        double maxHU = vent.maxHUt > 0.0 ? vent.maxHUt : 512.0;
        long maxEU = vent.maxEUt > 0 ? vent.maxEUt : (long) Math.ceil(maxHU / 4.0);

        double curTemp = tile.getTemperature();
        double tempDiff = curTemp - ambient;
        if (Math.abs(tempDiff) < 1e-4) return;

        double huNeeded = Math.abs(tempDiff) * HU_PER_DEGREE;
        double huToMove = Math.min(huNeeded, maxHU);
        if (huToMove <= 0.0) return;

        long euNeeded = (long) Math.ceil(huToMove / 4.0);
        if (euNeeded > maxEU) {
            euNeeded = maxEU;
            huToMove = euNeeded * 4.0;
        }

        // Draws from energy hatch buffers. If insufficient EU, component stops work for that tick.
        if (!tile.drainEnergy(euNeeded)) {
            return;
        }

        double deltaTemp = huToMove / HU_PER_DEGREE;
        if (tempDiff > 0) {
            tile.setTemperature(Math.max(ambient, curTemp - deltaTemp));
        } else {
            tile.setTemperature(Math.min(ambient, curTemp + deltaTemp));
        }
    }

    /**
     * Processes an active heat exchanger, equalizing temperature between own cell and all cardinally adjacent cells.
     * Consumes 1 EU per 4 HU transported.
     */
    public static void processHeatExchanger(INuclearTile[][] grid, int x, int y, int sizeX, int sizeY) {
        INuclearTile center = grid[x][y];
        if (center == null) return;
        com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.HeatExchangerData hex = center
            .getHeatExchangerData();
        if (hex == null) return;
        double maxHU = hex.maxHUt > 0.0 ? hex.maxHUt : 1024.0;
        long maxEU = hex.maxEUt > 0 ? hex.maxEUt : (long) Math.ceil(maxHU / 4.0);

        // Gather center and up to 4 cardinally adjacent neighbor cells
        int[] dX = { 0, 0, -1, 1 };
        int[] dY = { -1, 1, 0, 0 };
        List<INuclearTile> cluster = new ArrayList<>(5);
        cluster.add(center);

        for (int i = 0; i < 4; i++) {
            int nx = x + dX[i];
            int ny = y + dY[i];
            if (nx >= 0 && nx < sizeX && ny >= 0 && ny < sizeY && grid[nx][ny] != null) {
                cluster.add(grid[nx][ny]);
            }
        }

        if (cluster.size() <= 1) return;

        // Equalize temperature between own cell and all cardinally adjacent cells
        double sumTemp = 0.0;
        for (INuclearTile t : cluster) {
            sumTemp += t.getTemperature();
        }
        double targetTemp = sumTemp / cluster.size();

        double totalHuTransported = 0.0;
        for (INuclearTile t : cluster) {
            double diff = t.getTemperature() - targetTemp;
            if (diff > 0) {
                totalHuTransported += diff * HU_PER_DEGREE;
            }
        }

        if (totalHuTransported < 1e-4) return;

        double scale = 1.0;
        if (totalHuTransported > maxHU) {
            scale = maxHU / totalHuTransported;
        }

        double huActuallyTransported = totalHuTransported * scale;
        long euNeeded = (long) Math.ceil(huActuallyTransported / 4.0);
        if (euNeeded > maxEU) {
            euNeeded = maxEU;
            huActuallyTransported = euNeeded * 4.0;
            scale = huActuallyTransported / totalHuTransported;
        }

        // Draws from energy hatch buffers. If insufficient EU, component stops work for that tick.
        if (!center.drainEnergy(euNeeded)) {
            return;
        }

        for (INuclearTile t : cluster) {
            double diff = targetTemp - t.getTemperature();
            t.setTemperature(t.getTemperature() + diff * scale);
        }
    }
}
