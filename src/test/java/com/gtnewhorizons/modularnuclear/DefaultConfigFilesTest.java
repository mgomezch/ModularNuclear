package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gtnewhorizons.modularnuclear.common.config.CoolantLoopConfigData;
import com.gtnewhorizons.modularnuclear.common.config.CoolantLoopDataRegistry;
import com.gtnewhorizons.modularnuclear.common.config.DefaultConfigFiles;
import com.gtnewhorizons.modularnuclear.common.config.DefaultConfigFiles.Mode;
import com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig;
import com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry;

/** See Note [Config files only override the built-in defaults] */
public class DefaultConfigFilesTest {

    private static final String FUELS = "modularnuclear/fuels.yaml";

    @AfterEach
    public void tearDown() {
        ReactorDataRegistry.loadDefaults();
        CoolantLoopConfigData.loadDefaults();
        ModularNuclearConfig.ticksPerCycle = 5;
    }

    private static byte[] resource(String path) throws Exception {
        String name = path.substring("modularnuclear/".length());
        try (InputStream in = DefaultConfigFilesTest.class
            .getResourceAsStream("/assets/modularnuclear/default_config/" + name)) {
            assertNotNull(in, name);
            return in.readAllBytes();
        }
    }

    private static String read(File dir, String path) throws Exception {
        return new String(Files.readAllBytes(new File(dir, path).toPath()), StandardCharsets.UTF_8);
    }

    private static void write(File dir, String path, String text) throws Exception {
        File file = new File(dir, path);
        file.getParentFile()
            .mkdirs();
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void testGeneratesEveryFileIntoAnEmptyDirectory(@TempDir Path tempDir) throws Exception {
        File dir = tempDir.toFile();
        DefaultConfigFiles.Result result = DefaultConfigFiles.generate(dir, Mode.IF_NONE_EXIST);

        assertFalse(result.refused);
        assertEquals(DefaultConfigFiles.paths(), result.written);
        assertEquals(10, result.written.size());
        for (String path : result.written) {
            assertTrue(new File(dir, path).isFile(), path);
            if (path.endsWith(".yaml")) {
                assertArrayEquals(resource(path), Files.readAllBytes(new File(dir, path).toPath()), path);
            }
        }
        assertTrue(read(dir, DefaultConfigFiles.FORGE_CONFIG).contains("I:ticksPerCycle=5"));
        assertTrue(read(dir, DefaultConfigFiles.README).contains("/modularnuclear config generate"));
    }

    @Test
    public void testWritesNothingWhenAnyFileExists(@TempDir Path tempDir) throws Exception {
        File dir = tempDir.toFile();
        write(dir, FUELS, "custom: true\n");

        DefaultConfigFiles.Result result = DefaultConfigFiles.generate(dir, Mode.IF_NONE_EXIST);

        assertTrue(result.refused);
        assertTrue(result.written.isEmpty());
        assertEquals(java.util.List.of(FUELS), result.existing);
        assertEquals("custom: true\n", read(dir, FUELS));
        assertEquals(java.util.List.of(FUELS), DefaultConfigFiles.existing(dir), "no other file may be written");
    }

    @Test
    public void testMissingOnlyLeavesExistingFilesAlone(@TempDir Path tempDir) throws Exception {
        File dir = tempDir.toFile();
        write(dir, FUELS, "custom: true\n");

        DefaultConfigFiles.Result result = DefaultConfigFiles.generate(dir, Mode.MISSING_ONLY);

        assertFalse(result.refused);
        assertEquals(
            DefaultConfigFiles.paths()
                .size() - 1,
            result.written.size());
        assertFalse(result.written.contains(FUELS));
        assertEquals("custom: true\n", read(dir, FUELS));
        assertEquals(DefaultConfigFiles.paths(), DefaultConfigFiles.existing(dir));
    }

    @Test
    public void testForceOverwritesExistingFiles(@TempDir Path tempDir) throws Exception {
        File dir = tempDir.toFile();
        write(dir, FUELS, "custom: true\n");
        write(dir, DefaultConfigFiles.FORGE_CONFIG, "simulation {\n    I:ticksPerCycle=9\n}\n");

        DefaultConfigFiles.Result result = DefaultConfigFiles.generate(dir, Mode.OVERWRITE);

        assertEquals(DefaultConfigFiles.paths(), result.written);
        assertArrayEquals(resource(FUELS), Files.readAllBytes(new File(dir, FUELS).toPath()));
        assertTrue(read(dir, DefaultConfigFiles.FORGE_CONFIG).contains("I:ticksPerCycle=5"));
    }

    @Test
    public void testGeneratedFilesLoadAsTheDefaults(@TempDir Path tempDir) throws Exception {
        File dir = tempDir.toFile();
        CoolantLoopConfigData.loadDefaults();
        double phe = CoolantLoopDataRegistry.getDeviceData()
            .getPhe().heatTransferMultiplier;
        ReactorDataRegistry.loadDefaults();
        int fuels = ReactorDataRegistry.getAllFuels()
            .size();
        String[] coolants = ModularNuclearConfig.allowedCoolantFluids.clone();

        DefaultConfigFiles.generate(dir, Mode.IF_NONE_EXIST);
        ReactorDataRegistry.init(dir);
        CoolantLoopDataRegistry.init(dir);
        ModularNuclearConfig.init(new File(dir, DefaultConfigFiles.FORGE_CONFIG));

        assertEquals(
            fuels,
            ReactorDataRegistry.getAllFuels()
                .size());
        assertEquals(
            phe,
            CoolantLoopDataRegistry.getDeviceData()
                .getPhe().heatTransferMultiplier,
            0.0);
        assertEquals(5, ModularNuclearConfig.ticksPerCycle);
        assertArrayEquals(coolants, ModularNuclearConfig.allowedCoolantFluids);
    }

    @Test
    public void testForgeConfigGetsBuiltInDefaultsNotTheSettingsInUse(@TempDir Path tempDir) throws Exception {
        File file = new File(tempDir.toFile(), DefaultConfigFiles.FORGE_CONFIG);
        ModularNuclearConfig.ticksPerCycle = 7;

        ModularNuclearConfig.writeDefaults(file);

        assertTrue(read(tempDir.toFile(), DefaultConfigFiles.FORGE_CONFIG).contains("I:ticksPerCycle=5"));
        assertEquals(7, ModularNuclearConfig.ticksPerCycle, "the settings in use must be left alone");
    }

    @Test
    public void testStartupWritesNoConfigFiles(@TempDir Path tempDir) throws Exception {
        File dir = tempDir.toFile();
        ModularNuclearConfig.init(new File(dir, DefaultConfigFiles.FORGE_CONFIG));
        ReactorDataRegistry.init(dir);
        CoolantLoopDataRegistry.init(dir);

        assertArrayEquals(new String[0], dir.list(), "startup must not write any file");
    }
}
