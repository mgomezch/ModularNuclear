package com.gtnewhorizons.modularnuclear.common.nuclear.standalone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gtnewhorizons.modularnuclear.common.nuclear.INuclearTile;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;

/**
 * Manages an N x M grid of nuclear tiles, casing constraints, safety checks,
 * and historical telemetry for standalone simulation.
 */
public class StandaloneNuclearGrid {

    private int width;
    private int height;
    private SimTile[][] grid;
    private int pipeTier = NuclearSimulationEngine.PIPE_TIER_ELECTRUM;
    private CoolantLoopModel.CoolingMode coolingMode = CoolantLoopModel.CoolingMode.MODULAR;
    private CoolantLoopModel coolantLoop = new CoolantLoopModel();
    private double grossPowerEUt = 0.0;

    // Maintenance & Structural Integrity
    private double reactorDamage = 0.0;
    private int maintenanceIssues = 0;

    // Simulation metrics
    private long currentTick = 0;
    private boolean exploded = false;
    private String explosionReason = "";
    private boolean powerFailed = false;
    private String powerFailReason = "";
    private double coreMaxTemp = NuclearSimulationEngine.AMBIENT_TEMP;
    private double coreAvgTemp = NuclearSimulationEngine.AMBIENT_TEMP;
    private double efficiency = 1.0;
    private int lastNeutronsProduced = 0;
    private int lastFastAbsorbed = 0;
    private int lastThermalAbsorbed = 0;
    private int lastEscapedNeutrons = 0;
    private int lastWallReflected = 0;
    private int lastWallAbsorbed = 0;
    private double lastWallHeatPool = 0;
    private int burnedFuelCount = 0;
    private int voidedHatchCount = 0;
    private double peakLifetimeTemp = NuclearSimulationEngine.AMBIENT_TEMP;

    // Historical accumulators
    private long totalNeutronsGenerated = 0;
    private long totalSteamProduced = 0;
    private double totalEnergyEU = 0;
    private int totalDeuteriumProduced = 0;
    private int totalTritiumProduced = 0;

    // Turbine configuration
    private TurbineCalculator.TurbineMaterial turbineMaterial = TurbineCalculator.TurbineMaterial.HSS_E;
    private TurbineCalculator.TurbineSize turbineSize = TurbineCalculator.TurbineSize.LARGE;
    private TurbineCalculator.FittingMode turbineFitting = TurbineCalculator.FittingMode.TIGHT;

    // Instantaneous flows (L/t)
    private double flowRegularSteam = 0;
    private double flowSuperheatedSteam = 0;
    private double flowSupercriticalSteam = 0;
    private double flowHeavyWaterSteam = 0;
    private double flowHPHeavyWaterSteam = 0;
    private double flowHotCoolant = 0;
    private double flowDirectEU = 0;

    private TurbineCalculator.PowerEstimationResult lastPowerResult = new TurbineCalculator.PowerEstimationResult();
    private TurbineCalculator.ScenarioHypotheticalResult lastScenariosResult = null;

    public static class TickTelemetry {

        public final long tick;
        public final double maxTemp;
        public final double avgTemp;
        public final double efficiency;
        public final int neutrons;
        public final double powerEUt;
        public final boolean safe;
        public final double reactorDamage;
        public final int maintenanceIssues;
        public final double maintenanceEfficiency;

        public TickTelemetry(long tick, double maxTemp, double avgTemp, double efficiency, int neutrons,
            double powerEUt, boolean safe) {
            this(tick, maxTemp, avgTemp, efficiency, neutrons, powerEUt, safe, 0.0, 0, 1.0);
        }

        public TickTelemetry(long tick, double maxTemp, double avgTemp, double efficiency, int neutrons,
            double powerEUt, boolean safe, double reactorDamage, int maintenanceIssues, double maintenanceEfficiency) {
            this.tick = tick;
            this.maxTemp = maxTemp;
            this.avgTemp = avgTemp;
            this.efficiency = efficiency;
            this.neutrons = neutrons;
            this.powerEUt = powerEUt;
            this.safe = safe;
            this.reactorDamage = reactorDamage;
            this.maintenanceIssues = maintenanceIssues;
            this.maintenanceEfficiency = maintenanceEfficiency;
        }

        public long tick() {
            return tick;
        }

        public double maxTemp() {
            return maxTemp;
        }

        public double avgTemp() {
            return avgTemp;
        }

        public double efficiency() {
            return efficiency;
        }

        public int neutrons() {
            return neutrons;
        }

        public double powerEUt() {
            return powerEUt;
        }

        public boolean safe() {
            return safe;
        }

        public double reactorDamage() {
            return reactorDamage;
        }

        public int maintenanceIssues() {
            return maintenanceIssues;
        }

        public double maintenanceEfficiency() {
            return maintenanceEfficiency;
        }
    }

    public static class IncidentEvent {

        public final long tick;
        public final String type;
        public final String message;
        public final double damage;

        public IncidentEvent(long tick, String type, String message, double damage) {
            this.tick = tick;
            this.type = type;
            this.message = message;
            this.damage = damage;
        }

        public long tick() {
            return tick;
        }

        public String type() {
            return type;
        }

        public String message() {
            return message;
        }

        public double damage() {
            return damage;
        }
    }

    public static class SolidFuelByproduct {

        public final SimTile.TileType type;
        public final String fuelName;
        public final String fuelCode;
        public final String depletedName;
        public final String depletedCode;
        public final int activeRods;
        public final double itemsPerMinute;
        public final double itemsPerHour;
        public final long totalProduced;
        public final double avgLifespanMinutes;

        public SolidFuelByproduct(SimTile.TileType type, String fuelName, String fuelCode, String depletedName,
            String depletedCode, int activeRods, double itemsPerMinute, double itemsPerHour, long totalProduced,
            double avgLifespanMinutes) {
            this.type = type;
            this.fuelName = fuelName;
            this.fuelCode = fuelCode;
            this.depletedName = depletedName;
            this.depletedCode = depletedCode;
            this.activeRods = activeRods;
            this.itemsPerMinute = itemsPerMinute;
            this.itemsPerHour = itemsPerHour;
            this.totalProduced = totalProduced;
            this.avgLifespanMinutes = avgLifespanMinutes;
        }
    }

    public static class LiquidFuelByproduct {

        public final SimTile.TileType type;
        public final String fluidName;
        public final String displayName;
        public final int activeHatches;
        public final double litersPerMinute;
        public final double litersPerHour;
        public final long totalLiters;

        public LiquidFuelByproduct(SimTile.TileType type, String fluidName, String displayName, int activeHatches,
            double litersPerMinute, double litersPerHour, long totalLiters) {
            this.type = type;
            this.fluidName = fluidName;
            this.displayName = displayName;
            this.activeHatches = activeHatches;
            this.litersPerMinute = litersPerMinute;
            this.litersPerHour = litersPerHour;
            this.totalLiters = totalLiters;
        }
    }

    public static class IsotopeByproduct {

        public final String name;
        public final String code;
        public final double litersPerMinute;
        public final double litersPerHour;
        public final long totalLiters;

        public IsotopeByproduct(String name, String code, double litersPerMinute, double litersPerHour,
            long totalLiters) {
            this.name = name;
            this.code = code;
            this.litersPerMinute = litersPerMinute;
            this.litersPerHour = litersPerHour;
            this.totalLiters = totalLiters;
        }
    }

    public static class RawMaterialBalance {

        public final String name;
        public final String code;
        public final String unit;
        public final double consumedPerMinute;
        public final double consumedPerHour;
        public final double producedPerMinute;
        public final double producedPerHour;
        public final double netPerMinute;
        public final double netPerHour;

        public RawMaterialBalance(String name, String code, String unit, double consumedPerMinute,
            double consumedPerHour, double producedPerMinute, double producedPerHour) {
            this.name = name;
            this.code = code;
            this.unit = unit;
            this.consumedPerMinute = consumedPerMinute;
            this.consumedPerHour = consumedPerHour;
            this.producedPerMinute = producedPerMinute;
            this.producedPerHour = producedPerHour;
            this.netPerMinute = producedPerMinute - consumedPerMinute;
            this.netPerHour = producedPerHour - consumedPerHour;
        }
    }

    private final List<TickTelemetry> history = new ArrayList<>();
    private boolean strictMode = false;
    private final List<IncidentEvent> incidentLog = new ArrayList<>();
    private long lastIncidentTick = 0;
    private boolean autoReplaceFuel = true;
    private boolean stopOnIncidents = false;
    private boolean haltedByIncident = false;
    private String lastHaltIncidentReason = "";
    private final Map<SimTile.TileType, Long> cumulativeDepletedFuelItems = new EnumMap<>(SimTile.TileType.class);
    private final Map<SimTile.TileType, Long> cumulativeDepletedLiquidLiters = new EnumMap<>(SimTile.TileType.class);

    public boolean isStopOnIncidents() {
        return stopOnIncidents;
    }

    public void setStopOnIncidents(boolean stopOnIncidents) {
        this.stopOnIncidents = stopOnIncidents;
        if (!stopOnIncidents) {
            this.haltedByIncident = false;
            this.lastHaltIncidentReason = "";
        }
    }

    public boolean isHaltedByIncident() {
        return haltedByIncident;
    }

    public void clearHaltedByIncident() {
        this.haltedByIncident = false;
        this.lastHaltIncidentReason = "";
    }

    public void setHaltedByIncident(boolean halted, String reason) {
        this.haltedByIncident = halted;
        this.lastHaltIncidentReason = (reason != null) ? reason : "";
    }

    public String getLastHaltIncidentReason() {
        return lastHaltIncidentReason;
    }

    public boolean isAutoSupplyFuel() {
        return autoReplaceFuel;
    }

    public void setAutoSupplyFuel(boolean autoSupplyFuel) {
        this.autoReplaceFuel = autoSupplyFuel;
    }

    public boolean isStrictMode() {
        return strictMode;
    }

    public void setStrictMode(boolean strictMode) {
        this.strictMode = strictMode;
    }

    public List<IncidentEvent> getIncidentLog() {
        return Collections.unmodifiableList(incidentLog);
    }

    public long getLastIncidentTick() {
        return lastIncidentTick;
    }

    public void clearIncidentLog() {
        this.incidentLog.clear();
    }

    public void logIncident(String type, String message, double damage) {
        this.lastIncidentTick = this.currentTick;
        if (this.incidentLog.size() >= 100) {
            this.incidentLog.remove(0);
        }
        this.incidentLog.add(new IncidentEvent(this.currentTick, type, message, damage));
        if (this.stopOnIncidents && damage > 0.0 && !this.haltedByIncident) {
            this.haltedByIncident = true;
            this.lastHaltIncidentReason = String
                .format(java.util.Locale.US, "[%s] %s (+%.1f%% damage)", type, message, damage);
            this.incidentLog.add(
                new IncidentEvent(
                    this.currentTick,
                    "PENDING_INCIDENT",
                    String.format(
                        java.util.Locale.US,
                        "Pending Incident: Simulation stopped on [%s] (%s). Action required to prevent further damage.",
                        type,
                        message),
                    0.0));
        }
    }

    public static class IntermediateStepSnapshot {

        public final long tick;
        public final String phase;
        public final double minTemp;
        public final int minX;
        public final int minY;
        public final String minTileCode;
        public final double maxTemp;
        public final double avgTemp;
        public final String details;

        public IntermediateStepSnapshot(long tick, String phase, double minTemp, int minX, int minY, String minTileCode,
            double maxTemp, double avgTemp, String details) {
            this.tick = tick;
            this.phase = phase;
            this.minTemp = minTemp;
            this.minX = minX;
            this.minY = minY;
            this.minTileCode = minTileCode;
            this.maxTemp = maxTemp;
            this.avgTemp = avgTemp;
            this.details = details;
        }

        @Override
        public String toString() {
            return String.format(
                java.util.Locale.US,
                "[Tick %4d | %-20s] Min: %8.2f°C at (%d,%d) [%-2s] | Max: %8.2f°C | Avg: %8.2f°C | %s",
                tick,
                phase,
                minTemp,
                minX,
                minY,
                minTileCode,
                maxTemp,
                avgTemp,
                details);
        }
    }

    private boolean diagnosticTraceEnabled = false;
    private int maxTraceSteps = 50;
    private final List<IntermediateStepSnapshot> stepTraceBuffer = new ArrayList<>();
    private boolean negativeTempDetected = false;
    private String negativeTempReason = "";

    public void enableDiagnosticTrace(int maxSteps) {
        this.diagnosticTraceEnabled = true;
        this.maxTraceSteps = Math.max(10, maxSteps);
        this.stepTraceBuffer.clear();
    }

    public boolean isDiagnosticTraceEnabled() {
        return diagnosticTraceEnabled;
    }

    public boolean isNegativeTempDetected() {
        return negativeTempDetected;
    }

    public String getNegativeTempReason() {
        return negativeTempReason;
    }

    public List<IntermediateStepSnapshot> getStepTrace() {
        return Collections.unmodifiableList(stepTraceBuffer);
    }

    public void recordTraceSnapshot(String phase, String details) {
        if (!diagnosticTraceEnabled) return;
        double minT = Double.POSITIVE_INFINITY;
        double maxT = Double.NEGATIVE_INFINITY;
        double sumT = 0;
        int minX = -1, minY = -1;
        String minCode = "";

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SimTile t = grid[x][y];
                if (t != null) {
                    double temp = t.getTemperature();
                    sumT += temp;
                    if (temp < minT) {
                        minT = temp;
                        minX = x;
                        minY = y;
                        minCode = t.getType().code;
                    }
                    if (temp > maxT) {
                        maxT = temp;
                    }
                }
            }
        }
        double avgT = (width * height > 0) ? (sumT / (width * height)) : 0;
        if (stepTraceBuffer.size() >= maxTraceSteps) {
            stepTraceBuffer.remove(0);
        }
        stepTraceBuffer
            .add(new IntermediateStepSnapshot(currentTick, phase, minT, minX, minY, minCode, maxT, avgT, details));
    }

    public StandaloneNuclearGrid(int width, int height, int pipeTier) {
        this.width = width;
        this.height = height;
        this.pipeTier = pipeTier;
        applyDefaultTurbinesForTier();
        this.lastScenariosResult = TurbineCalculator.calculateBothScenarios(pipeTier, 0, 0, 0, 0, 0, 0, 0, null, null);
        this.grid = new SimTile[width][height];
        clearGrid();
    }

    public void clearGrid() {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (NuclearSimulationEngine.isCornerNullCell(x, y, width, height)) {
                    grid[x][y] = new SimTile(SimTile.TileType.NULL_WALL);
                } else {
                    grid[x][y] = new SimTile(SimTile.TileType.EMPTY);
                }
            }
        }
        resetMetrics();
    }

    public void resetMetrics() {
        this.currentTick = 0;
        this.exploded = false;
        this.explosionReason = "";
        this.coreMaxTemp = NuclearSimulationEngine.AMBIENT_TEMP;
        this.coreAvgTemp = NuclearSimulationEngine.AMBIENT_TEMP;
        this.efficiency = 1.0;
        this.lastNeutronsProduced = 0;
        this.lastFastAbsorbed = 0;
        this.lastThermalAbsorbed = 0;
        this.lastEscapedNeutrons = 0;
        this.totalNeutronsGenerated = 0;
        this.totalSteamProduced = 0;
        this.totalEnergyEU = 0;
        this.totalDeuteriumProduced = 0;
        this.totalTritiumProduced = 0;
        this.negativeTempDetected = false;
        this.negativeTempReason = "";
        this.powerFailed = false;
        this.powerFailReason = "";
        this.burnedFuelCount = 0;
        this.voidedHatchCount = 0;
        this.peakLifetimeTemp = NuclearSimulationEngine.AMBIENT_TEMP;
        this.reactorDamage = 0.0;
        this.maintenanceIssues = 0;
        this.coolantLoop.reset();
        this.grossPowerEUt = 0.0;
        this.stepTraceBuffer.clear();
        this.history.clear();
        this.incidentLog.clear();
        this.lastIncidentTick = 0;
        this.haltedByIncident = false;
        this.lastHaltIncidentReason = "";
        this.cumulativeDepletedFuelItems.clear();
        this.cumulativeDepletedLiquidLiters.clear();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SimTile tile = grid[x][y];
                if (tile != null) {
                    tile.setTemperature(NuclearSimulationEngine.AMBIENT_TEMP);
                    if (tile.isFuel()) {
                        tile.setDurability(tile.getMaxDurability());
                        tile.setDepleted(false);
                        tile.setDepletionLogged(false);
                    }
                    if (tile.isHatch()) {
                        tile.setInputFluidAmount(tile.getInputFluidCapacity());
                        tile.setOutputFluidAmount(0);
                        tile.setWasDry(false);
                        tile.setDepleted(false);
                    }
                }
            }
        }
    }

    public void setTile(int x, int y, SimTile.TileType type) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            if (NuclearSimulationEngine.isCornerNullCell(x, y, width, height)) {
                return;
            }
            grid[x][y].setType(type);
            if (grid[x][y].isHatch()) {
                int hatchTier = Math.max(1, pipeTier + 4);
                grid[x][y].setTier(hatchTier);
                grid[x][y].setInputFluidCapacity(8000 * (1 << hatchTier));
            }
            if (type == SimTile.TileType.PASSAGE_CORE && isTier2ConvectiveAllowed() && coolingMode == CoolantLoopModel.CoolingMode.MODULAR) {
                coolingMode = CoolantLoopModel.CoolingMode.CONVECTIVE_LOOP;
            }
        }
    }

    public SimTile getTile(int x, int y) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            return grid[x][y];
        }
        return null;
    }

    public void updateHatchCapacities(int newCap) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SimTile tile = grid[x][y];
                if (tile != null && tile.isHatch()) {
                    tile.setInputFluidCapacity(newCap);
                }
            }
        }
    }

    /**
     * Executes one simulation tick over the grid.
     */
    public boolean step() {
        if (exploded || powerFailed) return false;
        if (stopOnIncidents && haltedByIncident) return false;

        double initialDamage = this.reactorDamage;
        currentTick++;
        recordTraceSnapshot("PRE_TICK", "State before coolant feed");

        // 1. Coolant & Liquid Fuel Feed Phase: Replenish hatches that have space, checking dry thermal shock
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SimTile tile = grid[x][y];
                if (tile == null) continue;
                if (tile.isLiquidFuelHatch()) {
                    if (autoReplaceFuel) {
                        int space = tile.getInputFluidCapacity() - tile.getInputFluidAmount();
                        if (space > 0) {
                            tile.setInputFluidAmount(tile.getInputFluidCapacity());
                            tile.setDepleted(false);
                            tile.setWasDry(false);
                        }
                    }
                } else if (tile.isCoolantHatch() && tile.isAutoRefill()) {
                    int space = tile.getInputFluidCapacity() - tile.getInputFluidAmount();
                    if (space > 0) {
                        int feed = Math.min(space, NuclearSimulationEngine.coolantFeedRate);
                        if (feed > 0) {
                            if (tile.isWasDry()) {
                                double threshold = NuclearSimulationEngine
                                    .getCoolantBoilingThreshold(tile.getInputFluidName());
                                if (tile.getTemperature() > threshold) {
                                    if (this.stopOnIncidents && !this.haltedByIncident) {
                                        this.haltedByIncident = true;
                                        this.lastHaltIncidentReason = String.format(
                                            java.util.Locale.US,
                                            "Pending Incident: Cold coolant is about to be fed into dry superheated hatch at (%d,%d) (%.1f°C > %.1f°C boiling threshold). Simulation stopped to prevent thermal shock.",
                                            x,
                                            y,
                                            tile.getTemperature(),
                                            threshold);
                                        logIncident("PENDING_INCIDENT", this.lastHaltIncidentReason, 0.0);
                                        return false;
                                    }
                                    triggerThermalShock(
                                        x,
                                        y,
                                        String.format(
                                            java.util.Locale.US,
                                            "Thermal Shock: Cold coolant fed into dry superheated hatch at (%d,%d) with temperature %.1f°C exceeding boiling threshold %.1f°C",
                                            x,
                                            y,
                                            tile.getTemperature(),
                                            threshold));
                                    tile.setInputFluidAmount(0);
                                    tile.setWasDry(true);
                                    if (exploded || powerFailed) return false;
                                    continue;
                                }
                                tile.setWasDry(false);
                            }
                            tile.setInputFluidAmount(tile.getInputFluidAmount() + feed);
                        }
                    }
                }
            }
        }
        recordTraceSnapshot("POST_COOLANT_FEED", "Coolant fed into hatches");

        // 2. Track empty coolant hatches as dry
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SimTile tile = grid[x][y];
                if (tile != null && tile.isCoolantHatch()) {
                    if (tile.getInputFluidAmount() <= 0) {
                        tile.setWasDry(true);
                    }
                }
            }
        }

        // 3. Call the mod's pure Java NuclearSimulationEngine with live maintenance efficiency
        INuclearTile[][] simGrid = new INuclearTile[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (grid[x][y] != null && grid[x][y].getType() == SimTile.TileType.NULL_WALL) {
                    simGrid[x][y] = null;
                } else {
                    simGrid[x][y] = grid[x][y];
                }
            }
        }
        NuclearSimulationEngine.SimulationResult res = NuclearSimulationEngine
            .simulate(simGrid, width, height, getMaintenanceEfficiency(), NuclearSimulationEngine.ambientTemp);

        coreMaxTemp = res.maxTemperature;
        coreAvgTemp = res.averageTemperature;
        peakLifetimeTemp = Math.max(peakLifetimeTemp, coreMaxTemp);
        lastNeutronsProduced = res.totalNeutronsGenerated;
        lastFastAbsorbed = res.fastNeutronsAbsorbed;
        lastThermalAbsorbed = res.thermalNeutronsAbsorbed;
        lastEscapedNeutrons = res.neutronsEscaped;
        lastWallReflected = res.wallNeutronsReflected;
        lastWallAbsorbed = res.wallNeutronsAbsorbed;
        lastWallHeatPool = res.wallHeatPool;
        efficiency = res.averageReactivity;

        totalNeutronsGenerated += lastNeutronsProduced;
        recordTraceSnapshot("POST_SIMULATE", "Nuclear fission, diffusion and boiling completed");

        // 3.1. Fuel burnup & byproduct tracking
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SimTile tile = grid[x][y];
                if (tile == null) continue;

                if (tile.isLiquidFuelHatch()) {
                    int burned = tile.getLastLiquidFuelBurned();
                    if (burned > 0) {
                        cumulativeDepletedLiquidLiters.put(
                            tile.getType(),
                            cumulativeDepletedLiquidLiters.getOrDefault(tile.getType(), 0L) + burned);
                    }
                    if (autoReplaceFuel) {
                        tile.setInputFluidAmount(tile.getInputFluidCapacity());
                        tile.setDepleted(false);
                        tile.setWasDry(false);
                    }
                } else if (tile.isFuel() || tile.isDepleted()) {
                    if (tile.getDurability() <= 0 || tile.isDepleted()) {
                        if (autoReplaceFuel) {
                            cumulativeDepletedFuelItems
                                .put(tile.getType(), cumulativeDepletedFuelItems.getOrDefault(tile.getType(), 0L) + 1L);
                            tile.setDurability(tile.getMaxDurability());
                            tile.setDepleted(false);
                            logIncident(
                                "FUEL_CYCLED",
                                String.format(
                                    java.util.Locale.US,
                                    "Spent %s at (%d,%d) replaced with fresh rod (produced 1x %s [%s])",
                                    tile.getType().displayName,
                                    x,
                                    y,
                                    tile.getDepletedDisplayName(),
                                    tile.getDepletedCode()),
                                0.0);
                        } else if (!tile.isDepletionLogged()) {
                            cumulativeDepletedFuelItems
                                .put(tile.getType(), cumulativeDepletedFuelItems.getOrDefault(tile.getType(), 0L) + 1L);
                            tile.setDepletionLogged(true);
                            logIncident(
                                "FUEL_DEPLETED",
                                String.format(
                                    java.util.Locale.US,
                                    "Fuel rod %s at (%d,%d) reached 0 durability and depleted (produced 1x %s [%s])",
                                    tile.getType().displayName,
                                    x,
                                    y,
                                    tile.getDepletedDisplayName(),
                                    tile.getDepletedCode()),
                                0.0);
                        }
                    }
                }
            }
        }

        // 3.5. Execute Coolant Loop Convective Heat Transfer & Secondary Boiling
        if (shouldStepCoolantLoop()) {
            boolean loopOk = coolantLoop.step(this, grid, width, height);
            if (!loopOk || coolantLoop.isRuptured()) {
                triggerExplosion("Coolant Loop Failure: " + coolantLoop.getRuptureReason());
                return false;
            }
            recordTraceSnapshot(
                "POST_COOLANT_LOOP",
                "Convective coolant loop heat extraction and PHE secondary boiling completed");
        }

        // 4. Strict Check for Negative Temperature Anomaly
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SimTile tile = grid[x][y];
                if (tile != null && tile.getTemperature() < 0.0) {
                    this.negativeTempDetected = true;
                    this.negativeTempReason = String.format(
                        java.util.Locale.US,
                        "Negative temperature anomaly: tile (%d, %d) [%s] reached %.2f°C at tick %d (min allowed: %.1f°C) | Last Cooling: %s",
                        x,
                        y,
                        tile.getType().code,
                        tile.getTemperature(),
                        currentTick,
                        NuclearSimulationEngine.AMBIENT_TEMP,
                        tile.getLastCoolingDetails());
                    recordTraceSnapshot("NEGATIVE_TEMP_DETECTED", negativeTempReason);
                    triggerExplosion("Simulation Logic Failure: " + negativeTempReason);
                    return false;
                }
            }
        }

        // 5. Check casing operating temperature limit:
        // Overheating hatches void items and fluids inside and inflict 1% reactor damage per tile per tick
        double maxTempAllowed = NuclearSimulationEngine.getMaxOperatingTemperature(pipeTier);
        int overheatingCount = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SimTile tile = grid[x][y];
                if (tile != null && tile.getTemperature() > maxTempAllowed) {
                    overheatingCount++;
                    if (tile.isHatch()) {
                        voidedHatchCount++;
                        tile.setInputFluidAmount(0);
                        tile.setOutputFluidAmount(0);
                        tile.setWasDry(true);
                    } else if (tile.isFuel()) {
                        burnedFuelCount++;
                        tile.setType(SimTile.TileType.EMPTY);
                        logIncident(
                            "FUEL_BURNED",
                            String.format(
                                java.util.Locale.US,
                                "Fuel rod burned up at (%d,%d) due to casing overheat (%.1f °C > %.1f °C)",
                                x,
                                y,
                                tile.getTemperature(),
                                maxTempAllowed),
                            1.0);
                    }
                }
            }
        }
        if (overheatingCount > 0) {
            double addedDamage = overheatingCount * 1.0;
            reactorDamage = Math.min(100.0, reactorDamage + addedDamage);
            String overheatMsg = String.format(
                java.util.Locale.US,
                "%d tiles overheated casing max (%.1f°C). Reactor Damage +%.1f%% (now %.1f%%)",
                overheatingCount,
                maxTempAllowed,
                addedDamage,
                reactorDamage);
            logIncident("CASING_OVERHEAT", overheatMsg, addedDamage);
            recordTraceSnapshot("CASING_OVERHEAT", overheatMsg);
            if (strictMode) {
                triggerPowerFail(
                    String.format(
                        java.util.Locale.US,
                        "Calibration Disqualification: %d tiles overheated casing max (%.1f°C)",
                        overheatingCount,
                        maxTempAllowed));
                return false;
            }
            if (reactorDamage >= 100.0) {
                if (this.stopOnIncidents && !this.haltedByIncident) {
                    this.reactorDamage = 99.9;
                    this.haltedByIncident = true;
                    this.lastHaltIncidentReason = String.format(
                        java.util.Locale.US,
                        "Pending Incident: Reactor structural damage at 100%% from %d overheating tile(s). Simulation stopped to prevent meltdown.",
                        overheatingCount);
                    logIncident("PENDING_INCIDENT", this.lastHaltIncidentReason, 0.0);
                    return false;
                }
                triggerExplosion(
                    String.format(
                        java.util.Locale.US,
                        "Meltdown: Reactor structural damage reached 100%% from casing overheating! (%d overheated tiles at >%.1f°C)",
                        overheatingCount,
                        maxTempAllowed));
                return false;
            }
        }

        // 4. Calculate energy and steam generation in this tick
        long cumSteam = 0;
        int dCount = 0;
        int tCount = 0;

        flowRegularSteam = 0;
        flowSuperheatedSteam = 0;
        flowSupercriticalSteam = 0;
        flowHeavyWaterSteam = 0;
        flowHPHeavyWaterSteam = 0;
        flowHotCoolant = 0;
        flowDirectEU = 0;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SimTile tile = grid[x][y];
                cumSteam += tile.getTotalSteamProduced();
                dCount += tile.getTotalDeuteriumProduced();
                tCount += tile.getTotalTritiumProduced();
                flowDirectEU += tile.getDirectEUProduced();

                int tickProduced = tile.getLastTickProduced();
                if (tickProduced > 0) {
                    switch (tile.getType()) {
                        case HATCH_DISTILLED_WATER -> flowRegularSteam += tickProduced;
                        case HATCH_HEAVY_WATER -> flowHeavyWaterSteam += tickProduced;
                        case HATCH_IC2_COOLANT -> flowHotCoolant += tickProduced;
                        default -> {}
                    }
                }
            }
        }

        if (shouldStepCoolantLoop()) {
            double loopSteam = coolantLoop.getLastSecondarySteamProducedLt();
            if (loopSteam > 0) {
                double temp = coolantLoop.getCurrentCoolantTempCelsius();
                if (temp >= 700.0) {
                    flowSupercriticalSteam += loopSteam;
                } else if (temp >= 300.0) {
                    flowSuperheatedSteam += loopSteam;
                } else {
                    flowRegularSteam += loopSteam;
                }
            }
            cumSteam += (long) Math.round(coolantLoop.getTotalSecondarySteamProduced());
            dCount += (int) coolantLoop.getTotalDeuteriumProduced();
            tCount += (int) coolantLoop.getTotalTritiumProduced();
        }

        totalSteamProduced = cumSteam;
        totalDeuteriumProduced = dCount;
        totalTritiumProduced = tCount;

        // Calculate power estimation with tier-specific turbines (LST for EV, XLST for IV+) and heat exchangers
        lastPowerResult = TurbineCalculator.calculatePower(
            pipeTier,
            flowRegularSteam,
            flowSuperheatedSteam,
            flowSupercriticalSteam,
            flowHeavyWaterSteam,
            flowHPHeavyWaterSteam,
            flowHotCoolant,
            flowDirectEU,
            turbineMaterial,
            turbineSize,
            turbineFitting);

        lastScenariosResult = TurbineCalculator.calculateBothScenarios(
            pipeTier,
            flowRegularSteam,
            flowSuperheatedSteam,
            flowSupercriticalSteam,
            flowHeavyWaterSteam,
            flowHPHeavyWaterSteam,
            flowHotCoolant,
            flowDirectEU,
            null,
            null);

        grossPowerEUt = lastPowerResult.totalPowerEUt;
        if (shouldStepCoolantLoop()) {
            double pumpPower = coolantLoop.getLastPumpPowerEUt();
            lastPowerResult.totalPowerEUt = Math.max(0.0, lastPowerResult.totalPowerEUt - pumpPower);
        }

        totalEnergyEU += lastPowerResult.totalPowerEUt;

        // Telemetry sampling (keep last 500 ticks for charts)
        if (history.size() >= 500) {
            history.remove(0);
        }
        history.add(
            new TickTelemetry(
                currentTick,
                coreMaxTemp,
                coreAvgTemp,
                efficiency,
                lastNeutronsProduced,
                lastPowerResult.totalPowerEUt,
                true,
                reactorDamage,
                maintenanceIssues,
                getMaintenanceEfficiency()));

        if (this.stopOnIncidents && !this.haltedByIncident && this.reactorDamage > initialDamage) {
            this.haltedByIncident = true;
            this.lastHaltIncidentReason = String.format(
                java.util.Locale.US,
                "Reactor structural damage increased from %.1f%% to %.1f%%",
                initialDamage,
                this.reactorDamage);
            this.incidentLog.add(
                new IncidentEvent(
                    this.currentTick,
                    "PENDING_INCIDENT",
                    String.format(
                        java.util.Locale.US,
                        "Pending Incident: %s. Simulation stopped to prevent further damage.",
                        this.lastHaltIncidentReason),
                    0.0));
        }

        if (this.stopOnIncidents && this.haltedByIncident) {
            return false;
        }

        return true;
    }

    public void triggerExplosion(String reason) {
        this.exploded = true;
        this.explosionReason = reason;
        logIncident("MELTDOWN", reason, 100.0 - this.reactorDamage);
        this.reactorDamage = 100.0;
        if (!history.isEmpty()) {
            TickTelemetry last = history.get(history.size() - 1);
            history.set(
                history.size() - 1,
                new TickTelemetry(
                    last.tick(),
                    last.maxTemp(),
                    last.avgTemp(),
                    last.efficiency(),
                    last.neutrons(),
                    last.powerEUt(),
                    false,
                    100.0,
                    last.maintenanceIssues,
                    0.0));
        }
    }

    public void triggerPowerFail(String reason) {
        this.powerFailed = true;
        this.powerFailReason = reason;
        logIncident("POWER_FAIL", reason, 0.0);
        if (!history.isEmpty()) {
            TickTelemetry last = history.get(history.size() - 1);
            history.set(
                history.size() - 1,
                new TickTelemetry(
                    last.tick(),
                    last.maxTemp(),
                    last.avgTemp(),
                    last.efficiency(),
                    last.neutrons(),
                    last.powerEUt(),
                    false,
                    last.reactorDamage,
                    last.maintenanceIssues,
                    last.maintenanceEfficiency));
        }
    }

    public void triggerThermalShock(int x, int y, String reason) {
        reactorDamage = Math.min(100.0, reactorDamage + 2.0);
        causeNewMaintenanceIssue();
        logIncident("THERMAL_SHOCK", reason, 2.0);
        recordTraceSnapshot(
            "THERMAL_SHOCK",
            String.format(
                java.util.Locale.US,
                "%s [Reactor Damage: %.1f%%, Maint Issues: %d/6]",
                reason,
                reactorDamage,
                maintenanceIssues));
        if (strictMode) {
            triggerPowerFail("Calibration Disqualification: Thermal Shock occurred (" + reason + ")");
            return;
        }
        if (reactorDamage >= 100.0) {
            triggerExplosion(
                "Meltdown: Reactor structural damage reached 100% from catastrophic thermal shock! (" + reason + ")");
        }
    }

    public void triggerDryCoolantShutdown(String reason) {
        triggerThermalShock(-1, -1, reason);
    }

    public double getReactorDamage() {
        return reactorDamage;
    }

    public void setReactorDamage(double damage) {
        this.reactorDamage = Math.max(0.0, Math.min(100.0, damage));
        if (this.reactorDamage < 100.0) {
            if (this.exploded) {
                this.exploded = false;
                this.explosionReason = "";
            }
            if (this.haltedByIncident) {
                this.haltedByIncident = false;
                this.lastHaltIncidentReason = "";
            }
        } else if (this.reactorDamage >= 100.0 && !this.exploded) {
            triggerExplosion("Reactor structural damage set to 100%");
        }
    }

    public int getMaintenanceIssues() {
        return maintenanceIssues;
    }

    public void setMaintenanceIssues(int issues) {
        this.maintenanceIssues = Math.max(0, Math.min(6, issues));
    }

    public double getMaintenanceEfficiency() {
        double baseEff = Math.max(0.0, Math.min(1.0, (6.0 - maintenanceIssues) / 6.0));
        double damageFactor = Math.max(0.0, Math.min(1.0, (100.0 - reactorDamage) / 100.0));
        return baseEff * damageFactor;
    }

    public void causeNewMaintenanceIssue() {
        if (maintenanceIssues < 6) {
            maintenanceIssues++;
        }
    }

    public double getRepairTemperatureThreshold() {
        return NuclearSimulationEngine.getRepairTemperatureThreshold(pipeTier);
    }

    public boolean canRepair() {
        return coreAvgTemp <= getRepairTemperatureThreshold();
    }

    public boolean repairMaintenance() {
        return repairMaintenance(false);
    }

    public boolean repairMaintenance(boolean force) {
        if (!force && !canRepair()) {
            logIncident(
                "REPAIR_FAILED",
                String.format(
                    java.util.Locale.US,
                    "Repair blocked: core average temperature (%.1f°C) exceeds safe repair threshold (%.1f°C)",
                    coreAvgTemp,
                    getRepairTemperatureThreshold()),
                0.0);
            return false;
        }
        this.maintenanceIssues = 0;
        this.reactorDamage = 0.0;
        this.exploded = false;
        this.explosionReason = "";
        this.haltedByIncident = false;
        this.lastHaltIncidentReason = "";
        logIncident("REPAIR", "Reactor structural damage repaired and maintenance issues cleared", 0.0);
        return true;
    }

    public boolean repair() {
        return repairMaintenance(false);
    }

    public boolean forceRepair() {
        return repairMaintenance(true);
    }

    public double getBaseHatchConductance() {
        return NuclearSimulationEngine.baseHatchConductance;
    }

    public void setBaseHatchConductance(double val) {
        NuclearSimulationEngine.setBaseHatchConductance(val);
    }

    /**
     * Loads a preset layout into the grid.
     */
    public void loadPreset(String presetName) {
        resetMetrics();
        NuclearSimulationEngine.resetDefaultParameters();
        switch (presetName.toUpperCase()) {
            // ==================== 60A CALIBRATED BASELINE DESIGNS ====================
            case "60A_ELECTRUM_5X5", "EV_60A", "ELECTRUM_60A" -> {
                this.pipeTier = NuclearSimulationEngine.PIPE_TIER_ELECTRUM;
                applyDefaultTurbinesForTier();
                loadLayout("RB,HC,HC,HC,RB;HC,M4,HC,M4,HC;HC,HC,M4,HC,HC;HC,M4,HC,M4,HC;RB,HC,HC,HC,RB");
                updateHatchCapacities(8000);
            }
            case "60A_PLATINUM_9X9", "IV_60A", "PLATINUM_60A" -> {
                this.pipeTier = NuclearSimulationEngine.PIPE_TIER_PLATINUM;
                applyDefaultTurbinesForTier();
                loadLayout(
                    "RB,RB,HC,HC,HC,HC,HC,RB,RB;RB,HC,HC,HC,HC,HC,HC,HC,RB;HC,HC,M4,HC,M4,HC,M4,HC,HC;HC,HC,HC,M4,HC,M4,HC,HC,HC;HC,HC,M4,HC,M4,HC,M4,HC,HC;HC,HC,HC,HC,HC,HC,HC,HC,HC;HC,HC,HC,HC,HC,HC,HC,HC,HC;RB,HC,HC,HC,HC,HC,HC,HC,RB;RB,RB,HC,HC,HC,HC,HC,RB,RB");
                updateHatchCapacities(16000);
            }
            case "60A_OSMIUM_9X9", "LUV_60A", "OSMIUM_60A" -> {
                this.pipeTier = NuclearSimulationEngine.PIPE_TIER_OSMIUM;
                applyDefaultTurbinesForTier();
                loadLayout(
                    "RB,RB,CP,CP,CP,CP,CP,RB,RB;RB,CP,CP,CP,CP,CP,CP,CP,RB;CP,CP,CP,M4,CP,M4,CP,CP,CP;CP,CP,M4,CP,CP,CP,M4,CP,CP;CP,CP,CP,CP,CP,CP,CP,CP,CP;CP,CP,M4,CP,CP,CP,M4,CP,CP;CP,CP,CP,M4,CP,M4,CP,CP,CP;RB,CP,M4,CP,CP,CP,M4,CP,RB;RB,RB,CP,CP,CP,CP,CP,RB,RB");
                updateHatchCapacities(32000);
            }
            case "60A_QUANTIUM_13X13", "ZPM_60A", "QUANTIUM_60A" -> {
                this.pipeTier = NuclearSimulationEngine.PIPE_TIER_QUANTIUM;
                applyDefaultTurbinesForTier();
                loadLayout(
                    "RB,RB,RB,CP,CP,CP,CP,CP,CP,CP,RB,RB,RB;RB,RB,CP,CP,CP,CP,CP,CP,CP,CP,CP,RB,RB;RB,CP,CP,CP,CP,CP,CP,CP,CP,CP,CP,CP,RB;CP,CP,CP,CP,CP,CP,CP,CP,CP,CP,CP,CP,CP;CP,CP,M4,CP,CP,M4,CP,M4,CP,CP,M4,CP,CP;CP,CP,CP,M4,CP,M4,CP,M4,CP,M4,CP,CP,CP;CP,CP,CP,CP,M4,CP,CP,CP,M4,CP,CP,CP,CP;CP,CP,CP,M4,CP,M4,CP,M4,CP,M4,CP,CP,CP;CP,CP,M4,CP,CP,M4,CP,M4,CP,CP,M4,CP,CP;CP,CP,CP,CP,CP,M4,CP,M4,CP,CP,CP,CP,CP;RB,CP,CP,CP,CP,CP,CP,CP,CP,CP,CP,CP,RB;RB,RB,CP,CP,CP,CP,CP,CP,CP,CP,CP,RB,RB;RB,RB,RB,CP,CP,CP,CP,CP,CP,CP,RB,RB,RB");
                updateHatchCapacities(64000);
            }
            case "60A_FLUXED_13X13", "UV_60A", "FLUXED_60A" -> {
                this.pipeTier = NuclearSimulationEngine.PIPE_TIER_FLUXED_ELECTRUM;
                applyDefaultTurbinesForTier();
                loadLayout(
                    "RB,RB,RB,CP,CP,CP,CP,CP,CP,CP,RB,RB,RB;RB,RB,CP,NQR,CP,CP,CP,CP,CP,NQR,CP,RB,RB;RB,NQR,CP,NQR,CP,CP,CP,CP,CP,NQR,CP,NQR,RB;NQR,CP,NQR,CP,NQR,NQR,CP,NQR,NQR,CP,NQR,CP,NQR;CP,CP,CP,CP,CP,CP,CP,CP,CP,CP,CP,CP,CP;NQR,CP,CP,NQR,CP,CP,CP,CP,CP,NQR,CP,CP,NQR;CP,CP,CP,CP,NQR,NQR,CR,NQR,NQR,CP,CP,CP,CP;NQR,CP,CP,NQR,CP,CP,CP,CP,CP,NQ,CP,CP,NQ;CP,CP,CP,CP,CP,CP,CP,CP,CP,CP,CP,CP,CP;NQ,CP,NQ,CP,NQ,NQ,CP,NQ,NQ,CP,NQ,CP,NQ;RB,NQ,CP,NQ,CP,CP,CP,CP,CP,NQ,CP,NQ,RB;RB,RB,CP,NQ,CP,CP,CP,CP,CP,NQ,CP,RB,RB;RB,RB,RB,CP,CP,CP,CP,CP,CP,CP,RB,RB,RB");
                updateHatchCapacities(128000);
            }
            case "60A_PLUTONIUM_13X13", "UHV_60A", "PLUTONIUM_60A" -> {
                this.pipeTier = NuclearSimulationEngine.PIPE_TIER_BLACK_PLUTONIUM;
                applyDefaultTurbinesForTier();
                loadLayout(
                    "RB,RB,RB,CP,CP,CP,CP,CP,CP,CP,RB,RB,RB;RB,NQR,CP,NQR,CP,NQR,CP,NQR,CP,NQR,CP,NQR,RB;RB,CP,NQR,CP,NQR,CP,NQR,CP,NQR,CP,NQR,CP,RB;CP,NQR,CP,NQR,CP,NQR,CP,NQR,CP,NQR,CP,NQR,CP;CP,CP,NQR,CP,NQR,CP,CR,CP,NQR,CP,NQR,CP,CP;CP,NQR,CP,NQR,CP,NQR,CP,NQR,CP,NQR,CP,NQR,CP;CP,CP,NQR,CP,CR,CP,NQR,CP,CR,CP,NQR,CP,CP;CP,NQR,CP,NQR,CP,NQR,CP,NQR,CP,NQR,CP,NQR,CP;CP,CP,NQR,CP,NQR,CP,CR,CP,NQR,CP,NQR,CP,CP;CP,NQR,CP,NQR,CP,NQR,CP,NQR,CP,NQR,CP,NQR,CP;RB,CP,NQR,CP,NQR,CP,NQR,CP,NQR,CP,NQR,CP,RB;RB,NQR,CP,NQR,CP,NQR,CP,NQR,CP,NQR,CP,NQR,RB;RB,RB,RB,CP,CP,CP,CP,CP,CP,CP,RB,RB,RB");
                updateHatchCapacities(256000);
            }

            // ==================== MAXXED-OUT OPTIMUM CEILINGS (nuclear_ceiling_best.json) ====================
            case "BEST_ELECTRUM_5X5", "MAX_ELECTRUM_5X5", "ELECTRUM_POWER_5X5", "BASIC_ELECTRUM_5X5", "EV_PEAK" -> {
                this.pipeTier = NuclearSimulationEngine.PIPE_TIER_ELECTRUM;
                this.turbineMaterial = TurbineCalculator.TurbineMaterial.ORINARUKON;
                this.turbineSize = TurbineCalculator.TurbineSize.NORMAL;
                this.turbineFitting = TurbineCalculator.FittingMode.TIGHT;
                loadLayout("RB,HD,M4,HD,RB;U4,M4,M4,M4,U4;M4,HD,HD,HD,M4;U4,M4,M4,M4,U4;RB,HD,M4,HD,RB");
                updateHatchCapacities(8000);
            }
            case "BREEDER_7X7", "BEST_PLATINUM_7X7" -> {
                this.pipeTier = NuclearSimulationEngine.PIPE_TIER_PLATINUM;
                this.turbineMaterial = TurbineCalculator.TurbineMaterial.HSS_S;
                this.turbineSize = TurbineCalculator.TurbineSize.LARGE;
                this.turbineFitting = TurbineCalculator.FittingMode.TIGHT;
                loadLayout(
                    "U4,U4,U4,M4,U4,U4,U4;U4,M4,HD,HD,HD,M4,U4;T4,HD,M4,HD,M4,HD,T4;U4,M2,HD,HD,HD,M2,U4;T4,HD,M4,HD,M4,HD,T4;U4,M4,HD,HD,HD,M4,U4;U4,U4,U4,M4,U4,U4,U4");
                updateHatchCapacities(16000);
            }
            case "BEST_PLATINUM_9X9", "MAX_PLATINUM_9X9", "BREEDER_9X9", "IV_PEAK" -> {
                this.pipeTier = NuclearSimulationEngine.PIPE_TIER_PLATINUM;
                this.turbineMaterial = TurbineCalculator.TurbineMaterial.ICHORIUM;
                this.turbineSize = TurbineCalculator.TurbineSize.LARGE;
                this.turbineFitting = TurbineCalculator.FittingMode.TIGHT;
                loadLayout(
                    "RB,RB,HD,M4,HDP,M4,HD,RB,RB;RB,M4,HD,HDP,CP,HDP,HD,M4,RB;HDP,HD,M4,HP,HDP,HP,M4,HD,HDP;M4,M4,HC,M4,CP,M4,HC,M4,M4;G1,M4,CP,HC,U4,HC,CP,M4,G1;M4,M4,HC,M4,CP,M4,HC,M4,M4;HDP,HD,M4,HP,HDP,HP,M4,HD,HDP;RB,M4,HD,HDP,CP,HDP,HD,M4,RB;RB,RB,HD,M4,HDP,M4,HD,RB,RB");
                updateHatchCapacities(16000);
            }
            case "BEST_OSMIUM_9X9", "MAX_OSMIUM_9X9", "SUPERHEATED_POWER_9X9", "BEST_OSMIUM_7X7", "SUPERHEATED_POWER_7X7", "LUV_PEAK" -> {
                this.pipeTier = NuclearSimulationEngine.PIPE_TIER_OSMIUM;
                this.turbineMaterial = TurbineCalculator.TurbineMaterial.DURANIUM;
                this.turbineSize = TurbineCalculator.TurbineSize.LARGE;
                this.turbineFitting = TurbineCalculator.FittingMode.TIGHT;
                loadLayout(
                    "RB,RB,HDP,EXP,CP,EXP,HDP,RB,RB;RB,EXU,HD,EXP,EXP,EXP,HD,EXU,RB;HDP,HD,EXP,HD,HDP,HD,EXP,HD,HDP;EXP,EXP,CP,EXP,HC,EXP,CP,EXP,EXP;HD,CP,EXU,CP,CP,CP,EXU,CP,HD;EXP,EXP,CP,EXP,HC,EXP,CP,EXP,EXP;HDP,HD,EXP,HD,HDP,HD,EXP,HD,HDP;RB,EXU,HD,EXP,EXP,EXP,HD,EXU,RB;RB,RB,HDP,EXP,CP,EXP,HDP,RB,RB");
                updateHatchCapacities(32000);
            }
            case "BEST_QUANTIUM_13X13", "BEST_QUANTIUM_9X9", "MAX_QUANTIUM_13X13", "CANDU_HEAVY_WATER_13X13", "CANDU_HEAVY_WATER_9X9", "ZPM_PEAK" -> {
                this.pipeTier = NuclearSimulationEngine.PIPE_TIER_QUANTIUM;
                this.turbineMaterial = TurbineCalculator.TurbineMaterial.DURANIUM;
                this.turbineSize = TurbineCalculator.TurbineSize.HUGE;
                this.turbineFitting = TurbineCalculator.FittingMode.TIGHT;
                loadLayout(
                    "RB,RB,RB,HW,HW,U4,X1,U4,HW,HW,RB,RB,RB;RB,RB,HW,HW,EXP,NQ,HW,NQ,EXP,HW,HW,RB,RB;RB,HW,HW,EXU,HW,XA,HW,XA,HW,EXU,HW,HW,RB;HW,HDU,NQ,HW,TIB,HW,HW,HW,TIB,HW,NQ,HDU,HW;EXP,XA,HDP,NQ,HW,CP,HW,CP,HW,NQ,HDP,XA,EXP;EXP,U4,CP,HW,NQ,HW,NQ,HW,NQ,HW,CP,U4,EXP;CP,HW,NQ,M4,HW,NQ,XA,NQ,HW,M4,NQ,HW,CP;EXP,U4,CP,HW,NQ,HW,NQ,HW,NQ,HW,CP,U4,EXP;EXP,XA,HDP,NQ,HW,CP,HW,CP,HW,NQ,HDP,XA,EXP;HW,HDU,NQ,HW,TIB,HW,HW,HW,TIB,HW,NQ,HDU,HW;RB,HW,HW,EXU,HW,XA,HW,XA,HW,EXU,HW,HW,RB;RB,RB,HW,HW,EXP,NQ,HW,NQ,EXP,HW,HW,RB,RB;RB,RB,RB,HW,HW,U4,X1,U4,HW,HW,RB,RB,RB");
                updateHatchCapacities(64000);
            }
            case "BEST_FLUXED_13X13", "BEST_FLUXED_9X9", "MAX_FLUXED_13X13", "FLUXED_SUPERCRITICAL_13X13", "FLUXED_SUPERCRITICAL_9X9", "UV_PEAK" -> {
                this.pipeTier = NuclearSimulationEngine.PIPE_TIER_FLUXED_ELECTRUM;
                this.turbineMaterial = TurbineCalculator.TurbineMaterial.DURANIUM;
                this.turbineSize = TurbineCalculator.TurbineSize.HUGE;
                this.turbineFitting = TurbineCalculator.FittingMode.TIGHT;
                loadLayout(
                    "RB,RB,RB,VA,XC,HW,HH,HW,XC,VA,RB,RB,RB;RB,RB,EXU,HW,HW,NQR,HW,NQR,HW,HW,EXU,RB,RB;RB,HW,HW,NQR,CP,NQR,HH,NQR,CP,NQR,HW,HW,RB;M4,HW,NQR,HW,NQR,CP,NQR,CP,NQR,HW,NQR,HW,M4;HW,NQR,HW,NQR,HW,CP,NQR,CP,HW,NQR,HW,NQR,HW;TIB,HW,NQR,HW,HW,HW,RH,HW,HW,HW,NQR,HW,TIB;HW,NQR,HH,M4,NQ,HW,HW,HW,NQ,M4,HH,NQR,HW;TIB,HW,NQR,HW,HW,HW,RH,HW,HW,HW,NQR,HW,TIB;HW,NQR,HW,NQR,HW,CP,NQR,CP,HW,NQR,HW,NQR,HW;M4,HW,NQR,HW,NQR,CP,NQR,CP,NQR,HW,NQR,HW,M4;RB,HW,HW,NQR,CP,NQR,HH,NQR,CP,NQR,HW,HW,RB;RB,RB,EXU,HW,HW,NQR,HW,NQR,HW,HW,EXU,RB,RB;RB,RB,RB,VA,XC,HW,HH,HW,XC,VA,RB,RB,RB");
                updateHatchCapacities(128000);
            }
            case "BEST_PLUTONIUM_13X13", "BEST_PLUTONIUM_9X9", "MAX_PLUTONIUM_13X13", "BLACK_PLUTONIUM_13X13", "BLACK_PLUTONIUM_9X9", "UHV_PEAK" -> {
                this.pipeTier = NuclearSimulationEngine.PIPE_TIER_BLACK_PLUTONIUM;
                this.turbineMaterial = TurbineCalculator.TurbineMaterial.INFINITY;
                this.turbineSize = TurbineCalculator.TurbineSize.HUGE;
                this.turbineFitting = TurbineCalculator.FittingMode.LOOSE;
                loadLayout(
                    "RB,RB,RB,M2,CP,T4,T1,T4,CP,M2,RB,RB,RB;RB,RB,HW,CP,U4,HW,CP,HW,U4,CP,HW,RB,RB;RB,U1,HW,NQ32,HW,NQ32,CP,NQ32,HW,NQ32,HW,U1,RB;NQR,HW,NQR,CP,HW,HH,HW,HH,HW,CP,NQR,HW,NQR;NQ,CP,X1,HW,TIB,HW,T4,HW,TIB,HW,X1,CP,NQ;CP,NQ32,HW,NQR,HC,NQR,VA,NQR,HC,NQR,HW,NQ32,CP;HW,CP,RH,V1,HP,HW,HW,HW,HP,V1,RH,CP,HW;CP,NQ32,HW,NQR,HC,NQR,VA,NQR,HC,NQR,HW,NQ32,CP;NQ,CP,X1,HW,TIB,HW,T4,HW,TIB,HW,X1,CP,NQ;NQR,HW,NQR,CP,HW,HH,HW,HH,HW,CP,NQR,HW,NQR;RB,U1,HW,NQ32,HW,NQ32,CP,NQ32,HW,NQ32,HW,U1,RB;RB,RB,HW,CP,U4,HW,CP,HW,U4,CP,HW,RB,RB;RB,RB,RB,M2,CP,T4,T1,T4,CP,M2,RB,RB,RB");
                updateHatchCapacities(256000);
            }
            default -> {
                this.pipeTier = NuclearSimulationEngine.PIPE_TIER_ELECTRUM;
                applyDefaultTurbinesForTier();
                loadLayout("RB,HC,HC,HC,RB;HC,M4,HC,M4,HC;HC,HC,M4,HC,HC;HC,M4,HC,M4,HC;RB,HC,HC,HC,RB");
                updateHatchCapacities(8000);
            }
        }
    }

    /**
     * Loads a custom layout string into the grid.
     * Can accept row-separated formats (with ';' or '/' or newline) or flat comma/space-separated codes.
     */
    public void loadLayout(String layoutStr) {
        if (layoutStr == null || layoutStr.trim()
            .isEmpty()) return;
        resetMetrics();
        String trimmed = layoutStr.trim();
        String[] rows = trimmed.split("[;/\\n]+");
        if (rows.length > 1) {
            int h = rows.length;
            String[] firstRowCols = rows[0].trim()
                .split("[,\\s]+");
            int w = firstRowCols.length;
            if (w > 0 && h > 0) {
                this.width = w;
                this.height = h;
                this.grid = new SimTile[w][h];
                clearGrid();
                for (int y = 0; y < h; y++) {
                    String[] cols = rows[y].trim()
                        .split("[,\\s]+");
                    for (int x = 0; x < Math.min(w, cols.length); x++) {
                        setTile(x, y, SimTile.TileType.fromCode(cols[x]));
                    }
                }
                return;
            }
        }

        // Flat sequence of codes
        String[] tokens = trimmed.split("[,\\s]+");
        int sqrt = (int) Math.round(Math.sqrt(tokens.length));
        if (sqrt * sqrt == tokens.length && (this.width * this.height != tokens.length)) {
            this.width = sqrt;
            this.height = sqrt;
            this.grid = new SimTile[sqrt][sqrt];
        }
        clearGrid();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                if (idx < tokens.length) {
                    setTile(x, y, SimTile.TileType.fromCode(tokens[idx]));
                }
            }
        }
    }

    /**
     * Serializes the current grid layout to a compact string format: rows separated by ';', cells separated by ','.
     */
    public String toLayoutString() {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < height; y++) {
            if (y > 0) sb.append(";");
            for (int x = 0; x < width; x++) {
                if (x > 0) sb.append(",");
                sb.append(grid[x][y].getType().code);
            }
        }
        return sb.toString();
    }

    /**
     * Converts grid to ASCII representation.
     */
    public String toAscii() {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                sb.append(String.format("%-3s", grid[x][y].getType().code));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // Getters and configuration
    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getPipeTier() {
        return pipeTier;
    }

    public void setPipeTier(int pipeTier) {
        this.pipeTier = pipeTier;
        applyDefaultTurbinesForTier();
        applyDefaultCoolingForTier();
        int hatchTier = Math.max(1, pipeTier + 4);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SimTile tile = grid[x][y];
                if (tile != null && tile.isHatch()) {
                    tile.setTier(hatchTier);
                    tile.setInputFluidCapacity(8000 * (1 << hatchTier));
                }
            }
        }
        if (pipeTier < NuclearSimulationEngine.PIPE_TIER_PLATINUM
            && coolingMode == CoolantLoopModel.CoolingMode.CONVECTIVE_LOOP) {
            coolingMode = CoolantLoopModel.CoolingMode.MODULAR;
        }
    }

    public CoolantLoopModel.CoolingMode getCoolingMode() {
        return coolingMode;
    }

    public boolean setCoolingMode(CoolantLoopModel.CoolingMode mode) {
        if (mode == CoolantLoopModel.CoolingMode.CONVECTIVE_LOOP) {
            if (this.pipeTier < NuclearSimulationEngine.PIPE_TIER_PLATINUM) {
                return false; // Convective loop requires Tier 2+
            }
        }
        this.coolingMode = (mode != null) ? mode : CoolantLoopModel.CoolingMode.MODULAR;
        return true;
    }

    public boolean isCoolantLoopActive() {
        if (!isTier2ConvectiveAllowed()) return false;
        if (coolantLoop == null) return false;
        if (!coolantLoop.getAttachedPoints()
            .isEmpty()) return true;
        return hasCoolantLoopPassages();
    }

    public boolean hasCoolantLoopPassages() {
        if (grid == null) return false;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (grid[x][y] != null && grid[x][y].getType() == SimTile.TileType.PASSAGE_CORE) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean shouldStepCoolantLoop() {
        if (!isTier2ConvectiveAllowed()) {
            return false;
        }
        if (coolingMode == CoolantLoopModel.CoolingMode.CONDUCTIVE) {
            return false;
        }
        return coolingMode == CoolantLoopModel.CoolingMode.CONVECTIVE_LOOP || isCoolantLoopActive();
    }

    public CoolantLoopModel getCoolantLoop() {
        return coolantLoop;
    }

    public void setCoolantLoop(CoolantLoopModel loop) {
        if (loop != null) {
            this.coolantLoop = loop;
        }
    }

    public double getGrossPowerEUt() {
        return grossPowerEUt;
    }

    public double getPumpPowerEUt() {
        return shouldStepCoolantLoop() ? coolantLoop.getLastPumpPowerEUt() : 0.0;
    }

    public void updatePowerResultNet() {
        if (lastPowerResult != null) {
            double net = grossPowerEUt;
            if (shouldStepCoolantLoop()) {
                net = Math.max(0.0, net - coolantLoop.getLastPumpPowerEUt());
            }
            lastPowerResult.totalPowerEUt = net;
        }
    }

    public void applyDefaultTurbinesForTier() {
        TurbineCalculator.ScenarioTurbineConfig cfg = TurbineCalculator.getDefaultTightTurbine(this.pipeTier);
        this.turbineMaterial = cfg.material;
        this.turbineSize = cfg.size;
        this.turbineFitting = cfg.mode;
    }

    public void applyDefaultCoolingForTier() {
        if (coolantLoop == null) return;
        coolantLoop.setPumpOverclocked(false);
        coolantLoop.setDutyCyclePercent(100.0);
        coolantLoop.setMaxFlowRateLPerSec(0.0);
        coolantLoop.setMaxPressureBar(0.0);
        switch (this.pipeTier) {
            case NuclearSimulationEngine.PIPE_TIER_ELECTRUM -> {
                coolantLoop.setMaterial(CoolantLoopModel.LoopMaterial.TITANIUM);
                coolantLoop.setPipeSize(CoolantLoopModel.LoopPipeSize.NORMAL);
                coolantLoop.setFluidType(CoolantLoopModel.CoolantFluidType.DISTILLED_WATER);
                coolantLoop.setHatchTier(CoolantLoopModel.EnergyHatchTier.EV);
                coolantLoop.setImpellerMaterial("ORIHARUKON");
            }
            case NuclearSimulationEngine.PIPE_TIER_PLATINUM -> {
                coolantLoop.setMaterial(CoolantLoopModel.LoopMaterial.TUNGSTENSTEEL);
                coolantLoop.setPipeSize(CoolantLoopModel.LoopPipeSize.LARGE);
                coolantLoop.setFluidType(CoolantLoopModel.CoolantFluidType.DISTILLED_WATER);
                coolantLoop.setHatchTier(CoolantLoopModel.EnergyHatchTier.IV);
                coolantLoop.setImpellerMaterial("ICHORIUM");
            }
            case NuclearSimulationEngine.PIPE_TIER_OSMIUM -> {
                coolantLoop.setMaterial(CoolantLoopModel.LoopMaterial.OSMIUM);
                coolantLoop.setPipeSize(CoolantLoopModel.LoopPipeSize.LARGE);
                coolantLoop.setFluidType(CoolantLoopModel.CoolantFluidType.DISTILLED_WATER);
                coolantLoop.setHatchTier(CoolantLoopModel.EnergyHatchTier.LUV);
                coolantLoop.setImpellerMaterial("DURANIUM");
            }
            case NuclearSimulationEngine.PIPE_TIER_QUANTIUM -> {
                coolantLoop.setMaterial(CoolantLoopModel.LoopMaterial.NEUTRONIUM);
                coolantLoop.setPipeSize(CoolantLoopModel.LoopPipeSize.HUGE);
                coolantLoop.setFluidType(CoolantLoopModel.CoolantFluidType.HEAVY_WATER);
                coolantLoop.setHatchTier(CoolantLoopModel.EnergyHatchTier.ZPM);
                coolantLoop.setImpellerMaterial("DURANIUM");
            }
            case NuclearSimulationEngine.PIPE_TIER_FLUXED_ELECTRUM -> {
                coolantLoop.setMaterial(CoolantLoopModel.LoopMaterial.NEUTRONIUM);
                coolantLoop.setPipeSize(CoolantLoopModel.LoopPipeSize.HUGE);
                coolantLoop.setFluidType(CoolantLoopModel.CoolantFluidType.HEAVY_WATER);
                coolantLoop.setHatchTier(CoolantLoopModel.EnergyHatchTier.UV);
                coolantLoop.setImpellerMaterial("DURANIUM");
            }
            case NuclearSimulationEngine.PIPE_TIER_BLACK_PLUTONIUM -> {
                coolantLoop.setMaterial(CoolantLoopModel.LoopMaterial.NEUTRONIUM);
                coolantLoop.setPipeSize(CoolantLoopModel.LoopPipeSize.HUGE);
                coolantLoop.setFluidType(CoolantLoopModel.CoolantFluidType.HEAVY_WATER);
                coolantLoop.setHatchTier(CoolantLoopModel.EnergyHatchTier.UHV);
                coolantLoop.setImpellerMaterial("INFINITY");
            }
        }
        if (isTier2ConvectiveAllowed()) {
            this.coolingMode = CoolantLoopModel.CoolingMode.CONVECTIVE_LOOP;
        }
        updatePowerResultNet();
    }

    public long getCurrentTick() {
        return currentTick;
    }

    public boolean isExploded() {
        return exploded;
    }

    public String getExplosionReason() {
        return explosionReason;
    }

    public boolean isPowerFailed() {
        return powerFailed;
    }

    public String getPowerFailReason() {
        return powerFailReason;
    }

    public double getCoreMaxTemp() {
        return coreMaxTemp;
    }

    public double getPeakLifetimeTemp() {
        return peakLifetimeTemp;
    }

    public int getBurnedFuelCount() {
        return burnedFuelCount;
    }

    public int getVoidedHatchCount() {
        return voidedHatchCount;
    }

    public boolean hasFuelBurned() {
        return burnedFuelCount > 0;
    }

    public double getCoreAvgTemp() {
        return coreAvgTemp;
    }

    public double getEfficiency() {
        return efficiency;
    }

    public int getLastNeutronsProduced() {
        return lastNeutronsProduced;
    }

    public int getLastFastAbsorbed() {
        return lastFastAbsorbed;
    }

    public int getLastThermalAbsorbed() {
        return lastThermalAbsorbed;
    }

    public int getLastEscapedNeutrons() {
        return lastEscapedNeutrons;
    }

    public int getLastWallReflected() {
        return lastWallReflected;
    }

    public int getLastWallAbsorbed() {
        return lastWallAbsorbed;
    }

    public double getLastWallHeatPool() {
        return lastWallHeatPool;
    }

    public long getTotalNeutronsGenerated() {
        return totalNeutronsGenerated;
    }

    public long getTotalSteamProduced() {
        return totalSteamProduced;
    }

    public double getTotalEnergyEU() {
        return totalEnergyEU;
    }

    public int getTotalDeuteriumProduced() {
        return totalDeuteriumProduced;
    }

    public int getTotalTritiumProduced() {
        return totalTritiumProduced;
    }

    public List<TickTelemetry> getHistory() {
        return history;
    }

    public TurbineCalculator.TurbineMaterial getTurbineMaterial() {
        return turbineMaterial;
    }

    public void setTurbineMaterial(TurbineCalculator.TurbineMaterial turbineMaterial) {
        this.turbineMaterial = turbineMaterial;
    }

    public TurbineCalculator.TurbineSize getTurbineSize() {
        return turbineSize;
    }

    public void setTurbineSize(TurbineCalculator.TurbineSize turbineSize) {
        this.turbineSize = turbineSize;
    }

    public TurbineCalculator.FittingMode getTurbineFitting() {
        return turbineFitting;
    }

    public void setTurbineFitting(TurbineCalculator.FittingMode turbineFitting) {
        this.turbineFitting = turbineFitting;
    }

    public double getFlowRegularSteam() {
        return flowRegularSteam;
    }

    public double getFlowSuperheatedSteam() {
        return flowSuperheatedSteam;
    }

    public double getFlowSupercriticalSteam() {
        return flowSupercriticalSteam;
    }

    public double getFlowHeavyWaterSteam() {
        return flowHeavyWaterSteam;
    }

    public double getFlowHPHeavyWaterSteam() {
        return flowHPHeavyWaterSteam;
    }

    public double getFlowHotCoolant() {
        return flowHotCoolant;
    }

    public double getFlowDirectEU() {
        return flowDirectEU;
    }

    public TurbineCalculator.PowerEstimationResult getLastPowerResult() {
        return lastPowerResult;
    }

    public TurbineCalculator.ScenarioHypotheticalResult getLastScenariosResult() {
        return lastScenariosResult;
    }

    public int getActiveFuelRodCount() {
        int count = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SimTile tile = grid[x][y];
                if (tile != null && tile.isFuel() && !tile.isDepleted()) {
                    count++;
                }
            }
        }
        return count;
    }

    public double getMinFuelRodLongevityMinutes() {
        double minMins = Double.POSITIVE_INFINITY;
        if (currentTick <= 0) return minMins;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SimTile tile = grid[x][y];
                if (tile != null && tile.isFuel()) {
                    int lost = tile.getMaxDurability() - tile.getDurability();
                    if (lost > 0) {
                        double ratePerTick = (double) lost / (double) currentTick;
                        double ticksRemaining = (double) tile.getMaxDurability() / ratePerTick;
                        double mins = ticksRemaining / (20.0 * 60.0);
                        if (mins < minMins) {
                            minMins = mins;
                        }
                    }
                }
            }
        }
        return minMins;
    }

    public double getAvgFuelRodLongevityMinutes() {
        double sumMins = 0;
        int count = 0;
        if (currentTick <= 0) return Double.POSITIVE_INFINITY;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SimTile tile = grid[x][y];
                if (tile != null && tile.isFuel()) {
                    int lost = tile.getMaxDurability() - tile.getDurability();
                    if (lost > 0) {
                        double ratePerTick = (double) lost / (double) currentTick;
                        double ticksRemaining = (double) tile.getMaxDurability() / ratePerTick;
                        double mins = ticksRemaining / (20.0 * 60.0);
                        sumMins += mins;
                        count++;
                    }
                }
            }
        }
        return count > 0 ? (sumMins / count) : Double.POSITIVE_INFINITY;
    }

    public int getTotalDurabilityLost() {
        int sum = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SimTile tile = grid[x][y];
                if (tile != null && tile.isFuel()) {
                    sum += (tile.getMaxDurability() - tile.getDurability());
                }
            }
        }
        return sum;
    }

    public boolean isTier2ConvectiveAllowed() {
        return this.pipeTier >= NuclearSimulationEngine.PIPE_TIER_PLATINUM;
    }

    public boolean isAutoReplaceFuel() {
        return autoReplaceFuel;
    }

    public void setAutoReplaceFuel(boolean autoReplaceFuel) {
        this.autoReplaceFuel = autoReplaceFuel;
    }

    public long getCumulativeDepletedItems(SimTile.TileType type) {
        return cumulativeDepletedFuelItems.getOrDefault(type, 0L);
    }

    public long getCumulativeDepletedLiquid(SimTile.TileType type) {
        return cumulativeDepletedLiquidLiters.getOrDefault(type, 0L);
    }

    public List<SolidFuelByproduct> getSolidFuelByproducts() {
        List<SolidFuelByproduct> list = new ArrayList<>();
        for (SimTile.TileType type : SimTile.TileType.values()) {
            int active = 0;
            double totalDamage = 0.0;
            int maxDur = 0;
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    SimTile tile = grid[x][y];
                    if (tile != null && tile.getType() == type) {
                        if (tile.isFuel() && !tile.isDepleted()) {
                            active++;
                            totalDamage += tile.getLastDurabilityLoss();
                            maxDur = tile.getMaxDurability();
                        }
                    }
                }
            }
            long produced = cumulativeDepletedFuelItems.getOrDefault(type, 0L);
            if (active > 0 || produced > 0) {
                if (maxDur <= 0) {
                    com.gtnewhorizons.modularnuclear.common.nuclear.NuclearFuelType f = new SimTile(type).getFuelType();
                    if (f != null) maxDur = f.defaultDurability;
                }
                double itemsPerMin = (maxDur > 0) ? (totalDamage / (double) maxDur) * 1200.0 : 0.0;
                if (itemsPerMin == 0.0 && currentTick == 0 && active > 0 && maxDur > 0) {
                    itemsPerMin = ((double) active / (double) maxDur) * 1200.0;
                }
                double itemsPerHour = itemsPerMin * 60.0;
                double avgLifespan = (active > 0 && totalDamage > 0 && maxDur > 0)
                    ? ((double) maxDur / (totalDamage / (double) active)) / 1200.0
                    : Double.POSITIVE_INFINITY;
                SimTile sample = new SimTile(type);
                list.add(
                    new SolidFuelByproduct(
                        type,
                        type.displayName,
                        type.code,
                        sample.getDepletedDisplayName(),
                        sample.getDepletedCode(),
                        active,
                        itemsPerMin,
                        itemsPerHour,
                        produced,
                        avgLifespan));
            }
        }
        return list;
    }

    public List<LiquidFuelByproduct> getLiquidFuelByproducts() {
        List<LiquidFuelByproduct> list = new ArrayList<>();
        SimTile.TileType[] liquidTypes = new SimTile.TileType[] { SimTile.TileType.HATCH_LIQUID_FUEL_URANIUM,
            SimTile.TileType.HATCH_LIQUID_FUEL_THORIUM, SimTile.TileType.HATCH_LIQUID_FUEL_PLUTONIUM };
        for (SimTile.TileType type : liquidTypes) {
            int active = 0;
            int totalBurned = 0;
            String fluidName = "";
            String dispName = "";
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    SimTile tile = grid[x][y];
                    if (tile != null && tile.getType() == type) {
                        active++;
                        totalBurned += tile.getLastLiquidFuelBurned();
                        fluidName = tile.getOutputFluidName();
                        dispName = tile.getDepletedDisplayName();
                    }
                }
            }
            long totalLiters = cumulativeDepletedLiquidLiters.getOrDefault(type, 0L);
            if (active > 0 || totalLiters > 0) {
                if (fluidName.isEmpty()) {
                    SimTile sample = new SimTile(type);
                    fluidName = sample.getOutputFluidName();
                    dispName = sample.getDepletedDisplayName();
                }
                double lPerMin = totalBurned * 1200.0;
                if (lPerMin == 0.0 && currentTick == 0 && active > 0) {
                    lPerMin = active * 1200.0;
                }
                double lPerHour = lPerMin * 60.0;
                list.add(new LiquidFuelByproduct(type, fluidName, dispName, active, lPerMin, lPerHour, totalLiters));
            }
        }
        return list;
    }

    public List<IsotopeByproduct> getIsotopeByproducts() {
        List<IsotopeByproduct> list = new ArrayList<>();
        double dPerMin = (currentTick > 0) ? ((double) totalDeuteriumProduced / (double) currentTick) * 1200.0 : 0.0;
        double dPerHour = dPerMin * 60.0;
        double tPerMin = (currentTick > 0) ? ((double) totalTritiumProduced / (double) currentTick) * 1200.0 : 0.0;
        double tPerHour = tPerMin * 60.0;
        list.add(new IsotopeByproduct("Deuterium", "D", dPerMin, dPerHour, totalDeuteriumProduced));
        list.add(new IsotopeByproduct("Tritium", "T", tPerMin, tPerHour, totalTritiumProduced));
        return list;
    }

    public List<RawMaterialBalance> getRawMaterialBalances() {
        class MatAcc {

            final String name;
            final String code;
            final String unit;
            double consumedPerMin = 0.0;
            double producedPerMin = 0.0;

            MatAcc(String name, String code, String unit) {
                this.name = name;
                this.code = code;
                this.unit = unit;
            }
        }

        Map<String, MatAcc> map = new LinkedHashMap<>();
        map.put("U-235", new MatAcc("Uranium-235", "U-235", "dust"));
        map.put("U-238", new MatAcc("Uranium-238", "U-238", "dust"));
        map.put("Pu-239", new MatAcc("Plutonium-239", "Pu-239", "dust"));
        map.put("Pu-241", new MatAcc("Plutonium-241", "Pu-241", "dust"));
        map.put("Th-232", new MatAcc("Thorium-232", "Th-232", "dust"));
        map.put("Lu", new MatAcc("Lutetium", "Lu", "dust"));
        map.put("NQ", new MatAcc("Naquadah", "NQ", "dust"));
        map.put("NQ+", new MatAcc("Enriched Naquadah", "NQ+", "dust"));
        map.put("NQR", new MatAcc("Naquadria", "NQR", "dust"));
        map.put("TIB", new MatAcc("Tiberium", "TIB", "dust"));
        map.put("Li", new MatAcc("Lithium", "Li", "dust"));
        map.put("G", new MatAcc("Glowstone", "G", "dust"));
        map.put("Sun", new MatAcc("Sunnarium", "Sun", "dust"));
        map.put("He", new MatAcc("Helium", "He", "L"));
        map.put("D", new MatAcc("Deuterium", "D", "L"));
        map.put("T", new MatAcc("Tritium", "T", "L"));

        // 1. Solid Fuel Rods (Exact GTNH Recipes from FissionFuelLoader.java & RecipeLoader.java)
        List<SolidFuelByproduct> solid = getSolidFuelByproducts();
        for (SolidFuelByproduct s : solid) {
            double rate = s.itemsPerMinute;
            if (rate <= 0.0) continue;
            switch (s.type) {
                case FUEL_URANIUM_SINGLE -> {
                    // FissionFuelLoader: 6x U-238 + 3x Small U-235 (1/3 dust) -> Depleted: 4x U-238 + 1x Small Pu (1/9
                    // dust)
                    map.get("U-235").consumedPerMin += (3.0 / 9.0) * rate;
                    map.get("U-238").consumedPerMin += 6.0 * rate;
                    map.get("Pu-239").producedPerMin += (1.0 / 9.0) * rate;
                    map.get("U-238").producedPerMin += 4.0 * rate;
                }
                case FUEL_URANIUM_DUAL -> {
                    map.get("U-235").consumedPerMin += (6.0 / 9.0) * rate;
                    map.get("U-238").consumedPerMin += 12.0 * rate;
                    map.get("Pu-239").producedPerMin += (2.0 / 9.0) * rate;
                    map.get("U-238").producedPerMin += 8.0 * rate;
                }
                case FUEL_URANIUM_QUAD -> {
                    map.get("U-235").consumedPerMin += (12.0 / 9.0) * rate;
                    map.get("U-238").consumedPerMin += 24.0 * rate;
                    map.get("Pu-239").producedPerMin += (4.0 / 9.0) * rate;
                    map.get("U-238").producedPerMin += 16.0 * rate;
                }
                case FUEL_MOX_SINGLE -> {
                    // FissionFuelLoader: 6x U-238 + 3x Pu-239 -> Depleted: 3x Pu-239 + 1x Small Pu (1/9 dust)
                    map.get("Pu-239").consumedPerMin += 3.0 * rate;
                    map.get("U-238").consumedPerMin += 6.0 * rate;
                    map.get("Pu-239").producedPerMin += (3.0 + 1.0 / 9.0) * rate;
                }
                case FUEL_MOX_DUAL -> {
                    map.get("Pu-239").consumedPerMin += 6.0 * rate;
                    map.get("U-238").consumedPerMin += 12.0 * rate;
                    map.get("Pu-239").producedPerMin += (6.0 + 2.0 / 9.0) * rate;
                }
                case FUEL_MOX_QUAD -> {
                    map.get("Pu-239").consumedPerMin += 12.0 * rate;
                    map.get("U-238").consumedPerMin += 24.0 * rate;
                    map.get("Pu-239").producedPerMin += (12.0 + 4.0 / 9.0) * rate;
                }
                case FUEL_THORIUM_SINGLE -> {
                    // FissionFuelLoader: 3x Thorium -> Depleted: 1x Thorium + 2x Small Lutetium (2/9 dust)
                    map.get("Th-232").consumedPerMin += 3.0 * rate;
                    map.get("Th-232").producedPerMin += 1.0 * rate;
                    map.get("Lu").producedPerMin += (2.0 / 9.0) * rate;
                }
                case FUEL_THORIUM_DUAL -> {
                    map.get("Th-232").consumedPerMin += 6.0 * rate;
                    map.get("Th-232").producedPerMin += 2.0 * rate;
                    map.get("Lu").producedPerMin += 1.0 * rate;
                }
                case FUEL_THORIUM_QUAD -> {
                    map.get("Th-232").consumedPerMin += 12.0 * rate;
                    map.get("Th-232").producedPerMin += 4.0 * rate;
                    map.get("Lu").producedPerMin += 2.0 * rate;
                }
                case FUEL_HD_URANIUM -> {
                    // 4 HD nuggets = 16 Uranium dust -> Depleted: 8 Uranium, 2 Plutonium, 0.5 U-235, 0.3 Pu-241
                    map.get("U-238").consumedPerMin += 16.0 * rate;
                    map.get("U-238").producedPerMin += 8.0 * rate;
                    map.get("Pu-239").producedPerMin += 2.0 * rate;
                    map.get("U-235").producedPerMin += 0.5 * rate;
                    map.get("Pu-241").producedPerMin += 0.3 * rate;
                }
                case FUEL_HD_PLUTONIUM -> {
                    // 4 HD nuggets = 20 Plutonium dust + 4 Uranium dust -> Depleted: 16 Plutonium, 8 Pu-241, 2 Uranium,
                    // 1.2 U-235
                    map.get("Pu-239").consumedPerMin += 20.0 * rate;
                    map.get("U-238").consumedPerMin += 4.0 * rate;
                    map.get("Pu-239").producedPerMin += 16.0 * rate;
                    map.get("Pu-241").producedPerMin += 8.0 * rate;
                    map.get("U-238").producedPerMin += 2.0 * rate;
                    map.get("U-235").producedPerMin += 1.2 * rate;
                }
                case FUEL_EXCITED_URANIUM -> {
                    // 1000 L = 36 Uranium dust
                    map.get("U-238").consumedPerMin += 36.0 * rate;
                }
                case FUEL_EXCITED_PLUTONIUM -> {
                    // 1000 L = 45 Plutonium dust + 9 Uranium dust
                    map.get("Pu-239").consumedPerMin += 45.0 * rate;
                    map.get("U-238").consumedPerMin += 9.0 * rate;
                }
                case FUEL_GLOWSTONE -> {
                    // FissionFuelLoader: 9x Glowstone + 250L Helium -> Depleted: 2x Glowstone + 1x Sunnarium
                    map.get("G").consumedPerMin += 9.0 * rate;
                    map.get("He").consumedPerMin += 250.0 * rate;
                    map.get("G").producedPerMin += 2.0 * rate;
                    map.get("Sun").producedPerMin += 1.0 * rate;
                }
                case FUEL_LITHIUM -> {
                    // FissionFuelLoader: 1x Tiny Lithium (1/9 dust) -> Depleted: 32L Tritium gas
                    map.get("Li").consumedPerMin += (1.0 / 9.0) * rate;
                    map.get("T").producedPerMin += 32.0 * rate;
                }
                case FUEL_NAQUADAH -> {
                    // FissionFuelLoader: 3x Enriched Naquadah -> Depleted: 1.5 Naquadah, 0.111 Naquadria, 0.0555
                    // Enriched Naquadah
                    map.get("NQ+").consumedPerMin += 3.0 * rate;
                    map.get("NQ").producedPerMin += 1.5 * rate;
                    map.get("NQR").producedPerMin += (1.0 / 9.0) * rate;
                    map.get("NQ+").producedPerMin += (0.5 / 9.0) * rate;
                }
                case FUEL_NAQUADRIA -> {
                    // FissionFuelLoader: 12x Naquadria (Quad) -> Depleted: 6x Naquadah, 2x Enriched Naquadah, 0.222
                    // Naquadria
                    map.get("NQR").consumedPerMin += 12.0 * rate;
                    map.get("NQ").producedPerMin += 6.0 * rate;
                    map.get("NQ+").producedPerMin += 2.0 * rate;
                    map.get("NQR").producedPerMin += (2.0 / 9.0) * rate;
                }
                case FUEL_TIBERIUM -> {
                    // FissionFuelLoader: 12x Tiberium (Quad) -> Depleted: 2x Tiberium
                    map.get("TIB").consumedPerMin += 12.0 * rate;
                    map.get("TIB").producedPerMin += 2.0 * rate;
                }
                case FUEL_CORE -> {
                    // FissionFuelLoader: 32x RodNaquadah (96 Enriched Naquadah) + 128 Tiberium -> Depleted: 8x Quad
                    // Naquadah rods
                    // Reprocessing 8 Quads: 48 Naquadah, 8 Naquadria, 1.778 Enriched Naquadah
                    map.get("NQ+").consumedPerMin += 96.0 * rate;
                    map.get("TIB").consumedPerMin += 128.0 * rate;
                    map.get("NQ").producedPerMin += 48.0 * rate;
                    map.get("NQR").producedPerMin += 8.0 * rate;
                    map.get("NQ+").producedPerMin += (16.0 / 9.0) * rate;
                }
                default -> {}
            }
        }

        // 2. Liquid Fuels (Rates per 1000 L)
        List<LiquidFuelByproduct> liquid = getLiquidFuelByproducts();
        for (LiquidFuelByproduct l : liquid) {
            double kLRate = l.litersPerMinute / 1000.0;
            if (kLRate <= 0.0) continue;
            switch (l.type) {
                case HATCH_LIQUID_FUEL_URANIUM -> {
                    map.get("U-238").consumedPerMin += 36.0 * kLRate;
                }
                case HATCH_LIQUID_FUEL_THORIUM -> {
                    map.get("Th-232").consumedPerMin += 99.0 * kLRate;
                    map.get("Th-232").producedPerMin += 76.8 * kLRate;
                }
                case HATCH_LIQUID_FUEL_PLUTONIUM -> {
                    map.get("Pu-239").consumedPerMin += 45.0 * kLRate;
                    map.get("U-238").consumedPerMin += 9.0 * kLRate;
                }
                default -> {}
            }
        }

        // 3. Coolant Transmutation Isotopes
        List<IsotopeByproduct> isotopes = getIsotopeByproducts();
        for (IsotopeByproduct iso : isotopes) {
            if ("Deuterium".equals(iso.name) || "D".equals(iso.code)) {
                map.get("D").producedPerMin += iso.litersPerMinute;
            } else if ("Tritium".equals(iso.name) || "T".equals(iso.code)) {
                map.get("T").producedPerMin += iso.litersPerMinute;
            }
        }

        List<RawMaterialBalance> result = new ArrayList<>();
        for (MatAcc acc : map.values()) {
            if (acc.consumedPerMin > 1e-6 || acc.producedPerMin > 1e-6) {
                result.add(
                    new RawMaterialBalance(
                        acc.name,
                        acc.code,
                        acc.unit,
                        acc.consumedPerMin,
                        acc.consumedPerMin * 60.0,
                        acc.producedPerMin,
                        acc.producedPerMin * 60.0));
            }
        }
        return result;
    }

    public void setControlRod(int x, int y, boolean hasRod, SimTile.ControlRodType type, int insertion) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            SimTile tile = grid[x][y];
            if (tile != null) {
                tile.setHasControlRod(hasRod);
                tile.setControlRodType(type != null ? type : SimTile.ControlRodType.NONE);
                tile.setControlRodInsertion(Math.max(0, Math.min(100, insertion)));
            }
        }
    }

    public void setAllControlRodsInsertion(int insertion) {
        int ins = Math.max(0, Math.min(100, insertion));
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SimTile tile = grid[x][y];
                if (tile != null && tile.hasControlRod()) {
                    tile.setControlRodInsertion(ins);
                }
            }
        }
    }

    public void scram() {
        setAllControlRodsInsertion(100);
        logIncident("SCRAM", "Emergency SCRAM: All control rods fully inserted (100%)", 0.0);
    }

    public int getControlRodCount() {
        int count = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SimTile tile = grid[x][y];
                if (tile != null && tile.hasControlRod()) {
                    count++;
                }
            }
        }
        return count;
    }
}
