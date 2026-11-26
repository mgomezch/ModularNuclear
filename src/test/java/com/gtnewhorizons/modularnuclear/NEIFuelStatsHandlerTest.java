package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry;
import com.gtnewhorizons.modularnuclear.common.config.data.FuelData;
import com.gtnewhorizons.modularnuclear.common.config.data.PhysicsData;
import com.gtnewhorizons.modularnuclear.common.metatileentity.ModMetaTileEntities;
import com.gtnewhorizons.modularnuclear.common.nei.NEIFuelStatsHandler;
import com.gtnewhorizons.modularnuclear.common.nuclear.EngineParameters;
import com.gtnewhorizons.modularnuclear.common.nuclear.FuelDamageCurveSolver;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearFuelType;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.TileProperties;
import com.gtnewhorizons.modularnuclear.common.nuclear.TileTick;

import codechicken.nei.recipe.HandlerInfo;

public class NEIFuelStatsHandlerTest {

    @BeforeAll
    public static void setUp() {
        TestEnvironmentHelper.init();

        if (ModMetaTileEntities.reactor == null) {
            ModMetaTileEntities.reactor = new ItemStack(Items.diamond, 1, 0);
        }

        if (FluidRegistry.getFluid("thorium based liquid fuel") == null) {
            FluidRegistry.registerFluid(new Fluid("thorium based liquid fuel"));
        }
        if (FluidRegistry.getFluid("thorium based liquid fuel (excited state)") == null) {
            FluidRegistry.registerFluid(new Fluid("thorium based liquid fuel (excited state)"));
        }
        if (FluidRegistry.getFluid("molten.uraniumhexafluoride") == null) {
            FluidRegistry.registerFluid(new Fluid("molten.uraniumhexafluoride"));
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
        FuelData fd = ReactorDataRegistry.getAllFuels()
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
        assertEquals(
            20.0,
            NEIFuelStatsHandler.AMBIENT_TEMP,
            1e-6,
            "NEI MPTR fuels ambient temperature must be fixed to 20.0 °C");
        assertEquals(20.0, NEIFuelStatsHandler.DEFAULT_AMBIENT_TEMP, 1e-6);
        assertEquals(20.0, NEIFuelStatsHandler.sAmbientTemp, 1e-6);
    }

    @Test
    public void testDurabilityCurveDamageCalculation() {
        for (NuclearFuelType fuel : NuclearFuelType.values()) {
            FuelDamageCurveSolver.FuelCurveStats stats = FuelDamageCurveSolver.solve(fuel);

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

    @Test
    public void testLiquidFuelCanonicalNames() {
        FuelData thExcited = ReactorDataRegistry.getAllFuels()
            .stream()
            .filter(f -> "fluid_fuel_thorium_excited".equalsIgnoreCase(f.id))
            .findFirst()
            .orElse(null);
        assertNotNull(thExcited);
        assertEquals("Thorium Based Liquid Fuel (Excited State)", thExcited.name);
        assertTrue(thExcited.isLiquid);
        assertEquals("thorium based liquid fuel (depleted)", thExcited.spentProduct);

        FuelData thStd = ReactorDataRegistry.getAllFuels()
            .stream()
            .filter(f -> "fluid_fuel_thorium".equalsIgnoreCase(f.id))
            .findFirst()
            .orElse(null);
        assertNotNull(thStd);
        assertEquals("Thorium Based Liquid Fuel", thStd.name);
        assertTrue(thStd.isLiquid);
        assertEquals("thorium based liquid fuel (depleted)", thStd.spentProduct);

        FuelData uHex = ReactorDataRegistry.getAllFuels()
            .stream()
            .filter(f -> "fluid_fuel_u_hexafluoride".equalsIgnoreCase(f.id))
            .findFirst()
            .orElse(null);
        assertNotNull(uHex);
        assertEquals("Uranium Hexafluoride", uHex.name);
        assertTrue(uHex.isLiquid);
        assertEquals("molten.uraniumtetrafluoride", uHex.spentProduct);
    }

    @Test
    public void testFindFluidIsExact() {
        // Register test fluids if not already registered
        if (FluidRegistry.getFluid("thorium based liquid fuel (excited state)") == null) {
            FluidRegistry.registerFluid(new Fluid("thorium based liquid fuel (excited state)"));
        }
        if (FluidRegistry.getFluid("molten.uraniumhexafluoride") == null) {
            FluidRegistry.registerFluid(new Fluid("molten.uraniumhexafluoride"));
        }

        // Test exact matches
        assertNotNull(ReactorDataRegistry.findFluid("thorium based liquid fuel (excited state)"));
        assertNotNull(ReactorDataRegistry.findFluid("molten.uraniumhexafluoride"));

        // Prefix variations name nothing
        assertNull(ReactorDataRegistry.findFluid("uraniumhexafluoride"));
        assertNull(ReactorDataRegistry.findFluid("fluid.molten.uraniumhexafluoride"));

        // Normalized, recased or permuted names name nothing
        assertNull(ReactorDataRegistry.findFluid("thoriumbasedliquidfuelexcited"));
        assertNull(ReactorDataRegistry.findFluid("thoriumbasedliquidfuelexcitedstate"));
        assertNull(ReactorDataRegistry.findFluid("Thorium Based Liquid Fuel (Excited State)"));
        assertNull(ReactorDataRegistry.findFluid("Thorium-Based Liquid Fuel"));
        assertNull(ReactorDataRegistry.findFluid("Excited Thorium-Based Liquid Fuel"));
    }

    @Test
    public void testMatchesFluid() {
        if (FluidRegistry.getFluid("thorium based liquid fuel (excited state)") == null) {
            FluidRegistry.registerFluid(new Fluid("thorium based liquid fuel (excited state)"));
        }
        Fluid fTh = ReactorDataRegistry.findFluid("thorium based liquid fuel (excited state)");
        assertNotNull(fTh);
        FluidStack fs = new FluidStack(fTh, 1000);

        List<String> names = List.of("thorium based liquid fuel (excited state)");
        assertTrue(ReactorDataRegistry.matchesFluid(fs, names));
        assertTrue(ReactorDataRegistry.matchesFluid(fTh, names));
        assertTrue(ReactorDataRegistry.matchesFluid("thorium based liquid fuel (excited state)", names));

        // Only the exact registry name matches: no recasing, normalization or prefix stripping
        List<String> fuzzy = List.of(
            "Thorium Based Liquid Fuel (Excited State)",
            "thoriumbasedliquidfuelexcited",
            "fluid.thoriumbasedliquidfuelexcited");
        assertFalse(ReactorDataRegistry.matchesFluid(fs, fuzzy));
        assertFalse(ReactorDataRegistry.matchesFluid("thorium based liquid fuel (excited state)", fuzzy));
        assertFalse(ReactorDataRegistry.matchesFluid("thoriumbasedliquidfuelexcited", names));
    }

    @Test
    public void testLiquidFuelNEIDisplayStacks() {
        FuelData thExcited = ReactorDataRegistry.getAllFuels()
            .stream()
            .filter(f -> "fluid_fuel_thorium_excited".equalsIgnoreCase(f.id))
            .findFirst()
            .orElse(null);
        assertNotNull(thExcited);

        List<ItemStack> stacks = NEIFuelStatsHandler.getFuelStacks(thExcited);
        assertNotNull(stacks);
        assertFalse(stacks.isEmpty(), "Liquid fuel should produce display stacks in NEI handler");
        ItemStack displayStack = stacks.get(0);
        assertNotNull(displayStack);
        assertNotEquals(Items.water_bucket, displayStack.getItem(), "Should not fall back to water bucket");
    }

    // mgomezch/ModularNuclear#36: the plotted wear is what TileTick.fuelWear charges for temperature
    @Test
    public void testPlottedWearIsWhatTheReactorCharges() {
        EngineParameters p = NuclearSimulationEngine.params()
            .copy();
        p.fuelBurnupMultiplier = 0.04;
        for (FuelData fd : ReactorDataRegistry.getAllFuels()) {
            FuelDamageCurveSolver.FuelCurveStats stats = NEIFuelStatsHandler.curveStats(fd, null);
            for (double temp : new double[] { 20.0, 300.0, fd.peakReactivityTemp, fd.floorTemp }) {
                double raw = fd.calculateTemperatureDamage(temp);
                double charged = TileTick.fuelWear(p, raw, 0, 0, 0, 1.0);
                double plotted = NEIFuelStatsHandler.temperatureWear(p, stats, temp);
                assertEquals(charged, plotted, Math.max(1e-12, charged * 1e-9), fd.id + " at " + temp);
                if (raw > 0.0) assertTrue(plotted < raw, fd.id + ": the burnup multiplier applies");
            }
        }
    }

    @Test
    public void testWearAxisAndLabels() {
        assertEquals(0.08, NEIFuelStatsHandler.wearAxisMax(0.07), 1e-12); // 0.077 / 4 rounds up to 0.02
        assertEquals(2.0, NEIFuelStatsHandler.wearAxisMax(1.5), 1e-12); // 1.65 / 4 to 0.5
        assertEquals(40.0, NEIFuelStatsHandler.wearAxisMax(30.0), 1e-12); // 33 / 4 to 10
        assertEquals(4.0, NEIFuelStatsHandler.wearAxisMax(10.0 / 1.1 * 4.0 / 10.0), 1e-12); // exactly 1 per step
        assertEquals(1.0, NEIFuelStatsHandler.wearAxisMax(0.0), 1e-12);
        assertEquals("0.0438", NEIFuelStatsHandler.formatWear(0.0437655));
        assertEquals("12.5", NEIFuelStatsHandler.formatWear(12.5));
        assertEquals("0.02", NEIFuelStatsHandler.formatWear(0.02));
        assertEquals("0", NEIFuelStatsHandler.formatWear(0.0));
    }
}
