package com.gtnewhorizons.modularnuclear.common.metatileentity.multi;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizons.modularnuclear.common.config.CoolantLoopDataRegistry;
import com.gtnewhorizons.modularnuclear.common.config.data.CoolantDeviceData;
import com.gtnewhorizons.modularnuclear.common.coolantloop.ConvectiveHeatTransferModel;
import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantFluidProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.ICoolantLoopDevice;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopSegment;
import com.gtnewhorizons.modularnuclear.common.coolantloop.PheExchange;
import com.gtnewhorizons.modularnuclear.common.coolantloop.PheGeometry;
import com.gtnewhorizons.modularnuclear.common.gui.CoolantLoopMachineGui;
import com.gtnewhorizons.modularnuclear.common.metatileentity.CoolantStructureChannels;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchPressurizedFluid;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.structure.PheSheetmetalTier;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.structure.PressurizedHeatExchangerStructure;
import com.gtnewhorizons.modularnuclear.common.util.MultiblockStartup;
import com.gtnewhorizons.modularnuclear.common.util.StructureChunks;
import com.gtnewhorizons.modularui.api.widget.IWidgetBuilder;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.HatchElement;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputDebug;
import gregtech.api.metatileentity.implementations.MTEHatchMaintenance;
import gregtech.api.metatileentity.implementations.MTEHatchMultiInput;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gregtech.common.tileentities.machines.MTEHatchInputME;

/**
 * Pressurized Heat Exchanger Multiblock.
 * 3x3x5 Dimensions (Width 3, Height 3, Length 5).
 *
 * Transfers thermal energy from primary coolant loop to secondary fluid
 * (e.g. Distilled Water -> Steam at standard 1:160 expansion ratio).
 */
public class MTEPressurizedHeatExchanger extends MTEEnhancedMultiBlockBase<MTEPressurizedHeatExchanger>
    implements ICoolantLoopDevice, ISurvivalConstructable {

    public static final int CASING_INDEX_HEAT_PROOF = 11;

    protected MTEHatchPressurizedFluid mPrimaryInlet = null;
    protected MTEHatchPressurizedFluid mPrimaryOutlet = null;
    protected int mCasing = 0;

    protected int mStructureHeight = 3;
    protected int mStructureLength = 5;
    protected int mSheetmetalTier = 0;
    protected PheSheetmetalTier mFirstSheetmetalTier = null;
    protected boolean mSheetmetalsUniform = true;
    protected int mSheetmetalCount = 0;
    protected double mHeatTransferMultiplier = 1.0;

    protected double mCurrentCoolantTemp = 20.0;
    protected double mExchangeArea = 9.0; // Dynamic surface area: height * (length - 2) (9 m^2 base)
    protected double mMinorLossK = 1.2; // Heat exchanger core resistance
    protected double mLastHeatTransferredWatts = 0.0;
    protected ConvectiveHeatTransferModel mConvectiveModel = ConvectiveHeatTransferModel.DEFAULT;
    protected LoopSegment mBoundSegment = null;
    protected CoolantFluidProperty mLoopFluid = null;
    protected double mLastCoolantFlowRateM3s = 0.0;
    protected long mLastStepTick = -1;
    protected final PheExchange.Batch mBatch = new PheExchange.Batch();

    public MTEPressurizedHeatExchanger(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        initFromConfig();
    }

    public MTEPressurizedHeatExchanger(String aName) {
        super(aName);
        initFromConfig();
    }

    private void initFromConfig() {
        if (CoolantLoopDataRegistry.getDeviceData() != null) {
            mMinorLossK = CoolantLoopDataRegistry.getDeviceData()
                .getPhe().minorLossK;
        }
        if (CoolantLoopDataRegistry.getConvectiveModel() != null) {
            mConvectiveModel = CoolantLoopDataRegistry.getConvectiveModel();
        }
        mExchangeArea = PheGeometry.exchangeArea(mStructureHeight, mStructureLength);
    }

    public void addCasingBlock() {
        mCasing++;
    }

    public void recordSheetmetalBlock(PheSheetmetalTier tier) {
        if (tier != null) {
            mSheetmetalCount++;
            if (mFirstSheetmetalTier == null) {
                mFirstSheetmetalTier = tier;
            } else if (mFirstSheetmetalTier != tier) {
                mSheetmetalsUniform = false;
            }
        }
    }

    public boolean areSheetmetalsUniform() {
        return mSheetmetalsUniform;
    }

    public int getStructureHeight() {
        return mStructureHeight;
    }

    public int getStructureLength() {
        return mStructureLength;
    }

    public int getSheetmetalTier() {
        return mSheetmetalTier;
    }

    public void setSheetmetalTier(int tier) {
        mSheetmetalTier = Math.max(0, Math.min(PheSheetmetalTier.values().length - 1, tier));
        mFirstSheetmetalTier = PheSheetmetalTier.getByTier(mSheetmetalTier);
        mSheetmetalsUniform = true;
        mHeatTransferMultiplier = PheSheetmetalTier.getByTier(mSheetmetalTier)
            .getHeatTransferMultiplier();
    }

    public void setDimensions(int height, int length) {
        mStructureHeight = height;
        mStructureLength = length;
        mExchangeArea = PheGeometry.exchangeArea(height, length);
    }

    public double getHeatTransferMultiplier() {
        return mHeatTransferMultiplier;
    }

    public double getMaxTemperatureCelsius() {
        return PheSheetmetalTier.getByTier(mSheetmetalTier)
            .getMaxTemperatureCelsius();
    }

    @Override
    public double getMaxOperatingTemperatureCelsius() {
        return getMaxTemperatureCelsius();
    }

    @Override
    public boolean shouldCheckMaintenance() {
        return true;
    }

    public double getEfficiencyFactor() {
        int eff = getCurrentEfficiency(null);
        if (eff < 0) eff = 0;
        if (eff > 10000) eff = 10000;
        return eff / 10000.0;
    }

    @Override
    protected void onStructureCheckFinished(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onStructureCheckFinished(aBaseMetaTileEntity);
        if (mMachine) {
            if (aBaseMetaTileEntity != null && !aBaseMetaTileEntity.isAllowedToWork()) {
                aBaseMetaTileEntity.enableWorking();
            }
        }
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEPressurizedHeatExchanger(mName);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int aColorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            return new ITexture[] { TextureFactory.of(GregTechAPI.sBlockCasings1, 11),
                TextureFactory.of(
                    aActive ? Textures.BlockIcons.OVERLAY_FRONT_HEAT_EXCHANGER_ACTIVE
                        : Textures.BlockIcons.OVERLAY_FRONT_HEAT_EXCHANGER) };
        }
        return new ITexture[] { TextureFactory.of(GregTechAPI.sBlockCasings1, 11) };
    }

    public MTEHatchPressurizedFluid getPrimaryInlet() {
        return mPrimaryInlet;
    }

    public MTEHatchPressurizedFluid getPrimaryOutlet() {
        return mPrimaryOutlet;
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> aErrors) {
        mPrimaryInlet = null;
        mPrimaryOutlet = null;
        mInputHatches.clear();
        mOutputHatches.clear();
        mMaintenanceHatches.clear();
        mEnergyHatches.clear();
        mCasing = 0;
        mSheetmetalCount = 0;
        mFirstSheetmetalTier = null;
        mSheetmetalsUniform = true;

        int[] detectedDims = PressurizedHeatExchangerStructure.detectDimensions(aBaseMetaTileEntity);
        int detH = detectedDims[0];
        int detL = detectedDims[1];

        List<StructureError> testErrors = new ArrayList<>();
        boolean matched = checkPiece(
            PressurizedHeatExchangerStructure.getPieceName(detH, detL),
            1,
            detH / 2,
            0,
            testErrors);
        int matchedH = detH;
        int matchedL = detL;

        if (!matched) {
            outer: for (int h : PressurizedHeatExchangerStructure.VALID_HEIGHTS) {
                for (int l = PressurizedHeatExchangerStructure.MIN_LENGTH; l
                    <= PressurizedHeatExchangerStructure.MAX_LENGTH; l++) {
                    if (h == detH && l == detL) continue;
                    mPrimaryInlet = null;
                    mPrimaryOutlet = null;
                    mInputHatches.clear();
                    mOutputHatches.clear();
                    mMaintenanceHatches.clear();
                    mEnergyHatches.clear();
                    mCasing = 0;
                    mSheetmetalCount = 0;
                    mFirstSheetmetalTier = null;
                    mSheetmetalsUniform = true;
                    testErrors.clear();

                    if (checkPiece(PressurizedHeatExchangerStructure.getPieceName(h, l), 1, h / 2, 0, testErrors)) {
                        matched = true;
                        matchedH = h;
                        matchedL = l;
                        break outer;
                    }
                }
            }
        }

        if (!matched) {
            mPrimaryInlet = null;
            mPrimaryOutlet = null;
            mInputHatches.clear();
            mOutputHatches.clear();
            mMaintenanceHatches.clear();
            mEnergyHatches.clear();
            mCasing = 0;
            mSheetmetalCount = 0;
            mFirstSheetmetalTier = null;
            mSheetmetalsUniform = true;
            checkPiece(PressurizedHeatExchangerStructure.getPieceName(detH, detL), 1, detH / 2, 0, aErrors);
            return;
        }

        mStructureHeight = matchedH;
        mStructureLength = matchedL;
        mExchangeArea = PheGeometry.exchangeArea(mStructureHeight, mStructureLength);

        if (mFirstSheetmetalTier != null) {
            mSheetmetalTier = mFirstSheetmetalTier.getTier();
        } else {
            mSheetmetalTier = 0;
        }
        mHeatTransferMultiplier = PheSheetmetalTier.getByTier(mSheetmetalTier)
            .getHeatTransferMultiplier();

        int minCasings = PressurizedHeatExchangerStructure.getMinCasings(matchedH, matchedL);
        checkCasingMin(aErrors, mCasing, minCasings);
        int reqSheetmetal = PressurizedHeatExchangerStructure.getRequiredSheetmetalCount(matchedH, matchedL);
        if (mSheetmetalCount < reqSheetmetal) {
            aErrors.add(StructureErrors.of("coolantloops.structure.error.sheetmetal_missing"));
        }
        if (!mSheetmetalsUniform) {
            aErrors.add(StructureErrors.of("coolantloops.structure.error.sheetmetal_mixed"));
        }
        checkOneMaintenanceHatch(aErrors);
        if (mEnergyHatches.isEmpty()) {
            checkHasEnergyHatch(aErrors);
        } else if (mEnergyHatches.size() > 2) {
            checkHatchMax(aErrors, HatchElement.Energy, 2);
        }
        for (MTEHatchEnergy eh : mEnergyHatches) {
            if (!isSingleAmpEnergyHatch(eh)) {
                aErrors.add(StructureErrors.of("coolantloops.structure.error.single_amp_energy_required"));
                break;
            }
        }
        checkOneInputHatch(aErrors);
        checkOneOutputHatch(aErrors);

        if (mPrimaryInlet == null) {
            aErrors.add(StructureErrors.of("coolantloops.structure.error.missing_primary_inlet"));
        }
        if (mPrimaryOutlet == null) {
            aErrors.add(StructureErrors.of("coolantloops.structure.error.missing_primary_outlet"));
        }

        if (!aErrors.isEmpty()) {
            return;
        }

        if (mPrimaryInlet != null) {
            mPrimaryInlet.updateTexture(CASING_INDEX_HEAT_PROOF);
            mPrimaryInlet.setMode(MTEHatchPressurizedFluid.HatchMode.SUCTION);
        }
        if (mPrimaryOutlet != null) {
            mPrimaryOutlet.updateTexture(CASING_INDEX_HEAT_PROOF);
            mPrimaryOutlet.setMode(MTEHatchPressurizedFluid.HatchMode.DISCHARGE);
        }
        for (MTEHatchInput ih : mInputHatches) {
            ih.updateTexture(CASING_INDEX_HEAT_PROOF);
        }
        for (MTEHatchOutput oh : mOutputHatches) {
            oh.updateTexture(CASING_INDEX_HEAT_PROOF);
        }
        for (MTEHatchMaintenance mh : mMaintenanceHatches) {
            mh.updateTexture(CASING_INDEX_HEAT_PROOF);
        }
        for (MTEHatchEnergy eh : mEnergyHatches) {
            eh.updateTexture(CASING_INDEX_HEAT_PROOF);
        }

        if (getBaseMetaTileEntity() != null) {
            ItemStack icon = getMachineCraftingIcon();
            if (mPrimaryInlet != null) mPrimaryInlet.updateCraftingIcon(icon);
            if (mPrimaryOutlet != null) mPrimaryOutlet.updateCraftingIcon(icon);
            for (MTEHatchInput ih : mInputHatches) ih.updateCraftingIcon(icon);
            for (MTEHatchOutput oh : mOutputHatches) oh.updateCraftingIcon(icon);
            for (MTEHatchMaintenance mh : mMaintenanceHatches) mh.updateCraftingIcon(icon);
            for (MTEHatchEnergy eh : mEnergyHatches) eh.updateCraftingIcon(icon);
        }

        checkMaintenance();
        if (aBaseMetaTileEntity != null && !aBaseMetaTileEntity.isAllowedToWork()) {
            aBaseMetaTileEntity.enableWorking();
        }
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        return checkMachine_TT(aBaseMetaTileEntity);
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity != null && aBaseMetaTileEntity.isServerSide()) {
            stepHeatExchange();
        }
    }

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        MultiblockStartup.formNow(this);
    }

    @Override
    public void collectStructureChunks(Set<Long> chunks) {
        String piece = PressurizedHeatExchangerStructure.getPieceName(mStructureHeight, mStructureLength);
        StructureChunks.collect(this, piece, 1, mStructureHeight / 2, 0, chunks);
    }

    public static boolean isSingleAmpEnergyHatch(MTEHatchEnergy hatch) {
        if (hatch == null) return false;
        return hatch.maxWorkingAmperesIn() <= 2L && hatch.maxAmperesIn() <= 2L;
    }

    public List<MTEHatchEnergy> getValidEnergyHatches() {
        List<MTEHatchEnergy> list = new ArrayList<>();
        if (mEnergyHatches != null) {
            for (MTEHatchEnergy hatch : mEnergyHatches) {
                if (hatch != null && (hatch.isValid() || hatch.getBaseMetaTileEntity() != null)
                    && isSingleAmpEnergyHatch(hatch)) {
                    list.add(hatch);
                }
            }
        }
        return list;
    }

    public long getTargetVoltage() {
        List<MTEHatchEnergy> hatches = getValidEnergyHatches();
        if (hatches.isEmpty()) {
            return 32L;
        }
        if (hatches.size() == 1) {
            int tier = Math.max(1, Math.min(hatches.get(0).mTier, GTValues.V.length - 1));
            return GTValues.V[tier];
        }
        int maxTier = 1;
        for (MTEHatchEnergy h : hatches) {
            if (h.mTier > maxTier) {
                maxTier = h.mTier;
            }
        }
        maxTier = Math.max(1, Math.min(maxTier, GTValues.V.length - 1));
        return 4L * GTValues.V[maxTier];
    }

    public long getRequiredPowerDrawEU() {
        long v = getTargetVoltage();
        return Math.max(1L, (15L * v) / 16L);
    }

    public double getOverclockMultiplier() {
        return (double) getTargetVoltage() / 32.0;
    }

    public double getMaxHeatExchangePerBatchJoules() {
        double baseHeat = (CoolantLoopDataRegistry.getDeviceData() != null) ? CoolantLoopDataRegistry.getDeviceData()
            .getPhe().baseMaxHeatPerBatchJoules : 2260000.0;
        return baseHeat * getOverclockMultiplier();
    }

    public int getBatchTicks() {
        return (CoolantLoopDataRegistry.getDeviceData() != null) ? CoolantLoopDataRegistry.getDeviceData()
            .getPhe().batchTicks : 20;
    }

    public int getBatchTimer() {
        return mBatch.timer;
    }

    public double getBatchAccumulatedHeatJoules() {
        return mBatch.heatJoules;
    }

    public boolean triggerPowerfail() {
        stopMachine(ShutDownReasonRegistry.POWER_LOSS);
        if (getBaseMetaTileEntity() != null) {
            getBaseMetaTileEntity().setActive(false);
            getBaseMetaTileEntity().setShutDownReason(ShutDownReasonRegistry.POWER_LOSS);
        }
        mLastHeatTransferredWatts = 0.0;
        mBatch.reset();
        return false;
    }

    public boolean drainEnergy(long aEU) {
        if (aEU <= 0) return true;

        List<MTEHatchEnergy> hatches = getValidEnergyHatches();
        if (!hatches.isEmpty()) {
            long totalStored = 0L;
            for (MTEHatchEnergy h : hatches) {
                IGregTechTileEntity bte = h.getBaseMetaTileEntity();
                if (bte != null) {
                    totalStored += bte.getStoredEU();
                }
            }

            if (totalStored < aEU) {
                return false;
            }

            long remaining = aEU;
            for (MTEHatchEnergy h : hatches) {
                IGregTechTileEntity bte = h.getBaseMetaTileEntity();
                if (bte != null) {
                    long stored = bte.getStoredEU();
                    if (stored > 0) {
                        long toDrain = Math.min(stored, remaining);
                        bte.decreaseStoredEnergyUnits(toDrain, false);
                        remaining -= toDrain;
                        if (remaining <= 0) {
                            return true;
                        }
                    }
                }
            }
            return remaining <= 0;
        }

        if (getBaseMetaTileEntity() != null && getBaseMetaTileEntity().getStoredEU() >= aEU) {
            return getBaseMetaTileEntity().decreaseStoredEnergyUnits(aEU, false);
        }
        return false;
    }

    public void completeBatch() {
        if (mBatch.heatJoules > 0.0) {
            mLastHeatTransferredWatts = mBatch.heatJoules / (getBatchTicks() * 0.05);
            CoolantDeviceData.PHEData.SecondaryCoolantEntry matchedCoolant = mBatch.coolant;
            CoolantDeviceData.PHEData pheConfig = pheConfig();
            if (matchedCoolant == null) {
                scanSecondaryInput(pheConfig);
                matchedCoolant = mScanCoolant;
            }

            if (matchedCoolant != null) {
                int fluidToBoil = PheExchange.fluidToBoilMl(mBatch.heatJoules, matchedCoolant);

                if (fluidToBoil > 0) {
                    int actualFluidDrained = 0;
                    for (MTEHatchInput hatch : mInputHatches) {
                        if (hatch != null && hatch.getFluid() != null) {
                            Fluid f = hatch.getFluid()
                                .getFluid();
                            if (secondaryCoolantFor(pheConfig, f) == matchedCoolant
                                || isNamedInput(f, matchedCoolant)) {
                                int needed = fluidToBoil - actualFluidDrained;
                                FluidStack drained = hatch.drain(needed, true);
                                if (drained != null && drained.amount > 0) {
                                    actualFluidDrained += drained.amount;
                                    if (actualFluidDrained >= fluidToBoil) {
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    if (actualFluidDrained > 0) {
                        int expansion = matchedCoolant.expansionRatio > 0 ? matchedCoolant.expansionRatio : 1;
                        int gasProduced = actualFluidDrained * expansion;

                        Fluid outputFluid = matchedCoolant.isSteam
                            ? steamFluid(
                                PheExchange.steamGrade(mBatch.effectiveTemperature(mCurrentCoolantTemp), pheConfig))
                            : gasFluid(matchedCoolant);
                        if (outputFluid != null) {
                            addOutput(new FluidStack(outputFluid, gasProduced));
                        }
                    }
                }
            }
        }

        mBatch.reset();
    }

    private static final CoolantDeviceData.PHEData DEFAULT_PHE_CONFIG = new CoolantDeviceData.PHEData();

    private static CoolantDeviceData.PHEData pheConfig() {
        CoolantDeviceData data = CoolantLoopDataRegistry.getDeviceData();
        return data != null ? data.getPhe() : DEFAULT_PHE_CONFIG;
    }

    /** The last fluid matched against the secondary coolants, its entry (or null), and the config it was matched in. */
    private Fluid mMatchedFluid;
    private CoolantDeviceData.PHEData.SecondaryCoolantEntry mMatchedCoolant;
    private CoolantDeviceData.PHEData mMatchedConfig;

    /**
     * The secondary coolant a fluid boils as, or null. An exchanger is fed one coolant, so the last answer is kept and
     * the name lookup runs only when the fluid (or the config) changes.
     */
    private CoolantDeviceData.PHEData.SecondaryCoolantEntry secondaryCoolantFor(CoolantDeviceData.PHEData cfg,
        Fluid fluid) {
        if (fluid == null) return null;
        if (fluid != mMatchedFluid || cfg != mMatchedConfig) {
            mMatchedCoolant = cfg.findSecondaryCoolant(fluid.getName());
            mMatchedFluid = fluid;
            mMatchedConfig = cfg;
        }
        return mMatchedCoolant;
    }

    /** Whether the fluid is the coolant's configured input by name (only checked when it matched no entry). */
    private static boolean isNamedInput(Fluid fluid, CoolantDeviceData.PHEData.SecondaryCoolantEntry coolant) {
        return fluid != null && fluid.getName()
            .equalsIgnoreCase(coolant.inputFluidName);
    }

    /** What the last scanSecondaryInput found: the first input fluid that boils as a secondary coolant. */
    private CoolantDeviceData.PHEData.SecondaryCoolantEntry mScanCoolant;
    private int mScanAmount;

    /**
     * Finds the first input fluid that boils as a secondary coolant, in the order GregTech's getStoredFluids lists
     * them (ME hatches last), without building that list (and its map, and the ME query when another hatch has
     * coolant) every tick. Sets mScanCoolant and mScanAmount.
     */
    private void scanSecondaryInput(CoolantDeviceData.PHEData cfg) {
        mScanCoolant = null;
        mScanAmount = 0;
        boolean anyME = false;
        for (int i = 0, n = mInputHatches.size(); i < n; i++) {
            MTEHatchInput hatch = mInputHatches.get(i);
            if (hatch == null || !hatch.isValid()) continue;
            if (hatch instanceof MTEHatchInputME) {
                anyME = true;
            } else if (hatch instanceof MTEHatchMultiInput multi) {
                for (FluidStack fs : multi.getStoredFluid()) {
                    if (fs != null && offerSecondary(cfg, fs.getFluid(), fs.amount)) return;
                }
            } else if (hatch instanceof MTEHatchInputDebug debug) {
                for (FluidStack fs : debug.getFluidList()) {
                    if (fs != null && offerSecondary(cfg, fs.getFluid(), Integer.MAX_VALUE)) return;
                }
            } else {
                FluidStack fs = hatch.getFillableStack();
                if (fs != null && offerSecondary(cfg, fs.getFluid(), fs.amount)) return;
            }
        }
        if (!anyME) return;
        for (int i = 0, n = mInputHatches.size(); i < n; i++) {
            if (mInputHatches.get(i) instanceof MTEHatchInputME me && me.isValid()) {
                for (FluidStack fs : me.getStoredFluids()) {
                    if (fs != null && offerSecondary(cfg, fs.getFluid(), fs.amount)) return;
                }
            }
        }
    }

    private boolean offerSecondary(CoolantDeviceData.PHEData cfg, Fluid fluid, int amount) {
        if (fluid == null || amount <= 0) return false;
        CoolantDeviceData.PHEData.SecondaryCoolantEntry coolant = secondaryCoolantFor(cfg, fluid);
        if (coolant == null) return false;
        mScanCoolant = coolant;
        mScanAmount = amount;
        return true;
    }

    /** Output fluids by name, resolved once (steam grades are a few fixed names; a gas is per coolant entry). */
    private String mSteamName;
    private Fluid mSteamFluid;
    private CoolantDeviceData.PHEData.SecondaryCoolantEntry mGasCoolant;
    private Fluid mGasFluid;

    private Fluid steamFluid(String steamName) {
        if (!steamName.equals(mSteamName) || mSteamFluid == null) {
            Fluid steam = FluidRegistry.getFluid(steamName);
            if (steam == null && steamName.equals("supercriticalsteam")) {
                steam = FluidRegistry.getFluid("supercriticalSteam");
            }
            if (steam == null) steam = FluidRegistry.getFluid("steam");
            mSteamName = steamName;
            mSteamFluid = steam;
        }
        return mSteamFluid;
    }

    private Fluid gasFluid(CoolantDeviceData.PHEData.SecondaryCoolantEntry coolant) {
        if (coolant != mGasCoolant || mGasFluid == null) {
            String gasName = coolant.outputGasName;
            Fluid gas = FluidRegistry.getFluid(gasName);
            if (gas == null) gas = FluidRegistry.getFluid(gasName.toLowerCase());
            if (gas == null) gas = FluidRegistry.getFluid(gasName.toUpperCase());
            mGasCoolant = coolant;
            mGasFluid = gas;
        }
        return mGasFluid;
    }

    public boolean stepHeatExchange() {
        IGregTechTileEntity te = getBaseMetaTileEntity();
        long currentTick = (te != null) ? te.getTimer() : -1;
        if (te != null && te.getWorld() != null && currentTick >= 0) {
            if (mLastStepTick == currentTick) {
                return true;
            }
            mLastStepTick = currentTick;
        }

        checkMaintenance();
        if (!isAllowedToWork() || mPrimaryInlet == null || mPrimaryOutlet == null) {
            mLastHeatTransferredWatts = 0.0;
            if (getBaseMetaTileEntity() != null) {
                getBaseMetaTileEntity().setActive(false);
            }
            return true;
        }

        double effFactor = getEfficiencyFactor();
        if (effFactor <= 0.0) {
            mLastHeatTransferredWatts = 0.0;
            return true;
        }

        // Secondary side channels pumping requires PHE energy consumption each tick
        long requiredEU = getRequiredPowerDrawEU();
        if (!drainEnergy(requiredEU)) {
            return triggerPowerfail();
        }
        if (getBaseMetaTileEntity() != null) {
            getBaseMetaTileEntity().setActive(true);
        }

        CoolantDeviceData.PHEData pheConfig = pheConfig();
        scanSecondaryInput(pheConfig);
        CoolantDeviceData.PHEData.SecondaryCoolantEntry matchedCoolant = mScanCoolant;

        double dt = 0.05; // 1 tick = 0.05 seconds
        double effectiveArea = mExchangeArea * mHeatTransferMultiplier * pheConfig.heatTransferMultiplier;
        mCurrentCoolantTemp = PheExchange.tick(
            mBatch,
            pheConfig,
            matchedCoolant,
            mScanAmount,
            mLastCoolantFlowRateM3s,
            mBoundSegment,
            mLoopFluid,
            mCurrentCoolantTemp,
            effectiveArea,
            mConvectiveModel,
            effFactor,
            getMaxHeatExchangePerBatchJoules());
        if (mBatch.lastTickHeatJoules > 0.0 && mPrimaryOutlet != null) {
            mPrimaryOutlet.setCurrentTemperatureCelsius(mCurrentCoolantTemp);
        }
        double heatTransferredThisTickJoules = mBatch.lastTickHeatJoules;

        mLastHeatTransferredWatts = heatTransferredThisTickJoules / dt;

        if (mBatch.timer >= getBatchTicks()) {
            completeBatch();
        }

        return true;
    }

    @Override
    public boolean onRunningTick(ItemStack aStack) {
        return stepHeatExchange();
    }

    // --- ICoolantLoopDevice Implementation ---

    private String cachedDeviceId;

    @Override
    public String getDeviceId() {
        if (cachedDeviceId != null) return cachedDeviceId;
        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te != null) {
            cachedDeviceId = "phe_" + te.getXCoord() + "_" + te.getYCoord() + "_" + te.getZCoord();
            return cachedDeviceId;
        }
        return "phe_unbound";
    }

    @Override
    public double getMinorLossK() {
        return mMinorLossK;
    }

    @Override
    public double getDeviceTemperatureCelsius() {
        return mCurrentCoolantTemp;
    }

    @Override
    public void processThermalExchange(double coolantFlowRateM3s, double dt, CoolantFluidProperty fluid,
        LoopSegment segment) {
        this.mBoundSegment = segment;
        this.mLoopFluid = fluid;
        this.mLastCoolantFlowRateM3s = coolantFlowRateM3s;
        if (segment != null) {
            this.mCurrentCoolantTemp = segment.getCurrentTemperatureCelsius();
        }
        stepHeatExchange();
    }

    public LoopSegment getBoundSegment() {
        return mBoundSegment;
    }

    public void setBoundSegment(LoopSegment segment) {
        this.mBoundSegment = segment;
        if (segment != null) {
            this.mCurrentCoolantTemp = segment.getCurrentTemperatureCelsius();
        }
    }

    public CoolantFluidProperty getLoopFluid() {
        return mLoopFluid;
    }

    public void setLoopFluid(CoolantFluidProperty fluid) {
        this.mLoopFluid = fluid;
    }

    public double getLastCoolantFlowRateM3s() {
        return mLastCoolantFlowRateM3s;
    }

    public void setLastCoolantFlowRateM3s(double flowRate) {
        this.mLastCoolantFlowRateM3s = flowRate;
    }

    public double getLastHeatTransferredWatts() {
        return mLastHeatTransferredWatts;
    }

    public void setCurrentCoolantTemp(double temp) {
        this.mCurrentCoolantTemp = temp;
        if (mBoundSegment != null) {
            mBoundSegment.setCurrentTemperatureCelsius(temp);
        }
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("mBatchTimer", mBatch.timer);
        aNBT.setDouble("mBatchAccumulatedHeatJoules", mBatch.heatJoules);
        aNBT.setDouble("mBatchWeightedTempSum", mBatch.weightedTempSum);
        aNBT.setDouble("mCurrentCoolantTemp", mCurrentCoolantTemp);
        aNBT.setDouble("mLastHeatTransferredWatts", mLastHeatTransferredWatts);
        aNBT.setInteger("mStructureHeight", mStructureHeight);
        aNBT.setInteger("mStructureLength", mStructureLength);
        aNBT.setInteger("mSheetmetalTier", mSheetmetalTier);
        aNBT.setDouble("mExchangeArea", mExchangeArea);
        aNBT.setDouble("mHeatTransferMultiplier", mHeatTransferMultiplier);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mBatch.timer = aNBT.getInteger("mBatchTimer");
        mBatch.heatJoules = aNBT.getDouble("mBatchAccumulatedHeatJoules");
        mBatch.weightedTempSum = aNBT.getDouble("mBatchWeightedTempSum");
        if (aNBT.hasKey("mCurrentCoolantTemp")) {
            mCurrentCoolantTemp = aNBT.getDouble("mCurrentCoolantTemp");
        }
        if (aNBT.hasKey("mLastHeatTransferredWatts")) {
            mLastHeatTransferredWatts = aNBT.getDouble("mLastHeatTransferredWatts");
        }
        if (aNBT.hasKey("mStructureHeight")) {
            mStructureHeight = aNBT.getInteger("mStructureHeight");
        }
        if (aNBT.hasKey("mStructureLength")) {
            mStructureLength = aNBT.getInteger("mStructureLength");
            if (mStructureLength < PressurizedHeatExchangerStructure.MIN_LENGTH) {
                mStructureLength += 2;
            }
        }
        if (aNBT.hasKey("mSheetmetalTier")) {
            mSheetmetalTier = aNBT.getInteger("mSheetmetalTier");
        }
        if (aNBT.hasKey("mExchangeArea")) {
            mExchangeArea = aNBT.getDouble("mExchangeArea");
        } else {
            mExchangeArea = PheGeometry.exchangeArea(mStructureHeight, mStructureLength);
        }
        if (aNBT.hasKey("mHeatTransferMultiplier")) {
            mHeatTransferMultiplier = aNBT.getDouble("mHeatTransferMultiplier");
        } else {
            mHeatTransferMultiplier = PheSheetmetalTier.getByTier(mSheetmetalTier)
                .getHeatTransferMultiplier();
        }
    }

    @Override
    public boolean checkRecipe(ItemStack aStack) {
        return true;
    }

    @Override
    protected MTEMultiBlockBaseGui<?> getGui() {
        return new CoolantLoopMachineGui<>(this);
    }

    @Override
    public boolean supportsSingleRecipeLocking() {
        return false;
    }

    @Override
    public ButtonWidget createLockToSingleRecipeButton(IWidgetBuilder<?> builder) {
        return (ButtonWidget) new ButtonWidget().setSize(0, 0)
            .setEnabled(false);
    }

    public boolean addMaintenanceOrEnergyHatch(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity == null) return false;
        if (aMetaTileEntity instanceof MTEHatchMaintenance) {
            MTEHatchMaintenance mh = (MTEHatchMaintenance) aMetaTileEntity;
            mh.updateTexture(aBaseCasingIndex);
            if (getBaseMetaTileEntity() != null) {
                mh.updateCraftingIcon(getMachineCraftingIcon());
            }
            return mMaintenanceHatches.add(mh);
        }
        if (aMetaTileEntity instanceof MTEHatchEnergy) {
            MTEHatchEnergy eh = (MTEHatchEnergy) aMetaTileEntity;
            if (!isSingleAmpEnergyHatch(eh)) {
                return false;
            }
            eh.updateTexture(aBaseCasingIndex);
            if (getBaseMetaTileEntity() != null) {
                eh.updateCraftingIcon(getMachineCraftingIcon());
            }
            return mEnergyHatches.add(eh);
        }
        return false;
    }

    public boolean addPressurizedHatch(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity == null) return false;
        if (aMetaTileEntity instanceof MTEHatchPressurizedFluid) {
            MTEHatchPressurizedFluid ph = (MTEHatchPressurizedFluid) aMetaTileEntity;
            ph.updateTexture(aBaseCasingIndex);
            if (getBaseMetaTileEntity() != null) {
                ph.updateCraftingIcon(getMachineCraftingIcon());
            }
            if (ph.isInput()) {
                if (mPrimaryInlet == null) {
                    mPrimaryInlet = ph;
                    return true;
                }
                return false;
            } else if (ph.isOutput()) {
                if (mPrimaryOutlet == null) {
                    mPrimaryOutlet = ph;
                    return true;
                }
                return false;
            }
            return false;
        }
        return false;
    }

    public boolean addSecondaryHatch(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity == null) return false;
        if (aMetaTileEntity instanceof MTEHatchInput) {
            MTEHatchInput ih = (MTEHatchInput) aMetaTileEntity;
            ih.updateTexture(aBaseCasingIndex);
            if (getBaseMetaTileEntity() != null) {
                ih.updateCraftingIcon(getMachineCraftingIcon());
            }
            return mInputHatches.add(ih);
        }
        if (aMetaTileEntity instanceof MTEHatchOutput) {
            MTEHatchOutput oh = (MTEHatchOutput) aMetaTileEntity;
            oh.updateTexture(aBaseCasingIndex);
            if (getBaseMetaTileEntity() != null) {
                oh.updateCraftingIcon(getMachineCraftingIcon());
            }
            return mOutputHatches.add(oh);
        }
        return false;
    }

    public boolean addBottomHatch(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        return addPressurizedHatch(aTileEntity, aBaseCasingIndex);
    }

    @Override
    public IStructureDefinition<MTEPressurizedHeatExchanger> getStructureDefinition() {
        return PressurizedHeatExchangerStructure.getStructureDefinition();
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        int h = PressurizedHeatExchangerStructure.getHeightFromTrigger(stackSize);
        int l = PressurizedHeatExchangerStructure.getLengthFromTrigger(stackSize);
        buildPiece(PressurizedHeatExchangerStructure.getPieceName(h, l), stackSize, hintsOnly, 1, h / 2, 0);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        int h = PressurizedHeatExchangerStructure.getHeightFromTrigger(stackSize);
        int l = PressurizedHeatExchangerStructure.getLengthFromTrigger(stackSize);
        return survivalBuildPiece(
            PressurizedHeatExchangerStructure.getPieceName(h, l),
            stackSize,
            1,
            h / 2,
            0,
            elementBudget,
            env,
            false,
            true);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gt.multiblock.pressurized_heat_exchanger.machine_type"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.pressurized_heat_exchanger.desc1"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.pressurized_heat_exchanger.desc2"))
            .addSeparator()
            .beginVariableStructureBlock(3, 3, 3, 9, 5, 11, false)
            .addController(
                StatCollector.translateToLocal("gt.multiblock.pressurized_heat_exchanger.structure.controller"))
            .addCasingInfoMin(
                StatCollector.translateToLocal("gt.multiblock.pressurized_heat_exchanger.structure.casings"),
                15,
                false)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.pressurized_heat_exchanger.structure.cooling_duct"),
                StatCollector.translateToLocal("gt.multiblock.pressurized_heat_exchanger.structure.cooling_duct_pos"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.pressurized_heat_exchanger.structure.heating_duct"),
                StatCollector.translateToLocal("gt.multiblock.pressurized_heat_exchanger.structure.heating_duct_pos"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.pressurized_heat_exchanger.structure.sheetmetal_tiered"),
                StatCollector
                    .translateToLocal("gt.multiblock.pressurized_heat_exchanger.structure.sheetmetal_tiered_pos"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.pressurized_heat_exchanger.structure.hatch_inlet"),
                StatCollector.translateToLocal("gt.multiblock.pressurized_heat_exchanger.structure.hatch_inlet_pos"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.pressurized_heat_exchanger.structure.hatch_outlet"),
                StatCollector.translateToLocal("gt.multiblock.pressurized_heat_exchanger.structure.hatch_outlet_pos"))
            .addInputHatch(
                StatCollector.translateToLocal("gt.multiblock.pressurized_heat_exchanger.structure.input_hatch"),
                1)
            .addOutputHatch(
                StatCollector.translateToLocal("gt.multiblock.pressurized_heat_exchanger.structure.output_hatch"),
                1)
            .addMaintenanceHatch(
                StatCollector.translateToLocal("gt.multiblock.pressurized_heat_exchanger.structure.maintenance"),
                1)
            .addEnergyHatch(
                StatCollector.translateToLocal("gt.multiblock.pressurized_heat_exchanger.structure.energy"),
                1)
            .addSubChannel(CoolantStructureChannels.PHE_HEIGHT)
            .addSubChannel(CoolantStructureChannels.PHE_LENGTH)
            .addSubChannel(CoolantStructureChannels.PHE_SHEETMETAL)
            .toolTipFinisher(StatCollector.translateToLocal("gt.multiblock.coolantloops.finisher"));
        return tt;
    }
}
