package com.gtnewhorizons.modularnuclear.common.metatileentity.multi.util;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearBus;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHatch;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.NuclearGridTile;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.ReactorDummy;
import com.gtnewhorizons.modularnuclear.common.nuclear.INuclearTile;
import com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification;

import gregtech.api.items.ItemRadioactiveCell;
import ic2.api.reactor.IReactorComponent;

/**
 * Utility responsible for calculating reactor telemetry, including thermal distributions,
 * component/fuel durability, fluid storage levels, and hatch statistics across the active grid.
 */
public final class NuclearReactorTelemetry {

    private NuclearReactorTelemetry() {}

    public static void update(MTENuclearReactor reactor) {
        if (reactor == null) return;

        double minTemp = Double.MAX_VALUE;
        double maxTemp = -Double.MAX_VALUE;
        double sumTemp = 0.0;
        int tileCount = 0;

        double minCoolDur = Double.MAX_VALUE;
        double maxCoolDur = -Double.MAX_VALUE;
        double sumCoolDur = 0.0;
        int coolItemCount = 0;
        int totalCoolantItems = 0;

        double minFuelDur = Double.MAX_VALUE;
        double maxFuelDur = -Double.MAX_VALUE;
        double sumFuelDur = 0.0;
        int fuelItemCount = 0;
        int totalFuelItems = 0;

        double minCoolFill = Double.MAX_VALUE;
        double maxCoolFill = -Double.MAX_VALUE;
        double sumCoolFill = 0.0;
        int coolantFluidHatchCount = 0;
        long totalCoolantFluid = 0;
        long totalCoolantCapacity = 0;

        double minFuelFill = Double.MAX_VALUE;
        double maxFuelFill = -Double.MAX_VALUE;
        double sumFuelFill = 0.0;
        int fuelFluidHatchCount = 0;
        long totalFuelFluid = 0;
        long totalFuelCapacity = 0;

        int totalCoolantCoreHatches = 0;
        int totalFuelCoreHatches = 0;

        INuclearTile[][] grid = reactor.mGrid;
        int gridSize = reactor.gridSize;

        if (grid != null && gridSize > 0) {
            for (int x = 0; x < gridSize; x++) {
                for (int y = 0; y < gridSize; y++) {
                    INuclearTile tile = grid[x][y];
                    if (tile != null) {
                        double temp = tile.getTemperature();
                        if (temp < minTemp) minTemp = temp;
                        if (temp > maxTemp) maxTemp = temp;
                        sumTemp += temp;
                        tileCount++;
                    }
                    if (tile instanceof NuclearGridTile gt) {
                        if (gt.isBus()) {
                            MTEHatchNuclearBus bus = gt.getBus();
                            ItemStack stack = bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT];
                            boolean isCoolant = bus.mUsedForCooling;
                            if (stack != null && stack.stackSize > 0) {
                                if (NuclearFuelClassification.isItemFuel(stack)) {
                                    isCoolant = false;
                                    totalFuelItems += stack.stackSize;
                                    double dur;
                                    if (stack.getItem() instanceof ItemRadioactiveCell radCell) {
                                        int max = radCell.getMaxDamageEx();
                                        int cur = radCell.getDamageOfStack(stack);
                                        dur = max > 0 ? ((double) (max - cur) / max) * 100.0 : 0.0;
                                    } else if (stack.isItemStackDamageable() && stack.getMaxDamage() > 0) {
                                        dur = ((double) (stack.getMaxDamage() - stack.getItemDamage())
                                            / stack.getMaxDamage()) * 100.0;
                                    } else {
                                        dur = 100.0;
                                    }
                                    dur = Math.max(0.0, Math.min(100.0, dur));
                                    if (dur < minFuelDur) minFuelDur = dur;
                                    if (dur > maxFuelDur) maxFuelDur = dur;
                                    sumFuelDur += dur;
                                    fuelItemCount++;
                                } else if (NuclearFuelClassification.isItemCoolant(stack)
                                    || NuclearFuelClassification.isItemHeatVent(stack)
                                    || NuclearFuelClassification.isItemHeatExchanger(stack)
                                    || (stack.getItem() instanceof IReactorComponent)) {
                                        isCoolant = true;
                                        totalCoolantItems += stack.stackSize;
                                        double dur;
                                        if (stack.getItem() instanceof IReactorComponent comp) {
                                            ReactorDummy dummy = reactor.getReactorDummy();
                                            dummy.setCurrentTile(gt);
                                            int maxH = comp.getMaxHeat(dummy, stack, gt.getGx(), gt.getGy());
                                            int curH = comp.getCurrentHeat(dummy, stack, gt.getGx(), gt.getGy());
                                            dur = maxH > 0 ? ((double) (maxH - curH) / maxH) * 100.0 : 100.0;
                                        } else if (stack.isItemStackDamageable() && stack.getMaxDamage() > 0) {
                                            dur = ((double) (stack.getMaxDamage() - stack.getItemDamage())
                                                / stack.getMaxDamage()) * 100.0;
                                        } else {
                                            dur = 100.0;
                                        }
                                        dur = Math.max(0.0, Math.min(100.0, dur));
                                        if (dur < minCoolDur) minCoolDur = dur;
                                        if (dur > maxCoolDur) maxCoolDur = dur;
                                        sumCoolDur += dur;
                                        coolItemCount++;
                                    }
                            }
                            if (isCoolant) {
                                totalCoolantCoreHatches++;
                            } else {
                                totalFuelCoreHatches++;
                            }
                        } else if (gt.isHatch()) {
                            MTEHatchNuclearHatch hatch = gt.getHatch();
                            boolean isCoolant = hatch.mUsedForCooling;
                            if (hatch.mInputFluid != null && hatch.mInputFluid.amount > 0) {
                                if (NuclearFuelClassification.isCoolantFluid(hatch.mInputFluid)
                                    && !NuclearFuelClassification.isFluidFuel(hatch.mInputFluid)) {
                                    isCoolant = true;
                                } else if (NuclearFuelClassification.isFluidFuel(hatch.mInputFluid)) {
                                    isCoolant = false;
                                }
                            }
                            long cap = hatch.mCapacity;
                            long amt = (hatch.mInputFluid != null) ? hatch.mInputFluid.amount : 0;
                            double fill = cap > 0 ? ((double) amt / cap) * 100.0 : 0.0;
                            fill = Math.max(0.0, Math.min(100.0, fill));

                            if (isCoolant) {
                                totalCoolantCoreHatches++;
                                coolantFluidHatchCount++;
                                totalCoolantFluid += amt;
                                totalCoolantCapacity += cap;
                                if (fill < minCoolFill) minCoolFill = fill;
                                if (fill > maxCoolFill) maxCoolFill = fill;
                                sumCoolFill += fill;
                            } else {
                                totalFuelCoreHatches++;
                                fuelFluidHatchCount++;
                                totalFuelFluid += amt;
                                totalFuelCapacity += cap;
                                if (fill < minFuelFill) minFuelFill = fill;
                                if (fill > maxFuelFill) maxFuelFill = fill;
                                sumFuelFill += fill;
                            }
                        }
                    }
                }
            }
        }

        reactor.mMinTileTemp = (tileCount > 0) ? minTemp : 0.0;
        reactor.mMaxTileTemp = (tileCount > 0) ? maxTemp : 0.0;
        reactor.mAvgTileTemp = (tileCount > 0) ? sumTemp / tileCount : 0.0;

        reactor.mMinCoolantItemDur = (coolItemCount > 0) ? minCoolDur : 0.0;
        reactor.mMaxCoolantItemDur = (coolItemCount > 0) ? maxCoolDur : 0.0;
        reactor.mAvgCoolantItemDur = (coolItemCount > 0) ? sumCoolDur / coolItemCount : 0.0;

        reactor.mMinFuelItemDur = (fuelItemCount > 0) ? minFuelDur : 0.0;
        reactor.mMaxFuelItemDur = (fuelItemCount > 0) ? maxFuelDur : 0.0;
        reactor.mAvgFuelItemDur = (fuelItemCount > 0) ? sumFuelDur / fuelItemCount : 0.0;

        reactor.mMinCoolantHatchFill = (coolantFluidHatchCount > 0) ? minCoolFill : 0.0;
        reactor.mMaxCoolantHatchFill = (coolantFluidHatchCount > 0) ? maxCoolFill : 0.0;
        reactor.mAvgCoolantHatchFill = (coolantFluidHatchCount > 0) ? sumCoolFill / coolantFluidHatchCount : 0.0;

        reactor.mMinFuelHatchFill = (fuelFluidHatchCount > 0) ? minFuelFill : 0.0;
        reactor.mMaxFuelHatchFill = (fuelFluidHatchCount > 0) ? maxFuelFill : 0.0;
        reactor.mAvgFuelHatchFill = (fuelFluidHatchCount > 0) ? sumFuelFill / fuelFluidHatchCount : 0.0;

        reactor.mTotalFuelItems = totalFuelItems;
        reactor.mTotalCoolantItems = totalCoolantItems;

        reactor.mTotalCoolantFluid = totalCoolantFluid;
        reactor.mTotalCoolantCapacity = totalCoolantCapacity;
        reactor.mTotalFuelFluid = totalFuelFluid;
        reactor.mTotalFuelCapacity = totalFuelCapacity;

        reactor.mCoolantHatchCount = totalCoolantCoreHatches;
        reactor.mFuelHatchCount = totalFuelCoreHatches;
    }
}
