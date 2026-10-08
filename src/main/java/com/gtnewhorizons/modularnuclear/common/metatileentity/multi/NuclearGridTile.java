package com.gtnewhorizons.modularnuclear.common.metatileentity.multi;

import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearBus;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHatch;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHighPressure;
import com.gtnewhorizons.modularnuclear.common.nuclear.INuclearTile;
import com.gtnewhorizons.modularnuclear.common.nuclear.NeutronType;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearFuelType;

public class NuclearGridTile implements INuclearTile {

    private final MTENuclearReactor reactor;
    private final MTEHatchNuclearBus bus;
    private final MTEHatchNuclearHatch hatch;
    private final MTEHatchNuclearHighPressure highPressureHatch;
    private MTEHatchNuclearControlRod bottomControlRod;
    private final int gx;
    private final int gy;

    public NuclearGridTile(MTENuclearReactor reactor, MTEHatchNuclearBus bus, int gx, int gy) {
        this.reactor = reactor;
        this.bus = bus;
        this.hatch = null;
        this.highPressureHatch = null;
        this.gx = gx;
        this.gy = gy;
    }

    public NuclearGridTile(MTENuclearReactor reactor, MTEHatchNuclearHatch hatch, int gx, int gy) {
        this.reactor = reactor;
        this.bus = null;
        this.hatch = hatch;
        this.highPressureHatch = null;
        this.gx = gx;
        this.gy = gy;
    }

    public NuclearGridTile(MTENuclearReactor reactor, MTEHatchNuclearControlRod controlRod, int gx, int gy) {
        this.reactor = reactor;
        this.bus = null;
        this.hatch = null;
        this.bottomControlRod = controlRod;
        this.highPressureHatch = null;
        this.gx = gx;
        this.gy = gy;
    }

    public NuclearGridTile(MTENuclearReactor reactor, MTEHatchNuclearHighPressure highPressureHatch, int gx, int gy) {
        this.reactor = reactor;
        this.bus = null;
        this.hatch = null;
        this.highPressureHatch = highPressureHatch;
        this.gx = gx;
        this.gy = gy;
    }

    public MTENuclearReactor getReactor() {
        return reactor;
    }

    public boolean hasControlRod() {
        return bottomControlRod != null;
    }

    public MTEHatchNuclearControlRod getBottomControlRod() {
        return bottomControlRod;
    }

    public void setBottomControlRod(MTEHatchNuclearControlRod bottomControlRod) {
        this.bottomControlRod = bottomControlRod;
    }

    public boolean isBus() {
        return bus != null;
    }

    public boolean isHatch() {
        return hatch != null;
    }

    public boolean isControlRod() {
        return bottomControlRod != null && bus == null && hatch == null && highPressureHatch == null;
    }

    public boolean isHighPressureHatch() {
        return highPressureHatch != null;
    }

    public MTEHatchNuclearBus getBus() {
        return bus;
    }

    public MTEHatchNuclearHatch getHatch() {
        return hatch;
    }

    public MTEHatchNuclearControlRod getControlRod() {
        return bottomControlRod;
    }

    public MTEHatchNuclearHighPressure getHighPressureHatch() {
        return highPressureHatch;
    }

    public double getHeatOutput() {
        if (isHatch()) return hatch.mLastHeatOutput;
        if (isHighPressureHatch()) return highPressureHatch.mLastHeatOutput;
        if (isBus()) return bus.mLastHeatOutput;
        return 0.0;
    }

    public int getGx() {
        return gx;
    }

    public int getGy() {
        return gy;
    }

    @Override
    public double getTemperature() {
        return reactor.getTileTemperature(this);
    }

    @Override
    public void setTemperature(double temp) {
        reactor.setTileTemperature(this, temp);
    }

    @Override
    public void addHeat(double heatEU) {
        reactor.addTileHeat(this, heatEU);
    }

    @Override
    public double getHeatTransferCoeff() {
        return reactor.getTileHeatTransferCoeff(this);
    }

    @Override
    public boolean isFuel() {
        return reactor.isTileFuel(this);
    }

    @Override
    public NuclearFuelType getFuelType() {
        return reactor.getTileFuelType(this);
    }

    @Override
    public int generateNeutrons(double efficiency) {
        return reactor.generateTileNeutrons(this, efficiency);
    }

    @Override
    public int getNeutronEmissionCount() {
        return reactor.getTileNeutronEmissionCount(this);
    }

    @Override
    public double getAbsorptionProbability(NeutronType type) {
        return reactor.getTileAbsorptionProbability(this, type);
    }

    @Override
    public double getScatteringProbability(NeutronType type) {
        return reactor.getTileScatteringProbability(this, type);
    }

    @Override
    public double getModerationProbability() {
        return reactor.getTileModerationProbability(this);
    }

    @Override
    public void onNeutronAbsorbed(NeutronType type, int count) {
        reactor.onTileNeutronAbsorbed(this, type, count);
    }

    @Override
    public void onNeutronScattered(NeutronType type, int count) {
        reactor.onTileNeutronScattered(this, type, count);
    }

    @Override
    public void addNeutronFlux(NeutronType type, int count) {
        reactor.addTileNeutronFlux(this, type, count);
    }

    @Override
    public void nuclearTick(double efficiency) {
        reactor.processTileNuclearTick(this, efficiency);
    }

    @Override
    public double getInsulationDampening() {
        return reactor.getTileInsulationDampening(this);
    }
}
