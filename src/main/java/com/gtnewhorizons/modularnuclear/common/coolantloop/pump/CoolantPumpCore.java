package com.gtnewhorizons.modularnuclear.common.coolantloop.pump;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.modularnuclear.common.config.CoolantLoopDataRegistry;
import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantFluidProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantLoopEngine;
import com.gtnewhorizons.modularnuclear.common.coolantloop.ICoolantLoopDevice;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopChunkLoader;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopFailure;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopGraphCrawler;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopSegment;
import com.gtnewhorizons.modularnuclear.common.gui.graph.CoolantLoopGraphData;
import com.gtnewhorizons.modularnuclear.common.util.LocalizedText;
import com.gtnewhorizons.modularnuclear.common.util.StructureChunks;
import com.gtnewhorizons.modularnuclear.common.util.TickPhase;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTLog;
import gregtech.api.util.shutdown.ShutDownReason;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;

/*-
Note [Pump cores and their hosts] (generated)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Two machines own coolant loops: the multiblock MTECoolantPump and the singleblock MTECompactLoopPump.  GregTech gives
them different base classes, so what they share can't be a superclass.  It is this class instead: a CoolantPumpCore
holds a loop's state (its engine, devices, fill and crawl) and runs it, and a machine that owns one implements
ICoolantPumpHost to be its host.

The core.  Everything about running a loop: crawling it (crawlLoopGraph, applyCrawlResult), binding its devices,
keeping its chunks loaded, noticing breaks, filling and draining, the liquid and gas circulation steps
(CoolantPumpCirculation, CoolantPumpGasLoop, CoolantPumpFillDrain), failures, and saving all of that
(CoolantPumpNbt).  Its helpers in this package take the core.

The host.  What differs between machines: where the loop leaves and returns (getDischargePort, getSuctionPort),
the reservoir's tank (createReservoir), power (drainEnergy, getRequiredPowerDrawEU), whether the machine may
work, its own structure, the pipe material it demands (getTankMaterial, null for any), an impeller for liquid loops
(getImpeller, null for a pump that only charges gas loops), and its limits (PumpLimits).  ICoolantPumpHost's
default methods pass the core's public methods through, so a host is also the ICoolantLoopPump its devices see.

The host's tick calls the core's: onPostTick, onFirstTick, onRemoval and onUnload.
*/
/** A pump's loop and the running of it. See Note [Pump cores and their hosts] */
public class CoolantPumpCore {

    public final ICoolantPumpHost host;

    public CoolantPumpCore(ICoolantPumpHost host) {
        this.host = host;
        this.mReservoir = host.createReservoir();
    }

    public IGregTechTileEntity getBaseMetaTileEntity() {
        return host.getBaseMetaTileEntity();
    }

    public boolean isAllowedToWork() {
        return host.isAllowedToWork();
    }

    public boolean drainEnergy(long eu) {
        return host.drainEnergy(eu);
    }

    public long getRequiredPowerDrawEU() {
        return host.getRequiredPowerDrawEU();
    }

    public double getBiomeTemperatureCelsius() {
        return host.getBiomeTemperatureCelsius();
    }

    public boolean isDischargeHatchDisabled() {
        return host.isDischargeHatchDisabled();
    }

    /** The host's tick, server side. */
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        int phase = schedulePhase();
        if (mNeedsPostLoadRebind) {
            rebindRestoredLoop(aBaseMetaTileEntity);
        }
        if (mActiveCrawl != null) {
            stepActiveCrawl();
        } else if (!mLoopFormed && host.isPumpStructureFormed() && TickPhase.due(aTick, phase, 100)) {
            crawlLoopGraph();
        }
        if (mLoopFormed && !mLoopAtRest && TickPhase.due(aTick, phase, LOOP_CHECK_INTERVAL_TICKS)) {
            LocalizedText loopBreak = findLoopBreak(aBaseMetaTileEntity.getWorld());
            if (loopBreak != null) onDeviceDisconnected(null, loopBreak);
        }
        if (mLoopFormed && !mLoopAtRest
            && mActiveCrawl == null
            && TickPhase.due(aTick, phase, LOOP_RECRAWL_INTERVAL_TICKS)) {
            crawlLoopGraph();
        }
        stepCoolantLoop();
        // Saves the loop's changing state; markDirty also updates comparators around the pump, so not every second
        if (mLoopFormed && TickPhase.due(aTick, phase, 100)) {
            aBaseMetaTileEntity.markDirty();
        }
    }

    /** Before the host's structure forms on its first tick: loads a saved loop's chunks. */
    public void beforeFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        if (mNeedsPostLoadRebind) loadLoopChunks(aBaseMetaTileEntity.getWorld());
    }

    /** After the host's structure formed on its first tick: binds a saved loop again. */
    public void afterFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        if (mNeedsPostLoadRebind) rebindRestoredLoop(aBaseMetaTileEntity);
    }

    public void onRemoval() {
        releaseLoopDevices();
        releaseLoopChunks();
    }

    public void onUnload() {
        releaseLoopDevices();
        // The chunk tickets stay: they are saved with the world and bring the loop back loaded
    }

    public void onEnableWorking() {
        mPowerFailed = false;
    }

    /** A screwdriver on the pump: on a gas loop, the next charge setting. False on a liquid loop. */
    public boolean cycleGasCharge(EntityPlayer player) {
        if (CoolantPumpGasLoop.loopGas(this, getReservoirFluid()) == null) return false;
        CoolantPumpGasLoop.cycleCharge(this, player);
        return true;
    }

    public void saveNBTData(NBTTagCompound aNBT) {
        CoolantPumpNbt.save(this, aNBT);
    }

    public void loadNBTData(NBTTagCompound aNBT) {
        CoolantPumpNbt.load(this, aNBT);
    }

    // Simulation Engine
    public static class DeviceSegmentBinding {

        public final ICoolantLoopDevice device;
        public final LoopSegment segment;

        public DeviceSegmentBinding(ICoolantLoopDevice device, LoopSegment segment) {
            this.device = device;
            this.segment = segment;
        }
    }

    public CoolantLoopEngine mEngine = new CoolantLoopEngine(CoolantFluidProperty.DISTILLED_WATER);
    public List<ICoolantLoopDevice> mLoopDevices = new ArrayList<>();
    public final List<DeviceSegmentBinding> mDeviceBindings = new ArrayList<>();
    public long mLastSyncedGasVersion = -1L;
    public boolean mLoopFormed = false;
    /**
     * The blocks the formed loop runs through (pipes and devices), as they were when it formed; rebuilt lazily after
     * a load. A loop whose blocks change no longer exists.
     */
    public int[] mFootprint;
    public static final long STRUCTURE_SETTLE_TICKS = 200;
    /** How often (ticks) a formed loop checks that its blocks are still there; the check walks the whole footprint. */
    private static final int LOOP_CHECK_INTERVAL_TICKS = 100;
    /**
     * How often (ticks) a formed loop is crawled again. The loop check sees blocks change, not a device dropping its
     * binding to the pump (a reactor's passages, say), which only a crawl restores.
     */
    private static final int LOOP_RECRAWL_INTERVAL_TICKS = 200;
    public Block[] mFootprintBlocks;
    /** The status line, as a lang key and its arguments: each client localizes it. */
    public LocalizedText mLoopStatus = LocalizedText.of("modularnuclear.coolant_loop.pump.status.waiting");

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
    public LoopState mLoopState = LoopState.EMPTY;
    public long mCurrentFillLiters = 0L;
    public long mRequiredFillLiters = 0L;
    public long mFillRateLitersPerTick = (CoolantLoopDataRegistry.getPhysicsData() != null)
        ? CoolantLoopDataRegistry.getPhysicsData()
            .getFillRateLitersPerTick()
        : 1000L;
    /**
     * Running total of the fill volume of the loop's pipes (in crawl order) then devices, so filling can go part by
     * part; null until the loop is crawled.
     */
    public long[] mFillOrderCumulative = null;
    // The stopped loop can change without harm until it restarts. See Note [Changing a stopped coolant loop]
    public boolean mLoopAtRest = false;
    /** The crawl under way is the one a restart waits for. */
    public boolean mRestartCrawlRunning = false;

    // Reservoir management
    public final CoolantPumpReservoir mReservoir;

    public boolean mPowerFailed = false;

    // Multi-tick loop crawling state
    public long mLastCrawlStartTime = -1L;
    public long mLastCrawlCompleteTime = -1L;
    public LoopGraphCrawler.CrawlSession mActiveCrawl = null;
    public boolean mNeedsPostLoadRebind = false;
    /** The next crawl is of a loop that was formed, so the loop keeps its state if the crawl finds it unchanged. */
    public boolean mRecrawlKeepsState = false;
    /** Chunk tickets keeping the formed loop's chunks loaded. */
    public List<ForgeChunkManager.Ticket> mChunkTickets = new ArrayList<>();
    /** The chunks the formed loop occupies, as chunkXZ2Int keys; saved, so a load can load them all at once. */
    public Set<Long> mLoopChunks = new HashSet<>();

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
    public LoopGraphCrawler.CrawlResult mLastCrawlResult = null;

    public LoopGraphCrawler.CrawlResult getLastCrawlResult() {
        return mLastCrawlResult;
    }

    public String getLoopGraphDataJson() {
        return CoolantLoopGraphData.buildFrom(host)
            .toJson();
    }

    public boolean isPowerFailed() {
        if (getBaseMetaTileEntity() != null && getBaseMetaTileEntity().wasShutdown()) {
            return getBaseMetaTileEntity().getLastShutDownReason() == ShutDownReasonRegistry.POWER_LOSS;
        }
        return mPowerFailed;
    }

    public void setLoopStatus(LocalizedText loopStatus) {
        mLoopStatus = loopStatus != null ? loopStatus : LocalizedText.EMPTY;
    }

    public CoolantPumpReservoir getReservoir() {
        return mReservoir;
    }

    public void addLoopDevice(ICoolantLoopDevice dev) {
        if (dev != null && !mLoopDevices.contains(dev)) {
            mLoopDevices.add(dev);
            dev.setLoopPump(host);
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
            CoolantPumpGasLoop.bindStages(this);
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
        CoolantPumpGasLoop.bindStages(this);
    }

    /** The bindings as they are, without rebuilding them. */
    public List<DeviceSegmentBinding> getDeviceBindingsRaw() {
        return mDeviceBindings;
    }

    /** Which charge a gas loop takes. See Note [The pump on a gas loop] */
    public int mGasChargeSetting = 0;

    public List<DeviceSegmentBinding> getDeviceBindings() {
        if (mDeviceBindings.isEmpty() && !mLoopDevices.isEmpty()) {
            rebuildDeviceBindings();
        }
        return mDeviceBindings;
    }

    /** Flow through a device's segment: the loop flow times the share of it that reaches that branch. */
    public static double branchFlow(double loopFlowM3s, LoopSegment segment) {
        return segment != null ? loopFlowM3s * segment.getFlowFraction() : loopFlowM3s;
    }

    public List<ICoolantLoopDevice> getLoopDevices() {
        return mLoopDevices;
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

        LoopPort discharge = host.getDischargePort();
        LoopPort suction = host.getSuctionPort();
        if (discharge == null || suction == null) {
            mActiveCrawl = null;
            mRecrawlKeepsState = false;
            releaseLoopChunks();
            mLoopFormed = false;
            mRestartCrawlRunning = false;
            if (mLoopAtRest) {
                mLoopState = LoopState.STOPPED;
            } else {
                mLoopState = LoopState.EMPTY;
                mCurrentFillLiters = 0L;
            }
            setLoopStatusKey("modularnuclear.coolant_loop.pump.status.req_hatches");
            return false;
        }

        mLastCrawlStartTime = (te != null) ? te.getTimer() : System.currentTimeMillis();
        LoopGraphCrawler crawler = new LoopGraphCrawler(te.getWorld(), LoopGraphCrawler.DEFAULT_MAX_STEPS);
        mActiveCrawl = crawler.startSession(
            discharge.x,
            discharge.y,
            discharge.z,
            discharge.side,
            suction.x,
            suction.y,
            suction.z,
            suction.side,
            new LoopGraphCrawler.BlockPosCoord(te.getXCoord(), te.getYCoord(), te.getZCoord()),
            host,
            host.getTankMaterial());

        mActiveCrawl.step(LoopGraphCrawler.STEPS_PER_TICK);

        if (mActiveCrawl.isDone()) {
            mLastCrawlCompleteTime = (te != null) ? te.getTimer() : System.currentTimeMillis();
            LoopGraphCrawler.CrawlResult result = mActiveCrawl.getResult();
            mActiveCrawl = null;
            return applyCrawlResult(result);
        } else if (!mLoopFormed) {
            setLoopStatusKey(
                "modularnuclear.coolant_loop.pump.status.crawling",
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
                "modularnuclear.coolant_loop.pump.status.crawling",
                mActiveCrawl.getStepCount(),
                mActiveCrawl.getTicksElapsed());
        }
    }

    public boolean applyCrawlResult(LoopGraphCrawler.CrawlResult result) {
        return CoolantPumpLoopBinding.applyCrawlResult(this, result);
    }

    /** This pump's offset for its periodic checks, so pumps loaded together do not crawl on the same tick. */
    private int mSchedulePhase = -1;

    public int schedulePhase() {
        if (mSchedulePhase < 0 && getBaseMetaTileEntity() != null) {
            mSchedulePhase = TickPhase.of(getBaseMetaTileEntity());
        }
        return Math.max(mSchedulePhase, 0);
    }

    /**
     * Loads every chunk a saved loop occupies, so the loop can be restored on the pump's first tick instead of
     * waiting for its chunks. It runs in a tick, never while a chunk is loading, so it cannot recurse into loads.
     * Chunk tickets normally have them loaded already; this covers a loop saved without them.
     */
    void loadLoopChunks(World world) {
        if (!(world instanceof WorldServer ws)) return;
        Set<Long> chunks = new HashSet<>(mLoopChunks);
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) host.collectPumpChunks(chunks, true);
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

    public void rebindRestoredLoop(IGregTechTileEntity aBaseMetaTileEntity) {
        CoolantPumpLoopBinding.rebindRestoredLoop(this, aBaseMetaTileEntity);
    }

    public boolean triggerPowerfail() {
        mPowerFailed = true;
        host.stopMachine(ShutDownReasonRegistry.POWER_LOSS);
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

    // A stopped loop holding coolant comes to rest. See Note [Changing a stopped coolant loop]
    public void noteLoopAtRest() {
        if (mCurrentFillLiters <= 0L) {
            mLoopAtRest = false;
        } else if (mEngine.getVolumetricFlowRate() == 0.0) {
            mLoopAtRest = true;
        }
    }

    public boolean isLoopAtRest() {
        return mLoopAtRest;
    }

    public boolean stepCoastingAndCheckRupture() {
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
                double q = b.segment != null ? mEngine.getLocalVolumetricFlow(b.segment) : flowRate;
                b.device.processThermalExchange(q, 0.05, mEngine.getFluid(), b.segment);
            }
        }
    }

    /** The devices of the current loop stop pointing at this pump, so a removed pump is not kept alive by them. */
    public void releaseLoopDevices() {
        for (ICoolantLoopDevice dev : mLoopDevices) {
            if (dev != null) dev.setLoopPump(null);
        }
        mLoopDevices = new ArrayList<>();
        mDeviceBindings.clear();
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
    public void updateLoopChunks() {
        CoolantPumpLoopBinding.updateLoopChunks(this);
    }

    public void releaseLoopChunks() {
        LoopChunkLoader.release(mChunkTickets);
        mChunkTickets = new ArrayList<>();
        mLoopChunks = new HashSet<>();
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null && base.getWorld() != null && !base.getWorld().isRemote) {
            LoopChunkLoader.releaseRestored(base.getWorld(), base.getXCoord(), base.getYCoord(), base.getZCoord());
        }
    }

    /** Records the loop's pipe blocks; none after a load, until the first crawl finds them again. */
    public void captureFootprint(World world) {
        CoolantPumpLoopBinding.captureFootprint(this, world);
    }

    public void onDeviceDisconnected(ICoolantLoopDevice device, LocalizedText reason) {
        CoolantPumpLoopBinding.onDeviceDisconnected(this, device, reason);
    }

    /** How a part of {@link #stepCoolantLoop} ended: carry on with the step, or end it as done or failed. */
    public enum Step {

        CARRY_ON,
        DONE,
        FAILED;

        static Step of(boolean done) {
            return done ? DONE : FAILED;
        }
    }

    public boolean stepCoolantLoop() {
        return CoolantPumpCirculation.step(this);
    }

    /** Pipes or devices the pump fills per second at least, however large they are. */
    static final long FILL_PARTS_PER_SECOND = 4L;

    /**
     * Litres per tick the loop fills from (or drains back into) the reservoir: the configured fill rate, but never
     * slower than four pipes (or devices) per second, taking them in crawl order. Filling is not limited by the loop's
     * flow: a loop of large high-tier pipes, each of which takes its whole capacity, would otherwise take hours.
     */
    public long currentFillRateLitersPerTick() {
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

    /** The engine's rupture as a status line. */
    public LocalizedText engineFailureText() {
        LoopFailure f = mEngine.getFailure();
        return f != null ? LocalizedText.of(f.langKey, mEngine.getFailureArgs())
            : LocalizedText.of("modularnuclear.coolant_loop.engine.failed");
    }

    /** A GregTech shutdown reason as a status line, in each client's language. */
    public static LocalizedText shutDownText(ShutDownReason reason) {
        return LocalizedText.of("GT5U.gui.text.shutdown_reason." + reason.getKey());
    }

    public void triggerCatastrophicExplosion(LocalizedText reason) {
        triggerCatastrophicExplosion(reason, mEngine != null ? mEngine.getRupturedSegment() : null);
    }

    public void triggerCatastrophicExplosion(LocalizedText reason, LoopSegment rupturedSegment) {
        GTLog.writeExplosionLog(
            host.asMetaTileEntity(),
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

    public void spillCoolantFluid(World world, LoopSegment rupturedSegment) {
        CoolantPumpFluidSafety.spillCoolantFluid(this, world, rupturedSegment);
    }

    public double getPumpFluidTemperatureCelsius() {
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

    public void failOnLiquidNuclearFuel(String fluidName) {
        PumpImpeller impeller = host.getImpeller();
        if (impeller != null) impeller.destroyImpeller();
        mLoopState = LoopState.STOPPED;
        mEngine.setPumpPowered(false);
        mEngine.setPumpMechanicalPowerWatts(0.0);
        mEngine.setBraking(false);
        // Do NOT abruptly zero flow rate! Fluid continues circulating through inertia and slowing with drag.
        String name = fluidName != null ? fluidName : "liquid nuclear fuel";
        setLoopStatusKey("modularnuclear.coolant_loop.pump.status.catastrophic_fuel", name);
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

    public LocalizedText getLoopStatusMessage() {
        return mLoopStatus;
    }

    /** Sets the status line to a lang key and its arguments; an unchanged line keeps the same object. */
    public void setLoopStatusKey(String key, Object... args) {
        LocalizedText next = LocalizedText.of(key, args);
        if (!next.equals(mLoopStatus)) mLoopStatus = next;
    }

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

    public double getFillFraction() {
        if (mRequiredFillLiters > 0) {
            return Math.min(1.0, (double) mCurrentFillLiters / mRequiredFillLiters);
        }
        return mLoopFormed ? 1.0 : 0.0;
    }

    public String getLoopStateName() {
        return mLoopState != null ? mLoopState.name() : "STOPPED";
    }

    public boolean isFlowing() {
        return mLoopState == LoopState.CIRCULATING || mLoopState == LoopState.DECELERATING;
    }

    public double getMaxFlowRateLitersPerSecond() {
        return mEngine != null ? mEngine.getMaxFlowRateLitersPerSecond() : 0.0;
    }

    public double getFlowRateLitersPerSecond() {
        return mEngine != null ? mEngine.getFlowRateLitersPerSecond() : 0.0;
    }

    public double getPumpMechanicalPressureBar() {
        return mEngine != null ? mEngine.getPumpHeadPressureBar() : 0.0;
    }

    public double getPeakLoopPressureBar() {
        return mEngine != null ? mEngine.getPeakLoopPressureBar() : 1.0;
    }

    public double getPeakLoopTempCelsius() {
        return mEngine != null ? mEngine.getPeakLoopTempCelsius() : 20.0;
    }

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

    public double getPumpMechanicalPowerWatts() {
        return mEngine != null ? mEngine.getPumpMechanicalPowerWatts() : 0.0;
    }

    public long getReservoirCapacityLiters() {
        return mReservoir != null ? mReservoir.getTotalTankCapacityLiters() : 0L;
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

    public void consumeCoolant(long liters) {
        if (liters <= 0) return;
        drainFromReservoir((int) Math.min(Integer.MAX_VALUE, liters));
    }

    public void addDissolvedGas(String gasName, long liters) {
        mReservoir.addDissolvedGas(gasName, liters);
    }

    public double getDissolvedGasFraction() {
        return mReservoir.getDissolvedGasFraction();
    }

    public double getDissolvedGasPercentage() {
        return mReservoir.getDissolvedGasPercentage();
    }

    public long getTotalDissolvedGasLiters() {
        return mReservoir.getTotalDissolvedGasLiters();
    }

    public long getDissolvedGasAmount(String gasName) {
        return mReservoir.getDissolvedGasAmount(gasName);
    }

    public Map<String, Long> getDissolvedGases() {
        return mReservoir.getDissolvedGases();
    }

    public synchronized long extractDissolvedGas(String gasName, long maxLiters) {
        return mReservoir.extractDissolvedGas(gasName, maxLiters);
    }

    public long getTotalCoolantLiters() {
        return mReservoir.getTotalCoolantLiters();
    }

    public CoolantFluidProperty getCoolantFluidProperty() {
        return mEngine != null ? mEngine.getFluid() : CoolantFluidProperty.DISTILLED_WATER;
    }
}
