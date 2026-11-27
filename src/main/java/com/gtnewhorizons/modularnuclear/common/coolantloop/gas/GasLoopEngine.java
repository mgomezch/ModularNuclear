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
     densities say, carried from segment to segment by flows worked out in flow order (W5).
  4. Enthalpy is advected on those flows with LoopAdvection.stepScalar, each segment's mass as its capacity and a
     stage's own segment fixed at the stage's outlet enthalpy.
  5. Each segment's temperature, quality and density follow from its zone's pressure and its new enthalpy, and
     the limits are checked: a pipe above its rating bursts, gas below its freezing point freezes, a segment above
     its temperature limit melts.

Moving parts.  GasLoopEngine (zones, the solve and the step), GasPressureStage and its Context, GasStageModels,
GasProperty, LoopSegment's gas fields (getGasEnthalpy, getGasMassKg and the pending heat), and
LoopAdvection.stepScalar.  Devices exchange heat with a segment through SegmentHeat, which works for liquid and gas
loops alike.

Wrinkles:

(W1) Heat from devices is applied in the next step's advection, not to the segment at once
     (LoopSegment.addPendingGasHeatJoules, the source term of stepScalar).  A device's segment holds far less gas
     than flows through it in a tick (a PHE's segment holds a fraction of a kilogram of vapour; tens of kilograms
     pass in a tick), so heat added to the segment's own mass could move almost nothing without the segment
     crossing the temperature it exchanges with.  As a source in the advection the heat goes into the gas
     flowing through, the outlet settles at h_in - Q / mdot, and the heat is conserved exactly.
     SegmentHeat.limit caps a device's heat by what the gas in and through the segment can give up or take
     before it reaches the device's temperature, on the last step's flow.  The flow may change before the step
     applies the heat, so the segment also keeps the enthalpy the device's temperature bounds it to
     (LoopSegment.boundGasHeat), and the step holds the new enthalpy to it.  What the bound holds back of the
     segment's own content stays pending for the next step, so no heat is lost.

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

(W5) Gas moves from segment to segment with its mass, so heat stays where its gas is.  A step works out the mass
     each segment ends with: what its density at its zone's pressure holds, scaled so the zone holds its mass
     plus what the stages brought in less what they took out.  Then, in flow order from the stage that feeds
     the zone (buildFlowOrder), each segment's inflow is what its upstream passed on, and its outflow is that
     inflow less the mass it gained.  Advection on those flows by backward Euler conserves heat exactly when
     the flows change along the zone: (m + F_in) h' = m h + F_in h_in + Q.  A zone with no stage feeding it (a
     loop at rest) has no flows, and its mass only re-spreads.
     Devices see the zone's through-flow (LoopSegment.getGasMassFlow: the smaller of what enters and leaves the
     zone, times the segment's flow fraction), not the segment's own inflow.  The inflows swing as gas is
     compressed and expanded along the zone, and a recuperator that sized its exchange on them fed the swings
     back into the densities and rang.

(W8) Each step takes a segment's mass only halfway (MASS_RELAXATION) to what its density at the zone's
     pressure holds, as gas takes time to flow between parts of a zone and even out their pressures.  A segment
     whose gas a device cools quickly would otherwise draw in its whole new share of the zone's mass at once,
     and the flows that carry it (W5) swing the neighbouring segments' temperatures from tick to tick.  The zone's
     mass still comes out exactly; only how it is spread lags.
     Historical note: a first version advected each zone at one flow and spread mass changes over the zone at
     the segments' own enthalpies, with corrections spread over the zone to balance the heat.  The corrections
     moved heat from one end of a zone to the other and set a Brayton loop with a recuperator oscillating.

(W6) A stage's flow follows what it answers with a lag of FLOW_LAG_SECONDS, the gas's inertia.  Without it the
     flows and pressures answer each other within a tick and ring with a period of two ticks: the compressor's
     flow follows the suction pressure the valve's last flow left, and the other way round.

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
    public static final double CUTOUT_FRACTION = 0.85;
    /** Most of its upstream zone's mass a stage takes in a step. See wrinkle (W2) */
    static final double MAX_TURNOVER = 0.5;
    /** Discharge pressure (share of the rating) that stops a compressor, and that lets it start again. */
    public static final double CUTOUT_LATCH_FRACTION = 0.9;
    public static final double CUTOUT_RELEASE_FRACTION = 0.8;

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
    private double[] target = new double[0], outflow = new double[0];
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
            double flow = stageMassFlow[k] + (answered - stageMassFlow[k]) * dt / (FLOW_LAG_SECONDS + dt);
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

        // 4. The flows between segments that carry each one's change in mass. See wrinkle (W5)
        Arrays.fill(inflow, 0.0);
        for (int k = 0; k < stageSegment.length; k++) inflow[stageSegment[k]] = stageMassFlow[k] * dt;
        for (int z = 0; z < zoneCount; z++) {
            for (int i : zoneOrder[z]) {
                if (!isStage[i]) {
                    double in = 0.0;
                    for (int u : upstreams[i]) {
                        if (zoneOf[u] == z) in += outflow[u] * shareInto(u, i);
                    }
                    inflow[i] = in;
                }
                outflow[i] = Math.max(0.0, inflow[i] - (target[i] - cap[i]));
            }
        }

        // 5. Enthalpy advection on those flows, with the devices' heat as sources. See wrinkle (W1)
        for (int i = 0; i < n; i++) {
            source[i] = segments.get(i)
                .takePendingGasHeatJoules();
            fixed[i] = isStage[i];
        }
        for (int k = 0; k < stageSegment.length; k++) value[stageSegment[k]] = stageEnthalpyOut[k];
        LoopAdvection.stepScalar(advectionPlan, segments, cap, inflow, source, fixed, value, incoming, true);
        for (int i = 0; i < n; i++) {
            LoopSegment s = segments.get(i);
            // See wrinkle (W1): heat capped on the last step's flow must not carry the gas past its bound
            double bounded = Math.max(Math.min(value[i], s.takeGasHeatCeiling()), s.takeGasHeatFloor());
            if (bounded != value[i]) {
                s.addPendingGasHeatJoules(target[i] * (value[i] - bounded));
                value[i] = bounded;
            }
            s.setGasEnthalpy(value[i]);
            s.setGasMassKg(target[i]);
            // Devices exchange with the zone's through-flow, not with the swings of compression. See wrinkle (W5)
            s.setGasMassFlow(isStage[i] ? inflow[i] / dt : zoneFlow[zoneOf[i]] * s.getFlowFraction());
        }

        volumetricFlowRate = largestVolumeFlow > 1e-9 ? largestVolumeFlow : 0.0;
        deriveAndCheck(true);
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
