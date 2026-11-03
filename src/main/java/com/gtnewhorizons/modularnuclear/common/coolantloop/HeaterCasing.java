package com.gtnewhorizons.modularnuclear.common.coolantloop;

import com.gtnewhorizons.modularnuclear.common.config.CoolantLoopConfigData;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.WasmStrings;

/**
 * A Pressurized Fluid Heater's pipe casing, by casing tier (1 Tin ... 8 Black Plutonium), as the game and the
 * standalone simulator both need it: the coolant loop pipe material it rates as (HeaterPipeCasingTier's equivalent
 * loop material), which gives the heater's pressure drop multiplier and burst pressure, and its temperature limit.
 */
public enum HeaterCasing {

    TIN("castiron", -1, 1000.0),
    BRASS("steel", -1, 1100.0),
    ELECTRUM("stainlesssteel", NuclearSimulationEngine.PIPE_TIER_ELECTRUM, 1200.0),
    PLATINUM("titanium", NuclearSimulationEngine.PIPE_TIER_PLATINUM, 1400.0),
    OSMIUM("osmium", NuclearSimulationEngine.PIPE_TIER_OSMIUM, 1800.0),
    QUANTIUM("neutronium", NuclearSimulationEngine.PIPE_TIER_QUANTIUM, 2200.0),
    FLUXED_ELECTRUM("neutronium", NuclearSimulationEngine.PIPE_TIER_FLUXED_ELECTRUM, 2600.0),
    BLACK_PLUTONIUM("neutronium", NuclearSimulationEngine.PIPE_TIER_BLACK_PLUTONIUM, 3200.0);

    /** Pipe material (pipes.yaml key) the casing rates as. */
    public final String loopMaterial;
    private final int enginePipeTier;
    private final double fallbackMaxTempC;

    HeaterCasing(String loopMaterial, int enginePipeTier, double fallbackMaxTempC) {
        this.loopMaterial = loopMaterial;
        this.enginePipeTier = enginePipeTier;
        this.fallbackMaxTempC = fallbackMaxTempC;
    }

    /** Casing tier, 1 (Tin) to 8 (Black Plutonium). */
    public int casingTier() {
        return ordinal() + 1;
    }

    public static HeaterCasing byCasingTier(int casingTier) {
        HeaterCasing[] all = values();
        return all[Math.max(1, Math.min(all.length, casingTier)) - 1];
    }

    /** Pressure drop multiplier of the coolant passing through: that of the loop pipe material it rates as. */
    public double frictionMultiplier() {
        return CoolantLoopConfigData.getPipeData()
            .getFrictionMultiplier(loopMaterial);
    }

    /** Burst pressure in bar: that of a coolant loop pipe of the material it rates as. */
    public double maxPressureBar() {
        return CoolantLoopConfigData.getPipePressureRating(loopMaterial);
    }

    /** Highest inner temperature in °C before the heater powerfails. */
    public double maxTemperatureCelsius() {
        return enginePipeTier >= 0 ? NuclearSimulationEngine.getMaxOperatingTemperature(enginePipeTier)
            : fallbackMaxTempC;
    }

    /** The best casing that rates no better than a loop pipe material (a heater built to the loop's tier). */
    public static HeaterCasing forLoopMaterial(String materialName) {
        int tier = CoolantPipeSpec.materialIndex(materialName);
        HeaterCasing best = TIN;
        for (HeaterCasing c : values()) {
            if (CoolantPipeSpec.materialIndex(c.loopMaterial) <= tier) best = c;
        }
        return best;
    }

    public static HeaterCasing fromString(String s) {
        if (s == null) return null;
        String key = WasmStrings.normalizeToken(s, " -");
        for (HeaterCasing c : values()) {
            if (c.name()
                .equals(key)
                || c.name()
                    .replace("_", "")
                    .equals(key.replace("_", ""))) {
                return c;
            }
        }
        try {
            return byCasingTier(Integer.parseInt(key));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
