package com.gtnewhorizons.modularnuclear.common.coolantloop;

/**
 * The coolant pipes the mod registers (material by size), shared by the game's registration and the standalone
 * simulator so both use the same throughput and heat resistance.
 */
public final class CoolantPipeSpec {

    /** GregTech material names, in registration order. */
    public static final String[] MATERIAL_NAMES = { "CastIron", "Steel", "StainlessSteel", "Titanium", "TungstenSteel",
        "Osmium", "Neutronium" };

    /** Base capacity (L/t) of a medium pipe of each material. */
    public static final int[] BASE_CAPACITIES = { 180, 240, 360, 480, 600, 1200, 16800 };

    /** GregTech pipe heat resistance (K) of each material. */
    public static final int[] HEAT_RESISTANCES = { 2250, 2500, 3000, 5000, 7500, 10000, 1000000 };

    /**
     * GregTech voltage tier of each material (LV 1 ... ZPM 7): the tier of the GregTech hatch a high-pressure passage
     * hatch of that material is crafted from, and the tier that sets its heat capacity and heat transfer.
     */
    public static final int[] VOLTAGE_TIERS = { 1, 2, 3, 4, 5, 6, 7 };

    public static final String[] SIZE_NAMES = { "Tiny", "Small", "Medium", "Large", "Huge" };

    public static final float[] THICKNESSES = { 0.25F, 0.375F, 0.50F, 0.75F, 0.875F };

    private CoolantPipeSpec() {}

    /** Capacity in L/t of a pipe of the given material and size index. */
    public static int capacityPerTick(int materialIndex, int sizeIndex) {
        int base = BASE_CAPACITIES[materialIndex];
        return switch (sizeIndex) {
            case 0 -> Math.max(1, base / 6);
            case 1 -> Math.max(1, base / 3);
            case 2 -> base;
            case 3 -> base * 2;
            default -> base * 4;
        };
    }

    /** Capacity in L/s (GregTech pipes move their capacity every tick). */
    public static double capacityLitersPerSecond(int materialIndex, int sizeIndex) {
        return capacityPerTick(materialIndex, sizeIndex) * 20.0;
    }

    /** Index of a material name in {@link #MATERIAL_NAMES} (case-insensitive), or -1. */
    public static int materialIndex(String materialName) {
        if (materialName == null) return -1;
        for (int i = 0; i < MATERIAL_NAMES.length; i++) {
            if (MATERIAL_NAMES[i].equalsIgnoreCase(materialName)) return i;
        }
        return -1;
    }
}
