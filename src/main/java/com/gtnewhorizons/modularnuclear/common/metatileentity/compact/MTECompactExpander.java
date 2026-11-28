package com.gtnewhorizons.modularnuclear.common.metatileentity.compact;

import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.drawable.UITexture;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasPressureStage;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasStageModels;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.gas.GasMachineParts;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.api.render.TextureFactory;

/**
 * Compact Turbo-Expander: a one-block expander for permanent-gas (Brayton) loops. The gas does work as it expands and
 * leaves colder than a valve would let it; the work comes out of the machine's back as EU, up to one ampere of its
 * tier. See Note [Compact loop machines]
 */
public class MTECompactExpander extends MTECompactLoopMachine implements GasPressureStage {

    private double mEuCredit = 0.0;
    private double mLastMassFlow = 0.0, mLastWorkWatts = 0.0;
    private double mLastInBar = 0.0, mLastOutBar = 0.0;
    private long mLastEuPerTick = 0L;

    public MTECompactExpander(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier, 0);
    }

    public MTECompactExpander(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, 0, aDescription, aTextures);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTECompactExpander(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    protected String getKind() {
        return "compact_expander";
    }

    @Override
    protected ITexture frontOverlay(boolean active) {
        return TextureFactory
            .of(active ? Textures.BlockIcons.LARGETURBINE_NEW_ACTIVE5 : Textures.BlockIcons.LARGETURBINE_NEW5);
    }

    // It gives power out of its back and takes none in

    @Override
    protected boolean usesPower() {
        return false;
    }

    @Override
    public boolean isElectric() {
        return true;
    }

    /** It charges the power slot's item, whenever it holds power. See Note [The compact machines' power slot] */
    @Override
    public boolean hasPowerSlot() {
        return true;
    }

    @Override
    public int rechargerSlotCount() {
        return 1;
    }

    @Override
    public boolean isEnetOutput() {
        return true;
    }

    @Override
    public boolean isOutputFacing(ForgeDirection side) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        return base != null && side == base.getBackFacing();
    }

    @Override
    public long maxEUOutput() {
        return GTValues.V[mTier];
    }

    @Override
    public long maxAmperesOut() {
        return 1L;
    }

    @Override
    public long maxEUStore() {
        return GTValues.V[mTier] * 64L;
    }

    @Override
    public void computeStage(GasPressureStage.Context c) {
        mLastInBar = c.pressureIn / 1e5;
        mLastOutBar = c.pressureOut / 1e5;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) {
            c.clearAnswers();
            mLastMassFlow = 0.0;
            return;
        }
        CompactTier t = tier();
        GasStageModels.Expander model = new GasStageModels.Expander(t.expanderNozzle, t.expanderEfficiency);
        // Loaded while it works: its brake takes what it can't put out
        boolean loaded = base.isAllowedToWork() && c.loopRunning;
        model.compute(c, loaded);
        double watts = -c.shaftPowerWatts;
        double eu = watts * GasMachineParts.EXPANDER_GENERATOR_EFFICIENCY / (GasMachineParts.SHAFT_JOULES_PER_EU * 20.0)
            + mEuCredit;
        long whole = Math.min((long) Math.floor(eu), t.euPerTick);
        mEuCredit = Math.min(1.0, eu - whole);
        long room = Math.max(0L, maxEUStore() - base.getStoredEU());
        if (whole > room) whole = room;
        if (whole > 0) base.increaseStoredEnergyUnits(whole, true);
        mLastMassFlow = c.massFlow;
        mLastWorkWatts = watts;
        mLastEuPerTick = whole;
        mStatus = !loaded ? "Free-wheeling" : c.massFlow > 0.0 ? "Expanding" : "No flow";
        base.setActive(loaded && c.massFlow > 0.0);
    }

    // --- GUI: see Note [Compact machine GUIs]

    @Override
    public UITexture getGuiProgressTexture() {
        return GTGuiTextures.PROGRESSBAR_ARROW_MULTIPLE;
    }

    @Override
    protected double guiLoad() {
        return (double) mLastEuPerTick / Math.max(1L, tier().euPerTick);
    }

    @Override
    protected double guiInletBar() {
        return mLastInBar;
    }

    @Override
    protected void addReadoutLines(List<String> l) {
        l.add(String.format("%.2f bar -> %.2f bar, %.1f kg/s", mLastInBar, mLastOutBar, mLastMassFlow));
        l.add(
            String.format(
                "Work: %sW, %d EU/t out (max %d)",
                GasMachineParts.si(mLastWorkWatts),
                mLastEuPerTick,
                tier().euPerTick));
    }

    @Override
    protected void addTooltipLines(List<String> lines) {
        CompactTier t = tier();
        lines.add(StatCollector.translateToLocal("modularnuclear.compact.compact_expander.tooltip"));
        lines.add(
            StatCollector.translateToLocalFormatted(
                "modularnuclear.compact.compact_expander.tooltip.stats",
                t.expanderEfficiency * 100.0,
                t.euPerTick));
    }

    @Override
    public void saveNBTData(NBTTagCompound nbt) {
        super.saveNBTData(nbt);
        nbt.setDouble("mEuCredit", mEuCredit);
    }

    @Override
    public void loadNBTData(NBTTagCompound nbt) {
        super.loadNBTData(nbt);
        mEuCredit = nbt.getDouble("mEuCredit");
    }
}
