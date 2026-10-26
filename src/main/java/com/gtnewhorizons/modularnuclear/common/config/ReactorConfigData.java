package com.gtnewhorizons.modularnuclear.common.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ControlRodData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.CoolantFluidData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.CoolantItemData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.HeatExchangerData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.HeatVentData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.InsulatorData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ModeratorData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.RadiovoltaicData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ReflectorData;
import com.gtnewhorizons.modularnuclear.common.config.data.FuelData;
import com.gtnewhorizons.modularnuclear.common.config.data.PhysicsData;
import com.gtnewhorizons.modularnuclear.common.config.data.TierData;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.WasmStrings;

/**
 * Reactor configuration state (physics, tiers, fuels and components) without any Minecraft or YAML dependency, shared
 * by the game and the standalone/WebAssembly simulator.
 *
 * <p>
 * Defaults come from {@link ReactorDefaultsJson}, which the build generates from
 * {@code assets/modularnuclear/default_config/*.yaml}, so both sides load identical values. The game's
 * {@link ReactorDataRegistry} adds YAML file overrides and item/fluid matching on top.
 */
public final class ReactorConfigData {

    static PhysicsData physicsData = new PhysicsData();
    static final Map<Integer, TierData> tierDataMap = new LinkedHashMap<>();
    static final List<FuelData> fuels = new ArrayList<>();
    static ComponentData componentData = new ComponentData();
    private static volatile boolean defaultsLoaded = false;
    private static int generation = 0;

    private ReactorConfigData() {}

    /** Loads the built-in defaults and pushes the physics constants into the engine. */
    public static synchronized void loadDefaults() {
        loadBuiltInDefaults();
        syncPhysicsToEngine();
    }

    private static void loadBuiltInDefaults() {
        loadFrom(
            MiniJson.parse(ReactorDefaultsJson.physics()),
            MiniJson.parse(ReactorDefaultsJson.tiers()),
            MiniJson.parse(ReactorDefaultsJson.fuels()),
            MiniJson.parse(ReactorDefaultsJson.components()));
        defaultsLoaded = true;
    }

    /**
     * Loads the built-in defaults if nothing has been loaded yet, without touching the engine's physics constants
     * (which a caller may already have set).
     */
    public static void ensureLoaded() {
        if (!defaultsLoaded) loadIfNeeded();
    }

    private static synchronized void loadIfNeeded() {
        if (!defaultsLoaded) loadBuiltInDefaults();
    }

    /** Incremented whenever the configuration changes, so callers can invalidate derived caches. */
    public static int generation() {
        return generation;
    }

    /**
     * Replaces the whole configuration with parsed documents shaped like physics.yaml, tiers.yaml, fuels.yaml and
     * components.yaml. Does not sync the engine; call {@link #syncPhysicsToEngine()} for that.
     */
    @SuppressWarnings("unchecked")
    public static synchronized void loadFrom(Object physicsDoc, Object tiersDoc, Object fuelsDoc,
        Object componentsDoc) {
        physicsData = new PhysicsData();
        if (physicsDoc instanceof Map<?, ?>m) {
            Object phys = ((Map<String, Object>) m).getOrDefault("physics", m);
            if (phys instanceof Map<?, ?>pm) physicsData = PhysicsData.fromMap((Map<String, Object>) pm);
        }

        tierDataMap.clear();
        for (Object o : listOf(tiersDoc, "tiers")) {
            if (o instanceof Map<?, ?>tm) {
                TierData td = TierData.fromMap((Map<String, Object>) tm);
                tierDataMap.put(td.tier, td);
            }
        }

        fuels.clear();
        for (Object o : listOf(fuelsDoc, "fuels")) {
            if (o instanceof Map<?, ?>fm) fuels.add(FuelData.fromMap((Map<String, Object>) fm));
        }

        componentData = (componentsDoc instanceof Map<?, ?>cm) ? ComponentData.fromMap((Map<String, Object>) cm)
            : new ComponentData();
        defaultsLoaded = true;
        generation++;
    }

    private static List<?> listOf(Object doc, String key) {
        if (doc instanceof Map<?, ?>m && m.get(key) instanceof List<?>l) return l;
        if (doc instanceof List<?>l) return l;
        return Collections.emptyList();
    }

    /** Merges a physics.yaml-shaped override document into the current physics and syncs the engine. */
    @SuppressWarnings("unchecked")
    public static synchronized void applyPhysicsOverrides(Object doc) {
        if (!(doc instanceof Map<?, ?>m)) return;
        Object phys = ((Map<String, Object>) m).getOrDefault("physics", m);
        if (phys instanceof Map<?, ?>pm) {
            Map<String, Object> merged = physicsData.toMap();
            deepMerge(merged, (Map<String, Object>) pm);
            physicsData = PhysicsData.fromMap(merged);
        }
        generation++;
        syncPhysicsToEngine();
    }

    /** Pushes the loaded physics constants into the simulation engine's static parameters. */
    public static synchronized void syncPhysicsToEngine() {
        if (physicsData == null) return;
        NuclearSimulationEngine.setHuPerDegree(physicsData.huPerDegree);
        NuclearSimulationEngine.setGlobalThermalFissionMultiplier(physicsData.globalThermalFissionMultiplier);
        NuclearSimulationEngine.setGlobalFastFissionMultiplier(physicsData.globalFastFissionMultiplier);
        NuclearSimulationEngine.setFastAbsorptionDamageMultiplier(physicsData.fastAbsorptionDamageMultiplier);
        NuclearSimulationEngine.setThermalAbsorptionDamageMultiplier(physicsData.thermalAbsorptionDamageMultiplier);
        NuclearSimulationEngine.fissionHeatPerNeutron = physicsData.fissionHeatPerNeutron;
        NuclearSimulationEngine.wallReflectionChance = physicsData.wallReflectionChance;
        NuclearSimulationEngine.setBeamEnergyToFastNeutronMultiplier(physicsData.beamEnergyToFastNeutronMultiplier);
        NuclearSimulationEngine.setGridConductionMultiplier(physicsData.gridConductionMultiplier);
        NuclearSimulationEngine.setFuelBurnupMultiplier(physicsData.fuelBurnupMultiplier);
        NuclearSimulationEngine.coolantPassageHeatTransferMultiplier = Math
            .max(0.0, physicsData.coolantPassageHeatTransferMultiplier);
        NuclearSimulationEngine.denseNbaseMultiplier = physicsData.denseNbaseMultiplier;
        NuclearSimulationEngine.denseFissionMultiplier = physicsData.denseFissionMultiplier;
        NuclearSimulationEngine.excitedNbaseMultiplier = physicsData.excitedNbaseMultiplier;
        NuclearSimulationEngine.excitedFissionMultiplier = physicsData.excitedFissionMultiplier;
        NuclearSimulationEngine.standardNbaseMultiplier = physicsData.standardNbaseMultiplier;
        NuclearSimulationEngine.standardFissionMultiplier = physicsData.standardFissionMultiplier;
        NuclearSimulationEngine.breederNbaseMultiplier = physicsData.breederNbaseMultiplier;
        NuclearSimulationEngine.breederFissionMultiplier = physicsData.breederFissionMultiplier;
    }

    public static PhysicsData getPhysics() {
        ensureLoaded();
        return physicsData;
    }

    public static TierData getTier(int tier) {
        ensureLoaded();
        TierData td = tierDataMap.get(tier);
        if (td != null) return td;
        return tierDataMap
            .getOrDefault(0, new TierData(tier, "Tier " + tier, "Casing", 2048L, 1200.0, 120.0, tier >= 2));
    }

    public static boolean hasTier(int tier) {
        ensureLoaded();
        return tierDataMap.containsKey(tier);
    }

    public static List<TierData> getAllTiers() {
        ensureLoaded();
        return new ArrayList<>(tierDataMap.values());
    }

    public static List<FuelData> getAllFuels() {
        ensureLoaded();
        return Collections.unmodifiableList(fuels);
    }

    public static ComponentData getComponentData() {
        ensureLoaded();
        return componentData;
    }

    /** Finds a fuel by its config id (e.g. {@code fuel_uranium_4}). */
    public static FuelData findFuelById(String id) {
        ensureLoaded();
        if (id == null) return null;
        for (FuelData fd : fuels) {
            if (id.equals(fd.id)) return fd;
        }
        return null;
    }

    public static <T> T findById(List<T> list, Function<T, String> idGetter, String id) {
        if (list == null || id == null) return null;
        for (T item : list) {
            if (id.equals(idGetter.apply(item))) return item;
        }
        return null;
    }

    private static boolean isRemoved(Map<String, Object> map) {
        if (map == null) return true;
        Object rem = map.get("remove");
        if (Boolean.TRUE.equals(rem) || "true".equalsIgnoreCase(String.valueOf(rem))) return true;
        Object dis = map.get("disabled");
        if (Boolean.TRUE.equals(dis) || "true".equalsIgnoreCase(String.valueOf(dis))) return true;
        Object en = map.get("enabled");
        if (Boolean.FALSE.equals(en) || "false".equalsIgnoreCase(String.valueOf(en))) return true;
        return false;
    }

    private static void deepMerge(Map<String, Object> target, Map<String, Object> source) {
        if (target == null || source == null) return;
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            if (val instanceof Map<?, ?>sourceChild && target.get(key) instanceof Map<?, ?>targetChild) {
                @SuppressWarnings("unchecked")
                Map<String, Object> tc = (Map<String, Object>) targetChild;
                @SuppressWarnings("unchecked")
                Map<String, Object> sc = (Map<String, Object>) sourceChild;
                deepMerge(tc, sc);
            } else if (val != null) {
                target.put(key, val);
            }
        }
    }

    private static Map<String, Map<String, Object>> normalizeEntries(Object raw) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        if (raw instanceof List<?>list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?>m) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = new LinkedHashMap<>((Map<String, Object>) m);
                    String id = (String) map.get("id");
                    if (id != null) {
                        result.put(id, map);
                    }
                }
            }
        } else if (raw instanceof Map<?, ?>m) {
            for (Map.Entry<?, ?> entry : m.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object val = entry.getValue();
                if (val == null) {
                    Map<String, Object> removeEntry = new LinkedHashMap<>();
                    removeEntry.put("id", key);
                    removeEntry.put("remove", true);
                    result.put(key, removeEntry);
                } else if (val instanceof Map<?, ?>childMap) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = new LinkedHashMap<>((Map<String, Object>) childMap);
                    if (!map.containsKey("id")) {
                        map.put("id", key);
                    }
                    result.put(key, map);
                }
            }
        }
        return result;
    }

    public static void applyTierOverrides(Object obj) {
        generation++;
        if (obj == null) return;
        Object rawTiers = obj;
        if (obj instanceof Map<?, ?>m && m.containsKey("tiers")) {
            rawTiers = m.get("tiers");
        }
        if (rawTiers instanceof List<?>list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?>tm) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) tm;
                    int tier = PhysicsData.getInt(map, "tier", -1);
                    if (tier >= 0) {
                        if (isRemoved(map)) {
                            tierDataMap.remove(tier);
                        } else if (tierDataMap.containsKey(tier)) {
                            Map<String, Object> merged = tierDataMap.get(tier)
                                .toMap();
                            deepMerge(merged, map);
                            tierDataMap.put(tier, TierData.fromMap(merged));
                        } else {
                            tierDataMap.put(tier, TierData.fromMap(map));
                        }
                    }
                }
            }
        } else if (rawTiers instanceof Map<?, ?>tm) {
            for (Map.Entry<?, ?> entry : tm.entrySet()) {
                int tier;
                try {
                    tier = Integer.parseInt(String.valueOf(entry.getKey()));
                } catch (NumberFormatException e) {
                    continue;
                }
                Object val = entry.getValue();
                if (val == null) {
                    tierDataMap.remove(tier);
                } else if (val instanceof Map<?, ?>map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> userMap = (Map<String, Object>) map;
                    if (isRemoved(userMap)) {
                        tierDataMap.remove(tier);
                    } else if (tierDataMap.containsKey(tier)) {
                        Map<String, Object> merged = tierDataMap.get(tier)
                            .toMap();
                        deepMerge(merged, userMap);
                        tierDataMap.put(tier, TierData.fromMap(merged));
                    } else {
                        userMap.put("tier", tier);
                        tierDataMap.put(tier, TierData.fromMap(userMap));
                    }
                }
            }
        }
    }

    public static void applyFuelOverrides(Object obj) {
        generation++;
        if (obj == null) return;
        Object rawFuels = obj;
        if (obj instanceof Map<?, ?>m && m.containsKey("fuels")) {
            rawFuels = m.get("fuels");
        }
        Map<String, Map<String, Object>> userEntries = normalizeEntries(rawFuels);
        if (userEntries.isEmpty()) return;

        Map<String, FuelData> fuelMap = new LinkedHashMap<>();
        for (FuelData fd : fuels) {
            if (fd.id != null) {
                fuelMap.put(fd.id, fd);
            }
        }

        for (Map.Entry<String, Map<String, Object>> entry : userEntries.entrySet()) {
            String id = entry.getKey();
            Map<String, Object> userMap = entry.getValue();
            if (isRemoved(userMap)) {
                fuelMap.remove(id);
            } else if (fuelMap.containsKey(id)) {
                Map<String, Object> merged = fuelMap.get(id)
                    .toMap();
                deepMerge(merged, userMap);
                fuelMap.put(id, FuelData.fromMap(merged));
            } else {
                fuelMap.put(id, FuelData.fromMap(userMap));
            }
        }

        fuels.clear();
        fuels.addAll(fuelMap.values());
    }

    public static void applyComponentOverrides(Object obj) {
        generation++;
        if (!(obj instanceof Map<?, ?>m)) return;
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) m;

        mergeComponentCategory(
            componentData.reflectors,
            map.get("reflectors"),
            d -> d.id,
            ReflectorData::toMap,
            ReflectorData::fromMap);
        mergeComponentCategory(
            componentData.moderators,
            map.get("moderators"),
            d -> d.id,
            ModeratorData::toMap,
            ModeratorData::fromMap);
        mergeComponentCategory(
            componentData.controlRods,
            map.get("control_rods"),
            d -> d.id,
            ControlRodData::toMap,
            ControlRodData::fromMap);
        mergeComponentCategory(
            componentData.heatVents,
            map.get("heat_vents"),
            d -> d.id,
            HeatVentData::toMap,
            HeatVentData::fromMap);
        mergeComponentCategory(
            componentData.heatExchangers,
            map.get("heat_exchangers"),
            d -> d.id,
            HeatExchangerData::toMap,
            HeatExchangerData::fromMap);
        mergeComponentCategory(
            componentData.insulators,
            map.get("insulators"),
            d -> d.id,
            InsulatorData::toMap,
            InsulatorData::fromMap);
        mergeComponentCategory(
            componentData.radiovoltaics,
            map.get("radiovoltaics"),
            d -> d.id,
            RadiovoltaicData::toMap,
            RadiovoltaicData::fromMap);
        mergeComponentCategory(
            componentData.coolantFluids,
            map.get("coolant_fluids"),
            d -> d.id,
            CoolantFluidData::toMap,
            CoolantFluidData::fromMap);
        mergeComponentCategory(
            componentData.coolantItems,
            map.get("coolant_items"),
            d -> d.id,
            CoolantItemData::toMap,
            CoolantItemData::fromMap);
    }

    private static <T> void mergeComponentCategory(List<T> targetList, Object rawUserCategory,
        Function<T, String> idGetter, Function<T, Map<String, Object>> toMap,
        Function<Map<String, Object>, T> fromMap) {
        if (rawUserCategory == null) return;
        Map<String, Map<String, Object>> userEntries = normalizeEntries(rawUserCategory);
        if (userEntries.isEmpty()) return;

        Map<String, T> activeMap = new LinkedHashMap<>();
        for (T item : targetList) {
            String id = idGetter.apply(item);
            if (id != null) {
                activeMap.put(id, item);
            }
        }

        for (Map.Entry<String, Map<String, Object>> entry : userEntries.entrySet()) {
            String id = entry.getKey();
            Map<String, Object> userMap = entry.getValue();
            if (isRemoved(userMap)) {
                activeMap.remove(id);
            } else if (activeMap.containsKey(id)) {
                Map<String, Object> merged = toMap.apply(activeMap.get(id));
                deepMerge(merged, userMap);
                activeMap.put(id, fromMap.apply(merged));
            } else {
                activeMap.put(id, fromMap.apply(userMap));
            }
        }

        targetList.clear();
        targetList.addAll(activeMap.values());
    }

    // ---- Fluid name matching (shared with the game's item/fluid lookups) ----

    public static boolean matchesPattern(String text, String pattern) {
        if (text == null || pattern == null) return false;
        String t = text.trim()
            .toLowerCase();
        String p = pattern.trim()
            .toLowerCase();
        if (p.equals("*") || p.equals(t)) return true;
        if (p.startsWith("*") && p.endsWith("*")) {
            return t.contains(p.substring(1, p.length() - 1));
        }
        if (p.startsWith("*")) {
            return t.endsWith(p.substring(1));
        }
        if (p.endsWith("*")) {
            return t.startsWith(p.substring(0, p.length() - 1));
        }
        return p.equals(t);
    }

    public static String normalizeFluidString(String s) {
        if (s == null) return "";
        String clean = s.toLowerCase();
        if (clean.startsWith("fluid.")) clean = clean.substring(6);
        if (clean.startsWith("molten.")) clean = clean.substring(7);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    static boolean matchesPermutedDescriptors(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        String stripA = WasmStrings.replace(a, "state", "");
        String stripB = WasmStrings.replace(b, "state", "");
        if (stripA.equals(stripB)) return true;
        for (String modifier : new String[] { "excited", "depleted" }) {
            if (stripA.contains(modifier) && stripB.contains(modifier)) {
                String baseA = WasmStrings.replace(stripA, modifier, "");
                String baseB = WasmStrings.replace(stripB, modifier, "");
                if (!baseA.isEmpty() && baseA.equals(baseB)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean matchesFluid(String fluidName, List<String> patterns) {
        if (fluidName == null || patterns == null || patterns.isEmpty()) return false;
        String cleanFluid = fluidName.startsWith("fluid.") ? fluidName.substring(6) : fluidName;
        String normFluid = normalizeFluidString(fluidName);
        for (String pattern : patterns) {
            if (pattern == null || pattern.isEmpty()) continue;
            if (fluidName.equalsIgnoreCase(pattern)) return true;
            String cleanPattern = pattern.startsWith("fluid.") ? pattern.substring(6) : pattern;
            if (cleanFluid.equalsIgnoreCase(cleanPattern)) return true;
            if (pattern.contains("*") && matchesPattern(fluidName, pattern)) return true;
            String normPattern = normalizeFluidString(pattern);
            if (!normPattern.isEmpty()) {
                if (matchesPermutedDescriptors(normFluid, normPattern)) return true;
            }
        }
        return false;
    }

    /** The coolant fluid entry (components.yaml coolant_fluids) matching a fluid name, or null. */
    public static ComponentData.CoolantFluidData findCoolantFluid(String fluidName) {
        if (fluidName == null) return null;
        for (ComponentData.CoolantFluidData d : getComponentData().coolantFluids) {
            if (matchesFluid(fluidName, d.matching)) return d;
        }
        return null;
    }
}
