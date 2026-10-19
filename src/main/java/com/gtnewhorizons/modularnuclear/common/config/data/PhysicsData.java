package com.gtnewhorizons.modularnuclear.common.config.data;

import java.util.LinkedHashMap;
import java.util.Map;

import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;

public class PhysicsData {

    public double huPerDegree = 32.0;
    public double globalThermalFissionMultiplier = 1.0;
    public double globalFastFissionMultiplier = 1.0;
    public double fastAbsorptionDamageMultiplier = 8.0;
    public double thermalAbsorptionDamageMultiplier = 1.0;
    public double fissionHeatPerNeutron = NuclearSimulationEngine.DEFAULT_FISSION_HEAT_PER_NEUTRON;
    public double heatPerFastNeutronAbsorption = 10.0;
    public double heatPerThermalNeutronAbsorption = 25.0;
    public double gridHeatConduction = 0.15;
    public double boundaryHeatLoss = 0.02;
    public double radiationDecayFactor = 0.95;
    public double dopplerDeficitExponent = 1.2;
    public double wallReflectionChance = 0.0;
    public double wallAbsorbHeatPerNeutron = 1.0;
    public int wallMaintenanceNeutronThreshold = 10000;
    public double defaultAmbientTemp = 293.15;
    public double beamEnergyToFastNeutronMultiplier = 0.001;
    public double t10WearFactor = 8.0;

    // Fuel Archetype / Class Multipliers (Global Calibration Parameters)
    public double denseNbaseMultiplier = 0.50;      // -50% base neutrons (subcritical/mostly-inert)
    public double denseFissionMultiplier = 1.20;    // +20% thermal fission multiplication (high reaction potential)
    public double excitedNbaseMultiplier = 1.40;    // +40% base neutrons (driver / primary neutron source)
    public double excitedFissionMultiplier = 0.80;  // -20% thermal fission multiplication (already excited)
    public double standardNbaseMultiplier = 1.00;
    public double standardFissionMultiplier = 1.00;
    public double breederNbaseMultiplier = 1.00;
    public double breederFissionMultiplier = 1.00;

    public double getArchetypeNbaseMultiplier(String archetype) {
        if (archetype == null) return standardNbaseMultiplier;
        return switch (archetype.toLowerCase(java.util.Locale.ROOT)) {
            case "dense", "subcritical", "naquadah" -> denseNbaseMultiplier;
            case "excited", "driver" -> excitedNbaseMultiplier;
            case "breeder" -> breederNbaseMultiplier;
            default -> standardNbaseMultiplier;
        };
    }

    public double getArchetypeFissionMultiplier(String archetype) {
        if (archetype == null) return standardFissionMultiplier;
        return switch (archetype.toLowerCase(java.util.Locale.ROOT)) {
            case "dense", "subcritical", "naquadah" -> denseFissionMultiplier;
            case "excited", "driver" -> excitedFissionMultiplier;
            case "breeder" -> breederFissionMultiplier;
            default -> standardFissionMultiplier;
        };
    }

    public static PhysicsData fromMap(Map<String, Object> map) {
        PhysicsData d = new PhysicsData();
        if (map == null) return d;
        d.huPerDegree = getDouble(map, "hu_per_degree", d.huPerDegree);
        d.globalThermalFissionMultiplier = getDouble(map, "global_thermal_fission_multiplier", d.globalThermalFissionMultiplier);
        d.globalFastFissionMultiplier = getDouble(map, "global_fast_fission_multiplier", d.globalFastFissionMultiplier);
        d.fastAbsorptionDamageMultiplier = getDouble(map, "fast_absorption_damage_multiplier", d.fastAbsorptionDamageMultiplier);
        d.thermalAbsorptionDamageMultiplier = getDouble(map, "thermal_absorption_damage_multiplier", d.thermalAbsorptionDamageMultiplier);
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
        d.beamEnergyToFastNeutronMultiplier = getDouble(map, "beam_energy_to_fast_neutron_multiplier", d.beamEnergyToFastNeutronMultiplier);
        d.t10WearFactor = getDouble(map, "t10_wear_factor", d.t10WearFactor);

        d.denseNbaseMultiplier = getDouble(map, "dense_nbase_multiplier", d.denseNbaseMultiplier);
        d.denseFissionMultiplier = getDouble(map, "dense_fission_multiplier", d.denseFissionMultiplier);
        d.excitedNbaseMultiplier = getDouble(map, "excited_nbase_multiplier", d.excitedNbaseMultiplier);
        d.excitedFissionMultiplier = getDouble(map, "excited_fission_multiplier", d.excitedFissionMultiplier);
        d.standardNbaseMultiplier = getDouble(map, "standard_nbase_multiplier", d.standardNbaseMultiplier);
        d.standardFissionMultiplier = getDouble(map, "standard_fission_multiplier", d.standardFissionMultiplier);
        d.breederNbaseMultiplier = getDouble(map, "breeder_nbase_multiplier", d.breederNbaseMultiplier);
        d.breederFissionMultiplier = getDouble(map, "breeder_fission_multiplier", d.breederFissionMultiplier);
        return d;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hu_per_degree", huPerDegree);
        m.put("global_thermal_fission_multiplier", globalThermalFissionMultiplier);
        m.put("global_fast_fission_multiplier", globalFastFissionMultiplier);
        m.put("fast_absorption_damage_multiplier", fastAbsorptionDamageMultiplier);
        m.put("thermal_absorption_damage_multiplier", thermalAbsorptionDamageMultiplier);
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
        m.put("beam_energy_to_fast_neutron_multiplier", beamEnergyToFastNeutronMultiplier);
        m.put("t10_wear_factor", t10WearFactor);

        m.put("dense_nbase_multiplier", denseNbaseMultiplier);
        m.put("dense_fission_multiplier", denseFissionMultiplier);
        m.put("excited_nbase_multiplier", excitedNbaseMultiplier);
        m.put("excited_fission_multiplier", excitedFissionMultiplier);
        m.put("standard_nbase_multiplier", standardNbaseMultiplier);
        m.put("standard_fission_multiplier", standardFissionMultiplier);
        m.put("breeder_nbase_multiplier", breederNbaseMultiplier);
        m.put("breeder_fission_multiplier", breederFissionMultiplier);
        return m;
    }

    public static double getDouble(Map<String, Object> map, String key, double def) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.doubleValue();
        if (val instanceof String s) {
            try { return Double.parseDouble(s); } catch (Exception ignored) {}
        }
        return def;
    }

    public static int getInt(Map<String, Object> map, String key, int def) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (Exception ignored) {}
        }
        return def;
    }

    public static String getString(Map<String, Object> map, String key, String def) {
        Object val = map.get(key);
        if (val instanceof String s) return s;
        if (val != null) return String.valueOf(val);
        return def;
    }
}
