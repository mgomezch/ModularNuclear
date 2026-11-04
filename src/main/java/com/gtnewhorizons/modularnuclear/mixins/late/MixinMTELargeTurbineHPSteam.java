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
import gregtech.common.tileentities.machines.multi.turbines.MTELargeTurbineHPSteam;

/**
 * Superheated heavy-water steam in the large high-pressure steam turbine: GregTech's superheated steam list takes
 * it in (ModFluids.registerWithGregTech); its share of the exhaust leaves as heavy-water steam.
 */
@Mixin(value = MTELargeTurbineHPSteam.class, remap = false)
public abstract class MixinMTELargeTurbineHPSteam {

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
            target = "Lgregtech/common/tileentities/machines/multi/turbines/MTELargeTurbineHPSteam;depleteInput(Lnet/minecraftforge/fluids/FluidStack;)Z"))
    private boolean modularnuclear$trackDepletedSteam(MTELargeTurbineHPSteam instance, FluidStack depleted) {
        if (depleted != null && depleted.amount > 0) {
            if (HeavyWaterSteamTurbineHelper.isSuperheatedHeavyWaterSteam(depleted)) {
                this.modularnuclear$heavyFlow += depleted.amount;
            } else if (GTModHandler.isSuperHeatedSteam(depleted)) {
                this.modularnuclear$regularFlow += depleted.amount;
            }
        }
        return instance.depleteInput(depleted);
    }

    @Redirect(
        method = "fluidIntoPower",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/common/tileentities/machines/multi/turbines/MTELargeTurbineHPSteam;addOutputPartial(Lnet/minecraftforge/fluids/FluidStack;)V"))
    private void modularnuclear$splitExhaust(MTELargeTurbineHPSteam instance, FluidStack exhaust) {
        HeavyWaterSteamTurbineHelper.splitExhaust(
            instance::addOutputPartial,
            exhaust,
            this.modularnuclear$heavyFlow,
            this.modularnuclear$regularFlow);
    }
}
