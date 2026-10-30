package com.gtnewhorizons.modularnuclear.common.nuclear.standalone.cli;

import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.CoolantLoopModel;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.TurbineCalculator;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.TurbineCalculator.PowerEstimationResult;

/** Runs a simulation and prints its result as one line of JSON (the format the calibration tools read). */
public final class JsonReport {

    private JsonReport() {}

    /** What a run recorded besides the grid's own state. */
    private static final class Run {

        int ticksReached;
        boolean steadyStateReached;
        int steadyStateTick;
        double[] power, avgTemp, maxTemp, loopTemp;
        /** Energy and game tick at the half-way point, for the second-half (steady-state) average power. */
        double halfEnergy;
        long halfTick = -1;
    }

    public static void run(RunConfig cfg) {
        StandaloneNuclearGrid grid = cfg.buildGrid(false);
        Run run = simulate(grid, cfg);
        System.out.println(toJson(grid, cfg, run));
    }

    private static Run simulate(StandaloneNuclearGrid grid, RunConfig cfg) {
        Run run = new Run();
        int ticks = cfg.ticks;
        SteadyStateDetector detector = cfg.steadyState ? new SteadyStateDetector(cfg.steadyWindow) : null;
        if (cfg.trackHistory) {
            run.power = new double[ticks];
            run.avgTemp = new double[ticks];
            run.maxTemp = new double[ticks];
            run.loopTemp = new double[ticks];
        }
        for (int t = 1; t <= ticks; t++) {
            run.ticksReached = t;
            if (!grid.step()) break;
            if (t == (ticks + 1) / 2) {
                run.halfEnergy = grid.getTotalEnergyEU();
                run.halfTick = grid.getCurrentTick();
            }
            if (detector == null && !cfg.trackHistory) continue;

            PowerEstimationResult pr = grid.getLastPowerResult();
            double power = pr != null ? pr.totalPowerEUt : 0.0;
            double avgTemp = grid.getCoreAvgTemp();
            double loopTemp = grid.getCoolingMode() == CoolantLoopModel.CoolingMode.CONVECTIVE_LOOP
                ? grid.getCoolantLoop()
                    .getCurrentCoolantTempCelsius()
                : 0.0;
            if (cfg.trackHistory) {
                run.power[t - 1] = power;
                run.avgTemp[t - 1] = avgTemp;
                run.maxTemp[t - 1] = grid.getCoreMaxTemp();
                run.loopTemp[t - 1] = loopTemp;
            }
            if (detector != null) {
                detector.addSample(power, avgTemp, loopTemp);
                if (t >= cfg.steadyWindow && detector.isSteadyState()) {
                    run.steadyStateReached = true;
                    run.steadyStateTick = t;
                    break;
                }
            }
        }
        return run;
    }

    private static String toJson(StandaloneNuclearGrid grid, RunConfig cfg, Run run) {
        PowerEstimationResult p = grid.getLastPowerResult();
        double instantPower = p != null ? p.totalPowerEUt : 0.0;
        double avgPower = grid.getAveragePowerEUt();
        double instantDirectPower = p != null ? p.directPowerEUt : 0.0;
        double avgDirectPower = grid.getAverageDirectPowerEUt();
        double power = avgPower > 0.0 ? avgPower : instantPower;
        double directPower = avgDirectPower > 0.0 ? avgDirectPower : instantDirectPower;
        double tailPower = (run.halfTick >= 0 && grid.getCurrentTick() > run.halfTick)
            ? (grid.getTotalEnergyEU() - run.halfEnergy) / (double) (grid.getCurrentTick() - run.halfTick)
            : power;
        int tier = grid.getPipeTier();
        long tierVoltage = NuclearSimulationEngine.getPipeTierVoltage(tier);

        JsonWriter j = new JsonWriter().beginObject();
        j.field("preset", cfg.preset)
            .field("layout", grid.toLayoutString())
            .field("size", grid.getWidth())
            .field("tier", tier)
            .field("ticksSimulated", run.ticksReached)
            .field("ticksTarget", cfg.ticks)
            .field("steadyStateReached", run.steadyStateReached)
            .field("steadyStateTick", run.steadyStateTick);
        if (cfg.trackHistory) {
            j.fixedArray("powerHistory", run.power, run.ticksReached, 1)
                .fixedArray("tempHistory", run.avgTemp, run.ticksReached, 1)
                .fixedArray("maxTempHistory", run.maxTemp, run.ticksReached, 1)
                .fixedArray("loopTempHistory", run.loopTemp, run.ticksReached, 1);
        }
        j.array("structureErrors");
        for (com.gtnewhorizons.modularnuclear.common.nuclear.ReactorStructureRules.Violation v : grid
            .getStructureViolations()) j.value(v.message);
        j.endArray();
        j.field("exploded", grid.isExploded())
            .field("explosionReason", grid.getExplosionReason())
            .field("powerFailed", grid.isPowerFailed())
            .field("powerFailReason", grid.getPowerFailReason())
            .field("stopOnIncidents", grid.isStopOnIncidents())
            .field("haltedByIncident", grid.isHaltedByIncident())
            .field("lastHaltIncidentReason", grid.getLastHaltIncidentReason())
            .field("autoSupplyFuel", grid.isAutoSupplyFuel())
            .field("burnedFuelCount", grid.getBurnedFuelCount())
            .field("fuelBurned", grid.hasFuelBurned())
            .field("voidedHatchCount", grid.getVoidedHatchCount())
            .fixed("peakLifetimeTemp", grid.getPeakLifetimeTemp(), 2)
            .fixed("coreMaxTemp", grid.getCoreMaxTemp(), 2)
            .fixed("coreAvgTemp", grid.getCoreAvgTemp(), 2)
            .fixed("efficiency", grid.getEfficiency(), 4)
            .fixed("baseHatchConductance", grid.getBaseHatchConductance(), 1)
            .fixed("reactorDamage", grid.getReactorDamage(), 1)
            .field("maintenanceIssues", grid.getMaintenanceIssues())
            .fixed("maintenanceEfficiency", grid.getMaintenanceEfficiency(), 4)
            .field("strictMode", grid.isStrictMode())
            .field("totalNeutrons", grid.getTotalNeutronsGenerated())
            .field("totalSteam", grid.getTotalSteamProduced())
            .fixed("flowHotCoolant", grid.getFlowHotCoolant(), 3)
            .field("totalHotCoolant", grid.getTotalHotCoolantProduced())
            .field("totalDeuterium", grid.getTotalDeuteriumProduced())
            .field("totalTritium", grid.getTotalTritiumProduced())
            .field("totalOxygen", grid.getTotalOxygenProduced())
            .fixed("totalEnergyEU", grid.getTotalEnergyEU(), 1)
            .fixed("avgPowerEUt", avgPower, 1)
            .fixed("instantPowerEUt", instantPower, 1)
            .fixed("avgDirectPowerEUt", avgDirectPower, 1)
            .fixed("instantDirectPowerEUt", instantDirectPower, 1)
            .fixed("powerEUt", power, 1)
            .fixed("tailPowerEUt", tailPower, 1)
            .fixed("directPowerEUt", directPower, 1);
        turbineCounts(j, p);
        j.field("coolantMachine", p != null ? p.coolantMachine : "None")
            .field("coolantMachineMode", p != null ? p.coolantMachineMode : "Inactive")
            .field("voltageTier", CliFormat.voltageTierName(power))
            .field("activeFuelRods", grid.getActiveFuelRodCount())
            .field("totalDurabilityLost", grid.getTotalDurabilityLost())
            .fixed("minLongevityMinutes", grid.getMinFuelRodLongevityMinutes(), 2)
            .fixed("avgLongevityMinutes", grid.getAvgFuelRodLongevityMinutes(), 2)
            .field("casingNominalTier", NuclearSimulationEngine.getPipeTierVoltageName(tier))
            .field("casingVoltage", tierVoltage)
            .fixed("targetPower60A", 60.0 * tierVoltage, 1)
            .fixed("actualAmps", power / (double) tierVoltage, 2)
            .field("fuelBurnupMultiplier", NuclearSimulationEngine.fuelBurnupMultiplier)
            .field("tempThresholdLow", NuclearSimulationEngine.tempThresholdLow)
            .field("tempThresholdHigh", NuclearSimulationEngine.tempThresholdHigh)
            .field("reactivityPower", NuclearSimulationEngine.reactivityPower)
            .field("thermalFissionMultiplier", NuclearSimulationEngine.thermalFissionMultiplier)
            .field("fissionHeatPerNeutron", NuclearSimulationEngine.fissionHeatPerNeutron)
            .field("hatchCoolantCapacity", NuclearSimulationEngine.hatchCoolantCapacity)
            .field("turnoverCurve", NuclearSimulationEngine.turnoverCurve.name())
            .field("turnoverDeltaTMax", NuclearSimulationEngine.turnoverDeltaTMax)
            .field("turnoverExponent", NuclearSimulationEngine.turnoverExponent)
            .field("coolantFeedRate", NuclearSimulationEngine.coolantFeedRate)
            .field("coolingHeatPerLiter", NuclearSimulationEngine.coolingHeatPerLiter)
            .field("ambientTemp", NuclearSimulationEngine.ambientTemp)
            .field("ic2CoolantHeatPerLiter", NuclearSimulationEngine.ic2CoolantHeatPerLiter);
        scenarios(j, grid, tier);
        j.field("machineLimitExceeded", p != null && p.machineLimitExceeded)
            .fixed("voidedCoolantFlow", p != null ? p.voidedCoolantFlow : 0.0, 1)
            .fixed("voidedSteamFlow", p != null ? p.totalVoidedSteamFlow : 0.0, 1)
            .fixed("voidedSupercriticalSteam", p != null ? p.voidedSupercriticalSteam : 0.0, 1)
            .fixed("voidedSuperheatedSteam", p != null ? p.voidedSuperheatedSteam : 0.0, 1)
            .fixed("voidedRegularSteam", p != null ? p.voidedRegularSteam : 0.0, 1)
            .field(
                "coolingMode",
                grid.getCoolingMode()
                    .name())
            .fixed("grossPowerEUt", grid.getGrossPowerEUt(), 1)
            .fixed("pumpPowerEUt", grid.getPumpPowerEUt(), 1);
        coolantLoop(j, grid);
        byproducts(j, grid);
        return j.endObject()
            .toString();
    }

    private static void turbineCounts(JsonWriter j, PowerEstimationResult p) {
        j.fixed("totalTurbinesNeeded", p != null ? p.totalTurbinesNeeded : 0.0, 2)
            .fixed("lstTurbinesNeeded", p != null ? p.lstTurbinesNeeded : 0.0, 2)
            .fixed("xlstTurbinesNeeded", p != null ? p.xlstTurbinesNeeded : 0.0, 2)
            .fixed("xlstHpTurbinesNeeded", p != null ? p.xlstHpTurbinesNeeded : 0.0, 2)
            .fixed("xlstScTurbinesNeeded", p != null ? p.xlstScTurbinesNeeded : 0.0, 2);
    }

    private static void scenarios(JsonWriter j, StandaloneNuclearGrid grid, int tier) {
        TurbineCalculator.ScenarioHypotheticalResult sc = grid.getLastScenariosResult();
        if (sc == null) {
            sc = TurbineCalculator.calculateBothScenarios(
                tier,
                grid.getFlowRegularSteam(),
                grid.getFlowSuperheatedSteam(),
                grid.getFlowSupercriticalSteam(),
                grid.getFlowHeavyWaterSteam(),
                grid.getFlowHPHeavyWaterSteam(),
                grid.getFlowHotCoolant(),
                grid.getFlowDirectEU(),
                null,
                null,
                grid.getMachineLimits());
        }
        j.object("scenarios");
        scenario(j.object("tight"), sc.tightConfig, sc.tightResult);
        scenario(j.object("loose"), sc.looseConfig, sc.looseResult);
        j.fixed("turbineReductionRatio", sc.turbineReductionRatio, 2)
            .fixed("powerReductionRatio", sc.powerReductionRatio, 2)
            .endObject();
    }

    private static void scenario(JsonWriter j, TurbineCalculator.ScenarioTurbineConfig config,
        PowerEstimationResult r) {
        j.field("material", config.material.displayName)
            .field("size", config.size.displayName)
            .field("mode", config.mode.name())
            .fixed("efficiency", config.efficiency, 2)
            .field("description", config.description)
            .fixed("powerEUt", r.totalPowerEUt, 1);
        turbineCounts(j, r);
        j.field("coolantMachine", r.coolantMachine)
            .fixed("coolantMachineCount", r.coolantMachineCount, 1)
            .field("machineLimitExceeded", r.machineLimitExceeded)
            .fixed("voidedCoolantFlow", r.voidedCoolantFlow, 1)
            .fixed("voidedSteamFlow", r.totalVoidedSteamFlow, 1)
            .endObject();
    }

    private static void coolantLoop(JsonWriter j, StandaloneNuclearGrid grid) {
        CoolantLoopModel cl = grid.getCoolantLoop();
        j.object("coolantLoop")
            .field("enabled", grid.getCoolingMode() == CoolantLoopModel.CoolingMode.CONVECTIVE_LOOP)
            .field(
                "material",
                cl.getMaterial()
                    .name())
            .field(
                "pipeSize",
                cl.getPipeSize()
                    .name())
            .field(
                "fluid",
                cl.getFluidType()
                    .name())
            .fixed("pumpPowerEUt", cl.getLastPumpPowerEUt(), 1)
            .fixed("circulationFlowLs", cl.getCurrentFlowRateLPerSec(), 2)
            .fixed("loopTemperature", cl.getCurrentCoolantTempCelsius(), 2)
            .fixed("peakPressureBar", cl.getCurrentPressureBar(), 2)
            .fixed(
                "maxSafePressureBar",
                cl.getMaterial()
                    .maxPressureBar(),
                2)
            .fixed(
                "maxSafeTemp",
                cl.getMaterial()
                    .maxTemperatureCelsius(),
                2)
            .field("ruptured", cl.isRuptured())
            .field("ruptureReason", cl.getRuptureReason())
            .fixed("convectiveHeatRemovedHut", cl.getLastHeatExtractedHut(), 2)
            .fixed("secondarySteamProduced", cl.getLastSecondarySteamProducedLt(), 2)
            .fixed("steamLt", cl.getLastSteamLt(), 2)
            .fixed("superheatedSteamLt", cl.getLastSuperheatedSteamLt(), 2)
            .fixed("supercriticalSteamLt", cl.getLastSupercriticalSteamLt(), 2)
            .fixed("coldLegTemperature", cl.getColdLegTempCelsius(), 2)
            .fixed("loopPowerEUt", cl.getLastLoopPowerEUt(), 1)
            .fixed("phePowerEUt", cl.getLastPhePowerEUt(), 1)
            .fixed("pumpMechanicalPowerW", cl.pumpMechanicalPowerWatts(), 1)
            .fixed("maxFlowLs", cl.maxFlowLitersPerSecond(), 1)
            .field("pheCount", cl.getPheCount())
            .array("circuits");
        for (CoolantLoopModel.Circuit c : cl.getCircuits()) {
            j.beginObject()
                .field(
                    "fluid",
                    c.getFluidType()
                        .name())
                .field("passages", c.getPassageCount())
                .field("copies", c.getCopies())
                .field("pheCount", c.getPheCount())
                .fixed("flowLs", c.getCurrentFlowRateLPerSec(), 1)
                .fixed("pressureBar", c.getCurrentPressureBar(), 2)
                .fixed("coldLeg", c.getColdLegTempCelsius(), 1)
                .fixed("hotLeg", c.getHotLegTempCelsius(), 1)
                .fixed("heatHut", c.getLastHeatExtractedHut(), 1)
                .fixed("steamLt", c.getLastSteamLt(), 1)
                .fixed("superheatedSteamLt", c.getLastSuperheatedSteamLt(), 1)
                .fixed("supercriticalSteamLt", c.getLastSupercriticalSteamLt(), 1)
                .fixed("pumpEUt", c.getLastPumpPowerEUt(), 1)
                .fixed("pheEUt", c.getLastPhePowerEUt(), 1)
                .fixed("pumpOn", c.getLastPumpOnFraction(), 3)
                .field("limit", c.getLimitReason())
                .field("filled", c.isFilled())
                .endObject();
        }
        j.endArray()
            .field("loopCount", cl.getLoopCount())
            .field("pumpHatches", cl.getPumpHatchCount())
            .field(
                "pumpHatchTier",
                cl.getHatchTier()
                    .name())
            .field(
                "pheHatchTier",
                cl.getPheHatchTier()
                    .name())
            .field(
                "rotor",
                cl.getRotorMaterial()
                    .name())
            .field("totalDeuteriumProduced", cl.getTotalDeuteriumProduced())
            .field("totalTritiumProduced", cl.getTotalTritiumProduced())
            .field("totalOxygenProduced", cl.getTotalOxygenProduced())
            .endObject();
    }

    /** Depleted fuel items, depleted liquid fuels, isotopes and raw material balances. */
    private static void byproducts(JsonWriter j, StandaloneNuclearGrid grid) {
        double solidPerMin = 0.0, solidPerHour = 0.0, liquidPerMin = 0.0, liquidPerHour = 0.0;
        long depletedItems = 0, depletedLiquid = 0;
        for (StandaloneNuclearGrid.SolidFuelByproduct s : grid.getSolidFuelByproducts()) {
            solidPerMin += s.itemsPerMinute;
            solidPerHour += s.itemsPerHour;
            depletedItems += s.totalProduced;
        }
        for (StandaloneNuclearGrid.LiquidFuelByproduct l : grid.getLiquidFuelByproducts()) {
            liquidPerMin += l.litersPerMinute;
            liquidPerHour += l.litersPerHour;
            depletedLiquid += l.totalLiters;
        }
        j.object("byproducts")
            .field("autoReplaceFuel", grid.isAutoReplaceFuel())
            .fixed("totalSolidItemsPerMin", solidPerMin, 2)
            .fixed("totalSolidItemsPerHour", solidPerHour, 1)
            .fixed("totalLiquidLitersPerMin", liquidPerMin, 1)
            .fixed("totalLiquidLitersPerHour", liquidPerHour, 0)
            .field("totalDepletedItemsProduced", depletedItems)
            .field("totalDepletedLiquidProduced", depletedLiquid);
        j.array("solidRods");
        for (StandaloneNuclearGrid.SolidFuelByproduct s : grid.getSolidFuelByproducts()) {
            j.beginObject()
                .field("type", s.type.name())
                .field("fuelName", s.fuelName)
                .field("fuelCode", s.fuelCode)
                .field("depletedName", s.depletedName)
                .field("depletedCode", s.depletedCode)
                .field("activeRods", s.activeRods)
                .fixed("itemsPerMin", s.itemsPerMinute, 2)
                .fixed("itemsPerHour", s.itemsPerHour, 1)
                .field("totalProduced", s.totalProduced)
                .fixed("avgLifespanMin", s.avgLifespanMinutes, 1)
                .endObject();
        }
        j.endArray()
            .array("liquidFuels");
        for (StandaloneNuclearGrid.LiquidFuelByproduct l : grid.getLiquidFuelByproducts()) {
            j.beginObject()
                .field("type", l.type.name())
                .field("fluidName", l.fluidName)
                .field("displayName", l.displayName)
                .field("activeHatches", l.activeHatches)
                .fixed("litersPerMin", l.litersPerMinute, 1)
                .fixed("litersPerHour", l.litersPerHour, 0)
                .field("totalLiters", l.totalLiters)
                .endObject();
        }
        j.endArray()
            .array("isotopes");
        for (StandaloneNuclearGrid.IsotopeByproduct iso : grid.getIsotopeByproducts()) {
            j.beginObject()
                .field("name", iso.name)
                .field("code", iso.code)
                .fixed("litersPerMin", iso.litersPerMinute, 2)
                .fixed("litersPerHour", iso.litersPerHour, 1)
                .field("totalLiters", iso.totalLiters)
                .endObject();
        }
        j.endArray()
            .array("rawMaterials");
        for (StandaloneNuclearGrid.RawMaterialBalance r : grid.getRawMaterialBalances()) {
            j.beginObject()
                .field("name", r.name)
                .field("code", r.code)
                .field("unit", r.unit)
                .fixed("consumedPerMin", r.consumedPerMinute, 2)
                .fixed("consumedPerHour", r.consumedPerHour, 1)
                .fixed("producedPerMin", r.producedPerMinute, 2)
                .fixed("producedPerHour", r.producedPerHour, 1)
                .fixed("netPerMin", r.netPerMinute, 2)
                .fixed("netPerHour", r.netPerHour, 1)
                .endObject();
        }
        j.endArray()
            .endObject();
    }
}
