package com.gtnewhorizons.modularnuclear.common.nuclear.standalone.cli;

/** The command-line usage text. */
public final class CliHelp {

    private CliHelp() {}

    public static void print() {
        System.out.println(CliFormat.WHITE_BOLD + "GTNH Nuclear Simulation CLI & Diagnostic Tool" + CliFormat.RESET);
        System.out.println("Usage: gtnh-nuclear-sim [options]");
        System.out.println("Options:");
        System.out.println(
            "  --preset <name>       Preset layout: BEST_ELECTRUM_5X5, BEST_PLATINUM_7X7, BEST_OSMIUM_9X9, BEST_QUANTIUM_9X9, BEST_FLUXED_9X9, BEST_PLUTONIUM_9X9");
        System.out.println("  --size <N>            Grid dimensions N x N (default 5)");
        System.out.println(
            "  --tier <name/#>       Pipe casing tier: electrum(0), platinum(1), osmium(2), quantium(3), fluxed(4), black_plutonium(5)");
        System.out.println("  --ticks <N>           Simulation duration in ticks (default 100)");
        System.out
            .println("  --seed <N>            Seed the simulation's random source, so the run can be repeated exactly");
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
        System.out.println("  --cooling-mode <mode> Cooling mode: CONDUCTIVE, CONVECTIVE_LOOP (default CONDUCTIVE)");
        System.out.println("  --base-conductance <U> Base hatch conductance in EU/(t·°C) (default 32.0)");
        System.out.println("  --repair              Repair all reactor damage and clear maintenance issues");
        System.out.println(
            "  --strict              Fail-fast on thermal shock, casing overheat, or fuel burnup (calibration mode)");
        System.out.println("  --no-strict           Disable fail-fast (accumulate damage and maintenance issues)");
        System.out
            .println("  --auto-refuel         Automatically replace spent fuel rods for continuous run (default true)");
        System.out.println("  --no-auto-refuel      Leave spent fuel rods depleted without replacement");
        System.out.println("  --stop-on-incidents   Pause simulation automatically when an incident/damage occurs");
        System.out.println("  --no-stop-on-incidents Do not pause on incidents (continue accumulating damage)");
        System.out.println(
            "  --auto-supply-fuel    Automatically replace spent fuel and replenish liquid fuel (default true)");
        System.out.println("  --no-auto-supply-fuel Disable automatic fuel replacement and replenishment");
        System.out.println(
            "  --loop-material <mat> Coolant loop material: STEEL, STAINLESS_STEEL, TITANIUM, TUNGSTENSTEEL, NEUTRONIUM");
        System.out.println(
            "  --loop-size <size>    Coolant loop pipe size: TINY, SMALL, NORMAL, LARGE, HUGE (default NORMAL)");
        System.out.println(
            "  --loop-fluid <fluid>  Coolant loop fluid: DISTILLED_WATER, HEAVY_WATER (default DISTILLED_WATER)");
        System.out.println(
            "  --loop-power <EU/t>   Coolant loop pump power in EU/t (calculates flow from hydrodynamic polynomial)");
        System.out.println("  --loop-flow <L/s>     Coolant loop target circulation flow in L/s");
        System.out.println(
            "  --loop-points <pts>   Semicolon-separated core coordinates to attach to loop (e.g. \"2,2;2,3;2,4\")");
        System.out.println(
            "  --trace [N]           Enable intermediate step snapshot ring buffer (up to N steps, default 500)");
        System.out.println("  --json                Output compact JSON summary for automated test scripts");
        System.out.println("  --web [port]          Launch standalone Web GUI on specified port (default 8085)");
        System.out.println("  --help, -h            Display this help message");
    }
}
