package com.gtnewhorizons.modularnuclear.common.steam;

import java.util.function.Consumer;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.modularnuclear.common.fluid.ModFluids;

import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTUtility;

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

    public static void distributeCondensedWater(Consumer<FluidStack> outputConsumer, int totalWater, int hwSteamConsumed,
        int regularSteamConsumed) {
        if (totalWater <= 0 || outputConsumer == null) {
            return;
        }
        int totalSteam = hwSteamConsumed + regularSteamConsumed;
        if (totalSteam <= 0) {
            outputConsumer.accept(GTModHandler.getDistilledWater(totalWater));
            return;
        }
        int hwWater = (int) Math.round((double) totalWater * hwSteamConsumed / totalSteam);
        int dwWater = totalWater - hwWater;
        if (dwWater > 0) {
            outputConsumer.accept(GTModHandler.getDistilledWater(dwWater));
        }
        if (hwWater > 0) {
            outputConsumer.accept(getHeavyWater(hwWater));
        }
    }
}
