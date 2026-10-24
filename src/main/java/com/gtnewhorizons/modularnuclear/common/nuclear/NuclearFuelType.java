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

    GLOWSTONE("Glowstone", "breeder", 200.0, 2000.0, 0.60, 0.05, 80000, 1),
    LITHIUM("Lithium", "breeder", 390.0, 2100.0, 0.80, 0.05, 80000, 1),
    THORIUM("Thorium", "standard", 580.0, 2200.0, 1.00, 0.08, 400000, 2),
    URANIUM("Uranium", "standard", 770.0, 2300.0, 1.25, 0.10, 160000, 4),
    MOX("MOX", "standard", 960.0, 2400.0, 1.50, 0.15, 80000, 8),
    HD_URANIUM("High-Density Uranium", "dense", 1150.0, 2500.0, 1.80, 0.12, 560000, 8),
    HD_PLUTONIUM("High-Density Plutonium", "dense", 1350.0, 2600.0, 2.10, 0.20, 560000, 12),
    EXCITED_URANIUM("Excited Uranium", "excited", 1540.0, 2700.0, 2.50, 0.15, 48000, 16),
    EXCITED_PLUTONIUM("Excited Plutonium", "excited", 1730.0, 2800.0, 2.90, 0.25, 80000, 24),
    NAQUADAH("Naquadah", "dense", 1920.0, 2900.0, 3.40, 0.25, 800000, 16),
    NAQUADRIA("Naquadria", "dense", 2110.0, 3000.0, 4.00, 0.30, 800000, 20),
    TIBERIUM("Tiberium", "standard", 2300.0, 3100.0, 4.70, 0.20, 400000, 24),
    THE_CORE("The Core", "dense", 2500.0, 3200.0, 5.50, 0.50, 2560000, 512);

    public final String displayName;
    public final String archetype;
    public final double peakReactivityTemp;
    public final double floorTemp;
    public final double baseThermalFissionMultiplier;
    public final double baseFastFissionMultiplier;
    public final int defaultDurability;
    public final int baseNeutrons;
    public final double wearFactor;
    public final double damageK;
    public final double damageA;
    public final double temp10Percent;
    public final double damageRatio;

    NuclearFuelType(String displayName, String archetype, double peakReactivityTemp, double floorTemp,
        double baseThermalFissionMultiplier, double baseFastFissionMultiplier, int defaultDurability,
        int baseNeutrons) {
        this(
            displayName,
            archetype,
            peakReactivityTemp,
            floorTemp,
            baseThermalFissionMultiplier,
            baseFastFissionMultiplier,
            defaultDurability,
            baseNeutrons,
            FuelDamageCurveSolver.DEFAULT_T10_WEAR_FACTOR);
    }

    NuclearFuelType(String displayName, String archetype, double peakReactivityTemp, double floorTemp,
        double baseThermalFissionMultiplier, double baseFastFissionMultiplier, int defaultDurability, int baseNeutrons,
        double wearFactor) {
        this.displayName = displayName;
        this.archetype = archetype != null ? archetype : "standard";
        this.peakReactivityTemp = peakReactivityTemp;
        this.floorTemp = floorTemp;
        this.baseThermalFissionMultiplier = baseThermalFissionMultiplier;
        this.baseFastFissionMultiplier = baseFastFissionMultiplier;
        this.defaultDurability = defaultDurability;
        this.baseNeutrons = baseNeutrons;
        this.wearFactor = wearFactor > 1.0 ? wearFactor : FuelDamageCurveSolver.DEFAULT_T10_WEAR_FACTOR;

        FuelDamageCurveSolver.FuelCurveStats stats = FuelDamageCurveSolver
            .solve(null, peakReactivityTemp, floorTemp, 20.0, this.wearFactor);
        this.damageK = stats.k;
        this.damageA = stats.A;
        this.temp10Percent = stats.t10Percent;
        this.damageRatio = stats.damageRatioPercent;
    }

    public int getEffectiveBaseNeutrons(com.gtnewhorizons.modularnuclear.common.config.data.PhysicsData physics) {
        double mult = (physics != null) ? physics.getArchetypeNbaseMultiplier(this.archetype) : 1.0;
        return Math.max(1, (int) Math.round(this.baseNeutrons * mult));
    }

    public double getEffectiveFissionMultiplier(
        com.gtnewhorizons.modularnuclear.common.config.data.PhysicsData physics) {
        double mult = (physics != null) ? physics.getArchetypeFissionMultiplier(this.archetype) : 1.0;
        return this.baseThermalFissionMultiplier * mult;
    }

    public double getEffectiveFastFissionMultiplier(
        com.gtnewhorizons.modularnuclear.common.config.data.PhysicsData physics) {
        double mult = (physics != null) ? physics.getArchetypeFissionMultiplier(this.archetype) : 1.0;
        return this.baseFastFissionMultiplier * mult;
    }

    /**
     * Temperature durability damage following a slowly-growing zero-crossing-adjusted exponential curve.
     * Produces zero damage at or below ambient temperature (fixed to 20 °C).
     */
    public double calculateTemperatureDamage(double temp, double ambientTemp) {
        return calculateTemperatureDamage(temp);
    }

    public double calculateTemperatureDamage(double temp) {
        if (temp <= 20.0) {
            return 0.0;
        }
        return damageA * (Math.exp(damageK * (temp - 20.0)) - 1.0);
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

    public static NuclearFuelType fromFuelData(com.gtnewhorizons.modularnuclear.common.config.data.FuelData fd) {
        if (fd == null || fd.id == null) return null;
        return switch (fd.id) {
            case "fuel_glowstone" -> GLOWSTONE;
            case "fuel_lithium" -> LITHIUM;
            case "fuel_thorium_1", "fuel_thorium_2", "fuel_thorium_4", "fluid_fuel_thorium", "fluid_fuel_thorium_excited" -> THORIUM;
            case "fuel_uranium_1", "fuel_uranium_2", "fuel_uranium_4", "fluid_fuel_uranium", "fluid_fuel_u_hexafluoride" -> URANIUM;
            case "fuel_mox_1", "fuel_mox_2", "fuel_mox_4" -> MOX;
            case "fuel_hd_uranium_1", "fuel_hd_uranium_2", "fuel_hd_uranium_4" -> HD_URANIUM;
            case "fuel_hd_plutonium_1", "fuel_hd_plutonium_2", "fuel_hd_plutonium_4", "fluid_fuel_plutonium" -> HD_PLUTONIUM;
            case "fuel_excited_uranium_1", "fuel_excited_uranium_2", "fuel_excited_uranium_4", "fluid_fuel_uranium_excited" -> EXCITED_URANIUM;
            case "fuel_excited_plutonium_1", "fuel_excited_plutonium_2", "fuel_excited_plutonium_4", "fluid_fuel_plutonium_excited" -> EXCITED_PLUTONIUM;
            case "fuel_naquadah_1", "fuel_naquadah_2", "fuel_naquadah_4" -> NAQUADAH;
            case "fuel_naquadria_1", "fuel_naquadria_2", "fuel_naquadria_4" -> NAQUADRIA;
            case "fuel_tiberium_1", "fuel_tiberium_2", "fuel_tiberium_4" -> TIBERIUM;
            case "fuel_naquadah_32" -> THE_CORE;
            default -> null;
        };
    }

    /**
     * Resolves NuclearFuelType from fuel identifier, display name, enum name, or registry lookup.
     */
    public static NuclearFuelType fromName(String name) {
        if (name == null || name.isEmpty()) return null;
        if (NuclearSimulationEngine.configProvider != null) {
            com.gtnewhorizons.modularnuclear.common.config.data.FuelData fd = NuclearSimulationEngine.configProvider
                .findFuel(name);
            if (fd != null) {
                NuclearFuelType nft = fromFuelData(fd);
                if (nft != null) return nft;
            }
        }
        for (NuclearFuelType type : values()) {
            if (type.name()
                .equalsIgnoreCase(name) || type.displayName.equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null;
    }
}
