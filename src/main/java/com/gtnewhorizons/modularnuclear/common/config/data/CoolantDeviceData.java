package com.gtnewhorizons.modularnuclear.common.config.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data-driven configuration container for loop devices (PHE, Degasser, Creative Reservoir).
 */
public class CoolantDeviceData {

    public static class PHEData {

        public static class SecondaryCoolantEntry {

            public String inputFluidName;
            public double boilingPointCelsius;
            public String outputGasName;
            public double latentHeatJoulePerLiter;
            public int expansionRatio;
            public boolean isSteam;

            /**
             * The heat a liter takes up boiling, set in game from the electricity a vacuum freezer spends making that
             * liter (FreezerRecipeEnergy); 0 where the freezer does not make this fluid, or outside the game.
             */
            public transient double recipeLatentHeatJoulePerLiter;
            public transient boolean recipeLatentHeatResolved;

            /** The heat a liter takes up boiling: the freezer's energy for it where it makes it, else as configured. */
            public double latentHeat() {
                return recipeLatentHeatJoulePerLiter > 0.0 ? recipeLatentHeatJoulePerLiter : latentHeatJoulePerLiter;
            }

            public SecondaryCoolantEntry() {}

            public SecondaryCoolantEntry(String inputFluidName, double boilingPointCelsius, String outputGasName,
                double latentHeatJoulePerLiter, int expansionRatio, boolean isSteam) {
                this.inputFluidName = inputFluidName;
                this.boilingPointCelsius = boilingPointCelsius;
                this.outputGasName = outputGasName;
                this.latentHeatJoulePerLiter = latentHeatJoulePerLiter;
                this.expansionRatio = expansionRatio;
                this.isSteam = isSteam;
            }

            public Map<String, Object> toMap() {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("inputFluidName", inputFluidName);
                map.put("boilingPointCelsius", boilingPointCelsius);
                map.put("outputGasName", outputGasName);
                map.put("latentHeatJoulePerLiter", latentHeatJoulePerLiter);
                map.put("expansionRatio", expansionRatio);
                map.put("isSteam", isSteam);
                return map;
            }

            public static SecondaryCoolantEntry fromMap(Map<String, Object> map) {
                SecondaryCoolantEntry e = new SecondaryCoolantEntry();
                if (map == null) return e;
                if (map.containsKey("inputFluidName")) e.inputFluidName = String.valueOf(map.get("inputFluidName"));
                if (map.containsKey("boilingPointCelsius"))
                    e.boilingPointCelsius = ((Number) map.get("boilingPointCelsius")).doubleValue();
                if (map.containsKey("outputGasName")) e.outputGasName = String.valueOf(map.get("outputGasName"));
                if (map.containsKey("latentHeatJoulePerLiter"))
                    e.latentHeatJoulePerLiter = ((Number) map.get("latentHeatJoulePerLiter")).doubleValue();
                if (map.containsKey("expansionRatio"))
                    e.expansionRatio = ((Number) map.get("expansionRatio")).intValue();
                if (map.containsKey("isSteam")) e.isSteam = Boolean.parseBoolean(String.valueOf(map.get("isSteam")));
                return e;
            }
        }

        /** Scales heat transfer across every PHE's exchange area (set by its size and sheetmetal tier). */
        public double heatTransferMultiplier = 1.0;
        public double minorLossK = 1.2; // Heat exchanger core resistance
        public double latentHeatJoulePerLiter = 2260000.0; // Latent heat of vaporization for water
        public int steamExpansionRatio = 160; // 1 L water -> 160 L steam
        public double steamLitersPerEU = 2.0; // Steam a turbine at 100% efficiency takes per EU

        /**
         * The heat one EU is worth: what a PHE spends boiling the water whose steam a turbine at 100% efficiency turns
         * into that EU (2.26 MJ per liter of water, 160 L of steam, 2 L of steam per EU: 28250 J).
         */
        public double joulesPerEU() {
            return latentHeatJoulePerLiter / Math.max(1, steamExpansionRatio) * steamLitersPerEU;
        }

        public double superheatedTempThreshold = 300.0; // deg C for superheated steam
        public double supercriticalTempThreshold = 700.0; // deg C for supercritical steam
        public double approachMinDeltaT = 5.0; // Minimum approach temperature difference (pinch point) in deg C
        public double superheatThresholdDeltaT = 10.0; // Minimum superheat delta above boiling point for producing
                                                       // superheated vapor
        public double baseMaxHeatPerBatchJoules = 2260000.0; // Base heat transfer cap per 20-tick batch at 1A LV (32
                                                             // EU/t) (equivalent to 1 L distilled water)
        public int batchTicks = 20; // Batch duration (20 ticks = 1 second)
        /**
         * Further fluids a PHE may boil, by exact fluid registry name, as steam at 100 °C: fluids with an entry in
         * secondaryCoolants need none. Plain water boils only if listed here.
         */
        public List<String> allowedSecondaryFluids = new ArrayList<>();
        public List<SecondaryCoolantEntry> secondaryCoolants = new ArrayList<>(
            Arrays.asList(
                new SecondaryCoolantEntry("ic2distilledwater", 100.0, "steam", 2260000.0, 160, true),
                // Heavy water boils to heavy-water steam of the same grades (PheExchange.steamOfFamily)
                new SecondaryCoolantEntry("heavywater", 101.4, "heavywatersteam", 2300000.0, 160, true),
                // Cryogens take up the heat a vacuum freezer's electricity for making them is worth (joulesPerEU):
                // in game from its recipes (FreezerRecipeEnergy), and here as GregTech's stock recipes have it
                // (nitrogen and oxygen 576 EU/L, air 13.44 EU/L). The freezer makes no liquid helium
                new SecondaryCoolantEntry("liquidnitrogen", -196.0, "nitrogen", 16272000.0, 1, false),
                new SecondaryCoolantEntry("liquidoxygen", -183.0, "oxygen", 16272000.0, 1, false),
                new SecondaryCoolantEntry("liquidair", -194.0, "air", 379680.0, 1, false),
                new SecondaryCoolantEntry("liquid helium", -269.0, "helium", 20700.0, 1, false)));

        public boolean isAllowedSecondaryFluid(String fluidName) {
            return findSecondaryCoolant(fluidName) != null;
        }

        /** findSecondaryCoolant's answers by fluid name; the configuration is fixed once loaded. */
        private final Map<String, Object> secondaryByName = new java.util.concurrent.ConcurrentHashMap<>();
        private static final Object NO_SECONDARY = new Object();

        /**
         * The secondary coolant entry a fluid of exactly this registry name boils as, or null. Exchangers ask every
         * tick, so answers are kept per name: a name gets the same entry each time.
         */
        public SecondaryCoolantEntry findSecondaryCoolant(String fluidName) {
            if (fluidName == null) return null;
            Object hit = secondaryByName.get(fluidName);
            if (hit == null) {
                SecondaryCoolantEntry found = scanSecondaryCoolant(fluidName);
                hit = found != null ? found : NO_SECONDARY;
                secondaryByName.put(fluidName, hit);
            }
            return hit == NO_SECONDARY ? null : (SecondaryCoolantEntry) hit;
        }

        private SecondaryCoolantEntry scanSecondaryCoolant(String fluidName) {
            for (SecondaryCoolantEntry entry : secondaryCoolants) {
                if (entry != null && fluidName.equals(entry.inputFluidName)) {
                    return entry;
                }
            }
            if (allowedSecondaryFluids.contains(fluidName)) {
                return new SecondaryCoolantEntry(
                    fluidName,
                    100.0,
                    "steam",
                    latentHeatJoulePerLiter,
                    steamExpansionRatio,
                    true);
            }
            return null;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("heatTransferMultiplier", heatTransferMultiplier);
            map.put("minorLossK", minorLossK);
            map.put("latentHeatJoulePerLiter", latentHeatJoulePerLiter);
            map.put("steamExpansionRatio", steamExpansionRatio);
            map.put("steamLitersPerEU", steamLitersPerEU);
            map.put("superheatedTempThreshold", superheatedTempThreshold);
            map.put("supercriticalTempThreshold", supercriticalTempThreshold);
            map.put("approachMinDeltaT", approachMinDeltaT);
            map.put("superheatThresholdDeltaT", superheatThresholdDeltaT);
            map.put("allowedSecondaryFluids", allowedSecondaryFluids);
            List<Map<String, Object>> coolantMaps = new ArrayList<>();
            for (SecondaryCoolantEntry e : secondaryCoolants) {
                if (e != null) coolantMaps.add(e.toMap());
            }
            map.put("secondaryCoolants", coolantMaps);
            return map;
        }

        @SuppressWarnings("unchecked")
        public static PHEData fromMap(Map<String, Object> map) {
            PHEData d = new PHEData();
            if (map == null) return d;
            if (map.containsKey("heatTransferMultiplier"))
                d.heatTransferMultiplier = ((Number) map.get("heatTransferMultiplier")).doubleValue();
            if (map.containsKey("minorLossK")) d.minorLossK = ((Number) map.get("minorLossK")).doubleValue();
            if (map.containsKey("latentHeatJoulePerLiter"))
                d.latentHeatJoulePerLiter = ((Number) map.get("latentHeatJoulePerLiter")).doubleValue();
            if (map.containsKey("steamExpansionRatio"))
                d.steamExpansionRatio = ((Number) map.get("steamExpansionRatio")).intValue();
            if (map.containsKey("steamLitersPerEU"))
                d.steamLitersPerEU = ((Number) map.get("steamLitersPerEU")).doubleValue();
            if (map.containsKey("superheatedTempThreshold"))
                d.superheatedTempThreshold = ((Number) map.get("superheatedTempThreshold")).doubleValue();
            if (map.containsKey("supercriticalTempThreshold"))
                d.supercriticalTempThreshold = ((Number) map.get("supercriticalTempThreshold")).doubleValue();
            if (map.containsKey("approachMinDeltaT"))
                d.approachMinDeltaT = ((Number) map.get("approachMinDeltaT")).doubleValue();
            if (map.containsKey("superheatThresholdDeltaT"))
                d.superheatThresholdDeltaT = ((Number) map.get("superheatThresholdDeltaT")).doubleValue();
            if (map.containsKey("allowedSecondaryFluids") && map.get("allowedSecondaryFluids") instanceof List) {
                d.allowedSecondaryFluids = new ArrayList<>();
                for (Object item : (List<?>) map.get("allowedSecondaryFluids")) {
                    if (item != null) {
                        d.allowedSecondaryFluids.add(item.toString());
                    }
                }
            }
            if (map.containsKey("secondaryCoolants") && map.get("secondaryCoolants") instanceof List) {
                d.secondaryCoolants = new ArrayList<>();
                for (Object item : (List<?>) map.get("secondaryCoolants")) {
                    if (item instanceof Map) {
                        d.secondaryCoolants.add(SecondaryCoolantEntry.fromMap((Map<String, Object>) item));
                    }
                }
            }
            return d;
        }

        public double getApproachMinDeltaT() {
            return approachMinDeltaT;
        }

        public void setApproachMinDeltaT(double approachMinDeltaT) {
            this.approachMinDeltaT = approachMinDeltaT;
        }

        public double getSuperheatThresholdDeltaT() {
            return superheatThresholdDeltaT;
        }

        public void setSuperheatThresholdDeltaT(double superheatThresholdDeltaT) {
            this.superheatThresholdDeltaT = superheatThresholdDeltaT;
        }
    }

    public static class DegasserData {

        public double minorLossK = 1.5; // Column pack resistance
        public double gasExtractionEfficiency = 0.25; // Extraction fraction
        public double gasExtractionPowerEuPerLiter = 8.0; // EU cost per liter extracted

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("minorLossK", minorLossK);
            map.put("gasExtractionEfficiency", gasExtractionEfficiency);
            map.put("gasExtractionPowerEuPerLiter", gasExtractionPowerEuPerLiter);
            return map;
        }

        public static DegasserData fromMap(Map<String, Object> map) {
            DegasserData d = new DegasserData();
            if (map == null) return d;
            if (map.containsKey("minorLossK")) d.minorLossK = ((Number) map.get("minorLossK")).doubleValue();
            if (map.containsKey("gasExtractionEfficiency"))
                d.gasExtractionEfficiency = ((Number) map.get("gasExtractionEfficiency")).doubleValue();
            if (map.containsKey("gasExtractionPowerEuPerLiter"))
                d.gasExtractionPowerEuPerLiter = ((Number) map.get("gasExtractionPowerEuPerLiter")).doubleValue();
            return d;
        }
    }

    public static class ReservoirData {

        public double exchangeArea = 2.0; // Heat exchange surface area in m^2
        public List<Double> temperaturePresets = new ArrayList<>(Arrays.asList(20.0, 100.0, 300.0, 600.0, 1000.0));

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("exchangeArea", exchangeArea);
            map.put("temperaturePresets", temperaturePresets);
            return map;
        }

        public static ReservoirData fromMap(Map<String, Object> map) {
            ReservoirData d = new ReservoirData();
            if (map == null) return d;
            if (map.containsKey("exchangeArea")) d.exchangeArea = ((Number) map.get("exchangeArea")).doubleValue();
            if (map.containsKey("temperaturePresets") && map.get("temperaturePresets") instanceof List) {
                d.temperaturePresets.clear();
                for (Object o : (List<?>) map.get("temperaturePresets")) {
                    if (o instanceof Number) d.temperaturePresets.add(((Number) o).doubleValue());
                }
            }
            return d;
        }
    }

    public static class CoolantBathData {

        public long fillVolumePerSlotLiters = 1000L; // Coolant fill volume per slot in Liters
        public double baseMinorLossK = 1.0; // Base minor loss coefficient of empty bath
        public double itemMinorLossK = 1.0; // Additional minor loss K per active item in bath
        /**
         * The hottest an object may be for bare skin to touch it for any length of time: NASA's touch temperature limit
         * for all materials and contact times until 1995, from pain onset on heated aluminum plates, and still the
         * screening temperature below which any common material is safe to touch. E. Ungar and K. Stroud, "A New
         * Approach to Defining Human Touch Temperature Standards", 40th International Conference on Environmental
         * Systems (AIAA), Barcelona, 2010, NASA JSC-CN-20089, pp. 1 and 6:
         * https://ntrs.nasa.gov/api/citations/20100020960/downloads/20100020960.pdf
         */
        public static final double TOUCH_SAFE_TEMP_C = 45.0;

        /** A hot ingot at or below this has cooled into its ingot: one safe to hold in a bare hand. */
        public double ingotCooledTempC = TOUCH_SAFE_TEMP_C;

        /**
         * The share of a vacuum freezer's electricity for a change of state (cooling a hot ingot, liquefying a gas)
         * that an item in a bath holds as heat to give off, or cold to take heat up with: most of what the freezer
         * draws is lost in the machine, not taken out of the item.
         */
        public double recoverableEnergyFraction = 0.2;

        /**
         * An item's own heat and pace, in place of what the vacuum freezer's recipe for it gives: by item
         * ("modid:name" or "modid:name:meta"), the heat it holds in joules (heatJoules) and the seconds it takes over
         * it (seconds, see CoolantBathRegistry.exchangeArea). Either may be left out.
         */
        public static class ItemOverride {

            public String item;
            public double heatJoules = -1.0;
            public double seconds = -1.0;

            public Map<String, Object> toMap() {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("item", item);
                if (heatJoules > 0.0) map.put("heatJoules", heatJoules);
                if (seconds > 0.0) map.put("seconds", seconds);
                return map;
            }

            public static ItemOverride fromMap(Map<String, Object> map) {
                ItemOverride o = new ItemOverride();
                if (map == null) return o;
                if (map.get("item") != null) o.item = String.valueOf(map.get("item"));
                if (map.get("heatJoules") instanceof Number)
                    o.heatJoules = ((Number) map.get("heatJoules")).doubleValue();
                if (map.get("seconds") instanceof Number) o.seconds = ((Number) map.get("seconds")).doubleValue();
                return o;
            }
        }

        public List<ItemOverride> itemOverrides = new ArrayList<>();

        /** The share of the heat leaving an item that reaches the coolant, under the lowest glass a bath takes (EV). */
        public double glassEfficiencyBase = 0.5;
        /** What each glass tier above that adds to it. */
        public double glassEfficiencyPerTier = 0.05;

        /** The bounds a bath's efficiency stays within, whatever its glass and configuration. */
        public static final double MIN_EFFICIENCY = 0.5;
        public static final double MAX_EFFICIENCY = 0.9;
        /** The lowest glass tier a bath takes: EV. */
        public static final int BASE_GLASS_TIER = 4;

        /** The share of the heat an item gives off that reaches the coolant, in a bath under glass of this tier. */
        public double efficiency(int glassTier) {
            double e = glassEfficiencyBase + glassEfficiencyPerTier * (glassTier - BASE_GLASS_TIER);
            return Math.max(MIN_EFFICIENCY, Math.min(MAX_EFFICIENCY, e));
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("fillVolumePerSlotLiters", fillVolumePerSlotLiters);
            map.put("baseMinorLossK", baseMinorLossK);
            map.put("itemMinorLossK", itemMinorLossK);
            map.put("ingotCooledTempC", ingotCooledTempC);
            map.put("recoverableEnergyFraction", recoverableEnergyFraction);
            List<Map<String, Object>> overrides = new ArrayList<>();
            for (ItemOverride o : itemOverrides) if (o != null) overrides.add(o.toMap());
            map.put("itemOverrides", overrides);
            map.put("glassEfficiencyBase", glassEfficiencyBase);
            map.put("glassEfficiencyPerTier", glassEfficiencyPerTier);
            return map;
        }

        public static CoolantBathData fromMap(Map<String, Object> map) {
            CoolantBathData d = new CoolantBathData();
            if (map == null) return d;
            if (map.containsKey("fillVolumePerSlotLiters"))
                d.fillVolumePerSlotLiters = ((Number) map.get("fillVolumePerSlotLiters")).longValue();
            if (map.containsKey("baseMinorLossK"))
                d.baseMinorLossK = ((Number) map.get("baseMinorLossK")).doubleValue();
            if (map.containsKey("itemMinorLossK"))
                d.itemMinorLossK = ((Number) map.get("itemMinorLossK")).doubleValue();
            if (map.containsKey("ingotCooledTempC"))
                d.ingotCooledTempC = ((Number) map.get("ingotCooledTempC")).doubleValue();
            if (map.containsKey("recoverableEnergyFraction"))
                d.recoverableEnergyFraction = ((Number) map.get("recoverableEnergyFraction")).doubleValue();
            if (map.get("itemOverrides") instanceof List) {
                for (Object o : (List<?>) map.get("itemOverrides")) {
                    if (o instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> m = (Map<String, Object>) o;
                        d.itemOverrides.add(ItemOverride.fromMap(m));
                    }
                }
            }
            if (map.containsKey("glassEfficiencyBase"))
                d.glassEfficiencyBase = ((Number) map.get("glassEfficiencyBase")).doubleValue();
            if (map.containsKey("glassEfficiencyPerTier"))
                d.glassEfficiencyPerTier = ((Number) map.get("glassEfficiencyPerTier")).doubleValue();
            return d;
        }
    }

    public static class HeaterData {

        public double baseExchangeArea = 4.0; // Heat exchange surface area in m^2 per ring layer

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("baseExchangeArea", baseExchangeArea);
            return map;
        }

        public static HeaterData fromMap(Map<String, Object> map) {
            HeaterData d = new HeaterData();
            if (map == null) return d;
            if (map.containsKey("baseExchangeArea"))
                d.baseExchangeArea = ((Number) map.get("baseExchangeArea")).doubleValue();
            return d;
        }
    }

    private PHEData phe = new PHEData();
    private DegasserData degasser = new DegasserData();
    private ReservoirData reservoir = new ReservoirData();
    private CoolantBathData bath = new CoolantBathData();
    private HeaterData heater = new HeaterData();

    public PHEData getPhe() {
        return phe;
    }

    public void setPhe(PHEData phe) {
        this.phe = phe;
    }

    public DegasserData getDegasser() {
        return degasser;
    }

    public void setDegasser(DegasserData degasser) {
        this.degasser = degasser;
    }

    public ReservoirData getReservoir() {
        return reservoir;
    }

    public void setReservoir(ReservoirData reservoir) {
        this.reservoir = reservoir;
    }

    public CoolantBathData getBath() {
        return bath;
    }

    public void setBath(CoolantBathData bath) {
        this.bath = bath;
    }

    public HeaterData getHeater() {
        return heater;
    }

    public void setHeater(HeaterData heater) {
        this.heater = heater;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("phe", phe.toMap());
        map.put("degasser", degasser.toMap());
        map.put("bath", bath.toMap());
        map.put("reservoir", reservoir.toMap());
        map.put("heater", heater.toMap());
        return map;
    }

    @SuppressWarnings("unchecked")
    public static CoolantDeviceData fromMap(Map<String, Object> map) {
        CoolantDeviceData data = new CoolantDeviceData();
        if (map == null) return data;
        if (map.containsKey("phe") && map.get("phe") instanceof Map) {
            data.phe = PHEData.fromMap((Map<String, Object>) map.get("phe"));
        }
        if (map.containsKey("degasser") && map.get("degasser") instanceof Map) {
            data.degasser = DegasserData.fromMap((Map<String, Object>) map.get("degasser"));
        }
        if (map.containsKey("bath") && map.get("bath") instanceof Map) {
            data.bath = CoolantBathData.fromMap((Map<String, Object>) map.get("bath"));
        }
        if (map.containsKey("reservoir") && map.get("reservoir") instanceof Map) {
            data.reservoir = ReservoirData.fromMap((Map<String, Object>) map.get("reservoir"));
        }
        if (map.containsKey("heater") && map.get("heater") instanceof Map) {
            data.heater = HeaterData.fromMap((Map<String, Object>) map.get("heater"));
        }
        return data;
    }
}
