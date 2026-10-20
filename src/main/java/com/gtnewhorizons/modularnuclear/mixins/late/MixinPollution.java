package com.gtnewhorizons.modularnuclear.mixins.late;

import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.modularnuclear.common.entity.EntityMeltdownFallout;

import gregtech.common.pollution.Pollution;

@Mixin(value = Pollution.class, remap = false)
public abstract class MixinPollution {

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
