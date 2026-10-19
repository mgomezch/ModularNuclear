package com.gtnewhorizons.modularnuclear.mixins.late;

import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnewhorizons.modularnuclear.common.steam.HeavyWaterSteamTurbineHelper;

import goodgenerator.blocks.tileEntity.MTECoolantTower;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTUtility;

@Mixin(value = MTECoolantTower.class, remap = false)
public abstract class MixinMTECoolantTower {

    @Unique
    private int modularnuclear$heavyWaterSteamDrained = 0;

    @Unique
    private int modularnuclear$regularSteamDrained = 0;

    @Inject(method = "checkProcessing_EM", at = @At("HEAD"))
    private void modularnuclear$resetCoolantTowerTracking(CallbackInfoReturnable<CheckRecipeResult> cir) {
        this.modularnuclear$heavyWaterSteamDrained = 0;
        this.modularnuclear$regularSteamDrained = 0;
    }

    @Redirect(
        method = "maybeDrainSteam",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/api/util/GTUtility;areFluidsEqual(Lnet/minecraftforge/fluids/FluidStack;Lnet/minecraftforge/fluids/FluidStack;)Z"))
    private boolean modularnuclear$allowHeavyWaterSteam(FluidStack maybeSteam, FluidStack regularSteam) {
        if (GTUtility.areFluidsEqual(maybeSteam, regularSteam)) {
            return true;
        }
        return HeavyWaterSteamTurbineHelper.isHeavyWaterSteam(maybeSteam);
    }

    @Redirect(
        method = "maybeDrainSteam",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/api/metatileentity/implementations/MTEHatchInput;drain(Lnet/minecraftforge/common/util/ForgeDirection;Lnet/minecraftforge/fluids/FluidStack;Z)Lnet/minecraftforge/fluids/FluidStack;"))
    private FluidStack modularnuclear$trackDrainedSteam(MTEHatchInput hatch, ForgeDirection direction,
        FluidStack resource, boolean doDrain) {
        FluidStack drained = hatch.drain(direction, resource, doDrain);
        if (drained != null && drained.amount > 0 && doDrain) {
            if (HeavyWaterSteamTurbineHelper.isHeavyWaterSteam(drained)) {
                this.modularnuclear$heavyWaterSteamDrained += drained.amount;
            } else {
                this.modularnuclear$regularSteamDrained += drained.amount;
            }
        }
        return drained;
    }

    @Redirect(
        method = "checkProcessing_EM",
        at = @At(
            value = "INVOKE",
            target = "Lgoodgenerator/blocks/tileEntity/MTECoolantTower;addOutputPartial(Lnet/minecraftforge/fluids/FluidStack;)V"))
    private void modularnuclear$redirectCoolantTowerOutput(MTECoolantTower instance, FluidStack defaultDistilledWater) {
        int dwAmount = this.modularnuclear$regularSteamDrained / 160;
        int hwAmount = this.modularnuclear$heavyWaterSteamDrained / 160;
        if (dwAmount > 0) {
            instance.addOutputPartial(GTModHandler.getDistilledWater(dwAmount));
        }
        if (hwAmount > 0) {
            instance.addOutputPartial(HeavyWaterSteamTurbineHelper.getHeavyWater(hwAmount));
        }
    }
}
