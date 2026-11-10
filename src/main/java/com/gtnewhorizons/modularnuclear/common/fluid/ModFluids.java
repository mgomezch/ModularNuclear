package com.gtnewhorizons.modularnuclear.common.fluid;

import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.modularnuclear.client.texture.TintedSprite;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.enums.ItemList;
import gregtech.api.util.GTModHandler;
import ic2.core.item.ItemFluidCell;

public class ModFluids {

    public static Fluid fluidHeavyWater;
    public static Fluid fluidHeavyWaterSteam;
    /** Heavy-water steam's higher grades, as superheated and supercritical steam are plain steam's. */
    public static Fluid fluidSuperheatedHeavyWaterSteam;
    public static Fluid fluidSupercriticalHeavyWaterSteam;
    public static Fluid fluidCorium;

    public static void init() {
        fluidHeavyWater = registerOrGet("heavywater", 1100, 1100, 300);
        fluidHeavyWaterSteam = registerOrGet("heavywatersteam", -100, 100, 375);
        fluidHeavyWaterSteam.setGaseous(true);
        fluidSuperheatedHeavyWaterSteam = registerOrGet("superheatedheavywatersteam", -100, 100, 600);
        fluidSuperheatedHeavyWaterSteam.setGaseous(true);
        fluidSupercriticalHeavyWaterSteam = registerOrGet("supercriticalheavywatersteam", -100, 100, 1000);
        fluidSupercriticalHeavyWaterSteam.setGaseous(true);

        fluidCorium = registerOrGet("corium", 8000, 36000, 3000);
        fluidCorium.setLuminosity(15);
        fluidCorium.setDensity(8000);
        fluidCorium.setViscosity(36000);
        fluidCorium.setTemperature(3000);

        registerWithGregTech();

        if (FMLCommonHandler.instance() != null && FMLCommonHandler.instance()
            .getSide() != null
            && FMLCommonHandler.instance()
                .getSide()
                .isClient()) {
            registerClientEvents();
        }

    }

    /**
     * Heavy-water steam joins GregTech's steam, and its superheated grade GregTech's superheated steam, so the turbines
     * that take those take them too (the turbine mixins turn their exhaust back into heavy water).
     */
    public static void registerWithGregTech() {
        if (fluidHeavyWaterSteam != null && GTModHandler.sAnySteamFluidIDs != null) {
            GTModHandler.sAnySteamFluidIDs = withFluid(GTModHandler.sAnySteamFluidIDs, fluidHeavyWaterSteam);
        }
        if (fluidSuperheatedHeavyWaterSteam != null && GTModHandler.sSuperHeatedSteamFluidIDs != null) {
            GTModHandler.sSuperHeatedSteamFluidIDs = withFluid(
                GTModHandler.sSuperHeatedSteamFluidIDs,
                fluidSuperheatedHeavyWaterSteam);
        }
    }

    /** The list with the fluid's id in it (a copy if the list cannot be added to). */
    private static java.util.List<Integer> withFluid(java.util.List<Integer> ids, Fluid fluid) {
        int id = fluid.getID();
        if (ids.contains(id)) return ids;
        try {
            ids.add(id);
            return ids;
        } catch (UnsupportedOperationException e) {
            java.util.List<Integer> copy = new java.util.ArrayList<>(ids);
            copy.add(id);
            return copy;
        }
    }

    /**
     * IC2's universal fluid cells of the mod's fluids. Their GregTech cells come from their BartWorks materials
     * (ModWerkstoffs), and GregTech makes the canner recipes of both from the fluid container registry.
     */
    public static void registerContainers() {
        if (!Loader.isModLoaded("IC2")) return;
        for (Fluid fluid : new Fluid[] { fluidHeavyWater, fluidHeavyWaterSteam, fluidSuperheatedHeavyWaterSteam,
            fluidSupercriticalHeavyWaterSteam }) {
            registerUniversalFluidCell(fluid);
        }
    }

    private static void registerUniversalFluidCell(Fluid fluid) {
        if (fluid == null) return;
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
            // Forge prefixes "fluid." to the unlocalized name, which defaults to the fluid's name
            fluid = new Fluid(name);
            fluid.setDensity(density)
                .setViscosity(viscosity)
                .setTemperature(temperature);
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

        /**
         * Heavy water's tint: 23 degrees of hue from water's blue toward purple, the ripples and their opacity
         * pushed out so they stay visible, and a tenth of the way toward a deep violet. See Note [Tinting
         * Minecraft's water]
         */
        private static final TintedSprite.Tint HEAVY_WATER = new TintedSprite.Tint(
            23.0f / 360.0f,
            3.0f,
            2.5f,
            1.0f,
            1.0f,
            0x38007C,
            0.10f);

        @SubscribeEvent
        public void onTextureStitch(TextureStitchEvent.Pre event) {
            if (event.map.getTextureType() == 0) { // terrain / blocks atlas
                if (fluidHeavyWater != null) {
                    fluidHeavyWater.setIcons(
                        tintedWater(event.map, "heavywater_still", "water_still"),
                        tintedWater(event.map, "heavywater_flow", "water_flow"));
                }
                for (Fluid steam : new Fluid[] { fluidHeavyWaterSteam, fluidSuperheatedHeavyWaterSteam,
                    fluidSupercriticalHeavyWaterSteam }) {
                    registerIcons(event.map, steam, "gregtech:fluids/fluid.steam", "gregtech:fluids/fluid.steam");
                }
                registerIcons(
                    event.map,
                    fluidCorium,
                    "modularnuclear:fluids/corium_still",
                    "modularnuclear:fluids/corium_flow");
            }
        }

        /**
         * Heavy water's look: Minecraft's water, as the loaded resource packs have it, tinted purple (HEAVY_WATER)
         * when the atlas is stitched.
         */
        private IIcon tintedWater(TextureMap map, String name, String water) {
            String icon = "modularnuclear:fluids/" + name;
            map.setTextureEntry(
                icon,
                new TintedSprite(
                    icon,
                    new ResourceLocation("minecraft", "textures/blocks/" + water + ".png"),
                    HEAVY_WATER));
            return map.getTextureExtry(icon);
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
