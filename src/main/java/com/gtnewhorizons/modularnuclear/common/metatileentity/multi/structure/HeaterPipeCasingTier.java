package com.gtnewhorizons.modularnuclear.common.metatileentity.multi.structure;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantPipingRegistry;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.common.blocks.BlockCasings11;

/**
 * Item pipe casing tiers ({@link GregTechAPI#sBlockCasings11}, meta 0..7, casingTier 1..8) used in the centerline of
 * the Pressurized Fluid Heater.
 *
 * <p>
 * Each tier maps to its casing material, passage voltage tier (used for heat capacity and convective heat transfer),
 * maximum operating pressure (bar, matching the coolant loop pump/pipes), maximum operating temperature (°C, exceeding
 * which causes a powerfail and loop disconnection), and matching coolant loop pipe/reservoir materials.
 */
public enum HeaterPipeCasingTier {

    TIN(1, 0, "Tin", Materials.Tin, Materials.CastIron, 1, -1, 1000.0, "tin", "castiron", "iron", "wroughtiron"),
    BRASS(2, 1, "Brass", Materials.Brass, Materials.Steel, 2, -1, 1100.0, "brass", "steel"),
    ELECTRUM(3, 2, "Electrum", Materials.Electrum, Materials.StainlessSteel, 3,
        NuclearSimulationEngine.PIPE_TIER_ELECTRUM, 1200.0, "electrum", "stainlesssteel"),
    PLATINUM(4, 3, "Platinum", Materials.Platinum, Materials.Titanium, 4, NuclearSimulationEngine.PIPE_TIER_PLATINUM,
        1400.0, "platinum", "titanium", "tungstensteel"),
    OSMIUM(5, 4, "Osmium", Materials.Osmium, Materials.Osmium, 5, NuclearSimulationEngine.PIPE_TIER_OSMIUM, 1800.0,
        "osmium"),
    QUANTIUM(6, 5, "Quantium", Materials.Quantium, Materials.Neutronium, 6, NuclearSimulationEngine.PIPE_TIER_QUANTIUM,
        2200.0, "quantium", "tungstensteel", "neutronium"),
    FLUXED_ELECTRUM(7, 6, "Fluxed Electrum", Materials.ElectrumFlux, Materials.Neutronium, 7,
        NuclearSimulationEngine.PIPE_TIER_FLUXED_ELECTRUM, 2600.0, "electrumflux", "fluxedelectrum", "neutronium"),
    BLACK_PLUTONIUM(8, 7, "Black Plutonium", Materials.BlackPlutonium, Materials.Neutronium, 8,
        NuclearSimulationEngine.PIPE_TIER_BLACK_PLUTONIUM, 3200.0, "blackplutonium", "neutronium");

    private final int casingTier;
    private final int meta;
    private final String displayName;
    private final Materials casingMaterial;
    private final Materials equivalentLoopMaterial;
    private final int passageVoltageTier;
    private final int enginePipeTier;
    private final double fallbackMaxTempC;
    private final String[] matchingLoopMaterialNames;

    HeaterPipeCasingTier(int casingTier, int meta, String displayName, Materials casingMaterial,
        Materials equivalentLoopMaterial, int passageVoltageTier, int enginePipeTier, double fallbackMaxTempC,
        String... matchingLoopMaterialNames) {
        this.casingTier = casingTier;
        this.meta = meta;
        this.displayName = displayName;
        this.casingMaterial = casingMaterial;
        this.equivalentLoopMaterial = equivalentLoopMaterial;
        this.passageVoltageTier = passageVoltageTier;
        this.enginePipeTier = enginePipeTier;
        this.fallbackMaxTempC = fallbackMaxTempC;
        this.matchingLoopMaterialNames = matchingLoopMaterialNames;
    }

    /** 1-based casing tier (1..8) matching {@code GTStructureUtility.chainItemPipeCasings}. */
    public int getCasingTier() {
        return casingTier;
    }

    /** Block metadata in {@link GregTechAPI#sBlockCasings11} (0..7). */
    public int getMeta() {
        return meta;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Materials getCasingMaterial() {
        return casingMaterial;
    }

    public Materials getEquivalentLoopMaterial() {
        return equivalentLoopMaterial;
    }

    /** Voltage tier (1..8) passed to {@code CoolantPassage.heatCapacity(tier)} and {@code heatTransferFactor(tier)}. */
    public int getPassageVoltageTier() {
        return passageVoltageTier;
    }

    /** Maximum operating pressure in bar, determined the same way as the coolant loop pump and pipes. */
    public double getMaxPressureBar() {
        return CoolantPipingRegistry.getMaxPressureBar(equivalentLoopMaterial);
    }

    /** Maximum inner operating temperature in °C before the fluid heater powerfails. */
    public double getMaxOperatingTemperatureCelsius() {
        if (enginePipeTier >= 0) {
            return NuclearSimulationEngine.getMaxOperatingTemperature(enginePipeTier);
        }
        return fallbackMaxTempC;
    }

    /**
     * Checks whether this item pipe casing tier matches the given coolant loop pipe/reservoir material.
     */
    public boolean matchesLoopMaterial(Materials loopMaterial) {
        if (loopMaterial == null) return false;
        if (loopMaterial == casingMaterial || loopMaterial == equivalentLoopMaterial) return true;
        if (CoolantPipingRegistry.areMaterialsEqual(casingMaterial, loopMaterial)) return true;
        if (CoolantPipingRegistry.areMaterialsEqual(equivalentLoopMaterial, loopMaterial)) return true;
        return matchesLoopMaterialName(loopMaterial.mName);
    }

    /**
     * Checks whether this item pipe casing tier matches the given coolant loop pipe/reservoir material name.
     */
    public boolean matchesLoopMaterialName(String materialName) {
        if (materialName == null || materialName.isEmpty()) return false;
        String clean = materialName.trim()
            .replace(" ", "")
            .replace("_", "")
            .replace("-", "")
            .toLowerCase(java.util.Locale.ROOT);
        for (String allowed : matchingLoopMaterialNames) {
            if (allowed.equals(clean)) {
                return true;
            }
        }
        return false;
    }

    public ItemStack getItemStack() {
        if (GregTechAPI.sBlockCasings11 != null) {
            return new ItemStack(GregTechAPI.sBlockCasings11, 1, meta);
        }
        return new ItemStack(Blocks.stone, 1, meta);
    }

    public static HeaterPipeCasingTier getByCasingTier(int casingTier) {
        for (HeaterPipeCasingTier t : values()) {
            if (t.casingTier == casingTier) return t;
        }
        if (casingTier <= 1) return TIN;
        return BLACK_PLUTONIUM;
    }

    public static HeaterPipeCasingTier getByMeta(int meta) {
        for (HeaterPipeCasingTier t : values()) {
            if (t.meta == meta) return t;
        }
        if (meta <= 0) return TIN;
        return BLACK_PLUTONIUM;
    }

    public static HeaterPipeCasingTier fromBlock(Block block, int meta) {
        if (block == null) return null;
        if (block == GregTechAPI.sBlockCasings11 || block instanceof BlockCasings11) {
            if (meta >= 0 && meta < values().length) {
                return getByMeta(meta);
            }
        }
        return null;
    }

    public static HeaterPipeCasingTier getByMaterial(Materials mat) {
        if (mat == null) return null;
        for (HeaterPipeCasingTier t : values()) {
            if (t.casingMaterial == mat) return t;
        }
        for (HeaterPipeCasingTier t : values()) {
            if (t.matchesLoopMaterial(mat)) return t;
        }
        return null;
    }
}
