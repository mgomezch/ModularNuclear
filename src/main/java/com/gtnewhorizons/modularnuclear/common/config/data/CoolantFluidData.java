package com.gtnewhorizons.modularnuclear.common.config.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gtnewhorizons.modularnuclear.common.config.ConfigException;
import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantFluidProperty;

/**
 * Data-driven configuration container for closed-loop coolant fluids.
 */
public class CoolantFluidData {

    public static class FluidPropertyEntry {

        private String fluidName;
        private double density = 1000.0; // kg/m^3
        private double dynamicViscosity = 0.001; // Pa*s
        private double specificHeat = 4184.0; // J/(kg*K)
        private double thermalConductivity = 0.606; // W/(m*K)
        private double boilingPointCelsius = Double.POSITIVE_INFINITY;
        private double freezingPointCelsius = 0.0;
        private boolean isMolten = false;
        private double radiolysisMultiplier = 1.0;
        private boolean spillOnRupture = true;

        public FluidPropertyEntry() {}

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("fluidName", fluidName);
            map.put("density", density);
            map.put("dynamicViscosity", dynamicViscosity);
            map.put("specificHeat", specificHeat);
            map.put("thermalConductivity", thermalConductivity);
            map.put("boilingPointCelsius", Double.isInfinite(boilingPointCelsius) ? "Infinity" : boilingPointCelsius);
            map.put(
                "freezingPointCelsius",
                Double.isInfinite(freezingPointCelsius)
                    ? (freezingPointCelsius == Double.NEGATIVE_INFINITY ? "-Infinity" : "Infinity")
                    : freezingPointCelsius);
            map.put("isMolten", isMolten);
            map.put("radiolysisMultiplier", radiolysisMultiplier);
            map.put("spillOnRupture", spillOnRupture);
            return map;
        }

        @SuppressWarnings("unchecked")
        public static FluidPropertyEntry fromMap(Map<String, Object> map) {
            return fromMap(null, map);
        }

        @SuppressWarnings("unchecked")
        public static FluidPropertyEntry fromMap(String entryKey, Map<String, Object> map) {
            FluidPropertyEntry entry = new FluidPropertyEntry();
            if (map == null) return entry;

            String fluidName = null;
            if (map.containsKey("fluidName") && map.get("fluidName") != null) {
                fluidName = map.get("fluidName")
                    .toString();
            } else if (entryKey != null && !entryKey.isEmpty()) {
                fluidName = entryKey;
            }
            entry.fluidName = fluidName;

            // A built-in coolant of the same name is the baseline for what the entry leaves out
            CoolantFluidProperty canonical = CoolantFluidProperty.getDefault(fluidName);

            if (canonical != null) {
                entry.density = canonical.getDensity();
                entry.dynamicViscosity = canonical.getDynamicViscosity();
                entry.specificHeat = canonical.getSpecificHeat();
                entry.thermalConductivity = canonical.getThermalConductivity();
                entry.boilingPointCelsius = canonical.getBoilingPointCelsius();
                entry.freezingPointCelsius = canonical.getFreezingPointCelsius();
                entry.isMolten = canonical.isMolten();
                entry.radiolysisMultiplier = canonical.getRadiolysisMultiplier();
                entry.spillOnRupture = canonical.isSpillOnRupture();
            }

            if (map.containsKey("density")) entry.density = ((Number) map.get("density")).doubleValue();
            if (map.containsKey("dynamicViscosity"))
                entry.dynamicViscosity = ((Number) map.get("dynamicViscosity")).doubleValue();
            if (map.containsKey("specificHeat")) entry.specificHeat = ((Number) map.get("specificHeat")).doubleValue();
            if (map.containsKey("thermalConductivity"))
                entry.thermalConductivity = ((Number) map.get("thermalConductivity")).doubleValue();

            if (map.containsKey("boilingPointCelsius")) {
                Object b = map.get("boilingPointCelsius");
                if (b instanceof Number) {
                    entry.boilingPointCelsius = ((Number) b).doubleValue();
                } else if (b instanceof String) {
                    String str = ((String) b).trim()
                        .toLowerCase();
                    if (str.contains("inf") || str.equals("none") || str.equals("null")) {
                        entry.boilingPointCelsius = Double.POSITIVE_INFINITY;
                    } else {
                        try {
                            entry.boilingPointCelsius = Double.parseDouble(str);
                        } catch (NumberFormatException e) {
                            throw new ConfigException(
                                entryKey + ".boilingPointCelsius: expected a temperature, got " + b);
                        }
                    }
                }
            }

            if (map.containsKey("freezingPointCelsius")) {
                Object f = map.get("freezingPointCelsius");
                if (f instanceof Number) {
                    entry.freezingPointCelsius = ((Number) f).doubleValue();
                } else if (f instanceof String) {
                    String str = ((String) f).trim()
                        .toLowerCase();
                    if (str.equals("-infinity") || str.equals("-inf")) {
                        entry.freezingPointCelsius = Double.NEGATIVE_INFINITY;
                    } else if (str.contains("inf") || str.equals("none") || str.equals("null")) {
                        entry.freezingPointCelsius = Double.POSITIVE_INFINITY;
                    } else {
                        try {
                            entry.freezingPointCelsius = Double.parseDouble(str);
                        } catch (NumberFormatException e) {
                            throw new ConfigException(
                                entryKey + ".freezingPointCelsius: expected a temperature, got " + f);
                        }
                    }
                }
            }

            if (map.containsKey("isMolten")) entry.isMolten = Boolean.TRUE.equals(map.get("isMolten"));
            if (map.containsKey("radiolysisMultiplier"))
                entry.radiolysisMultiplier = ((Number) map.get("radiolysisMultiplier")).doubleValue();
            if (map.containsKey("spillOnRupture")) {
                entry.spillOnRupture = Boolean.TRUE.equals(map.get("spillOnRupture"));
            } else if (map.containsKey("spill_on_rupture")) {
                entry.spillOnRupture = Boolean.TRUE.equals(map.get("spill_on_rupture"));
            }

            // IC2 coolant intentionally never freezes regardless of configuration
            if (CoolantFluidProperty.isIC2Coolant(fluidName)) {
                entry.freezingPointCelsius = Double.NEGATIVE_INFINITY;
            }

            return entry;
        }

        public String getFluidName() {
            return fluidName;
        }

        public void setFluidName(String fluidName) {
            this.fluidName = fluidName;
        }

        public double getDensity() {
            return density;
        }

        public void setDensity(double density) {
            this.density = density;
        }

        public double getDynamicViscosity() {
            return dynamicViscosity;
        }

        public void setDynamicViscosity(double dynamicViscosity) {
            this.dynamicViscosity = dynamicViscosity;
        }

        public double getSpecificHeat() {
            return specificHeat;
        }

        public void setSpecificHeat(double specificHeat) {
            this.specificHeat = specificHeat;
        }

        public double getThermalConductivity() {
            return thermalConductivity;
        }

        public void setThermalConductivity(double thermalConductivity) {
            this.thermalConductivity = thermalConductivity;
        }

        public double getBoilingPointCelsius() {
            return boilingPointCelsius;
        }

        public void setBoilingPointCelsius(double boilingPointCelsius) {
            this.boilingPointCelsius = boilingPointCelsius;
        }

        public double getFreezingPointCelsius() {
            return freezingPointCelsius;
        }

        public void setFreezingPointCelsius(double freezingPointCelsius) {
            this.freezingPointCelsius = freezingPointCelsius;
        }

        public boolean isMolten() {
            return isMolten;
        }

        public void setMolten(boolean molten) {
            isMolten = molten;
        }

        public double getRadiolysisMultiplier() {
            return radiolysisMultiplier;
        }

        public void setRadiolysisMultiplier(double radiolysisMultiplier) {
            this.radiolysisMultiplier = radiolysisMultiplier;
        }

        public boolean isSpillOnRupture() {
            return spillOnRupture;
        }

        public void setSpillOnRupture(boolean spillOnRupture) {
            this.spillOnRupture = spillOnRupture;
        }
    }

    private Map<String, FluidPropertyEntry> fluids = new LinkedHashMap<>();
    /** Fluid registry names of the liquid nuclear fuels that wreck a pump's impeller. */
    private List<String> liquidNuclearFuels = new ArrayList<>(
        java.util.Arrays.asList(
            "thorium based liquid fuel",
            "thorium based liquid fuel (excited state)",
            "thorium based liquid fuel (depleted)",
            "uranium based liquid fuel",
            "uranium based liquid fuel (excited state)",
            "uranium based liquid fuel (depleted)",
            "plutonium based liquid fuel",
            "plutonium based liquid fuel (excited state)",
            "plutonium based liquid fuel (depleted)",
            "naquadah based liquid fuel mki",
            "naquadah based liquid fuel mki (depleted)",
            "naquadah based liquid fuel mkii",
            "naquadah based liquid fuel mkii (depleted)",
            "naquadah based liquid fuel mkiii",
            "naquadah based liquid fuel mkiii (depleted)",
            "naquadah based liquid fuel mkiv",
            "naquadah based liquid fuel mkiv (depleted)",
            "naquadah based liquid fuel mkv",
            "naquadah based liquid fuel mkv (depleted)",
            "naquadah based liquid fuel mkvi",
            "naquadah based liquid fuel mkvi (depleted)",
            "molten.uraniumhexafluoride",
            "molten.uraniumtetrafluoride"));

    public Map<String, FluidPropertyEntry> getFluids() {
        return fluids;
    }

    public void setFluids(Map<String, FluidPropertyEntry> fluids) {
        this.fluids = fluids;
    }

    private Set<String> liquidNuclearFuelSet = new HashSet<>(liquidNuclearFuels);

    public List<String> getLiquidNuclearFuels() {
        return liquidNuclearFuels;
    }

    public void setLiquidNuclearFuels(List<String> fuels) {
        this.liquidNuclearFuels = fuels;
        this.liquidNuclearFuelSet = new HashSet<>(fuels);
    }

    public boolean isLiquidNuclearFuel(String name) {
        return name != null && liquidNuclearFuelSet.contains(name);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        Map<String, Object> fluidMap = new LinkedHashMap<>();
        for (Map.Entry<String, FluidPropertyEntry> e : fluids.entrySet()) {
            fluidMap.put(
                e.getKey(),
                e.getValue()
                    .toMap());
        }
        map.put("fluids", fluidMap);
        map.put("liquidNuclearFuels", liquidNuclearFuels);
        return map;
    }

    @SuppressWarnings("unchecked")
    public static CoolantFluidData fromMap(Map<String, Object> map) {
        CoolantFluidData data = new CoolantFluidData();
        if (map == null) return data;

        if (map.containsKey("fluids") && map.get("fluids") instanceof Map) {
            Map<String, Object> fMap = (Map<String, Object>) map.get("fluids");
            for (Map.Entry<String, Object> entry : fMap.entrySet()) {
                if (entry.getValue() instanceof Map) {
                    FluidPropertyEntry prop = FluidPropertyEntry
                        .fromMap(entry.getKey(), (Map<String, Object>) entry.getValue());
                    if (prop.getFluidName() == null || prop.getFluidName()
                        .isEmpty()) {
                        prop.setFluidName(entry.getKey());
                    }
                    data.fluids.put(entry.getKey(), prop);
                }
            }
        }

        if (map.containsKey("liquidNuclearFuels") && map.get("liquidNuclearFuels") instanceof List) {
            List<String> fuels = new ArrayList<>();
            for (Object o : (List<?>) map.get("liquidNuclearFuels")) {
                if (o != null) {
                    fuels.add(o.toString());
                }
            }
            data.setLiquidNuclearFuels(fuels);
        }
        return data;
    }
}
