package com.gtnewhorizons.modularnuclear.common.metatileentity.multi;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.modularnuclear.common.config.data.CoolantDeviceData;
import com.gtnewhorizons.modularnuclear.common.config.data.CoolantDeviceData.PHEData.SecondaryCoolantEntry;
import com.gtnewhorizons.modularnuclear.common.coolantloop.ConvectiveHeatTransferModel;
import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantFluidProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopSegment;
import com.gtnewhorizons.modularnuclear.common.coolantloop.PheExchange;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.Counterflow;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.SegmentHeat;

import gregtech.api.metatileentity.implementations.MTEHatchInput;

/*-
Note [PHE gas modes] (generated)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
A PHE works three ways beyond boiling a secondary fluid with a liquid loop's heat:

  Dual-loop mode.  A pair of high-pressure hatches in place of its secondary hatches (the left column of its
    middle layer) joins a second loop, or a second pass of the same loop: a recuperator.  The pair is a loop
    device of its own (PheSecondSide, id <phe>_b), which LoopDevices.at finds through ILoopHatchOwner.deviceOf.
    Each tick the PHE moves heat from the hotter stream to the colder as a counterflow exchanger (Note
    [Counterflow exchange between two streams]), with U = 1/(1/h_a + 1/h_b) from each side's velocity and phase
    (SegmentHeat.phaseFactor), over the same effective area as for boiling, and draws its usual power.
    stepDual.

  Boiling on a gas loop.  A hot gas loop (a Brayton loop's compressor discharge) boils a secondary fluid as a
    liquid loop does, with the heat taken out of the gas flowing through (SegmentHeat).  stepBoilGas.

  Liquefaction.  A gas loop colder than a cryogen's boiling point by the approach temperature condenses the
    cryogen's gas (nitrogen, oxygen, air, helium) from the secondary input into its liquid, the heat going into
    the loop.  A litre takes up the heat a litre boiling gives off (SecondaryCoolantEntry.latentHeat, the
    freezer's energy for it), so boiling and condensing balance.  stepCondense.  A litre of liquid nitrogen
    takes 16 MJ, many batches' heat on a small loop, so a batch makes the whole litres its heat pays for and
    carries the rest of the heat to the next batch (completeCondense), and no heat is lost to the rounding.

The PHE's stepHeatExchange picks the mode: dual if it has the second pair, else on a gas loop condensing when the
loop is cold enough for a condensable gas in its input, boiling otherwise.
*/
/** The PHE's dual-loop, gas boiling and liquefaction modes. See Note [PHE gas modes] */
final class PheGasModes {

    private PheGasModes() {}

    /** A batch of liquefaction, with the heat earlier batches took towards a litre they didn't finish. */
    static final class CondenseBatch {

        int timer;
        double heatJoules;
        SecondaryCoolantEntry coolant;

        void reset() {
            timer = 0;
            heatJoules = 0.0;
            coolant = null;
        }
    }

    static GasProperty gasOf(LoopSegment s, CoolantFluidProperty fluid) {
        if (s == null || !s.holdsGas() || fluid == null) return null;
        return GasProperty.get(fluid.getFluidName());
    }

    /** Liquid coolant flowing through a segment, as a counterflow stream (enthalpy relative to 0 K). */
    static Counterflow.Stream liquidStream(LoopSegment s, CoolantFluidProperty f, double flowM3s, double dt) {
        double cp = f.getSpecificHeat();
        return new Counterflow.Stream() {

            @Override
            public double inletK() {
                return s.getIncomingTemperatureCelsius() + 273.15;
            }

            @Override
            public double massPerStep() {
                return Math.max(0.0, flowM3s) * f.getDensity() * dt;
            }

            @Override
            public double inletEnthalpy() {
                return cp * inletK();
            }

            @Override
            public double enthalpyAt(double t) {
                return cp * t;
            }
        };
    }

    /** Most heat (J) a side can take (positive) or give (negative) before reaching boundK this step. */
    static double sideLimit(GasProperty gas, LoopSegment s, CoolantFluidProperty f, double flowM3s, double boundK,
        double dt) {
        if (gas != null) return SegmentHeat.limit(gas, s, boundK, dt);
        double volume = Math.max(s.getVolume(), flowM3s * dt);
        return volume * f.getDensity() * f.getSpecificHeat() * (boundK - 273.15 - s.getCurrentTemperatureCelsius());
    }

    /** Puts heat into a side (negative to take it out), bounded by boundK. */
    static void applySide(GasProperty gas, LoopSegment s, CoolantFluidProperty f, double flowM3s, double boundK,
        double joules, double dt) {
        if (gas != null) {
            SegmentHeat.exchange(gas, s, boundK, joules, dt);
            return;
        }
        double volume = Math.max(s.getVolume(), flowM3s * dt);
        double c = volume * f.getDensity() * f.getSpecificHeat();
        if (c <= 0.0) return;
        double t = s.getCurrentTemperatureCelsius() + joules / c;
        double bound = boundK - 273.15;
        s.setCurrentTemperatureCelsius(joules > 0 ? Math.min(t, bound) : Math.max(t, bound));
    }

    static double convection(ConvectiveHeatTransferModel model, LoopSegment s, double flowM3s) {
        double v = s.getArea() > 0 ? Math.abs(flowM3s) / s.getArea() : 0.0;
        return model.computeH(v) * SegmentHeat.phaseFactor(s);
    }

    /** One tick of dual-loop exchange. Returns the heat moved from side a into side b, J. */
    static double stepDual(MTEPressurizedHeatExchanger phe, double effectiveArea, double maxPerTick, double dt) {
        PheSecondSide side = phe.mSideB;
        LoopSegment a = phe.mBoundSegment, b = side.segment;
        double qa = phe.mLastCoolantFlowRateM3s, qb = side.flowM3s;
        CoolantFluidProperty fa = phe.mLoopFluid, fb = side.fluid;
        if (a == null || b == null || fa == null || fb == null || qa <= 1e-9 || qb <= 1e-9) return 0.0;
        GasProperty ga = gasOf(a, fa), gb = gasOf(b, fb);
        double ha = convection(phe.mConvectiveModel, a, qa), hb = convection(phe.mConvectiveModel, b, qb);
        if (ha <= 0.0 || hb <= 0.0) return 0.0;
        double ua = effectiveArea / (1.0 / ha + 1.0 / hb);
        Counterflow.Stream sa = ga != null ? SegmentHeat.stream(ga, a, dt) : liquidStream(a, fa, qa, dt);
        Counterflow.Stream sb = gb != null ? SegmentHeat.stream(gb, b, dt) : liquidStream(b, fb, qb, dt);
        double q = Counterflow.exchange(sa, sb, ua, dt);
        if (q == 0.0) return 0.0;
        double ta = sa.inletK(), tb = sb.inletK();
        q = Math.signum(q) * Math.min(Math.abs(q), maxPerTick);
        // Each side can take only so much before it reaches the other's inlet temperature
        double fromA = -sideLimit(ga, a, fa, qa, tb, dt), intoB = sideLimit(gb, b, fb, qb, ta, dt);
        if (q > 0) q = Math.min(q, Math.min(Math.max(0.0, fromA), Math.max(0.0, intoB)));
        else q = Math.max(q, Math.max(Math.min(0.0, fromA), Math.min(0.0, intoB)));
        if (q == 0.0) return 0.0;
        applySide(ga, a, fa, qa, tb, -q, dt);
        applySide(gb, b, fb, qb, ta, q, dt);
        return q;
    }

    /**
     * One tick of boiling the secondary coolant with a gas loop's heat, into the PHE's batch (as PheExchange.tick
     * does for liquid loops). Returns the heat moved, J.
     */
    static double stepBoilGas(MTEPressurizedHeatExchanger phe, GasProperty gas, CoolantDeviceData.PHEData cfg,
        SecondaryCoolantEntry coolant, double availableMl, double effectiveArea, double maxBatchHeat, double dt) {
        PheExchange.Batch b = phe.mBatch;
        b.timer++;
        b.lastTickHeatJoules = 0.0;
        LoopSegment s = phe.mBoundSegment;
        double flow = phe.mLastCoolantFlowRateM3s;
        if (s == null || coolant == null || flow <= 1e-9 || availableMl <= 0) return 0.0;
        double t = s.getCurrentTemperatureCelsius();
        double floorC = coolant.boilingPointCelsius + cfg.approachMinDeltaT;
        if (t <= floorC) return 0.0;
        double q = convection(phe.mConvectiveModel, s, flow) * effectiveArea * (t - coolant.boilingPointCelsius) * dt;
        int batchTicks = Math.max(1, cfg.batchTicks);
        double latentPerMl = coolant.latentHeat() / 1000.0;
        q = Math.min(q, maxBatchHeat / batchTicks);
        q = Math.min(q, Math.max(0.0, maxBatchHeat - b.heatJoules));
        q = Math.min(q, Math.max(0.0, availableMl * latentPerMl - b.heatJoules));
        if (q <= 0.0) return 0.0;
        double moved = -SegmentHeat.exchange(gas, s, floorC + 273.15, -q, dt);
        if (moved <= 0.0) return 0.0;
        b.lastTickHeatJoules = moved;
        b.heatJoules += moved;
        b.weightedTempSum += s.getIncomingTemperatureCelsius() * moved;
        b.coolant = coolant;
        return moved;
    }

    /** The first cryogen whose gas is in the PHE's input hatches, and how much of it (L); null if none. */
    static Object[] findCondensable(MTEPressurizedHeatExchanger phe, CoolantDeviceData.PHEData cfg) {
        for (MTEHatchInput hatch : phe.inputHatches()) {
            if (hatch == null || !hatch.isValid()) continue;
            FluidStack fs = hatch.getFillableStack();
            if (fs == null || fs.getFluid() == null || fs.amount <= 0) continue;
            String name = fs.getFluid()
                .getName();
            for (SecondaryCoolantEntry e : cfg.secondaryCoolants) {
                if (e == null || e.isSteam || e.outputGasName == null) continue;
                if (!e.outputGasName.equalsIgnoreCase(name)) continue;
                Fluid liquid = FluidRegistry.getFluid(e.inputFluidName);
                if (liquid == null) continue;
                // Resolves the cryogen's latent heat from the freezer's recipes, as boiling it does
                SecondaryCoolantEntry resolved = phe.secondaryCoolantFor(cfg, liquid);
                return new Object[] { resolved != null ? resolved : e, fs.amount, fs.getFluid() };
            }
        }
        return null;
    }

    /** One tick of liquefaction; completes the batch when due. Returns the heat moved into the loop, J. */
    static double stepCondense(MTEPressurizedHeatExchanger phe, GasProperty gas, CoolantDeviceData.PHEData cfg,
        SecondaryCoolantEntry coolant, int availableL, Fluid gasFluid, double effectiveArea, double maxBatchHeat,
        double dt) {
        CondenseBatch b = phe.mCondenseBatch;
        b.timer++;
        LoopSegment s = phe.mBoundSegment;
        double flow = phe.mLastCoolantFlowRateM3s;
        double moved = 0.0;
        if (s != null && flow > 1e-9 && availableL > 0) {
            double t = s.getCurrentTemperatureCelsius();
            double ceilingC = coolant.boilingPointCelsius - cfg.approachMinDeltaT;
            if (t < ceilingC) {
                double q = convection(phe.mConvectiveModel, s, flow) * effectiveArea
                    * (coolant.boilingPointCelsius - t)
                    * dt;
                int batchTicks = Math.max(1, cfg.batchTicks);
                double latentPerL = coolant.latentHeat();
                q = Math.min(q, maxBatchHeat / batchTicks);
                q = Math.min(q, Math.max(0.0, availableL * latentPerL - b.heatJoules));
                if (q > 0.0) {
                    moved = SegmentHeat.exchange(gas, s, ceilingC + 273.15, q, dt);
                    b.heatJoules += moved;
                    b.coolant = coolant;
                }
            }
        }
        if (b.timer >= Math.max(1, cfg.batchTicks)) completeCondense(phe, gasFluid);
        return moved;
    }

    /**
     * The heat a liquefaction batch carries to the next: what it took beyond the litres it made, and, if the gas ran
     * out before it made all the litres it paid for, no more than a litre's worth.
     */
    static double carriedHeat(double heatJoules, int litresMade, double latentPerLitre) {
        return Math.max(0.0, Math.min(heatJoules - litresMade * latentPerLitre, latentPerLitre));
    }

    private static void completeCondense(MTEPressurizedHeatExchanger phe, Fluid gasFluid) {
        CondenseBatch b = phe.mCondenseBatch;
        SecondaryCoolantEntry coolant = b.coolant;
        if (coolant != null && b.heatJoules > 0.0 && gasFluid != null) {
            int liters = (int) Math.floor(b.heatJoules / Math.max(1.0, coolant.latentHeat()));
            int drained = 0;
            for (MTEHatchInput hatch : phe.inputHatches()) {
                if (drained >= liters) break;
                FluidStack fs = hatch != null ? hatch.getFillableStack() : null;
                if (fs == null || fs.getFluid() != gasFluid) continue;
                FluidStack got = hatch.drain(liters - drained, true);
                if (got != null) drained += got.amount;
            }
            Fluid liquid = FluidRegistry.getFluid(coolant.inputFluidName);
            if (drained > 0 && liquid != null) phe.addOutput(new FluidStack(liquid, drained));
            phe.mCondensedLiters += drained;
            double rest = carriedHeat(b.heatJoules, drained, coolant.latentHeat());
            b.reset();
            b.heatJoules = rest;
            b.coolant = coolant;
            return;
        }
        b.reset();
    }
}
