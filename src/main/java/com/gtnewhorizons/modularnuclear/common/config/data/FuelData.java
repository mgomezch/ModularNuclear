package com.gtnewhorizons.modularnuclear.common.config.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gtnewhorizons.modularnuclear.common.nuclear.FuelDamageCurveSolver;

public class FuelData {

    public String id;
    public String name;
    public List<String> matching = new ArrayList<>();
    public boolean isLiquid = false;

    // Reactivity and Neutronic parameters
    public String archetype = "standard"; // standard, dense, excited, breeder
    public int packetCount = 1;
    public int baseNeutrons = 4;
    public double thermalFissionMultiplier = 1.0;
    public double fastFissionMultiplier = 0.10;
    public double absorptionFast = 0.25;
    public double absorptionThermal = 0.80;
    public double scatteringFast = 0.15;
    public double scatteringThermal = 0.10;
    public double moderation = 0.10;

    // Thermodynamic parameters
    public double heatTransferCoeff = 0.05;
    public double heatPerReaction = 2.0;
    public int defaultDurability = 10000;
    public double maxTemperature = 2000.0;
    public double peakReactivityTemp = 770.0;
    public double floorTemp = 2300.0;

    // Depletion / Damage Curve profile
    public double peakReactivityMult = 1.0;
    public double peakDamageFraction = 0.0;
    public double endReactivityMult = 0.10;
    public double endDamageFraction = 1.0;

    public String spentProduct;

    private transient boolean solverInitialized = false;
    private transient double damageK;
    private transient double damageA;

    public FuelData() {}

    public FuelData(String id, String name, String archetype, boolean isLiquid, int packetCount, int baseNeutrons,
        double thermalFissMult, double fastFissMult, double absFast, double absThermal,
        double scattFast, double scattThermal, double moderation, double heatTransferCoeff,
        double heatPerReaction, int defaultDurability, double peakReactivityTemp, double floorTemp,
        double peakMult, double peakPoint, double endMult, String spentProduct, double maxTemperature,
        String... matchPatterns) {
        this.id = id;
        this.name = name;
        this.archetype = archetype != null ? archetype : "standard";
        this.isLiquid = isLiquid;
        this.packetCount = packetCount;
        this.baseNeutrons = baseNeutrons;
        this.thermalFissionMultiplier = thermalFissMult;
        this.fastFissionMultiplier = fastFissMult;
        this.absorptionFast = absFast;
        this.absorptionThermal = absThermal;
        this.scatteringFast = scattFast;
        this.scatteringThermal = scattThermal;
        this.moderation = moderation;
        this.heatTransferCoeff = heatTransferCoeff;
        this.heatPerReaction = heatPerReaction;
        this.defaultDurability = defaultDurability;
        this.peakReactivityTemp = peakReactivityTemp;
        this.floorTemp = floorTemp;
        this.peakReactivityMult = peakMult;
        this.peakDamageFraction = peakPoint;
        this.endReactivityMult = endMult;
        this.spentProduct = spentProduct;
        this.maxTemperature = maxTemperature;
        if (matchPatterns != null) {
            for (String p : matchPatterns) {
                if (p != null && !p.trim().isEmpty()) {
                    this.matching.add(p.trim());
                }
            }
        }
    }

    public FuelData(String id, String name, boolean isLiquid, int packetCount, int baseNeutrons,
        double thermalFissMult, double fastFissMult, double absFast, double absThermal,
        double scattFast, double scattThermal, double moderation, double heatTransferCoeff,
        double heatPerReaction, int defaultDurability, double peakReactivityTemp, double floorTemp,
        double peakMult, double peakPoint, double endMult, String spentProduct, double maxTemperature,
        String... matchPatterns) {
        this(id, name, "standard", isLiquid, packetCount, baseNeutrons, thermalFissMult, fastFissMult,
            absFast, absThermal, scattFast, scattThermal, moderation, heatTransferCoeff, heatPerReaction,
            defaultDurability, peakReactivityTemp, floorTemp, peakMult, peakPoint, endMult, spentProduct,
            maxTemperature, matchPatterns);
    }

    public FuelData(String id, String name, boolean isLiquid, int baseNeutrons, double thermalFissMult,
        double fastFissMult, double absFast, double absThermal, double scattFast, double scattThermal,
        double moderation, double heatTransferCoeff, double heatPerReaction, int defaultDurability,
        double peakMult, double peakPoint, double endMult, String spentProduct, double maxTemperature,
        String... matchPatterns) {
        this(id, name, isLiquid, isLiquid ? 4 : 1, baseNeutrons, thermalFissMult, fastFissMult,
            absFast, absThermal, scattFast, scattThermal, moderation, heatTransferCoeff,
            heatPerReaction, defaultDurability, 770.0, 2300.0, peakMult, peakPoint, endMult,
            spentProduct, maxTemperature, matchPatterns);
    }

    private synchronized void ensureSolver() {
        if (!solverInitialized) {
            FuelDamageCurveSolver.FuelCurveStats stats = FuelDamageCurveSolver
                .solve(null, peakReactivityTemp, floorTemp, 20.0);
            this.damageK = stats.k;
            this.damageA = stats.A;
            this.solverInitialized = true;
        }
    }

    /**
     * Temperature durability damage following a slowly-growing zero-crossing-adjusted exponential curve.
     * Produces zero damage at or below ambient temperature.
     */
    public double calculateTemperatureDamage(double temp, double ambientTemp) {
        if (temp <= ambientTemp) {
            return 0.0;
        }
        ensureSolver();
        return damageA * (Math.exp(damageK * (temp - ambientTemp)) - 1.0);
    }

    public double calculateTemperatureDamage(double temp) {
        return calculateTemperatureDamage(temp, 20.0);
    }

    /**
     * Reactivity curve: ramps smoothly up from 50% (0.50) at 0 °C to 100% (1.00) at peakReactivityTemp,
     * then reduces smoothly to 5% (0.05) at floorTemp, and stays at 5% (0.05) for all higher temperatures.
     */
    public double calculateReactivity(double temp) {
        if (temp <= 0.0) {
            return 0.50;
        }
        if (temp <= peakReactivityTemp) {
            return 0.50 + 0.50 * Math.sin((Math.PI / 2.0) * (temp / peakReactivityTemp));
        }
        if (temp >= floorTemp) {
            return 0.05;
        }
        double progress = (temp - peakReactivityTemp) / (floorTemp - peakReactivityTemp);
        return 0.05 + 0.95 * (0.5 * (1.0 + Math.cos(Math.PI * progress)));
    }

    public static FuelData fromMap(Map<String, Object> map) {
        FuelData fd = new FuelData();
        if (map == null) return fd;
        fd.id = (String) map.getOrDefault("id", "fuel");
        fd.name = (String) map.getOrDefault("name", fd.id);
        Object isLiq = map.get("is_liquid");
        if (isLiq instanceof Boolean b) fd.isLiquid = b;
        else if (isLiq instanceof String s) fd.isLiquid = Boolean.parseBoolean(s);

        Object matchingObj = map.get("matching");
        if (matchingObj instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) fd.matching.add(item.toString());
            }
        }

        fd.archetype = PhysicsData.getString(map, "archetype", fd.archetype);
        fd.packetCount = PhysicsData.getInt(map, "packet_count", fd.packetCount);
        fd.baseNeutrons = PhysicsData.getInt(map, "base_neutrons", fd.baseNeutrons);
        fd.thermalFissionMultiplier = PhysicsData.getDouble(map, "thermal_fission_multiplier", fd.thermalFissionMultiplier);
        fd.fastFissionMultiplier = PhysicsData.getDouble(map, "fast_fission_multiplier", fd.fastFissionMultiplier);
        fd.absorptionFast = PhysicsData.getDouble(map, "absorption_fast", fd.absorptionFast);
        fd.absorptionThermal = PhysicsData.getDouble(map, "absorption_thermal", fd.absorptionThermal);
        fd.scatteringFast = PhysicsData.getDouble(map, "scattering_fast", fd.scatteringFast);
        fd.scatteringThermal = PhysicsData.getDouble(map, "scattering_thermal", fd.scatteringThermal);
        fd.moderation = PhysicsData.getDouble(map, "moderation", fd.moderation);
        fd.heatTransferCoeff = PhysicsData.getDouble(map, "heat_transfer_coeff", fd.heatTransferCoeff);
        fd.heatPerReaction = PhysicsData.getDouble(map, "heat_per_reaction", fd.heatPerReaction);
        fd.defaultDurability = PhysicsData.getInt(map, "default_durability", fd.defaultDurability);
        fd.maxTemperature = PhysicsData.getDouble(map, "max_temperature", fd.maxTemperature);
        fd.peakReactivityTemp = PhysicsData.getDouble(map, "peak_reactivity_temp", fd.peakReactivityTemp);
        fd.floorTemp = PhysicsData.getDouble(map, "floor_temp", fd.floorTemp);
        fd.spentProduct = (String) map.get("spent_product");

        Object curveObj = map.get("damage_curve");
        if (curveObj instanceof Map<?, ?> cMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cm = (Map<String, Object>) cMap;
            fd.peakReactivityMult = PhysicsData.getDouble(cm, "peak_reactivity_mult", fd.peakReactivityMult);
            fd.peakDamageFraction = PhysicsData.getDouble(cm, "peak_damage_fraction", fd.peakDamageFraction);
            fd.endReactivityMult = PhysicsData.getDouble(cm, "end_reactivity_mult", fd.endReactivityMult);
            fd.endDamageFraction = PhysicsData.getDouble(cm, "end_damage_fraction", fd.endDamageFraction);
        }

        return fd;
    }

    public int getEffectiveBaseNeutrons(PhysicsData physics) {
        double mult = (physics != null) ? physics.getArchetypeNbaseMultiplier(this.archetype) : 1.0;
        return Math.max(1, (int) Math.round(this.baseNeutrons * mult));
    }

    public double getEffectiveFissionMultiplier(PhysicsData physics) {
        double mult = (physics != null) ? physics.getArchetypeFissionMultiplier(this.archetype) : 1.0;
        return this.thermalFissionMultiplier * mult;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("archetype", archetype);
        m.put("matching", new ArrayList<>(matching));
        m.put("is_liquid", isLiquid);
        m.put("packet_count", packetCount);
        m.put("base_neutrons", baseNeutrons);
        m.put("thermal_fission_multiplier", thermalFissionMultiplier);
        m.put("fast_fission_multiplier", fastFissionMultiplier);
        m.put("absorption_fast", absorptionFast);
        m.put("absorption_thermal", absorptionThermal);
        m.put("scattering_fast", scatteringFast);
        m.put("scattering_thermal", scatteringThermal);
        m.put("moderation", moderation);
        m.put("heat_transfer_coeff", heatTransferCoeff);
        m.put("heat_per_reaction", heatPerReaction);
        m.put("default_durability", defaultDurability);
        m.put("max_temperature", maxTemperature);
        m.put("peak_reactivity_temp", peakReactivityTemp);
        m.put("floor_temp", floorTemp);
        if (spentProduct != null) m.put("spent_product", spentProduct);

        Map<String, Object> curve = new LinkedHashMap<>();
        curve.put("peak_reactivity_mult", peakReactivityMult);
        curve.put("peak_damage_fraction", peakDamageFraction);
        curve.put("end_reactivity_mult", endReactivityMult);
        curve.put("end_damage_fraction", endDamageFraction);
        m.put("damage_curve", curve);

        return m;
    }
}
