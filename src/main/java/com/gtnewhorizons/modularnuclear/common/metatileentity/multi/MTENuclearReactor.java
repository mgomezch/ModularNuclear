package com.gtnewhorizons.modularnuclear.common.metatileentity.multi;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.Maintenance;
import static gregtech.api.metatileentity.BaseTileEntity.TOOLTIP_DELAY;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTStructureUtility.chainItemPipeCasings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.alignment.enumerable.Rotation;
import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.IStructureElement.BlocksToPlace;
import com.gtnewhorizon.structurelib.structure.IStructureElement.PlaceResult;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizon.structurelib.util.ItemStackPredicate;
import com.gtnewhorizons.modularnuclear.ModularNuclear;
import com.gtnewhorizons.modularnuclear.common.block.BlockNuclearCasing;
import com.gtnewhorizons.modularnuclear.common.block.ModBlocks;
import com.gtnewhorizons.modularnuclear.common.entity.EntityMeltdownFallout;
import com.gtnewhorizons.modularnuclear.common.gui.ClientScreenHelper;
import com.gtnewhorizons.modularnuclear.common.gui.NuclearReactorGridWidget;
import com.gtnewhorizons.modularnuclear.common.gui.WindowResizeWidget;
import com.gtnewhorizons.modularnuclear.common.gui.ZoomSliderWidget;
import com.gtnewhorizons.modularnuclear.common.metatileentity.ModMetaTileEntities;
import com.gtnewhorizons.modularnuclear.common.metatileentity.NuclearStructureChannels;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearBus;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControl;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHatch;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHighPressure;
import com.gtnewhorizons.modularnuclear.common.nuclear.INuclearTile;
import com.gtnewhorizons.modularnuclear.common.nuclear.NeutronType;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearFuelType;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.ReactorGridSyncData;
import com.gtnewhorizons.modularnuclear.common.nuclearcontrol.ItemCardModularNuclear;
import com.gtnewhorizons.modularnuclear.common.nuclearcontrol.ItemKitModularNuclear;
import com.gtnewhorizons.modularnuclear.common.nuclearcontrol.NuclearControlIntegration;
import com.gtnewhorizons.modularnuclear.common.textures.ModularNuclearTextures;
import com.gtnewhorizons.modularui.api.GlStateManager;
import com.gtnewhorizons.modularui.api.drawable.IDrawable;
import com.gtnewhorizons.modularui.api.drawable.ItemDrawable;
import com.gtnewhorizons.modularui.api.math.Alignment;
import com.gtnewhorizons.modularui.api.math.Color;
import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.gtnewhorizons.modularui.api.math.Size;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.api.widget.IWidgetBuilder;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.Scrollable;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;

import codechicken.lib.gui.GuiDraw;
import cpw.mods.fml.common.Loader;
import goodgenerator.items.GGMaterial;
import gregtech.GTMod;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.SoundResource;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.ICasingTextureProvider;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.interfaces.tileentity.ITurnable;
import gregtech.api.items.ItemCoolantCell;
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
import gregtech.common.blocks.ItemMachines;
import gregtech.common.misc.GTStructureChannels;
import gregtech.common.pollution.Pollution;
import ic2.api.reactor.IReactor;
import ic2.api.reactor.IReactorComponent;
import ic2.core.Ic2Items;
import ic2.core.item.reactor.ItemReactorHeatSwitch;
import ic2.core.item.reactor.ItemReactorMOX;
import ic2.core.item.reactor.ItemReactorUranium;
import ic2.core.item.reactor.ItemReactorVent;
import ic2.core.item.reactor.ItemReactorVentSpread;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.overlay.tooltiprenderers.TTRenderBar;
import shedar.mods.ic2.nuclearcontrol.panel.CardWrapperImpl;

public class MTENuclearReactor extends MTEEnhancedMultiBlockBase<MTENuclearReactor>
    implements ISurvivalConstructable, ICasingTextureProvider {

    public static final int CASING_INDEX = BlockNuclearCasing.CASING_TEXTURE_INDEX;
    public static final String STRUCTURE_TIER_1 = "tier_1";
    public static final String STRUCTURE_TIER_2 = "tier_2";
    public static final String STRUCTURE_TIER_3 = "tier_3";
    @Deprecated
    protected static final String STRUCTURE_3X3 = STRUCTURE_TIER_1;
    @Deprecated
    protected static final String STRUCTURE_5X5 = STRUCTURE_TIER_2;
    @Deprecated
    protected static final String STRUCTURE_7X7 = STRUCTURE_TIER_3;

    public static final String[][] SHAPE_TIER_1 = new String[][] {
        // Slice 0 (Top - Nuclear Hatches & Casings)
        { " ccccc ", "ccgggcc", "cgggggc", "cgggggc", "cgggggc", "ccgggcc", " ccccc " },
        // Slice 1 (Second from top - Item Pipe Casings & Casings)
        { " ccccc ", "ccpppcc", "cpppppc", "cpppppc", "cpppppc", "ccpppcc", " ccccc " },
        // Slice 2 (Third from top - Nuclear Casing Walls, Hollow Interior)
        { " ccccc ", "cc---cc", "c-----c", "c-----c", "c-----c", "cc---cc", " ccccc " },
        // Slice 3 (Fourth from top / Controller layer - Front Center)
        { " cc~cc ", "cc---cc", "c-----c", "c-----c", "c-----c", "cc---cc", " ccccc " },
        // Slice 4 (Bottom - Nuclear Core Bottom Casings / HP Hatches & Perimeter Casings)
        { " ccccc ", "ccbbbcc", "cbbbbbc", "cbbbbbc", "cbbbbbc", "ccbbbcc", " ccccc " } };

    public static final String[][] SHAPE_TIER_2 = new String[][] {
        // Slice 0 (Top - Nuclear Hatches & Casings)
        { "  ccccccc  ", " ccgggggcc ", "ccgggggggcc", "cgggggggggc", "cgggggggggc", "cgggggggggc", "cgggggggggc",
            "cgggggggggc", "ccgggggggcc", " ccgggggcc ", "  ccccccc  " },
        // Slice 1 (Second from top - Item Pipe Casings & Casings)
        { "  ccccccc  ", " ccpppppcc ", "ccpppppppcc", "cpppppppppc", "cpppppppppc", "cpppppppppc", "cpppppppppc",
            "cpppppppppc", "ccpppppppcc", " ccpppppcc ", "  ccccccc  " },
        // Slice 2 (Third from top - Nuclear Casing Walls, Hollow Interior)
        { "  ccccccc  ", " cc-----cc ", "cc-------cc", "c---------c", "c---------c", "c---------c", "c---------c",
            "c---------c", "cc-------cc", " cc-----cc ", "  ccccccc  " },
        // Slice 3 (Fourth from top / Controller layer - Front Center)
        { "  ccc~ccc  ", " cc-----cc ", "cc-------cc", "c---------c", "c---------c", "c---------c", "c---------c",
            "c---------c", "cc-------cc", " cc-----cc ", "  ccccccc  " },
        // Slice 4 (Bottom - Nuclear Core Bottom Casings / HP Hatches & Perimeter Casings)
        { "  ccccccc  ", " ccbbbbbcc ", "ccbbbbbbbcc", "cbbbbbbbbbc", "cbbbbbbbbbc", "cbbbbbbbbbc", "cbbbbbbbbbc",
            "cbbbbbbbbbc", "ccbbbbbbbcc", " ccbbbbbcc ", "  ccccccc  " } };

    public static final String[][] SHAPE_TIER_3 = new String[][] {
        // Slice 0 (Top - Nuclear Hatches & Casings)
        { "   ccccccccc   ", "  ccgggggggcc  ", " ccgggggggggcc ", "ccgggggggggggcc", "cgggggggggggggc",
            "cgggggggggggggc", "cgggggggggggggc", "cgggggggggggggc", "cgggggggggggggc", "cgggggggggggggc",
            "cgggggggggggggc", "ccgggggggggggcc", " ccgggggggggcc ", "  ccgggggggcc  ", "   ccccccccc   " },
        // Slice 1 (Second from top - Item Pipe Casings & Casings)
        { "   ccccccccc   ", "  ccpppppppcc  ", " ccpppppppppcc ", "ccpppppppppppcc", "cpppppppppppppc",
            "cpppppppppppppc", "cpppppppppppppc", "cpppppppppppppc", "cpppppppppppppc", "cpppppppppppppc",
            "cpppppppppppppc", "ccpppppppppppcc", " ccpppppppppcc ", "  ccpppppppcc  ", "   ccccccccc   " },
        // Slice 2 (Third from top - Nuclear Casing Walls, Hollow Interior)
        { "   ccccccccc   ", "  cc-------cc  ", " cc---------cc ", "cc-----------cc", "c-------------c",
            "c-------------c", "c-------------c", "c-------------c", "c-------------c", "c-------------c",
            "c-------------c", "cc-----------cc", " cc---------cc ", "  cc-------cc  ", "   ccccccccc   " },
        // Slice 3 (Fourth from top / Controller layer - Front Center)
        { "   cccc~cccc   ", "  cc-------cc  ", " cc---------cc ", "cc-----------cc", "c-------------c",
            "c-------------c", "c-------------c", "c-------------c", "c-------------c", "c-------------c",
            "c-------------c", "cc-----------cc", " cc---------cc ", "  cc-------cc  ", "   ccccccccc   " },
        // Slice 4 (Bottom - Nuclear Core Bottom Casings / HP Hatches & Perimeter Casings)
        { "   ccccccccc   ", "  ccbbbbbbbcc  ", " ccbbbbbbbbbcc ", "ccbbbbbbbbbbbcc", "cbbbbbbbbbbbbbc",
            "cbbbbbbbbbbbbbc", "cbbbbbbbbbbbbbc", "cbbbbbbbbbbbbbc", "cbbbbbbbbbbbbbc", "cbbbbbbbbbbbbbc",
            "cbbbbbbbbbbbbbc", "ccbbbbbbbbbbbcc", " ccbbbbbbbbbcc ", "  ccbbbbbbbcc  ", "   ccccccccc   " } };

    private static IStructureDefinition<MTENuclearReactor> STRUCTURE_DEFINITION = null;

    public int mPipeTier = -1;
    public int gridSize = 0;
    public int coreDimension = 0;
    public INuclearTile[][] mGrid = null;
    public final List<IGregTechTileEntity> mNuclearTiles = new ArrayList<>();
    public final List<MTEHatchNuclearHighPressure> mTopHighPressureHatches = new ArrayList<>();
    public final List<MTEHatchNuclearHighPressure> mBottomHighPressureHatches = new ArrayList<>();

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
    public static final int GUI_MODE_NEUTRON_FLUX = 2;
    public static final int GUI_MODE_NEUTRON_ABSORPTION = 3;
    public int mCurrentGuiMode = GUI_MODE_COMPONENTS;
    public ReactorGridSyncData mClientGridData = null;

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
            int randIdx = (base != null)
                ? base.getRandomNumber(working.size())
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
        if (tier == 0 && ModMetaTileEntities.nuclearBus != null) {
            return ModMetaTileEntities.nuclearBus.copy();
        }
        if (tier >= 1 && tier <= 9 && ModMetaTileEntities.nuclearHatches[tier - 1] != null) {
            return ModMetaTileEntities.nuclearHatches[tier - 1].copy();
        }
        if (tier == 10 && ModMetaTileEntities.nuclearHighPressureHatch != null) {
            return ModMetaTileEntities.nuclearHighPressureHatch.copy();
        }
        return null;
    }

    public ForgeDirection getNuclearHatchFacing() {
        ExtendedFacing ef = getExtendedFacing();
        return ef != null ? ef.getRelativeUpInWorld() : ForgeDirection.UP;
    }

    public static class NuclearHatchElement implements IStructureElement<MTENuclearReactor> {

        @Override
        public boolean check(MTENuclearReactor t, World world, int x, int y, int z) {
            if (world.getTileEntity(x, y, z) instanceof IGregTechTileEntity te) {
                ForgeDirection requiredFacing = t != null ? t.getNuclearHatchFacing() : ForgeDirection.UP;
                if (te.getFrontFacing() != requiredFacing) {
                    return false;
                }
                IMetaTileEntity mte = te.getMetaTileEntity();
                if (mte instanceof MTEHatchNuclearHatch hatch) {
                    hatch.mReactor = t;
                    int tier = hatch.mTier;
                    if (t.mHatchTier == -1) {
                        t.mHatchTier = tier;
                    } else if (t.mHatchTier != tier) {
                        t.mHatchTierInconsistent = true;
                    }
                    t.mNuclearTiles.add(te);
                    return true;
                } else if (mte instanceof MTEHatchNuclearBus bus) {
                    bus.mReactor = t;
                    t.mNuclearTiles.add(te);
                    return true;
                } else if (mte instanceof MTEHatchNuclearControlRod rod) {
                    rod.mReactor = t;
                    t.mNuclearTiles.add(te);
                    return true;
                } else if (mte instanceof MTEHatchNuclearHighPressure hp) {
                    hp.mReactor = t;
                    hp.setIsInlet(true);
                    t.mNuclearTiles.add(te);
                    t.mTopHighPressureHatches.add(hp);
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean couldBeValid(MTENuclearReactor t, World world, int x, int y, int z, ItemStack trigger) {
            if (world.getTileEntity(x, y, z) instanceof IGregTechTileEntity te) {
                IMetaTileEntity mte = te.getMetaTileEntity();
                return mte instanceof MTEHatchNuclearHatch || mte instanceof MTEHatchNuclearBus
                    || mte instanceof MTEHatchNuclearControlRod
                    || mte instanceof MTEHatchNuclearHighPressure;
            }
            return world.getBlock(x, y, z) == GregTechAPI.sBlockMachines;
        }

        @Override
        public List<String> getDescription(MTENuclearReactor t) {
            return Collections.singletonList("Top-layer hatches must face opposite to pipe casings");
        }

        @Override
        public boolean spawnHint(MTENuclearReactor t, World world, int x, int y, int z, ItemStack trigger) {
            StructureLibAPI.hintParticle(world, x, y, z, GregTechAPI.sBlockMachines, 0);
            return true;
        }

        @Override
        public boolean placeBlock(MTENuclearReactor t, World world, int x, int y, int z, ItemStack trigger) {
            ForgeDirection targetFacing = t != null ? t.getNuclearHatchFacing() : ForgeDirection.UP;
            TileEntity existingTe = world.getTileEntity(x, y, z);
            if (existingTe instanceof IGregTechTileEntity gte && isNuclearCoreHatch(gte.getMetaTileEntity())) {
                gte.setFrontFacing(targetFacing);
                return true;
            }

            int tier = NuclearStructureChannels.NUCLEAR_HATCH.getValueClamped(trigger, 0, 10);
            ItemStack stack = getNuclearHatchStack(tier);
            if (stack == null) return false;
            if (stack.getItem() instanceof ItemMachines itemMachines) {
                boolean success = itemMachines
                    .placeBlockAt(stack, null, world, x, y, z, targetFacing.ordinal(), 0.5f, 0.5f, 0.5f, 0);
                if (success && world.getTileEntity(x, y, z) instanceof ITurnable turnable) {
                    turnable.setFrontFacing(targetFacing);
                }
                return success;
            }
            return false;
        }

        @Override
        public PlaceResult survivalPlaceBlock(MTENuclearReactor t, World world, int x, int y, int z, ItemStack trigger,
            AutoPlaceEnvironment env) {
            ForgeDirection targetFacing = t != null ? t.getNuclearHatchFacing() : ForgeDirection.UP;
            TileEntity existingTe = world.getTileEntity(x, y, z);
            if (existingTe instanceof IGregTechTileEntity gte && isNuclearCoreHatch(gte.getMetaTileEntity())) {
                if (gte.getFrontFacing() != targetFacing) {
                    gte.setFrontFacing(targetFacing);
                    return PlaceResult.ACCEPT;
                }
                return PlaceResult.SKIP;
            }

            if (check(t, world, x, y, z)) return PlaceResult.SKIP;
            if (!StructureLibAPI.isBlockTriviallyReplaceable(world, x, y, z, env.getActor())) {
                return PlaceResult.REJECT;
            }
            int tier = NuclearStructureChannels.NUCLEAR_HATCH.getValueClamped(trigger, 0, 10);
            ItemStack stack = getNuclearHatchStack(tier);
            if (stack == null) return PlaceResult.REJECT;

            PlaceResult result = StructureUtility.survivalPlaceBlock(
                stack,
                ItemStackPredicate.NBTMode.EXACT,
                null,
                false,
                world,
                x,
                y,
                z,
                env.getSource(),
                env.getActor(),
                env.getChatter());
            if (result == PlaceResult.ACCEPT && world.getTileEntity(x, y, z) instanceof ITurnable turnable) {
                turnable.setFrontFacing(targetFacing);
            }
            return result;
        }

        @Override
        public BlocksToPlace getBlocksToPlace(MTENuclearReactor t, World world, int x, int y, int z, ItemStack trigger,
            AutoPlaceEnvironment env) {
            int tier = NuclearStructureChannels.NUCLEAR_HATCH.getValueClamped(trigger, 0, 10);
            ItemStack stack = getNuclearHatchStack(tier);
            return stack != null ? BlocksToPlace.create(stack) : BlocksToPlace.createEmpty();
        }
    }

    public static class BottomCoreElement implements IStructureElement<MTENuclearReactor> {

        @Override
        public boolean check(MTENuclearReactor t, World world, int x, int y, int z) {
            Block block = world.getBlock(x, y, z);
            int meta = world.getBlockMetadata(x, y, z);
            if (block == ModBlocks.nuclearCasing && meta == 0) {
                if (t != null) {
                    t.mCasing++;
                }
                return true;
            }
            if (world.getTileEntity(x, y, z) instanceof IGregTechTileEntity te) {
                ForgeDirection requiredFacing = (t != null ? t.getNuclearHatchFacing() : ForgeDirection.UP)
                    .getOpposite();
                if (te.getFrontFacing() != requiredFacing) {
                    return false;
                }
                IMetaTileEntity mte = te.getMetaTileEntity();
                if (mte instanceof MTEHatchNuclearHighPressure hpHatch) {
                    if (t != null) {
                        hpHatch.mReactor = t;
                        hpHatch.setIsInlet(false);
                        t.mBottomHighPressureHatches.add(hpHatch);
                    }
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean couldBeValid(MTENuclearReactor t, World world, int x, int y, int z, ItemStack trigger) {
            Block block = world.getBlock(x, y, z);
            int meta = world.getBlockMetadata(x, y, z);
            if (block == ModBlocks.nuclearCasing && meta == 0) {
                return true;
            }
            if (world.getTileEntity(x, y, z) instanceof IGregTechTileEntity te) {
                return te.getMetaTileEntity() instanceof MTEHatchNuclearHighPressure;
            }
            return block == GregTechAPI.sBlockMachines;
        }

        @Override
        public List<String> getDescription(MTENuclearReactor t) {
            return Collections.singletonList(
                "Bottom core position: Nuclear Casing or Nuclear Core High-Pressure Hatch (facing DOWN)");
        }

        @Override
        public boolean spawnHint(MTENuclearReactor t, World world, int x, int y, int z, ItemStack trigger) {
            StructureLibAPI.hintParticle(world, x, y, z, ModBlocks.nuclearCasing, 0);
            return true;
        }

        @Override
        public boolean placeBlock(MTENuclearReactor t, World world, int x, int y, int z, ItemStack trigger) {
            ForgeDirection targetFacing = (t != null ? t.getNuclearHatchFacing() : ForgeDirection.UP).getOpposite();
            TileEntity existingTe = world.getTileEntity(x, y, z);
            if (existingTe instanceof IGregTechTileEntity gte
                && gte.getMetaTileEntity() instanceof MTEHatchNuclearHighPressure) {
                gte.setFrontFacing(targetFacing);
                return true;
            }
            return world.setBlock(x, y, z, ModBlocks.nuclearCasing, 0, 3);
        }

        @Override
        public PlaceResult survivalPlaceBlock(MTENuclearReactor t, World world, int x, int y, int z, ItemStack trigger,
            AutoPlaceEnvironment env) {
            if (check(t, world, x, y, z)) return PlaceResult.SKIP;
            if (!StructureLibAPI.isBlockTriviallyReplaceable(world, x, y, z, env.getActor())) {
                return PlaceResult.REJECT;
            }
            ItemStack casingStack = new ItemStack(ModBlocks.nuclearCasing, 1, 0);
            return StructureUtility.survivalPlaceBlock(
                casingStack,
                ItemStackPredicate.NBTMode.EXACT,
                null,
                false,
                world,
                x,
                y,
                z,
                env.getSource(),
                env.getActor(),
                env.getChatter());
        }

        @Override
        public BlocksToPlace getBlocksToPlace(MTENuclearReactor t, World world, int x, int y, int z, ItemStack trigger,
            AutoPlaceEnvironment env) {
            return BlocksToPlace.create(new ItemStack(ModBlocks.nuclearCasing, 1, 0));
        }
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
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<MTENuclearReactor>builder()
                // Tier 1: 7x7 Footprint, 5x5 Octagonal Chamber (21 cells), Height 5
                .addShape(STRUCTURE_TIER_1, transpose(SHAPE_TIER_1))
                // Tier 2: 11x11 Footprint, 9x9 Octagonal Chamber (69 cells), Height 5
                .addShape(STRUCTURE_TIER_2, transpose(SHAPE_TIER_2))
                // Tier 3: 15x15 Footprint, 13x13 Octagonal Chamber (145 cells), Height 5
                .addShape(STRUCTURE_TIER_3, transpose(SHAPE_TIER_3))
                .addElement(
                    'c',
                    buildHatchAdder(MTENuclearReactor.class).atLeast(Maintenance)
                        .adder(
                            (t, te, index) -> t.addMaintenanceToMachineList(te, index)
                                || t.addOutputToMachineList(te, index)
                                || t.addDynamoToMachineList(te, index)
                                || t.addExoticDynamoToMachineList(te, index)
                                || t.addNuclearControlHatchToMachineList(te, index))
                        .casingIndex(CASING_INDEX)
                        .hint(1)
                        .buildAndChain(
                            StructureUtility
                                .onElementPass(t -> t.mCasing++, StructureUtility.ofBlock(ModBlocks.nuclearCasing, 0))))
                .addElement('p', chainItemPipeCasings(-1, (t, casingTier) -> {
                    if (casingTier < 3) {
                        t.mPipeTier = -1;
                    } else {
                        t.mPipeTier = casingTier - 3;
                    }
                }, t -> t.mPipeTier == -1 ? -1 : t.mPipeTier + 3))
                .addElement('g', NuclearStructureChannels.NUCLEAR_HATCH.use(new NuclearHatchElement()))
                .addElement('b', new BottomCoreElement())
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType("Modular Pressure Tube Reactor (MPTR)")
            .addInfo("Modular nuclear reactor simulating discrete neutron transport and moderation")
            .addInfo("Supports self-stabilizing negative temperature reactivity feedback")
            .addInfo("Height is fixed at 5 blocks for all tiers (octagonal prism chamber)")
            .addInfo("Core chamber features cut-corner null cells with reflecting/absorbing casing walls")
            .addInfo("Wall heat dissipation is uniformly distributed to all active cells via coolant pool")
            .addInfo("Item pipe casings determine operating temperature and allowed coolants:")
            .addInfo(" - Electrum: IC2 coolant (sub-boiling conductive cooling, max 1000 °C)")
            .addInfo(" - Platinum: distilled water (sub-boiling conductive cooling, max 1400 °C)")
            .addInfo(" - Osmium: external coolant loops via HP passage hatches (max 1800 °C)")
            .addInfo(" - Quantium: heavy water (sub-boiling conductive cooling, max 2200 °C)")
            .addInfo(" - Fluxed Electrum: external coolant loops with heavy water (max 2600 °C)")
            .addInfo(" - Black Plutonium: all coolants and loop configurations (max 3200 °C)")
            .addInfo("Accepts dynamo and multi-amp dynamo hatches for direct radiovoltaic EU output")
            .addInfo(" - Radiovoltaic cells convert absorbed neutron flux directly to EU (HV 2A, EV 2A)")
            .addInfo("Outputs (depleted items, byproducts, molten cheese) eject to output buses and hatches")
            .addInfo(
                EnumChatFormatting.RED
                    + "Warning: fluid hatches only support sub-boiling cooling (boiling causes explosion)!")
            .addInfo(EnumChatFormatting.RED + "Warning: overheating hatches void contents!")
            .addInfo(
                EnumChatFormatting.RED
                    + "Warning: HP coolant passage hatches require Tier 2+ and 1:1 top/bottom pairing!")
            .addInfo(EnumChatFormatting.RED + "Note: This multiblock cannot share walls!")
            .addInfo("Top-layer hatches must face UP; bottom-layer HP hatches must face DOWN")
            .beginVariableStructureBlock(7, 15, 5, 5, 7, 15, true)
            .addStructureInfo(EnumChatFormatting.RED + "This multiblock cannot share walls")
            .addStructureInfo("Top-layer hatches face UP; bottom-layer HP hatches face DOWN")
            .addController("Front center, 2nd layer")
            .addCasing("50+", "Nuclear casings", false)
            .addCasing(
                "21+",
                "Item pipe casings (Electrum / Platinum / Osmium / Quantium / Fluxed Electrum / Black Plutonium)",
                false)
            .addOtherStructurePart(
                "Nuclear bus / hatch / control rod / HP hatch",
                "Top layer octagonal core positions (facing UP)",
                1)
            .addOtherStructurePart(
                "Nuclear casing or Nuclear core HP hatch",
                "Bottom layer core positions (HP hatch facing DOWN, paired with top)",
                1)
            .addOtherStructurePart("Nuclear control hatch", "Any outer casing", 2)
            .addMaintenanceHatch("Any outer casing (exactly 1)", 1)
            .addDynamoHatch("Any outer casing (optional for radiovoltaic direct EU, max 1)", 1)
            .addOutputBus("Any outer casing (optional)", 1)
            .addOutputHatch("Any outer casing (optional)", 1)
            .addAir("Interior of the structure")
            .addSubChannel(GTStructureChannels.ITEM_PIPE_CASING)
            .addSubChannel(NuclearStructureChannels.NUCLEAR_HATCH)
            .toolTipFinisher(EnumChatFormatting.AQUA + "GregTech nuclear power");
        return tt;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        int tier = stackSize == null ? 1 : stackSize.stackSize;
        if (tier >= 3) {
            buildPiece(STRUCTURE_7X7, stackSize, hintsOnly, 7, 3, 0);
        } else if (tier == 2) {
            buildPiece(STRUCTURE_5X5, stackSize, hintsOnly, 5, 3, 0);
        } else {
            buildPiece(STRUCTURE_3X3, stackSize, hintsOnly, 3, 3, 0);
        }
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        int tier = stackSize == null ? 1 : stackSize.stackSize;
        if (tier >= 3) {
            return survivalBuildPiece(STRUCTURE_7X7, stackSize, 7, 3, 0, elementBudget, env, false, true);
        } else if (tier == 2) {
            return survivalBuildPiece(STRUCTURE_5X5, stackSize, 5, 3, 0, elementBudget, env, false, true);
        } else {
            return survivalBuildPiece(STRUCTURE_3X3, stackSize, 3, 3, 0, elementBudget, env, false, true);
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
        if (!mMachine) {
            mWallNeutronAccumulator = 0;
            mWallMaintenanceTimer = 0;
            mOutputCoolantRate = 0;
            mOutputCoolantName = "";
            for (IGregTechTileEntity te : mNuclearTiles) {
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

        if (checkPiece(STRUCTURE_3X3, 3, 3, 0, errors)) {
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
            if (checkPiece(STRUCTURE_5X5, 5, 3, 0, errors)) {
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
                if (checkPiece(STRUCTURE_7X7, 7, 3, 0, errors)) {
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
                } else if (mte instanceof MTEHatchNuclearControlRod rod) {
                    mGrid[gx][gy] = new NuclearGridTile(this, rod, gx, gy);
                } else if (mte instanceof MTEHatchNuclearHighPressure hp) {
                    mGrid[gx][gy] = new NuclearGridTile(this, hp, gx, gy);
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
            for (IGregTechTileEntity te : mNuclearTiles) {
                if (te != null && te.getMetaTileEntity() instanceof MTEHatchNuclearControlRod rod) {
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
            IGregTechTileEntity base = getBaseMetaTileEntity();
            double ambient = (base != null && base.getWorld() != null)
                ? calculateAmbientTemperature(base.getWorld(), base.getXCoord(), base.getYCoord(), base.getZCoord())
                : NuclearSimulationEngine.DEFAULT_AMBIENT_TEMP;
            double avgTemp = Math.max(mAvgTemp, mAvgTileTemp);
            if (avgTemp <= 1.50 * ambient) {
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
        super.onRemoval();
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
                // Overheating non-fuel contents in hatches/buses are voided, but fuel rods and fluids are NOT directly voided!
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
                        } else if (te.getMetaTileEntity() instanceof MTEHatchNuclearControlRod rod) {
                            if (rod.mTemperature > maxTemp) {
                                overheatingHatchesCount++;
                                rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD] = null;
                                rod.markTileDirty();
                            }
                        }
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
            for (MTEHatchNuclearControl hatch : mControlHatches) {
                if (hatch != null && hatch.isValid()) {
                    hatch.setOutputRedstone((byte) 0);
                }
            }
        }
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
        if (aItemStack != null && aItemStack.hasTagCompound() && aItemStack.getTagCompound().hasKey("mReactorDamage")) {
            double dmg = aItemStack.getTagCompound().getDouble("mReactorDamage");
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
                        } else if (gt.isHatch()) {
                            MTEHatchNuclearHatch hatch = gt.getHatch();
                            cell.isFluid = true;
                            cell.fluidStack = (hatch.mInputFluid != null) ? hatch.mInputFluid.copy() : null;
                            cell.fastFlux = hatch.mLastFastFlux;
                            cell.thermalFlux = hatch.mLastThermalFlux;
                            cell.fastAbsorbed = hatch.mLastFastAbsorbed;
                            cell.thermalAbsorbed = hatch.mLastThermalAbsorbed;
                        } else if (gt.isControlRod()) {
                            MTEHatchNuclearControlRod rod = gt.getControlRod();
                            cell.isFluid = false;
                            ItemStack in = rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD];
                            cell.itemStack = (in != null) ? in.copy() : null;
                            cell.fastFlux = rod.mLastFastFlux;
                            cell.thermalFlux = rod.mLastThermalFlux;
                            cell.fastAbsorbed = rod.mLastFastAbsorbed;
                            cell.thermalAbsorbed = rod.mLastThermalAbsorbed;
                        } else if (gt.isHighPressureHatch()) {
                            MTEHatchNuclearHighPressure hp = gt.getHighPressureHatch();
                            cell.isFluid = false;
                            cell.isHighPressure = true;
                            cell.fastFlux = hp.mLastFastFlux;
                            cell.thermalFlux = hp.mLastThermalFlux;
                            cell.fastAbsorbed = hp.mLastFastAbsorbed;
                            cell.thermalAbsorbed = hp.mLastThermalAbsorbed;
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
        for (IGregTechTileEntity te : mNuclearTiles) {
            if (te != null && te.getMetaTileEntity() instanceof MTEHatchNuclearControlRod rod) {
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

    public ButtonWidget createScramButton(IWidgetBuilder<?> builder) {
        ButtonWidget scramButton = new ButtonWidget() {

            @Override
            public void draw(float partialTicks) {
                int x = 0;
                int y = 0;
                int w = getSize().width;
                int h = getSize().height;
                // Outer dark red/black border
                GuiDraw.drawRect(x, y, w, h, 0xFF330000);
                // Background red
                boolean active = mScram;
                int bgColor = isHovering() ? (active ? 0xFFFF3333 : 0xFFDD1111) : (active ? 0xFFCC0000 : 0xFFAA0000);
                GuiDraw.drawRect(x + 1, y + 1, w - 2, h - 2, bgColor);
                // Highlight line at top
                GuiDraw.drawRect(x + 1, y + 1, w - 2, 1, 0x55FFFFFF);
                // Text: SCRAM or SCRAMMED in bold white letters
                String text = active ? (EnumChatFormatting.BOLD + "SCRAMMED") : (EnumChatFormatting.BOLD + "SCRAM");
                int strW = GuiDraw.getStringWidth(text);
                int strH = 8;
                GuiDraw.drawString(text, (w - strW) / 2, (h - strH) / 2, 0xFFFFFFFF, true);
            }
        };
        scramButton.setPos(8, 91)
            .setSize(64, 16);
        scramButton.setPlayClickSound(true);
        scramButton.dynamicTooltip(() -> {
            List<String> tt = new ArrayList<>();
            tt.add(EnumChatFormatting.RED + "" + EnumChatFormatting.BOLD + "Emergency SCRAM");
            tt.add(EnumChatFormatting.GRAY + "Immediately inserts all control rods to 100%.");
            if (mScram) {
                tt.add(EnumChatFormatting.YELLOW + "Status: active (click to reset/disengage)");
            } else {
                tt.add(EnumChatFormatting.GREEN + "Status: disengaged (normal redstone control)");
            }
            return tt;
        });
        scramButton.setUpdateTooltipEveryTick(true);
        scramButton.setOnClick((clickData, widget) -> { setScram(!mScram); });
        return scramButton;
    }

    public static final int REACTOR_GRID_WINDOW_ID = 20;

    public ButtonWidget createReactorGridButton(IWidgetBuilder<?> builder) {
        ButtonWidget button = (ButtonWidget) ButtonWidget.openSyncedWindowButton(REACTOR_GRID_WINDOW_ID)
            .setPlayClickSound(true)
            .setBackground(
                () -> new IDrawable[] { GTUITextures.BUTTON_STANDARD, new ItemDrawable(ItemList.RodUranium.get(1L)) })
            .setPos(174, 91)
            .setSize(16, 16);
        button.addTooltip(StatCollector.translateToLocal("GT5U.gui.button.reactor_hatches"))
            .setTooltipShowUpDelay(TOOLTIP_DELAY);
        return button;
    }

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext buildContext) {
        builder.widget(
            new DrawableWidget().setDrawable(GTUITextures.PICTURE_SCREEN_BLACK)
                .setPos(4, 4)
                .setSize(190, 85));
        final SlotWidget inventorySlot = new SlotWidget(inventoryHandler, 1);
        builder.widget(
            inventorySlot.setPos(173, 167)
                .setBackground(GTUITextures.SLOT_DARK_GRAY));

        final DynamicPositionedColumn screenElements = new DynamicPositionedColumn();
        drawTexts(screenElements, inventorySlot);
        builder.widget(
            new Scrollable().setVerticalScroll()
                .widget(screenElements)
                .setPos(10, 7)
                .setSize(182, 79));

        builder.widget(createStructureUpdateButton(builder));

        // Add Reactor Hatches button on the right column
        builder.widget(createReactorGridButton(builder));

        // Add big red SCRAM button
        builder.widget(createScramButton(builder));

        // Register synced window for the Reactor Hatches view
        buildContext.addSyncedWindow(REACTOR_GRID_WINDOW_ID, this::createReactorGridWindow);

        // Network syncer for reactor grid & telemetry
        builder.widget(
            new FakeSyncWidget<>(
                this::collectGridSyncData,
                this::applyGridSyncData,
                ReactorGridSyncData::writeToBuffer,
                ReactorGridSyncData::readFromBuffer));
    }

    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);

        screenElements.widget(
            new TextWidget()
                .setStringSupplier(
                    () -> String.format(
                        "Core Temp: %.1f / %.0f °C",
                        mCoreTemp,
                        NuclearSimulationEngine.getMaxOperatingTemperature(mPipeTier)))
                .setDefaultColor(Color.rgb(255, 200, 0))
                .setTextAlignment(Alignment.CenterLeft)
                .setEnabled(widget -> mMachine));
        screenElements.widget(
            new TextWidget().setStringSupplier(() -> String.format("Avg Reactivity: %.1f%%", mReactivity * 100.0))
                .setDefaultColor(Color.rgb(100, 200, 255))
                .setTextAlignment(Alignment.CenterLeft)
                .setEnabled(widget -> mMachine));
        screenElements.widget(
            new TextWidget()
                .setStringSupplier(() -> "Flux: " + NuclearSimulationEngine.formatNeutronFlux(mNeutronsProduced))
                .setDefaultColor(Color.rgb(100, 220, 255))
                .setTextAlignment(Alignment.CenterLeft)
                .setEnabled(widget -> mMachine));
        screenElements.widget(
            new TextWidget().setStringSupplier(
                () -> String
                    .format("Neutrons: %d fast, %d therm, %d esc", mFastAbsorbed, mThermalAbsorbed, mEscapedNeutrons))
                .setDefaultColor(Color.rgb(200, 200, 200))
                .setTextAlignment(Alignment.CenterLeft)
                .setEnabled(widget -> mMachine));
        screenElements.widget(
            new TextWidget()
                .setStringSupplier(() -> String.format(Locale.US, "Damage: %.1f%%", mReactorDamage))
                .setDefaultColor(Color.rgb(255, 80, 80))
                .setTextAlignment(Alignment.CenterLeft)
                .setEnabled(widget -> mMachine && mReactorDamage > 0.0));
    }

    public ModularWindow createReactorGridWindow(final EntityPlayer player) {
        ReactorGridSyncData sync = getClientGridData();
        int N = 7;
        if (sync != null && sync.gridSize > 0) {
            N = sync.gridSize;
        } else if (mGrid != null && mGrid.length > 0) {
            N = mGrid.length;
        }

        final int idealH = N * 18 + 44;
        final int idealW = Math.max(154, N * 18 + 20);

        int screenW = ClientScreenHelper.getScaledScreenWidth();
        int screenH = ClientScreenHelper.getScaledScreenHeight();
        int topLimit = ClientScreenHelper.getTopReservedHeight();
        int parentH = getGUIHeight();
        int mainY = (screenH - parentH) / 2;
        int playerInvY = mainY + 104;
        int bottomLimit = playerInvY - 2;
        int availH = Math.max(70, bottomLimit - topLimit);

        final int w = Math.min(idealW, screenW - 8);
        final int h = Math.min(idealH, availH);

        ModularWindow.Builder builder = ModularWindow.builder(w, h);
        builder.setBackground(GTUITextures.BACKGROUND_SINGLEBLOCK_DEFAULT);
        builder.setGuiTint(getGUIColorization());
        builder.setDraggable(true);
        builder.setPos((screenSize, mainWindow) -> {
            int scW = (screenSize != null && screenSize.width > 0) ? screenSize.width
                : ClientScreenHelper.getScaledScreenWidth();
            int scH = (screenSize != null && screenSize.height > 0) ? screenSize.height
                : ClientScreenHelper.getScaledScreenHeight();
            int top = ClientScreenHelper.getTopReservedHeight();
            int mY = (mainWindow != null) ? mainWindow.getPos().y : (scH - getGUIHeight()) / 2;
            int pInvY = mY + 104;
            int bLimit = pInvY - 2;
            int avail = Math.max(70, bLimit - top);

            int targetH = Math.min(idealH, avail);
            int targetW = Math.min(idealW, scW - 8);

            int px = Math.max(2, (scW - targetW) / 2);
            int py = Math.max(top, top + (avail - targetH) / 2);
            return new Pos2d(px, py);
        });

        NuclearReactorGridWidget gridWidget = new NuclearReactorGridWidget(this);
        Scrollable scrollable = new Scrollable().setVerticalScroll()
            .setHorizontalScroll();
        scrollable.widget(gridWidget);
        scrollable.setPos(10, 18)
            .setSizeProvider(
                (size, window, parent) -> new Size(
                    Math.max(60, window.getSize().width - 20),
                    Math.max(30, window.getSize().height - 44)));
        gridWidget.setParentScrollable(scrollable);
        builder.widget(scrollable);

        // Mode Toggle Button (Compact 16x16 at top-right)
        ButtonWidget modeButton = new ButtonWidget() {

            @Override
            public void draw(float partialTicks) {
                super.draw(partialTicks);
                GlStateManager.pushMatrix();
                if (mCurrentGuiMode == GUI_MODE_COMPONENTS) {
                    new ItemDrawable(ItemList.RodUranium.get(1L)).draw(0, 0, 16, 16, partialTicks);
                } else if (mCurrentGuiMode == GUI_MODE_TEMPERATURE) {
                    new ItemDrawable(new ItemStack(Items.fire_charge)).draw(0, 0, 16, 16, partialTicks);
                } else if (mCurrentGuiMode == GUI_MODE_NEUTRON_FLUX) {
                    new ItemDrawable(new ItemStack(Items.nether_star)).draw(0, 0, 16, 16, partialTicks);
                } else {
                    new ItemDrawable(new ItemStack(Blocks.iron_bars)).draw(0, 0, 16, 16, partialTicks);
                }
                GlStateManager.popMatrix();
                GlStateManager.disableLighting();
                GlStateManager.disableDepth();
                GlStateManager.enableBlend();
                GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            }
        };
        modeButton.setPosProvider((size, window, parent) -> new Pos2d(window.getSize().width - 36, 1))
            .setSize(16, 16);
        modeButton.setBackground(GTUITextures.BUTTON_STANDARD);
        modeButton.setUpdateTooltipEveryTick(true);
        modeButton.dynamicTooltip(this::getModeButtonTooltip);
        modeButton.setOnClick((clickData, widget) -> {
            if (clickData.shift) {
                mCurrentGuiMode = (mCurrentGuiMode + 1) % 4;
            } else {
                mCurrentGuiMode = (mCurrentGuiMode == GUI_MODE_COMPONENTS) ? GUI_MODE_TEMPERATURE : GUI_MODE_COMPONENTS;
            }
        });
        builder.widget(modeButton);

        // Close Button (Compact 16x16 at top-right corner)
        builder.widget(
            ButtonWidget.closeWindowButton(true)
                .setPosProvider((size, window, parent) -> new Pos2d(window.getSize().width - 18, 1))
                .setSize(16, 16));

        // Controller Position Indicator (Front face orientation icon, centered above zoom controls)
        builder.widget(new com.gtnewhorizons.modularui.api.widget.Widget() {

            private final ItemDrawable drawable = new ItemDrawable(ModMetaTileEntities.reactor.copy());

            @Override
            public void draw(float partialTicks) {
                GlStateManager.pushMatrix();
                drawable.draw(0, 0, 12, 12, partialTicks);
                GlStateManager.popMatrix();
                GlStateManager.disableLighting();
                GlStateManager.disableDepth();
                GlStateManager.enableBlend();
                GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            }
        }.setPosProvider(
            (size, window, parent) -> new Pos2d((window.getSize().width - 12) / 2, window.getSize().height - 25))
            .setSize(12, 12)
            .addTooltip("Reactor controller (front face)"));

        // Zoom Out Button [-] (Compact 12x10 at bottom)
        ButtonWidget zoomOutBtn = new ButtonWidget() {

            @Override
            public void draw(float partialTicks) {
                super.draw(partialTicks);
                String str = "-";
                int sw = GuiDraw.getStringWidth(str);
                GuiDraw.drawString(str, (getSize().width - sw) / 2, 1, 0xFFFFFF, false);
            }
        };
        zoomOutBtn
            .setPosProvider(
                (size, window, parent) -> new Pos2d((window.getSize().width - 119) / 2, window.getSize().height - 12))
            .setSize(12, 10);
        zoomOutBtn.setBackground(GTUITextures.BUTTON_STANDARD);
        zoomOutBtn.addTooltip("Zoom out");
        zoomOutBtn.setOnClick((clickData, widget) -> gridWidget.zoomOut());
        builder.widget(zoomOutBtn);

        // Zoom Slider [===O===] (Compact 70x10 at bottom)
        ZoomSliderWidget zoomSlider = new ZoomSliderWidget(gridWidget);
        zoomSlider.setPosProvider(
            (size, window, parent) -> new Pos2d((window.getSize().width - 119) / 2 + 15, window.getSize().height - 12));
        builder.widget(zoomSlider);

        // Zoom In Button [+] (Compact 12x10 at bottom)
        ButtonWidget zoomInBtn = new ButtonWidget() {

            @Override
            public void draw(float partialTicks) {
                super.draw(partialTicks);
                String str = "+";
                int sw = GuiDraw.getStringWidth(str);
                GuiDraw.drawString(str, (getSize().width - sw) / 2, 1, 0xFFFFFF, false);
            }
        };
        zoomInBtn.setPosProvider(
            (size, window, parent) -> new Pos2d((window.getSize().width - 119) / 2 + 88, window.getSize().height - 12))
            .setSize(12, 10);
        zoomInBtn.setBackground(GTUITextures.BUTTON_STANDARD);
        zoomInBtn.addTooltip("Zoom in");
        zoomInBtn.setOnClick((clickData, widget) -> gridWidget.zoomIn());
        builder.widget(zoomInBtn);

        // Reset Zoom Button [1:1] (Compact 16x10 at bottom)
        ButtonWidget zoomResetBtn = new ButtonWidget() {

            @Override
            public void draw(float partialTicks) {
                super.draw(partialTicks);
                String str = "1:1";
                int sw = GuiDraw.getStringWidth(str);
                int color = (gridWidget.getCurrentCellSize() == 18) ? 0x88FF88 : 0xFFFFFF;
                GuiDraw.drawString(str, (getSize().width - sw) / 2, 1, color, false);
            }
        };
        zoomResetBtn.setPosProvider(
            (size, window, parent) -> new Pos2d((window.getSize().width - 119) / 2 + 103, window.getSize().height - 12))
            .setSize(16, 10);
        zoomResetBtn.setBackground(GTUITextures.BUTTON_STANDARD);
        zoomResetBtn.dynamicTooltip(() -> {
            List<String> tt = new ArrayList<>();
            tt.add("Reset zoom (1:1)");
            tt.add(EnumChatFormatting.GRAY + "Current zoom: " + gridWidget.getZoomPercent() + "%");
            return tt;
        });
        zoomResetBtn.setUpdateTooltipEveryTick(true);
        zoomResetBtn.setOnClick((clickData, widget) -> gridWidget.resetZoom());
        builder.widget(zoomResetBtn);

        // Window resize widget covering the window perimeter
        builder.widget(new WindowResizeWidget(gridWidget));

        return builder.build();
    }

    private final ReactorDummy mReactorDummy = new ReactorDummy(this);

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
        if (stack == null) return false;
        if (stack.getItem() instanceof ItemRadioactiveCell) return true;
        if (stack.getItem() instanceof ItemReactorUranium) return true;
        if (NuclearFuelType.fromName(stack.getUnlocalizedName()) != null) return true;
        String name = stack.getUnlocalizedName();
        if (name != null) {
            String lower = name.toLowerCase();
            return lower.contains("uranium") || lower.contains("mox")
                || lower.contains("thorium")
                || lower.contains("plutonium")
                || lower.contains("naquadah")
                || lower.contains("naquadria")
                || lower.contains("tiberium")
                || lower.contains("thecore")
                || lower.contains("glowstone")
                || lower.contains("lithium")
                || lower.contains("fuelrod");
        }
        return false;
    }

    public static boolean isFluidFuel(FluidStack fluid) {
        if (fluid == null || fluid.getFluid() == null) return false;
        String name = fluid.getFluid()
            .getName()
            .toLowerCase();
        if (name.contains("naquadah")) return false; // Avoid overlap with Large Naquadah Reactor
        if (name.contains("thoriumbasedliquidfuel") || (name.contains("thorium") && name.contains("liquidfuel")))
            return true;
        if (name.contains("uraniumbasedliquidfuel") || (name.contains("uranium") && name.contains("liquidfuel")))
            return true;
        if (name.contains("plutoniumbasedliquidfuel") || (name.contains("plutonium") && name.contains("liquidfuel")))
            return true;
        if (name.contains("uraniumhexafluoride")) return true;
        try {
            if (fluid.isFluidEqual(GGMaterial.uraniumBasedLiquidFuel.getFluidOrGas(1))
                || fluid.isFluidEqual(GGMaterial.uraniumBasedLiquidFuelExcited.getFluidOrGas(1))
                || fluid.isFluidEqual(GGMaterial.thoriumBasedLiquidFuel.getFluidOrGas(1))
                || fluid.isFluidEqual(GGMaterial.thoriumBasedLiquidFuelExcited.getFluidOrGas(1))
                || fluid.isFluidEqual(GGMaterial.plutoniumBasedLiquidFuel.getFluidOrGas(1))
                || fluid.isFluidEqual(GGMaterial.plutoniumBasedLiquidFuelExcited.getFluidOrGas(1))) {
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static Fluid getSpentFluid(FluidStack fuel) {
        if (fuel == null || fuel.getFluid() == null) return null;
        String name = fuel.getFluid()
            .getName()
            .toLowerCase();
        if (name.contains("thorium")) {
            try {
                return GGMaterial.thoriumBasedLiquidFuelDepleted.getFluidOrGas(1)
                    .getFluid();
            } catch (Throwable ignored) {}
            Fluid f = FluidRegistry.getFluid("thoriumbasedliquidfueldepleted");
            if (f != null) return f;
            return FluidRegistry.getFluid("fluid.thoriumbasedliquidfueldepleted");
        }
        if (name.contains("uraniumbased") || (name.contains("uranium") && name.contains("liquidfuel"))) {
            try {
                return GGMaterial.uraniumBasedLiquidFuelDepleted.getFluidOrGas(1)
                    .getFluid();
            } catch (Throwable ignored) {}
            Fluid f = FluidRegistry.getFluid("uraniumbasedliquidfueldepleted");
            if (f != null) return f;
            return FluidRegistry.getFluid("fluid.uraniumbasedliquidfueldepleted");
        }
        if (name.contains("plutonium")) {
            try {
                return GGMaterial.plutoniumBasedLiquidFuelDepleted.getFluidOrGas(1)
                    .getFluid();
            } catch (Throwable ignored) {}
            Fluid f = FluidRegistry.getFluid("plutoniumbasedliquidfueldepleted");
            if (f != null) return f;
            return FluidRegistry.getFluid("fluid.plutoniumbasedliquidfueldepleted");
        }
        if (name.contains("uraniumhexafluoride")) {
            Fluid tetra = FluidRegistry.getFluid("uraniumtetrafluoride");
            if (tetra != null) return tetra;
            return FluidRegistry.getFluid("fluid.uraniumtetrafluoride");
        }
        return null;
    }

    public static boolean isCoolantFluid(FluidStack fluid) {
        if (fluid == null || fluid.getFluid() == null) return false;
        String name = fluid.getFluid()
            .getName()
            .toLowerCase();
        return name.contains("water") || name.contains("coolant") || name.contains("sodium") || name.contains("lead");
    }

    public static boolean isItemCoolant(ItemStack stack) {
        if (stack == null) return false;
        if (stack.getItem() instanceof ItemCoolantCell) return true;
        String name = stack.getUnlocalizedName()
            .toLowerCase();
        return name.contains("coolant") || name.contains("heatcapacitor");
    }

    public static boolean isItemHeatVent(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        String name = stack.getUnlocalizedName()
            .toLowerCase();
        // Skip reactor heat vent as requested (no reactor hull heat)
        if (name.contains("core")) return false;
        if (stack.getItem() instanceof ItemReactorVent || stack.getItem() instanceof ItemReactorVentSpread) return true;
        return name.contains("reactorvent");
    }

    public static boolean isItemHeatExchanger(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        String name = stack.getUnlocalizedName()
            .toLowerCase();
        // Skip reactor heat exchanger as requested (no reactor hull heat)
        if (name.contains("core")) return false;
        if (stack.getItem() instanceof ItemReactorHeatSwitch) return true;
        return name.contains("heatswitch") || name.contains("heatexchanger");
    }

    public static double getInsulationDampening(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return 0.0;

        // 1. Naquarite Universal Insulator Foil: rejects 100% heat & radiation
        try {
            if (ItemList.Naquarite_Universal_Insulator_Foil.isStackEqual(stack, false, true)) {
                return 1.0;
            }
        } catch (Throwable ignored) {}

        String unlocalizedName = stack.getUnlocalizedName();
        if (unlocalizedName != null) {
            String lower = unlocalizedName.toLowerCase();
            if (lower.contains("naquarite_universal_insulator_foil")
                || lower.contains("naquariteuniversalinsulatorfoil")
                || (lower.contains("naquarite") && lower.contains("insulator"))) {
                return 1.0;
            }
            if (lower.contains("micainsulatorfoil") || lower.contains("mica_insulator_foil")
                || (lower.contains("mica") && lower.contains("foil"))) {
                return 0.60;
            }
            if (lower.contains("thermalclotht2") || lower.contains("thermal_cloth_t2")
                || (lower.contains("thermalcloth") && (lower.contains("t2") || lower.contains("2")))) {
                return 0.40;
            }
            if (lower.contains("itembasicasteroids") && stack.getItemDamage() == 7) {
                return 0.20;
            }
            if (lower.contains("thermalcloth") || lower.contains("thermal_cloth")) {
                return 0.20;
            }
        }

        try {
            ItemStack gcCloth = gregtech.api.util.GTModHandler
                .getModItem("GalacticraftMars", "item.itemBasicAsteroids", 1, 7);
            if (gcCloth != null && GTUtility.areStacksEqual(stack, gcCloth, false)) {
                return 0.20;
            }
            ItemStack gsClothT2 = gregtech.api.util.GTModHandler.getModItem("GalaxySpace", "item.ThermalClothT2", 1);
            if (gsClothT2 != null && GTUtility.areStacksEqual(stack, gsClothT2, true)) {
                return 0.40;
            }
            ItemStack micaFoil = gregtech.api.util.GTModHandler.getModItem("dreamcraft", "MicaInsulatorFoil", 1);
            if (micaFoil != null && GTUtility.areStacksEqual(stack, micaFoil, true)) {
                return 0.60;
            }
        } catch (Throwable ignored) {}

        return 0.0;
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

            case MTEHatchNuclearControl.MODE_DAMAGE_MIN,
                 MTEHatchNuclearControl.MODE_DAMAGE_MAX,
                 MTEHatchNuclearControl.MODE_DAMAGE_AVG -> mReactorDamage / 100.0;

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
        double minTemp = Double.MAX_VALUE;
        double maxTemp = -Double.MAX_VALUE;
        double sumTemp = 0.0;
        int tileCount = 0;

        double minCoolDur = Double.MAX_VALUE;
        double maxCoolDur = -Double.MAX_VALUE;
        double sumCoolDur = 0.0;
        int coolItemCount = 0;
        int totalCoolantItems = 0;

        double minFuelDur = Double.MAX_VALUE;
        double maxFuelDur = -Double.MAX_VALUE;
        double sumFuelDur = 0.0;
        int fuelItemCount = 0;
        int totalFuelItems = 0;

        double minCoolFill = Double.MAX_VALUE;
        double maxCoolFill = -Double.MAX_VALUE;
        double sumCoolFill = 0.0;
        int coolantFluidHatchCount = 0;
        long totalCoolantFluid = 0;
        long totalCoolantCapacity = 0;

        double minFuelFill = Double.MAX_VALUE;
        double maxFuelFill = -Double.MAX_VALUE;
        double sumFuelFill = 0.0;
        int fuelFluidHatchCount = 0;
        long totalFuelFluid = 0;
        long totalFuelCapacity = 0;

        int totalCoolantCoreHatches = 0;
        int totalFuelCoreHatches = 0;

        if (mGrid != null && gridSize > 0) {
            for (int x = 0; x < gridSize; x++) {
                for (int y = 0; y < gridSize; y++) {
                    INuclearTile tile = mGrid[x][y];
                    if (tile != null) {
                        double temp = tile.getTemperature();
                        if (temp < minTemp) minTemp = temp;
                        if (temp > maxTemp) maxTemp = temp;
                        sumTemp += temp;
                        tileCount++;
                    }
                    if (tile instanceof NuclearGridTile gt) {
                        if (gt.isBus()) {
                            MTEHatchNuclearBus bus = gt.getBus();
                            ItemStack stack = bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT];
                            boolean isCoolant = bus.mUsedForCooling;
                            if (stack != null && stack.stackSize > 0) {
                                if (isItemFuel(stack)) {
                                    isCoolant = false;
                                    totalFuelItems += stack.stackSize;
                                    double dur;
                                    if (stack.getItem() instanceof ItemRadioactiveCell radCell) {
                                        int max = radCell.getMaxDamageEx();
                                        int cur = radCell.getDamageOfStack(stack);
                                        dur = max > 0 ? ((double) (max - cur) / max) * 100.0 : 0.0;
                                    } else if (stack.isItemStackDamageable() && stack.getMaxDamage() > 0) {
                                        dur = ((double) (stack.getMaxDamage() - stack.getItemDamage())
                                            / stack.getMaxDamage()) * 100.0;
                                    } else {
                                        dur = 100.0;
                                    }
                                    dur = Math.max(0.0, Math.min(100.0, dur));
                                    if (dur < minFuelDur) minFuelDur = dur;
                                    if (dur > maxFuelDur) maxFuelDur = dur;
                                    sumFuelDur += dur;
                                    fuelItemCount++;
                                } else if (isItemCoolant(stack) || isItemHeatVent(stack)
                                    || isItemHeatExchanger(stack)
                                    || (stack.getItem() instanceof IReactorComponent)) {
                                        isCoolant = true;
                                        totalCoolantItems += stack.stackSize;
                                        double dur;
                                        if (stack.getItem() instanceof IReactorComponent comp) {
                                            mReactorDummy.setCurrentTile(gt);
                                            int maxH = comp.getMaxHeat(mReactorDummy, stack, gt.getGx(), gt.getGy());
                                            int curH = comp
                                                .getCurrentHeat(mReactorDummy, stack, gt.getGx(), gt.getGy());
                                            dur = maxH > 0 ? ((double) (maxH - curH) / maxH) * 100.0 : 100.0;
                                        } else if (stack.isItemStackDamageable() && stack.getMaxDamage() > 0) {
                                            dur = ((double) (stack.getMaxDamage() - stack.getItemDamage())
                                                / stack.getMaxDamage()) * 100.0;
                                        } else {
                                            dur = 100.0;
                                        }
                                        dur = Math.max(0.0, Math.min(100.0, dur));
                                        if (dur < minCoolDur) minCoolDur = dur;
                                        if (dur > maxCoolDur) maxCoolDur = dur;
                                        sumCoolDur += dur;
                                        coolItemCount++;
                                    }
                            }
                            if (isCoolant) {
                                totalCoolantCoreHatches++;
                            } else {
                                totalFuelCoreHatches++;
                            }
                        } else if (gt.isHatch()) {
                            MTEHatchNuclearHatch hatch = gt.getHatch();
                            boolean isCoolant = hatch.mUsedForCooling;
                            if (hatch.mInputFluid != null && hatch.mInputFluid.amount > 0) {
                                if (isCoolantFluid(hatch.mInputFluid) && !isFluidFuel(hatch.mInputFluid)) {
                                    isCoolant = true;
                                } else if (isFluidFuel(hatch.mInputFluid)) {
                                    isCoolant = false;
                                }
                            }
                            long cap = hatch.mCapacity;
                            long amt = (hatch.mInputFluid != null) ? hatch.mInputFluid.amount : 0;
                            double fill = cap > 0 ? ((double) amt / cap) * 100.0 : 0.0;
                            fill = Math.max(0.0, Math.min(100.0, fill));

                            if (isCoolant) {
                                totalCoolantCoreHatches++;
                                coolantFluidHatchCount++;
                                totalCoolantFluid += amt;
                                totalCoolantCapacity += cap;
                                if (fill < minCoolFill) minCoolFill = fill;
                                if (fill > maxCoolFill) maxCoolFill = fill;
                                sumCoolFill += fill;
                            } else {
                                totalFuelCoreHatches++;
                                fuelFluidHatchCount++;
                                totalFuelFluid += amt;
                                totalFuelCapacity += cap;
                                if (fill < minFuelFill) minFuelFill = fill;
                                if (fill > maxFuelFill) maxFuelFill = fill;
                                sumFuelFill += fill;
                            }
                        }
                    }
                }
            }
        }

        mMinTileTemp = (tileCount > 0) ? minTemp : 0.0;
        mMaxTileTemp = (tileCount > 0) ? maxTemp : 0.0;
        mAvgTileTemp = (tileCount > 0) ? sumTemp / tileCount : 0.0;

        mMinCoolantItemDur = (coolItemCount > 0) ? minCoolDur : 0.0;
        mMaxCoolantItemDur = (coolItemCount > 0) ? maxCoolDur : 0.0;
        mAvgCoolantItemDur = (coolItemCount > 0) ? sumCoolDur / coolItemCount : 0.0;

        mMinFuelItemDur = (fuelItemCount > 0) ? minFuelDur : 0.0;
        mMaxFuelItemDur = (fuelItemCount > 0) ? maxFuelDur : 0.0;
        mAvgFuelItemDur = (fuelItemCount > 0) ? sumFuelDur / fuelItemCount : 0.0;

        mMinCoolantHatchFill = (coolantFluidHatchCount > 0) ? minCoolFill : 0.0;
        mMaxCoolantHatchFill = (coolantFluidHatchCount > 0) ? maxCoolFill : 0.0;
        mAvgCoolantHatchFill = (coolantFluidHatchCount > 0) ? sumCoolFill / coolantFluidHatchCount : 0.0;

        mMinFuelHatchFill = (fuelFluidHatchCount > 0) ? minFuelFill : 0.0;
        mMaxFuelHatchFill = (fuelFluidHatchCount > 0) ? maxFuelFill : 0.0;
        mAvgFuelHatchFill = (fuelFluidHatchCount > 0) ? sumFuelFill / fuelFluidHatchCount : 0.0;

        mTotalFuelItems = totalFuelItems;
        mTotalCoolantItems = totalCoolantItems;

        mTotalCoolantFluid = totalCoolantFluid;
        mTotalCoolantCapacity = totalCoolantCapacity;
        mTotalFuelFluid = totalFuelFluid;
        mTotalFuelCapacity = totalFuelCapacity;

        mCoolantHatchCount = totalCoolantCoreHatches;
        mFuelHatchCount = totalFuelCoreHatches;
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
        if (fuel == null) return null;

        // 1. Check if fuel is ItemRadioactiveCellIC with sDepleted
        if (fuel.getItem() instanceof ItemRadioactiveCellIC icCell && icCell.sDepleted != null) {
            return icCell.sDepleted.copy();
        }

        // 2. Check IC2 native fuel items
        if (fuel.getItem() instanceof ItemReactorUranium ic2Uran) {
            boolean isMox = ic2Uran instanceof ItemReactorMOX;
            int cells = ic2Uran.numberOfCells;
            if (isMox) {
                if (cells >= 4 && Ic2Items.reactorDepletedMOXQuad != null)
                    return Ic2Items.reactorDepletedMOXQuad.copy();
                if (cells >= 2 && Ic2Items.reactorDepletedMOXDual != null)
                    return Ic2Items.reactorDepletedMOXDual.copy();
                if (Ic2Items.reactorDepletedMOXSimple != null) return Ic2Items.reactorDepletedMOXSimple.copy();
                return (cells >= 4) ? ItemList.DepletedRodMOX4.get(1L)
                    : (cells >= 2) ? ItemList.DepletedRodMOX2.get(1L) : ItemList.DepletedRodMOX.get(1L);
            } else {
                if (cells >= 4 && Ic2Items.reactorDepletedUraniumQuad != null)
                    return Ic2Items.reactorDepletedUraniumQuad.copy();
                if (cells >= 2 && Ic2Items.reactorDepletedUraniumDual != null)
                    return Ic2Items.reactorDepletedUraniumDual.copy();
                if (Ic2Items.reactorDepletedUraniumSimple != null) return Ic2Items.reactorDepletedUraniumSimple.copy();
                return (cells >= 4) ? ItemList.DepletedRodUranium4.get(1L)
                    : (cells >= 2) ? ItemList.DepletedRodUranium2.get(1L) : ItemList.DepletedRodUranium.get(1L);
            }
        }

        String name = fuel.getUnlocalizedName();
        if (name == null) return null;
        String lower = name.toLowerCase();

        // 3. The Core (RodNaquadah32)
        if (lower.contains("naquadah32") || lower.contains("thecore")
            || (lower.contains("naquadah") && lower.contains("32"))) {
            return ItemList.DepletedRodNaquadah32.get(1L);
        }

        // 4. Excited variants
        if (lower.contains("exciteduranium") || (lower.contains("excited") && lower.contains("uranium"))) {
            if (lower.contains("quad") || lower.contains("4")) return ItemList.DepletedRodExcitedUranium4.get(1L);
            if (lower.contains("dual") || lower.contains("2")) return ItemList.DepletedRodExcitedUranium2.get(1L);
            return ItemList.DepletedRodExcitedUranium.get(1L);
        }
        if (lower.contains("excitedplutonium") || (lower.contains("excited") && lower.contains("plutonium"))) {
            if (lower.contains("quad") || lower.contains("4")) return ItemList.DepletedRodExcitedPlutonium4.get(1L);
            if (lower.contains("dual") || lower.contains("2")) return ItemList.DepletedRodExcitedPlutonium2.get(1L);
            return ItemList.DepletedRodExcitedPlutonium.get(1L);
        }

        // 5. High density variants
        if (lower.contains("highdensityuranium") || (lower.contains("highdensity") && lower.contains("uranium"))) {
            if (lower.contains("quad") || lower.contains("4")) return ItemList.DepletedRodHighDensityUranium4.get(1L);
            if (lower.contains("dual") || lower.contains("2")) return ItemList.DepletedRodHighDensityUranium2.get(1L);
            return ItemList.DepletedRodHighDensityUranium.get(1L);
        }
        if (lower.contains("highdensityplutonium") || (lower.contains("highdensity") && lower.contains("plutonium"))) {
            if (lower.contains("quad") || lower.contains("4")) return ItemList.DepletedRodHighDensityPlutonium4.get(1L);
            if (lower.contains("dual") || lower.contains("2")) return ItemList.DepletedRodHighDensityPlutonium2.get(1L);
            return ItemList.DepletedRodHighDensityPlutonium.get(1L);
        }

        // 6. Naquadria & Tiberium
        if (lower.contains("naquadria")) {
            if (lower.contains("quad") || lower.contains("4")) return ItemList.DepletedRodNaquadria4.get(1L);
            if (lower.contains("dual") || lower.contains("2")) return ItemList.DepletedRodNaquadria2.get(1L);
            return ItemList.DepletedRodNaquadria.get(1L);
        }
        if (lower.contains("tiberium")) {
            if (lower.contains("quad") || lower.contains("4")) return ItemList.DepletedRodTiberium4.get(1L);
            if (lower.contains("dual") || lower.contains("2")) return ItemList.DepletedRodTiberium2.get(1L);
            return ItemList.DepletedRodTiberium.get(1L);
        }

        // 7. Standard fuels: Naquadah, Thorium, MOX, Uranium
        if (lower.contains("naquadah")) {
            if (lower.contains("quad") || lower.contains("4")) return ItemList.DepletedRodNaquadah4.get(1L);
            if (lower.contains("dual") || lower.contains("2")) return ItemList.DepletedRodNaquadah2.get(1L);
            return ItemList.DepletedRodNaquadah.get(1L);
        } else if (lower.contains("mox")) {
            if (lower.contains("quad") || lower.contains("4")) return ItemList.DepletedRodMOX4.get(1L);
            if (lower.contains("dual") || lower.contains("2")) return ItemList.DepletedRodMOX2.get(1L);
            return ItemList.DepletedRodMOX.get(1L);
        } else if (lower.contains("thorium")) {
            if (lower.contains("quad") || lower.contains("4")) return ItemList.DepletedRodThorium4.get(1L);
            if (lower.contains("dual") || lower.contains("2")) return ItemList.DepletedRodThorium2.get(1L);
            return ItemList.DepletedRodThorium.get(1L);
        } else if (lower.contains("uranium")) {
            if (lower.contains("quad") || lower.contains("4")) return ItemList.DepletedRodUranium4.get(1L);
            if (lower.contains("dual") || lower.contains("2")) return ItemList.DepletedRodUranium2.get(1L);
            return ItemList.DepletedRodUranium.get(1L);
        }
        return null;
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

    public double getTileTemperature(NuclearGridTile tile) {
        if (tile.isBus()) return tile.getBus().mTemperature;
        if (tile.isHatch()) return tile.getHatch().mTemperature;
        if (tile.isControlRod()) return tile.getControlRod().mTemperature;
        if (tile.isHighPressureHatch()) return tile.getHighPressureHatch()
            .getTemperature();
        return getAmbientTemperature();
    }

    public void setTileTemperature(NuclearGridTile tile, double temp) {
        double ambient = getAmbientTemperature();
        if (tile.isBus()) {
            tile.getBus().mTemperature = Math.max(ambient, temp);
        } else if (tile.isHatch()) {
            double minTemp = tile.getHatch()
                .hasWaterCoolant() ? Math.max(0.0, ambient) : ambient;
            tile.getHatch().mTemperature = Math.max(minTemp, temp);
        } else if (tile.isControlRod()) {
            tile.getControlRod().mTemperature = Math.max(ambient, temp);
        } else if (tile.isHighPressureHatch()) {
            tile.getHighPressureHatch()
                .setTemperature(Math.max(ambient, temp));
        }
    }

    public void addTileHeat(NuclearGridTile tile, double heatEU) {
        if (tile.isBus()) {
            tile.getBus().mHeatEU += heatEU;
            tile.getBus().mTemperature += heatEU / NuclearSimulationEngine.EU_PER_DEGREE;
        } else if (tile.isHatch()) {
            tile.getHatch().mHeatEU += heatEU;
            tile.getHatch().mTemperature += heatEU / NuclearSimulationEngine.EU_PER_DEGREE;
        } else if (tile.isControlRod()) {
            tile.getControlRod().mHeatEU += heatEU;
            tile.getControlRod().mTemperature += heatEU / NuclearSimulationEngine.EU_PER_DEGREE;
        } else if (tile.isHighPressureHatch()) {
            tile.getHighPressureHatch()
                .addHeat(heatEU);
        }
    }

    public double getTileHeatTransferCoeff(NuclearGridTile tile) {
        if (tile.isBus()) {
            ItemStack stack = tile.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            if (stack == null) return 0.02;
            if (isItemInsulator(stack)) return 0.01;
            if (isItemRadiovoltaic(stack)) return 0.10;
            String name = stack.getUnlocalizedName()
                .toLowerCase();
            if (name.contains("coolant") || name.contains("vent")
                || name.contains("switch")
                || name.contains("heatexchanger")) return 0.40;
            if (name.contains("reflector")) return 0.15;
            if (name.contains("fuel") || name.contains("uranium") || name.contains("mox") || name.contains("thorium"))
                return 0.05;
            return 0.03;
        } else if (tile.isHatch()) {
            FluidStack fluid = tile.getHatch().mInputFluid;
            if (fluid == null) return 0.05;
            String name = fluid.getFluid()
                .getName()
                .toLowerCase();
            if (name.contains("water")) return 0.25;
            if (name.contains("coolant")) return 0.50;
            if (name.contains("sodium") || name.contains("lead")) return 0.70;
            return 0.15;
        } else if (tile.isControlRod()) {
            return tile.getControlRod()
                .getHeatTransferCoeff();
        } else if (tile.isHighPressureHatch()) {
            return tile.getHighPressureHatch()
                .getHeatTransferCoeff();
        }
        return 0.05;
    }

    public boolean isTileFuel(NuclearGridTile tile) {
        if (tile.isBus()) {
            return isItemFuel(tile.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT]);
        } else if (tile.isHatch()) {
            return isFluidFuel(tile.getHatch().mInputFluid);
        }
        return false;
    }

    public NuclearFuelType getTileFuelType(NuclearGridTile tile) {
        if (tile.isBus()) {
            ItemStack stack = tile.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            if (stack != null) {
                return NuclearFuelType.fromName(stack.getUnlocalizedName());
            }
        } else if (tile.isHatch()) {
            FluidStack fluid = tile.getHatch().mInputFluid;
            if (fluid != null && fluid.getFluid() != null) {
                return NuclearFuelType.fromName(
                    fluid.getFluid()
                        .getName());
            }
        }
        return null;
    }

    public int generateTileNeutrons(NuclearGridTile tile, double efficiency) {
        NuclearFuelType fuel = getTileFuelType(tile);
        double baseFissMult = (fuel != null) ? fuel.baseThermalFissionMultiplier : 1.0;
        double effectiveFissMult = baseFissMult * NuclearSimulationEngine.globalThermalFissionMultiplier;

        if (tile.isBus()) {
            MTEHatchNuclearBus bus = tile.getBus();
            ItemStack stack = bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            if (!isItemFuel(stack)) {
                bus.mLastNeutronsGenerated = 0;
                return 0;
            }
            int baseNeutrons = 4;
            if (stack.getItem() instanceof ItemRadioactiveCellIC icCell) {
                baseNeutrons = 4 * icCell.numberOfCells;
                if (icCell.sMox) baseNeutrons *= 2;
            } else if (stack.getItem() instanceof ItemReactorUranium ic2Uran) {
                baseNeutrons = 4 * ic2Uran.numberOfCells;
                if (ic2Uran instanceof ItemReactorMOX) baseNeutrons *= 2;
            } else {
                String name = stack.getUnlocalizedName();
                if (name != null) {
                    String lower = name.toLowerCase();
                    if (lower.contains("naquadah32") || lower.contains("thecore")
                        || (lower.contains("naquadah") && lower.contains("32"))) {
                        baseNeutrons = 128;
                    } else if (lower.contains("quad") || lower.contains("4")) {
                        baseNeutrons = 16;
                    } else if (lower.contains("dual") || lower.contains("2")) {
                        baseNeutrons = 8;
                    }
                    if (lower.contains("mox")) baseNeutrons *= 2;
                }
            }
            String name = stack.getUnlocalizedName();
            if (name != null) {
                String lower = name.toLowerCase();
                if (lower.contains("glowstone") || lower.contains("lithium")) baseNeutrons = 1;
                if (lower.contains("thorium")) baseNeutrons = Math.max(1, baseNeutrons / 2);
                if (lower.contains("naquadah")) baseNeutrons *= 4;
                if (lower.contains("naquadria")) baseNeutrons *= 4;
                if (lower.contains("tiberium")) baseNeutrons *= 2;
            }

            int chainNeutrons = (int) Math.round(bus.mLastThermalAbsorbed * effectiveFissMult);
            int produced = (int) Math.round((baseNeutrons + chainNeutrons) * efficiency);
            bus.mLastNeutronsGenerated = produced;
            return produced;
        } else if (tile.isHatch()) {
            MTEHatchNuclearHatch hatch = tile.getHatch();
            FluidStack fluid = hatch.mInputFluid;
            if (!isFluidFuel(fluid) || fluid == null || fluid.amount <= 0) {
                hatch.mLastNeutronsGenerated = 0;
                return 0;
            }
            String name = fluid.getFluid()
                .getName()
                .toLowerCase();
            int baseNeutrons = 8;
            if (name.contains("thorium")) {
                baseNeutrons = name.contains("excited") ? 8 : 4;
            } else if (name.contains("uranium")) {
                baseNeutrons = name.contains("excited") ? 16 : 8;
            } else if (name.contains("plutonium")) {
                baseNeutrons = name.contains("excited") ? 32 : 16;
            } else if (name.contains("uraniumhexafluoride")) {
                baseNeutrons = 12;
            }

            int chainNeutrons = (int) Math.round(hatch.mLastThermalAbsorbed * effectiveFissMult);
            int produced = (int) Math.round((baseNeutrons + chainNeutrons) * efficiency);
            hatch.mLastNeutronsGenerated = produced;
            return produced;
        }
        return 0;
    }

    public int getTileNeutronEmissionCount(NuclearGridTile tile) {
        if (tile.isHatch()) {
            return 4;
        }
        if (tile.isBus()) {
            ItemStack stack = tile.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            if (stack == null) return 1;
            if (stack.getItem() instanceof ItemRadioactiveCellIC icCell) {
                return Math.max(1, icCell.numberOfCells);
            } else if (stack.getItem() instanceof ItemReactorUranium ic2Uran) {
                return Math.max(1, ic2Uran.numberOfCells);
            } else {
                String name = stack.getUnlocalizedName();
                if (name != null) {
                    String lower = name.toLowerCase();
                    if (lower.contains("naquadah32") || lower.contains("thecore")
                        || (lower.contains("naquadah") && lower.contains("32"))) {
                        return 16;
                    } else if (lower.contains("quad") || lower.contains("4")) {
                        return 4;
                    } else if (lower.contains("dual") || lower.contains("2")) {
                        return 2;
                    }
                }
            }
        }
        return 1;
    }

    public double getTileAbsorptionProbability(NuclearGridTile tile, NeutronType type) {
        if (tile.isBus()) {
            ItemStack stack = tile.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            if (stack == null) return 0.01;
            if (isNaquariteInsulatorFoil(stack)) return 1.0;
            if (isItemInsulator(stack)) return 0.01;
            if (isItemRadiovoltaic(stack)) return 1.0;
            String name = stack.getUnlocalizedName()
                .toLowerCase();
            if (name.contains("graphite") || name.contains("carbon") || name.contains("moderator")) {
                return (type == NeutronType.THERMAL) ? 0.009 : 0.002;
            }
            if (name.contains("reflector")) return (type == NeutronType.THERMAL) ? 0.02 : 0.01;
            if (name.contains("boron") || name.contains("cadmium") || name.contains("control")) {
                return (type == NeutronType.THERMAL) ? 0.95 : 0.85;
            }
            if (isItemFuel(stack)) {
                return (type == NeutronType.THERMAL) ? 0.80 : 0.25;
            }
            if (name.contains("coolant")) return 0.05;
            return 0.02;
        } else if (tile.isHatch()) {
            FluidStack fluid = tile.getHatch().mInputFluid;
            if (fluid == null) return 0.01;
            String name = fluid.getFluid()
                .getName()
                .toLowerCase();
            if (name.contains("heavywater")) return (type == NeutronType.THERMAL) ? 0.01 : 0.005;
            if (name.contains("distilledwater")) return (type == NeutronType.THERMAL) ? 0.10 : 0.05;
            if (name.contains("coolant")) return (type == NeutronType.THERMAL) ? 0.12 : 0.03;
            if (name.contains("boron")) return 0.95;
            if (isFluidFuel(fluid)) return (type == NeutronType.THERMAL) ? 0.85 : 0.25;
            return 0.05;
        } else if (tile.isControlRod()) {
            return tile.getControlRod()
                .getAbsorptionProbability(type);
        } else if (tile.isHighPressureHatch()) {
            return tile.getHighPressureHatch()
                .getAbsorptionProbability(type);
        }
        return 0.01;
    }

    public double getTileScatteringProbability(NuclearGridTile tile, NeutronType type) {
        if (tile.isBus()) {
            ItemStack stack = tile.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            if (stack == null) return 0.02;
            if (isNaquariteInsulatorFoil(stack)) return 0.0;
            if (isItemInsulator(stack)) return 0.05;
            if (isItemRadiovoltaic(stack)) return 0.0;
            String name = stack.getUnlocalizedName()
                .toLowerCase();
            if (name.contains("graphite") || name.contains("carbon") || name.contains("moderator")) {
                return (type == NeutronType.THERMAL) ? 0.621 : 0.93;
            }
            if (name.contains("reflector")) return (type == NeutronType.THERMAL) ? 0.98 : 0.95;
            if (name.contains("boron") || name.contains("cadmium") || name.contains("control")) {
                return (type == NeutronType.THERMAL) ? 0.05 : 0.10;
            }
            if (name.contains("coolant")) return 0.45;
            if (isItemFuel(stack)) return (type == NeutronType.THERMAL) ? 0.10 : 0.15;
            return 0.05;
        } else if (tile.isHatch()) {
            FluidStack fluid = tile.getHatch().mInputFluid;
            if (fluid == null) return 0.02;
            String name = fluid.getFluid()
                .getName()
                .toLowerCase();
            if (name.contains("heavywater")) return 0.85;
            if (name.contains("distilledwater")) return 0.70;
            if (name.contains("coolant")) return 0.45;
            if (name.contains("sodium")) return 0.20;
            return 0.10;
        } else if (tile.isControlRod()) {
            return tile.getControlRod()
                .getScatteringProbability(type);
        } else if (tile.isHighPressureHatch()) {
            return tile.getHighPressureHatch()
                .getScatteringProbability(type);
        }
        return 0.02;
    }

    public double getTileModerationProbability(NuclearGridTile tile) {
        if (tile.isBus()) {
            ItemStack stack = tile.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            if (isItemRadiovoltaic(stack)) return 0.0;
            if (stack == null) return 0.05;
            String name = stack.getUnlocalizedName()
                .toLowerCase();
            if (name.contains("graphite") || name.contains("carbon") || name.contains("moderator")) return 0.50;
            if (name.contains("reflector")) return 0.20;
            if (name.contains("coolant")) return 0.40;
            if (isItemFuel(stack)) return 0.10;
            if (name.contains("boron") || name.contains("cadmium") || name.contains("control")) return 0.05;
            return 0.05;
        } else if (tile.isHatch()) {
            FluidStack fluid = tile.getHatch().mInputFluid;
            if (fluid == null) return 0.05;
            String name = fluid.getFluid()
                .getName()
                .toLowerCase();
            if (name.contains("heavywater")) return 0.90;
            if (name.contains("distilledwater")) return 0.80;
            if (name.contains("coolant")) return 0.40;
            if (name.contains("sodium")) return 0.05;
            return 0.20;
        } else if (tile.isControlRod()) {
            return 0.01;
        } else if (tile.isHighPressureHatch()) {
            return tile.getHighPressureHatch()
                .getModerationProbability();
        }
        return 0.05;
    }

    public void onTileNeutronAbsorbed(NuclearGridTile tile, NeutronType type, int count) {
        if (tile.isBus()) {
            MTEHatchNuclearBus bus = tile.getBus();
            if (type == NeutronType.FAST) bus.mFastAbsorbed += count;
            else bus.mThermalAbsorbed += count;
        } else if (tile.isHatch()) {
            MTEHatchNuclearHatch hatch = tile.getHatch();
            if (type == NeutronType.FAST) hatch.mFastAbsorbed += count;
            else hatch.mThermalAbsorbed += count;

            if (type == NeutronType.FAST && hatch.mInputFluid != null && hatch.mInputFluid.amount > 0) {
                String name = hatch.mInputFluid.getFluid()
                    .getName()
                    .toLowerCase();
                boolean isHP = name.contains("highpressure");
                int chance = isHP ? Math.min(100, count * 10) : Math.min(100, count * 5);
                int yield = isHP ? 2 : 1;

                if (name.contains("distilledwater")) {
                    if (getRandomNumber(100) < chance) {
                        hatch.mInputFluid.amount -= 1;
                        if (hatch.mInputFluid.amount <= 0) hatch.mInputFluid = null;
                        mCycleTransmutationLoss += 1;
                        Fluid deut = FluidRegistry.getFluid("deuterium");
                        if (deut == null) deut = FluidRegistry.getFluid("fluid.deuterium");
                        if (deut != null) {
                            this.addOutputPartial(new FluidStack(deut, yield));
                            mCycleTransmutationByproducts += yield;
                        }
                        hatch.markTileDirty();
                    }
                } else if (name.contains("heavywater")) {
                    if (getRandomNumber(100) < chance) {
                        hatch.mInputFluid.amount -= 1;
                        if (hatch.mInputFluid.amount <= 0) hatch.mInputFluid = null;
                        mCycleTransmutationLoss += 1;
                        Fluid trit = FluidRegistry.getFluid("tritium");
                        if (trit == null) trit = FluidRegistry.getFluid("fluid.tritium");
                        if (trit != null) {
                            this.addOutputPartial(new FluidStack(trit, yield));
                            mCycleTransmutationByproducts += yield;
                        }
                        hatch.markTileDirty();
                    }
                }
            }
        } else if (tile.isControlRod()) {
            MTEHatchNuclearControlRod rod = tile.getControlRod();
            if (type == NeutronType.FAST) rod.mFastAbsorbed += count;
            else rod.mThermalAbsorbed += count;
        } else if (tile.isHighPressureHatch()) {
            tile.getHighPressureHatch()
                .onNeutronAbsorbed(type, count);
        }
    }

    public void onTileNeutronScattered(NuclearGridTile tile, NeutronType type, int count) {
        if (tile.isBus()) {
            MTEHatchNuclearBus bus = tile.getBus();
            ItemStack stack = bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            if (stack != null && stack.isItemStackDamageable()) {
                String name = stack.getUnlocalizedName()
                    .toLowerCase();
                if (name.contains("reflector")) {
                    if (getRandomNumber(20) == 0) {
                        damageItemComponent(bus, 1);
                    }
                }
            }
        } else if (tile.isHighPressureHatch()) {
            tile.getHighPressureHatch()
                .onNeutronScattered(type, count);
        }
    }

    public void addTileNeutronFlux(NuclearGridTile tile, NeutronType type, int count) {
        if (tile.isBus()) {
            if (type == NeutronType.FAST) tile.getBus().mFastFlux += count;
            else tile.getBus().mThermalFlux += count;
        } else if (tile.isHatch()) {
            if (type == NeutronType.FAST) tile.getHatch().mFastFlux += count;
            else tile.getHatch().mThermalFlux += count;
        } else if (tile.isControlRod()) {
            if (type == NeutronType.FAST) tile.getControlRod().mFastFlux += count;
            else tile.getControlRod().mThermalFlux += count;
        } else if (tile.isHighPressureHatch()) {
            tile.getHighPressureHatch()
                .addNeutronFlux(type, count);
        }
    }

    public void processTileNuclearTick(NuclearGridTile tile, double efficiency) {
        if (tile.isBus()) {
            processBusNuclearTick(tile.getBus(), tile, efficiency);
        } else if (tile.isHatch()) {
            processHatchNuclearTick(tile.getHatch(), tile, efficiency);
        } else if (tile.isControlRod()) {
            processControlRodNuclearTick(tile.getControlRod(), tile, efficiency);
        } else if (tile.isHighPressureHatch()) {
            tile.getHighPressureHatch()
                .nuclearTick(efficiency);
        }
    }

    public void processControlRodNuclearTick(MTEHatchNuclearControlRod rod, NuclearGridTile tile, double efficiency) {
        rod.mLastFastFlux = rod.mFastFlux;
        rod.mLastThermalFlux = rod.mThermalFlux;
        rod.mLastFastAbsorbed = rod.mFastAbsorbed;
        rod.mLastThermalAbsorbed = rod.mThermalAbsorbed;
        rod.mFastFlux = 0;
        rod.mThermalFlux = 0;
        rod.mFastAbsorbed = 0;
        rod.mThermalAbsorbed = 0;
    }

    public void processBusNuclearTick(MTEHatchNuclearBus bus, NuclearGridTile tile, double efficiency) {
        ItemStack stack = bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT];
        if (stack == null) {
            bus.mLastFastFlux = bus.mFastFlux;
            bus.mLastThermalFlux = bus.mThermalFlux;
            bus.mLastFastAbsorbed = bus.mFastAbsorbed;
            bus.mLastThermalAbsorbed = bus.mThermalAbsorbed;
            bus.mDirectEUProduced = 0;
            bus.mFastFlux = 0;
            bus.mThermalFlux = 0;
            bus.mFastAbsorbed = 0;
            bus.mThermalAbsorbed = 0;
            return;
        }

        // 1. FUEL DEPLETION (Driven by 3 physical processes: emitting fast neutrons, absorbing any neutron, and temperature above ambient)
        if (isItemFuel(stack)) {
            bus.mDirectEUProduced = 0;
            double ambient = bus.getAmbientTemperature();
            NuclearFuelType fuelType = NuclearFuelType.fromName(stack.getUnlocalizedName());
            double tempDmg = (fuelType != null)
                ? fuelType.calculateTemperatureDamage(bus.mTemperature, ambient)
                : (bus.mTemperature > ambient ? (bus.mTemperature - ambient) / 100.0 : 0.0);

            double totalDmg = Math.max(0.0, bus.mLastNeutronsGenerated * 0.25)
                + (bus.mFastAbsorbed + bus.mThermalAbsorbed) * 1.0
                + tempDmg;
            int damage = (int) totalDmg;
            double remainder = totalDmg - damage;
            if (remainder > 0.0 && Math.random() < remainder) {
                damage++;
            }
            if (stack.getItem() instanceof ItemRadioactiveCell radCell) {
                radCell.damageItemStack(stack, damage);
                if (radCell.getDamageOfStack(stack) >= radCell.getMaxDamageEx()) {
                    mCycleZeroedFuelItems++;
                    ItemStack depleted = null;
                    if (radCell instanceof ItemRadioactiveCellIC icCell && icCell.sDepleted != null) {
                        depleted = icCell.sDepleted.copy();
                    } else {
                        depleted = getItemDepletedForm(stack);
                    }
                    bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = null;
                    if (depleted != null) {
                        this.addOutputPartial(depleted);
                    }
                }
                bus.markTileDirty();
            } else if (stack.getItem() instanceof ItemReactorUranium) {
                int curDmg = stack.getItemDamage();
                int maxDmg = stack.getMaxDamage();
                int newDmg = curDmg + damage;
                if (newDmg >= maxDmg) {
                    mCycleZeroedFuelItems++;
                    ItemStack depleted = getItemDepletedForm(stack);
                    bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = null;
                    if (depleted != null) {
                        this.addOutputPartial(depleted);
                    }
                } else {
                    stack.setItemDamage(newDmg);
                }
                bus.markTileDirty();
            } else {
                damageItemComponent(bus, damage);
            }
        }
        // 2. RADIOVOLTAIC DIRECT EU GENERATION
        else if (isItemRadiovoltaic(stack)) {
            int tier = getRadiovoltaicTier(stack);
            long maxEU = (tier >= 2) ? 4096 : 1024;
            double weightedFlux = bus.mFastAbsorbed * 4.0 + bus.mThermalAbsorbed * 1.0;
            double satFlux = 60.0;
            double effFactor = Math.max(0.0, Math.min(1.0, efficiency));
            long genEU = (long) Math.round(maxEU * Math.tanh(weightedFlux / satFlux) * effFactor);
            bus.mDirectEUProduced = genEU;
            double totalEnergy = weightedFlux * 20.0;
            double excessHeat = Math.max(0.0, totalEnergy - genEU);
            if (excessHeat > 0.0) {
                addTileHeat(tile, excessHeat);
            }
        }
        // 3. IC2 HEAT VENTS & HEAT EXCHANGERS (Passive cooling and adjacent balancing)
        else if (isItemHeatVent(stack) || isItemHeatExchanger(stack)) {
            bus.mDirectEUProduced = 0;
            if (stack.getItem() instanceof IReactorComponent comp) {
                mReactorDummy.setCurrentTile(tile);
                int gx = tile.getGx();
                int gy = tile.getGy();

                if (comp.canStoreHeat(mReactorDummy, stack, gx, gy)) {
                    int maxHeat = comp.getMaxHeat(mReactorDummy, stack, gx, gy);
                    int curHeat = comp.getCurrentHeat(mReactorDummy, stack, gx, gy);
                    double ambient = getAmbientTemperature();
                    if (maxHeat > 0 && bus.mTemperature > ambient) {
                        int maxTransferPerTick = Math.max(20, maxHeat / 50);
                        double heatAvailable = (bus.mTemperature - ambient) * NuclearSimulationEngine.EU_PER_DEGREE;
                        double effFactor = Math.max(0.0, Math.min(1.0, efficiency));
                        int heatToTake = (int) Math
                            .round(Math.min(heatAvailable / 25.0, (double) maxTransferPerTick) * effFactor);
                        int room = maxHeat - curHeat;
                        heatToTake = Math.min(heatToTake, room);

                        if (heatToTake > 0) {
                            comp.alterHeat(mReactorDummy, stack, gx, gy, heatToTake);
                            double heatConsumed = heatToTake * 25.0;
                            bus.mTemperature -= (heatConsumed / NuclearSimulationEngine.EU_PER_DEGREE);
                            bus.markTileDirty();
                        }
                    }
                }

                // Process IC2 native component chamber tick (cooling self or balancing with adjacent components)
                comp.processChamber(mReactorDummy, stack, gx, gy, true);
                bus.markTileDirty();
            }
        }
        // 4. COOLANT CELL HEAT ABSORPTION (Capacity-based scaling via IReactorComponent)
        else if (stack.getItem() instanceof IReactorComponent comp) {
            mReactorDummy.setCurrentTile(tile);
            if (comp.canStoreHeat(mReactorDummy, stack, 0, 0)) {
                bus.mDirectEUProduced = 0;
                int maxHeat = comp.getMaxHeat(mReactorDummy, stack, 0, 0);
                int curHeat = comp.getCurrentHeat(mReactorDummy, stack, 0, 0);
                double ambient = getAmbientTemperature();
                if (maxHeat > 0 && bus.mTemperature > ambient) {
                    int maxTransferPerTick = Math.max(1, maxHeat / 100);
                    double heatAvailable = (bus.mTemperature - ambient) * NuclearSimulationEngine.EU_PER_DEGREE;
                    double effFactor = Math.max(0.0, Math.min(1.0, efficiency));
                    int heatToTake = (int) Math
                        .round(Math.min(heatAvailable / 25.0, (double) maxTransferPerTick) * effFactor);
                    int room = maxHeat - curHeat;
                    heatToTake = Math.min(heatToTake, room);

                    if (heatToTake > 0) {
                        comp.alterHeat(mReactorDummy, stack, 0, 0, heatToTake);
                        double heatConsumed = heatToTake * 25.0;
                        bus.mTemperature -= (heatConsumed / NuclearSimulationEngine.EU_PER_DEGREE);
                        bus.markTileDirty();
                    }
                }

                // Eject hot/full coolant cells to output buses for freezer re-cooling
                if (comp.getCurrentHeat(mReactorDummy, stack, 0, 0) >= maxHeat) {
                    mCycleZeroedCoolantItems++;
                    ItemStack fullCell = stack.copy();
                    bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = null;
                    this.addOutputPartial(fullCell);
                    bus.markTileDirty();
                }
            } else {
                bus.mDirectEUProduced = 0;
            }
        }
        // 4. GENERIC COOLANT/VENT FALLBACK
        else if (stack.getUnlocalizedName()
            .toLowerCase()
            .contains("coolant")) {
                bus.mDirectEUProduced = 0;
                double ambient = getAmbientTemperature();
                if (bus.mTemperature > ambient) {
                    double heatToAbsorb = Math.min(bus.mTemperature - ambient, 100.0)
                        * NuclearSimulationEngine.EU_PER_DEGREE;
                    if (heatToAbsorb > 0) {
                        bus.mTemperature -= (heatToAbsorb / NuclearSimulationEngine.EU_PER_DEGREE);
                        int cellDamage = Math.max(1, (int) (heatToAbsorb / 50.0));
                        damageItemComponent(bus, cellDamage);
                    }
                }
            }
        // 5. EASTER EGG: MOLTEN CHEESE EXTRACTION ABOVE 65°C
        else if (bus.mTemperature > 65.0) {
            bus.mDirectEUProduced = 0;
            if (getBaseMetaTileEntity() != null) {
                long aTick = getBaseMetaTileEntity().getTimer();
                if (aTick != bus.mLastCheeseTick) {
                    bus.mLastCheeseTick = aTick;
                    this.processCheeseExtraction(bus);
                }
            } else {
                this.processCheeseExtraction(bus);
            }
        } else {
            bus.mDirectEUProduced = 0;
        }

        // Reset transient flux counters for next tick's display
        bus.mLastFastFlux = bus.mFastFlux;
        bus.mLastThermalFlux = bus.mThermalFlux;
        bus.mLastFastAbsorbed = bus.mFastAbsorbed;
        bus.mLastThermalAbsorbed = bus.mThermalAbsorbed;
        bus.mFastFlux = 0;
        bus.mThermalFlux = 0;
        bus.mFastAbsorbed = 0;
        bus.mThermalAbsorbed = 0;
    }

    public boolean processCheeseExtraction(MTEHatchNuclearBus bus) {
        if (bus.mTemperature <= 65.0) return false;
        ItemStack stack = bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT];
        if (stack == null || stack.stackSize <= 0) return false;

        GTRecipe recipe = bus.findCheeseExtractionRecipe(stack);
        if (recipe == null) return false;

        FluidStack cheeseOutput = null;
        if (recipe.mFluidOutputs != null) {
            for (FluidStack out : recipe.mFluidOutputs) {
                if (out != null && MTEHatchNuclearBus.isMoltenCheese(out)) {
                    cheeseOutput = out.copy();
                    break;
                }
            }
        }
        if (cheeseOutput == null || cheeseOutput.amount <= 0) return false;

        long totalEU = (long) recipe.mDuration * recipe.mEUt;
        double heatAbsorbed = Math.max(1.0, (double) totalEU);
        double tempDrop = heatAbsorbed / NuclearSimulationEngine.EU_PER_DEGREE;
        bus.mTemperature = Math.max(getAmbientTemperature(), bus.mTemperature - tempDrop);

        int consumeCount = 1;
        if (recipe.mInputs != null && recipe.mInputs.length > 0 && recipe.mInputs[0] != null) {
            consumeCount = Math.max(1, recipe.mInputs[0].stackSize);
        }
        stack.stackSize -= consumeCount;
        if (stack.stackSize <= 0) {
            bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = null;
        }

        this.addOutputPartial(cheeseOutput);

        if (recipe.mOutputs != null) {
            for (ItemStack out : recipe.mOutputs) {
                if (out != null) {
                    this.addOutputPartial(out.copy());
                }
            }
        }

        bus.markTileDirty();
        return true;
    }

    public void processHatchNuclearTick(MTEHatchNuclearHatch hatch, NuclearGridTile tile, double efficiency) {
        hatch.mLastFastFlux = hatch.mFastFlux;
        hatch.mLastThermalFlux = hatch.mThermalFlux;
        hatch.mLastFastAbsorbed = hatch.mFastAbsorbed;
        hatch.mLastThermalAbsorbed = hatch.mThermalAbsorbed;
        hatch.mFastFlux = 0;
        hatch.mThermalFlux = 0;
        hatch.mFastAbsorbed = 0;
        hatch.mThermalAbsorbed = 0;

        if (hatch.mInputFluid == null || hatch.mInputFluid.amount <= 0) return;

        // 1. LIQUID NUCLEAR FUEL PROCESSING (Turns into spent liquid fuel, emits neutrons)
        if (isFluidFuel(hatch.mInputFluid)) {
            FluidStack fluid = hatch.mInputFluid;
            Fluid spentFluid = getSpentFluid(fluid);
            double ambient = hatch.getAmbientTemperature();
            NuclearFuelType fuelType = (fluid != null && fluid.getFluid() != null)
                ? NuclearFuelType.fromName(fluid.getFluid().getName())
                : null;
            double tempDmg = (fuelType != null)
                ? fuelType.calculateTemperatureDamage(hatch.mTemperature, ambient)
                : (hatch.mTemperature > ambient ? (hatch.mTemperature - ambient) / 100.0 : 0.0);

            double totalBurn = Math.max(0.0, hatch.mLastNeutronsGenerated * 0.25)
                + (hatch.mFastAbsorbed + hatch.mThermalAbsorbed) * 1.0
                + tempDmg;
            int burn = (int) totalBurn;
            double rem = totalBurn - burn;
            if (rem > 0.0 && Math.random() < rem) {
                burn++;
            }
            burn = Math.max(1, burn);
            int toConsume = Math.max(1, Math.min(fluid.amount, burn));

            if (toConsume > 0) {
                fluid.amount -= toConsume;
                mCycleDepletedLiquidFuel += toConsume;
                if (fluid.amount <= 0) hatch.mInputFluid = null;
                if (spentFluid != null) {
                    this.addOutputPartial(new FluidStack(spentFluid, toConsume));
                }
                hatch.markTileDirty();
            }
            return;
        }

        String name = hatch.mInputFluid.getFluid()
            .getName()
            .toLowerCase();
        if (name.equals("water")) return; // Regular water is completely disallowed

        int reqTier = MTEHatchNuclearHatch.getRequiredFluidTier(name);
        if (mPipeTier >= 0 && mPipeTier < reqTier) {
            return;
        }

        double minOperatingTemp = NuclearSimulationEngine.getCoolantSinkTemperature(name, getAmbientTemperature());
        double heatPerMB = NuclearSimulationEngine.coolingHeatPerLiter;
        int steamRatio = 1;
        String outputFluidName = "steam";

        if (name.contains("coolant") && !name.contains("hot")) {
            minOperatingTemp = NuclearSimulationEngine.getCoolantSinkTemperature(name, getAmbientTemperature());
            heatPerMB = NuclearSimulationEngine.ic2CoolantHeatPerLiter;
            steamRatio = 1;
            outputFluidName = "ic2hotcoolant";
        } else if (name.contains("heavywater") && !name.contains("steam")) {
            minOperatingTemp = NuclearSimulationEngine.getCoolantSinkTemperature(name, getAmbientTemperature());
            heatPerMB = NuclearSimulationEngine.coolingHeatPerLiter;
            steamRatio = 160;
            outputFluidName = "heavywatersteam";
        } else if (name.contains("distilledwater")) {
            minOperatingTemp = NuclearSimulationEngine.getCoolantSinkTemperature(name, getAmbientTemperature());
            heatPerMB = NuclearSimulationEngine.coolingHeatPerLiter;
            steamRatio = 160;
            outputFluidName = "steam";
        } else {
            return;
        }

        hatch.mLastProducedAmount = 0;
        hatch.mLastProducedFluidName = "";
        if (hatch.mTemperature > minOperatingTemp) {
            double effFactor = Math.max(0.0, Math.min(1.0, efficiency));
            double qMax = NuclearSimulationEngine.calculateConductiveHeatTransfer(
                hatch.mTemperature, minOperatingTemp, hatch.mTier, effFactor);
            int desiredTurnover = (heatPerMB > 0) ? (int) Math.round(qMax / heatPerMB) : 0;
            int fluidToProcess = Math.min(hatch.mInputFluid.amount, desiredTurnover);

            if (fluidToProcess > 0) {
                int outAmount = fluidToProcess * steamRatio;
                hatch.mInputFluid.amount -= fluidToProcess;
                mCycleConsumedCoolant += fluidToProcess;
                mCycleProducedHotCoolant += outAmount;
                if (hatch.mInputFluid.amount <= 0) {
                    hatch.mInputFluid = null;
                    hatch.mWasDry = true;
                }

                Fluid outFluid = FluidRegistry.getFluid(outputFluidName);
                if (outFluid == null && outputFluidName.startsWith("fluid.")) {
                    outFluid = FluidRegistry.getFluid(outputFluidName.substring(6));
                }
                if (outFluid == null && !outputFluidName.startsWith("fluid.")) {
                    outFluid = FluidRegistry.getFluid("fluid." + outputFluidName);
                }
                if (outFluid == null) {
                    outFluid = FluidRegistry.getFluid("steam");
                }
                if (outFluid != null && outAmount > 0) {
                    this.addOutputPartial(new FluidStack(outFluid, outAmount));
                }

                hatch.mLastProducedAmount = outAmount;
                hatch.mLastProducedFluidName = outputFluidName;
                double heatConsumed = fluidToProcess * heatPerMB;
                hatch.mTemperature = Math
                    .max(minOperatingTemp, hatch.mTemperature - (heatConsumed / NuclearSimulationEngine.EU_PER_DEGREE));
                hatch.markTileDirty();
            }
        }
    }

    public static class NuclearGridTile implements INuclearTile {

        private final MTENuclearReactor reactor;
        private final MTEHatchNuclearBus bus;
        private final MTEHatchNuclearHatch hatch;
        private final MTEHatchNuclearControlRod controlRod;
        private final MTEHatchNuclearHighPressure highPressureHatch;
        private final int gx;
        private final int gy;

        public NuclearGridTile(MTENuclearReactor reactor, MTEHatchNuclearBus bus, int gx, int gy) {
            this.reactor = reactor;
            this.bus = bus;
            this.hatch = null;
            this.controlRod = null;
            this.highPressureHatch = null;
            this.gx = gx;
            this.gy = gy;
        }

        public NuclearGridTile(MTENuclearReactor reactor, MTEHatchNuclearHatch hatch, int gx, int gy) {
            this.reactor = reactor;
            this.bus = null;
            this.hatch = hatch;
            this.controlRod = null;
            this.highPressureHatch = null;
            this.gx = gx;
            this.gy = gy;
        }

        public NuclearGridTile(MTENuclearReactor reactor, MTEHatchNuclearControlRod controlRod, int gx, int gy) {
            this.reactor = reactor;
            this.bus = null;
            this.hatch = null;
            this.controlRod = controlRod;
            this.highPressureHatch = null;
            this.gx = gx;
            this.gy = gy;
        }

        public NuclearGridTile(MTENuclearReactor reactor, MTEHatchNuclearHighPressure highPressureHatch, int gx,
            int gy) {
            this.reactor = reactor;
            this.bus = null;
            this.hatch = null;
            this.controlRod = null;
            this.highPressureHatch = highPressureHatch;
            this.gx = gx;
            this.gy = gy;
        }

        public boolean isBus() {
            return bus != null;
        }

        public boolean isHatch() {
            return hatch != null;
        }

        public boolean isControlRod() {
            return controlRod != null;
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
            return controlRod;
        }

        public MTEHatchNuclearHighPressure getHighPressureHatch() {
            return highPressureHatch;
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

    @Override
    public void getExtraWailaNBT(EntityPlayerMP playerMP, TileEntity tileEntity, NBTTagCompound tag, World world, int x,
        int y, int z) {
        tag.setDouble("coreTemp", mCoreTemp);
        double maxTemp = NuclearSimulationEngine.getMaxOperatingTemperature(mPipeTier);
        tag.setDouble("maxTemp", maxTemp);
        tag.setLong("euOutput", mDirectPowerEUt);
        tag.setInteger("coolantRate", mOutputCoolantRate);
        tag.setString("coolantName", mOutputCoolantName != null ? mOutputCoolantName : "");
        tag.setFloat("reactivity", (float) mReactivity);
        tag.setInteger("neutronsProduced", mNeutronsProduced);
        tag.setInteger("fastAbsorbed", mFastAbsorbed);
        tag.setInteger("thermalAbsorbed", mThermalAbsorbed);
        tag.setInteger("escapedNeutrons", mEscapedNeutrons);
        tag.setDouble("damage", mReactorDamage);

        int fuelCount = 0;
        int coolantHatchCount = 0;
        int controlRodCount = 0;
        int hpPassageCount = 0;
        for (IGregTechTileEntity te : mNuclearTiles) {
            if (te != null) {
                IMetaTileEntity mte = te.getMetaTileEntity();
                if (mte instanceof MTEHatchNuclearBus) {
                    fuelCount++;
                } else if (mte instanceof MTEHatchNuclearHatch) {
                    coolantHatchCount++;
                } else if (mte instanceof MTEHatchNuclearControlRod) {
                    controlRodCount++;
                } else if (mte instanceof MTEHatchNuclearHighPressure) {
                    hpPassageCount++;
                }
            }
        }
        tag.setInteger("fuelCount", fuelCount);
        tag.setInteger("coolantHatchCount", coolantHatchCount);
        tag.setInteger("controlRodCount", controlRodCount);
        tag.setInteger("hpPassageCount", hpPassageCount);
        tag.setInteger("totalCells", mNuclearTiles.size());
    }

    @Override
    public void getExtraWailaBody(ItemStack itemStack, List<String> list, NBTTagCompound tag,
        IWailaDataAccessor accessor, IWailaConfigHandler config) {
        double temp = tag.getDouble("coreTemp");
        double maxTemp = tag.getDouble("maxTemp");
        if (maxTemp <= 0.0) {
            maxTemp = 2500.0;
        }
        double ratio = Math.max(0.0, Math.min(1.0, temp / maxTemp));

        int topColor;
        int bottomColor;
        if (ratio >= 0.90) {
            topColor = 0xFFFF3333;
            bottomColor = 0xFF880000;
        } else if (ratio >= 0.75) {
            topColor = 0xFFFF9900;
            bottomColor = 0xFFCC5500;
        } else if (ratio >= 0.50) {
            topColor = 0xFFFFD700;
            bottomColor = 0xFFB8860B;
        } else {
            topColor = 0xFF00E676;
            bottomColor = 0xFF007A33;
        }
        String tempText = String.format("Core Temp: %,.1f / %,.0f °C", temp, maxTemp);
        list.add(TTRenderBar.create(tempText, topColor, bottomColor, ratio));

        long euOutput = tag.getLong("euOutput");
        int coolantRate = tag.getInteger("coolantRate");
        String coolantName = tag.getString("coolantName");

        if (euOutput > 0) {
            list.add(
                EnumChatFormatting.GREEN + "EU Output: "
                    + EnumChatFormatting.WHITE
                    + String.format("+%,d EU/t", euOutput));
        }
        if (coolantRate > 0 && !coolantName.isEmpty()) {
            list.add(
                EnumChatFormatting.AQUA + "Coolant Output: "
                    + EnumChatFormatting.WHITE
                    + String.format("%,d L/s %s", coolantRate, coolantName));
        }
        if (euOutput == 0 && coolantRate == 0) {
            list.add(EnumChatFormatting.GRAY + "Output: " + EnumChatFormatting.DARK_GRAY + "0 EU/t | 0 L/s");
        }

        double dmg = tag.getDouble("damage");
        if (dmg > 0.0) {
            list.add(EnumChatFormatting.RED + String.format(Locale.US, "Reactor Damage: %.1f%%", dmg));
        }

        float reactivity = tag.getFloat("reactivity");
        int flux = tag.getInteger("neutronsProduced");
        list.add(
            EnumChatFormatting.YELLOW + "Reactivity: "
                + EnumChatFormatting.WHITE
                + String.format("%.1f%%", reactivity * 100.0f)
                + EnumChatFormatting.GRAY
                + " | "
                + EnumChatFormatting.YELLOW
                + "Flux: "
                + EnumChatFormatting.WHITE
                + NuclearSimulationEngine.formatNeutronFlux(flux));

        int fast = tag.getInteger("fastAbsorbed");
        int therm = tag.getInteger("thermalAbsorbed");
        int esc = tag.getInteger("escapedNeutrons");
        if (flux > 0 || fast > 0 || therm > 0 || esc > 0) {
            list.add(
                EnumChatFormatting.GRAY + String.format("Neutrons: %,d fast, %,d therm, %,d esc", fast, therm, esc));
        }

        int fuelCount = tag.getInteger("fuelCount");
        int coolantCount = tag.getInteger("coolantHatchCount");
        int totalCells = tag.getInteger("totalCells");
        if (totalCells > 0) {
            list.add(
                EnumChatFormatting.GRAY
                    + String.format("Grid Cells: %d Fuel, %d Coolant / %d Total", fuelCount, coolantCount, totalCells));
        }
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currentTip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        final NBTTagCompound tag = accessor.getNBTData();
        String efficiency = EnumChatFormatting.RESET + StatCollector
            .translateToLocalFormatted("GT5U.waila.multiblock.status.efficiency", tag.getFloat("efficiency"));
        if (tag.getBoolean("hasProblems")) {
            currentTip.add(
                EnumChatFormatting.RED + StatCollector.translateToLocal("GT5U.waila.multiblock.status.has_problem")
                    + efficiency);
        } else if (!tag.getBoolean("incompleteStructure")) {
            currentTip.add(
                EnumChatFormatting.GREEN + StatCollector.translateToLocal("GT5U.waila.multiblock.status.running_fine")
                    + efficiency);
        }
        try {
            if (gregtech.GTMod.proxy != null && gregtech.GTMod.proxy.wailaAverageNS && tag.hasKey("averageNS")) {
                int tAverageTime = tag.getInteger("averageNS");
                currentTip.add(
                    StatCollector.translateToLocalFormatted(
                        "GT5U.waila.multiblock.status.cpu_load",
                        formatNumber((long) tAverageTime)));
            }
        } catch (Throwable ignored) {}
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
        if (!mMachine) return;
        double maxTemp = NuclearSimulationEngine.getMaxOperatingTemperature(mPipeTier);
        info.add(String.format("Core Temp: %,.1f / %,.0f °C", mCoreTemp, maxTemp));
        if (mDirectPowerEUt > 0) {
            info.add(String.format("EU Output: +%,d EU/t", mDirectPowerEUt));
        }
        if (mOutputCoolantRate > 0 && mOutputCoolantName != null && !mOutputCoolantName.isEmpty()) {
            info.add(String.format("Coolant Output: %,d L/s %s", mOutputCoolantRate, mOutputCoolantName));
        }
        info.add(String.format("Reactivity: %.1f%%", mReactivity * 100.0));
        info.add("Flux: " + NuclearSimulationEngine.formatNeutronFlux(mNeutronsProduced));
        info.add(
            String.format("Neutrons: %,d fast, %,d therm, %,d esc", mFastAbsorbed, mThermalAbsorbed, mEscapedNeutrons));
        int fuelCount = 0;
        int coolantHatchCount = 0;
        int controlRodCount = 0;
        int hpPassageCount = 0;
        for (IGregTechTileEntity te : mNuclearTiles) {
            if (te != null) {
                IMetaTileEntity mte = te.getMetaTileEntity();
                if (mte instanceof MTEHatchNuclearBus) {
                    fuelCount++;
                } else if (mte instanceof MTEHatchNuclearHatch) {
                    coolantHatchCount++;
                } else if (mte instanceof MTEHatchNuclearControlRod) {
                    controlRodCount++;
                } else if (mte instanceof MTEHatchNuclearHighPressure) {
                    hpPassageCount++;
                }
            }
        }
        if (!mNuclearTiles.isEmpty()) {
            info.add(
                String.format(
                    "Grid Cells: %d Fuel, %d Coolant, %d HP Loop, %d Control / %d Total",
                    fuelCount,
                    coolantHatchCount,
                    hpPassageCount,
                    controlRodCount,
                    mNuclearTiles.size()));
        }
    }

    public static class ReactorDummy implements IReactor {

        private final MTENuclearReactor reactor;
        private NuclearGridTile currentTile;

        public ReactorDummy(MTENuclearReactor reactor) {
            this.reactor = reactor;
        }

        public void setCurrentTile(NuclearGridTile tile) {
            this.currentTile = tile;
        }

        @Override
        public ChunkCoordinates getPosition() {
            if (reactor.getBaseMetaTileEntity() == null) return new ChunkCoordinates(0, 0, 0);
            return new ChunkCoordinates(
                reactor.getBaseMetaTileEntity()
                    .getXCoord(),
                reactor.getBaseMetaTileEntity()
                    .getYCoord(),
                reactor.getBaseMetaTileEntity()
                    .getZCoord());
        }

        @Override
        public World getWorld() {
            return reactor.getBaseMetaTileEntity() != null ? reactor.getBaseMetaTileEntity()
                .getWorld() : null;
        }

        @Override
        public int getHeat() {
            return 0; // No concept of reactor hull heat
        }

        @Override
        public void setHeat(int heat) {}

        @Override
        public int addHeat(int amount) {
            return 0;
        }

        @Override
        public int getMaxHeat() {
            return 10000;
        }

        @Override
        public void setMaxHeat(int maxHeat) {}

        @Override
        public void addEmitHeat(int heat) {}

        @Override
        public float getHeatEffectModifier() {
            return 1.0f;
        }

        @Override
        public void setHeatEffectModifier(float modifier) {}

        @Override
        public float getReactorEnergyOutput() {
            return 0;
        }

        @Override
        public double getReactorEUEnergyOutput() {
            return 0;
        }

        @Override
        public float addOutput(float energy) {
            return energy;
        }

        @Override
        public ItemStack getItemAt(int x, int y) {
            if (reactor.mGrid != null && x >= 0 && x < reactor.gridSize && y >= 0 && y < reactor.gridSize) {
                INuclearTile t = reactor.mGrid[x][y];
                if (t instanceof NuclearGridTile gt && gt.isBus()) {
                    return gt.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT];
                }
            } else if (currentTile != null && currentTile.isBus()) {
                return currentTile.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            }
            return null;
        }

        @Override
        public void setItemAt(int x, int y, ItemStack item) {
            if (reactor.mGrid != null && x >= 0 && x < reactor.gridSize && y >= 0 && y < reactor.gridSize) {
                INuclearTile t = reactor.mGrid[x][y];
                if (t instanceof NuclearGridTile gt && gt.isBus()) {
                    gt.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT] = item;
                    gt.getBus()
                        .markTileDirty();
                }
            } else if (currentTile != null && currentTile.isBus()) {
                currentTile.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT] = item;
                currentTile.getBus()
                    .markTileDirty();
            }
        }

        @Override
        public void explode() {}

        @Override
        public int getTickRate() {
            return 20;
        }

        @Override
        public boolean produceEnergy() {
            return true;
        }

        @Override
        public void setRedstoneSignal(boolean redstone) {}

        @Override
        public boolean isFluidCooled() {
            return false;
        }
    }
}
