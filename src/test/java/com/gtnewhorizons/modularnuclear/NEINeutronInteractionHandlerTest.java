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
            if (ModItems.radiovoltaicPlateHV == null) {
                ModItems.radiovoltaicPlateHV = new com.gtnewhorizons.modularnuclear.common.item.ItemRadiovoltaicPlate(
                    "modularnuclear.radiovoltaic.plate.hv",
                    1);
            }
            if (ModItems.radiovoltaicPlateEV == null) {
                ModItems.radiovoltaicPlateEV = new com.gtnewhorizons.modularnuclear.common.item.ItemRadiovoltaicPlate(
                    "modularnuclear.radiovoltaic.plate.ev",
                    2);
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
}
