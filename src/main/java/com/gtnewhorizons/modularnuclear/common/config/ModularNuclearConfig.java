package com.gtnewhorizons.modularnuclear.common.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import com.gtnewhorizons.modularnuclear.ModularNuclear;

public final class ModularNuclearConfig {

    private ModularNuclearConfig() {}

    public static boolean enableCherenkovRadiation = true;
    public static boolean enableCherenkovLightEmission = true;
    public static int cherenkovLightLevel = 10;
    public static float cherenkovIntensityMultiplier = 2.5f;

    public static void init(File configFile) {
        Configuration config = new Configuration(configFile);
        try {
            config.load();

            enableCherenkovRadiation = config.getBoolean(
                "enableCherenkovRadiation",
                "client_visuals",
                true,
                "Enable client-side Cherenkov radiation visual effects (blue beams and particles) above flooded reactors.");

            enableCherenkovLightEmission = config.getBoolean(
                "enableCherenkovLightEmission",
                "client_visuals",
                true,
                "Enable dynamic client block light emission from hatches when Cherenkov radiation is active.");

            cherenkovLightLevel = config.getInt(
                "cherenkovLightLevel",
                "client_visuals",
                10,
                1,
                15,
                "Block light level emitted by reactor core hatches when Cherenkov radiation is active (default 10).");

            cherenkovIntensityMultiplier = config.getFloat(
                "cherenkovIntensityMultiplier",
                "client_visuals",
                2.5f,
                0.1f,
                10.0f,
                "Multiplier for Cherenkov visual beam intensity (default 2.5).");

        } catch (Exception e) {
            ModularNuclear.LOG.error("Failed to load ModularNuclear config", e);
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }
}
