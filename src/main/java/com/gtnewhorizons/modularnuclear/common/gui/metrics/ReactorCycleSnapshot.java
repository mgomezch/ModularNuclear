package com.gtnewhorizons.modularnuclear.common.gui.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    public static ReactorCycleSnapshot create(long cycleIndex, List<Double> temperatures, List<Double> reactivities,
        int escapedNeutrons, int fastNeutronsProduced, int thermalNeutronsAbsorbed, long directEU, long coolantOutput) {
        return create(
            cycleIndex,
            temperatures,
            reactivities,
            escapedNeutrons,
            fastNeutronsProduced,
            thermalNeutronsAbsorbed,
            directEU,
            coolantOutput,
            NuclearSimulationEngine.DEFAULT_TICKS_PER_CYCLE);
    }

    public static ReactorCycleSnapshot create(long cycleIndex, List<Double> temperatures, List<Double> reactivities,
        int escapedNeutrons, int fastNeutronsProduced, int thermalNeutronsAbsorbed, long directEU, long coolantOutput,
        int ticksPerCycle) {
        ReactorCycleSnapshot s = new ReactorCycleSnapshot();
        s.cycleIndex = cycleIndex;
        s.escapedNeutrons = escapedNeutrons;
        s.fastNeutronsProduced = fastNeutronsProduced;
        s.thermalNeutronsAbsorbed = thermalNeutronsAbsorbed;
        s.directEU = directEU;
        s.coolantOutput = (int) Math.min(Integer.MAX_VALUE, Math.max(0, coolantOutput));
        s.ticksPerCycle = ticksPerCycle;

        // Compute quartiles for temperature
        float[] tStats = calculateQuartiles(temperatures);
        s.tempMin = tStats[0];
        s.tempQ1 = tStats[1];
        s.tempMedian = tStats[2];
        s.tempQ3 = tStats[3];
        s.tempMax = tStats[4];
        s.tempAvg = tStats[5];

        // Compute quartiles for reactivity
        float[] rStats = calculateQuartiles(reactivities);
        s.reactivityMin = rStats[0];
        s.reactivityQ1 = rStats[1];
        s.reactivityMedian = rStats[2];
        s.reactivityQ3 = rStats[3];
        s.reactivityMax = rStats[4];
        s.reactivityAvg = rStats[5];

        return s;
    }

    /**
     * Calculates statistical summary: min, Q1, median, Q3, max, and average using standard
     * linear interpolation between closest ranks (NIST / Excel PERCENTILE.INC).
     *
     * @return float array: [min, Q1, median, Q3, max, avg]
     */
    public static float[] calculateQuartiles(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return new float[] { 0f, 0f, 0f, 0f, 0f, 0f };
        }
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        double sum = 0.0;
        for (double v : sorted) {
            sum += v;
        }
        float avg = (float) (sum / n);
        float min = sorted.get(0)
            .floatValue();
        float max = sorted.get(n - 1)
            .floatValue();

        if (n == 1) {
            return new float[] { min, min, min, min, max, avg };
        }

        float q1 = (float) getPercentile(sorted, 25.0);
        float median = (float) getPercentile(sorted, 50.0);
        float q3 = (float) getPercentile(sorted, 75.0);

        return new float[] { min, q1, median, q3, max, avg };
    }

    private static double getPercentile(List<Double> sortedValues, double percentile) {
        int n = sortedValues.size();
        if (n == 1) return sortedValues.get(0);
        double rank = (percentile / 100.0) * (n - 1);
        int lowerIndex = (int) Math.floor(rank);
        int upperIndex = (int) Math.ceil(rank);
        if (lowerIndex == upperIndex) {
            return sortedValues.get(lowerIndex);
        }
        double weight = rank - lowerIndex;
        return sortedValues.get(lowerIndex) * (1.0 - weight) + sortedValues.get(upperIndex) * weight;
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
