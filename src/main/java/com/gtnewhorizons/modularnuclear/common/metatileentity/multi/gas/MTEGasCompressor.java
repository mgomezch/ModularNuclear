package com.gtnewhorizons.modularnuclear.common.metatileentity.multi.gas;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasLoopEngine;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasPressureStage;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasStageModels;

import gregtech.api.enums.HatchElement;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.MultiblockTooltipBuilder;

/**
 * Gas Compressor: the pressure stage that drives a gas loop, raising its gas from the suction zone's pressure to the
 * discharge zone's and drawing EU for the shaft work. See Note [Gas loop machines] and Note [Gas loop pressure
 * zones]
 */
public class MTEGasCompressor extends MTEInlineLoopMachine<MTEGasCompressor> implements GasPressureStage {

    private static final int CASING_META = 1; // Clean Stainless Steel Casing
    private static IStructureDefinition<MTEGasCompressor> DEFINITION;

    /** Fraction of full speed the last step ran at. */
    private double mSpeed = 0.0;
    private double mLastShaftWatts = 0.0;
    private double mLastMassFlow = 0.0;
    private double mLastSuctionBar = 0.0, mLastDischargeBar = 0.0, mLastSuctionQuality = 1.0;
    private long mLastEuPerTick = 0L;
    private double mEuDebt = 0.0;
    private int mSluggingTicks = 0;
    private String mStatus = "Idle";

    public MTEGasCompressor(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEGasCompressor(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEGasCompressor(mName);
    }

    @Override
    public int getMinLength() {
        return 3;
    }

    @Override
    public int getMaxLength() {
        return 9;
    }

    @Override
    protected String getKind() {
        return "gas_compressor";
    }

    @Override
    protected IStructureElement<MTEGasCompressor> casingElement() {
        return StructureUtility.ofBlock(GasMachineParts.casings4(), CASING_META);
    }

    @Override
    protected IStructureElement<MTEGasCompressor> ringElement() {
        return GasMachineParts.tiered(
            GasMachineParts.casings2(),
            GasMachineParts.GEARBOX_METAS,
            (t, v) -> t.mRingTier = v,
            t -> t.mRingTier);
    }

    @Override
    protected IStructureElement<MTEGasCompressor> coreElement() {
        return GasMachineParts.tiered(
            GasMachineParts.casings2(),
            GasMachineParts.PIPE_CASING_METAS,
            (t, v) -> t.mCoreTier = v,
            t -> t.mCoreTier);
    }

    @Override
    protected int getCasingTextureIndex() {
        return GasMachineParts.textureIndex(GasMachineParts.casings4(), CASING_META);
    }

    @Override
    protected List<IHatchElement<? super MTEGasCompressor>> casingHatches() {
        return new ArrayList<>(Arrays.asList(HatchElement.Maintenance, HatchElement.Energy));
    }

    @Override
    protected void checkHatches(List<StructureError> errors) {
        checkOneMaintenanceHatch(errors);
        if (mEnergyHatches.isEmpty()) checkHasEnergyHatch(errors);
    }

    @Override
    public IStructureDefinition<MTEGasCompressor> getStructureDefinition() {
        if (DEFINITION == null) DEFINITION = buildDefinition();
        return DEFINITION;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity base, ForgeDirection side, ForgeDirection facing, int color,
        boolean active, boolean redstone) {
        ITexture casing = TextureFactory.of(GasMachineParts.casings4(), CASING_META);
        if (side != facing) return new ITexture[] { casing };
        return new ITexture[] { casing,
            TextureFactory.of(
                active ? Textures.BlockIcons.OVERLAY_FRONT_IMPLOSION_COMPRESSOR_ACTIVE
                    : Textures.BlockIcons.OVERLAY_FRONT_IMPLOSION_COMPRESSOR) };
    }

    // --- The stage ---

    /** The compressor's physics at its size, tier and maintenance. */
    public GasStageModels.Compressor model() {
        double displacement = getRingCount() * GasMachineParts.compressorDisplacementPerRing(Math.max(1, mRingTier));
        double eta = GasMachineParts.COMPRESSOR_ISENTROPIC_EFFICIENCY * Math.max(0.3, getEfficiencyFactor());
        return new GasStageModels.Compressor(displacement, eta, GasMachineParts.COMPRESSOR_CLEARANCE);
    }

    @Override
    public boolean isPassive() {
        return false;
    }

    @Override
    public void computeStage(GasPressureStage.Context c) {
        mLastSuctionBar = c.pressureIn / 1e5;
        mLastDischargeBar = c.pressureOut / 1e5;
        mLastSuctionQuality = c.in.quality;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (!mMachine || base == null || !isAllowedToWork() || !c.loopRunning) {
            stop(c, c.loopRunning ? "Disabled" : "Waiting for the loop's pump");
            return;
        }
        checkMaintenance();
        GasStageModels.Compressor m = model();
        double full = m.fullSpeedPower(c);
        long maxEu = maxInputPerTick();
        double fullEu = full / (GasMachineParts.SHAFT_JOULES_PER_EU * 20.0);
        double speed = fullEu > 0.0 ? Math.min(1.0, maxEu / fullEu) : 1.0;
        // Never more than the energy hatches hold
        double affordable = storedEnergy() + 0.0;
        if (fullEu * speed > affordable) speed = fullEu > 0.0 ? Math.max(0.0, affordable / fullEu) : speed;
        if (speed <= 0.0 && fullEu > 0.0) {
            stop(c, "Not enough power");
            return;
        }
        m.compute(c, speed);
        double eu = c.shaftPowerWatts / (GasMachineParts.SHAFT_JOULES_PER_EU * 20.0) + mEuDebt;
        long whole = (long) Math.floor(eu);
        mEuDebt = eu - whole;
        if (whole > 0 && !drainEnergyInput(whole)) {
            stop(c, "Not enough power");
            return;
        }
        mSpeed = speed;
        mLastShaftWatts = c.shaftPowerWatts;
        mLastMassFlow = c.massFlow;
        mLastEuPerTick = whole;
        base.setActive(c.massFlow > 0.0);
        // Liquid reaching the suction: slugging wears the compressor
        double uncapped = Math.min(m.displacement * speed, c.maxVolumetricFlow)
            * GasStageModels.Compressor.suctionDensity(c);
        // Held back by the engine's cutout (wrinkle (W9) of Note [Gas loop pressure zones]) near its rating
        boolean cutout = c.maxMassFlow < 0.95 * uncapped
            && c.pressureOut > 0.5 * GasLoopEngine.CUTOUT_FRACTION * c.downstreamMaxPressure;
        if (cutout) {
            mStatus = String.format("High-pressure cutout at %.1f bar", c.pressureOut / 1e5);
        } else if (c.in.quality < 0.9 && c.massFlow > 0.0) {
            mSluggingTicks++;
            if (mSluggingTicks % 200 == 0) causeMaintenanceIssue();
            mStatus = String.format("Liquid slugging (%.0f%% vapour at suction)", c.in.quality * 100.0);
        } else {
            mSluggingTicks = 0;
            mStatus = speed < 0.999 ? String.format("Running at %.0f%% speed (power-limited)", speed * 100.0)
                : "Running";
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
    public double getLoopVolumeM3() {
        return GasMachineParts.VOLUME_PER_RING_M3 * getRingCount();
    }

    @Override
    public List<String> getReadoutLines() {
        List<String> l = new ArrayList<>();
        l.add(
            String.format(
                "Displacement: %.0f m³/s (%d rings, tier %d)",
                model().displacement,
                getRingCount(),
                Math.max(1, mRingTier)));
        l.add("Status: " + mStatus);
        l.add(
            String.format(
                "Suction %.2f bar -> discharge %.2f bar (x%.2f)",
                mLastSuctionBar,
                mLastDischargeBar,
                mLastSuctionBar > 0 ? mLastDischargeBar / mLastSuctionBar : 0.0));
        l.add(String.format("Mass flow: %.1f kg/s, speed %.0f%%", mLastMassFlow, mSpeed * 100.0));
        l.add(
            String.format(
                "Shaft power: %sW, %d EU/t (max %d)",
                GasMachineParts.si(mLastShaftWatts),
                mLastEuPerTick,
                maxInputPerTick()));
        l.add(gasStateLine());
        return l;
    }

    public double getLastMassFlow() {
        return mLastMassFlow;
    }

    public long getLastEuPerTick() {
        return mLastEuPerTick;
    }

    public String getStatusLine() {
        return mStatus;
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

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gt.multiblock.gas_compressor.machine_type"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.gas_compressor.desc1"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.gas_compressor.desc2"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.gas_compressor.desc3"))
            .addSeparator()
            .beginVariableStructureBlock(3, 3, 3, 3, 3, 9, false)
            .addController(StatCollector.translateToLocal("gt.multiblock.gas_loop.structure.controller"))
            .addCasingInfoMin(
                StatCollector.translateToLocal("gt.multiblock.gas_compressor.structure.casing"),
                10,
                false)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.gas_compressor.structure.rings"),
                StatCollector.translateToLocal("gt.multiblock.gas_loop.structure.rings_pos"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.gas_loop.structure.core"),
                StatCollector.translateToLocal("gt.multiblock.gas_loop.structure.core_pos"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.gas_loop.structure.inlet"),
                StatCollector.translateToLocal("gt.multiblock.gas_loop.structure.inlet_pos"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.gas_loop.structure.outlet"),
                StatCollector.translateToLocal("gt.multiblock.gas_loop.structure.outlet_pos"))
            .addEnergyHatch(StatCollector.translateToLocal("gt.multiblock.gas_loop.structure.front_back"), 1)
            .addMaintenanceHatch(StatCollector.translateToLocal("gt.multiblock.gas_loop.structure.front_back"), 1)
            .toolTipFinisher(StatCollector.translateToLocal("gt.multiblock.modularnuclear.coolant_loop.finisher"));
        return tt;
    }
}
