package com.gtnewhorizons.modularnuclear.common.util;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.modularnuclear.common.config.data.CoolantDeviceData.PHEData;
import com.gtnewhorizons.modularnuclear.common.config.data.CoolantDeviceData.PHEData.SecondaryCoolantEntry;

import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;

/**
 * The electricity a GregTech vacuum freezer spends on a change of state (a recipe's EU/t times its duration): what the
 * coolant loop's machines may give back as heat, or take up as cold, for the same change, so that no loop between a
 * freezer and a coolant bath or PHE comes out ahead. EU becomes heat at the rate a PHE and a steam turbine at 100%
 * efficiency turn heat back into EU (PHEData.joulesPerEU).
 */
public final class FreezerRecipeEnergy {

    private FreezerRecipeEnergy() {}

    /** EU per item, by what the freezer takes (a hot ingot) and by what it makes (a cell of liquid nitrogen). */
    private static final Map<ItemDamageKey, Double> EU_BY_INPUT = new HashMap<>();
    private static final Map<ItemDamageKey, Double> EU_BY_OUTPUT = new HashMap<>();
    /** The seconds the freezer takes over one item, by the same. */
    private static final Map<ItemDamageKey, Double> SECONDS_BY_INPUT = new HashMap<>();
    private static final Map<ItemDamageKey, Double> SECONDS_BY_OUTPUT = new HashMap<>();
    /** EU per liter of a fluid the freezer makes, as a fluid or in a cell. */
    private static final Map<String, Double> EU_PER_LITER = new HashMap<>();
    private static boolean scanned = false;

    private static synchronized void ensureScanned() {
        if (scanned) return;
        scanned = true;
        if (RecipeMaps.vacuumFreezerRecipes == null) return;
        for (GTRecipe recipe : RecipeMaps.vacuumFreezerRecipes.getAllRecipes()) {
            double eu = (double) recipe.mEUt * recipe.mDuration;
            if (eu <= 0.0) continue;
            ItemStack in = single(recipe.mInputs);
            ItemStack out = single(recipe.mOutputs);
            boolean noFluids = isEmpty(recipe.mFluidInputs) && isEmpty(recipe.mFluidOutputs);
            if (in != null && out != null && noFluids) {
                double seconds = recipe.mDuration / 20.0;
                if (keepLeast(EU_BY_INPUT, ItemDamageKey.of(in), eu / in.stackSize))
                    SECONDS_BY_INPUT.put(ItemDamageKey.of(in), seconds / in.stackSize);
                if (keepLeast(EU_BY_OUTPUT, ItemDamageKey.of(out), eu / out.stackSize))
                    SECONDS_BY_OUTPUT.put(ItemDamageKey.of(out), seconds / out.stackSize);
                FluidStack held = GTUtility.getFluidForFilledItem(out, true);
                if (held != null && held.amount > 0 && held.getFluid() != null) {
                    keepLeast(
                        EU_PER_LITER,
                        held.getFluid()
                            .getName(),
                        eu / ((double) held.amount * out.stackSize));
                }
            } else if (isEmpty(recipe.mOutputs) && recipe.mFluidOutputs != null
                && recipe.mFluidOutputs.length == 1
                && recipe.mFluidOutputs[0] != null
                && recipe.mFluidOutputs[0].amount > 0
                && recipe.mFluidOutputs[0].getFluid() != null) {
                    keepLeast(
                        EU_PER_LITER,
                        recipe.mFluidOutputs[0].getFluid()
                            .getName(),
                        eu / recipe.mFluidOutputs[0].amount);
                }
        }

    }

    private static ItemStack single(ItemStack[] stacks) {
        if (stacks == null) return null;
        ItemStack found = null;
        for (ItemStack s : stacks) {
            if (s == null || s.stackSize <= 0) continue;
            if (found != null) return null;
            found = s;
        }
        return found;
    }

    private static boolean isEmpty(Object[] array) {
        if (array == null) return true;
        for (Object o : array) if (o != null) return false;
        return true;
    }

    /** Where recipes differ on a change of state, the cheapest is what a loop could be run against. */
    private static <K> boolean keepLeast(Map<K, Double> map, K key, double eu) {
        if (key == null) return false;
        Double had = map.get(key);
        if (had != null && eu >= had) return false;
        map.put(key, eu);
        return true;
    }

    /** Seconds a freezer takes over one of this item (a hot ingot), or -1 if it has no recipe for it. */
    public static double secondsToFreeze(ItemStack input) {
        ensureScanned();
        Double t = input != null ? SECONDS_BY_INPUT.get(ItemDamageKey.of(input)) : null;
        return t != null ? t : -1.0;
    }

    /** Seconds a freezer takes making one of this item, or -1 if it has no recipe for it. */
    public static double secondsToMake(ItemStack output) {
        ensureScanned();
        Double t = output != null ? SECONDS_BY_OUTPUT.get(ItemDamageKey.of(output)) : null;
        return t != null ? t : -1.0;
    }

    /** EU a freezer spends on one of this item (a hot ingot), or -1 if it has no recipe for it. */
    public static double euToFreeze(ItemStack input) {
        ensureScanned();
        Double eu = input != null ? EU_BY_INPUT.get(ItemDamageKey.of(input)) : null;
        return eu != null ? eu : -1.0;
    }

    /** EU a freezer spends making one of this item (an ingot from its hot ingot), or -1 if it has no recipe for it. */
    public static double euToMake(ItemStack output) {
        ensureScanned();
        Double eu = output != null ? EU_BY_OUTPUT.get(ItemDamageKey.of(output)) : null;
        return eu != null ? eu : -1.0;
    }

    /** EU a freezer spends per liter of this fluid it makes, or -1 if it has no recipe for it. */
    public static double euPerLiter(String fluidName) {
        ensureScanned();
        Double eu = fluidName != null ? EU_PER_LITER.get(fluidName) : null;
        return eu != null ? eu : -1.0;
    }

    /**
     * Sets the heat a liter of a boiling secondary coolant takes up to what the freezer spends making that liter, for
     * coolants the freezer makes (liquid nitrogen from nitrogen). Others keep their configured latent heat.
     */
    public static void applyTo(SecondaryCoolantEntry coolant, String fluidName, PHEData cfg) {
        if (coolant == null || cfg == null || coolant.isSteam || coolant.recipeLatentHeatResolved) return;
        coolant.recipeLatentHeatResolved = true;
        double eu = euPerLiter(fluidName);
        if (eu > 0.0) coolant.recipeLatentHeatJoulePerLiter = eu * cfg.joulesPerEU();
    }
}
