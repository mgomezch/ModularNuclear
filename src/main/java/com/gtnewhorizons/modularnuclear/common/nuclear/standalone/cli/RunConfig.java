package com.gtnewhorizons.modularnuclear.common.nuclear.standalone.cli;

import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.CoolantLoopModel;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.TurbineCalculator;

/**
 * One simulator run's settings, as read from the command line or a batch line (see {@link SimOptions}). Settings left
 * null keep the grid's own defaults. Engine physics options are not held here: they set the engine directly.
 */
public final class RunConfig {

    /** What the command line asks for besides a run. */
    public enum Mode {
        TEXT,
        JSON,
        BATCH,
        WEB,
        HELP,
        TILE_CATALOG
    }

    public Mode mode = Mode.TEXT;

    public String preset;
    public String layout;
    public int size;
    public int ticks;
    /** Casing tier (see NuclearSimulationEngine.PIPE_TIER_*); null keeps the layout's or preset's. */
    public Integer tier;

    public TurbineCalculator.TurbineMaterial turbineMaterial = TurbineCalculator.TurbineMaterial.HSS_E;
    public TurbineCalculator.TurbineSize turbineSize = TurbineCalculator.TurbineSize.LARGE;
    public TurbineCalculator.FittingMode turbineFitting = TurbineCalculator.FittingMode.TIGHT;
    public boolean turbineSpecified = false;

    public CoolantLoopModel.CoolingMode coolingMode;
    /** Loop settings in the order given (see {@link LoopSettings}). */
    public final StringBuilder loopSettings = new StringBuilder();
    public String loopPoints;

    public Double baseConductance;
    public boolean repair = false;
    public boolean strict;
    public Boolean autoReplaceFuel;
    public Boolean stopOnIncidents;
    public final TurbineCalculator.MachineLimits limits = new TurbineCalculator.MachineLimits();

    public boolean steadyState = false;
    public int steadyWindow = 200;
    public boolean trackHistory = false;
    public boolean trace = false;
    public int traceSteps = 50;
    /** Seed for the simulation's random source; null leaves it unseeded. */
    public Long seed;

    private RunConfig(String preset, int size, int ticks, boolean strict) {
        this.preset = preset;
        this.size = size;
        this.ticks = ticks;
        this.strict = strict;
    }

    /** Defaults for a run from the command line. */
    public static RunConfig commandLine() {
        RunConfig c = new RunConfig("BASIC_ELECTRUM_5X5", 5, 100, false);
        c.tierDefault = NuclearSimulationEngine.PIPE_TIER_ELECTRUM;
        return c;
    }

    /** Defaults for each batch line. */
    public static RunConfig batchLine() {
        RunConfig c = new RunConfig("BREEDER_7X7", 7, 300, true);
        c.mode = Mode.JSON;
        c.tierDefault = NuclearSimulationEngine.PIPE_TIER_PLATINUM;
        return c;
    }

    /** Casing tier the grid is created with when none is given. */
    private int tierDefault;

    void addLoopSetting(String key, String value) {
        loopSettings.append(key)
            .append('=')
            .append(value)
            .append(',');
    }

    boolean hasLayout() {
        return layout != null && !layout.trim()
            .isEmpty();
    }

    /** Builds the grid this run simulates: casing, layout or preset, turbine, run options and coolant loops. */
    public StandaloneNuclearGrid buildGrid(boolean withTrace) {
        if (seed != null) NuclearSimulationEngine.setRandomSeed(seed);
        StandaloneNuclearGrid grid = new StandaloneNuclearGrid(size, size, tier != null ? tier : tierDefault);
        grid.setStrictMode(strict);
        grid.setMachineLimits(limits);
        if (autoReplaceFuel != null) grid.setAutoReplaceFuel(autoReplaceFuel);
        if (stopOnIncidents != null) grid.setStopOnIncidents(stopOnIncidents);
        if (baseConductance != null) grid.setBaseHatchConductance(baseConductance);
        if (repair) grid.repair();
        if (withTrace && trace) grid.enableDiagnosticTrace(traceSteps);
        if (hasLayout()) grid.loadLayout(layout);
        else grid.loadPreset(preset);
        if (tier != null) grid.setPipeTier(tier);
        if (turbineSpecified) {
            grid.setTurbineMaterial(turbineMaterial);
            grid.setTurbineSize(turbineSize);
            grid.setTurbineFitting(turbineFitting);
        }
        if (coolingMode != null && !grid.setCoolingMode(coolingMode)) {
            System.err.println(
                "WARNING: Convective cooling mode requires Tier 2+ (IV Platinum / LuV Osmium). Falling back to CONDUCTIVE.");
        }
        LoopSettings.configure(grid.getCoolantLoop(), loopSettings.toString(), loopPoints);
        return grid;
    }
}
