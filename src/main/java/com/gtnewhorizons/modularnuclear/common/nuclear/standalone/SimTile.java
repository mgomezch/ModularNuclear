package com.gtnewhorizons.modularnuclear.common.nuclear.standalone;

import com.gtnewhorizons.modularnuclear.common.config.ReactorConfigData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData;
import com.gtnewhorizons.modularnuclear.common.config.data.FuelData;
import com.gtnewhorizons.modularnuclear.common.nuclear.CoolantPassage;
import com.gtnewhorizons.modularnuclear.common.nuclear.INuclearTile;
import com.gtnewhorizons.modularnuclear.common.nuclear.NeutronType;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearFuelType;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.TileContent;
import com.gtnewhorizons.modularnuclear.common.nuclear.TileProperties;
import com.gtnewhorizons.modularnuclear.common.nuclear.TileTick;

/**
 * Pure Java implementation of INuclearTile for standalone simulation and tooling.
 * Completely decoupled from Minecraft, Forge, and IC2 runtime classes.
 */
public class SimTile implements INuclearTile {

    public enum TileType {

        EMPTY("Empty", ".", null),
        FUEL_URANIUM_SINGLE("Uranium Single", "U1", "fuel_uranium_1"),
        FUEL_URANIUM_DUAL("Uranium Dual", "U2", "fuel_uranium_2"),
        FUEL_URANIUM_QUAD("Uranium Quad", "U4", "fuel_uranium_4"),
        FUEL_MOX_SINGLE("MOX Single", "M1", "fuel_mox_1"),
        FUEL_MOX_DUAL("MOX Dual", "M2", "fuel_mox_2"),
        FUEL_MOX_QUAD("MOX Quad", "M4", "fuel_mox_4"),
        FUEL_THORIUM_SINGLE("Thorium Single", "T1", "fuel_thorium_1"),
        FUEL_THORIUM_DUAL("Thorium Dual", "T2", "fuel_thorium_2"),
        FUEL_THORIUM_QUAD("Thorium Quad", "T4", "fuel_thorium_4"),
        FUEL_GLOWSTONE("Glowstone Rod", "G1", "fuel_glowstone"),
        FUEL_LITHIUM("Lithium Rod", "LI1", "fuel_lithium"),
        FUEL_HD_URANIUM_SINGLE("HD Uranium Single", "HDU1", "fuel_hd_uranium_1"),
        FUEL_HD_URANIUM_DUAL("HD Uranium Dual", "HDU2", "fuel_hd_uranium_2"),
        FUEL_HD_URANIUM_QUAD("HD Uranium Quad", "HDU4", "fuel_hd_uranium_4"),
        FUEL_HD_PLUTONIUM_SINGLE("HD Plutonium Single", "HDP1", "fuel_hd_plutonium_1"),
        FUEL_HD_PLUTONIUM_DUAL("HD Plutonium Dual", "HDP2", "fuel_hd_plutonium_2"),
        FUEL_HD_PLUTONIUM_QUAD("HD Plutonium Quad", "HDP4", "fuel_hd_plutonium_4"),
        FUEL_EXCITED_URANIUM_SINGLE("Excited Uranium Single", "EXU1", "fuel_excited_uranium_1"),
        FUEL_EXCITED_URANIUM_DUAL("Excited Uranium Dual", "EXU2", "fuel_excited_uranium_2"),
        FUEL_EXCITED_URANIUM_QUAD("Excited Uranium Quad", "EXU4", "fuel_excited_uranium_4"),
        FUEL_EXCITED_PLUTONIUM_SINGLE("Excited Plutonium Single", "EXP1", "fuel_excited_plutonium_1"),
        FUEL_EXCITED_PLUTONIUM_DUAL("Excited Plutonium Dual", "EXP2", "fuel_excited_plutonium_2"),
        FUEL_EXCITED_PLUTONIUM_QUAD("Excited Plutonium Quad", "EXP4", "fuel_excited_plutonium_4"),
        FUEL_NAQUADAH_SINGLE("Naquadah Single", "NQ1", "fuel_naquadah_1"),
        FUEL_NAQUADAH_DUAL("Naquadah Dual", "NQ2", "fuel_naquadah_2"),
        FUEL_NAQUADAH_QUAD("Naquadah Quad", "NQ4", "fuel_naquadah_4"),
        FUEL_NAQUADRIA_SINGLE("Naquadria Single", "NQR1", "fuel_naquadria_1"),
        FUEL_NAQUADRIA_DUAL("Naquadria Dual", "NQR2", "fuel_naquadria_2"),
        FUEL_NAQUADRIA_QUAD("Naquadria Quad", "NQR4", "fuel_naquadria_4"),
        FUEL_TIBERIUM_SINGLE("Tiberium Single", "TIB1", "fuel_tiberium_1"),
        FUEL_TIBERIUM_DUAL("Tiberium Dual", "TIB2", "fuel_tiberium_2"),
        FUEL_TIBERIUM_QUAD("Tiberium Quad", "TIB4", "fuel_tiberium_4"),
        FUEL_CORE("The Core", "NQ32", "fuel_naquadah_32"),
        HATCH_LIQUID_FUEL_URANIUM("Liquid Uranium Fuel Hatch", "LFU", "fluid_fuel_uranium"),
        HATCH_LIQUID_FUEL_THORIUM("Liquid Thorium Fuel Hatch", "LFT", "fluid_fuel_thorium"),
        HATCH_LIQUID_FUEL_PLUTONIUM("Liquid Plutonium Fuel Hatch", "LFP", "fluid_fuel_plutonium"),
        HATCH_LIQUID_FUEL_URANIUM_EXCITED("Excited Liquid Uranium Fuel Hatch", "LFUX", "fluid_fuel_uranium_excited"),
        HATCH_LIQUID_FUEL_THORIUM_EXCITED("Excited Liquid Thorium Fuel Hatch", "LFTX", "fluid_fuel_thorium_excited"),
        HATCH_LIQUID_FUEL_PLUTONIUM_EXCITED("Excited Liquid Plutonium Fuel Hatch", "LFPX",
            "fluid_fuel_plutonium_excited"),
        HATCH_DISTILLED_WATER("Distilled Water Hatch", "HD", "coolant_distilled_water"),
        HATCH_HEAVY_WATER("Heavy Water Hatch", "HW", "coolant_heavy_water"),
        HATCH_IC2_COOLANT("IC2 Coolant Hatch", "HC", "coolant_ic2"),
        REFLECTOR_BERYLLIUM("Beryllium Reflector", "RB", null),
        REFLECTOR_CARBON("Carbon Reflector", "RC", null),
        REFLECTOR_GOLD("Gold Plate Reflector", "RG", "reflector_gold"),
        REFLECTOR_STAINLESS("Stainless Steel Plate Reflector", "RS", "reflector_stainless_steel"),
        REFLECTOR_TUNGSTEN_CARBIDE("Tungsten Carbide Plate Reflector", "RT", "reflector_tungsten_carbide"),
        REFLECTOR_IC2("Neutron Reflector", "RN", "reflector_ic2"),
        REFLECTOR_IC2_THICK("Thick Neutron Reflector", "RTN", "reflector_ic2_thick"),
        MODERATOR_GRAPHITE("Graphite Moderator", "MG", "moderator_graphite"),
        MODERATOR_CARBON("Carbon Moderator", "MC", "moderator_carbon"),
        MODERATOR_BERYLLIUM("Beryllium Moderator", "MB", "moderator_beryllium"),
        CONTROL_ROD("Boron Control Rod", "CR", null),
        COOLANT_CELL_10K("10k Coolant Cell", "C1", "coolant_cell_ic2"),
        COOLANT_CELL_60K("60k Coolant Cell", "C6", "coolant_cell_ic2"),
        COOLANT_CELL_360K("360k Coolant Cell", "C3", "coolant_cell_he"),
        RADIOVOLTAIC_HV("Radiovoltaic Cell (HV)", "RH", "radiovoltaic_hv"),
        RADIOVOLTAIC_EV("Radiovoltaic Cell (EV)", "RV", "radiovoltaic_ev"),
        INSULATOR_BASIC_THERMAL_CLOTH("Basic Thermal Cloth", "IT1", "insulator_cloth_t1"),
        INSULATOR_T2_THERMAL_CLOTH("T2 Thermal Cloth", "IT2", "insulator_cloth_t2"),
        INSULATOR_MICA_FOIL("Mica Insulator Foil", "IM", "insulator_mica"),
        INSULATOR_NAQUARITE_FOIL("Naquarite Universal Insulator Foil", "INQ", "insulator_naquarite"),
        VENT_STANDARD("Heat Vent", "V1", "vent_standard"),
        VENT_ADVANCED("Advanced Heat Vent", "VA", "vent_diamond"),
        VENT_OVERCLOCKED("Overclocked Heat Vent", "VO", "vent_gold"),
        VENT_COMPONENT("Component Heat Vent", "VC", "vent_spread"),
        EXCHANGER_STANDARD("Heat Exchanger", "X1", "exchanger_standard"),
        EXCHANGER_ADVANCED("Advanced Heat Exchanger", "XA", "exchanger_diamond"),
        EXCHANGER_COMPONENT("Component Heat Exchanger", "XC", "exchanger_spread"),
        PASSAGE_CORE("Coolant Passage", "CP", null),
        NEUTRON_SOURCE("Neutron Source", "NS", null),
        NULL_WALL("Casing Wall", "NL", null);

        // Backward-compatibility aliases for legacy quad types
        public static final TileType FUEL_HD_URANIUM = FUEL_HD_URANIUM_QUAD;
        public static final TileType FUEL_HD_PLUTONIUM = FUEL_HD_PLUTONIUM_QUAD;
        public static final TileType FUEL_EXCITED_URANIUM = FUEL_EXCITED_URANIUM_QUAD;
        public static final TileType FUEL_EXCITED_PLUTONIUM = FUEL_EXCITED_PLUTONIUM_QUAD;
        public static final TileType FUEL_NAQUADAH = FUEL_NAQUADAH_QUAD;
        public static final TileType FUEL_NAQUADRIA = FUEL_NAQUADRIA_QUAD;
        public static final TileType FUEL_TIBERIUM = FUEL_TIBERIUM_QUAD;

        public final String displayName;
        public final String code;
        /**
         * Id of this tile's entry in fuels.yaml or components.yaml, which supplies all of its physical properties;
         * null for tiles without a configuration entry.
         */
        public final String configId;

        TileType(String displayName, String code, String configId) {
            this.displayName = displayName;
            this.code = code;
            this.configId = configId;
        }

        public static TileType fromCode(String code) {
            if (code == null) return EMPTY;
            String trimmed = code.trim()
                .toUpperCase();
            if (passageLoopFromCode(trimmed) >= 0) return PASSAGE_CORE;
            if (trimmed.equals("BH")) return RADIOVOLTAIC_HV;
            if (trimmed.equals("BV")) return RADIOVOLTAIC_EV;
            if (trimmed.equals("RN")) return REFLECTOR_IC2;
            if (trimmed.equals("RTN")) return REFLECTOR_IC2_THICK;
            if (trimmed.equals("HP") || trimmed.equals("HH")
                || trimmed.equals("HATCH_HP_DISTILLED_WATER")
                || trimmed.equals("HATCH_HP_HEAVY_WATER")) {
                return PASSAGE_CORE;
            }
            if (trimmed.equals("HDU") || trimmed.equals("FUEL_HD_URANIUM")) return FUEL_HD_URANIUM_QUAD;
            if (trimmed.equals("HDP") || trimmed.equals("FUEL_HD_PLUTONIUM")) return FUEL_HD_PLUTONIUM_QUAD;
            if (trimmed.equals("EXU") || trimmed.equals("FUEL_EXCITED_URANIUM")) return FUEL_EXCITED_URANIUM_QUAD;
            if (trimmed.equals("EXP") || trimmed.equals("FUEL_EXCITED_PLUTONIUM")) return FUEL_EXCITED_PLUTONIUM_QUAD;
            if (trimmed.equals("NQ") || trimmed.equals("FUEL_NAQUADAH")) return FUEL_NAQUADAH_QUAD;
            if (trimmed.equals("NQR") || trimmed.equals("FUEL_NAQUADRIA")) return FUEL_NAQUADRIA_QUAD;
            if (trimmed.equals("TIB") || trimmed.equals("FUEL_TIBERIUM")) return FUEL_TIBERIUM_QUAD;
            for (TileType type : values()) {
                if (type.code.equalsIgnoreCase(trimmed) || type.name()
                    .equalsIgnoreCase(trimmed)) {
                    return type;
                }
            }
            return EMPTY;
        }
    }

    public enum ControlRodType {

        NONE("None", 0.0, 0.0, 0.02),
        SILVER("Silver", 0.50, 0.20, 0.20),
        BORON("Boron", 0.70, 0.35, 0.08),
        CADMIUM("Cadmium", 0.85, 0.50, 0.12),
        INDIUM("Indium", 0.95, 0.65, 0.14),
        HAFNIUM("Hafnium", 0.99, 0.80, 0.16);

        /** Id of the rod's entry in components.yaml control_rods, which supplies its physics in the simulator. */
        public final String configId = "rod_" + name().toLowerCase(java.util.Locale.ROOT);

        public final String displayName;
        public final double maxThermalAbsorption;
        public final double maxFastAbsorption;
        public final double heatTransferCoeff;

        ControlRodType(String displayName, double maxThermalAbsorption, double maxFastAbsorption,
            double heatTransferCoeff) {
            this.displayName = displayName;
            this.maxThermalAbsorption = maxThermalAbsorption;
            this.maxFastAbsorption = maxFastAbsorption;
            this.heatTransferCoeff = heatTransferCoeff;
        }

        public static ControlRodType fromName(String name) {
            if (name == null) return NONE;
            for (ControlRodType t : values()) {
                if (t.name()
                    .equalsIgnoreCase(name) || t.displayName.equalsIgnoreCase(name)) {
                    return t;
                }
            }
            return NONE;
        }
    }

    private static TileContent[] contentCache = new TileContent[TileType.values().length];
    private static int contentCacheGeneration = -1;

    /** The configuration-backed content of a tile type, shared with the game through {@link TileContent}. */
    public static TileContent contentOf(TileType type) {
        int gen = ReactorConfigData.generation();
        if (gen != contentCacheGeneration) {
            contentCache = new TileContent[TileType.values().length];
            contentCacheGeneration = gen;
        }
        TileContent c = contentCache[type.ordinal()];
        if (c == null) {
            c = resolveContent(type);
            contentCache[type.ordinal()] = c;
        }
        return c;
    }

    private static TileContent resolveContent(TileType type) {
        switch (type) {
            case EMPTY:
            case NULL_WALL:
                return TileContent.EMPTY;
            case NEUTRON_SOURCE:
                return TileContent.NEUTRON_SOURCE;
            case REFLECTOR_BERYLLIUM:
            case REFLECTOR_CARBON:
                return TileContent.GENERIC_REFLECTOR;
            default:
                break;
        }
        ReactorConfigData.ensureLoaded();
        return type.configId != null ? TileContent.byConfigId(type.configId) : TileContent.OTHER;
    }

    /**
     * What this tile currently holds, as the game would see it: an empty hatch or a depleted rod no longer counts as
     * its original content.
     */
    public TileContent getContent() {
        if (depleted && (isFuel(type) || isLiquidFuelHatch())) return TileContent.OTHER;
        if ((isCoolantHatch() || isLiquidFuelHatch()) && inputFluidAmount <= 0) return TileContent.EMPTY_HATCH;
        return contentOf(type);
    }

    /** The fuel's configuration record, or null if this tile is not a fuel. */
    public FuelData getFuelData() {
        return contentOf(type).fuelData();
    }

    private static boolean isFuel(TileType type) {
        return contentOf(type).isFuel();
    }

    private TileType type = TileType.EMPTY;
    private double temperature = NuclearSimulationEngine.AMBIENT_TEMP;
    private double heatHu = 0;
    /** The coolant loop (0-based) a passage belongs to: layout code CP is loop 0, CP2 to CP9 are loops 1 to 8. */
    private int passageLoop = 0;
    /** Fluid of the running loop a passage is connected to, or null when it is on none. */
    private String passageLoopFluid = null;
    /** Voltage tier of a passage: its loop's pipe material's (titanium, EV, until it joins a loop). */
    private int passageTier = DEFAULT_PASSAGE_TIER;
    public static final int DEFAULT_PASSAGE_TIER = 4;

    /** Loop index of a passage layout code (CP, CP2 ... CP9), or -1 if the code is not a passage with a loop. */
    public static int passageLoopFromCode(String code) {
        if (code == null) return -1;
        String c = code.trim()
            .toUpperCase();
        if (c.equals("CP")) return 0;
        if (c.length() == 3 && c.startsWith("CP") && c.charAt(2) >= '2' && c.charAt(2) <= '9') {
            return c.charAt(2) - '1';
        }
        return -1;
    }

    /** Layout code of this tile, with the loop number for passages on loops other than the first. */
    public String getLayoutCode() {
        if (type == TileType.PASSAGE_CORE && passageLoop > 0) return "CP" + (passageLoop + 1);
        return type.code;
    }

    public int getPassageLoop() {
        return passageLoop;
    }

    public void setPassageLoop(int loop) {
        this.passageLoop = Math.max(0, Math.min(8, loop));
    }

    public String getPassageLoopFluid() {
        return passageLoopFluid;
    }

    public void setPassageLoopFluid(String fluidName) {
        this.passageLoopFluid = fluidName;
    }

    /** Voltage tier that sets this cell's exchange with a loop: a passage's own, or a hatch's tier. */
    public int getPassageTier() {
        return type == TileType.PASSAGE_CORE ? passageTier : tier;
    }

    public void setPassageTier(int passageTier) {
        this.passageTier = Math.max(0, passageTier);
    }

    // Fuel state
    private int maxDurability = 10000;
    private int durability = 10000;
    private double durabilityLossAccumulator = 0.0;
    private double lastDurabilityLoss = 0.0;
    private int lastLiquidFuelBurned = 0;
    private boolean depleted = false;
    private boolean depletionLogged = false;
    private int lastNeutronsGenerated = 0;

    // Coolant hatch state
    private int tier = 3;
    private String inputFluidName = "";
    private int inputFluidAmount = 0;
    private int inputFluidCapacity = 16000;
    private String outputFluidName = "";
    private int outputFluidAmount = 0;
    private boolean wasDry = false;
    private boolean autoRefill = true;
    private int totalSteamProduced = 0;
    private int totalDeuteriumProduced = 0;
    private int totalTritiumProduced = 0;
    private int totalOxygenProduced = 0;
    private int lastTickProduced = 0;

    // Coolant cell state
    private int maxCellHeat = 0;
    private int currentCellHeat = 0;

    // Radiovoltaic direct EU state
    private long directEUProduced = 0;
    private double radiovoltaicRate = 0.0;
    private double pendingCoolantHeat = 0.0;
    private double chainRate = 0.0;
    private double totalDurabilityLoss = 0.0;
    private long wearCycles = 0;

    public StandaloneNuclearGrid grid;

    public int getTicksPerCycle() {
        return grid != null ? grid.getTicksPerCycle() : NuclearSimulationEngine.ticksPerCycle;
    }

    public long getDirectEUProduced() {
        return directEUProduced;
    }

    // Transient flux stats
    private int fastFlux = 0;
    private int thermalFlux = 0;
    private int fastAbsorbed = 0;
    private int thermalAbsorbed = 0;
    private int lastFastFlux = 0;
    private int lastThermalFlux = 0;
    private int lastTotalFlux = 0;
    private int lastFastAbsorbed = 0;
    private int lastThermalAbsorbed = 0;
    private String lastCoolingDetails = "";

    // Control rod state (bottom-layer hatch)
    private boolean hasControlRod = false;
    private ControlRodType controlRodType = ControlRodType.NONE;
    private int controlRodInsertion = 0; // 0 to 100%
    private int controlRodMiningPipes = 5; // 0 to 5 pipes (default 5 for 100% extraction)
    private int lastControlRodFastAbsorbed = 0;
    private int lastControlRodThermalAbsorbed = 0;
    private int controlRodFastAbsorbed = 0;
    private int controlRodThermalAbsorbed = 0;

    // Neutron source state
    private float neutronSourceBeamEnergy = 0.0f;
    private int neutronSourceBeamRate = 0;
    private double beamlinePacketAccumulator = 0.0;

    public float getNeutronSourceBeamEnergy() {
        return neutronSourceBeamEnergy;
    }

    public void setNeutronSourceBeamEnergy(float energy) {
        this.neutronSourceBeamEnergy = energy;
    }

    public int getNeutronSourceBeamRate() {
        return neutronSourceBeamRate;
    }

    public void setNeutronSourceBeamRate(int rate) {
        this.neutronSourceBeamRate = rate;
    }

    @Override
    public boolean hasControlRod() {
        return hasControlRod;
    }

    public void setHasControlRod(boolean hasControlRod) {
        this.hasControlRod = hasControlRod;
    }

    public ControlRodType getControlRodType() {
        return controlRodType;
    }

    public void setControlRodType(ControlRodType controlRodType) {
        this.controlRodType = (controlRodType != null) ? controlRodType : ControlRodType.NONE;
    }

    public int getControlRodInsertion() {
        return controlRodInsertion;
    }

    public void setControlRodInsertion(int controlRodInsertion) {
        this.controlRodInsertion = Math.max(0, Math.min(100, controlRodInsertion));
    }

    @Override
    public int getControlRodMiningPipes() {
        return controlRodMiningPipes;
    }

    public void setControlRodMiningPipes(int count) {
        this.controlRodMiningPipes = Math.max(0, Math.min(5, count));
    }

    public int getMinControlRodInsertion() {
        return Math.max(0, 100 - controlRodMiningPipes * 20);
    }

    public int getMaxControlRodExtraction() {
        return Math.min(100, controlRodMiningPipes * 20);
    }

    public int getEffectiveControlRodInsertion() {
        return Math.max(getMinControlRodInsertion(), controlRodInsertion);
    }

    public int getLastControlRodFastAbsorbed() {
        return lastControlRodFastAbsorbed;
    }

    public int getLastControlRodThermalAbsorbed() {
        return lastControlRodThermalAbsorbed;
    }

    public String getLastCoolingDetails() {
        return lastCoolingDetails;
    }

    private double lastHeatOutput = 0.0;

    public double getLastHeatOutput() {
        return lastHeatOutput;
    }

    public void setLastHeatOutput(double lastHeatOutput) {
        this.lastHeatOutput = lastHeatOutput;
    }

    public SimTile(TileType type) {
        setType(type);
    }

    public void setType(TileType newType) {
        this.type = (newType == null) ? TileType.EMPTY : newType;
        this.passageLoop = 0;
        this.passageLoopFluid = null;
        this.passageTier = DEFAULT_PASSAGE_TIER;
        this.depleted = false;
        this.depletionLogged = false;
        this.lastDurabilityLoss = 0.0;
        this.lastLiquidFuelBurned = 0;
        this.wasDry = false;
        this.inputFluidName = "";
        this.inputFluidAmount = 0;
        this.inputFluidCapacity = 0;
        this.outputFluidName = "";
        this.outputFluidAmount = 0;
        this.maxCellHeat = 0;
        this.currentCellHeat = 0;
        this.totalSteamProduced = 0;
        this.totalDeuteriumProduced = 0;
        this.totalTritiumProduced = 0;
        this.totalOxygenProduced = 0;
        this.lastFastFlux = 0;
        this.lastThermalFlux = 0;
        this.lastTotalFlux = 0;
        this.lastFastAbsorbed = 0;
        this.lastThermalAbsorbed = 0;
        this.radiovoltaicRate = 0.0;
        this.pendingCoolantHeat = 0.0;
        this.chainRate = 0.0;
        this.totalDurabilityLoss = 0.0;
        this.wearCycles = 0;
        this.durabilityLossAccumulator = 0.0;
        this.beamlinePacketAccumulator = 0.0;
        this.directEUProduced = 0;

        FuelData fuelData = getFuelData();
        TileContent content = contentOf(this.type);
        if (fuelData != null && !isLiquidFuelHatch()) {
            this.maxDurability = fuelData.defaultDurability;
            this.durability = fuelData.defaultDurability;
            this.inputFluidAmount = 0;
        } else if (content.moderator() != null || content.reflector() != null) {
            int d = content.moderator() != null ? content.moderator().durability : content.reflector().durability;
            this.maxDurability = d;
            this.durability = d;
        } else {
            switch (this.type) {
                case HATCH_LIQUID_FUEL_URANIUM -> {
                    this.inputFluidName = "uraniumbasedliquidfuel";
                    this.inputFluidCapacity = NuclearSimulationEngine.hatchCoolantCapacity;
                    this.inputFluidAmount = this.inputFluidCapacity;
                    this.outputFluidName = "depleteduraniumbasedliquidfuel";
                }
                case HATCH_LIQUID_FUEL_THORIUM -> {
                    this.inputFluidName = "thoriumbasedliquidfuel";
                    this.inputFluidCapacity = NuclearSimulationEngine.hatchCoolantCapacity;
                    this.inputFluidAmount = this.inputFluidCapacity;
                    this.outputFluidName = "depletedthoriumbasedliquidfuel";
                }
                case HATCH_LIQUID_FUEL_PLUTONIUM -> {
                    this.inputFluidName = "plutoniumbasedliquidfuel";
                    this.inputFluidCapacity = NuclearSimulationEngine.hatchCoolantCapacity;
                    this.inputFluidAmount = this.inputFluidCapacity;
                    this.outputFluidName = "depletedplutoniumbasedliquidfuel";
                }
                case HATCH_LIQUID_FUEL_URANIUM_EXCITED, HATCH_LIQUID_FUEL_THORIUM_EXCITED, HATCH_LIQUID_FUEL_PLUTONIUM_EXCITED -> {
                    String element = (this.type == TileType.HATCH_LIQUID_FUEL_URANIUM_EXCITED) ? "uranium"
                        : (this.type == TileType.HATCH_LIQUID_FUEL_THORIUM_EXCITED) ? "thorium" : "plutonium";
                    this.inputFluidName = element + "basedliquidfuelexcited";
                    this.inputFluidCapacity = NuclearSimulationEngine.hatchCoolantCapacity;
                    this.inputFluidAmount = this.inputFluidCapacity;
                    this.outputFluidName = "depleted" + element + "basedliquidfuel";
                }
                case HATCH_DISTILLED_WATER -> {
                    this.inputFluidName = "distilledwater";
                    this.inputFluidCapacity = NuclearSimulationEngine.hatchCoolantCapacity;
                    this.inputFluidAmount = this.inputFluidCapacity;
                    this.outputFluidName = "steam";
                }
                case HATCH_HEAVY_WATER -> {
                    this.inputFluidName = "heavywater";
                    this.inputFluidCapacity = NuclearSimulationEngine.hatchCoolantCapacity;
                    this.inputFluidAmount = this.inputFluidCapacity;
                    this.outputFluidName = "fluid.heavywatersteam";
                }
                case HATCH_IC2_COOLANT -> {
                    this.inputFluidName = "ic2coolant";
                    this.inputFluidCapacity = NuclearSimulationEngine.hatchCoolantCapacity;
                    this.inputFluidAmount = this.inputFluidCapacity;
                    this.outputFluidName = "ic2hotcoolant";
                }
                case COOLANT_CELL_10K -> {
                    this.maxCellHeat = 10000;
                    this.currentCellHeat = 0;
                }
                case COOLANT_CELL_60K -> {
                    this.maxCellHeat = 60000;
                    this.currentCellHeat = 0;
                }
                case COOLANT_CELL_360K -> {
                    this.maxCellHeat = 360000;
                    this.currentCellHeat = 0;
                }
                case VENT_STANDARD, VENT_ADVANCED, VENT_OVERCLOCKED -> {
                    this.maxCellHeat = 1000;
                    this.currentCellHeat = 0;
                }
                case VENT_COMPONENT -> {
                    this.maxCellHeat = 0;
                    this.currentCellHeat = 0;
                }
                case EXCHANGER_STANDARD -> {
                    this.maxCellHeat = 2500;
                    this.currentCellHeat = 0;
                }
                case EXCHANGER_ADVANCED -> {
                    this.maxCellHeat = 10000;
                    this.currentCellHeat = 0;
                }
                case EXCHANGER_COMPONENT -> {
                    this.maxCellHeat = 5000;
                    this.currentCellHeat = 0;
                }
                default -> {
                    this.inputFluidAmount = 0;
                    this.maxCellHeat = 0;
                    this.currentCellHeat = 0;
                }
            }
        }
    }

    public TileType getType() {
        return type;
    }

    @Override
    public NuclearFuelType getFuelType() {
        return NuclearFuelType.fromFuelData(getFuelData());
    }

    public String getArchetype() {
        FuelData fd = getFuelData();
        return (fd != null && fd.archetype != null) ? fd.archetype : "standard";
    }

    @Override
    public double getTemperature() {
        return temperature;
    }

    @Override
    public void setTemperature(double temp) {
        this.temperature = Math.max(-273.15, temp);
    }

    @Override
    public void addHeat(double heat) {
        this.heatHu += heat;
        this.temperature = Math.max(-273.15, this.temperature + heat / getHeatCapacity());
    }

    @Override
    public double getHeatCapacity() {
        if (isCoolantHatch() || isLiquidFuelHatch()) return NuclearSimulationEngine.getCoreHatchHeatCapacity(tier);
        if (type == TileType.PASSAGE_CORE) return CoolantPassage.heatCapacity(passageTier);
        return NuclearSimulationEngine.HU_PER_DEGREE;
    }

    public double getHeatHu() {
        return heatHu;
    }

    @Override
    public double getHeatTransferCoeff() {
        if (type == TileType.PASSAGE_CORE) return CoolantPassage.conduction();
        return TileProperties.heatTransferCoeff(getContent());
    }

    private double insulationDampening = 0.0;

    @Override
    public double getInsulationDampening() {
        ComponentData.InsulatorData ins = contentOf(type).insulator();
        return ins != null ? ins.dampening : insulationDampening;
    }

    public void setInsulationDampening(double dampening) {
        this.insulationDampening = dampening;
    }

    public boolean isInsulator() {
        return getInsulationDampening() > 0.0;
    }

    public boolean isRadiovoltaic() {
        return type == TileType.RADIOVOLTAIC_HV || type == TileType.RADIOVOLTAIC_EV;
    }

    public boolean isModerator() {
        return type == TileType.MODERATOR_GRAPHITE || type == TileType.MODERATOR_CARBON
            || type == TileType.MODERATOR_BERYLLIUM;
    }

    @Override
    public boolean isFuel() {
        return !depleted && getFuelData() != null;
    }

    @Override
    public boolean isNeutronSource() {
        return type == TileType.NEUTRON_SOURCE;
    }

    public boolean isHatch() {
        return isCoolantHatch() || isLiquidFuelHatch() || isCoolantPassage();
    }

    public boolean isCoolantPassage() {
        return type == TileType.PASSAGE_CORE;
    }

    public boolean isCoolantHatch() {
        return switch (type) {
            case HATCH_DISTILLED_WATER, HATCH_HEAVY_WATER, HATCH_IC2_COOLANT -> true;
            default -> false;
        };
    }

    public boolean isLiquidFuelHatch() {
        return switch (type) {
            case HATCH_LIQUID_FUEL_URANIUM, HATCH_LIQUID_FUEL_THORIUM, HATCH_LIQUID_FUEL_PLUTONIUM, HATCH_LIQUID_FUEL_URANIUM_EXCITED, HATCH_LIQUID_FUEL_THORIUM_EXCITED, HATCH_LIQUID_FUEL_PLUTONIUM_EXCITED -> true;
            default -> false;
        };
    }

    /** Base neutron yield per second before archetype multipliers (fuels.yaml base_neutrons). */
    public int getRawBaseNeutrons() {
        FuelData fd = getFuelData();
        return fd != null ? fd.baseNeutrons : 0;
    }

    /** Thermal fission multiplier before archetype and global multipliers (fuels.yaml). */
    public double getThermalFissionMultiplier() {
        FuelData fd = getFuelData();
        return fd != null ? fd.thermalFissionMultiplier : 1.0;
    }

    @Override
    public int generateNeutrons(double efficiency) {
        double dt = (double) getTicksPerCycle() / 20.0;
        if (isNeutronSource()) {
            double packets = neutronSourceBeamEnergy * neutronSourceBeamRate
                * NuclearSimulationEngine.beamEnergyToFastNeutronMultiplier
                * dt;
            beamlinePacketAccumulator += packets;
            int packetSize = (int) beamlinePacketAccumulator;
            beamlinePacketAccumulator -= packetSize;
            int totalNeutrons = packetSize * 4;
            lastNeutronsGenerated = totalNeutrons;
            return totalNeutrons;
        }

        if (!isFuel() || depleted) {
            lastNeutronsGenerated = 0;
            chainRate = 0.0;
            return 0;
        }

        FuelData fd = getFuelData();
        chainRate = TileProperties.chainRate(
            chainRate,
            lastThermalAbsorbed,
            lastFastAbsorbed,
            TileProperties.effectiveThermalFissionMultiplier(fd),
            TileProperties.effectiveFastFissionMultiplier(fd),
            dt);
        int produced = TileProperties.fuelNeutrons(TileProperties.effectiveBaseNeutrons(fd), chainRate, efficiency, dt);
        lastNeutronsGenerated = produced;
        return produced;
    }

    @Override
    public int getNeutronEmissionCount() {
        if (isNeutronSource()) return 4;
        if (isLiquidFuelHatch()) return TileProperties.liquidPacketCount(inputFluidAmount);
        FuelData fd = getFuelData();
        return fd != null ? TileProperties.solidPacketCount(fd) : 1;
    }

    public double getBaseAbsorptionProbability(NeutronType nType) {
        if (type == TileType.CONTROL_ROD) {
            return (nType == NeutronType.THERMAL) ? 0.95 : 0.85;
        }
        if (type == TileType.PASSAGE_CORE) {
            return CoolantPassage.absorption(nType);
        }
        return TileProperties.absorption(getContent(), nType);
    }

    public double getControlRodAbsorptionProbability(NeutronType nType) {
        if (!hasControlRod) return 0.0;
        if (controlRodType == ControlRodType.NONE || controlRodType == null) {
            return 0.01;
        }
        return TileTick.rodAbsorption(getControlRodData(), getEffectiveControlRodInsertion() / 100.0, nType);
    }

    /** The configuration record (components.yaml control_rods) of this tile's control rod, or null for none. */
    public ComponentData.ControlRodData getControlRodData() {
        if (controlRodType == null || controlRodType == ControlRodType.NONE) return null;
        return ReactorConfigData
            .findById(ReactorConfigData.getComponentData().controlRods, d -> d.id, controlRodType.configId);
    }

    @Override
    public double getControlRodAbsorptionFraction(NeutronType nType) {
        if (!hasControlRod) return 0.0;
        double pRod = getControlRodAbsorptionProbability(nType);
        double pBase = getBaseAbsorptionProbability(nType);
        double sum = pBase + pRod;
        return (sum > 0.0) ? (pRod / sum) : 0.0;
    }

    @Override
    public double getAbsorptionProbability(NeutronType nType) {
        double pBase = getBaseAbsorptionProbability(nType);
        if (hasControlRod) {
            // The simulator's rod housings hold no moderator.
            return TileTick.combinedAbsorption(pBase, getControlRodAbsorptionProbability(nType), 0.0, 0.0);
        }
        return pBase;
    }

    public double getBaseScatteringProbability(NeutronType nType) {
        if (type == TileType.PASSAGE_CORE) {
            return CoolantPassage.scattering();
        }
        return TileProperties.scattering(getContent(), nType);
    }

    @Override
    public double getScatteringProbability(NeutronType nType) {
        double pBase = getBaseScatteringProbability(nType);
        if (hasControlRod) {
            ComponentData.ControlRodData rod = getControlRodData();
            double pRodScattering = rod != null
                ? TileTick.rodScattering(rod, getEffectiveControlRodInsertion() / 100.0, nType)
                : 0.02;
            return TileTick
                .combinedScattering(pBase, getControlRodAbsorptionProbability(nType), 0.0, pRodScattering, 0.0);
        }
        return pBase;
    }

    @Override
    public double getModerationProbability() {
        // A passage moderates as the coolant of the loop it is connected to, as the game's high-pressure hatch does
        double pBase = (type == TileType.PASSAGE_CORE) ? CoolantPassage.moderation(passageLoopFluid)
            : TileProperties.moderation(getContent());
        if (hasControlRod) {
            return TileTick.combinedModeration(pBase, getControlRodAbsorptionProbability(NeutronType.FAST), 0.0);
        }
        return pBase;
    }

    @Override
    public void onNeutronAbsorbed(NeutronType nType, int count) {
        if (count <= 0) return;
        if (hasControlRod) {
            int nRod = TileTick.rodShareOfAbsorbed(
                count,
                getBaseAbsorptionProbability(nType),
                getControlRodAbsorptionProbability(nType));
            int nBase = count - nRod;

            if (nRod > 0) {
                if (nType == NeutronType.FAST) controlRodFastAbsorbed += nRod;
                else controlRodThermalAbsorbed += nRod;
            }
            if (nBase > 0) {
                onBaseNeutronAbsorbed(nType, nBase);
            }
        } else {
            onBaseNeutronAbsorbed(nType, count);
        }
    }

    public void onBaseNeutronAbsorbed(NeutronType nType, int count) {
        if (nType == NeutronType.FAST) fastAbsorbed += count;
        else thermalAbsorbed += count;

        // Fast neutron capture transmutation
        if (nType == NeutronType.FAST && isCoolantHatch() && inputFluidAmount >= 2) {
            ComponentData.CoolantFluidData cfd = contentOf(type).coolantFluid();
            if (TileTick.transmutes(
                cfd,
                count,
                NuclearSimulationEngine.random()
                    .nextInt(10000))) {
                inputFluidAmount -= 2;
                if ("deuterium".equalsIgnoreCase(cfd.transmutationProduct)) {
                    totalDeuteriumProduced += 2;
                    totalOxygenProduced += 1;
                } else if ("tritium".equalsIgnoreCase(cfd.transmutationProduct)) {
                    totalTritiumProduced += 2;
                    totalOxygenProduced += 1;
                }
            }
        }
    }

    @Override
    public void onNeutronScattered(NeutronType nType, int count) {
        if ((type == TileType.REFLECTOR_IC2 || type == TileType.REFLECTOR_IC2_THICK) && !depleted
            && maxDurability > 0) {
            for (int i = 0; i < count; i++) {
                if (NuclearSimulationEngine.random()
                    .nextInt(20) == 0) {
                    durability--;
                    if (durability <= 0) {
                        durability = 0;
                        depleted = true;
                        type = TileType.EMPTY;
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void addNeutronFlux(NeutronType nType, int count) {
        if (nType == NeutronType.FAST) fastFlux += count;
        else thermalFlux += count;
    }

    @Override
    public void nuclearTick(double efficiency) {
        lastFastFlux = fastFlux;
        lastThermalFlux = thermalFlux;
        lastTotalFlux = fastFlux + thermalFlux;
        lastFastAbsorbed = fastAbsorbed;
        lastThermalAbsorbed = thermalAbsorbed;
        lastControlRodFastAbsorbed = controlRodFastAbsorbed;
        lastControlRodThermalAbsorbed = controlRodThermalAbsorbed;
        controlRodFastAbsorbed = 0;
        controlRodThermalAbsorbed = 0;
        lastTickProduced = 0;
        lastDurabilityLoss = 0.0;
        lastLiquidFuelBurned = 0;
        lastHeatOutput = 0.0;

        // 1. Fuel burnup (3 physical processes: emitting fast neutrons, absorbing any neutron, and temperature above
        // ambient)
        double dt = (double) getTicksPerCycle() / 20.0;
        if (isLiquidFuelHatch()) {
            if (inputFluidAmount > 0) {
                FuelData fd = getFuelData();
                double tempDmg = (fd != null) ? fd.calculateTemperatureDamage(temperature)
                    : TileTick.genericFuelTemperatureDamage(temperature);
                int burn = Math.max(
                    1,
                    TileTick.stochasticRound(
                        TileTick.fuelWear(tempDmg, lastNeutronsGenerated, fastAbsorbed, thermalAbsorbed, dt)));
                int toConsume = Math.max(1, Math.min(inputFluidAmount, burn));
                inputFluidAmount -= toConsume;
                outputFluidAmount += toConsume;
                lastLiquidFuelBurned = toConsume;
                if (inputFluidAmount <= 0) {
                    inputFluidAmount = 0;
                    depleted = true;
                }
            } else {
                depleted = true;
            }
        } else if (isFuel() && !depleted) {
            double rawDamage = TileTick.fuelWear(
                getFuelData().calculateTemperatureDamage(temperature),
                lastNeutronsGenerated,
                fastAbsorbed,
                thermalAbsorbed,
                dt);
            lastDurabilityLoss = rawDamage;
            totalDurabilityLoss += rawDamage;
            wearCycles++;
            int intDamage = TileTick.stochasticRound(rawDamage);
            if (intDamage > 0) {
                durability = Math.max(0, durability - intDamage);
                if (durability == 0) {
                    depleted = true;
                }
            }
        } else if (isModerator() && !depleted) {
            double rawDamage = TileTick.moderatorWear(
                getModeratorData(),
                temperature,
                fastAbsorbed,
                thermalAbsorbed,
                fastFlux + thermalFlux,
                dt);
            lastDurabilityLoss = rawDamage;
            totalDurabilityLoss += rawDamage;
            wearCycles++;
            int intDamage = TileTick.stochasticRound(rawDamage);
            if (intDamage > 0) {
                durability = Math.max(0, durability - intDamage);
                if (durability == 0) {
                    depleted = true;
                    type = TileType.EMPTY;
                }
            }
        }

        // Radiovoltaic direct EU generation & excess heat
        if (isRadiovoltaic()) {
            com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.RadiovoltaicData rv = getRadiovoltaicData();
            double weightedAbsorbed = fastAbsorbed * 4.0 + thermalAbsorbed * 1.0;
            radiovoltaicRate = TileTick.radiovoltaicRate(radiovoltaicRate, weightedAbsorbed, dt);
            long genEU = Math.round(rv.calculatePower(radiovoltaicRate));
            this.directEUProduced = genEU;
            double excessHeat = com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.RadiovoltaicData
                .excessHeat(weightedAbsorbed, genEU, dt);
            if (excessHeat > 0.0) {
                addHeat(excessHeat);
            }
        } else {
            this.directEUProduced = 0;
        }

        fastFlux = 0;
        thermalFlux = 0;
        fastAbsorbed = 0;
        thermalAbsorbed = 0;

        // 2. Heat Vent & Coolant cell absorption & venting
        if (isCoolantCell()) {
            ComponentData.CoolantItemData cItem = contentOf(type).coolantItem();
            double huPerDurability = (cItem != null) ? cItem.getHuPerDurability() : 25.0;
            int take = TileTick.coolantCellUptake(
                maxCellHeat,
                currentCellHeat,
                temperature,
                NuclearSimulationEngine.AMBIENT_TEMP,
                huPerDurability,
                efficiency,
                dt);
            if (take > 0) {
                currentCellHeat += take;
                double heatConsumed = take * huPerDurability;
                temperature -= heatConsumed / NuclearSimulationEngine.HU_PER_DEGREE;
                lastHeatOutput = heatConsumed;
            }
        }

        // 3. Coolant turnover: the heat left the hatch during the conduction sub-steps (coolantSubstep); here it
        // becomes coolant consumed and hot fluid produced.
        ComponentData.CoolantFluidData cfd = activeCoolant();
        double pendingHeat = pendingCoolantHeat;
        pendingCoolantHeat = 0.0;
        if (cfd != null && pendingHeat > 0.0) {
            int mbToCool = Math.min(inputFluidAmount, TileTick.coolantConsumed(cfd, pendingHeat));
            if (mbToCool > 0) {
                int fluidOut = mbToCool * cfd.expansionRatio;
                inputFluidAmount -= mbToCool;
                lastTickProduced = fluidOut;
                outputFluidAmount += fluidOut;
                if (type != TileType.HATCH_IC2_COOLANT) {
                    totalSteamProduced += fluidOut;
                }
                lastHeatOutput = pendingHeat;
                lastCoolingDetails = String.format(
                    java.util.Locale.US,
                    "%s %dL (cap %dL, heatPerL %.1f), consumed %.1f Hu, temp %.1f°C",
                    type.displayName,
                    mbToCool,
                    inputFluidCapacity,
                    cfd.heatPerLiter,
                    pendingHeat,
                    temperature);
            }
        }
        if (isCoolantHatch() && inputFluidAmount <= 0) {
            inputFluidAmount = 0;
            wasDry = true;
        }
    }

    /** The coolant this hatch can cool with right now, or null (not a coolant hatch, empty, or pipe tier too low). */
    private ComponentData.CoolantFluidData activeCoolant() {
        if (!isCoolantHatch() || inputFluidAmount <= 0) return null;
        ComponentData.CoolantFluidData cfd = contentOf(type).coolantFluid();
        if (cfd == null) return null;
        if (grid != null && grid.getPipeTier() >= 0 && grid.getPipeTier() < cfd.requiredTier) return null;
        return cfd;
    }

    @Override
    public double coolantSubstep(double dtSub, double efficiency) {
        ComponentData.CoolantFluidData cfd = activeCoolant();
        if (cfd == null) return 0.0;
        double sink = TileTick.coolantSinkTemperature(cfd, NuclearSimulationEngine.AMBIENT_TEMP);
        double remaining = inputFluidAmount * cfd.heatPerLiter - pendingCoolantHeat;
        double q = TileTick.coolantSubstepHeat(temperature, sink, tier, efficiency, dtSub, remaining);
        if (q > 0.0) {
            pendingCoolantHeat += q;
            temperature -= q / getHeatCapacity();
        }
        return q;
    }

    /**
     * Refills coolant fluid, respecting wasDry explosion safety checks.
     * 
     * @return true if refilled safely, false if thermal shock explosion triggered
     */
    public boolean refillCoolant() {
        return refillCoolant(inputFluidCapacity);
    }

    public boolean refillCoolant(int maxFeed) {
        if (inputFluidAmount >= inputFluidCapacity) return true;
        if (com.gtnewhorizons.modularnuclear.common.nuclear.CoreHatchRules
            .checkCoolantFeed(inputFluidName, wasDry, temperature, NuclearSimulationEngine.ambientTemp)
            != com.gtnewhorizons.modularnuclear.common.nuclear.CoreHatchRules.CoolantFeed.SAFE) {
            return false; // Thermal shock
        }
        wasDry = false;
        int toAdd = Math.min(maxFeed, inputFluidCapacity - inputFluidAmount);
        inputFluidAmount += toAdd;
        return true;
    }

    public void setInputFluidCapacity(int capacity) {
        this.inputFluidCapacity = Math.max(100, capacity);
        if (this.inputFluidAmount > this.inputFluidCapacity) {
            this.inputFluidAmount = this.inputFluidCapacity;
        }
    }

    public void setInputFluidAmount(int amount) {
        this.inputFluidAmount = Math.max(0, Math.min(amount, inputFluidCapacity));
        if (this.inputFluidAmount > 0) {
            this.wasDry = false;
        }
    }

    // Getters and helper status methods
    public int getDurability() {
        return durability;
    }

    public int getMaxDurability() {
        return maxDurability;
    }

    public double getDurabilityPercent() {
        return maxDurability <= 0 ? 0.0 : (durability * 100.0 / maxDurability);
    }

    public boolean isDepleted() {
        return depleted;
    }

    public void setDepleted(boolean depleted) {
        this.depleted = depleted;
    }

    public void setDurability(int durability) {
        this.durability = Math.max(0, Math.min(maxDurability, durability));
        if (this.durability == 0) {
            this.depleted = true;
        }
    }

    public double getLastDurabilityLoss() {
        return lastDurabilityLoss;
    }

    /** Mean durability loss per cycle since this tile's content was placed (less noisy than the last cycle's). */
    public double getAverageDurabilityLoss() {
        return wearCycles > 0 ? totalDurabilityLoss / wearCycles : lastDurabilityLoss;
    }

    public int getLastLiquidFuelBurned() {
        return lastLiquidFuelBurned;
    }

    public boolean isDepletionLogged() {
        return depletionLogged;
    }

    public void setDepletionLogged(boolean depletionLogged) {
        this.depletionLogged = depletionLogged;
    }

    public String getDepletedDisplayName() {
        return switch (type) {
            case FUEL_URANIUM_SINGLE -> "Depleted Uranium Single";
            case FUEL_URANIUM_DUAL -> "Depleted Uranium Dual";
            case FUEL_URANIUM_QUAD -> "Depleted Uranium Quad";
            case FUEL_MOX_SINGLE -> "Depleted MOX Single";
            case FUEL_MOX_DUAL -> "Depleted MOX Dual";
            case FUEL_MOX_QUAD -> "Depleted MOX Quad";
            case FUEL_THORIUM_SINGLE -> "Depleted Thorium Single";
            case FUEL_THORIUM_DUAL -> "Depleted Thorium Dual";
            case FUEL_THORIUM_QUAD -> "Depleted Thorium Quad";
            case FUEL_GLOWSTONE -> "Depleted Glowstone Rod";
            case FUEL_LITHIUM -> "Depleted Lithium Rod";

            case FUEL_HD_URANIUM_SINGLE -> "Depleted HD Uranium Single";
            case FUEL_HD_URANIUM_DUAL -> "Depleted HD Uranium Dual";
            case FUEL_HD_URANIUM_QUAD -> "Depleted HD Uranium Quad";

            case FUEL_HD_PLUTONIUM_SINGLE -> "Depleted HD Plutonium Single";
            case FUEL_HD_PLUTONIUM_DUAL -> "Depleted HD Plutonium Dual";
            case FUEL_HD_PLUTONIUM_QUAD -> "Depleted HD Plutonium Quad";

            case FUEL_EXCITED_URANIUM_SINGLE -> "Depleted Excited Uranium Single";
            case FUEL_EXCITED_URANIUM_DUAL -> "Depleted Excited Uranium Dual";
            case FUEL_EXCITED_URANIUM_QUAD -> "Depleted Excited Uranium Quad";

            case FUEL_EXCITED_PLUTONIUM_SINGLE -> "Depleted Excited Plutonium Single";
            case FUEL_EXCITED_PLUTONIUM_DUAL -> "Depleted Excited Plutonium Dual";
            case FUEL_EXCITED_PLUTONIUM_QUAD -> "Depleted Excited Plutonium Quad";

            case FUEL_NAQUADAH_SINGLE -> "Depleted Naquadah Single";
            case FUEL_NAQUADAH_DUAL -> "Depleted Naquadah Dual";
            case FUEL_NAQUADAH_QUAD -> "Depleted Naquadah Quad";

            case FUEL_NAQUADRIA_SINGLE -> "Depleted Naquadria Single";
            case FUEL_NAQUADRIA_DUAL -> "Depleted Naquadria Dual";
            case FUEL_NAQUADRIA_QUAD -> "Depleted Naquadria Quad";

            case FUEL_TIBERIUM_SINGLE -> "Depleted Tiberium Single";
            case FUEL_TIBERIUM_DUAL -> "Depleted Tiberium Dual";
            case FUEL_TIBERIUM_QUAD -> "Depleted Tiberium Quad";

            case FUEL_CORE -> "Depleted The Core";
            case HATCH_LIQUID_FUEL_URANIUM, HATCH_LIQUID_FUEL_URANIUM_EXCITED -> "Depleted Uranium Liquid Fuel";
            case HATCH_LIQUID_FUEL_THORIUM, HATCH_LIQUID_FUEL_THORIUM_EXCITED -> "Depleted Thorium Liquid Fuel";
            case HATCH_LIQUID_FUEL_PLUTONIUM, HATCH_LIQUID_FUEL_PLUTONIUM_EXCITED -> "Depleted Plutonium Liquid Fuel";
            default -> "Depleted Fuel";
        };
    }

    public String getDepletedCode() {
        return switch (type) {
            case FUEL_URANIUM_SINGLE -> "DU1";
            case FUEL_URANIUM_DUAL -> "DU2";
            case FUEL_URANIUM_QUAD -> "DU4";
            case FUEL_MOX_SINGLE -> "DM1";
            case FUEL_MOX_DUAL -> "DM2";
            case FUEL_MOX_QUAD -> "DM4";
            case FUEL_THORIUM_SINGLE -> "DT1";
            case FUEL_THORIUM_DUAL -> "DT2";
            case FUEL_THORIUM_QUAD -> "DT4";
            case FUEL_GLOWSTONE -> "DG1";
            case FUEL_LITHIUM -> "DLI1";

            case FUEL_HD_URANIUM_SINGLE -> "DHDU1";
            case FUEL_HD_URANIUM_DUAL -> "DHDU2";
            case FUEL_HD_URANIUM_QUAD -> "DHDU4";

            case FUEL_HD_PLUTONIUM_SINGLE -> "DHDP1";
            case FUEL_HD_PLUTONIUM_DUAL -> "DHDP2";
            case FUEL_HD_PLUTONIUM_QUAD -> "DHDP4";

            case FUEL_EXCITED_URANIUM_SINGLE -> "DEXU1";
            case FUEL_EXCITED_URANIUM_DUAL -> "DEXU2";
            case FUEL_EXCITED_URANIUM_QUAD -> "DEXU4";

            case FUEL_EXCITED_PLUTONIUM_SINGLE -> "DEXP1";
            case FUEL_EXCITED_PLUTONIUM_DUAL -> "DEXP2";
            case FUEL_EXCITED_PLUTONIUM_QUAD -> "DEXP4";

            case FUEL_NAQUADAH_SINGLE -> "DNQ1";
            case FUEL_NAQUADAH_DUAL -> "DNQ2";
            case FUEL_NAQUADAH_QUAD -> "DNQ4";

            case FUEL_NAQUADRIA_SINGLE -> "DNQR1";
            case FUEL_NAQUADRIA_DUAL -> "DNQR2";
            case FUEL_NAQUADRIA_QUAD -> "DNQR4";

            case FUEL_TIBERIUM_SINGLE -> "DTIB1";
            case FUEL_TIBERIUM_DUAL -> "DTIB2";
            case FUEL_TIBERIUM_QUAD -> "DTIB4";

            case FUEL_CORE -> "DNQ32";
            case HATCH_LIQUID_FUEL_URANIUM, HATCH_LIQUID_FUEL_URANIUM_EXCITED -> "DLFU";
            case HATCH_LIQUID_FUEL_THORIUM, HATCH_LIQUID_FUEL_THORIUM_EXCITED -> "DLFT";
            case HATCH_LIQUID_FUEL_PLUTONIUM, HATCH_LIQUID_FUEL_PLUTONIUM_EXCITED -> "DLFP";
            default -> "DF";
        };
    }

    public int getLastNeutronsGenerated() {
        return lastNeutronsGenerated;
    }

    public int getLastTickProduced() {
        return lastTickProduced;
    }

    public String getInputFluidName() {
        return inputFluidName;
    }

    public int getInputFluidAmount() {
        return inputFluidAmount;
    }

    public int getInputFluidCapacity() {
        return inputFluidCapacity;
    }

    public String getOutputFluidName() {
        return outputFluidName;
    }

    public int getOutputFluidAmount() {
        return outputFluidAmount;
    }

    public void setOutputFluidAmount(int amount) {
        this.outputFluidAmount = Math.max(0, amount);
    }

    public int getTotalSteamProduced() {
        return totalSteamProduced;
    }

    public int getTotalDeuteriumProduced() {
        return totalDeuteriumProduced;
    }

    public int getTotalTritiumProduced() {
        return totalTritiumProduced;
    }

    public int getTotalOxygenProduced() {
        return totalOxygenProduced;
    }

    public boolean isWasDry() {
        return wasDry;
    }

    public void setWasDry(boolean wasDry) {
        this.wasDry = wasDry;
    }

    public int getTier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = tier;
    }

    public boolean isAutoRefill() {
        return autoRefill;
    }

    public void setAutoRefill(boolean autoRefill) {
        this.autoRefill = autoRefill;
    }

    public int getMaxCellHeat() {
        return maxCellHeat;
    }

    public int getCurrentCellHeat() {
        return currentCellHeat;
    }

    public int getFastFlux() {
        return fastFlux;
    }

    public int getThermalFlux() {
        return thermalFlux;
    }

    public int getFastAbsorbed() {
        return fastAbsorbed;
    }

    public int getThermalAbsorbed() {
        return thermalAbsorbed;
    }

    public int getLastFastFlux() {
        return lastFastFlux;
    }

    public int getLastThermalFlux() {
        return lastThermalFlux;
    }

    public int getLastTotalFlux() {
        return lastTotalFlux;
    }

    public int getLastFastAbsorbed() {
        return lastFastAbsorbed;
    }

    public int getLastThermalAbsorbed() {
        return lastThermalAbsorbed;
    }

    public boolean isCoolantCell() {
        return switch (type) {
            case COOLANT_CELL_10K, COOLANT_CELL_60K, COOLANT_CELL_360K -> true;
            default -> false;
        };
    }

    @Override
    public boolean isHeatVent() {
        return switch (type) {
            case VENT_STANDARD, VENT_ADVANCED, VENT_OVERCLOCKED, VENT_COMPONENT -> true;
            default -> false;
        };
    }

    /** Defaults matching components.yaml (HV: 1024 EU/t, EV: 4096 EU/t; 95% output at 10 absorbed per second). */
    public ComponentData.RadiovoltaicData getRadiovoltaicData() {
        return contentOf(type).radiovoltaic();
    }

    @Override
    public ComponentData.HeatVentData getHeatVentData() {
        return contentOf(type).heatVent();
    }

    @Override
    public boolean isHeatExchanger() {
        return switch (type) {
            case EXCHANGER_STANDARD, EXCHANGER_ADVANCED, EXCHANGER_COMPONENT -> true;
            default -> false;
        };
    }

    @Override
    public ComponentData.HeatExchangerData getHeatExchangerData() {
        return contentOf(type).heatExchanger();
    }

    public long drainedEnergy = 0;
    public boolean energyDrainingAllowed = true;

    @Override
    public boolean drainEnergy(long eu) {
        if (!energyDrainingAllowed) return false;
        drainedEnergy += eu;
        return true;
    }

    @Override
    public long getAvailableEnergy() {
        return energyDrainingAllowed ? Long.MAX_VALUE : 0L;
    }

    public boolean canStoreHeat() {
        return maxCellHeat > 0;
    }

    public void setCurrentCellHeat(int heat) {
        this.currentCellHeat = Math.max(0, Math.min(maxCellHeat, heat));
    }

    public int alterCellHeat(int delta) {
        int target = this.currentCellHeat + delta;
        if (target > maxCellHeat) {
            int overflow = target - maxCellHeat;
            this.currentCellHeat = maxCellHeat;
            return overflow;
        } else if (target < 0) {
            int underflow = target;
            this.currentCellHeat = 0;
            return underflow;
        } else {
            this.currentCellHeat = target;
            return 0;
        }
    }

    public void processNeighborComponents(INuclearTile[][] grid, int x, int y, int sizeX, int sizeY) {
        NuclearSimulationEngine
            .processActiveVentsAndExchangers(grid, sizeX, sizeY, NuclearSimulationEngine.ambientTemp);
    }

    public double storedWignerEnergy = 0.0;

    @Override
    public void addWignerEnergy(double amountHu) {
        ComponentData.ModeratorData mod = getModeratorData();
        if (mod != null && mod.wignerEnergyPerFast > 0.0 && amountHu > 0.0) {
            double maxCap = mod.wignerMaxEnergy > 0.0 ? mod.wignerMaxEnergy : 10000.0;
            storedWignerEnergy = Math.min(maxCap, storedWignerEnergy + amountHu);
        }
    }

    @Override
    public double releaseWignerEnergy() {
        ComponentData.ModeratorData mod = getModeratorData();
        if (mod == null || mod.wignerEnergyPerFast <= 0.0) {
            storedWignerEnergy = 0.0;
            return 0.0;
        }
        if (storedWignerEnergy <= 0.0) return 0.0;
        double released = mod.calculateWignerHeatRelease(temperature, storedWignerEnergy);
        if (released > 0.0) storedWignerEnergy = Math.max(0.0, storedWignerEnergy - released);
        return released;
    }

    @Override
    public double getStoredWignerEnergy() {
        return storedWignerEnergy;
    }

    @Override
    public ComponentData.ModeratorData getModeratorData() {
        return contentOf(type).moderator();
    }

    @Override
    public double getThermalNeutronEmissionYield(int fastNeutronsAbsorbed) {
        return 0.0;
    }

    @Override
    public void voidComponent() {
        this.type = TileType.EMPTY;
    }
}
