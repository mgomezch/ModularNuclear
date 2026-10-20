package com.gtnewhorizons.modularnuclear.mixins.late;

import java.util.ArrayList;

import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnewhorizons.modularnuclear.common.steam.HeavyWaterSteamTurbineHelper;

import gregtech.api.util.GTModHandler;
import gregtech.api.util.TurbineStatCalculator;
import gregtech.common.tileentities.machines.multi.turbines.MTELargeTurbineSteam;

@Mixin(value = MTELargeTurbineSteam.class, remap = false)
public abstract class MixinMTELargeTurbineSteam {

    @Unique
    private int modularnuclear$heavyWaterSteamFlow = 0;

    @Unique
    private int modularnuclear$regularSteamFlow = 0;

    @Inject(method = "fluidIntoPower", at = @At("HEAD"))
    private void modularnuclear$resetFlowTracking(ArrayList<FluidStack> aFluids, TurbineStatCalculator turbine,
        CallbackInfoReturnable<Integer> cir) {
        this.modularnuclear$heavyWaterSteamFlow = 0;
        this.modularnuclear$regularSteamFlow = 0;
    }

    @Redirect(
        method = "fluidIntoPower",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/common/tileentities/machines/multi/turbines/MTELargeTurbineSteam;depleteInput(Lnet/minecraftforge/fluids/FluidStack;)Z"))
    private boolean modularnuclear$trackDepletedSteam(MTELargeTurbineSteam instance, FluidStack depleted) {
        if (depleted != null && depleted.amount > 0) {
            if (HeavyWaterSteamTurbineHelper.isHeavyWaterSteam(depleted)) {
                this.modularnuclear$heavyWaterSteamFlow += depleted.amount;
            } else if (GTModHandler.isAnySteam(depleted)) {
                this.modularnuclear$regularSteamFlow += depleted.amount;
            }
        }
        return instance.depleteInput(depleted);
    }

    @Redirect(
        method = "fluidIntoPower",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/common/tileentities/machines/multi/turbines/MTELargeTurbineSteam;addOutputPartial(Lnet/minecraftforge/fluids/FluidStack;)V"))
    private void modularnuclear$redirectCondensedOutput(MTELargeTurbineSteam instance,
        FluidStack defaultDistilledWater) {
        int totalWater = defaultDistilledWater != null ? defaultDistilledWater.amount : 0;
        HeavyWaterSteamTurbineHelper.distributeCondensedWater(
            instance::addOutputPartial,
            totalWater,
            this.modularnuclear$heavyWaterSteamFlow,
            this.modularnuclear$regularSteamFlow);
    }
}
