package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.WasmStrings;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.CoolantLoopModel;

public class WasmStringsTest {

    @Test
    void testReplaceMatchesStringReplace() {
        String[][] cases = { { "1.23e+05 n", "+0", "" }, { "1.23e+05 n", "+", "" }, { "excitedstate", "state", "" },
            { "aaaa", "aa", "b" }, { "no match here", "xyz", "q" }, { "", "a", "b" }, { "edge", "edge", "" },
            { "a-b-c", "-", "_" }, { "x", "x", "xx" } };
        for (String[] c : cases) {
            assertEquals(c[0].replace(c[1], c[2]), WasmStrings.replace(c[0], c[1], c[2]), Arrays.toString(c));
        }
        String s = "unchanged";
        assertSame(s, WasmStrings.replace(s, "zz", "y"), "No match returns the same instance");
    }

    @Test
    void testNormalizeToken() {
        assertEquals("CAST_IRON", WasmStrings.normalizeToken("  cast-iron ", " -"));
        assertEquals("FLUID_HEAVYWATER", WasmStrings.normalizeToken("fluid.heavywater", " ."));
        assertEquals("CLOSED_LOOP", WasmStrings.normalizeToken("closed loop", " "));
        assertEquals("A-B", WasmStrings.normalizeToken("a-b", " "), "Characters not listed are kept");
    }

    @Test
    void testParsersStillAcceptSeparatorVariants() {
        assertEquals(CoolantLoopModel.LoopMaterial.CAST_IRON, CoolantLoopModel.LoopMaterial.fromString("cast-iron"));
        assertEquals(CoolantLoopModel.LoopMaterial.CAST_IRON, CoolantLoopModel.LoopMaterial.fromString("Cast Iron"));
        assertEquals(
            CoolantLoopModel.CoolingMode.CONVECTIVE_LOOP,
            CoolantLoopModel.CoolingMode.fromString("closed loop"));
        assertEquals(CoolantLoopModel.LoopPipeSize.LARGE, CoolantLoopModel.LoopPipeSize.fromString("pipe large"));
        assertEquals(
            CoolantLoopModel.CoolantFluidType.HEAVY_WATER,
            CoolantLoopModel.CoolantFluidType.fromString("heavy water"));
        assertEquals(
            CoolantLoopModel.CoolantFluidType.SODIUM,
            CoolantLoopModel.CoolantFluidType.fromString("molten.sodium"));
    }

    @Test
    void testFluxFormattingDropsExponentSign() {
        // 1 neutron/cycle at 5 ticks/cycle = 4e13 n/cm²/s
        assertEquals("4.00e13 neutrons/cm²/sec", NuclearSimulationEngine.formatNeutronFlux(1.0, 5));
    }

    /**
     * TeaVM 0.10.2 does not compile String.replace for WebAssembly; any call reachable from the simulator traps at
     * runtime ("table index is out of bounds"). Code compiled into nuclear-sim.wasm must use WasmStrings instead.
     */
    @Test
    void testNoStringReplaceInWasmReachableSources() throws IOException {
        Path base = Paths.get("src/main/java/com/gtnewhorizons/modularnuclear/common");
        List<Path> roots = new ArrayList<>(Arrays.asList(base.resolve("nuclear"), base.resolve("config")));
        roots.addAll(SharedCodeIsolationTest.sharedLoopSources());
        // JVM-only entry points (and the command-line simulator's cli package): never compiled to WebAssembly
        List<String> jvmOnly = Arrays.asList("NuclearSimulationCLI.java", "NuclearSimulationWebServer.java");
        Path cliPackage = base.resolve("nuclear/standalone/cli");
        Pattern call = Pattern.compile("\\.replace(All|First)?\\s*\\(");
        List<String> offenders = new ArrayList<>();
        for (Path root : roots) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path f : (Iterable<Path>) files.filter(
                    p -> p.toString()
                        .endsWith(".java"))::iterator) {
                    String name = f.getFileName()
                        .toString();
                    if (jvmOnly.contains(name) || name.equals("WasmStrings.java") || f.startsWith(cliPackage)) continue;
                    List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i)
                            .replace("WasmStrings.replace(", "");
                        if (call.matcher(line)
                            .find()) {
                            offenders.add(
                                f + ":"
                                    + (i + 1)
                                    + ": "
                                    + lines.get(i)
                                        .trim());
                        }
                    }
                }
            }
        }
        assertTrue(
            offenders.isEmpty(),
            "Use WasmStrings instead of String.replace in:\n" + String.join("\n", offenders));
    }
}
