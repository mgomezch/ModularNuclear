package com.gtnewhorizons.modularnuclear.common.nuclear.standalone;

import java.util.List;

import org.teavm.interop.Export;

import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearColorMaps;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;

/**
 * WebAssembly and standalone bridge exposing the nuclear simulation engine
 * directly to browser JavaScript via WebAssembly or client-side execution.
 */
public class NuclearSimWasmBridge {

    private static StandaloneNuclearGrid grid;
    private static boolean isRunning = false;

    public static void main(String[] args) {
        initGrid(7, 7, NuclearSimulationEngine.PIPE_TIER_PLATINUM);
        loadPreset("BEST_PLATINUM_7X7");
    }

    @Export(name = "initGrid")
    public static void initGrid(int width, int height, int pipeTier) {
        NuclearSimulationEngine.setGlobalThermalFissionMultiplier(0.348);
        NuclearSimulationEngine.fissionHeatPerNeutron = 77.2;
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
        for (int i = 0; i < count && !grid.isExploded() && !(grid.isStopOnIncidents() && grid.isHaltedByIncident()); i++) {
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

    @Export(name = "setTile")
    public static void setTile(int x, int y, String typeStr) {
        if (grid != null && typeStr != null) {
            try {
                SimTile.TileType type = SimTile.TileType.fromCode(typeStr);
                grid.setTile(x, y, type);
            } catch (Exception ignored) {}
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
            } catch (Exception ignored) {}
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
            grid.getCoolantLoop().setMaterial(CoolantLoopModel.LoopMaterial.fromString(matStr));
        }
    }

    @Export(name = "setCoolantLoopPipeSize")
    public static void setCoolantLoopPipeSize(String sizeStr) {
        if (grid != null && sizeStr != null) {
            grid.getCoolantLoop().setPipeSize(CoolantLoopModel.LoopPipeSize.fromString(sizeStr));
        }
    }

    @Export(name = "setCoolantLoopFluid")
    public static void setCoolantLoopFluid(String fluidStr) {
        if (grid != null && fluidStr != null) {
            grid.getCoolantLoop().setFluidType(CoolantLoopModel.CoolantFluidType.fromString(fluidStr));
        }
    }

    @Export(name = "setCoolantLoopPumpPower")
    public static void setCoolantLoopPumpPower(double powerEUt) {
        if (grid != null) {
            grid.getCoolantLoop().setPumpElectricalPowerEUt(powerEUt);
            grid.getCoolantLoop().setUseTargetFlowMode(false);
        }
    }

    @Export(name = "setCoolantLoopHatchTier")
    public static void setCoolantLoopHatchTier(String tierStr) {
        if (grid != null && tierStr != null) {
            grid.getCoolantLoop().setHatchTier(CoolantLoopModel.EnergyHatchTier.fromString(tierStr));
            grid.getCoolantLoop().setUseTargetFlowMode(false);
        }
    }

    @Export(name = "setCoolantLoopDutyCycle")
    public static void setCoolantLoopDutyCycle(double dutyPercent) {
        if (grid != null) {
            grid.getCoolantLoop().setDutyCyclePercent(dutyPercent);
            grid.getCoolantLoop().setUseTargetFlowMode(false);
        }
    }

    @Export(name = "setCoolantLoopMaxFlow")
    public static void setCoolantLoopMaxFlow(double maxFlow) {
        if (grid != null) {
            grid.getCoolantLoop().setMaxFlowRateLPerSec(maxFlow);
        }
    }

    @Export(name = "setCoolantLoopMaxPressure")
    public static void setCoolantLoopMaxPressure(double maxPressure) {
        if (grid != null) {
            grid.getCoolantLoop().setMaxPressureBar(maxPressure);
        }
    }

    @Export(name = "setCoolantLoopControl")
    public static void setCoolantLoopControl(String hatchTier, double dutyCycle, double maxFlow, double maxPressure) {
        if (grid != null) {
            CoolantLoopModel cl = grid.getCoolantLoop();
            if (hatchTier != null && !hatchTier.isEmpty()) {
                cl.setHatchTier(CoolantLoopModel.EnergyHatchTier.fromString(hatchTier));
            }
            cl.setDutyCyclePercent(dutyCycle);
            cl.setMaxFlowRateLPerSec(maxFlow);
            cl.setMaxPressureBar(maxPressure);
            cl.setUseTargetFlowMode(false);
        }
    }

    @Export(name = "setCoolantLoopFlowRate")
    public static void setCoolantLoopFlowRate(double flowRate) {
        if (grid != null) {
            grid.getCoolantLoop().setTargetFlowRateLPerSec(flowRate);
            grid.getCoolantLoop().setUseTargetFlowMode(true);
        }
    }

    @Export(name = "attachCoolantLoopPoint")
    public static void attachCoolantLoopPoint(int x, int y) {
        if (grid != null) {
            grid.getCoolantLoop().attachPoint(x, y);
        }
    }

    @Export(name = "detachCoolantLoopPoint")
    public static void detachCoolantLoopPoint(int x, int y) {
        if (grid != null) {
            grid.getCoolantLoop().detachPoint(x, y);
        }
    }

    @Export(name = "clearCoolantLoopPoints")
    public static void clearCoolantLoopPoints() {
        if (grid != null) {
            grid.getCoolantLoop().clearAttachedPoints();
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
        if ("autoSupplyFuel".equalsIgnoreCase(key) || "autoReplaceFuel".equalsIgnoreCase(key) || "autoRefuel".equalsIgnoreCase(key)) {
            if (grid != null) {
                grid.setAutoSupplyFuel("true".equalsIgnoreCase(value) || "1".equals(value));
            }
            return;
        }
        if ("reset".equalsIgnoreCase(key)) {
            NuclearSimulationEngine.resetDefaultParameters();
            if (grid != null) {
                grid.updateHatchCapacities(NuclearSimulationEngine.hatchCoolantCapacity);
            }
            return;
        }
        if ("repair".equalsIgnoreCase(key)) {
            if (grid != null) {
                grid.repairMaintenance();
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
            NuclearSimulationEngine.turnoverCurve = NuclearSimulationEngine.TurnoverCurve.fromString(value);
            return;
        }
        double d = 0;
        try {
            d = Double.parseDouble(value);
        } catch (Exception ignored) {
            return;
        }
        if ("euPerDegree".equals(key)) {
            NuclearSimulationEngine.setEuPerDegree(d);
        } else if ("baseHatchConductance".equals(key) || "baseConductance".equals(key) || "conductance".equals(key)) {
            NuclearSimulationEngine.setBaseHatchConductance(d);
        } else if ("reactorDamage".equals(key) || "damage".equals(key)) {
            if (grid != null) {
                grid.setReactorDamage(d);
            }
        } else if ("maintenanceIssues".equals(key)) {
            if (grid != null) {
                grid.setMaintenanceIssues((int) d);
            }
        } else if ("hatchCapacity".equals(key)) {
            NuclearSimulationEngine.hatchCoolantCapacity = Math.max(100, (int) d);
            if (grid != null) {
                grid.updateHatchCapacities(NuclearSimulationEngine.hatchCoolantCapacity);
            }
        } else if ("turnoverDeltaTMax".equals(key)) {
            NuclearSimulationEngine.turnoverDeltaTMax = d;
        } else if ("turnoverExponent".equals(key)) {
            NuclearSimulationEngine.turnoverExponent = d;
        } else if ("coolantFeedRate".equals(key)) {
            NuclearSimulationEngine.coolantFeedRate = (int) d;
        } else if ("coolingHeat".equals(key)) {
            NuclearSimulationEngine.coolingHeatPerLiter = d;
        } else if ("ambientTemp".equals(key)) {
            NuclearSimulationEngine.ambientTemp = d;
        } else if ("ic2CoolantHeat".equals(key)) {
            NuclearSimulationEngine.ic2CoolantHeatPerLiter = d;
        } else if ("tempThresholdLow".equals(key)) {
            NuclearSimulationEngine.tempThresholdLow = d;
        } else if ("tempThresholdHigh".equals(key)) {
            NuclearSimulationEngine.tempThresholdHigh = d;
        } else if ("reactivityPower".equals(key)) {
            NuclearSimulationEngine.reactivityPower = d;
        } else if ("thermalFissionMultiplier".equals(key) || "fissionMult".equals(key)) {
            NuclearSimulationEngine.thermalFissionMultiplier = d;
        } else if ("globalThermalFissionMultiplier".equals(key)) {
            NuclearSimulationEngine.globalThermalFissionMultiplier = d;
        } else if ("fissionHeatPerNeutron".equals(key) || "fissionHeat".equals(key)) {
            NuclearSimulationEngine.fissionHeatPerNeutron = d;
        } else if ("hpWaterBoilingPoint".equals(key) || "hpBoil".equals(key)) {
            NuclearSimulationEngine.hpWaterBoilingPoint = d;
        }
    }

    @Export(name = "getStateJson")
    public static String getStateJson() {
        if (grid == null) {
            initGrid(7, 7, NuclearSimulationEngine.PIPE_TIER_PLATINUM);
            loadPreset("BEST_PLATINUM_7X7");
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
        sb.append("]}");
        return sb.toString();
    }

    public static String buildStateJson(StandaloneNuclearGrid targetGrid, boolean running) {
        if (targetGrid == null) return "{}";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
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
            .append(targetGrid.getCoolingMode().name())
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
            .append(running && !targetGrid.isExploded() && !(targetGrid.isStopOnIncidents() && targetGrid.isHaltedByIncident()))
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
                .append("\"tick\":").append(ev.tick()).append(",")
                .append("\"type\":\"").append(escapeJson(ev.type())).append("\",")
                .append("\"message\":\"").append(escapeJson(ev.message())).append("\",")
                .append("\"damage\":").append(fmt1(ev.damage()))
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
        sb.append("\"wallHeatPool\":")
            .append(fmt1(targetGrid.getLastWallHeatPool()))
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

        CoolantLoopModel cl = targetGrid.getCoolantLoop();
        sb.append("\"coolantLoop\":{");
        sb.append("\"material\":\"").append(cl.getMaterial().name()).append("\",");
        sb.append("\"materialName\":\"").append(cl.getMaterial().displayName).append("\",");
        sb.append("\"materialTier\":\"").append(cl.getMaterial().tierUnlocked).append("\",");
        sb.append("\"maxPressureBar\":").append(fmt1(cl.getMaterial().maxPressureBar)).append(",");
        sb.append("\"maxTempC\":").append(fmt1(cl.getMaterial().maxTemperatureCelsius)).append(",");
        sb.append("\"pipeSize\":\"").append(cl.getPipeSize().name()).append("\",");
        sb.append("\"pipeSizeName\":\"").append(cl.getPipeSize().displayName).append("\",");
        sb.append("\"fluid\":\"").append(cl.getFluidType().name()).append("\",");
        sb.append("\"fluidName\":\"").append(cl.getFluidType().displayName).append("\",");
        sb.append("\"byproductGas\":\"").append(cl.getFluidType().byproductGas).append("\",");
        sb.append("\"hatchTier\":\"").append(cl.getHatchTier().name()).append("\",");
        sb.append("\"hatchTierName\":\"").append(cl.getHatchTier().displayName).append("\",");
        sb.append("\"hatchVoltage\":").append(fmt1(cl.getHatchTier().voltageEU)).append(",");
        sb.append("\"dutyCyclePercent\":").append(fmt1(cl.getDutyCyclePercent())).append(",");
        sb.append("\"effectiveDutyCyclePercent\":").append(fmt1(cl.getEffectiveDutyCyclePercent())).append(",");
        sb.append("\"maxFlowRateLimit\":").append(fmt1(cl.getMaxFlowRateLPerSec())).append(",");
        sb.append("\"maxPressureLimit\":").append(fmt1(cl.getMaxPressureBar())).append(",");
        sb.append("\"pressureLimited\":").append(cl.isPressureLimited()).append(",");
        sb.append("\"flowLimited\":").append(cl.isFlowLimited()).append(",");
        sb.append("\"limitReason\":\"").append(escapeJson(cl.getLimitReason())).append("\",");
        sb.append("\"pumpPowerEUt\":").append(fmt1(cl.getPumpElectricalPowerEUt())).append(",");
        sb.append("\"targetFlowRateLPerSec\":").append(fmt1(cl.getTargetFlowRateLPerSec())).append(",");
        sb.append("\"useTargetFlowMode\":").append(cl.isUseTargetFlowMode()).append(",");
        sb.append("\"currentFlowRateLPerSec\":").append(fmt1(cl.getCurrentFlowRateLPerSec())).append(",");
        sb.append("\"currentPressureBar\":").append(fmt2(cl.getCurrentPressureBar())).append(",");
        sb.append("\"currentCoolantTempCelsius\":").append(fmt1(cl.getCurrentCoolantTempCelsius())).append(",");
        sb.append("\"lastHeatExtractedWatts\":").append(fmt1(cl.getLastHeatExtractedWatts())).append(",");
        sb.append("\"lastHeatExtractedEUt\":").append(fmt1(cl.getLastHeatExtractedEUt())).append(",");
        sb.append("\"lastSecondarySteamProducedLt\":").append(fmt1(cl.getLastSecondarySteamProducedLt())).append(",");
        sb.append("\"lastSecondaryWaterBoiledLt\":").append(fmt1(cl.getLastSecondaryWaterBoiledLt())).append(",");
        sb.append("\"lastPumpPowerEUt\":").append(fmt1(cl.getLastPumpPowerEUt())).append(",");
        sb.append("\"totalDeuteriumProduced\":").append(cl.getTotalDeuteriumProduced()).append(",");
        sb.append("\"totalTritiumProduced\":").append(cl.getTotalTritiumProduced()).append(",");
        sb.append("\"totalSecondarySteamProduced\":").append(cl.getTotalSecondarySteamProduced()).append(",");
        sb.append("\"ruptured\":").append(cl.isRuptured()).append(",");
        sb.append("\"ruptureReason\":\"").append(escapeJson(cl.getRuptureReason())).append("\",");
        sb.append("\"attachedPoints\":[");
        int ptIdx = 0;
        for (String pt : cl.getAttachedPoints()) {
            if (ptIdx++ > 0) sb.append(",");
            sb.append("\"").append(pt).append("\"");
        }
        sb.append("],");
        sb.append("\"energyHatchTiers\":[");
        int tierIdx = 0;
        for (CoolantLoopModel.EnergyHatchTier t : CoolantLoopModel.EnergyHatchTier.values()) {
            if (tierIdx++ > 0) sb.append(",");
            sb.append("{\"id\":\"").append(t.name()).append("\",\"name\":\"").append(t.displayName)
              .append("\",\"voltage\":").append(fmt1(t.voltageEU)).append("}");
        }
        sb.append("],");
        sb.append("\"allowedMaterials\":[");
        int matIdx = 0;
        for (CoolantLoopModel.LoopMaterial mat : CoolantLoopModel.LoopMaterial.values()) {
            if (mat.isAllowedInReactorTier(targetGrid.getPipeTier())) {
                if (matIdx++ > 0) sb.append(",");
                sb.append("{\"id\":\"").append(mat.name()).append("\",\"name\":\"").append(mat.displayName)
                    .append("\",\"maxPressure\":").append(mat.maxPressureBar)
                    .append(",\"tier\":\"").append(mat.tierUnlocked)
                    .append("\",\"progressionAppropriate\":").append(mat.isProgressionAppropriate(targetGrid.getPipeTier()))
                    .append("}");
            }
        }
        sb.append("]");
        sb.append("},");
        sb.append("\"grossPowerEUt\":").append(fmt1(targetGrid.getGrossPowerEUt())).append(",");
        sb.append("\"pumpPowerEUt\":").append(fmt1(targetGrid.getPumpPowerEUt())).append(",");

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
        sb.append("\"eheMode\":\"")
            .append(p.eheMode)
            .append("\",");
        sb.append("\"eheSteamProduced\":")
            .append(fmt1(p.eheSteamProduced))
            .append(",");
        sb.append("\"eheDistilledWaterConsumed\":")
            .append(fmt1(p.eheDistilledWaterConsumed))
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
            .append(fmt0(p.optFlowPerTurbine));
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
                null);
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
        sb.append("\"euPerDegree\":")
            .append(fmt1(NuclearSimulationEngine.euPerDegree))
            .append(",");
        sb.append("\"hatchCapacity\":")
            .append(NuclearSimulationEngine.hatchCoolantCapacity)
            .append(",");
        sb.append("\"turnoverCurve\":\"")
            .append(NuclearSimulationEngine.turnoverCurve.name())
            .append("\",");
        sb.append("\"turnoverDeltaTMax\":")
            .append(fmt1(NuclearSimulationEngine.turnoverDeltaTMax))
            .append(",");
        sb.append("\"turnoverExponent\":")
            .append(fmt2(NuclearSimulationEngine.turnoverExponent))
            .append(",");
        sb.append("\"coolantFeedRate\":")
            .append(NuclearSimulationEngine.coolantFeedRate)
            .append(",");
        sb.append("\"coolingHeatPerLiter\":")
            .append(fmt1(NuclearSimulationEngine.coolingHeatPerLiter))
            .append(",");
        sb.append("\"ambientTemp\":")
            .append(fmt1(NuclearSimulationEngine.ambientTemp))
            .append(",");
        sb.append("\"ic2CoolantHeatPerLiter\":")
            .append(fmt1(NuclearSimulationEngine.ic2CoolantHeatPerLiter))
            .append(",");
        sb.append("\"tempThresholdLow\":")
            .append(fmt1(NuclearSimulationEngine.tempThresholdLow))
            .append(",");
        sb.append("\"tempThresholdHigh\":")
            .append(fmt1(NuclearSimulationEngine.tempThresholdHigh))
            .append(",");
        sb.append("\"reactivityPower\":")
            .append(fmt2(NuclearSimulationEngine.reactivityPower))
            .append(",");
        sb.append("\"thermalFissionMultiplier\":")
            .append(fmt2(NuclearSimulationEngine.thermalFissionMultiplier))
            .append(",");
        sb.append("\"globalThermalFissionMultiplier\":")
            .append(fmt2(NuclearSimulationEngine.globalThermalFissionMultiplier))
            .append(",");
        sb.append("\"fissionHeatPerNeutron\":")
            .append(fmt1(NuclearSimulationEngine.fissionHeatPerNeutron))
            .append(",");
        sb.append("\"hpWaterBoilingPoint\":")
            .append(fmt1(NuclearSimulationEngine.hpWaterBoilingPoint))
            .append(",");
        sb.append("\"baseHatchConductance\":")
            .append(fmt1(NuclearSimulationEngine.baseHatchConductance));
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
                    .append(t.getType().code)
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
                    .append(t.getControlRodType().name())
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
                    .append(t.getLastControlRodThermalAbsorbed());
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
        sb.append("\"autoReplaceFuel\":").append(targetGrid.isAutoReplaceFuel()).append(",");
        sb.append("\"totalSolidItemsPerMin\":").append(fmt2(totalSolidItemsPerMin)).append(",");
        sb.append("\"totalSolidItemsPerHour\":").append(fmt1(totalSolidItemsPerHour)).append(",");
        sb.append("\"totalLiquidLitersPerMin\":").append(fmt1(totalLiquidLitersPerMin)).append(",");
        sb.append("\"totalLiquidLitersPerHour\":").append(fmt0(totalLiquidLitersPerHour)).append(",");
        sb.append("\"totalDepletedItemsProduced\":").append(totalDepletedItemsProduced).append(",");
        sb.append("\"totalDepletedLiquidProduced\":").append(totalDepletedLiquidProduced).append(",");

        sb.append("\"solidRods\":[");
        for (int i = 0; i < solidByproducts.size(); i++) {
            if (i > 0) sb.append(",");
            StandaloneNuclearGrid.SolidFuelByproduct s = solidByproducts.get(i);
            sb.append("{");
            sb.append("\"type\":\"").append(s.type.name()).append("\",");
            sb.append("\"fuelName\":\"").append(escapeJson(s.fuelName)).append("\",");
            sb.append("\"fuelCode\":\"").append(s.fuelCode).append("\",");
            sb.append("\"depletedName\":\"").append(escapeJson(s.depletedName)).append("\",");
            sb.append("\"depletedCode\":\"").append(s.depletedCode).append("\",");
            sb.append("\"activeRods\":").append(s.activeRods).append(",");
            sb.append("\"itemsPerMin\":").append(fmt2(s.itemsPerMinute)).append(",");
            sb.append("\"itemsPerHour\":").append(fmt1(s.itemsPerHour)).append(",");
            sb.append("\"totalProduced\":").append(s.totalProduced).append(",");
            sb.append("\"avgLifespanMin\":").append(Double.isInfinite(s.avgLifespanMinutes) ? "\"Infinity\"" : fmt1(s.avgLifespanMinutes));
            sb.append("}");
        }
        sb.append("],");

        sb.append("\"liquidFuels\":[");
        for (int i = 0; i < liquidByproducts.size(); i++) {
            if (i > 0) sb.append(",");
            StandaloneNuclearGrid.LiquidFuelByproduct l = liquidByproducts.get(i);
            sb.append("{");
            sb.append("\"type\":\"").append(l.type.name()).append("\",");
            sb.append("\"fluidName\":\"").append(l.fluidName).append("\",");
            sb.append("\"displayName\":\"").append(escapeJson(l.displayName)).append("\",");
            sb.append("\"activeHatches\":").append(l.activeHatches).append(",");
            sb.append("\"litersPerMin\":").append(fmt1(l.litersPerMinute)).append(",");
            sb.append("\"litersPerHour\":").append(fmt0(l.litersPerHour)).append(",");
            sb.append("\"totalLiters\":").append(l.totalLiters);
            sb.append("}");
        }
        sb.append("],");

        sb.append("\"isotopes\":[");
        for (int i = 0; i < isotopeByproducts.size(); i++) {
            if (i > 0) sb.append(",");
            StandaloneNuclearGrid.IsotopeByproduct iso = isotopeByproducts.get(i);
            sb.append("{");
            sb.append("\"name\":\"").append(iso.name).append("\",");
            sb.append("\"code\":\"").append(iso.code).append("\",");
            sb.append("\"litersPerMin\":").append(fmt2(iso.litersPerMinute)).append(",");
            sb.append("\"litersPerHour\":").append(fmt1(iso.litersPerHour)).append(",");
            sb.append("\"totalLiters\":").append(iso.totalLiters);
            sb.append("}");
        }
        sb.append("],");

        List<StandaloneNuclearGrid.RawMaterialBalance> rawMaterials = targetGrid.getRawMaterialBalances();
        sb.append("\"rawMaterials\":[");
        for (int i = 0; i < rawMaterials.size(); i++) {
            if (i > 0) sb.append(",");
            StandaloneNuclearGrid.RawMaterialBalance r = rawMaterials.get(i);
            sb.append("{");
            sb.append("\"name\":\"").append(escapeJson(r.name)).append("\",");
            sb.append("\"code\":\"").append(r.code).append("\",");
            sb.append("\"unit\":\"").append(r.unit).append("\",");
            sb.append("\"consumedPerMin\":").append(fmt2(r.consumedPerMinute)).append(",");
            sb.append("\"consumedPerHour\":").append(fmt1(r.consumedPerHour)).append(",");
            sb.append("\"producedPerMin\":").append(fmt2(r.producedPerMinute)).append(",");
            sb.append("\"producedPerHour\":").append(fmt1(r.producedPerHour)).append(",");
            sb.append("\"netPerMin\":").append(fmt2(r.netPerMinute)).append(",");
            sb.append("\"netPerHour\":").append(fmt1(r.netPerHour));
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
                sb.append('\\').append('"');
            } else if (c == '\\') {
                sb.append('\\').append('\\');
            } else if (c == '\n') {
                sb.append('\\').append('n');
            } else if (c == '\r') {
                sb.append('\\').append('r');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
