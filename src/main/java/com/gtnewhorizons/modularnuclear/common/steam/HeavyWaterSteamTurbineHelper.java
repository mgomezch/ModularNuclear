package com.gtnewhorizons.modularnuclear.common.steam;

import java.util.function.Consumer;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.modularnuclear.common.fluid.ModFluids;

import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTUtility;

/**
 * Heavy-water steam in GregTech's turbines. It comes in the grades plain steam does (steam, superheated,
 * supercritical); a turbine takes a heavy grade as it takes the plain one, and the share of its exhaust that came from
 * heavy steam leaves as the heavy counterpart (supercritical to superheated to steam to heavy water).
 */
public class HeavyWaterSteamTurbineHelper {

    public static boolean isHeavyWaterSteam(Fluid fluid) {
        if (fluid == null) return false;
        if (ModFluids.fluidHeavyWaterSteam != null && fluid == ModFluids.fluidHeavyWaterSteam) {
            return true;
        }
        String name = fluid.getName();
        if ("heavywatersteam".equalsIgnoreCase(name) || "fluid.heavywatersteam".equalsIgnoreCase(name)) {
            return true;
        }
        String unlocalized = fluid.getUnlocalizedName();
        return "fluid.heavywatersteam".equalsIgnoreCase(unlocalized) || "heavywatersteam".equalsIgnoreCase(unlocalized);
    }

    public static boolean isHeavyWaterSteam(FluidStack stack) {
        if (stack == null || stack.getFluid() == null) return false;
        return isHeavyWaterSteam(stack.getFluid());
    }

    public static boolean isSuperheatedHeavyWaterSteam(FluidStack stack) {
        return stack != null && stack.getFluid() != null
            && stack.getFluid() == ModFluids.fluidSuperheatedHeavyWaterSteam;
    }

    public static boolean isSupercriticalHeavyWaterSteam(FluidStack stack) {
        return stack != null && stack.getFluid() != null
            && stack.getFluid() == ModFluids.fluidSupercriticalHeavyWaterSteam;
    }

    /** Heavy-water steam of any grade. */
    public static boolean isAnyHeavyWaterSteam(FluidStack stack) {
        return isHeavyWaterSteam(stack) || isSuperheatedHeavyWaterSteam(stack) || isSupercriticalHeavyWaterSteam(stack);
    }

    /** The heavy-water counterpart of a turbine's exhaust (or condensate), or null if it has none. */
    public static Fluid heavyCounterpart(FluidStack exhaust) {
        if (exhaust == null || exhaust.getFluid() == null) return null;
        Fluid f = exhaust.getFluid();
        if (f == FluidRegistry.getFluid("ic2superheatedsteam")) return ModFluids.fluidSuperheatedHeavyWaterSteam;
        if ("steam".equals(f.getName()) || GTModHandler.isSteam(exhaust)) return ModFluids.fluidHeavyWaterSteam;
        if (exhaust.isFluidEqual(GTModHandler.getDistilledWater(1))) {
            return getHeavyWater(1).getFluid();
        }
        return null;
    }

    /**
     * Puts out a turbine's exhaust, the share of it that heavy steam was fed (heavyFlow of heavyFlow + regularFlow) as
     * its heavy-water counterpart. An exhaust with none (dense steam) goes out as it is.
     */
    public static void splitExhaust(Consumer<FluidStack> outputConsumer, FluidStack exhaust, int heavyFlow,
        int regularFlow) {
        if (exhaust == null || exhaust.amount <= 0 || outputConsumer == null) return;
        Fluid heavy = heavyCounterpart(exhaust);
        int totalFlow = heavyFlow + regularFlow;
        if (heavy == null || heavyFlow <= 0 || totalFlow <= 0) {
            outputConsumer.accept(exhaust);
            return;
        }
        int heavyAmount = (int) Math.round((double) exhaust.amount * heavyFlow / totalFlow);
        int regularAmount = exhaust.amount - heavyAmount;
        if (regularAmount > 0) outputConsumer.accept(new FluidStack(exhaust, regularAmount));
        if (heavyAmount > 0) outputConsumer.accept(new FluidStack(heavy, heavyAmount));
    }

    public static FluidStack getHeavyWater(long amount) {
        if (ModFluids.fluidHeavyWater != null) {
            return new FluidStack(ModFluids.fluidHeavyWater, GTUtility.safeInt(amount));
        }
        Fluid hw = FluidRegistry.getFluid("heavywater");
        if (hw != null) {
            return new FluidStack(hw, GTUtility.safeInt(amount));
        }
        return GTModHandler.getDistilledWater(amount);
    }

    public static void distributeCondensedWater(Consumer<FluidStack> outputConsumer, int totalWater,
        int hwSteamConsumed, int regularSteamConsumed) {
        if (totalWater <= 0) return;
        splitExhaust(outputConsumer, GTModHandler.getDistilledWater(totalWater), hwSteamConsumed, regularSteamConsumed);
    }
}
