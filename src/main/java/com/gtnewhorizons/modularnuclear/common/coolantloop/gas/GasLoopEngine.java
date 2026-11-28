package com.gtnewhorizons.modularnuclear.common.coolantloop.gas;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantFluidProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantLoopEngine;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopAdvection;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopFailure;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopId;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopSegment;

/*-
Note [Gas loop pressure zones] (generated)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Topic.  A gas loop (a refrigeration loop) carries a compressible gas that condenses and boils, so it can't share
the liquid engine's model of one incompressible flow driven by the pump's head.  GasLoopEngine replaces
CoolantLoopEngine.step for loops whose fluid is a GasProperty (Note [The gas model]); everything else about the
loop (segments, crawling, the pump, saving) stays as it is.

Design.  The pressure stages (GasPressureStage: compressors, expansion valves, turbo-expanders) split the loop
into pressure zones, the runs of segments between them.  A zone has one pressure; friction inside it is not
modelled.  Each step (step):

  1. Each zone's pressure is solved from its mass and its segments' enthalpies: the P at which
     sum rho(P, h_i) V_i equals the zone's mass (solvePressure).  Density rises with pressure in every phase,
     so the root is unique.  Each segment's mass becomes rho_i V_i.
  2. Each stage sees its inlet (the upstream zone's pressure and the enthalpy flowing in) and its outlet pressure,
     and answers a mass flow and an outlet enthalpy (GasStageModels has the physics).
  3. The mass moves: each zone gains what flows in and loses what flows out, spread over its segments as their
     densities say.
  4. Enthalpy is advected at each zone's through-flow (W5) with LoopAdvection.stepScalar, each segment's mass as
     its capacity and a stage's own segment fixed at the stage's outlet enthalpy.
  5. Each segment's temperature, quality and density follow from its zone's pressure and its new enthalpy, and
     the limits are checked: a pipe above its rating bursts, gas below its freezing point freezes, a segment above
     its temperature limit melts.

Moving parts.  GasLoopEngine (zones, the solve and the step), GasPressureStage and its Context, GasStageModels,
GasProperty, LoopSegment's gas fields (getGasEnthalpy, getGasMassKg and the pending heat), and
LoopAdvection.stepScalar.  Devices exchange heat with a segment through SegmentHeat, which works for liquid and gas
loops alike.

Wrinkles:

(W1) Devices act on the gas as a pull towards their own temperature, solved with the next step's advection
     (LoopSegment.addGasRelaxation, the relaxK terms of stepScalar): a pull of k (kg) towards enthalpy h_b adds
     k (h_b - h') to a segment's heat, with h' its enthalpy at the end of the step.  A device's segment holds far
     less gas than flows through it in a tick (a PHE's segment holds a fraction of a kilogram of vapour; tens of
     kilograms pass in a tick), so heat put into the segment's own gas would move almost nothing without crossing
     the device's temperature.  Against a through-flow F a well-mixed segment settles at effectiveness
     k / (F + k), so an exchanger of effectiveness eps pulls with k = F eps / (1 - eps)
     (SegmentHeat.exchangeThrough); a device that has worked out its heat pulls with the k that moves it now
     (SegmentHeat.exchange).  Solved implicitly, no pull, however strong, takes the gas past h_b.  A pull is
     towards a temperature, which the step turns into an enthalpy at the pressure it has just solved, and it moves
     no more than the device's conductance times the temperature difference it finds (k <= UA dt dT / dh).  A
     condenser at ambient with its gas condensing at ambient pulls on a target that a small change in pressure
     moves across the phase change, and without that cap a pull sized on sensible heat moved the latent heat
     instead and rang the high side between 5 and 77 bar.
     Historical note: devices first put in a fixed heat worked out on the last step's state.  When the flow or
     the inflow changed before the step applied it, strong exchangers overshot and undershot in turn, and long
     loops swung between ambient and hundreds of degrees from tick to tick.

(W2) A passive stage (a valve or an expander) passes a flow that follows the pressure difference.  Over a step
     it could pass more than equalizes its two zones and set them oscillating, so the engine caps it at half of
     what would equalize them, from each zone's compliance dm/dP (zoneCompliance, measured in the solve).  A
     stage never takes more than half of its upstream zone's mass in a step (MAX_TURNOVER).  The heat a game's
     machines move (a coolant bath of hot ingots takes tens of megawatts) runs a loop's gas round many times a
     second, so a tighter limit (a quarter, at first) held a test loop's compressor to a tenth of its
     displacement.

(W3) A segment new to a charged loop (a pipe added while the loop was stopped) holds no gas yet: it takes the
     loop's mean enthalpy and no mass, and the zone's pressure falls as its gas spreads into it.  The pump then
     tops up the charge.

(W4) The engine's volumetric flow (getVolumetricFlowRate, what the pump and its GUI read as the loop's flow) is
     the largest stage's flow at its inlet density, so "flowing" and "at rest" mean what they do for a liquid
     loop.  Devices read their own segment's flow with getLocalVolumetricFlow, from its mass flow and density.

(W5) A segment's gas is advected at a flow that runs from what enters its zone, at the zone's entry, to what
     leaves it, at its exit, in proportion to the volume before the segment (in flow order, buildFlowOrder),
     times its flow fraction.  What a zone gains or loses changes the gas's density where it is, as each step
     spreads the zone's mass over its segments by their densities.  The heat is exact in a steady loop, where
     nothing re-spreads and every zone passes on what it takes in, and slightly off while flows and densities
     change.  Devices see the same flow (LoopSegment.getGasMassFlow): a condenser filling with liquid sees the
     compressor's flow, not the valve's trickle, and with the compressor stopped no gas comes out of it.
     Historical note: two versions conserved heat exactly in transients.  The first spread each step's
     imbalance over the zone as a source, which moved heat from one end of a zone to the other and set a Brayton
     loop with a recuperator oscillating.  The second carried each segment's change in mass on flows worked out
     in flow order; a segment a strong exchanger chilled took in the whole flow to fill up with denser gas,
     starved the segments after it, and set long loops of many pipes swinging between 1 K and thousands of
     degrees.  Gas would flow back into such a segment from both sides; flows in one direction can't do that.

(W6) A stage's flow follows what it answers with a lag of FLOW_LAG_SECONDS, the gas's inertia.  Without it the
     flows and pressures answer each other within a tick and ring with a period of two ticks: the compressor's
     flow follows the suction pressure the valve's last flow left, and the other way round.  A passive stage's lag is held
     below a quarter of the time either zone takes to turn its gas over (its mass over the flow), which is about
     the time its pressure takes to answer the flow: a helium zone of a few hundred grams passing several kilograms
     a second settles within two ticks, and a lag of four ticks there set the flows and pressures swinging until a
     zone emptied.  Each answer is also damped as by backward Euler on its upstream zone's emptying: a stage's flow
     goes with its inlet pressure, which falls as its zone empties, so the flow is the one the step's end pressure
     would give, mdot' = mdot m / (m + mdot dt) for a zone of mass m.  It leaves a large zone's flow alone and
     halves one that would empty its zone within the step.

(W9) A compressor has a high-pressure cutout: it passes no more than half of what would take its discharge zone
     to CUTOUT_FRACTION of the zone's weakest pressure rating (zoneMaxPressure): the mass the zone holds at that
     pressure at its present enthalpies (zoneCutoutMass), less what it holds.  The zone's compliance would not do:
     a two-phase zone takes mass easily until it is full of liquid, which takes almost none (wrinkle (W2) of Note
     [The gas model]), and one tick of a compressor pushing into an overcharged high side took a test loop from 14
     bar to 756.  The compressor's hot gas also raises a zone's pressure at the same mass, so the cutout also
     latches: a compressor whose discharge reaches CUTOUT_LATCH_FRACTION of the rating stops until it falls below
     CUTOUT_RELEASE_FRACTION (stageCutOut).  In game a coolant bath full of hot ingots on an overcharged loop
     burst its high side at 40 bar on 35 bar pipe before the latch.  A loop can still burst, from heat
     raising its pressure or from a valve or expander passing too much, but not from its compressor alone.

GasLoopEngineTest runs whole vapour-compression and Brayton cycles to steady state and checks mass and energy.
*/
/** The engine of a gas loop. See Note [Gas loop pressure zones] */
public class GasLoopEngine extends CoolantLoopEngine {

    /** Lowest pressure a zone falls to, Pa: an evacuated zone. */
    static final double MIN_PRESSURE_PA = 100.0;
    private static final double MAX_PRESSURE_PA = 1e10;
    /** How quickly a stage's flow follows what it answers. See wrinkle (W6) of Note [Gas loop pressure zones] */
    static final double FLOW_LAG_SECONDS = 0.2;
    /** How far a step takes each segment's mass towards what its density holds. See wrinkle (W8) */
    static final double MASS_RELAXATION = 0.5;
    /** Share of its zone's weakest rating a compressor's discharge is held to. See wrinkle (W9) */
    public static final double CUTOUT_FRACTION = 0.7;
    /** Most of its upstream zone's mass a stage takes in a step. See wrinkle (W2) */
    static final double MAX_TURNOVER = 0.5;
    /** Discharge pressure (share of the rating) that stops a compressor, and that lets it start again. */
    public static final double CUTOUT_LATCH_FRACTION = 0.8;
    public static final double CUTOUT_RELEASE_FRACTION = 0.7;

    private final GasProperty gas;
    private final Map<LoopId, GasPressureStage> stages = new HashMap<>();

    // Topology, rebuilt when the segments or the stages change
    private boolean topologyValid = false;
    private int[] zoneOf = new int[0];
    private int zoneCount = 0;
    private int[][] zoneMembers = new int[0][];
    private int[] stageSegment = new int[0];
    private GasPressureStage[] stageImpl = new GasPressureStage[0];
    private int[] stageUpZone = new int[0];
    private int[] stageDownZone = new int[0];
    private int[][] upstreams = new int[0][];
    private int[] zoneExitSegment = new int[0];
    private boolean[] isStage = new boolean[0];

    // State per zone and stage
    private double[] zonePressure = new double[0];
    private double[] zoneCompliance = new double[0];
    private double[] zoneFlow = new double[0];
    private double[] stageMassFlow = new double[0];
    private double[] stageEnthalpyOut = new double[0];
    private double[] stageShaftPower = new double[0];
    private int[][] downstreams = new int[0][];
    private int[][] zoneOrder = new int[0][];
    private double[] zoneMaxPressure = new double[0], zoneCutoutMass = new double[0];
    private boolean[] stageCutOut = new boolean[0];

    // Scratch
    private double[] cap = new double[0], inflow = new double[0], source = new double[0], value = new double[0],
        incoming = new double[0], tempK = new double[0], quality = new double[0];
    private boolean[] fixed = new boolean[0];
    private double[] zoneIn = new double[0], zoneOut = new double[0], zoneMass = new double[0];
    private double[] target = new double[0], outflow = new double[0], relaxK = new double[0], relaxKH = new double[0];
    private final GasPressureStage.Context ctx = new GasPressureStage.Context();
    private final GasProperty.State st = new GasProperty.State();

    public GasLoopEngine(GasProperty gas) {
        super(coolantPropertyOf(gas));
        this.gas = gas;
        setMaxPumpHeadPressurePa(0.0);
    }

    /** The liquid-loop view of a gas, for code that reads any loop's fluid (names, displays, spills). */
    public static CoolantFluidProperty coolantPropertyOf(GasProperty gas) {
        double freezeC = Double.isNaN(gas.getFreezingPointK()) ? Double.NEGATIVE_INFINITY
            : gas.getFreezingPointK() - 273.15;
        return new CoolantFluidProperty(
            gas.getFluidName(),
            gas.getStandardDensity(),
            gas.getVaporViscosity(),
            gas.getCpVapor(),
            gas.getVaporConductivity(),
            gas.getReferenceTemperatureK() - 273.15,
            freezeC,
            false,
            0.0,
            false);
    }

    public GasProperty getGas() {
        return gas;
    }

    @Override
    public boolean isGasLoop() {
        return true;
    }

    // --- Stages ---

    /** Makes the segment with this id a pressure stage. */
    public void bindStage(LoopId segment, GasPressureStage stage) {
        if (segment == null || stage == null) return;
        if (stages.put(segment, stage) != stage) topologyValid = false;
    }

    public void clearStages() {
        if (!stages.isEmpty()) {
            stages.clear();
            topologyValid = false;
        }
    }

    public int getStageCount() {
        return stages.size();
    }

    @Override
    protected void segmentsChanged() {
        super.segmentsChanged();
        topologyValid = false;
    }

    // --- Charge ---

    /** Whether the loop holds any gas. */
    public boolean isCharged() {
        for (LoopSegment s : segments) if (s.holdsGas() && s.getGasMassKg() > 0.0) return true;
        return false;
    }

    /** Total gas in the loop, kg. */
    public double getTotalGasMassKg() {
        double m = 0.0;
        for (LoopSegment s : segments) if (s.holdsGas()) m += s.getGasMassKg();
        return m;
    }

    @Override
    public double getTotalFluidMassKg() {
        return getTotalGasMassKg();
    }

    public double getTotalVolumeM3() {
        double v = 0.0;
        for (LoopSegment s : segments) v += s.getGasVolume();
        return v;
    }

    /**
     * The charge (kg) a loop of this volume takes at an ambient temperature: for a condensing gas, enough that
     * liquidFraction of its volume is liquid at ambient; for a permanent gas (or a condensing one above its critical
     * temperature), enough for chargePressurePa at ambient.
     */
    public static double requiredChargeKg(GasProperty gas, double volumeM3, double ambientK, double liquidFraction,
        double chargePressurePa) {
        if (gas.isCondensing() && ambientK < gas.getCriticalTemperatureK()) {
            double rhoG = gas.saturationPressure(ambientK) / (gas.getGasConstant() * ambientK);
            return volumeM3 * (liquidFraction * gas.getLiquidDensity() + (1.0 - liquidFraction) * rhoG);
        }
        return volumeM3 * chargePressurePa / (gas.getGasConstant() * ambientK);
    }

    /**
     * Sets the whole loop to an even mass of gas at one temperature, at rest: how a pump charges a loop or takes
     * gas back. Every segment gets the same enthalpy and density.
     */
    public void equilibrate(double totalMassKg, double temperatureK) {
        double volume = getTotalVolumeM3();
        if (volume <= 0.0) return;
        double rho = Math.max(0.0, totalMassKg) / volume;
        double[] ph = equilibriumState(gas, rho, temperatureK);
        for (LoopSegment s : segments) {
            s.setGasEnthalpy(ph[1]);
            s.setGasMassKg(rho * s.getGasVolume());
            s.takePendingGasHeatJoules();
            s.setGasMassFlow(0.0);
        }
        topologyValid = false;
        ensureTopology();
        Arrays.fill(zonePressure, Math.max(MIN_PRESSURE_PA, ph[0]));
        Arrays.fill(stageMassFlow, 0.0);
        volumetricFlowRate = 0.0;
        deriveAndCheck(false);
    }

    /** Pressure (Pa) and enthalpy (J/kg) of gas at a density and temperature, at equilibrium. */
    public static double[] equilibriumState(GasProperty gas, double rho, double temperatureK) {
        double r = gas.getGasConstant();
        if (temperatureK < gas.getCriticalTemperatureK()) {
            double ps = gas.saturationPressure(temperatureK);
            double rhoG = ps / (r * temperatureK);
            if (rho <= rhoG) return new double[] { rho * r * temperatureK, gas.vaporEnthalpy(temperatureK) };
            double rhoL = gas.getLiquidDensity();
            double hl = gas.liquidEnthalpy(temperatureK);
            if (rho < rhoL) {
                double x = (1.0 / rho - 1.0 / rhoL) / (1.0 / rhoG - 1.0 / rhoL);
                return new double[] { ps, hl + x * gas.latentHeat(temperatureK) };
            }
            return new double[] { ps + GasProperty.LIQUID_BULK_MODULUS_PA * (rho / rhoL - 1.0), hl };
        }
        return new double[] { rho * r * temperatureK, gas.vaporEnthalpy(temperatureK) };
    }

    // --- Readings ---

    /** Pressure (bar) of the zone a segment is in. */
    public double getZonePressureBar(LoopSegment segment) {
        return segment.getInletPressureBar();
    }

    /** Mass flow (kg/s) through the stage on this segment over the last step; 0 if it is none. */
    public double getStageMassFlow(LoopId segment) {
        ensureTopology();
        for (int k = 0; k < stageSegment.length; k++) {
            if (segments.get(stageSegment[k])
                .getKey()
                .equals(segment)) return stageMassFlow[k];
        }
        return 0.0;
    }

    /** Pressure (Pa) of the zone upstream of a stage: its suction or inlet. */
    public double getStageInletPressurePa(LoopId segment) {
        ensureTopology();
        for (int k = 0; k < stageSegment.length; k++) {
            if (segments.get(stageSegment[k])
                .getKey()
                .equals(segment)) return zonePressure[stageUpZone[k]];
        }
        return 0.0;
    }

    public int getZoneCount() {
        ensureTopology();
        return zoneCount;
    }

    /** Zone pressures in Pa, for tests and displays. */
    public double[] getZonePressures() {
        ensureTopology();
        return zonePressure.clone();
    }

    /** Net shaft power of the stages over the last step, W: positive when the loop spends it. */
    public double getNetShaftPowerWatts() {
        double w = 0.0;
        for (double p : stageShaftPower) w += p;
        return w;
    }

    @Override
    public double getLocalVolumetricFlow(LoopSegment segment) {
        double rho = segment.getGasDensity();
        return rho > 0.0 ? segment.getGasMassFlow() / rho : 0.0;
    }

    @Override
    public double computeHydraulicInertia() {
        return 1.0;
    }

    // --- The step ---

    @Override
    public void step(double dt) {
        if (isRuptured || segments.isEmpty()) return;
        ensureTopology();
        if (!isCharged()) {
            volumetricFlowRate = 0.0;
            return;
        }
        int n = segments.size();

        // 1. Zone pressures, and the mass each segment holds at its zone's pressure
        for (int i = 0; i < n; i++) {
            LoopSegment s = segments.get(i);
            cap[i] = s.getGasMassKg();
            value[i] = s.getGasEnthalpy();
        }
        for (int z = 0; z < zoneCount; z++) {
            double m = 0.0;
            for (int i : zoneMembers[z]) m += cap[i];
            zoneMass[z] = m;
            zonePressure[z] = solvePressure(z, m, zonePressure[z]);
            // What the zone holds at its cutout pressure, at its enthalpies now. See wrinkle (W9)
            zoneCutoutMass[z] = Double.isFinite(zoneMaxPressure[z])
                ? massAt(zoneMembers[z], CUTOUT_FRACTION * zoneMaxPressure[z])
                : Double.POSITIVE_INFINITY;
        }
        for (int i = 0; i < n; i++) {
            gas.state(zonePressure[zoneOf[i]], value[i], st);
            target[i] = st.density * segments.get(i)
                .getGasVolume();
            tempK[i] = st.temperatureK;
            quality[i] = st.quality;
        }

        // 2. Stages
        boolean running = isPumpPowered() && !isBraking();
        Arrays.fill(zoneIn, 0.0);
        Arrays.fill(zoneOut, 0.0);
        double largestVolumeFlow = 0.0;
        for (int k = 0; k < stageSegment.length; k++) {
            int up = stageUpZone[k], down = stageDownZone[k];
            ctx.gas = gas;
            ctx.pressureIn = zonePressure[up];
            ctx.enthalpyIn = mixedEnthalpy(stageSegment[k]);
            gas.state(ctx.pressureIn, ctx.enthalpyIn, ctx.in);
            ctx.pressureOut = zonePressure[down];
            ctx.loopRunning = running;
            ctx.dt = dt;
            ctx.downstreamExitSuperheatK = exitSuperheat(down);
            ctx.maxVolumetricFlow = Double.POSITIVE_INFINITY;
            ctx.maxMassFlow = maxMassFlow(k, dt);
            ctx.downstreamMaxPressure = zoneMaxPressure[down];
            stageImpl[k].computeStage(ctx);
            // See wrinkle (W6)
            double answered = Math.max(0.0, ctx.massFlow);
            // Backward Euler on the upstream zone's emptying: the flow the step's end pressure would give. (W6)
            if (zoneMass[up] > 0.0) answered *= zoneMass[up] / (zoneMass[up] + answered * dt);
            // The lag is shorter than the time either zone takes to turn its gas over. See wrinkle (W6)
            double moving = Math.max(answered, stageMassFlow[k]);
            double turnover = moving > 0.0 ? Math.min(zoneMass[up], zoneMass[down]) / moving : Double.MAX_VALUE;
            double lag = stageImpl[k].isPassive() ? Math.min(FLOW_LAG_SECONDS, 0.25 * turnover) : FLOW_LAG_SECONDS;
            double flow = stageMassFlow[k] + (answered - stageMassFlow[k]) * dt / (lag + dt);
            if (answered == 0.0 && flow < 1e-6) flow = 0.0;
            flow = Math.min(flow, ctx.maxMassFlow);
            if (up == down) flow = 0.0;
            stageMassFlow[k] = flow;
            stageEnthalpyOut[k] = flow > 0.0 ? ctx.enthalpyOut : value[stageSegment[k]];
            stageShaftPower[k] = ctx.shaftPowerWatts;
            zoneOut[up] += flow;
            zoneIn[down] += flow;
            if (ctx.in.density > 0.0) largestVolumeFlow = Math.max(largestVolumeFlow, flow / ctx.in.density);
        }

        // 3. Each zone's new mass, spread over its segments as their densities say, a step at a time. See (W8)
        for (int z = 0; z < zoneCount; z++) {
            double next = Math.max(0.0, zoneMass[z] + (zoneIn[z] - zoneOut[z]) * dt);
            double held = 0.0;
            for (int i : zoneMembers[z]) held += target[i];
            double scale = held > 0.0 ? next / held : 0.0;
            double relaxed = 0.0;
            for (int i : zoneMembers[z]) {
                target[i] *= scale;
                target[i] = cap[i] + MASS_RELAXATION * (target[i] - cap[i]);
                relaxed += target[i];
            }
            if (relaxed > 0.0) {
                double fix = next / relaxed;
                for (int i : zoneMembers[z]) target[i] *= fix;
            } else if (next > 0.0) {
                for (int i : zoneMembers[z]) target[i] = next / zoneMembers[z].length;
            }
            zoneFlow[z] = Math.min(zoneIn[z], zoneOut[z]);
        }

        // 4. The flow through each segment, from what enters its zone to what leaves it. See wrinkle (W5)
        for (int z = 0; z < zoneCount; z++) {
            double volume = 0.0;
            for (int i : zoneMembers[z]) volume += segments.get(i)
                .getGasVolume();
            double before = 0.0;
            for (int i : zoneOrder[z]) {
                LoopSegment s = segments.get(i);
                double v = s.getGasVolume();
                if (isStage[i]) {
                    inflow[i] = stageMassFlow[stageIndexOf(i)] * dt;
                } else {
                    double along = volume > 0.0 ? (before + 0.5 * v) / volume : 0.5;
                    inflow[i] = (zoneIn[z] + (zoneOut[z] - zoneIn[z]) * along) * s.getFlowFraction() * dt;
                }
                before += v;
            }
        }

        // 5. Enthalpy advection on those flows, with the devices' heat as sources. See wrinkle (W1)
        for (int i = 0; i < n; i++) {
            LoopSegment s = segments.get(i);
            source[i] = s.takePendingGasHeatJoules();
            // A pull towards a temperature, as the enthalpy at that temperature at the zone's pressure now
            relaxK[i] = s.takeGasRelaxK();
            double kt = s.takeGasRelaxKH(), ua = s.takeGasRelaxUa();
            if (relaxK[i] > 0.0) {
                double p = zonePressure[zoneOf[i]], tb = kt / relaxK[i];
                double hb = gas.enthalpy(p, tb);
                // No more than the conductance times the temperature difference it finds: across a phase change a
                // small difference is a large one in enthalpy. See wrinkle (W1)
                double gap = Math.abs(hb - value[i]);
                double dT = Math.abs(tb - gas.state(p, value[i], st).temperatureK);
                if (gap > 1e-9) relaxK[i] = Math.min(relaxK[i], ua * dT / gap);
                relaxKH[i] = relaxK[i] * hb;
            } else {
                relaxKH[i] = 0.0;
            }
            fixed[i] = isStage[i];
        }
        for (int k = 0; k < stageSegment.length; k++) value[stageSegment[k]] = stageEnthalpyOut[k];
        LoopAdvection
            .stepScalar(advectionPlan, segments, cap, inflow, source, relaxK, relaxKH, fixed, value, incoming, true);
        for (int i = 0; i < n; i++) {
            LoopSegment s = segments.get(i);
            // See wrinkle (W1): heat capped on the last step's flow must not carry the gas past its bound
            value[i] = Math.max(Math.min(value[i], s.takeGasHeatCeiling()), s.takeGasHeatFloor());
            s.setLastGasHeatJoules(
                source[i] + (relaxK[i] > 0.0 ? relaxK[i] * (relaxKH[i] / relaxK[i] - value[i]) : 0.0));
            s.setGasEnthalpy(value[i]);
            s.setGasMassKg(target[i]);
            // Devices exchange with the zone's through-flow, not with the swings of compression. See wrinkle (W5)
            s.setGasMassFlow(inflow[i] / dt);
        }

        volumetricFlowRate = largestVolumeFlow > 1e-9 ? largestVolumeFlow : 0.0;
        deriveAndCheck(true);
    }

    private int stageIndexOf(int segment) {
        for (int k = 0; k < stageSegment.length; k++) if (stageSegment[k] == segment) return k;
        return -1;
    }

    /** The share of what segment u passes on that goes into segment d, by the flow fractions of u's downstreams. */
    private double shareInto(int u, int d) {
        int[] downs = downstreams[u];
        if (downs.length <= 1) return 1.0;
        double total = 0.0;
        for (int j : downs) total += Math.max(
            0.0,
            segments.get(j)
                .getFlowFraction());
        return total > 0.0 ? Math.max(
            0.0,
            segments.get(d)
                .getFlowFraction())
            / total : 1.0 / downs.length;
    }

    /**
     * Each segment's downstreams, and each zone's segments in flow order from where the gas enters it (its stages'
     * own segments) to where it leaves. A zone with a cycle in it (a loop with no stage) keeps its list order.
     */
    private void buildFlowOrder(int n) {
        int[] downCount = new int[n];
        for (int i = 0; i < n; i++) for (int u : upstreams[i]) downCount[u]++;
        downstreams = new int[n][];
        for (int i = 0; i < n; i++) downstreams[i] = new int[downCount[i]];
        int[] fill = new int[n];
        for (int i = 0; i < n; i++) for (int u : upstreams[i]) downstreams[u][fill[u]++] = i;
        zoneOrder = new int[zoneCount][];
        int[] pending = new int[n];
        for (int z = 0; z < zoneCount; z++) {
            int[] members = zoneMembers[z];
            int[] order = new int[members.length];
            int head = 0, tail = 0;
            for (int i : members) {
                int p = 0;
                if (!isStage[i]) for (int u : upstreams[i]) if (zoneOf[u] == z) p++;
                pending[i] = p;
                if (p == 0) order[tail++] = i;
            }
            while (head < tail) {
                int i = order[head++];
                for (int d : downstreams[i]) {
                    if (zoneOf[d] == z && !isStage[d] && --pending[d] == 0) order[tail++] = d;
                }
            }
            zoneOrder[z] = tail == members.length ? order : members.clone();
        }
    }

    /** Most mass a stage may pass this step. See wrinkles (W2) and (W9) of Note [Gas loop pressure zones] */
    private double maxMassFlow(int k, double dt) {
        int up = stageUpZone[k], down = stageDownZone[k];
        double limit = MAX_TURNOVER * zoneMass[up] / dt;
        if (!stageImpl[k].isPassive() && Double.isFinite(zoneMaxPressure[down])) {
            double p = zonePressure[down], pMax = zoneMaxPressure[down];
            if (p >= CUTOUT_LATCH_FRACTION * pMax) stageCutOut[k] = true;
            else if (p < CUTOUT_RELEASE_FRACTION * pMax) stageCutOut[k] = false;
            if (stageCutOut[k]) return 0.0;
            limit = Math.min(limit, 0.5 * Math.max(0.0, zoneCutoutMass[down] - zoneMass[down]) / dt);
        }
        if (stageImpl[k].isPassive()) {
            double dp = zonePressure[up] - zonePressure[down];
            if (dp <= 0.0) return 0.0;
            double cu = zoneCompliance[up], cd = zoneCompliance[down];
            if (cu > 0.0 && cd > 0.0) {
                double equalizing = dp / (1.0 / cu + 1.0 / cd);
                limit = Math.min(limit, 0.5 * equalizing / dt);
            }
        }
        return limit;
    }

    /** Enthalpy flowing into segment i: its upstreams', mixed by their flow fractions. */
    private double mixedEnthalpy(int i) {
        int[] ups = upstreams[i];
        if (ups.length == 0) return value[i];
        if (ups.length == 1) return value[ups[0]];
        double total = 0.0, h = 0.0;
        for (int u : ups) {
            double w = Math.max(
                0.0,
                segments.get(u)
                    .getFlowFraction());
            total += w;
            h += w * value[u];
        }
        return total > 0.0 ? h / total : value[ups[0]];
    }

    /** Superheat of the gas leaving zone z towards its next stage (see GasProperty.State.superheatK). */
    private double exitSuperheat(int z) {
        int i = zoneExitSegment[z];
        if (i < 0) return 0.0;
        double ts = gas.saturationTemperature(zonePressure[z]);
        if (quality[i] >= 1.0) return tempK[i] - ts;
        return -(1.0 - quality[i]) * 20.0 - (ts - tempK[i]);
    }

    /** Sets each segment's derived state from its zone's pressure and its enthalpy, then checks the limits. */
    private void deriveAndCheck(boolean check) {
        double peakP = 0.0, peakT = -Double.MAX_VALUE;
        for (int i = 0; i < segments.size(); i++) {
            LoopSegment s = segments.get(i);
            int z = zoneOf[i];
            double p = zonePressure[z];
            gas.state(p, s.getGasEnthalpy(), st);
            s.setGasDerived(st.quality, st.density);
            s.setAdvectedTemperatureCelsius(st.temperatureCelsius());
            double inH = incoming.length > i && check ? incoming[i] : s.getGasEnthalpy();
            s.setGasIncomingEnthalpy(inH);
            s.setIncomingTemperatureCelsius(
                gas.state(p, inH, st)
                    .temperatureCelsius());
            gas.state(p, s.getGasEnthalpy(), st);
            s.setInletPressureBar(p / 1e5);
            s.setOutletPressureBar(p / 1e5);
            s.markAdvected();
            peakP = Math.max(peakP, p / 1e5);
            peakT = Math.max(peakT, st.temperatureCelsius());
            if (!check || isRuptured) continue;
            if (s.hasPressureLimit() && p / 1e5 > s.getMaxPressureBar()) {
                isRuptured = true;
                rupturedSegment = s;
                fail(LoopFailure.BURST, s.getId(), p / 1e5, s.getMaxPressureBar());
            } else if (holdsCoolant() && s.hasTemperatureLimit()
                && st.temperatureCelsius() > s.getMaxTemperatureCelsius()) {
                    isRuptured = true;
                    rupturedSegment = s;
                    fail(LoopFailure.MELTED, s.getId(), st.temperatureCelsius(), s.getMaxTemperatureCelsius());
                } else if (!Double.isNaN(gas.getFreezingPointK()) && st.temperatureK < gas.getFreezingPointK()
                    && s.getGasMassKg() > 0.0) {
                        isRuptured = true;
                        rupturedSegment = s;
                        fail(
                            LoopFailure.FROZE,
                            st.temperatureCelsius(),
                            s.getId(),
                            gas.getFreezingPointK() - 273.15,
                            gas.getFluidName());
                    }
        }
        peakLoopPressureBar = peakP;
        if (peakT > -Double.MAX_VALUE) peakLoopTempCelsius = peakT;
    }

    /**
     * The pressure at which zone z's segments, at their enthalpies, hold mass m: Illinois false position on ln P,
     * from a bracket around the last pressure. Records the zone's compliance dm/dP. See Note [Gas loop pressure
     * zones]
     */
    private double solvePressure(int z, double m, double guess) {
        int[] members = zoneMembers[z];
        if (m <= 0.0 || members.length == 0) {
            zoneCompliance[z] = 0.0;
            return MIN_PRESSURE_PA;
        }
        double p0 = Math.max(MIN_PRESSURE_PA, Double.isFinite(guess) && guess > 0 ? guess : 1e5);
        double lo = Math.log(p0 / 1.2), hi = Math.log(p0 * 1.2);
        double flo = massAt(members, Math.exp(lo)) - m, fhi = massAt(members, Math.exp(hi)) - m;
        for (int i = 0; i < 80 && flo > 0.0; i++) {
            hi = lo;
            fhi = flo;
            lo -= 0.7;
            if (Math.exp(lo) < MIN_PRESSURE_PA) {
                lo = Math.log(MIN_PRESSURE_PA);
                flo = massAt(members, MIN_PRESSURE_PA) - m;
                break;
            }
            flo = massAt(members, Math.exp(lo)) - m;
        }
        if (flo > 0.0) {
            zoneCompliance[z] = 0.0;
            return MIN_PRESSURE_PA;
        }
        for (int i = 0; i < 80 && fhi < 0.0; i++) {
            lo = hi;
            flo = fhi;
            hi += 0.7;
            if (Math.exp(hi) > MAX_PRESSURE_PA) {
                hi = Math.log(MAX_PRESSURE_PA);
                fhi = massAt(members, MAX_PRESSURE_PA) - m;
                break;
            }
            fhi = massAt(members, Math.exp(hi)) - m;
        }
        double x = 0.5 * (lo + hi);
        int side = 0;
        for (int i = 0; i < 100; i++) {
            if (fhi == flo) break;
            x = (lo * fhi - hi * flo) / (fhi - flo);
            double fx = massAt(members, Math.exp(x)) - m;
            if (Math.abs(fx) <= 1e-10 * m || hi - lo < 1e-12) break;
            if (fx * fhi > 0.0) {
                hi = x;
                fhi = fx;
                if (side == -1) flo *= 0.5;
                side = -1;
            } else {
                lo = x;
                flo = fx;
                if (side == 1) fhi *= 0.5;
                side = 1;
            }
        }
        double p = Math.exp(x);
        double dp = p * 1e-4;
        zoneCompliance[z] = (massAt(members, p + dp) - massAt(members, p)) / dp;
        return p;
    }

    /** Mass the segments hold at pressure p, each at its own enthalpy. */
    private double massAt(int[] members, double p) {
        double ts = gas.saturationTemperature(p);
        double latent = gas.latentHeat(ts);
        double hg = gas.saturatedVaporEnthalpy(ts);
        double m = 0.0;
        for (int i : members) {
            LoopSegment s = segments.get(i);
            m += gas.density(p, s.getGasEnthalpy(), ts, hg, latent) * s.getGasVolume();
        }
        return m;
    }

    /** Rebuilds zones and stages after the segments or the stages changed. */
    private void ensureTopology() {
        if (topologyValid && zoneOf.length == segments.size()) return;
        int n = segments.size();
        upstreams = LoopAdvection.upstreamIndices(advectionPlan, segments);
        isStage = new boolean[n];
        int stageCount = 0;
        for (int i = 0; i < n; i++) {
            if (stages.containsKey(
                segments.get(i)
                    .getKey())) {
                isStage[i] = true;
                stageCount++;
            }
        }
        // Zones: segments joined by an edge that doesn't enter a stage
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        for (int i = 0; i < n; i++) {
            if (isStage[i]) continue;
            for (int u : upstreams[i]) union(parent, i, u);
        }
        zoneOf = new int[n];
        int[] zoneIdOfRoot = new int[n];
        Arrays.fill(zoneIdOfRoot, -1);
        zoneCount = 0;
        for (int i = 0; i < n; i++) {
            int r = find(parent, i);
            if (zoneIdOfRoot[r] < 0) zoneIdOfRoot[r] = zoneCount++;
            zoneOf[i] = zoneIdOfRoot[r];
        }
        int[] sizes = new int[zoneCount];
        for (int i = 0; i < n; i++) sizes[zoneOf[i]]++;
        zoneMembers = new int[zoneCount][];
        for (int z = 0; z < zoneCount; z++) zoneMembers[z] = new int[sizes[z]];
        int[] fill = new int[zoneCount];
        for (int i = 0; i < n; i++) zoneMembers[zoneOf[i]][fill[zoneOf[i]]++] = i;

        stageSegment = new int[stageCount];
        stageImpl = new GasPressureStage[stageCount];
        stageUpZone = new int[stageCount];
        stageDownZone = new int[stageCount];
        zoneExitSegment = new int[zoneCount];
        Arrays.fill(zoneExitSegment, -1);
        int k = 0;
        for (int i = 0; i < n; i++) {
            if (!isStage[i]) continue;
            stageSegment[k] = i;
            stageImpl[k] = stages.get(
                segments.get(i)
                    .getKey());
            int upSeg = upstreams[i].length > 0 ? upstreams[i][0] : i;
            stageUpZone[k] = zoneOf[upSeg];
            stageDownZone[k] = zoneOf[i];
            if (zoneExitSegment[stageUpZone[k]] < 0) zoneExitSegment[stageUpZone[k]] = upSeg;
            k++;
        }

        double[] oldPressures = zonePressure;
        zonePressure = new double[zoneCount];
        for (int z = 0; z < zoneCount; z++) {
            zonePressure[z] = z < oldPressures.length ? oldPressures[z] : 1e5;
        }
        // Start from the pressures segments last had (a save, a recrawl)
        for (int i = 0; i < n; i++) {
            double bar = segments.get(i)
                .getInletPressureBar();
            if (bar > 0.0) zonePressure[zoneOf[i]] = bar * 1e5;
        }
        zoneCompliance = new double[zoneCount];
        zoneFlow = new double[zoneCount];
        zoneIn = new double[zoneCount];
        zoneOut = new double[zoneCount];
        zoneMass = new double[zoneCount];
        stageMassFlow = new double[stageCount];
        stageEnthalpyOut = new double[stageCount];
        stageShaftPower = new double[stageCount];
        target = new double[n];
        outflow = new double[n];
        relaxK = new double[n];
        relaxKH = new double[n];
        buildFlowOrder(n);
        zoneMaxPressure = new double[zoneCount];
        zoneCutoutMass = new double[zoneCount];
        stageCutOut = new boolean[stageCount];
        Arrays.fill(zoneMaxPressure, Double.POSITIVE_INFINITY);
        for (int i = 0; i < n; i++) {
            LoopSegment s = segments.get(i);
            if (s.hasPressureLimit()) {
                zoneMaxPressure[zoneOf[i]] = Math.min(zoneMaxPressure[zoneOf[i]], s.getMaxPressureBar() * 1e5);
            }
        }
        cap = new double[n];
        inflow = new double[n];
        source = new double[n];
        value = new double[n];
        incoming = new double[n];
        tempK = new double[n];
        quality = new double[n];
        fixed = new boolean[n];
        adoptNewSegments();
        topologyValid = true;
    }

    /** See wrinkle (W3) of Note [Gas loop pressure zones] */
    private void adoptNewSegments() {
        double m = 0.0, mh = 0.0;
        boolean any = false, missing = false;
        for (LoopSegment s : segments) {
            if (s.holdsGas()) {
                any = true;
                m += s.getGasMassKg();
                mh += s.getGasMassKg() * s.getGasEnthalpy();
            } else {
                missing = true;
            }
        }
        if (!any || !missing) return;
        double h = m > 0.0 ? mh / m : gas.vaporEnthalpy(293.15);
        for (LoopSegment s : segments) {
            if (!s.holdsGas()) {
                s.setGasEnthalpy(h);
                s.setGasMassKg(0.0);
            }
        }
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a), rb = find(parent, b);
        if (ra != rb) parent[ra] = rb;
    }
}
