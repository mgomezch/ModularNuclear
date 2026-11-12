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
import com.gtnewhorizons.modularnuclear.common.gui.ColorScales;
import com.gtnewhorizons.modularnuclear.common.item.ModItems;
import com.gtnewhorizons.modularnuclear.common.metatileentity.ModMetaTileEntities;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.util.Swallowed;

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

    public static final ResourceLocation LONG_ARROW = new ResourceLocation(
        "modularnuclear",
        "textures/gui/nei/long_arrow.png");

    public static final String OVERLAY_ID = "gt.nei.neutron_interaction";

    public static final String CATEGORY_EMPTY = "Empty Core Tile";

    public static final int THERMAL_PANEL_Y = 101;
    public static final int THERMAL_SLOT_Y = THERMAL_PANEL_Y + 23;

    public static final int HEAT_PANEL_Y = 169;
    public static final int HEAT_PANEL_H = 108;
    public static final int HEAT_SLOT_Y = HEAT_PANEL_Y + 23;
    public static final int HEAT_ROW_Y = HEAT_PANEL_Y + 78;
    private static final int[] HEAT_ROW_X = { 17, 36, 55, 74, 93, 112, 131 };

    public static final int DURABILITY_PANEL_Y = HEAT_PANEL_Y + HEAT_PANEL_H + 4;
    public static final int DURABILITY_SLOT_Y = DURABILITY_PANEL_Y + 28;

    public static final int RV_PANEL_X = 6;
    public static final int RV_PANEL_Y = DURABILITY_PANEL_Y;
    public static final int RV_PANEL_W = 154;
    public static final int RV_PANEL_H = 72;

    public static final int RV_PLOT_X = 28;
    public static final int RV_PLOT_Y = RV_PANEL_Y + 15;
    public static final int RV_PLOT_W = 104;
    public static final int RV_PLOT_H = 42;

    public static final int FAST_PIE_X = 142;
    public static final int FAST_SCATTERING_LABEL_X = 120;

    public static class ComponentStatsData {

        public final ItemStack stack;
        public final List<ItemStack> alternateStacks = new ArrayList<>();
        public final String name;
        public final String category;

        // Page 1: Neutron Interaction
        public final double fastScattering;
        /**
         * Fast reflection, for an item with no configuration entry (the card otherwise shows the configured one, as for
         * the other fast numbers). See Note [Fast neutrons are reflected or scattered]
         */
        public double fastReflection;
        public final double fastAbsorption;
        public final double slowingProbability;
        public final double thermalScattering;
        public final double thermalAbsorption;

        // Page 2: Thermal & Energy Properties
        public final double heatTransferCoeff;
        public final double thermalInsulationFactor;
        // Radiovoltaic output
        public final long maxDirectEU;

        // Durability / Capacity / Transmutation / Byproducts
        public final boolean hasDurability;
        public final long durability;
        public final String durabilityLabel;
        public final boolean hasTransmutation;
        public final ItemStack transmutationOutput;
        public final long transmutationNeutrons;

        public final String extraInfo;

        public final boolean isRadiovoltaic;
        /** The plate's config, for radiovoltaics; null otherwise. */
        public final ComponentData.RadiovoltaicData radiovoltaic;

        private ComponentCardValues cardValues;
        private Object cardValuesConfig;

        /** What the thermal and heat cards show, worked out again if the config has been reloaded. */
        public ComponentCardValues cardValues() {
            Object config = ReactorDataRegistry.getComponentData();
            if (cardValues == null || cardValuesConfig != config) {
                cardValues = ComponentCardValues.of(this);
                cardValuesConfig = config;
            }
            return cardValues;
        }

        private static double plateEuPerHu() {
            return NuclearSimulationEngine.params()
                .radiovoltaicEuPerHu();
        }

        /** The heat rate (HU/s) at which output reaches 95% of maxDirectEU. See Note [Radiovoltaic energy]. */
        public double fullOutputHeatPerSecond() {
            return radiovoltaic.fullOutputHeatPerSecond(plateEuPerHu());
        }

        /** EU/t for an absorbed heat rate in HU/s. */
        public double calculatePower(double heatPerSecond) {
            return radiovoltaic.calculatePower(heatPerSecond, plateEuPerHu());
        }

        /** Heat the plate keeps, in HU/s, for an absorbed heat rate in HU/s. */
        public double calculateHeat(double heatPerSecond) {
            return radiovoltaic.keptHeatPerSecond(heatPerSecond, plateEuPerHu());
        }

        /**
         * The heat rate (HU/s) from which plain steam makes as much as the plate's maximum output. See Note
         * [Radiovoltaic energy].
         */
        public double steamCatchUpHeatPerSecond() {
            return 20.0 * radiovoltaic.getMaxEffectiveEU()
                * radiovoltaic.euConversionEfficiency
                / NuclearSimulationEngine.params().radiovoltaicSteamEuPerHu;
        }

        /** EU/t plain steam makes of the same heat: the baseline plates are measured against. */
        public double steamPower(double heatPerSecond) {
            return heatPerSecond * NuclearSimulationEngine.params().radiovoltaicSteamEuPerHu / 20.0;
        }

        public ComponentStatsData(ItemStack stack, String name, String category, double fastScattering,
            double fastAbsorption, double slowingProbability, double thermalScattering, double thermalAbsorption,
            double heatTransferCoeff, double thermalInsulationFactor, long maxDirectEU, boolean hasDurability,
            long durability, String durabilityLabel, boolean hasTransmutation, ItemStack transmutationOutput,
            long transmutationNeutrons, String extraInfo) {
            this(
                stack,
                name,
                category,
                fastScattering,
                fastAbsorption,
                slowingProbability,
                thermalScattering,
                thermalAbsorption,
                heatTransferCoeff,
                thermalInsulationFactor,
                maxDirectEU,
                hasDurability,
                durability,
                durabilityLabel,
                hasTransmutation,
                transmutationOutput,
                transmutationNeutrons,
                extraInfo,
                null);
        }

        public ComponentStatsData(ItemStack stack, String name, String category, double fastScattering,
            double fastAbsorption, double slowingProbability, double thermalScattering, double thermalAbsorption,
            double heatTransferCoeff, double thermalInsulationFactor, long maxDirectEU, boolean hasDurability,
            long durability, String durabilityLabel, boolean hasTransmutation, ItemStack transmutationOutput,
            long transmutationNeutrons, String extraInfo, ComponentData.RadiovoltaicData radiovoltaic) {
            this.stack = stack;
            this.name = name;
            this.category = category;
            this.fastScattering = fastScattering;
            this.fastAbsorption = fastAbsorption;
            this.slowingProbability = slowingProbability;
            this.thermalScattering = thermalScattering;
            this.thermalAbsorption = thermalAbsorption;
            this.heatTransferCoeff = heatTransferCoeff;
            this.thermalInsulationFactor = thermalInsulationFactor;
            this.maxDirectEU = maxDirectEU;
            this.hasDurability = hasDurability;
            this.durability = durability;
            this.durabilityLabel = durabilityLabel;
            this.hasTransmutation = hasTransmutation;
            this.transmutationOutput = transmutationOutput;
            this.transmutationNeutrons = transmutationNeutrons;
            this.extraInfo = extraInfo;
            this.isRadiovoltaic = radiovoltaic != null;
            this.radiovoltaic = radiovoltaic;
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
        public final PositionedStack inputHeatRow;
        public final PositionedStack durabilityInput;
        public final PositionedStack transmutationOutput;
        public final PositionedStack catalyst;
        public final ComponentStatsData data;

        public CachedNeutronInteractionRecipe(ComponentStatsData data) {
            this.data = data;
            List<ItemStack> allVariants = new ArrayList<>();
            if (data.stack != null) allVariants.add(data.stack);
            allVariants.addAll(data.alternateStacks);

            for (ItemStack is : allVariants) {
                if (is != null && is.getItemDamage() == OreDictionary.WILDCARD_VALUE) {
                    is.setItemDamage(0);
                }
            }

            // Card 1 (Fast neutrons): slot box at (74, 56) -> stack at (75, 57)
            this.input = new PositionedStack(allVariants, 75, 57, false);

            // Card 2 (Thermal neutrons)
            this.inputThermal = new PositionedStack(allVariants, 75, THERMAL_SLOT_Y + 1, false);

            // Card 3 (Heat): once between its hot and cold neighbours, once in the row of tiles
            this.inputHeat = new PositionedStack(allVariants, 75, HEAT_SLOT_Y + 1, false);
            this.inputHeatRow = new PositionedStack(allVariants, 75, HEAT_ROW_Y + 1, false);

            // Card 4 (Durability & byproducts):
            if (!data.isRadiovoltaic) {
                int durSlotX = data.transmutationOutput != null ? 30 : 66;
                this.durabilityInput = new PositionedStack(allVariants, durSlotX + 1, DURABILITY_SLOT_Y + 1, false);
                this.transmutationOutput = data.transmutationOutput != null
                    ? new PositionedStack(data.transmutationOutput, 101, DURABILITY_SLOT_Y + 1, false)
                    : null;
            } else {
                this.durabilityInput = null;
                this.transmutationOutput = null;
            }

            // Header: catalyst slot box at (6, 2) -> stack at (7, 3)
            ItemStack ctrl = getControllerStack();
            this.catalyst = ctrl != null ? new PositionedStack(ctrl, 7, 3, false) : null;
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
            list.add(inputHeatRow);
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

    public static final List<ComponentStatsData> ALL_COMPONENTS = new ArrayList<>();
    private static boolean initialized = false;

    public static synchronized List<ComponentStatsData> getAllComponents() {
        initData();
        return Collections.unmodifiableList(ALL_COMPONENTS);
    }

    public static boolean sTestMode = false;

    private static int placeholderCounter = 1;
    private static final java.util.Map<String, Integer> placeholderMetaMap = new java.util.HashMap<>();

    public static synchronized void resetData() {
        initialized = false;
        ALL_COMPONENTS.clear();
        placeholderCounter = 1;
        placeholderMetaMap.clear();
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
        } catch (RuntimeException | LinkageError t) {
            return true;
        }
        return false;
    }

    public static synchronized ItemStack createTestPlaceholder(String name) {
        int meta = placeholderMetaMap.computeIfAbsent(name, k -> placeholderCounter++);
        ItemStack stack = new ItemStack(net.minecraft.init.Items.paper, 1, meta);
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
        if (isTestMode()) {
            return createTestPlaceholder(materialName + " Plate");
        }
        ItemStack s = null;
        if (mat != null) {
            s = GTOreDictUnificator.get(OrePrefixes.plate, mat, 1L);
        }
        if (s == null && materialName != null) {
            s = getFirstOre("plate" + materialName);
        }
        return s;
    }

    private static ItemStack getModeratorRod(Materials mat, String materialName) {
        if (isTestMode()) {
            return createTestPlaceholder(materialName + " Long Rod");
        }
        ItemStack s = null;
        if (mat != null) {
            s = GTOreDictUnificator.get(OrePrefixes.stickLong, mat, 1L);
        }
        if (s == null && materialName != null) {
            s = getFirstOre("stickLong" + materialName);
        }
        if (s != null && s.getItemDamage() == OreDictionary.WILDCARD_VALUE) {
            s.setItemDamage(0);
        }
        return s;
    }

    public static synchronized void initData() {
        if (initialized) return;
        initialized = true;
        ALL_COMPONENTS.clear();

        // 1. REFLECTORS - Reflect fast neutrons straight back into adjacent core cells
        addReflector(
            getReflectorPlate(Materials.Gold, "Gold"),
            "Gold Plate Reflector",
            0.65,
            0.15,
            0.10,
            0.20,
            0.90,
            0.0,
            0.30,
            0,
            "Indestructible plate reflector (k = 0.30)");
        addReflector(
            getReflectorPlate(Materials.StainlessSteel, "StainlessSteel"),
            "Stainless Steel Plate Reflector",
            0.70,
            0.10,
            0.05,
            0.20,
            0.95,
            0.0,
            0.20,
            0,
            "Indestructible plate reflector (k = 0.20)");
        addReflector(
            getReflectorPlate(Materials.TungstenCarbide, "TungstenCarbide"),
            "Tungsten Carbide Plate Reflector",
            0.75,
            0.07,
            0.02,
            0.18,
            0.98,
            0.0,
            0.10,
            0,
            "Indestructible plate reflector (k = 0.10)");
        addIC2Reflector(
            "reactorReflector",
            "Neutron Reflector",
            0.85,
            0.01,
            0.02,
            0.10,
            0.98,
            0.20,
            0.15,
            10_000,
            "Standard beryllium reflector (10,000 impacts)");
        addIC2Reflector(
            "reactorReflectorThick",
            "Thick Neutron Reflector",
            0.90,
            0.005,
            0.01,
            0.08,
            0.99,
            0.25,
            0.20,
            40_000,
            "Reinforced reflector (40,000 impacts)");
        addGTReflector(
            ItemList.Neutron_Reflector,
            "Iridium Neutron Reflector",
            0.85,
            0.01,
            0.02,
            0.10,
            0.98,
            0.20,
            0.15,
            0,
            "Permanent iridium reflector (indestructible)");
        addGTReflector(
            ItemList.Reactor_NeutronReflector,
            "Thick Iridium Neutron Reflector",
            0.90,
            0.005,
            0.01,
            0.08,
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
            0.10,
            0.83,
            0.002,
            0.50,
            0.621,
            0.009,
            0.05,
            80_000,
            dustAsh,
            "Standard moderator | Burns into ash above 2500 °C");

        ItemStack stickCarbon = getModeratorRod(Materials.Carbon, "Carbon");
        addModerator(
            stickCarbon,
            "Carbon Long Rod",
            0.10,
            0.80,
            0.003,
            0.45,
            0.60,
            0.012,
            0.05,
            10_000,
            dustAsh,
            "Budget carbon moderator | Burns into ash above 2200 °C");

        ItemStack stickBeryllium = getModeratorRod(Materials.Beryllium, "Beryllium");
        addModerator(
            stickBeryllium,
            "Beryllium Long Rod",
            0.15,
            0.81,
            0.001,
            0.65,
            0.85,
            0.004,
            0.15,
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
        addControlRod(
            Materials.Neutronium,
            "Neutronium",
            "Neutronium Control Rod",
            1.0,
            1.0,
            0.0,
            0.0,
            0.20,
            100_000_000L,
            "Total absorber | captures every fast and thermal neutron when fully inserted");

        // 4. RADIOVOLTAIC PLATES - 40% (HV) / 60% (EV) absorption, generates direct EU from absorbed neutron flux
        ComponentData.RadiovoltaicData rvHv = null;
        ComponentData.RadiovoltaicData rvEv = null;
        if (ReactorDataRegistry.getComponentData() != null
            && ReactorDataRegistry.getComponentData().radiovoltaics != null) {
            for (ComponentData.RadiovoltaicData r : ReactorDataRegistry.getComponentData().radiovoltaics) {
                if ("radiovoltaic_hv".equals(r.id) || r.tier == 1) rvHv = r;
                else if ("radiovoltaic_ev".equals(r.id) || r.tier == 2) rvEv = r;
            }
        }

        if (rvHv == null) rvHv = new ComponentData.RadiovoltaicData(
            "radiovoltaic_hv",
            "HV Radiovoltaic Plate",
            1,
            1.0,
            1024L,
            0.10,
            0.40,
            0.40);
        if (rvEv == null) rvEv = new ComponentData.RadiovoltaicData(
            "radiovoltaic_ev",
            "EV Radiovoltaic Plate",
            2,
            1.0,
            4096L,
            0.10,
            0.60,
            0.60);
        addRadiovoltaic(
            ModItems.radiovoltaicPlateHV != null ? ModItems.radiovoltaicPlateHV.copy() : null,
            "HV Radiovoltaic Plate",
            rvHv,
            String
                .format(Locale.US, "Converts absorbed neutrons to direct EU | Max %d EU/t", rvHv.getMaxEffectiveEU()));
        addRadiovoltaic(
            ModItems.radiovoltaicPlateEV != null ? ModItems.radiovoltaicPlateEV.copy() : null,
            "EV Radiovoltaic Plate",
            rvEv,
            String
                .format(Locale.US, "Converts absorbed neutrons to direct EU | Max %d EU/t", rvEv.getMaxEffectiveEU()));

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
            false,
            null,
            0L,
            "Non-boiling high-capacity coolant | Cycles to Hot Coolant");

        // 8. IC2 HEAT VENTS & HEAT EXCHANGERS
        addHeatVent("reactorVent", "Heat Vent", 0.40, "Vents its own heat, using EU");
        addHeatVent("reactorVentGold", "Overclocked Heat Vent", 0.45, "Vents its own heat, using EU");
        addHeatVent("reactorVentSpread", "Component Heat Vent", 0.40, "Vents its own heat, using EU");
        addHeatVent("reactorVentDiamond", "Advanced Heat Vent", 0.50, "Vents its own heat, using EU");

        addHeatExchanger("reactorHeatSwitch", "Heat Exchanger", 0.40, "Evens out heat with its neighbours, using EU");
        addHeatExchanger(
            "reactorHeatSwitchSpread",
            "Component Heat Exchanger",
            0.45,
            "Evens out heat with its neighbours, using EU");
        addHeatExchanger(
            "reactorHeatSwitchDiamond",
            "Advanced Heat Exchanger",
            0.50,
            "Evens out heat with its neighbours, using EU");

        // 9. EMPTY CORE TILE - Unimpeded neutron streaming
        ItemStack emptyStack = ModItems.emptyCoreTile != null ? new ItemStack(ModItems.emptyCoreTile)
            : (isTestMode() ? createTestPlaceholder("Empty Core Tile") : null);
        if (emptyStack != null) {
            ALL_COMPONENTS.add(
                new ComponentStatsData(
                    emptyStack,
                    "Empty Core Tile",
                    CATEGORY_EMPTY,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.02,
                    0.0,
                    0L,
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

    private static void addReflector(ItemStack stack, String name, double fastReflection, double fastAbs,
        double thermAbs, double fastScattering, double thermScattering, double slowing, double conduction,
        long durability, String extra) {
        if (stack == null && isTestMode()) stack = createTestPlaceholder(name);
        if (stack == null) return;
        ComponentStatsData data = new ComponentStatsData(
            stack,
            name,
            "Reflector",
            fastScattering,
            fastAbs,
            slowing,
            thermScattering,
            thermAbs,
            conduction,
            0.0,
            0L,
            durability > 0,
            durability,
            durability > 0 ? "Impacts" : "Indestructible",
            false,
            null,
            0L,
            extra);
        data.fastReflection = fastReflection;
        ALL_COMPONENTS.add(data);
    }

    private static void addIC2Reflector(String ic2Name, String name, double fastReflection, double fastAbs,
        double thermAbs, double fastScattering, double thermScattering, double slowing, double conduction,
        long durability, String extra) {
        ItemStack stack = resolveStack("IC2:" + ic2Name);
        if (stack == null) stack = resolveStack("IC2:" + ic2Name + ":1");
        if (stack == null && isTestMode()) stack = createTestPlaceholder(name);
        if (stack != null) {
            addReflector(
                stack,
                name,
                fastReflection,
                fastAbs,
                thermAbs,
                fastScattering,
                thermScattering,
                slowing,
                conduction,
                durability,
                extra);
        }
    }

    private static void addGTReflector(ItemList item, String name, double fastReflection, double fastAbs,
        double thermAbs, double fastScattering, double thermScattering, double slowing, double conduction,
        long durability, String extra) {
        ItemStack stack = null;
        if (item != null && item.hasBeenSet()) {
            stack = item.get(1L);
        } else if (isTestMode()) {
            stack = createTestPlaceholder(name);
        }
        if (stack != null) {
            addReflector(
                stack,
                name,
                fastReflection,
                fastAbs,
                thermAbs,
                fastScattering,
                thermScattering,
                slowing,
                conduction,
                durability,
                extra);
        }
    }

    private static void addModerator(ItemStack stack, String name, double fastReflection, double fastScattering,
        double fastAbs, double slowing, double thermScattering, double thermAbs, double conduction, long wearCapacity,
        ItemStack byproduct, String extra) {
        if (stack == null && isTestMode()) stack = createTestPlaceholder(name);
        if (stack == null) return;
        if (stack.getItemDamage() == OreDictionary.WILDCARD_VALUE) {
            stack.setItemDamage(0);
        }
        ComponentStatsData data = new ComponentStatsData(
            stack,
            name,
            "Moderator",
            fastScattering,
            fastAbs,
            slowing,
            thermScattering,
            thermAbs,
            conduction,
            0.0,
            0L,
            true,
            wearCapacity,
            "Wear Capacity",
            byproduct != null,
            byproduct,
            wearCapacity,
            extra);
        data.fastReflection = fastReflection;
        ALL_COMPONENTS.add(data);
    }

    private static ItemStack getFirstOre(String oreName) {
        List<ItemStack> ores = OreDictionary.getOres(oreName);
        if (ores != null && !ores.isEmpty()) {
            for (ItemStack ore : ores) {
                if (ore != null && ore.getItemDamage() != OreDictionary.WILDCARD_VALUE) {
                    return ore.copy();
                }
            }
            ItemStack first = ores.get(0)
                .copy();
            if (first.getItemDamage() == OreDictionary.WILDCARD_VALUE) {
                first.setItemDamage(0);
            }
            return first;
        }
        return null;
    }

    private static void addControlRod(Materials mat, String materialName, String name, double fastAbs, double thermAbs,
        double fastScattering, double thermScattering, double conduction, long capacity, String extra) {
        ItemStack mainStack = null;
        if (mat != null) {
            mainStack = GTOreDictUnificator.get(OrePrefixes.stickLong, mat, 1L);
        }
        if (mainStack == null && materialName != null) {
            mainStack = getFirstOre("stickLong" + materialName);
        }
        if (mainStack == null && isTestMode()) {
            mainStack = createTestPlaceholder(name);
        }
        if (mainStack == null) return;
        if (mainStack.getItemDamage() == OreDictionary.WILDCARD_VALUE) {
            mainStack.setItemDamage(0);
        }

        ComponentStatsData data = new ComponentStatsData(
            mainStack,
            name,
            "Control Rod",
            fastScattering,
            fastAbs,
            0.0,
            thermScattering,
            thermAbs,
            conduction,
            0.0,
            0L,
            true,
            capacity,
            "Neutrons",
            false,
            null,
            0L,
            extra);

        ALL_COMPONENTS.add(data);
    }

    private static void addRadiovoltaic(ItemStack stack, String name, ComponentData.RadiovoltaicData rv, String extra) {
        if (stack == null && isTestMode()) stack = createTestPlaceholder(name);
        if (stack == null) return;
        ALL_COMPONENTS.add(
            new ComponentStatsData(
                stack,
                name,
                "Radiovoltaic Plate",
                rv.scatteringFast,
                rv.absorptionFast,
                0.0,
                rv.scatteringThermal,
                rv.absorptionThermal,
                rv.heatTransferCoeff,
                0.0,
                rv.getMaxEffectiveEU(),
                false,
                0L,
                "Indestructible",
                false,
                null,
                0L,
                extra,
                rv));
    }

    private static void addInsulator(ItemStack stack, String name, double insulation, double conduction, double fastAbs,
        double thermAbs, double fastScattering, double thermScattering, String extra) {
        if (stack == null && isTestMode()) stack = createTestPlaceholder(name);
        if (stack == null) return;
        ALL_COMPONENTS.add(
            new ComponentStatsData(
                stack,
                name,
                "Thermal Insulator",
                fastScattering,
                fastAbs,
                0.0,
                thermScattering,
                thermAbs,
                conduction,
                insulation,
                0L,
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
        ComponentData.CoolantItemData configured = ReactorDataRegistry.findCoolantItem(stack);
        if (configured != null) {
            heatCapacity = Math.round(ComponentCardValues.coolantCellCapacity(stack, configured));
        }

        ComponentStatsData data = new ComponentStatsData(
            stack,
            name,
            "Coolant Cell",
            0.45,
            0.02,
            0.05,
            0.45,
            0.05,
            conduction,
            0.0,
            0L,
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

    private static void addFluidCoolant(String fluidName, String name, double fastScattering, double fastAbs,
        double slowing, double thermScattering, double thermAbs, double conduction, boolean hasTransmutation,
        ItemStack product, long reqNeutrons, String extra) {
        Fluid fluid = null;
        fluid = FluidRegistry.getFluid(fluidName);

        ItemStack display = null;
        if (fluid != null) {
            if (ItemList.Display_Fluid.hasBeenSet()) {
                display = GTUtility.getFluidDisplayStack(new FluidStack(fluid, 1000), false);
            }

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
                fastScattering,
                fastAbs,
                slowing,
                thermScattering,
                thermAbs,
                conduction,
                0.0,
                0L,
                false,
                0L,
                hasTransmutation ? "Transmutes" : "Continuous",
                hasTransmutation,
                product,
                reqNeutrons,
                extra));
    }

    private static void addHeatVent(String ic2Name, String name, double conduction, String extra) {
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
                conduction,
                0.0,
                0L,
                false,
                0L,
                "Indestructible",
                false,
                null,
                0L,
                extra));
    }

    private static void addHeatExchanger(String ic2Name, String name, double conduction, String extra) {
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
                conduction,
                0.0,
                0L,
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
        Fluid fluid = FluidRegistry.getFluid(fluidName);
        if (fluid != null && ItemList.Display_Fluid.hasBeenSet()) {
            return GTUtility.getFluidDisplayStack(new FluidStack(fluid, amount), false);
        }

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

    public boolean matches(ItemStack target, ComponentStatsData data) {
        if (target == null || data == null) return false;
        if (matchesStack(target, data.stack)) return true;
        for (ItemStack alt : data.alternateStacks) {
            if (matchesStack(target, alt)) return true;
        }
        return false;
    }

    public boolean matchesStack(ItemStack target, ItemStack component) {
        if (target == null || component == null) return false;
        if (target.getItem() != component.getItem()) return false;
        if (!target.isItemStackDamageable() && target.getItemDamage() != component.getItemDamage()) {
            return false;
        }
        return true;
    }

    @Override
    public int getRecipeHeight(int recipe) {
        if (recipe >= 0 && recipe < arecipes.size()) {
            CachedRecipe cr = arecipes.get(recipe);
            if (cr instanceof CachedNeutronInteractionRecipe nr && nr.data.isRadiovoltaic) {
                return RV_PANEL_Y + 77;
            }
        }
        return DURABILITY_PANEL_Y + 67;
    }

    // =========================================================================
    // GUI Rendering
    // =========================================================================

    @Override
    public void drawBackground(int recipeIndex) {
        if (recipeIndex < 0 || recipeIndex >= arecipes.size()) return;
        CachedRecipe cached = this.arecipes.get(recipeIndex);

        if (cached instanceof CachedNeutronInteractionRecipe recipe) {
            // See Note [Resource packs restyle our GUIs]
            NEIPalette palette = NEIPalette.current();

            // Header catalyst slot box at (6, 2)
            palette.drawSlot(6, 2);

            // Card 1: Fast neutrons panel at y=33
            palette.drawPanel(6, 33, 154, 64);
            palette.drawSlot(74, 56);
            drawFastNeutronDiagram(recipe.data, 33);

            // Card 2: Thermal neutrons panel
            ComponentCardValues values = recipe.data.cardValues();
            palette.drawPanel(6, THERMAL_PANEL_Y, 154, 64);
            drawThermalNeutronDiagram(values, THERMAL_PANEL_Y);
            palette.drawSlot(74, THERMAL_SLOT_Y);

            // Card 3: Heat panel, its hot-to-cold diagram above its row of tiles
            palette.drawPanel(6, HEAT_PANEL_Y, 154, HEAT_PANEL_H);
            drawHeatDiagram(values, HEAT_PANEL_Y);
            palette.drawSlot(74, HEAT_SLOT_Y);
            drawHeatRow(values);

            // Card 4: Durability & byproducts panel OR Radiovoltaic curve panel
            if (recipe.data.isRadiovoltaic) {
                palette.drawPanel(RV_PANEL_X, RV_PANEL_Y, RV_PANEL_W, RV_PANEL_H);
                palette.drawPlotWell(RV_PLOT_X, RV_PLOT_Y, RV_PLOT_W, RV_PLOT_H);
            } else {
                palette.drawPanel(6, DURABILITY_PANEL_Y, 154, 64);
                int durSlotX = recipe.data.transmutationOutput != null ? 30 : 66;
                palette.drawSlot(durSlotX, DURABILITY_SLOT_Y);

                if (recipe.data.transmutationOutput != null) {
                    palette.drawSlot(100, DURABILITY_SLOT_Y);
                    GuiDraw.changeTexture(LONG_ARROW);
                    drawCustomTexturedModalRect(58, DURABILITY_SLOT_Y - 1, 0, 0, 40, 20, 40, 40);
                    long cycle = System.currentTimeMillis() % 3000L;
                    int fillW = (int) (40.0 * (cycle / 3000.0));
                    if (fillW > 0) {
                        drawCustomTexturedModalRect(58, DURABILITY_SLOT_Y - 1, 0, 20, fillW, 20, 40, 40);
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

            NEIPalette palette = NEIPalette.current();

            // Header summary (at top: category & description next to catalyst)
            GuiDraw.drawString("[" + d.category + "]", 28, 3, palette.category, false);
            if (d.extraInfo != null) {
                renderWrappedExtraInfo(d.extraInfo, 28, 13, 132);
            }

            // --- Card 1: Fast Neutron Panel (baseY = 33) ---
            GuiDraw.drawStringC(
                StatCollector.translateToLocal("gt.nei.neutron_interaction.fast"),
                83,
                36,
                palette.ink,
                false);

            // See Note [Fast neutrons are reflected or scattered]
            ComponentCardValues.Fast fast = d.cardValues().fast;

            // Reflected neutrons percentage (between the two blue arrows)
            GuiDraw.drawStringC(
                formatPercent(fast.reflected()),
                38,
                61,
                fast.reflected() > 0 ? palette.reflectedText : palette.ink,
                false);

            // Absorbed neutrons percentage (below red arrow)
            GuiDraw.drawStringC(
                formatPercent(fast.absorbed()),
                83,
                86,
                fast.absorbed() > 0 ? palette.absorbedText : palette.ink,
                false);

            // Overall scattered fast neutrons percentage (between green arrows and pie chart, vertically centered)
            GuiDraw.drawStringC(
                formatPercent(fast.scattered()),
                FAST_SCATTERING_LABEL_X,
                61,
                fast.scattered() > 0 ? palette.scatteredText : palette.ink,
                false);

            // Relative scattered fast neutrons conditional percentage (above blue arrow)
            double slowing = fast.moderated();
            double fastFrac = 1.0 - slowing;
            String fastScatteringStr = formatPercent(fastFrac);
            GuiDraw
                .drawStringC(fastScatteringStr, FAST_PIE_X, 37, fastFrac > 0 ? palette.fastText : palette.ink, false);

            // Relative scattered moderated neutrons conditional percentage (below purple arrow)
            String modScatteringStr = formatPercent(slowing);
            GuiDraw.drawStringC(modScatteringStr, FAST_PIE_X, 86, slowing > 0 ? palette.thermal : palette.ink, false);

            // --- Card 2: Thermal Neutron Panel ---
            ComponentCardValues values = d.cardValues();
            drawThermalNeutronText(values, THERMAL_PANEL_Y);

            // --- Card 3: Heat Panel ---
            drawHeatText(values, HEAT_PANEL_Y);

            // --- Card 4: Durability & Byproducts Panel OR Radiovoltaic Power & Heat Curve ---
            if (d.isRadiovoltaic) {
                drawRadiovoltaicCurve(recipe, recipeIndex);
            } else {
                GuiDraw.drawStringC(
                    StatCollector.translateToLocal("gt.nei.neutron_interaction.absorption"),
                    83,
                    DURABILITY_PANEL_Y + 3,
                    palette.ink,
                    false);

                if (d.transmutationOutput != null) {
                    String reqStr = formatNeutrons(d.transmutationNeutrons);
                    String transStr = reqStr + " Neutrons -> " + d.transmutationOutput.getDisplayName();
                    GuiDraw.drawStringC(transStr, 83, DURABILITY_PANEL_Y + 52, palette.byproductText, false);
                } else if (d.hasDurability) {
                    String durStr = d.durabilityLabel + ": " + formatNeutrons(d.durability);
                    GuiDraw.drawStringC(durStr, 83, DURABILITY_PANEL_Y + 52, palette.ink, false);
                } else {
                    GuiDraw.drawStringC(
                        d.durabilityLabel != null ? d.durabilityLabel : "Indestructible",
                        83,
                        DURABILITY_PANEL_Y + 52,
                        palette.indestructibleText,
                        false);
                }
            }
        }
    }

    /** The radiovoltaic chart's x axis runs over absorbed heat up to twice where steam catches up with the plate. */
    static double rvPlotMaxHeat(ComponentStatsData d) {
        return 2.0 * d.steamCatchUpHeatPerSecond();
    }

    public static boolean isMouseOverRVPlot(int x, int y) {
        return x >= RV_PLOT_X && x <= (RV_PLOT_X + RV_PLOT_W) && y >= RV_PLOT_Y && y <= (RV_PLOT_Y + RV_PLOT_H);
    }

    private static String compact(double v) {
        if (v >= 10_000.0) return Math.round(v / 1000.0) + "k";
        if (v >= 1000.0) return String.format(Locale.US, "%.1fk", v / 1000.0);
        return String.valueOf(Math.round(v));
    }

    /*-
    The radiovoltaic chart, against the heat rate H (HU/s) the plate's absorbed neutrons bring, from 0 to twice the
    heat at which plain steam makes the plate's maximum output (steamCatchUpHeatPerSecond):
      left axis    EU/t, up to twice the plate's maximum output.  Steam's dashed line is the diagonal, and it crosses
                   the plate's saturated power curve at the centre, whatever radiovoltaic_efficiency_multiplier is.
                   See Note [Radiovoltaic energy]
      right axis   HU/s on the same scale as H: the heat the plate keeps
    */
    private void drawRadiovoltaicCurve(CachedNeutronInteractionRecipe recipe, int recipeIndex) {
        ComponentStatsData d = recipe.data;
        double maxH = rvPlotMaxHeat(d);
        double maxEU = Math.max(1.0, d.steamPower(maxH));

        NEIPalette palette = NEIPalette.current();

        GuiDraw.drawStringC(
            StatCollector.translateToLocal("gt.nei.neutron_interaction.radiovoltaic_curve"),
            83,
            RV_PANEL_Y + 2,
            palette.ink,
            false);

        for (int p = 25; p < 100; p += 25) {
            int gy = RV_PLOT_Y + RV_PLOT_H - (int) Math.round((p / 100.0) * RV_PLOT_H);
            GuiDraw.drawRect(RV_PLOT_X, gy, RV_PLOT_W, 1, palette.grid);
            int gx = RV_PLOT_X + (int) Math.round((p / 100.0) * RV_PLOT_W);
            GuiDraw.drawRect(gx, RV_PLOT_Y, 1, RV_PLOT_H, palette.grid);
        }

        // Axis labels at half scale
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        GL11.glPushMatrix();
        GL11.glScalef(0.5f, 0.5f, 1.0f);
        font.drawString("EU/t", (RV_PLOT_X - 20) * 2, (RV_PLOT_Y - 6) * 2, palette.powerLabel);
        font.drawString("HU/s", (RV_PLOT_X + RV_PLOT_W + 2) * 2, (RV_PLOT_Y - 6) * 2, palette.damageCurve);
        for (int p = 0; p <= 100; p += 50) {
            int gy = RV_PLOT_Y + RV_PLOT_H - (int) Math.round((p / 100.0) * RV_PLOT_H);
            String pStr = compact((p / 100.0) * maxEU);
            font.drawString(pStr, (RV_PLOT_X - 2) * 2 - font.getStringWidth(pStr), (gy - 2) * 2, palette.powerLabel);
            font.drawString(
                compact((p / 100.0) * maxH),
                (RV_PLOT_X + RV_PLOT_W + 3) * 2,
                (gy - 2) * 2,
                palette.damageCurve);
        }
        for (int p = 0; p <= 100; p += 50) {
            String hStr = compact((p / 100.0) * maxH);
            int x = (RV_PLOT_X + (int) Math.round((p / 100.0) * RV_PLOT_W)) * 2 - font.getStringWidth(hStr) / 2;
            font.drawString(hStr, x, (RV_PLOT_Y + RV_PLOT_H + 2) * 2, palette.ink);
        }
        font.drawString("HU/s", (RV_PLOT_X + RV_PLOT_W + 10) * 2, (RV_PLOT_Y + RV_PLOT_H + 2) * 2, palette.ink);
        GL11.glPopMatrix();

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);

        // Plain steam: the diagonal, dashed
        GL11.glLineWidth(1.0f);
        NEIPalette.glColor(palette.steamRoute, 1.0f);
        GL11.glEnable(GL11.GL_LINE_STIPPLE);
        GL11.glLineStipple(2, (short) 0x3333);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(RV_PLOT_X, RV_PLOT_Y + RV_PLOT_H);
        GL11.glVertex2f(RV_PLOT_X + RV_PLOT_W, RV_PLOT_Y);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_STIPPLE);

        GL11.glLineWidth(2.0f);
        NEIPalette.glColor(palette.powerCurve, 1.0f);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int i = 0; i <= RV_PLOT_W; i++) {
            double h = (i / (double) RV_PLOT_W) * maxH;
            GL11.glVertex2f(RV_PLOT_X + i, plotY(d.calculatePower(h) / maxEU));
        }
        GL11.glEnd();

        NEIPalette.glColor(palette.damageCurve, 1.0f);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int i = 0; i <= RV_PLOT_W; i++) {
            double h = (i / (double) RV_PLOT_W) * maxH;
            GL11.glVertex2f(RV_PLOT_X + i, plotY(d.calculateHeat(h) / maxH));
        }
        GL11.glEnd();

        Point mouseInRecipe = null;
        if (Minecraft.getMinecraft().currentScreen instanceof GuiRecipe<?>guiRecipe) {
            mouseInRecipe = getMouseInRecipe(guiRecipe, recipeIndex);
        }
        if (mouseInRecipe != null && isMouseOverRVPlot(mouseInRecipe.x, mouseInRecipe.y)) {
            int mx = mouseInRecipe.x;
            double hAtMouse = ((mx - RV_PLOT_X) / (double) RV_PLOT_W) * maxH;
            float yp = plotY(d.calculatePower(hAtMouse) / maxEU);
            float yh = plotY(d.calculateHeat(hAtMouse) / maxH);

            GL11.glLineWidth(1.0f);
            NEIPalette.glColor(palette.cursor, ((palette.cursor >>> 24) & 0xFF) / 255.0f);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(mx, RV_PLOT_Y);
            GL11.glVertex2f(mx, RV_PLOT_Y + RV_PLOT_H);
            GL11.glEnd();

            NEIPalette.glColor(palette.powerCurve, 0.5f);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(RV_PLOT_X, yp);
            GL11.glVertex2f(mx, yp);
            GL11.glEnd();

            NEIPalette.glColor(palette.damageCurve, 0.5f);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(mx, yh);
            GL11.glVertex2f(RV_PLOT_X + RV_PLOT_W, yh);
            GL11.glEnd();

            NEIFuelStatsHandler.drawIndicatorDot(mx, yp, palette.powerCurve);
            NEIFuelStatsHandler.drawIndicatorDot(mx, yh, palette.damageCurve);
        }

        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(1.0f);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glPopMatrix();
    }

    /** The screen y of a value from 0 (bottom) to 1 (top) of the radiovoltaic plot, clamped to it. */
    private static float plotY(double fraction) {
        return (float) (RV_PLOT_Y + RV_PLOT_H - Math.max(0.0, Math.min(1.0, fraction)) * RV_PLOT_H);
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
            @SuppressWarnings("unchecked")
            List<String> lines = (List<String>) fr.listFormattedStringToWidth(text, wrapWidth);
            if (lines != null && !lines.isEmpty()) {
                return lines;
            }

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
            GuiDraw.drawString(line, x, y, NEIPalette.current().inkFaint, false);
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
        } catch (NoSuchFieldException t) {
            try {
                fieldGuiLeft = GuiContainer.class.getDeclaredField("field_147003_i");
                fieldGuiLeft.setAccessible(true);
            } catch (NoSuchFieldException swallowed) {
                Swallowed.note(swallowed);
            }
        }
        try {
            fieldGuiTop = GuiContainer.class.getDeclaredField("guiTop");
            fieldGuiTop.setAccessible(true);
        } catch (NoSuchFieldException t) {
            try {
                fieldGuiTop = GuiContainer.class.getDeclaredField("field_147009_r");
                fieldGuiTop.setAccessible(true);
            } catch (NoSuchFieldException swallowed) {
                Swallowed.note(swallowed);
            }
        }
    }

    private static int getGuiLeft(GuiRecipe<?> gui) {
        if (fieldGuiLeft != null) {
            try {
                return fieldGuiLeft.getInt(gui);
            } catch (IllegalAccessException swallowed) {
                Swallowed.note(swallowed);
            }
        }
        return (gui.width - 176) / 2;
    }

    private static int getGuiTop(GuiRecipe<?> gui) {
        if (fieldGuiTop != null) {
            try {
                return fieldGuiTop.getInt(gui);
            } catch (IllegalAccessException swallowed) {
                Swallowed.note(swallowed);
            }
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
                // 1. Fast reflection & incoming neutrons (covers the two blue arrows and % text: X in [18, 73], Y in
                // [45, 85])
                if (relX >= 18 && relX <= 73 && relY >= 45 && relY <= 85) {
                    currenttip.add(
                        EnumChatFormatting.GOLD
                            + StatCollector.translateToLocal("gt.nei.neutron_interaction.reflection"));
                    currenttip.add(
                        EnumChatFormatting.GRAY
                            + StatCollector.translateToLocal("gt.nei.neutron_interaction.reflection_fast.desc"));
                    // 2. Fast absorption (center bottom: arrow and % text at (83, 86))
                } else if (relX >= 68 && relX <= 98 && relY >= 75 && relY <= 96) {
                    currenttip.add(
                        EnumChatFormatting.RED
                            + StatCollector.translateToLocal("gt.nei.neutron_interaction.absorption_title"));
                    currenttip.add(
                        EnumChatFormatting.GRAY
                            + StatCollector.translateToLocal("gt.nei.neutron_interaction.absorption_fast.desc"));
                    // 3. Scattering arrows and overall probability label: X in [94, 129], Y in [49, 81]
                } else if (relX >= 94 && relX <= 129 && relY >= 49 && relY <= 81) {
                    currenttip.add(
                        EnumChatFormatting.GREEN
                            + StatCollector.translateToLocal("gt.nei.neutron_interaction.scattering"));
                    currenttip.add(
                        EnumChatFormatting.GRAY
                            + StatCollector.translateToLocal("gt.nei.neutron_interaction.scattering_fast.desc"));
                    // 4. Upper: Scattered fast arrow & percentage (X in [122, 159], Y in [35, 54])
                } else if (relX >= 122 && relX <= 159 && relY >= 35 && relY <= 54) {
                    currenttip.add(
                        EnumChatFormatting.BLUE
                            + StatCollector.translateToLocal("gt.nei.neutron_interaction.fast_fraction"));
                    currenttip.add(
                        EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(
                            "gt.nei.neutron_interaction.fast_fraction.desc",
                            formatPercent(1.0 - d.cardValues().fast.moderated())));
                    // 5. Lower: Scattered moderated arrow & percentage (X in [122, 159], Y in [76, 96])
                } else if (relX >= 122 && relX <= 159 && relY >= 76 && relY <= 96) {
                    currenttip.add(
                        EnumChatFormatting.LIGHT_PURPLE
                            + StatCollector.translateToLocal("gt.nei.neutron_interaction.thermal_fraction"));
                    currenttip.add(
                        EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(
                            "gt.nei.neutron_interaction.thermal_fraction.desc",
                            formatPercent(d.cardValues().fast.moderated())));
                    // 6. Center: Pie chart (X in [130, 158], Y in [55, 75])
                } else if (relX >= 130 && relX <= 158 && relY >= 55 && relY <= 75) {
                    currenttip.add(
                        EnumChatFormatting.BLUE
                            + StatCollector.translateToLocal("gt.nei.neutron_interaction.fast_fraction"));
                    currenttip.add(
                        EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(
                            "gt.nei.neutron_interaction.fast_fraction.desc",
                            formatPercent(1.0 - d.cardValues().fast.moderated())));
                    currenttip.add(
                        EnumChatFormatting.LIGHT_PURPLE
                            + StatCollector.translateToLocal("gt.nei.neutron_interaction.thermal_fraction"));
                    currenttip.add(
                        EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(
                            "gt.nei.neutron_interaction.thermal_fraction.desc",
                            formatPercent(d.cardValues().fast.moderated())));
                }

                // Card 2: Thermal Neutron Panel
            } else if (relY >= THERMAL_PANEL_Y && relY < THERMAL_PANEL_Y + 64) {
                addThermalTooltip(d.cardValues(), relX, relY - THERMAL_PANEL_Y, currenttip);

                // Card 3: Heat Panel
            } else if (relY >= HEAT_PANEL_Y && relY < HEAT_PANEL_Y + HEAT_PANEL_H) {
                addHeatTooltip(d.cardValues(), relX, relY - HEAT_PANEL_Y, currenttip);

                // Card 4: Durability & Byproducts Panel OR Radiovoltaic Power & Heat Curve
            } else if (relY >= RV_PANEL_Y && relY <= RV_PANEL_Y + RV_PANEL_H) {
                if (d.isRadiovoltaic) {
                    if (isMouseOverRVPlot(relX, relY)) {
                        long maxEU = Math.max(1L, d.maxDirectEU);
                        double fullH = d.fullOutputHeatPerSecond();
                        double h = ((relX - RV_PLOT_X) / (double) RV_PLOT_W) * rvPlotMaxHeat(d);
                        double power = d.calculatePower(h);
                        double kept = d.calculateHeat(h);
                        double fast = h / NuclearSimulationEngine.HU_FOR_FAST_NEUTRON;

                        currenttip.add(
                            EnumChatFormatting.WHITE + ""
                                + EnumChatFormatting.BOLD
                                + String.format(Locale.US, "%s HU/s absorbed", formatNumber(h)));
                        currenttip.add(
                            EnumChatFormatting.GRAY + String.format(
                                Locale.US,
                                "%s fast or %s thermal neutrons/s",
                                formatNumber(fast),
                                formatNumber(2.0 * fast)));
                        String satNote = (h >= fullH) ? EnumChatFormatting.GOLD + " (Saturated)"
                            : (h >= fullH * 0.5 ? EnumChatFormatting.YELLOW + " (Knee)" : "");
                        currenttip.add(
                            EnumChatFormatting.AQUA + "Power output: "
                                + formatNumber(power)
                                + " EU/t ("
                                + formatPercent(power / maxEU)
                                + ")"
                                + satNote);
                        currenttip
                            .add(EnumChatFormatting.GRAY + "Plain steam: " + formatNumber(d.steamPower(h)) + " EU/t");
                        String heatNote = (kept <= 0.0) ? EnumChatFormatting.GRAY + " (None)" : "";
                        currenttip
                            .add(EnumChatFormatting.RED + "Heat kept: " + formatNumber(kept) + " HU/s" + heatNote);
                    } else if (relX >= 6 && relX <= 160 && relY <= RV_PANEL_Y + 11) {
                        currenttip.add(
                            EnumChatFormatting.AQUA
                                + StatCollector.translateToLocal("gt.nei.neutron_interaction.radiovoltaic_curve"));
                        currenttip.add(
                            EnumChatFormatting.GRAY
                                + StatCollector.translateToLocal("gt.nei.neutron_interaction.radiovoltaic_curve.desc"));
                    }
                } else {
                    if (relX >= 25 && relX <= 140
                        && relY >= DURABILITY_PANEL_Y + 20
                        && relY <= DURABILITY_PANEL_Y + 62) {
                        currenttip.add(
                            EnumChatFormatting.WHITE
                                + StatCollector.translateToLocal("gt.nei.neutron_interaction.absorption_capacity"));
                        currenttip.add(
                            EnumChatFormatting.GRAY + StatCollector
                                .translateToLocal("gt.nei.neutron_interaction.absorption_capacity.desc"));
                    }
                }
            }
        }
        return currenttip;
    }

    private static void addTooltip(List<String> tip, EnumChatFormatting color, String titleKey, String... lines) {
        tip.add(color + StatCollector.translateToLocal(titleKey));
        for (String line : lines) tip.add(EnumChatFormatting.GRAY + line);
    }

    private static String tr(String key, Object... args) {
        return StatCollector.translateToLocalFormatted("gt.nei.neutron_interaction." + key, args);
    }

    /** @param y relative to the panel's top */
    private static void addThermalTooltip(ComponentCardValues v, int x, int y, List<String> tip) {
        if (x >= 129 && y >= 2) {
            addTooltip(
                tip,
                EnumChatFormatting.LIGHT_PURPLE,
                "gt.nei.neutron_interaction.thermal_spread",
                tr("thermal_spread.desc", formatOutcome(v.perSide()), formatOutcome(v.perCorner())),
                tr("thermal_spread.edge"));
        } else if (x >= 93 && x < 129 && y >= 12 && y <= 42) {
            addTooltip(
                tip,
                EnumChatFormatting.GREEN,
                "gt.nei.neutron_interaction.thermal_scattered",
                tr("thermal_scattered.desc", formatOutcome(v.scattered)),
                tr("thermal_scattered.stay"));
        } else if (x >= 74 && x < 129 && y > 42) {
            addTooltip(
                tip,
                EnumChatFormatting.RED,
                "gt.nei.neutron_interaction.thermal_absorbed",
                tr("thermal_absorbed.desc", formatOutcome(v.absorbed)));
        } else if (x < 74 && y > 42) {
            addTooltip(
                tip,
                EnumChatFormatting.GRAY,
                "gt.nei.neutron_interaction.thermal_lost",
                tr("thermal_lost.desc", formatOutcome(v.lost)));
        } else if (x < 74 && y >= 22) {
            addTooltip(
                tip,
                EnumChatFormatting.LIGHT_PURPLE,
                "gt.nei.neutron_interaction.thermal_arriving",
                tr("thermal_arriving.desc"));
        }
    }

    /** @param y relative to the panel's top */
    private static void addHeatTooltip(ComponentCardValues v, int x, int y, List<String> tip) {
        int rowY = HEAT_ROW_Y - HEAT_PANEL_Y;
        if (x >= 74 && x < 92 && y >= rowY && y < rowY + 18) return; // The slot's tooltip is the item's
        if (y >= rowY - 14) {
            addTooltip(
                tip,
                EnumChatFormatting.GOLD,
                "gt.nei.neutron_interaction.heat_row",
                tr("heat_row.desc", formatNumber(ComponentCardValues.NEIGHBOUR_K)),
                v.inHatch ? tr("heat_row.hatch") : tr("heat_row.tile"));
        } else if (y >= 53 || (y >= 42 && x >= 76 && x <= 90)) {
            addTooltip(
                tip,
                EnumChatFormatting.AQUA,
                "gt.nei.neutron_interaction.heat_action",
                heatActionDescription(v));
        } else if (y < 13 && x < 73) {
            String[] lines = v.inHatch ? new String[] { tr("capacity.desc"), tr("capacity.hatch") }
                : new String[] { tr("capacity.desc") };
            addTooltip(tip, EnumChatFormatting.GOLD, "gt.nei.neutron_interaction.capacity", lines);
        } else if (y < 13 && x > 93) {
            addTooltip(
                tip,
                EnumChatFormatting.GOLD,
                "gt.nei.neutron_interaction.conduction",
                tr("conduction.desc", formatNumber(v.k)));
        } else if (y >= 13 && y < 53 && (x < 74 || x >= 92)) {
            addTooltip(
                tip,
                EnumChatFormatting.GOLD,
                "gt.nei.neutron_interaction.conductance",
                v.dampening >= 1.0 ? tr("conductance.none")
                    : tr(
                        "conductance.desc",
                        formatCardNumber(v.conductance()),
                        formatNumber(ComponentCardValues.NEIGHBOUR_K)));
        }
    }

    private static String heatActionDescription(ComponentCardValues v) {
        return switch (v.action) {
            case BOILS -> tr("heat_action.boils.desc");
            case COOLS_TO_AMBIENT -> tr("heat_action.cools.desc");
            case VENTS -> tr("heat_action.vents.desc");
            case EXCHANGES -> tr("heat_action.exchanges.desc");
            case STORES -> tr("heat_action.stores.desc");
            case BLOCKS -> tr("heat_action.blocks.desc");
            case BURNS -> tr("heat_action.burns.desc");
            case CONDUCTS_ONLY -> tr("heat_action.conducts.desc");
        };
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
        ComponentStatsData data = (rec instanceof CachedNeutronInteractionRecipe r1) ? r1.data : null;
        if (data == null) return currenttip;

        if (matches(stack, data)) {
            currenttip.add(
                EnumChatFormatting.GOLD
                    + StatCollector.translateToLocalFormatted("gt.nei.neutron_interaction.role", data.category));
            currenttip.add(
                EnumChatFormatting.YELLOW + StatCollector.translateToLocalFormatted(
                    "gt.nei.neutron_interaction.fast_stats",
                    formatPercent(data.cardValues().fast.reflected()),
                    formatPercent(data.cardValues().fast.scattered()),
                    formatPercent(data.cardValues().fast.absorbed()),
                    formatPercent(data.cardValues().fast.moderated())));
            currenttip.add(
                EnumChatFormatting.AQUA + tr(
                    "thermal_stats",
                    formatOutcome(data.cardValues().absorbed),
                    formatOutcome(data.cardValues().scattered),
                    formatOutcome(data.cardValues().lost)));
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

    /**
     * The fast card's pie: the start and end angles (counterclockwise from the right) of the slice for scattered fast
     * neutrons, centred straight up on its arrow; the moderated slice runs from its end round to its start, so that it
     * is centred straight down on its own.
     */
    public static double[] fastPieAngles(double fastFrac) {
        double half = Math.max(0.0, Math.min(1.0, fastFrac)) * Math.PI;
        return new double[] { Math.PI / 2 - half, Math.PI / 2 + half };
    }

    public static void drawFastNeutronDiagram(ComponentStatsData d) {
        drawFastNeutronDiagram(d, 33);
    }

    public static void drawFastNeutronDiagram(ComponentStatsData d, int baseY) {
        double dy = baseY - 2.0;
        NEIPalette palette = NEIPalette.current();

        // Incoming fast neutrons arrow (blue, down-right towards center tile)
        drawArrow(24.0, 17.0 + dy, 70.0, 30.0 + dy, 2.0, 5.0, 6.0, palette.fast);

        // Reflected fast neutrons arrow (blue, down-left away from center tile)
        drawArrow(70.0, 38.0 + dy, 24.0, 51.0 + dy, 2.0, 5.0, 6.0, palette.fast);

        // Absorbed neutrons arrow (red, straight down under center tile)
        drawArrow(83.0, 44.0 + dy, 83.0, 52.0 + dy, 2.0, 4.0, 5.5, palette.absorbed);

        // Three scattered neutrons arrows (green, pointing right away from center tile)
        // Middle: horizontal
        drawArrow(96.0, 34.0 + dy, 107.0, 34.0 + dy, 1.2, 3.5, 4.5, palette.scattered);
        // Upper: 45 degrees up-right
        drawArrow(96.0, 32.0 + dy, 106.0, 22.0 + dy, 1.2, 3.5, 4.5, palette.scattered);
        // Lower: 45 degrees down-right
        drawArrow(96.0, 36.0 + dy, 106.0, 46.0 + dy, 1.2, 3.5, 4.5, palette.scattered);

        // Dynamic pie chart at (FAST_PIE_X, 34 + dy), radius 9.0
        double slowing = d.cardValues().fast.moderated();
        double fastFrac = 1.0 - slowing;
        if (slowing <= 0.001) {
            drawPieSlice(FAST_PIE_X, 34.0 + dy, 9.0, 0.0, 2.0 * Math.PI, palette.fast);
        } else if (fastFrac <= 0.001) {
            drawPieSlice(FAST_PIE_X, 34.0 + dy, 9.0, 0.0, 2.0 * Math.PI, palette.thermal);
        } else {
            double[] a = fastPieAngles(fastFrac);
            drawPieSlice(FAST_PIE_X, 34.0 + dy, 9.0, a[0], a[1], palette.fast);
            drawPieSlice(FAST_PIE_X, 34.0 + dy, 9.0, a[1], a[0] + 2.0 * Math.PI, palette.thermal);
        }

        // Scattered fast neutrons arrow (blue, straight up above pie chart)
        drawArrow(FAST_PIE_X, 24.0 + dy, FAST_PIE_X, 16.0 + dy, 2.0, 4.0, 5.5, palette.fast);

        // Scattered moderated neutrons arrow (purple, straight down below pie chart)
        drawArrow(FAST_PIE_X, 44.0 + dy, FAST_PIE_X, 52.0 + dy, 2.0, 4.0, 5.5, palette.thermal);

    }

    /**
     * Formats a share of neutrons for the thermal card. Shares are computed values such as 84.15%, so unlike
     * {@link #formatPercent} this rounds them to fit the panel.
     */
    public static String formatOutcome(double frac) {
        if (frac <= 0.0) return "0%";
        if (frac >= 0.0995) return Math.round(frac * 100.0) + "%";
        if (frac < 0.001) return "<0.1%";
        return formatPercentFromPercent(Math.round(frac * 1000.0) / 10.0);
    }

    /**
     * Formats a number for the heat card: up to 2 decimals below 100, whole below 10000, and shortened from there on.
     */
    public static String formatCardNumber(double val) {
        if (val >= 1_000_000.0) return formatNumber(Math.round(val / 100_000.0) / 10.0) + "M";
        if (val >= 10_000.0) return formatNumber(Math.round(val / 100.0) / 10.0) + "k";
        return val >= 100.0 ? String.valueOf(Math.round(val)) : formatNumber(val);
    }

    /** The width of the band that stands for all arriving thermal neutrons. */
    private static final double THERMAL_BAND = 12.0;

    private static double thermalWidth(double share) {
        return share > 0.0 ? Math.max(1.0, THERMAL_BAND * share) : 0.0;
    }

    // See Note [Thermal and heat cards]
    public static void drawThermalNeutronDiagram(ComponentCardValues v, int baseY) {
        double midY = baseY + 32.0;
        NEIPalette palette = NEIPalette.current();

        drawArrow(12.0, midY, 73.0, midY, THERMAL_BAND, 7.0, THERMAL_BAND + 6.0, palette.thermalFaint);

        double absorbed = thermalWidth(v.absorbed);
        if (absorbed > 0.0) {
            drawArrow(83.0, baseY + 42.0, 83.0, baseY + 55.0, absorbed, 4.0, absorbed + 5.0, palette.absorbed);
        }
        double lost = thermalWidth(v.lost);
        if (lost > 0.0) {
            drawArrow(72.0, baseY + 45.0, 46.0, baseY + 55.0, lost, 4.0, lost + 5.0, palette.lost);
        }
        double scattered = thermalWidth(v.scattered);
        if (scattered > 0.0) {
            drawArrow(93.0, midY, 124.0, midY, scattered, 5.0, scattered + 5.0, palette.scattered);
        }

        // The spread: the tile in the middle, its neighbours by what each receives
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                boolean middle = i == 1 && j == 1;
                int color = middle ? palette.spreadMiddle : spreadColor(palette, v.scattered, i != 1 && j != 1);
                GuiDraw.drawRect(131 + i * 8, baseY + 21 + j * 8, 7, 7, color);
            }
        }
        drawArrow(142.5, baseY + 20.0, 142.5, baseY + 13.0, 2.0, 4.0, 5.5, palette.thermal);
        drawArrow(150.5, baseY + 44.0, 150.5, baseY + 51.0, 1.2, 3.5, 4.5, palette.thermalFainter);
    }

    private static int spreadColor(NEIPalette palette, double scattered, boolean corner) {
        if (scattered <= 0.0) return palette.spreadEmpty;
        double strength = (corner ? 0.32 : 0.62) * (0.35 + 0.65 * Math.min(1.0, scattered * 6.0));
        return NEIPalette.mix(palette.panel, palette.thermal, strength + (corner ? 0.05 : 0.2));
    }

    private static void drawThermalNeutronText(ComponentCardValues v, int baseY) {
        NEIPalette palette = NEIPalette.current();
        GuiDraw.drawStringC(
            StatCollector.translateToLocal("gt.nei.neutron_interaction.thermal"),
            83,
            baseY + 3,
            palette.ink,
            false);
        GuiDraw.drawStringC(formatOutcome(v.lost), 32, baseY + 52, palette.inkDim, false);
        GuiDraw.drawStringC(
            formatOutcome(v.absorbed),
            104,
            baseY + 51,
            v.absorbed > 0.0 ? palette.absorbedText : palette.ink,
            false);
        GuiDraw.drawStringC(
            formatOutcome(v.scattered),
            108,
            baseY + 15,
            v.scattered > 0.0 ? palette.scatteredText : palette.ink,
            false);
        boolean spreads = v.scattered > 0.0;
        GuiDraw.drawStringC(formatOutcome(v.perSide()), 142, baseY + 4, spreads ? palette.thermal : palette.ink, false);
        GuiDraw.drawStringR(
            formatOutcome(v.perCorner()),
            158,
            baseY + 53,
            spreads ? palette.thermalCornerText : palette.ink,
            false);
    }

    /** Arrow width for a k: 0.005 draws 1 px wide and 0.9 draws 8 px wide, as k spans orders of magnitude. */
    public static double conductionWidth(double k) {
        return 1.0 + 7.0 * Math.log(Math.max(k, 0.005) / 0.005) / Math.log(0.9 / 0.005);
    }

    private static void drawShadedTile(NEIPalette palette, int x, int y, int color) {
        GuiDraw.drawRect(x, y, 18, 18, color);
        GuiDraw.drawRect(x, y, 18, 1, palette.tileHighlight);
        GuiDraw.drawRect(x, y + 17, 18, 1, palette.tileShadow);
    }

    // See Note [Thermal and heat cards]
    public static void drawHeatDiagram(ComponentCardValues v, int baseY) {
        double midY = baseY + 32.0;
        boolean walled = v.dampening > 0.0;
        NEIPalette palette = NEIPalette.current();

        drawShadedTile(palette, 12, baseY + 23, palette.hotTile);
        drawShadedTile(palette, 136, baseY + 23, palette.coldTile);

        if (v.dampening < 1.0) {
            double width = Math.max(1.0, conductionWidth(v.k) * (1.0 - v.dampening));
            drawArrow(31.0, midY, walled ? 68.0 : 72.0, midY, width, 5.0, width + 5.0, palette.heatArrow);
            drawArrow(walled ? 98.0 : 94.0, midY, 135.0, midY, width, 5.0, width + 5.0, palette.heatArrow);
        }
        if (walled) {
            for (int x : new int[] { 69, 94 }) {
                GuiDraw.drawRect(x, baseY + 22, 3, 21, palette.ink);
                for (int y = baseY + 23; y < baseY + 42; y += 4) GuiDraw.drawRect(x + 1, y, 1, 2, palette.panel);
            }
        }
        if (v.action == ComponentCardValues.HeatAction.EXCHANGES) {
            for (int[] span : new int[][] { { 34, 70 }, { 96, 132 } }) {
                drawArrow(span[0], baseY + 39.0, span[1], baseY + 39.0, 1.2, 3.5, 4.5, palette.removal);
                drawArrow(span[1], baseY + 43.0, span[0], baseY + 43.0, 1.2, 3.5, 4.5, palette.removal);
            }
        }
        if (v.removesHeat()) {
            drawArrow(83.0, baseY + 42.0, 83.0, baseY + 51.0, 2.0, 4.0, 5.5, palette.removal);
        }
    }

    /** The row's current frame: it runs through in 6 s of real time, then holds for 2 s. */
    private static int heatRowFrame(float[][] frames) {
        double seconds = (System.currentTimeMillis() % 8000L) / 1000.0;
        return (int) Math.round((frames.length - 1) * Math.min(1.0, seconds / 6.0));
    }

    private static int rowTemperatureColor(double temperature) {
        return ColorScales.temperature(temperature, HeatRowSimulation.HOT_END) | 0xFF000000;
    }

    public static void drawHeatRow(ComponentCardValues v) {
        NEIPalette palette = NEIPalette.current();
        GuiDraw.drawRect(8, HEAT_ROW_Y - 14, 150, 1, palette.divider);
        float[][] frames = v.heatRow();
        float[] temperatures = frames[heatRowFrame(frames)];
        for (int i = 0; i < HEAT_ROW_X.length; i++) {
            if (i == HeatRowSimulation.MIDDLE) continue;
            drawShadedTile(palette, HEAT_ROW_X[i], HEAT_ROW_Y, rowTemperatureColor(temperatures[i]));
        }
        palette.drawSlot(HEAT_ROW_X[HeatRowSimulation.MIDDLE], HEAT_ROW_Y);
        GuiDraw.drawRect(
            HEAT_ROW_X[HeatRowSimulation.MIDDLE] + 1,
            HEAT_ROW_Y + 18,
            16,
            3,
            rowTemperatureColor(temperatures[HeatRowSimulation.MIDDLE]));
    }

    /** Draws text from its left edge at a scale, keeping it vertically centred on its line. */
    private static void drawScaledText(String text, double left, int y, int color, double scale) {
        if (scale >= 1.0) {
            GuiDraw.drawString(text, (int) Math.round(left), y, color, false);
            return;
        }
        GL11.glPushMatrix();
        GL11.glTranslated(left, y + (1.0 - scale) * 4.0, 0.0);
        GL11.glScaled(scale, scale, 1.0);
        GuiDraw.drawString(text, 0, 0, color, false);
        GL11.glPopMatrix();
    }

    private static double fitScale(String text, int maxWidth) {
        int width = GuiDraw.getStringWidth(text);
        return width > maxWidth ? maxWidth / (double) width : 1.0;
    }

    private static void drawTextCentredFit(String text, int centre, int y, int color, int maxWidth) {
        double scale = fitScale(text, maxWidth);
        drawScaledText(text, centre - GuiDraw.getStringWidth(text) * scale / 2.0, y, color, scale);
    }

    private static String capacityText(ComponentCardValues v) {
        String range = v.minCapacity == v.maxCapacity ? formatNumber(v.minCapacity)
            : formatNumber(v.minCapacity) + "-" + Math.round(v.maxCapacity / 1000.0) + "k";
        return tr("capacity.value", range);
    }

    /** One line on what the component does about heat, other than conduct it. */
    public static String heatActionText(ComponentCardValues v) {
        return switch (v.action) {
            case BOILS -> tr("heat_action.boils", formatNumber(v.actionRate), formatNumber(v.actionHeat));
            case COOLS_TO_AMBIENT -> tr("heat_action.cools", formatNumber(v.actionHeat));
            // Per-tick heat caps, shown per second. See Note [Neutron and heat rates are per second]
            case VENTS -> tr(
                "heat_action.vents",
                formatCardNumber(v.actionHeat * 20.0),
                formatCardNumber(v.actionRate));
            case EXCHANGES -> tr(
                "heat_action.exchanges",
                formatCardNumber(v.actionHeat * 20.0),
                formatCardNumber(v.actionRate));
            case STORES -> tr("heat_action.stores", formatCardNumber(v.actionHeat), formatCardNumber(v.actionRate));
            case BLOCKS -> tr("heat_action.blocks", formatPercent(v.dampening));
            case BURNS -> tr("heat_action.burns", formatNumber(v.actionRate));
            case CONDUCTS_ONLY -> tr("heat_action.conducts");
        };
    }

    private static int heatActionColor(NEIPalette palette, ComponentCardValues v) {
        return switch (v.action) {
            case BLOCKS -> palette.blocksText;
            case BURNS -> palette.burnsText;
            case CONDUCTS_ONLY -> palette.inkDim;
            default -> palette.removal;
        };
    }

    private static void drawHeatText(ComponentCardValues v, int baseY) {
        NEIPalette palette = NEIPalette.current();
        GuiDraw.drawStringC(
            StatCollector.translateToLocal("gt.nei.neutron_interaction.capture"),
            83,
            baseY + 3,
            palette.ink,
            false);
        String capacity = capacityText(v);
        drawScaledText(capacity, 10, baseY + 3, palette.ink, Math.min(0.75, fitScale(capacity, 60)));
        GuiDraw.drawStringR(tr("k.value", formatNumber(v.k)), 156, baseY + 3, palette.ink, false);

        GuiDraw.drawStringC("+", 21, baseY + 28, palette.tileSign, false);
        GuiDraw.drawStringC("-", 145, baseY + 28, palette.tileSign, false);
        if (v.dampening < 1.0) {
            drawTextCentredFit(
                tr("conductance.value", formatCardNumber(v.conductance())),
                50,
                baseY + 14,
                palette.conductanceText,
                56);
        }
        drawTextCentredFit(heatActionText(v), 83, baseY + 54, heatActionColor(palette, v), 150);

        // The row of tiles
        float[][] frames = v.heatRow();
        int frame = heatRowFrame(frames);
        int second = frame / HeatRowSimulation.FRAMES_PER_SECOND;
        GuiDraw.drawStringC(tr("heat_row.title", second), 83, HEAT_ROW_Y - 11, palette.ink, false);
        float[] temperatures = frames[frame];
        for (int i = 0; i < HEAT_ROW_X.length; i++) {
            String degrees = String.valueOf(Math.round(temperatures[i]));
            double left = HEAT_ROW_X[i] + 9 - GuiDraw.getStringWidth(degrees) * 0.25;
            int color = i == HeatRowSimulation.MIDDLE ? palette.inkStrong : palette.ink;
            drawScaledText(degrees, left, HEAT_ROW_Y + 22, color, 0.5);
        }
    }
}
