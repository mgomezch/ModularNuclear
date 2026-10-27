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
    public double gridConductionMultiplier = NuclearSimulationEngine.DEFAULT_GRID_CONDUCTION_MULTIPLIER;
    public double fuelBurnupMultiplier = NuclearSimulationEngine.DEFAULT_FUEL_BURNUP_MULTIPLIER;
    /** Scales every fuel's base neutron emission, on top of its archetype's multiplier. */
    public double neutronYieldMultiplier = NuclearSimulationEngine.DEFAULT_NEUTRON_YIELD_MULT;
    public double coolantPassageHeatTransferMultiplier = NuclearSimulationEngine.DEFAULT_COOLANT_PASSAGE_HEAT_TRANSFER_MULT;
    public double boundaryHeatLoss = 0.02;
    public double radiationDecayFactor = 0.95;
    public double dopplerDeficitExponent = 1.2;
    public double wallReflectionChance = 0.0;
    public int wallMaintenanceNeutronThreshold = 10000;
    public double defaultAmbientTemp = 293.15;
    public double beamEnergyToFastNeutronMultiplier = 0.001;
    public double t10WearFactor = 8.0;
    public int ticksPerCycle = NuclearSimulationEngine.DEFAULT_TICKS_PER_CYCLE;
    /** Conductance of a core hatch cell to its neighbours, before the tier's multiplier (EU/(t·°C)). */
    public double baseHatchConductance = NuclearSimulationEngine.DEFAULT_BASE_HATCH_CONDUCTANCE;
    public double hatchConductanceTierRatio = NuclearSimulationEngine.DEFAULT_HATCH_CONDUCTANCE_TIER_RATIO;
    /** Heat capacity of a tier-0 core hatch (HU/°C); a tier-t hatch holds 2^t times this, as its tank does. */
    public double coreHatchHeatCapacity = NuclearSimulationEngine.DEFAULT_CORE_HATCH_HEAT_CAPACITY;

    // Coolant passage (high-pressure hatch pair) as a core cell
    /** Conduction coefficient of the passage cell (metal pressure tube). */
    public double coolantPassageConduction = 0.90;
    public double coolantPassageScattering = 0.80;
    public double coolantPassageFastAbsorption = 0.02;
    public double coolantPassageThermalAbsorption = 0.05;
    /** Moderation when the passage is on no loop, or its loop fluid has no coolant entry. */
    public double coolantPassageDefaultModeration = 0.05;
    /** Neutrons a passage absorbs per radiolysis event (2 L of coolant to 2 L of gas and 1 L of oxygen). */
    public int coolantPassageNeutronsPerRadiolysis = 4;
    /** Minor loss coefficient of the passage in its loop (pressure tube with fuel bundle spacers). */
    public double coolantPassageMinorLossK = 2.5;

    // Fuel Archetype / Class Multipliers (Global Calibration Parameters)
    public double denseNbaseMultiplier = 0.50; // -50% base neutrons (subcritical/mostly-inert)
    public double denseFissionMultiplier = 1.20; // +20% thermal fission multiplication (high reaction potential)
    public double excitedNbaseMultiplier = 2.00; // 2x base neutrons (driver / primary neutron source)
    public double excitedFissionMultiplier = 0.70; // -30% fission multiplication (already excited)
    public double standardNbaseMultiplier = 1.00;
    public double standardFissionMultiplier = 1.00;
    public double breederNbaseMultiplier = 1.00;
    public double breederFissionMultiplier = 1.00;

    /** Base neutron multiplier of a fuel archetype, including the global neutron yield multiplier. */
    public double getArchetypeNbaseMultiplier(String archetype) {
        double mult = archetype == null ? standardNbaseMultiplier
            : switch (archetype.toLowerCase(java.util.Locale.ROOT)) {
            case "dense", "subcritical", "naquadah" -> denseNbaseMultiplier;
            case "excited", "driver" -> excitedNbaseMultiplier;
            case "breeder" -> breederNbaseMultiplier;
            default -> standardNbaseMultiplier;
            };
        return mult * neutronYieldMultiplier;
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
        d.globalThermalFissionMultiplier = getDouble(
            map,
            "global_thermal_fission_multiplier",
            d.globalThermalFissionMultiplier);
        d.globalFastFissionMultiplier = getDouble(map, "global_fast_fission_multiplier", d.globalFastFissionMultiplier);
        d.fastAbsorptionDamageMultiplier = getDouble(
            map,
            "fast_absorption_damage_multiplier",
            d.fastAbsorptionDamageMultiplier);
        d.thermalAbsorptionDamageMultiplier = getDouble(
            map,
            "thermal_absorption_damage_multiplier",
            d.thermalAbsorptionDamageMultiplier);
        d.fissionHeatPerNeutron = getDouble(map, "fission_heat_per_neutron", d.fissionHeatPerNeutron);
        d.gridConductionMultiplier = getDouble(map, "grid_conduction_multiplier", d.gridConductionMultiplier);
        d.fuelBurnupMultiplier = getDouble(map, "fuel_burnup_multiplier", d.fuelBurnupMultiplier);
        d.neutronYieldMultiplier = Math.max(0.0, getDouble(map, "neutron_yield_multiplier", d.neutronYieldMultiplier));
        d.coolantPassageHeatTransferMultiplier = getDouble(
            map,
            "coolant_passage_heat_transfer_multiplier",
            d.coolantPassageHeatTransferMultiplier);
        d.boundaryHeatLoss = getDouble(map, "boundary_heat_loss", d.boundaryHeatLoss);
        d.radiationDecayFactor = getDouble(map, "radiation_decay_factor", d.radiationDecayFactor);
        d.dopplerDeficitExponent = getDouble(map, "doppler_deficit_exponent", d.dopplerDeficitExponent);
        d.wallReflectionChance = getDouble(map, "wall_reflection_chance", d.wallReflectionChance);
        d.wallMaintenanceNeutronThreshold = getInt(
            map,
            "wall_maintenance_neutron_threshold",
            d.wallMaintenanceNeutronThreshold);
        d.defaultAmbientTemp = getDouble(map, "default_ambient_temp", d.defaultAmbientTemp);
        d.beamEnergyToFastNeutronMultiplier = getDouble(
            map,
            "beam_energy_to_fast_neutron_multiplier",
            d.beamEnergyToFastNeutronMultiplier);
        d.t10WearFactor = getDouble(map, "t10_wear_factor", d.t10WearFactor);
        d.ticksPerCycle = getInt(map, "ticks_per_cycle", getInt(map, "ticksPerCycle", d.ticksPerCycle));
        d.baseHatchConductance = getDouble(map, "base_hatch_conductance", d.baseHatchConductance);
        d.hatchConductanceTierRatio = getDouble(map, "hatch_conductance_tier_ratio", d.hatchConductanceTierRatio);
        d.coreHatchHeatCapacity = getDouble(map, "core_hatch_heat_capacity", d.coreHatchHeatCapacity);
        d.coolantPassageConduction = getDouble(map, "coolant_passage_conduction", d.coolantPassageConduction);
        d.coolantPassageScattering = getDouble(map, "coolant_passage_scattering", d.coolantPassageScattering);
        d.coolantPassageFastAbsorption = getDouble(
            map,
            "coolant_passage_fast_absorption",
            d.coolantPassageFastAbsorption);
        d.coolantPassageThermalAbsorption = getDouble(
            map,
            "coolant_passage_thermal_absorption",
            d.coolantPassageThermalAbsorption);
        d.coolantPassageDefaultModeration = getDouble(
            map,
            "coolant_passage_default_moderation",
            d.coolantPassageDefaultModeration);
        d.coolantPassageNeutronsPerRadiolysis = Math
            .max(1, getInt(map, "coolant_passage_neutrons_per_radiolysis", d.coolantPassageNeutronsPerRadiolysis));
        d.coolantPassageMinorLossK = getDouble(map, "coolant_passage_minor_loss_k", d.coolantPassageMinorLossK);

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
        m.put("ticks_per_cycle", ticksPerCycle);
        m.put("hu_per_degree", huPerDegree);
        m.put("global_thermal_fission_multiplier", globalThermalFissionMultiplier);
        m.put("global_fast_fission_multiplier", globalFastFissionMultiplier);
        m.put("fast_absorption_damage_multiplier", fastAbsorptionDamageMultiplier);
        m.put("thermal_absorption_damage_multiplier", thermalAbsorptionDamageMultiplier);
        m.put("fission_heat_per_neutron", fissionHeatPerNeutron);
        m.put("grid_conduction_multiplier", gridConductionMultiplier);
        m.put("fuel_burnup_multiplier", fuelBurnupMultiplier);
        m.put("neutron_yield_multiplier", neutronYieldMultiplier);
        m.put("coolant_passage_heat_transfer_multiplier", coolantPassageHeatTransferMultiplier);
        m.put("base_hatch_conductance", baseHatchConductance);
        m.put("hatch_conductance_tier_ratio", hatchConductanceTierRatio);
        m.put("core_hatch_heat_capacity", coreHatchHeatCapacity);
        m.put("coolant_passage_conduction", coolantPassageConduction);
        m.put("coolant_passage_scattering", coolantPassageScattering);
        m.put("coolant_passage_fast_absorption", coolantPassageFastAbsorption);
        m.put("coolant_passage_thermal_absorption", coolantPassageThermalAbsorption);
        m.put("coolant_passage_default_moderation", coolantPassageDefaultModeration);
        m.put("coolant_passage_neutrons_per_radiolysis", coolantPassageNeutronsPerRadiolysis);
        m.put("coolant_passage_minor_loss_k", coolantPassageMinorLossK);
        m.put("boundary_heat_loss", boundaryHeatLoss);
        m.put("radiation_decay_factor", radiationDecayFactor);
        m.put("doppler_deficit_exponent", dopplerDeficitExponent);
        m.put("wall_reflection_chance", wallReflectionChance);
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
            try {
                return Double.parseDouble(s);
            } catch (Exception ignored) {}
        }
        return def;
    }

    public static int getInt(Map<String, Object> map, String key, int def) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (Exception ignored) {}
        }
        return def;
    }

    public static String getString(Map<String, Object> map, String key, String def) {
        Object val = map.get(key);
        if (val instanceof String s) return s;
        if (val != null) return String.valueOf(val);
        return def;
    }

    public static boolean getBoolean(Map<String, Object> map, String key, boolean def) {
        Object val = map.get(key);
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return def;
    }
}
