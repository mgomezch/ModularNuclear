package com.gtnewhorizons.modularnuclear.common.nuclear.standalone.cli;

import static com.gtnewhorizons.modularnuclear.common.nuclear.standalone.cli.OptionTable.DOUBLE;
import static com.gtnewhorizons.modularnuclear.common.nuclear.standalone.cli.OptionTable.INT;
import static com.gtnewhorizons.modularnuclear.common.nuclear.standalone.cli.OptionTable.STRING;

import java.util.Locale;
import java.util.function.DoubleConsumer;

import com.gtnewhorizons.modularnuclear.common.config.CoolantLoopConfigData;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.CoolantLoopModel;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneConfig;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.TurbineCalculator;

/**
 * Every option a simulator run takes, for both the command line ({@code --fission-heat 2600}) and batch lines
 * ({@code fission_heat=2600}). Physics options set the engine as they are read; the rest fill a {@link RunConfig}.
 * Loop settings (see {@link LoopSettings}) are accepted too and kept in order.
 */
public final class SimOptions {

    private SimOptions() {}

    /** An option that sets an engine parameter. */
    private static OptionTable<RunConfig> engine(OptionTable<RunConfig> t, DoubleConsumer set, String... names) {
        return t.value(DOUBLE, (c, v) -> set.accept(v), names);
    }

    /** A new option table (batch lines pass quiet to keep malformed values silent, as stderr is not read). */
    public static OptionTable<RunConfig> table(boolean quiet) {
        OptionTable<RunConfig> t = new OptionTable<>();
        if (quiet) t.onBadValue((name, value) -> {});

        // What to do
        t.action(c -> c.mode = RunConfig.Mode.BATCH, "batch")
            .action(c -> c.mode = RunConfig.Mode.JSON, "json")
            .action(c -> c.mode = RunConfig.Mode.HELP, "help", "h")
            .action(c -> c.mode = RunConfig.Mode.TILE_CATALOG, "tile_catalog")
            .optionalValue(INT, c -> c.mode = RunConfig.Mode.WEB, (c, port) -> {}, "web");

        // The reactor
        t.value(STRING, (c, v) -> c.preset = v, "preset")
            .value(STRING, (c, v) -> c.layout = v, "layout")
            .value(INT, (c, v) -> c.size = v, "size")
            .value(SimOptions::parseTier, (c, v) -> c.tier = v, "tier")
            .value(INT, (c, v) -> c.ticks = v, "ticks")
            .value(OptionTable.LONG, (c, v) -> c.seed = v, "seed")
            .value(INT, (c, v) -> NuclearSimulationEngine.setTicksPerCycle(v), "ticks_per_cycle")
            .value(s -> TurbineCalculator.TurbineMaterial.fromString(s), (c, v) -> {
                c.turbineMaterial = v;
                c.turbineSpecified = true;
            }, "turb_mat", "material")
            .value(s -> TurbineCalculator.TurbineSize.fromString(s), (c, v) -> {
                c.turbineSize = v;
                c.turbineSpecified = true;
            }, "turb_size")
            .value(s -> TurbineCalculator.FittingMode.fromString(s), (c, v) -> {
                c.turbineFitting = v;
                c.turbineSpecified = true;
            }, "turb_fit", "fitting")
            .value(DOUBLE, (c, v) -> c.baseConductance = v, "base_conductance", "conductance");

        // Physics
        engine(t, v -> NuclearSimulationEngine.tempThresholdLow = v, "temp_low");
        engine(t, v -> NuclearSimulationEngine.tempThresholdHigh = v, "temp_high");
        engine(t, v -> NuclearSimulationEngine.reactivityPower = v, "reactivity_pow");
        engine(t, v -> NuclearSimulationEngine.thermalFissionMultiplier = v, "fission_mult");
        engine(t, v -> NuclearSimulationEngine.globalThermalFissionMultiplier = v, "global_fission_mult");
        engine(t, v -> NuclearSimulationEngine.globalFastFissionMultiplier = v, "global_fast_fission_mult");
        engine(
            t,
            v -> NuclearSimulationEngine.fastAbsorptionDamageMultiplier = v,
            "fast_damage_mult",
            "fast_absorption_damage_mult");
        engine(
            t,
            v -> NuclearSimulationEngine.thermalAbsorptionDamageMultiplier = v,
            "thermal_damage_mult",
            "thermal_absorption_damage_mult");
        engine(t, NuclearSimulationEngine::setHuPerDegree, "hu_per_degree");
        engine(
            t,
            NuclearSimulationEngine::setGridConductionMultiplier,
            "grid_conduction_mult",
            "grid_conduction_multiplier");
        engine(t, v -> NuclearSimulationEngine.denseNbaseMultiplier = v, "dense_nbase_mult", "dense_nbase_multiplier");
        engine(
            t,
            v -> NuclearSimulationEngine.denseFissionMultiplier = v,
            "dense_fission_mult",
            "dense_fission_multiplier");
        engine(
            t,
            v -> NuclearSimulationEngine.excitedNbaseMultiplier = v,
            "excited_nbase_mult",
            "excited_nbase_multiplier");
        engine(
            t,
            v -> NuclearSimulationEngine.excitedFissionMultiplier = v,
            "excited_fission_mult",
            "excited_fission_multiplier");
        engine(
            t,
            v -> NuclearSimulationEngine.standardNbaseMultiplier = v,
            "standard_nbase_mult",
            "standard_nbase_multiplier");
        engine(
            t,
            v -> NuclearSimulationEngine.standardFissionMultiplier = v,
            "standard_fission_mult",
            "standard_fission_multiplier");
        engine(
            t,
            v -> NuclearSimulationEngine.breederNbaseMultiplier = v,
            "breeder_nbase_mult",
            "breeder_nbase_multiplier");
        engine(
            t,
            v -> NuclearSimulationEngine.breederFissionMultiplier = v,
            "breeder_fission_mult",
            "breeder_fission_multiplier");
        engine(t, v -> NuclearSimulationEngine.fissionHeatPerNeutron = v, "fission_heat");
        engine(t, v -> NuclearSimulationEngine.hpWaterBoilingPoint = v, "hp_boil");
        t.value(INT, (c, v) -> NuclearSimulationEngine.hatchCoolantCapacity = v, "hatch_cap");
        t.value(
            s -> NuclearSimulationEngine.TurnoverCurve.fromString(s),
            (c, v) -> NuclearSimulationEngine.turnoverCurve = v,
            "turnover_curve");
        engine(t, v -> NuclearSimulationEngine.turnoverDeltaTMax = v, "turnover_dt");
        engine(t, v -> NuclearSimulationEngine.turnoverExponent = v, "turnover_exp", "turnover_pow");
        t.value(INT, (c, v) -> NuclearSimulationEngine.coolantFeedRate = v, "feed_rate");
        engine(t, StandaloneConfig::setWaterHeatPerLiter, "cooling_heat");
        engine(t, NuclearSimulationEngine::setAmbientTemperature, "ambient_temp", "ambient");
        engine(t, StandaloneConfig::setIc2CoolantHeatPerLiter, "ic2_heat", "ic2_cooling_heat");
        engine(t, v -> NuclearSimulationEngine.fuelBurnupMultiplier = v, "burnup_mult");
        engine(t, v -> NuclearSimulationEngine.coolantPassageHeatTransferMultiplier = v, "passage_htc_mult");
        engine(
            t,
            v -> CoolantLoopConfigData.getPhysicsData()
                .setPumpMechanicalPowerPerEU(v),
            "pump_w_per_eu");
        engine(
            t,
            v -> CoolantLoopConfigData.getDeviceData()
                .getPhe().exchangeArea = v,
            "phe_area");

        // Run behaviour
        t.toggle((c, v) -> c.repair = v, "repair")
            .toggle((c, v) -> c.strict = v, "strict", "strict_mode")
            .toggle((c, v) -> c.stopOnIncidents = v, "stop_on_incidents", "stop_incidents")
            .toggle((c, v) -> c.autoReplaceFuel = v, "auto_supply_fuel", "auto_refuel", "auto_replace_fuel")
            .toggle((c, v) -> c.steadyState = v, "steady_state")
            .value(INT, (c, v) -> {
                c.steadyWindow = v;
                c.steadyState = true;
            }, "steady_window")
            .toggle((c, v) -> c.trackHistory = v, "history", "track_history")
            .optionalValue(INT, c -> c.trace = true, (c, v) -> c.traceSteps = v, "trace");

        // Machine limits (-1 = unlimited)
        t.value(INT, (c, v) -> c.limits.maxThermalBoilers = v, "max_thermal_boilers", "max_boilers")
            .value(INT, (c, v) -> c.limits.maxLhe = v, "max_lhe")
            .value(INT, (c, v) -> c.limits.maxEhe = v, "max_ehe")
            .value(INT, (c, v) -> c.limits.maxLst = v, "max_lst")
            .value(INT, (c, v) -> c.limits.maxXlst = v, "max_xlst")
            .value(INT, (c, v) -> c.limits.maxXlstHp = v, "max_xlst_hp")
            .value(INT, (c, v) -> c.limits.maxXlstSc = v, "max_xlst_sc")
            .value(INT, (c, v) -> c.limits.maxTurbines = v, "max_turbines");

        // Cooling and coolant loops
        t.value(s -> CoolantLoopModel.CoolingMode.fromString(s), (c, v) -> c.coolingMode = v, "cooling_mode", "cooling")
            .value(
                STRING,
                (c, v) -> c.loopSettings.append(v)
                    .append(','),
                "loop_options")
            .value(STRING, (c, v) -> c.loopPoints = v, "loop_points");
        for (String name : LoopSettings.TABLE.names()) {
            t.value(STRING, (c, v) -> c.addLoopSetting(name, v), name);
        }
        return t;
    }

    /** A casing tier by material, voltage tier or number (EV/Electrum = 0 ... UHV/Black Plutonium = 5). */
    public static int parseTier(String text) {
        String s = text.toLowerCase(Locale.ROOT)
            .trim();
        switch (s) {
            case "electrum", "ev" -> {
                return NuclearSimulationEngine.PIPE_TIER_ELECTRUM;
            }
            case "platinum", "iv" -> {
                return NuclearSimulationEngine.PIPE_TIER_PLATINUM;
            }
            case "osmium", "luv" -> {
                return NuclearSimulationEngine.PIPE_TIER_OSMIUM;
            }
            case "quantium", "zpm" -> {
                return NuclearSimulationEngine.PIPE_TIER_QUANTIUM;
            }
            case "fluxed", "fluxed_electrum", "uv" -> {
                return NuclearSimulationEngine.PIPE_TIER_FLUXED_ELECTRUM;
            }
            case "black_plutonium", "plutonium", "uhv" -> {
                return NuclearSimulationEngine.PIPE_TIER_BLACK_PLUTONIUM;
            }
            default -> {
                try {
                    return Integer.parseInt(s);
                } catch (NumberFormatException e) {
                    return NuclearSimulationEngine.PIPE_TIER_ELECTRUM;
                }
            }
        }
    }
}
