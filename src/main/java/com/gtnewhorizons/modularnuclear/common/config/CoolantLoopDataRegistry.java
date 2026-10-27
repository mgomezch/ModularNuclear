package com.gtnewhorizons.modularnuclear.common.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

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
 * Central data registry for CoolantLoops, providing structured YAML-backed
 * configuration of hydrodynamic constants, fluid thermodynamic properties,
 * piping limits, and multiblock device parameters.
 */
public class CoolantLoopDataRegistry {

    private static final Logger LOG = LogManager.getLogger("CoolantLoopsDataRegistry");
    private static final String DEFAULTS_PATH = "assets/coolantloops/default_config/";

    private static File configFolder;

    public static synchronized void init(File configDirectory) {
        configFolder = new File(configDirectory, "coolantloops");
        if (!configFolder.exists()) {
            configFolder.mkdirs();
        }

        // Start from the shipped defaults so a file that fails to load keeps them.
        CoolantLoopConfigData.loadDefaults();
        CoolantLoopConfigData.setPhysics(loadFile("physics.yaml", null));
        CoolantLoopConfigData.setFluids(loadFile("fluids.yaml", "coolants.yaml"));
        CoolantLoopConfigData.setPipes(loadFile("pipes.yaml", null));
        CoolantLoopConfigData.setDevices(loadFile("devices.yaml", null));
        CoolantLoopConfigData.finishLoading();
        LOG.info("CoolantLoops YAML Data Registry initialized successfully.");
    }

    private static Yaml createYaml() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        return new Yaml(options);
    }

    /**
     * Loads one config document, writing the shipped default first if the file is missing. Returns null (keeping the
     * defaults) if it cannot be read.
     */
    private static Object loadFile(String name, String legacyName) {
        File file = new File(configFolder, name);
        if (!file.exists() && legacyName != null) {
            File alt = new File(configFolder, legacyName);
            if (alt.exists()) file = alt;
        }
        if (!file.exists()) {
            writeDefault(name, file);
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            return createYaml().load(reader);
        } catch (Exception e) {
            LOG.error("Failed to load " + file.getName() + ", keeping defaults", e);
            return null;
        }
    }

    private static void writeDefault(String name, File file) {
        try (InputStream in = CoolantLoopDataRegistry.class.getClassLoader()
            .getResourceAsStream(DEFAULTS_PATH + name); OutputStream out = new FileOutputStream(file)) {
            if (in == null) {
                LOG.error("Default " + name + " resource not found in JAR!");
                return;
            }
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } catch (Exception e) {
            LOG.error("Failed to write default YAML file " + file.getName(), e);
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
