package com.gtnewhorizons.modularnuclear.common.entity;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

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

        // 1. Immediate pollution spike on creation
        if (mAge == 0 && !worldObj.isRemote) {
            int chunkX = MathHelper.floor_double(posX) >> 4;
            int chunkZ = MathHelper.floor_double(posZ) >> 4;
            Pollution.addPollution(worldObj, chunkX, chunkZ, 2_000_000);
        }

        mAge++;

        // 2. Green particles ("happyVillager") randomly in a 3-chunk radius (48m)
        spawnFalloutParticles();

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

    private void spawnFalloutParticles() {
        Random rand = worldObj.rand;
        // 3-chunk radius = 48 meters
        double radius = 48.0;

        if (worldObj.isRemote) {
            // Client side particle rendering (6 per tick)
            for (int i = 0; i < 6; i++) {
                double angle = rand.nextDouble() * 2 * Math.PI;
                double dist = Math.sqrt(rand.nextDouble()) * radius;
                double px = posX + dist * Math.cos(angle);
                double pz = posZ + dist * Math.sin(angle);
                double py = posY + (rand.nextDouble() * 20.0 - 5.0);
                worldObj.spawnParticle("happyVillager", px, py, pz, 0.0, 0.05 * rand.nextDouble(), 0.0);
            }
        } else if (worldObj instanceof WorldServer server && (mAge % 4 == 0)) {
            // Server side sync packet (4 per 4 ticks)
            for (int i = 0; i < 4; i++) {
                double angle = rand.nextDouble() * 2 * Math.PI;
                double dist = Math.sqrt(rand.nextDouble()) * radius;
                double px = posX + dist * Math.cos(angle);
                double pz = posZ + dist * Math.sin(angle);
                double py = posY + (rand.nextDouble() * 20.0 - 5.0);
                server.func_147487_a("happyVillager", px, py, pz, 1, 0.1, 0.1, 0.1, 0.0);
            }
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
                        // Inflict maximum IC2 radiation potion effect
                        if (Loader.isModLoaded("IC2") && IC2Potion.radiation != null) {
                            player.addPotionEffect(new PotionEffect(IC2Potion.radiation.id, 240, 3));
                        }
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
