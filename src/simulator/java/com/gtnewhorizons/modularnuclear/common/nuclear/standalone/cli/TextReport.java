package com.gtnewhorizons.modularnuclear.common.nuclear.standalone.cli;

import java.util.List;

import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.CoolantLoopModel;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.TurbineCalculator;

/** Runs a simulation and prints a human-readable report with a progress table and final telemetry. */
public final class TextReport {

    private TextReport() {}

    public static void run(RunConfig cfg) {
        System.out
            .println(CliFormat.CYAN + "============================================================" + CliFormat.RESET);
        System.out
            .println(CliFormat.WHITE_BOLD + "   GTNH MODULAR PRESSURE TUBE REACTOR (MPTR) SIMULATOR" + CliFormat.RESET);
        System.out
            .println(CliFormat.CYAN + "============================================================" + CliFormat.RESET);

        StandaloneNuclearGrid grid = cfg.buildGrid(true);
        String preset = cfg.preset;
        int ticks = cfg.ticks;
        boolean strictMode = cfg.strict;

        if (cfg.hasLayout()) {
            System.out.println("Layout:        " + CliFormat.GREEN + grid.toLayoutString() + CliFormat.RESET);
        } else {
            System.out.println("Preset:        " + CliFormat.GREEN + preset + CliFormat.RESET);
        }
        System.out.println("Grid Size:     " + grid.getWidth() + "x" + grid.getHeight());
        System.out.println("Casing Tier:   " + NuclearSimulationEngine.getPipeTierName(grid.getPipeTier()));
        System.out.println(
            "Max Safe Temp: " + NuclearSimulationEngine.getMaxOperatingTemperature(grid.getPipeTier()) + " °C");
        System.out.println(
            "Conductance:   "
                + String.format(java.util.Locale.US, "%.1f EU/(t·°C) [Base]", grid.getBaseHatchConductance()));
        System.out.println(
            "Strict Mode:   " + (strictMode ? CliFormat.YELLOW + "Enabled (fail-fast on damage/void)" + CliFormat.RESET
                : CliFormat.GREEN + "Disabled (permissive)" + CliFormat.RESET));
        System.out.println(
            "Stop On Incidents: " + (grid.isStopOnIncidents()
                ? CliFormat.YELLOW + "Enabled (pause on damage/thermal shock)" + CliFormat.RESET
                : CliFormat.GREEN + "Disabled" + CliFormat.RESET));
        System.out.println(
            "Auto-Supply Fuel:  "
                + (grid.isAutoSupplyFuel() ? CliFormat.GREEN + "Enabled (continuous rod/fluid cycle)" + CliFormat.RESET
                    : CliFormat.YELLOW + "Disabled (single batch)" + CliFormat.RESET));
        if (grid.getReactorDamage() > 0.0 || grid.getMaintenanceIssues() > 0) {
            System.out.println(
                "Health/Maint:  " + CliFormat.YELLOW
                    + String.format(
                        java.util.Locale.US,
                        "Damage: %.1f%%, %d issues (efficiency: %.1f%%)",
                        grid.getReactorDamage(),
                        grid.getMaintenanceIssues(),
                        grid.getMaintenanceEfficiency() * 100.0)
                    + CliFormat.RESET);
        }
        TurbineCalculator.TurbineMaterial activeMat = grid.getTurbineMaterial();
        TurbineCalculator.TurbineSize activeSize = grid.getTurbineSize();
        TurbineCalculator.FittingMode activeFit = grid.getTurbineFitting();
        System.out.println(
            "Turbine Rotor: " + (activeMat != null ? activeMat.displayName : "None")
                + " ("
                + (activeSize != null ? activeSize.name() : "None")
                + ", "
                + (activeFit != null ? activeFit.name() : "None")
                + ")");
        System.out.println(
            "Cooling Mode:  " + (grid.getCoolingMode() == CoolantLoopModel.CoolingMode.CONVECTIVE_LOOP
                ? CliFormat.CYAN + "CONVECTIVE COOLANT LOOP"
                : CliFormat.YELLOW + "CONDUCTIVE (sub-boiling hatches)") + CliFormat.RESET);
        if (grid.getCoolingMode() == CoolantLoopModel.CoolingMode.CONVECTIVE_LOOP) {
            CoolantLoopModel cl = grid.getCoolantLoop();
            System.out.println(
                "  Loop Mat:    " + cl.getMaterial().displayName
                    + " (Max: "
                    + cl.getMaterial()
                        .maxPressureBar()
                    + " bar, "
                    + cl.getMaterial()
                        .maxTemperatureCelsius()
                    + " °C)");
            System.out.println(
                "  Pipe Size:   " + cl.getPipeSize().displayName
                    + " ("
                    + (int) (cl.getPipeSize()
                        .diameterMeters() * 1000)
                    + " mm ID)");
            System.out.println("  Loop Fluid:  " + cl.getFluidType().displayName);
            System.out.println(
                "  Pump:        " + String.format(
                    java.util.Locale.US,
                    "%d x %s hatch, %.0f EU/t, %s %s rotor (%.0f%%), %d PHE(s)",
                    cl.getPumpHatchCount(),
                    cl.getHatchTier().displayName,
                    cl.pumpPowerEUt(),
                    cl.getRotorSize().displayName,
                    cl.getRotorMaterial().displayName,
                    cl.getRotorEfficiency() * 100.0,
                    cl.getPheCount()));
            if (!cl.getAttachedPoints()
                .isEmpty()) {
                System.out.println(
                    "  Attached:    " + cl.getAttachedPoints()
                        .size() + " core cell(s)");
            }
        }
        System.out.println("Ticks to Sim:  " + ticks);
        System.out.println();

        System.out.println(CliFormat.WHITE_BOLD + "Initial Grid Layout:" + CliFormat.RESET);
        System.out.print(grid.toAscii());
        System.out.println();

        long startTime = System.currentTimeMillis();
        int reportedInterval = Math.max(1, ticks / 10);

        System.out.println(
            String.format(
                "%-8s | %-12s | %-12s | %-10s | %-10s | %-14s",
                "Tick",
                "Max Temp (°C)",
                "Avg Temp (°C)",
                "Efficiency",
                "Neutrons",
                "Power (EU/t)"));
        System.out.println("------------------------------------------------------------------------------");

        for (int t = 1; t <= ticks; t++) {
            boolean ok = grid.step();
            if (!ok) {
                System.out.println();
                if (grid.isExploded()) {
                    System.out
                        .println(CliFormat.RED + ">>> EXPLOSION OCCURRED AT TICK " + t + " <<<" + CliFormat.RESET);
                    System.out.println(CliFormat.RED + "Cause: " + grid.getExplosionReason() + CliFormat.RESET);
                } else if (grid.isHaltedByIncident()) {
                    System.out.println(
                        CliFormat.YELLOW + ">>> SIMULATION PAUSED DUE TO INCIDENT AT TICK "
                            + t
                            + " <<<"
                            + CliFormat.RESET);
                    System.out
                        .println(CliFormat.YELLOW + "Reason: " + grid.getLastHaltIncidentReason() + CliFormat.RESET);
                } else if (grid.isPowerFailed()) {
                    System.out.println(
                        CliFormat.YELLOW + ">>> STRICT CALIBRATION DISQUALIFICATION AT TICK "
                            + t
                            + " <<<"
                            + CliFormat.RESET);
                    System.out.println(CliFormat.YELLOW + "Cause: " + grid.getPowerFailReason() + CliFormat.RESET);
                }
                break;
            }

            if (t % reportedInterval == 0 || t == ticks) {
                TurbineCalculator.PowerEstimationResult pr = grid.getLastPowerResult();
                double powerEUt = pr != null ? pr.totalPowerEUt : 0.0;
                String tierName = CliFormat.voltageTierName(powerEUt);
                double grossPower = grid.getGrossPowerEUt();
                double pumpPower = grid.getPumpPowerEUt();
                String powerStr = (pumpPower > 0)
                    ? String.format(java.util.Locale.US, "%.0f [Net, Gross: %.0f] (%s)", powerEUt, grossPower, tierName)
                    : String.format(java.util.Locale.US, "%.0f (%s)", powerEUt, tierName);
                System.out.println(
                    String.format(
                        "%-8d | %-12.1f | %-12.1f | %-10.2f | %-10d | %-14s",
                        t,
                        grid.getCoreMaxTemp(),
                        grid.getCoreAvgTemp(),
                        grid.getEfficiency(),
                        grid.getLastNeutronsProduced(),
                        powerStr));
            }
        }

        long elapsedMs = System.currentTimeMillis() - startTime;
        System.out.println("------------------------------------------------------------------------------");
        System.out.println(CliFormat.GREEN + "Simulation complete in " + elapsedMs + " ms." + CliFormat.RESET);
        System.out.println();

        if (grid.isNegativeTempDetected() || grid.isDiagnosticTraceEnabled()) {
            List<StandaloneNuclearGrid.IntermediateStepSnapshot> trace = grid.getStepTrace();
            if (!trace.isEmpty()) {
                System.out.println(
                    CliFormat.YELLOW + "============================================================"
                        + CliFormat.RESET);
                System.out.println(
                    CliFormat.YELLOW + "   DIAGNOSTIC INTERMEDIATE STEP TRACE ("
                        + trace.size()
                        + " steps in memory)"
                        + CliFormat.RESET);
                System.out.println(
                    CliFormat.YELLOW + "============================================================"
                        + CliFormat.RESET);
                for (StandaloneNuclearGrid.IntermediateStepSnapshot snap : trace) {
                    System.out.println(snap.toString());
                }
                System.out.println(
                    CliFormat.YELLOW + "============================================================"
                        + CliFormat.RESET);
                System.out.println();
            }
        }

        printFinalReport(grid);
    }

    private static void printFinalReport(StandaloneNuclearGrid grid) {
        System.out.println(CliFormat.CYAN + "=== FINAL SIMULATION TELEMETRY ===" + CliFormat.RESET);
        System.out.println(
            "Status:                 " + (grid.isExploded() ? CliFormat.RED + "DESTROYED (Meltdown)"
                : CliFormat.GREEN + "STABLE / OPERATIONAL") + CliFormat.RESET);
        System.out.println("Peak Core Temperature:  " + String.format("%.2f", grid.getCoreMaxTemp()) + " °C");
        System.out.println("Peak Lifetime Temp:     " + String.format("%.2f", grid.getPeakLifetimeTemp()) + " °C");
        if (grid.hasFuelBurned()) {
            System.out.println(
                CliFormat.RED + "WARNING:                "
                    + grid.getBurnedFuelCount()
                    + " fuel rod(s) burned up due to excessive heat!"
                    + CliFormat.RESET);
        }
        if (grid.getVoidedHatchCount() > 0) {
            System.out.println(
                CliFormat.RED + "WARNING:                "
                    + grid.getVoidedHatchCount()
                    + " hatch(es) voided due to over-temperature!"
                    + CliFormat.RESET);
        }
        System.out.println("Average Core Temp:      " + String.format("%.2f", grid.getCoreAvgTemp()) + " °C");
        System.out.println(
            "Casing Max Allowed:     " + NuclearSimulationEngine.getMaxOperatingTemperature(grid.getPipeTier())
                + " °C");
        if (grid.getReactorDamage() > 0.0) {
            System.out.println(
                CliFormat.RED + "Reactor Damage:         "
                    + String.format(
                        java.util.Locale.US,
                        "%.1f %% (Issues: %d/6, Maint Efficiency: %.1f %%)",
                        grid.getReactorDamage(),
                        grid.getMaintenanceIssues(),
                        grid.getMaintenanceEfficiency() * 100.0)
                    + CliFormat.RESET);
        } else {
            System.out.println(
                "Maintenance Health:     " + String.format(
                    java.util.Locale.US,
                    "%.1f %% (%d issues)",
                    grid.getMaintenanceEfficiency() * 100.0,
                    grid.getMaintenanceIssues()));
        }
        System.out.println(
            "Base Conductance:       "
                + String.format(java.util.Locale.US, "%.1f EU/(t·°C)", grid.getBaseHatchConductance()));
        System.out.println("Reactivity Efficiency:  " + String.format("%.2f %%", grid.getEfficiency() * 100.0));
        System.out.println("Total Neutrons Emitted: " + grid.getTotalNeutronsGenerated());
        System.out.println("Total Steam Produced:   " + grid.getTotalSteamProduced() + " L");
        System.out.println("Total Deuterium Bred:   " + grid.getTotalDeuteriumProduced() + " L");
        System.out.println("Total Tritium Bred:     " + grid.getTotalTritiumProduced() + " L");
        System.out.println("Total Energy Equivalent:" + String.format("%.0f EU", grid.getTotalEnergyEU()));
        System.out.println();

        TurbineCalculator.PowerEstimationResult p = grid.getLastPowerResult();
        if (p != null) {
            String turbTitle = p.isLST ? "Turbine Power Generation (Large Steam Turbines at Optimum Flow):"
                : "Turbine Power Generation (XLST & Heat Exchanger at Optimum Flow):";
            System.out.println(CliFormat.WHITE_BOLD + turbTitle + CliFormat.RESET);
            System.out.println("  Rotor Material:       " + grid.getTurbineMaterial().displayName);
            System.out.println(
                "  Rotor Size / Fitting: " + grid.getTurbineSize()
                    .name()
                    + " ("
                    + grid.getTurbineSize().multiplier
                    + "x) / "
                    + grid.getTurbineFitting()
                        .name());
            System.out.println("  Rotor Efficiency:     " + String.format("%.2f %%", p.efficiency * 100.0));
            System.out.println("  Opt Flow / Turbine:   " + String.format("%.0f L/t", p.optFlowPerTurbine));
            System.out.println(
                "  Total Estimated Power:" + CliFormat.GREEN
                    + String.format(" %.1f EU/t (%s)", p.totalPowerEUt, CliFormat.voltageTierName(p.totalPowerEUt))
                    + CliFormat.RESET);
            if (p.machineLimitExceeded) {
                System.out.println(
                    CliFormat.YELLOW + "  [!] Machine limits exceeded! Voided: "
                        + String.format(
                            java.util.Locale.US,
                            "%.1f L/t coolant, %.1f L/t steam",
                            p.voidedCoolantFlow,
                            p.totalVoidedSteamFlow)
                        + CliFormat.RESET);
            }
            if (p.isLST && p.lstPowerEUt > 0) {
                System.out.println(
                    "    - LST Power:        "
                        + String.format("%.1f EU/t (%.2f turbines needed)", p.lstPowerEUt, p.lstTurbinesNeeded));
            }
            if (p.xlstScPowerEUt > 0) {
                System.out.println(
                    "    - XLST-SC Power:    "
                        + String.format("%.1f EU/t (%.2f turbines needed)", p.xlstScPowerEUt, p.xlstScTurbinesNeeded));
            }
            if (p.xlstHpPowerEUt > 0) {
                System.out.println(
                    "    - XLST-HP Power:    "
                        + String.format("%.1f EU/t (%.2f turbines needed)", p.xlstHpPowerEUt, p.xlstHpTurbinesNeeded));
            }
            if (!p.isLST && p.xlstPowerEUt > 0) {
                System.out.println(
                    "    - XLST Power:       "
                        + String.format("%.1f EU/t (%.2f turbines needed)", p.xlstPowerEUt, p.xlstTurbinesNeeded));
            }
            if (p.directPowerEUt > 0) {
                System.out.println(
                    "    - Radiovoltaic Direct EU: " + String
                        .format("%.1f EU/t (%s)", p.directPowerEUt, CliFormat.voltageTierName(p.directPowerEUt)));
            }
            if (!"None".equals(p.coolantMachine)) {
                System.out.println(
                    "  Coolant Exchanger:    " + p.coolantMachine
                        + " ["
                        + p.coolantMachineMode
                        + "]"
                        + " (produced: "
                        + String.format("%.1f L/t", p.coolantSteamProduced)
                        + " steam, needed: "
                        + String.format("%.0f", p.coolantMachineCount)
                        + " machines)");
            }
            System.out.println(
                "  Instant Steam Flows:  " + String.format(
                    "Reg: %.1f | SH: %.1f | SC: %.1f | HW: %.1f | HP HW: %.1f L/t",
                    grid.getFlowRegularSteam(),
                    grid.getFlowSuperheatedSteam(),
                    grid.getFlowSupercriticalSteam(),
                    grid.getFlowHeavyWaterSteam(),
                    grid.getFlowHPHeavyWaterSteam()));
            System.out.println();
        }

        TurbineCalculator.ScenarioHypotheticalResult sc = grid.getLastScenariosResult();
        if (sc != null) {
            System.out.println(CliFormat.CYAN + "=== HYPOTHETICAL OPERATIONAL SCENARIOS ===" + CliFormat.RESET);
            System.out.println(String.format("  Scenario A [tight / max efficiency]: %s", sc.tightConfig.description));
            System.out.println(
                String.format(
                    "    - Power Generation:    %.1f EU/t (%s)",
                    sc.tightResult.totalPowerEUt,
                    CliFormat.voltageTierName(sc.tightResult.totalPowerEUt)));
            System.out.println(
                String.format(
                    "    - Turbines Needed:     %.2f turbines (opt flow: %.0f L/t)",
                    sc.tightResult.totalTurbinesNeeded,
                    sc.tightResult.optFlowPerTurbine));
            if (!"None".equals(sc.tightResult.coolantMachine)) {
                System.out.println(
                    String.format(
                        "    - Coolant Processing:  %.0f x %s",
                        sc.tightResult.coolantMachineCount,
                        sc.tightResult.coolantMachine));
            }
            System.out.println(String.format("  Scenario B [loose / high throughput]: %s", sc.looseConfig.description));
            System.out.println(
                String.format(
                    "    - Power Generation:    %.1f EU/t (%s)",
                    sc.looseResult.totalPowerEUt,
                    CliFormat.voltageTierName(sc.looseResult.totalPowerEUt)));
            System.out.println(
                String.format(
                    "    - Turbines Needed:     %.2f turbines (opt flow: %.0f L/t)",
                    sc.looseResult.totalTurbinesNeeded,
                    sc.looseResult.optFlowPerTurbine));
            if (!"None".equals(sc.looseResult.coolantMachine)) {
                System.out.println(
                    String.format(
                        "    - Coolant Processing:  %.0f x %s",
                        sc.looseResult.coolantMachineCount,
                        sc.looseResult.coolantMachine));
            }
            System.out.println(
                String.format(
                    "  Infrastructure Comparison: Loose uses %.2fx fewer turbines (%.1f%% of Tight power output)",
                    sc.looseResult.totalTurbinesNeeded > 0
                        ? (sc.tightResult.totalTurbinesNeeded / sc.looseResult.totalTurbinesNeeded)
                        : 1.0,
                    sc.tightResult.totalPowerEUt > 0
                        ? (sc.looseResult.totalPowerEUt / sc.tightResult.totalPowerEUt * 100.0)
                        : 100.0));
            System.out.println();
        }

        if (grid.getCoolingMode() == CoolantLoopModel.CoolingMode.CONVECTIVE_LOOP) {
            CoolantLoopModel cl = grid.getCoolantLoop();
            System.out.println(CliFormat.CYAN + "=== CONVECTIVE COOLANT LOOP TELEMETRY ===" + CliFormat.RESET);
            System.out.println(
                "  Loop Status:          "
                    + (cl.isRuptured() ? CliFormat.RED + "RUPTURED (" + cl.getRuptureReason() + ")"
                        : CliFormat.GREEN + "INTACT / OPERATIONAL")
                    + CliFormat.RESET);
            System.out.println(
                "  Piping Material:      " + cl.getMaterial().displayName + " [" + cl.getMaterial().tierUnlocked + "]");
            System.out.println(
                "  Internal Diameter:    " + cl.getPipeSize().displayName
                    + " ("
                    + (int) (cl.getPipeSize()
                        .diameterMeters() * 1000)
                    + " mm)");
            System.out.println("  Working Fluid:        " + cl.getFluidType().displayName);
            System.out.println(
                "  Operating Flow Rate:  "
                    + String.format(java.util.Locale.US, "%.2f L/s", cl.getCurrentFlowRateLPerSec()));
            double pressRatio = cl.getCurrentPressureBar() / cl.getMaterial()
                .maxPressureBar() * 100.0;
            String pressColor = pressRatio > 85.0 ? CliFormat.RED
                : pressRatio > 60.0 ? CliFormat.YELLOW : CliFormat.GREEN;
            System.out.println(
                "  Peak Loop Pressure:   " + pressColor
                    + String.format(
                        java.util.Locale.US,
                        "%.1f bar / %.1f bar max (%.1f %%)",
                        cl.getCurrentPressureBar(),
                        cl.getMaterial()
                            .maxPressureBar(),
                        pressRatio)
                    + CliFormat.RESET);
            double tempRatio = cl.getCurrentCoolantTempCelsius() / cl.getMaterial()
                .maxTemperatureCelsius() * 100.0;
            String tempColor = tempRatio > 85.0 ? CliFormat.RED : tempRatio > 60.0 ? CliFormat.YELLOW : CliFormat.GREEN;
            System.out.println(
                "  Coolant Temperature:  " + tempColor
                    + String.format(
                        java.util.Locale.US,
                        "%.1f °C / %.1f °C max (%.1f %%)",
                        cl.getCurrentCoolantTempCelsius(),
                        cl.getMaterial()
                            .maxTemperatureCelsius(),
                        tempRatio)
                    + CliFormat.RESET);
            System.out.println(
                "  Heat Extracted:       " + String.format(
                    java.util.Locale.US,
                    "%.1f HU/t (%.2f MWth)",
                    cl.getLastHeatExtractedHut(),
                    cl.getLastHeatExtractedWatts() / 1e6));
            System.out.println(
                "  Cold Leg Temperature: " + String.format(java.util.Locale.US, "%.1f °C", cl.getColdLegTempCelsius()));
            System.out.println(
                "  Secondary PHE Steam:  " + String.format(
                    java.util.Locale.US,
                    "%.1f L/t (steam %.1f, superheated %.1f, supercritical %.1f; %.0f L total)",
                    cl.getLastSecondarySteamProducedLt(),
                    cl.getLastSteamLt(),
                    cl.getLastSuperheatedSteamLt(),
                    cl.getLastSupercriticalSteamLt(),
                    cl.getTotalSecondarySteamProduced()));
            System.out.println(
                "  Parasitic Loop Draw:  " + CliFormat.YELLOW
                    + String.format(
                        java.util.Locale.US,
                        "%.1f EU/t pump + %.1f EU/t PHE (Net: %.1f EU/t, Gross: %.1f EU/t)",
                        cl.getLastPumpPowerEUt(),
                        cl.getLastPhePowerEUt(),
                        grid.getLastPowerResult() != null ? grid.getLastPowerResult().totalPowerEUt : 0.0,
                        grid.getGrossPowerEUt())
                    + CliFormat.RESET);
            if (cl.getFluidType() == CoolantLoopModel.CoolantFluidType.HEAVY_WATER) {
                System.out.println("  Tritium Radiolytic:   " + cl.getTotalTritiumProduced() + " L bred");
            } else if (cl.getFluidType() == CoolantLoopModel.CoolantFluidType.DISTILLED_WATER) {
                System.out.println("  Deuterium Radiolytic: " + cl.getTotalDeuteriumProduced() + " L bred");
            }
            if (!cl.getAttachedPoints()
                .isEmpty()) {
                System.out.println(
                    "  Attached Core Cells:  " + cl.getAttachedPoints()
                        .size() + " points " + cl.getAttachedPoints());
            }
            System.out.println();
        }

        List<StandaloneNuclearGrid.SolidFuelByproduct> solidByproducts = grid.getSolidFuelByproducts();
        List<StandaloneNuclearGrid.LiquidFuelByproduct> liquidByproducts = grid.getLiquidFuelByproducts();
        List<StandaloneNuclearGrid.IsotopeByproduct> isotopeByproducts = grid.getIsotopeByproducts();
        if (!solidByproducts.isEmpty() || !liquidByproducts.isEmpty()
            || (grid.getTotalDeuteriumProduced() > 0 || grid.getTotalTritiumProduced() > 0)) {
            System.out.println(CliFormat.GREEN + "=== FUEL BYPRODUCTS & LOGISTICS ===" + CliFormat.RESET);
            System.out.println(
                "  Auto-Refuel Mode:     "
                    + (grid.isAutoReplaceFuel() ? CliFormat.GREEN + "ENABLED (continuous cycling)"
                        : CliFormat.YELLOW + "DISABLED (single batch)")
                    + CliFormat.RESET);
            if (!solidByproducts.isEmpty()) {
                System.out
                    .println(CliFormat.WHITE_BOLD + "  Solid fuel rods (depleted item output):" + CliFormat.RESET);
                for (StandaloneNuclearGrid.SolidFuelByproduct s : solidByproducts) {
                    String lifespanStr = Double.isInfinite(s.avgLifespanMinutes) ? "Infinite"
                        : String.format(java.util.Locale.US, "%.1f min", s.avgLifespanMinutes);
                    System.out.println(
                        String.format(
                            java.util.Locale.US,
                            "    • [%-4s] %-28s : %6.2f /min | %7.1f /h  (Active: %2d, Lifespan: %s, Total: %d)",
                            s.depletedCode,
                            s.depletedName,
                            s.itemsPerMinute,
                            s.itemsPerHour,
                            s.activeRods,
                            lifespanStr,
                            s.totalProduced));
                }
            }
            if (!liquidByproducts.isEmpty()) {
                System.out
                    .println(CliFormat.WHITE_BOLD + "  Liquid fuel hatches (depleted fluid output):" + CliFormat.RESET);
                for (StandaloneNuclearGrid.LiquidFuelByproduct l : liquidByproducts) {
                    System.out.println(
                        String.format(
                            java.util.Locale.US,
                            "    • %-32s : %8.1f L/min | %10.0f L/h  (Hatches: %2d, Total: %d L)",
                            l.displayName,
                            l.litersPerMinute,
                            l.litersPerHour,
                            l.activeHatches,
                            l.totalLiters));
                }
            }
            if (!isotopeByproducts.isEmpty()
                && (grid.getTotalDeuteriumProduced() > 0 || grid.getTotalTritiumProduced() > 0)) {
                System.out.println(CliFormat.WHITE_BOLD + "  Transmuted isotopes (breeding output):" + CliFormat.RESET);
                for (StandaloneNuclearGrid.IsotopeByproduct iso : isotopeByproducts) {
                    System.out.println(
                        String.format(
                            java.util.Locale.US,
                            "    • %-10s (%-2s)                  : %8.2f L/min | %10.1f L/h  (Total: %d L)",
                            iso.name,
                            iso.code,
                            iso.litersPerMinute,
                            iso.litersPerHour,
                            iso.totalLiters));
                }
            }
            List<StandaloneNuclearGrid.RawMaterialBalance> rawBalances = grid.getRawMaterialBalances();
            if (!rawBalances.isEmpty()) {
                System.out.println(
                    CliFormat.CYAN + "  Base Raw Material Logistics & Net Balance Forecast:" + CliFormat.RESET);
                for (StandaloneNuclearGrid.RawMaterialBalance r : rawBalances) {
                    String netColor = r.netPerMinute > 0.001 ? CliFormat.GREEN
                        : (r.netPerMinute < -0.001 ? CliFormat.RED : CliFormat.RESET);
                    String tag = r.netPerMinute > 0.001 ? "[SURPLUS / BREEDING]"
                        : (r.netPerMinute < -0.001 ? "[NET DEFICIT / REQ]" : "[NEUTRAL]");
                    String netSign = r.netPerMinute > 0.001 ? "+" : "";
                    System.out.println(
                        String.format(
                            java.util.Locale.US,
                            "    • %-6s (%-16s) : %s%s%6.2f %s/min | %s%7.1f %s/h%s  (Req: %5.2f, Prod: %5.2f) %s",
                            r.code,
                            r.name,
                            netColor,
                            netSign,
                            r.netPerMinute,
                            r.unit,
                            netSign,
                            r.netPerHour,
                            r.unit,
                            CliFormat.RESET,
                            r.consumedPerMinute,
                            r.producedPerMinute,
                            tag));
                }
            }
            System.out.println();
        }

        System.out.println(CliFormat.WHITE_BOLD + "Core Heat Map (°C):" + CliFormat.RESET);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                double temp = grid.getTile(x, y)
                    .getTemperature();
                String color = (temp > 1500) ? CliFormat.PURPLE
                    : (temp > 800) ? CliFormat.RED : (temp > 300) ? CliFormat.YELLOW : CliFormat.GREEN;
                System.out.print(color + String.format("%5.0f ", temp) + CliFormat.RESET);
            }
            System.out.println();
        }
        System.out.println();
    }
}
