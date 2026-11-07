package com.gtnewhorizons.modularnuclear.common.config.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data-driven configuration container for coolant loop piping, burst ratings, and material limits.
 */
public class CoolantPipeData {

    private Map<String, Double> materialPressureRatingsBar = new LinkedHashMap<>();
    private Map<String, Double> materialTemperatureLimitsCelsius = new LinkedHashMap<>();
    private Map<String, Double> thicknessDiameters = new LinkedHashMap<>();
    private List<String> allowedMaterials = new ArrayList<>();
    /** Railcraft tank material a pump needs for loops of each pipe material that has no tank of its own. */
    private Map<String, String> tankMaterials = new LinkedHashMap<>();
    private double defaultRoughness = 0.000045; // Commercial metal pipe roughness in meters
    /** Multiplier on a pipe block's pressure drop by its material; 1 for materials not listed. */
    private Map<String, Double> materialFrictionMultipliers = new LinkedHashMap<>();

    public CoolantPipeData() {
        populateDefaults();
    }

    private void populateDefaults() {
        // Default burst pressure ratings (bar) strictly for allowed coolant pipe materials
        materialPressureRatingsBar.put("Iron", 16.0);
        materialPressureRatingsBar.put("Steel", 35.0);
        materialPressureRatingsBar.put("StainlessSteel", 70.0);
        materialPressureRatingsBar.put("Titanium", 140.0);
        materialPressureRatingsBar.put("TungstenSteel", 280.0);
        materialPressureRatingsBar.put("Osmium", 600.0);
        materialPressureRatingsBar.put("Neutronium", 3000.0);
        materialPressureRatingsBar.put("DraconiumAwakened", 6000.0);
        materialPressureRatingsBar.put("Infinity", 12000.0);

        // Explicit temperature limits (deg C) overrides strictly for allowed coolant pipe materials
        materialTemperatureLimitsCelsius.put("Iron", 1538.0);
        materialTemperatureLimitsCelsius.put("Steel", 1500.0);
        materialTemperatureLimitsCelsius.put("StainlessSteel", 1700.0);
        materialTemperatureLimitsCelsius.put("Titanium", 1668.0);
        materialTemperatureLimitsCelsius.put("TungstenSteel", 3000.0);
        materialTemperatureLimitsCelsius.put("Osmium", 3033.0);
        materialTemperatureLimitsCelsius.put("Neutronium", 10000.0);
        materialTemperatureLimitsCelsius.put("DraconiumAwakened", 10000.0);
        materialTemperatureLimitsCelsius.put("Infinity", 10000.0);

        // Better pipes lose less pressure per block, so a high-tier loop can be laid out further from its reactor
        materialFrictionMultipliers.put("Iron", 0.5);
        materialFrictionMultipliers.put("Steel", 0.4);
        materialFrictionMultipliers.put("StainlessSteel", 0.3);
        materialFrictionMultipliers.put("Titanium", 0.25);
        materialFrictionMultipliers.put("TungstenSteel", 0.2);
        materialFrictionMultipliers.put("Osmium", 0.15);
        materialFrictionMultipliers.put("Neutronium", 0.1);
        materialFrictionMultipliers.put("DraconiumAwakened", 0.07);
        materialFrictionMultipliers.put("Infinity", 0.05);

        // Pipe thickness to flow diameter mappings (meters)
        thicknessDiameters.put("tiny", 0.05);
        thicknessDiameters.put("small", 0.075);
        thicknessDiameters.put("normal", 0.10);
        thicknessDiameters.put("large", 0.15);
        thicknessDiameters.put("huge", 0.20);

        // Materials allowed in closed loops (must match Railcraft tank availability)
        allowedMaterials.add("Iron");
        allowedMaterials.add("Steel");
        allowedMaterials.add("StainlessSteel");
        allowedMaterials.add("Titanium");
        allowedMaterials.add("TungstenSteel");
        allowedMaterials.add("Osmium");
        allowedMaterials.add("Neutronium");
        allowedMaterials.add("DraconiumAwakened");
        allowedMaterials.add("Infinity");

        // Railcraft has no awakened draconium or infinity tanks: their loops' pumps take neutronium tanks
        tankMaterials.put("DraconiumAwakened", "Neutronium");
        tankMaterials.put("Infinity", "Neutronium");
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("materialPressureRatingsBar", materialPressureRatingsBar);
        map.put("materialTemperatureLimitsCelsius", materialTemperatureLimitsCelsius);
        map.put("thicknessDiameters", thicknessDiameters);
        map.put("allowedMaterials", allowedMaterials);
        map.put("tankMaterials", tankMaterials);
        map.put("defaultRoughness", defaultRoughness);
        map.put("materialFrictionMultipliers", materialFrictionMultipliers);
        return map;
    }

    @SuppressWarnings("unchecked")
    public static CoolantPipeData fromMap(Map<String, Object> map) {
        CoolantPipeData data = new CoolantPipeData();
        if (map == null) return data;

        if (map.containsKey("materialPressureRatingsBar") && map.get("materialPressureRatingsBar") instanceof Map) {
            data.materialPressureRatingsBar.clear();
            Map<String, Object> pMap = (Map<String, Object>) map.get("materialPressureRatingsBar");
            for (Map.Entry<String, Object> e : pMap.entrySet()) {
                if (e.getValue() instanceof Number) {
                    data.materialPressureRatingsBar.put(e.getKey(), ((Number) e.getValue()).doubleValue());
                }
            }
        }

        if (map.containsKey("materialTemperatureLimitsCelsius")
            && map.get("materialTemperatureLimitsCelsius") instanceof Map) {
            data.materialTemperatureLimitsCelsius.clear();
            Map<String, Object> tMap = (Map<String, Object>) map.get("materialTemperatureLimitsCelsius");
            for (Map.Entry<String, Object> e : tMap.entrySet()) {
                if (e.getValue() instanceof Number) {
                    data.materialTemperatureLimitsCelsius.put(e.getKey(), ((Number) e.getValue()).doubleValue());
                }
            }
        }

        if (map.containsKey("thicknessDiameters") && map.get("thicknessDiameters") instanceof Map) {
            data.thicknessDiameters.clear();
            Map<String, Object> dMap = (Map<String, Object>) map.get("thicknessDiameters");
            for (Map.Entry<String, Object> e : dMap.entrySet()) {
                if (e.getValue() instanceof Number) {
                    data.thicknessDiameters.put(e.getKey(), ((Number) e.getValue()).doubleValue());
                }
            }
        }

        if (map.containsKey("allowedMaterials") && map.get("allowedMaterials") instanceof List) {
            data.allowedMaterials.clear();
            for (Object o : (List<?>) map.get("allowedMaterials")) {
                if (o != null) data.allowedMaterials.add(o.toString());
            }
        }

        if (map.containsKey("tankMaterials") && map.get("tankMaterials") instanceof Map) {
            data.tankMaterials.clear();
            Map<String, Object> tMap = (Map<String, Object>) map.get("tankMaterials");
            for (Map.Entry<String, Object> e : tMap.entrySet()) {
                if (e.getValue() != null) data.tankMaterials.put(
                    e.getKey(),
                    e.getValue()
                        .toString());
            }
        }

        if (map.containsKey("materialFrictionMultipliers") && map.get("materialFrictionMultipliers") instanceof Map) {
            data.materialFrictionMultipliers.clear();
            Map<String, Object> fMap = (Map<String, Object>) map.get("materialFrictionMultipliers");
            for (Map.Entry<String, Object> e : fMap.entrySet()) {
                if (e.getValue() instanceof Number) {
                    data.materialFrictionMultipliers
                        .put(e.getKey(), Math.max(0.0, ((Number) e.getValue()).doubleValue()));
                }
            }
        }

        if (map.containsKey("defaultRoughness")) {
            data.defaultRoughness = ((Number) map.get("defaultRoughness")).doubleValue();
        }

        return data;
    }

    public boolean areMaterialsEqual(String m1, String m2) {
        return m1 != null && m1.equals(m2);
    }

    /** Whether a pump with a tank of tankMaterial can drive a loop of pipeMaterial pipes. */
    public boolean takesTank(String pipeMaterial, String tankMaterial) {
        if (pipeMaterial == null || tankMaterial == null) return false;
        String tank = tankMaterials.get(pipeMaterial);
        return areMaterialsEqual(tank != null ? tank : pipeMaterial, tankMaterial);
    }

    public Map<String, Double> getMaterialPressureRatingsBar() {
        return materialPressureRatingsBar;
    }

    public Map<String, Double> getMaterialTemperatureLimitsCelsius() {
        return materialTemperatureLimitsCelsius;
    }

    public Map<String, Double> getThicknessDiameters() {
        return thicknessDiameters;
    }

    public List<String> getAllowedMaterials() {
        return allowedMaterials;
    }

    public Map<String, Double> getMaterialFrictionMultipliers() {
        return materialFrictionMultipliers;
    }

    /** The pressure drop multiplier of a pipe material's blocks; 1 for a material not listed. */
    public double getFrictionMultiplier(String materialName) {
        if (materialName == null) return 1.0;
        Double m = materialFrictionMultipliers.get(materialName);
        return m != null ? m : 1.0;
    }

    public double getDefaultRoughness() {
        return defaultRoughness;
    }

    public void setDefaultRoughness(double defaultRoughness) {
        this.defaultRoughness = defaultRoughness;
    }
}
