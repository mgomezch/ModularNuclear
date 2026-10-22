package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.metatileentity.ModMetaTileEntities;
import com.gtnewhorizons.modularnuclear.common.nei.NEIFuelStatsHandler;

import codechicken.nei.recipe.HandlerInfo;

public class NEIFuelStatsHandlerTest {

    @BeforeAll
    public static void setUp() {
        try {
            Bootstrap.func_151354_b();
        } catch (Throwable ignored) {}

        if (ModMetaTileEntities.reactor == null) {
            ModMetaTileEntities.reactor = new ItemStack(Items.diamond, 1, 0);
        }
    }

    @Test
    public void testControllerStackAndHandlerId() {
        ItemStack controller = NEIFuelStatsHandler.getControllerStack();
        assertNotNull(controller, "Controller stack should not be null");

        NEIFuelStatsHandler handler = new NEIFuelStatsHandler();
        assertEquals(NEIFuelStatsHandler.OVERLAY_ID, handler.getHandlerId());
        assertEquals(NEIFuelStatsHandler.OVERLAY_ID, handler.getOverlayIdentifier());
    }

    @Test
    public void testHandlerInfoBuilderWithControllerStack() {
        ItemStack controller = NEIFuelStatsHandler.getControllerStack();
        assertNotNull(controller);

        HandlerInfo info = new HandlerInfo.Builder(
            NEIFuelStatsHandler.OVERLAY_ID,
            "Modular Nuclear",
            ModularNuclear.MODID).setDisplayStack(controller)
                .setMaxRecipesPerPage(1)
                .build();

        assertNotNull(info);
        assertEquals(NEIFuelStatsHandler.OVERLAY_ID, info.getHandlerName());
        assertNotNull(info.getItemStack(), "Display stack on HandlerInfo must not be null");
        assertEquals(
            controller.getItem(),
            info.getItemStack()
                .getItem());
    }

    @Test
    public void testControllerRecipeLookup() {
        NEIFuelStatsHandler handler = new NEIFuelStatsHandler();
        ItemStack controller = NEIFuelStatsHandler.getControllerStack();

        handler.loadCraftingRecipes(controller);
        assertFalse(handler.arecipes.isEmpty(), "Clicking 'R' on controller should load fuel recipes");

        handler.arecipes.clear();
        handler.loadUsageRecipes(controller);
        assertFalse(handler.arecipes.isEmpty(), "Clicking 'U' on controller should load fuel recipes");
    }

    @Test
    public void testCachedFuelStatsRecipeWithFuelData() {
        com.gtnewhorizons.modularnuclear.common.config.data.FuelData fd = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .getAllFuels()
            .stream()
            .filter(f -> "fuel_uranium_1".equalsIgnoreCase(f.id))
            .findFirst()
            .orElse(null);
        assertNotNull(fd, "Uranium FuelData should exist in ReactorDataRegistry");
        assertEquals(160000, fd.defaultDurability);
        assertEquals(770.0, fd.peakReactivityTemp);
        assertEquals(2300.0, fd.floorTemp);

        NEIFuelStatsHandler.CachedFuelStatsRecipe recipe = new NEIFuelStatsHandler().new CachedFuelStatsRecipe(fd,
            NEIFuelStatsHandler.getFuelStacks(fd));
        assertNotNull(recipe.fuelData);
        assertEquals(fd, recipe.fuelData);
        assertEquals(160000, recipe.fuelData.defaultDurability);
    }

    @Test
    public void testAmbientTemperatureFixedAt20C() {
        assertEquals(20.0, NEIFuelStatsHandler.AMBIENT_TEMP, 1e-6, "NEI MPTR fuels ambient temperature must be fixed to 20.0 °C");
        assertEquals(20.0, NEIFuelStatsHandler.DEFAULT_AMBIENT_TEMP, 1e-6);
        assertEquals(20.0, NEIFuelStatsHandler.sAmbientTemp, 1e-6);
    }

    @Test
    public void testDurabilityCurveDamageCalculation() {
        for (com.gtnewhorizons.modularnuclear.common.nuclear.NuclearFuelType fuel : com.gtnewhorizons.modularnuclear.common.nuclear.NuclearFuelType
            .values()) {
            com.gtnewhorizons.modularnuclear.common.nuclear.FuelDamageCurveSolver.FuelCurveStats stats = com.gtnewhorizons.modularnuclear.common.nuclear.FuelDamageCurveSolver
                .solve(fuel);

            // Damage at or below ambient must be strictly 0
            assertEquals(0.0, stats.calculateDamage(20.0), 1e-6, "Damage at ambient temp must be 0 for " + fuel);
            assertEquals(0.0, stats.calculateDamage(15.0), 1e-6, "Damage below ambient temp must be 0 for " + fuel);
            assertEquals(0.0, stats.calculateDamage(0.0), 1e-6, "Damage at 0 °C must be 0 when ambient is 20 °C");

            // Damage above ambient must be positive and monotonic
            double d50 = stats.calculateDamage(50.0);
            double dPeak = stats.calculateDamage(fuel.peakReactivityTemp);
            double dFloor = stats.calculateDamage(fuel.floorTemp);

            assertTrue(d50 > 0.0, "Damage above ambient temp must be > 0 for " + fuel);
            assertTrue(dPeak > d50, "Damage at peak temp must exceed damage at 50 °C for " + fuel);
            assertTrue(dFloor > dPeak, "Damage at floor temp must exceed damage at peak temp for " + fuel);
            assertTrue(stats.damageRatioPercent <= 25.01, "Damage ratio D(T_pk)/D(T_10) must be <= 25% for " + fuel);
        }
    }
}
