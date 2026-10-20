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
    public void testAmbientTemperatureDefaultAndAdjustment() {
        NEIFuelStatsHandler.resetAmbientTemp();
        assertEquals(24.0, NEIFuelStatsHandler.sAmbientTemp, 1e-6, "Default ambient temperature must be 24.0 °C");
        assertEquals("24", NEIFuelStatsHandler.sInputText);

        // Test adjustment: +1.0 and -5.0
        NEIFuelStatsHandler.adjustAmbientTemp(1.0);
        assertEquals(25.0, NEIFuelStatsHandler.sAmbientTemp, 1e-6);
        assertEquals("25", NEIFuelStatsHandler.sInputText);

        NEIFuelStatsHandler.adjustAmbientTemp(-5.0);
        assertEquals(20.0, NEIFuelStatsHandler.sAmbientTemp, 1e-6);
        assertEquals("20", NEIFuelStatsHandler.sInputText);

        // Test reset
        NEIFuelStatsHandler.resetAmbientTemp();
        assertEquals(24.0, NEIFuelStatsHandler.sAmbientTemp, 1e-6);
        assertEquals("24", NEIFuelStatsHandler.sInputText);
    }

    @Test
    public void testAmbientTemperatureInputParsingAndClamping() {
        NEIFuelStatsHandler.sInputText = "100";
        NEIFuelStatsHandler.updateAmbientFromInput();
        assertEquals(100.0, NEIFuelStatsHandler.sAmbientTemp, 1e-6);

        NEIFuelStatsHandler.sInputText = "-50";
        NEIFuelStatsHandler.updateAmbientFromInput();
        assertEquals(-50.0, NEIFuelStatsHandler.sAmbientTemp, 1e-6);

        // Clamp below absolute zero
        NEIFuelStatsHandler.sInputText = "-300";
        NEIFuelStatsHandler.updateAmbientFromInput();
        assertEquals(-273.15, NEIFuelStatsHandler.sAmbientTemp, 1e-6);

        // Clamp above 5000 °C
        NEIFuelStatsHandler.sInputText = "9999";
        NEIFuelStatsHandler.updateAmbientFromInput();
        assertEquals(5000.0, NEIFuelStatsHandler.sAmbientTemp, 1e-6);

        NEIFuelStatsHandler.resetAmbientTemp();
    }

    @Test
    public void testDynamicDurabilityCurveDamageCalculation() {
        NEIFuelStatsHandler.sAmbientTemp = 24.0;
        for (com.gtnewhorizons.modularnuclear.common.nuclear.NuclearFuelType fuel : com.gtnewhorizons.modularnuclear.common.nuclear.NuclearFuelType
            .values()) {
            com.gtnewhorizons.modularnuclear.common.nuclear.FuelDamageCurveSolver.FuelCurveStats stats = com.gtnewhorizons.modularnuclear.common.nuclear.FuelDamageCurveSolver
                .solve(fuel, 24.0);

            // Damage at or below ambient must be strictly 0
            assertEquals(0.0, stats.calculateDamage(24.0), 1e-6, "Damage at ambient temp must be 0 for " + fuel);
            assertEquals(0.0, stats.calculateDamage(20.0), 1e-6, "Damage below ambient temp must be 0 for " + fuel);
            assertEquals(0.0, stats.calculateDamage(0.0), 1e-6, "Damage at 0 °C must be 0 when ambient is 24 °C");

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
