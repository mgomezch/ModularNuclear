package com.gtnewhorizons.modularnuclear.common.nuclear.standalone;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;

/**
 * Standalone web export utility providing static HTML generation
 * for visual nuclear reactor design and client-side WebAssembly simulation.
 */
public class NuclearSimulationWebServer {

    private static StandaloneNuclearGrid grid;

    public static void main(String[] args) {
        com.gtnewhorizons.modularnuclear.common.config.ReactorConfigData.loadDefaults();
        if (args.length >= 2 && "--export-html".equals(args[0])) {
            try {
                java.io.File file = new java.io.File(args[1]);
                file.getParentFile()
                    .mkdirs();
                NuclearSimulationEngine.setGlobalThermalFissionMultiplier(0.351);
                NuclearSimulationEngine.fissionHeatPerNeutron = 80.1;
                grid = new StandaloneNuclearGrid(9, 9, NuclearSimulationEngine.PIPE_TIER_PLATINUM);
                grid.loadPreset("BEST_PLATINUM_9X9");
                String presetsJson = loadCeilingPresetsJson(args);
                String html = getIndexHtml(webDir(args))
                    .replace("/*__INITIAL_STATE__*/", "window.__INITIAL_STATE__ = " + getStateJson() + ";")
                    .replace("/*__CEILING_PRESETS__*/", "window.__CEILING_PRESETS__ = " + presetsJson + ";")
                    .replace(
                        "/*__SECONDARY_COOLANTS__*/",
                        "window.__SECONDARY_COOLANTS__ = " + getSecondaryCoolantsJson() + ";")
                    .replace(
                        "/*__TURBINE_MATERIALS__*/",
                        "window.__TURBINE_MATERIALS__ = " + getTurbineMaterialsJson() + ";");
                java.nio.file.Files.write(file.toPath(), html.getBytes(StandardCharsets.UTF_8));
                System.out.println("Exported static simulator HTML to " + file.getAbsolutePath());
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
            return;
        }
        System.out.println("The Java server version of the simulator webapp has been retired.");
        System.out.println("The simulator is now an entirely client-side WebAssembly application.");
        System.out.println(
            "Usage: --export-html <path/to/index.html> [--presets <path/to/nuclear_ceiling_best.json>] [--web-dir <page sources>]");
    }

    private static String loadCeilingPresetsJson(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--presets".equals(args[i])) {
                java.io.File f = new java.io.File(args[i + 1]);
                if (f.exists() && f.isFile()) {
                    try {
                        return new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
                    } catch (java.io.IOException e) {
                        System.err.println("Cannot read presets " + f + ": " + e);
                    }
                }
            }
        }
        String[] candidatePaths = new String[] { "../../tools/nuclear_ceiling_best.json",
            "../tools/nuclear_ceiling_best.json", "tools/nuclear_ceiling_best.json" };
        for (String p : candidatePaths) {
            java.io.File f = new java.io.File(p);
            if (f.exists() && f.isFile()) {
                try {
                    return new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
                } catch (java.io.IOException e) {
                    System.err.println("Cannot read presets " + f + ": " + e);
                }
            }
        }
        return "[]";
    }

    public static String getStateJson() {
        return NuclearSimWasmBridge.buildStateJson(grid, false);
    }

    public static String getSecondaryCoolantsJson() {
        return NuclearSimWasmBridge.secondaryCoolantsJson();
    }

    public static String getTurbineMaterialsJson() {
        StringBuilder sb = new StringBuilder("[");
        int i = 0;
        for (TurbineCalculator.TurbineMaterial m : TurbineCalculator.TurbineMaterial.values()) {
            if (i++ > 0) sb.append(",");
            sb.append("{\"id\":\"")
                .append(m.name())
                .append("\",\"name\":\"")
                .append(m.displayName)
                .append(" (")
                .append((int) Math.round(m.tightEff * 100))
                .append("% / ")
                .append((int) Math.round(m.looseEff * 100))
                .append("%)\",\"tightEff\":")
                .append(m.tightEff)
                .append(",\"looseEff\":")
                .append(m.looseEff)
                .append(",\"optFlowLargeTight\":")
                .append(m.optFlowLargeTight)
                .append(",\"optFlowLargeLoose\":")
                .append(m.optFlowLargeLoose)
                .append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    /** Directory of the page sources: --web-dir, or the source tree when run from the project. */
    private static Path webDir(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--web-dir".equals(args[i])) return Paths.get(args[i + 1]);
        }
        return Paths.get("src/main/web/simulator");
    }

    /**
     * The page: {@code index.html} with every {@code <!-- include: name -->} line replaced by that file's contents
     * (the stylesheet and scripts, inlined so the page is a single file).
     */
    static String getIndexHtml(Path dir) throws IOException {
        StringBuilder out = new StringBuilder();
        for (String line : Files.readAllLines(dir.resolve("index.html"), StandardCharsets.UTF_8)) {
            Matcher m = INCLUDE.matcher(line);
            if (m.matches()) {
                out.append(new String(Files.readAllBytes(dir.resolve(m.group(1))), StandardCharsets.UTF_8));
            } else {
                out.append(line)
                    .append('\n');
            }
        }
        return out.toString();
    }

    private static final Pattern INCLUDE = Pattern.compile("<!-- include: (\\S+) -->");
}
