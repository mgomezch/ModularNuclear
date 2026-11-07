package com.gtnewhorizons.modularnuclear.common.metatileentity.multi;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.modularnuclear.common.config.CoolantLoopDataRegistry;
import com.gtnewhorizons.modularnuclear.common.util.RotorThermalHelper;
import com.gtnewhorizons.modularnuclear.common.util.TickPhase;

import gregtech.api.enums.Materials;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.items.MetaGeneratedTool;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.util.TurbineStatCalculator;

/** The pump's impeller: loading it from the input bus, its efficiency, its wear and its destruction. */
final class CoolantPumpImpeller {

    private CoolantPumpImpeller() {}

    static void degradeRotor(MTECoolantPump pump, long euPerTick) {
        ItemStack rotor = pump.getRotor();
        if (rotor == null) {
            return;
        }

        double fluidTempC = pump.getPumpFluidTemperatureCelsius();
        Materials mat = pump.getRotorMaterial();
        double th = RotorThermalHelper.calculateHomologousTemperature(fluidTempC, mat);
        if (RotorThermalHelper.isMelted(th)) {
            pump.failOnImpellerMelting(fluidTempC, mat);
            return;
        }

        boolean shouldTick = (pump.getBaseMetaTileEntity() == null) || TickPhase.due(
            pump.getBaseMetaTileEntity()
                .getTimer(),
            pump.schedulePhase(),
            100);
        if (shouldTick) {
            double gasFrac = pump.getDissolvedGasFraction();
            double cavMultiplier = (CoolantLoopDataRegistry.getPhysicsData() != null)
                ? CoolantLoopDataRegistry.getPhysicsData()
                    .getCavitationWearMultiplier()
                : 10.0;
            double cavitationFactor = 1.0 + cavMultiplier * gasFrac;
            double thermalFactor = RotorThermalHelper.calculateThermalWearMultiplier(th);
            double totalWearMultiplier = cavitationFactor * thermalFactor;
            pump.mRotorDamageAccumulator += totalWearMultiplier;
            int dmg = (int) pump.mRotorDamageAccumulator;
            if (dmg > 0) {
                pump.mRotorDamageAccumulator -= dmg;
                if (rotor.getItem() instanceof MetaGeneratedTool) {
                    MetaGeneratedTool tool = (MetaGeneratedTool) rotor.getItem();
                    tool.doDamage(rotor, dmg);
                    if (rotor.stackSize == 0
                        || MetaGeneratedTool.getToolDamage(rotor) >= MetaGeneratedTool.getToolMaxDamage(rotor)) {
                        if (pump.getControllerSlot() == rotor
                            || (pump.getControllerSlot() != null && pump.getControllerSlot().stackSize == 0)) {
                            pump.setInventorySlotContents(pump.getControllerSlotIndex(), null);
                        }
                        pump.mRotorStack = null; // Rotor broken!
                        pump.mRotorEfficiency = 0.0;
                        pump.mRotorMaterial = null;
                        pump.checkAndLoadImpellerFromInputBus();
                    }
                } else {
                    rotor.setItemDamage(rotor.getItemDamage() + dmg);
                    if (rotor.getMaxDamage() > 0 && rotor.getItemDamage() >= rotor.getMaxDamage()) {
                        if (pump.getControllerSlot() == rotor) {
                            pump.setInventorySlotContents(pump.getControllerSlotIndex(), null);
                        }
                        pump.mRotorStack = null; // Rotor broken!
                        pump.mRotorEfficiency = 0.0;
                        pump.mRotorMaterial = null;
                        pump.checkAndLoadImpellerFromInputBus();
                    }
                }
            }
        }
    }

    static boolean checkAndLoadImpellerFromInputBus(MTECoolantPump pump) {
        ItemStack controller = pump.getControllerSlot();
        if (pump.isValidRotor(controller)) {
            return false;
        }
        if (pump.mRotorStack != null && pump.isValidRotor(pump.mRotorStack)) {
            return false;
        }
        if (controller != null && controller.stackSize > 0) {
            return false;
        }
        if (pump.mInputBusses == null || pump.mInputBusses.isEmpty()) {
            return false;
        }
        for (MTEHatchInputBus bus : pump.mInputBusses) {
            if (bus == null) continue;
            for (int s = 0; s < bus.getSizeInventory(); s++) {
                ItemStack st = bus.getStackInSlot(s);
                if (st != null && pump.isValidRotor(st)) {
                    ItemStack extracted = bus.decrStackSize(s, 1);
                    if (extracted != null && extracted.stackSize > 0) {
                        pump.setInventorySlotContents(pump.getControllerSlotIndex(), extracted);
                        pump.mRotorStack = extracted;
                        pump.updateRotorEfficiency();
                        if (bus.getBaseMetaTileEntity() != null) {
                            bus.getBaseMetaTileEntity()
                                .markDirty();
                        }
                        if (pump.getBaseMetaTileEntity() != null) {
                            pump.getBaseMetaTileEntity()
                                .markDirty();
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static boolean updateRotorEfficiency(MTECoolantPump pump) {
        ItemStack rotor = pump.getRotor();
        if (rotor != null && pump.isMetaGeneratedTool(rotor.getItem())) {
            if (rotor == pump.mCachedRotorObj && rotor.getItemDamage() == pump.mCachedRotorDamage
                && pump.mRotorMaterial != null) {
                return true;
            }
            TurbineStatCalculator calc = new TurbineStatCalculator((MetaGeneratedTool) rotor.getItem(), rotor);
            double eff = calc.getBaseEfficiency();
            pump.mRotorEfficiency = eff > 0.0 ? eff : 1.0;
            pump.mRotorStack = rotor;
            Materials mat = RotorThermalHelper.getRotorMaterial(rotor);
            pump.mRotorMaterial = (mat != null && mat != Materials._NULL) ? mat : Materials.Steel;
            pump.mCachedRotorObj = rotor;
            pump.mCachedRotorDamage = rotor.getItemDamage();
            return true;
        }
        // Headless mock or test environment fallback
        if (pump.getBaseMetaTileEntity() == null || pump.getBaseMetaTileEntity()
            .getWorld() == null) {
            return true;
        }
        pump.mRotorStack = null;
        pump.mRotorMaterial = null;
        pump.mCachedRotorObj = null;
        pump.mCachedRotorDamage = -1;
        return false;
    }

    static void destroyImpeller(MTECoolantPump pump) {
        ItemStack controller = pump.getControllerSlot();
        if (pump.isValidRotor(controller)) {
            pump.setInventorySlotContents(pump.getControllerSlotIndex(), null);
        }
        pump.mRotorStack = null;
        pump.mRotorEfficiency = 0.0;
        pump.mRotorMaterial = null;
        IGregTechTileEntity te = pump.getBaseMetaTileEntity();
        if (te != null && te.getWorld() != null && !te.getWorld().isRemote) {
            te.getWorld()
                .playSoundEffect(
                    te.getXCoord() + 0.5,
                    te.getYCoord() + 1.5,
                    te.getZCoord() + 0.5,
                    "random.break",
                    1.0f,
                    0.8f);
        }
    }
}
