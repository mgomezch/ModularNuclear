package com.gtnewhorizons.modularnuclear.common.nuclear;

import java.util.ArrayList;
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
    public static final int DEFAULT_TICKS_PER_CYCLE = 5;

    public static int ticksPerCycle = DEFAULT_TICKS_PER_CYCLE;
    public static com.gtnewhorizons.modularnuclear.common.config.data.INuclearConfigProvider configProvider;

    public static void setConfigProvider(
        com.gtnewhorizons.modularnuclear.common.config.data.INuclearConfigProvider provider) {
        configProvider = provider;
    }

    public static com.gtnewhorizons.modularnuclear.common.config.data.INuclearConfigProvider getConfigProvider() {
        return configProvider;
    }

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
        ic2CoolantHeatPerLiter = DEFAULT_IC2_COOLANT_HEAT_PER_LITER;
        baseHatchConductance = DEFAULT_BASE_HATCH_CONDUCTANCE;
        gridConductionMultiplier = DEFAULT_GRID_CONDUCTION_MULTIPLIER;
        setBeamEnergyToFastNeutronMultiplier(DEFAULT_BEAM_ENERGY_TO_FAST_NEUTRON_MULTIPLIER);
        setAmbientTemperature(DEFAULT_AMBIENT_TEMP);
        ticksPerCycle = DEFAULT_TICKS_PER_CYCLE;
        // physics.yaml (and the fuel/component records) take precedence over the constants above.
        com.gtnewhorizons.modularnuclear.common.config.ReactorConfigData.loadDefaults();
    }

    public static void setTicksPerCycle(int ticks) {
        ticksPerCycle = Math.max(1, ticks);
    }

    public static int getTicksPerCycle() {
        return ticksPerCycle;
    }

    public static final double DEFAULT_GRID_CONDUCTION_MULTIPLIER = 1.0;
    /** Scales cell-to-cell heat conduction (not boundary loss or hatch-to-coolant transfer). */
    public static double gridConductionMultiplier = DEFAULT_GRID_CONDUCTION_MULTIPLIER;

    public static void setGridConductionMultiplier(double mult) {
        gridConductionMultiplier = Math.max(0.0, mult);
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
        if (configProvider != null) {
            com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.CoolantFluidData cfd = configProvider
                .findCoolantFluid(fluidName);
            if (cfd != null) {
                if (cfd.expansionRatio > 1) {
                    return Math.max(ambientTemp, cfd.boilingTemperature);
                }
                return ambientTemp;
            }
        }
        String name = fluidName.toLowerCase();
        if (name.contains("coolant")) {
            return ambientTemp;
        }
        if (name.contains("heavywater")) {
            return Math.max(ambientTemp, 101.4);
        }
        if (name.contains("distilledwater")) {
            return Math.max(ambientTemp, 100.0);
        }
        return ambientTemp;
    }

    /**
     * Analytical lumped-capacitance heat transfer model based on continuous exponential cooling.
     * Calculates the maximum heat energy (Hu) transferred from a hatch at temperature temp
     * into a coolant heat sink at sinkTemp over the default ticksPerCycle interval.
     *
     * @param temp       The hatch temperature in °C
     * @param sinkTemp   The coolant sink temperature in °C
     * @param tier       The tier of the nuclear core hatch (LV=1, MV=2, HV=3, EV=4, ...)
     * @param efficiency Reactor efficiency multiplier [0.0, 1.0]
     * @return Heat energy in Hu transferred this cycle
     */
    public static double calculateConductiveHeatTransfer(double temp, double sinkTemp, int tier, double efficiency) {
        return calculateConductiveHeatTransfer(temp, sinkTemp, tier, efficiency, ticksPerCycle);
    }

    /**
     * Analytical lumped-capacitance heat transfer model based on continuous exponential cooling.
     * Calculates the maximum heat energy (Hu) transferred from a hatch at temperature temp
     * into a coolant heat sink at sinkTemp over a specified interval of ticks (dt = ticks / 20.0).
     *
     * @param temp       The hatch temperature in °C
     * @param sinkTemp   The coolant sink temperature in °C
     * @param tier       The tier of the nuclear core hatch (LV=1, MV=2, HV=3, EV=4, ...)
     * @param efficiency Reactor efficiency multiplier [0.0, 1.0]
     * @param ticks      The duration of the cycle in world ticks
     * @return Heat energy in Hu transferred over the specified duration
     */
    public static double calculateConductiveHeatTransfer(double temp, double sinkTemp, int tier, double efficiency,
        int ticks) {
        if (temp <= sinkTemp || efficiency <= 0.0) return 0.0;
        double deltaT = temp - sinkTemp;
        double effFactor = Math.max(0.0, Math.min(1.0, efficiency));
        double conductance = getHatchConductance(tier) * effFactor;
        double ch = HU_PER_DEGREE;
        double dt = (double) Math.max(1, ticks) / 20.0;
        return ch * deltaT * (1.0 - Math.exp(-conductance * dt / ch));
    }

    public static final int PIPE_TIER_ELECTRUM = 0;
    public static final int PIPE_TIER_PLATINUM = 1;
    public static final int PIPE_TIER_OSMIUM = 2;
    public static final int PIPE_TIER_QUANTIUM = 3;
    public static final int PIPE_TIER_FLUXED_ELECTRUM = 4;
    public static final int PIPE_TIER_BLACK_PLUTONIUM = 5;

    public static String getPipeTierName(int tier) {
        return switch (tier) {
            case PIPE_TIER_ELECTRUM -> "Electrum (IC2 coolant)";
            case PIPE_TIER_PLATINUM -> "Platinum (distilled water)";
            case PIPE_TIER_OSMIUM -> "Osmium (HP core hatches)";
            case PIPE_TIER_QUANTIUM -> "Quantium (heavy water)";
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
        if (configProvider != null) {
            com.gtnewhorizons.modularnuclear.common.config.data.TierData td = configProvider.getTier(tier);
            if (td != null) return td.maxOperatingTemp;
        }
        if (tier >= 0
            && tier < com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.maxOperatingTempTier.length) {
            return com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.maxOperatingTempTier[tier];
        }
        return 800.0;
    }

    public static boolean allowsCoolantLoops(int tier) {
        if (configProvider != null) {
            com.gtnewhorizons.modularnuclear.common.config.data.TierData td = configProvider.getTier(tier);
            if (td != null) return td.allowsCoolantLoops;
        }
        return tier >= PIPE_TIER_OSMIUM;
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
        if (configProvider != null) {
            com.gtnewhorizons.modularnuclear.common.config.data.TierData td = configProvider.getTier(tier);
            if (td != null) return td.repairTempThreshold;
        }
        return com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.repairTempThresholdRatio
            * getMaxOperatingTemperature(tier);
    }

    public static double getCoolantBoilingThreshold(String fluidName) {
        if (fluidName == null) return Double.POSITIVE_INFINITY;
        if (configProvider != null) {
            com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.CoolantFluidData cfd = configProvider
                .findCoolantFluid(fluidName);
            if (cfd != null) {
                return cfd.explosionTemperature;
            }
        }
        String name = fluidName.toLowerCase();
        if (name.contains("coolant")) return Double.POSITIVE_INFINITY;
        if (name.contains("heavywater")) return 101.4;
        return 100.0;
    }

    public static int getRequiredFluidTier(String fluidName) {
        if (fluidName == null) return -1;
        if (configProvider != null) {
            com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.CoolantFluidData cfd = configProvider
                .findCoolantFluid(fluidName);
            if (cfd != null) {
                return cfd.requiredTier;
            }
            com.gtnewhorizons.modularnuclear.common.config.data.FuelData fd = configProvider.findFuel(fluidName);
            if (fd != null && fd.isLiquid) {
                return fd.requiredTier;
            }
            return -1;
        }
        String name = fluidName.toLowerCase();
        if (name.equals("water")) return -1;
        if (name.contains("naquadah")) return -1;
        if (name.contains("coolant") && !name.contains("hot")) return PIPE_TIER_ELECTRUM;
        if (name.contains("distilledwater")) return PIPE_TIER_PLATINUM;
        if (name.contains("heavywater")) return PIPE_TIER_QUANTIUM;
        if (name.contains("thoriumbasedliquidfuel") || (name.contains("thorium") && name.contains("liquidfuel")))
            return PIPE_TIER_ELECTRUM;
        if (name.contains("uraniumbasedliquidfuel") || (name.contains("uranium") && name.contains("liquidfuel")))
            return PIPE_TIER_PLATINUM;
        if (name.contains("plutoniumbasedliquidfuel") || (name.contains("plutonium") && name.contains("liquidfuel")))
            return PIPE_TIER_FLUXED_ELECTRUM;
        return -1;
    }

    public static boolean isFluidAllowed(String fluidName, int pipeTier) {
        if (fluidName == null) return false;
        int reqTier = getRequiredFluidTier(fluidName);
        if (reqTier < 0) return false;
        if (configProvider != null) {
            com.gtnewhorizons.modularnuclear.common.config.data.TierData td = configProvider.getTier(pipeTier);
            if (td != null && !td.allowedCoolants.isEmpty()) {
                if (td.allowedCoolants.contains("*") || configProvider.matchesFluid(fluidName, td.allowedCoolants)) {
                    return true;
                }
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
     * Uses current ticksPerCycle to extrapolate from per-cycle neutron population to physical rate per second.
     */
    public static String formatNeutronFlux(double neutronsProduced) {
        return formatNeutronFlux(neutronsProduced, ticksPerCycle);
    }

    public static String formatNeutronFlux(double neutronsProduced, int tpc) {
        if (neutronsProduced <= 0) {
            return "0 neutrons/cm²/sec";
        }
        double cyclesPerSec = (tpc > 0) ? (20.0 / (double) tpc) : 1.0;
        double physicalFlux = neutronsProduced * cyclesPerSec * 1.0e13;
        return WasmStrings.replace(
            WasmStrings.replace(String.format(java.util.Locale.US, "%.2e neutrons/cm²/sec", physicalFlux), "+0", ""),
            "+",
            "");
    }

    public static String formatNeutronFlux(int neutronsProduced) {
        return formatNeutronFlux((double) neutronsProduced, ticksPerCycle);
    }

    public static String formatNeutronFlux(int neutronsProduced, int tpc) {
        return formatNeutronFlux((double) neutronsProduced, tpc);
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
        return WasmStrings.replace(
            WasmStrings.replace(String.format(java.util.Locale.US, "%.2e neutrons/cm²", physicalFluence), "+0", ""),
            "+",
            "");
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
        double[][] tileScatteringThermal = new double[sizeX][sizeY];

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
                    double pScatteringThermal = Math
                        .max(0.0, Math.min(1.0, hitTile.getScatteringProbability(NeutronType.THERMAL)));
                    double scatterT = survT * pScatteringThermal;
                    double unscatteredT = Math.max(0.0, survT - scatterT);
                    result.neutronsEscaped += (int) Math.round(unscatteredT);
                    tileScatteringThermal[x][y] += scatterT;

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

                int sT = (int) Math.round(tileScatteringThermal[x][y]);
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
        // Explicit integration overshoots (temperatures oscillate) once a cell's combined transfer per sub-step
        // exceeds about half its temperature difference, so use enough sub-steps to keep the worst cell under 0.5.
        final double dt = (double) ticksPerCycle / 20.0;
        double maxCoeff = BASE_HEAT_CONDUCTION;
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                if (grid[x][y] != null) {
                    maxCoeff = Math.max(maxCoeff, grid[x][y].getHeatTransferCoeff());
                }
            }
        }
        final int SUBSTEPS = Math
            .max(5, (int) Math.ceil(8.0 * maxCoeff * dt * Math.max(1.0, gridConductionMultiplier)));
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
                            double transferCoeff = 0.5 * (coeffA + coeffB) * dt / SUBSTEPS * gridConductionMultiplier;
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
                            double loss = (tempA - ambient) * (coeffA * dt / (2.0 * SUBSTEPS))
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

        // --- PASS 6: END-OF-TICK TELEMETRY & METRICS ---
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
     * Executes active heat exchanger equalization and active heat vent cooling over the 2D grid.
     * <p>
     * Heat exchangers act first, as one atomic, order-independent pass over a snapshot of cell temperatures (see
     * {@link #processHeatExchangers}). Heat vents then act on the resulting temperatures, so that vents remove heat
     * moved into their cells by exchangers within the same tick. Within each stage, components share the available
     * energy proportionally when it cannot cover every component's demand.
     */
    public static void processActiveVentsAndExchangers(INuclearTile[][] grid, int sizeX, int sizeY, double ambient) {
        if (grid == null || sizeX <= 0 || sizeY <= 0) return;
        processHeatExchangers(grid, sizeX, sizeY);
        processHeatVents(grid, sizeX, sizeY, ambient);
    }

    private static final double MIN_ACTIVE_TRANSFER_HU = 1e-4;

    /**
     * Rounds an EU amount up, tolerating floating-point noise just above an integer.
     */
    private static long ceilEU(double eu) {
        return (long) Math.ceil(eu - 1e-9);
    }

    /**
     * Energy shared by a set of components: they all draw from the same reactor energy hatches, so the pool is the
     * smallest amount any of them reports as available.
     */
    private static long sharedAvailableEnergy(List<INuclearTile> tiles) {
        long available = Long.MAX_VALUE;
        for (INuclearTile t : tiles) {
            available = Math.min(available, t.getAvailableEnergy());
        }
        return available;
    }

    /**
     * Fraction of demand that can be served from a pool. Leaves one EU of headroom per component so that rounding
     * each component's share up never exceeds the pool.
     */
    private static double energyScale(double demandEU, long availableEU, int components) {
        if (demandEU <= availableEU) return 1.0;
        return Math.max(0.0, (availableEU - (double) components) / demandEU);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Heat vents
    // ------------------------------------------------------------------------------------------------------------

    /** Planned action of one heat vent on its own cell. */
    private static final class VentPlan {

        final INuclearTile tile;
        final double ambient;
        final double huToMove;
        final double euPerHu;
        final long euNeeded;

        VentPlan(INuclearTile tile, double ambient, double huToMove, double euPerHu, long euNeeded) {
            this.tile = tile;
            this.ambient = ambient;
            this.huToMove = huToMove;
            this.euPerHu = euPerHu;
            this.euNeeded = euNeeded;
        }
    }

    /**
     * Plans a heat vent's action, equalizing its own cell towards ambient. Voids an over-temperature vent.
     * Returns null when the vent has nothing to do.
     */
    private static VentPlan planHeatVent(INuclearTile tile, double ambient) {
        com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.HeatVentData vent = tile.getHeatVentData();
        if (vent == null) return null;
        double curTemp = tile.getTemperature();
        if (vent.maxTemperature > 0.0 && curTemp > vent.maxTemperature) {
            tile.voidComponent();
            return null;
        }

        double euPerHu = vent.euPerHu > 0.0 ? vent.euPerHu : 0.25;
        double maxHU = vent.maxHUt > 0.0 ? vent.maxHUt : 512.0;
        long maxEU = vent.maxEUt > 0 ? vent.maxEUt : (long) Math.ceil(maxHU * euPerHu);

        double tempDiff = curTemp - ambient;
        if (Math.abs(tempDiff) < 1e-4) return null;

        double huToMove = Math.min(Math.abs(tempDiff) * HU_PER_DEGREE, maxHU);
        if (huToMove <= 0.0) return null;

        long euNeeded = ceilEU(huToMove * euPerHu);
        if (euNeeded > maxEU) {
            euNeeded = maxEU;
            huToMove = euNeeded / euPerHu;
        }
        return new VentPlan(tile, ambient, huToMove, euPerHu, euNeeded);
    }

    /**
     * Executes a planned vent action at a fraction of its planned throughput. Draws the scaled energy first; if it
     * cannot be drawn, the vent does nothing this tick.
     */
    private static void applyHeatVent(VentPlan plan, double scale) {
        double huToMove = plan.huToMove * scale;
        long euNeeded = (scale >= 1.0) ? plan.euNeeded : ceilEU(huToMove * plan.euPerHu);
        if (huToMove <= 0.0) return;
        if (!plan.tile.drainEnergy(euNeeded)) return;

        double curTemp = plan.tile.getTemperature();
        double deltaTemp = huToMove / HU_PER_DEGREE;
        if (curTemp > plan.ambient) {
            plan.tile.setTemperature(Math.max(plan.ambient, curTemp - deltaTemp));
        } else {
            plan.tile.setTemperature(Math.min(plan.ambient, curTemp + deltaTemp));
        }
    }

    /**
     * Processes a single active heat vent on its own cell, equalizing cell temperature towards ambient.
     * Consumes 1 EU per 4 HU removed or transported (configurable per vent).
     */
    public static void processHeatVent(INuclearTile tile, double ambient) {
        VentPlan plan = planHeatVent(tile, ambient);
        if (plan != null) {
            applyHeatVent(plan, 1.0);
        }
    }

    /**
     * Processes every heat vent on the grid. Vents act only on their own cells, so they are independent of each
     * other; when the shared energy pool cannot cover every vent's demand, all vents are throttled by the same
     * fraction.
     */
    public static void processHeatVents(INuclearTile[][] grid, int sizeX, int sizeY, double ambient) {
        List<VentPlan> plans = new ArrayList<>();
        List<INuclearTile> powered = new ArrayList<>();
        double demand = 0.0;
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                INuclearTile tile = grid[x][y];
                if (tile == null || !tile.isHeatVent()) continue;
                VentPlan plan = planHeatVent(tile, ambient);
                if (plan == null || tile.getAvailableEnergy() <= 0) continue;
                plans.add(plan);
                powered.add(tile);
                demand += plan.euNeeded;
            }
        }
        if (plans.isEmpty()) return;

        double scale = energyScale(demand, sharedAvailableEnergy(powered), plans.size());
        for (VentPlan plan : plans) {
            applyHeatVent(plan, scale);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Heat exchangers
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Planned action of one heat exchanger, computed from the temperature snapshot.
     * <p>
     * An exchanger equalizing its n cells towards their mean by a fraction s moves each cell by
     * s * (mean - T_i) = (s / n) * sum_j (T_j - T_i), i.e. it is a set of pairwise flows with weight s / n between
     * every pair of its cells.
     */
    private static final class ExchangerPlan {

        final INuclearTile tile;
        final int[] cells; // Flat indices x * sizeY + y; cells[0] is the exchanger's own cell
        final double weight; // Pairwise weight s / n
        final double euPerHu;
        final double maxHU;
        final long maxEU;
        double[] delta; // Per-cell temperature change (°C) after normalization and caps, parallel to cells
        double huMoved; // Heat moved (HU) after normalization and caps

        ExchangerPlan(INuclearTile tile, int[] cells, double weight, double euPerHu, double maxHU, long maxEU) {
            this.tile = tile;
            this.cells = cells;
            this.weight = weight;
            this.euPerHu = euPerHu;
            this.maxHU = maxHU;
            this.maxEU = maxEU;
        }
    }

    /**
     * Plans a heat exchanger's action from the snapshot. Voids an over-temperature exchanger. Returns null when the
     * exchanger has nothing to do or no energy to do it with.
     */
    private static ExchangerPlan planHeatExchanger(INuclearTile[][] grid, int x, int y, int sizeX, int sizeY,
        double[] snapshot) {
        INuclearTile center = grid[x][y];
        if (center == null) return null;
        com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.HeatExchangerData hex = center
            .getHeatExchangerData();
        if (hex == null) return null;
        if (hex.maxTemperature > 0.0 && snapshot[x * sizeY + y] > hex.maxTemperature) {
            center.voidComponent();
            return null;
        }
        if (center.getAvailableEnergy() <= 0) return null;

        double euPerHu = hex.euPerHu > 0.0 ? hex.euPerHu : 0.25;
        double maxHU = hex.maxHUt > 0.0 ? hex.maxHUt : 1024.0;
        long maxEU = hex.maxEUt > 0 ? hex.maxEUt : (long) Math.ceil(maxHU * euPerHu);

        // Gather own cell and up to 4 cardinally adjacent cells
        int[] cdX = { 0, 0, -1, 1 };
        int[] cdY = { -1, 1, 0, 0 };
        int[] buf = new int[5];
        int n = 0;
        buf[n++] = x * sizeY + y;
        for (int i = 0; i < 4; i++) {
            int nx = x + cdX[i];
            int ny = y + cdY[i];
            if (nx >= 0 && nx < sizeX && ny >= 0 && ny < sizeY && grid[nx][ny] != null) {
                buf[n++] = nx * sizeY + ny;
            }
        }
        if (n <= 1) return null;
        int[] cells = java.util.Arrays.copyOf(buf, n);

        double sumTemp = 0.0;
        for (int c : cells) sumTemp += snapshot[c];
        double mean = sumTemp / n;
        double totalHu = 0.0;
        for (int c : cells) {
            double diff = snapshot[c] - mean;
            if (diff > 0) totalHu += diff * HU_PER_DEGREE;
        }
        if (totalHu < MIN_ACTIVE_TRANSFER_HU) return null;

        double s = Math.min(1.0, maxHU / totalHu);
        if (ceilEU(totalHu * s * euPerHu) > maxEU) {
            s = (maxEU / euPerHu) / totalHu;
        }
        return new ExchangerPlan(center, cells, s / n, euPerHu, maxHU, maxEU);
    }

    /**
     * Processes every heat exchanger on the grid as one atomic, order-independent action.
     * <p>
     * 1. Every exchanger plans its equalization from the same temperature snapshot and expresses it as pairwise flows
     * f_ij = w * (T_j - T_i) between all pairs of its cells.<br>
     * 2. Pair weights from all exchangers are summed. Where exchangers overlap, a cell's total weight S_i can exceed
     * 1, which would overshoot (push a cell past every temperature around it). Each pair weight is therefore divided
     * by max(1, S_i, S_j). The result stays symmetric (heat is conserved) and every cell's total weight is at most 1,
     * so every new temperature is a weighted average of snapshot temperatures (no overshoot).<br>
     * 3. Per-exchanger HU and EU caps are re-applied to the normalized flows, and when the shared energy pool cannot
     * cover all exchangers, every exchanger is throttled by the same fraction. These only scale weights down, which
     * preserves both properties above.<br>
     * 4. Energy is drawn and all temperature changes are applied at once.
     */
    public static void processHeatExchangers(INuclearTile[][] grid, int sizeX, int sizeY) {
        if (grid == null || sizeX <= 0 || sizeY <= 0) return;
        double[] snapshot = takeTemperatureSnapshot(grid, sizeX, sizeY);
        List<ExchangerPlan> plans = new ArrayList<>();
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                INuclearTile tile = grid[x][y];
                if (tile != null && tile.isHeatExchanger()) {
                    ExchangerPlan plan = planHeatExchanger(grid, x, y, sizeX, sizeY, snapshot);
                    if (plan != null) plans.add(plan);
                }
            }
        }
        executeExchangerPlans(grid, sizeX, sizeY, snapshot, plans);
    }

    /**
     * Processes a single active heat exchanger, equalizing temperature between its own cell and all cardinally
     * adjacent cells. Consumes configured EU per HU transported (default 1 EU per 4 HU).
     */
    public static void processHeatExchanger(INuclearTile[][] grid, int x, int y, int sizeX, int sizeY) {
        if (grid == null || grid[x][y] == null) return;
        double[] snapshot = takeTemperatureSnapshot(grid, sizeX, sizeY);
        ExchangerPlan plan = planHeatExchanger(grid, x, y, sizeX, sizeY, snapshot);
        if (plan == null) return;
        List<ExchangerPlan> plans = new ArrayList<>(1);
        plans.add(plan);
        executeExchangerPlans(grid, sizeX, sizeY, snapshot, plans);
    }

    private static double[] takeTemperatureSnapshot(INuclearTile[][] grid, int sizeX, int sizeY) {
        double[] snapshot = new double[sizeX * sizeY];
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                INuclearTile tile = grid[x][y];
                snapshot[x * sizeY + y] = (tile != null) ? tile.getTemperature() : 0.0;
            }
        }
        return snapshot;
    }

    private static void executeExchangerPlans(INuclearTile[][] grid, int sizeX, int sizeY, double[] snapshot,
        List<ExchangerPlan> plans) {
        if (plans.isEmpty()) return;
        int cellCount = sizeX * sizeY;

        // 1. Total weight of each cell across all exchangers (sum over all pairs it belongs to)
        double[] cellWeight = new double[cellCount];
        for (ExchangerPlan plan : plans) {
            double perCell = plan.weight * (plan.cells.length - 1);
            for (int c : plan.cells) cellWeight[c] += perCell;
        }

        // 2. Normalized per-exchanger flows, then per-exchanger HU / EU caps
        double demandEU = 0.0;
        List<INuclearTile> powered = new ArrayList<>(plans.size());
        for (ExchangerPlan plan : plans) {
            int n = plan.cells.length;
            double[] delta = new double[n];
            for (int i = 0; i < n; i++) {
                int a = plan.cells[i];
                for (int j = i + 1; j < n; j++) {
                    int b = plan.cells[j];
                    double norm = Math.max(1.0, Math.max(cellWeight[a], cellWeight[b]));
                    double flow = plan.weight / norm * (snapshot[b] - snapshot[a]);
                    delta[i] += flow;
                    delta[j] -= flow;
                }
            }
            double hu = 0.0;
            for (double d : delta) {
                if (d < 0) hu -= d * HU_PER_DEGREE;
            }
            double cap = 1.0;
            if (hu > plan.maxHU) cap = plan.maxHU / hu;
            if (hu * cap * plan.euPerHu > plan.maxEU) cap = plan.maxEU / (hu * plan.euPerHu);
            for (int i = 0; i < n; i++) delta[i] *= cap;
            plan.delta = delta;
            plan.huMoved = hu * cap;
            if (plan.huMoved >= MIN_ACTIVE_TRANSFER_HU) {
                demandEU += plan.huMoved * plan.euPerHu;
                powered.add(plan.tile);
            }
        }
        if (powered.isEmpty()) return;

        // 3. Shared energy pool: throttle all exchangers by the same fraction when it cannot cover the demand
        double scale = energyScale(demandEU, sharedAvailableEnergy(powered), powered.size());

        // 4. Draw energy, accumulate, then apply all changes atomically
        double[] totalDelta = new double[cellCount];
        for (ExchangerPlan plan : plans) {
            if (plan.huMoved < MIN_ACTIVE_TRANSFER_HU) continue;
            long euNeeded = ceilEU(plan.huMoved * scale * plan.euPerHu);
            if (euNeeded <= 0 || !plan.tile.drainEnergy(euNeeded)) continue;
            for (int i = 0; i < plan.cells.length; i++) {
                totalDelta[plan.cells[i]] += plan.delta[i] * scale;
            }
        }
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                int c = x * sizeY + y;
                if (grid[x][y] != null && totalDelta[c] != 0.0) {
                    grid[x][y].setTemperature(snapshot[c] + totalDelta[c]);
                }
            }
        }
    }
}
