package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantFluidProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopSegment;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHighPressure;

import gregtech.api.interfaces.ITexture;

public class NuclearHighPressureHatchEnergyConservationTest {

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
    }

    @Test
    void testStrictEnergyConservationBetweenCoreAndLoop() {
        MTEHatchNuclearHighPressure hatch = new MTEHatchNuclearHighPressure(
            "test_passage",
            4,
            new String[0],
            new ITexture[0][0][0]);
        hatch.setTemperature(800.0); // 800 °C core

        // Create a 5m passage segment (0.1m diameter) with water at 100 °C
        LoopSegment segment = new LoopSegment("core_passage_test", 5.0, 0.10, 0.000045, 2.5, 500.0, 3200.0, 100.0);
        CoolantFluidProperty fluid = CoolantFluidProperty.WATER;

        double initialCoreTemp = hatch.getTemperature();
        double initialCoolantTemp = segment.getCurrentTemperatureCelsius();

        double flowRateM3s = 0.1; // 100 L/s
        double dtSeconds = 0.05; // 1 tick

        // Process thermal exchange for 1 tick
        hatch.processThermalExchange(flowRateM3s, dtSeconds, fluid, segment);

        double finalCoreTemp = hatch.getTemperature();
        double finalCoolantTemp = segment.getCurrentTemperatureCelsius();

        double deltaTCoolant = finalCoolantTemp - initialCoolantTemp;
        double deltaTCore = initialCoreTemp - finalCoreTemp;

        assertTrue(deltaTCoolant > 0, "Coolant should warm up");
        assertTrue(deltaTCore > 0, "Core should cool down");

        // Energy lost by core: Q_core = deltaTCore * C_core
        double coreHeatCapacityJoulesPerC = hatch.getHeatCapacity() * 128.0;
        double energyLostByCoreJoules = deltaTCore * coreHeatCapacityJoulesPerC;

        // Energy gained by coolant: Q_coolant = deltaTCoolant * C_segment
        double segmentMassKg = segment.getVolume() * fluid.getDensity();
        double segmentHeatCapacityJoulesPerC = segmentMassKg * fluid.getSpecificHeat();
        double energyGainedByCoolantJoules = deltaTCoolant * segmentHeatCapacityJoulesPerC;

        // Strict energy conservation: Q_core == Q_coolant
        assertEquals(
            energyLostByCoreJoules,
            energyGainedByCoolantJoules,
            1e-6,
            "Energy removed from reactor core must exactly equal energy gained by coolant passage");

        // Verify accumulator in Hu matches energy / 128
        assertEquals(
            energyLostByCoreJoules / 128.0,
            hatch.mHeatOutputAccumulator,
            1e-6,
            "Hatch heat accumulator must record exact Hu extracted");
    }

    @Test
    void testEquilibriumNoOvershoot() {
        MTEHatchNuclearHighPressure hatch = new MTEHatchNuclearHighPressure(
            "test_passage_eq",
            4,
            new String[0],
            new ITexture[0][0][0]);
        hatch.setTemperature(300.0);

        LoopSegment segment = new LoopSegment("core_passage_eq", 0.01, 0.01, 0.000045, 2.5, 500.0, 3200.0, 20.0);
        CoolantFluidProperty fluid = CoolantFluidProperty.WATER;

        // Very high flow and large dt to test overshoot clamping
        hatch.processThermalExchange(10.0, 1.0, fluid, segment);

        assertTrue(
            hatch.getTemperature() >= segment.getCurrentTemperatureCelsius(),
            "Core temperature should not drop below coolant temperature");
        assertTrue(
            segment.getCurrentTemperatureCelsius() >= 20.0,
            "Coolant temperature should not decrease when heated");
    }
}
