package com.gtnewhorizons.modularnuclear.common.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import com.gtnewhorizons.modularnuclear.common.config.data.CoolantDeviceData;
import com.gtnewhorizons.modularnuclear.common.config.data.CoolantFluidData;
import com.gtnewhorizons.modularnuclear.common.config.data.CoolantPhysicsData;
import com.gtnewhorizons.modularnuclear.common.config.data.CoolantPipeData;
import com.gtnewhorizons.modularnuclear.common.coolantloop.ConvectiveHeatTransferModel;

/**
 * Central data registry for coolant loops, providing structured YAML-backed
 * configuration of hydrodynamic constants, fluid thermodynamic properties,
 * piping limits, and multiblock device parameters.
 */
public class CoolantLoopDataRegistry {

    private static final Logger LOG = LogManager.getLogger("CoolantLoopDataRegistry");
    private static File configFolder;

    /**
     * Loads the built-in defaults, then any override files in {@code config/modularnuclear/coolant_loops}. Nothing is
     * written there: a copy of the defaults would freeze them, so later builds' defaults would never take effect.
     */
    public static synchronized void init(File configDirectory) {
        configFolder = new File(configDirectory, "modularnuclear/coolant_loops");
        loadFromDir();
        LOG.info("Coolant loop YAML Data Registry initialized successfully.");
    }

    /** The config files, in the order they are applied. */
    public static final String[] FILES = { "physics.yaml", "fluids.yaml", "pipes.yaml", "devices.yaml" };

    /** Loads the configuration directory's files again (after a server's configuration was in use). */
    public static synchronized void loadFromDir() {
        CoolantLoopConfigData.loadDefaults();
        loadFile("physics.yaml", CoolantLoopConfigData::setPhysics);
        loadFile("fluids.yaml", CoolantLoopConfigData::setFluids);
        loadFile("pipes.yaml", CoolantLoopConfigData::setPipes);
        loadFile("devices.yaml", CoolantLoopConfigData::setDevices);
        CoolantLoopConfigData.finishLoading();
    }

    /** Loads another machine's config files (name to YAML text), e.g. the server's. */
    public static synchronized void loadFromTexts(Map<String, String> texts) {
        CoolantLoopConfigData.loadDefaults();
        applyText(texts, "physics.yaml", CoolantLoopConfigData::setPhysics);
        applyText(texts, "fluids.yaml", CoolantLoopConfigData::setFluids);
        applyText(texts, "pipes.yaml", CoolantLoopConfigData::setPipes);
        applyText(texts, "devices.yaml", CoolantLoopConfigData::setDevices);
        CoolantLoopConfigData.finishLoading();
    }

    /** The configuration directory's files (name to YAML text), as {@link #loadFromTexts} takes them. */
    public static synchronized Map<String, String> fileTexts() {
        Map<String, String> texts = new java.util.LinkedHashMap<>();
        if (configFolder == null) return texts;
        for (String name : FILES) {
            File file = new File(configFolder, name);
            if (!file.exists()) continue;
            try {
                texts.put(name, new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            } catch (java.io.IOException e) {
                throw ConfigException.inFile(file, e);
            }
        }
        return texts;
    }

    private static void applyText(Map<String, String> texts, String name, Consumer<Object> applier) {
        String text = texts.get(name);
        if (text == null) return;
        try {
            Object doc = createYaml().load(text);
            if (doc != null && !(doc instanceof Map)) {
                throw new ConfigException("expected a map of settings, got " + doc);
            }
            applier.accept(doc);
        } catch (RuntimeException e) {
            throw ConfigException.in("the server's " + name, e);
        }
    }

    private static Yaml createYaml() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        return new Yaml(options);
    }

    /**
     * Applies one override file, read as UTF-8, on top of the defaults; a missing file overrides nothing. A file that
     * cannot be read or applied stops loading.
     */
    private static void loadFile(String name, Consumer<Object> applier) {
        File file = new File(configFolder, name);
        if (!file.exists()) return;
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Object doc = createYaml().load(reader);
            if (doc != null && !(doc instanceof Map)) {
                throw new ConfigException("expected a map of settings, got " + doc);
            }
            applier.accept(doc);
        } catch (Exception e) {
            throw ConfigException.inFile(file, e);
        }
    }

    public static synchronized void resetToDefaults() {
        CoolantLoopConfigData.resetToClassDefaults();
    }

    // Lookup APIs (shared with the simulator through CoolantLoopConfigData)

    public static ConvectiveHeatTransferModel getConvectiveModel() {
        return CoolantLoopConfigData.getConvectiveModel();
    }

    public static CoolantPhysicsData getPhysicsData() {
        return CoolantLoopConfigData.getPhysicsData();
    }

    public static CoolantFluidData getFluidData() {
        return CoolantLoopConfigData.getFluidData();
    }

    public static CoolantPipeData getPipeData() {
        return CoolantLoopConfigData.getPipeData();
    }

    public static CoolantDeviceData getDeviceData() {
        return CoolantLoopConfigData.getDeviceData();
    }

    public static void setDeviceData(CoolantDeviceData data) {
        CoolantLoopConfigData.setDeviceData(data);
    }

    public static double getPipePressureRating(String materialName) {
        return CoolantLoopConfigData.getPipePressureRating(materialName);
    }

    public static double getPipeTemperatureLimit(String materialName) {
        return CoolantLoopConfigData.getPipeTemperatureLimit(materialName);
    }

    public static double getDiameterForThickness(float thickness) {
        return CoolantLoopConfigData.getDiameterForThickness(thickness);
    }

    public static boolean isAllowedCoolantPipeMaterial(String materialName) {
        return CoolantLoopConfigData.isAllowedCoolantPipeMaterial(materialName);
    }
}
