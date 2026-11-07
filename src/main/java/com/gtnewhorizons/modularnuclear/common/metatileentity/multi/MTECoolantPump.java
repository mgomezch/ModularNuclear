package com.gtnewhorizons.modularnuclear.common.metatileentity.multi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizons.modularnuclear.common.config.CoolantLoopDataRegistry;
import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantFluidProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantLoopEngine;
import com.gtnewhorizons.modularnuclear.common.coolantloop.ICoolantLoopDevice;
import com.gtnewhorizons.modularnuclear.common.coolantloop.ICoolantLoopPump;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopChunkLoader;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopFailure;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopGraphCrawler;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopSegment;
import com.gtnewhorizons.modularnuclear.common.gui.CoolantLoopMachineGui;
import com.gtnewhorizons.modularnuclear.common.gui.graph.CoolantLoopGraphData;
import com.gtnewhorizons.modularnuclear.common.metatileentity.CoolantStructureChannels;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchPressurizedFluid;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.structure.CoolantPumpStructure;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.util.CoolantPumpReservoir;
import com.gtnewhorizons.modularnuclear.common.util.ClientText;
import com.gtnewhorizons.modularnuclear.common.util.CoolantFluidHelper;
import com.gtnewhorizons.modularnuclear.common.util.LocalizedText;
import com.gtnewhorizons.modularnuclear.common.util.LocalizedTextNbt;
import com.gtnewhorizons.modularnuclear.common.util.MonitorCover;
import com.gtnewhorizons.modularnuclear.common.util.MultiblockStartup;
import com.gtnewhorizons.modularnuclear.common.util.RotorThermalHelper;
import com.gtnewhorizons.modularnuclear.common.util.StructureChunks;
import com.gtnewhorizons.modularnuclear.common.util.TickPhase;
import com.gtnewhorizons.modularui.api.widget.IWidgetBuilder;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.HatchElement;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.items.MetaGeneratedTool;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchMaintenance;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.GTLog;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.TurbineStatCalculator;
import gregtech.api.util.shutdown.ShutDownReason;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * Multiblock Coolant Loop Pump.
 *
 * Positions directly underneath a Railcraft multiblock tank.
 * Solves the closed loop hydrodynamics and advective heat transport.
 */
public class MTECoolantPump extends MTEEnhancedMultiBlockBase<MTECoolantPump>
    implements ICoolantLoopPump, ISurvivalConstructable {

    // Multiblock dimensions (dynamic footprint W x L, fixed height 2)
    protected int mWidth = 3;
    protected int mLength = 3;
    protected final int mHeight = 2;

    // Hatches
    public MTEHatchPressurizedFluid mDischargeHatch = null;
    public MTEHatchPressurizedFluid mSuctionHatch = null;

    // Simulation Engine
    public static class DeviceSegmentBinding {

        public final ICoolantLoopDevice device;
        public final LoopSegment segment;

        public DeviceSegmentBinding(ICoolantLoopDevice device, LoopSegment segment) {
            this.device = device;
            this.segment = segment;
        }
    }

    protected CoolantLoopEngine mEngine = new CoolantLoopEngine(CoolantFluidProperty.DISTILLED_WATER);
    protected List<ICoolantLoopDevice> mLoopDevices = new ArrayList<>();
    protected final List<DeviceSegmentBinding> mDeviceBindings = new ArrayList<>();
    protected long mLastSyncedGasVersion = -1L;
    protected boolean mLoopFormed = false;
    /**
     * The blocks the formed loop runs through (pipes and devices), as they were when it formed; rebuilt lazily after
     * a load. A loop whose blocks change no longer exists.
     */
    protected int[] mFootprint;
    static final long STRUCTURE_SETTLE_TICKS = 200;
    /** How often (ticks) a formed loop checks that its blocks are still there; the check walks the whole footprint. */
    private static final int LOOP_CHECK_INTERVAL_TICKS = 100;
    /**
     * How often (ticks) a formed loop is crawled again. The loop check sees blocks change, not a device dropping its
     * binding to the pump (a reactor's passages, say), which only a crawl restores.
     */
    private static final int LOOP_RECRAWL_INTERVAL_TICKS = 200;
    /** The widest pump, matching the widest Railcraft tank interior. */
    private static final int MAX_PUMP_WIDTH = 9;
    protected Block[] mFootprintBlocks;
    /** The status line, as a lang key and its arguments: each client localizes it. */
    LocalizedText mLoopStatus = LocalizedText.of("coolantloops.pump.status.waiting");
    protected Materials mTankMaterial = Materials.Steel;

    // Impeller slot
    protected ItemStack mRotorStack = null;
    protected double mRotorEfficiency = 0.85;
    protected Materials mRotorMaterial = null;
    protected Double mSimulatedFluidTempCelsius = null;

    public enum LoopState {
        EMPTY,
        FILLING,
        FILLED,
        CIRCULATING,
        DECELERATING,
        DRAINING,
        STOPPED,
        FAILED
    }

    // Loop State & Initial Filling Phase
    protected LoopState mLoopState = LoopState.EMPTY;
    protected long mCurrentFillLiters = 0L;
    protected long mRequiredFillLiters = 0L;
    protected long mFillRateLitersPerTick = (CoolantLoopDataRegistry.getPhysicsData() != null)
        ? CoolantLoopDataRegistry.getPhysicsData()
            .getFillRateLitersPerTick()
        : 1000L;
    /**
     * Running total of the fill volume of the loop's pipes (in crawl order) then devices, so filling can go part by
     * part; null until the loop is crawled.
     */
    protected long[] mFillOrderCumulative = null;

    // Reservoir management
    protected final CoolantPumpReservoir mReservoir = new CoolantPumpReservoir(this);
    protected boolean mPowerFailed = false;

    // Multi-tick loop crawling state
    protected long mLastCrawlStartTime = -1L;
    protected long mLastCrawlCompleteTime = -1L;
    protected LoopGraphCrawler.CrawlSession mActiveCrawl = null;
    protected boolean mNeedsPostLoadRebind = false;
    /** The next crawl is of a loop that was formed, so the loop keeps its state if the crawl finds it unchanged. */
    boolean mRecrawlKeepsState = false;
    /** Chunk tickets keeping the formed loop's chunks loaded. */
    List<ForgeChunkManager.Ticket> mChunkTickets = new ArrayList<>();
    /** The chunks the formed loop occupies, as chunkXZ2Int keys; saved, so a load can load them all at once. */
    Set<Long> mLoopChunks = new HashSet<>();
    protected int[] mSavedDischargeCoord = null;
    protected int[] mSavedSuctionCoord = null;

    public long getLastCrawlStartTime() {
        return mLastCrawlStartTime;
    }

    public long getLastCrawlCompleteTime() {
        return mLastCrawlCompleteTime;
    }

    public LoopGraphCrawler.CrawlSession getActiveCrawl() {
        return mActiveCrawl;
    }

    // Loop Graph Visualization & Cover Detection
    protected LoopGraphCrawler.CrawlResult mLastCrawlResult = null;

    public LoopGraphCrawler.CrawlResult getLastCrawlResult() {
        return mLastCrawlResult;
    }

    public boolean hasComputerMonitorCover() {
        return MonitorCover.isOn(getBaseMetaTileEntity());
    }

    @Override
    public boolean allowCoverOnSide(ForgeDirection side, ItemStack coverItem) {
        if (MonitorCover.is(coverItem)) return true;
        return super.allowCoverOnSide(side, coverItem);
    }

    public String getLoopGraphDataJson() {
        return CoolantLoopGraphData.buildFrom(this)
            .toJson();
    }

    public boolean isPowerFailed() {
        if (getBaseMetaTileEntity() != null && getBaseMetaTileEntity().wasShutdown()) {
            return getBaseMetaTileEntity().getLastShutDownReason() == ShutDownReasonRegistry.POWER_LOSS;
        }
        return mPowerFailed;
    }

    @Override
    public void onEnableWorking() {
        super.onEnableWorking();
        mPowerFailed = false;
    }

    public boolean isMachineFormed() {
        return mMachine;
    }

    public int getWidth() {
        return mWidth;
    }

    public void setWidth(int width) {
        this.mWidth = width;
    }

    public int getLength() {
        return mLength;
    }

    public void setLength(int length) {
        this.mLength = length;
    }

    public void setLoopStatus(LocalizedText loopStatus) {
        mLoopStatus = loopStatus != null ? loopStatus : LocalizedText.EMPTY;
    }

    public CoolantPumpReservoir getReservoir() {
        return mReservoir;
    }

    public boolean isDischargeHatchDisabled() {
        if (mDischargeHatch == null) return false;
        return !mDischargeHatch.isAllowedToWork();
    }

    public long getSimulatedTankCapacityLiters() {
        return mReservoir.getSimulatedTankCapacityLiters();
    }

    public void setSimulatedTankCapacityLiters(long capacity) {
        mReservoir.setSimulatedTankCapacityLiters(capacity);
    }

    public long getSimulatedCoolantLiters() {
        return mReservoir.getSimulatedCoolantLiters();
    }

    public void setSimulatedCoolantLiters(long liters) {
        mReservoir.setSimulatedCoolantLiters(liters);
    }

    public void addLoopDevice(ICoolantLoopDevice dev) {
        if (dev != null && !mLoopDevices.contains(dev)) {
            mLoopDevices.add(dev);
            dev.setLoopPump(this);
            LoopSegment seg = null;
            if (mEngine != null) {
                String devId = dev.getDeviceId();
                for (LoopSegment s : mEngine.getSegments()) {
                    if (s.getId()
                        .equals(devId)) {
                        seg = s;
                        break;
                    }
                }
            }
            mDeviceBindings.add(new DeviceSegmentBinding(dev, seg));
        }
    }

    public void rebuildDeviceBindings() {
        mDeviceBindings.clear();
        if (mEngine == null || mLoopDevices.isEmpty()) {
            return;
        }
        Map<String, LoopSegment> segmentMap = new HashMap<>();
        for (LoopSegment s : mEngine.getSegments()) {
            segmentMap.put(s.getId(), s);
        }
        for (ICoolantLoopDevice dev : mLoopDevices) {
            mDeviceBindings.add(new DeviceSegmentBinding(dev, segmentMap.get(dev.getDeviceId())));
        }
    }

    public List<DeviceSegmentBinding> getDeviceBindings() {
        if (mDeviceBindings.isEmpty() && !mLoopDevices.isEmpty()) {
            rebuildDeviceBindings();
        }
        return mDeviceBindings;
    }

    /** Flow through a device's segment: the loop flow times the share of it that reaches that branch. */
    static double branchFlow(double loopFlowM3s, LoopSegment segment) {
        return segment != null ? loopFlowM3s * segment.getFlowFraction() : loopFlowM3s;
    }

    public List<ICoolantLoopDevice> getLoopDevices() {
        return mLoopDevices;
    }

    public MTECoolantPump(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTECoolantPump(String aName) {
        super(aName);
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
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTECoolantPump(mName);
    }

    @Override
    public ItemStack getMachineCraftingIcon() {
        if (getBaseMetaTileEntity() == null) {
            return null;
        }
        return super.getMachineCraftingIcon();
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int aColorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            return new ITexture[] { TextureFactory.of(GregTechAPI.sBlockCasings2, 15),
                TextureFactory.of(
                    aActive ? Textures.BlockIcons.OVERLAY_FRONT_DIESEL_ENGINE_ACTIVE
                        : Textures.BlockIcons.OVERLAY_FRONT_DIESEL_ENGINE) };
        }
        return new ITexture[] { TextureFactory.of(GregTechAPI.sBlockCasings2, 15) };
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> aErrors) {
        mDischargeHatch = null;
        mSuctionHatch = null;
        mEnergyHatches.clear();
        mMaintenanceHatches.clear();
        mInputBusses.clear();

        // Check Railcraft tank presence above pump
        boolean hasValidTank = verifyRailcraftTankAbove(aBaseMetaTileEntity);
        if (!hasValidTank) {
            if ("coolantloops.pump.status.formed".equals(mLoopStatus.key) || mLoopStatus.equals(LocalizedText.EMPTY)) {
                setLoopStatusKey("coolantloops.pump.status.missing_or_mismatched_tank");
            }
            aErrors.add(StructureErrors.of("coolantloops.structure.error.pump.missing_or_mismatched_tank"));
            return;
        }

        World world = aBaseMetaTileEntity.getWorld();
        if (world != null) {
            List<MTEHatchPressurizedFluid> pressHatches = CoolantPumpStructureScan
                .scan(this, aBaseMetaTileEntity, world, aErrors);
            if (pressHatches == null) return;

            if (mDischargeHatch == null) {
                setLoopStatusKey("coolantloops.pump.status.req_hatches");
                aErrors.add(StructureErrors.of("coolantloops.structure.error.missing_discharge_hatch"));
            }
            if (mSuctionHatch == null) {
                setLoopStatusKey("coolantloops.pump.status.req_hatches");
                aErrors.add(StructureErrors.of("coolantloops.structure.error.missing_suction_hatch"));
            }

            checkOneMaintenanceHatch(aErrors);

            if (mEnergyHatches.isEmpty()) {
                checkHasEnergyHatch(aErrors);
            } else if (mEnergyHatches.size() > 2) {
                checkHatchMax(aErrors, HatchElement.Energy, 2);
            }

            if (mInputBusses.size() > 1) {
                checkHatchMax(aErrors, HatchElement.InputBus, 1);
            }

            if (!aErrors.isEmpty()) {
                return;
            }

            CoolantPumpStructureScan.texture(this, pressHatches);
        }

        checkMaintenance();
        setLoopStatusKey("coolantloops.pump.status.formed");
        // A saved loop is restored by rebindRestoredLoop, which re-crawls only if it changed
        if (mActiveCrawl != null && !mActiveCrawl.isDone() || mNeedsPostLoadRebind) {
            return;
        }
        crawlLoopGraph();
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        return checkMachine_TT(aBaseMetaTileEntity);
    }

    /**
     * Checks if a Railcraft multiblock tank sits directly above this pump structure,
     * fully formed with matching footprint and containing coolant fluid.
     */
    public boolean verifyRailcraftTankAbove(IGregTechTileEntity aBaseMetaTileEntity) {
        return mReservoir.verifyRailcraftTankAbove(aBaseMetaTileEntity);
    }

    public static boolean isPlainRegularWater(net.minecraftforge.fluids.Fluid fluid) {
        return CoolantFluidHelper.isPlainRegularWater(fluid);
    }

    public static boolean isPlainRegularWater(FluidStack stack) {
        return CoolantFluidHelper.isPlainRegularWater(stack);
    }

    public static boolean isLiquidNuclearFuel(FluidStack stack) {
        return CoolantFluidHelper.isLiquidNuclearFuel(stack);
    }

    public static boolean isLiquidNuclearFuel(String name) {
        return CoolantFluidHelper.isLiquidNuclearFuel(name);
    }

    public static boolean isGaseousFluid(net.minecraftforge.fluids.Fluid fluid, FluidStack stack) {
        return CoolantFluidHelper.isGaseousFluid(fluid, stack);
    }

    public static boolean isGaseousFluid(FluidStack stack) {
        return CoolantFluidHelper.isGaseousFluid(stack);
    }

    public static boolean isLava(String name) {
        return CoolantFluidHelper.isLava(name);
    }

    public static boolean isLava(net.minecraftforge.fluids.Fluid fluid, FluidStack stack) {
        return CoolantFluidHelper.isLava(fluid, stack);
    }

    public static boolean isLava(FluidStack stack) {
        return CoolantFluidHelper.isLava(stack);
    }

    public static boolean isNetherWorld(World world) {
        return CoolantFluidHelper.isNetherWorld(world);
    }

    public static boolean isMoltenFluid(net.minecraftforge.fluids.Fluid fluid, FluidStack stack,
        CoolantFluidProperty prop) {
        return CoolantFluidHelper.isMoltenFluid(fluid, stack, prop);
    }

    public static boolean isMoltenFluid(FluidStack stack) {
        return CoolantFluidHelper.isMoltenFluid(stack);
    }

    public static double getDeclaredGregTechFluidTemperatureCelsius(net.minecraftforge.fluids.Fluid fluid,
        FluidStack stack, CoolantFluidProperty prop) {
        return CoolantFluidHelper.getDeclaredGregTechFluidTemperatureCelsius(fluid, stack, prop);
    }

    public static double getFluidStackTemperatureCelsius(FluidStack stack) {
        return CoolantFluidHelper.getFluidStackTemperatureCelsius(stack);
    }

    protected double mSimulatedBiomeTempCelsius = 20.0;

    public void setSimulatedBiomeTemperatureCelsius(double temp) {
        this.mSimulatedBiomeTempCelsius = temp;
    }

    public static double calculateAmbientTemperature(World world, int x, int y, int z) {
        return CoolantFluidHelper.calculateAmbientTemperature(world, x, y, z);
    }

    public double getBiomeTemperatureCelsius() {
        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te != null && te.getWorld() != null) {
            return calculateAmbientTemperature(te.getWorld(), te.getXCoord(), te.getYCoord(), te.getZCoord());
        }
        return mSimulatedBiomeTempCelsius;
    }

    public double getCoolantTemperatureCelsius() {
        if (mEngine != null && mEngine.getSegments() != null
            && !mEngine.getSegments()
                .isEmpty()) {
            if (mLoopState == LoopState.CIRCULATING || mEngine.getVolumetricFlowRate() > 1e-5) {
                return mEngine.getMinLoopTempCelsius();
            }
        }
        if (mReservoir != null) {
            double resTemp = mReservoir.getReservoirTemperatureCelsius();
            if (!Double.isNaN(resTemp)) {
                return resTemp;
            }
        }
        return getBiomeTemperatureCelsius();
    }

    public FluidStack getReservoirFluid() {
        return mReservoir.getReservoirFluid();
    }

    /**
     * Re-crawls the connected piping circuit from the discharge hatch back to suction.
     */
    public boolean crawlLoopGraph() {
        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te == null || te.getWorld() == null) {
            return false;
        }
        if (mLoopFormed) mRecrawlKeepsState = true;

        if (mDischargeHatch == null || mSuctionHatch == null) {
            mActiveCrawl = null;
            mRecrawlKeepsState = false;
            releaseLoopChunks();
            mLoopFormed = false;
            mLoopState = LoopState.EMPTY;
            mCurrentFillLiters = 0L;
            setLoopStatusKey("coolantloops.pump.status.req_hatches");
            return false;
        }

        IGregTechTileEntity discTE = mDischargeHatch.getBaseMetaTileEntity();
        IGregTechTileEntity sucTE = mSuctionHatch.getBaseMetaTileEntity();

        if (discTE == null || sucTE == null) {
            mActiveCrawl = null;
            mLoopFormed = false;
            return false;
        }

        mLastCrawlStartTime = (te != null) ? te.getTimer() : System.currentTimeMillis();
        LoopGraphCrawler crawler = new LoopGraphCrawler(te.getWorld(), LoopGraphCrawler.DEFAULT_MAX_STEPS);
        mActiveCrawl = crawler.startSession(
            discTE.getXCoord(),
            discTE.getYCoord(),
            discTE.getZCoord(),
            discTE.getFrontFacing(),
            sucTE.getXCoord(),
            sucTE.getYCoord(),
            sucTE.getZCoord(),
            sucTE.getFrontFacing(),
            new LoopGraphCrawler.BlockPosCoord(te.getXCoord(), te.getYCoord(), te.getZCoord()),
            this,
            mTankMaterial);

        mActiveCrawl.step(LoopGraphCrawler.STEPS_PER_TICK);

        if (mActiveCrawl.isDone()) {
            mLastCrawlCompleteTime = (te != null) ? te.getTimer() : System.currentTimeMillis();
            LoopGraphCrawler.CrawlResult result = mActiveCrawl.getResult();
            mActiveCrawl = null;
            return applyCrawlResult(result);
        } else if (!mLoopFormed) {
            setLoopStatusKey(
                "coolantloops.pump.status.crawling",
                mActiveCrawl.getStepCount(),
                mActiveCrawl.getTicksElapsed());
            return false;
        } else {
            // A formed loop keeps circulating while it is re-crawled
            return false;
        }
    }

    public void stepActiveCrawl() {
        if (mActiveCrawl == null) return;
        mActiveCrawl.step(LoopGraphCrawler.STEPS_PER_TICK);
        if (mActiveCrawl.isDone()) {
            IGregTechTileEntity te = getBaseMetaTileEntity();
            mLastCrawlCompleteTime = (te != null) ? te.getTimer() : System.currentTimeMillis();
            LoopGraphCrawler.CrawlResult result = mActiveCrawl.getResult();
            mActiveCrawl = null;
            applyCrawlResult(result);
        } else if (!mLoopFormed) {
            setLoopStatusKey(
                "coolantloops.pump.status.crawling",
                mActiveCrawl.getStepCount(),
                mActiveCrawl.getTicksElapsed());
        }
    }

    public boolean applyCrawlResult(LoopGraphCrawler.CrawlResult result) {
        return CoolantPumpLoopBinding.applyCrawlResult(this, result);
    }

    /** This pump's offset for its periodic checks, so pumps loaded together do not crawl on the same tick. */
    private int mSchedulePhase = -1;

    int schedulePhase() {
        if (mSchedulePhase < 0 && getBaseMetaTileEntity() != null) {
            mSchedulePhase = TickPhase.of(getBaseMetaTileEntity());
        }
        return Math.max(mSchedulePhase, 0);
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity != null && aBaseMetaTileEntity.isServerSide()) {
            int phase = schedulePhase();
            if (mNeedsPostLoadRebind) {
                rebindRestoredLoop(aBaseMetaTileEntity);
            }
            if (!hasRotor()) {
                checkAndLoadImpellerFromInputBus();
            }
            if (mActiveCrawl != null) {
                stepActiveCrawl();
            } else if (!mLoopFormed && mMachine && TickPhase.due(aTick, phase, 100)) {
                crawlLoopGraph();
            }
            if (mLoopFormed && TickPhase.due(aTick, phase, LOOP_CHECK_INTERVAL_TICKS)) {
                LocalizedText loopBreak = findLoopBreak(aBaseMetaTileEntity.getWorld());
                if (loopBreak != null) onDeviceDisconnected(null, loopBreak);
            }
            if (mLoopFormed && mActiveCrawl == null && TickPhase.due(aTick, phase, LOOP_RECRAWL_INTERVAL_TICKS)) {
                crawlLoopGraph();
            }
            stepCoolantLoop();
            // Saves the loop's changing state; markDirty also updates comparators around the pump, so not every second
            if (mLoopFormed && TickPhase.due(aTick, phase, 100)) {
                aBaseMetaTileEntity.markDirty();
            }
        }
    }

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        if (!aBaseMetaTileEntity.isServerSide()) return;
        if (mNeedsPostLoadRebind) loadLoopChunks(aBaseMetaTileEntity.getWorld());
        MultiblockStartup.formNow(this);
        if (mNeedsPostLoadRebind) rebindRestoredLoop(aBaseMetaTileEntity);
    }

    /**
     * Loads every chunk a saved loop occupies, so the loop can be restored on the pump's first tick instead of
     * waiting for its chunks. It runs in a tick, never while a chunk is loading, so it cannot recurse into loads.
     * Chunk tickets normally have them loaded already; this covers a loop saved without them.
     */
    private void loadLoopChunks(World world) {
        if (!(world instanceof WorldServer ws)) return;
        Set<Long> chunks = new HashSet<>(mLoopChunks);
        IGregTechTileEntity base = getBaseMetaTileEntity();
        // The pump's width is not known before its structure check, so this covers the widest pump and tank
        if (base != null) collectPumpChunks(base, MAX_PUMP_WIDTH, chunks);
        if (mLastCrawlResult != null && mLastCrawlResult.pipePositions != null) {
            for (LoopGraphCrawler.BlockPosCoord p : mLastCrawlResult.pipePositions) {
                StructureChunks.addBlock(p.x, p.z, chunks);
            }
        }
        for (long chunk : chunks) {
            int cx = (int) chunk, cz = (int) (chunk >>> 32);
            if (!ws.theChunkProviderServer.chunkExists(cx, cz)) ws.theChunkProviderServer.loadChunk(cx, cz);
        }
    }

    protected void rebindRestoredLoop(IGregTechTileEntity aBaseMetaTileEntity) {
        CoolantPumpLoopBinding.rebindRestoredLoop(this, aBaseMetaTileEntity);
    }

    public List<MTEHatchEnergy> getValidEnergyHatches() {
        List<MTEHatchEnergy> list = new ArrayList<>();
        if (mEnergyHatches != null) {
            for (MTEHatchEnergy hatch : mEnergyHatches) {
                if (hatch != null && (hatch.isValid() || hatch.getBaseMetaTileEntity() != null)) {
                    list.add(hatch);
                }
            }
        }
        return list;
    }

    public long getRequiredPowerDrawEU() {
        List<MTEHatchEnergy> hatches = getValidEnergyHatches();
        if (hatches.isEmpty()) {
            return 512L;
        }
        if (hatches.size() == 1) {
            int tier = Math.max(0, Math.min(hatches.get(0).mTier, GTValues.V.length - 1));
            return GTValues.V[tier];
        }
        int maxTier = 0;
        for (MTEHatchEnergy h : hatches) {
            if (h.mTier > maxTier) {
                maxTier = h.mTier;
            }
        }
        maxTier = Math.max(0, Math.min(maxTier, GTValues.V.length - 1));
        return 4L * GTValues.V[maxTier];
    }

    public boolean triggerPowerfail() {
        mPowerFailed = true;
        stopMachine(ShutDownReasonRegistry.POWER_LOSS);
        if (getBaseMetaTileEntity() != null) {
            getBaseMetaTileEntity().setActive(false);
            getBaseMetaTileEntity().setShutDownReason(ShutDownReasonRegistry.POWER_LOSS);
        }
        mLoopState = LoopState.STOPPED;
        mEngine.setPumpPowered(false);
        mEngine.setPumpMechanicalPowerWatts(0.0);
        mEngine.setBraking(false);
        setLoopStatus(shutDownText(ShutDownReasonRegistry.POWER_LOSS));
        return stepCoastingAndCheckRupture();
    }

    protected boolean stepCoastingAndCheckRupture() {
        mEngine.step(0.05);
        processCoastingThermalExchange();
        if (mEngine.isRuptured()) {
            triggerCatastrophicExplosion(engineFailureText(), mEngine.getRupturedSegment());
            return false;
        }
        return true;
    }

    /**
     * Executes thermal exchange across in-line devices while fluid is coasting down through inertia.
     */
    public void processCoastingThermalExchange() {
        double flowRate = mEngine.getVolumetricFlowRate();
        if (flowRate > 1e-6) {
            for (DeviceSegmentBinding b : getDeviceBindings()) {
                b.device.processThermalExchange(branchFlow(flowRate, b.segment), 0.05, mEngine.getFluid(), b.segment);
            }
        }
    }

    public boolean drainEnergy(long aEU) {
        if (aEU <= 0) {
            mPowerFailed = false;
            return true;
        }

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
                            mPowerFailed = false;
                            return true;
                        }
                    }
                }
            }
            if (remaining <= 0) {
                mPowerFailed = false;
                return true;
            }
            return false;
        }

        if (getBaseMetaTileEntity() != null && getBaseMetaTileEntity().getStoredEU() >= aEU) {
            boolean drained = getBaseMetaTileEntity().decreaseStoredEnergyUnits(aEU, false);
            if (drained) {
                mPowerFailed = false;
            }
            return drained;
        }
        return false;
    }

    /** The devices of the current loop stop pointing at this pump, so a removed pump is not kept alive by them. */
    protected void releaseLoopDevices() {
        for (ICoolantLoopDevice dev : mLoopDevices) {
            if (dev != null) dev.setLoopPump(null);
        }
        mLoopDevices = new ArrayList<>();
        mDeviceBindings.clear();
    }

    @Override
    public void onRemoval() {
        releaseLoopDevices();
        releaseLoopChunks();
        super.onRemoval();
    }

    @Override
    public void onUnload() {
        releaseLoopDevices();
        // The chunk tickets stay: they are saved with the world and bring the loop back loaded
        super.onUnload();
    }

    /**
     * Why the formed loop no longer exists, or null if it still does: the pump's own structure broke, a device's
     * machine was removed or unformed, or a pipe or device block in a loaded chunk changed since the loop formed.
     */
    public LocalizedText findLoopBreak(World world) {
        return CoolantPumpLoopBinding.findLoopBreak(this, world);
    }

    /**
     * Keeps loaded every chunk the formed loop occupies: the pump, its pipes and the whole structure of each device.
     */
    void updateLoopChunks() {
        CoolantPumpLoopBinding.updateLoopChunks(this);
    }

    /**
     * The chunks of the pump and the Railcraft tank on it. The tank is one block wider than the pump on every side
     * (its walls), and Railcraft checks one more block around it, so its structure can reach into the next chunk.
     */
    static void collectPumpChunks(IGregTechTileEntity base, int width, Set<Long> chunks) {
        ForgeDirection front = base.getFrontFacing();
        boolean faced = front != null && front != ForgeDirection.UNKNOWN;
        ForgeDirection back = faced ? front.getOpposite() : ForgeDirection.SOUTH;
        ForgeDirection right = faced ? front.getRotation(ForgeDirection.UP) : ForgeDirection.WEST;
        int reach = width / 2 + 2;
        for (int side : new int[] { -reach, reach }) {
            for (int depth : new int[] { -2, width + 1 }) {
                StructureChunks.addBlock(
                    base.getXCoord() + right.offsetX * side + back.offsetX * depth,
                    base.getZCoord() + right.offsetZ * side + back.offsetZ * depth,
                    chunks);
            }
        }
    }

    void releaseLoopChunks() {
        LoopChunkLoader.release(mChunkTickets);
        mChunkTickets = new ArrayList<>();
        mLoopChunks = new HashSet<>();
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null && base.getWorld() != null && !base.getWorld().isRemote) {
            LoopChunkLoader.releaseRestored(base.getWorld(), base.getXCoord(), base.getYCoord(), base.getZCoord());
        }
    }

    /** Records the loop's pipe blocks; none after a load, until the first crawl finds them again. */
    void captureFootprint(World world) {
        CoolantPumpLoopBinding.captureFootprint(this, world);
    }

    @Override
    public void onDeviceDisconnected(ICoolantLoopDevice device, LocalizedText reason) {
        CoolantPumpLoopBinding.onDeviceDisconnected(this, device, reason);
    }

    /** How a part of {@link #stepCoolantLoop} ended: carry on with the step, or end it as done or failed. */
    enum Step {

        CARRY_ON,
        DONE,
        FAILED;

        static Step of(boolean done) {
            return done ? DONE : FAILED;
        }
    }

    public boolean stepCoolantLoop() {
        if (!mLoopFormed || mEngine.getSegments()
            .isEmpty()) {
            if (mLoopState != LoopState.EMPTY && mLoopState != LoopState.STOPPED && mLoopState != LoopState.FAILED) {
                mLoopState = LoopState.STOPPED;
            }
            IGregTechTileEntity baseTE = getBaseMetaTileEntity();
            if (baseTE != null && baseTE.isActive()) {
                baseTE.setActive(false);
            }
            return true;
        }

        if (mLoopDevices != null && !mLoopDevices.isEmpty()) {
            // Indexed: onDeviceDisconnected replaces the list, and returns straight after
            for (int i = 0, n = mLoopDevices.size(); i < n; i++) {
                ICoolantLoopDevice dev = mLoopDevices.get(i);
                if (dev != null && dev.isDeviceDisconnected()) {
                    onDeviceDisconnected(dev, dev.getDisconnectionReason());
                    return true;
                }
            }
        }

        if (mEngine.isRuptured()) {
            triggerCatastrophicExplosion(engineFailureText(), mEngine.getRupturedSegment());
            return false;
        }

        IGregTechTileEntity baseTE = getBaseMetaTileEntity();
        if (baseTE == null) {
            return true;
        }

        mReservoir.updateReservoirState();
        mEngine.setHoldsCoolant(mCurrentFillLiters > 0);

        // Draw electrical power if allowed to work (soft hammer / redstone control)
        boolean isWorking = isAllowedToWork();
        if (!isWorking) {
            if (baseTE != null) {
                baseTE.setActive(false);
            }
            mEngine.setPumpPowered(false);
            mEngine.setPumpMechanicalPowerWatts(0.0);
            mEngine.setBraking(false);
            if (isPowerFailed()) {
                setLoopStatus(shutDownText(ShutDownReasonRegistry.POWER_LOSS));
            } else if (baseTE != null && baseTE.wasShutdown() && baseTE.getLastShutDownReason() != null) {
                setLoopStatus(shutDownText(baseTE.getLastShutDownReason()));
            } else if (mLoopState == LoopState.FILLING) {
                setLoopStatusKey("coolantloops.pump.status.filling_paused", mCurrentFillLiters, mRequiredFillLiters);
            } else if (mLoopState == LoopState.DECELERATING || mLoopState == LoopState.DRAINING) {
                setLoopStatusKey("coolantloops.pump.status.stopping_paused", mCurrentFillLiters, mRequiredFillLiters);
            } else {
                setLoopStatusKey(
                    "coolantloops.pump.status.disabled_coasting",
                    mEngine.getVolumetricFlowRate() * 1000.0);
            }
            return stepCoastingAndCheckRupture();
        }

        // Validate fluid in reservoir / loop: liquid nuclear fuel fails pump and destroys impeller immediately!
        FluidStack reservoirFluid = getReservoirFluid();
        Step refuseUnsuitableFluidResult = refuseUnsuitableFluid(reservoirFluid);
        if (refuseUnsuitableFluidResult != Step.CARRY_ON) return refuseUnsuitableFluidResult == Step.DONE;

        // Validate molten fluid temperature
        CoolantFluidProperty currentProp = mEngine.getFluid();
        if (currentProp == null && reservoirFluid != null) {
            currentProp = CoolantFluidProperty.get(
                reservoirFluid.getFluid()
                    .getName());
        }
        boolean isMolten = isMoltenFluid(
            reservoirFluid != null ? reservoirFluid.getFluid() : null,
            reservoirFluid,
            currentProp);

        World loopWorld = getBaseMetaTileEntity() != null ? getBaseMetaTileEntity().getWorld() : null;
        boolean isLavaNether = ((reservoirFluid != null && isLava(reservoirFluid))
            || (currentProp != null && currentProp.isLava())) && isNetherWorld(loopWorld);

        Step checkMoltenCoolantResult = checkMoltenCoolant(reservoirFluid, currentProp, isMolten, isLavaNether);
        if (checkMoltenCoolantResult != Step.CARRY_ON) return checkMoltenCoolantResult == Step.DONE;

        // Require valid turbine rotor impeller in controller slot
        if (!updateRotorEfficiency()) {
            mLoopState = LoopState.STOPPED;
            mEngine.setPumpPowered(false);
            mEngine.setPumpMechanicalPowerWatts(0.0);
            mEngine.setBraking(false);
            setLoopStatusKey("coolantloops.pump.status.missing_rotor");
            return stepCoastingAndCheckRupture();
        }

        // Impeller melting check: if fluid temperature exceeds impeller melting point, melt catastrophically!
        double pumpFluidTemp = getPumpFluidTemperatureCelsius();
        Materials rotorMat = getRotorMaterial();
        double th = RotorThermalHelper.calculateHomologousTemperature(pumpFluidTemp, rotorMat);
        if (RotorThermalHelper.isMelted(th)) {
            failOnImpellerMelting(pumpFluidTemp, rotorMat);
            return stepCoastingAndCheckRupture();
        }

        // Check if high-pressure output hatch is disabled by machine controller cover
        boolean dischargeDisabled = isDischargeHatchDisabled();
        Step stepWithDischargeDisabledResult = stepWithDischargeDisabled(baseTE, dischargeDisabled);
        if (stepWithDischargeDisabledResult != Step.CARRY_ON) return stepWithDischargeDisabledResult == Step.DONE;

        mEngine.setBraking(false);

        // Initial Loop Filling Phase (when hatch is enabled)
        Step stepFillingResult = stepFilling(baseTE, reservoirFluid, currentProp);
        if (stepFillingResult != Step.CARRY_ON) return stepFillingResult == Step.DONE;

        // Active Circulation Phase
        checkMaintenance();
        double effFactor = getEfficiencyFactor();
        long requiredEU = getRequiredPowerDrawEU();
        boolean hasPower = drainEnergy(requiredEU);
        if (!hasPower) {
            return triggerPowerfail();
        }
        if (baseTE != null) {
            baseTE.setActive(true);
        }

        // Refuse to accelerate flow if molten fluid is below its melting point
        if (isMolten) {
            double minLoopTemp = mEngine.getMinLoopTempCelsius();
            double declaredMelting = CoolantFluidHelper.getMeltingPointCelsius(
                reservoirFluid != null ? reservoirFluid.getFluid() : null,
                reservoirFluid,
                currentProp);
            if (minLoopTemp < declaredMelting - CoolantLoopEngine.FREEZE_TOLERANCE_CELSIUS) {
                mEngine.setRuptured(true);
                triggerCatastrophicExplosion(
                    LocalizedText.of(
                        "coolantloops.pump.status.catastrophic_solidification",
                        minLoopTemp,
                        declaredMelting,
                        currentProp != null ? currentProp.getFluidName() : "molten fluid"),
                    mEngine.getRupturedSegment());
                return false;
            }
        }

        mLoopState = LoopState.CIRCULATING;
        mEngine.setPumpPowered(true);
        double wattsPerEu = (CoolantLoopDataRegistry.getPhysicsData() != null)
            ? CoolantLoopDataRegistry.getPhysicsData()
                .getPumpMechanicalPowerPerEU()
            : 80.0;
        double mechanicalWatts = requiredEU * wattsPerEu * mRotorEfficiency * effFactor;
        mEngine.setPumpMechanicalPowerWatts(mechanicalWatts);
        degradeRotor(requiredEU);

        // Synchronize absolute dissolved gases across engine and all segments when changed
        if (mEngine != null) {
            long currentGasVer = mReservoir.getDissolvedGasesVersion();
            if (mLastSyncedGasVersion != currentGasVer) {
                mLastSyncedGasVersion = currentGasVer;
                Map<String, Long> gases = mReservoir.getDissolvedGases();
                mEngine.setDissolvedGases(gases);
                double gasFrac = getDissolvedGasFraction();
                mEngine.setDissolvedGasFraction(gasFrac);
                long loopLiters = mEngine.getTotalLoopVolumeLiters();
                for (LoopSegment seg : mEngine.getSegments()) {
                    seg.setDissolvedGases(gases, loopLiters);
                    seg.setDissolvedGasFraction(gasFrac);
                }
            }
        }

        // Advance 1 tick (0.05 seconds) of fluid dynamics
        mEngine.step(0.05);

        // Process thermal exchange across in-line devices matching segment ID
        double flowRate = mEngine.getVolumetricFlowRate();
        for (DeviceSegmentBinding b : getDeviceBindings()) {
            b.device.processThermalExchange(branchFlow(flowRate, b.segment), 0.05, mEngine.getFluid(), b.segment);
        }

        // Check for catastrophic rupture
        if (mEngine.isRuptured()) {
            triggerCatastrophicExplosion(engineFailureText(), mEngine.getRupturedSegment());
            return false;
        }

        return true;
    }

    @Override
    public boolean onRunningTick(ItemStack aStack) {
        return true;
    }

    /**
     * Refuses liquid fuel (which destroys the impeller), plain water, gases, and lava outside the Nether. Returns
     * CARRY_ON to carry on with the step.
     */
    private Step refuseUnsuitableFluid(FluidStack reservoirFluid) {
        return CoolantPumpFluidSafety.refuseUnsuitableFluid(this, reservoirFluid);
    }

    /**
     * A molten coolant must be at its melting point or hotter to fill or flow. Returns CARRY_ON to carry on
     * with the step.
     */
    private Step checkMoltenCoolant(FluidStack reservoirFluid, CoolantFluidProperty currentProp, boolean isMolten,
        boolean isLavaNether) {
        if (isMolten && !isLavaNether) {
            double declaredMelting = CoolantFluidHelper.getMeltingPointCelsius(
                reservoirFluid != null ? reservoirFluid.getFluid() : null,
                reservoirFluid,
                currentProp);

            // If the loop contains fluid and temperature drops below melting point, catastrophic solidification
            // rupture!
            if (mCurrentFillLiters > 0 && !mEngine.getSegments()
                .isEmpty()) {
                double minLoopTemp = mEngine.getMinLoopTempCelsius();
                if (minLoopTemp < declaredMelting - CoolantLoopEngine.FREEZE_TOLERANCE_CELSIUS) {
                    mEngine.setRuptured(true);
                    triggerCatastrophicExplosion(
                        LocalizedText.of(
                            "coolantloops.pump.status.catastrophic_solidification",
                            minLoopTemp,
                            declaredMelting,
                            currentProp != null ? currentProp.getFluidName() : "molten fluid"),
                        mEngine.getRupturedSegment());
                    return Step.FAILED;
                }
            } else {
                // Loop is empty (before initial fill): refuse to fill if the reservoir is below the melting point.
                // Unfilled pipe has no temperature, so it never stops a fill: it takes the coolant's as it fills
                double resTemp = mReservoir.getReservoirTemperatureCelsius();
                if (Double.isNaN(resTemp)) {
                    resTemp = CoolantFluidHelper.getBaselineInletTemperatureCelsius(
                        reservoirFluid != null ? reservoirFluid.getFluid() : null,
                        reservoirFluid,
                        currentProp,
                        getBiomeTemperatureCelsius());
                }
                if (resTemp < declaredMelting - CoolantLoopEngine.FREEZE_TOLERANCE_CELSIUS) {
                    mLoopState = LoopState.STOPPED;
                    mEngine.setPumpPowered(false);
                    mEngine.setPumpMechanicalPowerWatts(0.0);
                    mEngine.setBraking(false);
                    setLoopStatusKey(
                        "coolantloops.pump.status.refuse_solidify",
                        resTemp,
                        declaredMelting,
                        currentProp != null ? currentProp.getFluidName() : "molten fluid");
                    return Step.DONE;
                }
            }
        }
        return Step.CARRY_ON;
    }

    /**
     * With the discharge hatch disabled by a cover the pump brakes the flow, then drains the loop. Returns CARRY_ON
     * to carry on with the step.
     */
    private Step stepWithDischargeDisabled(IGregTechTileEntity baseTE, boolean dischargeDisabled) {
        return CoolantPumpFillDrain.stepWithDischargeDisabled(this, baseTE, dischargeDisabled);
    }

    /** Pipes or devices the pump fills per second at least, however large they are. */
    static final long FILL_PARTS_PER_SECOND = 4L;

    /**
     * Litres per tick the loop fills from (or drains back into) the reservoir: the configured fill rate, but never
     * slower than four pipes (or devices) per second, taking them in crawl order. Filling is not limited by the loop's
     * flow: a loop of large high-tier pipes, each of which takes its whole capacity, would otherwise take hours.
     */
    long currentFillRateLitersPerTick() {
        long rate = mFillRateLitersPerTick;
        long[] cumulative = mFillOrderCumulative;
        if (cumulative != null && cumulative.length > 0) {
            int i = Arrays.binarySearch(cumulative, mCurrentFillLiters + 1);
            if (i < 0) i = -i - 1;
            i = Math.min(i, cumulative.length - 1);
            long part = cumulative[i] - (i > 0 ? cumulative[i - 1] : 0L);
            rate = Math.max(rate, (part * FILL_PARTS_PER_SECOND + 19) / 20);
        }
        return Math.min(rate, Integer.MAX_VALUE);
    }

    /**
     * Fills the loop from the reservoir until it holds what its pipes and devices need. Returns CARRY_ON to carry on
     * with the step.
     */
    private Step stepFilling(IGregTechTileEntity baseTE, FluidStack reservoirFluid, CoolantFluidProperty currentProp) {
        return CoolantPumpFillDrain.stepFilling(this, baseTE, reservoirFluid, currentProp);
    }

    /** Triggers the catastrophic pump explosion and drains 80% of the fluid from the Railcraft tank. */
    /** The engine's rupture as a status line. */
    protected LocalizedText engineFailureText() {
        LoopFailure f = mEngine.getFailure();
        return f != null ? LocalizedText.of(f.langKey, mEngine.getFailureArgs())
            : LocalizedText.of("coolantloops.engine.failed");
    }

    /** A GregTech shutdown reason as a status line, in each client's language. */
    private static LocalizedText shutDownText(ShutDownReason reason) {
        return LocalizedText.of("GT5U.gui.text.shutdown_reason." + reason.getKey());
    }

    protected void triggerCatastrophicExplosion(LocalizedText reason) {
        triggerCatastrophicExplosion(reason, mEngine != null ? mEngine.getRupturedSegment() : null);
    }

    protected void triggerCatastrophicExplosion(LocalizedText reason, LoopSegment rupturedSegment) {
        GTLog.writeExplosionLog(
            this,
            "COOLANT LOOP FAILURE: " + reason.english()
                + (rupturedSegment != null ? " (segment " + rupturedSegment.getId() + ")" : "")
                + String.format(
                    ", peak %.1f bar, flow %.1f L/s",
                    mEngine.getPeakLoopPressureBar(),
                    mEngine.getVolumetricFlowRate() * 1000.0));

        // Drain 80% of fluid from tank above
        drainFromReservoir((int) (getTotalCoolantLiters() * 0.80));

        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te != null && te.getWorld() != null && !te.getWorld().isRemote) {
            World world = te.getWorld();
            int x = te.getXCoord();
            int y = te.getYCoord();
            int z = te.getZCoord();

            // Explosion scaled to peak loop pressure
            float explosionStrength = Math.min(25.0f, (float) (4.0f + mEngine.getPeakLoopPressureBar() / 10.0));
            if (GregTechAPI.sMachineExplosions) {
                world.createExplosion(null, x + 0.5, y + 1.5, z + 0.5, explosionStrength, true);
                te.doExplosion((long) (explosionStrength * 1000));
            }

            spillCoolantFluid(world, rupturedSegment);
        }

        mLoopState = LoopState.FAILED;
        mEngine.setPumpPowered(false);
        mEngine.setPumpMechanicalPowerWatts(0.0);
        mEngine.setBraking(false);
        setLoopStatus(reason);
    }

    protected void spillCoolantFluid(World world, LoopSegment rupturedSegment) {
        CoolantPumpFluidSafety.spillCoolantFluid(this, world, rupturedSegment);
    }

    public static boolean isMetaGeneratedTool(Item item) {
        if (item == null) return false;
        if (item instanceof MetaGeneratedTool) {
            return true;
        }
        Class<?> clazz = item.getClass();
        while (clazz != null && clazz != Object.class) {
            if (clazz.getName()
                .endsWith("MetaGeneratedTool")) {
                return true;
            }
            clazz = clazz.getSuperclass();
        }

        return false;
    }

    public static boolean isValidRotor(ItemStack aStack) {
        if (aStack == null || aStack.getItem() == null) return false;
        if (isMetaGeneratedTool(aStack.getItem())) {
            int damage = aStack.getItemDamage();
            return damage >= 170 && damage <= 179;
        }
        return false;
    }

    public boolean hasImpeller() {
        return hasRotor();
    }

    public boolean checkAndLoadImpellerFromInputBus() {
        return CoolantPumpImpeller.checkAndLoadImpellerFromInputBus(this);
    }

    public ItemStack getRotor() {
        ItemStack controller = getControllerSlot();
        if (isValidRotor(controller)) {
            return controller;
        }
        if (mRotorStack != null) {
            if (isValidRotor(mRotorStack)) {
                return mRotorStack;
            }
            if (getBaseMetaTileEntity() == null || getBaseMetaTileEntity().getWorld() == null) {
                return mRotorStack;
            }
        }
        if (checkAndLoadImpellerFromInputBus()) {
            ItemStack loaded = getControllerSlot();
            if (isValidRotor(loaded)) {
                return loaded;
            }
            return mRotorStack;
        }
        return null;
    }

    ItemStack mCachedRotorObj = null;
    int mCachedRotorDamage = -1;

    public boolean updateRotorEfficiency() {
        return CoolantPumpImpeller.updateRotorEfficiency(this);
    }

    public void setRotorStack(ItemStack stack) {
        this.mRotorStack = stack;
        this.mCachedRotorObj = stack;
        this.mCachedRotorDamage = stack != null ? stack.getItemDamage() : -1;
        this.mRotorMaterial = RotorThermalHelper.getRotorMaterial(stack);
        if (stack != null && isMetaGeneratedTool(stack.getItem())) {
            TurbineStatCalculator calc = new TurbineStatCalculator((MetaGeneratedTool) stack.getItem(), stack);
            this.mRotorEfficiency = calc.getBaseEfficiency();

        }
    }

    public ItemStack getRotorStack() {
        return getRotor();
    }

    public double getRotorEfficiency() {
        return mRotorEfficiency;
    }

    @Override
    public Materials getRotorMaterial() {
        if (mRotorMaterial != null) return mRotorMaterial;
        ItemStack r = getRotor();
        if (r != null) {
            mRotorMaterial = RotorThermalHelper.getRotorMaterial(r);
        }
        return mRotorMaterial;
    }

    public void setRotorMaterial(Materials mat) {
        this.mRotorMaterial = mat;
    }

    public void setSimulatedFluidTemperatureCelsius(Double temp) {
        this.mSimulatedFluidTempCelsius = temp;
    }

    public Double getSimulatedFluidTemperatureCelsius() {
        return this.mSimulatedFluidTempCelsius;
    }

    @Override
    public double getPumpFluidTemperatureCelsius() {
        if (mSimulatedFluidTempCelsius != null) {
            return mSimulatedFluidTempCelsius;
        }
        if (mEngine != null && mEngine.getSegments() != null
            && !mEngine.getSegments()
                .isEmpty()) {
            List<LoopSegment> segs = mEngine.getSegments();
            LoopSegment suctionSeg = segs.get(segs.size() - 1);
            if (suctionSeg != null) {
                return suctionSeg.getCurrentTemperatureCelsius();
            }
        }
        return getBiomeTemperatureCelsius();
    }

    @Override
    public double getHomologousTemperature() {
        return RotorThermalHelper.calculateHomologousTemperature(getPumpFluidTemperatureCelsius(), getRotorMaterial());
    }

    @Override
    public double getThermalWearMultiplier() {
        return RotorThermalHelper.calculateThermalWearMultiplier(getHomologousTemperature());
    }

    @Override
    public double getRotorMeltingPointCelsius() {
        return RotorThermalHelper.getMeltingPointCelsius(getRotorMaterial());
    }

    @Override
    public double getRotorSofteningCelsius() {
        return RotorThermalHelper.getThermalSofteningCelsius(getRotorMaterial());
    }

    @Override
    public boolean isCorrectMachinePart(ItemStack aStack) {
        return isValidRotor(aStack);
    }

    @Override
    public int getDamageToComponent(ItemStack aStack) {
        return 1;
    }

    @Override
    public int getMaxEfficiency(ItemStack aStack) {
        if (aStack == null) return 10000;
        if (isValidRotor(aStack)) return 10000;
        return 0;
    }

    public void failOnLiquidNuclearFuel(String fluidName) {
        destroyImpeller();
        mLoopState = LoopState.STOPPED;
        mEngine.setPumpPowered(false);
        mEngine.setPumpMechanicalPowerWatts(0.0);
        mEngine.setBraking(false);
        // Do NOT abruptly zero flow rate! Fluid continues circulating through inertia and slowing with drag.
        String name = fluidName != null ? fluidName : "liquid nuclear fuel";
        setLoopStatusKey("coolantloops.pump.status.catastrophic_fuel", name);
    }

    public void failOnImpellerMelting(double fluidTemp, Materials mat) {
        destroyImpeller();
        mLoopState = LoopState.STOPPED;
        mEngine.setPumpPowered(false);
        mEngine.setPumpMechanicalPowerWatts(0.0);
        mEngine.setBraking(false);
        // Do NOT abruptly zero flow rate! Fluid continues circulating through inertia and slowing with drag.
        String matName = mat != null ? RotorThermalHelper.getMaterialLocalizedName(mat) : "impeller";
        double meltC = RotorThermalHelper.getMeltingPointCelsius(mat);
        setLoopStatusKey("coolantloops.pump.status.catastrophic_melt", fluidTemp, meltC, matName);
    }

    public void destroyImpeller() {
        CoolantPumpImpeller.destroyImpeller(this);
    }

    double mRotorDamageAccumulator = 0.0;

    public double getRotorDamageAccumulator() {
        return mRotorDamageAccumulator;
    }

    public void setRotorDamageAccumulator(double accumulator) {
        this.mRotorDamageAccumulator = accumulator;
    }

    public void degradeRotor(long euPerTick) {
        CoolantPumpImpeller.degradeRotor(this, euPerTick);
    }

    public CoolantLoopEngine getEngine() {
        return mEngine;
    }

    public boolean isLoopFormed() {
        return mLoopFormed;
    }

    public void setLoopFormed(boolean formed) {
        this.mLoopFormed = formed;
    }

    @Override
    public LocalizedText getLoopStatusMessage() {
        return mLoopStatus;
    }

    /** Sets the status line to a lang key and its arguments; an unchanged line keeps the same object. */
    public void setLoopStatusKey(String key, Object... args) {
        LocalizedText next = LocalizedText.of(key, args);
        if (!next.equals(mLoopStatus)) mLoopStatus = next;
    }

    @Override
    public boolean isCoolantFlowing() {
        return mLoopState == LoopState.CIRCULATING && mEngine.getVolumetricFlowRate() > 1e-9;
    }

    public LoopState getLoopState() {
        return mLoopState;
    }

    public void setLoopState(LoopState state) {
        this.mLoopState = state;
    }

    public long getCurrentFillLiters() {
        return mCurrentFillLiters;
    }

    public void setCurrentFillLiters(long liters) {
        this.mCurrentFillLiters = liters;
    }

    public long getRequiredFillLiters() {
        return mRequiredFillLiters;
    }

    public void setRequiredFillLiters(long liters) {
        this.mRequiredFillLiters = liters;
    }

    public void setFillRateLitersPerTick(long litersPerTick) {
        this.mFillRateLitersPerTick = litersPerTick;
    }

    @Override
    public double getFillFraction() {
        if (mRequiredFillLiters > 0) {
            return Math.min(1.0, (double) mCurrentFillLiters / mRequiredFillLiters);
        }
        return mLoopFormed ? 1.0 : 0.0;
    }

    @Override
    public String getLoopStateName() {
        return mLoopState != null ? mLoopState.name() : "STOPPED";
    }

    @Override
    public boolean isFlowing() {
        return mLoopState == LoopState.CIRCULATING || mLoopState == LoopState.DECELERATING;
    }

    @Override
    public double getMaxFlowRateLitersPerSecond() {
        return mEngine != null ? mEngine.getMaxFlowRateLitersPerSecond() : 0.0;
    }

    @Override
    public double getFlowRateLitersPerSecond() {
        return mEngine != null ? mEngine.getFlowRateLitersPerSecond() : 0.0;
    }

    @Override
    public double getPumpMechanicalPressureBar() {
        return mEngine != null ? mEngine.getPumpHeadPressureBar() : 0.0;
    }

    @Override
    public double getPeakLoopPressureBar() {
        return mEngine != null ? mEngine.getPeakLoopPressureBar() : 1.0;
    }

    @Override
    public double getPeakLoopTempCelsius() {
        return mEngine != null ? mEngine.getPeakLoopTempCelsius() : 20.0;
    }

    @Override
    public boolean hasRotor() {
        return getRotor() != null;
    }

    @Override
    public int getRotorDamage() {
        ItemStack rotor = getRotor();
        if (rotor == null) return 0;
        if (rotor.getItem() instanceof MetaGeneratedTool) {
            return (int) Math.min(Integer.MAX_VALUE, MetaGeneratedTool.getToolDamage(rotor));
        }
        return rotor.getItemDamage();
    }

    @Override
    public int getRotorMaxDamage() {
        ItemStack rotor = getRotor();
        if (rotor == null) return 0;
        if (rotor.getItem() instanceof MetaGeneratedTool) {
            return (int) Math.min(Integer.MAX_VALUE, MetaGeneratedTool.getToolMaxDamage(rotor));
        }
        return rotor.getMaxDamage();
    }

    @Override
    public double getRotorDurabilityPercent() {
        ItemStack rotor = getRotor();
        if (rotor != null) {
            if (rotor.getItem() instanceof MetaGeneratedTool) {
                long max = MetaGeneratedTool.getToolMaxDamage(rotor);
                if (max > 0) {
                    long dmg = MetaGeneratedTool.getToolDamage(rotor);
                    return Math.max(0.0, 100.0 * (1.0 - (double) dmg / max));
                }
            } else if (rotor.getMaxDamage() > 0) {
                return Math.max(0.0, 100.0 * (1.0 - (double) rotor.getItemDamage() / rotor.getMaxDamage()));
            }
        }
        return 0.0;
    }

    @Override
    public long getPowerConsumptionEU() {
        if (mPowerFailed || !isAllowedToWork()) {
            return 0L;
        }
        if (mLoopState == LoopState.CIRCULATING || mLoopState == LoopState.FILLING) {
            return getRequiredPowerDrawEU();
        }
        if (mLoopState == LoopState.DECELERATING && (mEngine == null || mEngine.getVolumetricFlowRate() > 1e-5)) {
            return getRequiredPowerDrawEU();
        }
        return 0L;
    }

    @Override
    public double getPumpMechanicalPowerWatts() {
        return mEngine != null ? mEngine.getPumpMechanicalPowerWatts() : 0.0;
    }

    @Override
    public long getReservoirCapacityLiters() {
        return mReservoir != null ? mReservoir.getTotalTankCapacityLiters() : 0L;
    }

    @Override
    public boolean checkRecipe(ItemStack aStack) {
        return mLoopFormed;
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
    public boolean hasRunningText() {
        return mLoopFormed && super.hasRunningText();
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

    /**
     * Drains fluid from the Railcraft tank multiblock reservoir directly above the pump.
     */
    public int drainFromReservoir(int liters) {
        return mReservoir.drainFromReservoir(liters);
    }

    /**
     * Returns fluid from the coolant loop back into the Railcraft tank reservoir directly above the pump.
     */
    public int fillIntoReservoir(int liters) {
        return mReservoir.fillIntoReservoir(liters);
    }

    /**
     * Checks if the Railcraft tank reservoir directly above the pump is full.
     */
    public boolean isReservoirFull() {
        return mReservoir.isReservoirFull();
    }

    @Override
    public void consumeCoolant(long liters) {
        if (liters <= 0) return;
        drainFromReservoir((int) Math.min(Integer.MAX_VALUE, liters));
    }

    @Override
    public void addDissolvedGas(String gasName, long liters) {
        mReservoir.addDissolvedGas(gasName, liters);
    }

    @Override
    public double getDissolvedGasFraction() {
        return mReservoir.getDissolvedGasFraction();
    }

    @Override
    public double getDissolvedGasPercentage() {
        return mReservoir.getDissolvedGasPercentage();
    }

    @Override
    public long getTotalDissolvedGasLiters() {
        return mReservoir.getTotalDissolvedGasLiters();
    }

    @Override
    public long getDissolvedGasAmount(String gasName) {
        return mReservoir.getDissolvedGasAmount(gasName);
    }

    @Override
    public Map<String, Long> getDissolvedGases() {
        return mReservoir.getDissolvedGases();
    }

    @Override
    public synchronized long extractDissolvedGas(String gasName, long maxLiters) {
        return mReservoir.extractDissolvedGas(gasName, maxLiters);
    }

    @Override
    public long getTotalCoolantLiters() {
        return mReservoir.getTotalCoolantLiters();
    }

    @Override
    public CoolantFluidProperty getCoolantFluidProperty() {
        return mEngine != null ? mEngine.getFluid() : CoolantFluidProperty.DISTILLED_WATER;
    }

    @Override
    public Materials getTankMaterial() {
        return mTankMaterial;
    }

    public void setTankMaterial(Materials mat) {
        this.mTankMaterial = mat;
    }

    public static Materials getRailcraftTankMaterial(TileEntity te) {
        return CoolantPumpReservoir.getRailcraftTankMaterial(te);
    }

    /** GregTech's scanner takes finished lines, as its own are: they are formatted in the game's language here. */
    @Override
    public String[] getInfoData() {
        String tankMatName = mTankMaterial != null ? mTankMaterial.mDefaultLocalName : "Unknown";
        double maxFlowRateLPerSec = mEngine.getMaxFlowRateLitersPerSecond();
        String maxFlowStr = (maxFlowRateLPerSec == Double.MAX_VALUE)
            ? ClientText.get("gt.scanner.coolant_pump.max_pipe_flow.unlimited")
            : String.format("%.1f L/s", maxFlowRateLPerSec);
        String hatchState = isDischargeHatchDisabled()
            ? ClientText.get("gt.scanner.coolant_pump.discharge_hatch.disabled")
            : ClientText.get("gt.scanner.coolant_pump.discharge_hatch.enabled");
        Materials rMat = getRotorMaterial();
        String impellerStr;
        String thermalWearStr;
        if (rMat != null || hasRotor()) {
            String matName = rMat != null ? RotorThermalHelper.getMaterialLocalizedName(rMat) : "Unknown";
            double meltC = RotorThermalHelper.getMeltingPointCelsius(rMat);
            impellerStr = ClientText
                .format("gt.scanner.coolant_pump.impeller", matName, mRotorEfficiency * 100.0, meltC);
            double th = getHomologousTemperature();
            double thermalWear = getThermalWearMultiplier();
            String softeningActive = th >= 0.5 ? ClientText.get("gt.scanner.coolant_pump.impeller_thermal.softening")
                : "";
            thermalWearStr = ClientText
                .format("gt.scanner.coolant_pump.impeller_thermal_wear", thermalWear, th, softeningActive);
        } else {
            impellerStr = ClientText.get("gt.scanner.coolant_pump.impeller.none");
            thermalWearStr = ClientText.format("gt.scanner.coolant_pump.impeller_thermal_wear", 1.0, 0.0, "");
        }

        String loopStatusStr = mLoopFormed
            ? ClientText.format(
                "gt.scanner.coolant_pump.loop_active",
                mEngine.getSegments()
                    .size(),
                mLoopDevices.size())
            : ClientText.format("gt.scanner.coolant_pump.loop_incomplete", ClientText.of(mLoopStatus));

        return new String[] { ClientText.format("gt.scanner.coolant_pump.state", mLoopState.name()),
            ClientText.format("gt.scanner.coolant_pump.discharge_hatch", hatchState),
            ClientText.format("gt.scanner.coolant_pump.tank_material", tankMatName),
            ClientText.format("gt.scanner.coolant_pump.current_flow", mEngine.getFlowRateLitersPerSecond()),
            ClientText.format("gt.scanner.coolant_pump.max_pipe_flow", maxFlowStr),
            ClientText.format("gt.scanner.coolant_pump.peak_pressure", mEngine.getPeakLoopPressureBar()),
            ClientText.format("gt.scanner.coolant_pump.peak_temp", mEngine.getPeakLoopTempCelsius()),
            ClientText.format("gt.scanner.coolant_pump.loop_fluid", mCurrentFillLiters, mRequiredFillLiters),
            ClientText.format("gt.scanner.coolant_pump.dissolved_gas", getDissolvedGasFraction() * 100.0), impellerStr,
            thermalWearStr, loopStatusStr };
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        if (player != null) {
            super.getWailaNBTData(player, tile, tag, world, x, y, z);
        }
        tag.setBoolean("loopFormed", mLoopFormed);
        tag.setTag("loopStatus", LocalizedTextNbt.write(mLoopStatus));
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currentTip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        final NBTTagCompound tag = accessor.getNBTData();
        if (tag.getBoolean("incompleteStructure")) {
            super.getWailaBody(itemStack, currentTip, accessor, config);
            return;
        }
        if (!tag.getBoolean("loopFormed")) {
            String status = ClientText.of(LocalizedTextNbt.read(tag.getCompoundTag("loopStatus")));
            if (status.isEmpty()) {
                status = ClientText.get("coolantloops.pump.status.waiting");
            }
            currentTip.add(EnumChatFormatting.RED + ClientText.format("coolantloops.waila.loop_not_formed", status));
            return;
        }
        super.getWailaBody(itemStack, currentTip, accessor, config);
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        CoolantPumpNbt.save(this, aNBT);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        CoolantPumpNbt.load(this, aNBT);
    }

    public boolean addBottomHatch(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity == null) return false;
        if (aMetaTileEntity instanceof MTEHatchPressurizedFluid) {
            MTEHatchPressurizedFluid ph = (MTEHatchPressurizedFluid) aMetaTileEntity;
            ph.updateTexture(aBaseCasingIndex);
            if (getBaseMetaTileEntity() != null) {
                ph.updateCraftingIcon(getMachineCraftingIcon());
            }
            if (ph.isOutput()) {
                if (mDischargeHatch == null) {
                    mDischargeHatch = ph;
                    return true;
                }
                return false;
            } else if (ph.isInput()) {
                if (mSuctionHatch == null) {
                    mSuctionHatch = ph;
                    return true;
                }
                return false;
            }
            return false;
        }
        if (aMetaTileEntity instanceof MTEHatchMaintenance) {
            return addMaintenanceToMachineList(aTileEntity, aBaseCasingIndex);
        }
        if (aMetaTileEntity instanceof MTEHatchEnergy) {
            if (!MTEPressurizedHeatExchanger.isSingleAmpEnergyHatch((MTEHatchEnergy) aMetaTileEntity)) {
                return false;
            }
            return addEnergyInputToMachineList(aTileEntity, aBaseCasingIndex);
        }
        if (aMetaTileEntity instanceof MTEHatchInputBus) {
            if (mInputBusses.size() >= 1) {
                return false;
            }
            if (aMetaTileEntity.getBaseMetaTileEntity() == null) {
                aMetaTileEntity.setBaseMetaTileEntity(aTileEntity);
            }
            return addInputBusToMachineList(aTileEntity, aBaseCasingIndex);
        }
        return false;
    }

    public static Materials getMaterialForTier(int tier) {
        return CoolantPumpStructure.getMaterialForTier(tier);
    }

    public static Materials getMaterialFromTrigger(ItemStack trigger) {
        return CoolantPumpStructure.getMaterialFromTrigger(trigger);
    }

    public static int getWidthFromTrigger(ItemStack trigger) {
        return CoolantPumpStructure.getWidthFromTrigger(trigger);
    }

    public static int getHeightFromTrigger(ItemStack trigger) {
        return CoolantPumpStructure.getHeightFromTrigger(trigger);
    }

    public static ItemStack getTankWallItem(Materials mat) {
        return CoolantPumpStructure.getTankWallItem(mat);
    }

    public static ItemStack getTankValveItem(Materials mat) {
        return CoolantPumpStructure.getTankValveItem(mat);
    }

    public static ItemStack getTankGaugeItem(Materials mat) {
        return CoolantPumpStructure.getTankGaugeItem(mat);
    }

    public static IStructureElement<MTECoolantPump> ofCornerFrame() {
        return CoolantPumpStructure.ofCornerFrame();
    }

    public static IStructureElement<MTECoolantPump> ofTankWall() {
        return CoolantPumpStructure.ofTankWall();
    }

    public static String[] makePumpBaseShape0(int width) {
        return CoolantPumpStructure.makePumpBaseShape0(width);
    }

    public static String[] makePumpBaseShape1(int width) {
        return CoolantPumpStructure.makePumpBaseShape1(width);
    }

    public static String[] makeTankBottomShape(int width) {
        return CoolantPumpStructure.makeTankBottomShape(width);
    }

    public static String[] makeTankMidShape(int width) {
        return CoolantPumpStructure.makeTankMidShape(width);
    }

    public static String[] makeTankTopShape(int width) {
        return CoolantPumpStructure.makeTankTopShape(width);
    }

    @Override
    public IStructureDefinition<MTECoolantPump> getStructureDefinition() {
        return CoolantPumpStructure.getStructureDefinition();
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        int width = getWidthFromTrigger(stackSize);
        int height = getHeightFromTrigger(stackSize);
        int hOffset = width / 2;
        int dOffset = 0;

        buildPiece("pump_" + width + "_base_0", stackSize, hintsOnly, hOffset, 0, dOffset);
        buildPiece("pump_" + width + "_base_1", stackSize, hintsOnly, hOffset, 1, dOffset);
        buildPiece("tank_" + width + "_bottom", stackSize, hintsOnly, hOffset, 2, dOffset);
        for (int i = 1; i < height - 1; i++) {
            buildPiece("tank_" + width + "_mid", stackSize, hintsOnly, hOffset, 2 + i, dOffset);
        }
        buildPiece("tank_" + width + "_top", stackSize, hintsOnly, hOffset, 2 + height - 1, dOffset);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        int width = getWidthFromTrigger(stackSize);
        int height = getHeightFromTrigger(stackSize);
        int hOffset = width / 2;
        int dOffset = 0;

        int built;
        built = survivalBuildPiece(
            "pump_" + width + "_base_0",
            stackSize,
            hOffset,
            0,
            dOffset,
            elementBudget,
            env,
            false,
            true);
        if (built >= 0) return built;
        built = survivalBuildPiece(
            "pump_" + width + "_base_1",
            stackSize,
            hOffset,
            1,
            dOffset,
            elementBudget,
            env,
            false,
            true);
        if (built >= 0) return built;
        built = survivalBuildPiece(
            "tank_" + width + "_bottom",
            stackSize,
            hOffset,
            2,
            dOffset,
            elementBudget,
            env,
            false,
            true);
        if (built >= 0) return built;
        for (int i = 1; i < height - 1; i++) {
            built = survivalBuildPiece(
                "tank_" + width + "_mid",
                stackSize,
                hOffset,
                2 + i,
                dOffset,
                elementBudget,
                env,
                false,
                true);
            if (built >= 0) return built;
        }
        return survivalBuildPiece(
            "tank_" + width + "_top",
            stackSize,
            hOffset,
            2 + height - 1,
            dOffset,
            elementBudget,
            env,
            false,
            true);
    }

    @Override
    public MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gt.multiblock.coolant_pump.machine_type"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.coolant_pump.desc1"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.coolant_pump.desc2"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.coolant_pump.desc3"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.coolant_pump.desc4"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.coolant_pump.desc5"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.coolant_pump.desc6"))
            .addPerfectOCInfo()
            .addSeparator()
            .beginVariableStructureBlock(3, 9, 6, 10, 3, 9, false)
            .addController(StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.controller"))
            .addCasingInfo(
                StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.casings"),
                StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.casings_pos"),
                false)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.frames"),
                StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.frames_pos"))
            .addEnergyHatch(StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.energy"), 1)
            .addMaintenanceHatch(StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.maintenance"), 1)
            .addInputBus(StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.input_bus"), 1)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.hatch_discharge"),
                StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.hatch_discharge_pos"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.hatch_suction"),
                StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.hatch_suction_pos"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.rc_tank"),
                StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.rc_tank_pos"))
            .addSubChannel(CoolantStructureChannels.STRUCTURE_WIDTH)
            .addSubChannel(CoolantStructureChannels.STRUCTURE_HEIGHT)
            .addSubChannel(CoolantStructureChannels.TANK_MATERIAL)
            .toolTipFinisher(StatCollector.translateToLocal("gt.multiblock.coolantloops.finisher"));
        return tt;
    }
}
