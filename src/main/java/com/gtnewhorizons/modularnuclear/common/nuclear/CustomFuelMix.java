package com.gtnewhorizons.modularnuclear.common.nuclear;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
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
            case 1 -> 90; // 2.5 dusts
            case 2 -> 180; // 5.0 dusts
            default -> 360; // 10.0 dusts (Quad)
        };
    }

    public enum ActinideIsotope {

        THORIUM("Thorium", "Th-232", 580.0, 2200.0, 1.00, 0.08, 8, 400000),
        URANIUM_238("Uranium-238", "U-238", 673.8, 2250.0, 1.09, 0.09, 10, 174000),
        URANIUM_235("Uranium-235", "U-235", 1540.0, 2700.0, 2.50, 0.15, 64, 48000),
        PLUTONIUM_239("Plutonium-239", "Pu-239", 1350.0, 2600.0, 2.10, 0.20, 48, 200000),
        PLUTONIUM_241("Plutonium-241", "Pu-241", 1730.0, 2800.0, 2.90, 0.25, 96, 80000),
        URANIUM_233("Uranium-233", "U-233", 1150.0, 2500.0, 1.80, 0.12, 32, 320000),
        AMERICIUM("Americium", "Am-241", 1250.0, 2600.0, 1.90, 0.18, 40, 240000),
        CALIFORNIUM("Californium", "Cf-252", 1600.0, 2750.0, 2.60, 0.22, 80, 144000),
        NAQUADAH("Naquadah", "Nq", 1920.0, 2900.0, 3.40, 0.25, 64, 800000);

        public final String displayName;
        public final String symbol;
        public final double peakReactivityTemp;
        public final double floorTemp;
        public final double baseThermalFissionMultiplier;
        public final double baseFastFissionMultiplier;
        public final int baseNeutrons;
        public final int baseDurability;

        ActinideIsotope(String displayName, String symbol, double peakReactivityTemp, double floorTemp,
            double baseThermalFissionMultiplier, double baseFastFissionMultiplier, int baseNeutrons,
            int baseDurability) {
            this.displayName = displayName;
            this.symbol = symbol;
            this.peakReactivityTemp = peakReactivityTemp;
            this.floorTemp = floorTemp;
            this.baseThermalFissionMultiplier = baseThermalFissionMultiplier;
            this.baseFastFissionMultiplier = baseFastFissionMultiplier;
            this.baseNeutrons = baseNeutrons;
            this.baseDurability = baseDurability;
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

        private static final Map<String, ActinideIsotope> NAME_LOOKUP = new HashMap<>();

        static {
            for (ActinideIsotope iso : values()) {
                NAME_LOOKUP.put(
                    iso.name()
                        .toLowerCase(Locale.ENGLISH),
                    iso);
                NAME_LOOKUP.put(iso.displayName.toLowerCase(Locale.ENGLISH), iso);
                NAME_LOOKUP.put(iso.symbol.toLowerCase(Locale.ENGLISH), iso);
            }
            NAME_LOOKUP.put("uranium235", URANIUM_235);
            NAME_LOOKUP.put("u235", URANIUM_235);
            NAME_LOOKUP.put("u-235", URANIUM_235);
            NAME_LOOKUP.put("uranium-235", URANIUM_235);

            NAME_LOOKUP.put("uranium233", URANIUM_233);
            NAME_LOOKUP.put("u233", URANIUM_233);
            NAME_LOOKUP.put("u-233", URANIUM_233);
            NAME_LOOKUP.put("uranium-233", URANIUM_233);

            NAME_LOOKUP.put("uranium238", URANIUM_238);
            NAME_LOOKUP.put("u238", URANIUM_238);
            NAME_LOOKUP.put("u-238", URANIUM_238);
            NAME_LOOKUP.put("uranium-238", URANIUM_238);
            NAME_LOOKUP.put("uranium", URANIUM_238);

            NAME_LOOKUP.put("plutonium241", PLUTONIUM_241);
            NAME_LOOKUP.put("pu241", PLUTONIUM_241);
            NAME_LOOKUP.put("pu-241", PLUTONIUM_241);
            NAME_LOOKUP.put("plutonium-241", PLUTONIUM_241);

            NAME_LOOKUP.put("plutonium239", PLUTONIUM_239);
            NAME_LOOKUP.put("pu239", PLUTONIUM_239);
            NAME_LOOKUP.put("pu-239", PLUTONIUM_239);
            NAME_LOOKUP.put("plutonium-239", PLUTONIUM_239);
            NAME_LOOKUP.put("plutonium", PLUTONIUM_239);

            NAME_LOOKUP.put("thorium", THORIUM);
            NAME_LOOKUP.put("th232", THORIUM);
            NAME_LOOKUP.put("th-232", THORIUM);
            NAME_LOOKUP.put("thorium232", THORIUM);
            NAME_LOOKUP.put("thorium-232", THORIUM);

            NAME_LOOKUP.put("americium", AMERICIUM);
            NAME_LOOKUP.put("am241", AMERICIUM);
            NAME_LOOKUP.put("americium241", AMERICIUM);
            NAME_LOOKUP.put("americium-241", AMERICIUM);

            NAME_LOOKUP.put("californium", CALIFORNIUM);
            NAME_LOOKUP.put("cf252", CALIFORNIUM);
            NAME_LOOKUP.put("californium252", CALIFORNIUM);
            NAME_LOOKUP.put("californium-252", CALIFORNIUM);

            NAME_LOOKUP.put("naquadah", NAQUADAH);
            NAME_LOOKUP.put("naquadahenriched", NAQUADAH);
            NAME_LOOKUP.put("naquadah_enriched", NAQUADAH);
            NAME_LOOKUP.put("enrichednaquadah", NAQUADAH);
        }

        public static ActinideIsotope fromName(String name) {
            if (name == null) return null;
            return NAME_LOOKUP.get(
                name.trim()
                    .toLowerCase(Locale.ENGLISH));
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

    public CustomFuelMix(Map<ActinideIsotope, Integer> units) {
        int total = 0;
        for (ActinideIsotope iso : ActinideIsotope.values()) {
            Integer count = units.get(iso);
            if (count != null && count > 0) {
                total += count;
                isotopeUnits.put(iso, count);
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
        }

        this.peakReactivityTemp = Math.round(sumPeak * 10.0) / 10.0;
        this.floorTemp = Math.round(sumFloor * 10.0) / 10.0;
        this.baseThermalFissionMultiplier = Math.round(sumThermalMult * 100.0) / 100.0;
        this.baseFastFissionMultiplier = Math.round(sumFastMult * 100.0) / 100.0;
        this.baseNeutrons = Math.max(4, (int) Math.round(sumNeutrons));
        this.defaultDurability = Math.max(8000, (int) Math.round(sumDurability));

        FuelDamageCurveSolver.FuelCurveStats stats = FuelDamageCurveSolver
            .solve(null, this.peakReactivityTemp, this.floorTemp, 20.0, FuelDamageCurveSolver.DEFAULT_T10_WEAR_FACTOR);
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
