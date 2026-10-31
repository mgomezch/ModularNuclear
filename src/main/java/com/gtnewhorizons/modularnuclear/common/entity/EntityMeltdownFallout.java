package com.gtnewhorizons.modularnuclear.common.entity;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;

import com.gtnewhorizons.modularnuclear.ModularNuclear;
import com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopChunkLoader;
import com.gtnewhorizons.modularnuclear.common.util.Swallowed;

import cpw.mods.fml.common.Loader;
import gregtech.api.hazards.Hazard;
import gregtech.api.hazards.HazardProtection;
import gregtech.api.util.GTUtility;
import gregtech.common.pollution.Pollution;
import ic2.core.IC2Potion;

public class EntityMeltdownFallout extends Entity {

    public static final int INITIAL_CONTAMINATION = 10000;
    public static final int TOTAL_LIFETIME = 12000; // 10 minutes (600s * 20 ticks) when pollution disabled

    private static final Set<EntityMeltdownFallout> ACTIVE_SERVER_FALLOUTS = Collections
        .newSetFromMap(new ConcurrentHashMap<>());

    private int mAge = 0;
    private double mContamination = INITIAL_CONTAMINATION;
    private ForgeChunkManager.Ticket mChunkTicket;

    public void initChunkLoading(ForgeChunkManager.Ticket ticket) {
        this.mChunkTicket = ticket;
        forceChunks();
    }

    public void setupChunkLoading() {
        if (worldObj == null || worldObj.isRemote || mChunkTicket != null) return;
        try {
            mChunkTicket = ForgeChunkManager
                .requestTicket(ModularNuclear.instance, worldObj, ForgeChunkManager.Type.ENTITY);
            if (mChunkTicket != null) {
                mChunkTicket.bindEntity(this);
                forceChunks();
            }
        } catch (Throwable swallowed) {
            Swallowed.note(swallowed);
        }
    }

    private void forceChunks() {
        if (mChunkTicket == null) return;
        int centerChunkX = MathHelper.floor_double(posX) >> 4;
        int centerChunkZ = MathHelper.floor_double(posZ) >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                try {
                    ForgeChunkManager
                        .forceChunk(mChunkTicket, new ChunkCoordIntPair(centerChunkX + dx, centerChunkZ + dz));
                } catch (Throwable swallowed) {
                    Swallowed.note(swallowed);
                }
            }
        }
    }

    public void releaseChunkLoading() {
        if (mChunkTicket != null) {
            try {
                ForgeChunkManager.releaseTicket(mChunkTicket);
            } catch (Throwable swallowed) {
                Swallowed.note(swallowed);
            }
            mChunkTicket = null;
        }
    }

    public ForgeChunkManager.Ticket getChunkTicket() {
        return mChunkTicket;
    }

    @Override
    public void setDead() {
        ACTIVE_SERVER_FALLOUTS.remove(this);
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
                } else if (LoopChunkLoader.isLoopTicket(ticket)) {
                    LoopChunkLoader.restore(ticket, world);
                }
            }
        }

        @Override
        public List<ForgeChunkManager.Ticket> ticketsLoaded(List<ForgeChunkManager.Ticket> tickets, World world,
            int maxTicketCount) {
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
    protected void entityInit() {
        this.dataWatcher.addObject(12, (float) INITIAL_CONTAMINATION);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound nbt) {
        mAge = nbt.getInteger("mAge");
        if (nbt.hasKey("mContamination")) {
            mContamination = nbt.getDouble("mContamination");
        } else {
            mContamination = INITIAL_CONTAMINATION;
        }
        if (this.dataWatcher != null) {
            try {
                this.dataWatcher.updateObject(12, (float) mContamination);
            } catch (Throwable swallowed) {
                Swallowed.note(swallowed);
            }
        }
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound nbt) {
        nbt.setInteger("mAge", mAge);
        nbt.setDouble("mContamination", mContamination);
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

    public static boolean isPollutionEnabled() {
        try {
            return gregtech.GTMod.proxy != null && gregtech.GTMod.proxy.mPollution;
        } catch (Throwable swallowed) {
            Swallowed.note(swallowed);
            return false;
        }
    }

    public double getContamination() {
        if (worldObj != null && worldObj.isRemote) {
            try {
                return this.dataWatcher.getWatchableObjectFloat(12);
            } catch (Throwable swallowed) {
                Swallowed.note(swallowed);
            }
        }
        return mContamination;
    }

    public void reduceContamination(double amount) {
        if (worldObj != null && worldObj.isRemote) return;
        if (isDead) return;
        mContamination -= amount;
        if (mContamination <= 0.0) {
            mContamination = 0.0;
            try {
                this.dataWatcher.updateObject(12, 0.0f);
            } catch (Throwable swallowed) {
                Swallowed.note(swallowed);
            }
            setDead();
        } else {
            try {
                this.dataWatcher.updateObject(12, (float) mContamination);
            } catch (Throwable swallowed) {
                Swallowed.note(swallowed);
            }
        }
    }

    public boolean isAffectingChunk(int chunkX, int chunkZ) {
        if (this.isDead || this.mContamination <= 0.0) return false;
        int entityChunkX = MathHelper.floor_double(this.posX) >> 4;
        int entityChunkZ = MathHelper.floor_double(this.posZ) >> 4;

        if (Math.abs(chunkX - entityChunkX) <= 1 && Math.abs(chunkZ - entityChunkZ) <= 1) {
            return true;
        }

        double currentRadius = 160.0 * (this.mContamination / (double) INITIAL_CONTAMINATION);
        double chunkCenterX = (chunkX << 4) + 8.0;
        double chunkCenterZ = (chunkZ << 4) + 8.0;
        double dx = chunkCenterX - this.posX;
        double dz = chunkCenterZ - this.posZ;
        return (dx * dx + dz * dz) <= (currentRadius * currentRadius);
    }

    public static int getVirtualPollution(World world, int chunkX, int chunkZ) {
        if (world == null || !isPollutionEnabled()) return 0;
        if (ACTIVE_SERVER_FALLOUTS.isEmpty()) return 0;

        double reductionRatio = ModularNuclearConfig.falloutContaminationReductionPerPollution;
        if (reductionRatio <= 0.0) return 0;

        int totalVirtual = 0;
        for (EntityMeltdownFallout fallout : ACTIVE_SERVER_FALLOUTS) {
            if (fallout.isDead || fallout.worldObj != world || fallout.mContamination <= 0.0) continue;

            if (fallout.isAffectingChunk(chunkX, chunkZ)) {
                int equivalent = (int) Math
                    .min(Integer.MAX_VALUE / 2, Math.round(fallout.mContamination / reductionRatio));
                if (equivalent > totalVirtual) {
                    totalVirtual = equivalent;
                }
            }
        }
        return totalVirtual;
    }

    public static void registerActiveFallout(EntityMeltdownFallout fallout) {
        if (fallout != null) {
            ACTIVE_SERVER_FALLOUTS.add(fallout);
        }
    }

    public static void clearActiveFallouts() {
        ACTIVE_SERVER_FALLOUTS.clear();
    }

    public static Set<EntityMeltdownFallout> getActiveServerFallouts() {
        return Collections.unmodifiableSet(ACTIVE_SERVER_FALLOUTS);
    }

    public void setContamination(double contamination) {
        this.mContamination = Math.max(0.0, contamination);
        if (worldObj != null && !worldObj.isRemote) {
            try {
                this.dataWatcher.updateObject(12, (float) mContamination);
            } catch (Throwable swallowed) {
                Swallowed.note(swallowed);
            }
        }
        if (this.mContamination <= 0.0) {
            setDead();
        }
    }

    public static void onPollutionRemoved(World world, int chunkX, int chunkZ, int removedPollution) {
        if (world == null || world.isRemote || removedPollution <= 0) return;
        if (ACTIVE_SERVER_FALLOUTS.isEmpty()) return;

        double reductionRatio = ModularNuclearConfig.falloutContaminationReductionPerPollution;
        if (reductionRatio <= 0.0) return;

        double contaminationReduction = removedPollution * reductionRatio;

        for (EntityMeltdownFallout fallout : ACTIVE_SERVER_FALLOUTS) {
            if (fallout.isDead || fallout.worldObj != world) continue;

            if (fallout.isAffectingChunk(chunkX, chunkZ)) {
                fallout.reduceContamination(contaminationReduction);
            }
        }
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        if (!worldObj.isRemote) {
            ACTIVE_SERVER_FALLOUTS.add(this);
            if (mChunkTicket == null) {
                setupChunkLoading();
            }
        } else {
            ModularNuclear.proxy.registerFallout(this);
        }

        // 1. Immediate pollution spike on creation
        if (mAge == 0 && !worldObj.isRemote) {
            int chunkX = MathHelper.floor_double(posX) >> 4;
            int chunkZ = MathHelper.floor_double(posZ) >> 4;
            if (isPollutionEnabled()) {
                Pollution.addPollution(worldObj, chunkX, chunkZ, 2_000_000);
            }
        }

        mAge++;

        if (!worldObj.isRemote) {
            if (!isPollutionEnabled()) {
                // When pollution is disabled, fallout counter reduces automatically over TOTAL_LIFETIME (12000 ticks)
                double decayPerTick = (double) INITIAL_CONTAMINATION / (double) TOTAL_LIFETIME;
                reduceContamination(decayPerTick);
            } else {
                // When pollution is enabled, no fixed duration; entity vanishes only when counter reaches 0
                if (mContamination <= 0.0) {
                    setDead();
                    return;
                }
            }

            // 3. Radiation in radius proportional to contamination
            if (mAge % 20 == 0 && mContamination > 0.0) {
                applyRadiationFallout();
            }
        }
    }

    public static boolean isWearingFullHazmat(EntityPlayer player) {
        if (player == null) return false;
        try {
            if (HazardProtection.isWearingFullRadioHazmat(player)) {
                return true;
            }
        } catch (Throwable swallowed) {
            Swallowed.note(swallowed);
        }
        int count = 0;
        for (int i = 1; i <= 4; i++) {
            ItemStack stack = player.getEquipmentInSlot(i);
            if (stack == null) continue;
            boolean protects = false;
            try {
                if (HazardProtection.protectsAgainstHazard(player, stack, Hazard.RADIOLOGICAL)
                    || HazardProtection.protectsAgainstHazardFully(player, stack, Hazard.RADIOLOGICAL)
                    || HazardProtection.protectsAgainstHazard(stack, Hazard.RADIOLOGICAL)) {
                    protects = true;
                }
            } catch (Throwable swallowed) {
                Swallowed.note(swallowed);
            }
            if (!protects && stack.getUnlocalizedName() != null) {
                String lower = stack.getUnlocalizedName()
                    .toLowerCase();
                if (lower.contains("hazmat") || lower.contains("rubboot")
                    || lower.contains("rubber")
                    || lower.contains("radiation")) {
                    protects = true;
                }
            }
            if (!protects && stack.getItem() != null) {
                String id = (String) net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem());
                if (id != null) {
                    String lowerId = id.toLowerCase();
                    if (lowerId.contains("hazmat") || lowerId.contains("rubboot")
                        || lowerId.contains("rubber")
                        || lowerId.contains("radiation")) {
                        protects = true;
                    }
                }
            }
            if (protects) count++;
        }
        return count >= 4;
    }

    private void applyRadiationFallout() {
        double currentRadius = 160.0 * (mContamination / (double) INITIAL_CONTAMINATION);
        if (currentRadius <= 0.0) return;
        double radiusSq = currentRadius * currentRadius;

        for (Object obj : worldObj.playerEntities) {
            if (obj instanceof EntityPlayer player) {
                double dx = player.posX - posX;
                double dz = player.posZ - posZ;
                if (dx * dx + dz * dz <= radiusSq) {
                    if (!isWearingFullHazmat(player)) {
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

    public int getAge() {
        return (int) Math.round(getContamination());
    }

    public int getLifetimeAge() {
        return mAge;
    }

    public void setAge(int age) {
        this.mAge = age;
    }

    public boolean isSolidified() {
        return false;
    }
}
