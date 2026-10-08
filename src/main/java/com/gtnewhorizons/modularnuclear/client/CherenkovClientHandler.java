package com.gtnewhorizons.modularnuclear.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.modularnuclear.client.particle.EntityCherenkovBeamFX;
import com.gtnewhorizons.modularnuclear.client.particle.EntityCherenkovFX;
import com.gtnewhorizons.modularnuclear.client.renderer.CherenkovWorldRenderer;
import com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;

@SideOnly(Side.CLIENT)
public class CherenkovClientHandler {

    private static final Map<MTENuclearReactor, Set<BlockPos>> LIT_HATCHES = new HashMap<>();
    private static final Map<MTENuclearReactor, EntityCherenkovBeamFX> ACTIVE_BEAMS = new HashMap<>();

    private static class BlockPos {

        final int x, y, z;

        BlockPos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BlockPos)) return false;
            BlockPos p = (BlockPos) o;
            return x == p.x && y == p.y && z == p.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, z);
        }
    }

    public static void update(MTENuclearReactor reactor, IGregTechTileEntity base) {
        World world = base.getWorld();
        if (world == null) return;

        if (!ModularNuclearConfig.enableCherenkovRadiation || !base.isActive()) {
            if (reactor.mClientWaterBlocks > 0 || LIT_HATCHES.containsKey(reactor) || ACTIVE_BEAMS.containsKey(reactor)) {
                clearHatchLights(reactor, world);
                reactor.mClientWaterBlocks = 0;
                reactor.mClientCherenkovIntensity = 0f;
                EntityCherenkovBeamFX beam = ACTIVE_BEAMS.remove(reactor);
                if (beam != null) {
                    beam.setDead();
                }
                CherenkovWorldRenderer.unregister(reactor);
            }
            return;
        }

        long tick = base.getTimer();
        // Infrequent check: scan water every 60 ticks (3.0 seconds at 20 TPS)
        if (tick % 60 == 0 || reactor.mClientWaterBlocks == -1) {
            scanWater(reactor, base, world);
        }

        if (reactor.mClientWaterBlocks > 0 && reactor.mClientCherenkovIntensity > 0f) {
            spawnParticles(reactor, base, world);
        }
    }

    private static void scanWater(MTENuclearReactor reactor, IGregTechTileEntity base, World world) {
        ForgeDirection facing = base.getFrontFacing();
        ForgeDirection back = facing.getOpposite();

        int tier = reactor.mClientTier;
        int offset = (tier == 3) ? 7 : (tier == 2) ? 5 : 3;
        int radius = (tier == 3) ? 6 : (tier == 2) ? 4 : 2;

        int centerX = base.getXCoord() + back.offsetX * offset;
        int centerZ = base.getZCoord() + back.offsetZ * offset;
        int baseY = base.getYCoord();

        // Hatches are located on the top slice of the reactor (baseY + 3).
        // Cherenkov radiation occurs strictly in the water pool ABOVE the reactor,
        // activated when any of the 3 layers immediately in front of/above the hatches contain water.
        int hatchTopY = baseY + 3;
        int minAboveY = hatchTopY + 1; // First layer above hatches (baseY + 4)
        int maxAboveY = hatchTopY + 10; // Scan up to 10 blocks above reactor

        int waterInFirst3Layers = 0;
        int totalWaterAbove = 0;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (int y = minAboveY; y <= maxAboveY; y++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz <= radius * radius + 1) {
                        Block b = world.getBlock(centerX + dx, y, centerZ + dz);
                        if (b != null && b.getMaterial() == Material.water) {
                            totalWaterAbove++;
                            if (y <= minAboveY + 2) {
                                waterInFirst3Layers++;
                            }
                            if (y < minY) minY = y;
                            if (y > maxY) maxY = y;
                        }
                    }
                }
            }
        }

        if (waterInFirst3Layers > 0) {
            reactor.mClientWaterBlocks = totalWaterAbove;
            reactor.mClientWaterMinY = minY;
            reactor.mClientWaterMaxY = maxY + 1.0;
            reactor.mClientCenterX = centerX + 0.5;
            reactor.mClientCenterZ = centerZ + 0.5;
            reactor.mClientRadius = radius + 0.5;
            reactor.mClientCherenkovIntensity = Math.min(1.0f, 0.40f + (totalWaterAbove / 15.0f) * 0.60f);
            CherenkovWorldRenderer.register(reactor);

            EntityCherenkovBeamFX beam = ACTIVE_BEAMS.get(reactor);
            if (beam == null || beam.isDead || beam.worldObj != world) {
                beam = new EntityCherenkovBeamFX(world, reactor);
                Minecraft.getMinecraft().effectRenderer.addEffect(beam);
                ACTIVE_BEAMS.put(reactor, beam);
            }

            updateHatchesLight(reactor, world, centerX, centerZ, hatchTopY, radius, true);
        } else {
            reactor.mClientWaterBlocks = 0;
            reactor.mClientCherenkovIntensity = 0f;
            CherenkovWorldRenderer.unregister(reactor);
            EntityCherenkovBeamFX beam = ACTIVE_BEAMS.remove(reactor);
            if (beam != null) {
                beam.setDead();
            }
            clearHatchLights(reactor, world);
        }
    }

    private static void updateHatchesLight(MTENuclearReactor reactor, World world, int centerX, int centerZ,
        int hatchTopY, int radius, boolean enable) {
        if (!enable || !ModularNuclearConfig.enableCherenkovLightEmission) {
            clearHatchLights(reactor, world);
            return;
        }

        byte targetLight = (byte) Math.max(1, Math.min(15, ModularNuclearConfig.cherenkovLightLevel));
        Set<BlockPos> currentPos = new HashSet<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radius * radius + 1) {
                    int hx = centerX + dx;
                    int hy = hatchTopY;
                    int hz = centerZ + dz;
                    TileEntity te = world.getTileEntity(hx, hy, hz);
                    if (te instanceof BaseMetaTileEntity) {
                        BlockPos pos = new BlockPos(hx, hy, hz);
                        currentPos.add(pos);
                        BaseMetaTileEntity bmte = (BaseMetaTileEntity) te;
                        if (bmte.getLightValue() != targetLight) {
                            bmte.setLightValue(targetLight);
                            world.setLightValue(EnumSkyBlock.Block, hx, hy, hz, targetLight);
                            world.updateLightByType(EnumSkyBlock.Block, hx, hy, hz);
                        }
                    }
                }
            }
        }

        Set<BlockPos> prevPos = LIT_HATCHES.put(reactor, currentPos);
        if (prevPos != null) {
            for (BlockPos p : prevPos) {
                if (!currentPos.contains(p)) {
                    resetHatchLight(world, p.x, p.y, p.z);
                }
            }
        }
    }

    private static void clearHatchLights(MTENuclearReactor reactor, World world) {
        Set<BlockPos> prevPos = LIT_HATCHES.remove(reactor);
        if (prevPos != null && world != null) {
            for (BlockPos p : prevPos) {
                resetHatchLight(world, p.x, p.y, p.z);
            }
        }
    }

    private static void resetHatchLight(World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof BaseMetaTileEntity) {
            ((BaseMetaTileEntity) te).setLightValue((byte) 0);
        }
        world.setLightValue(EnumSkyBlock.Block, x, y, z, 0);
        world.updateLightByType(EnumSkyBlock.Block, x, y, z);
    }

    private static void spawnParticles(MTENuclearReactor reactor, IGregTechTileEntity base, World world) {
        Random rand = world.rand;
        for (int i = 0; i < 2; i++) {
            double angle = rand.nextDouble() * Math.PI * 2;
            double dist = Math.sqrt(rand.nextDouble()) * (reactor.mClientRadius * 0.85);
            double px = reactor.mClientCenterX + Math.cos(angle) * dist;
            double pz = reactor.mClientCenterZ + Math.sin(angle) * dist;
            double py = reactor.mClientWaterMinY
                + rand.nextDouble() * (reactor.mClientWaterMaxY - reactor.mClientWaterMinY);

            Block b = world.getBlock((int) Math.floor(px), (int) Math.floor(py), (int) Math.floor(pz));
            if (b != null && b.getMaterial() == Material.water) {
                double vx = (rand.nextDouble() - 0.5) * 0.012;
                double vy = 0.02 + rand.nextDouble() * 0.03;
                double vz = (rand.nextDouble() - 0.5) * 0.012;

                Minecraft.getMinecraft().effectRenderer
                    .addEffect(new EntityCherenkovFX(world, px, py, pz, vx, vy, vz, reactor.mClientCherenkovIntensity));
            }
        }
    }

    public static void onRemoval(MTENuclearReactor reactor) {
        World world = (reactor.getBaseMetaTileEntity() != null) ? reactor.getBaseMetaTileEntity()
            .getWorld() : null;
        clearHatchLights(reactor, world);
        reactor.mClientWaterBlocks = 0;
        reactor.mClientCherenkovIntensity = 0f;
        EntityCherenkovBeamFX beam = ACTIVE_BEAMS.remove(reactor);
        if (beam != null) {
            beam.setDead();
        }
        CherenkovWorldRenderer.unregister(reactor);
    }
}
