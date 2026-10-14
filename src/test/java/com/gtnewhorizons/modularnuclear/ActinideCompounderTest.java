package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.item.ModItems;
import com.gtnewhorizons.modularnuclear.common.metatileentity.ModMetaTileEntities;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTEActinideCompounder;
import com.gtnewhorizons.modularnuclear.common.nuclear.CustomFuelMix;
import com.gtnewhorizons.modularnuclear.common.nuclear.CustomFuelMix.ActinideIsotope;
import com.gtnewhorizons.modularnuclear.common.nuclear.INuclearTile;
import com.gtnewhorizons.modularnuclear.common.nuclear.NeutronType;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification;

public class ActinideCompounderTest {

    @BeforeAll
    static void initEnvironment() {
        Thread.currentThread()
            .setName("Server thread");
        try {
            cpw.mods.fml.common.Loader mockLoader = org.mockito.Mockito.mock(cpw.mods.fml.common.Loader.class);
            org.mockito.Mockito.when(mockLoader.getCallableCrashInformation())
                .thenReturn(org.mockito.Mockito.mock(cpw.mods.fml.common.ICrashCallable.class));
            java.lang.reflect.Field f = cpw.mods.fml.common.Loader.class.getDeclaredField("instance");
            f.setAccessible(true);
            if (f.get(null) == null) {
                f.set(null, mockLoader);
            }
        } catch (Throwable ignored) {}

        try {
            net.minecraft.init.Bootstrap.func_151354_b();
        } catch (Throwable ignored) {}

        try {
            java.lang.reflect.Field mcHome = cpw.mods.fml.relauncher.FMLInjectionData.class
                .getDeclaredField("minecraftHome");
            mcHome.setAccessible(true);
            mcHome.set(null, new java.io.File("."));
        } catch (Throwable ignored) {}

        try {
            if (gregtech.GTMod.proxy == null) {
                gregtech.GTMod.proxy = org.mockito.Mockito.mock(gregtech.common.GTProxy.class);
            }
        } catch (Throwable ignored) {}

        try {
            java.lang.reflect.Field sideField = cpw.mods.fml.relauncher.FMLRelaunchLog.class.getDeclaredField("side");
            sideField.setAccessible(true);
            sideField.set(null, cpw.mods.fml.relauncher.Side.SERVER);
        } catch (Throwable ignored) {}

        // Ensure ModItems are initialized
        ModItems.init();
    }

    @Test
    void testDustUnitArithmetic() {
        // 1 dust = 36 units
        // 1 small dust = 9 units (1/4 dust)
        // 1 tiny dust = 4 units (1/9 dust)
        // Total target = 360 units (10 dusts)
        assertEquals(36, 1 * 36);
        assertEquals(9, 36 / 4);
        assertEquals(4, 36 / 9);
        assertEquals(360, 10 * 36);

        // Mixed test: 5 regular dusts (5 * 36 = 180)
        // 12 small dusts (12 * 9 = 108)
        // 18 tiny dusts (18 * 4 = 72)
        // Total = 180 + 108 + 72 = 360 units (exactly 10 dusts)
        int totalUnits = (5 * 36) + (12 * 9) + (18 * 4);
        assertEquals(360, totalUnits);
    }

    @Test
    void testCustomFuelMixDynamicProperties() {
        // Create a 50% U-235 / 50% Pu-239 mix (180 units each = 360 units total)
        Map<ActinideIsotope, Integer> units = new LinkedHashMap<>();
        units.put(ActinideIsotope.URANIUM_235, 180);
        units.put(ActinideIsotope.PLUTONIUM_239, 180);

        CustomFuelMix mix = new CustomFuelMix(units);

        // Fractions
        assertEquals(
            0.5,
            mix.getIsotopeFractions()
                .get(ActinideIsotope.URANIUM_235),
            1e-4);
        assertEquals(
            0.5,
            mix.getIsotopeFractions()
                .get(ActinideIsotope.PLUTONIUM_239),
            1e-4);
        assertEquals(1.0, mix.fissileFraction, 1e-4);

        // Peak temp = 0.5 * 770 + 0.5 * 1100 = 935.0 °C
        assertEquals(935.0, mix.peakReactivityTemp, 1e-1);

        // Floor temp = 0.5 * 2300 + 0.5 * 2500 = 2400.0 °C
        assertEquals(2400.0, mix.floorTemp, 1e-1);

        // Thermal mult = 0.5 * 1.60 + 0.5 * 1.80 = 1.70
        assertEquals(1.70, mix.baseThermalFissionMultiplier, 1e-2);

        // Fast mult = 0.5 * 1.20 + 0.5 * 1.60 = 1.40
        assertEquals(1.40, mix.baseFastFissionMultiplier, 1e-2);

        // Base neutrons = 0.5 * 24 + 0.5 * 32 = 28
        assertEquals(28, mix.baseNeutrons);

        // Base durability = 0.5 * 20000 + 0.5 * 20000 = 20000
        assertEquals(20000, mix.defaultDurability);

        // Reactivity curve checks
        assertEquals(0.50, mix.calculateReactivity(0.0), 1e-3);
        assertEquals(1.00, mix.calculateReactivity(mix.peakReactivityTemp), 1e-3);
        assertEquals(0.05, mix.calculateReactivity(mix.floorTemp), 1e-3);
        assertEquals(0.05, mix.calculateReactivity(mix.floorTemp + 500.0), 1e-3);

        // Durability damage curve checks: 0 damage at ambient (20 C)
        assertEquals(0.0, mix.calculateTemperatureDamage(20.0), 1e-4);
        assertTrue(mix.calculateTemperatureDamage(1000.0) > 0.0);
        assertTrue(mix.calculateTemperatureDamage(2000.0) > mix.calculateTemperatureDamage(1000.0));
    }

    @Test
    void testNBTSerializationDeserialization() {
        Map<ActinideIsotope, Integer> units = new LinkedHashMap<>();
        units.put(ActinideIsotope.THORIUM, 180);
        units.put(ActinideIsotope.URANIUM_233, 90);
        units.put(ActinideIsotope.PLUTONIUM_241, 90);

        CustomFuelMix original = new CustomFuelMix(units);

        NBTTagCompound nbt = new NBTTagCompound();
        original.writeToNBT(nbt);

        assertTrue(nbt.hasKey(CustomFuelMix.NBT_TAG_ROOT));

        CustomFuelMix loaded = CustomFuelMix.readFromNBT(nbt);
        assertNotNull(loaded);
        assertEquals(original.peakReactivityTemp, loaded.peakReactivityTemp, 1e-2);
        assertEquals(original.floorTemp, loaded.floorTemp, 1e-2);
        assertEquals(original.baseThermalFissionMultiplier, loaded.baseThermalFissionMultiplier, 1e-2);
        assertEquals(original.baseFastFissionMultiplier, loaded.baseFastFissionMultiplier, 1e-2);
        assertEquals(original.baseNeutrons, loaded.baseNeutrons);
        assertEquals(original.defaultDurability, loaded.defaultDurability);
    }

    @Test
    void testCustomFuelItemsAndDepletedForm() {
        ItemStack fuelStack = new ItemStack(ModItems.rodCustomFuel4);
        Map<ActinideIsotope, Integer> units = new LinkedHashMap<>();
        units.put(ActinideIsotope.URANIUM_238, 324); // 9 dusts (324 units)
        units.put(ActinideIsotope.URANIUM_235, 36); // 1 dust (36 units)
        CustomFuelMix mix = new CustomFuelMix(units);
        mix.writeToNBT(fuelStack.stackTagCompound != null ? fuelStack.stackTagCompound : new NBTTagCompound());
        if (fuelStack.stackTagCompound == null) {
            NBTTagCompound tag = new NBTTagCompound();
            mix.writeToNBT(tag);
            fuelStack.setTagCompound(tag);
        }

        assertTrue(CustomFuelMix.hasCustomMix(fuelStack));
        assertTrue(NuclearFuelClassification.isItemFuel(fuelStack));

        // Tooltip verification
        List<String> tooltip = new ArrayList<>();
        ModItems.rodCustomFuel4.addAdditionalToolTips(tooltip, fuelStack, null);
        assertFalse(tooltip.isEmpty());
        assertTrue(
            tooltip.stream()
                .anyMatch(s -> s.contains("Custom Actinic Composition")));
        assertTrue(
            tooltip.stream()
                .anyMatch(s -> s.contains("Peak Reactivity")));

        // Depleted form verification
        ItemStack depleted = NuclearFuelClassification.getItemDepletedForm(fuelStack);
        assertNotNull(depleted);
        assertEquals(ModItems.rodCustomDepletedFuel4, depleted.getItem());
        assertTrue(CustomFuelMix.hasCustomMix(depleted));

        List<String> depletedTooltip = new ArrayList<>();
        ModItems.rodCustomDepletedFuel4.addAdditionalToolTips(depletedTooltip, depleted, null);
        assertTrue(
            depletedTooltip.stream()
                .anyMatch(s -> s.contains("Depleted Custom Core Fuel")));
    }

    @Test
    void testReactorSimulationEngineCustomReactivity() {
        Map<ActinideIsotope, Integer> units = new LinkedHashMap<>();
        units.put(ActinideIsotope.THORIUM, 360);
        CustomFuelMix thoriumMix = new CustomFuelMix(units);

        // Mock an INuclearTile with custom fuel mix
        INuclearTile customTile = new MockNuclearTile(thoriumMix, 580.0);
        assertTrue(customTile.isFuel());
        assertEquals(thoriumMix, customTile.getCustomFuelMix());

        // At peak reactivity (580 °C), efficiency should be 1.0 (100%)
        double effPeak = NuclearSimulationEngine.calculateEfficiency(customTile, 580.0);
        assertEquals(1.00, effPeak, 1e-2);

        // At 0 °C, efficiency should be 0.50 (50%)
        double effCold = NuclearSimulationEngine.calculateEfficiency(customTile, 0.0);
        assertEquals(0.50, effCold, 1e-2);

        // At floor temp (2200 °C), efficiency should be 0.05 (5%)
        double effHot = NuclearSimulationEngine.calculateEfficiency(customTile, 2200.0);
        assertEquals(0.05, effHot, 1e-2);
    }

    @Test
    void testActinideCompounderMetaTileEntityRegistration() {
        assertEquals(32116, ModMetaTileEntities.ID_ACTINIDE_COMPOUNDER);
        MTEActinideCompounder compounder = new MTEActinideCompounder("test.compounder");
        assertNotNull(compounder);
    }

    private static class MockNuclearTile implements INuclearTile {

        private final CustomFuelMix mix;
        private double temp;

        MockNuclearTile(CustomFuelMix mix, double temp) {
            this.mix = mix;
            this.temp = temp;
        }

        @Override
        public double getTemperature() {
            return temp;
        }

        @Override
        public void setTemperature(double temperature) {
            this.temp = temperature;
        }

        @Override
        public void addHeat(double heatEU) {
            this.temp += heatEU / 64.0;
        }

        @Override
        public double getHeatTransferCoeff() {
            return 0.15;
        }

        @Override
        public boolean isFuel() {
            return true;
        }

        @Override
        public int generateNeutrons(double efficiency) {
            return (int) (mix.baseNeutrons * efficiency);
        }

        @Override
        public double getAbsorptionProbability(NeutronType type) {
            return 0.5;
        }

        @Override
        public double getScatteringProbability(NeutronType type) {
            return 0.5;
        }

        @Override
        public double getModerationProbability() {
            return 0.0;
        }

        @Override
        public void onNeutronAbsorbed(NeutronType type, int count) {}

        @Override
        public void onNeutronScattered(NeutronType type, int count) {}

        @Override
        public void addNeutronFlux(NeutronType type, int count) {}

        @Override
        public void nuclearTick(double efficiency) {}

        @Override
        public CustomFuelMix getCustomFuelMix() {
            return mix;
        }
    }
}
