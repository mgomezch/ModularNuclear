package com.gtnewhorizons.modularnuclear.common.nuclear.standalone;

import java.util.List;

import org.teavm.interop.Export;

import com.gtnewhorizons.modularnuclear.common.config.CoolantLoopConfigData;
import com.gtnewhorizons.modularnuclear.common.config.ReactorConfigData;
import com.gtnewhorizons.modularnuclear.common.config.data.CoolantDeviceData.PHEData.SecondaryCoolantEntry;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearColorMaps;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.ReactorStructureRules;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.cli.RunConfig;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.cli.SimOptions;

/**
 * WebAssembly and standalone bridge exposing the nuclear simulation engine
 * directly to browser JavaScript via WebAssembly or client-side execution.
 */
public class NuclearSimWasmBridge {

    private static StandaloneNuclearGrid grid;
    private static boolean isRunning = false;

    public static void main(String[] args) {
        initGrid(9, 9, NuclearSimulationEngine.PIPE_TIER_PLATINUM);
        loadPreset("BEST_PLATINUM_9X9");
    }

    static {
        // Same defaults as the game: physics.yaml, fuels.yaml and components.yaml embedded at build time.
        ReactorConfigData.loadDefaults();
    }

    @Export(name = "initGrid")
    public static void initGrid(int width, int height, int pipeTier) {
        grid = new StandaloneNuclearGrid(width, height, pipeTier);
    }

    @Export(name = "step")
    public static void step() {
        if (grid == null) return;
        if (grid.isExploded() || (grid.isStopOnIncidents() && grid.isHaltedByIncident())) {
            isRunning = false;
            return;
        }
        grid.step();
        if (grid.isExploded() || (grid.isStopOnIncidents() && grid.isHaltedByIncident())) {
            isRunning = false;
        }
    }

    @Export(name = "stepTicks")
    public static void stepTicks(int count) {
        if (grid == null) return;
        if (grid.isExploded() || (grid.isStopOnIncidents() && grid.isHaltedByIncident())) {
            isRunning = false;
            return;
        }
        for (int i = 0; i < count && !grid.isExploded()
            && !(grid.isStopOnIncidents() && grid.isHaltedByIncident()); i++) {
            grid.step();
            if (grid.isStopOnIncidents() && grid.isHaltedByIncident()) {
                break;
            }
        }
        if (grid.isExploded() || (grid.isStopOnIncidents() && grid.isHaltedByIncident())) {
            isRunning = false;
        }
    }

    @Export(name = "reset")
    public static void reset() {
        if (grid != null) {
            isRunning = false;
            grid.resetMetrics();
        }
    }

    @Export(name = "loadPreset")
    public static void loadPreset(String presetName) {
        if (grid != null && presetName != null) {
            grid.loadPreset(presetName);
        }
    }

    /**
     * Builds the grid from a simulator option line, the one the CLI's batch mode reads (layout, casing tier, turbine,
     * cooling, coolant loop circuits, machine limits), so a saved design loads exactly as the calibration ran it.
     * Parameters the line does not set start from the shipped configuration.
     */
    @Export(name = "loadRunOptions")
    public static void loadRunOptions(String line) {
        if (line == null || line.trim()
            .isEmpty()) return;
        NuclearSimulationEngine.resetDefaultParameters();
        CoolantLoopConfigData.loadDefaults();
        RunConfig cfg = RunConfig.batchLine();
        // As in the game, an overheating cell damages the reactor rather than disqualifying the run
        cfg.strict = false;
        SimOptions.table(true)
            .parseTokens(cfg, line.trim(), "\\s+");
        isRunning = false;
        grid = cfg.buildGrid(false);
    }

    @Export(name = "loadLayout")
    public static void loadLayout(String layout) {
        if (grid != null && layout != null) {
            grid.loadLayout(layout);
        }
    }

    @Export(name = "setTile")
    public static void setTile(int x, int y, String typeStr) {
        if (grid != null && typeStr != null) {
            try {
                grid.setTileCode(x, y, typeStr);
            } catch (RuntimeException e) {
                System.err.println("setTile(" + x + ", " + y + ", " + typeStr + ") failed: " + e);
            }
        }
    }

    @Export(name = "setPipeTier")
    public static void setPipeTier(int tier) {
        if (grid != null) {
            grid.setPipeTier(tier);
        }
    }

    @Export(name = "setTurbine")
    public static void setTurbine(String mat, String sz, String fit) {
        if (grid != null) {
            try {
                if (mat != null && !mat.isEmpty()) {
                    grid.setTurbineMaterial(TurbineCalculator.TurbineMaterial.valueOf(mat));
                }
                if (sz != null && !sz.isEmpty()) {
                    grid.setTurbineSize(TurbineCalculator.TurbineSize.valueOf(sz));
                }
                if (fit != null && !fit.isEmpty()) {
                    grid.setTurbineFitting(TurbineCalculator.FittingMode.valueOf(fit));
                }
            } catch (IllegalArgumentException e) {
                System.err.println("setTurbine: unknown turbine option: " + e.getMessage());
            }
        }
    }

    @Export(name = "setMachineLimits")
    public static void setMachineLimits(int maxThermalBoilers, int maxLhe, int maxEhe, int maxLst, int maxXlst,
        int maxXlstHp, int maxXlstSc, int maxTurbines) {
        if (grid != null) {
            TurbineCalculator.MachineLimits limits = new TurbineCalculator.MachineLimits(
                maxThermalBoilers,
                maxLhe,
                maxEhe,
                maxLst,
                maxXlst,
                maxXlstHp,
                maxXlstSc,
                maxTurbines);
            grid.setMachineLimits(limits);
        }
    }

    @Export(name = "setRunning")
    public static void setRunning(boolean running) {
        if (running && grid != null && grid.isExploded()) {
            isRunning = false;
            return;
        }
        isRunning = running;
        if (running && grid != null && grid.isHaltedByIncident()) {
            grid.clearHaltedByIncident();
        }
    }

    @Export(name = "isRunning")
    public static boolean isRunning() {
        return isRunning;
    }

    public static StandaloneNuclearGrid getGrid() {
        return grid;
    }

    @Export(name = "setCoolingMode")
    public static void setCoolingMode(String modeStr) {
        if (grid != null && modeStr != null) {
            grid.setCoolingMode(CoolantLoopModel.CoolingMode.fromString(modeStr));
        }
    }

    @Export(name = "setCoolantLoopMaterial")
    public static void setCoolantLoopMaterial(String matStr) {
        if (grid != null && matStr != null) {
            grid.getCoolantLoop()
                .setMaterial(CoolantLoopModel.LoopMaterial.fromString(matStr));
            grid.updatePowerResultNet();
        }
    }

    @Export(name = "setCoolantLoopPipeSize")
    public static void setCoolantLoopPipeSize(String sizeStr) {
        if (grid != null && sizeStr != null) {
            grid.getCoolantLoop()
                .setPipeSize(CoolantLoopModel.LoopPipeSize.fromString(sizeStr));
            grid.updatePowerResultNet();
        }
    }

    @Export(name = "setCoolantLoopFluid")
    public static void setCoolantLoopFluid(String fluidStr) {
        if (grid != null && fluidStr != null) {
            grid.getCoolantLoop()
                .setFluidType(CoolantLoopModel.CoolantFluidType.fromString(fluidStr));
            grid.updatePowerResultNet();
        }
    }

    @Export(name = "setCoolantLoopSecondaryCoolant")
    public static void setCoolantLoopSecondaryCoolant(String fluidName) {
        if (grid != null && fluidName != null) {
            grid.getCoolantLoop()
                .setSecondaryCoolant(fluidName);
            grid.updatePowerResultNet();
        }
    }

    @Export(name = "setCoolantLoopHatchTier")
    public static void setCoolantLoopHatchTier(String tierStr) {
        if (grid != null && tierStr != null) {
            grid.getCoolantLoop()
                .setHatchTier(CoolantLoopModel.EnergyHatchTier.fromString(tierStr));
            grid.updatePowerResultNet();
        }
    }

    /** Energy hatches on the pump: one draws the tier's voltage, two or more draw four times it. */
    @Export(name = "setCoolantLoopPumpHatches")
    public static void setCoolantLoopPumpHatches(int count) {
        if (grid != null) {
            grid.getCoolantLoop()
                .setPumpHatchCount(count);
            grid.updatePowerResultNet();
        }
    }

    @Export(name = "setCoolantLoopRotor")
    public static void setCoolantLoopRotor(String material, String size) {
        if (grid != null) {
            CoolantLoopModel cl = grid.getCoolantLoop();
            if (material != null && !material.isEmpty()) cl.setRotorMaterial(material);
            if (size != null && !size.isEmpty()) cl.setRotorSize(CoolantLoopModel.RotorSize.fromString(size));
            grid.updatePowerResultNet();
        }
    }

    /**
     * Selects the loop circuit (0-based) that the coolant loop setters act on, adding circuits up to it. A circuit's
     * passages are CP (circuit 1), CP2, CP3 ... in the layout.
     */
    @Export(name = "selectCoolantCircuit")
    public static void selectCoolantCircuit(int index) {
        if (grid != null) {
            grid.getCoolantLoop()
                .selectCircuit(index);
            grid.updatePowerResultNet();
        }
    }

    /** Removes the last loop circuit (the first always stays). */
    @Export(name = "removeCoolantCircuit")
    public static void removeCoolantCircuit() {
        if (grid != null) {
            grid.getCoolantLoop()
                .removeLastCircuit();
            grid.updatePowerResultNet();
        }
    }

    /** Share of ticks the selected circuit's pump runs (a pulsed signal on its machine controller cover). */
    @Export(name = "setCoolantLoopDutyCycle")
    public static void setCoolantLoopDutyCycle(double percent) {
        if (grid != null) {
            grid.getCoolantLoop()
                .setDutyCyclePercent(percent);
            grid.updatePowerResultNet();
        }
    }

    /** Switches the pump off while the loop flow is at or above this (0 for no cap). */
    @Export(name = "setCoolantLoopMaxFlow")
    public static void setCoolantLoopMaxFlow(double flowLPerSec) {
        if (grid != null) {
            grid.getCoolantLoop()
                .setMaxFlowRateLPerSec(flowLPerSec);
            grid.updatePowerResultNet();
        }
    }

    /** Switches the pump off while the loop's peak pressure is at or above this (0 for no cap). */
    @Export(name = "setCoolantLoopMaxPressure")
    public static void setCoolantLoopMaxPressure(double bar) {
        if (grid != null) {
            grid.getCoolantLoop()
                .setMaxPressureBar(bar);
            grid.updatePowerResultNet();
        }
    }

    /**
     * Temperature band on the selected circuit: the pump switches on above max and off below min, reading the
     * hottest passage or the hot leg coolant (sensor PASSAGE or HOT_LEG). enabled 0 turns the band off.
     */
    @Export(name = "setCoolantLoopTempBand")
    public static void setCoolantLoopTempBand(int enabled, double minC, double maxC, String sensor) {
        if (grid != null) {
            CoolantLoopModel cl = grid.getCoolantLoop();
            if (enabled != 0) {
                cl.setTemperatureBand(minC, maxC);
            } else {
                cl.setTemperatureBand(Double.NaN, Double.NaN);
            }
            if (sensor != null && !sensor.isEmpty()) {
                cl.getSelectedCircuit()
                    .setSensor(CoolantLoopModel.TempSensor.fromString(sensor));
            }
            grid.updatePowerResultNet();
        }
    }

    /**
     * PHE temperature band on the selected circuit: the PHEs run above max and stop below min while the pump keeps
     * running, reading the hot leg coolant or a passage (sensor HOT_LEG, PASSAGE or COLDEST_PASSAGE). enabled 0
     * turns the band off.
     */
    @Export(name = "setCoolantLoopPheTempBand")
    public static void setCoolantLoopPheTempBand(int enabled, double minC, double maxC, String sensor) {
        if (grid != null) {
            CoolantLoopModel cl = grid.getCoolantLoop();
            if (enabled != 0) {
                cl.setPheTemperatureBand(minC, maxC);
            } else {
                cl.setPheTemperatureBand(Double.NaN, Double.NaN);
            }
            if (sensor != null && !sensor.isEmpty()) {
                cl.getSelectedCircuit()
                    .setPheSensor(CoolantLoopModel.TempSensor.fromString(sensor));
            }
            grid.updatePowerResultNet();
        }
    }

    /**
     * PHE design on the selected circuit: sheetmetal tier (0 copper ... 7 neutronium), height (3-9, odd) and overall
     * length (5-11). Exchange area is height x (length - 2) m², times the sheetmetal's heat transfer multiplier.
     */
    @Export(name = "setCoolantLoopPheDesign")
    public static void setCoolantLoopPheDesign(int sheetmetalTier, int height, int length) {
        if (grid != null) {
            grid.getCoolantLoop()
                .setPheDesign(sheetmetalTier, height, length);
            grid.updatePowerResultNet();
        }
    }

    /** Independent loops, each with its own pump and PHEs, that the passages are split among. */
    @Export(name = "setCoolantLoopCount")
    public static void setCoolantLoopCount(int count) {
        if (grid != null) {
            grid.getCoolantLoop()
                .setLoopCount(count);
            grid.updatePowerResultNet();
        }
    }

    /** Pressurized heat exchangers in parallel on the loop, with the tier and number of energy hatches on each. */
    @Export(name = "setCoolantLoopPhe")
    public static void setCoolantLoopPhe(int count, String hatchTier, int hatchCount) {
        if (grid != null) {
            CoolantLoopModel cl = grid.getCoolantLoop();
            cl.setPheCount(count);
            if (hatchTier != null && !hatchTier.isEmpty()) {
                cl.setPheHatchTier(CoolantLoopModel.EnergyHatchTier.fromString(hatchTier));
            }
            cl.setPheHatchCount(hatchCount);
            grid.updatePowerResultNet();
        }
    }

    /** Display label of a PHE secondary fluid: its fluid, boiling point and product. */
    public static String secondaryCoolantLabel(SecondaryCoolantEntry e) {
        if (e == null) return "None";
        return e.inputFluidName + " (boils at " + Math.round(e.boilingPointCelsius) + "°C → " + e.outputGasName + ")";
    }

    /** The secondary fluids a PHE accepts, from the coolant loop device config. */
    public static String secondaryCoolantsJson() {
        StringBuilder sb = new StringBuilder("[");
        int i = 0;
        for (SecondaryCoolantEntry e : CoolantLoopModel.secondaryCoolantChoices()) {
            if (i++ > 0) sb.append(",");
            sb.append("{\"id\":\"")
                .append(escapeJson(e.inputFluidName))
                .append("\",\"name\":\"")
                .append(escapeJson(secondaryCoolantLabel(e)))
                .append("\",\"boilingPointC\":")
                .append(fmt1(e.boilingPointCelsius))
                .append(",\"fluidName\":\"")
                .append(escapeJson(e.inputFluidName))
                .append("\",\"outputGasName\":\"")
                .append(escapeJson(e.outputGasName))
                .append("\",\"isSteam\":")
                .append(e.isSteam)
                .append("}");
        }
        return sb.append("]")
            .toString();
    }

    @Export(name = "attachCoolantLoopPoint")
    public static void attachCoolantLoopPoint(int x, int y) {
        if (grid != null) {
            grid.getCoolantLoop()
                .attachPoint(x, y);
        }
    }

    @Export(name = "detachCoolantLoopPoint")
    public static void detachCoolantLoopPoint(int x, int y) {
        if (grid != null) {
            grid.getCoolantLoop()
                .detachPoint(x, y);
        }
    }

    @Export(name = "clearCoolantLoopPoints")
    public static void clearCoolantLoopPoints() {
        if (grid != null) {
            grid.getCoolantLoop()
                .clearAttachedPoints();
        }
    }

    @Export(name = "setStrictMode")
    public static void setStrictMode(boolean strict) {
        if (grid != null) {
            grid.setStrictMode(strict);
        }
    }

    @Export(name = "clearIncidentLog")
    public static void clearIncidentLog() {
        if (grid != null) {
            grid.clearIncidentLog();
        }
    }

    @Export(name = "setStopOnIncidents")
    public static void setStopOnIncidents(boolean stop) {
        if (grid != null) {
            grid.setStopOnIncidents(stop);
        }
    }

    @Export(name = "isStopOnIncidents")
    public static boolean isStopOnIncidents() {
        return grid != null && grid.isStopOnIncidents();
    }

    @Export(name = "setControlRod")
    public static void setControlRod(int x, int y, boolean hasRod, String typeStr, int insertion) {
        if (grid != null) {
            SimTile.ControlRodType rodType = SimTile.ControlRodType.fromName(typeStr);
            grid.setControlRod(x, y, hasRod, rodType, insertion);
        }
    }

    @Export(name = "setAllControlRodsInsertion")
    public static void setAllControlRodsInsertion(int insertion) {
        if (grid != null) {
            grid.setAllControlRodsInsertion(insertion);
        }
    }

    @Export(name = "scram")
    public static void scram() {
        if (grid != null) {
            grid.scram();
        }
    }

    @Export(name = "clearHaltedByIncident")
    public static void clearHaltedByIncident() {
        if (grid != null) {
            grid.clearHaltedByIncident();
        }
    }

    @Export(name = "setAutoSupplyFuel")
    public static void setAutoSupplyFuel(boolean autoSupply) {
        if (grid != null) {
            grid.setAutoSupplyFuel(autoSupply);
        }
    }

    @Export(name = "isAutoSupplyFuel")
    public static boolean isAutoSupplyFuel() {
        return grid != null && grid.isAutoSupplyFuel();
    }

    @Export(name = "setAutoReplaceFuel")
    public static void setAutoReplaceFuel(boolean autoReplace) {
        setAutoSupplyFuel(autoReplace);
    }

    @Export(name = "isAutoReplaceFuel")
    public static boolean isAutoReplaceFuel() {
        return isAutoSupplyFuel();
    }

    @Export(name = "setParam")
    public static void setParam(String key, String value) {
        applyParam(key, value);
    }

    private static void applyParam(String key, String value) {
        if (key == null || value == null) return;
        if ("stopOnIncidents".equalsIgnoreCase(key) || "stopIncidents".equalsIgnoreCase(key)) {
            if (grid != null) {
                grid.setStopOnIncidents("true".equalsIgnoreCase(value) || "1".equals(value));
            }
            return;
        }
        if ("clearHaltedByIncident".equalsIgnoreCase(key)) {
            if (grid != null) {
                grid.clearHaltedByIncident();
            }
            return;
        }
        if ("autoSupplyFuel".equalsIgnoreCase(key) || "autoReplaceFuel".equalsIgnoreCase(key)
            || "autoRefuel".equalsIgnoreCase(key)) {
            if (grid != null) {
                grid.setAutoSupplyFuel("true".equalsIgnoreCase(value) || "1".equals(value));
            }
            return;
        }
        if ("reset".equalsIgnoreCase(key)) {
            NuclearSimulationEngine.resetDefaultParameters();
            if (grid != null) {
                grid.updateHatchCapacities(NuclearSimulationEngine.params().hatchCoolantCapacity);
            }
            return;
        }
        if ("repair".equalsIgnoreCase(key)) {
            if (grid != null) {
                grid.forceRepair();
            }
            return;
        }
        if ("strict".equalsIgnoreCase(key) || "strictMode".equalsIgnoreCase(key)) {
            if (grid != null) {
                grid.setStrictMode("true".equalsIgnoreCase(value) || "1".equals(value));
            }
            return;
        }
        if ("clearIncidents".equalsIgnoreCase(key) || "clearIncidentLog".equalsIgnoreCase(key)) {
            if (grid != null) {
                grid.clearIncidentLog();
            }
            return;
        }
        if ("turnoverCurve".equals(key)) {
            NuclearSimulationEngine.params().turnoverCurve = NuclearSimulationEngine.TurnoverCurve.fromString(value);
            return;
        }
        double d = 0;
        try {
            d = Double.parseDouble(value);
        } catch (NumberFormatException e) {
            System.err.println("setParam(" + key + "): not a number: " + value);
            return;
        }
        if ("huPerDegree".equals(key)) {
            NuclearSimulationEngine.setHuPerDegree(d);
        } else if ("baseHatchConductance".equals(key) || "baseConductance".equals(key) || "conductance".equals(key)) {
            NuclearSimulationEngine.setBaseHatchConductance(d);
        } else if ("coreHatchHeatCapacity".equals(key)) {
            NuclearSimulationEngine.setCoreHatchHeatCapacity(d);
        } else if ("hatchConductanceTierRatio".equals(key)) {
            NuclearSimulationEngine.setHatchConductanceTierRatio(d);
        } else if ("reactorDamage".equals(key) || "damage".equals(key)) {
            if (grid != null) {
                grid.setReactorDamage(d);
            }
        } else if ("maintenanceIssues".equals(key)) {
            if (grid != null) {
                grid.setMaintenanceIssues((int) d);
            }
        } else if ("hatchCapacity".equals(key)) {
            NuclearSimulationEngine.params().hatchCoolantCapacity = Math.max(100, (int) d);
            if (grid != null) {
                grid.updateHatchCapacities(NuclearSimulationEngine.params().hatchCoolantCapacity);
            }
        } else if ("turnoverDeltaTMax".equals(key)) {
            NuclearSimulationEngine.params().turnoverDeltaTMax = d;
        } else if ("turnoverExponent".equals(key)) {
            NuclearSimulationEngine.params().turnoverExponent = d;
        } else if ("coolantFeedRate".equals(key)) {
            NuclearSimulationEngine.params().coolantFeedRate = (int) d;
        } else if ("coolingHeat".equals(key)) {
            StandaloneConfig.setWaterHeatPerLiter(d);
        } else if ("ambientTemp".equals(key)) {
            NuclearSimulationEngine.params().ambientTemp = d;
        } else if ("ic2CoolantHeat".equals(key)) {
            StandaloneConfig.setIc2CoolantHeatPerLiter(d);
        } else if ("tempThresholdLow".equals(key)) {
            NuclearSimulationEngine.params().tempThresholdLow = d;
        } else if ("tempThresholdHigh".equals(key)) {
            NuclearSimulationEngine.params().tempThresholdHigh = d;
        } else if ("reactivityPower".equals(key)) {
            NuclearSimulationEngine.params().reactivityPower = d;
        } else if ("thermalFissionMultiplier".equals(key) || "fissionMult".equals(key)) {
            NuclearSimulationEngine.params().thermalFissionMultiplier = d;
        } else if ("globalThermalFissionMultiplier".equals(key)) {
            NuclearSimulationEngine.params().globalThermalFissionMultiplier = d;
        } else if ("globalFastFissionMultiplier".equals(key) || "fastFissionMult".equals(key)) {
            NuclearSimulationEngine.params().globalFastFissionMultiplier = d;
        } else if ("fastAbsorptionDamageMultiplier".equals(key) || "fastDamageMult".equals(key)) {
            NuclearSimulationEngine.params().fastAbsorptionDamageMultiplier = d;
        } else if ("thermalAbsorptionDamageMultiplier".equals(key) || "thermalDamageMult".equals(key)) {
            NuclearSimulationEngine.params().thermalAbsorptionDamageMultiplier = d;
        } else if ("fissionHeatPerNeutron".equals(key) || "fissionHeat".equals(key)) {
            NuclearSimulationEngine.params().fissionHeatPerNeutron = d;
        } else if ("hpWaterBoilingPoint".equals(key) || "hpBoil".equals(key)) {
            NuclearSimulationEngine.params().hpWaterBoilingPoint = d;
        } else if ("gridConductionMultiplier".equals(key) || "gridConductionMult".equals(key)) {
            NuclearSimulationEngine.setGridConductionMultiplier(d);
        } else if ("fuelBurnupMultiplier".equals(key) || "burnupMult".equals(key)) {
            NuclearSimulationEngine.setFuelBurnupMultiplier(d);
        } else if ("denseNbaseMultiplier".equals(key)) {
            NuclearSimulationEngine.params().denseNbaseMultiplier = d;
        } else if ("denseFissionMultiplier".equals(key)) {
            NuclearSimulationEngine.params().denseFissionMultiplier = d;
        } else if ("excitedNbaseMultiplier".equals(key)) {
            NuclearSimulationEngine.params().excitedNbaseMultiplier = d;
        } else if ("excitedFissionMultiplier".equals(key)) {
            NuclearSimulationEngine.params().excitedFissionMultiplier = d;
        } else if ("standardNbaseMultiplier".equals(key)) {
            NuclearSimulationEngine.params().standardNbaseMultiplier = d;
        } else if ("standardFissionMultiplier".equals(key)) {
            NuclearSimulationEngine.params().standardFissionMultiplier = d;
        } else if ("breederNbaseMultiplier".equals(key)) {
            NuclearSimulationEngine.params().breederNbaseMultiplier = d;
        } else if ("breederFissionMultiplier".equals(key)) {
            NuclearSimulationEngine.params().breederFissionMultiplier = d;
        } else if ("ticksPerCycle".equalsIgnoreCase(key) || "ticks_per_cycle".equalsIgnoreCase(key)) {
            int ticks = Math.max(1, (int) d);
            NuclearSimulationEngine.setTicksPerCycle(ticks);
            if (grid != null) {
                grid.setTicksPerCycle(ticks);
            }
        }
    }

    @Export(name = "setTicksPerCycle")
    public static void setTicksPerCycle(int ticks) {
        int t = Math.max(1, ticks);
        NuclearSimulationEngine.setTicksPerCycle(t);
        if (grid != null) {
            grid.setTicksPerCycle(t);
        }
    }

    @Export(name = "getTicksPerCycle")
    public static int getTicksPerCycle() {
        return grid != null ? grid.getTicksPerCycle() : NuclearSimulationEngine.getTicksPerCycle();
    }

    @Export(name = "getLayoutString")
    public static String getLayoutString() {
        return grid != null ? grid.toLayoutString() : "";
    }

    @Export(name = "getStateJson")
    public static String getStateJson() {
        if (grid == null) {
            initGrid(9, 9, NuclearSimulationEngine.PIPE_TIER_PLATINUM);
            loadPreset("BEST_PLATINUM_9X9");
        }
        return buildStateJson(grid, isRunning);
    }

    @Export(name = "getColorMapsJson")
    public static String getColorMapsJson() {
        return buildColorMapsJson();
    }

    public static String buildColorMapsJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"temperature\":[");
        for (int i = 0; i < NuclearColorMaps.TEMPERATURE_COLORS.length; i++) {
            if (i > 0) sb.append(",");
            int c = NuclearColorMaps.TEMPERATURE_COLORS[i];
            int a = (c >> 24) & 0xFF;
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8) & 0xFF;
            int b = c & 0xFF;
            double alpha = Math.round((a / 255.0) * 100.0) / 100.0;
            sb.append("\"rgba(")
                .append(r)
                .append(",")
                .append(g)
                .append(",")
                .append(b)
                .append(",")
                .append(alpha)
                .append(")\"");
        }
        sb.append("],\"neutron\":[");
        for (int i = 0; i < NuclearColorMaps.NEUTRON_COLORS.length; i++) {
            if (i > 0) sb.append(",");
            int c = NuclearColorMaps.NEUTRON_COLORS[i];
            int a = (c >> 24) & 0xFF;
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8) & 0xFF;
            int b = c & 0xFF;
            double alpha = Math.round((a / 255.0) * 100.0) / 100.0;
            sb.append("\"rgba(")
                .append(r)
                .append(",")
                .append(g)
                .append(",")
                .append(b)
                .append(",")
                .append(alpha)
                .append(")\"");
        }
        sb.append("],\"heatOutput\":[");
        for (int i = 0; i < NuclearColorMaps.HEAT_OUTPUT_COLORS.length; i++) {
            if (i > 0) sb.append(",");
            int c = NuclearColorMaps.HEAT_OUTPUT_COLORS[i];
            int a = (c >> 24) & 0xFF;
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8) & 0xFF;
            int b = c & 0xFF;
            double alpha = Math.round((a / 255.0) * 100.0) / 100.0;
            sb.append("\"rgba(")
                .append(r)
                .append(",")
                .append(g)
                .append(",")
                .append(b)
                .append(",")
                .append(alpha)
                .append(")\"");
        }
        sb.append("]}");
        return sb.toString();
    }

    public static String buildStateJson(StandaloneNuclearGrid targetGrid, boolean running) {
        if (targetGrid == null) return "{}";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"layout\":\"")
            .append(escapeJson(targetGrid.toLayoutString()))
            .append("\",");
        sb.append("\"width\":")
            .append(targetGrid.getWidth())
            .append(",");
        sb.append("\"height\":")
            .append(targetGrid.getHeight())
            .append(",");
        sb.append("\"pipeTier\":")
            .append(targetGrid.getPipeTier())
            .append(",");
        sb.append("\"pipeTierName\":\"")
            .append(NuclearSimulationEngine.getPipeTierName(targetGrid.getPipeTier()))
            .append("\",");
        sb.append("\"coolingMode\":\"")
            .append(
                targetGrid.getCoolingMode()
                    .name())
            .append("\",");
        sb.append("\"coolingModeName\":\"")
            .append(targetGrid.getCoolingMode().displayName)
            .append("\",");
        sb.append("\"isTier2ConvectiveAllowed\":")
            .append(targetGrid.getPipeTier() >= NuclearSimulationEngine.PIPE_TIER_PLATINUM)
            .append(",");
        sb.append("\"isCoolantLoopActive\":")
            .append(targetGrid.isCoolantLoopActive())
            .append(",");
        sb.append("\"maxSafeTemp\":")
            .append(NuclearSimulationEngine.getMaxOperatingTemperature(targetGrid.getPipeTier()))
            .append(",");
        sb.append("\"currentTick\":")
            .append(targetGrid.getCurrentTick())
            .append(",");
        sb.append("\"isRunning\":")
            .append(
                running && !targetGrid.isExploded()
                    && !(targetGrid.isStopOnIncidents() && targetGrid.isHaltedByIncident()))
            .append(",");
        sb.append("\"exploded\":")
            .append(targetGrid.isExploded())
            .append(",");
        sb.append("\"explosionReason\":\"")
            .append(escapeJson(targetGrid.getExplosionReason()))
            .append("\",");
        sb.append("\"stopOnIncidents\":")
            .append(targetGrid.isStopOnIncidents())
            .append(",");
        sb.append("\"haltedByIncident\":")
            .append(targetGrid.isHaltedByIncident())
            .append(",");
        sb.append("\"lastHaltIncidentReason\":\"")
            .append(escapeJson(targetGrid.getLastHaltIncidentReason()))
            .append("\",");
        sb.append("\"autoSupplyFuel\":")
            .append(targetGrid.isAutoSupplyFuel())
            .append(",");
        sb.append("\"autoReplaceFuel\":")
            .append(targetGrid.isAutoSupplyFuel())
            .append(",");
        sb.append("\"powerFailed\":")
            .append(targetGrid.isPowerFailed())
            .append(",");
        sb.append("\"powerFailReason\":\"")
            .append(escapeJson(targetGrid.getPowerFailReason()))
            .append("\",");
        // The game's structure rules this core breaks: it would not form, so it does not run
        sb.append("\"structureErrors\":[");
        boolean firstViolation = true;
        for (ReactorStructureRules.Violation v : targetGrid.getStructureViolations()) {
            if (!firstViolation) sb.append(",");
            firstViolation = false;
            sb.append("\"")
                .append(escapeJson(v.message))
                .append("\"");
        }
        sb.append("],");
        sb.append("\"strictMode\":")
            .append(targetGrid.isStrictMode())
            .append(",");
        sb.append("\"lastIncidentTick\":")
            .append(targetGrid.getLastIncidentTick())
            .append(",");
        sb.append("\"incidentLog\":[");
        List<StandaloneNuclearGrid.IncidentEvent> incLog = targetGrid.getIncidentLog();
        for (int i = 0; i < incLog.size(); i++) {
            if (i > 0) sb.append(",");
            StandaloneNuclearGrid.IncidentEvent ev = incLog.get(i);
            sb.append("{")
                .append("\"tick\":")
                .append(ev.tick())
                .append(",")
                .append("\"type\":\"")
                .append(escapeJson(ev.type()))
                .append("\",")
                .append("\"message\":\"")
                .append(escapeJson(ev.message()))
                .append("\",")
                .append("\"damage\":")
                .append(fmt1(ev.damage()))
                .append("}");
        }
        sb.append("],");
        sb.append("\"coreMaxTemp\":")
            .append(fmt2(targetGrid.getCoreMaxTemp()))
            .append(",");
        sb.append("\"coreAvgTemp\":")
            .append(fmt2(targetGrid.getCoreAvgTemp()))
            .append(",");
        sb.append("\"efficiency\":")
            .append(fmt3(targetGrid.getEfficiency()))
            .append(",");
        sb.append("\"reactorDamage\":")
            .append(fmt1(targetGrid.getReactorDamage()))
            .append(",");
        sb.append("\"maintenanceIssues\":")
            .append(targetGrid.getMaintenanceIssues())
            .append(",");
        sb.append("\"maintenanceEfficiency\":")
            .append(fmt3(targetGrid.getMaintenanceEfficiency()))
            .append(",");
        sb.append("\"repairThreshold\":")
            .append(fmt1(targetGrid.getRepairTemperatureThreshold()))
            .append(",");
        sb.append("\"canRepair\":")
            .append(targetGrid.canRepair())
            .append(",");
        sb.append("\"controlRodCount\":")
            .append(targetGrid.getControlRodCount())
            .append(",");
        sb.append("\"lastNeutrons\":")
            .append(targetGrid.getLastNeutronsProduced())
            .append(",");
        sb.append("\"fastAbsorbed\":")
            .append(targetGrid.getLastFastAbsorbed())
            .append(",");
        sb.append("\"thermalAbsorbed\":")
            .append(targetGrid.getLastThermalAbsorbed())
            .append(",");
        sb.append("\"escapedNeutrons\":")
            .append(targetGrid.getLastEscapedNeutrons())
            .append(",");
        sb.append("\"wallReflected\":")
            .append(targetGrid.getLastWallReflected())
            .append(",");
        sb.append("\"wallAbsorbed\":")
            .append(targetGrid.getLastWallAbsorbed())
            .append(",");
        sb.append("\"totalNeutrons\":")
            .append(targetGrid.getTotalNeutronsGenerated())
            .append(",");
        sb.append("\"totalSteam\":")
            .append(targetGrid.getTotalSteamProduced())
            .append(",");
        sb.append("\"totalEU\":")
            .append(fmt0(targetGrid.getTotalEnergyEU()))
            .append(",");
        sb.append("\"totalDeuterium\":")
            .append(targetGrid.getTotalDeuteriumProduced())
            .append(",");
        sb.append("\"totalTritium\":")
            .append(targetGrid.getTotalTritiumProduced())
            .append(",");
        sb.append("\"totalOxygen\":")
            .append(targetGrid.getTotalOxygenProduced())
            .append(",");

        CoolantLoopModel cl = targetGrid.getCoolantLoop();
        SecondaryCoolantEntry secondary = cl.getSecondaryCoolant();
        sb.append("\"coolantLoop\":{");
        sb.append("\"material\":\"")
            .append(
                escapeJson(
                    cl.getMaterial()
                        .name()))
            .append("\",");
        sb.append("\"materialName\":\"")
            .append(escapeJson(cl.getMaterial().displayName))
            .append("\",");
        sb.append("\"materialTier\":\"")
            .append(escapeJson(cl.getMaterial().tierUnlocked))
            .append("\",");
        sb.append("\"maxPressureBar\":")
            .append(
                fmt1(
                    cl.getMaterial()
                        .maxPressureBar()))
            .append(",");
        sb.append("\"maxTempC\":")
            .append(
                fmt1(
                    cl.getMaterial()
                        .maxTemperatureCelsius()))
            .append(",");
        sb.append("\"pipeSize\":\"")
            .append(
                escapeJson(
                    cl.getPipeSize()
                        .name()))
            .append("\",");
        sb.append("\"pipeSizeName\":\"")
            .append(escapeJson(cl.getPipeSize().displayName))
            .append("\",");
        sb.append("\"maxFlowLPerSec\":")
            .append(fmt1(cl.maxFlowLitersPerSecond()))
            .append(",");
        sb.append("\"fluid\":\"")
            .append(
                escapeJson(
                    cl.getFluidType()
                        .name()))
            .append("\",");
        sb.append("\"fluidName\":\"")
            .append(escapeJson(cl.getFluidType().displayName))
            .append("\",");
        sb.append("\"secondaryCoolant\":\"")
            .append(escapeJson(cl.getSecondaryFluidName()))
            .append("\",");
        sb.append("\"secondaryCoolantName\":\"")
            .append(escapeJson(secondaryCoolantLabel(secondary)))
            .append("\",");
        sb.append("\"secondaryBoilingPointC\":")
            .append(fmt1(secondary != null ? secondary.boilingPointCelsius : 0.0))
            .append(",");
        sb.append("\"hatchTier\":\"")
            .append(
                escapeJson(
                    cl.getHatchTier()
                        .name()))
            .append("\",");
        sb.append("\"hatchTierName\":\"")
            .append(escapeJson(cl.getHatchTier().displayName))
            .append("\",");
        sb.append("\"pumpHatchCount\":")
            .append(cl.getPumpHatchCount())
            .append(",");
        sb.append("\"pumpPowerEUt\":")
            .append(fmt1(cl.pumpPowerEUt()))
            .append(",");
        sb.append("\"rotorMaterial\":\"")
            .append(
                escapeJson(
                    cl.getRotorMaterial()
                        .name()))
            .append("\",");
        sb.append("\"rotorSize\":\"")
            .append(
                escapeJson(
                    cl.getRotorSize()
                        .name()))
            .append("\",");
        sb.append("\"rotorEfficiency\":")
            .append(fmt2(cl.getRotorEfficiency()))
            .append(",");
        sb.append("\"pumpMechanicalPowerW\":")
            .append(fmt1(cl.pumpMechanicalPowerWatts()))
            .append(",");
        CoolantLoopModel.Circuit sel = cl.getSelectedCircuit();
        sb.append("\"selectedCircuit\":")
            .append(cl.getSelectedIndex())
            .append(",");
        sb.append("\"dutyCyclePercent\":")
            .append(fmt1(sel.getDutyCyclePercent()))
            .append(",");
        sb.append("\"maxFlowRateLimit\":")
            .append(fmt1(sel.getMaxFlowLPerSec()))
            .append(",");
        sb.append("\"maxPressureLimit\":")
            .append(fmt1(sel.getMaxPressureBar()))
            .append(",");
        sb.append("\"tempBandEnabled\":")
            .append(sel.hasTemperatureBand())
            .append(",");
        sb.append("\"tempBandMin\":")
            .append(fmt1(sel.hasTemperatureBand() ? sel.getControlMinTempC() : 0.0))
            .append(",");
        sb.append("\"tempBandMax\":")
            .append(fmt1(sel.hasTemperatureBand() ? sel.getControlMaxTempC() : 0.0))
            .append(",");
        sb.append("\"tempSensor\":\"")
            .append(
                sel.getSensor()
                    .name())
            .append("\",");
        sb.append("\"pheSheetmetalTier\":")
            .append(sel.getPheSheetmetalTier())
            .append(",");
        sb.append("\"pheHeight\":")
            .append(sel.getPheHeight())
            .append(",");
        sb.append("\"pheLength\":")
            .append(sel.getPheLength())
            .append(",");
        sb.append("\"pheTempBandEnabled\":")
            .append(sel.hasPheTemperatureBand())
            .append(",");
        sb.append("\"pheTempBandMin\":")
            .append(fmt1(sel.hasPheTemperatureBand() ? sel.getPheMinTempC() : 0.0))
            .append(",");
        sb.append("\"pheTempBandMax\":")
            .append(fmt1(sel.hasPheTemperatureBand() ? sel.getPheMaxTempC() : 0.0))
            .append(",");
        sb.append("\"pheTempSensor\":\"")
            .append(
                sel.getPheSensor()
                    .name())
            .append("\",");
        sb.append("\"pheOnFraction\":")
            .append(fmt2(sel.getLastPheOnFraction()))
            .append(",");
        sb.append("\"pumpOnFraction\":")
            .append(fmt2(sel.getLastPumpOnFraction()))
            .append(",");
        sb.append("\"limitReason\":\"")
            .append(escapeJson(sel.getLimitReason()))
            .append("\",");
        sb.append("\"filled\":")
            .append(sel.isFilled())
            .append(",");
        sb.append("\"fillTempC\":")
            .append(fmt1(sel.fillTemperatureCelsius()))
            .append(",");
        sb.append("\"circuits\":[");
        int circuitIdx = 0;
        for (CoolantLoopModel.Circuit c : cl.getCircuits()) {
            if (circuitIdx > 0) sb.append(",");
            sb.append("{\"index\":")
                .append(circuitIdx++)
                .append(",\"code\":\"")
                .append(circuitIdx == 1 ? "CP" : "CP" + circuitIdx)
                .append("\",\"fluid\":\"")
                .append(c.getFluidType().displayName)
                .append("\",\"passages\":")
                .append(c.getPassageCount())
                .append(",\"copies\":")
                .append(c.getCopies())
                .append(",\"flowLPerSec\":")
                .append(fmt1(c.getCurrentFlowRateLPerSec()))
                .append(",\"coldLegC\":")
                .append(fmt1(c.getColdLegTempCelsius()))
                .append(",\"hotLegC\":")
                .append(fmt1(c.getHotLegTempCelsius()))
                .append(",\"heatHut\":")
                .append(fmt1(c.getLastHeatExtractedHut()))
                .append(",\"steamLt\":")
                .append(fmt1(c.getLastSteamLt() + c.getLastSuperheatedSteamLt() + c.getLastSupercriticalSteamLt()))
                .append(",\"pumpEUt\":")
                .append(fmt1(c.getLastPumpPowerEUt()))
                .append(",\"pumpOn\":")
                .append(fmt2(c.getLastPumpOnFraction()))
                .append("}");
        }
        sb.append("],");
        sb.append("\"loopCount\":")
            .append(cl.getLoopCount())
            .append(",");
        sb.append("\"pheCount\":")
            .append(cl.getPheCount())
            .append(",");
        sb.append("\"pheHatchTier\":\"")
            .append(
                escapeJson(
                    cl.getPheHatchTier()
                        .name()))
            .append("\",");
        sb.append("\"pheHatchCount\":")
            .append(cl.getPheHatchCount())
            .append(",");
        sb.append("\"phePowerEUt\":")
            .append(fmt1(cl.phePowerEUt()))
            .append(",");
        sb.append("\"currentFlowRateLPerSec\":")
            .append(fmt1(cl.getCurrentFlowRateLPerSec()))
            .append(",");
        sb.append("\"currentPressureBar\":")
            .append(fmt2(cl.getCurrentPressureBar()))
            .append(",");
        sb.append("\"currentCoolantTempCelsius\":")
            .append(fmt1(cl.getCurrentCoolantTempCelsius()))
            .append(",");
        sb.append("\"coldLegTempCelsius\":")
            .append(fmt1(cl.getColdLegTempCelsius()))
            .append(",");
        sb.append("\"peakLoopTempCelsius\":")
            .append(fmt1(cl.getPeakLoopTempCelsius()))
            .append(",");
        sb.append("\"lastHeatExtractedWatts\":")
            .append(fmt1(cl.getLastHeatExtractedWatts()))
            .append(",");
        sb.append("\"lastHeatExtractedHut\":")
            .append(fmt1(cl.getLastHeatExtractedHut()))
            .append(",");
        sb.append("\"lastSteamLt\":")
            .append(fmt1(cl.getLastSteamLt()))
            .append(",");
        sb.append("\"lastSuperheatedSteamLt\":")
            .append(fmt1(cl.getLastSuperheatedSteamLt()))
            .append(",");
        sb.append("\"lastSupercriticalSteamLt\":")
            .append(fmt1(cl.getLastSupercriticalSteamLt()))
            .append(",");
        sb.append("\"lastSecondarySteamProducedLt\":")
            .append(fmt1(cl.getLastSecondarySteamProducedLt()))
            .append(",");
        sb.append("\"lastSecondaryGasLt\":")
            .append(fmt1(cl.getLastSecondaryGasLt()))
            .append(",");
        sb.append("\"lastSecondaryWaterBoiledLt\":")
            .append(fmt1(cl.getLastSecondaryWaterBoiledLt()))
            .append(",");
        sb.append("\"lastPumpPowerEUt\":")
            .append(fmt1(cl.getLastPumpPowerEUt()))
            .append(",");
        sb.append("\"lastPhePowerEUt\":")
            .append(fmt1(cl.getLastPhePowerEUt()))
            .append(",");
        sb.append("\"totalDeuteriumProduced\":")
            .append(cl.getTotalDeuteriumProduced())
            .append(",");
        sb.append("\"totalTritiumProduced\":")
            .append(cl.getTotalTritiumProduced())
            .append(",");
        sb.append("\"totalOxygenProduced\":")
            .append(cl.getTotalOxygenProduced())
            .append(",");
        sb.append("\"totalHeatExtractedHut\":")
            .append(fmt1(cl.getTotalHeatExtractedHut()))
            .append(",");
        sb.append("\"totalSecondarySteamProduced\":")
            .append(cl.getTotalSecondarySteamProduced())
            .append(",");
        sb.append("\"ruptured\":")
            .append(cl.isRuptured())
            .append(",");
        sb.append("\"ruptureReason\":\"")
            .append(escapeJson(cl.getRuptureReason()))
            .append("\",");
        sb.append("\"attachedPoints\":[");
        int ptIdx = 0;
        for (CoolantLoopModel.GridCoord pt : cl.getAttachedPoints()) {
            if (ptIdx++ > 0) sb.append(",");
            sb.append("\"")
                .append(pt.x)
                .append(",")
                .append(pt.y)
                .append("\"");
        }
        sb.append("],");
        sb.append("\"energyHatchTiers\":[");
        int tierIdx = 0;
        for (CoolantLoopModel.EnergyHatchTier t : CoolantLoopModel.EnergyHatchTier.values()) {
            if (tierIdx++ > 0) sb.append(",");
            sb.append("{\"id\":\"")
                .append(t.name())
                .append("\",\"name\":\"")
                .append(t.displayName)
                .append("\",\"voltage\":")
                .append(fmt1(t.voltageEU))
                .append("}");
        }
        sb.append("],");
        sb.append("\"rotorMaterials\":[");
        int rotorIdx = 0;
        for (CoolantLoopModel.RotorMaterial m : CoolantLoopModel.RotorMaterial.values()) {
            if (rotorIdx++ > 0) sb.append(",");
            sb.append("{\"id\":\"")
                .append(m.name())
                .append("\",\"name\":\"")
                .append(escapeJson(m.displayName))
                .append("\",\"efficiency\":")
                .append(fmt2(CoolantLoopModel.rotorEfficiency(m, cl.getRotorSize())))
                .append("}");
        }
        sb.append("],");
        sb.append("\"rotorSizes\":[");
        int sizeIdx = 0;
        for (CoolantLoopModel.RotorSize size : CoolantLoopModel.RotorSize.values()) {
            if (sizeIdx++ > 0) sb.append(",");
            sb.append("{\"id\":\"")
                .append(size.name())
                .append("\",\"name\":\"")
                .append(size.displayName)
                .append("\"}");
        }
        sb.append("],");
        sb.append("\"secondaryCoolants\":")
            .append(secondaryCoolantsJson())
            .append(",");
        sb.append("\"allowedMaterials\":[");
        int matIdx = 0;
        for (CoolantLoopModel.LoopMaterial mat : CoolantLoopModel.LoopMaterial.values()) {
            if (mat.isAllowedInReactorTier(targetGrid.getPipeTier())) {
                if (matIdx++ > 0) sb.append(",");
                sb.append("{\"id\":\"")
                    .append(mat.name())
                    .append("\",\"name\":\"")
                    .append(mat.displayName)
                    .append("\",\"maxPressure\":")
                    .append(fmt1(mat.maxPressureBar()))
                    .append(",\"tier\":\"")
                    .append(mat.tierUnlocked)
                    .append("\",\"progressionAppropriate\":")
                    .append(mat.isProgressionAppropriate(targetGrid.getPipeTier()))
                    .append("}");
            }
        }
        sb.append("]");
        sb.append("},");
        sb.append("\"grossPowerEUt\":")
            .append(fmt1(targetGrid.getGrossPowerEUt()))
            .append(",");
        sb.append("\"pumpPowerEUt\":")
            .append(fmt1(targetGrid.getPumpPowerEUt()))
            .append(",");

        sb.append("\"turbineMaterial\":\"")
            .append(
                targetGrid.getTurbineMaterial()
                    .name())
            .append("\",");
        sb.append("\"turbineMaterialName\":\"")
            .append(targetGrid.getTurbineMaterial().displayName)
            .append("\",");
        sb.append("\"turbineSize\":\"")
            .append(
                targetGrid.getTurbineSize()
                    .name())
            .append("\",");
        sb.append("\"turbineSizeName\":\"")
            .append(targetGrid.getTurbineSize().displayName)
            .append("\",");
        sb.append("\"turbineFitting\":\"")
            .append(
                targetGrid.getTurbineFitting()
                    .name())
            .append("\",");
        sb.append("\"turbineFittingName\":\"")
            .append(targetGrid.getTurbineFitting().displayName)
            .append("\",");
        sb.append("\"turbineMaterials\":[");
        int turbMatIdx = 0;
        for (TurbineCalculator.TurbineMaterial m : TurbineCalculator.TurbineMaterial.values()) {
            if (turbMatIdx++ > 0) sb.append(",");
            sb.append("{\"id\":\"")
                .append(m.name())
                .append("\",\"name\":\"")
                .append(escapeJson(m.displayName))
                .append(" (")
                .append((int) Math.round(m.tightEff * 100))
                .append("% / ")
                .append((int) Math.round(m.looseEff * 100))
                .append("%)\",\"tightEff\":")
                .append(fmt2(m.tightEff))
                .append(",\"looseEff\":")
                .append(fmt2(m.looseEff))
                .append("}");
        }
        sb.append("],");

        sb.append("\"flowRegularSteam\":")
            .append(fmt1(targetGrid.getFlowRegularSteam()))
            .append(",");
        sb.append("\"flowSuperheatedSteam\":")
            .append(fmt1(targetGrid.getFlowSuperheatedSteam()))
            .append(",");
        sb.append("\"flowSupercriticalSteam\":")
            .append(fmt1(targetGrid.getFlowSupercriticalSteam()))
            .append(",");
        sb.append("\"flowHeavyWaterSteam\":")
            .append(fmt1(targetGrid.getFlowHeavyWaterSteam()))
            .append(",");
        sb.append("\"flowHPHeavyWaterSteam\":")
            .append(fmt1(targetGrid.getFlowHPHeavyWaterSteam()))
            .append(",");
        sb.append("\"flowHotCoolant\":")
            .append(fmt1(targetGrid.getFlowHotCoolant()))
            .append(",");
        sb.append("\"flowDirectEU\":")
            .append(fmt1(targetGrid.getFlowDirectEU()))
            .append(",");

        TurbineCalculator.PowerEstimationResult p = targetGrid.getLastPowerResult();
        sb.append("\"powerEstimate\":{");
        sb.append("\"totalPowerEUt\":")
            .append(fmt1(p.totalPowerEUt))
            .append(",");
        sb.append("\"directPowerEUt\":")
            .append(fmt1(p.directPowerEUt))
            .append(",");
        sb.append("\"isLST\":")
            .append(p.isLST)
            .append(",");
        sb.append("\"lstPowerEUt\":")
            .append(fmt1(p.lstPowerEUt))
            .append(",");
        sb.append("\"xlstPowerEUt\":")
            .append(fmt1(p.xlstPowerEUt))
            .append(",");
        sb.append("\"xlstHpPowerEUt\":")
            .append(fmt1(p.xlstHpPowerEUt))
            .append(",");
        sb.append("\"xlstScPowerEUt\":")
            .append(fmt1(p.xlstScPowerEUt))
            .append(",");
        sb.append("\"coolantMachine\":\"")
            .append(p.coolantMachine)
            .append("\",");
        sb.append("\"coolantMachineMode\":\"")
            .append(p.coolantMachineMode)
            .append("\",");
        sb.append("\"coolantSteamProduced\":")
            .append(fmt1(p.coolantSteamProduced))
            .append(",");
        sb.append("\"coolantWaterConsumed\":")
            .append(fmt1(p.coolantWaterConsumed))
            .append(",");
        sb.append("\"coolantMachineCount\":")
            .append(fmt0(p.coolantMachineCount))
            .append(",");
        sb.append("\"lstTurbinesNeeded\":")
            .append(fmt2(p.lstTurbinesNeeded))
            .append(",");
        sb.append("\"xlstTurbinesNeeded\":")
            .append(fmt2(p.xlstTurbinesNeeded))
            .append(",");
        sb.append("\"xlstHpTurbinesNeeded\":")
            .append(fmt2(p.xlstHpTurbinesNeeded))
            .append(",");
        sb.append("\"xlstScTurbinesNeeded\":")
            .append(fmt2(p.xlstScTurbinesNeeded))
            .append(",");
        sb.append("\"totalTurbinesNeeded\":")
            .append(fmt2(p.totalTurbinesNeeded))
            .append(",");
        sb.append("\"efficiency\":")
            .append(fmt3(p.efficiency))
            .append(",");
        sb.append("\"optFlowPerTurbine\":")
            .append(fmt0(p.optFlowPerTurbine))
            .append(",");
        sb.append("\"machineLimitExceeded\":")
            .append(p.machineLimitExceeded)
            .append(",");
        sb.append("\"voidedCoolantFlow\":")
            .append(fmt1(p.voidedCoolantFlow))
            .append(",");
        sb.append("\"voidedSteamFlow\":")
            .append(fmt1(p.totalVoidedSteamFlow))
            .append(",");
        sb.append("\"voidedSupercriticalSteam\":")
            .append(fmt1(p.voidedSupercriticalSteam))
            .append(",");
        sb.append("\"voidedSuperheatedSteam\":")
            .append(fmt1(p.voidedSuperheatedSteam))
            .append(",");
        sb.append("\"voidedRegularSteam\":")
            .append(fmt1(p.voidedRegularSteam));
        sb.append("},");

        TurbineCalculator.ScenarioHypotheticalResult sc = targetGrid.getLastScenariosResult();
        if (sc == null) {
            sc = TurbineCalculator.calculateBothScenarios(
                targetGrid.getPipeTier(),
                targetGrid.getFlowRegularSteam(),
                targetGrid.getFlowSuperheatedSteam(),
                targetGrid.getFlowSupercriticalSteam(),
                targetGrid.getFlowHeavyWaterSteam(),
                targetGrid.getFlowHPHeavyWaterSteam(),
                targetGrid.getFlowHotCoolant(),
                targetGrid.getFlowDirectEU(),
                null,
                null,
                targetGrid.getMachineLimits());
        }
        sb.append("\"scenarios\":{");
        sb.append("\"tight\":{")
            .append("\"material\":\"")
            .append(sc.tightConfig.material.name())
            .append("\",")
            .append("\"materialName\":\"")
            .append(sc.tightConfig.material.displayName)
            .append("\",")
            .append("\"size\":\"")
            .append(sc.tightConfig.size.name())
            .append("\",")
            .append("\"sizeName\":\"")
            .append(sc.tightConfig.size.displayName)
            .append("\",")
            .append("\"mode\":\"")
            .append(sc.tightConfig.mode.name())
            .append("\",")
            .append("\"efficiency\":")
            .append(fmt2(sc.tightConfig.efficiency))
            .append(",")
            .append("\"description\":\"")
            .append(sc.tightConfig.description)
            .append("\",")
            .append("\"powerEUt\":")
            .append(fmt1(sc.tightResult.totalPowerEUt))
            .append(",")
            .append("\"totalTurbinesNeeded\":")
            .append(fmt2(sc.tightResult.totalTurbinesNeeded))
            .append(",")
            .append("\"lstTurbinesNeeded\":")
            .append(fmt2(sc.tightResult.lstTurbinesNeeded))
            .append(",")
            .append("\"xlstTurbinesNeeded\":")
            .append(fmt2(sc.tightResult.xlstTurbinesNeeded))
            .append(",")
            .append("\"xlstHpTurbinesNeeded\":")
            .append(fmt2(sc.tightResult.xlstHpTurbinesNeeded))
            .append(",")
            .append("\"xlstScTurbinesNeeded\":")
            .append(fmt2(sc.tightResult.xlstScTurbinesNeeded))
            .append(",")
            .append("\"coolantMachine\":\"")
            .append(sc.tightResult.coolantMachine)
            .append("\",")
            .append("\"coolantMachineCount\":")
            .append(fmt1(sc.tightResult.coolantMachineCount))
            .append("},");
        sb.append("\"loose\":{")
            .append("\"material\":\"")
            .append(sc.looseConfig.material.name())
            .append("\",")
            .append("\"materialName\":\"")
            .append(sc.looseConfig.material.displayName)
            .append("\",")
            .append("\"size\":\"")
            .append(sc.looseConfig.size.name())
            .append("\",")
            .append("\"sizeName\":\"")
            .append(sc.looseConfig.size.displayName)
            .append("\",")
            .append("\"mode\":\"")
            .append(sc.looseConfig.mode.name())
            .append("\",")
            .append("\"efficiency\":")
            .append(fmt2(sc.looseConfig.efficiency))
            .append(",")
            .append("\"description\":\"")
            .append(sc.looseConfig.description)
            .append("\",")
            .append("\"powerEUt\":")
            .append(fmt1(sc.looseResult.totalPowerEUt))
            .append(",")
            .append("\"totalTurbinesNeeded\":")
            .append(fmt2(sc.looseResult.totalTurbinesNeeded))
            .append(",")
            .append("\"lstTurbinesNeeded\":")
            .append(fmt2(sc.looseResult.lstTurbinesNeeded))
            .append(",")
            .append("\"xlstTurbinesNeeded\":")
            .append(fmt2(sc.looseResult.xlstTurbinesNeeded))
            .append(",")
            .append("\"xlstHpTurbinesNeeded\":")
            .append(fmt2(sc.looseResult.xlstHpTurbinesNeeded))
            .append(",")
            .append("\"xlstScTurbinesNeeded\":")
            .append(fmt2(sc.looseResult.xlstScTurbinesNeeded))
            .append(",")
            .append("\"coolantMachine\":\"")
            .append(sc.looseResult.coolantMachine)
            .append("\",")
            .append("\"coolantMachineCount\":")
            .append(fmt1(sc.looseResult.coolantMachineCount))
            .append("},");
        sb.append("\"turbineReductionRatio\":")
            .append(fmt2(sc.turbineReductionRatio))
            .append(",");
        sb.append("\"powerReductionRatio\":")
            .append(fmt2(sc.powerReductionRatio));
        sb.append("},");

        sb.append("\"params\":{");
        sb.append("\"ticksPerCycle\":")
            .append(grid != null ? grid.getTicksPerCycle() : NuclearSimulationEngine.params().ticksPerCycle)
            .append(",");
        sb.append("\"huPerDegree\":")
            .append(fmt1(NuclearSimulationEngine.params().huPerDegree))
            .append(",");
        sb.append("\"hatchCapacity\":")
            .append(NuclearSimulationEngine.params().hatchCoolantCapacity)
            .append(",");
        sb.append("\"turnoverCurve\":\"")
            .append(NuclearSimulationEngine.params().turnoverCurve.name())
            .append("\",");
        sb.append("\"turnoverDeltaTMax\":")
            .append(fmt1(NuclearSimulationEngine.params().turnoverDeltaTMax))
            .append(",");
        sb.append("\"turnoverExponent\":")
            .append(fmt2(NuclearSimulationEngine.params().turnoverExponent))
            .append(",");
        sb.append("\"coolantFeedRate\":")
            .append(NuclearSimulationEngine.params().coolantFeedRate)
            .append(",");
        sb.append("\"coolingHeatPerLiter\":")
            .append(fmt1(NuclearSimulationEngine.params().coolingHeatPerLiter))
            .append(",");
        sb.append("\"ambientTemp\":")
            .append(fmt1(NuclearSimulationEngine.params().ambientTemp))
            .append(",");
        sb.append("\"ic2CoolantHeatPerLiter\":")
            .append(fmt1(NuclearSimulationEngine.ic2CoolantHeatPerLiter))
            .append(",");
        sb.append("\"tempThresholdLow\":")
            .append(fmt1(NuclearSimulationEngine.params().tempThresholdLow))
            .append(",");
        sb.append("\"tempThresholdHigh\":")
            .append(fmt1(NuclearSimulationEngine.params().tempThresholdHigh))
            .append(",");
        sb.append("\"reactivityPower\":")
            .append(fmt2(NuclearSimulationEngine.params().reactivityPower))
            .append(",");
        sb.append("\"thermalFissionMultiplier\":")
            .append(fmt2(NuclearSimulationEngine.params().thermalFissionMultiplier))
            .append(",");
        sb.append("\"globalThermalFissionMultiplier\":")
            .append(fmt2(NuclearSimulationEngine.params().globalThermalFissionMultiplier))
            .append(",");
        sb.append("\"globalFastFissionMultiplier\":")
            .append(fmt2(NuclearSimulationEngine.params().globalFastFissionMultiplier))
            .append(",");
        sb.append("\"fastAbsorptionDamageMultiplier\":")
            .append(fmt2(NuclearSimulationEngine.params().fastAbsorptionDamageMultiplier))
            .append(",");
        sb.append("\"thermalAbsorptionDamageMultiplier\":")
            .append(fmt2(NuclearSimulationEngine.params().thermalAbsorptionDamageMultiplier))
            .append(",");
        sb.append("\"fissionHeatPerNeutron\":")
            .append(fmt1(NuclearSimulationEngine.params().fissionHeatPerNeutron))
            .append(",");
        sb.append("\"hpWaterBoilingPoint\":")
            .append(fmt1(NuclearSimulationEngine.params().hpWaterBoilingPoint))
            .append(",");
        sb.append("\"baseHatchConductance\":")
            .append(fmt3(NuclearSimulationEngine.params().baseHatchConductance))
            .append(",");
        sb.append("\"hatchConductanceTierRatio\":")
            .append(fmt3(NuclearSimulationEngine.params().hatchConductanceTierRatio))
            .append(",");
        sb.append("\"coreHatchHeatCapacity\":")
            .append(fmt1(NuclearSimulationEngine.params().coreHatchHeatCapacity))
            .append(",");
        sb.append("\"gridConductionMultiplier\":")
            .append(fmt3(NuclearSimulationEngine.params().gridConductionMultiplier))
            .append(",");
        sb.append("\"fuelBurnupMultiplier\":")
            .append(fmt3(NuclearSimulationEngine.params().fuelBurnupMultiplier))
            .append(",");
        sb.append("\"denseNbaseMultiplier\":")
            .append(fmt2(NuclearSimulationEngine.params().denseNbaseMultiplier))
            .append(",");
        sb.append("\"denseFissionMultiplier\":")
            .append(fmt2(NuclearSimulationEngine.params().denseFissionMultiplier))
            .append(",");
        sb.append("\"excitedNbaseMultiplier\":")
            .append(fmt2(NuclearSimulationEngine.params().excitedNbaseMultiplier))
            .append(",");
        sb.append("\"excitedFissionMultiplier\":")
            .append(fmt2(NuclearSimulationEngine.params().excitedFissionMultiplier))
            .append(",");
        sb.append("\"standardNbaseMultiplier\":")
            .append(fmt2(NuclearSimulationEngine.params().standardNbaseMultiplier))
            .append(",");
        sb.append("\"standardFissionMultiplier\":")
            .append(fmt2(NuclearSimulationEngine.params().standardFissionMultiplier))
            .append(",");
        sb.append("\"breederNbaseMultiplier\":")
            .append(fmt2(NuclearSimulationEngine.params().breederNbaseMultiplier))
            .append(",");
        sb.append("\"breederFissionMultiplier\":")
            .append(fmt2(NuclearSimulationEngine.params().breederFissionMultiplier));
        sb.append("},");

        // Tiles
        sb.append("\"tiles\":[");
        for (int y = 0; y < targetGrid.getHeight(); y++) {
            for (int x = 0; x < targetGrid.getWidth(); x++) {
                SimTile t = targetGrid.getTile(x, y);
                if (x > 0 || y > 0) sb.append(",");
                sb.append("{");
                sb.append("\"x\":")
                    .append(x)
                    .append(",");
                sb.append("\"y\":")
                    .append(y)
                    .append(",");
                sb.append("\"type\":\"")
                    .append(
                        t.getType()
                            .name())
                    .append("\",");
                sb.append("\"name\":\"")
                    .append(t.getType().displayName)
                    .append("\",");
                sb.append("\"code\":\"")
                    .append(t.getLayoutCode())
                    .append("\",");
                sb.append("\"temp\":")
                    .append(fmt1(t.getTemperature()))
                    .append(",");
                sb.append("\"isFuel\":")
                    .append(t.isFuel())
                    .append(",");
                sb.append("\"durability\":")
                    .append(t.getDurability())
                    .append(",");
                sb.append("\"durabilityPct\":")
                    .append(fmt1(t.getDurabilityPercent()))
                    .append(",");
                sb.append("\"fluidName\":\"")
                    .append(t.getInputFluidName())
                    .append("\",");
                sb.append("\"fluidAmount\":")
                    .append(t.getInputFluidAmount())
                    .append(",");
                sb.append("\"fluidCapacity\":")
                    .append(t.getInputFluidCapacity())
                    .append(",");
                sb.append("\"wasDry\":")
                    .append(t.isWasDry())
                    .append(",");
                sb.append("\"lastProduced\":")
                    .append(t.getLastTickProduced())
                    .append(",");
                sb.append("\"steamAmount\":")
                    .append(t.getTotalSteamProduced())
                    .append(",");
                sb.append("\"outputFluidAmount\":")
                    .append(t.getOutputFluidAmount())
                    .append(",");
                sb.append("\"fastFlux\":")
                    .append(t.getLastFastFlux())
                    .append(",");
                sb.append("\"thermalFlux\":")
                    .append(t.getLastThermalFlux())
                    .append(",");
                sb.append("\"totalFlux\":")
                    .append(t.getLastTotalFlux())
                    .append(",");
                sb.append("\"fastAbsorbed\":")
                    .append(t.getLastFastAbsorbed())
                    .append(",");
                sb.append("\"thermalAbsorbed\":")
                    .append(t.getLastThermalAbsorbed())
                    .append(",");
                sb.append("\"directEU\":")
                    .append(t.getDirectEUProduced())
                    .append(",");
                sb.append("\"isDepleted\":")
                    .append(t.isDepleted())
                    .append(",");
                sb.append("\"lastDurabilityLoss\":")
                    .append(fmt2(t.getLastDurabilityLoss()))
                    .append(",");
                sb.append("\"lastLiquidBurned\":")
                    .append(t.getLastLiquidFuelBurned())
                    .append(",");
                sb.append("\"depletedName\":\"")
                    .append(escapeJson(t.getDepletedDisplayName()))
                    .append("\",");
                sb.append("\"depletedCode\":\"")
                    .append(t.getDepletedCode())
                    .append("\",");
                sb.append("\"hasControlRod\":")
                    .append(t.hasControlRod())
                    .append(",");
                sb.append("\"controlRodType\":\"")
                    .append(
                        t.getControlRodType()
                            .name())
                    .append("\",");
                sb.append("\"controlRodTypeName\":\"")
                    .append(escapeJson(t.getControlRodType().displayName))
                    .append("\",");
                sb.append("\"controlRodInsertion\":")
                    .append(t.getControlRodInsertion())
                    .append(",");
                sb.append("\"controlRodFastAbsorbed\":")
                    .append(t.getLastControlRodFastAbsorbed())
                    .append(",");
                sb.append("\"controlRodThermalAbsorbed\":")
                    .append(t.getLastControlRodThermalAbsorbed())
                    .append(",");
                sb.append("\"heatOutput\":")
                    .append(fmt1(t.getLastHeatOutput()));
                sb.append("}");
            }
        }
        sb.append("],");

        // Telemetry history
        sb.append("\"history\":[");
        List<StandaloneNuclearGrid.TickTelemetry> hist = targetGrid.getHistory();
        int start = Math.max(0, hist.size() - 60);
        for (int i = start; i < hist.size(); i++) {
            if (i > start) sb.append(",");
            StandaloneNuclearGrid.TickTelemetry entry = hist.get(i);
            sb.append("{");
            sb.append("\"t\":")
                .append(entry.tick())
                .append(",");
            sb.append("\"maxT\":")
                .append(fmt1(entry.maxTemp()))
                .append(",");
            sb.append("\"avgT\":")
                .append(fmt1(entry.avgTemp()))
                .append(",");
            sb.append("\"eff\":")
                .append(fmt2(entry.efficiency()))
                .append(",");
            sb.append("\"power\":")
                .append(fmt1(entry.powerEUt()))
                .append(",");
            sb.append("\"safe\":")
                .append(entry.safe());
            sb.append("}");
        }
        sb.append("],");

        // Byproducts (depleted fuel items, depleted liquid fuels, and isotopes)
        List<StandaloneNuclearGrid.SolidFuelByproduct> solidByproducts = targetGrid.getSolidFuelByproducts();
        List<StandaloneNuclearGrid.LiquidFuelByproduct> liquidByproducts = targetGrid.getLiquidFuelByproducts();
        List<StandaloneNuclearGrid.IsotopeByproduct> isotopeByproducts = targetGrid.getIsotopeByproducts();

        double totalSolidItemsPerMin = 0.0;
        double totalSolidItemsPerHour = 0.0;
        long totalDepletedItemsProduced = 0;
        for (StandaloneNuclearGrid.SolidFuelByproduct s : solidByproducts) {
            totalSolidItemsPerMin += s.itemsPerMinute;
            totalSolidItemsPerHour += s.itemsPerHour;
            totalDepletedItemsProduced += s.totalProduced;
        }

        double totalLiquidLitersPerMin = 0.0;
        double totalLiquidLitersPerHour = 0.0;
        long totalDepletedLiquidProduced = 0;
        for (StandaloneNuclearGrid.LiquidFuelByproduct l : liquidByproducts) {
            totalLiquidLitersPerMin += l.litersPerMinute;
            totalLiquidLitersPerHour += l.litersPerHour;
            totalDepletedLiquidProduced += l.totalLiters;
        }

        sb.append("\"byproducts\":{");
        sb.append("\"autoReplaceFuel\":")
            .append(targetGrid.isAutoReplaceFuel())
            .append(",");
        sb.append("\"totalSolidItemsPerMin\":")
            .append(fmt2(totalSolidItemsPerMin))
            .append(",");
        sb.append("\"totalSolidItemsPerHour\":")
            .append(fmt1(totalSolidItemsPerHour))
            .append(",");
        sb.append("\"totalLiquidLitersPerMin\":")
            .append(fmt1(totalLiquidLitersPerMin))
            .append(",");
        sb.append("\"totalLiquidLitersPerHour\":")
            .append(fmt0(totalLiquidLitersPerHour))
            .append(",");
        sb.append("\"totalDepletedItemsProduced\":")
            .append(totalDepletedItemsProduced)
            .append(",");
        sb.append("\"totalDepletedLiquidProduced\":")
            .append(totalDepletedLiquidProduced)
            .append(",");

        sb.append("\"solidRods\":[");
        for (int i = 0; i < solidByproducts.size(); i++) {
            if (i > 0) sb.append(",");
            StandaloneNuclearGrid.SolidFuelByproduct s = solidByproducts.get(i);
            sb.append("{");
            sb.append("\"type\":\"")
                .append(s.type.name())
                .append("\",");
            sb.append("\"fuelName\":\"")
                .append(escapeJson(s.fuelName))
                .append("\",");
            sb.append("\"fuelCode\":\"")
                .append(s.fuelCode)
                .append("\",");
            sb.append("\"depletedName\":\"")
                .append(escapeJson(s.depletedName))
                .append("\",");
            sb.append("\"depletedCode\":\"")
                .append(s.depletedCode)
                .append("\",");
            sb.append("\"activeRods\":")
                .append(s.activeRods)
                .append(",");
            sb.append("\"itemsPerMin\":")
                .append(fmt2(s.itemsPerMinute))
                .append(",");
            sb.append("\"itemsPerHour\":")
                .append(fmt1(s.itemsPerHour))
                .append(",");
            sb.append("\"totalProduced\":")
                .append(s.totalProduced)
                .append(",");
            sb.append("\"avgLifespanMin\":")
                .append(Double.isInfinite(s.avgLifespanMinutes) ? "\"Infinity\"" : fmt1(s.avgLifespanMinutes));
            sb.append("}");
        }
        sb.append("],");

        sb.append("\"liquidFuels\":[");
        for (int i = 0; i < liquidByproducts.size(); i++) {
            if (i > 0) sb.append(",");
            StandaloneNuclearGrid.LiquidFuelByproduct l = liquidByproducts.get(i);
            sb.append("{");
            sb.append("\"type\":\"")
                .append(l.type.name())
                .append("\",");
            sb.append("\"fluidName\":\"")
                .append(l.fluidName)
                .append("\",");
            sb.append("\"displayName\":\"")
                .append(escapeJson(l.displayName))
                .append("\",");
            sb.append("\"activeHatches\":")
                .append(l.activeHatches)
                .append(",");
            sb.append("\"litersPerMin\":")
                .append(fmt1(l.litersPerMinute))
                .append(",");
            sb.append("\"litersPerHour\":")
                .append(fmt0(l.litersPerHour))
                .append(",");
            sb.append("\"totalLiters\":")
                .append(l.totalLiters);
            sb.append("}");
        }
        sb.append("],");

        sb.append("\"isotopes\":[");
        for (int i = 0; i < isotopeByproducts.size(); i++) {
            if (i > 0) sb.append(",");
            StandaloneNuclearGrid.IsotopeByproduct iso = isotopeByproducts.get(i);
            sb.append("{");
            sb.append("\"name\":\"")
                .append(iso.name)
                .append("\",");
            sb.append("\"code\":\"")
                .append(iso.code)
                .append("\",");
            sb.append("\"litersPerMin\":")
                .append(fmt2(iso.litersPerMinute))
                .append(",");
            sb.append("\"litersPerHour\":")
                .append(fmt1(iso.litersPerHour))
                .append(",");
            sb.append("\"totalLiters\":")
                .append(iso.totalLiters);
            sb.append("}");
        }
        sb.append("],");

        List<StandaloneNuclearGrid.RawMaterialBalance> rawMaterials = targetGrid.getRawMaterialBalances();
        sb.append("\"rawMaterials\":[");
        for (int i = 0; i < rawMaterials.size(); i++) {
            if (i > 0) sb.append(",");
            StandaloneNuclearGrid.RawMaterialBalance r = rawMaterials.get(i);
            sb.append("{");
            sb.append("\"name\":\"")
                .append(escapeJson(r.name))
                .append("\",");
            sb.append("\"code\":\"")
                .append(r.code)
                .append("\",");
            sb.append("\"unit\":\"")
                .append(r.unit)
                .append("\",");
            sb.append("\"consumedPerMin\":")
                .append(fmt2(r.consumedPerMinute))
                .append(",");
            sb.append("\"consumedPerHour\":")
                .append(fmt1(r.consumedPerHour))
                .append(",");
            sb.append("\"producedPerMin\":")
                .append(fmt2(r.producedPerMinute))
                .append(",");
            sb.append("\"producedPerHour\":")
                .append(fmt1(r.producedPerHour))
                .append(",");
            sb.append("\"netPerMin\":")
                .append(fmt2(r.netPerMinute))
                .append(",");
            sb.append("\"netPerHour\":")
                .append(fmt1(r.netPerHour));
            sb.append("}");
        }
        sb.append("]");
        sb.append("}");

        sb.append("}");
        return sb.toString();
    }

    private static String fmt0(double val) {
        if (Double.isNaN(val) || Double.isInfinite(val)) return "0";
        return Long.toString(Math.round(val));
    }

    private static String fmt1(double val) {
        if (Double.isNaN(val) || Double.isInfinite(val)) return "0.0";
        long r = Math.round(val * 10.0);
        long intPart = r / 10;
        long fracPart = Math.abs(r % 10);
        return intPart + "." + fracPart;
    }

    private static String fmt2(double val) {
        if (Double.isNaN(val) || Double.isInfinite(val)) return "0.00";
        long r = Math.round(val * 100.0);
        long intPart = r / 100;
        long fracPart = Math.abs(r % 100);
        return intPart + "." + (fracPart < 10 ? "0" : "") + fracPart;
    }

    private static String fmt3(double val) {
        if (Double.isNaN(val) || Double.isInfinite(val)) return "0.000";
        long r = Math.round(val * 1000.0);
        long intPart = r / 1000;
        long fracPart = Math.abs(r % 1000);
        if (fracPart < 10) return intPart + ".00" + fracPart;
        if (fracPart < 100) return intPart + ".0" + fracPart;
        return intPart + "." + fracPart;
    }

    private static String escapeJson(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') {
                sb.append('\\')
                    .append('"');
            } else if (c == '\\') {
                sb.append('\\')
                    .append('\\');
            } else if (c == '\n') {
                sb.append('\\')
                    .append('n');
            } else if (c == '\r') {
                sb.append('\\')
                    .append('r');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
