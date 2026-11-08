package com.gtnewhorizons.modularnuclear.common.metatileentity.multi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.gtnewhorizons.modularnuclear.common.coolantloop.ICoolantLoopDevice;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopChunkLoader;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopDevices;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopGraphCrawler;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopSegment;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchPressurizedFluid;
import com.gtnewhorizons.modularnuclear.common.util.LocalizedText;
import com.gtnewhorizons.modularnuclear.common.util.MultiblockStartup;
import com.gtnewhorizons.modularnuclear.common.util.StructureChunks;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/**
 * A pump's loop: applying a crawl's result, binding it again after a load, noticing when it breaks, and keeping its
 * chunks loaded.
 */
/*-
Note [Changing a stopped coolant loop] (generated)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Players expect to stop a pump, watch its flow fall to zero, and then rework their pipes without draining the loop or
blowing anything up.  Draining first would be more realistic, but too tedious for a small change of route.  So a loop
at rest, a stopped loop holding coolant with exactly zero flow, may change freely until its pump starts again.

Reaching zero.  Friction and CoolantLoopEngine's coastDownDecay slow the flow in proportion, so alone they never reach
zero.  So in CoolantLoopEngine.step, while the pump is unpowered and the flow is under coastDownStopFlow (1 L/s), the
flow also falls at a constant rate that stops it within coastDownStopSeconds (2 s).  This is ergonomics, not physics.
It only acts on an unpowered pump, so it can't touch a pump speeding up or a running loop, however slow.

At rest.  MTECoolantPump.noteLoopAtRest sets mLoopAtRest when a stopped pump (disabled, unpowered, without a rotor, or
with its discharge disabled) finds zero flow with coolant in the loop.  While it's set, the pump doesn't look for
breaks or crawl on schedule, and onDeviceDisconnected ignores devices leaving, so nothing explodes or spills.  A crawl
that does run and fails keeps the coolant (applyCrawlResult), and one that succeeds only updates the loop.

Restarting.  With the pump allowed to work, these steps in CoolantPumpFillDrain run in turn, and flow starts only when
they've all passed.  None of them can spill or explode.

1. stepRestartCrawl crawls the loop again.  If the loop isn't closed the pump refuses to start, and retries.
2. stepReturningExcess returns coolant to the reservoir if the loop now holds less, and refuses to start while the
   reservoir has no room for it.
3. stepFilling draws more if the loop now holds more, and stops as for a first fill if the reservoir runs dry.

(W1) What's compared.  The pump saves only the loop's total, mRequiredFillLiters and mCurrentFillLiters, rather than
     every pipe's position: storing those would take too much NBT, and the totals are all a restart needs.
(W2) Coolant in new pipes.  CoolantLoopEngine.replaceSegmentsKeepingCoolant gives a new segment the average temperature
     of the coolant, and segments still in the loop keep theirs.
(W3) Once restarted, the pump is back to normal: a pipe removed during a refill or while circulating breaks the loop.

CoolantLoopEndToEndTest and the testStoppedLoop tests in CoolantPumpStructureTest check this, and in game so does
inworld-tests/test_coolant_loop_restructure.py.
*/

final class CoolantPumpLoopBinding {

    private CoolantPumpLoopBinding() {}

    static boolean applyCrawlResult(MTECoolantPump pump, LoopGraphCrawler.CrawlResult result) {
        boolean keepState = pump.mRecrawlKeepsState;
        pump.mRecrawlKeepsState = false;
        boolean restarting = pump.mRestartCrawlRunning;
        pump.mRestartCrawlRunning = false;
        boolean atRest = pump.mLoopAtRest;
        if (result == null || !result.isSuccess) {
            pump.releaseLoopDevices();
            pump.releaseLoopChunks();
            pump.mLastCrawlResult = result;
            pump.mLoopFormed = false;
            if (atRest) {
                pump.mLoopState = MTECoolantPump.LoopState.STOPPED;
            } else {
                pump.mLoopState = MTECoolantPump.LoopState.EMPTY;
                pump.mCurrentFillLiters = 0L;
            }
            LocalizedText reason = (result != null && result.failureReason != null) ? result.failureReason
                : LocalizedText.of("coolantloops.crawler.no_suction_path");
            if (result != null && result.isLoopTooLarge()) {
                pump.setLoopStatus(reason);
            } else {
                pump.setLoopStatusKey("coolantloops.pump.status.loop_disconnect", reason);
            }
            return false;
        }

        pump.mLastCrawlResult = result;
        boolean sameLoop = atRest ? pump.mEngine.replaceSegmentsKeepingCoolant(result.segments)
            : pump.mEngine.replaceSegments(result.segments, keepState);
        if (atRest && !sameLoop) pump.mLastSyncedGasVersion = -1L;
        if (result.maxPumpPipeCapacityLPerSec > 0 && result.maxPumpPipeCapacityLPerSec != Double.MAX_VALUE) {
            pump.mEngine.setMaxVolumetricFlowRate(result.maxPumpPipeCapacityLPerSec / 1000.0);
        }
        long requiredFill = 0L;
        List<Long> fillOrder = new ArrayList<>();
        for (LoopSegment s : result.segments) {
            if (s.getKey()
                .isKind("pipe")) {
                requiredFill += s.getCapacityLiters();
                fillOrder.add(requiredFill);
            }
        }
        // Devices still in the loop stay bound throughout, so a periodic re-crawl does not flicker them
        for (ICoolantLoopDevice dev : pump.mLoopDevices) {
            if (dev != null && !result.devices.contains(dev)) dev.setLoopPump(null);
        }
        pump.mLoopDevices = result.devices;
        for (ICoolantLoopDevice dev : pump.mLoopDevices) {
            dev.setLoopPump(pump);
            requiredFill += dev.getFillVolumeLiters();
            fillOrder.add(requiredFill);
        }
        pump.mFillOrderCumulative = new long[fillOrder.size()];
        for (int i = 0; i < pump.mFillOrderCumulative.length; i++) pump.mFillOrderCumulative[i] = fillOrder.get(i);
        pump.rebuildDeviceBindings();
        pump.mRequiredFillLiters = requiredFill;
        pump.mLoopFormed = true;
        pump.mFootprint = null;
        pump.updateLoopChunks();

        if (atRest && !restarting) {
            pump.mLoopState = MTECoolantPump.LoopState.STOPPED;
            pump.setLoopStatusKey("coolantloops.pump.status.at_rest", pump.mCurrentFillLiters);
            return true;
        }
        pump.mLoopAtRest = false;

        if (sameLoop && pump.mLoopState != MTECoolantPump.LoopState.STOPPED
            && pump.mLoopState != MTECoolantPump.LoopState.EMPTY) {
            // A re-crawl of an unchanged running loop leaves it doing whatever it was doing
        } else if (pump.mCurrentFillLiters < pump.mRequiredFillLiters) {
            pump.mLoopState = MTECoolantPump.LoopState.FILLING;
            pump.setLoopStatusKey(
                "coolantloops.pump.status.filling_loop",
                pump.mCurrentFillLiters,
                pump.mRequiredFillLiters,
                pump.mRequiredFillLiters > 0 ? (pump.mCurrentFillLiters * 100.0 / pump.mRequiredFillLiters) : 100.0);
        } else {
            pump.mLoopState = MTECoolantPump.LoopState.CIRCULATING;
            pump.setLoopStatusKey(
                "coolantloops.pump.status.loop_active",
                result.segments.size(),
                result.devices.size(),
                pump.mCurrentFillLiters);
        }
        return true;
    }

    static void rebindRestoredLoop(MTECoolantPump pump, IGregTechTileEntity aBaseMetaTileEntity) {
        World w = aBaseMetaTileEntity.getWorld();
        if (w == null) return;

        boolean missingOrChanged = false;

        if (pump.mSavedDischargeCoord != null) {
            int cx = pump.mSavedDischargeCoord[0], cy = pump.mSavedDischargeCoord[1], cz = pump.mSavedDischargeCoord[2];
            if (!w.blockExists(cx, cy, cz)) return;
            TileEntity te = w.getTileEntity(cx, cy, cz);
            if (te instanceof IGregTechTileEntity gte
                && gte.getMetaTileEntity() instanceof MTEHatchPressurizedFluid ph) {
                pump.mDischargeHatch = ph;
            } else {
                missingOrChanged = true;
            }
        } else {
            missingOrChanged = true;
        }

        if (pump.mSavedSuctionCoord != null) {
            int cx = pump.mSavedSuctionCoord[0], cy = pump.mSavedSuctionCoord[1], cz = pump.mSavedSuctionCoord[2];
            if (!w.blockExists(cx, cy, cz)) return;
            TileEntity te = w.getTileEntity(cx, cy, cz);
            if (te instanceof IGregTechTileEntity gte
                && gte.getMetaTileEntity() instanceof MTEHatchPressurizedFluid ph) {
                pump.mSuctionHatch = ph;
            } else {
                missingOrChanged = true;
            }
        } else {
            missingOrChanged = true;
        }

        if (pump.mEngine.getSegments()
            .isEmpty()) {
            missingOrChanged = true;
        }

        pump.releaseLoopDevices();
        Set<String> seenDeviceIds = new HashSet<>();

        if (pump.mLastCrawlResult != null && pump.mLastCrawlResult.graphNodes != null) {
            for (LoopGraphCrawler.FlowNode node : pump.mLastCrawlResult.graphNodes.values()) {
                if (node.type == LoopGraphCrawler.NodeType.DEVICE
                    && node.data instanceof LoopGraphCrawler.BlockPosCoord bc) {
                    if (!w.blockExists(bc.x, bc.y, bc.z)) return;
                    TileEntity te = w.getTileEntity(bc.x, bc.y, bc.z);
                    if (te == null) {
                        missingOrChanged = true;
                        break;
                    }
                    ICoolantLoopDevice dev = LoopDevices.at(w, bc.x, bc.y, bc.z);
                    if (dev == null) {
                        missingOrChanged = true;
                        break;
                    }
                    if (dev instanceof MTEMultiBlockBase multiblock) MultiblockStartup.formNow(multiblock);
                    if (seenDeviceIds.add(dev.getDeviceId())) {
                        dev.setLoopPump(pump);
                        pump.mLoopDevices.add(dev);
                    }
                }
            }
        }

        // The pipes are not saved: the loop runs on its saved segments until the next scheduled crawl, which finds
        // any pipe that changed while it was unloaded, as a multiblock runs until its next structure check
        if (missingOrChanged) {
            pump.mNeedsPostLoadRebind = false;
            pump.mLoopFormed = false;
            pump.releaseLoopDevices();
            // Re-crawled at once, keeping the loop's state if it is unchanged after all, so it does not stop meanwhile
            pump.mRecrawlKeepsState = true;
            pump.crawlLoopGraph();
            while (pump.mActiveCrawl != null) pump.stepActiveCrawl();
            return;
        }

        pump.mNeedsPostLoadRebind = false;
        pump.rebuildDeviceBindings();
        pump.updateLoopChunks();
        if (pump.mLastCrawlResult != null) {
            pump.mLastCrawlResult = LoopGraphCrawler.CrawlResult.success(
                pump.mEngine.getSegments(),
                pump.mLoopDevices,
                pump.mLastCrawlResult.pipePositions,
                pump.mLastCrawlResult.maxPumpPipeCapacityLPerSec,
                pump.mLastCrawlResult.loopMaterial,
                pump.mLastCrawlResult.pipeConfigs,
                pump.mLastCrawlResult.graphNodes,
                pump.mLastCrawlResult.graphEdges);
        }

        if (pump.mLoopState == MTECoolantPump.LoopState.CIRCULATING && pump.isAllowedToWork()) {
            aBaseMetaTileEntity.setActive(true);
            pump.mEngine.setPumpPowered(true);
        }
    }

    /**
     * Why the formed loop no longer exists, or null if it still does: the pump's own structure broke, a device's
     * machine was removed or unformed, or a pipe or device block in a loaded chunk changed since the loop formed.
     */
    static LocalizedText findLoopBreak(MTECoolantPump pump, World world) {
        IGregTechTileEntity base = pump.getBaseMetaTileEntity();
        if (pump.mNeedsPostLoadRebind) return null;
        // Multiblocks report unformed until their first structure check after a load
        boolean settled = base != null && base.getTimer() > pump.STRUCTURE_SETTLE_TICKS;
        if (!pump.mMachine && settled) {
            return LocalizedText
                .of("coolantloops.crawler.loop_broken", base.getXCoord(), (int) base.getYCoord(), base.getZCoord());
        }
        for (ICoolantLoopDevice dev : pump.mLoopDevices) {
            if (dev instanceof IMetaTileEntity mte) {
                IGregTechTileEntity dte = mte.getBaseMetaTileEntity();
                if (dte == null || dte.isInvalidTileEntity()) {
                    return LocalizedText.of("coolantloops.crawler.device_removed");
                }
                if (mte instanceof MTEMultiBlockBase mb && !mb.mMachine
                    && dte.getTimer() > pump.STRUCTURE_SETTLE_TICKS) {
                    return LocalizedText.of(
                        "coolantloops.crawler.device_unformed",
                        dte.getXCoord(),
                        (int) dte.getYCoord(),
                        dte.getZCoord());
                }
            } else if (dev instanceof TileEntity te && te.isInvalid()) {
                return LocalizedText.of("coolantloops.crawler.device_removed");
            }
        }
        if (world == null || pump.mLastCrawlResult == null) return null;
        if (pump.mFootprint == null) pump.captureFootprint(world);
        if (pump.mFootprint == null) return null;
        for (int i = 0, n = pump.mFootprintBlocks.length; i < n; i++) {
            int x = pump.mFootprint[3 * i], y = pump.mFootprint[3 * i + 1], z = pump.mFootprint[3 * i + 2];
            if (world.blockExists(x, y, z) && world.getBlock(x, y, z) != pump.mFootprintBlocks[i]) {
                return LocalizedText.of("coolantloops.crawler.loop_broken", x, y, z);
            }
        }
        return null;
    }

    /**
     * Keeps loaded every chunk the formed loop occupies: the pump, its pipes and the whole structure of each device.
     */
    static void updateLoopChunks(MTECoolantPump pump) {
        IGregTechTileEntity base = pump.getBaseMetaTileEntity();
        World world = base != null ? base.getWorld() : null;
        if (world == null || world.isRemote) return;
        int x = base.getXCoord(), y = base.getYCoord(), z = base.getZCoord();
        Set<Long> chunks = new HashSet<>();
        pump.collectPumpChunks(base, pump.mWidth, chunks);
        if (pump.mLastCrawlResult != null && pump.mLastCrawlResult.pipePositions != null) {
            for (LoopGraphCrawler.BlockPosCoord p : pump.mLastCrawlResult.pipePositions) {
                StructureChunks.addBlock(p.x, p.z, chunks);
            }
        }
        if (pump.mLastCrawlResult != null && pump.mLastCrawlResult.graphNodes != null) {
            for (LoopGraphCrawler.FlowNode node : pump.mLastCrawlResult.graphNodes.values()) {
                if (node.data instanceof LoopGraphCrawler.BlockPosCoord p) StructureChunks.addBlock(p.x, p.z, chunks);
            }
        }
        for (ICoolantLoopDevice dev : pump.mLoopDevices) {
            dev.collectStructureChunks(chunks);
        }
        pump.mLoopChunks = chunks;
        pump.mChunkTickets = LoopChunkLoader.update(pump.mChunkTickets, world, x, y, z, chunks);
    }

    /** Records the loop's pipe blocks; none after a load, until the first crawl finds them again. */
    static void captureFootprint(MTECoolantPump pump, World world) {
        Set<LoopGraphCrawler.BlockPosCoord> positions = pump.mLastCrawlResult.pipePositions;
        if (positions == null || positions.isEmpty()) return;
        int[] coords = new int[3 * positions.size()];
        Block[] blocks = new Block[positions.size()];
        int i = 0;
        for (LoopGraphCrawler.BlockPosCoord p : positions) {
            // A chunk unloaded now cannot be checked until a new crawl; mark it as matching anything
            blocks[i] = world.blockExists(p.x, p.y, p.z) ? world.getBlock(p.x, p.y, p.z) : null;
            coords[3 * i] = blocks[i] != null ? p.x : 0;
            coords[3 * i + 1] = blocks[i] != null ? p.y : -1;
            coords[3 * i + 2] = blocks[i] != null ? p.z : 0;
            i++;
        }
        pump.mFootprint = coords;
        pump.mFootprintBlocks = blocks;
    }

    static void onDeviceDisconnected(MTECoolantPump pump, ICoolantLoopDevice device, LocalizedText reason) {
        if (pump.mLoopAtRest) return;
        pump.mActiveCrawl = null;
        pump.mRecrawlKeepsState = false;
        boolean wasActiveLoop = pump.mLoopFormed
            && (pump.mCurrentFillLiters > 0L || pump.mLoopState == MTECoolantPump.LoopState.CIRCULATING
                || pump.mLoopState == MTECoolantPump.LoopState.DECELERATING
                || (pump.mEngine != null && pump.mEngine.getVolumetricFlowRate() > 1e-5));

        LoopSegment deviceSeg = null;
        if (device != null && pump.mEngine != null) {
            String devId = device.getDeviceId();
            for (LoopSegment s : pump.mEngine.getSegments()) {
                if (s != null && s.getId()
                    .equals(devId)) {
                    deviceSeg = s;
                    break;
                }
            }
        }

        if (pump.mEngine != null) {
            pump.mEngine.setPumpPowered(false);
            pump.mEngine.setPumpMechanicalPowerWatts(0.0);
            pump.mEngine.setBraking(false);
        }
        IGregTechTileEntity baseTE = pump.getBaseMetaTileEntity();
        if (baseTE != null && baseTE.isActive()) {
            baseTE.setActive(false);
        }
        LocalizedText msg = reason != null ? reason
            : LocalizedText.of("coolantloops.crawler.heater_powerfailed_generic");
        pump.applyCrawlResult(LoopGraphCrawler.CrawlResult.fail(msg));

        if (wasActiveLoop && pump.mEngine != null) {
            pump.mEngine.setRuptured(true);
            if (deviceSeg != null) {
                pump.mEngine.setRupturedSegment(deviceSeg);
            }
            pump.triggerCatastrophicExplosion(msg, deviceSeg);
        }
    }
}
