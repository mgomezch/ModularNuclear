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

import cpw.mods.fml.common.Loader;
import gregtech.api.hazards.Hazard;
import gregtech.api.hazards.HazardProtection;
import gregtech.api.util.GTUtility;
import gregtech.common.pollution.Pollution;
import ic2.core.IC2Potion;

/*-
Note [Fallout and GregTech pollution]
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
A meltdown leaves an EntityMeltdownFallout.  Its contamination starts at INITIAL_CONTAMINATION (10,000) and sets the
radius of its radiation: 160 blocks at full contamination, shrinking in proportion.  How contamination goes down
depends on GregTech's "Activate Pollution" setting (isPollutionEnabled):

- Pollution off: contamination falls on a timer in onUpdate, INITIAL_CONTAMINATION over TOTAL_LIFETIME ticks
  (10 minutes), and nothing else lowers it.
- Pollution on: there is no timer.  Contamination falls only when pollution in the fallout's area falls, by
  ModularNuclearConfig.falloutContaminationReductionPerPollution per unit of pollution: 62.5 per 30,000, which is
  one block of radius.  The fallout vanishes when contamination reaches zero.

With pollution on, fallout is a pollution problem.  Players clean it with the machines they already use against
pollution, such as the Electric Air Filter, and fallout left alone waits on GregTech's own decay and spread.  Two
halves make that work:

1. The fallout is pollution.  On its first tick it adds 2,000,000 pollution to its chunk (onUpdate).  While it lives,
   getVirtualPollution adds contamination / falloutContaminationReductionPerPollution (4,800,000 at full
   contamination) to Pollution.getPollution in every chunk it affects (isAffectingChunk).  Anything that reads
   pollution sees it, so an Electric Air Filter near the fallout finds its area polluted and runs.
2. Removing pollution cleans the fallout.  Every decrease in a chunk's pollution calls onPollutionRemoved, which
   lowers the contamination of each fallout affecting that chunk.  GregTech lowers pollution in two ways, and
   MixinPollution hooks both:
   - Pollution.addPollution(World, int, int, int) with a negative amount: air filters, scrubbers, /gt pollution.
   - Pollution.setChunkPollution, from GregTech's pollution cycle (every 1,200 ticks), which decays each chunk by
     0.55% and spreads a chunk above 400,000 to its four neighbours.

Moving parts: in this class isPollutionEnabled, onUpdate, isAffectingChunk, getVirtualPollution, onPollutionRemoved
and ACTIVE_SERVER_FALLOUTS (the server's live fallouts, which the static hooks search); MixinPollution's three
injections; and ModularNuclearConfig.falloutContaminationReductionPerPollution.

(W1) The cycle compares stored pollution only.  The cycle reads a chunk's stored pollution, without the fallout's
     virtual share, and writes the new value back through setChunkPollution.  MixinPollution's
     modularnuclear$onSetChunkPollution compares against that same stored value, reading it with
     modularnuclear$physicalOnly set.  Compared against the full getPollution, every cycle would look like a drop of
     millions.
(W2) Removals count the virtual share.  MixinPollution's modularnuclear$onAddPollution caps a removal at
     getPollution, virtual share included, while GregTech clamps the stored amount at zero.  So an air filter keeps
     cleaning the fallout after the chunk's stored pollution is gone, until the fallout itself is.
(W3) Spreading counts as removal.  When the cycle spreads pollution, the source chunk's loss lowers contamination
     and the neighbour's gain does not raise it.  Nothing that adds pollution raises contamination, the fallout's
     own 2,000,000 included.
(W4) Overlaps.  isAffectingChunk covers the 3×3 chunks around the fallout plus every chunk whose centre lies inside
     its radius.  A removal lowers every fallout affecting that chunk, and removals in several affected chunks each
     count in full.  getVirtualPollution reports the largest share among the fallouts affecting a chunk, not their
     sum.
(W5) Pollution off.  Pollution.getPollution returns 0, addPollution does nothing and the cycle never runs, so
     getVirtualPollution returns 0 and the hooks never call onPollutionRemoved: the timer is the only way down.

inworld-tests/test_fallout_decay.py checks both settings against stock GregTech: the timer with pollution off, and
with pollution on the natural cycle, removals with /gt pollution, and an Electric Air Filter cleaning a fallout to
zero.  Its pollution-off case needs the game's GregTech/Pollution.cfg changed first.  inworld-tests/test_meltdown.py
checks a real meltdown's fallout.  NuclearSimulationEngineTest.testVirtualPollutionCalculations checks
getVirtualPollution and onPollutionRemoved.
*/

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
        mChunkTicket = ForgeChunkManager
            .requestTicket(ModularNuclear.instance, worldObj, ForgeChunkManager.Type.ENTITY);
        if (mChunkTicket != null) {
            mChunkTicket.bindEntity(this);
            forceChunks();
        }

    }

    private void forceChunks() {
        if (mChunkTicket == null) return;
        int centerChunkX = MathHelper.floor_double(posX) >> 4;
        int centerChunkZ = MathHelper.floor_double(posZ) >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ForgeChunkManager.forceChunk(mChunkTicket, new ChunkCoordIntPair(centerChunkX + dx, centerChunkZ + dz));

            }
        }
    }

    public void releaseChunkLoading() {
        if (mChunkTicket != null) {
            ForgeChunkManager.releaseTicket(mChunkTicket);

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
            this.dataWatcher.updateObject(12, (float) mContamination);

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

    // Which of the two ways contamination falls applies: see Note [Fallout and GregTech pollution]
    public static boolean isPollutionEnabled() {
        return gregtech.GTMod.proxy != null && gregtech.GTMod.proxy.mPollution;
    }

    public double getContamination() {
        if (worldObj != null && worldObj.isRemote) {
            return this.dataWatcher.getWatchableObjectFloat(12);

        }
        return mContamination;
    }

    public void reduceContamination(double amount) {
        if (worldObj != null && worldObj.isRemote) return;
        if (isDead) return;
        mContamination -= amount;
        if (mContamination <= 0.0) {
            mContamination = 0.0;
            this.dataWatcher.updateObject(12, 0.0f);

            setDead();
        } else {
            this.dataWatcher.updateObject(12, (float) mContamination);

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

    // Pollution from the fallouts affecting a chunk. See Note [Fallout and GregTech pollution]
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
            this.dataWatcher.updateObject(12, (float) mContamination);

        }
        if (this.mContamination <= 0.0) {
            setDead();
        }
    }

    // Pollution removed from a chunk cleans the fallouts affecting it. See Note [Fallout and GregTech pollution]
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

        // 1. Immediate pollution spike on creation. See Note [Fallout and GregTech pollution]
        if (mAge == 0 && !worldObj.isRemote) {
            int chunkX = MathHelper.floor_double(posX) >> 4;
            int chunkZ = MathHelper.floor_double(posZ) >> 4;
            if (isPollutionEnabled()) {
                Pollution.addPollution(worldObj, chunkX, chunkZ, 2_000_000);
            }
        }

        mAge++;

        if (!worldObj.isRemote) {
            // See Note [Fallout and GregTech pollution]
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
        if (HazardProtection.isWearingFullRadioHazmat(player)) {
            return true;
        }

        int count = 0;
        for (int i = 1; i <= 4; i++) {
            ItemStack stack = player.getEquipmentInSlot(i);
            if (stack == null) continue;
            boolean protects = false;
            if (HazardProtection.protectsAgainstHazard(player, stack, Hazard.RADIOLOGICAL)
                || HazardProtection.protectsAgainstHazardFully(player, stack, Hazard.RADIOLOGICAL)
                || HazardProtection.protectsAgainstHazard(stack, Hazard.RADIOLOGICAL)) {
                protects = true;
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
