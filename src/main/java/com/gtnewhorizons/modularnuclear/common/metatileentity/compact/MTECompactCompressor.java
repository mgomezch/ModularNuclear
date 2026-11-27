package com.gtnewhorizons.modularnuclear.common.metatileentity.compact;

import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasLoopEngine;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasPressureStage;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasStageModels;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.gas.GasMachineParts;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.render.TextureFactory;

/**
 * Compact Gas Compressor: the one-block pressure stage that drives a gas loop, on one ampere of its tier. See Note
 * [Compact loop machines] and Note [Gas loop pressure zones]
 */
public class MTECompactCompressor extends MTECompactLoopMachine implements GasPressureStage {

    /** Ticks of liquid at the suction before the compressor trips, and how long it then rests. */
    static final int SLUGGING_TRIP_TICKS = 100;
    static final int TRIP_REST_TICKS = 200;

    private double mSpeed = 0.0;
    private double mLastShaftWatts = 0.0;
    private double mLastMassFlow = 0.0;
    private double mLastSuctionBar = 0.0, mLastDischargeBar = 0.0;
    private long mLastEuPerTick = 0L;
    private double mEuDebt = 0.0;
    private int mSluggingTicks = 0;
    private int mTripTicks = 0;

    public MTECompactCompressor(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier, 0);
    }

    public MTECompactCompressor(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, 0, aDescription, aTextures);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTECompactCompressor(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    protected String getKind() {
        return "compact_compressor";
    }

    @Override
    protected ITexture frontOverlay(boolean active) {
        return TextureFactory.of(
            active ? Textures.BlockIcons.OVERLAY_FRONT_IMPLOSION_COMPRESSOR_ACTIVE
                : Textures.BlockIcons.OVERLAY_FRONT_IMPLOSION_COMPRESSOR);
    }

    public static GasStageModels.Compressor model(CompactTier t) {
        return new GasStageModels.Compressor(
            t.displacement,
            t.compressorEfficiency,
            GasMachineParts.COMPRESSOR_CLEARANCE,
            t.builtInRatio);
    }

    @Override
    public boolean isPassive() {
        return false;
    }

    @Override
    public void computeStage(GasPressureStage.Context c) {
        mLastSuctionBar = c.pressureIn / 1e5;
        mLastDischargeBar = c.pressureOut / 1e5;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || !base.isAllowedToWork() || !c.loopRunning) {
            stop(c, c.loopRunning ? "Disabled" : "Waiting for the loop's pump");
            return;
        }
        if (mTripTicks > 0) {
            mTripTicks--;
            stop(c, "Tripped on liquid at its suction: resting");
            return;
        }
        CompactTier t = tier();
        GasStageModels.Compressor m = model(t);
        double fullEu = m.fullSpeedPower(c) / (t.shaftJoulesPerEu() * 20.0);
        double speed = (fullEu > 0.0 ? Math.min(1.0, t.euPerTick / fullEu) : 1.0) * m.unloading(c);
        // Never more than the machine holds
        double affordable = storedEnergy();
        if (fullEu * speed > affordable) speed = fullEu > 0.0 ? Math.max(0.0, affordable / fullEu) : speed;
        if (speed <= 0.0 && fullEu > 0.0) {
            stop(c, storedEnergy() <= 0L ? "Not enough power" : "Idle: the loop is at its pressure ratio");
            return;
        }
        m.compute(c, speed);
        double eu = c.shaftPowerWatts / (t.shaftJoulesPerEu() * 20.0) + mEuDebt;
        long whole = (long) Math.floor(eu);
        mEuDebt = eu - whole;
        if (whole > 0 && !drainEnergy(whole)) {
            stop(c, "Not enough power");
            return;
        }
        mSpeed = speed;
        mLastShaftWatts = c.shaftPowerWatts;
        mLastMassFlow = c.massFlow;
        mLastEuPerTick = whole;
        base.setActive(c.massFlow > 0.0);
        double uncapped = Math.min(m.displacement * speed, c.maxVolumetricFlow)
            * GasStageModels.Compressor.suctionDensity(c);
        // Held back by the engine's cutout (wrinkle (W9) of Note [Gas loop pressure zones]) near its rating
        boolean cutout = c.maxMassFlow < 0.95 * uncapped
            && c.pressureOut > 0.5 * GasLoopEngine.CUTOUT_FRACTION * c.downstreamMaxPressure;
        if (cutout) {
            mStatus = String.format("High-pressure cutout at %.1f bar", c.pressureOut / 1e5);
        } else if (c.in.quality < 0.9 && c.massFlow > 0.0) {
            // Liquid reaching the suction: it trips rather than wear, having no maintenance
            if (++mSluggingTicks >= SLUGGING_TRIP_TICKS) {
                mSluggingTicks = 0;
                mTripTicks = TRIP_REST_TICKS;
            }
            mStatus = String.format("Liquid slugging (%.0f%% vapour at suction)", c.in.quality * 100.0);
        } else {
            mSluggingTicks = 0;
            mStatus = speed < 0.999 ? String.format("Running at %.0f%% speed", speed * 100.0) : "Running";
        }
    }

    private void stop(GasPressureStage.Context c, String why) {
        c.clearAnswers();
        mSpeed = 0.0;
        mLastShaftWatts = 0.0;
        mLastMassFlow = 0.0;
        mLastEuPerTick = 0L;
        mStatus = why;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) base.setActive(false);
    }

    @Override
    protected void addReadoutLines(List<String> l) {
        CompactTier t = tier();
        l.add(
            String.format(
                "Suction %.2f bar -> discharge %.2f bar (built-in ratio %.1f)",
                mLastSuctionBar,
                mLastDischargeBar,
                t.builtInRatio));
        l.add(String.format("Mass flow: %.1f kg/s, speed %.0f%%", mLastMassFlow, mSpeed * 100.0));
        l.add(
            String.format(
                "Shaft power: %sW, %d EU/t (max %d)",
                GasMachineParts.si(mLastShaftWatts),
                mLastEuPerTick,
                t.euPerTick));
    }

    @Override
    protected void addTooltipLines(List<String> lines) {
        CompactTier t = tier();
        lines.add(StatCollector.translateToLocal("modularnuclear.compact.compact_compressor.tooltip"));
        lines.add(
            StatCollector.translateToLocalFormatted(
                "modularnuclear.compact.compact_compressor.tooltip.stats",
                t.euPerTick,
                t.driveEfficiency * 100.0,
                t.builtInRatio));
    }

    public long getLastEuPerTick() {
        return mLastEuPerTick;
    }

    @Override
    public void saveNBTData(NBTTagCompound nbt) {
        super.saveNBTData(nbt);
        nbt.setDouble("mEuDebt", mEuDebt);
    }

    @Override
    public void loadNBTData(NBTTagCompound nbt) {
        super.loadNBTData(nbt);
        mEuDebt = nbt.getDouble("mEuDebt");
    }
}
