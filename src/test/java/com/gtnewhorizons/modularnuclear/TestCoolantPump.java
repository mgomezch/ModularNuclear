package com.gtnewhorizons.modularnuclear;

import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTECoolantPump;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.util.CoolantPumpReservoir;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * A coolant pump for tests without a world: its reservoir is an in-memory tank (100000 L, unlimited capacity unless
 * set), and its biome and pumped-fluid temperatures can be set. In a world it behaves as the real pump.
 */
public class TestCoolantPump extends MTECoolantPump {

    private Double fluidTemperatureCelsius;
    private double biomeTemperatureCelsius = 20.0;

    public TestCoolantPump(String aName) {
        super(aName);
    }

    @Override
    protected CoolantPumpReservoir createReservoir() {
        return new FakeReservoir(this);
    }

    @Override
    public FakeReservoir getReservoir() {
        return (FakeReservoir) super.getReservoir();
    }

    public long getSimulatedCoolantLiters() {
        return getReservoir().coolantLiters;
    }

    public void setSimulatedCoolantLiters(long liters) {
        getReservoir().coolantLiters = liters;
    }

    public void setSimulatedTankCapacityLiters(long liters) {
        getReservoir().capacityLiters = liters;
    }

    /** The temperature the pump sees in its fluid, in place of its loop's; null for the loop's. */
    public void setSimulatedFluidTemperatureCelsius(Double temp) {
        this.fluidTemperatureCelsius = temp;
    }

    public void setSimulatedBiomeTemperatureCelsius(double temp) {
        this.biomeTemperatureCelsius = temp;
    }

    @Override
    public double getPumpFluidTemperatureCelsius() {
        return fluidTemperatureCelsius != null ? fluidTemperatureCelsius : super.getPumpFluidTemperatureCelsius();
    }

    @Override
    public double getBiomeTemperatureCelsius() {
        return inWorld() ? super.getBiomeTemperatureCelsius() : biomeTemperatureCelsius;
    }

    boolean inWorld() {
        IGregTechTileEntity te = getBaseMetaTileEntity();
        return te != null && te.getWorld() != null;
    }

    /** The reservoir's in-memory tank, used while the pump has no world. */
    public static class FakeReservoir extends CoolantPumpReservoir {

        private final TestCoolantPump pump;
        long coolantLiters = 100000L;
        /** 0 for unlimited. */
        long capacityLiters = 0L;

        FakeReservoir(TestCoolantPump pump) {
            super(pump);
            this.pump = pump;
        }

        public void setSimulatedCoolantLiters(long liters) {
            coolantLiters = liters;
        }

        public void setSimulatedTankCapacityLiters(long liters) {
            capacityLiters = liters;
        }

        @Override
        public long getTotalCoolantLiters() {
            return pump.inWorld() ? super.getTotalCoolantLiters() : coolantLiters;
        }

        @Override
        public long getTotalTankCapacityLiters() {
            return capacityLiters > 0 ? capacityLiters : super.getTotalTankCapacityLiters();
        }

        @Override
        public boolean verifyRailcraftTankAbove(IGregTechTileEntity base) {
            if (base != null && base.getWorld() == null) return coolantLiters > 0;
            return super.verifyRailcraftTankAbove(base);
        }

        @Override
        protected int drainFromReservoirInternal(int liters) {
            if (pump.inWorld() || liters <= 0) return super.drainFromReservoirInternal(liters);
            int drained = (int) Math.min(liters, coolantLiters);
            coolantLiters -= drained;
            return drained;
        }

        @Override
        protected int fillIntoReservoirInternal(int liters) {
            if (pump.inWorld() || liters <= 0) return super.fillIntoReservoirInternal(liters);
            int filled = capacityLiters > 0 ? (int) Math.min(liters, Math.max(0, capacityLiters - coolantLiters))
                : liters;
            coolantLiters += filled;
            return filled;
        }

        @Override
        public boolean isReservoirFull() {
            if (pump.inWorld()) return super.isReservoirFull();
            return capacityLiters > 0 && coolantLiters >= capacityLiters;
        }
    }
}
