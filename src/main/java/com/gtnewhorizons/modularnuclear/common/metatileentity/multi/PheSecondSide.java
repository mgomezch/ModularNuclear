package com.gtnewhorizons.modularnuclear.common.metatileentity.multi;

import java.util.Set;

import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantFluidProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.ICoolantLoopPump;
import com.gtnewhorizons.modularnuclear.common.coolantloop.IInlineLoopDevice;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopSegment;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchPressurizedFluid;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * The second loop of a PHE in dual-loop mode: the pair of high-pressure hatches in place of its secondary hatches,
 * a loop device of its own (its own id and segment), on another loop or on the same one. See Note [PHE gas modes]
 */
public final class PheSecondSide implements IInlineLoopDevice {

    private final MTEPressurizedHeatExchanger phe;
    ICoolantLoopPump pump;
    LoopSegment segment;
    CoolantFluidProperty fluid;
    double flowM3s;
    long lastFlowReportTick = -1;

    PheSecondSide(MTEPressurizedHeatExchanger phe) {
        this.phe = phe;
    }

    public MTEPressurizedHeatExchanger getExchanger() {
        return phe;
    }

    @Override
    public String getDeviceId() {
        return phe.getDeviceId() + "_b";
    }

    @Override
    public boolean isLoopReady() {
        return phe.mMachine && phe.isDualLoop();
    }

    @Override
    public double getLoopLengthMeters() {
        return 2.0;
    }

    @Override
    public double getLoopVolumeM3() {
        return 0.05;
    }

    @Override
    public double getLoopMaxTemperatureCelsius() {
        return phe.getMaxOperatingTemperatureCelsius();
    }

    @Override
    public MTEHatchPressurizedFluid getLoopInlet() {
        return phe.mSecondaryInlet;
    }

    @Override
    public MTEHatchPressurizedFluid getLoopOutlet() {
        return phe.mSecondaryOutlet;
    }

    @Override
    public double getMinorLossK() {
        return phe.getMinorLossK();
    }

    @Override
    public double getDeviceTemperatureCelsius() {
        return segment != null ? segment.getCurrentTemperatureCelsius() : 20.0;
    }

    @Override
    public double getMaxOperatingTemperatureCelsius() {
        return phe.getMaxOperatingTemperatureCelsius();
    }

    @Override
    public void setLoopPump(ICoolantLoopPump pump) {
        this.pump = pump;
    }

    @Override
    public long getFillVolumeLiters() {
        return 50L;
    }

    @Override
    public void processThermalExchange(double coolantFlowRateM3s, double dt, CoolantFluidProperty fluid,
        LoopSegment segment) {
        this.segment = segment;
        this.fluid = fluid;
        this.flowM3s = coolantFlowRateM3s;
        IGregTechTileEntity te = phe.getBaseMetaTileEntity();
        if (te != null) lastFlowReportTick = te.getTimer();
        phe.stepHeatExchange();
    }

    @Override
    public void collectStructureChunks(Set<Long> chunks) {
        phe.collectStructureChunks(chunks);
    }
}
