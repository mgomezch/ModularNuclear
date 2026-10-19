package com.gtnewhorizons.modularnuclear.common.entity;

import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.ForgeChunkManager;

import com.gtnewhorizons.modularnuclear.ModularNuclear;
import com.gtnewhorizons.modularnuclear.common.block.ModBlocks;

import cpw.mods.fml.common.Loader;
import gregtech.api.hazards.HazardProtection;
import gregtech.api.util.GTUtility;
import gregtech.common.pollution.Pollution;
import ic2.core.IC2Potion;

public class EntityMeltdownFallout extends Entity {

    public static final int SOLIDIFY_TIME = 2400; // 2 minutes (120s * 20 ticks)
    public static final int TOTAL_LIFETIME = 12000; // 10 minutes (600s * 20 ticks)

    private int mAge = 0;
    private boolean mSolidified = false;
    private ForgeChunkManager.Ticket mChunkTicket;

    public void initChunkLoading(ForgeChunkManager.Ticket ticket) {
        this.mChunkTicket = ticket;
        forceChunks();
    }

    public void setupChunkLoading() {
        if (worldObj == null || worldObj.isRemote || mChunkTicket != null) return;
        try {
            mChunkTicket = ForgeChunkManager.requestTicket(ModularNuclear.instance, worldObj, ForgeChunkManager.Type.ENTITY);
            if (mChunkTicket != null) {
                mChunkTicket.bindEntity(this);
                forceChunks();
            }
        } catch (Throwable ignored) {}
    }

    private void forceChunks() {
        if (mChunkTicket == null) return;
        int centerChunkX = MathHelper.floor_double(posX) >> 4;
        int centerChunkZ = MathHelper.floor_double(posZ) >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                try {
                    ForgeChunkManager.forceChunk(mChunkTicket, new ChunkCoordIntPair(centerChunkX + dx, centerChunkZ + dz));
                } catch (Throwable ignored) {}
            }
        }
    }

    public void releaseChunkLoading() {
        if (mChunkTicket != null) {
            try {
                ForgeChunkManager.releaseTicket(mChunkTicket);
            } catch (Throwable ignored) {}
            mChunkTicket = null;
        }
    }

    public ForgeChunkManager.Ticket getChunkTicket() {
        return mChunkTicket;
    }

    @Override
    public void setDead() {
        releaseChunkLoading();
        super.setDead();
        if (worldObj != null && worldObj.isRemote) {
            ModularNuclear.proxy.unregisterFallout(this);
        }
    }

    public static class MeltdownChunkLoadingCallback implements ForgeChunkManager.OrderedLoadingCallback {

        @Override
        public void ticketsLoaded(List<ForgeChunkManager.Ticket> tickets, World world) {
            for (ForgeChunkManager.Ticket ticket : tickets) {
                if (ticket != null && ticket.getEntity() instanceof EntityMeltdownFallout fallout) {
                    fallout.initChunkLoading(ticket);
                }
            }
        }

        @Override
        public List<ForgeChunkManager.Ticket> ticketsLoaded(List<ForgeChunkManager.Ticket> tickets, World world, int maxTicketCount) {
            return tickets;
        }
    }

    public EntityMeltdownFallout(World world) {
        super(world);
        this.setSize(0.1F, 0.1F);
        this.noClip = true;
        this.isImmuneToFire = true;
        this.ignoreFrustumCheck = true;
    }

    public EntityMeltdownFallout(World world, double x, double y, double z) {
        this(world);
        this.setPosition(x, y, z);
    }

    @Override
    protected void entityInit() {}

    @Override
    protected void readEntityFromNBT(NBTTagCompound nbt) {
        mAge = nbt.getInteger("mAge");
        mSolidified = nbt.getBoolean("mSolidified");
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound nbt) {
        nbt.setInteger("mAge", mAge);
        nbt.setBoolean("mSolidified", mSolidified);
    }

    @Override
    public boolean isInvisible() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public AxisAlignedBB getBoundingBox() {
        return null;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        if (!worldObj.isRemote && mChunkTicket == null) {
            setupChunkLoading();
        }

        // 1. Immediate pollution spike on creation
        if (mAge == 0 && !worldObj.isRemote) {
            int chunkX = MathHelper.floor_double(posX) >> 4;
            int chunkZ = MathHelper.floor_double(posZ) >> 4;
            Pollution.addPollution(worldObj, chunkX, chunkZ, 2_000_000);
        }

        mAge++;

        // 2. Client-side visual effect tracking
        if (worldObj.isRemote) {
            ModularNuclear.proxy.registerFallout(this);
        }

        // 3. Radiation in 10-chunk radius (160m)
        if (!worldObj.isRemote && (mAge % 20 == 0)) {
            applyRadiationFallout();
        }

        // 4. Solidify corium into bedrock after 2 minutes (2400 ticks)
        if (!worldObj.isRemote && mAge >= SOLIDIFY_TIME) {
            if (!mSolidified || (mAge % 40 == 0 && mAge <= SOLIDIFY_TIME + 400)) {
                solidifyCorium();
                mSolidified = true;
            }
        }

        // 5. Cleanup after 10 minutes (12000 ticks)
        if (mAge >= TOTAL_LIFETIME) {
            setDead();
        }
    }

    private void applyRadiationFallout() {
        double radiusSq = 160.0 * 160.0; // 10 chunks = 160m

        for (Object obj : worldObj.playerEntities) {
            if (obj instanceof EntityPlayer player) {
                double dx = player.posX - posX;
                double dz = player.posZ - posZ;
                if (dx * dx + dz * dz <= radiusSq) {
                    if (!HazardProtection.isWearingFullRadioHazmat(player)) {
                        // Apply severe GT radioactivity
                        GTUtility.applyRadioactivity(player, 5, 20);
                        // Strip vanilla nausea so it does not distort the camera or clash with the fallout static shader
                        player.removePotionEffect(Potion.confusion.id);
                        // Inflict maximum IC2 radiation potion effect
                        if (Loader.isModLoaded("IC2") && IC2Potion.radiation != null) {
                            player.addPotionEffect(new PotionEffect(IC2Potion.radiation.id, 240, 3));
                        }
                    } else {
                        // Clear any lingering nausea if player is fully protected
                        player.removePotionEffect(Potion.confusion.id);
                    }
                }
            }
        }
    }

    public void solidifyCorium() {
        if (worldObj.isRemote) return;

        int cx = MathHelper.floor_double(posX);
        int cy = MathHelper.floor_double(posY);
        int cz = MathHelper.floor_double(posZ);

        int minChunkX = (cx - 48) >> 4;
        int maxChunkX = (cx + 48) >> 4;
        int minChunkZ = (cz - 48) >> 4;
        int maxChunkZ = (cz + 48) >> 4;

        int maxY = Math.min(255, cy + 10);
        int minY = 1;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!worldObj.getChunkProvider()
                    .chunkExists(chunkX, chunkZ)) continue;
                Chunk chunk = worldObj.getChunkFromChunkCoords(chunkX, chunkZ);
                ExtendedBlockStorage[] storage = chunk.getBlockStorageArray();
                if (storage == null) continue;

                for (int subY = 0; subY < storage.length; subY++) {
                    ExtendedBlockStorage ebs = storage[subY];
                    if (ebs == null) continue;
                    int baseY = subY << 4;
                    if (baseY > maxY || baseY + 15 < minY) continue;

                    for (int lx = 0; lx < 16; lx++) {
                        for (int lz = 0; lz < 16; lz++) {
                            for (int ly = 0; ly < 16; ly++) {
                                int worldY = baseY + ly;
                                if (worldY < minY || worldY > maxY) continue;
                                Block b = ebs.getBlockByExtId(lx, ly, lz);
                                if (b == ModBlocks.blockCorium) {
                                    int worldX = (chunkX << 4) + lx;
                                    int worldZ = (chunkZ << 4) + lz;
                                    worldObj.setBlock(worldX, worldY, worldZ, Blocks.bedrock, 0, 3);
                                    worldObj.playSoundEffect(
                                        worldX + 0.5,
                                        worldY + 0.5,
                                        worldZ + 0.5,
                                        "random.fizz",
                                        0.5F,
                                        2.6F);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public int getAge() {
        return mAge;
    }

    public void setAge(int age) {
        this.mAge = age;
    }

    public boolean isSolidified() {
        return mSolidified;
    }
}
