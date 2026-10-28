package com.gtnewhorizons.modularnuclear.common.nuclear.standalone;

/**
 * Calculates optimal power generation and turbine requirements for XLST, XLST-HP, XLST-SC,
 * and Extreme Heat Exchanger (EHE) cascading stages based on GTNH Wiki formulas.
 */
public class TurbineCalculator {

    /** An XL turbine's flow and power relative to a large turbine with the same rotor */
    public static final double XL_FLOW_MULTIPLIER = 16.0;

    public static class TurbineMaterial {

        private static final java.util.Map<String, TurbineMaterial> REGISTRY = new java.util.LinkedHashMap<>();

        // Rotor stats as GregTech defines them (tool speed, tool quality, steam flow multiplier); efficiency and
        // flow follow gregtech.api.util.TurbineStatCalculator. TurbineCalculatorGregTechTest checks them against
        // the game.
        public static final TurbineMaterial HSS_E = register(new TurbineMaterial("HSS_E", "HSS-E", 32f, 7, 1f));
        public static final TurbineMaterial HSS_S = register(new TurbineMaterial("HSS_S", "HSS-S", 32f, 8, 1f));
        public static final TurbineMaterial ELVEN_ELEMENTIUM = register(
            new TurbineMaterial("ELVEN_ELEMENTIUM", "Elven Elementium", 20f, 7, 1f));
        public static final TurbineMaterial ORINARUKON = register(
            new TurbineMaterial("ORINARUKON", "Oriharukon", 32f, 5, 1f));
        public static final TurbineMaterial SHADOW_METAL = register(
            new TurbineMaterial("SHADOW_METAL", "Shadow Metal", 32f, 4, 1f));
        public static final TurbineMaterial ICHORIUM = register(
            new TurbineMaterial("ICHORIUM", "Ichorium", 32f, 12, 6f));
        public static final TurbineMaterial DURANIUM = register(
            new TurbineMaterial("DURANIUM", "Duranium", 32f, 11, 16f));
        public static final TurbineMaterial GAIA_SPIRIT = register(
            new TurbineMaterial("GAIA_SPIRIT", "Gaia Spirit", 32f, 12, 1f));
        public static final TurbineMaterial ADAMANTIUM = register(
            new TurbineMaterial("ADAMANTIUM", "Adamantium", 32f, 10, 1f));
        public static final TurbineMaterial EXT_UNST_NAQUADAH = register(
            new TurbineMaterial("EXT_UNST_NAQUADAH", "Ext. Unst. Naquadah", 100f, 11, 1f));
        public static final TurbineMaterial COSMIC_NEUTRONIUM = register(
            new TurbineMaterial("COSMIC_NEUTRONIUM", "Cosmic Neutronium", 96f, 12, 6f));
        public static final TurbineMaterial INFINITY = register(
            new TurbineMaterial("INFINITY", "Infinity", 256f, 17, 1f));
        public static final TurbineMaterial TUNGSTENSTEEL = register(
            new TurbineMaterial("TUNGSTENSTEEL", "Tungstensteel", 8f, 4, 1f));
        public static final TurbineMaterial TITANIUM = register(new TurbineMaterial("TITANIUM", "Titanium", 7f, 3, 1f));

        public final String name;
        public final String displayName;
        public final float toolSpeed;
        public final int toolQuality;
        public final float steamMultiplier;
        // A Large rotor's efficiency and its XL turbine's optimal steam flow (L/t), for listings
        public final double tightEff;
        public final double looseEff;
        public final double optFlowLargeTight;
        public final double optFlowLargeLoose;

        public TurbineMaterial(String name, String displayName, float toolSpeed, int toolQuality,
            float steamMultiplier) {
            this.name = name != null ? name : "UNKNOWN";
            this.displayName = displayName != null ? displayName : this.name;
            this.toolSpeed = toolSpeed;
            this.toolQuality = toolQuality;
            this.steamMultiplier = steamMultiplier;
            this.tightEff = efficiency(TurbineSize.LARGE, FittingMode.TIGHT);
            this.looseEff = efficiency(TurbineSize.LARGE, FittingMode.LOOSE);
            this.optFlowLargeTight = xlOptimalSteamFlow(TurbineSize.LARGE, FittingMode.TIGHT);
            this.optFlowLargeLoose = xlOptimalSteamFlow(TurbineSize.LARGE, FittingMode.LOOSE);
        }

        private float baseEfficiency(TurbineSize size) {
            return 0.5F + (0.5F + (size.baseDamage + toolQuality)) * 0.1F;
        }

        /** Steam efficiency of a rotor of this size, as TurbineStatCalculator computes it. */
        public double efficiency(TurbineSize size, FittingMode mode) {
            float base = baseEfficiency(size);
            if (mode == FittingMode.TIGHT) return base;
            return (float) (-0.2F + Math.round(base * 85.0F) * 0.01D) * 0.9F;
        }

        /** Optimal steam flow (L/t) of one rotor of this size in a large turbine. */
        public double optimalSteamFlow(TurbineSize size, FittingMode mode) {
            float flow = size.speedMultiplier * toolSpeed * 50.0F * steamMultiplier;
            if (mode == FittingMode.TIGHT) return flow;
            return 3.0F * flow * (float) Math.pow(1.1F, (baseEfficiency(size) - 0.8F) * 20.0F);
        }

        /** Optimal steam flow (L/t) of an XL turbine, which runs 12 rotors at 16x a large turbine's flow. */
        public double xlOptimalSteamFlow(TurbineSize size, FittingMode mode) {
            return XL_FLOW_MULTIPLIER * optimalSteamFlow(size, mode);
        }

        public static synchronized TurbineMaterial register(TurbineMaterial mat) {
            if (mat == null) return null;
            if (REGISTRY != null) {
                REGISTRY.put(mat.name.toUpperCase(java.util.Locale.ROOT), mat);
            }
            return mat;
        }

        public String name() {
            return name;
        }

        public String getDisplayName() {
            return displayName;
        }

        public double getTightEff() {
            return tightEff;
        }

        public double getLooseEff() {
            return looseEff;
        }

        public double getOptFlowLargeTight() {
            return optFlowLargeTight;
        }

        public double getOptFlowLargeLoose() {
            return optFlowLargeLoose;
        }

        public static synchronized java.util.List<TurbineMaterial> getAll() {
            return new java.util.ArrayList<>(REGISTRY.values());
        }

        public static TurbineMaterial[] values() {
            java.util.List<TurbineMaterial> all = getAll();
            return all.toArray(new TurbineMaterial[0]);
        }

        public static TurbineMaterial valueOf(String name) {
            return fromString(name);
        }

        public static TurbineMaterial fromString(String name) {
            if (name == null || name.trim()
                .isEmpty()) return HSS_E;
            String trimmed = name.trim();
            for (TurbineMaterial m : getAll()) {
                if (m.name.equalsIgnoreCase(trimmed) || m.displayName.equalsIgnoreCase(trimmed)) {
                    return m;
                }
            }
            String cleanTrimmed = cleanString(trimmed);
            for (TurbineMaterial m : getAll()) {
                if (cleanTrimmed.equalsIgnoreCase(cleanString(m.name))
                    || cleanTrimmed.equalsIgnoreCase(cleanString(m.displayName))) {
                    return m;
                }
            }
            return HSS_E;
        }

        private static String cleanString(String s) {
            if (s == null) return "";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (Character.isLetterOrDigit(c)) {
                    sb.append(Character.toUpperCase(c));
                }
            }
            return sb.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TurbineMaterial that = (TurbineMaterial) o;
            return name.equalsIgnoreCase(that.name);
        }

        @Override
        public int hashCode() {
            return name.toLowerCase()
                .hashCode();
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public enum TurbineSize {

        // GregTech's rotor speed multiplier and base damage (ToolTurbineSmall ... ToolTurbineHuge)
        SMALL("Small", 1.0F, 0.0F),
        NORMAL("Normal", 2.0F, 2.5F),
        LARGE("Large", 3.0F, 5.0F),
        HUGE("Huge", 4.0F, 7.5F);

        public final String displayName;
        public final float speedMultiplier;
        public final float baseDamage;
        /** Flow relative to a Large rotor */
        public final double multiplier;

        TurbineSize(String displayName, float speedMultiplier, float baseDamage) {
            this.displayName = displayName;
            this.speedMultiplier = speedMultiplier;
            this.baseDamage = baseDamage;
            this.multiplier = speedMultiplier / 3.0;
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

        TIGHT("Tight fitting (max efficiency)"),
        LOOSE("Loose fitting (high throughput)");

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

    public static class MachineLimits {

        public int maxThermalBoilers = -1;
        public int maxLhe = -1;
        public int maxEhe = -1;
        public int maxLst = -1;
        public int maxXlst = -1;
        public int maxXlstHp = -1;
        public int maxXlstSc = -1;
        public int maxTurbines = -1;

        public MachineLimits() {}

        public MachineLimits(int maxThermalBoilers, int maxLhe, int maxEhe, int maxLst, int maxXlst, int maxXlstHp,
            int maxXlstSc, int maxTurbines) {
            this.maxThermalBoilers = maxThermalBoilers;
            this.maxLhe = maxLhe;
            this.maxEhe = maxEhe;
            this.maxLst = maxLst;
            this.maxXlst = maxXlst;
            this.maxXlstHp = maxXlstHp;
            this.maxXlstSc = maxXlstSc;
            this.maxTurbines = maxTurbines;
        }

        public MachineLimits copy() {
            return new MachineLimits(
                maxThermalBoilers,
                maxLhe,
                maxEhe,
                maxLst,
                maxXlst,
                maxXlstHp,
                maxXlstSc,
                maxTurbines);
        }

        public boolean hasAnyLimit() {
            return maxThermalBoilers >= 0 || maxLhe >= 0
                || maxEhe >= 0
                || maxLst >= 0
                || maxXlst >= 0
                || maxXlstHp >= 0
                || maxXlstSc >= 0
                || maxTurbines >= 0;
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

        // Turbines needed
        public double lstTurbinesNeeded = 0;
        public double xlstTurbinesNeeded = 0;
        public double xlstHpTurbinesNeeded = 0;
        public double xlstScTurbinesNeeded = 0;
        public double totalTurbinesNeeded = 0;

        public double efficiency = 1.0;
        public double optFlowPerTurbine = 0;
        public boolean isLST = false;

        // Machine limit and voiding diagnostics
        public boolean machineLimitExceeded = false;
        public double voidedCoolantFlow = 0;
        public double voidedSupercriticalSteam = 0;
        public double voidedSuperheatedSteam = 0;
        public double voidedRegularSteam = 0;
        public double totalVoidedSteamFlow = 0;
    }

    public static PowerEstimationResult calculatePower(int tier, double regularSteamFlow, double superheatedSteamFlow,
        double supercriticalSteamFlow, double heavyWaterSteamFlow, double hpHeavyWaterSteamFlow, double hotCoolantFlow,
        double directPowerEU, TurbineMaterial material, TurbineSize size, FittingMode mode) {
        return calculatePower(
            tier,
            regularSteamFlow,
            superheatedSteamFlow,
            supercriticalSteamFlow,
            heavyWaterSteamFlow,
            hpHeavyWaterSteamFlow,
            hotCoolantFlow,
            directPowerEU,
            material,
            size,
            mode,
            null);
    }

    public static PowerEstimationResult calculatePower(int tier, double regularSteamFlow, double superheatedSteamFlow,
        double supercriticalSteamFlow, double heavyWaterSteamFlow, double hpHeavyWaterSteamFlow, double hotCoolantFlow,
        double directPowerEU, TurbineMaterial material, TurbineSize size, FittingMode mode, MachineLimits limits) {
        PowerEstimationResult res = new PowerEstimationResult();

        double eff = material.efficiency(size, mode);
        res.efficiency = eff;
        double xlstOptFlow = material.xlOptimalSteamFlow(size, mode);

        // 1. Coolant Heat Exchanger Stage:
        // EV (tier <= 0): Large Heat Exchanger (LHE) -> converts Hot Coolant to Regular Steam (1L -> 400L Steam)
        // IV (tier == 1): Thermal Boiler -> converts Hot Coolant to Superheated Steam (1L -> 200L SH Steam)
        // LuV (tier == 2): Extreme Heat Exchanger (EHE) -> produces Superheated Steam (1L -> 200L SH Steam)
        // ZPM+ (tier >= 3): Extreme Heat Exchanger (EHE) -> produces Supercritical Steam (1L -> 200L SC Steam if >=
        // 8,000 L/s, else SH Steam)

        double effectiveRegular = regularSteamFlow + heavyWaterSteamFlow;
        double effectiveSuperheated = superheatedSteamFlow;
        double effectiveSupercritical = supercriticalSteamFlow;

        if (hotCoolantFlow > 0) {
            double coolantLPerSec = hotCoolantFlow * 20.0;
            if (tier <= com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine.PIPE_TIER_ELECTRUM) {
                // EV: Large Heat Exchanger (LHE)
                // In EV, superheated steam cannot be processed in turbines, so LHE produces Regular Steam (1:400)
                res.coolantMachine = "Large Heat Exchanger";
                res.coolantMachineMode = "Regular Steam (1:400)";
                int maxLhe = limits != null ? limits.maxLhe : -1;
                double usableCoolant = hotCoolantFlow;
                if (maxLhe >= 0) {
                    double maxCoolantCapacity = maxLhe * 80.0; // 1,600 L/s = 80 L/t per LHE
                    if (hotCoolantFlow > maxCoolantCapacity) {
                        usableCoolant = maxCoolantCapacity;
                        res.voidedCoolantFlow = hotCoolantFlow - usableCoolant;
                        res.machineLimitExceeded = true;
                    }
                    res.coolantMachineCount = maxLhe;
                } else {
                    res.coolantMachineCount = Math.max(1.0, Math.ceil(coolantLPerSec / 1600.0));
                }
                res.coolantSteamProduced = usableCoolant * 400.0;
                res.coolantWaterConsumed = res.coolantSteamProduced / 160.0;
                effectiveRegular += res.coolantSteamProduced;
            } else if (tier
                == com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine.PIPE_TIER_PLATINUM) {
                    // IV: Thermal Boiler
                    // Fixed rate: 500 L/s (25 L/t) per Thermal Boiler, produces 200L SH Steam per 1L Hot Coolant
                    res.coolantMachine = "Thermal Boiler";
                    res.coolantMachineMode = "Superheated Steam (1:200)";
                    int maxBoilers = limits != null ? limits.maxThermalBoilers : -1;
                    double usableCoolant = hotCoolantFlow;
                    if (maxBoilers >= 0) {
                        double maxCoolantCapacity = maxBoilers * 25.0; // 500 L/s = 25 L/t per boiler
                        if (hotCoolantFlow > maxCoolantCapacity) {
                            usableCoolant = maxCoolantCapacity;
                            res.voidedCoolantFlow = hotCoolantFlow - usableCoolant;
                            res.machineLimitExceeded = true;
                        }
                        res.coolantMachineCount = maxBoilers;
                    } else {
                        res.coolantMachineCount = Math.max(1.0, Math.ceil(coolantLPerSec / 500.0));
                    }
                    res.coolantSteamProduced = usableCoolant * 200.0;
                    res.coolantWaterConsumed = usableCoolant * 1.25; // 625 L/s water for 500 L/s hot coolant
                    effectiveSuperheated += res.coolantSteamProduced;
                } else
                if (tier == com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine.PIPE_TIER_OSMIUM) {
                    // LuV: Extreme Heat Exchanger (EHE)
                    // Supercritical steam cannot be processed until ZPM (XLST-SC), so EHE produces Superheated Steam
                    // (1:200)
                    res.coolantMachine = "Extreme Heat Exchanger";
                    res.coolantMachineMode = "Superheated Steam (1:200)";
                    int maxEhe = limits != null ? limits.maxEhe : -1;
                    double usableCoolant = hotCoolantFlow;
                    if (maxEhe >= 0) {
                        double maxCoolantCapacity = maxEhe * 6400.0; // 128,000 L/s = 6,400 L/t per EHE
                        if (hotCoolantFlow > maxCoolantCapacity) {
                            usableCoolant = maxCoolantCapacity;
                            res.voidedCoolantFlow = hotCoolantFlow - usableCoolant;
                            res.machineLimitExceeded = true;
                        }
                        res.coolantMachineCount = maxEhe;
                    } else {
                        res.coolantMachineCount = Math.max(1.0, Math.ceil(coolantLPerSec / 128000.0));
                    }
                    res.coolantSteamProduced = usableCoolant * 200.0;
                    res.coolantWaterConsumed = res.coolantSteamProduced / 160.0;
                    effectiveSuperheated += res.coolantSteamProduced;
                } else {
                    // ZPM+: Extreme Heat Exchanger (EHE)
                    // >= 8,000 L/s -> Supercritical Steam; < 8,000 L/s -> Superheated Steam
                    res.coolantMachine = "Extreme Heat Exchanger";
                    int maxEhe = limits != null ? limits.maxEhe : -1;
                    double usableCoolant = hotCoolantFlow;
                    if (maxEhe >= 0) {
                        double maxCoolantCapacity = maxEhe * 6400.0;
                        if (hotCoolantFlow > maxCoolantCapacity) {
                            usableCoolant = maxCoolantCapacity;
                            res.voidedCoolantFlow = hotCoolantFlow - usableCoolant;
                            res.machineLimitExceeded = true;
                        }
                        res.coolantMachineCount = maxEhe;
                    } else {
                        res.coolantMachineCount = Math.max(1.0, Math.ceil(coolantLPerSec / 128000.0));
                    }
                    double usableLPerSec = usableCoolant * 20.0;
                    if (usableLPerSec >= 8000.0) {
                        res.coolantMachineMode = "Supercritical (>= 8,000 L/s)";
                        res.coolantSteamProduced = usableCoolant * 200.0;
                        effectiveSupercritical += res.coolantSteamProduced;
                    } else {
                        res.coolantMachineMode = "Superheated (< 8,000 L/s)";
                        res.coolantSteamProduced = usableCoolant * 200.0;
                        effectiveSuperheated += res.coolantSteamProduced;
                    }
                    res.coolantWaterConsumed = res.coolantSteamProduced / 160.0;
                }
        }

        // 2. Turbine Processing Stage:
        // EV: Large Steam Turbines (LST) exclusively for regular steam. No XLST, no SH or SC turbine.
        // IV+: XLST-HP unlocked for Superheated Steam (1.0 EU/L, cascades 1:1 into Regular Steam)
        // ZPM+: XLST-SC unlocked for Supercritical Steam (1.0 EU/L, cascades 1:1 into Superheated Steam)

        int maxLst = limits != null ? limits.maxLst : -1;
        int maxXlst = limits != null ? limits.maxXlst : -1;
        int maxXlstHp = limits != null ? limits.maxXlstHp : -1;
        int maxXlstSc = limits != null ? limits.maxXlstSc : -1;
        int maxTurbines = limits != null ? limits.maxTurbines : -1;

        if (tier <= com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine.PIPE_TIER_ELECTRUM) {
            // EV: Large Steam Turbine (LST)
            res.isLST = true;
            double lstOptFlow = material.optimalSteamFlow(size, mode);
            res.optFlowPerTurbine = lstOptFlow;

            // Only regular steam can be processed in LST (at 0.5 EU/L * efficiency)
            if (effectiveRegular > 0) {
                int capLst = maxLst;
                if (maxTurbines >= 0) {
                    capLst = (capLst >= 0) ? Math.min(capLst, maxTurbines) : maxTurbines;
                }
                double neededTurbines = (lstOptFlow > 0) ? (effectiveRegular / lstOptFlow) : 0;
                double processedRegular = effectiveRegular;
                if (capLst >= 0 && neededTurbines > capLst) {
                    processedRegular = capLst * lstOptFlow;
                    res.voidedRegularSteam = effectiveRegular - processedRegular;
                    res.machineLimitExceeded = true;
                    res.lstTurbinesNeeded = capLst;
                } else {
                    res.lstTurbinesNeeded = neededTurbines;
                }
                res.lstPowerEUt = processedRegular * 0.5 * res.efficiency;
                res.xlstPowerEUt = res.lstPowerEUt; // alias
                res.totalTurbinesNeeded = res.lstTurbinesNeeded;
            }
            // Superheated or supercritical steam cannot be processed in EV: it is wasted
            res.voidedSuperheatedSteam = effectiveSuperheated;
            res.voidedSupercriticalSteam = effectiveSupercritical + hpHeavyWaterSteamFlow;
        } else {
            // IV+: XL Turbo Steam Turbines (XLST)
            res.isLST = false;
            res.optFlowPerTurbine = xlstOptFlow;
            int remTurbines = maxTurbines;

            // 2a. XLST-SC (Supercritical Steam): Requires ZPM or higher (tier >= 3)
            if (tier >= com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine.PIPE_TIER_QUANTIUM) {
                double scFlowTotal = effectiveSupercritical + hpHeavyWaterSteamFlow;
                if (scFlowTotal > 0) {
                    int capSc = maxXlstSc;
                    if (remTurbines >= 0) {
                        capSc = (capSc >= 0) ? Math.min(capSc, remTurbines) : remTurbines;
                    }
                    double neededSc = (xlstOptFlow > 0) ? (scFlowTotal / xlstOptFlow) : 0;
                    double processedSc = scFlowTotal;
                    if (capSc >= 0 && neededSc > capSc) {
                        processedSc = capSc * xlstOptFlow;
                        res.voidedSupercriticalSteam = scFlowTotal - processedSc;
                        res.machineLimitExceeded = true;
                        res.xlstScTurbinesNeeded = capSc;
                    } else {
                        res.xlstScTurbinesNeeded = neededSc;
                    }
                    if (remTurbines >= 0) {
                        remTurbines = Math.max(0, remTurbines - (int) Math.ceil(res.xlstScTurbinesNeeded));
                    }
                    res.xlstScPowerEUt = processedSc * 1.0 * res.efficiency;
                    // Only processed SC cascades 1:1 into Superheated Steam
                    effectiveSuperheated += processedSc;
                }
            } else {
                // Below ZPM there is no supercritical turbine: the steam is wasted
                res.voidedSupercriticalSteam = effectiveSupercritical + hpHeavyWaterSteamFlow;
            }

            // 2b. XLST-HP (Superheated Steam): Requires IV or higher (tier >= 1)
            double hpFlowTotal = effectiveSuperheated;
            if (hpFlowTotal > 0) {
                int capHp = maxXlstHp;
                if (remTurbines >= 0) {
                    capHp = (capHp >= 0) ? Math.min(capHp, remTurbines) : remTurbines;
                }
                double neededHp = (xlstOptFlow > 0) ? (hpFlowTotal / xlstOptFlow) : 0;
                double processedHp = hpFlowTotal;
                if (capHp >= 0 && neededHp > capHp) {
                    processedHp = capHp * xlstOptFlow;
                    res.voidedSuperheatedSteam = hpFlowTotal - processedHp;
                    res.machineLimitExceeded = true;
                    res.xlstHpTurbinesNeeded = capHp;
                } else {
                    res.xlstHpTurbinesNeeded = neededHp;
                }
                if (remTurbines >= 0) {
                    remTurbines = Math.max(0, remTurbines - (int) Math.ceil(res.xlstHpTurbinesNeeded));
                }
                res.xlstHpPowerEUt = processedHp * 1.0 * res.efficiency;
                // Only processed HP cascades 1:1 into Regular Steam
                effectiveRegular += processedHp;
            }

            // 2c. XLST (Regular Steam):
            if (effectiveRegular > 0) {
                int capReg = maxXlst;
                if (remTurbines >= 0) {
                    capReg = (capReg >= 0) ? Math.min(capReg, remTurbines) : remTurbines;
                }
                double neededReg = (xlstOptFlow > 0) ? (effectiveRegular / xlstOptFlow) : 0;
                double processedReg = effectiveRegular;
                if (capReg >= 0 && neededReg > capReg) {
                    processedReg = capReg * xlstOptFlow;
                    res.voidedRegularSteam = effectiveRegular - processedReg;
                    res.machineLimitExceeded = true;
                    res.xlstTurbinesNeeded = capReg;
                } else {
                    res.xlstTurbinesNeeded = neededReg;
                }
                res.xlstPowerEUt = processedReg * 0.5 * res.efficiency;
            }

            res.totalTurbinesNeeded = res.xlstScTurbinesNeeded + res.xlstHpTurbinesNeeded + res.xlstTurbinesNeeded;
        }

        res.totalVoidedSteamFlow = res.voidedSupercriticalSteam + res.voidedSuperheatedSteam + res.voidedRegularSteam;
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

    public static class ScenarioTurbineConfig {

        public final TurbineMaterial material;
        public final TurbineSize size;
        public final FittingMode mode;
        public final double efficiency;
        public final String description;

        public ScenarioTurbineConfig(TurbineMaterial material, TurbineSize size, FittingMode mode) {
            this.material = material;
            this.size = size;
            this.mode = mode;
            this.efficiency = material.efficiency(size, mode);
            this.description = size.displayName + " "
                + material.displayName
                + " ("
                + Math.round(efficiency * 100)
                + "%)";
        }
    }

    public static class ScenarioHypotheticalResult {

        public PowerEstimationResult tightResult;
        public PowerEstimationResult looseResult;
        public ScenarioTurbineConfig tightConfig;
        public ScenarioTurbineConfig looseConfig;
        public double turbineReductionRatio;
        public double powerReductionRatio;
    }

    public static ScenarioTurbineConfig getDefaultTightTurbine(int tier) {
        return switch (tier) {
            case com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine.PIPE_TIER_ELECTRUM -> new ScenarioTurbineConfig(
                TurbineMaterial.ORINARUKON,
                TurbineSize.NORMAL,
                FittingMode.TIGHT);
            case com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine.PIPE_TIER_PLATINUM -> new ScenarioTurbineConfig(
                TurbineMaterial.ICHORIUM,
                TurbineSize.LARGE,
                FittingMode.TIGHT);
            case com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine.PIPE_TIER_OSMIUM -> new ScenarioTurbineConfig(
                TurbineMaterial.DURANIUM,
                TurbineSize.LARGE,
                FittingMode.TIGHT);
            case com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine.PIPE_TIER_QUANTIUM, com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine.PIPE_TIER_FLUXED_ELECTRUM -> new ScenarioTurbineConfig(
                TurbineMaterial.DURANIUM,
                TurbineSize.HUGE,
                FittingMode.TIGHT);
            case com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine.PIPE_TIER_BLACK_PLUTONIUM -> new ScenarioTurbineConfig(
                TurbineMaterial.INFINITY,
                TurbineSize.HUGE,
                FittingMode.TIGHT);
            default -> new ScenarioTurbineConfig(TurbineMaterial.ORINARUKON, TurbineSize.NORMAL, FittingMode.TIGHT);
        };
    }

    public static ScenarioTurbineConfig getDefaultLooseTurbine(int tier) {
        return switch (tier) {
            case com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine.PIPE_TIER_ELECTRUM -> new ScenarioTurbineConfig(
                TurbineMaterial.ORINARUKON,
                TurbineSize.NORMAL,
                FittingMode.LOOSE);
            case com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine.PIPE_TIER_PLATINUM -> new ScenarioTurbineConfig(
                TurbineMaterial.HSS_S,
                TurbineSize.LARGE,
                FittingMode.LOOSE);
            case com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine.PIPE_TIER_OSMIUM -> new ScenarioTurbineConfig(
                TurbineMaterial.GAIA_SPIRIT,
                TurbineSize.LARGE,
                FittingMode.LOOSE);
            case com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine.PIPE_TIER_QUANTIUM, com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine.PIPE_TIER_FLUXED_ELECTRUM -> new ScenarioTurbineConfig(
                TurbineMaterial.DURANIUM,
                TurbineSize.HUGE,
                FittingMode.LOOSE);
            case com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine.PIPE_TIER_BLACK_PLUTONIUM -> new ScenarioTurbineConfig(
                TurbineMaterial.INFINITY,
                TurbineSize.HUGE,
                FittingMode.LOOSE);
            default -> new ScenarioTurbineConfig(TurbineMaterial.ORINARUKON, TurbineSize.NORMAL, FittingMode.LOOSE);
        };
    }

    public static ScenarioHypotheticalResult calculateBothScenarios(int tier, double regularSteamFlow,
        double superheatedSteamFlow, double supercriticalSteamFlow, double heavyWaterSteamFlow,
        double hpHeavyWaterSteamFlow, double hotCoolantFlow, double directPowerEU, ScenarioTurbineConfig tightOverride,
        ScenarioTurbineConfig looseOverride) {
        return calculateBothScenarios(
            tier,
            regularSteamFlow,
            superheatedSteamFlow,
            supercriticalSteamFlow,
            heavyWaterSteamFlow,
            hpHeavyWaterSteamFlow,
            hotCoolantFlow,
            directPowerEU,
            tightOverride,
            looseOverride,
            null);
    }

    public static ScenarioHypotheticalResult calculateBothScenarios(int tier, double regularSteamFlow,
        double superheatedSteamFlow, double supercriticalSteamFlow, double heavyWaterSteamFlow,
        double hpHeavyWaterSteamFlow, double hotCoolantFlow, double directPowerEU, ScenarioTurbineConfig tightOverride,
        ScenarioTurbineConfig looseOverride, MachineLimits limits) {

        ScenarioTurbineConfig tightCfg = tightOverride != null ? tightOverride : getDefaultTightTurbine(tier);
        ScenarioTurbineConfig looseCfg = looseOverride != null ? looseOverride : getDefaultLooseTurbine(tier);

        ScenarioHypotheticalResult res = new ScenarioHypotheticalResult();
        res.tightConfig = tightCfg;
        res.looseConfig = looseCfg;

        res.tightResult = calculatePower(
            tier,
            regularSteamFlow,
            superheatedSteamFlow,
            supercriticalSteamFlow,
            heavyWaterSteamFlow,
            hpHeavyWaterSteamFlow,
            hotCoolantFlow,
            directPowerEU,
            tightCfg.material,
            tightCfg.size,
            tightCfg.mode,
            limits);

        res.looseResult = calculatePower(
            tier,
            regularSteamFlow,
            superheatedSteamFlow,
            supercriticalSteamFlow,
            heavyWaterSteamFlow,
            hpHeavyWaterSteamFlow,
            hotCoolantFlow,
            directPowerEU,
            looseCfg.material,
            looseCfg.size,
            looseCfg.mode,
            limits);

        if (res.looseResult.totalTurbinesNeeded > 0) {
            res.turbineReductionRatio = res.tightResult.totalTurbinesNeeded / res.looseResult.totalTurbinesNeeded;
        } else {
            res.turbineReductionRatio = 1.0;
        }

        if (res.tightResult.totalPowerEUt > 0) {
            res.powerReductionRatio = res.looseResult.totalPowerEUt / res.tightResult.totalPowerEUt;
        } else {
            res.powerReductionRatio = 1.0;
        }

        return res;
    }
}
