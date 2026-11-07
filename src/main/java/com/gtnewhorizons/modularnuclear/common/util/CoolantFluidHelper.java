package com.gtnewhorizons.modularnuclear.common.util;

import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.modularnuclear.common.config.CoolantLoopDataRegistry;
import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantFluidProperty;

import gregtech.api.enums.Materials;

public final class CoolantFluidHelper {

    private static final IdentityCache<Fluid, Boolean> GASEOUS = new IdentityCache<>();
    private static final IdentityCache<Fluid, Boolean> LIQUID_FUEL_FLUID = new IdentityCache<>();
    private static final NameCache<Boolean> LIQUID_FUEL_NAME = new NameCache<>();

    public static void clearCaches() {
        GASEOUS.clear();
        LIQUID_FUEL_FLUID.clear();
        LIQUID_FUEL_NAME.clear();
    }

    private CoolantFluidHelper() {}

    public static boolean isPlainRegularWater(Fluid fluid) {
        if (fluid == null) return false;
        return fluid == FluidRegistry.WATER || "water".equals(fluid.getName());
    }

    public static boolean isPlainRegularWater(FluidStack stack) {
        if (stack == null || stack.getFluid() == null) return false;
        return isPlainRegularWater(stack.getFluid());
    }

    public static boolean isLava(String name) {
        return "lava".equals(name);
    }

    public static boolean isLava(Fluid fluid, FluidStack stack) {
        if (fluid == null && stack != null) {
            fluid = stack.getFluid();
        }
        if (fluid == null) return false;
        return fluid == FluidRegistry.LAVA || isLava(fluid.getName());
    }

    public static boolean isLava(FluidStack stack) {
        if (stack == null || stack.getFluid() == null) return false;
        return isLava(stack.getFluid(), stack);
    }

    public static boolean isNetherWorld(World world) {
        if (world == null || world.provider == null) return false;
        return world.provider.dimensionId == -1 || world.provider.isHellWorld;
    }

    public static boolean isLiquidNuclearFuel(String name) {
        if (name == null) return false;
        return LIQUID_FUEL_NAME.get(name, CoolantFluidHelper::computeIsLiquidNuclearFuel);
    }

    private static Boolean computeIsLiquidNuclearFuel(String name) {
        return CoolantLoopDataRegistry.getFluidData()
            .isLiquidNuclearFuel(name);
    }

    public static boolean isLiquidNuclearFuel(Fluid fluid, FluidStack stack) {
        if (fluid == null && stack != null) {
            fluid = stack.getFluid();
        }
        if (fluid == null) return false;
        return LIQUID_FUEL_FLUID.get(fluid, f -> isLiquidNuclearFuel(f.getName()));
    }

    public static boolean isLiquidNuclearFuel(FluidStack stack) {
        if (stack == null || stack.getFluid() == null) return false;
        return isLiquidNuclearFuel(stack.getFluid(), stack);
    }

    public static boolean isGaseousFluid(Fluid fluid, FluidStack stack) {
        if (fluid == null && stack != null) {
            fluid = stack.getFluid();
        }
        if (fluid == null) return false;
        if (stack != null && fluid.isGaseous(stack)) return true;
        return GASEOUS.get(fluid, CoolantFluidHelper::scanGaseous);
    }

    private static Boolean scanGaseous(Fluid fluid) {
        boolean res = fluid.isGaseous();
        if (!res) {
            String name = fluid.getName()
                .trim()
                .toLowerCase();
            if (name.startsWith("gas_") || name.endsWith("_gas") || name.endsWith(".gas")) {
                res = true;
            } else {
                try {
                    Materials mat = Materials.FLUID_MAP.get(fluid);
                    if (mat != null && mat.mGas != null && mat.mGas == fluid) {
                        res = true;
                    }
                } catch (Throwable swallowed) {
                    Swallowed.note(swallowed);
                }
            }
        }
        return res;
    }

    public static boolean isGaseousFluid(FluidStack stack) {
        if (stack == null || stack.getFluid() == null) return false;
        return isGaseousFluid(stack.getFluid(), stack);
    }

    public static boolean isMoltenFluid(Fluid fluid, FluidStack stack, CoolantFluidProperty prop) {
        if (isLava(fluid, stack)) {
            return true;
        }
        if (prop != null) {
            return prop.isMolten();
        }
        if (fluid != null) {
            CoolantFluidProperty p = CoolantFluidProperty.get(fluid.getName());
            if (p != null) {
                return p.isMolten();
            }
        }
        if (stack != null && stack.getFluid() != null) {
            CoolantFluidProperty p = CoolantFluidProperty.get(
                stack.getFluid()
                    .getName());
            if (p != null) {
                return p.isMolten();
            }
        }
        return false;
    }

    public static boolean isMoltenFluid(FluidStack stack) {
        if (stack == null || stack.getFluid() == null) return false;
        return isMoltenFluid(
            stack.getFluid(),
            stack,
            CoolantFluidProperty.get(
                stack.getFluid()
                    .getName()));
    }

    public static double getDeclaredGregTechFluidTemperatureCelsius(Fluid fluid, FluidStack stack,
        CoolantFluidProperty prop) {
        if (fluid == null && stack != null) {
            fluid = stack.getFluid();
        }
        if (fluid == null && prop != null) {
            try {
                fluid = FluidRegistry.getFluid(prop.getFluidName());
            } catch (Throwable swallowed) {
                Swallowed.note(swallowed);
            }
        }
        if (fluid != null) {
            int tempK = (stack != null) ? fluid.getTemperature(stack) : fluid.getTemperature();
            if (tempK > 0) {
                return tempK - 273.15;
            }
        }
        if (prop != null) {
            return prop.getDeclaredTemperatureCelsius();
        }
        return 20.0;
    }

    /**
     * Temperature below which a molten fluid solidifies: its configured freezing point, or the temperature GregTech
     * declares for the fluid when it has no configured one.
     */
    public static double getMeltingPointCelsius(Fluid fluid, FluidStack stack, CoolantFluidProperty prop) {
        if (prop != null && Double.isFinite(prop.getFreezingPointCelsius())) {
            return prop.getFreezingPointCelsius();
        }
        return getDeclaredGregTechFluidTemperatureCelsius(fluid, stack, prop);
    }

    /**
     * Temperature fresh coolant enters a loop at: the ambient, or for a molten coolant the temperature GregTech
     * declares for its fluid (Liquid Sodium 495 K), never below its melting point.
     */
    public static double getBaselineInletTemperatureCelsius(Fluid fluid, FluidStack stack, CoolantFluidProperty prop,
        double ambientTemp) {
        if (isMoltenFluid(fluid, stack, prop)) {
            return Math.max(
                ambientTemp,
                Math.max(
                    getMeltingPointCelsius(fluid, stack, prop),
                    getDeclaredGregTechFluidTemperatureCelsius(fluid, stack, prop)));
        }
        return ambientTemp;
    }

    public static double getFluidStackTemperatureCelsius(FluidStack stack) {
        if (stack == null || stack.getFluid() == null) return 20.0;
        if (stack.tag != null) {
            if (stack.tag.hasKey("temperature")) {
                int t = stack.tag.getInteger("temperature");
                return t > 200 ? (t - 273.15) : (double) t;
            }
            if (stack.tag.hasKey("Temperature")) {
                int t = stack.tag.getInteger("Temperature");
                return t > 200 ? (t - 273.15) : (double) t;
            }
            if (stack.tag.hasKey("temp")) {
                double t = stack.tag.getDouble("temp");
                return t > 200.0 ? (t - 273.15) : t;
            }
        }
        int tempK = stack.getFluid()
            .getTemperature(stack);
        return tempK - 273.15;
    }

    public static double calculateAmbientTemperature(World world, int x, int y, int z) {
        if (world != null) {
            try {
                float bTemp = world.getBiomeGenForCoords(x, z)
                    .getFloatTemperature(x, y, z);
                return (bTemp * 100.0 - 32.0) / 1.8;
            } catch (Exception swallowed) {
                Swallowed.note(swallowed);
            }
        }
        return 20.0;
    }
}
