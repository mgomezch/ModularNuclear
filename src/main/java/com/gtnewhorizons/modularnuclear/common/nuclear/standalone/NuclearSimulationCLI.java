package com.gtnewhorizons.modularnuclear.common.nuclear.standalone;

import java.util.List;

import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;

/**
 * Command-line interface tool for batch-testing and simulating GTNH nuclear reactor designs
 * outside the game context using the mod's native NuclearSimulationEngine.
 */
public class NuclearSimulationCLI {

    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_PURPLE = "\u001B[35m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_WHITE_BOLD = "\u001B[1;37m";

    public static void main(String[] args) {
        String preset = "BASIC_ELECTRUM_5X5";
        String layout = null;
        int size = 5;
        int ticks = 100;
        int tier = NuclearSimulationEngine.PIPE_TIER_ELECTRUM;
        boolean tierSpecified = false;
        boolean turbSpecified = false;
        boolean startWeb = false;
        int webPort = 8085;
        TurbineCalculator.TurbineMaterial turbMat = TurbineCalculator.TurbineMaterial.HSS_E;
        TurbineCalculator.TurbineSize turbSize = TurbineCalculator.TurbineSize.LARGE;
        TurbineCalculator.FittingMode turbFitting = TurbineCalculator.FittingMode.TIGHT;
        boolean jsonOutput = false;
        boolean batchMode = false;
        boolean traceEnabled = false;
        int traceSteps = 50;
        CoolantLoopModel.CoolingMode coolingMode = CoolantLoopModel.CoolingMode.CONDUCTIVE;
        boolean coolingModeSpecified = false;
        double baseConductance = 32.0;
        boolean baseConductanceSpecified = false;
        boolean repair = false;
        boolean autoReplaceFuel = true;
        boolean autoReplaceFuelSpecified = false;
        CoolantLoopModel.LoopMaterial loopMat = CoolantLoopModel.LoopMaterial.STEEL;
        boolean loopMatSpecified = false;
        CoolantLoopModel.LoopPipeSize loopSize = CoolantLoopModel.LoopPipeSize.NORMAL;
        boolean loopSizeSpecified = false;
        CoolantLoopModel.CoolantFluidType loopFluid = CoolantLoopModel.CoolantFluidType.DISTILLED_WATER;
        boolean loopFluidSpecified = false;
        double loopPumpPower = -1.0;
        double loopFlowRate = -1.0;
        String loopPoints = null;
        boolean strictMode = false;
        boolean strictModeSpecified = false;
        boolean stopOnIncidents = false;
        boolean stopOnIncidentsSpecified = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--batch" -> {
                    batchMode = true;
                }
                case "--web" -> {
                    startWeb = true;
                    if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        try {
                            webPort = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--layout" -> {
                    if (i + 1 < args.length) layout = args[++i];
                }
                case "--preset" -> {
                    if (i + 1 < args.length) preset = args[++i];
                }
                case "--size" -> {
                    if (i + 1 < args.length) {
                        try {
                            size = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--ticks" -> {
                    if (i + 1 < args.length) {
                        try {
                            ticks = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--tier" -> {
                    if (i + 1 < args.length) {
                        tierSpecified = true;
                        tier = parseTier(args[++i]);
                    }
                }
                case "--material", "--turb-mat" -> {
                    if (i + 1 < args.length) {
                        turbSpecified = true;
                        turbMat = TurbineCalculator.TurbineMaterial.fromString(args[++i]);
                    }
                }
                case "--turb-size" -> {
                    if (i + 1 < args.length) {
                        turbSpecified = true;
                        turbSize = TurbineCalculator.TurbineSize.fromString(args[++i]);
                    }
                }
                case "--fitting", "--turb-fit" -> {
                    if (i + 1 < args.length) {
                        turbSpecified = true;
                        turbFitting = TurbineCalculator.FittingMode.fromString(args[++i]);
                    }
                }
                case "--temp-low" -> {
                    if (i + 1 < args.length) {
                        try {
                            NuclearSimulationEngine.tempThresholdLow = Double.parseDouble(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--temp-high" -> {
                    if (i + 1 < args.length) {
                        try {
                            NuclearSimulationEngine.tempThresholdHigh = Double.parseDouble(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--reactivity-pow" -> {
                    if (i + 1 < args.length) {
                        try {
                            NuclearSimulationEngine.reactivityPower = Double.parseDouble(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--fission-mult" -> {
                    if (i + 1 < args.length) {
                        try {
                            NuclearSimulationEngine.thermalFissionMultiplier = Double.parseDouble(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--global-fission-mult" -> {
                    if (i + 1 < args.length) {
                        try {
                            NuclearSimulationEngine.globalThermalFissionMultiplier = Double.parseDouble(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--eu-per-degree" -> {
                    if (i + 1 < args.length) {
                        try {
                            NuclearSimulationEngine.setEuPerDegree(Double.parseDouble(args[++i]));
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--fission-heat" -> {
                    if (i + 1 < args.length) {
                        try {
                            NuclearSimulationEngine.fissionHeatPerNeutron = Double.parseDouble(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--hp-boil" -> {
                    if (i + 1 < args.length) {
                        try {
                            NuclearSimulationEngine.hpWaterBoilingPoint = Double.parseDouble(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--hatch-cap" -> {
                    if (i + 1 < args.length) {
                        try {
                            NuclearSimulationEngine.hatchCoolantCapacity = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--turnover-curve" -> {
                    if (i + 1 < args.length) {
                        NuclearSimulationEngine.turnoverCurve = NuclearSimulationEngine.TurnoverCurve
                            .fromString(args[++i]);
                    }
                }
                case "--turnover-dt" -> {
                    if (i + 1 < args.length) {
                        try {
                            NuclearSimulationEngine.turnoverDeltaTMax = Double.parseDouble(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--turnover-pow", "--turnover-exp" -> {
                    if (i + 1 < args.length) {
                        try {
                            NuclearSimulationEngine.turnoverExponent = Double.parseDouble(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--feed-rate" -> {
                    if (i + 1 < args.length) {
                        try {
                            NuclearSimulationEngine.coolantFeedRate = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--cooling-heat" -> {
                    if (i + 1 < args.length) {
                        try {
                            NuclearSimulationEngine.coolingHeatPerLiter = Double.parseDouble(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--ambient-temp", "--ambient" -> {
                    if (i + 1 < args.length) {
                        try {
                            NuclearSimulationEngine.setAmbientTemperature(Double.parseDouble(args[++i]));
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--ic2-heat", "--ic2-cooling-heat" -> {
                    if (i + 1 < args.length) {
                        try {
                            NuclearSimulationEngine.ic2CoolantHeatPerLiter = Double.parseDouble(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--burnup-mult" -> {
                    if (i + 1 < args.length) {
                        try {
                            NuclearSimulationEngine.fuelBurnupMultiplier = Double.parseDouble(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--trace" -> {
                    traceEnabled = true;
                    if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                        try {
                            traceSteps = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--base-conductance", "--conductance" -> {
                    if (i + 1 < args.length) {
                        try {
                            baseConductance = Double.parseDouble(args[++i]);
                            baseConductanceSpecified = true;
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--repair" -> {
                    repair = true;
                }
                case "--strict" -> {
                    strictMode = true;
                    strictModeSpecified = true;
                }
                case "--no-strict" -> {
                    strictMode = false;
                    strictModeSpecified = true;
                }
                case "--stop-on-incidents", "--stop-incidents" -> {
                    stopOnIncidents = true;
                    stopOnIncidentsSpecified = true;
                }
                case "--no-stop-on-incidents", "--no-stop-incidents" -> {
                    stopOnIncidents = false;
                    stopOnIncidentsSpecified = true;
                }
                case "--auto-supply-fuel", "--auto-refuel", "--auto-replace-fuel" -> {
                    autoReplaceFuel = true;
                    autoReplaceFuelSpecified = true;
                }
                case "--no-auto-supply-fuel", "--no-auto-refuel", "--no-auto-replace-fuel" -> {
                    autoReplaceFuel = false;
                    autoReplaceFuelSpecified = true;
                }
                case "--cooling-mode", "--cooling" -> {
                    if (i + 1 < args.length) {
                        coolingModeSpecified = true;
                        coolingMode = CoolantLoopModel.CoolingMode.fromString(args[++i]);
                    }
                }
                case "--loop-material", "--loop-mat" -> {
                    if (i + 1 < args.length) {
                        loopMatSpecified = true;
                        loopMat = CoolantLoopModel.LoopMaterial.fromString(args[++i]);
                    }
                }
                case "--loop-size" -> {
                    if (i + 1 < args.length) {
                        loopSizeSpecified = true;
                        loopSize = CoolantLoopModel.LoopPipeSize.fromString(args[++i]);
                    }
                }
                case "--loop-fluid" -> {
                    if (i + 1 < args.length) {
                        loopFluidSpecified = true;
                        loopFluid = CoolantLoopModel.CoolantFluidType.fromString(args[++i]);
                    }
                }
                case "--loop-power", "--loop-pump-power" -> {
                    if (i + 1 < args.length) {
                        try {
                            loopPumpPower = Double.parseDouble(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--loop-flow", "--loop-flow-rate" -> {
                    if (i + 1 < args.length) {
                        try {
                            loopFlowRate = Double.parseDouble(args[++i]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--loop-points" -> {
                    if (i + 1 < args.length) {
                        loopPoints = args[++i];
                    }
                }
                case "--json" -> {
                    jsonOutput = true;
                }
                case "--help", "-h" -> {
                    printHelp();
                    return;
                }
                default -> {}
            }
        }

        if (startWeb) {
            System.out.println("The Java server version of the simulator webapp has been retired.");
            System.out.println("The simulator is now a static WebAssembly application accessible at:");
            System.out.println("  https://nuclear.maderita.mgomez.ch");
            System.out.println("or locally at build/nuclear-sim-dist/index.html");
            return;
        }

        if (batchMode) {
            runBatchLoop();
            return;
        }

        if (jsonOutput) {
            runJsonSimulation(
                preset,
                layout,
                size,
                tier,
                ticks,
                turbMat,
                turbSize,
                turbFitting,
                tierSpecified,
                turbSpecified,
                coolingMode,
                coolingModeSpecified,
                loopMat,
                loopMatSpecified,
                loopSize,
                loopSizeSpecified,
                loopFluid,
                loopFluidSpecified,
                loopPumpPower,
                loopFlowRate,
                loopPoints,
                baseConductance,
                baseConductanceSpecified,
                repair,
                strictMode,
                autoReplaceFuel,
                autoReplaceFuelSpecified,
                stopOnIncidents,
                stopOnIncidentsSpecified);
        } else {
            runCliSimulation(
                preset,
                layout,
                size,
                tier,
                ticks,
                turbMat,
                turbSize,
                turbFitting,
                tierSpecified,
                turbSpecified,
                traceEnabled,
                traceSteps,
                coolingMode,
                coolingModeSpecified,
                loopMat,
                loopMatSpecified,
                loopSize,
                loopSizeSpecified,
                loopFluid,
                loopFluidSpecified,
                loopPumpPower,
                loopFlowRate,
                loopPoints,
                baseConductance,
                baseConductanceSpecified,
                repair,
                strictMode,
                autoReplaceFuel,
                autoReplaceFuelSpecified,
                stopOnIncidents,
                stopOnIncidentsSpecified);
        }
    }

    private static void runBatchLoop() {
        try (java.io.BufferedReader br = new java.io.BufferedReader(
            new java.io.InputStreamReader(System.in, java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) break;

                String preset = "BREEDER_7X7";
                String layout = null;
                int size = 7;
                int tier = NuclearSimulationEngine.PIPE_TIER_PLATINUM;
                int ticks = 300;
                TurbineCalculator.TurbineMaterial turbMat = TurbineCalculator.TurbineMaterial.HSS_E;
                TurbineCalculator.TurbineSize turbSize = TurbineCalculator.TurbineSize.LARGE;
                TurbineCalculator.FittingMode turbFitting = TurbineCalculator.FittingMode.TIGHT;

                boolean tierSpecified = false;
                boolean turbSpecified = false;
                CoolantLoopModel.CoolingMode coolingMode = CoolantLoopModel.CoolingMode.CONDUCTIVE;
                boolean coolingModeSpecified = false;
                double baseConductance = 32.0;
                boolean baseConductanceSpecified = false;
                boolean repair = false;
                boolean strictMode = true;
                boolean autoReplaceFuel = true;
                boolean autoReplaceFuelSpecified = false;
                boolean stopOnIncidents = false;
                boolean stopOnIncidentsSpecified = false;
                CoolantLoopModel.LoopMaterial loopMat = CoolantLoopModel.LoopMaterial.STEEL;
                boolean loopMatSpecified = false;
                CoolantLoopModel.LoopPipeSize loopSize = CoolantLoopModel.LoopPipeSize.NORMAL;
                boolean loopSizeSpecified = false;
                CoolantLoopModel.CoolantFluidType loopFluid = CoolantLoopModel.CoolantFluidType.DISTILLED_WATER;
                boolean loopFluidSpecified = false;
                double loopPumpPower = -1.0;
                double loopFlowRate = -1.0;
                String loopPoints = null;

                String[] tokens = line.split("\\s+");
                for (String token : tokens) {
                    int eq = token.indexOf('=');
                    if (eq <= 0) continue;
                    String k = token.substring(0, eq)
                        .toLowerCase();
                    String v = token.substring(eq + 1);
                    switch (k) {
                        case "preset" -> preset = v;
                        case "layout" -> layout = v;
                        case "size" -> {
                            try {
                                size = Integer.parseInt(v);
                            } catch (Exception ignored) {}
                        }
                        case "tier" -> {
                            tierSpecified = true;
                            tier = parseTier(v);
                        }
                        case "ticks" -> {
                            try {
                                ticks = Integer.parseInt(v);
                            } catch (Exception ignored) {}
                        }
                        case "temp_low", "templow" -> {
                            try {
                                NuclearSimulationEngine.tempThresholdLow = Double.parseDouble(v);
                            } catch (Exception ignored) {}
                        }
                        case "temp_high", "temphigh" -> {
                            try {
                                NuclearSimulationEngine.tempThresholdHigh = Double.parseDouble(v);
                            } catch (Exception ignored) {}
                        }
                        case "reactivity_pow", "reactivitypow" -> {
                            try {
                                NuclearSimulationEngine.reactivityPower = Double.parseDouble(v);
                            } catch (Exception ignored) {}
                        }
                        case "fission_mult", "fissionmult" -> {
                            try {
                                NuclearSimulationEngine.thermalFissionMultiplier = Double.parseDouble(v);
                            } catch (Exception ignored) {}
                        }
                        case "global_fission_mult", "globalfissionmult" -> {
                            try {
                                NuclearSimulationEngine.globalThermalFissionMultiplier = Double.parseDouble(v);
                            } catch (Exception ignored) {}
                        }
                        case "eu_per_degree", "euperdegree" -> {
                            try {
                                NuclearSimulationEngine.setEuPerDegree(Double.parseDouble(v));
                            } catch (Exception ignored) {}
                        }
                        case "fission_heat", "fissionheat" -> {
                            try {
                                NuclearSimulationEngine.fissionHeatPerNeutron = Double.parseDouble(v);
                            } catch (Exception ignored) {}
                        }
                        case "hp_boil", "hpboil" -> {
                            try {
                                NuclearSimulationEngine.hpWaterBoilingPoint = Double.parseDouble(v);
                            } catch (Exception ignored) {}
                        }
                        case "hatch_cap", "hatchcap" -> {
                            try {
                                NuclearSimulationEngine.hatchCoolantCapacity = Integer.parseInt(v);
                            } catch (Exception ignored) {}
                        }
                        case "turnover_curve", "turnovercurve" -> NuclearSimulationEngine.turnoverCurve = NuclearSimulationEngine.TurnoverCurve
                            .fromString(v);
                        case "turnover_dt", "turnoverdt" -> {
                            try {
                                NuclearSimulationEngine.turnoverDeltaTMax = Double.parseDouble(v);
                            } catch (Exception ignored) {}
                        }
                        case "turnover_pow", "turnover_exp", "turnoverexp" -> {
                            try {
                                NuclearSimulationEngine.turnoverExponent = Double.parseDouble(v);
                            } catch (Exception ignored) {}
                        }
                        case "feed_rate", "feedrate" -> {
                            try {
                                NuclearSimulationEngine.coolantFeedRate = Integer.parseInt(v);
                            } catch (Exception ignored) {}
                        }
                        case "cooling_heat", "coolingheat" -> {
                            try {
                                NuclearSimulationEngine.coolingHeatPerLiter = Double.parseDouble(v);
                            } catch (Exception ignored) {}
                        }
                        case "ambient_temp", "ambient" -> {
                            try {
                                NuclearSimulationEngine.setAmbientTemperature(Double.parseDouble(v));
                            } catch (Exception ignored) {}
                        }
                        case "ic2_heat", "ic2_cooling_heat" -> {
                            try {
                                NuclearSimulationEngine.ic2CoolantHeatPerLiter = Double.parseDouble(v);
                            } catch (Exception ignored) {}
                        }
                        case "burnup_mult", "burnupmult" -> {
                            try {
                                NuclearSimulationEngine.fuelBurnupMultiplier = Double.parseDouble(v);
                            } catch (Exception ignored) {}
                        }
                        case "material", "turb_mat" -> {
                            turbMat = TurbineCalculator.TurbineMaterial.fromString(v);
                            turbSpecified = true;
                        }
                        case "turb_size" -> {
                            turbSize = TurbineCalculator.TurbineSize.fromString(v);
                            turbSpecified = true;
                        }
                        case "fitting", "turb_fit" -> {
                            turbFitting = TurbineCalculator.FittingMode.fromString(v);
                            turbSpecified = true;
                        }
                        case "cooling", "cooling_mode", "coolingmode" -> {
                            coolingMode = CoolantLoopModel.CoolingMode.fromString(v);
                            coolingModeSpecified = true;
                        }
                        case "base_conductance", "baseconductance", "conductance" -> {
                            try {
                                baseConductance = Double.parseDouble(v);
                                baseConductanceSpecified = true;
                            } catch (Exception ignored) {}
                        }
                        case "repair" -> {
                            repair = Boolean.parseBoolean(v) || "1".equals(v) || "true".equalsIgnoreCase(v);
                        }
                        case "strict", "strict_mode", "strictmode" -> {
                            strictMode = Boolean.parseBoolean(v) || "1".equals(v) || "true".equalsIgnoreCase(v);
                        }
                        case "loop_material", "loop_mat", "loopmat" -> {
                            loopMat = CoolantLoopModel.LoopMaterial.fromString(v);
                            loopMatSpecified = true;
                        }
                        case "loop_size", "loopsize" -> {
                            loopSize = CoolantLoopModel.LoopPipeSize.fromString(v);
                            loopSizeSpecified = true;
                        }
                        case "loop_fluid", "loopfluid" -> {
                            loopFluid = CoolantLoopModel.CoolantFluidType.fromString(v);
                            loopFluidSpecified = true;
                        }
                        case "loop_power", "looppower" -> {
                            try {
                                loopPumpPower = Double.parseDouble(v);
                            } catch (Exception ignored) {}
                        }
                        case "loop_flow", "loopflow" -> {
                            try {
                                loopFlowRate = Double.parseDouble(v);
                            } catch (Exception ignored) {}
                        }
                        case "loop_points", "looppoints" -> loopPoints = v;
                        case "auto_refuel", "autorefuel", "auto_replace_fuel", "autoreplacefuel", "auto_supply_fuel", "autosupplyfuel" -> {
                            autoReplaceFuel = Boolean.parseBoolean(v) || "1".equals(v) || "true".equalsIgnoreCase(v);
                            autoReplaceFuelSpecified = true;
                        }
                        case "stop_on_incidents", "stoponincidents" -> {
                            stopOnIncidents = Boolean.parseBoolean(v) || "1".equals(v) || "true".equalsIgnoreCase(v);
                            stopOnIncidentsSpecified = true;
                        }
                        default -> {}
                    }
                }
                runJsonSimulation(
                    preset,
                    layout,
                    size,
                    tier,
                    ticks,
                    turbMat,
                    turbSize,
                    turbFitting,
                    tierSpecified,
                    turbSpecified,
                    coolingMode,
                    coolingModeSpecified,
                    loopMat,
                    loopMatSpecified,
                    loopSize,
                    loopSizeSpecified,
                    loopFluid,
                    loopFluidSpecified,
                    loopPumpPower,
                    loopFlowRate,
                    loopPoints,
                    baseConductance,
                    baseConductanceSpecified,
                    repair,
                    strictMode,
                    autoReplaceFuel,
                    autoReplaceFuelSpecified,
                    stopOnIncidents,
                    stopOnIncidentsSpecified);
                System.out.flush();
            }
        } catch (java.io.IOException e) {
            System.err.println("Batch error: " + e.getMessage());
        }
    }

    private static void configureCoolantLoop(StandaloneNuclearGrid grid,
        CoolantLoopModel.CoolingMode coolingMode, boolean coolingModeSpecified,
        CoolantLoopModel.LoopMaterial loopMat, boolean loopMatSpecified,
        CoolantLoopModel.LoopPipeSize loopSize, boolean loopSizeSpecified,
        CoolantLoopModel.CoolantFluidType loopFluid, boolean loopFluidSpecified,
        double loopPumpPower, double loopFlowRate, String loopPoints) {
        if (coolingModeSpecified) {
            if (!grid.setCoolingMode(coolingMode)) {
                System.err.println("WARNING: Convective cooling mode requires Tier 2+ (IV Platinum / LuV Osmium). Falling back to CONDUCTIVE.");
            }
        }
        CoolantLoopModel loop = grid.getCoolantLoop();
        if (loop != null) {
            if (loopMatSpecified) {
                loop.setMaterial(loopMat);
            }
            if (loopSizeSpecified) {
                loop.setPipeSize(loopSize);
            }
            if (loopFluidSpecified) {
                loop.setFluidType(loopFluid);
            }
            if (loopPumpPower >= 0) {
                loop.setPumpPowerEUt(loopPumpPower);
            } else if (loopFlowRate >= 0) {
                loop.setCirculationFlowLs(loopFlowRate);
            }
            if (loopPoints != null && !loopPoints.trim().isEmpty()) {
                loop.clearAttachedPoints();
                String[] pairs = loopPoints.split("[;\\s]+");
                for (String pair : pairs) {
                    if (pair.trim().isEmpty()) continue;
                    String[] xy = pair.split("[,:]");
                    if (xy.length == 2) {
                        try {
                            int px = Integer.parseInt(xy[0].trim());
                            int py = Integer.parseInt(xy[1].trim());
                            loop.attachPoint(px, py);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }
    }

    private static void runJsonSimulation(String preset, String layout, int size, int tier, int ticks,
        TurbineCalculator.TurbineMaterial turbMat, TurbineCalculator.TurbineSize turbSize,
        TurbineCalculator.FittingMode turbFitting, boolean tierSpecified, boolean turbSpecified,
        CoolantLoopModel.CoolingMode coolingMode, boolean coolingModeSpecified,
        CoolantLoopModel.LoopMaterial loopMat, boolean loopMatSpecified,
        CoolantLoopModel.LoopPipeSize loopSize, boolean loopSizeSpecified,
        CoolantLoopModel.CoolantFluidType loopFluid, boolean loopFluidSpecified,
        double loopPumpPower, double loopFlowRate, String loopPoints,
        double baseConductance, boolean baseConductanceSpecified, boolean repair,
        boolean strictMode, boolean autoReplaceFuel, boolean autoReplaceFuelSpecified,
        boolean stopOnIncidents, boolean stopOnIncidentsSpecified) {
        StandaloneNuclearGrid grid = new StandaloneNuclearGrid(size, size, tier);
        grid.setStrictMode(strictMode);
        if (autoReplaceFuelSpecified) {
            grid.setAutoReplaceFuel(autoReplaceFuel);
        }
        if (stopOnIncidentsSpecified) {
            grid.setStopOnIncidents(stopOnIncidents);
        }
        if (baseConductanceSpecified) {
            grid.setBaseHatchConductance(baseConductance);
        }
        if (repair) {
            grid.repair();
        }
        if (layout != null && !layout.trim()
            .isEmpty()) {
            grid.loadLayout(layout);
            if (tierSpecified) grid.setPipeTier(tier);
        } else {
            grid.loadPreset(preset);
            if (tierSpecified) grid.setPipeTier(tier);
        }
        if (turbSpecified) {
            grid.setTurbineMaterial(turbMat);
            grid.setTurbineSize(turbSize);
            grid.setTurbineFitting(turbFitting);
        }
        configureCoolantLoop(grid, coolingMode, coolingModeSpecified, loopMat, loopMatSpecified,
            loopSize, loopSizeSpecified, loopFluid, loopFluidSpecified, loopPumpPower, loopFlowRate, loopPoints);

        int tickReached = 0;
        for (int t = 1; t <= ticks; t++) {
            tickReached = t;
            boolean ok = grid.step();
            if (!ok) break;
        }

        TurbineCalculator.PowerEstimationResult p = grid.getLastPowerResult();
        double power = p != null ? p.totalPowerEUt : 0.0;
        double minLongevity = grid.getMinFuelRodLongevityMinutes();
        double avgLongevity = grid.getAvgFuelRodLongevityMinutes();
        int activeFuelRods = grid.getActiveFuelRodCount();
        int totalDurabilityLost = grid.getTotalDurabilityLost();
        int actualTier = grid.getPipeTier();
        long tierVoltage = NuclearSimulationEngine.getPipeTierVoltage(actualTier);
        String nominalTierName = NuclearSimulationEngine.getPipeTierVoltageName(actualTier);
        double targetPower60A = 60.0 * tierVoltage;
        double actualAmps = power / (double) tierVoltage;

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"preset\":\"")
            .append(preset)
            .append("\",");
        sb.append("\"layout\":\"")
            .append(grid.toLayoutString())
            .append("\",");
        sb.append("\"size\":")
            .append(grid.getWidth())
            .append(",");
        sb.append("\"tier\":")
            .append(actualTier)
            .append(",");
        sb.append("\"ticksSimulated\":")
            .append(tickReached)
            .append(",");
        sb.append("\"ticksTarget\":")
            .append(ticks)
            .append(",");
        sb.append("\"exploded\":")
            .append(grid.isExploded())
            .append(",");
        sb.append("\"explosionReason\":\"")
            .append(
                grid.getExplosionReason()
                    .replace("\"", "\\\""))
            .append("\",");
        sb.append("\"powerFailed\":")
            .append(grid.isPowerFailed())
            .append(",");
        sb.append("\"powerFailReason\":\"")
            .append(
                grid.getPowerFailReason()
                    .replace("\"", "\\\""))
            .append("\",");
        sb.append("\"stopOnIncidents\":")
            .append(grid.isStopOnIncidents())
            .append(",");
        sb.append("\"haltedByIncident\":")
            .append(grid.isHaltedByIncident())
            .append(",");
        sb.append("\"lastHaltIncidentReason\":\"")
            .append(escapeJson(grid.getLastHaltIncidentReason()))
            .append("\",");
        sb.append("\"autoSupplyFuel\":")
            .append(grid.isAutoSupplyFuel())
            .append(",");
        sb.append("\"burnedFuelCount\":")
            .append(grid.getBurnedFuelCount())
            .append(",");
        sb.append("\"fuelBurned\":")
            .append(grid.hasFuelBurned())
            .append(",");
        sb.append("\"voidedHatchCount\":")
            .append(grid.getVoidedHatchCount())
            .append(",");
        sb.append("\"peakLifetimeTemp\":")
            .append(String.format(java.util.Locale.US, "%.2f", grid.getPeakLifetimeTemp()))
            .append(",");
        sb.append("\"coreMaxTemp\":")
            .append(String.format(java.util.Locale.US, "%.2f", grid.getCoreMaxTemp()))
            .append(",");
        sb.append("\"coreAvgTemp\":")
            .append(String.format(java.util.Locale.US, "%.2f", grid.getCoreAvgTemp()))
            .append(",");
        sb.append("\"efficiency\":")
            .append(String.format(java.util.Locale.US, "%.4f", grid.getEfficiency()))
            .append(",");
        sb.append("\"baseHatchConductance\":")
            .append(String.format(java.util.Locale.US, "%.1f", grid.getBaseHatchConductance()))
            .append(",");
        sb.append("\"reactorDamage\":")
            .append(String.format(java.util.Locale.US, "%.1f", grid.getReactorDamage()))
            .append(",");
        sb.append("\"maintenanceIssues\":")
            .append(grid.getMaintenanceIssues())
            .append(",");
        sb.append("\"maintenanceEfficiency\":")
            .append(String.format(java.util.Locale.US, "%.4f", grid.getMaintenanceEfficiency()))
            .append(",");
        sb.append("\"strictMode\":")
            .append(grid.isStrictMode())
            .append(",");
        sb.append("\"totalNeutrons\":")
            .append(grid.getTotalNeutronsGenerated())
            .append(",");
        sb.append("\"totalSteam\":")
            .append(grid.getTotalSteamProduced())
            .append(",");
        sb.append("\"totalDeuterium\":")
            .append(grid.getTotalDeuteriumProduced())
            .append(",");
        sb.append("\"totalTritium\":")
            .append(grid.getTotalTritiumProduced())
            .append(",");
        sb.append("\"powerEUt\":")
            .append(String.format(java.util.Locale.US, "%.1f", power))
            .append(",");
        sb.append("\"directPowerEUt\":")
            .append(String.format(java.util.Locale.US, "%.1f", p != null ? p.directPowerEUt : 0.0))
            .append(",");
        sb.append("\"totalTurbinesNeeded\":")
            .append(String.format(java.util.Locale.US, "%.2f", p != null ? p.totalTurbinesNeeded : 0.0))
            .append(",");
        sb.append("\"lstTurbinesNeeded\":")
            .append(String.format(java.util.Locale.US, "%.2f", p != null ? p.lstTurbinesNeeded : 0.0))
            .append(",");
        sb.append("\"xlstTurbinesNeeded\":")
            .append(String.format(java.util.Locale.US, "%.2f", p != null ? p.xlstTurbinesNeeded : 0.0))
            .append(",");
        sb.append("\"xlstHpTurbinesNeeded\":")
            .append(String.format(java.util.Locale.US, "%.2f", p != null ? p.xlstHpTurbinesNeeded : 0.0))
            .append(",");
        sb.append("\"xlstScTurbinesNeeded\":")
            .append(String.format(java.util.Locale.US, "%.2f", p != null ? p.xlstScTurbinesNeeded : 0.0))
            .append(",");
        sb.append("\"coolantMachine\":\"")
            .append(p != null ? p.coolantMachine : "None")
            .append("\",");
        sb.append("\"coolantMachineMode\":\"")
            .append(p != null ? p.coolantMachineMode : "Inactive")
            .append("\",");
        sb.append("\"eheMode\":\"")
            .append(p != null ? p.eheMode : "Inactive")
            .append("\",");
        sb.append("\"eheSteamProduced\":")
            .append(String.format(java.util.Locale.US, "%.1f", p != null ? p.eheSteamProduced : 0.0))
            .append(",");
        sb.append("\"voltageTier\":\"")
            .append(getVoltageTier(power))
            .append("\",");
        sb.append("\"activeFuelRods\":")
            .append(activeFuelRods)
            .append(",");
        sb.append("\"totalDurabilityLost\":")
            .append(totalDurabilityLost)
            .append(",");
        sb.append("\"minLongevityMinutes\":")
            .append(
                Double.isInfinite(minLongevity) ? "\"Infinity\""
                    : String.format(java.util.Locale.US, "%.2f", minLongevity))
            .append(",");
        sb.append("\"avgLongevityMinutes\":")
            .append(
                Double.isInfinite(avgLongevity) ? "\"Infinity\""
                    : String.format(java.util.Locale.US, "%.2f", avgLongevity))
            .append(",");
        sb.append("\"casingNominalTier\":\"")
            .append(nominalTierName)
            .append("\",");
        sb.append("\"casingVoltage\":")
            .append(tierVoltage)
            .append(",");
        sb.append("\"targetPower60A\":")
            .append(String.format(java.util.Locale.US, "%.1f", targetPower60A))
            .append(",");
        sb.append("\"actualAmps\":")
            .append(String.format(java.util.Locale.US, "%.2f", actualAmps))
            .append(",");
        sb.append("\"fuelBurnupMultiplier\":")
            .append(NuclearSimulationEngine.fuelBurnupMultiplier)
            .append(",");
        sb.append("\"tempThresholdLow\":")
            .append(NuclearSimulationEngine.tempThresholdLow)
            .append(",");
        sb.append("\"tempThresholdHigh\":")
            .append(NuclearSimulationEngine.tempThresholdHigh)
            .append(",");
        sb.append("\"reactivityPower\":")
            .append(NuclearSimulationEngine.reactivityPower)
            .append(",");
        sb.append("\"thermalFissionMultiplier\":")
            .append(NuclearSimulationEngine.thermalFissionMultiplier)
            .append(",");
        sb.append("\"fissionHeatPerNeutron\":")
            .append(NuclearSimulationEngine.fissionHeatPerNeutron)
            .append(",");
        sb.append("\"hatchCoolantCapacity\":")
            .append(NuclearSimulationEngine.hatchCoolantCapacity)
            .append(",");
        sb.append("\"turnoverCurve\":\"")
            .append(NuclearSimulationEngine.turnoverCurve.name())
            .append("\",");
        sb.append("\"turnoverDeltaTMax\":")
            .append(NuclearSimulationEngine.turnoverDeltaTMax)
            .append(",");
        sb.append("\"turnoverExponent\":")
            .append(NuclearSimulationEngine.turnoverExponent)
            .append(",");
        sb.append("\"coolantFeedRate\":")
            .append(NuclearSimulationEngine.coolantFeedRate)
            .append(",");
        sb.append("\"coolingHeatPerLiter\":")
            .append(NuclearSimulationEngine.coolingHeatPerLiter)
            .append(",");
        sb.append("\"ambientTemp\":")
            .append(NuclearSimulationEngine.ambientTemp)
            .append(",");
        sb.append("\"ic2CoolantHeatPerLiter\":")
            .append(NuclearSimulationEngine.ic2CoolantHeatPerLiter)
            .append(",");

        TurbineCalculator.ScenarioHypotheticalResult sc = grid.getLastScenariosResult();
        if (sc == null) {
            sc = TurbineCalculator.calculateBothScenarios(
                actualTier,
                grid.getFlowRegularSteam(),
                grid.getFlowSuperheatedSteam(),
                grid.getFlowSupercriticalSteam(),
                grid.getFlowHeavyWaterSteam(),
                grid.getFlowHPHeavyWaterSteam(),
                grid.getFlowHotCoolant(),
                grid.getFlowDirectEU(),
                null,
                null);
        }
        sb.append("\"scenarios\":{");
        sb.append("\"tight\":{")
            .append("\"material\":\"")
            .append(sc.tightConfig.material.displayName)
            .append("\",")
            .append("\"size\":\"")
            .append(sc.tightConfig.size.displayName)
            .append("\",")
            .append("\"mode\":\"")
            .append(sc.tightConfig.mode.name())
            .append("\",")
            .append("\"efficiency\":")
            .append(String.format(java.util.Locale.US, "%.2f", sc.tightConfig.efficiency))
            .append(",")
            .append("\"description\":\"")
            .append(sc.tightConfig.description)
            .append("\",")
            .append("\"powerEUt\":")
            .append(String.format(java.util.Locale.US, "%.1f", sc.tightResult.totalPowerEUt))
            .append(",")
            .append("\"totalTurbinesNeeded\":")
            .append(String.format(java.util.Locale.US, "%.2f", sc.tightResult.totalTurbinesNeeded))
            .append(",")
            .append("\"lstTurbinesNeeded\":")
            .append(String.format(java.util.Locale.US, "%.2f", sc.tightResult.lstTurbinesNeeded))
            .append(",")
            .append("\"xlstTurbinesNeeded\":")
            .append(String.format(java.util.Locale.US, "%.2f", sc.tightResult.xlstTurbinesNeeded))
            .append(",")
            .append("\"xlstHpTurbinesNeeded\":")
            .append(String.format(java.util.Locale.US, "%.2f", sc.tightResult.xlstHpTurbinesNeeded))
            .append(",")
            .append("\"xlstScTurbinesNeeded\":")
            .append(String.format(java.util.Locale.US, "%.2f", sc.tightResult.xlstScTurbinesNeeded))
            .append(",")
            .append("\"coolantMachine\":\"")
            .append(sc.tightResult.coolantMachine)
            .append("\",")
            .append("\"coolantMachineCount\":")
            .append(String.format(java.util.Locale.US, "%.1f", sc.tightResult.coolantMachineCount))
            .append("},");
        sb.append("\"loose\":{")
            .append("\"material\":\"")
            .append(sc.looseConfig.material.displayName)
            .append("\",")
            .append("\"size\":\"")
            .append(sc.looseConfig.size.displayName)
            .append("\",")
            .append("\"mode\":\"")
            .append(sc.looseConfig.mode.name())
            .append("\",")
            .append("\"efficiency\":")
            .append(String.format(java.util.Locale.US, "%.2f", sc.looseConfig.efficiency))
            .append(",")
            .append("\"description\":\"")
            .append(sc.looseConfig.description)
            .append("\",")
            .append("\"powerEUt\":")
            .append(String.format(java.util.Locale.US, "%.1f", sc.looseResult.totalPowerEUt))
            .append(",")
            .append("\"totalTurbinesNeeded\":")
            .append(String.format(java.util.Locale.US, "%.2f", sc.looseResult.totalTurbinesNeeded))
            .append(",")
            .append("\"lstTurbinesNeeded\":")
            .append(String.format(java.util.Locale.US, "%.2f", sc.looseResult.lstTurbinesNeeded))
            .append(",")
            .append("\"xlstTurbinesNeeded\":")
            .append(String.format(java.util.Locale.US, "%.2f", sc.looseResult.xlstTurbinesNeeded))
            .append(",")
            .append("\"xlstHpTurbinesNeeded\":")
            .append(String.format(java.util.Locale.US, "%.2f", sc.looseResult.xlstHpTurbinesNeeded))
            .append(",")
            .append("\"xlstScTurbinesNeeded\":")
            .append(String.format(java.util.Locale.US, "%.2f", sc.looseResult.xlstScTurbinesNeeded))
            .append(",")
            .append("\"coolantMachine\":\"")
            .append(sc.looseResult.coolantMachine)
            .append("\",")
            .append("\"coolantMachineCount\":")
            .append(String.format(java.util.Locale.US, "%.1f", sc.looseResult.coolantMachineCount))
            .append("},");
        sb.append("\"turbineReductionRatio\":")
            .append(String.format(java.util.Locale.US, "%.2f", sc.turbineReductionRatio))
            .append(",");
        sb.append("\"powerReductionRatio\":")
            .append(String.format(java.util.Locale.US, "%.2f", sc.powerReductionRatio));
        sb.append("},");
        sb.append("\"coolingMode\":\"").append(grid.getCoolingMode().name()).append("\",");
        sb.append("\"grossPowerEUt\":").append(String.format(java.util.Locale.US, "%.1f", grid.getGrossPowerEUt())).append(",");
        sb.append("\"pumpPowerEUt\":").append(String.format(java.util.Locale.US, "%.1f", grid.getPumpPowerEUt())).append(",");
        CoolantLoopModel cl = grid.getCoolantLoop();
        sb.append("\"coolantLoop\":{");
        sb.append("\"enabled\":").append(grid.getCoolingMode() == CoolantLoopModel.CoolingMode.CONVECTIVE_LOOP).append(",");
        sb.append("\"material\":\"").append(cl.getMaterial().name()).append("\",");
        sb.append("\"pipeSize\":\"").append(cl.getPipeSize().name()).append("\",");
        sb.append("\"fluid\":\"").append(cl.getFluidType().name()).append("\",");
        sb.append("\"pumpPowerEUt\":").append(String.format(java.util.Locale.US, "%.1f", cl.getLastPumpPowerEUt())).append(",");
        sb.append("\"circulationFlowLs\":").append(String.format(java.util.Locale.US, "%.2f", cl.getCurrentFlowRateLPerSec())).append(",");
        sb.append("\"loopTemperature\":").append(String.format(java.util.Locale.US, "%.2f", cl.getCurrentCoolantTempCelsius())).append(",");
        sb.append("\"peakPressureBar\":").append(String.format(java.util.Locale.US, "%.2f", cl.getCurrentPressureBar())).append(",");
        sb.append("\"maxSafePressureBar\":").append(String.format(java.util.Locale.US, "%.2f", cl.getMaterial().maxPressureBar)).append(",");
        sb.append("\"maxSafeTemp\":").append(String.format(java.util.Locale.US, "%.2f", cl.getMaterial().maxTemperatureCelsius)).append(",");
        sb.append("\"ruptured\":").append(cl.isRuptured()).append(",");
        sb.append("\"ruptureReason\":\"").append(cl.getRuptureReason().replace("\"", "\\\"")).append("\",");
        sb.append("\"convectiveHeatRemovedEUt\":").append(String.format(java.util.Locale.US, "%.2f", cl.getLastHeatExtractedEUt())).append(",");
        sb.append("\"secondarySteamProduced\":").append(String.format(java.util.Locale.US, "%.2f", cl.getLastSecondarySteamProducedLt())).append(",");
        sb.append("\"totalDeuteriumProduced\":").append(cl.getTotalDeuteriumProduced()).append(",");
        sb.append("\"totalTritiumProduced\":").append(cl.getTotalTritiumProduced());
        sb.append("},");

        // Byproducts (depleted fuel items, depleted liquid fuels, and isotopes)
        List<StandaloneNuclearGrid.SolidFuelByproduct> solidByproducts = grid.getSolidFuelByproducts();
        List<StandaloneNuclearGrid.LiquidFuelByproduct> liquidByproducts = grid.getLiquidFuelByproducts();
        List<StandaloneNuclearGrid.IsotopeByproduct> isotopeByproducts = grid.getIsotopeByproducts();

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
        sb.append("\"autoReplaceFuel\":").append(grid.isAutoReplaceFuel()).append(",");
        sb.append("\"totalSolidItemsPerMin\":").append(String.format(java.util.Locale.US, "%.2f", totalSolidItemsPerMin)).append(",");
        sb.append("\"totalSolidItemsPerHour\":").append(String.format(java.util.Locale.US, "%.1f", totalSolidItemsPerHour)).append(",");
        sb.append("\"totalLiquidLitersPerMin\":").append(String.format(java.util.Locale.US, "%.1f", totalLiquidLitersPerMin)).append(",");
        sb.append("\"totalLiquidLitersPerHour\":").append(String.format(java.util.Locale.US, "%.0f", totalLiquidLitersPerHour)).append(",");
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
            sb.append("\"itemsPerMin\":").append(String.format(java.util.Locale.US, "%.2f", s.itemsPerMinute)).append(",");
            sb.append("\"itemsPerHour\":").append(String.format(java.util.Locale.US, "%.1f", s.itemsPerHour)).append(",");
            sb.append("\"totalProduced\":").append(s.totalProduced).append(",");
            sb.append("\"avgLifespanMin\":").append(Double.isInfinite(s.avgLifespanMinutes) ? "\"Infinity\"" : String.format(java.util.Locale.US, "%.1f", s.avgLifespanMinutes));
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
            sb.append("\"litersPerMin\":").append(String.format(java.util.Locale.US, "%.1f", l.litersPerMinute)).append(",");
            sb.append("\"litersPerHour\":").append(String.format(java.util.Locale.US, "%.0f", l.litersPerHour)).append(",");
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
            sb.append("\"litersPerMin\":").append(String.format(java.util.Locale.US, "%.2f", iso.litersPerMinute)).append(",");
            sb.append("\"litersPerHour\":").append(String.format(java.util.Locale.US, "%.1f", iso.litersPerHour)).append(",");
            sb.append("\"totalLiters\":").append(iso.totalLiters);
            sb.append("}");
        }
        sb.append("],");

        List<StandaloneNuclearGrid.RawMaterialBalance> rawMaterials = grid.getRawMaterialBalances();
        sb.append("\"rawMaterials\":[");
        for (int i = 0; i < rawMaterials.size(); i++) {
            if (i > 0) sb.append(",");
            StandaloneNuclearGrid.RawMaterialBalance r = rawMaterials.get(i);
            sb.append("{");
            sb.append("\"name\":\"").append(escapeJson(r.name)).append("\",");
            sb.append("\"code\":\"").append(r.code).append("\",");
            sb.append("\"unit\":\"").append(r.unit).append("\",");
            sb.append("\"consumedPerMin\":").append(String.format(java.util.Locale.US, "%.2f", r.consumedPerMinute)).append(",");
            sb.append("\"consumedPerHour\":").append(String.format(java.util.Locale.US, "%.1f", r.consumedPerHour)).append(",");
            sb.append("\"producedPerMin\":").append(String.format(java.util.Locale.US, "%.2f", r.producedPerMinute)).append(",");
            sb.append("\"producedPerHour\":").append(String.format(java.util.Locale.US, "%.1f", r.producedPerHour)).append(",");
            sb.append("\"netPerMin\":").append(String.format(java.util.Locale.US, "%.2f", r.netPerMinute)).append(",");
            sb.append("\"netPerHour\":").append(String.format(java.util.Locale.US, "%.1f", r.netPerHour));
            sb.append("}");
        }
        sb.append("]");
        sb.append("}");

        sb.append("}");
        System.out.println(sb.toString());
    }

    private static void printHelp() {
        System.out.println(ANSI_WHITE_BOLD + "GTNH Nuclear Simulation CLI & Diagnostic Tool" + ANSI_RESET);
        System.out.println("Usage: gtnh-nuclear-sim [options]");
        System.out.println("Options:");
        System.out.println(
            "  --preset <name>       Preset layout: BEST_ELECTRUM_5X5, BEST_PLATINUM_7X7, BEST_OSMIUM_9X9, BEST_QUANTIUM_9X9, BEST_FLUXED_9X9, BEST_PLUTONIUM_9X9");
        System.out.println("  --size <N>            Grid dimensions N x N (default 5)");
        System.out.println(
            "  --tier <name/#>       Pipe casing tier: electrum(0), platinum(1), osmium(2), quantium(3), fluxed(4), black_plutonium(5)");
        System.out.println("  --ticks <N>           Simulation duration in ticks (default 100)");
        System.out.println("  --material <name>     Turbine rotor material (default HSS-E)");
        System.out.println("  --turb-size <size>    Turbine size: small, normal, large, huge (default large)");
        System.out.println("  --fitting <mode>      Housing fitting: tight, loose (default tight)");
        System.out.println("  --hatch-cap <L>       Hatch coolant capacity in liters (default 2000)");
        System.out.println(
            "  --turnover-curve <C>  Turnover curve: EXPONENTIAL, LINEAR, SIGMOID, STEP (default EXPONENTIAL)");
        System.out.println("  --turnover-dt <T>     Turnover delta-T max in °C above boiling (default 100.0)");
        System.out.println("  --turnover-pow <P>    Turnover power-law exponent (default 1.5)");
        System.out.println("  --feed-rate <L/t>     Coolant refill rate per tick (default 2000)");
        System.out
            .println("  --cooling-heat <H>    Latent cooling heat extracted per liter of steam in EU (default 4.0)");
        System.out.println("  --ambient-temp <T>    Ambient temperature baseline in °C (default 24.0)");
        System.out.println(
            "  --ic2-heat <H>        Continuous cooling heat extracted per liter of IC2 coolant in EU (default 20.0)");
        System.out.println("  --temp-low <T>        Negative reactivity low threshold in °C (default 800)");
        System.out.println("  --temp-high <T>       Negative reactivity high threshold in °C (default 2800)");
        System.out.println("  --reactivity-pow <P>  Negative reactivity curve exponent (default 1.2)");
        System.out.println("  --layout <codes>      Custom layout string (rows separated by ';', cells by ',')");
        System.out.println("  --fission-mult <K>    Thermal neutron induced fission multiplier (default 1.1)");
        System.out.println("  --fission-heat <H>    Direct heat EU generated per fission neutron (default 18.0)");
        System.out.println("  --hp-boil <T>         High pressure coolant boiling point in °C (default 200.0)");
        System.out.println(
            "  --cooling-mode <mode> Cooling mode: CONDUCTIVE, CONVECTIVE_LOOP (default CONDUCTIVE)");
        System.out.println("  --base-conductance <U> Base hatch conductance in EU/(t·°C) (default 32.0)");
        System.out.println("  --repair              Repair all reactor damage and clear maintenance issues");
        System.out.println("  --strict              Fail-fast on thermal shock, casing overheat, or fuel burnup (calibration mode)");
        System.out.println("  --no-strict           Disable fail-fast (accumulate damage and maintenance issues)");
        System.out.println("  --auto-refuel         Automatically replace spent fuel rods for continuous run (default true)");
        System.out.println("  --no-auto-refuel      Leave spent fuel rods depleted without replacement");
        System.out.println("  --stop-on-incidents   Pause simulation automatically when an incident/damage occurs");
        System.out.println("  --no-stop-on-incidents Do not pause on incidents (continue accumulating damage)");
        System.out.println("  --auto-supply-fuel    Automatically replace spent fuel and replenish liquid fuel (default true)");
        System.out.println("  --no-auto-supply-fuel Disable automatic fuel replacement and replenishment");
        System.out.println("  --loop-material <mat> Coolant loop material: STEEL, STAINLESS_STEEL, TITANIUM, TUNGSTENSTEEL, NEUTRONIUM");
        System.out.println(
            "  --loop-size <size>    Coolant loop pipe size: TINY, SMALL, NORMAL, LARGE, HUGE (default NORMAL)");
        System.out.println(
            "  --loop-fluid <fluid>  Coolant loop fluid: DISTILLED_WATER, HEAVY_WATER (default DISTILLED_WATER)");
        System.out.println(
            "  --loop-power <EU/t>   Coolant loop pump power in EU/t (calculates flow from hydrodynamic polynomial)");
        System.out.println(
            "  --loop-flow <L/s>     Coolant loop target circulation flow in L/s");
        System.out.println(
            "  --loop-points <pts>   Semicolon-separated core coordinates to attach to loop (e.g. \"2,2;2,3;2,4\")");
        System.out.println(
            "  --trace [N]           Enable intermediate step snapshot ring buffer (up to N steps, default 500)");
        System.out.println("  --json                Output compact JSON summary for automated test scripts");
        System.out.println("  --web [port]          Launch standalone Web GUI on specified port (default 8085)");
        System.out.println("  --help, -h            Display this help message");
    }

    private static void runCliSimulation(String preset, String layout, int size, int tier, int ticks,
        TurbineCalculator.TurbineMaterial turbMat, TurbineCalculator.TurbineSize turbSize,
        TurbineCalculator.FittingMode turbFitting, boolean tierSpecified, boolean turbSpecified, boolean traceEnabled,
        int traceSteps, CoolantLoopModel.CoolingMode coolingMode, boolean coolingModeSpecified,
        CoolantLoopModel.LoopMaterial loopMat, boolean loopMatSpecified,
        CoolantLoopModel.LoopPipeSize loopSize, boolean loopSizeSpecified,
        CoolantLoopModel.CoolantFluidType loopFluid, boolean loopFluidSpecified,
        double loopPumpPower, double loopFlowRate, String loopPoints,
        double baseConductance, boolean baseConductanceSpecified, boolean repair,
        boolean strictMode, boolean autoReplaceFuel, boolean autoReplaceFuelSpecified,
        boolean stopOnIncidents, boolean stopOnIncidentsSpecified) {
        System.out.println(ANSI_CYAN + "============================================================" + ANSI_RESET);
        System.out.println(ANSI_WHITE_BOLD + "   GTNH MODULAR PRESSURE TUBE REACTOR (MPTR) SIMULATOR" + ANSI_RESET);
        System.out.println(ANSI_CYAN + "============================================================" + ANSI_RESET);

        StandaloneNuclearGrid grid = new StandaloneNuclearGrid(size, size, tier);
        grid.setStrictMode(strictMode);
        if (autoReplaceFuelSpecified) {
            grid.setAutoReplaceFuel(autoReplaceFuel);
        }
        if (stopOnIncidentsSpecified) {
            grid.setStopOnIncidents(stopOnIncidents);
        }
        if (baseConductanceSpecified) {
            grid.setBaseHatchConductance(baseConductance);
        }
        if (repair) {
            grid.repair();
        }
        if (traceEnabled) {
            grid.enableDiagnosticTrace(traceSteps);
        }
        if (layout != null && !layout.trim()
            .isEmpty()) {
            grid.loadLayout(layout);
            if (tierSpecified) grid.setPipeTier(tier);
        } else {
            grid.loadPreset(preset);
            if (tierSpecified) grid.setPipeTier(tier);
        }
        if (turbSpecified) {
            grid.setTurbineMaterial(turbMat);
            grid.setTurbineSize(turbSize);
            grid.setTurbineFitting(turbFitting);
        }
        configureCoolantLoop(grid, coolingMode, coolingModeSpecified, loopMat, loopMatSpecified,
            loopSize, loopSizeSpecified, loopFluid, loopFluidSpecified, loopPumpPower, loopFlowRate, loopPoints);

        if (layout != null && !layout.trim()
            .isEmpty()) {
            System.out.println("Layout:        " + ANSI_GREEN + grid.toLayoutString() + ANSI_RESET);
        } else {
            System.out.println("Preset:        " + ANSI_GREEN + preset + ANSI_RESET);
        }
        System.out.println("Grid Size:     " + grid.getWidth() + "x" + grid.getHeight());
        System.out.println("Casing Tier:   " + NuclearSimulationEngine.getPipeTierName(grid.getPipeTier()));
        System.out.println(
            "Max Safe Temp: " + NuclearSimulationEngine.getMaxOperatingTemperature(grid.getPipeTier()) + " °C");
        System.out.println(
            "Conductance:   " + String.format(java.util.Locale.US, "%.1f EU/(t·°C) [Base]", grid.getBaseHatchConductance()));
        System.out.println("Strict Mode:   " + (strictMode
            ? ANSI_YELLOW + "Enabled (Fail-Fast on Damage/Void)" + ANSI_RESET
            : ANSI_GREEN + "Disabled (Permissive)" + ANSI_RESET));
        System.out.println("Stop On Incidents: " + (grid.isStopOnIncidents()
            ? ANSI_YELLOW + "Enabled (Pause on Damage/Thermal Shock)" + ANSI_RESET
            : ANSI_GREEN + "Disabled" + ANSI_RESET));
        System.out.println("Auto-Supply Fuel:  " + (grid.isAutoSupplyFuel()
            ? ANSI_GREEN + "Enabled (Continuous Rod/Fluid Cycle)" + ANSI_RESET
            : ANSI_YELLOW + "Disabled (Single Batch)" + ANSI_RESET));
        if (grid.getReactorDamage() > 0.0 || grid.getMaintenanceIssues() > 0) {
            System.out.println(
                "Health/Maint:  " + ANSI_YELLOW + String.format(java.util.Locale.US, "Damage: %.1f%%, %d issues (Efficiency: %.1f%%)",
                    grid.getReactorDamage(), grid.getMaintenanceIssues(), grid.getMaintenanceEfficiency() * 100.0) + ANSI_RESET);
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
        System.out.println("Cooling Mode:  " + (grid.getCoolingMode() == CoolantLoopModel.CoolingMode.CONVECTIVE_LOOP
            ? ANSI_CYAN + "CONVECTIVE COOLANT LOOP"
            : ANSI_YELLOW + "CONDUCTIVE (Sub-boiling Hatches)") + ANSI_RESET);
        if (grid.getCoolingMode() == CoolantLoopModel.CoolingMode.CONVECTIVE_LOOP) {
            CoolantLoopModel cl = grid.getCoolantLoop();
            System.out.println("  Loop Mat:    " + cl.getMaterial().displayName + " (Max: " + cl.getMaterial().maxPressureBar + " bar, " + cl.getMaterial().maxTemperatureCelsius + " °C)");
            System.out.println("  Pipe Size:   " + cl.getPipeSize().displayName + " (" + (int)(cl.getPipeSize().diameterMeters * 1000) + " mm ID)");
            System.out.println("  Loop Fluid:  " + cl.getFluidType().displayName + " [Byproduct: " + cl.getFluidType().byproductGas + "]");
            if (cl.isUseTargetFlowMode()) {
                System.out.println("  Target Flow: " + String.format(java.util.Locale.US, "%.1f L/s (Hydrodynamic Power: %.1f EU/t)", cl.getTargetFlowRateLPerSec(), cl.getLastPumpPowerEUt()));
            } else {
                System.out.println("  Pump Power:  " + String.format(java.util.Locale.US, "%.1f EU/t", cl.getPumpElectricalPowerEUt()));
            }
            if (!cl.getAttachedPoints().isEmpty()) {
                System.out.println("  Attached:    " + cl.getAttachedPoints().size() + " core cell(s)");
            }
        }
        System.out.println("Ticks to Sim:  " + ticks);
        System.out.println();

        System.out.println(ANSI_WHITE_BOLD + "Initial Grid Layout:" + ANSI_RESET);
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
                    System.out.println(ANSI_RED + ">>> EXPLOSION OCCURRED AT TICK " + t + " <<<" + ANSI_RESET);
                    System.out.println(ANSI_RED + "Cause: " + grid.getExplosionReason() + ANSI_RESET);
                } else if (grid.isHaltedByIncident()) {
                    System.out.println(ANSI_YELLOW + ">>> SIMULATION PAUSED DUE TO INCIDENT AT TICK " + t + " <<<" + ANSI_RESET);
                    System.out.println(ANSI_YELLOW + "Reason: " + grid.getLastHaltIncidentReason() + ANSI_RESET);
                } else if (grid.isPowerFailed()) {
                    System.out.println(ANSI_YELLOW + ">>> STRICT CALIBRATION DISQUALIFICATION AT TICK " + t + " <<<" + ANSI_RESET);
                    System.out.println(ANSI_YELLOW + "Cause: " + grid.getPowerFailReason() + ANSI_RESET);
                }
                break;
            }

            if (t % reportedInterval == 0 || t == ticks) {
                TurbineCalculator.PowerEstimationResult pr = grid.getLastPowerResult();
                double powerEUt = pr != null ? pr.totalPowerEUt : 0.0;
                String tierName = getVoltageTier(powerEUt);
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
        System.out.println(ANSI_GREEN + "Simulation complete in " + elapsedMs + " ms." + ANSI_RESET);
        System.out.println();

        if (grid.isNegativeTempDetected() || grid.isDiagnosticTraceEnabled()) {
            java.util.List<StandaloneNuclearGrid.IntermediateStepSnapshot> trace = grid.getStepTrace();
            if (!trace.isEmpty()) {
                System.out
                    .println(ANSI_YELLOW + "============================================================" + ANSI_RESET);
                System.out.println(
                    ANSI_YELLOW + "   DIAGNOSTIC INTERMEDIATE STEP TRACE ("
                        + trace.size()
                        + " steps in memory)"
                        + ANSI_RESET);
                System.out
                    .println(ANSI_YELLOW + "============================================================" + ANSI_RESET);
                for (StandaloneNuclearGrid.IntermediateStepSnapshot snap : trace) {
                    System.out.println(snap.toString());
                }
                System.out
                    .println(ANSI_YELLOW + "============================================================" + ANSI_RESET);
                System.out.println();
            }
        }

        printFinalReport(grid);
    }

    private static void printFinalReport(StandaloneNuclearGrid grid) {
        System.out.println(ANSI_CYAN + "=== FINAL SIMULATION TELEMETRY ===" + ANSI_RESET);
        System.out.println(
            "Status:                 "
                + (grid.isExploded() ? ANSI_RED + "DESTROYED (Meltdown)" : ANSI_GREEN + "STABLE / OPERATIONAL")
                + ANSI_RESET);
        System.out.println("Peak Core Temperature:  " + String.format("%.2f", grid.getCoreMaxTemp()) + " °C");
        System.out.println("Peak Lifetime Temp:     " + String.format("%.2f", grid.getPeakLifetimeTemp()) + " °C");
        if (grid.hasFuelBurned()) {
            System.out.println(
                ANSI_RED + "WARNING:                "
                    + grid.getBurnedFuelCount()
                    + " fuel rod(s) burned up due to excessive heat!"
                    + ANSI_RESET);
        }
        if (grid.getVoidedHatchCount() > 0) {
            System.out.println(
                ANSI_RED + "WARNING:                "
                    + grid.getVoidedHatchCount()
                    + " hatch(es) voided due to over-temperature!"
                    + ANSI_RESET);
        }
        System.out.println("Average Core Temp:      " + String.format("%.2f", grid.getCoreAvgTemp()) + " °C");
        System.out.println(
            "Casing Max Allowed:     " + NuclearSimulationEngine.getMaxOperatingTemperature(grid.getPipeTier())
                + " °C");
        if (grid.getReactorDamage() > 0.0) {
            System.out.println(
                ANSI_RED + "Reactor Damage:         "
                    + String.format(java.util.Locale.US, "%.1f %% (Issues: %d/6, Maint Efficiency: %.1f %%)",
                        grid.getReactorDamage(), grid.getMaintenanceIssues(), grid.getMaintenanceEfficiency() * 100.0)
                    + ANSI_RESET);
        } else {
            System.out.println(
                "Maintenance Health:     "
                    + String.format(java.util.Locale.US, "%.1f %% (%d issues)", grid.getMaintenanceEfficiency() * 100.0, grid.getMaintenanceIssues()));
        }
        System.out.println("Base Conductance:       " + String.format(java.util.Locale.US, "%.1f EU/(t·°C)", grid.getBaseHatchConductance()));
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
            System.out.println(ANSI_WHITE_BOLD + turbTitle + ANSI_RESET);
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
                "  Total Estimated Power:" + ANSI_GREEN
                    + String.format(" %.1f EU/t (%s)", p.totalPowerEUt, getVoltageTier(p.totalPowerEUt))
                    + ANSI_RESET);
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
                    "    - Radiovoltaic Direct EU: "
                        + String.format("%.1f EU/t (%s)", p.directPowerEUt, getVoltageTier(p.directPowerEUt)));
            }
            if (!"None".equals(p.coolantMachine)) {
                System.out.println(
                    "  Coolant Exchanger:    " + p.coolantMachine
                        + " ["
                        + p.coolantMachineMode
                        + "]"
                        + " (Produced: "
                        + String.format("%.1f L/t", p.coolantSteamProduced)
                        + " steam, Needed: "
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
            System.out.println(ANSI_CYAN + "=== HYPOTHETICAL OPERATIONAL SCENARIOS ===" + ANSI_RESET);
            System.out.println(String.format("  Scenario A [Tight / Max Efficiency]: %s", sc.tightConfig.description));
            System.out.println(
                String.format(
                    "    - Power Generation:    %.1f EU/t (%s)",
                    sc.tightResult.totalPowerEUt,
                    getVoltageTier(sc.tightResult.totalPowerEUt)));
            System.out.println(
                String.format(
                    "    - Turbines Needed:     %.2f turbines (Opt Flow: %.0f L/t)",
                    sc.tightResult.totalTurbinesNeeded,
                    sc.tightResult.optFlowPerTurbine));
            if (!"None".equals(sc.tightResult.coolantMachine)) {
                System.out.println(
                    String.format(
                        "    - Coolant Processing:  %.0f x %s",
                        sc.tightResult.coolantMachineCount,
                        sc.tightResult.coolantMachine));
            }
            System.out.println(String.format("  Scenario B [Loose / High Throughput]: %s", sc.looseConfig.description));
            System.out.println(
                String.format(
                    "    - Power Generation:    %.1f EU/t (%s)",
                    sc.looseResult.totalPowerEUt,
                    getVoltageTier(sc.looseResult.totalPowerEUt)));
            System.out.println(
                String.format(
                    "    - Turbines Needed:     %.2f turbines (Opt Flow: %.0f L/t)",
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
            System.out.println(ANSI_CYAN + "=== CONVECTIVE COOLANT LOOP TELEMETRY ===" + ANSI_RESET);
            System.out.println("  Loop Status:          " + (cl.isRuptured() ? ANSI_RED + "RUPTURED (" + cl.getRuptureReason() + ")" : ANSI_GREEN + "INTACT / OPERATIONAL") + ANSI_RESET);
            System.out.println("  Piping Material:      " + cl.getMaterial().displayName + " [" + cl.getMaterial().tierUnlocked + "]");
            System.out.println("  Internal Diameter:    " + cl.getPipeSize().displayName + " (" + (int)(cl.getPipeSize().diameterMeters * 1000) + " mm)");
            System.out.println("  Working Fluid:        " + cl.getFluidType().displayName);
            System.out.println("  Operating Flow Rate:  " + String.format(java.util.Locale.US, "%.2f L/s", cl.getCurrentFlowRateLPerSec()));
            double pressRatio = cl.getCurrentPressureBar() / cl.getMaterial().maxPressureBar * 100.0;
            String pressColor = pressRatio > 85.0 ? ANSI_RED : pressRatio > 60.0 ? ANSI_YELLOW : ANSI_GREEN;
            System.out.println("  Peak Loop Pressure:   " + pressColor + String.format(java.util.Locale.US, "%.1f bar / %.1f bar max (%.1f %%)", cl.getCurrentPressureBar(), cl.getMaterial().maxPressureBar, pressRatio) + ANSI_RESET);
            double tempRatio = cl.getCurrentCoolantTempCelsius() / cl.getMaterial().maxTemperatureCelsius * 100.0;
            String tempColor = tempRatio > 85.0 ? ANSI_RED : tempRatio > 60.0 ? ANSI_YELLOW : ANSI_GREEN;
            System.out.println("  Coolant Temperature:  " + tempColor + String.format(java.util.Locale.US, "%.1f °C / %.1f °C max (%.1f %%)", cl.getCurrentCoolantTempCelsius(), cl.getMaterial().maxTemperatureCelsius, tempRatio) + ANSI_RESET);
            System.out.println("  Heat Extracted:       " + String.format(java.util.Locale.US, "%.1f EU/t (%.2f MWth)", cl.getLastHeatExtractedEUt(), cl.getLastHeatExtractedWatts() / 1e6));
            System.out.println("  Secondary PHE Steam:  " + String.format(java.util.Locale.US, "%.1f L/t (%.0f L total, 1:160 expansion)", cl.getLastSecondarySteamProducedLt(), (double) cl.getTotalSecondarySteamProduced()));
            System.out.println("  Parasitic Pump Draw:  " + ANSI_YELLOW + String.format(java.util.Locale.US, "%.1f EU/t (Net: %.1f EU/t, Gross: %.1f EU/t)", cl.getLastPumpPowerEUt(), grid.getLastPowerResult() != null ? grid.getLastPowerResult().totalPowerEUt : 0.0, grid.getGrossPowerEUt()) + ANSI_RESET);
            if (cl.getFluidType() == CoolantLoopModel.CoolantFluidType.HEAVY_WATER) {
                System.out.println("  Tritium Radiolytic:   " + cl.getTotalTritiumProduced() + " L bred");
            } else {
                System.out.println("  Deuterium Radiolytic: " + cl.getTotalDeuteriumProduced() + " L bred");
            }
            if (!cl.getAttachedPoints().isEmpty()) {
                System.out.println("  Attached Core Cells:  " + cl.getAttachedPoints().size() + " points " + cl.getAttachedPoints());
            }
            System.out.println();
        }

        List<StandaloneNuclearGrid.SolidFuelByproduct> solidByproducts = grid.getSolidFuelByproducts();
        List<StandaloneNuclearGrid.LiquidFuelByproduct> liquidByproducts = grid.getLiquidFuelByproducts();
        List<StandaloneNuclearGrid.IsotopeByproduct> isotopeByproducts = grid.getIsotopeByproducts();
        if (!solidByproducts.isEmpty() || !liquidByproducts.isEmpty() || (grid.getTotalDeuteriumProduced() > 0 || grid.getTotalTritiumProduced() > 0)) {
            System.out.println(ANSI_GREEN + "=== FUEL BYPRODUCTS & LOGISTICS ===" + ANSI_RESET);
            System.out.println("  Auto-Refuel Mode:     " + (grid.isAutoReplaceFuel() ? ANSI_GREEN + "ENABLED (Continuous Cycling)" : ANSI_YELLOW + "DISABLED (Single Batch)") + ANSI_RESET);
            if (!solidByproducts.isEmpty()) {
                System.out.println(ANSI_WHITE_BOLD + "  Solid Fuel Rods (Depleted Item Output):" + ANSI_RESET);
                for (StandaloneNuclearGrid.SolidFuelByproduct s : solidByproducts) {
                    String lifespanStr = Double.isInfinite(s.avgLifespanMinutes) ? "Infinite" : String.format(java.util.Locale.US, "%.1f min", s.avgLifespanMinutes);
                    System.out.println(String.format(java.util.Locale.US,
                        "    • [%-4s] %-28s : %6.2f /min | %7.1f /h  (Active: %2d, Lifespan: %s, Total: %d)",
                        s.depletedCode, s.depletedName, s.itemsPerMinute, s.itemsPerHour, s.activeRods, lifespanStr, s.totalProduced));
                }
            }
            if (!liquidByproducts.isEmpty()) {
                System.out.println(ANSI_WHITE_BOLD + "  Liquid Fuel Hatches (Depleted Fluid Output):" + ANSI_RESET);
                for (StandaloneNuclearGrid.LiquidFuelByproduct l : liquidByproducts) {
                    System.out.println(String.format(java.util.Locale.US,
                        "    • %-32s : %8.1f L/min | %10.0f L/h  (Hatches: %2d, Total: %d L)",
                        l.displayName, l.litersPerMinute, l.litersPerHour, l.activeHatches, l.totalLiters));
                }
            }
            if (!isotopeByproducts.isEmpty() && (grid.getTotalDeuteriumProduced() > 0 || grid.getTotalTritiumProduced() > 0)) {
                System.out.println(ANSI_WHITE_BOLD + "  Transmuted Isotopes (Breeding Output):" + ANSI_RESET);
                for (StandaloneNuclearGrid.IsotopeByproduct iso : isotopeByproducts) {
                    System.out.println(String.format(java.util.Locale.US,
                        "    • %-10s (%-2s)                  : %8.2f L/min | %10.1f L/h  (Total: %d L)",
                        iso.name, iso.code, iso.litersPerMinute, iso.litersPerHour, iso.totalLiters));
                }
            }
            List<StandaloneNuclearGrid.RawMaterialBalance> rawBalances = grid.getRawMaterialBalances();
            if (!rawBalances.isEmpty()) {
                System.out.println(ANSI_CYAN + "  Base Raw Material Logistics & Net Balance Forecast:" + ANSI_RESET);
                for (StandaloneNuclearGrid.RawMaterialBalance r : rawBalances) {
                    String netColor = r.netPerMinute > 0.001 ? ANSI_GREEN : (r.netPerMinute < -0.001 ? ANSI_RED : ANSI_RESET);
                    String tag = r.netPerMinute > 0.001 ? "[SURPLUS / BREEDING]" : (r.netPerMinute < -0.001 ? "[NET DEFICIT / REQ]" : "[NEUTRAL]");
                    String netSign = r.netPerMinute > 0.001 ? "+" : "";
                    System.out.println(String.format(java.util.Locale.US,
                        "    • %-6s (%-16s) : %s%s%6.2f %s/min | %s%7.1f %s/h%s  (Req: %5.2f, Prod: %5.2f) %s",
                        r.code, r.name,
                        netColor, netSign, r.netPerMinute, r.unit,
                        netSign, r.netPerHour, r.unit, ANSI_RESET,
                        r.consumedPerMinute, r.producedPerMinute, tag));
                }
            }
            System.out.println();
        }

        System.out.println(ANSI_WHITE_BOLD + "Core Heat Map (°C):" + ANSI_RESET);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                double temp = grid.getTile(x, y)
                    .getTemperature();
                String color = (temp > 1500) ? ANSI_PURPLE
                    : (temp > 800) ? ANSI_RED : (temp > 300) ? ANSI_YELLOW : ANSI_GREEN;
                System.out.print(color + String.format("%5.0f ", temp) + ANSI_RESET);
            }
            System.out.println();
        }
        System.out.println();
    }

    private static String getVoltageTier(double eut) {
        if (eut <= 0) return "OFF";
        if (eut <= 32) return "LV";
        if (eut <= 128) return "MV";
        if (eut <= 512) return "HV";
        if (eut <= 2048) return "EV";
        if (eut <= 8192) return "IV";
        if (eut <= 32768) return "LuV";
        if (eut <= 131072) return "ZPM";
        if (eut <= 524288) return "UV";
        if (eut <= 2097152) return "UHV";
        if (eut <= 8388608) return "UEV";
        if (eut <= 33554432) return "UIV";
        if (eut <= 134217728) return "UMV";
        return "UXV+";
    }

    public static int parseTier(String t) {
        if (t == null) return NuclearSimulationEngine.PIPE_TIER_ELECTRUM;
        String s = t.toLowerCase()
            .trim();
        if (s.contains("elec") || s.equals("ev")) return NuclearSimulationEngine.PIPE_TIER_ELECTRUM;
        if (s.contains("plat") || s.equals("iv")) return NuclearSimulationEngine.PIPE_TIER_PLATINUM;
        if (s.contains("osmi") || s.equals("luv")) return NuclearSimulationEngine.PIPE_TIER_OSMIUM;
        if (s.contains("quan") || s.equals("zpm")) return NuclearSimulationEngine.PIPE_TIER_QUANTIUM;
        if (s.contains("flux") || s.equals("uv")) return NuclearSimulationEngine.PIPE_TIER_FLUXED_ELECTRUM;
        if (s.contains("plut") || s.equals("uhv")) return NuclearSimulationEngine.PIPE_TIER_BLACK_PLUTONIUM;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ignored) {}
        return NuclearSimulationEngine.PIPE_TIER_ELECTRUM;
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
