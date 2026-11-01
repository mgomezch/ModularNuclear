package com.gtnewhorizons.modularnuclear.common.coolantloop;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.tileentity.TileEntityLoopInstrument;

class LoopInstrumentTest {

    @Test
    void testThermometerRedstoneScaling() {
        TileEntityLoopInstrument thermometer = new TileEntityLoopInstrument();
        thermometer.setTrackedMetric(TileEntityLoopInstrument.TrackedMetric.TEMPERATURE);

        LoopSegment coldSeg = new LoopSegment("cold", 1.0, 0.1, 0.000045, 0.05, 50.0, 1000.0, 20.0);
        thermometer.processThermalExchange(0.01, 1.0, CoolantFluidProperty.WATER, coldSeg);
        assertEquals(0, thermometer.getRedstoneOutput(), "20 C should produce 0 Redstone");

        LoopSegment warmSeg = new LoopSegment("warm", 1.0, 0.1, 0.000045, 0.05, 50.0, 1000.0, 500.0);
        thermometer.processThermalExchange(0.01, 1.0, CoolantFluidProperty.WATER, warmSeg);
        assertEquals(7, thermometer.getRedstoneOutput(), "500 C should produce ~7 Redstone (mid-scale)");

        LoopSegment hotSeg = new LoopSegment("hot", 1.0, 0.1, 0.000045, 0.05, 50.0, 1000.0, 1000.0);
        thermometer.processThermalExchange(0.01, 1.0, CoolantFluidProperty.WATER, hotSeg);
        assertEquals(15, thermometer.getRedstoneOutput(), "1000 C should produce 15 Redstone (max-scale)");
    }

    @Test
    void testFlowMeterRedstoneScaling() {
        TileEntityLoopInstrument flowMeter = new TileEntityLoopInstrument();
        flowMeter.setTrackedMetric(TileEntityLoopInstrument.TrackedMetric.FLOW_RATE);

        // Pipe area = pi * 0.05^2 ~= 0.007854 m^2
        LoopSegment seg = new LoopSegment("pipe", 1.0, 0.1, 0.000045, 0.05, 50.0, 1000.0, 20.0);

        // 0 flow
        flowMeter.processThermalExchange(0.0, 1.0, CoolantFluidProperty.WATER, seg);
        assertEquals(0, flowMeter.getRedstoneOutput());

        // 5 m/s velocity -> Q = 5 * 0.007854 ~= 0.03927 m^3/s
        double v5Flow = 5.0 * seg.getArea();
        flowMeter.processThermalExchange(v5Flow, 1.0, CoolantFluidProperty.WATER, seg);
        assertEquals(7, flowMeter.getRedstoneOutput(), "5 m/s (50% max) should produce ~7 Redstone");

        // 10 m/s velocity -> Q = 10 * 0.007854 ~= 0.07854 m^3/s
        double v10Flow = 10.0 * seg.getArea();
        flowMeter.processThermalExchange(v10Flow, 1.0, CoolantFluidProperty.WATER, seg);
        assertEquals(15, flowMeter.getRedstoneOutput(), "10 m/s (100% max) should produce 15 Redstone");
    }

    private static void measure(TileEntityLoopInstrument instrument, double tempC) {
        LoopSegment seg = new LoopSegment("seg", 1.0, 0.1, 0.000045, 0.05, 50.0, 1000.0, tempC);
        instrument.processThermalExchange(0.01, 1.0, CoolantFluidProperty.WATER, seg);
    }

    @Test
    void testThresholdModeSwitchesWithHysteresis() {
        TileEntityLoopInstrument thermostat = new TileEntityLoopInstrument();
        thermostat.setTrackedMetric(TileEntityLoopInstrument.TrackedMetric.TEMPERATURE);
        thermostat.setThreshold(740.0, 720.0, false);

        measure(thermostat, 730.0);
        assertEquals(0, thermostat.getRedstoneOutput(), "Inside the band, starting off, it stays off");
        measure(thermostat, 741.0);
        assertEquals(15, thermostat.getRedstoneOutput(), "Above the upper setpoint it switches fully on");
        assertEquals(255, thermostat.getFineOutputStrength());
        measure(thermostat, 725.0);
        assertEquals(15, thermostat.getRedstoneOutput(), "Inside the band, once on, it stays on");
        measure(thermostat, 719.0);
        assertEquals(0, thermostat.getRedstoneOutput(), "Below the lower setpoint it switches off");
        assertEquals(0, thermostat.getFineOutputStrength());
    }

    @Test
    void testThresholdModeReachesPastProportionalScale() {
        // The proportional temperature scale saturates at 1000 C; a threshold does not
        TileEntityLoopInstrument thermostat = new TileEntityLoopInstrument();
        thermostat.setTrackedMetric(TileEntityLoopInstrument.TrackedMetric.TEMPERATURE);
        thermostat.setThreshold(1500.0, 1500.0, false);
        measure(thermostat, 1200.0);
        assertEquals(0, thermostat.getRedstoneOutput());
        measure(thermostat, 1600.0);
        assertEquals(15, thermostat.getRedstoneOutput());
    }

    @Test
    void testThresholdInvertedAndBackToProportional() {
        TileEntityLoopInstrument thermostat = new TileEntityLoopInstrument();
        thermostat.setTrackedMetric(TileEntityLoopInstrument.TrackedMetric.TEMPERATURE);
        thermostat.setThreshold(500.0, 500.0, true);
        measure(thermostat, 400.0);
        assertEquals(15, thermostat.getRedstoneOutput(), "Inverted: full output while below the setpoint");
        measure(thermostat, 600.0);
        assertEquals(0, thermostat.getRedstoneOutput(), "Inverted: zero once above it");

        thermostat.setThresholdMode(false);
        assertEquals(9, thermostat.getRedstoneOutput(), "Proportional again: 600 C of 1000 C is 9 / 15");
    }

    @Test
    void testThresholdSetpointsStayOrdered() {
        TileEntityLoopInstrument thermostat = new TileEntityLoopInstrument();
        thermostat.setThreshold(100.0, 200.0, false);
        assertEquals(100.0, thermostat.getThresholdLow(), "A lower setpoint above the upper one is taken as it");
        thermostat.setThresholdLow(300.0);
        assertEquals(300.0, thermostat.getThresholdHigh(), "Raising the lower setpoint raises the upper with it");
        thermostat.setThresholdHigh(50.0);
        assertEquals(50.0, thermostat.getThresholdLow(), "Lowering the upper setpoint lowers the lower with it");
    }

    @Test
    void testThresholdSurvivesNbt() throws Exception {
        // TileEntity.writeToNBT needs the class mapped to an id, which only happens when the mod registers it
        java.lang.reflect.Method addMapping = net.minecraft.tileentity.TileEntity.class
            .getDeclaredMethod("addMapping", Class.class, String.class);
        addMapping.setAccessible(true);
        try {
            addMapping.invoke(null, TileEntityLoopInstrument.class, "LoopInstrumentTest");
        } catch (java.lang.reflect.InvocationTargetException alreadyMapped) {}
        TileEntityLoopInstrument thermostat = new TileEntityLoopInstrument();
        thermostat.setTrackedMetric(TileEntityLoopInstrument.TrackedMetric.TEMPERATURE);
        thermostat.setThreshold(740.0, 720.0, true);
        measure(thermostat, 750.0);
        net.minecraft.nbt.NBTTagCompound nbt = new net.minecraft.nbt.NBTTagCompound();
        thermostat.writeToNBT(nbt);

        TileEntityLoopInstrument copy = new TileEntityLoopInstrument();
        copy.readFromNBT(nbt);
        assertTrue(copy.isThresholdMode());
        assertEquals(740.0, copy.getThresholdHigh());
        assertEquals(720.0, copy.getThresholdLow());
        assertTrue(copy.isThresholdInverted());
        assertTrue(copy.isThresholdOn(), "The latched state is kept, so a reload inside the band does not flip it");
    }
}
