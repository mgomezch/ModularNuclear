package com.gtnewhorizons.modularnuclear.common.metatileentity.multi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantFluidProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopGraphCrawler;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopSegment;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * What a pump refuses to pump (plain water, lava outside the nether, liquid fuels, molten coolant colder than its
 * melting point) and what a ruptured loop spills.
 */
final class CoolantPumpFluidSafety {

    private CoolantPumpFluidSafety() {}

    /**
     * Refuses liquid fuel (which destroys the impeller), plain water, gases, and lava outside the Nether. Returns
     * CARRY_ON to carry on with the step.
     */
    static MTECoolantPump.Step refuseUnsuitableFluid(MTECoolantPump pump, FluidStack reservoirFluid) {
        if (pump.mCurrentFillLiters <= 0 && pump.getTotalCoolantLiters() <= 0) {
            // Nothing in the tank or the loop: the pump waits for coolant
            pump.mLoopState = MTECoolantPump.LoopState.STOPPED;
            pump.mEngine.setPumpPowered(false);
            pump.mEngine.setPumpMechanicalPowerWatts(0.0);
            pump.mEngine.setBraking(false);
            pump.setLoopStatusKey("coolantloops.pump.status.tank_empty");
            return MTECoolantPump.Step.DONE;
        }
        if (reservoirFluid != null && reservoirFluid.getFluid() != null) {
            if (pump.isLiquidNuclearFuel(reservoirFluid)) {
                pump.failOnLiquidNuclearFuel(
                    reservoirFluid.getFluid()
                        .getName());
                return MTECoolantPump.Step.of(pump.stepCoastingAndCheckRupture());
            }
            if (pump.isPlainRegularWater(reservoirFluid)) {
                pump.mLoopState = MTECoolantPump.LoopState.STOPPED;
                pump.mEngine.setPumpPowered(false);
                pump.mEngine.setPumpMechanicalPowerWatts(0.0);
                pump.mEngine.setBraking(false);
                pump.setLoopStatusKey("coolantloops.pump.status.refuse_water");
                return MTECoolantPump.Step.DONE;
            }
            if (pump.isGaseousFluid(reservoirFluid)) {
                pump.mLoopState = MTECoolantPump.LoopState.STOPPED;
                pump.mEngine.setPumpPowered(false);
                pump.mEngine.setPumpMechanicalPowerWatts(0.0);
                pump.mEngine.setBraking(false);
                pump.setLoopStatusKey(
                    "coolantloops.pump.status.refuse_gas",
                    reservoirFluid.getFluid()
                        .getName());
                return MTECoolantPump.Step.DONE;
            }
            if (pump.isLava(reservoirFluid)) {
                World world = pump.getBaseMetaTileEntity() != null ? pump.getBaseMetaTileEntity()
                    .getWorld() : null;
                if (!pump.isNetherWorld(world)) {
                    pump.mLoopState = MTECoolantPump.LoopState.STOPPED;
                    pump.mEngine.setPumpPowered(false);
                    pump.mEngine.setPumpMechanicalPowerWatts(0.0);
                    pump.mEngine.setBraking(false);
                    pump.setLoopStatusKey("coolantloops.pump.status.refuse_lava");
                    return MTECoolantPump.Step.DONE;
                }
            }
        } else if (pump.mEngine.getFluid() != null) {
            if (pump.isLiquidNuclearFuel(
                pump.mEngine.getFluid()
                    .getFluidName())) {
                pump.failOnLiquidNuclearFuel(
                    pump.mEngine.getFluid()
                        .getFluidName());
                return MTECoolantPump.Step.of(pump.stepCoastingAndCheckRupture());
            }
            if (pump.mEngine.getFluid()
                .isPlainWater()) {
                pump.mLoopState = MTECoolantPump.LoopState.STOPPED;
                pump.mEngine.setPumpPowered(false);
                pump.mEngine.setPumpMechanicalPowerWatts(0.0);
                pump.mEngine.setBraking(false);
                pump.setLoopStatusKey("coolantloops.pump.status.refuse_water");
                return MTECoolantPump.Step.DONE;
            }
            if (pump.mEngine.getFluid()
                .isLava()) {
                World world = pump.getBaseMetaTileEntity() != null ? pump.getBaseMetaTileEntity()
                    .getWorld() : null;
                if (!pump.isNetherWorld(world)) {
                    pump.mLoopState = MTECoolantPump.LoopState.STOPPED;
                    pump.mEngine.setPumpPowered(false);
                    pump.mEngine.setPumpMechanicalPowerWatts(0.0);
                    pump.mEngine.setBraking(false);
                    pump.setLoopStatusKey("coolantloops.pump.status.refuse_lava");
                    return MTECoolantPump.Step.DONE;
                }
            }
        }
        return MTECoolantPump.Step.CARRY_ON;
    }

    static void spillCoolantFluid(MTECoolantPump pump, World world, LoopSegment rupturedSegment) {
        if (world == null || world.isRemote) {
            return;
        }

        CoolantFluidProperty prop = pump.mEngine.getFluid();
        FluidStack reservoirFluid = pump.getReservoirFluid();
        if (prop == null && reservoirFluid != null && reservoirFluid.getFluid() != null) {
            prop = CoolantFluidProperty.get(
                reservoirFluid.getFluid()
                    .getName());
        }

        // Check per-fluid configuration
        if (prop != null && !prop.isSpillOnRupture()) {
            return;
        }

        Fluid forgeFluid = null;
        if (reservoirFluid != null && reservoirFluid.getFluid() != null) {
            forgeFluid = reservoirFluid.getFluid();
        } else if (prop != null) {
            forgeFluid = FluidRegistry.getFluid(prop.getFluidName());
        }

        if (forgeFluid == null || !forgeFluid.canBePlacedInWorld()) {
            return;
        }

        Block fluidBlock = forgeFluid.getBlock();
        if (fluidBlock == null) {
            return;
        }

        List<LoopGraphCrawler.BlockPosCoord> candidateOrigins = new ArrayList<>();
        if (rupturedSegment != null && rupturedSegment.hasPosition()) {
            candidateOrigins.add(
                new LoopGraphCrawler.BlockPosCoord(
                    rupturedSegment.getBlockX(),
                    rupturedSegment.getBlockY(),
                    rupturedSegment.getBlockZ()));
        }

        IGregTechTileEntity te = pump.getBaseMetaTileEntity();
        if (te != null) {
            candidateOrigins.add(new LoopGraphCrawler.BlockPosCoord(te.getXCoord(), te.getYCoord(), te.getZCoord()));
        }

        List<LoopGraphCrawler.BlockPosCoord> validSpillPositions = new ArrayList<>();
        Set<LoopGraphCrawler.BlockPosCoord> seen = new HashSet<>();

        for (LoopGraphCrawler.BlockPosCoord origin : candidateOrigins) {
            for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
                int cx = origin.x + dir.offsetX;
                int cy = origin.y + dir.offsetY;
                int cz = origin.z + dir.offsetZ;
                LoopGraphCrawler.BlockPosCoord candidate = new LoopGraphCrawler.BlockPosCoord(cx, cy, cz);
                if (seen.add(candidate)) {
                    if (world.isAirBlock(cx, cy, cz)
                        || (world.getBlock(cx, cy, cz) != null && world.getBlock(cx, cy, cz)
                            .isReplaceable(world, cx, cy, cz))) {
                        validSpillPositions.add(candidate);
                    }
                }
            }
        }

        if (validSpillPositions.isEmpty()) {
            return;
        }

        Collections.shuffle(validSpillPositions);
        int randOffset = (world.rand != null) ? world.rand.nextInt(2) : 0;
        int spillCount = Math.min(validSpillPositions.size(), 1 + randOffset);

        for (int i = 0; i < spillCount; i++) {
            LoopGraphCrawler.BlockPosCoord pos = validSpillPositions.get(i);
            world.setBlock(pos.x, pos.y, pos.z, fluidBlock, 0, 3);
            world.notifyBlocksOfNeighborChange(pos.x, pos.y, pos.z, fluidBlock);
            world.markBlockForUpdate(pos.x, pos.y, pos.z);
        }
    }
}
