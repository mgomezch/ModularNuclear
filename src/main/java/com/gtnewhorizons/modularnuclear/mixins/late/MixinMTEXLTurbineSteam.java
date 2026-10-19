package com.gtnewhorizons.modularnuclear.mixins.late;

import java.util.ArrayList;

import net.minecraftforge.fluids.Fluid;
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
import gregtech.common.tileentities.machines.multi.xlturbines.MTEXLTurbineSteam;

@Mixin(value = MTEXLTurbineSteam.class, remap = false)
public abstract class MixinMTEXLTurbineSteam {

    @Unique
    private int modularnuclear$heavyWaterSteamFlow = 0;

    @Unique
    private int modularnuclear$regularSteamFlow = 0;

    @Inject(method = "fluidIntoPower", at = @At("HEAD"))
    private void modularnuclear$resetFlowTracking(ArrayList<FluidStack> aFluids, TurbineStatCalculator turbine,
        CallbackInfoReturnable<Long> cir) {
        this.modularnuclear$heavyWaterSteamFlow = 0;
        this.modularnuclear$regularSteamFlow = 0;
    }

    @Redirect(
        method = "fluidIntoPower",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraftforge/fluids/Fluid;getUnlocalizedName(Lnet/minecraftforge/fluids/FluidStack;)Ljava/lang/String;"))
    private String modularnuclear$aliasHeavyWaterSteamUnlocalized(Fluid fluid, FluidStack stack) {
        if (HeavyWaterSteamTurbineHelper.isHeavyWaterSteam(stack)) {
            return "fluid.steam";
        }
        return fluid.getUnlocalizedName(stack);
    }

    @Redirect(
        method = "fluidIntoPower",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/common/tileentities/machines/multi/xlturbines/MTEXLTurbineSteam;depleteInput(Lnet/minecraftforge/fluids/FluidStack;)Z"))
    private boolean modularnuclear$trackDepletedSteam(MTEXLTurbineSteam instance, FluidStack depleted) {
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
            target = "Lgregtech/common/tileentities/machines/multi/xlturbines/MTEXLTurbineSteam;addOutputPartial(Lnet/minecraftforge/fluids/FluidStack;)V"))
    private void modularnuclear$redirectCondensedOutput(MTEXLTurbineSteam instance, FluidStack defaultDistilledWater) {
        int totalWater = defaultDistilledWater != null ? defaultDistilledWater.amount : 0;
        HeavyWaterSteamTurbineHelper.distributeCondensedWater(
            instance::addOutputPartial,
            totalWater,
            this.modularnuclear$heavyWaterSteamFlow,
            this.modularnuclear$regularSteamFlow);
    }
}
