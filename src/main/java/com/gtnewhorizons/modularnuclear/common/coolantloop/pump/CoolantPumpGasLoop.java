package com.gtnewhorizons.modularnuclear.common.coolantloop.pump;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantLoopEngine;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopSegment;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasLoopEngine;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasPressureStage;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasProperty;
import com.gtnewhorizons.modularnuclear.common.util.LocalizedText;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTUtility;

/*-
Note [The pump on a gas loop] (generated)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
A pump whose reservoir holds a gas (one GasProperty.get knows) runs its loop as a gas loop: its engine becomes a
GasLoopEngine (useEngineFor), and CoolantPumpCirculation hands each tick to step here instead of its own liquid
path.  The pump stays the loop's owner, as on a liquid loop: it crawls the loop, keeps its chunks loaded, holds
its reservoir and charges the loop from it, and its being allowed to work is what lets the compressors run.  What
it doesn't do on a gas loop is pump: it needs no rotor, and the compressors drive the gas.

Charge.  The pump's fill (mCurrentFillLiters, mRequiredFillLiters) counts litres of the gas fluid, at 0 °C and
1 atm (GasProperty.massOfLiters).  The charge a loop takes (requiredChargeLiters) follows from its volume and the
pump's ambient temperature (GasLoopEngine.requiredChargeKg): for a condensing gas, enough for a fraction of the
loop's volume to be liquid at ambient (10% by default: the high side, often a small part of the loop, has to hold
that liquid once the compressor pumps the low side down), and for a permanent gas, enough for a charge pressure.  A screwdriver on the
pump's controller cycles both settings (cycleCharge).  The pump charges a stopped loop at its fill rate, holding
the loop at equilibrium at the reservoir's temperature as it goes (GasLoopEngine.equilibrate).

Stopping.  Disabled (soft mallet, redstone), the pump stops its compressors and lets the loop equalize through its
valves and expanders until it comes to rest.  With its discharge hatch disabled it also takes the charge back
into the reservoir once the loop is at rest, as it drains a liquid loop.

GasLoopEngineTest covers the engine; inworld-tests/test_refrigeration_loop.py the pump in game.
*/
/** A pump's tick on a gas loop. See Note [The pump on a gas loop] */
final class CoolantPumpGasLoop {

    private CoolantPumpGasLoop() {}

    /** Liquid fraction (condensing gases) and charge pressure in bar (permanent gases) the screwdriver cycles. */
    static final double[] LIQUID_FRACTIONS = { 0.1, 0.05, 0.2, 0.3, 0.02 };
    static final double[] CHARGE_PRESSURES_BAR = { 10.0, 5.0, 20.0, 40.0, 2.0 };

    /**
     * The gas the pump's loop carries, or null for a liquid loop: the gas engine's, or the reservoir's fluid's if it
     * is a gas.
     */
    static GasProperty loopGas(CoolantPumpCore p, FluidStack reservoirFluid) {
        if (p.mEngine instanceof GasLoopEngine g && p.mCurrentFillLiters > 0L) return g.getGas();
        if (reservoirFluid != null && reservoirFluid.getFluid() != null) {
            GasProperty gas = GasProperty.get(
                reservoirFluid.getFluid()
                    .getName());
            if (gas != null) return gas;
        }
        return p.mEngine instanceof GasLoopEngine g ? g.getGas() : null;
    }

    /** Makes the pump's engine a gas engine for this gas, or a liquid engine for null, keeping the segments. */
    static void useEngineFor(CoolantPumpCore p, GasProperty gas) {
        CoolantLoopEngine current = p.mEngine;
        boolean fits = gas == null ? !(current instanceof GasLoopEngine)
            : current instanceof GasLoopEngine g && g.getGas() == gas;
        if (fits) return;
        CoolantLoopEngine next = gas != null ? new GasLoopEngine(gas) : new CoolantLoopEngine();
        for (LoopSegment s : current.getSegments()) {
            if (gas == null) s.clearGasState();
            next.addSegment(s);
        }
        next.setMaxVolumetricFlowRate(current.getMaxVolumetricFlowRate());
        p.mEngine = next;
        p.rebuildDeviceBindings();
    }

    /** Binds the pump's pressure stages to its gas engine. Called after every rebuild of the device bindings. */
    static void bindStages(CoolantPumpCore p) {
        if (!(p.mEngine instanceof GasLoopEngine e)) return;
        e.clearStages();
        for (CoolantPumpCore.DeviceSegmentBinding b : p.getDeviceBindingsRaw()) {
            if (b.device instanceof GasPressureStage stage && b.segment != null) e.bindStage(b.segment.getKey(), stage);
        }
    }

    static double liquidFraction(CoolantPumpCore p) {
        return LIQUID_FRACTIONS[Math.floorMod(p.mGasChargeSetting, LIQUID_FRACTIONS.length)];
    }

    static double chargePressureBar(CoolantPumpCore p) {
        return CHARGE_PRESSURES_BAR[Math.floorMod(p.mGasChargeSetting, CHARGE_PRESSURES_BAR.length)];
    }

    /** Litres of gas (at 0 °C, 1 atm) the loop takes. */
    static long requiredChargeLiters(CoolantPumpCore p, GasLoopEngine e) {
        double ambientK = p.getBiomeTemperatureCelsius() + 273.15;
        double kg = GasLoopEngine.requiredChargeKg(
            e.getGas(),
            e.getTotalVolumeM3(),
            ambientK,
            liquidFraction(p),
            chargePressureBar(p) * 1e5);
        return Math.max(
            1L,
            Math.round(
                e.getGas()
                    .litersOfMass(kg)));
    }

    /** The screwdriver on the pump's controller: the next charge setting. */
    static void cycleCharge(CoolantPumpCore p, EntityPlayer player) {
        p.mGasChargeSetting = Math.floorMod(p.mGasChargeSetting + 1, LIQUID_FRACTIONS.length);
        GTUtility.sendChatToPlayer(
            player,
            String.format(
                "Gas loop charge: %.0f%% liquid at ambient for condensing gases, %.0f bar for permanent gases",
                liquidFraction(p) * 100.0,
                chargePressureBar(p)));
    }

    /** One tick of a gas loop. False when the loop failed. */
    static boolean step(CoolantPumpCore p, IGregTechTileEntity baseTE, GasProperty gas) {
        useEngineFor(p, gas);
        GasLoopEngine e = (GasLoopEngine) p.mEngine;
        if (e.getStageCount() == 0) bindStages(p);
        p.mRequiredFillLiters = requiredChargeLiters(p, e);
        double ambientK = p.getBiomeTemperatureCelsius() + 273.15;

        if (!p.isAllowedToWork()) {
            if (p.host.recoversChargeWhenStopped()) return pumpDown(p, baseTE, e);
            stopCompressors(p, baseTE);
            boolean ok = runLoop(p, e);
            p.setLoopStatusKey(
                e.getVolumetricFlowRate() > 0.0 ? "modularnuclear.gas_loop.pump.status.disabled_equalizing"
                    : "modularnuclear.gas_loop.pump.status.disabled",
                p.mCurrentFillLiters);
            p.noteLoopAtRest();
            return ok;
        }

        // A loop at rest may have changed: crawled again before it starts. See Note [Changing a stopped coolant loop]
        CoolantPumpCore.Step restart = CoolantPumpFillDrain.stepRestartCrawl(p, baseTE);
        if (restart != CoolantPumpCore.Step.CARRY_ON) return restart == CoolantPumpCore.Step.DONE;

        if (p.isDischargeHatchDisabled()) return pumpDown(p, baseTE, e);

        if (p.mCurrentFillLiters < p.mRequiredFillLiters) return charge(p, baseTE, e, ambientK);

        p.mLoopState = CoolantPumpCore.LoopState.CIRCULATING;
        e.setPumpPowered(true);
        e.setBraking(false);
        baseTE.setActive(true);
        boolean ok = runLoop(p, e);
        if (ok) {
            double[] zones = e.getZonePressures();
            double low = Double.MAX_VALUE, high = 0.0;
            for (double z : zones) {
                low = Math.min(low, z);
                high = Math.max(high, z);
            }
            p.setLoopStatusKey(
                "modularnuclear.gas_loop.pump.status.running",
                gas.getFluidName(),
                zones.length,
                zones.length > 0 ? low / 1e5 : 0.0,
                high / 1e5,
                e.getMinLoopTempCelsius(),
                e.getPeakLoopTempCelsius());
        }
        return ok;
    }

    private static void stopCompressors(CoolantPumpCore p, IGregTechTileEntity baseTE) {
        baseTE.setActive(false);
        p.mEngine.setPumpPowered(false);
        p.mEngine.setBraking(false);
        if (p.mLoopState == CoolantPumpCore.LoopState.CIRCULATING) p.mLoopState = CoolantPumpCore.LoopState.STOPPED;
    }

    /** Steps the engine and the devices' exchange. False if the loop ruptured. */
    private static boolean runLoop(CoolantPumpCore p, GasLoopEngine e) {
        e.setHoldsCoolant(p.mCurrentFillLiters > 0L);
        e.step(0.05);
        if (e.isRuptured()) {
            p.triggerCatastrophicExplosion(p.engineFailureText(), e.getRupturedSegment());
            return false;
        }
        for (CoolantPumpCore.DeviceSegmentBinding b : p.getDeviceBindings()) {
            double q = b.segment != null ? e.getLocalVolumetricFlow(b.segment) : 0.0;
            b.device.processThermalExchange(q, 0.05, e.getFluid(), b.segment);
        }
        return true;
    }

    /** Charges a stopped loop from the reservoir. */
    private static boolean charge(CoolantPumpCore p, IGregTechTileEntity baseTE, GasLoopEngine e, double ambientK) {
        stopCompressors(p, baseTE);
        // The loop settles while it charges
        boolean ok = runLoop(p, e);
        if (!ok) return false;
        if (e.getVolumetricFlowRate() > 0.0) {
            p.setLoopStatusKey("modularnuclear.gas_loop.pump.status.settling");
            return true;
        }
        long requiredEU = p.getRequiredPowerDrawEU();
        if (!p.drainEnergy(requiredEU)) return p.triggerPowerfail();
        baseTE.setActive(true);
        p.mLoopState = CoolantPumpCore.LoopState.FILLING;
        long needed = p.mRequiredFillLiters - p.mCurrentFillLiters;
        int drained = p
            .drainFromReservoir((int) Math.min(Integer.MAX_VALUE, Math.min(p.currentFillRateLitersPerTick(), needed)));
        if (drained <= 0) {
            p.mLoopState = CoolantPumpCore.LoopState.STOPPED;
            p.setLoopStatusKey(
                "modularnuclear.gas_loop.pump.status.charge_short",
                p.mCurrentFillLiters,
                p.mRequiredFillLiters);
            return true;
        }
        p.mCurrentFillLiters += drained;
        double t = p.mReservoir.getReservoirTemperatureCelsius();
        double tK = Double.isNaN(t) ? ambientK : t + 273.15;
        // Gas already in a loop that ran keeps its average temperature; a first charge comes in at the reservoir's
        double loopK = e.isCharged() ? e.getAvgLoopTempCelsius() + 273.15 : tK;
        e.equilibrate(
            e.getGas()
                .massOfLiters(p.mCurrentFillLiters),
            loopK);
        p.setLoopStatusKey(
            "modularnuclear.gas_loop.pump.status.charging",
            p.mCurrentFillLiters,
            p.mRequiredFillLiters,
            p.mRequiredFillLiters > 0 ? p.mCurrentFillLiters * 100.0 / p.mRequiredFillLiters : 100.0);
        return true;
    }

    /** Stops the compressors, lets the loop come to rest, then takes the charge back into the reservoir. */
    private static boolean pumpDown(CoolantPumpCore p, IGregTechTileEntity baseTE, GasLoopEngine e) {
        stopCompressors(p, baseTE);
        boolean ok = runLoop(p, e);
        if (!ok) return false;
        if (e.getVolumetricFlowRate() > 0.0) {
            p.mLoopState = CoolantPumpCore.LoopState.DECELERATING;
            p.setLoopStatusKey("modularnuclear.gas_loop.pump.status.settling");
            return true;
        }
        if (p.mCurrentFillLiters <= 0L) {
            p.mLoopState = CoolantPumpCore.LoopState.EMPTY;
            p.setLoopStatusKey("modularnuclear.coolant_loop.pump.status.drained_complete");
            p.noteLoopAtRest();
            return true;
        }
        if (p.isReservoirFull()) {
            p.mLoopState = CoolantPumpCore.LoopState.STOPPED;
            p.setLoopStatusKey("modularnuclear.coolant_loop.pump.status.draining_paused", p.mCurrentFillLiters);
            return true;
        }
        p.mLoopState = CoolantPumpCore.LoopState.DRAINING;
        int toReturn = (int) Math
            .min(Integer.MAX_VALUE, Math.min(p.currentFillRateLitersPerTick(), p.mCurrentFillLiters));
        int returned = p.fillIntoReservoir(toReturn);
        if (returned > 0) {
            double loopK = e.getAvgLoopTempCelsius() + 273.15;
            p.mCurrentFillLiters -= returned;
            e.equilibrate(
                e.getGas()
                    .massOfLiters(p.mCurrentFillLiters),
                loopK);
        }
        if (p.mCurrentFillLiters <= 0L) {
            p.mCurrentFillLiters = 0L;
            p.mLoopState = CoolantPumpCore.LoopState.EMPTY;
            p.setLoopStatusKey("modularnuclear.coolant_loop.pump.status.drained_complete");
        } else {
            p.setLoopStatusKey(
                "modularnuclear.coolant_loop.pump.status.draining_loop",
                p.mCurrentFillLiters,
                p.mRequiredFillLiters);
        }
        p.noteLoopAtRest();
        return true;
    }

    /** A status line for a gas loop's GUI and logs. */
    static LocalizedText describe(CoolantPumpCore p) {
        return p.getLoopStatusMessage();
    }
}
