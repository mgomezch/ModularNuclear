package com.gtnewhorizons.modularnuclear.common.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
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
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.PlatingData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.RadiovoltaicData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ReflectorData;
import com.gtnewhorizons.modularnuclear.common.config.data.FuelData;
import com.gtnewhorizons.modularnuclear.common.config.data.PhysicsData;
import com.gtnewhorizons.modularnuclear.common.config.data.TierData;
import com.gtnewhorizons.modularnuclear.common.nuclear.CustomFuelMix;

import cpw.mods.fml.common.registry.GameRegistry;

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

    static {
        loadDefaults();
    }

    private ReactorDataRegistry() {}

    public static synchronized void init(File modConfigDir) {
        configDirectory = new File(modConfigDir, "modularnuclear");
        if (!configDirectory.exists()) {
            configDirectory.mkdirs();
        }
        loadFromDir(configDirectory);
    }

    public static synchronized void loadDefaults() {
        physicsData = buildDefaultPhysics();

        tierDataMap.clear();
        for (TierData td : buildDefaultTiers()) {
            tierDataMap.put(td.tier, td);
        }

        fuels.clear();
        fuels.addAll(buildDefaultFuels());

        componentData = buildDefaultComponents();
    }

    public static synchronized void loadFromDir(File dir) {
        if (dir == null) return;
        if (!dir.exists()) dir.mkdirs();

        Yaml yaml = createYaml();

        // 1. Physics
        File physicsFile = new File(dir, "physics.yaml");
        if (physicsFile.exists()) {
            try (FileReader r = new FileReader(physicsFile)) {
                Object obj = yaml.load(r);
                if (obj instanceof Map<?, ?> m) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) m;
                    Object phys = map.getOrDefault("physics", map);
                    if (phys instanceof Map<?, ?> pMap) {
                        physicsData = PhysicsData.fromMap((Map<String, Object>) pMap);
                    }
                }
            } catch (Exception e) {
                ModularNuclear.LOG.error("Failed to load physics.yaml, keeping defaults", e);
            }
        } else {
            savePhysicsFile(physicsFile, yaml);
        }

        // 2. Tiers
        File tiersFile = new File(dir, "tiers.yaml");
        if (tiersFile.exists()) {
            try (FileReader r = new FileReader(tiersFile)) {
                Object obj = yaml.load(r);
                if (obj instanceof Map<?, ?> m) {
                    Object tList = ((Map<?, ?>) m).get("tiers");
                    if (tList instanceof List<?> list) {
                        tierDataMap.clear();
                        for (Object o : list) {
                            if (o instanceof Map<?, ?> tm) {
                                TierData td = TierData.fromMap((Map<String, Object>) tm);
                                tierDataMap.put(td.tier, td);
                            }
                        }
                    }
                } else if (obj instanceof List<?> list) {
                    tierDataMap.clear();
                    for (Object o : list) {
                        if (o instanceof Map<?, ?> tm) {
                            TierData td = TierData.fromMap((Map<String, Object>) tm);
                            tierDataMap.put(td.tier, td);
                        }
                    }
                }
            } catch (Exception e) {
                ModularNuclear.LOG.error("Failed to load tiers.yaml, keeping defaults", e);
            }
        } else {
            saveTiersFile(tiersFile, yaml);
        }

        // 3. Fuels
        File fuelsFile = new File(dir, "fuels.yaml");
        if (fuelsFile.exists()) {
            try (FileReader r = new FileReader(fuelsFile)) {
                Object obj = yaml.load(r);
                List<?> list = null;
                if (obj instanceof Map<?, ?> m) {
                    Object fObj = ((Map<?, ?>) m).get("fuels");
                    if (fObj instanceof List<?> l) list = l;
                } else if (obj instanceof List<?> l) {
                    list = l;
                }
                if (list != null) {
                    fuels.clear();
                    for (Object o : list) {
                        if (o instanceof Map<?, ?> fm) {
                            fuels.add(FuelData.fromMap((Map<String, Object>) fm));
                        }
                    }
                }
            } catch (Exception e) {
                ModularNuclear.LOG.error("Failed to load fuels.yaml, keeping defaults", e);
            }
        } else {
            saveFuelsFile(fuelsFile, yaml);
        }

        // 4. Components
        File compFile = new File(dir, "components.yaml");
        if (compFile.exists()) {
            try (FileReader r = new FileReader(compFile)) {
                Object obj = yaml.load(r);
                if (obj instanceof Map<?, ?> m) {
                    componentData = ComponentData.fromMap((Map<String, Object>) m);
                }
            } catch (Exception e) {
                ModularNuclear.LOG.error("Failed to load components.yaml, keeping defaults", e);
            }
        } else {
            saveComponentsFile(compFile, yaml);
        }
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
        return tierDataMap.getOrDefault(0, new TierData(tier, "Tier " + tier, "Casing", 2048L, 1200.0, 120.0, 100.0, 1.0));
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
                FuelData fd = new FuelData();
                fd.id = "custom_fuel_mix";
                fd.name = "Custom Fuel Mix";
                fd.baseNeutrons = mix.baseNeutrons;
                fd.thermalFissionMultiplier = mix.baseThermalFissionMultiplier;
                fd.fastFissionMultiplier = mix.baseFastFissionMultiplier;
                fd.absorptionThermal = 0.80;
                fd.absorptionFast = 0.25;
                fd.scatteringThermal = 0.10;
                fd.scatteringFast = 0.15;
                fd.moderation = 0.10;
                fd.heatTransferCoeff = 0.05;
                fd.defaultDurability = mix.defaultDurability;
                fd.maxTemperature = 2500.0;
                return fd;
            }
        }

        for (FuelData fd : fuels) {
            if (!fd.isLiquid && matchesItem(stack, fd.matching)) {
                return fd;
            }
        }
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
        for (ReflectorData d : componentData.reflectors) {
            if (matchesItem(stack, d.matching)) return d;
        }
        return null;
    }

    public static ModeratorData findModerator(ItemStack stack) {
        if (stack == null) return null;
        for (ModeratorData d : componentData.moderators) {
            if (matchesItem(stack, d.matching)) return d;
        }
        return null;
    }

    public static ControlRodData findControlRod(ItemStack stack) {
        if (stack == null) return null;
        for (ControlRodData d : componentData.controlRods) {
            if (matchesItem(stack, d.matching)) return d;
        }
        return null;
    }

    public static HeatVentData findHeatVent(ItemStack stack) {
        if (stack == null) return null;
        for (HeatVentData d : componentData.heatVents) {
            if (matchesItem(stack, d.matching)) return d;
        }
        return null;
    }

    public static HeatExchangerData findHeatExchanger(ItemStack stack) {
        if (stack == null) return null;
        for (HeatExchangerData d : componentData.heatExchangers) {
            if (matchesItem(stack, d.matching)) return d;
        }
        return null;
    }

    public static PlatingData findPlating(ItemStack stack) {
        if (stack == null) return null;
        for (PlatingData d : componentData.platings) {
            if (matchesItem(stack, d.matching)) return d;
        }
        return null;
    }

    public static InsulatorData findInsulator(ItemStack stack) {
        if (stack == null) return null;
        for (InsulatorData d : componentData.insulators) {
            if (matchesItem(stack, d.matching)) return d;
        }
        return null;
    }

    public static RadiovoltaicData findRadiovoltaic(ItemStack stack) {
        if (stack == null) return null;
        for (RadiovoltaicData d : componentData.radiovoltaics) {
            if (matchesItem(stack, d.matching)) return d;
        }
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
        for (CoolantItemData d : componentData.coolantItems) {
            if (matchesItem(stack, d.matching)) return d;
        }
        return null;
    }

    // =========================================================================
    // Pattern Matching Helpers
    // =========================================================================

    public static boolean matchesPattern(String text, String pattern) {
        if (text == null || pattern == null) return false;
        String t = text.trim().toLowerCase();
        String p = pattern.trim().toLowerCase();
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

            String oreTarget = pattern.startsWith("ore:") ? pattern.substring(4) : pattern;
            if (oreIDs != null && oreIDs.length > 0) {
                for (int id : oreIDs) {
                    String oreName = OreDictionary.getOreName(id);
                    if (oreName != null && oreName.equalsIgnoreCase(oreTarget)) return true;
                }
            }

            if (regName != null) {
                if (matchesPattern(regName, pattern)) return true;
                if (matchesPattern(regName + ":" + damage, pattern)) return true;
            }

            if (unloc != null && matchesPattern(unloc, pattern)) {
                return true;
            }
        }
        return false;
    }

    public static boolean matchesFluid(FluidStack fluid, List<String> patterns) {
        if (fluid == null || fluid.getFluid() == null || patterns == null || patterns.isEmpty()) return false;
        String name = fluid.getFluid().getName();
        String unloc = fluid.getFluid().getUnlocalizedName();
        for (String pattern : patterns) {
            if (pattern == null || pattern.isEmpty()) continue;
            if (matchesPattern(name, pattern)) return true;
            if (unloc != null && matchesPattern(unloc, pattern)) return true;
        }
        return false;
    }

    public static boolean matchesFluid(String fluidName, List<String> patterns) {
        if (fluidName == null || patterns == null || patterns.isEmpty()) return false;
        for (String pattern : patterns) {
            if (pattern == null || pattern.isEmpty()) continue;
            if (matchesPattern(fluidName, pattern)) return true;
        }
        return false;
    }

    // =========================================================================
    // Default Objects Builders
    // =========================================================================

    public static PhysicsData buildDefaultPhysics() {
        return new PhysicsData();
    }

    public static List<TierData> buildDefaultTiers() {
        List<TierData> list = new ArrayList<>();
        list.add(new TierData(0, "Tier 0 (EV - Electrum)", "Electrum Casing", 2048L, 1200.0, 120.0, 100.0, 1.0, "ic2coolant"));
        list.add(new TierData(1, "Tier 1 (IV - Platinum)", "Platinum Casing", 8192L, 1400.0, 140.0, 150.0, 1.1, "ic2coolant", "distilledwater"));
        list.add(new TierData(2, "Tier 2 (LuV - Osmium)", "Osmium Casing", 32768L, 1800.0, 180.0, 250.0, 1.2, "ic2coolant", "distilledwater", "sodium", "molten.sodium"));
        list.add(new TierData(3, "Tier 3 (ZPM - Quantium)", "Quantium Casing", 131072L, 2200.0, 220.0, 400.0, 1.35, "ic2coolant", "distilledwater", "heavywater", "sodium", "molten.sodium", "lead", "molten.lead"));
        list.add(new TierData(4, "Tier 4 (UV - Fluxed)", "Fluxed Electrum Casing", 524288L, 2600.0, 260.0, 600.0, 1.5, "*"));
        list.add(new TierData(5, "Tier 5 (UHV - Black Plutonium)", "Black Plutonium Casing", 2097152L, 3200.0, 320.0, 1000.0, 1.75, "*"));
        return list;
    }

    public static List<FuelData> buildDefaultFuels() {
        List<FuelData> list = new ArrayList<>();

        // Lithium & Glowstone
        list.add(new FuelData("fuel_lithium", "Lithium Fuel Rod", false, 1, 1.0, 0.05, 0.20, 0.85, 0.15, 0.10, 0.10, 0.05, 1.0, 10000, 1.0, 0.0, 0.1, "DepletedRodLithium", 2000.0, "*lithium*", "IC2:reactorLithiumCell"));
        list.add(new FuelData("fuel_glowstone", "Glowstone Fuel Rod", false, 1, 1.0, 0.05, 0.20, 0.80, 0.15, 0.10, 0.10, 0.05, 1.0, 10000, 1.0, 0.0, 0.1, "DepletedRodGlowstone", 2000.0, "*glowstone*"));

        // Thorium
        list.add(new FuelData("fuel_thorium_1", "Thorium Fuel Rod", false, 2, 1.0, 0.08, 0.25, 0.80, 0.15, 0.10, 0.10, 0.05, 2.0, 50000, 1.0, 0.0, 0.1, "DepletedRodThorium", 2200.0, "*thorium*", "gregtech:gt.reactorThorium"));
        list.add(new FuelData("fuel_thorium_2", "Dual Thorium Fuel Rod", false, 4, 1.0, 0.08, 0.25, 0.80, 0.15, 0.10, 0.10, 0.05, 2.0, 50000, 1.0, 0.0, 0.1, "DepletedRodThorium2", 2200.0, "*thorium2*", "*thorium*dual*"));
        list.add(new FuelData("fuel_thorium_4", "Quad Thorium Fuel Rod", false, 8, 1.0, 0.08, 0.25, 0.80, 0.15, 0.10, 0.10, 0.05, 2.0, 50000, 1.0, 0.0, 0.1, "DepletedRodThorium4", 2200.0, "*thorium4*", "*thorium*quad*"));

        // Uranium
        list.add(new FuelData("fuel_uranium_1", "Uranium Fuel Rod", false, 4, 1.0, 0.10, 0.25, 0.80, 0.15, 0.10, 0.10, 0.05, 2.0, 10000, 1.0, 0.0, 0.1, "DepletedRodUranium", 2000.0, "*uranium*", "IC2:reactorUraniumSimple", "gregtech:gt.reactorUranium"));
        list.add(new FuelData("fuel_uranium_2", "Dual Uranium Fuel Rod", false, 8, 1.0, 0.10, 0.25, 0.80, 0.15, 0.10, 0.10, 0.05, 2.0, 10000, 1.0, 0.0, 0.1, "DepletedRodUranium2", 2000.0, "*uranium2*", "IC2:reactorUraniumDual", "*uranium*dual*"));
        list.add(new FuelData("fuel_uranium_4", "Quad Uranium Fuel Rod", false, 16, 1.0, 0.10, 0.25, 0.80, 0.15, 0.10, 0.10, 0.05, 2.0, 10000, 1.0, 0.0, 0.1, "DepletedRodUranium4", 2000.0, "*uranium4*", "IC2:reactorUraniumQuad", "*uranium*quad*"));

        // MOX
        list.add(new FuelData("fuel_mox_1", "MOX Fuel Rod", false, 8, 1.5, 0.15, 0.30, 0.85, 0.15, 0.10, 0.10, 0.05, 3.0, 10000, 2.0, 0.5, 0.2, "DepletedRodMOX", 2200.0, "*mox*", "IC2:reactorMOXSimple", "gregtech:gt.reactorMOX"));
        list.add(new FuelData("fuel_mox_2", "Dual MOX Fuel Rod", false, 16, 1.5, 0.15, 0.30, 0.85, 0.15, 0.10, 0.10, 0.05, 3.0, 10000, 2.0, 0.5, 0.2, "DepletedRodMOX2", 2200.0, "*mox2*", "IC2:reactorMOXDual", "*mox*dual*"));
        list.add(new FuelData("fuel_mox_4", "Quad MOX Fuel Rod", false, 32, 1.5, 0.15, 0.30, 0.85, 0.15, 0.10, 0.10, 0.05, 3.0, 10000, 2.0, 0.5, 0.2, "DepletedRodMOX4", 2200.0, "*mox4*", "IC2:reactorMOXQuad", "*mox*quad*"));

        // High Density Uranium & Plutonium
        list.add(new FuelData("fuel_hd_uranium_1", "High Density Uranium Rod", false, 8, 1.2, 0.12, 0.25, 0.80, 0.15, 0.10, 0.10, 0.05, 2.5, 20000, 1.0, 0.0, 0.1, "DepletedRodHighDensityUranium", 2400.0, "*highdensityuranium*"));
        list.add(new FuelData("fuel_hd_uranium_2", "Dual High Density Uranium Rod", false, 16, 1.2, 0.12, 0.25, 0.80, 0.15, 0.10, 0.10, 0.05, 2.5, 20000, 1.0, 0.0, 0.1, "DepletedRodHighDensityUranium2", 2400.0, "*highdensityuranium2*"));
        list.add(new FuelData("fuel_hd_uranium_4", "Quad High Density Uranium Rod", false, 32, 1.2, 0.12, 0.25, 0.80, 0.15, 0.10, 0.10, 0.05, 2.5, 20000, 1.0, 0.0, 0.1, "DepletedRodHighDensityUranium4", 2400.0, "*highdensityuranium4*"));
        list.add(new FuelData("fuel_hd_plutonium_1", "High Density Plutonium Rod", false, 16, 1.8, 0.20, 0.30, 0.85, 0.15, 0.10, 0.10, 0.05, 3.5, 20000, 1.2, 0.3, 0.15, "DepletedRodHighDensityPlutonium", 2400.0, "*highdensityplutonium*"));
        list.add(new FuelData("fuel_hd_plutonium_2", "Dual High Density Plutonium Rod", false, 32, 1.8, 0.20, 0.30, 0.85, 0.15, 0.10, 0.10, 0.05, 3.5, 20000, 1.2, 0.3, 0.15, "DepletedRodHighDensityPlutonium2", 2400.0, "*highdensityplutonium2*"));
        list.add(new FuelData("fuel_hd_plutonium_4", "Quad High Density Plutonium Rod", false, 64, 1.8, 0.20, 0.30, 0.85, 0.15, 0.10, 0.10, 0.05, 3.5, 20000, 1.2, 0.3, 0.15, "DepletedRodHighDensityPlutonium4", 2400.0, "*highdensityplutonium4*"));

        // Excited Uranium & Plutonium
        list.add(new FuelData("fuel_excited_uranium_1", "Excited Uranium Rod", false, 16, 1.5, 0.15, 0.25, 0.80, 0.15, 0.10, 0.10, 0.05, 3.0, 15000, 1.0, 0.0, 0.1, "DepletedRodExcitedUranium", 2500.0, "*exciteduranium*"));
        list.add(new FuelData("fuel_excited_uranium_2", "Dual Excited Uranium Rod", false, 32, 1.5, 0.15, 0.25, 0.80, 0.15, 0.10, 0.10, 0.05, 3.0, 15000, 1.0, 0.0, 0.1, "DepletedRodExcitedUranium2", 2500.0, "*exciteduranium2*"));
        list.add(new FuelData("fuel_excited_uranium_4", "Quad Excited Uranium Rod", false, 64, 1.5, 0.15, 0.25, 0.80, 0.15, 0.10, 0.10, 0.05, 3.0, 15000, 1.0, 0.0, 0.1, "DepletedRodExcitedUranium4", 2500.0, "*exciteduranium4*"));
        list.add(new FuelData("fuel_excited_plutonium_1", "Excited Plutonium Rod", false, 32, 2.0, 0.25, 0.30, 0.85, 0.15, 0.10, 0.10, 0.05, 4.0, 15000, 1.2, 0.3, 0.15, "DepletedRodExcitedPlutonium", 2500.0, "*excitedplutonium*"));
        list.add(new FuelData("fuel_excited_plutonium_2", "Dual Excited Plutonium Rod", false, 64, 2.0, 0.25, 0.30, 0.85, 0.15, 0.10, 0.10, 0.05, 4.0, 15000, 1.2, 0.3, 0.15, "DepletedRodExcitedPlutonium2", 2500.0, "*excitedplutonium2*"));
        list.add(new FuelData("fuel_excited_plutonium_4", "Quad Excited Plutonium Rod", false, 128, 2.0, 0.25, 0.30, 0.85, 0.15, 0.10, 0.10, 0.05, 4.0, 15000, 1.2, 0.3, 0.15, "DepletedRodExcitedPlutonium4", 2500.0, "*excitedplutonium4*"));

        // Naquadah, The Core, Naquadria, Tiberium
        list.add(new FuelData("fuel_naquadah_1", "Naquadah Fuel Rod", false, 16, 2.0, 0.25, 0.30, 0.85, 0.15, 0.10, 0.10, 0.05, 4.0, 40000, 1.0, 0.0, 0.1, "DepletedRodNaquadah", 2800.0, "*naquadah*"));
        list.add(new FuelData("fuel_naquadah_2", "Dual Naquadah Fuel Rod", false, 32, 2.0, 0.25, 0.30, 0.85, 0.15, 0.10, 0.10, 0.05, 4.0, 40000, 1.0, 0.0, 0.1, "DepletedRodNaquadah2", 2800.0, "*naquadah2*"));
        list.add(new FuelData("fuel_naquadah_4", "Quad Naquadah Fuel Rod", false, 64, 2.0, 0.25, 0.30, 0.85, 0.15, 0.10, 0.10, 0.05, 4.0, 40000, 1.0, 0.0, 0.1, "DepletedRodNaquadah4", 2800.0, "*naquadah4*"));
        list.add(new FuelData("fuel_naquadah_32", "The Core (Naquadah-32)", false, 128, 4.0, 0.50, 0.35, 0.90, 0.15, 0.10, 0.10, 0.05, 8.0, 100000, 1.0, 0.0, 0.1, "DepletedRodNaquadah32", 3500.0, "*naquadah32*", "*thecore*"));
        list.add(new FuelData("fuel_naquadria_1", "Naquadria Fuel Rod", false, 16, 2.5, 0.30, 0.30, 0.85, 0.15, 0.10, 0.10, 0.05, 5.0, 30000, 1.0, 0.0, 0.1, "DepletedRodNaquadria", 3000.0, "*naquadria*"));
        list.add(new FuelData("fuel_naquadria_2", "Dual Naquadria Fuel Rod", false, 32, 2.5, 0.30, 0.30, 0.85, 0.15, 0.10, 0.10, 0.05, 5.0, 30000, 1.0, 0.0, 0.1, "DepletedRodNaquadria2", 3000.0, "*naquadria2*"));
        list.add(new FuelData("fuel_naquadria_4", "Quad Naquadria Fuel Rod", false, 64, 2.5, 0.30, 0.30, 0.85, 0.15, 0.10, 0.10, 0.05, 5.0, 30000, 1.0, 0.0, 0.1, "DepletedRodNaquadria4", 3000.0, "*naquadria4*"));
        list.add(new FuelData("fuel_tiberium_1", "Tiberium Fuel Rod", false, 8, 1.8, 0.20, 0.25, 0.80, 0.15, 0.10, 0.10, 0.05, 3.5, 25000, 1.0, 0.0, 0.1, "DepletedRodTiberium", 2600.0, "*tiberium*"));
        list.add(new FuelData("fuel_tiberium_2", "Dual Tiberium Fuel Rod", false, 16, 1.8, 0.20, 0.25, 0.80, 0.15, 0.10, 0.10, 0.05, 3.5, 25000, 1.0, 0.0, 0.1, "DepletedRodTiberium2", 2600.0, "*tiberium2*"));
        list.add(new FuelData("fuel_tiberium_4", "Quad Tiberium Fuel Rod", false, 32, 1.8, 0.20, 0.25, 0.80, 0.15, 0.10, 0.10, 0.05, 3.5, 25000, 1.0, 0.0, 0.1, "DepletedRodTiberium4", 2600.0, "*tiberium4*"));

        // Custom Fuel Rod
        list.add(new FuelData("fuel_custom_rod", "Custom Fuel Rod", false, 4, 1.0, 0.10, 0.25, 0.80, 0.15, 0.10, 0.10, 0.05, 2.0, 20000, 1.0, 0.0, 0.1, "modularnuclear:rodCustomDepletedFuel4", 2500.0, "modularnuclear:rodCustomFuel4", "*rodcustomfuel*"));

        // Liquid fuels
        list.add(new FuelData("fluid_fuel_thorium", "Thorium-Based Liquid Fuel", true, 4, 1.0, 0.08, 0.25, 0.85, 0.15, 0.10, 0.10, 0.15, 2.0, 50000, 1.0, 0.0, 0.1, "thoriumbasedliquidfueldepleted", 2500.0, "thoriumbasedliquidfuel"));
        list.add(new FuelData("fluid_fuel_thorium_excited", "Excited Thorium-Based Liquid Fuel", true, 8, 1.5, 0.12, 0.25, 0.85, 0.15, 0.10, 0.10, 0.15, 3.0, 35000, 1.0, 0.0, 0.1, "thoriumbasedliquidfueldepleted", 2500.0, "thoriumbasedliquidfuelexcited"));
        list.add(new FuelData("fluid_fuel_uranium", "Uranium-Based Liquid Fuel", true, 8, 1.0, 0.10, 0.25, 0.85, 0.15, 0.10, 0.10, 0.15, 2.0, 20000, 1.0, 0.0, 0.1, "uraniumbasedliquidfueldepleted", 2500.0, "uraniumbasedliquidfuel"));
        list.add(new FuelData("fluid_fuel_uranium_excited", "Excited Uranium-Based Liquid Fuel", true, 16, 1.5, 0.15, 0.25, 0.85, 0.15, 0.10, 0.10, 0.15, 3.0, 15000, 1.0, 0.0, 0.1, "uraniumbasedliquidfueldepleted", 2500.0, "uraniumbasedliquidfuelexcited"));
        list.add(new FuelData("fluid_fuel_plutonium", "Plutonium-Based Liquid Fuel", true, 16, 1.8, 0.20, 0.25, 0.85, 0.15, 0.10, 0.10, 0.15, 3.5, 20000, 1.0, 0.0, 0.1, "plutoniumbasedliquidfueldepleted", 2500.0, "plutoniumbasedliquidfuel"));
        list.add(new FuelData("fluid_fuel_plutonium_excited", "Excited Plutonium-Based Liquid Fuel", true, 32, 2.0, 0.25, 0.25, 0.85, 0.15, 0.10, 0.10, 0.15, 4.0, 15000, 1.0, 0.0, 0.1, "plutoniumbasedliquidfueldepleted", 2500.0, "plutoniumbasedliquidfuelexcited"));
        list.add(new FuelData("fluid_fuel_u_hexafluoride", "Uranium Hexafluoride", true, 12, 1.2, 0.12, 0.25, 0.85, 0.15, 0.10, 0.10, 0.12, 2.5, 25000, 1.0, 0.0, 0.1, "uraniumtetrafluoride", 2500.0, "uraniumhexafluoride"));

        return list;
    }

    public static ComponentData buildDefaultComponents() {
        ComponentData cd = new ComponentData();

        // Reflectors
        cd.reflectors.add(new ReflectorData("reflector_beryllium", "Beryllium Reflector", 0.01, 0.02, 0.95, 0.98, 0.20, 0.15, 0, "plateBeryllium", "blockBeryllium"));
        cd.reflectors.add(new ReflectorData("reflector_ic2", "Neutron Reflector", 0.01, 0.02, 0.95, 0.98, 0.20, 0.15, 10000, "IC2:reactorReflector", "gregtech:gt.neutronReflector*"));
        cd.reflectors.add(new ReflectorData("reflector_ic2_thick", "Thick Neutron Reflector", 0.005, 0.01, 0.98, 0.99, 0.25, 0.20, 40000, "IC2:reactorReflectorThick"));

        // Moderators
        cd.moderators.add(new ModeratorData("moderator_graphite", "Graphite Moderator", 0.002, 0.009, 0.93, 0.621, 0.50, 0.05, 2500.0, "blockGraphite", "plateGraphite", "ingotGraphite", "dustGraphite"));
        cd.moderators.add(new ModeratorData("moderator_carbon", "Carbon Moderator", 0.003, 0.012, 0.90, 0.60, 0.45, 0.05, 2200.0, "plateCarbon", "plateAlloyCarbon", "dustCarbon"));

        // Control Rods
        cd.controlRods.add(new ControlRodData("rod_silver", "Silver Control Rod", 0.50, 0.20, 0.05, 0.05, 0.20, "stickLongSilver", "*silver*"));
        cd.controlRods.add(new ControlRodData("rod_boron", "Boron Control Rod", 0.70, 0.35, 0.05, 0.05, 0.08, "stickLongBoron", "*boron*"));
        cd.controlRods.add(new ControlRodData("rod_cadmium", "Cadmium Control Rod", 0.85, 0.50, 0.05, 0.05, 0.12, "stickLongCadmium", "*cadmium*"));
        cd.controlRods.add(new ControlRodData("rod_indium", "Indium Control Rod", 0.95, 0.65, 0.05, 0.05, 0.14, "stickLongIndium", "*indium*"));
        cd.controlRods.add(new ControlRodData("rod_hafnium", "Hafnium Control Rod", 0.99, 0.80, 0.05, 0.05, 0.16, "stickLongHafnium", "*hafnium*"));

        // Heat Vents
        cd.heatVents.add(new HeatVentData("vent_standard", "Reactor Heat Vent", 0.40, 6.0, 1500.0, "IC2:reactorVent"));
        cd.heatVents.add(new HeatVentData("vent_gold", "Advanced Heat Vent", 0.45, 12.0, 1600.0, "IC2:reactorVentGold"));
        cd.heatVents.add(new HeatVentData("vent_spread", "Overclocked Heat Vent", 0.40, 8.0, 1500.0, "IC2:reactorVentSpread"));
        cd.heatVents.add(new HeatVentData("vent_diamond", "Diamond Heat Vent", 0.50, 20.0, 2000.0, "IC2:reactorVentDiamond"));

        // Heat Exchangers
        cd.heatExchangers.add(new HeatExchangerData("exchanger_standard", "Heat Exchanger", 0.40, 12.0, 1500.0, "IC2:reactorHeatSwitch"));
        cd.heatExchangers.add(new HeatExchangerData("exchanger_spread", "Advanced Heat Exchanger", 0.45, 24.0, 1600.0, "IC2:reactorHeatSwitchSpread"));
        cd.heatExchangers.add(new HeatExchangerData("exchanger_diamond", "Component Heat Exchanger", 0.50, 36.0, 2000.0, "IC2:reactorHeatSwitchDiamond"));

        // Platings
        cd.platings.add(new PlatingData("plating_standard", "Reactor Plating", 1000.0, 0.05, 0.05, "IC2:reactorPlating"));
        cd.platings.add(new PlatingData("plating_heat", "Heat-Capacity Reactor Plating", 2000.0, 0.02, 0.08, "IC2:reactorPlatingHeat"));
        cd.platings.add(new PlatingData("plating_explosive", "Containment Reactor Plating", 500.0, 0.15, 0.05, "IC2:reactorPlatingExplosive"));

        // Insulators
        cd.insulators.add(new InsulatorData("insulator_naquarite", "Naquarite Universal Insulator Foil", 1.0, 0.005, 1.0, 1.0, 0.0, 0.0, "Naquarite_Universal_Insulator_Foil*", "*naquarite*insulator*"));
        cd.insulators.add(new InsulatorData("insulator_mica", "Mica Insulator Foil", 0.60, 0.01, 0.01, 0.01, 0.05, 0.05, "MicaInsulatorFoil*", "*mica*foil*"));
        cd.insulators.add(new InsulatorData("insulator_cloth_t2", "Thermal Cloth T2", 0.40, 0.01, 0.01, 0.01, 0.05, 0.05, "ThermalClothT2*", "*thermalcloth*t2*"));
        cd.insulators.add(new InsulatorData("insulator_cloth_t1", "Thermal Cloth", 0.20, 0.01, 0.01, 0.01, 0.05, 0.05, "ThermalCloth*", "*thermalcloth*", "itemBasicAsteroids:7"));

        // Radiovoltaics
        cd.radiovoltaics.add(new RadiovoltaicData("radiovoltaic_hv", "HV Radiovoltaic Plate", 0.25, 0.10, 1.0, 1.0, "modularnuclear:radiovoltaicPlateHV", "*radiovoltaic*hv*"));
        cd.radiovoltaics.add(new RadiovoltaicData("radiovoltaic_ev", "EV Radiovoltaic Plate", 0.50, 0.10, 1.0, 1.0, "modularnuclear:radiovoltaicPlateEV", "*radiovoltaic*ev*"));

        // Coolant Fluids
        cd.coolantFluids.add(new CoolantFluidData("coolant_water", "Water", 100.0, 0.25, 4.184, 0.05, 0.10, 0.70, 0.70, 0.25, "steam", 1.0, "water"));
        cd.coolantFluids.add(new CoolantFluidData("coolant_distilled_water", "Distilled Water", 100.0, 0.30, 4.184, 0.05, 0.10, 0.70, 0.70, 0.25, "ic2superheatedsteam", 1.2, "distilledwater", "ic2distilledwater"));
        cd.coolantFluids.add(new CoolantFluidData("coolant_heavy_water", "Heavy Water", 101.4, 0.30, 4.22, 0.005, 0.01, 0.85, 0.85, 0.90, "steam", 1.5, "heavywater"));
        cd.coolantFluids.add(new CoolantFluidData("coolant_ic2", "IC2 Coolant", 300.0, 0.50, 8.0, 0.03, 0.12, 0.45, 0.45, 0.40, "ic2hotcoolant", 2.0, "coolant", "ic2coolant"));
        cd.coolantFluids.add(new CoolantFluidData("coolant_sodium", "Molten Sodium", 883.0, 0.70, 1.28, 0.01, 0.05, 0.20, 0.20, 0.05, "molten.sodium", 3.0, "sodium", "molten.sodium"));
        cd.coolantFluids.add(new CoolantFluidData("coolant_lead", "Molten Lead", 1749.0, 0.75, 0.16, 0.005, 0.02, 0.15, 0.15, 0.02, "molten.lead", 3.5, "lead", "molten.lead"));

        // Coolant Items
        cd.coolantItems.add(new CoolantItemData("coolant_cell_he", "Helium Coolant Cell", 60000.0, 0.40, 0.02, 0.05, 0.45, 0.45, 0.40, "gregtech:gt.reactorCoolantHe*", "IC2:reactorCoolant*"));
        cd.coolantItems.add(new CoolantItemData("coolant_cell_nak", "NaK Coolant Cell", 180000.0, 0.50, 0.02, 0.05, 0.35, 0.35, 0.25, "gregtech:gt.reactorCoolantNaK*"));
        cd.coolantItems.add(new CoolantItemData("coolant_cell_sp", "Sp Coolant Cell", 360000.0, 0.60, 0.01, 0.03, 0.25, 0.25, 0.15, "gregtech:gt.reactorCoolantSp*"));
        cd.coolantItems.add(new CoolantItemData("condensator", "Condensator", 100000.0, 0.45, 0.03, 0.08, 0.40, 0.40, 0.30, "IC2:reactorCondensator*"));

        return cd;
    }
}
