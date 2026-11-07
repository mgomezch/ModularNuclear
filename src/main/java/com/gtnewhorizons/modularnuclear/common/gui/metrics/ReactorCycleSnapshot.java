package com.gtnewhorizons.modularnuclear.common.gui.metrics;

import java.util.Arrays;

import net.minecraft.network.PacketBuffer;

import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;

/**
 * Immutable data snapshot of a single MPTR reactor cycle for telemetry and charting.
 * Records temperature and reactivity distributions (min, Q1, median, Q3, max, avg)
 * and scalar metrics (neutrons, direct EU, coolant output).
 */
public class ReactorCycleSnapshot {

    public long cycleIndex;

    // Temperature distribution (°C)
    public float tempMin;
    public float tempQ1;
    public float tempMedian;
    public float tempQ3;
    public float tempMax;
    public float tempAvg;

    // Reactivity distribution (%)
    public float reactivityMin;
    public float reactivityQ1;
    public float reactivityMedian;
    public float reactivityQ3;
    public float reactivityMax;
    public float reactivityAvg;

    // Scalar reactor statistics
    public int escapedNeutrons;
    public int fastNeutronsProduced;
    public int thermalNeutronsAbsorbed;
    public long directEU;
    public int coolantOutput;
    public int ticksPerCycle = NuclearSimulationEngine.DEFAULT_TICKS_PER_CYCLE;

    public double getCyclesPerSec() {
        return ticksPerCycle > 0 ? (20.0 / (double) ticksPerCycle) : 1.0;
    }

    public double getFastNeutronsPerSec() {
        return fastNeutronsProduced * getCyclesPerSec();
    }

    public double getThermalNeutronsAbsorbedPerSec() {
        return thermalNeutronsAbsorbed * getCyclesPerSec();
    }

    public double getEscapedNeutronsPerSec() {
        return escapedNeutrons * getCyclesPerSec();
    }

    public double getCoolantOutputPerSec() {
        return coolantOutput * getCyclesPerSec();
    }

    /**
     * A cycle's snapshot from the first {@code tempCount} core temperatures and {@code reactivityCount} fuel
     * reactivities in the given arrays, which it sorts in place (the reactor reuses them every cycle).
     */
    public static ReactorCycleSnapshot create(long cycleIndex, double[] temperatures, int tempCount,
        double[] reactivities, int reactivityCount, int escapedNeutrons, int fastNeutronsProduced,
        int thermalNeutronsAbsorbed, long directEU, long coolantOutput, int ticksPerCycle) {
        ReactorCycleSnapshot s = new ReactorCycleSnapshot();
        s.cycleIndex = cycleIndex;
        s.escapedNeutrons = escapedNeutrons;
        s.fastNeutronsProduced = fastNeutronsProduced;
        s.thermalNeutronsAbsorbed = thermalNeutronsAbsorbed;
        s.directEU = directEU;
        s.coolantOutput = (int) Math.min(Integer.MAX_VALUE, Math.max(0, coolantOutput));
        s.ticksPerCycle = ticksPerCycle;

        // Compute quartiles for temperature
        float[] tStats = calculateQuartiles(temperatures, tempCount);
        s.tempMin = tStats[0];
        s.tempQ1 = tStats[1];
        s.tempMedian = tStats[2];
        s.tempQ3 = tStats[3];
        s.tempMax = tStats[4];
        s.tempAvg = tStats[5];

        // Compute quartiles for reactivity
        float[] rStats = calculateQuartiles(reactivities, reactivityCount);
        s.reactivityMin = rStats[0];
        s.reactivityQ1 = rStats[1];
        s.reactivityMedian = rStats[2];
        s.reactivityQ3 = rStats[3];
        s.reactivityMax = rStats[4];
        s.reactivityAvg = rStats[5];

        return s;
    }

    /**
     * Statistical summary of the first {@code n} values, which it sorts in place: min, Q1, median, Q3, max and average,
     * with linear interpolation between closest ranks (NIST / Excel PERCENTILE.INC).
     *
     * @return float array: [min, Q1, median, Q3, max, avg]
     */
    public static float[] calculateQuartiles(double[] values, int n) {
        if (values == null || n <= 0) {
            return new float[] { 0f, 0f, 0f, 0f, 0f, 0f };
        }
        Arrays.sort(values, 0, n);
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            sum += values[i];
        }
        float avg = (float) (sum / n);
        float min = (float) values[0];
        float max = (float) values[n - 1];

        if (n == 1) {
            return new float[] { min, min, min, min, max, avg };
        }

        float q1 = (float) getPercentile(values, n, 25.0);
        float median = (float) getPercentile(values, n, 50.0);
        float q3 = (float) getPercentile(values, n, 75.0);

        return new float[] { min, q1, median, q3, max, avg };
    }

    private static double getPercentile(double[] sorted, int n, double percentile) {
        double rank = (percentile / 100.0) * (n - 1);
        int lowerIndex = (int) Math.floor(rank);
        int upperIndex = (int) Math.ceil(rank);
        if (lowerIndex == upperIndex) {
            return sorted[lowerIndex];
        }
        double weight = rank - lowerIndex;
        return sorted[lowerIndex] * (1.0 - weight) + sorted[upperIndex] * weight;
    }

    public void writeToBuffer(PacketBuffer buf) {
        buf.writeLong(cycleIndex);
        buf.writeFloat(tempMin);
        buf.writeFloat(tempQ1);
        buf.writeFloat(tempMedian);
        buf.writeFloat(tempQ3);
        buf.writeFloat(tempMax);
        buf.writeFloat(tempAvg);

        buf.writeFloat(reactivityMin);
        buf.writeFloat(reactivityQ1);
        buf.writeFloat(reactivityMedian);
        buf.writeFloat(reactivityQ3);
        buf.writeFloat(reactivityMax);
        buf.writeFloat(reactivityAvg);

        buf.writeVarIntToBuffer(escapedNeutrons);
        buf.writeVarIntToBuffer(fastNeutronsProduced);
        buf.writeVarIntToBuffer(thermalNeutronsAbsorbed);
        buf.writeLong(directEU);
        buf.writeVarIntToBuffer(coolantOutput);
        buf.writeVarIntToBuffer(ticksPerCycle);
    }

    public static ReactorCycleSnapshot readFromBuffer(PacketBuffer buf) {
        ReactorCycleSnapshot s = new ReactorCycleSnapshot();
        s.cycleIndex = buf.readLong();
        s.tempMin = buf.readFloat();
        s.tempQ1 = buf.readFloat();
        s.tempMedian = buf.readFloat();
        s.tempQ3 = buf.readFloat();
        s.tempMax = buf.readFloat();
        s.tempAvg = buf.readFloat();

        s.reactivityMin = buf.readFloat();
        s.reactivityQ1 = buf.readFloat();
        s.reactivityMedian = buf.readFloat();
        s.reactivityQ3 = buf.readFloat();
        s.reactivityMax = buf.readFloat();
        s.reactivityAvg = buf.readFloat();

        s.escapedNeutrons = buf.readVarIntFromBuffer();
        s.fastNeutronsProduced = buf.readVarIntFromBuffer();
        s.thermalNeutronsAbsorbed = buf.readVarIntFromBuffer();
        s.directEU = buf.readLong();
        s.coolantOutput = buf.readVarIntFromBuffer();
        s.ticksPerCycle = buf.readVarIntFromBuffer();
        return s;
    }
}
