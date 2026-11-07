package com.gtnewhorizons.modularnuclear.common.item;

import static gregtech.api.recipe.RecipeMaps.extruderRecipes;
import static gregtech.api.recipe.RecipeMaps.formingPressRecipes;
import static gregtech.api.util.GTRecipeBuilder.TICKS;

import net.minecraft.item.ItemStack;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.SubTag;
import gregtech.api.interfaces.IMaterialHandler;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;

/**
 * The reactor's moderator and control rods that GregTech does not make: it makes rods only for metals and gems, and
 * graphite, boron and cadmium are dust-only materials. Their rods and long rods are added to GregTech's own items
 * ({@code stickGraphite}, {@code stickLongBoron}, ...), so they get its names, textures, ore dictionary entries,
 * cutting and recycling, and boron's and cadmium's hammer recipes. This adds the recipes GregTech has no way to make
 * from dust: the extruder makes rods from dust, the forming press long rods from two rods.
 */
public final class ReactorRodMaterials implements IMaterialHandler {

    private ReactorRodMaterials() {}

    /**
     * Registers with GregTech; call at construction. GregTech's material handlers run in its pre-init, after its
     * materials exist and before it makes its items, and must be registered before then.
     */
    public static void register() {
        GregTechAPI.sBeforeGTPreload.add(() -> Materials.add(new ReactorRodMaterials()));
    }

    private static Materials[] materials() {
        return new Materials[] { Materials.Graphite, Materials.Boron, Materials.Cadmium };
    }

    @Override
    public void onMaterialsInit() {
        enableRods();
    }

    /** Adds the materials to the rod and long rod items GregTech makes. */
    public static void enableRods() {
        for (Materials material : materials()) {
            for (OrePrefixes prefix : new OrePrefixes[] { OrePrefixes.stick, OrePrefixes.stickLong }) {
                prefix.mDisabledItems.remove(material);
                prefix.mGeneratedItems.add(material);
            }
        }
    }

    /** The extruder and forming press recipes, costed as GregTech's extruder recipes from ingots; call in post-init. */
    public static void registerRecipes() {
        if (!ItemList.Shape_Extruder_Rod.hasBeenSet() || !ItemList.Shape_Mold_Rod_Long.hasBeenSet()) return;
        for (Materials material : materials()) {
            ItemStack dust = GTOreDictUnificator.get(OrePrefixes.dust, material, 1L);
            ItemStack rods = GTOreDictUnificator.get(OrePrefixes.stick, material, 2L);
            ItemStack longRod = GTOreDictUnificator.get(OrePrefixes.stickLong, material, 1L);
            if (dust == null || rods == null || longRod == null) continue;
            int voltageMultiplier = material.mBlastFurnaceTemp >= 2800 ? 60 : 15;
            if (material.contains(SubTag.NO_SMASHING)) voltageMultiplier /= 4;
            long duration = Math.max(material.getMass() * 2L, 1L);
            GTValues.RA.stdBuilder()
                .itemInputs(dust, ItemList.Shape_Extruder_Rod.get(0L))
                .itemOutputs(rods)
                .duration(duration * TICKS)
                .eut(GTUtility.calculateRecipeEU(material, 6 * voltageMultiplier))
                .addTo(extruderRecipes);
            GTValues.RA.stdBuilder()
                .itemInputs(rods, ItemList.Shape_Mold_Rod_Long.get(0L))
                .itemOutputs(longRod)
                .duration(duration * TICKS)
                .eut(GTUtility.calculateRecipeEU(material, 6 * voltageMultiplier))
                .addTo(formingPressRecipes);
        }
    }
}
