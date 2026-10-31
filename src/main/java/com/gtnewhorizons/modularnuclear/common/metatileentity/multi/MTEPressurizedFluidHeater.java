package com.gtnewhorizons.modularnuclear.common.metatileentity.multi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizons.modularnuclear.common.config.CoolantLoopDataRegistry;
import com.gtnewhorizons.modularnuclear.common.config.ReactorConfigData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData;
import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantFluidProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.ICoolantLoopDevice;
import com.gtnewhorizons.modularnuclear.common.coolantloop.ICoolantLoopPump;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopSegment;
import com.gtnewhorizons.modularnuclear.common.gui.CoolantLoopMachineGui;
import com.gtnewhorizons.modularnuclear.common.metatileentity.CoolantStructureChannels;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchPressurizedFluid;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchPressurizedInput;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchPressurizedOutput;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.structure.HeaterPipeCasingTier;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.structure.PressurizedFluidHeaterStructure;
import com.gtnewhorizons.modularnuclear.common.nuclear.CoolantPassage;
import com.gtnewhorizons.modularnuclear.common.util.CoolantFluidHelper;
import com.gtnewhorizons.modularnuclear.common.util.CoolantLocalization;
import com.gtnewhorizons.modularnuclear.common.util.MultiblockStartup;
import com.gtnewhorizons.modularnuclear.common.util.StructureChunks;
import com.gtnewhorizons.modularui.api.widget.IWidgetBuilder;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;

import cpw.mods.fml.common.Optional;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.HeatingCoilLevel;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEHatchMaintenance;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.IGTHatchAdder;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gregtech.common.covers.Cover;
import gregtech.common.covers.CoverEUMeter;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gregtech.common.misc.GTStructureChannels;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mrtjp.projectred.api.IBundledEmitter;
import tectech.thing.metaTileEntity.hatch.MTEHatchEnergyMulti;
import tectech.thing.metaTileEntity.hatch.MTEHatchEnergyTunnel;

/**
 * Pressurized Fluid Heater multiblock.
 *
 * <ul>
 * <li>Dimensions: 3 wide x 3 high x 3 to 11 long (1 to 9 middle heating ring layers).</li>
 * <li>Controller at front-bottom center (1, 2, 0); High-Pressure Input Hatch at front center (1, 1, 0);
 * High-Pressure Output Hatch at back center (1, 1, L-1).</li>
 * <li>Interior centerline uses Item Pipe Casings matching the coolant loop pipe/reservoir material.</li>
 * <li>Middle outer rings use EBF Heating Coils (Cupronickel = LV, scaling 4x EU/t per coil tier).</li>
 * <li>Front and back layers use Heat-Proof Machine Casings, with 1 Maintenance Hatch (any type) and
 * 1 Energy Hatch (single-amp or multi-amp, no laser).</li>
 * <li>Consumes up to {@code 32 * rings * 4^coilTierIndex} EU/t without powerfailing on partial EU,
 * heating the inner thermal mass using MPTR's {@code euPerHu} equivalence and exchanging heat convectively
 * with the coolant loop in either direction whether powered on or off.</li>
 * <li>Exceeding the item pipe casing's operating temperature limit triggers powerfail and disconnects the loop.</li>
 * </ul>
 */
@Optional.Interface(iface = "mrtjp.projectred.api.IBundledEmitter", modid = "ProjRed|Transmission")
public class MTEPressurizedFluidHeater extends MTEExtendedPowerMultiBlockBase<MTEPressurizedFluidHeater>
    implements ICoolantLoopDevice, ISurvivalConstructable, IBundledEmitter {

    public static final int CASING_INDEX = PressurizedFluidHeaterStructure.CASING_INDEX;

    public int mCasingAmount = 0;
    public final List<MTEHatchPressurizedFluid> mPressurizedInputs = new ArrayList<>();
    public final List<MTEHatchPressurizedFluid> mPressurizedOutputs = new ArrayList<>();

    protected int mStructureLength = PressurizedFluidHeaterStructure.MIN_LENGTH;
    protected int mRingCount = PressurizedFluidHeaterStructure.MIN_RINGS;
    protected HeatingCoilLevel mCoilLevel = HeatingCoilLevel.None;
    protected int mPipeCasingTier = -1;
    protected boolean mPipeCasingUniform = true;
    protected boolean mRejectedLaserHatch = false;

    protected double mInnerTemperatureCelsius = 20.0;
    protected double mSimulatedAmbientTempCelsius = 20.0;
    protected double mMinorLossK = 1.5;
    protected double mBaseExchangeAreaM2 = 4.0;

    protected long mLastConsumedEuPerTick = 0L;
    protected double mLastHeatingHuPerTick = 0.0;
    protected double mLastHeatTransferHuPerTick = 0.0;
    protected double mLastHeatTransferWatts = 0.0;
    protected double mLastCoolantFlowLitersPerSecond = 0.0;

    protected boolean mPowerFailed = false;
    protected boolean mWorks = true;
    protected boolean mHasEverBeenEnabled = false;
    protected ICoolantLoopPump mConnectedPump = null;
    protected LoopSegment mBoundSegment = null;
    protected CoolantFluidProperty mLoopFluid = null;

    // Headless unit test energy & cover simulation
    protected long mSimulatedStoredEU = -1L;
    protected int mSimulatedEnergyHatchTier = -1;
    protected ForgeDirection mSimulatedDetectorSide = ForgeDirection.UNKNOWN;
    protected long mSimulatedDetectorThreshold = 0L;
    protected boolean mSimulatedDetectorInverted = false;

    // Redstone & ProjectRed telemetry
    protected byte mRedstoneOutput = 0;
    protected int mFineOutputStrength = 0;
    protected int mBundledChannelMode = 0; // 0 = broadcast all 16 channels, 1..16 = channel 0..15
    protected final byte[] mBundledSignal = new byte[16];

    public enum HeaterHatchElement implements IHatchElement<MTEPressurizedFluidHeater> {

        Maintenance(MTEPressurizedFluidHeater::addMaintenanceToMachineList, MTEHatchMaintenance.class) {

            @Override
            public long count(MTEPressurizedFluidHeater t) {
                return t.mMaintenanceHatches.size();
            }
        },
        Energy(MTEPressurizedFluidHeater::addEnergyOrMultiAmpToMachineList, MTEHatchEnergy.class,
            MTEHatchEnergyMulti.class) {

            @Override
            public long count(MTEPressurizedFluidHeater t) {
                return t.getTotalEnergyHatchCount();
            }
        },
        PressurizedInput(MTEPressurizedFluidHeater::addPressurizedInputToMachineList, MTEHatchPressurizedInput.class) {

            @Override
            public long count(MTEPressurizedFluidHeater t) {
                return t.mPressurizedInputs.size();
            }
        },
        PressurizedOutput(MTEPressurizedFluidHeater::addPressurizedOutputToMachineList,
            MTEHatchPressurizedOutput.class) {

            @Override
            public long count(MTEPressurizedFluidHeater t) {
                return t.mPressurizedOutputs.size();
            }
        };

        private final List<Class<? extends IMetaTileEntity>> mteClasses;
        private final IGTHatchAdder<MTEPressurizedFluidHeater> adder;

        @SafeVarargs
        HeaterHatchElement(IGTHatchAdder<MTEPressurizedFluidHeater> adder,
            Class<? extends IMetaTileEntity>... mteClasses) {
            this.mteClasses = Collections.unmodifiableList(Arrays.asList(mteClasses));
            this.adder = adder;
        }

        @Override
        public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
            return mteClasses;
        }

        @Override
        public IGTHatchAdder<? super MTEPressurizedFluidHeater> adder() {
            return adder;
        }
    }

    public MTEPressurizedFluidHeater(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        initFromConfig();
    }

    public MTEPressurizedFluidHeater(String aName) {
        super(aName);
        initFromConfig();
    }

    private void initFromConfig() {
        if (CoolantLoopDataRegistry.getDeviceData() != null && CoolantLoopDataRegistry.getDeviceData()
            .getHeater() != null) {
            mBaseExchangeAreaM2 = CoolantLoopDataRegistry.getDeviceData()
                .getHeater().baseExchangeArea;
            mMinorLossK = CoolantLoopDataRegistry.getDeviceData()
                .getHeater().minorLossK;
        }
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEPressurizedFluidHeater(mName);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int aColorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            return new ITexture[] { TextureFactory.of(GregTechAPI.sBlockCasings1, CASING_INDEX),
                TextureFactory.of(
                    aActive ? Textures.BlockIcons.OVERLAY_FRONT_ELECTRIC_BLAST_FURNACE_ACTIVE
                        : Textures.BlockIcons.OVERLAY_FRONT_ELECTRIC_BLAST_FURNACE) };
        }
        return new ITexture[] { TextureFactory.of(GregTechAPI.sBlockCasings1, CASING_INDEX) };
    }

    // --- Structure & Hatch Callbacks ---

    public HeatingCoilLevel getCoilLevel() {
        return mCoilLevel;
    }

    public void setCoilLevel(HeatingCoilLevel coilLevel) {
        this.mCoilLevel = coilLevel != null ? coilLevel : HeatingCoilLevel.None;
    }

    public int getPipeCasingTier() {
        return mPipeCasingTier;
    }

    public void setPipeCasingTier(int tier) {
        if (mPipeCasingTier != -1 && mPipeCasingTier != tier) {
            mPipeCasingUniform = false;
        }
        this.mPipeCasingTier = tier;
    }

    public HeaterPipeCasingTier getHeaterPipeCasingTier() {
        return HeaterPipeCasingTier.getByCasingTier(mPipeCasingTier > 0 ? mPipeCasingTier : 1);
    }

    public int getPassageVoltageTier() {
        return getHeaterPipeCasingTier().getPassageVoltageTier();
    }

    public int getStructureLength() {
        return mStructureLength;
    }

    public int getRingCount() {
        return mRingCount;
    }

    public void setStructureLength(int length) {
        this.mStructureLength = PressurizedFluidHeaterStructure.clampLength(length);
        this.mRingCount = PressurizedFluidHeaterStructure.getRingCount(this.mStructureLength);
    }

    public void setRingCount(int rings) {
        int clampedRings = Math
            .max(PressurizedFluidHeaterStructure.MIN_RINGS, Math.min(PressurizedFluidHeaterStructure.MAX_RINGS, rings));
        this.mRingCount = clampedRings;
        this.mStructureLength = clampedRings + 2;
    }

    public boolean addPressurizedInputToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity mte = aTileEntity.getMetaTileEntity();
        if (mte instanceof MTEHatchPressurizedInput || (mte instanceof MTEHatchPressurizedFluid ph && ph.isInput())) {
            MTEHatchPressurizedFluid hatch = (MTEHatchPressurizedFluid) mte;
            hatch.updateTexture(aBaseCasingIndex);
            hatch.setMode(MTEHatchPressurizedFluid.HatchMode.SUCTION);
            mPressurizedInputs.add(hatch);
            return true;
        }
        return false;
    }

    public boolean addPressurizedOutputToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity mte = aTileEntity.getMetaTileEntity();
        if (mte instanceof MTEHatchPressurizedOutput || (mte instanceof MTEHatchPressurizedFluid ph && ph.isOutput())) {
            MTEHatchPressurizedFluid hatch = (MTEHatchPressurizedFluid) mte;
            hatch.updateTexture(aBaseCasingIndex);
            hatch.setMode(MTEHatchPressurizedFluid.HatchMode.DISCHARGE);
            mPressurizedOutputs.add(hatch);
            return true;
        }
        return false;
    }

    public static boolean isLaserEnergyHatch(IMetaTileEntity mte) {
        if (mte == null) return false;
        if (mte instanceof MTEHatchEnergyTunnel) {
            return true;
        }
        String className = mte.getClass()
            .getName();
        return className.contains("EnergyTunnel") || className.contains("Laser");
    }

    public boolean addEnergyOrMultiAmpToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity mte = aTileEntity.getMetaTileEntity();
        if (mte == null) return false;
        if (isLaserEnergyHatch(mte)) {
            mRejectedLaserHatch = true;
            return false;
        }
        if (mte instanceof MTEHatchEnergy) {
            return addEnergyInputToMachineList(aTileEntity, aBaseCasingIndex);
        }
        if (mte instanceof MTEHatchEnergyMulti multi) {
            if (addMultiAmpEnergyInputToMachineList(aTileEntity, aBaseCasingIndex)) {
                return true;
            }
            multi.updateTexture(aBaseCasingIndex);
            return mExoticEnergyHatches.add(multi);
        }
        return false;
    }

    public int getTotalEnergyHatchCount() {
        return mEnergyHatches.size() + mExoticEnergyHatches.size();
    }

    public int getInstalledEnergyHatchTier() {
        if (mSimulatedEnergyHatchTier >= 0) {
            return mSimulatedEnergyHatchTier;
        }
        int maxTier = 0;
        for (MTEHatchEnergy eh : mEnergyHatches) {
            if (eh != null && eh.mTier > maxTier) {
                maxTier = eh.mTier;
            }
        }
        for (MTEHatch eh : mExoticEnergyHatches) {
            if (eh != null && eh.mTier > maxTier) {
                maxTier = eh.mTier;
            }
        }
        return maxTier;
    }

    public void setSimulatedEnergyHatchTier(int tier) {
        this.mSimulatedEnergyHatchTier = tier;
    }

    public int getMaxAllowedEnergyHatchTier() {
        return PressurizedFluidHeaterStructure.getMaxAllowedEnergyHatchTier(mCoilLevel);
    }

    public long getMaxEuPerTick() {
        return PressurizedFluidHeaterStructure.getMaxEuPerTick(mRingCount, mCoilLevel);
    }

    public MTEHatchPressurizedFluid getPressurizedInputHatch() {
        return mPressurizedInputs.isEmpty() ? null : mPressurizedInputs.get(0);
    }

    public MTEHatchPressurizedFluid getPressurizedOutputHatch() {
        return mPressurizedOutputs.isEmpty() ? null : mPressurizedOutputs.get(0);
    }

    @Override
    public IStructureDefinition<MTEPressurizedFluidHeater> getStructureDefinition() {
        return PressurizedFluidHeaterStructure.getStructureDefinition();
    }

    @Override
    protected void onStructureCheckFinished(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onStructureCheckFinished(aBaseMetaTileEntity);
        if (mMachine) {
            if (aBaseMetaTileEntity != null && !aBaseMetaTileEntity.isAllowedToWork() && !mHasEverBeenEnabled) {
                aBaseMetaTileEntity.enableWorking();
            }
            mHasEverBeenEnabled = true;
        }
    }

    private void resetStructureCounters() {
        mCasingAmount = 0;
        mCoilLevel = HeatingCoilLevel.None;
        mPipeCasingTier = -1;
        mPipeCasingUniform = true;
        mRejectedLaserHatch = false;
        mPressurizedInputs.clear();
        mPressurizedOutputs.clear();
        mMaintenanceHatches.clear();
        mEnergyHatches.clear();
        mExoticEnergyHatches.clear();
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> aErrors) {
        resetStructureCounters();

        int detectedLen = PressurizedFluidHeaterStructure.detectLength(aBaseMetaTileEntity);
        List<StructureError> testErrors = new ArrayList<>();
        boolean matched = checkPiece(PressurizedFluidHeaterStructure.getShapeName(detectedLen), 1, 2, 0, testErrors);
        int matchedLen = detectedLen;

        if (!matched) {
            for (int l = PressurizedFluidHeaterStructure.MIN_LENGTH; l
                <= PressurizedFluidHeaterStructure.MAX_LENGTH; l++) {
                if (l == detectedLen) continue;
                resetStructureCounters();
                testErrors.clear();
                if (checkPiece(PressurizedFluidHeaterStructure.getShapeName(l), 1, 2, 0, testErrors)) {
                    matched = true;
                    matchedLen = l;
                    break;
                }
            }
        }

        if (!matched) {
            resetStructureCounters();
            checkPiece(PressurizedFluidHeaterStructure.getShapeName(detectedLen), 1, 2, 0, aErrors);
            if (mRejectedLaserHatch) {
                aErrors.add(StructureErrors.of("coolantloops.structure.error.heater_no_laser"));
            }
            return;
        }

        setStructureLength(matchedLen);

        checkCasingMin(aErrors, mCasingAmount, PressurizedFluidHeaterStructure.getMinCasings());

        if (mCoilLevel == null || mCoilLevel == HeatingCoilLevel.None) {
            aErrors.add(StructureErrors.of("coolantloops.structure.error.heater_missing_coils"));
        }
        if (mPipeCasingTier < 1 || mPipeCasingTier > 8) {
            aErrors.add(StructureErrors.of("coolantloops.structure.error.heater_missing_pipe_casing"));
        }
        if (!mPipeCasingUniform) {
            aErrors.add(StructureErrors.of("coolantloops.structure.error.heater_mixed_pipe_casing"));
        }

        if (mPressurizedInputs.size() != 1) {
            aErrors.add(StructureErrors.of("coolantloops.structure.error.missing_primary_inlet"));
        }
        if (mPressurizedOutputs.size() != 1) {
            aErrors.add(StructureErrors.of("coolantloops.structure.error.missing_primary_outlet"));
        }

        checkOneMaintenanceHatch(aErrors);

        if (mRejectedLaserHatch) {
            aErrors.add(StructureErrors.of("coolantloops.structure.error.heater_no_laser"));
        }

        int totalEnergyHatches = getTotalEnergyHatchCount();
        if (totalEnergyHatches == 0) {
            aErrors.add(StructureErrors.of("coolantloops.structure.error.heater_missing_energy"));
        } else if (totalEnergyHatches > 1) {
            aErrors.add(StructureErrors.of("coolantloops.structure.error.heater_multiple_energy"));
        } else {
            int hatchTier = getInstalledEnergyHatchTier();
            int maxAllowedTier = getMaxAllowedEnergyHatchTier();
            if (hatchTier > maxAllowedTier) {
                aErrors.add(StructureErrors.of("coolantloops.structure.error.heater_energy_tier_too_high"));
            }
        }

        if (!aErrors.isEmpty()) {
            return;
        }

        for (MTEHatchPressurizedFluid in : mPressurizedInputs) {
            in.updateTexture(CASING_INDEX);
            in.setMode(MTEHatchPressurizedFluid.HatchMode.SUCTION);
        }
        for (MTEHatchPressurizedFluid out : mPressurizedOutputs) {
            out.updateTexture(CASING_INDEX);
            out.setMode(MTEHatchPressurizedFluid.HatchMode.DISCHARGE);
        }
        for (MTEHatchMaintenance mh : mMaintenanceHatches) {
            mh.updateTexture(CASING_INDEX);
        }
        for (MTEHatchEnergy eh : mEnergyHatches) {
            eh.updateTexture(CASING_INDEX);
        }
        for (MTEHatch eh : mExoticEnergyHatches) {
            eh.updateTexture(CASING_INDEX);
        }

        if (getBaseMetaTileEntity() != null) {
            ItemStack icon = getMachineCraftingIcon();
            for (MTEHatchPressurizedFluid in : mPressurizedInputs) in.updateCraftingIcon(icon);
            for (MTEHatchPressurizedFluid out : mPressurizedOutputs) out.updateCraftingIcon(icon);
            for (MTEHatchMaintenance mh : mMaintenanceHatches) mh.updateCraftingIcon(icon);
            for (MTEHatchEnergy eh : mEnergyHatches) eh.updateCraftingIcon(icon);
            for (MTEHatch eh : mExoticEnergyHatches) eh.updateCraftingIcon(icon);
        }

        checkMaintenance();
        if (aBaseMetaTileEntity != null && !aBaseMetaTileEntity.isAllowedToWork() && !mHasEverBeenEnabled) {
            aBaseMetaTileEntity.enableWorking();
            mHasEverBeenEnabled = true;
        }
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        return checkMachine_TT(aBaseMetaTileEntity);
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        int length = PressurizedFluidHeaterStructure.getLengthFromTrigger(stackSize);
        buildPiece(PressurizedFluidHeaterStructure.getShapeName(length), stackSize, hintsOnly, 1, 2, 0);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        int length = PressurizedFluidHeaterStructure.getLengthFromTrigger(stackSize);
        return survivalBuildPiece(
            PressurizedFluidHeaterStructure.getShapeName(length),
            stackSize,
            1,
            2,
            0,
            elementBudget,
            env,
            false,
            true);
    }

    // --- Thermal & Electrical Physics ---

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

    /**
     * Heating efficiency multiplier based on heating coil tier (25% at Cupronickel up to 100% at MAX coil tier).
     */
    public double getCoilHeatingEfficiency() {
        return PressurizedFluidHeaterStructure.getCoilHeatingEfficiency(mCoilLevel);
    }

    /**
     * Reuses MPTR's configured {@code euPerHu} (default 0.25 EU/HU -> 4 HU per EU) without duplicating config keys.
     */
    public static double getEuPerHu() {
        ComponentData comp = ReactorConfigData.getComponentData();
        if (comp != null && comp.heatExchangers != null && !comp.heatExchangers.isEmpty()) {
            double val = comp.heatExchangers.get(0).euPerHu;
            if (val > 0.0) return val;
        }
        if (comp != null && comp.heatVents != null && !comp.heatVents.isEmpty()) {
            double val = comp.heatVents.get(0).euPerHu;
            if (val > 0.0) return val;
        }
        return 0.25;
    }

    /**
     * Heat capacity of the heater's inner core in HU/°C:
     * starts at the same value as a reactor passage of the same tier and multiplies by the number of ring layers.
     */
    public double getHeatCapacityHuPerC() {
        int rings = Math.max(1, mRingCount);
        return CoolantPassage.heatCapacity(getPassageVoltageTier()) * rings;
    }

    /**
     * Heat transfer surface area in m^2:
     * starts at 4 m^2 (configurable via {@code devices.yaml}) and multiplies by the number of ring layers.
     */
    public double getExchangeAreaM2() {
        int rings = Math.max(1, mRingCount);
        return mBaseExchangeAreaM2 * rings;
    }

    public double getMaxOperatingPressureBar() {
        return getHeaterPipeCasingTier().getMaxPressureBar();
    }

    @Override
    public double getMaxOperatingTemperatureCelsius() {
        return getHeaterPipeCasingTier().getMaxOperatingTemperatureCelsius();
    }

    public double getInnerTemperatureCelsius() {
        return mInnerTemperatureCelsius;
    }

    public void setInnerTemperatureCelsius(double tempC) {
        this.mInnerTemperatureCelsius = tempC;
    }

    public double getAmbientTemperatureCelsius() {
        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te != null && te.getWorld() != null) {
            return CoolantFluidHelper
                .calculateAmbientTemperature(te.getWorld(), te.getXCoord(), te.getYCoord(), te.getZCoord());
        }
        return mSimulatedAmbientTempCelsius;
    }

    public void setSimulatedAmbientTemperatureCelsius(double tempC) {
        this.mSimulatedAmbientTempCelsius = tempC;
    }

    public void setSimulatedStoredEU(long storedEU) {
        this.mSimulatedStoredEU = storedEU;
    }

    public long getSimulatedStoredEU() {
        return mSimulatedStoredEU;
    }

    public long getLastConsumedEuPerTick() {
        return mLastConsumedEuPerTick;
    }

    public double getLastHeatingHuPerTick() {
        return mLastHeatingHuPerTick;
    }

    public double getLastHeatTransferHuPerTick() {
        return mLastHeatTransferHuPerTick;
    }

    public double getLastHeatTransferWatts() {
        return mLastHeatTransferWatts;
    }

    public double getLastCoolantFlowLitersPerSecond() {
        return mLastCoolantFlowLitersPerSecond;
    }

    public boolean isPowerFailed() {
        if (getBaseMetaTileEntity() != null && getBaseMetaTileEntity().wasShutdown()) {
            if (getBaseMetaTileEntity().getLastShutDownReason() == ShutDownReasonRegistry.POWER_LOSS) {
                return true;
            }
        }
        return mPowerFailed;
    }

    public void setPowerFailed(boolean powerFailed) {
        this.mPowerFailed = powerFailed;
    }

    @Override
    public void onEnableWorking() {
        super.onEnableWorking();
        mPowerFailed = false;
        mWorks = true;
    }

    @Override
    public boolean isAllowedToWork() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) {
            return base.isAllowedToWork() && !mPowerFailed;
        }
        return !mPowerFailed && mWorks;
    }

    /**
     * Drains up to {@code maxEU} from the installed energy hatch (or simulated EU buffer).
     * Never triggers powerfail when less than {@code maxEU} is available; simply drains whatever is available.
     */
    public long drainUpToEnergy(long maxEU) {
        if (maxEU <= 0L) {
            return 0L;
        }
        if (mSimulatedStoredEU >= 0L) {
            long drained = Math.min(mSimulatedStoredEU, maxEU);
            mSimulatedStoredEU -= drained;
            return drained;
        }

        long remaining = maxEU;
        long totalDrained = 0L;

        for (MTEHatch tHatch : getExoticAndNormalEnergyHatchList()) {
            if (remaining <= 0L) break;
            if (tHatch == null) continue;
            IGregTechTileEntity bte = tHatch.getBaseMetaTileEntity();
            if (bte != null) {
                long stored = bte.getStoredEU();
                if (stored > 0L) {
                    long toDrain = Math.min(stored, remaining);
                    if (bte.decreaseStoredEnergyUnits(toDrain, false)) {
                        totalDrained += toDrain;
                        remaining -= toDrain;
                    }
                }
            }
        }

        if (totalDrained == 0L && getBaseMetaTileEntity() != null) {
            long stored = getBaseMetaTileEntity().getStoredEU();
            if (stored > 0L) {
                long toDrain = Math.min(stored, remaining);
                if (getBaseMetaTileEntity().decreaseStoredEnergyUnits(toDrain, false)) {
                    totalDrained += toDrain;
                }
            }
        }

        return totalDrained;
    }

    /**
     * Triggers over-temperature powerfail, shuts down the heater, and disconnects the coolant loop.
     */
    public void triggerPowerfail() {
        mPowerFailed = true;
        mLastConsumedEuPerTick = 0L;
        mLastHeatingHuPerTick = 0.0;
        mWorks = false;
        stopMachine(ShutDownReasonRegistry.POWER_LOSS);
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) {
            base.setActive(false);
            base.setShutDownReason(ShutDownReasonRegistry.POWER_LOSS);
            base.disableWorking();
        }
        if (mConnectedPump != null) {
            mConnectedPump.onDeviceDisconnected(this, getDisconnectionReason());
        }
    }

    /**
     * Executes one server tick (0.05 s) of electrical heating.
     */
    public void stepFluidHeater() {
        if (!mMachine) {
            mLastConsumedEuPerTick = 0L;
            mLastHeatingHuPerTick = 0.0;
            return;
        }

        if (mPowerFailed) {
            mLastConsumedEuPerTick = 0L;
            mLastHeatingHuPerTick = 0.0;
            IGregTechTileEntity base = getBaseMetaTileEntity();
            if (base != null && base.isActive()) {
                base.setActive(false);
            }
            return;
        }

        checkMaintenance();

        if (!isAllowedToWork()) {
            mLastConsumedEuPerTick = 0L;
            mLastHeatingHuPerTick = 0.0;
            IGregTechTileEntity base = getBaseMetaTileEntity();
            if (base != null && base.isActive()) {
                base.setActive(false);
            }
            if (mInnerTemperatureCelsius > getMaxOperatingTemperatureCelsius()) {
                triggerPowerfail();
            }
            return;
        }

        long maxEu = getMaxEuPerTick();
        long consumedEu = drainUpToEnergy(maxEu);
        mLastConsumedEuPerTick = consumedEu;

        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) {
            base.setActive(consumedEu > 0L);
        }

        if (consumedEu > 0L) {
            double eff = getEfficiencyFactor();
            double coilEff = getCoilHeatingEfficiency();
            double euPerHu = getEuPerHu();
            double addedHu = (consumedEu / euPerHu) * coilEff * eff;
            mLastHeatingHuPerTick = addedHu;
            double capacityHuPerC = getHeatCapacityHuPerC();
            if (capacityHuPerC > 0.0) {
                mInnerTemperatureCelsius += addedHu / capacityHuPerC;
            }
        } else {
            mLastHeatingHuPerTick = 0.0;
        }

        if (mInnerTemperatureCelsius > getMaxOperatingTemperatureCelsius()) {
            triggerPowerfail();
        }
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity != null && aBaseMetaTileEntity.isServerSide()) {
            stepFluidHeater();
            updateTemperatureRedstoneAndProjectRed();
        }
    }

    // --- ICoolantLoopDevice Implementation ---

    @Override
    public String getDeviceId() {
        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te != null) {
            return "heater_" + te.getXCoord() + "_" + te.getYCoord() + "_" + te.getZCoord();
        }
        return "heater_unbound";
    }

    @Override
    public double getMinorLossK() {
        return mMinorLossK;
    }

    @Override
    public double getDeviceTemperatureCelsius() {
        return mInnerTemperatureCelsius;
    }

    @Override
    public long getFillVolumeLiters() {
        return 1000L * Math.max(1, mRingCount);
    }

    @Override
    public void setLoopPump(ICoolantLoopPump pump) {
        this.mConnectedPump = pump;
    }

    public ICoolantLoopPump getLoopPump() {
        return mConnectedPump;
    }

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        MultiblockStartup.formNow(this);
    }

    @Override
    public void collectStructureChunks(Set<Long> chunks) {
        StructureChunks.collect(this, PressurizedFluidHeaterStructure.getShapeName(mStructureLength), 1, 2, 0, chunks);
    }

    @Override
    public boolean isDeviceDisconnected() {
        return mPowerFailed || !mMachine;
    }

    @Override
    public String getDisconnectionReason() {
        if (mPowerFailed) {
            return CoolantLocalization.format(
                "coolantloops.crawler.heater_powerfailed",
                mInnerTemperatureCelsius,
                getMaxOperatingTemperatureCelsius());
        }
        if (!mMachine) {
            return CoolantLocalization.get("coolantloops.crawler.heater_unformed_generic");
        }
        return null;
    }

    /**
     * Performs convective heat transfer between the heater's inner thermal mass and the circulating coolant.
     * Transfers heat in either direction (inner <-> coolant) whether the heater is powered on or powered off.
     */
    @Override
    public void processThermalExchange(double coolantFlowRateM3s, double dt, CoolantFluidProperty fluid,
        LoopSegment segment) {
        mBoundSegment = segment;
        mLoopFluid = fluid;
        mLastCoolantFlowLitersPerSecond = Math.max(0.0, coolantFlowRateM3s * 1000.0);

        if (isDeviceDisconnected()) {
            mLastHeatTransferHuPerTick = 0.0;
            mLastHeatTransferWatts = 0.0;
            if (mConnectedPump != null) {
                mConnectedPump.onDeviceDisconnected(this, getDisconnectionReason());
            }
            return;
        }

        if (segment == null || fluid == null || coolantFlowRateM3s <= 1e-9 || dt <= 0.0) {
            mLastHeatTransferHuPerTick = 0.0;
            mLastHeatTransferWatts = 0.0;
            return;
        }

        double capacityHuPerC = getHeatCapacityHuPerC();
        double areaM2 = getExchangeAreaM2();
        double eff = getEfficiencyFactor();
        int passageTier = getPassageVoltageTier();

        double transferredHu = CoolantPassage.exchange(
            mInnerTemperatureCelsius,
            capacityHuPerC,
            passageTier,
            areaM2,
            eff,
            coolantFlowRateM3s,
            dt,
            fluid,
            segment);

        if (capacityHuPerC > 0.0) {
            mInnerTemperatureCelsius -= transferredHu / capacityHuPerC;
        }

        double ticksInDt = dt / 0.05;
        mLastHeatTransferHuPerTick = ticksInDt > 0.0 ? (transferredHu / ticksInDt) : transferredHu;
        mLastHeatTransferWatts = (transferredHu * CoolantPassage.JOULES_PER_HU) / dt;

        if (!mPressurizedInputs.isEmpty() && mPressurizedInputs.get(0) != null) {
            mPressurizedInputs.get(0)
                .setCurrentTemperatureCelsius(segment.getIncomingTemperatureCelsius());
        }
        if (!mPressurizedOutputs.isEmpty() && mPressurizedOutputs.get(0) != null) {
            mPressurizedOutputs.get(0)
                .setCurrentTemperatureCelsius(segment.getCurrentTemperatureCelsius());
        }

        if (mInnerTemperatureCelsius > getMaxOperatingTemperatureCelsius()) {
            triggerPowerfail();
        }
    }

    // --- Energy Detector Cover, Redstone, & ProjectRed Integration ---

    @Override
    public long maxEUStore() {
        return (long) Math.ceil(getMaxOperatingTemperatureCelsius());
    }

    public void setSimulatedEnergyDetectorCover(ForgeDirection side, long threshold, boolean inverted) {
        this.mSimulatedDetectorSide = side != null ? side : ForgeDirection.UNKNOWN;
        this.mSimulatedDetectorThreshold = threshold;
        this.mSimulatedDetectorInverted = inverted;
    }

    public byte getRedstoneOutput() {
        return mRedstoneOutput;
    }

    public int getFineOutputStrength() {
        return mFineOutputStrength;
    }

    public int getBundledChannelMode() {
        return mBundledChannelMode;
    }

    public void setBundledChannelMode(int mode) {
        if (mode < 0 || mode > 16) {
            mode = 0;
        }
        this.mBundledChannelMode = mode;
    }

    public static int computeTemperatureFineSignal(double currentTempC, double ambientTempC, double thresholdTempC,
        boolean inverted) {
        double span = thresholdTempC - ambientTempC;
        double fraction;
        if (span <= 1e-6) {
            fraction = currentTempC >= thresholdTempC ? 1.0 : 0.0;
        } else {
            fraction = (currentTempC - ambientTempC) / span;
        }
        if (fraction < 0.0) fraction = 0.0;
        if (fraction > 1.0) fraction = 1.0;
        if (inverted) {
            fraction = 1.0 - fraction;
        }
        return (int) Math.round(fraction * 255.0);
    }

    public static byte computeTemperatureRedstoneSignal(double currentTempC, double ambientTempC, double thresholdTempC,
        boolean inverted) {
        double span = thresholdTempC - ambientTempC;
        double fraction;
        if (span <= 1e-6) {
            fraction = currentTempC >= thresholdTempC ? 1.0 : 0.0;
        } else {
            fraction = (currentTempC - ambientTempC) / span;
        }
        if (fraction < 0.0) fraction = 0.0;
        if (fraction > 1.0) fraction = 1.0;
        if (inverted) {
            fraction = 1.0 - fraction;
        }
        return (byte) Math.min(15, Math.max(0, (int) Math.floor(fraction * 15.0 + 1e-9)));
    }

    public void updateTemperatureRedstoneAndProjectRed() {
        double ambient = getAmbientTemperatureCelsius();
        double maxTemp = getMaxOperatingTemperatureCelsius();

        boolean foundCover = false;
        byte highestRedstone = 0;
        int highestFine = 0;

        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) {
            for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
                Cover cover = base.getCoverAtSide(side);
                if (cover instanceof CoverEUMeter euMeter) {
                    foundCover = true;
                    long rawThreshold = euMeter.getThreshold();
                    double threshold = rawThreshold > 0L ? (double) rawThreshold : maxTemp;
                    boolean inverted = euMeter.isInverted();
                    byte rs = computeTemperatureRedstoneSignal(mInnerTemperatureCelsius, ambient, threshold, inverted);
                    int fine = computeTemperatureFineSignal(mInnerTemperatureCelsius, ambient, threshold, inverted);
                    base.setInternalOutputRedstoneSignal(side, rs);
                    base.setOutputRedstoneSignal(side, rs);
                    if (fine >= highestFine) {
                        highestFine = fine;
                        highestRedstone = rs;
                    }
                }
            }
        }

        if (mSimulatedDetectorSide != ForgeDirection.UNKNOWN) {
            foundCover = true;
            double threshold = mSimulatedDetectorThreshold > 0L ? (double) mSimulatedDetectorThreshold : maxTemp;
            byte rs = computeTemperatureRedstoneSignal(
                mInnerTemperatureCelsius,
                ambient,
                threshold,
                mSimulatedDetectorInverted);
            int fine = computeTemperatureFineSignal(
                mInnerTemperatureCelsius,
                ambient,
                threshold,
                mSimulatedDetectorInverted);
            if (base != null) {
                base.setInternalOutputRedstoneSignal(mSimulatedDetectorSide, rs);
                base.setOutputRedstoneSignal(mSimulatedDetectorSide, rs);
            }
            highestRedstone = rs;
            highestFine = fine;
        }

        if (!foundCover) {
            highestRedstone = 0;
            highestFine = 0;
        }

        mRedstoneOutput = highestRedstone;
        mFineOutputStrength = highestFine;

        Arrays.fill(mBundledSignal, (byte) 0);
        if (foundCover) {
            byte bundledVal = (byte) (mFineOutputStrength & 0xFF);
            if (mBundledChannelMode == 0) {
                Arrays.fill(mBundledSignal, bundledVal);
            } else if (mBundledChannelMode >= 1 && mBundledChannelMode <= 16) {
                mBundledSignal[mBundledChannelMode - 1] = bundledVal;
            }
        }
    }

    public boolean hasEnergyDetectorCoverAtSide(ForgeDirection side) {
        if (side == null || side == ForgeDirection.UNKNOWN) return false;
        if (mSimulatedDetectorSide == side) return true;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) {
            return base.getCoverAtSide(side) instanceof CoverEUMeter;
        }
        return false;
    }

    public boolean hasAnyEnergyDetectorCover() {
        if (mSimulatedDetectorSide != ForgeDirection.UNKNOWN) return true;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) {
            for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
                if (base.getCoverAtSide(side) instanceof CoverEUMeter) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    @Optional.Method(modid = "ProjRed|Transmission")
    public byte[] getBundledSignal(int dir) {
        ForgeDirection side = ForgeDirection.getOrientation(dir);
        if (hasEnergyDetectorCoverAtSide(side) || hasAnyEnergyDetectorCover()) {
            return mBundledSignal.clone();
        }
        return null;
    }

    public byte[] getBundledSignalSnapshot() {
        return mBundledSignal.clone();
    }

    // --- GUI, Scanner, Waila, Tooltip, & NBT ---

    @Override
    public boolean checkRecipe(ItemStack aStack) {
        return true;
    }

    @Override
    public boolean showRecipeTextInGUI() {
        return false;
    }

    @Override
    public boolean shouldDisplayCheckRecipeResult() {
        return false;
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
    public boolean supportsVoidProtection() {
        return false;
    }

    @Override
    public boolean supportsInputSeparation() {
        return false;
    }

    @Override
    public boolean supportsBatchMode() {
        return false;
    }

    @Override
    public ButtonWidget createLockToSingleRecipeButton(IWidgetBuilder<?> builder) {
        return (ButtonWidget) new ButtonWidget().setSize(0, 0)
            .setEnabled(false);
    }

    @Override
    public String[] getInfoData() {
        HeaterPipeCasingTier pipeTier = getHeaterPipeCasingTier();
        String coilName = mCoilLevel != null ? mCoilLevel.getName() : "None";
        return new String[] {
            String.format(
                Locale.US,
                "Inner temperature: %.1f / %.1f °C",
                mInnerTemperatureCelsius,
                getMaxOperatingTemperatureCelsius()),
            String.format(Locale.US, "Heat capacity: %.1f HU/°C", getHeatCapacityHuPerC()),
            String.format(Locale.US, "Heat transfer area: %.1f m²", getExchangeAreaM2()),
            String.format(Locale.US, "Coolant flow rate: %.1f L/s", mLastCoolantFlowLitersPerSecond),
            String.format(
                Locale.US,
                "Heat transfer rate: %.1f HU/t (%.1f kW)",
                mLastHeatTransferHuPerTick,
                mLastHeatTransferWatts / 1000.0),
            String
                .format(Locale.US, "Electrical consumption: %,d / %,d EU/t", mLastConsumedEuPerTick, getMaxEuPerTick()),
            String.format(
                Locale.US,
                "Item pipe casing: %s (max %.0f bar)",
                pipeTier.getDisplayName(),
                pipeTier.getMaxPressureBar()),
            String.format(
                Locale.US,
                "Heating coils: %s (%d rings, %.1f%% heating eff, max %s energy hatch)",
                coilName,
                mRingCount,
                getCoilHeatingEfficiency() * 100.0,
                GTValues.VN[Math.min(GTValues.VN.length - 1, getMaxAllowedEnergyHatchTier())]) };
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        if (player != null) {
            super.getWailaNBTData(player, tile, tag, world, x, y, z);
        }
        tag.setDouble("innerTempC", mInnerTemperatureCelsius);
        tag.setDouble("maxTempC", getMaxOperatingTemperatureCelsius());
        tag.setDouble("flowLPerS", mLastCoolantFlowLitersPerSecond);
        tag.setDouble("transferHuPerT", mLastHeatTransferHuPerTick);
        tag.setBoolean("powerFailed", mPowerFailed);
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currentTip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        super.getWailaBody(itemStack, currentTip, accessor, config);
        final NBTTagCompound tag = accessor.getNBTData();
        if (tag.hasKey("innerTempC")) {
            currentTip.add(
                String.format(
                    Locale.US,
                    "Inner temperature: %.1f / %.1f °C",
                    tag.getDouble("innerTempC"),
                    tag.getDouble("maxTempC")));
            currentTip.add(String.format(Locale.US, "Coolant flow: %.1f L/s", tag.getDouble("flowLPerS")));
            currentTip.add(String.format(Locale.US, "Heat transfer: %.1f HU/t", tag.getDouble("transferHuPerT")));
        }
        if (tag.getBoolean("powerFailed")) {
            currentTip.add(EnumChatFormatting.RED + "Over-temperature powerfail (loop disconnected)");
        }
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("mStructureLength", mStructureLength);
        aNBT.setInteger("mRingCount", mRingCount);
        aNBT.setInteger("mPipeCasingTier", mPipeCasingTier);
        if (mCoilLevel != null) {
            aNBT.setString("mCoilLevel", mCoilLevel.name());
        }
        aNBT.setDouble("mInnerTemperatureCelsius", mInnerTemperatureCelsius);
        aNBT.setLong("mLastConsumedEuPerTick", mLastConsumedEuPerTick);
        aNBT.setDouble("mLastHeatingHuPerTick", mLastHeatingHuPerTick);
        aNBT.setDouble("mLastHeatTransferHuPerTick", mLastHeatTransferHuPerTick);
        aNBT.setDouble("mLastHeatTransferWatts", mLastHeatTransferWatts);
        aNBT.setDouble("mLastCoolantFlowLitersPerSecond", mLastCoolantFlowLitersPerSecond);
        aNBT.setBoolean("mPowerFailed", mPowerFailed);
        aNBT.setBoolean("mHasEverBeenEnabled", mHasEverBeenEnabled);
        aNBT.setByte("mRedstoneOutput", mRedstoneOutput);
        aNBT.setInteger("mFineOutputStrength", mFineOutputStrength);
        aNBT.setInteger("mBundledChannelMode", mBundledChannelMode);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        if (aNBT.hasKey("mStructureLength")) {
            setStructureLength(aNBT.getInteger("mStructureLength"));
        }
        if (aNBT.hasKey("mRingCount")) {
            setRingCount(aNBT.getInteger("mRingCount"));
        }
        if (aNBT.hasKey("mPipeCasingTier")) {
            mPipeCasingTier = aNBT.getInteger("mPipeCasingTier");
        }
        if (aNBT.hasKey("mCoilLevel")) {
            try {
                mCoilLevel = HeatingCoilLevel.valueOf(aNBT.getString("mCoilLevel"));
            } catch (IllegalArgumentException ignored) {
                mCoilLevel = HeatingCoilLevel.None;
            }
        }
        if (aNBT.hasKey("mInnerTemperatureCelsius")) {
            mInnerTemperatureCelsius = aNBT.getDouble("mInnerTemperatureCelsius");
        }
        if (aNBT.hasKey("mLastConsumedEuPerTick")) {
            mLastConsumedEuPerTick = aNBT.getLong("mLastConsumedEuPerTick");
        }
        if (aNBT.hasKey("mLastHeatingHuPerTick")) {
            mLastHeatingHuPerTick = aNBT.getDouble("mLastHeatingHuPerTick");
        }
        if (aNBT.hasKey("mLastHeatTransferHuPerTick")) {
            mLastHeatTransferHuPerTick = aNBT.getDouble("mLastHeatTransferHuPerTick");
        }
        if (aNBT.hasKey("mLastHeatTransferWatts")) {
            mLastHeatTransferWatts = aNBT.getDouble("mLastHeatTransferWatts");
        }
        if (aNBT.hasKey("mLastCoolantFlowLitersPerSecond")) {
            mLastCoolantFlowLitersPerSecond = aNBT.getDouble("mLastCoolantFlowLitersPerSecond");
        }
        if (aNBT.hasKey("mPowerFailed")) {
            mPowerFailed = aNBT.getBoolean("mPowerFailed");
        }
        if (aNBT.hasKey("mHasEverBeenEnabled")) {
            mHasEverBeenEnabled = aNBT.getBoolean("mHasEverBeenEnabled");
        }
        if (aNBT.hasKey("mRedstoneOutput")) {
            mRedstoneOutput = aNBT.getByte("mRedstoneOutput");
        }
        if (aNBT.hasKey("mFineOutputStrength")) {
            mFineOutputStrength = aNBT.getInteger("mFineOutputStrength");
        }
        if (aNBT.hasKey("mBundledChannelMode")) {
            mBundledChannelMode = aNBT.getInteger("mBundledChannelMode");
        }
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gt.multiblock.pressurized_fluid_heater.machine_type"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.pressurized_fluid_heater.desc1"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.pressurized_fluid_heater.desc2"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.pressurized_fluid_heater.desc3"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.pressurized_fluid_heater.desc4"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.pressurized_fluid_heater.desc5"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.pressurized_fluid_heater.desc6"))
            .addSeparator()
            .beginVariableStructureBlock(3, 3, 3, 3, 3, 11, false)
            .addController(
                StatCollector.translateToLocal("gt.multiblock.pressurized_fluid_heater.structure.controller"))
            .addCasingInfoMin(
                StatCollector.translateToLocal("gt.multiblock.pressurized_fluid_heater.structure.casings"),
                PressurizedFluidHeaterStructure.getMinCasings(),
                false)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.pressurized_fluid_heater.structure.coils"),
                StatCollector.translateToLocal("gt.multiblock.pressurized_fluid_heater.structure.coils_pos"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.pressurized_fluid_heater.structure.pipe_casings"),
                StatCollector.translateToLocal("gt.multiblock.pressurized_fluid_heater.structure.pipe_casings_pos"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.pressurized_fluid_heater.structure.hp_in"),
                StatCollector.translateToLocal("gt.multiblock.pressurized_fluid_heater.structure.hp_in_pos"),
                2)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.pressurized_fluid_heater.structure.hp_out"),
                StatCollector.translateToLocal("gt.multiblock.pressurized_fluid_heater.structure.hp_out_pos"),
                3)
            .addMaintenanceHatch(
                StatCollector.translateToLocal("gt.multiblock.pressurized_fluid_heater.structure.maintenance"),
                1)
            .addEnergyHatch(
                StatCollector.translateToLocal("gt.multiblock.pressurized_fluid_heater.structure.energy"),
                1)
            .addSubChannel(CoolantStructureChannels.HEATER_LENGTH)
            .addSubChannel(GTStructureChannels.HEATING_COIL)
            .addSubChannel(GTStructureChannels.ITEM_PIPE_CASING)
            .toolTipFinisher(StatCollector.translateToLocal("gt.multiblock.coolantloops.finisher"));
        return tt;
    }
}
