package com.gtnewhorizons.modularnuclear.common.nuclear;

import java.util.Locale;

/**
 * What happens to coolant reaching a core hatch, shared by the game's controller and the simulator so both apply the
 * same rule: coolant that boils at ambient flashes at once, and coolant fed into a hatch that ran dry and is now
 * above the coolant's boiling point flashes too. Either is a thermal shock. Liquid fuel never boils.
 */
public final class CoreHatchRules {

    /** Structural damage (percent) of one thermal shock. */
    public static final double THERMAL_SHOCK_DAMAGE_PERCENT = 2.0;

    public enum CoolantFeed {
        /** The coolant can enter the hatch. */
        SAFE,
        /** The coolant boils at ambient temperature, so it flashes as soon as it arrives. */
        BOILS_AT_AMBIENT,
        /** The hatch ran dry and is hotter than the coolant's boiling point. */
        INTO_DRY_HOT_HATCH
    }

    private CoreHatchRules() {}

    public static CoolantFeed checkCoolantFeed(String fluidName, boolean hatchWasDry, double hatchTemperature,
        double ambient) {
        double boiling = NuclearSimulationEngine.getCoolantBoilingThreshold(fluidName);
        if (ambient >= boiling) return CoolantFeed.BOILS_AT_AMBIENT;
        if (hatchWasDry && hatchTemperature > boiling) return CoolantFeed.INTO_DRY_HOT_HATCH;
        return CoolantFeed.SAFE;
    }

    /** The thermal shock reason for a feed that is not safe. */
    public static String describe(CoolantFeed feed, String fluidName, double hatchTemperature, double ambient) {
        double boiling = NuclearSimulationEngine.getCoolantBoilingThreshold(fluidName);
        return switch (feed) {
            case BOILS_AT_AMBIENT -> String.format(
                Locale.US,
                "Coolant evaporates immediately at ambient temperature (%s, ambient %.1f°C >= boiling threshold %.1f°C)",
                fluidName,
                ambient,
                boiling);
            case INTO_DRY_HOT_HATCH -> String.format(
                Locale.US,
                "Coolant fed into dry superheated hatch (%s, %.1f°C > boiling threshold %.1f°C)",
                fluidName,
                hatchTemperature,
                boiling);
            case SAFE -> "";
        };
    }
}
