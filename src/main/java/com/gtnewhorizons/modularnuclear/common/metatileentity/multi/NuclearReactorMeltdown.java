package com.gtnewhorizons.modularnuclear.common.metatileentity.multi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

import com.gtnewhorizons.modularnuclear.ModularNuclear;
import com.gtnewhorizons.modularnuclear.common.block.BlockCorium;
import com.gtnewhorizons.modularnuclear.common.block.ModBlocks;
import com.gtnewhorizons.modularnuclear.common.coolantloop.ICoolantPassageHatch;
import com.gtnewhorizons.modularnuclear.common.entity.EntityMeltdownFallout;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearBus;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControl;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHatch;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHighPressure;
import com.gtnewhorizons.modularnuclear.common.nuclear.CoreHatchRules;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.SoundResource;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchDynamo;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.util.GTLog;
import gregtech.api.util.GTUtility;
import gtnhlanth.common.hatch.MTEHatchInputBeamline;

/** A reactor's failures: overheating columns, a meltdown, and what they destroy. */
final class NuclearReactorMeltdown {

    private NuclearReactorMeltdown() {}

    static void voidAllContents(MTENuclearReactor reactor) {
        IGregTechTileEntity base = reactor.getBaseMetaTileEntity();

        Set<IGregTechTileEntity> allNuclearTiles = new HashSet<>(reactor.mNuclearTiles);
        allNuclearTiles.addAll(reactor.mLastFormedNuclearTiles);
        for (IGregTechTileEntity te : allNuclearTiles) {
            if (te != null && !te.isDead()) {
                IMetaTileEntity mte = te.getMetaTileEntity();
                if (mte instanceof MTEHatchNuclearHatch hatch) {
                    hatch.voidFluid();
                } else if (mte instanceof MTEHatchNuclearBus bus) {
                    bus.voidContents();
                } else if (mte instanceof MTEHatchNuclearControlRod rod) {
                    rod.voidContents();
                }
            }
        }

        for (MTEHatchNuclearControlRod rod : reactor.mBottomControlRodHatches) {
            if (rod != null) {
                rod.voidContents();
            }
        }
        for (MTEHatchNuclearControl ctrl : reactor.mControlHatches) {
            if (ctrl != null) {
                ctrl.setOutputRedstone((byte) 0);
                if (ctrl.getBaseMetaTileEntity() != null) {
                    ctrl.getBaseMetaTileEntity()
                        .markDirty();
                }
            }
        }

        for (ICoolantPassageHatch hp : reactor.mTopHighPressureHatches) {
            if (hp instanceof MetaTileEntity mte && mte.getBaseMetaTileEntity() != null) {
                mte.getBaseMetaTileEntity()
                    .markDirty();
            }
        }
        for (ICoolantPassageHatch hp : reactor.mBottomHighPressureHatches) {
            if (hp instanceof MetaTileEntity mte && mte.getBaseMetaTileEntity() != null) {
                mte.getBaseMetaTileEntity()
                    .markDirty();
            }
        }

        for (MTEHatchInputBeamline beam : reactor.mBottomBeamInputHatches) {
            if (beam != null) {
                beam.dataPacket = null;
                if (beam.getBaseMetaTileEntity() != null) {
                    beam.getBaseMetaTileEntity()
                        .markDirty();
                }
            }
        }

        for (MTEHatchInput inHatch : reactor.mInputHatches) {
            if (inHatch != null) {
                inHatch.setFillableStack(null);
                inHatch.markDirty();
            }
        }
        for (MTEHatchOutput outHatch : reactor.mOutputHatches) {
            if (outHatch != null) {
                outHatch.setDrainableStack(null);
                outHatch.markDirty();
            }
        }
        for (MTEHatchInputBus inBus : reactor.mInputBusses) {
            if (inBus != null && inBus.mInventory != null) {
                for (int i = 0; i < inBus.getSizeInventory(); i++) inBus.setInventorySlotContents(i, null);
                inBus.markDirty();
            }
        }
        for (MTEHatchOutputBus outBus : reactor.mOutputBusses) {
            if (outBus != null && outBus.mInventory != null) {
                for (int i = 0; i < outBus.getSizeInventory(); i++) outBus.setInventorySlotContents(i, null);
                outBus.markDirty();
            }
        }
        for (MTEHatchEnergy energy : reactor.mEnergyHatches) {
            if (energy != null) {
                energy.setEUVar(0);
                if (energy.getBaseMetaTileEntity() != null) energy.getBaseMetaTileEntity()
                    .markDirty();
            }
        }
        for (MTEHatchDynamo dynamo : reactor.mDynamoHatches) {
            if (dynamo != null) {
                dynamo.setEUVar(0);
                if (dynamo.getBaseMetaTileEntity() != null) dynamo.getBaseMetaTileEntity()
                    .markDirty();
            }
        }

        reactor.mDirectPowerEUt = 0;
        reactor.mNeutronsProduced = 0;
        reactor.mFastAbsorbed = 0;
        reactor.mThermalAbsorbed = 0;
        reactor.mEscapedNeutrons = 0;
        reactor.mReactorDamage = 0.0;
        reactor.mScram = true;
        reactor.mMachine = false;
        reactor.stopMachine();
        if (base != null) {
            base.setActive(false);
            base.setLightValue((byte) 0);
            base.markDirty();
            World world = base.getWorld();
            int cX = base.getXCoord();
            int cY = base.getYCoord();
            int cZ = base.getZCoord();
            GTUtility.sendSoundToPlayers(
                world,
                SoundResource.IC2_MACHINES_MACHINE_OVERLOAD,
                1.0F,
                0.5F,
                cX + 0.5,
                cY + 0.5,
                cZ + 0.5);

        }
    }

    static void triggerMeltdown(MTENuclearReactor reactor) {
        IGregTechTileEntity base = reactor.getBaseMetaTileEntity();
        if (base == null || !base.isServerSide()) return;

        GTLog.writeExplosionLog(
            reactor,
            String.format("MELTDOWN: reactor damage %.1f%%, core %.0f °C", reactor.mReactorDamage, reactor.mCoreTemp));

        if (!GregTechAPI.sMachineExplosions) {
            ModularNuclear.LOG.warn(
                "Meltdown condition reached, but machine explosions are disabled! Voiding all contents and shutting down safely.");
            reactor.voidAllContents();
            return;
        }

        World world = base.getWorld();
        if (world == null) return;

        // Mark controller as permanently ruined from meltdown prior to explosion
        reactor.mMoltenDown = true;
        base.markDirty();
        base.sendBlockEvent((byte) 102, (byte) 1);
        base.issueTextureUpdate();

        List<ChunkCoordinates> hollowCoords = new ArrayList<>();
        double sumX = 0;
        double sumY = 0;
        double sumZ = 0;
        int count = 0;

        for (IGregTechTileEntity te : reactor.mNuclearTiles) {
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
        world.createExplosion(null, centerX, centerY, centerZ, 20.0F, true);

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
                    BlockCorium.claimOwnerlessGtMachine(world, coord.posX, coord.posY, coord.posZ);
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
                BlockCorium.claimOwnerlessGtMachine(world, coord.posX, coord.posY, coord.posZ);
                world.setBlock(coord.posX, coord.posY, coord.posZ, ModBlocks.blockCorium, 0, 3);
            }
        }

        // 3. Invisible fallout entity for timers, radiation, particles, and corium solidification
        EntityMeltdownFallout fallout = new EntityMeltdownFallout(world, centerX, centerY, centerZ);
        world.spawnEntityInWorld(fallout);

        // 4. Shutdown reactor
        reactor.mMachine = false;
        reactor.stopMachine();
        base.setActive(false);

        ModularNuclear.LOG.warn(
            "Modular Pressure Tube Reactor melted down at ({}, {}, {})! Produced corium and spawned fallout entity.",
            centerX,
            centerY,
            centerZ);
    }

    /** Columns past the casing's operating temperature lose their non-fuel contents and add damage (manual 12.3). */
    static void applyOverheating(MTENuclearReactor reactor) {
        // 4. Check casing-dependent maximum operating temperature:
        // Overheating hatches increase reactor damage by 1% per overheating hatch.
        // Overheating non-fuel contents in hatches/buses are voided, but fuel rods and fluids are NOT directly
        // voided!
        double maxTemp = NuclearSimulationEngine.getMaxOperatingTemperature(reactor.mPipeTier);
        int overheatingHatchesCount = 0;
        for (IGregTechTileEntity te : reactor.mNuclearTiles) {
            if (te != null) {
                if (te.getMetaTileEntity() instanceof MTEHatchNuclearHatch hatch) {
                    if (hatch.mTemperature > maxTemp) {
                        overheatingHatchesCount++;
                        hatch.voidNonFuel();
                    }
                } else if (te.getMetaTileEntity() instanceof MTEHatchNuclearBus bus) {
                    if (bus.mTemperature > maxTemp) {
                        overheatingHatchesCount++;
                        bus.voidNonFuelInput();
                    }
                } else if (te.getMetaTileEntity() instanceof MTEHatchNuclearHighPressure hp) {
                    if (hp.getTemperature() > maxTemp || hp.mIncomingCoolantTemp > maxTemp) {
                        overheatingHatchesCount++;
                    }
                }
            }
        }
        for (MTEHatchNuclearControlRod rod : reactor.mBottomControlRodHatches) {
            if (rod != null && rod.mTemperature > maxTemp) {
                overheatingHatchesCount++;
                rod.voidRod();
            }
        }
        if (overheatingHatchesCount > 0) {
            reactor.mReactorDamage = Math.min(
                100.0,
                reactor.mReactorDamage + overheatingHatchesCount * CoreHatchRules.OVERHEAT_DAMAGE_PERCENT_PER_COLUMN);
        }
    }
}
