package com.gtnewhorizons.modularnuclear.mixins.late;

import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnewhorizons.modularnuclear.common.entity.EntityMeltdownFallout;

import gregtech.common.pollution.Pollution;

@Mixin(value = Pollution.class, remap = false)
public abstract class MixinPollution {

    @Inject(method = "getPollution(Lnet/minecraft/world/World;II)I", at = @At("RETURN"), cancellable = true)
    private static void modularnuclear$onGetPollution(World w, int chunkX, int chunkZ,
        CallbackInfoReturnable<Integer> cir) {
        if (w != null && !w.isRemote) {
            int virtual = EntityMeltdownFallout.getVirtualPollution(w, chunkX, chunkZ);
            if (virtual > 0) {
                int physical = Math.max(0, cir.getReturnValueI());
                cir.setReturnValue((int) Math.min(Integer.MAX_VALUE, (long) physical + virtual));
            }
        }
    }

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
}
