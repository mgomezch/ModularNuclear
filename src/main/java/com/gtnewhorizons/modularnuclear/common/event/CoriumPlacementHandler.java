package com.gtnewhorizons.modularnuclear.common.event;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.world.BlockEvent;

import com.gtnewhorizons.modularnuclear.common.block.BlockCorium;
import com.gtnewhorizons.modularnuclear.common.block.ModBlocks;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Prevents players or automated block placers from overwriting flowing or source Corium,
 * and prevents empty buckets from erasing Corium blocks.
 */
public class CoriumPlacementHandler {

    private boolean isCorium(Block block) {
        return block instanceof BlockCorium || (ModBlocks.blockCorium != null && block == ModBlocks.blockCorium);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockEvent.PlaceEvent event) {
        if (event == null) {
            return;
        }

        if (event.blockSnapshot != null) {
            Block replaced = event.blockSnapshot.getReplacedBlock();
            if (replaced == null) {
                replaced = event.blockSnapshot.replacedBlock;
            }
            if (isCorium(replaced)) {
                event.setCanceled(true);
                return;
            }
        }

        if (event instanceof BlockEvent.MultiPlaceEvent multi) {
            List<BlockSnapshot> snapshots = multi.getReplacedBlockSnapshots();
            if (snapshots != null) {
                for (BlockSnapshot snap : snapshots) {
                    if (snap != null) {
                        Block replaced = snap.getReplacedBlock();
                        if (replaced == null) {
                            replaced = snap.replacedBlock;
                        }
                        if (isCorium(replaced)) {
                            event.setCanceled(true);
                            return;
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBucketFill(FillBucketEvent event) {
        if (event == null || event.world == null || event.target == null) {
            return;
        }

        if (event.target.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK) {
            Block block = event.world.getBlock(event.target.blockX, event.target.blockY, event.target.blockZ);
            if (isCorium(block)) {
                event.setCanceled(true);
            }
        }
    }
}
