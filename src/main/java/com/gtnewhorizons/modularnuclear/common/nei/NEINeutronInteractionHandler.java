package com.gtnewhorizons.modularnuclear.common.nei;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

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

        public ComponentStatsData(ItemStack stack, String name, String category, double fastScattering,
            double fastAbsorption, double slowingProbability, double thermalScattering, double thermalAbsorption,
            boolean isReflector, double reflectionFast, double reflectionThermal, double heatTransferCoeff,
            double thermalInsulationFactor, double selfCoolingRate, double componentCoolingRate,
            double heatTransferRate, double maxTemperature, double boilingPoint, double specificHeat,
            boolean producesDirectEU, long maxDirectEU, String directEUTier, double directEUPenNeutronFast,
            double directEUPenNeutronThermal, boolean hasDurability, long durability, String durabilityLabel,
            boolean hasTransmutation, ItemStack transmutationOutput, long transmutationNeutrons, String extraInfo) {
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
        }
    }

    /**
     * Page 1: Fast & Thermal neutron scattering, moderation, absorption, and reflection.
     */
    public class CachedNeutronInteractionRecipe extends CachedRecipe {

        public final PositionedStack input;
        public final PositionedStack inputThermal;
        public final PositionedStack catalyst;
        public final ComponentStatsData data;

        public CachedNeutronInteractionRecipe(ComponentStatsData data) {
            this.data = data;
            List<ItemStack> allVariants = new ArrayList<>();
            if (data.stack != null) allVariants.add(data.stack);
            allVariants.addAll(data.alternateStacks);

            this.input = new PositionedStack(allVariants, 67, 31);
            this.inputThermal = new PositionedStack(allVariants, 67, 97);
            ItemStack ctrl = getControllerStack();
            this.catalyst = ctrl != null ? new PositionedStack(ctrl, 6, 135) : null;
        }

        @Override
        public PositionedStack getIngredient() {
            return input;
        }

        @Override
        public PositionedStack getResult() {
            return input;
        }

        @Override
        public List<PositionedStack> getOtherStacks() {
            List<PositionedStack> list = new ArrayList<>();
            list.add(inputThermal);
            if (catalyst != null) list.add(catalyst);
            return list;
        }
    }

    /**
     * Page 2: Thermal properties, direct EU generation, durability, and radiolytic transmutation.
     */
    public class CachedComponentThermalRecipe extends CachedRecipe {

        public final PositionedStack thermalInput;
        public final PositionedStack durabilityInput;
        public final PositionedStack transmutationOutput;
        public final PositionedStack catalyst;
        public final ComponentStatsData data;

        public CachedComponentThermalRecipe(ComponentStatsData data) {
            this.data = data;
            List<ItemStack> allVariants = new ArrayList<>();
            if (data.stack != null) allVariants.add(data.stack);
            allVariants.addAll(data.alternateStacks);

            this.thermalInput = new PositionedStack(allVariants, 58, 27);
            int durX = data.transmutationOutput != null ? 31 : 67;
            this.durabilityInput = new PositionedStack(allVariants, durX, 97);
            this.transmutationOutput = data.transmutationOutput != null
                ? new PositionedStack(data.transmutationOutput, 101, 97)
                : null;
            ItemStack ctrl = getControllerStack();
            this.catalyst = ctrl != null ? new PositionedStack(ctrl, 6, 135) : null;
        }

        @Override
        public PositionedStack getIngredient() {
            return durabilityInput;
        }

        @Override
        public PositionedStack getResult() {
            return transmutationOutput != null ? transmutationOutput : thermalInput;
        }

        @Override
        public List<PositionedStack> getOtherStacks() {
            List<PositionedStack> list = new ArrayList<>();
            list.add(thermalInput);
            if (catalyst != null) list.add(catalyst);
            if (transmutationOutput != null) {
                list.add(transmutationOutput);
            }
            return list;
        }
    }

    private static final List<ComponentStatsData> ALL_COMPONENTS = new ArrayList<>();
    private static boolean initialized = false;

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

        // 4. RADIOVOLTAIC PLATES - 100% absorption, generates direct EU from absorbed neutron flux
        addRadiovoltaic(
            ModItems.radiovoltaicPlateHV != null ? new ItemStack(ModItems.radiovoltaicPlateHV) : null,
            "HV Radiovoltaic Plate",
            1,
            1024L,
            "HV (2A)",
            "Converts absorbed neutrons to direct EU | Max 1024 EU/t");
        addRadiovoltaic(
            ModItems.radiovoltaicPlateEV != null ? new ItemStack(ModItems.radiovoltaicPlateEV) : null,
            "EV Radiovoltaic Plate",
            2,
            4096L,
            "EV (2A)",
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
        String extra) {
        if (stack == null && isTestMode()) stack = createTestPlaceholder(name);
        if (stack == null) return;
        ALL_COMPONENTS.add(
            new ComponentStatsData(
                stack,
                name,
                "Radiovoltaic Plate",
                0.0,
                1.0,
                0.0,
                0.0,
                1.0,
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
                extra));
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
        this.transferRects.add(new RecipeTransferRect(new Rectangle(6, 135, 18, 18), getOverlayIdentifier()));
    }

    @Override
    public void loadCraftingRecipes(String outputId, Object... results) {
        initData();
        if (outputId != null && outputId.equals(getOverlayIdentifier())) {
            for (ComponentStatsData data : ALL_COMPONENTS) {
                this.arecipes.add(new CachedNeutronInteractionRecipe(data));
                this.arecipes.add(new CachedComponentThermalRecipe(data));
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
                this.arecipes.add(new CachedComponentThermalRecipe(data));
            } else if (data.transmutationOutput != null && matchesStack(result, data.transmutationOutput)) {
                this.arecipes.add(new CachedComponentThermalRecipe(data));
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
                this.arecipes.add(new CachedComponentThermalRecipe(data));
            }
            return;
        }

        for (ComponentStatsData data : ALL_COMPONENTS) {
            if (matches(ingredient, data)) {
                this.arecipes.add(new CachedNeutronInteractionRecipe(data));
                this.arecipes.add(new CachedComponentThermalRecipe(data));
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

    // =========================================================================
    // GUI Rendering
    // =========================================================================

    @Override
    public void drawBackground(int recipeIndex) {
        if (recipeIndex < 0 || recipeIndex >= arecipes.size()) return;
        CachedRecipe cached = this.arecipes.get(recipeIndex);

        // Standard outer panels (top & bottom)
        drawPanel(6, 2, 154, 64);
        drawPanel(6, 68, 154, 64);
        drawSlotBox(5, 134);

        if (cached instanceof CachedNeutronInteractionRecipe recipe) {
            // Page 1: Fast & Thermal interaction diagrams
            drawSlotBox(66, 30);
            drawSlotBox(66, 96);

            GuiDraw.changeTexture(TEXTURE_ATLAS);
            // Fast diagram: (0, 0, 88, 54) at (14, 12)
            GuiDraw.drawTexturedModalRect(14, 12, 0, 0, 88, 54);
            // Thermal diagram: (0, 54, 88, 54) at (14, 78)
            GuiDraw.drawTexturedModalRect(14, 78, 0, 54, 88, 54);

            // Pie chart: (u=index*16, v=240, w=16, h=16) at (115, 28)
            double slowing = recipe.data.slowingProbability;
            int index = 1 + (int) Math.floor(slowing * 9.0);
            if (slowing <= 0.0) index = 0;
            else if (slowing >= 1.0) index = 10;
            GuiDraw.drawTexturedModalRect(115, 28, index * 16, 240, 16, 16);

        } else if (cached instanceof CachedComponentThermalRecipe recipe) {
            // Page 2: Thermal & Energy Properties and Durability / Transmutation
            drawSlotBox(57, 26);

            int durSlotX = recipe.data.transmutationOutput != null ? 30 : 66;
            drawSlotBox(durSlotX, 96);

            if (recipe.data.transmutationOutput != null) {
                drawSlotBox(100, 96);
            }

            // Draw thermal / energy capture diagram: (0, 109, 92, 31) at (30, 20)
            GuiDraw.changeTexture(TEXTURE_ATLAS);
            GuiDraw.drawTexturedModalRect(30, 20, 0, 109, 92, 31);

            // Draw animated progress arrow if transmutation product exists
            if (recipe.data.transmutationOutput != null) {
                GuiDraw.changeTexture(LONG_ARROW);
                drawCustomTexturedModalRect(58, 95, 0, 0, 40, 20, 40, 40);
                long cycle = System.currentTimeMillis() % 3000L;
                int fillW = (int) (40.0 * (cycle / 3000.0));
                if (fillW > 0) {
                    drawCustomTexturedModalRect(58, 95, 0, 20, fillW, 20, 40, 40);
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

            // --- Page 1: Fast Neutron Panel ---
            GuiDraw
                .drawStringC(StatCollector.translateToLocal("gt.nei.neutron_interaction.fast"), 83, 5, 0x404040, false);

            if (d.isReflector) {
                String reflStr = String.format(Locale.US, "%.1f %%", d.reflectionFast * 100.0);
                GuiDraw.drawStringC(reflStr, 47, 16, 0x9A6B00, false);
                GuiDraw.drawStringC("REFLECT", 47, 25, 0x9A6B00, false);
            } else {
                String fastScatStr = String.format(Locale.US, "%.1f %%", d.fastScattering * 100.0);
                GuiDraw.drawStringC(fastScatStr, 47, 16, 0x404040, false);
            }

            String fastAbsStr = String.format(Locale.US, "%.1f %%", d.fastAbsorption * 100.0);
            GuiDraw.drawStringC(fastAbsStr, 97, 54, 0x00A000, false);

            String thermFracStr = String.format(Locale.US, "%.1f %%", d.slowingProbability * 100.0);
            GuiDraw.drawStringC(thermFracStr, 123, 16, 0x0C27A7, false);

            String fastFracStr = String.format(Locale.US, "%.1f %%", (1.0 - d.slowingProbability) * 100.0);
            GuiDraw.drawStringC(fastFracStr, 123, 46, 0xBC1A1A, false);

            // --- Page 1: Thermal Neutron Panel ---
            GuiDraw.drawStringC(
                StatCollector.translateToLocal("gt.nei.neutron_interaction.thermal"),
                83,
                71,
                0x404040,
                false);

            if (d.isReflector) {
                String reflStr = String.format(Locale.US, "%.1f %%", d.reflectionThermal * 100.0);
                GuiDraw.drawStringC(reflStr, 47, 82, 0x9A6B00, false);
                GuiDraw.drawStringC("REFLECT", 47, 91, 0x9A6B00, false);
            } else {
                String thermScatStr = String.format(Locale.US, "%.1f %%", d.thermalScattering * 100.0);
                GuiDraw.drawStringC(thermScatStr, 47, 82, 0x404040, false);
            }

            String thermAbsStr = String.format(Locale.US, "%.1f %%", d.thermalAbsorption * 100.0);
            GuiDraw.drawStringC(thermAbsStr, 97, 120, 0x00A000, false);

            // Bottom summary
            GuiDraw.drawString(EnumChatFormatting.DARK_BLUE + "[" + d.category + "]", 28, 137, 0x103080, false);
            if (d.extraInfo != null) {
                GuiDraw.drawString(EnumChatFormatting.GRAY + d.extraInfo, 28, 147, 0x505050, false);
            }

        } else if (cached instanceof CachedComponentThermalRecipe recipe) {
            ComponentStatsData d = recipe.data;

            // --- Page 2: Thermal & Energy Panel ---
            GuiDraw.drawStringC(
                StatCollector.translateToLocal("gt.nei.neutron_interaction.capture"),
                83,
                5,
                0x404040,
                false);

            // Direct EU production (left of diagram)
            if (d.producesDirectEU) {
                if (d.directEUPenNeutronFast > 0) {
                    GuiDraw.drawString(
                        String.format(Locale.US, "%.0f EU/n", d.directEUPenNeutronFast),
                        32,
                        52,
                        0x00A000,
                        false);
                } else if (d.selfCoolingRate > 0) {
                    GuiDraw
                        .drawString(String.format(Locale.US, "%.0f Hu/t", d.selfCoolingRate), 32, 52, 0x0055AA, false);
                } else if (d.heatTransferRate > 0) {
                    GuiDraw
                        .drawString(String.format(Locale.US, "%.0f Hu/t", d.heatTransferRate), 32, 52, 0x0055AA, false);
                } else {
                    GuiDraw.drawString("Active", 34, 52, 0x00A000, false);
                }
            } else {
                GuiDraw.drawString("0 EU", 36, 52, 0x606060, false);
            }

            // Heat transfer & thermal metrics (right of diagram)
            if (d.thermalInsulationFactor > 0.0) {
                GuiDraw.drawString(
                    String.format(Locale.US, "Barrier: %.0f%%", d.thermalInsulationFactor * 100.0),
                    110,
                    18,
                    0x902020,
                    false);
                GuiDraw.drawString(String.format(Locale.US, "k = %.3f", d.heatTransferCoeff), 110, 34, 0x404040, false);
            } else if (d.boilingPoint > 0.0) {
                GuiDraw
                    .drawString(String.format(Locale.US, "Boils: %.0f °C", d.boilingPoint), 110, 18, 0x205090, false);
                GuiDraw.drawString(String.format(Locale.US, "Cp: %.2f", d.specificHeat), 110, 34, 0x404040, false);
            } else if (d.selfCoolingRate > 0.0 || d.componentCoolingRate > 0.0) {
                double cool = d.selfCoolingRate > 0 ? d.selfCoolingRate : d.componentCoolingRate;
                GuiDraw.drawString(String.format(Locale.US, "Cool: %.0f Hu/t", cool), 110, 18, 0x0055AA, false);
                GuiDraw.drawString(String.format(Locale.US, "k = %.2f", d.heatTransferCoeff), 110, 34, 0x404040, false);
            } else if (d.heatTransferRate > 0.0) {
                GuiDraw.drawString(
                    String.format(Locale.US, "Xfer: %.0f Hu/t", d.heatTransferRate),
                    110,
                    18,
                    0x0055AA,
                    false);
                GuiDraw.drawString(String.format(Locale.US, "k = %.2f", d.heatTransferCoeff), 110, 34, 0x404040, false);
            } else if (d.maxTemperature > 0.0) {
                GuiDraw.drawString(String.format(Locale.US, "k = %.2f", d.heatTransferCoeff), 110, 18, 0x404040, false);
                GuiDraw
                    .drawString(String.format(Locale.US, "Max: %.0f °C", d.maxTemperature), 110, 34, 0xAA5500, false);
            } else {
                GuiDraw.drawString(String.format(Locale.US, "k = %.2f", d.heatTransferCoeff), 110, 18, 0x404040, false);
                GuiDraw.drawString("Passive", 110, 34, 0x606060, false);
            }

            // Summary line in top panel
            if (d.maxDirectEU > 0) {
                String euText = String.format(
                    Locale.US,
                    "Max: %d EU/t (%s)",
                    d.maxDirectEU,
                    d.directEUTier != null ? d.directEUTier : "");
                GuiDraw.drawStringC(euText, 83, 54, 0x00A000, false);
            } else if (d.selfCoolingRate > 0) {
                GuiDraw.drawStringC(
                    String.format(Locale.US, "Self-cooling rate: %.1f Hu/t", d.selfCoolingRate),
                    83,
                    54,
                    0x0055AA,
                    false);
            } else if (d.componentCoolingRate > 0) {
                GuiDraw.drawStringC(
                    String.format(Locale.US, "Adjacent cooling: %.1f Hu/t", d.componentCoolingRate),
                    83,
                    54,
                    0x0055AA,
                    false);
            } else if (d.heatTransferRate > 0) {
                GuiDraw.drawStringC(
                    String.format(Locale.US, "Adjacent component transfer: %.1f Hu/t", d.heatTransferRate),
                    83,
                    54,
                    0x0055AA,
                    false);
            } else if (d.thermalInsulationFactor > 0) {
                GuiDraw.drawStringC(
                    String.format(Locale.US, "Blocks %.0f%% of heat conduction", d.thermalInsulationFactor * 100.0),
                    83,
                    54,
                    0x902020,
                    false);
            } else {
                GuiDraw.drawStringC(
                    String.format(Locale.US, "Thermal conduction coefficient: k = %.2f", d.heatTransferCoeff),
                    83,
                    54,
                    0x505050,
                    false);
            }

            // --- Page 2: Durability & Byproducts Panel ---
            GuiDraw.drawStringC(
                StatCollector.translateToLocal("gt.nei.neutron_interaction.absorption"),
                83,
                71,
                0x404040,
                false);

            if (d.transmutationOutput != null) {
                String reqStr = formatNeutrons(d.transmutationNeutrons);
                String transStr = reqStr + " Neutrons -> " + d.transmutationOutput.getDisplayName();
                GuiDraw.drawStringC(transStr, 83, 120, 0x205090, false);
            } else if (d.hasDurability) {
                String durStr = d.durabilityLabel + ": " + formatNeutrons(d.durability);
                GuiDraw.drawStringC(durStr, 83, 120, 0x404040, false);
            } else {
                GuiDraw.drawStringC(
                    d.durabilityLabel != null ? d.durabilityLabel : "Indestructible",
                    83,
                    120,
                    0x008000,
                    false);
            }

            // Bottom summary
            GuiDraw.drawString(EnumChatFormatting.DARK_BLUE + "[" + d.category + "]", 28, 137, 0x103080, false);
            if (d.extraInfo != null) {
                GuiDraw.drawString(EnumChatFormatting.GRAY + d.extraInfo, 28, 147, 0x505050, false);
            }
        }
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

    // =========================================================================
    // Tooltips
    // =========================================================================

    @Override
    public List<String> handleTooltip(GuiRecipe<?> gui, List<String> currenttip, int recipeIndex) {
        currenttip = super.handleTooltip(gui, currenttip, recipeIndex);
        if (recipeIndex < 0 || recipeIndex >= arecipes.size()) return currenttip;

        Point mousepos = GuiDraw.getMousePosition();
        Dimension displaySize = GuiDraw.displaySize();
        int ySize = Math.min(Math.max(displaySize.height - 68, 166), 370);
        int guiLeft = (displaySize.width - 176) / 2;
        int guiTop = (displaySize.height - ySize) / 2 + 10;
        Point offset = gui.getRecipePosition(recipeIndex);
        int relX = mousepos.x - guiLeft - offset.x;
        int relY = mousepos.y - guiTop - offset.y;

        CachedRecipe rec = arecipes.get(recipeIndex);
        if (rec instanceof CachedNeutronInteractionRecipe r1) {
            ComponentStatsData d = r1.data;
            // Page 1 Tooltips
            if (relX >= 25 && relX <= 65 && relY >= 10 && relY <= 26) {
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
                            + StatCollector.translateToLocal("gt.nei.neutron_interaction.scattering"));
                    currenttip.add(
                        EnumChatFormatting.GRAY
                            + StatCollector.translateToLocal("gt.nei.neutron_interaction.scattering_fast.desc"));
                }
            } else if (relX >= 80 && relX <= 115 && relY >= 46 && relY <= 62) {
                currenttip.add(
                    EnumChatFormatting.GREEN
                        + StatCollector.translateToLocal("gt.nei.neutron_interaction.absorption_title"));
                currenttip.add(
                    EnumChatFormatting.GRAY
                        + StatCollector.translateToLocal("gt.nei.neutron_interaction.absorption_fast.desc"));
            } else if ((relX >= 105 && relX <= 145 && relY >= 10 && relY <= 24)
                || (relX >= 112 && relX <= 134 && relY >= 26 && relY <= 46)) {
                    currenttip.add(
                        EnumChatFormatting.BLUE
                            + StatCollector.translateToLocal("gt.nei.neutron_interaction.thermal_fraction"));
                    currenttip.add(
                        EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(
                            "gt.nei.neutron_interaction.thermal_fraction.desc",
                            d.slowingProbability * 100.0));
                } else if (relX >= 105 && relX <= 145 && relY >= 40 && relY <= 54) {
                    currenttip.add(
                        EnumChatFormatting.RED
                            + StatCollector.translateToLocal("gt.nei.neutron_interaction.fast_fraction"));
                    currenttip.add(
                        EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(
                            "gt.nei.neutron_interaction.fast_fraction.desc",
                            (1.0 - d.slowingProbability) * 100.0));
                } else if (relX >= 25 && relX <= 65 && relY >= 74 && relY <= 92) {
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
                } else if (relX >= 80 && relX <= 115 && relY >= 112 && relY <= 128) {
                    currenttip.add(
                        EnumChatFormatting.GREEN
                            + StatCollector.translateToLocal("gt.nei.neutron_interaction.absorption_title"));
                    currenttip.add(
                        EnumChatFormatting.GRAY
                            + StatCollector.translateToLocal("gt.nei.neutron_interaction.absorption_thermal.desc"));
                }
        } else if (rec instanceof CachedComponentThermalRecipe r2) {
            ComponentStatsData d = r2.data;
            // Page 2 Tooltips
            if (relX >= 20 && relX <= 55 && relY >= 44 && relY <= 62) {
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
            } else if (relX >= 95 && relX <= 155 && relY >= 12 && relY <= 40) {
                currenttip.add(
                    EnumChatFormatting.GOLD + StatCollector.translateToLocal("gt.nei.neutron_interaction.conduction"));
                currenttip.add(
                    EnumChatFormatting.GRAY + StatCollector
                        .translateToLocalFormatted("gt.nei.neutron_interaction.conduction.desc", d.heatTransferCoeff));
            } else if (relX >= 25 && relX <= 140 && relY >= 48 && relY <= 64) {
                currenttip.add(
                    EnumChatFormatting.AQUA
                        + StatCollector.translateToLocal("gt.nei.neutron_interaction.multiplication"));
                currenttip.add(
                    EnumChatFormatting.GRAY
                        + StatCollector.translateToLocal("gt.nei.neutron_interaction.multiplication.desc"));
            } else if (relX >= 25 && relX <= 140 && relY >= 90 && relY <= 130) {
                currenttip.add(
                    EnumChatFormatting.WHITE
                        + StatCollector.translateToLocal("gt.nei.neutron_interaction.absorption_capacity"));
                currenttip.add(
                    EnumChatFormatting.GRAY
                        + StatCollector.translateToLocal("gt.nei.neutron_interaction.absorption_capacity.desc"));
            }
        }
        return currenttip;
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
                    data.fastScattering * 100.0,
                    data.fastAbsorption * 100.0,
                    data.slowingProbability * 100.0));
            currenttip.add(
                EnumChatFormatting.AQUA + StatCollector.translateToLocalFormatted(
                    "gt.nei.neutron_interaction.thermal_stats",
                    data.thermalScattering * 100.0,
                    data.thermalAbsorption * 100.0));
            if (data.isReflector) {
                currenttip.add(
                    EnumChatFormatting.GREEN + String.format(
                        Locale.US,
                        "Reflection: Fast %.1f%%, Thermal %.1f%%",
                        data.reflectionFast * 100.0,
                        data.reflectionThermal * 100.0));
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
}
