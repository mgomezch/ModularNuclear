package com.gtnewhorizons.modularnuclear.common.nuclear.standalone.cli;

import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.CoolantLoopModel;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.CoolantLoopModel.EnergyHatchTier;

/**
 * Coolant loop settings: comma-separated {@code key=value} pairs applied in order. {@code circuit=N} (1-based)
 * switches the following settings to that circuit (its passages are CPN in the layout), adding circuits as needed.
 */
public final class LoopSettings {

    /** The loop being configured, and the temperature bands read so far for the current circuit. */
    private static final class Target {

        final CoolantLoopModel loop;
        double bandMin = Double.NaN;
        double bandMax = Double.NaN;
        double pheBandMin = Double.NaN;
        double pheBandMax = Double.NaN;

        Target(CoolantLoopModel loop) {
            this.loop = loop;
        }

        CoolantLoopModel.Circuit circuit() {
            return loop.getSelectedCircuit();
        }

        /** Applies the bands read for the current circuit, if any. */
        void flushBand() {
            if (!Double.isNaN(bandMin) || !Double.isNaN(bandMax)) loop.setTemperatureBand(bandMin, bandMax);
            if (!Double.isNaN(pheBandMin) || !Double.isNaN(pheBandMax)) {
                loop.setPheTemperatureBand(pheBandMin, pheBandMax);
            }
            bandMin = bandMax = pheBandMin = pheBandMax = Double.NaN;
        }
    }

    /** Every loop setting; the names are also accepted as run options (see {@link SimOptions}). */
    static final OptionTable<Target> TABLE = new OptionTable<Target>().value(OptionTable.INT, (t, n) -> {
        t.flushBand();
        t.loop.selectCircuit(n - 1);
    }, "circuit")
        .value(
            s -> CoolantLoopModel.LoopMaterial.fromString(s),
            (t, v) -> t.loop.setMaterial(v),
            "loop_mat",
            "loop_material")
        .value(s -> CoolantLoopModel.LoopPipeSize.fromString(s), (t, v) -> t.loop.setPipeSize(v), "loop_size")
        .value(s -> CoolantLoopModel.CoolantFluidType.fromString(s), (t, v) -> t.loop.setFluidType(v), "loop_fluid")
        .value(OptionTable.DOUBLE, (t, v) -> setPumpPower(t.loop, v), "loop_power", "loop_pump_power")
        .value(OptionTable.DOUBLE, (t, v) -> t.loop.setDutyCyclePercent(v), "loop_duty")
        .value(OptionTable.DOUBLE, (t, v) -> t.loop.setMaxFlowRateLPerSec(v), "loop_max_flow", "loop_flow")
        .value(OptionTable.DOUBLE, (t, v) -> t.loop.setMaxPressureBar(v), "loop_max_pressure")
        .value(OptionTable.DOUBLE, (t, v) -> t.bandMin = v, "loop_temp_min")
        .value(OptionTable.DOUBLE, (t, v) -> t.bandMax = v, "loop_temp_max")
        .value(
            OptionTable.DOUBLE,
            (t, v) -> t.circuit()
                .setFillTempOverrideC(v),
            "loop_fill_temp")
        .value(
            s -> CoolantLoopModel.TempSensor.fromString(s),
            (t, v) -> t.circuit()
                .setSensor(v),
            "loop_sensor")
        .value(OptionTable.DOUBLE, (t, v) -> t.pheBandMin = v, "phe_temp_min")
        .value(OptionTable.DOUBLE, (t, v) -> t.pheBandMax = v, "phe_temp_max")
        .value(
            s -> CoolantLoopModel.TempSensor.fromString(s),
            (t, v) -> t.circuit()
                .setPheSensor(v),
            "phe_sensor")
        .value(s -> EnergyHatchTier.fromString(s), (t, v) -> t.loop.setHatchTier(v), "loop_hatch")
        .value(OptionTable.INT, (t, v) -> t.loop.setPumpHatchCount(v), "loop_hatches")
        .value(OptionTable.STRING, (t, v) -> t.loop.setRotorMaterial(v), "loop_rotor")
        .value(s -> CoolantLoopModel.RotorSize.fromString(s), (t, v) -> t.loop.setRotorSize(v), "loop_rotor_size")
        .value(OptionTable.STRING, (t, v) -> t.loop.setSecondaryCoolant(v), "loop_secondary")
        .value(OptionTable.INT, (t, v) -> t.loop.setPheCount(v), "phe_count")
        .value(OptionTable.INT, (t, v) -> t.loop.setLoopCount(v), "loop_count")
        .value(s -> EnergyHatchTier.fromString(s), (t, v) -> t.loop.setPheHatchTier(v), "phe_hatch")
        .value(OptionTable.INT, (t, v) -> t.loop.setPheHatchCount(v), "phe_hatches")
        .onUnknown(name -> System.err.println("WARNING: unknown loop option " + name));

    private LoopSettings() {}

    /** Whether a run option is a loop setting. */
    static boolean isLoopSetting(String name) {
        return TABLE.has(name);
    }

    /** Picks the pump's energy hatches whose draw is the least at or above a power, preferring a single hatch. */
    static void setPumpPower(CoolantLoopModel loop, double powerEUt) {
        EnergyHatchTier bestTier = EnergyHatchTier.UHV;
        int bestCount = 2;
        double bestDraw = Double.MAX_VALUE;
        for (EnergyHatchTier tier : EnergyHatchTier.values()) {
            for (int count = 1; count <= 2; count++) {
                double draw = count == 1 ? tier.voltageEU : 4.0 * tier.voltageEU;
                if (draw >= powerEUt && draw < bestDraw) {
                    bestDraw = draw;
                    bestTier = tier;
                    bestCount = count;
                }
            }
        }
        loop.setHatchTier(bestTier);
        loop.setPumpHatchCount(bestCount);
    }

    /**
     * Applies loop settings (from circuit 1), then attaches the given core cells ("x,y;x,y", or x:y) to circuit 1 in
     * place of its passages.
     */
    static void configure(CoolantLoopModel loop, String settings, String points) {
        if (loop == null) return;
        loop.selectCircuit(0);
        Target target = new Target(loop);
        TABLE.parseTokens(target, settings, ",");
        target.flushBand();
        loop.selectCircuit(0);
        if (points == null || points.trim()
            .isEmpty()) return;
        loop.clearAttachedPoints();
        for (String pair : points.split("[;\\s]+")) {
            String[] xy = pair.trim()
                .split("[,:]");
            if (xy.length != 2) continue;
            try {
                loop.attachPoint(Integer.parseInt(xy[0].trim()), Integer.parseInt(xy[1].trim()));
            } catch (NumberFormatException ignored) {}
        }
    }
}
