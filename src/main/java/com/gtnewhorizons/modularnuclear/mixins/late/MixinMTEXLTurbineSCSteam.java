package com.gtnewhorizons.modularnuclear.mixins.late;

import java.util.ArrayList;

import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnewhorizons.modularnuclear.common.steam.HeavyWaterSteamTurbineHelper;

import gregtech.api.util.GTUtility;
import gregtech.api.util.TurbineStatCalculator;
import gregtech.common.tileentities.machines.multi.xlturbines.MTEXLTurbineSCSteam;

/**
 * Supercritical heavy-water steam in the XL supercritical steam turbine, which takes only the fluid named
 * supercriticalsteam: the heavy grade passes for it, and its share of the exhaust leaves as superheated heavy-water
 * steam.
 */
@Mixin(value = MTEXLTurbineSCSteam.class, remap = false)
public abstract class MixinMTEXLTurbineSCSteam {

    @Unique
    private int modularnuclear$heavyFlow = 0;

    @Unique
    private int modularnuclear$regularFlow = 0;

    @Inject(method = "fluidIntoPower", at = @At("HEAD"))
    private void modularnuclear$resetFlowTracking(ArrayList<FluidStack> aFluids, TurbineStatCalculator turbine,
        CallbackInfoReturnable<Long> cir) {
        this.modularnuclear$heavyFlow = 0;
        this.modularnuclear$regularFlow = 0;
    }

    @Redirect(
        method = "fluidIntoPower",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/api/util/GTUtility;areFluidsEqual(Lnet/minecraftforge/fluids/FluidStack;Lnet/minecraftforge/fluids/FluidStack;Z)Z"))
    private boolean modularnuclear$acceptHeavyWaterSteam(FluidStack stack, FluidStack supercritical,
        boolean ignoreNBT) {
        return GTUtility.areFluidsEqual(stack, supercritical, ignoreNBT)
            || HeavyWaterSteamTurbineHelper.isSupercriticalHeavyWaterSteam(stack);
    }

    @Redirect(
        method = "fluidIntoPower",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/common/tileentities/machines/multi/xlturbines/MTEXLTurbineSCSteam;depleteInput(Lnet/minecraftforge/fluids/FluidStack;)Z"))
    private boolean modularnuclear$trackDepletedSteam(MTEXLTurbineSCSteam instance, FluidStack depleted) {
        if (depleted != null && depleted.amount > 0) {
            if (HeavyWaterSteamTurbineHelper.isSupercriticalHeavyWaterSteam(depleted)) {
                this.modularnuclear$heavyFlow += depleted.amount;
            } else if (depleted.getFluid() == FluidRegistry.getFluid("supercriticalsteam")) {
                this.modularnuclear$regularFlow += depleted.amount;
            }
        }
        return instance.depleteInput(depleted);
    }

    @Redirect(
        method = "fluidIntoPower",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/common/tileentities/machines/multi/xlturbines/MTEXLTurbineSCSteam;addOutputPartial(Lnet/minecraftforge/fluids/FluidStack;)V"))
    private void modularnuclear$splitExhaust(MTEXLTurbineSCSteam instance, FluidStack exhaust) {
        HeavyWaterSteamTurbineHelper.splitExhaust(
            instance::addOutputPartial,
            exhaust,
            this.modularnuclear$heavyFlow,
            this.modularnuclear$regularFlow);
    }
}
