package com.gtnewhorizons.modularnuclear.common.metatileentity.compact;

import java.util.List;

import net.minecraft.util.StatCollector;

import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantFluidProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopSegment;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.SegmentHeat;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.gas.GasMachineParts;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.gas.MTERadiator;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.render.TextureFactory;

/**
 * Compact Radiator: a one-block fan coil that rejects a loop's heat to the air around it, down to ambient, and loses
 * it. Its fan draws an eighth of an ampere of its tier. See Note [Compact loop machines]
 */
public class MTECompactRadiator extends MTECompactLoopMachine {

    private double mLastHeatWatts = 0.0;
    private long mLastEuPerTick = 0L;
    private boolean mFanRunning = false;

    public MTECompactRadiator(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier, 0);
    }

    public MTECompactRadiator(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, 0, aDescription, aTextures);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTECompactRadiator(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    protected String getKind() {
        return "compact_radiator";
    }

    @Override
    protected ITexture frontOverlay(boolean active) {
        return TextureFactory.of(
            active ? Textures.BlockIcons.OVERLAY_FRONT_VACUUM_FREEZER_ACTIVE
                : Textures.BlockIcons.OVERLAY_FRONT_VACUUM_FREEZER);
    }

    @Override
    protected void onLoopTick(double flowM3s, double dt, CoolantFluidProperty fluid, LoopSegment segment) {
        mLastHeatWatts = 0.0;
        mLastEuPerTick = 0L;
        mFanRunning = false;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (segment == null || base == null) return;
        boolean flowing = segment.holdsGas() ? segment.getGasMassFlow() > 0.0 : flowM3s > 1e-9;
        if (!flowing) {
            base.setActive(false);
            mStatus = "No flow";
            return;
        }
        CompactTier t = tier();
        double ambient = ambientK();
        double ua = t.radiatorConductance * GasMachineParts.RADIATOR_STILL_AIR;
        double duty = CompactTier.fanDuty(segment.getCurrentTemperatureCelsius() + 273.15, ambient);
        long fanEu = Math.max(1L, Math.round(t.radiatorFanEu * duty));
        if (base.isAllowedToWork() && storedEnergy() >= fanEu && drainEnergy(fanEu)) {
            ua = Math.max(ua, t.radiatorConductance * duty);
            mFanRunning = true;
            mLastEuPerTick = fanEu;
        }
        GasProperty gas = gas();
        double heat;
        if (segment.holdsGas() && gas != null) {
            heat = -SegmentHeat.exchangeThrough(gas, segment, ambient, ua, dt);
        } else {
            heat = MTERadiator.coolLiquid(segment, fluid, flowM3s, ua, ambient - 273.15, dt);
        }
        mLastHeatWatts = heat / dt;
        mStatus = mFanRunning ? "Fan running" : "Fan stopped: still air only";
        base.setActive(mFanRunning);
    }

    // --- GUI: see Note [Compact machine GUIs]

    @Override
    protected double guiLoad() {
        return (double) mLastEuPerTick / Math.max(1L, tier().radiatorFanEu);
    }

    @Override
    protected void addReadoutLines(List<String> l) {
        l.add(
            String.format(
                "Heat rejected: %sW to air at %.1f °C",
                GasMachineParts.si(mLastHeatWatts),
                ambientK() - 273.15));
        l.add(String.format("Fan: %d EU/t (%d at full speed)", mLastEuPerTick, tier().radiatorFanEu));
    }

    @Override
    protected void addTooltipLines(List<String> lines) {
        CompactTier t = tier();
        lines.add(StatCollector.translateToLocal("modularnuclear.compact.compact_radiator.tooltip"));
        lines.add(
            StatCollector.translateToLocalFormatted(
                "modularnuclear.compact.compact_radiator.tooltip.stats",
                GasMachineParts.si(t.radiatorConductance),
                t.radiatorFanEu));
    }

    public double getLastHeatWatts() {
        return mLastHeatWatts;
    }
}
