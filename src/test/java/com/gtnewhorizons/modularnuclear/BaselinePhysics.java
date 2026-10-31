package com.gtnewhorizons.modularnuclear;

import java.util.LinkedHashMap;
import java.util.Map;

import com.gtnewhorizons.modularnuclear.common.config.CoolantLoopConfigData;
import com.gtnewhorizons.modularnuclear.common.config.ReactorConfigData;

/**
 * The physics the mechanics tests are written for: unit multipliers and the uncalibrated constants. physics.yaml and
 * devices.yaml ship the calibration's values (tools/apply_calibration.py), which change whenever the calibration runs
 * again; a test of a mechanism should not.
 */
final class BaselinePhysics {

    private BaselinePhysics() {}

    /** Applies the baseline on top of the loaded configuration; call it after resetting the engine. */
    static void apply() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fission_heat_per_neutron", 38.0);
        m.put("global_thermal_fission_multiplier", 1.0);
        m.put("grid_conduction_multiplier", 1.0);
        m.put("neutron_yield_multiplier", 1.0);
        m.put("fuel_burnup_multiplier", 1.0);
        m.put("coolant_passage_heat_transfer_multiplier", 0.11);
        m.put("standard_nbase_multiplier", 1.0);
        m.put("standard_fission_multiplier", 1.0);
        m.put("excited_nbase_multiplier", 2.0);
        m.put("excited_fission_multiplier", 0.7);
        ReactorConfigData.applyPhysicsOverrides(m);
        CoolantLoopConfigData.getDeviceData()
            .getPhe().heatTransferMultiplier = 1.0;
    }
}
