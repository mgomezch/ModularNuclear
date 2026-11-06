package com.gtnewhorizons.modularnuclear.common.config;

import java.io.File;

import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.modularnuclear.ModularNuclear;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification;
import com.gtnewhorizons.modularnuclear.common.util.Swallowed;

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
    // Reactor simulation
    // ==========================================
    public static final String CAT_SIMULATION = "simulation";
    public static int ticksPerCycle = 5;

    // ==========================================
    // GUI sync
    // ==========================================
    public static final String CAT_GUI = "gui";
    public static int loopGraphSyncIntervalDedicated = 20;
    public static int loopGraphSyncIntervalIntegrated = 1;

    // ==========================================
    // Allowlists & Blocklists (Core Components)
    // ==========================================
    // Entries are exact: "modid:name" (any meta), "modid:name:meta" or "ore:oreName" for items, and registry names
    // for fluids.
    public static String[] blockedItems = new String[] { "gregtech:gt.depletedRodLithium",
        "gregtech:gt.depletedRodGlowstone", "gregtech:gt.depletedRodExcitedUranium",
        "gregtech:gt.depletedRodExcitedUranium2", "gregtech:gt.depletedRodExcitedUranium4",
        "gregtech:gt.depletedRodExcitedPlutonium", "gregtech:gt.depletedRodExcitedPlutonium2",
        "gregtech:gt.depletedRodExcitedPlutonium4", "gregtech:gt.depletedRodHighDensityUranium",
        "gregtech:gt.depletedRodHighDensityUranium2", "gregtech:gt.depletedRodHighDensityUranium4",
        "gregtech:gt.depletedRodHighDensityPlutonium", "gregtech:gt.depletedRodHighDensityPlutonium2",
        "gregtech:gt.depletedRodHighDensityPlutonium4", "gregtech:gt.depletedRodNaquadria",
        "gregtech:gt.depletedRodNaquadria2", "gregtech:gt.depletedRodNaquadria4", "gregtech:gt.depletedRodNaquadah",
        "gregtech:gt.depletedRodNaquadah2", "gregtech:gt.depletedRodNaquadah4", "gregtech:gt.depletedRodMOX",
        "gregtech:gt.depletedRodMOX2", "gregtech:gt.depletedRodMOX4", "gregtech:gt.depletedRodThorium",
        "gregtech:gt.depletedRodThorium2", "gregtech:gt.depletedRodThorium4", "gregtech:gt.depletedRodUranium",
        "gregtech:gt.depletedRodUranium2", "gregtech:gt.depletedRodUranium4", "gregtech:gt.depletedRodTiberium",
        "gregtech:gt.depletedRodTiberium2", "gregtech:gt.depletedRodTiberium4", "gregtech:gt.depletedRodNaquadah32",
        "modularnuclear:gt.modularnuclear.fuelrod.depleted.custom1",
        "modularnuclear:gt.modularnuclear.fuelrod.depleted.custom2",
        "modularnuclear:gt.modularnuclear.fuelrod.depleted.custom4", "miscutils:itemCellUraniumDepletedMoltenSaltUSalt",
        "miscutils:itemCellThoriumDepletedMoltenSaltTSalt",
        "miscutils:itemCellThoriumBerylliumDepletedMoltenSaltTBSalt", "IC2:itemFuelRod" };

    public static String[] allowedFuelItems = new String[] { "IC2:reactorUraniumSimple", "IC2:reactorMOXSimple",
        "modularnuclear:gt.modularnuclear.fuelrod.custom1", "modularnuclear:gt.modularnuclear.fuelrod.custom2",
        "modularnuclear:gt.modularnuclear.fuelrod.custom4" };

    public static String[] allowedFuelFluids = new String[] { "uranium based liquid fuel", "thorium based liquid fuel",
        "plutonium based liquid fuel", "molten.uraniumhexafluoride" };

    public static String[] blockedFuelFluids = new String[] { "molten.naquadah" };

    public static String[] allowedCoolantItems = new String[] { "IC2:reactorCoolantSimple", "IC2:reactorCoolantTriple",
        "IC2:reactorCoolantSix" };

    public static String[] allowedCoolantFluids = new String[] { "ic2coolant", "heavywater", "ic2distilledwater" };

    public static String[] allowedControlRods = new String[] { "ore:stickLongSilver=SILVER", "ore:stickLongBoron=BORON",
        "ore:stickLongCadmium=CADMIUM", "ore:stickLongIndium=INDIUM", "ore:stickLongHafnium=HAFNIUM",
        "ore:stickLongNeutronium=NEUTRONIUM" };

    public static String[] allowedReflectors = new String[] { "ore:plateGold", "ore:plateStainlessSteel",
        "ore:plateTungstenCarbide", "IC2:reactorReflector", "IC2:reactorReflectorThick",
        "gregtech:gt.neutronreflector" };

    public static String[] allowedModerators = new String[] { "ore:stickLongGraphite", "ore:stickLongCarbon",
        "ore:stickLongBeryllium" };

    public static String[] allowedHeatVents = new String[] { "IC2:reactorVent", "IC2:reactorVentCore",
        "IC2:reactorVentGold", "IC2:reactorVentSpread", "IC2:reactorVentDiamond" };

    public static String[] allowedHeatExchangers = new String[] { "IC2:reactorHeatSwitch", "IC2:reactorHeatSwitchCore",
        "IC2:reactorHeatSwitchSpread", "IC2:reactorHeatSwitchDiamond" };

    public static String[] allowedInsulators = new String[] { "gregtech:gt.metaitem.03:32251=1.0",
        "dreamcraft:MicaInsulatorFoil=0.60", "GalaxySpace:item.ThermalClothT2=0.40",
        "GalacticraftMars:item.itemBasicAsteroids:7=0.20" };

    public static String[] allowedRadiovoltaics = new String[] { "modularnuclear:radiovoltaic_plate:0",
        "modularnuclear:radiovoltaic_plate:1" };

    public static String[] allowedNeutronSources = new String[] { "GoodGenerator:neutronSource" };

    private static volatile int generation;

    /**
     * Counts configuration changes: what reactors work out from this configuration once (item and fluid roles) is
     * kept until it changes. Code that edits the lists above at runtime must call {@link #changed()}.
     */
    public static int generation() {
        return generation;
    }

    public static void changed() {
        generation++;
    }

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
                "Items blocked from insertion into reactor core hatches (depleted rods, empty rods, etc.): exact modid:name, modid:name:meta or ore:oreName.");

            allowedFuelItems = config.getStringList(
                "allowedFuelItems",
                CAT_ALLOWLISTS,
                allowedFuelItems,
                "Items recognized as active nuclear fuel in reactor core hatches: exact modid:name, modid:name:meta or ore:oreName.");

            allowedFuelFluids = config.getStringList(
                "allowedFuelFluids",
                CAT_ALLOWLISTS,
                allowedFuelFluids,
                "Fluid registry names recognized as nuclear fuels for fluid-cooled reactor cores.");

            blockedFuelFluids = config.getStringList(
                "blockedFuelFluids",
                CAT_ALLOWLISTS,
                blockedFuelFluids,
                "Fluid registry names blocked from reactor fuel hatches (e.g. molten.naquadah).");

            allowedCoolantItems = config.getStringList(
                "allowedCoolantItems",
                CAT_ALLOWLISTS,
                allowedCoolantItems,
                "Items recognized as coolant cells in reactor core hatches: exact modid:name, modid:name:meta or ore:oreName.");

            allowedCoolantFluids = config.getStringList(
                "allowedCoolantFluids",
                CAT_ALLOWLISTS,
                allowedCoolantFluids,
                "Fluid registry names recognized as valid reactor coolants.");

            allowedControlRods = config.getStringList(
                "allowedControlRods",
                CAT_ALLOWLISTS,
                allowedControlRods,
                "Items mapped to control rod types (item=TYPE), item as modid:name, modid:name:meta or ore:oreName.");

            allowedReflectors = config.getStringList(
                "allowedReflectors",
                CAT_ALLOWLISTS,
                allowedReflectors,
                "Items recognized as neutron reflectors: modid:name, modid:name:meta or ore:oreName.");

            allowedModerators = config.getStringList(
                "allowedModerators",
                CAT_ALLOWLISTS,
                allowedModerators,
                "Items recognized as neutron moderators: modid:name, modid:name:meta or ore:oreName.");

            allowedHeatVents = config
                .getStringList("allowedHeatVents", CAT_ALLOWLISTS, allowedHeatVents, "Items recognized as heat vents.");

            allowedHeatExchangers = config.getStringList(
                "allowedHeatExchangers",
                CAT_ALLOWLISTS,
                allowedHeatExchangers,
                "Items recognized as heat exchangers.");

            allowedInsulators = config.getStringList(
                "allowedInsulators",
                CAT_ALLOWLISTS,
                allowedInsulators,
                "Items mapped to insulation dampening factor (item=fraction), item as modid:name, modid:name:meta or ore:oreName.");

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

            ticksPerCycle = config.getInt(
                "ticksPerCycle",
                CAT_SIMULATION,
                ticksPerCycle,
                1,
                100,
                "Number of ticks per reactor simulation cycle (default 5, i.e. 4 cycles/second).");
            NuclearSimulationEngine.params().ticksPerCycle = ticksPerCycle;

            config.setCategoryComment(CAT_GUI, "How often machine GUIs send their data to the players viewing them.");
            loopGraphSyncIntervalDedicated = config.getInt(
                "loopGraphSyncIntervalDedicated",
                CAT_GUI,
                loopGraphSyncIntervalDedicated,
                1,
                1200,
                "Minimum ticks between coolant loop graph updates to each player viewing one, on a dedicated server (default 20, once a second). A large loop's graph is tens of KB, rebuilt for each update.");
            loopGraphSyncIntervalIntegrated = config.getInt(
                "loopGraphSyncIntervalIntegrated",
                CAT_GUI,
                loopGraphSyncIntervalIntegrated,
                1,
                1200,
                "Minimum ticks between coolant loop graph updates in singleplayer and LAN games (the integrated server; default 1, every tick).");

        } catch (Exception e) {
            ModularNuclear.LOG.error("Failed to load ModularNuclear config", e);
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
            changed();
        }
    }

    /** Minimum ticks between coolant loop graph updates on the server running now. */
    public static int loopGraphSyncIntervalTicks() {
        MinecraftServer server = MinecraftServer.getServer();
        return server != null && server.isDedicatedServer() ? loopGraphSyncIntervalDedicated
            : loopGraphSyncIntervalIntegrated;
    }

    // ==========================================
    // Matching and Registry Lookup Helpers
    // ==========================================

    /** Whether one of the entries names the stack exactly (see {@link ReactorDataRegistry#matchesItem}). */
    public static boolean matchesItem(ItemStack stack, String[] list) {
        if (stack == null || list == null) return false;
        for (String entry : list) {
            if (ReactorDataRegistry.matchesItem(stack, entry)) return true;
        }
        return false;
    }

    public static boolean matchesFluid(FluidStack fluid, String[] list) {
        return fluid != null && matchesFluid(fluid.getFluid(), list);
    }

    /** Whether one of the entries is exactly the fluid's registry name. */
    public static boolean matchesFluid(Fluid fluid, String[] list) {
        if (fluid == null || list == null) return false;
        String name = fluid.getName();
        for (String entry : list) {
            if (name.equals(entry)) return true;
        }
        return false;
    }

    public static boolean isItemBlocked(ItemStack stack) {
        return matchesItem(stack, blockedItems);
    }

    public static boolean isItemFuel(ItemStack stack) {
        return matchesItem(stack, allowedFuelItems);
    }

    public static boolean isFluidFuel(Fluid fluid) {
        if (matchesFluid(fluid, blockedFuelFluids)) return false;
        return matchesFluid(fluid, allowedFuelFluids);
    }

    public static boolean isFluidFuel(FluidStack fluid) {
        if (matchesFluid(fluid, blockedFuelFluids)) return false;
        return matchesFluid(fluid, allowedFuelFluids);
    }

    public static boolean isFluidBlocked(Fluid fluid) {
        return matchesFluid(fluid, blockedFuelFluids);
    }

    public static boolean isFluidBlocked(FluidStack fluid) {
        return matchesFluid(fluid, blockedFuelFluids);
    }

    public static boolean isItemCoolant(ItemStack stack) {
        return matchesItem(stack, allowedCoolantItems);
    }

    public static boolean isCoolantFluid(Fluid fluid) {
        return matchesFluid(fluid, allowedCoolantFluids);
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
                if (ReactorDataRegistry.matchesItem(stack, itemPattern)) {
                    return val;
                }
            } catch (Exception swallowed) {
                Swallowed.note(swallowed);
            }
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
            if (ReactorDataRegistry.matchesItem(stack, itemPattern)) {
                return rodType;
            }
        }
        return null;
    }
}
