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

import gregtech.api.util.TurbineStatCalculator;
import gregtech.common.tileentities.machines.multi.turbines.MTELargeTurbineSCSteam;

/**
 * Supercritical heavy-water steam in the large supercritical steam turbine, which takes only the fluid named
 * supercriticalsteam: the heavy grade passes for it, and its share of the exhaust leaves as superheated heavy-water
 * steam.
 */
@Mixin(value = MTELargeTurbineSCSteam.class, remap = false)
public abstract class MixinMTELargeTurbineSCSteam {

    @Unique
    private int modularnuclear$heavyFlow = 0;

    @Unique
    private int modularnuclear$regularFlow = 0;

    @Inject(method = "fluidIntoPower", at = @At("HEAD"))
    private void modularnuclear$resetFlowTracking(ArrayList<FluidStack> aFluids, TurbineStatCalculator turbine,
        CallbackInfoReturnable<Integer> cir) {
        this.modularnuclear$heavyFlow = 0;
        this.modularnuclear$regularFlow = 0;
    }

    @Redirect(
        method = "fluidIntoPower",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraftforge/fluids/FluidStack;isFluidEqual(Lnet/minecraftforge/fluids/FluidStack;)Z"))
    private boolean modularnuclear$acceptHeavyWaterSteam(FluidStack supercritical, FluidStack stack) {
        return supercritical.isFluidEqual(stack) || HeavyWaterSteamTurbineHelper.isSupercriticalHeavyWaterSteam(stack);
    }

    @Redirect(
        method = "fluidIntoPower",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/common/tileentities/machines/multi/turbines/MTELargeTurbineSCSteam;depleteInput(Lnet/minecraftforge/fluids/FluidStack;)Z"))
    private boolean modularnuclear$trackDepletedSteam(MTELargeTurbineSCSteam instance, FluidStack depleted) {
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
            target = "Lgregtech/common/tileentities/machines/multi/turbines/MTELargeTurbineSCSteam;addOutputPartial(Lnet/minecraftforge/fluids/FluidStack;)V"))
    private void modularnuclear$splitExhaust(MTELargeTurbineSCSteam instance, FluidStack exhaust) {
        HeavyWaterSteamTurbineHelper.splitExhaust(
            instance::addOutputPartial,
            exhaust,
            this.modularnuclear$heavyFlow,
            this.modularnuclear$regularFlow);
    }
}
