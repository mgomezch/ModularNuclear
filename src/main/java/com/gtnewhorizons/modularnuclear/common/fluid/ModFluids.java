package com.gtnewhorizons.modularnuclear.common.fluid;

import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.IIcon;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ModFluids {

    public static Fluid fluidHeavyWater;
    public static Fluid fluidHighPressureDistilledWater;
    public static Fluid fluidHighPressureHeavyWater;

    public static void init() {
        fluidHeavyWater = registerOrGet("heavywater", 1100, 1100, 300);
        fluidHighPressureDistilledWater = registerOrGet("highpressuredistilledwater", 1000, 1000, 373);
        fluidHighPressureHeavyWater = registerOrGet("highpressureheavywater", 1100, 1100, 374);

        if (FMLCommonHandler.instance()
            .getSide()
            .isClient()) {
            registerClientEvents();
        }
    }

    private static Fluid registerOrGet(String name, int density, int viscosity, int temperature) {
        Fluid fluid = FluidRegistry.getFluid(name);
        if (fluid == null) {
            fluid = new Fluid(name);
            fluid.setDensity(density)
                .setViscosity(viscosity)
                .setTemperature(temperature)
                .setUnlocalizedName("fluid." + name);
            FluidRegistry.registerFluid(fluid);
        }
        return fluid;
    }

    @SideOnly(Side.CLIENT)
    private static void registerClientEvents() {
        MinecraftForge.EVENT_BUS.register(new ClientFluidTextureHandler());
    }

    @SideOnly(Side.CLIENT)
    public static class ClientFluidTextureHandler {

        @SubscribeEvent
        public void onTextureStitch(TextureStitchEvent.Pre event) {
            if (event.map.getTextureType() == 0) { // terrain / blocks atlas
                registerIcons(
                    event.map,
                    fluidHeavyWater,
                    "modularnuclear:fluids/heavywater_still",
                    "modularnuclear:fluids/heavywater_flow");
                registerIcons(
                    event.map,
                    fluidHighPressureDistilledWater,
                    "modularnuclear:fluids/highpressuredistilledwater_still",
                    "modularnuclear:fluids/highpressuredistilledwater_flow");
                registerIcons(
                    event.map,
                    fluidHighPressureHeavyWater,
                    "modularnuclear:fluids/highpressureheavywater_still",
                    "modularnuclear:fluids/highpressureheavywater_flow");
            }
        }

        private void registerIcons(TextureMap map, Fluid fluid, String stillPath, String flowPath) {
            if (fluid != null) {
                IIcon still = map.registerIcon(stillPath);
                IIcon flow = map.registerIcon(flowPath);
                fluid.setIcons(still, flow);
            }
        }
    }
}
