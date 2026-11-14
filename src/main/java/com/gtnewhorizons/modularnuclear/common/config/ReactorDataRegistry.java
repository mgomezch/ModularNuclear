package com.gtnewhorizons.modularnuclear.common.config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
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
import com.gtnewhorizons.modularnuclear.common.item.ItemCustomFuelRod;
import com.gtnewhorizons.modularnuclear.common.item.ModItems;
import com.gtnewhorizons.modularnuclear.common.nuclear.CustomFuelMix;
import com.gtnewhorizons.modularnuclear.common.util.ConfigGeneration;
import com.gtnewhorizons.modularnuclear.common.util.IdentityCache;
import com.gtnewhorizons.modularnuclear.common.util.LongKeyCache;
import com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification;

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
    // What each item kind (and fluid) is to the configuration, until it changes (see ConfigGeneration)
    private static final LongKeyCache<FuelData> fuelCache = new LongKeyCache<>();
    private static final LongKeyCache<ModeratorData> moderatorCache = new LongKeyCache<>();
    private static final LongKeyCache<ReflectorData> reflectorCache = new LongKeyCache<>();
    private static final LongKeyCache<ControlRodData> controlRodCache = new LongKeyCache<>();
    private static final LongKeyCache<InsulatorData> insulatorCache = new LongKeyCache<>();
    private static final LongKeyCache<HeatVentData> heatVentCache = new LongKeyCache<>();
    private static final LongKeyCache<HeatExchangerData> heatExchangerCache = new LongKeyCache<>();
    private static final LongKeyCache<RadiovoltaicData> radiovoltaicCache = new LongKeyCache<>();
    private static final LongKeyCache<CoolantItemData> coolantItemCache = new LongKeyCache<>();
    private static final LongKeyCache<CoolantFluidData> coolantFluidItemCache = new LongKeyCache<>();
    private static final LongKeyCache<Boolean> spentProductCache = new LongKeyCache<>();
    private static final IdentityCache<Fluid, FuelData> liquidFuelByFluid = new IdentityCache<>();
    private static final IdentityCache<Fluid, CoolantFluidData> coolantFluidByFluid = new IdentityCache<>();

    public static long getItemKey(ItemStack stack) {
        if (stack == null) return 0L;
        Item item = null;
        item = stack.getItem();

        if (item == null) return 0L;
        int id = 0;
        id = Item.getIdFromItem(item);

        if (id <= 0) return 0L;
        int damage = 0;
        damage = stack.isItemStackDamageable() ? 0 : stack.getItemDamage();

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

    /** The configuration changed: every cache built from it starts over. */
    public static synchronized void clearCaches() {
        ConfigGeneration.bump();
        NuclearFuelClassification.clearCaches();
    }

    /** The first entry whose matching patterns cover the stack, or null. */
    private static <D> D firstMatch(ItemStack stack, List<D> entries, Function<D, List<String>> matching) {
        if (entries == null) return null;
        for (D d : entries) {
            if (matchesItem(stack, matching.apply(d))) return d;
        }
        return null;
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

    /** Loads the built-in defaults and any override files in the configuration directory, which it never writes. */
    public static synchronized void init(File modConfigDir) {
        // See Note [Config files only override the built-in defaults]
        configDirectory = new File(modConfigDir, "modularnuclear");
        loadFromDir(configDirectory);
    }

    public static synchronized void loadDefaults() {
        clearCaches();
        ReactorConfigData.loadDefaults();
    }

    /** The override files, in the order they are applied. */
    public static final String[] OVERRIDE_FILES = { "physics.yaml", "tiers.yaml", "fuels.yaml", "components.yaml" };

    public static synchronized void loadFromDir(File dir) {
        if (dir == null || !dir.exists()) {
            loadFrom(name -> null, name -> name);
            return;
        }
        loadFrom(name -> readText(new File(dir, name)), name -> new File(dir, name).getPath());
    }

    /** Loads the configuration directory's override files again (after a server's configuration was in use). */
    public static synchronized void reload() {
        loadFromDir(configDirectory);
    }

    /** Loads the defaults and another machine's override files (name to YAML text), e.g. the server's. */
    public static synchronized void loadFromTexts(Map<String, String> texts) {
        loadFrom(texts::get, name -> "the server's " + name);
    }

    /** The override files of the configuration directory (name to YAML text), as {@link #loadFromTexts} takes them. */
    public static synchronized Map<String, String> overrideTexts() {
        Map<String, String> texts = new java.util.LinkedHashMap<>();
        if (configDirectory == null) return texts;
        for (String name : OVERRIDE_FILES) {
            String text = readText(new File(configDirectory, name));
            if (text != null) texts.put(name, text);
        }
        return texts;
    }

    private static void loadFrom(Function<String, String> textOf, Function<String, String> where) {
        loadDefaults();
        Yaml yaml = createYaml();
        apply(yaml, textOf, where, "physics.yaml", doc -> ReactorConfigData.applyPhysicsOverrides(asMap(doc)));
        apply(yaml, textOf, where, "tiers.yaml", ReactorConfigData::applyTierOverrides);
        apply(yaml, textOf, where, "fuels.yaml", ReactorConfigData::applyFuelOverrides);
        apply(yaml, textOf, where, "components.yaml", ReactorConfigData::applyComponentOverrides);
        clearCaches();
    }

    /** A file's text as UTF-8, or null if it does not exist. */
    private static String readText(File file) {
        if (!file.exists()) return null;
        try {
            return new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw ConfigException.inFile(file, e);
        }
    }

    /** Parses and applies one override document; any problem stops loading, naming where it came from. */
    private static void apply(Yaml yaml, Function<String, String> textOf, Function<String, String> where, String name,
        Consumer<Object> applier) {
        String text = textOf.apply(name);
        if (text == null) return;
        try {
            Object doc = yaml.load(text);
            if (doc != null) applier.accept(doc);
        } catch (RuntimeException e) {
            throw ConfigException.in(where.apply(name), e);
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

        return fuelCache.get(getItemKey(stack), stack, (s, k) -> scanFuel(s));
    }

    private static FuelData scanFuel(ItemStack stack) {
        FluidStack fs = null;
        if (ItemList.Display_Fluid.hasBeenSet()) {
            fs = GTUtility.getFluidFromDisplayStack(stack);
        }

        if (fs != null) return findFuel(fs);
        for (FuelData fd : ReactorConfigData.fuels) {
            if (!fd.isLiquid && matchesItem(stack, fd.matching)) return fd;
        }
        return null;
    }

    public static FuelData findFuel(FluidStack fluid) {
        return fluid == null ? null : findFuel(fluid.getFluid());
    }

    public static FuelData findFuel(Fluid fluid) {
        if (fluid == null) return null;
        return liquidFuelByFluid.get(fluid, f -> {
            for (FuelData fd : ReactorConfigData.fuels) {
                if (fd.isLiquid && matchesFluid(f, fd.matching)) return fd;
            }
            return null;
        });
    }

    public static FuelData findFuel(String fluidName) {
        return ReactorConfigData.findLiquidFuel(fluidName);
    }

    public static ReflectorData findReflector(ItemStack stack) {
        if (stack == null) return null;
        return reflectorCache.get(
            getItemKey(stack),
            stack,
            (s, k) -> firstMatch(s, ReactorConfigData.componentData.reflectors, d -> d.matching));
    }

    public static ModeratorData findModerator(ItemStack stack) {
        if (stack == null) return null;
        return moderatorCache.get(
            getItemKey(stack),
            stack,
            (s, k) -> firstMatch(s, ReactorConfigData.componentData.moderators, d -> d.matching));
    }

    public static ControlRodData findControlRod(ItemStack stack) {
        if (stack == null) return null;
        return controlRodCache.get(
            getItemKey(stack),
            stack,
            (s, k) -> firstMatch(s, ReactorConfigData.componentData.controlRods, d -> d.matching));
    }

    public static HeatVentData findHeatVent(ItemStack stack) {
        if (stack == null) return null;
        return heatVentCache.get(
            getItemKey(stack),
            stack,
            (s, k) -> firstMatch(s, ReactorConfigData.componentData.heatVents, d -> d.matching));
    }

    public static HeatExchangerData findHeatExchanger(ItemStack stack) {
        if (stack == null) return null;
        return heatExchangerCache.get(
            getItemKey(stack),
            stack,
            (s, k) -> firstMatch(s, ReactorConfigData.componentData.heatExchangers, d -> d.matching));
    }

    public static InsulatorData findInsulator(ItemStack stack) {
        if (stack == null) return null;
        return insulatorCache.get(
            getItemKey(stack),
            stack,
            (s, k) -> firstMatch(s, ReactorConfigData.componentData.insulators, d -> d.matching));
    }

    public static RadiovoltaicData findRadiovoltaic(ItemStack stack) {
        if (stack == null) return null;
        return radiovoltaicCache.get(
            getItemKey(stack),
            stack,
            (s, k) -> firstMatch(s, ReactorConfigData.componentData.radiovoltaics, d -> d.matching));
    }

    public static CoolantFluidData findCoolantFluid(ItemStack stack) {
        if (stack == null) return null;
        return coolantFluidItemCache.get(getItemKey(stack), stack, (s, k) -> {
            FluidStack fs = null;
            if (ItemList.Display_Fluid.hasBeenSet()) {
                fs = GTUtility.getFluidFromDisplayStack(s);
            }

            return fs != null ? findCoolantFluid(fs) : null;
        });
    }

    public static CoolantFluidData findCoolantFluid(FluidStack fluid) {
        return fluid == null ? null : findCoolantFluid(fluid.getFluid());
    }

    public static CoolantFluidData findCoolantFluid(Fluid fluid) {
        if (fluid == null) return null;
        return coolantFluidByFluid.get(fluid, f -> {
            for (CoolantFluidData d : ReactorConfigData.componentData.coolantFluids) {
                if (matchesFluid(f, d.matching)) return d;
            }
            return null;
        });
    }

    public static CoolantFluidData findCoolantFluid(String fluidName) {
        return ReactorConfigData.findCoolantFluid(fluidName);
    }

    public static CoolantItemData findCoolantItem(ItemStack stack) {
        if (stack == null) return null;
        return coolantItemCache.get(
            getItemKey(stack),
            stack,
            (s, k) -> firstMatch(s, ReactorConfigData.componentData.coolantItems, d -> d.matching));
    }

    public static boolean isSpentProduct(ItemStack stack) {
        if (stack == null) return false;
        return spentProductCache.get(getItemKey(stack), stack, (s, k) -> {
            for (FuelData fd : ReactorConfigData.fuels) {
                if (fd.spentProduct != null && !fd.spentProduct.isEmpty()
                    && matchesItem(s, Collections.singletonList(fd.spentProduct))) return Boolean.TRUE;
            }
            return Boolean.FALSE;
        });
    }

    public static boolean isSpentProduct(String name) {
        if (name == null || name.isEmpty()) return false;
        for (FuelData fd : ReactorConfigData.fuels) {
            if (fd.spentProduct != null && !fd.spentProduct.isEmpty()) {
                if (fd.spentProduct.equals(name)) return true;
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
