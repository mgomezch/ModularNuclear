package com.gtnewhorizons.modularnuclear.common.nuclear;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import gregtech.api.enums.Materials;

/**
 * Represents a dynamically compounded nuclear fuel mix.
 * <p>
 * In the Actinide Fuel Compounder, the player combines actinide dusts totaling exactly 10 dusts:
 * - 1 regular dust = 36 units
 * - 1 small dust = 9 units (1/4 dust)
 * - 1 tiny dust = 4 units (1/9 dust)
 * Total target = exactly 360 units.
 * <p>
 * The relative concentration of each actinide determines its fraction f_i in the custom mix.
 * Physical properties (peak reactivity temperature, floor temperature, thermal/fast fission multipliers,
 * base neutron yield, durability, and exponential damage coefficients) are calculated dynamically
 * from this composition.
 */
public class CustomFuelMix {

    public static final String NBT_TAG_ROOT = "CustomFuelMix";
    public static final int REQUIRED_TOTAL_UNITS = 360;

    public static int getRequiredUnits(int rodSize) {
        return switch (rodSize) {
            case 1 -> 90;   // 2.5 dusts
            case 2 -> 180;  // 5.0 dusts
            default -> 360; // 10.0 dusts (Quad)
        };
    }

    public enum ActinideIsotope {

        THORIUM("Thorium", "Th-232", 580.0, 2200.0, 1.00, 0.80, 16, 50000, false),
        URANIUM_238("Uranium-238", "U-238", 770.0, 2300.0, 1.10, 1.20, 16, 40000, false),
        URANIUM_235("Uranium-235", "U-235", 770.0, 2300.0, 1.60, 1.20, 24, 20000, true),
        PLUTONIUM_239("Plutonium-239", "Pu-239", 1100.0, 2500.0, 1.80, 1.60, 32, 20000, true),
        PLUTONIUM_241("Plutonium-241", "Pu-241", 1350.0, 2600.0, 2.20, 2.00, 40, 15000, true),
        URANIUM_233("Uranium-233", "U-233", 650.0, 2250.0, 1.50, 1.00, 24, 35000, true),
        AMERICIUM("Americium", "Am-241", 1200.0, 2550.0, 1.70, 1.50, 28, 25000, true),
        CALIFORNIUM("Californium", "Cf-252", 1600.0, 2700.0, 2.50, 2.20, 36, 18000, true),
        NAQUADAH("Naquadah", "Nq", 1920.0, 2900.0, 3.40, 2.80, 48, 80000, true);

        public final String displayName;
        public final String symbol;
        public final double peakReactivityTemp;
        public final double floorTemp;
        public final double baseThermalFissionMultiplier;
        public final double baseFastFissionMultiplier;
        public final int baseNeutrons;
        public final int baseDurability;
        public final boolean fissile;

        ActinideIsotope(String displayName, String symbol, double peakReactivityTemp, double floorTemp,
            double baseThermalFissionMultiplier, double baseFastFissionMultiplier, int baseNeutrons, int baseDurability,
            boolean fissile) {
            this.displayName = displayName;
            this.symbol = symbol;
            this.peakReactivityTemp = peakReactivityTemp;
            this.floorTemp = floorTemp;
            this.baseThermalFissionMultiplier = baseThermalFissionMultiplier;
            this.baseFastFissionMultiplier = baseFastFissionMultiplier;
            this.baseNeutrons = baseNeutrons;
            this.baseDurability = baseDurability;
            this.fissile = fissile;
        }

        public static ActinideIsotope fromMaterial(Materials mat) {
            if (mat == null) return null;
            if (mat == Materials.Thorium) return THORIUM;
            if (mat == Materials.Uranium) return URANIUM_238;
            if (mat == Materials.Uranium235) return URANIUM_235;
            if (mat == Materials.Plutonium) return PLUTONIUM_239;
            if (mat == Materials.Plutonium241) return PLUTONIUM_241;
            if (mat == Materials.Americium) return AMERICIUM;
            if (mat == Materials.Naquadah || mat == Materials.NaquadahEnriched) return NAQUADAH;
            if (mat.mName != null) {
                return fromName(mat.mName);
            }
            return null;
        }

        public static ActinideIsotope fromName(String name) {
            if (name == null) return null;
            String lower = name.toLowerCase();
            if (lower.contains("uranium235") || lower.contains("u235") || lower.contains("u-235")) return URANIUM_235;
            if (lower.contains("uranium233") || lower.contains("u233") || lower.contains("u-233")) return URANIUM_233;
            if (lower.contains("uranium238") || lower.contains("u238")
                || lower.contains("u-238")
                || lower.equals("uranium")) return URANIUM_238;
            if (lower.contains("plutonium241") || lower.contains("pu241") || lower.contains("pu-241"))
                return PLUTONIUM_241;
            if (lower.contains("plutonium239") || lower.contains("pu239")
                || lower.contains("pu-239")
                || lower.equals("plutonium")) return PLUTONIUM_239;
            if (lower.contains("thorium") || lower.contains("th232")) return THORIUM;
            if (lower.contains("americium")) return AMERICIUM;
            if (lower.contains("californium")) return CALIFORNIUM;
            if (lower.contains("naquadah")) return NAQUADAH;
            return null;
        }
    }

    private final Map<ActinideIsotope, Integer> isotopeUnits = new LinkedHashMap<>();
    private final Map<ActinideIsotope, Double> isotopeFractions = new LinkedHashMap<>();

    public final double peakReactivityTemp;
    public final double floorTemp;
    public final double baseThermalFissionMultiplier;
    public final double baseFastFissionMultiplier;
    public final int baseNeutrons;
    public final int defaultDurability;
    public final double damageK;
    public final double damageA;
    public final double temp10Percent;
    public final double damageRatio;
    public final double fissileFraction;

    public CustomFuelMix(Map<ActinideIsotope, Integer> units) {
        int total = 0;
        for (Map.Entry<ActinideIsotope, Integer> entry : units.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                total += entry.getValue();
                isotopeUnits.put(entry.getKey(), entry.getValue());
            }
        }
        if (total <= 0) {
            throw new IllegalArgumentException("Custom fuel mix must have at least one actinide unit");
        }

        double sumPeak = 0.0;
        double sumFloor = 0.0;
        double sumThermalMult = 0.0;
        double sumFastMult = 0.0;
        double sumNeutrons = 0.0;
        double sumDurability = 0.0;
        double sumFissile = 0.0;

        for (Map.Entry<ActinideIsotope, Integer> entry : isotopeUnits.entrySet()) {
            ActinideIsotope iso = entry.getKey();
            double frac = (double) entry.getValue() / (double) total;
            isotopeFractions.put(iso, frac);

            sumPeak += frac * iso.peakReactivityTemp;
            sumFloor += frac * iso.floorTemp;
            sumThermalMult += frac * iso.baseThermalFissionMultiplier;
            sumFastMult += frac * iso.baseFastFissionMultiplier;
            sumNeutrons += frac * iso.baseNeutrons;
            sumDurability += frac * iso.baseDurability;
            if (iso.fissile) {
                sumFissile += frac;
            }
        }

        this.peakReactivityTemp = Math.round(sumPeak * 10.0) / 10.0;
        this.floorTemp = Math.round(sumFloor * 10.0) / 10.0;
        this.baseThermalFissionMultiplier = Math.round(sumThermalMult * 100.0) / 100.0;
        this.baseFastFissionMultiplier = Math.round(sumFastMult * 100.0) / 100.0;
        this.baseNeutrons = Math.max(4, (int) Math.round(sumNeutrons));
        this.defaultDurability = Math.max(1000, (int) Math.round(sumDurability));
        this.fissileFraction = sumFissile;

        FuelDamageCurveSolver.FuelCurveStats stats = FuelDamageCurveSolver
            .solve(null, this.peakReactivityTemp, this.floorTemp, 20.0);
        this.damageK = stats.k;
        this.damageA = stats.A;
        this.temp10Percent = stats.t10Percent;
        this.damageRatio = stats.damageRatioPercent;
    }

    public Map<ActinideIsotope, Integer> getIsotopeUnits() {
        return Collections.unmodifiableMap(isotopeUnits);
    }

    public Map<ActinideIsotope, Double> getIsotopeFractions() {
        return Collections.unmodifiableMap(isotopeFractions);
    }

    public int getBaseNeutrons(int rodSize) {
        if (rodSize <= 1) {
            return Math.max(1, (int) Math.round(this.baseNeutrons * 0.25));
        } else if (rodSize == 2) {
            return Math.max(1, (int) Math.round(this.baseNeutrons * 0.50));
        }
        return this.baseNeutrons;
    }

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

    public double calculateTemperatureDamage(double temp, double ambientTemp) {
        if (temp <= ambientTemp) {
            return 0.0;
        }
        return damageA * (Math.exp(damageK * (temp - ambientTemp)) - 1.0);
    }

    public double calculateTemperatureDamage(double temp) {
        return calculateTemperatureDamage(temp, 20.0);
    }

    public void writeToNBT(NBTTagCompound nbt) {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound unitsTag = new NBTTagCompound();
        for (Map.Entry<ActinideIsotope, Integer> entry : isotopeUnits.entrySet()) {
            unitsTag.setInteger(
                entry.getKey()
                    .name(),
                entry.getValue());
        }
        root.setTag("Units", unitsTag);
        root.setDouble("PeakTemp", peakReactivityTemp);
        root.setDouble("FloorTemp", floorTemp);
        root.setDouble("ThermalMult", baseThermalFissionMultiplier);
        root.setDouble("FastMult", baseFastFissionMultiplier);
        root.setInteger("BaseNeutrons", baseNeutrons);
        root.setInteger("MaxDurability", defaultDurability);

        nbt.setTag(NBT_TAG_ROOT, root);
    }

    public static CustomFuelMix readFromNBT(NBTTagCompound nbt) {
        if (nbt == null || !nbt.hasKey(NBT_TAG_ROOT)) {
            return null;
        }
        NBTTagCompound root = nbt.getCompoundTag(NBT_TAG_ROOT);
        NBTTagCompound unitsTag = root.getCompoundTag("Units");
        Map<ActinideIsotope, Integer> units = new LinkedHashMap<>();
        for (ActinideIsotope iso : ActinideIsotope.values()) {
            if (unitsTag.hasKey(iso.name())) {
                units.put(iso, unitsTag.getInteger(iso.name()));
            }
        }
        if (units.isEmpty()) {
            return null;
        }
        return new CustomFuelMix(units);
    }

    public static boolean hasCustomMix(ItemStack stack) {
        if (stack == null) return false;
        NBTTagCompound nbt = stack.getTagCompound();
        return nbt != null && nbt.hasKey(NBT_TAG_ROOT);
    }

    public static CustomFuelMix fromStack(ItemStack stack) {
        if (stack == null) return null;
        return readFromNBT(stack.getTagCompound());
    }
}
