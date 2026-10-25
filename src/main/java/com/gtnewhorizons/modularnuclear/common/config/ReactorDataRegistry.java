package com.gtnewhorizons.modularnuclear.common.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import com.gtnewhorizons.modularnuclear.ModularNuclear;
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
import com.gtnewhorizons.modularnuclear.common.nuclear.CustomFuelMix;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.ItemList;
import gregtech.api.util.GTUtility;
import ic2.core.Ic2Items;

/**
 * Data-driven registry managing YAML-configured reactor physics, multiblock tiers,
 * fuels, and non-fuel internal reactor components.
 */
public final class ReactorDataRegistry {

    private static File configDirectory;

    private static PhysicsData physicsData = new PhysicsData();
    private static final Map<Integer, TierData> tierDataMap = new LinkedHashMap<>();
    private static final List<FuelData> fuels = new ArrayList<>();
    private static ComponentData componentData = new ComponentData();

    // Fast item-level lookup caches for WAILA and client tick responsiveness
    private static final Map<Long, FuelData> fuelCache = new ConcurrentHashMap<>();
    private static final Map<Long, ModeratorData> moderatorCache = new ConcurrentHashMap<>();
    private static final Map<Long, ReflectorData> reflectorCache = new ConcurrentHashMap<>();
    private static final Map<Long, ControlRodData> controlRodCache = new ConcurrentHashMap<>();
    private static final Map<Long, InsulatorData> insulatorCache = new ConcurrentHashMap<>();
    private static final Map<Long, HeatVentData> heatVentCache = new ConcurrentHashMap<>();
    private static final Map<Long, HeatExchangerData> heatExchangerCache = new ConcurrentHashMap<>();
    private static final Map<Long, RadiovoltaicData> radiovoltaicCache = new ConcurrentHashMap<>();
    private static final Map<Long, CoolantItemData> coolantItemCache = new ConcurrentHashMap<>();
    private static final Map<Long, CoolantFluidData> coolantFluidItemCache = new ConcurrentHashMap<>();
    private static final Map<Long, Boolean> spentProductCache = new ConcurrentHashMap<>();

    private static final FuelData NO_FUEL = new FuelData();
    private static final ModeratorData NO_MODERATOR = new ModeratorData();
    private static final ReflectorData NO_REFLECTOR = new ReflectorData();
    private static final ControlRodData NO_CONTROL_ROD = new ControlRodData();
    private static final InsulatorData NO_INSULATOR = new InsulatorData();
    private static final HeatVentData NO_HEAT_VENT = new HeatVentData();
    private static final HeatExchangerData NO_HEAT_EXCHANGER = new HeatExchangerData();
    private static final RadiovoltaicData NO_RADIOVOLTAIC = new RadiovoltaicData();
    private static final CoolantItemData NO_COOLANT_ITEM = new CoolantItemData();
    private static final CoolantFluidData NO_COOLANT_FLUID = new CoolantFluidData();

    public static long getItemKey(ItemStack stack) {
        if (stack == null) return 0L;
        Item item = null;
        try {
            item = stack.getItem();
        } catch (Throwable ignored) {}
        if (item == null) return 0L;
        int id = 0;
        try {
            id = Item.getIdFromItem(item);
        } catch (Throwable ignored) {}
        if (id <= 0) return 0L;
        int damage = 0;
        try {
            damage = stack.isItemStackDamageable() ? 0 : stack.getItemDamage();
        } catch (Throwable ignored) {}
        if (damage == OreDictionary.WILDCARD_VALUE) {
            damage = 0;
        }
        return (((long) id) << 32) | (damage & 0xFFFFFFFFL);
    }

    public static synchronized void clearCaches() {
        fuelCache.clear();
        moderatorCache.clear();
        reflectorCache.clear();
        controlRodCache.clear();
        insulatorCache.clear();
        heatVentCache.clear();
        heatExchangerCache.clear();
        radiovoltaicCache.clear();
        coolantItemCache.clear();
        coolantFluidItemCache.clear();
        spentProductCache.clear();
        try {
            Class.forName("net.minecraft.item.Item");
            Class<?> clazz = Class.forName("com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification");
            clazz.getMethod("clearCaches")
                .invoke(null);
        } catch (Throwable ignored) {
            // Standalone CLI or WebAssembly environment without Minecraft classes
        }
    }

    public static synchronized void preIndexKnownItems() {
        if (fuels != null) {
            for (FuelData fd : fuels) {
                if (fd.isLiquid || fd.matching == null) continue;
                for (String pattern : fd.matching) {
                    indexPattern(pattern, (key) -> fuelCache.put(key, fd));
                }
                if (fd.spentProduct != null && !fd.spentProduct.isEmpty()) {
                    indexPattern(fd.spentProduct, (key) -> spentProductCache.put(key, Boolean.TRUE));
                }
            }
        }
        if (componentData != null) {
            if (componentData.reflectors != null) {
                for (ReflectorData d : componentData.reflectors) {
                    if (d.matching == null) continue;
                    for (String p : d.matching) {
                        indexPattern(p, (key) -> reflectorCache.put(key, d));
                    }
                }
            }
            if (componentData.moderators != null) {
                for (ModeratorData d : componentData.moderators) {
                    if (d.matching == null) continue;
                    for (String p : d.matching) {
                        indexPattern(p, (key) -> moderatorCache.put(key, d));
                    }
                }
            }
            if (componentData.controlRods != null) {
                for (ControlRodData d : componentData.controlRods) {
                    if (d.matching == null) continue;
                    for (String p : d.matching) {
                        indexPattern(p, (key) -> controlRodCache.put(key, d));
                    }
                }
            }
            if (componentData.heatVents != null) {
                for (HeatVentData d : componentData.heatVents) {
                    if (d.matching == null) continue;
                    for (String p : d.matching) {
                        indexPattern(p, (key) -> heatVentCache.put(key, d));
                    }
                }
            }
            if (componentData.heatExchangers != null) {
                for (HeatExchangerData d : componentData.heatExchangers) {
                    if (d.matching == null) continue;
                    for (String p : d.matching) {
                        indexPattern(p, (key) -> heatExchangerCache.put(key, d));
                    }
                }
            }
            if (componentData.insulators != null) {
                for (InsulatorData d : componentData.insulators) {
                    if (d.matching == null) continue;
                    for (String p : d.matching) {
                        indexPattern(p, (key) -> insulatorCache.put(key, d));
                    }
                }
            }
            if (componentData.radiovoltaics != null) {
                for (RadiovoltaicData d : componentData.radiovoltaics) {
                    if (d.matching == null) continue;
                    for (String p : d.matching) {
                        indexPattern(p, (key) -> radiovoltaicCache.put(key, d));
                    }
                }
            }
            if (componentData.coolantItems != null) {
                for (CoolantItemData d : componentData.coolantItems) {
                    if (d.matching == null) continue;
                    for (String p : d.matching) {
                        indexPattern(p, (key) -> coolantItemCache.put(key, d));
                    }
                }
            }
        }
    }

    private static void indexPattern(String pattern, java.util.function.LongConsumer consumer) {
        if (pattern == null || pattern.isEmpty() || pattern.contains("*")) return;
        try {
            String oreTarget = pattern.startsWith("ore:") ? pattern.substring(4)
                : (pattern.startsWith("plate") || pattern.startsWith("dust") || pattern.startsWith("ingot")) ? pattern
                    : null;
            if (oreTarget != null) {
                List<ItemStack> ores = OreDictionary.getOres(oreTarget);
                if (ores != null) {
                    for (ItemStack ore : ores) {
                        long key = getItemKey(ore);
                        if (key != 0L) consumer.accept(key);
                    }
                }
            }
            ItemStack is = resolveItemStack(pattern);
            if (is != null) {
                long key = getItemKey(is);
                if (key != 0L) consumer.accept(key);
            }
        } catch (Throwable ignored) {}
    }

    static {
        NuclearSimulationEngine
            .setConfigProvider(new com.gtnewhorizons.modularnuclear.common.config.data.INuclearConfigProvider() {

                @Override
                public com.gtnewhorizons.modularnuclear.common.config.data.TierData getTier(int tier) {
                    return ReactorDataRegistry.getTier(tier);
                }

                @Override
                public com.gtnewhorizons.modularnuclear.common.config.data.FuelData findFuel(String name) {
                    return ReactorDataRegistry.findFuel(name);
                }

                @Override
                public com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.CoolantFluidData findCoolantFluid(
                    String name) {
                    return ReactorDataRegistry.findCoolantFluid(name);
                }

                @Override
                public boolean matchesFluid(String fluidName, List<String> allowed) {
                    return ReactorDataRegistry.matchesFluid(fluidName, allowed);
                }
            });
        loadDefaults();
    }

    private ReactorDataRegistry() {}

    private static InputStream getResource(String name) {
        String path = "/assets/modularnuclear/default_config/" + name;
        InputStream in = ReactorDataRegistry.class.getResourceAsStream(path);
        if (in == null) {
            in = ReactorDataRegistry.class.getClassLoader()
                .getResourceAsStream("assets/modularnuclear/default_config/" + name);
        }
        if (in == null && Thread.currentThread()
            .getContextClassLoader() != null) {
            in = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("assets/modularnuclear/default_config/" + name);
        }
        return in;
    }

    public static synchronized void init(File modConfigDir) {
        configDirectory = new File(modConfigDir, "modularnuclear");
        if (!configDirectory.exists()) {
            configDirectory.mkdirs();
            writeReadme(new File(configDirectory, "README.txt"));
        }
        loadFromDir(configDirectory);
    }

    private static void writeReadme(File readmeFile) {
        if (readmeFile.exists()) return;
        try (FileWriter w = new FileWriter(readmeFile)) {
            w.write("Modular Nuclear Reactor - Configuration Directory\n");
            w.write("==================================================\n\n");
            w.write("All default configurations for physics, tiers, fuels, and components are\n");
            w.write("loaded directly from the mod JAR.\n\n");
            w.write("To customize or override settings, create any of the following YAML files:\n");
            w.write("- physics.yaml    : override specific global physics constants (e.g. hu_per_degree)\n");
            w.write("- tiers.yaml      : override or define reactor pressure tube tiers\n");
            w.write("- fuels.yaml      : override fuel parameters, define new fuels, or disable defaults\n");
            w.write("- components.yaml : override moderators, reflectors, control rods, vents, exchangers,\n");
            w.write("                    insulators, radiovoltaics, and coolant fluids/items\n\n");
            w.write("Overriding rules:\n");
            w.write("1. Partial override: specify only the fields you wish to change. All omitted fields\n");
            w.write("   will continue to use default values from the JAR.\n");
            w.write("2. Disabling items : to remove a default fuel or component, specify its id with\n");
            w.write("   'remove: true' (or 'enabled: false', or map its key to null in YAML).\n");
            w.write("3. Adding items    : to add a new fuel or component, specify a unique id and required fields.\n");
        } catch (Exception e) {
            ModularNuclear.LOG.error("Failed to write README.txt to configuration directory", e);
        }
    }

    public static synchronized void loadDefaults() {
        clearCaches();
        Yaml yaml = createYaml();

        // 1. Physics defaults
        try (InputStream in = getResource("physics.yaml")) {
            if (in != null) {
                Object obj = yaml.load(in);
                if (obj instanceof Map<?, ?>m) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) m;
                    Object phys = map.getOrDefault("physics", map);
                    if (phys instanceof Map<?, ?>pMap) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> pm = (Map<String, Object>) pMap;
                        physicsData = PhysicsData.fromMap(pm);
                    }
                }
            } else {
                ModularNuclear.LOG.error("Default physics.yaml resource not found in JAR!");
                physicsData = new PhysicsData();
            }
        } catch (Exception e) {
            ModularNuclear.LOG.error("Failed to load default physics.yaml from JAR", e);
            physicsData = new PhysicsData();
        }
        syncPhysicsToEngine();

        // 2. Tiers defaults
        tierDataMap.clear();
        try (InputStream in = getResource("tiers.yaml")) {
            if (in != null) {
                Object obj = yaml.load(in);
                List<?> list = null;
                if (obj instanceof Map<?, ?>m && m.containsKey("tiers")) {
                    Object tList = m.get("tiers");
                    if (tList instanceof List<?>l) list = l;
                } else if (obj instanceof List<?>l) {
                    list = l;
                }
                if (list != null) {
                    for (Object o : list) {
                        if (o instanceof Map<?, ?>tm) {
                            @SuppressWarnings("unchecked")
                            TierData td = TierData.fromMap((Map<String, Object>) tm);
                            tierDataMap.put(td.tier, td);
                        }
                    }
                }
            } else {
                ModularNuclear.LOG.error("Default tiers.yaml resource not found in JAR!");
            }
        } catch (Exception e) {
            ModularNuclear.LOG.error("Failed to load default tiers.yaml from JAR", e);
        }

        // 3. Fuels defaults
        fuels.clear();
        try (InputStream in = getResource("fuels.yaml")) {
            if (in != null) {
                Object obj = yaml.load(in);
                List<?> list = null;
                if (obj instanceof Map<?, ?>m && m.containsKey("fuels")) {
                    Object fObj = m.get("fuels");
                    if (fObj instanceof List<?>l) list = l;
                } else if (obj instanceof List<?>l) {
                    list = l;
                }
                if (list != null) {
                    for (Object o : list) {
                        if (o instanceof Map<?, ?>fm) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> map = (Map<String, Object>) fm;
                            fuels.add(FuelData.fromMap(map));
                        }
                    }
                }
            } else {
                ModularNuclear.LOG.error("Default fuels.yaml resource not found in JAR!");
            }
        } catch (Exception e) {
            ModularNuclear.LOG.error("Failed to load default fuels.yaml from JAR", e);
        }

        // 4. Components defaults
        try (InputStream in = getResource("components.yaml")) {
            if (in != null) {
                Object obj = yaml.load(in);
                if (obj instanceof Map<?, ?>m) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) m;
                    componentData = ComponentData.fromMap(map);
                }
            } else {
                ModularNuclear.LOG.error("Default components.yaml resource not found in JAR!");
                componentData = new ComponentData();
            }
        } catch (Exception e) {
            ModularNuclear.LOG.error("Failed to load default components.yaml from JAR", e);
            componentData = new ComponentData();
        }
    }

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
        NuclearSimulationEngine.denseNbaseMultiplier = physicsData.denseNbaseMultiplier;
        NuclearSimulationEngine.denseFissionMultiplier = physicsData.denseFissionMultiplier;
        NuclearSimulationEngine.excitedNbaseMultiplier = physicsData.excitedNbaseMultiplier;
        NuclearSimulationEngine.excitedFissionMultiplier = physicsData.excitedFissionMultiplier;
        NuclearSimulationEngine.standardNbaseMultiplier = physicsData.standardNbaseMultiplier;
        NuclearSimulationEngine.standardFissionMultiplier = physicsData.standardFissionMultiplier;
        NuclearSimulationEngine.breederNbaseMultiplier = physicsData.breederNbaseMultiplier;
        NuclearSimulationEngine.breederFissionMultiplier = physicsData.breederFissionMultiplier;
    }

    public static synchronized void loadFromDir(File dir) {
        loadDefaults();
        if (dir == null || !dir.exists()) return;

        Yaml yaml = createYaml();

        // 1. Physics overrides
        File physicsFile = new File(dir, "physics.yaml");
        if (physicsFile.exists()) {
            try (FileReader r = new FileReader(physicsFile)) {
                Object obj = yaml.load(r);
                if (obj instanceof Map<?, ?>m) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) m;
                    Object phys = map.getOrDefault("physics", map);
                    if (phys instanceof Map<?, ?>pMap) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> pm = (Map<String, Object>) pMap;
                        Map<String, Object> merged = physicsData.toMap();
                        deepMerge(merged, pm);
                        physicsData = PhysicsData.fromMap(merged);
                    }
                }
            } catch (Exception e) {
                ModularNuclear.LOG.error("Failed to load physics.yaml overrides", e);
            }
            syncPhysicsToEngine();
        }

        // 2. Tiers overrides
        File tiersFile = new File(dir, "tiers.yaml");
        if (tiersFile.exists()) {
            try (FileReader r = new FileReader(tiersFile)) {
                Object obj = yaml.load(r);
                applyTierOverrides(obj);
            } catch (Exception e) {
                ModularNuclear.LOG.error("Failed to load tiers.yaml overrides", e);
            }
        }

        // 3. Fuels overrides
        File fuelsFile = new File(dir, "fuels.yaml");
        if (fuelsFile.exists()) {
            try (FileReader r = new FileReader(fuelsFile)) {
                Object obj = yaml.load(r);
                applyFuelOverrides(obj);
            } catch (Exception e) {
                ModularNuclear.LOG.error("Failed to load fuels.yaml overrides", e);
            }
        }

        // 4. Components overrides
        File compFile = new File(dir, "components.yaml");
        if (compFile.exists()) {
            try (FileReader r = new FileReader(compFile)) {
                Object obj = yaml.load(r);
                applyComponentOverrides(obj);
            } catch (Exception e) {
                ModularNuclear.LOG.error("Failed to load components.yaml overrides", e);
            }
        }

        clearCaches();
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

    private static void applyTierOverrides(Object obj) {
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

    private static void applyFuelOverrides(Object obj) {
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

    private static void applyComponentOverrides(Object obj) {
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

    public static synchronized void saveToDir(File dir) {
        if (dir == null) return;
        if (!dir.exists()) dir.mkdirs();
        Yaml yaml = createYaml();
        savePhysicsFile(new File(dir, "physics.yaml"), yaml);
        saveTiersFile(new File(dir, "tiers.yaml"), yaml);
        saveFuelsFile(new File(dir, "fuels.yaml"), yaml);
        saveComponentsFile(new File(dir, "components.yaml"), yaml);
    }

    private static Yaml createYaml() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        return new Yaml(options);
    }

    private static void savePhysicsFile(File file, Yaml yaml) {
        try (FileWriter w = new FileWriter(file)) {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("physics", physicsData.toMap());
            yaml.dump(root, w);
        } catch (Exception e) {
            ModularNuclear.LOG.error("Failed to save default physics.yaml", e);
        }
    }

    private static void saveTiersFile(File file, Yaml yaml) {
        try (FileWriter w = new FileWriter(file)) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (TierData td : tierDataMap.values()) {
                list.add(td.toMap());
            }
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("tiers", list);
            yaml.dump(root, w);
        } catch (Exception e) {
            ModularNuclear.LOG.error("Failed to save default tiers.yaml", e);
        }
    }

    private static void saveFuelsFile(File file, Yaml yaml) {
        try (FileWriter w = new FileWriter(file)) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (FuelData fd : fuels) {
                list.add(fd.toMap());
            }
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("fuels", list);
            yaml.dump(root, w);
        } catch (Exception e) {
            ModularNuclear.LOG.error("Failed to save default fuels.yaml", e);
        }
    }

    private static void saveComponentsFile(File file, Yaml yaml) {
        try (FileWriter w = new FileWriter(file)) {
            yaml.dump(componentData.toMap(), w);
        } catch (Exception e) {
            ModularNuclear.LOG.error("Failed to save default components.yaml", e);
        }
    }

    // =========================================================================
    // Getters & Lookups
    // =========================================================================

    public static PhysicsData getPhysics() {
        return physicsData;
    }

    public static TierData getTier(int tier) {
        TierData td = tierDataMap.get(tier);
        if (td != null) return td;
        return tierDataMap
            .getOrDefault(0, new TierData(tier, "Tier " + tier, "Casing", 2048L, 1200.0, 120.0, tier >= 2));
    }

    public static boolean hasTier(int tier) {
        return tierDataMap.containsKey(tier);
    }

    public static List<TierData> getAllTiers() {
        return new ArrayList<>(tierDataMap.values());
    }

    public static List<FuelData> getAllFuels() {
        return Collections.unmodifiableList(fuels);
    }

    public static ComponentData getComponentData() {
        return componentData;
    }

    public static FuelData findFuel(ItemStack stack) {
        if (stack == null) return null;

        if (CustomFuelMix.hasCustomMix(stack)) {
            CustomFuelMix mix = CustomFuelMix.fromStack(stack);
            if (mix != null) {
                int rodSize = 4;
                if (stack
                    .getItem() instanceof com.gtnewhorizons.modularnuclear.common.item.ItemCustomFuelRod customRod) {
                    rodSize = customRod.rodSize;
                }
                FuelData fd = new FuelData();
                fd.id = "custom_fuel_mix_" + rodSize;
                fd.name = (rodSize == 1 ? "Custom Fuel Rod"
                    : rodSize == 2 ? "Dual Custom Fuel Rod" : "Quad Custom Fuel Rod");
                fd.packetCount = rodSize;
                fd.baseNeutrons = mix.getBaseNeutrons(rodSize);
                fd.thermalFissionMultiplier = mix.baseThermalFissionMultiplier;
                fd.fastFissionMultiplier = mix.baseFastFissionMultiplier;
                fd.absorptionThermal = 0.80;
                fd.absorptionFast = 0.25;
                fd.scatteringThermal = 0.10;
                fd.scatteringFast = 0.15;
                fd.moderation = 0.10;
                fd.heatTransferCoeff = 0.05;
                fd.defaultDurability = mix.defaultDurability;
                fd.spentProduct = "modularnuclear:rodCustomDepletedFuel" + rodSize;
                return fd;
            }
        }

        // Uncalibrated / empty-NBT custom fuel rods are not active fuels
        if (stack.getItem() instanceof com.gtnewhorizons.modularnuclear.common.item.ItemCustomFuelRod
            || (com.gtnewhorizons.modularnuclear.common.item.ModItems.rodCustomFuel1 != null
                && stack.getItem() == com.gtnewhorizons.modularnuclear.common.item.ModItems.rodCustomFuel1)
            || (com.gtnewhorizons.modularnuclear.common.item.ModItems.rodCustomFuel2 != null
                && stack.getItem() == com.gtnewhorizons.modularnuclear.common.item.ModItems.rodCustomFuel2)
            || (com.gtnewhorizons.modularnuclear.common.item.ModItems.rodCustomFuel4 != null
                && stack.getItem() == com.gtnewhorizons.modularnuclear.common.item.ModItems.rodCustomFuel4)) {
            return null;
        }

        long key = getItemKey(stack);
        if (key != 0L) {
            FuelData cached = fuelCache.get(key);
            if (cached != null) {
                return cached == NO_FUEL ? null : cached;
            }
        }

        FluidStack fs = null;
        try {
            if (ItemList.Display_Fluid.hasBeenSet()) {
                fs = GTUtility.getFluidFromDisplayStack(stack);
            }
        } catch (Throwable ignored) {}
        if (fs != null) {
            FuelData result = findFuel(fs);
            if (key != 0L) fuelCache.put(key, result != null ? result : NO_FUEL);
            return result;
        }

        for (FuelData fd : fuels) {
            if (!fd.isLiquid && matchesItem(stack, fd.matching)) {
                if (key != 0L) fuelCache.put(key, fd);
                return fd;
            }
        }
        if (key != 0L) fuelCache.put(key, NO_FUEL);
        return null;
    }

    public static FuelData findFuel(FluidStack fluid) {
        if (fluid == null || fluid.getFluid() == null) return null;
        for (FuelData fd : fuels) {
            if (fd.isLiquid && matchesFluid(fluid, fd.matching)) {
                return fd;
            }
        }
        return null;
    }

    public static FuelData findFuel(String fluidName) {
        if (fluidName == null) return null;
        for (FuelData fd : fuels) {
            if (fd.isLiquid && matchesFluid(fluidName, fd.matching)) {
                return fd;
            }
        }
        return null;
    }

    public static ReflectorData findReflector(ItemStack stack) {
        if (stack == null) return null;
        long key = getItemKey(stack);
        if (key != 0L) {
            ReflectorData cached = reflectorCache.get(key);
            if (cached != null) {
                return cached == NO_REFLECTOR ? null : cached;
            }
        }
        for (ReflectorData d : componentData.reflectors) {
            if (matchesItem(stack, d.matching)) {
                if (key != 0L) reflectorCache.put(key, d);
                return d;
            }
        }
        if (key != 0L) reflectorCache.put(key, NO_REFLECTOR);
        return null;
    }

    public static ModeratorData findModerator(ItemStack stack) {
        if (stack == null) return null;
        long key = getItemKey(stack);
        if (key != 0L) {
            ModeratorData cached = moderatorCache.get(key);
            if (cached != null) {
                return cached == NO_MODERATOR ? null : cached;
            }
        }
        for (ModeratorData d : componentData.moderators) {
            if (matchesItem(stack, d.matching)) {
                if (key != 0L) moderatorCache.put(key, d);
                return d;
            }
        }
        if (key != 0L) moderatorCache.put(key, NO_MODERATOR);
        return null;
    }

    public static ControlRodData findControlRod(ItemStack stack) {
        if (stack == null) return null;
        long key = getItemKey(stack);
        if (key != 0L) {
            ControlRodData cached = controlRodCache.get(key);
            if (cached != null) {
                return cached == NO_CONTROL_ROD ? null : cached;
            }
        }
        for (ControlRodData d : componentData.controlRods) {
            if (matchesItem(stack, d.matching)) {
                if (key != 0L) controlRodCache.put(key, d);
                return d;
            }
        }
        if (key != 0L) controlRodCache.put(key, NO_CONTROL_ROD);
        return null;
    }

    public static HeatVentData findHeatVent(ItemStack stack) {
        if (stack == null) return null;
        long key = getItemKey(stack);
        if (key != 0L) {
            HeatVentData cached = heatVentCache.get(key);
            if (cached != null) {
                return cached == NO_HEAT_VENT ? null : cached;
            }
        }
        for (HeatVentData d : componentData.heatVents) {
            if (matchesItem(stack, d.matching)) {
                if (key != 0L) heatVentCache.put(key, d);
                return d;
            }
        }
        if (key != 0L) heatVentCache.put(key, NO_HEAT_VENT);
        return null;
    }

    public static HeatExchangerData findHeatExchanger(ItemStack stack) {
        if (stack == null) return null;
        long key = getItemKey(stack);
        if (key != 0L) {
            HeatExchangerData cached = heatExchangerCache.get(key);
            if (cached != null) {
                return cached == NO_HEAT_EXCHANGER ? null : cached;
            }
        }
        for (HeatExchangerData d : componentData.heatExchangers) {
            if (matchesItem(stack, d.matching)) {
                if (key != 0L) heatExchangerCache.put(key, d);
                return d;
            }
        }
        if (key != 0L) heatExchangerCache.put(key, NO_HEAT_EXCHANGER);
        return null;
    }

    public static InsulatorData findInsulator(ItemStack stack) {
        if (stack == null) return null;
        long key = getItemKey(stack);
        if (key != 0L) {
            InsulatorData cached = insulatorCache.get(key);
            if (cached != null) {
                return cached == NO_INSULATOR ? null : cached;
            }
        }
        for (InsulatorData d : componentData.insulators) {
            if (matchesItem(stack, d.matching)) {
                if (key != 0L) insulatorCache.put(key, d);
                return d;
            }
        }
        if (key != 0L) insulatorCache.put(key, NO_INSULATOR);
        return null;
    }

    public static RadiovoltaicData findRadiovoltaic(ItemStack stack) {
        if (stack == null) return null;
        long key = getItemKey(stack);
        if (key != 0L) {
            RadiovoltaicData cached = radiovoltaicCache.get(key);
            if (cached != null) {
                return cached == NO_RADIOVOLTAIC ? null : cached;
            }
        }
        for (RadiovoltaicData d : componentData.radiovoltaics) {
            if (matchesItem(stack, d.matching)) {
                if (key != 0L) radiovoltaicCache.put(key, d);
                return d;
            }
        }
        if (key != 0L) radiovoltaicCache.put(key, NO_RADIOVOLTAIC);
        return null;
    }

    public static CoolantFluidData findCoolantFluid(ItemStack stack) {
        if (stack == null) return null;
        long key = getItemKey(stack);
        if (key != 0L) {
            CoolantFluidData cached = coolantFluidItemCache.get(key);
            if (cached != null) {
                return cached == NO_COOLANT_FLUID ? null : cached;
            }
        }
        FluidStack fs = null;
        try {
            if (ItemList.Display_Fluid.hasBeenSet()) {
                fs = GTUtility.getFluidFromDisplayStack(stack);
            }
        } catch (Throwable ignored) {}
        if (fs != null) {
            CoolantFluidData res = findCoolantFluid(fs);
            if (key != 0L) coolantFluidItemCache.put(key, res != null ? res : NO_COOLANT_FLUID);
            return res;
        }
        if (key != 0L) coolantFluidItemCache.put(key, NO_COOLANT_FLUID);
        return null;
    }

    public static CoolantFluidData findCoolantFluid(FluidStack fluid) {
        if (fluid == null || fluid.getFluid() == null) return null;
        for (CoolantFluidData d : componentData.coolantFluids) {
            if (matchesFluid(fluid, d.matching)) return d;
        }
        return null;
    }

    public static CoolantFluidData findCoolantFluid(String fluidName) {
        if (fluidName == null) return null;
        for (CoolantFluidData d : componentData.coolantFluids) {
            if (matchesFluid(fluidName, d.matching)) return d;
        }
        return null;
    }

    public static CoolantItemData findCoolantItem(ItemStack stack) {
        if (stack == null) return null;
        long key = getItemKey(stack);
        if (key != 0L) {
            CoolantItemData cached = coolantItemCache.get(key);
            if (cached != null) {
                return cached == NO_COOLANT_ITEM ? null : cached;
            }
        }
        for (CoolantItemData d : componentData.coolantItems) {
            if (matchesItem(stack, d.matching)) {
                if (key != 0L) coolantItemCache.put(key, d);
                return d;
            }
        }
        if (key != 0L) coolantItemCache.put(key, NO_COOLANT_ITEM);
        return null;
    }

    public static boolean isSpentProduct(ItemStack stack) {
        if (stack == null) return false;
        long key = getItemKey(stack);
        if (key != 0L) {
            Boolean cached = spentProductCache.get(key);
            if (cached != null) {
                return cached.booleanValue();
            }
        }
        for (FuelData fd : fuels) {
            if (fd.spentProduct != null && !fd.spentProduct.isEmpty()) {
                if (matchesItem(stack, Collections.singletonList(fd.spentProduct))) {
                    if (key != 0L) spentProductCache.put(key, Boolean.TRUE);
                    return true;
                }
            }
        }
        if (key != 0L) spentProductCache.put(key, Boolean.FALSE);
        return false;
    }

    public static boolean isSpentProduct(String name) {
        if (name == null || name.isEmpty()) return false;
        for (FuelData fd : fuels) {
            if (fd.spentProduct != null && !fd.spentProduct.isEmpty()) {
                if (fd.spentProduct.equalsIgnoreCase(name) || ("fluid." + fd.spentProduct).equalsIgnoreCase(name))
                    return true;
            }
        }
        return false;
    }

    public static ItemStack resolveItemStack(String identifier) {
        if (identifier == null || identifier.trim()
            .isEmpty()) return null;
        String id = identifier.trim();

        // 1. Try ItemList enum (e.g. "DepletedRodLithium" or "ItemList.DepletedRodLithium")
        String itemListName = id.startsWith("ItemList.") ? id.substring("ItemList.".length()) : id;
        try {
            ItemList il = ItemList.valueOf(itemListName);
            if (il != null && il.hasBeenSet()) {
                ItemStack is = il.get(1L);
                if (is != null) return is;
            }
        } catch (Throwable ignored) {}

        // 2. Try OreDictionary (e.g. "ore:dustTritium")
        if (id.startsWith("ore:")) {
            try {
                List<ItemStack> ores = OreDictionary.getOres(id.substring(4));
                if (ores != null && !ores.isEmpty()) {
                    ItemStack is = ores.get(0)
                        .copy();
                    is.stackSize = 1;
                    return is;
                }
            } catch (Throwable ignored) {}
        }

        // 3. Try IC2 item by name (e.g. "IC2:reactorDepletedUraniumSimple")
        if (id.toUpperCase()
            .startsWith("IC2:")) {
            String ic2Name = id.substring(4);
            try {
                java.lang.reflect.Field f = Ic2Items.class.getField(ic2Name);
                Object val = f.get(null);
                if (val instanceof ItemStack is) {
                    ItemStack res = is.copy();
                    res.stackSize = 1;
                    return res;
                }
            } catch (Throwable ignored) {}
            try {
                ItemStack is = GameRegistry.findItemStack("IC2", ic2Name, 1);
                if (is != null) return is;
            } catch (Throwable ignored) {}
        }

        // 4. Try GameRegistry (e.g. "modularnuclear:rodCustomDepletedFuel4", "minecraft:diamond", "modid:item:meta")
        if (id.contains(":")) {
            String[] parts = id.split(":");
            if (parts.length >= 2) {
                String modId = parts[0];
                String name = parts[1];
                int meta = 0;
                if (parts.length >= 3) {
                    try {
                        meta = Integer.parseInt(parts[2]);
                    } catch (NumberFormatException ignored) {}
                }
                try {
                    Item it = GameRegistry.findItem(modId, name);
                    if (it != null) {
                        return new ItemStack(it, 1, meta);
                    }
                } catch (Throwable ignored) {}
            }
        }

        // 5. Try FluidRegistry (e.g. "fluid.distilledwater", "distilledwater", "Thorium Based Liquid Fuel")
        try {
            Fluid f = findFluid(id);
            if (f != null && gregtech.api.enums.ItemList.Display_Fluid.hasBeenSet()) {
                ItemStack is = GTUtility.getFluidDisplayStack(new FluidStack(f, 1000), false);
                if (is != null) return is;
            }
        } catch (Throwable ignored) {}

        return null;
    }

    // =========================================================================
    // Pattern Matching Helpers
    // =========================================================================

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

    public static boolean matchesItem(ItemStack stack, List<String> patterns) {
        if (stack == null || patterns == null || patterns.isEmpty()) return false;

        Item item = null;
        try {
            item = stack.getItem();
        } catch (Throwable ignored) {}

        String unloc = null;
        try {
            unloc = stack.getUnlocalizedName();
        } catch (Throwable ignored) {}

        String regName = null;
        try {
            if (item != null) {
                GameRegistry.UniqueIdentifier uid = GameRegistry.findUniqueIdentifierFor(item);
                if (uid != null) {
                    regName = uid.modId + ":" + uid.name;
                }
            }
        } catch (Throwable ignored) {}

        int[] oreIDs = null;
        try {
            oreIDs = OreDictionary.getOreIDs(stack);
        } catch (Throwable ignored) {}

        int damage = 0;
        try {
            damage = stack.getItemDamage();
        } catch (Throwable ignored) {}

        for (String pattern : patterns) {
            if (pattern == null || pattern.isEmpty()) continue;

            // 1. Exact Registry Name match ("gregtech:gt.rodUranium", "IC2:reactorUraniumSimple", or
            // "modid:item:damage")
            if (regName != null) {
                if (regName.equalsIgnoreCase(pattern)) return true;
                if ((regName + ":" + damage).equalsIgnoreCase(pattern)) return true;
            }

            // 2. Exact Unlocalized Name match ("gt.rodUranium", "item.super_uranium", etc.)
            if (unloc != null) {
                if (unloc.equalsIgnoreCase(pattern)) return true;
                if (unloc.startsWith("item.") && unloc.substring(5)
                    .equalsIgnoreCase(pattern)) return true;
                if (unloc.startsWith("tile.") && unloc.substring(5)
                    .equalsIgnoreCase(pattern)) return true;
                if (pattern.startsWith("item.") && pattern.substring(5)
                    .equalsIgnoreCase(unloc)) return true;
                if (pattern.startsWith("tile.") && pattern.substring(5)
                    .equalsIgnoreCase(unloc)) return true;
            }

            // 3. Exact OreDictionary match ("ore:plateBeryllium" or "plateBeryllium")
            String oreTarget = pattern.startsWith("ore:") ? pattern.substring(4) : pattern;
            if (oreIDs != null && oreIDs.length > 0) {
                for (int id : oreIDs) {
                    String oreName = OreDictionary.getOreName(id);
                    if (oreName != null && oreName.equalsIgnoreCase(oreTarget)) return true;
                }
            }

            // 4. Exact ItemList match (supports "ItemList.RodUranium" or enum name "RodUranium")
            if (pattern.startsWith("ItemList.")) {
                try {
                    ItemList il = ItemList.valueOf(pattern.substring("ItemList.".length()));
                    if (il != null && il.hasBeenSet() && il.isStackEqual(stack, false, true)) {
                        return true;
                    }
                } catch (Throwable ignored) {}
            } else if (!pattern.contains(":") && !pattern.contains(".") && !pattern.contains("*")) {
                try {
                    ItemList il = ItemList.valueOf(pattern);
                    if (il != null && il.hasBeenSet() && il.isStackEqual(stack, false, true)) {
                        return true;
                    }
                } catch (Throwable ignored) {}
            }

            // 5. Exact IC2 item match ("IC2:reactorUraniumSimple") via reflection / GameRegistry
            if (pattern.toUpperCase()
                .startsWith("IC2:")) {
                String ic2Name = pattern.substring(4);
                try {
                    java.lang.reflect.Field f = Ic2Items.class.getField(ic2Name);
                    Object val = f.get(null);
                    if (val instanceof ItemStack is && GTUtility.areStacksEqual(stack, is, true)) {
                        return true;
                    }
                } catch (Throwable ignored) {}
                try {
                    ItemStack is = GameRegistry.findItemStack("IC2", ic2Name, 1);
                    if (is != null && GTUtility.areStacksEqual(stack, is, true)) {
                        return true;
                    }
                } catch (Throwable ignored) {}
            }

            // 6. Explicit Wildcard Fallback (only if pattern explicitly contains '*')
            if (pattern.contains("*")) {
                if (regName != null) {
                    if (matchesPattern(regName, pattern)) return true;
                    if (matchesPattern(regName + ":" + damage, pattern)) return true;
                }
                if (unloc != null && matchesPattern(unloc, pattern)) {
                    return true;
                }
            }
        }
        return false;
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

    /**
     * Resolves a Forge Fluid by exact name, unlocalized name, localized name, or normalized name.
     * Handles common prefixes ("fluid.", "molten.") and spacing/parentheses variations.
     */
    public static Fluid findFluid(String query) {
        if (query == null || query.trim()
            .isEmpty()) return null;
        String q = query.trim();
        try {
            // 1. Direct registry lookup
            Fluid fluid = FluidRegistry.getFluid(q);
            if (fluid != null) return fluid;

            // 2. Prefix variations (fluid. and molten.)
            if (q.startsWith("fluid.")) {
                fluid = FluidRegistry.getFluid(q.substring(6));
                if (fluid != null) return fluid;
            } else {
                fluid = FluidRegistry.getFluid("fluid." + q);
                if (fluid != null) return fluid;
            }

            if (q.startsWith("molten.")) {
                fluid = FluidRegistry.getFluid(q.substring(7));
                if (fluid != null) return fluid;
            } else {
                fluid = FluidRegistry.getFluid("molten." + q);
                if (fluid != null) return fluid;
            }

            // 3. Scan registered fluids
            Map<String, Fluid> registered = FluidRegistry.getRegisteredFluids();
            if (registered != null && !registered.isEmpty()) {
                // 3a. Case-insensitive exact match against registered key, unlocalizedName, or localizedName
                for (Map.Entry<String, Fluid> entry : registered.entrySet()) {
                    if (entry.getKey()
                        .equalsIgnoreCase(q)) return entry.getValue();
                    Fluid f = entry.getValue();
                    if (f != null) {
                        String unloc = f.getUnlocalizedName();
                        if (unloc != null) {
                            if (unloc.equalsIgnoreCase(q)) return f;
                            if (unloc.startsWith("fluid.") && unloc.substring(6)
                                .equalsIgnoreCase(q)) return f;
                            if (q.startsWith("fluid.") && q.substring(6)
                                .equalsIgnoreCase(unloc)) return f;
                        }
                        try {
                            String loc = f.getLocalizedName(new FluidStack(f, 1000));
                            if (loc != null && loc.equalsIgnoreCase(q)) return f;
                        } catch (Throwable ignored) {}
                    }
                }

                // 3b. Normalized alphanumeric comparison (ignoring spaces, punctuation, prefixes)
                String normQ = normalizeFluidString(q);
                if (!normQ.isEmpty()) {
                    for (Map.Entry<String, Fluid> entry : registered.entrySet()) {
                        String normKey = normalizeFluidString(entry.getKey());
                        if (matchesPermutedDescriptors(normKey, normQ)) return entry.getValue();
                        Fluid f = entry.getValue();
                        if (f != null) {
                            String normUnloc = normalizeFluidString(f.getUnlocalizedName());
                            if (matchesPermutedDescriptors(normUnloc, normQ)) return f;
                            try {
                                String loc = f.getLocalizedName(new FluidStack(f, 1000));
                                if (loc != null && matchesPermutedDescriptors(normalizeFluidString(loc), normQ))
                                    return f;
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static boolean matchesPermutedDescriptors(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        String stripA = a.replace("state", "");
        String stripB = b.replace("state", "");
        if (stripA.equals(stripB)) return true;
        for (String modifier : new String[] { "excited", "depleted" }) {
            if (stripA.contains(modifier) && stripB.contains(modifier)) {
                String baseA = stripA.replace(modifier, "");
                String baseB = stripB.replace(modifier, "");
                if (!baseA.isEmpty() && baseA.equals(baseB)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean matchesFluid(FluidStack fluid, List<String> patterns) {
        if (fluid == null || fluid.getFluid() == null || patterns == null || patterns.isEmpty()) return false;
        String name = fluid.getFluid()
            .getName();
        String unloc = fluid.getFluid()
            .getUnlocalizedName();
        String normName = normalizeFluidString(name);
        String normUnloc = normalizeFluidString(unloc);
        for (String pattern : patterns) {
            if (pattern == null || pattern.isEmpty()) continue;
            if (name != null && name.equalsIgnoreCase(pattern)) return true;
            if (unloc != null) {
                if (unloc.equalsIgnoreCase(pattern)) return true;
                if (unloc.startsWith("fluid.") && unloc.substring(6)
                    .equalsIgnoreCase(pattern)) return true;
                if (pattern.startsWith("fluid.") && pattern.substring(6)
                    .equalsIgnoreCase(unloc)) return true;
            }
            if (pattern.contains("*")) {
                if (matchesPattern(name, pattern)) return true;
                if (unloc != null && matchesPattern(unloc, pattern)) return true;
            }
            String normPattern = normalizeFluidString(pattern);
            if (!normPattern.isEmpty()) {
                if (matchesPermutedDescriptors(normName, normPattern)
                    || matchesPermutedDescriptors(normUnloc, normPattern)) return true;
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
}
