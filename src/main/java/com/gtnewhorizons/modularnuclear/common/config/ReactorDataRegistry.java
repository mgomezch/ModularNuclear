package com.gtnewhorizons.modularnuclear.common.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

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
import com.gtnewhorizons.modularnuclear.common.item.ItemCustomFuelRod;
import com.gtnewhorizons.modularnuclear.common.item.ModItems;
import com.gtnewhorizons.modularnuclear.common.nuclear.CustomFuelMix;
import com.gtnewhorizons.modularnuclear.common.util.LongKeyMap;
import com.gtnewhorizons.modularnuclear.common.util.Swallowed;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.ItemList;
import gregtech.api.util.GTUtility;

/**
 * Data-driven registry managing YAML-configured reactor physics, multiblock tiers,
 * fuels, and non-fuel internal reactor components.
 */
public final class ReactorDataRegistry {

    private static File configDirectory;

    // Fast item-level lookup caches for WAILA and client tick responsiveness
    private static final LongKeyMap<FuelData> fuelCache = new LongKeyMap<>();
    private static final LongKeyMap<ModeratorData> moderatorCache = new LongKeyMap<>();
    private static final LongKeyMap<ReflectorData> reflectorCache = new LongKeyMap<>();
    private static final LongKeyMap<ControlRodData> controlRodCache = new LongKeyMap<>();
    private static final LongKeyMap<InsulatorData> insulatorCache = new LongKeyMap<>();
    private static final LongKeyMap<HeatVentData> heatVentCache = new LongKeyMap<>();
    private static final LongKeyMap<HeatExchangerData> heatExchangerCache = new LongKeyMap<>();
    private static final LongKeyMap<RadiovoltaicData> radiovoltaicCache = new LongKeyMap<>();
    private static final LongKeyMap<CoolantItemData> coolantItemCache = new LongKeyMap<>();
    private static final LongKeyMap<CoolantFluidData> coolantFluidItemCache = new LongKeyMap<>();
    private static final LongKeyMap<Boolean> spentProductCache = new LongKeyMap<>();
    /** Fluid lookups match names against every entry's patterns, so they are kept per fluid like item lookups. */
    private static final Map<net.minecraftforge.fluids.Fluid, Object> liquidFuelByFluid = new ConcurrentHashMap<>();
    private static final Map<net.minecraftforge.fluids.Fluid, Object> coolantFluidByFluid = new ConcurrentHashMap<>();
    private static final Object NO_FLUID_MATCH = new Object();

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
        } catch (Throwable swallowed) {
            Swallowed.note(swallowed);
        }
        if (item == null) return 0L;
        int id = 0;
        try {
            id = Item.getIdFromItem(item);
        } catch (Throwable swallowed) {
            Swallowed.note(swallowed);
        }
        if (id <= 0) return 0L;
        int damage = 0;
        try {
            damage = stack.isItemStackDamageable() ? 0 : stack.getItemDamage();
        } catch (Throwable swallowed) {
            Swallowed.note(swallowed);
        }
        if (damage == OreDictionary.WILDCARD_VALUE) {
            damage = 0;
        }
        return (((long) id) << 32) | (damage & 0xFFFFFFFFL);
    }

    /**
     * Whether the stack is the given GregTech item (any wear, for a damageable item). {@link ItemList#isStackEqual}
     * compares ore dictionary unifications instead, under which a fuel rod can equal its own depleted rod.
     */
    public static boolean isItemListStack(ItemList item, ItemStack stack) {
        if (item == null || stack == null || !item.hasBeenSet()) return false;
        long key = getItemKey(stack);
        return key != 0L && key == getItemKey(item.get(1L));
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
        liquidFuelByFluid.clear();
        coolantFluidByFluid.clear();
        try {
            Class.forName("net.minecraft.item.Item");
            Class<?> clazz = Class.forName("com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification");
            clazz.getMethod("clearCaches")
                .invoke(null);
        } catch (Throwable swallowed) {
            Swallowed.note(swallowed);
            // Standalone CLI or WebAssembly environment without Minecraft classes
        }
    }

    public static synchronized void preIndexKnownItems() {
        if (ReactorConfigData.fuels != null) {
            for (FuelData fd : ReactorConfigData.fuels) {
                if (fd.isLiquid || fd.matching == null) continue;
                for (String pattern : fd.matching) {
                    indexPattern(pattern, (key) -> fuelCache.put(key, fd));
                }
                if (fd.spentProduct != null && !fd.spentProduct.isEmpty()) {
                    indexPattern(fd.spentProduct, (key) -> spentProductCache.put(key, Boolean.TRUE));
                }
            }
        }
        if (ReactorConfigData.componentData != null) {
            if (ReactorConfigData.componentData.reflectors != null) {
                for (ReflectorData d : ReactorConfigData.componentData.reflectors) {
                    if (d.matching == null) continue;
                    for (String p : d.matching) {
                        indexPattern(p, (key) -> reflectorCache.put(key, d));
                    }
                }
            }
            if (ReactorConfigData.componentData.moderators != null) {
                for (ModeratorData d : ReactorConfigData.componentData.moderators) {
                    if (d.matching == null) continue;
                    for (String p : d.matching) {
                        indexPattern(p, (key) -> moderatorCache.put(key, d));
                    }
                }
            }
            if (ReactorConfigData.componentData.controlRods != null) {
                for (ControlRodData d : ReactorConfigData.componentData.controlRods) {
                    if (d.matching == null) continue;
                    for (String p : d.matching) {
                        indexPattern(p, (key) -> controlRodCache.put(key, d));
                    }
                }
            }
            if (ReactorConfigData.componentData.heatVents != null) {
                for (HeatVentData d : ReactorConfigData.componentData.heatVents) {
                    if (d.matching == null) continue;
                    for (String p : d.matching) {
                        indexPattern(p, (key) -> heatVentCache.put(key, d));
                    }
                }
            }
            if (ReactorConfigData.componentData.heatExchangers != null) {
                for (HeatExchangerData d : ReactorConfigData.componentData.heatExchangers) {
                    if (d.matching == null) continue;
                    for (String p : d.matching) {
                        indexPattern(p, (key) -> heatExchangerCache.put(key, d));
                    }
                }
            }
            if (ReactorConfigData.componentData.insulators != null) {
                for (InsulatorData d : ReactorConfigData.componentData.insulators) {
                    if (d.matching == null) continue;
                    for (String p : d.matching) {
                        indexPattern(p, (key) -> insulatorCache.put(key, d));
                    }
                }
            }
            if (ReactorConfigData.componentData.radiovoltaics != null) {
                for (RadiovoltaicData d : ReactorConfigData.componentData.radiovoltaics) {
                    if (d.matching == null) continue;
                    for (String p : d.matching) {
                        indexPattern(p, (key) -> radiovoltaicCache.put(key, d));
                    }
                }
            }
            if (ReactorConfigData.componentData.coolantItems != null) {
                for (CoolantItemData d : ReactorConfigData.componentData.coolantItems) {
                    if (d.matching == null) continue;
                    for (String p : d.matching) {
                        indexPattern(p, (key) -> coolantItemCache.put(key, d));
                    }
                }
            }
        }
    }

    private static void indexPattern(String pattern, java.util.function.LongConsumer consumer) {
        if (pattern == null || pattern.isEmpty()) return;
        try {
            String oreTarget = pattern.startsWith(ORE_PREFIX) ? pattern.substring(ORE_PREFIX.length()) : null;
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
        } catch (Throwable swallowed) {
            Swallowed.note(swallowed);
        }
    }

    static {
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
        try (Writer w = new OutputStreamWriter(new FileOutputStream(readmeFile), StandardCharsets.UTF_8)) {
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
        ReactorConfigData.loadDefaults();
    }

    public static synchronized void loadFromDir(File dir) {
        loadDefaults();
        if (dir == null || !dir.exists()) return;

        Yaml yaml = createYaml();

        apply(yaml, new File(dir, "physics.yaml"), doc -> ReactorConfigData.applyPhysicsOverrides(asMap(doc)));
        apply(yaml, new File(dir, "tiers.yaml"), ReactorConfigData::applyTierOverrides);
        apply(yaml, new File(dir, "fuels.yaml"), ReactorConfigData::applyFuelOverrides);
        apply(yaml, new File(dir, "components.yaml"), ReactorConfigData::applyComponentOverrides);

        clearCaches();
    }

    /** Reads one override file as UTF-8, or null if it does not exist. Any problem stops loading. */
    private static Object load(Yaml yaml, File file) {
        if (!file.exists()) return null;
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            return yaml.load(r);
        } catch (Exception e) {
            throw ConfigException.inFile(file, e);
        }
    }

    private static void apply(Yaml yaml, File file, Consumer<Object> applier) {
        Object doc = load(yaml, file);
        if (doc == null) return;
        try {
            applier.accept(doc);
        } catch (RuntimeException e) {
            throw ConfigException.inFile(file, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object doc) {
        if (doc instanceof Map<?, ?>m) return (Map<String, Object>) m;
        throw new ConfigException("expected a map of settings, got " + doc);
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
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("physics", ReactorConfigData.physicsData.toMap());
            yaml.dump(root, w);
        } catch (Exception e) {
            ModularNuclear.LOG.error("Failed to save default physics.yaml", e);
        }
    }

    private static void saveTiersFile(File file, Yaml yaml) {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (TierData td : ReactorConfigData.tierDataMap.values()) {
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
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (FuelData fd : ReactorConfigData.fuels) {
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
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            yaml.dump(ReactorConfigData.componentData.toMap(), w);
        } catch (Exception e) {
            ModularNuclear.LOG.error("Failed to save default components.yaml", e);
        }
    }

    // =========================================================================
    // Getters & Lookups
    // =========================================================================

    public static PhysicsData getPhysics() {
        return ReactorConfigData.getPhysics();
    }

    public static TierData getTier(int tier) {
        return ReactorConfigData.getTier(tier);
    }

    public static boolean hasTier(int tier) {
        return ReactorConfigData.hasTier(tier);
    }

    public static List<TierData> getAllTiers() {
        return ReactorConfigData.getAllTiers();
    }

    public static List<FuelData> getAllFuels() {
        return ReactorConfigData.getAllFuels();
    }

    public static ComponentData getComponentData() {
        return ReactorConfigData.getComponentData();
    }

    public static synchronized void syncPhysicsToEngine() {
        ReactorConfigData.syncPhysicsToEngine();
    }

    public static FuelData findFuel(ItemStack stack) {
        if (stack == null) return null;

        if (CustomFuelMix.hasCustomMix(stack)) {
            CustomFuelMix mix = CustomFuelMix.fromStack(stack);
            if (mix != null) {
                int rodSize = 4;
                if (stack.getItem() instanceof ItemCustomFuelRod customRod) {
                    rodSize = customRod.rodSize;
                }
                return mix.asFuelData(rodSize);
            }
        }

        // Uncalibrated / empty-NBT custom fuel rods are not active fuels
        if (stack.getItem() instanceof ItemCustomFuelRod
            || (ModItems.rodCustomFuel1 != null && stack.getItem() == ModItems.rodCustomFuel1)
            || (ModItems.rodCustomFuel2 != null && stack.getItem() == ModItems.rodCustomFuel2)
            || (ModItems.rodCustomFuel4 != null && stack.getItem() == ModItems.rodCustomFuel4)) {
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
        } catch (Throwable swallowed) {
            Swallowed.note(swallowed);
        }
        if (fs != null) {
            FuelData result = findFuel(fs);
            if (key != 0L) fuelCache.put(key, result != null ? result : NO_FUEL);
            return result;
        }

        for (FuelData fd : ReactorConfigData.fuels) {
            if (!fd.isLiquid && matchesItem(stack, fd.matching)) {
                if (key != 0L) fuelCache.put(key, fd);
                return fd;
            }
        }
        if (key != 0L) fuelCache.put(key, NO_FUEL);
        return null;
    }

    public static FuelData findFuel(FluidStack fluid) {
        return fluid == null ? null : findFuel(fluid.getFluid());
    }

    public static FuelData findFuel(Fluid fluid) {
        if (fluid == null) return null;
        Object hit = liquidFuelByFluid.get(fluid);
        if (hit == null) {
            hit = NO_FLUID_MATCH;
            for (FuelData fd : ReactorConfigData.fuels) {
                if (fd.isLiquid && matchesFluid(fluid, fd.matching)) {
                    hit = fd;
                    break;
                }
            }
            liquidFuelByFluid.put(fluid, hit);
        }
        return hit == NO_FLUID_MATCH ? null : (FuelData) hit;
    }

    public static FuelData findFuel(String fluidName) {
        return ReactorConfigData.findLiquidFuel(fluidName);
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
        for (ReflectorData d : ReactorConfigData.componentData.reflectors) {
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
        for (ModeratorData d : ReactorConfigData.componentData.moderators) {
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
        for (ControlRodData d : ReactorConfigData.componentData.controlRods) {
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
        for (HeatVentData d : ReactorConfigData.componentData.heatVents) {
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
        for (HeatExchangerData d : ReactorConfigData.componentData.heatExchangers) {
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
        for (InsulatorData d : ReactorConfigData.componentData.insulators) {
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
        for (RadiovoltaicData d : ReactorConfigData.componentData.radiovoltaics) {
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
        } catch (Throwable swallowed) {
            Swallowed.note(swallowed);
        }
        if (fs != null) {
            CoolantFluidData res = findCoolantFluid(fs);
            if (key != 0L) coolantFluidItemCache.put(key, res != null ? res : NO_COOLANT_FLUID);
            return res;
        }
        if (key != 0L) coolantFluidItemCache.put(key, NO_COOLANT_FLUID);
        return null;
    }

    public static CoolantFluidData findCoolantFluid(FluidStack fluid) {
        return fluid == null ? null : findCoolantFluid(fluid.getFluid());
    }

    public static CoolantFluidData findCoolantFluid(Fluid fluid) {
        if (fluid == null) return null;
        Object hit = coolantFluidByFluid.get(fluid);
        if (hit == null) {
            hit = NO_FLUID_MATCH;
            for (CoolantFluidData d : ReactorConfigData.componentData.coolantFluids) {
                if (matchesFluid(fluid, d.matching)) {
                    hit = d;
                    break;
                }
            }
            coolantFluidByFluid.put(fluid, hit);
        }
        return hit == NO_FLUID_MATCH ? null : (CoolantFluidData) hit;
    }

    public static CoolantFluidData findCoolantFluid(String fluidName) {
        return ReactorConfigData.findCoolantFluid(fluidName);
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
        for (CoolantItemData d : ReactorConfigData.componentData.coolantItems) {
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
        for (FuelData fd : ReactorConfigData.fuels) {
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
        for (FuelData fd : ReactorConfigData.fuels) {
            if (fd.spentProduct != null && !fd.spentProduct.isEmpty()) {
                if (fd.spentProduct.equalsIgnoreCase(name) || ("fluid." + fd.spentProduct).equalsIgnoreCase(name))
                    return true;
            }
        }
        return false;
    }

    /**
     * The item or fluid an exact config identifier names: {@code modid:name} or {@code modid:name:meta} (an item),
     * {@code ore:name} (the first item of that ore dictionary name), or a fluid's registry name (its display stack).
     * Null if it names nothing.
     */
    public static ItemStack resolveItemStack(String identifier) {
        if (identifier == null || identifier.isEmpty()) return null;
        if (identifier.startsWith(ORE_PREFIX)) {
            List<ItemStack> ores = OreDictionary.getOres(identifier.substring(ORE_PREFIX.length()));
            if (ores.isEmpty()) return null;
            ItemStack is = ores.get(0)
                .copy();
            is.stackSize = 1;
            return is;
        }
        String[] parts = identifier.split(":", -1);
        if (parts.length == 2 || parts.length == 3) {
            Item it = GameRegistry.findItem(parts[0], parts[1]);
            if (it == null) return null;
            int meta = parts.length == 3 ? parseMeta(parts[2]) : 0;
            return meta < 0 ? null : new ItemStack(it, 1, meta);
        }
        Fluid f = findFluid(identifier);
        if (f != null && ItemList.Display_Fluid.hasBeenSet()) {
            return GTUtility.getFluidDisplayStack(new FluidStack(f, 1000), false);
        }
        return null;
    }

    private static int parseMeta(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // =========================================================================
    // Pattern Matching Helpers
    // =========================================================================

    /** The prefix of an ore dictionary name in config. */
    public static final String ORE_PREFIX = "ore:";

    /**
     * Whether a config item identifier names this stack, exactly: {@code ore:name} (the stack has that ore dictionary
     * name), {@code modid:name} (that item, any meta) or {@code modid:name:meta}. Names are case-sensitive, as the
     * registries are.
     */
    public static boolean matchesItem(ItemStack stack, String identifier) {
        if (stack == null || identifier == null || stack.getItem() == null) return false;
        if (identifier.startsWith(ORE_PREFIX)) {
            String ore = identifier.substring(ORE_PREFIX.length());
            for (int id : OreDictionary.getOreIDs(stack)) {
                if (ore.equals(OreDictionary.getOreName(id))) return true;
            }
            return false;
        }
        int colon = identifier.indexOf(':');
        if (colon < 0) return false;
        int metaColon = identifier.indexOf(':', colon + 1);
        // "modid:name", or null for an item that is not registered
        String registryName = Item.itemRegistry.getNameForObject(stack.getItem());
        if (registryName == null) return false;
        if (metaColon < 0) return identifier.equals(registryName);
        return identifier.startsWith(registryName) && metaColon == registryName.length()
            && parseMeta(identifier.substring(metaColon + 1)) == stack.getItemDamage();
    }

    public static boolean matchesItem(ItemStack stack, List<String> identifiers) {
        if (stack == null || identifiers == null) return false;
        for (String id : identifiers) {
            if (matchesItem(stack, id)) return true;
        }
        return false;
    }

    /** The fluid with exactly this registry name, or null. */
    public static Fluid findFluid(String name) {
        return name == null ? null : FluidRegistry.getFluid(name);
    }

    public static boolean matchesFluid(FluidStack fluid, List<String> names) {
        return fluid != null && matchesFluid(fluid.getFluid(), names);
    }

    /** Whether one of the names is exactly this fluid's registry name. */
    public static boolean matchesFluid(Fluid fluid, List<String> names) {
        return fluid != null && matchesFluid(fluid.getName(), names);
    }

    public static boolean matchesFluid(String fluidName, List<String> names) {
        return ReactorConfigData.matchesFluid(fluidName, names);
    }
}
