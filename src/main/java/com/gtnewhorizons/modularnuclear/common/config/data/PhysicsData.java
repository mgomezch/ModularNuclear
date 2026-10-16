package com.gtnewhorizons.modularnuclear.common.config.data;

import java.util.LinkedHashMap;
import java.util.Map;

public class PhysicsData {

    public double huPerDegree = 32.0;
    public double euPerDegree = 32.0; // Backwards-compatible alias
    public double globalThermalFissionMultiplier = 1.0;
    public double fastFissionMultiplier = 0.10;
    public double fissionHeatPerNeutron = 2.0;
    public double heatPerFastNeutronAbsorption = 10.0;
    public double heatPerThermalNeutronAbsorption = 25.0;
    public double gridHeatConduction = 0.15;
    public double boundaryHeatLoss = 0.02;
    public double radiationDecayFactor = 0.95;
    public double dopplerDeficitExponent = 1.2;
    public double wallReflectionChance = 0.50;
    public double wallAbsorbHeatPerNeutron = 1.0;
    public int wallMaintenanceNeutronThreshold = 10000;
    public double defaultAmbientTemp = 293.15;

    public static PhysicsData fromMap(Map<String, Object> map) {
        PhysicsData d = new PhysicsData();
        if (map == null) return d;
        d.huPerDegree = getDouble(map, "hu_per_degree", getDouble(map, "eu_per_degree", d.huPerDegree));
        d.euPerDegree = d.huPerDegree;
        d.globalThermalFissionMultiplier = getDouble(map, "global_thermal_fission_multiplier", d.globalThermalFissionMultiplier);
        d.fastFissionMultiplier = getDouble(map, "fast_fission_multiplier", d.fastFissionMultiplier);
        d.fissionHeatPerNeutron = getDouble(map, "fission_heat_per_neutron", d.fissionHeatPerNeutron);
        d.heatPerFastNeutronAbsorption = getDouble(map, "heat_per_fast_neutron_absorption", d.heatPerFastNeutronAbsorption);
        d.heatPerThermalNeutronAbsorption = getDouble(map, "heat_per_thermal_neutron_absorption", d.heatPerThermalNeutronAbsorption);
        d.gridHeatConduction = getDouble(map, "grid_heat_conduction", d.gridHeatConduction);
        d.boundaryHeatLoss = getDouble(map, "boundary_heat_loss", d.boundaryHeatLoss);
        d.radiationDecayFactor = getDouble(map, "radiation_decay_factor", d.radiationDecayFactor);
        d.dopplerDeficitExponent = getDouble(map, "doppler_deficit_exponent", d.dopplerDeficitExponent);
        d.wallReflectionChance = getDouble(map, "wall_reflection_chance", d.wallReflectionChance);
        d.wallAbsorbHeatPerNeutron = getDouble(map, "wall_absorb_heat_per_neutron", d.wallAbsorbHeatPerNeutron);
        d.wallMaintenanceNeutronThreshold = getInt(map, "wall_maintenance_neutron_threshold", d.wallMaintenanceNeutronThreshold);
        d.defaultAmbientTemp = getDouble(map, "default_ambient_temp", d.defaultAmbientTemp);
        return d;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hu_per_degree", huPerDegree);
        m.put("eu_per_degree", huPerDegree);
        m.put("global_thermal_fission_multiplier", globalThermalFissionMultiplier);
        m.put("fast_fission_multiplier", fastFissionMultiplier);
        m.put("fission_heat_per_neutron", fissionHeatPerNeutron);
        m.put("heat_per_fast_neutron_absorption", heatPerFastNeutronAbsorption);
        m.put("heat_per_thermal_neutron_absorption", heatPerThermalNeutronAbsorption);
        m.put("grid_heat_conduction", gridHeatConduction);
        m.put("boundary_heat_loss", boundaryHeatLoss);
        m.put("radiation_decay_factor", radiationDecayFactor);
        m.put("doppler_deficit_exponent", dopplerDeficitExponent);
        m.put("wall_reflection_chance", wallReflectionChance);
        m.put("wall_absorb_heat_per_neutron", wallAbsorbHeatPerNeutron);
        m.put("wall_maintenance_neutron_threshold", wallMaintenanceNeutronThreshold);
        m.put("default_ambient_temp", defaultAmbientTemp);
        return m;
    }

    static double getDouble(Map<String, Object> map, String key, double def) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.doubleValue();
        if (val instanceof String s) {
            try { return Double.parseDouble(s); } catch (Exception ignored) {}
        }
        return def;
    }

    static int getInt(Map<String, Object> map, String key, int def) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (Exception ignored) {}
        }
        return def;
    }
}
