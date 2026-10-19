package com.gtnewhorizons.modularnuclear.common.config;

import java.io.File;
import java.util.regex.Pattern;

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
    public static float cherenkovIntensityMultiplier = 2.5f;

    // ==========================================
    // Allowlists & Blocklists (Core Components)
    // ==========================================
    public static String[] blockedItems = new String[] {
        "*depleted*",
        "*fuelrodempty*",
        "IC2:itemFuelRod",
        "modularnuclear:rodCustomDepletedFuel1",
        "modularnuclear:rodCustomDepletedFuel2",
        "modularnuclear:rodCustomDepletedFuel4"
    };

    public static String[] allowedFuelItems = new String[] {
        "IC2:reactorUraniumSimple",
        "IC2:reactorMOXSimple",
        "IC2:reactorLithiumCell",
        "gregtech:gt.reactorUranium*",
        "gregtech:gt.reactorMOX*",
        "gregtech:gt.reactorThorium*",
        "gregtech:gt.reactorNaquadah*",
        "modularnuclear:rodCustomFuel1",
        "modularnuclear:rodCustomFuel2",
        "modularnuclear:rodCustomFuel4"
    };

    public static String[] allowedFuelFluids = new String[] {
        "uraniumbasedliquidfuel",
        "thoriumbasedliquidfuel",
        "plutoniumbasedliquidfuel",
        "uraniumhexafluoride"
    };

    public static String[] blockedFuelFluids = new String[] {
        "naquadah"
    };

    public static String[] allowedCoolantItems = new String[] {
        "IC2:reactorCoolantSimple",
        "IC2:reactorCoolantTriple",
        "IC2:reactorCoolantSix",
        "gregtech:gt.reactorCoolant*"
    };

    public static String[] allowedCoolantFluids = new String[] {
        "ic2coolant",
        "heavywater",
        "distilledwater",
        "sodium",
        "lead",
        "molten.sodium",
        "molten.lead"
    };

    public static String[] allowedControlRods = new String[] {
        "stickLongSilver=SILVER",
        "stickLongBoron=BORON",
        "stickLongCadmium=CADMIUM",
        "stickLongIndium=INDIUM",
        "stickLongHafnium=HAFNIUM"
    };

    public static String[] allowedReflectors = new String[] {
        "plateGold",
        "plateStainlessSteel",
        "plateTungstenCarbide",
        "IC2:reactorReflector",
        "IC2:reactorReflectorThick",
        "gregtech:gt.neutronReflector"
    };

    public static String[] allowedModerators = new String[] {
        "stickLongGraphite",
        "stickLongCarbon",
        "stickLongBeryllium"
    };

    public static String[] allowedHeatVents = new String[] {
        "IC2:reactorVent*"
    };

    public static String[] allowedHeatExchangers = new String[] {
        "IC2:reactorHeatSwitch*"
    };

    public static String[] allowedPlatings = new String[] {
        "IC2:reactorPlating*"
    };

    public static String[] allowedInsulators = new String[] {
        "Naquarite_Universal_Insulator_Foil=1.0",
        "MicaInsulatorFoil=0.60",
        "ThermalClothT2=0.40",
        "ThermalCloth=0.20",
        "itemBasicAsteroids:7=0.20"
    };

    public static String[] allowedRadiovoltaics = new String[] {
        "modularnuclear:radiovoltaicPlateHV",
        "modularnuclear:radiovoltaicPlateEV"
    };

    public static String[] allowedNeutronSources = new String[] {
        "GoodGenerator:neutronSource",
        "item.neutronSource"
    };

    // ==========================================
    // Simulation Physics & Tuning Parameters
    // ==========================================
    public static double baseAbsorptionProbability = 0.05;
    public static double baseScatteringProbability = 0.10;
    public static double baseModerationProbability = 0.0;
    public static double gridHeatConduction = 0.15;
    public static double boundaryHeatLoss = 0.02;
    public static double radiationDecayFactor = 0.95;
    public static double heatPerFastNeutronAbsorption = 10.0;
    public static double heatPerThermalNeutronAbsorption = 25.0;
    public static double dopplerDeficitExponent = 1.2;
    public static int wallMaintenanceNeutronThreshold = 10000;
    public static double defaultAmbientTemp = 293.15;
    public static float beamEnergyToFastNeutronMultiplier = 0.001f;

    // ==========================================
    // Reactor Tiers & Material Limits
    // ==========================================
    public static final double[] DEFAULT_MAX_OPERATING_TEMP_TIER = new double[] {
        1200.0, 1400.0, 1800.0, 2200.0, 2600.0, 3200.0
    };
    public static double[] maxOperatingTempTier = new double[] {
        1200.0, // Tier 0: Electrum (EV)
        1400.0, // Tier 1: Platinum (IV)
        1800.0, // Tier 2: Osmium (LuV)
        2200.0, // Tier 3: Quantium (ZPM)
        2600.0, // Tier 4: Fluxed Electrum (UV)
        3200.0  // Tier 5: Black Plutonium (UHV)
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
                "Multiplier for Cherenkov visual beam intensity (default 2.5).");

            // Allowlists & Blocklists
            final String CAT_ALLOWLISTS = "allowlists";
            config.setCategoryComment(CAT_ALLOWLISTS, "Data-driven item and fluid allowlists/blocklists for the reactor core.");

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

            allowedHeatVents = config.getStringList(
                "allowedHeatVents",
                CAT_ALLOWLISTS,
                allowedHeatVents,
                "Items recognized as heat vents.");

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

            // Simulation Physics & Tuning Parameters
            final String CAT_PHYSICS = "simulation_physics";
            config.setCategoryComment(CAT_PHYSICS, "Simulation constants and physics tuning parameters.");

            baseAbsorptionProbability = config.getFloat(
                "baseAbsorptionProbability",
                CAT_PHYSICS,
                (float) baseAbsorptionProbability,
                0.0f,
                1.0f,
                "Base neutron absorption probability for standard reactor components.");

            baseScatteringProbability = config.getFloat(
                "baseScatteringProbability",
                CAT_PHYSICS,
                (float) baseScatteringProbability,
                0.0f,
                1.0f,
                "Base neutron scattering probability for standard reactor components.");

            baseModerationProbability = config.getFloat(
                "baseModerationProbability",
                CAT_PHYSICS,
                (float) baseModerationProbability,
                0.0f,
                1.0f,
                "Base fast-to-thermal neutron moderation probability.");

            gridHeatConduction = config.getFloat(
                "gridHeatConduction",
                CAT_PHYSICS,
                (float) gridHeatConduction,
                0.0f,
                1.0f,
                "Inter-tile heat conduction coefficient across adjacent grid cells.");

            boundaryHeatLoss = config.getFloat(
                "boundaryHeatLoss",
                CAT_PHYSICS,
                (float) boundaryHeatLoss,
                0.0f,
                1.0f,
                "Passive thermal loss coefficient across the reactor casing boundary.");

            radiationDecayFactor = config.getFloat(
                "radiationDecayFactor",
                CAT_PHYSICS,
                (float) radiationDecayFactor,
                0.0f,
                1.0f,
                "Decay factor applied to residual neutron flux and radiation fields per tick.");

            heatPerFastNeutronAbsorption = config.getFloat(
                "heatPerFastNeutronAbsorption",
                CAT_PHYSICS,
                (float) heatPerFastNeutronAbsorption,
                0.0f,
                1000.0f,
                "Heat energy in Hu deposited per fast neutron absorption.");

            heatPerThermalNeutronAbsorption = config.getFloat(
                "heatPerThermalNeutronAbsorption",
                CAT_PHYSICS,
                (float) heatPerThermalNeutronAbsorption,
                0.0f,
                1000.0f,
                "Heat energy in Hu deposited per thermal neutron absorption.");

            dopplerDeficitExponent = config.getFloat(
                "dopplerDeficitExponent",
                CAT_PHYSICS,
                (float) dopplerDeficitExponent,
                0.1f,
                5.0f,
                "Exponent for thermal Doppler broadening and resonance absorption deficit.");

            wallMaintenanceNeutronThreshold = config.getInt(
                "wallMaintenanceNeutronThreshold",
                CAT_PHYSICS,
                wallMaintenanceNeutronThreshold,
                100,
                1000000,
                "Cumulative neutron wall absorption required to trigger a maintenance inspection check.");

            defaultAmbientTemp = config.getFloat(
                "defaultAmbientTemp",
                CAT_PHYSICS,
                (float) defaultAmbientTemp,
                0.0f,
                1000.0f,
                "Default ambient temperature in Kelvin (293.15 K = 20 C).");

            beamEnergyToFastNeutronMultiplier = config.getFloat(
                "beamEnergyToFastNeutronMultiplier",
                CAT_PHYSICS,
                (float) beamEnergyToFastNeutronMultiplier,
                0.0f,
                10.0f,
                "Conversion factor from beam energy intensity (E * R) to fast neutron packet size.");

            // Reactor Tiers & Material Limits
            final String CAT_TIERS = "reactor_tiers";
            config.setCategoryComment(CAT_TIERS, "Maximum operating temperatures and safety thresholds per reactor pipe tier.");

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
        String t = text.trim().toLowerCase();
        String p = pattern.trim().toLowerCase();
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
        String name = fluid.getFluid().getName();
        String unloc = fluid.getFluid().getUnlocalizedName();

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
        return matchesItem(stack, allowedPlatings);
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
