package com.gtnewhorizons.modularnuclear.common.nuclear.standalone;

import java.util.Random;

import com.gtnewhorizons.modularnuclear.common.nuclear.INuclearTile;
import com.gtnewhorizons.modularnuclear.common.nuclear.NeutronType;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearFuelType;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;

/**
 * Pure Java implementation of INuclearTile for standalone simulation and tooling.
 * Completely decoupled from Minecraft, Forge, and IC2 runtime classes.
 */
public class SimTile implements INuclearTile {

    public enum TileType {

        EMPTY("Empty", "."),
        FUEL_URANIUM_SINGLE("Uranium Single", "U1"),
        FUEL_URANIUM_DUAL("Uranium Dual", "U2"),
        FUEL_URANIUM_QUAD("Uranium Quad", "U4"),
        FUEL_MOX_SINGLE("MOX Single", "M1"),
        FUEL_MOX_DUAL("MOX Dual", "M2"),
        FUEL_MOX_QUAD("MOX Quad", "M4"),
        FUEL_THORIUM_SINGLE("Thorium Single", "T1"),
        FUEL_THORIUM_DUAL("Thorium Dual", "T2"),
        FUEL_THORIUM_QUAD("Thorium Quad", "T4"),
        FUEL_GLOWSTONE("Glowstone Rod", "G1"),
        FUEL_LITHIUM("Lithium Rod", "LI1"),
        FUEL_HD_URANIUM("HD Uranium Quad", "HDU"),
        FUEL_HD_PLUTONIUM("HD Plutonium Quad", "HDP"),
        FUEL_EXCITED_URANIUM("Excited Uranium Quad", "EXU"),
        FUEL_EXCITED_PLUTONIUM("Excited Plutonium Quad", "EXP"),
        FUEL_NAQUADAH("Naquadah Rod", "NQ"),
        FUEL_NAQUADRIA("Naquadria Quad", "NQR"),
        FUEL_TIBERIUM("Tiberium Quad", "TIB"),
        FUEL_CORE("The Core", "NQ32"),
        HATCH_LIQUID_FUEL_URANIUM("Liquid Uranium Fuel Hatch", "LFU"),
        HATCH_LIQUID_FUEL_THORIUM("Liquid Thorium Fuel Hatch", "LFT"),
        HATCH_LIQUID_FUEL_PLUTONIUM("Liquid Plutonium Fuel Hatch", "LFP"),
        HATCH_DISTILLED_WATER("Distilled Water Hatch", "HD"),
        HATCH_HP_DISTILLED_WATER("HP Distilled Water Hatch", "HP"),
        HATCH_HEAVY_WATER("Heavy Water Hatch", "HW"),
        HATCH_HP_HEAVY_WATER("HP Heavy Water Hatch", "HH"),
        HATCH_IC2_COOLANT("IC2 Coolant Hatch", "HC"),
        REFLECTOR_BERYLLIUM("Beryllium Reflector", "RB"),
        REFLECTOR_CARBON("Carbon Reflector", "RC"),
        CONTROL_ROD("Boron Control Rod", "CR"),
        COOLANT_CELL_10K("10k Coolant Cell", "C1"),
        COOLANT_CELL_60K("60k Coolant Cell", "C6"),
        COOLANT_CELL_360K("360k Coolant Cell", "C3"),
        RADIOVOLTAIC_HV("Radiovoltaic Cell (HV)", "RH"),
        RADIOVOLTAIC_EV("Radiovoltaic Cell (EV)", "RV"),
        INSULATOR_BASIC_THERMAL_CLOTH("Basic Thermal Cloth", "IT1"),
        INSULATOR_T2_THERMAL_CLOTH("T2 Thermal Cloth", "IT2"),
        INSULATOR_MICA_FOIL("Mica Insulator Foil", "IM"),
        INSULATOR_NAQUARITE_FOIL("Naquarite Universal Insulator Foil", "INQ"),
        VENT_STANDARD("Heat Vent", "V1"),
        VENT_ADVANCED("Advanced Heat Vent", "VA"),
        VENT_OVERCLOCKED("Overclocked Heat Vent", "VO"),
        VENT_COMPONENT("Component Heat Vent", "VC"),
        EXCHANGER_STANDARD("Heat Exchanger", "X1"),
        EXCHANGER_ADVANCED("Advanced Heat Exchanger", "XA"),
        EXCHANGER_COMPONENT("Component Heat Exchanger", "XC"),
        NULL_WALL("Reflective Wall", "NL");

        public final String displayName;
        public final String code;

        TileType(String displayName, String code) {
            this.displayName = displayName;
            this.code = code;
        }

        public static TileType fromCode(String code) {
            if (code == null) return EMPTY;
            String trimmed = code.trim()
                .toUpperCase();
            if (trimmed.equals("BH")) return RADIOVOLTAIC_HV;
            if (trimmed.equals("BV")) return RADIOVOLTAIC_EV;
            for (TileType type : values()) {
                if (type.code.equalsIgnoreCase(trimmed) || type.name()
                    .equalsIgnoreCase(trimmed)) {
                    return type;
                }
            }
            return EMPTY;
        }
    }

    private static final Random RAND = new Random();

    private TileType type = TileType.EMPTY;
    private double temperature = NuclearSimulationEngine.AMBIENT_TEMP;
    private double heatEU = 0;

    // Fuel state
    private int maxDurability = 10000;
    private int durability = 10000;
    private double durabilityLossAccumulator = 0.0;
    private boolean depleted = false;
    private int lastNeutronsGenerated = 0;

    // Coolant hatch state
    private String inputFluidName = "";
    private int inputFluidAmount = 0;
    private int inputFluidCapacity = 16000;
    private String outputFluidName = "";
    private int outputFluidAmount = 0;
    private boolean wasDry = false;
    private boolean autoRefill = true;
    private int totalSteamProduced = 0;
    private int totalDeuteriumProduced = 0;
    private int totalTritiumProduced = 0;
    private int lastTickProduced = 0;

    // Coolant cell state
    private int maxCellHeat = 0;
    private int currentCellHeat = 0;

    // Radiovoltaic direct EU state
    private long directEUProduced = 0;

    public long getDirectEUProduced() {
        return directEUProduced;
    }

    // Transient flux stats
    private int fastFlux = 0;
    private int thermalFlux = 0;
    private int fastAbsorbed = 0;
    private int thermalAbsorbed = 0;
    private int lastFastFlux = 0;
    private int lastThermalFlux = 0;
    private int lastTotalFlux = 0;
    private int lastFastAbsorbed = 0;
    private int lastThermalAbsorbed = 0;
    private String lastCoolingDetails = "";

    public String getLastCoolingDetails() {
        return lastCoolingDetails;
    }

    public SimTile(TileType type) {
        setType(type);
    }

    public void setType(TileType newType) {
        this.type = (newType == null) ? TileType.EMPTY : newType;
        this.depleted = false;
        this.wasDry = false;
        this.outputFluidAmount = 0;
        this.totalSteamProduced = 0;
        this.totalDeuteriumProduced = 0;
        this.totalTritiumProduced = 0;
        this.lastFastFlux = 0;
        this.lastThermalFlux = 0;
        this.lastTotalFlux = 0;
        this.lastFastAbsorbed = 0;
        this.lastThermalAbsorbed = 0;
        this.durabilityLossAccumulator = 0.0;
        this.directEUProduced = 0;

        NuclearFuelType fuel = getFuelType();
        if (fuel != null && !isLiquidFuelHatch()) {
            this.maxDurability = fuel.defaultDurability;
            this.durability = fuel.defaultDurability;
            this.inputFluidAmount = 0;
        } else {
            switch (this.type) {
                case HATCH_LIQUID_FUEL_URANIUM -> {
                    this.inputFluidName = "uraniumbasedliquidfuel";
                    this.inputFluidCapacity = NuclearSimulationEngine.hatchCoolantCapacity;
                    this.inputFluidAmount = this.inputFluidCapacity;
                    this.outputFluidName = "depleteduraniumbasedliquidfuel";
                }
                case HATCH_LIQUID_FUEL_THORIUM -> {
                    this.inputFluidName = "thoriumbasedliquidfuel";
                    this.inputFluidCapacity = NuclearSimulationEngine.hatchCoolantCapacity;
                    this.inputFluidAmount = this.inputFluidCapacity;
                    this.outputFluidName = "depletedthoriumbasedliquidfuel";
                }
                case HATCH_LIQUID_FUEL_PLUTONIUM -> {
                    this.inputFluidName = "plutoniumbasedliquidfuel";
                    this.inputFluidCapacity = NuclearSimulationEngine.hatchCoolantCapacity;
                    this.inputFluidAmount = this.inputFluidCapacity;
                    this.outputFluidName = "depletedplutoniumbasedliquidfuel";
                }
                case HATCH_DISTILLED_WATER -> {
                    this.inputFluidName = "distilledwater";
                    this.inputFluidCapacity = NuclearSimulationEngine.hatchCoolantCapacity;
                    this.inputFluidAmount = this.inputFluidCapacity;
                    this.outputFluidName = "steam";
                }
                case HATCH_HP_DISTILLED_WATER -> {
                    this.inputFluidName = "highpressuredistilledwater";
                    this.inputFluidCapacity = NuclearSimulationEngine.hatchCoolantCapacity;
                    this.inputFluidAmount = this.inputFluidCapacity;
                    this.outputFluidName = "ic2superheatedsteam";
                }
                case HATCH_HEAVY_WATER -> {
                    this.inputFluidName = "heavywater";
                    this.inputFluidCapacity = NuclearSimulationEngine.hatchCoolantCapacity;
                    this.inputFluidAmount = this.inputFluidCapacity;
                    this.outputFluidName = "fluid.heavywatersteam";
                }
                case HATCH_HP_HEAVY_WATER -> {
                    this.inputFluidName = "highpressureheavywater";
                    this.inputFluidCapacity = NuclearSimulationEngine.hatchCoolantCapacity;
                    this.inputFluidAmount = this.inputFluidCapacity;
                    this.outputFluidName = "fluid.highpressureheavywatersteam";
                }
                case HATCH_IC2_COOLANT -> {
                    this.inputFluidName = "ic2coolant";
                    this.inputFluidCapacity = NuclearSimulationEngine.hatchCoolantCapacity;
                    this.inputFluidAmount = this.inputFluidCapacity;
                    this.outputFluidName = "ic2hotcoolant";
                }
                case COOLANT_CELL_10K -> {
                    this.maxCellHeat = 10000;
                    this.currentCellHeat = 0;
                }
                case COOLANT_CELL_60K -> {
                    this.maxCellHeat = 60000;
                    this.currentCellHeat = 0;
                }
                case COOLANT_CELL_360K -> {
                    this.maxCellHeat = 360000;
                    this.currentCellHeat = 0;
                }
                case VENT_STANDARD, VENT_ADVANCED, VENT_OVERCLOCKED -> {
                    this.maxCellHeat = 1000;
                    this.currentCellHeat = 0;
                }
                case VENT_COMPONENT -> {
                    this.maxCellHeat = 0;
                    this.currentCellHeat = 0;
                }
                case EXCHANGER_STANDARD -> {
                    this.maxCellHeat = 2500;
                    this.currentCellHeat = 0;
                }
                case EXCHANGER_ADVANCED -> {
                    this.maxCellHeat = 10000;
                    this.currentCellHeat = 0;
                }
                case EXCHANGER_COMPONENT -> {
                    this.maxCellHeat = 5000;
                    this.currentCellHeat = 0;
                }
                default -> {
                    this.inputFluidAmount = 0;
                    this.maxCellHeat = 0;
                    this.currentCellHeat = 0;
                }
            }
        }
    }

    public TileType getType() {
        return type;
    }

    @Override
    public NuclearFuelType getFuelType() {
        return switch (type) {
            case FUEL_GLOWSTONE -> NuclearFuelType.GLOWSTONE;
            case FUEL_LITHIUM -> NuclearFuelType.LITHIUM;
            case FUEL_THORIUM_SINGLE, FUEL_THORIUM_DUAL, FUEL_THORIUM_QUAD, HATCH_LIQUID_FUEL_THORIUM -> NuclearFuelType.THORIUM;
            case FUEL_URANIUM_SINGLE, FUEL_URANIUM_DUAL, FUEL_URANIUM_QUAD, HATCH_LIQUID_FUEL_URANIUM -> NuclearFuelType.URANIUM;
            case FUEL_MOX_SINGLE, FUEL_MOX_DUAL, FUEL_MOX_QUAD -> NuclearFuelType.MOX;
            case FUEL_HD_URANIUM -> NuclearFuelType.HD_URANIUM;
            case FUEL_HD_PLUTONIUM, HATCH_LIQUID_FUEL_PLUTONIUM -> NuclearFuelType.HD_PLUTONIUM;
            case FUEL_EXCITED_URANIUM -> NuclearFuelType.EXCITED_URANIUM;
            case FUEL_EXCITED_PLUTONIUM -> NuclearFuelType.EXCITED_PLUTONIUM;
            case FUEL_NAQUADAH -> NuclearFuelType.NAQUADAH;
            case FUEL_NAQUADRIA -> NuclearFuelType.NAQUADRIA;
            case FUEL_TIBERIUM -> NuclearFuelType.TIBERIUM;
            case FUEL_CORE -> NuclearFuelType.THE_CORE;
            default -> null;
        };
    }

    @Override
    public double getTemperature() {
        return temperature;
    }

    @Override
    public void setTemperature(double temp) {
        this.temperature = Math.max(NuclearSimulationEngine.AMBIENT_TEMP, temp);
    }

    @Override
    public void addHeat(double heat) {
        this.heatEU += heat;
        this.temperature = Math
            .max(NuclearSimulationEngine.AMBIENT_TEMP, this.temperature + heat / NuclearSimulationEngine.euPerDegree);
    }

    @Override
    public double getHeatTransferCoeff() {
        if (isFuel() && !isLiquidFuelHatch()) return 0.05;
        return switch (type) {
            case HATCH_IC2_COOLANT -> 0.50;
            case HATCH_DISTILLED_WATER, HATCH_HP_DISTILLED_WATER, HATCH_HEAVY_WATER, HATCH_HP_HEAVY_WATER -> 0.25;
            case COOLANT_CELL_10K, COOLANT_CELL_60K, COOLANT_CELL_360K -> 0.40;
            case VENT_STANDARD, VENT_ADVANCED, VENT_OVERCLOCKED, VENT_COMPONENT -> 0.35;
            case EXCHANGER_STANDARD, EXCHANGER_ADVANCED, EXCHANGER_COMPONENT -> 0.40;
            case REFLECTOR_BERYLLIUM, REFLECTOR_CARBON -> 0.15;
            case RADIOVOLTAIC_HV, RADIOVOLTAIC_EV -> 0.10;
            case INSULATOR_BASIC_THERMAL_CLOTH, INSULATOR_T2_THERMAL_CLOTH, INSULATOR_MICA_FOIL, INSULATOR_NAQUARITE_FOIL -> 0.01;
            case HATCH_LIQUID_FUEL_URANIUM, HATCH_LIQUID_FUEL_THORIUM, HATCH_LIQUID_FUEL_PLUTONIUM -> 0.25;
            default -> 0.02;
        };
    }

    private double insulationDampening = 0.0;

    @Override
    public double getInsulationDampening() {
        return switch (type) {
            case INSULATOR_BASIC_THERMAL_CLOTH -> 0.20;
            case INSULATOR_T2_THERMAL_CLOTH -> 0.40;
            case INSULATOR_MICA_FOIL -> 0.60;
            case INSULATOR_NAQUARITE_FOIL -> 1.00;
            default -> insulationDampening;
        };
    }

    public void setInsulationDampening(double dampening) {
        this.insulationDampening = dampening;
    }

    public boolean isInsulator() {
        return getInsulationDampening() > 0.0;
    }

    public boolean isRadiovoltaic() {
        return type == TileType.RADIOVOLTAIC_HV || type == TileType.RADIOVOLTAIC_EV;
    }

    @Override
    public boolean isFuel() {
        if (depleted) return false;
        return getFuelType() != null;
    }

    public boolean isHatch() {
        return isCoolantHatch() || isLiquidFuelHatch();
    }

    public boolean isCoolantHatch() {
        return switch (type) {
            case HATCH_DISTILLED_WATER, HATCH_HP_DISTILLED_WATER, HATCH_HEAVY_WATER, HATCH_HP_HEAVY_WATER, HATCH_IC2_COOLANT -> true;
            default -> false;
        };
    }

    public boolean isLiquidFuelHatch() {
        return switch (type) {
            case HATCH_LIQUID_FUEL_URANIUM, HATCH_LIQUID_FUEL_THORIUM, HATCH_LIQUID_FUEL_PLUTONIUM -> true;
            default -> false;
        };
    }

    @Override
    public int generateNeutrons(double efficiency) {
        if (!isFuel() || depleted) {
            lastNeutronsGenerated = 0;
            return 0;
        }

        int baseNeutrons = switch (type) {
            case FUEL_GLOWSTONE, FUEL_LITHIUM -> 1;
            case FUEL_THORIUM_SINGLE -> 2;
            case FUEL_THORIUM_DUAL -> 4;
            case FUEL_THORIUM_QUAD -> 8;
            case FUEL_URANIUM_SINGLE -> 4;
            case FUEL_URANIUM_DUAL -> 8;
            case FUEL_URANIUM_QUAD -> 16;
            case FUEL_MOX_SINGLE -> 8;
            case FUEL_MOX_DUAL -> 16;
            case FUEL_MOX_QUAD -> 32;
            case FUEL_HD_URANIUM -> 16;
            case FUEL_HD_PLUTONIUM -> 24;
            case FUEL_EXCITED_URANIUM -> 32;
            case FUEL_EXCITED_PLUTONIUM -> 48;
            case FUEL_NAQUADAH -> 16;
            case FUEL_NAQUADRIA -> 32;
            case FUEL_TIBERIUM -> 48;
            case FUEL_CORE -> 512;
            case HATCH_LIQUID_FUEL_THORIUM -> 4;
            case HATCH_LIQUID_FUEL_URANIUM -> 8;
            case HATCH_LIQUID_FUEL_PLUTONIUM -> 16;
            default -> 4;
        };

        NuclearFuelType fuel = getFuelType();
        double baseFissionMult = (fuel != null) ? fuel.baseThermalFissionMultiplier : 1.0;
        double effectiveFissionMult = baseFissionMult * NuclearSimulationEngine.globalThermalFissionMultiplier;

        int chainNeutrons = (int) Math.round(lastThermalAbsorbed * effectiveFissionMult);
        int produced = (int) Math.round((baseNeutrons + chainNeutrons) * efficiency);
        lastNeutronsGenerated = produced;
        return produced;
    }

    @Override
    public int getNeutronEmissionCount() {
        return switch (type) {
            case FUEL_URANIUM_SINGLE, FUEL_MOX_SINGLE, FUEL_THORIUM_SINGLE, FUEL_GLOWSTONE, FUEL_LITHIUM -> 1;
            case FUEL_URANIUM_DUAL, FUEL_MOX_DUAL, FUEL_THORIUM_DUAL -> 2;
            case FUEL_CORE -> 16;
            default -> 4;
        };
    }

    @Override
    public double getAbsorptionProbability(NeutronType nType) {
        if (type == TileType.INSULATOR_NAQUARITE_FOIL) {
            return 1.0;
        }
        if (isRadiovoltaic()) {
            return 1.0;
        }
        if (type == TileType.CONTROL_ROD) {
            return (nType == NeutronType.THERMAL) ? 0.95 : 0.85;
        }
        if (isLiquidFuelHatch()) {
            if (inputFluidAmount <= 0) return 0.01;
            return (nType == NeutronType.THERMAL) ? 0.85 : 0.25;
        }
        if (isFuel()) {
            return (nType == NeutronType.THERMAL) ? 0.80 : 0.25;
        }
        if (isCoolantHatch()) {
            if (inputFluidAmount <= 0) return 0.01;
            return switch (type) {
                case HATCH_HEAVY_WATER, HATCH_HP_HEAVY_WATER -> (nType == NeutronType.THERMAL) ? 0.01 : 0.005;
                case HATCH_DISTILLED_WATER, HATCH_HP_DISTILLED_WATER -> (nType == NeutronType.THERMAL) ? 0.10 : 0.05;
                case HATCH_IC2_COOLANT -> (nType == NeutronType.THERMAL) ? 0.12 : 0.03;
                default -> 0.05;
            };
        }
        return switch (type) {
            case REFLECTOR_BERYLLIUM, REFLECTOR_CARBON -> 0.02;
            case COOLANT_CELL_10K, COOLANT_CELL_60K, COOLANT_CELL_360K, VENT_STANDARD, VENT_ADVANCED, VENT_OVERCLOCKED, VENT_COMPONENT, EXCHANGER_STANDARD, EXCHANGER_ADVANCED, EXCHANGER_COMPONENT -> 0.03;
            default -> 0.01;
        };
    }

    @Override
    public double getScatteringProbability(NeutronType nType) {
        if (type == TileType.INSULATOR_NAQUARITE_FOIL) {
            return 0.0;
        }
        if (isRadiovoltaic()) {
            return 0.0;
        }
        if (type == TileType.REFLECTOR_BERYLLIUM || type == TileType.REFLECTOR_CARBON) {
            return 0.95;
        }
        if (isLiquidFuelHatch()) {
            if (inputFluidAmount <= 0) return 0.02;
            return 0.15;
        }
        if (isCoolantHatch()) {
            if (inputFluidAmount <= 0) return 0.02;
            return switch (type) {
                case HATCH_HEAVY_WATER, HATCH_HP_HEAVY_WATER -> 0.85;
                case HATCH_DISTILLED_WATER, HATCH_HP_DISTILLED_WATER -> 0.70;
                case HATCH_IC2_COOLANT -> 0.45;
                default -> 0.10;
            };
        }
        if (isFuel()) return 0.15;
        return 0.05;
    }

    @Override
    public double getModerationProbability() {
        if (isRadiovoltaic()) {
            return 0.0;
        }
        if (type == TileType.REFLECTOR_BERYLLIUM || type == TileType.REFLECTOR_CARBON) {
            return 0.65;
        }
        if (isLiquidFuelHatch()) {
            return 0.10;
        }
        if (isCoolantHatch()) {
            if (inputFluidAmount <= 0) return 0.05;
            return switch (type) {
                case HATCH_HEAVY_WATER, HATCH_HP_HEAVY_WATER -> 0.90;
                case HATCH_DISTILLED_WATER, HATCH_HP_DISTILLED_WATER -> 0.80;
                case HATCH_IC2_COOLANT -> 0.40;
                default -> 0.20;
            };
        }
        return 0.10;
    }

    @Override
    public void onNeutronAbsorbed(NeutronType nType, int count) {
        if (nType == NeutronType.FAST) fastAbsorbed += count;
        else thermalAbsorbed += count;

        // Fast neutron capture transmutation
        if (nType == NeutronType.FAST && isCoolantHatch() && inputFluidAmount > 0) {
            if (type == TileType.HATCH_DISTILLED_WATER || type == TileType.HATCH_HP_DISTILLED_WATER) {
                boolean isHP = (type == TileType.HATCH_HP_DISTILLED_WATER);
                int chance = isHP ? Math.min(100, count * 10) : Math.min(100, count * 5);
                int yield = isHP ? 2 : 1;
                if (RAND.nextInt(100) < chance) {
                    inputFluidAmount -= 1;
                    totalDeuteriumProduced += yield;
                }
            } else if (type == TileType.HATCH_HEAVY_WATER || type == TileType.HATCH_HP_HEAVY_WATER) {
                boolean isHP = (type == TileType.HATCH_HP_HEAVY_WATER);
                int chance = isHP ? Math.min(100, count * 10) : Math.min(100, count * 5);
                int yield = isHP ? 2 : 1;
                if (RAND.nextInt(100) < chance) {
                    inputFluidAmount -= 1;
                    totalTritiumProduced += yield;
                }
            }
        }
    }

    @Override
    public void onNeutronScattered(NeutronType nType, int count) {}

    @Override
    public void addNeutronFlux(NeutronType nType, int count) {
        if (nType == NeutronType.FAST) fastFlux += count;
        else thermalFlux += count;
    }

    @Override
    public void nuclearTick(double efficiency) {
        lastFastFlux = fastFlux;
        lastThermalFlux = thermalFlux;
        lastTotalFlux = fastFlux + thermalFlux;
        lastFastAbsorbed = fastAbsorbed;
        lastThermalAbsorbed = thermalAbsorbed;
        lastTickProduced = 0;

        // 1. Fuel burnup
        if (isLiquidFuelHatch()) {
            if (inputFluidAmount > 0) {
                int burn = fastAbsorbed * 1 + thermalAbsorbed * 2 + Math.max(1, lastNeutronsGenerated / 4);
                int toConsume = Math.max(1, Math.min(inputFluidAmount, burn));
                inputFluidAmount -= toConsume;
                outputFluidAmount += toConsume;
                if (inputFluidAmount <= 0) {
                    inputFluidAmount = 0;
                    depleted = true;
                }
            } else {
                depleted = true;
            }
        } else if (isFuel() && !depleted) {
            double rawDamage = (fastAbsorbed * 1.0 + thermalAbsorbed * 2.0 + Math.max(1.0, lastNeutronsGenerated / 4.0))
                * NuclearSimulationEngine.fuelBurnupMultiplier;
            durabilityLossAccumulator += rawDamage;
            int intDamage = (int) durabilityLossAccumulator;
            if (intDamage > 0) {
                durability = Math.max(0, durability - intDamage);
                durabilityLossAccumulator -= intDamage;
                if (durability == 0) {
                    depleted = true;
                }
            }
        }

        // Radiovoltaic direct EU generation & excess heat
        if (isRadiovoltaic()) {
            double effFactor = Math.max(0.0, Math.min(1.0, efficiency));
            long maxEU = (type == TileType.RADIOVOLTAIC_EV) ? 4096 : 1024;
            double weightedFlux = fastAbsorbed * 4.0 + thermalAbsorbed * 1.0;
            double satFlux = 60.0;
            long genEU = (long) Math.round(maxEU * Math.tanh(weightedFlux / satFlux) * effFactor);
            this.directEUProduced = genEU;
            double totalEnergy = weightedFlux * 20.0;
            double excessHeat = Math.max(0.0, totalEnergy - genEU);
            if (excessHeat > 0.0) {
                addHeat(excessHeat);
            }
        } else {
            this.directEUProduced = 0;
        }

        fastFlux = 0;
        thermalFlux = 0;
        fastAbsorbed = 0;
        thermalAbsorbed = 0;

        // 2. Heat Vent & Coolant cell absorption & venting
        if (isHeatVent()) {
            int selfVent = switch (type) {
                case VENT_STANDARD -> 6;
                case VENT_ADVANCED -> 12;
                case VENT_OVERCLOCKED -> 20;
                default -> 0;
            };

            double effFactor = Math.max(0.0, Math.min(1.0, efficiency));
            double tempDiff = temperature - NuclearSimulationEngine.AMBIENT_TEMP;
            if (selfVent > 0 && tempDiff > 0 && currentCellHeat < maxCellHeat) {
                int maxHu = (int) Math.min(
                    (double) (selfVent * 2),
                    (tempDiff * NuclearSimulationEngine.EU_PER_DEGREE) / 25.0 * effFactor);
                int toTake = Math.min(maxHu, maxCellHeat - currentCellHeat);
                if (toTake > 0) {
                    currentCellHeat += toTake;
                    temperature = Math.max(
                        NuclearSimulationEngine.AMBIENT_TEMP,
                        temperature - (toTake * 25.0) / NuclearSimulationEngine.EU_PER_DEGREE);
                }
            }

            if (selfVent > 0) {
                int vented = Math.min(currentCellHeat, selfVent);
                currentCellHeat -= vented;
                lastCoolingDetails = String.format(java.util.Locale.US, "Vented %d Hu (%d EU)", vented, vented * 25);
            }
        } else if (isCoolantCell()) {
            if (maxCellHeat > 0 && currentCellHeat < maxCellHeat) {
                double effFactor = Math.max(0.0, Math.min(1.0, efficiency));
                double tempDiff = temperature - NuclearSimulationEngine.AMBIENT_TEMP;
                if (tempDiff > 0) {
                    int heatToAbsorb = (int) Math.min(
                        tempDiff * NuclearSimulationEngine.EU_PER_DEGREE * 0.1 * effFactor,
                        maxCellHeat - currentCellHeat);
                    currentCellHeat += heatToAbsorb;
                    temperature = Math.max(
                        NuclearSimulationEngine.AMBIENT_TEMP,
                        temperature - heatToAbsorb / NuclearSimulationEngine.EU_PER_DEGREE);
                }
            }
        }

        // 3. Fluid cooling & heat exchange
        if (isCoolantHatch()) {
            double effFactor = Math.max(0.0, Math.min(1.0, efficiency));
            if (type == TileType.HATCH_IC2_COOLANT) {
                double operatingThreshold = NuclearSimulationEngine.AMBIENT_TEMP;
                double heatPerL = NuclearSimulationEngine.ic2CoolantHeatPerLiter;

                if (inputFluidAmount > 0 && temperature > operatingThreshold) {
                    double deltaT = temperature - operatingThreshold;
                    double heatAvailable = deltaT * NuclearSimulationEngine.EU_PER_DEGREE;
                    int maxCoolByHeat = (heatPerL > 0) ? (int) Math.floor(heatAvailable / heatPerL) : inputFluidAmount;

                    double frac = NuclearSimulationEngine.calculateTurnoverFraction(deltaT);
                    int desiredTurnover = Math.max(1, (int) Math.round(inputFluidCapacity * frac * effFactor));
                    int mbToCool = Math.min(inputFluidAmount, Math.min(desiredTurnover, maxCoolByHeat));

                    if (mbToCool > 0) {
                        inputFluidAmount -= mbToCool;
                        lastTickProduced = mbToCool;
                        outputFluidAmount += mbToCool;
                        totalSteamProduced += mbToCool;

                        double heatConsumed = mbToCool * heatPerL;
                        double tempDrop = heatConsumed / NuclearSimulationEngine.EU_PER_DEGREE;
                        double tempBefore = temperature;
                        temperature = Math.max(operatingThreshold, temperature - tempDrop);
                        lastCoolingDetails = String.format(
                            java.util.Locale.US,
                            "IC2 Coolant %dL (cap %dL, frac %.2f, maxByHeat %dL), consumed %.1f EU, temp %.1f°C -> %.1f°C (drop %.1f°C)",
                            mbToCool,
                            inputFluidCapacity,
                            frac,
                            maxCoolByHeat,
                            heatConsumed,
                            tempBefore,
                            temperature,
                            tempDrop);
                    }
                }

                if (inputFluidAmount <= 0) {
                    inputFluidAmount = 0;
                    wasDry = true;
                }
            } else {
                // Phase-change boiling hatches (Distilled Water, HP Distilled Water, Heavy Water, HP Heavy Water)
                double boilingPoint = 100.0;
                double heatPerL = NuclearSimulationEngine.coolingHeatPerLiter;
                int steamRatio = 160;

                switch (type) {
                    case HATCH_HP_HEAVY_WATER -> {
                        boilingPoint = NuclearSimulationEngine.hpWaterBoilingPoint;
                        heatPerL = NuclearSimulationEngine.coolingHeatPerLiter * 4.0;
                        steamRatio = 320;
                    }
                    case HATCH_HP_DISTILLED_WATER -> {
                        boilingPoint = NuclearSimulationEngine.hpWaterBoilingPoint;
                        heatPerL = NuclearSimulationEngine.coolingHeatPerLiter * 2.0;
                        steamRatio = 320;
                    }
                    case HATCH_HEAVY_WATER -> {
                        boilingPoint = 100.0;
                        heatPerL = NuclearSimulationEngine.coolingHeatPerLiter;
                        steamRatio = 160;
                    }
                    case HATCH_DISTILLED_WATER -> {
                        boilingPoint = 100.0;
                        heatPerL = NuclearSimulationEngine.coolingHeatPerLiter;
                        steamRatio = 160;
                    }
                    default -> {}
                }

                if (inputFluidAmount > 0 && temperature > boilingPoint) {
                    double deltaT = temperature - boilingPoint;
                    double heatAvailable = deltaT * NuclearSimulationEngine.EU_PER_DEGREE;
                    int maxBoilByHeat = (heatPerL > 0) ? (int) Math.floor(heatAvailable / heatPerL) : inputFluidAmount;

                    double frac = NuclearSimulationEngine.calculateTurnoverFraction(deltaT);
                    int desiredTurnover = Math.max(1, (int) Math.round(inputFluidCapacity * frac * effFactor));
                    int mbToBoil = Math.min(inputFluidAmount, Math.min(desiredTurnover, maxBoilByHeat));

                    if (mbToBoil > 0) {
                        inputFluidAmount -= mbToBoil;
                        int steamProduced = mbToBoil * steamRatio;
                        lastTickProduced = steamProduced;
                        outputFluidAmount += steamProduced;
                        totalSteamProduced += steamProduced;

                        double heatConsumed = mbToBoil * heatPerL;
                        double tempDrop = heatConsumed / NuclearSimulationEngine.EU_PER_DEGREE;
                        double tempBefore = temperature;
                        temperature = Math.max(boilingPoint, temperature - tempDrop);
                        lastCoolingDetails = String.format(
                            java.util.Locale.US,
                            "Boiled %dL (cap %dL, frac %.2f, maxByHeat %dL), consumed %.1f EU, temp %.1f°C -> %.1f°C (drop %.1f°C)",
                            mbToBoil,
                            inputFluidCapacity,
                            frac,
                            maxBoilByHeat,
                            heatConsumed,
                            tempBefore,
                            temperature,
                            tempDrop);
                    }
                }

                if (inputFluidAmount <= 0) {
                    inputFluidAmount = 0;
                    wasDry = true;
                }
            }
        }
    }

    /**
     * Refills coolant fluid, respecting wasDry explosion safety checks.
     * 
     * @return true if refilled safely, false if thermal shock explosion triggered
     */
    public boolean refillCoolant() {
        return refillCoolant(inputFluidCapacity);
    }

    public boolean refillCoolant(int maxFeed) {
        if (inputFluidAmount >= inputFluidCapacity) return true;
        if (wasDry) {
            double threshold = NuclearSimulationEngine.getCoolantBoilingThreshold(inputFluidName);
            if (temperature > threshold) {
                return false; // Thermal shock explosion!
            }
            wasDry = false;
        }
        int toAdd = Math.min(maxFeed, inputFluidCapacity - inputFluidAmount);
        inputFluidAmount += toAdd;
        return true;
    }

    public void setInputFluidCapacity(int capacity) {
        this.inputFluidCapacity = Math.max(100, capacity);
        if (this.inputFluidAmount > this.inputFluidCapacity) {
            this.inputFluidAmount = this.inputFluidCapacity;
        }
    }

    public void setInputFluidAmount(int amount) {
        this.inputFluidAmount = Math.max(0, Math.min(amount, inputFluidCapacity));
        if (this.inputFluidAmount > 0) {
            this.wasDry = false;
        }
    }

    // Getters and helper status methods
    public int getDurability() {
        return durability;
    }

    public int getMaxDurability() {
        return maxDurability;
    }

    public double getDurabilityPercent() {
        return maxDurability <= 0 ? 0.0 : (durability * 100.0 / maxDurability);
    }

    public boolean isDepleted() {
        return depleted;
    }

    public int getLastNeutronsGenerated() {
        return lastNeutronsGenerated;
    }

    public int getLastTickProduced() {
        return lastTickProduced;
    }

    public String getInputFluidName() {
        return inputFluidName;
    }

    public int getInputFluidAmount() {
        return inputFluidAmount;
    }

    public int getInputFluidCapacity() {
        return inputFluidCapacity;
    }

    public String getOutputFluidName() {
        return outputFluidName;
    }

    public int getOutputFluidAmount() {
        return outputFluidAmount;
    }

    public void setOutputFluidAmount(int amount) {
        this.outputFluidAmount = Math.max(0, amount);
    }

    public int getTotalSteamProduced() {
        return totalSteamProduced;
    }

    public int getTotalDeuteriumProduced() {
        return totalDeuteriumProduced;
    }

    public int getTotalTritiumProduced() {
        return totalTritiumProduced;
    }

    public boolean isWasDry() {
        return wasDry;
    }

    public void setWasDry(boolean wasDry) {
        this.wasDry = wasDry;
    }

    public boolean isAutoRefill() {
        return autoRefill;
    }

    public void setAutoRefill(boolean autoRefill) {
        this.autoRefill = autoRefill;
    }

    public int getMaxCellHeat() {
        return maxCellHeat;
    }

    public int getCurrentCellHeat() {
        return currentCellHeat;
    }

    public int getFastFlux() {
        return fastFlux;
    }

    public int getThermalFlux() {
        return thermalFlux;
    }

    public int getFastAbsorbed() {
        return fastAbsorbed;
    }

    public int getThermalAbsorbed() {
        return thermalAbsorbed;
    }

    public int getLastFastFlux() {
        return lastFastFlux;
    }

    public int getLastThermalFlux() {
        return lastThermalFlux;
    }

    public int getLastTotalFlux() {
        return lastTotalFlux;
    }

    public int getLastFastAbsorbed() {
        return lastFastAbsorbed;
    }

    public int getLastThermalAbsorbed() {
        return lastThermalAbsorbed;
    }

    public boolean isCoolantCell() {
        return switch (type) {
            case COOLANT_CELL_10K, COOLANT_CELL_60K, COOLANT_CELL_360K -> true;
            default -> false;
        };
    }

    public boolean isHeatVent() {
        return switch (type) {
            case VENT_STANDARD, VENT_ADVANCED, VENT_OVERCLOCKED, VENT_COMPONENT -> true;
            default -> false;
        };
    }

    public boolean isHeatExchanger() {
        return switch (type) {
            case EXCHANGER_STANDARD, EXCHANGER_ADVANCED, EXCHANGER_COMPONENT -> true;
            default -> false;
        };
    }

    public boolean canStoreHeat() {
        return maxCellHeat > 0;
    }

    public void setCurrentCellHeat(int heat) {
        this.currentCellHeat = Math.max(0, Math.min(maxCellHeat, heat));
    }

    public int alterCellHeat(int delta) {
        int target = this.currentCellHeat + delta;
        if (target > maxCellHeat) {
            int overflow = target - maxCellHeat;
            this.currentCellHeat = maxCellHeat;
            return overflow;
        } else if (target < 0) {
            int underflow = target;
            this.currentCellHeat = 0;
            return underflow;
        } else {
            this.currentCellHeat = target;
            return 0;
        }
    }

    public void processNeighborComponents(INuclearTile[][] grid, int x, int y, int sizeX, int sizeY) {
        if (type == TileType.VENT_COMPONENT) {
            final int sideVent = 4;
            int[] dx = { 0, 0, -1, 1 };
            int[] dy = { -1, 1, 0, 0 };
            for (int k = 0; k < 4; k++) {
                int nx = x + dx[k];
                int ny = y + dy[k];
                if (nx >= 0 && nx < sizeX && ny >= 0 && ny < sizeY && grid[nx][ny] instanceof SimTile neighbor) {
                    if (neighbor.canStoreHeat()) {
                        neighbor.alterCellHeat(-sideVent);
                    }
                }
            }
        } else if (isHeatExchanger()) {
            int switchSide = switch (type) {
                case EXCHANGER_STANDARD -> 12;
                case EXCHANGER_ADVANCED -> 24;
                case EXCHANGER_COMPONENT -> 36;
                default -> 0;
            };

            // 1. Absorb heat from own tile if hotter than ambient
            double tempDiff = temperature - NuclearSimulationEngine.ambientTemp;
            if (tempDiff > 0 && currentCellHeat < maxCellHeat) {
                int maxHu = (int) Math
                    .min((double) switchSide, (tempDiff * NuclearSimulationEngine.euPerDegree) / 25.0);
                int toTake = Math.min(maxHu, maxCellHeat - currentCellHeat);
                if (toTake > 0) {
                    currentCellHeat += toTake;
                    temperature = Math.max(
                        NuclearSimulationEngine.ambientTemp,
                        temperature - (toTake * 25.0) / NuclearSimulationEngine.euPerDegree);
                }
            }

            // 2. Balance heat % with up to 4 adjacent components
            int[] dx = { 0, 0, -1, 1 };
            int[] dy = { -1, 1, 0, 0 };
            for (int k = 0; k < 4; k++) {
                int nx = x + dx[k];
                int ny = y + dy[k];
                if (nx >= 0 && nx < sizeX && ny >= 0 && ny < sizeY && grid[nx][ny] instanceof SimTile neighbor) {
                    if (neighbor.canStoreHeat() && neighbor.getMaxCellHeat() > 0) {
                        double myPct = (currentCellHeat * 100.0) / maxCellHeat;
                        double nPct = (neighbor.getCurrentCellHeat() * 100.0) / neighbor.getMaxCellHeat();
                        double diff = nPct - myPct;
                        if (Math.abs(diff) > 0.01) {
                            int transfer = (int) Math.round((diff / 2.0) * (maxCellHeat / 100.0));
                            transfer = Math.max(-switchSide, Math.min(switchSide, transfer));
                            if (transfer > 0) {
                                int actual = Math.min(
                                    transfer,
                                    Math.min(neighbor.getCurrentCellHeat(), maxCellHeat - currentCellHeat));
                                if (actual > 0) {
                                    currentCellHeat += actual;
                                    neighbor.alterCellHeat(-actual);
                                }
                            } else if (transfer < 0) {
                                int push = Math.min(
                                    -transfer,
                                    Math.min(
                                        currentCellHeat,
                                        neighbor.getMaxCellHeat() - neighbor.getCurrentCellHeat()));
                                if (push > 0) {
                                    currentCellHeat -= push;
                                    neighbor.alterCellHeat(push);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
