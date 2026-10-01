package com.gtnewhorizons.modularnuclear.common.nuclear.standalone;

/**
 * Calculates optimal power generation and turbine requirements for XLST, XLST-HP, XLST-SC,
 * and Extreme Heat Exchanger (EHE) cascading stages based on GTNH Wiki formulas.
 */
public class TurbineCalculator {

    public enum TurbineMaterial {

        HSS_E("HSS-E", 1.75, 1.16, 76800, 1409106),
        HSS_S("HSS-S", 1.85, 1.23, 76800, 1705018),
        ELVEN_ELEMENTIUM("Elven Elementium", 1.75, 1.16, 48000, 880691),
        ORINARUKON("Oriharukon", 1.55, 1.05, 76800, 1100000),
        SHADOW_METAL("Shadow Metal", 1.45, 0.98, 76800, 950000),
        ICHORIUM("Ichorium", 2.25, 1.52, 460800, 12000000),
        DURANIUM("Duranium", 2.15, 1.66, 1228800, 103778507),
        GAIA_SPIRIT("Gaia Spirit", 1.95, 1.74, 1500000, 7848249),
        ADAMANTIUM("Adamantium", 1.80, 1.58, 1200000, 5360459),
        EXT_UNST_NAQUADAH("Ext. Unst. Naquadah", 1.90, 1.66, 4000000, 20269239),
        COSMIC_NEUTRONIUM("Cosmic Neutronium", 2.00, 1.74, 5000000, 23544749),
        INFINITY("Infinity", 2.40, 2.12, 35000000, 162850748),
        TUNGSTENSTEEL("Tungstensteel", 1.30, 0.88, 48000, 600000),
        TITANIUM("Titanium", 1.20, 0.81, 32000, 400000);

        public final String displayName;
        public final double tightEff;
        public final double looseEff;
        public final double optFlowLargeTight;
        public final double optFlowLargeLoose;

        TurbineMaterial(String displayName, double tightEff, double looseEff, double optFlowLargeTight,
            double optFlowLargeLoose) {
            this.displayName = displayName;
            this.tightEff = tightEff;
            this.looseEff = looseEff;
            this.optFlowLargeTight = optFlowLargeTight;
            this.optFlowLargeLoose = optFlowLargeLoose;
        }

        public static TurbineMaterial fromString(String name) {
            if (name == null) return HSS_E;
            for (TurbineMaterial m : values()) {
                if (m.name()
                    .equalsIgnoreCase(name) || m.displayName.equalsIgnoreCase(name)) {
                    return m;
                }
            }
            return HSS_E;
        }
    }

    public enum TurbineSize {

        SMALL("Small", 1.0 / 3.0),
        NORMAL("Normal", 2.0 / 3.0),
        LARGE("Large", 1.0),
        HUGE("Huge", 4.0 / 3.0);

        public final String displayName;
        public final double multiplier;

        TurbineSize(String displayName, double multiplier) {
            this.displayName = displayName;
            this.multiplier = multiplier;
        }

        public static TurbineSize fromString(String name) {
            if (name == null) return LARGE;
            for (TurbineSize s : values()) {
                if (s.name()
                    .equalsIgnoreCase(name) || s.displayName.equalsIgnoreCase(name)) {
                    return s;
                }
            }
            return LARGE;
        }
    }

    public enum FittingMode {

        TIGHT("Tight Fitting (Max Efficiency)"),
        LOOSE("Loose Fitting (High Throughput)");

        public final String displayName;

        FittingMode(String displayName) {
            this.displayName = displayName;
        }

        public static FittingMode fromString(String name) {
            if (name == null) return TIGHT;
            for (FittingMode m : values()) {
                if (m.name()
                    .equalsIgnoreCase(name) || m.displayName.equalsIgnoreCase(name)) {
                    return m;
                }
            }
            return TIGHT;
        }
    }

    public static class PowerEstimationResult {

        public double totalPowerEUt = 0;
        public double lstPowerEUt = 0;
        public double xlstPowerEUt = 0;
        public double xlstHpPowerEUt = 0;
        public double xlstScPowerEUt = 0;
        public double directPowerEUt = 0;

        // Coolant heat exchange machine info
        public String coolantMachine = "None";
        public String coolantMachineMode = "Inactive";
        public double coolantSteamProduced = 0;
        public double coolantWaterConsumed = 0;
        public double coolantMachineCount = 0;

        // Legacy / compatibility aliases
        public String eheMode = "Inactive";
        public double eheSteamProduced = 0;
        public double eheDistilledWaterConsumed = 0;

        // Turbines needed
        public double lstTurbinesNeeded = 0;
        public double xlstTurbinesNeeded = 0;
        public double xlstHpTurbinesNeeded = 0;
        public double xlstScTurbinesNeeded = 0;
        public double totalTurbinesNeeded = 0;

        public double efficiency = 1.0;
        public double optFlowPerTurbine = 0;
        public boolean isLST = false;
    }

    public static PowerEstimationResult calculatePower(int tier, double regularSteamFlow, double superheatedSteamFlow,
        double supercriticalSteamFlow, double heavyWaterSteamFlow, double hpHeavyWaterSteamFlow, double hotCoolantFlow,
        double directPowerEU, TurbineMaterial material, TurbineSize size, FittingMode mode) {
        PowerEstimationResult res = new PowerEstimationResult();

        boolean isTight = (mode == FittingMode.TIGHT);
        res.efficiency = isTight ? material.tightEff : material.looseEff;
        double baseOptFlow = isTight ? material.optFlowLargeTight : material.optFlowLargeLoose;
        double xlstOptFlow = baseOptFlow * size.multiplier;

        // 1. Coolant Heat Exchanger Stage:
        // EV (tier <= 0): Large Heat Exchanger (LHE) -> converts Hot Coolant to Regular Steam (1L -> 400L Steam)
        // IV (tier == 1): Thermal Boiler -> converts Hot Coolant to Superheated Steam (1L -> 200L SH Steam)
        // LuV (tier == 2): Extreme Heat Exchanger (EHE) -> produces Superheated Steam (1L -> 200L SH Steam)
        // ZPM+ (tier >= 3): Extreme Heat Exchanger (EHE) -> produces Supercritical Steam (1L -> 200L SC Steam if >=
        // 8,000 L/s, else SH Steam)

        double effectiveRegular = regularSteamFlow;
        double effectiveSuperheated = superheatedSteamFlow;
        double effectiveSupercritical = supercriticalSteamFlow;

        if (hotCoolantFlow > 0) {
            double coolantLPerSec = hotCoolantFlow * 20.0;
            if (tier <= com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine.PIPE_TIER_ELECTRUM) {
                // EV: Large Heat Exchanger (LHE)
                // In EV, superheated steam cannot be processed in turbines, so LHE produces Regular Steam (1:400)
                res.coolantMachine = "Large Heat Exchanger";
                res.coolantMachineMode = "Regular Steam (1:400)";
                res.coolantSteamProduced = hotCoolantFlow * 400.0;
                res.coolantWaterConsumed = res.coolantSteamProduced / 160.0;
                // Max throughput per LHE: 1,600 L/s (80 L/t)
                res.coolantMachineCount = Math.max(1.0, Math.ceil(coolantLPerSec / 1600.0));
                effectiveRegular += res.coolantSteamProduced;
            } else if (tier
                == com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine.PIPE_TIER_PLATINUM) {
                    // IV: Thermal Boiler
                    // Fixed rate: 500 L/s (25 L/t) per Thermal Boiler, produces 200L SH Steam per 1L Hot Coolant
                    res.coolantMachine = "Thermal Boiler";
                    res.coolantMachineMode = "Superheated Steam (1:200)";
                    res.coolantSteamProduced = hotCoolantFlow * 200.0;
                    res.coolantWaterConsumed = hotCoolantFlow * 1.25; // 625 L/s water for 500 L/s hot coolant
                    res.coolantMachineCount = Math.max(1.0, Math.ceil(coolantLPerSec / 500.0));
                    effectiveSuperheated += res.coolantSteamProduced;
                } else
                if (tier == com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine.PIPE_TIER_OSMIUM) {
                    // LuV: Extreme Heat Exchanger (EHE)
                    // Supercritical steam cannot be processed until ZPM (XLST-SC), so EHE produces Superheated Steam
                    // (1:200)
                    res.coolantMachine = "Extreme Heat Exchanger";
                    res.coolantMachineMode = "Superheated Steam (1:200)";
                    res.coolantSteamProduced = hotCoolantFlow * 200.0;
                    res.coolantWaterConsumed = res.coolantSteamProduced / 160.0;
                    res.coolantMachineCount = Math.max(1.0, Math.ceil(coolantLPerSec / 128000.0));
                    effectiveSuperheated += res.coolantSteamProduced;
                } else {
                    // ZPM+: Extreme Heat Exchanger (EHE)
                    // >= 8,000 L/s -> Supercritical Steam; < 8,000 L/s -> Superheated Steam
                    res.coolantMachine = "Extreme Heat Exchanger";
                    if (coolantLPerSec >= 8000.0) {
                        res.coolantMachineMode = "Supercritical (>= 8,000 L/s)";
                        res.coolantSteamProduced = hotCoolantFlow * 200.0;
                        effectiveSupercritical += res.coolantSteamProduced;
                    } else {
                        res.coolantMachineMode = "Superheated (< 8,000 L/s)";
                        res.coolantSteamProduced = hotCoolantFlow * 200.0;
                        effectiveSuperheated += res.coolantSteamProduced;
                    }
                    res.coolantWaterConsumed = res.coolantSteamProduced / 160.0;
                    res.coolantMachineCount = Math.max(1.0, Math.ceil(coolantLPerSec / 128000.0));
                }
            // Populate legacy EHE fields for backwards compatibility
            res.eheMode = res.coolantMachineMode;
            res.eheSteamProduced = res.coolantSteamProduced;
            res.eheDistilledWaterConsumed = res.coolantWaterConsumed;
        }

        // 2. Turbine Processing Stage:
        // EV: Large Steam Turbines (LST) exclusively for regular steam. No XLST, no SH or SC turbine.
        // IV+: XLST-HP unlocked for Superheated Steam (1.0 EU/L, cascades 1:1 into Regular Steam)
        // ZPM+: XLST-SC unlocked for Supercritical Steam (1.0 EU/L, cascades 1:1 into Superheated Steam)

        if (tier <= com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine.PIPE_TIER_ELECTRUM) {
            // EV: Large Steam Turbine (LST)
            res.isLST = true;
            double lstOptFlow = xlstOptFlow / 16.0;
            res.optFlowPerTurbine = lstOptFlow;

            // Only regular steam can be processed in LST (at 0.5 EU/L * efficiency)
            if (effectiveRegular > 0) {
                res.lstPowerEUt = effectiveRegular * 0.5 * res.efficiency;
                res.xlstPowerEUt = res.lstPowerEUt; // alias
                res.lstTurbinesNeeded = (lstOptFlow > 0) ? (effectiveRegular / lstOptFlow) : 0;
                res.totalTurbinesNeeded = res.lstTurbinesNeeded;
            }
            // Superheated or supercritical steam cannot be processed in EV
        } else {
            // IV+: XL Turbo Steam Turbines (XLST)
            res.isLST = false;
            res.optFlowPerTurbine = xlstOptFlow;

            // 2a. XLST-SC (Supercritical Steam): Requires ZPM or higher (tier >= 3)
            if (tier >= com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine.PIPE_TIER_QUANTIUM) {
                double scFlowTotal = effectiveSupercritical + hpHeavyWaterSteamFlow;
                if (scFlowTotal > 0) {
                    res.xlstScPowerEUt = scFlowTotal * 1.0 * res.efficiency;
                    res.xlstScTurbinesNeeded = (xlstOptFlow > 0) ? (scFlowTotal / xlstOptFlow) : 0;
                    // Cascades 1:1 into Superheated Steam
                    effectiveSuperheated += effectiveSupercritical;
                    heavyWaterSteamFlow += hpHeavyWaterSteamFlow;
                }
            }

            // 2b. XLST-HP (Superheated Steam): Requires IV or higher (tier >= 1)
            double hpFlowTotal = effectiveSuperheated + heavyWaterSteamFlow;
            if (hpFlowTotal > 0) {
                res.xlstHpPowerEUt = hpFlowTotal * 1.0 * res.efficiency;
                res.xlstHpTurbinesNeeded = (xlstOptFlow > 0) ? (hpFlowTotal / xlstOptFlow) : 0;
                // Regular superheated cascades 1:1 into Regular Steam
                effectiveRegular += effectiveSuperheated;
            }

            // 2c. XLST (Regular Steam):
            if (effectiveRegular > 0) {
                res.xlstPowerEUt = effectiveRegular * 0.5 * res.efficiency;
                res.xlstTurbinesNeeded = (xlstOptFlow > 0) ? (effectiveRegular / xlstOptFlow) : 0;
            }

            res.totalTurbinesNeeded = res.xlstScTurbinesNeeded + res.xlstHpTurbinesNeeded + res.xlstTurbinesNeeded;
        }

        res.directPowerEUt = directPowerEU;
        res.totalPowerEUt = res.xlstScPowerEUt + res.xlstHpPowerEUt + res.xlstPowerEUt + directPowerEU;
        return res;
    }

    public static PowerEstimationResult calculatePower(double regularSteamFlow, double superheatedSteamFlow,
        double supercriticalSteamFlow, double heavyWaterSteamFlow, double hpHeavyWaterSteamFlow, double hotCoolantFlow,
        double directPowerEU, TurbineMaterial material, TurbineSize size, FittingMode mode) {
        return calculatePower(
            com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine.PIPE_TIER_ELECTRUM,
            regularSteamFlow,
            superheatedSteamFlow,
            supercriticalSteamFlow,
            heavyWaterSteamFlow,
            hpHeavyWaterSteamFlow,
            hotCoolantFlow,
            directPowerEU,
            material,
            size,
            mode);
    }

    public static PowerEstimationResult calculatePower(double regularSteamFlow, double superheatedSteamFlow,
        double supercriticalSteamFlow, double heavyWaterSteamFlow, double hpHeavyWaterSteamFlow, double hotCoolantFlow,
        TurbineMaterial material, TurbineSize size, FittingMode mode) {
        return calculatePower(
            regularSteamFlow,
            superheatedSteamFlow,
            supercriticalSteamFlow,
            heavyWaterSteamFlow,
            hpHeavyWaterSteamFlow,
            hotCoolantFlow,
            0.0,
            material,
            size,
            mode);
    }
}
