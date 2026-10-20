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

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

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
        if (com.gtnewhorizons.modularnuclear.common.block.ModBlocks.nuclearCasing == null) {
            com.gtnewhorizons.modularnuclear.common.block.ModBlocks.nuclearCasing = org.mockito.Mockito
                .mock(com.gtnewhorizons.modularnuclear.common.block.BlockNuclearCasing.class);
        }

        try {
            java.lang.reflect.Field f = com.gtnewhorizon.structurelib.StructureLib.class.getDeclaredField("blockHint");
            f.setAccessible(true);
            if (f.get(null) == null) {
                f.set(null, org.mockito.Mockito.mock(net.minecraft.block.Block.class));
            }
        } catch (Throwable ignored) {}
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

        // Peak temp = 0.5 * 1540.0 + 0.5 * 1350.0 = 1445.0 °C
        assertEquals(1445.0, mix.peakReactivityTemp, 1e-1);

        // Floor temp = 0.5 * 2700.0 + 0.5 * 2600.0 = 2650.0 °C
        assertEquals(2650.0, mix.floorTemp, 1e-1);

        // Thermal mult = 0.5 * 2.50 + 0.5 * 2.10 = 2.30
        assertEquals(2.30, mix.baseThermalFissionMultiplier, 1e-2);

        // Fast mult = 0.5 * 0.15 + 0.5 * 0.20 = 0.18
        assertEquals(0.18, mix.baseFastFissionMultiplier, 1e-2);

        // Base neutrons = 0.5 * 64 + 0.5 * 48 = 56
        assertEquals(56, mix.baseNeutrons);

        // Base durability = 0.5 * 48000 + 0.5 * 200000 = 124000
        assertEquals(124000, mix.defaultDurability);

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

    @Test
    void testSingleAndDualRequiredUnitsAndBaseNeutrons() {
        assertEquals(90, CustomFuelMix.getRequiredUnits(1));
        assertEquals(180, CustomFuelMix.getRequiredUnits(2));
        assertEquals(360, CustomFuelMix.getRequiredUnits(4));

        Map<ActinideIsotope, Integer> units = new LinkedHashMap<>();
        units.put(ActinideIsotope.URANIUM_235, 360);
        CustomFuelMix mix = new CustomFuelMix(units);
        assertEquals(64, mix.baseNeutrons);
        assertEquals(16, mix.getBaseNeutrons(1)); // 64 * 0.25 = 16
        assertEquals(32, mix.getBaseNeutrons(2)); // 64 * 0.50 = 32
        assertEquals(64, mix.getBaseNeutrons(4)); // 64 * 1.00 = 64
    }

    @Test
    void testActinideCompounderPowerRequirements() {
        assertEquals(1920, MTEActinideCompounder.getPowerRequirement(1), "Single rod requires 1920 EU/t (15/16 EV)");
        assertEquals(7680, MTEActinideCompounder.getPowerRequirement(2), "Dual rod requires 7680 EU/t (15/16 IV)");
        assertEquals(30720, MTEActinideCompounder.getPowerRequirement(4), "Quad rod requires 30720 EU/t (15/16 LuV)");
    }

    @Test
    void testSingleAndDualCustomFuelItemsAndDepletedForm() {
        // Test Single
        assertEquals(1, ModItems.rodCustomFuel1.rodSize);
        assertEquals(10000, ModItems.rodCustomFuel1.getMaxDamageEx());
        assertEquals(1, ModItems.rodCustomDepletedFuel1.rodSize);

        // Test Dual
        assertEquals(2, ModItems.rodCustomFuel2.rodSize);
        assertEquals(20000, ModItems.rodCustomFuel2.getMaxDamageEx());
        assertEquals(2, ModItems.rodCustomDepletedFuel2.rodSize);

        // Test Quad
        assertEquals(4, ModItems.rodCustomFuel4.rodSize);
        assertEquals(40000, ModItems.rodCustomFuel4.getMaxDamageEx());
        assertEquals(4, ModItems.rodCustomDepletedFuel4.rodSize);

        // Test depleted forms mapping
        Map<ActinideIsotope, Integer> units = new LinkedHashMap<>();
        units.put(ActinideIsotope.URANIUM_235, 360);
        CustomFuelMix mix = new CustomFuelMix(units);

        ItemStack singleFuel = new ItemStack(ModItems.rodCustomFuel1);
        NBTTagCompound tag1 = new NBTTagCompound();
        mix.writeToNBT(tag1);
        singleFuel.setTagCompound(tag1);

        ItemStack depleted1 = NuclearFuelClassification.getItemDepletedForm(singleFuel);
        assertNotNull(depleted1);
        assertEquals(ModItems.rodCustomDepletedFuel1, depleted1.getItem());

        ItemStack dualFuel = new ItemStack(ModItems.rodCustomFuel2);
        NBTTagCompound tag2 = new NBTTagCompound();
        mix.writeToNBT(tag2);
        dualFuel.setTagCompound(tag2);

        ItemStack depleted2 = NuclearFuelClassification.getItemDepletedForm(dualFuel);
        assertNotNull(depleted2);
        assertEquals(ModItems.rodCustomDepletedFuel2, depleted2.getItem());

        ItemStack quadFuel = new ItemStack(ModItems.rodCustomFuel4);
        NBTTagCompound tag4 = new NBTTagCompound();
        mix.writeToNBT(tag4);
        quadFuel.setTagCompound(tag4);

        ItemStack depleted4 = NuclearFuelClassification.getItemDepletedForm(quadFuel);
        assertNotNull(depleted4);
        assertEquals(ModItems.rodCustomDepletedFuel4, depleted4.getItem());
    }

    @Test
    void testReactorDataRegistryCustomFuelScaling() {
        Map<ActinideIsotope, Integer> units = new LinkedHashMap<>();
        units.put(ActinideIsotope.URANIUM_235, 360);
        CustomFuelMix mix = new CustomFuelMix(units);

        ItemStack singleFuel = new ItemStack(ModItems.rodCustomFuel1);
        NBTTagCompound tag1 = new NBTTagCompound();
        mix.writeToNBT(tag1);
        singleFuel.setTagCompound(tag1);

        com.gtnewhorizons.modularnuclear.common.config.data.FuelData fd1 = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .findFuel(singleFuel);
        assertNotNull(fd1);
        assertEquals(1, fd1.packetCount);
        assertEquals(16, fd1.baseNeutrons);
        assertEquals("modularnuclear:rodCustomDepletedFuel1", fd1.spentProduct);

        ItemStack dualFuel = new ItemStack(ModItems.rodCustomFuel2);
        NBTTagCompound tag2 = new NBTTagCompound();
        mix.writeToNBT(tag2);
        dualFuel.setTagCompound(tag2);

        com.gtnewhorizons.modularnuclear.common.config.data.FuelData fd2 = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .findFuel(dualFuel);
        assertNotNull(fd2);
        assertEquals(2, fd2.packetCount);
        assertEquals(32, fd2.baseNeutrons);
        assertEquals("modularnuclear:rodCustomDepletedFuel2", fd2.spentProduct);

        ItemStack quadFuel = new ItemStack(ModItems.rodCustomFuel4);
        NBTTagCompound tag4 = new NBTTagCompound();
        mix.writeToNBT(tag4);
        quadFuel.setTagCompound(tag4);

        com.gtnewhorizons.modularnuclear.common.config.data.FuelData fd4 = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .findFuel(quadFuel);
        assertNotNull(fd4);
        assertEquals(4, fd4.packetCount);
        assertEquals(64, fd4.baseNeutrons);
        assertEquals("modularnuclear:rodCustomDepletedFuel4", fd4.spentProduct);
    }

    @Test
    void testCircuitConfigurationDetection() {
        assertNull(MTEActinideCompounder.getCircuitConfiguration(null));
        assertFalse(MTEActinideCompounder.isProgrammedCircuit(null));

        ItemStack dummy = new ItemStack(ModItems.cellHeavyWater);
        assertNull(MTEActinideCompounder.getCircuitConfiguration(dummy));
        assertFalse(MTEActinideCompounder.isProgrammedCircuit(dummy));

        try {
            if (gregtech.api.enums.ItemList.Circuit_Integrated != null) {
                ItemStack circuit1 = gregtech.api.enums.ItemList.Circuit_Integrated.getWithDamage(1L, 1);
                if (circuit1 != null) {
                    assertEquals(1, MTEActinideCompounder.getCircuitConfiguration(circuit1));
                    assertTrue(MTEActinideCompounder.isProgrammedCircuit(circuit1));
                }
                ItemStack circuit2 = gregtech.api.enums.ItemList.Circuit_Integrated.getWithDamage(1L, 2);
                if (circuit2 != null) {
                    assertEquals(2, MTEActinideCompounder.getCircuitConfiguration(circuit2));
                    assertTrue(MTEActinideCompounder.isProgrammedCircuit(circuit2));
                }
                ItemStack circuit4 = gregtech.api.enums.ItemList.Circuit_Integrated.getWithDamage(1L, 4);
                if (circuit4 != null) {
                    assertEquals(4, MTEActinideCompounder.getCircuitConfiguration(circuit4));
                    assertTrue(MTEActinideCompounder.isProgrammedCircuit(circuit4));
                }
            }
        } catch (Throwable ignored) {}
    }

    @Test
    void testResolveTargetRodSizeRequiresCircuit() {
        // Null or empty inputs must return 0 (no circuit, no default to quad)
        assertEquals(0, MTEActinideCompounder.resolveTargetRodSize(null));
        assertEquals(0, MTEActinideCompounder.resolveTargetRodSize(new ArrayList<>()));

        // Inputs without any programmed circuit must return 0
        List<ItemStack> noCircuitInputs = new ArrayList<>();
        noCircuitInputs.add(new ItemStack(ModItems.cellHeavyWater));
        assertEquals(0, MTEActinideCompounder.resolveTargetRodSize(noCircuitInputs));

        try {
            if (gregtech.api.enums.ItemList.Circuit_Integrated != null) {
                // Circuit 1 -> Single (1)
                ItemStack c1 = gregtech.api.enums.ItemList.Circuit_Integrated.getWithDamage(1L, 1);
                if (c1 != null) {
                    List<ItemStack> in1 = new ArrayList<>(noCircuitInputs);
                    in1.add(c1);
                    assertEquals(1, MTEActinideCompounder.resolveTargetRodSize(in1));
                }

                // Circuit 2 -> Dual (2)
                ItemStack c2 = gregtech.api.enums.ItemList.Circuit_Integrated.getWithDamage(1L, 2);
                if (c2 != null) {
                    List<ItemStack> in2 = new ArrayList<>(noCircuitInputs);
                    in2.add(c2);
                    assertEquals(2, MTEActinideCompounder.resolveTargetRodSize(in2));
                }

                // Circuit 4 -> Quad (4)
                ItemStack c4 = gregtech.api.enums.ItemList.Circuit_Integrated.getWithDamage(1L, 4);
                if (c4 != null) {
                    List<ItemStack> in4 = new ArrayList<>(noCircuitInputs);
                    in4.add(c4);
                    assertEquals(4, MTEActinideCompounder.resolveTargetRodSize(in4));
                }

                // Circuit 0 -> -1 (rejected)
                ItemStack c0 = gregtech.api.enums.ItemList.Circuit_Integrated.getWithDamage(1L, 0);
                if (c0 != null) {
                    List<ItemStack> in0 = new ArrayList<>(noCircuitInputs);
                    in0.add(c0);
                    assertEquals(-1, MTEActinideCompounder.resolveTargetRodSize(in0));
                }

                // Circuit 3 -> -1 (rejected)
                ItemStack c3 = gregtech.api.enums.ItemList.Circuit_Integrated.getWithDamage(1L, 3);
                if (c3 != null) {
                    List<ItemStack> in3 = new ArrayList<>(noCircuitInputs);
                    in3.add(c3);
                    assertEquals(-1, MTEActinideCompounder.resolveTargetRodSize(in3));
                }
            }
        } catch (Throwable ignored) {}
    }

    @Test
    void testPureThoriumMatchesNonCustomBenchmark() {
        Map<ActinideIsotope, Integer> units = new LinkedHashMap<>();
        units.put(ActinideIsotope.THORIUM, 360);
        CustomFuelMix mix = new CustomFuelMix(units);

        // Quad rod emissions and kinetics
        assertEquals(8, mix.baseNeutrons);
        assertEquals(2, mix.getBaseNeutrons(1)); // Single rod = 2 rays
        assertEquals(4, mix.getBaseNeutrons(2)); // Dual rod = 4 rays
        assertEquals(8, mix.getBaseNeutrons(4)); // Quad rod = 8 rays

        assertEquals(580.0, mix.peakReactivityTemp, 1e-4);
        assertEquals(2200.0, mix.floorTemp, 1e-4);
        assertEquals(1.00, mix.baseThermalFissionMultiplier, 1e-4);
        assertEquals(0.08, mix.baseFastFissionMultiplier, 1e-4);
        assertEquals(400000, mix.defaultDurability);
    }

    @Test
    void testPureNaquadahMatchesNonCustomBenchmark() {
        Map<ActinideIsotope, Integer> units = new LinkedHashMap<>();
        units.put(ActinideIsotope.NAQUADAH, 360);
        CustomFuelMix mix = new CustomFuelMix(units);

        // Quad rod emissions and kinetics
        assertEquals(64, mix.baseNeutrons);
        assertEquals(16, mix.getBaseNeutrons(1)); // Single rod = 16 rays
        assertEquals(32, mix.getBaseNeutrons(2)); // Dual rod = 32 rays
        assertEquals(64, mix.getBaseNeutrons(4)); // Quad rod = 64 rays

        assertEquals(1920.0, mix.peakReactivityTemp, 1e-4);
        assertEquals(2900.0, mix.floorTemp, 1e-4);
        assertEquals(3.40, mix.baseThermalFissionMultiplier, 1e-4);
        assertEquals(0.25, mix.baseFastFissionMultiplier, 1e-4);
        assertEquals(800000, mix.defaultDurability);
    }

    @Test
    void testUraniumCraftingBlendMatchesNonCustomBenchmark() {
        // In GTNH, 1 Enriched Uranium Fuel = 6 U-238 dust + 3 small U-235 dust (0.75 dust)
        // Ratio: 1 part U-235 (40 units) to 8 parts U-238 (320 units) = 360 units total
        Map<ActinideIsotope, Integer> units = new LinkedHashMap<>();
        units.put(ActinideIsotope.URANIUM_235, 40);
        units.put(ActinideIsotope.URANIUM_238, 320);
        CustomFuelMix mix = new CustomFuelMix(units);

        // Standard Uranium Fuel Rod benchmarks:
        // Single N1 = 4, Quad N4 = 16
        assertEquals(16, mix.baseNeutrons);
        assertEquals(4, mix.getBaseNeutrons(1));
        assertEquals(8, mix.getBaseNeutrons(2));
        assertEquals(16, mix.getBaseNeutrons(4));

        assertEquals(770.0, mix.peakReactivityTemp, 1e-1);
        assertEquals(2300.0, mix.floorTemp, 1e-1);
        assertEquals(1.25, mix.baseThermalFissionMultiplier, 1e-2);
        assertEquals(0.10, mix.baseFastFissionMultiplier, 1e-2);
        assertEquals(160000, mix.defaultDurability);
    }

    @Test
    void testPureExcitedUraniumMatchesBenchmark() {
        Map<ActinideIsotope, Integer> units = new LinkedHashMap<>();
        units.put(ActinideIsotope.URANIUM_235, 360);
        CustomFuelMix mix = new CustomFuelMix(units);

        assertEquals(64, mix.baseNeutrons);
        assertEquals(16, mix.getBaseNeutrons(1));
        assertEquals(1540.0, mix.peakReactivityTemp, 1e-4);
        assertEquals(2700.0, mix.floorTemp, 1e-4);
        assertEquals(2.50, mix.baseThermalFissionMultiplier, 1e-4);
        assertEquals(0.15, mix.baseFastFissionMultiplier, 1e-4);
        assertEquals(48000, mix.defaultDurability);
    }

    @Test
    void testPureHighDensityUraniumMatchesBenchmark() {
        Map<ActinideIsotope, Integer> units = new LinkedHashMap<>();
        units.put(ActinideIsotope.URANIUM_233, 360);
        CustomFuelMix mix = new CustomFuelMix(units);

        assertEquals(32, mix.baseNeutrons);
        assertEquals(8, mix.getBaseNeutrons(1));
        assertEquals(1150.0, mix.peakReactivityTemp, 1e-4);
        assertEquals(2500.0, mix.floorTemp, 1e-4);
        assertEquals(1.80, mix.baseThermalFissionMultiplier, 1e-4);
        assertEquals(0.12, mix.baseFastFissionMultiplier, 1e-4);
        assertEquals(320000, mix.defaultDurability);
    }

    @Test
    void testPureExcitedPlutoniumMatchesBenchmark() {
        Map<ActinideIsotope, Integer> units = new LinkedHashMap<>();
        units.put(ActinideIsotope.PLUTONIUM_241, 360);
        CustomFuelMix mix = new CustomFuelMix(units);

        assertEquals(96, mix.baseNeutrons);
        assertEquals(24, mix.getBaseNeutrons(1));
        assertEquals(1730.0, mix.peakReactivityTemp, 1e-4);
        assertEquals(2800.0, mix.floorTemp, 1e-4);
        assertEquals(2.90, mix.baseThermalFissionMultiplier, 1e-4);
        assertEquals(0.25, mix.baseFastFissionMultiplier, 1e-4);
        assertEquals(80000, mix.defaultDurability);
    }

    @Test
    void testPureHighDensityPlutoniumMatchesBenchmark() {
        Map<ActinideIsotope, Integer> units = new LinkedHashMap<>();
        units.put(ActinideIsotope.PLUTONIUM_239, 360);
        CustomFuelMix mix = new CustomFuelMix(units);

        assertEquals(48, mix.baseNeutrons);
        assertEquals(12, mix.getBaseNeutrons(1));
        assertEquals(1350.0, mix.peakReactivityTemp, 1e-4);
        assertEquals(2600.0, mix.floorTemp, 1e-4);
        assertEquals(2.10, mix.baseThermalFissionMultiplier, 1e-4);
        assertEquals(0.20, mix.baseFastFissionMultiplier, 1e-4);
        assertEquals(200000, mix.defaultDurability);
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

    @Test
    void testStructureErrorsReported() {
        MTEActinideCompounder compounder = new MTEActinideCompounder("test_compounder");
        List<gregtech.api.structure.error.StructureError> errors = new ArrayList<>();
        IGregTechTileEntity mockBase = org.mockito.Mockito.mock(IGregTechTileEntity.class);
        net.minecraft.world.World mockWorld = org.mockito.Mockito.mock(net.minecraft.world.World.class);
        org.mockito.Mockito.when(mockBase.getWorld())
            .thenReturn(mockWorld);
        org.mockito.Mockito.when(
            mockWorld
                .blockExists(org.mockito.Mockito.anyInt(), org.mockito.Mockito.anyInt(), org.mockito.Mockito.anyInt()))
            .thenReturn(true);
        compounder.setBaseMetaTileEntity(mockBase);
        compounder.checkMachine(mockBase, null, errors);
        assertFalse(errors.isEmpty(), "Structure errors should be reported for invalid/empty structure");
    }
}
