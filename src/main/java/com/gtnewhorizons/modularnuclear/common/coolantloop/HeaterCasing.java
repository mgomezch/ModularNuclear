package com.gtnewhorizons.modularnuclear.common.coolantloop;

import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.WasmStrings;

/**
 * A Pressurized Fluid Heater's pipe casing, by casing tier (1 Tin ... 8 Black Plutonium), as the game and the
 * standalone simulator both need it: its temperature limit. The heater's pressure drop and burst pressure are those of
 * the loop pipe it is attached to, whatever its casings.
 */
public enum HeaterCasing {

    TIN(-1, 1000.0),
    BRASS(-1, 1100.0),
    ELECTRUM(NuclearSimulationEngine.PIPE_TIER_ELECTRUM, 1200.0),
    PLATINUM(NuclearSimulationEngine.PIPE_TIER_PLATINUM, 1400.0),
    OSMIUM(NuclearSimulationEngine.PIPE_TIER_OSMIUM, 1800.0),
    QUANTIUM(NuclearSimulationEngine.PIPE_TIER_QUANTIUM, 2200.0),
    FLUXED_ELECTRUM(NuclearSimulationEngine.PIPE_TIER_FLUXED_ELECTRUM, 2600.0),
    BLACK_PLUTONIUM(NuclearSimulationEngine.PIPE_TIER_BLACK_PLUTONIUM, 3200.0);

    private final int enginePipeTier;
    private final double fallbackMaxTempC;

    HeaterCasing(int enginePipeTier, double fallbackMaxTempC) {
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

    /** Highest inner temperature in °C before the heater powerfails. */
    public double maxTemperatureCelsius() {
        return enginePipeTier >= 0 ? NuclearSimulationEngine.getMaxOperatingTemperature(enginePipeTier)
            : fallbackMaxTempC;
    }

    /** The casing of a loop pipe material's voltage tier (a heater built to the loop's tier). */
    public static HeaterCasing forLoopMaterial(String materialName) {
        int m = Math.max(0, CoolantPipeSpec.materialIndex(materialName));
        return byCasingTier(CoolantPipeSpec.VOLTAGE_TIERS[m]);
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
