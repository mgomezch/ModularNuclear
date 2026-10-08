package com.gtnewhorizons.modularnuclear.common.metatileentity.multi;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.alignment.enumerable.Rotation;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizons.modularnuclear.ModularNuclear;
import com.gtnewhorizons.modularnuclear.common.block.BlockNuclearCasing;
import com.gtnewhorizons.modularnuclear.common.block.ModBlocks;
import com.gtnewhorizons.modularnuclear.common.entity.EntityMeltdownFallout;
import com.gtnewhorizons.modularnuclear.common.gui.NuclearReactorGui;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearBus;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControl;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHatch;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHighPressure;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.structure.NuclearReactorStructure;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.util.NuclearReactorTelemetry;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.util.NuclearReactorTooltipValues;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.util.NuclearReactorWailaProvider;
import com.gtnewhorizons.modularnuclear.common.nuclear.INuclearTile;
import com.gtnewhorizons.modularnuclear.common.nuclear.NeutronType;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearFuelType;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.ReactorGridSyncData;
import com.gtnewhorizons.modularnuclear.common.nuclearcontrol.ItemCardModularNuclear;
import com.gtnewhorizons.modularnuclear.common.nuclearcontrol.ItemKitModularNuclear;
import com.gtnewhorizons.modularnuclear.common.nuclearcontrol.NuclearControlIntegration;
import com.gtnewhorizons.modularnuclear.common.textures.ModularNuclearTextures;
import com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification;
import com.gtnewhorizons.modularui.api.drawable.UITexture;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.api.widget.IWidgetBuilder;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;
import com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;

import cpw.mods.fml.common.Loader;
import gregtech.GTMod;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.SoundResource;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.ICasingTextureProvider;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.items.ItemRadioactiveCell;
import gregtech.api.items.ItemRadioactiveCellIC;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.GTLog;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gregtech.common.pollution.Pollution;
import ic2.api.reactor.IReactor;
import ic2.api.reactor.IReactorComponent;
import ic2.core.item.reactor.ItemReactorMOX;
import ic2.core.item.reactor.ItemReactorUranium;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.overlay.tooltiprenderers.TTRenderBar;
import shedar.mods.ic2.nuclearcontrol.panel.CardWrapperImpl;

public class MTENuclearReactor extends MTEEnhancedMultiBlockBase<MTENuclearReactor>
    implements ISurvivalConstructable, ICasingTextureProvider {

    public static final int CASING_INDEX = BlockNuclearCasing.CASING_TEXTURE_INDEX;
    public static final String STRUCTURE_TIER_1 = NuclearReactorStructure.STRUCTURE_TIER_1;
    public static final String STRUCTURE_TIER_2 = NuclearReactorStructure.STRUCTURE_TIER_2;
    public static final String STRUCTURE_TIER_3 = NuclearReactorStructure.STRUCTURE_TIER_3;

    public static final String[][] SHAPE_TIER_1 = NuclearReactorStructure.SHAPE_TIER_1;
    public static final String[][] SHAPE_TIER_2 = NuclearReactorStructure.SHAPE_TIER_2;
    public static final String[][] SHAPE_TIER_3 = NuclearReactorStructure.SHAPE_TIER_3;

    public int mPipeTier = -1;
    public int gridSize = 0;
    public int coreDimension = 0;
    public INuclearTile[][] mGrid = null;
    public final List<IGregTechTileEntity> mNuclearTiles = new ArrayList<>();
    public final List<MTEHatchNuclearHighPressure> mTopHighPressureHatches = new ArrayList<>();
    public final List<MTEHatchNuclearHighPressure> mBottomHighPressureHatches = new ArrayList<>();
    public final List<MTEHatchNuclearControlRod> mBottomControlRodHatches = new ArrayList<>();

    public int getReactorTier() {
        if (gridSize == 5) return 1;
        if (gridSize == 9) return 2;
        if (gridSize == 13) return 3;
        return 0;
    }

    // Telemetry
    public double mCachedAmbientTemp = NuclearSimulationEngine.DEFAULT_AMBIENT_TEMP;
    public double mCoreTemp = NuclearSimulationEngine.DEFAULT_AMBIENT_TEMP;
    public double mAvgTemp = NuclearSimulationEngine.DEFAULT_AMBIENT_TEMP;
    public double mReactorDamage = 0.0;
    public boolean mScram = false;
    public int mNeutronsProduced = 0;
    public int mFastAbsorbed = 0;
    public int mThermalAbsorbed = 0;
    public int mEscapedNeutrons = 0;
    public double mReactivity = 1.0;
    public long mDirectPowerEUt = 0;
    public long mWallNeutronAccumulator = 0;
    public int mWallMaintenanceTimer = 0;
    public int mOutputCoolantRate = 0;
    public String mOutputCoolantName = "";
    public static final int GUI_MODE_COMPONENTS = 0;
    public static final int GUI_MODE_TEMPERATURE = 1;
    public static final int GUI_MODE_HEAT_OUTPUT = 2;
    public static final int GUI_MODE_NEUTRON_FLUX = 3;
    public static final int GUI_MODE_NEUTRON_ABSORPTION = 4;
    public static final int GUI_MODE_CONTROL_RODS = 5;
    private static final UITexture TAB_NEI_SELECTED = UITexture
        .partly(new ResourceLocation("nei", "textures/nei_tabbed_sprites.png"), 256, 256, 0, 16, 24, 40);
    private static final UITexture TAB_NEI_UNSELECTED = UITexture
        .partly(new ResourceLocation("nei", "textures/nei_tabbed_sprites.png"), 256, 256, 24, 16, 48, 40);
    public int mCurrentGuiMode = GUI_MODE_COMPONENTS;
    public ReactorGridSyncData mClientGridData = null;

    // Client-side Cherenkov radiation state
    public int mClientTier = 1;
    public int mClientWaterBlocks = -1;
    public double mClientWaterMinY = 0;
    public double mClientWaterMaxY = 0;
    public double mClientCenterX = 0;
    public double mClientCenterZ = 0;
    public double mClientRadius = 2.5;
    public float mClientCherenkovIntensity = 0f;

    public int mHatchTier = -1;
    public boolean mHatchTierInconsistent = false;
    public int mCasing = 0;
    public final List<MTEHatchNuclearControl> mControlHatches = new ArrayList<>();
    public boolean mWasMachineFormed = false;
    public final List<IGregTechTileEntity> mLastFormedNuclearTiles = new ArrayList<>();
    public boolean mWorldSaved = false;

    // Telemetry - Operating parameters (Min / Max / Avg)
    public double mMinTileTemp = 0.0;
    public double mMaxTileTemp = 0.0;
    public double mAvgTileTemp = 0.0;
    public double mMinCoolantItemDur = 0.0;
    public double mMaxCoolantItemDur = 0.0;
    public double mAvgCoolantItemDur = 0.0;
    public double mMinFuelItemDur = 0.0;
    public double mMaxFuelItemDur = 0.0;
    public double mAvgFuelItemDur = 0.0;
    public double mMinCoolantHatchFill = 0.0;
    public double mMaxCoolantHatchFill = 0.0;
    public double mAvgCoolantHatchFill = 0.0;
    public double mMinFuelHatchFill = 0.0;
    public double mMaxFuelHatchFill = 0.0;
    public double mAvgFuelHatchFill = 0.0;

    // Telemetry - Item Counts
    public int mTotalFuelItems = 0;
    public int mTotalCoolantItems = 0;

    // Telemetry - Fluid Volumes and Capacities
    public long mTotalCoolantFluid = 0;
    public long mTotalCoolantCapacity = 0;
    public long mTotalFuelFluid = 0;
    public long mTotalFuelCapacity = 0;

    // Telemetry - Hatch Counts
    public int mCoolantHatchCount = 0;
    public int mFuelHatchCount = 0;

    // Telemetry - Last Cycle Totals
    public int mZeroedCoolantItemsLastCycle = 0;
    public int mZeroedFuelItemsLastCycle = 0;
    public long mConsumedCoolantLastCycle = 0;
    public long mProducedHotCoolantLastCycle = 0;
    public long mTransmutationLossLastCycle = 0;
    public long mTransmutationByproductsLastCycle = 0;
    public long mDepletedLiquidFuelLastCycle = 0;

    // Transient Cycle Accumulators
    public int mCycleZeroedCoolantItems = 0;
    public int mCycleZeroedFuelItems = 0;
    public long mCycleConsumedCoolant = 0;
    public long mCycleProducedHotCoolant = 0;
    public long mCycleTransmutationLoss = 0;
    public long mCycleTransmutationByproducts = 0;
    public long mCycleDepletedLiquidFuel = 0;

    public boolean isDisablingAllowed() {
        return false;
    }

    @Override
    public IAlignmentLimits getInitialAlignmentLimits() {
        return (d, r, f) -> d.offsetY == 0 && f.isNotFlipped();
    }

    private ExtendedFacing mReactorExtendedFacing = null;

    @Override
    public ExtendedFacing getExtendedFacing() {
        if (mReactorExtendedFacing != null) {
            return mReactorExtendedFacing;
        }
        return super.getExtendedFacing();
    }

    @Override
    public void setExtendedFacing(ExtendedFacing newExtendedFacing) {
        this.mReactorExtendedFacing = newExtendedFacing;
        if (getBaseMetaTileEntity() != null) {
            super.setExtendedFacing(newExtendedFacing);
        }
    }

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        if (!mWorldSaved) {
            mWrench = false;
            mScrewdriver = false;
            mSoftMallet = false;
            mHardHammer = false;
            mSolderingTool = false;
            mCrowbar = false;
        }
    }

    public boolean addNuclearControlHatchToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity mte = aTileEntity.getMetaTileEntity();
        if (mte instanceof MTEHatchNuclearControl controlHatch) {
            controlHatch.mReactor = this;
            mControlHatches.add(controlHatch);
            return true;
        }
        return false;
    }

    @Override
    public void clearHatches() {
        super.clearHatches();
        mControlHatches.clear();
        for (MTEHatchNuclearHighPressure hp : mTopHighPressureHatches) {
            hp.setOppositeHatch(null);
            hp.setConnectedPump(null);
        }
        for (MTEHatchNuclearHighPressure hp : mBottomHighPressureHatches) {
            hp.setOppositeHatch(null);
            hp.setConnectedPump(null);
        }
        mTopHighPressureHatches.clear();
        mBottomHighPressureHatches.clear();
        mBottomControlRodHatches.clear();
    }

    public double getMaintenanceEfficiency() {
        if (!mMachine) return 0.0;
        int ideal = getIdealStatus();
        if (ideal <= 0) return 1.0;
        int repair = getRepairStatus();
        double baseEff = Math.max(0.0, Math.min(1.0, (double) repair / (double) ideal));
        double damageFactor = Math.max(0.0, Math.min(1.0, (100.0 - mReactorDamage) / 100.0));
        return baseEff * damageFactor;
    }

    public void causeNewMaintenanceIssue() {
        List<Integer> working = new ArrayList<>();
        if (mWrench) working.add(0);
        if (mScrewdriver) working.add(1);
        if (mSoftMallet) working.add(2);
        if (mHardHammer) working.add(3);
        if (mSolderingTool) working.add(4);
        if (mCrowbar) working.add(5);
        if (!working.isEmpty()) {
            IGregTechTileEntity base = getBaseMetaTileEntity();
            int randIdx = (base != null) ? base.getRandomNumber(working.size())
                : (int) (Math.random() * working.size());
            int pick = working.get(randIdx);
            switch (pick) {
                case 0 -> mWrench = false;
                case 1 -> mScrewdriver = false;
                case 2 -> mSoftMallet = false;
                case 3 -> mHardHammer = false;
                case 4 -> mSolderingTool = false;
                case 5 -> mCrowbar = false;
            }
            if (base != null) {
                base.markDirty();
            }
        }
    }

    public static ItemStack getNuclearHatchStack(int tier) {
        return NuclearReactorStructure.getNuclearHatchStack(tier);
    }

    public ForgeDirection getNuclearHatchFacing() {
        ExtendedFacing ef = getExtendedFacing();
        return ef != null ? ef.getRelativeUpInWorld() : ForgeDirection.UP;
    }

    public int getPipeTier() {
        return mPipeTier;
    }

    public void setPipeTier(int aPipeTier) {
        this.mPipeTier = aPipeTier;
    }

    public static String getPipeTierName(int tier) {
        return NuclearSimulationEngine.getPipeTierName(tier);
    }

    public MTENuclearReactor(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTENuclearReactor(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTENuclearReactor(this.mName);
    }

    @Override
    public ITexture getCasingTexture() {
        ITexture casingTex = gregtech.api.enums.Textures.BlockIcons.getCasingTextureForId(CASING_INDEX);
        if (casingTex == null) {
            casingTex = gregtech.api.enums.Textures.BlockIcons.casingTexturePages[0][16];
        }
        return casingTex;
    }

    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public void registerIcons(net.minecraft.client.renderer.texture.IIconRegister aBlockIconRegister) {
        ModularNuclearTextures.init();
        super.registerIcons(aBlockIconRegister);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        ITexture casingTex = getCasingTexture();
        if (side == aFacing) {
            ModularNuclearTextures.init();
            gregtech.api.interfaces.IIconContainer overlay = aActive
                ? ModularNuclearTextures.OVERLAY_FRONT_FISSION_REACTOR_ACTIVE
                : ModularNuclearTextures.OVERLAY_FRONT_FISSION_REACTOR;
            if (overlay != null) {
                return new ITexture[] { casingTex, TextureFactory.builder()
                    .addIcon(overlay)
                    .extFacing()
                    .build() };
            }
            return new ITexture[] { casingTex };
        }
        return new ITexture[] { casingTex };
    }

    @Override
    public IStructureDefinition<MTENuclearReactor> getStructureDefinition() {
        return NuclearReactorStructure.getStructureDefinition();
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        return NuclearReactorTooltipValues.createTooltip();
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        int tier = stackSize == null ? 1 : stackSize.stackSize;
        if (tier >= 3) {
            buildPiece(STRUCTURE_TIER_3, stackSize, hintsOnly, 7, 3, 0);
        } else if (tier == 2) {
            buildPiece(STRUCTURE_TIER_2, stackSize, hintsOnly, 5, 3, 0);
        } else {
            buildPiece(STRUCTURE_TIER_1, stackSize, hintsOnly, 3, 3, 0);
        }
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        int tier = stackSize == null ? 1 : stackSize.stackSize;
        if (tier >= 3) {
            return survivalBuildPiece(STRUCTURE_TIER_3, stackSize, 7, 3, 0, elementBudget, env, false, true);
        } else if (tier == 2) {
            return survivalBuildPiece(STRUCTURE_TIER_2, stackSize, 5, 3, 0, elementBudget, env, false, true);
        } else {
            return survivalBuildPiece(STRUCTURE_TIER_1, stackSize, 3, 3, 0, elementBudget, env, false, true);
        }
    }

    public void updateNuclearTilesPipeTier() {
        for (IGregTechTileEntity te : mNuclearTiles) {
            if (te != null && te.getMetaTileEntity() instanceof MTEHatchNuclearHatch hatch) {
                hatch.setReactorPipeTier(mPipeTier);
            }
        }
    }

    public void explodeReactor(boolean highPressure, String reason) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || !base.isServerSide()) return;

        GTLog.writeExplosionLog(this, reason);
        World world = base.getWorld();
        int cX = base.getXCoord();
        int cY = base.getYCoord();
        int cZ = base.getZCoord();

        // High pressure causes twice the explosion radius (18.0F vs 9.0F from large boilers)
        float strength = highPressure ? 18.0F : 9.0F;

        Pollution.addPollution(base, GTMod.proxy.mPollutionOnExplosion * (highPressure ? 4 : 2));

        for (IGregTechTileEntity te : mNuclearTiles) {
            if (te != null && !te.isDead()) {
                world.setBlock(te.getXCoord(), te.getYCoord(), te.getZCoord(), Blocks.air);
            }
        }

        for (MTEHatchNuclearHighPressure bottom : mBottomHighPressureHatches) {
            IGregTechTileEntity bTe = bottom.getBaseMetaTileEntity();
            if (bTe != null && !bTe.isDead()) {
                world.setBlock(bTe.getXCoord(), bTe.getYCoord(), bTe.getZCoord(), Blocks.air);
            }
        }

        for (MTEHatchNuclearControlRod bottom : mBottomControlRodHatches) {
            IGregTechTileEntity bTe = bottom.getBaseMetaTileEntity();
            if (bTe != null && !bTe.isDead()) {
                world.setBlock(bTe.getXCoord(), bTe.getYCoord(), bTe.getZCoord(), Blocks.air);
            }
        }

        world.setBlock(cX, cY, cZ, Blocks.air);

        GTUtility.sendSoundToPlayers(
            world,
            SoundResource.IC2_MACHINES_MACHINE_OVERLOAD,
            1.0F,
            -1,
            cX + 0.5,
            cY + 0.5,
            cZ + 0.5);

        if (GregTechAPI.sMachineExplosions) {
            world.createExplosion(null, cX + 0.5, cY + 0.5, cZ + 0.5, strength, true);
        }
    }

    public void triggerThermalShock(MTEHatchNuclearHatch hatch, String reason) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || !base.isServerSide()) return;

        try {
            GTLog.writeExplosionLog(this, "THERMAL SHOCK: " + reason);
        } catch (Throwable ignored) {}

        // Void the coolant in the affected hatch (flashes into steam)
        if (hatch != null) {
            hatch.mInputFluid = null;
            hatch.mWasDry = true;
            if (hatch.getBaseMetaTileEntity() != null) {
                hatch.getBaseMetaTileEntity()
                    .markDirty();
            }
        }

        // Hot-dry-hatch conduction-coolant flash condition increases reactor damage by 2%
        mReactorDamage = Math.min(100.0, mReactorDamage + 2.0);
        if (mReactorDamage >= 100.0) {
            triggerMeltdown();
            return;
        }

        // Increase maintenance issues by 1 up to the maximum (instead of maxing out all issues)
        causeNewMaintenanceIssue();
        super.mEfficiency = (int) Math.round(getMaintenanceEfficiency() * 10000);

        // Issue powerfail notification to player (without stopping machine entity)
        try {
            if (GTMod.proxy != null && GTMod.proxy.powerfailTracker != null) {
                GTMod.proxy.powerfailTracker.createPowerfailEvent(base);
            }
        } catch (Throwable ignored) {}

        try {
            World world = base.getWorld();
            int cX = base.getXCoord();
            int cY = base.getYCoord();
            int cZ = base.getZCoord();
            GTUtility.sendSoundToPlayers(
                world,
                SoundResource.IC2_MACHINES_MACHINE_OVERLOAD,
                0.8F,
                0.5F,
                cX + 0.5,
                cY + 0.5,
                cZ + 0.5);
        } catch (Throwable ignored) {}
    }

    public void triggerDryCoolantShutdown(String reason) {
        triggerThermalShock(null, reason);
    }

    public void triggerMeltdown() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || !base.isServerSide()) return;
        World world = base.getWorld();
        if (world == null) return;

        List<ChunkCoordinates> hollowCoords = new ArrayList<>();
        double sumX = 0;
        double sumY = 0;
        double sumZ = 0;
        int count = 0;

        for (IGregTechTileEntity te : mNuclearTiles) {
            if (te != null) {
                int tx = te.getXCoord();
                int ty = te.getYCoord();
                int tz = te.getZCoord();
                hollowCoords.add(new ChunkCoordinates(tx, ty - 2, tz));
                hollowCoords.add(new ChunkCoordinates(tx, ty - 3, tz));
                sumX += tx;
                sumY += ty;
                sumZ += tz;
                count++;
            }
        }

        double centerX = (count > 0) ? (sumX / count) : base.getXCoord();
        double centerY = (count > 0) ? ((sumY / count) - 2.5) : (base.getYCoord() + 0.5);
        double centerZ = (count > 0) ? (sumZ / count) : base.getZCoord();

        // 1. Center explosion damaging internal casings/hatches
        world.createExplosion(null, centerX, centerY, centerZ, 4.5F, true);

        // 2. Corium fluid generation in inner hollow space
        if (!hollowCoords.isEmpty()) {
            Random rand = world.rand;
            // 2-block-radius randomly-centred sphere of the inner hollow space
            ChunkCoordinates sphereCenter = hollowCoords.get(rand.nextInt(hollowCoords.size()));
            double radiusSq = 2.0 * 2.0;
            Set<ChunkCoordinates> coriumPlaced = new HashSet<>();

            for (ChunkCoordinates coord : hollowCoords) {
                double dx = coord.posX - sphereCenter.posX;
                double dy = coord.posY - sphereCenter.posY;
                double dz = coord.posZ - sphereCenter.posZ;
                if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                    world.setBlock(coord.posX, coord.posY, coord.posZ, ModBlocks.blockCorium, 0, 3);
                    coriumPlaced.add(coord);
                }
            }

            // Plus 4 more random blocks anywhere in the reactor hollow space
            List<ChunkCoordinates> remaining = new ArrayList<>();
            for (ChunkCoordinates coord : hollowCoords) {
                if (!coriumPlaced.contains(coord)) {
                    remaining.add(coord);
                }
            }
            Collections.shuffle(remaining, rand);
            int extraCount = Math.min(4, remaining.size());
            for (int i = 0; i < extraCount; i++) {
                ChunkCoordinates coord = remaining.get(i);
                world.setBlock(coord.posX, coord.posY, coord.posZ, ModBlocks.blockCorium, 0, 3);
            }
        }

        // 3. Invisible fallout entity for timers, radiation, particles, and corium solidification
        EntityMeltdownFallout fallout = new EntityMeltdownFallout(world, centerX, centerY, centerZ);
        world.spawnEntityInWorld(fallout);

        // 4. Shutdown reactor
        mMachine = false;
        stopMachine();
        base.setActive(false);

        ModularNuclear.LOG.warn(
            "Modular Pressure Tube Reactor melted down at ({}, {}, {})! Produced corium and spawned fallout entity.",
            centerX,
            centerY,
            centerZ);
    }

    @Override
    public void onMachineBlockUpdate() {
        super.onMachineBlockUpdate();
        if (!mMachine && !mNuclearTiles.isEmpty()) {
            mWallNeutronAccumulator = 0;
            mWallMaintenanceTimer = 0;
            mOutputCoolantRate = 0;
            mOutputCoolantName = "";
            for (IGregTechTileEntity te : new ArrayList<>(mNuclearTiles)) {
                if (te != null && te.getMetaTileEntity() instanceof MTEHatchNuclearHatch hatch) {
                    hatch.mWasDry = false;
                }
            }
        }
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        clearHatches();
        mNuclearTiles.clear();
        mGrid = null;
        gridSize = 0;
        coreDimension = 0;
        mPipeTier = -1;
        mHatchTier = -1;
        mHatchTierInconsistent = false;
        mCasing = 0;

        if (checkPiece(STRUCTURE_TIER_1, 3, 3, 0, errors)) {
            gridSize = 5;
            coreDimension = 5;
        } else {
            clearHatches();
            mNuclearTiles.clear();
            mPipeTier = -1;
            mHatchTier = -1;
            mHatchTierInconsistent = false;
            mCasing = 0;
            errors.clear();
            if (checkPiece(STRUCTURE_TIER_2, 5, 3, 0, errors)) {
                gridSize = 9;
                coreDimension = 9;
            } else {
                clearHatches();
                mNuclearTiles.clear();
                mPipeTier = -1;
                mHatchTier = -1;
                mHatchTierInconsistent = false;
                mCasing = 0;
                errors.clear();
                if (checkPiece(STRUCTURE_TIER_3, 7, 3, 0, errors)) {
                    gridSize = 13;
                    coreDimension = 13;
                } else {
                    return;
                }
            }
        }

        checkOneMaintenanceHatch(errors);

        int totalDynamos = mDynamoHatches.size() + mExoticDynamoHatches.size();
        if (totalDynamos > 1) {
            errors.add(StructureErrors.tooManyHatches(ItemList.Hatch_Dynamo_HV.get(1), 1));
        }

        if (mPipeTier < 0) {
            errors.add(StructureErrors.of("GT5U.gui.text.structure_error.invalid_pipe_tier"));
        }

        if (mHatchTierInconsistent) {
            errors.add(StructureErrors.of("GT5U.gui.text.structure_error.inconsistent_nuclear_hatch_tier"));
        }

        checkCasingMin(errors, mCasing, 50);

        checkNuclearHatchFacings(errors);
        validateHighPressureHatchPairing(aBaseMetaTileEntity, errors);

        if (!errors.isEmpty()) {
            return;
        }

        // Build 2D grid from matched tiles
        mGrid = new INuclearTile[gridSize][gridSize];
        int cX = aBaseMetaTileEntity.getXCoord();
        int cY = aBaseMetaTileEntity.getYCoord();
        int cZ = aBaseMetaTileEntity.getZCoord();
        ExtendedFacing ef = getExtendedFacing();
        boolean isUpsideDown = ef.getRotation() == Rotation.UPSIDE_DOWN;
        int hOffset = gridSize / 2;
        int[] in = new int[3];
        int[] out = new int[3];

        for (IGregTechTileEntity te : mNuclearTiles) {
            in[0] = te.getXCoord() - cX;
            in[1] = te.getYCoord() - cY;
            in[2] = te.getZCoord() - cZ;

            ef.getOffsetABC(in, out);

            int localA = isUpsideDown ? -out[0] : out[0];
            int gx = localA + hOffset;
            int gy = out[2] - 1;

            if (gx >= 0 && gx < gridSize && gy >= 0 && gy < gridSize) {
                IMetaTileEntity mte = te.getMetaTileEntity();
                if (mte instanceof MTEHatchNuclearBus bus) {
                    mGrid[gx][gy] = new NuclearGridTile(this, bus, gx, gy);
                } else if (mte instanceof MTEHatchNuclearHatch hatch) {
                    mGrid[gx][gy] = new NuclearGridTile(this, hatch, gx, gy);
                } else if (mte instanceof MTEHatchNuclearHighPressure hp) {
                    mGrid[gx][gy] = new NuclearGridTile(this, hp, gx, gy);
                }
            }
        }

        // Attach bottom-layer control rod hatches to corresponding grid cells
        for (MTEHatchNuclearControlRod rod : mBottomControlRodHatches) {
            IGregTechTileEntity bTe = rod.getBaseMetaTileEntity();
            if (bTe != null) {
                int[] gCoords = calculateGridCoords(bTe, cX, cY, cZ, ef, isUpsideDown, hOffset);
                if (gCoords != null && gCoords[0] >= 0
                    && gCoords[0] < gridSize
                    && gCoords[1] >= 0
                    && gCoords[1] < gridSize) {
                    INuclearTile tile = mGrid[gCoords[0]][gCoords[1]];
                    if (tile instanceof NuclearGridTile gt) {
                        gt.setBottomControlRod(rod);
                        rod.mReactor = this;
                        if (mScram) {
                            rod.setScram(true);
                        }
                    }
                }
            }
        }

        for (IGregTechTileEntity te : mNuclearTiles) {
            if (te != null && te.getMetaTileEntity() instanceof MTEHatchNuclearHatch hatch) {
                hatch.mWasDry = false;
            }
        }

        updateNuclearTilesPipeTier();

        mCachedAmbientTemp = getAmbientTemperature();
        if (mScram) {
            for (MTEHatchNuclearControlRod rod : mBottomControlRodHatches) {
                if (rod != null) {
                    rod.setScram(true);
                }
            }
        }
    }

    public int[] calculateGridCoords(IGregTechTileEntity te, int cX, int cY, int cZ, ExtendedFacing ef,
        boolean isUpsideDown, int hOffset) {
        if (te == null) return null;
        int[] in = new int[] { te.getXCoord() - cX, te.getYCoord() - cY, te.getZCoord() - cZ };
        int[] out = new int[3];
        ef.getOffsetABC(in, out);
        int localA = isUpsideDown ? -out[0] : out[0];
        int gx = localA + hOffset;
        int gy = out[2] - 1;
        return new int[] { gx, gy };
    }

    public void verifyCasingMin(List<StructureError> errors, int current, int required) {
        checkCasingMin(errors, current, required);
    }

    public static boolean isNuclearCoreHatch(IMetaTileEntity mte) {
        return mte instanceof MTEHatchNuclearHatch || mte instanceof MTEHatchNuclearBus
            || mte instanceof MTEHatchNuclearControlRod
            || mte instanceof MTEHatchNuclearHighPressure;
    }

    public static boolean isNuclearHatchTile(TileEntity te) {
        if (te instanceof IGregTechTileEntity gte) {
            return isNuclearCoreHatch(gte.getMetaTileEntity());
        }
        return false;
    }

    public void checkNuclearHatchFacings(List<StructureError> errors) {
        ForgeDirection requiredFacing = getNuclearHatchFacing();
        for (IGregTechTileEntity te : mNuclearTiles) {
            if (te != null && te.getFrontFacing() != requiredFacing) {
                errors.add(StructureErrors.of("GT5U.gui.text.structure_error.wrong_nuclear_hatch_facing"));
                return;
            }
        }
    }

    public void verifyNuclearHatchFacings(List<StructureError> errors) {
        checkNuclearHatchFacings(errors);
    }

    public void validateHighPressureHatchPairing(IGregTechTileEntity aBaseMetaTileEntity, List<StructureError> errors) {
        if (!mTopHighPressureHatches.isEmpty() || !mBottomHighPressureHatches.isEmpty()) {
            if (getReactorTier() < 2) {
                errors.add(StructureErrors.of("GT5U.gui.text.structure_error.hp_hatch_tier"));
            }
            if (mTopHighPressureHatches.size() != mBottomHighPressureHatches.size()) {
                errors.add(StructureErrors.of("GT5U.gui.text.structure_error.hp_hatch_unpaired"));
            } else if (gridSize > 0) {
                int cX = aBaseMetaTileEntity != null ? aBaseMetaTileEntity.getXCoord() : 0;
                int cY = aBaseMetaTileEntity != null ? aBaseMetaTileEntity.getYCoord() : 0;
                int cZ = aBaseMetaTileEntity != null ? aBaseMetaTileEntity.getZCoord() : 0;
                ExtendedFacing ef = getExtendedFacing();
                boolean isUpsideDown = ef.getRotation() == Rotation.UPSIDE_DOWN;
                int hOffset = gridSize / 2;

                MTEHatchNuclearHighPressure[][] bottomGrid = new MTEHatchNuclearHighPressure[gridSize][gridSize];
                for (MTEHatchNuclearHighPressure bottom : mBottomHighPressureHatches) {
                    IGregTechTileEntity bTe = bottom.getBaseMetaTileEntity();
                    if (bTe != null) {
                        int[] gCoords = calculateGridCoords(bTe, cX, cY, cZ, ef, isUpsideDown, hOffset);
                        if (gCoords != null && gCoords[0] >= 0
                            && gCoords[0] < gridSize
                            && gCoords[1] >= 0
                            && gCoords[1] < gridSize) {
                            bottomGrid[gCoords[0]][gCoords[1]] = bottom;
                        }
                    }
                }

                boolean anyUnpaired = false;
                for (MTEHatchNuclearHighPressure top : mTopHighPressureHatches) {
                    IGregTechTileEntity tTe = top.getBaseMetaTileEntity();
                    if (tTe != null) {
                        int[] gCoords = calculateGridCoords(tTe, cX, cY, cZ, ef, isUpsideDown, hOffset);
                        if (gCoords != null && gCoords[0] >= 0
                            && gCoords[0] < gridSize
                            && gCoords[1] >= 0
                            && gCoords[1] < gridSize) {
                            MTEHatchNuclearHighPressure bottom = bottomGrid[gCoords[0]][gCoords[1]];
                            if (bottom != null) {
                                top.setOppositeHatch(bottom.getBaseMetaTileEntity());
                                top.setIsInlet(true);
                                bottom.setOppositeHatch(top.getBaseMetaTileEntity());
                                bottom.setIsInlet(false);
                                bottomGrid[gCoords[0]][gCoords[1]] = null;
                            } else {
                                anyUnpaired = true;
                            }
                        } else {
                            anyUnpaired = true;
                        }
                    } else {
                        anyUnpaired = true;
                    }
                }

                if (anyUnpaired) {
                    errors.add(StructureErrors.of("GT5U.gui.text.structure_error.hp_hatch_unpaired"));
                }
            }
        }
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
    public CheckRecipeResult checkProcessing() {
        if (!mMachine || mGrid == null) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        mMaxProgresstime = 20;
        mProgresstime = 0;
        super.mEfficiency = (int) Math.round(getMaintenanceEfficiency() * 10000);
        mEfficiencyIncrease = 0;
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Override
    public void checkMaintenance() {
        int repairBefore = getRepairStatus();
        super.checkMaintenance();
        int repairAfter = getRepairStatus();
        int repaired = Math.max(0, repairAfter - repairBefore);

        if (repaired > 0 && mReactorDamage > 0.0) {
            double avgTemp = Math.max(mAvgTemp, mAvgTileTemp);
            double repairThreshold = NuclearSimulationEngine.getRepairTemperatureThreshold(mPipeTier);
            if (avgTemp <= repairThreshold) {
                mReactorDamage = Math.max(0.0, mReactorDamage - 2.0 * repaired);
            }
        }

        if (mReactorDamage > 0.0) {
            // The reactor should always report at least one maintenance issue while damage is greater than 0
            if (getRepairStatus() == getIdealStatus()) {
                mWrench = false;
            }
        } else {
            if (mMachine && getRepairStatus() == getIdealStatus()) {
                IGregTechTileEntity base = getBaseMetaTileEntity();
                if (base != null && base.getLastShutDownReason() == ShutDownReasonRegistry.NO_REPAIR) {
                    base.setShutdownStatus(false);
                    base.setShutDownReason(ShutDownReasonRegistry.NONE);
                    base.enableWorking();
                }
            }
        }
    }

    @Override
    protected void onStructureCheckFinished(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onStructureCheckFinished(aBaseMetaTileEntity);
        if (mMachine) {
            mWasMachineFormed = true;
            mLastFormedNuclearTiles.clear();
            mLastFormedNuclearTiles.addAll(mNuclearTiles);
            int tier = (gridSize >= 145) ? 3 : (gridSize >= 69) ? 2 : 1;
            aBaseMetaTileEntity.sendBlockEvent((byte) 100, (byte) tier);
        } else if (mWasMachineFormed) {
            mWasMachineFormed = false;
            handleStructureBreak();
            mLastFormedNuclearTiles.clear();
        }
    }

    @Override
    public void onRemoval() {
        if (mWasMachineFormed || mMachine) {
            mWasMachineFormed = false;
            handleStructureBreak();
            mLastFormedNuclearTiles.clear();
        }
        ModularNuclear.proxy.removeReactor(this);
        super.onRemoval();
    }

    @Override
    public void receiveClientEvent(byte aEventID, byte aValue) {
        super.receiveClientEvent(aEventID, aValue);
        if (aEventID == 100) {
            this.mClientTier = aValue;
            this.mClientRadius = (mClientTier == 3) ? 6.5 : (mClientTier == 2) ? 4.5 : 2.5;
            this.mClientWaterBlocks = -1;
        }
    }

    public boolean isAnyTemperatureAbove100() {
        if (mCoreTemp > 100.0 || mAvgTemp > 100.0) {
            return true;
        }
        for (IGregTechTileEntity te : mLastFormedNuclearTiles) {
            if (te != null && !te.isDead()) {
                IMetaTileEntity mte = te.getMetaTileEntity();
                if (mte instanceof MTEHatchNuclearBus bus) {
                    if (bus.mTemperature > 100.0) return true;
                } else if (mte instanceof MTEHatchNuclearHatch hatch) {
                    if (hatch.mTemperature > 100.0) return true;
                } else if (mte instanceof MTEHatchNuclearControlRod rod) {
                    if (rod.mTemperature > 100.0) return true;
                }
            }
        }
        for (MTEHatchNuclearControlRod rod : mBottomControlRodHatches) {
            if (rod != null && rod.mTemperature > 100.0) return true;
        }
        return false;
    }

    public void handleStructureBreak() {
        if (isAnyTemperatureAbove100()) {
            for (IGregTechTileEntity te : mLastFormedNuclearTiles) {
                if (te != null && !te.isDead()) {
                    IMetaTileEntity mte = te.getMetaTileEntity();
                    if (mte instanceof MTEHatchNuclearBus bus) {
                        for (int i = 0; i < bus.mInventory.length; i++) {
                            bus.mInventory[i] = null;
                        }
                        bus.markDirty();
                        if (bus.getBaseMetaTileEntity() != null) {
                            bus.getBaseMetaTileEntity()
                                .markDirty();
                        }
                    } else if (mte instanceof MTEHatchNuclearHatch hatch) {
                        hatch.mInputFluid = null;
                        hatch.markDirty();
                        if (hatch.getBaseMetaTileEntity() != null) {
                            hatch.getBaseMetaTileEntity()
                                .markDirty();
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);

        if (!aBaseMetaTileEntity.isAllowedToWork()) {
            aBaseMetaTileEntity.enableWorking();
        }

        if (aBaseMetaTileEntity.isServerSide() && mMachine) {
            boolean shouldBeActive = !mScram && (mNeutronsProduced > 0);
            if (aBaseMetaTileEntity.isActive() != shouldBeActive) {
                aBaseMetaTileEntity.setActive(shouldBeActive);
                aBaseMetaTileEntity.setLightValue((byte) (shouldBeActive ? 15 : 0));
            }

            if (mStartUpCheck >= 0) {
                checkMaintenance();
            }

            if (mDirectPowerEUt > 0) {
                addEnergyOutputMultipleDynamos(mDirectPowerEUt, true);
            }

            if (aTick % 100 == 0) {
                mCachedAmbientTemp = getAmbientTemperature();
            }

            if (mGrid != null && (aTick % 20 == 0)) {
                updateNuclearTilesPipeTier();

                double ambient = getAmbientTemperature();

                // Check coolant boiling against ambient or dry hatch injection (thermal shock)
                for (IGregTechTileEntity te : mNuclearTiles) {
                    if (te != null && te.getMetaTileEntity() instanceof MTEHatchNuclearHatch hatch) {
                        if (hatch.mInputFluid != null && hatch.mInputFluid.amount > 0) {
                            String name = hatch.mInputFluid.getFluid()
                                .getName()
                                .toLowerCase();
                            double boilingThreshold = NuclearSimulationEngine.getCoolantBoilingThreshold(name);
                            if (ambient >= boilingThreshold) {
                                triggerThermalShock(
                                    hatch,
                                    "Coolant evaporates immediately at ambient temperature (" + name
                                        + ", ambient="
                                        + ambient
                                        + "C >= boiling threshold="
                                        + boilingThreshold
                                        + "C)");
                                return;
                            }
                            if (hatch.mWasDry) {
                                if (hatch.mTemperature > boilingThreshold) {
                                    triggerThermalShock(
                                        hatch,
                                        "Coolant injected into dry superheated hatch above boiling threshold (" + name
                                            + ", temp="
                                            + hatch.mTemperature
                                            + "C > threshold="
                                            + boilingThreshold
                                            + "C)");
                                    return;
                                }
                                hatch.mWasDry = false;
                            }
                        } else {
                            // Hatch has zero coolant: controller remembers that
                            hatch.mWasDry = true;
                        }
                    }
                }

                mCycleZeroedCoolantItems = 0;
                mCycleZeroedFuelItems = 0;
                mCycleConsumedCoolant = 0;
                mCycleProducedHotCoolant = 0;
                mCycleTransmutationLoss = 0;
                mCycleTransmutationByproducts = 0;
                mCycleDepletedLiquidFuel = 0;

                double maintEff = getMaintenanceEfficiency();
                NuclearSimulationEngine.SimulationResult res = NuclearSimulationEngine
                    .simulate(mGrid, gridSize, gridSize, maintEff, ambient);
                mCoreTemp = res.maxTemperature;
                mAvgTemp = res.averageTemperature;
                mNeutronsProduced = res.totalNeutronsGenerated;
                mFastAbsorbed = res.fastNeutronsAbsorbed;
                mThermalAbsorbed = res.thermalNeutronsAbsorbed;
                mEscapedNeutrons = res.neutronsEscaped;
                mReactivity = res.averageReactivity;

                // Accumulate wall neutron impacts for custom maintenance mechanic
                int wallHits = res.wallNeutronsReflected + res.wallNeutronsAbsorbed;
                mWallNeutronAccumulator += wallHits;
                mWallMaintenanceTimer += 20;

                // Once per minute (1200 ticks = 60s), check maintenance issue probability: min(0.1, N/10000)
                if (mWallMaintenanceTimer >= 1200) {
                    mWallMaintenanceTimer = 0;
                    long N = mWallNeutronAccumulator;
                    mWallNeutronAccumulator = 0;
                    double prob = Math.min(0.1, (double) N / 10000.0);
                    if (prob > 0.0 && (aBaseMetaTileEntity.getRandomNumber(1000000) / 1000000.0) < prob) {
                        causeNewMaintenanceIssue();
                    }
                }

                // Sum direct EU from radiovoltaic cells across the grid
                long directEU = 0;
                for (int x = 0; x < gridSize; x++) {
                    for (int y = 0; y < gridSize; y++) {
                        INuclearTile tile = mGrid[x][y];
                        if (tile instanceof NuclearGridTile gt && gt.isBus()) {
                            directEU += gt.getBus().mDirectEUProduced;
                        }
                    }
                }
                mDirectPowerEUt = (long) Math.round(directEU * maintEff);

                // Sum output coolant production across all coolant hatches
                int totalCoolantProduced = 0;
                String coolantName = "";
                for (IGregTechTileEntity te : mNuclearTiles) {
                    if (te != null && te.getMetaTileEntity() instanceof MTEHatchNuclearHatch hatch) {
                        if (hatch.mLastProducedAmount > 0) {
                            totalCoolantProduced += hatch.mLastProducedAmount;
                            if (coolantName.isEmpty() && hatch.mLastProducedFluidName != null
                                && !hatch.mLastProducedFluidName.isEmpty()) {
                                Fluid f = FluidRegistry.getFluid(hatch.mLastProducedFluidName);
                                if (f != null) {
                                    coolantName = f.getLocalizedName(new FluidStack(f, 1000));
                                } else {
                                    coolantName = hatch.mLastProducedFluidName;
                                }
                            }
                        }
                    }
                }
                mOutputCoolantRate = totalCoolantProduced;
                mOutputCoolantName = coolantName;
                super.mEfficiency = (int) Math.round(maintEff * 10000);

                // 4. Check casing-dependent maximum operating temperature:
                // Overheating hatches increase reactor damage by 1% per overheating hatch.
                // Overheating non-fuel contents in hatches/buses are voided, but fuel rods and fluids are NOT directly
                // voided!
                double maxTemp = NuclearSimulationEngine.getMaxOperatingTemperature(mPipeTier);
                int overheatingHatchesCount = 0;
                for (IGregTechTileEntity te : mNuclearTiles) {
                    if (te != null) {
                        if (te.getMetaTileEntity() instanceof MTEHatchNuclearHatch hatch) {
                            if (hatch.mTemperature > maxTemp) {
                                overheatingHatchesCount++;
                                if (!isFluidFuel(hatch.mInputFluid)) {
                                    hatch.mInputFluid = null;
                                    hatch.markTileDirty();
                                }
                            }
                        } else if (te.getMetaTileEntity() instanceof MTEHatchNuclearBus bus) {
                            if (bus.mTemperature > maxTemp) {
                                overheatingHatchesCount++;
                                if (!isItemFuel(bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT])) {
                                    bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = null;
                                    bus.markTileDirty();
                                }
                            }
                        }
                    }
                }
                for (MTEHatchNuclearControlRod rod : mBottomControlRodHatches) {
                    if (rod != null && rod.mTemperature > maxTemp) {
                        overheatingHatchesCount++;
                        rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD] = null;
                        rod.markTileDirty();
                    }
                }
                if (overheatingHatchesCount > 0) {
                    mReactorDamage = Math.min(100.0, mReactorDamage + overheatingHatchesCount * 1.0);
                }

                // 5. Update last cycle totals, calculate telemetry, coolant tracking, and nuclear control hatches
                mZeroedCoolantItemsLastCycle = mCycleZeroedCoolantItems;
                mZeroedFuelItemsLastCycle = mCycleZeroedFuelItems;
                mConsumedCoolantLastCycle = mCycleConsumedCoolant;
                mProducedHotCoolantLastCycle = mCycleProducedHotCoolant;
                mTransmutationLossLastCycle = mCycleTransmutationLoss;
                mTransmutationByproductsLastCycle = mCycleTransmutationByproducts;
                mDepletedLiquidFuelLastCycle = mCycleDepletedLiquidFuel;

                updateCoolantTracking();
                calculateTelemetry();
                updateControlHatches();

                // 6. Meltdown check: triggers strictly when reactor damage reaches 100%
                if (mReactorDamage >= 100.0) {
                    triggerMeltdown();
                    return;
                }
            }
        } else if (aBaseMetaTileEntity.isServerSide()) {
            if (aBaseMetaTileEntity.isActive()) {
                aBaseMetaTileEntity.setActive(false);
                aBaseMetaTileEntity.setLightValue((byte) 0);
            }
            for (MTEHatchNuclearControl hatch : mControlHatches) {
                if (hatch != null && hatch.isValid()) {
                    hatch.setOutputRedstone((byte) 0);
                }
            }
        } else if (aBaseMetaTileEntity.isClientSide()) {
            ModularNuclear.proxy.updateCherenkov(this, aBaseMetaTileEntity);
        }
    }

    @Override
    public boolean needsClientTick() {
        return true;
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setBoolean("mWorldSaved", true);
        aNBT.setBoolean("mScram", mScram);
        aNBT.setDouble("mCoreTemp", mCoreTemp);
        aNBT.setDouble("mAvgTemp", mAvgTemp);
        aNBT.setDouble("mReactorDamage", mReactorDamage);
        aNBT.setInteger("gridSize", gridSize);
        aNBT.setInteger("coreDimension", coreDimension);
        aNBT.setInteger("mPipeTier", mPipeTier);
        aNBT.setLong("mDirectPowerEUt", mDirectPowerEUt);
        aNBT.setLong("mWallNeutronAccumulator", mWallNeutronAccumulator);
        aNBT.setInteger("mWallMaintenanceTimer", mWallMaintenanceTimer);
        aNBT.setInteger("mOutputCoolantRate", mOutputCoolantRate);
        aNBT.setString("mOutputCoolantName", mOutputCoolantName != null ? mOutputCoolantName : "");

        aNBT.setDouble("mMinTileTemp", mMinTileTemp);
        aNBT.setDouble("mMaxTileTemp", mMaxTileTemp);
        aNBT.setDouble("mAvgTileTemp", mAvgTileTemp);
        aNBT.setDouble("mMinCoolantItemDur", mMinCoolantItemDur);
        aNBT.setDouble("mMaxCoolantItemDur", mMaxCoolantItemDur);
        aNBT.setDouble("mAvgCoolantItemDur", mAvgCoolantItemDur);
        aNBT.setDouble("mMinFuelItemDur", mMinFuelItemDur);
        aNBT.setDouble("mMaxFuelItemDur", mMaxFuelItemDur);
        aNBT.setDouble("mAvgFuelItemDur", mAvgFuelItemDur);
        aNBT.setDouble("mMinCoolantHatchFill", mMinCoolantHatchFill);
        aNBT.setDouble("mMaxCoolantHatchFill", mMaxCoolantHatchFill);
        aNBT.setDouble("mAvgCoolantHatchFill", mAvgCoolantHatchFill);
        aNBT.setDouble("mMinFuelHatchFill", mMinFuelHatchFill);
        aNBT.setDouble("mMaxFuelHatchFill", mMaxFuelHatchFill);
        aNBT.setDouble("mAvgFuelHatchFill", mAvgFuelHatchFill);

        aNBT.setInteger("mTotalFuelItems", mTotalFuelItems);
        aNBT.setInteger("mTotalCoolantItems", mTotalCoolantItems);
        aNBT.setLong("mTotalCoolantFluid", mTotalCoolantFluid);
        aNBT.setLong("mTotalCoolantCapacity", mTotalCoolantCapacity);
        aNBT.setLong("mTotalFuelFluid", mTotalFuelFluid);
        aNBT.setLong("mTotalFuelCapacity", mTotalFuelCapacity);
        aNBT.setInteger("mCoolantHatchCount", mCoolantHatchCount);
        aNBT.setInteger("mFuelHatchCount", mFuelHatchCount);

        aNBT.setInteger("mZeroedCoolantItemsLastCycle", mZeroedCoolantItemsLastCycle);
        aNBT.setInteger("mZeroedFuelItemsLastCycle", mZeroedFuelItemsLastCycle);
        aNBT.setLong("mConsumedCoolantLastCycle", mConsumedCoolantLastCycle);
        aNBT.setLong("mProducedHotCoolantLastCycle", mProducedHotCoolantLastCycle);
        aNBT.setLong("mTransmutationLossLastCycle", mTransmutationLossLastCycle);
        aNBT.setLong("mTransmutationByproductsLastCycle", mTransmutationByproductsLastCycle);
        aNBT.setLong("mDepletedLiquidFuelLastCycle", mDepletedLiquidFuelLastCycle);
    }

    @Override
    public void setItemNBT(NBTTagCompound aNBT) {
        super.setItemNBT(aNBT);
        if (mReactorDamage > 0.0) {
            aNBT.setDouble("mReactorDamage", mReactorDamage);
        }
    }

    @Override
    public void addAdditionalTooltipInformation(ItemStack aItemStack, List<String> aList) {
        super.addAdditionalTooltipInformation(aItemStack, aList);
        if (aItemStack != null && aItemStack.hasTagCompound()
            && aItemStack.getTagCompound()
                .hasKey("mReactorDamage")) {
            double dmg = aItemStack.getTagCompound()
                .getDouble("mReactorDamage");
            if (dmg > 0.0) {
                aList.add(EnumChatFormatting.RED + String.format(Locale.US, "Reactor Damage: %.1f%%", dmg));
            }
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mWorldSaved = aNBT.getBoolean("mWorldSaved");
        if (!mWorldSaved) {
            mWrench = false;
            mScrewdriver = false;
            mSoftMallet = false;
            mHardHammer = false;
            mSolderingTool = false;
            mCrowbar = false;
        }
        mScram = aNBT.getBoolean("mScram");
        if (aNBT.hasKey("mCoreTemp")) {
            mCoreTemp = aNBT.getDouble("mCoreTemp");
            mAvgTemp = aNBT.getDouble("mAvgTemp");
        } else {
            mCoreTemp = getAmbientTemperature();
            mAvgTemp = getAmbientTemperature();
        }
        if (aNBT.hasKey("mReactorDamage")) {
            mReactorDamage = aNBT.getDouble("mReactorDamage");
        } else {
            mReactorDamage = 0.0;
        }
        gridSize = aNBT.getInteger("gridSize");
        coreDimension = aNBT.getInteger("coreDimension");
        mPipeTier = aNBT.getInteger("mPipeTier");
        mDirectPowerEUt = aNBT.getLong("mDirectPowerEUt");
        mWallNeutronAccumulator = aNBT.getLong("mWallNeutronAccumulator");
        mWallMaintenanceTimer = aNBT.getInteger("mWallMaintenanceTimer");
        mOutputCoolantRate = aNBT.getInteger("mOutputCoolantRate");
        mOutputCoolantName = aNBT.getString("mOutputCoolantName");

        mMinTileTemp = aNBT.getDouble("mMinTileTemp");
        mMaxTileTemp = aNBT.getDouble("mMaxTileTemp");
        mAvgTileTemp = aNBT.getDouble("mAvgTileTemp");
        mMinCoolantItemDur = aNBT.getDouble("mMinCoolantItemDur");
        mMaxCoolantItemDur = aNBT.getDouble("mMaxCoolantItemDur");
        mAvgCoolantItemDur = aNBT.getDouble("mAvgCoolantItemDur");
        mMinFuelItemDur = aNBT.getDouble("mMinFuelItemDur");
        mMaxFuelItemDur = aNBT.getDouble("mMaxFuelItemDur");
        mAvgFuelItemDur = aNBT.getDouble("mAvgFuelItemDur");
        mMinCoolantHatchFill = aNBT.getDouble("mMinCoolantHatchFill");
        mMaxCoolantHatchFill = aNBT.getDouble("mMaxCoolantHatchFill");
        mAvgCoolantHatchFill = aNBT.getDouble("mAvgCoolantHatchFill");
        mMinFuelHatchFill = aNBT.getDouble("mMinFuelHatchFill");
        mMaxFuelHatchFill = aNBT.getDouble("mMaxFuelHatchFill");
        mAvgFuelHatchFill = aNBT.getDouble("mAvgFuelHatchFill");

        mTotalFuelItems = aNBT.getInteger("mTotalFuelItems");
        mTotalCoolantItems = aNBT.getInteger("mTotalCoolantItems");
        mTotalCoolantFluid = aNBT.getLong("mTotalCoolantFluid");
        mTotalCoolantCapacity = aNBT.getLong("mTotalCoolantCapacity");
        mTotalFuelFluid = aNBT.getLong("mTotalFuelFluid");
        mTotalFuelCapacity = aNBT.getLong("mTotalFuelCapacity");
        mCoolantHatchCount = aNBT.getInteger("mCoolantHatchCount");
        mFuelHatchCount = aNBT.getInteger("mFuelHatchCount");

        mZeroedCoolantItemsLastCycle = aNBT.getInteger("mZeroedCoolantItemsLastCycle");
        mZeroedFuelItemsLastCycle = aNBT.getInteger("mZeroedFuelItemsLastCycle");
        mConsumedCoolantLastCycle = aNBT.getLong("mConsumedCoolantLastCycle");
        mProducedHotCoolantLastCycle = aNBT.getLong("mProducedHotCoolantLastCycle");
        mTransmutationLossLastCycle = aNBT.getLong("mTransmutationLossLastCycle");
        mTransmutationByproductsLastCycle = aNBT.getLong("mTransmutationByproductsLastCycle");
        mDepletedLiquidFuelLastCycle = aNBT.getLong("mDepletedLiquidFuelLastCycle");
    }

    public ReactorGridSyncData getClientGridData() {
        if (mClientGridData != null) return mClientGridData;
        if (mGrid != null && gridSize > 0) return collectGridSyncData();
        return null;
    }

    public void applyGridSyncData(ReactorGridSyncData data) {
        this.mClientGridData = data;
        if (data != null) {
            if (data.gridSize > 0) this.mMachine = true;
            this.gridSize = data.gridSize;
            this.coreDimension = data.coreDimension;
            if (data.gridSize >= 145) {
                this.mClientTier = 3;
            } else if (data.gridSize >= 69) {
                this.mClientTier = 2;
            } else if (data.gridSize > 0) {
                this.mClientTier = 1;
            }
            this.mClientRadius = (mClientTier == 3) ? 6.5 : (mClientTier == 2) ? 4.5 : 2.5;
            this.mClientWaterBlocks = -1;
            this.mCoreTemp = data.coreTemp;
            this.mAvgTemp = data.avgTemp;
            this.mReactivity = data.efficiency;
            this.mPipeTier = data.pipeTier;
            this.mDirectPowerEUt = data.directPowerEUt;
            this.mNeutronsProduced = data.neutronsProduced;
            this.mFastAbsorbed = data.fastAbsorbed;
            this.mThermalAbsorbed = data.thermalAbsorbed;
            this.mEscapedNeutrons = data.escapedNeutrons;
            this.mOutputCoolantRate = data.outputCoolantRate;
            this.mOutputCoolantName = data.outputCoolantName;
            this.mScram = data.scram;
            this.mCachedAmbientTemp = data.ambientTemp;
            this.mReactorDamage = data.reactorDamage;
        }
    }

    public ReactorGridSyncData collectGridSyncData() {
        ReactorGridSyncData data = new ReactorGridSyncData();
        data.gridSize = this.gridSize;
        data.coreDimension = this.coreDimension;
        data.coreTemp = (float) this.mCoreTemp;
        data.avgTemp = (float) this.mAvgTemp;
        data.efficiency = this.mReactivity;
        data.pipeTier = this.mPipeTier;
        data.directPowerEUt = this.mDirectPowerEUt;
        data.neutronsProduced = this.mNeutronsProduced;
        data.fastAbsorbed = this.mFastAbsorbed;
        data.thermalAbsorbed = this.mThermalAbsorbed;
        data.escapedNeutrons = this.mEscapedNeutrons;
        data.outputCoolantRate = this.mOutputCoolantRate;
        data.outputCoolantName = this.mOutputCoolantName;
        data.scram = this.mScram;
        data.ambientTemp = (float) getAmbientTemperature();
        data.reactorDamage = (float) this.mReactorDamage;

        if (mGrid != null && gridSize > 0) {
            for (int x = 0; x < gridSize; x++) {
                for (int y = 0; y < gridSize; y++) {
                    INuclearTile tile = mGrid[x][y];
                    ReactorGridSyncData.ReactorGridCellData cell = new ReactorGridSyncData.ReactorGridCellData();
                    if (tile instanceof NuclearGridTile gt) {
                        cell.exists = true;
                        cell.temperature = (float) gt.getTemperature();
                        if (gt.isBus()) {
                            MTEHatchNuclearBus bus = gt.getBus();
                            cell.isFluid = false;
                            ItemStack in = bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT];
                            cell.itemStack = (in != null) ? in.copy() : null;
                            cell.fastFlux = bus.mLastFastFlux;
                            cell.thermalFlux = bus.mLastThermalFlux;
                            cell.fastAbsorbed = bus.mLastFastAbsorbed;
                            cell.thermalAbsorbed = bus.mLastThermalAbsorbed;
                            cell.directEU = bus.mDirectEUProduced;
                            cell.heatOutput = (float) bus.mLastHeatOutput;
                        } else if (gt.isHatch()) {
                            MTEHatchNuclearHatch hatch = gt.getHatch();
                            cell.isFluid = true;
                            cell.fluidStack = (hatch.mInputFluid != null) ? hatch.mInputFluid.copy() : null;
                            cell.fastFlux = hatch.mLastFastFlux;
                            cell.thermalFlux = hatch.mLastThermalFlux;
                            cell.fastAbsorbed = hatch.mLastFastAbsorbed;
                            cell.thermalAbsorbed = hatch.mLastThermalAbsorbed;
                            cell.heatOutput = (float) hatch.mLastHeatOutput;
                        } else if (gt.isHighPressureHatch()) {
                            MTEHatchNuclearHighPressure hp = gt.getHighPressureHatch();
                            cell.isFluid = false;
                            cell.isHighPressure = true;
                            cell.fastFlux = hp.mLastFastFlux;
                            cell.thermalFlux = hp.mLastThermalFlux;
                            cell.fastAbsorbed = hp.mLastFastAbsorbed;
                            cell.thermalAbsorbed = hp.mLastThermalAbsorbed;
                            cell.heatOutput = (float) hp.mLastHeatOutput;
                        }

                        if (gt.hasControlRod()) {
                            MTEHatchNuclearControlRod rod = gt.getBottomControlRod();
                            cell.hasControlRod = true;
                            cell.controlRodInsertion = rod.getInsertionPercent();
                            cell.controlRodType = MTEHatchNuclearControlRod
                                .getRodType(rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD])
                                .ordinal();
                        }
                    }
                    data.cells.add(cell);
                }
            }
        }
        return data;
    }

    public List<String> getModeButtonTooltip() {
        List<String> list = new ArrayList<>();
        switch (mCurrentGuiMode) {
            case GUI_MODE_COMPONENTS:
                list.add(EnumChatFormatting.WHITE + "Mode: " + EnumChatFormatting.GREEN + "Component view");
                list.add(
                    EnumChatFormatting.GRAY + "Click: switch to " + EnumChatFormatting.GOLD + "temperature overlay");
                list.add(EnumChatFormatting.DARK_GRAY + "Shift-click: cycle through all overlay modes");
                break;
            case GUI_MODE_TEMPERATURE:
                list.add(EnumChatFormatting.WHITE + "Mode: " + EnumChatFormatting.GOLD + "Temperature overlay");
                list.add(EnumChatFormatting.GRAY + "Click: switch to " + EnumChatFormatting.GREEN + "component view");
                list.add(EnumChatFormatting.DARK_GRAY + "Shift-click: cycle through all overlay modes");
                break;
            case GUI_MODE_HEAT_OUTPUT:
                list.add(EnumChatFormatting.WHITE + "Mode: " + EnumChatFormatting.GOLD + "Heat output overlay");
                list.add(EnumChatFormatting.GRAY + "Click: switch to " + EnumChatFormatting.GREEN + "component view");
                list.add(EnumChatFormatting.DARK_GRAY + "Shift-click: cycle through all overlay modes");
                break;
            case GUI_MODE_NEUTRON_FLUX:
                list.add(EnumChatFormatting.WHITE + "Mode: " + EnumChatFormatting.AQUA + "Neutron flux heatmap");
                list.add(EnumChatFormatting.GRAY + "Click: switch to " + EnumChatFormatting.GREEN + "component view");
                list.add(EnumChatFormatting.DARK_GRAY + "Shift-click: cycle through all overlay modes");
                break;
            case GUI_MODE_NEUTRON_ABSORPTION:
                list.add(
                    EnumChatFormatting.WHITE + "Mode: "
                        + EnumChatFormatting.LIGHT_PURPLE
                        + "Neutron absorption heatmap");
                list.add(EnumChatFormatting.GRAY + "Click: switch to " + EnumChatFormatting.GREEN + "component view");
                list.add(EnumChatFormatting.DARK_GRAY + "Shift-click: cycle through all overlay modes");
                break;
        }
        return list;
    }

    @Override
    protected boolean useMui2() {
        return false;
    }

    @Override
    public boolean supportsPowerPanel() {
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
    public boolean supportsSingleRecipeLocking() {
        return false;
    }

    public void setScram(boolean scram) {
        this.mScram = scram;
        for (MTEHatchNuclearControlRod rod : mBottomControlRodHatches) {
            if (rod != null) {
                rod.setScram(scram);
            }
        }
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null && base.getWorld() != null) {
            World world = base.getWorld();
            int cX = base.getXCoord();
            int cY = base.getYCoord();
            int cZ = base.getZCoord();
            GTUtility.sendSoundToPlayers(
                world,
                scram ? SoundResource.IC2_MACHINES_MACHINE_OVERLOAD : SoundResource.IC2_MACHINES_INTERRUPT_ONE,
                0.6F,
                1.0F,
                cX + 0.5,
                cY + 0.5,
                cZ + 0.5);
            base.markDirty();
        }
    }

    public static final int REACTOR_GRID_WINDOW_ID = NuclearReactorGui.REACTOR_GRID_WINDOW_ID;

    public ButtonWidget createScramButton(IWidgetBuilder<?> builder) {
        return NuclearReactorGui.createScramButton(this, builder);
    }

    public ButtonWidget createReactorGridButton(IWidgetBuilder<?> builder) {
        return NuclearReactorGui.createReactorGridButton(this);
    }

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext buildContext) {
        NuclearReactorGui.buildMainUI(this, builder, buildContext);
    }

    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        NuclearReactorGui.drawTexts(this, screenElements, inventorySlot);
    }

    public ModularWindow createReactorGridWindow(final EntityPlayer player) {
        return NuclearReactorGui.createReactorGridWindow(this, player);
    }

    private final ReactorDummy mReactorDummy = new ReactorDummy(this);

    public ReactorDummy getReactorDummy() {
        return mReactorDummy;
    }

    public static double calculateAmbientTemperature(World world, int x, int y, int z) {
        if (world != null) {
            try {
                float bTemp = world.getBiomeGenForCoords(x, z)
                    .getFloatTemperature(x, y, z);
                return (bTemp * 100.0 - 32.0) / 1.8;
            } catch (Exception ignored) {}
        }
        return NuclearSimulationEngine.DEFAULT_AMBIENT_TEMP;
    }

    public double getAmbientTemperature() {
        if (getBaseMetaTileEntity() != null && getBaseMetaTileEntity().getWorld() != null) {
            mCachedAmbientTemp = calculateAmbientTemperature(
                getBaseMetaTileEntity().getWorld(),
                getBaseMetaTileEntity().getXCoord(),
                getBaseMetaTileEntity().getYCoord(),
                getBaseMetaTileEntity().getZCoord());
        }
        return mCachedAmbientTemp;
    }

    public int getRandomNumber(int max) {
        if (getBaseMetaTileEntity() != null) {
            return getBaseMetaTileEntity().getRandomNumber(max);
        }
        return (int) (Math.random() * max);
    }

    public boolean isItemFuel(ItemStack stack) {
        return NuclearFuelClassification.isItemFuel(stack);
    }

    public static boolean isFluidFuel(FluidStack fluid) {
        return NuclearFuelClassification.isFluidFuel(fluid);
    }

    public static Fluid getSpentFluid(FluidStack fuel) {
        return NuclearFuelClassification.getSpentFluid(fuel);
    }

    public static boolean isCoolantFluid(FluidStack fluid) {
        return NuclearFuelClassification.isCoolantFluid(fluid);
    }

    public static boolean isItemCoolant(ItemStack stack) {
        return NuclearFuelClassification.isItemCoolant(stack);
    }

    public static boolean isItemHeatVent(ItemStack stack) {
        return NuclearFuelClassification.isItemHeatVent(stack);
    }

    public static boolean isItemHeatExchanger(ItemStack stack) {
        return NuclearFuelClassification.isItemHeatExchanger(stack);
    }

    public static double getInsulationDampening(ItemStack stack) {
        return NuclearFuelClassification.getInsulationDampening(stack);
    }

    public static boolean isItemInsulator(ItemStack stack) {
        return getInsulationDampening(stack) > 0.0;
    }

    public static boolean isNaquariteInsulatorFoil(ItemStack stack) {
        return getInsulationDampening(stack) >= 1.0;
    }

    public double getTileInsulationDampening(NuclearGridTile tile) {
        if (tile.isBus()) {
            ItemStack stack = tile.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            return getInsulationDampening(stack);
        }
        return 0.0;
    }

    public void updateCoolantTracking() {
        if (mGrid == null) return;
        for (int x = 0; x < gridSize; x++) {
            for (int y = 0; y < gridSize; y++) {
                INuclearTile tile = mGrid[x][y];
                if (tile instanceof NuclearGridTile gt) {
                    if (gt.isHatch()) {
                        MTEHatchNuclearHatch hatch = gt.getHatch();
                        FluidStack fluid = hatch.mInputFluid;
                        if (fluid != null && fluid.amount > 0) {
                            if (isCoolantFluid(fluid) && !isFluidFuel(fluid)) {
                                hatch.mUsedForCooling = true;
                            } else {
                                hatch.mUsedForCooling = false;
                            }
                        } else {
                            hatch.mUsedForCooling = false;
                        }
                    } else if (gt.isBus()) {
                        MTEHatchNuclearBus bus = gt.getBus();
                        ItemStack stack = bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT];
                        if (stack != null) {
                            if (isItemCoolant(stack)) {
                                bus.mUsedForCooling = true;
                            } else {
                                bus.mUsedForCooling = false;
                            }
                        }
                    }
                }
            }
        }
    }

    public void updateControlHatches() {
        for (MTEHatchNuclearControl hatch : mControlHatches) {
            if (hatch != null && hatch.isValid()) {
                int mode = hatch.getMode();
                byte signal = calculateSignalForMode(mode);
                int fineSignal = calculateFineSignalForMode(mode);

                byte[] allSignals = new byte[MTEHatchNuclearControl.MODE_COUNT];
                for (int m = 0; m < MTEHatchNuclearControl.MODE_COUNT; m++) {
                    allSignals[m] = (byte) (calculateFineSignalForMode(m) & 0xFF);
                }

                hatch.setOutputs(signal, fineSignal, allSignals);
            }
        }
    }

    public byte calculateSignal(int metric, int statistic) {
        return calculateSignalForMode(metric * MTEHatchNuclearControl.STAT_COUNT + statistic);
    }

    public int calculateFineSignal(int metric, int statistic) {
        return calculateFineSignalForMode(metric * MTEHatchNuclearControl.STAT_COUNT + statistic);
    }

    public double calculateFractionForMode(int mode) {
        if (mGrid == null || gridSize <= 0) return 0.0;
        calculateTelemetry();

        double maxOperatingTemp = NuclearSimulationEngine.getMaxOperatingTemperature(mPipeTier);
        if (maxOperatingTemp <= 0) maxOperatingTemp = 1000.0;

        double fraction = switch (mode) {
            case MTEHatchNuclearControl.MODE_TEMP_MIN -> mMinTileTemp / maxOperatingTemp;
            case MTEHatchNuclearControl.MODE_TEMP_MAX -> mMaxTileTemp / maxOperatingTemp;
            case MTEHatchNuclearControl.MODE_TEMP_AVG -> mAvgTileTemp / maxOperatingTemp;

            case MTEHatchNuclearControl.MODE_COOLANT_ITEM_DUR_MIN -> mMinCoolantItemDur / 100.0;
            case MTEHatchNuclearControl.MODE_COOLANT_ITEM_DUR_MAX -> mMaxCoolantItemDur / 100.0;
            case MTEHatchNuclearControl.MODE_COOLANT_ITEM_DUR_AVG -> mAvgCoolantItemDur / 100.0;

            case MTEHatchNuclearControl.MODE_FUEL_ITEM_DUR_MIN -> mMinFuelItemDur / 100.0;
            case MTEHatchNuclearControl.MODE_FUEL_ITEM_DUR_MAX -> mMaxFuelItemDur / 100.0;
            case MTEHatchNuclearControl.MODE_FUEL_ITEM_DUR_AVG -> mAvgFuelItemDur / 100.0;

            case MTEHatchNuclearControl.MODE_COOLANT_HATCH_FILL_MIN -> mMinCoolantHatchFill / 100.0;
            case MTEHatchNuclearControl.MODE_COOLANT_HATCH_FILL_MAX -> mMaxCoolantHatchFill / 100.0;
            case MTEHatchNuclearControl.MODE_COOLANT_HATCH_FILL_AVG -> mAvgCoolantHatchFill / 100.0;

            case MTEHatchNuclearControl.MODE_FUEL_HATCH_FILL_MIN -> mMinFuelHatchFill / 100.0;
            case MTEHatchNuclearControl.MODE_FUEL_HATCH_FILL_MAX -> mMaxFuelHatchFill / 100.0;
            case MTEHatchNuclearControl.MODE_FUEL_HATCH_FILL_AVG -> mAvgFuelHatchFill / 100.0;

            case MTEHatchNuclearControl.MODE_DAMAGE_MIN, MTEHatchNuclearControl.MODE_DAMAGE_MAX, MTEHatchNuclearControl.MODE_DAMAGE_AVG -> mReactorDamage
                / 100.0;

            default -> 0.0;
        };

        return Math.max(0.0, Math.min(1.0, fraction));
    }

    public byte calculateSignalForMode(int mode) {
        return (byte) Math.round(calculateFractionForMode(mode) * 15.0);
    }

    public int calculateFineSignalForMode(int mode) {
        return (int) Math.round(calculateFractionForMode(mode) * 255.0);
    }

    public void calculateTelemetry() {
        NuclearReactorTelemetry.update(this);
    }

    public boolean handleSensorCardLinking(ItemStack stack, EntityPlayer player, IGregTechTileEntity target) {
        if (stack == null || player == null || target == null) return false;
        if (!Loader.isModLoaded("IC2NuclearControl")) return false;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        int tx = base != null ? base.getXCoord() : target.getXCoord();
        int ty = base != null ? base.getYCoord() : target.getYCoord();
        int tz = base != null ? base.getZCoord() : target.getZCoord();

        if (stack.getItem() instanceof ItemKitModularNuclear) {
            ItemStack card = new ItemStack(NuclearControlIntegration.cardModularNuclear);
            CardWrapperImpl helper = new CardWrapperImpl(card, -1);
            helper.setTarget(tx, ty, tz);
            stack.stackSize--;
            if (stack.stackSize <= 0) {
                player.inventory.mainInventory[player.inventory.currentItem] = card;
            } else if (!player.inventory.addItemStackToInventory(card)) {
                player.dropPlayerItemWithRandomChoice(card, false);
            }
            if (!target.getWorld().isRemote) {
                GTUtility.sendChatToPlayer(
                    player,
                    "Sensor card linked to MPTR Reactor at [" + tx + ", " + ty + ", " + tz + "]");
            }
            return true;
        } else if (stack.getItem() instanceof ItemCardModularNuclear) {
            CardWrapperImpl helper = new CardWrapperImpl(stack, -1);
            helper.setTarget(tx, ty, tz);
            if (!target.getWorld().isRemote) {
                GTUtility.sendChatToPlayer(
                    player,
                    "Sensor card target updated to MPTR Reactor at [" + tx + ", " + ty + ", " + tz + "]");
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        if (aPlayer != null && aPlayer.getHeldItem() != null) {
            if (handleSensorCardLinking(aPlayer.getHeldItem(), aPlayer, aBaseMetaTileEntity)) {
                return true;
            }
        }
        return super.onRightclick(aBaseMetaTileEntity, aPlayer);
    }

    public boolean isItemRadiovoltaic(ItemStack stack) {
        if (stack == null) return false;
        if (stack.getItem() instanceof com.gtnewhorizons.modularnuclear.common.item.ItemRadiovoltaicPlate) return true;
        String name = stack.getUnlocalizedName()
            .toLowerCase();
        return name.contains("radiovoltaic") || name.contains("radiocell") || name.contains("neutronovoltaic");
    }

    public int getRadiovoltaicTier(ItemStack stack) {
        if (stack == null) return 0;
        if (stack.getItem() instanceof com.gtnewhorizons.modularnuclear.common.item.ItemRadiovoltaicPlate plate)
            return plate.getTier();
        String name = stack.getUnlocalizedName()
            .toLowerCase();
        if (name.contains("ev") || name.contains("extreme") || name.contains("tier2") || name.contains("t2")) return 2;
        return 1;
    }

    public ItemStack getItemDepletedForm(ItemStack fuel) {
        return NuclearFuelClassification.getItemDepletedForm(fuel);
    }

    public void damageItemComponent(MTEHatchNuclearBus bus, int damage) {
        ItemStack stack = bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT];
        if (stack == null || !stack.isItemStackDamageable()) return;

        int newDamage = stack.getItemDamage() + damage;
        if (newDamage >= stack.getMaxDamage()) {
            if (isItemFuel(stack)) {
                mCycleZeroedFuelItems++;
            } else if (isItemCoolant(stack) || isItemHeatVent(stack) || isItemHeatExchanger(stack)) {
                mCycleZeroedCoolantItems++;
            }
            ItemStack depleted = getItemDepletedForm(stack);
            bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = null;
            if (depleted != null) {
                this.addOutputPartial(depleted);
            }
        } else {
            stack.setItemDamage(newDamage);
        }
        bus.markTileDirty();
    }

    @Override
    public void addOutputPartial(ItemStack aStack) {
        super.addOutputPartial(aStack);
    }

    @Override
    public void addOutputPartial(FluidStack aStack) {
        super.addOutputPartial(aStack);
    }

    public double getTileTemperature(NuclearGridTile tile) {
        return NuclearReactorPhysics.getTileTemperature(this, tile);
    }

    public void setTileTemperature(NuclearGridTile tile, double temp) {
        NuclearReactorPhysics.setTileTemperature(this, tile, temp);
    }

    public void addTileHeat(NuclearGridTile tile, double heatEU) {
        NuclearReactorPhysics.addTileHeat(tile, heatEU);
    }

    public double getTileHeatTransferCoeff(NuclearGridTile tile) {
        return NuclearReactorPhysics.getTileHeatTransferCoeff(this, tile);
    }

    public boolean isTileFuel(NuclearGridTile tile) {
        return NuclearReactorPhysics.isTileFuel(this, tile);
    }

    public NuclearFuelType getTileFuelType(NuclearGridTile tile) {
        return NuclearReactorPhysics.getTileFuelType(tile);
    }

    public int generateTileNeutrons(NuclearGridTile tile, double efficiency) {
        return NuclearReactorPhysics.generateTileNeutrons(this, tile, efficiency);
    }

    public int getTileNeutronEmissionCount(NuclearGridTile tile) {
        return NuclearReactorPhysics.getTileNeutronEmissionCount(tile);
    }

    public double getBaseAbsorptionProbability(NuclearGridTile tile, NeutronType type) {
        return NuclearReactorPhysics.getBaseAbsorptionProbability(this, tile, type);
    }

    public double getTileAbsorptionProbability(NuclearGridTile tile, NeutronType type) {
        return NuclearReactorPhysics.getTileAbsorptionProbability(this, tile, type);
    }

    public double getBaseScatteringProbability(NuclearGridTile tile, NeutronType type) {
        return NuclearReactorPhysics.getBaseScatteringProbability(this, tile, type);
    }

    public double getTileScatteringProbability(NuclearGridTile tile, NeutronType type) {
        return NuclearReactorPhysics.getTileScatteringProbability(this, tile, type);
    }

    public double getTileModerationProbability(NuclearGridTile tile) {
        return NuclearReactorPhysics.getTileModerationProbability(tile);
    }

    public void onTileNeutronAbsorbed(NuclearGridTile tile, NeutronType type, int count) {
        NuclearReactorPhysics.onTileNeutronAbsorbed(this, tile, type, count);
    }

    public void onBaseTileNeutronAbsorbed(NuclearGridTile tile, NeutronType type, int count) {
        NuclearReactorPhysics.onBaseTileNeutronAbsorbed(this, tile, type, count);
    }

    public void onTileNeutronScattered(NuclearGridTile tile, NeutronType type, int count) {
        NuclearReactorPhysics.onTileNeutronScattered(this, tile, type, count);
    }

    public void addTileNeutronFlux(NuclearGridTile tile, NeutronType type, int count) {
        NuclearReactorPhysics.addTileNeutronFlux(tile, type, count);
    }

    public void processTileNuclearTick(NuclearGridTile tile, double efficiency) {
        NuclearReactorPhysics.processTileNuclearTick(this, tile, efficiency);
    }

    public void processControlRodNuclearTick(MTEHatchNuclearControlRod rod, NuclearGridTile tile, double efficiency) {
        NuclearReactorPhysics.processControlRodNuclearTick(rod, tile, efficiency);
    }

    public void processBusNuclearTick(MTEHatchNuclearBus bus, NuclearGridTile tile, double efficiency) {
        NuclearReactorPhysics.processBusNuclearTick(this, bus, tile, efficiency);
    }

    public boolean processCheeseExtraction(MTEHatchNuclearBus bus) {
        return NuclearReactorPhysics.processCheeseExtraction(this, bus);
    }

    public void processHatchNuclearTick(MTEHatchNuclearHatch hatch, NuclearGridTile tile, double efficiency) {
        NuclearReactorPhysics.processHatchNuclearTick(this, hatch, tile, efficiency);
    }

    @Override
    public void getExtraWailaNBT(EntityPlayerMP playerMP, TileEntity tileEntity, NBTTagCompound tag, World world, int x,
        int y, int z) {
        NuclearReactorWailaProvider.getExtraWailaNBT(this, playerMP, tileEntity, tag, world, x, y, z);
    }

    @Override
    public void getExtraWailaBody(ItemStack itemStack, List<String> list, NBTTagCompound tag,
        IWailaDataAccessor accessor, IWailaConfigHandler config) {
        NuclearReactorWailaProvider.getExtraWailaBody(itemStack, list, tag, accessor, config);
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currentTip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        NuclearReactorWailaProvider.getWailaBody(itemStack, currentTip, accessor, config);
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        try {
            super.getWailaNBTData(player, tile, tag, world, x, y, z);
        } catch (Throwable ignored) {}
        tag.setInteger("progress", 0);
        tag.setInteger("maxProgress", 0);
    }

    @Override
    public void getExtraInfoData(List<String> info) {
        NuclearReactorWailaProvider.getExtraInfoData(this, info);
    }
}
