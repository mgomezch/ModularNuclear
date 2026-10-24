package com.gtnewhorizons.modularnuclear.common.nei;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData;
import com.gtnewhorizons.modularnuclear.common.item.ModItems;
import com.gtnewhorizons.modularnuclear.common.metatileentity.ModMetaTileEntities;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.TemplateRecipeHandler;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;

/**
 * Unified NEI handler for non-fuel MPTR reactor components:
 * Reflectors, Moderators, Radiovoltaic Plates, Thermal Insulators,
 * Coolant Cells, Coolant Fluids, IC2 Heat Vents & Exchangers, Control Rods,
 * and Empty Core Tiles.
 * <p>
 * Page 1: Fast & Thermal neutron interaction (scattering, absorption, moderation fraction, reflection).
 * Page 2: Thermal & energy properties (conduction, active cooling/transfer, direct EU generation)
 * and durability / radiolytic transmutation byproducts.
 * </p>
 */
public class NEINeutronInteractionHandler extends TemplateRecipeHandler {

    public static final ResourceLocation TEXTURE_ATLAS = new ResourceLocation(
        "modularnuclear",
        "textures/gui/nei/neutron_interaction_atlas.png");

    public static final ResourceLocation LONG_ARROW = new ResourceLocation(
        "modularnuclear",
        "textures/gui/nei/long_arrow.png");

    public static final String OVERLAY_ID = "gt.nei.neutron_interaction";

    public static final int RV_PANEL_X = 6;
    public static final int RV_PANEL_Y = 237;
    public static final int RV_PANEL_W = 154;
    public static final int RV_PANEL_H = 72;

    public static final int RV_SCREEN_X = 8;
    public static final int RV_SCREEN_Y = 249;
    public static final int RV_SCREEN_W = 150;
    public static final int RV_SCREEN_H = 58;

    public static final int RV_PLOT_X = 28;
    public static final int RV_PLOT_Y = 252;
    public static final int RV_PLOT_W = 104;
    public static final int RV_PLOT_H = 42;

    public static class ComponentStatsData {

        public final ItemStack stack;
        public final List<ItemStack> alternateStacks = new ArrayList<>();
        public final String name;
        public final String category;

        // Page 1: Neutron Interaction
        public final double fastScattering;
        public final double fastAbsorption;
        public final double slowingProbability;
        public final double thermalScattering;
        public final double thermalAbsorption;

        // Reflection
        public final boolean isReflector;
        public final double reflectionFast;
        public final double reflectionThermal;

        // Page 2: Thermal & Energy Properties
        public final double heatTransferCoeff;
        public final double thermalInsulationFactor;
        public final double selfCoolingRate;
        public final double componentCoolingRate;
        public final double heatTransferRate;
        public final double maxTemperature;
        public final double boilingPoint;
        public final double specificHeat;

        // Direct EU Production
        public final boolean producesDirectEU;
        public final long maxDirectEU;
        public final String directEUTier;
        public final double directEUPenNeutronFast;
        public final double directEUPenNeutronThermal;

        // Durability / Capacity / Transmutation / Byproducts
        public final boolean hasDurability;
        public final long durability;
        public final String durabilityLabel;
        public final boolean hasTransmutation;
        public final ItemStack transmutationOutput;
        public final long transmutationNeutrons;

        public final String extraInfo;

        public final boolean isRadiovoltaic;
        public final double saturationFlux;

        public double calculatePower(double absorbedFlux) {
            double effectiveSat = saturationFlux > 0 ? saturationFlux : 50.0;
            return maxDirectEU * Math.tanh(absorbedFlux / effectiveSat);
        }

        public double calculateHeat(double absorbedFlux) {
            double totalEnergy = absorbedFlux * 20.0;
            double power = calculatePower(absorbedFlux);
            return Math.max(0.0, totalEnergy - power);
        }

        public ComponentStatsData(ItemStack stack, String name, String category, double fastScattering,
            double fastAbsorption, double slowingProbability, double thermalScattering, double thermalAbsorption,
            boolean isReflector, double reflectionFast, double reflectionThermal, double heatTransferCoeff,
            double thermalInsulationFactor, double selfCoolingRate, double componentCoolingRate,
            double heatTransferRate, double maxTemperature, double boilingPoint, double specificHeat,
            boolean producesDirectEU, long maxDirectEU, String directEUTier, double directEUPenNeutronFast,
            double directEUPenNeutronThermal, boolean hasDurability, long durability, String durabilityLabel,
            boolean hasTransmutation, ItemStack transmutationOutput, long transmutationNeutrons, String extraInfo) {
            this(stack, name, category, fastScattering, fastAbsorption, slowingProbability, thermalScattering,
                thermalAbsorption, isReflector, reflectionFast, reflectionThermal, heatTransferCoeff,
                thermalInsulationFactor, selfCoolingRate, componentCoolingRate, heatTransferRate, maxTemperature,
                boilingPoint, specificHeat, producesDirectEU, maxDirectEU, directEUTier, directEUPenNeutronFast,
                directEUPenNeutronThermal, hasDurability, durability, durabilityLabel, hasTransmutation,
                transmutationOutput, transmutationNeutrons, extraInfo, false, 0.0);
        }

        public ComponentStatsData(ItemStack stack, String name, String category, double fastScattering,
            double fastAbsorption, double slowingProbability, double thermalScattering, double thermalAbsorption,
            boolean isReflector, double reflectionFast, double reflectionThermal, double heatTransferCoeff,
            double thermalInsulationFactor, double selfCoolingRate, double componentCoolingRate,
            double heatTransferRate, double maxTemperature, double boilingPoint, double specificHeat,
            boolean producesDirectEU, long maxDirectEU, String directEUTier, double directEUPenNeutronFast,
            double directEUPenNeutronThermal, boolean hasDurability, long durability, String durabilityLabel,
            boolean hasTransmutation, ItemStack transmutationOutput, long transmutationNeutrons, String extraInfo,
            boolean isRadiovoltaic, double saturationFlux) {
            this.stack = stack;
            this.name = name;
            this.category = category;
            this.fastScattering = fastScattering;
            this.fastAbsorption = fastAbsorption;
            this.slowingProbability = slowingProbability;
            this.thermalScattering = thermalScattering;
            this.thermalAbsorption = thermalAbsorption;
            this.isReflector = isReflector;
            this.reflectionFast = reflectionFast;
            this.reflectionThermal = reflectionThermal;
            this.heatTransferCoeff = heatTransferCoeff;
            this.thermalInsulationFactor = thermalInsulationFactor;
            this.selfCoolingRate = selfCoolingRate;
            this.componentCoolingRate = componentCoolingRate;
            this.heatTransferRate = heatTransferRate;
            this.maxTemperature = maxTemperature;
            this.boilingPoint = boilingPoint;
            this.specificHeat = specificHeat;
            this.producesDirectEU = producesDirectEU;
            this.maxDirectEU = maxDirectEU;
            this.directEUTier = directEUTier;
            this.directEUPenNeutronFast = directEUPenNeutronFast;
            this.directEUPenNeutronThermal = directEUPenNeutronThermal;
            this.hasDurability = hasDurability;
            this.durability = durability;
            this.durabilityLabel = durabilityLabel;
            this.hasTransmutation = hasTransmutation;
            this.transmutationOutput = transmutationOutput;
            this.transmutationNeutrons = transmutationNeutrons;
            this.extraInfo = extraInfo;
            this.isRadiovoltaic = isRadiovoltaic;
            this.saturationFlux = saturationFlux;
        }
    }

    /**
     * Unified recipe for MPTR non-fuel component:
     * Renders Header (Role/Description), Fast Neutrons, Thermal Neutrons, Heat, and Durability & Byproducts cards
     * all on a single scrollable page.
     */
    public class CachedNeutronInteractionRecipe extends CachedRecipe {

        public final PositionedStack input;
        public final PositionedStack inputThermal;
        public final PositionedStack inputHeat;
        public final PositionedStack durabilityInput;
        public final PositionedStack transmutationOutput;
        public final PositionedStack catalyst;
        public final ComponentStatsData data;

        public CachedNeutronInteractionRecipe(ComponentStatsData data) {
            this.data = data;
            List<ItemStack> allVariants = new ArrayList<>();
            if (data.stack != null) allVariants.add(data.stack);
            allVariants.addAll(data.alternateStacks);

            // Card 1 (Fast neutrons): slot box at (74, 56) -> stack at (75, 57)
            this.input = new PositionedStack(allVariants, 75, 57);

            // Card 2 (Thermal neutrons): slot box at (66, 129) -> stack at (67, 130)
            this.inputThermal = new PositionedStack(allVariants, 67, 130);

            // Card 3 (Heat): slot box at (57, 193) -> stack at (58, 194)
            this.inputHeat = new PositionedStack(allVariants, 58, 194);

            // Card 4 (Durability & byproducts):
            if (!data.isRadiovoltaic) {
                int durSlotX = data.transmutationOutput != null ? 30 : 66;
                this.durabilityInput = new PositionedStack(allVariants, durSlotX + 1, 266);
                this.transmutationOutput = data.transmutationOutput != null
                    ? new PositionedStack(data.transmutationOutput, 101, 266)
                    : null;
            } else {
                this.durabilityInput = null;
                this.transmutationOutput = null;
            }

            // Header: catalyst slot box at (6, 2) -> stack at (7, 3)
            ItemStack ctrl = getControllerStack();
            this.catalyst = ctrl != null ? new PositionedStack(ctrl, 7, 3) : null;
        }

        @Override
        public PositionedStack getIngredient() {
            return input;
        }

        @Override
        public PositionedStack getResult() {
            return transmutationOutput != null ? transmutationOutput : input;
        }

        @Override
        public List<PositionedStack> getOtherStacks() {
            List<PositionedStack> list = new ArrayList<>();
            list.add(inputThermal);
            list.add(inputHeat);
            if (durabilityInput != null) {
                list.add(durabilityInput);
            }
            if (transmutationOutput != null) {
                list.add(transmutationOutput);
            }
            if (catalyst != null) {
                list.add(catalyst);
            }
            return list;
        }
    }

    /**
     * Legacy subclass retained for backward compatibility.
     */
    public class CachedComponentThermalRecipe extends CachedNeutronInteractionRecipe {

        public final PositionedStack thermalInput;

        public CachedComponentThermalRecipe(ComponentStatsData data) {
            super(data);
            this.thermalInput = this.inputHeat;
        }
    }

    public static final List<ComponentStatsData> ALL_COMPONENTS = new ArrayList<>();
    private static boolean initialized = false;

    public static synchronized List<ComponentStatsData> getAllComponents() {
        initData();
        return Collections.unmodifiableList(ALL_COMPONENTS);
    }

    public static boolean sTestMode = false;

    public static synchronized void resetData() {
        initialized = false;
        ALL_COMPONENTS.clear();
    }

    private static boolean isTestMode() {
        if (sTestMode) return true;
        try {
            cpw.mods.fml.common.Loader ldr = cpw.mods.fml.common.Loader.instance();
            if (ldr == null || ldr.getModList() == null
                || ldr.getModList()
                    .isEmpty()) {
                return true;
            }
        } catch (Throwable t) {
            return true;
        }
        return false;
    }

    private static ItemStack createTestPlaceholder(String name) {
        ItemStack stack = new ItemStack(net.minecraft.init.Items.paper);
        stack.setStackDisplayName(name);
        return stack;
    }

    public static ItemStack getControllerStack() {
        if (ModMetaTileEntities.reactor != null) {
            return ModMetaTileEntities.reactor.copy();
        }
        if (gregtech.api.GregTechAPI.METATILEENTITIES != null && ModMetaTileEntities.ID_NUCLEAR_REACTOR >= 0
            && ModMetaTileEntities.ID_NUCLEAR_REACTOR < gregtech.api.GregTechAPI.METATILEENTITIES.length
            && gregtech.api.GregTechAPI.METATILEENTITIES[ModMetaTileEntities.ID_NUCLEAR_REACTOR] != null) {
            return gregtech.api.GregTechAPI.METATILEENTITIES[ModMetaTileEntities.ID_NUCLEAR_REACTOR].getStackForm(1L);
        }
        return isTestMode() ? createTestPlaceholder("Nuclear Reactor") : null;
    }

    private static ItemStack getReflectorPlate(Materials mat, String materialName) {
        ItemStack s = null;
        if (mat != null) {
            s = GTOreDictUnificator.get(OrePrefixes.plate, mat, 1L);
        }
        if (s == null && materialName != null) {
            s = getFirstOre("plate" + materialName);
        }
        if (s == null && isTestMode()) {
            s = createTestPlaceholder(materialName + " Plate");
        }
        return s;
    }

    private static ItemStack getModeratorRod(Materials mat, String materialName) {
        ItemStack s = null;
        if (mat != null) {
            s = GTOreDictUnificator.get(OrePrefixes.stickLong, mat, 1L);
        }
        if (s == null && materialName != null) {
            s = getFirstOre("stickLong" + materialName);
        }
        if (s == null && isTestMode()) {
            s = createTestPlaceholder(materialName + " Long Rod");
        }
        return s;
    }

    public static synchronized void initData() {
        if (initialized) return;
        initialized = true;
        ALL_COMPONENTS.clear();

        // 1. REFLECTORS - High scattering / reflection back into adjacent core cells
        addReflector(
            getReflectorPlate(Materials.Gold, "Gold"),
            "Gold Plate Reflector",
            0.15,
            0.10,
            0.85,
            0.90,
            0.0,
            0.30,
            0,
            "Indestructible plate reflector (k = 0.30)");
        addReflector(
            getReflectorPlate(Materials.StainlessSteel, "StainlessSteel"),
            "Stainless Steel Plate Reflector",
            0.10,
            0.05,
            0.90,
            0.95,
            0.0,
            0.20,
            0,
            "Indestructible plate reflector (k = 0.20)");
        addReflector(
            getReflectorPlate(Materials.TungstenCarbide, "TungstenCarbide"),
            "Tungsten Carbide Plate Reflector",
            0.07,
            0.02,
            0.93,
            0.98,
            0.0,
            0.10,
            0,
            "Indestructible plate reflector (k = 0.10)");
        addIC2Reflector(
            "reactorReflector",
            "Neutron Reflector",
            0.01,
            0.02,
            0.95,
            0.98,
            0.20,
            0.15,
            10_000,
            "Standard beryllium reflector (10,000 impacts)");
        addIC2Reflector(
            "reactorReflectorThick",
            "Thick Neutron Reflector",
            0.005,
            0.01,
            0.98,
            0.99,
            0.25,
            0.20,
            40_000,
            "Reinforced reflector (40,000 impacts)");
        addGTReflector(
            ItemList.Neutron_Reflector,
            "Iridium Neutron Reflector",
            0.01,
            0.02,
            0.95,
            0.98,
            0.20,
            0.15,
            0,
            "Permanent iridium reflector (indestructible)");
        addGTReflector(
            ItemList.Reactor_NeutronReflector,
            "Thick Iridium Neutron Reflector",
            0.005,
            0.01,
            0.98,
            0.99,
            0.25,
            0.20,
            0,
            "Permanent dense iridium reflector (indestructible)");

        // 2. MODERATORS - Restricted strictly to long-rod form; slows fast neutrons to thermal
        ItemStack stickGraphite = getModeratorRod(Materials.Graphite, "Graphite");
        ItemStack dustAsh = GTOreDictUnificator.get(OrePrefixes.dust, Materials.Ash, 1L);
        if (dustAsh == null && isTestMode()) dustAsh = createTestPlaceholder("Ash Dust");
        addModerator(
            stickGraphite,
            "Graphite Long Rod",
            0.93,
            0.002,
            0.50,
            0.621,
            0.009,
            0.05,
            2500.0,
            80_000,
            dustAsh,
            "Standard moderator | Burns into ash above 2500 °C");

        ItemStack stickCarbon = getModeratorRod(Materials.Carbon, "Carbon");
        addModerator(
            stickCarbon,
            "Carbon Long Rod",
            0.90,
            0.003,
            0.45,
            0.60,
            0.012,
            0.05,
            2200.0,
            10_000,
            dustAsh,
            "Budget carbon moderator | Burns into ash above 2200 °C");

        ItemStack stickBeryllium = getModeratorRod(Materials.Beryllium, "Beryllium");
        addModerator(
            stickBeryllium,
            "Beryllium Long Rod",
            0.96,
            0.001,
            0.65,
            0.85,
            0.004,
            0.15,
            2600.0,
            240_000,
            null,
            "High-performance moderator | High thermal conductivity (k = 0.15)");

        // 3. CONTROL RODS - Throttle reactivity via neutron absorption (burnout capacity)
        addControlRod(
            Materials.Silver,
            "Silver",
            "Silver Control Rod",
            0.20,
            0.50,
            0.05,
            0.05,
            0.20,
            100_000_000L,
            "Soft control absorber | Medium thermal capture");
        addControlRod(
            Materials.Boron,
            "Boron",
            "Boron Control Rod",
            0.35,
            0.70,
            0.05,
            0.05,
            0.08,
            100_000_000L,
            "Intermediate absorber | 70% thermal absorption");
        addControlRod(
            Materials.Cadmium,
            "Cadmium",
            "Cadmium Control Rod",
            0.50,
            0.85,
            0.05,
            0.05,
            0.12,
            100_000_000L,
            "Standard reactor control rod | 85% thermal absorption");
        addControlRod(
            Materials.Indium,
            "Indium",
            "Indium Control Rod",
            0.65,
            0.95,
            0.05,
            0.05,
            0.14,
            100_000_000L,
            "High-efficiency absorber | 95% thermal absorption");
        addControlRod(
            null,
            "Hafnium",
            "Hafnium Control Rod",
            0.80,
            0.99,
            0.05,
            0.05,
            0.16,
            100_000_000L,
            "Extreme absorber | 99% thermal capture, high fast capture");

        // 4. RADIOVOLTAIC PLATES - 40% (HV) / 60% (EV) absorption, generates direct EU from absorbed neutron flux
        ComponentData.RadiovoltaicData rvHv = null;
        ComponentData.RadiovoltaicData rvEv = null;
        try {
            if (ReactorDataRegistry.getComponentData() != null
                && ReactorDataRegistry.getComponentData().radiovoltaics != null) {
                for (ComponentData.RadiovoltaicData r : ReactorDataRegistry.getComponentData().radiovoltaics) {
                    if ("radiovoltaic_hv".equals(r.id) || r.tier == 1) rvHv = r;
                    else if ("radiovoltaic_ev".equals(r.id) || r.tier == 2) rvEv = r;
                }
            }
        } catch (Throwable ignored) {}

        double hvAbs = (rvHv != null) ? rvHv.absorptionFast : 0.40;
        double hvSat = (rvHv != null && rvHv.saturationFlux > 0) ? rvHv.saturationFlux : 50.0;
        long hvMaxEU = (rvHv != null && rvHv.maxEU > 0) ? rvHv.maxEU : 1024L;

        double evAbs = (rvEv != null) ? rvEv.absorptionFast : 0.60;
        double evSat = (rvEv != null && rvEv.saturationFlux > 0) ? rvEv.saturationFlux : 150.0;
        long evMaxEU = (rvEv != null && rvEv.maxEU > 0) ? rvEv.maxEU : 4096L;

        addRadiovoltaic(
            ModItems.radiovoltaicPlateHV != null ? ModItems.radiovoltaicPlateHV.copy() : null,
            "HV Radiovoltaic Plate",
            1,
            hvMaxEU,
            "HV (2A)",
            hvSat,
            hvAbs,
            "Converts absorbed neutrons to direct EU | Max 1024 EU/t");
        addRadiovoltaic(
            ModItems.radiovoltaicPlateEV != null ? ModItems.radiovoltaicPlateEV.copy() : null,
            "EV Radiovoltaic Plate",
            2,
            evMaxEU,
            "EV (2A)",
            evSat,
            evAbs,
            "Converts absorbed neutrons to direct EU | Max 4096 EU/t");

        // 5. THERMAL INSULATORS - Restrict heat conduction across reactor core zones
        addInsulator(
            getItemListStack(ItemList.Naquarite_Universal_Insulator_Foil),
            "Naquarite Universal Insulator Foil",
            1.0,
            0.005,
            1.0,
            1.0,
            0.0,
            0.0,
            "Total thermal barrier (100%) | 100% neutron absorption shield");
        addInsulator(
            resolveStack("dreamcraft:MicaInsulatorFoil"),
            "Mica Insulator Foil",
            0.60,
            0.01,
            0.01,
            0.01,
            0.05,
            0.05,
            "Thermal barrier (60%) | k = 0.01 conduction");
        addInsulator(
            resolveStack("GalaxySpace:item.ThermalClothT2"),
            "Thermal Cloth T2",
            0.40,
            0.01,
            0.01,
            0.01,
            0.05,
            0.05,
            "Thermal barrier (40%) | k = 0.01 conduction");
        addInsulator(
            resolveStack("GalacticraftMars:item.itemBasicAsteroids:7"),
            "Thermal Cloth",
            0.20,
            0.01,
            0.01,
            0.01,
            0.05,
            0.05,
            "Thermal barrier (20%) | k = 0.01 conduction");

        // 6. COOLANT CELLS - Heat storage sinks, auto-ejected when full for freezer re-cooling
        addCoolantCell("reactorCoolantSimple", null, "10k Coolant Cell", 250_000L, 0.40);
        addCoolantCell("reactorCoolantTriple", null, "30k Coolant Cell", 750_000L, 0.40);
        addCoolantCell("reactorCoolantSix", null, "60k Coolant Cell", 1_500_000L, 0.40);
        addCoolantCell("reactorCoolantHelium1", ItemList.Reactor_Coolant_He_1, "60k He-Coolant Cell", 1_500_000L, 0.40);
        addCoolantCell(
            "reactorCoolantHelium2",
            ItemList.Reactor_Coolant_He_3,
            "180k He-Coolant Cell",
            4_500_000L,
            0.40);
        addCoolantCell(
            "reactorCoolantHelium3",
            ItemList.Reactor_Coolant_He_6,
            "360k He-Coolant Cell",
            9_000_000L,
            0.40);
        addCoolantCell("reactorCoolantNaK1", ItemList.Reactor_Coolant_NaK_1, "60k NaK-Coolant Cell", 4_500_000L, 0.50);
        addCoolantCell(
            "reactorCoolantNaK2",
            ItemList.Reactor_Coolant_NaK_3,
            "180k NaK-Coolant Cell",
            13_500_000L,
            0.50);
        addCoolantCell(
            "reactorCoolantNaK3",
            ItemList.Reactor_Coolant_NaK_6,
            "360k NaK-Coolant Cell",
            27_000_000L,
            0.50);
        addCoolantCell(null, ItemList.Reactor_Coolant_Sp_1, "10k Sp-Coolant Cell", 9_000_000L, 0.60);
        addCoolantCell(null, ItemList.Reactor_Coolant_Sp_2, "20k Sp-Coolant Cell", 18_000_000L, 0.60);
        addCoolantCell(null, ItemList.Reactor_Coolant_Sp_3, "30k Sp-Coolant Cell", 27_000_000L, 0.60);
        addCoolantCell(null, ItemList.Reactor_Coolant_Sp_6, "60k Sp-Coolant Cell", 54_000_000L, 0.60);
        addCoolantCell("reactorCondensator", null, "Condensator", 2_500_000L, 0.45);
        addCoolantCell("reactorCondensatorLap", null, "Lapotron Condensator", 10_000_000L, 0.45);

        // 7. COOLANT FLUIDS (for conduction & radiolytic transmutation)
        addFluidCoolant(
            "ic2distilledwater",
            "Distilled Water",
            0.70,
            0.05,
            0.25,
            0.70,
            0.10,
            0.30,
            100.0,
            4.184,
            true,
            getFluidDisplay("deuterium", 1000),
            40L,
            "Sub-boiling coolant (boils at 100 °C) | Transmutes to Deuterium");
        addFluidCoolant(
            "heavywater",
            "Heavy Water",
            0.85,
            0.005,
            0.90,
            0.85,
            0.01,
            0.30,
            101.4,
            4.220,
            true,
            getFluidDisplay("tritium", 1000),
            100L,
            "Exceptional moderator (90% slowing) | Transmutes to Tritium");
        addFluidCoolant(
            "ic2coolant",
            "IC2 Coolant",
            0.45,
            0.03,
            0.05,
            0.45,
            0.12,
            0.50,
            300.0,
            8.000,
            false,
            null,
            0L,
            "Non-boiling high-capacity coolant | Cycles to Hot Coolant");
        addFluidCoolant(
            "molten.sodium",
            "Molten Sodium",
            0.20,
            0.01,
            0.05,
            0.20,
            0.05,
            0.70,
            883.0,
            1.280,
            false,
            null,
            0L,
            "Liquid metal coolant (melts at 97.8 °C, boils at 883 °C) | k = 0.70");
        addFluidCoolant(
            "molten.lead",
            "Molten Lead",
            0.15,
            0.005,
            0.02,
            0.15,
            0.02,
            0.75,
            1749.0,
            0.160,
            false,
            null,
            0L,
            "High-temperature heavy liquid metal | k = 0.75 conduction");

        // 8. IC2 HEAT VENTS & HEAT EXCHANGERS
        addHeatVent("reactorVent", "Reactor Heat Vent", 6.0, 0.0, 0.40, 128L, 512L, "Self-cooling: 6 Hu/t | 128 EU/t");
        addHeatVent(
            "reactorVentGold",
            "Advanced Heat Vent",
            12.0,
            0.0,
            0.45,
            512L,
            2048L,
            "Self-cooling: 12 Hu/t | 512 EU/t");
        addHeatVent(
            "reactorVentSpread",
            "Overclocked Heat Vent",
            8.0,
            0.0,
            0.40,
            1024L,
            4096L,
            "Self-cooling: 8 Hu/t | 1024 EU/t");
        addHeatVent(
            "reactorVentDiamond",
            "Component Heat Vent",
            0.0,
            20.0,
            0.50,
            2048L,
            8192L,
            "Adjacent cooling: 20 Hu/t | 2048 EU/t");

        addHeatExchanger(
            "reactorHeatSwitch",
            "Heat Exchanger",
            12.0,
            0.40,
            256L,
            1024L,
            "Heat transfer: 12 Hu/t | 256 EU/t");
        addHeatExchanger(
            "reactorHeatSwitchSpread",
            "Advanced Heat Exchanger",
            24.0,
            0.45,
            512L,
            2048L,
            "Heat transfer: 24 Hu/t | 512 EU/t");
        addHeatExchanger(
            "reactorHeatSwitchDiamond",
            "Component Heat Exchanger",
            36.0,
            0.50,
            1024L,
            4096L,
            "Adjacent component heat transfer: 36 Hu/t | 1024 EU/t");

        // 9. EMPTY CORE TILE - Unimpeded neutron streaming
        ItemStack emptyStack = ModItems.emptyCoreTile != null ? new ItemStack(ModItems.emptyCoreTile)
            : (isTestMode() ? createTestPlaceholder("Empty Core Tile") : null);
        if (emptyStack != null) {
            ALL_COMPONENTS.add(
                new ComponentStatsData(
                    emptyStack,
                    "Empty Core Tile",
                    "Empty Core Tile",
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    false,
                    0.0,
                    0.0,
                    0.02,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    false,
                    0L,
                    null,
                    0.0,
                    0.0,
                    false,
                    0L,
                    "N/A",
                    false,
                    null,
                    0L,
                    "Open core space | Neutrons stream through unimpeded"));
        }
    }

    // =========================================================================
    // Factory Helpers
    // =========================================================================

    private static void addReflector(ItemStack stack, String name, double fastAbs, double thermAbs, double fastRefl,
        double thermRefl, double slowing, double conduction, long durability, String extra) {
        if (stack == null && isTestMode()) stack = createTestPlaceholder(name);
        if (stack == null) return;
        ALL_COMPONENTS.add(
            new ComponentStatsData(
                stack,
                name,
                "Reflector",
                fastRefl,
                fastAbs,
                slowing,
                thermRefl,
                thermAbs,
                true,
                fastRefl,
                thermRefl,
                conduction,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                false,
                0L,
                null,
                0.0,
                0.0,
                durability > 0,
                durability,
                durability > 0 ? "Impacts" : "Indestructible",
                false,
                null,
                0L,
                extra));
    }

    private static void addIC2Reflector(String ic2Name, String name, double fastAbs, double thermAbs, double fastRefl,
        double thermRefl, double slowing, double conduction, long durability, String extra) {
        ItemStack stack = resolveStack("IC2:" + ic2Name);
        if (stack == null) stack = resolveStack("IC2:" + ic2Name + ":1");
        if (stack == null && isTestMode()) stack = createTestPlaceholder(name);
        if (stack != null) {
            addReflector(stack, name, fastAbs, thermAbs, fastRefl, thermRefl, slowing, conduction, durability, extra);
        }
    }

    private static void addGTReflector(ItemList item, String name, double fastAbs, double thermAbs, double fastRefl,
        double thermRefl, double slowing, double conduction, long durability, String extra) {
        ItemStack stack = null;
        if (item != null && item.hasBeenSet()) {
            stack = item.get(1L);
        } else if (isTestMode()) {
            stack = createTestPlaceholder(name);
        }
        if (stack != null) {
            addReflector(stack, name, fastAbs, thermAbs, fastRefl, thermRefl, slowing, conduction, durability, extra);
        }
    }

    private static void addModerator(ItemStack stack, String name, double fastScat, double fastAbs, double slowing,
        double thermScat, double thermAbs, double conduction, double maxTemp, long wearCapacity, ItemStack byproduct,
        String extra) {
        if (stack == null && isTestMode()) stack = createTestPlaceholder(name);
        if (stack == null) return;
        ALL_COMPONENTS.add(
            new ComponentStatsData(
                stack,
                name,
                "Moderator",
                fastScat,
                fastAbs,
                slowing,
                thermScat,
                thermAbs,
                false,
                0.0,
                0.0,
                conduction,
                0.0,
                0.0,
                0.0,
                0.0,
                maxTemp,
                0.0,
                0.0,
                false,
                0L,
                null,
                0.0,
                0.0,
                true,
                wearCapacity,
                "Wear Capacity",
                byproduct != null,
                byproduct,
                wearCapacity,
                extra));
    }

    private static ItemStack getFirstOre(String oreName) {
        List<ItemStack> ores = OreDictionary.getOres(oreName);
        if (ores != null && !ores.isEmpty()) {
            return ores.get(0)
                .copy();
        }
        return null;
    }

    private static void addControlRod(Materials mat, String materialName, String name, double fastAbs, double thermAbs,
        double fastScat, double thermScat, double conduction, long capacity, String extra) {
        ItemStack mainStack = null;
        if (mat != null) {
            mainStack = GTOreDictUnificator.get(OrePrefixes.stickLong, mat, 1L);
            if (mainStack == null) mainStack = GTOreDictUnificator.get(OrePrefixes.stick, mat, 1L);
            if (mainStack == null) mainStack = GTOreDictUnificator.get(OrePrefixes.plate, mat, 1L);
        }
        if (mainStack == null && materialName != null) {
            mainStack = getFirstOre("stickLong" + materialName);
            if (mainStack == null) mainStack = getFirstOre("stick" + materialName);
            if (mainStack == null) mainStack = getFirstOre("plate" + materialName);
        }
        if (mainStack == null && isTestMode()) {
            mainStack = createTestPlaceholder(name);
        }
        if (mainStack == null) return;

        ComponentStatsData data = new ComponentStatsData(
            mainStack,
            name,
            "Control Rod",
            fastScat,
            fastAbs,
            0.0,
            thermScat,
            thermAbs,
            false,
            0.0,
            0.0,
            conduction,
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,
            false,
            0L,
            null,
            0.0,
            0.0,
            true,
            capacity,
            "Neutrons",
            false,
            null,
            0L,
            extra);

        if (mat != null) {
            addAlternate(data, GTOreDictUnificator.get(OrePrefixes.stick, mat, 1L));
            addAlternate(data, GTOreDictUnificator.get(OrePrefixes.plate, mat, 1L));
        }
        if (materialName != null) {
            addAlternate(data, getFirstOre("stick" + materialName));
            addAlternate(data, getFirstOre("plate" + materialName));
        }
        ALL_COMPONENTS.add(data);
    }

    private static void addRadiovoltaic(ItemStack stack, String name, int tier, long maxEU, String tierLabel,
        double satFlux, double absRate, String extra) {
        if (stack == null && isTestMode()) stack = createTestPlaceholder(name);
        if (stack == null) return;
        ALL_COMPONENTS.add(
            new ComponentStatsData(
                stack,
                name,
                "Radiovoltaic Plate",
                0.0,
                absRate,
                0.0,
                0.0,
                absRate,
                false,
                0.0,
                0.0,
                0.10,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                true,
                maxEU,
                tierLabel,
                20.0,
                5.0,
                false,
                0L,
                "Indestructible",
                false,
                null,
                0L,
                extra,
                true,
                satFlux));
    }

    private static void addInsulator(ItemStack stack, String name, double insulation, double conduction, double fastAbs,
        double thermAbs, double fastScat, double thermScat, String extra) {
        if (stack == null && isTestMode()) stack = createTestPlaceholder(name);
        if (stack == null) return;
        ALL_COMPONENTS.add(
            new ComponentStatsData(
                stack,
                name,
                "Thermal Insulator",
                fastScat,
                fastAbs,
                0.0,
                thermScat,
                thermAbs,
                false,
                0.0,
                0.0,
                conduction,
                insulation,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                false,
                0L,
                null,
                0.0,
                0.0,
                false,
                0L,
                "Indestructible",
                false,
                null,
                0L,
                extra));
    }

    private static void addCoolantCell(String ic2Name, ItemList gtItem, String name, long heatCapacity,
        double conduction) {
        ItemStack stack = null;
        if (ic2Name != null) stack = resolveStack("IC2:" + ic2Name);
        if (stack == null && gtItem != null && gtItem.hasBeenSet()) stack = gtItem.get(1L);
        if (stack == null && isTestMode()) stack = createTestPlaceholder(name);
        if (stack == null) return;

        ComponentStatsData data = new ComponentStatsData(
            stack,
            name,
            "Coolant Cell",
            0.45,
            0.02,
            0.05,
            0.45,
            0.05,
            false,
            0.0,
            0.0,
            conduction,
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,
            false,
            0L,
            null,
            0.0,
            0.0,
            true,
            heatCapacity,
            "HU Heat Capacity",
            false,
            null,
            0L,
            "Stores " + NumberFormat.getNumberInstance(Locale.US)
                .format(heatCapacity) + " HU | Ejected when full");

        if (gtItem != null && gtItem.hasBeenSet()
            && !gtItem.get(1L)
                .isItemEqual(stack)) {
            addAlternate(data, gtItem.get(1L));
        }
        ALL_COMPONENTS.add(data);
    }

    private static void addFluidCoolant(String fluidName, String name, double fastScat, double fastAbs, double slowing,
        double thermScat, double thermAbs, double conduction, double boilingPoint, double specificHeat,
        boolean hasTransmutation, ItemStack product, long reqNeutrons, String extra) {
        Fluid fluid = null;
        try {
            fluid = FluidRegistry.getFluid(fluidName);
            if (fluid == null && fluidName.startsWith("ic2")) {
                fluid = FluidRegistry.getFluid(fluidName.substring(3));
            }
            if (fluid == null && fluidName.startsWith("molten.")) {
                fluid = FluidRegistry.getFluid(fluidName.substring(7));
            }
        } catch (Throwable ignored) {}

        ItemStack display = null;
        if (fluid != null) {
            try {
                if (ItemList.Display_Fluid.hasBeenSet()) {
                    display = GTUtility.getFluidDisplayStack(new FluidStack(fluid, 1000), false);
                }
            } catch (Throwable ignored) {}
        }
        if (display == null && isTestMode()) {
            display = createTestPlaceholder(name);
        }
        if (display == null) return;

        ALL_COMPONENTS.add(
            new ComponentStatsData(
                display,
                name,
                "Fluid Coolant",
                fastScat,
                fastAbs,
                slowing,
                thermScat,
                thermAbs,
                false,
                0.0,
                0.0,
                conduction,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                boilingPoint,
                specificHeat,
                false,
                0L,
                null,
                0.0,
                0.0,
                false,
                0L,
                hasTransmutation ? "Transmutes" : "Continuous",
                hasTransmutation,
                product,
                reqNeutrons,
                extra));
    }

    private static void addHeatVent(String ic2Name, String name, double selfCooling, double compCooling,
        double conduction, long directEU, long maxEU, String extra) {
        ItemStack stack = resolveStack("IC2:" + ic2Name);
        if (stack == null && isTestMode()) stack = createTestPlaceholder(name);
        if (stack == null) return;
        ALL_COMPONENTS.add(
            new ComponentStatsData(
                stack,
                name,
                "Heat Vent",
                0.40,
                0.02,
                0.05,
                0.40,
                0.05,
                false,
                0.0,
                0.0,
                conduction,
                0.0,
                selfCooling,
                compCooling,
                0.0,
                0.0,
                0.0,
                0.0,
                true,
                maxEU,
                directEU + " EU/t",
                0.0,
                0.0,
                false,
                0L,
                "Indestructible",
                false,
                null,
                0L,
                extra));
    }

    private static void addHeatExchanger(String ic2Name, String name, double transferRate, double conduction,
        long directEU, long maxEU, String extra) {
        ItemStack stack = resolveStack("IC2:" + ic2Name);
        if (stack == null && isTestMode()) stack = createTestPlaceholder(name);
        if (stack == null) return;
        ALL_COMPONENTS.add(
            new ComponentStatsData(
                stack,
                name,
                "Heat Exchanger",
                0.45,
                0.02,
                0.05,
                0.45,
                0.05,
                false,
                0.0,
                0.0,
                conduction,
                0.0,
                0.0,
                0.0,
                transferRate,
                0.0,
                0.0,
                0.0,
                true,
                maxEU,
                directEU + " EU/t",
                0.0,
                0.0,
                false,
                0L,
                "Indestructible",
                false,
                null,
                0L,
                extra));
    }

    private static void addAlternate(ComponentStatsData data, ItemStack alt) {
        if (alt != null && !alt.isItemEqual(data.stack)) {
            data.alternateStacks.add(alt);
        }
    }

    private static ItemStack resolveStack(String id) {
        if (id.startsWith("IC2:")) {
            String name = id.substring(4);
            int meta = 0;
            if (name.contains(":")) {
                String[] p = name.split(":");
                name = p[0];
                try {
                    meta = Integer.parseInt(p[1]);
                } catch (NumberFormatException ignored) {}
            }
            ItemStack is = GTModHandler.getIC2Item(name, 1L, meta);
            if (is != null) return is;
        }
        if (id.contains(":")) {
            String[] p = id.split(":");
            if (p.length >= 2) {
                int meta = p.length >= 3 ? Integer.parseInt(p[2]) : 0;
                net.minecraft.item.Item it = cpw.mods.fml.common.registry.GameRegistry.findItem(p[0], p[1]);
                if (it != null) return new ItemStack(it, 1, meta);
            }
        }
        return null;
    }

    private static ItemStack getItemListStack(ItemList item) {
        return (item != null && item.hasBeenSet()) ? item.get(1L) : null;
    }

    private static ItemStack getFluidDisplay(String fluidName, int amount) {
        try {
            Fluid fluid = FluidRegistry.getFluid(fluidName);
            if (fluid == null && !fluidName.startsWith("fluid.")) {
                fluid = FluidRegistry.getFluid("fluid." + fluidName);
            }
            if (fluid != null && ItemList.Display_Fluid.hasBeenSet()) {
                return GTUtility.getFluidDisplayStack(new FluidStack(fluid, amount), false);
            }
        } catch (Throwable ignored) {}
        if (isTestMode()) {
            return createTestPlaceholder(fluidName);
        }
        return null;
    }

    // =========================================================================
    // TemplateRecipeHandler Implementation
    // =========================================================================

    public NEINeutronInteractionHandler() {
        initData();
    }

    @Override
    public String getRecipeName() {
        return StatCollector.translateToLocal("gt.nei.neutron_interaction.name");
    }

    @Override
    public String getGuiTexture() {
        return "gregtech:textures/gui/nei/neutron_interaction_atlas.png";
    }

    @Override
    public String getOverlayIdentifier() {
        return OVERLAY_ID;
    }

    @Override
    public int recipiesPerPage() {
        return 1;
    }

    @Override
    public void loadTransferRects() {
        this.transferRects.add(new RecipeTransferRect(new Rectangle(6, 2, 18, 18), getOverlayIdentifier()));
    }

    @Override
    public void loadCraftingRecipes(String outputId, Object... results) {
        initData();
        if (outputId != null && outputId.equals(getOverlayIdentifier())) {
            for (ComponentStatsData data : ALL_COMPONENTS) {
                this.arecipes.add(new CachedNeutronInteractionRecipe(data));
            }
        } else {
            super.loadCraftingRecipes(outputId, results);
        }
    }

    @Override
    public void loadCraftingRecipes(ItemStack result) {
        initData();
        if (result == null) return;
        for (ComponentStatsData data : ALL_COMPONENTS) {
            if (matches(result, data)) {
                this.arecipes.add(new CachedNeutronInteractionRecipe(data));
            } else if (data.transmutationOutput != null && matchesStack(result, data.transmutationOutput)) {
                this.arecipes.add(new CachedNeutronInteractionRecipe(data));
            }
        }
    }

    @Override
    public void loadUsageRecipes(ItemStack ingredient) {
        initData();
        if (ingredient == null) return;

        // Clicking on the nuclear reactor catalyst shows all components
        ItemStack ctrl = getControllerStack();
        if (ctrl != null && ctrl.isItemEqual(ingredient)) {
            for (ComponentStatsData data : ALL_COMPONENTS) {
                this.arecipes.add(new CachedNeutronInteractionRecipe(data));
            }
            return;
        }

        for (ComponentStatsData data : ALL_COMPONENTS) {
            if (matches(ingredient, data)) {
                this.arecipes.add(new CachedNeutronInteractionRecipe(data));
            }
        }
    }

    private boolean matches(ItemStack target, ComponentStatsData data) {
        if (target == null || data == null) return false;
        if (matchesStack(target, data.stack)) return true;
        for (ItemStack alt : data.alternateStacks) {
            if (matchesStack(target, alt)) return true;
        }
        return false;
    }

    private boolean matchesStack(ItemStack target, ItemStack component) {
        if (target == null || component == null) return false;
        if (GTUtility.areStacksEqual(target, component, true)) return true;
        if (target.getItem() == component.getItem()) {
            return target.getItemDamage() == OreDictionary.WILDCARD_VALUE
                || component.getItemDamage() == OreDictionary.WILDCARD_VALUE
                || target.getItemDamage() == component.getItemDamage();
        }
        return false;
    }

    @Override
    public int getRecipeHeight(int recipe) {
        if (recipe >= 0 && recipe < arecipes.size()) {
            CachedRecipe cr = arecipes.get(recipe);
            if (cr instanceof CachedNeutronInteractionRecipe nr && nr.data.isRadiovoltaic) {
                return 314;
            }
        }
        return 304;
    }

    // =========================================================================
    // GUI Rendering
    // =========================================================================

    @Override
    public void drawBackground(int recipeIndex) {
        if (recipeIndex < 0 || recipeIndex >= arecipes.size()) return;
        CachedRecipe cached = this.arecipes.get(recipeIndex);

        if (cached instanceof CachedNeutronInteractionRecipe recipe) {
            // Header catalyst slot box at (6, 2)
            drawSlotBox(6, 2);

            // Card 1: Fast neutrons panel at y=33
            drawPanel(6, 33, 154, 64);
            drawSlotBox(74, 56);
            drawFastNeutronDiagram(recipe.data, 33);

            // Card 2: Thermal neutrons panel at y=101
            drawPanel(6, 101, 154, 64);
            drawSlotBox(66, 129);
            GuiDraw.changeTexture(TEXTURE_ATLAS);
            GuiDraw.drawTexturedModalRect(14, 111, 0, 60, 88, 54);

            // Card 3: Heat panel at y=169
            drawPanel(6, 169, 154, 64);
            drawSlotBox(57, 193);
            GuiDraw.changeTexture(TEXTURE_ATLAS);
            GuiDraw.drawTexturedModalRect(30, 187, 0, 109, 92, 31);

            // Card 4: Durability & byproducts panel at y=237 OR Radiovoltaic curve panel
            if (recipe.data.isRadiovoltaic) {
                NEIFuelStatsHandler.drawThemePanel(RV_PANEL_X, RV_PANEL_Y, RV_PANEL_W, RV_PANEL_H);
                NEIFuelStatsHandler.drawSunkenBox(RV_SCREEN_X, RV_SCREEN_Y, RV_SCREEN_W, RV_SCREEN_H, 0xFF141619);
            } else {
                drawPanel(6, 237, 154, 64);
                int durSlotX = recipe.data.transmutationOutput != null ? 30 : 66;
                drawSlotBox(durSlotX, 265);

                if (recipe.data.transmutationOutput != null) {
                    drawSlotBox(100, 265);
                    GuiDraw.changeTexture(LONG_ARROW);
                    drawCustomTexturedModalRect(58, 264, 0, 0, 40, 20, 40, 40);
                    long cycle = System.currentTimeMillis() % 3000L;
                    int fillW = (int) (40.0 * (cycle / 3000.0));
                    if (fillW > 0) {
                        drawCustomTexturedModalRect(58, 264, 0, 20, fillW, 20, 40, 40);
                    }
                }
            }
        }
    }

    @Override
    public void drawExtras(int recipeIndex) {
        if (recipeIndex < 0 || recipeIndex >= arecipes.size()) return;
        CachedRecipe cached = this.arecipes.get(recipeIndex);

        if (cached instanceof CachedNeutronInteractionRecipe recipe) {
            ComponentStatsData d = recipe.data;

            // Header summary (at top: category & description next to catalyst)
            GuiDraw.drawString(EnumChatFormatting.DARK_BLUE + "[" + d.category + "]", 28, 3, 0x103080, false);
            if (d.extraInfo != null) {
                renderWrappedExtraInfo(d.extraInfo, 28, 13, 132);
            }

            // --- Card 1: Fast Neutron Panel (baseY = 33) ---
            GuiDraw
                .drawStringC(StatCollector.translateToLocal("gt.nei.neutron_interaction.fast"), 83, 36, 0x404040, false);

            // Reflected neutrons percentage (between the two blue arrows)
            String fastReflStr = formatPercent(d.isReflector ? d.reflectionFast : 0.0);
            GuiDraw
                .drawStringC(fastReflStr, 38, 61, (d.isReflector && d.reflectionFast > 0) ? 0x9A6B00 : 0x404040, false);

            // Absorbed neutrons percentage (below red arrow)
            String absStr = formatPercent(d.fastAbsorption);
            GuiDraw.drawStringC(absStr, 83, 86, d.fastAbsorption > 0 ? 0xCC0000 : 0x404040, false);

            // Scattered fast neutrons percentage (above blue arrow)
            double pFast = d.fastScattering * (1.0 - d.slowingProbability);
            String fastScatStr = formatPercent(pFast);
            GuiDraw.drawStringC(fastScatStr, 136, 37, pFast > 0 ? 0x0033CC : 0x404040, false);

            // Scattered moderated neutrons percentage (below purple arrow)
            double pMod = d.fastScattering * d.slowingProbability;
            String modScatStr = formatPercent(pMod);
            GuiDraw.drawStringC(modScatStr, 136, 86, pMod > 0 ? 0x7F00FF : 0x404040, false);

            // --- Card 2: Thermal Neutron Panel (baseY = 101) ---
            GuiDraw.drawStringC(
                StatCollector.translateToLocal("gt.nei.neutron_interaction.thermal"),
                83,
                104,
                0x404040,
                false);

            if (d.isReflector) {
                String reflStr = formatPercent(d.reflectionThermal);
                GuiDraw.drawStringC(reflStr, 47, 115, 0x9A6B00, false);
                GuiDraw.drawStringC("REFLECT", 47, 124, 0x9A6B00, false);
            } else {
                String thermScatStr = formatPercent(d.thermalScattering);
                GuiDraw.drawStringC(thermScatStr, 47, 115, 0x404040, false);
            }

            String thermAbsStr = formatPercent(d.thermalAbsorption);
            GuiDraw.drawStringC(thermAbsStr, 97, 153, 0x00A000, false);

            // --- Card 3: Heat Panel (baseY = 169) ---
            GuiDraw.drawStringC(
                StatCollector.translateToLocal("gt.nei.neutron_interaction.capture"),
                83,
                172,
                0x404040,
                false);

            // Direct EU production (left of diagram)
            if (d.producesDirectEU) {
                if (d.directEUPenNeutronFast > 0) {
                    GuiDraw.drawString(
                        String.format(Locale.US, "%.0f EU/n", d.directEUPenNeutronFast),
                        32,
                        219,
                        0x00A000,
                        false);
                } else if (d.selfCoolingRate > 0) {
                    GuiDraw
                        .drawString(String.format(Locale.US, "%.0f Hu/t", d.selfCoolingRate), 32, 219, 0x0055AA, false);
                } else if (d.heatTransferRate > 0) {
                    GuiDraw
                        .drawString(String.format(Locale.US, "%.0f Hu/t", d.heatTransferRate), 32, 219, 0x0055AA, false);
                } else {
                    GuiDraw.drawString("Active", 34, 219, 0x00A000, false);
                }
            } else {
                GuiDraw.drawString("0 EU", 36, 219, 0x606060, false);
            }

            // Heat transfer & thermal metrics (right of diagram)
            if (d.thermalInsulationFactor > 0.0) {
                GuiDraw.drawString("Barrier: " + formatPercent(d.thermalInsulationFactor), 110, 185, 0x902020, false);
                GuiDraw.drawString("k = " + formatNumber(d.heatTransferCoeff), 110, 201, 0x404040, false);
            } else if (d.boilingPoint > 0.0) {
                GuiDraw.drawString("Boils: " + formatNumber(d.boilingPoint) + " °C", 110, 185, 0x205090, false);
                GuiDraw.drawString("Cp: " + formatNumber(d.specificHeat), 110, 201, 0x404040, false);
            } else if (d.selfCoolingRate > 0.0 || d.componentCoolingRate > 0.0) {
                double cool = d.selfCoolingRate > 0 ? d.selfCoolingRate : d.componentCoolingRate;
                GuiDraw.drawString("Cool: " + formatNumber(cool) + " Hu/t", 110, 185, 0x0055AA, false);
                GuiDraw.drawString("k = " + formatNumber(d.heatTransferCoeff), 110, 201, 0x404040, false);
            } else if (d.heatTransferRate > 0.0) {
                GuiDraw.drawString("Xfer: " + formatNumber(d.heatTransferRate) + " Hu/t", 110, 185, 0x0055AA, false);
                GuiDraw.drawString("k = " + formatNumber(d.heatTransferCoeff), 110, 201, 0x404040, false);
            } else if (d.maxTemperature > 0.0) {
                GuiDraw.drawString("k = " + formatNumber(d.heatTransferCoeff), 110, 185, 0x404040, false);
                GuiDraw.drawString("Max: " + formatNumber(d.maxTemperature) + " °C", 110, 201, 0xAA5500, false);
            } else {
                GuiDraw.drawString("k = " + formatNumber(d.heatTransferCoeff), 110, 185, 0x404040, false);
                GuiDraw.drawString("Passive", 110, 201, 0x606060, false);
            }

            // Summary line in heat panel (y = 221)
            if (d.maxDirectEU > 0) {
                String euText = String.format(
                    Locale.US,
                    "Max: %d EU/t (%s)",
                    d.maxDirectEU,
                    d.directEUTier != null ? d.directEUTier : "");
                GuiDraw.drawStringC(euText, 83, 221, 0x00A000, false);
            } else if (d.selfCoolingRate > 0) {
                GuiDraw.drawStringC("Self-cooling rate: " + formatNumber(d.selfCoolingRate) + " Hu/t", 83, 221, 0x0055AA, false);
            } else if (d.componentCoolingRate > 0) {
                GuiDraw.drawStringC("Adjacent cooling: " + formatNumber(d.componentCoolingRate) + " Hu/t", 83, 221, 0x0055AA, false);
            } else if (d.heatTransferRate > 0) {
                GuiDraw.drawStringC("Adjacent component transfer: " + formatNumber(d.heatTransferRate) + " Hu/t", 83, 221, 0x0055AA, false);
            } else if (d.thermalInsulationFactor > 0) {
                GuiDraw.drawStringC("Blocks " + formatPercent(d.thermalInsulationFactor) + " of heat conduction", 83, 221, 0x902020, false);
            } else {
                GuiDraw.drawStringC("Thermal conduction coefficient: k = " + formatNumber(d.heatTransferCoeff), 83, 221, 0x505050, false);
            }

            // --- Card 4: Durability & Byproducts Panel OR Radiovoltaic Power & Heat Curve (baseY = 237) ---
            if (d.isRadiovoltaic) {
                drawRadiovoltaicCurve(recipe, recipeIndex);
            } else {
                GuiDraw.drawStringC(
                    StatCollector.translateToLocal("gt.nei.neutron_interaction.absorption"),
                    83,
                    240,
                    0x404040,
                    false);

                if (d.transmutationOutput != null) {
                    String reqStr = formatNeutrons(d.transmutationNeutrons);
                    String transStr = reqStr + " Neutrons -> " + d.transmutationOutput.getDisplayName();
                    GuiDraw.drawStringC(transStr, 83, 289, 0x205090, false);
                } else if (d.hasDurability) {
                    String durStr = d.durabilityLabel + ": " + formatNeutrons(d.durability);
                    GuiDraw.drawStringC(durStr, 83, 289, 0x404040, false);
                } else {
                    GuiDraw.drawStringC(
                        d.durabilityLabel != null ? d.durabilityLabel : "Indestructible",
                        83,
                        289,
                        0x008000,
                        false);
                }
            }
        }
    }

    public static boolean isMouseOverRVPlot(int x, int y) {
        return x >= RV_PLOT_X && x <= (RV_PLOT_X + RV_PLOT_W) && y >= RV_PLOT_Y && y <= (RV_PLOT_Y + RV_PLOT_H);
    }

    private void drawRadiovoltaicCurve(CachedNeutronInteractionRecipe recipe, int recipeIndex) {
        ComponentStatsData d = recipe.data;
        long maxEU = d.maxDirectEU > 0 ? d.maxDirectEU : (d.saturationFlux > 100.0 ? 4096 : 1024);
        double satFlux = d.saturationFlux > 0 ? d.saturationFlux : (maxEU >= 4096 ? 150.0 : 50.0);
        double maxFlux = (maxEU >= 4096) ? 300.0 : 100.0;
        double maxHeat = (maxEU >= 4096) ? 3000.0 : 1500.0;

        // Title at (83, 239)
        GuiDraw.drawStringC(
            StatCollector.translateToLocal("gt.nei.neutron_interaction.radiovoltaic_curve"),
            83,
            239,
            0x404040,
            false);

        // 1. Grid Lines
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

        GL11.glColor4f(1.0f, 1.0f, 1.0f, 0.08f);
        GL11.glLineWidth(1.0f);
        GL11.glBegin(GL11.GL_LINES);
        for (int p = 25; p < 100; p += 25) {
            int gy = RV_PLOT_Y + RV_PLOT_H - (int) Math.round((p / 100.0) * RV_PLOT_H);
            GL11.glVertex2f(RV_PLOT_X, gy);
            GL11.glVertex2f(RV_PLOT_X + RV_PLOT_W, gy);
        }
        for (int p = 25; p < 100; p += 25) {
            int gx = RV_PLOT_X + (int) Math.round((p / 100.0) * RV_PLOT_W);
            GL11.glVertex2f(gx, RV_PLOT_Y);
            GL11.glVertex2f(gx, RV_PLOT_Y + RV_PLOT_H);
        }
        GL11.glEnd();
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glPopMatrix();

        // 2. Axis text & labels (using 0.5x scale font renderer)
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        GL11.glPushMatrix();
        GL11.glScalef(0.5f, 0.5f, 1.0f);

        // Left Axis: Power EU/t (Cyan: 0xFF38BDF8)
        font.drawString("EU", (RV_PLOT_X - 16) * 2, (RV_PLOT_Y - 2) * 2, 0xFF38BDF8);
        for (int p = 0; p <= 100; p += 50) {
            int gy = RV_PLOT_Y + RV_PLOT_H - (int) Math.round((p / 100.0) * RV_PLOT_H);
            long pVal = Math.round((p / 100.0) * maxEU);
            String pStr = (pVal >= 1000) ? String.format(Locale.US, "%.1fk", pVal / 1000.0) : String.valueOf(pVal);
            int sw = font.getStringWidth(pStr);
            font.drawString(pStr, (RV_PLOT_X - 2) * 2 - sw, (gy - 2) * 2, 0xFF38BDF8);
        }

        // Right Axis: Excess Heat Hu/t (Coral Red: 0xFFF87171)
        font.drawString("Hu", (RV_PLOT_X + RV_PLOT_W + 3) * 2, (RV_PLOT_Y - 2) * 2, 0xFFF87171);
        for (int p = 0; p <= 100; p += 50) {
            int gy = RV_PLOT_Y + RV_PLOT_H - (int) Math.round((p / 100.0) * RV_PLOT_H);
            double hVal = (p / 100.0) * maxHeat;
            String hStr = (hVal >= 1000.0) ? String.format(Locale.US, "%.1fk", hVal / 1000.0) : String.valueOf((int) hVal);
            font.drawString(hStr, (RV_PLOT_X + RV_PLOT_W + 3) * 2, (gy - 2) * 2, 0xFFF87171);
        }

        // Bottom Axis: Flux labels (Silver: 0xFF9CA3AF)
        font.drawString("0", (RV_PLOT_X - 1) * 2, (RV_PLOT_Y + RV_PLOT_H + 2) * 2, 0xFF9CA3AF);
        int midFlux = (int) (maxFlux / 2);
        String midStr = String.valueOf(midFlux);
        int midSw = font.getStringWidth(midStr);
        font.drawString(midStr, (RV_PLOT_X + RV_PLOT_W / 2) * 2 - midSw / 2, (RV_PLOT_Y + RV_PLOT_H + 2) * 2, 0xFF9CA3AF);
        String maxStr = String.valueOf((int) maxFlux);
        int maxSw = font.getStringWidth(maxStr);
        font.drawString(maxStr, (RV_PLOT_X + RV_PLOT_W) * 2 - maxSw / 2, (RV_PLOT_Y + RV_PLOT_H + 2) * 2, 0xFF9CA3AF);
        font.drawString("n/t", (RV_PLOT_X + RV_PLOT_W + 10) * 2, (RV_PLOT_Y + RV_PLOT_H + 2) * 2, 0xFF9CA3AF);

        GL11.glPopMatrix();

        // 3. Draw Dynamic Anti-aliased Vector Curves
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(2.0f);

        // Power curve (Cyan: #38BDF8)
        GL11.glColor4f(0.22f, 0.74f, 0.97f, 1.0f);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int i = 0; i <= RV_PLOT_W; i++) {
            double flux = (i / (double) RV_PLOT_W) * maxFlux;
            double p = d.calculatePower(flux);
            float y = (float) (RV_PLOT_Y + RV_PLOT_H - Math.max(0.0, Math.min(1.0, p / maxEU)) * RV_PLOT_H);
            GL11.glVertex2f(RV_PLOT_X + i, y);
        }
        GL11.glEnd();

        // Heat curve (Coral Red: #F87171)
        GL11.glColor4f(0.97f, 0.44f, 0.44f, 1.0f);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int i = 0; i <= RV_PLOT_W; i++) {
            double flux = (i / (double) RV_PLOT_W) * maxFlux;
            double h = d.calculateHeat(flux);
            float y = (float) (RV_PLOT_Y + RV_PLOT_H - Math.max(0.0, Math.min(1.0, h / maxHeat)) * RV_PLOT_H);
            GL11.glVertex2f(RV_PLOT_X + i, y);
        }
        GL11.glEnd();

        // 4. Interactive Crosshair & Guide Lines on Hover
        Point mouseInRecipe = null;
        if (Minecraft.getMinecraft().currentScreen instanceof GuiRecipe<?> guiRecipe) {
            mouseInRecipe = getMouseInRecipe(guiRecipe, recipeIndex);
        }

        if (mouseInRecipe != null && isMouseOverRVPlot(mouseInRecipe.x, mouseInRecipe.y)) {
            int mx = mouseInRecipe.x;
            double fluxAtMouse = ((mx - RV_PLOT_X) / (double) RV_PLOT_W) * maxFlux;
            double pAtMouse = d.calculatePower(fluxAtMouse);
            double hAtMouse = d.calculateHeat(fluxAtMouse);
            float yp = (float) (RV_PLOT_Y + RV_PLOT_H - Math.max(0.0, Math.min(1.0, pAtMouse / maxEU)) * RV_PLOT_H);
            float yh = (float) (RV_PLOT_Y + RV_PLOT_H - Math.max(0.0, Math.min(1.0, hAtMouse / maxHeat)) * RV_PLOT_H);

            // Vertical cursor line (neutral white)
            GL11.glLineWidth(1.0f);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 0.6f);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(mx, RV_PLOT_Y);
            GL11.glVertex2f(mx, RV_PLOT_Y + RV_PLOT_H);
            GL11.glEnd();

            // Horizontal projection to left axis for Power (Cyan)
            GL11.glColor4f(0.22f, 0.74f, 0.97f, 0.5f);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(RV_PLOT_X, yp);
            GL11.glVertex2f(mx, yp);
            GL11.glEnd();

            // Horizontal projection to right axis for Heat (Coral Red)
            GL11.glColor4f(0.97f, 0.44f, 0.44f, 0.5f);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(mx, yh);
            GL11.glVertex2f(RV_PLOT_X + RV_PLOT_W, yh);
            GL11.glEnd();

            // Snap indicator dots (diamonds)
            NEIFuelStatsHandler.drawIndicatorDot(mx, yp, 0.22f, 0.74f, 0.97f);
            NEIFuelStatsHandler.drawIndicatorDot(mx, yh, 0.97f, 0.44f, 0.44f);
        }

        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(1.0f);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glPopMatrix();
    }

    private static String formatNeutrons(long n) {
        if (n >= 1_000_000_000L) {
            return String.format(Locale.US, "%.1fB", n / 1_000_000_000.0);
        } else if (n >= 1_000_000L) {
            return String.format(Locale.US, "%.1fM", n / 1_000_000.0);
        } else if (n >= 1_000L) {
            return NumberFormat.getNumberInstance(Locale.US)
                .format(n);
        }
        return String.valueOf(n);
    }

    public static List<String> wrapExtraInfo(String text, int wrapWidth, FontRenderer fr) {
        if (text == null || text.isEmpty()) return Collections.emptyList();
        if (fr != null) {
            try {
                @SuppressWarnings("unchecked")
                List<String> lines = (List<String>) fr.listFormattedStringToWidth(text, wrapWidth);
                if (lines != null && !lines.isEmpty()) {
                    return lines;
                }
            } catch (Throwable ignored) {}
        }
        // Fallback character-based word wrapping (~5px per char average in MC)
        int maxChars = Math.max(15, wrapWidth / 5);
        if (text.length() <= maxChars) {
            return Collections.singletonList(text);
        }
        List<String> lines = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (current.length() == 0) {
                current.append(word);
            } else if (current.length() + 1 + word.length() <= maxChars) {
                current.append(" ")
                    .append(word);
            } else {
                lines.add(current.toString());
                current = new StringBuilder(word);
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    private void renderWrappedExtraInfo(String text, int x, int startY, int wrapWidth) {
        List<String> lines = wrapExtraInfo(text, wrapWidth, GuiDraw.fontRenderer);
        if (lines.isEmpty()) return;
        int lineSpacing = lines.size() > 2 ? 8 : 9;
        int y = lines.size() > 2 ? startY - 1 : startY;
        for (String line : lines) {
            GuiDraw.drawString(EnumChatFormatting.GRAY + line, x, y, 0x505050, false);
            y += lineSpacing;
        }
    }

    // =========================================================================
    // Tooltips
    // =========================================================================

    private static Field fieldGuiLeft;
    private static Field fieldGuiTop;

    static {
        try {
            fieldGuiLeft = GuiContainer.class.getDeclaredField("guiLeft");
            fieldGuiLeft.setAccessible(true);
        } catch (Throwable t) {
            try {
                fieldGuiLeft = GuiContainer.class.getDeclaredField("field_147003_i");
                fieldGuiLeft.setAccessible(true);
            } catch (Throwable ignored) {}
        }
        try {
            fieldGuiTop = GuiContainer.class.getDeclaredField("guiTop");
            fieldGuiTop.setAccessible(true);
        } catch (Throwable t) {
            try {
                fieldGuiTop = GuiContainer.class.getDeclaredField("field_147009_r");
                fieldGuiTop.setAccessible(true);
            } catch (Throwable ignored) {}
        }
    }

    private static int getGuiLeft(GuiRecipe<?> gui) {
        if (fieldGuiLeft != null) {
            try {
                return fieldGuiLeft.getInt(gui);
            } catch (Throwable ignored) {}
        }
        return (gui.width - 176) / 2;
    }

    private static int getGuiTop(GuiRecipe<?> gui) {
        if (fieldGuiTop != null) {
            try {
                return fieldGuiTop.getInt(gui);
            } catch (Throwable ignored) {}
        }
        return (gui.height - 166) / 2;
    }

    public static Point getMouseInRecipe(GuiRecipe<?> gui, int recipeIndex) {
        if (gui == null) return null;
        Point mousepos = GuiDraw.getMousePosition();
        Point offset = gui.getRecipePosition(recipeIndex);
        if (offset == null) return null;
        return new Point(mousepos.x - getGuiLeft(gui) - offset.x, mousepos.y - getGuiTop(gui) - offset.y);
    }

    public List<String> getTooltipsAt(CachedRecipe rec, int relX, int relY, List<String> currenttip) {
        if (currenttip == null) currenttip = new ArrayList<>();
        if (rec instanceof CachedNeutronInteractionRecipe r1) {
            ComponentStatsData d = r1.data;

            // Card 1: Fast Neutron Panel (Y in [33, 97])
            if (relY >= 33 && relY <= 97) {
                // 1. Fast reflection & incoming neutrons (covers the two blue arrows and % text: X in [18, 73], Y in [45, 85])
                if (relX >= 18 && relX <= 73 && relY >= 45 && relY <= 85) {
                    if (d.isReflector) {
                        currenttip.add(
                            EnumChatFormatting.GOLD
                                + StatCollector.translateToLocal("gt.nei.neutron_interaction.reflection"));
                        currenttip.add(
                            EnumChatFormatting.GRAY
                                + StatCollector.translateToLocal("gt.nei.neutron_interaction.reflection_fast.desc"));
                    } else {
                        currenttip.add(
                            EnumChatFormatting.WHITE
                                + StatCollector.translateToLocal("gt.nei.neutron_interaction.reflection"));
                        currenttip.add(
                            EnumChatFormatting.GRAY
                                + StatCollector.translateToLocal("gt.nei.neutron_interaction.reflection_fast.desc"));
                    }
                    // 2. Fast absorption (center bottom: arrow and % text at (83, 86))
                } else if (relX >= 68 && relX <= 98 && relY >= 75 && relY <= 96) {
                    currenttip.add(
                        EnumChatFormatting.RED
                            + StatCollector.translateToLocal("gt.nei.neutron_interaction.absorption_title"));
                    currenttip.add(
                        EnumChatFormatting.GRAY
                            + StatCollector.translateToLocal("gt.nei.neutron_interaction.absorption_fast.desc"));
                    // 3. Scattering arrows (green arrows between center slot and pie chart: X in [94, 118], Y in [49, 81])
                } else if (relX >= 94 && relX <= 118 && relY >= 49 && relY <= 81) {
                    currenttip.add(
                        EnumChatFormatting.GREEN
                            + StatCollector.translateToLocal("gt.nei.neutron_interaction.scattering"));
                    currenttip.add(
                        EnumChatFormatting.GRAY
                            + StatCollector.translateToLocal("gt.nei.neutron_interaction.scattering_fast.desc"));
                    // 4. Upper: Scattered fast arrow & percentage (X in [120, 155], Y in [35, 54])
                } else if (relX >= 120 && relX <= 155 && relY >= 35 && relY <= 54) {
                    currenttip.add(
                        EnumChatFormatting.BLUE
                            + StatCollector.translateToLocal("gt.nei.neutron_interaction.fast_fraction"));
                    currenttip.add(
                        EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(
                            "gt.nei.neutron_interaction.fast_fraction.desc",
                            formatPercent(1.0 - d.slowingProbability)));
                    // 5. Lower: Scattered moderated arrow & percentage (X in [120, 155], Y in [76, 96])
                } else if (relX >= 120 && relX <= 155 && relY >= 76 && relY <= 96) {
                    currenttip.add(
                        EnumChatFormatting.LIGHT_PURPLE
                            + StatCollector.translateToLocal("gt.nei.neutron_interaction.thermal_fraction"));
                    currenttip.add(
                        EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(
                            "gt.nei.neutron_interaction.thermal_fraction.desc",
                            formatPercent(d.slowingProbability)));
                    // 6. Center: Pie chart (X in [124, 148], Y in [55, 75])
                } else if (relX >= 124 && relX <= 148 && relY >= 55 && relY <= 75) {
                    currenttip.add(
                        EnumChatFormatting.BLUE
                            + StatCollector.translateToLocal("gt.nei.neutron_interaction.fast_fraction"));
                    currenttip.add(
                        EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(
                            "gt.nei.neutron_interaction.fast_fraction.desc",
                            formatPercent(1.0 - d.slowingProbability)));
                    currenttip.add(
                        EnumChatFormatting.LIGHT_PURPLE
                            + StatCollector.translateToLocal("gt.nei.neutron_interaction.thermal_fraction"));
                    currenttip.add(
                        EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(
                            "gt.nei.neutron_interaction.thermal_fraction.desc",
                            formatPercent(d.slowingProbability)));
                }

            // Card 2: Thermal Neutron Panel (Y in [101, 165])
            } else if (relY >= 101 && relY <= 165) {
                // Thermal scattering / reflection: covers text (47, 115) and full curved arrow
                if (relX >= 25 && relX <= 66 && relY >= 107 && relY <= 135) {
                    if (d.isReflector) {
                        currenttip.add(
                            EnumChatFormatting.GOLD
                                + StatCollector.translateToLocal("gt.nei.neutron_interaction.reflection"));
                        currenttip.add(
                            EnumChatFormatting.GRAY
                                + StatCollector.translateToLocal("gt.nei.neutron_interaction.reflection_thermal.desc"));
                    } else {
                        currenttip.add(
                            EnumChatFormatting.WHITE
                                + StatCollector.translateToLocal("gt.nei.neutron_interaction.scattering"));
                        currenttip.add(
                            EnumChatFormatting.GRAY
                                + StatCollector.translateToLocal("gt.nei.neutron_interaction.scattering_thermal.desc"));
                    }
                    // Thermal absorption: covers text (97, 153), downward arrow, and curved arrow
                } else if ((relX >= 65 && relX <= 118 && relY >= 141 && relY <= 163)
                    || (relX >= 72 && relX <= 104 && relY >= 117 && relY <= 139)) {
                    currenttip.add(
                        EnumChatFormatting.GREEN
                            + StatCollector.translateToLocal("gt.nei.neutron_interaction.absorption_title"));
                    currenttip.add(
                        EnumChatFormatting.GRAY
                            + StatCollector.translateToLocal("gt.nei.neutron_interaction.absorption_thermal.desc"));
                }

            // Card 3: Heat Panel (Y in [169, 233])
            } else if (relY >= 169 && relY <= 233) {
                // Direct EU / Hu production (left of diagram)
                if (relX >= 20 && relX <= 55 && relY >= 211 && relY <= 229) {
                    if (d.producesDirectEU) {
                        currenttip.add(
                            EnumChatFormatting.GREEN
                                + StatCollector.translateToLocal("gt.nei.neutron_interaction.direct_eu"));
                        currenttip.add(
                            EnumChatFormatting.GRAY + StatCollector
                                .translateToLocalFormatted("gt.nei.neutron_interaction.direct_eu.desc", d.maxDirectEU));
                    } else {
                        currenttip.add(EnumChatFormatting.GRAY + "No direct electricity produced");
                    }
                // Conduction & thermal properties (right of diagram)
                } else if (relX >= 95 && relX <= 155 && relY >= 179 && relY <= 207) {
                    currenttip.add(
                        EnumChatFormatting.GOLD + StatCollector.translateToLocal("gt.nei.neutron_interaction.conduction"));
                    currenttip.add(
                        EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(
                            "gt.nei.neutron_interaction.conduction.desc",
                            formatNumber(d.heatTransferCoeff)));
                // Summary line (bottom of heat panel)
                } else if (relX >= 25 && relX <= 140 && relY >= 215 && relY <= 231) {
                    currenttip.add(
                        EnumChatFormatting.AQUA
                            + StatCollector.translateToLocal("gt.nei.neutron_interaction.multiplication"));
                    currenttip.add(
                        EnumChatFormatting.GRAY
                            + StatCollector.translateToLocal("gt.nei.neutron_interaction.multiplication.desc"));
                }

            // Card 4: Durability & Byproducts Panel OR Radiovoltaic Power & Heat Curve (Y in [237, 309])
            } else if (relY >= 237 && relY <= 309) {
                if (d.isRadiovoltaic) {
                    if (isMouseOverRVPlot(relX, relY)) {
                        long maxEU = d.maxDirectEU > 0 ? d.maxDirectEU : (d.saturationFlux > 100.0 ? 4096 : 1024);
                        double satFlux = d.saturationFlux > 0 ? d.saturationFlux : (maxEU >= 4096 ? 150.0 : 50.0);
                        double maxFlux = (maxEU >= 4096) ? 300.0 : 100.0;
                        double flux = ((relX - RV_PLOT_X) / (double) RV_PLOT_W) * maxFlux;
                        double power = d.calculatePower(flux);
                        double heat = d.calculateHeat(flux);

                        currenttip.add(
                            EnumChatFormatting.WHITE + ""
                                + EnumChatFormatting.BOLD
                                + String.format(Locale.US, "%.1f n/t flux", flux));

                        String satNote = (flux >= satFlux * 1.5)
                            ? EnumChatFormatting.GOLD + " (Saturated)"
                            : (flux >= satFlux ? EnumChatFormatting.YELLOW + " (Knee)" : "");

                        currenttip.add(
                            EnumChatFormatting.AQUA + "Power output: "
                                + formatNumber(power)
                                + " EU/t ("
                                + formatPercent(power / maxEU)
                                + ")"
                                + satNote);

                        String heatNote = (heat <= 0.0) ? EnumChatFormatting.GRAY + " (None)" : "";
                        currenttip.add(
                            EnumChatFormatting.RED + "Excess heating: "
                                + formatNumber(heat)
                                + " Hu/t"
                                + heatNote);
                    } else if (relX >= 6 && relX <= 160 && relY >= 237 && relY <= 248) {
                        currenttip.add(
                            EnumChatFormatting.AQUA
                                + StatCollector.translateToLocal("gt.nei.neutron_interaction.radiovoltaic_curve"));
                        currenttip.add(
                            EnumChatFormatting.GRAY
                                + StatCollector.translateToLocal("gt.nei.neutron_interaction.radiovoltaic_curve.desc"));
                    }
                } else {
                    if (relX >= 25 && relX <= 140 && relY >= 257 && relY <= 299) {
                        currenttip.add(
                            EnumChatFormatting.WHITE
                                + StatCollector.translateToLocal("gt.nei.neutron_interaction.absorption_capacity"));
                        currenttip.add(
                            EnumChatFormatting.GRAY
                                + StatCollector.translateToLocal("gt.nei.neutron_interaction.absorption_capacity.desc"));
                    }
                }
            }
        }
        return currenttip;
    }

    @Override
    public List<String> handleTooltip(GuiRecipe<?> gui, List<String> currenttip, int recipeIndex) {
        currenttip = super.handleTooltip(gui, currenttip, recipeIndex);
        if (recipeIndex < 0 || recipeIndex >= arecipes.size()) return currenttip;

        Point m = getMouseInRecipe(gui, recipeIndex);
        int relX;
        int relY;
        if (m != null) {
            relX = m.x;
            relY = m.y;
        } else {
            Point mousepos = GuiDraw.getMousePosition();
            Dimension displaySize = GuiDraw.displaySize();
            int ySize = Math.min(Math.max(displaySize.height - 68, 166), 370);
            int guiLeft = (displaySize.width - 176) / 2;
            int guiTop = (displaySize.height - ySize) / 2 + 10;
            Point offset = gui.getRecipePosition(recipeIndex);
            relX = mousepos.x - guiLeft - offset.x;
            relY = mousepos.y - guiTop - offset.y;
        }

        CachedRecipe rec = arecipes.get(recipeIndex);
        return getTooltipsAt(rec, relX, relY, currenttip);
    }

    @Override
    public List<String> handleItemTooltip(GuiRecipe<?> gui, ItemStack stack, List<String> currenttip, int recipeIndex) {
        currenttip = super.handleItemTooltip(gui, stack, currenttip, recipeIndex);
        if (recipeIndex < 0 || recipeIndex >= arecipes.size() || stack == null) return currenttip;

        CachedRecipe rec = arecipes.get(recipeIndex);
        ComponentStatsData data = (rec instanceof CachedNeutronInteractionRecipe r1) ? r1.data
            : (rec instanceof CachedComponentThermalRecipe r2) ? r2.data : null;
        if (data == null) return currenttip;

        if (matches(stack, data)) {
            currenttip.add(
                EnumChatFormatting.GOLD
                    + StatCollector.translateToLocalFormatted("gt.nei.neutron_interaction.role", data.category));
            currenttip.add(
                EnumChatFormatting.YELLOW + StatCollector.translateToLocalFormatted(
                    "gt.nei.neutron_interaction.fast_stats",
                    formatPercent(data.fastScattering),
                    formatPercent(data.fastAbsorption),
                    formatPercent(data.slowingProbability)));
            currenttip.add(
                EnumChatFormatting.AQUA + StatCollector.translateToLocalFormatted(
                    "gt.nei.neutron_interaction.thermal_stats",
                    formatPercent(data.thermalScattering),
                    formatPercent(data.thermalAbsorption)));
            if (data.isReflector) {
                currenttip.add(
                    EnumChatFormatting.GREEN + String.format(
                        Locale.US,
                        "Reflection: Fast %s, Thermal %s",
                        formatPercent(data.reflectionFast),
                        formatPercent(data.reflectionThermal)));
            }
            if (data.extraInfo != null) {
                currenttip.add(EnumChatFormatting.GRAY + data.extraInfo);
            }
        } else if (data.transmutationOutput != null && matchesStack(stack, data.transmutationOutput)) {
            currenttip.add(
                EnumChatFormatting.DARK_RED
                    + StatCollector.translateToLocal("gt.nei.neutron_interaction.depleted_title"));
            currenttip.add(
                EnumChatFormatting.GRAY + StatCollector.translateToLocal("gt.nei.neutron_interaction.depleted_desc"));
        }
        return currenttip;
    }

    public static void drawPanel(int x, int y, int w, int h) {
        GuiDraw.drawRect(x + 1, y, w - 2, 1, 0xFF373737);
        GuiDraw.drawRect(x, y + 1, 1, h - 2, 0xFF373737);
        GuiDraw.drawRect(x + 1, y + h - 1, w - 2, 1, 0xFFFFFFFF);
        GuiDraw.drawRect(x + w - 1, y + 1, 1, h - 2, 0xFFFFFFFF);
        GuiDraw.drawRect(x + 1, y + 1, w - 2, h - 2, 0xFFC6C6C6);
    }

    public static void drawSlotBox(int x, int y) {
        GuiDraw.drawRect(x, y, 18, 1, 0xFF373737);
        GuiDraw.drawRect(x, y, 1, 18, 0xFF373737);
        GuiDraw.drawRect(x + 17, y, 1, 18, 0xFFFFFFFF);
        GuiDraw.drawRect(x, y + 17, 18, 1, 0xFFFFFFFF);
        GuiDraw.drawRect(x + 1, y + 1, 16, 16, 0xFF8B8B8B);
    }

    public static void drawCustomTexturedModalRect(int x, int y, int u, int v, int width, int height, int texWidth,
        int texHeight) {
        float f = 1.0F / (float) texWidth;
        float f1 = 1.0F / (float) texHeight;
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + height, 0.0, (float) u * f, (float) (v + height) * f1);
        tessellator.addVertexWithUV(x + width, y + height, 0.0, (float) (u + width) * f, (float) (v + height) * f1);
        tessellator.addVertexWithUV(x + width, y, 0.0, (float) (u + width) * f, (float) v * f1);
        tessellator.addVertexWithUV(x, y, 0.0, (float) u * f, (float) v * f1);
        tessellator.draw();
    }

    /**
     * Formats a numeric value with up to 2 fractional digits (rounded half-up)
     * and trims trailing decimal zeros and decimal points (e.g. 10.00 -> "10", 10.50 -> "10.5", 10.25 -> "10.25").
     */
    public static String formatNumber(double val) {
        if (Double.isNaN(val) || Double.isInfinite(val)) {
            return "0";
        }
        double rounded = Math.round(val * 100.0) / 100.0;
        if (Math.abs(rounded) == 0.0) {
            return "0";
        }
        String s = String.format(Locale.US, "%.2f", rounded);
        if (s.indexOf('.') >= 0) {
            while (s.endsWith("0")) {
                s = s.substring(0, s.length() - 1);
            }
            if (s.endsWith(".")) {
                s = s.substring(0, s.length() - 1);
            }
        }
        return s;
    }

    /**
     * Formats a fraction (0.0 .. 1.0) as a percentage string (0% .. 100%) with up to 2 fractional digits,
     * trimming trailing zeros (e.g. 0.10 -> "10%", 0.09999999999999998 -> "10%", 0.125 -> "12.5%").
     */
    public static String formatPercent(double frac) {
        return formatPercentFromPercent(frac * 100.0);
    }

    /**
     * Formats a percentage value (0.0 .. 100.0) with up to 2 fractional digits and a trailing '%' sign.
     */
    public static String formatPercentFromPercent(double pct) {
        return formatNumber(pct) + "%";
    }

    public static void drawArrow(double x1, double y1, double x2, double y2, double shaftWidth, double headLength,
        double headWidth, int argb) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-4) return;

        double ux = dx / len;
        double uy = dy / len;
        double nx = -uy;
        double ny = ux;

        double hLen = Math.min(headLength, len * 0.8);
        double baseX = x2 - hLen * ux;
        double baseY = y2 - hLen * uy;

        float a = ((argb >> 24) & 0xFF) / 255.0F;
        float r = ((argb >> 16) & 0xFF) / 255.0F;
        float g = ((argb >> 8) & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(r, g, b, a);

        Tessellator tess = Tessellator.instance;

        // Draw shaft as 2 triangles
        tess.startDrawing(GL11.GL_TRIANGLES);
        double sLeftX1 = x1 + (shaftWidth / 2.0) * nx;
        double sLeftY1 = y1 + (shaftWidth / 2.0) * ny;
        double sRightX1 = x1 - (shaftWidth / 2.0) * nx;
        double sRightY1 = y1 - (shaftWidth / 2.0) * ny;
        double sLeftX2 = baseX + (shaftWidth / 2.0) * nx;
        double sLeftY2 = baseY + (shaftWidth / 2.0) * ny;
        double sRightX2 = baseX - (shaftWidth / 2.0) * nx;
        double sRightY2 = baseY - (shaftWidth / 2.0) * ny;

        tess.addVertex(sLeftX1, sLeftY1, 0.0);
        tess.addVertex(sLeftX2, sLeftY2, 0.0);
        tess.addVertex(sRightX2, sRightY2, 0.0);

        tess.addVertex(sLeftX1, sLeftY1, 0.0);
        tess.addVertex(sRightX2, sRightY2, 0.0);
        tess.addVertex(sRightX1, sRightY1, 0.0);

        // Draw head as 1 triangle
        tess.addVertex(x2, y2, 0.0);
        tess.addVertex(baseX + (headWidth / 2.0) * nx, baseY + (headWidth / 2.0) * ny, 0.0);
        tess.addVertex(baseX - (headWidth / 2.0) * nx, baseY - (headWidth / 2.0) * ny, 0.0);

        tess.draw();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glPopAttrib();
        GL11.glPopMatrix();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void drawPieSlice(double cx, double cy, double radius, double startAngle, double endAngle, int argb) {
        double sweep = endAngle - startAngle;
        if (sweep <= 1e-4) return;

        float a = ((argb >> 24) & 0xFF) / 255.0F;
        float r = ((argb >> 16) & 0xFF) / 255.0F;
        float g = ((argb >> 8) & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(r, g, b, a);

        int segments = Math.max(3, (int) Math.ceil(36 * (sweep / (2 * Math.PI))));
        double step = sweep / segments;

        Tessellator tess = Tessellator.instance;
        tess.startDrawing(GL11.GL_TRIANGLES);
        for (int i = 0; i < segments; i++) {
            double a1 = startAngle + i * step;
            double a2 = startAngle + (i + 1) * step;
            tess.addVertex(cx, cy, 0.0);
            tess.addVertex(cx + radius * Math.cos(a1), cy - radius * Math.sin(a1), 0.0);
            tess.addVertex(cx + radius * Math.cos(a2), cy - radius * Math.sin(a2), 0.0);
        }
        tess.draw();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glPopAttrib();
        GL11.glPopMatrix();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void drawFastNeutronDiagram(ComponentStatsData d) {
        drawFastNeutronDiagram(d, 33);
    }

    public static void drawFastNeutronDiagram(ComponentStatsData d, int baseY) {
        try {
            double dy = baseY - 2.0;

            // Incoming fast neutrons arrow (blue, down-right towards center tile)
            drawArrow(24.0, 17.0 + dy, 70.0, 30.0 + dy, 2.0, 5.0, 6.0, 0xFF0033EE);

            // Reflected fast neutrons arrow (blue, down-left away from center tile)
            drawArrow(70.0, 38.0 + dy, 24.0, 51.0 + dy, 2.0, 5.0, 6.0, 0xFF0033EE);

            // Absorbed neutrons arrow (red, straight down under center tile)
            drawArrow(83.0, 44.0 + dy, 83.0, 52.0 + dy, 2.0, 4.0, 5.5, 0xFFDD0000);

            // Three scattered neutrons arrows (green, pointing right away from center tile)
            // Middle: horizontal
            drawArrow(96.0, 34.0 + dy, 110.0, 34.0 + dy, 1.2, 3.5, 4.5, 0xFF00CC00);
            // Upper: 45 degrees up-right
            drawArrow(96.0, 32.0 + dy, 107.0, 21.0 + dy, 1.2, 3.5, 4.5, 0xFF00CC00);
            // Lower: 45 degrees down-right
            drawArrow(96.0, 36.0 + dy, 107.0, 47.0 + dy, 1.2, 3.5, 4.5, 0xFF00CC00);

            // Dynamic pie chart at (136, 34 + dy), radius 9.0
            double slowing = Math.max(0.0, Math.min(1.0, d.slowingProbability));
            double fastFrac = 1.0 - slowing;
            if (slowing <= 0.001) {
                drawPieSlice(136.0, 34.0 + dy, 9.0, 0.0, 2.0 * Math.PI, 0xFF0033EE);
            } else if (fastFrac <= 0.001) {
                drawPieSlice(136.0, 34.0 + dy, 9.0, 0.0, 2.0 * Math.PI, 0xFF7F00FF);
            } else {
                double splitAngle = fastFrac * 2.0 * Math.PI;
                drawPieSlice(136.0, 34.0 + dy, 9.0, 0.0, splitAngle, 0xFF0033EE);
                drawPieSlice(136.0, 34.0 + dy, 9.0, splitAngle, 2.0 * Math.PI, 0xFF7F00FF);
            }

            // Scattered fast neutrons arrow (blue, straight up above pie chart)
            drawArrow(136.0, 24.0 + dy, 136.0, 16.0 + dy, 2.0, 4.0, 5.5, 0xFF0033EE);

            // Scattered moderated neutrons arrow (purple, straight down below pie chart)
            drawArrow(136.0, 44.0 + dy, 136.0, 52.0 + dy, 2.0, 4.0, 5.5, 0xFF7F00FF);
        } catch (Throwable ignored) {}
    }
}
