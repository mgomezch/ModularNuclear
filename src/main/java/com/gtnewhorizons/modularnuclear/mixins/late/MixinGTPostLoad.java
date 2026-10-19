package com.gtnewhorizons.modularnuclear.mixins.late;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.modularnuclear.common.fluid.ModFluids;

import gregtech.loaders.postload.GTPostLoad;

@Mixin(value = GTPostLoad.class, remap = false)
public abstract class MixinGTPostLoad {

    @Inject(method = "identifyAnySteam", at = @At("RETURN"))
    private static void modularnuclear$registerHeavyWaterSteam(CallbackInfo ci) {
        ModFluids.registerWithGregTech();
    }
}
