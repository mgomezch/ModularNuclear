package com.gtnewhorizons.modularnuclear.common.nuclear.standalone.cli;

import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;

/** Detects when a run has settled: power, temperatures and loop temperature flat over a window. */
public final class SteadyStateDetector {

    private final int windowSize;
    private final double slopeTolerancePct;
    private final double halfDiffTolerancePct;
    private final double tempSlopeTolerance;
    private final double maxCv;

    private final double[] powerHistory;
    private final double[] tempHistory;
    private final double[] loopTempHistory;
    private int count = 0;
    private int head = 0;

    public SteadyStateDetector(int windowSize) {
        this(windowSize, 0.008, 0.005, 0.005, 0.08);
    }

    public SteadyStateDetector(int windowSize, double slopeTolerancePct, double halfDiffTolerancePct,
        double tempSlopeTolerance, double maxCv) {
        this.windowSize = Math.max(20, windowSize);
        this.slopeTolerancePct = slopeTolerancePct;
        this.halfDiffTolerancePct = halfDiffTolerancePct;
        this.tempSlopeTolerance = tempSlopeTolerance;
        this.maxCv = maxCv;
        this.powerHistory = new double[this.windowSize];
        this.tempHistory = new double[this.windowSize];
        this.loopTempHistory = new double[this.windowSize];
    }

    public void addSample(double power, double coreAvgTemp) {
        addSample(power, coreAvgTemp, 0.0);
    }

    public void addSample(double power, double coreAvgTemp, double loopTemp) {
        powerHistory[head] = power;
        tempHistory[head] = coreAvgTemp;
        loopTempHistory[head] = loopTemp;
        head = (head + 1) % windowSize;
        if (count < windowSize) {
            count++;
        }
    }

    public boolean isSteadyState() {
        if (count < windowSize) {
            return false;
        }

        int n = windowSize;
        double sumP = 0.0;
        double sumT = 0.0;
        double[] p = new double[n];
        double[] tArr = new double[n];

        for (int i = 0; i < n; i++) {
            int idx = (head + i) % n;
            p[i] = powerHistory[idx];
            tArr[i] = tempHistory[idx];
            sumP += p[i];
            sumT += tArr[i];
        }

        double meanP = sumP / n;
        double meanT = sumT / n;

        if (meanP <= 1.0) {
            return meanT <= (NuclearSimulationEngine.AMBIENT_TEMP + 5.0);
        }

        // 1. Ordinary Least Squares (OLS) linear regression slope on Power
        double meanIdx = (n - 1) / 2.0;
        double ssTy = 0.0;
        double ssTt = (n * (n * (double) n - 1.0)) / 12.0;
        double ssDiff = 0.0;

        for (int i = 0; i < n; i++) {
            double dt = i - meanIdx;
            double dp = p[i] - meanP;
            ssTy += dt * dp;
            ssDiff += dp * dp;
        }

        double slopeP = ssTy / ssTt;
        double totalDriftP = slopeP * n;
        double slopeRelP = Math.abs(totalDriftP) / meanP;

        // If visibly ramping up or drifting, slopeRelP will be high
        if (slopeRelP > slopeTolerancePct) {
            return false;
        }

        // 2. Sub-window halves comparison (first half vs second half)
        int half = n / 2;
        double sumFirstHalf = 0.0;
        double sumSecondHalf = 0.0;
        for (int i = 0; i < half; i++) {
            sumFirstHalf += p[i];
        }
        for (int i = half; i < n; i++) {
            sumSecondHalf += p[i];
        }
        double meanFirstHalf = sumFirstHalf / half;
        double meanSecondHalf = sumSecondHalf / (n - half);
        double halfShiftPct = Math.abs(meanSecondHalf - meanFirstHalf) / meanP;

        if (halfShiftPct > halfDiffTolerancePct) {
            return false;
        }

        // 3. Coefficient of Variation (CV) check on power oscillation
        double stdDevP = Math.sqrt(ssDiff / n);
        double cvP = stdDevP / meanP;
        if (cvP > maxCv) {
            return false;
        }

        // 4. Core Average Temperature stability check
        double ssTyT = 0.0;
        for (int i = 0; i < n; i++) {
            ssTyT += (i - meanIdx) * (tArr[i] - meanT);
        }
        double slopeT = ssTyT / ssTt;
        double totalDriftT = slopeT * n;
        double relDriftT = Math.abs(totalDriftT) / Math.max(10.0, meanT);

        if (Math.abs(totalDriftT) > 3.0 && relDriftT > tempSlopeTolerance) {
            return false; // Still thermally heating up
        }

        // 5. Convective Coolant Loop Stability & Phase Transition Check
        double sumLoopT = 0.0;
        double[] ltArr = new double[n];
        for (int i = 0; i < n; i++) {
            int idx = (head + i) % n;
            ltArr[i] = loopTempHistory[idx];
            sumLoopT += ltArr[i];
        }
        double meanLoopT = sumLoopT / n;

        // Only evaluate if coolant loop is thermally active (> ambient + 5°C)
        if (meanLoopT > (NuclearSimulationEngine.AMBIENT_TEMP + 5.0)) {
            double ssTyLt = 0.0;
            for (int i = 0; i < n; i++) {
                ssTyLt += (i - meanIdx) * (ltArr[i] - meanLoopT);
            }
            double slopeLt = ssTyLt / ssTt;
            double totalDriftLt = slopeLt * n;
            double relDriftLt = Math.abs(totalDriftLt) / meanLoopT;

            // A. Thermal drift check: loop temperature must not be actively drifting across the window
            if (Math.abs(totalDriftLt) > 2.0 && relDriftLt > tempSlopeTolerance) {
                return false; // Coolant loop is still warming up or cooling down
            }

            // B. Phase Transition Boundary Proximity Check:
            // If coolant loop is heating up (slopeLt > 0.005 °C/tick), prevent early exit
            // when in the approach band for a major steam phase change:
            // 100°C: Sub-boiling -> Secondary Steam generation (1:160 expansion)
            // 300°C: Regular Steam -> Superheated Steam (3x power multiplier in XLST-HP)
            // 700°C: Superheated Steam -> Supercritical Steam (5x power multiplier in XLST-SC)
            double lastLoopT = ltArr[n - 1];
            if (slopeLt > 0.005) {
                if (lastLoopT >= 85.0 && lastLoopT < 105.0) {
                    return false;
                }
                if (lastLoopT >= 260.0 && lastLoopT < 310.0) {
                    return false;
                }
                if (lastLoopT >= 640.0 && lastLoopT < 715.0) {
                    return false;
                }
            }
        }

        return true;
    }
}
