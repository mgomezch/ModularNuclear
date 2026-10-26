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
 * A reactor's convective coolant loop in the standalone simulator. The hydraulics and heat transport run on the
 * game's own {@link CoolantLoopEngine}, the passages exchange heat through {@link CoolantPassage} and the heat
 * exchangers through {@link PheExchange}, all configured from the same coolant loop config as the game, on the loop
 * the game's crawler would build for a pump feeding every passage in parallel.
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
    private final Set<GridCoord> attachedPoints = new HashSet<>();

    private int loopCount = 1;

    /** One pump's loop, built from the configuration (rebuilt when it or the number of passages changes). */
    private static final class Loop {

        CoolantLoopEngine engine;
        final List<SimTile> passageTiles = new ArrayList<>();
        final List<LoopSegment> passageSegments = new ArrayList<>();
        final List<LoopSegment> pheSegments = new ArrayList<>();
        final List<PheExchange.Batch> pheBatches = new ArrayList<>();
        LoopSegment hotLegSegment;
        LoopSegment coldLegSegment;
        int[] radiolysisAccumulators = new int[0];
    }

    private final List<Loop> loops = new ArrayList<>();
    private boolean topologyDirty = true;

    // Last cycle
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

    public CoolantLoopModel() {}

    // ---- Electrical draw (as the game's pump and PHE compute it) -------------------------------------------------

    /**
     * Voltage of a multiblock's energy hatches: the tier's voltage with one hatch, four times the highest with more.
     */
    private static double hatchVoltage(EnergyHatchTier tier, int hatchCount) {
        if (hatchCount <= 0) return 0.0;
        return hatchCount == 1 ? tier.voltageEU : 4.0 * tier.voltageEU;
    }

    /** EU/t one loop's pump draws (512 without energy hatches, as in the game). */
    public double pumpPowerEUt() {
        return pumpHatchCount <= 0 ? 512.0 : hatchVoltage(hatchTier, pumpHatchCount);
    }

    /** Voltage each PHE runs at (32 without energy hatches, as in the game). */
    public double pheVoltage() {
        return pheHatchCount <= 0 ? 32.0 : hatchVoltage(pheHatchTier, pheHatchCount);
    }

    /** EU/t one loop's PHEs draw together (15/16 of their voltage each). */
    public double phePowerEUt() {
        return pheCount * Math.max(1.0, Math.floor(15.0 * pheVoltage() / 16.0));
    }

    public double getRotorEfficiency() {
        return rotorEfficiency(rotorMaterial, rotorSize);
    }

    /** Mechanical power the pump puts into the coolant, in W. */
    public double pumpMechanicalPowerWatts() {
        return pumpPowerEUt() * CoolantLoopConfigData.getPhysicsData()
            .getPumpMechanicalPowerPerEU() * getRotorEfficiency();
    }

    /** Most flow the loop's pipes carry, in L/s. */
    public double maxFlowLitersPerSecond() {
        return material.capacityLitersPerSecond(pipeSize);
    }

    // ---- Loop construction ------------------------------------------------------------------------------------------

    private LoopSegment pipe(String id) {
        return new LoopSegment(
            id,
            1.0,
            pipeSize.diameterMeters(),
            CoolantLoopConfigData.getPipeData()
                .getDefaultRoughness(),
            0.05,
            material.maxPressureBar(),
            material.maxTemperatureCelsius(),
            NuclearSimulationEngine.ambientTemp);
    }

    private LoopSegment manifold(String id, double temp) {
        LoopSegment m = new LoopSegment(
            id,
            1.0,
            pipeSize.diameterMeters(),
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
     * Builds the canonical loop the game's crawler would produce for a pump feeding all passages in parallel: pump
     * discharge, cold leg pipes, distribution manifold, the passages, collection manifold, hot leg pipes, the PHEs (in
     * parallel between two more manifolds when there are several), and the return run to the pump suction.
     */
    private Loop buildLoop(int passageCount, double startTemp) {
        Loop loop = new Loop();
        CoolantLoopEngine engine = new CoolantLoopEngine(fluidType.property());
        loop.engine = engine;
        engine.setMaxVolumetricFlowRate(maxFlowLitersPerSecond() / 1000.0);
        loop.radiolysisAccumulators = new int[passageCount];

        List<LoopSegment> segs = new ArrayList<>();
        LoopSegment prev = null;
        for (int i = 0; i < PIPE_RUN_BLOCKS; i++) {
            LoopSegment s = pipe("cold_" + i);
            if (prev != null) chain(prev, s);
            segs.add(s);
            prev = s;
        }
        loop.coldLegSegment = prev;

        LoopSegment distribution = manifold("manifold_in", startTemp);
        chain(prev, distribution);
        segs.add(distribution);

        LoopSegment collection = manifold("manifold_out", startTemp);
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
            LoopSegment s = pipe("hot_" + i);
            chain(prev, s);
            segs.add(s);
            prev = s;
        }
        loop.hotLegSegment = segs.get(segs.size() - 1);

        PHEData cfg = pheConfig();
        LoopSegment pheSplit = null;
        LoopSegment pheJoin = null;
        if (pheCount > 1) {
            pheSplit = manifold("phe_manifold_in", startTemp);
            chain(prev, pheSplit);
            segs.add(pheSplit);
            pheJoin = manifold("phe_manifold_out", startTemp);
        }
        for (int j = 0; j < pheCount; j++) {
            LoopSegment phe = new LoopSegment(
                "phe_" + j,
                2.0,
                0.12,
                0.000045,
                cfg.minorLossK,
                500.0,
                2000.0,
                startTemp);
            phe.setFlowFraction(1.0 / pheCount);
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
            LoopSegment s = pipe("return_" + i);
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

    /** Splits the passages among the loops (round robin) and builds each loop, keeping the coolant's temperature. */
    private void buildLoops(List<SimTile> passageTiles) {
        double startTemp = NuclearSimulationEngine.ambientTemp;
        if (!loops.isEmpty()) {
            double sum = 0.0;
            for (Loop l : loops) sum += l.engine.getAvgLoopTempCelsius();
            startTemp = sum / loops.size();
        }
        loops.clear();
        int n = Math.max(1, Math.min(loopCount, Math.max(1, passageTiles.size())));
        for (int k = 0; k < n; k++) {
            List<SimTile> mine = new ArrayList<>();
            for (int i = k; i < passageTiles.size(); i += n) mine.add(passageTiles.get(i));
            Loop loop = buildLoop(mine.size(), startTemp);
            loop.passageTiles.addAll(mine);
            loops.add(loop);
        }
        topologyDirty = false;
    }

    private void markDirty() {
        topologyDirty = true;
    }

    // ---- Simulation -------------------------------------------------------------------------------------------------

    /** Runs one tick (0.05 s) of the loop. */
    public boolean step(StandaloneNuclearGrid grid, SimTile[][] tiles, int width, int height) {
        return step(grid, tiles, width, height, 1);
    }

    /**
     * Runs the loop for the given number of ticks, as the game's pump does every tick: powers the pump, advances the
     * shared loop engine, then lets each passage and PHE exchange heat with the coolant in its segment.
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

        List<SimTile> passageTiles = new ArrayList<>();
        boolean hasAttached = !attachedPoints.isEmpty();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SimTile tile = tiles[x][y];
                if (tile == null) continue;
                if (tile.getType() == SimTile.TileType.PASSAGE_CORE || (hasAttached && isPointAttached(x, y))) {
                    passageTiles.add(tile);
                }
            }
        }
        int built = 0;
        for (Loop l : loops) built += l.passageTiles.size();
        boolean sameTiles = built == passageTiles.size();
        for (int k = 0; sameTiles && k < loops.size(); k++) {
            Loop l = loops.get(k);
            for (int i = 0; i < l.passageTiles.size(); i++) {
                if (l.passageTiles.get(i) != passageTiles.get(k + i * loops.size())) {
                    sameTiles = false;
                    break;
                }
            }
        }
        if (topologyDirty || loops.isEmpty() || !sameTiles) {
            buildLoops(passageTiles);
        }

        PHEData cfg = pheConfig();
        SecondaryCoolantEntry secondary = getSecondaryCoolant();
        double maxBatchHeat = PheExchange.maxBatchHeatJoules(cfg, (long) pheVoltage());
        double exchangeArea = cfg.exchangeArea;
        double mechanicalWatts = pumpMechanicalPowerWatts();
        double latentPerMl = secondary != null ? secondary.latentHeatJoulePerLiter / 1000.0 : 0.0;
        int expansion = secondary != null && secondary.expansionRatio > 0 ? secondary.expansionRatio : 1;

        double extractedJoules = 0.0;
        double boiledMl = 0.0;
        double steam = 0.0, superheated = 0.0, supercritical = 0.0, gas = 0.0;

        for (int t = 0; t < subTicks; t++) {
            for (Loop loop : loops) {
                CoolantLoopEngine engine = loop.engine;
                CoolantFluidProperty fluid = engine.getFluid();
                engine.setPumpPowered(true);
                engine.setPumpMechanicalPowerWatts(mechanicalWatts);
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
        String gasProduct = CoolantPassage.radiolysisGas(fluidType.fluidName);
        for (Loop loop : loops) {
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

        double seconds = subTicks * 0.05;
        totalHeatExtractedJoules += extractedJoules;
        totalHeatExtractedHut += extractedJoules / CoolantPassage.JOULES_PER_HU;
        totalSecondarySteamProduced += (steam + superheated + supercritical) * 1000.0;
        lastHeatExtractedWatts = extractedJoules / seconds;
        lastHeatExtractedHut = extractedJoules / CoolantPassage.JOULES_PER_HU / subTicks;
        lastSteamLt = steam * 1000.0 / subTicks;
        lastSuperheatedSteamLt = superheated * 1000.0 / subTicks;
        lastSupercriticalSteamLt = supercritical * 1000.0 / subTicks;
        lastSecondaryGasLt = gas * 1000.0 / subTicks;
        lastSecondaryBoiledLt = boiledMl / 1000.0 / subTicks;
        lastPumpPowerEUt = pumpPowerEUt() * loops.size();
        lastPhePowerEUt = phePowerEUt() * loops.size();
        return true;
    }

    private boolean rupture(String reason) {
        this.ruptured = true;
        this.ruptureReason = reason != null ? reason : "Coolant loop failure";
        return false;
    }

    public void reset() {
        this.ruptured = false;
        this.ruptureReason = "";
        this.loops.clear();
        this.topologyDirty = true;
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

    // ---- Attached passage cells -------------------------------------------------------------------------------------

    public void attachPoint(int x, int y) {
        attachedPoints.add(new GridCoord(x, y));
    }

    public void attachPoint(GridCoord coord) {
        if (coord != null) attachedPoints.add(coord);
    }

    public void detachPoint(int x, int y) {
        attachedPoints.remove(new GridCoord(x, y));
    }

    public void detachPoint(GridCoord coord) {
        if (coord != null) attachedPoints.remove(coord);
    }

    public boolean isPointAttached(int x, int y) {
        return attachedPoints.contains(new GridCoord(x, y));
    }

    public boolean isPointAttached(GridCoord coord) {
        return coord != null && attachedPoints.contains(coord);
    }

    public void clearAttachedPoints() {
        attachedPoints.clear();
    }

    public Set<GridCoord> getAttachedPoints() {
        return Collections.unmodifiableSet(attachedPoints);
    }

    // ---- Configuration ----------------------------------------------------------------------------------------------

    public LoopMaterial getMaterial() {
        return material;
    }

    public void setMaterial(LoopMaterial material) {
        if (material != null && material != this.material) {
            this.material = material;
            markDirty();
        }
    }

    public LoopPipeSize getPipeSize() {
        return pipeSize;
    }

    public void setPipeSize(LoopPipeSize pipeSize) {
        if (pipeSize != null && pipeSize != this.pipeSize) {
            this.pipeSize = pipeSize;
            markDirty();
        }
    }

    public CoolantFluidType getFluidType() {
        return fluidType;
    }

    public void setFluidType(CoolantFluidType fluidType) {
        if (fluidType != null && fluidType != this.fluidType) {
            this.fluidType = fluidType;
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
            this.secondaryFluidName = fluidName;
        }
    }

    public EnergyHatchTier getHatchTier() {
        return hatchTier;
    }

    public void setHatchTier(EnergyHatchTier hatchTier) {
        if (hatchTier != null) this.hatchTier = hatchTier;
    }

    public int getPumpHatchCount() {
        return pumpHatchCount;
    }

    /** Energy hatches on the pump: one draws the tier's voltage, two or more draw four times it. */
    public void setPumpHatchCount(int count) {
        this.pumpHatchCount = Math.max(0, count);
    }

    public RotorMaterial getRotorMaterial() {
        return rotorMaterial;
    }

    public void setRotorMaterial(RotorMaterial mat) {
        if (mat != null) this.rotorMaterial = mat;
    }

    public void setRotorMaterial(String name) {
        setRotorMaterial(RotorMaterial.fromString(name));
    }

    public RotorSize getRotorSize() {
        return rotorSize;
    }

    public void setRotorSize(RotorSize size) {
        if (size != null) this.rotorSize = size;
    }

    public int getLoopCount() {
        return loopCount;
    }

    /** Independent loops (each with its own pump and PHEs) the passages are split among. */
    public void setLoopCount(int count) {
        int c = Math.max(1, count);
        if (c != loopCount) {
            this.loopCount = c;
            markDirty();
        }
    }

    public int getPheCount() {
        return pheCount;
    }

    public void setPheCount(int count) {
        int c = Math.max(1, count);
        if (c != pheCount) {
            this.pheCount = c;
            markDirty();
        }
    }

    public EnergyHatchTier getPheHatchTier() {
        return pheHatchTier;
    }

    public void setPheHatchTier(EnergyHatchTier tier) {
        if (tier != null) this.pheHatchTier = tier;
    }

    public int getPheHatchCount() {
        return pheHatchCount;
    }

    public void setPheHatchCount(int count) {
        this.pheHatchCount = Math.max(0, count);
    }

    // ---- State ------------------------------------------------------------------------------------------------------

    /** Flow in each loop (L/s); the loops are identical, so this is their average. */
    public double getCurrentFlowRateLPerSec() {
        double sum = 0.0;
        for (Loop l : loops) sum += l.engine.getFlowRateLitersPerSecond();
        return loops.isEmpty() ? 0.0 : sum / loops.size();
    }

    /** Highest pressure in any loop. */
    public double getCurrentPressureBar() {
        double max = 1.0;
        for (Loop l : loops) max = Math.max(max, l.engine.getPeakLoopPressureBar());
        return max;
    }

    /** Coolant temperature leaving the reactor (hot leg), averaged over the loops. */
    public double getCurrentCoolantTempCelsius() {
        double sum = 0.0;
        for (Loop l : loops) sum += l.hotLegSegment.getCurrentTemperatureCelsius();
        return loops.isEmpty() ? NuclearSimulationEngine.ambientTemp : sum / loops.size();
    }

    /** Coolant temperature entering the reactor (cold leg), averaged over the loops. */
    public double getColdLegTempCelsius() {
        double sum = 0.0;
        for (Loop l : loops) sum += l.coldLegSegment.getCurrentTemperatureCelsius();
        return loops.isEmpty() ? NuclearSimulationEngine.ambientTemp : sum / loops.size();
    }

    public double getPeakLoopTempCelsius() {
        double max = NuclearSimulationEngine.ambientTemp;
        for (Loop l : loops) max = Math.max(max, l.engine.getPeakLoopTempCelsius());
        return max;
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

    /** Steam (L/t) of each grade the PHEs made over the last cycle. */
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

    /** Gas (L/t) from a cryogenic secondary fluid over the last cycle. */
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

    /** EU/t the loop's machines (pump and PHEs) draw. */
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
