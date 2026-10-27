package com.gtnewhorizons.modularnuclear.common.coolantloop;

import com.gtnewhorizons.modularnuclear.common.config.data.CoolantDeviceData.PHEData;
import com.gtnewhorizons.modularnuclear.common.config.data.CoolantDeviceData.PHEData.SecondaryCoolantEntry;

/**
 * Heat exchange in a pressurized heat exchanger (PHE): the loop coolant boils a secondary fluid (steam or a
 * cryogenic gas) in batches. Shared by the game's PHE multiblock and the standalone simulator; the callers own power,
 * maintenance and fluid input/output.
 */
public final class PheExchange {

    /** Heat accumulated over the current batch. */
    public static final class Batch {

        public int timer;
        public double heatJoules;
        public double weightedTempSum;
        public SecondaryCoolantEntry coolant;
        /** Heat moved in the most recent tick. */
        public double lastTickHeatJoules;

        public void reset() {
            timer = 0;
            heatJoules = 0.0;
            weightedTempSum = 0.0;
            coolant = null;
        }

        /** Heat-weighted mean coolant temperature over the batch, or fallback if no heat moved. */
        public double effectiveTemperature(double fallback) {
            return heatJoules > 0 ? weightedTempSum / heatJoules : fallback;
        }
    }

    private PheExchange() {}

    /** Most heat one batch can move: the configured base, scaled by the PHE's voltage relative to LV (32 EU/t). */
    public static double maxBatchHeatJoules(PHEData cfg, long voltage) {
        return cfg.baseMaxHeatPerBatchJoules * (voltage / 32.0);
    }

    /**
     * Advances the batch by one tick (0.05 s), moving heat from the coolant in the PHE's loop segment into the
     * secondary fluid. Heat moves only while coolant flows and stays at least approachMinDeltaT above the secondary
     * fluid's boiling point; it is limited by convection across the exchange area, by the sensible heat available
     * above that approach temperature, by the per-tick and per-batch caps, and by the secondary fluid available.
     * Returns the coolant temperature after the tick (also written to the segment).
     *
     * @param secondaryAvailableMl secondary fluid available to boil this batch, in mL
     */
    public static double tick(Batch b, PHEData cfg, SecondaryCoolantEntry coolant, double secondaryAvailableMl,
        double flowM3s, LoopSegment segment, CoolantFluidProperty loopFluid, double coolantTemp, double exchangeArea,
        ConvectiveHeatTransferModel model, double effFactor, double maxBatchHeatJoules) {
        final double dt = 0.05;
        b.timer++;
        b.lastTickHeatJoules = 0.0;
        boolean hasFlow = segment != null && Math.abs(flowM3s) > 1e-9;
        double minTransferTemp = coolant != null ? coolant.boilingPointCelsius + cfg.approachMinDeltaT
            : Double.POSITIVE_INFINITY;
        if (!hasFlow || coolant == null || coolantTemp <= minTransferTemp || secondaryAvailableMl <= 0) {
            return coolantTemp;
        }
        double velocity = segment.getArea() > 0 ? Math.abs(flowM3s) / segment.getArea() : 0.0;
        if (velocity <= 0.0) return coolantTemp;

        double qWatts = model.computeHeatTransferRate(velocity, exchangeArea, coolantTemp, coolant.boilingPointCelsius)
            * effFactor;
        double effectiveVolume = Math.max(segment.getVolume(), Math.abs(flowM3s) * dt);
        double density = loopFluid != null ? loopFluid.getDensity() : 1000.0;
        double specificHeat = loopFluid != null ? loopFluid.getSpecificHeat() : 4184.0;
        double fluidHeatCapacity = effectiveVolume * density * specificHeat;
        double maxSensible = fluidHeatCapacity > 0 ? fluidHeatCapacity * Math.max(0.0, coolantTemp - minTransferTemp)
            : 0.0;

        int batchTicks = Math.max(1, cfg.batchTicks);
        double latentPerMl = coolant.latentHeatJoulePerLiter / 1000.0;
        double heat = Math.min(qWatts * dt, maxSensible);
        heat = Math.min(heat, maxBatchHeatJoules / batchTicks);
        heat = Math.min(heat, Math.max(0.0, maxBatchHeatJoules - b.heatJoules));
        heat = Math
            .min(heat, Math.max(0.0, (latentPerMl > 0 ? secondaryAvailableMl * latentPerMl : 0.0) - b.heatJoules));
        if (heat <= 0.0) return coolantTemp;

        double newTemp = coolantTemp;
        if (fluidHeatCapacity > 0) {
            newTemp = Math.max(minTransferTemp, coolantTemp - heat / fluidHeatCapacity);
            segment.setCurrentTemperatureCelsius(newTemp);
        }
        b.lastTickHeatJoules = heat;
        b.heatJoules += heat;
        b.weightedTempSum += newTemp * heat;
        b.coolant = coolant;
        return newTemp;
    }

    /** True once the batch has run its configured number of ticks. */
    public static boolean batchComplete(Batch b, PHEData cfg) {
        return b.timer >= Math.max(1, cfg.batchTicks);
    }

    /** Millilitres of secondary fluid the batch's heat boils. */
    public static int fluidToBoilMl(double heatJoules, SecondaryCoolantEntry coolant) {
        double latentPerMl = coolant.latentHeatJoulePerLiter / 1000.0;
        return latentPerMl > 0 ? (int) (heatJoules / latentPerMl) : 0;
    }

    /** Steam grade produced at a heat-weighted coolant temperature: supercritical, superheated or plain steam. */
    public static String steamGrade(double effectiveTemp, PHEData cfg) {
        if (effectiveTemp >= cfg.supercriticalTempThreshold) return "supercriticalsteam";
        if (effectiveTemp >= cfg.superheatedTempThreshold) return "ic2superheatedsteam";
        return "steam";
    }
}
