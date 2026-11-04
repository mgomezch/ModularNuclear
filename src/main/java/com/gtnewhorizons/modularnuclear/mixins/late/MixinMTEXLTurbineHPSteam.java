package com.gtnewhorizons.modularnuclear.mixins.late;

import java.util.ArrayList;

import net.minecraftforge.fluids.Fluid;
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
import gregtech.common.tileentities.machines.multi.xlturbines.MTEXLTurbineHPSteam;

/**
 * Superheated heavy-water steam in the XL high-pressure steam turbine, which tells its fluids by name: it passes for
 * superheated steam (heavy-water steam for steam, burnt as waste), and its share of the exhaust leaves as heavy-water
 * steam.
 */
@Mixin(value = MTEXLTurbineHPSteam.class, remap = false)
public abstract class MixinMTEXLTurbineHPSteam {

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
            target = "Lnet/minecraftforge/fluids/Fluid;getUnlocalizedName(Lnet/minecraftforge/fluids/FluidStack;)Ljava/lang/String;"))
    private String modularnuclear$aliasHeavyWaterSteam(Fluid fluid, FluidStack stack) {
        if (HeavyWaterSteamTurbineHelper.isSuperheatedHeavyWaterSteam(stack)) return "ic2.fluidSuperheatedSteam";
        if (HeavyWaterSteamTurbineHelper.isHeavyWaterSteam(stack)) return "fluid.steam";
        return fluid.getUnlocalizedName(stack);
    }

    @Redirect(
        method = "fluidIntoPower",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/common/tileentities/machines/multi/xlturbines/MTEXLTurbineHPSteam;depleteInput(Lnet/minecraftforge/fluids/FluidStack;)Z"))
    private boolean modularnuclear$trackDepletedSteam(MTEXLTurbineHPSteam instance, FluidStack depleted) {
        if (depleted != null && depleted.amount > 0) {
            if (HeavyWaterSteamTurbineHelper.isSuperheatedHeavyWaterSteam(depleted)) {
                this.modularnuclear$heavyFlow += depleted.amount;
            } else if (depleted.getFluid() == FluidRegistry.getFluid("ic2superheatedsteam")) {
                this.modularnuclear$regularFlow += depleted.amount;
            }
        }
        return instance.depleteInput(depleted);
    }

    @Redirect(
        method = "fluidIntoPower",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/common/tileentities/machines/multi/xlturbines/MTEXLTurbineHPSteam;addOutputPartial(Lnet/minecraftforge/fluids/FluidStack;)V"))
    private void modularnuclear$splitExhaust(MTEXLTurbineHPSteam instance, FluidStack exhaust) {
        HeavyWaterSteamTurbineHelper.splitExhaust(
            instance::addOutputPartial,
            exhaust,
            this.modularnuclear$heavyFlow,
            this.modularnuclear$regularFlow);
    }
}
