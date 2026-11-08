package com.gtnewhorizons.modularnuclear.common.metatileentity.multi;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraftforge.common.util.Constants;

import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopGraphCrawler;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopId;
import com.gtnewhorizons.modularnuclear.common.util.LocalizedTextNbt;
import com.gtnewhorizons.modularnuclear.common.util.LoopStateNbt;
import com.gtnewhorizons.modularnuclear.common.util.Swallowed;

import gregtech.api.enums.Materials;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * The coolant pump's saved state: its loop chunks, fill and rotor state, its hatches' positions, the loop engine's
 * state, and the devices of its last crawl, so a load can bind them again.
 */
final class CoolantPumpNbt {

    private CoolantPumpNbt() {}

    static void save(MTECoolantPump p, NBTTagCompound aNBT) {
        if (!p.mLoopChunks.isEmpty()) {
            int[] chunks = new int[2 * p.mLoopChunks.size()];
            int i = 0;
            for (long chunk : p.mLoopChunks) {
                chunks[i++] = (int) chunk;
                chunks[i++] = (int) (chunk >>> 32);
            }
            aNBT.setIntArray("mLoopChunks", chunks);
        }
        p.mReservoir.saveNBTData(aNBT);
        aNBT.setBoolean("mLoopFormed", p.mLoopFormed);
        aNBT.setString("mLoopState", p.mLoopState.name());
        aNBT.setTag("mLoopStatus", LocalizedTextNbt.write(p.mLoopStatus));
        aNBT.setLong("mCurrentFillLiters", p.mCurrentFillLiters);
        aNBT.setLong("mRequiredFillLiters", p.mRequiredFillLiters);
        aNBT.setBoolean("mLoopAtRest", p.mLoopAtRest);
        aNBT.setDouble("mRotorDamageAccumulator", p.mRotorDamageAccumulator);
        aNBT.setBoolean("mPowerFailed", p.mPowerFailed);
        if (p.mTankMaterial != null && p.mTankMaterial.mName != null) {
            aNBT.setString("mTankMaterial", p.mTankMaterial.mName);
        }
        if (p.mRotorMaterial != null && p.mRotorMaterial.mName != null) {
            aNBT.setString("mRotorMaterial", p.mRotorMaterial.mName);
        }

        if (p.mDischargeHatch != null && p.mDischargeHatch.getBaseMetaTileEntity() != null) {
            IGregTechTileEntity dte = p.mDischargeHatch.getBaseMetaTileEntity();
            aNBT.setIntArray("discCoord", new int[] { dte.getXCoord(), dte.getYCoord(), dte.getZCoord() });
        } else if (p.mSavedDischargeCoord != null) {
            aNBT.setIntArray("discCoord", p.mSavedDischargeCoord);
        }
        if (p.mSuctionHatch != null && p.mSuctionHatch.getBaseMetaTileEntity() != null) {
            IGregTechTileEntity ste = p.mSuctionHatch.getBaseMetaTileEntity();
            aNBT.setIntArray("sucCoord", new int[] { ste.getXCoord(), ste.getYCoord(), ste.getZCoord() });
        } else if (p.mSavedSuctionCoord != null) {
            aNBT.setIntArray("sucCoord", p.mSavedSuctionCoord);
        }

        NBTTagCompound engineTag = new NBTTagCompound();
        LoopStateNbt.write(p.mEngine.exportState(), engineTag);
        aNBT.setTag("mEngine", engineTag);

        if (p.mLastCrawlResult != null && p.mLastCrawlResult.isSuccess) {
            NBTTagCompound crawlTag = new NBTTagCompound();
            crawlTag.setDouble("maxPumpPipeCap", p.mLastCrawlResult.maxPumpPipeCapacityLPerSec);
            if (p.mLastCrawlResult.loopMaterial != null) {
                crawlTag.setString("loopMaterial", p.mLastCrawlResult.loopMaterial.mName);
            }
            // Only the devices, by position, to bind them again after a load. The pipes and the graph's edges are
            // found again by the first crawl after the load
            if (p.mLastCrawlResult.graphNodes != null) {
                NBTTagList nodeList = new NBTTagList();
                for (LoopGraphCrawler.FlowNode fn : p.mLastCrawlResult.graphNodes.values()) {
                    if (fn.type != LoopGraphCrawler.NodeType.DEVICE) continue;
                    int[] coord = nodePosition(fn.data);
                    if (coord == null) continue;
                    NBTTagCompound nTag = new NBTTagCompound();
                    nTag.setString("id", fn.id.toString());
                    nTag.setString("type", fn.type.name());
                    nTag.setIntArray("coord", coord);
                    nodeList.appendTag(nTag);
                }
                crawlTag.setTag("graphNodes", nodeList);
            }
            aNBT.setTag("mLastCrawlResult", crawlTag);
        }
    }

    static void load(MTECoolantPump p, NBTTagCompound aNBT) {
        p.mLoopChunks = new HashSet<>();
        int[] loopChunks = aNBT.getIntArray("mLoopChunks");
        for (int i = 0; i + 1 < loopChunks.length; i += 2) {
            p.mLoopChunks.add(ChunkCoordIntPair.chunkXZ2Int(loopChunks[i], loopChunks[i + 1]));
        }
        p.mReservoir.loadNBTData(aNBT);
        if (aNBT.hasKey("mLoopState")) {
            try {
                p.mLoopState = MTECoolantPump.LoopState.valueOf(aNBT.getString("mLoopState"));
            } catch (Exception swallowed) {
                Swallowed.note(swallowed);
            }
        }
        if (aNBT.hasKey("mCurrentFillLiters")) {
            p.mCurrentFillLiters = aNBT.getLong("mCurrentFillLiters");
        }
        p.mLoopAtRest = aNBT.getBoolean("mLoopAtRest");
        if (aNBT.hasKey("mRequiredFillLiters")) {
            p.mRequiredFillLiters = aNBT.getLong("mRequiredFillLiters");
        }
        if (aNBT.hasKey("mRotorDamageAccumulator")) {
            p.mRotorDamageAccumulator = aNBT.getDouble("mRotorDamageAccumulator");
        }
        if (aNBT.hasKey("mPowerFailed")) {
            p.mPowerFailed = aNBT.getBoolean("mPowerFailed");
        }
        if (aNBT.hasKey("mLoopStatus", Constants.NBT.TAG_COMPOUND)) {
            p.mLoopStatus = LocalizedTextNbt.read(aNBT.getCompoundTag("mLoopStatus"));
        }
        if (aNBT.hasKey("mTankMaterial")) {
            Materials mat = Materials.get(aNBT.getString("mTankMaterial"));
            if (mat != null) {
                p.mTankMaterial = mat;
            }
        }
        if (aNBT.hasKey("mRotorMaterial")) {
            Materials mat = Materials.get(aNBT.getString("mRotorMaterial"));
            if (mat != null) {
                p.mRotorMaterial = mat;
            }
        }
        if (aNBT.hasKey("discCoord")) {
            p.mSavedDischargeCoord = aNBT.getIntArray("discCoord");
        }
        if (aNBT.hasKey("sucCoord")) {
            p.mSavedSuctionCoord = aNBT.getIntArray("sucCoord");
        }

        if (aNBT.hasKey("mEngine", 10)) {
            p.mEngine.importState(LoopStateNbt.read(aNBT.getCompoundTag("mEngine")));
        }
        if (aNBT.hasKey("mLoopFormed")) {
            p.mLoopFormed = aNBT.getBoolean("mLoopFormed") && !p.mEngine.getSegments()
                .isEmpty();
        }

        if (aNBT.hasKey("mLastCrawlResult", 10)) {
            NBTTagCompound crawlTag = aNBT.getCompoundTag("mLastCrawlResult");
            double maxCap = crawlTag.getDouble("maxPumpPipeCap");
            Materials loopMat = crawlTag.hasKey("loopMaterial") ? Materials.get(crawlTag.getString("loopMaterial"))
                : null;
            Map<LoopId, LoopGraphCrawler.FlowNode> graphNodes = new LinkedHashMap<>();
            if (crawlTag.hasKey("graphNodes", 9)) {
                NBTTagList nodeList = crawlTag.getTagList("graphNodes", 10);
                for (int i = 0; i < nodeList.tagCount(); i++) {
                    NBTTagCompound nTag = nodeList.getCompoundTagAt(i);
                    LoopId id = LoopId.of(nTag.getString("id"));
                    LoopGraphCrawler.NodeType type = LoopGraphCrawler.NodeType.valueOf(nTag.getString("type"));
                    Object data = null;
                    if (nTag.hasKey("coord")) {
                        int[] c = nTag.getIntArray("coord");
                        if (c.length >= 3) data = new LoopGraphCrawler.BlockPosCoord(c[0], c[1], c[2]);
                    }
                    graphNodes.put(id, new LoopGraphCrawler.FlowNode(id, type, data));
                }
            }
            // The edges are not saved: the first crawl after the load finds them again
            Map<LoopId, List<LoopId>> graphEdges = new LinkedHashMap<>();
            p.mLastCrawlResult = LoopGraphCrawler.CrawlResult.success(
                p.mEngine.getSegments(),
                p.mLoopDevices,
                new LinkedHashSet<>(),
                maxCap,
                loopMat,
                null,
                graphNodes,
                graphEdges);
        }

        if (p.mLoopFormed) {
            p.mNeedsPostLoadRebind = true;
        }
    }

    /**
     * Where a graph node's block is, so a load can find the node again: devices are recorded as the device itself,
     * which is saved as the position of its block (a multiblock's controller, or a hatch).
     */
    private static int[] nodePosition(Object data) {
        if (data instanceof LoopGraphCrawler.BlockPosCoord bc) return new int[] { bc.x, bc.y, bc.z };
        if (data instanceof IMetaTileEntity mte && mte.getBaseMetaTileEntity() != null) {
            IGregTechTileEntity base = mte.getBaseMetaTileEntity();
            return new int[] { base.getXCoord(), base.getYCoord(), base.getZCoord() };
        }
        if (data instanceof TileEntity te) return new int[] { te.xCoord, te.yCoord, te.zCoord };
        return null;
    }
}
