package com.gtnewhorizons.modularnuclear.common.fluid;

import bartworks.API.WerkstoffAdderRegistry;
import bartworks.system.material.Werkstoff;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TextureSet;

/**
 * The mod's materials as BartWorks materials, as GTNH's addons make theirs (GoodGenerator, GTNH Lanthanides), so they
 * get GregTech's standard items and show in its material handlers. The fluids get only the standard GregTech cell
 * (oredict cell&lt;Name&gt;, its container registration and canner recipes); corium gets its dusts and rod.
 *
 * <p>
 * A material's fluid is the one named as the material without spaces, lowercased; ModFluids registers those first,
 * so the materials take the mod's fluids (and their icons) rather than making new ones. The names here are therefore
 * fluid ids: the display names come from the lang file (Material.&lt;name&gt;), which BartWorks' defaults do not
 * override.
 */
public final class ModWerkstoffs implements Runnable {

    /**
     * The mod's material ids. BartWorks keeps one id space for every addon's materials: GoodGenerator takes 10001 on,
     * GTNH Lanthanides 11000 on, BartWorks' own reach 11503, its bot materials 29900 on.
     */
    public static final int HEAVY_WATER_ID = 12_500;
    public static final int HEAVY_WATER_STEAM_ID = 12_501;
    public static final int SUPERHEATED_HEAVY_WATER_STEAM_ID = 12_502;
    public static final int SUPERCRITICAL_HEAVY_WATER_STEAM_ID = 12_503;
    public static final int CORIUM_ID = 12_504;
    /** The refrigerant and its precursors. See Note [R-134a chemistry] in RefrigerantRecipes */
    public static final int R134A_ID = 12_505;
    public static final int TRICHLOROETHYLENE_ID = 12_506;
    public static final int CHLOROTRIFLUOROETHANE_ID = 12_507;

    public static Werkstoff heavyWater;
    public static Werkstoff heavyWaterSteam;
    public static Werkstoff superheatedHeavyWaterSteam;
    public static Werkstoff supercriticalHeavyWaterSteam;
    /** Solidified corium as a material: its dust and rod (the molten corium of a meltdown is a fluid of its own). */
    public static Werkstoff corium;
    public static Werkstoff r134a;
    public static Werkstoff trichloroethylene;
    public static Werkstoff chlorotrifluoroethane;

    private ModWerkstoffs() {}

    /** Has BartWorks make the materials when it makes its own (its init); call in pre-init. */
    public static void register() {
        WerkstoffAdderRegistry.addWerkstoffAdder(new ModWerkstoffs());
    }

    @Override
    public void run() {
        heavyWater = cellsOnly(HEAVY_WATER_ID, "HeavyWater", new short[] { 45, 94, 130 }, false);
        // The fluid ids are the names lowercased: "r134a" is the gas GasProperty.R134A knows
        r134a = cellsOnly(R134A_ID, "R134a", "CF₃CH₂F", new short[] { 170, 225, 240 }, true);
        trichloroethylene = cellsOnly(
            TRICHLOROETHYLENE_ID,
            "Trichloroethylene",
            "C₂HCl₃",
            new short[] { 200, 230, 180 },
            false);
        chlorotrifluoroethane = cellsOnly(
            CHLOROTRIFLUOROETHANE_ID,
            "Chlorotrifluoroethane",
            "CF₃CH₂Cl",
            new short[] { 190, 240, 200 },
            true);
        heavyWaterSteam = cellsOnly(HEAVY_WATER_STEAM_ID, "HeavyWaterSteam", new short[] { 200, 210, 225 }, true);
        superheatedHeavyWaterSteam = cellsOnly(
            SUPERHEATED_HEAVY_WATER_STEAM_ID,
            "SuperheatedHeavyWaterSteam",
            new short[] { 225, 215, 200 },
            true);
        supercriticalHeavyWaterSteam = cellsOnly(
            SUPERCRITICAL_HEAVY_WATER_STEAM_ID,
            "SupercriticalHeavyWaterSteam",
            new short[] { 235, 200, 190 },
            true);
        checkFree(CORIUM_ID, "Corium");
        corium = new Werkstoff(
            new short[] { 0xBC, 0x00, 0xFD },
            "Corium",
            "",
            new Werkstoff.Stats().setRadioactive(true),
            Werkstoff.Types.MIXTURE,
            new Werkstoff.GenerationFeatures().disable()
                .onlyDust()
                .addPrefix(OrePrefixes.stick),
            CORIUM_ID,
            TextureSet.SET_NUCLEAR);
    }

    private static void checkFree(int id, String name) {
        Werkstoff existing = Werkstoff.werkstoffHashMap.get((short) id);
        if (existing != null) {
            throw new IllegalStateException(
                "ModularNuclear material " + name
                    + " needs BartWorks material id "
                    + id
                    + ", which "
                    + existing.getDefaultName()
                    + " already has");
        }
    }

    private static Werkstoff cellsOnly(int id, String name, short[] rgb, boolean gas) {
        return cellsOnly(id, name, "D₂O", rgb, gas);
    }

    private static Werkstoff cellsOnly(int id, String name, String formula, short[] rgb, boolean gas) {
        checkFree(id, name);
        return new Werkstoff(
            rgb,
            name,
            formula,
            new Werkstoff.Stats().setGas(gas),
            Werkstoff.Types.COMPOUND,
            new Werkstoff.GenerationFeatures().disable()
                .addCells(),
            id,
            TextureSet.SET_FLUID);
    }

    /** Fails if another addon took one of the mod's ids after the mod made its materials. */
    public static void checkIds() {
        check(HEAVY_WATER_ID, heavyWater);
        check(HEAVY_WATER_STEAM_ID, heavyWaterSteam);
        check(SUPERHEATED_HEAVY_WATER_STEAM_ID, superheatedHeavyWaterSteam);
        check(SUPERCRITICAL_HEAVY_WATER_STEAM_ID, supercriticalHeavyWaterSteam);
        check(CORIUM_ID, corium);
        check(R134A_ID, r134a);
        check(TRICHLOROETHYLENE_ID, trichloroethylene);
        check(CHLOROTRIFLUOROETHANE_ID, chlorotrifluoroethane);
    }

    private static void check(int id, Werkstoff ours) {
        if (ours == null) return;
        Werkstoff holder = Werkstoff.werkstoffHashMap.get((short) id);
        if (holder != ours) {
            throw new IllegalStateException(
                "BartWorks material id " + id
                    + " is ModularNuclear's "
                    + ours.getDefaultName()
                    + ", but "
                    + (holder != null ? holder.getDefaultName() : "nothing")
                    + " has it");
        }
    }
}
