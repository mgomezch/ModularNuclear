package com.gtnewhorizons.modularnuclear.common.nei;

import java.util.Arrays;

import com.gtnewhorizons.modularnuclear.common.nuclear.EngineParameters;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;

/** The row of tiles on the heat card, heating up from one end. See Note [Thermal and heat cards]. */
public final class HeatRowSimulation {

    public static final int CELLS = 7;
    public static final int MIDDLE = CELLS / 2;
    /** How long the row runs in game time, and how many frames per second of it are kept. */
    public static final int SECONDS = 12;
    public static final int FRAMES_PER_SECOND = 10;
    public static final double HOT_END = 1000.0;
    public static final double COLD_END = ComponentCardValues.AMBIENT;

    /** The hatch tier a fluid is shown in: EV. */
    public static final int HATCH_TIER = 4;

    private static final int TICKS_PER_SECOND = 20;

    private HeatRowSimulation() {}

    /**
     * Runs a row with the component in the middle and tiles of k {@link ComponentCardValues#NEIGHBOUR_K} around it, all
     * starting at ambient temperature. The left end stays at {@link #HOT_END} and the right end at {@link #COLD_END}.
     *
     * @return temperatures by frame ({@link #FRAMES_PER_SECOND}), then by cell; frame 0 is the start
     */
    public static float[][] run(ComponentCardValues v, EngineParameters p) {
        double[] temperature = new double[CELLS];
        double[] capacity = new double[CELLS];
        double[] k = new double[CELLS];
        for (int i = 0; i < CELLS; i++) {
            temperature[i] = COLD_END;
            capacity[i] = p.huPerDegree;
            k[i] = Math.max(NuclearSimulationEngine.BASE_HEAT_CONDUCTION, ComponentCardValues.NEIGHBOUR_K);
        }
        temperature[0] = HOT_END;
        k[MIDDLE] = Math.max(NuclearSimulationEngine.BASE_HEAT_CONDUCTION, v.k);
        if (v.inHatch) capacity[MIDDLE] = p.hatchHeatCapacity(HATCH_TIER);

        // As in NuclearSimulationEngine.simulate, enough sub-steps that no cell overshoots
        double conduction = p.gridConductionMultiplier * p.huPerDegree / TICKS_PER_SECOND;
        int substeps = Math.max(5, (int) Math.ceil(8.0 * Math.max(k[MIDDLE], k[0]) * conduction / p.huPerDegree));

        double[] delta = new double[CELLS];
        float[][] frames = new float[SECONDS * FRAMES_PER_SECOND + 1][CELLS];
        for (int frame = 0; frame < frames.length; frame++) {
            for (int i = 0; i < CELLS; i++) frames[frame][i] = (float) temperature[i];
            for (int tick = 0; tick < TICKS_PER_SECOND / FRAMES_PER_SECOND; tick++) {
                for (int sub = 0; sub < substeps; sub++) {
                    conduct(temperature, capacity, k, v.dampening, conduction / substeps, delta);
                }
                act(temperature, capacity, v, p);
            }
        }
        return frames;
    }

    private static void conduct(double[] temperature, double[] capacity, double[] k, double dampening, double factor,
        double[] delta) {
        Arrays.fill(delta, 0.0);
        for (int i = 0; i < CELLS - 1; i++) {
            double open = (i == MIDDLE || i + 1 == MIDDLE) ? 1.0 - dampening : 1.0;
            double flow = (temperature[i] - temperature[i + 1]) * (k[i] + k[i + 1]) / 2.0 * factor * open;
            delta[i] -= flow;
            delta[i + 1] += flow;
        }
        // The ends stay put
        for (int i = 1; i < CELLS - 1; i++) temperature[i] += delta[i] / capacity[i];
    }

    private static void act(double[] temperature, double[] capacity, ComponentCardValues v, EngineParameters p) {
        double t = temperature[MIDDLE];
        double c = capacity[MIDDLE];
        switch (v.action) {
            case VENTS -> {
                if (t > COLD_END) temperature[MIDDLE] = Math.max(COLD_END, t - v.actionHeat / c);
            }
            case STORES -> {
                if (t > COLD_END) {
                    temperature[MIDDLE] = Math.max(COLD_END, t - v.actionRate / TICKS_PER_SECOND / c);
                }
            }
            case BOILS, COOLS_TO_AMBIENT -> {
                double sink = v.action == ComponentCardValues.HeatAction.BOILS ? v.actionRate : COLD_END;
                temperature[MIDDLE] = t - p.conductiveHeatTransfer(t, sink, HATCH_TIER, 1.0, 1) / c;
            }
            case EXCHANGES -> exchange(temperature, capacity, v.actionHeat);
            default -> {}
        }
    }

    /** As NuclearSimulationEngine's exchanger plans do for one exchanger: towards the mean, capped in HU. */
    private static void exchange(double[] temperature, double[] capacity, double maxHeat) {
        double heat = 0.0;
        double totalCapacity = 0.0;
        for (int i = MIDDLE - 1; i <= MIDDLE + 1; i++) {
            heat += temperature[i] * capacity[i];
            totalCapacity += capacity[i];
        }
        double mean = heat / totalCapacity;
        double excess = 0.0;
        for (int i = MIDDLE - 1; i <= MIDDLE + 1; i++) {
            excess += Math.max(0.0, temperature[i] - mean) * capacity[i];
        }
        if (excess <= 0.0) return;
        double share = Math.min(1.0, maxHeat / excess);
        for (int i = MIDDLE - 1; i <= MIDDLE + 1; i++) temperature[i] += share * (mean - temperature[i]);
    }
}
