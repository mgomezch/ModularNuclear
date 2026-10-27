package com.gtnewhorizons.modularnuclear.common.nuclear.standalone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.gtnewhorizons.coolantloops.common.config.CoolantLoopConfigData;
import com.gtnewhorizons.coolantloops.common.config.data.CoolantDeviceData.PHEData;
import com.gtnewhorizons.coolantloops.common.config.data.CoolantDeviceData.PHEData.SecondaryCoolantEntry;
import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;
import com.gtnewhorizons.coolantloops.engine.CoolantLoopEngine;
import com.gtnewhorizons.coolantloops.engine.CoolantPipeSpec;
import com.gtnewhorizons.coolantloops.engine.LoopSegment;
import com.gtnewhorizons.coolantloops.engine.PheExchange;
import com.gtnewhorizons.modularnuclear.common.nuclear.CoolantPassage;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.WasmStrings;

/**
 * A reactor's convective coolant loops in the standalone simulator. The passages are split among independent
 * circuits (layout codes CP, CP2 ... CP9), each with its own coolant, pump, PHEs and pump control. The hydraulics
 * and heat transport run on the game's own {@link CoolantLoopEngine}, the passages exchange heat through
 * {@link CoolantPassage} and the heat exchangers through {@link PheExchange}, all configured from the same coolant
 * loop config as the game, on the loop the game's crawler would build for a pump feeding its passages in parallel.
 */
public class CoolantLoopModel {

    public enum CoolingMode {

        MODULAR("Modular (per-cell cooling)"),
        CONDUCTIVE("Conductive (sub-boiling hatches)"),
        CONVECTIVE_LOOP("Convective (closed coolant loop)");

        public final String displayName;

        CoolingMode(String displayName) {
            this.displayName = displayName;
        }

        public static CoolingMode fromString(String str) {
            if (str == null) return MODULAR;
            String s = WasmStrings.normalizeToken(str, " ");
            switch (s) {
                case "MODULAR", "PER_CELL", "CELL":
                    return MODULAR;
                case "CONDUCTIVE", "SUB_BOILING", "HATCH":
                    return CONDUCTIVE;
                case "CONVECTIVE_LOOP", "CONVECTIVE", "LOOP", "CLOSED_LOOP":
                    return CONVECTIVE_LOOP;
            }
            for (CoolingMode mode : values()) {
                if (mode.name()
                    .equalsIgnoreCase(s) || mode.displayName.equalsIgnoreCase(s)) {
                    return mode;
                }
            }
            return MODULAR;
        }
    }

    /**
     * Zero-dependency WebAssembly bridge representation of GregTech energy hatch voltage tiers.
     * <p>
     * This enum mirrors GregTech's {@code GTValues.V} and {@code VoltageIndex} (LV through UHV)
     * while remaining strictly isolated from the Forge and GregTech runtime classpath. This isolation
     * ensures that TeaVM can compile the standalone simulation engine into WebAssembly for browser
     * execution without pulling in Minecraft, Forge, or GregTech classes.
     * </p>
     */
    public enum EnergyHatchTier {

        LV(1, "LV", 32.0),
        MV(2, "MV", 128.0),
        HV(3, "HV", 512.0),
        EV(4, "EV", 2048.0),
        IV(5, "IV", 8192.0),
        LUV(6, "LuV", 32768.0),
        ZPM(7, "ZPM", 131072.0),
        UV(8, "UV", 524288.0),
        UHV(9, "UHV", 2097152.0);

        public final int tier;
        public final String displayName;
        public final double voltageEU;

        EnergyHatchTier(int tier, String displayName, double voltageEU) {
            this.tier = tier;
            this.displayName = displayName;
            this.voltageEU = voltageEU;
        }

        public static EnergyHatchTier fromTier(int tier) {
            return switch (tier) {
                case 1 -> LV;
                case 2 -> MV;
                case 3 -> HV;
                case 4 -> EV;
                case 5 -> IV;
                case 6 -> LUV;
                case 7 -> ZPM;
                case 8 -> UV;
                case 9 -> UHV;
                default -> tier < 1 ? LV : UHV;
            };
        }

        public static EnergyHatchTier fromString(String str) {
            if (str == null) return EV;
            String s = WasmStrings.normalizeToken(str, " ");
            switch (s) {
                case "1", "LV", "TIER_LV", "ENERGY_HATCH_LV":
                    return LV;
                case "2", "MV", "TIER_MV", "ENERGY_HATCH_MV":
                    return MV;
                case "3", "HV", "TIER_HV", "ENERGY_HATCH_HV":
                    return HV;
                case "4", "EV", "TIER_EV", "ENERGY_HATCH_EV":
                    return EV;
                case "5", "IV", "TIER_IV", "ENERGY_HATCH_IV":
                    return IV;
                case "6", "LUV", "TIER_LUV", "ENERGY_HATCH_LUV":
                    return LUV;
                case "7", "ZPM", "TIER_ZPM", "ENERGY_HATCH_ZPM":
                    return ZPM;
                case "8", "UV", "TIER_UV", "ENERGY_HATCH_UV":
                    return UV;
                case "9", "UHV", "TIER_UHV", "ENERGY_HATCH_UHV":
                    return UHV;
            }
            for (EnergyHatchTier tier : values()) {
                if (tier.name()
                    .equalsIgnoreCase(s) || tier.displayName.equalsIgnoreCase(s)) {
                    return tier;
                }
            }
            return EV;
        }
    }

    /**
     * Immutable 2D reactor grid coordinate used for attached core points.
     */
    public static final class GridCoord {

        public final int x;
        public final int y;

        public GridCoord(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public static GridCoord of(int x, int y) {
            return new GridCoord(x, y);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GridCoord that = (GridCoord) o;
            return x == that.x && y == that.y;
        }

        @Override
        public int hashCode() {
            return (x * 31) ^ y;
        }

        @Override
        public String toString() {
            return x + "," + y;
        }
    }

    /** Pipe material of the loop (the coolant pipes the CoolantLoops mod registers). */
    public enum LoopMaterial {

        CAST_IRON("Cast Iron", "CastIron", "ULV"),
        STEEL("Steel", "Steel", "LV"),
        STAINLESS_STEEL("Stainless Steel", "StainlessSteel", "MV"),
        TITANIUM("Titanium", "Titanium", "EV"),
        TUNGSTENSTEEL("Tungstensteel", "TungstenSteel", "IV"),
        OSMIUM("Osmium", "Osmium", "LuV"),
        NEUTRONIUM("Neutronium", "Neutronium", "ZPM");

        public final String displayName;
        public final String materialName;
        public final String tierUnlocked;

        LoopMaterial(String displayName, String materialName, String tierUnlocked) {
            this.displayName = displayName;
            this.materialName = materialName;
            this.tierUnlocked = tierUnlocked;
        }

        /** Burst pressure from the coolant loop pipe config. */
        public double maxPressureBar() {
            return CoolantLoopConfigData.getPipePressureRating(materialName);
        }

        /** Highest coolant temperature the pipe takes, from the coolant loop pipe config. */
        public double maxTemperatureCelsius() {
            return CoolantLoopConfigData.getPipeTemperatureLimit(materialName);
        }

        /** Throughput of one pipe of this material and size, in L/s. */
        public double capacityLitersPerSecond(LoopPipeSize size) {
            return CoolantPipeSpec.capacityLitersPerSecond(CoolantPipeSpec.materialIndex(materialName), size.ordinal());
        }

        public boolean isAllowedInReactorTier(int reactorTier) {
            return reactorTier >= NuclearSimulationEngine.PIPE_TIER_OSMIUM;
        }

        public boolean isProgressionAppropriate(int reactorTier) {
            if (reactorTier < NuclearSimulationEngine.PIPE_TIER_OSMIUM) {
                return false;
            }
            if (reactorTier <= NuclearSimulationEngine.PIPE_TIER_OSMIUM) {
                // Progression filter: exclude Neutronium from automated LuV search space
                return this != NEUTRONIUM;
            }
            return true;
        }

        public static LoopMaterial fromMaterialName(String name) {
            if (name == null) return TITANIUM;
            for (LoopMaterial mat : values()) {
                if (mat.materialName.equalsIgnoreCase(name)) return mat;
            }
            return fromString(name);
        }

        public static LoopMaterial fromString(String str) {
            if (str == null) return TITANIUM;
            String s = WasmStrings.normalizeToken(str, " -");
            switch (s) {
                case "CAST_IRON", "CASTIRON", "CAST", "IRON":
                    return CAST_IRON;
                case "STEEL":
                    return STEEL;
                case "STAINLESS_STEEL", "STAINLESS", "STAINLESSSTEEL":
                    return STAINLESS_STEEL;
                case "TITANIUM", "TITAN":
                    return TITANIUM;
                case "TUNGSTENSTEEL", "TUNGSTEN_STEEL", "TUNGSTEN":
                    return TUNGSTENSTEEL;
                case "OSMIUM":
                    return OSMIUM;
                case "NEUTRONIUM", "NEUTRON":
                    return NEUTRONIUM;
            }
            for (LoopMaterial mat : values()) {
                if (mat.name()
                    .equalsIgnoreCase(s) || mat.displayName.equalsIgnoreCase(s)
                    || mat.materialName.equalsIgnoreCase(s)) {
                    return mat;
                }
            }
            return TITANIUM;
        }
    }

    /** Pipe size, in the order {@link CoolantPipeSpec} lists them. */
    public enum LoopPipeSize {

        TINY("Tiny"),
        SMALL("Small"),
        NORMAL("Normal"),
        LARGE("Large"),
        HUGE("Huge");

        public final String displayName;

        LoopPipeSize(String displayName) {
            this.displayName = displayName;
        }

        /** Inner diameter, from the coolant loop pipe config. */
        public double diameterMeters() {
            return CoolantLoopConfigData.getDiameterForThickness(CoolantPipeSpec.THICKNESSES[ordinal()]);
        }

        public static LoopPipeSize fromString(String str) {
            if (str == null) return NORMAL;
            String s = WasmStrings.normalizeToken(str, " ");
            switch (s) {
                case "TINY", "PIPE_TINY", "0.05":
                    return TINY;
                case "SMALL", "PIPE_SMALL", "0.075":
                    return SMALL;
                case "NORMAL", "MEDIUM", "REGULAR", "PIPE_NORMAL", "0.1", "0.10":
                    return NORMAL;
                case "LARGE", "PIPE_LARGE", "0.15":
                    return LARGE;
                case "HUGE", "PIPE_HUGE", "0.2", "0.20":
                    return HUGE;
            }
            for (LoopPipeSize size : values()) {
                if (size.name()
                    .equalsIgnoreCase(s) || size.displayName.equalsIgnoreCase(s)) {
                    return size;
                }
            }
            return NORMAL;
        }
    }

    /** Loop coolant; its physical properties come from the coolant loop fluid config. */
    public enum CoolantFluidType {

        DISTILLED_WATER("Distilled Water", "ic2distilledwater"),
        HEAVY_WATER("Heavy Water", "heavywater"),
        IC2_COOLANT("IC2 Coolant", "ic2coolant"),
        SODIUM("Sodium", "sodium"),
        MOLTEN_LEAD("Molten Lead", "molten.lead"),
        MOLTEN_CHEESE("Molten Cheese", "molten.cheese"),
        LAVA("Lava", "lava");

        public final String displayName;
        public final String fluidName;

        CoolantFluidType(String displayName, String fluidName) {
            this.displayName = displayName;
            this.fluidName = fluidName;
        }

        public CoolantFluidProperty property() {
            CoolantLoopConfigData.ensureLoaded();
            CoolantFluidProperty prop = CoolantFluidProperty.get(fluidName);
            if (prop == null) prop = CoolantFluidProperty.getCanonical(fluidName);
            return prop != null ? prop : CoolantFluidProperty.DISTILLED_WATER;
        }

        public static CoolantFluidType fromFluidName(String fluidName) {
            if (fluidName == null) return DISTILLED_WATER;
            for (CoolantFluidType f : values()) {
                if (f.fluidName.equalsIgnoreCase(fluidName)) return f;
            }
            return fromString(fluidName);
        }

        public static CoolantFluidType fromString(String str) {
            if (str == null) return DISTILLED_WATER;
            String s = WasmStrings.normalizeToken(str, " .");
            switch (s) {
                case "DISTILLED_WATER", "DISTILLEDWATER", "IC2DISTILLEDWATER", "WATER":
                    return DISTILLED_WATER;
                case "HEAVY_WATER", "HEAVYWATER":
                    return HEAVY_WATER;
                case "IC2_COOLANT", "IC2COOLANT", "COOLANT":
                    return IC2_COOLANT;
                case "SODIUM", "MOLTEN_SODIUM", "MOLTENSODIUM":
                    return SODIUM;
                case "LEAD", "MOLTEN_LEAD", "MOLTENLEAD":
                    return MOLTEN_LEAD;
                case "LAVA":
                    return LAVA;
                case "MOLTEN_CHEESE", "MOLTENCHEESE", "CHEESE":
                    return MOLTEN_CHEESE;
            }
            for (CoolantFluidType fluid : values()) {
                if (fluid.name()
                    .equalsIgnoreCase(s) || fluid.displayName.equalsIgnoreCase(s)
                    || fluid.fluidName.equalsIgnoreCase(s)) {
                    return fluid;
                }
            }
            return DISTILLED_WATER;
        }
    }

    /** Turbine rotor size installed in the pump, with GregTech's base damage for that rotor tool. */
    public enum RotorSize {

        SMALL("Small", 0.0),
        NORMAL("Normal", 2.5),
        LARGE("Large", 5.0),
        HUGE("Huge", 7.5);

        public final String displayName;
        public final double baseDamage;

        RotorSize(String displayName, double baseDamage) {
            this.displayName = displayName;
            this.baseDamage = baseDamage;
        }

        public static RotorSize fromString(String str) {
            if (str == null) return HUGE;
            for (RotorSize size : values()) {
                if (size.name()
                    .equalsIgnoreCase(str.trim()) || size.displayName.equalsIgnoreCase(str.trim())) {
                    return size;
                }
            }
            return HUGE;
        }
    }

    /** Turbine rotor material installed in the pump, with its GregTech tool quality. */
    public enum RotorMaterial {

        STEEL("Steel", 3),
        STAINLESS_STEEL("Stainless Steel", 4),
        TITANIUM("Titanium", 3),
        TUNGSTENSTEEL("Tungstensteel", 4),
        OSMIUM("Osmium", 4),
        NAQUADAH("Naquadah", 4),
        NAQUADAH_ALLOY("Naquadah Alloy", 5),
        ORIHARUKON("Oriharukon", 5),
        NEUTRONIUM("Neutronium", 6),
        TRITANIUM("Tritanium", 6),
        HSS_E("HSS-E", 7),
        HSS_S("HSS-S", 8),
        TRINIUM("Trinium", 8),
        BEDROCKIUM("Bedrockium", 9),
        ADAMANTIUM("Adamantium", 10),
        DURANIUM("Duranium", 11),
        ICHORIUM("Ichorium", 12),
        COSMIC_NEUTRONIUM("Cosmic Neutronium", 12),
        INFINITY("Infinity", 17);

        public final String displayName;
        public final int toolQuality;

        RotorMaterial(String displayName, int toolQuality) {
            this.displayName = displayName;
            this.toolQuality = toolQuality;
        }

        public static RotorMaterial fromString(String str) {
            if (str == null) return TUNGSTENSTEEL;
            String s = WasmStrings.normalizeToken(str, " -");
            if (s.equals("ORINARUKON")) return ORIHARUKON;
            for (RotorMaterial mat : values()) {
                if (WasmStrings.normalizeToken(mat.name(), "_")
                    .equals(WasmStrings.normalizeToken(s, "_"))
                    || WasmStrings.normalizeToken(mat.displayName, " -")
                        .equals(s)) {
                    return mat;
                }
            }
            return TUNGSTENSTEEL;
        }
    }

    /**
     * Efficiency of a pump rotor: GregTech's turbine base efficiency, 0.5 + (0.5 + rotor damage) / 10, where the
     * damage is the rotor tool's base damage plus its material's tool quality.
     */
    public static double rotorEfficiency(RotorMaterial material, RotorSize size) {
        return 0.5 + (0.5 + size.baseDamage + material.toolQuality) * 0.1;
    }

    /** Secondary fluids a PHE can boil, one per product, from the PHE config. */
    public static List<SecondaryCoolantEntry> secondaryCoolantChoices() {
        List<SecondaryCoolantEntry> out = new ArrayList<>();
        Set<String> products = new HashSet<>();
        for (SecondaryCoolantEntry e : pheConfig().secondaryCoolants) {
            if (e != null && e.inputFluidName != null && products.add(e.outputGasName + "@" + e.boilingPointCelsius)) {
                out.add(e);
            }
        }
        return out;
    }

    private static PHEData pheConfig() {
        return CoolantLoopConfigData.getDeviceData()
            .getPhe();
    }

    /** Pipe blocks in each of the cold leg, hot leg and return run of the canonical loop. */
    public static final int PIPE_RUN_BLOCKS = 4;
    /** Minor loss of the distribution and collection manifolds (the crawler's minimum per manifold group). */
    private static final double MANIFOLD_MINOR_LOSS_K = 0.5;
    /** Most independent loop circuits a reactor's passages can be split among (layout codes CP, CP2 ... CP9). */
    public static final int MAX_CIRCUITS = 9;

    /** What a circuit's temperature controller reads. */
    public enum TempSensor {

        PASSAGE("Hottest passage"),
        COLDEST_PASSAGE("Coldest passage"),
        HOT_LEG("Hot leg coolant");

        public final String displayName;

        TempSensor(String displayName) {
            this.displayName = displayName;
        }

        public static TempSensor fromString(String str) {
            if (str == null) return PASSAGE;
            String s = WasmStrings.normalizeToken(str, " -");
            for (TempSensor t : values()) {
                if (t.name()
                    .equals(s)
                    || WasmStrings.normalizeToken(t.displayName, " -")
                        .equals(s)) {
                    return t;
                }
            }
            if (s.equals("COLDEST") || s.equals("COLD")) return COLDEST_PASSAGE;
            return s.equals("HOT") || s.equals("HOTLEG") || s.equals("COOLANT") ? HOT_LEG : PASSAGE;
        }
    }

    /**
     * One coolant loop design and the passages on it: pipes, coolant, pump, PHEs, and the pump control a player
     * builds with a machine controller cover and redstone (duty cycle, flow and pressure caps, a temperature band).
     * It is built {@code copies} times, each copy with its own pump and PHEs, splitting the circuit's passages.
     */
    public static final class Circuit {

        private LoopMaterial material = LoopMaterial.TITANIUM;
        private LoopPipeSize pipeSize = LoopPipeSize.NORMAL;
        private CoolantFluidType fluidType = CoolantFluidType.DISTILLED_WATER;
        private String secondaryFluidName = "ic2distilledwater";
        private EnergyHatchTier hatchTier = EnergyHatchTier.EV;
        private int pumpHatchCount = 1;
        private RotorMaterial rotorMaterial = RotorMaterial.TUNGSTENSTEEL;
        private RotorSize rotorSize = RotorSize.HUGE;
        private int pheCount = 1;
        private EnergyHatchTier pheHatchTier = EnergyHatchTier.EV;
        private int pheHatchCount = 1;
        private int copies = 1;
        private double dutyCyclePercent = 100.0;
        private double maxFlowLPerSec = 0.0; // 0 = no cap
        private double maxPressureBar = 0.0; // 0 = no cap
        private double controlMinTempC = Double.NaN; // NaN = no temperature band
        private double controlMaxTempC = Double.NaN;
        private TempSensor sensor = TempSensor.PASSAGE;
        private double fillTempOverrideC = Double.NaN; // NaN = the game's fill temperature
        private final Set<GridCoord> attachedPoints = new HashSet<>();

        private final List<Loop> loops = new ArrayList<>();
        private boolean dirty = true;
        private CoolantFluidType builtFluid = null;

        // Last cycle
        private double lastHeatJoules = 0.0;
        private double lastHeatHut = 0.0;
        private double lastSteamLt = 0.0;
        private double lastSuperheatedSteamLt = 0.0;
        private double lastSupercriticalSteamLt = 0.0;
        private double lastGasLt = 0.0;
        private double lastBoiledLt = 0.0;
        private double lastPumpPowerEUt = 0.0;
        private double lastPhePowerEUt = 0.0;
        private double lastPumpOnFraction = 0.0;
        private String limitReason = "";
        private int passageCount = 0;

        private void markDirty() {
            dirty = true;
        }

        // ---- Electrical draw and capacity (as the game's pump and PHE compute them) ----

        /** EU/t one copy's pump draws while running (512 without energy hatches, as in the game). */
        public double pumpPowerEUt() {
            return pumpHatchCount <= 0 ? 512.0 : hatchVoltage(hatchTier, pumpHatchCount);
        }

        /** Voltage each PHE runs at (32 without energy hatches, as in the game). */
        public double pheVoltage() {
            return pheHatchCount <= 0 ? 32.0 : hatchVoltage(pheHatchTier, pheHatchCount);
        }

        /** EU/t one copy's PHEs draw together (15/16 of their voltage each, every tick). */
        public double phePowerEUt() {
            return pheCount * Math.max(1.0, Math.floor(15.0 * pheVoltage() / 16.0));
        }

        public double getRotorEfficiency() {
            return rotorEfficiency(rotorMaterial, rotorSize);
        }

        /** Mechanical power a running pump puts into the coolant, in W. */
        public double pumpMechanicalPowerWatts() {
            return pumpPowerEUt() * CoolantLoopConfigData.getPhysicsData()
                .getPumpMechanicalPowerPerEU() * getRotorEfficiency();
        }

        /** Most flow the loop's pipes carry, in L/s. */
        public double maxFlowLitersPerSecond() {
            return material.capacityLitersPerSecond(pipeSize);
        }

        /**
         * Temperature the coolant enters the loop at: ambient, or for a molten fluid its declared melting point if
         * that is higher (as the game's pump fills it).
         */
        public double fillTemperatureCelsius() {
            if (!Double.isNaN(fillTempOverrideC)) return fillTempOverrideC;
            CoolantFluidProperty prop = fluidType.property();
            double ambient = NuclearSimulationEngine.ambientTemp;
            return prop.isMolten() ? Math.max(ambient, prop.getDeclaredTemperatureCelsius()) : ambient;
        }

        public double getFillTempOverrideC() {
            return fillTempOverrideC;
        }

        /**
         * Simulator-only experiment: the coolant enters at this temperature instead of the game's fill temperature (as
         * if the loop were preheated; NaN restores the game's).
         */
        public void setFillTempOverrideC(double temp) {
            if (Double.compare(temp, fillTempOverrideC) != 0) {
                fillTempOverrideC = temp;
                builtFluid = null;
                markDirty();
            }
        }

        public boolean hasTemperatureBand() {
            return !Double.isNaN(controlMinTempC) && !Double.isNaN(controlMaxTempC);
        }

        // ---- Configuration ----

        public LoopMaterial getMaterial() {
            return material;
        }

        public void setMaterial(LoopMaterial m) {
            if (m != null && m != material) {
                material = m;
                markDirty();
            }
        }

        public LoopPipeSize getPipeSize() {
            return pipeSize;
        }

        public void setPipeSize(LoopPipeSize s) {
            if (s != null && s != pipeSize) {
                pipeSize = s;
                markDirty();
            }
        }

        public CoolantFluidType getFluidType() {
            return fluidType;
        }

        public void setFluidType(CoolantFluidType f) {
            if (f != null && f != fluidType) {
                fluidType = f;
                markDirty();
            }
        }

        /** The PHE secondary fluid's config entry, or null if the PHE config does not accept it. */
        public SecondaryCoolantEntry getSecondaryCoolant() {
            return pheConfig().findSecondaryCoolant(secondaryFluidName);
        }

        public String getSecondaryFluidName() {
            return secondaryFluidName;
        }

        public void setSecondaryCoolant(String fluidName) {
            if (fluidName != null && pheConfig().findSecondaryCoolant(fluidName) != null) {
                secondaryFluidName = fluidName;
            }
        }

        public EnergyHatchTier getHatchTier() {
            return hatchTier;
        }

        public void setHatchTier(EnergyHatchTier t) {
            if (t != null) hatchTier = t;
        }

        public int getPumpHatchCount() {
            return pumpHatchCount;
        }

        /** Energy hatches on the pump: one draws the tier's voltage, two or more draw four times it. */
        public void setPumpHatchCount(int count) {
            pumpHatchCount = Math.max(0, count);
        }

        public RotorMaterial getRotorMaterial() {
            return rotorMaterial;
        }

        public void setRotorMaterial(RotorMaterial m) {
            if (m != null) rotorMaterial = m;
        }

        public RotorSize getRotorSize() {
            return rotorSize;
        }

        public void setRotorSize(RotorSize s) {
            if (s != null) rotorSize = s;
        }

        public int getPheCount() {
            return pheCount;
        }

        public void setPheCount(int count) {
            int c = Math.max(1, count);
            if (c != pheCount) {
                pheCount = c;
                markDirty();
            }
        }

        public EnergyHatchTier getPheHatchTier() {
            return pheHatchTier;
        }

        public void setPheHatchTier(EnergyHatchTier t) {
            if (t != null) pheHatchTier = t;
        }

        public int getPheHatchCount() {
            return pheHatchCount;
        }

        public void setPheHatchCount(int count) {
            pheHatchCount = Math.max(0, count);
        }

        public int getCopies() {
            return copies;
        }

        /** Identical copies of this loop (each with its own pump and PHEs) the circuit's passages are split among. */
        public void setCopies(int count) {
            int c = Math.max(1, count);
            if (c != copies) {
                copies = c;
                markDirty();
            }
        }

        public double getDutyCyclePercent() {
            return dutyCyclePercent;
        }

        /** Share of ticks the pump is switched on (a pulsed redstone signal on a machine controller cover). */
        public void setDutyCyclePercent(double percent) {
            dutyCyclePercent = Math.max(0.0, Math.min(100.0, percent));
        }

        public double getMaxFlowLPerSec() {
            return maxFlowLPerSec;
        }

        /** Switches the pump off while the loop flow is at or above this (0 for no cap). */
        public void setMaxFlowLPerSec(double flow) {
            maxFlowLPerSec = Math.max(0.0, flow);
        }

        public double getMaxPressureBar() {
            return maxPressureBar;
        }

        /** Switches the pump off while the loop's peak pressure is at or above this (0 for no cap). */
        public void setMaxPressureBar(double bar) {
            maxPressureBar = Math.max(0.0, bar);
        }

        public double getControlMinTempC() {
            return controlMinTempC;
        }

        public double getControlMaxTempC() {
            return controlMaxTempC;
        }

        /**
         * Temperature band: the pump switches on when the sensed temperature rises above max and off when it falls
         * below min (NaN for either turns the band off).
         */
        public void setTemperatureBand(double minC, double maxC) {
            if (Double.isNaN(minC) || Double.isNaN(maxC)) {
                controlMinTempC = Double.NaN;
                controlMaxTempC = Double.NaN;
            } else {
                controlMinTempC = Math.min(minC, maxC);
                controlMaxTempC = Math.max(minC, maxC);
            }
        }

        public TempSensor getSensor() {
            return sensor;
        }

        public void setSensor(TempSensor s) {
            if (s != null) sensor = s;
        }

        public Set<GridCoord> getAttachedPoints() {
            return Collections.unmodifiableSet(attachedPoints);
        }

        // ---- State ----

        public int getPassageCount() {
            return passageCount;
        }

        /** Flow in each copy (L/s); the copies are identical, so this is their average. */
        public double getCurrentFlowRateLPerSec() {
            double sum = 0.0;
            for (Loop l : loops) sum += l.engine.getFlowRateLitersPerSecond();
            return loops.isEmpty() ? 0.0 : sum / loops.size();
        }

        /** Highest pressure in any copy. */
        public double getCurrentPressureBar() {
            double max = 1.0;
            for (Loop l : loops) max = Math.max(max, l.engine.getPeakLoopPressureBar());
            return max;
        }

        /** Coolant temperature leaving the reactor (hot leg), averaged over the copies. */
        public double getHotLegTempCelsius() {
            double sum = 0.0;
            for (Loop l : loops) sum += l.hotLegSegment.getCurrentTemperatureCelsius();
            return loops.isEmpty() ? fillTemperatureCelsius() : sum / loops.size();
        }

        /** Coolant temperature entering the reactor (cold leg), averaged over the copies. */
        public double getColdLegTempCelsius() {
            double sum = 0.0;
            for (Loop l : loops) sum += l.coldLegSegment.getCurrentTemperatureCelsius();
            return loops.isEmpty() ? fillTemperatureCelsius() : sum / loops.size();
        }

        public double getPeakLoopTempCelsius() {
            double max = -Double.MAX_VALUE;
            for (Loop l : loops) max = Math.max(max, l.engine.getPeakLoopTempCelsius());
            return loops.isEmpty() ? fillTemperatureCelsius() : max;
        }

        public double getLastHeatExtractedHut() {
            return lastHeatHut;
        }

        public double getLastSteamLt() {
            return lastSteamLt;
        }

        public double getLastSuperheatedSteamLt() {
            return lastSuperheatedSteamLt;
        }

        public double getLastSupercriticalSteamLt() {
            return lastSupercriticalSteamLt;
        }

        public double getLastSecondaryGasLt() {
            return lastGasLt;
        }

        /** EU/t the copies' pumps drew on average over the last cycle. */
        public double getLastPumpPowerEUt() {
            return lastPumpPowerEUt;
        }

        public double getLastPhePowerEUt() {
            return lastPhePowerEUt;
        }

        /** Share of ticks the pumps ran over the last cycle. */
        public double getLastPumpOnFraction() {
            return lastPumpOnFraction;
        }

        /** What last switched a pump off ("", "duty cycle", "flow cap", "pressure cap" or "temperature band"). */
        public String getLimitReason() {
            return limitReason;
        }
    }

    /** One built copy of a circuit: its loop engine, segments, PHE batches and pump control state. */
    private static final class Loop {

        CoolantLoopEngine engine;
        final List<SimTile> passageTiles = new ArrayList<>();
        final List<LoopSegment> passageSegments = new ArrayList<>();
        final List<LoopSegment> pheSegments = new ArrayList<>();
        final List<PheExchange.Batch> pheBatches = new ArrayList<>();
        LoopSegment hotLegSegment;
        LoopSegment coldLegSegment;
        int[] radiolysisAccumulators = new int[0];
        double dutyAccumulator = 0.0;
        boolean bandOn = true;
    }

    private final List<Circuit> circuits = new ArrayList<>();
    private int selected = 0;

    // Last cycle, all circuits
    private double lastHeatExtractedWatts = 0.0;
    private double lastHeatExtractedHut = 0.0;
    private double lastSteamLt = 0.0;
    private double lastSuperheatedSteamLt = 0.0;
    private double lastSupercriticalSteamLt = 0.0;
    private double lastSecondaryGasLt = 0.0;
    private double lastSecondaryBoiledLt = 0.0;
    private double lastPumpPowerEUt = 0.0;
    private double lastPhePowerEUt = 0.0;
    private boolean ruptured = false;
    private String ruptureReason = "";

    // Cumulative stats
    private long totalDeuteriumProduced = 0;
    private long totalTritiumProduced = 0;
    private long totalOxygenProduced = 0;
    private double totalSecondarySteamProduced = 0.0;
    private double totalHeatExtractedJoules = 0.0;
    private double totalHeatExtractedHut = 0.0;

    public CoolantLoopModel() {
        circuits.add(new Circuit());
    }

    // ---- Circuits -----------------------------------------------------------------------------------------------

    public List<Circuit> getCircuits() {
        return Collections.unmodifiableList(circuits);
    }

    /** The circuit the configuration setters below act on. */
    public Circuit getSelectedCircuit() {
        return circuits.get(selected);
    }

    public int getSelectedIndex() {
        return selected;
    }

    /** Selects a circuit (0-based) for the setters, adding circuits up to it if needed. */
    public Circuit selectCircuit(int index) {
        int i = Math.max(0, Math.min(MAX_CIRCUITS - 1, index));
        while (circuits.size() <= i) circuits.add(new Circuit());
        selected = i;
        return circuits.get(i);
    }

    /** Removes the last circuit (its passages are then on no loop); the first circuit always stays. */
    public void removeLastCircuit() {
        if (circuits.size() > 1) {
            circuits.remove(circuits.size() - 1);
            if (selected >= circuits.size()) selected = circuits.size() - 1;
        }
    }

    /** Keeps only the first circuit, with its settings. */
    public void clearExtraCircuits() {
        while (circuits.size() > 1) circuits.remove(circuits.size() - 1);
        selected = 0;
    }

    // ---- Electrical draw (selected circuit) -------------------------------------------------------------------

    /**
     * Voltage of a multiblock's energy hatches: the tier's voltage with one hatch, four times the highest with more.
     */
    private static double hatchVoltage(EnergyHatchTier tier, int hatchCount) {
        if (hatchCount <= 0) return 0.0;
        return hatchCount == 1 ? tier.voltageEU : 4.0 * tier.voltageEU;
    }

    public double pumpPowerEUt() {
        return getSelectedCircuit().pumpPowerEUt();
    }

    public double pheVoltage() {
        return getSelectedCircuit().pheVoltage();
    }

    public double phePowerEUt() {
        return getSelectedCircuit().phePowerEUt();
    }

    public double getRotorEfficiency() {
        return getSelectedCircuit().getRotorEfficiency();
    }

    public double pumpMechanicalPowerWatts() {
        return getSelectedCircuit().pumpMechanicalPowerWatts();
    }

    public double maxFlowLitersPerSecond() {
        return getSelectedCircuit().maxFlowLitersPerSecond();
    }

    // ---- Loop construction ------------------------------------------------------------------------------------------

    private static LoopSegment pipe(Circuit c, String id, double temp) {
        return new LoopSegment(
            id,
            1.0,
            c.pipeSize.diameterMeters(),
            CoolantLoopConfigData.getPipeData()
                .getDefaultRoughness(),
            0.05,
            c.material.maxPressureBar(),
            c.material.maxTemperatureCelsius(),
            temp);
    }

    private static LoopSegment manifold(Circuit c, String id, double temp) {
        LoopSegment m = new LoopSegment(
            id,
            1.0,
            c.pipeSize.diameterMeters(),
            0.000045,
            MANIFOLD_MINOR_LOSS_K,
            500.0,
            2000.0,
            temp);
        m.setManifold(true);
        return m;
    }

    private static void chain(LoopSegment from, LoopSegment to) {
        to.addUpstreamSegment(from);
    }

    /**
     * Builds the canonical loop the game's crawler would produce for a pump feeding its passages in parallel: pump
     * discharge, cold leg pipes, distribution manifold, the passages, collection manifold, hot leg pipes, the PHEs (in
     * parallel between two more manifolds when there are several), and the return run to the pump suction.
     */
    private static Loop buildLoop(Circuit c, int passageCount, double startTemp) {
        Loop loop = new Loop();
        CoolantLoopEngine engine = new CoolantLoopEngine(c.fluidType.property());
        loop.engine = engine;
        engine.setMaxVolumetricFlowRate(c.maxFlowLitersPerSecond() / 1000.0);
        loop.radiolysisAccumulators = new int[passageCount];

        List<LoopSegment> segs = new ArrayList<>();
        LoopSegment prev = null;
        for (int i = 0; i < PIPE_RUN_BLOCKS; i++) {
            LoopSegment s = pipe(c, "cold_" + i, startTemp);
            if (prev != null) chain(prev, s);
            segs.add(s);
            prev = s;
        }
        loop.coldLegSegment = prev;

        LoopSegment distribution = manifold(c, "manifold_in", startTemp);
        chain(prev, distribution);
        segs.add(distribution);

        LoopSegment collection = manifold(c, "manifold_out", startTemp);
        for (int i = 0; i < passageCount; i++) {
            LoopSegment p = new LoopSegment(
                "passage_" + i,
                CoolantPassage.length(),
                CoolantPassage.diameter(),
                0.000045,
                CoolantPassage.MINOR_LOSS_K,
                500.0,
                3200.0,
                startTemp);
            p.setFlowFraction(1.0 / passageCount);
            chain(distribution, p);
            chain(p, collection);
            segs.add(p);
            loop.passageSegments.add(p);
        }
        segs.add(collection);
        prev = collection;

        for (int i = 0; i < PIPE_RUN_BLOCKS; i++) {
            LoopSegment s = pipe(c, "hot_" + i, startTemp);
            chain(prev, s);
            segs.add(s);
            prev = s;
        }
        loop.hotLegSegment = prev;

        PHEData cfg = pheConfig();
        LoopSegment pheSplit = null;
        LoopSegment pheJoin = null;
        if (c.pheCount > 1) {
            pheSplit = manifold(c, "phe_manifold_in", startTemp);
            chain(prev, pheSplit);
            segs.add(pheSplit);
            pheJoin = manifold(c, "phe_manifold_out", startTemp);
        }
        for (int j = 0; j < c.pheCount; j++) {
            LoopSegment phe = new LoopSegment(
                "phe_" + j,
                2.0,
                0.12,
                0.000045,
                cfg.minorLossK,
                500.0,
                2000.0,
                startTemp);
            phe.setFlowFraction(1.0 / c.pheCount);
            chain(pheSplit != null ? pheSplit : prev, phe);
            if (pheJoin != null) chain(phe, pheJoin);
            segs.add(phe);
            loop.pheSegments.add(phe);
            loop.pheBatches.add(new PheExchange.Batch());
        }
        if (pheJoin != null) {
            segs.add(pheJoin);
            prev = pheJoin;
        } else {
            prev = loop.pheSegments.get(0);
        }

        for (int i = 0; i < PIPE_RUN_BLOCKS; i++) {
            LoopSegment s = pipe(c, "return_" + i, startTemp);
            chain(prev, s);
            segs.add(s);
            prev = s;
        }
        chain(prev, segs.get(0));

        for (LoopSegment s : segs) {
            s.setCurrentTemperatureCelsius(startTemp);
            s.setIncomingTemperatureCelsius(startTemp);
            engine.addSegment(s);
        }
        return loop;
    }

    /**
     * Splits a circuit's passages among its copies (round robin) and builds each copy. A rebuild keeps the coolant's
     * temperature unless the fluid changed, in which case the new fluid enters at its fill temperature.
     */
    private static void buildCircuit(Circuit c, List<SimTile> passageTiles) {
        double startTemp = c.fillTemperatureCelsius();
        if (!c.loops.isEmpty() && c.builtFluid == c.fluidType) {
            double sum = 0.0;
            for (Loop l : c.loops) sum += l.engine.getAvgLoopTempCelsius();
            startTemp = sum / c.loops.size();
        }
        c.loops.clear();
        int n = Math.max(1, Math.min(c.copies, Math.max(1, passageTiles.size())));
        for (int k = 0; k < n; k++) {
            List<SimTile> mine = new ArrayList<>();
            for (int i = k; i < passageTiles.size(); i += n) mine.add(passageTiles.get(i));
            Loop loop = buildLoop(c, mine.size(), startTemp);
            loop.passageTiles.addAll(mine);
            c.loops.add(loop);
        }
        c.builtFluid = c.fluidType;
        c.dirty = false;
    }

    private static boolean sameTiles(Circuit c, List<SimTile> tiles) {
        int built = 0;
        for (Loop l : c.loops) built += l.passageTiles.size();
        if (built != tiles.size()) return false;
        int n = c.loops.size();
        for (int k = 0; k < n; k++) {
            List<SimTile> mine = c.loops.get(k).passageTiles;
            for (int i = 0; i < mine.size(); i++) {
                if (mine.get(i) != tiles.get(k + i * n)) return false;
            }
        }
        return true;
    }

    // ---- Simulation -------------------------------------------------------------------------------------------------

    /** Runs one tick (0.05 s) of the loops. */
    public boolean step(StandaloneNuclearGrid grid, SimTile[][] tiles, int width, int height) {
        return step(grid, tiles, width, height, 1);
    }

    /**
     * Runs every circuit for the given number of ticks, as the game's pumps do every tick: decides whether each pump
     * runs (its control), advances the shared loop engine, then lets each passage and PHE exchange heat with the
     * coolant in its segment. Passages are assigned to circuits by their loop number (CP, CP2 ...); passages on a
     * loop number with no circuit are on no loop.
     */
    public boolean step(StandaloneNuclearGrid grid, SimTile[][] tiles, int width, int height, int ticks) {
        if (ruptured) {
            return false;
        }
        int subTicks = Math.max(1, ticks);

        if (grid.getPipeTier() < NuclearSimulationEngine.PIPE_TIER_OSMIUM) {
            this.ruptured = true;
            this.ruptureReason = "Tier Violation: Convective coolant loops require Tier 2+ (LuV Osmium+) casing!";
            return false;
        }

        List<List<SimTile>> assigned = new ArrayList<>();
        for (int c = 0; c < circuits.size(); c++) assigned.add(new ArrayList<>());
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SimTile tile = tiles[x][y];
                if (tile == null) continue;
                int target = -1;
                for (int c = 0; c < circuits.size() && target < 0; c++) {
                    if (circuits.get(c).attachedPoints.contains(new GridCoord(x, y))) target = c;
                }
                if (target < 0 && tile.getType() == SimTile.TileType.PASSAGE_CORE
                    && tile.getPassageLoop() < circuits.size()) {
                    target = tile.getPassageLoop();
                }
                if (target >= 0) {
                    assigned.get(target)
                        .add(tile);
                    if (tile.isCoolantPassage()) tile.setPassageLoopFluid(circuits.get(target).fluidType.fluidName);
                } else if (tile.isCoolantPassage()) {
                    tile.setPassageLoopFluid(null);
                }
            }
        }

        double extractedJoules = 0.0, boiledLt = 0.0, steam = 0.0, superheated = 0.0, supercritical = 0.0, gas = 0.0;
        double pumpEUt = 0.0, pheEUt = 0.0;
        for (int c = 0; c < circuits.size(); c++) {
            Circuit circuit = circuits.get(c);
            if (!stepCircuit(circuit, assigned.get(c), subTicks)) return false;
            extractedJoules += circuit.lastHeatJoules;
            boiledLt += circuit.lastBoiledLt;
            steam += circuit.lastSteamLt;
            superheated += circuit.lastSuperheatedSteamLt;
            supercritical += circuit.lastSupercriticalSteamLt;
            gas += circuit.lastGasLt;
            pumpEUt += circuit.lastPumpPowerEUt;
            pheEUt += circuit.lastPhePowerEUt;
        }

        double seconds = subTicks * 0.05;
        totalHeatExtractedJoules += extractedJoules;
        totalHeatExtractedHut += extractedJoules / CoolantPassage.JOULES_PER_HU;
        totalSecondarySteamProduced += (steam + superheated + supercritical) * subTicks;
        lastHeatExtractedWatts = extractedJoules / seconds;
        lastHeatExtractedHut = extractedJoules / CoolantPassage.JOULES_PER_HU / subTicks;
        lastSteamLt = steam;
        lastSuperheatedSteamLt = superheated;
        lastSupercriticalSteamLt = supercritical;
        lastSecondaryGasLt = gas;
        lastSecondaryBoiledLt = boiledLt;
        lastPumpPowerEUt = pumpEUt;
        lastPhePowerEUt = pheEUt;
        return true;
    }

    /** Runs one circuit's copies for the cycle and records its outputs (per tick) on the circuit. */
    private boolean stepCircuit(Circuit c, List<SimTile> passageTiles, int subTicks) {
        c.passageCount = passageTiles.size();
        c.lastHeatJoules = 0.0;
        c.lastHeatHut = 0.0;
        c.lastSteamLt = c.lastSuperheatedSteamLt = c.lastSupercriticalSteamLt = c.lastGasLt = c.lastBoiledLt = 0.0;
        c.lastPumpPowerEUt = c.lastPhePowerEUt = c.lastPumpOnFraction = 0.0;
        if (passageTiles.isEmpty()) {
            c.loops.clear();
            c.dirty = true;
            return true;
        }
        if (c.dirty || c.loops.isEmpty() || !sameTiles(c, passageTiles)) {
            buildCircuit(c, passageTiles);
        }

        PHEData cfg = pheConfig();
        SecondaryCoolantEntry secondary = c.getSecondaryCoolant();
        double maxBatchHeat = PheExchange.maxBatchHeatJoules(cfg, (long) c.pheVoltage());
        double exchangeArea = cfg.exchangeArea;
        double mechanicalWatts = c.pumpMechanicalPowerWatts();
        double latentPerMl = secondary != null ? secondary.latentHeatJoulePerLiter / 1000.0 : 0.0;
        int expansion = secondary != null && secondary.expansionRatio > 0 ? secondary.expansionRatio : 1;

        double extractedJoules = 0.0, boiledMl = 0.0;
        double steam = 0.0, superheated = 0.0, supercritical = 0.0, gas = 0.0;
        int onTicks = 0;
        String reason = "";

        for (int t = 0; t < subTicks; t++) {
            for (Loop loop : c.loops) {
                CoolantLoopEngine engine = loop.engine;
                CoolantFluidProperty fluid = engine.getFluid();

                // Pump control: the redstone a player wires to the pump's machine controller cover
                boolean on = true;
                if (c.dutyCyclePercent < 100.0) {
                    loop.dutyAccumulator += c.dutyCyclePercent / 100.0;
                    if (loop.dutyAccumulator >= 1.0) {
                        loop.dutyAccumulator -= 1.0;
                    } else {
                        on = false;
                        reason = "duty cycle";
                    }
                }
                if (c.maxFlowLPerSec > 0.0 && engine.getFlowRateLitersPerSecond() >= c.maxFlowLPerSec) {
                    on = false;
                    reason = "flow cap";
                }
                if (c.maxPressureBar > 0.0 && engine.getPeakLoopPressureBar() >= c.maxPressureBar) {
                    on = false;
                    reason = "pressure cap";
                }
                if (c.hasTemperatureBand()) {
                    double sensed = sense(c, loop);
                    if (sensed > c.controlMaxTempC) loop.bandOn = true;
                    else if (sensed < c.controlMinTempC) loop.bandOn = false;
                    if (!loop.bandOn) {
                        on = false;
                        reason = "temperature band";
                    }
                }
                engine.setPumpPowered(on);
                engine.setPumpMechanicalPowerWatts(on ? mechanicalWatts : 0.0);
                if (on) onTicks++;

                engine.step(0.05);
                if (engine.isRuptured()) {
                    return rupture(engine.getFailureReason());
                }
                double flow = engine.getVolumetricFlowRate();

                for (int i = 0; i < loop.passageTiles.size(); i++) {
                    SimTile tile = loop.passageTiles.get(i);
                    LoopSegment seg = loop.passageSegments.get(i);
                    double hu = CoolantPassage
                        .exchange(tile.getTemperature(), flow * seg.getFlowFraction(), 0.05, fluid, seg);
                    if (hu == 0.0) continue;
                    tile.setTemperature(tile.getTemperature() - hu / NuclearSimulationEngine.HU_PER_DEGREE);
                    if (hu > 0) {
                        tile.setLastHeatOutput(tile.getLastHeatOutput() + hu);
                        extractedJoules += hu * CoolantPassage.JOULES_PER_HU;
                    }
                }

                for (int j = 0; j < loop.pheSegments.size(); j++) {
                    LoopSegment seg = loop.pheSegments.get(j);
                    PheExchange.Batch batch = loop.pheBatches.get(j);
                    PheExchange.tick(
                        batch,
                        cfg,
                        secondary,
                        Double.MAX_VALUE,
                        flow * seg.getFlowFraction(),
                        seg,
                        fluid,
                        seg.getCurrentTemperatureCelsius(),
                        exchangeArea,
                        CoolantLoopConfigData.getConvectiveModel(),
                        1.0,
                        maxBatchHeat);
                    double heat = batch.lastTickHeatJoules;
                    if (heat > 0.0 && latentPerMl > 0.0) {
                        // Credit output as the heat moves (the game releases it per batch, at the batch's mean
                        // temperature)
                        double ml = heat / latentPerMl;
                        double out = ml * expansion / 1000.0;
                        boiledMl += ml;
                        if (secondary.isSteam) {
                            String grade = PheExchange
                                .steamGrade(batch.effectiveTemperature(seg.getCurrentTemperatureCelsius()), cfg);
                            if (grade.equals("supercriticalsteam")) supercritical += out;
                            else if (grade.equals("ic2superheatedsteam")) superheated += out;
                            else steam += out;
                        } else {
                            gas += out;
                        }
                    }
                    if (PheExchange.batchComplete(batch, cfg)) {
                        batch.reset();
                    }
                }
            }
        }

        // Radiolysis in the passages, as the game's high-pressure hatches do on neutron absorption
        String gasProduct = CoolantPassage.radiolysisGas(c.fluidType.fluidName);
        for (Loop loop : c.loops) {
            for (int i = 0; i < loop.passageTiles.size(); i++) {
                SimTile tile = loop.passageTiles.get(i);
                loop.radiolysisAccumulators[i] += tile.getLastFastAbsorbed() + tile.getLastThermalAbsorbed();
                int events = loop.radiolysisAccumulators[i] / CoolantPassage.NEUTRONS_PER_RADIOLYSIS;
                loop.radiolysisAccumulators[i] %= CoolantPassage.NEUTRONS_PER_RADIOLYSIS;
                if (events > 0 && gasProduct != null) {
                    if (gasProduct.equals("Tritium")) totalTritiumProduced += events * 2L;
                    else totalDeuteriumProduced += events * 2L;
                    totalOxygenProduced += events;
                }
            }
        }

        int loopTicks = subTicks * c.loops.size();
        c.lastHeatJoules = extractedJoules;
        c.lastHeatHut = extractedJoules / CoolantPassage.JOULES_PER_HU / subTicks;
        c.lastSteamLt = steam * 1000.0 / subTicks;
        c.lastSuperheatedSteamLt = superheated * 1000.0 / subTicks;
        c.lastSupercriticalSteamLt = supercritical * 1000.0 / subTicks;
        c.lastGasLt = gas * 1000.0 / subTicks;
        c.lastBoiledLt = boiledMl / 1000.0 / subTicks;
        c.lastPumpPowerEUt = c.pumpPowerEUt() * onTicks / subTicks;
        c.lastPhePowerEUt = c.phePowerEUt() * c.loops.size();
        c.lastPumpOnFraction = loopTicks > 0 ? (double) onTicks / loopTicks : 0.0;
        c.limitReason = onTicks < loopTicks ? reason : "";
        return true;
    }

    /** The temperature a circuit's controller reads on one copy. */
    private static double sense(Circuit c, Loop loop) {
        if (c.sensor == TempSensor.HOT_LEG) return loop.hotLegSegment.getCurrentTemperatureCelsius();
        if (c.sensor == TempSensor.COLDEST_PASSAGE) {
            double min = Double.MAX_VALUE;
            for (SimTile t : loop.passageTiles) min = Math.min(min, t.getTemperature());
            return min;
        }
        double max = -Double.MAX_VALUE;
        for (SimTile t : loop.passageTiles) max = Math.max(max, t.getTemperature());
        return max;
    }

    private boolean rupture(String reason) {
        this.ruptured = true;
        this.ruptureReason = reason != null ? reason : "Coolant loop failure";
        return false;
    }

    public void reset() {
        this.ruptured = false;
        this.ruptureReason = "";
        for (Circuit c : circuits) {
            c.loops.clear();
            c.dirty = true;
            c.builtFluid = null;
        }
        this.lastHeatExtractedWatts = 0.0;
        this.lastHeatExtractedHut = 0.0;
        this.lastSteamLt = 0.0;
        this.lastSuperheatedSteamLt = 0.0;
        this.lastSupercriticalSteamLt = 0.0;
        this.lastSecondaryGasLt = 0.0;
        this.lastSecondaryBoiledLt = 0.0;
        this.lastPumpPowerEUt = 0.0;
        this.lastPhePowerEUt = 0.0;
        this.totalDeuteriumProduced = 0;
        this.totalTritiumProduced = 0;
        this.totalOxygenProduced = 0;
        this.totalSecondarySteamProduced = 0.0;
        this.totalHeatExtractedJoules = 0.0;
        this.totalHeatExtractedHut = 0.0;
    }

    // ---- Attached cells (selected circuit) ----------------------------------------------------------------------

    public void attachPoint(int x, int y) {
        getSelectedCircuit().attachedPoints.add(new GridCoord(x, y));
    }

    public void attachPoint(GridCoord coord) {
        if (coord != null) getSelectedCircuit().attachedPoints.add(coord);
    }

    public void detachPoint(int x, int y) {
        for (Circuit c : circuits) c.attachedPoints.remove(new GridCoord(x, y));
    }

    public void detachPoint(GridCoord coord) {
        if (coord != null) for (Circuit c : circuits) c.attachedPoints.remove(coord);
    }

    public boolean isPointAttached(int x, int y) {
        return isPointAttached(new GridCoord(x, y));
    }

    public boolean isPointAttached(GridCoord coord) {
        if (coord == null) return false;
        for (Circuit c : circuits) if (c.attachedPoints.contains(coord)) return true;
        return false;
    }

    public void clearAttachedPoints() {
        for (Circuit c : circuits) c.attachedPoints.clear();
    }

    /** Cells attached to any circuit. */
    public Set<GridCoord> getAttachedPoints() {
        Set<GridCoord> all = new HashSet<>();
        for (Circuit c : circuits) all.addAll(c.attachedPoints);
        return Collections.unmodifiableSet(all);
    }

    // ---- Configuration (selected circuit) -----------------------------------------------------------------------

    public LoopMaterial getMaterial() {
        return getSelectedCircuit().getMaterial();
    }

    public void setMaterial(LoopMaterial material) {
        getSelectedCircuit().setMaterial(material);
    }

    public LoopPipeSize getPipeSize() {
        return getSelectedCircuit().getPipeSize();
    }

    public void setPipeSize(LoopPipeSize pipeSize) {
        getSelectedCircuit().setPipeSize(pipeSize);
    }

    public CoolantFluidType getFluidType() {
        return getSelectedCircuit().getFluidType();
    }

    public void setFluidType(CoolantFluidType fluidType) {
        getSelectedCircuit().setFluidType(fluidType);
    }

    public SecondaryCoolantEntry getSecondaryCoolant() {
        return getSelectedCircuit().getSecondaryCoolant();
    }

    public String getSecondaryFluidName() {
        return getSelectedCircuit().getSecondaryFluidName();
    }

    public void setSecondaryCoolant(String fluidName) {
        getSelectedCircuit().setSecondaryCoolant(fluidName);
    }

    public EnergyHatchTier getHatchTier() {
        return getSelectedCircuit().getHatchTier();
    }

    public void setHatchTier(EnergyHatchTier hatchTier) {
        getSelectedCircuit().setHatchTier(hatchTier);
    }

    public int getPumpHatchCount() {
        return getSelectedCircuit().getPumpHatchCount();
    }

    public void setPumpHatchCount(int count) {
        getSelectedCircuit().setPumpHatchCount(count);
    }

    public RotorMaterial getRotorMaterial() {
        return getSelectedCircuit().getRotorMaterial();
    }

    public void setRotorMaterial(RotorMaterial mat) {
        getSelectedCircuit().setRotorMaterial(mat);
    }

    public void setRotorMaterial(String name) {
        getSelectedCircuit().setRotorMaterial(RotorMaterial.fromString(name));
    }

    public RotorSize getRotorSize() {
        return getSelectedCircuit().getRotorSize();
    }

    public void setRotorSize(RotorSize size) {
        getSelectedCircuit().setRotorSize(size);
    }

    public int getLoopCount() {
        return getSelectedCircuit().getCopies();
    }

    /** Identical copies of the selected circuit's loop, each with its own pump and PHEs. */
    public void setLoopCount(int count) {
        getSelectedCircuit().setCopies(count);
    }

    public int getPheCount() {
        return getSelectedCircuit().getPheCount();
    }

    public void setPheCount(int count) {
        getSelectedCircuit().setPheCount(count);
    }

    public EnergyHatchTier getPheHatchTier() {
        return getSelectedCircuit().getPheHatchTier();
    }

    public void setPheHatchTier(EnergyHatchTier tier) {
        getSelectedCircuit().setPheHatchTier(tier);
    }

    public int getPheHatchCount() {
        return getSelectedCircuit().getPheHatchCount();
    }

    public void setPheHatchCount(int count) {
        getSelectedCircuit().setPheHatchCount(count);
    }

    public double getDutyCyclePercent() {
        return getSelectedCircuit().getDutyCyclePercent();
    }

    public void setDutyCyclePercent(double percent) {
        getSelectedCircuit().setDutyCyclePercent(percent);
    }

    public double getMaxFlowRateLPerSec() {
        return getSelectedCircuit().getMaxFlowLPerSec();
    }

    public void setMaxFlowRateLPerSec(double flow) {
        getSelectedCircuit().setMaxFlowLPerSec(flow);
    }

    public double getMaxPressureBar() {
        return getSelectedCircuit().getMaxPressureBar();
    }

    public void setMaxPressureBar(double bar) {
        getSelectedCircuit().setMaxPressureBar(bar);
    }

    public void setTemperatureBand(double minC, double maxC) {
        getSelectedCircuit().setTemperatureBand(minC, maxC);
    }

    // ---- State ------------------------------------------------------------------------------------------------------

    public double getCurrentFlowRateLPerSec() {
        return getSelectedCircuit().getCurrentFlowRateLPerSec();
    }

    public double getCurrentPressureBar() {
        double max = 1.0;
        for (Circuit c : circuits) max = Math.max(max, c.getCurrentPressureBar());
        return max;
    }

    /** Coolant temperature leaving the reactor (hot leg) on the selected circuit. */
    public double getCurrentCoolantTempCelsius() {
        return getSelectedCircuit().getHotLegTempCelsius();
    }

    /** Coolant temperature entering the reactor (cold leg) on the selected circuit. */
    public double getColdLegTempCelsius() {
        return getSelectedCircuit().getColdLegTempCelsius();
    }

    public double getPeakLoopTempCelsius() {
        return getSelectedCircuit().getPeakLoopTempCelsius();
    }

    public double getLastHeatExtractedWatts() {
        return lastHeatExtractedWatts;
    }

    public double getLastHeatExtractedHut() {
        return lastHeatExtractedHut;
    }

    public double getTotalHeatExtractedJoules() {
        return totalHeatExtractedJoules;
    }

    public double getTotalHeatExtractedHut() {
        return totalHeatExtractedHut;
    }

    /** Steam (L/t) of each grade all PHEs made over the last cycle. */
    public double getLastSteamLt() {
        return lastSteamLt;
    }

    public double getLastSuperheatedSteamLt() {
        return lastSuperheatedSteamLt;
    }

    public double getLastSupercriticalSteamLt() {
        return lastSupercriticalSteamLt;
    }

    public double getLastSecondarySteamProducedLt() {
        return lastSteamLt + lastSuperheatedSteamLt + lastSupercriticalSteamLt;
    }

    /** Gas (L/t) from cryogenic secondary fluids over the last cycle. */
    public double getLastSecondaryGasLt() {
        return lastSecondaryGasLt;
    }

    public double getLastSecondaryWaterBoiledLt() {
        return lastSecondaryBoiledLt;
    }

    public double getLastPumpPowerEUt() {
        return lastPumpPowerEUt;
    }

    public double getLastPhePowerEUt() {
        return lastPhePowerEUt;
    }

    /** EU/t all loops' machines (pumps and PHEs) drew over the last cycle. */
    public double getLastLoopPowerEUt() {
        return lastPumpPowerEUt + lastPhePowerEUt;
    }

    public boolean isRuptured() {
        return ruptured;
    }

    public String getRuptureReason() {
        return ruptureReason;
    }

    public long getTotalDeuteriumProduced() {
        return totalDeuteriumProduced;
    }

    public long getTotalTritiumProduced() {
        return totalTritiumProduced;
    }

    public long getTotalOxygenProduced() {
        return totalOxygenProduced;
    }

    public double getTotalSecondarySteamProduced() {
        return totalSecondarySteamProduced;
    }
}
