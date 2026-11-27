package com.gtnewhorizons.modularnuclear.common.coolantloop.pump;

import java.util.Map;

import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.modularnuclear.common.config.CoolantLoopDataRegistry;
import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantFluidProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantLoopEngine;
import com.gtnewhorizons.modularnuclear.common.coolantloop.ICoolantLoopDevice;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopSegment;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasProperty;
import com.gtnewhorizons.modularnuclear.common.util.CoolantFluidHelper;
import com.gtnewhorizons.modularnuclear.common.util.LocalizedText;
import com.gtnewhorizons.modularnuclear.common.util.RotorThermalHelper;

import gregtech.api.enums.Materials;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;

/**
 * The coolant pump's tick while its loop is formed: device disconnects and ruptures, power and redstone, the fluid and
 * impeller checks, filling, then circulation: power the impeller, step the engine and run the devices' heat exchange.
 */
final class CoolantPumpCirculation {

    private CoolantPumpCirculation() {}

    /** One tick of the loop. False when the loop failed (it ruptured or exploded). */
    static boolean step(CoolantPumpCore p) {
        if (!p.mLoopFormed || p.mEngine.getSegments()
            .isEmpty()) {
            if (p.mLoopState != CoolantPumpCore.LoopState.EMPTY && p.mLoopState != CoolantPumpCore.LoopState.STOPPED
                && p.mLoopState != CoolantPumpCore.LoopState.FAILED) {
                p.mLoopState = CoolantPumpCore.LoopState.STOPPED;
            }
            IGregTechTileEntity baseTE = p.getBaseMetaTileEntity();
            if (baseTE != null && baseTE.isActive()) {
                baseTE.setActive(false);
            }
            return true;
        }

        if (!p.mLoopAtRest && p.mLoopDevices != null && !p.mLoopDevices.isEmpty()) {
            // Indexed: onDeviceDisconnected replaces the list, and returns straight after
            for (int i = 0, n = p.mLoopDevices.size(); i < n; i++) {
                ICoolantLoopDevice dev = p.mLoopDevices.get(i);
                if (dev != null && dev.isDeviceDisconnected()) {
                    p.onDeviceDisconnected(dev, dev.getDisconnectionReason());
                    return true;
                }
            }
        }

        if (p.mEngine.isRuptured()) {
            p.triggerCatastrophicExplosion(p.engineFailureText(), p.mEngine.getRupturedSegment());
            return false;
        }

        IGregTechTileEntity baseTE = p.getBaseMetaTileEntity();
        if (baseTE == null) {
            return true;
        }

        p.mReservoir.updateReservoirState();
        p.mEngine.setHoldsCoolant(p.mCurrentFillLiters > 0);

        // See Note [The pump on a gas loop]
        GasProperty loopGas = CoolantPumpGasLoop.loopGas(p, p.getReservoirFluid());
        if (loopGas != null) return CoolantPumpGasLoop.step(p, baseTE, loopGas);
        if (p.mCurrentFillLiters <= 0L) CoolantPumpGasLoop.useEngineFor(p, null);

        // A pump without an impeller only runs gas loops. See Note [Pump cores and their hosts]
        PumpImpeller impeller = p.host.getImpeller();
        if (impeller == null) return stepWithoutImpeller(p, baseTE);
        // Draw electrical power if allowed to work (soft hammer / redstone control)
        boolean isWorking = p.isAllowedToWork();
        if (!isWorking) {
            baseTE.setActive(false);
            p.mEngine.setPumpPowered(false);
            p.mEngine.setPumpMechanicalPowerWatts(0.0);
            p.mEngine.setBraking(false);
            if (p.isPowerFailed()) {
                p.setLoopStatus(CoolantPumpCore.shutDownText(ShutDownReasonRegistry.POWER_LOSS));
            } else if (baseTE.wasShutdown() && baseTE.getLastShutDownReason() != null) {
                p.setLoopStatus(CoolantPumpCore.shutDownText(baseTE.getLastShutDownReason()));
            } else if (p.mLoopState == CoolantPumpCore.LoopState.FILLING) {
                p.setLoopStatusKey(
                    "modularnuclear.coolant_loop.pump.status.filling_paused",
                    p.mCurrentFillLiters,
                    p.mRequiredFillLiters);
            } else if (p.mLoopState == CoolantPumpCore.LoopState.DECELERATING
                || p.mLoopState == CoolantPumpCore.LoopState.DRAINING) {
                    p.setLoopStatusKey(
                        "modularnuclear.coolant_loop.pump.status.stopping_paused",
                        p.mCurrentFillLiters,
                        p.mRequiredFillLiters);
                } else if (p.mLoopAtRest) {
                    p.setLoopStatusKey("modularnuclear.coolant_loop.pump.status.at_rest", p.mCurrentFillLiters);
                } else {
                    p.setLoopStatusKey(
                        "modularnuclear.coolant_loop.pump.status.disabled_coasting",
                        p.mEngine.getVolumetricFlowRate() * 1000.0);
                }
            boolean survived = p.stepCoastingAndCheckRupture();
            p.noteLoopAtRest();
            return survived;
        }

        // Validate fluid in reservoir / loop: liquid nuclear fuel fails pump and destroys impeller immediately!
        FluidStack reservoirFluid = p.getReservoirFluid();
        CoolantPumpCore.Step refuseUnsuitableFluidResult = CoolantPumpFluidSafety
            .refuseUnsuitableFluid(p, reservoirFluid);
        if (refuseUnsuitableFluidResult != CoolantPumpCore.Step.CARRY_ON)
            return refuseUnsuitableFluidResult == CoolantPumpCore.Step.DONE;

        // Validate molten fluid temperature
        CoolantFluidProperty currentProp = p.mEngine.getFluid();
        if (currentProp == null && reservoirFluid != null) {
            currentProp = CoolantFluidProperty.get(
                reservoirFluid.getFluid()
                    .getName());
        }
        boolean isMolten = CoolantFluidHelper
            .isMoltenFluid(reservoirFluid != null ? reservoirFluid.getFluid() : null, reservoirFluid, currentProp);

        World loopWorld = p.getBaseMetaTileEntity() != null ? p.getBaseMetaTileEntity()
            .getWorld() : null;
        boolean isLavaNether = ((reservoirFluid != null && CoolantFluidHelper.isLava(reservoirFluid))
            || (currentProp != null && currentProp.isLava())) && CoolantFluidHelper.isNetherWorld(loopWorld);

        CoolantPumpCore.Step checkMoltenCoolantResult = checkMoltenCoolant(
            p,
            reservoirFluid,
            currentProp,
            isMolten,
            isLavaNether);
        if (checkMoltenCoolantResult != CoolantPumpCore.Step.CARRY_ON)
            return checkMoltenCoolantResult == CoolantPumpCore.Step.DONE;

        // Require valid turbine rotor impeller in controller slot
        if (!impeller.updateRotorEfficiency()) {
            p.mLoopState = CoolantPumpCore.LoopState.STOPPED;
            p.mEngine.setPumpPowered(false);
            p.mEngine.setPumpMechanicalPowerWatts(0.0);
            p.mEngine.setBraking(false);
            p.setLoopStatusKey("modularnuclear.coolant_loop.pump.status.missing_rotor");
            boolean survived = p.stepCoastingAndCheckRupture();
            p.noteLoopAtRest();
            return survived;
        }

        // Impeller melting check: if fluid temperature exceeds impeller melting point, melt catastrophically!
        double pumpFluidTemp = p.getPumpFluidTemperatureCelsius();
        Materials rotorMat = impeller.getRotorMaterial();
        double th = RotorThermalHelper.calculateHomologousTemperature(pumpFluidTemp, rotorMat);
        if (RotorThermalHelper.isMelted(th)) {
            impeller.failOnImpellerMelting(pumpFluidTemp, rotorMat);
            return p.stepCoastingAndCheckRupture();
        }

        // Check if high-pressure output hatch is disabled by machine controller cover
        boolean dischargeDisabled = p.isDischargeHatchDisabled();
        CoolantPumpCore.Step stepWithDischargeDisabledResult = CoolantPumpFillDrain
            .stepWithDischargeDisabled(p, baseTE, dischargeDisabled);
        if (stepWithDischargeDisabledResult != CoolantPumpCore.Step.CARRY_ON)
            return stepWithDischargeDisabledResult == CoolantPumpCore.Step.DONE;

        p.mEngine.setBraking(false);

        // A loop at rest may have changed. See Note [Changing a stopped coolant loop]
        CoolantPumpCore.Step stepRestartCrawlResult = CoolantPumpFillDrain.stepRestartCrawl(p, baseTE);
        if (stepRestartCrawlResult != CoolantPumpCore.Step.CARRY_ON)
            return stepRestartCrawlResult == CoolantPumpCore.Step.DONE;
        CoolantPumpCore.Step stepReturningExcessResult = CoolantPumpFillDrain.stepReturningExcess(p, baseTE);
        if (stepReturningExcessResult != CoolantPumpCore.Step.CARRY_ON)
            return stepReturningExcessResult == CoolantPumpCore.Step.DONE;

        // Initial Loop Filling Phase (when hatch is enabled)
        CoolantPumpCore.Step stepFillingResult = CoolantPumpFillDrain
            .stepFilling(p, baseTE, reservoirFluid, currentProp);
        if (stepFillingResult != CoolantPumpCore.Step.CARRY_ON) return stepFillingResult == CoolantPumpCore.Step.DONE;

        // Active Circulation Phase
        impeller.checkMaintenance();
        double effFactor = impeller.getEfficiencyFactor();
        long requiredEU = p.getRequiredPowerDrawEU();
        boolean hasPower = p.drainEnergy(requiredEU);
        if (!hasPower) {
            return p.triggerPowerfail();
        }
        baseTE.setActive(true);

        // Refuse to accelerate flow if molten fluid is below its melting point
        if (isMolten) {
            double minLoopTemp = p.mEngine.getMinLoopTempCelsius();
            double declaredMelting = CoolantFluidHelper.getMeltingPointCelsius(
                reservoirFluid != null ? reservoirFluid.getFluid() : null,
                reservoirFluid,
                currentProp);
            if (minLoopTemp < declaredMelting - CoolantLoopEngine.FREEZE_TOLERANCE_CELSIUS) {
                p.mEngine.setRuptured(true);
                p.triggerCatastrophicExplosion(
                    LocalizedText.of(
                        "modularnuclear.coolant_loop.pump.status.catastrophic_solidification",
                        minLoopTemp,
                        declaredMelting,
                        currentProp != null ? currentProp.getFluidName() : "molten fluid"),
                    p.mEngine.getRupturedSegment());
                return false;
            }
        }

        p.mLoopState = CoolantPumpCore.LoopState.CIRCULATING;
        p.mEngine.setPumpPowered(true);
        double wattsPerEu = (CoolantLoopDataRegistry.getPhysicsData() != null)
            ? CoolantLoopDataRegistry.getPhysicsData()
                .getPumpMechanicalPowerPerEU()
            : 80.0;
        double mechanicalWatts = requiredEU * wattsPerEu * impeller.getRotorEfficiency() * effFactor;
        p.mEngine.setPumpMechanicalPowerWatts(mechanicalWatts);
        impeller.degradeRotor(requiredEU);

        syncDissolvedGases(p);

        // Advance 1 tick (0.05 seconds) of fluid dynamics
        p.mEngine.step(0.05);

        // Process thermal exchange across in-line devices matching segment ID
        double flowRate = p.mEngine.getVolumetricFlowRate();
        for (CoolantPumpCore.DeviceSegmentBinding b : p.getDeviceBindings()) {
            b.device.processThermalExchange(
                b.segment != null ? p.mEngine.getLocalVolumetricFlow(b.segment) : flowRate,
                0.05,
                p.mEngine.getFluid(),
                b.segment);
        }

        // Check for catastrophic rupture
        if (p.mEngine.isRuptured()) {
            p.triggerCatastrophicExplosion(p.engineFailureText(), p.mEngine.getRupturedSegment());
            return false;
        }

        return true;
    }

    /** A gas-only pump whose tank holds no gas it knows: it waits, and whatever its loop still holds settles. */
    private static boolean stepWithoutImpeller(CoolantPumpCore p, IGregTechTileEntity baseTE) {
        baseTE.setActive(false);
        if (p.mLoopState == CoolantPumpCore.LoopState.CIRCULATING) p.mLoopState = CoolantPumpCore.LoopState.STOPPED;
        p.mEngine.setPumpPowered(false);
        p.mEngine.setPumpMechanicalPowerWatts(0.0);
        p.mEngine.setBraking(false);
        p.setLoopStatusKey(
            p.getReservoirFluid() == null ? "modularnuclear.gas_loop.pump.status.no_gas"
                : "modularnuclear.gas_loop.pump.status.not_a_gas");
        return true;
    }

    /**
     * A molten coolant must be at its melting point or hotter to fill or flow. Returns CARRY_ON to carry on
     * with the step.
     */
    private static CoolantPumpCore.Step checkMoltenCoolant(CoolantPumpCore p, FluidStack reservoirFluid,
        CoolantFluidProperty currentProp, boolean isMolten, boolean isLavaNether) {
        if (isMolten && !isLavaNether) {
            double declaredMelting = CoolantFluidHelper.getMeltingPointCelsius(
                reservoirFluid != null ? reservoirFluid.getFluid() : null,
                reservoirFluid,
                currentProp);

            // If the loop contains fluid and temperature drops below melting point, catastrophic solidification
            // rupture!
            if (p.mCurrentFillLiters > 0 && !p.mEngine.getSegments()
                .isEmpty()) {
                double minLoopTemp = p.mEngine.getMinLoopTempCelsius();
                if (minLoopTemp < declaredMelting - CoolantLoopEngine.FREEZE_TOLERANCE_CELSIUS) {
                    p.mEngine.setRuptured(true);
                    p.triggerCatastrophicExplosion(
                        LocalizedText.of(
                            "modularnuclear.coolant_loop.pump.status.catastrophic_solidification",
                            minLoopTemp,
                            declaredMelting,
                            currentProp != null ? currentProp.getFluidName() : "molten fluid"),
                        p.mEngine.getRupturedSegment());
                    return CoolantPumpCore.Step.FAILED;
                }
            } else {
                // Loop is empty (before initial fill): refuse to fill if the reservoir is below the melting point.
                // Unfilled pipe has no temperature, so it never stops a fill: it takes the coolant's as it fills
                double resTemp = p.mReservoir.getReservoirTemperatureCelsius();
                if (Double.isNaN(resTemp)) {
                    resTemp = CoolantFluidHelper.getBaselineInletTemperatureCelsius(
                        reservoirFluid != null ? reservoirFluid.getFluid() : null,
                        reservoirFluid,
                        currentProp,
                        p.getBiomeTemperatureCelsius());
                }
                if (resTemp < declaredMelting - CoolantLoopEngine.FREEZE_TOLERANCE_CELSIUS) {
                    p.mLoopState = CoolantPumpCore.LoopState.STOPPED;
                    p.mEngine.setPumpPowered(false);
                    p.mEngine.setPumpMechanicalPowerWatts(0.0);
                    p.mEngine.setBraking(false);
                    p.setLoopStatusKey(
                        "modularnuclear.coolant_loop.pump.status.refuse_solidify",
                        resTemp,
                        declaredMelting,
                        currentProp != null ? currentProp.getFluidName() : "molten fluid");
                    return CoolantPumpCore.Step.DONE;
                }
            }
        }
        return CoolantPumpCore.Step.CARRY_ON;
    }

    /** Hands the reservoir's dissolved gases to the engine and every segment when they changed. */
    private static void syncDissolvedGases(CoolantPumpCore p) {
        long currentGasVer = p.mReservoir.getDissolvedGasesVersion();
        if (p.mLastSyncedGasVersion != currentGasVer) {
            p.mLastSyncedGasVersion = currentGasVer;
            Map<String, Long> gases = p.mReservoir.getDissolvedGases();
            p.mEngine.setDissolvedGases(gases);
            double gasFrac = p.getDissolvedGasFraction();
            p.mEngine.setDissolvedGasFraction(gasFrac);
            long loopLiters = p.mEngine.getTotalLoopVolumeLiters();
            for (LoopSegment seg : p.mEngine.getSegments()) {
                seg.setDissolvedGases(gases, loopLiters);
                seg.setDissolvedGasFraction(gasFrac);
            }
        }
    }
}
