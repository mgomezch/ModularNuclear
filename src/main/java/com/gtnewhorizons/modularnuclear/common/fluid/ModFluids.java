package com.gtnewhorizons.modularnuclear.common.fluid;

import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.modularnuclear.common.item.ModItems;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.enums.ItemList;
import ic2.core.item.ItemFluidCell;

public class ModFluids {

    public static Fluid fluidHeavyWater;
    public static Fluid fluidCorium;

    public static void init() {
        fluidHeavyWater = registerOrGet("heavywater", 1100, 1100, 300);
        fluidCorium = registerOrGet("corium", 8000, 18000, 3000);
        fluidCorium.setLuminosity(15);
        fluidCorium.setDensity(8000);
        fluidCorium.setViscosity(18000);
        fluidCorium.setTemperature(3000);

        try {
            if (FMLCommonHandler.instance() != null && FMLCommonHandler.instance()
                .getSide() != null
                && FMLCommonHandler.instance()
                    .getSide()
                    .isClient()) {
                registerClientEvents();
            }
        } catch (Throwable ignored) {}
    }

    public static void registerContainers() {
        registerFluidContainers(fluidHeavyWater, ModItems.cellHeavyWater);
    }

    private static void registerFluidContainers(Fluid fluid, Item cellItem) {
        if (fluid == null) return;

        ItemStack emptyCell = ItemList.Cell_Empty.get(1);
        if (emptyCell != null && cellItem != null) {
            FluidContainerRegistry.registerFluidContainer(
                new FluidContainerRegistry.FluidContainerData(
                    new FluidStack(fluid, 1000),
                    new ItemStack(cellItem),
                    emptyCell));
        }

        if (Loader.isModLoaded("IC2")) {
            registerUniversalFluidCell(fluid);
        }
    }

    private static void registerUniversalFluidCell(Fluid fluid) {
        ItemStack emptyUniversalCell = ItemList.Cell_Universal_Fluid.get(1);
        ItemStack filledUniversalCell = ItemFluidCell.getUniversalFluidCell(new FluidStack(fluid, 1000));
        if (emptyUniversalCell != null && filledUniversalCell != null) {
            FluidContainerRegistry.registerFluidContainer(
                new FluidContainerRegistry.FluidContainerData(
                    new FluidStack(fluid, 1000),
                    filledUniversalCell,
                    emptyUniversalCell));
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
                    fluidCorium,
                    "modularnuclear:fluids/corium_still",
                    "modularnuclear:fluids/corium_flow");
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
