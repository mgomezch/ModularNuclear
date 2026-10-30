package com.gtnewhorizons.modularnuclear.common.nuclear.standalone;

import com.gtnewhorizons.modularnuclear.common.config.ReactorConfigData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.CoolantFluidData;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;

/**
 * Simulator tuning knobs that correspond to values the game reads from its configuration. They write into the shared
 * configuration records, so the simulator keeps running the same code paths as the game.
 */
public final class StandaloneConfig {

    private StandaloneConfig() {}

    /** Heat absorbed per litre of distilled and heavy water boiled to steam. */
    public static void setWaterHeatPerLiter(double heatPerLiter) {
        NuclearSimulationEngine.params().coolingHeatPerLiter = heatPerLiter;
        setCoolantHeatPerLiter("coolant_distilled_water", heatPerLiter);
        setCoolantHeatPerLiter("coolant_heavy_water", heatPerLiter);
    }

    /** Heat absorbed per litre of IC2 coolant heated. */
    public static void setIc2CoolantHeatPerLiter(double heatPerLiter) {
        NuclearSimulationEngine.ic2CoolantHeatPerLiter = heatPerLiter;
        setCoolantHeatPerLiter("coolant_ic2", heatPerLiter);
    }

    private static void setCoolantHeatPerLiter(String id, double heatPerLiter) {
        CoolantFluidData cfd = ReactorConfigData
            .findById(ReactorConfigData.getComponentData().coolantFluids, d -> d.id, id);
        if (cfd != null) cfd.heatPerLiter = Math.max(0.1, heatPerLiter);
    }
}
