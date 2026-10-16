package com.gtnewhorizons.modularnuclear.common.config.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured data models for non-fuel reactor components:
 * Reflectors, Moderators, Control Rods, Heat Vents, Heat Exchangers,
 * Platings, Insulators, Radiovoltaics, Coolant Fluids, and Coolant Items.
 */
public class ComponentData {

    public List<ReflectorData> reflectors = new ArrayList<>();
    public List<ModeratorData> moderators = new ArrayList<>();
    public List<ControlRodData> controlRods = new ArrayList<>();
    public List<HeatVentData> heatVents = new ArrayList<>();
    public List<HeatExchangerData> heatExchangers = new ArrayList<>();
    public List<PlatingData> platings = new ArrayList<>();
    public List<InsulatorData> insulators = new ArrayList<>();
    public List<RadiovoltaicData> radiovoltaics = new ArrayList<>();
    public List<CoolantFluidData> coolantFluids = new ArrayList<>();
    public List<CoolantItemData> coolantItems = new ArrayList<>();

    public ComponentData() {}

    public static ComponentData fromMap(Map<String, Object> map) {
        ComponentData cd = new ComponentData();
        if (map == null) return cd;

        loadList(map, "reflectors", list -> {
            for (Object obj : list) {
                if (obj instanceof Map<?, ?> m) cd.reflectors.add(ReflectorData.fromMap((Map<String, Object>) m));
            }
        });
        loadList(map, "moderators", list -> {
            for (Object obj : list) {
                if (obj instanceof Map<?, ?> m) cd.moderators.add(ModeratorData.fromMap((Map<String, Object>) m));
            }
        });
        loadList(map, "control_rods", list -> {
            for (Object obj : list) {
                if (obj instanceof Map<?, ?> m) cd.controlRods.add(ControlRodData.fromMap((Map<String, Object>) m));
            }
        });
        loadList(map, "heat_vents", list -> {
            for (Object obj : list) {
                if (obj instanceof Map<?, ?> m) cd.heatVents.add(HeatVentData.fromMap((Map<String, Object>) m));
            }
        });
        loadList(map, "heat_exchangers", list -> {
            for (Object obj : list) {
                if (obj instanceof Map<?, ?> m) cd.heatExchangers.add(HeatExchangerData.fromMap((Map<String, Object>) m));
            }
        });
        loadList(map, "platings", list -> {
            for (Object obj : list) {
                if (obj instanceof Map<?, ?> m) cd.platings.add(PlatingData.fromMap((Map<String, Object>) m));
            }
        });
        loadList(map, "insulators", list -> {
            for (Object obj : list) {
                if (obj instanceof Map<?, ?> m) cd.insulators.add(InsulatorData.fromMap((Map<String, Object>) m));
            }
        });
        loadList(map, "radiovoltaics", list -> {
            for (Object obj : list) {
                if (obj instanceof Map<?, ?> m) cd.radiovoltaics.add(RadiovoltaicData.fromMap((Map<String, Object>) m));
            }
        });
        loadList(map, "coolant_fluids", list -> {
            for (Object obj : list) {
                if (obj instanceof Map<?, ?> m) cd.coolantFluids.add(CoolantFluidData.fromMap((Map<String, Object>) m));
            }
        });
        loadList(map, "coolant_items", list -> {
            for (Object obj : list) {
                if (obj instanceof Map<?, ?> m) cd.coolantItems.add(CoolantItemData.fromMap((Map<String, Object>) m));
            }
        });

        return cd;
    }

    private static void loadList(Map<String, Object> map, String key, java.util.function.Consumer<List<?>> consumer) {
        Object val = map.get(key);
        if (val instanceof List<?> l) {
            consumer.accept(l);
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();

        List<Map<String, Object>> rList = new ArrayList<>();
        for (ReflectorData d : reflectors) rList.add(d.toMap());
        m.put("reflectors", rList);

        List<Map<String, Object>> modList = new ArrayList<>();
        for (ModeratorData d : moderators) modList.add(d.toMap());
        m.put("moderators", modList);

        List<Map<String, Object>> crList = new ArrayList<>();
        for (ControlRodData d : controlRods) crList.add(d.toMap());
        m.put("control_rods", crList);

        List<Map<String, Object>> hvList = new ArrayList<>();
        for (HeatVentData d : heatVents) hvList.add(d.toMap());
        m.put("heat_vents", hvList);

        List<Map<String, Object>> heList = new ArrayList<>();
        for (HeatExchangerData d : heatExchangers) heList.add(d.toMap());
        m.put("heat_exchangers", heList);

        List<Map<String, Object>> plList = new ArrayList<>();
        for (PlatingData d : platings) plList.add(d.toMap());
        m.put("platings", plList);

        List<Map<String, Object>> inList = new ArrayList<>();
        for (InsulatorData d : insulators) inList.add(d.toMap());
        m.put("insulators", inList);

        List<Map<String, Object>> rvList = new ArrayList<>();
        for (RadiovoltaicData d : radiovoltaics) rvList.add(d.toMap());
        m.put("radiovoltaics", rvList);

        List<Map<String, Object>> cfList = new ArrayList<>();
        for (CoolantFluidData d : coolantFluids) cfList.add(d.toMap());
        m.put("coolant_fluids", cfList);

        List<Map<String, Object>> ciList = new ArrayList<>();
        for (CoolantItemData d : coolantItems) ciList.add(d.toMap());
        m.put("coolant_items", ciList);

        return m;
    }

    // =========================================================================
    // ReflectorData
    // =========================================================================
    public static class ReflectorData {
        public String id;
        public String name;
        public List<String> matching = new ArrayList<>();
        public double absorptionFast = 0.01;
        public double absorptionThermal = 0.02;
        public double scatteringFast = 0.95;
        public double scatteringThermal = 0.98;
        public double moderation = 0.20;
        public double heatTransferCoeff = 0.15;
        public int durability = 0; // 0 = infinite

        public ReflectorData() {}

        public ReflectorData(String id, String name, double absFast, double absThermal, double scattFast,
            double scattThermal, double moderation, double heatTransferCoeff, int durability, String... matching) {
            this.id = id;
            this.name = name;
            this.absorptionFast = absFast;
            this.absorptionThermal = absThermal;
            this.scatteringFast = scattFast;
            this.scatteringThermal = scattThermal;
            this.moderation = moderation;
            this.heatTransferCoeff = heatTransferCoeff;
            this.durability = durability;
            if (matching != null) this.matching.addAll(Arrays.asList(matching));
        }

        public static ReflectorData fromMap(Map<String, Object> map) {
            ReflectorData d = new ReflectorData();
            if (map == null) return d;
            d.id = (String) map.getOrDefault("id", "reflector");
            d.name = (String) map.getOrDefault("name", d.id);
            readMatching(map, d.matching);
            d.absorptionFast = PhysicsData.getDouble(map, "absorption_fast", d.absorptionFast);
            d.absorptionThermal = PhysicsData.getDouble(map, "absorption_thermal", d.absorptionThermal);
            d.scatteringFast = PhysicsData.getDouble(map, "scattering_fast", d.scatteringFast);
            d.scatteringThermal = PhysicsData.getDouble(map, "scattering_thermal", d.scatteringThermal);
            d.moderation = PhysicsData.getDouble(map, "moderation", d.moderation);
            d.heatTransferCoeff = PhysicsData.getDouble(map, "heat_transfer_coeff", d.heatTransferCoeff);
            d.durability = PhysicsData.getInt(map, "durability", d.durability);
            return d;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("name", name);
            m.put("matching", new ArrayList<>(matching));
            m.put("absorption_fast", absorptionFast);
            m.put("absorption_thermal", absorptionThermal);
            m.put("scattering_fast", scatteringFast);
            m.put("scattering_thermal", scatteringThermal);
            m.put("moderation", moderation);
            m.put("heat_transfer_coeff", heatTransferCoeff);
            m.put("durability", durability);
            return m;
        }
    }

    // =========================================================================
    // ModeratorData
    // =========================================================================
    public static class ModeratorData {
        public String id;
        public String name;
        public List<String> matching = new ArrayList<>();
        public double absorptionFast = 0.002;
        public double absorptionThermal = 0.009;
        public double scatteringFast = 0.93;
        public double scatteringThermal = 0.621;
        public double moderation = 0.50;
        public double heatTransferCoeff = 0.05;
        public double maxTemperature = 2500.0;

        public ModeratorData() {}

        public ModeratorData(String id, String name, double absFast, double absThermal, double scattFast,
            double scattThermal, double moderation, double heatTransferCoeff, double maxTemp, String... matching) {
            this.id = id;
            this.name = name;
            this.absorptionFast = absFast;
            this.absorptionThermal = absThermal;
            this.scatteringFast = scattFast;
            this.scatteringThermal = scattThermal;
            this.moderation = moderation;
            this.heatTransferCoeff = heatTransferCoeff;
            this.maxTemperature = maxTemp;
            if (matching != null) this.matching.addAll(Arrays.asList(matching));
        }

        public static ModeratorData fromMap(Map<String, Object> map) {
            ModeratorData d = new ModeratorData();
            if (map == null) return d;
            d.id = (String) map.getOrDefault("id", "moderator");
            d.name = (String) map.getOrDefault("name", d.id);
            readMatching(map, d.matching);
            d.absorptionFast = PhysicsData.getDouble(map, "absorption_fast", d.absorptionFast);
            d.absorptionThermal = PhysicsData.getDouble(map, "absorption_thermal", d.absorptionThermal);
            d.scatteringFast = PhysicsData.getDouble(map, "scattering_fast", d.scatteringFast);
            d.scatteringThermal = PhysicsData.getDouble(map, "scattering_thermal", d.scatteringThermal);
            d.moderation = PhysicsData.getDouble(map, "moderation", d.moderation);
            d.heatTransferCoeff = PhysicsData.getDouble(map, "heat_transfer_coeff", d.heatTransferCoeff);
            d.maxTemperature = PhysicsData.getDouble(map, "max_temperature", d.maxTemperature);
            return d;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("name", name);
            m.put("matching", new ArrayList<>(matching));
            m.put("absorption_fast", absorptionFast);
            m.put("absorption_thermal", absorptionThermal);
            m.put("scattering_fast", scatteringFast);
            m.put("scattering_thermal", scatteringThermal);
            m.put("moderation", moderation);
            m.put("heat_transfer_coeff", heatTransferCoeff);
            m.put("max_temperature", maxTemperature);
            return m;
        }
    }

    // =========================================================================
    // ControlRodData
    // =========================================================================
    public static class ControlRodData {
        public String id;
        public String name;
        public List<String> matching = new ArrayList<>();
        public double maxThermalAbsorption = 0.50;
        public double maxFastAbsorption = 0.20;
        public double scatteringFast = 0.05;
        public double scatteringThermal = 0.05;
        public double heatTransferCoeff = 0.20;

        public ControlRodData() {}

        public ControlRodData(String id, String name, double maxThermal, double maxFast, double scattFast,
            double scattThermal, double heatTransferCoeff, String... matching) {
            this.id = id;
            this.name = name;
            this.maxThermalAbsorption = maxThermal;
            this.maxFastAbsorption = maxFast;
            this.scatteringFast = scattFast;
            this.scatteringThermal = scattThermal;
            this.heatTransferCoeff = heatTransferCoeff;
            if (matching != null) this.matching.addAll(Arrays.asList(matching));
        }

        public static ControlRodData fromMap(Map<String, Object> map) {
            ControlRodData d = new ControlRodData();
            if (map == null) return d;
            d.id = (String) map.getOrDefault("id", "control_rod");
            d.name = (String) map.getOrDefault("name", d.id);
            readMatching(map, d.matching);
            d.maxThermalAbsorption = PhysicsData.getDouble(map, "max_thermal_absorption", d.maxThermalAbsorption);
            d.maxFastAbsorption = PhysicsData.getDouble(map, "max_fast_absorption", d.maxFastAbsorption);
            d.scatteringFast = PhysicsData.getDouble(map, "scattering_fast", d.scatteringFast);
            d.scatteringThermal = PhysicsData.getDouble(map, "scattering_thermal", d.scatteringThermal);
            d.heatTransferCoeff = PhysicsData.getDouble(map, "heat_transfer_coeff", d.heatTransferCoeff);
            return d;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("name", name);
            m.put("matching", new ArrayList<>(matching));
            m.put("max_thermal_absorption", maxThermalAbsorption);
            m.put("max_fast_absorption", maxFastAbsorption);
            m.put("scattering_fast", scatteringFast);
            m.put("scattering_thermal", scatteringThermal);
            m.put("heat_transfer_coeff", heatTransferCoeff);
            return m;
        }
    }

    // =========================================================================
    // HeatVentData
    // =========================================================================
    public static class HeatVentData {
        public String id;
        public String name;
        public List<String> matching = new ArrayList<>();
        public double heatTransferCoeff = 0.40;
        public double selfCoolingRate = 6.0;
        public double maxTemperature = 1500.0;

        public HeatVentData() {}

        public HeatVentData(String id, String name, double heatTransferCoeff, double selfCoolingRate, double maxTemp,
            String... matching) {
            this.id = id;
            this.name = name;
            this.heatTransferCoeff = heatTransferCoeff;
            this.selfCoolingRate = selfCoolingRate;
            this.maxTemperature = maxTemp;
            if (matching != null) this.matching.addAll(Arrays.asList(matching));
        }

        public static HeatVentData fromMap(Map<String, Object> map) {
            HeatVentData d = new HeatVentData();
            if (map == null) return d;
            d.id = (String) map.getOrDefault("id", "heat_vent");
            d.name = (String) map.getOrDefault("name", d.id);
            readMatching(map, d.matching);
            d.heatTransferCoeff = PhysicsData.getDouble(map, "heat_transfer_coeff", d.heatTransferCoeff);
            d.selfCoolingRate = PhysicsData.getDouble(map, "self_cooling_rate", d.selfCoolingRate);
            d.maxTemperature = PhysicsData.getDouble(map, "max_temperature", d.maxTemperature);
            return d;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("name", name);
            m.put("matching", new ArrayList<>(matching));
            m.put("heat_transfer_coeff", heatTransferCoeff);
            m.put("self_cooling_rate", selfCoolingRate);
            m.put("max_temperature", maxTemperature);
            return m;
        }
    }

    // =========================================================================
    // HeatExchangerData
    // =========================================================================
    public static class HeatExchangerData {
        public String id;
        public String name;
        public List<String> matching = new ArrayList<>();
        public double heatTransferCoeff = 0.40;
        public double switchTransferRate = 12.0;
        public double maxTemperature = 1500.0;

        public HeatExchangerData() {}

        public HeatExchangerData(String id, String name, double heatTransferCoeff, double switchTransferRate,
            double maxTemp, String... matching) {
            this.id = id;
            this.name = name;
            this.heatTransferCoeff = heatTransferCoeff;
            this.switchTransferRate = switchTransferRate;
            this.maxTemperature = maxTemp;
            if (matching != null) this.matching.addAll(Arrays.asList(matching));
        }

        public static HeatExchangerData fromMap(Map<String, Object> map) {
            HeatExchangerData d = new HeatExchangerData();
            if (map == null) return d;
            d.id = (String) map.getOrDefault("id", "heat_exchanger");
            d.name = (String) map.getOrDefault("name", d.id);
            readMatching(map, d.matching);
            d.heatTransferCoeff = PhysicsData.getDouble(map, "heat_transfer_coeff", d.heatTransferCoeff);
            d.switchTransferRate = PhysicsData.getDouble(map, "switch_transfer_rate", d.switchTransferRate);
            d.maxTemperature = PhysicsData.getDouble(map, "max_temperature", d.maxTemperature);
            return d;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("name", name);
            m.put("matching", new ArrayList<>(matching));
            m.put("heat_transfer_coeff", heatTransferCoeff);
            m.put("switch_transfer_rate", switchTransferRate);
            m.put("max_temperature", maxTemperature);
            return m;
        }
    }

    // =========================================================================
    // PlatingData
    // =========================================================================
    public static class PlatingData {
        public String id;
        public String name;
        public List<String> matching = new ArrayList<>();
        public double heatCapacityBonus = 1000.0;
        public double explosionContainmentBonus = 0.05;
        public double heatTransferCoeff = 0.05;

        public PlatingData() {}

        public PlatingData(String id, String name, double heatCapacityBonus, double explosionContainmentBonus,
            double heatTransferCoeff, String... matching) {
            this.id = id;
            this.name = name;
            this.heatCapacityBonus = heatCapacityBonus;
            this.explosionContainmentBonus = explosionContainmentBonus;
            this.heatTransferCoeff = heatTransferCoeff;
            if (matching != null) this.matching.addAll(Arrays.asList(matching));
        }

        public static PlatingData fromMap(Map<String, Object> map) {
            PlatingData d = new PlatingData();
            if (map == null) return d;
            d.id = (String) map.getOrDefault("id", "plating");
            d.name = (String) map.getOrDefault("name", d.id);
            readMatching(map, d.matching);
            d.heatCapacityBonus = PhysicsData.getDouble(map, "heat_capacity_bonus", d.heatCapacityBonus);
            d.explosionContainmentBonus = PhysicsData.getDouble(map, "explosion_containment_bonus", d.explosionContainmentBonus);
            d.heatTransferCoeff = PhysicsData.getDouble(map, "heat_transfer_coeff", d.heatTransferCoeff);
            return d;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("name", name);
            m.put("matching", new ArrayList<>(matching));
            m.put("heat_capacity_bonus", heatCapacityBonus);
            m.put("explosion_containment_bonus", explosionContainmentBonus);
            m.put("heat_transfer_coeff", heatTransferCoeff);
            return m;
        }
    }

    // =========================================================================
    // InsulatorData
    // =========================================================================
    public static class InsulatorData {
        public String id;
        public String name;
        public List<String> matching = new ArrayList<>();
        public double dampening = 0.20;
        public double heatTransferCoeff = 0.01;
        public double absorptionFast = 0.01;
        public double absorptionThermal = 0.01;
        public double scatteringFast = 0.05;
        public double scatteringThermal = 0.05;

        public InsulatorData() {}

        public InsulatorData(String id, String name, double dampening, double heatTransferCoeff, double absFast,
            double absThermal, double scattFast, double scattThermal, String... matching) {
            this.id = id;
            this.name = name;
            this.dampening = dampening;
            this.heatTransferCoeff = heatTransferCoeff;
            this.absorptionFast = absFast;
            this.absorptionThermal = absThermal;
            this.scatteringFast = scattFast;
            this.scatteringThermal = scattThermal;
            if (matching != null) this.matching.addAll(Arrays.asList(matching));
        }

        public static InsulatorData fromMap(Map<String, Object> map) {
            InsulatorData d = new InsulatorData();
            if (map == null) return d;
            d.id = (String) map.getOrDefault("id", "insulator");
            d.name = (String) map.getOrDefault("name", d.id);
            readMatching(map, d.matching);
            d.dampening = PhysicsData.getDouble(map, "dampening", d.dampening);
            d.heatTransferCoeff = PhysicsData.getDouble(map, "heat_transfer_coeff", d.heatTransferCoeff);
            d.absorptionFast = PhysicsData.getDouble(map, "absorption_fast", d.absorptionFast);
            d.absorptionThermal = PhysicsData.getDouble(map, "absorption_thermal", d.absorptionThermal);
            d.scatteringFast = PhysicsData.getDouble(map, "scattering_fast", d.scatteringFast);
            d.scatteringThermal = PhysicsData.getDouble(map, "scattering_thermal", d.scatteringThermal);
            return d;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("name", name);
            m.put("matching", new ArrayList<>(matching));
            m.put("dampening", dampening);
            m.put("heat_transfer_coeff", heatTransferCoeff);
            m.put("absorption_fast", absorptionFast);
            m.put("absorption_thermal", absorptionThermal);
            m.put("scattering_fast", scatteringFast);
            m.put("scattering_thermal", scatteringThermal);
            return m;
        }
    }

    // =========================================================================
    // RadiovoltaicData
    // =========================================================================
    public static class RadiovoltaicData {
        public String id;
        public String name;
        public List<String> matching = new ArrayList<>();
        public double euConversionEfficiency = 0.25;
        public double heatTransferCoeff = 0.10;
        public double absorptionFast = 1.0;
        public double absorptionThermal = 1.0;

        public RadiovoltaicData() {}

        public RadiovoltaicData(String id, String name, double euConversionEfficiency, double heatTransferCoeff,
            double absFast, double absThermal, String... matching) {
            this.id = id;
            this.name = name;
            this.euConversionEfficiency = euConversionEfficiency;
            this.heatTransferCoeff = heatTransferCoeff;
            this.absorptionFast = absFast;
            this.absorptionThermal = absThermal;
            if (matching != null) this.matching.addAll(Arrays.asList(matching));
        }

        public static RadiovoltaicData fromMap(Map<String, Object> map) {
            RadiovoltaicData d = new RadiovoltaicData();
            if (map == null) return d;
            d.id = (String) map.getOrDefault("id", "radiovoltaic");
            d.name = (String) map.getOrDefault("name", d.id);
            readMatching(map, d.matching);
            d.euConversionEfficiency = PhysicsData.getDouble(map, "eu_conversion_efficiency", d.euConversionEfficiency);
            d.heatTransferCoeff = PhysicsData.getDouble(map, "heat_transfer_coeff", d.heatTransferCoeff);
            d.absorptionFast = PhysicsData.getDouble(map, "absorption_fast", d.absorptionFast);
            d.absorptionThermal = PhysicsData.getDouble(map, "absorption_thermal", d.absorptionThermal);
            return d;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("name", name);
            m.put("matching", new ArrayList<>(matching));
            m.put("eu_conversion_efficiency", euConversionEfficiency);
            m.put("heat_transfer_coeff", heatTransferCoeff);
            m.put("absorption_fast", absorptionFast);
            m.put("absorption_thermal", absorptionThermal);
            return m;
        }
    }

    // =========================================================================
    // CoolantFluidData
    // =========================================================================
    public static class CoolantFluidData {
        public String id;
        public String name;
        public List<String> matching = new ArrayList<>();
        public double boilingTemperature = 100.0;
        public double heatTransferCoeff = 0.25;
        public double heatCapacity = 4.184;
        public double absorptionFast = 0.05;
        public double absorptionThermal = 0.10;
        public double scatteringFast = 0.70;
        public double scatteringThermal = 0.70;
        public double moderation = 0.25;
        public String hotFluidName;
        public double coolingMultiplier = 1.0;

        public CoolantFluidData() {}

        public CoolantFluidData(String id, String name, double boilingTemp, double heatTransferCoeff,
            double heatCapacity, double absFast, double absThermal, double scattFast, double scattThermal,
            double moderation, String hotFluidName, double coolingMult, String... matching) {
            this.id = id;
            this.name = name;
            this.boilingTemperature = boilingTemp;
            this.heatTransferCoeff = heatTransferCoeff;
            this.heatCapacity = heatCapacity;
            this.absorptionFast = absFast;
            this.absorptionThermal = absThermal;
            this.scatteringFast = scattFast;
            this.scatteringThermal = scattThermal;
            this.moderation = moderation;
            this.hotFluidName = hotFluidName;
            this.coolingMultiplier = coolingMult;
            if (matching != null) this.matching.addAll(Arrays.asList(matching));
        }

        public static CoolantFluidData fromMap(Map<String, Object> map) {
            CoolantFluidData d = new CoolantFluidData();
            if (map == null) return d;
            d.id = (String) map.getOrDefault("id", "coolant_fluid");
            d.name = (String) map.getOrDefault("name", d.id);
            readMatching(map, d.matching);
            d.boilingTemperature = PhysicsData.getDouble(map, "boiling_temperature", d.boilingTemperature);
            d.heatTransferCoeff = PhysicsData.getDouble(map, "heat_transfer_coeff", d.heatTransferCoeff);
            d.heatCapacity = PhysicsData.getDouble(map, "heat_capacity", d.heatCapacity);
            d.absorptionFast = PhysicsData.getDouble(map, "absorption_fast", d.absorptionFast);
            d.absorptionThermal = PhysicsData.getDouble(map, "absorption_thermal", d.absorptionThermal);
            d.scatteringFast = PhysicsData.getDouble(map, "scattering_fast", d.scatteringFast);
            d.scatteringThermal = PhysicsData.getDouble(map, "scattering_thermal", d.scatteringThermal);
            d.moderation = PhysicsData.getDouble(map, "moderation", d.moderation);
            d.hotFluidName = (String) map.get("hot_fluid_name");
            d.coolingMultiplier = PhysicsData.getDouble(map, "cooling_multiplier", d.coolingMultiplier);
            return d;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("name", name);
            m.put("matching", new ArrayList<>(matching));
            m.put("boiling_temperature", boilingTemperature);
            m.put("heat_transfer_coeff", heatTransferCoeff);
            m.put("heat_capacity", heatCapacity);
            m.put("absorption_fast", absorptionFast);
            m.put("absorption_thermal", absorptionThermal);
            m.put("scattering_fast", scatteringFast);
            m.put("scattering_thermal", scatteringThermal);
            m.put("moderation", moderation);
            if (hotFluidName != null) m.put("hot_fluid_name", hotFluidName);
            m.put("cooling_multiplier", coolingMultiplier);
            return m;
        }
    }

    // =========================================================================
    // CoolantItemData
    // =========================================================================
    public static class CoolantItemData {
        public String id;
        public String name;
        public List<String> matching = new ArrayList<>();
        public double heatCapacity = 60000.0;
        public double heatTransferCoeff = 0.40;
        public double absorptionFast = 0.02;
        public double absorptionThermal = 0.05;
        public double scatteringFast = 0.45;
        public double scatteringThermal = 0.45;
        public double moderation = 0.40;

        public CoolantItemData() {}

        public CoolantItemData(String id, String name, double heatCapacity, double heatTransferCoeff, double absFast,
            double absThermal, double scattFast, double scattThermal, double moderation, String... matching) {
            this.id = id;
            this.name = name;
            this.heatCapacity = heatCapacity;
            this.heatTransferCoeff = heatTransferCoeff;
            this.absorptionFast = absFast;
            this.absorptionThermal = absThermal;
            this.scatteringFast = scattFast;
            this.scatteringThermal = scattThermal;
            this.moderation = moderation;
            if (matching != null) this.matching.addAll(Arrays.asList(matching));
        }

        public static CoolantItemData fromMap(Map<String, Object> map) {
            CoolantItemData d = new CoolantItemData();
            if (map == null) return d;
            d.id = (String) map.getOrDefault("id", "coolant_item");
            d.name = (String) map.getOrDefault("name", d.id);
            readMatching(map, d.matching);
            d.heatCapacity = PhysicsData.getDouble(map, "heat_capacity", d.heatCapacity);
            d.heatTransferCoeff = PhysicsData.getDouble(map, "heat_transfer_coeff", d.heatTransferCoeff);
            d.absorptionFast = PhysicsData.getDouble(map, "absorption_fast", d.absorptionFast);
            d.absorptionThermal = PhysicsData.getDouble(map, "absorption_thermal", d.absorptionThermal);
            d.scatteringFast = PhysicsData.getDouble(map, "scattering_fast", d.scatteringFast);
            d.scatteringThermal = PhysicsData.getDouble(map, "scattering_thermal", d.scatteringThermal);
            d.moderation = PhysicsData.getDouble(map, "moderation", d.moderation);
            return d;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("name", name);
            m.put("matching", new ArrayList<>(matching));
            m.put("heat_capacity", heatCapacity);
            m.put("heat_transfer_coeff", heatTransferCoeff);
            m.put("absorption_fast", absorptionFast);
            m.put("absorption_thermal", absorptionThermal);
            m.put("scattering_fast", scatteringFast);
            m.put("scattering_thermal", scatteringThermal);
            m.put("moderation", moderation);
            return m;
        }
    }

    private static void readMatching(Map<String, Object> map, List<String> target) {
        Object obj = map.get("matching");
        if (obj instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) target.add(item.toString());
            }
        }
    }
}
