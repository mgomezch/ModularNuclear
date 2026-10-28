package com.gtnewhorizons.modularnuclear.common.coolantloop;

/**
 * Pressurized heat exchanger (PHE) sizes and sheetmetal tiers, shared by the game's PHE multiblock and the standalone
 * simulator (which runs without GregTech). A PHE is 3 wide, {@link #VALID_HEIGHTS} high and {@link #MIN_LENGTH} to
 * {@link #MAX_LENGTH} long (overall, controller face to rear wall); its exchange area is height x (length - 2) m²,
 * times
 * its sheetmetal's heat transfer multiplier. Every sheetmetal block in one PHE must be the same tier.
 */
public final class PheGeometry {

    public static final int[] VALID_HEIGHTS = { 3, 5, 7, 9 };
    public static final int MIN_LENGTH = 5;
    public static final int MAX_LENGTH = 11;

    /** Sheetmetal tiers in order (index = tier), aligned with the coolant loop material progression. */
    public static final String[] SHEETMETAL_NAMES = { "Copper", "Electrum", "Platinum", "Osmium", "Quantium",
        "Fluxed Electrum", "Black Plutonium", "Neutronium" };
    /** Heat transfer multiplier of each sheetmetal tier: doubles per tier. */
    public static final double[] SHEETMETAL_HEAT_TRANSFER = { 1.0, 2.0, 4.0, 8.0, 16.0, 32.0, 64.0, 128.0 };
    /** Highest coolant temperature (°C) each sheetmetal tier withstands; copper's is its GregTech melting point. */
    public static final double[] SHEETMETAL_MAX_TEMP_C = { 1084.0, 1584.0, 2084.0, 4084.0, 6584.0, 9084.0, 15000.0,
        1000000.0 };

    private PheGeometry() {}

    /** Bare exchange area (m²) of a PHE of the given height and overall length. */
    public static double exchangeArea(int height, int length) {
        return (double) height * (length - 2);
    }

    /** Exchange area (m²) times the sheetmetal tier's heat transfer multiplier. */
    public static double effectiveExchangeArea(int height, int length, int sheetmetalTier) {
        return exchangeArea(height, length) * SHEETMETAL_HEAT_TRANSFER[clampTier(sheetmetalTier)];
    }

    public static double maxTemperatureCelsius(int sheetmetalTier) {
        return SHEETMETAL_MAX_TEMP_C[clampTier(sheetmetalTier)];
    }

    public static int clampTier(int tier) {
        return Math.max(0, Math.min(SHEETMETAL_NAMES.length - 1, tier));
    }

    /** Nearest valid height (an odd number from 3 to 9). */
    public static int clampHeight(int height) {
        int best = VALID_HEIGHTS[0];
        for (int h : VALID_HEIGHTS) if (Math.abs(h - height) < Math.abs(best - height)) best = h;
        return best;
    }

    public static int clampLength(int length) {
        return Math.max(MIN_LENGTH, Math.min(MAX_LENGTH, length));
    }

    /** Sheetmetal tier by name (case and spaces ignored) or index, or -1. */
    public static int tierFromString(String s) {
        if (s == null) return -1;
        String n = s.trim()
            .replace(" ", "")
            .replace("_", "");
        for (int i = 0; i < SHEETMETAL_NAMES.length; i++) {
            if (SHEETMETAL_NAMES[i].replace(" ", "")
                .equalsIgnoreCase(n)) return i;
        }
        try {
            return clampTier(Integer.parseInt(n));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
