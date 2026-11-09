package com.gtnewhorizons.modularnuclear.common.metatileentity.multi;

import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.modularnuclear.common.config.CoolantLoopDataRegistry;
import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantFluidProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopSegment;
import com.gtnewhorizons.modularnuclear.common.util.CoolantFluidHelper;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** Filling a loop from the reservoir, and stopping and draining it back. */
final class CoolantPumpFillDrain {

    private CoolantPumpFillDrain() {}

    // See Note [Changing a stopped coolant loop]
    /** A loop at rest is crawled again before its pump can start. Returns CARRY_ON to carry on with the step. */
    static MTECoolantPump.Step stepRestartCrawl(MTECoolantPump pump, IGregTechTileEntity baseTE) {
        if (!pump.mLoopAtRest) return MTECoolantPump.Step.CARRY_ON;
        if (pump.mCurrentFillLiters <= 0L) {
            pump.mLoopAtRest = false;
            return MTECoolantPump.Step.CARRY_ON;
        }
        stopPumping(pump, baseTE);
        if (pump.mActiveCrawl == null) {
            pump.setLoopStatusKey("modularnuclear.coolant_loop.pump.status.restart_crawl");
            pump.mRestartCrawlRunning = true;
            pump.crawlLoopGraph();
        }
        return MTECoolantPump.Step.DONE;
    }

    // See Note [Changing a stopped coolant loop]
    /**
     * Returns coolant to the reservoir when a change at rest left the loop holding less. Returns CARRY_ON to carry on
     * with the step.
     */
    static MTECoolantPump.Step stepReturningExcess(MTECoolantPump pump, IGregTechTileEntity baseTE) {
        if (pump.mCurrentFillLiters <= pump.mRequiredFillLiters || pump.mEngine.getVolumetricFlowRate() > 0.0) {
            return MTECoolantPump.Step.CARRY_ON;
        }
        stopPumping(pump, baseTE);
        long excess = pump.mCurrentFillLiters - pump.mRequiredFillLiters;
        int returned = pump.fillIntoReservoir((int) Math.min(pump.currentFillRateLitersPerTick(), excess));
        if (returned <= 0) {
            pump.mLoopState = MTECoolantPump.LoopState.STOPPED;
            pump.setLoopStatusKey("modularnuclear.coolant_loop.pump.status.excess_no_room", excess);
            return MTECoolantPump.Step.DONE;
        }
        pump.mCurrentFillLiters -= returned;
        if (pump.mCurrentFillLiters > pump.mRequiredFillLiters) {
            pump.mLoopState = MTECoolantPump.LoopState.DRAINING;
            pump.setLoopStatusKey(
                "modularnuclear.coolant_loop.pump.status.returning_excess",
                pump.mCurrentFillLiters - pump.mRequiredFillLiters);
        } else {
            pump.mLoopState = MTECoolantPump.LoopState.CIRCULATING;
            pump.setLoopStatusKey(
                "modularnuclear.coolant_loop.pump.status.filled_circulating",
                pump.mCurrentFillLiters);
        }
        return MTECoolantPump.Step.DONE;
    }

    private static void stopPumping(MTECoolantPump pump, IGregTechTileEntity baseTE) {
        if (baseTE != null) baseTE.setActive(false);
        pump.mEngine.setPumpPowered(false);
        pump.mEngine.setPumpMechanicalPowerWatts(0.0);
    }

    /**
     * Fills the loop from the reservoir until it holds what its pipes and devices need. Returns CARRY_ON to carry on
     * with the step.
     */
    static MTECoolantPump.Step stepFilling(MTECoolantPump pump, IGregTechTileEntity baseTE, FluidStack reservoirFluid,
        CoolantFluidProperty currentProp) {
        if (pump.mLoopState == MTECoolantPump.LoopState.EMPTY || pump.mLoopState == MTECoolantPump.LoopState.DRAINING
            || pump.mCurrentFillLiters < pump.mRequiredFillLiters) {
            long requiredEU = pump.getRequiredPowerDrawEU();
            if (!pump.drainEnergy(requiredEU)) {
                return MTECoolantPump.Step.of(pump.triggerPowerfail());
            }
            if (baseTE != null) {
                baseTE.setActive(true);
            }
            pump.degradeRotor(requiredEU);

            pump.mLoopState = MTECoolantPump.LoopState.FILLING;
            // No flow acceleration, power or convective heat transfer during filling
            pump.mEngine.setPumpPowered(false);
            pump.mEngine.setPumpMechanicalPowerWatts(0.0);

            long needed = pump.mRequiredFillLiters - pump.mCurrentFillLiters;
            int toDraw = (int) Math.min(pump.currentFillRateLitersPerTick(), needed);
            int drained = pump.drainFromReservoir(toDraw);

            if (drained > 0) {
                pump.mCurrentFillLiters += drained;
                double fillTemp = pump.mReservoir.getReservoirTemperatureCelsius();
                if (Double.isNaN(fillTemp)) {
                    fillTemp = CoolantFluidHelper.getBaselineInletTemperatureCelsius(
                        reservoirFluid != null ? reservoirFluid.getFluid() : null,
                        reservoirFluid,
                        currentProp,
                        pump.getBiomeTemperatureCelsius());
                }
                for (LoopSegment seg : pump.mEngine.getSegments()) {
                    if (seg.getCurrentTemperatureCelsius() < fillTemp) {
                        seg.setCurrentTemperatureCelsius(fillTemp);
                    }
                }
                if (pump.mCurrentFillLiters >= pump.mRequiredFillLiters) {
                    pump.mCurrentFillLiters = pump.mRequiredFillLiters;
                    pump.mLoopState = MTECoolantPump.LoopState.CIRCULATING;
                    pump.setLoopStatusKey(
                        "modularnuclear.coolant_loop.pump.status.filled_circulating",
                        pump.mCurrentFillLiters);
                } else {
                    pump.setLoopStatusKey(
                        "modularnuclear.coolant_loop.pump.status.filling_loop",
                        pump.mCurrentFillLiters,
                        pump.mRequiredFillLiters,
                        pump.mRequiredFillLiters > 0 ? (pump.mCurrentFillLiters * 100.0 / pump.mRequiredFillLiters)
                            : 100.0);
                }
            } else {
                // Reservoir ran out of fluid before loop filled!
                // Safe fail condition: stop cleanly WITHOUT EXPLOSION
                pump.mLoopState = MTECoolantPump.LoopState.FAILED;
                pump.setLoopStatusKey(
                    "modularnuclear.coolant_loop.pump.status.filling_failed",
                    pump.mCurrentFillLiters,
                    pump.mRequiredFillLiters);
            }
            return MTECoolantPump.Step.DONE;
        }
        return MTECoolantPump.Step.CARRY_ON;
    }

    /**
     * With the discharge hatch disabled by a cover the pump brakes the flow, then drains the loop. Returns CARRY_ON
     * to carry on with the step.
     */
    static MTECoolantPump.Step stepWithDischargeDisabled(MTECoolantPump pump, IGregTechTileEntity baseTE,
        boolean dischargeDisabled) {
        if (dischargeDisabled) {
            double flowRate = pump.mEngine.getVolumetricFlowRate();
            if (flowRate > 1e-5) {
                // Phase A: Active deceleration down to zero
                pump.checkMaintenance();
                double effFactor = pump.getEfficiencyFactor();
                long requiredEU = pump.getRequiredPowerDrawEU();
                boolean powered = pump.drainEnergy(requiredEU);
                if (!powered) {
                    return MTECoolantPump.Step.of(pump.triggerPowerfail());
                }
                if (baseTE != null) {
                    baseTE.setActive(true);
                }
                double wattsPerEu = (CoolantLoopDataRegistry.getPhysicsData() != null)
                    ? CoolantLoopDataRegistry.getPhysicsData()
                        .getPumpMechanicalPowerPerEU()
                    : 80.0;
                double mechanicalWatts = requiredEU * wattsPerEu * pump.mRotorEfficiency * effFactor;
                pump.degradeRotor(requiredEU);

                pump.mLoopState = MTECoolantPump.LoopState.DECELERATING;
                pump.mEngine.setPumpPowered(true);
                pump.mEngine.setPumpMechanicalPowerWatts(mechanicalWatts);
                pump.mEngine.setBraking(true);

                // Advance fluid mechanics with active motor braking
                pump.mEngine.step(0.05);

                if (pump.mEngine.isRuptured()) {
                    pump.triggerCatastrophicExplosion(pump.engineFailureText(), pump.mEngine.getRupturedSegment());
                    return MTECoolantPump.Step.FAILED;
                }

                if (pump.mEngine.getVolumetricFlowRate() <= 1e-5) {
                    pump.mEngine.setVolumetricFlowRate(0.0);
                    pump.mEngine.setBraking(false);
                }

                pump.setLoopStatusKey(
                    "modularnuclear.coolant_loop.pump.status.braking",
                    Math.round(pump.mEngine.getFlowRateLitersPerSecond() * 10.0) / 10.0,
                    Math.round(mechanicalWatts / 100.0) / 10.0);

                // Thermal exchange during deceleration
                double decelFlowRate = pump.mEngine.getVolumetricFlowRate();
                for (MTECoolantPump.DeviceSegmentBinding b : pump.getDeviceBindings()) {
                    b.device.processThermalExchange(
                        pump.branchFlow(decelFlowRate, b.segment),
                        0.05,
                        pump.mEngine.getFluid(),
                        b.segment);
                }
                return MTECoolantPump.Step.DONE;
            } else {
                // Phase B: Flow is zero, return fluid in loop back to reservoir
                if (baseTE != null) {
                    baseTE.setActive(false);
                }
                pump.mEngine.setVolumetricFlowRate(0.0);
                pump.mEngine.setBraking(false);
                pump.mEngine.setPumpPowered(false);
                pump.mEngine.setPumpMechanicalPowerWatts(0.0);

                if (pump.mCurrentFillLiters > 0) {
                    if (pump.isReservoirFull()) {
                        pump.mLoopState = MTECoolantPump.LoopState.STOPPED;
                        pump.setLoopStatusKey(
                            "modularnuclear.coolant_loop.pump.status.draining_paused",
                            pump.mCurrentFillLiters);
                    } else {
                        pump.mLoopState = MTECoolantPump.LoopState.DRAINING;
                        long neededReturn = pump.mCurrentFillLiters;
                        int toReturn = (int) Math.min(pump.currentFillRateLitersPerTick(), neededReturn);
                        int returned = pump.fillIntoReservoir(toReturn);
                        if (returned > 0) {
                            pump.mCurrentFillLiters -= returned;
                        }

                        if (pump.mCurrentFillLiters <= 0) {
                            pump.mCurrentFillLiters = 0;
                            pump.mLoopState = MTECoolantPump.LoopState.EMPTY;
                            pump.setLoopStatusKey("modularnuclear.coolant_loop.pump.status.drained_complete");
                        } else if (pump.isReservoirFull()) {
                            pump.mLoopState = MTECoolantPump.LoopState.STOPPED;
                            pump.setLoopStatusKey(
                                "modularnuclear.coolant_loop.pump.status.draining_paused",
                                pump.mCurrentFillLiters);
                        } else {
                            pump.setLoopStatusKey(
                                "modularnuclear.coolant_loop.pump.status.draining_loop",
                                pump.mCurrentFillLiters,
                                pump.mRequiredFillLiters);
                        }
                    }
                } else {
                    pump.mLoopState = MTECoolantPump.LoopState.EMPTY;
                    pump.setLoopStatusKey("modularnuclear.coolant_loop.pump.status.flow_stopped_empty");
                }
                pump.noteLoopAtRest();
                return MTECoolantPump.Step.DONE;
            }
        }
        return MTECoolantPump.Step.CARRY_ON;
    }
}
