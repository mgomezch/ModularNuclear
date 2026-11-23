package com.gtnewhorizons.modularnuclear.common.fluid;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtnewhorizons.modularnuclear.ModularNuclear;
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
    // At integration into GT5-Unofficial: mgomezch/ModularNuclear#45
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
         * pushed out so they stay visible, and a tenth of the way toward a deep violet.
         */
        // See Note [Tinting heavy water's textures]
        private static final TintedSprite.Tint HEAVY_WATER = new TintedSprite.Tint(
            23.0f / 360.0f,
            3.0f,
            2.5f,
            1.0f,
            1.0f,
            0x38007C,
            0.10f);

        /**
         * Heavy-water steam's tint: the steam of the same grade blended 30% of the way toward heavy water's average
         * colour, its mottle pushed out to make up for what the blend takes.
         */
        // See Note [Tinting heavy water's textures]
        private static final TintedSprite.Tint HEAVY_WATER_STEAM = new TintedSprite.Tint(
            0.0f,
            1.4f,
            1.0f,
            1.0f,
            1.0f,
            0x612AE8,
            0.30f);

        /** Where a resource pack can set the tints. */
        // See wrinkle (W3) of Note [Tinting heavy water's textures]
        private static final ResourceLocation PACK_TINTS = new ResourceLocation("modularnuclear", "fluid_tints.json");

        /** This stitch's tints, by fluid name: the mod's, with a resource pack's fields over them. */
        private final Map<String, TintedSprite.Tint> tints = new HashMap<>();

        @SubscribeEvent
        public void onTextureStitch(TextureStitchEvent.Pre event) {
            if (event.map.getTextureType() == 0) { // terrain / blocks atlas
                tints.clear();
                tints.put("heavywater", HEAVY_WATER);
                for (String steam : new String[] { "heavywatersteam", "superheatedheavywatersteam",
                    "supercriticalheavywatersteam" }) {
                    tints.put(steam, HEAVY_WATER_STEAM);
                }
                packTints(
                    Minecraft.getMinecraft()
                        .getResourceManager());
                if (fluidHeavyWater != null) {
                    fluidHeavyWater.setIcons(
                        tintedWater(event.map, "heavywater_still", "water_still"),
                        tintedWater(event.map, "heavywater_flow", "water_flow"));
                }
                if (fluidHeavyWaterSteam != null) {
                    // Steam as GTNH shows it, Railcraft's, or else GregTech's played forward and back
                    // See wrinkle (W2) of Note [Tinting heavy water's textures]
                    boolean railcraft = Loader.isModLoaded("Railcraft");
                    IIcon steam = tinted(
                        event.map,
                        "heavywatersteam",
                        railcraft ? new ResourceLocation("railcraft", "textures/blocks/fluids/steam_still.png")
                            : new ResourceLocation("gregtech", "textures/blocks/fluids/fluid.steam.png"),
                        tints.get("heavywatersteam"),
                        !railcraft);
                    fluidHeavyWaterSteam.setIcons(steam, steam);
                }
                if (fluidSuperheatedHeavyWaterSteam != null) {
                    IIcon steam = tinted(
                        event.map,
                        "superheatedheavywatersteam",
                        new ResourceLocation("ic2", "textures/blocks/fluids/superheatedsteam_still.png"),
                        tints.get("superheatedheavywatersteam"),
                        false);
                    fluidSuperheatedHeavyWaterSteam.setIcons(steam, steam);
                }
                if (fluidSupercriticalHeavyWaterSteam != null) {
                    fluidSupercriticalHeavyWaterSteam.setIcons(
                        tinted(
                            event.map,
                            "supercriticalheavywatersteam_still",
                            new ResourceLocation(
                                "goodgenerator",
                                "textures/blocks/fluids/supercriticalSteam.still.png"),
                            tints.get("supercriticalheavywatersteam"),
                            false),
                        tinted(
                            event.map,
                            "supercriticalheavywatersteam_flow",
                            new ResourceLocation(
                                "goodgenerator",
                                "textures/blocks/fluids/supercriticalSteam.flowing.png"),
                            tints.get("supercriticalheavywatersteam"),
                            false));
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
         * when the atlas is stitched, unless a resource pack draws heavy water itself.
         */
        // See Note [Tinting heavy water's textures]
        private IIcon tintedWater(TextureMap map, String name, String water) {
            return tinted(
                map,
                name,
                new ResourceLocation("minecraft", "textures/blocks/" + water + ".png"),
                tints.get("heavywater"),
                false);
        }

        /** Puts a resource pack's fluid_tints.json entries over the mod's tints, if a pack has the file. */
        // See wrinkle (W3) of Note [Tinting heavy water's textures]
        private void packTints(IResourceManager manager) {
            IResource resource;
            try {
                resource = manager.getResource(PACK_TINTS);
            } catch (IOException e) {
                return; // no pack sets them
            }
            JsonObject root;
            try (Reader in = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                root = new JsonParser().parse(in)
                    .getAsJsonObject();
            } catch (IOException | RuntimeException e) {
                ModularNuclear.LOG.error("Could not read " + PACK_TINTS + "; keeping the mod's fluid tints", e);
                return;
            }
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                TintedSprite.Tint tint = tints.get(entry.getKey());
                if (tint == null) {
                    ModularNuclear.LOG.warn(PACK_TINTS + " names no fluid the mod tints: " + entry.getKey());
                    continue;
                }
                try {
                    tints.put(
                        entry.getKey(),
                        tint.withOverrides(
                            entry.getValue()
                                .getAsJsonObject()));
                } catch (RuntimeException e) {
                    ModularNuclear.LOG.error("Could not read " + entry.getKey() + " in " + PACK_TINTS, e);
                }
            }
        }

        /** The icon modularnuclear:fluids/name: source tinted, unless a resource pack draws the icon itself. */
        private IIcon tinted(TextureMap map, String name, ResourceLocation source, TintedSprite.Tint tint,
            boolean pingPong) {
            String icon = "modularnuclear:fluids/" + name;
            map.setTextureEntry(icon, new TintedSprite(icon, source, tint, pingPong));
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
