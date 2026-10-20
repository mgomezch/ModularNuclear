package com.gtnewhorizons.modularnuclear.common.config;

import java.io.File;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import com.gtnewhorizons.modularnuclear.ModularNuclear;
import com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification;

import cpw.mods.fml.common.registry.GameRegistry;

public final class ModularNuclearConfig {

    private ModularNuclearConfig() {}

    // ==========================================
    // Client Visuals
    // ==========================================
    public static boolean enableCherenkovRadiation = true;
    public static boolean enableCherenkovLightEmission = true;
    public static int cherenkovLightLevel = 10;
    public static float cherenkovIntensityMultiplier = 6.0f;
    public static boolean enableFalloutVisualEffect = true;
    public static float falloutEffectMaxDistance = 80.0f;

    // ==========================================
    // Meltdown Fallout Dynamics
    // ==========================================
    public static double falloutContaminationReductionPerPollution = 62.5 / 30000.0;

    // ==========================================
    // Allowlists & Blocklists (Core Components)
    // ==========================================
    public static String[] blockedItems = new String[] { "*depleted*", "*fuelrodempty*", "IC2:itemFuelRod",
        "modularnuclear:rodCustomDepletedFuel1", "modularnuclear:rodCustomDepletedFuel2",
        "modularnuclear:rodCustomDepletedFuel4" };

    public static String[] allowedFuelItems = new String[] { "IC2:reactorUraniumSimple", "IC2:reactorMOXSimple",
        "IC2:reactorLithiumCell", "gregtech:gt.reactorUranium*", "gregtech:gt.reactorMOX*",
        "gregtech:gt.reactorThorium*", "gregtech:gt.reactorNaquadah*", "modularnuclear:rodCustomFuel1",
        "modularnuclear:rodCustomFuel2", "modularnuclear:rodCustomFuel4" };

    public static String[] allowedFuelFluids = new String[] { "uraniumbasedliquidfuel", "thoriumbasedliquidfuel",
        "plutoniumbasedliquidfuel", "uraniumhexafluoride" };

    public static String[] blockedFuelFluids = new String[] { "naquadah" };

    public static String[] allowedCoolantItems = new String[] { "IC2:reactorCoolantSimple", "IC2:reactorCoolantTriple",
        "IC2:reactorCoolantSix", "gregtech:gt.reactorCoolant*" };

    public static String[] allowedCoolantFluids = new String[] { "ic2coolant", "heavywater", "distilledwater", "sodium",
        "lead", "molten.sodium", "molten.lead" };

    public static String[] allowedControlRods = new String[] { "stickLongSilver=SILVER", "stickLongBoron=BORON",
        "stickLongCadmium=CADMIUM", "stickLongIndium=INDIUM", "stickLongHafnium=HAFNIUM" };

    public static String[] allowedReflectors = new String[] { "plateGold", "plateStainlessSteel",
        "plateTungstenCarbide", "IC2:reactorReflector", "IC2:reactorReflectorThick", "gregtech:gt.neutronReflector" };

    public static String[] allowedModerators = new String[] { "stickLongGraphite", "stickLongCarbon",
        "stickLongBeryllium" };

    public static String[] allowedHeatVents = new String[] { "IC2:reactorVent*" };

    public static String[] allowedHeatExchangers = new String[] { "IC2:reactorHeatSwitch*" };

    public static String[] allowedPlatings = new String[0];

    public static String[] allowedInsulators = new String[] { "Naquarite_Universal_Insulator_Foil=1.0",
        "MicaInsulatorFoil=0.60", "ThermalClothT2=0.40", "ThermalCloth=0.20", "itemBasicAsteroids:7=0.20" };

    public static String[] allowedRadiovoltaics = new String[] { "modularnuclear:radiovoltaicPlateHV",
        "modularnuclear:radiovoltaicPlateEV" };

    public static String[] allowedNeutronSources = new String[] { "GoodGenerator:neutronSource", "item.neutronSource" };

    // ==========================================
    // Reactor Tiers & Material Limits
    // ==========================================
    public static final double[] DEFAULT_MAX_OPERATING_TEMP_TIER = new double[] { 1200.0, 1400.0, 1800.0, 2200.0,
        2600.0, 3200.0 };
    public static double[] maxOperatingTempTier = new double[] { 1200.0, // Tier 0: Electrum (EV)
        1400.0, // Tier 1: Platinum (IV)
        1800.0, // Tier 2: Osmium (LuV)
        2200.0, // Tier 3: Quantium (ZPM)
        2600.0, // Tier 4: Fluxed Electrum (UV)
        3200.0 // Tier 5: Black Plutonium (UHV)
    };
    public static double repairTempThresholdRatio = 0.10;
    public static double coolantBoilingThresholdHeavyWater = 101.4;
    public static double coolantBoilingThresholdDistilledWater = 100.0;

    public static void init(File configFile) {
        Configuration config = new Configuration(configFile);
        try {
            config.load();

            // Client Visuals
            enableCherenkovRadiation = config.getBoolean(
                "enableCherenkovRadiation",
                "client_visuals",
                enableCherenkovRadiation,
                "Enable client-side Cherenkov radiation visual effects (blue beams and particles) above flooded reactors.");

            enableCherenkovLightEmission = config.getBoolean(
                "enableCherenkovLightEmission",
                "client_visuals",
                enableCherenkovLightEmission,
                "Enable dynamic client block light emission from hatches when Cherenkov radiation is active.");

            cherenkovLightLevel = config.getInt(
                "cherenkovLightLevel",
                "client_visuals",
                cherenkovLightLevel,
                1,
                15,
                "Block light level emitted by reactor core hatches when Cherenkov radiation is active (default 10).");

            cherenkovIntensityMultiplier = config.getFloat(
                "cherenkovIntensityMultiplier",
                "client_visuals",
                cherenkovIntensityMultiplier,
                0.1f,
                10.0f,
                "Multiplier for Cherenkov visual beam intensity (default 6.0).");

            enableFalloutVisualEffect = config.getBoolean(
                "enableFalloutVisualEffect",
                "client_visuals",
                enableFalloutVisualEffect,
                "Enable the post-processing shader visual effect (static, desaturation, darkening) during meltdown fallout.");

            falloutEffectMaxDistance = config.getFloat(
                "falloutEffectMaxDistance",
                "client_visuals",
                falloutEffectMaxDistance,
                16.0f,
                160.0f,
                "Maximum distance in meters where the meltdown fallout visual effect is visible (default 80).");

            // Meltdown Fallout Dynamics
            final String CAT_FALLOUT = "meltdown_fallout";
            config.setCategoryComment(CAT_FALLOUT, "Meltdown fallout contamination and pollution mitigation tuning.");

            falloutContaminationReductionPerPollution = config.get(
                CAT_FALLOUT,
                "falloutContaminationReductionPerPollution",
                falloutContaminationReductionPerPollution,
                "Contamination counter reduction per unit of pollution removed by scrubbers or mufflers (default: 1/480, reducing fallout radius by 1m per 30,000 pollution removed).")
                .getDouble();

            // Allowlists & Blocklists
            final String CAT_ALLOWLISTS = "allowlists";
            config.setCategoryComment(
                CAT_ALLOWLISTS,
                "Data-driven item and fluid allowlists/blocklists for the reactor core.");

            blockedItems = config.getStringList(
                "blockedItems",
                CAT_ALLOWLISTS,
                blockedItems,
                "Items and wildcard patterns strictly blocked from insertion into reactor core hatches (depleted rods, empty rods, etc.).");

            allowedFuelItems = config.getStringList(
                "allowedFuelItems",
                CAT_ALLOWLISTS,
                allowedFuelItems,
                "Items and wildcard patterns recognized as active nuclear fuel in reactor core hatches.");

            allowedFuelFluids = config.getStringList(
                "allowedFuelFluids",
                CAT_ALLOWLISTS,
                allowedFuelFluids,
                "Fluid names recognized as nuclear fuels for fluid-cooled reactor cores.");

            blockedFuelFluids = config.getStringList(
                "blockedFuelFluids",
                CAT_ALLOWLISTS,
                blockedFuelFluids,
                "Fluid names explicitly blocked from reactor fuel hatches (e.g. naquadah).");

            allowedCoolantItems = config.getStringList(
                "allowedCoolantItems",
                CAT_ALLOWLISTS,
                allowedCoolantItems,
                "Items and wildcard patterns recognized as coolant cells in reactor core hatches.");

            allowedCoolantFluids = config.getStringList(
                "allowedCoolantFluids",
                CAT_ALLOWLISTS,
                allowedCoolantFluids,
                "Fluid names recognized as valid reactor coolants.");

            allowedControlRods = config.getStringList(
                "allowedControlRods",
                CAT_ALLOWLISTS,
                allowedControlRods,
                "Items and oreDict entries mapped to control rod types (item=TYPE).");

            allowedReflectors = config.getStringList(
                "allowedReflectors",
                CAT_ALLOWLISTS,
                allowedReflectors,
                "Items and oreDict entries recognized as neutron reflectors.");

            allowedModerators = config.getStringList(
                "allowedModerators",
                CAT_ALLOWLISTS,
                allowedModerators,
                "Items and oreDict entries recognized as neutron moderators.");

            allowedHeatVents = config
                .getStringList("allowedHeatVents", CAT_ALLOWLISTS, allowedHeatVents, "Items recognized as heat vents.");

            allowedHeatExchangers = config.getStringList(
                "allowedHeatExchangers",
                CAT_ALLOWLISTS,
                allowedHeatExchangers,
                "Items recognized as heat exchangers.");

            allowedPlatings = config.getStringList(
                "allowedPlatings",
                CAT_ALLOWLISTS,
                allowedPlatings,
                "Items recognized as reactor platings.");

            allowedInsulators = config.getStringList(
                "allowedInsulators",
                CAT_ALLOWLISTS,
                allowedInsulators,
                "Items and oreDict entries mapped to insulation dampening factor (item=fraction).");

            allowedRadiovoltaics = config.getStringList(
                "allowedRadiovoltaics",
                CAT_ALLOWLISTS,
                allowedRadiovoltaics,
                "Items recognized as radiovoltaic energy converters.");

            allowedNeutronSources = config.getStringList(
                "allowedNeutronSources",
                CAT_ALLOWLISTS,
                allowedNeutronSources,
                "Items recognized as external beam neutron sources.");

            // Reactor Tiers & Material Limits
            final String CAT_TIERS = "reactor_tiers";
            config.setCategoryComment(
                CAT_TIERS,
                "Maximum operating temperatures and safety thresholds per reactor pipe tier.");

            for (int t = 0; t < maxOperatingTempTier.length; t++) {
                maxOperatingTempTier[t] = config.getFloat(
                    "maxOperatingTempTier" + t,
                    CAT_TIERS,
                    (float) maxOperatingTempTier[t],
                    100.0f,
                    10000.0f,
                    "Maximum operating temperature (K) for pipe tier " + t);
            }

            repairTempThresholdRatio = config.getFloat(
                "repairTempThresholdRatio",
                CAT_TIERS,
                (float) repairTempThresholdRatio,
                0.01f,
                0.50f,
                "Ratio of max operating temperature below which structural damage repairs can proceed (default 0.10 = 10%).");

            coolantBoilingThresholdHeavyWater = config.getFloat(
                "coolantBoilingThresholdHeavyWater",
                CAT_TIERS,
                (float) coolantBoilingThresholdHeavyWater,
                50.0f,
                500.0f,
                "Atmospheric boiling threshold (C) for heavy water coolant.");

            coolantBoilingThresholdDistilledWater = config.getFloat(
                "coolantBoilingThresholdDistilledWater",
                CAT_TIERS,
                (float) coolantBoilingThresholdDistilledWater,
                50.0f,
                500.0f,
                "Atmospheric boiling threshold (C) for distilled water coolant.");

        } catch (Exception e) {
            ModularNuclear.LOG.error("Failed to load ModularNuclear config", e);
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }

    // ==========================================
    // Matching and Registry Lookup Helpers
    // ==========================================

    public static boolean matchesPattern(String text, String pattern) {
        if (text == null || pattern == null) return false;
        String t = text.trim()
            .toLowerCase();
        String p = pattern.trim()
            .toLowerCase();
        if (p.equals("*") || p.equals(t)) return true;

        if (p.startsWith("*") && p.endsWith("*")) {
            String sub = p.substring(1, p.length() - 1);
            return t.contains(sub);
        }
        if (p.startsWith("*")) {
            return t.endsWith(p.substring(1));
        }
        if (p.endsWith("*")) {
            return t.startsWith(p.substring(0, p.length() - 1));
        }
        return p.equals(t);
    }

    public static boolean matchesItem(ItemStack stack, String[] list) {
        if (stack == null || list == null || list.length == 0) return false;

        Item item = stack.getItem();
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

        for (String entry : list) {
            if (entry == null || entry.isEmpty()) continue;
            String pattern = entry.trim();

            // 1. OreDict prefix check (e.g. "ore:blockGraphite" or direct "blockGraphite")
            String oreTarget = pattern.startsWith("ore:") ? pattern.substring(4) : pattern;
            if (oreIDs != null && oreIDs.length > 0) {
                for (int id : oreIDs) {
                    String oreName = OreDictionary.getOreName(id);
                    if (oreName != null && oreName.equalsIgnoreCase(oreTarget)) {
                        return true;
                    }
                }
            }

            // 2. Registry name check ("modid:item_name" or "modid:item_name:damage")
            if (regName != null) {
                if (matchesPattern(regName, pattern)) return true;
                if (matchesPattern(regName + ":" + stack.getItemDamage(), pattern)) return true;
            }

            // 3. Unlocalized name wildcard check
            if (unloc != null) {
                if (matchesPattern(unloc, pattern)) return true;
                if (unloc.startsWith("item.") && matchesPattern(unloc.substring(5), pattern)) return true;
                if (unloc.startsWith("tile.") && matchesPattern(unloc.substring(5), pattern)) return true;
            }
        }

        return false;
    }

    public static boolean matchesFluid(FluidStack fluid, String[] list) {
        if (fluid == null || fluid.getFluid() == null || list == null || list.length == 0) return false;
        String name = fluid.getFluid()
            .getName();
        String unloc = fluid.getFluid()
            .getUnlocalizedName();

        for (String entry : list) {
            if (entry == null || entry.isEmpty()) continue;
            if (matchesPattern(name, entry)) return true;
            if (unloc != null && matchesPattern(unloc, entry)) return true;
        }
        return false;
    }

    public static boolean isItemBlocked(ItemStack stack) {
        return matchesItem(stack, blockedItems);
    }

    public static boolean isItemFuel(ItemStack stack) {
        return matchesItem(stack, allowedFuelItems);
    }

    public static boolean isFluidFuel(FluidStack fluid) {
        if (matchesFluid(fluid, blockedFuelFluids)) return false;
        return matchesFluid(fluid, allowedFuelFluids);
    }

    public static boolean isFluidBlocked(FluidStack fluid) {
        return matchesFluid(fluid, blockedFuelFluids);
    }

    public static boolean isItemCoolant(ItemStack stack) {
        return matchesItem(stack, allowedCoolantItems);
    }

    public static boolean isCoolantFluid(FluidStack fluid) {
        return matchesFluid(fluid, allowedCoolantFluids);
    }

    public static boolean isItemReflector(ItemStack stack) {
        return matchesItem(stack, allowedReflectors);
    }

    public static boolean isItemModerator(ItemStack stack) {
        return matchesItem(stack, allowedModerators);
    }

    public static boolean isItemHeatVent(ItemStack stack) {
        return matchesItem(stack, allowedHeatVents);
    }

    public static boolean isItemHeatExchanger(ItemStack stack) {
        return matchesItem(stack, allowedHeatExchangers);
    }

    public static boolean isItemReactorPlating(ItemStack stack) {
        return false;
    }

    public static boolean isItemRadiovoltaic(ItemStack stack) {
        return matchesItem(stack, allowedRadiovoltaics);
    }

    public static boolean isItemNeutronSource(ItemStack stack) {
        if (stack == null) return false;
        return matchesItem(stack, allowedNeutronSources) || NuclearFuelClassification.isItemNeutronSource(stack);
    }

    public static double getInsulatorDampening(ItemStack stack) {
        if (stack == null) return 0.0;
        for (String entry : allowedInsulators) {
            if (entry == null || !entry.contains("=")) continue;
            String[] parts = entry.split("=", 2);
            String itemPattern = parts[0].trim();
            try {
                double val = Double.parseDouble(parts[1].trim());
                if (matchesItem(stack, new String[] { itemPattern })) {
                    return val;
                }
            } catch (Exception ignored) {}
        }
        return 0.0;
    }

    public static String getControlRodType(ItemStack stack) {
        if (stack == null) return null;
        for (String entry : allowedControlRods) {
            if (entry == null || !entry.contains("=")) continue;
            String[] parts = entry.split("=", 2);
            String itemPattern = parts[0].trim();
            String rodType = parts[1].trim();
            if (matchesItem(stack, new String[] { itemPattern })) {
                return rodType;
            }
        }
        return null;
    }
}
