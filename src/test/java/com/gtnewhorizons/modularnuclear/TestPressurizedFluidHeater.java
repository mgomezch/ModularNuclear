package com.gtnewhorizons.modularnuclear;

import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTEPressurizedFluidHeater;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * A pressurized fluid heater for tests without a world: an in-memory energy buffer, an energy hatch tier, an ambient
 * temperature and an energy detector cover can be set in place of the real hatches, biome and covers.
 */
public class TestPressurizedFluidHeater extends MTEPressurizedFluidHeater {

    /** Negative to use the energy hatches. */
    private long storedEU = -1L;
    /** Negative to use the energy hatches. */
    private int energyHatchTier = -1;
    private double ambientTemperatureCelsius = 20.0;
    private ForgeDirection detectorSide = ForgeDirection.UNKNOWN;
    private EnergyDetector detector;

    public TestPressurizedFluidHeater(String aName) {
        super(aName);
    }

    public void setSimulatedStoredEU(long storedEU) {
        this.storedEU = storedEU;
    }

    public long getSimulatedStoredEU() {
        return storedEU;
    }

    public void setSimulatedEnergyHatchTier(int tier) {
        this.energyHatchTier = tier;
    }

    public void setSimulatedAmbientTemperatureCelsius(double tempC) {
        this.ambientTemperatureCelsius = tempC;
    }

    public void setSimulatedEnergyDetectorCover(ForgeDirection side, long threshold, boolean inverted) {
        this.detectorSide = side != null ? side : ForgeDirection.UNKNOWN;
        this.detector = new EnergyDetector(threshold, inverted);
    }

    @Override
    public long drainUpToEnergy(long maxEU) {
        if (storedEU < 0L || maxEU <= 0L) return super.drainUpToEnergy(maxEU);
        long drained = Math.min(storedEU, maxEU);
        storedEU -= drained;
        return drained;
    }

    @Override
    public int getInstalledEnergyHatchTier() {
        return energyHatchTier >= 0 ? energyHatchTier : super.getInstalledEnergyHatchTier();
    }

    @Override
    public double getAmbientTemperatureCelsius() {
        IGregTechTileEntity te = getBaseMetaTileEntity();
        return te != null && te.getWorld() != null ? super.getAmbientTemperatureCelsius() : ambientTemperatureCelsius;
    }

    @Override
    protected EnergyDetector energyDetectorAt(ForgeDirection side) {
        if (side != null && side != ForgeDirection.UNKNOWN && side == detectorSide) return detector;
        return super.energyDetectorAt(side);
    }
}
