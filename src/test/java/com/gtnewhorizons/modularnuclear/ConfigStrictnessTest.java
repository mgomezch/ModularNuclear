package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gtnewhorizons.modularnuclear.common.config.ConfigException;
import com.gtnewhorizons.modularnuclear.common.config.CoolantLoopDataRegistry;
import com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry;

/** A config file that cannot be read, or holds a value of the wrong kind, stops loading and names the file. */
public class ConfigStrictnessTest {

    @AfterEach
    public void tearDown() {
        ReactorDataRegistry.loadDefaults();
        CoolantLoopDataRegistry.resetToDefaults();
    }

    private static void write(File dir, String name, String text) throws Exception {
        dir.mkdirs();
        Files.write(new File(dir, name).toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void reactorYamlSyntaxErrorNamesFileAndLine(@TempDir Path tempDir) throws Exception {
        File dir = tempDir.toFile();
        write(dir, "fuels.yaml", "fuels:\n  - id: x\n   bad: [\n");
        ConfigException e = assertThrows(ConfigException.class, () -> ReactorDataRegistry.loadFromDir(dir));
        assertTrue(
            e.getMessage()
                .contains("fuels.yaml"),
            e.getMessage());
        assertTrue(
            e.getMessage()
                .contains("line"),
            e.getMessage());
    }

    @Test
    public void reactorWrongValueTypeFails(@TempDir Path tempDir) throws Exception {
        File dir = tempDir.toFile();
        write(dir, "physics.yaml", "physics:\n  hu_per_degree: lots\n");
        ConfigException e = assertThrows(ConfigException.class, () -> ReactorDataRegistry.loadFromDir(dir));
        assertTrue(
            e.getMessage()
                .contains("physics.yaml"),
            e.getMessage());
        assertTrue(
            e.getMessage()
                .contains("hu_per_degree"),
            e.getMessage());
    }

    @Test
    public void reactorValidOverrideStillApplies(@TempDir Path tempDir) throws Exception {
        File dir = tempDir.toFile();
        write(dir, "physics.yaml", "physics:\n  hu_per_degree: 40\n");
        ReactorDataRegistry.loadFromDir(dir);
        assertEquals(40.0, ReactorDataRegistry.getPhysics().huPerDegree, 1e-9);
    }

    @Test
    public void reactorFileIsReadAsUtf8(@TempDir Path tempDir) throws Exception {
        File dir = tempDir.toFile();
        write(dir, "physics.yaml", "# Ünïcödé comment, D₂O\nphysics:\n  hu_per_degree: 40\n");
        ReactorDataRegistry.loadFromDir(dir);
        assertEquals(40.0, ReactorDataRegistry.getPhysics().huPerDegree, 1e-9);
    }

    @Test
    public void coolantYamlSyntaxErrorFails(@TempDir Path tempDir) throws Exception {
        File configDir = tempDir.toFile();
        write(new File(configDir, "coolantloops"), "fluids.yaml", "fluids:\n  water: {density: [\n");
        ConfigException e = assertThrows(ConfigException.class, () -> CoolantLoopDataRegistry.init(configDir));
        assertTrue(
            e.getMessage()
                .contains("fluids.yaml"),
            e.getMessage());
    }

    @Test
    public void coolantWrongValueTypeFails(@TempDir Path tempDir) throws Exception {
        File configDir = tempDir.toFile();
        write(new File(configDir, "coolantloops"), "fluids.yaml", "fluids:\n  water:\n    boilingPointCelsius: hot\n");
        ConfigException e = assertThrows(ConfigException.class, () -> CoolantLoopDataRegistry.init(configDir));
        assertTrue(
            e.getMessage()
                .contains("boilingPointCelsius"),
            e.getMessage());
    }
}
