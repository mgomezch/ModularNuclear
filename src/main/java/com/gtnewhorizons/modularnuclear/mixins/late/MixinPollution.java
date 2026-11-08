package com.gtnewhorizons.modularnuclear.mixins.late;

import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnewhorizons.modularnuclear.common.entity.EntityMeltdownFallout;

import gregtech.common.pollution.Pollution;

/** Ties meltdown fallout to GregTech pollution. See Note [Fallout and GregTech pollution]. */
@Mixin(value = Pollution.class, remap = false)
public abstract class MixinPollution {

    @Shadow
    @Final
    private World world;

    /**
     * Set while reading a chunk's own pollution, without the fallout's virtual pollution.
     * See wrinkle (W1) of Note [Fallout and GregTech pollution].
     */
    @Unique
    private static boolean modularnuclear$physicalOnly;

    @Inject(method = "getPollution(Lnet/minecraft/world/World;II)I", at = @At("RETURN"), cancellable = true)
    private static void modularnuclear$onGetPollution(World w, int chunkX, int chunkZ,
        CallbackInfoReturnable<Integer> cir) {
        if (w != null && !w.isRemote && !modularnuclear$physicalOnly) {
            int virtual = EntityMeltdownFallout.getVirtualPollution(w, chunkX, chunkZ);
            if (virtual > 0) {
                int physical = Math.max(0, cir.getReturnValueI());
                cir.setReturnValue((int) Math.min(Integer.MAX_VALUE, (long) physical + virtual));
            }
        }
    }

    // See wrinkle (W2) of Note [Fallout and GregTech pollution]
    @Inject(method = "addPollution(Lnet/minecraft/world/World;III)V", at = @At("HEAD"))
    private static void modularnuclear$onAddPollution(World w, int chunkX, int chunkZ, int aPollution,
        CallbackInfo ci) {
        if (w != null && !w.isRemote && aPollution < 0) {
            int current = Pollution.getPollution(w, chunkX, chunkZ);
            int actuallyRemoved = Math.min(current, -aPollution);
            if (actuallyRemoved > 0) {
                EntityMeltdownFallout.onPollutionRemoved(w, chunkX, chunkZ, actuallyRemoved);
            }
        }
    }

    /**
     * GregTech's pollution cycle writes each chunk's new pollution here, after it decays and spreads to its neighbours,
     * without going through {@code addPollution}. A chunk losing pollution this way cleans fallout too. See wrinkles
     * (W1) and (W3) of Note [Fallout and GregTech pollution].
     */
    @Inject(method = "setChunkPollution", at = @At("HEAD"))
    private void modularnuclear$onSetChunkPollution(ChunkCoordIntPair coord, int pollution, CallbackInfo ci) {
        int before;
        modularnuclear$physicalOnly = true;
        try {
            before = Pollution.getPollution(world, coord.chunkXPos, coord.chunkZPos);
        } finally {
            modularnuclear$physicalOnly = false;
        }
        if (pollution < before) {
            EntityMeltdownFallout.onPollutionRemoved(world, coord.chunkXPos, coord.chunkZPos, before - pollution);
        }
    }
}
