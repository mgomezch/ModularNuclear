package com.gtnewhorizons.modularnuclear.common.metatileentity.compact;

import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasPressureStage;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasStageModels;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.render.TextureFactory;

/**
 * Compact Expansion Valve: a one-block thermostatic valve, holding its evaporator's outlet a few kelvin superheated.
 * It draws no power and has one mode. See Note [Compact loop machines]
 */
public class MTECompactValve extends MTECompactLoopMachine implements GasPressureStage {

    private final GasStageModels.Thermostat mThermostat = new GasStageModels.Thermostat();
    private double mOpening = 0.2;
    private double mLastMassFlow = 0.0;
    private double mLastInBar = 0.0, mLastOutBar = 0.0, mLastSuperheat = 0.0;

    public MTECompactValve(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier, 0);
    }

    public MTECompactValve(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, 0, aDescription, aTextures);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTECompactValve(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    protected String getKind() {
        return "compact_valve";
    }

    @Override
    protected boolean usesPower() {
        return false;
    }

    @Override
    protected ITexture frontOverlay(boolean active) {
        return TextureFactory.of(Textures.BlockIcons.OVERLAY_VALVE);
    }

    @Override
    public void computeStage(GasPressureStage.Context c) {
        mLastInBar = c.pressureIn / 1e5;
        mLastOutBar = c.pressureOut / 1e5;
        mLastSuperheat = c.downstreamExitSuperheatK;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) {
            c.clearAnswers();
            mLastMassFlow = 0.0;
            return;
        }
        if (!base.isAllowedToWork()) {
            mOpening = 0.0;
            mStatus = "Closed";
        } else {
            // Stopped, it bleeds the high side down so the loop equalizes and comes to rest
            mOpening = c.loopRunning ? mThermostat.opening(c.downstreamExitSuperheatK, c.dt) : 1.0;
            mStatus = c.loopRunning ? "Regulating" : "Open: the loop is equalizing";
        }
        new GasStageModels.Valve(tier().valveArea).compute(c, mOpening);
        mLastMassFlow = c.massFlow;
        base.setActive(c.massFlow > 0.0);
    }

    @Override
    protected void addReadoutLines(List<String> l) {
        l.add(String.format("Opening: %.1f%%, %.1f kg/s", mOpening * 100.0, mLastMassFlow));
        l.add(String.format("%.2f bar -> %.2f bar, superheat after %.1f K", mLastInBar, mLastOutBar, mLastSuperheat));
    }

    @Override
    protected void addTooltipLines(List<String> lines) {
        lines.add(StatCollector.translateToLocal("modularnuclear.compact.compact_valve.tooltip"));
        lines.add(StatCollector.translateToLocal("modularnuclear.compact.compact_valve.tooltip.stats"));
    }

    @Override
    public void saveNBTData(NBTTagCompound nbt) {
        super.saveNBTData(nbt);
        nbt.setDouble("mOpening", mOpening);
        nbt.setDouble("mBias", mThermostat.bias);
    }

    @Override
    public void loadNBTData(NBTTagCompound nbt) {
        super.loadNBTData(nbt);
        mOpening = nbt.getDouble("mOpening");
        if (nbt.hasKey("mBias")) mThermostat.bias = nbt.getDouble("mBias");
    }
}
