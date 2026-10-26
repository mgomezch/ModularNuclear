package com.gtnewhorizons.modularnuclear;

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

/**
 * The simulation engine, tile physics and configuration core are shared between the game and the WebAssembly
 * simulator. They must not reach into Minecraft, Forge, GregTech, IC2, SnakeYAML or the game-side
 * ReactorDataRegistry: such a reference either breaks the TeaVM build or, worse, makes the simulator silently run
 * different code (for example ReactorDataRegistry's static initialiser reloading the config).
 */
public class SharedCodeIsolationTest {

    private static final Path BASE = Paths.get("src/main/java/com/gtnewhorizons/modularnuclear/common");

    /** Game-only files that live next to shared code. */
    private static final List<String> GAME_ONLY = Arrays.asList(
        "NuclearSimulationCLI.java",
        "NuclearSimulationWebServer.java",
        "CustomFuelMix.java",
        "ReactorGridSyncData.java");

    private static final Pattern FORBIDDEN = Pattern
        .compile("ReactorDataRegistry|net\\.minecraft|cpw\\.mods|gregtech\\.|ic2\\.|org\\.yaml|ModularNuclear\\.LOG");

    /** Known remaining coupling: the engine reads per-tier temperature limits from the Forge config defaults. */
    private static final Pattern ALLOWED = Pattern.compile("common\\.config\\.ModularNuclearConfig");

    @Test
    void testSharedCodeHasNoGameDependencies() throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> s = Files.walk(BASE.resolve("config/data"))) {
            s.forEach(files::add);
        }
        files.add(BASE.resolve("config/ReactorConfigData.java"));
        files.add(BASE.resolve("config/MiniJson.java"));
        try (Stream<Path> s = Files.list(BASE.resolve("nuclear"))) {
            s.forEach(files::add);
        }
        try (Stream<Path> s = Files.walk(BASE.resolve("nuclear/standalone"))) {
            s.forEach(files::add);
        }

        List<String> offenders = new ArrayList<>();
        for (Path f : files) {
            String name = f.getFileName()
                .toString();
            if (!name.endsWith(".java") || GAME_ONLY.contains(name)) continue;
            List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i)
                    .trim();
                if (line.startsWith("*") || line.startsWith("/*") || line.startsWith("//")) continue;
                if (FORBIDDEN.matcher(line)
                    .find()
                    && !ALLOWED.matcher(line)
                        .find()) {
                    offenders.add(f + ":" + (i + 1) + ": " + line);
                }
            }
        }
        assertTrue(offenders.isEmpty(), "Shared code references game-only code:\n" + String.join("\n", offenders));
    }
}
