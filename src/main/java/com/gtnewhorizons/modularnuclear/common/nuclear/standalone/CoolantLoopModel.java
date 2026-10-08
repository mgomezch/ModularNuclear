package com.gtnewhorizons.modularnuclear.common.nuclear.standalone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;

/**
 * High-fidelity physical simulation model of an external closed Coolant Loop.
 * Integrates hydrodynamic friction (Darcy-Weisbach / polynomial scaling),
 * Dittus-Boelter convective heat extraction, secondary PHE steam generation (1:160 expansion),
 * radiolytic gas generation, and tier-appropriate pipe/tank material safety limits.
 */
public class CoolantLoopModel {

    public enum CoolingMode {

        MODULAR("Modular (Per-Cell Cooling)"),
        CONDUCTIVE("Conductive (Sub-boiling Hatches)"),
        CONVECTIVE_LOOP("Convective (Closed Coolant Loop)");

        public final String displayName;

        CoolingMode(String displayName) {
            this.displayName = displayName;
        }

        public static CoolingMode fromString(String str) {
            if (str == null) return MODULAR;
            String s = str.trim();
            for (CoolingMode mode : values()) {
                if (mode.name()
                    .equalsIgnoreCase(s) || mode.displayName.equalsIgnoreCase(s)) {
                    return mode;
                }
            }
            if (containsSub(s, "CONVECTIVE") || containsSub(s, "LOOP")) {
                return CONVECTIVE_LOOP;
            }
            if (containsSub(s, "CONDUCTIVE") || containsSub(s, "HATCH")) {
                return CONDUCTIVE;
            }
            return MODULAR;
        }
    }

    public enum LoopMaterial {

        STEEL("Steel", 35.0, 2226.85, 2500, "LV"),
        STAINLESS_STEEL("Stainless Steel", 70.0, 2726.85, 3000, "MV"),
        TITANIUM("Titanium", 140.0, 4726.85, 5000, "EV"),
        TUNGSTENSTEEL("Tungstensteel", 280.0, 7226.85, 7500, "IV"),
        OSMIUM("Osmium", 600.0, 3032.85, 3306, "LuV"),
        NEUTRONIUM("Neutronium", 3000.0, 99726.85, 100000, "ZPM");

        public final String displayName;
        public final double maxPressureBar;
        public final double maxTemperatureCelsius;
        public final int maxTemperatureKelvin;
        public final String tierUnlocked;

        LoopMaterial(String displayName, double maxPressureBar, double maxTemperatureCelsius, int maxTemperatureKelvin,
            String tierUnlocked) {
            this.displayName = displayName;
            this.maxPressureBar = maxPressureBar;
            this.maxTemperatureCelsius = maxTemperatureCelsius;
            this.maxTemperatureKelvin = maxTemperatureKelvin;
            this.tierUnlocked = tierUnlocked;
        }

        /**
         * Checks if this material is physically allowed for convective loop cooling in the given reactor tier.
         * Tier 1 (Electrum / EV): Convective loop cooling not allowed in reactor structure.
         * Tier 2+ (Platinum / IV, Osmium / LuV, Quantium / ZPM+): All valid loop materials (including Neutronium)
         * can physically be installed and will function.
         */
        public boolean isAllowedInReactorTier(int reactorTier) {
            return reactorTier >= NuclearSimulationEngine.PIPE_TIER_PLATINUM;
        }

        /**
         * Progression recommendation check for automated layout optimization searches.
         * In automated searches for Tier 2 reactors, Neutronium is excluded because a player
         * who has reached ZPM to craft Neutronium would build a larger Tier 3 reactor instead.
         * For IV Platinum, Osmium (unlocked at LuV) is also excluded.
         */
        public boolean isProgressionAppropriate(int reactorTier) {
            if (reactorTier < NuclearSimulationEngine.PIPE_TIER_PLATINUM) {
                return false;
            }
            if (reactorTier == NuclearSimulationEngine.PIPE_TIER_PLATINUM) {
                return this != NEUTRONIUM && this != OSMIUM;
            }
            if (reactorTier <= NuclearSimulationEngine.PIPE_TIER_OSMIUM) {
                // Progression filter: exclude Neutronium from automated Tier 2 search space
                return this != NEUTRONIUM;
            }
            return true;
        }

        public static LoopMaterial fromString(String str) {
            if (str == null) return TITANIUM;
            String s = str.trim();
            for (LoopMaterial mat : values()) {
                if (mat.name()
                    .equalsIgnoreCase(s) || mat.displayName.equalsIgnoreCase(s)) {
                    return mat;
                }
            }
            if (containsSub(s, "STAINLESS")) return STAINLESS_STEEL;
            if (containsSub(s, "TUNGSTEN")) return TUNGSTENSTEEL;
            if (containsSub(s, "OSMIUM")) return OSMIUM;
            if (containsSub(s, "TITAN")) return TITANIUM;
            if (containsSub(s, "NEUTRON")) return NEUTRONIUM;
            if (containsSub(s, "STEEL")) return STEEL;
            return TITANIUM;
        }
    }

    public enum LoopPipeSize {

        TINY("Tiny", 0.05, 0.5),
        SMALL("Small", 0.075, 0.75),
        NORMAL("Normal", 0.10, 1.0),
        LARGE("Large", 0.15, 1.5),
        HUGE("Huge", 0.20, 2.0);

        public final String displayName;
        public final double diameterMeters;
        public final double relativeCapacity;

        LoopPipeSize(String displayName, double diameterMeters, double relativeCapacity) {
            this.displayName = displayName;
            this.diameterMeters = diameterMeters;
            this.relativeCapacity = relativeCapacity;
        }

        public static LoopPipeSize fromString(String str) {
            if (str == null) return NORMAL;
            String s = str.trim();
            for (LoopPipeSize size : values()) {
                if (size.name()
                    .equalsIgnoreCase(s) || size.displayName.equalsIgnoreCase(s)) {
                    return size;
                }
            }
            if (containsSub(s, "TINY")) return TINY;
            if (containsSub(s, "SMALL")) return SMALL;
            if (containsSub(s, "HUGE")) return HUGE;
            if (containsSub(s, "LARGE")) return LARGE;
            return NORMAL;
        }
    }

    public enum CoolantFluidType {

        DISTILLED_WATER("Distilled Water", 1000.0, 4184.0, 0.001, 0.60, "Deuterium"),
        HEAVY_WATER("Heavy Water", 1105.0, 4220.0, 0.00125, 0.59, "Tritium"),
        MOLTEN_CHEESE("Molten Cheese", 1120.0, 3000.0, 0.557, 0.481, "Methane");

        public final String displayName;
        public final double density; // kg/m^3
        public final double specificHeat; // J/(kg*K)
        public final double dynamicViscosity; // Pa*s
        public final double thermalConductivity;// W/(m*K)
        public final String byproductGas;

        CoolantFluidType(String displayName, double density, double specificHeat, double dynamicViscosity,
            double thermalConductivity, String byproductGas) {
            this.displayName = displayName;
            this.density = density;
            this.specificHeat = specificHeat;
            this.dynamicViscosity = dynamicViscosity;
            this.thermalConductivity = thermalConductivity;
            this.byproductGas = byproductGas;
        }

        public static CoolantFluidType fromString(String str) {
            if (str == null) return DISTILLED_WATER;
            String s = str.trim();
            for (CoolantFluidType fluid : values()) {
                if (fluid.name()
                    .equalsIgnoreCase(s) || fluid.displayName.equalsIgnoreCase(s)) {
                    return fluid;
                }
            }
            if (containsSub(s, "CHEESE")) return MOLTEN_CHEESE;
            if (containsSub(s, "HEAVY")) return HEAVY_WATER;
            return DISTILLED_WATER;
        }
    }

    public enum EnergyHatchTier {

        LV("LV", 32.0),
        MV("MV", 128.0),
        HV("HV", 512.0),
        EV("EV", 2048.0),
        IV("IV", 8192.0),
        LUV("LuV", 32768.0),
        ZPM("ZPM", 131072.0),
        UV("UV", 524288.0),
        UHV("UHV", 2097152.0);

        public final String displayName;
        public final double voltageEU;

        EnergyHatchTier(String displayName, double voltageEU) {
            this.displayName = displayName;
            this.voltageEU = voltageEU;
        }

        public static EnergyHatchTier fromString(String str) {
            if (str == null) return EV;
            String s = str.trim();
            for (EnergyHatchTier tier : values()) {
                if (tier.name()
                    .equalsIgnoreCase(s) || tier.displayName.equalsIgnoreCase(s)) {
                    return tier;
                }
            }
            if (containsSub(s, "UHV")) return UHV;
            if (containsSub(s, "UV")) return UV;
            if (containsSub(s, "ZPM")) return ZPM;
            if (containsSub(s, "LUV")) return LUV;
            if (containsSub(s, "IV")) return IV;
            if (containsSub(s, "EV")) return EV;
            if (containsSub(s, "HV")) return HV;
            if (containsSub(s, "MV")) return MV;
            if (containsSub(s, "LV")) return LV;
            return EV;
        }
    }

    public static boolean containsSub(String s, String sub) {
        if (s == null || sub == null) return false;
        int sLen = s.length();
        int subLen = sub.length();
        if (subLen == 0) return true;
        if (sLen < subLen) return false;
        for (int i = 0; i <= sLen - subLen; i++) {
            boolean match = true;
            for (int j = 0; j < subLen; j++) {
                char c1 = s.charAt(i + j);
                char c2 = sub.charAt(j);
                if (c1 != c2 && Character.toUpperCase(c1) != Character.toUpperCase(c2)) {
                    match = false;
                    break;
                }
            }
            if (match) return true;
        }
        return false;
    }

    // Configurable parameters
    private LoopMaterial material = LoopMaterial.TITANIUM;
    private LoopPipeSize pipeSize = LoopPipeSize.NORMAL;
    private CoolantFluidType fluidType = CoolantFluidType.DISTILLED_WATER;
    private EnergyHatchTier hatchTier = EnergyHatchTier.EV;
    private double dutyCyclePercent = 100.0;
    private double maxFlowRateLPerSec = 0.0; // 0 = no user limit
    private double maxPressureBar = 0.0; // 0 = no user limit
    private double pumpElectricalPowerEUt = 2048.0; // EU/t
    private double targetFlowRateLPerSec = 100.0; // L/s
    private boolean useTargetFlowMode = false; // If true, flow is set directly and pump EU is calculated
    private boolean pumpOverclocked = false; // If true, pump draws 4A instead of 1A
    private TurbineCalculator.TurbineMaterial impellerMaterial = TurbineCalculator.TurbineMaterial.ORINARUKON;
    private final Set<String> attachedPoints = new HashSet<>(); // Set of "x,y" strings

    // Dynamic state
    private double currentFlowRateLPerSec = 0.0;
    private double currentPressureBar = 1.0;
    private double currentCoolantTempCelsius = 20.0;
    private double lastHeatExtractedWatts = 0.0;
    private double lastHeatExtractedEUt = 0.0;
    private double lastSecondarySteamProducedLt = 0.0;
    private double lastSecondaryWaterBoiledLt = 0.0;
    private double lastPumpPowerEUt = 0.0;
    private boolean pressureLimited = false;
    private boolean flowLimited = false;
    private double effectiveDutyCyclePercent = 100.0;
    private String limitReason = "";
    private boolean ruptured = false;
    private String ruptureReason = "";

    // Cumulative stats
    private long totalDeuteriumProduced = 0;
    private long totalTritiumProduced = 0;
    private long totalMethaneProduced = 0;
    private double totalSecondarySteamProduced = 0.0;
    private int neutronTransmuteAccumulator = 0;

    // Polynomial coefficients derived from detailed hydrodynamic CFD simulation:
    // P_pump(Q) in EU/t = c0 + c1*Q + c2*Q^2 + c3*Q^3 (Q in L/s, Normal D=0.10m pipe)
    public static final double P_COEFF_C0 = 0.0176;
    public static final double P_COEFF_C1 = -0.0044;
    public static final double P_COEFF_C2 = 0.0011;
    public static final double P_COEFF_C3 = 0.001383;

    public CoolantLoopModel() {
        // Defaults: Titanium material, normal pipe, distilled water
        updatePumpState();
    }

    public boolean isPumpOverclocked() {
        return pumpOverclocked;
    }

    public void setPumpOverclocked(boolean pumpOverclocked) {
        this.pumpOverclocked = pumpOverclocked;
        updatePumpState();
    }

    public TurbineCalculator.TurbineMaterial getImpellerMaterial() {
        return impellerMaterial;
    }

    public void setImpellerMaterial(TurbineCalculator.TurbineMaterial mat) {
        if (mat != null) {
            this.impellerMaterial = mat;
            updatePumpState();
        }
    }

    public void setImpellerMaterial(String name) {
        this.impellerMaterial = TurbineCalculator.TurbineMaterial.fromString(name);
        updatePumpState();
    }

    public double getImpellerEfficiency() {
        return impellerMaterial != null ? impellerMaterial.tightEff : 1.0;
    }

    /**
     * Calculates the required electrical pump power (EU/t) for a given flow rate Q (L/s)
     * using the calibrated cubic hydrodynamic scaling polynomial and impeller efficiency.
     */
    public double calculatePumpPowerForFlow(double flowLPerSec) {
        if (flowLPerSec <= 0.0) return 0.0;
        double q = flowLPerSec;
        // Diameter scale factor: D_normal / D_actual
        double dScale = LoopPipeSize.NORMAL.diameterMeters / pipeSize.diameterMeters;
        // Pressure drop scales as (1/D)^5 approximately for same flow rate
        double geomFactor = Math.pow(dScale, 4.5);
        double basePower = P_COEFF_C0 + P_COEFF_C1 * q + P_COEFF_C2 * q * q + P_COEFF_C3 * q * q * q;
        double mechanicalPower = Math.max(1.0, basePower * geomFactor);
        return mechanicalPower / Math.max(0.1, getImpellerEfficiency());
    }

    /**
     * Estimates flow rate Q (L/s) from available pump electrical power (EU/t) and impeller efficiency.
     */
    public double calculateFlowFromPumpPower(double pumpElectricalEUt) {
        if (pumpElectricalEUt <= 0.0) return 0.0;
        double mechanicalPowerEUt = pumpElectricalEUt * getImpellerEfficiency();
        double dScale = LoopPipeSize.NORMAL.diameterMeters / pipeSize.diameterMeters;
        double geomFactor = Math.pow(dScale, 4.5);
        double effectiveP = mechanicalPowerEUt / geomFactor;
        // Invert cubic term P ~ c3 * Q^3 -> Q ~ (P / c3)^(1/3)
        double qEst = Math.pow(Math.max(0.1, effectiveP / P_COEFF_C3), 1.0 / 3.0);
        // Fine-tune with Newton-Raphson iterations
        for (int iter = 0; iter < 3; iter++) {
            double pCur = P_COEFF_C0 + P_COEFF_C1 * qEst + P_COEFF_C2 * qEst * qEst + P_COEFF_C3 * qEst * qEst * qEst;
            double pDeriv = P_COEFF_C1 + 2.0 * P_COEFF_C2 * qEst + 3.0 * P_COEFF_C3 * qEst * qEst;
            if (pDeriv > 1e-6) {
                qEst -= (pCur - effectiveP) / pDeriv;
            }
        }
        return Math.max(0.0, qEst);
    }

    /**
     * Calculates loop peak pressure (bar) for a given flow rate.
     */
    public double calculatePeakPressureBar(double flowLPerSec) {
        if (flowLPerSec <= 0.0) return 1.0;
        double qM3s = flowLPerSec / 1000.0;
        double d = pipeSize.diameterMeters;
        double area = Math.PI * Math.pow(d / 2.0, 2);
        double vel = qM3s / area;
        // Total minor + friction K equivalent ~ 30.0 for 25m loop
        double kEquiv = 25.0 * (0.02 / d) + 8.2;
        double dpPa = kEquiv * (fluidType.density * vel * vel / 2.0);
        return 1.0 + (dpPa / 1.0e5);
    }

    /**
     * Calculates the maximum circulation flow (L/s) that produces a given peak pressure (bar).
     * Exact analytical inverse of calculatePeakPressureBar.
     */
    public double calculateMaxFlowForPressure(double targetPressureBar) {
        if (targetPressureBar <= 1.0) return 0.0;
        double dpPa = (targetPressureBar - 1.0) * 1.0e5;
        double d = pipeSize.diameterMeters;
        double area = Math.PI * Math.pow(d / 2.0, 2);
        double kEquiv = 25.0 * (0.02 / d) + 8.2;
        double vSquared = (2.0 * dpPa) / Math.max(1e-6, kEquiv * fluidType.density);
        if (vSquared <= 0.0) return 0.0;
        double vel = Math.sqrt(vSquared);
        double qM3s = vel * area;
        return qM3s * 1000.0;
    }

    /**
     * Updates pump electrical power consumption, hydrodynamic flow rate,
     * system pressure, effective duty cycle, and limit status based on current configuration.
     */
    public void updatePumpState() {
        if (this.ruptured) {
            return;
        }

        // Control System: Calculate pump power, duty cycle, flow, and safety throttling
        double pumpAmps = pumpOverclocked ? 4.0 : 1.0;
        double maxPumpPowerEUt = hatchTier.voltageEU * pumpAmps;

        if (useTargetFlowMode) {
            currentFlowRateLPerSec = Math.max(0.0, targetFlowRateLPerSec);
            lastPumpPowerEUt = calculatePumpPowerForFlow(currentFlowRateLPerSec);
            effectiveDutyCyclePercent = maxPumpPowerEUt > 0 ? (lastPumpPowerEUt / maxPumpPowerEUt) * 100.0 : 0.0;
            pressureLimited = false;
            flowLimited = false;
            limitReason = "";
            currentPressureBar = calculatePeakPressureBar(currentFlowRateLPerSec);
        } else {
            double requestedDuty = Math.max(0.0, Math.min(100.0, dutyCyclePercent));
            double nominalPower = maxPumpPowerEUt * (requestedDuty / 100.0);

            pressureLimited = false;
            flowLimited = false;
            effectiveDutyCyclePercent = requestedDuty;
            limitReason = "";

            if (requestedDuty <= 0.0 || nominalPower <= 0.0) {
                lastPumpPowerEUt = 0.0;
                currentFlowRateLPerSec = 0.0;
                currentPressureBar = 1.0;
                effectiveDutyCyclePercent = 0.0;
            } else {
                // Implicit pipe material burst protection (safe control system margin, 99.5% of maxPressureBar)
                double allowedPressure = material.maxPressureBar * 0.995;
                boolean userPressureActive = false;
                if (maxPressureBar > 1.0 && maxPressureBar < allowedPressure) {
                    allowedPressure = maxPressureBar;
                    userPressureActive = true;
                }

                double maxFlowFromPressure = calculateMaxFlowForPressure(allowedPressure);
                double allowedFlow = maxFlowFromPressure;
                boolean userFlowActive = false;
                if (maxFlowRateLPerSec > 0.0 && maxFlowRateLPerSec < allowedFlow) {
                    allowedFlow = maxFlowRateLPerSec;
                    userFlowActive = true;
                }

                double maxAllowedPower = calculatePumpPowerForFlow(allowedFlow);

                if (nominalPower > maxAllowedPower) {
                    lastPumpPowerEUt = Math.max(0.0, maxAllowedPower);
                    currentFlowRateLPerSec = allowedFlow;
                    currentPressureBar = calculatePeakPressureBar(currentFlowRateLPerSec);
                    effectiveDutyCyclePercent = maxPumpPowerEUt > 0 ? (lastPumpPowerEUt / maxPumpPowerEUt) * 100.0 : 0.0;

                    if (userFlowActive && allowedFlow == maxFlowRateLPerSec) {
                        flowLimited = true;
                        limitReason = "Flow capped at " + fmt1(maxFlowRateLPerSec) + " L/s";
                    } else {
                        pressureLimited = true;
                        if (userPressureActive) {
                            limitReason = "Pressure capped at " + fmt1(maxPressureBar) + " bar";
                        } else {
                            limitReason = "Throttled by " + material.displayName
                                + " pipe limit ("
                                + fmt1(material.maxPressureBar)
                                + " bar)";
                        }
                    }
                } else {
                    lastPumpPowerEUt = nominalPower;
                    currentFlowRateLPerSec = calculateFlowFromPumpPower(lastPumpPowerEUt);
                    currentPressureBar = calculatePeakPressureBar(currentFlowRateLPerSec);
                    effectiveDutyCyclePercent = requestedDuty;
                }
            }
        }
    }

    /**
     * Executes one tick (0.05 seconds) of joint coolant loop simulation.
     */
    public boolean step(StandaloneNuclearGrid grid, SimTile[][] tiles, int width, int height) {
        if (ruptured) {
            return false;
        }

        int reactorTier = grid.getPipeTier();

        // 1. Verify Tier Constraint: Convective cooling availability
        if (reactorTier < NuclearSimulationEngine.PIPE_TIER_PLATINUM) {
            this.ruptured = true;
            this.ruptureReason = "Tier Violation: Convective coolant loops require Tier 2+ (IV Platinum / LuV Osmium) casing!";
            return false;
        }

        // 2. Control System: Calculate pump power, duty cycle, flow, and safety throttling
        updatePumpState();

        // 3. Safety Burst Verification (in case of forced external flow or edge conditions)
        currentPressureBar = calculatePeakPressureBar(currentFlowRateLPerSec);
        if (currentPressureBar > material.maxPressureBar) {
            this.ruptured = true;
            this.ruptureReason = String.format(
                "Coolant Loop Burst! Peak pressure %.1f bar exceeded maximum burst rating %.1f bar for %s piping.",
                currentPressureBar,
                material.maxPressureBar,
                material.displayName);
            return false;
        }

        // 4. Melt Limit Verification
        if (currentCoolantTempCelsius > material.maxTemperatureCelsius) {
            this.ruptured = true;
            this.ruptureReason = String.format(
                "Coolant Loop Melted! Coolant temperature %.1f °C exceeded maximum safe rating %.1f °C (%d K) for %s piping.",
                currentCoolantTempCelsius,
                material.maxTemperatureCelsius,
                material.maxTemperatureKelvin,
                material.displayName);
            return false;
        }

        // 5. Gather all attached core passage cells (configured points + any placed PASSAGE_CORE tiles)
        List<SimTile> passageTiles = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SimTile tile = tiles[x][y];
                if (tile == null) continue;
                String key = x + "," + y;
                if (tile.getType() == SimTile.TileType.PASSAGE_CORE || attachedPoints.contains(key)) {
                    passageTiles.add(tile);
                }
            }
        }

        // If no passage tiles are attached, the loop circulates without core thermal exchange
        if (passageTiles.isEmpty()) {
            lastHeatExtractedWatts = 0.0;
            lastHeatExtractedEUt = 0.0;
            lastSecondarySteamProducedLt = 0.0;
            lastSecondaryWaterBoiledLt = 0.0;
            // Loop naturally cools toward ambient
            currentCoolantTempCelsius = Math.max(NuclearSimulationEngine.ambientTemp, currentCoolantTempCelsius - 0.1);
            return true;
        }

        // 6. Convective Heat Transfer in Core Passages (Dittus-Boelter Correlation)
        int numPassages = passageTiles.size();
        double flowPerPassageM3s = (currentFlowRateLPerSec / 1000.0) / Math.max(1, numPassages);
        double passageDiameter = pipeSize.diameterMeters;
        double passageLength = 5.0; // Standard 5m core channel height
        double passageArea = Math.PI * passageDiameter * passageLength; // ~1.57 m^2 per channel
        double flowArea = Math.PI * Math.pow(passageDiameter / 2.0, 2);
        double vel = flowArea > 0 ? (flowPerPassageM3s / flowArea) : 0.0;

        double re = (fluidType.density * vel * passageDiameter) / Math.max(1e-6, fluidType.dynamicViscosity);
        double pr = (fluidType.dynamicViscosity * fluidType.specificHeat)
            / Math.max(1e-4, fluidType.thermalConductivity);

        double nu;
        if (re < 2300.0) {
            nu = 4.36; // Laminar fully developed
        } else {
            nu = 0.023 * Math.pow(re, 0.8) * Math.pow(Math.max(0.6, pr), 0.4); // Turbulent Dittus-Boelter
        }

        double hConvective = (nu * fluidType.thermalConductivity) / passageDiameter; // W/(m^2*K)
        double totalHeatExtractedJoulesTick = 0.0;

        for (SimTile pTile : passageTiles) {
            double cellTemp = pTile.getTemperature();
            double deltaT = cellTemp - currentCoolantTempCelsius;
            if (deltaT > 0.001) {
                double qDotWatts = hConvective * passageArea * deltaT;
                double joulesTick = qDotWatts * 0.05; // 0.05s per tick
                double euTick = joulesTick / 128.0;

                // Max heat without overcooling tile below coolant temperature
                double maxEuDrop = (cellTemp - currentCoolantTempCelsius) * NuclearSimulationEngine.euPerDegree;
                if (euTick > maxEuDrop) {
                    euTick = maxEuDrop;
                    joulesTick = euTick * 128.0;
                }

                pTile.setTemperature(
                    Math.max(currentCoolantTempCelsius, cellTemp - euTick / NuclearSimulationEngine.euPerDegree));
                pTile.setLastHeatOutput(pTile.getLastHeatOutput() + euTick);
                totalHeatExtractedJoulesTick += joulesTick;
            }

            // Radiolytic dissociation: every 4 absorbed fast neutrons dissociates 2L water -> 2L byproduct gas
            int fastAbsorbed = pTile.getLastFastAbsorbed();
            if (fastAbsorbed > 0) {
                neutronTransmuteAccumulator += fastAbsorbed;
                if (neutronTransmuteAccumulator >= 4) {
                    int cycles = neutronTransmuteAccumulator / 4;
                    neutronTransmuteAccumulator %= 4;
                    if (fluidType == CoolantFluidType.HEAVY_WATER) {
                        totalTritiumProduced += cycles * 2L;
                    } else if (fluidType == CoolantFluidType.MOLTEN_CHEESE) {
                        totalMethaneProduced += cycles * 2L;
                    } else {
                        totalDeuteriumProduced += cycles * 2L;
                    }
                }
            }
        }

        lastHeatExtractedWatts = totalHeatExtractedJoulesTick / 0.05;
        lastHeatExtractedEUt = totalHeatExtractedJoulesTick / 128.0;

        // 7. Coolant Temperature Rise
        // Mass flow per tick (kg)
        double loopFluidMassKg = Math.max(10.0, (currentFlowRateLPerSec * 0.05) * (fluidType.density / 1000.0) * 10.0);
        double tempRise = totalHeatExtractedJoulesTick / (loopFluidMassKg * fluidType.specificHeat);
        currentCoolantTempCelsius += tempRise;

        // 8. Secondary Steam Generation at Pressurized Heat Exchanger (PHE)
        // 1 L water requires 2.26 MJ (2260 J/mL) latent heat to vaporize -> produces 160 L steam (1:160 expansion)
        double steamGeneratedL = 0.0;
        if (currentCoolantTempCelsius > 100.0) {
            double deltaTPhe = currentCoolantTempCelsius - 100.0;
            double pheArea = 24.0; // 24 m^2 tube bundle area
            // h_phe ~ 2500 W/(m^2*K) for water-to-water heat exchanger
            double qPheWatts = 2500.0 * pheArea * deltaTPhe;
            double qPheJoules = Math.min(
                totalHeatExtractedJoulesTick + (deltaTPhe * loopFluidMassKg * fluidType.specificHeat * 0.5),
                qPheWatts * 0.05);

            // 1 Liter (mB) water requires 2260 J (2.26 MJ/L) latent heat to vaporize -> produces 160 L (mB) steam
            // (1:160 expansion)
            double waterBoiledL = qPheJoules / 2260.0;
            steamGeneratedL = waterBoiledL * 160.0; // 1:160 expansion

            lastSecondaryWaterBoiledLt = waterBoiledL;
            lastSecondarySteamProducedLt = steamGeneratedL;
            totalSecondarySteamProduced += steamGeneratedL;

            // Cool coolant back down toward 100°C
            double tempDrop = qPheJoules / (loopFluidMassKg * fluidType.specificHeat);
            currentCoolantTempCelsius = Math.max(100.0, currentCoolantTempCelsius - tempDrop);
        } else {
            lastSecondaryWaterBoiledLt = 0.0;
            lastSecondarySteamProducedLt = 0.0;
        }

        return true;
    }

    public void reset() {
        this.ruptured = false;
        this.ruptureReason = "";
        this.currentFlowRateLPerSec = 0.0;
        this.currentPressureBar = 1.0;
        this.currentCoolantTempCelsius = NuclearSimulationEngine.ambientTemp;
        this.lastHeatExtractedWatts = 0.0;
        this.lastHeatExtractedEUt = 0.0;
        this.lastSecondarySteamProducedLt = 0.0;
        this.lastSecondaryWaterBoiledLt = 0.0;
        this.lastPumpPowerEUt = 0.0;
        this.totalDeuteriumProduced = 0;
        this.totalTritiumProduced = 0;
        this.totalMethaneProduced = 0;
        this.totalSecondarySteamProduced = 0.0;
        this.neutronTransmuteAccumulator = 0;
    }

    // Attach / Detach coordinate helpers
    public void attachPoint(int x, int y) {
        attachedPoints.add(x + "," + y);
    }

    public void detachPoint(int x, int y) {
        attachedPoints.remove(x + "," + y);
    }

    public boolean isPointAttached(int x, int y) {
        return attachedPoints.contains(x + "," + y);
    }

    public void clearAttachedPoints() {
        attachedPoints.clear();
    }

    public Set<String> getAttachedPoints() {
        return Collections.unmodifiableSet(attachedPoints);
    }

    // Getters and Setters
    public LoopMaterial getMaterial() {
        return material;
    }

    public void setMaterial(LoopMaterial material) {
        if (material != null) {
            this.material = material;
            updatePumpState();
        }
    }

    public LoopPipeSize getPipeSize() {
        return pipeSize;
    }

    public void setPipeSize(LoopPipeSize pipeSize) {
        if (pipeSize != null) {
            this.pipeSize = pipeSize;
            updatePumpState();
        }
    }

    public CoolantFluidType getFluidType() {
        return fluidType;
    }

    public void setFluidType(CoolantFluidType fluidType) {
        if (fluidType != null) {
            this.fluidType = fluidType;
            updatePumpState();
        }
    }

    public EnergyHatchTier getHatchTier() {
        return hatchTier;
    }

    public void setHatchTier(EnergyHatchTier hatchTier) {
        if (hatchTier != null) {
            this.hatchTier = hatchTier;
            this.useTargetFlowMode = false;
            updatePumpState();
        }
    }

    public double getDutyCyclePercent() {
        return dutyCyclePercent;
    }

    public void setDutyCyclePercent(double dutyCyclePercent) {
        this.dutyCyclePercent = Math.max(0.0, Math.min(100.0, dutyCyclePercent));
        this.useTargetFlowMode = false;
        updatePumpState();
    }

    public double getMaxFlowRateLPerSec() {
        return maxFlowRateLPerSec;
    }

    public void setMaxFlowRateLPerSec(double maxFlowRateLPerSec) {
        this.maxFlowRateLPerSec = Math.max(0.0, maxFlowRateLPerSec);
        updatePumpState();
    }

    public double getMaxPressureBar() {
        return maxPressureBar;
    }

    public void setMaxPressureBar(double maxPressureBar) {
        this.maxPressureBar = Math.max(0.0, maxPressureBar);
        updatePumpState();
    }

    public boolean isPressureLimited() {
        return pressureLimited;
    }

    public boolean isFlowLimited() {
        return flowLimited;
    }

    public double getEffectiveDutyCyclePercent() {
        return effectiveDutyCyclePercent;
    }

    public String getLimitReason() {
        return limitReason;
    }

    public double getPumpElectricalPowerEUt() {
        return pumpElectricalPowerEUt;
    }

    public void setPumpElectricalPowerEUt(double power) {
        this.pumpElectricalPowerEUt = Math.max(0.0, power);
        this.useTargetFlowMode = false;
        for (EnergyHatchTier tier : EnergyHatchTier.values()) {
            if (power <= tier.voltageEU) {
                this.hatchTier = tier;
                this.dutyCyclePercent = Math.min(100.0, (power / tier.voltageEU) * 100.0);
                updatePumpState();
                return;
            }
        }
        this.hatchTier = EnergyHatchTier.UHV;
        this.dutyCyclePercent = Math.min(100.0, (power / EnergyHatchTier.UHV.voltageEU) * 100.0);
        updatePumpState();
    }

    public double getTargetFlowRateLPerSec() {
        return targetFlowRateLPerSec;
    }

    public void setTargetFlowRateLPerSec(double flow) {
        this.targetFlowRateLPerSec = Math.max(0.0, flow);
        updatePumpState();
    }

    public boolean isUseTargetFlowMode() {
        return useTargetFlowMode;
    }

    public void setUseTargetFlowMode(boolean useTargetFlowMode) {
        this.useTargetFlowMode = useTargetFlowMode;
        updatePumpState();
    }

    public double getCurrentFlowRateLPerSec() {
        return currentFlowRateLPerSec;
    }

    public double getCurrentPressureBar() {
        return currentPressureBar;
    }

    public double getCurrentCoolantTempCelsius() {
        return currentCoolantTempCelsius;
    }

    public void setCurrentCoolantTempCelsius(double temp) {
        this.currentCoolantTempCelsius = temp;
    }

    public double getLastHeatExtractedWatts() {
        return lastHeatExtractedWatts;
    }

    public double getLastHeatExtractedEUt() {
        return lastHeatExtractedEUt;
    }

    public double getLastSecondarySteamProducedLt() {
        return lastSecondarySteamProducedLt;
    }

    public double getLastSecondaryWaterBoiledLt() {
        return lastSecondaryWaterBoiledLt;
    }

    public double getLastPumpPowerEUt() {
        return lastPumpPowerEUt;
    }

    public boolean isRuptured() {
        return ruptured;
    }

    public String getRuptureReason() {
        return ruptureReason;
    }

    public long getTotalDeuteriumProduced() {
        return totalDeuteriumProduced;
    }

    public long getTotalTritiumProduced() {
        return totalTritiumProduced;
    }

    public long getTotalMethaneProduced() {
        return totalMethaneProduced;
    }

    public double getTotalSecondarySteamProduced() {
        return totalSecondarySteamProduced;
    }

    public void setPumpPowerEUt(double power) {
        setPumpElectricalPowerEUt(power);
    }

    public void setCirculationFlowLs(double flow) {
        setTargetFlowRateLPerSec(flow);
        setUseTargetFlowMode(true);
    }

    private static String fmt1(double val) {
        long r = Math.round(val * 10.0);
        long intPart = r / 10;
        long fracPart = Math.abs(r % 10);
        return intPart + "." + fracPart;
    }
}
