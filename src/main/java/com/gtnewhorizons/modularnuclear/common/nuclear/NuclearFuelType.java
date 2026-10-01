package com.gtnewhorizons.modularnuclear.common.nuclear;

/**
 * Fuel tiering progression and thermal neutron physics properties for GTNH nuclear reactor fuels.
 * Progression order:
 * 1. Glowstone
 * 2. Lithium
 * 3. Thorium
 * 4. Uranium
 * 5. MOX
 * 6. High-Density Uranium
 * 7. High-Density Plutonium
 * 8. Excited Uranium
 * 9. Excited Plutonium
 * 10. Naquadah
 * 11. Naquadria
 * 12. Tiberium
 * 13. The Core
 */
public enum NuclearFuelType {

    GLOWSTONE("Glowstone", 200.0, 2000.0, 0.60, 10000, 1),
    LITHIUM("Lithium", 390.0, 2100.0, 0.80, 10000, 1),
    THORIUM("Thorium", 580.0, 2200.0, 1.00, 50000, 2),
    URANIUM("Uranium", 770.0, 2300.0, 1.25, 20000, 4),
    MOX("MOX", 960.0, 2400.0, 1.50, 10000, 8),
    HD_URANIUM("High-Density Uranium", 1150.0, 2500.0, 1.80, 70000, 8),
    HD_PLUTONIUM("High-Density Plutonium", 1350.0, 2600.0, 2.10, 70000, 12),
    EXCITED_URANIUM("Excited Uranium", 1540.0, 2700.0, 2.50, 6000, 16),
    EXCITED_PLUTONIUM("Excited Plutonium", 1730.0, 2800.0, 2.90, 10000, 24),
    NAQUADAH("Naquadah", 1920.0, 2900.0, 3.40, 100000, 16),
    NAQUADRIA("Naquadria", 2110.0, 3000.0, 4.00, 100000, 20),
    TIBERIUM("Tiberium", 2300.0, 3100.0, 4.70, 50000, 24),
    THE_CORE("The Core", 2500.0, 3200.0, 5.50, 320000, 512);

    public final String displayName;
    public final double peakReactivityTemp;
    public final double floorTemp;
    public final double baseThermalFissionMultiplier;
    public final int defaultDurability;
    public final int baseNeutrons;

    NuclearFuelType(String displayName, double peakReactivityTemp, double floorTemp,
        double baseThermalFissionMultiplier, int defaultDurability, int baseNeutrons) {
        this.displayName = displayName;
        this.peakReactivityTemp = peakReactivityTemp;
        this.floorTemp = floorTemp;
        this.baseThermalFissionMultiplier = baseThermalFissionMultiplier;
        this.defaultDurability = defaultDurability;
        this.baseNeutrons = baseNeutrons;
    }

    /**
     * Reactivity curve: ramps smoothly up from 50% (0.50) at 0 °C to 100% (1.00) at peakReactivityTemp,
     * then reduces smoothly to 5% (0.05) at floorTemp, and stays at 5% (0.05) for all higher temperatures.
     * C^1 continuous everywhere.
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

    /**
     * Resolves NuclearFuelType from item or fluid unlocalized names.
     */
    public static NuclearFuelType fromName(String name) {
        if (name == null || name.isEmpty()) return null;
        String lower = name.toLowerCase();
        if (lower.contains("thecore") || lower.contains("naquadah32")
            || (lower.contains("naquadah") && lower.contains("32"))) {
            return THE_CORE;
        }
        if (lower.contains("glowstone")) return GLOWSTONE;
        if (lower.contains("lithium")) return LITHIUM;
        if (lower.contains("excited") && lower.contains("uranium")) return EXCITED_URANIUM;
        if (lower.contains("excited") && lower.contains("plutonium")) return EXCITED_PLUTONIUM;
        if ((lower.contains("highdensity") || lower.contains("high-density") || lower.contains("hd"))
            && lower.contains("uranium")) {
            return HD_URANIUM;
        }
        if ((lower.contains("highdensity") || lower.contains("high-density") || lower.contains("hd"))
            && lower.contains("plutonium")) {
            return HD_PLUTONIUM;
        }
        if (lower.contains("naquadria")) return NAQUADRIA;
        if (lower.contains("naquadah")) return NAQUADAH;
        if (lower.contains("tiberium")) return TIBERIUM;
        if (lower.contains("mox")) return MOX;
        if (lower.contains("thorium")) return THORIUM;
        if (lower.contains("uranium")) return URANIUM;
        return null;
    }
}
