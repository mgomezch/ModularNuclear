package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.item.ModItems;
import com.gtnewhorizons.modularnuclear.common.metatileentity.ModMetaTileEntities;
import com.gtnewhorizons.modularnuclear.common.nei.NEIFuelStatsHandler;
import com.gtnewhorizons.modularnuclear.common.nei.NEINeutronInteractionHandler;
import com.gtnewhorizons.modularnuclear.common.nei.NEINeutronInteractionHandler.CachedComponentThermalRecipe;
import com.gtnewhorizons.modularnuclear.common.nei.NEINeutronInteractionHandler.CachedNeutronInteractionRecipe;
import com.gtnewhorizons.modularnuclear.common.nei.NEINeutronInteractionHandler.ComponentStatsData;

import codechicken.nei.recipe.TemplateRecipeHandler.CachedRecipe;

public class NEINeutronInteractionHandlerTest {

    @BeforeAll
    public static void setUp() {
        TestEnvironmentHelper.init();

        try {
            if (ModItems.emptyCoreTile == null) {
                ModItems.emptyCoreTile = new com.gtnewhorizons.modularnuclear.common.item.ItemEmptyCoreTile();
            }
            if (ModItems.radiovoltaicPlate == null) {
                ModItems.radiovoltaicPlate = new com.gtnewhorizons.modularnuclear.common.item.ItemRadiovoltaicPlate(
                    "modularnuclear.radiovoltaic.plate");
            }
            if (ModItems.radiovoltaicPlateHV == null) {
                ModItems.radiovoltaicPlateHV = new ItemStack(ModItems.radiovoltaicPlate, 1, 0);
            }
            if (ModItems.radiovoltaicPlateEV == null) {
                ModItems.radiovoltaicPlateEV = new ItemStack(ModItems.radiovoltaicPlate, 1, 1);
            }
        } catch (Throwable ignored) {}

        if (ModMetaTileEntities.reactor == null) {
            ModMetaTileEntities.reactor = new ItemStack(Items.diamond, 1, 0);
        }

        NEINeutronInteractionHandler.sTestMode = true;
        NEINeutronInteractionHandler.resetData();
    }

    @Test
    public void testHandlerIdAndOverlay() {
        NEINeutronInteractionHandler handler = new NEINeutronInteractionHandler();
        assertEquals("gt.nei.neutron_interaction", handler.getOverlayIdentifier());
        assertNotNull(handler.getRecipeName());
        assertEquals("gregtech:textures/gui/nei/neutron_interaction_atlas.png", handler.getGuiTexture());
    }

    @Test
    public void testFullComponentCoverageAcrossAllCategories() {
        NEINeutronInteractionHandler handler = new NEINeutronInteractionHandler();
        handler.loadCraftingRecipes(handler.getOverlayIdentifier());

        assertFalse(handler.arecipes.isEmpty(), "Handler recipes should not be empty");

        Set<String> categoriesFound = new HashSet<>();
        boolean foundReflector = false;
        boolean foundModerator = false;
        boolean foundRadiovoltaic = false;
        boolean foundInsulator = false;
        boolean foundCoolantCell = false;
        boolean foundFluidCoolant = false;
        boolean foundHeatVent = false;
        boolean foundHeatExchanger = false;
        boolean foundControlRod = false;
        boolean foundEmptyTile = false;

        for (CachedRecipe cr : handler.arecipes) {
            if (cr instanceof CachedNeutronInteractionRecipe r1) {
                ComponentStatsData d = r1.data;
                categoriesFound.add(d.category);

                switch (d.category) {
                    case "Reflector" -> {
                        foundReflector = true;
                        assertTrue(d.isReflector, "Reflector must have isReflector flag set");
                        assertTrue(d.reflectionFast > 0.0, "Reflector must specify fast reflection");
                        assertTrue(d.reflectionThermal > 0.0, "Reflector must specify thermal reflection");
                    }
                    case "Moderator" -> {
                        foundModerator = true;
                        assertTrue(d.slowingProbability > 0.40, "Moderators must have high slowing probability");
                        assertTrue(d.hasDurability, "Moderators must specify wear durability");
                        assertTrue(d.maxTemperature >= 2200.0, "Moderators must specify max operating temperature");
                    }
                    case "Radiovoltaic Plate" -> {
                        foundRadiovoltaic = true;
                        assertEquals(1.0, d.fastAbsorption, 1e-6);
                        assertEquals(1.0, d.thermalAbsorption, 1e-6);
                        assertTrue(d.producesDirectEU, "Radiovoltaic plates must produce direct EU");
                        assertTrue(d.maxDirectEU >= 1024L, "Radiovoltaic plates must specify max EU/t");
                    }
                    case "Thermal Insulator" -> {
                        foundInsulator = true;
                        assertTrue(d.thermalInsulationFactor > 0.0, "Insulators must specify thermal barrier fraction");
                        assertTrue(d.heatTransferCoeff <= 0.05, "Insulators must have low heat transfer coefficient");
                    }
                    case "Coolant Cell" -> {
                        foundCoolantCell = true;
                        assertTrue(d.hasDurability, "Coolant cells must specify heat capacity");
                        assertTrue(d.durability >= 250_000L, "Coolant cells must have configured heat capacity in HU");
                    }
                    case "Fluid Coolant" -> {
                        foundFluidCoolant = true;
                        assertTrue(d.boilingPoint > 0.0, "Fluid coolants must specify boiling point");
                        assertTrue(d.specificHeat > 0.0, "Fluid coolants must specify specific heat");
                    }
                    case "Heat Vent" -> {
                        foundHeatVent = true;
                        assertTrue(d.producesDirectEU, "Heat vents must produce direct EU while active");
                        assertTrue(
                            d.selfCoolingRate > 0.0 || d.componentCoolingRate > 0.0,
                            "Heat vents must specify cooling rate");
                    }
                    case "Heat Exchanger" -> {
                        foundHeatExchanger = true;
                        assertTrue(d.producesDirectEU, "Heat exchangers must produce direct EU while active");
                        assertTrue(d.heatTransferRate > 0.0, "Heat exchangers must specify heat transfer rate");
                    }
                    case "Control Rod" -> {
                        foundControlRod = true;
                        assertTrue(d.thermalAbsorption >= 0.50, "Control rods must have high thermal absorption");
                        assertTrue(d.hasDurability, "Control rods must specify burnout capacity");
                        assertEquals(
                            100_000_000L,
                            d.durability,
                            "Control rods must have standard 100M burnout capacity");
                    }
                    case "Empty Core Tile" -> {
                        foundEmptyTile = true;
                        assertEquals(0.0, d.fastScattering, 1e-6);
                        assertEquals(0.0, d.fastAbsorption, 1e-6);
                        assertEquals(0.0, d.thermalScattering, 1e-6);
                        assertEquals(0.0, d.thermalAbsorption, 1e-6);
                        assertEquals(0.02, d.heatTransferCoeff, 1e-6);
                    }
                }
            }
        }

        assertTrue(foundReflector, "Reflectors must be present in NEI handler");
        assertTrue(foundModerator, "Moderators must be present in NEI handler");
        assertTrue(foundRadiovoltaic, "Radiovoltaic plates must be present in NEI handler");
        assertTrue(foundInsulator, "Insulators must be present in NEI handler");
        assertTrue(foundCoolantCell, "Coolant cells must be present in NEI handler");
        assertTrue(foundFluidCoolant, "Fluid coolants must be present in NEI handler");
        assertTrue(foundHeatVent, "Heat vents must be present in NEI handler");
        assertTrue(foundHeatExchanger, "Heat exchangers must be present in NEI handler");
        assertTrue(foundControlRod, "Control rods must be present in NEI handler");
        assertTrue(foundEmptyTile, "Empty core tile must be present in NEI handler");
    }

    @Test
    public void testTwoPagesPerComponent() {
        NEINeutronInteractionHandler handler = new NEINeutronInteractionHandler();
        handler.loadCraftingRecipes(handler.getOverlayIdentifier());

        long p1Count = handler.arecipes.stream()
            .filter(r -> r instanceof CachedNeutronInteractionRecipe)
            .count();
        long p2Count = handler.arecipes.stream()
            .filter(r -> r instanceof CachedComponentThermalRecipe)
            .count();

        assertEquals(
            p1Count,
            p2Count,
            "Every component must have Page 1 (Interaction) and Page 2 (Thermal/Durability)");
        assertTrue(p1Count >= 20, "Should have at least 20 component recipes registered");
    }

    @Test
    public void testReactorControllerCatalystLoadsAllComponents() {
        NEINeutronInteractionHandler handler = new NEINeutronInteractionHandler();
        ItemStack controller = NEIFuelStatsHandler.getControllerStack();
        assertNotNull(controller);

        handler.loadUsageRecipes(controller);
        assertFalse(handler.arecipes.isEmpty(), "Clicking 'U' on reactor controller should load all components");
    }

    @Test
    public void testThermalNeutronArrowTooltips() {
        NEINeutronInteractionHandler handler = new NEINeutronInteractionHandler();
        handler.loadCraftingRecipes(handler.getOverlayIdentifier());

        CachedNeutronInteractionRecipe r1 = null;
        for (CachedRecipe cr : handler.arecipes) {
            if (cr instanceof CachedNeutronInteractionRecipe recipe && !recipe.data.isReflector) {
                r1 = recipe;
                break;
            }
        }
        assertNotNull(r1, "Should find non-reflector neutron recipe");

        // 1. Thermal scattering arrow and text
        java.util.List<String> tipScatTip = handler.getTooltipsAt(r1, 40, 90, null);
        assertFalse(tipScatTip.isEmpty(), "Thermal scattering arrow tip (40, 90) must show tooltip");
        assertTrue(
            tipScatTip.stream()
                .anyMatch(
                    s -> s.toLowerCase()
                        .contains("scattering")),
            "Should mention scattering");

        java.util.List<String> tipScatCurve = handler.getTooltipsAt(r1, 52, 95, null);
        assertFalse(tipScatCurve.isEmpty(), "Thermal scattering arrow curve (52, 95) must show tooltip");

        java.util.List<String> tipScatText = handler.getTooltipsAt(r1, 47, 82, null);
        assertFalse(tipScatText.isEmpty(), "Thermal scattering text (47, 82) must show tooltip");

        // 2. Thermal absorption downward arrow, curved arrow, and text
        java.util.List<String> tipAbsDown = handler.getTooltipsAt(r1, 74, 120, null);
        assertFalse(tipAbsDown.isEmpty(), "Thermal absorption downward arrow (74, 120) must show tooltip");
        assertTrue(
            tipAbsDown.stream()
                .anyMatch(
                    s -> s.toLowerCase()
                        .contains("absorption")),
            "Should mention absorption");

        java.util.List<String> tipAbsCurved = handler.getTooltipsAt(r1, 85, 92, null);
        assertFalse(tipAbsCurved.isEmpty(), "Thermal absorption curved arrow (85, 92) must show tooltip");
        assertTrue(
            tipAbsCurved.stream()
                .anyMatch(
                    s -> s.toLowerCase()
                        .contains("absorption")),
            "Should mention absorption");

        java.util.List<String> tipAbsText = handler.getTooltipsAt(r1, 97, 120, null);
        assertFalse(tipAbsText.isEmpty(), "Thermal absorption text (97, 120) must show tooltip");

        // 3. Fast neutron arrows
        java.util.List<String> tipFastScat = handler.getTooltipsAt(r1, 40, 24, null);
        assertFalse(tipFastScat.isEmpty(), "Fast scattering arrow (40, 24) must show tooltip");

        java.util.List<String> tipFastAbsCurved = handler.getTooltipsAt(r1, 85, 26, null);
        assertFalse(tipFastAbsCurved.isEmpty(), "Fast absorption curved arrow (85, 26) must show tooltip");

        java.util.List<String> tipFastAbsText = handler.getTooltipsAt(r1, 97, 54, null);
        assertFalse(tipFastAbsText.isEmpty(), "Fast absorption text (97, 54) must show tooltip");
    }

    @Test
    public void testWordWrappingExtraInfo() {
        String longText = "Liquid metal coolant (melts at 97.8 °C, boils at 883 °C) | k = 0.70";
        java.util.List<String> lines = NEINeutronInteractionHandler.wrapExtraInfo(longText, 132, null);
        assertTrue(lines.size() >= 2, "Long extraInfo text must wrap into at least 2 lines");

        for (String line : lines) {
            assertTrue(line.length() <= 35, "Each wrapped line should fit within max character limit: " + line);
        }

        String shortText = "Short info";
        java.util.List<String> shortLines = NEINeutronInteractionHandler.wrapExtraInfo(shortText, 132, null);
        assertEquals(1, shortLines.size(), "Short text should remain a single line");
        assertEquals("Short info", shortLines.get(0));

        assertTrue(
            NEINeutronInteractionHandler.wrapExtraInfo(null, 132, null)
                .isEmpty());
        assertTrue(
            NEINeutronInteractionHandler.wrapExtraInfo("", 132, null)
                .isEmpty());
    }
}
