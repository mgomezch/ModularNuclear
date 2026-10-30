package com.gtnewhorizons.modularnuclear.common.nuclear.standalone;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.gtnewhorizons.modularnuclear.common.config.CoolantLoopConfigData;
import com.gtnewhorizons.modularnuclear.common.config.ReactorConfigData;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.cli.CliHelp;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.cli.JsonReport;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.cli.OptionTable;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.cli.RunConfig;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.cli.SimOptions;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.cli.TextReport;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.cli.TileCatalog;

/**
 * Command-line simulator for reactor designs, running the game's own engine outside the game: one run from the
 * command line (as a text report or JSON), or, with --batch, one JSON result per line of {@code key=value} options read
 * from standard input. The options are listed in {@link SimOptions}.
 */
public class NuclearSimulationCLI {

    public static void main(String[] args) {
        ReactorConfigData.loadDefaults();
        RunConfig cfg = RunConfig.commandLine();
        SimOptions.table(false)
            .parseArgs(cfg, args);
        switch (cfg.mode) {
            case HELP -> CliHelp.print();
            case TILE_CATALOG -> System.out.println(TileCatalog.json());
            case WEB -> {
                System.out.println("The Java server version of the simulator webapp has been retired.");
                System.out.println("The simulator is now a static WebAssembly application accessible at:");
                System.out.println("  https://nuclear.maderita.mgomez.ch");
                System.out.println("or locally at build/nuclear-sim-dist/index.html");
            }
            case BATCH -> runBatch();
            case JSON -> JsonReport.run(cfg);
            case TEXT -> TextReport.run(cfg);
        }
    }

    /** Runs one simulation per line of standard input until an empty line, "exit" or "quit". */
    private static void runBatch() {
        OptionTable<RunConfig> options = SimOptions.table(true);
        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) break;
                // Every line starts from the game's defaults (engine constants, then physics.yaml and the fuel and
                // component records), so a parameter a line does not set never carries over from an earlier line.
                NuclearSimulationEngine.resetDefaultParameters();
                CoolantLoopConfigData.loadDefaults();
                RunConfig cfg = RunConfig.batchLine();
                options.parseTokens(cfg, line, "\\s+");
                JsonReport.run(cfg);
                System.out.flush();
            }
        } catch (IOException e) {
            System.err.println("Batch error: " + e.getMessage());
        }
    }
}
