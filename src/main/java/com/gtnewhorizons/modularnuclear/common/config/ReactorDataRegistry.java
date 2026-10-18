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
import gregtech.api.enums.ItemList;
import gregtech.api.util.GTUtility;
import ic2.core.Ic2Items;
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
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;

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
        syncPhysicsToEngine();
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
        NuclearSimulationEngine.wallAbsorbHeatPerNeutron = physicsData.wallAbsorbHeatPerNeutron;
        NuclearSimulationEngine.setBeamEnergyToFastNeutronMultiplier(physicsData.beamEnergyToFastNeutronMultiplier);
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
        syncPhysicsToEngine();

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
                int rodSize = 4;
                if (stack.getItem() instanceof com.gtnewhorizons.modularnuclear.common.item.ItemCustomFuelRod customRod) {
                    rodSize = customRod.rodSize;
                }
                FuelData fd = new FuelData();
                fd.id = "custom_fuel_mix_" + rodSize;
                fd.name = (rodSize == 1 ? "Custom Fuel Rod" : rodSize == 2 ? "Dual Custom Fuel Rod" : "Quad Custom Fuel Rod");
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

    public static boolean isSpentProduct(ItemStack stack) {
        if (stack == null) return false;
        for (FuelData fd : fuels) {
            if (fd.spentProduct != null && !fd.spentProduct.isEmpty()) {
                if (matchesItem(stack, Collections.singletonList(fd.spentProduct))) return true;
            }
        }
        return false;
    }

    public static boolean isSpentProduct(String name) {
        if (name == null || name.isEmpty()) return false;
        for (FuelData fd : fuels) {
            if (fd.spentProduct != null && !fd.spentProduct.isEmpty()) {
                if (fd.spentProduct.equalsIgnoreCase(name) || ("fluid." + fd.spentProduct).equalsIgnoreCase(name)) return true;
            }
        }
        return false;
    }

    public static ItemStack resolveItemStack(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) return null;
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
                    ItemStack is = ores.get(0).copy();
                    is.stackSize = 1;
                    return is;
                }
            } catch (Throwable ignored) {}
        }

        // 3. Try IC2 item by name (e.g. "IC2:reactorDepletedUraniumSimple")
        if (id.toUpperCase().startsWith("IC2:")) {
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
                ItemStack is = gregtech.api.util.GTModHandler.getModItem("IC2", ic2Name, 1);
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

            // 1. Exact ItemList match (supports "ItemList.RodUranium" or enum name "RodUranium")
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

            // 2. Exact IC2 item match ("IC2:reactorUraniumSimple")
            if (pattern.toUpperCase().startsWith("IC2:")) {
                String ic2Name = pattern.substring(4);
                try {
                    java.lang.reflect.Field f = Ic2Items.class.getField(ic2Name);
                    Object val = f.get(null);
                    if (val instanceof ItemStack is && GTUtility.areStacksEqual(stack, is, true)) {
                        return true;
                    }
                } catch (Throwable ignored) {}
                try {
                    ItemStack is = gregtech.api.util.GTModHandler.getModItem("IC2", ic2Name, 1);
                    if (is != null && GTUtility.areStacksEqual(stack, is, true)) {
                        return true;
                    }
                } catch (Throwable ignored) {}
            }

            // 3. Exact OreDictionary match ("ore:plateBeryllium" or "plateBeryllium")
            String oreTarget = pattern.startsWith("ore:") ? pattern.substring(4) : pattern;
            if (oreIDs != null && oreIDs.length > 0) {
                for (int id : oreIDs) {
                    String oreName = OreDictionary.getOreName(id);
                    if (oreName != null && oreName.equalsIgnoreCase(oreTarget)) return true;
                }
            }

            // 4. Exact Registry Name match ("gregtech:gt.rodUranium" or "modid:item:damage")
            if (regName != null) {
                if (regName.equalsIgnoreCase(pattern)) return true;
                if ((regName + ":" + damage).equalsIgnoreCase(pattern)) return true;
            }

            // 5. Exact Unlocalized Name match ("gt.rodUranium", "item.super_uranium", etc.)
            if (unloc != null) {
                if (unloc.equalsIgnoreCase(pattern)) return true;
                if (unloc.startsWith("item.") && unloc.substring(5).equalsIgnoreCase(pattern)) return true;
                if (unloc.startsWith("tile.") && unloc.substring(5).equalsIgnoreCase(pattern)) return true;
                if (pattern.startsWith("item.") && pattern.substring(5).equalsIgnoreCase(unloc)) return true;
                if (pattern.startsWith("tile.") && pattern.substring(5).equalsIgnoreCase(unloc)) return true;
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

    public static boolean matchesFluid(FluidStack fluid, List<String> patterns) {
        if (fluid == null || fluid.getFluid() == null || patterns == null || patterns.isEmpty()) return false;
        String name = fluid.getFluid().getName();
        String unloc = fluid.getFluid().getUnlocalizedName();
        for (String pattern : patterns) {
            if (pattern == null || pattern.isEmpty()) continue;
            if (name != null && name.equalsIgnoreCase(pattern)) return true;
            if (unloc != null) {
                if (unloc.equalsIgnoreCase(pattern)) return true;
                if (unloc.startsWith("fluid.") && unloc.substring(6).equalsIgnoreCase(pattern)) return true;
                if (pattern.startsWith("fluid.") && pattern.substring(6).equalsIgnoreCase(unloc)) return true;
            }
            if (pattern.contains("*")) {
                if (matchesPattern(name, pattern)) return true;
                if (unloc != null && matchesPattern(unloc, pattern)) return true;
            }
        }
        return false;
    }

    public static boolean matchesFluid(String fluidName, List<String> patterns) {
        if (fluidName == null || patterns == null || patterns.isEmpty()) return false;
        String cleanFluid = fluidName.startsWith("fluid.") ? fluidName.substring(6) : fluidName;
        for (String pattern : patterns) {
            if (pattern == null || pattern.isEmpty()) continue;
            if (fluidName.equalsIgnoreCase(pattern)) return true;
            String cleanPattern = pattern.startsWith("fluid.") ? pattern.substring(6) : pattern;
            if (cleanFluid.equalsIgnoreCase(cleanPattern)) return true;
            if (pattern.contains("*") && matchesPattern(fluidName, pattern)) return true;
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

    private static void addRodTrio(List<FuelData> list, String baseId, String baseName, String archetype,
        int baseNeutrons, double thermalFissMult, double fastFissMult,
        double absFast, double absThermal, double scattFast, double scattThermal, double moderation,
        double heatTransferCoeff, double heatPerReaction, int defaultDurability,
        double peakReactivityTemp, double floorTemp,
        double peakMult, double peakPoint, double endMult,
        String singleItem, String dualItem, String quadItem,
        String singleSpent, String dualSpent, String quadSpent,
        String singleIc2, String dualIc2, String quadIc2,
        String unlocRoot) {

        String quadGt = Character.toLowerCase(quadItem.charAt(0)) + quadItem.substring(1);
        String dualGt = Character.toLowerCase(dualItem.charAt(0)) + dualItem.substring(1);
        String singleGt = Character.toLowerCase(singleItem.charAt(0)) + singleItem.substring(1);

        // Quad rod first (highest cell density)
        list.add(new FuelData(
            baseId + "_4", "Quad " + baseName + " Fuel Rod", archetype, false, 4, baseNeutrons * 4,
            thermalFissMult, fastFissMult, absFast, absThermal, scattFast, scattThermal, moderation,
            heatTransferCoeff, heatPerReaction, defaultDurability, peakReactivityTemp, floorTemp,
            peakMult, peakPoint, endMult, "ItemList." + quadSpent,
            "ItemList." + quadItem, quadItem,
            "gregtech:gt." + quadGt, "gt." + quadGt,
            "gt.reactor" + quadItem, "gt.reactor" + (unlocRoot != null ? unlocRoot : "") + "4",
            quadIc2 != null ? quadIc2 : ""
        ));

        // Dual rod
        list.add(new FuelData(
            baseId + "_2", "Dual " + baseName + " Fuel Rod", archetype, false, 2, baseNeutrons * 2,
            thermalFissMult, fastFissMult, absFast, absThermal, scattFast, scattThermal, moderation,
            heatTransferCoeff, heatPerReaction, defaultDurability, peakReactivityTemp, floorTemp,
            peakMult, peakPoint, endMult, "ItemList." + dualSpent,
            "ItemList." + dualItem, dualItem,
            "gregtech:gt." + dualGt, "gt." + dualGt,
            "gt.reactor" + dualItem, "gt.reactor" + (unlocRoot != null ? unlocRoot : "") + "2",
            dualIc2 != null ? dualIc2 : ""
        ));

        // Single rod
        list.add(new FuelData(
            baseId + "_1", baseName + " Fuel Rod", archetype, false, 1, baseNeutrons,
            thermalFissMult, fastFissMult, absFast, absThermal, scattFast, scattThermal, moderation,
            heatTransferCoeff, heatPerReaction, defaultDurability, peakReactivityTemp, floorTemp,
            peakMult, peakPoint, endMult, "ItemList." + singleSpent,
            "ItemList." + singleItem, singleItem,
            "gregtech:gt." + singleGt, "gt." + singleGt,
            "gt.reactor" + singleItem, "gt.reactor" + (unlocRoot != null ? unlocRoot : ""),
            singleIc2 != null ? singleIc2 : ""
        ));
    }

    public static List<FuelData> buildDefaultFuels() {
        List<FuelData> list = new ArrayList<>();

        // Lithium & Glowstone (Breeder Targets)
        list.add(new FuelData("fuel_lithium", "Lithium Fuel Rod", "breeder", false, 1, 1, 0.80, 0.05, 0.20, 0.85, 0.15, 0.10, 0.10, 0.05, 1.0, 80000, 390.0, 2100.0, 1.0, 0.0, 0.10, "ItemList.DepletedRodLithium",
            "ItemList.RodLithium", "RodLithium", "gregtech:gt.rodLithium", "gt.rodLithium", "gt.reactorLithium", "gt.reactorRodLithium", "IC2:reactorLithiumCell"));
        list.add(new FuelData("fuel_glowstone", "Glowstone Fuel Rod", "breeder", false, 1, 1, 0.60, 0.05, 0.20, 0.80, 0.15, 0.10, 0.10, 0.05, 1.0, 80000, 200.0, 2000.0, 1.0, 0.0, 0.10, "ItemList.DepletedRodGlowstone",
            "ItemList.RodGlowstone", "RodGlowstone", "gregtech:gt.rodGlowstone", "gt.rodGlowstone", "gt.reactorGlowstone", "gt.reactorRodGlowstone"));

        // Excited Uranium & Plutonium (Excited archetype: primary neutron sources / drivers)
        addRodTrio(list, "fuel_excited_uranium", "Excited Uranium", "excited", 16, 2.50, 0.15, 0.25, 0.80, 0.15, 0.10, 0.10, 0.05, 3.0, 48000, 1540.0, 2700.0, 1.0, 0.0, 0.10, "RodExcitedUranium", "RodExcitedUranium2", "RodExcitedUranium4", "DepletedRodExcitedUranium", "DepletedRodExcitedUranium2", "DepletedRodExcitedUranium4", null, null, null, "ExcitedUranium");
        addRodTrio(list, "fuel_excited_plutonium", "Excited Plutonium", "excited", 24, 2.90, 0.25, 0.30, 0.85, 0.15, 0.10, 0.10, 0.05, 4.0, 80000, 1730.0, 2800.0, 1.2, 0.3, 0.15, "RodExcitedPlutonium", "RodExcitedPlutonium2", "RodExcitedPlutonium4", "DepletedRodExcitedPlutonium", "DepletedRodExcitedPlutonium2", "DepletedRodExcitedPlutonium4", null, null, null, "ExcitedPlutonium");

        // High Density Uranium & Plutonium (Dense archetype: subcritical multipliers)
        addRodTrio(list, "fuel_hd_uranium", "High Density Uranium", "dense", 8, 1.80, 0.12, 0.25, 0.80, 0.15, 0.10, 0.10, 0.05, 2.5, 560000, 1150.0, 2500.0, 1.0, 0.0, 0.10, "RodHighDensityUranium", "RodHighDensityUranium2", "RodHighDensityUranium4", "DepletedRodHighDensityUranium", "DepletedRodHighDensityUranium2", "DepletedRodHighDensityUranium4", null, null, null, "HighDensityUranium");
        addRodTrio(list, "fuel_hd_plutonium", "High Density Plutonium", "dense", 12, 2.10, 0.20, 0.30, 0.85, 0.15, 0.10, 0.10, 0.05, 3.5, 560000, 1350.0, 2600.0, 1.2, 0.3, 0.15, "RodHighDensityPlutonium", "RodHighDensityPlutonium2", "RodHighDensityPlutonium4", "DepletedRodHighDensityPlutonium", "DepletedRodHighDensityPlutonium2", "DepletedRodHighDensityPlutonium4", null, null, null, "HighDensityPlutonium");

        // The Core, Naquadria, Naquadah (Dense archetype: subcritical multipliers)
        list.add(new FuelData("fuel_naquadah_32", "The Core (Naquadah-32)", "dense", false, 16, 512, 5.50, 0.50, 0.35, 0.90, 0.15, 0.10, 0.10, 0.05, 8.0, 2560000, 2500.0, 3200.0, 1.0, 0.0, 0.10, "ItemList.DepletedRodNaquadah32",
            "ItemList.RodNaquadah32", "RodNaquadah32", "gregtech:gt.rodNaquadah32", "gt.rodNaquadah32", "gt.reactorNaquadah32", "gt.reactorRodNaquadah32"));
        addRodTrio(list, "fuel_naquadria", "Naquadria", "dense", 20, 4.00, 0.30, 0.30, 0.85, 0.15, 0.10, 0.10, 0.05, 5.0, 800000, 2110.0, 3000.0, 1.0, 0.0, 0.10, "RodNaquadria", "RodNaquadria2", "RodNaquadria4", "DepletedRodNaquadria", "DepletedRodNaquadria2", "DepletedRodNaquadria4", null, null, null, "Naquadria");
        addRodTrio(list, "fuel_naquadah", "Naquadah", "dense", 16, 3.40, 0.25, 0.30, 0.85, 0.15, 0.10, 0.10, 0.05, 4.0, 800000, 1920.0, 2900.0, 1.0, 0.0, 0.10, "RodNaquadah", "RodNaquadah2", "RodNaquadah4", "DepletedRodNaquadah", "DepletedRodNaquadah2", "DepletedRodNaquadah4", null, null, null, "Naquadah");

        // MOX, Thorium, Uranium (Standard archetype)
        addRodTrio(list, "fuel_mox", "MOX", "standard", 8, 1.50, 0.15, 0.30, 0.85, 0.15, 0.10, 0.10, 0.05, 3.0, 80000, 960.0, 2400.0, 2.0, 0.5, 0.20, "RodMOX", "RodMOX2", "RodMOX4", "DepletedRodMOX", "DepletedRodMOX2", "DepletedRodMOX4", "IC2:reactorMOXSimple", "IC2:reactorMOXDual", "IC2:reactorMOXQuad", "MOX");
        addRodTrio(list, "fuel_thorium", "Thorium", "standard", 2, 1.00, 0.08, 0.25, 0.80, 0.15, 0.10, 0.10, 0.05, 2.0, 400000, 580.0, 2200.0, 1.0, 0.0, 0.10, "RodThorium", "RodThorium2", "RodThorium4", "DepletedRodThorium", "DepletedRodThorium2", "DepletedRodThorium4", null, null, null, "Thorium");
        addRodTrio(list, "fuel_uranium", "Uranium", "standard", 4, 1.25, 0.10, 0.25, 0.80, 0.15, 0.10, 0.10, 0.05, 2.0, 160000, 770.0, 2300.0, 1.0, 0.0, 0.10, "RodUranium", "RodUranium2", "RodUranium4", "DepletedRodUranium", "DepletedRodUranium2", "DepletedRodUranium4", "IC2:reactorUraniumSimple", "IC2:reactorUraniumDual", "IC2:reactorUraniumQuad", "Uranium");

        // Tiberium (Standard archetype)
        addRodTrio(list, "fuel_tiberium", "Tiberium", "standard", 24, 4.70, 0.20, 0.25, 0.80, 0.15, 0.10, 0.10, 0.05, 3.5, 400000, 2300.0, 3100.0, 1.0, 0.0, 0.10, "RodTiberium", "RodTiberium2", "RodTiberium4", "DepletedRodTiberium", "DepletedRodTiberium2", "DepletedRodTiberium4", null, null, null, "Tiberium");

        // Custom Fuel Rods (Standard archetype)
        list.add(new FuelData("fuel_custom_rod_1", "Custom Fuel Rod", "standard", false, 1, 1, 1.0, 0.10, 0.25, 0.80, 0.15, 0.10, 0.10, 0.05, 2.0, 160000, 770.0, 2300.0, 1.0, 0.0, 0.10, "modularnuclear:rodCustomDepletedFuel1", "modularnuclear:rodCustomFuel1", "fuelRodCustom1"));
        list.add(new FuelData("fuel_custom_rod_2", "Dual Custom Fuel Rod", "standard", false, 2, 2, 1.0, 0.10, 0.25, 0.80, 0.15, 0.10, 0.10, 0.05, 2.0, 160000, 770.0, 2300.0, 1.0, 0.0, 0.10, "modularnuclear:rodCustomDepletedFuel2", "modularnuclear:rodCustomFuel2", "fuelRodCustom2"));
        list.add(new FuelData("fuel_custom_rod_4", "Quad Custom Fuel Rod", "standard", false, 4, 4, 1.0, 0.10, 0.25, 0.80, 0.15, 0.10, 0.10, 0.05, 2.0, 160000, 770.0, 2300.0, 1.0, 0.0, 0.10, "modularnuclear:rodCustomDepletedFuel4", "modularnuclear:rodCustomFuel4", "fuelRodCustom4"));

        // Liquid fuels (Excited first)
        list.add(new FuelData("fluid_fuel_thorium_excited", "Excited Thorium-Based Liquid Fuel", "excited", true, 4, 8, 1.50, 0.12, 0.25, 0.85, 0.15, 0.10, 0.10, 0.15, 3.0, 35000, 580.0, 2200.0, 1.0, 0.0, 0.10, "thoriumbasedliquidfueldepleted", 4, "thoriumbasedliquidfuelexcited", "fluid.thoriumbasedliquidfuelexcited"));
        list.add(new FuelData("fluid_fuel_thorium", "Thorium-Based Liquid Fuel", "standard", true, 4, 4, 1.00, 0.08, 0.25, 0.85, 0.15, 0.10, 0.10, 0.15, 2.0, 50000, 580.0, 2200.0, 1.0, 0.0, 0.10, "thoriumbasedliquidfueldepleted", 0, "thoriumbasedliquidfuel", "fluid.thoriumbasedliquidfuel"));
        list.add(new FuelData("fluid_fuel_uranium_excited", "Excited Uranium-Based Liquid Fuel", "excited", true, 4, 16, 2.50, 0.15, 0.25, 0.85, 0.15, 0.10, 0.10, 0.15, 3.0, 15000, 1540.0, 2700.0, 1.0, 0.0, 0.10, "uraniumbasedliquidfueldepleted", 3, "uraniumbasedliquidfuelexcited", "fluid.uraniumbasedliquidfuelexcited"));
        list.add(new FuelData("fluid_fuel_uranium", "Uranium-Based Liquid Fuel", "standard", true, 4, 8, 1.25, 0.10, 0.25, 0.85, 0.15, 0.10, 0.10, 0.15, 2.0, 20000, 770.0, 2300.0, 1.0, 0.0, 0.10, "uraniumbasedliquidfueldepleted", 1, "uraniumbasedliquidfuel", "fluid.uraniumbasedliquidfuel"));
        list.add(new FuelData("fluid_fuel_plutonium_excited", "Excited Plutonium-Based Liquid Fuel", "excited", true, 4, 32, 2.90, 0.25, 0.25, 0.85, 0.15, 0.10, 0.10, 0.15, 4.0, 15000, 1730.0, 2800.0, 1.0, 0.0, 0.10, "plutoniumbasedliquidfueldepleted", 4, "plutoniumbasedliquidfuelexcited", "fluid.plutoniumbasedliquidfuelexcited"));
        list.add(new FuelData("fluid_fuel_plutonium", "Plutonium-Based Liquid Fuel", "standard", true, 4, 16, 1.80, 0.20, 0.25, 0.85, 0.15, 0.10, 0.10, 0.15, 3.5, 20000, 1350.0, 2600.0, 1.0, 0.0, 0.10, "plutoniumbasedliquidfueldepleted", 2, "plutoniumbasedliquidfuel", "fluid.plutoniumbasedliquidfuel"));
        list.add(new FuelData("fluid_fuel_u_hexafluoride", "Uranium Hexafluoride", "standard", true, 4, 12, 1.25, 0.12, 0.25, 0.85, 0.15, 0.10, 0.10, 0.12, 2.5, 25000, 770.0, 2300.0, 1.0, 0.0, 0.10, "uraniumtetrafluoride", 2, "uraniumhexafluoride", "fluid.uraniumhexafluoride"));

        return list;
    }

    public static ComponentData buildDefaultComponents() {
        ComponentData cd = new ComponentData();

        // Reflectors - plates only, no blocks or rods
        cd.reflectors.add(new ReflectorData("reflector_gold", "Gold Plate Reflector", 0.15, 0.10, 0.85, 0.90, 0.0, 0.30, 0,
            "plateGold", "ore:plateGold"));
        cd.reflectors.add(new ReflectorData("reflector_stainless_steel", "Stainless Steel Plate Reflector", 0.10, 0.05, 0.90, 0.95, 0.0, 0.20, 0,
            "plateStainlessSteel", "ore:plateStainlessSteel"));
        cd.reflectors.add(new ReflectorData("reflector_tungsten_carbide", "Tungsten Carbide Plate Reflector", 0.07, 0.02, 0.93, 0.98, 0.0, 0.10, 0,
            "plateTungstenCarbide", "ore:plateTungstenCarbide"));
        cd.reflectors.add(new ReflectorData("reflector_ic2", "Neutron Reflector", 0.01, 0.02, 0.95, 0.98, 0.20, 0.15, 10000,
            "IC2:reactorReflector", "ItemList.Neutron_Reflector", "gregtech:gt.neutronReflector"));
        cd.reflectors.add(new ReflectorData("reflector_ic2_thick", "Thick Neutron Reflector", 0.005, 0.01, 0.98, 0.99, 0.25, 0.20, 40000,
            "IC2:reactorReflectorThick", "ItemList.Neutron_Reflector_Thick"));

        // Moderators - restricted strictly to long-rod form
        cd.moderators.add(new ModeratorData("moderator_graphite", "Graphite Moderator",
            0.002, 0.009, 0.93, 0.621, 0.50, 0.05, 2500.0,
            80000, "ore:dustAsh", 0.0003, 1.0, 1500.0, 2500.0, 0.0,
            1.0, 1500.0, 2500.0, 2000.0, 10000.0,
            "stickLongGraphite", "ore:stickLongGraphite"));
        cd.moderators.add(new ModeratorData("moderator_carbon", "Carbon Moderator",
            0.003, 0.012, 0.90, 0.60, 0.45, 0.05, 2200.0,
            10000, "ore:dustAsh", 0.0005, 1.0, 1500.0, 2500.0, 0.0,
            0.0, 1500.0, 2500.0, 0.0,
            "stickLongCarbon", "ore:stickLongCarbon"));
        cd.moderators.add(new ModeratorData("moderator_beryllium", "Beryllium Moderator",
            0.001, 0.004, 0.96, 0.85, 0.65, 0.15, 2600.0,
            240000, "", 0.0002, 0.5, 1500.0, 2500.0, 0.0,
            0.0, 1500.0, 2500.0, 0.0,
            "stickLongBeryllium", "ore:stickLongBeryllium"));

        // Control Rods - explicitly configured, no wildcards
        cd.controlRods.add(new ControlRodData("rod_silver", "Silver Control Rod", 0.50, 0.20, 0.05, 0.05, 0.20,
            "stickLongSilver", "ore:stickLongSilver", "stickSilver", "ore:stickSilver", "rodSilver", "ore:rodSilver"));
        cd.controlRods.add(new ControlRodData("rod_boron", "Boron Control Rod", 0.70, 0.35, 0.05, 0.05, 0.08,
            "stickLongBoron", "ore:stickLongBoron", "stickBoron", "ore:stickBoron", "rodBoron", "ore:rodBoron"));
        cd.controlRods.add(new ControlRodData("rod_cadmium", "Cadmium Control Rod", 0.85, 0.50, 0.05, 0.05, 0.12,
            "stickLongCadmium", "ore:stickLongCadmium", "stickCadmium", "ore:stickCadmium", "rodCadmium", "ore:rodCadmium"));
        cd.controlRods.add(new ControlRodData("rod_indium", "Indium Control Rod", 0.95, 0.65, 0.05, 0.05, 0.14,
            "stickLongIndium", "ore:stickLongIndium", "stickIndium", "ore:stickIndium", "rodIndium", "ore:rodIndium"));
        cd.controlRods.add(new ControlRodData("rod_hafnium", "Hafnium Control Rod", 0.99, 0.80, 0.05, 0.05, 0.16,
            "stickLongHafnium", "ore:stickLongHafnium", "stickHafnium", "ore:stickHafnium", "rodHafnium", "ore:rodHafnium"));

        // Heat Vents - explicitly configured, moderation = 0.05
        cd.heatVents.add(new HeatVentData("vent_standard", "Reactor Heat Vent", 0.40, 6.0, 1500.0, 0.05, 128, 512.0, "IC2:reactorVent"));
        cd.heatVents.add(new HeatVentData("vent_gold", "Advanced Heat Vent", 0.45, 12.0, 1600.0, 0.05, 512, 2048.0, "IC2:reactorVentGold"));
        cd.heatVents.add(new HeatVentData("vent_spread", "Overclocked Heat Vent", 0.40, 8.0, 1500.0, 0.05, 1024, 4096.0, "IC2:reactorVentSpread"));
        cd.heatVents.add(new HeatVentData("vent_diamond", "Component Heat Vent", 0.50, 20.0, 2000.0, 0.05, 2048, 8192.0, "IC2:reactorVentDiamond"));

        // Heat Exchangers - explicitly configured, moderation = 0.05
        cd.heatExchangers.add(new HeatExchangerData("exchanger_standard", "Heat Exchanger", 0.40, 12.0, 1500.0, 0.05, 256, 1024.0, "IC2:reactorHeatSwitch"));
        cd.heatExchangers.add(new HeatExchangerData("exchanger_spread", "Advanced Heat Exchanger", 0.45, 24.0, 1600.0, 0.05, 512, 2048.0, "IC2:reactorHeatSwitchSpread"));
        cd.heatExchangers.add(new HeatExchangerData("exchanger_diamond", "Component Heat Exchanger", 0.50, 36.0, 2000.0, 0.05, 1024, 4096.0, "IC2:reactorHeatSwitchDiamond"));

        // Platings
        cd.platings.add(new PlatingData("plating_standard", "Reactor Plating", 1000.0, 0.05, 0.05, "IC2:reactorPlating"));
        cd.platings.add(new PlatingData("plating_heat", "Heat-Capacity Reactor Plating", 2000.0, 0.02, 0.08, "IC2:reactorPlatingHeat"));
        cd.platings.add(new PlatingData("plating_explosive", "Containment Reactor Plating", 500.0, 0.15, 0.05, "IC2:reactorPlatingExplosive"));

        // Insulators - explicitly configured, no wildcards
        cd.insulators.add(new InsulatorData("insulator_naquarite", "Naquarite Universal Insulator Foil", 1.0, 0.005, 1.0, 1.0, 0.0, 0.0,
            "ItemList.Naquarite_Universal_Insulator_Foil", "Naquarite_Universal_Insulator_Foil", "gregtech:gt.naquariteUniversalInsulatorFoil", "ore:foilNaquarite"));
        cd.insulators.add(new InsulatorData("insulator_mica", "Mica Insulator Foil", 0.60, 0.01, 0.01, 0.01, 0.05, 0.05,
            "dreamcraft:MicaInsulatorFoil", "MicaInsulatorFoil", "item.MicaInsulatorFoil"));
        cd.insulators.add(new InsulatorData("insulator_cloth_t2", "Thermal Cloth T2", 0.40, 0.01, 0.01, 0.01, 0.05, 0.05,
            "GalaxySpace:item.ThermalClothT2", "item.ThermalClothT2"));
        cd.insulators.add(new InsulatorData("insulator_cloth_t1", "Thermal Cloth", 0.20, 0.01, 0.01, 0.01, 0.05, 0.05,
            "GalacticraftMars:item.itemBasicAsteroids:7", "itemBasicAsteroids:7", "item.ThermalCloth", "itemThermalCloth"));

        // Radiovoltaics
        cd.radiovoltaics.add(new RadiovoltaicData("radiovoltaic_hv", "HV Radiovoltaic Plate", 1, 0.25, 0.10, 1.0, 1.0, "modularnuclear:radiovoltaicPlateHV"));
        cd.radiovoltaics.add(new RadiovoltaicData("radiovoltaic_ev", "EV Radiovoltaic Plate", 2, 0.50, 0.10, 1.0, 1.0, "modularnuclear:radiovoltaicPlateEV"));

        // Coolant Fluids - explicitly configured, no hardcoded name fallbacks
        cd.coolantFluids.add(new CoolantFluidData("coolant_water", "Water", -1, 100.0, 0.25, 4.184, 0.05, 0.10, 0.70, 0.70, 0.25, "steam", 1.0, 160, 160.0, 0.0, 100.0, "deuterium", 0.025, "water", "fluid.water"));
        cd.coolantFluids.add(new CoolantFluidData("coolant_distilled_water", "Distilled Water", 1, 100.0, 0.30, 4.184, 0.05, 0.10, 0.70, 0.70, 0.25, "ic2superheatedsteam", 1.2, 160, 160.0, 0.0, 100.0, "deuterium", 0.025, "distilledwater", "fluid.distilledwater", "ic2distilledwater", "fluid.ic2distilledwater"));
        cd.coolantFluids.add(new CoolantFluidData("coolant_heavy_water", "Heavy Water", 3, 101.4, 0.30, 4.22, 0.005, 0.01, 0.85, 0.85, 0.90, "steam", 1.5, 160, 160.0, 3.8, 101.4, "tritium", 0.010, "heavywater", "fluid.heavywater"));
        cd.coolantFluids.add(new CoolantFluidData("coolant_ic2", "IC2 Coolant", 0, 300.0, 0.50, 8.0, 0.03, 0.12, 0.45, 0.45, 0.05, "ic2hotcoolant", 2.0, 1, 320.0, -273.15, Double.POSITIVE_INFINITY, null, 0.0, "coolant", "fluid.coolant", "ic2coolant", "fluid.ic2coolant"));
        cd.coolantFluids.add(new CoolantFluidData("coolant_sodium", "Molten Sodium", 2, 883.0, 0.70, 1.28, 0.01, 0.05, 0.20, 0.20, 0.05, "molten.sodium", 3.0, 1, 480.0, 97.8, 883.0, null, 0.0, "sodium", "fluid.sodium", "molten.sodium", "fluid.molten.sodium"));
        cd.coolantFluids.add(new CoolantFluidData("coolant_lead", "Molten Lead", 3, 1749.0, 0.75, 0.16, 0.005, 0.02, 0.15, 0.15, 0.02, "molten.lead", 3.5, 1, 560.0, 327.5, 1749.0, null, 0.0, "lead", "fluid.lead", "molten.lead", "fluid.molten.lead"));

        // Coolant Items - all moderation set to 0.05, explicitly configured items
        cd.coolantItems.add(new CoolantItemData("coolant_cell_ic2", "IC2 Coolant Cell", 10000.0, 0.40, 0.02, 0.05, 0.45, 0.45, 0.05,
            "IC2:reactorCoolantSimple", "IC2:reactorCoolantTriple", "IC2:reactorCoolantSix",
            "ItemList.Reactor_Coolant_Simple", "ItemList.Reactor_Coolant_Triple", "ItemList.Reactor_Coolant_Six"));
        cd.coolantItems.add(new CoolantItemData("coolant_cell_he", "Helium Coolant Cell", 60000.0, 0.40, 0.02, 0.05, 0.45, 0.45, 0.05,
            "ItemList.Reactor_Coolant_He_1", "ItemList.Reactor_Coolant_He_3", "ItemList.Reactor_Coolant_He_6",
            "gregtech:gt.reactorCoolantHe1", "gregtech:gt.reactorCoolantHe3", "gregtech:gt.reactorCoolantHe6"));
        cd.coolantItems.add(new CoolantItemData("coolant_cell_nak", "NaK Coolant Cell", 180000.0, 0.50, 0.02, 0.05, 0.35, 0.35, 0.05,
            "ItemList.Reactor_Coolant_NaK_1", "ItemList.Reactor_Coolant_NaK_3", "ItemList.Reactor_Coolant_NaK_6",
            "gregtech:gt.reactorCoolantNaK1", "gregtech:gt.reactorCoolantNaK3", "gregtech:gt.reactorCoolantNaK6"));
        cd.coolantItems.add(new CoolantItemData("coolant_cell_sp", "Sp Coolant Cell", 360000.0, 0.60, 0.01, 0.03, 0.25, 0.25, 0.05,
            "ItemList.Reactor_Coolant_Sp_1", "ItemList.Reactor_Coolant_Sp_2", "ItemList.Reactor_Coolant_Sp_3", "ItemList.Reactor_Coolant_Sp_6",
            "gregtech:gt.reactorCoolantSp1", "gregtech:gt.reactorCoolantSp2", "gregtech:gt.reactorCoolantSp3", "gregtech:gt.reactorCoolantSp6"));
        cd.coolantItems.add(new CoolantItemData("condensator", "Condensator", 100000.0, 0.45, 0.03, 0.08, 0.40, 0.40, 0.05,
            "IC2:reactorCondensator", "IC2:reactorCondensatorLap",
            "ItemList.Reactor_Condensator", "ItemList.Reactor_Condensator_Lap"));

        return cd;
    }
}
